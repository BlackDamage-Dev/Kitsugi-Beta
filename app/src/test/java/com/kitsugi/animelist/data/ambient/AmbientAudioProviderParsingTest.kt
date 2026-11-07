package com.kitsugi.animelist.data.ambient

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientAudioProviderParsingTest {
    @Test
    fun `Openverse keeps safe playback when license metadata is unknown`() {
        val item = JSONObject()
            .put("id", "freesound:track-1")
            .put("title", "Calm instrumental bed")
            .put("creator", "Composer")
            .put("url", "https://cdn.example.org/audio.mp3")
            .put("download_url", "https://cdn.example.org/download/audio.mp3")
            .put("filetype", "audio/mpeg")
            .put("duration", 90)
            .put("license", "unknown") // intentionally unknown; never a playback gate
            .put("tags", JSONArray(listOf("ambient", "instrumental")))

        val parsed = OpenverseAudioSource().parseTrack(item, AmbientAudioSource.FREESOUND)

        assertNotNull(parsed)
        assertEquals("mp3", parsed?.let { AmbientAudioFormats.canonicalType(it.fileType) })
        assertEquals("https://cdn.example.org/download/audio.mp3", parsed?.downloadUrl)
        assertTrue(parsed?.let { AmbientAudioPlanner.isSafeCandidate(it) } == true)
    }

    @Test
    fun `Audius exposes downloads only for explicitly downloadable tracks`() {
        val source = AudiusAudioSource()
        val downloadable = JSONObject()
            .put("id", "123")
            .put("title", "Audius instrumental")
            .put("duration", 120)
            .put("is_streamable", true)
            .put("is_gated", false)
            .put("is_downloadable", true)
            .put("user", JSONObject().put("name", "Artist"))

        val parsed = source.parseTrack(downloadable)
        val notDownloadable = source.parseTrack(JSONObject(downloadable.toString()).put("is_downloadable", false))

        assertTrue(parsed?.downloadUrl?.contains("/tracks/123/download") == true)
        assertNull(notDownloadable?.downloadUrl)
        assertNull(source.parseTrack(JSONObject(downloadable.toString()).put("is_gated", true)))
    }

    @Test
    fun `Free To Use excludes premium tracks and offers its direct MP3 file`() {
        val source = FreeToUseAudioSource()
        val freeItem = JSONObject()
            .put("id", "free-1")
            .put("title", "Soft piano")
            .put("is_premium", false)
            .put("files", JSONObject().put("mp3", "https://cdn.example.org/soft.mp3"))
            .put("artists", JSONArray().put(JSONArray().put(0).put(JSONObject().put("name", "Artist"))))

        assertEquals("https://cdn.example.org/soft.mp3", source.parseTrack(freeItem)?.downloadUrl)
        assertNull(source.parseTrack(JSONObject(freeItem.toString()).put("is_premium", true)))
    }

    @Test
    fun `Wikimedia Commons parses direct audio file and strips metadata markup`() {
        val page = JSONObject()
            .put("title", "File:Quiet_room.mp3")
            .put("imageinfo", JSONArray().put(
                JSONObject()
                    .put("url", "https://upload.wikimedia.org/audio/quiet_room.mp3")
                    .put("descriptionurl", "https://commons.wikimedia.org/wiki/File:Quiet_room.mp3")
                    .put("mediatype", "AUDIO")
                    .put("mime", "audio/mpeg")
                    .put("size", 123_456)
                    .put("extmetadata", JSONObject()
                        .put("ObjectName", JSONObject().put("value", "<b>Quiet room</b> &amp; rain"))
                        .put("Artist", JSONObject().put("value", "<a href='https://example.org'>Artist</a>"))
                        .put("ImageDescription", JSONObject().put("value", "<p>ambient; rain</p>")))
            ))

        val parsed = WikimediaCommonsAudioSource().parseTrack(page)

        assertEquals("Quiet room & rain", parsed?.title)
        assertEquals("Artist", parsed?.creator)
        assertEquals("https://upload.wikimedia.org/audio/quiet_room.mp3", parsed?.downloadUrl)
    }

    @Test
    fun `ccMixter parses bounded RSS audio items with a direct enclosure`() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel><item>
                <title>Quiet ambient track</title>
                <link>https://ccmixter.org/files/artist/123</link>
                <dc:creator>Artist</dc:creator>
                <itunes:duration>120</itunes:duration>
                <category>ambient</category>
                <enclosure url="https://ccmixter.org/content/quiet.mp3" type="audio/mpeg" length="800000" />
              </item></channel>
            </rss>
        """.trimIndent()

        val parsed = CcMixterAudioSource().parseFeed(feed)

        assertEquals(1, parsed.size)
        assertEquals("ccmixter", parsed.single().provider)
        assertEquals("https://ccmixter.org/content/quiet.mp3", parsed.single().downloadUrl)
        assertEquals(120_000L, parsed.single().durationMs)
    }

    @Test
    fun `Internet Archive selects supported audio file and returns its direct download URL`() {
        val body = JSONObject()
            .put("metadata", JSONObject()
                .put("title", "Archive soundscape")
                .put("creator", "Archive artist")
                .put("runtime", "120")
                .put("subject", JSONArray(listOf("ambient", "instrumental"))))
            .put("files", JSONArray()
                .put(JSONObject().put("name", "readme.txt"))
                .put(JSONObject().put("name", "soundscape.flac").put("size", "987654")))
            .toString()

        val parsed = InternetArchiveAudioSource().parseItemMetadata(body, "archive-id")

        assertEquals("flac", parsed?.fileType)
        assertTrue(parsed?.downloadUrl?.endsWith("/archive-id/soundscape.flac") == true)
        assertEquals(120_000L, parsed?.durationMs)
    }

    @Test
    fun `Radio Browser accepts null-duration HLS live stream but never offers a download`() {
        val station = JSONObject()
            .put("stationuuid", "station-1")
            .put("name", "Ambient Radio")
            .put("url_resolved", "https://radio.example.org/live.m3u8")
            .put("lastcheckok", 1)
            .put("hls", 1)
            .put("codec", "")
            .put("tags", "ambient,chillout")

        val parsed = RadioBrowserAudioSource().parseStation(station)

        assertNotNull(parsed)
        assertNull(parsed?.durationMs)
        assertNull(parsed?.downloadUrl)
        assertTrue(parsed?.let { AmbientAudioPlanner.isSafeCandidate(it) } == true)
        assertFalse(AmbientAudioFormats.isDirectAudioFile(parsed?.fileType.orEmpty()))
    }
}
