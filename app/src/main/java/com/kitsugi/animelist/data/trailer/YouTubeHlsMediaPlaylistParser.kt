package com.kitsugi.animelist.data.trailer

import java.net.URI

/**
 * YouTube'un HLS **media** (varyant) playlist'i — indirme planı.
 *
 * NEDEN VAR?
 * YouTube'un `hlsManifestUrl` alanı çoğunlukla bir master playlist döner ve bu playlist
 * tek başına indirilebilir bir dosya değildir; içindeki segmentler ayrı ayrı inilmeden
 * oynatılamaz. Teaser/fragman gibi kısa videoların "indir" düğmesi için segmentleri
 * sırayla indirip tek dosyada birleştirmek gerekiyor. Bu ayrıştırıcı, birleştirmenin
 * doğru olması için gerekli üç bilgiyi çıkarır:
 *
 *  - `EXT-X-MAP` (fMP4 başlangıç/init segmenti) → **önce** yazılmalıdır, yoksa dosya
 *    hiçbir oynatıcıda açılmaz,
 *  - segment listesi → sırayla yazılır,
 *  - `EXT-X-KEY` şifrelemesi → varsa birleştirme yapılmaz (bozuk dosya üretmek yerine
 *    çağıran taraf başka bir akışa düşer).
 *
 * Saf fonksiyondur (ağ yok) → birim testleriyle doğrulanabilir.
 */
internal data class YouTubeHlsMediaPlaylist(
    /** `EXT-X-MAP` init segmenti (fMP4). MPEG-TS akışlarında `null`. */
    val initSegmentUrl: String?,
    /** Sırayla yazılacak medya segmentleri. */
    val segmentUrls: List<String>,
    /** Segmentler MPEG-TS mi? (`true` → çıktı `.ts` olarak kaydedilir.) */
    val isTransportStream: Boolean,
    /** `EXT-X-KEY:METHOD=AES-128` gibi bir şifreleme var mı? */
    val isEncrypted: Boolean
) {
    val isUsable: Boolean get() = segmentUrls.isNotEmpty() && !isEncrypted
}

internal object YouTubeHlsMediaPlaylistParser {

    fun parse(playlistUrl: String, body: String): YouTubeHlsMediaPlaylist? {
        if (body.isBlank()) return null

        val lines = body
            .lineSequence()
            .map { it.trim().removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.firstOrNull() != "#EXTM3U") return null

        // Master playlist'te URI satırları varyant akışlarıdır, medya segmentleri değildir.
        val isMasterPlaylist = lines.any {
            it.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) ||
                it.startsWith("#EXT-X-I-FRAME-STREAM-INF:", ignoreCase = true)
        }
        if (isMasterPlaylist) {
            return YouTubeHlsMediaPlaylist(
                initSegmentUrl = null,
                segmentUrls = emptyList(),
                isTransportStream = false,
                isEncrypted = false
            )
        }

        var initSegmentUrl: String? = null
        var encrypted = false
        var transportStream = false
        val segments = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val method = attributeValue(line, "METHOD")
                    if (method != null && !method.equals("NONE", ignoreCase = true)) {
                        encrypted = true
                    }
                }

                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    // init segment yalnızca fMP4'te olur; TS akışlarında bulunmaz.
                    val uri = attributeValue(line, "URI")
                    if (!uri.isNullOrBlank()) initSegmentUrl = resolve(playlistUrl, uri)
                }

                line.startsWith("#") -> Unit // yorum / diğer etiketler

                else -> {
                    val resolved = resolve(playlistUrl, line)
                    if (resolved.substringBefore('?').endsWith(".ts", ignoreCase = true)) {
                        transportStream = true
                    }
                    segments += resolved
                }
            }
        }

        // Master playlist'te hiç segment satırı yoktur; boş liste ile döneriz ki çağıran
        // taraf bunu "master" olarak tanıyıp en yüksek varyanta inebilsin.
        return YouTubeHlsMediaPlaylist(
            initSegmentUrl = initSegmentUrl,
            segmentUrls = segments,
            isTransportStream = transportStream,
            isEncrypted = encrypted
        )
    }

    /** `#EXT-X-KEY:METHOD=AES-128,URI="https://…"` → `AES-128` (tırnaklı değerler dahil). */
    private fun attributeValue(line: String, key: String): String? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val attributes = parseAttributeList(line.substring(colon + 1))
        return attributes[key] ?: attributes.entries
            .firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value
    }

    private fun parseAttributeList(raw: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var readingKey = true
        var inQuotes = false
        for (char in raw) {
            if (readingKey) {
                if (char == '=') readingKey = false else key.append(char)
                continue
            }
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    if (key.isNotEmpty()) values[key.toString().trim()] = value.toString().trim()
                    key.clear()
                    value.clear()
                    readingKey = true
                }

                else -> value.append(char)
            }
        }
        if (key.isNotEmpty()) values[key.toString().trim()] = value.toString().trim()
        return values
    }

    private fun resolve(baseUrl: String, childUrl: String): String = runCatching {
        URI(baseUrl).resolve(childUrl).toString()
    }.getOrElse { childUrl }
}
