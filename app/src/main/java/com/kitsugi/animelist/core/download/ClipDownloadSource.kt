package com.kitsugi.animelist.core.download

import android.util.Log
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import com.kitsugi.animelist.data.trailer.TrailerPlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Detay sayfasındaki küçük videoların (fragman / kamera arkası / tema) **indirilebilir**
 * kaynağını çözer.
 *
 * ## Neden ayrı bir çözümleyici?
 * Detay sayfasındaki video bağlantıları çoğunlukla YouTube `watch` sayfalarıdır; doğrudan
 * indirilemezler. [InAppYouTubeExtractor] oynatma için muxed HLS ya da ayrı video/ses
 * akışları döndürür. Ayrı ses/video akışları **tek dosyaya birleştirilemediği** için
 * (uygulamada muxer yok) indirme sessiz bir video üretirdi. Bu yüzden:
 *
 *  - muxed HLS manifest (`hlsManifestUrl`) → indirilebilir (segmentler birleştirilir),
 *  - progressive (ses+görüntü birlikte mp4) → indirilebilir,
 *  - yalnızca ayrı adaptive video + ses → **indirilemez**, kullanıcıya net mesaj gösterilir.
 *
 * Bu karar kaynak çözümleme anında verilir; indirme başladıktan sonra "ses yok" sürprizi
 * yaşanmaz ve yarım dosya diskte kalmaz.
 */
object ClipDownloadSource {

    private const val TAG = "ClipDownloadSource"

    /** İndirme için ayrılması gereken en az boş alan (dosya boyutu bilinmiyorsa). */
    private const val MIN_FREE_BYTES = 96L * 1024L * 1024L

    /** İndirilebilir bir akış. */
    data class Resolved(
        /** Doğrudan indirilecek URL (HLS manifesti veya progressive medya). */
        val url: String,
        /** Dosya uzantısı: `mp4`, `webm`, `m4v` veya HLS için `ts`. */
        val extension: String,
        /** URL bir HLS manifesti mi? (segment segment indirilir) */
        val isHls: Boolean,
        /** YouTube video kimliği (varsa) — tekil anahtar ve günlükler için. */
        val youtubeVideoId: String? = null
    )

    /** Çözümleme başarısızlık nedenleri — UI'da yerelleştirilmiş mesaja çevrilir. */
    enum class Failure {
        /** Bağlantı boş/geçersiz veya desteklenmeyen bir şema. */
        UNSUPPORTED_URL,
        /** Kaynakta indirilebilir akış yok (canlı yayın, kaldırılmış video, bölge kilidi…). */
        NO_STREAM,
        /** Yalnızca ayrı video + ses akışları var; tek dosyaya birleştirilemiyor. */
        SEPARATE_AUDIO,
        /** Çözümleme sırasında ağ hatası. */
        NETWORK
    }

    sealed class Result {
        data class Success(val resolved: Resolved) : Result()
        data class Error(val failure: Failure, val detail: String? = null) : Result()
    }

    // ── URL sınıflandırma ─────────────────────────────────────────────────────

    private val YOUTUBE_HOST_HINTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /** Bağlantı YouTube (veya kısa youtu.be) adresi mi? */
    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        if (lower.isBlank()) return false
        if (YOUTUBE_HOST_HINTS.any { lower.contains(it) }) return true
        // Çıplak 11 karakterlik video kimliği de kabul edilir.
        return runCatching { InAppYouTubeExtractor.shared.extractVideoId(url) }.getOrNull() != null &&
            !lower.startsWith("http")
    }

    /** YouTube video kimliği; YouTube değilse `null`. */
    fun youtubeVideoId(url: String): String? = runCatching {
        if (!isYouTubeUrl(url)) null else InAppYouTubeExtractor.shared.extractVideoId(url.trim())
    }.getOrNull()

    /**
     * Tekilleştirme anahtarı: YouTube ise video kimliği, değilse sorgu parametreleri
     * atılmış URL. Aynı videonun iki farklı bağlantısı (`watch?v=`, `youtu.be/`, `&t=`)
     * böylece **aynı** anahtara düşer ve ikinci kez indirilmez.
     */
    fun identityKey(url: String): String {
        val trimmed = url.trim()
        youtubeVideoId(trimmed)?.let { return "yt:$it" }
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        return "url:${withoutQuery.ifBlank { trimmed }.lowercase(Locale.ROOT)}"
    }

    private fun guessExtension(url: String): String {
        val path = runCatching { url.substringBefore('?').substringBefore('#') }.getOrDefault(url)
        val ext = File(path).extension.lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "m4v", "webm", "mkv", "mov", "ts" -> ext
            else -> "mp4"
        }
    }

    // ── Çözümleme ─────────────────────────────────────────────────────────────

    /**
     * Verilen bağlantıyı indirilebilir akışa çevirir. Ağ erişimi içerir → IO'da çalışır.
     *
     * @param url detay sayfasındaki video bağlantısı (YouTube watch/be ya da doğrudan medya)
     */
    suspend fun resolve(url: String): Result = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext Result.Error(Failure.UNSUPPORTED_URL)

        if (!isYouTubeUrl(trimmed)) {
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                return@withContext Result.Error(Failure.UNSUPPORTED_URL)
            }
            val isHls = trimmed.contains(".m3u8", ignoreCase = true)
            return@withContext Result.Success(
                Resolved(
                    url = trimmed,
                    extension = if (isHls) "ts" else guessExtension(trimmed),
                    isHls = isHls
                )
            )
        }

        val playback: TrailerPlaybackSource? = try {
            InAppYouTubeExtractor.shared.extractPlaybackSource(trimmed)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Kaynak çözümlenemedi (${identityKey(trimmed)}): ${e.message}")
            return@withContext Result.Error(Failure.NETWORK, e.message)
        }

        val videoUrl = playback?.videoUrl?.trim().orEmpty()
        if (playback == null || videoUrl.isBlank()) {
            return@withContext Result.Error(Failure.NO_STREAM)
        }

        val videoId = youtubeVideoId(trimmed)

        // 1) Muxed HLS: ses + görüntü tek manifestte → segmentler birleştirilip .ts yazılır.
        if (videoUrl.contains(".m3u8", ignoreCase = true)) {
            return@withContext Result.Success(
                Resolved(url = videoUrl, extension = "ts", isHls = true, youtubeVideoId = videoId)
            )
        }

        // 2) Ayrı ses akışı varsa video tek başına sessiz olur → indirmeyi reddet.
        if (!playback.audioUrl.isNullOrBlank()) {
            Log.i(TAG, "Ayrı ses akışı var, muxed akış yok: ${identityKey(trimmed)}")
            return@withContext Result.Error(Failure.SEPARATE_AUDIO)
        }

        // 3) Progressive (ses+görüntü birlikte) akış.
        Result.Success(
            Resolved(
                url = videoUrl,
                extension = guessExtension(videoUrl),
                isHls = false,
                youtubeVideoId = videoId
            )
        )
    }

    // ── Depolama ön kontrolü ──────────────────────────────────────────────────

    /**
     * İndirme başlamadan önce boş alan kontrolü.
     *
     * @param requiredBytes bilinen içerik uzunluğu (0 → yalnızca alt sınır kontrol edilir)
     * @param targetDir dosyanın yazılacağı klasör (farklı birim olabilir)
     */
    fun hasFreeSpace(targetDir: File, requiredBytes: Long): Boolean {
        val usable = runCatching {
            var dir = targetDir
            while (!dir.exists() && dir.parentFile != null) {
                dir = dir.parentFile!!
            }
            dir.usableSpace
        }.getOrDefault(0L)
        if (usable <= 0L) return true // ölçülemiyorsa indirmeyi engelleme
        val needed = if (requiredBytes > 0) requiredBytes + MIN_FREE_BYTES else MIN_FREE_BYTES
        return usable > needed
    }
}
