package com.kitsugi.animelist.core.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DashAudioExportTest {
    private val base = "https://cdn.example/series/master.mpd"
    private val tr = AudioTrackIdentity("tr", null)

    @Test fun `static template chooses uniquely matched representation and expands numbered URLs`() {
        val xml = """
            <MPD type="static" mediaPresentationDuration="PT6S">
              <BaseURL>media/</BaseURL><Period>
                <AdaptationSet contentType="audio" lang="tr">
                  <Representation id="hi" bandwidth="256000"/>
                  <Representation id="lo" bandwidth="128000"/>
                  <SegmentTemplate timescale="1" duration="2" startNumber="5"
                    initialization="${'$'}RepresentationID${'$'}-init.mp4"
                    media="${'$'}RepresentationID${'$'}-${'$'}Number%03d${'$'}.m4s"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val plan = DashAudioExport.plan(xml, base, tr, listOf(tr), preferredBitrate = 128000)
        assertEquals(listOf("lo-init.mp4", "lo-005.m4s", "lo-006.m4s", "lo-007.m4s"),
            plan.parts.map { it.url.substringAfterLast('/') })
        assertEquals("https://cdn.example/series/media/lo-005.m4s", plan.parts[1].url)
        // A sole player's Turkish row represents the same audio voice at two bitrates.
        assertEquals("hi-init.mp4", DashAudioExport.plan(xml, base, tr, listOf(tr)).parts.first().url.substringAfterLast('/'))
        assertEquals("hi-init.mp4", DashAudioExport.plan(xml, base, tr, listOf(tr),
            preferredBitrate = 64000).parts.first().url.substringAfterLast('/'))
    }

    @Test fun `static timeline expands negative repeat to end of period`() {
        val xml = """
            <MPD type="static" mediaPresentationDuration="PT6S"><Period>
              <AdaptationSet contentType="audio" lang="tr"><Representation id="a" bandwidth="128000">
                <SegmentTemplate timescale="1" startNumber="1" initialization="init.mp4"
                  media="seg-${'$'}Time${'$'}.m4s"><SegmentTimeline><S t="0" d="2" r="-1"/></SegmentTimeline>
                </SegmentTemplate>
              </Representation></AdaptationSet>
            </Period></MPD>
        """.trimIndent()
        assertEquals(listOf("init.mp4", "seg-0.m4s", "seg-2.m4s", "seg-4.m4s"),
            DashAudioExport.plan(xml, base, tr, listOf(tr)).parts.map { it.url.substringAfterLast('/') })
    }

    @Test fun `segment list handles explicit HTTP byte ranges`() {
        val xml = """
            <MPD type="static"><Period><AdaptationSet contentType="audio" lang="tr">
              <Representation id="only" bandwidth="64000"><BaseURL>voice.mp4</BaseURL>
                <SegmentList><Initialization range="0-99"/>
                  <SegmentURL mediaRange="100-199"/><SegmentURL mediaRange="200-299"/>
                </SegmentList>
              </Representation></AdaptationSet></Period></MPD>
        """.trimIndent()
        val parts = DashAudioExport.plan(xml, base, tr, listOf(tr)).parts
        assertEquals(3, parts.size)
        assertEquals("https://cdn.example/series/voice.mp4", parts[0].url)
        assertEquals(AudioExportHttp.Range(0, 99), parts[0].range)
        assertEquals(AudioExportHttp.Range(200, 299), parts[2].range)
    }

    @Test fun `sole DASH set with conflicting declared language is not relabeled`() {
        val xml = """<MPD type="static"><Period><AdaptationSet contentType="audio" lang="en">
            <Representation id="voice"><BaseURL>en.m4a</BaseURL></Representation>
            </AdaptationSet></Period></MPD>"""
        assertThrows(RuntimeException::class.java) {
            DashAudioExport.plan(xml, base, tr, listOf(tr))
        }
    }

    @Test fun `DASH ABR fallback refuses multiple player rows of the same language`() {
        val selected = AudioTrackIdentity("tr", "Turkish")
        val xml = """
            <MPD type="static" mediaPresentationDuration="PT2S"><Period>
              <AdaptationSet contentType="audio" lang="tr"><Label>Turkish</Label>
                <Representation id="one" bandwidth="64000"/>
                <Representation id="two" bandwidth="128000"/>
                <SegmentTemplate duration="2" timescale="1" initialization="init-${'$'}RepresentationID${'$'}.m4a"
                  media="seg-${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s"/>
              </AdaptationSet>
            </Period></MPD>
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            DashAudioExport.plan(xml, base, selected, listOf(selected, selected))
        }
        assertEquals("init-two.m4a", DashAudioExport.plan(xml, base, selected,
            listOf(selected, selected), preferredBitrate = 128000).parts.first().url.substringAfterLast('/'))
        assertThrows(IllegalArgumentException::class.java) {
            DashAudioExport.plan(xml.replace("bandwidth=\"64000\"", "bandwidth=\"128000\""),
                base, selected, listOf(selected))
        }
    }

    @Test fun `external DASH URL only counts rows on that same media file`() {
        val xml = """
            <MPD type="static"><Period><AdaptationSet contentType="audio">
              <Representation id="voice"><BaseURL>voice.m4a</BaseURL></Representation>
            </AdaptationSet></Period></MPD>
        """.trimIndent()
        val external = AudioTrackIdentity(null, null, "https://cdn.example/voice/manifest.mpd")
        val plan = DashAudioExport.plan(xml, base, external,
            listOf(external, AudioTrackIdentity("en", "Video audio")))
        assertEquals("https://cdn.example/series/voice.m4a", plan.parts.single().url)
    }

    @Test fun `static SegmentBase downloads complete representation instead of sidx bytes`() {
        val xml = """
            <MPD type="static"><Period><AdaptationSet contentType="audio" lang="tr">
              <Representation id="tr" bandwidth="128000"><BaseURL>audio-file.m4a?token=abc</BaseURL>
                <SegmentBase indexRange="300-399"><Initialization range="0-299"/></SegmentBase>
              </Representation></AdaptationSet></Period></MPD>
        """.trimIndent()
        val plan = DashAudioExport.plan(xml, base, tr, listOf(tr))
        assertEquals(1, plan.parts.size)
        assertEquals("https://cdn.example/series/audio-file.m4a?token=abc", plan.parts.single().url)
        assertEquals(null, plan.parts.single().range)
        assertThrows(IllegalArgumentException::class.java) {
            DashAudioExport.plan(xml.replace("<BaseURL>audio-file.m4a?token=abc</BaseURL>", ""), base, tr, listOf(tr))
        }
    }

    @Test fun `dynamic multi period DRM and XML entities are rejected`() {
        val voice = "<AdaptationSet contentType=\"audio\" lang=\"tr\"><Representation id=\"a\"><BaseURL>voice.m4a</BaseURL></Representation></AdaptationSet>"
        val cases = listOf(
            "<MPD type=\"dynamic\"><Period>$voice</Period></MPD>",
            "<MPD type=\"static\"><Period>$voice</Period><Period>$voice</Period></MPD>",
            "<MPD type=\"static\"><Period><AdaptationSet contentType=\"audio\" lang=\"tr\"><ContentProtection schemeIdUri=\"urn:uuid:DRM\"/><Representation id=\"a\"><BaseURL>voice.m4a</BaseURL></Representation></AdaptationSet></Period></MPD>",
            "<!DOCTYPE MPD [<!ENTITY spy SYSTEM \"file:///etc/passwd\">]><MPD type=\"static\"><Period>$voice</Period></MPD>",
            "<MPD type=\"static\"><Period start=\"PT2S\">$voice</Period></MPD>"
        )
        cases.forEach { xml ->
            assertThrows(Exception::class.java) { DashAudioExport.plan(xml, base, tr, listOf(tr)) }
        }
    }

    @Test fun `duplicate language without a distinct label is rejected`() {
        val xml = """
            <MPD type="static"><Period>
              <AdaptationSet contentType="audio" lang="tr"><Representation id="a"><BaseURL>a.m4a</BaseURL></Representation></AdaptationSet>
              <AdaptationSet contentType="audio" lang="tr"><Representation id="b"><BaseURL>b.m4a</BaseURL></Representation></AdaptationSet>
            </Period></MPD>
        """.trimIndent()
        assertThrows(RuntimeException::class.java) {
            DashAudioExport.plan(xml, base, tr, listOf(tr, AudioTrackIdentity("tr", null)))
        }
    }
}
