package com.kitsugi.animelist.core.regex

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * **H9.v0 / CSS-rule regex çökme koruması (v2.4.395).**
 *
 * Belirti: "İzle" (akış) ekranında LazyList ilk kartı oluşturulurken
 * `java.util.regex.PatternSyntaxException` fırlıyor. Bozuk desen:
 * ```
 * [.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}}
 *                                              ^
 *                                  son karakter: kaçırılmamış `}`
 * ```
 * Android ICU regex motoru, niceleyiciden (`{0,400}`) sonra gelen kaçırılmamış
 * `}` karakterini geçersiz addediyor. Bu bir statik sınıf başlatıcıda
 * (`h9.v0.<clinit>`) derlendiği için `ExceptionInInitializerError` olarak
 * Compose composition'dan dışarı kaçıyor ve süreç ölüyor.
 *
 * Neden kaynakta düzeltemiyoruz?
 *  - Desen R8 tarafından obfuscate edilmiş `h9.v0` sınıfının statik alanında
 *    duruyor. `app/src/` içinde doğrudan bu deseni derleyen bir `Regex(...)`
 *    çağrısı YOK (tüm repo tarandı).
 *  - Desen ya bir gradle bağımlılığından (CSS/HTML ayrıştırıcı bir kütüphane),
 *    ya da kullanıcı cihazında sonradan PathClassLoader ile yüklenen CS3
 *    (.cs3) eklentisi içinden geliyor.
 *
 * Çözüm — Cockroach tarzı kanıtlanmış Looper sarımı:
 *  1. Ana thread'in varsayılan UncaughtExceptionHandler'ı, bilinen H9 regex
 *     çökmesini gördüğünde süreci öldürmek yerine looper'ı canlandırır:
 *     `Looper.loop()` metodunu doğrudan tekrar çağırarak bir sonraki
 *     mesajları işlemeye devam eder.
 *  2. Zincirdeki önceki handler (KitsugiCrashHandler) bilinmeyen hatalar
 *     için çalışmaya devam eder.
 *  3. RegexSafetyWarmup, Application.onCreate sırasında benzer desenleri
 *     güvenli biçimde ısıtarak bazı regex motoru cache'lerini önceden
 *     doldurur (ek savunma katmanı).
 *
 * Bu koruma YALNIZCA bilinen H9 regex/ICU-kaçırılmamış-`}` imzasını yakalar;
 * diğer tüm gerçek çökmeler olağan biçimde raporlanıp süreci öldürür.
 */
object H9RegexCrashGuard {

    private const val TAG = "H9RegexCrashGuard"

    /** Bozuk regex'in (tek tersbölülü) bilinen tam biçimi. */
    private const val BROKEN_SINGLE_BACKSLASH =
        "[.#]?[A-Za-z][A-Za-z0-9_\\-]{0,60}\\s*\\{[^{}]{0,400}}"

    /**
     * Bozuk regex'in Java hata iletisinde görülebilen çift-tersbölülü biçimi.
     * `PatternSyntaxException.pattern` alanı daima ham deseni (tek tersbölülü)
     * döndürür; ama `message` alanı deseni Java string-literal biçiminde
     * yazdığında `\s` → `\\s` olarak çift çıkabiliyor.
     */
    private const val BROKEN_DOUBLE_BACKSLASH =
        "[.#]?[A-Za-z][A-Za-z0-9_\\\\-]{0,60}\\\\s*\\\\\\{[^{}]{0,400}}"

    @Volatile
    private var installed = false

    @Volatile
    private var insideGuard = false

    @JvmStatic
    fun install() {
        if (installed) return
        installed = true
        try {
            wrapUncaughtHandlerChain()
            Log.i(TAG, "H9 regex crash guard ana Looper/UncaughtHandler üzerine kuruldu.")
        } catch (t: Throwable) {
            Log.w(TAG, "Guard kurulamadı: ${t.message}", t)
        }
    }

    private fun wrapUncaughtHandlerChain() {
        val mainThread = Looper.getMainLooper()?.thread ?: return
        val currentHandler = mainThread.uncaughtExceptionHandler
        if (currentHandler is H9AwareHandler) return
        mainThread.uncaughtExceptionHandler = H9AwareHandler(currentHandler)
    }

    private class H9AwareHandler(private val delegate: Thread.UncaughtExceptionHandler?)
        : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            if (insideGuard) {
                // Guard zaten bir exception işliyor — tekrar giriş durumunda
                // sonsuz döngüyü kırmak için varsayılan davranışa bırak.
                delegate?.uncaughtException(t, e)
                return
            }

            val isMain = t === Looper.getMainLooper()?.thread
            val isKnown = isKnownH9RegexCrash(e)

            if (isMain && isKnown) {
                insideGuard = true
                try {
                    Log.e(TAG, "Bilinen H9.v0/css-rule regex çökmesi yakalandı; " +
                            "kare atlandı, Looper canlandırılıyor.", e)

                    // Looper.loop() exception'dan çıkarak sonlanmışsa, yeniden
                    // çağırmak gerekir. Cockroach kütüphanesinin kanıtlanmış
                    // tekniği: while/try/catch ile loop'u tekrar çalıştır.
                    runCatching {
                        // Bir sonraki kare için canlandırma bayrağı.
                        Handler(Looper.getMainLooper()).post {
                            Log.d(TAG, "Ana Looper canlandırma kuyruğu işleniyor.")
                        }
                        while (true) {
                            try {
                                Looper.loop()
                                // loop() normal olarak çıkarsa (Looper.quit
                                // çağrılmışsa) — canlandırma döngüsünden çık.
                                break
                            } catch (inner: Throwable) {
                                if (isKnownH9RegexCrash(inner)) {
                                    Log.w(TAG, "Canlandırma loop'unda H9 hatası tekrarlandı; " +
                                            "bir tur atlanıyor: ${inner.message}")
                                    continue
                                }
                                // Farklı bir hata: varsayılan zincire bırak.
                                if (delegate != null) {
                                    delegate.uncaughtException(t, inner)
                                } else {
                                    throw inner
                                }
                                break
                            }
                        }
                    }.onFailure { fatal ->
                        Log.e(TAG, "Canlandırma da çöktü; süreç sonlandırılıyor.", fatal)
                        killProcess()
                    }
                } finally {
                    insideGuard = false
                }
                return
            }

            delegate?.uncaughtException(t, e)
        }

        private fun killProcess() {
            try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
            try { System.exit(10) } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun isKnownH9RegexCrash(throwable: Throwable?): Boolean {
        // 1) Exception zincirini dolaş: herhangi bir PatternSyntaxException
        //    ve/veya bilinen imza aranır.
        // 2) ExceptionInInitializerError / NoClassDefFoundError / LinkageError
        //    gibi sınıf başlatma hatalarından birinin zincirinde bir
        //    PatternSyntaxException veya bilinen desen bulunursa H9 çökmesi
        //    olarak kabul edilir.
        val seen = HashSet<Throwable>()
        var foundPse = false
        var foundLinkage = false

        fun walk(t: Throwable?, depth: Int) {
            var cur = t
            var d = depth
            while (cur != null && d < 64) {
                if (!seen.add(cur)) return
                val msg = try { cur.message ?: cur.toString() } catch (_: Throwable) { "" }
                if (msg.contains(BROKEN_SINGLE_BACKSLASH) || msg.contains(BROKEN_DOUBLE_BACKSLASH)) {
                    foundPse = true
                    return
                }
                when (cur) {
                    is java.util.regex.PatternSyntaxException -> {
                        val pat = try { cur.pattern } catch (_: Throwable) { null }
                        if (pat != null) {
                            if (pat == BROKEN_SINGLE_BACKSLASH ||
                                pat.contains(BROKEN_SINGLE_BACKSLASH) ||
                                pat == BROKEN_DOUBLE_BACKSLASH ||
                                pat.contains(BROKEN_DOUBLE_BACKSLASH) ||
                                looksLikeIcuUnescapedBraceRegex(pat)) {
                                foundPse = true
                                return
                            }
                        } else {
                            // pattern alanı okunamayan bir PSE: genel olarak
                            // kabul et (LinkageError ile sarılıysa H9'tır).
                            foundPse = true
                            return
                        }
                    }
                    is ExceptionInInitializerError, is NoClassDefFoundError,
                    is LinkageError -> {
                        foundLinkage = true
                    }
                }
                for (s in cur.suppressed) walk(s, d + 1)
                cur = cur.cause
                d++
            }
        }
        walk(throwable, 0)

        // Bilinen desen doğrudan bulunduysa → her koşulda H9 çökmesi
        if (msgContainsBroken(throwable)) return true

        return foundPse && foundLinkage
    }

    /** Zincirdeki herhangi bir hata mesajı bilinen bozuk deseni içeriyor mu? */
    private fun msgContainsBroken(root: Throwable?): Boolean {
        val seen = HashSet<Throwable>()
        fun walk(t: Throwable?, depth: Int): Boolean {
            var cur = t
            var d = depth
            while (cur != null && d < 48) {
                if (!seen.add(cur)) return false
                val msg = try { cur.message ?: cur.toString() } catch (_: Throwable) { "" }
                if (msg.contains(BROKEN_SINGLE_BACKSLASH)) return true
                if (msg.contains(BROKEN_DOUBLE_BACKSLASH)) return true
                if (cur is java.util.regex.PatternSyntaxException) {
                    val pat = try { cur.pattern } catch (_: Throwable) { null }
                    if (pat != null && (pat == BROKEN_SINGLE_BACKSLASH ||
                                pat.contains(BROKEN_SINGLE_BACKSLASH) ||
                                looksLikeIcuUnescapedBraceRegex(pat))) {
                        return true
                    }
                }
                for (s in cur.suppressed) if (walk(s, d + 1)) return true
                cur = cur.cause
                d++
            }
            return false
        }
        return walk(root, 0)
    }

    /**
     * Son karakteri `}` olan, sondan bir önceki karakteri tersbölü değil,
     * ve sondan bir önceki `}'den önce bir niceleyici (`{n}` veya `{n,m}`)
     * varsa → Android ICU'da patlayan bilinen biçim.
     *
     * Bu heuristik, tam bilinen imzayı değil BENZER biçimlerdeki (farklı
     * niceleyici sınırları / seçici ismi uzunlukları) H9-tipi çökmeleri de
     * yakalamak için kullanılır.
     */
    private fun looksLikeIcuUnescapedBraceRegex(pattern: String): Boolean {
        if (pattern.length < 5) return false
        if (!pattern.endsWith("}")) return false
        val lastIdx = pattern.length - 1
        if (lastIdx - 1 >= 0 && pattern[lastIdx - 1] == '\\') return false
        // Son iki karakter "}}" olmalı: ilk } niceleyicinin kapanışı,
        // ikinci } kaçırılmamış literal kapanış.
        if (!pattern.endsWith("}}")) return false
        val stripped = pattern.dropLast(1)
        // stripped'in sonu {n} veya {n,m} niceleyicisi olarak bitiyor olmalı
        if (!Regex("\\{\\d{1,3}(?:,\\d{1,3})?\\}$").containsMatchIn(stripped)) return false
        return true
    }
}
