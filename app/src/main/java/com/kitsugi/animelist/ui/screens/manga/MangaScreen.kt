package com.kitsugi.animelist.ui.screens.manga
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
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

@Composable
fun MangaScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    onSeeAllSection: (title: String, results: List<JikanSearchResult>) -> Unit,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onOpenMangaReader: () -> Unit = {},
    viewModel: MangaViewModel = viewModel()
) {
    val accentColor = LocalKitsugiAccent.current

    val filteredTopManga = viewModel.topManga.filter { showAdultContent || !it.isAdult }
    val filteredPublishingManga = viewModel.publishingManga.filter { showAdultContent || !it.isAdult }
    val filteredCompletedManga = viewModel.completedManga.filter { showAdultContent || !it.isAdult }

    fun isAlreadyInList(result: JikanSearchResult): Boolean {
        return currentEntries.any { entry ->
            entry.matchesInSource(result)
        }
    }

    fun handleDirectAdd(result: JikanSearchResult, synopsis: String? = null) {
        onAddSelectionToList(ApiSearchSelection(result = result, synopsis = synopsis))
    }

    KitsugiPage(
        title = "Manga",
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // ── Manga Okuyucu Giriş Butonu ──────────────────────────────────────
        KitsugiButton(
            onClick = onOpenMangaReader,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoStories,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(KitsugiStrings.get(R.string.inline_read_manga_extension_sources_cf7a30a))
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            viewModel.errorCode?.let { code ->
                Spacer(modifier = Modifier.height(8.dp))
                com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        KitsugiHorizontalMediaSection(
            title = stringResource(R.string.populer_manga),
            results = filteredTopManga,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Popüler Manga", filteredTopManga) }
        )

        Spacer(modifier = Modifier.height(26.dp))

        KitsugiHorizontalMediaSection(
            title = stringResource(R.string.yayindaki_manga),
            results = filteredPublishingManga,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Yayındaki Manga", filteredPublishingManga) }
        )

        Spacer(modifier = Modifier.height(26.dp))

        KitsugiHorizontalMediaSection(
            title = "Tamamlanan Manga",
            results = filteredCompletedManga,
            isLoading = viewModel.isLoading,
            alreadyInList = ::isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onSeeAllClick = { onSeeAllSection("Tamamlanan Manga", filteredCompletedManga) }
        )

        Spacer(modifier = Modifier.height(90.dp))
    }
}