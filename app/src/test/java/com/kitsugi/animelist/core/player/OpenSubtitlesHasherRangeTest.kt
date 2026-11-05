package com.kitsugi.animelist.core.player

import org.junit.Assert.*
import org.junit.Test

class OpenSubtitlesHasherRangeTest {
    @Test fun requiresExactPartialResponse() {
        assertTrue(OpenSubtitlesHasher.validRange(206, "bytes 0-65535/200000", 0, 65535, 200000))
        assertFalse(OpenSubtitlesHasher.validRange(200, null, 0, 65535, 200000))
        assertFalse(OpenSubtitlesHasher.validRange(206, "bytes 1-65536/200000", 0, 65535, 200000))
    }
}
