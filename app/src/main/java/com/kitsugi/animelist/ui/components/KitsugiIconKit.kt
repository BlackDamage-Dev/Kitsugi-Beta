package com.kitsugi.animelist.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.icons.KitsugiIcons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * KITSUGI İKON KİTİ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uygulamadaki tüm tıklanır ikon alanları için ortak, tema uyumlu ve **animasyonlu**
 * davranışlar. İkonlar [KitsugiIcons] HD setinden gelir; hiçbiri arka plan çizmez
 * (arka plan yalnızca buton/alan tarafından, tema yüzeyiyle sağlanır).
 */

/** Basınca hafifçe küçülen, temaya duyarlı, arka plansız ikon butonu. */
@Composable
fun KitsugiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.55f),
        label = "kitsugiIconButtonPress"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.size(buttonSize)
    ) {
        Box(modifier = Modifier.size(buttonSize).scale(scale), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else KitsugiColors.TextMuted,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Açılır/kapanır alanların oku.
 *
 * Aynı ikon (aşağı bakan chevron) 180° dönerek açılma durumunu gösterir; böylece
 * `if (expanded) ExpandLess else ExpandMore` gibi iki ikon arasında geçiş yapan
 * sıçramalı görünüm yerine akıcı bir animasyon elde edilir.
 */
@Composable
fun KitsugiChevronIcon(
    expanded: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.75f),
        label = "kitsugiChevronRotation"
    )
    Icon(
        imageVector = KitsugiIcons.ChevronDown,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.rotate(rotation)
    )
}

/**
 * İki durumlu (ör. oynat/duraklat, favori ekle/çıkar, göster/gizle) ikon alanları.
 * Durum değişiminde ikon yumuşak bir geçişle değişir ve kısa bir "pop" yapar.
 */
@Composable
fun KitsugiStateIcon(
    active: Boolean,
    iconWhenInactive: ImageVector,
    iconWhenActive: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    activeTint: Color? = null
) {
    Crossfade(
        targetState = active,
        animationSpec = tween(durationMillis = KitsugiMotion.fastMillis),
        label = "kitsugiStateIcon"
    ) { isActive ->
        Icon(
            imageVector = if (isActive) iconWhenActive else iconWhenInactive,
            contentDescription = contentDescription,
            tint = if (isActive) activeTint ?: tint else tint,
            modifier = modifier
        )
    }
}
