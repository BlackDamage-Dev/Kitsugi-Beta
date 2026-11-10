package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.player.SubtitleInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * mpv harici altyazılarını ANA İŞ PARÇACIĞINI KİLİTLEMEDEN yüklemek için ön-indirici.
 *
 * SORUN (v2.4.369 raporları — MIUI `APP_SCOUT_WARNING` 2,5 sn / `APP_SCOUT_HANG` 5 sn):
 * `sub-add <uzak URL>` senkron bir libmpv komutudur; altyazı dosyası ağdan inene kadar
 * çağıran iş parçacığı bekler. Bu çağrı `MPV_EVENT_FILE_LOADED` işleyicisinde ANA iş
 * parçacığında, her altyazı için sırayla yapılıyordu → arayüz donuyor, 5 sn'yi aşınca ANR.
 *
 * Komutun kendisini arka plana taşımak güvenli değil: aynı mpv tutamacı arka planda
 * kullanılırken oynatıcı kapanışında ana iş parçacığında `destroy()` çağrılırsa serbest
 * bırakılmış belleğe erişim (native çökme) riski doğar. Bu yüzden uzak dosya önce IO'da
 * indirilir ve mpv'ye YEREL dosya olarak verilir (yerel `sub-add` milisaniyeler sürer).
 * Yerel / `content://` / `fd://` kaynaklar olduğu gibi geçer.
 */
internal object MpvSubtitlePrefetcher {

    private const val TAG = "MpvSubtitlePrefetch"
    private const val DIR_NAME = "mpv_subtitles"
    private const val MAX_PARALLEL_DOWNLOADS = 3
    private const val MAX_SUBTITLE_BYTES = 20L * 1024 * 1024
    private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 300

    private val client: OkHttpClient by lazy {
        com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Video isteğinin başlıklarından altyazı isteğine TAŞINMAMASI gerekenler. */
    private val DROPPED_HEADERS = setOf("range", "accept-encoding", "content-length", "host", "connection")

    /** mpv'nin içerik yoklamasına ek ipucu olarak korunan uzantılar; bilinmeyenler `srt` olur. */
    private val KNOWN_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "dfxp", "smi", "sami", "lrc", "sup")

    fun isRemote(url: String): Boolean {
        val lower = url.trimStart().lowercase(Locale.ROOT)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /**
     * Uzak altyazıları önbelleğe indirir; yerel kaynaklar aynen döner. Sıra korunur.
     * İndirilemeyen uzak altyazılar listeden çıkarılır: mpv onları zaten yükleyemezdi ve
     * ana iş parçacığında yeniden denemek donmayı geri getirirdi.
     */
    suspend fun materialize(
        context: Context,
        subtitles: List<SubtitleInput>,
        headers: Map<String, String>
    ): List<Pair<SubtitleInput, String>> = withContext(Dispatchers.IO) {
        if (subtitles.isEmpty()) return@withContext emptyList()
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        pruneCache(dir)
        val forwardedHeaders = headers.filterKeys { it.lowercase(Locale.ROOT) !in DROPPED_HEADERS }
        val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
        coroutineScope {
            subtitles.map { sub ->
                async {
                    if (sub.url.isBlank()) return@async null
                    if (!isRemote(sub.url)) return@async sub to sub.url
                    gate.withPermit {
                        download(dir, sub.url, forwardedHeaders)?.let { file -> sub to file.absolutePath }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun download(dir: File, url: String, headers: Map<String, String>): File? {
        val target = File(dir, fileNameFor(url))
        // Aynı URL (ör. yedek motora geçiş / yeniden deneme) ağa ikinci kez gitmez.
        if (target.isFile && target.length() > 0L) return target
        val partial = File(dir, target.name + ".part")
        return try {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Altyazı indirilemedi (HTTP ${response.code}): ${redact(url)}")
                    return null
                }
                val body = response.body ?: return null
                if (body.contentLength() > MAX_SUBTITLE_BYTES) {
                    Log.w(TAG, "Altyazı çok büyük (${body.contentLength()} bayt), atlandı: ${redact(url)}")
                    return null
                }
                body.byteStream().use { input ->
                    partial.outputStream().use { output -> copyLimited(input, output, MAX_SUBTITLE_BYTES) }
                }
            }
            if (partial.length() <= 0L) {
                partial.delete()
                return null
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "Altyazı indirilemedi (${e.javaClass.simpleName}: ${e.message}): ${redact(url)}")
            partial.delete()
            null
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("altyazı $limit bayt sınırını aştı")
            output.write(buffer, 0, read)
        }
    }

    /** 24 saatten eski dosyaları ve sınırı aşan en eski dosyaları siler. */
    private fun pruneCache(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val kept = files.filter { file ->
            if (now - file.lastModified() > CACHE_MAX_AGE_MS) {
                file.delete()
                false
            } else {
                true
            }
        }
        if (kept.size > CACHE_MAX_FILES) {
            kept.sortedBy { it.lastModified() }
                .take(kept.size - CACHE_MAX_FILES)
                .forEach { it.delete() }
        }
    }

    /** URL'ye özgü, dosya sistemi için güvenli ad: SHA-1 öneki + (biliniyorsa) özgün uzantı. */
    internal fun fileNameFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(24)
        return "$hex.${extensionFor(url)}"
    }

    internal fun extensionFor(url: String): String {
        val lastSegment = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val ext = lastSegment.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return when {
            ext == "webvtt" -> "vtt"
            ext in KNOWN_EXTENSIONS -> ext
            else -> "srt"
        }
    }

    /** Günlüklerde belirteç (token) sızmasın: sorgu kısmı atılır. */
    private fun redact(url: String): String = url.substringBefore('?').take(160)
}
