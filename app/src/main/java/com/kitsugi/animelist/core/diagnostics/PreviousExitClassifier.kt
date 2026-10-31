package com.kitsugi.animelist.core.diagnostics

/**
 * Önceki sürecin neden temiz kapanmadığını sınıflandırır (saf Kotlin → JVM birim testli).
 *
 * NEDEN (v2.4.369 raporları): `crash_history` içindeki 5 "TEMİZ OLMAYAN KAPANMA (SESSİZ
 * ÇÖKME)" kaydının 4'ü yanlış alarmdı:
 *  - 10:06 ve 14:57 → uygulama GÜNCELLENDİ (2.4.365 → 2.4.367 → 2.4.369); paket yükleyici
 *    eski süreci öldürdü. Logcat'teki `ClassLoaderContext classpath element mismatch` ve
 *    `Unable to resolve path .../base.apk` satırları bunun kanıtı.
 *  - 15:07:40 ve 16:20:54 → aynı süreçteki ön plan çökmesi, çökme yakalayıcısı tarafından
 *    ZATEN raporlanmıştı; gözetmen aynı ölümü ikinci kez "sessiz çökme" diye yazıyordu.
 *  - 08:40 ve 16:02 → uygulama arka plandayken sistem kapattı ("SEBEP BULUNAMADI"):
 *    uygulama süreçleri sistem günlüklerini okuyamaz; kesin neden Android 11+
 *    `ApplicationExitInfo` kaydındadır.
 */
internal object PreviousExitClassifier {

    // android.app.ApplicationExitInfo.REASON_* değerleri (API 30+; sabitler derleme
    // zamanında gömülür, eski API'lerde de güvenle kullanılabilir).
    const val REASON_UNKNOWN = 0
    const val REASON_EXIT_SELF = 1
    const val REASON_SIGNALED = 2
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_INITIALIZATION_FAILURE = 7
    const val REASON_PERMISSION_CHANGE = 8
    const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    const val REASON_USER_REQUESTED = 10
    const val REASON_USER_STOPPED = 11
    const val REASON_DEPENDENCY_DIED = 12
    const val REASON_OTHER = 13
    const val REASON_FREEZER = 14
    const val REASON_PACKAGE_STATE_CHANGE = 15
    const val REASON_PACKAGE_UPDATED = 16

    /** ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE — bu değer ve altı kullanıcıya görünür. */
    const val IMPORTANCE_VISIBLE = 200

    /** Paket güncelleme zamanı ile son kalp atışı arasındaki saat/yazma gecikmesi toleransı. */
    private const val UPDATE_CLOCK_SLACK_MS = 5_000L

    enum class Kind {
        /** Çökme yakalayıcısı bu süreç için zaten rapor yazdı → yineleme yok. */
        RECORDED_CRASH,
        /** Uygulama güncellendi / paket değişti → yükleyici kapattı, çökme değil. */
        APP_UPDATE,
        /** Kullanıcı kapattı (zorla durdur, son uygulamalardan kaydırma). */
        USER_CLOSED,
        ANR,
        JAVA_CRASH,
        NATIVE_CRASH,
        LOW_MEMORY,
        EXCESSIVE_RESOURCE_USAGE,
        INITIALIZATION_FAILURE,
        /** Sistem başka bir nedenle kapattı (sinyal, bağımlılık, izin değişimi, dondurucu…). */
        SYSTEM_KILL,
        /** Neden bilinmiyor. */
        UNKNOWN
    }

    data class Verdict(
        val kind: Kind,
        /** Kullanıcıya uyarı/çökme penceresi gösterilmeli mi? */
        val surface: Boolean,
        /** Önceki süreç kullanıcıya görünürken mi öldü (sistem kaydı varsa ona göre)? */
        val foreground: Boolean,
        val headline: String
    )

    /**
     * Önceki oturum ile şu an arasında uygulama güncellendi mi?
     *  - Sürüm adı değiştiyse kesin evet (her derleme `-beta.<zaman>` ekiyle benzersizdir).
     *  - Aynı sürüm yeniden kurulduysa paketin son güncellenme zamanı önceki sürecin son kalp
     *    atışından SONRA ise evet (yükleyici çalışan süreci öldürür).
     */
    fun isAppUpdate(
        previousBuild: String?,
        currentBuild: String,
        packageLastUpdateMs: Long,
        previousLastAliveMs: Long
    ): Boolean {
        val previous = previousBuild?.trim().orEmpty()
        if (previous.isNotEmpty() && previous != "?" && previous != currentBuild.trim()) return true
        return packageLastUpdateMs > 0L && previousLastAliveMs > 0L &&
            packageLastUpdateMs + UPDATE_CLOCK_SLACK_MS >= previousLastAliveMs
    }

    fun classify(
        heartbeatForeground: Boolean,
        crashAlreadyRecorded: Boolean,
        appUpdated: Boolean,
        exitReason: Int?,
        exitDescription: String?,
        exitImportance: Int?
    ): Verdict {
        // Sistem kaydı varsa "ön planda mıydı" kararı ona göre verilir: kalp atışı ~15 sn'de bir
        // diske yazıldığı için arka plana geçtikten hemen sonra öldürülen süreç yanlışlıkla
        // "ön planda" görünebiliyordu.
        val foreground = exitImportance?.let { it in 1..IMPORTANCE_VISIBLE } ?: heartbeatForeground

        if (crashAlreadyRecorded) {
            return Verdict(
                Kind.RECORDED_CRASH, surface = false, foreground = foreground,
                headline = "Yakalanan bir çökmeyle sonlandı — ayrıntılı çökme raporu zaten kaydedildi (yineleme atlandı)"
            )
        }
        if (appUpdated || exitReason == REASON_PACKAGE_UPDATED || exitReason == REASON_PACKAGE_STATE_CHANGE) {
            return Verdict(
                Kind.APP_UPDATE, surface = false, foreground = foreground,
                headline = "Uygulama güncellendi / paket değişti — eski süreci paket yükleyici kapattı (çökme DEĞİL)"
            )
        }
        val description = exitDescription.orEmpty().lowercase()
        return when (exitReason) {
            REASON_ANR -> Verdict(Kind.ANR, true, foreground,
                "ANR (UYGULAMA YANIT VERMEDİ) — ana iş parçacığı kilitlendi, sistem süreci sonlandırdı")
            REASON_CRASH -> Verdict(Kind.JAVA_CRASH, true, foreground,
                "JAVA ÇÖKMESİ (sistem kaydı) — yakalayıcıya ulaşmadan sonlandı")
            REASON_CRASH_NATIVE -> Verdict(Kind.NATIVE_CRASH, true, foreground,
                "NATIVE ÇÖKME (sistem kaydı — SIGSEGV/SIGABRT; oynatıcı/decoder/GPU/JNI katmanı)")
            REASON_EXCESSIVE_RESOURCE_USAGE -> Verdict(Kind.EXCESSIVE_RESOURCE_USAGE, true, foreground,
                "AŞIRI KAYNAK KULLANIMI — sistem süreci sonlandırdı (CPU/arka plan sınırı)")
            REASON_INITIALIZATION_FAILURE -> Verdict(Kind.INITIALIZATION_FAILURE, true, foreground,
                "BAŞLATMA HATASI — süreç başlatılırken sonlandı")
            REASON_LOW_MEMORY -> Verdict(Kind.LOW_MEMORY, surface = foreground, foreground = foreground,
                headline = if (foreground) "BELLEK YETERSİZLİĞİ — uygulama açıkken sistem kapattı (LMK)"
                else "Arka plandayken sistem bellek için kapattı (LMK) — normal Android davranışı")
            REASON_USER_REQUESTED, REASON_USER_STOPPED -> Verdict(Kind.USER_CLOSED, false, foreground,
                "Kullanıcı kapattı (zorla durdurma / son uygulamalardan kaydırma) — çökme değil")
            REASON_OTHER ->
                if ("remove task" in description || "swipe" in description) {
                    Verdict(Kind.USER_CLOSED, false, foreground,
                        "Son uygulamalardan kaydırılarak kapatıldı — çökme değil")
                } else {
                    Verdict(Kind.SYSTEM_KILL, surface = foreground, foreground = foreground,
                        headline = "Sistem kapattı (${exitDescription ?: "diğer"})")
                }
            null, REASON_UNKNOWN -> Verdict(Kind.UNKNOWN, surface = foreground, foreground = foreground,
                headline = if (foreground) "Neden bilinmiyor — uygulama ön plandayken sonlandı"
                else "Arka plandayken sonlandı (neden bilinmiyor; büyük olasılıkla sistem/arka plan kısıtlaması)")
            else -> Verdict(Kind.SYSTEM_KILL, surface = foreground, foreground = foreground,
                headline = "Sistem kapattı: ${reasonName(exitReason)}${exitDescription?.let { " ($it)" } ?: ""}")
        }
    }

    fun reasonName(reason: Int?): String = when (reason) {
        null -> "kayıt yok"
        REASON_UNKNOWN -> "UNKNOWN"
        REASON_EXIT_SELF -> "EXIT_SELF"
        REASON_SIGNALED -> "SIGNALED"
        REASON_LOW_MEMORY -> "LOW_MEMORY"
        REASON_CRASH -> "CRASH"
        REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        REASON_ANR -> "ANR"
        REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        REASON_USER_REQUESTED -> "USER_REQUESTED"
        REASON_USER_STOPPED -> "USER_STOPPED"
        REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        REASON_OTHER -> "OTHER"
        REASON_FREEZER -> "FREEZER"
        REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    /** Combines the authoritative system exit reason with weaker post-mortem logcat evidence. */
    fun combinedHeadline(verdict: Verdict, logcatHeadline: String): String = when {
        verdict.kind == Kind.ANR && logcatHeadline.startsWith("SEBEP BULUNAMADI") ->
            "ANR (SİSTEM KAYDI) — uygulama yanıt vermedi; logcat arabelleğinde ek iz yok\n" +
                "    ${verdict.headline.substringAfter(" — ", verdict.headline)}"
        verdict.kind == Kind.LOW_MEMORY && logcatHeadline.startsWith("SEBEP BULUNAMADI") ->
            "DÜŞÜK BELLEK (SİSTEM KAYDI) — LMKD; logcat arabelleğinde ek iz yok"
        else -> logcatHeadline
    }
}
