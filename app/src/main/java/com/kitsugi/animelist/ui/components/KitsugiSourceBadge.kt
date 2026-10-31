package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.toFriendlySourceLabel

/**
 * Platform kaynak rozeti — poster görselinin sol-alt köşesine yerleştirilmek üzere tasarlanır.
 * Harfler veya gereksiz arka plan kutusu olmadan, doğrudan platformun orijinal logosunu
 * rozet şekline (sol-alt ve sağ-üst köşe yuvarlatmalı) uyarlanmış olarak gösterir.
 *
 * Desteklenen kaynaklar: anilist, mal/jikan, tmdb, simkl, kitsu, shikimori, bangumi.
 * Bilinmeyen kaynaklar (örn. CS3 eklentileri) için aynı köşe rozeti içinde genel bir
 * eklenti simgesi gösterilir — böylece eklenti kartları da ana sayfa kartlarıyla
 * birebir aynı rozet dilini paylaşır.
 */
@Composable
fun KitsugiSourceBadge(
    source: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val normalized = source.lowercase()
    val badgeShape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 6.dp)

    if (KitsugiPlatformLogos.resFor(normalized) == null) {
        // Bilinmeyen kaynak (CS3 eklentisi) — genel eklenti rozeti
        Box(
            modifier = modifier
                .clip(badgeShape)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Extension,
                contentDescription = source,
                tint = LocalKitsugiAccent.current,
                modifier = Modifier.size(size * 0.62f)
            )
        }
        return
    }

    KitsugiPlatformLogo(
        platformId = normalized,
        modifier = modifier,
        size = size,
        shape = badgeShape
    )
}

/**
 * Kaynak adını GÖRSEL olarak gösteren ortak çip: platformun orijinal logosu +
 * dostça adı (örn. "Bangumi", "MyAnimeList", "Shikimori"). Vitrin (hero),
 * detay yükleme ekranı ve detay sayfaları dahil tüm kaynaklarda aynı biçimde
 * kullanılır — böylece her kaynakta isim+logo tutarlı şekilde görünür.
 *
 * Bilinmeyen kaynaklar (örn. CS3 eklentileri) için genel eklenti simgesi +
 * eklenti adı gösterilir; vitrin çipi hiçbir zaman boş kalmaz.
 */
@Composable
fun KitsugiSourceNamePill(
    source: String,
    modifier: Modifier = Modifier,
    logoSize: Dp = 17.dp
) {
    val normalized = source.trim().lowercase()
    val label = normalized.toFriendlySourceLabel()

    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.Background.copy(alpha = 0.72f))
            .border(1.dp, KitsugiColors.TextPrimary.copy(alpha = 0.16f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (KitsugiPlatformLogos.resFor(normalized) != null) {
            KitsugiPlatformLogo(platformId = normalized, size = logoSize)
        } else {
            Icon(
                imageVector = Icons.Extension,
                contentDescription = null,
                tint = LocalKitsugiAccent.current,
                modifier = Modifier.size(logoSize * 0.8f)
            )
        }
        Text(
            text = label,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
