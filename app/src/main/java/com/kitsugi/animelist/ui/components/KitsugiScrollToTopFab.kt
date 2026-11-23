package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.core.player.audio.player.KitsugiAudioPlayerManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.navigation.KitsugiBottomBarTokens
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.ScrollVisibilityState

/**
 * Tüm ekranlarda (Keşfet, Listem, Arama, Profil, Wiki okuyucu, detay ve kategori sayfaları)
 * kullanılan tek tip Keşfet tarzı koyu cam + vurgu çerçeveli "yukarı hızlı kaydırma" düğmesi.
 *
 * Yüzen alt bar ([AppBottomBar]) ile aynı animasyon eğrisi ve yüksekliğini paylaşır:
 * alt bar açıkken barın hemen üstüne hizalanır, aşağı kaydırılıp bar gizlendiğinde veya
 * yatay modda alt kenara yumuşakça yerleşir; gereksiz dikey boşluk bırakmaz.
 */
@Composable
fun KitsugiScrollToTopFab(
    visible: Boolean,
    isLandscape: Boolean,
    bottomBarScrollState: ScrollVisibilityState?,
    fallbackBottomBarVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = KitsugiBottomBarTokens.ExpandedControlOffset,
    contentDescriptionRes: Int = R.string.yukari_cik
) {
    val bottomBarVisible = bottomBarScrollState?.isVisible ?: fallbackBottomBarVisible
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = LocalContext.current
    val audioState by remember(context) { KitsugiAudioPlayerManager.getInstance(context).state }.collectAsState()
    val miniAudioClearance = if (audioState.isActive && audioState.currentItem != null && !audioState.isExpanded) 68.dp else 0.dp
    val isCompact = isLandscape || LocalConfiguration.current.screenWidthDp < 360
    val buttonSize = if (isCompact) {
        KitsugiBottomBarTokens.CompactFloatingControlSize
    } else {
        KitsugiBottomBarTokens.FloatingControlSize
    }

    val targetBottomOffset = when {
        isLandscape -> KitsugiBottomBarTokens.LandscapeControlOffset + navigationPadding + miniAudioClearance
        bottomBarVisible -> bottomBarHeight + navigationPadding + miniAudioClearance
        else -> KitsugiBottomBarTokens.CollapsedControlOffset + navigationPadding + miniAudioClearance
    }
    val bottomPadding by animateDpAsState(
        targetValue = targetBottomOffset,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "scroll_to_top_bottom_padding"
    )
    val accent = LocalKitsugiAccent.current
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.75f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "scroll_to_top_scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f),
        modifier = modifier.padding(
            end = KitsugiBottomBarTokens.FloatingControlEdgePadding,
            bottom = bottomPadding
        )
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            KitsugiColors.Surface.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = accent.copy(alpha = 0.62f),
                    shape = CircleShape
                )
                .tvClickable(shape = CircleShape, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.KeyboardArrowUp,
                contentDescription = stringResource(contentDescriptionRes),
                tint = accent,
                modifier = Modifier.size(if (isCompact) 20.dp else 22.dp)
            )
        }
    }
}

/**
 * Listem (`Tamamlandı` vb.) ve Profil (`Genel` vb.) ekranlarının sol altında beliren,
 * [KitsugiScrollToTopFab] ve yüzen alt bar ile birebir aynı yükseklik ve cam dilini
 * paylaşan kategori / durum seçici hap.
 */
@Composable
fun KitsugiFloatingFilterPill(
    visible: Boolean,
    label: String,
    icon: ImageVector,
    iconContentDescription: String?,
    isLandscape: Boolean,
    isBottomBarVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = LocalContext.current
    val audioState by remember(context) { KitsugiAudioPlayerManager.getInstance(context).state }.collectAsState()
    val miniAudioClearance = if (audioState.isActive && audioState.currentItem != null && !audioState.isExpanded) 68.dp else 0.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isCompact = isLandscape || screenWidthDp < 360
    val pillHeight = if (isCompact) {
        KitsugiBottomBarTokens.CompactFloatingControlSize
    } else {
        KitsugiBottomBarTokens.FloatingControlSize
    }
    val maxPillWidth = (screenWidthDp.dp - 96.dp).coerceAtLeast(120.dp)

    val bottomPadding by animateDpAsState(
        targetValue = KitsugiBottomBarTokens.floatingControlOffset(
            isBottomBarVisible = isBottomBarVisible,
            isLandscape = isLandscape
        ) + navigationPadding + miniAudioClearance,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "floating_filter_pill_bottom_padding"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f),
        modifier = modifier.padding(
            start = KitsugiBottomBarTokens.FloatingControlEdgePadding,
            bottom = bottomPadding
        )
    ) {
        Row(
            modifier = Modifier
                .height(pillHeight)
                .widthIn(max = maxPillWidth)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            KitsugiColors.Surface.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = accent.copy(alpha = 0.62f),
                    shape = CircleShape
                )
                .tvClickable(shape = CircleShape, onClick = onClick)
                .padding(horizontal = if (isCompact) 14.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = accent,
                modifier = Modifier.size(if (isCompact) 17.dp else 18.dp)
            )
            Text(
                text = label,
                color = KitsugiColors.TextPrimary,
                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * [LazyListState] tabanlı ekranlar için kolay sarmalayıcı (otomatik görünürlük eşiği + yumuşak tepeye dönüş).
 */
@Composable
fun KitsugiScrollToTopFab(
    listState: LazyListState,
    isBottomBarVisible: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    bottomBarScrollState: ScrollVisibilityState? = null,
    contentDescriptionRes: Int = R.string.yukari_cik,
    onAfterScrollToTop: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val showFab by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 ||
                (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 120)
        }
    }
    KitsugiScrollToTopFab(
        visible = showFab,
        isLandscape = isLandscape,
        bottomBarScrollState = bottomBarScrollState,
        fallbackBottomBarVisible = isBottomBarVisible,
        onClick = {
            scope.launch {
                listState.animateScrollToItem(0)
                onAfterScrollToTop?.invoke()
            }
        },
        modifier = modifier,
        contentDescriptionRes = contentDescriptionRes
    )
}

/**
 * [LazyGridState] tabanlı ızgara ekranları için kolay sarmalayıcı.
 */
@Composable
fun KitsugiScrollToTopFab(
    gridState: LazyGridState,
    isBottomBarVisible: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    bottomBarScrollState: ScrollVisibilityState? = null,
    contentDescriptionRes: Int = R.string.yukari_cik,
    onAfterScrollToTop: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val showFab by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 2 ||
                (gridState.firstVisibleItemIndex >= 1 && gridState.firstVisibleItemScrollOffset > 160)
        }
    }
    KitsugiScrollToTopFab(
        visible = showFab,
        isLandscape = isLandscape,
        bottomBarScrollState = bottomBarScrollState,
        fallbackBottomBarVisible = isBottomBarVisible,
        onClick = {
            scope.launch {
                gridState.animateScrollToItem(0)
                onAfterScrollToTop?.invoke()
            }
        },
        modifier = modifier,
        contentDescriptionRes = contentDescriptionRes
    )
}

