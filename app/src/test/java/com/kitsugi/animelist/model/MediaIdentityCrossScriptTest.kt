package com.kitsugi.animelist.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies cross-script `titleJapanese` matching (`E101`) while preserving strict guards
 * against year remakes and episode-count mismatches.
 */
class MediaIdentityCrossScriptTest {

    @Test
    fun pureCjkTitleJapaneseMatchesBangumiSubjectTitleAcrossFullwidthDigitNormalization() {
        val aniListRow = MediaEntry(
            id = 1,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        )
        val bangumiRow = MediaEntry(
            id = 2,
            title = "怪獣8号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "bangumi",
            malId = 500_400_602,
            year = null
        )

        assertTrue(MediaIdentity.sameMedia(aniListRow, bangumiRow))
    }

    @Test
    fun cjkTitleMatchStillRejectsConflictingYearsOrTotals() {
        val oldAdaptation = MediaEntry(
            id = 1,
            title = "HUNTER×HUNTER",
            titleJapanese = "ハンター×ハンター",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 8,
            progress = 62,
            total = 62,
            source = "mal",
            malId = 136,
            year = 1999
        )
        val remake2011 = MediaEntry(
            id = 2,
            title = "ハンター×ハンター",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 148,
            total = 148,
            source = "bangumi",
            malId = 500_010_357,
            year = 2011
        )

        assertFalse(MediaIdentity.sameMedia(oldAdaptation, remake2011))
    }
}
