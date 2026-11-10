package com.kitsugi.animelist.core.player

import java.net.URI
import java.util.Locale

/**
 * HLS master playlist (`master.m3u8`) içindeki **gerçek** video varyantlarından biri.
 *
 * NEDEN GEREKLİ: Rapidrame / closeload gibi Türkçe kaynaklar (HDFilmCehennemi,
 * FilmMakinesi) tek bir `master.m3u8` linki verir ve eklenti kaliteyi
 * `Qualities.Unknown` olarak bildirir. Gerçek kalite listesi yalnızca playlist'in
 * içindedir:
 *
 * ```
 * #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
 * index-v1.m3u8
 * ```
 *
 * Bu model, o satırlardan çözülen **oynatılabilir** varyant URL'sini ve onun
 * çözünürlüğünü taşır. Böylece oynatıcı "Kalite" sayfası tahmin değil, kaynağın
 * kendi bildirdiği varyantları listeleyebilir.
 */
data class HlsVariant(
    /** Oynatılabilir varyant (medya) playlist URL'si — master'a göre mutlaklaştırılmış. */
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    /** `BANDWIDTH` (bit/sn) — yoksa `AVERAGE-BANDWIDTH`. */
    val bandwidth: Long? = null,
    val codecs: String? = null,
    /** `NAME` / `#EXT-X-MEDIA` eşleşmesinden gelen etiket (varsa). */
    val name: String? = null,
    /** Yalnızca ses içeren varyant (video kodeği yok). Kalite listesinde gösterilmez. */
    val audioOnly: Boolean = false,
    /**
     * Ses, master'daki **ayrı bir `#EXT-X-MEDIA:TYPE=AUDIO`** rendition'ında duruyor.
     *
     * NEDEN ÖNEMLİ: Böyle bir master'da varyant (medya playlist) URL'sine doğrudan geçmek
     * videoyu getirir ama **sesi kaybeder** — ses ayrı bir playlist'te olduğu için oynatıcı
     * sessiz video açar. Bu yüzden bu varyantlarda kalite seçimi URL değiştirmek yerine
     * oynatıcı motorunun çözünürlük tavanıyla uygulanır (master adaptif kalır, ses korunur).
     */
    val requiresMasterAudio: Boolean = false,
) {
    /** Bu varyantın URL'sine geçmek güvenli mi? (ayrı ses grubu yoksa evet) */
    val canSwitchUrl: Boolean get() = !requiresMasterAudio

    /**
     * Kullanıcıya gösterilecek kalite etiketi: `1080p`, `720p` …
     *
     * Çözünürlük yoksa isim, o da yoksa bit hızı kullanılır; hiçbiri yoksa
     * uydurma bir değer basılmaz.
     */
    val label: String
        get() = when {
            height != null && height > 0 -> "${height}p"
            !name.isNullOrBlank() -> name.trim()
            bandwidth != null && bandwidth > 0 -> formatBandwidth(bandwidth)
            else -> ""
        }

    /** "4,5 Mbps" / "850 kbps" — yalnızca bilgi gerçekten varsa. */
    val bandwidthText: String?
        get() = bandwidth?.takeIf { it > 0 }?.let { formatBandwidth(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HlsVariant) return false
        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()

    companion object {
        fun formatBandwidth(bitsPerSecond: Long): String {
            if (bitsPerSecond <= 0L) return ""
            val mbps = bitsPerSecond / 1_000_000.0
            return if (mbps >= 1.0) {
                String.format(Locale.getDefault(), "%.1f Mbps", mbps)
            } else {
                "${bitsPerSecond / 1000} kbps"
            }
        }
    }
}

/**
 * HLS master playlist ayrıştırıcısı (saf Kotlin — ağ/Android bağımlılığı yok).
 *
 * Desteklenen satırlar:
 *  - `#EXT-X-STREAM-INF` (BANDWIDTH, AVERAGE-BANDWIDTH, RESOLUTION, CODECS, NAME, AUDIO)
 *  - `#EXT-X-MEDIA` (yalnızca `NAME` eşleştirmesi için okunur)
 *  - URI satırı (STREAM-INF'ten hemen sonraki ilk yorum olmayan satır)
 *
 * Medya playlist'i (varyant içermeyen `#EXTINF` listesi) verilirse **boş liste** döner:
 * böyle bir akışta seçilebilir kalite yoktur.
 */
object HlsMasterPlaylist {

    private const val MAX_VARIANTS = 40

    private val ATTR_REGEX = Regex("""([A-Za-z0-9\-]+)=("[^"]*"|[^,]*)""")
    private val HEIGHT_IN_URI_REGEX = Regex("""(?:^|[^0-9])(\d{3,4})p(?:[^0-9]|$)""", RegexOption.IGNORE_CASE)

    /**
     * Playlist gövdesini ayrıştırır.
     *
     * @param playlistUrl master playlist'in **gerçek** URL'si (göreli URI'leri mutlaklaştırmak için).
     * @param body HTTP ile indirilen playlist metni.
     * @return yükseklik/bit hızına göre **yüksekten alçağa** sıralı video varyantları.
     */
    fun parse(playlistUrl: String, body: String): List<HlsVariant> {
        if (body.isBlank() || !body.contains("#EXTM3U", ignoreCase = true)) return emptyList()
        if (!body.contains("#EXT-X-STREAM-INF", ignoreCase = true)) return emptyList()

        val lines = body.lineSequence().map { it.trim() }.toList()
        val measured = ArrayList<HlsVariant>(8)

        // Ayrı ses renditions'ı olan gruplar (URI'si dolu EXT-X-MEDIA:TYPE=AUDIO).
        // Bu gruplara bağlı varyantlarda URL değiştirmek sesi kaybettirir.
        val audioGroupsWithUri = HashSet<String>(4)
        for (line in lines) {
            if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) continue
            val attrs = parseAttributes(line.substringAfter(':', ""))
            val type = attrs["TYPE"]?.uppercase(Locale.ROOT)
            if (type != "AUDIO") continue
            if (attrs["URI"]?.unquoteOrNull().isNullOrBlank()) continue
            val groupId = attrs["GROUP-ID"]?.unquoteOrNull() ?: continue
            audioGroupsWithUri.add(groupId)
        }

        var pendingAttrs: Map<String, String>? = null

        for (line in lines) {
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pendingAttrs = parseAttributes(line.substringAfter(':', ""))
                continue
            }

            // EXT-X-MEDIA / EXT-X-I-FRAME-STREAM-INF gibi diğer etiketler URI beklemez;
            // I-frame (hızlı sarım) akışı kullanıcıya kalite olarak gösterilmez.
            if (line.startsWith("#")) continue

            val attrs = pendingAttrs ?: continue
            pendingAttrs = null
            val variant = buildVariant(playlistUrl, attrs, line, audioGroupsWithUri) ?: continue
            measured.add(variant)
            if (measured.size >= MAX_VARIANTS) break
        }

        return normalize(measured)
    }

    // ── Varyant kurulumu ─────────────────────────────────────────────────────

    private fun buildVariant(
        playlistUrl: String,
        attrs: Map<String, String>,
        uriLine: String,
        audioGroupsWithUri: Set<String>,
    ): HlsVariant? {
        val absolute = resolveUri(playlistUrl, uriLine) ?: return null

        val codecs = attrs["CODECS"]?.unquoteOrNull()
        val (width, heightFromAttrs) = parseResolution(attrs["RESOLUTION"])
        val name = attrs["NAME"]?.unquoteOrNull()
        val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull()
            ?: attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull()

        // RESOLUTION yoksa isimden/URI'den yükseklik çıkarılabilir (bazı CDN'ler
        // yalnızca `index_1080.m3u8` gibi adlar kullanır). Tahmin değil, ad ipucu.
        val height = heightFromAttrs
            ?: extractHeight(name)
            ?: extractHeight(uriLine)

        val audioGroup = attrs["AUDIO"]?.unquoteOrNull()
        val requiresMasterAudio = !audioGroup.isNullOrBlank() && audioGroupsWithUri.contains(audioGroup)

        return HlsVariant(
            url = absolute,
            width = width,
            height = height,
            bandwidth = bandwidth,
            codecs = codecs,
            name = name,
            audioOnly = isAudioOnlyCodecs(codecs),
            requiresMasterAudio = requiresMasterAudio,
        )
    }

    private fun parseResolution(raw: String?): Pair<Int?, Int?> {
        val value = raw?.unquoteOrNull() ?: return null to null
        val parts = value.lowercase(Locale.ROOT).split('x')
        if (parts.size != 2) return null to null
        return parts[0].toIntOrNull() to parts[1].toIntOrNull()
    }

    private fun extractHeight(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = HEIGHT_IN_URI_REGEX.find(text) ?: return null
        val h = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return h.takeIf { it in 144..4320 }
    }

    /**
     * Yalnızca ses kodeği bildiren varyantlar (ör. `CODECS="mp4a.40.2"`) kalite listesine
     * girmez: kullanıcı "kalite" seçtiğinde görüntü bekler, ses akışı seçmez.
     */
    private fun isAudioOnlyCodecs(codecs: String?): Boolean {
        val value = codecs?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (value.isEmpty()) return false
        val hasVideo = VIDEO_CODEC_HINTS.any { value.contains(it) }
        val hasAudio = AUDIO_CODEC_HINTS.any { value.contains(it) }
        return hasAudio && !hasVideo
    }

    // ── Normalizasyon ────────────────────────────────────────────────────────

    /**
     * Aynı çözünürlükte birden fazla varyant varsa en yüksek bit hızlısı korunur
     * (rapidrame gibi CDN'ler aynı yükseklik için farklı ses grupları/ölçekler sunar).
     * Aynı URL iki kez listelenmez.
     */
    private fun normalize(variants: List<HlsVariant>): List<HlsVariant> {
        val videoOnly = variants.filter { !it.audioOnly && it.url.isNotBlank() }
        if (videoOnly.isEmpty()) return emptyList()

        val byKey = LinkedHashMap<String, HlsVariant>()
        for (variant in videoOnly) {
            val key = when {
                variant.height != null -> "h${variant.height}"
                !variant.name.isNullOrBlank() -> "n${variant.name}"
                else -> "u${variant.url}"
            }
            val existing = byKey[key]
            if (existing == null || preferNewVariant(existing, variant)) {
                byKey[key] = variant
            }
        }

        return byKey.values.sortedWith(
            compareByDescending<HlsVariant> { it.height ?: 0 }
                .thenByDescending { it.bandwidth ?: 0L }
        )
    }

    /**
     * Aynı çözünürlükte iki varyant varsa (ör. H.264 ve HEVC) hangisi kalır?
     *
     * **H.264 (AVC) tercih edilir:** cihazların HEVC/AV1 çözücüsü her zaman yoktur;
     * en yüksek bit hızlı varyant HEVC ise seçim "ses var görüntü yok" durumuna yol açabilir.
     * AVC yoksa yüksek bit hızlı varyant kazanır.
     */
    private fun preferNewVariant(existing: HlsVariant, candidate: HlsVariant): Boolean {
        val existingAvc = isAvc(existing.codecs)
        val candidateAvc = isAvc(candidate.codecs)
        if (existingAvc != candidateAvc) return candidateAvc
        return (candidate.bandwidth ?: 0L) > (existing.bandwidth ?: 0L)
    }

    private fun isAvc(codecs: String?): Boolean =
        codecs?.lowercase(Locale.ROOT)?.trim()?.startsWith("avc") == true

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    private fun parseAttributes(raw: String): Map<String, String> {
        val map = LinkedHashMap<String, String>(8)
        ATTR_REGEX.findAll(raw).forEach { match ->
            val key = match.groupValues[1].uppercase(Locale.ROOT)
            val value = match.groupValues[2]
            if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
        }
        return map
    }

    private fun String.unquoteOrNull(): String? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        return trimmed.removePrefix("\"").removeSuffix("\"").takeIf { it.isNotBlank() }
    }

    /**
     * Göreli varyant URI'sini master'a göre mutlaklaştırır.
     *
     * İmzalı CDN'lerde (rapidrame: `.../master.m3u8?t=<token>`) varyant satırı çoğu zaman
     * sorgu dizesi taşımaz. Bu durumda master'ın sorgusu **korunur**; aksi hâlde CDN
     * 403 döner ve kalite seçimi "oynatıcı hatası" gibi görünür.
     */
    internal fun resolveUri(baseUrl: String, uri: String): String? {
        val target = uri.trim()
        if (target.isEmpty()) return null
        if (target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true)
        ) {
            return target
        }
        val resolved = runCatching { URI(baseUrl).resolve(target).toString() }.getOrNull() ?: return null
        if (resolved.startsWith("http", ignoreCase = true)) {
            val baseQuery = runCatching { URI(baseUrl).rawQuery }.getOrNull()
            if (!baseQuery.isNullOrBlank() && !resolved.contains('?')) {
                return "$resolved?$baseQuery"
            }
            return resolved
        }
        // Şema göreli (`//cdn/...`) URI'ler
        if (target.startsWith("//")) {
            val scheme = runCatching { URI(baseUrl).scheme }.getOrNull() ?: "https"
            return "$scheme:$target"
        }
        return null
    }

    private val VIDEO_CODEC_HINTS = listOf(
        "avc1", "avc3", "hvc1", "hev1", "vp08", "vp8", "vp09", "vp9", "av01", "dvh1", "dvhe"
    )

    private val AUDIO_CODEC_HINTS = listOf(
        "mp4a", "ac-3", "ac3", "ec-3", "ec3", "opus", "flac", "alac", "dtsc", "dtse"
    )
}
