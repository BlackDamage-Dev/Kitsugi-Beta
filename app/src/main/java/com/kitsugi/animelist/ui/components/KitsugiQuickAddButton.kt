package com.kitsugi.animelist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Posterin sağ üst köşesine **yapışık** duran kompakt "listeye ekle" kısayolu.
 *
 * Tasarım dili kartlardaki diğer köşe rozetleriyle (sıra etiketi `#1`, kaynak logosu,
 * durum rozeti) aynı: tek köşesi yuvarlatılmış, postere sıfır boşlukla oturan cam
 * yüzey. Böylece posterin üstüne "yapıştırılmış" bir daire gibi durmaz; kartın
 * kendi kromunun parçası gibi okunur ve altındaki görseli gereksiz kapatmaz.
 *
 * Durum ayrımı bilinçli olarak sessiz: kullanıcı listesi kalabalıkken onlarca kartta
 * yanan yeşil bloklar görmek yerine yalnızca ince bir yeşil çerçeve + tik görür.
 * (Durum rozeti zaten yeşil dolgulu; iki güçlü yeşil öğe kartı boğuyordu.)
 *
 * - Listede değil → füme cam zemin + beyaz `+`
 * - Listede → aynı cam zemin + yeşil çerçeve + yeşil tik
 *
 * Çağrı yerlerinde `Modifier.align(Alignment.TopEnd)` ile, **padding vermeden**
 * kullanılmalıdır; yuvarlatılan köşe `bottomStart` olduğu için posterin üst-sağ
 * köşesine oturur (posterin kendi `clip`'i üst-sağ köşeyi kart yarıçapıyla kıvar).
 *
 * @param size rozetin kenar uzunluğu (liste kartlarında 24-26dp, grid posterinde 28dp)
 * @param cornerRadius posterin içine bakan (alt-sol) köşenin yarıçapı
 */
@Composable
fun KitsugiQuickAddButton(
    inList: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    cornerRadius: Dp = 10.dp
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(bottomStart = cornerRadius) }

    // Liste durumundaki geçiş sert olmasın: katman/kontur/tint birlikte kıvılsın.
    val scrimColor by animateColorAsState(
        targetValue = if (inList) {
            KitsugiColors.AccentGreen.copy(alpha = 0.20f)
        } else {
            Color.Black.copy(alpha = 0.52f)
        },
        label = "quickAddScrim"
    )
    val borderColor by animateColorAsState(
        targetValue = if (inList) {
            KitsugiColors.AccentGreen.copy(alpha = 0.62f)
        } else {
            Color.White.copy(alpha = 0.16f)
        },
        label = "quickAddBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (inList) KitsugiColors.AccentGreen else Color.White,
        label = "quickAddIcon"
    )

    val description = if (inList) {
        stringResource(R.string.duzenle)
    } else {
        stringResource(R.string.listeye_ekle)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(scrimColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .tvClickable(
                shape = shape,
                // Posterin köşesine yapışık olduğu için büyüme kırpılıyordu:
                // TV'de yalnızca odak konturuyla geri bildirim ver.
                scaleOnFocus = false,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (inList) Icons.Check else Icons.Add,
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}
