package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdbListAniListIdentityTest {

    @Test
    fun malBackedAniListResultKeepsMalIdForRatingLookup() {
        // Attack on Titan: AniList arama sonucu stableId olarak gerçek idMal değerini taşır.
        val ids = resolveMdbListAniListIds(stableId = 16_498, realMalId = 16_498)

        assertEquals(16_498, ids.malId)
        assertNull(ids.aniListId)
    }

    @Test
    fun malBackedStableIdIsUsedWhenCrossIdHasNotArrivedYet() {
        val ids = resolveMdbListAniListIds(stableId = 16_498, realMalId = null)

        assertEquals(16_498, ids.malId)
        assertNull(ids.aniListId)
    }

    @Test
    fun offsetAniListIdIsDecodedWithoutLeakingIntoMalId() {
        val ids = resolveMdbListAniListIds(stableId = 100_016_498, realMalId = null)

        assertNull(ids.malId)
        assertEquals(16_498, ids.aniListId)
    }

    @Test
    fun explicitlyResolvedMalIdIsPreservedAlongsideOffsetAniListId() {
        val ids = resolveMdbListAniListIds(stableId = 100_000_123, realMalId = 46)

        assertEquals(46, ids.malId)
        assertEquals(123, ids.aniListId)
    }

    @Test
    fun foreignNamespaceAndInvalidIdsAreRejected() {
        val ids = resolveMdbListAniListIds(stableId = 500_016_498, realMalId = 400_016_498)

        assertNull(ids.malId)
        assertNull(ids.aniListId)
    }
}
