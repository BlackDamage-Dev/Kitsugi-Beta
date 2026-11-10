package com.kitsugi.animelist.data.remote

import android.content.Context
import com.kitsugi.animelist.testing.LocalHttpTestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class SubtitleAddonHttpContractTest {
    @Test fun retriesWithoutExtrasAndKeepsReleaseMetadata() = runBlocking {
        LocalHttpTestServer(listOf(
            LocalHttpTestServer.Response(404, body = "not implemented".toByteArray()),
            LocalHttpTestServer.Response(body = """{"subtitles":[{"id":"72857","url":"https://example.test/file/95005","lang":"tur","subtitleFileName":"Shawshank Redemption.srt"}]}""".toByteArray())
        )).use { server ->
            val client = AddonStreamClient()
            val result = client.fetchSubtitles(
                context = mock(Context::class.java),
                manifestUrl = server.url + "/secret/manifest.json?token=abc",
                type = "movie", id = "tt0111161", extraParams = mapOf("filename" to "A Film.mkv")
            )
            assertEquals(1, result.size)
            assertEquals("Shawshank Redemption.srt", result.single().subtitleFileName)
            assertEquals("/secret/subtitles/movie/tt0111161/filename=A%20Film.mkv.json?token=abc", server.takeRequest().path)
            assertEquals("/secret/subtitles/movie/tt0111161.json?token=abc", server.takeRequest().path)
        }
    }
    @Test fun malformedOptionalEpisodeMetadataDoesNotDiscardProviderResponse() {
        val json = """{"subtitles":[{"id":"1","url":"https://example.test/1.srt","lang":"tur","season":{"label":"one"},"episode":"1"}]}"""
        val parsed = com.google.gson.Gson().fromJson(json, SubtitleResponseWrapper::class.java)
        assertEquals(1, parsed.subtitles?.size)
        assertTrue(parsed.subtitles!!.single().season!!.isJsonObject)
        assertEquals("1", parsed.subtitles!!.single().episode!!.asString)
    }
}
