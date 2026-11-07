package com.kitsugi.animelist.ui.screens.manga.reader

import com.kitsugi.animelist.data.manga.ReadingMode

/** Platform-independent key policy. Android key codes are adapted in MangaReaderKeyHandler. */
enum class ReaderKey {
    Left, Right, Up, Down, PageUp, PageDown, Home, End, Space, Enter, Menu, Escape, Back,
    N, P, M, S, C, G, R, F, O, D, I, B, A, H, J, K, F1, Plus, Minus, Zero,
    LeftBracket, RightBracket, VolumeUp, VolumeDown, Other,
}

enum class ReaderAction(val repeatable: Boolean = false) {
    Forward(true), Backward(true), NextChapter, PreviousChapter, FirstPage, LastPage,
    ToggleMenu, Settings, Chapters, GoToPage, Retry, Fullscreen, Rotate, CycleMode, CycleFit,
    CycleBackground, ToggleAutoScroll, Help, ZoomIn(true), ZoomOut(true), ResetZoom, Back,
}

data class ReaderKeyStroke(
    val key: ReaderKey,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val repeatCount: Int = 0,
    val keyUp: Boolean = false,
)

/**
 * Okuyucuda alttan açılan paneller. Her panelin onu AÇAN bir kısayolu vardır;
 * panel açıkken aynı tuşa tekrar basmak paneli kapatır (aç/kapa).
 */
enum class ReaderPanel { Settings, Chapters, GoToPage, Shortcuts }

/** Bir paneli açan/kapatan kısayolun eylemi (kapatma aynı eylemle yapılır: host toggle eder). */
internal fun ReaderPanel.toggleAction(): ReaderAction = when (this) {
    ReaderPanel.Settings -> ReaderAction.Settings
    ReaderPanel.Chapters -> ReaderAction.Chapters
    ReaderPanel.GoToPage -> ReaderAction.GoToPage
    ReaderPanel.Shortcuts -> ReaderAction.Help
}

/** Tuş → açtığı panel eşlemesi (yoksa null). */
internal fun ReaderKey.panel(): ReaderPanel? = when (this) {
    ReaderKey.S -> ReaderPanel.Settings
    ReaderKey.C -> ReaderPanel.Chapters
    ReaderKey.G -> ReaderPanel.GoToPage
    ReaderKey.H, ReaderKey.F1 -> ReaderPanel.Shortcuts
    else -> null
}

data class ReaderKeyContext(
    val mode: ReadingMode,
    val overlayOpen: Boolean = false,
    val contentFocused: Boolean = true,
    val menuVisible: Boolean = false,
    val volumeKeys: Boolean = false,
    /** Açık panel (varsa). Yalnızca onu açan tuş paneli kapatabilir. */
    val openPanel: ReaderPanel? = null,
)

data class ReaderKeyResult(val consumed: Boolean = false, val action: ReaderAction? = null)

object MangaReaderShortcutPolicy {
    fun resolve(stroke: ReaderKeyStroke, context: ReaderKeyContext): ReaderKeyResult {
        val key = stroke.key
        if (stroke.alt || stroke.meta) return ReaderKeyResult()
        if (key == ReaderKey.Back || key == ReaderKey.Escape) return result(ReaderAction.Back, stroke)
        // ── Aç/kapa: bir panel açıkken YALNIZCA onu açan tuş onu kapatır ──────
        // Böylece "S"ye iki kez basmak ayarları açar ve kapatır; "C"/"G"/"H" için
        // de aynı davranış geçerli. Diğer tuşlar panele bırakılır.
        if (context.openPanel != null) {
            val panel = key.panel()
            return if (panel != null && panel == context.openPanel) {
                result(panel.toggleAction(), stroke)
            } else {
                ReaderKeyResult()
            }
        }
        // Never steal input from a dialog, page-number field, chip, slider or focused HUD button.
        if (context.overlayOpen) return ReaderKeyResult()
        if (!context.contentFocused) return ReaderKeyResult()
        if (stroke.ctrl) {
            val action = when (key) {
                ReaderKey.Right -> ReaderAction.NextChapter
                ReaderKey.Left -> ReaderAction.PreviousChapter
                else -> return ReaderKeyResult()
            }
            return result(action, stroke)
        }
        val rtl = context.mode == ReadingMode.RightToLeft
        val action = when (key) {
            ReaderKey.Left -> if (rtl) ReaderAction.Forward else ReaderAction.Backward
            ReaderKey.Right -> if (rtl) ReaderAction.Backward else ReaderAction.Forward
            ReaderKey.Down, ReaderKey.PageDown, ReaderKey.J -> ReaderAction.Forward
            ReaderKey.Up, ReaderKey.PageUp, ReaderKey.K -> ReaderAction.Backward
            ReaderKey.Space -> if (stroke.shift) ReaderAction.Backward else ReaderAction.Forward
            ReaderKey.Home -> ReaderAction.FirstPage
            ReaderKey.End -> ReaderAction.LastPage
            ReaderKey.N, ReaderKey.RightBracket -> ReaderAction.NextChapter
            ReaderKey.P, ReaderKey.LeftBracket -> ReaderAction.PreviousChapter
            ReaderKey.M, ReaderKey.Enter, ReaderKey.Menu -> ReaderAction.ToggleMenu
            ReaderKey.S -> ReaderAction.Settings
            ReaderKey.C -> ReaderAction.Chapters
            ReaderKey.G -> ReaderAction.GoToPage
            ReaderKey.R -> ReaderAction.Retry
            ReaderKey.F -> ReaderAction.Fullscreen
            ReaderKey.O -> ReaderAction.Rotate
            ReaderKey.D -> ReaderAction.CycleMode
            ReaderKey.I -> if (context.mode != ReadingMode.Webtoon) ReaderAction.CycleFit else return ReaderKeyResult()
            ReaderKey.B -> ReaderAction.CycleBackground
            ReaderKey.A -> if (context.mode == ReadingMode.Webtoon) ReaderAction.ToggleAutoScroll else return ReaderKeyResult()
            ReaderKey.H, ReaderKey.F1 -> ReaderAction.Help
            ReaderKey.Plus -> if (context.mode == ReadingMode.Webtoon) ReaderAction.ZoomIn else return ReaderKeyResult()
            ReaderKey.Minus -> if (context.mode == ReadingMode.Webtoon) ReaderAction.ZoomOut else return ReaderKeyResult()
            ReaderKey.Zero -> ReaderAction.ResetZoom
            ReaderKey.VolumeUp, ReaderKey.VolumeDown -> {
                if (!context.volumeKeys || context.menuVisible) return ReaderKeyResult()
                if (key == ReaderKey.VolumeDown) ReaderAction.Forward else ReaderAction.Backward
            }
            else -> return ReaderKeyResult()
        }
        return result(action, stroke)
    }

    private fun result(action: ReaderAction, stroke: ReaderKeyStroke): ReaderKeyResult = ReaderKeyResult(
        consumed = true,
        action = action.takeUnless { stroke.keyUp || (stroke.repeatCount > 0 && !it.repeatable) },
    )
}
