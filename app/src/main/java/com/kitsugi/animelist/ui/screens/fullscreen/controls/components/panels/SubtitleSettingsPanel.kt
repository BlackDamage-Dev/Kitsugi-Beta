package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.panels

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.theme.gradient.Slider as KitsugiSlider
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text as KitsugiText
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Aniyomi'den uyarlanmis altyazi ayarlari paneli - 3 kartli yapi.
 *
 * Ekran goruntulerinde (Topografi / Renkler / Diger) gorunen tum kontroller
 * burada uygulanmistir: yazı tipi, boyut, kalin/italik, hizalama, sinir sekli,
 * kenar boyutu, golge, renk seciciler (RGBA) ve ASS gecersiz kilma.
 */
@Composable
fun SubtitleSettingsPanel(
    subtitleStyle: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismissRequest)
    val isPortrait = LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT
    val pagerState = rememberPagerState { 3 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = if (isPortrait) Alignment.TopCenter else Alignment.CenterEnd,
    ) {
        if (isPortrait) {
            // Portrait: TopAppBar + Horizontal Pager (Aniyomi gibi)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = stringResource(R.string.inline_subtitle_settings_60fdbff),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = Shadow(blurRadius = 20f)),
                    )
                    Spacer(Modifier.width(48.dp))
                }

                // Pager indicator dots (optional simple)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = if (pagerState.currentPage == index) 20.dp else 8.dp, height = 8.dp)
                                .background(
                                    color = if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.4f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(LocalConfiguration.current.screenWidthDp.dp * 0.88f),
                    verticalAlignment = Alignment.Top,
                    pageSpacing = 12.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        when (page) {
                            0 -> SubtitleTypographyCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                            1 -> SubtitleColorsCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                            2 -> SubtitleMiscCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        } else {
            // Landscape: vertical scroll with all 3 cards
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = LANDSCAPE_PANEL_MAX_WIDTH)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.inline_subtitle_settings_60fdbff),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = Shadow(blurRadius = 20f)),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                SubtitleTypographyCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                SubtitleColorsCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                SubtitleMiscCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val PORTRAIT_PANEL_MAX_WIDTH = 560.dp
private val LANDSCAPE_PANEL_MAX_WIDTH = CARDS_MAX_WIDTH + 80.dp

// ─── Typography Card ──────────────────────────────────────────────────────────
@Composable
private fun SubtitleTypographyCard(
    style: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(colors = panelCardsColors(), modifier = modifier.widthIn(max = CARDS_MAX_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.FormatColorText, contentDescription = null, tint = Color.White)
                Text("Topografi", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            // Bold / Italic / Justification toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconToggleButton(
                    checked = style.bold,
                    onCheckedChange = { onStyleChange(style.copy(bold = it)) },
                ) {
                    Icon(Icons.FormatBold, contentDescription = "Kalın", modifier = Modifier.size(24.dp), tint = if (style.bold) Color.White else Color.White.copy(alpha = 0.6f))
                }
                IconToggleButton(
                    checked = style.italic,
                    onCheckedChange = { onStyleChange(style.copy(italic = it)) },
                ) {
                    Icon(Icons.FormatItalic, contentDescription = "İtalik", modifier = Modifier.size(24.dp), tint = if (style.italic) Color.White else Color.White.copy(alpha = 0.6f))
                }
                // Justification: left / center / right / auto
                val justifications = listOf(
                    "left" to Icons.FormatAlignLeft,
                    "center" to Icons.FormatAlignCenter,
                    "right" to Icons.FormatAlignRight,
                    "auto" to Icons.FormatAlignJustify,
                )
                justifications.forEach { (value, icon) ->
                    IconToggleButton(
                        checked = style.justification == value,
                        onCheckedChange = {
                            if (it) onStyleChange(style.copy(justification = value))
                            else onStyleChange(style.copy(justification = "auto"))
                        },
                    ) {
                        Icon(icon, contentDescription = value, tint = if (style.justification == value) Color.White else Color.White.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // Sıfırla - typography reset
                    onStyleChange(style.copy(
                        bold = false,
                        italic = false,
                        justification = "center",
                        font = "Sans Serif",
                        fontSize = 55,
                        borderStyle = "outline-and-shadow",
                        borderSize = 3,
                        shadowOffset = 0,
                    ))
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.FormatClear, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.sifirla), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Font dropdown
            FontDropdown(
                selectedFont = style.font,
                onFontSelected = { onStyleChange(style.copy(font = it)) }
            )

            // Font size slider 1..100
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Yazı tipi boyutu", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Text("${style.fontSize}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                KitsugiSlider(
                    value = style.fontSize.toFloat(),
                    onValueChange = { onStyleChange(style.copy(fontSize = it.toInt())) },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Border style dropdown
            BorderStyleDropdown(
                selectedStyle = style.borderStyle,
                onStyleSelected = { onStyleChange(style.copy(borderStyle = it)) }
            )

            // Border size slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kenar boyutu", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Text("${style.borderSize}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                KitsugiSlider(
                    value = style.borderSize.toFloat(),
                    onValueChange = { onStyleChange(style.copy(borderSize = it.toInt())) },
                    valueRange = 0f..10f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Shadow offset slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gölge uzaklığı", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Text("${style.shadowOffset}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                KitsugiSlider(
                    value = style.shadowOffset.toFloat(),
                    onValueChange = { onStyleChange(style.copy(shadowOffset = it.toInt())) },
                    valueRange = 0f..10f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Colors Card ──────────────────────────────────────────────────────────────
@Composable
private fun SubtitleColorsCard(
    style: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentColorType by remember { mutableStateOf(SubColorType.Text) }

    // Derive current color from style based on selected type
    val currentColor = when (currentColorType) {
        SubColorType.Text -> style.textColor
        SubColorType.Border -> style.borderColor
        SubColorType.Background -> style.backgroundColor
    }

    Card(colors = panelCardsColors(), modifier = modifier.widthIn(max = CARDS_MAX_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Palette, contentDescription = null, tint = Color.White)
                Text("Renkler", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            // Color type selector: Metin / Kenar / Arka plan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SubColorType.entries.forEach { type ->
                    IconToggleButton(
                        checked = currentColorType == type,
                        onCheckedChange = { currentColorType = type },
                    ) {
                        Icon(
                            when (type) {
                                SubColorType.Text -> Icons.FormatColorText
                                SubColorType.Border -> Icons.BorderColor
                                SubColorType.Background -> Icons.FormatColorFill
                            },
                            contentDescription = type.label,
                            tint = if (currentColorType == type) Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    when (currentColorType) {
                        SubColorType.Text -> "Metin"
                        SubColorType.Border -> "Kenar"
                        SubColorType.Background -> "Arka plan"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // Reset current color type to default
                    val defaultColor = when (currentColorType) {
                        SubColorType.Text -> 0xFFFFFFFF.toInt()
                        SubColorType.Border -> 0xFF000000.toInt()
                        SubColorType.Background -> 0x00000000
                    }
                    when (currentColorType) {
                        SubColorType.Text -> onStyleChange(style.copy(textColor = defaultColor))
                        SubColorType.Border -> onStyleChange(style.copy(borderColor = defaultColor))
                        SubColorType.Background -> onStyleChange(style.copy(backgroundColor = defaultColor))
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.FormatColorReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.sifirla), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Color preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color(currentColor), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            )

            // RGBA sliders
            SubtitlesColorPicker(
                color = currentColor,
                onColorChange = { newColor ->
                    when (currentColorType) {
                        SubColorType.Text -> onStyleChange(style.copy(textColor = newColor))
                        SubColorType.Border -> onStyleChange(style.copy(borderColor = newColor))
                        SubColorType.Background -> onStyleChange(style.copy(backgroundColor = newColor))
                    }
                }
            )
        }
    }
}

// ─── Miscellaneous Card (Diger) ─────────────────────────────────────────────
@Composable
private fun SubtitleMiscCard(
    style: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(colors = panelCardsColors(), modifier = modifier.widthIn(max = CARDS_MAX_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Tune, contentDescription = null, tint = Color.White)
                Text("Diğer", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            // ASS/SSA override toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ASS/SSA altyazılarını geçersiz kıl", style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = style.overrideAss,
                    onCheckedChange = { onStyleChange(style.copy(overrideAss = it)) },
                )
            }

            // Font scale (Boyut) 0.2..5.0
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.FormatSize, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Text("Boyut", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text(String.format("%.1f", style.fontScale), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                KitsugiSlider(
                    value = style.fontScale,
                    onValueChange = { onStyleChange(style.copy(fontScale = it)) },
                    valueRange = 0.2f..5f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Position (Konum) 0..150
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Tune, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Text("Konum", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text("${style.pos}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                KitsugiSlider(
                    value = style.pos.toFloat(),
                    onValueChange = { onStyleChange(style.copy(pos = it.toInt())) },
                    valueRange = 0f..150f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Dikey offset (legacy verticalOffset) -200..200
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dikey Konum (ince ayar)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    Text("${style.verticalOffset}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
                KitsugiSlider(
                    value = style.verticalOffset.toFloat(),
                    onValueChange = { onStyleChange(style.copy(verticalOffset = it.toInt())) },
                    valueRange = -200f..200f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Sadece tercih edilen dil
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sadece_tercih_edilen_dil), style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = style.showOnlyPreferredLanguages,
                    onCheckedChange = { onStyleChange(style.copy(showOnlyPreferredLanguages = it)) },
                )
            }

            // Reset misc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    onStyleChange(style.copy(
                        fontScale = 1f,
                        pos = 100,
                        verticalOffset = 0,
                        overrideAss = false,
                    ))
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.FormatClear, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.sifirla), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun FontDropdown(
    selectedFont: String,
    onFontSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fonts = remember {
        val builtIn = listOf(
            "Sans Serif", "Serif", "Monospace", "Arial", "Noto Sans", "Roboto",
            "Ubuntu", "Open Sans", "Lato", "Oswald", "PT Sans", "Source Sans Pro"
        )
        // Aniyomi paritesi: cihazdaki TTF/OTF fontlarini tara (varsa)
        val scanned = try {
            val dirs = listOfNotNull(
                context.filesDir?.let { java.io.File(it, "fonts") },
                context.getExternalFilesDir(null)?.let { java.io.File(it, "fonts") }
            )
            dirs.flatMap { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { f -> f.isFile && (f.name.endsWith(".ttf", true) || f.name.endsWith(".otf", true)) }?.map { it.nameWithoutExtension } ?: emptyList()
                } else emptyList()
            }.distinct()
        } catch (_: Exception) { emptyList() }
        (builtIn + scanned).distinct()
    }
    Column {
        Text("Yazı tipi", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(Color.White.copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedFont, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text("▼", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                fonts.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font) },
                        onClick = {
                            onFontSelected(font)
                            expanded = false
                        },
                        trailingIcon = {
                            if (font == selectedFont) Icon(Icons.Check, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BorderStyleDropdown(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "outline-and-shadow" to "Dış hat ve gölge",
        "opaque-box" to "Opak kutu",
        "background-box" to "Arka plan kutusu",
    )
    val displayText = options.find { it.first == selectedStyle }?.second ?: selectedStyle
    Column {
        Text("Sınır şekli", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(Color.White.copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(displayText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text("▼", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onStyleSelected(value)
                            expanded = false
                        },
                        trailingIcon = {
                            if (value == selectedStyle) Icon(Icons.Check, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

private enum class SubColorType(val label: String) {
    Text("Metin"),
    Border("Kenar"),
    Background("Arka plan"),
}

@Composable
fun SubtitlesColorPicker(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Color(color)
    // Extract ARGB
    val alpha = (color shr 24) and 0xFF
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF

    fun copyAsArgb(a: Int = alpha, r: Int = red, g: Int = green, b: Int = blue): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Kırmızı
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kırmızı", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("$red", style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            KitsugiSlider(
                value = red.toFloat(),
                onValueChange = { onColorChange(copyAsArgb(r = it.toInt())) },
                valueRange = 0f..255f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Yeşil
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Yeşil", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("$green", style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            KitsugiSlider(
                value = green.toFloat(),
                onValueChange = { onColorChange(copyAsArgb(g = it.toInt())) },
                valueRange = 0f..255f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Mavi
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mavi", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("$blue", style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            KitsugiSlider(
                value = blue.toFloat(),
                onValueChange = { onColorChange(copyAsArgb(b = it.toInt())) },
                valueRange = 0f..255f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Alfa
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Alfa", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("$alpha", style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            KitsugiSlider(
                value = alpha.toFloat(),
                onValueChange = { onColorChange(copyAsArgb(a = it.toInt())) },
                valueRange = 0f..255f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
