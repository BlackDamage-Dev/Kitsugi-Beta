package com.kitsugi.animelist.ui.screens.explore.mobile

import org.junit.Assert.*
import org.junit.Test

class MobileExploreScoreTest {
    @Test fun providerHundredPointScoresBecomeSafeTenPointDisplayValues() {
        assertEquals(9, mobileExploreDisplayScore(85))
        assertEquals(8, mobileExploreDisplayScore(79))
        assertEquals(10, mobileExploreDisplayScore(100))
        assertEquals(8, mobileExploreDisplayScore(8))
        for (input in 1..100) {
            val output = mobileExploreDisplayScore(input)
            assertTrue(output != null && output in 1..10)
        }
    }

    @Test fun invalidScoresAreHiddenAndRealEntryHasPriority() {
        assertEquals(null, mobileExploreDisplayScore(null))
        assertEquals(null, mobileExploreDisplayScore(0))
        assertEquals(null, mobileExploreDisplayScore(-1))
        assertEquals(null, mobileExploreDisplayScore(101))
        assertEquals(7, mobileExplorePreferredDisplayScore(7, 85))
        assertEquals(9, mobileExplorePreferredDisplayScore(null, 85))
        assertEquals(9, mobileExplorePreferredDisplayScore(101, 85))
        assertEquals(null, mobileExplorePreferredDisplayScore(-1, 101))
    }
}
