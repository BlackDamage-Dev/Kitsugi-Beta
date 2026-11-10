package com.kitsugi.animelist.ui.screens.anime

import androidx.compose.foundation.layout.Spacer
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiErrorState
import com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection
import com.kitsugi.animelist.ui.components.KitsugiInfoDialog
import com.kitsugi.animelist.ui.components.KitsugiPage
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * @deprecated T4-04: Bu ekran AppNavigation/AppRoot'a bağlı değildir — ölü kod.
 * Ana keşif işlevselliği HomeScreen ve ExploreScreen üzerinden sağlanmaktadır.
 * Gelecekteki bir cleanup sprint'inde kaldırılabilir.
 */
@Deprecated("AnimeScreen is not connected to AppNavigation. Use HomeScreen or ExploreScreen instead.")
@Composable
fun AnimeScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    onSeeAllSection: (title: String, results: List<JikanSearchResult>) -> Unit,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    viewModel: AnimeViewModel = viewModel()
) {
    val accentColor = LocalKitsugiAccent.current

    val filteredTopAnime = viewModel.topAnime.filter { showAdultContent || !it.isAdult }
    val filteredAiringAnime = viewModel.airingAnime.filter { showAdultContent || !it.isAdult }
    val filteredUpcomingAnime = viewModel.upcomingAnime.filter { showAdultContent || !it.isAdult }

    fun isAlreadyInList(result: JikanSearchResult): Boolean {
        return currentEntries.any { entry ->
            entry.matchesInSource(result)
        }
    }

    fun handleDirectAdd(result: JikanSearchResult, synopsis: String? = null) {
        onAddSelectionToList(ApiSearchSelection(result = result, synopsis = synopsis))
    }

    KitsugiPage(
        title = "Anime",
        modifier = Modifier.kitsugiVerticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        TextButton(
            enabled = !viewModel.isLoading,
            onClick = { viewModel.loadData(forceRefresh = true) }
        ) {
            Text(
                text = if (viewModel.isLoading) KitsugiStrings.get(R.string.loading) else "Yenile",
                color = if (viewModel.isLoading) KitsugiColors.TextMuted else accentColor,
                fontWeight = FontWeight.Bold
            )
        }



        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(18.dp))
            KitsugiErrorState(
                message = viewModel.errorMessage.orEmpty(),
                onRetryClick = { viewModel.loadData(forceRefresh = true) }
            )
            viewModel.platformErrorCode?.let { code ->
                Spacer(modifier = Modifier.height(8.dp))
                PlatformErrorExplanationBlock(
                    code = code,
                    tint = KitsugiColors.AccentRed,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        KitsugiHorizontalMediaSection(
            title = stringResource(R.string.populer_anime),
            results = filteredTopAnime,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Popüler Anime", filteredTopAnime) }
        )

        Spacer(modifier = Modifier.height(26.dp))

        KitsugiHorizontalMediaSection(
            title = stringResource(R.string.yayindaki_anime),
            results = filteredAiringAnime,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Yayındaki Anime", filteredAiringAnime) }
        )

        Spacer(modifier = Modifier.height(26.dp))

        KitsugiHorizontalMediaSection(
            title = stringResource(R.string.yaklasan_anime),
            results = filteredUpcomingAnime,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Yaklaşan Anime", filteredUpcomingAnime) }
        )

        Spacer(modifier = Modifier.height(90.dp))
    }
}