package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey

/**
 * Explicit finite fallback for a mobile shelf whose legacy grid query is not verified.
 * It re-applies the current adult setting and never fetches a different category/page,
 * and makes the limit visible. Wiring it into navigation is a separate integration step.
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
) {
    val visibleResults = remember(results, showAdultContent) { results.filter { showAdultContent || !it.isAdult } }
    Column(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.mobile_grid_back), tint = KitsugiColors.TextPrimary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = KitsugiColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.mobile_grid_loaded_results_only),
            style = MaterialTheme.typography.bodySmall,
            color = KitsugiColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(visibleResults, key = { index, item -> item.kitsugiLazyKey("mobile_finite_grid", index) }) { _, item ->
                // Some providers expose 0..100. Existing formatters require 0..10; pass
                // display-only copies so callbacks still receive the original identity/data.
                val entry = getMediaEntry(item)
                val displayResult = item.copy(score = mobileExploreDisplayScore(item.score))
                val displayEntry = entry?.copy(score = mobileExploreDisplayScore(entry.score))
                KitsugiExploreMediaCard(
                    result = displayResult,
                    alreadyInList = alreadyInList(item),
                    mediaEntry = displayEntry,
                    onClick = { onItemClick(item) },
                    onLongClick = { onLongClickItem(item) },
                    titleLanguage = titleLanguage,
                    scoreFormat = scoreFormat,
                    hideScores = hideScores,
                    blurAdultMedia = blurAdultMedia,
                    forceVertical = true,
                )
            }
        }
    }
}
