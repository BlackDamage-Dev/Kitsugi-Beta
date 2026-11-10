package com.kitsugi.animelist.core.diagnostics

/**
 * LogEchoGuard — günlük yankısı (log feedback loop) koruması.
 *
 * SORUN (2026-11-10 çökme raporları)
 * ──────────────────────────────────
 * `kitsugi_runtime (1) (4).log` dosyasının **tamamı** şu satırlardan oluşuyordu:
 *
 *     D/SYSTEM [logcat] 11-10 13:11:51.859 D/KitsugiLog( 3570): system|logcat|2026-11-10 13:11:51.859 D/SYSTEM [logcat] ...
 *
 * Yani logcat'i okuyan bileşen, okuduğu satırları yeniden `Log`'a veriyor; her yeni satır
 * bir öncekini içerdiği için dosya üstel büyüyor (821 KB → 2.7 MB), CPU ve bellek tüketiyor,
 * `crash_history` dosyası da aynı şekilde 1.3 MB'a şişiyor. Cihazda PSS 485 MB'a çıkıp süreç
 * arka planda sessizce öldürülüyor (ApplicationExitInfo: SwipeUpClean / OTHER-13).
 *
 * ÇÖZÜM
 * ─────
 * Bu nesne, kendi logcat çıktısını tekrar günlüğe yazan satırları tanır. Günlük yazan TÜM
 * bileşenler (FileLoggingTree, ileride eklenecek logcat yakalayıcıları) satırı yazmadan önce
 * [isEcho] kontrolünü yapmalıdır. Ayrıca [sanitize] ile tek satırlık dev kütükler kırpılır.
 */
object LogEchoGuard {

    /** Tek bir günlük satırı için üst sınır — dev satırlar dosyayı ve belleği şişirmesin. */
    const val MAX_LINE_CHARS = 4_000

    /** Yankıları yakalamak için kontrol edilecek azami derinlik (satırın başı yeterli). */
    private const val SCAN_CHARS = 1_200

    /**
     * [message] kendi günlük çıktımızın yeniden yazılması mı?
     *
     * @param tag günlük etiketi (logcat yakalayıcıları genelde "…Logcat…"/"KitsugiLog" kullanır)
     */
    fun isEcho(tag: String?, message: String): Boolean {
        if (message.isEmpty()) return false
        val head = if (message.length > SCAN_CHARS) message.substring(0, SCAN_CHARS) else message

        if (head.contains("system|logcat|")) return true
        if (head.contains("D/SYSTEM [logcat]")) return true
        if (head.contains("E/KitsugiLog(") || head.contains("D/KitsugiLog(")) return true

        // Kendi satır biçimimiz ([DEBUG] [...]) başka bir logcat satırının içinde geçiyorsa
        // bu da bir yankıdır.
        val ownFormat = head.contains("] [DEBUG] [") || head.contains("] [INFO] [") ||
            head.contains("] [WARN] [") || head.contains("] [ERROR] [")
        if (ownFormat && (head.contains("[logcat]") || tag?.contains("logcat", ignoreCase = true) == true)) {
            return true
        }

        // Aşırı tekrar eden "logcat" etiketli, aşırı uzun satırlar: yankı kabul et.
        return tag?.contains("logcat", ignoreCase = true) == true && message.length > MAX_LINE_CHARS
    }

    /** Satırı [maxChars] ile kırpar; kırpıldığını görünür biçimde bildirir. */
    fun sanitize(message: String, maxChars: Int = MAX_LINE_CHARS): String =
        if (message.length <= maxChars) message
        else message.take(maxChars) + "… (+${message.length - maxChars} karakter kırpıldı)"
}
