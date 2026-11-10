@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.toStreamMediaIds
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Left panel for [ApiResultDetailPage] in landscape mode.
 *
 * Contains the [KitsugiDetailHero] and all action buttons (Watch, Read, Add/Edit, Source link).
 */
@Composable
fun ApiDetailLeftPanel(
    displayResult: JikanSearchResult,
    detailState: KitsugiMediaDetail?,
    resolvedTmdbId: Int?,
    galleryItems: List<GalleryItem>,
    existingEntry: MediaEntry?,
    isConnected: Boolean,
    isFavorite: Boolean,
    showFavouriteButton: Boolean,
    synopsisForSave: String?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    showAnimeLogos: Boolean,
    logoUrl: String?,
    blurAdultMedia: Boolean,
    onBackClick: () -> Unit,
    onAddClick: (ApiSearchSelection) -> Unit,
    /** "Düzenle" — aynı sağlayıcıdaki mevcut koleksiyon kaydını açar. */
    onEditClick: (MediaEntry, String) -> Unit,
    onToggleFavoriteClick: ((ApiSearchSelection) -> Unit)?,
    onReadMangaClick: (() -> Unit)?,
    onGalleryOpen: (items: List<GalleryItem>, index: Int) -> Unit,
    onShowAuthWarning: () -> Unit,
    leftPanelFocusRequester: FocusRequester,
    tabBarFocusRequester: FocusRequester,
    targetSeason: Int = 1,
    /** Sayfa açılmadan önce çözülmüş vitrin görsel planı (TMDB/Fanart.tv kalite sırası). */
    heroArtwork: KitsugiHeroArtworkResolver.HeroArtwork? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current
    val externalUrl = com.kitsugi.animelist.utils.ShareLinks.mediaUrl(
        source = displayResult.source,
        storedId = displayResult.malId,
        type = displayResult.type,
        tmdbId = displayResult.tmdbId,
        realMalId = displayResult.realMalId
    )

    KitsugiDetailHero(
        title = displayResult.getDisplayTitle(titleLanguage),
        subtitle = displayResult.subtitle,
        imageUrl = displayResult.imageUrl,
        backdropUrl = displayResult.backdropUrl,
        mediaType = displayResult.type,
        artworkItem = displayResult,
        preResolvedArtwork = heroArtwork,
        galleryItems = galleryItems,
        logoUrl = if (showAnimeLogos) logoUrl else null,
        source = displayResult.source,
        typeLabel = when (displayResult.type) {
            MediaType.Anime  -> "ANIME"
            MediaType.Movie  -> "FİLM"
            MediaType.TvShow -> "DİZİ"
            else             -> "MANGA"
        },
        year = displayResult.year?.toString(),
        isAdult = displayResult.isAdult,
        onBackClick = onBackClick,
        blurAdultMedia = blurAdultMedia,
        onShareClick = {
            // Kaynak + kimlik çözümlemesi tek yerde: ShareLinks (bkz. ShareLinks.kt).
            val shareUrl = com.kitsugi.animelist.utils.ShareLinks.mediaUrl(
                source = displayResult.source,
                storedId = displayResult.malId,
                type = displayResult.type,
                tmdbId = displayResult.tmdbId,
                realMalId = displayResult.realMalId
            )
            com.kitsugi.animelist.utils.ShareUtils.shareText(
                context,
                displayResult.getDisplayTitle(titleLanguage),
                shareUrl
            )
        },
        onPosterClick = { clickedUrl, clickedCategory ->
            val selection = selectHeroArtworkInGallery(
                galleryItems = galleryItems,
                clickedUrl = clickedUrl,
                clickedCategory = clickedCategory,
                source = displayResult.source
            )
            onGalleryOpen(selection.items, selection.index)
        },
        onGalleryClick = if (galleryItems.isNotEmpty() || !displayResult.imageUrl.isNullOrBlank()) {
            {
                val clickedUrl = displayResult.imageUrl
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
                        source = displayResult.source
                    )
                    onGalleryOpen(listOf(coverItem) + galleryItems, 0)
                } else {
                    val posterIndex = galleryItems.indexOfFirst { it.category == GalleryCategory.POSTER }.coerceAtLeast(0)
                    onGalleryOpen(galleryItems, posterIndex)
                }
            }
        } else null,
        scoreLabel = if (!hideScores) displayResult.getDisplayScore(scoreFormat, hideScores) else null,
        alreadyInList = existingEntry != null,
        isFavorite = isFavorite,
        showFavoriteButton = showFavouriteButton,
        onToggleFavoriteClick = if (onToggleFavoriteClick != null) {
            { onToggleFavoriteClick(ApiSearchSelection(result = displayResult, synopsis = synopsisForSave)) }
        } else null,
        totalEpisodes = displayResult.total,
        nextAiring = detailState?.nextAiringEpisode
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── İzle butonu ──
            if (displayResult.type == MediaType.Anime || displayResult.type == MediaType.TvShow || displayResult.type == MediaType.Movie) {
                KitsugiDetailActionButton(
                    text = stringResource(R.string.izle),
                    icon = Icons.PlayArrow,
                    onClick = {
                        val streamIds = displayResult.toStreamMediaIds(
                            resolvedRealMalId = detailState?.realMalId ?: displayResult.realMalId,
                            resolvedTmdbId = detailState?.tmdbId ?: resolvedTmdbId ?: displayResult.tmdbId
                        )
                        KitsugiStreamActivity.start(
                            context = context,
                            malId = streamIds.malId,
                            aniListId = streamIds.aniListId,
                            tmdbId = streamIds.tmdbId,
                            bangumiId = streamIds.bangumiId,
                            episode = 1,
                            isMovie = displayResult.type == MediaType.Movie,
                            mediaType = displayResult.type,
                            season = targetSeason,
                            title = displayResult.title,
                            posterUrl = displayResult.imageUrl,
                            titleEnglish = displayResult.titleEnglish,
                            titleRomaji = detailState?.titleRomaji,
                            titleNative = detailState?.titleNative,
                            synonyms = detailState?.synonyms.orEmpty(),
                            startYear = displayResult.year,
                            imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                                ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
                        )
                    },
                    modifier = Modifier
                        .focusRequester(leftPanelFocusRequester)
                        .focusProperties { right = tabBarFocusRequester }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // ── Oku butonu (Manga) ──
            if (displayResult.type == MediaType.Manga && onReadMangaClick != null) {
                KitsugiDetailActionButton(
                    text = stringResource(R.string.oku),
                    icon = Icons.AutoStories,
                    onClick = onReadMangaClick,
                    modifier = Modifier
                        .focusRequester(leftPanelFocusRequester)
                        .focusProperties { right = tabBarFocusRequester }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            val fallbackFocusMod = if (
                displayResult.type != MediaType.Anime &&
                displayResult.type != MediaType.TvShow &&
                displayResult.type != MediaType.Movie &&
                displayResult.type != MediaType.Manga
            ) Modifier.focusRequester(leftPanelFocusRequester) else Modifier

            // ── Düzenle / Listeye Ekle ──
            if (existingEntry != null) {
                ApiActionButton(
                    text = stringResource(R.string.duzenle_eb13ca5),
                    primary = true,
                    enabled = true,
                    modifier = fallbackFocusMod.focusProperties { right = tabBarFocusRequester },
                    onClick = { onEditClick(existingEntry, displayResult.source) }
                )
            } else {
                ApiActionButton(
                    text = stringResource(R.string.listeye_ekle),
                    primary = true,
                    enabled = true,
                    modifier = fallbackFocusMod.focusProperties { right = tabBarFocusRequester },
                    onClick = {
                        if (isConnected) {
                            onAddClick(ApiSearchSelection(result = displayResult, synopsis = synopsisForSave))
                        } else {
                            onShowAuthWarning()
                        }
                    }
                )
            }

            // ── Kaynakta Aç ──
            if (externalUrl != null) {
                val sourceLinkLabel = when (displayResult.source.lowercase()) {
                    "anilist"       -> "AniList'te Aç"
                    "jikan", "mal"  -> "MAL'da Gör"
                    "tmdb"          -> "TMDB'de Aç"
                    else            -> "Kaynakta Aç"
                }
                ApiActionButton(
                    text = sourceLinkLabel,
                    primary = false,
                    enabled = true,
                    modifier = Modifier.focusProperties { right = tabBarFocusRequester },
                    onClick = { uriHandler.openUri(externalUrl) }
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
