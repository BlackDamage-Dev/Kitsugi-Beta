package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsMediaResponsePolicyTest {
    private fun classify(status: Int, type: String?, body: String) =
        CsMediaResponsePolicy.classify(status, type, body.toByteArray())

    @Test
    fun `content beats misleading extension and content type`() {
        assertEquals(CsMediaResponseKind.HLS, classify(200, "text/plain", "\uFEFF\n#EXTM3U\n#EXT-X-VERSION:3"))
        assertEquals(CsMediaResponseKind.DASH, classify(200, "application/xml", "<?xml version='1.0'?><MPD></MPD>"))
        assertEquals(CsMediaResponseKind.HTML, classify(200, "video/mp4", "<!DOCTYPE html><html>quota</html>"))
        assertEquals(CsMediaResponseKind.HTML, classify(200, "application/octet-stream", "<!-- warning --><html>quota</html>"))
        assertEquals(CsMediaResponseKind.VIDEO, classify(206, "video/mp4", "binary data"))
    }

    @Test
    fun `authorization and server errors remain unknown`() {
        assertEquals(CsMediaResponseKind.UNKNOWN, classify(403, "text/html", "<html>forbidden</html>"))
        assertEquals(CsMediaResponseKind.UNKNOWN, classify(500, "video/mp4", ""))
        assertEquals(CsMediaResponseKind.DEAD, classify(404, null, ""))
        assertEquals(CsMediaResponseKind.DEAD, classify(410, null, ""))
    }

    @Test
    fun `drive download is not a preview page`() {
        assertTrue(CsMediaResponsePolicy.isDriveDownloadUrl("https://drive.google.com/uc?export=download&id=A&confirm=secret"))
        assertTrue(CsMediaResponsePolicy.isDriveDownloadUrl("https://drive.usercontent.google.com/download?id=A&uuid=B"))
        assertFalse(CsMediaResponsePolicy.isDriveDownloadUrl("https://drive.google.com/file/d/A/preview"))
        assertFalse(CsMediaResponsePolicy.isDriveDownloadUrl("https://drive.google.com.evil.test/uc?id=A"))
    }

    @Test
    fun `explicit player pages are not considered direct manifests`() {
        assertTrue(CsMediaResponsePolicy.isObviousEmbedPage("https://host.example/player/abc"))
        assertTrue(CsMediaResponsePolicy.isObviousEmbedPage("https://host.example/embed/abc?x=1"))
        assertFalse(CsMediaResponsePolicy.isObviousEmbedPage("https://cdn.example/hls/abc"))
    }
}
