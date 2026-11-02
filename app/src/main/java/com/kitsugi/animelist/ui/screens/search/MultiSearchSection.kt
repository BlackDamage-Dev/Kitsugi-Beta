package com.kitsugi.animelist.ui.screens.search

import android.content.res.Configuration
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerResponsiveMediaRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey

/**
 * All-in-One Çoklu Platform Arama Bölümü (Yatay Kart Listesi - Keşfet Sayfası ile Birebir Aynı Mekanik ve Görünüm)
 *
 * ORTAK BİLEŞEN: Ana arama sayfası (SearchScreen) VE Eklenti Portalı
 * (PluginPickerScreen) arama sonuçlarını bu TEK raf bileşeniyle çizer.
 * Böylece iki yüzeyin görünümü ve mekaniği (kart boyutu, shimmer, yatay
 * kaydırma, "Tümünü Gör", TV odağı) birebir aynı kalır — biri değişince
 * diğeri de değişir, ayrışma olmaz.
 *
 * [platformId] bilinen platformlarda logo gösterir; eklenti rafları gibi
 * logo taşımayan kaynaklar [headerIcon] ile kendi simgesini (bayrak vb.) verir.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun MultiSearchSection(
    title: String,
    platformId: String?,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry? = { null },
    onItemClick: (JikanSearchResult) -> Unit,
    onSeeAllClick: () -> Unit,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    headerIcon: (@Composable () -> Unit)? = null
) {
    if (results.isEmpty() && !isLoading) return

    val accentColor = LocalKitsugiAccent.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTvDevice = LocalIsTvDevice.current
    val isTv = LocalIsTv.current
    val cardWidth = if (isLandscape) 260.dp else 180.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Platform Logosu + Başlık & "Tümünü Gör" Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (headerIcon != null) {
                headerIcon()
            } else {
                KitsugiPlatformLogo(
                    platformId = platformId,
                    size = 24.dp,
                    cornerRadius = 6.dp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (results.isNotEmpty()) {
                TextButton(
                    onClick = onSeeAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tumunu_gor),
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading && results.isEmpty() -> {
                // Gerçek kartlarla aynı yön, genişlik ve yatay kaydırma davranışı:
                // portrede 2:3 poster; yatayda poster + metin düzeni.
                KitsugiShimmerResponsiveMediaRow(
                    cardCount = 5,
                    cardWidth = cardWidth,
                    isLandscape = isLandscape && !isTv,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    cardSpacing = 12.dp,
                )
            }

            else -> {
                val lazyListState = rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }

                if (isTvDevice) {
                    val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
                    val lastFocusedIndex = remember(results) { mutableStateOf(0) }
                    val focusRequesters = remember(results) { mutableMapOf<Int, FocusRequester>() }
                    val rowFocusRequester = remember { FocusRequester() }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier
                                .focusRequester(rowFocusRequester)
                                .focusRestorer {
                                    focusRequesters[lastFocusedIndex.value] ?: FocusRequester.Default
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = results,
                                // DİKKAT: anahtarda `type` ZORUNLU — "Tümünü Gör" rafı karma
                                // anime+manga listesi taşıyabilir; aynı kaynak+id iki farklı
                                // yapımı gösterebilir (bkz. KitsugiLazyKeys).
                                key = { idx, result -> result.kitsugiLazyKey("sr_tv", idx) },
                                contentType = { _, _ -> "h_media_card" }
                            ) { index, result ->
                                val requester = focusRequesters.getOrPut(index) { FocusRequester() }
                                KitsugiExploreMediaCard(
                                    result = result,
                                    alreadyInList = isAlreadyInList(result),
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
                    LazyRow(
                        state = lazyListState,
                        flingBehavior = KitsugiScrollDefaults.rememberRowSnapFling(lazyListState),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = results,
                            key = { idx, result -> result.kitsugiLazyKey("sr", idx) },
                            contentType = { _, _ -> "h_media_card" }
                        ) { _, result ->
                            KitsugiExploreMediaCard(
                                result = result,
                                alreadyInList = isAlreadyInList(result),
                                mediaEntry = getMediaEntry(result),
                                modifier = Modifier.width(cardWidth),
                                onClick = { onItemClick(result) },
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
