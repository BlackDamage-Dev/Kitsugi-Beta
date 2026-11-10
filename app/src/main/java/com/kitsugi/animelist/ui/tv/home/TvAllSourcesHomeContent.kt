package com.kitsugi.animelist.ui.tv.home

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.screens.explore.*
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun TvAllSourcesHomeContent(
    viewModel: ExploreViewModel,
    entries: List<MediaEntry>,
    showAdultContent: Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onSeeAllClick: (String, List<JikanSearchResult>) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val navigationHeight = with(LocalDensity.current) { 64.dp.roundToPx() }
    var collapsedNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val entryMap = remember(entries) { generateExploreEntryMap(entries) }
    val getEntry = remember(entryMap) { { item: JikanSearchResult -> getMediaEntryFromMap(item, entryMap) } }
    // Bölümler yalnızca kaynak durumları değişince hesaplanır (bkz. ExploreScreen notu).
    val allSectionsList = remember(viewModel.allSourceStates, showAdultContent) {
        allSourceSections(viewModel.allSourceStates, showAdultContent)
    }
    val allSectionsByPlatform = remember(allSectionsList) { allSectionsList.groupBy { it.platform } }
    val allSectionsDistinctCounts = remember(allSectionsList) {
        allSectionsByPlatform.mapValues { (_, sections) ->
            sections.flatMap { it.results }.distinctBy { it.exploreIdentity() }.size
        }
    }
    KitsugiShimmerProvider(active = viewModel.isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(KitsugiColors.Background),
            contentPadding = PaddingValues(bottom = 80.dp)) {
            item(key = "tv_all_source_selector") {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExplorePlatformToggle(selectedPlatform = ExplorePlatform.ALL,
                        onPlatformSelected = { viewModel.selectPlatform(it) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.loadData(forceRefresh = true) }, enabled = !viewModel.isLoading) {
                        Text(stringResource(R.string.yenile))
                    }
                }
            }
            allSourcesExploreSections(
                states = viewModel.allSourceStates,
                sectionsByPlatform = allSectionsByPlatform,
                distinctCounts = allSectionsDistinctCounts,
                collapsedSources = ExplorePlatform.sources.filter { it.name in collapsedNames }.toSet(),
                onToggleSource = { source -> collapsedNames = if (source.name in collapsedNames) collapsedNames - source.name else collapsedNames + source.name },
                startIndex = 1,
                onJumpToIndex = { index -> scope.launch { listState.animateScrollToItem(index, -navigationHeight) } },
                onRetrySource = viewModel::retrySource,
                loadedCategories = viewModel.aniListLoadedCategories,
                loadingCategories = viewModel.aniListLoadingCategories,
                failedCategories = viewModel.aniListFailedCategories,
                onLoadCategory = viewModel::loadExploreSection,
                onRetryCategory = viewModel::retryExploreSection,
                alreadyInList = { getEntry(it) != null },
                getMediaEntry = getEntry,
                onItemClick = onItemClick,
                onLongClickItem = null,
                onSeeAllSection = { title, _, results, _ -> onSeeAllClick(title, results) }
            )
        }
    }
}
