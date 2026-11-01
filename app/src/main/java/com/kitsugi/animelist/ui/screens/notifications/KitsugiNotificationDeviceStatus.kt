package com.kitsugi.animelist.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.work.WorkManager
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.notifications.KitsugiNotificationDispatcher
import com.kitsugi.animelist.core.notifications.NotificationDeliveryTracker
import com.kitsugi.animelist.core.notifications.NotificationScheduler
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Teşhis panelinin **cihaz** bölümü: "ağ çalışıyor" ile "telefonuma bildirim düştü"
 * arasındaki farkı gösterir.
 *
 * Gösterilenler:
 *   • İzin / cihaz ayarı durumu (Android 13+ POST_NOTIFICATIONS dâhil),
 *   • Arka plan denetiminin planlı olup olmadığı ve aralığı,
 *   • Son çalışma zamanı ve o çalıştırmada kaynak başına **gönderilen** bildirim sayısı,
 *   • Uçtan uca kanıt için "Test bildirimi gönder" düğmesi.
 */
@Composable
internal fun NotifDeviceDeliverySection(
    sources: List<String>,
    accentColor: Color,
    notificationIntervalMinutes: Int
) {
    val context = LocalContext.current
    var gate by remember { mutableStateOf(KitsugiNotificationDispatcher.gate(context)) }
    var snapshot by remember {
        mutableStateOf(NotificationDeliveryTracker.snapshot(context, sources))
    }
    var workScheduled by remember { mutableStateOf<Boolean?>(null) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        gate = KitsugiNotificationDispatcher.gate(context)
        snapshot = NotificationDeliveryTracker.snapshot(context, sources)
        workScheduled = withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME)
                    .get()
                    .any { !it.state.isFinished }
            }.getOrNull()
        }
    }

    // Test bildiriminin sonucu kısa süre gösterilir.
    LaunchedEffect(testResult) {
        if (testResult == null) return@LaunchedEffect
        delay(4_000)
        testResult = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.notif_device_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(R.string.notif_device_subtitle),
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))

        // 1) İzin / cihaz ayarı
        when (gate) {
            KitsugiNotificationDispatcher.Gate.OK -> DeviceStatusRow(
                icon = { Icon(Icons.Rounded.CheckCircle, null, tint = accentColor, modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_permission_ok),
                color = KitsugiColors.TextSecondary
            )
            KitsugiNotificationDispatcher.Gate.PERMISSION -> DeviceStatusRow(
                icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_permission_missing),
                color = Color(0xFFFF8A8A)
            )
            KitsugiNotificationDispatcher.Gate.DISABLED -> DeviceStatusRow(
                icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_disabled),
                color = Color(0xFFFF8A8A)
            )
        }

        // 2) Arka plan denetimi
        when (workScheduled) {
            true -> DeviceStatusRow(
                icon = { Icon(Icons.Rounded.Schedule, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_work_scheduled, notificationIntervalMinutes),
                color = KitsugiColors.TextSecondary
            )
            false -> DeviceStatusRow(
                icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFFB86B), modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_work_missing),
                color = Color(0xFFFFC98A)
            )
            null -> Unit
        }

        // 3) Son çalışma
        if (snapshot.lastRunAtMs > 0L) {
            DeviceStatusRow(
                icon = { Icon(Icons.Rounded.Schedule, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(14.dp)) },
                text = stringResource(
                    R.string.notif_device_last_run,
                    formatNotifTime(snapshot.lastRunAtMs)
                ),
                color = KitsugiColors.TextSecondary
            )
        } else {
            DeviceStatusRow(
                icon = { Icon(Icons.Rounded.Schedule, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_never_ran),
                color = KitsugiColors.TextMuted
            )
        }

        if (snapshot.totalPosted > 0) {
            DeviceStatusRow(
                icon = { Icon(Icons.Rounded.CheckCircle, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(14.dp)) },
                text = stringResource(R.string.notif_device_total, snapshot.totalPosted),
                color = KitsugiColors.TextMuted
            )
        }

        Spacer(Modifier.height(8.dp))

        // 4) Kaynak bazında son teslim
        snapshot.sources.forEach { row ->
            val line = when {
                row.lastError != null -> stringResource(
                    R.string.notif_device_source_error,
                    row.source,
                    row.lastError
                )
                row.lastRunAtMs <= 0L -> stringResource(R.string.notif_device_source_never, row.source)
                else -> stringResource(R.string.notif_device_source_row, row.source, row.lastPosted)
            }
            Text(
                text = "• $line",
                color = if (row.lastError != null) Color(0xFFFFB4B4) else KitsugiColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceActionButton(
                label = stringResource(R.string.notif_device_test),
                accentColor = accentColor,
                onClick = {
                    val ok = KitsugiNotificationDispatcher.postTest(context)
                    testResult = ok
                    tick++
                }
            )
            if (gate != KitsugiNotificationDispatcher.Gate.OK) {
                DeviceActionButton(
                    label = stringResource(R.string.notif_device_grant_action),
                    accentColor = accentColor,
                    onClick = { KitsugiNotificationDispatcher.openSystemSettings(context) }
                )
            }
        }

        testResult?.let { ok ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (ok) {
                    stringResource(R.string.notif_device_permission_ok)
                } else {
                    stringResource(R.string.notif_device_test_blocked)
                },
                color = if (ok) accentColor else Color(0xFFFF8A8A),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DeviceStatusRow(
    icon: @Composable () -> Unit,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun DeviceActionButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatNotifTime(epochMs: Long): String =
    runCatching {
        SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(epochMs))
    }.getOrDefault("—")
