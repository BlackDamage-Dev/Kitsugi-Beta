package com.kitsugi.animelist.core.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kitsugi.animelist.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * KitsugiCrashLogger — Merkezi çökme günlükleme sistemi.
 *
 * Görevleri:
 *  • Çökme anında KANITI ÖNCE yazar (bayrak dosyası + prefs) — rapor üretimi yarıda kalsa bile
 *    uygulama sonraki açılışta çökmeyi bildirir.
 *  • Tam ayrıntılı rapor dosyası yazar (senkron, süreç ölmeden önce)
 *  • Geçmiş çökmeleri `crash_history.txt` dosyasında tutar
 *  • Çökme anında logcat anlık görüntüsünü yakalar
 *  • TÜM istisna türlerini (Error dahil) güvenle ele alır — `catch (e: Exception)` kullanmaz
 */
object KitsugiCrashLogger {

    private const val CRASH_LOG_FILE     = "crash_log.txt"
    /** Paylaşım kartına verilen, her zaman EN YENİ kanıtı içeren rapor dosyası. */
    private const val SHARE_REPORT_FILE  = "Kitsugi_Crash_Report.txt"
    private const val CRASH_HISTORY_FILE = "crash_history.txt"
    private const val LOGCAT_CRASH_FILE  = "logcat_at_crash.txt"
    private const val PENDING_FLAG_FILE  = "crash_pending.flag"
    private const val MAX_HISTORY_BYTES  = 400 * 1024L  // 400 KB
    private const val MAX_LOGCAT_LINES   = 500
    private const val PREFS_NAME         = "kitsugi_crash_prefs"
    private const val LAST_CRASH_PID_KEY = "last_crash_pid"

    // SimpleDateFormat thread-safe DEĞİLDİR → kilit altında kullanılır.
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dateFmtLock = Any()

    private fun nowText(): String = synchronized(dateFmtLock) { dateFmt.format(Date()) }

    // ── Çökme bayrağı: rapor üretiminden ÖNCE, en ucuz şekilde ─────────────────

    /**
     * Bir sonraki açılışta kesin görünecek "çökme oldu" işaretini yazar.
     * İki bağımsız kanal kullanılır (dosya + SharedPreferences). commit() senkrondur;
     * süreç milisaniyeler sonra öldürülse bile kayıt kalıcıdır.
     */
    fun markCrashPending(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = System.currentTimeMillis()
        val headline = buildString {
            append(throwable.javaClass.name)
            if (!throwable.message.isNullOrBlank()) append(": ${throwable.message}")
        }.take(300)

        // 4. satır: çöken sürecin pid'i. Oturum gözetmeni bir sonraki açılışta aynı ölümü
        // ikinci kez "sessiz çökme" diye raporlamasın diye kullanılır (ilk 3 satır geriye uyumlu).
        val pid = android.os.Process.myPid()
        try {
            File(context.filesDir, PENDING_FLAG_FILE).writeText(
                "$stamp\n${thread.name}\n$headline\n$pid\n"
            )
        } catch (_: Throwable) {}

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", true)
                .putBoolean("has_unclean_exit", false)
                .putLong("last_crash_time", stamp)
                .putString("last_crash_title", headline)
                .putInt(LAST_CRASH_PID_KEY, pid)
                .commit()
        } catch (_: Throwable) {}
    }

    /**
     * [pid] numaralı süreç, [sinceMs] anından sonra bu yakalayıcı tarafından kaydedilmiş bir
     * çökmeyle mi sonlandı? (PID'ler yeniden kullanılabildiği için zaman da karşılaştırılır.)
     * Oturum gözetmeni aynı ölüm için ikinci bir "temiz olmayan kapanma" raporu üretmesin.
     */
    fun wasCrashRecordedForPid(context: Context, pid: Int, sinceMs: Long): Boolean {
        if (pid <= 0) return false
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(LAST_CRASH_PID_KEY, -1) == pid &&
                prefs.getLong("last_crash_time", 0L) >= sinceMs
        } catch (_: Throwable) {
            false
        }
    }

    // ── Çökme Raporu Oluşturma ─────────────────────────────────────────────────

    /**
     * Tam ayrıntılı çökme raporu metni oluşturur.
     * Senkron çalışır — thread kill edilmeden önce çağrılmalıdır.
     */
    fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        isForeground: Boolean
    ): String = buildString {

        val now = nowText()
        val launchTime = KitsugiApplication_LaunchTime ?: System.currentTimeMillis()
        val uptime = (System.currentTimeMillis() - launchTime) / 1000L

        // ── Başlık ──
        appendLine("╔══════════════════════════════════════════════════════════════╗")
        appendLine("║           KİTSUGİ CRASH REPORT — $now")
        appendLine("╚══════════════════════════════════════════════════════════════╝")
        appendLine()

        // ── Uygulama Bilgisi ──
        appendLine("▶ UYGULAMA BİLGİSİ")
        appendLine("  Version    : ${runCatching { BuildConfig.VERSION_NAME }.getOrDefault("?")} " +
                "(${runCatching { BuildConfig.VERSION_CODE }.getOrDefault(0)})")
        appendLine("  Build Type : ${runCatching { BuildConfig.BUILD_TYPE }.getOrDefault("?")}")
        appendLine("  Uptime     : ${uptime}s")
        appendLine("  PID        : ${android.os.Process.myPid()}")
        appendLine()

        // ── Cihaz Bilgisi ──
        appendLine("▶ CİHAZ BİLGİSİ")
        appendLine("  Marka      : ${Build.BRAND}")
        appendLine("  Model      : ${Build.MODEL}")
        appendLine("  Üretici    : ${Build.MANUFACTURER}")
        appendLine("  Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("  Cihaz      : ${Build.DEVICE}")
        appendLine("  Ürün       : ${Build.PRODUCT}")
        appendLine("  ABI        : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A"}")
        appendLine()

        // ── Thread Bilgisi ──
        val threadType = if (isForeground) "FOREGROUND (Ana UI Thread)" else "BACKGROUND (${thread.name})"
        appendLine("▶ THREAD BİLGİSİ")
        appendLine("  Thread     : ${thread.name}")
        appendLine("  Thread ID  : ${runCatching { thread.id }.getOrDefault(-1L)}")
        appendLine("  Tip        : $threadType")
        appendLine("  Daemon     : ${thread.isDaemon}")
        appendLine()

        // ── Bellek Bilgisi ──
        try {
            val rt = Runtime.getRuntime()
            val totalMb = rt.totalMemory() / 1024 / 1024
            val freeMb  = rt.freeMemory()  / 1024 / 1024
            val usedMb  = totalMb - freeMb
            val maxMb   = rt.maxMemory()   / 1024 / 1024
            appendLine("▶ BELLEK DURUMU")
            appendLine("  Kullanılan : ${usedMb} MB / ${maxMb} MB (max)")
            appendLine("  Toplam JVM : ${totalMb} MB")
            appendLine("  Serbest    : ${freeMb} MB")
            runCatching {
                appendLine("  Önbellekler: ${com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.describe()}")
            }
            appendLine()
        } catch (_: Throwable) {}

        // ── Hata Zinciri ──
        appendLine("▶ HATA ZİNCİRİ")
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < 10) {
            val prefix = if (depth == 0) "  HATA  " else "  NEDEN $depth"
            appendLine("  $prefix : ${cause.javaClass.name}: ${cause.message}")
            cause = cause.cause
            depth++
        }
        appendLine()

        // ── Tam Stacktrace ──
        appendLine("▶ TAM STACKTRACE")
        appendLine(runCatching { android.util.Log.getStackTraceString(throwable) }.getOrDefault("(stacktrace alınamadı)"))
        appendLine()

        // ── Son ekran + eylem izi (breadcrumbs) ──
        runCatching {
            appendLine("▶ SON BİLİNEN EKRAN")
            appendLine("  ${KitsugiSessionSupervisor.lastKnownScreen()}")
            appendLine()
            appendLine("▶ UYGULAMA EYLEM İZİ (son eylemler, sondan geriye)")
            val ctx = com.kitsugi.animelist.KitsugiApplication.getInstance()
            if (ctx != null) {
                KitsugiSessionSupervisor.breadcrumbText(ctx).lines().asReversed().take(30)
                    .forEach { appendLine("  $it") }
            } else {
                appendLine("  (uygulama bağlamı yok)")
            }
            appendLine()
        }

        // ── Aktif Thread Listesi (adet sınırlı — OOM/SOE durumunda patlamasın) ──
        try {
            appendLine("▶ AKTİF THREAD LİSTESİ (ilk 25)")
            Thread.getAllStackTraces().keys
                .take(25)
                .sortedByDescending { it.name == "main" }
                .forEach { t ->
                    val state = t.state.name.take(8).padEnd(8)
                    appendLine("  [$state] ${t.name} (daemon=${t.isDaemon})")
                }
            appendLine()
        } catch (_: Throwable) {}

        appendLine("═══════════════════════════════════════════════════════════════")
    }

    // ── Senkron Dosya Yazımı (crash anında çağrılır) ──────────────────────────

    /**
     * Çökme raporunu `crash_log.txt` ve `crash_history.txt` dosyalarına SENKRON yazar.
     * Tüm adımlar `Throwable` güvenlidir: Error türleri (OOM, StackOverflow) raporun
     * kaydedilmesini engelleyemez.
     */
    fun writeCrashReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        isForeground: Boolean
    ): String {
        val report = try {
            buildCrashReport(thread, throwable, isForeground)
        } catch (t: Throwable) {
            "=== KİTSUGİ ÇÖKME (rapor üretimi kısmen başarısız: ${t.javaClass.simpleName}) ===\n" +
                "${throwable.javaClass.name}: ${throwable.message}\n" +
                runCatching { android.util.Log.getStackTraceString(throwable) }.getOrDefault("")
        }

        // 1. Ana dahili crash_log.txt
        try {
            File(context.filesDir, CRASH_LOG_FILE).writeText(report)
        } catch (_: Throwable) {}

        // 2. Yalnızca BU SÜRECE (aynı PID) ait native çökme izi varsa yanına kopyala
        try {
            val nativeTrace = NativeCrashBridge.readRecentNativeCrash(
                context = context,
                expectedPid = android.os.Process.myPid()
            )
            if (!nativeTrace.isNullOrBlank()) {
                File(context.filesDir, CRASH_LOG_FILE).appendText(
                    "\n\n▶ SON NATIVE ÇÖKME İZİ\n$nativeTrace\n"
                )
            }
        } catch (_: Throwable) {}

        // 3. Harici uygulama dizini (dosya yöneticisiyle erişilebilir)
        try {
            context.getExternalFilesDir(null)?.let { extDir ->
                File(extDir, CRASH_LOG_FILE).writeText(report)
            }
        } catch (_: Throwable) {}

        // 4. Harici önbellek dizini
        try {
            context.externalCacheDir?.let { cacheDir ->
                File(cacheDir, CRASH_LOG_FILE).writeText(report)
            }
        } catch (_: Throwable) {}

        // 5. Genel İndirilenler klasörü (MediaStore — Android 10+ uyumlu)
        try { saveTextToDownloads(context, "Kitsugi_Crash_Report.txt", report) } catch (_: Throwable) {}

        // 6. crash_history.txt (geçmiş tüm çökmeler — ekleme modunda)
        try { appendToHistory(context, report, isForeground) } catch (_: Throwable) {}

        return report
    }

    /** Geçmiş çökme kaydını ekler; dosya çok büyürse ilk yarısını keser. */
    private fun appendToHistory(context: Context, report: String, isForeground: Boolean) {
        try {
            val historyFile = File(context.filesDir, CRASH_HISTORY_FILE)
            val tag = if (isForeground) "[FOREGROUND CRASH]" else "[BACKGROUND CRASH]"
            val entry = "\n$tag ${nowText()}\n$report\n"

            // Boyut kontrolü — çok büyürse yarıya indir
            if (historyFile.exists() && historyFile.length() > MAX_HISTORY_BYTES) {
                val content = historyFile.readText()
                val trimmed = content.drop(content.length / 2)
                historyFile.writeText("=== Eski geçmiş kısaltıldı ===\n$trimmed")
            }

            historyFile.appendText(entry)
        } catch (_: Throwable) {}
    }

    /**
     * Çökme anında logcat anlık görüntüsünü yakalar ve dosyaya yazar.
     * Yeterli okuma penceresi (1500 ms) ve boş çıktı yedeği sayesinde 73 baytlık boş başlık dosyası oluşmaz.
     */
    fun captureLogcatSync(context: Context, fallbackReport: String? = null) {
        var process: java.lang.Process? = null
        try {
            process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-b", "main,system,crash", "-d", "-t", MAX_LOGCAT_LINES.toString(), "-v", "threadtime", "*:W")
            )
            val output = StringBuilder()
            output.appendLine("=== Çökme Anı Logcat (Son $MAX_LOGCAT_LINES Satır) — ${nowText()} ===")
            var capturedLines = 0

            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                        lines.forEach { line ->
                            if (output.length < 400_000) {
                                output.appendLine(line)
                                capturedLines++
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
            readerThread.isDaemon = true
            readerThread.start()
            readerThread.join(1_500)
            try { process.destroy() } catch (_: Throwable) {}

            val targetFile = File(context.filesDir, LOGCAT_CRASH_FILE)
            if (capturedLines == 0) {
                // Eğer son birkaç saniye içinde yazılmış dolu bir logcat dosyası zaten varsa ezme.
                val existingFresh = targetFile.exists() &&
                    targetFile.length() > 128L &&
                    System.currentTimeMillis() - targetFile.lastModified() < 15_000L
                if (existingFresh) return
                if (!fallbackReport.isNullOrBlank()) {
                    output.appendLine("(logd tamponu zaman aşımına uğradı — doğrudan yakalanan çökme raporu eklendi)")
                    output.appendLine(fallbackReport)
                }
            }
            targetFile.writeText(output.toString())
        } catch (_: Throwable) {
            try { process?.destroy() } catch (_: Throwable) {}
        }
    }

    // ── Yardımcı Okuyucular ───────────────────────────────────────────────────

    /** Dosya zaman damgası (epoch ms) — "hangisi daha yeni" karşılaştırmaları için. */
    private fun lastModified(file: File): Long =
        try { if (file.exists()) file.lastModified() else 0L } catch (_: Throwable) { 0L }

    fun readCrashLog(context: Context): String {
        return try {
            val primary  = File(context.filesDir, CRASH_LOG_FILE)
            val unclean  = KitsugiSessionSupervisor.uncleanExitReportFile(context)

            val primaryTime = lastModified(primary)
            val uncleanTime = lastModified(unclean)

            // Açılış aşaması izi: SADECE uygulama ana ekranı açılamadan öldüyse doludur.
            // "Uygulama açılmadan çöküyor" vakasında asıl kanıt bu bloktur.
            val stageBlock = StartupStage.reportBlock(context)

            val body = when {
                // Hangisi daha yeniyse onu göster: Java çökmesi mi, sessiz ölüm mü?
                // NOT: unclean_exit.txt kendi oturumuna (prevPid) ait native izi zaten üretim anında içerir;
                // burada tekrar PID doğrulamasız native_crash.txt eklemek eski günlerin bayat izini yapıştırıyordu.
                uncleanTime > 0 && uncleanTime >= primaryTime -> unclean.readText()
                primaryTime > 0 -> primary.readText()
                stageBlock.isNotBlank() ->
                    "▶ ÖNCEKİ OTURUM ANA EKRANA ULAŞMADAN SONLANDI\n" +
                        "(Bu sürüm için kayıtlı Java çökme raporu yok. Bu tek başına çökme kanıtı değildir;\n" +
                        " kullanıcı kapatması, sistem sonlandırması veya native ölüm olabilir.\n" +
                        " Aşağıdaki başlatma aşaması izi yalnızca son çalışan init adımını gösterir.)"
                else -> {
                    val nativeTrace = NativeCrashBridge.readRecentNativeCrash(
                        context = context,
                        maxAgeMs = NativeCrashBridge.UNCLEAN_TRACE_MAX_AGE_MS,
                        expectedPid = null
                    )
                    if (!nativeTrace.isNullOrBlank()) "▶ NATIVE ÇÖKME İZİ\n$nativeTrace"
                    else "Henüz çökme kaydı bulunamadı."
                }
            }

            if (stageBlock.isBlank()) body else "$body\n$stageBlock"
        } catch (_: Throwable) { "Crash log okunamadı." }
    }

    fun readCrashHistory(context: Context): String {
        return try {
            File(context.filesDir, CRASH_HISTORY_FILE).let {
                if (it.exists()) it.readText() else KitsugiStrings.get(R.string.diag_crash_history_not_found)
            }
        } catch (_: Throwable) { "Crash geçmişi okunamadı." }
    }

    fun crashHistoryExists(context: Context): Boolean =
        File(context.filesDir, CRASH_HISTORY_FILE).let { it.exists() && it.length() > 50 }

    fun crashHistorySizeKb(context: Context): Long =
        try { File(context.filesDir, CRASH_HISTORY_FILE).length() / 1024 } catch (_: Throwable) { 0L }

    fun clearHistory(context: Context) {
        try { File(context.filesDir, CRASH_HISTORY_FILE).delete() } catch (_: Throwable) {}
        try { File(context.filesDir, CRASH_LOG_FILE).delete() } catch (_: Throwable) {}
        try { File(context.filesDir, LOGCAT_CRASH_FILE).delete() } catch (_: Throwable) {}
        try { File(context.filesDir, PENDING_FLAG_FILE).delete() } catch (_: Throwable) {}
        try { context.getExternalFilesDir(null)?.let { File(it, CRASH_LOG_FILE).delete() } } catch (_: Throwable) {}
        try { NativeCrashBridge.clearNativeCrash(context) } catch (_: Throwable) {}
        try { KitsugiSessionSupervisor.clearUncleanExitReport(context) } catch (_: Throwable) {}
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        } catch (_: Throwable) {}
    }

    /**
     * Raporu kullanıcının doğrudan erişebileceği İndirilenler klasörüne yazar.
     * Android 10+ için MediaStore kullanılır (izin gerekmez); alt sürümlerde doğrudan dosya.
     *
     * @return kullanıcıya gösterilecek konum metni; başarısızsa null
     */
    fun exportReportToDownloads(context: Context): String? {
        return try {
            val report = readCrashLog(context)
            saveTextToDownloads(context, "Kitsugi_Crash_Report.txt", report)
        } catch (_: Throwable) { null }
    }

    /**
     * Paylaşım (share sheet) için kullanılacak çökme raporu dosyasını döndürür.
     * Dosya yoksa ya da boşsa (örn. sessiz kapanma senaryosunda `crash_log.txt`
     * yazılmamış olabilir) güncel rapor metni [readCrashLog] ile dosyaya yazılır.
     * Dönen dosya FileProvider `files-path` köküyle (`${packageName}.fileprovider`)
     * paylaşılabilir. Rapor metni de boşsa null döner.
     */
    fun ensureCrashLogFile(context: Context): File? {
        return try {
            val primary = File(context.filesDir, CRASH_LOG_FILE)
            val unclean = KitsugiSessionSupervisor.uncleanExitReportFile(context)

            val primaryTime = lastModified(primary)
            val uncleanTime = lastModified(unclean)

            // ── BAYAT RAPOR TUZAĞI (düzeltildi) ─────────────────────────────────────
            // Eski kod yalnızca "dosya var mı?" diye bakıyordu. crash_log.txt günler
            // önceki bir Java çökmesini tutarken daha YENİ bir sessiz kapanma
            // (unclean_exit.txt + native iz + başlatma aşaması) olabiliyordu. Bu yüzden
            // "Hata dosyasını paylaş" her seferinde ESKİ raporu gönderiyor, yeni çökme
            // hiçbir zaman bize ulaşmıyordu.
            // KURAL: paylaşılan dosya her zaman `readCrashLog()` çıktısıdır; o fonksiyon
            // en yeni kaynağı (unclean/native/stage) kendisi seçer.
            val primaryIsStale = uncleanTime > primaryTime
            val primaryUsable = primary.exists() && primary.length() > 0L

            if (primaryUsable && !primaryIsStale && StartupStage.read(context) == null) {
                // Durum net: taze Java çökme raporu var, daha yeni sessiz ölüm yok.
                return primary
            }

            val report = readCrashLog(context)
            if (report.isBlank() || report == "Henüz çökme kaydı bulunamadı.") {
                return primary.takeIf { it.exists() && it.length() > 0L }
            }

            // Paylaşım için ayrı dosya: crash_log.txt (Java çökmesi arşivi) EZİLMEZ.
            val shareFile = File(context.filesDir, SHARE_REPORT_FILE)
            shareFile.writeText(report)
            shareFile.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Throwable) { null }
    }

    /** Metni İndirilenler/Kitsugi klasörüne kaydeder. @return kayıt konumu açıklaması */
    fun saveTextToDownloads(context: Context, displayName: String, text: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kitsugi")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                    out.flush()
                } ?: return null
                return "İndirilenler/Kitsugi/$displayName"
            } catch (_: Throwable) {
                return null
            }
        }

        // Android 9 ve altı: doğrudan genel İndirilenler klasörü
        return try {
            @Suppress("DEPRECATION")
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadDir, "Kitsugi")
            if (!targetDir.exists() && !targetDir.mkdirs()) return null
            val target = File(targetDir, displayName)
            target.writeText(text)
            target.absolutePath
        } catch (_: Throwable) { null }
    }

    // ── Kullanıcı bildirimi ──────────────────────────────────────────────────

    /**
     * Gösterilmemiş bir çökme/ani kapanma var mı?
     * Üç bağımsız kanal kontrol edilir (prefs + bayrak dosyası + temiz olmayan kapanma raporu).
     */
    fun hasUnreadCrash(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean("has_unread_crash", false)) {
                val time = prefs.getLong("last_crash_time", 0L)
                // 7 günden eski bildirimler rahatsız etmesin
                if (time <= 0L || System.currentTimeMillis() - time < 7L * 24 * 60 * 60 * 1000) {
                    return true
                }
            }
            if (File(context.filesDir, PENDING_FLAG_FILE).let { it.exists() && it.length() > 0 }) {
                val time = lastModified(File(context.filesDir, PENDING_FLAG_FILE))
                if (time <= 0L || System.currentTimeMillis() - time < 7L * 24 * 60 * 60 * 1000) {
                    return true
                }
            }
            if (KitsugiSessionSupervisor.hasUncleanExitReport(context)) {
                // Dosya tabanlı kontrol de yaşa bakmalı: aylar önceki bir sessiz kapanma
                // raporu silinmeden kaldıysa uygulama her açılışta "beklenmedik kapanma"
                // uyarısı gösteriyordu. 7 günden eski iz artık rahatsız etmez.
                val time = lastModified(KitsugiSessionSupervisor.uncleanExitReportFile(context))
                if (time <= 0L || System.currentTimeMillis() - time < 7L * 24 * 60 * 60 * 1000) {
                    return true
                }
            }
            false
        } catch (_: Throwable) { false }
    }

    /** Bir sonraki açılış penceresinin başlığı için kısa özet. */
    fun crashHeadline(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString("last_crash_title", null)
                ?: File(context.filesDir, PENDING_FLAG_FILE)
                    .takeIf { it.exists() }
                    ?.readLines()?.getOrNull(2)
                ?: "Bilinmeyen hata"
        } catch (_: Throwable) { "Bilinmeyen hata" }
    }

    fun isUncleanExit(context: Context): Boolean =
        try { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("has_unclean_exit", false) } catch (_: Throwable) { false }

    fun markCrashAsRead(context: Context) {
        try {
            File(context.filesDir, PENDING_FLAG_FILE).delete()
        } catch (_: Throwable) {}
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", false)
                .putBoolean("has_unclean_exit", false)
                .commit()
        } catch (_: Throwable) {}
        // Sessiz kapanma ve native iz "gösterildi" sayılır; geçmiş dosyasında kaydı kalır,
        // bayat native_crash.txt sonraki oturumlara sızmaz.
        try { KitsugiSessionSupervisor.clearUncleanExitReport(context) } catch (_: Throwable) {}
        try { NativeCrashBridge.clearNativeCrash(context) } catch (_: Throwable) {}
    }

    fun getLogcatCrashFile(context: Context): File = File(context.filesDir, LOGCAT_CRASH_FILE)
    fun getCrashLogFile(context: Context): File    = File(context.filesDir, CRASH_LOG_FILE)
    fun getCrashHistoryFile(context: Context): File = File(context.filesDir, CRASH_HISTORY_FILE)

    // ─── Launch zamanını Application sınıfından alır ──────────────────────────
    @Volatile
    var KitsugiApplication_LaunchTime: Long? = null
}
