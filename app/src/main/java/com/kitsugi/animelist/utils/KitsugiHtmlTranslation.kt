package com.kitsugi.animelist.utils

/**
 * HTML gövdedeki (AniList / MAL kullanıcı biyografileri) **yalnızca metin
 * düğümlerini** çevirir; etiketler, öznitelikler ve görseller olduğu gibi korunur.
 *
 * Wiki sayfası çevirisiyle aynı yaklaşım: her metin parçası ayrı ayrı çevrilir ve
 * parçalardan biri bile çevrilemezse orijinal HTML döndürülür (yarım/bozuk sayfa
 * göstermemek için). `<script>`, `<style>` ve `<code>` içerikleri çevrilmez.
 */
object KitsugiHtmlTranslation {

    private val tagName = Regex("(?s)^<\\s*/?\\s*([a-zA-Z0-9]+)")
    private val skipTags = setOf("script", "style", "code", "pre")
    private val letterRegex = Regex("\\p{L}")
    private val urlOnly = Regex("^(?:https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val entityOnly = Regex("^(?:&[a-zA-Z#0-9]+;|\\s)+$")

    /** Çevrilmeye değer metin düğümü mi? */
    private fun isTranslatable(chunk: String): Boolean {
        val trimmed = chunk.trim()
        if (trimmed.length < 2) return false
        if (urlOnly.matches(trimmed)) return false
        if (entityOnly.matches(trimmed)) return false
        // En az iki harf içermeli (yalnızca sayı/noktalama olan parçalar çevrilmez).
        return letterRegex.findAll(trimmed).take(2).count() == 2
    }

    /**
     * HTML'i etiketler (`<...>`) ve aralarındaki metin parçalarına sırayla ayırır.
     * Tüm parçalar birleştirildiğinde orijinal HTML eksiksiz elde edilir.
     */
    private fun tokenize(html: String): List<String> {
        val tokens = ArrayList<String>()
        var i = 0
        val len = html.length
        while (i < len) {
            if (html[i] == '<') {
                val end = html.indexOf('>', i)
                if (end != -1) {
                    tokens.add(html.substring(i, end + 1))
                    i = end + 1
                } else {
                    tokens.add(html.substring(i))
                    break
                }
            } else {
                val nextTag = html.indexOf('<', i)
                if (nextTag != -1) {
                    tokens.add(html.substring(i, nextTag))
                    i = nextTag
                } else {
                    tokens.add(html.substring(i))
                    break
                }
            }
        }
        return tokens
    }

    /**
     * HTML'i metin/etiket parçalarına ayırır ve çevrilebilir metin düğümlerinin
     * sıralı listesini döndürür.
     */
    fun extractTextSegments(html: String): List<String> {
        val result = ArrayList<String>()
        var skipDepth = 0
        for (part in tokenize(html)) {
            if (part.isEmpty()) continue
            if (part.startsWith("<")) {
                val name = tagName.find(part)?.groupValues?.getOrNull(1)?.lowercase()
                if (name != null && name in skipTags) {
                    val closing = part.startsWith("</")
                    if (closing) skipDepth = (skipDepth - 1).coerceAtLeast(0) else skipDepth++
                }
                continue
            }
            if (skipDepth > 0) continue
            if (isTranslatable(part)) result.add(part)
        }
        return result
    }

    /**
     * [extractTextSegments] ile aynı sırada ilerleyerek çevrilmiş parçaları
     * yerleştirir. [translations] eksikse orijinal parça korunur.
     */
    fun rebuild(html: String, translations: List<String?>): String {
        val builder = StringBuilder(html.length + 64)
        var skipDepth = 0
        var index = 0
        for (part in tokenize(html)) {
            if (part.isEmpty()) continue
            if (part.startsWith("<")) {
                val name = tagName.find(part)?.groupValues?.getOrNull(1)?.lowercase()
                if (name != null && name in skipTags) {
                    val closing = part.startsWith("</")
                    if (closing) skipDepth = (skipDepth - 1).coerceAtLeast(0) else skipDepth++
                }
                builder.append(part)
                continue
            }
            if (skipDepth > 0 || !isTranslatable(part)) {
                builder.append(part)
                continue
            }
            val translated = translations.getOrNull(index)
            index++
            if (translated != null) {
                val leading = part.takeWhile { it.isWhitespace() }
                val trailing = part.takeLastWhile { it.isWhitespace() }
                builder.append(leading).append(translated.trim()).append(trailing)
            } else {
                builder.append(part)
            }
        }
        return builder.toString()
    }

    /**
     * HTML'i çevirir.
     *
     * Bir düğüm çevrilemezse (ağ hatası, teknik metin) o düğüm orijinal hâliyle
     * bırakılır; hiçbir düğüm çevrilemezse null döndürülür. Böylece bağlantı ve
     * görseller korunur, biyografi tamamen kaybolmaz.
     */
    suspend fun translateHtml(
        html: String,
        translate: suspend (String) -> String?
    ): String? {
        if (html.isBlank()) return null
        val segments = extractTextSegments(html)
        if (segments.isEmpty()) return null
        val translations = ArrayList<String?>(segments.size)
        var translatedCount = 0
        for (segment in segments) {
            val translated = try {
                translate(segment)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!translated.isNullOrBlank()) translatedCount++
            translations.add(translated?.takeIf { it.isNotBlank() }?.trim())
        }
        if (translatedCount == 0) return null
        return rebuild(html, translations)
    }
}
