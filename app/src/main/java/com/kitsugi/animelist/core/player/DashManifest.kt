package com.kitsugi.animelist.core.player

import java.util.Locale

/**
 * DASH manifest (`.mpd`) ayrıştırıcısı — yalnızca **video kaliteleri**.
 *
 * NEDEN: Bazı kaynaklar HLS yerine DASH sunar. DASH'te kaliteler tek bir URL içindeki
 * `<Representation height="1080" bandwidth="5000000"/>` düğümleridir; her kalite ayrı bir
 * playlist URL'si değildir. Bu yüzden seçim, oynatıcı motorunun **çözünürlük tavanı**
 * (`setMaxVideoHeight`) ile uygulanır ve [QualityOption.switchUrl] boş kalır.
 *
 * Ayrıştırma bilinçli olarak toleranslıdır: XML parser kullanılmaz (bazı CDN'ler bozuk
 * XML döner ve Android'in parser'ı tüm manifesti reddeder); `AdaptationSet` /
 * `Representation` nitelikleri metinden okunur. Karşılaştırma satır bazında yapıldığı için
 * iç içe yapı (Period → AdaptationSet → Representation) doğru eşleşir.
 */
object DashManifest {

    private const val MAX_OPTIONS = 40

    // Not: `"""...""""` (raw string + kapanış tırnağı) gibi kafa karıştıran kalıplar yerine
    // normal kaçışlı string kullanılır; hem okunur hem derleyici/araç dostudur.
    private val MIME_AUDIO = Regex("mimeType\\s*=\\s*\"audio/[^\"]*\"", RegexOption.IGNORE_CASE)
    private val CONTENT_TYPE = Regex("contentType\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    /**
     * Manifest gövdesini ayrıştırır.
     *
     * @return yüksekten alçağa sıralı, tekilleştirilmiş video kaliteleri. Manifest DASH
     *         değilse veya video Representation yoksa **boş liste** (uydurma yok).
     */
    fun parse(body: String): List<QualityOption> {
        if (body.isBlank() || !body.contains("<MPD", ignoreCase = true)) return emptyList()

        val options = ArrayList<QualityOption>(8)
        var inVideoAdaptation = false

        // Küçültülmüş (tek satır) manifestlerde düğümler `><` ile ayrılır; önce onları
        // satır başına böl ki etiket taraması çalışsın.
        normalizedLines(body).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("<AdaptationSet", ignoreCase = true)) {
                val contentType = CONTENT_TYPE.find(line)?.groupValues?.getOrNull(1)
                val isAudio = MIME_AUDIO.containsMatchIn(line) ||
                    contentType?.equals("audio", ignoreCase = true) == true
                // mimeType/contentType hiç yoksa alt Representation'ların kendi mimeType'ı
                // belirleyici olur; bu yüzden "ses değilse" video kabul edilir.
                inVideoAdaptation = !isAudio
                return@forEach
            }

            if (line.startsWith("</AdaptationSet", ignoreCase = true)) {
                inVideoAdaptation = false
                return@forEach
            }

            if (!line.startsWith("<Representation", ignoreCase = true)) return@forEach
            if (!inVideoAdaptation) return@forEach

            // Ses Representation'ı asla video kalitesi sayılmaz.
            if (MIME_AUDIO.containsMatchIn(line)) return@forEach
            val codecs = attr(line, "codecs")
            if (codecs != null && isAudioCodecs(codecs)) return@forEach

            val height = attr(line, "height")?.toIntOrNull()
            val width = attr(line, "width")?.toIntOrNull()
            val bandwidth = attr(line, "bandwidth")?.toLongOrNull()
            val id = attr(line, "id") ?: attr(line, "mimeType")

            val label = when {
                height != null && height > 0 -> "${height}p"
                width != null && width > 0 -> "${width}w"
                !id.isNullOrBlank() -> id.trim()
                else -> ""
            }
            if (label.isBlank()) return@forEach

            options.add(
                QualityOption(
                    label = label,
                    height = height?.takeIf { it in 144..4320 },
                    bandwidth = bandwidth?.takeIf { it > 0 },
                    switchUrl = null,
                )
            )
        }

        return normalize(options)
    }

    /** Manifest gövdesini satır bazlı tarama için normalize eder. */
    private fun normalizedLines(body: String): Sequence<String> =
        body.replace("><", ">\n<").lineSequence()

    /** Aynı yükseklikte en yüksek bit hızı kalır; liste yüksekten alçağa sıralanır. */
    private fun normalize(options: List<QualityOption>): List<QualityOption> {
        val byHeight = LinkedHashMap<String, QualityOption>()
        for (option in options) {
            val key = option.height?.let { "h$it" } ?: "l${option.label}"
            val existing = byHeight[key]
            if (existing == null || (option.bandwidth ?: 0L) > (existing.bandwidth ?: 0L)) {
                byHeight[key] = option
            }
        }
        return byHeight.values
            .sortedWith(
                compareByDescending<QualityOption> { it.height ?: 0 }
                    .thenByDescending { it.bandwidth ?: 0L }
            )
            .take(MAX_OPTIONS)
    }

    private fun attr(line: String, name: String): String? = Regex(
        "(?:^|\\s)$name\\s*=\\s*\"([^\"]*)\"",
        RegexOption.IGNORE_CASE
    ).find(line)?.groupValues?.getOrNull(1)

    private fun isAudioCodecs(codecs: String): Boolean {
        val value = codecs.lowercase(Locale.ROOT)
        val videoHints = listOf("avc", "hvc", "hev", "vp0", "av01", "dvh", "dva", "mp4v")
        return videoHints.none { value.contains(it) } &&
            listOf("mp4a", "ac-3", "ec-3", "opus", "flac", "dts").any { value.contains(it) }
    }
}
