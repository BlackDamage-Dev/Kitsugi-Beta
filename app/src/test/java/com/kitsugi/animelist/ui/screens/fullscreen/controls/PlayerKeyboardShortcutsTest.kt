package com.kitsugi.animelist.ui.screens.fullscreen.controls

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeyboardShortcutsTest {

    @Test
    fun `volume brightness and mute have distinct mappings`() {
        assertEquals(PlayerKeyAction.VolumeUp, action(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PlayerKeyAction.VolumeDown, action(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(PlayerKeyAction.VolumeUp, action(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(PlayerKeyAction.VolumeDown, action(KeyEvent.KEYCODE_9))
        assertEquals(PlayerKeyAction.VolumeUp, action(KeyEvent.KEYCODE_0))
        assertEquals(PlayerKeyAction.ToggleMute, action(KeyEvent.KEYCODE_M))
        assertEquals(PlayerKeyAction.BrightnessUp, action(KeyEvent.KEYCODE_DPAD_UP, ctrl = true))
        assertEquals(PlayerKeyAction.BrightnessDown, action(KeyEvent.KEYCODE_DPAD_DOWN, ctrl = true))
        assertEquals(PlayerKeyAction.BrightnessUp, action(KeyEvent.KEYCODE_BRIGHTNESS_UP))
    }

    @Test
    fun `shift selects large seek without changing letter aliases`() {
        assertEquals(PlayerKeyAction.SeekBackward, action(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(PlayerKeyAction.SeekBackwardLarge, action(KeyEvent.KEYCODE_DPAD_LEFT, shift = true))
        assertEquals(PlayerKeyAction.SeekForward, action(KeyEvent.KEYCODE_L))
        assertEquals(PlayerKeyAction.SeekForwardLarge, action(KeyEvent.KEYCODE_L, shift = true))
    }

    @Test
    fun `player panels and help are mapped`() {
        assertEquals(PlayerKeyAction.SubtitleTracks, action(KeyEvent.KEYCODE_S))
        assertEquals(PlayerKeyAction.AudioTracks, action(KeyEvent.KEYCODE_A))
        assertEquals(PlayerKeyAction.CycleAudioTrack, action(KeyEvent.KEYCODE_POUND))
        assertEquals(PlayerKeyAction.Screenshot, action(KeyEvent.KEYCODE_O))
        assertEquals(PlayerKeyAction.VideoFilters, action(KeyEvent.KEYCODE_G))
        assertEquals(PlayerKeyAction.ShowKeyboardShortcuts, action(KeyEvent.KEYCODE_H))
        assertEquals(PlayerKeyAction.ShowKeyboardShortcuts, action(KeyEvent.KEYCODE_SLASH, shift = true))
        assertEquals(
            PlayerKeyAction.ShowKeyboardShortcuts,
            PlayerKeyboardShortcuts.actionFor(
                PlayerKeyStroke(
                    keyCode = KeyEvent.KEYCODE_7,
                    isShiftPressed = true,
                    unicodeChar = '?'.code,
                )
            )
        )
        assertNull(action(KeyEvent.KEYCODE_SLASH))
    }

    @Test
    fun `system modifier shortcuts stay untouched`() {
        assertNull(action(KeyEvent.KEYCODE_A, alt = true))
        assertNull(action(KeyEvent.KEYCODE_F, meta = true))
    }

    @Test
    fun `only continuous controls repeat while key is held`() {
        assertTrue(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.VolumeUp))
        assertTrue(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.BrightnessDown))
        assertTrue(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.SeekForward))
        assertFalse(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.ToggleMute))
        assertFalse(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.TogglePlayPause))
        assertFalse(PlayerKeyboardShortcuts.isRepeatable(PlayerKeyAction.ShowKeyboardShortcuts))
    }

    @Test
    fun `space k and enter map to play pause while arrows map to seek and volume`() {
        assertEquals(PlayerKeyAction.TogglePlayPause, action(KeyEvent.KEYCODE_SPACE))
        assertEquals(PlayerKeyAction.TogglePlayPause, action(KeyEvent.KEYCODE_K))
        assertEquals(PlayerKeyAction.TogglePlayPause, action(KeyEvent.KEYCODE_ENTER))
        assertEquals(PlayerKeyAction.TogglePlayPause, action(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(PlayerKeyAction.SeekBackward, action(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(PlayerKeyAction.SeekForward, action(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(PlayerKeyAction.VolumeUp, action(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PlayerKeyAction.VolumeDown, action(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    private fun action(
        keyCode: Int,
        shift: Boolean = false,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): PlayerKeyAction? = PlayerKeyboardShortcuts.actionFor(
        PlayerKeyStroke(
            keyCode = keyCode,
            isShiftPressed = shift,
            isCtrlPressed = ctrl,
            isAltPressed = alt,
            isMetaPressed = meta,
        )
    )
}
