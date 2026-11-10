package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.NextAiringFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMediaDetailClientTest {

    @Test
    fun disabledDetailFeaturesSkipOptionalRequestsAndPayloadFields() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val detailResult = TmdbMediaDetailClient.fetchMediaDetail(
            tmdbId = 123,
            isMovie = false,
            apiKey = "test-key",
            language = "en-US",
            features = TmdbDetailFeatures(
                useBasicInfo = false,
                useDetails = false,
                useReleaseDates = false,
                useArtwork = false,
                useTrailers = false,
                useProductions = false,
                useNetworks = false
            ),
            executeGet = { url ->
                requestedUrls += url
                baseTvResponse
            }
        )

        val detail = requireNotNull(detailResult)
        assertEquals("Sample Series", detail.title)
        assertEquals(123, detail.tmdbId)
        assertNull(detail.synopsis)
        assertTrue(detail.genres.isEmpty())
        assertNull(detail.rating)
        assertNull(detail.score)
        assertNull(detail.year)
        assertNull(detail.total)
        assertNull(detail.episodeDuration)
        assertNull(detail.startDate)
        assertNull(detail.endDate)
        assertNull(detail.nextAiringEpisode)
        assertNull(detail.imageUrl)
        assertTrue(detail.pictures.isEmpty())
        assertNull(detail.trailerUrl)
        assertTrue(detail.openings.isEmpty())
        assertTrue(detail.studios.isEmpty())
        assertTrue(detail.producers.isEmpty())
        assertTrue(detail.networks.isEmpty())
        assertTrue(detail.externalLinks.isEmpty())
        assertTrue(detail.streamingLinks.isEmpty())
        assertEquals(1, requestedUrls.size)
        assertFalse(requestedUrls.any { "/images" in it || "/videos" in it || "/watch/providers" in it })
    }

    @Test
    fun enabledNetworksArtworkAndTrailerFeaturesAreReturned() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val detailResult = TmdbMediaDetailClient.fetchMediaDetail(
            tmdbId = 123,
            isMovie = false,
            apiKey = "test-key",
            language = "en-US",
            executeGet = { url ->
                requestedUrls += url
                when {
                    "/images" in url -> """{"backdrops":[],"posters":[]}"""
                    "/watch/providers" in url -> """{"results":{"TR":{"link":"https://www.themoviedb.org/tv/123/watch","flatrate":[{"provider_name":"Netflix"}]}}}"""
                    "/videos" in url -> """{"results":[{"site":"YouTube","type":"Trailer","key":"abc123","name":"Official Trailer"}]}"""
                    else -> baseTvResponse
                }
            }
        )

        val detail = requireNotNull(detailResult)
        assertTrue(requestedUrls.any { "/images" in it })
        assertTrue(requestedUrls.any { "/videos" in it })
        assertTrue(requestedUrls.any { "/watch/providers" in it })
        assertEquals("https://www.youtube.com/watch?v=abc123", detail.trailerUrl)
        assertTrue(detail.openings.any { it.videoUrl == detail.trailerUrl })
        assertEquals("Netflix", detail.externalLinks.single().site)
        assertEquals("The CW", detail.networks.single().name)
        assertEquals(StudioRole.NETWORK, detail.networks.single().role)
        assertEquals("tmdb", detail.networks.single().source)
        assertEquals("Studio One", detail.studios.single().name)
        assertEquals("Studio Two", detail.producers.single().name)
        // nextAiringEpisode ortak makine biçiminde: "episode|epoch"
        assertEquals("25|${NextAiringFormat.isoDateToEpoch("2024-02-01")}", detail.nextAiringEpisode)
    }

    @Test
    fun movieWithFutureReleaseDateProducesMovieAiringMarker() = runBlocking {
        val detailResult = TmdbMediaDetailClient.fetchMediaDetail(
            tmdbId = 55,
            isMovie = true,
            apiKey = "test-key",
            language = "tr-TR",
            features = TmdbDetailFeatures(
                useBasicInfo = true,
                useDetails = false,
                useReleaseDates = true,
                useArtwork = false,
                useTrailers = false,
                useProductions = false,
                useNetworks = false
            ),
            executeGet = { baseMovieFutureResponse }
        )

        val detail = requireNotNull(detailResult)
        // 0 = Film işareti; gelecek vizyon tarihi
        assertEquals("0|${NextAiringFormat.isoDateToEpoch("2030-05-05")}", detail.nextAiringEpisode)
    }

    @Test
    fun movieWithPastReleaseDateHasNoAiringMarker() = runBlocking {
        val detailResult = TmdbMediaDetailClient.fetchMediaDetail(
            tmdbId = 56,
            isMovie = true,
            apiKey = "test-key",
            language = "tr-TR",
            features = TmdbDetailFeatures(
                useBasicInfo = true,
                useDetails = false,
                useReleaseDates = true,
                useArtwork = false,
                useTrailers = false,
                useProductions = false,
                useNetworks = false
            ),
            executeGet = { baseMoviePastResponse }
        )

        val detail = requireNotNull(detailResult)
        assertNull(detail.nextAiringEpisode)
    }

    private val baseTvResponse = """
        {
          "id":123,
          "name":"Sample Series",
          "original_name":"Sample Series",
          "original_language":"en",
          "overview":"A useful sample overview.",
          "poster_path":"/poster.jpg",
          "genres":[{"id":18,"name":"Drama"}],
          "vote_average":8.2,
          "vote_count":250,
          "popularity":12.3,
          "status":"Returning Series",
          "first_air_date":"2020-01-02",
          "last_air_date":"2024-01-02",
          "number_of_episodes":24,
          "number_of_seasons":2,
          "episode_run_time":[42],
          "production_companies":[
            {"id":10,"name":"Studio One"},
            {"id":11,"name":"Studio Two"}
          ],
          "networks":[{"id":20,"name":"The CW"}],
          "next_episode_to_air":{"episode_number":25,"air_date":"2024-02-01"},
          "alternative_titles":{"results":[{"iso_3166_1":"US","title":"Sample Series"}]}
        }
    """.trimIndent()

    private val baseMovieFutureResponse = """
        {
          "id":55,
          "title":"Future Movie",
          "original_title":"Future Movie",
          "original_language":"en",
          "overview":"Soon.",
          "poster_path":"/future.jpg",
          "release_date":"2030-05-05",
          "genres":[{"id":28,"name":"Action"}],
          "vote_average":7.5,
          "vote_count":10
        }
    """.trimIndent()

    private val baseMoviePastResponse = """
        {
          "id":56,
          "title":"Old Movie",
          "original_title":"Old Movie",
          "original_language":"en",
          "overview":"Long ago.",
          "poster_path":"/old.jpg",
          "release_date":"1999-01-01",
          "genres":[{"id":18,"name":"Drama"}],
          "vote_average":8.0,
          "vote_count":20
        }
    """.trimIndent()
}
