package com.kitsugi.animelist.core.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsDownloadAudioAndParserTest {

    // ── URL & İçerik Tespiti (HDFilmCehennemi / FilmMakinesi / DiziBox / Anizm) ──

    @Test
    fun closeAndRapidCdnUrls_areDetectedAsHls() {
        // HDFilmCehennemi & FilmMakinesi Close CDN (.mp4 klasör adı altında /txt/master.txt!)
        assertTrue(
            HlsManifestParser.isLikelyHlsUrl(
                "https://srv9.cdnimages2312.cyou/hls/spidermanbrandnewday-2026-webdl-trdual-tt22084616mp4-EWeEdu0Ig0q.mp4/txt/master.txt"
            )
        )
        // HDFilmCehennemi & FilmMakinesi Rapid CDN
        assertTrue(
            HlsManifestParser.isLikelyHlsUrl(
                "https://s231.rapidrame.com/hls2/01/00037/rsyi6h14btcs_,l,n,.urlset/master.m3u8?t=abc&s=123"
            )
        )
        // Anizm Beta Player & Playmix
        assertTrue(HlsManifestParser.isLikelyHlsUrl("https://playmix.uno/hls/abc123/master.txt"))
        assertTrue(HlsManifestParser.isLikelyHlsUrl("https://anizmplayer.com/stream/9f8a7b/master.txt"))
        // DiziBox MolyStream uzantısız HLS
        assertTrue(HlsManifestParser.isLikelyHlsUrl("https://molystream.org/embed/sheila/abc123xyz"))
        // Sobreatsesuyp playlist.txt
        assertTrue(HlsManifestParser.isLikelyHlsUrl("https://sobreatsesuyp.com/playlist/xyz123.txt"))
    }

    @Test
    fun progressiveAndLocalUris_areNotDetectedAsHls() {
        assertFalse(HlsManifestParser.isLikelyHlsUrl("https://srv.closeload.top/hls/04c0b9/1080p.mp4"))
        assertFalse(HlsManifestParser.isLikelyHlsUrl("https://tau-video.xyz/files/ep1.mp4?token=1"))
        assertFalse(HlsManifestParser.isLikelyHlsUrl("file:///storage/emulated/0/Download/Kitsugi/Video/Film.mp4"))
        assertFalse(HlsManifestParser.isLikelyHlsUrl("file:///storage/emulated/0/Download/Kitsugi/Video/Film.ts"))
        assertFalse(HlsManifestParser.isLikelyHlsUrl("content://com.android.providers.downloads/stream/42"))
    }

    @Test
    fun utf8BomAndPlainUrlHop_areHandledCleanly() {
        val bomManifest = "\uFEFF#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4.0,\nseg0.ts\n".toByteArray(Charsets.UTF_8)
        assertTrue(HlsManifestParser.looksLikeHlsContent("text/plain", bomManifest))

        val hopBody = "\uFEFF  https://s427.rapidrame.com/hls2/01/master.m3u8?t=xyz \r\n"
        assertEquals(
            "https://s427.rapidrame.com/hls2/01/master.m3u8?t=xyz",
            HlsManifestParser.extractPlainUrlHop(hopBody)
        )
        assertNull(HlsManifestParser.extractPlainUrlHop("#EXTM3U\nhttps://cdn.x/a.m3u8"))
    }

    // ── Ayrık Sesli (#EXT-X-MEDIA:TYPE=AUDIO) Master Playlist & Dil Seçimi ─────

    @Test
    fun trDualMasterPlaylist_selectsHighestVideoAndRanksAudioByKind() {
        val baseUrl = "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/txt/master.txt"
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-grp",NAME="English",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="en",URI="../audio/en/index.txt"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-grp",NAME="Türkçe Dublaj",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE="tr",URI="../audio/tr/index.txt"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Türkçe",DEFAULT=YES,LANGUAGE="tr",URI="../subs/tr.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio-grp",SUBTITLES="subs"
            ../720p/index.txt
            #EXT-X-STREAM-INF:BANDWIDTH=4200000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="audio-grp",SUBTITLES="subs"
            ../1080p/index.txt
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            ../audio_only/index.txt
        """.trimIndent()

        assertTrue(HlsManifestParser.hasDetachedAudioRendition(manifest))

        val parsed = HlsManifestParser.parse(manifest, baseUrl) as HlsManifestParser.ParsedHlsPlaylist.Master
        assertEquals(3, parsed.variants.size)
        assertEquals(2, parsed.audioRenditions.size)
        assertEquals(1, parsed.subtitleRenditions.size)

        // 1) DUAL veya Türkçe tercihli kullanıcı -> 1080p video + Türkçe ses birinci aday!
        val dualPlan = HlsManifestParser.selectBestPlan(
            master = parsed,
            preferredAudioLanguages = listOf("tr", "ja", "en"),
            audioKindHint = "dual"
        )
        assertNotNull(dualPlan)
        assertEquals(
            "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/1080p/index.txt",
            dualPlan!!.selectedVariant.url
        )
        assertEquals(2, dualPlan.audioCandidates.size)
        assertEquals(
            "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/audio/tr/index.txt",
            dualPlan.audioCandidates[0].uri
        )
        assertEquals(
            "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/audio/en/index.txt",
            dualPlan.audioCandidates[1].uri
        )

        // 2) Kullanıcı özellikle "Türkçe Altyazılı" ("sub") seçtiyse -> Orijinal (English) ses birinci aday!
        val subPlan = HlsManifestParser.selectBestPlan(
            master = parsed,
            preferredAudioLanguages = listOf("tr", "ja", "en"),
            audioKindHint = "sub"
        )
        assertNotNull(subPlan)
        assertEquals(
            "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/audio/en/index.txt",
            subPlan!!.audioCandidates[0].uri
        )

        // 3) Kullanıcı "Türkçe Dublaj" ("dub") seçtiyse -> Türkçe ses birinci aday!
        val dubPlan = HlsManifestParser.selectBestPlan(
            master = parsed,
            preferredAudioLanguages = listOf("en"),
            audioKindHint = "dub"
        )
        assertNotNull(dubPlan)
        assertEquals(
            "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/audio/tr/index.txt",
            dubPlan!!.audioCandidates[0].uri
        )
    }

    @Test
    fun variantSelection_prefersAudioBearingVariantOverSilentVideoOnly() {
        val manifest = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028"
            silent_1080p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            muxed_1080p.m3u8
        """.trimIndent()

        val parsed = HlsManifestParser.parse(manifest, "https://cdn.example.com/hls/master.m3u8")
            as HlsManifestParser.ParsedHlsPlaylist.Master
        val plan = HlsManifestParser.selectBestPlan(parsed)
        assertNotNull(plan)
        assertEquals("https://cdn.example.com/hls/muxed_1080p.m3u8", plan!!.selectedVariant.url)
        assertTrue(plan.audioCandidates.isEmpty())
    }

    // ── Media Playlist: fMP4 MAP, ByteRange ve AES-128 ────────────────────────

    @Test
    fun mediaPlaylist_parsesInitMapByteRangeAndAes128Keys() {
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-KEY:METHOD=AES-128,URI="keys/k1.key",IV=0x00000000000000000000000000000005
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:4.004,
            #EXT-X-BYTERANGE:1000@720
            main.m4s
            #EXTINF:4.004,
            #EXT-X-BYTERANGE:1200
            main.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:2.500,
            tail.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val media = HlsManifestParser.parse(playlist, "https://cdn.example.com/v/index.m3u8")
            as HlsManifestParser.ParsedHlsPlaylist.Media

        assertEquals(3, media.segments.size)
        assertTrue(media.isEndList)

        val s0 = media.segments[0]
        assertEquals(100L, s0.sequenceNumber)
        assertEquals("https://cdn.example.com/v/init.mp4", s0.initSegment?.url)
        assertEquals(0L, s0.initSegment?.byteRange?.offset)
        assertEquals(720L, s0.initSegment?.byteRange?.length)
        assertEquals(720L, s0.byteRange?.offset)
        assertEquals(1000L, s0.byteRange?.length)
        assertTrue(s0.encryptionKey?.isAes128 == true)
        assertEquals("https://cdn.example.com/v/keys/k1.key", s0.encryptionKey?.uri)

        // Örtük (implicit) ByteRange offset: 720 + 1000 = 1720
        val s1 = media.segments[1]
        assertEquals(101L, s1.sequenceNumber)
        assertEquals(1720L, s1.byteRange?.offset)
        assertEquals(1200L, s1.byteRange?.length)

        // METHOD=NONE sonrası şifresiz segment
        val s2 = media.segments[2]
        assertNull(s2.encryptionKey)
    }

    // ── AES-128 Şifre Çözme ───────────────────────────────────────────────────

    @Test
    fun aes128Decryption_roundTripsWithSequenceAndExplicitIv() {
        val key = ByteArray(16) { (it * 7 + 3).toByte() }
        val plain = ByteArray(188 * 3) { idx -> if (idx % 188 == 0) 0x47.toByte() else (idx and 0xFF).toByte() }

        // Sequence-based IV (seq = 42)
        val seqIv = ByteBuffer.allocate(16).putLong(8, 42L).array()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(seqIv))
        val encrypted = cipher.doFinal(plain)

        val decrypted = HlsSegmentProcessor.decryptAes128(
            encrypted = encrypted,
            keyBytes = key,
            explicitIvHex = null,
            sequenceNumber = 42L
        )
        assertArrayEquals(plain, decrypted)
    }

    // ── Sahte PNG Başlığı Temizleme & Packed Audio ID3 Sökme ──────────────────

    @Test
    fun sanitizeSegment_stripsFakePngHeaderFromDisguisedTs() {
        val fakePngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        val cleanTs = ByteArray(188 * 4)
        for (pkt in 0 until 4) {
            cleanTs[pkt * 188] = 0x47
            cleanTs[pkt * 188 + 1] = 0x41 // PUSI=1, TEI=0
            cleanTs[pkt * 188 + 2] = 0x00
            cleanTs[pkt * 188 + 3] = 0x10
        }
        val trailingJunk = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte())

        val disguised = fakePngHeader + cleanTs + trailingJunk
        val sanitized = HlsSegmentProcessor.sanitizeSegment(disguised)

        assertEquals(HlsSegmentProcessor.SegmentFormat.MPEG_TS, sanitized.format)
        assertArrayEquals(cleanTs, sanitized.toByteArray())
    }

    @Test
    fun sanitizeSegment_extractsApplePtsAndStripsId3FromPackedAdtsAudio() {
        // 90kHz PTS = 90_000 -> 1_000_000 µs (1.0 sn)
        val owner = "com.apple.streaming.transportStreamTimestamp\u0000".toByteArray(Charsets.ISO_8859_1)
        val ptsBytes = ByteBuffer.allocate(8).putLong(90_000L).array()
        val privPayload = owner + ptsBytes
        // Basit ID3v2.4 PRIV frame
        val frameHeader = ByteArray(10).apply {
            this[0] = 'P'.code.toByte()
            this[1] = 'R'.code.toByte()
            this[2] = 'I'.code.toByte()
            this[3] = 'V'.code.toByte()
            this[7] = privPayload.size.toByte()
        }
        val id3Body = frameHeader + privPayload
        val s = id3Body.size
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00,
            ((s ushr 21) and 0x7F).toByte(),
            ((s ushr 14) and 0x7F).toByte(),
            ((s ushr 7) and 0x7F).toByte(),
            (s and 0x7F).toByte()
        )
        val adtsFrame = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x02, 0x1F, 0xFC.toByte(),
            0x21, 0x22, 0x23, 0x24
        )

        val packedSegment = id3Header + id3Body + adtsFrame
        val sanitized = HlsSegmentProcessor.sanitizeSegment(packedSegment)

        assertEquals(HlsSegmentProcessor.SegmentFormat.ADTS_AAC, sanitized.format)
        assertEquals(1_000_000L, sanitized.id3TimestampUs)
        assertArrayEquals(adtsFrame, sanitized.toByteArray())
    }

    // ── MPEG-TS PAT / PMT Akış Denetçisi (AAC vs Dolby E-AC-3) ────────────────

    @Test
    fun tsStreamInspector_distinguishesAacAndDolbyEac3Streams() {
        val aacTs = buildSyntheticTsStream(videoStreamType = 0x1B, audioStreamType = 0x0F)
        val aacInfo = HlsSegmentProcessor.inspectTsBytes(aacTs)
        assertTrue(aacInfo.isMpegTs)
        assertTrue(aacInfo.hasVideo)
        assertTrue(aacInfo.hasAudio)
        assertFalse(aacInfo.hasNonAacAudio)

        val eac3Ts = buildSyntheticTsStream(videoStreamType = 0x1B, audioStreamType = 0x87)
        val eac3Info = HlsSegmentProcessor.inspectTsBytes(eac3Ts)
        assertTrue(eac3Info.isMpegTs)
        assertTrue(eac3Info.hasVideo)
        assertTrue(eac3Info.hasAudio)
        assertTrue(eac3Info.hasNonAacAudio)

        val silentTs = buildSyntheticTsStream(videoStreamType = 0x1B, audioStreamType = null)
        val silentInfo = HlsSegmentProcessor.inspectTsBytes(silentTs)
        assertTrue(silentInfo.isMpegTs)
        assertTrue(silentInfo.hasVideo)
        assertFalse(silentInfo.hasAudio)
    }

    // ── İstek Başlıkları, Dosya Adlandırma & Yan Ses (Companion Audio) Çözümleme ──

    @Test
    fun effectiveHeaders_autoDerivesOriginFromEmbedRefererAndPreservesPluginSubtitleHeaders() {
        val videoUrl = "https://srv9.cdnimages2312.cyou/hls/movie-trdual.mp4/txt/master.txt"
        val rawHeaders = mapOf("Referer" to "https://closeload.filmmakinesi.to/embed/xyz")

        val effective = DownloadHttpClientFactory.buildEffectiveHeaders(
            rawHeaders = rawHeaders,
            videoUrl = videoUrl
        )
        assertEquals("https://closeload.filmmakinesi.to/embed/xyz", effective["Referer"])
        assertEquals("https://closeload.filmmakinesi.to", effective["Origin"])
        assertTrue(!effective["User-Agent"].isNullOrBlank())

        // CloseLoad VTT altyazısı (farklı CDN alt alanında olsa bile embed Referer + Origin korunmalı)
        val pluginSubHeaders = DownloadHttpClientFactory.resolveSubtitleHeaders(
            subtitleUrl = "https://sub.closeload.top/tracks/tr.vtt",
            videoUrl = videoUrl,
            effectiveVideoHeaders = effective
        )
        assertEquals("https://closeload.filmmakinesi.to/embed/xyz", pluginSubHeaders["Referer"])
        assertEquals("https://closeload.filmmakinesi.to", pluginSubHeaders["Origin"])

        // Genel OpenSubtitles servisine yabancı Origin gönderilmemeli
        val publicSubHeaders = DownloadHttpClientFactory.resolveSubtitleHeaders(
            subtitleUrl = "https://dl.opensubtitles.org/en/download/sub/12345",
            videoUrl = videoUrl,
            effectiveVideoHeaders = effective
        )
        assertNull(publicSubHeaders["Origin"])
        assertNull(publicSubHeaders["Referer"])
        assertTrue(!publicSubHeaders["User-Agent"].isNullOrBlank())
    }

    @Test
    fun exportNamingAndOfflinePlayback_resolvesCompanionAudioAndSubtitles() {
        val base = DownloadExportNaming.exportBaseName("Örümcek Adam", 1)
        assertEquals("Örümcek Adam_Bölüm_1", base)

        val audioName = DownloadExportNaming.companionAudioFileName(base, "eac3")
        assertEquals("Örümcek Adam_Bölüm_1.audio.eac3", audioName)
        assertTrue(DownloadExportNaming.isCompanionAudioOfBase(audioName, base))
        assertFalse(DownloadExportNaming.isVideoOfBase(audioName, base))
        assertTrue(DownloadExportNaming.isVideoOfBase("$base.mp4", base))
        assertTrue(DownloadExportNaming.isVideoOfBase("$base.ts", base))

        // Geçici klasörde tam çevrimdışı çözümleme doğrulaması
        val tempDir = java.nio.file.Files.createTempDirectory("kitsugi_dl_test").toFile()
        try {
            val videoFile = java.io.File(tempDir, "$base.mp4").apply { writeBytes(ByteArray(128) { 1 }) }
            val audioFile = java.io.File(tempDir, audioName).apply { writeBytes(ByteArray(64) { 2 }) }
            val subFile = java.io.File(
                tempDir,
                DownloadExportNaming.subtitleFileName(base, "tr", "Türkçe", "srt")
            ).apply { writeText("1\n00:00:01,000 --> 00:00:03,000\nMerhaba\n") }

            val params = com.kitsugi.animelist.core.player.OfflinePlaybackHelper.resolveFilePlaybackParams(
                videoFile = videoFile,
                fallbackTitle = "Örümcek Adam - Bölüm 1"
            )
            assertNotNull(params)
            assertEquals("file://${videoFile.absolutePath}", params!!.videoUri)
            assertEquals("file://${audioFile.absolutePath}", params.audioUri)
            assertEquals(1, params.subtitles.size)
            assertEquals("file://${subFile.absolutePath}", params.subtitles[0].url)
            assertEquals("tr", params.subtitles[0].lang)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildSyntheticTsStream(videoStreamType: Int, audioStreamType: Int?): ByteArray {
        val out = ByteArrayOutputStream()

        // Paket 1: PAT (PID 0x0000 -> PMT PID 0x0100)
        val patPkt = ByteArray(188) { 0xFF.toByte() }
        patPkt[0] = 0x47
        patPkt[1] = 0x40 // PUSI=1, PID=0
        patPkt[2] = 0x00
        patPkt[3] = 0x10 // payload only
        patPkt[4] = 0x00 // pointer_field
        patPkt[5] = 0x00 // table_id = PAT
        patPkt[6] = 0xB0.toByte()
        patPkt[7] = 0x0D // section_length = 13
        patPkt[8] = 0x00; patPkt[9] = 0x01 // transport_stream_id
        patPkt[10] = 0xC1.toByte()
        patPkt[11] = 0x00; patPkt[12] = 0x00
        patPkt[13] = 0x00; patPkt[14] = 0x01 // program_number = 1
        patPkt[15] = 0xE1.toByte(); patPkt[16] = 0x00 // PMT PID = 0x0100
        out.write(patPkt)

        // Paket 2: PMT (PID 0x0100 -> Video PID 0x0101, Audio PID 0x0102)
        val pmtPkt = ByteArray(188) { 0xFF.toByte() }
        pmtPkt[0] = 0x47
        pmtPkt[1] = 0x41 // PUSI=1, PID=0x0100
        pmtPkt[2] = 0x00
        pmtPkt[3] = 0x10
        pmtPkt[4] = 0x00 // pointer_field
        pmtPkt[5] = 0x02 // table_id = PMT
        val esBytesLen = if (audioStreamType != null) 10 else 5
        val secLen = 9 + esBytesLen + 4
        pmtPkt[6] = 0xB0.toByte()
        pmtPkt[7] = secLen.toByte()
        pmtPkt[8] = 0x00; pmtPkt[9] = 0x01
        pmtPkt[10] = 0xC1.toByte()
        pmtPkt[11] = 0x00; pmtPkt[12] = 0x00
        pmtPkt[13] = 0xE1.toByte(); pmtPkt[14] = 0x01 // PCR_PID = 0x0101
        pmtPkt[15] = 0xF0.toByte(); pmtPkt[16] = 0x00 // program_info_length = 0
        // Video ES
        pmtPkt[17] = videoStreamType.toByte()
        pmtPkt[18] = 0xE1.toByte(); pmtPkt[19] = 0x01 // PID 0x0101
        pmtPkt[20] = 0xF0.toByte(); pmtPkt[21] = 0x00
        if (audioStreamType != null) {
            pmtPkt[22] = audioStreamType.toByte()
            pmtPkt[23] = 0xE1.toByte(); pmtPkt[24] = 0x02 // PID 0x0102
            pmtPkt[25] = 0xF0.toByte(); pmtPkt[26] = 0x00
        }
        out.write(pmtPkt)

        // Paket 3: Video PES (PID 0x0101)
        val vidPkt = ByteArray(188)
        vidPkt[0] = 0x47; vidPkt[1] = 0x41; vidPkt[2] = 0x01; vidPkt[3] = 0x10
        out.write(vidPkt)

        // Paket 4: Audio PES (PID 0x0102)
        if (audioStreamType != null) {
            val audPkt = ByteArray(188)
            audPkt[0] = 0x47; audPkt[1] = 0x41; audPkt[2] = 0x02; audPkt[3] = 0x10
            out.write(audPkt)
        }

        return out.toByteArray()
    }
}
