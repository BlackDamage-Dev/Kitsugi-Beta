package com.kitsugi.animelist.ui.app

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.DetailScreen
import com.kitsugi.animelist.MangaDetailNavState
import com.kitsugi.animelist.MangaReaderNavState
import com.kitsugi.animelist.data.manga.MangaTitleSet

/** Kept while a source-search page is underneath a pushed detail screen. */
class SourceSearchOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    var initialized = false
}

@Stable
class AppNavigationState(
    val stateHolder: SaveableStateHolder
) {
    var detailBackStack by mutableStateOf<List<DetailScreen>>(emptyList())
    private val sourceSearchOwners = mutableMapOf<String, SourceSearchOwner>()

    fun sourceSearchOwner(key: String): SourceSearchOwner =
        sourceSearchOwners.getOrPut(key) { SourceSearchOwner() }
    var mangaBrowseOpen by mutableStateOf(false)
        private set
    var mangaBrowseQuery by mutableStateOf<String?>(null)
        private set
    var mangaBrowseMediaId by mutableStateOf<Int?>(null)
        private set
    /** Eklenti Portalı'ndan tek kaynakla açılış: seçili kaynağın stableSourceKey'i. */
    var mangaBrowseSourceKey by mutableStateOf<String?>(null)
        private set
    /** Aynı eserin diğer dillerdeki başlıkları (romaji / İngilizce / Japonca). */
    var mangaBrowseAlternates by mutableStateOf<List<String>>(emptyList())
        private set
    /** "Oku" akışından gelen, dil sırasına göre aday başlıklar. */
    var mangaBrowseTitleVariants by mutableStateOf<List<String>>(emptyList())
        private set
    /**
     * "Oku" akışından gelen serinin **tüm** adları.
     * Arama dili ekranda değiştirildiğinde sorgular bu kümeden yeniden üretilir.
     */
    var mangaBrowseTitleSet by mutableStateOf<MangaTitleSet?>(null)
        private set

    fun openMangaBrowse(
        query: String? = null,
        mediaId: Int? = null,
        sourceKey: String? = null,
        alternates: List<String> = emptyList(),
        titleVariants: List<String> = emptyList(),
        titleSet: MangaTitleSet? = null
    ) {
        mangaBrowseQuery = query
        mangaBrowseMediaId = mediaId
        mangaBrowseSourceKey = sourceKey
        mangaBrowseAlternates = alternates.filter { it.isNotBlank() }.distinct()
        mangaBrowseTitleVariants = (titleVariants.ifEmpty { alternates }).filter { it.isNotBlank() }.distinct()
        mangaBrowseTitleSet = titleSet
        mangaBrowseOpen = true
    }

    fun closeMangaBrowse() {
        mangaBrowseOpen = false
        mangaBrowseQuery = null
        mangaBrowseMediaId = null
        mangaBrowseSourceKey = null
        mangaBrowseAlternates = emptyList()
        mangaBrowseTitleVariants = emptyList()
        mangaBrowseTitleSet = null
    }

    var mangaSourceHealthOpen by mutableStateOf(false)
        private set

    fun openMangaSourceHealth() {
        mangaSourceHealthOpen = true
    }

    fun closeMangaSourceHealth() {
        mangaSourceHealthOpen = false
    }

    var mangaLibraryOpen by mutableStateOf(false)
        private set

    fun openMangaLibrary() {
        mangaLibraryOpen = true
    }

    fun closeMangaLibrary() {
        mangaLibraryOpen = false
    }

    var mangaDetailNavState by mutableStateOf<MangaDetailNavState?>(null)

    /**
     * Okuyucudan dönüşte artırılır. Manga detay ekranı bu değeri izleyip **bölüm listesini
     * yeniden çekmeden** yalnızca okuma ilerlemesini Room'dan tazeler; böylece okundu
     * işaretleri ve "Devam Et" hedefi anında güncellenir.
     */
    var mangaDetailRefreshTick by mutableStateOf(0)
    var mangaReaderNavState by mutableStateOf<MangaReaderNavState?>(null)
    var fullScreenGridState by mutableStateOf<FullScreenMediaGridState?>(null)
    var addonFullScreenGridState by mutableStateOf<AddonFullScreenGridState?>(null)
    /** Eklenti Keşfet dialogunun açık olup olmadığını saklar.
     * AnimatedContent geçişleri sırasında yerel remember state sıfırlandığından
     * bu değer navState'te tutulur — tam ekran grid kapandığında dialog geri gelir. */
    var addonExploreOpen by mutableStateOf(false)
    /** Ayarlar alt sayfalarında (Oynatıcı Ayarları vb.) alt barın tamamen gizlenmesi için tutulur. */
    var isSettingsSubPageOpen by mutableStateOf(false)
    var previousBackStack by mutableStateOf<List<DetailScreen>>(emptyList())

    fun popDetailStack() {
        detailBackStack = detailBackStack.dropLast(1)
    }

    fun navigateToDetail(screen: DetailScreen) {
        detailBackStack = detailBackStack + screen
    }

    fun clearPreviousScreens() {
        val poppedWithIndices = previousBackStack.mapIndexed { index, screen -> index to screen }
            .filter { (_, screen) -> screen !in detailBackStack }

        val baseOffset = (if (fullScreenGridState != null) 1 else 0) +
                         (if (addonFullScreenGridState != null) 1 else 0) +
                         (if (mangaBrowseOpen) 1 else 0) +
                         (if (mangaDetailNavState != null) 1 else 0) +
                         (if (mangaReaderNavState != null) 1 else 0) +
                         (if (mangaSourceHealthOpen) 1 else 0) +
                         (if (mangaLibraryOpen) 1 else 0)

        poppedWithIndices.forEach { (idx, screen) ->
            val depth = baseOffset + (idx + 1)
            val key = when (screen) {
                is DetailScreen.MediaDetail -> "media_${depth}_${screen.entryId}"
                is DetailScreen.ApiResultDetail -> "api_${depth}_${screen.result.source}_${screen.result.malId}"
                is DetailScreen.CharacterDetail -> "char_${depth}_${screen.source}_${screen.characterId}_${screen.name.orEmpty()}_${screen.isRealMediaRole}"
                is DetailScreen.StaffDetail -> "staff_${depth}_${screen.source}_${screen.staffId}"
                is DetailScreen.StudioDetail -> "studio_${depth}_${screen.source}_${screen.studioId}_${screen.name.orEmpty()}"
                is DetailScreen.AiringCalendar -> "airing_calendar_${depth}_${screen.preferredSource}"
                DetailScreen.Stats -> "stats_${depth}"
                DetailScreen.Favourites -> "favourites_${depth}"
                DetailScreen.About -> "about_${depth}"
                is DetailScreen.UserProfile -> "user_profile_${depth}_${screen.source}_${screen.userId}_${screen.username.orEmpty()}"
                is DetailScreen.UserMediaList -> "user_media_list_${depth}_${screen.userId}_${screen.initialMediaType.name}"
                is DetailScreen.Notifications -> "notifications_${depth}_${screen.preferredSource}"
                DetailScreen.Downloads -> "downloads_${depth}"
                DetailScreen.WatchHistory -> "watch_history_${depth}"
                DetailScreen.PluginPicker -> "plugin_picker_${depth}"
                is DetailScreen.AddonExplore -> "addon_explore_${depth}_${screen.apiName}"
                is DetailScreen.MangaSourceExplore -> "manga_source_explore_${depth}_${screen.sourceKey}"
                is DetailScreen.SourceSearchPage -> "source_search_${depth}_${screen.engine.id}"
            }
            stateHolder.removeState(key)
            // Eğer aynı ekran baseOffset değişimi nedeniyle yalın derinlik ile kaydedildiyse onu da temizle
            if (baseOffset != 0) {
                val plainDepth = idx + 1
                val fallbackKey = when (screen) {
                    is DetailScreen.MediaDetail -> "media_${plainDepth}_${screen.entryId}"
                    is DetailScreen.ApiResultDetail -> "api_${plainDepth}_${screen.result.source}_${screen.result.malId}"
                    is DetailScreen.CharacterDetail -> "char_${plainDepth}_${screen.source}_${screen.characterId}_${screen.name.orEmpty()}_${screen.isRealMediaRole}"
                    is DetailScreen.StaffDetail -> "staff_${plainDepth}_${screen.source}_${screen.staffId}"
                    is DetailScreen.StudioDetail -> "studio_${plainDepth}_${screen.source}_${screen.studioId}_${screen.name.orEmpty()}"
                    is DetailScreen.AiringCalendar -> "airing_calendar_${plainDepth}_${screen.preferredSource}"
                    DetailScreen.Stats -> "stats_${plainDepth}"
                    DetailScreen.Favourites -> "favourites_${plainDepth}"
                    DetailScreen.About -> "about_${plainDepth}"
                    is DetailScreen.UserProfile -> "user_profile_${plainDepth}_${screen.source}_${screen.userId}_${screen.username.orEmpty()}"
                    is DetailScreen.UserMediaList -> "user_media_list_${plainDepth}_${screen.userId}_${screen.initialMediaType.name}"
                    is DetailScreen.Notifications -> "notifications_${plainDepth}_${screen.preferredSource}"
                    DetailScreen.Downloads -> "downloads_${plainDepth}"
                    DetailScreen.WatchHistory -> "watch_history_${plainDepth}"
                    DetailScreen.PluginPicker -> "plugin_picker_${plainDepth}"
                    is DetailScreen.AddonExplore -> "addon_explore_${plainDepth}_${screen.apiName}"
                    is DetailScreen.MangaSourceExplore -> "manga_source_explore_${plainDepth}_${screen.sourceKey}"
                    is DetailScreen.SourceSearchPage -> "source_search_${plainDepth}_${screen.engine.id}"
                }
                stateHolder.removeState(fallbackKey)
                sourceSearchOwners.remove(fallbackKey)?.viewModelStore?.clear()
            }
            sourceSearchOwners.remove(key)?.viewModelStore?.clear()
        }
        previousBackStack = detailBackStack
    }
}

@Composable
fun rememberAppNavigationState(
    stateHolder: SaveableStateHolder = rememberSaveableStateHolder()
): AppNavigationState {
    return remember(stateHolder) {
        AppNavigationState(stateHolder)
    }
}
