package com.kitsugi.animelist.ui.screens.manga.components

import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import com.kitsugi.animelist.MainActivity

/** Optional reader-only volume interception. The activity's normal key handling remains the fallback. */
@Composable
internal fun ReaderVolumeKeys(enabled: Boolean, inverted: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val context = LocalContext.current
    var target: Context = context
    while (target is ContextWrapper && target !is MainActivity) {
        val next = target.baseContext
        if (next === target) break
        target = next
    }
    val activity = target as? MainActivity
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    DisposableEffect(activity, dialogWindow, enabled, inverted) {
        val handler: (KeyEvent) -> Boolean = { event ->
            if (!enabled || activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true ||
                (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN)) {
                false
            } else {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val goNext = volumeKeyIsNext(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN, inverted)
                    if (goNext) next() else previous()
                }
                event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
            }
        }
        val previousCallback = if (enabled) dialogWindow?.callback else null
        val callback = previousCallback?.let { original ->
            object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean = handler(event) || original.dispatchKeyEvent(event)
            }
        }
        if (callback != null) dialogWindow?.callback = callback
        else if (enabled) activity?.readerVolumeKeyHandler = handler
        onDispose {
            if (callback != null && dialogWindow?.callback === callback) dialogWindow?.callback = previousCallback
            if (activity != null && activity.readerVolumeKeyHandler === handler) activity.readerVolumeKeyHandler = null
        }
    }
}

