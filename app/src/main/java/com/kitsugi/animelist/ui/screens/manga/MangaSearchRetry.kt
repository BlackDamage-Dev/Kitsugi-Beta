package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.stableSourceKey

// ─── Per-source fetch state ───────────────────────────────────────────────────

data class MangaSourceFetchState(
    val source: MangaSource,
    val isLoading: Boolean = false,
    val mangas: List<MangaDetails> = emptyList(),
    val error: String? = null,
    /** Kaynağın manga hata kodu (KTS-SRC-...); hata yoksa null. */
    val errorCode: String? = null,
    val currentPage: Int = 1,
    val hasNextPage: Boolean = true
)

/** Explicit retry uses the failed/visible sources, not the automatic cooldown-filtered candidates. */
internal fun retrySearchTargets(
    states: List<MangaSourceFetchState>,
    availableSources: List<MangaSource>,
    selected: MangaSource?,
): List<MangaSource> {
    val selectedKey = selected?.stableSourceKey()
    val failed = states.filter { it.error != null && (selectedKey == null || it.source.stableSourceKey() == selectedKey) }
        .map { it.source }
    return if (failed.isNotEmpty()) failed else if (states.isEmpty()) {
        availableSources.filter { selectedKey == null || it.stableSourceKey() == selectedKey }
    } else emptyList()
}
