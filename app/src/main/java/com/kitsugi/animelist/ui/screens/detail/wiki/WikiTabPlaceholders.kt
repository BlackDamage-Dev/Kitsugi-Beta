package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Wiki sekmesi açılır açılmaz görünen iskelet: sekme artık çözümlemeyi beklemez,
 * içerik yerinde yüklenir (kayma/zıplama yok).
 */
@Composable
internal fun WikiTabSkeleton(accentColor: Color, modifier: Modifier = Modifier) {
    KitsugiShimmerProvider {
        val brush = LocalShimmerBrush.current ?: return@KitsugiShimmerProvider
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fandom_tab_loading),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Bilgi kutusu kartı iskeleti
            WikiSkeletonCard {
                Row {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.width(92.dp).height(126.dp),
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.7f).height(18.dp), 8.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.45f).height(12.dp), 8.dp)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                SkeletonFieldLine(brush)
                SkeletonFieldLine(brush)
                SkeletonFieldLine(brush)
                SkeletonFieldLine(brush)
            }

            // Özet kartı iskeleti
            WikiSkeletonCard {
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.35f).height(16.dp), 8.dp)
                Spacer(modifier = Modifier.height(10.dp))
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth().height(12.dp), 8.dp)
                Spacer(modifier = Modifier.height(6.dp))
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth().height(12.dp), 8.dp)
                Spacer(modifier = Modifier.height(6.dp))
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.8f).height(12.dp), 8.dp)
            }

            // İçindekiler kartı iskeleti
            WikiSkeletonCard {
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.3f).height(16.dp), 8.dp)
                Spacer(modifier = Modifier.height(10.dp))
                repeat(5) {
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.6f).height(14.dp), 8.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun WikiSkeletonCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SkeletonFieldLine(brush: androidx.compose.ui.graphics.Brush) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        KitsugiShimmerBlock(brush, Modifier.width(90.dp).height(12.dp), 8.dp)
        Spacer(modifier = Modifier.width(12.dp))
        KitsugiShimmerBlock(brush, Modifier.weight(1f).height(12.dp), 8.dp)
    }
}

/**
 * Wiki bulunamadığında sekmenin içinde kalan nazik boş durum: sekme belirip kaybolmaz,
 * kullanıcı neden boş olduğunu görür ve isterse yeniden dener.
 */
@Composable
internal fun WikiUnavailableCard(
    accentColor: Color,
    messageRes: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    titleRes: Int = R.string.fandom_unavailable_title
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(titleRes),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(messageRes),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(R.string.fandom_unavailable_hint),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.fandom_retry),
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
