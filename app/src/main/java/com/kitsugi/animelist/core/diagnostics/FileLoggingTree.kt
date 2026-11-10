package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object FileLoggingTree {
    private const val TAG = "KitsugiDebugLog"
    private const val LOG_DIR = "diagnostics"
    private const val LOG_FILE = "kitsugi_debug.log"
    private const val MAX_LOG_BYTES = 5 * 1024 * 1024L // 5 MB
    private const val REPEAT_WINDOW_MS = 3_000L

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var appContext: Context? = null

    // ── Yankı (echo) koruması ────────────────────────────────────────────────────
    /** Aynı anda iç içe günlük çağrısı olursa (logcat okuyucu kendini tekrar logluyor). */
    private val reentrancy = ThreadLocal.withInitial { false }

    /** Son yazılan satır + tekrar sayacı (gürültüyü azaltır). */
    private var lastSignature: String? = null
    private var lastSignatureAt = 0L
    private var lastSignatureRepeat = 0

    /**
     * Tekrar tespiti için imza üretirken **zaman damgalarını nötrler**.
     */
    private val TIMESTAMP_REGEX = Regex("""\d{1,2}:\d{2}:\d{2}\.\d{1,3}""")

    private fun signatureOf(message: String): Int =
        if (message.length <= 600) TIMESTAMP_REGEX.replace(message, "~TS~").hashCode()
        else message.hashCode()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    private fun formatDate(date: Date): String {
        return dateFmt.format(date)
    }

    fun log(priority: Int, tag: String?, message: String, throwable: Throwable? = null) {
        val safeTag = tag ?: "Global"

        // 1) Kendi ürettiğimiz satırların günlüğe geri yazılması = sonsuz büyüme.
        if (reentrancy.get() || LogEchoGuard.isEcho(safeTag, message)) return

        reentrancy.set(true)
        try {
            val exceptionStr = throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            
            when (priority) {
                Log.VERBOSE -> Log.v(safeTag, message, throwable)
                Log.DEBUG -> Log.d(safeTag, message, throwable)
                Log.INFO -> Log.i(safeTag, message, throwable)
                Log.WARN -> Log.w(safeTag, message, throwable)
                Log.ERROR -> Log.e(safeTag, message, throwable)
                else -> Log.d(safeTag, message, throwable)
            }

            // v2.4.342: oynatıcı, akış ve manga tanı satırları da **kalıcı** uygulama günlüğüne
            // düşsün. Böylece "Günlükler" ekranı ve paylaşılan günlük tek dosyadan okunur.
            runCatching {
                KitsugiLog.log(
                    level = runCatching { kitsugiLevelOf(priority) }.getOrDefault(KitsugiLog.Level.DEBUG),
                    channel = channelForTag(safeTag),
                    tag = safeTag,
                    message = message,
                    throwable = throwable
                )
            }

            val context = appContext ?: return
            val level = when (priority) {
                Log.VERBOSE -> "VERBOSE"
                Log.DEBUG -> "DEBUG"
                Log.INFO -> "INFO"
                Log.WARN -> "WARN"
                Log.ERROR -> "ERROR"
                Log.ASSERT -> "ASSERT"
                else -> "UNKNOWN"
            }

            val now = System.currentTimeMillis()
            val signature = "$level|$safeTag|${signatureOf(message)}"

            // 2) Aynı mesajın saniyede onlarca kez yazılması: tekilleştir.
            if (signature == lastSignature && now - lastSignatureAt < REPEAT_WINDOW_MS) {
                lastSignatureRepeat++
                lastSignatureAt = now
                return
            }
            val repeatSuffix = if (signature == lastSignature && lastSignatureRepeat > 0) {
                " (önceki mesaj ${lastSignatureRepeat + 1} kez tekrarlandı)"
            } else {
                ""
            }
            if (signature != lastSignature) {
                lastSignatureRepeat = 0
            }
            lastSignature = signature
            lastSignatureAt = now

            val logTime = Date(now)
            // 3) Dev satırlar belleği ve dosyayı şişirmesin.
            val safeMessage = LogEchoGuard.sanitize(message)
            val safeException = LogEchoGuard.sanitize(exceptionStr)

            logExecutor.execute {
                val line = "[${formatDate(logTime)}] [$level] [$safeTag] $safeMessage$safeException$repeatSuffix"
                synchronized(this) {
                    try {
                        val file = getOrCreateLogFile(context)
                        rotateIfNeeded(file)
                        FileWriter(file, true).use { fw ->
                            PrintWriter(fw).use { pw ->
                                pw.println(line)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write log to file: ${e.message}")
                    }
                }
            }
        } finally {
            reentrancy.set(false)
        }
    }

    fun v(tag: String?, message: String, throwable: Throwable? = null) = log(Log.VERBOSE, tag, message, throwable)
    fun d(tag: String?, message: String, throwable: Throwable? = null) = log(Log.DEBUG, tag, message, throwable)
    fun i(tag: String?, message: String, throwable: Throwable? = null) = log(Log.INFO, tag, message, throwable)
    fun w(tag: String?, message: String, throwable: Throwable? = null) = log(Log.WARN, tag, message, throwable)
    fun e(tag: String?, message: String, throwable: Throwable? = null) = log(Log.ERROR, tag, message, throwable)

    fun getLogFile(context: Context): File = getOrCreateLogFile(context)

    fun getLogFilePath(context: Context): String = getOrCreateLogFile(context).absolutePath

    fun flush() {
        val future = logExecutor.submit { /* flush drain */ }
        try {
            future.get(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun clearLogs(context: Context) {
        logExecutor.execute {
            synchronized(this) {
                try {
                    getOrCreateLogFile(context).writeText(
                        "=== Kitsugi Debug Log Dosyası Temizlendi: ${formatDate(Date())} ===\n"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Log temizlenemedi: ${e.message}")
                }
            }
        }
    }

    private fun getOrCreateLogFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        return File(dir, LOG_FILE).also { if (!it.exists()) it.createNewFile() }
    }

    /**
     * Dosya sınırı aşılınca son yarısını tutar. Belleğe tüm dosya okunmaz; son
     * [MAX_LOG_BYTES]/2 baytlık bölüm bir tamponla aktarılır (büyük günlüklerde OOM yok).
     */
    private fun rotateIfNeeded(file: File) {
        if (file.length() < MAX_LOG_BYTES) return
        val keepBytes = MAX_LOG_BYTES / 2
        val startAt = (file.length() - keepBytes).coerceAtLeast(0L)
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            RandomAccessFile(file, "r").use { input ->
                input.seek(startAt)
                // Yarım kalan satırı atla.
                if (startAt > 0L) input.readLine()
                tempFile.outputStream().buffered().use { output ->
                    output.write(
                        "=== Eski loglar temizlendi (boyut limiti 5MB): ${formatDate(Date())} ===\n"
                            .toByteArray()
                    )
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (tempFile.length() > 0L) {
                if (!tempFile.renameTo(file)) {
                    file.writeBytes(tempFile.readBytes())
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Log rotasyonu başarısız: ${e.message}")
            runCatching { tempFile.delete() }
            runCatching { file.writeText("=== Log dosyası sıfırlandı: ${formatDate(Date())} ===\n") }
        }
    }
}

/** `android.util.Log` önceliğini [KitsugiLog] seviyesine çevirir (v2.4.342). */
private fun kitsugiLevelOf(priority: Int): KitsugiLog.Level = when (priority) {
    Log.VERBOSE -> KitsugiLog.Level.VERBOSE
    Log.DEBUG -> KitsugiLog.Level.DEBUG
    Log.INFO -> KitsugiLog.Level.INFO
    Log.WARN -> KitsugiLog.Level.WARN
    Log.ERROR, Log.ASSERT -> KitsugiLog.Level.ERROR
    else -> KitsugiLog.Level.DEBUG
}

/** Etikete bakarak günlük kanalını tahmin eder (görüntüleyicideki konu filtresi için). */
private fun channelForTag(tag: String): KitsugiLog.Channel {
    val lower = tag.lowercase()
    return when {
        lower.contains("player") || lower.contains("stream") || lower.contains("mpv") ->
            KitsugiLog.Channel.PLAYER
        lower.contains("manga") || lower.contains("source") || lower.contains("plugin") ||
            lower.contains("cloudstream") -> KitsugiLog.Channel.SOURCE
        lower.contains("sync") || lower.contains("anilist") || lower.contains("mal") ||
            lower.contains("kitsu") || lower.contains("shikimori") || lower.contains("simkl") ||
            lower.contains("bangumi") -> KitsugiLog.Channel.SYNC
        lower.contains("net") || lower.contains("http") || lower.contains("api") ->
            KitsugiLog.Channel.NET
        else -> KitsugiLog.Channel.APP
    }
}
