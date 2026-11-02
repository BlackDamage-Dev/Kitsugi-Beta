package com.kitsugi.animelist.data.cloudstream.embed

import java.net.URI
import java.util.Locale

/**
 * FP-40 — EmbedMediaScanner
 *
 * CloudStream eklentileri çoğu zaman doğrudan `.mp4` / `.m3u8` URL'si vermez; onun yerine
 * bir **iframe/embed oynatıcı sayfası** verir (Vidmoly, Filemoon, Alions, CloseLoad, GStore,
 * TRsTX, Molystream, StreamBox, Pichive, Doodstream, Uqload, vdyn, bst, vb.).
 *
 * `loadExtractor` yalnızca kütüphaneye kayıtlı extractor'ları tanır. Türkçe provider'ların
 * kullandığı onlarca küçük CDN bu listede olmadığı için extractor boş döner ve eklenti
 * "boş" görünür.
 *
 * Bu sınıf, embed sayfasının HTML/JS içeriğinden gerçek medya URL'sini **tahmine dayanmadan**
 * (skorlanmış aday listesi + ayrı doğrulama) çıkarır. Saf Kotlin'dir (android.util.Log yok),
 * bu yüzden JVM birim testleriyle doğrulanabilir.
 *
 * Kapsam:
 *  - `<video>`, `<source>`, `file:`, `src:`, `source:`, `url:`, `hls:`, `dash:`, `playlist`
 *  - JWPlayer `setup({...})`, `sources: [{ file: ... }]`, `new Player(...)`
 *  - Kaçış dizileri: `\/`, `\u002F`, `\x2F`, `&amp;`, `&#38;`, `%2F`
 *  - `atob("...")` / uzun base64 blobları → çözüp yeniden tarama
 *  - `eval(function(p,a,c,k,e,d){...})` (Dean Edwards p.a.c.k.e.r) → unpack edip yeniden tarama
 *  - Protokolsüz (`//host/...`) ve göreli (`/x.m3u8`) adresleri mutlaklaştırma
 *  - iframe zinciri için aday iframe URL'leri (ayrı liste)
 *
 * @see <a href="https://github.com/recloudstream/cloudstream">recloudstream/cloudstream</a>
 */
object EmbedMediaScanner {

    /** Taranan sayfayı sınırla — devasa sayfalarda CPU/bellek patlamasını önler. */
    const val MAX_SCAN_CHARS: Int = 1_500_000

    private const val MAX_CANDIDATES = 40
    private const val MAX_IFRAMES = 8

    data class Candidate(
        val url: String,
        /** Yüksek = daha güvenilir. Aynı URL için en yüksek skor tutulur. */
        val score: Int,
        /** Nereden bulunduğu (log/teşhis için). */
        val reason: String
    )

    data class Result(
        val media: List<Candidate>,
        val iframes: List<String>,
        /** Gerçekten bir oynatıcı sayfası mı (WebView sniffer'ı hak ediyor mu)? */
        val looksLikePlayer: Boolean
    )

    // ── Genel API ─────────────────────────────────────────────────────────────

    /**
     * HTML/JS içeriğini tarar ve skor sırasına göre medya adayları döndürür.
     *
     * @param html    Embed sayfasının içeriği
     * @param baseUrl Aday URL'leri mutlaklaştırmak için kullanılan sayfa adresi
     */
    fun scan(html: String, baseUrl: String): Result {
        if (html.isBlank()) return Result(emptyList(), emptyList(), false)

        val page = if (html.length > MAX_SCAN_CHARS) html.substring(0, MAX_SCAN_CHARS) else html
        val scores = LinkedHashMap<String, Candidate>()   // url -> aday (sıralı — deterministik)
        val iframes = LinkedHashSet<String>()

        // 0a) Paketlenmiş (packer) JS varsa önce aç, sonra metni zenginleştir.
        val packed = unpackPackedJs(page)

        // 0b) Kaçışlı (escaped) metin: `https:\/\/host\/x.m3u8` biçiminde yazılmış URL'ler
        //     tırnak/bare URL regex'lerine takılmaz (ters bölü içerirler). Bu yüzden kaçışların
        //     çözüldüğü ikinci bir metin üretilir ve tarama ona da uygulanır. Yalnızca kaçış
        //     gerçekten varsa yapılır (büyük sayfalarda gereksiz CPU harcamamak için).
        val hasEscapes = page.contains("\\/") || page.contains("\\u002F", ignoreCase = true) ||
            page.contains("\\x2F", ignoreCase = true) || page.contains("\\u002f")
        val deEscaped = if (hasEscapes) decodeEscapes(page) else null

        val workingText = buildString(page.length + 1024) {
            append(page)
            if (packed != null) { append('\n'); append(packed) }
            if (deEscaped != null) { append('\n'); append(deEscaped) }
        }

        collectFromText(workingText, baseUrl, "raw", scores)
        collectStructured(workingText, baseUrl, scores)

        // atob / base64 blobları: çözülen içerik yeni URL'ler barındırabilir.
        decodeBase64Blobs(workingText)?.let { decoded ->
            collectFromText(decoded, baseUrl, "base64", scores)
            collectStructured(decoded, baseUrl, scores)
        }

        // iframe'ler: zincir takibi için ayrı liste (medya değil).
        collectIframes(workingText, baseUrl, iframes)

        val media = scores.values
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.url })
            .take(MAX_CANDIDATES)

        return Result(
            media = media,
            iframes = iframes.take(MAX_IFRAMES),
            looksLikePlayer = looksLikePlayerPage(workingText)
        )
    }

    /** HTML/JS içinde gerçek bir oynatıcı sayfası mı? (WebView sniffer kararı için) */
    fun looksLikePlayerPage(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return t.contains("<video") ||
            t.contains("jwplayer") ||
            t.contains("hls.js") ||
            t.contains("hls.min.js") ||
            t.contains("dash.js") ||
            t.contains("videojs") ||
            t.contains("clappr") ||
            t.contains("player.setup") ||
            t.contains("hlsurl") ||
            t.contains("sources:") ||
            t.contains("fluidplayer") ||
            t.contains("plyr") ||
            t.contains("apirequest") ||
            (t.contains("iframe") && t.contains("player"))
    }

    /** URL medya gibi görünüyor mu? (öbnizleyici/tıklama/sayfa değil) */
    fun looksLikeMediaUrl(url: String): Boolean {
        val u = url.lowercase(Locale.ROOT)
        if (!u.startsWith("http")) return false
        if (u.startsWith("data:") || u.startsWith("blob:")) return false
        val path = runCatching { URI(u).path.orEmpty() }.getOrDefault(u.substringBefore('?'))
        val lowerPath = path.lowercase(Locale.ROOT)

        if (HARD_DENY_EXTENSIONS.any { lowerPath.endsWith(it) }) return false
        if (DENY_URL_TOKENS.any { u.contains(it) }) return false

        if (MEDIA_EXTENSIONS.any { lowerPath.endsWith(it) }) return true
        // Uzantısız HLS endpoint'leri: /hls/ , /playlist/ , master.txt
        if (ALLOW_URL_TOKENS.any { u.contains(it) }) return true
        // m3u8/mp4 sorgu parametre içinde (ör. ?file=x.mp4) veya path içinde (ör. /x.mp4/index.m3u8)
        if (u.contains(".m3u8") || u.contains(".mp4") || u.contains(".mpd")) return true
        return false
    }

    // ── Toplama aşamaları ─────────────────────────────────────────────────────

    /** Tüm tırnaklı dizeler + çıplak http(s) URL'leri üzerinden geçer. */
    private fun collectFromText(
        text: String,
        baseUrl: String,
        reason: String,
        out: MutableMap<String, Candidate>
    ) {
        // 1) Tırnak içindeki adaylar (tek ve çift tırnak; kaçışlı tırnaklara izin ver)
        QUOTED_URL_REGEX.findAll(text).forEach { m ->
            consider(m.groupValues[1], baseUrl, reason, out)
        }

        // 2) Tırnaksız çıplak URL'ler
        BARE_URL_REGEX.findAll(text).forEach { m ->
            consider(m.value, baseUrl, "$reason:bare", out)
        }

        // 3) '/' ile başlayan göreli medya yolları (ör. \"/hls/1080/index.m3u8\")
        RELATIVE_URL_REGEX.findAll(text).forEach { m ->
            consider(m.groupValues[1], baseUrl, "$reason:rel", out)
        }
    }

    /** Etiketli / yapılandırılmış alanlar — bunlar en yüksek güvenilirlikte. */
    private fun collectStructured(
        text: String,
        baseUrl: String,
        out: MutableMap<String, Candidate>
    ) {
        for (regex in STRUCTURED_REGEXES) {
            regex.findAll(text).forEach { m ->
                val value = m.groupValues.getOrNull(1).orEmpty()
                consider(value, baseUrl, "structured", out, bonus = STRUCTURED_BONUS)
            }
        }
    }

    /** iframe src'leri: aynı medya değil ama oynatıcı zincirinde bir üst katman olabilir. */
    private fun collectIframes(text: String, baseUrl: String, out: MutableSet<String>) {
        IFRAME_REGEX.findAll(text).forEach { m ->
            val raw = m.groupValues[1]
            val absolute = absolutize(raw, baseUrl) ?: return@forEach
            if (absolute.startsWith("http") && !absolute.contains(baseUrl.substringBefore('?'), ignoreCase = true)) {
                out.add(absolute)
            }
        }
    }

    /**
     * Tek bir adayı değerlendirir: kaçışları çözer, mutlaklaştırır, skorlar ve kaydeder.
     */
    private fun consider(
        rawCandidate: String,
        baseUrl: String,
        reason: String,
        out: MutableMap<String, Candidate>,
        bonus: Int = 0
    ) {
        val decoded = decodeEscapes(htmlUnescape(rawCandidate)).trim()
        if (decoded.length < 8 || decoded.length > 4096) return
        val absolute = absolutize(decoded, baseUrl) ?: return
        if (!looksLikeMediaUrl(absolute)) return

        val score = scoreFor(absolute, decoded) + bonus
        if (score <= 0) return

        val existing = out[absolute]
        if (existing == null || existing.score < score) {
            out[absolute] = Candidate(absolute, score, reason)
        }
    }

    // ── Skorlama ──────────────────────────────────────────────────────────────

    /**
     * Adayın güvenilirlik skoru. Düşük skorlu adaylar elenir; yüksek skorlu adaylar
     * doğrulama (HEAD/GET) aşamasında ilk denenir.
     */
    fun scoreFor(absoluteUrl: String, originalCandidate: String = absoluteUrl): Int {
        val u = absoluteUrl.lowercase(Locale.ROOT)
        val path = runCatching { URI(u).path.orEmpty() }.getOrDefault(u.substringBefore('?'))
        var score = 0

        when {
            path.endsWith(".m3u8") -> score += 60
            u.contains(".m3u8") -> score += 50
            path.endsWith(".mpd") -> score += 45
            path.endsWith(".mp4") -> score += 40
            path.endsWith(".mkv") -> score += 35
            path.endsWith(".webm") -> score += 30
            path.endsWith(".flv") -> score += 20
            path.endsWith(".ts") -> score += 15
            path.endsWith("master.txt") || path.endsWith("playlist.txt") -> score += 55
            else -> score += 10
        }

        if (u.contains("/hls/")) score += 8
        if (u.contains("master")) score += 6
        if (u.contains("index.m3u8") || u.contains("playlist.m3u8")) score += 4
        if (u.contains("1080") || u.contains("720")) score += 3
        if (u.contains("http://")) score -= 2          // http, https'e göre daha kırılgan

        // Reklam / tıklama / analitik izleri
        if (DENY_URL_TOKENS.any { u.contains(it) }) score -= 100
        if (u.contains("trailer") || u.contains("fragman")) score -= 30
        // Küçük önizlemeler
        if (u.contains("preview") || u.contains("thumb") || u.contains("sprite")) score -= 40

        return score
    }

    // ── URL işlemleri ─────────────────────────────────────────────────────────

    /** Kaçışlı/göreli adayı mutlak http(s) URL'sine çevirir; başarısızsa null. */
    fun absolutize(candidate: String, baseUrl: String): String? {
        val trimmed = candidate.trim().trim('"', '\'', ',', ';', ')', ']', '}')
        if (trimmed.isBlank()) return null

        return try {
            when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed

                trimmed.startsWith("//") -> "https:$trimmed"

                else -> {
                    val base = URI(baseUrl)
                    val host = base.host ?: return null
                    val scheme = base.scheme ?: "https"
                    val resolved = if (trimmed.startsWith("/")) {
                        "$scheme://$host$trimmed"
                    } else {
                        val basePath = base.path.orEmpty().substringBeforeLast('/', "")
                        "$scheme://$host$basePath/$trimmed"
                    }
                    resolved
                }
            }.let { url ->
                // Bazı siteler &amp; ile bölünmüş URL verir; tekrar temizle.
                htmlUnescape(url).replace(" ", "%20")
            }
        } catch (_: Exception) {
            null
        }
    }

    /** `\/`, `\u002F`, `\x2F`, `&amp;`, `&#38;`, `&quot;` gibi kaçışları çözer. */
    fun decodeEscapes(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw

        if (s.contains('\\')) {
            s = s.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "")
                .replace("\\r", "")
                .replace("\\t", "")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003f", "?")
            s = UNICODE_ESCAPE_REGEX.replace(s) { m ->
                runCatching { m.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(m.value)
            }
            s = HEX_ESCAPE_REGEX.replace(s) { m ->
                runCatching { m.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(m.value)
            }
            s = s.replace("\\\\", "\\")
        }
        return s
    }

    /** HTML entity'lerini çözer (&amp;, &#38;, &#x26; …). */
    fun htmlUnescape(raw: String): String {
        if (!raw.contains('&')) return raw
        var s = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", "")
        s = NUMERIC_ENTITY_REGEX.replace(s) { m ->
            val body = m.groupValues[1]
            val code = when {
                body.startsWith("x", ignoreCase = true) -> body.drop(1).toIntOrNull(16)
                else -> body.toIntOrNull()
            }
            code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
        }
        return s.replace("%2F", "/").replace("%2f", "/").replace("%3F", "?").replace("%3D", "=")
    }

    // ── Packed JS (Dean Edwards p.a.c.k.e.r) ──────────────────────────────────

    /**
     * `eval(function(p,a,c,k,e,d){…}('PAYLOAD',N,N,'a|b|c'.split('|'),0,{}))` bloğunu çözer.
     * Bulamazsa null döner. CloudStream'in `getAndUnpack` eşdeğeridir ama burada saf Kotlin'dir.
     */
    fun unpackPackedJs(text: String): String? {
        val match = PACKED_JS_REGEX.find(text) ?: return null
        val payloadEscaped = match.groupValues[1]
        val a = match.groupValues[2].toIntOrNull() ?: return null
        val c = match.groupValues[3].toIntOrNull() ?: return null
        val words = match.groupValues[4].split('|')

        if (a < 1 || c <= 0 || words.isEmpty()) return null

        val payload = decodeJsString(payloadEscaped)

        fun encode(n: Int): String {
            val base = if (n < a) "" else encode(n / a)
            val digit = n % a
            return base + if (digit > 35) {
                String(Character.toChars(digit + 29))
            } else {
                digit.toString(36)
            }
        }

        val dictionary = arrayOfNulls<String>(c)
        for (i in c - 1 downTo 0) {
            dictionary[i] = words.getOrNull(i).takeIf { !it.isNullOrEmpty() } ?: encode(i)
        }

        // Packer semantiği: gövdedeki TOKEN (0,1,2…a,b,c…) sözlükteki KELİME ile değiştirilir.
        // (Eskiden kelime→kelime değişimi yapılıyordu; bu yüzden paketlenmiş sayfalarda
        //  hiçbir URL ortaya çıkmıyordu.)
        var result = payload
        for (i in c - 1 downTo 0) {
            val word = dictionary[i] ?: continue
            if (word.isEmpty()) continue
            val token = encode(i)
            if (token == word) continue
            result = result.replace(WORD_REGEX(token).toRegex(), Regex.escapeReplacement(word))
        }
        return result
    }

    /** JS string içindeki kaçışları çözer (paketlenmiş payload'ı normal metne çevirir). */
    private fun decodeJsString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\'', '"', '\\', '/' -> { sb.append(next); i += 2 }
                    'x' -> {
                        val hex = raw.substring(i + 2, minOf(i + 4, raw.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 4 } else { sb.append(next); i += 2 }
                    }
                    'u' -> {
                        val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 6 } else { sb.append(next); i += 2 }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }

    // ── Base64 blobları ───────────────────────────────────────────────────────

    /**
     * `atob("...")` çağrılarını ve uzun base64 benzeri dizeleri çözer.
     * Çözülen metin hâlâ http/m3u8 içermiyorsa dahil edilmez.
     */
    fun decodeBase64Blobs(text: String): String? {
        val decodedPrimary = StringBuilder()
        var found = false

        BASE64_CALL_REGEX.findAll(text).forEach { m ->
            decodeBase64Lenient(m.groupValues[1])?.let {
                decodedPrimary.append('\n').append(it)
                found = true
            }
        }

        if (!found) {
            BARE_BASE64_REGEX.findAll(text).take(6).forEach { m ->
                decodeBase64Lenient(m.value)?.let {
                    if (it.contains("http") || it.contains(".m3u8") || it.contains(".mp4")) {
                        decodedPrimary.append('\n').append(it)
                        found = true
                    }
                }
            }
        }
        return if (found) decodedPrimary.toString() else null
    }

    private fun decodeBase64Lenient(raw: String): String? {
        val cleaned = raw.trim().replace("\\", "").replace("\n", "").replace(" ", "")
            .replace('-', '+').replace('_', '/')
        if (cleaned.length < 24) return null
        return try {
            val padded = when (cleaned.length % 4) {
                2 -> "$cleaned=="
                3 -> "$cleaned="
                0 -> cleaned
                else -> return null
            }
            val bytes = androidBase64Decode(padded) ?: return null
            val asString = String(bytes, Charsets.UTF_8)
            // Sadece yazdırılabilir içerik anlamlıdır.
            val printable = asString.count { it.code in 9..13 || it.code in 32..126 || it.code > 160 }
            if (printable.toDouble() / asString.length.coerceAtLeast(1) < 0.7) null else asString
        } catch (_: Exception) {
            null
        }
    }

    /**
     * android.util.Base64 olmadan (JVM testleri için) saf Kotlin/Java Base64 çözümü.
     */
    private fun androidBase64Decode(value: String): ByteArray? = try {
        java.util.Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        try {
            java.util.Base64.getMimeDecoder().decode(value)
        } catch (_: Exception) {
            null
        }
    }

    // ── Regex tablosu ─────────────────────────────────────────────────────────

    private val QUOTED_URL_REGEX = Regex(
        """["']([^"'\s<>\\]{10,2048}?\.(?:m3u8|mp4|mpd|mkv|webm|ts|flv)(?:\?[^"'\s<>\\]{0,512})?)["']""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_URL_REGEX = Regex(
        """https?://[^\s"'<>\\]{10,2048}""",
        RegexOption.IGNORE_CASE
    )

    private val RELATIVE_URL_REGEX = Regex(
        """["'](/(?:[^"'\s<>\\]{0,256})\.(?:m3u8|mp4|mpd|mkv|webm)(?:\?[^"'\s<>\\]{0,512})?)["']""",
        RegexOption.IGNORE_CASE
    )

    private val STRUCTURED_BONUS = 12

    private val STRUCTURED_REGEXES = listOf(
        // JWPlayer / video.js / plyr: file: "url" | source: "url" | src: "url" | hls: "url"
        Regex("""(?:file|source|src|url|hls|hlslink|dash|playlist|video_url|videoUrl|link|m3u8|mp4)\s*[:=]\s*["']([^"'\s]{10,2048})["']"""),
        // <source src="..."> / <video src="...">
        Regex("""<(?:source|video|iframe)[^>]{0,200}?src\s*=\s*["']([^"']{10,2048})["']""", RegexOption.IGNORE_CASE),
        // data-file / data-src / data-video / data-url
        Regex("""data-(?:file|src|video|url|source|hls)\s*=\s*["']([^"'\s]{10,2048})["']""", RegexOption.IGNORE_CASE),
        // setSource("...") / loadSource("...") / player.src("...")
        Regex("""(?:setSource|loadSource|setUrl|loadUrl|setupVideo|videoPlayer)\s*\(\s*["']([^"'\s]{10,2048})["']""", RegexOption.IGNORE_CASE)
    )

    private val IFRAME_REGEX = Regex(
        """<iframe[^>]{0,300}?src\s*=\s*["']([^"']{6,2048})["']""",
        RegexOption.IGNORE_CASE
    )

    private val PACKED_JS_REGEX = Regex(
        """\}\s*\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val BASE64_CALL_REGEX = Regex(
        """(?:atob|ATOB|decode)\s*\(\s*["']([A-Za-z0-9+/=_\-]{24,8192})["']""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_BASE64_REGEX = Regex("""["']([A-Za-z0-9+/=]{64,4096})["']""")

    private val UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9a-fA-F]{4})""")
    private val HEX_ESCAPE_REGEX = Regex("""\\x([0-9a-fA-F]{2})""")
    private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]{1,6});""")

    private val MEDIA_EXTENSIONS = listOf(
        ".m3u8", ".mp4", ".mpd", ".mkv", ".webm", ".flv", ".ts", ".m4v", ".mov", ".avi",
        "master.txt", "playlist.txt"
    )

    private val HARD_DENY_EXTENSIONS = listOf(
        ".css", ".js", ".json", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
        ".woff", ".woff2", ".ttf", ".eot", ".html", ".htm", ".php", ".xml", ".txt.map",
        ".css.map", ".js.map", ".vtt", ".srt", ".ass", ".ssa"
    )

    private val DENY_URL_TOKENS = listOf(
        "doubleclick", "googlesyndication", "adservice", "adsystem", "adserver",
        "/ads/", "advert", "banner", "popunder", "onclick", "click?",
        "google-analytics", "googletagmanager", "facebook.com/tr",
        "pixel.gif", "beacon", "analytics", "histats", "yandex.ru/metrika",
        "sentry", "crashlytics"
    )

    private val ALLOW_URL_TOKENS = listOf(
        "/hls/", "/dash/", "/stream/", "/playlist/", "master.txt", "playlist.txt",
        "index.m3u8", "playlist.m3u8", "manifest.mpd"
    )

    /** Packer `\b<kelime>\b` regex'i için güvenli desen — kelime regex metakarakterlerini kaçırır. */
    private fun WORD_REGEX(word: String): String = """\b${Regex.escape(word)}\b"""
}
