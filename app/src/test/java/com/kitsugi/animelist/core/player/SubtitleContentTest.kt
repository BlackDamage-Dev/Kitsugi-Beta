package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleContentTest {
    @Test fun rejectsErrorPagesAndArchives() {
        assertFalse(SubtitleContent.isUsable(byteArrayOf()))
        assertFalse(SubtitleContent.isUsable("  <!DOCTYPE html><html>error</html>".toByteArray()))
        assertFalse(SubtitleContent.isUsable("{\"error\":\"not found\"}".toByteArray()))
        assertFalse(SubtitleContent.isUsable(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        assertFalse(SubtitleContent.isUsable("Rar!".toByteArray()))
    }
    @Test fun acceptsCommonTextFormats() {
        assertTrue(SubtitleContent.isUsable("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello".toByteArray()))
        assertTrue(SubtitleContent.isUsable("1\n00:00:01,000 --> 00:00:02,000\nMerhaba".toByteArray()))
        assertTrue(SubtitleContent.isUsable("[Script Info]\nTitle: Test".toByteArray()))
    }
}
