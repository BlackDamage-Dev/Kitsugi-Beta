package com.kitsugi.animelist.data.repository

import java.util.Locale

/**
 * Bir akış bilgisinin (kalite / dil) NEREDEN geldiğini belirtir.
 *
 * Kitsugi artık "tahmin" ile rozet basmaz; her rozetin bir kaynağı vardır ve
 * kaynağı olmayan bilgi hiç gösterilmez.
 */
enum class StreamInfoOrigin {
    /** Eklentinin/kaynağın kendi meta verisi (CS `ExtractorLink.quality`, `DubStatus`, altyazı listesi, Stremio `name`). */
    PROVIDER,

    /** Kitsugi tarafından gerçek akış üzerinden ölçüldü (HLS master playlist, HTTP başlıkları). */
    MEASURED,

    /** Dosya/yayın adında açıkça yazıyor (ör. "1080p", "TR Dublaj", "1920x1080"). */
    FILENAME,

    /** Bilgi yok — uydurma yapılmaz. */
    NONE
}

/**
 * Gerçek akış üzerinden ölçülen bilgiler. [StreamProbe] tarafından üretilir.
 */
data class MeasuredStreamInfo(
    /** Tekil çözünürlük (medya playlist / mp4). */
    val height: Int? = null,
    /** HLS/DASH çok varyantlı (adaptif) yayın mı? */
    val isAdaptive: Boolean = false,
    /** Master playlist içindeki tüm varyant yükseklikleri. */
    val variantHeights: List<Int> = emptyList(),
    /**
     * Master playlist'ten çözülen **oynatılabilir** video varyantları (kalite seçimi için).
     *
     * rapidrame / closeload gibi kaynaklar tek bir `master.m3u8` verip kaliteyi
     * bildirmediğinde oynatıcının "Kalite" sayfası bu listeyi gösterir ve seçilen
     * varyantın URL'si ile oynatmayı sürdürür.
     */
    val variants: List<com.kitsugi.animelist.core.player.HlsVariant> = emptyList(),
    /** `#EXT-X-MEDIA:TYPE=AUDIO` ile bildirilen ses dilleri (BCP-47 / ISO kodları). */
    val audioLanguages: List<String> = emptyList(),
    /** `#EXT-X-MEDIA:TYPE=SUBTITLES` ile bildirilen altyazı dilleri. */
    val subtitleLanguages: List<String> = emptyList(),
    /** `Content-Length` (yalnızca progressive dosyalarda anlamlı). */
    val sizeBytes: Long? = null,
    /** Ölçüm denendi ama başarısız oldu (ağ hatası / desteklenmeyen tip). */
    val failed: Boolean = false
) {
    val hasAnyInfo: Boolean
        get() = height != null || variantHeights.isNotEmpty() ||
            audioLanguages.isNotEmpty() || subtitleLanguages.isNotEmpty() || sizeBytes != null
}

/** Çözünürlük bilgisi + bu bilginin kaynağı. [label] null ise gerçekten bilinmiyordur. */
data class StreamQualityInfo(
    val label: String?,
    val height: Int?,
    val origin: StreamInfoOrigin,
    val isAdaptive: Boolean = false
) {
    val isKnown: Boolean get() = label != null
}

enum class StreamAudioKind { DUB, SUB, DUAL, UNKNOWN }

/** Dil bilgisi + kaynağı. Hiçbir şey bilinmiyorsa [kind] = UNKNOWN ve listeler boştur. */
data class StreamLangInfo(
    val kind: StreamAudioKind,
    val origin: StreamInfoOrigin,
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList()
) {
    val isKnown: Boolean
        get() = kind != StreamAudioKind.UNKNOWN ||
            audioLanguages.isNotEmpty() || subtitleLanguages.isNotEmpty()
}

/**
 * Akış rozetleri için **doğrulanabilir** bilgi üretir.
 *
 * Tasarım kuralları:
 *  1. Kaynak (eklenti) açıkça söylediyse → [StreamInfoOrigin.PROVIDER]
 *  2. Kitsugi gerçek akıştan ölçtüyse    → [StreamInfoOrigin.MEASURED] (en güvenilir, öncelikli)
 *  3. Dosya adında açıkça yazıyorsa      → [StreamInfoOrigin.FILENAME]
 *  4. Hiçbiri yoksa                      → bilgi YOK (rozet basılmaz)
 *
 * Eskiden yapılan ve yanlış bilgi üreten varsayımlar kaldırıldı:
 *  - "hiçbir şey bulunamadıysa 1080p (HD) yaz"
 *  - "türkçe eklentiyse altyazılıdır"
 *  - CloudStream `Qualities.Unknown` (= 400) değerinin "400p" olarak basılması
 *  - salt alt-dize eşleşmesi ("Subaru" → sub, "Dubai" → dub)
 */
object StreamInfoResolver {

    // ── Çözünürlük ayrıştırma ────────────────────────────────────────────────

    /** "1080p", "720 p", "2160i" gibi AÇIK çözünürlük etiketleri. */
    private val RESOLUTION_TAG = Regex(
        """(?<![0-9])(144|240|360|480|540|576|720|1080|1440|2160|4320)\s?[pi](?![a-z0-9])""",
        RegexOption.IGNORE_CASE
    )

    /** "1920x1080", "1280×720" gibi boyut bildirimleri. */
    private val RESOLUTION_DIMENSION = Regex(
        """(?<![0-9])(\d{3,4})\s?[x×]\s?(\d{3,4})(?![0-9])""",
        RegexOption.IGNORE_CASE
    )

    /** Çözünürlüğe birebir karşılık gelen açık takma adlar. Belirsiz "hd"/"sd" KASITLI olarak yok. */
    private val QUALITY_ALIASES: List<Pair<Regex, Int>> = listOf(
        Regex("""(^|[^a-z0-9])(4k|uhd|ultra\s?hd)([^a-z0-9]|$)""", RegexOption.IGNORE_CASE) to 2160,
        Regex("""(^|[^a-z0-9])(qhd|wqhd|2k)([^a-z0-9]|$)""", RegexOption.IGNORE_CASE) to 1440,
        Regex("""(^|[^a-z0-9])(fhd|full\s?hd|fullhd)([^a-z0-9]|$)""", RegexOption.IGNORE_CASE) to 1080
    )

    /** Bilinen gerçek çözünürlükler. CloudStream'in `Qualities.Unknown` (400) değeri bilerek dışarıda. */
    val KNOWN_HEIGHTS = setOf(144, 240, 360, 480, 540, 576, 720, 1080, 1440, 2160, 4320)

    /** Metinde AÇIKÇA yazan çözünürlüğü döndürür; yoksa null (tahmin yok). */
    fun parseHeightFromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase(Locale.ROOT)

        RESOLUTION_DIMENSION.find(lower)?.let { m ->
            val h = m.groupValues[2].toIntOrNull()
            if (h != null && h in 100..4320) return h
        }
        RESOLUTION_TAG.find(lower)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { return it }
        }
        for ((regex, height) in QUALITY_ALIASES) {
            if (regex.containsMatchIn(lower)) return height
        }
        return null
    }

    /** Çözünürlük yüksekliğini kullanıcıya gösterilecek etikete çevirir. */
    fun heightToLabel(height: Int): String = when (height) {
        4320 -> "8K"
        2160 -> "4K"
        else -> "${height}p"
    }

    /** CloudStream `Qualities` değerini gerçek bir çözünürlüğe çevirir; bilinmiyorsa null. */
    fun csQualityHeightOrNull(quality: Int?): Int? = when {
        quality == null -> null
        quality == 4000 -> 2160            // Qualities.P2160 bazı sürümlerde 4000
        quality in KNOWN_HEIGHTS -> quality
        else -> null                        // 400 = Qualities.Unknown, 0, -1 ...
    }

    /**
     * Akışın çözünürlüğünü, en güvenilir kaynaktan başlayarak çözer.
     * Hiçbir kanıt yoksa [StreamQualityInfo.label] null döner.
     */
    fun resolveQuality(stream: StreamSource, measured: MeasuredStreamInfo? = null): StreamQualityInfo {
        // 1) Gerçekten ölçülmüş bilgi
        if (measured != null && !measured.failed) {
            val variants = measured.variantHeights.filter { it > 0 }
            if (variants.size > 1) {
                val best = variants.max()
                return StreamQualityInfo(
                    label = heightToLabel(best),
                    height = best,
                    origin = StreamInfoOrigin.MEASURED,
                    isAdaptive = true
                )
            }
            val single = measured.height ?: variants.firstOrNull()
            if (single != null && single > 0) {
                return StreamQualityInfo(
                    label = heightToLabel(single),
                    height = single,
                    origin = StreamInfoOrigin.MEASURED,
                    isAdaptive = measured.isAdaptive
                )
            }
        }

        // 2) Kaynağın kendi bildirdiği değer
        val providerHeight = csQualityHeightOrNull(stream.qualityValue)
        if (providerHeight != null) {
            return StreamQualityInfo(
                label = heightToLabel(providerHeight),
                height = providerHeight,
                origin = StreamInfoOrigin.PROVIDER,
                isAdaptive = isAdaptiveStream(stream)
            )
        }

        // 3) Dosya/yayın adında açıkça yazan değer
        val fromText = parseHeightFromText(stream.title)
            ?: parseHeightFromText(stream.name)
            ?: parseHeightFromText(stream.quality)
            ?: parseHeightFromText(stream.url)
        if (fromText != null) {
            return StreamQualityInfo(
                label = heightToLabel(fromText),
                height = fromText,
                origin = StreamInfoOrigin.FILENAME,
                isAdaptive = isAdaptiveStream(stream)
            )
        }

        // 4) Bilinmiyor — uydurmuyoruz
        return StreamQualityInfo(
            label = null,
            height = null,
            origin = StreamInfoOrigin.NONE,
            isAdaptive = measured?.isAdaptive == true || isAdaptiveStream(stream)
        )
    }

    fun isAdaptiveStream(stream: StreamSource): Boolean =
        isAdaptiveUrl(stream.url) ||
            stream.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.HLS ||
            stream.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.DASH ||
            stream.streamTypeHint.equals("M3U8", ignoreCase = true) ||
            stream.streamTypeHint.equals("HLS", ignoreCase = true) ||
            stream.streamTypeHint.equals("DASH", ignoreCase = true)

    fun isAdaptiveUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.ROOT) ?: return false
        return lower.contains(".m3u8") || lower.contains(".mpd")
    }

    // ── Dil ayrıştırma ───────────────────────────────────────────────────────

    private val DUAL_REGEX = Regex(
        """(^|[^\p{L}\p{N}])(dual(\s?audio)?|multi(\s?audio|\s?lang\p{L}*)?|çift\s?dil\p{L}*|cift\s?dil\p{L}*)([^\p{L}\p{N}]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val DUB_REGEX = Regex(
        """(^|[^\p{L}\p{N}])(dub|dubs|dubbed|dublaj\p{L}*|seslendirme\p{L}*|tr\s?dub|trdub|eng\s?dub|türkçe\s?ses|turkce\s?ses)([^\p{L}\p{N}]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val SUB_REGEX = Regex(
        """(^|[^\p{L}\p{N}])(sub|subs|subbed|subtitle\p{L}*|altyaz\p{L}*|softsub|hardsub|esub|msub|vostfr|tr\s?sub|trsub|eng\s?sub|çeviri\p{L}*|ceviri\p{L}*)([^\p{L}\p{N}]|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Dil rozetleri için metin. Eklenti adı KASITLI olarak dışarıda bırakılır:
     * "AnimeciX" ya da "Dizilla" olması içeriğin altyazılı/dublajlı olduğunu kanıtlamaz.
     */
    private fun languageText(stream: StreamSource): String {
        val cleanedName = if (stream.addonName.isNotBlank()) {
            stream.name.replace(stream.addonName, " ", ignoreCase = true)
        } else {
            stream.name
        }
        return "${stream.title} $cleanedName"
    }

    /** Metinden kanıta dayalı dil türü; kanıt yoksa null. */
    fun parseLangKindFromText(text: String?): StreamAudioKind? {
        if (text.isNullOrBlank()) return null
        val hasDub = DUB_REGEX.containsMatchIn(text)
        val hasSub = SUB_REGEX.containsMatchIn(text)
        return when {
            DUAL_REGEX.containsMatchIn(text) || (hasDub && hasSub) -> StreamAudioKind.DUAL
            hasDub -> StreamAudioKind.DUB
            hasSub -> StreamAudioKind.SUB
            else -> null
        }
    }

    private fun normalizeLangCode(raw: String?): String? {
        val value = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val base = value.substringBefore('-').substringBefore('_')
        return when (base) {
            "tur", "tr", "turkish", "türkçe", "turkce" -> "TR"
            "eng", "en", "english", "ingilizce" -> "EN"
            "jpn", "ja", "jp", "japanese", "japonca" -> "JA"
            "ger", "de", "deu", "german" -> "DE"
            "fre", "fra", "fr", "french" -> "FR"
            "spa", "es", "spanish" -> "ES"
            "ara", "ar", "arabic" -> "AR"
            "rus", "ru", "russian" -> "RU"
            else -> base.uppercase(Locale.ROOT).take(5)
        }
    }

    /**
     * Akışın dil bilgisini çözer. Sıralama: ölçülen > kaynak meta verisi > dosya adı > bilinmiyor.
     * Hiçbir kanıt yoksa [StreamAudioKind.UNKNOWN] döner ve arayüz rozet basmaz.
     */
    fun resolveLang(stream: StreamSource, measured: MeasuredStreamInfo? = null): StreamLangInfo {
        // Kaynağın gerçekten verdiği altyazı dosyaları (kanıtlanmış bilgi)
        val providerSubs = stream.subtitles
            .mapNotNull { normalizeLangCode(it.lang.ifBlank { it.name }) }
            .distinct()
        val measuredSubs = measured?.subtitleLanguages?.mapNotNull { normalizeLangCode(it) }?.distinct().orEmpty()
        val measuredAudio = measured?.audioLanguages?.mapNotNull { normalizeLangCode(it) }?.distinct().orEmpty()
        val subtitleLanguages = (providerSubs + measuredSubs).distinct()

        // 1) Ölçülmüş ses parçaları → en güvenilir
        if (measuredAudio.isNotEmpty()) {
            val kind = when {
                measuredAudio.size > 1 -> StreamAudioKind.DUAL
                subtitleLanguages.isNotEmpty() -> StreamAudioKind.SUB
                else -> StreamAudioKind.UNKNOWN
            }
            return StreamLangInfo(
                kind = kind,
                origin = StreamInfoOrigin.MEASURED,
                audioLanguages = measuredAudio,
                subtitleLanguages = subtitleLanguages
            )
        }

        // 2) Eklentinin kendi meta verisi (CloudStream DubStatus vb.)
        val providerKind = when (stream.providerAudioKind?.lowercase(Locale.ROOT)) {
            "dub", "dubbed" -> StreamAudioKind.DUB
            "sub", "subbed" -> StreamAudioKind.SUB
            "dual" -> StreamAudioKind.DUAL
            else -> null
        }
        if (providerKind != null) {
            return StreamLangInfo(
                kind = providerKind,
                origin = StreamInfoOrigin.PROVIDER,
                audioLanguages = stream.providerLanguages.mapNotNull { normalizeLangCode(it) }.distinct(),
                subtitleLanguages = subtitleLanguages
            )
        }

        // 3) Dosya / yayın adındaki açık etiketler
        parseLangKindFromText(languageText(stream))?.let { kind ->
            return StreamLangInfo(
                kind = kind,
                origin = StreamInfoOrigin.FILENAME,
                audioLanguages = emptyList(),
                subtitleLanguages = subtitleLanguages
            )
        }

        // 4) Ses hakkında bilgi yok ama kaynak gerçek altyazı dosyası verdi
        if (subtitleLanguages.isNotEmpty()) {
            return StreamLangInfo(
                kind = StreamAudioKind.SUB,
                origin = StreamInfoOrigin.PROVIDER,
                audioLanguages = emptyList(),
                subtitleLanguages = subtitleLanguages
            )
        }

        // 5) Gerçekten bilinmiyor
        return StreamLangInfo(StreamAudioKind.UNKNOWN, StreamInfoOrigin.NONE)
    }

    // ── Dosya boyutu ─────────────────────────────────────────────────────────

    private val SIZE_REGEX = Regex(
        """(\d+(?:[.,]\d+)?)\s?(gb|gib|mb|mib)""",
        RegexOption.IGNORE_CASE
    )

    /** Ölçülen veya kaynağın bildirdiği boyut etiketi; bilinmiyorsa null. */
    fun resolveSizeLabel(stream: StreamSource, measured: MeasuredStreamInfo? = null): String? {
        val measuredBytes = measured?.sizeBytes
        if (measuredBytes != null && measuredBytes > 0) return formatBytes(measuredBytes)
        val providerBytes = stream.videoSizeBytes
        if (providerBytes != null && providerBytes > 0) return formatBytes(providerBytes)
        val match = SIZE_REGEX.find("${stream.title} ${stream.name}") ?: return null
        return match.value.trim().uppercase(Locale.ROOT)
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) return String.format(Locale.ROOT, "%.2f GB", gb)
        if (bytes < 1_000L) return "$bytes B"
        if (bytes < 1_000_000L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1_000_000.0)
    }
}
