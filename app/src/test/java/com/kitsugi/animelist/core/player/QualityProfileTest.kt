package com.kitsugi.animelist.core.player

import org.junit.Assert.*
import org.junit.Test

/**
 * T4.1 – QualityProfileTest
 *
 * QualityProfile + QualityDataHelper mantığını ayrıntılı test eder.
 */
class QualityProfileTest {

    // ── Serialize / Deserialize ───────────────────────────────────────────────

    @Test
    fun `QualityProfile serialize produces pipe-separated string`() {
        val profile = QualityProfile(QualityPreference.P720, 4000)
        val result = QualityProfile.serialize(profile)
        assertEquals("P720|4000", result)
    }

    @Test
    fun `QualityProfile deserialize P1080 with maxBitrate`() {
        val result = QualityProfile.deserialize("P1080|8000")
        assertEquals(QualityPreference.P1080, result.preference)
        assertEquals(8000, result.maxBitrateKbps)
    }

    @Test
    fun `QualityProfile deserialize DATA_SAVER`() {
        val result = QualityProfile.deserialize("DATA_SAVER|-1")
        assertEquals(QualityPreference.DATA_SAVER, result.preference)
        assertEquals(-1, result.maxBitrateKbps)
    }

    @Test
    fun `QualityProfile deserialize unknown preference falls back to AUTO`() {
        val result = QualityProfile.deserialize("UNKNOWN|2000")
        assertEquals(QualityPreference.AUTO, result.preference)
    }

    @Test
    fun `QualityProfile deserialize no bitrate part uses -1`() {
        val result = QualityProfile.deserialize("P480")
        assertEquals(QualityPreference.P480, result.preference)
        assertEquals(-1, result.maxBitrateKbps)
    }

    // ── QualityPreference range invariants ────────────────────────────────────

    @Test
    fun `QualityPreference AUTO covers full range`() {
        val auto = QualityPreference.AUTO
        assertEquals(-1, auto.minQualityValue)
        assertEquals(Int.MAX_VALUE, auto.maxQualityValue)
    }

    @Test
    fun `QualityPreference P1080 lower bound is 1080`() {
        assertEquals(1080, QualityPreference.P1080.minQualityValue)
    }

    @Test
    fun `QualityPreference bands do not overlap`() {
        // P480 max < P720 min
        assertTrue(QualityPreference.P480.maxQualityValue < QualityPreference.P720.minQualityValue)
        // P720 max < P1080 min
        assertTrue(QualityPreference.P720.maxQualityValue < QualityPreference.P1080.minQualityValue)
    }

    // ── QualityDataHelper.filterByBitrate ─────────────────────────────────────

    @Test
    fun `filterByBitrate with -1 maxBitrate returns all sources`() {
        val sources = makeSources(listOf(480, 720, 1080))
        val result = QualityDataHelper.filterByBitrate(sources, -1)
        assertEquals(3, result.size)
    }

    @Test
    fun `filterByBitrate uses actual bitrate metadata and preserves unknown values`() {
        val sources = listOf(
            source("known-under", quality = 1080, bitrateKbps = 4_500),
            source("known-over", quality = 480, bitrateKbps = 5_500),
            source("unknown-1080p", quality = 1080, bitrateKbps = null)
        )

        val result = QualityDataHelper.filterByBitrate(sources, 5_000)
        assertEquals(listOf("known-under", "unknown-1080p"), result.map { it.title })
    }

    @Test
    fun `filterByBitrate does not mistake resolution height for bitrate`() {
        val sources = makeSources(listOf(480, 720, 1080))
        val result = QualityDataHelper.filterByBitrate(sources, 1_000)

        // Resolution metadata alone is insufficient to decide whether a source exceeds a bitrate cap.
        assertEquals(listOf(480, 720, 1080), result.mapNotNull { it.qualityValue })
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun makeSources(qualities: List<Int>): List<com.kitsugi.animelist.data.repository.StreamSource> =
        qualities.map { q -> source("${q}p", quality = q) }

    private fun source(
        title: String,
        quality: Int?,
        bitrateKbps: Int? = null
    ) = com.kitsugi.animelist.data.repository.StreamSource(
        addonName = "Test",
        name = title,
        title = title,
        url = "https://cdn.example.com/$title.m3u8",
        infoHash = null,
        fileIndex = null,
        qualityValue = quality,
        bitrateKbps = bitrateKbps
    )


    // ── Oynatıcıya uygulanan çözünürlük tavanı ───────────────────────────────

    @Test
    fun `preference maps to a resolution cap for the player`() {
        assertEquals(null, QualityPreference.AUTO.maxVideoHeight)
        assertEquals(null, QualityPreference.P1080.maxVideoHeight)
        assertEquals(720, QualityPreference.P720.maxVideoHeight)
        assertEquals(480, QualityPreference.P480.maxVideoHeight)
        assertEquals(360, QualityPreference.DATA_SAVER.maxVideoHeight)
    }

    @Test
    fun `profile exposes effective limits only when set`() {
        assertEquals(null, QualityProfile.DEFAULT.effectiveMaxVideoHeight)
        assertEquals(null, QualityProfile.DEFAULT.effectiveMaxBitrateKbps)

        val strict = QualityProfile(QualityPreference.P720, 2500)
        assertEquals(720, strict.effectiveMaxVideoHeight)
        assertEquals(2500, strict.effectiveMaxBitrateKbps)

        // 0 / negatif bit hızı "sınırsız" sayılır.
        assertEquals(null, QualityProfile(QualityPreference.P480, 0).effectiveMaxBitrateKbps)
    }
}
