package com.kitsugi.animelist

import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.utils.parseToMediaType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.matches
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.data.remote.firstMatchingInSource
import com.kitsugi.animelist.data.remote.firstMatchingInTargetOrResultSource
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.ui.app.AppViewModel
import com.kitsugi.animelist.ui.screens.detail.StudioDetailPage
import com.kitsugi.animelist.ui.screens.detail.StaffDetailPage
import com.kitsugi.animelist.ui.screens.detail.CharacterDetailPage
import com.kitsugi.animelist.ui.screens.detail.ApiResultDetailPage
import com.kitsugi.animelist.ui.screens.detail.MediaEntryDetailPage
import com.kitsugi.animelist.ui.screens.search.DetailSearchFilterRequest
import com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.KitsugiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.R

@Composable
fun AppRootDetailPages(
    key: AppStateKey,
    navState: AppNavigationState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    settingsDataStore: SettingsDataStore,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    mediaRepository: MediaEntryRepository,
    coroutineScope: CoroutineScope,
    updateViewModel: com.kitsugi.animelist.core.update.AppUpdateViewModel,
    addonViewModel: com.kitsugi.animelist.ui.app.AddonViewModel = viewModel(),
    onEditEntry: (MediaEntry) -> Unit,
    /** Detay sayfasından düzenleme; yalnızca aynı kaynakta bulunan üyelik kaydı açılır. */
    onEditEntryWithSource: (MediaEntry, String) -> Unit = { entry, _ -> onEditEntry(entry) },
    onDeleteEntry: (MediaEntry) -> Unit,
    onIncrementEntryProgress: (MediaEntry) -> Unit,
    onAddApiSelectionToList: (ApiSearchSelection) -> Unit,
    onLoginBangumi: () -> Unit = {},
    onReadMangaClick: (MediaEntry, MangaMappingEntity?) -> Unit,
    /**
     * Geçmiş sayfasındaki **okunan** bir bölüme dokunulduğunda manga detay sayfasını açar.
     * Kaynak adı yüklü eklentiler arasında bulunamazsa kullanıcıya bilgi verilir.
     */
    onOpenMangaReadHistory: (source: String, mangaUrl: String, mangaTitle: String, coverUrl: String?) -> Unit = { _, _, _, _ -> },
    triggerSearch: (String) -> Unit,
    triggerSearchByGenre: (String) -> Unit = {},
    triggerSearchByTag: (String) -> Unit = {},
    triggerDetailSearchFilter: (DetailSearchFilterRequest) -> Unit = {},
    onScrollReset: () -> Unit = {}
) {
    val context = LocalContext.current
    when (key) {
        is AppStateKey.StudioDetail -> {
            navState.stateHolder.SaveableStateProvider(
                key = "studio_${key.depth}_${key.source}_${key.studioId}_${key.name.orEmpty()}"
            ) {
                StudioDetailPage(
                    studioId = key.studioId,
                    source = key.source,
                    onBackClick = { navState.popDetailStack() },
                    onMediaClick = { mediaId, mediaType, mediaSource ->
                        val existingEntry = mediaEntries.firstMatchingInSource(
                            mediaId = mediaId,
                            mediaSource = mediaSource
                        )
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            val searchType = mediaType.parseToMediaType()
                            val searchResult = JikanSearchResult(
                                malId = mediaId,
                                title = context.getString(R.string.yukleniyor),
                                subtitle = "",
                                type = searchType,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = null,
                                year = null,
                                source = mediaSource,
                                bangumiSubjectType = if (canonicalMediaSourceId(mediaSource) == "bangumi") {
                                    BangumiIdNamespace.subjectTypeFor(searchType)
                                } else null
                            )
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(searchResult))
                        }
                    },
                    name = key.name,
                    imageUrl = key.imageUrl,
                    titleLanguage = appSettings.titleLanguage,
                    preferredTranslator = appSettings.preferredTranslator
                )
            }
        }

        is AppStateKey.StaffDetail -> {
            navState.stateHolder.SaveableStateProvider(key = "staff_${key.depth}_${key.source}_${key.staffId}") {
                StaffDetailPage(
                    staffId = key.staffId,
                    source = key.source,
                    onBackClick = { navState.popDetailStack() },
                    onCharacterClick = { charId, charSource, charName, charImageUrl ->
                        navState.navigateToDetail(DetailScreen.CharacterDetail(charId, charSource, charName, charImageUrl))
                    },
                    onMediaClick = { mediaId, mediaType, mediaSource ->
                        val existingEntry = mediaEntries.firstMatchingInSource(
                            mediaId = mediaId,
                            mediaSource = mediaSource
                        )
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            val searchType = mediaType.parseToMediaType()
                            val searchResult = JikanSearchResult(
                                malId = mediaId,
                                title = context.getString(R.string.yukleniyor),
                                subtitle = "",
                                type = searchType,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = null,
                                year = null,
                                source = mediaSource,
                                bangumiSubjectType = if (canonicalMediaSourceId(mediaSource) == "bangumi") {
                                    BangumiIdNamespace.subjectTypeFor(searchType)
                                } else null
                            )
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(searchResult))
                        }
                    },
                    name = key.name,
                    imageUrl = key.imageUrl,
                    titleLanguage = appSettings.titleLanguage,
                    staffNameLanguage = appSettings.staffNameLanguage,
                    preferredTranslator = appSettings.preferredTranslator
                )
            }
        }

        is AppStateKey.CharacterDetail -> {
            navState.stateHolder.SaveableStateProvider(
                key = "char_${key.depth}_${key.source}_${key.characterId}_${key.name.orEmpty()}_${key.isRealMediaRole}"
            ) {
                CharacterDetailPage(
                    characterId = key.characterId,
                    source = key.source,
                    isRealMediaRole = key.isRealMediaRole,
                    onBackClick = { navState.popDetailStack() },
                    onStaffClick = { staffId, staffSource, staffName, staffImageUrl ->
                        navState.navigateToDetail(DetailScreen.StaffDetail(staffId, staffSource, staffName, staffImageUrl))
                    },
                    onCharacterClick = { charId, charSource, charName, charImageUrl ->
                        navState.navigateToDetail(DetailScreen.CharacterDetail(charId, charSource, charName, charImageUrl))
                    },
                    onMediaClick = { mediaId, mediaType, mediaSource ->
                        val existingEntry = mediaEntries.firstMatchingInSource(
                            mediaId = mediaId,
                            mediaSource = mediaSource
                        )
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            val searchType = mediaType.parseToMediaType()
                            val searchResult = JikanSearchResult(
                                malId = mediaId,
                                title = context.getString(R.string.yukleniyor),
                                subtitle = "",
                                type = searchType,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = null,
                                year = null,
                                source = mediaSource,
                                bangumiSubjectType = if (canonicalMediaSourceId(mediaSource) == "bangumi") {
                                    BangumiIdNamespace.subjectTypeFor(searchType)
                                } else null
                            )
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(searchResult))
                        }
                    },
                    name = key.name,
                    imageUrl = key.imageUrl,
                    titleLanguage = appSettings.titleLanguage,
                    staffNameLanguage = appSettings.staffNameLanguage,
                    preferredTranslator = appSettings.preferredTranslator
                )
            }
        }

        is AppStateKey.ApiResultDetail -> {
            navState.stateHolder.SaveableStateProvider(key = "api_${key.depth}_${key.result.source}_${key.result.malId}") {
                val existingApiEntry = remember(mediaEntries, key.result) {
                    mediaEntries.firstMatchingInSource(key.result)
                }
                val findExistingApiEntry = remember(mediaEntries) {
                    { r: JikanSearchResult -> mediaEntries.firstOrNull { entry -> entry.matchesInSource(r) } }
                }
                ApiResultDetailPage(
                    result = key.result,
                    existingEntry = existingApiEntry,
                    // Ekleme butonu ve mevcut üyelik aynı sağlayıcı kapsamını kullanır.
                    findExistingEntry = findExistingApiEntry,
                    onBackClick = { navState.popDetailStack() },
                    onAddClick = { selection ->
                        onAddApiSelectionToList(selection)
                    },
                    onEditClick = { entry, pageSource ->
                        onEditEntryWithSource(entry, pageSource)
                    },
                    onRelationClick = { result ->
                        val existingEntry = mediaEntries.firstMatchingInSource(result)
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
                        }
                    },
                    onCharacterClick = { char ->
                        navState.navigateToDetail(DetailScreen.CharacterDetail(char.id, char.source, char.name, char.imageUrl, isRealMediaRole = char.isRealMediaRole))
                    },
                    onStaffClick = { staffId, staffSource, staffName, staffImageUrl ->
                        navState.navigateToDetail(DetailScreen.StaffDetail(staffId, staffSource, staffName, staffImageUrl))
                    },
                    onStudioClick = { studioId, studioSource, studioName, studioImageUrl ->
                        navState.navigateToDetail(DetailScreen.StudioDetail(studioId, studioSource, studioName, studioImageUrl))
                    },
                    onMagazineClick = { magazineId, magazineName ->
                        triggerDetailSearchFilter(
                            DetailSearchFilterRequest(
                                source = key.result.source,
                                mediaType = key.result.type,
                                magazineId = magazineId.takeIf { it > 0 },
                                magazineName = magazineName
                            )
                        )
                    },
                    onUserProfileClick = { userId, username, avatarUrl, profileSource ->
                        navState.navigateToDetail(DetailScreen.UserProfile(userId, username, avatarUrl, profileSource))
                    },
                    onSearchQuery = triggerSearch,
                    onSearchByGenre = { genre ->
                        triggerDetailSearchFilter(
                            DetailSearchFilterRequest(source = key.result.source, mediaType = key.result.type, genre = genre)
                        )
                    },
                    onSearchByTag = { tag ->
                        triggerDetailSearchFilter(
                            DetailSearchFilterRequest(
                                source = key.result.source,
                                mediaType = key.result.type,
                                tag = tag.name,
                                tagSource = tag.source,
                                keywordId = tag.id?.takeIf { CanonicalFilterBridge.isTmdbTagSource(tag.source) }
                            )
                        )
                    },
                    onSearchBySeason = { season ->
                        triggerDetailSearchFilter(
                            DetailSearchFilterRequest(
                                source = key.result.source,
                                mediaType = key.result.type,
                                season = season.apiSeason,
                                year = season.year
                            )
                        )
                    },
                    titleLanguage = appSettings.titleLanguage,
                    staffNameLanguage = appSettings.staffNameLanguage,
                    scoreFormat = appSettings.scoreFormat,
                    hideScores = appSettings.hideScores,
                    showAnimeLogos = appSettings.showAnimeLogos,
                    isAniListConnected = authViewModel.isAniListConnected,
                    isMalConnected = authViewModel.isMalConnected,
                    isSimklConnected = authViewModel.isSimklConnected,
                    isBangumiConnected = authViewModel.isBangumiConnected,
                    isTmdbConnected = authViewModel.isTmdbConnected,
                    onLoginBangumi = onLoginBangumi,
                    onLoginAniList = {
                        authViewModel.startExternalAuth("anilist")
                    },
                    onLoginMal = {
                        authViewModel.startMalOAuthWebSession()
                    },
                    onLoginSimkl = {
                        authViewModel.startExternalAuth("simkl")
                    },
                    onReadMangaClick = { mediaTitleLanguage, english, romaji, native, synonyms ->
                        // Arama dili: ayar "sabit dil" ise o dil, değilse ekranın başlık dili.
                        val effectiveLanguage = if (appSettings.mangaSearchFollowTitleLanguage) {
                            mediaTitleLanguage
                        } else {
                            appSettings.mangaSearchTitleLanguage
                        }
                        val titleSet = com.kitsugi.animelist.data.manga.MangaTitleSet(
                            localTitle = key.result.title,
                            englishTitle = english ?: key.result.titleEnglish,
                            romajiTitle = romaji ?: key.result.titleRomaji ?: key.result.title,
                            nativeTitle = native ?: key.result.titleJapanese,
                            synonyms = synonyms
                        )
                        val variants = titleSet.queries(
                            titleLanguage = appSettings.titleLanguage,
                            searchLanguage = com.kitsugi.animelist.data.manga.MangaSearchLanguage.fromId(
                                effectiveLanguage
                            ),
                            // Sabit dil seçiliyse başlık dilini takip etme.
                            followTitleLanguage = appSettings.mangaSearchFollowTitleLanguage
                        )
                        navState.openMangaBrowse(
                            query = variants.firstOrNull(),
                            alternates = listOfNotNull(
                                key.result.titleRomaji,
                                key.result.titleEnglish,
                                key.result.titleJapanese,
                            ),
                            titleVariants = variants,
                            titleSet = titleSet
                        )
                    },
                    mdbListShowImdb = appSettings.mdbListShowImdb,
                    mdbListShowTomatoes = appSettings.mdbListShowTomatoes,
                    mdbListShowMetacritic = appSettings.mdbListShowMetacritic,
                    mdbListShowAudience = appSettings.mdbListShowAudience,
                    mdbListShowLetterboxd = appSettings.mdbListShowLetterboxd,
                    mdbListShowTmdb = appSettings.mdbListShowTmdb,
                    mdbListShowTrakt = appSettings.mdbListShowTrakt,
                    settingsDataStore = settingsDataStore,
                    onToggleFavoriteClick = { selection ->
                        val result = selection.result
                        val targetSource = AppViewModel.resolvePrimarySourceForApiResult(
                            result = result,
                            isAniListConnected = authViewModel.isAniListConnected,
                            isMalConnected = authViewModel.isMalConnected,
                            isSimklConnected = authViewModel.isSimklConnected,
                            isBangumiConnected = authViewModel.isBangumiConnected,
                            isKitsuConnected = authViewModel.isKitsuConnected,
                            isShikimoriConnected = authViewModel.isShikimoriConnected,
                            isTmdbConnected = authViewModel.isTmdbConnected
                        )
                        val entry = mediaEntries.firstMatchingInTargetOrResultSource(result, targetSource)
                        if (entry != null) {
                            // Toggle existing entry
                            val updatedEntry = entry.copy(isFavorite = !entry.isFavorite)
                            coroutineScope.launch {
                                mediaRepository.update(updatedEntry)
                            }
                        } else {
                            // Kalp de normal "listeye ekle" zincirini kullanır. Ayrı bir doğrudan
                            // insert, TMDB katalog satırını sahte bir liste kaynağına dönüştürüyor;
                            // sağlayıcı seçimini ve çapraz kimlik çözümünü atlıyordu.
                            appViewModel.addApiSelectionToList(
                                selection = selection,
                                currentEntries = mediaEntries,
                                repository = mediaRepository,
                                isAniListConnected = authViewModel.isAniListConnected,
                                isMalConnected = authViewModel.isMalConnected,
                                isSimklConnected = authViewModel.isSimklConnected,
                                isBangumiConnected = authViewModel.isBangumiConnected,
                                isTmdbConnected = authViewModel.isTmdbConnected,
                                isKitsuConnected = authViewModel.isKitsuConnected,
                                isShikimoriConnected = authViewModel.isShikimoriConnected,
                                markFavorite = true
                            )
                        }
                    }
                )
            }
        }

        is AppStateKey.MediaDetail -> {
            val entry = mediaEntries.firstOrNull { it.id == key.entryId }
            if (entry != null) {
                navState.stateHolder.SaveableStateProvider(key = "media_${key.depth}_${key.entryId}") {
                    MediaEntryDetailPage(
                        entry = entry,
                        settingsDataStore = settingsDataStore,
                        onBackClick = { navState.popDetailStack() },
                        onIncrementProgressClick = {
                            onIncrementEntryProgress(entry)
                        },
                        onToggleFavoriteClick = {
                            val updatedEntry = entry.copy(
                                isFavorite = !entry.isFavorite
                            )
                            coroutineScope.launch {
                                mediaRepository.update(updatedEntry)
                            }
                        },
                        onSynopsisLoaded = { synopsis ->
                            if (entry.synopsis != synopsis && synopsis.isNotBlank()) {
                                coroutineScope.launch {
                                    mediaRepository.updateSynopsis(entry.id, entry.synopsis, synopsis)
                                }
                            }
                        },
                        onEditClick = {
                            onEditEntry(entry)
                        },
                        onDeleteClick = {
                            onDeleteEntry(entry)
                        },
                        onRelationClick = { result ->
                            val existingEntry = mediaEntries.firstMatchingInSource(result)
                            if (existingEntry != null) {
                                navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                            } else {
                                navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
                            }
                        },
                        onCharacterClick = { char ->
                            navState.navigateToDetail(DetailScreen.CharacterDetail(char.id, char.source, char.name, char.imageUrl, isRealMediaRole = char.isRealMediaRole))
                        },
                        onStaffClick = { staffId, staffSource, staffName, staffImageUrl ->
                            navState.navigateToDetail(DetailScreen.StaffDetail(staffId, staffSource, staffName, staffImageUrl))
                        },
                        onStudioClick = { studioId, studioSource, studioName, studioImageUrl ->
                            navState.navigateToDetail(DetailScreen.StudioDetail(studioId, studioSource, studioName, studioImageUrl))
                        },
                        onMagazineClick = { magazineId, magazineName ->
                            triggerDetailSearchFilter(
                                DetailSearchFilterRequest(
                                    source = entry.source,
                                    mediaType = entry.type,
                                    magazineId = magazineId.takeIf { it > 0 },
                                    magazineName = magazineName
                                )
                            )
                        },
                        onUserProfileClick = { userId, username, avatarUrl, profileSource ->
                            navState.navigateToDetail(DetailScreen.UserProfile(userId, username, avatarUrl, profileSource))
                        },
                        onSearchQuery = triggerSearch,
                        onSearchByGenre = { genre ->
                            triggerDetailSearchFilter(
                                DetailSearchFilterRequest(source = entry.source, mediaType = entry.type, genre = genre)
                            )
                        },
                        onSearchByTag = { tag ->
                            triggerDetailSearchFilter(
                                DetailSearchFilterRequest(
                                    source = entry.source,
                                    mediaType = entry.type,
                                    tag = tag.name,
                                    tagSource = tag.source,
                                    keywordId = tag.id?.takeIf { CanonicalFilterBridge.isTmdbTagSource(tag.source) }
                                )
                            )
                        },
                        onSearchBySeason = { season ->
                            triggerDetailSearchFilter(
                                DetailSearchFilterRequest(
                                    source = entry.source,
                                    mediaType = entry.type,
                                    season = season.apiSeason,
                                    year = season.year
                                )
                            )
                        },
                        titleLanguage = appSettings.titleLanguage,
                        staffNameLanguage = appSettings.staffNameLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        showAnimeLogos = appSettings.showAnimeLogos,
                        blurAdultMedia = appSettings.blurAdultMedia,
                        onReadMangaClick = { mapping ->
                            onReadMangaClick(entry, mapping)
                        },
                        // "Oku" (eşleme yok) → arama. Dil kararı BURADA verilir:
                        // ayar "sabit arama dili" ise o dil, değilse başlık dili.
                        onSearchMangaByTitles = { entryVariants ->
                            if (entry.type == com.kitsugi.animelist.model.MediaType.Manga) {
                                val titleSet = com.kitsugi.animelist.data.manga.MangaTitleSet(
                                    localTitle = entry.title,
                                    englishTitle = entry.titleEnglish,
                                    romajiTitle = entry.title,
                                    nativeTitle = entry.titleJapanese
                                )
                                val variants = titleSet.queries(
                                    titleLanguage = appSettings.titleLanguage,
                                    searchLanguage = com.kitsugi.animelist.data.manga.MangaSearchLanguage.fromId(
                                        if (appSettings.mangaSearchFollowTitleLanguage) {
                                            appSettings.titleLanguage
                                        } else {
                                            appSettings.mangaSearchTitleLanguage
                                        }
                                    ),
                                    followTitleLanguage = appSettings.mangaSearchFollowTitleLanguage
                                ).ifEmpty { entryVariants }
                                navState.openMangaBrowse(
                                    query = variants.firstOrNull(),
                                    mediaId = entry.id,
                                    alternates = listOfNotNull(entry.titleEnglish, entry.titleJapanese),
                                    titleVariants = variants,
                                    titleSet = titleSet
                                )
                            }
                        },
                        preferredTranslator = appSettings.preferredTranslator
                    )
                }
            }
        }
        is AppStateKey.AiringCalendar -> {
            navState.stateHolder.SaveableStateProvider(key = "airing_calendar_${key.depth}_${key.preferredSource}") {
                com.kitsugi.animelist.ui.screens.explore.KitsugiAiringCalendarScreen(
                    currentEntries = mediaEntries,
                    titleLanguage = appSettings.titleLanguage,
                    preferredSource = key.preferredSource ?: "anilist",
                    onOpenAiringEntry = { airingEntry ->
                        val result = airingEntry.toJikanSearchResult(preferredSource = key.preferredSource)
                        val existingEntry = mediaEntries.firstMatchingInSource(result)
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
                        }
                    },
                    onBackClick = { navState.popDetailStack() }
                )
            }
        }

        is AppStateKey.Stats -> {
            navState.stateHolder.SaveableStateProvider(key = "stats_${key.depth}") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val db = KitsugiDatabase.getDatabase(context)
                val statsVm: com.kitsugi.animelist.ui.screens.stats.StatsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return com.kitsugi.animelist.ui.screens.stats.StatsViewModel(db.mediaEntryDao()) as T
                        }
                    }
                )
                val uiState by statsVm.uiState.collectAsState()
                com.kitsugi.animelist.ui.screens.stats.StatsScreen(
                    uiState = uiState,
                    onBack = { navState.popDetailStack() }
                )
            }
        }

        is AppStateKey.Favourites -> {
            navState.stateHolder.SaveableStateProvider(key = "favourites_${key.depth}") {
                com.kitsugi.animelist.ui.screens.profile.tabs.FavouritesScreen(
                    mediaEntries = mediaEntries,
                    titleLanguage = appSettings.titleLanguage,
                    scoreFormat = appSettings.scoreFormat,
                    hideScores = appSettings.hideScores,
                    blurAdultMedia = appSettings.blurAdultMedia,
                    onBackClick = { navState.popDetailStack() },
                    onEntryClick = { entry ->
                        navState.navigateToDetail(DetailScreen.MediaDetail(entry.id))
                    }
                )
            }
        }

        is AppStateKey.About -> {
            navState.stateHolder.SaveableStateProvider(key = "about_${key.depth}") {
                com.kitsugi.animelist.ui.screens.more.AboutScreen(
                    autoUpdateCheckEnabled = appSettings.autoUpdateCheckEnabled,
                    onAutoUpdateCheckEnabledChanged = { enabled ->
                        coroutineScope.launch {
                            settingsDataStore.setAutoUpdateCheckEnabled(enabled)
                        }
                    },
                    onCheckForUpdatesClick = {
                        updateViewModel.checkForUpdates(silent = false, force = true)
                    },
                    onCheckForPluginUpdatesClick = {
                        addonViewModel.checkForPluginUpdates()
                    },
                    onBackClick = { navState.popDetailStack() }
                )
            }
        }

        is AppStateKey.UserProfile -> {
            navState.stateHolder.SaveableStateProvider(key = "user_profile_${key.depth}_${key.source}_${key.userId}_${key.username.orEmpty()}") {
                val profileSource = key.source.lowercase()
                val onProfileFavoriteMediaClick: (Int, MediaType, String, String, String?) -> Unit = { mediaId, mediaType, favSource, title, imageUrl ->
                    val stableId = if (favSource == "anilist") mediaId + 100_000_000 else mediaId
                    // isAdult, mevcut liste kaydından türetilerek blur tutarlılığı sağlanır.
                    val resolvedIsAdult = mediaEntries.firstOrNull { entry ->
                        entry.malId == stableId && entry.source.equals(favSource, ignoreCase = true)
                    }?.isAdult ?: false
                    val searchResult = com.kitsugi.animelist.data.remote.JikanSearchResult(
                        malId = stableId,
                        title = title.ifBlank { KitsugiStrings.get(R.string.loading) },
                        subtitle = "",
                        type = mediaType,
                        total = null,
                        score = null,
                        isAdult = resolvedIsAdult,
                        imageUrl = imageUrl,
                        year = null,
                        source = favSource
                    )
                    val existingEntry = mediaEntries.firstMatchingInSource(searchResult)
                    if (existingEntry != null) {
                        navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                    } else {
                        navState.navigateToDetail(DetailScreen.ApiResultDetail(searchResult))
                    }
                }
                if (profileSource == "mal" || profileSource == "jikan" || profileSource == "kitsu" || profileSource == "simkl") {
                    // MAL / Kitsu / Simkl kullanıcıları → uygulama içi, platforma özel profil
                    com.kitsugi.animelist.ui.screens.profile.PlatformUserProfileScreen(
                        source = profileSource,
                        userId = key.userId,
                        username = key.username,
                        avatarUrl = key.avatarUrl,
                        appSettings = appSettings,
                        mediaEntries = mediaEntries,
                        onBackClick = { navState.popDetailStack() },
                        onEntryClick = { entry ->
                            navState.navigateToDetail(DetailScreen.MediaDetail(entry.id))
                        },
                        onFavoriteMediaClick = onProfileFavoriteMediaClick,
                        onFavoriteCharacterClick = { charId, charSource, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.CharacterDetail(charId, charSource, name, imageUrl))
                        },
                        onFavoriteStaffClick = { staffId, staffSource, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.StaffDetail(staffId, staffSource, name, imageUrl))
                        },
                        onFavoriteStudioClick = { studioId, studioSource, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.StudioDetail(studioId, studioSource, name, imageUrl))
                        },
                        onGenreClick = triggerSearch,
                        onUserProfileClick = { friendId, friendName, friendAvatar ->
                            navState.navigateToDetail(DetailScreen.UserProfile(friendId, friendName, friendAvatar, profileSource))
                        },
                        onScrollReset = onScrollReset
                    )
                } else {
                    com.kitsugi.animelist.ui.screens.profile.KitsugiUserProfileScreen(
                    onScrollReset = onScrollReset,
                    userId = key.userId,
                    fallbackUsername = key.username,
                    fallbackAvatar = key.avatarUrl,
                    appSettings = appSettings,
                    mediaEntries = mediaEntries,
                    onBackClick = { navState.popDetailStack() },
                    onFavoriteMediaClick = onProfileFavoriteMediaClick,
                    onFavoriteCharacterClick = { charId, source, name, imageUrl ->
                        navState.navigateToDetail(DetailScreen.CharacterDetail(charId, source, name, imageUrl))
                    },
                    onFavoriteStaffClick = { staffId, source, name, imageUrl ->
                        navState.navigateToDetail(DetailScreen.StaffDetail(staffId, source, name, imageUrl))
                    },
                    onFavoriteStudioClick = { studioId, source, name, imageUrl ->
                        navState.navigateToDetail(DetailScreen.StudioDetail(studioId, source, name, imageUrl))
                    },
                    onUserProfileClick = { newUserId, newUsername, newAvatar ->
                        navState.navigateToDetail(DetailScreen.UserProfile(newUserId, newUsername, newAvatar))
                    },
                    onGenreClick = { genre -> triggerSearch(genre) },
                    onTagClick = { tag -> triggerSearch(tag) },
                    onOpenUserMediaList = { uId, mType ->
                        navState.navigateToDetail(DetailScreen.UserMediaList(uId, key.username ?: "Kullanıcı", mType))
                    }
                )
                }
            }
        }

        is AppStateKey.UserMediaList -> {
            navState.stateHolder.SaveableStateProvider(key = "user_media_list_${key.depth}_${key.userId}_${key.initialMediaType.name}") {
                com.kitsugi.animelist.ui.screens.profile.KitsugiUserMediaListScreen(
                    userId = key.userId,
                    username = key.username,
                    initialMediaType = key.initialMediaType,
                    appSettings = appSettings,
                    mediaEntries = mediaEntries,
                    onBackClick = { navState.popDetailStack() },
                    onMediaClick = { result ->
                        navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
                    },
                    onLocalEntryClick = { entry ->
                        navState.navigateToDetail(DetailScreen.MediaDetail(entry.id))
                    },
                    onListLayoutChange = { layoutId ->
                        coroutineScope.launch {
                            settingsDataStore.setSelectedListLayoutId(layoutId)
                        }
                    }
                )
            }
        }

        is AppStateKey.Notifications -> {
            navState.stateHolder.SaveableStateProvider(key = "notifications_${key.depth}") {
                com.kitsugi.animelist.ui.screens.notifications.KitsugiNotificationsScreen(
                    mediaEntries = mediaEntries,
                    isAniListConnected = authViewModel.isAniListConnected,
                    isMalConnected = authViewModel.isMalConnected,
                    isSimklConnected = authViewModel.isSimklConnected,
                    isKitsuConnected = authViewModel.isKitsuConnected,
                    isShikimoriConnected = authViewModel.isShikimoriConnected,
                    isBangumiConnected = authViewModel.isBangumiConnected,
                    isTmdbConnected = authViewModel.isTmdbConnected,
                    tmdbUsername = authViewModel.tmdbUsername,
                    onBack = { navState.popDetailStack() },
                    malSessionRefreshToken = authViewModel.malSessionReadyToken,
                    // Akıllı bildirim butonu: butona basılan sekmede seçili kaynak
                    // hangisiyse bildirimler doğrudan o kaynağın sekmesinde açılır.
                    preferredSource = key.preferredSource,
                    onOpenMalAccountSettings = {
                        if (authViewModel.isMalConnected) {
                            // Bağlı bir MAL hesabı VAR: web oturumu (bildirimler) aynı hesaba
                            // burada, tek adımda bağlanır — ayarlar sayfasına gitmeye gerek yok.
                            authViewModel.ensureMalUnifiedSession()
                        } else {
                            navState.popDetailStack()
                            appViewModel.selectTab(com.kitsugi.animelist.ui.navigation.MainTab.Settings)
                            appViewModel.requestSettingsRoute(com.kitsugi.animelist.ui.screens.settings.SettingsRoute.MalSettings)
                        }
                    },
                    onConnectPlatform = { platformIndex ->
                        // Bildirimler > "Hesabı Bağla": kaynagin kendi hesap sayfasina git.
                        // MAL (1) tek-oturum akisini kullanir; digerleri ayarlar rotasina gider.
                        if (platformIndex == 1) {
                            if (authViewModel.isMalConnected) {
                                authViewModel.ensureMalUnifiedSession()
                            } else {
                                navState.popDetailStack()
                                appViewModel.selectTab(com.kitsugi.animelist.ui.navigation.MainTab.Settings)
                                appViewModel.requestSettingsRoute(com.kitsugi.animelist.ui.screens.settings.SettingsRoute.MalSettings)
                            }
                        } else {
                            val route = when (platformIndex) {
                                0 -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.AniListSettings
                                2 -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.SimklSettings
                                3 -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.KitsuSettings
                                4 -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.ShikimoriSettings
                                5 -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.BangumiSettings
                                else -> com.kitsugi.animelist.ui.screens.settings.SettingsRoute.AccountConnections
                            }
                            navState.popDetailStack()
                            appViewModel.selectTab(com.kitsugi.animelist.ui.navigation.MainTab.Settings)
                            appViewModel.requestSettingsRoute(route)
                        }
                    },
                    onUserProfileClick = { userId, username, avatarUrl ->
                        navState.navigateToDetail(DetailScreen.UserProfile(userId, username, avatarUrl))
                    },
                    onOpenApiDetail = { mediaId, source, mediaTypeStr, title, imageUrl ->
                        val stableId = when {
                            source.equals("anilist", ignoreCase = true) && mediaId < 100_000_000 -> mediaId + 100_000_000
                            source.equals("kitsu", ignoreCase = true) && mediaId < com.kitsugi.animelist.data.remote.KitsuExploreClient.ID_OFFSET ->
                                mediaId + com.kitsugi.animelist.data.remote.KitsuExploreClient.ID_OFFSET
                            else -> mediaId
                        }
                        val existingEntry = mediaEntries.firstMatchingInSource(mediaId = stableId, mediaSource = source)
                        if (existingEntry != null) {
                            navState.navigateToDetail(DetailScreen.MediaDetail(existingEntry.id))
                        } else {
                            val mediaType = when (mediaTypeStr?.lowercase()) {
                                "manga" -> MediaType.Manga
                                "movie" -> MediaType.Movie
                                "tv" -> MediaType.TvShow
                                else -> MediaType.Anime
                            }
                            val searchResult = JikanSearchResult(
                                malId = stableId,
                                title = title?.takeIf { it.isNotBlank() } ?: KitsugiStrings.get(R.string.loading),
                                subtitle = "",
                                type = mediaType,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = imageUrl,
                                year = null,
                                source = source,
                                bangumiSubjectType = if (source.equals("bangumi", ignoreCase = true)) {
                                    when (mediaTypeStr?.lowercase()) {
                                        "anime" -> BangumiApiClient.SubjectType.ANIME
                                        "manga" -> BangumiApiClient.SubjectType.BOOK
                                        "movie", "tv", "real" -> BangumiApiClient.SubjectType.REAL
                                        else -> null
                                    }
                                } else null
                            )
                            navState.navigateToDetail(DetailScreen.ApiResultDetail(searchResult))
                        }
                    }
                )
            }
        }

        is AppStateKey.Downloads -> {
            navState.stateHolder.SaveableStateProvider(key = "downloads_${key.depth}") {
                com.kitsugi.animelist.ui.screens.offline.DownloadsScreen(
                    onBack = { navState.popDetailStack() }
                )
            }
        }

        is AppStateKey.WatchHistory -> {
            navState.stateHolder.SaveableStateProvider(key = "watch_history_${key.depth}") {
                com.kitsugi.animelist.ui.screens.history.WatchHistoryScreen(
                    onBack = { navState.popDetailStack() },
                    onOpenManga = { sourceName, mangaUrl, mangaTitle, coverUrl ->
                        onOpenMangaReadHistory(sourceName, mangaUrl, mangaTitle, coverUrl)
                    }
                )
            }
        }

        else -> {
            // No-op or throw for unhandled states
        }
    }
}
