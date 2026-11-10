package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kitsugi.animelist.utils.KitsugiTranslateUtils

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

/** Sayfa araçlarından açılan dil seçicisinin hangi yönü düzenlediği. */
private enum class WikiLanguagePickerKind { SOURCE, TARGET }

/** Dil kodunun katalogdaki adı; kaynak tarafta "auto" otomatik algılamadır. */
@Composable
private fun wikiLanguageName(code: String): String =
    if (code.equals("auto", ignoreCase = true)) {
        stringResource(R.string.translate_auto_detect)
    } else {
        KitsugiTranslateUtils.googleLanguageCatalog
            .firstOrNull { it.first.equals(code, ignoreCase = true) }?.second
            ?: code.uppercase()
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
    controller: WikiPageToolsController? = null,
    /**
     * Çeviri kaynak/hedef dili seçimi BURADAN yapılır; uygulama ayarlarına
     * gidilmesine gerek yoktur. Seçim aynı küresel ayara yazılır, böylece
     * Ayarlar sayfasıyla her zaman senkron kalır.
     */
    onSelectSourceLanguage: (String) -> Unit = {},
    onSelectTargetLanguage: (String) -> Unit = {}
) {
    var showTools by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var languagePicker by remember { mutableStateOf<WikiLanguagePickerKind?>(null) }

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
            sourceLanguageName = wikiLanguageName(config.source),
            targetLanguageName = wikiLanguageName(config.target),
            languageEnabled = preferences != null && !savingPreference,
            onOpenSourcePicker = { languagePicker = WikiLanguagePickerKind.SOURCE },
            onOpenTargetPicker = { languagePicker = WikiLanguagePickerKind.TARGET },
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
            theme = preferences?.pageTheme ?: "APP",
            zoom = WikiReadingAppearance.textPercent(preferences?.textZoom ?: 100),
            keepScreenOn = session.keepScreenOn,
            canKeepScreenOn = session.view != null,
            onToggleKeepScreenOn = { session.keepScreenOn = it },
            onSaveAppearance = onSaveAppearance,
            onToggleTranslate = { session.translationOverride = !config.translate },
            onToggleAds = onToggleAds,
            onOpenAppearance = { showTools = false; showAppearance = true },
            onOpenFind = { showTools = false; session.findOpen = true },
            onOpenInfo = { showTools = false; showInfo = true },
            onRetry = { session.translationRetry++ },
            onDismiss = { showTools = false }
        )
    }

    languagePicker?.let { kind ->
        val autoName = stringResource(R.string.translate_auto_detect)
        WikiLanguagePickerDialog(
            title = stringResource(
                if (kind == WikiLanguagePickerKind.SOURCE) R.string.settings_translate_source
                else R.string.settings_translate_target
            ),
            options = remember(kind, autoName) {
                val catalog = KitsugiTranslateUtils.googleLanguageCatalog
                if (kind == WikiLanguagePickerKind.SOURCE) listOf("auto" to autoName) + catalog else catalog
            },
            selectedCode = if (kind == WikiLanguagePickerKind.SOURCE) config.source else config.target,
            onDismiss = { languagePicker = null },
            onSelect = { code ->
                languagePicker = null
                // Dil seçmek = bu dile çevirmek istemektir: çeviri kapalıysa bu
                // sayfada açılır. Dil değişimi ayar akışıyla geldiğinde manuel
                // seçim sıfırlanır; bekleyen bayrak seçimin kaybolmasını önler.
                val current = if (kind == WikiLanguagePickerKind.SOURCE) config.source else config.target
                if (code.equals(current, ignoreCase = true)) {
                    session.translationOverride = true
                } else {
                    session.pendingTranslationEnable = true
                    if (kind == WikiLanguagePickerKind.SOURCE) {
                        onSelectSourceLanguage(code)
                    } else {
                        onSelectTargetLanguage(code)
                    }
                }
            }
        )
    }

    if (showAppearance && preferences != null) {
        WikiReadingSettingsDialog(
            mode = preferences.pageTheme,
            zoom = preferences.textZoom,
            onDismiss = { showAppearance = false }
        ) { mode, zoom ->
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
                Column(Modifier.heightIn(max = 420.dp).kitsugiVerticalScroll(rememberScrollState())) {
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
    sourceLanguageName: String,
    targetLanguageName: String,
    languageEnabled: Boolean,
    onOpenSourcePicker: () -> Unit,
    onOpenTargetPicker: () -> Unit,
    adsOn: Boolean,
    adsDetail: String?,
    adsEnabled: Boolean,
    canOpenAppearance: Boolean,
    canFind: Boolean,
    statusLines: List<String>,
    retryAvailable: Boolean,
    theme: String,
    zoom: Int,
    keepScreenOn: Boolean,
    canKeepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSaveAppearance: (String, Int) -> Unit,
    onToggleTranslate: () -> Unit,
    onToggleAds: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenFind: () -> Unit,
    onOpenInfo: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val zoomLabel = stringResource(R.string.wiki_text_percent, zoom)
    val canZoom = canOpenAppearance
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
                modifier = Modifier.heightIn(max = 460.dp).kitsugiVerticalScroll(rememberScrollState())
            ) {
                // 2026-10-23: satırlar tek sütun hâlinde yığılmıyor; uygulama temasındaki
                // ayar grupları gibi başlıklı, yuvarlak yüzeylere ayrıldı.
                WikiToolGroup(title = stringResource(R.string.wiki_tools_section_content)) {
                    WikiToolSwitchRow(
                        icon = Icons.Translate,
                        title = stringResource(R.string.wiki_tools_translate_title),
                        detail = translateDetail,
                        checked = translateOn,
                        enabled = translateEnabled,
                        accentColor = accentColor,
                        onCheckedChange = onToggleTranslate
                    )
                    WikiToolLanguageRow(
                        title = stringResource(R.string.wiki_translate_source_language),
                        value = sourceLanguageName,
                        enabled = languageEnabled,
                        onClick = onOpenSourcePicker
                    )
                    WikiToolLanguageRow(
                        title = stringResource(R.string.wiki_translate_target_language),
                        value = targetLanguageName,
                        enabled = languageEnabled,
                        onClick = onOpenTargetPicker
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
                }
                Spacer(Modifier.height(10.dp))
                WikiToolGroup(title = stringResource(R.string.wiki_tools_section_view)) {
                    WikiToolActionRow(
                        icon = Icons.Tune,
                        title = stringResource(R.string.wiki_reading_appearance),
                        detail = zoomLabel,
                        enabled = canOpenAppearance,
                        onClick = onOpenAppearance
                    )
                    WikiToolActionRow(
                        icon = Icons.FormatSize,
                        title = stringResource(R.string.wiki_text_smaller),
                        enabled = canZoom && zoom > 80,
                        onClick = { onSaveAppearance(theme, (zoom - 10).coerceAtLeast(80)) }
                    )
                    WikiToolActionRow(
                        icon = Icons.FormatSize,
                        title = stringResource(R.string.wiki_text_larger),
                        enabled = canZoom && zoom < 180,
                        onClick = { onSaveAppearance(theme, (zoom + 10).coerceAtMost(180)) }
                    )
                    WikiToolSwitchRow(
                        icon = Icons.Visibility,
                        title = stringResource(R.string.wiki_keep_screen_on),
                        detail = stringResource(R.string.wiki_keep_screen_on_desc),
                        checked = keepScreenOn,
                        enabled = canKeepScreenOn,
                        accentColor = accentColor,
                        onCheckedChange = { onToggleKeepScreenOn(!keepScreenOn) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                WikiToolGroup(title = stringResource(R.string.wiki_tools_section_page)) {
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
                }
                if (statusLines.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.wiki_tools_status_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
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
    onClick: () -> Unit,
    detail: String? = null
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
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** Çeviri anahtarının altındaki dil seçim satırı: başlık + seçili dil + açılır işaret. */
@Composable
private fun WikiToolLanguageRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .tvClickable(enabled = enabled, shape = shape, onClick = onClick)
            .padding(start = 32.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Language,
            contentDescription = null,
            tint = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.ArrowDropDown,
            contentDescription = null,
            tint = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Aranabilir dil seçici. Katalog ~130 dil olduğu için düz kaydırma yerine
 * arama + LazyColumn kullanılır; satırlar yalnızca görünürken bileşir.
 */
@Composable
private fun WikiLanguagePickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            options
        } else {
            options.filter { (code, name) ->
                name.contains(needle, ignoreCase = true) || code.contains(needle, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        title = {
            DisableDialogWindowForceDark()
            Text(title, color = KitsugiColors.TextPrimary)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(64) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        cursorColor = KitsugiColors.TextPrimary,
                        focusedBorderColor = KitsugiColors.TextPrimary,
                        unfocusedBorderColor = KitsugiColors.TextSecondary,
                        focusedLabelColor = KitsugiColors.TextPrimary,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    label = { Text(stringResource(R.string.wiki_translate_language_search)) },
                    leadingIcon = {
                        Icon(Icons.Search, contentDescription = null, tint = KitsugiColors.TextSecondary)
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).selectableGroup()
                ) {
                    items(filtered, key = { it.first }) { (code, name) ->
                        val selected = code.equals(selectedCode, ignoreCase = true)
                        val rowShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(rowShape)
                                .background(
                                    if (selected) accentColor.copy(alpha = 0.14f) else Color.Transparent
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(code) }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentColor,
                                    unselectedColor = KitsugiColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = name,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = code,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
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
        }
    )
}

/**
 * Araç sayfasındaki gruplu bölüm: uygulama genelindeki ayar kartlarıyla aynı dil
 * (yuvarlak yüzey + kısa başlık). Satırlar arasında ayırıcı çizgi yok; dokunma
 * hedefleri tek tek yuvarlatılmış olduğu için çizgiye gerek kalmıyor.
 */
@Composable
private fun WikiToolGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        color = KitsugiColors.TextMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        content = content
    )
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

/**
 * Okuma görünümü penceresi: tema modu + yazı boyutu.
 *
 * 2026-10-23: yalnızca okuyucunun ⋮ menüsünden değil, **Ayarlar → Fandom Wiki**
 * bölümünden de açılabilsin diye `internal` yapıldı ve tercih nesnesi yerine düz
 * değerler alıyor. Böylece aynı pencere iki yerden de tek kaynakla çiziliyor.
 */
@Composable
internal fun WikiReadingSettingsDialog(
    mode: String,
    zoom: Int,
    onDismiss: () -> Unit,
    onApply: (String, Int) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var selectedMode by remember { mutableStateOf(WikiReadingAppearance.normalize(mode)) }
    var selectedZoom by remember {
        mutableStateOf(WikiReadingAppearance.textPercent(zoom).toFloat())
    }
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
            Column(Modifier.heightIn(max = 420.dp).kitsugiVerticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.wiki_theme_explanation))
                Column(Modifier.selectableGroup()) {
                    WikiReadingAppearance.modes.forEach { value ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = selectedMode == value, role = Role.RadioButton,
                                onClick = { selectedMode = value }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == value, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor,
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
                Text(stringResource(R.string.wiki_text_percent, selectedZoom.toInt()), color = KitsugiColors.TextPrimary)
                Slider(value = selectedZoom,
                    onValueChange = { selectedZoom = WikiReadingAppearance.textPercent(it.toInt()).toFloat() },
                    valueRange = 80f..180f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = accentColor,
                        activeTrackColor = accentColor))
                Text(stringResource(R.string.wiki_text_scale_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary),
                    onClick = { selectedMode = "APP"; selectedZoom = 100f }) { Text(stringResource(R.string.wiki_reading_reset)) }
            }
        },
        confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { onApply(selectedMode, selectedZoom.toInt()) }) { Text(stringResource(R.string.wiki_reading_apply)) } },
        dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = onDismiss) { Text(stringResource(R.string.kapat)) } }
    )
}
