package com.kitsugi.animelist.ui.app

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kitsugi.animelist.core.notifications.AddonInstallNotifier
import com.kitsugi.animelist.core.security.TrustManager
import com.kitsugi.animelist.core.security.RepoVerifier
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaSourceStateEntity
import com.kitsugi.animelist.data.manga.MangaExtensionLoader
import com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.manga.model.SourceRuntimeStats
import com.kitsugi.animelist.data.remote.MangaExtensionInfo
import com.kitsugi.animelist.data.remote.MangaRepoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class MangaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val mangaRepository = MangaSourceRepository(context)
    private val mangaRepoClient = MangaRepoClient(context)
    private val mangaRepoPrefs = context.getSharedPreferences("manga_repos", android.content.Context.MODE_PRIVATE)

    var mangaSources by mutableStateOf(mangaRepository.getInstalledSources())
        private set

    var mangaReposState by mutableStateOf<List<String>>(emptyList())
        private set

    var mangaRepoExtensionsState by mutableStateOf<Map<String, List<MangaExtensionInfo>?>>(emptyMap())
    var mangaRepoLoadingState by mutableStateOf<Map<String, Boolean>>(emptyMap())

    /**
     * Güncelleme mevcut olan eklentilerin paket adları (pkg).
     * Mihon'un Extension.Installed.hasUpdate alanına karşılık gelir.
     * versionCode karşılaştırması ile doldurulur.
     */
    var extensionsWithUpdates by mutableStateOf<Set<String>>(emptySet())
        private set

    var mangaBulkInstallRepoUrl by mutableStateOf<String?>(null)
    var mangaBulkInstallDone by mutableStateOf(0)
    var mangaBulkInstallTotal by mutableStateOf(0)
    var mangaBulkInstallCurrentName by mutableStateOf("")

    private fun notifyMangaBulkProgress(repoName: String = mangaBulkInstallRepoUrl.orEmpty()) {
        AddonInstallNotifier.showProgress(
            context = context,
            repoName = repoName,
            done = mangaBulkInstallDone,
            total = mangaBulkInstallTotal,
            currentName = mangaBulkInstallCurrentName
        )
    }

    private fun finishMangaBulkNotification(message: String, success: Boolean) {
        AddonInstallNotifier.showResult(
            context = context,
            message = message
        )
    }

    var onShowMessage: ((String) -> Unit)? = null

    var untrustedRepoToConfirm by mutableStateOf<String?>(null)
        private set

    var untrustedSignatureToConfirm by mutableStateOf<Pair<MangaExtensionInfo, String>?>(null)
        private set

    fun clearUntrustedRepoConfirm() {
        untrustedRepoToConfirm = null
    }

    fun clearUntrustedSignatureConfirm() {
        untrustedSignatureToConfirm = null
    }

    var mangaSourceBusyKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    var mangaSourceStateReport by mutableStateOf<List<MangaSourceStateEntity>>(emptyList())
        private set

    /** Toplu silme sürerken yinelenen isteği engeller; butonun kendisini ASLA devre dışı bırakmaz. */
    var mangaIsBulkDeleting by mutableStateOf(false)
        private set

    /** "Toplu Sil" ile gizlenmiş, "Geri getir" ile geri açılabilecek yerleşik kaynak sayısı. */
    var mangaBulkDeleteHiddenBuiltInCount by mutableStateOf(0)
        private set

    init {
        com.kitsugi.animelist.data.manga.KitsugiMangaDefaultProfile.ensureApplied(context)
        mangaSources = mangaRepository.getInstalledSources()

        // Uygulama yeniden başlasa bile "Geri getir" butonu görünür kalmalı.
        viewModelScope.launch(Dispatchers.IO) {
            val hidden = mangaRepository.getBulkDeleteHiddenBuiltInCount()
            withContext(Dispatchers.Main) { mangaBulkDeleteHiddenBuiltInCount = hidden }
        }

        // Varsayılan resmi Kitsugi-Manga-Plugins deposunu yükle / tohumla
        val urls: List<String> = com.kitsugi.animelist.data.manga.KitsugiMangaDefaultProfile.loadOrSeedMangaRepos(context)
        mangaReposState = urls

        // Repo verilerini otomatik çek ve ardından güncelleme kontrolü yap
        viewModelScope.launch {
            urls.forEach { url ->
                if (!mangaRepoExtensionsState.containsKey(url) && mangaRepoLoadingState[url] != true) {
                    fetchMangaRepo(url)
                }
            }
            if (urls.isNotEmpty()) {
                checkAllUpdates()
            }
            ensureDefaultTurkishSourceInstalled()
        }

        viewModelScope.launch {
            mangaRepository.observeSourceStateReport().collect { report ->
                mangaSourceStateReport = report
            }
        }

        // STARTUP YARIŞI: MangaSourceRepository eklentileri IO'da async yüklüyordu;
        // ViewModel daha önce oluştuğu için "Installed (0)" / boş liste görülebiliyordu.
        // Yükleme bitene kadar bekle, eski Disabled bayraklarını migrate et, listeyi tazele.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mangaRepository.awaitExtensionsLoaded()
                mangaRepository.migrateLegacyDisabledStates()
            }
            mangaSources = mangaRepository.getInstalledSources()
            checkAllUpdates()
        }

        // Kurulum / silme / güncelleme / sağlık değişimi başka bir ekrandan yapılsa
        // bile tüm ViewModel örnekleri aynı listeyi görsün.
        viewModelScope.launch {
            MangaExtensionLoader.sourceChanges.collect {
                val sources = withContext(Dispatchers.IO) {
                    mangaRepository.getInstalledSources()
                }
                mangaSources = sources
            }
        }
    }

    /** Ayarlar ekranındaki aktif/pasif anahtarı için. */
    fun isMangaSourceEnabled(source: MangaSource): Boolean =
        mangaRepository.isSourceEnabled(source)

    /**
     * İmza "kullanıcı onayıyla güven" istisnası SADECE kullanıcının elle eklediği
     * üçüncü parti repolar için geçerlidir. Resmî Keiyoushi kataloglarında
     * asla uygulanmaz — aksi halde ele geçirilmiş bir yansıdan gelen imzasız /
     * farklı imzalı APK sessizce kabul edilirdi.
     */
    private fun allowCustomSignature(repoUrl: String): Boolean {
        if (repoUrl.contains("keiyoushi", ignoreCase = true)) return false
        val normalized = repoUrl.trim().trimEnd('/')
        return mangaReposState.any {
            it.trim().trimEnd('/').equals(normalized, ignoreCase = true)
        }
    }

    private fun saveMangaRepos(urls: List<String>) {
        mangaRepoPrefs.edit().putString("repo_urls", org.json.JSONArray(urls).toString()).apply()
    }

    /**
     * İlk kurulumda hiç manga eklentisi kurulu değilse, bilinen güvenilir bir Türkçe
     * kaynağı (Nirvana Manga) otomatik olarak indirip kurar. Tekrar tekrar denememek
     * için "default_source_installed" flag'ini SharedPreferences'a yazar.
     */
    private suspend fun ensureDefaultTurkishSourceInstalled() {
        val prefs = context.getSharedPreferences("manga_bootstrap", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("default_source_installed", false)) return

        // Zaten kurulu TR kaynak varsa bootstrap atla
        if (mangaSources.any { it.lang.equals("tr", ignoreCase = true) }) {
            prefs.edit().putBoolean("default_source_installed", true).apply()
            return
        }

        // Repo verisinin yüklenmesini bekle (en fazla 15sn)
        val defaultRepo = mangaReposState.firstOrNull {
            it.contains("Kitsugi-Manga-Plugins", ignoreCase = true)
        } ?: return

        var waited = 0
        while (mangaRepoLoadingState[defaultRepo] == true && waited < 15_000) {
            kotlinx.coroutines.delay(500)
            waited += 500
        }

        val extensions = mangaRepoExtensionsState[defaultRepo]
        // Nirvana Manga (MangaThemesia/KeiSource tabanlı, stabil, TR) veya ilk sıradaki TR non-nsfw
        val target = extensions?.firstOrNull { it.pkg.contains("nirvanamanga", ignoreCase = true) && !it.isNsfw }
            ?: extensions?.firstOrNull { it.lang.equals("tr", ignoreCase = true) && !it.isNsfw }
            ?: run {
                Log.w("MangaViewModel", "Varsayılan TR kaynak bulunamadı, bootstrap atlandı")
                return
            }

        Log.i("MangaViewModel", "Varsayılan TR kaynak otomatik kuruluyor: ${target.name}")
        try {
            val success = withContext(Dispatchers.IO) {
                val extensionId = target.pkg.replace(".", "_")
                val downloaded = MangaExtensionLoader.downloadExtension(context, extensionId, target.apkUrl)
                if (downloaded) {
                    MangaExtensionLoader.loadExtension(context, extensionId) != null
                } else false
            }
            if (success) {
                mangaSources = mangaRepository.getInstalledSources()
                onShowMessage?.invoke("✨ Varsayılan manga kaynağı kuruldu: ${target.name}")
                prefs.edit().putBoolean("default_source_installed", true).apply()
            }
        } catch (e: Exception) {
            Log.e("MangaViewModel", "Varsayılan kaynak kurulum hatası: ${e.message}", e)
        }
    }

    fun fetchMangaRepo(repoUrl: String) {
        viewModelScope.launch {
            mangaRepoLoadingState = mangaRepoLoadingState + (repoUrl to true)
            val extensions = mangaRepoClient.fetchExtensionsAutoDetect(repoUrl)
            mangaRepoExtensionsState = mangaRepoExtensionsState + (repoUrl to extensions)
            mangaRepoLoadingState = mangaRepoLoadingState + (repoUrl to false)
            // Bu repo yenilendikten sonra güncelleme durumunu yeniden hesapla
            checkAllUpdates()
        }
    }

    /**
     * Kurulu APK'nın versionCode'unu döndürür.
     * Mihon'daki PackageInfoCompat.getLongVersionCode() ile eşdeğer.
     * @return -1L → kurulu değil veya okunamadı
     */
    fun getInstalledVersionCode(pkg: String): Long =
        MangaExtensionLoader.getInstalledVersionCode(context, pkg)

    /**
     * Uzak repodaki versionCode ile yerel APK'yı karşılaştırır.
     * Mihon'daki hasUpdatedVer / hasUpdatedLib mantığına karşılık gelir.
     */
    fun hasUpdateAvailable(pkg: String, remoteVersionCode: Int): Boolean {
        val local = getInstalledVersionCode(pkg)
        return local >= 0L && remoteVersionCode.toLong() > local
    }

    /**
     * Tüm kayıtlı repolar üzerinden güncelleme kontrolü yapar.
     * Mihon'daki ExtensionApi.checkForUpdates() mantığına karşılık gelir:
     *   installedVer < remoteVer → extensionsWithUpdates'e ekle
     *
     * Uygulama açılışında ve her repo yenilemesinde çağrılır.
     */
    fun checkAllUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatable = mutableSetOf<String>()
            mangaRepoExtensionsState.values.forEach { extensions ->
                extensions?.forEach { ext ->
                    if (hasUpdateAvailable(ext.pkg, ext.versionCode)) {
                        updatable.add(ext.pkg)
                        Log.d("MangaViewModel", "Güncelleme mevcut: ${ext.name} (local=${getInstalledVersionCode(ext.pkg)} < remote=${ext.versionCode})")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                extensionsWithUpdates = updatable
                if (updatable.isNotEmpty()) {
                    Log.i("MangaViewModel", "${updatable.size} eklenti için güncelleme mevcut")
                }
            }
        }
    }

    fun addMangaRepo(urlsInput: String, force: Boolean = false) {
        viewModelScope.launch {
            val newUrls = urlsInput.split(Regex("[,\\n\\r\\s]+"))
                .map { MangaRepoClient.normalizeMangaRepoUrl(it.trim()) }
                .filter { it.isNotBlank() && !mangaReposState.contains(it) }
            if (newUrls.isEmpty()) return@launch

            if (!force) {
                val untrusted = newUrls.firstOrNull { !RepoVerifier.isUrlTrusted(context, it) }
                if (untrusted != null) {
                    untrustedRepoToConfirm = urlsInput
                    return@launch
                }
            }

            untrustedRepoToConfirm = null
            newUrls.forEach { RepoVerifier.trustRepo(context, it) }

            // repo.json varsa meta.signingKeyFingerprint'i güven listesine ekle.
            // Özel repo anahtarı döndüğünde eklentiler "Signature untrusted" olmasın.
            newUrls.forEach { repoUrl ->
                val metaUrl = repoUrl.substringBeforeLast("/") + "/repo.json"
                val meta = mangaRepoClient.fetchRepoMeta(metaUrl)
                if (meta != null) TrustManager.trustRepoSigningKey(context, meta)
            }

            val updatedList = mangaReposState + newUrls
            mangaReposState = updatedList
            saveMangaRepos(updatedList)
            newUrls.forEach { fetchMangaRepo(it) }
        }
    }

    fun deleteMangaRepo(url: String) {
        val updatedList = mangaReposState.filter { it != url }
        mangaReposState = updatedList
        saveMangaRepos(updatedList)
        mangaRepoExtensionsState = mangaRepoExtensionsState - url
        mangaRepoLoadingState = mangaRepoLoadingState - url
    }

    fun installMangaExtension(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = mangaRepository.installExtensionFromUri(uri)
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getInstalledSources()
                if (result.isSuccess) {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.manga_eklentisi_yuklendi_x, result.getOrNull()?.name))
                } else {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.eklenti_yuklenemedi_x, result.exceptionOrNull()?.message))
                }
            }
        }
    }

    fun deleteMangaExtensions(sources: List<MangaSource>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val failures = withContext(Dispatchers.IO) {
                // Resolve all IDs before unloading: one APK can expose several sources.
                val targets = sources.distinctBy {
                    com.kitsugi.animelist.data.manga.MangaExtensionLoader.getExtensionIdForSource(it) ?: it.name
                }
                targets.count { source ->
                    try {
                        !mangaRepository.deleteExtension(source)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        true
                    }
                }
            }
            mangaSources = withContext(Dispatchers.IO) { mangaRepository.getInstalledSources() }
            onComplete(failures)
        }
    }

    fun deleteMangaExtension(source: MangaSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasBuiltIn = source.isBuiltIn
            mangaRepository.deleteExtension(source)
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getInstalledSources()
                if (wasBuiltIn) {
                    onShowMessage?.invoke("⏸️ Varsayılan dahili kaynak devre dışı bırakıldı: ${source.name}")
                } else {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.eklenti_kaldirildi_x_5014f43, source.name))
                }
            }
        }
    }

    /**
     * Kurulu tüm manga eklentilerini (APK) tek seferde kaldırır.
     * Video eklentilerindeki "Tümünü Sil" davranışının manga karşılığıdır.
     */
    fun uninstallAllMangaExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = mangaRepository.getAvailableSources()
            if (installed.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onShowMessage?.invoke(KitsugiStrings.get(R.string.eklenti_toplu_sil_bos))
                }
                return@launch
            }
            var removed = 0
            installed.forEach { source ->
                runCatching { mangaRepository.deleteExtension(source) }
                    .onSuccess { if (it) removed++ }
            }
            withContext(Dispatchers.Main) {
                mangaSources = mangaRepository.getAvailableSources()
                checkAllUpdates()
                onShowMessage?.invoke(KitsugiStrings.get(R.string.manga_eklenti_toplu_sil_sonuc, removed))
            }
        }
    }

    /**
     * Manga > Kurulu sekmesindeki "Toplu Sil" butonunun tek giriş noktası.
     *
     * Eski akış görünür `mangaSources` listesindeki her kaynak için `deleteExtension`
     * çağırıyordu; listedekilerin tamamı uygulama içi Kotatsu kaynağı olduğunda her
     * çağrı `false` dönüyor ve kullanıcı "butona basılmıyor" sanıyordu. Yeni akış
     * [MangaSourceRepository.deleteAllExtensions] ile diski temizler, yerleşik
     * kaynakları geri getirilebilir şekilde gizler ve her durumda sonucu bildirir.
     */
    fun deleteAllMangaExtensions() {
        if (mangaIsBulkDeleting) return
        mangaIsBulkDeleting = true
        viewModelScope.launch {
            // Hata durumunda bile bayrağı sıfırla; aksi halde buton bir daha tepki vermez.
            try {
                val summary = try {
                    withContext(Dispatchers.IO) { mangaRepository.deleteAllExtensions() }
                } catch (t: Throwable) {
                    null
                }
                mangaSources = mangaRepository.getAvailableSources()
                mangaBulkDeleteHiddenBuiltInCount = mangaRepository.getBulkDeleteHiddenBuiltInCount()
                // Silinen eklentiler için "güncelleme var" sayacı bayat kalmasın.
                checkAllUpdates()
                onShowMessage?.invoke(
                    when {
                        summary == null -> KitsugiStrings.get(R.string.manga_bulk_delete_failed)
                        summary.failedExtensions > 0 -> KitsugiStrings.get(
                            R.string.manga_bulk_delete_partial,
                            summary.deletedExtensions,
                            summary.failedExtensions,
                        )
                        summary.totalRemoved == 0 -> KitsugiStrings.get(R.string.manga_bulk_delete_empty)
                        else -> KitsugiStrings.get(
                            R.string.manga_bulk_delete_result,
                            summary.deletedExtensions,
                            summary.hiddenBuiltIns,
                        )
                    }
                )
            } finally {
                mangaIsBulkDeleting = false
            }
        }
    }

    /** "Toplu Sil" ile gizlenen yerleşik manga kaynaklarını geri getirir. */
    fun restoreBulkDeletedMangaBuiltIns() {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { mangaRepository.restoreBulkDeletedBuiltIns() }
            mangaSources = mangaRepository.getAvailableSources()
            mangaBulkDeleteHiddenBuiltInCount = mangaRepository.getBulkDeleteHiddenBuiltInCount()
            onShowMessage?.invoke(KitsugiStrings.get(R.string.manga_bulk_restore_result, restored))
        }
    }

    fun toggleMangaSource(source: MangaSource, enabled: Boolean) {
        viewModelScope.launch {
            mangaRepository.setSourceEnabled(source, enabled)
            mangaSources = mangaRepository.getInstalledSources()
            onShowMessage?.invoke(
                if (enabled) KitsugiStrings.get(R.string.eklenti_etkinlestirildi_x, source.name)
                else KitsugiStrings.get(R.string.manga_source_disabled, source.name)
            )
        }
    }

    fun installMangaApk(ext: MangaExtensionInfo, onResult: ((Boolean) -> Unit)?) {
        installMangaApk(ext, force = false, onResult = onResult)
    }

    fun installMangaApk(ext: MangaExtensionInfo, force: Boolean, onResult: ((Boolean) -> Unit)? = null, approvedSignature: String? = null) {
        viewModelScope.launch {
            val extensionId = ext.pkg.replace(".", "_")
            var signatureHashToPrompt: String? = null
            
            val success = withContext(Dispatchers.IO) {
                MangaExtensionLoader.installWithRollback(context, extensionId) {
                    val downloaded = MangaExtensionLoader.downloadExtension(
                        context, extensionId, ext.apkUrl,
                        expectedPackage = ext.pkg, expectedVersionCode = ext.versionCode.toLong()
                    )
                    if (downloaded) {
                        val apkFile = MangaExtensionLoader.apkFile(context, extensionId)
                        val sigHash = TrustManager.getApkSignatureHash(context, apkFile)
                        if (sigHash != null) {
                            if (force && (approvedSignature == null || sigHash.equals(approvedSignature, ignoreCase = true))) {
                                TrustManager.trustSignature(context, sigHash)
                            } else if (!TrustManager.isSignatureTrusted(context, sigHash)) {
                                signatureHashToPrompt = sigHash
                                return@installWithRollback false
                            }
                        }
                        try {
                            MangaExtensionLoader.unloadExtension(context, extensionId)
                            MangaExtensionLoader.loadExtension(context, extensionId) != null
                        } catch (e: SecurityException) {
                            val hash = e.message?.substringAfterLast(": ")?.trim() ?: ""
                            signatureHashToPrompt = hash.ifEmpty { sigHash ?: "" }
                            false
                        }
                    } else false
                }
            }

            if (signatureHashToPrompt != null && !force) {
                untrustedSignatureToConfirm = Pair(ext, signatureHashToPrompt!!)
                onResult?.invoke(false)
            } else {
                untrustedSignatureToConfirm = null
                mangaSources = mangaRepository.getInstalledSources()
                onResult?.invoke(success)
                val detail = MangaExtensionLoader.getLastInstallError(extensionId)
                onShowMessage?.invoke(
                    if (success) KitsugiStrings.get(R.string.yuklendi_x, ext.name)
                    else "${ext.name}: ${detail ?: "İndirme başarısız veya APK paket/sürüm bilgisi katalogla uyuşmuyor."}"
                )
                if (success) checkAllUpdates()
            }
        }
    }

    fun installAllMangaExtensions(repoUrl: String, extensions: List<MangaExtensionInfo>) {
        viewModelScope.launch {
            val toInstall = extensions.filter { ext ->
                mangaSources.none { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name }
            }
            if (toInstall.isEmpty()) {
                onShowMessage?.invoke(KitsugiStrings.get(R.string.bu_repodaki_tum_eklentiler_zaten_kurulu))
                return@launch
            }

            // İmza istisnası yalnızca elle eklenmiş üçüncü parti repolarda.
            val trustCustomSignature = allowCustomSignature(repoUrl)

            mangaBulkInstallRepoUrl = repoUrl
            mangaBulkInstallTotal = toInstall.size
            mangaBulkInstallDone = 0
            mangaBulkInstallCurrentName = toInstall.firstOrNull()?.name ?: ""
            notifyMangaBulkProgress()

            val semaphore = kotlinx.coroutines.sync.Semaphore(2) // Max 2 parallel downloads
            val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val successCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val failedCounter = java.util.concurrent.atomic.AtomicInteger(0)

            val jobs = toInstall.map { ext ->
                launch {
                    semaphore.acquire()
                    try {
                        val extensionId = ext.pkg.replace(".", "_")
                        val success = withContext(Dispatchers.IO) {
                            try {
                                MangaExtensionLoader.installWithRollback(context, extensionId) {
                                    val downloaded = MangaExtensionLoader.downloadExtension(
                                        context, extensionId, ext.apkUrl,
                                        expectedPackage = ext.pkg, expectedVersionCode = ext.versionCode.toLong()
                                    )
                                    if (downloaded) {
                                        MangaExtensionLoader.unloadExtension(context, extensionId)
                                        MangaExtensionLoader.loadExtension(context, extensionId, trustSignature = trustCustomSignature) != null
                                    } else false
                                }
                            } catch (e: SecurityException) {
                                Log.w("MangaViewModel", "${ext.name} imza hatası: ${e.message}")
                                false
                            } catch (e: Exception) {
                                Log.w("MangaViewModel", "${ext.name} kurulum hatası: ${e.message}")
                                false
                            }
                        }
                        if (success) successCounter.incrementAndGet() else failedCounter.incrementAndGet()
                        val completedCount = doneCounter.incrementAndGet()
                        mangaBulkInstallCurrentName = ext.name
                        mangaBulkInstallDone = completedCount
                        notifyMangaBulkProgress()
                        mangaSources = mangaRepository.getInstalledSources()
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.forEach { it.join() }

            val installedMsg = if (failedCounter.get() == 0) {
                "✅ ${successCounter.get()} eklenti başarıyla kuruldu!"
            } else {
                "✅ ${successCounter.get()} kuruldu, ⚠️ ${failedCounter.get()} kurulamadı. İmza onayı gereken eklentileri tek tek kurun."
            }
            onShowMessage?.invoke(installedMsg)
            finishMangaBulkNotification(installedMsg, success = successCounter.get() > 0)
            mangaBulkInstallRepoUrl = null
            mangaBulkInstallDone = 0
            mangaBulkInstallTotal = 0
            mangaBulkInstallCurrentName = ""
            checkAllUpdates()
        }
    }

    fun updateAllMangaExtensions(repoUrl: String, extensions: List<MangaExtensionInfo>) {
        viewModelScope.launch {
            val toUpdate = extensions.filter { ext ->
                mangaSources.any { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name } &&
                    hasUpdateAvailable(ext.pkg, ext.versionCode)
            }
            if (toUpdate.isEmpty()) {
                onShowMessage?.invoke(KitsugiStrings.get(R.string.guncellenecek_eklenti_bulunamadi))
                return@launch
            }

            // İmza istisnası yalnızca elle eklenmiş üçüncü parti repolarda.
            val trustCustomSignature = allowCustomSignature(repoUrl)

            mangaBulkInstallRepoUrl = repoUrl
            mangaBulkInstallTotal = toUpdate.size
            mangaBulkInstallDone = 0
            mangaBulkInstallCurrentName = toUpdate.firstOrNull()?.name ?: ""
            notifyMangaBulkProgress()

            val semaphore = kotlinx.coroutines.sync.Semaphore(2) // Max 2 parallel downloads
            val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val successCounter = java.util.concurrent.atomic.AtomicInteger(0)

            val jobs = toUpdate.map { ext ->
                launch {
                    semaphore.acquire()
                    try {
                        val extensionId = ext.pkg.replace(".", "_")
                        val success = withContext(Dispatchers.IO) {
                            try {
                                MangaExtensionLoader.installWithRollback(context, extensionId) {
                                    val downloaded = MangaExtensionLoader.downloadExtension(
                                        context, extensionId, ext.apkUrl,
                                        expectedPackage = ext.pkg, expectedVersionCode = ext.versionCode.toLong()
                                    )
                                    if (downloaded) {
                                        MangaExtensionLoader.unloadExtension(context, extensionId)
                                        MangaExtensionLoader.loadExtension(context, extensionId, trustSignature = trustCustomSignature) != null
                                    } else false
                                }
                            } catch (e: SecurityException) {
                                Log.w("MangaViewModel", "${ext.name} imza hatası: ${e.message}")
                                false
                            } catch (e: Exception) {
                                Log.w("MangaViewModel", "${ext.name} kurulum hatası: ${e.message}")
                                false
                            }
                        }
                        if (success) {
                            successCounter.incrementAndGet()
                        }
                        val completedCount = doneCounter.incrementAndGet()
                        mangaBulkInstallCurrentName = ext.name
                        mangaBulkInstallDone = completedCount
                        notifyMangaBulkProgress()
                        mangaSources = mangaRepository.getInstalledSources()
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.forEach { it.join() }

            val updatedMsg = "✅ ${successCounter.get()} eklenti başarıyla güncellendi!"
            onShowMessage?.invoke(updatedMsg)
            finishMangaBulkNotification(updatedMsg, success = successCounter.get() > 0)
            mangaBulkInstallRepoUrl = null
            mangaBulkInstallDone = 0
            mangaBulkInstallTotal = 0
            mangaBulkInstallCurrentName = ""
            checkAllUpdates()
        }
    }

    fun getInstalledMangaExtensionVersion(pkg: String): String? {
        val extensionId = pkg.replace(".", "_")
        val apkFile = File(MangaExtensionLoader.extensionDir(context), "$extensionId.apk")
        if (!apkFile.exists()) return null
        return try {
            val pm = context.packageManager
            val flags = 0
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
            packageInfo?.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun getSourceHealthStatus(source: MangaSource): SourceHealthStatus =
        mangaRepository.getSourceHealthStatus(source)

    fun getSourceRuntimeStats(source: MangaSource): SourceRuntimeStats =
        mangaRepository.getSourceRuntimeStats(source)

    fun getSourceFailureStreak(source: MangaSource): Int =
        mangaRepository.getSourceFailureStreak(source)

    fun getSourceCooldownUntil(source: MangaSource): Long =
        mangaRepository.getSourceCooldownUntil(source)

    fun getSourceLastErrorCode(source: MangaSource): String? =
        mangaRepository.getSourceLastErrorCode(source)

    fun getSourceLastReason(source: MangaSource): String? =
        mangaRepository.getSourceLastReason(source)

    fun getConfiguredSourceDomain(source: MangaSource): String? =
        mangaRepository.getConfiguredDomain(source)

    fun getConfiguredSourceBaseUrl(source: MangaSource): String =
        mangaRepository.getConfiguredBaseUrl(source)

    fun getSourceUserAgentOverride(source: MangaSource): String? =
        mangaRepository.getSourceUserAgentOverride(source)

    fun getSourceSlowdownEnabled(source: MangaSource): Boolean =
        mangaRepository.getSourceSlowdownEnabled(source)

    fun setSourceDomainOverride(source: MangaSource, value: String?) {
        val ok = mangaRepository.setConfiguredDomain(source, value)
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(
            when {
                value.isNullOrBlank() -> "🧹 Domain temizlendi: ${source.name}"
                ok -> "🌐 Domain kaydedildi: ${source.name}"
                else -> "❌ Geçersiz domain: ${source.name}"
            }
        )
    }

    fun setSourceUserAgentOverride(source: MangaSource, value: String?) {
        val ok = mangaRepository.setSourceUserAgentOverride(source, value)
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(
            when {
                value.isNullOrBlank() -> "🧹 User-Agent temizlendi: ${source.name}"
                ok -> "💾 User-Agent kaydedildi: ${source.name}"
                else -> "❌ Geçersiz User-Agent: ${source.name}"
            }
        )
    }

    fun setSourceSlowdownEnabled(source: MangaSource, enabled: Boolean) {
        mangaRepository.setSourceSlowdownEnabled(source, enabled)
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(
            if (enabled) KitsugiStrings.get(R.string.slowdown_acildi_x, source.name) else KitsugiStrings.get(R.string.slowdown_kapatildi_x, source.name)
        )
    }

    fun resetSourceDiagnostics(source: MangaSource) {
        mangaRepository.resetSourceDiagnostics(source)
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.source_istatistikleri_sifirlandi_x, source.name))
    }

    fun clearAllSourceDiagnostics() {
        mangaRepository.clearAllSourceDiagnostics()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.tum_source_rapor_verileri_temizlendi))
    }

    fun clearAllSourceConfigs() {
        mangaRepository.clearAllSourceConfigs()
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.tum_source_config_ayarlari_temizlendi))
    }

    fun isSourceBusy(source: MangaSource): Boolean =
        source.stableSourceKey() in mangaSourceBusyKeys

    /**
     * Belleğe yüklenmiş manga kaynaklarını yeniden okur.
     * Eklentiler uygulama açılışında arka planda yüklendiği için, Eklenti Portalı
     * gibi ekranlar Manga sekmesine geçerken listeyi tazelemek üzere çağırır.
     */
    fun refreshMangaSources() {
        mangaSources = mangaRepository.getInstalledSources()
    }

    fun quickCheckSource(source: MangaSource) {
        val key = source.stableSourceKey()
        if (key in mangaSourceBusyKeys) return
        mangaSourceBusyKeys = mangaSourceBusyKeys + key
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    mangaRepository.quickCheckSourceHealth(source)
                }
            }
            mangaSources = mangaRepository.getInstalledSources()
            mangaSourceBusyKeys = mangaSourceBusyKeys - key
            result.onSuccess { status ->
                onShowMessage?.invoke("🔎 ${source.name}: ${status.name}")
            }.onFailure { error ->
                onShowMessage?.invoke("❌ ${source.name}: ${error.message ?: "Sağlık testi başarısız"}")
            }
        }
    }

    fun refreshSourceMirror(source: MangaSource) {
        val key = source.stableSourceKey()
        if (key in mangaSourceBusyKeys) return
        mangaSourceBusyKeys = mangaSourceBusyKeys + key
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    mangaRepository.tryRefreshSourceMirror(source)
                }
            }
            mangaSources = mangaRepository.getInstalledSources()
            mangaSourceBusyKeys = mangaSourceBusyKeys - key
            result.onSuccess { success ->
                onShowMessage?.invoke(
                    if (success) KitsugiStrings.get(R.string.mirror_guncellendi_x, source.name)
                    else "⚠️ Mirror bulunamadı: ${source.name}"
                )
            }.onFailure { error ->
                onShowMessage?.invoke("❌ ${source.name}: ${error.message ?: "Mirror testi başarısız"}")
            }
        }
    }

    fun clearSourceMirror(source: MangaSource) {
        mangaRepository.clearConfiguredDomain(source)
        mangaSources = mangaRepository.getInstalledSources()
        onShowMessage?.invoke(KitsugiStrings.get(R.string.domain_temizlendi_x, source.name))
    }

    fun forceCheckMangaUpdates(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = MangaExtensionAutoUpdater.runIfNeeded(context, forceCheck = true)
            val msg = when (result) {
                is MangaExtensionAutoUpdater.UpdateResult.Success -> {
                    mangaSources = mangaRepository.getInstalledSources()
                    checkAllUpdates()
                    if (result.updated > 0) {
                        KitsugiStrings.get(
                            R.string.manga_auto_update_success,
                            result.updated,
                            result.updatedNames.joinToString(", ")
                        )
                    } else if (result.checked > 0) {
                        KitsugiStrings.get(R.string.manga_auto_update_all_current, result.checked)
                    } else {
                        KitsugiStrings.get(R.string.manga_auto_update_no_updates)
                    }
                }
                is MangaExtensionAutoUpdater.UpdateResult.Skipped -> {
                    KitsugiStrings.get(R.string.manga_auto_update_skipped, result.minutesUntilNextCheck)
                }
                is MangaExtensionAutoUpdater.UpdateResult.Failed -> {
                    val reason = when (result.reasonCode) {
                        "REPO_FETCH_FAILED" -> KitsugiStrings.get(R.string.manga_auto_update_repo_fetch_failed)
                        else -> result.reasonCode
                    }
                    KitsugiStrings.get(R.string.manga_auto_update_failed, reason)
                }
            }
            onResult(msg)
        }
    }
}
