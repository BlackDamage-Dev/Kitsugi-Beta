package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Öğrenen eklenti-URL hafızası — ortak kümenin kalıcı katmanı.
 *
 * Bir CloudStream eklentisi bir eserin içerik sayfasını BİR KEZ bulduğunda
 * (hangi yoldan olursa olsun: eklentinin kendi aramasıyla gelen doğrudan URL,
 * native `getLoadUrl()` kimlik çözümlemesi veya başlık araması+eşleme),
 * o URL eserin kanonik kimlik anahtarlarına ([CsUnifiedIdentity.aliasKeys])
 * kaydedilir. Sonraki isteklerde — kullanıcı esere hangi katalog kaynağından
 * girerse girsin — çözüm sırası şudur:
 *
 *   1. Hafıza: kayıtlı URL doğrudan `load` edilir (arama/eşleme yok — en hızlı, en kesin)
 *   2. Native ID: eklentinin `supportedSyncNames`/`getLoadUrl` yolu
 *   3. Başlık arama + eşleme: bugünkü davranış (son çare)
 *
 * Bu, Torrentio/Stremio eklentilerinin "kimlik → doğrudan sonuç" determinizmini
 * kazıyan (scraper) CloudStream eklentilerine uygulama tarafından kazandırır:
 * eklenti değişmez, ama bir kez kanıtlanmış eşleşme bir daha aranmaz.
 *
 * Güvenlik: hafızadaki URL artık çalışmıyorsa kayıt düşürülür ve alt yollara
 * geçilir — hafıza asla bir engel hâline gelmez, yalnızca bir kestirmedir.
 * Tüm işlemler sessizce başarısız olur (runCatching); hafıza bozuksa uygulama
 * bugünkü davranışıyla çalışmaya devam eder.
 */
internal object CsProviderUrlMemory {

    private const val TAG = "CsProviderUrlMemory"
    private const val PREFS_NAME = "cs_provider_url_memory"
    private const val MAX_ENTRIES = 1_500

    /** slot -> (url, kaydedilme zamanı) */
    private val memory = ConcurrentHashMap<String, Pair<String, Long>>()

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    /** Uygulama açılışında (CsRuntimeInit) bir kez çağrılır; kalıcı katmanı yükler. */
    fun init(context: Context) {
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            for ((slot, raw) in sp.all) {
                val value = raw as? String ?: continue
                val sep = value.indexOf('|')
                if (sep <= 0) continue
                val savedAt = value.substring(0, sep).toLongOrNull() ?: continue
                val url = value.substring(sep + 1)
                if (isUsableUrl(url)) memory[slot] = url to savedAt
            }
            Log.d(TAG, "Hafıza yüklendi: ${memory.size} kayıt")
        } catch (e: Throwable) {
            Log.w(TAG, "Hafıza başlatılamadı (yalnızca oturum içi çalışılacak): ${e.message}")
        }
    }

    private fun slot(aliasKey: String, providerName: String): String =
        aliasKey + "|" + providerName.trim().lowercase(Locale.ROOT)

    private fun isUsableUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.length in 9..2_048 &&
            (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true))
    }

    /**
     * Kanıtlanmış bir (kimlik kümesi, eklenti) → içerik URL'si eşleşmesini kaydeder.
     * Anahtarların HEPSİNE yazılır; böylece eser başka kaynaktan, başka bir
     * kimlikle gelse bile aynı kayda ulaşılır.
     */
    fun remember(aliasKeys: List<String>, providerName: String, url: String) {
        if (aliasKeys.isEmpty() || providerName.isBlank() || !isUsableUrl(url)) return
        val cleanUrl = url.trim()
        val now = System.currentTimeMillis()
        try {
            val editor = prefs?.edit()
            for (aliasKey in aliasKeys) {
                val s = slot(aliasKey, providerName)
                memory[s] = cleanUrl to now
                editor?.putString(s, "$now|$cleanUrl")
            }
            editor?.apply()
            pruneIfNeeded()
        } catch (e: Throwable) {
            Log.w(TAG, "remember başarısız: ${e.message}")
        }
    }

    /** Bu kimlik kümesi + eklenti için daha önce kanıtlanmış içerik URL'si; yoksa null. */
    fun recall(aliasKeys: List<String>, providerName: String): String? {
        if (aliasKeys.isEmpty() || providerName.isBlank()) return null
        for (aliasKey in aliasKeys) {
            memory[slot(aliasKey, providerName)]?.let { return it.first }
        }
        return null
    }

    /** Ölü/eski olduğu kanıtlanan kaydı düşürür; alt çözüm yolları devreye girer. */
    fun forget(aliasKeys: List<String>, providerName: String) {
        if (aliasKeys.isEmpty() || providerName.isBlank()) return
        try {
            val editor = prefs?.edit()
            for (aliasKey in aliasKeys) {
                val s = slot(aliasKey, providerName)
                memory.remove(s)
                editor?.remove(s)
            }
            editor?.apply()
        } catch (e: Throwable) {
            Log.w(TAG, "forget başarısız: ${e.message}")
        }
    }

    private fun pruneIfNeeded() {
        if (memory.size <= MAX_ENTRIES) return
        try {
            val overflow = memory.size - MAX_ENTRIES
            val oldest = memory.entries.sortedBy { it.value.second }.take(overflow)
            val editor = prefs?.edit()
            for ((s, _) in oldest) {
                memory.remove(s)
                editor?.remove(s)
            }
            editor?.apply()
        } catch (_: Throwable) {
        }
    }

    /** Yalnızca testler için: oturum içi + kalıcı tüm kayıtları temizler. */
    internal fun clearAllForTest() {
        memory.clear()
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (_: Throwable) {
        }
    }
}
