package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaReaderPreferencesTest {
    private val pages = List(7) { MangaPage(it, "chapter/$it") }

    @Test fun doublePageGroupsEveryRealPageExactlyOnce() {
        val display = buildMangaDisplayPages(pages, doublePage = true)
        assertEquals(listOf(0, 2, 4, 6), display.map { it.firstPageIndex })
        assertEquals((0..6).toList(), display.flatMap { group -> group.pages.map { it.index } })
        assertEquals(2, display[0].pages.size)
        assertEquals(1, display.last().pages.size)
    }

    @Test fun spreadOrderFollowsReadingDirection() {
        val pair = pages.take(2)
        assertEquals(listOf(0, 1), orderMangaSpreadPages(pair, ReadingMode.LeftToRight).map { it.index })
        assertEquals(listOf(1, 0), orderMangaSpreadPages(pair, ReadingMode.RightToLeft).map { it.index })
    }

    @Test fun disablingDoublePageDoesNotLoseOrDuplicatePages() {
        val display = buildMangaDisplayPages(pages, doublePage = false)
        assertEquals(7, display.size)
        assertEquals((0..6).toList(), display.map { it.firstPageIndex })
    }

    @Test fun currentRealPageMapsToItsDisplayGroupInBothModes() {
        val grouped = buildMangaDisplayPages(pages, doublePage = true)
        val single = buildMangaDisplayPages(pages, doublePage = false)
        assertEquals(0, mangaDisplayPageIndexFor(grouped, 0))
        assertEquals(0, mangaDisplayPageIndexFor(grouped, 1))
        assertEquals(2, mangaDisplayPageIndexFor(grouped, 5))
        assertEquals(5, mangaDisplayPageIndexFor(single, 5))
    }

    @Test fun invalidPageAndEmptyChapterAreSafelyClamped() {
        val grouped = buildMangaDisplayPages(pages, doublePage = true)
        assertEquals(0, mangaDisplayPageIndexFor(emptyList(), 10))
        assertEquals(grouped.lastIndex, mangaDisplayPageIndexFor(grouped, 99))
        assertEquals(0, mangaDisplayPageIndexFor(grouped, -1))
    }

    @Test fun doublePageOnlyAppliesToHorizontalPagedModes() {
        val enabled = MangaReaderOptions(dualPage = true)
        assertTrue(shouldUseDoublePage(ReadingMode.RightToLeft, false, enabled))
        assertTrue(shouldUseDoublePage(ReadingMode.LeftToRight, false, enabled))
        assertFalse(shouldUseDoublePage(ReadingMode.Vertical, false, enabled))
        assertFalse(shouldUseDoublePage(ReadingMode.Webtoon, false, enabled))
    }

    @Test fun landscapeAutomaticModeIsIndependentOfManualMode() {
        val automatic = MangaReaderOptions(dualPageOnLandscape = true)
        assertFalse(shouldUseDoublePage(ReadingMode.RightToLeft, false, automatic))
        assertTrue(shouldUseDoublePage(ReadingMode.RightToLeft, true, automatic))
        assertFalse(shouldUseDoublePage(ReadingMode.Webtoon, true, automatic))
    }

    @Test fun colorAndReadingModeCyclesAlwaysReturnToInitialMode() {
        var color = ColorFilterType.Normal
        repeat(ColorFilterType.entries.size) { color = nextMangaColorFilter(color) }
        assertEquals(ColorFilterType.Normal, color)
        var mode = ReadingMode.RightToLeft
        repeat(ReadingMode.entries.size) { mode = nextMangaReadingMode(mode) }
        assertEquals(ReadingMode.RightToLeft, mode)
    }

    @Test fun invalidPersistedRangesAreSanitized() {
        val preferences = MangaReaderPreferencesModel(
            brightness = -2f,
            options = MangaReaderOptions(
                sidePadding = 100, pageGap = -5, pageOpacity = 4f,
                preloadAhead = 99, autoScrollSpeed = -1,
                novelFontSizeSp = 500, novelLineHeight = Float.NaN, novelMarginDp = -20,
                novelSpeechRate = Float.POSITIVE_INFINITY,
            ),
        ).sanitized()
        assertEquals(0.1f, preferences.brightness, 0.0001f)
        assertEquals(32, preferences.options.sidePaddingDp)
        assertEquals(0, preferences.options.pageGapDp)
        assertEquals(1f, preferences.options.pageOpacity, 0.0001f)
        assertEquals(8, preferences.options.preloadAhead)
        assertEquals(8, preferences.options.autoScrollSpeed)
        assertEquals(36, preferences.options.novelFontSizeSp)
        assertEquals(1.5f, preferences.options.novelLineHeight, 0.0001f)
        assertEquals(0, preferences.options.novelMarginDp)
        assertEquals(1f, preferences.options.novelSpeechRate, 0.0001f)
    }

    @Test fun nonFiniteSliderValuesCannotCrashTheReader() {
        val preferences = MangaReaderPreferencesModel(
            brightness = Float.NaN,
            options = MangaReaderOptions(pageOpacity = Float.POSITIVE_INFINITY),
        ).sanitized()
        assertEquals(1f, preferences.brightness, 0.0001f)
        assertEquals(1f, preferences.options.pageOpacity, 0.0001f)
    }
}
