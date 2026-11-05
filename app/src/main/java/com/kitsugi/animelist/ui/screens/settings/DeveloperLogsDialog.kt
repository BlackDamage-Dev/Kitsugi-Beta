package com.kitsugi.animelist.ui.screens.settings

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.diagnostics.KitsugiLog
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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

/**
 * Günlük kaynağı (v2.4.342).
 *
 * `APP` = uygulamanın **kendi kalıcı günlüğü** (`files/diagnostics/kitsugi_runtime.log`).
 * Eşitleme, ağ, arka plan işleri, çökme ve donma kayıtları burada **kalıcıdır**; logcat halka
 * tamponu dolduğunda silinmez. `SYSTEM` = eski davranış (sistem logcat anlık görüntüsü),
 * yalnızca karşılaştırma/geliştirici için.
 */
private enum class LogSource(val label: String) {
    APP("Uygulama günlüğü"),
    SYSTEM("Sistem (logcat)")
}

/** Kalıcı günlükteki "konu" filtresi: satırdaki `I/SYNC` alanına bakar. */
private enum class LogChannelFilter(val label: String, val key: String?) {
    ALL("Tüm konular", null),
    SYNC("Eşitleme", "SYNC"),
    NET("Ağ", "NET"),
    SOURCE("Kaynak", "SOURCE"),
    WORK("Arka plan", "WORK"),
    FREEZE("Donma", "FREEZE"),
    CRASH("Çökme", "CRASH"),
    SYSTEM("Sistem", "SYSTEM"),
    OTHER("Diğer", "APP");

    fun matches(line: String): Boolean {
        val expected = key ?: return true
        val actual = channelOf(line) ?: return false
        return actual.equals(expected, ignoreCase = true)
    }
}

private val CHANNEL_REGEX = Regex("""\s[VDIWEF]/([A-Za-z]+)""")

private fun channelOf(line: String): String? =
    CHANNEL_REGEX.find(line)?.groupValues?.get(1)

@Composable
private fun logLevelColor(line: String): Color = when (logLevelOf(line)) {
    'E', 'F' -> KitsugiColors.AccentRed
    'W' -> KitsugiColors.AccentOrange
    'I' -> KitsugiColors.AccentPurple
    'D', 'V' -> KitsugiColors.AccentBlue
    else -> if (line.contains("KRİTİK HATA")) KitsugiColors.AccentRed else KitsugiColors.TextSecondary
}

/**
 * Geliştirici Günlükleri diyalogu.
 *
 * Tasarım kuralları:
 *  • Dikey / yatay ekran moduna göre sığdırılır; ekranı komple kaplamaz (fillMaxWidth + heightIn).
 *  • Her satır yatay kaydırılır (horizontalScroll), hiçbir şey kırılmaz / boş alan kalmaz.
 *  • Günlük listesi kalan tüm boşluğu doldurur (weight(1f)); üstteki kontroller kompakt kalır.
 *  • Tüm mantık (logcat, çökme raporu, filtre, kopyala/kaydet/paylaş, otomatik yenile) aynen korunur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dialogMaxHeight = if (isLandscape) 640.dp else 820.dp

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val myPid = remember { android.os.Process.myPid() }

    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var levelFilter by remember { mutableStateOf(LogLevelFilter.ALL) }
    var logSource by remember { mutableStateOf(LogSource.APP) }
    var channelFilter by remember { mutableStateOf(LogChannelFilter.ALL) }
    var logStats by remember { mutableStateOf("") }
    var appLogEmpty by remember { mutableStateOf(false) }
    var onlyThisApp by rememberSaveable { mutableStateOf(false) }
    var wrapLines by rememberSaveable { mutableStateOf(true) }
    var autoRefresh by rememberSaveable { mutableStateOf(false) }

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

    val loadLogs: suspend (Boolean) -> Unit = { showSpinner ->
        if (showSpinner) isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val crashFile = File(context.filesDir, "crash_log.txt")
                crashReport = if (crashFile.exists()) crashFile.readText() else null

                when (logSource) {
                    LogSource.APP -> {
                        val fromDisk = KitsugiLog.readDiskTail(context, 512 * 1024)
                        val fromMemory = KitsugiLog.tail(600)
                        val merged = if (fromDisk.isNotEmpty()) fromDisk else fromMemory
                        logStats = KitsugiLog.statsText(context)
                        appLogEmpty = merged.isEmpty()
                        logLines = merged.takeLast(3000).reversed()
                    }
                    LogSource.SYSTEM -> {
                        appLogEmpty = false
                        logStats = ""
                        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:D"))
                        val lines = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            reader.readLines()
                        }
                        process.waitFor()
                        logLines = lines.takeLast(1500).reversed()
                    }
                }
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
                runCatching { KitsugiLog.clear(context) }
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

    LaunchedEffect(logSource) {
        loadLogs(true)
    }

    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(3000L)
            loadLogs(false)
        }
    }

    val filteredLines = remember(logLines, searchQuery, levelFilter, onlyThisApp, channelFilter) {
        val pidTag = "($myPid)"
        logLines.asSequence()
            .filter { channelFilter.matches(it) }
            .filter { !onlyThisApp || it.contains(pidTag) }
            .filter { levelFilter.matches(it) }
            .filter { searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true) }
            .toList()
    }

    val errorCount = remember(logLines) { logLines.count { LogLevelFilter.ERROR.matches(it) } }
    val warnCount = remember(logLines) { logLines.count { LogLevelFilter.WARN.matches(it) } }
    val infoCount = remember(logLines) { logLines.count { LogLevelFilter.INFO.matches(it) } }
    val debugCount = remember(logLines) { logLines.count { LogLevelFilter.DEBUG.matches(it) } }
    val chipCounts = remember(logLines) {
        LogLevelFilter.entries.associateWith { f -> logLines.count { f.matches(it) } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 32.dp else 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = dialogMaxHeight)
                    .padding(12.dp)
            ) {
                // ── Başlık: tam genişlik, kapat butonu sağda ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.inline_developer_logs_1c5507d),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.inline_in_app_logcat_and_error_268f061),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 11.sp,
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

                Spacer(modifier = Modifier.height(6.dp))

                // ── Araç çubuğu: tek satır, yatay kaydırılabilir ──
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
                        val sharedFile = if (logSource == LogSource.APP) {
                            KitsugiLog.logFile(context)?.takeIf { it.exists() && it.length() > 0 }
                        } else null

                        if (sharedFile != null) {
                            runCatching {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    sharedFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Kitsugi günlüğü")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, KitsugiStrings.get(R.string.loglari_paylas))
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Günlük dosyası paylaşılamadı; metin olarak kopyalayın.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
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
                    }
                    LogToolButton(Icons.Delete, stringResource(R.string.temizle), KitsugiColors.AccentRed) {
                        scope.launch { clearLogs() }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Özet: tek satır, iki tarafa dağılmış ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MiniChip("H", errorCount, KitsugiColors.AccentRed, levelFilter == LogLevelFilter.ERROR) {
                            levelFilter = if (levelFilter == LogLevelFilter.ERROR) LogLevelFilter.ALL else LogLevelFilter.ERROR
                        }
                        MiniChip("U", warnCount, KitsugiColors.AccentOrange, levelFilter == LogLevelFilter.WARN) {
                            levelFilter = if (levelFilter == LogLevelFilter.WARN) LogLevelFilter.ALL else LogLevelFilter.WARN
                        }
                        MiniChip("B", infoCount, KitsugiColors.AccentPurple, levelFilter == LogLevelFilter.INFO) {
                            levelFilter = if (levelFilter == LogLevelFilter.INFO) LogLevelFilter.ALL else LogLevelFilter.INFO
                        }
                        MiniChip("D", debugCount, KitsugiColors.AccentBlue, levelFilter == LogLevelFilter.DEBUG) {
                            levelFilter = if (levelFilter == LogLevelFilter.DEBUG) LogLevelFilter.ALL else LogLevelFilter.DEBUG
                        }
                    }
                }

                if (logStats.isNotBlank()) {
                    Text(
                        text = logStats,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Kaynak seçimi: kalıcı uygulama günlüğü (varsayılan) / sistem logcat ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogSource.entries.forEach { source ->
                        FilterChip(
                            selected = logSource == source,
                            onClick = { logSource = source },
                            label = { Text(source.label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Konu filtresi ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogChannelFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = channelFilter == filter,
                            onClick = { channelFilter = filter },
                            label = { Text(filter.label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                    LogSwitch("Otomatik yenile (3s)", autoRefresh, accentColor) { autoRefresh = it }
                }

                Spacer(modifier = Modifier.height(6.dp))

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

                Spacer(modifier = Modifier.height(6.dp))

                // ── Son çökme raporu uyarısı ──
                if (crashReport != null) {
                    var isCrashExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .tvClickable(shape = RoundedCornerShape(12.dp)) { isCrashExpanded = !isCrashExpanded },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = KitsugiColors.AccentRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
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
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (isCrashExpanded) stringResource(R.string.ui_developer_log_close_hint) else stringResource(R.string.ui_developer_log_details_hint),
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (isCrashExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    Text(
                                        text = crashReport ?: "",
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 10.sp,
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

                // ── Günlük listesi: kalan alanı doldurur, boş alan kalmaz ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Background)
                        .padding(6.dp)
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
                                text = if (appLogEmpty && logSource == LogSource.APP) {
                                    "Henüz kayıt yok. Bir kaynakla eşitleme yapınca, bir hata oluşunca " +
                                        "veya uygulama donunca satırlar burada birikmeye başlar."
                                } else {
                                    stringResource(R.string.inline_no_matching_log_entry_found_7aebe62)
                                },
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (wrapLines) Modifier else Modifier.horizontalScroll(rememberScrollState())
                                    ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredLines) { line ->
                                    SelectionContainer {
                                        Text(
                                            text = line,
                                            color = logLevelColor(line),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
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
private fun MiniChip(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) color.copy(alpha = 0.25f) else KitsugiColors.SurfaceStrong
    val textColor = if (selected) color else KitsugiColors.TextSecondary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (selected) color else color.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(count.toString(), color = textColor, fontSize = 10.sp)
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
