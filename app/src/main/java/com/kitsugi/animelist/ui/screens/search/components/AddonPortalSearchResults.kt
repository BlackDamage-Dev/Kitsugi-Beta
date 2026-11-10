package com.kitsugi.animelist.ui.screens.search.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * Eklenti (CS3) arama sonucunu ana arama sayfasındaki JikanSearchResult kartına
 * dönüştürür — böylece KitsugiExploreMediaCard birebir aynı görünümle kullanılabilir.
 */
fun SearchResponse.toJikanResult(api: MainAPI): JikanSearchResult = JikanSearchResult(
    malId = (api.name + "|" + url).hashCode(),
    title = name,
    subtitle = quality?.name ?: tvTypeLabel(type),
    type = when (type) {
        TvType.Movie, TvType.AnimeMovie -> MediaType.Movie
        TvType.TvSeries, TvType.AsianDrama, TvType.Cartoon, TvType.Documentary -> MediaType.TvShow
        else -> MediaType.Anime
    },
    total = null,
    score = null,
    isAdult = isAddonAdultResponse(this),
    imageUrl = posterUrl,
    year = null,
    source = "cs3",
    cs3Url = url,
    cs3ApiName = api.name
)

private fun tvTypeLabel(type: TvType?): String = when (type) {
    TvType.Movie, TvType.AnimeMovie -> "Film"
    TvType.TvSeries -> "Dizi"
    TvType.Anime -> "Anime"
    TvType.AsianDrama -> "Asya Dizisi"
    TvType.Cartoon -> "Çizgi Film"
    TvType.Documentary -> "Belgesel"
    TvType.Live -> "Canlı"
    TvType.OVA -> "OVA"
    TvType.NSFW -> "NSFW"
    else -> ""
}

/**
 * Eklentiler arası arama sonuç bölümü — ana arama sayfasındaki MultiSearchSection ile
 * AYNI görünüm ve mantık: sol üstte eklenti rozeti + adı, sağda "Tümünü Gör",
 * altında yatay kaydırılabilir KitsugiExploreMediaCard listesi.
 */
@Composable
fun AddonSearchResultSection(
    api: MainAPI,
    results: List<SearchResponse>,
    onItemClick: (MainAPI, SearchResponse) -> Unit,
    onSeeAllClick: ((MainAPI) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    val accentColor = LocalKitsugiAccent.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = if (isLandscape) 260.dp else 180.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Eklenti Rozeti + Başlık & "Tümünü Gör" Header Row (MultiSearchSection ile aynı düzen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Extension,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = api.name,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.inline_1_s_results_b4b4d39, results.size),
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            if (onSeeAllClick != null) {
                TextButton(
                    onClick = { onSeeAllClick(api) },
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

        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            flingBehavior = KitsugiScrollDefaults.rememberRowSnapFling(rowState),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = results,
                key = { idx, item -> "addon_${api.name}_${item.url.hashCode()}_$idx" }
            ) { _, item ->
                KitsugiExploreMediaCard(
                    result = remember(api.name, item.url) { item.toJikanResult(api) },
                    alreadyInList = false,
                    mediaEntry = null,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onItemClick(api, item) }
                )
            }
        }
    }
}
