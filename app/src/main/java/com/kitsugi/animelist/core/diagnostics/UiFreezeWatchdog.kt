package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * **UiFreezeWatchdog** — "uygulama dondu" anlarını kanıtla birlikte günlüğe yazar (v2.4.342).
 *
 * ## Neden gerekli?
 * Kullanıcı geri bildirimi: *"çökme, donma olursa da günlükte görünsün"*. Bir donma
 * (ANR) çoğu zaman **çökme raporu üretmez**: süreç öldürülürse geriye yalnızca
 * `KitsugiSessionSupervisor`'ın "temiz kapanmadı" notu kalır, ama **hangi kodun** ana iş
 * parçacığını kilitlediği kaybolur. Bu bekçi, ana iş parçacığını saniyede bir yoklar;
 * yanıt gecikirse o anki **yığın izini** (stack trace) günlüğe yazar.
 *
 * ## "Donma yapmadan" kuralları
 *  - Ana iş parçacığına saniyede **tek** boş `post` bırakır (ölçülemeyecek kadar ucuz).
 *  - Kendi iş parçacığı `MIN_PRIORITY` daemon'dır; uygulama kapanınca ölür.
 *  - Günlüğe yazma [KitsugiLog] üzerinden, yani zaten bloklamayan yoldan geçer.
 *  - Aynı donma için en fazla **5 saniyede bir** kayıt üretir (günlük şişmez).
 */
object UiFreezeWatchdog {

    private const val TAG = "freeze"
    private const val TICK_MS = 1_000L
    private const val WARN_MS = 2_500L
    private const val SEVERE_MS = 8_000L
    private const val MIN_REPORT_INTERVAL_MS = 5_000L
    private const val MAX_FRAMES = 14

    private val started = AtomicBoolean(false)
    private val longestFreezeMs = AtomicLong(0L)
    private val freezeCount = AtomicLong(0L)

    /** Uygulama açılışında bir kez çağrılır. Asla istisna fırlatmaz. */
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            val thread = Thread({ loop() }, "KitsugiFreezeWatchdog").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
            thread.start()
            KitsugiLog.i(
                KitsugiLog.Channel.SYSTEM,
                TAG,
                "Donma bekçisi başladı",
                "eşik: ${WARN_MS} ms"
            )
        }
    }

    /** Şu ana kadar ölçülen en uzun ana iş parçacığı gecikmesi (ms). */
    fun longestFreezeMs(): Long = longestFreezeMs.get()

    /** Kaç kez donma kaydı üretildi. */
    fun freezeCount(): Long = freezeCount.get()

    private fun loop() {
        val main = Looper.getMainLooper()
        val handler = Handler(main)
        var lastReportAt = 0L

        while (true) {
            try {
                val postedAt = SystemClock.uptimeMillis()
                val latch = CountDownLatch(1)
                val accepted = handler.post { latch.countDown() }
                if (!accepted) {
                    // Ana Looper kapanıyorsa (süreç bitiyor) bekçi de dursun.
                    return
                }
                latch.await(WARN_MS + 1_000L, TimeUnit.MILLISECONDS)
                val latency = SystemClock.uptimeMillis() - postedAt

                if (latency > longestFreezeMs.get()) longestFreezeMs.set(latency)

                val now = SystemClock.elapsedRealtime()
                if (latency >= WARN_MS && now - lastReportAt >= MIN_REPORT_INTERVAL_MS) {
                    lastReportAt = now
                    freezeCount.incrementAndGet()
                    report(latency, severe = latency >= SEVERE_MS, mainThread = main.thread)
                }

                // Bir sonraki yoklamaya kadar bekle (gecikme zaten ölçüldü).
                Thread.sleep(TICK_MS)
            } catch (_: InterruptedException) {
                return
            } catch (_: Throwable) {
                // Bekçi kendi hatasıyla uygulamayı düşürmemeli; kısa bir mola verip devam et.
                runCatching { Thread.sleep(TICK_MS) }
            }
        }
    }

    private fun report(latencyMs: Long, severe: Boolean, mainThread: Thread) {
        val frames = runCatching {
            mainThread.stackTrace
                .take(MAX_FRAMES)
                .joinToString("\n") { "  at $it" }
        }.getOrDefault("")

        val level = if (severe) KitsugiLog.Level.ERROR else KitsugiLog.Level.WARN
        KitsugiLog.log(
            level = level,
            channel = KitsugiLog.Channel.FREEZE,
            tag = TAG,
            message = if (severe) {
                "Uygulama ${latencyMs} ms boyunca DONDU (ciddi)"
            } else {
                "Ana iş parçacığı ${latencyMs} ms yanıt vermedi (kısa donma)"
            },
            detail = buildString {
                append("son ekran: ").append(runCatching { KitsugiSessionSupervisor.lastKnownScreen() }.getOrDefault("?"))
                append(" | ana iş parçacığı yığını:")
                if (frames.isNotBlank()) append('\n').append(frames)
            }
        )
    }
}
