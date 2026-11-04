package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.model.SourceErrorCategory
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import java.util.Locale

/** Converts provider failures into a stable category/code without exposing raw text as the code. */
object SourceFailureClassifier {

    fun classifyCategory(error: Throwable?): SourceErrorCategory {
        val message = allFailureText(error)
        val classes = allFailureClasses(error)

        // Protection pages often return HTTP 403; identify the explicit platform signature first.
        return when {
            // KeiSource checks host interceptors by class name. This is a host ABI
            // failure, not a website CAPTCHA, even though the name says Cloudflare.
            message.contains("cloudflareinterceptor must be present") -> SourceErrorCategory.Unknown

            containsAny(
                message,
                "cloudflare", "captcha", "turnstile", "cf-ray", "just a moment", "ddos-guard", "ddos guard",
                "robot olmadığınızı", "robot olmadiginizi", "tarayıcı doğrulama", "tarayici dogrulama",
            ) || classes.any {
                it.contains("cloudflare", ignoreCase = true) ||
                    it.contains("captcha", ignoreCase = true) ||
                    it.contains("turnstile", ignoreCase = true) ||
                    it.contains("ddosguard", ignoreCase = true)
            } ->
                SourceErrorCategory.Captcha

            containsAny(
                message,
                "429", "too many requests", "rate limit", "rate-limited", "rate limited",
                "çok fazla istek", "cok fazla istek", "istek limiti", "istek sınırı", "istek siniri",
            ) ->
                SourceErrorCategory.RateLimited

            containsAny(
                message,
                "404", "not found", "no such chapter", "chapter does not exist", "page not found",
                "bölüm bulunamadı", "bolum bulunamadi", "sayfa bulunamadı", "sayfa bulunamadi",
            ) ->
                SourceErrorCategory.NotFound

            containsAny(
                message,
                "401", "403", "unauthorized", "forbidden", "authentication required", "login required",
                "giriş gerekli", "giris gerekli", "oturum aç", "oturum ac", "kimlik doğrulama gerekli",
                // Manga-TR 1.6.24 üye/bot kapısı: eklenti bu metinlerle IOException atar.
                // Genel "Broken/Unknown" yerine Auth olarak sınıflansın ki kullanıcıya
                // WebView doğrulaması önerilebilsin.
                "üye girişi", "uye girisi", "üye ol", "uye ol", "bot korumasını", "bot korumasini",
                "bot koruması", "bot korumasi", "verify.php", "oturum açmanız", "oturum acmaniz",
            ) ->
                SourceErrorCategory.Auth

            containsAny(
                message,
                "timeout", "timed out", "time out", "deadline exceeded",
                "zaman aşımı", "zaman asimi", "süre doldu", "sure doldu",
            ) ||
                classes.any { it.contains("timeout", ignoreCase = true) } ->
                SourceErrorCategory.Timeout

            containsAny(
                message,
                "unable to resolve host",
                "failed to connect",
                "connection reset",
                "connection refused",
                "network is unreachable",
                "no internet",
                "unknown host",
                "ssl handshake",
                "certificate verify",
                "dns lookup",
                "dns çözümlenemedi",
                "dns cozulemedi",
                "bağlantı kurulamadı",
                "baglanti kurulamadi",
            ) || classes.any {
                it.contains("unknownhost", ignoreCase = true) ||
                    it.contains("connectexception", ignoreCase = true) ||
                    it.contains("socketexception", ignoreCase = true) ||
                    it.contains("ssl", ignoreCase = true)
            } -> SourceErrorCategory.Network

            isParserFailure(message, classes) -> SourceErrorCategory.Parser
            else -> SourceErrorCategory.Unknown
        }
    }

    fun classifyCode(error: Throwable?): String? =
        PlatformErrorCode.forMangaCategory(classifyCategory(error))?.id

    fun classifyStatus(error: Throwable?): SourceHealthStatus = when (classifyCategory(error)) {
        SourceErrorCategory.RateLimited -> SourceHealthStatus.RateLimited
        SourceErrorCategory.Captcha -> SourceHealthStatus.CaptchaRequired
        SourceErrorCategory.NotFound,
        SourceErrorCategory.Timeout,
        SourceErrorCategory.Network,
        SourceErrorCategory.Auth,
        SourceErrorCategory.Parser,
        SourceErrorCategory.Unknown -> SourceHealthStatus.Degraded
        SourceErrorCategory.None -> SourceHealthStatus.Unknown
    }

    private fun allFailureText(error: Throwable?): String {
        if (error == null) return ""
        val seen = HashSet<Throwable>()
        val messages = buildList {
            var current: Throwable? = error
            while (current != null && seen.add(current)) {
                add(current.message.orEmpty())
                add(current.localizedMessage.orEmpty())
                current = current.cause
            }
        }
        return messages.joinToString(" ").lowercase(Locale.ROOT)
    }

    private fun allFailureClasses(error: Throwable?): List<String> {
        if (error == null) return emptyList()
        val seen = HashSet<Throwable>()
        return buildList {
            var current: Throwable? = error
            while (current != null && seen.add(current)) {
                add(current.javaClass.name)
                current = current.cause
            }
        }
    }

    private fun isParserFailure(message: String, classes: List<String>): Boolean {
        val parserClass = classes.any { className ->
            val name = className.lowercase(Locale.ROOT)
            name.contains("parseexception") ||
                name.contains("jsonparse") ||
                name.contains("jsonmapping") ||
                name.contains("serializationexception") ||
                name.contains("selector")
        }
        val parserMessage = containsAny(
            message,
            "parser error",
            "parse error",
            "parsing error",
            "failed to parse",
            "could not parse",
            "malformed html",
            "malformed json",
            "unexpected token",
            "selector not found",
            "no element found",
            "unable to extract",
            "failed to extract",
            "ayrıştırma hatası",
            "ayristirma hatasi",
            "ayrıştırılamadı",
            "ayristirilamadi",
        )
        return parserClass || parserMessage
    }

    private fun containsAny(value: String, vararg markers: String): Boolean =
        markers.any(value::contains)
}
