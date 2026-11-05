package com.kitsugi.animelist.ui.screens.fullscreen.controls

import android.view.KeyEvent

/** Oynatıcının donanım klavyesinden tetiklenebilen bütün eylemleri. */
enum class PlayerKeyAction {
    TogglePlayPause,
    Play,
    Pause,
    SeekBackward,
    SeekForward,
    SeekBackwardLarge,
    SeekForwardLarge,
    PreviousEpisode,
    NextEpisode,
    VolumeDown,
    VolumeUp,
    BrightnessDown,
    BrightnessUp,
    ToggleMute,
    SpeedDown,
    SpeedUp,
    SpeedReset,
    PlaybackSpeed,
    SubtitleTracks,
    ToggleSubtitles,
    AudioTracks,
    CycleAudioTrack,
    QualityTracks,
    Chapters,
    Screenshot,
    VideoFilters,
    More,
    EpisodeList,
    CycleAspectRatio,
    RotateScreen,
    ToggleControlsLock,
    ToggleControls,
    PictureInPicture,
    ShowKeyboardShortcuts,
}

/** Android KeyEvent'e bağımlı olmayan, birim testlerinde de kullanılabilen tuş bilgisi. */
data class PlayerKeyStroke(
    val keyCode: Int,
    val isShiftPressed: Boolean = false,
    val isCtrlPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
    val unicodeChar: Int = 0,
)

object PlayerKeyboardShortcuts {
    fun actionFor(event: KeyEvent): PlayerKeyAction? = actionFor(
        PlayerKeyStroke(
            keyCode = event.keyCode,
            isShiftPressed = event.isShiftPressed,
            isCtrlPressed = event.isCtrlPressed,
            isAltPressed = event.isAltPressed,
            isMetaPressed = event.isMetaPressed,
            unicodeChar = event.unicodeChar,
        )
    )

    fun actionFor(stroke: PlayerKeyStroke): PlayerKeyAction? {
        // Alt/Meta kısayolları sisteme veya kullanıcının MPV yapılandırmasına bırakılır.
        if (stroke.isAltPressed || stroke.isMetaPressed) return null
        // Fiziksel tuş yerine üretilen karaktere de bak: Türkçe ve diğer klavye
        // düzenlerinde soru işareti her zaman Shift+/ konumunda olmayabilir.
        if (stroke.unicodeChar == '?'.code) return PlayerKeyAction.ShowKeyboardShortcuts

        return when (stroke.keyCode) {
            KeyEvent.KEYCODE_BRIGHTNESS_UP -> PlayerKeyAction.BrightnessUp
            KeyEvent.KEYCODE_BRIGHTNESS_DOWN -> PlayerKeyAction.BrightnessDown
            KeyEvent.KEYCODE_DPAD_UP -> when {
                stroke.isCtrlPressed -> PlayerKeyAction.BrightnessUp
                else -> PlayerKeyAction.VolumeUp
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> when {
                stroke.isCtrlPressed -> PlayerKeyAction.BrightnessDown
                else -> PlayerKeyAction.VolumeDown
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> PlayerKeyAction.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> PlayerKeyAction.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_M -> PlayerKeyAction.ToggleMute

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_J ->
                if (stroke.isShiftPressed) PlayerKeyAction.SeekBackwardLarge else PlayerKeyAction.SeekBackward
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_L ->
                if (stroke.isShiftPressed) PlayerKeyAction.SeekForwardLarge else PlayerKeyAction.SeekForward
            KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerKeyAction.SeekBackwardLarge
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerKeyAction.SeekForwardLarge

            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerKeyAction.TogglePlayPause
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerKeyAction.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> PlayerKeyAction.Pause
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_N -> PlayerKeyAction.NextEpisode
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_P -> PlayerKeyAction.PreviousEpisode

            KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> PlayerKeyAction.SpeedDown
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD -> PlayerKeyAction.SpeedUp
            KeyEvent.KEYCODE_BACKSLASH -> PlayerKeyAction.SpeedReset
            KeyEvent.KEYCODE_U -> PlayerKeyAction.PlaybackSpeed

            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_CAPTIONS -> PlayerKeyAction.SubtitleTracks
            KeyEvent.KEYCODE_D -> PlayerKeyAction.ToggleSubtitles
            KeyEvent.KEYCODE_A -> PlayerKeyAction.AudioTracks
            KeyEvent.KEYCODE_POUND -> PlayerKeyAction.CycleAudioTrack
            KeyEvent.KEYCODE_Q -> PlayerKeyAction.QualityTracks
            KeyEvent.KEYCODE_C -> PlayerKeyAction.Chapters
            KeyEvent.KEYCODE_O -> PlayerKeyAction.Screenshot
            KeyEvent.KEYCODE_G -> PlayerKeyAction.VideoFilters
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> PlayerKeyAction.More
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_GUIDE -> PlayerKeyAction.EpisodeList

            KeyEvent.KEYCODE_Z -> PlayerKeyAction.CycleAspectRatio
            KeyEvent.KEYCODE_R -> PlayerKeyAction.RotateScreen
            KeyEvent.KEYCODE_X -> PlayerKeyAction.ToggleControlsLock
            KeyEvent.KEYCODE_TAB -> PlayerKeyAction.ToggleControls
            KeyEvent.KEYCODE_F -> PlayerKeyAction.PictureInPicture
            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_F1 -> PlayerKeyAction.ShowKeyboardShortcuts
            // ABD ve Türkçe klavyelerde soru işareti genellikle Shift+/ ile üretilir.
            KeyEvent.KEYCODE_SLASH -> if (stroke.isShiftPressed) PlayerKeyAction.ShowKeyboardShortcuts else null
            else -> null
        }
    }

    /** Basılı tutulduğunda tekrar etmesi güvenli ve beklenen eylemler. */
    fun isRepeatable(action: PlayerKeyAction): Boolean = when (action) {
        PlayerKeyAction.SeekBackward,
        PlayerKeyAction.SeekForward,
        PlayerKeyAction.SeekBackwardLarge,
        PlayerKeyAction.SeekForwardLarge,
        PlayerKeyAction.VolumeDown,
        PlayerKeyAction.VolumeUp,
        PlayerKeyAction.BrightnessDown,
        PlayerKeyAction.BrightnessUp,
        PlayerKeyAction.SpeedDown,
        PlayerKeyAction.SpeedUp -> true
        else -> false
    }
}
