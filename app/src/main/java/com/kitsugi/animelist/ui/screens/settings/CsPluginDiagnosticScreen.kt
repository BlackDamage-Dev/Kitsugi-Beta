package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import com.kitsugi.animelist.data.cloudstream.diag.CsTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.cloudstream.CsPluginDiagnosticRunner
import com.kitsugi.animelist.data.cloudstream.CsPluginDiagnosticViewModel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import java.io.File

/**
 * In-App CS Plugin Tanı Ekranı.
 *
 * Eklentileri uygulamanın mevcut ağ/WebView-cookie işleyişiyle E2E test eder; CF/WAF
 * cooldown'larını zorla kapatmaz ve yalnızca açıkça saptanan koruma imzalarını sayar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsPluginDiagnosticScreen(
    onDismiss: () -> Unit,
    embeddedMode: Boolean = false,
    onOpenErrorCatalog: (() -> Unit)? = null,
    vm: CsPluginDiagnosticViewModel = viewModel()
) {
    val context    = LocalContext.current
    val accent     = LocalKitsugiAccent.current
    val progress   by vm.progress.collectAsState()
    val results    by vm.results.collectAsState()
    val isRunning  by vm.isRunning.collectAsState()
    val reportPath by vm.reportPath.collectAsState()
    val scope      = rememberCoroutineScope()

    // Shared classifier keeps the on-screen and exported CF/WAF counts consistent.
    val summary = CsPluginDiagnosticRunner.summarize(results)

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(if (embeddedMode) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp))
                .background(KitsugiColors.Surface)
        ) {
            // ── Header ──────────────────────────────────────────────────
            if (!embeddedMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.BugReport,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.inline_plugin_diagnostics_mode_bea2832),
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val subtitleText = if (vm.onlyInstalled) {
                            stringResource(R.string.cs_diag_header_installed_subtitle)
                        } else {
                            stringResource(R.string.cs_diag_header_all_subtitle, CsPluginDiagnosticRunner.REPOS.size)
                        }
                        Text(
                            subtitleText,
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (!isRunning) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Close, contentDescription = stringResource(R.string.action_close), tint = KitsugiColors.TextSecondary)
                        }
                    }
                }
            }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Toggle Option ────────────────────────────────────────
                    if (!isRunning) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KitsugiColors.SurfaceSoft)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.inline_installed_extensions_only_8ee1192),
                                        color = KitsugiColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        stringResource(R.string.inline_quickly_test_local_extensions_with_22c6bd0),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = vm.onlyInstalled,
                                    onCheckedChange = { vm.onlyInstalled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = KitsugiColors.Surface,
                                        checkedTrackColor = accent
                                    )
                                )
                            }
                        }
                    }

                    // ── Progress card ────────────────────────────────────────
                    item {
                        AnimatedVisibility(visible = isRunning || progress != null) {
                            ProgressCard(progress = progress, isRunning = isRunning, accent = accent)
                        }
                    }

                    // ── Summary cards ────────────────────────────────────────
                    if (results.isNotEmpty()) {
                        item {
                            val hostAbiMissing by vm.hostAbiMissing.collectAsState()
                            if (hostAbiMissing.isNotEmpty()) {
                                HostAbiWarning(hostAbiMissing)
                            }
                            SummaryCards(summary)
                        }
                    }

                    // ── Info banner (başlamadan önce) ────────────────────────
                    if (!isRunning && results.isEmpty() && progress == null) {
                        item {
                            InfoBanner(accent, vm.onlyInstalled)
                        }
                    }

                    // ── Results list ─────────────────────────────────────────
                    if (results.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.inline_detailed_results_1_s_extensions_22f36a9, (results.size).toString()),
                                color = KitsugiColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        items(results, key = { it.pluginId + it.repoSlug }) { result ->
                            ResultRow(result = result, accent = accent)
                        }
                    }
                }

                // ── Bottom buttons ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isRunning) {
                        KitsugiButton(
                            onClick = { vm.startDiagnostic(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (results.isEmpty()) R.string.cs_diag_start else R.string.cs_diag_restart), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { vm.cancelDiagnostic() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.AccentRed)
                        ) {
                            Icon(Icons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cs_diag_stop), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Canlı izleme raporu (uygulama aramalarında otomatik kaydedilen hata izi)
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    runCatching { CsTrace.writeReport(context) }.getOrNull()
                                }
                                if (file != null && file.exists()) {
                                    shareCsReportFile(context, file)
                                } else {
                                    android.widget.Toast.makeText(
                                        context, context.getString(R.string.cs_diag_report_creation_failed), android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                    ) {
                        Icon(Icons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.inline_share_live_monitoring_report_md_ff2cf01), fontWeight = FontWeight.Bold)
                    }
                    if (onOpenErrorCatalog != null) {
                        OutlinedButton(
                            onClick = onOpenErrorCatalog,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        ) {
                            Icon(Icons.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.platform_error_catalog_open), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Raporu paylaş butonu
                    if (reportPath != null && !isRunning) {
                        OutlinedButton(
                            onClick = {
                                val file = File(reportPath!!)
                                if (file.exists()) {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.cs_diag_share_report)))
                                    } catch (e: Exception) {
                                        android.util.Log.e("CsPluginDiagnostic", context.getString(R.string.cs_diag_report_share_failed, e.message.orEmpty()))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                        ) {
                            Icon(Icons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.inline_share_report_md_79b230f), fontWeight = FontWeight.Bold)
                        }
                }
            }
        }
    }

    if (embeddedMode) {
        content()
    } else {
        Dialog(
            onDismissRequest = {
                if (!isRunning) onDismiss()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isRunning
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                content()
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ProgressCard(
    progress: CsPluginDiagnosticRunner.DiagnosticProgress?,
    isRunning: Boolean,
    accent: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = pulseAlpha))
                )
            }
            Text(
                stringResource(if (isRunning) R.string.cs_diag_progress_running else R.string.cs_diag_progress_completed),
                color = if (isRunning) accent else KitsugiColors.AccentGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (progress != null) {
            // Plugin adı
            KitsugiEmojiLabel(
                label = "${progress.currentPlugin} — ${progress.phase}",
                icon = Icons.Play,
                iconSize = 11.dp,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f)
            )

            // Sayaç
            Text(
                stringResource(R.string.cs_diag_plugins_progress_count, progress.current, progress.total),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Uygulama tarafında eksik eklenti-runtime sınıflarını gösterir.
 *
 * Bu durumda düşen eklentiler "bozuk" DEĞİLDİR: hata uygulama paketlemesinden (R8/küçültme)
 * kaynaklanır ve tüm eklentileri aynı anda etkiler. Kullanıcının yanlış teşhis koymasını
 * engellemek için sayaçların ÜSTÜNDE gösterilir.
 */
@Composable
private fun HostAbiWarning(missing: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KitsugiEmojiLabel(
            label = "Uygulama tarafı eksik (Host ABI)",
            icon = Icons.TriangleAlert,
            iconSize = 13.dp,
            color = KitsugiColors.AccentRed,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Şu sınıflar uygulamada bulunamadı: ${missing.joinToString(", ")}. " +
                "Bu sınıfları kullanan eklentiler \"bozuk\" değildir; hata uygulama paketlemesinden kaynaklanır.",
            color = KitsugiColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SummaryCards(summary: CsPluginDiagnosticRunner.DiagnosticSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryChip(Icons.CircleCheck, summary.working.toString(), stringResource(R.string.cs_diag_summary_working), KitsugiColors.AccentGreen, Modifier.weight(1f))
            SummaryChip(Icons.TriangleAlert, summary.noStreams.toString(), stringResource(R.string.cs_diag_summary_no_streams), KitsugiColors.AccentOrange, Modifier.weight(1f))
            SummaryChip(Icons.Lock, summary.cfBlocked.toString(), stringResource(R.string.cs_diag_summary_cf_blocked), KitsugiColors.AccentBlue, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryChip(Icons.Search, summary.searchEmpty.toString(), stringResource(R.string.cs_diag_summary_search_empty), KitsugiColors.TextMuted, Modifier.weight(1f))
            SummaryChip(Icons.TriangleAlert, summary.loadFailed.toString(), stringResource(R.string.cs_diag_summary_load_failed), KitsugiColors.AccentOrange, Modifier.weight(1f))
            SummaryChip(Icons.CircleX, summary.dead.toString(), stringResource(R.string.cs_diag_summary_broken), KitsugiColors.AccentRed, Modifier.weight(1f))
        }
        if (summary.runtimeIncompatible > 0 || summary.authRequired > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryChip(
                    Icons.Puzzle,
                    summary.runtimeIncompatible.toString(),
                    stringResource(R.string.cs_diag_summary_runtime_incompatible),
                    KitsugiColors.AccentRed,
                    Modifier.weight(1f),
                )
                SummaryChip(
                    Icons.Key,
                    summary.authRequired.toString(),
                    stringResource(R.string.cs_diag_summary_auth_required),
                    KitsugiColors.AccentBlue,
                    Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }
        }
        if (summary.errorCodeCounts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.SurfaceSoft)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.platform_error_code_summary),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                summary.errorCodeCounts.toList().forEach { (code, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlatformErrorCodeHint(code = code, compact = false)
                        Text(
                            text = "×$count",
                            color = KitsugiColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    count: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(count, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = KitsugiColors.TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun InfoBanner(accent: Color, onlyInstalled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.inline_in_app_plugin_diagnostics_152efe2), color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            stringResource(R.string.cs_diag_info_description),
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        val bulletPoints = if (onlyInstalled) {
            stringResource(R.string.cs_diag_info_installed_bullets)
        } else {
            stringResource(R.string.cs_diag_info_all_bullets, CsPluginDiagnosticRunner.REPOS.size)
        }
        Text(
            bulletPoints,
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ResultRow(
    result: CsPluginDiagnosticRunner.DiagnosticResult,
    accent: Color
) {
    val (statusIcon, statusColor) = when (result.status) {
        CsPluginDiagnosticRunner.ResultStatus.WORKING      -> Icons.CircleCheck to KitsugiColors.AccentGreen
        CsPluginDiagnosticRunner.ResultStatus.NO_STREAMS   -> Icons.TriangleAlert to KitsugiColors.AccentOrange
        CsPluginDiagnosticRunner.ResultStatus.SEARCH_EMPTY -> Icons.Search to KitsugiColors.TextMuted
        CsPluginDiagnosticRunner.ResultStatus.CF_BLOCKED   -> Icons.Lock to KitsugiColors.AccentBlue
        CsPluginDiagnosticRunner.ResultStatus.LOAD_FAILED  -> Icons.TriangleAlert to KitsugiColors.AccentOrange
        CsPluginDiagnosticRunner.ResultStatus.DEAD         -> Icons.CircleX to KitsugiColors.AccentRed
        CsPluginDiagnosticRunner.ResultStatus.RUNTIME_INCOMPATIBLE -> Icons.Puzzle to KitsugiColors.AccentRed
        CsPluginDiagnosticRunner.ResultStatus.AUTH_REQUIRED -> Icons.Key to KitsugiColors.AccentBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.displayName,
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val repoLabel = if (result.repoSlug == "local") stringResource(R.string.cs_diag_local_plugins) else result.repoSlug.substringBefore("/")
            Text(
                repoLabel,
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp
            )
            result.errorCode?.let { code -> PlatformErrorCodeHint(code = code) }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (result.streamCount > 0) {
                Text(
                    stringResource(R.string.cs_diag_stream_count, result.streamCount),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (result.status == CsPluginDiagnosticRunner.ResultStatus.SEARCH_EMPTY) {
                Text(
                    stringResource(R.string.cs_diag_result_search_empty),
                    color = statusColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (result.error != null) {
                Text(
                    result.error.take(20),
                    color = statusColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            KitsugiEmojiLabel(
                label = "${result.searchCount}",
                icon = Icons.Search,
                iconSize = 9.dp,
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            if (result.flowTested) {
                KitsugiEmojiLabel(
                    label = if (result.flowOk) "${result.flowStreams}" else "",
                    icon = if (result.flowOk) Icons.RefreshCw else Icons.CircleX,
                    iconSize = 9.dp,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


/** Canlı izleme raporu dosyasını Android paylaşım penceresiyle paylaşır. */
private fun shareCsReportFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.cs_diag_share_report)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.util.Log.e("CsPluginDiagnostic", context.getString(R.string.cs_diag_live_report_share_failed, e.message.orEmpty()))
    }
}
