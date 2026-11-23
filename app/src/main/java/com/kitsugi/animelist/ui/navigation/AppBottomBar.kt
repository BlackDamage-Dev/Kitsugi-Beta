package com.kitsugi.animelist.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlin.math.roundToInt

/**
 * NuvioMobile-inspired frosted glass floating pill navigation bar.
 *
 * - **Landscape / Tablet**: compact icon-only glass capsule positioned at TopCenter
 *   flanked by Explore's source selector & action cluster.
 * - **Portrait**: bottom-center floating glass capsule that smoothly collapses from
 *   icon+label to compact icon-only pill when scrolling down (unless `fixedNavBar` is on).
 */
@Composable
fun NuvioFloatingNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    overHero: Boolean = false,
    accentColor: Color = LocalKitsugiAccent.current,
) {
    val tabs = MainTab.entries
    val selectedIndex = remember(currentTab) { tabs.indexOf(currentTab).coerceAtLeast(0) }
    val showLabels = !isLandscape && !isCompact
    val isLightTheme = KitsugiColors.Background.luminance() > 0.5f
    val useDarkGlass = overHero || !isLightTheme
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    val barHeight by animateDpAsState(
        targetValue = if (showLabels) 60.dp else 46.dp,
        animationSpec = tween(durationMillis = 220),
        label = "nuvio_nav_height",
    )
    val maxBarWidth by animateDpAsState(
        targetValue = when {
            isLandscape && screenWidthDp < 840 -> 256.dp
            isLandscape -> 304.dp
            isCompact -> 292.dp
            else -> 396.dp
        },
        animationSpec = tween(durationMillis = 220),
        label = "nuvio_nav_width",
    )
    val horizontalOuterPadding by animateDpAsState(
        targetValue = when {
            isLandscape -> 0.dp
            isCompact -> 44.dp
            else -> 16.dp
        },
        animationSpec = tween(durationMillis = 220),
        label = "nuvio_nav_padding",
    )
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "nuvio_nav_indicator",
    )

    NuvioGlassPillSurface(
        modifier = modifier
            .padding(horizontal = horizontalOuterPadding)
            .widthIn(max = maxBarWidth)
            .fillMaxWidth()
            .height(barHeight),
        shape = CircleShape,
        overHero = overHero,
        shadowElevation = if (isLandscape) 10.dp else 12.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            val density = LocalDensity.current
            val tabWidth = maxWidth / tabs.size
            val indicatorOffsetPx = with(density) { (tabWidth.toPx() * animatedIndex).roundToInt() }

            // Spring-gliding Nuvio jelly highlight pill
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffsetPx, 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (useDarkGlass) 0.28f else 0.20f),
                                accentColor.copy(alpha = if (useDarkGlass) 0.16f else 0.11f),
                            )
                        )
                    )
                    .border(
                        width = 0.75.dp,
                        color = accentColor.copy(alpha = if (useDarkGlass) 0.42f else 0.34f),
                        shape = CircleShape,
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == currentTab
                    val label = stringResource(tab.labelRes)
                    val inactiveTint = if (useDarkGlass) {
                        Color.White.copy(alpha = 0.70f)
                    } else {
                        KitsugiColors.TextSecondary
                    }
                    val interactionSource = remember(tab) { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .semantics { selected = isSelected }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = if (isSelected) accentColor else inactiveTint,
                            modifier = Modifier.size(if (showLabels) 20.dp else 21.dp),
                        )
                        AnimatedVisibility(
                            visible = showLabels,
                            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else inactiveTint,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    accentColor: Color = LocalKitsugiAccent.current
) {
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = KitsugiColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(KitsugiColors.Border)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val label = stringResource(tab.labelRes)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .tvClickable(shape = RoundedCornerShape(16.dp)) {
                                onTabSelected(tab)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(30.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            brush = accentBrush,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = label,
                                tint = if (isSelected) onAccent else KitsugiColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = label,
                            color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Yatay ekranlarda (landscape) ekranın sol kenarına yerleşen ince dikey navigasyon rayı.
 * Alt barın yüksekliğini sıfıra indirerek içerik alanına dikeyde maksimum yer açar.
 */
@Composable
fun AppNavigationRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    accentColor: Color = LocalKitsugiAccent.current
) {
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp),
        color = KitsugiColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Üst: mini logo başlığı
                Text(
                    text = "Kitsugi",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Orta: ana sekmeler (Ayarlar hariç)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MainTab.entries
                        .filter { it != MainTab.Settings }
                        .forEach { tab ->
                            val isSelected = currentTab == tab
                            val label = stringResource(tab.labelRes)
                            androidx.compose.material3.NavigationRailItem(
                                selected = isSelected,
                                onClick = { onTabSelected(tab) },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(30.dp)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.background(
                                                        brush = accentBrush,
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = label,
                                            tint = if (isSelected) onAccent else KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                                    selectedIconColor = onAccent,
                                    selectedTextColor = accentColor,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = KitsugiColors.TextSecondary,
                                    unselectedTextColor = KitsugiColors.TextSecondary
                                )
                            )
                        }
                }

                // Alt: Ayarlar sekmesi
                val settingsTab = MainTab.Settings
                val isSettingsSelected = currentTab == settingsTab
                val settingsLabel = stringResource(settingsTab.labelRes)
                androidx.compose.material3.NavigationRailItem(
                    selected = isSettingsSelected,
                    onClick = { onTabSelected(settingsTab) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(30.dp)
                                .then(
                                    if (isSettingsSelected) {
                                        Modifier.background(
                                            brush = accentBrush,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = settingsTab.icon,
                                contentDescription = settingsLabel,
                                tint = if (isSettingsSelected) onAccent else KitsugiColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = settingsLabel,
                            color = if (isSettingsSelected) accentColor else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                        selectedIconColor = onAccent,
                        selectedTextColor = accentColor,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = KitsugiColors.TextSecondary,
                        unselectedTextColor = KitsugiColors.TextSecondary
                    )
                )
            }

            // Sağ kenar ayırıcı çizgisi
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(KitsugiColors.Border)
            )
        }
    }
}

/**
 * Shared geometry & scroll-clearance tokens for the floating bottom capsule bar,
 * landscape side rail, scroll-to-top FABs, and floating filter/category pills.
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

