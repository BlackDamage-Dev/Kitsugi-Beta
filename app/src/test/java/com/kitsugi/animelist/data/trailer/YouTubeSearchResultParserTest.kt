package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeSearchResultParserTest {

    @Test
    fun `finds first video renderer and ignores other renderer types`() {
        val html = """
            <html><script>
            var ytInitialData = {
              "contents": [
                {"channelRenderer": {"videoId": "channelxxxx"}},
                {"playlistRenderer": {"videoId": "playlistxx"}},
                {"videoRenderer": {"videoId": "abcdefghijk"}},
                {"videoRenderer": {"videoId": "lmnopqrstuv"}}
              ]
            };
            </script></html>
        """.trimIndent()

        assertEquals("abcdefghijk", extractFirstYouTubeSearchVideoId(html))
    }

    @Test
    fun `balanced initial data extraction tolerates braces and escaped quotes in strings`() {
        val html = """
            <script>window["ytInitialData"] = {
              "title": {"runs": [{"text": "A } { brace and a quote: \"ok\""}]},
              "contents": [{"videoRenderer": {"videoId": "lmnopqrstuv"}}]
            };</script>
        """.trimIndent()

        assertEquals("lmnopqrstuv", extractFirstYouTubeSearchVideoId(html))
    }

    @Test
    fun `skips unrelated marker and rejects invalid video ids`() {
        val html = """
            <script>
            var message = "ytInitialData is assigned later";
            var ytInitialData = {
              "contents": [
                {"videoRenderer": {"videoId": "short"}},
                {"videoRenderer": {"videoId": "zyxwvutsrqp"}}
              ]
            };
            </script>
        """.trimIndent()

        assertEquals("zyxwvutsrqp", extractFirstYouTubeSearchVideoId(html))
    }

    @Test
    fun `returns null for absent malformed or videoless search data`() {
        assertNull(extractFirstYouTubeSearchVideoId("<html>No data</html>"))
        assertNull(extractFirstYouTubeSearchVideoId("var ytInitialData = {broken"))
        assertNull(
            extractFirstYouTubeSearchVideoId(
                """var ytInitialData = {"contents":[{"channelRenderer":{"videoId":"abcdefghijk"}}]};"""
            )
        )
    }
}
