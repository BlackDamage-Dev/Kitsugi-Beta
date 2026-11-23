package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.icons.KitsugiIcons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * KITSUGI ORTAK ÜST ÇUBUK & KEŞFET TARZI CAM BUTON SİSTEMİ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uygulamadaki tüm ekranlarda (Keşfet, Listem, Arama, Profil, Ayarlar, Detay,
 * Tam Ekran Kategori vb.) üst bar butonları, kaynak seçici kapsüller ve satır
 * içi araç ikonları Keşfet sayfasının koyu cam dairesel tasarım dilini ve
 * kanonik konumlandırmasını paylaşır.
 *
 * Sıra kuralı (soldan sağa):
 *   Ekran içi aksiyonlar (Filtre, Arama, Yenile, Takvim, Eklentiler, İstatistik…)
 *   → Ortak küresel aksiyonlar: Geçmiş → İndirmeler → Rastgele → **Bildirim (EN SAĞDA)**
 *
 * Bu sayede paylaşılan küresel butonlar (Geçmiş, İndirmeler, Bildirim) her sekmede
 * hem dikey hem yatay modda birebir aynı sağ üst hizada kalır; dar ekranlarda ise
 * sığmayan sol aksiyonlar otomatik olarak kompakt/taşma (•••) menüsüne katlanır.
 */
enum class KitsugiTopAction(
    val icon: ImageVector,
    /** Kanonik (soldan sağa) sıra. Büyük değer daha sağda. */
    val order: Int,
    /** Seçili/aktif durumda kullanılacak dolu varyant (varsa). */
    val selectedIcon: ImageVector? = null
) {
    FILTER(KitsugiIcons.SlidersHorizontal, order = 10),
    SEARCH(KitsugiIcons.Search, order = 15),
    REFRESH(KitsugiIcons.RefreshCw, order = 20),
    CALENDAR(KitsugiIcons.CalendarDays, order = 22),
    EXTENSIONS(KitsugiIcons.Puzzle, order = 24),
    STATS(KitsugiIcons.ChartColumn, order = 26),
    SHARE(KitsugiIcons.Share2, order = 70),
    FAVORITE(KitsugiIcons.Heart, order = 75, selectedIcon = KitsugiIcons.HeartSolid),
    GALLERY(KitsugiIcons.Image, order = 80),
    FOLDER(KitsugiIcons.FolderOpen, order = 85),
    DIAGNOSTICS(KitsugiIcons.Bug, order = 88),
    CLEAR(KitsugiIcons.Eraser, order = 89),
    HISTORY(KitsugiIcons.ClockArrowUp, order = 91),
    DOWNLOADS(KitsugiIcons.Download, order = 92),
    RANDOM(KitsugiIcons.Dices, order = 95),

    /** Her zaman en sağda durur. */
    NOTIFICATIONS(KitsugiIcons.Bell, order = 100);

    companion object {
        /** Verilen aksiyonları kanonik sıraya göre dizer (bildirim daima en sağda). */
        fun canonicalOrder(actions: Collection<KitsugiTopAction>): List<KitsugiTopAction> =
            actions.distinct().sortedBy { it.order }
    }
}

/** Üst çubuk butonlarının ve ekran başlıklarının ortak Keşfet ölçüleri. */
object KitsugiTopActionTokens {
    val ButtonSize: Dp = 38.dp
    val IconSize: Dp = 19.dp
    val Spacing: Dp = 6.dp
    /** Keşfet sayfasındaki tam dairesel koyu cam buton geometrisi. */
    val CornerRadius: Dp = 999.dp

    /** Dar ekranlar ve yoğun araç satırları için kompakt dairesel ölçü. */
    val CompactButtonSize: Dp = 34.dp
    val CompactIconSize: Dp = 17.dp
    val CompactSpacing: Dp = 4.dp
    val CompactCornerRadius: Dp = 999.dp

    /** Keşfet (`MobileExploreEdgePadding = 13.dp`) ile birebir aynı ekran kenar boşluğu. */
    val HeaderHorizontalPadding: Dp = 13.dp
    val HeaderTopPaddingPortrait: Dp = 6.dp
    val HeaderTopPaddingLandscape: Dp = 4.dp
    val HeaderRowMinHeight: Dp = 42.dp
    val HeaderRowMinHeightLandscape: Dp = 38.dp

    /** Ortak koyu cam kapsül/buton zemin ve kenarlık renkleri. */
    val ContainerColor: Color
        @Composable get() = KitsugiColors.Surface.copy(alpha = 0.92f)
    val BorderColor: Color
        @Composable get() = KitsugiColors.TextMuted.copy(alpha = 0.34f)
}

/** Aksiyonun varsayılan erişilebilirlik etiketi. */
@Composable
fun KitsugiTopAction.defaultLabel(): String = when (this) {
    KitsugiTopAction.FILTER -> stringResource(R.string.filtreler)
    KitsugiTopAction.SEARCH -> stringResource(R.string.stream_action_manual_search)
    KitsugiTopAction.REFRESH -> stringResource(R.string.yenile)
    KitsugiTopAction.CALENDAR -> stringResource(R.string.yayin_takvimi)
    KitsugiTopAction.HISTORY -> stringResource(R.string.stream_action_history)
    KitsugiTopAction.DOWNLOADS -> stringResource(R.string.stream_action_downloads)
    KitsugiTopAction.EXTENSIONS -> stringResource(R.string.eklenti_portali)
    KitsugiTopAction.STATS -> stringResource(R.string.kutuphane_istatistikleri)
    KitsugiTopAction.SHARE -> stringResource(R.string.paylas)
    KitsugiTopAction.FAVORITE -> stringResource(R.string.action_favourite_add)
    KitsugiTopAction.GALLERY -> stringResource(R.string.action_gallery)
    KitsugiTopAction.FOLDER -> stringResource(R.string.indirme_klasorunu_ac)
    KitsugiTopAction.DIAGNOSTICS -> stringResource(R.string.notif_diag_action)
    KitsugiTopAction.CLEAR -> stringResource(R.string.tumunu_temizle)
    KitsugiTopAction.RANDOM -> stringResource(R.string.top_action_random)
    KitsugiTopAction.NOTIFICATIONS -> stringResource(R.string.notif_title)
}

/**
 * Keşfet tarzı genel amaçlı koyu cam dairesel ikon butonu.
 *
 * Geri (`<-`), arama, filtre, sıralama, görünüm değiştirme, senkronizasyon, menü (`•••`)
 * gibi tüm ekran içi aksiyonların Keşfet üst barıyla birebir aynı görsel dilde çizilmesini sağlar.
 */
@Composable
fun KitsugiIconGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current,
    size: Dp = KitsugiTopActionTokens.ButtonSize,
    cornerRadius: Dp = KitsugiTopActionTokens.CornerRadius,
    iconSize: Dp = KitsugiTopActionTokens.IconSize,
    iconTint: Color? = null,
    showBadge: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val shape = if (cornerRadius >= 900.dp) CircleShape else RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.6f),
        label = "kitsugiGlassBtnPress"
    )
    val surface = KitsugiColors.Surface
    val borderColor = if (selected) {
        accentColor.copy(alpha = 0.62f)
    } else {
        KitsugiColors.TextMuted.copy(alpha = 0.34f)
    }
    val resolvedTint = iconTint ?: if (selected) accentColor else KitsugiColors.TextPrimary

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = if (selected) 0.24f else 0.08f),
                        surface.copy(alpha = 0.92f)
                    )
                )
            )
            .border(width = 1.dp, color = borderColor, shape = shape)
            .tvClickable(
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) resolvedTint else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize)
        )

        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-5).dp, y = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(1.5.dp, surface, CircleShape)
            )
        }
    }
}

/**
 * Tek bir üst çubuk aksiyon butonu (Keşfet koyu cam dairesel tasarım).
 */
@Composable
fun KitsugiTopActionButton(
    action: KitsugiTopAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accentColor: Color = LocalKitsugiAccent.current,
    size: Dp = KitsugiTopActionTokens.ButtonSize,
    cornerRadius: Dp = KitsugiTopActionTokens.CornerRadius,
    iconSize: Dp = KitsugiTopActionTokens.IconSize,
    showBadge: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    KitsugiIconGlassButton(
        icon = if (selected) action.selectedIcon ?: action.icon else action.icon,
        contentDescription = contentDescription ?: action.defaultLabel(),
        onClick = onClick,
        modifier = modifier,
        accentColor = accentColor,
        size = size,
        cornerRadius = cornerRadius,
        iconSize = iconSize,
        showBadge = showBadge,
        enabled = enabled,
        selected = selected
    )
}

/**
 * Sağa hizalı **ortak** üst çubuk aksiyon satırı.
 *
 * - Sıralama [KitsugiTopAction.order] ile garanti edilir: paylaşılan küresel aksiyonlar
 *   her ekranda aynı sağ üst konumda durur, bildirim daima en sağdadır.
 * - Dar ekranlarda (`< 375dp` genişlik veya yoğun buton sayısında) butonlar otomatik olarak
 *   kompakt ölçüye iner; çok dar ekranlarda (`< 345dp`) en sağdaki birincil küresel butonlar
 *   sabit tutulurken taşan sol aksiyonlar Keşfet tarzı `•••` menüsüne toplanır.
 */
@Composable
fun KitsugiTopActionRow(
    actions: Collection<KitsugiTopAction>,
    onActionClick: (KitsugiTopAction) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current,
    size: Dp = KitsugiTopActionTokens.ButtonSize,
    cornerRadius: Dp = KitsugiTopActionTokens.CornerRadius,
    spacing: Dp = KitsugiTopActionTokens.Spacing,
    notificationBadge: Boolean = false,
    selectedActions: Set<KitsugiTopAction> = emptySet(),
    enabledActions: Set<KitsugiTopAction> = KitsugiTopAction.entries.toSet(),
    maxVisibleActions: Int? = null
) {
    val ordered = remember(actions) { KitsugiTopAction.canonicalOrder(actions) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isNarrow = screenWidthDp < 375 && ordered.size >= 4

    val effectiveSize = if (isNarrow && size == KitsugiTopActionTokens.ButtonSize) {
        KitsugiTopActionTokens.CompactButtonSize
    } else {
        size
    }
    val effectiveIconSize = if (effectiveSize <= KitsugiTopActionTokens.CompactButtonSize) {
        KitsugiTopActionTokens.CompactIconSize
    } else {
        KitsugiTopActionTokens.IconSize
    }
    val effectiveSpacing = if (isNarrow && spacing == KitsugiTopActionTokens.Spacing) {
        KitsugiTopActionTokens.CompactSpacing
    } else {
        spacing
    }

    val autoCap = when {
        maxVisibleActions != null -> maxVisibleActions
        screenWidthDp < 315 -> 2
        screenWidthDp < 345 -> 3
        else -> Int.MAX_VALUE
    }

    val needsOverflow = ordered.size > autoCap && autoCap >= 2
    // Keep rightmost (highest-order, global muscle-memory) actions inline; fold leading ones into overflow.
    val keepInlineCount = if (needsOverflow) (autoCap - 1).coerceAtLeast(1) else ordered.size
    val overflowActions = if (needsOverflow) ordered.dropLast(keepInlineCount) else emptyList()
    val inlineActions = if (needsOverflow) ordered.takeLast(keepInlineCount) else ordered

    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(effectiveSpacing)
    ) {
        if (overflowActions.isNotEmpty()) {
            Box {
                val hasSelectedInOverflow = overflowActions.any { it in selectedActions }
                KitsugiIconGlassButton(
                    icon = KitsugiIcons.MoreVert,
                    contentDescription = stringResource(R.string.daha_fazla),
                    onClick = { overflowMenuExpanded = true },
                    accentColor = accentColor,
                    size = effectiveSize,
                    cornerRadius = cornerRadius,
                    iconSize = effectiveIconSize,
                    selected = hasSelectedInOverflow
                )
                DropdownMenu(
                    expanded = overflowMenuExpanded,
                    onDismissRequest = { overflowMenuExpanded = false },
                    modifier = Modifier.background(KitsugiColors.SurfaceElevated)
                ) {
                    overflowActions.forEach { action ->
                        val isActionEnabled = action in enabledActions
                        val isActionSelected = action in selectedActions
                        DropdownMenuItem(
                            enabled = isActionEnabled,
                            text = {
                                Text(
                                    text = action.defaultLabel(),
                                    color = if (isActionSelected) accentColor else KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActionSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isActionSelected) action.selectedIcon ?: action.icon else action.icon,
                                    contentDescription = null,
                                    tint = if (isActionSelected) accentColor else KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                onActionClick(action)
                            }
                        )
                    }
                }
            }
        }

        inlineActions.forEach { action ->
            KitsugiTopActionButton(
                action = action,
                onClick = { onActionClick(action) },
                accentColor = accentColor,
                size = effectiveSize,
                cornerRadius = cornerRadius,
                iconSize = effectiveIconSize,
                showBadge = notificationBadge && action == KitsugiTopAction.NOTIFICATIONS,
                selected = action in selectedActions,
                enabled = action in enabledActions
            )
        }
    }
}

/**
 * Keşfet sayfasındaki kaynak seçici hap ile birebir aynı kapsül seçici bileşen.
 *
 * Keşfet, Listem, Arama ve Profil ekranlarında kaynak/motor seçimini aynı görsel
 * çerçeve, tipografi ve dokunma alanında sunar.
 */
@Composable
fun KitsugiSourceSelectorPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    platformId: String? = null,
    contentDescription: String? = null,
    isConnectedDotVisible: Boolean = false,
    compact: Boolean = false,
    accentColor: Color = LocalKitsugiAccent.current,
    borderColor: Color = KitsugiColors.TextMuted.copy(alpha = 0.38f)
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isTight = compact || screenWidthDp < 350
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 34.dp else 38.dp)
            .widthIn(min = if (compact) 42.dp else 48.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.08f),
                        KitsugiColors.Surface.copy(alpha = 0.94f)
                    )
                )
            )
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .tvClickable(shape = CircleShape, onClick = onClick)
            .padding(
                start = if (platformId != null) 10.dp else 13.dp,
                end = if (isTight) 7.dp else 9.dp,
                top = if (compact) 5.dp else 6.dp,
                bottom = if (compact) 5.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTight) 5.dp else 6.dp)
    ) {
        if (platformId != null) {
            KitsugiPlatformLogo(
                platformId = platformId,
                size = if (compact) 16.dp else 18.dp,
                fallbackTint = accentColor
            )
        }
        if (!compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (isTight) 12.5.sp else 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isConnectedDotVisible) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.AccentGreen)
            )
        }
        Icon(
            imageVector = KitsugiIcons.ArrowDropDown,
            contentDescription = contentDescription,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
        )
    }
}

/**
 * Detay sayfalarının sağ üst aksiyonları (Paylaş · Favori · Galeri).
 *
 * Hem hero üzerinde hem de kaydırılınca açılan yapışkan (sticky) başlıkta birebir
 * aynı sıra ve dairesel Keşfet cam buton stilinde çizilir.
 */
@Composable
fun KitsugiDetailHeaderActions(
    onShareClick: () -> Unit,
    accentColor: Color = LocalKitsugiAccent.current,
    showFavourite: Boolean = false,
    isFavourite: Boolean = false,
    onFavouriteClick: (() -> Unit)? = null,
    showGallery: Boolean = false,
    galleryLoading: Boolean = false,
    onGalleryClick: (() -> Unit)? = null
) {
    val isNarrow = LocalConfiguration.current.screenWidthDp < 360
    val btnSize = if (isNarrow) KitsugiTopActionTokens.CompactButtonSize else KitsugiTopActionTokens.ButtonSize
    val iconSz = if (isNarrow) KitsugiTopActionTokens.CompactIconSize else KitsugiTopActionTokens.IconSize
    val gap = if (isNarrow) KitsugiTopActionTokens.CompactSpacing else KitsugiTopActionTokens.Spacing

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        KitsugiTopActionButton(
            action = KitsugiTopAction.SHARE,
            onClick = onShareClick,
            accentColor = accentColor,
            size = btnSize,
            iconSize = iconSz
        )
        if (showFavourite && onFavouriteClick != null) {
            KitsugiTopActionButton(
                action = KitsugiTopAction.FAVORITE,
                onClick = onFavouriteClick,
                contentDescription = stringResource(
                    if (isFavourite) R.string.action_favourite_remove else R.string.action_favourite_add
                ),
                accentColor = accentColor,
                size = btnSize,
                iconSize = iconSz,
                selected = isFavourite
            )
        }
        if (showGallery && onGalleryClick != null) {
            KitsugiTopActionButton(
                action = KitsugiTopAction.GALLERY,
                onClick = onGalleryClick,
                accentColor = accentColor,
                size = btnSize,
                iconSize = iconSz,
                enabled = !galleryLoading,
                selected = !galleryLoading
            )
        }
    }
}
