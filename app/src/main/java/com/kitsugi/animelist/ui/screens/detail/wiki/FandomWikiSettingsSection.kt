package com.kitsugi.animelist.ui.screens.detail.wiki

import android.widget.Toast
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsListItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.components.KitsugiSettingsSwitchItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ayarlar → Tercihler → "Liste ve puan" sekmesindeki Fandom Wiki bölümü.
 *
 * Tercihleri doğrudan [FandomWikiPrefs] üzerinden okuyup yazar; ayarlar ekranının
 * parametre zincirine ek alan gerektirmez.
 */
@Composable
fun FandomWikiSettingsSection(accentColor: Color) {
    val context = LocalContext.current
    FandomWikiPrefs.ensureLoaded(context)
    val state by FandomWikiPrefs.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // ── Okuma görünümü (tema + yazı boyutu) ─────────────────────────────────
    // 2026-10-23: Bu tercih daha önce yalnızca okuyucunun ⋮ menüsündeki "Sayfa
    // araçları → Görünüm" yolundan erişilebiliyordu. Artık ayarlardan da açılıyor;
    // iki yol da aynı DataStore anahtarlarını ve aynı pencereyi kullanır.
    val store = remember(context) { SettingsDataStore(context) }
    val appearance by remember(store) {
        store.settingsFlow
            .map { it.wikiPageTheme to it.wikiTextZoom }
            .distinctUntilChanged()
            .catch { emit("APP" to 100) }
    }.collectAsState(initial = null)
    var showAppearance by remember { mutableStateOf(false) }

    KitsugiSettingsSection(title = stringResource(R.string.fandom_settings_title)) {
        KitsugiSettingsSwitchItem(
            title = stringResource(R.string.fandom_settings_enabled),
            description = stringResource(R.string.fandom_settings_enabled_desc),
            icon = Icons.AutoStories,
            iconColor = accentColor,
            checked = state.enabled,
            onCheckedChange = { checked -> FandomWikiPrefs.update(context) { it.copy(enabled = checked) } }
        )

        KitsugiSettingsDivider()

        KitsugiSettingsListItem(
            title = stringResource(R.string.wiki_reading_appearance),
            description = stringResource(R.string.wiki_reading_appearance_desc),
            value = stringResource(
                R.string.wiki_reading_appearance_value,
                stringResource(
                    when (appearance?.first ?: "APP") {
                        "SITE" -> R.string.wiki_theme_site
                        "LIGHT" -> R.string.wiki_theme_light
                        "DARK" -> R.string.wiki_theme_dark
                        else -> R.string.wiki_theme_app
                    }
                ),
                appearance?.second ?: 100
            ),
            icon = Icons.Rounded.Brush,
            iconColor = accentColor,
            onClick = { showAppearance = true }
        )

        KitsugiSettingsDivider()

        KitsugiSettingsSwitchItem(
            title = stringResource(R.string.fandom_settings_name),
            description = stringResource(R.string.fandom_settings_name_desc),
            icon = Icons.ManageSearch,
            iconColor = accentColor,
            checked = state.nameFallback,
            enabled = state.enabled,
            onCheckedChange = { checked -> FandomWikiPrefs.update(context) { it.copy(nameFallback = checked) } }
        )

        KitsugiSettingsDivider()

        KitsugiSettingsSwitchItem(
            title = stringResource(R.string.fandom_settings_spoiler),
            description = stringResource(R.string.fandom_settings_spoiler_desc),
            icon = Icons.VisibilityOff,
            iconColor = accentColor,
            checked = state.spoilerGuard,
            enabled = state.enabled,
            onCheckedChange = { checked -> FandomWikiPrefs.update(context) { it.copy(spoilerGuard = checked) } }
        )

        KitsugiSettingsDivider()

        KitsugiSettingsSwitchItem(
            title = stringResource(R.string.fandom_settings_turkish),
            description = stringResource(R.string.fandom_settings_turkish_desc),
            icon = Icons.Language,
            iconColor = accentColor,
            checked = state.preferTurkish,
            enabled = state.enabled,
            onCheckedChange = { checked -> FandomWikiPrefs.update(context) { it.copy(preferTurkish = checked) } }
        )

        KitsugiSettingsDivider()

        // Yanlışlıkla "Hayır, yanlış" denmiş ve geri alma kartı kapatılmış olsa bile kurtarma yolu.
        KitsugiSettingsItem(
            title = stringResource(R.string.fandom_settings_restore_rejected),
            description = stringResource(R.string.fandom_settings_restore_rejected_desc),
            icon = Icons.History,
            iconColor = accentColor,
            onClick = {
                scope.launch {
                    FandomRepository.restoreRejectedMatches(context)
                    Toast.makeText(context, context.getString(R.string.fandom_settings_rejected_restored), Toast.LENGTH_SHORT).show()
                }
            }
        )

        KitsugiSettingsDivider()

        KitsugiSettingsItem(
            title = stringResource(R.string.fandom_settings_clear_cache),
            description = stringResource(R.string.fandom_settings_clear_cache_desc),
            icon = Icons.DeleteSweep,
            iconColor = accentColor,
            onClick = {
                scope.launch {
                    FandomRepository.clearAll(context)
                    Toast.makeText(context, context.getString(R.string.fandom_settings_cache_cleared), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showAppearance) {
        WikiReadingSettingsDialog(
            mode = appearance?.first ?: "APP",
            zoom = appearance?.second ?: 100,
            onDismiss = { showAppearance = false }
        ) { theme, zoom ->
            showAppearance = false
            scope.launch {
                runCatching { store.setWikiReadingAppearance(theme, zoom) }
            }
        }
    }
}
