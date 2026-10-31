package com.kitsugi.animelist.ui.components

import android.content.ClipData
import com.kitsugi.animelist.ui.utils.uniqueItems
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kitsugi.animelist.data.auth.CrossSyncReportFormatter
import com.kitsugi.animelist.data.auth.CrossSyncReportStore
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.model.CrossPlatformStats
import com.kitsugi.animelist.model.CrossSyncLogEntry
import com.kitsugi.animelist.model.CrossSyncProgressState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

private enum class CrossSyncLogFilter { All, Issues, Errors, Warnings }

/**
 * Çapraz eşitleme paneli — tam ekran dialog.
 *
 * Dikey: ilerleme kartı → hesap özetleri (yatay kaydırma) → günlük (kalan tüm yükseklik) → alt eylem çubuğu.
 * Yatay: sol sütun (ilerleme, hesaplar, eylemler; dikey kaydırılabilir) + sağ sütun (filtreler ve günlük).
 * Düzen [LocalConfiguration] üzerinden yönlendirme değişiminde otomatik olarak yeniden kurulur.
 */
@Composable
fun KitsugiCrossSyncDialog(
    state: CrossSyncProgressState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {},
    onStart: () -> Unit = {}
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactHeight = configuration.screenHeightDp < 480
    // Yatay düzende sol sütun (ilerleme + hesaplar + eylemler) ekranın %40'ı kadar, ancak 300–460 dp
    // aralığında tutulur; böylece geniş tabletlerde günlük listesi alanın çoğunu alır, telefonlarda
    // sütun okunabilir kalır.
    val landscapeSidePaneWidth = (configuration.screenWidthDp * 0.4f).dp.coerceIn(300.dp, 460.dp)
    val logsListState = rememberLazyListState()
    val reportSnapshot = remember { mutableStateOf("") }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var logFilter by remember { mutableStateOf(CrossSyncLogFilter.All) }
    var clockNow by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isRunning, state.startedAt, state.finishedAt) {
        if (state.isRunning) {
            while (true) {
                clockNow = System.currentTimeMillis()
                delay(1_000L)
            }
        } else {
            clockNow = state.finishedAt ?: System.currentTimeMillis()
        }
    }

    val saveReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val report = reportSnapshot.value
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error(context.getString(R.string.cross_sync_selected_file_error))
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                    }
                    Toast.makeText(context, KitsugiStrings.get(R.string.esitleme_raporu_dosyaya_kaydedildi), Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, "Rapor kaydedilemedi: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val elapsedMs = state.startedAt?.let { (clockNow - it).coerceAtLeast(0L) } ?: 0L
    val phaseElapsedMs = state.progressPhaseStartedAt?.let { (clockNow - it).coerceAtLeast(0L) } ?: elapsedMs
    val estimatedRemainingMs = if (state.totalItems > 0 && state.processedItems > 0 && state.isRunning) {
        (phaseElapsedMs.toDouble() / state.processedItems * (state.totalItems - state.processedItems).coerceAtLeast(0)).toLong()
    } else null
    val lastMeaningfulUpdate = state.lastUpdatedAt ?: state.startedAt
    val looksStalled = state.isRunning && lastMeaningfulUpdate != null &&
        clockNow - lastMeaningfulUpdate > STALLED_WARNING_AFTER_MS

    val sourceLogs = if (!state.isRunning && state.reportLogs.isNotEmpty()) state.reportLogs else state.logs
    val visibleLogs = remember(sourceLogs, logFilter) {
        when (logFilter) {
            CrossSyncLogFilter.All -> sourceLogs
            CrossSyncLogFilter.Issues -> sourceLogs.filter { it.isError || it.isWarning }
            CrossSyncLogFilter.Errors -> sourceLogs.filter { it.isError }
            CrossSyncLogFilter.Warnings -> sourceLogs.filter { it.isWarning }
        }
    }
    val errorLogCount = remember(sourceLogs) { sourceLogs.count { it.isError } }
    val warningLogCount = remember(sourceLogs) { sourceLogs.count { it.isWarning } }

    // En yeni olayı görünür tut; filtre değişince listenin yeni sonuna kaydır.
    LaunchedEffect(visibleLogs.size, visibleLogs.lastOrNull()?.id, logFilter) {
        if (visibleLogs.isNotEmpty()) logsListState.scrollToItem(visibleLogs.lastIndex)
    }

    fun shareReport() {
        val reportState = state
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    CrossSyncReportStore.createShareableReport(context, reportState)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        context.getString(
                            R.string.cross_sync_share_subject,
                            if (reportState.isRunning) context.getString(R.string.cross_sync_share_partial_suffix) else ""
                        )
                    )
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.cross_sync_share_description) +
                            if (reportState.isRunning) context.getString(R.string.cross_sync_share_partial_note) else ""
                    )
                    clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.cross_sync_share_chooser))
                )
            } catch (error: Exception) {
                Toast.makeText(context, KitsugiStrings.get(R.string.cs_diag_report_share_failed, error.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveReport() {
        val reportState = state
        val timestamp = reportState.finishedAt ?: reportState.startedAt ?: System.currentTimeMillis()
        val filename = CrossSyncReportFormatter.fileName(timestamp)
        scope.launch {
            try {
                reportSnapshot.value = withContext(Dispatchers.IO) {
                    CrossSyncReportStore.format(context, reportState)
                }
                saveReportLauncher.launch(filename)
            } catch (error: Exception) {
                Toast.makeText(context, KitsugiStrings.get(R.string.rapor_hazirlanamadi_x, error.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    val hasReportContent = state.totalEventCount > 0 || state.logs.isNotEmpty() || state.errorMessage != null
    val statusColor = when {
        state.isCompleted -> KitsugiColors.AccentGreen
        state.errorMessage != null -> ERROR_COLOR
        state.isRunning -> accentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusSubtitle = when {
        state.isCompleted -> if (state.issueCount > 0) {
            stringResource(R.string.cross_sync_status_completed_issues, formatDuration(elapsedMs), state.issueCount)
        } else {
            stringResource(R.string.cross_sync_status_completed_clean, formatDuration(elapsedMs))
        }
        state.isRunning -> stringResource(
            R.string.cross_sync_status_running,
            formatDuration(elapsedMs),
            state.currentStep.ifBlank { stringResource(R.string.cross_sync_step_preparing) }
        )
        state.errorMessage != null -> stringResource(R.string.cross_sync_status_error_ready)
        state.startedAt != null && state.finishedAt != null -> stringResource(R.string.cross_sync_status_stopped_ready)
        else -> stringResource(R.string.cross_sync_status_last)
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
        ) {
            CrossSyncHeader(
                state = state,
                statusColor = statusColor,
                subtitle = statusSubtitle,
                compact = isLandscape && isCompactHeight,
                onDismiss = onDismiss
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(landscapeSidePaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressCard(
                            state = state,
                            accentColor = accentColor,
                            elapsedMs = elapsedMs,
                            estimatedRemainingMs = estimatedRemainingMs,
                            compact = true
                        )
                        if (looksStalled) StalledBanner()
                        if (!state.errorMessage.isNullOrBlank()) ErrorBanner(state.errorMessage, state.errorCode)
                        PlatformStatsSection(state = state, layoutAsGrid = true)
                        ActionButtons(
                            state = state,
                            hasReportContent = hasReportContent,
                            stacked = true,
                            onShare = ::shareReport,
                            onSave = ::saveReport,
                            onCancelRequest = { showCancelConfirmation = true },
                            onDismiss = onDismiss,
                            onStart = onStart
                        )
                        SafetyNote()
                        ReportLocationNote(state.reportSavedTo)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LogHeader(
                            state = state,
                            visibleCount = visibleLogs.size,
                            errorCount = errorLogCount,
                            warningCount = warningLogCount,
                            selected = logFilter,
                            onSelect = { logFilter = it }
                        )
                        LogList(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            visibleLogs = visibleLogs,
                            hasAnyLogs = sourceLogs.isNotEmpty(),
                            listState = logsListState
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressCard(
                        state = state,
                        accentColor = accentColor,
                        elapsedMs = elapsedMs,
                        estimatedRemainingMs = estimatedRemainingMs,
                        compact = false
                    )
                    if (looksStalled) StalledBanner()
                    if (!state.errorMessage.isNullOrBlank()) ErrorBanner(state.errorMessage, state.errorCode)
                    PlatformStatsSection(state = state, layoutAsGrid = false)
                    LogHeader(
                        state = state,
                        visibleCount = visibleLogs.size,
                        errorCount = errorLogCount,
                        warningCount = warningLogCount,
                        selected = logFilter,
                        onSelect = { logFilter = it }
                    )
                    LogList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        visibleLogs = visibleLogs,
                        hasAnyLogs = sourceLogs.isNotEmpty(),
                        listState = logsListState
                    )
                    ReportLocationNote(state.reportSavedTo)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    ActionButtons(
                        state = state,
                        hasReportContent = hasReportContent,
                        stacked = false,
                        onShare = ::shareReport,
                        onSave = ::saveReport,
                        onCancelRequest = { showCancelConfirmation = true },
                        onDismiss = onDismiss,
                        onStart = onStart
                    )
                    SafetyNote()
                }
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text(KitsugiStrings.get(R.string.inline_stop_sync_2ac4d77)) },
            text = {
                Text(
                    KitsugiStrings.get(R.string.inline_completed_remote_server_updates_ca_81e732b)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        onCancel()
                    }
                ) {
                    Text(stringResource(R.string.cross_sync_action_stop), color = ERROR_COLOR)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text(stringResource(R.string.devam_et))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bölümler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CrossSyncHeader(
    state: CrossSyncProgressState,
    statusColor: Color,
    subtitle: String,
    compact: Boolean,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 36.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    state.isCompleted -> Icons.CheckCircle
                    state.errorMessage != null -> Icons.ErrorOutline
                    else -> Icons.Sync
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier
                    .size(if (compact) 18.dp else 21.dp)
                    .rotate(if (state.isRunning) rotation else 0f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.cok_yonlu_esitleme),
                style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    .copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.platformStats.isNotEmpty()) {
            Text(
                text = stringResource(R.string.x_hesap_x_olay, state.platformStats.size, state.totalEventCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = stringResource(R.string.kapat),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressCard(
    state: CrossSyncProgressState,
    accentColor: Color,
    elapsedMs: Long,
    estimatedRemainingMs: Long?,
    compact: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp)) {
            Text(
                text = state.currentStep.ifBlank { stringResource(R.string.cross_sync_step_preparing) },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.currentDetail.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.currentDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

            if (state.isRunning && state.totalItems <= 0) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    progress = { if (state.isCompleted) 1f else state.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (state.isCompleted) KitsugiColors.AccentGreen else accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.totalItems > 0 || state.isCompleted) "${(state.progressPercent * 100).toInt()}%" else stringResource(R.string.cross_sync_in_progress),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
                Text(
                    text = if (state.totalItems > 0) {
                        "${state.processedItems} / ${state.totalItems} ${if (state.progressUnit.isBlank()) stringResource(R.string.cross_sync_vm_item_unit) else state.progressUnit}"
                    } else {
                        stringResource(R.string.cross_sync_network_preparation)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.isRunning) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.inline_elapsed_1_s_08b667a, formatDuration(elapsedMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (estimatedRemainingMs != null) {
                        Text(
                            text = stringResource(R.string.cross_sync_estimated_remaining, formatDuration(estimatedRemainingMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StalledBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = WARNING_COLOR.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, WARNING_COLOR.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.WarningAmber, contentDescription = null, tint = WARNING_COLOR, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.inline_progress_hasn_t_updated_for_e587968),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    code: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ERROR_COLOR.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, ERROR_COLOR.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.hata_x, message),
                style = MaterialTheme.typography.bodySmall,
                color = ERROR_COLOR,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            code?.let {
                Spacer(modifier = Modifier.height(6.dp))
                PlatformErrorCodeChip(code = it)
            }
        }
    }
}

@Composable
private fun PlatformStatsSection(
    state: CrossSyncProgressState,
    layoutAsGrid: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.inline_connected_accounts_3be75da),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val totalErrors = state.platformStats.values.sumOf { it.errorCount }
            val totalSkipped = state.platformStats.values.sumOf { it.skippedCount }
            if (state.platformStats.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.inline_1_s_skipped_2_s_3e7c5e0, totalSkipped, totalErrors),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        totalErrors > 0 -> ERROR_COLOR
                        totalSkipped > 0 -> WARNING_COLOR
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (state.platformStats.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Text(
                    stringResource(R.string.inline_checking_for_connected_accounts_wi_5b0a52f),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (layoutAsGrid) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 2
            ) {
                state.platformStats.values.forEach { stats ->
                    PlatformStatChip(
                        stats = stats,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 120.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.platformStats.values.forEach { stats ->
                    PlatformStatChip(stats = stats, modifier = Modifier.width(132.dp))
                }
            }
        }
    }
}

@Composable
private fun PlatformStatChip(
    stats: CrossPlatformStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KitsugiPlatformLogo(platformId = stats.platformName, size = 16.dp)
                Text(
                    text = stats.platformName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = stringResource(R.string.inline_1_s_entries_2_s_54c9b10, stats.initialCount, stats.addedCount, stats.updatedCount),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = if (stats.addedCount > 0 || stats.updatedCount > 0) Color(0xFF42A5F5) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    stats.skippedCount > 0 || stats.errorCount > 0 -> stringResource(R.string.inline_1_s_skipped_2_s_3e7c5e0, stats.skippedCount, stats.errorCount)
                    stats.initialCount > 0 -> stringResource(R.string.cross_sync_no_issues)
                    else -> stringResource(R.string.cross_sync_no_records)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = when {
                    stats.errorCount > 0 -> ERROR_COLOR
                    stats.skippedCount > 0 -> WARNING_COLOR
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LogHeader(
    state: CrossSyncProgressState,
    visibleCount: Int,
    errorCount: Int,
    warningCount: Int,
    selected: CrossSyncLogFilter,
    onSelect: (CrossSyncLogFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.isRunning) stringResource(R.string.cross_sync_live_log_title) else stringResource(R.string.cross_sync_log_title),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$visibleCount / ${state.totalEventCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CrossSyncLogFilter.entries.forEach { filter ->
                val label = when (filter) {
                    CrossSyncLogFilter.All -> stringResource(R.string.notif_filter_all)
                    CrossSyncLogFilter.Issues -> if (errorCount + warningCount > 0) {
                        stringResource(R.string.cross_sync_filter_issues_count, errorCount + warningCount)
                    } else stringResource(R.string.cross_sync_filter_issues)
                    CrossSyncLogFilter.Errors -> if (errorCount > 0) {
                        stringResource(R.string.cross_sync_filter_errors_count, errorCount)
                    } else stringResource(R.string.cross_sync_filter_errors)
                    CrossSyncLogFilter.Warnings -> if (warningCount > 0) {
                        stringResource(R.string.cross_sync_filter_warnings_count, warningCount)
                    } else stringResource(R.string.cross_sync_filter_warnings)
                }
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelect(filter) },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

@Composable
private fun LogList(
    modifier: Modifier,
    visibleLogs: List<CrossSyncLogEntry>,
    hasAnyLogs: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        if (visibleLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!hasAnyLogs) stringResource(R.string.cross_sync_empty_log) else stringResource(R.string.cross_sync_empty_filter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uniqueItems(visibleLogs, key = { it.id }) { log ->
                    LogItemRow(log = log)
                }
            }
        }
    }
}

@Composable
private fun ReportLocationNote(path: String?) {
    if (path == null) return
    Text(
        text = "Otomatik rapor: $path",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Güvenlik notu + yasal uyarı.
 *
 * ÖNEMLİ: Bu blok eskiden tek bir [Row] içinde üç kardeş öğe olarak çiziliyordu; uzun yasal
 * metin (`CrossSyncDisclaimerText`) satır içinde tam genişlik istediği için diğer öğeleri
 * eziyor ve içerik diyaloğun dışına taşıyordu. Artık tek bir kart içinde DİKEY olarak
 * istiflenir; metin kısaltılmaz, kaydırma alanının parçası olur.
 */
@Composable
private fun SafetyNote() {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Security,
                    contentDescription = null,
                    tint = KitsugiColors.AccentGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.veri_silinmez_yalnizca_guvenli_eslesmeler_eklenir_guncellenir) +
                        stringResource(R.string.raporda_erisim_anahtarlari_gizlenir),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) stringResource(R.string.settings_close) else stringResource(R.string.uyari),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = LocalKitsugiAccent.current
                )
                KitsugiChevronIcon(
                    expanded = expanded,
                    contentDescription = null,
                    tint = LocalKitsugiAccent.current,
                    modifier = Modifier.size(16.dp)
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    CrossSyncDisclaimerText(
                        full = true,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ActionButtons(
    state: CrossSyncProgressState,
    hasReportContent: Boolean,
    stacked: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCancelRequest: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val buttonHeight = 40.dp
    if (state.isRunning) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(stringResource(R.string.inline_partial_report_b0be79e), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onCancelRequest,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(stringResource(R.string.cross_sync_action_stop), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.arka_planda_devam_et), fontSize = 12.sp)
        }
    } else {
        if (stacked) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp),
                enabled = hasReportContent
            ) {
                Icon(Icons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(stringResource(R.string.dosyaya_kaydet), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp),
                enabled = hasReportContent
            ) {
                Icon(Icons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(stringResource(R.string.inline_share_report_a47e5b0), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp),
                    enabled = hasReportContent
                ) {
                    Icon(Icons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.kaydet), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp),
                    enabled = hasReportContent
                ) {
                    Icon(Icons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.paylas), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.kapat), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
            }
        }
        if (state.startedAt != null) {
            TextButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(stringResource(R.string.inline_start_new_sync_22f8bdc), fontSize = 12.sp)
            }
        } else if (stacked) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.kapat), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LogItemRow(log: CrossSyncLogEntry) {
    var expanded by remember(log.id) { mutableStateOf(false) }
    // Kaynağa özgü hata kodu (varsa) — açıklaması dil dosyasından okunur.
    val errorCode = remember(log.errorCode) { PlatformErrorCode.fromCode(log.errorCode) }
    val tagColor = when {
        log.isError -> ERROR_COLOR
        log.isWarning -> WARNING_COLOR
        log.isAddition -> KitsugiColors.AccentGreen
        log.isUpdate -> Color(0xFF42A5F5)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        log.isError -> Icons.ErrorOutline
        log.isWarning -> Icons.WarningAmber
        log.isAddition -> Icons.AddCircleOutline
        log.isUpdate -> Icons.Edit
        else -> null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        color = when {
            log.isError -> ERROR_COLOR.copy(alpha = 0.07f)
            log.isWarning -> WARNING_COLOR.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = log.platform,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = tagColor,
                    maxLines = 1,
                    modifier = Modifier.width(70.dp),
                    overflow = TextOverflow.Ellipsis
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (log.isError || log.isWarning) tagColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) 8 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (errorCode != null) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tagColor.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = errorCode.code,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = tagColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = timeOfDay(log.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && errorCode != null) {
                Spacer(Modifier.height(6.dp))
                PlatformErrorExplanationBlock(code = errorCode, tint = tagColor)
            }
            if (expanded && !log.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 24,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded && log.details.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.inline_no_additional_technical_details_ar_fa4739f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bir liste kaynağı hatasının "neden çıktı / ne yapmalı" açıklamasını gösterir.
 *
 * Metinler `values/strings.xml` (TR) ve `values-en/strings.xml` (EN) içinden,
 * cihazın aktif diline göre okunur — bu yüzden ek çeviri katmanı gerekmez.
 */
@Composable
internal fun PlatformErrorExplanationBlock(
    code: PlatformErrorCode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = code.code,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = tint
            )
            Text(
                text = stringResource(code.platform.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(code.severity.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = tint
            )
        }
        Text(
            text = stringResource(code.titleRes),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        PlatformErrorExplanationLine(
            label = stringResource(R.string.pf_err_why_label),
            value = stringResource(code.whyRes)
        )
        PlatformErrorExplanationLine(
            label = stringResource(R.string.pf_err_fix_label),
            value = stringResource(code.fixRes)
        )
        Text(
            text = stringResource(
                if (code.retryable) R.string.pf_err_retryable_yes else R.string.pf_err_retryable_no
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlatformErrorExplanationLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun timeOfDay(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private const val STALLED_WARNING_AFTER_MS = 90_000L
private val ERROR_COLOR = Color(0xFFEF5350)
private val WARNING_COLOR = Color(0xFFFFA726)
