package com.kitsugi.animelist.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.theme.LocalIsTv
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun KitsugiHorizontalMediaSection(
    title: String,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: ((JikanSearchResult) -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    getMediaEntry: (JikanSearchResult) -> com.kitsugi.animelist.model.MediaEntry? = { null },
    showTitle: Boolean = true
) {
    val accentColor = LocalKitsugiAccent.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = exploreShelfCardWidth(isLandscape)

    val shouldRender = results.isNotEmpty() || isLoading || onSeeAllClick == null
    if (shouldRender) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (onSeeAllClick != null) {
                        TextButton(
                            onClick = onSeeAllClick
                        ) {
                            Text(
                                text = stringResource(R.string.tumunu_gor),
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            when {
                isLoading && results.isEmpty() -> {
                    KitsugiShimmerResponsiveMediaRow(
                        cardWidth = cardWidth,
                        isLandscape = isLandscape,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        cardSpacing = 12.dp,
                    )
                }

                results.isEmpty() -> {
                    if (onSeeAllClick == null) {
                        Text(
                            text = stringResource(R.string.gosterilecek_icerik_yok),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                else -> {
                    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
                    if (isTvDevice) {
                        val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
                        val lastFocusedIndex = remember(results) { mutableStateOf(0) }
                        val focusRequesters = remember(results) { mutableMapOf<Int, FocusRequester>() }
                        val rowFocusRequester = remember { FocusRequester() }
                        // title'a bağlı stable state — platform değişiminde sıfırlanır, yoksa korunur
                        val lazyListState = androidx.compose.runtime.saveable.rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }
                        CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
                            LazyRow(
                                state = lazyListState,
                                modifier = Modifier
                                    .focusRequester(rowFocusRequester)
                                    .focusRestorer {
                                        focusRequesters[lastFocusedIndex.value] ?: FocusRequester.Default
                                    }
                                    .focusGroup(),
                                contentPadding = PaddingValues(
                                    horizontal = 20.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = results,
                                    // PERF: index-free stable key — Compose item'ları
                                    // yeniden kullanabilsin (filter/sort/prepend sonrası).
                                    key = { idx, result -> result.kitsugiLazyKey("tv", idx) },
                                    contentType = { _, _ -> "h_media_card" }
                                ) { index, result ->
                                    val requester = focusRequesters.getOrPut(index) { FocusRequester() }
                                    val cardWidth = if (isLandscape) 260.dp else 180.dp
                                    KitsugiExploreMediaCard(
                                        result = result,
                                        alreadyInList = alreadyInList(result),
                                        mediaEntry = getMediaEntry(result),
                                        modifier = Modifier
                                            .width(cardWidth)
                                            .focusRequester(requester)
                                            .onFocusChanged { state ->
                                                if (state.isFocused) {
                                                    lastFocusedIndex.value = index
                                                }
                                            },
                                        onClick = { onItemClick(result) },
                                        onLongClick = if (onLongClickItem != null) { { onLongClickItem(result) } } else null,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        blurAdultMedia = blurAdultMedia,
                                        forceVertical = false
                                    )
                                }
                            }
                        }
                    } else {
                        // title'a bağlı stable state — scroll pozisyonu platform değişince sıfırlanır
                        val lazyListState = androidx.compose.runtime.saveable.rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }
                        LazyRow(
                            state = lazyListState,
                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(lazyListState),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = results,
                                key = { idx, result -> result.kitsugiLazyKey("h", idx) },
                                contentType = { _, _ -> "h_media_card" }
                            ) { _, result ->
                                val cardWidth = if (isLandscape) 260.dp else 180.dp
                                KitsugiExploreMediaCard(
                                    result = result,
                                    alreadyInList = alreadyInList(result),
                                    mediaEntry = getMediaEntry(result),
                                    modifier = Modifier.width(cardWidth),
                                    onClick = {
                                        onItemClick(result)
                                    },
                                    onLongClick = if (onLongClickItem != null) { { onLongClickItem(result) } } else null,
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    forceVertical = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Eski statik LoadingSkeletonRow ve SkeletonMediaCard kaldırıldı.
// Animasyonlu versiyonları KitsugiShimmer.kt'de tanımlanmıştır:
//   - KitsugiShimmerMediaRow()  → yatay satır
//   - KitsugiShimmerMediaCard() → tek kart