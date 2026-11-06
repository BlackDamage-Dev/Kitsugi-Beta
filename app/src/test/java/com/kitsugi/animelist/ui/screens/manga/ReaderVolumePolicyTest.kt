package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.ui.screens.manga.components.volumeKeyIsNext
import org.junit.Assert.*
import org.junit.Test

class ReaderVolumePolicyTest {
    @Test fun normalAndReversedDirections() {
        assertTrue(volumeKeyIsNext(true, false))
        assertFalse(volumeKeyIsNext(false, false))
        assertFalse(volumeKeyIsNext(true, true))
        assertTrue(volumeKeyIsNext(false, true))
    }
}
