package com.kitsugi.animelist.core.download.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Küçük video indirme motorunun adres sınıflandırması.
 *
 * Bu testler gerçek saha hatalarını kilitler:
 *  - YouTube linkleri (Shikimori PV'leri) doğrudan dosya sanılıp indirilmeye çalışılırsa
 *    "sesi ve görüntüsü ayrı akışlarda" duvarına çarpılıyordu.
 *  - `.m3u8` adresleri HLS olarak tanınmazsa tek dosya yerine HTML/JSON kaydediliyordu.
 */
class VideoSourceClassifierTest {

    @Test
    fun youtubePagesAreRecognisedInEveryForm() {
        assertEquals(
            VideoSourceKind.YOUTUBE_PAGE,
            VideoSourceClassifier.kindOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            VideoSourceKind.YOUTUBE_PAGE,
            VideoSourceClassifier.kindOf("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            VideoSourceKind.YOUTUBE_PAGE,
            VideoSourceClassifier.kindOf("https://m.youtube.com/shorts/dQw4w9WgXcQ")
        )
        assertTrue(VideoSourceClassifier.isYouTubeUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun hlsAndDashAreSeparatedFromPlainFiles() {
        assertEquals(
            VideoSourceKind.HLS,
            VideoSourceClassifier.kindOf("https://cdn.example.com/hls/master.m3u8?token=abc")
        )
        assertEquals(
            VideoSourceKind.DASH,
            VideoSourceClassifier.kindOf("https://cdn.example.com/dash/manifest.mpd")
        )
        assertEquals(
            VideoSourceKind.DIRECT_FILE,
            VideoSourceClassifier.kindOf("https://v.animethemes.moe/Parasyte-OP1.webm")
        )
        assertEquals(
            VideoSourceKind.DIRECT_FILE,
            VideoSourceClassifier.kindOf("https://cdn.example.com/video.MP4")
        )
        assertEquals(
            VideoSourceKind.UNKNOWN,
            VideoSourceClassifier.kindOf("https://cdn.example.com/download?id=42")
        )
        assertEquals(VideoSourceKind.UNKNOWN, VideoSourceClassifier.kindOf(""))
        assertEquals(VideoSourceKind.UNKNOWN, VideoSourceClassifier.kindOf(null))
    }

    @Test
    fun extensionFallsBackToContentTypeThenMp4() {
        assertEquals("webm", VideoSourceClassifier.extensionFor("https://a/op1.webm"))
        assertEquals("mp4", VideoSourceClassifier.extensionFor("https://a/stream?id=1", "video/mp4"))
        assertEquals("webm", VideoSourceClassifier.extensionFor("https://a/stream?id=1", "video/webm; codecs=vp9"))
        assertEquals("ts", VideoSourceClassifier.extensionFor("https://a/x.m3u8", "application/vnd.apple.mpegurl"))
        assertEquals("mp4", VideoSourceClassifier.extensionFor("https://a/stream?id=1", null))
    }

    @Test
    fun relativePlaylistUrlsResolveAgainstTheManifest() {
        assertEquals(
            "https://cdn.example.com/hls/seg-1.ts",
            VideoSourceClassifier.resolveUrl("https://cdn.example.com/hls/master.m3u8", "seg-1.ts")
        )
        assertEquals(
            "https://cdn.example.com/hls/variant/index.m3u8",
            VideoSourceClassifier.resolveUrl("https://cdn.example.com/hls/master.m3u8", "variant/index.m3u8")
        )
        assertEquals(
            "https://other.example.com/abs.m3u8",
            VideoSourceClassifier.resolveUrl("https://cdn.example.com/hls/master.m3u8", "https://other.example.com/abs.m3u8")
        )
    }

    @Test
    fun fileNameSanitisingNeverProducesAnInvalidPath() {
        assertEquals("Parasyte OP1", VideoSourceClassifier.sanitizeFileName("Parasyte / OP1"))
        assertEquals("a b c", VideoSourceClassifier.sanitizeFileName("a:b*c?"))
        assertFalse(VideoSourceClassifier.sanitizeFileName("///").contains('/'))
        assertEquals("video", VideoSourceClassifier.sanitizeFileName("   "))
        assertTrue(VideoSourceClassifier.sanitizeFileName("x".repeat(400)).length <= 90)
    }

    @Test
    fun webmDetectionWorksWithoutExtension() {
        assertTrue(VideoSourceClassifier.looksLikeWebm("video/webm", "https://a/stream?id=1"))
        assertTrue(VideoSourceClassifier.looksLikeWebm(null, "https://a/op1.webm"))
        assertFalse(VideoSourceClassifier.looksLikeWebm("video/mp4", "https://a/stream?id=1"))
    }
}
