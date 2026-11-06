package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Restrict the wake setting to the reader and restore the view's previous state. */
@Composable
fun ReaderKeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        if (enabled) view.keepScreenOn = true
        onDispose { if (enabled) view.keepScreenOn = previous }
    }
}
