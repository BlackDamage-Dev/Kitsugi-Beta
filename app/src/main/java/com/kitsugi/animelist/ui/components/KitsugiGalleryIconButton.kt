package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R

/**
 * Detay sayfasında galerinin tüm kaynaklardan **tamamen** yüklenip yüklenmediği.
 *
 * `true` iken galeri butonu animasyonlu yükleniyor durumunda kalır ve tıklanamaz. Sayfalar,
 * ellerinde hazır olan vitrin görselini isterlerse bu sırada geçici galeri önizlemesi olarak
 * açabilir. Sayfa kökünde `CompositionLocalProvider` ile sağlanır.
 */
val LocalKitsugiGalleryLoading = compositionLocalOf { false }

/**
 * Galeri ikon butonu.
 *
 * - Yükleniyor ([isLoading] = true): ikon nabız gibi yanıp söner, etrafında dönen bir halka
 *   görünür ve buton devre dışıdır (dokunulmaz).
 * - Hazır: ikon normal parlaklıkta, dokunulabilir.
 *
 * Mevcut bir hero/başlık kutusunun içinde kullanılmak üzere tasarlanmıştır (dış kutunun
 * arka planını kendisi çizmez).
 */
@Composable
fun KitsugiGalleryIconButton(
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isLoading: Boolean = LocalKitsugiGalleryLoading.current
) {
    val transition = rememberInfiniteTransition(label = "galleryLoadingTransition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "galleryLoadingPulse"
    )

    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Image,
                contentDescription = stringResource(R.string.action_gallery),
                tint = accentColor,
                modifier = Modifier.alpha(if (isLoading) pulseAlpha * 0.6f else 1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
