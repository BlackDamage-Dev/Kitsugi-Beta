package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.data.model.ExternalAudioTrack
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/**
 * Saf JVM uyumlu MPEG-DASH (`.mpd`) manifest ayrıştırıcısı.
 *
 * `Anizm`, `DiziBox`, `FilmEkseni`, `VidMoly`, `EksenLoad`, `VkExtractor`, `HDFilmIzle`
 * ve `DzenExtractor` eklentilerinin ürettiği `<SegmentTemplate>`, `<SegmentTimeline>`,
 * `<SegmentList>` ve `<BaseURL>` tabanlı DASH akışlarını hem video hem bağımsız ses
 * indirmeleri için çözümler.
 */
object DashManifestResolver {

    data class DashSegment(
        val url: String,
        val byteRange: HlsManifestResolver.ByteRange? = null
    )

    data class DashRepresentationPlan(
        val id: String,
        val trackKey: String,
        val isAudio: Boolean,
        val mimeType: String,
        val codecs: String,
        val language: String,
        val label: String,
        val bandwidth: Long,
        val width: Int,
        val height: Int,
        val channels: Int,
        val initSegment: DashSegment?,
        val mediaSegments: List<DashSegment>
    ) {
        val normalizedLang: String
            get() = ExternalAudioTrack.normalizeLangCode(
                language.ifBlank { ExternalAudioTrack.inferLanguageFromText("$label $id") }
            )
    }

    data class ParsedDashManifest(
        val manifestUrl: String,
        val durationSec: Double,
        val videoRepresentations: List<DashRepresentationPlan>,
        val audioRepresentations: List<DashRepresentationPlan>
    )

    fun parse(manifestUrl: String, xmlText: String): ParsedDashManifest? {
        val trimmed = xmlText.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (!trimmed.contains("<MPD", ignoreCase = true)) return null

        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(trimmed.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return null

        val mpdEl = doc.documentElement ?: return null
        val mpdDurationSec = parseIsoDurationSec(mpdEl.getAttribute("mediaPresentationDuration"))
        val mpdBase = childBaseUrl(mpdEl, manifestUrl)

        val periods = childElements(mpdEl, "Period")
        val firstPeriod = periods.firstOrNull() ?: return null
        val periodDurationSec = parseIsoDurationSec(firstPeriod.getAttribute("duration"))
            .takeIf { it > 0.0 } ?: mpdDurationSec
        val periodBase = childBaseUrl(firstPeriod, mpdBase)

        val videoPlans = ArrayList<DashRepresentationPlan>()
        val audioPlans = ArrayList<DashRepresentationPlan>()
        var audioIndex = 0

        for (asEl in childElements(firstPeriod, "AdaptationSet")) {
            val asBase = childBaseUrl(asEl, periodBase)
            val asMime = asEl.getAttribute("mimeType").trim()
            val asContentType = asEl.getAttribute("contentType").trim().lowercase(Locale.ROOT)
            val asCodecs = asEl.getAttribute("codecs").trim()
            val asLang = asEl.getAttribute("lang").trim()
            val asLabel = asEl.getAttribute("label").trim()
                .ifBlank { childElements(asEl, "Label").firstOrNull()?.textContent?.trim().orEmpty() }
            val asChannels = childElements(asEl, "AudioChannelConfiguration")
                .firstOrNull()?.getAttribute("value")?.toIntOrNull() ?: 0
            val asTemplate = childElements(asEl, "SegmentTemplate").firstOrNull()
            val asSegmentList = childElements(asEl, "SegmentList").firstOrNull()
            val asSegmentBase = childElements(asEl, "SegmentBase").firstOrNull()

            for (repEl in childElements(asEl, "Representation")) {
                val repId = repEl.getAttribute("id").trim()
                val repBase = childBaseUrl(repEl, asBase)
                val mime = repEl.getAttribute("mimeType").trim().ifBlank { asMime }
                val codecs = repEl.getAttribute("codecs").trim().ifBlank { asCodecs }
                val bw = repEl.getAttribute("bandwidth").trim().toLongOrNull() ?: 0L
                val width = repEl.getAttribute("width").trim().toIntOrNull()
                    ?: asEl.getAttribute("width").trim().toIntOrNull() ?: 0
                val height = repEl.getAttribute("height").trim().toIntOrNull()
                    ?: asEl.getAttribute("height").trim().toIntOrNull() ?: 0
                val channels = childElements(repEl, "AudioChannelConfiguration")
                    .firstOrNull()?.getAttribute("value")?.toIntOrNull() ?: asChannels

                val isAudio = asContentType == "audio" ||
                    mime.startsWith("audio/", ignoreCase = true) ||
                    (asContentType.isBlank() && mime.isBlank() && width == 0 && height == 0 &&
                        (codecs.startsWith("mp4a", true) || codecs.contains("opus", true) ||
                            codecs.contains("vorbis", true) || codecs.contains("ec-3", true) ||
                            codecs.contains("ac-3", true) || codecs.contains("flac", true)))
                val isVideo = !isAudio && (
                    asContentType == "video" ||
                        mime.startsWith("video/", ignoreCase = true) ||
                        height > 0 || width > 0 ||
                        codecs.startsWith("avc", true) || codecs.startsWith("hev", true) ||
                        codecs.startsWith("hvc", true) || codecs.startsWith("vp09", true) ||
                        codecs.startsWith("vp9", true) || codecs.startsWith("av01", true)
                    )
                if (!isAudio && !isVideo) continue

                val repTemplate = childElements(repEl, "SegmentTemplate").firstOrNull()
                val repSegmentList = childElements(repEl, "SegmentList").firstOrNull()
                val repSegmentBase = childElements(repEl, "SegmentBase").firstOrNull()

                val (initSeg, mediaSegs) = when {
                    repTemplate != null || asTemplate != null -> buildFromTemplate(
                        baseUrl = repBase,
                        asTemplate = asTemplate,
                        repTemplate = repTemplate,
                        repId = repId,
                        bandwidth = bw,
                        periodDurationSec = periodDurationSec
                    )
                    repSegmentList != null || asSegmentList != null -> buildFromSegmentList(
                        baseUrl = repBase,
                        segListEl = repSegmentList ?: asSegmentList!!
                    )
                    else -> buildFromBaseUrl(
                        baseUrl = repBase,
                        segBaseEl = repSegmentBase ?: asSegmentBase
                    )
                }

                if (mediaSegs.isEmpty() && initSeg == null) continue

                val normLang = ExternalAudioTrack.normalizeLangCode(
                    asLang.ifBlank { ExternalAudioTrack.inferLanguageFromText("$asLabel $repId") }
                )
                val key = if (isAudio) "${normLang.ifBlank { "und" }}_${audioIndex++}" else "vid_$repId"

                val plan = DashRepresentationPlan(
                    id = repId,
                    trackKey = key,
                    isAudio = isAudio,
                    mimeType = mime,
                    codecs = codecs,
                    language = asLang,
                    label = asLabel,
                    bandwidth = bw,
                    width = width,
                    height = height,
                    channels = channels,
                    initSegment = initSeg,
                    mediaSegments = mediaSegs
                )
                if (isAudio) audioPlans.add(plan) else videoPlans.add(plan)
            }
        }

        if (videoPlans.isEmpty() && audioPlans.isEmpty()) return null
        return ParsedDashManifest(
            manifestUrl = manifestUrl,
            durationSec = periodDurationSec,
            videoRepresentations = videoPlans,
            audioRepresentations = audioPlans
        )
    }

    fun selectBestVideo(
        manifest: ParsedDashManifest,
        preferredMaxHeight: Int? = null
    ): DashRepresentationPlan? {
        if (manifest.videoRepresentations.isEmpty()) return null
        val cmp = compareBy<DashRepresentationPlan>(
            { it.height },
            { it.bandwidth },
            { it.width }
        )
        if (preferredMaxHeight != null && preferredMaxHeight > 0) {
            val capped = manifest.videoRepresentations.filter { it.height in 1..preferredMaxHeight }
            if (capped.isNotEmpty()) return capped.maxWithOrNull(cmp)
        }
        return manifest.videoRepresentations.maxWithOrNull(cmp)
    }

    fun selectPreferredAudio(
        manifest: ParsedDashManifest,
        preferredVideoMime: String? = null,
        preferredLanguages: List<String> = listOf("tr", "ja", "en"),
        requestedKey: String? = null,
        requestedIndex: Int = -1,
        requestedLang: String? = null,
        requestedLabel: String? = null
    ): DashRepresentationPlan? {
        val all = manifest.audioRepresentations
        if (all.isEmpty()) return null

        if (!requestedKey.isNullOrBlank() && requestedKey != "default") {
            all.firstOrNull { it.trackKey.equals(requestedKey, ignoreCase = true) }?.let { return it }
        }

        // Aynı dil/etiket grubundaki temsiller arasında en yüksek bant genişlikli ve video kabıyla uyumlu olanı seç
        val groupedByTrack = groupDistinctAudioTracks(all, preferredVideoMime)
        if (requestedIndex in groupedByTrack.indices) {
            return groupedByTrack[requestedIndex]
        }
        if (!requestedLabel.isNullOrBlank()) {
            val clean = requestedLabel.trim()
            groupedByTrack.firstOrNull {
                it.label.equals(clean, ignoreCase = true) ||
                    (it.label.length >= 3 && clean.contains(it.label, ignoreCase = true))
            }?.let { return it }
        }
        val normReq = ExternalAudioTrack.normalizeLangCode(requestedLang)
        if (normReq.isNotBlank()) {
            groupedByTrack.firstOrNull { it.normalizedLang == normReq }?.let { return it }
        }
        for (pref in preferredLanguages) {
            val normPref = ExternalAudioTrack.normalizeLangCode(pref)
            if (normPref.isBlank()) continue
            groupedByTrack.firstOrNull { it.normalizedLang == normPref }?.let { return it }
        }
        return groupedByTrack.first()
    }

    /**
     * Aynı dil + etiket altındaki farklı bitrate temsillerini tek bir "indirilebilir ses parçası"
     * olarak birleştirir (en yüksek kaliteli/uyumlu temsili seçer).
     */
    fun groupDistinctAudioTracks(
        audioReps: List<DashRepresentationPlan>,
        preferredVideoMime: String? = null
    ): List<DashRepresentationPlan> {
        if (audioReps.isEmpty()) return emptyList()
        val preferWebm = preferredVideoMime?.contains("webm", ignoreCase = true) == true
        return audioReps
            .groupBy { "${it.normalizedLang}|${it.label.lowercase(Locale.ROOT)}" }
            .values
            .map { candidates ->
                candidates.maxWithOrNull(
                    compareBy<DashRepresentationPlan>(
                        { rep ->
                            val isWebmAudio = rep.mimeType.contains("webm", ignoreCase = true)
                            if (preferWebm == isWebmAudio) 1 else 0
                        },
                        { it.bandwidth }
                    )
                ) ?: candidates.first()
            }
    }

    private fun buildFromTemplate(
        baseUrl: String,
        asTemplate: Element?,
        repTemplate: Element?,
        repId: String,
        bandwidth: Long,
        periodDurationSec: Double
    ): Pair<DashSegment?, List<DashSegment>> {
        fun attr(name: String): String =
            repTemplate?.getAttribute(name)?.takeIf { it.isNotBlank() }
                ?: asTemplate?.getAttribute(name).orEmpty()

        val initAttr = attr("initialization")
            .ifBlank {
                (repTemplate?.let { childElements(it, "Initialization").firstOrNull() }
                    ?: asTemplate?.let { childElements(it, "Initialization").firstOrNull() })
                    ?.getAttribute("sourceURL").orEmpty()
            }
        val mediaAttr = attr("media")
        val timescale = attr("timescale").toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        val duration = attr("duration").toLongOrNull() ?: 0L
        val startNumber = attr("startNumber").toLongOrNull() ?: 1L

        val initSeg = initAttr.takeIf { it.isNotBlank() }?.let { tpl ->
            val resolved = resolveUrl(baseUrl, formatDashTemplate(tpl, repId, startNumber, 0L, bandwidth))
            DashSegment(url = resolved)
        }

        if (mediaAttr.isBlank()) {
            return initSeg to emptyList()
        }

        val timelineEl = repTemplate?.let { childElements(it, "SegmentTimeline").firstOrNull() }
            ?: asTemplate?.let { childElements(it, "SegmentTimeline").firstOrNull() }

        val segments = ArrayList<DashSegment>()
        if (timelineEl != null) {
            val sElements = childElements(timelineEl, "S")
            var currentTime = 0L
            var currentNumber = startNumber
            val totalScaledDuration = if (periodDurationSec > 0.0) {
                (periodDurationSec * timescale).toLong()
            } else {
                0L
            }
            for (idx in sElements.indices) {
                val sEl = sElements[idx]
                sEl.getAttribute("t").takeIf { it.isNotBlank() }?.toLongOrNull()?.let { currentTime = it }
                val segDur = sEl.getAttribute("d").toLongOrNull() ?: continue
                if (segDur <= 0L) continue
                val rawRepeat = sEl.getAttribute("r").toLongOrNull() ?: 0L
                val repeatCount = when {
                    rawRepeat >= 0L -> rawRepeat
                    else -> {
                        val nextStart = sElements.getOrNull(idx + 1)
                            ?.getAttribute("t")?.toLongOrNull()
                            ?: totalScaledDuration
                        if (nextStart > currentTime) ((nextStart - currentTime) / segDur - 1L).coerceAtLeast(0L) else 0L
                    }
                }
                val count = (repeatCount + 1L).coerceAtMost(20_000L)
                for (step in 0 until count) {
                    val url = resolveUrl(
                        baseUrl,
                        formatDashTemplate(mediaAttr, repId, currentNumber, currentTime, bandwidth)
                    )
                    segments.add(DashSegment(url = url))
                    currentTime += segDur
                    currentNumber++
                }
            }
        } else if (duration > 0L && periodDurationSec > 0.0) {
            val segDurationSec = duration.toDouble() / timescale.toDouble()
            val totalSegments = ceil(periodDurationSec / segDurationSec).toInt().coerceIn(1, 20_000)
            for (i in 0 until totalSegments) {
                val num = startNumber + i
                val time = i.toLong() * duration
                val url = resolveUrl(
                    baseUrl,
                    formatDashTemplate(mediaAttr, repId, num, time, bandwidth)
                )
                segments.add(DashSegment(url = url))
            }
        }

        return initSeg to segments
    }

    private fun buildFromSegmentList(
        baseUrl: String,
        segListEl: Element
    ): Pair<DashSegment?, List<DashSegment>> {
        val initEl = childElements(segListEl, "Initialization").firstOrNull()
        val initSeg = initEl?.let { el ->
            val src = el.getAttribute("sourceURL").takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
                ?: baseUrl
            val range = parseDashRange(el.getAttribute("range"))
            DashSegment(url = src, byteRange = range)
        }
        val mediaSegs = childElements(segListEl, "SegmentURL").map { el ->
            val src = el.getAttribute("media").takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
                ?: baseUrl
            val range = parseDashRange(el.getAttribute("mediaRange"))
            DashSegment(url = src, byteRange = range)
        }
        return initSeg to mediaSegs
    }

    private fun buildFromBaseUrl(
        baseUrl: String,
        segBaseEl: Element?
    ): Pair<DashSegment?, List<DashSegment>> {
        if (baseUrl.isBlank()) return null to emptyList()
        // Tek dosyalık <BaseURL> temsilinde tüm dosyayı tek segment olarak indir (sidx+moof+mdat dahildir)
        val initEl = segBaseEl?.let { childElements(it, "Initialization").firstOrNull() }
        val initRange = initEl?.getAttribute("range")?.let { parseDashRange(it) }
        // Eğer dosya tek bir URL ise tam akışı tek parça indirmek en güvenli yoldur
        return if (initRange == null || initEl.getAttribute("sourceURL").isBlank()) {
            null to listOf(DashSegment(url = baseUrl))
        } else {
            val initSrc = resolveUrl(baseUrl, initEl.getAttribute("sourceURL"))
            DashSegment(url = initSrc, byteRange = initRange) to listOf(DashSegment(url = baseUrl))
        }
    }

    internal fun formatDashTemplate(
        template: String,
        representationId: String,
        number: Long,
        time: Long,
        bandwidth: Long
    ): String {
        val sb = StringBuilder(template.length + 16)
        var i = 0
        while (i < template.length) {
            val ch = template[i]
            if (ch != '$') {
                sb.append(ch)
                i++
                continue
            }
            val end = template.indexOf('$', i + 1)
            if (end < 0) {
                sb.append(ch)
                i++
                continue
            }
            if (end == i + 1) {
                sb.append('$')
                i = end + 1
                continue
            }
            val token = template.substring(i + 1, end)
            when {
                token == "RepresentationID" -> sb.append(representationId)
                token.startsWith("Number") -> sb.append(formatNumberToken(token.removePrefix("Number"), number))
                token.startsWith("Time") -> sb.append(formatNumberToken(token.removePrefix("Time"), time))
                token.startsWith("Bandwidth") -> sb.append(formatNumberToken(token.removePrefix("Bandwidth"), bandwidth))
                else -> sb.append('$').append(token).append('$')
            }
            i = end + 1
        }
        return sb.toString()
    }

    private fun formatNumberToken(formatSuffix: String, value: Long): String {
        if (formatSuffix.isBlank()) return value.toString()
        val widthMatch = Regex("""%0?(\d+)d""").find(formatSuffix)
        val width = widthMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (width != null && width > 0) {
            value.toString().padStart(width, '0')
        } else {
            value.toString()
        }
    }

    private fun parseDashRange(raw: String?): HlsManifestResolver.ByteRange? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split('-', limit = 2)
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        if (end < start) return null
        return HlsManifestResolver.ByteRange(length = end - start + 1L, offset = start)
    }

    internal fun parseIsoDurationSec(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val match = Regex(
            """^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
            RegexOption.IGNORE_CASE
        ).find(raw.trim()) ?: return 0.0
        val days = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val hours = match.groupValues[4].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[5].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[6].toDoubleOrNull() ?: 0.0
        return days * 86400.0 + hours * 3600.0 + minutes * 60.0 + seconds
    }

    private fun childBaseUrl(element: Element, parentBase: String): String {
        val childBase = childElements(element, "BaseURL").firstOrNull()?.textContent?.trim()
        return if (childBase.isNullOrBlank()) parentBase else resolveUrl(parentBase, childBase)
    }

    private fun childElements(parent: Element, tagName: String): List<Element> {
        val out = ArrayList<Element>()
        var node: Node? = parent.firstChild
        while (node != null) {
            if (node is Element) {
                val local = node.tagName.substringAfterLast(':')
                if (local.equals(tagName, ignoreCase = true)) {
                    out.add(node)
                }
            }
            node = node.nextSibling
        }
        return out
    }

    private fun resolveUrl(baseUrl: String, childUrl: String): String =
        HlsManifestResolver.resolveUrl(baseUrl, childUrl)
}
