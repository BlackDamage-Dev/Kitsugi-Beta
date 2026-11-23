package com.kitsugi.animelist.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Shared geometry & scroll-clearance tokens for the floating bottom capsule bar,
 * landscape side rail, scroll-to-top FABs, and floating filter/category pills.
 *
 * Centralizing these metrics guarantees every screen's floating controls
 * (`^` scroll-to-top, Listem status pill, Profile category pill, snackbars)
 * sit on the exact same horizontal & vertical baseline in both expanded and
 * scroll-collapsed states without dead gaps or overlap.
 */
object KitsugiBottomBarTokens {
    val PillHeight: Dp = 58.dp
    val CompactPillHeight: Dp = 52.dp
    val BottomMargin: Dp = 10.dp
    val HorizontalMargin: Dp = 14.dp
    val CompactHorizontalMargin: Dp = 8.dp
    val MaxPillWidth: Dp = 460.dp

    /** Unified size for circular scroll-to-top FABs & height of floating bottom pills. */
    val FloatingControlSize: Dp = 44.dp
    val CompactFloatingControlSize: Dp = 40.dp
    val FloatingControlEdgePadding: Dp = 16.dp

    /** Distance from navigation-bar inset to bottom of floating controls when bar is visible. */
    val ExpandedControlOffset: Dp = 76.dp
    /** Distance from navigation-bar inset to bottom of floating controls when bar is hidden. */
    val CollapsedControlOffset: Dp = 14.dp
    /** Distance from navigation-bar inset in landscape (side rail active, no bottom bar). */
    val LandscapeControlOffset: Dp = 14.dp

    /** Standard list bottom tail spacer so the last row clears floating controls cleanly. */
    val PortraitListBottomSpacer: Dp = 96.dp
    val LandscapeListBottomSpacer: Dp = 32.dp

    fun floatingControlOffset(
        isBottomBarVisible: Boolean,
        isLandscape: Boolean,
        customBarHeight: Dp? = null
    ): Dp = when {
        isLandscape -> LandscapeControlOffset
        isBottomBarVisible -> customBarHeight ?: ExpandedControlOffset
        else -> CollapsedControlOffset
    }
}

@Composable
fun AppBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    accentColor: Color = LocalKitsugiAccent.current
) {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp
    val isUltraNarrow = screenWidthDp < 315
    val isNarrow = screenWidthDp < 365

    val barHeight = if (isNarrow) KitsugiBottomBarTokens.CompactPillHeight else KitsugiBottomBarTokens.PillHeight
    val horizontalMargin = if (isNarrow) KitsugiBottomBarTokens.CompactHorizontalMargin else KitsugiBottomBarTokens.HorizontalMargin
    val capsuleShape = CircleShape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = horizontalMargin,
                end = horizontalMargin,
                bottom = KitsugiBottomBarTokens.BottomMargin,
                top = 2.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = KitsugiBottomBarTokens.MaxPillWidth)
                .fillMaxWidth()
                .height(barHeight)
                .shadow(
                    elevation = 14.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .border(
                    width = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.78f),
                    shape = capsuleShape
                ),
            shape = capsuleShape,
            color = KitsugiColors.Surface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isNarrow) 4.dp else 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 2.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val label = stringResource(tab.labelRes)
                    val itemShape = CircleShape
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "bottom_nav_item_bg"
                    )
                    val pillBorder by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.40f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "bottom_nav_item_border"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(itemShape)
                            .background(pillBg)
                            .border(width = 1.dp, color = pillBorder, shape = itemShape)
                            .tvClickable(shape = itemShape) {
                                onTabSelected(tab)
                            }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(if (isNarrow) 18.dp else 20.dp)
                        )

                        if (!isUltraNarrow) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isNarrow) 9.5.sp else 10.5.sp,
                                    lineHeight = if (isNarrow) 11.sp else 12.sp
                                ),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Landscape side navigation rail with zero vertical header waste.
 *
 * Adapts cleanly to both wide tablets and compact/narrow phone landscape heights:
 * - Respects start display-cutout + navigation bar insets so camera notches never overlap icons.
 * - Uses compact glass-pill indicators matching [AppBottomBar] and Keşfet's button language.
 * - Scrolls vertically when window height is ultra-short (split-screen / compact landscape)
 *   and hides text labels below 350dp height so all 5 tabs stay reachable without clipping.
 */
@Composable
fun AppNavigationRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    accentColor: Color = LocalKitsugiAccent.current
) {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp
    val screenHeightDp = config.screenHeightDp
    val showLabels = screenHeightDp >= 360
    val railWidth = when {
        screenWidthDp < 560 -> 56.dp
        screenWidthDp >= 840 -> 72.dp
        else -> 64.dp
    }
    val startInsets = WindowInsets.navigationBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(startInsets),
        color = KitsugiColors.Surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp, horizontal = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val label = stringResource(tab.labelRes)
                    val itemShape = RoundedCornerShape(16.dp)
                    val itemBg by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "rail_nav_item_bg"
                    )
                    val itemBorder by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.42f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "rail_nav_item_border"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(itemBg)
                            .border(width = 1.dp, color = itemBorder, shape = itemShape)
                            .tvClickable(shape = itemShape) {
                                onTabSelected(tab)
                            }
                            .padding(
                                horizontal = 4.dp,
                                vertical = if (showLabels) 7.dp else 9.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (showLabels) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp
                                ),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(KitsugiColors.Border.copy(alpha = 0.7f))
            )
        }
    }
}
