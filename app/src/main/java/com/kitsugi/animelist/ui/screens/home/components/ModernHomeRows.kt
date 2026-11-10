package com.kitsugi.animelist.ui.screens.home.components

import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * V2-D03 – ModernHomeRows
 *
 * Ana ekran için TMDB / Jikan içeriklerini gösteren modern yatay satır bileşeni.
 * - Shimmer yükleme desteği
 * - Poster + başlık + puan overlay içeren kart
 * - "Tümünü Gör" butonu
 * - LazyRow ile performanslı scroll
 */
@Composable
fun ModernHomeRows(
    title: String,
    results: List<JikanSearchResult>,
    isLoading: Boolean = false,
    onItemClick: (JikanSearchResult) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    cardWidth: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Başlık + Tümünü Gör
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onSeeAllClick != null && (results.isNotEmpty() || isLoading)) {
                TextButton(onClick = onSeeAllClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        text = stringResource(R.string.tumunu_gor),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Keep the row state alive while the placeholder replaces its content.
        val rowState = androidx.compose.runtime.saveable.rememberSaveable(title, saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
        when {
            isLoading && results.isEmpty() -> {
                // Match the poster-only cards used by this shelf, including custom widths.
                com.kitsugi.animelist.ui.components.KitsugiShimmerProvider {
                    val brush = com.kitsugi.animelist.ui.components.LocalShimmerBrush.current ?: com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush()
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(5) {
                            Box(Modifier.size(width = cardWidth, height = cardWidth * 1.5f)) {
                                com.kitsugi.animelist.ui.components.KitsugiShimmerBlock(brush, Modifier.fillMaxSize(), 10.dp)
                                Column(
                                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    com.kitsugi.animelist.ui.components.KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.8f).height(12.dp), 4.dp)
                                    com.kitsugi.animelist.ui.components.KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.4f).height(10.dp), 4.dp)
                                }
                            }
                        }
                    }
                }
            }
            results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.inline_no_content_found_b1f7076),
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            else -> {
                LazyRow(
                    state = rowState,
                    flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(rowState),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(results, key = { idx, item -> "${idx}_${item.source}_${item.type}_${item.malId}_${item.tmdbId ?: 0}" }) { _, item ->
                        ModernHomeMediaCard(
                            item = item,
                            cardWidth = cardWidth,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tek bir medya kartı — poster + gradient overlay + başlık + puan.
 */
@Composable
private fun ModernHomeMediaCard(
    item: JikanSearchResult,
    cardWidth: Dp,
    onClick: () -> Unit
) {
    val cardHeight = cardWidth * 1.5f
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = cardWidth, height = cardHeight),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster resmi — +18 içeriklerde her koşulda bulanıklık uygulanır
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = item.imageUrl,
                contentDescription = item.title,
                isAdult = item.isAdult,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Alt gradient + metin overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.score != null && item.score > 0.0) {
                        KitsugiEmojiLabel(
                            label = "%.1f".format(item.score),
                            icon = Icons.StarSolid,
                            iconSize = 9.dp,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// Geriye dönük uyumluluk için String tabanlı eski imza
@Composable
fun ModernHomeRows(
    title: String,
    items: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KitsugiColors.TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        val snapRowState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyRow(
            state = snapRowState,
            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(snapRowState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                Card(
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .size(width = 140.dp, height = 210.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(text = item, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
