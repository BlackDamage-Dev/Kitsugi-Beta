package com.kitsugi.animelist.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Arama sayfasının sağ üst köşesindeki aksiyon kümesi.
 *
 * Butonlar artık arama çubuğunun içinde/yanında değil; sayfa başlığıyla
 * ("Arama") aynı hizada, sağ üstte tek bir satırda durur. Keşfet, Listem ve
 * Profil sayfalarındaki sağ üst aksiyon düzeniyle birebir aynı görünüm
 * (38dp kare, 12dp köşe, SurfaceElevated zemin + ince kenarlık) kullanılır.
 * Bu sayede arama çubuğu da tam genişliğe kavuşur.
 */
@Composable
fun SearchTopActionBar(
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    showNotifications: Boolean,
    onOpenNotifications: () -> Unit,
    onOpenWatchHistory: (() -> Unit)?,
    onOpenPluginPicker: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchTopActionButton(
            icon = Icons.Tune,
            contentDescription = stringResource(R.string.filtreler),
            onClick = onFilterClick,
            isActive = isFilterActive,
            size = buttonSize
        )
        if (showNotifications) {
            SearchTopActionButton(
                icon = Icons.Notifications,
                contentDescription = stringResource(R.string.notif_title),
                onClick = onOpenNotifications,
                size = buttonSize
            )
        }
        if (onOpenWatchHistory != null) {
            SearchTopActionButton(
                icon = Icons.History,
                contentDescription = stringResource(R.string.stream_action_history),
                onClick = onOpenWatchHistory,
                size = buttonSize
            )
        }
        SearchTopActionButton(
            icon = Icons.Extension,
            contentDescription = stringResource(R.string.eklenti_portali),
            onClick = onOpenPluginPicker,
            size = buttonSize
        )
    }
}

@Composable
private fun SearchTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 38.dp,
    isActive: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceElevated,
                shape
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accentColor else KitsugiColors.SurfaceElevated.copy(alpha = 0.6f),
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) accentColor else KitsugiColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}
