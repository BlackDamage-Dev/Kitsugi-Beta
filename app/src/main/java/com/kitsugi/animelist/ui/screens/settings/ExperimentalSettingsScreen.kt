package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsSwitchItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.background
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * DENEYSEL ÖZELLİKLER SAYFASI
 *
 * ## Neden ayrı bir sayfa?
 * Otomatik liste takibi kullanıcı hesabındaki **gerçek listeleri** değiştirir (durum,
 * ilerleme, puan). Böyle bir yetenek "oynatıcı ayarlarının arasında bir anahtar" olarak
 * durmamalı:
 *  - varsayılan olarak **KAPALI**,
 *  - açılırken **riskleri açıkça söyleyen bir onay penceresi**,
 *  - kapalıyken tek bir yazım bile yapılmaması (savunma katmanı controller'da).
 *
 * ## Açma/kapama akışı
 * `AÇIK` yönünde: onay diyaloğu → kabul → yaz. `KAPALI` yönünde: doğrudan yaz (kapatmak
 * her zaman güvenli olmalı; kullanıcıyı ikinci bir pencereyle yormak yanlış olurdu).
 *
 * ## Alt anahtarlar
 * Özellik kapalıyken alt anahtarlar **gösterilmez** ve pasif kalır: kullanıcı "kapalı ama
 * şu ayar açık" gibi çelişkili bir durumla karşılaşmaz. Kapatıldığında hiçbir alt ayar
 * sıfırlanmaz — yeniden açtığında kaldığı yerden devam eder.
 */
@Composable
fun ExperimentalSettingsContent(
    settings: AppSettings?,
    store: SettingsDataStore,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val accent = LocalKitsugiAccent.current

    // Ayarlar akışından bağımsız çalışabilmek için yerel kopya: sayfa, oynatıcı açıkken
    // ya da ayar akışı gecikmeli geldiğinde de doğru anahtar durumunu gösterir.
    var current by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) { if (settings != null) current = settings }
    if (settings == null) {
        LaunchedEffect(Unit) { current = store.settingsFlow.first() }
    }

    var showConfirm by remember { mutableStateOf(false) }
    val enabled = current?.autoTrackEnabled == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Uyarı bloğu ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(KitsugiColors.AccentOrange.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_experimental_section_about),
                    style = MaterialTheme.typography.labelLarge,
                    color = KitsugiColors.AccentOrange
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_experimental_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionHeader(stringResource(R.string.settings_experimental_section_tracking))

        val row = current
        if (row != null) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.experimental_autotrack_title),
                description = stringResource(R.string.experimental_autotrack_desc),
                icon = Icons.PlaylistAddCheck,
                iconColor = accent,
                checked = enabled,
                onCheckedChange = { wanted ->
                    if (wanted) {
                        // Açmak: önce onay. Onaylanmadan hiçbir şey yazılmaz.
                        showConfirm = true
                    } else {
                        scope.launch { store.setAutoTrackEnabled(false) }
                    }
                }
            )

            if (enabled) {
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.auto_track_add_new),
                    description = stringResource(R.string.auto_track_add_new_desc),
                    icon = Icons.PlaylistAdd,
                    iconColor = accent,
                    checked = row.autoTrackAddNew,
                    onCheckedChange = { scope.launch { store.setAutoTrackAddNew(it) } }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.auto_track_sync_sources),
                    description = stringResource(R.string.auto_track_sync_sources_desc),
                    icon = Icons.Sync,
                    iconColor = accent,
                    checked = row.autoTrackSyncSources,
                    onCheckedChange = { scope.launch { store.setAutoTrackSyncSources(it) } }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.auto_track_respect_dropped),
                    description = stringResource(R.string.auto_track_respect_dropped_desc),
                    icon = Icons.Close,
                    iconColor = accent,
                    checked = row.autoTrackRespectDropped,
                    onCheckedChange = { scope.launch { store.setAutoTrackRespectDropped(it) } }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.quick_rate_enabled),
                    description = stringResource(R.string.quick_rate_enabled_desc),
                    icon = Icons.Star,
                    iconColor = accent,
                    checked = row.quickRateEnabled,
                    onCheckedChange = { scope.launch { store.setQuickRateEnabled(it) } }
                )
                if (row.quickRateEnabled) {
                    var rateSeconds by remember(row.quickRateSeconds) {
                        mutableFloatStateOf(row.quickRateSeconds.toFloat())
                    }
                    Text(
                        text = stringResource(R.string.quick_rate_seconds, rateSeconds.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KitsugiColors.TextPrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    )
                    Slider(
                        value = rateSeconds,
                        onValueChange = { rateSeconds = it },
                        valueRange = 5f..60f,
                        steps = 54,
                        onValueChangeFinished = {
                            scope.launch { store.setQuickRateSeconds(rateSeconds.toInt()) }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                var minimumSeconds by remember(row.autoTrackMinSeconds) {
                    mutableFloatStateOf(row.autoTrackMinSeconds.toFloat())
                }
                Text(
                    text = stringResource(R.string.auto_track_min_seconds, minimumSeconds.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KitsugiColors.TextPrimary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )
                Slider(
                    value = minimumSeconds,
                    onValueChange = { minimumSeconds = it },
                    valueRange = 15f..600f,
                    steps = 0,
                    onValueChangeFinished = {
                        scope.launch { store.setAutoTrackMinSeconds(minimumSeconds.toInt()) }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.experimental_autotrack_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KitsugiColors.TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(80.dp))
    }

    if (showConfirm) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.experimental_autotrack_confirm_title),
            message = stringResource(R.string.experimental_autotrack_confirm_message),
            confirmText = stringResource(R.string.experimental_autotrack_confirm_accept),
            dismissText = stringResource(R.string.experimental_autotrack_cancel),
            isDestructive = false,
            onConfirm = {
                showConfirm = false
                scope.launch { store.setAutoTrackEnabled(true) }
            },
            onDismiss = { showConfirm = false }
        )
    }
}
