package com.kitsugi.animelist.ui.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.core.player.input.PlayerKeyBinding
import com.kitsugi.animelist.core.player.input.PlayerShortcutAction
import com.kitsugi.animelist.core.player.input.PlayerShortcutGroup
import com.kitsugi.animelist.core.player.input.PlayerShortcutMap
import com.kitsugi.animelist.core.player.input.PlayerShortcutSettings
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsListItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.components.KitsugiSettingsSwitchItem
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Oynatıcı Ayarları — **Klavye & Gamepad** sekmesi (v2.4.341).
 *
 * Kullanıcı isteği: "klavye desteğini arşa çıkarmak ve oynatıcım için daha iyi ve
 * kapsamlı hale getirmek". Bu sekme, kısayolların tamamını görünür ve ayarlanabilir
 * yapar; hiçbir davranış gizli kalmaz.
 *
 * Bölümler:
 *  1. Genel: açma/kapama, otomatik algılama, gamepad.
 *  2. Gösterge (OSD): küçük aksiyon göstergesi, süre çarpanı, tam OSD davranışı.
 *  3. Arama adımları: normal / hassas / uzun + basılı tutma ivmesi.
 *  4. Kısayol listesi: gruplara ayrılmış, tuş başına etiket. Bir tuşa dokununca
 *     eylem devre dışı bırakılır (mpv'nin `--input-default-bindings=no` karşılığı
 *     yerine eylem bazlı susturma — tek eylemi kapatmak diğerlerini bozmaz).
 *  5. Ön ayarlar: Varsayılan / VLC tarzı / Sade.
 */
@Composable
internal fun PlayerKeyboardTab(
    keyboardEnabled: Boolean,
    autoDetectExternalKeyboard: Boolean,
    gamepadEnabled: Boolean,
    keyboardOsdEnabled: Boolean,
    keyboardOsdDurationScale: Float,
    keyboardOpensFullOsd: Boolean,
    keyboardHelpEnabled: Boolean,
    disabledShortcuts: String,
    customBindings: String,
    seekStepSeconds: Int,
    preciseSeekSeconds: Int,
    largeSeekSeconds: Int,
    holdToAccelerate: Boolean,
    accentColor: Color,
    onKeyboardEnabledChanged: (Boolean) -> Unit,
    onAutoDetectChanged: (Boolean) -> Unit,
    onGamepadEnabledChanged: (Boolean) -> Unit,
    onOsdEnabledChanged: (Boolean) -> Unit,
    onOsdDurationScaleChanged: (Float) -> Unit,
    onOpensFullOsdChanged: (Boolean) -> Unit,
    onHelpEnabledChanged: (Boolean) -> Unit,
    onDisabledShortcutsChanged: (String) -> Unit,
    onCustomBindingsChanged: (String) -> Unit,
    onSeekStepSelected: (Int) -> Unit,
    onPreciseSeekSelected: (Int) -> Unit,
    onLargeSeekSelected: (Int) -> Unit,
    onHoldToAccelerateChanged: (Boolean) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val disabled = remember(disabledShortcuts) {
        PlayerShortcutSettings.parseDisabled(disabledShortcuts)
    }
    val overrides = remember(customBindings) {
        PlayerShortcutSettings.parseOverrides(customBindings)
    }
    val effective = remember(disabledShortcuts, customBindings) {
        PlayerShortcutMap.resolve(overrides, disabled)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            KitsugiSettingsSection(title = "Klavye & Gamepad") {
                KitsugiSettingsSwitchItem(
                    title = "Klavye kısayolları",
                    description = "Harici klavye ve uzaktan kumanda ile oynatıcıyı yönet. Kapatırsanız tuşlar " +
                        "MPV motoruna ham iletilir (kendi input.conf eşlemeleriniz çalışır).",
                    checked = keyboardEnabled,
                    icon = Icons.Rounded.Keyboard,
                    iconColor = accentColor,
                    onCheckedChange = onKeyboardEnabledChanged,
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = "Gamepad desteği",
                    description = "Kol bağlıyken A = oynat/duraklat, B = çıkış onayı, omuz tuşları ile arama.",
                    checked = gamepadEnabled,
                    icon = Icons.Rounded.SportsEsports,
                    iconColor = accentColor,
                    onCheckedChange = onGamepadEnabledChanged,
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = "Harici klavyeyi otomatik algıla",
                    description = "Yalnızca fiziksel klavye/kol takılıyken kısayolları etkinleştir; " +
                        "dokunmatik kullanımda tuş çakışması olmaz.",
                    checked = autoDetectExternalKeyboard,
                    icon = Icons.Rounded.Keyboard,
                    iconColor = accentColor,
                    onCheckedChange = onAutoDetectChanged,
                )
            }
        }

        // ─── Gösterge (OSD) ───────────────────────────────────────────────────
        item {
            KitsugiSettingsSection(title = "Kısayol Göstergesi") {
                KitsugiSettingsSwitchItem(
                    title = "Sadece tuşun göstergesini göster",
                    description = "Klavyeyle kontrol ederken tam oynatıcı arayüzü açılmasın; " +
                        "yalnızca tetiklenen kısayolun küçük göstergesi görünüp kaybolsun.",
                    checked = keyboardOsdEnabled,
                    icon = Icons.Rounded.Visibility,
                    iconColor = accentColor,
                    onCheckedChange = onOsdEnabledChanged,
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = "Klavye tam arayüzü açsın",
                    description = "Eski davranış: her tuş basımında üst/alt bar 5 saniyeliğine açılır. " +
                        "Kapalı tutmak önerilir.",
                    checked = keyboardOpensFullOsd,
                    icon = Icons.Rounded.TouchApp,
                    iconColor = accentColor,
                    onCheckedChange = onOpensFullOsdChanged,
                )

                KitsugiSettingsDivider()

                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf(
                        0.5f to "Kısa (0,5×)",
                        1.0f to "Normal (1×)",
                        1.5f to "Uzun (1,5×)",
                        2.0f to "Çok uzun (2×)",
                    )
                    val current = options.minByOrNull { kotlin.math.abs(it.first - keyboardOsdDurationScale) }?.second
                        ?: "Normal (1×)"
                    KitsugiSettingsListItem(
                        title = "Gösterge süresi",
                        description = "Küçük göstergenin ekranda kalma süresi.",
                        value = current,
                        icon = Icons.Rounded.Timer,
                        iconColor = accentColor,
                        onClick = { expanded = true },
                    )
                    KitsugiDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        dialogTitle = "Gösterge süresi",
                    ) {
                        options.forEach { (value, label) ->
                            KitsugiDropdownItem(
                                text = label,
                                selected = kotlin.math.abs(value - keyboardOsdDurationScale) < 0.01f,
                                onClick = {
                                    onOsdDurationScaleChanged(value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = "Kısayol yardım ekranı",
                    description = "Oynatıcıda ? tuşuyla tüm kısayolları listeleyin.",
                    checked = keyboardHelpEnabled,
                    icon = Icons.Rounded.Keyboard,
                    iconColor = accentColor,
                    onCheckedChange = onHelpEnabledChanged,
                )
            }
        }

        // ─── Arama adımları ───────────────────────────────────────────────────
        item {
            KitsugiSettingsSection(title = "Arama Adımları") {
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf(3, 5, 10, 15, 30)
                    KitsugiSettingsListItem(
                        title = "Ok tuşu adımı",
                        description = "← / → tuşlarıyla atlanan süre (mpv varsayılanı: 5 sn).",
                        value = "$seekStepSeconds sn",
                        icon = Icons.Rounded.RestartAlt,
                        iconColor = accentColor,
                        onClick = { expanded = true },
                    )
                    KitsugiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, dialogTitle = "Ok tuşu adımı") {
                        options.forEach { value ->
                            KitsugiDropdownItem(
                                text = "$value sn",
                                selected = value == seekStepSeconds,
                                onClick = { onSeekStepSelected(value); expanded = false },
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf(1, 2, 3, 5, 10)
                    KitsugiSettingsListItem(
                        title = "Hassas arama adımı",
                        description = "Shift + ← / →, Ctrl + ← / →, , ve . tuşlarıyla atlanan süre (mpv: 1 sn).",
                        value = "$preciseSeekSeconds sn",
                        icon = Icons.Rounded.Timer,
                        iconColor = accentColor,
                        onClick = { expanded = true },
                    )
                    KitsugiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, dialogTitle = "Hassas arama adımı") {
                        options.forEach { value ->
                            KitsugiDropdownItem(
                                text = "$value sn",
                                selected = value == preciseSeekSeconds,
                                onClick = { onPreciseSeekSelected(value); expanded = false },
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf(30, 60, 120, 300)
                    KitsugiSettingsListItem(
                        title = "Uzun arama adımı",
                        description = "↑ / ↓ tuşlarıyla atlanan süre (mpv varsayılanı: 60 sn).",
                        value = "$largeSeekSeconds sn",
                        icon = Icons.Rounded.Speed,
                        iconColor = accentColor,
                        onClick = { expanded = true },
                    )
                    KitsugiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, dialogTitle = "Uzun arama adımı") {
                        options.forEach { value ->
                            KitsugiDropdownItem(
                                text = "$value sn",
                                selected = value == largeSeekSeconds,
                                onClick = { onLargeSeekSelected(value); expanded = false },
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = "Basılı tutarak hızlandır",
                    description = "Tuş basılı kaldıkça arama/ses tekrarlanır; toplam süre göstergede birikir " +
                        "(Just Player davranışı).",
                    checked = holdToAccelerate,
                    icon = Icons.Rounded.Speed,
                    iconColor = accentColor,
                    onCheckedChange = onHoldToAccelerateChanged,
                )
            }
        }

        // ─── Ön ayarlar ───────────────────────────────────────────────────────
        item {
            KitsugiSettingsSection(title = "Hazır Ön Ayarlar") {
                PlayerShortcutSettings.Preset.entries.forEach { preset ->
                    KitsugiSettingsListItem(
                        title = preset.label,
                        description = when (preset) {
                            PlayerShortcutSettings.Preset.DEFAULT ->
                                "mpv uyumlu tam eşleme (önerilen)."
                            PlayerShortcutSettings.Preset.VLC ->
                                "↑ / ↓ ses, f tam ekran, m sessiz — VLC alışkanlıkları."
                            PlayerShortcutSettings.Preset.MINIMAL ->
                                "Yalnızca oynatma, arama ve ses; geri kalanı kapalı."
                        },
                        value = if (preset == PlayerShortcutSettings.Preset.DEFAULT &&
                            disabledShortcuts.isBlank() && customBindings.isBlank()
                        ) "Etkin" else "Uygula",
                        icon = Icons.Rounded.Keyboard,
                        iconColor = accentColor,
                        onClick = {
                            val (newDisabled, newBindings) = PlayerShortcutSettings.applyPreset(preset)
                            onDisabledShortcutsChanged(newDisabled)
                            onCustomBindingsChanged(newBindings)
                        },
                    )
                    if (preset != PlayerShortcutSettings.Preset.entries.last()) {
                        KitsugiSettingsDivider()
                    }
                }
            }
        }

        // ─── Kısayol listesi ──────────────────────────────────────────────────
        item {
            KitsugiSettingsSection(title = "Kısayollar (dokun → kapat/aç)") {
                Text(
                    text = "Bir kısayolu kapatmak, yalnızca o eylemi susturur; diğer tuşlar çalışmaya devam eder. " +
                        "Donanım ses tuşları bilinçli olarak sisteme bırakıldı.",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }

        PlayerShortcutGroup.entries.forEach { group ->
            val actions = PlayerShortcutAction.entries.filter { it.group == group }
            if (actions.isEmpty()) return@forEach
            item(key = "group-${group.name}") {
                Text(
                    text = group.title.uppercase(),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            items(count = actions.size, key = { actions[it].name }) { index ->
                val action = actions[index]
                val keys = PlayerShortcutMap.bindingsFor(action, effective)
                val isEnabled = action !in disabled
                ShortcutRow(
                    action = action,
                    keys = keys,
                    enabled = isEnabled,
                    accentColor = accentColor,
                    onClick = {
                        val next = if (isEnabled) disabled + action else disabled - action
                        onDisabledShortcutsChanged(PlayerShortcutSettings.encodeDisabled(next))
                    },
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    action: PlayerShortcutAction,
    keys: List<PlayerKeyBinding>,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Color.Transparent else Color.White.copy(alpha = 0.04f))
            .tvClickable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = when {
                    keys.isNotEmpty() -> keys.joinToString("  /  ") { it.displayLabel() }
                    // Bilinçli olarak bağlanmayan eylemler: donanım ses tuşları sisteme
                    // bırakıldı (sistem ses kontrolü bozulmasın). Kullanıcı isterse
                    // "VLC tarzı" ön ayarla ok tuşlarına bağlayabilir.
                    action == PlayerShortcutAction.VOLUME_UP ||
                        action == PlayerShortcutAction.VOLUME_DOWN ->
                        "Atanmamış · donanım ses tuşları sisteme bırakıldı"
                    else -> "Atanmamış"
                },
                color = if (enabled) accentColor else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.Keyboard,
            contentDescription = if (enabled) "Kısayolu kapat" else "Kısayolu aç",
            tint = if (enabled) accentColor.copy(alpha = 0.9f) else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    }
    KitsugiSettingsDivider()
}
