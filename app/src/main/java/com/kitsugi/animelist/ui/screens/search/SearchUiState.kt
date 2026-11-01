package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.TaxonomyOrgRole
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiCountryOfOrigin
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaFormat
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSeason
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSortSearch
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSource
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaStatus

enum class KitsugiSearchTab(val label: String) {
    All("🌟 Tümü"),
    Anime("⚡ AniList"),
    Manga("📖 Manga"),
    MAL("⭐ MyAnimeList"),
    Shikimori("🌸 Shikimori 🇷🇺"),
    TMDB("🍿 Film & Dizi"),
    Kitsu("🦊 Kitsu"),
    Simkl("📺 Simkl"),
    Bangumi("🎌 Bangumi 🇨🇳"),
    Character("👤 Karakter"),
    Staff("🎙️ Personel")
}

/**
 * Seçenek C: All-in-One Çoklu platform eş zamanlı arama sonuçları.
 */
data class MultiPlatformResults(
    val aniListResults: List<JikanSearchResult> = emptyList(),
    val malResults: List<JikanSearchResult> = emptyList(),
    val tmdbResults: List<JikanSearchResult> = emptyList(),
    val shikimoriResults: List<JikanSearchResult> = emptyList(),
    val kitsuResults: List<JikanSearchResult> = emptyList(),
    val simklResults: List<JikanSearchResult> = emptyList(),
    val bangumiResults: List<JikanSearchResult> = emptyList(),
    val isLoadingAniList: Boolean = false,
    val isLoadingMal: Boolean = false,
    val isLoadingTmdb: Boolean = false,
    val isLoadingShikimori: Boolean = false,
    val isLoadingKitsu: Boolean = false,
    val isLoadingSimkl: Boolean = false,
    val isLoadingBangumi: Boolean = false,
) {
    val isEmpty: Boolean get() =
        aniListResults.isEmpty() &&
        malResults.isEmpty() &&
        tmdbResults.isEmpty() &&
        shikimoriResults.isEmpty() &&
        kitsuResults.isEmpty() &&
        simklResults.isEmpty() &&
        bangumiResults.isEmpty()

    val isAnyLoading: Boolean get() =
        isLoadingAniList || isLoadingMal || isLoadingTmdb ||
        isLoadingShikimori || isLoadingKitsu || isLoadingSimkl || isLoadingBangumi

    companion object {
        /**
         * Yalnızca gerçekten sorgulanacak kaynaklar "yükleniyor" işaretlenir.
         *
         * Arama başlarken tüm raflar boş + yüklenmiyor durumuna düşüyordu; gerçek
         * yükleme bayrakları ancak ağ isteği kurulduktan sonra yazıldığı için araya
         * tüm rafların kaybolduğu bir kare giriyordu (titreme). Ayrıca kapsamın
         * desteklemediği kaynaklar (örn. Manga kapsamında TMDB) hiç sorgulanmadığı
         * hâlde boşuna shimmer çiziyordu.
         */
        fun loading(
            aniList: Boolean,
            mal: Boolean,
            tmdb: Boolean,
            shikimori: Boolean,
            kitsu: Boolean,
            simkl: Boolean,
            bangumi: Boolean
        ): MultiPlatformResults = MultiPlatformResults(
            isLoadingAniList = aniList,
            isLoadingMal = mal,
            isLoadingTmdb = tmdb,
            isLoadingShikimori = shikimori,
            isLoadingKitsu = kitsu,
            isLoadingSimkl = simkl,
            isLoadingBangumi = bangumi
        )
    }
}

/**
 * Search ekranının UI durumu.
 * AniHyou SearchUiState.kt mimarisine ve Seçenek C Çoklu Platform motoruna tam uyarlanmıştır.
 */
data class SearchUiState(
    val query: String = "",
    val currentTab: KitsugiSearchTab = KitsugiSearchTab.All,
    val selectedMediaType: MediaType = MediaType.Anime,
    val selectedPlatform: SearchPlatform = SearchPlatform.All,
    val results: List<JikanSearchResult> = emptyList(),
    val multiResults: MultiPlatformResults = MultiPlatformResults(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val searchHistoryEnabled: Boolean = true,
    val lastDeletedHistoryItem: SearchHistoryItem? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasNextPage: Boolean = true,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    /** Tüm aramanın başarısız olduğu durumda platform hata kodu. */
    val platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
    /** Bu aramada hata veren kaynaklar (kaynak → platform hata kodu). */
    val sourceErrorCodes: Map<com.kitsugi.animelist.data.platform.ListPlatform, com.kitsugi.animelist.data.platform.PlatformErrorCode> = emptyMap(),
    val isFilterSheetOpen: Boolean = false,
    val showMoreFilters: Boolean = true,

    // ── Brave-style Engine & Scope ─────────────────────────────────────────
    val selectedEngine: SearchSourceEngine = SearchSourceEngine.ALL,
    val selectedScope: SearchScope = SearchScope.ALL_MIXED,

    // ── Source-specific filter bundles ─────────────────────────────────────
    val aniListSpecificFilters: AniListSpecificFilters = AniListSpecificFilters(),
    val malSpecificFilters: MalSpecificFilters = MalSpecificFilters(),
    val tmdbSpecificFilters: TmdbSpecificFilters = TmdbSpecificFilters(),
    val shikimoriSpecificFilters: ShikimoriSpecificFilters = ShikimoriSpecificFilters(),
    val kitsuSpecificFilters: KitsuSpecificFilters = KitsuSpecificFilters(),
    val simklSpecificFilters: SimklSpecificFilters = SimklSpecificFilters(),
    val bangumiSpecificFilters: BangumiSpecificFilters = BangumiSpecificFilters(),
    /** Last typed detail facet; used to avoid querying providers that cannot apply it. */
    val detailSearchFilterRequest: DetailSearchFilterRequest? = null,

    // ── Plugin Explore Mode ────────────────────────────────────────────────
    /** When non-null, the search screen shows this plugin's explore page instead of normal search */
    val selectedPluginApiName: String? = null,

    // ── Genre / Tag filters (used by GenresTagsSheet) ──────────────────────
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val organizations: List<OrgFilter> = emptyList(),

    // ── Kurum (stüdyo / yapımcı / şirket / kanal) filtreleri ────────────────
    /**
     * Ortak filtre çatısının kurum seçimleri. Her kayıt kendi kaynağının kimlik
     * uzayına aittir; motor değişince seçim kaybolmaz, yalnızca sunucu tarafında
     * uygulanabildiği kaynakta aramaya yansır (bkz. [SearchCommonFiltersBridge]).
     */
    val institutions: List<CommonInstitution> = emptyList(),

    // ── AniHyou-style chip filters (AniList-only) ──────────────────────────
    val selectedFormats: List<KitsugiMediaFormat> = emptyList(),
    val selectedStatuses: List<KitsugiMediaStatus> = emptyList(),
    val country: KitsugiCountryOfOrigin? = null,
    val selectedSources: List<KitsugiMediaSource> = emptyList(),

    // ── Date / Season ──────────────────────────────────────────────────────
    val startYear: Int? = null,
    val endYear: Int? = null,
    val season: KitsugiMediaSeason? = null,

    // ── Score / Episode / Duration ranges ──────────────────────────────────
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val minEpCh: Int? = null,
    val maxEpCh: Int? = null,
    val minDuration: Int? = null,
    val maxDuration: Int? = null,

    // ── AniHyou Tri-Filter states ──────────────────────────────────────────
    val onMyList: Boolean? = null,
    val isDoujin: Boolean? = null,
    val isAdultFilter: Boolean? = null,

    // ── Sort ───────────────────────────────────────────────────────────────
    val sortSearch: KitsugiMediaSortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
    val isSortDescending: Boolean = true,
) {
    /** Effective AniList sort string (e.g. "POPULARITY_DESC") */
    val effectiveSortApiValue: String get() =
        if (isSortDescending) sortSearch.descApiValue else sortSearch.ascApiValue

    /** Organization selection is preview-only until the provider capability mapping is shipped. */
    val hasFiltersApplied: Boolean get() = activeFilterCount > organizations.size

    val activeFilterCount: Int get() {
        return organizations.size + when (selectedEngine) {
            SearchSourceEngine.ALL -> {
                var c = 0
                if (genres.isNotEmpty()) c += genres.size
                if (excludedGenres.isNotEmpty()) c += excludedGenres.size
                if (tags.isNotEmpty()) c += tags.size
                if (startYear != null || endYear != null) c++
                if (season != null) c++
                if (minScore != null || maxScore != null) c++
                if (isAdultFilter != null) c++
                if (institutions.isNotEmpty()) c += institutions.size
                // Detay sayfası çip tıklamalarından gelen kimlik facetleri (stüdyo/
                // yapımcı/dergi/kanal): sayılırlarsa boş sorguyla arama tetiklenir.
                // Kurum listesinde zaten sayılan kimlikler ikinci kez sayılmaz.
                if (institutions.isEmpty()) {
                    if (malSpecificFilters.producerId != null) c++
                    if (malSpecificFilters.magazineId != null) c++
                    if (tmdbSpecificFilters.networkId != null) c++
                    if (tmdbSpecificFilters.companyId != null) c++
                }
                c
            }
            // Kanonik tür/etiket/yıl/puan değerleri her zaman motor paketine de
            // yansıtıldığı için ağırlıklı olarak paket sayılır. Kök alanlarda
            // (legacy çipler) bir değer varsa ve pakette karşılığı yoksa o da
            // sayılır; aynı filtre iki kez sayılmaz.
            SearchSourceEngine.ANILIST -> {
                val f = aniListSpecificFilters
                var count = f.activeCount
                if (f.formats.isEmpty() && selectedFormats.isNotEmpty()) count += selectedFormats.size
                if (f.statuses.isEmpty() && selectedStatuses.isNotEmpty()) count += selectedStatuses.size
                if (f.country == null && country != null) count++
                if (f.sources.isEmpty() && selectedSources.isNotEmpty()) count += selectedSources.size
                if (f.season == null && season != null) count++
                if (f.startYear == null && startYear != null) count++
                if (f.endYear == null && endYear != null) count++
                if (f.minScore == null && minScore != null) count++
                if (f.maxScore == null && maxScore != null) count++
                if (f.onMyList == null && onMyList != null) count++
                if (f.isDoujin == null && isDoujin != null) count++
                if (f.isAdult == null && isAdultFilter != null) count++
                count
            }
            // Kalan motorlar: paket sayılır. Paket boşsa (ör. seçilen tür bu
            // kaynağın kimlik uzayına çevrilemedi, ya da seçim henüz projeksiyona
            // yansımadı) kanonik kök alanlar sayılır. Aksi hâlde yalnızca kök
            // alanda duran bir seçimle boş sorgu araması hiç çalışmıyor ve sayfa
            // "filtre seçtim ama tepki yok" görünüyordu.
            SearchSourceEngine.MAL -> countForBundle(malSpecificFilters.activeCount)
            SearchSourceEngine.TMDB -> countForBundle(tmdbSpecificFilters.activeCount)
            SearchSourceEngine.SHIKIMORI -> countForBundle(shikimoriSpecificFilters.activeCount)
            SearchSourceEngine.KITSU -> countForBundle(kitsuSpecificFilters.activeCount)
            SearchSourceEngine.SIMKL -> countForBundle(simklSpecificFilters.activeCount)
            SearchSourceEngine.BANGUMI -> countForBundle(bangumiSpecificFilters.activeCount)
        }
    }

    /**
     * Motor paketindeki filtre sayısı; paket boşsa kanonik kök alanlara düşer.
     * Aynı filtre iki kez sayılmaz (biri ya da diğeri kullanılır).
     */
    private fun countForBundle(bundleCount: Int): Int {
        if (bundleCount > 0) return bundleCount
        var count = 0
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (tags.isNotEmpty()) count += tags.size
        if (institutions.isNotEmpty()) count += institutions.size
        if (startYear != null || endYear != null) count++
        if (minScore != null || maxScore != null) count++
        return count
    }

    /** Returns the legacy SearchFilters object for backward-compatible ViewModel code. */
    fun toLegacyFilters(): SearchFilters = SearchFilters(
        format = selectedFormats.firstOrNull()?.apiValue,
        status = selectedStatuses.firstOrNull()?.apiValue,
        genres = genres,
        excludedGenres = excludedGenres,
        tags = tags,
        organizations = organizations,
        minYear = startYear,
        maxYear = endYear,
        season = season?.apiValue,
        minScore = minScore,
        maxScore = maxScore,
        sort = effectiveSortApiValue
    )
}

data class SearchHistoryItem(
    val query: String,
    val platform: SearchPlatform,
    val mediaType: MediaType,
    val timestamp: Long = 0L,
    val engine: SearchSourceEngine = SearchSourceEngine.ALL,
    val scope: SearchScope = SearchScope.ALL_MIXED,
    val isPinned: Boolean = false,
    val searchCount: Int = 1
)

enum class SearchPlatform(val label: String) {
    All("Tümü"),
    AniList("AniList"),
    MAL("MyAnimeList"),
    TMDB("TMDB"),
    Kitsu("Kitsu"),
    Shikimori("Shikimori"),
    Simkl("Simkl"),
    Bangumi("Bangumi"),
    CS3("Eklentiler")
}

/** One catalog organization, identified by stable key and role (not by translated name). */
data class OrgFilter(val role: TaxonomyOrgRole, val canonicalKey: String)

/**
 * Legacy filter bag kept for backward compatibility with JikanApiClient
 * and the existing executeSearchForQuery logic.
 */
data class SearchFilters(
    val format: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val season: String? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val sort: String? = "POPULARITY_DESC",
    val organizations: List<OrgFilter> = emptyList()
) {
    fun isDefault(): Boolean =
        format == null &&
        status == null &&
        genres.isEmpty() &&
        excludedGenres.isEmpty() &&
        tags.isEmpty() &&
        organizations.isEmpty() &&
        minYear == null &&
        maxYear == null &&
        season == null &&
        minScore == null &&
        maxScore == null &&
        sort == "POPULARITY_DESC"
}
