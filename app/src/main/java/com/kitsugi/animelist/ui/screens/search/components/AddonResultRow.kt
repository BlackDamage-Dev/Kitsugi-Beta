package com.kitsugi.animelist.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.kitsugiNeonGlow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Eklenti (CloudStream) arama sonuç satırı.
 *
 * **Neden var:** Eklenti içi arama sonuçları 3'lü poster ızgarası olarak
 * çiziliyordu; ana arama sayfası ise tek sütun, yatay ve kompakt
 * [com.kitsugi.animelist.ui.screens.search.SearchResultRow] satırlarını
 * kullanıyor. İki ekran arasındaki bu görünüm farkı dikey/yatay geçişlerde
 * kullanılabilirliği bozuyordu (ızgarada yatay modda kartlar aşırı geniş,
 * dikeyde aşırı dar kalıyordu).
 *
 * Bu bileşen ana aramadaki satırın birebir aynı ölçülerini kullanır:
 *  - 56×80 dp poster, 10 dp köşe yarıçapı
 *  - 16 dp köşe yarıçaplı `Surface` satır + neon glow
 *  - Başlık 2 satır, alt bilgi 1 satır, chip şeridi
 * Böylece eklenti arama sayfası ana arama sayfasıyla **görsel olarak aynı**
 * hizada akar ve her iki yönde de aynı şekilde okunur.
 */
@Composable
fun AddonResultRow(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    apiName: String,
    qualityLabel: String?,
    year: Int? = null,
    typeLabel: String? = null,
    isAdult: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current
    val rowShape = remember { RoundedCornerShape(16.dp) }

    // Eklenti posterleri çoğu zaman Referer ister (Türk siteleri sıcak bağlantı
    // koruması uygular). Ana aramadaki kartlarla aynı davranış için başlık eklenir.
    val posterModel = remember(imageUrl) {
        val url = imageUrl
        if (url.isNullOrBlank()) {
            null
        } else {
            runCatching {
                val referer = try {
                    val uri = android.net.Uri.parse(url)
                    "${uri.scheme}://${uri.host}/"
                } catch (_: Exception) {
                    url
                }
                val headers = NetworkHeaders.Builder()
                    .set("Referer", referer)
                    .set("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                coil3.request.ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(headers)
                    .build()
            }.getOrNull() ?: url
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(rowShape)
            .clip(rowShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = rowShape, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Poster (ana aramadaki ile aynı ölçü) ──────────────────────────
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceStrong),
        ) {
            KitsugiNsfwImage(
                model = posterModel,
                contentDescription = title,
                isAdult = isAdult,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // ── Bilgiler ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!qualityLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qualityLabel,
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (year != null) {
                    Text(
                        text = "$year",
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (!typeLabel.isNullOrBlank()) {
                    Text(
                        text = typeLabel,
                        color = accentColor.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Kaynak rozeti (ana aramadaki "kaynak" çipiyle aynı görünüm)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = apiName,
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
