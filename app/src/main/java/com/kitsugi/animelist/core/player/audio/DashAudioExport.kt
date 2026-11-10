package com.kitsugi.animelist.core.player.audio

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.w3c.dom.Element
import java.io.File
import java.math.BigInteger
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/** Static single-period DASH (SegmentTemplate/Timeline, SegmentList or progressive BaseURL). */
internal object DashAudioExport {
    data class Part(val url: String, val range: AudioExportHttp.Range? = null)
    data class Plan(val parts: List<Part>)
    private const val MAX_PARTS = 25_000
    private val token = Regex("""\$(RepresentationID|Bandwidth|Number|Time)(?:%0(\d{1,2})d)?\$""")

    private fun children(node: Element, tag: String): List<Element> =
        (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }
            .filter { (it.localName ?: it.tagName.substringAfter(':')) == tag }

    private fun Element.attr(key: String): String? = getAttribute(key).takeIf { it.isNotBlank() }

    private fun base(parent: String, node: Element): String {
        val urls = children(node, "BaseURL")
        require(urls.size <= 1) { "Birden fazla DASH BaseURL desteklenmiyor" }
        return urls.singleOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { AudioExportHttp.resolve(parent, it) } ?: parent
    }

    private fun duration(value: String?): Long? = value?.let {
        try { Duration.parse(it).toMillis().also { ms -> require(ms > 0) } }
        catch (e: Exception) { throw IllegalArgumentException("DASH süresi geçersiz", e) }
    }

    private fun byteRange(value: String?): AudioExportHttp.Range? = value?.let {
        val parts = it.split('-')
        require(parts.size == 2) { "DASH byte aralığı geçersiz" }
        AudioExportHttp.Range(parts[0].toLong(), parts[1].toLong())
    }

    private fun expand(pattern: String, id: String, bandwidth: Long, number: Long, time: Long): String {
        val escaped = pattern.replace("\$\$", "\u0001")
        val result = token.replace(escaped) { match ->
            val value = when (match.groupValues[1]) {
                "RepresentationID" -> id
                "Bandwidth" -> bandwidth.toString()
                "Number" -> number.toString()
                else -> time.toString()
            }
            value.padStart((match.groupValues[2].toIntOrNull() ?: 0).coerceAtMost(16), '0')
        }
        require('$' !in result) { "Bilinmeyen DASH şablonu" }
        return result.replace("\u0001", "\$")
    }

    fun plan(xml: String, manifestUrl: String, identity: AudioTrackIdentity,
             playerTracks: List<AudioTrackIdentity>, preferredBitrate: Int? = null): Plan {
        require(xml.length <= AudioExportHttp.MAX_PLAYLIST_BYTES) { "DASH bildirimi çok büyük" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                override fun error(error: org.xml.sax.SAXParseException) { throw error }
                override fun fatalError(error: org.xml.sax.SAXParseException) { throw error }
            })
        }
        val document = builder.parse(xml.byteInputStream())
        val root = document.documentElement
        require((root.localName ?: root.tagName) == "MPD" && root.attr("type") != "dynamic") {
            "Canlı/geçersiz DASH bildirimi"
        }
        val periods = children(root, "Period")
        require(periods.size == 1) { "Çok dönemli DASH ayrı ses birleştirmesi henüz desteklenmiyor" }
        val period = periods.single()
        period.attr("start")?.let { start ->
            require(Duration.parse(start).isZero) { "Başlangıcı sıfır olmayan DASH dönemi desteklenmiyor" }
        }
        val sets = children(period, "AdaptationSet").filter { set ->
            set.attr("contentType") == "audio" || set.attr("mimeType")?.startsWith("audio/") == true ||
                children(set, "Representation").any { it.attr("mimeType")?.startsWith("audio/") == true }
        }
        fun lang(set: Element): String = set.attr("lang")
            ?: children(set, "Representation").mapNotNull { it.attr("lang") }.distinct().singleOrNull().orEmpty()
        val wanted = AudioExportSpec.language(identity.language)
        val selectedName = identity.name?.trim()?.takeIf { it.isNotBlank() }
        val named = sets.filter { set ->
            selectedName != null && children(set, "Label").any { it.textContent.trim().equals(selectedName, true) } &&
                (wanted.isEmpty() || lang(set).isBlank() || AudioExportSpec.language(lang(set)) == wanted)
        }
        val matching = sets.filter { wanted.isNotEmpty() && AudioExportSpec.language(lang(it)) == wanted }
        val needsName = AudioExportSpec.needsManifestName(identity, playerTracks)
        val sameFileRows = playerTracks.filter { it.externalUrl == identity.externalUrl }
        val set = when {
            named.size == 1 -> named.single()
            named.size > 1 || needsName -> error("DASH ses adı tek bir parça ile eşleşmiyor")
            matching.size == 1 -> matching.single()
            matching.size > 1 -> error("Aynı dilde birden çok DASH sesi var")
            sets.size == 1 && sameFileRows.size == 1 &&
                (wanted.isEmpty() || lang(sets.single()).isBlank() ||
                    AudioExportSpec.language(lang(sets.single())) == wanted) -> sets.single()
            else -> error("Seçilen ses DASH manifestiyle eşleşmiyor")
        }
        val representations = children(set, "Representation").filter { item ->
            wanted.isEmpty() || item.attr("lang")?.let { AudioExportSpec.language(it) == wanted } != false
        }
        require(representations.isNotEmpty()) { "Seçilen dilde DASH ses temsilcisi bulunamadı" }
        require(wanted.isNotEmpty() || representations.mapNotNull { it.attr("lang") }
            .map(AudioExportSpec::language).filter(String::isNotEmpty).distinct().size <= 1) {
            "DASH temsilcilerinde farklı ses dilleri var"
        }
        // A Representation is an ABR encoding, not necessarily another language/voice.
        // If the player exposes only ONE row for this language, use its highest uniquely
        // advertised bandwidth when the device did not supply an exact bitrate.
        val rep = if (representations.size == 1) representations.single() else {
            val exact = representations.filter { preferredBitrate != null &&
                it.attr("bandwidth")?.toIntOrNull() == preferredBitrate }
            require(exact.size <= 1) { "DASH ses kalitesi ayırt edilemiyor" }
            exact.singleOrNull() ?: run {
                val sameLanguageRows = sameFileRows.count {
                    AudioExportSpec.language(it.language) == wanted
                }
                require(sameFileRows.size == 1 || wanted.isNotEmpty() && sameLanguageRows == 1) {
                    "Birden çok DASH ses kalitesi arasında oynatıcı parçası ayırt edilemiyor"
                }
                val ranked = representations.mapNotNull { item ->
                    item.attr("bandwidth")?.toLongOrNull()?.takeIf { it > 0 }?.let { item to it }
                }.sortedByDescending { it.second }
                require(ranked.size == representations.size &&
                    ranked.size > 1 && ranked[0].second > ranked[1].second) {
                    "DASH ses kalitesi ayırt edilemiyor"
                }
                ranked.first().first
            }
        }
        val chain = listOf(root, period, set, rep)
        require(chain.none { it.hasAttributeNS("http://www.w3.org/1999/xlink", "href") }) {
            "DASH XLink desteklenmiyor"
        }
        require(chain.none { children(it, "ContentProtection").isNotEmpty() }) { "DRM korumalı DASH sesi çıkarılamıyor" }
        val base = chain.fold(manifestUrl) { acc, node -> base(acc, node) }
        val templates = chain.flatMap { children(it, "SegmentTemplate") }
        val lists = chain.flatMap { children(it, "SegmentList") }
        val segmentBases = chain.flatMap { children(it, "SegmentBase") }
        require(listOf(templates, lists, segmentBases).count { it.isNotEmpty() } <= 1) {
            "Karışık DASH bölümleme desteklenmiyor"
        }
        val parts = mutableListOf<Part>()
        fun add(url: String, range: AudioExportHttp.Range? = null) {
            require(parts.size < MAX_PARTS) { "DASH parça sınırı aşıldı" }
            parts += Part(AudioExportHttp.resolve(base, url), range)
        }
        if (templates.isNotEmpty()) {
            fun attribute(key: String) = templates.asReversed().firstNotNullOfOrNull { it.attr(key) }
            val media = attribute("media") ?: error("DASH media şablonu eksik")
            val init = attribute("initialization") ?: error("DASH init şablonu eksik")
            val timescale = attribute("timescale")?.toLongOrNull() ?: 1L
            require(timescale in 1..1_000_000_000L) { "DASH timescale geçersiz" }
            var number = attribute("startNumber")?.toLongOrNull() ?: 1L
            val endNumber = attribute("endNumber")?.toLongOrNull()
            require(number >= 0 && (endNumber == null || endNumber >= number)) { "DASH sıra numarası geçersiz" }
            val id = rep.attr("id").orEmpty()
            val bandwidth = rep.attr("bandwidth")?.toLongOrNull() ?: 0L
            fun url(template: String, time: Long) = expand(template, id, bandwidth, number, time)
            add(url(init, 0L))
            val timeline = templates.asReversed().firstNotNullOfOrNull { children(it, "SegmentTimeline").singleOrNull() }
            val ms = duration(period.attr("duration") ?: root.attr("mediaPresentationDuration"))
            val ticks = ms?.let { BigInteger.valueOf(it).multiply(BigInteger.valueOf(timescale))
                .divide(BigInteger.valueOf(1000)).longValueExact() }
            val offset = attribute("presentationTimeOffset")?.toLongOrNull() ?: 0L
            if (timeline != null) {
                val entries = children(timeline, "S")
                var time = 0L
                for ((index, item) in entries.withIndex()) {
                    time = item.attr("t")?.toLongOrNull() ?: time
                    val step = item.attr("d")?.toLongOrNull() ?: error("DASH zaman adımı eksik")
                    val repeat = item.attr("r")?.toLongOrNull() ?: 0L
                    require(time >= 0 && step > 0 && repeat >= -1) { "DASH zaman çizelgesi geçersiz" }
                    val count = if (repeat >= 0) repeat + 1 else {
                        val next = entries.getOrNull(index + 1)?.attr("t")?.toLongOrNull()
                        require(index == entries.lastIndex || next != null) { "DASH sonraki zaman sınırı eksik" }
                        val end = next ?: ticks?.let { Math.addExact(it, offset) }
                            ?: error("DASH dönem süresi eksik")
                        require(end > time) { "DASH zaman sınırı geçersiz" }
                        1 + (end - time - 1) / step
                    }
                    require(count in 1..MAX_PARTS.toLong() && parts.size + count <= MAX_PARTS) {
                        "DASH zaman çizelgesi çok uzun"
                    }
                    repeat(count.toInt()) {
                        if (endNumber == null || number <= endNumber) add(url(media, time))
                        time = Math.addExact(time, step)
                        number = Math.addExact(number, 1)
                    }
                }
            } else {
                val step = attribute("duration")?.toLongOrNull() ?: error("DASH parça süresi eksik")
                val count = when {
                    step <= 0 -> error("DASH parça süresi geçersiz")
                    endNumber != null -> endNumber - number + 1
                    ticks != null && ticks > 0 -> 1 + (ticks - 1) / step
                    else -> error("DASH bitiş süresi belirsiz")
                }
                require(count in 1..MAX_PARTS.toLong()) { "DASH parça sayısı geçersiz" }
                repeat(count.toInt()) { i ->
                    add(url(media, Math.addExact(Math.multiplyExact(i.toLong(), step), offset)))
                    number = Math.addExact(number, 1)
                }
            }
        } else if (lists.isNotEmpty()) {
            val init = lists.asReversed().firstNotNullOfOrNull { children(it, "Initialization").singleOrNull() }
            if (init != null) add(init.attr("sourceURL") ?: "", byteRange(init.attr("range")))
            val segments = lists.asReversed().firstNotNullOfOrNull { children(it, "SegmentURL").takeIf { list -> list.isNotEmpty() } }
                ?: error("DASH parça listesi boş")
            segments.forEach { item ->
                val media = item.attr("media")
                val range = byteRange(item.attr("mediaRange"))
                require(media != null || range != null) { "DASH parça adresi eksik" }
                add(media ?: "", range)
            }
        } else if (segmentBases.isNotEmpty()) {
            // SegmentBase/indexRange points inside ONE complete media file. Downloading the
            // Representation's whole BaseURL includes both init and media (not just sidx).
            // This is safer than concatenating byte ranges without an ISO BMFF index parser.
            require(children(rep, "BaseURL").singleOrNull()?.textContent?.isNotBlank() == true &&
                base != manifestUrl) { "DASH SegmentBase için ses dosyası adresi gerekli" }
            add("")
        } else {
            require(base != manifestUrl) { "DASH ses dosyası bulunamadı" }
            add("") // Static, progressive audio Representation/BaseURL.
        }
        require(parts.isNotEmpty() && (templates.isEmpty() && lists.isEmpty() || parts.size > 1)) {
            "DASH ses parçaları bulunamadı"
        }
        return Plan(parts)
    }

    suspend fun materialize(url: String, headers: Map<String, String>, identity: AudioTrackIdentity,
                            playerTracks: List<AudioTrackIdentity>, bitrate: Int?, output: File,
                            onProgress: suspend (Int) -> Unit, initial: Pair<String, String>? = null) {
        val (base, xml) = initial ?: AudioExportHttp.text(url, url, headers)
        val plan = plan(xml, base, identity, playerTracks, bitrate)
        var written = 0L
        output.outputStream().buffered().use { out ->
            plan.parts.forEachIndexed { index, part ->
                currentCoroutineContext().ensureActive()
                written += AudioExportHttp.append(url, part.url, headers, part.range, out, output.parentFile!!, written)
                onProgress(5 + (index + 1) * 65 / plan.parts.size)
            }
        }
    }
}
