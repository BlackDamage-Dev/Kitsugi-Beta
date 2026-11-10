package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreSourceState

/** The caller must supply state for this platform atomically; payloads have no embedded owner. */
internal sealed interface MobileExploreInput {
    data class Selected(val platform: ExplorePlatform, val state: ExploreSourceState) : MobileExploreInput {
        init { require(platform != ExplorePlatform.ALL) }
    }

    /** For personal shelves the caller chooses ONE current-account payload, never both cached copies. */
    data class All(
        val states: Map<ExplorePlatform, ExploreSourceState>,
        val personal: PersonalPayload? = null,
    ) : MobileExploreInput
}

internal data class PersonalPayload(val origin: ExplorePlatform, val payload: ExplorePayload) {
    init { require(origin == ExplorePlatform.TMDB || origin == ExplorePlatform.SIMKL) }
}

/** Null state means no state was supplied yet, not a successful empty result. */
internal data class MobileSourceStatus(val source: ExplorePlatform, val state: ExploreSourceState?)

internal data class MobileExplorePageData(
    val tab: MobileExploreTab,
    val supported: Boolean,
    val shelves: List<MobileExploreShelfData>,
    val sourceStatuses: List<MobileSourceStatus>,
    val hero: JikanSearchResult?,
) {
    // Neither an absent state nor an idle state without a payload is a completed response.
    val awaitingSourceState: Boolean get() = sourceStatuses.any { status ->
        val state = status.state
        state == null || (state.payload == null && !state.isLoading && state.error == null)
    }
    val loadingSources: List<ExplorePlatform> get() = sourceStatuses.filter { it.state?.isLoading == true }.map { it.source }
    val failedSources: List<MobileSourceStatus> get() = sourceStatuses.filter { it.state?.error != null }
    val showingCachedSources: List<ExplorePlatform> get() = sourceStatuses.filter { it.state?.isCached == true }.map { it.source }
    // An error is not proof of an empty catalogue, even when other sources completed.
    val showNoShelves: Boolean get() = supported && shelves.isEmpty() && !awaitingSourceState && loadingSources.isEmpty() && failedSources.isEmpty()
    // Keep stale real content scrollable; don't clamp the saved position to an error placeholder.
    val readyForScrollRestore: Boolean get() = shelves.isNotEmpty() || (!awaitingSourceState && loadingSources.isEmpty() && failedSources.isEmpty())
}

internal data class MobileExploreContent(
    val pages: List<MobileExplorePageData>,
    val rejected: List<MobileExploreRejectedItem>,
)

/**
 * Pure screen projection: it does not launch a request, guess an API failure from an empty
 * list, or merge different providers into one score chart. Recompute from the current input
 * when a source completes; never retain a previous source's page/hero in the projection.
 */
internal fun buildMobileExploreContent(
    input: MobileExploreInput,
    showAdultContent: Boolean,
    separateNovelsManga: Boolean,
): MobileExploreContent {
    val catalog = mutableListOf<MobileExploreShelfData>()
    val personal = mutableListOf<MobileExploreShelfData>()
    val rejected = mutableListOf<MobileExploreRejectedItem>()
    val sources = when (input) {
        is MobileExploreInput.Selected -> listOf(input.platform)
        is MobileExploreInput.All -> ExplorePlatform.sources
    }
    fun collect(source: ExplorePlatform, payload: ExplorePayload, allowFallback: Boolean) {
        val mapped = mobileExploreCatalogSections(source, payload, showAdultContent, separateNovelsManga, allowFallback)
        catalog += mapped.shelves
        rejected += mapped.rejected
    }
    fun collectPersonal(source: ExplorePlatform, payload: ExplorePayload) {
        val mapped = mobileExplorePersonalSections(source, payload, showAdultContent)
        personal += mapped.shelves
        rejected += mapped.rejected
    }
    when (input) {
        is MobileExploreInput.Selected -> input.state.payload?.let { payload ->
            collect(input.platform, payload, allowFallback = input.platform == ExplorePlatform.AniList)
            if (input.platform == ExplorePlatform.TMDB || input.platform == ExplorePlatform.SIMKL) {
                collectPersonal(input.platform, payload)
            }
        }
        is MobileExploreInput.All -> {
            for (source in ExplorePlatform.sources) {
                input.states[source]?.payload?.let { collect(source, it, allowFallback = false) }
            }
            input.personal?.let { collectPersonal(it.origin, it.payload) }
        }
    }
    val pages = MobileExploreTab.entries.map { tab ->
        val matching = (personal + catalog).filter { it.tab == tab }
        val status = sources.filter { mobileSourceSupportsTab(it, tab) }.map { source ->
            MobileSourceStatus(source, when (input) {
                is MobileExploreInput.Selected -> input.state
                is MobileExploreInput.All -> input.states[source]
            })
        }
        MobileExplorePageData(
            tab = tab,
            supported = sources.any { mobileSourceSupportsTab(it, tab) },
            shelves = matching,
            sourceStatuses = status,
            hero = mobileExploreHeroCandidate(tab, matching),
        )
    }
    return MobileExploreContent(pages, rejected)
}

/** Only history has an already verified destination. Others require an explicit safe grid. */
enum class MobileShelfAction { WATCH_HISTORY, FINITE_GRID_REQUIRES_UI, NONE }

fun mobileShelfAction(shelf: MobileExploreShelfData): MobileShelfAction = when {
    shelf.results.isEmpty() -> MobileShelfAction.NONE
    shelf.origin == MobileShelfOrigin.SIMKL_CONTINUE -> MobileShelfAction.WATCH_HISTORY
    // Never send a split/fallback/personal shelf into an unrelated legacy paginated query.
    // A future mobile finite grid must clearly indicate that only loaded results are shown.
    else -> MobileShelfAction.FINITE_GRID_REQUIRES_UI
}
