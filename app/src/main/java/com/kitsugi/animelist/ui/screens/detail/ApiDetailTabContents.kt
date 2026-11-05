package com.kitsugi.animelist.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata
import com.kitsugi.animelist.data.remote.StudioSourceSupport
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.StreamMediaIds
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity

import com.kitsugi.animelist.data.remote.JikanSearchResult
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Tab 0 — Overview (Bilgi) content for ApiResultDetailPage.
 */
@Composable
internal fun ApiDetailOverviewTab(
    result: JikanSearchResult,
    detail: KitsugiMediaDetail?,
    displaySynopsis: String?,
    isDetailLoading: Boolean,
    onSearchQuery: (String) -> Unit,
    onStudioClick: (id: Int, source: String, name: String?, url: String?) -> Unit,
    /** Manga yazarı çipi → kişi (Staff) detay sayfası. */
    onStaffClick: (id: Int, source: String, name: String?, url: String?) -> Unit,
    /** Dergi/serialization çipi → dergiye göre filtrelenmiş arama. */
    onMagazineClick: (id: Int, name: String) -> Unit,
    onGenreClick: (String) -> Unit = {},
    onTagClick: (KitsugiTag) -> Unit = {},
    onSearchBySeason: (DetailSeasonMetadata) -> Unit = {},
    onTranslateClick: () -> Unit,
    onCopyClick: () -> Unit,
    mdbListRatings: MdbListRatings? = null,
    mdbListLoading: Boolean = false,
    mdbListShowImdb: Boolean = true,
    mdbListShowTomatoes: Boolean = true,
    mdbListShowMetacritic: Boolean = true,
    mdbListShowAudience: Boolean = false,
    mdbListShowLetterboxd: Boolean = false,
    mdbListShowTmdb: Boolean = false,
    mdbListShowTrakt: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null,
    galleryItems: List<GalleryItem> = emptyList(),
    galleryLoading: Boolean = false,
    onGalleryItemRequest: ((items: List<GalleryItem>, index: Int) -> Unit)? = null,
    synopsisHasTranslation: Boolean = false,
    showSynopsisOriginal: Boolean = false,
    onToggleSynopsisOriginal: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.padding(horizontal = 0.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ApiSynopsisCard(
            synopsis = displaySynopsis,
            // Çeviri ayrı bir arka plan işidir; mevcut ham/çevrilmiş metni asla iskeletle kapatmaz.
            isLoading = isDetailLoading,
            onTranslateClick = onTranslateClick,
            onCopyClick = onCopyClick,
            onImageGalleryRequest = onImageGalleryRequest,
            hasTranslation = synopsisHasTranslation,
            showingOriginal = showSynopsisOriginal,
            onToggleOriginal = onToggleSynopsisOriginal
        )

        if (detail != null) {
            DetailOverviewStatsCard(detail = detail)
        }

        // MDBList external ratings — boşken (gizliyken) otomatik atlanır, komşu kartlar arası boşluk korunur
        ApiMdbListRatingCard(
            ratings = mdbListRatings,
            isLoading = mdbListLoading,
            showImdb = mdbListShowImdb,
            showTomatoes = mdbListShowTomatoes,
            showMetacritic = mdbListShowMetacritic,
            showAudience = mdbListShowAudience,
            showLetterboxd = mdbListShowLetterboxd,
            showTmdb = mdbListShowTmdb,
            showTrakt = mdbListShowTrakt,
            onSettingsClick = onSettingsClick,
            mediaTitle = result.title
        )

        if (!detail?.genres.isNullOrEmpty()) {
            ApiGenresChipRow(
                genres = detail!!.genres,
                tags = detail.tags,
                onGenreClick = onGenreClick
            )
        }

        if (detail != null && (detail.studios.isNotEmpty() || detail.producers.isNotEmpty() || detail.networks.isNotEmpty() || detail.serializations.isNotEmpty())) {
            // Rol bazlı yönlendirme: yazar çipleri kişi sayfasına, dergi çipleri
            // filtreli aramaya, stüdyo/yapımcı/yayıncı çipleri stüdyo sayfasına gider.
            val studioChipClick: (com.kitsugi.animelist.data.remote.KitsugiStudio) -> Unit = { chip ->
                when (chip.role) {
                    com.kitsugi.animelist.data.remote.StudioRole.AUTHOR ->
                        onStaffClick(chip.id, chip.source, chip.name, chip.imageUrl)
                    com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE -> {
                        // Dergi kimliği yalnızca MAL/Jikan kimlik uzayında anlamlıdır;
                        // diğer kaynaklarda (örn. Bangumi şirket ID'si) ad ile aranır.
                        val magSource = chip.source.lowercase(java.util.Locale.ROOT).trim()
                        val safeMagId = chip.id.takeIf {
                            it > 0 && (magSource == "jikan" || magSource == "mal" || magSource == "myanimelist")
                        } ?: 0
                        onMagazineClick(safeMagId, chip.name)
                    }
                    else -> {
                        val resolvedSource = resolveStudioClickSource(
                            studioSource = chip.source,
                            mediaSource = result.source.lowercase(),
                            mediaType = result.type,
                            role = chip.role
                        )
                        onStudioClick(chip.id, resolvedSource, chip.name, chip.imageUrl)
                    }
                }
            }
            KitsugiStudiosCard(
                studios = detail.studios,
                producers = detail.producers,
                networks = detail.networks,
                onStudioClick = studioChipClick,
                onProducerClick = studioChipClick,
                onNetworkClick = studioChipClick,
                serializations = detail.serializations,
                onSerializationClick = studioChipClick
            )
        }

        if (!detail?.tags.isNullOrEmpty()) {
            KitsugiTagsCard(
                tags = detail!!.tags,
                onTagClick = onTagClick
            )
        }

        if (detail != null) {
            ApiInfoSection(
                detail = detail,
                mediaType = result.type,
                fallbackYear = result.year,
                onSearchQuery = onSearchQuery,
                onSeasonClick = onSearchBySeason
            )
        }

        if (detail != null && (detail.streamingLinks.isNotEmpty() || detail.externalLinks.isNotEmpty())) {
            KitsugiLinksCard(
                streamingLinks = detail.streamingLinks,
                externalLinks = detail.externalLinks
            )
        }

    }
}

/** Resolve a studio/company ID using its own provider namespace before the media's source. */
internal fun resolveStudioClickSource(
    studioSource: String?,
    mediaSource: String,
    mediaType: MediaType,
    role: com.kitsugi.animelist.data.remote.StudioRole? = null
): String = StudioSourceSupport.resolveClickSource(studioSource, mediaSource, mediaType, role)

/**
 * Visible tab 9 — Episodes content for ApiResultDetailPage.
 */
@Composable
internal fun ApiDetailEpisodesTab(
    state: DetailTabState<List<KitsugiStreamingEpisode>>,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    targetSeason: Int?,
    totalSeasons: Int?,
    streamIds: StreamMediaIds,
    mediaType: MediaType,
    displayTitle: String,
    displaySource: String,
    displayImageUrl: String?,
    displayTitleEnglish: String?,
    displayTitleRomaji: String?,
    displayTitleNative: String?,
    displaySynonyms: List<String> = emptyList(),
    imdbId: String? = null,
    displayYear: Int?,
    isMovie: Boolean = false,
    isAdult: Boolean = false,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeOptionsRequested: (KitsugiStreamingEpisode) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val streamMalId = streamIds.malId
    val streamAniListId = streamIds.aniListId
    val currentAnimeId = (streamAniListId ?: streamMalId ?: streamIds.tmdbId ?: streamIds.bangumiId)
        ?.toString().orEmpty()

    EpisodesTabContent(
        state = state,
        animeId = currentAnimeId,
        isAdult = isAdult,
        episodeRatings = episodeRatings,
        targetSeason = targetSeason,
        totalSeasons = totalSeasons,
        onSeasonSelected = onSeasonSelected,
        onEpisodeClick = { episode ->
            val epNum = episode.episodeNumber
            if (epNum != null && epNum > 0) {
                val completedDownload = com.kitsugi.animelist.data.local.AnimeDownloadManager.downloads.value.find {
                    it.animeId == currentAnimeId &&
                    it.episode == epNum &&
                    (it.season == (targetSeason ?: 1) || it.season == (episode.seasonNumber ?: 1)) &&
                    it.status == com.kitsugi.animelist.data.model.AnimeDownload.Status.COMPLETED &&
                    it.localPath != null
                }
                if (completedDownload != null) {
                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = context,
                        videoUrl = "file://${completedDownload.localPath}",
                        title = context.getString(R.string.completeddownload_animetitle_bolum_completed, completedDownload.animeTitle, completedDownload.episode),
                        headers = emptyMap(),
                        subtitles = emptyList(),
                        malId = streamIds.malId, aniListId = streamIds.aniListId,
                        tmdbId = streamIds.tmdbId, bangumiId = streamIds.bangumiId, imdbId = imdbId,
                        season = episode.seasonNumber ?: targetSeason ?: 1, episode = epNum,
                        animeTitle = displayTitle, titleEnglish = displayTitleEnglish,
                        titleRomaji = displayTitleRomaji, titleNative = displayTitleNative,
                        synonyms = displaySynonyms, startYear = displayYear, isMovie = isMovie
                    )
                } else {
                    KitsugiStreamActivity.start(
                        context = context,
                        malId = streamMalId,
                        aniListId = streamAniListId,
                        tmdbId = streamIds.tmdbId,
                        bangumiId = streamIds.bangumiId,
                        imdbId = imdbId,
                        mediaType = mediaType,
                        episode = epNum,
                        season = targetSeason ?: 1,
                        isMovie = isMovie,
                        title = displayTitle,
                        posterUrl = displayImageUrl,
                        titleEnglish = displayTitleEnglish,
                        titleRomaji = displayTitleRomaji,
                        titleNative = displayTitleNative,
                        synonyms = displaySynonyms,
                        startYear = displayYear
                    )
                }
            } else {
                onEpisodeOptionsRequested(episode)
            }
        },
        onDownloadClick = { episode ->
            val epNum = episode.episodeNumber
            if (epNum != null && epNum > 0) {
                KitsugiStreamActivity.start(
                    context = context,
                    malId = streamMalId,
                    aniListId = streamAniListId,
                    tmdbId = streamIds.tmdbId,
                    bangumiId = streamIds.bangumiId,
                    imdbId = imdbId,
                    mediaType = mediaType,
                    episode = epNum,
                    season = targetSeason ?: 1,
                    isMovie = isMovie,
                    title = displayTitle,
                    posterUrl = displayImageUrl,
                    titleEnglish = displayTitleEnglish,
                    titleRomaji = displayTitleRomaji,
                    titleNative = displayTitleNative,
                    synonyms = displaySynonyms,
                    startYear = displayYear,
                    isDownloadMode = true
                )
            }
        },
        onRatingClick = { season, episode ->
            val tmdbId = streamIds.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                val url = "https://www.themoviedb.org/tv/$tmdbId/season/$season/episode/$episode"
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    runCatching { uriHandler.openUri(url) }
                }
            }
        }
    )
}
