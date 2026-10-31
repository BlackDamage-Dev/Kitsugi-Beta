package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.DisableDialogWindowForceDark
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Sayfa araçlarının tek giriş noktası.
 *
 * 2026-10-21 (ikinci tur) tasarım kararı: kullanıcı WebView'in üstünde TEK bir denetim
 * istedi — sağ üstteki `⋮`. Bu yüzden sayfa araçları artık ekranda hiç satır kaplamaz;
 * yalnızca üst şeritteki menüden açılan bir sayfa (dialog) olarak çizilir:
 *
 *  - Çeviri ve reklam engeli anahtarları (durum + engellenen istek sayısı),
 *  - Görünüm/yazı boyutu, sayfada bul, sayfa durumu ve hakkında.
 *
 * Menüden açma isteği [WikiPageToolsController] üzerinden gelir: controller, üst şeritteki
 * `⋮` menüsünün çağırdığı `openRequest` sayacını tutar. Böylece WebView sayfası ile üst
 * şerit arasında geri çağrım karmaşası olmadan tek yönlü bir sinyal yeter.
 */
@Stable
internal class WikiPageToolsController {
    /** Sayaç DEĞİL, açık istek sayısı: her `open()` bir kez tüketilir. */
    private var handledRequest = 0
    internal var openRequest by mutableStateOf(0)
        private set

    /** Üst şeritteki menüden "Sayfa araçları" seçildiğinde çağrılır. */
    fun open() {
        openRequest++
    }

    /**
     * İsteği yalnızca BİR kez tüketir. Sekme değişince `WikiPageTools` yeniden kurulur;
     * eski sayaç değeri yeni sayfada dialogu kendiliğinden açmamalı.
     */
    internal fun consume(request: Int): Boolean {
        if (request <= handledRequest) return false
        handledRequest = request
        return true
    }
}

@Composable
internal fun WikiPageTools(
    session: WikiOriginalSession,
    preferences: WikiPagePreferences?,
    assets: WikiPageAssets?,
    config: WikiPageToolConfig,
    savingPreference: Boolean,
    preferenceFailed: Boolean,
    onSaveAppearance: (String, Int) -> Unit,
    onToggleAds: () -> Unit,
    controller: WikiPageToolsController? = null
) {
    var showTools by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val host = remember(session.url) {
        runCatching { android.net.Uri.parse(session.url).host }.getOrNull().orEmpty()
    }
    val openRequest = controller?.openRequest ?: 0
    LaunchedEffect(openRequest) {
        if (controller != null && controller.consume(openRequest)) showTools = true
    }

    val statusLines = wikiStatusLines(session, preferences, config, preferenceFailed)
    val retryAvailable = session.enhancementError ||
        (config.translate && session.translationFailures > 0)
    val translateDetail = if (config.translate) {
        stringResource(
            if (session.translating) R.string.wiki_dom_translating else R.string.wiki_dom_translated,
            config.target,
            session.translatedNodes
        )
    } else null

    if (showTools) {
        WikiPageToolsDialog(
            translateOn = config.translate,
            translateDetail = translateDetail,
            translateEnabled = preferences != null && assets != null,
            adsOn = config.blockAds,
            adsDetail = when {
                !config.blockAds -> null
                session.blockedRequests > 0 ->
                    stringResource(R.string.wiki_tools_ad_block_blocked, session.blockedRequests)
                else -> null
            },
            adsEnabled = preferences != null && !savingPreference,
            canOpenAppearance = preferences != null && !savingPreference,
            canFind = session.view != null,
            statusLines = statusLines,
            retryAvailable = retryAvailable,
            onToggleTranslate = { session.translationOverride = !config.translate },
            onToggleAds = onToggleAds,
            onOpenAppearance = { showTools = false; showAppearance = true },
            onOpenFind = { showTools = false; session.findOpen = true },
            onOpenInfo = { showTools = false; showInfo = true },
            onRetry = { session.translationRetry++ },
            onDismiss = { showTools = false }
        )
    }

    if (showAppearance && preferences != null) {
        WikiReadingSettingsDialog(preferences, onDismiss = { showAppearance = false }) { mode, zoom ->
            showAppearance = false
            onSaveAppearance(mode, zoom)
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            textContentColor = KitsugiColors.TextSecondary,
            title = {
                DisableDialogWindowForceDark()
                Text(stringResource(R.string.wiki_tools_info), color = KitsugiColors.TextPrimary)
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (host.isNotBlank()) {
                        Text(stringResource(R.string.wiki_original_hint, host))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(stringResource(R.string.wiki_tools_explanation))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            R.string.wiki_filter_attribution,
                            assets?.rules?.ruleCount ?: 0,
                            assets?.filterRevision.orEmpty()
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(assets?.license.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary),
                    onClick = { showInfo = false }
                ) { Text(stringResource(R.string.kapat)) }
            }
        )
    }
}

/**
 * Üst şeritteki `⋮` menüsünden açılan sayfa araçları sayfası.
 *
 * Tüm durumlar tek yerde: iki anahtar, üç eylem ve varsa uyarı listesi. Eski sürümde bu
 * bilgilerin bir kısmı WebView'in üstünde sürekli görünür durumdaydı; artık yalnızca
 * istendiğinde açılıyor.
 */
@Composable
private fun WikiPageToolsDialog(
    translateOn: Boolean,
    translateDetail: String?,
    translateEnabled: Boolean,
    adsOn: Boolean,
    adsDetail: String?,
    adsEnabled: Boolean,
    canOpenAppearance: Boolean,
    canFind: Boolean,
    statusLines: List<String>,
    retryAvailable: Boolean,
    onToggleTranslate: () -> Unit,
    onToggleAds: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenFind: () -> Unit,
    onOpenInfo: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val retryAction: (@Composable () -> Unit)? = if (retryAvailable) {
        {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                onClick = onRetry
            ) { Text(stringResource(R.string.yenile)) }
        }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        title = {
            DisableDialogWindowForceDark()
            Text(stringResource(R.string.wiki_page_tools), color = KitsugiColors.TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                WikiToolSwitchRow(
                    icon = Icons.Translate,
                    title = stringResource(R.string.wiki_tools_translate_title),
                    detail = translateDetail,
                    checked = translateOn,
                    enabled = translateEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onToggleTranslate
                )
                WikiToolSwitchRow(
                    icon = Icons.Security,
                    title = stringResource(R.string.wiki_tools_ad_block_title),
                    detail = adsDetail,
                    checked = adsOn,
                    enabled = adsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onToggleAds
                )
                HorizontalDivider(
                    color = KitsugiColors.Border.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                WikiToolActionRow(
                    icon = Icons.Tune,
                    title = stringResource(R.string.wiki_reading_appearance),
                    enabled = canOpenAppearance,
                    onClick = onOpenAppearance
                )
                WikiToolActionRow(
                    icon = Icons.Search,
                    title = stringResource(R.string.wiki_find_in_page),
                    enabled = canFind,
                    onClick = onOpenFind
                )
                WikiToolActionRow(
                    icon = Icons.Info,
                    title = stringResource(R.string.wiki_tools_info),
                    enabled = true,
                    onClick = onOpenInfo
                )
                if (statusLines.isNotEmpty()) {
                    HorizontalDivider(
                        color = KitsugiColors.Border.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.wiki_tools_status_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    statusLines.forEach { line ->
                        Row(
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.8f))
                            )
                            Text(
                                text = line,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary),
                onClick = onDismiss
            ) { Text(stringResource(R.string.kapat)) }
        },
        dismissButton = retryAction
    )
}

@Composable
private fun WikiToolSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onCheckedChange: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .tvClickable(enabled = enabled, shape = shape, onClick = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accentColor,
                checkedThumbColor = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor)
            )
        )
    }
}

@Composable
private fun WikiToolActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(shape)
            .tvClickable(enabled = enabled, shape = shape, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Sayfa araçları sayfasında listelenecek kısa durum/uyarı satırları. */
@Composable
private fun wikiStatusLines(
    session: WikiOriginalSession,
    preferences: WikiPagePreferences?,
    config: WikiPageToolConfig,
    preferenceFailed: Boolean
): List<String> {
    val lines = mutableListOf<String>()
    if (preferences?.pageTheme != "SITE" && !session.pageThemeSupported) {
        lines.add(stringResource(R.string.wiki_theme_limited))
    }
    if (config.translate && session.progress == 100 && !session.articleAvailable && !session.enhancementError) {
        lines.add(stringResource(R.string.wiki_dom_no_article))
    }
    if (preferenceFailed) lines.add(stringResource(R.string.wiki_tools_save_failed))
    if (session.enhancementError) lines.add(stringResource(R.string.wiki_tools_script_failed))
    if (config.translate && session.translationLimited) lines.add(stringResource(R.string.wiki_dom_limit))
    if (config.translate && session.translationFailures > 0 && !session.translationLimited) {
        lines.add(stringResource(R.string.wiki_dom_partial))
    }
    return lines
}

@Composable
private fun WikiReadingSettingsDialog(
    preferences: WikiPagePreferences,
    onDismiss: () -> Unit,
    onApply: (String, Int) -> Unit
) {
    var mode by remember { mutableStateOf(WikiReadingAppearance.normalize(preferences.pageTheme)) }
    var zoom by remember { mutableStateOf(WikiReadingAppearance.textPercent(preferences.textZoom).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        title = {
            DisableDialogWindowForceDark()
            Text(stringResource(R.string.wiki_reading_appearance), color = KitsugiColors.TextPrimary)
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.wiki_theme_explanation))
                Column(Modifier.selectableGroup()) {
                    WikiReadingAppearance.modes.forEach { value ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = mode == value, role = Role.RadioButton, onClick = { mode = value }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == value, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = KitsugiColors.TextPrimary,
                                    unselectedColor = KitsugiColors.TextSecondary))
                            Text(stringResource(when (value) {
                                "APP" -> R.string.wiki_theme_app
                                "LIGHT" -> R.string.wiki_theme_light
                                "DARK" -> R.string.wiki_theme_dark
                                else -> R.string.wiki_theme_site
                            }), color = KitsugiColors.TextPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.wiki_text_percent, zoom.toInt()), color = KitsugiColors.TextPrimary)
                Slider(value = zoom, onValueChange = { zoom = WikiReadingAppearance.textPercent(it.toInt()).toFloat() },
                    valueRange = 80f..180f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = KitsugiColors.TextPrimary,
                        activeTrackColor = KitsugiColors.TextPrimary))
                Text(stringResource(R.string.wiki_text_scale_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { mode = "SITE"; zoom = 100f }) { Text(stringResource(R.string.wiki_reading_reset)) }
            }
        },
        confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { onApply(mode, zoom.toInt()) }) { Text(stringResource(R.string.wiki_reading_apply)) } },
        dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = onDismiss) { Text(stringResource(R.string.kapat)) } }
    )
}
