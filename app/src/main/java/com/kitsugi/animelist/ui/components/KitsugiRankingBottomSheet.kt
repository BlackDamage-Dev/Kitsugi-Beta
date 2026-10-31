@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.uniqueItemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsugiRankingBottomSheet(
    title: String,
    mediaType: MediaType,
    platform: ExplorePlatform,
    initialResults: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onDismissRequest: () -> Unit,
    titleLanguage: String = "ENGLISH",
    hideScores: Boolean = false,
    showAdultContent: Boolean = false,
    blurAdultMedia: Boolean = false,
    getMediaEntry: (JikanSearchResult) -> com.kitsugi.animelist.model.MediaEntry? = { null }
) {
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val jikanClient = remember { JikanApiClient() }
    val tmdbClient = remember { TmdbApiClient() }
    val lazyListState = rememberLazyListState()

    val isManga = mediaType == MediaType.Manga
    var selectedTab by remember { mutableStateOf("SCORE") } // SCORE, POPULARITY, FAVORITE, UPCOMING
    var resultsList by remember { mutableStateOf(initialResults) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadTabResults(tab: String) {
        selectedTab = tab
        isLoading = true
        scope.launch {
            try {
                val newItems = when (platform) {
                    ExplorePlatform.AniList -> {
                        when (tab) {
                            "SCORE" -> jikanClient.aniListTopAnime(page = 1, showAdultContent = showAdultContent)
                            "POPULARITY" -> jikanClient.aniListTrendingAnime(page = 1, showAdultContent = showAdultContent)
                            "FAVORITE" -> jikanClient.searchAniList(
                                query = "",
                                mediaType = mediaType,
                                showAdultContent = showAdultContent,
                                sort = listOf("FAVOURITES_DESC")
                            )
                            "UPCOMING" -> jikanClient.aniListUpcomingAnime(page = 1, showAdultContent = showAdultContent)
                            else -> initialResults
                        }
                    }
                    ExplorePlatform.MAL -> {
                        when (tab) {
                            "SCORE" -> if (isManga) jikanClient.topManga(1, showAdultContent) else jikanClient.topAnime(1, showAdultContent)
                            "POPULARITY" -> if (isManga) jikanClient.topManga(1, showAdultContent) else jikanClient.trendingAnime(1, showAdultContent)
                            "FAVORITE" -> jikanClient.searchMALOnly(
                                query = "",
                                mediaType = mediaType,
                                showAdultContent = showAdultContent,
                                orderBy = "favorites",
                                sort = "desc"
                            )
                            "UPCOMING" -> jikanClient.upcomingAnime(1, showAdultContent)
                            else -> initialResults
                        }
                    }
                    ExplorePlatform.TMDB -> {
                        when (tab) {
                            "SCORE" -> tmdbClient.getTopRatedShows(1)
                            "POPULARITY" -> tmdbClient.getTrendingShows(1)
                            "FAVORITE" -> tmdbClient.getPopularMovies(1)
                            "UPCOMING" -> tmdbClient.getTrendingAll(1)
                            else -> initialResults
                        }
                    }
                    ExplorePlatform.BANGUMI -> {
                        val bangumi = com.kitsugi.animelist.data.remote.KitsugiBangumiClient
                        when (tab) {
                            "SCORE" -> if (isManga) bangumi.topRatedManga(24) else bangumi.topRatedAnime(24)
                            "POPULARITY" -> if (isManga) bangumi.trendingManga(24) else bangumi.trendingAnime(24)
                            "FAVORITE" -> if (isManga) bangumi.topManga(24) else bangumi.topAnime(24)
                            "UPCOMING" -> bangumi.upcomingAnime(24)
                            else -> initialResults
                        }
                    }
                    else -> initialResults
                }
                if (newItems.isNotEmpty()) {
                    resultsList = newItems
                }
            } catch (e: Exception) {
                // Keep previous results on error
            } finally {
                isLoading = false
            }
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismissRequest,
        fillMaxHeight = true,
        innerScrollState = lazyListState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Header bar (Title + Close Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-Tabs Bar (Puan | Popülerlik | Favori | Yakında)
            KitsugiRankingTabs(
                selectedTab = selectedTab,
                isManga = isManga,
                onTabSelected = { tab -> loadTabResults(tab) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Content List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiPlasmaLoader(size = 46.dp)
                }
            } else if (resultsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiEmptyState(
                        title = KitsugiStrings.get(R.string.sonuc_bulunamadi_b51f9f8),
                        subtitle = KitsugiStrings.get(R.string.secilen_kategori_icin_siralama_verisi_mevcut_degil),
                        icon = Icons.SearchOff
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    uniqueItemsIndexed(
                        items = resultsList,
                        key = { _, item -> "${item.source}_${item.type}_${item.malId}_${item.tmdbId ?: 0}" }
                    ) { index, item ->
                        KitsugiRankingMediaCard(
                            result = item,
                            rankIndex = index + 1,
                            alreadyInList = alreadyInList(item),
                            mediaEntry = getMediaEntry(item),
                            onClick = {
                                onItemClick(item)
                                onDismissRequest()
                            },
                            titleLanguage = titleLanguage,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KitsugiRankingTabs(
    selectedTab: String,
    isManga: Boolean,
    onTabSelected: (String) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val tabs = remember(isManga) {
        if (isManga) {
            listOf("SCORE" to KitsugiStrings.get(R.string.detail_stat_score), "POPULARITY" to KitsugiStrings.get(R.string.populerlik), "FAVORITE" to "Favori")
        } else {
            listOf("SCORE" to KitsugiStrings.get(R.string.detail_stat_score), "POPULARITY" to KitsugiStrings.get(R.string.populerlik), "FAVORITE" to "Favori", "UPCOMING" to KitsugiStrings.get(R.string.yakinda))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (tabKey, tabTitle) ->
            val isSelected = selectedTab == tabKey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(tabKey) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = tabTitle,
                    color = if (isSelected) accentColor else KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(if (isSelected) 32.dp else 0.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
            }
        }
    }
}
