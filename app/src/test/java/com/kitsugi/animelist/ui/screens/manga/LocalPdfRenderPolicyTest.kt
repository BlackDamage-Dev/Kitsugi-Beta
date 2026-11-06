package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class LocalPdfRenderPolicyTest {
    @Test fun rejectsInvalidDimensions() {
        assertNull(localPdfRenderSize(0, 1))
        assertNull(localPdfRenderSize(1, -1))
    }
    @Test fun limitsAreaAndEdgeEvenForExtremeInputs() {
        for (w in listOf(1, 20, 600, 10000, Int.MAX_VALUE)) {
            for (h in listOf(1, 20, 800, 10000, Int.MAX_VALUE)) {
                val (rw, rh) = requireNotNull(localPdfRenderSize(w, h))
                assertTrue(rw in 1..3000 && rh in 1..3000)
                assertTrue(rw.toLong() * rh <= 4_000_000)
            }
        }
    }
    @Test fun scalesSmallPageWithoutDistortingIt() {
        assertEquals(1732 to 2309, localPdfRenderSize(600, 800))
    }
}
