package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailStickyHeaderVisibilityTest {

    @Test
    fun standardDetailContentRowRevealsFloatingHeader() {
        // Standard detail rows: hero = 0, sticky tabs = 1, pager content = 2.
        // Regression: media details used showAtIndex = 3, a row that does not exist.
        val visible = resolveDetailStickyHeaderVisibility(
            isCurrentlyVisible = false,
            firstVisibleItemIndex = 2,
            showAtIndex = 2,
            hideAtIndex = 0
        )

        assertTrue(visible)
    }

    @Test
    fun stickyTabBoundaryKeepsHeaderVisibleWhileScrolling() {
        val visible = resolveDetailStickyHeaderVisibility(
            isCurrentlyVisible = true,
            firstVisibleItemIndex = 1,
            showAtIndex = 2,
            hideAtIndex = 0
        )

        assertTrue(visible)
    }

    @Test
    fun returningToHeroHidesFloatingHeader() {
        val visible = resolveDetailStickyHeaderVisibility(
            isCurrentlyVisible = true,
            firstVisibleItemIndex = 0,
            showAtIndex = 2,
            hideAtIndex = 0
        )

        assertFalse(visible)
    }

    @Test
    fun headerStaysHiddenBetweenThresholdsUntilContentIsReached() {
        val visible = resolveDetailStickyHeaderVisibility(
            isCurrentlyVisible = false,
            firstVisibleItemIndex = 1,
            showAtIndex = 2,
            hideAtIndex = 0
        )

        assertFalse(visible)
    }

    @Test
    fun restoredDeepScrollPositionRevealsHeaderImmediately() {
        val visible = resolveDetailStickyHeaderVisibility(
            isCurrentlyVisible = false,
            firstVisibleItemIndex = 4,
            showAtIndex = 2,
            hideAtIndex = 0
        )

        assertTrue(visible)
    }
}
