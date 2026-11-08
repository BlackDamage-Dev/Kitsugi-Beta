package com.kitsugi.animelist.ui.utils

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiSafeScrollTest {

    @Test
    fun boundedConstraintsRemainUnchanged() {
        val original = Constraints(minWidth = 100, maxWidth = 500, minHeight = 200, maxHeight = 800)
        assertTrue(original.hasBoundedHeight)

        val safe = if (original.hasBoundedHeight) original else original.copy(maxHeight = 1000)
        assertEquals(original, safe)
    }

    @Test
    fun unboundedConstraintsAreClampedToFallback() {
        val unbounded = Constraints(minWidth = 0, maxWidth = 500, minHeight = 0, maxHeight = Constraints.Infinity)
        assertFalse(unbounded.hasBoundedHeight)

        val fallback = 1920
        val safe = if (unbounded.hasBoundedHeight) unbounded else unbounded.copy(maxHeight = maxOf(unbounded.minHeight, fallback))
        assertTrue(safe.hasBoundedHeight)
        assertEquals(fallback, safe.maxHeight)
    }
}
