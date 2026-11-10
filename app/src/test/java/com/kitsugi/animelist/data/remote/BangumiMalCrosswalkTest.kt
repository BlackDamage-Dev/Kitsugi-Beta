package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiMalCrosswalkTest {

    @Test
    fun parserBuildsBothDirectionsAndSkipsNulls() {
        val snapshot = BangumiMalCrosswalk.parse(
            mappingJson = """{"10":45,"12":45,"13":null}""",
            conflictsJson = "[]"
        )
        assertEquals(listOf(10, 12), snapshot.malToBangumi[45])
        assertEquals(45, snapshot.bangumiToMal[10])
        assertFalse(snapshot.bangumiToMal.containsKey(13))
    }

    @Test
    fun bothSidesOfKnownConflictAreExcluded() {
        val snapshot = BangumiMalCrosswalk.parse(
            mappingJson = """{"10":45,"11":46,"20":77}""",
            conflictsJson = """[
                {"bangumi_id":11,"chosen":{"mal_id":46},"rejected":{"mal_id":47}},
                {"bangumi_id":20,"chosen":{"mal_id":77},"rejected":{"mal_id":78}}
            ]"""
        )
        assertEquals(listOf(10), snapshot.malToBangumi[45])
        assertNull(snapshot.malToBangumi[46])
        assertNull(snapshot.malToBangumi[77])
        assertFalse(snapshot.bangumiToMal.containsKey(11))
        assertFalse(snapshot.bangumiToMal.containsKey(20))
    }
}
