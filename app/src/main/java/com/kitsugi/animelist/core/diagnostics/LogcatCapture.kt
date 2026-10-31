package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * **LogcatCapture** — uygulamanın kendi sistem günlüğünü akıtıp kalıcı günlüğe köprüler (v2.4.342).
 *
 * ## Neden gerekli?
 * Uygulamada yüzlerce yerde `android.util.Log.w/e(...)` çağrısı var (kütüphaneler, eklenti
 * çalışma zamanı, oynatıcı, ağ katmanı). Bunların **hepsini** tek tek `KitsugiLog`'a
 * çevirmek gerçekçi değil; ama kullanıcının "o anda arka planda ne oldu" sorusunun cevabı
 * tam olarak bu satırlarda. Bu sınıf, **yalnızca kendi sürecimizin** sistem günlüğünü
 * canlı okur ve kalıcı dosyaya yazar; böylece logcat halka tamponu dolup eski satırları
 * sildiğinde bile kanıt elimizde kalır.
 *
 * ## Çökme/donma yapmama kuralları
 *  - Ayrı **daemon** iş parçacığı, `MIN_PRIORITY`. Başlatılamazsa bir kez not düşer ve susar.
 *  - Saniyede en fazla [MAX_LINES_PER_WINDOW] satır aktarılır ([WINDOW_MS] penceresinde);
 *    fazlası sayılır, günlük boğulmaz.
 *  - Kendi yazdığımız satırlar ([KitsugiLog.LOGCAT_TAG]) **atlanır** → çift kayıt olmaz.
 *  - Süreç biterse akış da ölür; `waitFor` ile hiç beklenmez (ana iş parçacığı ilgilenmez).
 */
object LogcatCapture {

    private const val TAG = "logcat"
    private const val MAX_LINES_PER_WINDOW = 300
    private const val WINDOW_MS = 10_000L
    private const val MAX_LINE_CHARS = 2_000

    private val started = AtomicBoolean(false)
    private val dropped = AtomicLong(0L)

    /** Uygulama açılışında bir kez çağrılır. Başarısızlık uygulamayı etkilemez. */
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            Thread({ stream() }, "KitsugiLogcatCapture").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
            }
        }
    }

    /** Penceresi dolduğu için aktarılamayan sistem satırı sayısı. */
    fun droppedLines(): Long = dropped.get()

    private fun stream() {
        var process: java.lang.Process? = null
        try {
            process = runCatching {
                Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "--pid=${Process.myPid()}")
                )
            }.getOrNull()

            if (process == null) {
                KitsugiLog.i(
                    KitsugiLog.Channel.SYSTEM,
                    TAG,
                    "Sistem günlüğü akışı başlatılamadı",
                    "bu cihazda 'logcat' çağrılamıyor; yalnızca uygulama içi kayıtlar tutulacak"
                )
                return
            }

            KitsugiLog.i(KitsugiLog.Channel.SYSTEM, TAG, "Sistem günlüğü akışı başladı")

            var windowStart = System.currentTimeMillis()
            var windowCount = 0
            val proc = process

            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    // Kendi yazdığımız satırlar zaten dosyada; yeniden yazma.
                    if (line.contains(" ${KitsugiLog.LOGCAT_TAG}")) continue
                    if (line.contains("KitsugiLogcatCapture")) continue

                    val now = System.currentTimeMillis()
                    if (now - windowStart > WINDOW_MS) {
                        windowStart = now
                        windowCount = 0
                    }
                    if (windowCount >= MAX_LINES_PER_WINDOW) {
                        dropped.incrementAndGet()
                        continue
                    }
                    windowCount += 1
                    KitsugiLog.rawSystem(line.take(MAX_LINE_CHARS))
                }
            }
            KitsugiLog.i(KitsugiLog.Channel.SYSTEM, TAG, "Sistem günlüğü akışı kapandı")
        } catch (_: Throwable) {
            runCatching {
                KitsugiLog.w(
                    KitsugiLog.Channel.SYSTEM,
                    TAG,
                    "Sistem günlüğü akışı kesildi",
                    "aktarılamayan satır: ${dropped.get()}"
                )
            }
        } finally {
            runCatching { process?.destroy() }
        }
    }
}
