package com.kitsugi.animelist.core.download

import com.google.gson.Gson
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.DownloadKind
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class UniversalDownloadEngineTest {

    @Test
    fun sniffer_detectsManifestsContainersAndRejectsHtmlErrorPages() {
        val m3u8InTxt = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=2500000\nv1080.txt\n".toByteArray()
        assertEquals(
            StreamFormatSniffer.DetectedFormat.HLS_MANIFEST,
            StreamFormatSniffer.sniff(m3u8InTxt, contentType = "text/plain", url = "https://srv9.cdnimages.cyou/hls/film.mp4/master.txt")
        )

        val mpdBytes = """<?xml version="1.0"?><MPD mediaPresentationDuration="PT10S"><Period/></MPD>""".toByteArray()
        assertEquals(
            StreamFormatSniffer.DetectedFormat.DASH_MANIFEST,
            StreamFormatSniffer.sniff(mpdBytes, contentType = "application/xml")
        )

        val cfHtml = "<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>".toByteArray()
        assertEquals(
            StreamFormatSniffer.DetectedFormat.HTML_PAGE,
            StreamFormatSniffer.sniff(cfHtml, contentType = "text/html", url = "https://cdn.example.com/video.mp4")
        )

        // ISO BMFF MP4 vs M4A vs fMP4 styp
        val mp4Header = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray(Charsets.ISO_8859_1) + ByteArray(12)
        assertEquals(StreamFormatSniffer.DetectedFormat.MP4, StreamFormatSniffer.sniff(mp4Header))

        val m4aHeader = byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(12)
        assertEquals(StreamFormatSniffer.DetectedFormat.M4A, StreamFormatSniffer.sniff(m4aHeader))

        val stypHeader = byteArrayOf(0, 0, 0, 24) + "stypmsdh".toByteArray(Charsets.ISO_8859_1) + ByteArray(12)
        assertEquals(StreamFormatSniffer.DetectedFormat.FMP4_SEGMENT, StreamFormatSniffer.sniff(stypHeader))

        // Matroska vs WebM EBML
        val mkvHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + "matroska".toByteArray(Charsets.ISO_8859_1)
        assertEquals(StreamFormatSniffer.DetectedFormat.MKV, StreamFormatSniffer.sniff(mkvHeader))

        val webmHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + "webm".toByteArray(Charsets.ISO_8859_1)
        assertEquals(StreamFormatSniffer.DetectedFormat.WEBM, StreamFormatSniffer.sniff(webmHeader))
    }

    @Test
    fun sniffer_stripsFakePngHeaderFromDisguisedMpegTsSegment() {
        val fakePngPrefix = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        val tsPackets = ByteArray(188 * 4)
        for (i in 0 until 4) {
            tsPackets[i * 188] = 0x47
            tsPackets[i * 188 + 1] = 0x40
            tsPackets[i * 188 + 2] = 0x11
            tsPackets[i * 188 + 3] = 0x10
        }
        val disguised = fakePngPrefix + tsPackets

        assertEquals(StreamFormatSniffer.DetectedFormat.MPEG_TS, StreamFormatSniffer.sniff(disguised, contentType = "image/png"))

        val sanitized = StreamFormatSniffer.sanitizeDisguisedTsSegment(disguised)
        assertEquals(tsPackets.size, sanitized.size)
        assertEquals(0x47.toByte(), sanitized[0])
        assertArrayEquals(tsPackets, sanitized)
    }

    @Test
    fun sniffer_stripsLeadingId3TagFromPackedAudioSegment() {
        // 10-byte ID3v2 header with 6-byte payload => 16 bytes total ID3 tag, followed by ADTS syncword FF F1
        val id3Tag = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x04, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x06,
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06
        )
        val adtsPayload = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x01, 0xFF.toByte(), 0xFC.toByte())
        val packed = id3Tag + adtsPayload

        val stripped = StreamFormatSniffer.stripLeadingId3Tags(packed)
        assertArrayEquals(adtsPayload, stripped)
    }

    @Test
    fun hlsMaster_parsesDemuxedAndMuxedAudioAccordingToRfc8216() {
        val masterUrl = "https://srv21.cdnimages01.cyou/hls/movie.mp4/master.txt"
        val masterBody = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud-main",NAME="Türkçe Dublaj",LANGUAGE="tr",DEFAULT=YES,AUTOSELECT=YES,CHANNELS="2",URI="txt/audio_tr.txt"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud-main",NAME="Original English",LANGUAGE="en",DEFAULT=NO,AUTOSELECT=YES,CHANNELS="6",URI="txt/audio_en.txt"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud-muxed",NAME="Embedded Audio",LANGUAGE="ja",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="aud-main"
            txt/v720.txt
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,ec-3",AUDIO="aud-main"
            txt/v1080.txt
        """.trimIndent()

        val parsed = HlsManifestResolver.parse(masterUrl, masterBody) as? HlsManifestResolver.ParsedPlaylist.Master
        assertNotNull(parsed)
        masterBody.let { _ ->
            val master = parsed!!
            assertEquals(2, master.variants.size)
            val best = HlsManifestResolver.selectBestVariant(master)
            assertNotNull(best)
            assertEquals(1080, best!!.height)
            assertEquals("https://srv21.cdnimages01.cyou/hls/movie.mp4/txt/v1080.txt", best.url)

            val renditions = HlsManifestResolver.audioRenditionsForVariant(master, best)
            assertEquals(2, renditions.size)
            assertTrue(renditions.all { it.isSeparateStream })

            // Muxed rendition without URI must report isSeparateStream = false
            val muxedRendition = master.audioRenditions.first { it.groupId == "aud-muxed" }
            assertFalse(muxedRendition.isSeparateStream)

            // Default preference selects Turkish ("tr")
            val preferred = HlsManifestResolver.selectPreferredAudioRendition(renditions)
            assertNotNull(preferred)
            assertEquals("tr", preferred!!.normalizedLang)
            assertEquals("https://srv21.cdnimages01.cyou/hls/movie.mp4/txt/audio_tr.txt", preferred.uri)

            // Explicit English selection selects the 6-channel track
            val explicitEn = HlsManifestResolver.selectPreferredAudioRendition(
                renditions = renditions,
                requestedLang = "en"
            )
            assertNotNull(explicitEn)
            assertEquals("en", explicitEn!!.normalizedLang)
            assertEquals(6, explicitEn.channels)
        }
    }

    @Test
    fun hlsMedia_parsesInitMapByteRangesAndDecryptsAes128() {
        val mediaUrl = "https://cdn.example.com/hls/v1080/index.m3u8"
        val mediaBody = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="800@0"
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/main.key"
            #EXTINF:4.000,
            #EXT-X-BYTERANGE:1024@800
            main.m4s
            #EXTINF:4.000,
            #EXT-X-BYTERANGE:2048
            main.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val media = HlsManifestResolver.parse(mediaUrl, mediaBody) as? HlsManifestResolver.ParsedPlaylist.Media
        assertNotNull(media)
        val m = media!!
        assertTrue(m.isEndList)
        assertEquals(2, m.segments.size)

        val seg0 = m.segments[0]
        assertEquals("https://cdn.example.com/hls/v1080/init.mp4", seg0.initSegment?.url)
        assertEquals("bytes=0-799", seg0.initSegment?.byteRange?.toHttpRangeHeader())
        assertEquals("bytes=800-1823", seg0.byteRange?.toHttpRangeHeader())
        assertEquals(10L, seg0.sequenceNumber)
        assertEquals("https://cdn.example.com/hls/keys/main.key", seg0.encryption?.keyUrl)

        // Implicit byte-range offset advances automatically for same URL
        val seg1 = m.segments[1]
        assertEquals(1824L, seg1.byteRange?.offset)
        assertEquals("bytes=1824-3871", seg1.byteRange?.toHttpRangeHeader())
        assertEquals(11L, seg1.sequenceNumber)

        // AES-128-CBC roundtrip with sequence-derived IV
        val keyBytes = ByteArray(16) { (it + 1).toByte() }
        val ivBytes = seg0.encryption!!.resolveIv(seg0.sequenceNumber)
        val plain = "Kitsugi lossless AES-128 segment test payload!".toByteArray(Charsets.UTF_8)
        val encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        }
        val encrypted = encCipher.doFinal(plain)
        val decrypted = HlsManifestResolver.decryptAes128(encrypted, keyBytes, ivBytes)
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun dashManifest_parsesSegmentTimelineAndMultiLanguageAudio() {
        val mpdUrl = "https://cdn.dizibox.example/dash/ep1/manifest.mpd"
        val mpdXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD mediaPresentationDuration="PT12S">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" initialization="init-${'$'}RepresentationID${'$'}.m4s" media="seg-${'$'}RepresentationID${'$'}-${'$'}Number%04d${'$'}.m4s" startNumber="1">
                    <SegmentTimeline>
                      <S t="0" d="4000" r="2"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v720" bandwidth="2000000" width="1280" height="720" codecs="avc1.4d401f"/>
                  <Representation id="v1080" bandwidth="4200000" width="1920" height="1080" codecs="avc1.640028"/>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4" lang="tur" label="Türkçe Dublaj">
                  <AudioChannelConfiguration value="2"/>
                  <SegmentTemplate timescale="1000" initialization="init-${'$'}RepresentationID${'$'}.m4s" media="aud-${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s" duration="4000" startNumber="1"/>
                  <Representation id="a_tr" bandwidth="128000" codecs="mp4a.40.2"/>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/webm" lang="jpn">
                  <Label>Japanese Original</Label>
                  <Representation id="a_ja" bandwidth="160000" codecs="opus">
                    <BaseURL>audio_ja_full.webm</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val parsed = DashManifestResolver.parse(mpdUrl, mpdXml)
        assertNotNull(parsed)
        val dash = parsed!!
        assertEquals(2, dash.videoRepresentations.size)
        assertEquals(2, dash.audioRepresentations.size)

        val v1080 = dash.videoRepresentations.maxByOrNull { it.height }!!
        assertEquals(1080, v1080.height)
        assertEquals("https://cdn.dizibox.example/dash/ep1/init-v1080.m4s", v1080.initSegment?.url)
        assertEquals(3, v1080.mediaSegments.size)
        assertEquals("https://cdn.dizibox.example/dash/ep1/seg-v1080-0001.m4s", v1080.mediaSegments[0].url)
        assertEquals("https://cdn.dizibox.example/dash/ep1/seg-v1080-0003.m4s", v1080.mediaSegments[2].url)

        val trAudio = dash.audioRepresentations.first { it.normalizedLang == "tr" }
        assertEquals(3, trAudio.mediaSegments.size)
        assertEquals("https://cdn.dizibox.example/dash/ep1/aud-a_tr-1.m4s", trAudio.mediaSegments[0].url)

        val jaAudio = dash.audioRepresentations.first { it.normalizedLang == "ja" }
        assertNull(jaAudio.initSegment)
        assertEquals(1, jaAudio.mediaSegments.size)
        assertEquals("https://cdn.dizibox.example/dash/ep1/audio_ja_full.webm", jaAudio.mediaSegments[0].url)
    }

    @Test
    fun headersNamingAndModel_preserveBackwardCompatibilityAndCdnPolicies() {
        // CloseLoad / Rapidrame derives Origin from embed Referer and strips internal X-Kitsugi-* headers
        val normalizedCloseLoad = StreamDownloadHttpClient.normalizeHeadersForUrl(
            url = "https://srv14.cdnimages02.cyou/hls/film.mp4/master.txt",
            rawHeaders = mapOf(
                "Referer" to "https://closeload.filmmakinesi.to/embed/xyz",
                ExternalAudioTrack.META_HEADER_LABEL to "Türkçe",
                ExternalAudioTrack.META_HEADER_LANG to "tr"
            )
        )
        assertEquals("https://closeload.filmmakinesi.to", normalizedCloseLoad["Origin"])
        assertFalse(normalizedCloseLoad.containsKey(ExternalAudioTrack.META_HEADER_LABEL))
        assertFalse(normalizedCloseLoad.containsKey(ExternalAudioTrack.META_HEADER_LANG))

        // ok.ru / mycdn.me must NOT receive auto-injected foreign Origin
        val normalizedOkRu = StreamDownloadHttpClient.normalizeHeadersForUrl(
            url = "https://vd312.mycdn.me/?expires=123",
            rawHeaders = mapOf("Referer" to "https://ok.ru/videoembed/12345")
        )
        assertFalse(normalizedOkRu.containsKey("Origin"))

        // Export naming for video, companion audio, and standalone audio
        assertEquals("Solo Leveling_Bölüm_3.mp4", DownloadExportNaming.exportFileName("Solo Leveling", 3, "mp4"))
        assertEquals(
            "Solo Leveling_Bölüm_3.tr_Türkçe_Dublaj.m4a",
            DownloadExportNaming.audioExportFileName("Solo Leveling", 3, "tr", "Türkçe Dublaj", "m4a")
        )

        // Gson legacy deserialization (missing downloadKind / audio / headers fields) + uniqueKey isolation
        val legacyJson = """{"animeId":"151807","animeTitle":"Solo Leveling","episode":3,"season":1,"url":"https://example.com/ep3.m3u8","quality":"1080p","status":"COMPLETED","progress":100}"""
        val restored = Gson().fromJson(legacyJson, AnimeDownload::class.java).normalized()
        assertFalse(restored.isAudioOnly)
        assertEquals(DownloadKind.VIDEO, restored.effectiveKind)

        val audioTr = restored.copy(
            downloadKind = DownloadKind.AUDIO,
            audioTrackKey = "tr_0",
            audioTrackLang = "tr"
        )
        val audioJa = restored.copy(
            downloadKind = DownloadKind.AUDIO,
            audioTrackKey = "ja_1",
            audioTrackLang = "ja"
        )
        assertTrue(audioTr.isAudioOnly)
        assertNotEquals(restored.uniqueKey, audioTr.uniqueKey)
        assertNotEquals(audioTr.uniqueKey, audioJa.uniqueKey)
    }

    @Test
    fun universalTranscoder_detectsUnsupportedCodecsAndMultichannelSurround() {
        // 1-2 channel standard AAC-LC and MP3 are universally supported without re-encoding
        assertTrue(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mp4a-latm", "mp4a.40.2", 2))
        assertTrue(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mp4a-latm", "mp4a.40.5", 1))
        assertTrue(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mpeg", "mp3", 2))

        // Device-incompatible codecs MUST trigger automatic transcode to universal AAC-LC (.m4a)
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/eac3", "ec-3", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/eac3-joc", "ec-3", 6))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/ac3", "ac-3", 6))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/vnd.dts", "dtsc", 6))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/vnd.dts.hd", "dtsh", 8))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/true-hd", "mlp", 8))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/opus", "opus", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/vorbis", "vorbis", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/flac", "flac", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/alac", "alac", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/raw", "", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mpeg-L2", "", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mp4a-latm", "mp4a.40.42", 2)) // xHE-AAC

        // Multichannel (5.1 / 7.1) AAC or MP3 MUST downmix & transcode to Stereo AAC-LC (.m4a)
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mp4a-latm", "mp4a.40.2", 6))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mp4a-latm", "mp4a.40.2", 8))
        assertFalse(UniversalAudioTranscoder.isUniversalStandaloneCodec("audio/mpeg", "mp3", 6))

        // MP4 video mux codec policy: only 1-2 channel AAC-LC is universally muxable into .mp4 without transcode
        assertTrue(UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec("audio/mp4a-latm", "mp4a.40.2", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec("audio/eac3", "ec-3", 6))
        assertFalse(UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec("audio/opus", "opus", 2))
        assertFalse(UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec("audio/mp4a-latm", "mp4a.40.2", 6))
    }

    @Test
    fun universalTranscoder_downmixesSurroundConvertsPcmAndResamplesHiResAudio() {
        // 1) 5.1 surround frame where ONLY Center (dialog/dubbing) channel has signal (ch[2] = 16000)
        //    ITU-R BS.775 downmix must route Center equally to Left and Right so speech is never silent!
        val surround51 = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0)     // FL
            putShort(0)     // FR
            putShort(16000) // FC (speech!)
            putShort(0)     // LFE
            putShort(0)     // SL
            putShort(0)     // SR
        }.array()
        val stereo = UniversalAudioTranscoder.downmixToStereoPcm16Le(surround51, 6)
        assertEquals(4, stereo.size)
        val stereoBuf = java.nio.ByteBuffer.wrap(stereo).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val left = stereoBuf.short.toInt()
        val right = stereoBuf.short.toInt()
        assertEquals(left, right)
        assertTrue("Center speech must be audible in stereo downmix (got $left)", left > 7000)

        // 2) Float 32-bit PCM -> 16-bit signed LE PCM
        val floatPcm = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0.5f)
            putFloat(-0.5f)
        }.array()
        val pcm16 = UniversalAudioTranscoder.convertPcmTo16BitLe(floatPcm, androidx.media3.common.C.ENCODING_PCM_FLOAT)
        val pcm16Buf = java.nio.ByteBuffer.wrap(pcm16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertTrue(kotlin.math.abs(16384 - pcm16Buf.short.toInt()) <= 2)
        assertTrue(kotlin.math.abs(-16384 - pcm16Buf.short.toInt()) <= 2)

        // 3) Hi-Res sample rate selection & resampling (96 kHz / 192 kHz -> 48 kHz; 88.2 kHz -> 44.1 kHz)
        assertEquals(48000, UniversalAudioTranscoder.chooseTargetSampleRate(96000))
        assertEquals(48000, UniversalAudioTranscoder.chooseTargetSampleRate(192000))
        assertEquals(44100, UniversalAudioTranscoder.chooseTargetSampleRate(88200))
        assertEquals(44100, UniversalAudioTranscoder.chooseTargetSampleRate(44100))

        // 100 stereo frames at 96 kHz (400 bytes) -> 50 stereo frames at 48 kHz (200 bytes)
        val hiRes96k = ByteArray(100 * 4)
        val resampled48k = UniversalAudioTranscoder.resamplePcm16Le(hiRes96k, channels = 2, inSampleRate = 96000, outSampleRate = 48000)
        assertEquals(50 * 4, resampled48k.size)
    }

    @Test
    fun snifferAndInspector_detectFlvAviAndConversionBadges() {
        val flvHeader = byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte(), 0x01, 0x05, 0, 0, 0, 9)
        assertEquals(StreamFormatSniffer.DetectedFormat.FLV, StreamFormatSniffer.sniff(flvHeader))

        val aviHeader = "RIFF\u0000\u0000\u0000\u0000AVI LIST".toByteArray(Charsets.ISO_8859_1)
        assertEquals(StreamFormatSniffer.DetectedFormat.AVI, StreamFormatSniffer.sniff(aviHeader))

        val eac3Track = AudioTrackInspector.DownloadableAudioTrack(
            key = "tr_0", index = 0, label = "Türkçe", lang = "tr", codecHint = "ec-3", channels = 6
        )
        assertEquals("E-AC3 → M4A", eac3Track.codecBadge())

        val dtsTrack = AudioTrackInspector.DownloadableAudioTrack(
            key = "en_1", index = 1, label = "English", lang = "en", codecHint = "audio/vnd.dts.hd", channels = 8
        )
        assertEquals("DTS → M4A", dtsTrack.codecBadge())

        val surroundAacTrack = AudioTrackInspector.DownloadableAudioTrack(
            key = "ja_0", index = 0, label = "Japanese", lang = "ja", codecHint = "mp4a.40.2", channels = 6
        )
        assertEquals("AAC 6K → M4A", surroundAacTrack.codecBadge())

        val stereoAacTrack = AudioTrackInspector.DownloadableAudioTrack(
            key = "tr_1", index = 1, label = "Türkçe", lang = "tr", codecHint = "mp4a.40.2", channels = 2
        )
        assertEquals("AAC", stereoAacTrack.codecBadge())
    }
}
