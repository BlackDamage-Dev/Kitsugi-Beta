package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

import androidx.compose.foundation.clickable
import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon
import com.kitsugi.animelist.ui.icons.KitsugiRowIcon
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.theme.isSameAccentRgb
import com.kitsugi.animelist.ui.theme.accentBrushWithAlpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Açılır seçimlerin sunum biçimi. Normal ekranlarda çapa bağlı küçük menü korunur;
 * ayarlar sayfası bu değeri [SettingsDialog] yaparak AniHyou tarzı ortalanmış,
 * arka planı karartan seçim pencerelerini kullanır.
 */
internal enum class KitsugiDropdownPresentation {
    AnchoredPopup,
    SettingsDialog
}

internal val LocalKitsugiDropdownPresentation = staticCompositionLocalOf {
    KitsugiDropdownPresentation.AnchoredPopup
}

/** Seçim satırlarının modal pencere içinde radyo düğmesi çizmesini sağlar. */
private val LocalKitsugiDropdownUsesRadio = staticCompositionLocalOf { false }

@Composable
fun KitsugiSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = KitsugiColors.Surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

internal data class KitsugiSettingsTabOption(
    val title: String,
    val icon: ImageVector,
    /** Verilirse ikon yerine orijinal platform logosu gösterilir (AniList, MAL, Kitsu, ...). */
    val platformId: String? = null
)

/** Sekmenin sığmadığı durumda kullanılan en küçük genişlik. */
private val SettingsTabMinWidth = 104.dp
/** Kaydırmalı bantta sekmelerin ekran genişliğine oranı (aynı anda ~3 sekme görünür). */
private const val SettingsTabScrollWidthFraction = 0.34f
/** Kaydırmalı bantta sekme genişliği üst sınırı (tablet/yatay ekranda dev sekme olmasın). */
private val SettingsTabScrollMaxWidth = 168.dp

/**
 * Ayarlar sayfalarının ortak üst sekme bandı ("Görünüm & Tercihler" bandı ile birebir aynı).
 *
 * Davranış:
 * - Sekmeler ekrana **sığıyorsa** eşit olarak bölünür (taşma yok, kaydırma yok).
 * - Sığmıyorsa bant **kendi içinde** yatay kaydırılır; bant ekranın sağına/soluna taşmaz.
 * - Seçili sekme (ve TV'de odaklanan sekme) otomatik olarak görünür alana ortalanır.
 * - Kaydırılabilir bandın kenarlarında "devamı var" solma ipucu çizilir.
 *
 * Böylece dikey/yatay, telefon/tablet ve TV'de aynı bant davranışı korunur.
 */
@Composable
internal fun KitsugiSettingsSegmentedTabs(
    tabs: List<KitsugiSettingsTabOption>,
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val selectedSegmentFill = accentBrushWithAlpha(0.14f)
    val selectedSegmentBorder = accentBrushWithAlpha(0.48f)
    val safeSelectedIndex = selectedIndex.coerceIn(tabs.indices)
    val outerShape = RoundedCornerShape(16.dp)
    val segmentShape = RoundedCornerShape(13.dp)
    val containerColor = KitsugiColors.Surface
    val borderColor = KitsugiColors.Border
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val isTv = LocalIsTv.current
    var focusedIndex by remember { mutableStateOf(-1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(containerColor)
            .border(1.dp, borderColor.copy(alpha = 0.6f), outerShape)
            .padding(4.dp)
    ) {
        val spacing = 4.dp
        val viewportWidth = maxWidth
        val usableWidth = (viewportWidth - spacing * (tabs.size - 1)).coerceAtLeast(0.dp)
        val evenWidth = usableWidth / tabs.size
        val scrollable = evenWidth < SettingsTabMinWidth
        val segmentWidth = if (scrollable) {
            (viewportWidth * SettingsTabScrollWidthFraction)
                .coerceIn(SettingsTabMinWidth, SettingsTabScrollMaxWidth)
        } else {
            evenWidth
        }

        val spacingPx = with(density) { spacing.toPx() }
        val segmentPx = with(density) { segmentWidth.toPx() }
        val viewportPx = with(density) { viewportWidth.toPx() }
        val contentPx = tabs.size * segmentPx + spacingPx * (tabs.size - 1)
        val maxScrollPx = (contentPx - viewportPx).coerceAtLeast(0f)

        // Seçili (TV'de odaklı) sekmeyi görünür alana ortala
        LaunchedEffect(safeSelectedIndex, focusedIndex, scrollable, segmentPx, maxScrollPx, viewportPx) {
            if (!scrollable) {
                scrollState.scrollTo(0)
                return@LaunchedEffect
            }
            val targetIndex = if (isTv && focusedIndex in tabs.indices) focusedIndex else safeSelectedIndex
            val itemCenter = targetIndex * (segmentPx + spacingPx) + segmentPx / 2f
            scrollState.animateScrollTo((itemCenter - viewportPx / 2f).coerceIn(0f, maxScrollPx).toInt())
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) {
                        Modifier
                            .horizontalScroll(scrollState)
                            .drawWithContent {
                                drawContent()
                                val fadeWidth = 22.dp.toPx()
                                val canScrollStart = scrollState.value > 2
                                val canScrollEnd = scrollState.value < scrollState.maxValue - 2
                                if (canScrollStart) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to containerColor,
                                            fadeWidth to Color.Transparent
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(fadeWidth, size.height)
                                    )
                                }
                                if (canScrollEnd) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            fadeWidth to containerColor
                                        ),
                                        topLeft = Offset(size.width - fadeWidth, 0f),
                                        size = Size(fadeWidth, size.height)
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = safeSelectedIndex == index
                Column(
                    modifier = Modifier
                        .then(if (scrollable) Modifier.width(segmentWidth) else Modifier.weight(1f))
                        .heightIn(min = 54.dp)
                        .clip(segmentShape)
                        .then(
                            if (selected) {
                                Modifier
                                    .background(selectedSegmentFill, segmentShape)
                                    .border(1.dp, selectedSegmentBorder, segmentShape)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { if (it.isFocused) focusedIndex = index }
                        .tvClickable(shape = segmentShape) { onSelect(index) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (tab.platformId != null) {
                        KitsugiPlatformLogo(
                            platformId = tab.platformId,
                            size = 18.dp,
                            modifier = Modifier.alpha(if (selected) 1f else 0.72f)
                        )
                    } else {
                        val tabArt = KitsugiArt.fromVector(tab.icon)
                        if (tabArt != null) {
                            KitsugiArtIcon(art = tabArt, size = 21.dp, alpha = if (selected) 1f else 0.8f, accentGlow = selected)
                        } else {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) accentColor else KitsugiColors.TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.title,
                        color = if (selected) accentColor else KitsugiColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun KitsugiSettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    /** Verilirse [icon] adına bakılmadan bu 3B ikon çizilir. */
    art: KitsugiArt? = null
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            color = iconColor,
            art = art
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "›",
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun KitsugiSettingsSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Verilirse ikon yerine orijinal platform logosu (AniList, MAL, Simkl, ...) gösterilir. */
    platformId: String? = null,
    /** Verilirse [icon] adına bakılmadan bu 3B ikon çizilir. */
    art: KitsugiArt? = null
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused && enabled) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), enabled = enabled) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val finalIconColor = if (enabled) iconColor else KitsugiColors.TextMuted
        if (platformId != null) {
            KitsugiPlatformLogo(
                platformId = platformId,
                size = 42.dp,
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
            )
        } else {
            SettingsIcon(
                icon = icon,
                color = finalIconColor,
                art = art
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KitsugiColors.Background,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = KitsugiColors.TextSecondary,
                uncheckedTrackColor = KitsugiColors.SurfaceStrong
            )
        )
    }
}

/**
 * Uiverse (Galahhad) Sun/Moon animasyonlu tema geçiş butonu ile donatılmış ayar satırı.
 */
@Composable
fun KitsugiThemeModeSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Verilirse [icon] adına bakılmadan bu 3B ikon çizilir. */
    art: KitsugiArt? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val finalIconColor = if (enabled) iconColor else KitsugiColors.TextMuted
        SettingsIcon(
            icon = icon,
            color = finalIconColor,
            art = art
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        KitsugiThemeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}


@Composable
fun KitsugiSettingsListItem(
    title: String,
    description: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    /** Verilirse [icon] adına bakılmadan bu 3B ikon çizilir. */
    art: KitsugiArt? = null
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            color = iconColor,
            art = art
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title + description + value stacked in one column — no layout fight
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Value badge — accent-tinted pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "›",
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Açılır menünün çapa noktasından boşluğu. */
private val KitsugiMenuGap = 6.dp

/** Menünün ekran kenarına en az bırakılan boşluğu. */
private val KitsugiMenuScreenMargin = 8.dp

/**
 * Menü konumlandırıcı: Çapanın (tetikleyicinin) altına açar; alt tarafa sığmazsa üstüne
 * açar. Yatayda ekran kenarlarına taşmayacak şekilde sıkıştırır.
 */
private class KitsugiMenuPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)

        val below = anchorBounds.bottom + gapPx
        val maxY = windowSize.height - popupContentSize.height - marginPx
        val y = if (below <= maxY) {
            below
        } else {
            val above = anchorBounds.top - gapPx - popupContentSize.height
            if (above >= marginPx) above else maxY.coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

/**
 * Ortak tek-seçimli menü yüzeyi.
 *
 * Normal ekranlarda çapa bağlı, yay animasyonlu kompakt menü kullanılır. Ayarlar ağacında
 * [LocalKitsugiDropdownPresentation] değeri [KitsugiDropdownPresentation.SettingsDialog]
 * olduğu için aynı çağrı otomatik olarak ortalanmış Material seçim penceresine dönüşür.
 * Böylece arama/liste/detay ekranlarının kompakt menüleri etkilenmeden ayarlardaki tüm
 * seçimler AniHyou referansındaki modal davranışı paylaşır.
 */
@Composable
fun KitsugiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalKitsugiDropdownPresentation.current == KitsugiDropdownPresentation.SettingsDialog) {
        KitsugiSettingsDropdownDialog(
            expanded = expanded,
            title = dialogTitle ?: stringResource(R.string.ui_tv_select),
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content
        )
        return
    }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    // Kapanış animasyonu bitene kadar popup'ı canlı tut; sonra tamamen kaldır.
    if (!visibleState.currentState && !visibleState.targetState) return

    val density = LocalDensity.current
    val gapPx = with(density) { KitsugiMenuGap.roundToPx() }
    val marginPx = with(density) { KitsugiMenuScreenMargin.roundToPx() }
    val positionProvider = remember(gapPx, marginPx) {
        KitsugiMenuPositionProvider(gapPx = gapPx, marginPx = marginPx)
    }
    val shape = RoundedCornerShape(22.dp)
    val accentColor = LocalKitsugiAccent.current

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                scaleOut(
                    animationSpec = tween(durationMillis = 110),
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                )
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 18.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = accentColor.copy(alpha = 0.25f),
                        spotColor = accentColor.copy(alpha = 0.35f)
                    )
                    .clip(shape)
                    .background(KitsugiColors.SurfaceStrong, shape)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.55f),
                                accentColor.copy(alpha = 0.1f),
                                KitsugiColors.Border.copy(alpha = 0.35f)
                            )
                        ),
                        shape = shape
                    )
            ) {
                // Aurora üst ışık bandı — içeriğin arkasında kalır, tıklamayı engellemez.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                )
                Column(
                    modifier = modifier
                        .widthIn(min = 168.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Ayarlar ekranına özel, AniHyou referansındaki sade Material seçim penceresi.
 *
 * Çapa bağlı ve sayfa akışını örten büyük paneller yerine pencere ekranın ortasında açılır,
 * arkadaki ayar sayfasını karartır, seçenekleri radyo satırları olarak gösterir ve uzun
 * listeleri kendi içinde kaydırır. Telefon, tablet ve TV'de aynı görsel hiyerarşi korunur.
 */
@Composable
private fun KitsugiSettingsDropdownDialog(
    expanded: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    val accentColor = LocalKitsugiAccent.current
    val dialogShape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxHeight * 0.86f),
                shape = dialogShape,
                color = KitsugiColors.SurfaceStrong,
                tonalElevation = 6.dp,
                shadowElevation = 18.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 10.dp)
                    )

                    CompositionLocalProvider(LocalKitsugiDropdownUsesRadio provides true) {
                        Column(
                            modifier = modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            content = content
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(
                                text = stringResource(R.string.action_close),
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium açılır menü satırı. Seçili durum yay fiziğiyle canlanır:
 * solda gradyan seçim çubuğu büyür, zemin yarı saydam gradyan tona oturur ve
 * sağdaki onay işareti spring ile yerine seker. Layout kayması yaşanmaz
 * (çubuk her zaman yer kaplar, yalnızca görünürlüğü animasyonlanır).
 */
@Composable
fun KitsugiMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val accentColor = LocalKitsugiAccent.current
    val accentBrush = LocalKitsugiAccentBrush.current
    val shape = RoundedCornerShape(14.dp)

    if (LocalKitsugiDropdownUsesRadio.current) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .tvClickable(shape = shape, enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentColor,
                    unselectedColor = KitsugiColors.TextSecondary,
                    disabledSelectedColor = KitsugiColors.TextMuted,
                    disabledUnselectedColor = KitsugiColors.TextMuted
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides KitsugiColors.TextPrimary) {
                    text()
                }
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
        }
        return
    }

    val selectedBgBrush = accentBrushWithAlpha(0.16f)

    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "kitsugiMenuItemSelection"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .then(
                if (selectionProgress > 0.01f) {
                    Modifier.background(selectedBgBrush, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gradyan seçim çubuğu — seçimde dikeyde büyüyerek belirir.
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .graphicsLayer {
                    alpha = selectionProgress
                    scaleY = selectionProgress.coerceAtLeast(0.001f)
                }
                .clip(RoundedCornerShape(2.dp))
                .background(accentBrush, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))

        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            // Satır içeriğinin varsayılan rengi uygulama metin rengi olsun (Popup'ta tema rengi gelmez).
            CompositionLocalProvider(
                LocalContentColor provides (if (selected) accentColor else KitsugiColors.TextPrimary)
            ) {
                text()
            }
        }
        when {
            trailingIcon != null -> {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
            selected -> {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            alpha = selectionProgress
                            val checkScale = 0.5f + 0.5f * selectionProgress
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                )
            }
        }
    }
}

/**
 * Metin tabanlı açılır menü satırı. [selected] true ise accent renk + kalın yazı + onay işareti.
 */
@Composable
fun KitsugiDropdownItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    KitsugiMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        onClick = onClick,
        selected = selected
    )
}

@Composable
fun KitsugiSettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 62.dp)
            .background(KitsugiColors.Border)
    )
}

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    color: Color,
    art: KitsugiArt? = null
) {
    // Arka plan dairesi YOK: 3B görsel (varsa) doğrudan çizilir, saydam zeminlidir.
    // Eşleşen 3B ikon yoksa satır rengiyle boyanmış tek renkli glif gösterilir.
    KitsugiRowIcon(
        icon = icon,
        tint = color,
        art = art,
        artSize = 30.dp,
        glyphSize = 23.dp
    )
}