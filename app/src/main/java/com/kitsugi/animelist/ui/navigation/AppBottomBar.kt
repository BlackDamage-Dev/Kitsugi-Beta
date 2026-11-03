package com.kitsugi.animelist.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

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
