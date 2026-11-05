package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.toStreamMediaIds
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity

/**
 * Left panel for [MediaEntryDetailPage] in landscape mode.
 *
 * Contains [DetailHero] and [QuickActions] buttons.
 */
@Composable
fun EntryDetailLeftPanel(
    entry: MediaEntry,
    detailState: KitsugiMediaDetail?,
    logoUrl: String?,
    galleryItems: List<GalleryItem>,
    mangaMapping: MangaMappingEntity?,
    resolvedTmdbId: Int?,
    showAnimeLogos: Boolean,
    showFavouriteButton: Boolean,
    titleLanguage: String,
    blurAdultMedia: Boolean,
    onBackClick: () -> Unit,
    onIncrementProgressClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReadMangaClick: ((MangaMappingEntity?) -> Unit)?,
    onUnlinkMangaClick: () -> Unit,
    onGalleryOpen: (List<GalleryItem>, Int) -> Unit,
    leftPanelFocusRequester: FocusRequester,
    tabBarFocusRequester: FocusRequester,
    targetSeason: Int = 1
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val externalUrl = buildExternalUrl(entry)

    DetailHero(
        entry = entry,
        logoUrl = if (showAnimeLogos) logoUrl else null,
        onBackClick = onBackClick,
        titleLanguage = titleLanguage,
        blurAdultMedia = blurAdultMedia,
        onPosterClick = { clickedUrl, clickedCategory ->
            val selection = selectHeroArtworkInGallery(
                galleryItems = galleryItems,
                clickedUrl = clickedUrl,
                clickedCategory = clickedCategory,
                source = entry.source
            )
            onGalleryOpen(selection.items, selection.index)
        },
        onGalleryClick = if (galleryItems.isNotEmpty() || !entry.imageUrl.isNullOrBlank()) {
            {
                val clickedUrl = entry.imageUrl
                val index = if (!clickedUrl.isNullOrBlank()) {
                    galleryItems.indexOfFirst { item ->
                        item.url == clickedUrl || 
                        item.url.substringAfterLast("/") == clickedUrl.substringAfterLast("/")
                    }
                } else -1
                if (index >= 0) {
                    onGalleryOpen(galleryItems, index)
                } else if (!clickedUrl.isNullOrBlank()) {
                    val coverItem = GalleryItem(
                        url = clickedUrl,
                        category = GalleryCategory.POSTER,
                        source = entry.source
                    )
                    onGalleryOpen(listOf(coverItem) + galleryItems, 0)
                } else {
                    val posterIndex = galleryItems.indexOfFirst { it.category == GalleryCategory.POSTER }.coerceAtLeast(0)
                    onGalleryOpen(galleryItems, posterIndex)
                }
            }
        } else null,
        nextAiring = detailState?.nextAiringEpisode,
        showFavoriteButton = showFavouriteButton,
        onToggleFavoriteClick = onToggleFavoriteClick,
        // Yatay modda en kaliteli YATAY görsel (TMDB/Fanart arka planı) vitrinde kullanılır.
        galleryItems = galleryItems
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        QuickActions(
            entry = entry,
            externalUrl = externalUrl,
            onIncrementProgressClick = onIncrementProgressClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            onOpenExternalClick = {
                if (externalUrl != null) {
                    uriHandler.openUri(externalUrl)
                }
            },
            onWatchClick = if (entry.type == com.kitsugi.animelist.model.MediaType.Anime || entry.type == com.kitsugi.animelist.model.MediaType.TvShow || entry.type == com.kitsugi.animelist.model.MediaType.Movie) {
                {
                    val streamIds = entry.toStreamMediaIds(
                        resolvedRealMalId = detailState?.realMalId,
                        resolvedTmdbId = entry.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId
                    )
                    KitsugiStreamActivity.start(
                        context = context,
                        malId = streamIds.malId,
                        aniListId = streamIds.aniListId,
                        tmdbId = streamIds.tmdbId,
                        bangumiId = streamIds.bangumiId,
                        mediaType = entry.type,
                        episode = 1,
                        season = targetSeason,
                        isMovie = entry.type == com.kitsugi.animelist.model.MediaType.Movie,
                        title = entry.title,
                        posterUrl = entry.imageUrl,
                        titleEnglish = detailState?.titleEnglish,
                        titleRomaji = detailState?.titleRomaji,
                        titleNative = detailState?.titleNative,
                        synonyms = detailState?.synonyms.orEmpty(),
                        startYear = entry.year,
                        imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                            ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
                    )
                }
            } else null,
            onReadClick = if (entry.type == com.kitsugi.animelist.model.MediaType.Manga) {
                {
                    if (onReadMangaClick != null) {
                        onReadMangaClick(mangaMapping)
                    }
                }
            } else null,
            mangaMapping = mangaMapping,
            onLinkMangaClick = if (entry.type == com.kitsugi.animelist.model.MediaType.Manga) {
                {
                    if (onReadMangaClick != null) {
                        onReadMangaClick(null)
                    }
                }
            } else null,
            onUnlinkMangaClick = onUnlinkMangaClick,
            primaryFocusRequester = leftPanelFocusRequester,
            tabBarFocusRequester = tabBarFocusRequester
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
