package com.kitsugi.animelist.data.repository

import java.util.Locale

/**
 * Sorts streams by quality priority.
 *
 * Ported from KitsugiTV-dev's DirectDebridStreamFilter sort logic.
 *
 * Sort priority (descending):
 *   1. Debrid cache state: Cached > P2P/Direct > Not-cached
 *   2. Resolution: 4K > 1080p > 720p > 480p > unknown
 *   3. Source quality: BluRay Remux > BluRay > WEB-DL > WEBRip > HDTV > unknown
 *   4. Size (bytes, descending — bigger usually = better quality)
 */
object StreamSorter {

    fun sort(streams: List<StreamSource>): List<StreamSource> {
        return streams.sortedWith(
            compareByDescending<StreamSource> { cacheScore(it) }
                .thenByDescending { resolutionScore(it) }
                .thenByDescending { qualityScore(it) }
                // v2.4.340: aynı kalitedeki torrent'ler arasında swarm sağlığı belirleyici.
                // Seeder sayısı bildirilmediyse (-1) sıralama DEĞİŞMEZ, uydurma değer yok.
                .thenByDescending { seeders(it) }
                .thenByDescending { sizeBytes(it) }
        )
    }

    /** Kaynağın bildirdiği seeder sayısı; bilinmiyorsa -1 (sıralamada etkisiz). */
    private fun seeders(stream: StreamSource): Int = stream.swarmStats.seeders ?: -1

    // ── Cache state ────────────────────────────────────────────────────────────

    private fun cacheScore(stream: StreamSource): Int {
        val nameLower = stream.name.lowercase(Locale.ROOT)
        val titleLower = stream.title.lowercase(Locale.ROOT)
        return when {
            // RealDebrid / Torbox / Premiumize cached
            nameLower.contains("[rd+]") || nameLower.contains("rd+") ||
            nameLower.contains("[tb+]") || nameLower.contains("tb+") ||
            nameLower.contains("[pm+]") || nameLower.contains("pm+") ||
            nameLower.contains("[ad+]") || nameLower.contains("ad+") ||
            titleLower.contains("[rd+]") || nameLower.contains("cached") -> 3
            // P2P / torrent (no debrid)
            stream.infoHash != null && stream.url == null -> 2
            // Direct HTTP stream
            !stream.url.isNullOrBlank() -> 2
            // Not cached debrid download
            else -> 1
        }
    }

    // ── Resolution ─────────────────────────────────────────────────────────────

    private fun resolutionScore(stream: StreamSource): Int {
        // Check pre-computed qualityValue first
        if ((stream.qualityValue ?: 0) > 0) {
            return when {
                (stream.qualityValue ?: 0) >= 2160 -> 5
                (stream.qualityValue ?: 0) >= 1440 -> 4
                (stream.qualityValue ?: 0) >= 1080 -> 3
                (stream.qualityValue ?: 0) >= 720  -> 2
                (stream.qualityValue ?: 0) >= 480  -> 1
                else -> 0
            }
        }
        // Sıralama için dosya adındaki AÇIK çözünürlük etiketine bakılır (tahmin yok).
        val height = StreamInfoResolver.parseHeightFromText("${stream.title} ${stream.name}")
        return when {
            height == null -> 0
            height >= 2160 -> 5
            height >= 1440 -> 4
            height >= 1080 -> 3
            height >= 720 -> 2
            height >= 480 -> 1
            else -> 0
        }
    }

    // ── Source quality ─────────────────────────────────────────────────────────
    // Matches KitsugiTV-dev DirectDebridStreamFilter.streamQuality()

    private fun qualityScore(stream: StreamSource): Int {
        val text = "${stream.title} ${stream.name}".lowercase(Locale.ROOT)
        return when {
            text.contains("remux")                                        -> 8
            text.contains("blu-ray") || text.contains("bluray") ||
            text.contains("bdrip")   || text.contains("brrip")           -> 7
            text.contains("web-dl")  || text.contains("webdl")           -> 6
            text.contains("webrip")  || text.contains("web-rip")         -> 5
            text.contains("hdrip")                                        -> 4
            text.contains("hdtv")                                         -> 3
            text.contains("dvdrip")                                       -> 2
            text.hasToken("cam") || text.hasToken("ts") ||
            text.hasToken("tc") || text.hasToken("scr")                  -> 0
            else                                                          -> 1
        }
    }

    // ── Size ───────────────────────────────────────────────────────────────────

    private val sizeRegex = Regex("""(\d+(?:\.\d+)?)\s*(gb|mb|gib|mib)""", RegexOption.IGNORE_CASE)

    private fun sizeBytes(stream: StreamSource): Long {
        // Gerçek bayt bilgisi varsa metin ayrıştırmasına gerek yok.
        stream.videoSizeBytes?.let { if (it > 0) return it }
        val text = "${stream.fileName.orEmpty()} ${stream.title} ${stream.name}"
        val match = sizeRegex.find(text) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        return when (unit) {
            "gb", "gib" -> (value * 1_000_000_000).toLong()
            "mb", "mib" -> (value * 1_000_000).toLong()
            else -> 0L
        }
    }

    /**
     * Metinde AÇIKÇA yazan çözünürlüğü döndürür. Kanıt yoksa **null** döner.
     *
     * Eskiden burada "hiçbir şey bulunamadıysa HD yaz" davranışı vardı; bu, arayüzde
     * gerçekte doğrulanmamış kalite rozetlerine sebep oluyordu. Artık uydurmuyoruz.
     */
    fun parseQualityFromTitle(text: String): String? =
        StreamInfoResolver.parseHeightFromText(text)?.let { StreamInfoResolver.heightToLabel(it) }

    /** Kalite etiketinden çözünürlük yüksekliği; bilinmiyorsa **null**. */
    fun parseQualityValue(quality: String?): Int? =
        StreamInfoResolver.parseHeightFromText(quality)

    private fun String.hasToken(token: String): Boolean =
        Regex("(^|[^a-z0-9])${Regex.escape(token)}([^a-z0-9]|\$)").containsMatchIn(this)
}
