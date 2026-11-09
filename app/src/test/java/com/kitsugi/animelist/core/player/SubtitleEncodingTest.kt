package com.kitsugi.animelist.core.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class SubtitleEncodingTest {
    private val sample = "1\n00:00:01,000 --> 00:00:02,000\nTürkçe: yaş, ışık, çığ, ölü, şu.\n"
    @Test fun convertsWindows1254TurkishAndHonorsMislabelledUtf8() {
        val bytes = sample.toByteArray(Charset.forName("windows-1254"))
        assertEquals(sample, SubtitleEncoding.toUtf8(bytes, "text/plain; charset=utf-8", "tur").toString(Charsets.UTF_8))
        assertEquals(sample, SubtitleEncoding.toUtf8(bytes, "text/plain; charset=iso-8859-1", "Türkçe").toString(Charsets.UTF_8))
    }
    @Test fun leavesUtf8IntactAndRespectsDeclaredCyrillic() {
        val bytes = sample.toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, SubtitleEncoding.toUtf8(bytes, "text/plain; charset=iso-8859-9", "tr"))
        val russian = "Привет мир"
        assertEquals(russian, SubtitleEncoding.toUtf8(russian.toByteArray(Charset.forName("windows-1251")), null, "ru").toString(Charsets.UTF_8))
    }
    @Test fun handlesUtf16BomAndPreservesBinary() {
        assertEquals(sample, SubtitleEncoding.toUtf8(sample.toByteArray(Charsets.UTF_16), null, "tr").toString(Charsets.UTF_8))
        val binary = byteArrayOf(0, 1, 0xff.toByte())
        assertArrayEquals(binary, SubtitleEncoding.toUtf8(binary, null, "tr"))
    }
}
