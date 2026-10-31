package com.kitsugi.animelist.ui.screens.crash

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiTonalButton

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.KitsugiAccentForThemeId
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

class KitsugiCrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intentCrashReport = intent.getStringExtra("crash_report") ?: intent.getStringExtra("crash_summary")

        // ── Çöken ana süreç hâlâ asılı kaldıysa kapat ────────────────────────────────
        // Çökme ekranı AYRI bir süreçte (:crash) ve AYRI bir görevde çalışır; bu yüzden
        // ana sürecin ölümü bizi etkilemez. Eski tasarımda ana süreç kendini 800 ms sonra
        // öldürüyordu ve bu yarış yüzünden çökme ekranı çoğu zaman hiç görünmüyordu.
        val crashedPid = intent.getIntExtra("crashed_pid", -1)
        if (crashedPid > 0 && crashedPid != android.os.Process.myPid()) {
            window.decorView.post {
                try {
                    // GÜVENLİK: PID yeniden kullanılmış olabilir. Öldürmeden önce
                    // /proc/<pid>/cmdline kontrolü ile sürecin gerçekten BİZİM uygulamamıza
                    // ait olduğundan emin ol (yanlış süreci öldürmeyelim).
                    val cmdline = try {
                        java.io.File("/proc/$crashedPid/cmdline").readText()
                    } catch (_: Throwable) {
                        ""
                    }
                    val isOurProcess = cmdline.contains(packageName)
                    if (isOurProcess) {
                        android.os.Process.killProcess(crashedPid)
                        android.util.Log.i("KitsugiCrashActivity", "Çöken ana süreç kapatıldı: pid=$crashedPid")
                    } else {
                        android.util.Log.i("KitsugiCrashActivity",
                            "pid=$crashedPid artık bize ait değil / zaten ölmüş — öldürme atlandı")
                    }
                } catch (_: Throwable) {}
            }
        }

        try {
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Güvenli bağımsız renk — :crash sürecinde DataStore dosya kilidi hatası oluşmaması için DataStore çağrılmaz
            val activeAccentColor = KitsugiColors.AccentTeal

            // Son çökme raporu
            val crashReport = remember {
                if (!intentCrashReport.isNullOrBlank() && intentCrashReport.length > 500) intentCrashReport
                else KitsugiCrashLogger.readCrashLog(context)
            }

            // Geçmiş çökme sayısı (yaklaşık — newline sayısından)
            val historyExists = remember { KitsugiCrashLogger.crashHistoryExists(context) }
            val historySizeKb = remember { KitsugiCrashLogger.crashHistorySizeKb(context) }

            // Sekme: 0 = Son Çökme, 1 = Geçmiş
            var selectedTab by remember { mutableIntStateOf(0) }
            var historyText by remember { mutableStateOf<String?>(null) }

            CompositionLocalProvider(LocalKitsugiAccent provides activeAccentColor) {
                Scaffold(containerColor = KitsugiColors.Background) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(20.dp))

                        // ── Çökme Animasyonu ──────────────────────────────────────────────
                        com.kitsugi.animelist.ui.components.KitsugiCrashAnimation(
                            modifier = Modifier
                                .width(180.dp)
                                .height(101.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            KitsugiStrings.get(R.string.inline_oops_something_broke_53c30c0),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            KitsugiStrings.get(R.string.inline_kitsugi_stopped_unexpectedly_you_c_028a4d1),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )

                        // ── Uygulama Bilgi Etiketi ────────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(KitsugiColors.SurfaceSoft)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Info, null, tint = activeAccentColor, modifier = Modifier.size(14.dp))
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                                color = KitsugiColors.TextSecondary,
                                fontSize = 11.sp
                            )
                            if (historyExists) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(KitsugiColors.AccentRed)
                                )
                                Text(
                                    KitsugiStrings.get(R.string.inline_history_1_s_kb_467259e, historySizeKb),
                                    color = KitsugiColors.AccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── Sekme Başlıkları ──────────────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.Surface)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(KitsugiStrings.get(R.string.son_cokme), KitsugiStrings.get(R.string.cokme_gecmisi)).forEachIndexed { idx, label ->
                                val active = selectedTab == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (active) activeAccentColor else Color.Transparent)
                                        .clickable {
                                            selectedTab = idx
                                            if (idx == 1 && historyText == null) {
                                                historyText = KitsugiCrashLogger.readCrashHistory(context)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (idx == 1 && historyExists) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (active) KitsugiColors.Background
                                                        else KitsugiColors.AccentRed
                                                    )
                                            )
                                            Spacer(Modifier.width(5.dp))
                                        }
                                        Text(
                                            label,
                                            color = if (active) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // ── Rapor Metin Alanı ─────────────────────────────────────────────
                        // Kopyala / Paylaş / İndirilenler'e Kaydet tam metni (displayText) kullanır;
                        // ekrandaki tekil Text bileşeni ise GL_MAX_TEXTURE_SIZE ve 700ms StaticLayout
                        // takılmasını önlemek için en fazla 48 KB önizleme çizer.
                        val displayText = when (selectedTab) {
                            1 -> historyText ?: "Geçmiş yükleniyor..."
                            else -> crashReport
                        }
                        val renderedText = remember(displayText) {
                            val maxPreviewChars = 48_000
                            if (displayText.length <= maxPreviewChars) {
                                displayText
                            } else {
                                val half = maxPreviewChars / 2
                                buildString(maxPreviewChars + 256) {
                                    append(displayText.substring(0, half))
                                    append("\n\n════════════════════════════════════════════════════════════\n")
                                    append("[... Ekran akıcılığı için ")
                                    append(displayText.length - maxPreviewChars)
                                    append(" karakter gizlendi — Kopyala / Paylaş / Gönder TAM metni içerir ...]\n")
                                    append("════════════════════════════════════════════════════════════\n\n")
                                    append(displayText.substring(displayText.length - half))
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, KitsugiColors.Border, RoundedCornerShape(14.dp)),
                            color = KitsugiColors.Surface
                        ) {
                            SelectionContainer {
                                Text(
                                    text = renderedText,
                                    color = if (selectedTab == 0) KitsugiColors.AccentRed.copy(alpha = 0.85f)
                                            else KitsugiColors.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── Butonlar ──────────────────────────────────────────────────────
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Satır 1: Kopyala + Paylaş
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KitsugiTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kitsugi Crash", displayText))
                                        Toast.makeText(context, KitsugiStrings.get(R.string.rapor_kopyalandi), Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.ContentCopy, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(KitsugiStrings.get(R.string.action_copy), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                KitsugiTonalButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, displayText)
                                            putExtra(Intent.EXTRA_SUBJECT, "Kitsugi Hata Raporu v${BuildConfig.VERSION_NAME}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Raporu Paylaş"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Share, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(stringResource(R.string.paylas), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Satır 1.5: Dosya Olarak İndirilenler Klasörüne Kaydet
                            KitsugiTonalButton(
                                onClick = {
                                    val exported = KitsugiCrashLogger.exportReportToDownloads(context)
                                    if (exported != null) {
                                        Toast.makeText(context, "Rapor kaydedildi:\n$exported ✓", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, KitsugiStrings.get(R.string.indirilenler_klasorune_yazilamadi), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Download, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(KitsugiStrings.get(R.string.inline_save_file_to_downloads_txt_d3606fb), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Satır 2: Geliştiriciye Gönder (tüm dosyalar)
                            KitsugiButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val filesToShare = ArrayList<android.net.Uri>()

                                            suspend fun addFileIfExists(file: File?) {
                                                if (file != null && file.exists() && file.length() > 0) {
                                                    try {
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                                            context, "com.kitsugi.animelist.fileprovider", file
                                                        )
                                                        filesToShare.add(uri)
                                                    } catch (_: Exception) {}
                                                }
                                            }

                                            // 1. Cihaz bilgisi dosyası (anlık oluştur)
                                            val infoFile = File(context.filesDir, "device_info.txt")
                                            infoFile.writeText(buildString {
                                                appendLine("=== Kitsugi Cihaz Bilgisi ===")
                                                appendLine("Uygulama : v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.BUILD_TYPE}]")
                                                appendLine("Marka    : ${android.os.Build.BRAND}")
                                                appendLine("Model    : ${android.os.Build.MODEL}")
                                                appendLine("Android  : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                                                appendLine("ABI      : ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                                                appendLine("Üretici  : ${android.os.Build.MANUFACTURER}")
                                                appendLine("Bellek   : ${Runtime.getRuntime().let { "${(it.totalMemory() - it.freeMemory()) / 1024 / 1024}MB / ${it.maxMemory() / 1024 / 1024}MB" }}")
                                                appendLine("Saat     : ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                                            })
                                            addFileIfExists(infoFile)

                                            // 2. Son çökme raporu
                                            // ensureCrashLogFile: en YENİ kanıtı (Java çökmesi /
                                            // sessiz kapanma / native iz / başlatma aşaması)
                                            // seçip paylaşılabilir tek dosya üretir. Eskiden
                                            // getCrashLogFile kullanılıyordu ve günler önceki
                                            // rapor gönderilebiliyordu.
                                            addFileIfExists(KitsugiCrashLogger.ensureCrashLogFile(context))

                                            // 3. Çökme geçmişi
                                            addFileIfExists(KitsugiCrashLogger.getCrashHistoryFile(context))

                                            // 4. Çökme anı logcat
                                            addFileIfExists(KitsugiCrashLogger.getLogcatCrashFile(context))

                                            // 5. FileLoggingTree debug log
                                            addFileIfExists(com.kitsugi.animelist.core.diagnostics.FileLoggingTree.getLogFile(context))

                                            // 6. Anlık logcat dökümü (şimdiki)
                                            val liveLogcatFile = File(context.filesDir, "live_logcat_report.txt")
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "400", "-v", "time", "*:W"))
                                                    val sb = StringBuilder("=== Anlık Logcat (Son 400 satır) ===\n")
                                                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                                                        lines.forEach { sb.appendLine(it) }
                                                    }
                                                    process.waitFor()
                                                    liveLogcatFile.writeText(sb.toString())
                                                } catch (_: Exception) {}
                                            }
                                            addFileIfExists(liveLogcatFile)

                                            if (filesToShare.isNotEmpty()) {
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND_MULTIPLE
                                                    type = "text/plain"
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToShare)
                                                    putExtra(Intent.EXTRA_SUBJECT, "Kitsugi Crash Report v${BuildConfig.VERSION_NAME}")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Geliştiriciye Gönder — ${filesToShare.size} Dosya"))
                                            } else {
                                                Toast.makeText(context, KitsugiStrings.get(R.string.paylasilacak_dosya_bulunamadi), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.hata_x, e.message), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Send, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(KitsugiStrings.get(R.string.inline_send_to_developer_all_files_0f054f6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Satır 3: Geçmişi Temizle + Yeniden Başlat
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (historyExists) {
                                    KitsugiTonalButton(
                                        onClick = {
                                            KitsugiCrashLogger.clearHistory(context)
                                            historyText = "Geçmiş temizlendi."
                                            Toast.makeText(context, KitsugiStrings.get(R.string.cokme_gecmisi_temizlendi), Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.DeleteSweep, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.gecmisi_temizle), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                KitsugiButton(
                                    onClick = {
                                        KitsugiCrashLogger.markCrashAsRead(context)
                                        val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        restartIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        if (restartIntent != null) context.startActivity(restartIntent)
                                        finish()
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                        System.exit(0)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = activeAccentColor,
                                        contentColor = KitsugiColors.Background
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = if (historyExists) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.RestartAlt, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(KitsugiStrings.get(R.string.inline_restart_836b2de), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        } catch (t: Throwable) {
            // Compose arayüzü kurulumu çökerse kullanıcıyı boş ekranda bırakma.
            showEmergencyFallback(t)
        }
    }

    /**
     * Çökme ekranının kendisi çizilemezse gösterilen düz-Android son çare ekranı.
     * (Kullanıcı hiçbir durumda boş/siyah ekranda kalmamalı.)
     */
    private fun showEmergencyFallback(t: Throwable) {
        try {
            val report = try { KitsugiCrashLogger.readCrashLog(this) } catch (_: Throwable) { "(rapor okunamadı)" }
            val textView = android.widget.TextView(this).apply {
                text = buildString {
                    appendLine(KitsugiStrings.get(R.string.kitsugi_cokme_ekrani_acilamadi))
                    appendLine("${t.javaClass.name}: ${t.message}")
                    appendLine()
                    appendLine("─── KAYITLI ÇÖKME RAPORU ───")
                    appendLine(report.take(4000))
                }
                setTextIsSelectable(true)
                textSize = 10f
                setPadding(32, 32, 32, 32)
            }
            setContentView(android.widget.ScrollView(this).apply { addView(textView) })
        } catch (_: Throwable) {}
    }
}
