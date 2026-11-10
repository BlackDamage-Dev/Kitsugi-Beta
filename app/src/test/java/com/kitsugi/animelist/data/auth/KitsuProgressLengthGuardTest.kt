package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuProgressLengthGuardTest {

    @Test
    fun mediaLengthRequestCarriesBearerTokenForR18CatalogReads() {
        val request = KitsuApiClient.buildMediaLengthRequest(
            kitsuMediaId = 42919,
            isAnime = true,
            accessToken = "r18-token"
        )
        assertEquals("Bearer r18-token", request.header("Authorization"))
        assertEquals("episodeCount", request.url.queryParameter("fields[anime]"))
        assertEquals("42919", request.url.pathSegments.last())
    }

    @Test
    fun mediaLengthRequestDoesNotInventAuthorizationWhenTokenAbsent() {
        val request = KitsuApiClient.buildMediaLengthRequest(12, isAnime = false, accessToken = null)
        assertNull(request.header("Authorization"))
        assertEquals("chapterCount", request.url.queryParameter("fields[manga]"))
    }

    @Test
    fun reportProgressTenIsCappedToEightAndRetriedOnce() = runBlocking {
        val attempts = mutableListOf<Int>()
        var fetches = 0
        val outcome = KitsuProgressLengthGuard.write(
            requestedProgress = 10,
            write = { progress ->
                attempts += progress
                if (attempts.size == 1) {
                    KitsuApiClient.KitsuWriteResult(
                        success = false,
                        errorMessage = "HTTP 422: progress cannot exceed length of media",
                        httpCode = 422
                    )
                } else {
                    KitsuApiClient.KitsuWriteResult(success = true, entryId = "library-1")
                }
            },
            fetchLength = { fetches++; 8 }
        )
        assertEquals(listOf(10, 8), attempts)
        assertEquals(1, fetches)
        assertTrue(outcome.result.success)
        assertEquals(8, outcome.cappedProgressTo)
    }

    @Test
    fun unavailableLengthReturnsOriginalFailureWithoutSecondWrite() = runBlocking {
        val attempts = mutableListOf<Int>()
        val outcome = KitsuProgressLengthGuard.write(
            requestedProgress = 10,
            write = { progress ->
                attempts += progress
                KitsuApiClient.KitsuWriteResult(
                    success = false,
                    errorMessage = "cannot exceed length of media",
                    httpCode = 422
                )
            },
            fetchLength = { null }
        )
        assertEquals(listOf(10), attempts)
        assertFalse(outcome.result.success)
        assertEquals(422, outcome.result.httpCode)
        assertNull(outcome.cappedProgressTo)
    }

    @Test
    fun unrelatedHttp422IsNotRewrittenAsLengthRetry() = runBlocking {
        val attempts = mutableListOf<Int>()
        var fetches = 0
        val outcome = KitsuProgressLengthGuard.write(
            requestedProgress = 10,
            write = { progress ->
                attempts += progress
                KitsuApiClient.KitsuWriteResult(
                    success = false,
                    errorMessage = "HTTP 422: validation failed",
                    httpCode = 422
                )
            },
            fetchLength = { fetches++; 8 }
        )
        assertEquals(listOf(10), attempts)
        assertEquals(0, fetches)
        assertFalse(outcome.result.success)
        assertNull(outcome.cappedProgressTo)
    }
}
