package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Eklentilerden (CloudStream) gelen akış adı / başlıklarını **görüntülemeye uygun** hâle getirir.
 *
 * ## Neden gerekli (2026-11-01 kullanıcı raporu)
 * İzleme geçmişinde kaynak satırı şöyle görünüyordu:
 * ```
 * Yayın: HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL
 * ```
 * Bazı eklentiler kaynak adını sitenin HTML'inden `<span class="fa-secondary">` gibi
 * parçalarla birlikte (bazen CSS kuralının kendisini de) topluyor. Bu metin, oynatıcıda
 * ve izleme geçmişinde olduğu gibi gösterildiğinde kullanıcıya "bozuk arayüz" olarak
 * yansıyordu. Ayrıca aynı bilgi iki satırda tekrar ediyordu (`DUAL` iki kez, kaynak adı
 * iki kez).
 *
 * ## Kural
 * Burada **yalnızca kesin çöp** olan parçalar atılır; gerçek başlıklara dokunulmaz.
 *  - CSS kural blokları (`{ ... }` içinde `:` ya da `;` varsa) ve `@media`/`@keyframes`
 *    gibi @-kuralları silinir.
 *  - `<style>`/`<script>` gövdeleri ve kalan HTML etiketleri silinir.
 *  - HTML varlıkları (`&nbsp;` vb.) çözülür, sıfır genişlikli karakterler atılır.
 *  - Ayrılmış parçalar (`·`, `•`, `|`) tekilleştirilir, tekrar eden kelimeler sadeleşir.
 *  - Yalnızca nokta ile başlayan **CSS sınıfı görünümlü** parçalar (`.fa-secondary`) atılır;
 *    `.mp4` ya da `Spider-Man.No.Way.Home` gibi gerçek başlık parçaları korunur.
 */
object StreamLabelSanitizer {

    /** `.sinif{...}` / `#id{...}` / `eleman{...}` biçimindeki CSS kuralları. */
    private val CSS_RULE = Regex("""[.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}\}""")

    /** `@media (...) { ... }` gibi @-kurallar (tek seviye süslü parantez). */
    private val AT_RULE = Regex("""@[A-Za-z\-]+\s*[^{}]{0,120}\{[^{}]{0,400}\}""")

    private val STYLE_BLOCK = Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_BLOCK = Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HTML_TAG = Regex("""<[^>]{1,300}>""")
    private val ZERO_WIDTH = Regex("""[\u200B-\u200F\u202A-\u202E\uFEFF]""")
    private val INLINE_STYLE_ATTR = Regex("""style\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)
    private val CLASS_ATTR = Regex("""class\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)

    /** Sınıf görünümlü çıplak seçiciler: `.fa-secondary` (tire şart → gerçek başlıklara dokunmaz). */
    private val BARE_CSS_CLASS = Regex("""(?<![A-Za-z0-9_])\.([a-z][a-z0-9]*(?:-[a-z0-9]+)+)(?![A-Za-z0-9_])""")

    private val WHITESPACE = Regex("""\s+""")
    private val REPEATED_WORD = Regex("""(?i)\b([\p{L}\p{N}]{2,})\b(?:\s+\1\b)+""")

    private val ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&lt;" to "<",
        "&gt;" to ">",
        "&hellip;" to "…",
        "&middot;" to "·",
        "&ndash;" to "–"
    )

    /** Ham metni tek satırlık, okunabilir bir etikete dönüştürür. */
    fun sanitize(raw: String?): String {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return ""

        var text = input
        text = STYLE_BLOCK.replace(text, " ")
        text = SCRIPT_BLOCK.replace(text, " ")
        text = AT_RULE.replace(text, " ")
        text = CSS_RULE.replace(text) { match ->
            // Süslü parantez içi CSS bildirimi gibi görünüyorsa (":" ya da ";") at;
            // değilse koru — gerçek başlıklarda süslü parantez çok nadirdir ama
            // veri kaybı yaratmamak için kanıt aranır.
            val content = match.value.substringAfter('{', "")
            if (content.contains(':') || content.contains(';')) " " else match.value
        }
        text = INLINE_STYLE_ATTR.replace(text, " ")
        text = CLASS_ATTR.replace(text, " ")
        text = HTML_TAG.replace(text, " ")
        text = BARE_CSS_CLASS.replace(text, " ")
        for ((entity, replacement) in ENTITIES) {
            text = text.replace(entity, replacement, ignoreCase = true)
        }
        text = ZERO_WIDTH.replace(text, "")
        return tidy(text)
    }

    /**
     * Metni `·` / `•` / `|` parçalarına ayırır, boş ve tekrar eden parçaları atar.
     * Sıra korunur (ilk görülen kazanır).
     */
    fun segments(raw: String?): List<String> {
        val clean = sanitize(raw)
        if (clean.isEmpty()) return emptyList()
        // Tüm görsel ayırıcıları aynı karaktere indir, sonra parçala.
        val normalized = clean.replace('•', '·').replace('|', '·')
        val result = mutableListOf<String>()
        val seen = HashSet<String>()
        for (part in normalized.split('·')) {
            val trimmed = tidy(part)
            if (trimmed.isEmpty()) continue
            if (!seen.add(trimmed.lowercase(Locale.ROOT))) continue
            result += trimmed
        }
        return result
    }

    /**
     * İki etiketi (örn. eklenti adı + akış başlığı) tek satırda, **tekrarsız** birleştirir.
     * Örnek: `"HDFilmCehennemi · Close · … DUAL"` + `"Rapidrame · … DUAL"`
     *        → `"HDFilmCehennemi · Close · Rapidrame"`.
     */
    fun combine(primary: String?, secondary: String?, maxLength: Int = 120): String {
        val merged = mutableListOf<String>()
        val seen = HashSet<String>()
        for (source in listOf(primary, secondary)) {
            for (segment in segments(source)) {
                // Büyük/küçük harf farkı tekrar sayılmaz: "DUAL" ve "Dual" aynı bilgidir.
                if (seen.add(segment.lowercase(Locale.ROOT))) merged += segment
            }
        }
        if (merged.isEmpty()) return ""
        val joined = merged.joinToString(" · ")
        return if (joined.length <= maxLength) joined else joined.take(maxLength - 1).trimEnd() + "…"
    }

    /**
     * Ses türü kelimeleri.
     *
     * `\b` yerine `\p{L}` tabanlı sınır kullanılıyor: Java'nın varsayılan `\w` sınıfı
     * `ı/ş/ğ/ç/ö/ü` harflerini "kelime karakteri" saymadığı için `\baltyazılı\b`
     * eşleşmiyordu (Türkçe metinlerde sessizce çalışmayan bir kontrol).
     */
    private val AUDIO_TOKEN_REGEX = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(dual|dublajlı|dublajli|dublaj|dubbed|altyazılı|altyazili|altyazı|altyazi|subbed)(?![\\p{L}\\p{N}])"
    )

    /** Metinden ses türü etiketini (Dual / Dublaj / Altyazılı) çıkarır. */
    fun detectAudioLabel(raw: String?): String? {
        val lower = sanitize(raw).lowercase(Locale.ROOT)
        if (lower.isEmpty()) return null
        val tokens = AUDIO_TOKEN_REGEX.findAll(lower).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        return when {
            tokens.any { it == "dual" } -> "Dual"
            tokens.any { it.startsWith("dub") } -> "Dublaj"
            else -> "Altyazılı"
        }
    }

    /** Ses türü kelimelerini etiketten ayıklar (rozet olarak ayrıca gösterildiği için). */
    fun stripAudioTokens(raw: String): String {
        val text = sanitize(raw)
        if (text.isEmpty()) return text
        return tidy(AUDIO_TOKEN_REGEX.replace(text, " "))
    }

    /** Boşlukları ve ayırıcı artıklarını temizler. */
    private fun tidy(text: String): String {
        var out = WHITESPACE.replace(text, " ").trim()
        out = out.trim('·', '•', '|', ',', ';', '-', '—', '–', ' ')
        out = REPEATED_WORD.replace(out) { it.groupValues[1] }
        out = WHITESPACE.replace(out, " ").trim()
        return out
    }
}
