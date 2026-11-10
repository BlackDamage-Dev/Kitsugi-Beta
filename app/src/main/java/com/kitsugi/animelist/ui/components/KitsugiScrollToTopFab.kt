package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Icon
import com.kitsugi.animelist.ui.theme.gradient.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.onAccentColor
import com.kitsugi.animelist.utils.ScrollVisibilityState
import androidx.compose.ui.res.stringResource

/**
 * Tüm ekranlarda (Keşfet, Arama, Wiki okuyucu, detay sayfaları) kullanılan tek tip
 * "yukarı hızlı kaydırma" düğmesi.
 *
 * Tasarım: yuvarlak, gölgeli, accent renkli FAB; aşağı kaydırınca yumuşakça belirir,
 * tıklayınca en üste animasyonlu kaydırır. Alt bar görünür/gizli durumuna göre konumunu
 * otomatik ayarlar; böylece tek bir bileşen tüm ekranlarda tutarlı görünür.
 */
@Composable
fun KitsugiScrollToTopFab(
    visible: Boolean,
    isLandscape: Boolean,
    bottomBarScrollState: ScrollVisibilityState?,
    fallbackBottomBarVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Alt barın gerçek yüksekliği. Varsayılan 88dp, alt barda ekstra satır bulunan
     * ekranlarda (ör. manga okuyucudaki sayfa şeridi) düğme barın üstüne taşmasın diye
     * artırılabilir.
     */
    bottomBarHeight: Dp = 88.dp
) {
    val bottomBarVisible = bottomBarScrollState?.isVisible ?: fallbackBottomBarVisible
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding by animateDpAsState(
        targetValue = when {
            isLandscape -> 20.dp
            bottomBarVisible -> bottomBarHeight + navigationPadding
            else -> 24.dp + navigationPadding
        },
        animationSpec = tween(220),
        label = "scroll_to_top_bottom_padding"
    )
    val accent = LocalKitsugiAccent.current
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = tween(200),
        label = "scroll_to_top_scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.6f),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.6f),
        modifier = modifier.padding(end = 20.dp, bottom = bottomPadding)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = accent,
            contentColor = onAccentColor(accent),
            shape = CircleShape,
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Icon(
                imageVector = Icons.KeyboardArrowUp,
                contentDescription = stringResource(R.string.yukari_cik),
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
