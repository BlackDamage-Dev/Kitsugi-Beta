package com.kitsugi.animelist.core.download

import java.net.URI
import java.util.Locale

/**
 * RFC-8216 uyumlu, saf Kotlin HLS (`.m3u8`, `master.txt`, `playlist.txt`, uzantısız manifest)
 * ayrıştırıcısı ve akış seçicisi.
 *
 * Close CDN (`.../hls/<film>.mp4/txt/master.txt`), Rapid CDN (`rapidrame.com/.../master.m3u8`),
 * VidMoxy, MolyStream (`/embed/sheila/...`), Beta Player, Sistenn AES-128 ve ayrık sesli
 * (`#EXT-X-MEDIA:TYPE=AUDIO`) DUAL / Türkçe Dublaj akışlarının tamamını destekler.
 */
object HlsManifestParser {

    data class ByteRange(
        val length: Long,
        val offset: Long
    ) {
        val endInclusive: Long get() = offset + length - 1L
        fun toHttpRangeHeader(): String = "bytes=$offset-$endInclusive"
    }

    data class HlsEncryptionKey(
        val method: String,
        val uri: String?,
        val ivHex: String?,
        val keyFormat: String? = null
    ) {
        val isEncrypted: Boolean
            get() = !method.equals("NONE", ignoreCase = true) && !uri.isNullOrBlank()
        val isAes128: Boolean
            get() = method.equals("AES-128", ignoreCase = true) && !uri.isNullOrBlank()
    }

    data class HlsInitSegment(
        val url: String,
        val byteRange: ByteRange? = null,
        val encryptionKey: HlsEncryptionKey? = null
    ) {
        val cacheKey: String
            get() = buildString {
                append(url)
                byteRange?.let { append("#${it.offset}:${it.length}") }
            }
    }

    data class HlsSegment(
        val url: String,
        val durationSec: Double,
        val sequenceNumber: Long,
        val byteRange: ByteRange? = null,
        val initSegment: HlsInitSegment? = null,
        val encryptionKey: HlsEncryptionKey? = null,
        val discontinuitySequence: Int = 0
    )

    data class HlsAudioRendition(
        val groupId: String,
        val name: String,
        val language: String?,
        val assocLanguage: String?,
        val uri: String?,
        val isDefault: Boolean,
        val autoSelect: Boolean,
        val channels: String?,
        val declarationIndex: Int
    ) {
        val isDetached: Boolean get() = !uri.isNullOrBlank()
    }

    data class HlsSubtitleRendition(
        val groupId: String,
        val name: String,
        val language: String?,
        val uri: String?,
        val isDefault: Boolean,
        val declarationIndex: Int
    )

    data class HlsVariant(
        val url: String,
        val bandwidth: Long,
        val averageBandwidth: Long,
        val width: Int?,
        val height: Int?,
        val codecs: String?,
        val audioGroupId: String?,
        val subtitlesGroupId: String?,
        val declarationIndex: Int
    ) {
        val hasVideoCodec: Boolean
            get() = codecs?.let { containsAnyCodec(it, VIDEO_CODEC_PREFIXES) } ?: false

        val hasEmbeddedAudioCodec: Boolean
            get() = codecs?.let { containsAnyCodec(it, AUDIO_CODEC_PREFIXES) } ?: false

        val isAudioOnly: Boolean
            get() {
                val c = codecs
                if (!c.isNullOrBlank()) {
                    return hasEmbeddedAudioCodec && !hasVideoCodec && (width == null || width == 0)
                }
                return false
            }

        val effectiveHeight: Int
            get() = height ?: extractResolutionHintFromUrl(url)
    }

    sealed class ParsedHlsPlaylist {
        data class Master(
            val baseUrl: String,
            val variants: List<HlsVariant>,
            val audioRenditions: List<HlsAudioRendition>,
            val subtitleRenditions: List<HlsSubtitleRendition>
        ) : ParsedHlsPlaylist()

        data class Media(
            val baseUrl: String,
            val segments: List<HlsSegment>,
            val targetDurationSec: Double,
            val mediaSequence: Long,
            val isEndList: Boolean
        ) : ParsedHlsPlaylist()
    }

    data class HlsSelectionPlan(
        val selectedVariant: HlsVariant,
        /** Öncelik sırasına göre ayrık ses adayları (`#EXT-X-MEDIA:TYPE=AUDIO,URI=...`). Boşsa ses videoya gömülüdür. */
        val audioCandidates: List<HlsAudioRendition>,
        /** Master listede tanımlı VTT altyazı rendisyonları. */
        val subtitleRenditions: List<HlsSubtitleRendition>
    )

    private val VIDEO_CODEC_PREFIXES = listOf(
        "avc1", "avc3", "hvc1", "hev1", "dvh1", "dvhe",
        "vp08", "vp8", "vp09", "vp9", "av01", "mp4v", "vc-1"
    )

    private val AUDIO_CODEC_PREFIXES = listOf(
        "mp4a", "ac-3", "ec-3", "ac-4", "opus", "vorb", "vorbis",
        "flac", "mp3", ".mp3", "alac", "dtsc", "dtse", "dtsh", "dtsl", "dts"
    )

    private val DEFINITE_HLS_PATTERNS = listOf(
        ".m3u8",
        "/hls_playlist/",
        "/master.txt",
        "/index.txt",
        "/playlist.txt",
        "/txt/master",
        "/list/",
        ".urlset/",
        "/embed/sheila/",
        "format=m3u8",
        "type=m3u8",
        "output=m3u8"
    )

    private val DEFINITE_PROGRESSIVE_EXT_REGEX =
        Regex("""\.(mp4|mkv|webm|avi|mov|flv|m4v|ts|m2ts|mp3|m4a|aac|ogg|opus|flac)$""", RegexOption.IGNORE_CASE)

    /**
     * Bir URL'nin yüksek olasılıkla HLS manifesti olup olmadığını kontrol eder.
     * Yerel dosya URI'leri (`file://`, `content://`, `/...`) için her zaman `false` döner.
     */
    fun isLikelyHlsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("file://") || lower.startsWith("content://") || lower.startsWith("/")) {
            return false
        }

        val pathOnly = try {
            URI(trimmed).path?.lowercase(Locale.ROOT).orEmpty()
        } catch (_: Exception) {
            lower.substringBefore('?').substringBefore('#')
        }

        if (DEFINITE_HLS_PATTERNS.any { lower.contains(it) }) {
            return true
        }

        if (DEFINITE_PROGRESSIVE_EXT_REGEX.containsMatchIn(pathOnly)) {
            return false
        }

        if (pathOnly.endsWith(".txt") && (
                lower.contains("/hls/") ||
                    lower.contains("/playlist/") ||
                    lower.contains("/stream/") ||
                    lower.contains("master") ||
                    lower.contains("index")
                )
        ) {
            return true
        }

        return lower.contains("/hls/") ||
            lower.contains("/hls2/") ||
            lower.contains("/hlsmod/") ||
            lower.contains("/m3u8/") ||
            lower.contains("m3u8")
    }

    /**
     * HTTP yanıtının `Content-Type` başlığına veya ilk baytlarına bakarak içeriğin
     * bir HLS manifesti (`#EXTM3U` veya çıplak varyant listesi) olup olmadığını tespit eder.
     * UTF-8 BOM (`EF BB BF`) ve baştaki boşlukları tolere eder.
     */
    fun looksLikeHlsContent(contentType: String?, headBytes: ByteArray, length: Int = headBytes.size): Boolean {
        val ct = contentType?.lowercase(Locale.ROOT).orEmpty()
        if (ct.contains("mpegurl") || ct.contains("vnd.apple.mpegurl") || ct.contains("x-mpegurl")) {
            return true
        }
        if (length <= 0) return false
        val sampleLen = length.coerceAtMost(headBytes.size).coerceAtMost(4096)
        val headText = stripBomAndLeadingWhitespace(String(headBytes, 0, sampleLen, Charsets.UTF_8))
        if (headText.startsWith("#EXTM3U", ignoreCase = true) ||
            headText.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) ||
            headText.startsWith("#EXTINF", ignoreCase = true)
        ) {
            return true
        }
        return isBareVariantList(headText)
    }

    /**
     * Bazı Türk CDN / aracı uç noktaları (`Sobreatsesuyp`, `VidRame`, `DiziBox`)
     * ilk HTTP 200 gövdesinde doğrudan tek satırlık `https://.../master.m3u8` URL'si döndürür.
     */
    fun extractPlainUrlHop(rawBody: String): String? {
        val cleaned = stripBomAndLeadingWhitespace(rawBody).trim()
        if (cleaned.length !in 10..2048) return null
        if (cleaned.contains('\n') || cleaned.contains('\r') || cleaned.any { it.isWhitespace() }) {
            return null
        }
        return if (cleaned.startsWith("https://", ignoreCase = true) ||
            cleaned.startsWith("http://", ignoreCase = true)
        ) {
            cleaned
        } else {
            null
        }
    }

    /**
     * Bir master listesinde ayrık ses rendisyonu (`#EXT-X-MEDIA:TYPE=AUDIO` + `URI=...`)
     * bulunup bulunmadığını hızlıca kontrol eder (`KitsugiCdnJwExtractor` tarafından da kullanılır).
     */
    fun hasDetachedAudioRendition(manifestText: String): Boolean {
        val cleaned = stripBomAndLeadingWhitespace(manifestText)
        for (rawLine in cleaned.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA:", ignoreCase = true)) continue
            val attrs = parseAttributeList(line.substringAfter(':'))
            if (attrs["TYPE"].equals("AUDIO", ignoreCase = true) && !attrs["URI"].isNullOrBlank()) {
                return true
            }
        }
        return false
    }

    /**
     * Ham HLS manifest metnini `ParsedHlsPlaylist.Master` veya `ParsedHlsPlaylist.Media` olarak ayrıştırır.
     */
    fun parse(rawContent: String, baseUrl: String): ParsedHlsPlaylist {
        val cleaned = stripBomAndLeadingWhitespace(rawContent)
        val lines = cleaned.lines()

        // Çıplak varyant listesi kontrolü (ör. playmix/closeload satır satır .m3u8 listesi)
        if (!cleaned.startsWith("#EXTM3U", ignoreCase = true) && isBareVariantList(cleaned)) {
            val bareVariants = lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapIndexedNotNull { idx, line ->
                    val resolved = resolveUrl(baseUrl, line)
                    if (resolved.isBlank()) null
                    else HlsVariant(
                        url = resolved,
                        bandwidth = 0L,
                        averageBandwidth = 0L,
                        width = null,
                        height = extractResolutionHintFromUrl(resolved).takeIf { it > 0 },
                        codecs = null,
                        audioGroupId = null,
                        subtitlesGroupId = null,
                        declarationIndex = idx
                    )
                }
            return ParsedHlsPlaylist.Master(
                baseUrl = baseUrl,
                variants = bareVariants,
                audioRenditions = emptyList(),
                subtitleRenditions = emptyList()
            )
        }

        val hasMasterTags = lines.any { raw ->
            val l = raw.trim()
            l.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) ||
                l.startsWith("#EXT-X-MEDIA:", ignoreCase = true)
        }
        val hasMediaSegments = lines.any { raw ->
            raw.trim().startsWith("#EXTINF", ignoreCase = true)
        }

        return if (hasMasterTags && !hasMediaSegments) {
            parseMasterPlaylist(lines, baseUrl)
        } else {
            parseMediaPlaylist(lines, baseUrl)
        }
    }

    private fun parseMasterPlaylist(lines: List<String>, baseUrl: String): ParsedHlsPlaylist.Master {
        val variants = mutableListOf<HlsVariant>()
        val audioRenditions = mutableListOf<HlsAudioRendition>()
        val subtitleRenditions = mutableListOf<HlsSubtitleRendition>()

        var pendingStreamInfAttrs: Map<String, String>? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-MEDIA:", ignoreCase = true)) {
                val attrs = parseAttributeList(line.substringAfter(':'))
                val mediaType = attrs["TYPE"]?.uppercase(Locale.ROOT)
                val groupId = attrs["GROUP-ID"].orEmpty()
                val name = attrs["NAME"].orEmpty().ifBlank { attrs["LANGUAGE"].orEmpty().ifBlank { "Track" } }
                val language = attrs["LANGUAGE"]?.trim()
                val assocLang = attrs["ASSOC-LANGUAGE"]?.trim()
                val resolvedUri = attrs["URI"]?.takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
                val isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true)
                val autoSelect = attrs["AUTOSELECT"].equals("YES", ignoreCase = true)

                when (mediaType) {
                    "AUDIO" -> {
                        audioRenditions += HlsAudioRendition(
                            groupId = groupId,
                            name = name,
                            language = language,
                            assocLanguage = assocLang,
                            uri = resolvedUri,
                            isDefault = isDefault,
                            autoSelect = autoSelect,
                            channels = attrs["CHANNELS"],
                            declarationIndex = audioRenditions.size
                        )
                    }
                    "SUBTITLES" -> {
                        if (!resolvedUri.isNullOrBlank()) {
                            subtitleRenditions += HlsSubtitleRendition(
                                groupId = groupId,
                                name = name,
                                language = language,
                                uri = resolvedUri,
                                isDefault = isDefault,
                                declarationIndex = subtitleRenditions.size
                            )
                        }
                    }
                }
                continue
            }

            if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) ||
                line.equals("#EXT-X-STREAM-INF", ignoreCase = true)
            ) {
                val attrPart = if (line.contains(':')) line.substringAfter(':') else ""
                pendingStreamInfAttrs = parseAttributeList(attrPart)
                continue
            }

            if (line.startsWith("#")) {
                continue
            }

            val resolvedVariantUrl = resolveUrl(baseUrl, line)
            val attrs = pendingStreamInfAttrs
            pendingStreamInfAttrs = null

            if (attrs != null) {
                val (width, height) = parseResolution(attrs["RESOLUTION"])
                variants += HlsVariant(
                    url = resolvedVariantUrl,
                    bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                    averageBandwidth = attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L,
                    width = width,
                    height = height,
                    codecs = attrs["CODECS"],
                    audioGroupId = attrs["AUDIO"],
                    subtitlesGroupId = attrs["SUBTITLES"],
                    declarationIndex = variants.size
                )
            } else if (resolvedVariantUrl.contains(".m3u8", ignoreCase = true) ||
                resolvedVariantUrl.contains("master.txt", ignoreCase = true) ||
                resolvedVariantUrl.contains("index.txt", ignoreCase = true)
            ) {
                variants += HlsVariant(
                    url = resolvedVariantUrl,
                    bandwidth = 0L,
                    averageBandwidth = 0L,
                    width = null,
                    height = extractResolutionHintFromUrl(resolvedVariantUrl).takeIf { it > 0 },
                    codecs = null,
                    audioGroupId = null,
                    subtitlesGroupId = null,
                    declarationIndex = variants.size
                )
            }
        }

        return ParsedHlsPlaylist.Master(
            baseUrl = baseUrl,
            variants = variants,
            audioRenditions = audioRenditions,
            subtitleRenditions = subtitleRenditions
        )
    }

    private fun parseMediaPlaylist(lines: List<String>, baseUrl: String): ParsedHlsPlaylist.Media {
        val segments = mutableListOf<HlsSegment>()
        var targetDuration = 0.0
        var mediaSequence = 0L
        var isEndList = false
        var discontinuitySeq = 0

        var currentKey: HlsEncryptionKey? = null
        var currentInitSegment: HlsInitSegment? = null
        var pendingDuration = 0.0
        var pendingByteRangeSpec: String? = null
        val nextImplicitByteRangeOffsetByUrl = HashMap<String, Long>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXT-X-TARGETDURATION:", ignoreCase = true) -> {
                    targetDuration = line.substringAfter(':').trim().toDoubleOrNull() ?: targetDuration
                }
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) -> {
                    mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: mediaSequence
                }
                line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:", ignoreCase = true) -> {
                    discontinuitySeq = line.substringAfter(':').trim().toIntOrNull() ?: discontinuitySeq
                }
                line.equals("#EXT-X-DISCONTINUITY", ignoreCase = true) -> {
                    discontinuitySeq++
                }
                line.equals("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    isEndList = true
                }
                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val attrs = parseAttributeList(line.substringAfter(':'))
                    val method = attrs["METHOD"]?.uppercase(Locale.ROOT) ?: "NONE"
                    currentKey = if (method == "NONE") {
                        null
                    } else {
                        val keyUri = attrs["URI"]?.takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
                        HlsEncryptionKey(
                            method = method,
                            uri = keyUri,
                            ivHex = attrs["IV"],
                            keyFormat = attrs["KEYFORMAT"]
                        )
                    }
                }
                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val attrs = parseAttributeList(line.substringAfter(':'))
                    val mapUri = attrs["URI"]?.takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
                    if (mapUri != null) {
                        val mapRange = attrs["BYTERANGE"]?.let { spec ->
                            resolveByteRange(spec, mapUri, nextImplicitByteRangeOffsetByUrl)
                        }
                        currentInitSegment = HlsInitSegment(
                            url = mapUri,
                            byteRange = mapRange,
                            encryptionKey = currentKey
                        )
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val durPart = line.substringAfter(':').substringBefore(',').trim()
                    pendingDuration = durPart.toDoubleOrNull() ?: 0.0
                }
                line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> {
                    pendingByteRangeSpec = line.substringAfter(':').trim()
                }
                line.startsWith("#") -> {
                    // Diğer yorum/etiket satırları
                }
                else -> {
                    val segUrl = resolveUrl(baseUrl, line)
                    val byteRange = pendingByteRangeSpec?.let { spec ->
                        resolveByteRange(spec, segUrl, nextImplicitByteRangeOffsetByUrl)
                    }
                    val seqNum = mediaSequence + segments.size.toLong()
                    segments += HlsSegment(
                        url = segUrl,
                        durationSec = pendingDuration,
                        sequenceNumber = seqNum,
                        byteRange = byteRange,
                        initSegment = currentInitSegment,
                        encryptionKey = currentKey,
                        discontinuitySequence = discontinuitySeq
                    )
                    pendingDuration = 0.0
                    pendingByteRangeSpec = null
                }
            }
        }

        return ParsedHlsPlaylist.Media(
            baseUrl = baseUrl,
            segments = segments,
            targetDurationSec = targetDuration,
            mediaSequence = mediaSequence,
            isEndList = isEndList
        )
    }

    /**
     * Master playlist içinden en uygun video varyantını ve (varsa) ayrık ses rendisyon adaylarını seçer.
     *
     * @param preferredAudioLanguages Kullanıcının tercih ettiği dil kodları (ör. `["tr", "ja", "en"]`).
     * @param audioKindHint           Sağlayıcı ses türü ipucu (`"dub"`, `"sub"`, `"orig"`, `"dual"` veya `null`).
     * @param hasExternalAudio        `StreamSource.externalAudioUrl` dolu mu?
     */
    fun selectBestPlan(
        master: ParsedHlsPlaylist.Master,
        preferredAudioLanguages: List<String> = listOf("tr", "ja", "en"),
        audioKindHint: String? = null,
        hasExternalAudio: Boolean = false
    ): HlsSelectionPlan? {
        if (master.variants.isEmpty()) return null

        val videoCandidates = master.variants.filterNot { it.isAudioOnly }.ifEmpty { master.variants }

        fun variantHasAudioPath(variant: HlsVariant): Boolean {
            if (hasExternalAudio) return true
            val group = variant.audioGroupId
            if (!group.isNullOrBlank() && master.audioRenditions.any { it.groupId == group && it.isDetached }) {
                return true
            }
            if (group == null && master.audioRenditions.any { it.isDetached }) {
                return true
            }
            if (!group.isNullOrBlank() && master.audioRenditions.any { it.groupId == group && !it.isDetached }) {
                return true
            }
            if (variant.codecs.isNullOrBlank()) {
                return true // CODECS belirtilmemişse gömülü ses varsayılır
            }
            return variant.hasEmbeddedAudioCodec
        }

        val selectedVariant = videoCandidates.maxWithOrNull(
            compareBy<HlsVariant> { variantHasAudioPath(it) }
                .thenBy { it.effectiveHeight }
                .thenBy { it.bandwidth.takeIf { bw -> bw > 0L } ?: it.averageBandwidth }
                .thenBy { -it.declarationIndex }
        ) ?: return null

        // Seçilen varyantın AUDIO grubuna ait ayrık (URI != null) ses rendisyonlarını bul
        val groupDetached = if (!selectedVariant.audioGroupId.isNullOrBlank()) {
            master.audioRenditions.filter {
                it.groupId.equals(selectedVariant.audioGroupId, ignoreCase = true) && it.isDetached
            }
        } else {
            emptyList()
        }

        // Grup içinde hem gömülü (URI == null) hem ayrık (URI != null) olabilir:
        // Eğer seçilen varyantın grubu hiçbir ayrık URI içermiyorsa ancak grup belirtilmeden
        // master listede ayrık AUDIO rendisyonları varsa ve varyantta gömülü ses codec'i yoksa fallback yap.
        val candidatePool = when {
            groupDetached.isNotEmpty() -> groupDetached
            selectedVariant.audioGroupId.isNullOrBlank() && !selectedVariant.hasEmbeddedAudioCodec ->
                master.audioRenditions.filter { it.isDetached }
            else -> emptyList()
        }

        val rankedAudio = rankAudioRenditions(
            renditions = candidatePool,
            preferredAudioLanguages = preferredAudioLanguages,
            audioKindHint = audioKindHint
        )

        val matchingSubs = if (!selectedVariant.subtitlesGroupId.isNullOrBlank()) {
            master.subtitleRenditions.filter {
                it.groupId.equals(selectedVariant.subtitlesGroupId, ignoreCase = true)
            }.ifEmpty { master.subtitleRenditions }
        } else {
            master.subtitleRenditions
        }

        return HlsSelectionPlan(
            selectedVariant = selectedVariant,
            audioCandidates = rankedAudio,
            subtitleRenditions = matchingSubs
        )
    }

    /**
     * Ayrık ses rendisyonlarını kullanıcı dil tercihi ve akışın ses türüne (`dub` / `sub` / `dual`)
     * göre en uygun adaydan başlayarak sıralar.
     */
    fun rankAudioRenditions(
        renditions: List<HlsAudioRendition>,
        preferredAudioLanguages: List<String>,
        audioKindHint: String?
    ): List<HlsAudioRendition> {
        if (renditions.size <= 1) return renditions

        val normalizedPrefs = preferredAudioLanguages
            .map { normalizeLangToken(it) }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("tr", "ja", "en") }

        val kind = audioKindHint?.trim()?.lowercase(Locale.ROOT)

        fun score(r: HlsAudioRendition): Int {
            val lang = detectRenditionLanguage(r)
            val isTurkish = lang == "tr"
            val isOriginalLabel = containsOriginalKeyword(r.name)

            var total = 0
            when (kind) {
                "dub" -> {
                    if (isTurkish) total += 10_000
                }
                "sub", "orig", "original" -> {
                    if (!isTurkish) total += 8_000
                    if (isOriginalLabel) total += 2_000
                }
            }

            val prefIndex = normalizedPrefs.indexOfFirst { pref ->
                lang != null && (lang == pref || lang.startsWith("$pref-"))
            }
            if (prefIndex >= 0) {
                total += (1_000 - prefIndex * 100).coerceAtLeast(100)
            }

            if (r.isDefault) total += 50
            if (r.autoSelect) total += 25
            total -= r.declarationIndex
            return total
        }

        return renditions.sortedByDescending { score(it) }
    }

    fun detectRenditionLanguage(r: HlsAudioRendition): String? {
        val rawLang = r.language?.takeIf { it.isNotBlank() } ?: r.assocLanguage?.takeIf { it.isNotBlank() }
        if (rawLang != null) {
            val norm = normalizeLangToken(rawLang)
            if (norm.isNotEmpty() && norm != "und" && norm != "unk" && norm != "mul") {
                return norm
            }
        }
        val nameNorm = normalizeTurkishAscii(r.name)
        return when {
            nameNorm.contains("turkce") || nameNorm.contains("turkish") ||
                nameNorm.contains("dublaj") || nameNorm == "tr" || nameNorm.startsWith("tr ") -> "tr"
            nameNorm.contains("japonca") || nameNorm.contains("japanese") || nameNorm == "ja" || nameNorm == "jp" -> "ja"
            nameNorm.contains("ingilizce") || nameNorm.contains("english") || nameNorm == "en" -> "en"
            nameNorm.contains("almanca") || nameNorm.contains("german") || nameNorm == "de" -> "de"
            nameNorm.contains("fransizca") || nameNorm.contains("french") || nameNorm == "fr" -> "fr"
            nameNorm.contains("ispanyolca") || nameNorm.contains("spanish") || nameNorm == "es" -> "es"
            nameNorm.contains("rusca") || nameNorm.contains("russian") || nameNorm == "ru" -> "ru"
            else -> rawLang?.let { normalizeLangToken(it) }?.takeIf { it.isNotEmpty() }
        }
    }

    private fun normalizeLangToken(raw: String): String {
        val lower = normalizeTurkishAscii(raw).substringBefore('_').substringBefore('-')
        return when (lower) {
            "tur", "turkish", "turkce", "tr" -> "tr"
            "jpn", "japanese", "japonca", "jp", "ja" -> "ja"
            "eng", "english", "ingilizce", "en" -> "en"
            "deu", "ger", "german", "almanca", "de" -> "de"
            "fra", "fre", "french", "fransizca", "fr" -> "fr"
            "spa", "spanish", "ispanyolca", "es" -> "es"
            "rus", "russian", "rusca", "ru" -> "ru"
            "ita", "italian", "it" -> "it"
            "kor", "korean", "ko" -> "ko"
            "zho", "chi", "chinese", "zh" -> "zh"
            else -> lower
        }
    }

    private fun containsOriginalKeyword(text: String): Boolean {
        val norm = normalizeTurkishAscii(text)
        return norm.contains("original") || norm.contains("orijinal") || norm.contains("orig")
    }

    private fun normalizeTurkishAscii(input: String): String {
        return buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    'İ', 'I', 'ı', 'i' -> append('i')
                    'Ş', 'ş' -> append('s')
                    'Ğ', 'ğ' -> append('g')
                    'Ü', 'ü' -> append('u')
                    'Ö', 'ö' -> append('o')
                    'Ç', 'ç' -> append('c')
                    else -> append(ch.lowercaseChar())
                }
            }
        }.trim()
    }

    /**
     * RFC-8216 attribute listesini (`KEY=VALUE,KEY2="VAL,WITH,COMMA"`) güvenle ayrıştırır.
     */
    fun parseAttributeList(attrLine: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var i = 0
        val n = attrLine.length

        while (i < n) {
            while (i < n && (attrLine[i].isWhitespace() || attrLine[i] == ',')) i++
            if (i >= n) break

            val keyStart = i
            while (i < n && attrLine[i] != '=' && attrLine[i] != ',') i++
            val key = attrLine.substring(keyStart, i).trim().uppercase(Locale.ROOT)
            if (i >= n || attrLine[i] != '=') {
                continue
            }
            i++ // '=' geç

            while (i < n && attrLine[i] == ' ') i++
            if (i >= n) {
                if (key.isNotEmpty()) result[key] = ""
                break
            }

            val value: String
            if (attrLine[i] == '"') {
                i++ // açılış tırnağını geç
                val sb = StringBuilder()
                while (i < n) {
                    val ch = attrLine[i]
                    if (ch == '\\' && i + 1 < n && (attrLine[i + 1] == '"' || attrLine[i + 1] == '\\')) {
                        sb.append(attrLine[i + 1])
                        i += 2
                    } else if (ch == '"') {
                        i++ // kapanış tırnağını geç
                        break
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                value = sb.toString()
                while (i < n && attrLine[i] != ',') i++
            } else {
                val valStart = i
                while (i < n && attrLine[i] != ',') i++
                value = attrLine.substring(valStart, i).trim()
            }

            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    private fun parseResolution(raw: String?): Pair<Int?, Int?> {
        if (raw.isNullOrBlank()) return null to null
        val xIdx = raw.indexOf('x').takeIf { it > 0 } ?: raw.indexOf('X').takeIf { it > 0 } ?: return null to null
        val w = raw.substring(0, xIdx).trim().toIntOrNull()
        val h = raw.substring(xIdx + 1).trim().toIntOrNull()
        return w to h
    }

    private fun extractResolutionHintFromUrl(url: String): Int {
        val match = Regex("""(?:^|[/_\-.])(\d{3,4})p(?:[/_\-.?]|$)""", RegexOption.IGNORE_CASE).find(url)
            ?: Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(url)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun resolveByteRange(
        spec: String,
        targetUrl: String,
        implicitOffsets: MutableMap<String, Long>
    ): ByteRange? {
        val cleaned = spec.trim().trim('"')
        val parts = cleaned.split('@', limit = 2)
        val length = parts[0].trim().toLongOrNull() ?: return null
        if (length <= 0L) return null
        val offset = if (parts.size == 2) {
            parts[1].trim().toLongOrNull() ?: 0L
        } else {
            implicitOffsets[targetUrl] ?: 0L
        }
        implicitOffsets[targetUrl] = offset + length
        return ByteRange(length = length, offset = offset)
    }

    private fun containsAnyCodec(codecsAttr: String, prefixes: List<String>): Boolean {
        val tokens = codecsAttr.lowercase(Locale.ROOT).split(',').map { it.trim() }
        return tokens.any { token -> prefixes.any { prefix -> token.startsWith(prefix) } }
    }

    private fun isBareVariantList(text: String): Boolean {
        val nonEmpty = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)
            .toList()
        if (nonEmpty.isEmpty()) return false
        if (nonEmpty.first().startsWith("<") || nonEmpty.first().startsWith("{")) return false
        val allLookLikeVariantUrls = nonEmpty.all { line ->
            !line.any { it.isWhitespace() } && (
                line.contains(".m3u8", ignoreCase = true) ||
                    line.contains("master.txt", ignoreCase = true)
                ) && (
                line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) ||
                    line.startsWith("//")
                )
        }
        return allLookLikeVariantUrls
    }

    fun stripBomAndLeadingWhitespace(input: String): String {
        var idx = 0
        while (idx < input.length) {
            val ch = input[idx]
            if (ch == '\uFEFF' || ch.isWhitespace()) {
                idx++
            } else {
                break
            }
        }
        return if (idx == 0) input else input.substring(idx)
    }

    /**
     * Göreli veya protokol-göreli bir HLS URI'sini, yönlendirme sonrası nihai `baseUrl`
     * tabanına göre tam adrese çevirir.
     */
    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val cleanedRel = relativeUrl.replace("\\/", "/").trim().trim('"', '\'')
        if (cleanedRel.isEmpty()) return baseUrl
        if (cleanedRel.startsWith("http://", ignoreCase = true) ||
            cleanedRel.startsWith("https://", ignoreCase = true)
        ) {
            return cleanedRel
        }
        if (cleanedRel.startsWith("//")) {
            val scheme = baseUrl.substringBefore("://", "https").ifBlank { "https" }
            return "$scheme:$cleanedRel"
        }
        return try {
            URI(baseUrl).resolve(cleanedRel).toString()
        } catch (_: Exception) {
            val baseNoQuery = baseUrl.substringBefore('?').substringBefore('#')
            if (cleanedRel.startsWith("/")) {
                val schemeIdx = baseNoQuery.indexOf("://")
                val hostEnd = if (schemeIdx >= 0) baseNoQuery.indexOf('/', schemeIdx + 3) else -1
                val origin = if (hostEnd >= 0) baseNoQuery.substring(0, hostEnd) else baseNoQuery
                origin + cleanedRel
            } else {
                val lastSlash = baseNoQuery.lastIndexOf('/')
                if (lastSlash >= 0) {
                    baseNoQuery.substring(0, lastSlash + 1) + cleanedRel
                } else {
                    cleanedRel
                }
            }
        }
    }
}
