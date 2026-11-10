package com.kitsugi.animelist.core.regex

import android.util.Log
import java.util.regex.PatternSyntaxException

/**
 * **H9.v0 regex çökme düzeltmesi (v2.4.321).**
 *
 * Belirti: "İzle" akış ekranında ilk LazyList kartı oluşturulurken
 * `java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 51`
 * hatası fırlıyor ve uygulama ana iş parçacığından çöküyor. Hatalı regex:
 * ```
 * [.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}}
 *                                                          ^
 *                                                  index 51 (son karakter)
 * ```
 *
 * Kök neden: Harici/yerleşik bir kütüphanenin `object` / `companion object`
 * statik başlatıcısı içinde derlemeye çalıştığı bu regex, **Android ICU regex
 * motorunda (com.android.icu.util.regex)** JVM'den farklı olarak, bir niceleyiciden
 * (`{0,400}`) hemen sonra gelen **kaçırılmamış `}`** karakterini geçersiz addediyor.
 * Aynı regex OpenJDK `Pattern.compile` üzerinde çalışıyor ama Android cihazlarda
 * `<clinit>` aşamasında `ExceptionInInitializerError` veriyor.
 *
 * Bu sınıf düzeltmeyi 3 katmanda uygular:
 *  1. **Doğru yazılmış replacement regex** — sondaki `}` `\}` olarak kaçırılmıştır.
 *  2. **Warmup** — Uygulama açılır açılmaz çağrılarak doğru regex ısıtılır;
 *     bozuk regex de bilerek denenip hata yutulur (log'a yazılır).
 *  3. **Güvenli derleme yardımcısı** — dış kod regex derlerken güvenli fallback.
 */
object RegexSafetyWarmup {

    private const val TAG = "RegexSafetyWarmup"

    /** Bozuk (Android-ICU'da patlayan) regex'in loglarda görünen tam dizesi. */
    private const val BROKEN_CSS_RULE_REGEX_STR =
        "[.#]?[A-Za-z][A-Za-z0-9_\\-]{0,60}\\s*\\{[^{}]{0,400}}"

    /** DÜZELTİLMİŞ hal — son `}` `\}` olarak kaçırılmıştır. */
    const val CSS_RULE_REGEX_STR: String =
        "[.#]?[A-Za-z][A-Za-z0-9_\\-]{0,60}\\s*\\{[^{}]{0,400}\\}"

    /** Dış tüketiciler için güvenli, önceden derlenmiş regex. */
    val cssRuleRegex: Regex by lazy {
        try {
            Regex(CSS_RULE_REGEX_STR)
        } catch (pse: PatternSyntaxException) {
            Log.e(TAG, "Güvenli CSS regex de derlenemedi, fallback'e düşülüyor: ${pse.message}")
            FALLBACK_CSS_RULE_REGEX
        }
    }

    private val FALLBACK_CSS_RULE_REGEX: Regex =
        Regex("[.#]?[A-Za-z][A-Za-z0-9_\\-]{0,60}\\s*\\{[^\\{]*\\}")

    /** Uygulama açılışında çağrılır. */
    @JvmStatic
    fun warmUp() {
        // 1) Bozuk deseni bilerek derle ve hatayı burada yut.
        runCatching { Regex(BROKEN_CSS_RULE_REGEX_STR) }
            .onSuccess { Log.w(TAG, "Bozuk regex beklenmedik biçimde derlendi (JVM/CI ortamı).") }
            .onFailure { t ->
                Log.w(TAG, "Bozuk CSS-rule regex önceden tespit edildi (hata yutuldu): ${t.message}")
            }

        // 2) Doğru (düzeltilmiş) regex'i zorla derle.
        runCatching { cssRuleRegex }
            .onFailure { Log.e(TAG, "Güvenli CSS regex derlenemedi", it) }

        // 3) Android ICU'nun hassas olduğu bilinen benzer desenleri ısıt.
        listOf(
            "\\{[^{}]*\\}",
            "[A-Za-z][A-Za-z0-9_\\-]{0,60}\\s*\\{[^}]{0,400}\\}",
            "\\{[^{}]{0,400}\\}",
        ).forEach { pat ->
            runCatching { Regex(pat) }
                .onFailure { Log.e(TAG, "Isınma regex'i derlenemedi: $pat → ${it.message}") }
        }

        Log.i(TAG, "Warmup tamamlandı.")
    }

    /** HTML/CSS içindeki "selector { body }" biçimli kuralları döndürür. */
    fun findCssRulesIn(text: String): List<String> =
        cssRuleRegex.findAll(text).map { it.value }.toList()

    /** Bir regex'i güvenle derler; derlenemezse fallback döndürür. */
    fun compileSafely(pattern: String, fallback: Regex? = null): Regex =
        runCatching { Regex(pattern) }.getOrElse { firstErr ->
            // İkinci deneme: Android ICU'da JVM'den farklı olarak niceleyiciden
            // sonra gelen kaçırılmamış `}` patlıyorsa onu `\}` olarak kaçırarak
            // yeniden dene. Bunu YALNIZCA sonuncu `}` kaçırılmamışsa uygula.
            val sanitized = sanitizeForAndroidIcu(pattern)
            if (sanitized != null) {
                runCatching { Regex(sanitized) }.getOrElse { secondErr ->
                    Log.w(TAG, "Regex derlenemedi (orijinal + sanitize), fallback: '$pattern' → " +
                            "ilk=${firstErr.message}, ikinci=${secondErr.message}")
                    fallback ?: Regex("(?!)")
                }
            } else {
                Log.w(TAG, "Regex derlenemedi, fallback kullanılıyor: '$pattern' → ${firstErr.message}")
                fallback ?: Regex("(?!)")
            }
        }

    /**
     * Verilen regex desenini Android ICU'nun kabul edeceği biçimde düzeltmeye çalışır.
     * Özellikle niceleyiciden (`{d}` / `{d,d}`) sonra gelen kaçırılmamış `}`
     * karakterlerini `\}` olarak kaçırır.
     *
     * Düzeltme yapılamazsa `null` döndürür.
     */
    internal fun sanitizeForAndroidIcu(pattern: String): String? {
        if (pattern.isEmpty()) return null
        if (!pattern.endsWith("}")) return null
        if (pattern.length >= 2 && pattern[pattern.length - 2] == '\\') return null
        if (!pattern.endsWith("}}")) return null
        // Son iki karakter "}}" → ilki niceleyici kapanışı, ikincisi
        // kaçırılmamış literal `}`. İkinciyi kaçır.
        val head = pattern.dropLast(1)
        // head'in sonunun {n} / {n,m} niceleyici biçiminde olduğunu doğrula
        val qMatch = Regex("\\{\\d{1,3}(?:,\\d{1,3})?\\}$").find(head) ?: return null
        val qStart = qMatch.range.first
        if (qStart <= 0 || head[qStart - 1] == '\\') return null
        return head + "\\}"
    }
}
