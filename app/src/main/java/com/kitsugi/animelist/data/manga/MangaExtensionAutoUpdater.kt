package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kitsugi.animelist.data.remote.MangaExtensionInfo
import com.kitsugi.animelist.data.remote.MangaRepoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * MangaExtensionAutoUpdater
 *
 * Keiyoushi repo index'ini periyodik olarak kontrol ederek yüklü manga eklentilerini
 * otomatik günceller. Uygulama başlangıcında arka planda çalışır.
 *
 * Mantık:
 *   1) Keiyoushi index.min.json çek
 *   2) Yüklü her APK'nın paket adını → repo'daki versionCode ile karşılaştır
 *   3) Repo'daki versionCode daha yüksekse → indir ve bellekten yeniden yükle
 *   4) Sonuçları SharedPreferences'a kaydet (son kontrol zamanı + kaç eklenti güncellendi)
 *
 * Güvenlik mekanizmaları:
 *   - Günde en fazla 1 kontrol yapar (ağ tasarrufu için)
 *   - Tek bir eklenti hata verirse diğerlerine devam eder
 *   - İndirme başarısız olursa mevcut sürüm korunur
 */
object MangaExtensionAutoUpdater {

    private const val TAG = "MangaExtAutoUpdater"
    private const val PREFS_NAME = "manga_auto_updater"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LAST_UPDATED_COUNT = "last_updated_count"
    private const val KEY_LAST_CHECK_RESULT = "last_check_result"

    /** Günde en fazla bir kontrol (24 saat) */
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /**
     * Otomatik güncelleme akışını çalıştırır.
     * [forceCheck] = true ile 24 saatlik kısıtlamayı aşabilirsiniz (ayarlar ekranındaki "Şimdi Güncelle" butonu).
     *
     * @return Güncellenen eklenti sayısı
     */
    suspend fun runIfNeeded(
        context: Context,
        forceCheck: Boolean = false
    ): UpdateResult = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        val now = System.currentTimeMillis()

        if (!forceCheck && (now - lastCheck) < CHECK_INTERVAL_MS) {
            val since = (now - lastCheck) / 1000 / 60
            Log.d(TAG, "Son kontrolden bu yana $since dk geçmiş, atlanıyor (24 saat dolmadı).")
            return@withContext UpdateResult.Skipped(minutesUntilNextCheck = ((CHECK_INTERVAL_MS - (now - lastCheck)) / 1000 / 60).toInt())
        }

        // Kullanıcı otomatik güncellemeyi tamamen kapatmışsa hiç ağ isteği yapma.
        if (!forceCheck && !MangaExtensionPrefs.isAutoUpdateEnabled(context)) {
            Log.d(TAG, "Otomatik güncelleme kullanıcı tarafından kapatılmış, atlanıyor.")
            return@withContext UpdateResult.Skipped(minutesUntilNextCheck = (CHECK_INTERVAL_MS / 1000 / 60).toInt())
        }

        Log.i(TAG, "Keiyoushi repo kontrolü başlıyor...")
        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

        // Yüklü eklentileri al — loadedSources ve fiziksel klasör üzerinden paket adı → versionCode
        val loadedSources = MangaExtensionLoader.getLoadedSources()
        val installedPackages = buildInstalledPackageMap(context, loadedSources)
        if (installedPackages.isEmpty()) {
            Log.i(TAG, "Yüklü manga eklentisi veya APK paketi bulunamadı, güncelleme atlanıyor.")
            return@withContext UpdateResult.Success(updated = 0, checked = 0)
        }

        Log.i(TAG, "${installedPackages.size} yüklü APK paketi bulundu.")

        // Keiyoushi V2 index + kullanıcının eklediği özel repolar.
        // Önceden SADECE Keiyoushi kontrol ediliyordu; Kitsugi-Manga-Plugins'teki
        // Manga-TR (1.6.24) hiçbir zaman güncelleme almıyordu.
        val client = MangaRepoClient(context)
        val keiyoushi = client.fetchExtensionsAutoDetect(MangaRepoClient.KEIYOUSHI_INDEX_URL)
        val customRepos = loadCustomRepoUrls(context)
        val customEntries = mutableListOf<MangaExtensionInfo>()
        customRepos.forEach { repoUrl ->
            client.fetchExtensionsAutoDetect(repoUrl)?.let { customEntries += it }
        }

        if (keiyoushi == null && customEntries.isEmpty()) {
            Log.e(TAG, "Hiçbir manga repo'su çekilemedi.")
            prefs.edit().putString(KEY_LAST_CHECK_RESULT, "REPO_FETCH_FAILED").apply()
            return@withContext UpdateResult.Failed(reasonCode = "REPO_FETCH_FAILED")
        }

        // Aynı paket hem Keiyoushi hem özel repoda varsa KULLANICININ repolu kazanır.
        // Özel girişleri ÖNE koyuyoruz: distinctBy ve associateBy ikisi de ilk/son
        // kaydı farklı seçtiği için eski kodda liste ile lookup map'i çelişiyordu.
        val repoExtensions = (customEntries + keiyoushi.orEmpty()).distinctBy { it.pkg }
        Log.i(TAG, "Repo'larda ${repoExtensions.size} eklenti var (özel repo: ${customRepos.size}).")

        // Paket adına göre hızlı lookup için map oluştur (aynı sıralama → aynı kazanan)
        val repoByPkg = repoExtensions.associateBy { it.pkg }
        // repoExtensions özel girişlerle başladığı için repoByPkg bu paketlerde
        // mutlaka özel katalogdaki girdiyi döndürür.
        val customPkgs = customEntries.map { it.pkg }.toSet()

        var updatedCount = 0
        var checkedCount = 0
        var skippedByPreference = 0
        val updatedNames = mutableListOf<String>()

        for ((pkg, installedVersionCode) in installedPackages) {
            val repoEntry = repoByPkg[pkg] ?: continue
            // Eklenti başına "otomatik güncelleme" anahtarı kapalıysa bu eklentiyi atla.
            // (Video eklentilerindeki eklenti başına güncelleme kontrolü ile aynı mantık.)
            if (!MangaExtensionPrefs.isAutoUpdateEnabled(context, pkg)) {
                skippedByPreference++
                Log.d(TAG, "${repoEntry.name}: otomatik güncelleme kapalı, atlandı.")
                continue
            }
            checkedCount++

            val repoVersionCode = repoEntry.versionCode
            if (repoVersionCode > installedVersionCode) {
                Log.i(TAG, "${repoEntry.name}: güncelleme var! ($installedVersionCode → $repoVersionCode)")
                val success = tryUpdateExtension(
                    context,
                    repoEntry,
                    trustSignature = repoEntry.pkg in customPkgs,
                )
                if (success) {
                    updatedCount++
                    updatedNames.add(repoEntry.name)
                    Log.i(TAG, "${repoEntry.name}: güncellendi ✅")
                } else {
                    Log.w(TAG, "${repoEntry.name}: güncelleme başarısız ❌")
                }
            } else {
                Log.d(TAG, "${repoEntry.name}: güncel (v$installedVersionCode)")
            }
        }

        val summary = if (updatedCount > 0) {
            KitsugiStrings.get(R.string.manga_auto_update_success, updatedCount, updatedNames.joinToString(", "))
        } else if (checkedCount > 0) {
            KitsugiStrings.get(R.string.manga_auto_update_all_current, checkedCount)
        } else if (skippedByPreference > 0) {
            KitsugiStrings.get(R.string.manga_auto_update_all_disabled, skippedByPreference)
        } else {
            KitsugiStrings.get(R.string.manga_auto_update_no_updates)
        }

        Log.i(TAG, summary)
        prefs.edit()
            .putInt(KEY_LAST_UPDATED_COUNT, updatedCount)
            .putString(KEY_LAST_CHECK_RESULT, summary)
            .apply()

        UpdateResult.Success(updated = updatedCount, checked = checkedCount, updatedNames = updatedNames)
    }

    /**
     * Tek bir eklentiyi indir ve yeniden yükle.
     * Başarısız olursa mevcut APK dokunulmaz.
     */
    private fun tryUpdateExtension(
        context: Context,
        info: MangaExtensionInfo,
        trustSignature: Boolean = false,
    ): Boolean {
        return try {
            val extensionId = info.pkg.replace(".", "_")
            MangaExtensionLoader.installWithRollback(context, extensionId) {
                val downloaded = MangaExtensionLoader.downloadExtension(
                    context, extensionId, info.apkUrl,
                    expectedPackage = info.pkg, expectedVersionCode = info.versionCode.toLong()
                )
                if (!downloaded) return@installWithRollback false

                // Kullanıcının açıkça eklediği repodan gelen yeni imzaya güven.
                // Aksi halde özel derleme imza rotasyonunda güncelleme sessizce düşer.
                if (trustSignature) {
                    com.kitsugi.animelist.core.security.TrustManager
                        .getApkSignatureHash(context, MangaExtensionLoader.apkFile(context, extensionId))
                        ?.let { com.kitsugi.animelist.core.security.TrustManager.trustSignature(context, it) }
                }

                // Bellekten kaldır ve yeniden yükle
                MangaExtensionLoader.unloadExtension(context, extensionId)
                val loaded = MangaExtensionLoader.loadExtension(context, extensionId, trustSignature)
                loaded != null
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryUpdateExtension hata (${info.name}): ${e.message}", e)
            false
        }
    }

    /**
     * Yüklü kaynaklardan paket adı → kurulu versionCode haritası oluşturur.
     * PackageManager aracılığıyla APK'nın gerçek versionCode'unu okur.
     */
    private fun buildInstalledPackageMap(
        context: Context,
        sources: List<MangaSource>
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        
        // 1) loadedSources içindeki eklentileri al (dahili kaynaklar hariç)
        for (source in sources) {
            if (source.isBuiltIn) continue
            val pkg = source.pkgName.trim()
            if (pkg.isNotBlank()) {
                val vc = MangaExtensionLoader.getInstalledVersionCode(context, pkg)
                if (vc >= 0) {
                    result[pkg] = vc
                }
            }
        }
        
        // 2) manga_extensions dizinindeki tüm APK dosyalarını tarayarak yüklü olanları da al
        try {
            val extDir = MangaExtensionLoader.extensionDir(context)
            if (extDir.exists() && extDir.isDirectory) {
                val apkFiles = extDir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) }
                apkFiles?.forEach { file ->
                    // Dosya adından paket çıkarmak yanlış: elle kurulmuş APK'lar
                    // `tachiyomi-tr.mangatr-v1.6.24.apk` gibi adlar taşıyabilir.
                    // Gerçek paket adını manifest'ten oku.
                    val pkg = MangaExtensionLoader.readPackageName(context, file)
                        ?: file.nameWithoutExtension.replace("_", ".")
                    // Dosyanın kendisinden oku: paket adı → dosya adı varsayımı
                    // elle kurulmuş APK'larda tutmuyor ve versionCode -1 düşüyordu.
                    val vc = MangaExtensionLoader.getInstalledVersionCode(context, file)
                    if (vc >= 0) {
                        result[pkg] = vc
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "buildInstalledPackageMap dizin tarama hatası: ${e.message}")
        }
        
        return result
    }

    // ─── Durum sorgulama (Settings UI için) ──────────────────────────────────

    fun getLastCheckTimeMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK_MS, 0L)

    fun getLastUpdatedCount(context: Context): Int =
        prefs(context).getInt(KEY_LAST_UPDATED_COUNT, 0)

    fun getLastCheckResult(context: Context): String? =
        prefs(context).getString(KEY_LAST_CHECK_RESULT, null)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ayarlar → Manga Kaynakları → Repolar listesiyle aynı SharedPreferences. */
    private fun loadCustomRepoUrls(context: Context): List<String> {
        val saved = context.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
            .getString("repo_urls", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(saved)
            (0 until arr.length()).map { arr.getString(it) }
                .filter { it.isNotBlank() && !it.contains("keiyoushi", ignoreCase = true) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Result sınıfları ────────────────────────────────────────────────────

    sealed class UpdateResult {
        data class Success(
            val updated: Int,
            val checked: Int,
            val updatedNames: List<String> = emptyList()
        ) : UpdateResult()

        data class Skipped(val minutesUntilNextCheck: Int) : UpdateResult()
        data class Failed(val reasonCode: String) : UpdateResult()
    }
}
