package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KitsugiCrashHandler — uygulamanın TEK çökme giriş noktası.
 *
 * Tasarım kuralları:
 *  1. **Hiçbir şeyi yutmaz, ama önce kanıtı yazar.**
 *  2. **Throwable yakalar** (Exception değil — VerifyError/LinkageError/OutOfMemoryError/StackOverflowError dahil).
 *  3. **Tek uçuş (single-flight) koruması:** Aynı süreçte birden fazla arka plan iş parçacığı aynı anda
 *     çöktüğünde (ör. 10 coroutine worker'ın aynı anda patlaması), yalnızca İLK çökme (veya daha sonra
 *     ana UI thread'i de çökerse bir kez de ön-plan yükseltmesi) rapor/logcat/MediaStore/Activity başlatma
 *     işlemlerini yürütür; diğer iş parçacıkları dosyaları birbirinin üzerine yazıp bozamaz.
 *  4. **Ana iş parçacığını UYUTMAZ.**
 */
object KitsugiCrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "KitsugiCrash"

    @Volatile private var appContext: Context? = null
    @Volatile private var installed = false

    private val primaryCrashHandled = AtomicBoolean(false)
    private val foregroundCrashRecorded = AtomicBoolean(false)
    private val reportLock = Any()

    // ── Kurulum ────────────────────────────────────────────────────────────────

    /** Application.attachBaseContext() içinden çağrılır: süreçteki en erken güvenli nokta. */
    fun install(context: Context) {
        val ctx: Context = context.applicationContext ?: context
        appContext = ctx
        if (!installed) {
            installed = true
            try {
                Thread.setDefaultUncaughtExceptionHandler(this)
                Log.i(TAG, "UncaughtExceptionHandler kuruldu.")
            } catch (t: Throwable) {
                Log.w(TAG, "Handler kurulamadı: ${t.message}")
            }
        }
        try {
            NativeCrashBridge.install(ctx)
        } catch (_: Throwable) {}
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        handle(thread, throwable)
    }

    // ── Ana yol ───────────────────────────────────────────────────────────────

    fun handle(thread: Thread, throwable: Throwable) {
        val ctx = appContext
        val isForegroundThread = thread.name == "main" || thread.name.startsWith("main")

        // 1) Coroutine iptalleri normal akıştır.
        if (isCancellation(throwable)) {
            Log.d(TAG, "Coroutine iptali normal karşılandı: ${throwable.message}")
            return
        }

        // 2) Arka plan thread'lerindeki ağ/pencere hataları kullanıcıyı etkilemez.
        if (!isForegroundThread && isNetworkOrWindow(throwable)) {
            Log.w(TAG, "Arka plan ağ/pencere hatası bastırıldı (${thread.name}): " +
                    "${throwable.javaClass.simpleName}: ${throwable.message}")
            return
        }

        Log.e(TAG, "FATAL ÇÖKME (${thread.name}, anaThread=$isForegroundThread): " +
                "${throwable.javaClass.simpleName}: ${throwable.message}", throwable)

        // v2.4.342: çökme, kalıcı uygulama günlüğüne de yazılır. Süreç birazdan öleceği için
        // tampon kısa süreyle (en fazla 2 sn) boşaltılır; bu, kullanıcının günlükte
        // "neden çöktü" sorusunun cevabını bulması için kritik.
        runCatching {
            KitsugiLog.log(
                level = KitsugiLog.Level.FATAL,
                channel = KitsugiLog.Channel.CRASH,
                tag = thread.name.take(24),
                message = "ÇÖKME: ${throwable.javaClass.simpleName}: ${throwable.message}",
                detail = if (isForegroundThread) "ön plan (ana iş parçacığı)" else "arka plan",
                throwable = throwable
            )
            KitsugiLog.flushBlocking(2_000L)
        }

        if (ctx == null) {
            if (primaryCrashHandled.compareAndSet(false, true)) {
                killSelf(delayMs = 200)
            }
            return
        }

        val isFirstCrashInProcess = primaryCrashHandled.compareAndSet(false, true)
        val shouldRecordForegroundUpgrade = !isFirstCrashInProcess &&
            isForegroundThread &&
            foregroundCrashRecorded.compareAndSet(false, true)

        if (isForegroundThread) {
            foregroundCrashRecorded.set(true)
        }

        // Aynı anda çöken ikincil arka plan worker thread'leri: rapor/logcat dosyalarını ezmesin,
        // MediaStore Downloads'a kopya dosya yağdırmasın ve ikinci kez Activity başlatmasın.
        if (!isFirstCrashInProcess && !shouldRecordForegroundUpgrade) {
            return
        }

        val report: String = synchronized(reportLock) {
            // ── 2a) EN ÖNCE SİNYALİ VER: bayrak dosyası + prefs
            try {
                KitsugiCrashLogger.markCrashPending(ctx, thread, throwable)
            } catch (_: Throwable) {}

            // ── 2b) Eylem izini diske yaz
            try { KitsugiSessionSupervisor.flushNow() } catch (_: Throwable) {}

            // ── 3) Raporu senkron yaz
            val builtReport = try {
                KitsugiCrashLogger.writeCrashReport(
                    context = ctx,
                    thread = thread,
                    throwable = throwable,
                    isForeground = isForegroundThread
                )
            } catch (t: Throwable) {
                buildString {
                    appendLine("=== KİTSUGİ ÇÖKME (basit rapor) ===")
                    appendLine("Thread : ${thread.name} (anaThread=$isForegroundThread)")
                    appendLine("Hata   : ${throwable.javaClass.name}: ${throwable.message}")
                    appendLine("Rapor  : ayrıntılı rapor üretilemedi (${t.javaClass.simpleName}: ${t.message})")
                    appendLine(android.util.Log.getStackTraceString(throwable))
                }
            }

            // ── 4) Logcat anlık görüntüsü (yalnızca ilk seferde, yeterli bekleme penceresiyle)
            if (isFirstCrashInProcess) {
                try { KitsugiCrashLogger.captureLogcatSync(ctx, builtReport) } catch (_: Throwable) {}
            }
            builtReport
        }

        if (!isFirstCrashInProcess) {
            return
        }

        // ── 5) Çökme ekranını BAŞKA bir süreçte ve BAŞKA bir görevde aç
        val started = try {
            startCrashUi(ctx, report)
        } catch (t: Throwable) {
            Log.e(TAG, "Çökme ekranı başlatılamadı: ${t.message}")
            false
        }

        // ── 6) Bekçi: bu süreç ölü bir ana thread ile asılı kalmasın.
        killSelf(delayMs = if (started) 4_000L else 600L)
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private fun startCrashUi(context: Context, report: String): Boolean {
        val intent = Intent(context, com.kitsugi.animelist.ui.screens.crash.KitsugiCrashActivity::class.java).apply {
            putExtra("has_crash", true)
            putExtra("crash_summary", report.take(64_000))
            putExtra("crashed_pid", Process.myPid())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun killSelf(delayMs: Long) {
        Thread({
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {}
            try {
                Process.killProcess(Process.myPid())
            } catch (_: Throwable) {}
            try {
                System.exit(10)
            } catch (_: Throwable) {}
        }, "KitsugiCrashWatchdog").apply {
            isDaemon = false
            start()
        }
    }

    private fun isCancellation(throwable: Throwable): Boolean {
        var cur: Throwable? = throwable
        var depth = 0
        while (cur != null && depth < 12) {
            if (cur is kotlinx.coroutines.CancellationException) return true
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun isNetworkOrWindow(throwable: Throwable): Boolean {
        var cur: Throwable? = throwable
        var depth = 0
        while (cur != null && depth < 12) {
            when (cur) {
                is java.io.IOException,
                is java.net.SocketException,
                is java.net.UnknownHostException,
                is javax.net.ssl.SSLException,
                is java.net.ProtocolException,
                is android.view.WindowManager.BadTokenException -> return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }
}
