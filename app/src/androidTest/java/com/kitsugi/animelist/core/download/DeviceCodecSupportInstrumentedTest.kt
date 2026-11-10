package com.kitsugi.animelist.core.download

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gerçek Android ortamında (emülatör / cihaz) MediaCodecList ve
 * HLS indirme codec uyumluluk motorunun çalıştığını doğrulayan instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class DeviceCodecSupportInstrumentedTest {

    private val tag = "AnimeDownloader"

    @Test
    fun realDeviceCodecSupport_detectsStandardH264AndAac() {
        // Her Android cihazında H.264 (video/avc) ve AAC (audio/mp4a-latm) decoder bulunması zorunludur.
        val avcSupported = DeviceCodecSupport.isMimeSupported("video/avc")
        val aacSupported = DeviceCodecSupport.isMimeSupported("audio/mp4a-latm")

        Log.i(tag, "Cihaz Codec Raporu: " + DeviceCodecSupport.describe())
        Log.i(tag, "AVC desteği: $avcSupported, AAC desteği: $aacSupported")

        assertTrue("Standart Android cihazı video/avc desteklemeli", avcSupported)
        assertTrue("Standart Android cihazı audio/mp4a-latm desteklemeli", aacSupported)

        assertTrue(DeviceCodecSupport.isCodecStringSupported("avc1.4d401f") == true)
        assertTrue(DeviceCodecSupport.isCodecStringSupported("mp4a.40.2") == true)
    }

    @Test
    fun realDevice_hlsPlaylistParser_selectsCompatibleVariant() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=3840x2160,CODECS="avc1.640028,ec-3"
            4k/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720/index.m3u8
        """.trimIndent()

        val parsed = HlsPlaylistParser.parseMaster(master, "https://cdn.example.com/master.m3u8")!!
        val selected = HlsPlaylistParser.selectVariant(parsed)

        assertNotNull(selected)
        Log.i(tag, "Master playlist: ${parsed.variants.size} varyant, seçilen ${selected!!.url} (cihaz uyumlu=true)")

        // 4k varyantı bu cihazda bulunmayan ec-3 (E-AC-3) sesi içerdiği için atlanmalı,
        // cihazın çalabildiği en yüksek bitrate olan 1080p (H.264 + AAC) seçilmeli
        assertEquals("https://cdn.example.com/1080/index.m3u8", selected.url)
        assertEquals(1080, selected.height)
    }

    @Test
    fun realDevice_downloadVerifier_rejectsInvalidFile() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = File(appContext.cacheDir, "test_invalid.mp4")
        testFile.writeText("This is not a real media file content")

        val result = DownloadVerifier.verify(testFile)
        Log.i(tag, "Doğrulama: playable=${result.playable}, warning=${result.warning}, details=${result.details}")

        assertFalse("Bozuk veya geçersiz dosya playable=false dönmeli", result.playable)

        testFile.delete()
    }
}
