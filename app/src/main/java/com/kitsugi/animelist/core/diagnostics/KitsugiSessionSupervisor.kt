package com.kitsugi.animelist.core.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.annotation.RequiresApi
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * KitsugiSessionSupervisor — "sessiz ölüm" dedektörü.
 *
 * NEDEN GEREKLİ?
 * ─────────────
 * Java `Thread.setDefaultUncaughtExceptionHandler` yalnızca **yönetilen (managed) Java/Kotlin
 * istisnalarında** çalışır. Uygulama aşağıdaki durumlarda bu handler'a HİÇ uğramadan aniden
 * yok olur ve kullanıcı hiçbir çökme raporu göremez:
 *
 *   1. Native çökme      → SIGSEGV / SIGABRT / SIGBUS (libmpv, ffmpeg, MediaCodec, GPU/Skia,
 *                           libdovi JNI, TorrServer vb.)
 *   2. Sistem/IME kill   → LMKD (low memory killer) SIGKILL, "am_kill", "Killing ... (adj)"
 *   3. ANR + kill        → Ana iş parçacığı kilitlenir, sistem süreci zorla öldürür.
 *   4. Zorla durdurma    → "Force stop", "swipe from recents" sonrası temizlenmeyen durum.
 *
 * Bu sınıf her oturum için diske küçük bir "oturum durumu" dosyası yazar ve uygulama her
 * açılışta önceki oturumun temiz kapanıp kapanmadığını kontrol eder. Temiz kapanmadıysa:
 *
 *   • logcat geçmişinden ölüm sebebini kazar (Fatal signal / ANR / lmkd / am_kill ...)
 *   • `unclean_exit.txt` raporunu üretir
 *   • çökme bayrağını kaldırır → uygulama açıldığında çökme ekranı/uyarı penceresi çıkar
 *
 * Ayrıca "breadcrumb" (ekmek kırıntısı) izi tutar: kullanıcı hangi ekrandaydı, hangi
 * işlemi yapıyordu (galeri açtı, resim indirmeye bastı, detay sayfasına girdi...).
 * Böylece çökme raporu tek başına "nerede öldü" sorusunu da cevaplar.
 */
object KitsugiSessionSupervisor {

    private const val TAG = "KitsugiSession"

    private const val SESSION_FILE      = "session_state.txt"
    private const val UNCLEAN_REPORT    = "unclean_exit.txt"
    private const val BREADCRUMB_FILE   = "breadcrumbs.txt"
    private const val POSTMORTEM_LOGCAT = "post_mortem_logcat.txt"

    private const val HEARTBEAT_MS        = 5_000L
    private const val FLUSH_EVERY_N_TICKS = 3          // ~15 sn'de bir diske yaz
    private const val MAX_BREADCRUMBS     = 40
    private const val ACTION_DEDUPE_WINDOW_MS = 1_500L
    /** Kırıntı dosyası en fazla bu aralıkla yazılır (donma önleme, v2.4.342). */
    private const val BREADCRUMB_WRITE_INTERVAL_MS = 1_500L
    private const val MAX_LOGCAT_LINES    = 9_000
    private const val LOGCAT_RELEVANT_LIMIT = 220
    private const val ANR_TRACE_LINES = 2_000

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "KitsugiSessionIO").apply { isDaemon = true }
    }

    @Volatile private var appContext: Context? = null
    /** Yalnızca ANA süreçte true olur — :crash gibi yardımcı süreçler oturum dosyasına yazmaz. */
    @Volatile private var trackingEnabled = false
    @Volatile private var active = false
    @Volatile private var foreground = true
    @Volatile private var lastScreen: String = "baslangic"
    @Volatile private var lastAliveMs: Long = 0L
    @Volatile private var tick = 0
    @Volatile private var flushedOnce = false

    private val breadcrumbs = ArrayDeque<String>()
    private val breadcrumbLock = Any()
    /** Son eklenen eylem — birebir tekrarları ayıklamak için. */
    @Volatile private var lastActionRaw: String? = null
    @Volatile private var lastActionAt: Long = 0L
    /** Son kırıntı dosyası yazımı (ms) — yazma sıklığını sınırlar (v2.4.342). */
    @Volatile private var lastBreadcrumbWriteAt: Long = 0L

    // SimpleDateFormat thread-safe DEĞİLDİR; bu sınıf ana thread + IO thread + çökme
    // handler'ından birlikte çağrılır → her kullanım kilit altında yapılır.
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateFmtLock = Any()

    private fun nowText(): String = synchronized(dateFmtLock) { dateFmt.format(Date()) }

    private val uncleanExitDetected = AtomicBoolean(false)

    // ─────────────────────────────────────────────────────────────────────────────
    // Kurulum — Application.attachBaseContext içinden çağrılır (en erken nokta)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Uygulamanın gerçek (ana) sürecinde miyiz? `:crash` gibi yardımcı süreçlerde hayır. */
    fun isMainProcess(context: Context): Boolean {
        return try {
            val pkg = context.packageName
            val proc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                @Suppress("DEPRECATION")
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val pid = Process.myPid()
                am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            } ?: pkg
            proc == pkg
        } catch (_: Throwable) {
            true
        }
    }

    fun install(context: Context) {
        if (appContext != null) return
        // attachBaseContext() sırasında applicationContext null olabilir → geçilen context'i kullan.
        val ctx: Context = context.applicationContext ?: context
        appContext = ctx

        // Yardımcı süreçler (:crash) ana oturum dosyasına dokunmasın.
        if (!isMainProcess(ctx)) {
            Log.d(TAG, "Yardımcı süreçteyiz, oturum takibi devre dışı.")
            return
        }

        trackingEnabled = true

        try {
            inspectPreviousSession(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "Önceki oturum incelenemedi: ${t.javaClass.simpleName}: ${t.message}")
        }

        startNewSession(ctx)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Yeni oturum + kalp atışı (heartbeat)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun startNewSession(ctx: Context) {
        active = true
        foreground = true
        lastAliveMs = System.currentTimeMillis()
        tick = 0

        ioExecutor.execute {
            try {
                File(ctx.filesDir, BREADCRUMB_FILE).delete()
                commitSessionFile(ctx, clean = false)
            } catch (_: Throwable) {}
        }

        val handler = Handler(Looper.getMainLooper())
        val beat = object : Runnable {
            override fun run() {
                if (!active) return
                lastAliveMs = System.currentTimeMillis()
                tick++
                if (!flushedOnce || tick % FLUSH_EVERY_N_TICKS == 0) {
                    flushedOnce = true
                    val fg = foreground
                    val screen = lastScreen
                    ioExecutor.execute {
                        try { commitSessionFile(ctx, clean = false, fg = fg, screen = screen) } catch (_: Throwable) {}
                    }
                }
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
        handler.postDelayed(beat, HEARTBEAT_MS)
    }

    /**
     * Ana iş parçacığının canlı olduğunu kanıtlayan kalp atışı bilgisi.
     * (UI donduysa rapor "son canlı zaman" ile bunu gösterir.)
     */
    fun noteForeground(isForeground: Boolean) {
        foreground = isForeground
    }

    /** Ekran/rota değişimlerini izler — çökme raporunda "son ekran" olarak görünür. */
    fun noteScreen(screen: String) {
        if (!active) return
        lastScreen = screen
        noteAction("ekran → $screen")
        // v2.4.342: ekran geçişleri kalıcı günlüğe de yazılır. Donma/çökme anında
        // "hangi ekrandaydı" sorusunun cevabı böylece günlükte durur.
        runCatching {
            KitsugiLog.d(KitsugiLog.Channel.USER, "ekran", screen)
        }
    }

    /** Kullanıcı eylemlerini (galeri aç, resim indir, oynat...) halka tampona ekler. */
    fun noteAction(action: String) {
        if (!active) return
        val nowMs = System.currentTimeMillis()
        if (action == lastActionRaw && nowMs - lastActionAt < ACTION_DEDUPE_WINDOW_MS) return
        lastActionRaw = action
        lastActionAt = nowMs
        val line = "${nowText()}  $action"
        synchronized(breadcrumbLock) {
            breadcrumbs.addLast(line)
            while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        }
        val copy = synchronized(breadcrumbLock) { breadcrumbs.toList() }
        // v2.4.342 — DONMA KORUMASI: eskiden **her** kullanıcı eyleminde kırıntı dosyası
        // baştan yazılıyordu. Hızlı dokunuşlarda bu, tek iş parçacıklı kuyruğu doldurup
        // gereksiz disk yükü üretiyordu. Artık en fazla 1,5 sn'de bir yazılır; son hâl
        // yine kaybolmaz (çökme anında [flushNow] zorla yazar).
        val now = System.currentTimeMillis()
        if (now - lastBreadcrumbWriteAt < BREADCRUMB_WRITE_INTERVAL_MS) return
        lastBreadcrumbWriteAt = now
        ioExecutor.execute {
            try {
                appContext?.let { ctx ->
                    File(ctx.filesDir, BREADCRUMB_FILE).writeText(copy.joinToString("\n"))
                }
            } catch (_: Throwable) {}
        }
    }

    /** Çökme anında kırıntıları hemen diske yazar (rapora eklenecek). */
    fun flushNow() {
        if (!trackingEnabled) return
        val ctx = appContext ?: return
        val copy = synchronized(breadcrumbLock) { breadcrumbs.toList() }
        try {
            File(ctx.filesDir, BREADCRUMB_FILE).writeText(copy.joinToString("\n"))
        } catch (_: Throwable) {}
    }

    /** Rapor üretimi için son kırıntı metni. */
    fun breadcrumbText(context: Context): String {
        return try {
            val f = File(context.filesDir, BREADCRUMB_FILE)
            if (f.exists() && f.length() > 0) f.readText() else KitsugiStrings.get(R.string.diag_session_breadcrumb_empty)
        } catch (_: Throwable) {
            "(eylem izi okunamadı)"
        }
    }

    fun lastKnownScreen(): String = lastScreen

    /** Uygulama kullanıcı tarafından düzgün kapatıldıysa işaretlenir. */
    fun markCleanExit() {
        if (!trackingEnabled) return
        val ctx = appContext ?: return
        active = false
        try {
            commitSessionFile(ctx, clean = true)
            File(ctx.filesDir, BREADCRUMB_FILE).delete()
        } catch (_: Throwable) {}
    }

    fun wasUncleanExitDetected(): Boolean = uncleanExitDetected.get()

    fun hasUncleanExitReport(context: Context): Boolean {
        return try {
            File(context.filesDir, UNCLEAN_REPORT).let { it.exists() && it.length() > 32 }
        } catch (_: Throwable) {
            false
        }
    }

    fun uncleanExitReportFile(context: Context): File = File(context.filesDir, UNCLEAN_REPORT)

    fun readUncleanExitReport(context: Context): String {
        return try {
            val f = uncleanExitReportFile(context)
            if (f.exists()) f.readText() else KitsugiStrings.get(R.string.diag_session_unclean_record_none)
        } catch (_: Throwable) {
            "Temiz olmayan kapanma kaydı okunamadı."
        }
    }

    fun clearUncleanExitReport(context: Context) {
        try { uncleanExitReportFile(context).delete() } catch (_: Throwable) {}
        uncleanExitDetected.set(false)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Oturum dosyası
    // ─────────────────────────────────────────────────────────────────────────────

    private fun sessionFile(ctx: Context) = File(ctx.filesDir, SESSION_FILE)

    private fun commitSessionFile(
        ctx: Context,
        clean: Boolean,
        fg: Boolean = foreground,
        screen: String = lastScreen
    ) {
        val text = buildString {
            appendLine("pid=${Process.myPid()}")
            appendLine("started=${KitsugiCrashLogger.KitsugiApplication_LaunchTime ?: System.currentTimeMillis()}")
            appendLine("last_alive=${System.currentTimeMillis()}")
            appendLine("heartbeat_seen=$lastAliveMs")
            appendLine("foreground=${if (fg) 1 else 0}")
            appendLine("screen=$screen")
            appendLine("clean=${if (clean) 1 else 0}")
            appendLine("build=${com.kitsugi.animelist.BuildConfig.VERSION_NAME}")
        }
        try {
            sessionFile(ctx).writeText(text)
        } catch (_: Throwable) {}
    }

    private fun readKeyValues(file: File): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            file.readLines().forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        } catch (_: Throwable) {}
        return map
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Önceki oturumun ölüm sebebini araştır
    // ─────────────────────────────────────────────────────────────────────────────

    private fun inspectPreviousSession(ctx: Context) {
        val f = sessionFile(ctx)
        if (!f.exists()) return

        val info = readKeyValues(f)
        try { f.delete() } catch (_: Throwable) {}

        val prevPid     = info["pid"]?.toIntOrNull() ?: -1
        val clean       = info["clean"] == "1"
        val wasForeground = info["foreground"] == "1"
        val started     = info["started"]?.toLongOrNull() ?: 0L
        val lastAlive   = info["last_alive"]?.toLongOrNull() ?: started
        val screen      = info["screen"] ?: "bilinmiyor"
        val build       = info["build"] ?: "?"

        if (clean || prevPid <= 0 || prevPid == Process.myPid()) return

        val currentBuild = com.kitsugi.animelist.BuildConfig.VERSION_NAME
        // Ucuz yanlış-alarm kontrolleri (yalnızca prefs + PackageManager):
        //  • önceki süreç çökme yakalayıcısı tarafından zaten raporlandıysa yineleme yapılmaz;
        //  • uygulama güncellendiyse eski süreci paket yükleyici kapatmıştır (çökme değil).
        val crashAlreadyRecorded = KitsugiCrashLogger.wasCrashRecordedForPid(ctx, prevPid, started)
        val appUpdated = PreviousExitClassifier.isAppUpdate(
            previousBuild = build,
            currentBuild = currentBuild,
            packageLastUpdateMs = packageLastUpdateTime(ctx),
            previousLastAliveMs = lastAlive
        )

        Log.w(
            TAG,
            "Temiz olmayan kapanma tespit edildi (önceki pid=$prevPid, ekran=$screen, sürüm=$build → " +
                "$currentBuild, kayıtlı çökme=$crashAlreadyRecorded, güncelleme=$appUpdated)"
        )

        // v2.4.342: "sessiz ölüm" (native çökme, LMKD, ANR sonrası kill, zorla durdurma)
        // kalıcı günlüğe de yazılır. Kullanıcı günlükte tam olarak hangi ekranda, hangi
        // sürümde ve ne kadar süre sonra öldüğünü görebilir.
        runCatching {
            KitsugiLog.w(
                KitsugiLog.Channel.SYSTEM,
                "oturum",
                "Önceki oturum temiz kapanmadı (pid=$prevPid)",
                "ekran=$screen | ön planda=$wasForeground | sürüm=$build | " +
                    "son canlılık: ${if (lastAlive > 0) Date(lastAlive) else "?"}"
            )
        }

        // ── ÖN PLANDa öldüyse kullanıcıya HEMEN haber ver (bu kısım çok ucuz: sadece prefs) ──
        // Uygulama açılışını BLOKLAMAMAK için logcat kazısı + sistem çıkış kaydı + rapor üretimi
        // arka planda yapılır. Sistem kaydı sonradan "zararsız" derse (kullanıcı kapattı vb.)
        // bu erken bayrak arka planda geri alınır.
        val earlyFlagSet = wasForeground && !crashAlreadyRecorded && !appUpdated
        if (earlyFlagSet) {
            uncleanExitDetected.set(true)
            setUnreadFlag(ctx, "Önceki oturum beklenmedik şekilde sonlandı (sebep analiz ediliyor…)")
        }

        ioExecutor.execute {
            buildAndStoreUncleanReport(
                ctx, prevPid, started, lastAlive, wasForeground, screen, build,
                currentBuild, crashAlreadyRecorded, appUpdated, earlyFlagSet
            )
        }
    }

    private fun packageLastUpdateTime(ctx: Context): Long = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
    } catch (_: Throwable) {
        0L
    }

    /** Erken kaldırılan "beklenmedik kapanma" bayrağını geri alır (sistem kaydı zararsız dediğinde). */
    private fun clearEarlyUnreadFlag(ctx: Context) {
        uncleanExitDetected.set(false)
        try {
            ctx.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", false)
                .putBoolean("has_unclean_exit", false)
                .commit()
        } catch (_: Throwable) {}
    }

    private fun setUnreadFlag(ctx: Context, headline: String) {
        try {
            ctx.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", true)
                .putBoolean("has_unclean_exit", true)
                .putLong("last_crash_time", System.currentTimeMillis())
                .putString("last_crash_title", headline.take(200))
                .commit()
        } catch (_: Throwable) {}
    }

    private fun buildAndStoreUncleanReport(
        ctx: Context,
        prevPid: Int,
        started: Long,
        lastAlive: Long,
        wasForeground: Boolean,
        screen: String,
        build: String,
        currentBuild: String,
        crashAlreadyRecorded: Boolean,
        appUpdated: Boolean,
        earlyFlagSet: Boolean
    ) {
        // Android 11+: sistemin kesin çıkış kaydı (logcat kazısı sistem günlüklerini göremez;
        // eski raporlardaki "SEBEP BULUNAMADI" satırlarının nedeni buydu).
        val exitRecord = queryExitRecord(ctx, prevPid, started)
        val verdict = PreviousExitClassifier.classify(
            heartbeatForeground = wasForeground,
            crashAlreadyRecorded = crashAlreadyRecorded,
            appUpdated = appUpdated,
            exitReason = exitRecord?.reason,
            exitDescription = exitRecord?.description,
            exitImportance = exitRecord?.importance
        )
        if (verdict.kind == PreviousExitClassifier.Kind.RECORDED_CRASH ||
            verdict.kind == PreviousExitClassifier.Kind.APP_UPDATE
        ) {
            // Tam (yinelenen) rapor yerine geçmişe kısa bir iz düşülür; uyarı penceresi açılmaz.
            val tag = if (verdict.kind == PreviousExitClassifier.Kind.APP_UPDATE) "UPDATE EXIT" else "RECORDED CRASH EXIT"
            try {
                File(ctx.filesDir, "crash_history.txt").appendText(
                    "\n[$tag] ${nowText()}\n" +
                        "▶ ÖNCEKİ SÜREÇ  : pid=$prevPid (sürüm=$build → şimdi $currentBuild)\n" +
                        "▶ SINIFLANDIRMA : ${verdict.headline}\n" +
                        "▶ SİSTEM KAYDI  : ${exitRecord?.summary() ?: "yok"}\n"
                )
            } catch (_: Throwable) {}
            if (earlyFlagSet) clearEarlyUnreadFlag(ctx)
            return
        }

        val postMortem = try {
            scrapePostMortemLogcat(ctx, prevPid, started, lastAlive)
        } catch (_: Throwable) {
            PostMortem("SEBEP BULUNAMADI (logcat okunamadı)", "", false)
        }

        // Yalnızca ÖNCEKİ SÜRECE (prevPid) veya onun oturum aralığına ait taze native çökme izini al.
        // Önceki sürümlerdeki O_APPEND kaynaklı bayat native_crash.txt dosyaları bu kontrol sonunda
        // temizlenir; böylece günler önceki SIGSEGV izi yeni raporlara asla yapışmaz.
        val matchedNativeTrace = try {
            NativeCrashBridge.readRecentNativeCrash(
                context = ctx,
                maxAgeMs = NativeCrashBridge.UNCLEAN_TRACE_MAX_AGE_MS,
                expectedPid = prevPid,
                sessionStartMs = started,
                sessionEndMs = lastAlive
            )
        } catch (_: Throwable) {
            null
        }
        try {
            val nf = NativeCrashBridge.nativeCrashFile(ctx)
            if (nf.exists()) {
                val meta = NativeCrashBridge.parseLatestNativeCrash(nf.readText())
                if (meta?.pid != Process.myPid()) {
                    NativeCrashBridge.clearNativeCrash(ctx)
                }
            }
        } catch (_: Throwable) {}

        val report = buildString {
            appendLine("╔══════════════════════════════════════════════════════════════╗")
            appendLine("║   KİTSUGİ — TEMİZ OLMAYAN KAPANMA (SESSİZ ÇÖKME) RAPORU")
            appendLine("╚══════════════════════════════════════════════════════════════╝")
            appendLine()
            appendLine(KitsugiStrings.get(R.string.diag_session_detected_at, nowText()))
            appendLine("▶ ÖNCEKİ SÜREÇ  : pid=$prevPid (sürüm=$build)")
            appendLine("▶ OTURUM BAŞI   : ${if (started > 0) synchronized(dateFmtLock) { dateFmt.format(Date(started)) } else "?"}")
            appendLine("▶ SON CANLI ANI : ${if (lastAlive > 0) synchronized(dateFmtLock) { dateFmt.format(Date(lastAlive)) } else "?"}")
            if (started > 0 && lastAlive > 0) {
                appendLine("▶ YAŞAM SÜRESİ  : ${(lastAlive - started) / 1000} sn")
            }
            appendLine("▶ ARKA PLAN MI? : ${if (verdict.foreground) "HAYIR (uygulama ÖN PLANDAYDI)" else "EVET (arka planda sistem tarafından kapatıldı olabilir)"}")
            val crumbScreen = breadcrumbLastScreen(ctx)
            appendLine(
                "▶ SON EKRAN     : ${crumbScreen ?: screen}" +
                    (if (crumbScreen != null && crumbScreen != screen) "   (oturum anlık görüntüsü: $screen)" else "")
            )
            appendLine()
            appendLine("▶ SİSTEM ÇIKIŞ KAYDI (ApplicationExitInfo)")
            appendLine("  ${exitRecord?.summary() ?: "kayıt yok (Android 11 öncesi ya da sistem kaydı bulunamadı)"}")
            appendLine("  SINIFLANDIRMA : ${verdict.headline}")
            appendLine()
            appendLine("▶ ÖLÜM SEBEBİ (logcat analizi)")
            appendLine("  ${postMortem.headline}")
            appendLine()
            appendLine("▶ UYGULAMA EYLEM İZİ (breadcrumbs — sondan geriye)")
            appendLine(KitsugiSessionSupervisor.breadcrumbText(ctx).lines().asReversed().take(30).joinToString("\n"))
            appendLine()
            if (postMortem.lines.isNotBlank()) {
                appendLine("▶ LOGCAT KANIT SATIRLARI")
                appendLine(postMortem.lines)
                appendLine()
            }
            if (!matchedNativeTrace.isNullOrBlank()) {
                appendLine("▶ NATIVE ÇÖKME İZİ (pid=$prevPid)")
                appendLine(matchedNativeTrace)
                appendLine()
            }
            exitRecord?.anrTrace?.takeIf { it.isNotBlank() }?.let { trace ->
                appendLine("▶ ANR İZİ (sistem kaydı — ilk $ANR_TRACE_LINES satır)")
                appendLine(trace)
                appendLine()
            }
            appendLine("═══════════════════════════════════════════════════════════════")
        }

        val shouldSurface = verdict.surface || postMortem.looksLikeCrash || !matchedNativeTrace.isNullOrBlank()
        // Sistem kaydı zararsız dediyse (kullanıcı kapattı, arka planda LMK…) erken kaldırılan
        // bayrak geri alınır; kullanıcı her açılışta yanlış "beklenmedik kapanma" uyarısı görmez.
        if (!shouldSurface && earlyFlagSet) clearEarlyUnreadFlag(ctx)

        try {
            if (shouldSurface) {
                File(ctx.filesDir, UNCLEAN_REPORT).writeText(report)
            }
            File(ctx.filesDir, "crash_history.txt").appendText(
                "\n[${if (shouldSurface) "UNCLEAN EXIT" else "BACKGROUND EXIT"}] ${nowText()}\n$report\n"
            )
        } catch (_: Throwable) {}

        if (shouldSurface) {
            uncleanExitDetected.set(true)
            val logcatHeadline = postMortem.headline.lineSequence().firstOrNull().orEmpty()
            val headline = if (exitRecord != null && logcatHeadline.startsWith("SEBEP BULUNAMADI")) {
                verdict.headline
            } else {
                logcatHeadline.ifBlank { verdict.headline }
            }
            setUnreadFlag(ctx, headline)
            // v2.4.342: kazı sonucunun özeti günlüğe (kullanıcı paylaştığında kanıt olsun).
            runCatching {
                KitsugiLog.e(
                    KitsugiLog.Channel.CRASH,
                    "oturum",
                    "Beklenmedik kapanma raporu üretildi",
                    postMortem.headline.lineSequence().take(3).joinToString(" / ")
                )
            }
        }
    }

    /** Eylem izindeki son "ekran →" kaydı: çökme anındaki gerçek ekran (oturum anlık görüntüsü gecikebilir). */
    private fun breadcrumbLastScreen(ctx: Context): String? {
        return try {
            val f = File(ctx.filesDir, BREADCRUMB_FILE)
            if (!f.exists()) return null
            f.readLines().lastOrNull { it.contains("ekran → ") }
                ?.substringAfter("ekran → ")?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    internal data class PostMortem(val headline: String, val lines: String, val looksLikeCrash: Boolean)

    /** Android 11+ `ApplicationExitInfo` kaydının rapora giren özeti. */
    private data class ExitRecord(
        val reason: Int,
        val description: String?,
        val importance: Int,
        val status: Int,
        val timestampMs: Long,
        val pssKb: Long,
        val anrTrace: String?
    ) {
        fun summary(): String = buildString {
            append(PreviousExitClassifier.reasonName(reason))
            append(" (").append(reason).append(')')
            description?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
            append(" | önem=").append(importance)
            append(" | durum=").append(status)
            if (pssKb > 0) append(" | PSS=").append(pssKb / 1024).append(" MB")
        }
    }

    private fun queryExitRecord(ctx: Context, prevPid: Int, started: Long): ExitRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return null
            val info = am.getHistoricalProcessExitReasons(ctx.packageName, prevPid, 5)
                .firstOrNull { it.pid == prevPid && (started <= 0L || it.timestamp >= started) }
                ?: return null
            ExitRecord(
                reason = info.reason,
                description = info.description,
                importance = info.importance,
                status = info.status,
                timestampMs = info.timestamp,
                pssKb = info.pss,
                anrTrace = if (info.reason == ApplicationExitInfo.REASON_ANR) readTraceHead(info) else null
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Sistem çıkış kaydı okunamadı: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readTraceHead(info: ApplicationExitInfo): String? = try {
        info.traceInputStream?.bufferedReader()?.use { reader ->
            AnrTraceSelector.select(reader.lineSequence().take(ANR_TRACE_LINES).toList())
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Ölü sürecin izini logcat arabelleğinden kazar.
     * (Süreç öldükten sonra dahi logd arabelleği son binlerce satırı tutar.)
     */
    private fun scrapePostMortemLogcat(ctx: Context, prevPid: Int, started: Long, lastAlive: Long): PostMortem {
        val raw = try {
            readLogcat(arrayOf("logcat", "-d", "-v", "threadtime", "-t", MAX_LOGCAT_LINES.toString()))
        } catch (_: Throwable) {
            ""
        }
        val crashBuffer = try {
            readLogcat(arrayOf("logcat", "-b", "crash", "-d", "-v", "threadtime", "-t", "600"))
        } catch (_: Throwable) {
            ""
        }

        val all = (crashBuffer + "\n" + raw).lines()
        val pkg = try { ctx.packageName } catch (_: Throwable) { "com.kitsugi.animelist" }
        val result = analyzePostMortemLines(
            all = all,
            pkg = pkg,
            prevPid = prevPid,
            started = started,
            lastAlive = lastAlive,
            nowMs = System.currentTimeMillis(),
            lastScreenName = lastScreen
        )

        try {
            if (result.lines.isNotBlank()) {
                File(ctx.filesDir, POSTMORTEM_LOGCAT).writeText(
                    "=== Ölüm Sonrası Logcat Kanıtları (pid=$prevPid) ===\n" + result.lines
                )
            }
        } catch (_: Throwable) {}

        return result
    }

    internal fun analyzePostMortemLines(
        all: List<String>,
        pkg: String,
        prevPid: Int,
        started: Long,
        lastAlive: Long,
        nowMs: Long = System.currentTimeMillis(),
        lastScreenName: String = "bilinmiyor"
    ): PostMortem {
        val windowStart = if (started > 0L) started - 30_000L else Long.MIN_VALUE
        val windowEnd = if (lastAlive > 0L) lastAlive + 15L * 60_000L else Long.MAX_VALUE

        val relevant = ArrayList<String>()
        var nativeCrash: String? = null
        var anr: String? = null
        var lmkd: String? = null
        var javaOom: String? = null
        var javaFatalHeader: String? = null
        var javaFatalDetail: String? = null
        var captureFatalLinesRemaining = 0
        var amKill: String? = null

        all.forEach { line ->
            val timeMs = logcatTimeMs(line, nowMs)
            if (timeMs != null && (timeMs < windowStart || timeMs > windowEnd)) return@forEach

            val matchesPid = line.contains(" $prevPid ") || line.contains(",$prevPid,") ||
                    line.contains("($prevPid)") || line.contains("pid=$prevPid")
            val namesPid = matchesPid || line.contains("pid: $prevPid") || line.contains("pid $prevPid (")
            val mentionsPkg = line.contains(pkg)
            val isAnrLine = mentionsPkg && (line.contains("ANR in") || line.contains("PID: $prevPid"))

            if (matchesPid && line.contains("FATAL EXCEPTION:") && javaFatalHeader == null) {
                javaFatalHeader = line
                captureFatalLinesRemaining = 4
            } else if (captureFatalLinesRemaining > 0 && matchesPid) {
                captureFatalLinesRemaining--
                val payload = line.substringAfter("AndroidRuntime:", "").trim()
                if (payload.isNotEmpty() && !payload.startsWith("Process:") && !payload.startsWith("at ") && javaFatalDetail == null) {
                    javaFatalDetail = payload
                    captureFatalLinesRemaining = 0
                }
            }

            if (namesPid && (line.contains("Fatal signal") || line.contains("exiting due to SIG_DFL handler")) &&
                nativeCrash == null
            ) {
                nativeCrash = line
            } else if (line.contains("ANR in") && mentionsPkg && anr == null) {
                anr = line
            } else if ((line.contains("lowmemorykiller") || line.contains("lmkd")) && lmkd == null && (mentionsPkg || namesPid)) {
                lmkd = line
            } else if (line.contains("OutOfMemoryError") && matchesPid && javaOom == null) {
                javaOom = line
            } else if ((line.contains("am_kill") || line.contains("Killing ")) && amKill == null && (mentionsPkg || namesPid)) {
                amKill = line
            }

            if (matchesPid || namesPid || isAnrLine || (mentionsPkg && line.contains("FATAL"))) {
                if (relevant.size < LOGCAT_RELEVANT_LIMIT) {
                    relevant.add(line)
                }
            }
        }

        val headline = when {
            nativeCrash != null -> "NATIVE ÇÖKME (SIGSEGV/SIGABRT/SIGBUS — Java istisnası yok)\n  ${nativeCrash!!.trim()}\n  → Native kütüphane (oynatıcı/decoder/GPU) ya da JNI katmanı çöktü."
            javaOom != null -> "JAVA OutOfMemoryError (bellek tükendi)\n  ${javaOom!!.trim()}"
            javaFatalHeader != null -> buildString {
                append("JAVA / ART FATAL HATA (${javaFatalDetail ?: javaFatalHeader!!.trim()})")
                append("\n  ${javaFatalHeader!!.trim()}")
                if (!javaFatalDetail.isNullOrBlank()) append("\n  $javaFatalDetail")
            }
            anr != null -> "ANR (UYGULAMA YANIT VERMEDİ) — ana iş parçacığı kilitlendi, sistem süreci öldürdü\n  ${anr!!.trim()}\n  → Son ekran: $lastScreenName"
            lmkd != null -> "BELLEK YETERSİZLİĞİ (LMKD / düşük bellek nedeniyle sistem öldürdü)\n  ${lmkd!!.trim()}\n  → Görsel/bellek yükü çok yüksek."
            amKill != null -> "SİSTEM TARAFINDAN ÖLDÜRÜLDÜ\n  ${amKill!!.trim()}"
            else -> "SEBEP BULUNAMADI (sessiz SIGKILL olabilir — örn. kullanıcı geri tuşuyla kapatma, " +
                    "arka plan kısıtlaması veya log arabelleği dolmuş olabilir)"
        }

        val looksLikeCrash = nativeCrash != null || javaFatalHeader != null || anr != null ||
            lmkd != null || javaOom != null || amKill != null

        return PostMortem(headline, relevant.joinToString("\n"), looksLikeCrash)
    }

    /**
     * logcat "threadtime" satırının zaman damgasını (MM-dd HH:mm:ss.SSS) epoch ms olarak çözer.
     * logcat yıl bilgisi yazmaz; şimdiki yıl varsayılır, gelecekte görünen tarih bir yıl geri alınır.
     * Biçim tanınmazsa null döner (satır pencere filtresine takılmaz).
     */
    private fun logcatTimeMs(line: String, nowMs: Long): Long? {
        if (line.length < 18) return null
        val head = line.substring(0, 18)
        if (head[2] != '-' || head[5] != ' ' || head[8] != ':' || head[11] != ':' || head[14] != '.') return null
        return try {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = nowMs
            cal.set(java.util.Calendar.MONTH, head.substring(0, 2).toInt() - 1)
            cal.set(java.util.Calendar.DAY_OF_MONTH, head.substring(3, 5).toInt())
            cal.set(java.util.Calendar.HOUR_OF_DAY, head.substring(6, 8).toInt())
            cal.set(java.util.Calendar.MINUTE, head.substring(9, 11).toInt())
            cal.set(java.util.Calendar.SECOND, head.substring(12, 14).toInt())
            cal.set(java.util.Calendar.MILLISECOND, head.substring(15, 18).toInt())
            if (cal.timeInMillis > nowMs + 2L * 24L * 3600_000L) cal.add(java.util.Calendar.YEAR, -1)
            cal.timeInMillis
        } catch (_: Throwable) {
            null
        }
    }

    /** logcat'i oku; asla asılı kalma (sert zaman aşımı). */
    private fun readLogcat(cmd: Array<String>): String {
        val sb = StringBuilder()
        var proc: java.lang.Process? = null
        try {
            proc = Runtime.getRuntime().exec(cmd)
            val out = proc.inputStream
            val reader = Thread {
                try {
                    BufferedReader(InputStreamReader(out)).useLines { lines ->
                        lines.forEach { line ->
                            if (sb.length < 900_000) sb.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {}
            }
            reader.isDaemon = true
            reader.start()
            reader.join(1600)
            try { proc.destroy() } catch (_: Throwable) {}
        } catch (_: Throwable) {
            try { proc?.destroy() } catch (_: Throwable) {}
        }
        return sb.toString()
    }
}
