package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.storage.DownloadStorage
import com.kitsugi.animelist.core.storage.StorageJanitor
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ayarlar > Depolama ekranı.
 *
 * Kullanıcının uygulamanın diskte kapladığı yeri **kalem kalem** görmesini ve güvenle yer
 * açmasını sağlar. Öncesinde yalnızca P2P önbelleği için bir temizleme düğmesi vardı; görsel,
 * manga, önizleme, altyazı, teşhis ve veritabanı önbellekleri tamamen görünmezdi ve
 * kullanıcı yer açmak için uygulama verilerini tümden silmek (hesap/mylist kaybı) zorundaydı.
 *
 * Bu ekran **hiçbir kullanıcı verisini** silmez: liste (mylist), hesap bilgileri, izleme geçmişi
 * ve indirilen videolar kapsam dışıdır (bkz. [StorageJanitor]).
 */
@Composable
fun StorageSettingsContent() {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()

    var usages by remember { mutableStateOf<List<StorageJanitor.BucketUsage>?>(null) }
    var totalBytes by remember { mutableStateOf(0L) }
    var busyBucket by remember { mutableStateOf<StorageJanitor.Bucket?>(null) }
    var busyAll by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showWebViewConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // İndirilenler (kullanıcı dosyaları): video / görsel / altyazı
    var downloadUsages by remember { mutableStateOf<List<DownloadStorage.Usage>?>(null) }
    var downloadsTotal by remember { mutableStateOf(0L) }
    var busyDownloadKind by remember { mutableStateOf<DownloadStorage.Kind?>(null) }
    var busyAllDownloads by remember { mutableStateOf(false) }
    var confirmDownloadKind by remember { mutableStateOf<DownloadStorage.Kind?>(null) }
    var showDeleteAllDownloadsConfirm by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val list = StorageJanitor.scan(context)
        usages = list
        totalBytes = list.sumOf { it.bytes }

        // Kullanıcı dosyaları ayrı ölçülür: üstteki "Toplam kullanım" bilinçli olarak
        // yalnızca yeniden üretilebilir önbellekleri kapsar (bkz. storage_protected_note).
        downloadUsages = runCatching { DownloadStorage.scan(context) }.getOrNull()
            ?: DownloadStorage.Kind.entries.map { DownloadStorage.Usage(it, 0L, 0) }
        downloadsTotal = runCatching { DownloadStorage.totalBytes(context) }.getOrDefault(0L)
    }

    LaunchedEffect(Unit) { refresh() }

    // Kısa süreli durum mesajı ("X geri kazanıldı") kendiliğinden kaybolsun.
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3500)
            statusMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Genel Bakış ────────────────────────────────────────────────────────
        SettingsNavCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.storage_total),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (usages == null) stringResource(R.string.storage_calculating)
                        else formatBytes(totalBytes),
                        color = accentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.storage_protected_note),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (statusMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = statusMessage!!,
                        color = KitsugiColors.AccentGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Kalemler ───────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.storage_section_buckets))
        SettingsNavCard {
            val list = usages
            if (list == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp)
                }
            } else {
                list.forEachIndexed { index, usage ->
                    if (index > 0) KitsugiSettingsDivider()
                    StorageBucketRow(
                        usage = usage,
                        busy = busyBucket == usage.bucket,
                        enabled = !busyAll,
                        accentColor = accentColor,
                        onClear = {
                            if (usage.bucket == StorageJanitor.Bucket.WEBVIEW_DATA) {
                                showWebViewConfirm = true
                            } else {
                                busyBucket = usage.bucket
                                scope.launch {
                                    val result = StorageJanitor.clean(context, usage.bucket)
                                    refresh()
                                    busyBucket = null
                                    statusMessage = result.message
                                        ?: if (result.freedBytes > 0L)
                                            context.getString(
                                                R.string.storage_cleared_amount,
                                                formatBytes(result.freedBytes)
                                            )
                                        else context.getString(R.string.storage_cleared_nothing)
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── İndirilenler (kullanıcı dosyaları) ─────────────────────────────────
        // Önbelleklerden ayrı tutulur: buradaki dosyalar kullanıcının kendi içeriğidir,
        // yalnızca açık onayla silinir ve "Güvenli önbellekleri temizle" bunlara dokunmaz.
        SectionHeader(stringResource(R.string.storage_section_downloads))
        SettingsNavCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = stringResource(R.string.storage_downloads_total, formatBytes(downloadsTotal)),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.storage_downloads_hint),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SettingsNavCard {
            val downloads = downloadUsages
            if (downloads == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp)
                }
            } else {
                downloads.forEachIndexed { index, usage ->
                    if (index > 0) KitsugiSettingsDivider()
                    DownloadRow(
                        usage = usage,
                        busy = busyDownloadKind == usage.kind || busyAllDownloads,
                        enabled = !busyAll && !busyAllDownloads && busyDownloadKind == null,
                        accentColor = KitsugiColors.AccentRed,
                        onClear = { confirmDownloadKind = usage.kind }
                    )
                }

                KitsugiSettingsDivider()

                DeleteAllDownloadsRow(
                    busy = busyAllDownloads,
                    enabled = !busyAll && busyDownloadKind == null && !busyAllDownloads,
                    onClear = { showDeleteAllDownloadsConfirm = true }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Toplu temizleme ────────────────────────────────────────────────────
        SettingsNavCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvClickable(shape = RoundedCornerShape(24.dp)) {
                        if (!busyAll) showClearAllConfirm = true
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.CleaningServices,
                        contentDescription = null,
                        tint = accentColor
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = if (busyAll) stringResource(R.string.storage_clearing)
                    else stringResource(R.string.storage_clear_all),
                    color = if (busyAll) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Onay diyalogları ───────────────────────────────────────────────────────
    if (showClearAllConfirm) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.storage_clear_all_confirm_title),
            message = stringResource(R.string.storage_clear_all_confirm_message),
            confirmText = stringResource(R.string.storage_confirm_clear),
            onConfirm = {
                showClearAllConfirm = false
                busyAll = true
                scope.launch {
                    val freed = StorageJanitor.cleanAll(context)
                    refresh()
                    busyAll = false
                    statusMessage = if (freed > 0L) {
                        context.getString(R.string.storage_cleared_amount, formatBytes(freed))
                    } else {
                        context.getString(R.string.storage_cleared_nothing)
                    }
                }
            },
            onDismiss = { showClearAllConfirm = false }
        )
    }

    if (showWebViewConfirm) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.storage_clear_webview_confirm_title),
            message = stringResource(R.string.storage_clear_webview_confirm_message),
            confirmText = stringResource(R.string.storage_confirm_clear),
            isDestructive = true,
            onConfirm = {
                showWebViewConfirm = false
                busyBucket = StorageJanitor.Bucket.WEBVIEW_DATA
                scope.launch {
                    val result = StorageJanitor.clean(context, StorageJanitor.Bucket.WEBVIEW_DATA)
                    refresh()
                    busyBucket = null
                    statusMessage = if (result.freedBytes > 0L) {
                        context.getString(R.string.storage_cleared_amount, formatBytes(result.freedBytes))
                    } else {
                        context.getString(R.string.storage_cleared_nothing)
                    }
                }
            },
            onDismiss = { showWebViewConfirm = false }
        )
    }

    // ── İndirilenler: onay diyalogları ─────────────────────────────────────────
    // İndirilen dosyalar yeniden üretilemez (tekrar indirilmesi gerekir), bu yüzden
    // önbelleklerden farklı olarak her silme işlemi ayrıca onay ister.
    confirmDownloadKind?.let { kind ->
        KitsugiConfirmDialog(
            title = stringResource(
                R.string.storage_downloads_delete_confirm_title,
                stringResource(kind.labelRes)
            ),
            message = stringResource(downloadConfirmMessageRes(kind)),
            confirmText = stringResource(R.string.storage_downloads_delete_all),
            isDestructive = true,
            onConfirm = {
                confirmDownloadKind = null
                busyDownloadKind = kind
                scope.launch {
                    val result = DownloadStorage.clean(context, kind)
                    refresh()
                    busyDownloadKind = null
                    statusMessage = if (result.freedBytes > 0L) {
                        context.getString(R.string.storage_deleted_amount, formatBytes(result.freedBytes))
                    } else {
                        context.getString(R.string.storage_deleted_nothing)
                    }
                }
            },
            onDismiss = { confirmDownloadKind = null }
        )
    }

    if (showDeleteAllDownloadsConfirm) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.storage_downloads_all_confirm_title),
            message = stringResource(R.string.storage_downloads_all_confirm_message),
            confirmText = stringResource(R.string.storage_downloads_delete_all_short),
            isDestructive = true,
            onConfirm = {
                showDeleteAllDownloadsConfirm = false
                busyAllDownloads = true
                scope.launch {
                    val result = DownloadStorage.cleanAll(context)
                    refresh()
                    busyAllDownloads = false
                    statusMessage = if (result.freedBytes > 0L) {
                        context.getString(R.string.storage_deleted_amount, formatBytes(result.freedBytes))
                    } else {
                        context.getString(R.string.storage_deleted_nothing)
                    }
                }
            },
            onDismiss = { showDeleteAllDownloadsConfirm = false }
        )
    }
}

// ─── Satır ─────────────────────────────────────────────────────────────────────

@Composable
private fun StorageBucketRow(
    usage: StorageJanitor.BucketUsage,
    busy: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClear: () -> Unit
) {
    val bucket = usage.bucket
    val clickable = bucket.clearable && enabled && !busy

    // Composable modifier fabrikası koşullu olarak kurulur (tvClickable = composed {}).
    val rowModifier = if (clickable) {
        Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClear)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColorFor(bucket).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = iconFor(bucket),
                contentDescription = null,
                tint = iconColorFor(bucket),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(bucket.labelRes),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(bucket.descriptionRes),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            if (busy) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = formatBytes(usage.bytes),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (usage.fileCount > 0) {
                    Text(
                        text = stringResource(R.string.storage_files_count, usage.fileCount),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (bucket.clearable) stringResource(R.string.storage_clear)
                else stringResource(R.string.storage_info_only),
                color = if (bucket.clearable) accentColor else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── İndirilenler satırları ────────────────────────────────────────────────────

/**
 * Kullanıcının indirdiği dosyalar için satır. Önbellek satırlarından farkı: her dokunuş
 * yıkıcı bir onay penceresi açar ve eylem metni "Tümünü sil"dir.
 */
@Composable
private fun DownloadRow(
    usage: DownloadStorage.Usage,
    busy: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClear: () -> Unit
) {
    val kind = usage.kind
    val clickable = enabled && !busy

    val rowModifier = if (clickable) {
        Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClear)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = downloadIconFor(kind),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(kind.labelRes),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(kind.descriptionRes),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            if (busy) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = formatBytes(usage.bytes),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (usage.fileCount > 0) {
                    Text(
                        text = stringResource(R.string.storage_files_count, usage.fileCount),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.storage_downloads_delete_all),
                color = if (usage.bytes > 0L || usage.fileCount > 0) accentColor else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** "Tüm indirilenleri sil" satırı — videolar, görseller ve altyazılar birlikte. */
@Composable
private fun DeleteAllDownloadsRow(
    busy: Boolean,
    enabled: Boolean,
    onClear: () -> Unit
) {
    val accentColor = KitsugiColors.AccentRed
    val clickable = enabled && !busy

    val rowModifier = if (clickable) {
        Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClear)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.DeleteSweep,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.storage_downloads_delete_all_short),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.storage_downloads_delete_all_desc),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (busy) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun downloadIconFor(kind: DownloadStorage.Kind): ImageVector = when (kind) {
    DownloadStorage.Kind.VIDEOS -> Icons.VideoLibrary
    DownloadStorage.Kind.AUDIOS -> Icons.AudioLines
    DownloadStorage.Kind.IMAGES -> Icons.PhotoLibrary
    DownloadStorage.Kind.SUBTITLES -> Icons.Subtitles
}

private fun downloadConfirmMessageRes(kind: DownloadStorage.Kind): Int = when (kind) {
    DownloadStorage.Kind.VIDEOS -> R.string.storage_downloads_videos_confirm_message
    DownloadStorage.Kind.AUDIOS -> R.string.storage_downloads_audios_confirm_message
    DownloadStorage.Kind.IMAGES -> R.string.storage_downloads_images_confirm_message
    DownloadStorage.Kind.SUBTITLES -> R.string.storage_downloads_subtitles_confirm_message
}

private fun iconFor(bucket: StorageJanitor.Bucket): ImageVector = when (bucket) {
    StorageJanitor.Bucket.IMAGE_CACHE -> Icons.Image
    StorageJanitor.Bucket.P2P_CACHE -> Icons.CloudDownload
    StorageJanitor.Bucket.MANGA_CACHE -> Icons.MenuBook
    StorageJanitor.Bucket.PREVIEWS -> Icons.Movie
    StorageJanitor.Bucket.SUBTITLE_CACHE -> Icons.Movie
    StorageJanitor.Bucket.DATABASE -> Icons.Storage
    StorageJanitor.Bucket.WIKI_CACHE -> Icons.AutoStories
    StorageJanitor.Bucket.DIAGNOSTICS -> Icons.BugReport
    StorageJanitor.Bucket.WEBVIEW_DATA -> Icons.Public
    StorageJanitor.Bucket.EXTENSIONS -> Icons.Extension
}

private fun iconColorFor(bucket: StorageJanitor.Bucket): Color = when (bucket) {
    StorageJanitor.Bucket.IMAGE_CACHE -> KitsugiColors.AccentBlue
    StorageJanitor.Bucket.P2P_CACHE -> KitsugiColors.AccentPurple
    StorageJanitor.Bucket.MANGA_CACHE -> KitsugiColors.AccentOrange
    StorageJanitor.Bucket.PREVIEWS -> KitsugiColors.AccentTeal
    StorageJanitor.Bucket.SUBTITLE_CACHE -> KitsugiColors.AccentYellow
    StorageJanitor.Bucket.DATABASE -> KitsugiColors.AccentIndigo
    StorageJanitor.Bucket.WIKI_CACHE -> KitsugiColors.AccentSky
    StorageJanitor.Bucket.DIAGNOSTICS -> KitsugiColors.AccentRed
    StorageJanitor.Bucket.WEBVIEW_DATA -> KitsugiColors.AccentGreen
    StorageJanitor.Bucket.EXTENSIONS -> KitsugiColors.AccentPink
}

/** 1,2 MB / 340 KB gibi okunabilir boyut metni. */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "${bytes} ${units[0]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}
