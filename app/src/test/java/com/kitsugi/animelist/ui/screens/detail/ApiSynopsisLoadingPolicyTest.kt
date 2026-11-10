package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSynopsisLoadingPolicyTest {

    @Test
    fun skeletonIsShownOnlyWhileDetailIsLoadingWithoutText() {
        assertTrue(shouldShowApiSynopsisSkeleton(synopsis = null, isDetailLoading = true))
        assertTrue(shouldShowApiSynopsisSkeleton(synopsis = "   ", isDetailLoading = true))
    }

    @Test
    fun availableSynopsisIsNeverHiddenBehindLoadingSkeleton() {
        assertFalse(
            shouldShowApiSynopsisSkeleton(
                synopsis = "Two brothers search for the Philosopher's Stone.",
                isDetailLoading = true
            )
        )
        assertFalse(
            shouldShowApiSynopsisSkeleton(
                synopsis = "İki kardeş Felsefe Taşı'nı arar.",
                isDetailLoading = false
            )
        )
    }

    @Test
    fun completedDetailWithoutSynopsisShowsEmptyStateInsteadOfSkeleton() {
        assertFalse(shouldShowApiSynopsisSkeleton(synopsis = null, isDetailLoading = false))
        assertFalse(shouldShowApiSynopsisSkeleton(synopsis = "", isDetailLoading = false))
    }
}
