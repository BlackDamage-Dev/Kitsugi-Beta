package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HDR10 statik metadata SEI taraması.
 *
 * DV5 korumasının çekirdeği: RPU ancak taban katman gerçek HDR10 statik
 * metadatası (Mastering Display = payload type 137, Content Light Level = 144)
 * taşıyorsa sıyrılır. Bu testler sentetik NAL'lerle taramayı doğrular.
 */
class HevcDvRpuStripperSeiTest {

    private fun nal(type: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(((type shl 1) and 0x7E).toByte(), 0x01)
        return header + payload
    }

    /** Prefix SEI NAL: payloadType, payloadSize, payload, rbsp trailing bits. */
    private fun sei(payloadType: Int, payloadSize: Int): ByteArray {
        val payload = ByteArray(payloadSize) { 0x11 }
        return nal(
            39,
            byteArrayOf(payloadType.toByte(), payloadSize.toByte()) + payload + byteArrayOf(0x80.toByte())
        )
    }

    private fun lengthDelimited(vararg nals: ByteArray): Pair<ByteArray, Int> {
        var total = 0
        nals.forEach { total += 4 + it.size }
        val out = ByteArray(total)
        var pos = 0
        nals.forEach { n ->
            out[pos] = ((n.size shr 24) and 0xFF).toByte()
            out[pos + 1] = ((n.size shr 16) and 0xFF).toByte()
            out[pos + 2] = ((n.size shr 8) and 0xFF).toByte()
            out[pos + 3] = (n.size and 0xFF).toByte()
            System.arraycopy(n, 0, out, pos + 4, n.size)
            pos += 4 + n.size
        }
        return out to total
    }

    private fun annexB(vararg nals: ByteArray): Pair<ByteArray, Int> {
        var total = 0
        nals.forEach { total += 4 + it.size }
        val out = ByteArray(total)
        var pos = 0
        nals.forEach { n ->
            out[pos] = 0
            out[pos + 1] = 0
            out[pos + 2] = 0
            out[pos + 3] = 1
            System.arraycopy(n, 0, out, pos + 4, n.size)
            pos += 4 + n.size
        }
        return out to total
    }

    @Test
    fun `mastering display sei is detected in length delimited samples`() {
        val (sample, len) = lengthDelimited(nal(19, ByteArray(16)), sei(137, 24))
        assertTrue(HevcDvRpuStripper.containsHdr10StaticMetadataSei(sample, len, 4))
    }

    @Test
    fun `content light level sei is detected in length delimited samples`() {
        val (sample, len) = lengthDelimited(nal(19, ByteArray(8)), sei(144, 4))
        assertTrue(HevcDvRpuStripper.containsHdr10StaticMetadataSei(sample, len, 4))
    }

    @Test
    fun `hdr10plus only sample is not static metadata`() {
        // HDR10+ SEI = payload type 4 (user_data_registered_itu_t_t35).
        val (sample, len) = lengthDelimited(nal(19, ByteArray(16)), sei(4, 12))
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSei(sample, len, 4))
    }

    @Test
    fun `sample without sei is not static metadata`() {
        val (sample, len) = lengthDelimited(nal(19, ByteArray(16)), nal(1, ByteArray(32)))
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSei(sample, len, 4))
    }

    @Test
    fun `mastering display sei is detected in annex b samples`() {
        val (sample, len) = annexB(nal(19, ByteArray(16)), sei(137, 24))
        assertTrue(HevcDvRpuStripper.containsHdr10StaticMetadataSeiAnnexB(sample, len))
    }

    @Test
    fun `annex b without static metadata is rejected`() {
        val (sample, len) = annexB(nal(19, ByteArray(16)), sei(4, 12))
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSeiAnnexB(sample, len))
    }

    @Test
    fun `malformed length fields are ignored`() {
        val bad = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F, 0x01, 0x02)
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSei(bad, bad.size, 4))
        // Geçersiz uzunluk alanı genişliği hiçbir şeyi okumaz.
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSei(bad, bad.size, 5))
    }

    @Test
    fun `empty samples are rejected`() {
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSei(ByteArray(0), 0, 4))
        assertFalse(HevcDvRpuStripper.containsHdr10StaticMetadataSeiAnnexB(ByteArray(0), 0))
    }
}
