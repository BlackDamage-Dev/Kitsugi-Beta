package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.NuvioCatalogPosterTile
import com.kitsugi.animelist.ui.icons.KitsugiIcons
import com.kitsugi.animelist.ui.navigation.NuvioCircularActionButton
import com.kitsugi.animelist.ui.navigation.nuvioCatalogGridColumnsForWidth
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey

/**
 * NuvioMobile `CatalogScreen`-inspired "Tümünü Gör" (See All) view for mixed/personal
 * finite mobile shelves across both Portrait and Landscape orientations.
 */
@Composable
internal fun MobileExploreFiniteGrid(
    title: String,
    results: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onBack: () -> Unit,
    showAdultContent: Boolean,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    modifier: Modifier = Modifier,
    platform: ExplorePlatform? = null,
) {
    val visibleResults = remember(results, showAdultContent) {
        results.filter { showAdultContent || !it.isAdult }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(KitsugiColors.Background),
    ) {
        val columns = nuvioCatalogGridColumnsForWidth(maxWidth)
        val horizontalPad = if (maxWidth >= 600.dp) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Nuvio Catalog Header: Dairesel Geri + Başlık + Kaynak rozeti + İçerik sayacı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NuvioCircularActionButton(
                    icon = KitsugiIcons.ArrowBack,
                    contentDescription = stringResource(R.string.mobile_grid_back),
                    onClick = onBack,
                    visualSize = 38.dp,
                    iconSize = 19.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = KitsugiColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (platform != null) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(KitsugiColors.SurfaceElevated)
                                    .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.65f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (platform != ExplorePlatform.ALL) {
                                    KitsugiPlatformLogo(platformId = platform.name, size = 12.dp)
                                }
                                Text(
                                    text = if (platform == ExplorePlatform.ALL) {
                                        stringResource(R.string.mobile_explore_source_all)
                                    } else {
                                        platform.shortName
                                    },
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.mobile_grid_loaded_results_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceElevated)
                        .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = visibleResults.size.toString(),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                itemsIndexed(
                    items = visibleResults,
                    key = { index, item -> item.kitsugiLazyKey("mobile_finite_grid", index) },
                ) { _, item ->
                    NuvioCatalogPosterTile(
                        result = item,
                        alreadyInList = alreadyInList(item),
                        mediaEntry = getMediaEntry(item),
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        onClick = { onItemClick(item) },
                        onLongClick = { onLongClickItem(item) },
                    )
                }
            }
        }
    }
}
