package com.kitsugi.animelist.ui.screens.detail

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens

// Shared with the loaded detail cards so orientation changes cannot desynchronise them.
internal val DETAIL_RELATION_POSTER_WIDTH = 50.dp
internal val DETAIL_RELATION_POSTER_HEIGHT = 70.dp
internal val DETAIL_RECOMMENDATION_POSTER_WIDTH = 54.dp
internal val DETAIL_RECOMMENDATION_POSTER_HEIGHT = 76.dp
internal val DETAIL_EPISODE_THUMB_WIDTH = 100.dp
internal val DETAIL_EPISODE_THUMB_HEIGHT = 60.dp

/**
 * Detail-page placeholders share one shimmer clock per content group. The card
 * geometry intentionally mirrors the real content instead of using search-result
 * placeholders everywhere.
 */
@Composable
private fun DetailShimmerFrame(content: @Composable (Brush) -> Unit) {
    KitsugiShimmerProvider {
        val brush = LocalShimmerBrush.current ?: return@KitsugiShimmerProvider
        content(brush)
    }
}

@Composable
private fun DetailShimmerLine(
    brush: Brush,
    modifier: Modifier,
    cornerRadius: Dp = 999.dp
) {
    KitsugiShimmerBlock(brush = brush, modifier = modifier, cornerRadius = cornerRadius)
}

@Composable
internal fun DetailRelationsLoadingSkeleton(itemCount: Int = 3) {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.Surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailShimmerLine(
                        brush,
                        Modifier.size(width = DETAIL_RELATION_POSTER_WIDTH, height = DETAIL_RELATION_POSTER_HEIGHT),
                        8.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.82f).height(14.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.56f).height(11.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DetailShimmerLine(brush, Modifier.width(62.dp).height(9.dp))
                            DetailShimmerLine(brush, Modifier.width(45.dp).height(9.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailRecommendationsLoadingSkeleton(itemCount: Int = 4) {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.Surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailShimmerLine(
                        brush,
                        Modifier.size(width = DETAIL_RECOMMENDATION_POSTER_WIDTH, height = DETAIL_RECOMMENDATION_POSTER_HEIGHT),
                        10.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.88f).height(14.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.68f).height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DetailShimmerLine(brush, Modifier.width(74.dp).height(10.dp))
                            DetailShimmerLine(brush, Modifier.width(38.dp).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailCharactersLoadingSkeleton(itemCount: Int = 4) {
    DetailShimmerFrame { brush ->
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val safeCount = itemCount.coerceAtLeast(1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLandscape) {
                repeat((safeCount + 1) / 2) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(2) { columnIndex ->
                            val index = rowIndex * 2 + columnIndex
                            if (index < safeCount) {
                                DetailCharacterCardSkeleton(brush, Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                repeat(safeCount) {
                    DetailCharacterCardSkeleton(brush, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DetailCharacterCardSkeleton(brush: Brush, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailShimmerLine(brush, Modifier.size(44.dp), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DetailShimmerLine(brush, Modifier.fillMaxWidth(0.84f).height(13.dp))
                DetailShimmerLine(brush, Modifier.fillMaxWidth(0.55f).height(10.dp))
            }
            Spacer(Modifier.width(8.dp))
            DetailShimmerLine(brush, Modifier.size(34.dp), 10.dp)
        }
        Box(
            modifier = Modifier
                .padding(start = 21.dp, top = 2.dp, bottom = 2.dp)
                .width(2.dp)
                .height(10.dp)
                .background(KitsugiColors.SurfaceStrong)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailShimmerLine(brush, Modifier.size(44.dp), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DetailShimmerLine(brush, Modifier.fillMaxWidth(0.76f).height(13.dp))
                DetailShimmerLine(brush, Modifier.fillMaxWidth(0.48f).height(10.dp))
            }
        }
    }
}

@Composable
internal fun DetailStaffLoadingSkeleton(itemCount: Int = 5) {
    DetailShimmerFrame { brush ->
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val safeCount = itemCount.coerceAtLeast(1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLandscape) {
                repeat((safeCount + 1) / 2) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(2) { columnIndex ->
                            val index = rowIndex * 2 + columnIndex
                            if (index < safeCount) {
                                DetailStaffRowSkeleton(brush, Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                repeat(safeCount) {
                    DetailStaffRowSkeleton(brush, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DetailStaffRowSkeleton(brush: Brush, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailShimmerLine(brush, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.8f).height(14.dp))
            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.52f).height(10.dp))
        }
    }
}

/** Poster/name rail used by the TV detail screen's people sections. */
@Composable
internal fun DetailTvPeopleRailSkeleton(itemCount: Int = 6, showRole: Boolean = false) {
    DetailShimmerFrame { brush ->
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.itemGap)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailShimmerLine(brush, Modifier.size(72.dp), 999.dp)
                    DetailShimmerLine(brush, Modifier.width(72.dp).height(10.dp))
                    if (showRole) {
                        DetailShimmerLine(brush, Modifier.width(56.dp).height(8.dp))
                    }
                }
            }
        }
    }
}

/** Portrait-poster rail used by the TV detail screen's relation cards. */
@Composable
internal fun DetailTvRelationsRailSkeleton(itemCount: Int = 4) {
    DetailShimmerFrame { brush ->
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.itemGap)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Column(
                    modifier = Modifier.width(110.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    DetailShimmerLine(brush, Modifier.fillMaxWidth().height(150.dp), 10.dp)
                    DetailShimmerLine(brush, Modifier.fillMaxWidth(0.86f).height(11.dp))
                    DetailShimmerLine(brush, Modifier.fillMaxWidth(0.62f).height(9.dp))
                }
            }
        }
    }
}

/** Wide thumbnail rail matching the episode cards used in the TV detail layout. */
@Composable
internal fun DetailTvEpisodesRailSkeleton(itemCount: Int = 4) {
    DetailShimmerFrame { brush ->
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.itemGap)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Column(
                    modifier = Modifier
                        .width(KitsugiTvTokens.Cards.episodeWidth)
                        .clip(RoundedCornerShape(18.dp))
                        .background(KitsugiColors.Surface)
                ) {
                    DetailShimmerLine(brush, Modifier.fillMaxWidth().height(140.dp), 0.dp)
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.width(74.dp).height(10.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.9f).height(12.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.52f).height(9.dp))
                    }
                }
            }
        }
    }
}

/** TV stats use a chip row and horizontal distribution bars, not the mobile charts. */
@Composable
internal fun DetailTvStatsLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.65f).height(12.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.45f).height(20.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth().height(4.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailShimmerLine(brush, Modifier.width(130.dp).height(20.dp))
                repeat(5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DetailShimmerLine(brush, Modifier.width(30.dp).height(12.dp))
                        DetailShimmerLine(brush, Modifier.weight(1f).height(6.dp))
                        DetailShimmerLine(brush, Modifier.width(40.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * "Grafikler" sekmesi iskeleti — yüklenen içerikle aynı düzeni izler: sıralama
 * satırları, durum gösterge çubuğu + saran lejant çipleri ve puan kolonları.
 */
@Composable
internal fun DetailStatsLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1) Sıralama kartı
            StatsSkeletonCard {
                DetailShimmerLine(brush, Modifier.width(150.dp).height(16.dp))
                Spacer(Modifier.height(14.dp))
                repeat(3) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailShimmerLine(brush, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DetailShimmerLine(
                                brush,
                                Modifier.fillMaxWidth(if (index == 1) 0.62f else 0.74f).height(13.dp)
                            )
                            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.34f).height(10.dp))
                        }
                    }
                }
            }

            // 2) Durum dağılımı kartı
            StatsSkeletonCard {
                DetailShimmerLine(brush, Modifier.width(170.dp).height(16.dp))
                Spacer(Modifier.height(16.dp))
                DetailShimmerLine(brush, Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        DetailShimmerLine(
                            brush,
                            Modifier.width((104 + index * 16).dp).height(30.dp),
                            cornerRadius = 12.dp
                        )
                    }
                }
            }

            // 3) Puan dağılımı kartı
            StatsSkeletonCard {
                DetailShimmerLine(brush, Modifier.width(180.dp).height(16.dp))
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(10) { index ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            DetailShimmerLine(brush, Modifier.width(20.dp).height(8.dp))
                            val barHeight = (30 + ((index * 23) % 70)).dp
                            DetailShimmerLine(
                                brush,
                                Modifier.fillMaxWidth().height(barHeight),
                                cornerRadius = 8.dp
                            )
                            DetailShimmerLine(brush, Modifier.width(14.dp).height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSkeletonCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        content = content
    )
}

/** Horizontal review cards, matching the review section's actual scrolling direction. */
@Composable
internal fun DetailReviewsLoadingSkeleton(itemCount: Int = 2, cardWidth: Dp = 280.dp) {
    DetailShimmerFrame { brush ->
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(176.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.92f).height(12.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.76f).height(12.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.52f).height(12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DetailShimmerLine(brush, Modifier.width(36.dp).height(11.dp))
                            DetailShimmerLine(brush, Modifier.width(42.dp).height(11.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DetailShimmerLine(brush, Modifier.size(24.dp), 999.dp)
                            Spacer(Modifier.width(6.dp))
                            DetailShimmerLine(brush, Modifier.width(54.dp).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Skeleton cards for forum topics and activity items in the detail's social feed. */
@Composable
internal fun DetailSocialCardsLoadingSkeleton(
    itemCount: Int = 2,
    cardWidth: Dp = 280.dp,
    isActivity: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    cardSpacing: Dp = 10.dp,
    verticalPadding: Dp = 0.dp,
) {
    DetailShimmerFrame { brush ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            repeat(itemCount.coerceAtLeast(1)) {
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(176.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isActivity) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DetailShimmerLine(brush, Modifier.size(24.dp), 999.dp)
                                Spacer(Modifier.width(8.dp))
                                DetailShimmerLine(brush, Modifier.fillMaxWidth(0.45f).height(11.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    DetailShimmerLine(brush, Modifier.fillMaxWidth().height(11.dp))
                                    DetailShimmerLine(brush, Modifier.fillMaxWidth(0.78f).height(11.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                DetailShimmerLine(brush, Modifier.size(width = 30.dp, height = 42.dp), 6.dp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.92f).height(12.dp))
                            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.78f).height(12.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailShimmerLine(brush, Modifier.width(30.dp).height(10.dp))
                            DetailShimmerLine(brush, Modifier.width(30.dp).height(10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DetailShimmerLine(brush, Modifier.size(22.dp), 999.dp)
                            Spacer(Modifier.width(6.dp))
                            DetailShimmerLine(brush, Modifier.width(62.dp).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Skeleton content for the body and reply list in an opened forum-topic dialog. */
@Composable
internal fun DetailTopicDialogLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.Background)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { index ->
                    DetailShimmerLine(
                        brush,
                        Modifier.fillMaxWidth(if (index == 3) 0.58f else if (index == 1) 0.88f else 1f).height(12.dp)
                    )
                }
            }
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Background)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    DetailShimmerLine(brush, Modifier.size(32.dp), 999.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.42f).height(10.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth().height(11.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.78f).height(11.dp))
                    }
                }
            }
        }
    }
}

/** Skeleton content for the activity card and reply list in its detail dialog. */
@Composable
internal fun DetailActivityDialogLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.Background)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailShimmerLine(brush, Modifier.fillMaxWidth(0.86f).height(12.dp))
                    DetailShimmerLine(brush, Modifier.fillMaxWidth().height(11.dp))
                    DetailShimmerLine(brush, Modifier.fillMaxWidth(0.72f).height(11.dp))
                }
                DetailShimmerLine(brush, Modifier.size(width = 58.dp, height = 78.dp), 8.dp)
            }
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Background)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    DetailShimmerLine(brush, Modifier.size(30.dp), 999.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.38f).height(10.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth().height(11.dp))
                        DetailShimmerLine(brush, Modifier.fillMaxWidth(0.7f).height(11.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailSynopsisLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { index ->
                DetailShimmerLine(
                    brush,
                    Modifier.fillMaxWidth(if (index == 4) 0.58f else if (index == 2) 0.9f else 1f).height(13.dp)
                )
            }
        }
    }
}

@Composable
internal fun DetailRatingBadgesLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        // Gerçek kart artık ekran genişliğine eşit dağılan bir ızgara çiziyor; iskelet de
        // aynı geometriyi taklit eder ki yükleme bittiğinde kart zıplamasın.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailShimmerLine(brush, Modifier.width(if (index == 0) 42.dp else 54.dp).height(15.dp))
                    DetailShimmerLine(brush, Modifier.width(if (index == 1) 32.dp else 44.dp).height(9.dp))
                }
            }
        }
    }
}

@Composable
internal fun DetailGalleryLoadingSkeleton() {
    DetailShimmerFrame { brush ->
        // Gallery data decides which categories/sources exist. Preview one poster
        // category using the same header, source row and 100dp thumbnail geometry.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.SurfaceSoft).padding(vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DetailShimmerLine(brush, Modifier.width(100.dp).height(14.dp))
                    DetailShimmerLine(brush, Modifier.width(75.dp).height(10.dp))
                }
                DetailShimmerLine(brush, Modifier.size(22.dp), 6.dp)
            }
            Spacer(Modifier.height(16.dp))
            DetailShimmerLine(brush, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).width(100.dp).height(20.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DetailShimmerLine(brush, Modifier.size(18.dp), 4.dp)
                DetailShimmerLine(brush, Modifier.width(90.dp).height(16.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { DetailShimmerLine(brush, Modifier.size(width = (100f * 2f / 3f).dp, height = 100.dp), 12.dp) }
            }
        }
    }
}

@Composable
internal fun DetailEpisodesLoadingSkeleton(totalSeasons: Int? = null, targetSeason: Int? = null) {
    DetailShimmerFrame { brush ->
        val hasSeasons = (totalSeasons ?: 1) > 1
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasSeasons) {
                repeat((totalSeasons ?: 1).coerceIn(1, 30)) { seasonIndex ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KitsugiColors.Surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DetailShimmerLine(brush, Modifier.size(32.dp), 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailShimmerLine(brush, Modifier.width(92.dp).height(12.dp))
                                DetailShimmerLine(brush, Modifier.width(62.dp).height(9.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            DetailShimmerLine(brush, Modifier.size(22.dp), 999.dp)
                        }
                        if (seasonIndex + 1 == (targetSeason ?: 1)) {
                            Column(
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(2) { DetailEpisodeRowSkeleton(brush) }
                            }
                        }
                    }
                }
            } else {
                repeat(3) { DetailEpisodeRowSkeleton(brush) }
            }
        }
    }
}

@Composable
private fun DetailEpisodeRowSkeleton(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceStrong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailShimmerLine(brush, Modifier.size(width = DETAIL_EPISODE_THUMB_WIDTH, height = DETAIL_EPISODE_THUMB_HEIGHT), 10.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.9f).height(13.dp))
            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.66f).height(10.dp))
            DetailShimmerLine(brush, Modifier.fillMaxWidth(0.44f).height(9.dp))
        }
        DetailShimmerLine(brush, Modifier.size(28.dp), 999.dp)
    }
}
