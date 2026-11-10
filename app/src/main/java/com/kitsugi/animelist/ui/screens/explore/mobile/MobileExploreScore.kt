package com.kitsugi.animelist.ui.screens.explore.mobile

/**
 * UI formatters expect integer scores in 1..10, but some existing source parsers
 * (notably Simkl discovery) supply 11..100. Normalise only the display value;
 * keep the original media/entry for identity, callbacks and persistence.
 * Values 1..10 are already on the app's scale. Other invalid values are hidden.
 */
internal fun mobileExploreDisplayScore(score: Int?): Int? = when (score) {
    null, 0 -> null
    in 1..10 -> score
    in 11..100 -> ((score + 5) / 10).coerceIn(1, 10)
    else -> null
}

internal fun mobileExplorePreferredDisplayScore(entryScore: Int?, sourceScore: Int?): Int? =
    mobileExploreDisplayScore(entryScore) ?: mobileExploreDisplayScore(sourceScore)
