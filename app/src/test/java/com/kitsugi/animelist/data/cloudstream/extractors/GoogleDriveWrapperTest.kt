package com.kitsugi.animelist.data.cloudstream.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveWrapperTest {

    @Test
    fun recognizesDriveHostsAndExtractsFileIds() {
        assertTrue(GoogleDriveWrapper.isDriveUrl("https://drive.google.com/file/d/abcDEF_123456/preview"))
        assertTrue(GoogleDriveWrapper.isDriveUrl("https://docs.google.com/file/d/abcDEF_123456/view"))
        assertTrue(GoogleDriveWrapper.isDriveUrl("https://drive.usercontent.google.com/download?id=abcDEF_123456"))
        assertTrue(GoogleDriveWrapper.isDriveDownloadUrl("https://drive.usercontent.google.com/download?id=abcDEF_123456&export=download"))
        assertTrue(GoogleDriveWrapper.isDriveDownloadUrl("https://drive.google.com/uc?id=abcDEF_123456"))
        assertFalse(GoogleDriveWrapper.isDriveDownloadUrl("https://drive.google.com/file/d/abcDEF_123456/preview"))
        assertFalse(GoogleDriveWrapper.isDriveUrl("https://drive.google.com.example/file/d/abcDEF_123456"))
        assertEquals(
            "abcDEF_123456",
            GoogleDriveWrapper.fileId("https://drive.google.com/file/d/abcDEF_123456/preview")
        )
    }

    @Test
    fun parsesPlaybackProgressiveTranscodesAndSortsByHeight() {
        val body = """
            {
              "mediaStreamingData": {
                "formatStreamingData": {
                  "progressiveTranscodes": [
                    {"url":"https://cdn.example/480.mp4","transcodeMetadata":{"height":480}},
                    {"url":"https://cdn.example/1080.mp4","transcodeMetadata":{"height":1080}}
                  ]
                }
              }
            }
        """.trimIndent()

        val result = GoogleDriveWrapper.playbackStreams(body)

        assertEquals(2, result.size)
        assertEquals(1080, result[0].height)
        assertEquals("https://cdn.example/1080.mp4", result[0].url)
    }

    @Test
    fun parsesHiddenWarningFormInsteadOfGuessingConfirmQuery() {
        val html = """
            <form action="https://drive.usercontent.google.com/download">
              <input type="hidden" name="id" value="abcDEF_123456">
              <input type="hidden" name="export" value="download">
              <input type="hidden" name="confirm" value="t&amp;x">
            </form>
        """.trimIndent()

        val result = GoogleDriveWrapper.downloadUrl(html)

        assertTrue(result.orEmpty().startsWith("https://drive.usercontent.google.com/download?"))
        assertTrue(result.orEmpty().contains("id=abcDEF_123456"))
        assertTrue(result.orEmpty().contains("confirm=t%26x"))
        assertTrue(GoogleDriveWrapper.downloadUrl("<html>quota exceeded</html>") == null)
    }
}
