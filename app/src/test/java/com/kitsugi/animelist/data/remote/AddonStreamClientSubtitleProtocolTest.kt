package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.player.SubtitleFileCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AddonStreamClientSubtitleProtocolTest {

    private val client = AddonStreamClient()

    @Test
    fun `without extra hints builds canonical 3-segment Stremio subtitle URL`() {
        val urls = client.buildStremioSubtitleUrls(
            normalizedManifest = "https://altyazidb.online/manifest.json",
            type = "series",
            id = "tt13622776:1:1",
            extraParams = null
        )
        assertEquals(
            listOf("https://altyazidb.online/subtitles/series/tt13622776:1:1.json"),
            urls
        )
    }

    @Test
    fun `with extra hints builds 4-segment URL first and 3-segment canonical fallback second`() {
        val urls = client.buildStremioSubtitleUrls(
            normalizedManifest = "https://opensubtitles-v3.strem.io/manifest.json?lang=tr",
            type = "series",
            id = "tt13622776:1:1",
            extraParams = mapOf(
                "videoHash" to "8e245d9679d31e12",
                "videoSize" to "1482910208",
                "filename" to "VisionQuest S01E01.mkv"
            )
        )
        assertEquals(2, urls.size)
        assertEquals(
            "https://opensubtitles-v3.strem.io/subtitles/series/tt13622776:1:1/" +
                "videoHash=8e245d9679d31e12&videoSize=1482910208&filename=VisionQuest%20S01E01.mkv.json?lang=tr",
            urls[0]
        )
        assertEquals(
            "https://opensubtitles-v3.strem.io/subtitles/series/tt13622776:1:1.json?lang=tr",
            urls[1]
        )
    }

    @Test
    fun `parses wrapper and array subtitle responses and resolves relative URLs`() {
        val wrapperJson = """
            {
              "subtitles": [
                {"id": "1", "url": "/download/sub1.srt", "language": "tur"},
                {"id": "2", "url": "https://cdn.example.com/sub2.ass", "lang": "en", "format": "ass"}
              ]
            }
        """.trimIndent()
        val items = client.parseSubtitlePayload(wrapperJson, "https://altyazidb.online/v1")
        assertEquals(2, items.size)
        assertEquals("https://altyazidb.online/download/sub1.srt", items[0].url)
        assertEquals("tur", items[0].lang)
        assertEquals("https://cdn.example.com/sub2.ass", items[1].url)
        assertEquals("en", items[1].lang)

        val arrayJson = """[{"id":"3","url":"//subs.example.org/ep1.vtt","SubLanguageID":"tr"}]"""
        val arrayItems = client.parseSubtitlePayload(arrayJson, "https://example.org")
        assertEquals(1, arrayItems.size)
        assertEquals("https://subs.example.org/ep1.vtt", arrayItems[0].url)
        assertEquals("tr", arrayItems[0].lang)
    }

    @Test
    fun `unpacks gzip and zip compressed subtitle archives`() {
        val srtText = "1\n00:00:01,000 --> 00:00:03,000\nMerhaba dünya!\n"
        val gzBytes = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gos -> gos.write(srtText.toByteArray(Charsets.UTF_8)) }
            bos.toByteArray()
        }
        val (unpackedGz, _) = SubtitleFileCache.unpackCompressedSubtitleIfNeeded(gzBytes)
        assertEquals(srtText, unpackedGz.toString(Charsets.UTF_8))

        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("info".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("VisionQuest.S01E01.tr.ass"))
                zos.write("[Script Info]\nTitle: Test".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        val (unpackedZip, entryName) = SubtitleFileCache.unpackCompressedSubtitleIfNeeded(zipBytes)
        assertEquals("VisionQuest.S01E01.tr.ass", entryName)
        assertTrue(unpackedZip.toString(Charsets.UTF_8).startsWith("[Script Info]"))
    }
}
