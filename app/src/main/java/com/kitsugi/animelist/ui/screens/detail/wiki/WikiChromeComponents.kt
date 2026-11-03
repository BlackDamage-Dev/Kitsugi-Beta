package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Wiki okuyucusunun üst şeridi için ortak, kompakt yüzey parçaları.
 *
 * 2026-10-21 (ikinci tur): kullanıcı "hepsini üç noktaya taşı, sadece sekme kısmı kalsın"
 * dedi. Görünür segment denetimi ve araç pill'leri kaldırıldı; okuyucuda kalan tek kontrol
 * grubu sekme şeridi ve sağ köşedeki `⋮`. Bu dosyada yalnızca iki küçük yapı taşı kaldı:
 * köşedeki ikon düğmesi ve ince ayırıcı çizgi.
 *
 * Tasarım kuralları:
 *  - Dokunma hedefi 34 dp daire: Material `IconButton`'ın 48 dp'lik hedefi kompakt şeridi
 *    şişiriyordu.
 *  - Yumuşak renk geçişleri (yalnızca gerektiği yerde); hiçbir parça kendi dış boşluğunu
 *    taşımaz, boşlukları çağıran taraf verir.
 */

/** Üst şeritteki tek ikonluk eylem (menü, kapat…). */
@Composable
internal fun WikiIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .tvClickable(enabled = enabled, shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Üst şerit bölümleri arasındaki 1 dp'lik ayırıcı. */
@Composable
internal fun WikiChromeHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KitsugiColors.Border.copy(alpha = 0.5f))
    )
}
