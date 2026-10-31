package com.kitsugi.animelist.ui.screens.settings

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import com.kitsugi.animelist.utils.KitsugiStrings

/** Günlük seviyesi filtreleri (logcat -v time biçimi: "MM-dd HH:mm:ss.SSS L/tag(pid): mesaj"). */
private enum class LogLevelFilter(val label: String) {
    ALL("Tümü"),
    ERROR("Hata"),
    WARN("Uyarı"),
    INFO("Bilgi"),
    DEBUG("Debug");

    fun matches(line: String): Boolean = when (this) {
        ALL -> true
        ERROR -> logLevelOf(line).let { it == 'E' || it == 'F' }
        WARN -> logLevelOf(line) == 'W'
        INFO -> logLevelOf(line) == 'I'
        DEBUG -> logLevelOf(line).let { it == 'D' || it == 'V' }
    }
}

private val LOG_LEVEL_REGEX = Regex("""\s([VDIWEF])/""")

private fun logLevelOf(line: String): Char? =
    LOG_LEVEL_REGEX.find(line)?.groupValues?.get(1)?.firstOrNull()

@Composable
private fun logLevelColor(line: String): Color = when (logLevelOf(line)) {
    'E', 'F' -> KitsugiColors.AccentRed
    'W' -> KitsugiColors.AccentOrange
    'I' -> KitsugiColors.AccentPurple
    'D', 'V' -> KitsugiColors.AccentBlue
    else -> if (line.contains("KRİTİK HATA")) KitsugiColors.AccentRed else KitsugiColors.TextSecondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val myPid = remember { android.os.Process.myPid() }

    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var levelFilter by remember { mutableStateOf(LogLevelFilter.ALL) }
    var onlyThisApp by rememberSaveable { mutableStateOf(false) }
    var wrapLines by rememberSaveable { mutableStateOf(true) }
    var autoRefresh by rememberSaveable { mutableStateOf(false) }

    // Kaydetme diyaloğu geri çağrısı en güncel veriyi okuyabilsin diye anlık görüntü.
    val logSnapshotRef = remember { mutableStateOf<Pair<String?, List<String>>>(null to emptyList()) }

    val saveLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val (snapCrash, snapLines) = logSnapshotRef.value
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            if (snapCrash != null) {
                                writer.write("=== SON ÇÖKME RAPORU ===\n")
                                writer.write(snapCrash)
                                writer.write("\n=========================\n\n")
                            }
                            writer.write(snapLines.joinToString("\n"))
                        }
                    }
                    Toast.makeText(context, KitsugiStrings.get(R.string.loglar_basariyla_kaydedildi), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, KitsugiStrings.get(R.string.kaydetme_basarisiz_x, e.localizedMessage), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Logcat'i ve çökme raporunu okur. showSpinner=false: otomatik yenilemede ekran titremesin.
    val loadLogs: suspend (Boolean) -> Unit = { showSpinner ->
        if (showSpinner) isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val crashFile = File(context.filesDir, "crash_log.txt")
                crashReport = if (crashFile.exists()) crashFile.readText() else null

                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:D"))
                val lines = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLines()
                }
                process.waitFor()
                logLines = lines.takeLast(1500).reversed()
            } catch (e: Exception) {
                logLines = listOf(KitsugiStrings.get(R.string.loglar_okunamadi_x, e.localizedMessage))
            } finally {
                isLoading = false
            }
        }
    }

    val clearLogs: suspend () -> Unit = {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                val crashFile = File(context.filesDir, "crash_log.txt")
                if (crashFile.exists()) crashFile.delete()
                crashReport = null
                logLines = emptyList()
            } catch (e: Exception) {
                logLines = listOf(KitsugiStrings.get(R.string.temizleme_basarisiz_x, e.localizedMessage))
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs(true)
    }

    // Otomatik yenileme: açıkken 3 saniyede bir sessizce güncelle.
    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(3000L)
            loadLogs(false)
        }
    }

    val filteredLines = remember(logLines, searchQuery, levelFilter, onlyThisApp) {
        val pidTag = "($myPid)"
        logLines.asSequence()
            .filter { !onlyThisApp || it.contains(pidTag) }
            .filter { levelFilter.matches(it) }
            .filter { searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true) }
            .toList()
    }

    val errorCount = remember(logLines) { logLines.count { LogLevelFilter.ERROR.matches(it) } }
    val warnCount = remember(logLines) { logLines.count { LogLevelFilter.WARN.matches(it) } }
    val chipCounts = remember(logLines) {
        LogLevelFilter.entries.associateWith { f -> logLines.count { f.matches(it) } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // ── Başlık: tam genişlik. Kapat butonu sağda, başlık alanı kalan tüm genişliği kullanır ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.inline_developer_logs_1c5507d),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.inline_in_app_logcat_and_error_268f061),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LogToolButton(
                        icon = Icons.Close,
                        description = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextPrimary,
                        onClick = onDismiss
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Araç çubuğu: ekran dar olsa da butonlar yatay kaydırılarak erişilebilir ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogToolButton(Icons.Refresh, stringResource(R.string.yenile), KitsugiColors.TextPrimary) {
                        scope.launch { loadLogs(true) }
                    }
                    LogToolButton(Icons.ContentCopy, stringResource(R.string.action_copy), KitsugiColors.TextPrimary) {
                        val textToCopy = buildString {
                            if (crashReport != null) {
                                append("=== SON ÇÖKME RAPORU ===\n")
                                append(crashReport)
                                append("\n=========================\n\n")
                            }
                            append(filteredLines.joinToString("\n"))
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        Toast.makeText(context, KitsugiStrings.get(R.string.loglar_panoya_kopyalandi), Toast.LENGTH_SHORT).show()
                    }
                    LogToolButton(Icons.Save, stringResource(R.string.kaydet), KitsugiColors.TextPrimary) {
                        logSnapshotRef.value = crashReport to filteredLines
                        val timestamp = java.text.SimpleDateFormat(
                            "yyyyMMdd-HHmm",
                            java.util.Locale.US
                        ).format(java.util.Date())
                        saveLogLauncher.launch("Kitsugi-logcat-$timestamp.txt")
                    }
                    LogToolButton(Icons.Share, stringResource(R.string.paylas), KitsugiColors.TextPrimary) {
                        // Filtrelenmiş metni düz metin olarak paylaş (dosya sağlayıcısı gerektirmez).
                        val shareText = buildString {
                            if (crashReport != null) {
                                append("=== SON ÇÖKME RAPORU ===\n")
                                append(crashReport)
                                append("\n=========================\n\n")
                            }
                            append(filteredLines.take(800).joinToString("\n"))
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, KitsugiStrings.get(R.string.loglari_paylas)))
                    }
                    LogToolButton(Icons.Delete, stringResource(R.string.temizle), KitsugiColors.AccentRed) {
                        scope.launch { clearLogs() }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Özet sayaçları ──
                Text(
                    text = stringResource(
                        R.string.gelistirici_log_ozet_sayaclari,
                        logLines.size, errorCount, warnCount, filteredLines.size
                    ),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Seviye filtreleri ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogLevelFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = levelFilter == filter,
                            onClick = { levelFilter = filter },
                            label = {
                                Text(
                                    text = "${filter.label} (${chipCounts[filter] ?: 0})",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Anahtarlar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogSwitch("Sadece bu uygulama", onlyThisApp, accentColor) { onlyThisApp = it }
                    LogSwitch("Satır kaydır", wrapLines, accentColor) { wrapLines = it }
                    LogSwitch("Otomatik yenile", autoRefresh, accentColor) { autoRefresh = it }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Arama ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.inline_search_logs_e_g_turkanime_fea64cb),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Search, null, tint = KitsugiColors.TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Clear, null, tint = KitsugiColors.TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        focusedContainerColor = KitsugiColors.Background,
                        unfocusedContainerColor = KitsugiColors.Background,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Son çökme raporu uyarısı ──
                if (crashReport != null) {
                    var isCrashExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .tvClickable(shape = RoundedCornerShape(12.dp)) { isCrashExpanded = !isCrashExpanded },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = KitsugiColors.AccentRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Warning, null, tint = KitsugiColors.AccentRed)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.inline_latest_crash_report_detected_cff3667),
                                        color = KitsugiColors.AccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isCrashExpanded) stringResource(R.string.ui_developer_log_close_hint) else stringResource(R.string.ui_developer_log_details_hint),
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (isCrashExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(
                                        text = crashReport ?: "",
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Log listesi ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Background)
                        .padding(8.dp)
                ) {
                    when {
                        isLoading && logLines.isEmpty() -> {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        filteredLines.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.inline_no_matching_log_entry_found_7aebe62),
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            val listState = rememberLazyListState()
                            // ── SEÇİM KAPSAMI HER SATIRIN İÇİNDE ────────────────────────────────
                            // DİKKAT: `SelectionContainer { LazyColumn { ... } }` YAZILMAZ!
                            // Lazy liste öğeleri ekran dışına kaydığında kompozisyondan düşer.
                            // Compose'un SelectionManager'ı sürükleme sırasında hâlâ o öğenin
                            // selectableId'sini arar ve şu ölümcül hatayı fırlatır:
                            //   java.util.NoSuchElementException: Cannot find value for key N
                            //   at MultiWidgetSelectionDelegateKt.getDirectionById
                            //   at SelectionManager.getSelectionLayout
                            // (Upstream: compose-multiplatform-core#3480, "SelectionContainer +
                            //  lazy list anchor disposed" hatası — Compose 1.7.x'te hâlâ açık.)
                            // Çözüm: seçim kapsamı her satırın KENDİ içine alınır. Böylece her
                            // SelectionContainer tek bir Text barındırır; kaybolabilecek çoklu
                            // selectable kaydı oluşmaz. Uzun basıp satır seçme/kopyalama çalışır.
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        // Satır kaydırma kapalıysa yatay kaydırma; açıksa satırlar ekrana sığar.
                                        if (wrapLines) Modifier else Modifier.horizontalScroll(rememberScrollState())
                                    ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredLines) { line ->
                                    SelectionContainer {
                                        Text(
                                            text = line,
                                            color = logLevelColor(line),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogToolButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong)
    ) {
        Icon(icon, description, tint = tint)
    }
}

@Composable
private fun LogSwitch(
    label: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.35f)
            )
        )
    }
}
