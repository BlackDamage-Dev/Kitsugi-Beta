package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.kitsugi.animelist.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * **KitsugiLog** — uygulamanın kalıcı, eksiksiz ve çökmeden çalışan günlüğü (v2.4.342).
 *
 * ## Neden yeniden yazıldı?
 * Kullanıcı geri bildirimi: *"arka planda olan sorunları o sırada elle kopyalayamıyorum,
 * günlük bölümü her şeyi alabilmeli"*. Eski durum:
 *  - `DeveloperLogsDialog` yalnızca `logcat -d` okuyordu. Android 10+ üzerinde uygulama
 *    yalnızca **kendi** süreç günlüğünü görebilir, ayrıca logcat halka tamponu dolduğunda
 *    (yoğun kullanımda dakikalar içinde) **eski satırlar yok olur**. Yani "az önce ne oldu"
 *    sorusunun kanıtı çoğu zaman silinmiş oluyordu.
 *  - Dosyaya yazan tek yol (`FileLoggingTree`) yalnızca oynatıcı ve birkaç tanı noktasından
 *    çağrılıyordu; eşitleme/ağ/worker hataları dosyaya **hiç** girmiyordu.
 *  - Aynı satır saniyede yüzlerce kez tekrarlandığında dosya şişip okumayı kilitliyordu.
 *
 * Bu nesne tüm uygulama için tek yazma yoludur:
 *  - **Asla ana iş parçacığını bekletmez:** satır önce bellek halkasına, sonra tek bir arka
 *    plan iş parçacığına bırakılır. Kuyruk dolarsa satır **atılır ve sayılır**; çağıran asla
 *    bloklanmaz, OOM/ANR üretmez.
 *  - **Kalıcıdır:** `files/diagnostics/kitsugi_runtime.log`, 4 MB'da döner (1 yedek), yani en
 *    fazla ~8 MB. Uygulama kapansa da, çökse de satırlar durur.
 *  - **Tekrarları sıkıştırır:** aynı satır 1,5 sn içinde tekrarlanırsa tek satır +
 *    `(×N)` yazılır; hata döngüleri dosyayı boğamaz.
 *  - **Kendisi asla patlamaz:** her genel API `try/catch` içindedir; günlük yazamazsa
 *    sessizce `android.util.Log`'a düşer.
 *
 * Kayıt biçimi (görüntüleyici ve raporlar bunu ayrıştırır):
 * ```
 * 2026-10-31 14:03:22.193  W/SYNC    [anilist] istek sınırı doldu | detay
 * ```
 */
object KitsugiLog {

    /** Günlük dosyası adı (dizin: `files/diagnostics`). */
    const val FILE_NAME = "kitsugi_runtime.log"

    private const val LOG_DIR = "diagnostics"
    private const val BACKUP_SUFFIX = ".1"

    /** Tek dosya boyutu sınırı; aşılınca dosya `…log.1` olarak döner. */
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    /** Bellekte tutulan son satır sayısı (görüntüleyici önce buraya bakar). */
    private const val RING_CAPACITY = 3_000

    /** Yazma kuyruğu kapasitesi. Dolarsa satır atılır (bloklamak yasak). */
    private const val QUEUE_CAPACITY = 4_096

    private const val MAX_MESSAGE_CHARS = 4_000
    private const val MAX_STACK_CHARS = 6_000

    /** Günlük satırlarının logcat'e yazılırken kullandığı **tek** etiket.
     *  [LogcatCapture] bu etiketi görünce satırı atlar (çift kayıt olmasın). */
    const val LOGCAT_TAG = "KitsugiLog"

    /** Günlük seviyesi. Harf, görüntüleyicideki `W/`, `E/` filtresiyle uyumludur. */
    enum class Level(val letter: Char, private val priority: Int) {
        VERBOSE('V', Log.VERBOSE),
        DEBUG('D', Log.DEBUG),
        INFO('I', Log.INFO),
        WARN('W', Log.WARN),
        ERROR('E', Log.ERROR),
        FATAL('F', Log.ASSERT);

        /** `android.util.Log` önceliği (logcat'e aynı seviyeyle yazmak için). */
        fun toLogPriority(): Int = priority
    }

    /**
     * Kayıt kanalı. Görüntüleyicide "hangi konu" filtresi olarak kullanılır; dosyada
     * dile bağımlı olmayan kısa anahtar (`sync`, `net` …) olarak yazılır.
     */
    enum class Channel(val key: String) {
        APP("app"),        // genel uygulama olayları
        SYNC("sync"),      // liste eşitleme (AniList, MAL, Kitsu, Shikimori, Simkl, Bangumi)
        NET("net"),        // ağ istekleri/yanıtları ve hata kodları
        SOURCE("source"),  // manga / eklenti / CloudStream kaynakları
        WORK("work"),      // arka plan işleri (WorkManager, boşaltma, yenileme)
        PLAYER("player"),  // oynatıcı + akış çözümleme
        CRASH("crash"),    // çökme ve yakalanmamış istisnalar
        FREEZE("freeze"),  // donma / ana iş parçacığı kilitlenmesi
        USER("user"),      // kullanıcı eylemleri (ekmek kırıntısı)
        SYSTEM("system")   // sistem günlüğü (logcat köprüsü) ve süreç olayları
    }

    // ─── Durum ─────────────────────────────────────────────────────────────────

    @Volatile private var appContext: Context? = null
    private val initialized = AtomicBoolean(false)

    private val ring = ArrayDeque<String>(RING_CAPACITY)
    private val ringLock = Any()

    private val droppedByQueue = AtomicLong(0)
    private val suppressedRepeats = AtomicLong(0)
    private val writtenLines = AtomicLong(0)

    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val executor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(QUEUE_CAPACITY),
            { runnable ->
                Thread(runnable, "KitsugiLogIO").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            },
            // Kuyruk dolu: satırı at ama SAY. Bloklamak/asla beklemek yok.
            ThreadPoolExecutor.DiscardPolicy()
        )
    }

    // Yalnızca yazma iş parçacığından erişilir → kilit gerekmez.
    private var writer: BufferedWriter? = null
    private var writerFile: File? = null
    private var currentSize: Long = 0L
    private var lastLine: String? = null
    private var lastLineAt: Long = 0L
    private var repeatCount: Int = 0

    // ─── Kurulum ───────────────────────────────────────────────────────────────

    /**
     * Uygulama başlangıcında (attachBaseContext/onCreate) **bir kez** çağrılır.
     * Tekrar çağrı zararsızdır. Asla istisna fırlatmaz.
     */
    fun init(context: Context) {
        try {
            appContext = context.applicationContext ?: context
            if (!initialized.compareAndSet(false, true)) return
            // Oturum başlığı: sürüm, Android sürümü, cihaz, PID. Paylaşılan günlükte
            // "hangi sürümde oldu" sorusunu cevaplar.
            val header = sessionHeader(context)
            pushToRing(header)
            runCatching { executor.execute { writeLine(header) } }
        } catch (_: Throwable) {
            // Günlük kurulamazsa uygulama yine çalışsın.
        }
    }

    private fun sessionHeader(context: Context): String {
        val version = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("?")
        val device = runCatching { "${Build.MANUFACTURER} ${Build.MODEL}" }.getOrDefault("?")
        val stamp = timeFormat.get()!!.format(Date())
        return "$stamp  I/SYSTEM  [oturum] === GÜNLÜK BAŞLADI | sürüm $version | " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | $device | süreç ${android.os.Process.myPid()} ==="
    }

    // ─── Genel yazma API'si ────────────────────────────────────────────────────

    fun v(channel: Channel, tag: String, message: String, detail: String? = null, throwable: Throwable? = null) =
        log(Level.VERBOSE, channel, tag, message, detail, throwable)

    fun d(channel: Channel, tag: String, message: String, detail: String? = null, throwable: Throwable? = null) =
        log(Level.DEBUG, channel, tag, message, detail, throwable)

    fun i(channel: Channel, tag: String, message: String, detail: String? = null, throwable: Throwable? = null) =
        log(Level.INFO, channel, tag, message, detail, throwable)

    fun w(channel: Channel, tag: String, message: String, detail: String? = null, throwable: Throwable? = null) =
        log(Level.WARN, channel, tag, message, detail, throwable)

    fun e(channel: Channel, tag: String, message: String, detail: String? = null, throwable: Throwable? = null) =
        log(Level.ERROR, channel, tag, message, detail, throwable)

    /**
     * Ham sistem satırı (logcat köprüsü) yazar: seviyesi metinden çıkarılamıyorsa DEBUG.
     * Aşırı akışa karşı [LogcatCapture] tarafından zaten sınırlanır.
     */
    fun rawSystem(message: String) {
        log(Level.DEBUG, Channel.SYSTEM, "logcat", message, null, null)
    }

    /** Ana giriş noktası. **Asla bloklamaz, asla istisna fırlatmaz.** */
    fun log(
        level: Level,
        channel: Channel,
        tag: String,
        message: String,
        detail: String? = null,
        throwable: Throwable? = null
    ) {
        try {
            val stamp = timeFormat.get()!!.format(Date())
            val safeTag = tag.take(40)
            val line = buildString {
                append(stamp)
                append("  ")
                append(level.letter)
                append('/')
                append(channel.key.uppercase(Locale.US).padEnd(7))
                append('[')
                append(safeTag)
                append("] ")
                append(message.take(MAX_MESSAGE_CHARS))
                if (!detail.isNullOrBlank()) {
                    append(" | ")
                    append(detail.take(1_000))
                }
                if (throwable != null) {
                    append(" || ")
                    append(stackOf(throwable).take(MAX_STACK_CHARS))
                }
            }

            pushToRing(line)
            mirrorToLogcat(level, channel, safeTag, line)

            if (!initialized.get()) return
            val accepted = try {
                executor.execute { writeLine(line) }
                true
            } catch (_: Throwable) {
                false
            }
            if (!accepted) droppedByQueue.incrementAndGet()
        } catch (_: Throwable) {
            // Günlüğün kendisi uygulamayı düşürmemeli.
        }
    }

    private fun stackOf(throwable: Throwable): String = runCatching {
        throwable.stackTraceToString()
    }.getOrElse { "${throwable.javaClass.name}: ${throwable.message}" }

    private fun pushToRing(line: String) {
        synchronized(ringLock) {
            if (ring.size >= RING_CAPACITY) ring.pollFirst()
            ring.addLast(line)
        }
    }

    /**
     * Aynı satırı `android.util.Log`'a da yazar. Etiket **sabit** ([LOGCAT_TAG]) olduğu için
     * `LogcatCapture` bu satırları atlar; çift kayıt olmaz.
     */
    private fun mirrorToLogcat(level: Level, channel: Channel, tag: String, line: String) {
        try {
            Log.println(level.toLogPriority(), LOGCAT_TAG, "${channel.key}|$tag|$line")
        } catch (_: Throwable) {
            // logcat yazılamıyorsa (bazı ROM'larda kısıtlı) sorun değil.
        }
    }

    // ─── Dosya yazımı (tek arka plan iş parçacığı) ──────────────────────────────

    private fun writeLine(line: String) {
        try {
            val now = System.currentTimeMillis()

            // Tekrar sıkıştırma: aynı satır 1,5 sn içinde tekrar gelirse yalnızca sayaç artar.
            if (line == lastLine && now - lastLineAt < 1_500L) {
                repeatCount += 1
                suppressedRepeats.incrementAndGet()
                lastLineAt = now
                return
            }
            if (repeatCount > 0) {
                flushRepeatMarker()
                repeatCount = 0
            }
            lastLine = line
            lastLineAt = now
            writeRaw(line)
        } catch (_: Throwable) {
            closeWriter()
        }
    }

    private fun flushRepeatMarker() {
        val marker = "[… aynı satır ${repeatCount + 1} kez tekrarlandı]"
        writeRaw("${timeFormat.get()!!.format(Date())}  I/APP    [tekrar] $marker")
    }

    private fun writeRaw(line: String) {
        val context = appContext ?: return
        try {
            if (writer == null || writerFile == null) {
                val target = logFile(context) ?: return
                writerFile = target
                currentSize = target.length()
                writer = BufferedWriter(FileWriter(target, true), 16 * 1024)
            }
            val w = writer ?: return
            w.write(line)
            w.newLine()
            currentSize += line.length + 1L
            writtenLines.incrementAndGet()

            // Kuyruk boşaldıysa tamponu boşalt (her satırda disk çağrısı yapmamak için).
            if (executor.queue.isEmpty()) w.flush()

            if (currentSize >= MAX_FILE_BYTES) {
                rotate(context)
            }
        } catch (_: Throwable) {
            closeWriter()
        }
    }

    private fun rotate(context: Context) {
        try {
            closeWriter()
            val file = logFile(context) ?: return
            val backup = File(file.parentFile, FILE_NAME + BACKUP_SUFFIX)
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
            currentSize = 0L
        } catch (_: Throwable) {
            // Döndürme başarısız olsa da yazma devam eder.
        }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Throwable) {
        } finally {
            writer = null
            writerFile = null
        }
    }

    /** Günlük dosyası (`files/diagnostics/kitsugi_runtime.log`). */
    fun logFile(context: Context): File? = runCatching {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        File(dir, FILE_NAME).also { if (!it.exists()) it.createNewFile() }
    }.getOrNull()

    // ─── Okuma (görüntüleyici) ─────────────────────────────────────────────────

    /** Bellekteki son [limit] satır (en yeni en sonda). Ana iş parçacığında ucuzdur. */
    fun tail(limit: Int = RING_CAPACITY): List<String> = synchronized(ringLock) {
        if (limit <= 0) emptyList() else ring.toList().takeLast(limit)
    }

    /**
     * Dosyanın son [maxBytes] baytını okur (IO iş parçacığından çağrılmalı).
     * 8 MB'lik dosyayı baştan sona okumaz; bu yüzden büyük günlükte bile hızlıdır.
     */
    fun readDiskTail(context: Context, maxBytes: Int = 512 * 1024): List<String> {
        val file = logFile(context) ?: return emptyList()
        return runCatching {
            if (!file.exists() || file.length() == 0L) return emptyList()
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val start = (length - maxBytes).coerceAtLeast(0L)
                raf.seek(start)
                val bytes = ByteArray((length - start).toInt())
                raf.readFully(bytes)
                val text = String(bytes, Charsets.UTF_8)
                // İlk satır büyük olasılıkla yarımdır: at.
                val lines = text.split('\n')
                if (start > 0L) lines.drop(1) else lines
            }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /**
     * Paylaş/kopyala için tam metin: arşiv + canlı dosya + bellekteki son satırlar.
     * [maxChars] sınırı, panoya kopyalarken `TransactionTooLargeException` oluşmasını engeller.
     */
    fun exportText(context: Context, maxChars: Int = 400_000): String = runCatching {
        val builder = StringBuilder()
        val liveFile = logFile(context)
        runCatching {
            val dir = liveFile?.parentFile ?: return@runCatching
            val backup = File(dir, FILE_NAME + BACKUP_SUFFIX)
            if (backup.exists()) builder.append(backup.readText())
        }
        runCatching {
            if (liveFile != null && liveFile.exists()) builder.append(liveFile.readText())
        }
        if (builder.isEmpty()) {
            synchronized(ringLock) { builder.append(ring.joinToString("\n")) }
        }
        val text = builder.toString()
        if (text.length <= maxChars) text
        else "… (günlüğün ilk ${text.length - maxChars} karakteri kısaltıldı)\n" + text.takeLast(maxChars)
    }.getOrElse { "" }

    /** Görüntüleyicide gösterilen durum satırı: boyut, satır, atlanan tekrar. */
    fun statsText(context: Context): String = runCatching {
        val size = (logFile(context)?.length() ?: 0L) / 1024
        "dosya: ${size} KB · yazılan: ${writtenLines.get()} · " +
            "sıkıştırılan tekrar: ${suppressedRepeats.get()} · kuyrukta atılan: ${droppedByQueue.get()}"
    }.getOrDefault("")

    // ─── Bakım ─────────────────────────────────────────────────────────────────

    /** Günlüğü temizler (bellek + dosya + yedek). Ana iş parçacığını bekletmez. */
    fun clear(context: Context) {
        try {
            synchronized(ringLock) { ring.clear() }
            lastLine = null
            lastLineAt = 0L
            repeatCount = 0
            executor.execute {
                closeWriter()
                val file = logFile(context)
                runCatching { file?.writeText("") }
                runCatching {
                    val dir = file?.parentFile
                    if (dir != null) {
                        val backup = File(dir, FILE_NAME + BACKUP_SUFFIX)
                        if (backup.exists()) backup.delete()
                    }
                }
                currentSize = 0L
                writeRaw("${timeFormat.get()!!.format(Date())}  I/SYSTEM  [günlük] === GÜNLÜK TEMİZLENDİ ===")
            }
        } catch (_: Throwable) {
        }
    }

    /** Süreç kapanırken tamponu boşaltmayı dener (kısa süre bekler, en fazla 1,5 sn). */
    fun flushBlocking(timeoutMs: Long = 1_500L) {
        runCatching {
            val future = executor.submit { closeWriter() }
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }
}
