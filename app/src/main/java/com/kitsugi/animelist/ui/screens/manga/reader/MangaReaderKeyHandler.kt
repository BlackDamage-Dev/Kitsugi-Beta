package com.kitsugi.animelist.ui.screens.manga.reader

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/** Shared mobile/desktop-keyboard/TV adapter. Shortcuts fire once on down; up is consumed only. */
fun handleMangaReaderKey(
    event: KeyEvent,
    context: ReaderKeyContext,
    onAction: (ReaderAction, Boolean) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return false
    val native = event.nativeKeyEvent
    val key = when (native.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ReaderKey.Left
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ReaderKey.Right
        AndroidKeyEvent.KEYCODE_DPAD_UP -> ReaderKey.Up
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ReaderKey.Down
        AndroidKeyEvent.KEYCODE_PAGE_UP -> ReaderKey.PageUp
        AndroidKeyEvent.KEYCODE_PAGE_DOWN -> ReaderKey.PageDown
        AndroidKeyEvent.KEYCODE_MOVE_HOME -> ReaderKey.Home
        AndroidKeyEvent.KEYCODE_MOVE_END -> ReaderKey.End
        AndroidKeyEvent.KEYCODE_SPACE -> ReaderKey.Space
        AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_DPAD_CENTER -> ReaderKey.Enter
        AndroidKeyEvent.KEYCODE_MENU -> ReaderKey.Menu
        AndroidKeyEvent.KEYCODE_ESCAPE -> ReaderKey.Escape
        AndroidKeyEvent.KEYCODE_BACK -> ReaderKey.Back
        AndroidKeyEvent.KEYCODE_N -> ReaderKey.N
        AndroidKeyEvent.KEYCODE_P -> ReaderKey.P
        AndroidKeyEvent.KEYCODE_M -> ReaderKey.M
        AndroidKeyEvent.KEYCODE_S -> ReaderKey.S
        AndroidKeyEvent.KEYCODE_C -> ReaderKey.C
        AndroidKeyEvent.KEYCODE_G -> ReaderKey.G
        AndroidKeyEvent.KEYCODE_R -> ReaderKey.R
        AndroidKeyEvent.KEYCODE_F -> ReaderKey.F
        AndroidKeyEvent.KEYCODE_O -> ReaderKey.O
        AndroidKeyEvent.KEYCODE_D -> ReaderKey.D
        AndroidKeyEvent.KEYCODE_I -> ReaderKey.I
        AndroidKeyEvent.KEYCODE_B -> ReaderKey.B
        AndroidKeyEvent.KEYCODE_A -> ReaderKey.A
        AndroidKeyEvent.KEYCODE_H, AndroidKeyEvent.KEYCODE_SLASH -> ReaderKey.H
        AndroidKeyEvent.KEYCODE_J -> ReaderKey.J
        AndroidKeyEvent.KEYCODE_K -> ReaderKey.K
        AndroidKeyEvent.KEYCODE_F1 -> ReaderKey.F1
        AndroidKeyEvent.KEYCODE_PLUS, AndroidKeyEvent.KEYCODE_EQUALS, AndroidKeyEvent.KEYCODE_NUMPAD_ADD -> ReaderKey.Plus
        AndroidKeyEvent.KEYCODE_MINUS, AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT -> ReaderKey.Minus
        AndroidKeyEvent.KEYCODE_0, AndroidKeyEvent.KEYCODE_NUMPAD_0 -> ReaderKey.Zero
        AndroidKeyEvent.KEYCODE_LEFT_BRACKET -> ReaderKey.LeftBracket
        AndroidKeyEvent.KEYCODE_RIGHT_BRACKET -> ReaderKey.RightBracket
        AndroidKeyEvent.KEYCODE_VOLUME_UP -> ReaderKey.VolumeUp
        AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> ReaderKey.VolumeDown
        else -> ReaderKey.Other
    }
    val result = MangaReaderShortcutPolicy.resolve(ReaderKeyStroke(
        key, native.isShiftPressed, native.isCtrlPressed, native.isAltPressed, native.isMetaPressed,
        native.repeatCount, event.type == KeyEventType.KeyUp,
    ), context)
    result.action?.let { onAction(it, native.repeatCount > 0) }
    return result.consumed
}
