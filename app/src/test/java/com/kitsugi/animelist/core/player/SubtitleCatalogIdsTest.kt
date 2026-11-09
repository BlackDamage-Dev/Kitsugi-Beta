package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleCatalogIdsTest {
    @Test fun realFrierenCatalogNamespacesAreNotInterchanged() {
        // ARM Frieren: MAL 52991, AniList 154587, Kitsu 46474.
        val mal = SubtitleCatalogIds.decode(52991, null)
        assertEquals(52991, mal.malId)
        assertNull(mal.kitsuId)
        val ani = SubtitleCatalogIds.decode(null, 100_154_587)
        assertEquals(154587, ani.aniListId)
        val kitsu = SubtitleCatalogIds.decode(300_046_474, null)
        assertEquals(46474, kitsu.kitsuId)
        assertNull(kitsu.malId)

        val bangumi = SubtitleCatalogIds.decode(500_046_474, null)
        assertNull(bangumi.malId)
        assertEquals(46474, bangumi.bangumiId)

        val shiki = SubtitleCatalogIds.decode(400_052_991, null)
        assertNull(shiki.malId)
        assertEquals(52991, shiki.shikimoriId)

        val simkl = SubtitleCatalogIds.decode(150_012_345, null)
        assertNull(simkl.malId)
        assertEquals(12345, simkl.simklId)
    }

    @Test fun validatesImdbAndRejectsInvalidOffsets() {
        assertEquals("tt22248376", SubtitleCatalogIds.imdb(" tt22248376 "))
        assertNull(SubtitleCatalogIds.imdb("tt123:2:1"))
        assertNull(SubtitleCatalogIds.decode(300_000_000, 100_000_000).kitsuId)
        assertNull(SubtitleCatalogIds.decode(300_000_000, 100_000_000).aniListId)
    }
}
