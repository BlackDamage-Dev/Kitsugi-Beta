package com.kitsugi.animelist.data.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimklSyncContractTest {
    @Test fun animeWritesUseShowsNotReadCategory() {
        assertEquals("shows", SimklSyncContract.writeKey("anime"))
        assertEquals("shows", SimklSyncContract.writeKey("shows"))
        assertEquals("movies", SimklSyncContract.writeKey("movies"))
    }

    @Test fun animeReadUsesNestedShow() {
        // Shape from SIMKL/API apiary.apib Get All Items, NOT an invented anime object.
        val fixture = JSONObject("""{"anime":[{"status":"watching","show":{"title":"Fate/Stay Night","ids":{"simkl":46116,"mal":"22297"}}}]}""")
        val media = fixture.getJSONArray("anime").getJSONObject(0)
            .getJSONObject(SimklSyncContract.readMediaKey("anime"))
        assertEquals(46116, media.getJSONObject("ids").getInt("simkl"))
        assertEquals("movie", SimklSyncContract.readMediaKey("movies"))
    }

    @Test fun listReceiptCountsArraysAndReportsUnmatched() {
        val result = SimklSyncContract.receipt("""{"added":{"movies":[],"shows":[{"ids":{"mal":16498}}]},"not_found":{"shows":[{"ids":{"mal":999999}}]}}""")
        assertEquals(1, result.added)
        assertEquals(1, result.notFound)
    }

    @Test fun historyReceiptCountsNumbersIncludingEpisodes() {
        val result = SimklSyncContract.receipt("""{"added":{"movies":1,"shows":0,"episodes":12},"not_found":{"movies":[],"shows":[],"episodes":[]}}""")
        assertEquals(13, result.added)
        assertEquals(0, result.notFound)
    }

    @Test fun emptyMalformedOrIgnoredPayloadIsNotSuccess() {
        listOf("", "null", "{}", "not json", """{"added":{"shows":0,"movies":0},"not_found":{"shows":[]}}""")
            .forEach { body ->
                assertTrue("Must reject: $body", runCatching { SimklSyncContract.receipt(body) }.isFailure)
            }
    }

    @Test fun allUnmatchedIsVisible() {
        val result = SimklSyncContract.receipt("""{"added":{"shows":[]},"not_found":{"shows":[{}]}}""")
        assertEquals(0, result.added)
        assertEquals(1, result.notFound)
    }

    @Test fun twelveWatchedEpisodesMeansOneThroughTwelveNotOnlyTwelve() {
        val episodes = SimklSyncContract.animeEpisodes(12)
        assertEquals(12, episodes.length())
        (1..12).forEach { assertEquals(it, episodes.getJSONObject(it - 1).getInt("number")) }
    }

    @Test fun unsupportedMangaIsNotSilentlyATvShow() {
        assertTrue(runCatching { SimklSyncContract.writeKey("manga") }.isFailure)
    }

    // (E81) `/sync/add-to-list` `to` alanı: Simkl `dropped` değerini reddeder;
    // bırakılan kovası `notinteresting`dir. Asıl sözleşme çağırandadır
    // (`SimklSyncManager.watchStatusToSimkl`); bu testler istemci sınırındaki
    // son çeviriyi kilitler.

    @Test fun droppedIsTranslatedToNotInteresting() {
        assertEquals("notinteresting", SimklSyncContract.normalizeListStatus("dropped"))
        assertEquals("notinteresting", SimklSyncContract.normalizeListStatus("Dropped"))
        assertEquals("notinteresting", SimklSyncContract.normalizeListStatus(" DROPPED "))
    }

    @Test fun legalBucketsPassThroughUnchanged() {
        for (bucket in listOf("watching", "plantowatch", "completed", "notinteresting", "hold")) {
            assertEquals(bucket, SimklSyncContract.normalizeListStatus(bucket))
        }
    }

    @Test fun unknownValuesAreNotRewritten() {
        // Emniyet bir doğrulayıcı değildir: bilinmeyen değeri sessizce başka bir
        // kovaya çevirmek, hatayı görünmez yapar. Bilinen tek geçersiz eşleme çevrilir.
        assertEquals("rewatching", SimklSyncContract.normalizeListStatus("rewatching"))
        assertEquals("", SimklSyncContract.normalizeListStatus(""))
    }
}
