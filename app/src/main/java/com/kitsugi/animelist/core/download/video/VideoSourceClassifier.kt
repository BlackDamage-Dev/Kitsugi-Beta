package com.kitsugi.animelist.core.download.video

import java.net.URI
import java.util.Locale

/** Bir video adresinin hangi indirme yolundan geçmesi gerektiği. */
enum class VideoSourceKind {
    /** youtube.com / youtu.be sayfası — önce akış çözümlenir. */
    YOUTUBE_PAGE,

    /** `.m3u8` — parça parça indirilip tek dosyada birleştirilir. */
    HLS,

    /** `.mpd` — şimdilik yalnızca tek gösterimli (muxed) akışlar indirilir. */
    DASH,

    /** Doğrudan dosya (`.mp4`, `.webm`, `.mkv`, …). */
    DIRECT_FILE,

    /** Uzantı yok/bilinmiyor — `Content-Type` ile karar verilir. */
    UNKNOWN
}

/**
 * Küçük video indirmeleri için adres sınıflandırma ve dosya adı yardımcıları.
 *
 * Tamamı saf Kotlin: ağ yok, Android yok. Bu sayede JVM birim testleriyle doğrulanabilir
 * (`VideoSourceClassifierTest`).
 */
object VideoSourceClassifier {

    private val DIRECT_EXTENSIONS = listOf(
        "mp4", "m4v", "webm", "mkv", "mov", "ts", "m2ts", "flv", "avi", "3gp"
    )

    private val YOUTUBE_SUFFIXES = listOf("youtube.com", "youtube-nocookie.com", "youtu.be")

    private val ILLEGAL_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun kindOf(rawUrl: String?): VideoSourceKind {
        val url = normalizeUrl(rawUrl)
        if (url.isEmpty()) return VideoSourceKind.UNKNOWN

        val host = hostOf(url)
        if (host != null && isYouTubeHost(host)) return VideoSourceKind.YOUTUBE_PAGE

        val path = pathOf(url)
        return when {
            path.endsWith(".m3u8", ignoreCase = true) -> VideoSourceKind.HLS
            path.endsWith(".mpd", ignoreCase = true) -> VideoSourceKind.DASH
            DIRECT_EXTENSIONS.any { path.endsWith(".$it", ignoreCase = true) } -> VideoSourceKind.DIRECT_FILE
            else -> VideoSourceKind.UNKNOWN
        }
    }

    fun isYouTubeUrl(rawUrl: String?): Boolean {
        val host = hostOf(normalizeUrl(rawUrl)) ?: return false
        return isYouTubeHost(host)
    }

    fun isYouTubeHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).trimEnd('.')
        return YOUTUBE_SUFFIXES.any { normalized == it || normalized.endsWith(".$it") }
    }

    /**
     * Adresten dosya uzantısı çıkarır. Bilinmiyorsa [contentType] üzerinden tahmin eder;
     * o da yoksa `mp4` döner (Android'de en güvenli varsayılan).
     */
    fun extensionFor(rawUrl: String?, contentType: String? = null): String {
        val path = pathOf(normalizeUrl(rawUrl))
        val fromPath = DIRECT_EXTENSIONS.firstOrNull { path.endsWith(".$it", ignoreCase = true) }
        if (fromPath != null) return fromPath.lowercase(Locale.ROOT)

        val type = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            type.contains("webm") -> "webm"
            type.contains("matroska") -> "mkv"
            type.contains("mpegurl") || type.contains("m3u8") -> "ts"
            type.contains("dash") -> "mp4"
            type.contains("mp4") || type.contains("m4v") -> "mp4"
            type.contains("quicktime") -> "mov"
            type.contains("mpegts") -> "ts"
            else -> "mp4"
        }
    }

    /** `Content-Type: video/webm` gibi yanıtlardan kapsayıcı tahmini. */
    fun looksLikeWebm(contentType: String?, url: String?): Boolean {
        val type = contentType.orEmpty().lowercase(Locale.ROOT)
        if (type.contains("webm") || type.contains("matroska")) return true
        return pathOf(normalizeUrl(url)).endsWith(".webm", ignoreCase = true)
    }

    /**
     * Başlıktan güvenli bir dosya adı üretir. Yol ayırıcıları, Windows/Android'de
     * sorun çıkaran karakterler ve kontrol karakterleri temizlenir.
     */
    fun sanitizeFileName(raw: String, maxLength: Int = 90): String {
        val cleaned = raw
            .replace(ILLEGAL_FILE_CHARS, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ' ', '-', '_')
        val limited = if (cleaned.length > maxLength) cleaned.take(maxLength).trim() else cleaned
        return limited.ifBlank { "video" }
    }

    /**
     * Medya listelerinde (HLS) göreli adresleri mutlak adrese çevirir.
     * Protokol/şema bozuksa referans olduğu gibi döner; çağıran taraf hata yakalar.
     */
    fun resolveUrl(baseUrl: String, reference: String): String {
        val ref = reference.trim()
        if (ref.isEmpty()) return baseUrl
        if (ref.startsWith("http://", ignoreCase = true) ||
            ref.startsWith("https://", ignoreCase = true)
        ) {
            return ref
        }
        return runCatching {
            URI(normalizeUrl(baseUrl)).resolve(ref.replace(" ", "%20")).toString()
        }.getOrDefault(ref)
    }

    fun hostOf(rawUrl: String?): String? = runCatching {
        URI(normalizeUrl(rawUrl)).host
    }.getOrNull()?.lowercase(Locale.ROOT)?.trimEnd('.')

    private fun pathOf(url: String): String = runCatching {
        URI(url).rawPath.orEmpty()
    }.getOrDefault(url.substringBefore('?').substringBefore('#'))

    private fun normalizeUrl(rawUrl: String?): String = rawUrl?.trim()
        ?.replace(" ", "%20")
        .orEmpty()
}
