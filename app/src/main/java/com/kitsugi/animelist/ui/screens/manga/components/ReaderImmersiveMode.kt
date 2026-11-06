package com.kitsugi.animelist.ui.screens.manga.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Apply only to the reader's window, then restore its prior visibility and behavior. */
@Composable
internal fun ReaderImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    var context: Context = LocalContext.current
    while (context is ContextWrapper && context !is Activity) {
        val next = context.baseContext
        if (next === context) break
        context = next
    }
    val window = (view.parent as? DialogWindowProvider)?.window ?: (context as? Activity)?.window
    val initialInsets = key(view, enabled) {
        val result by produceState<WindowInsetsCompat?>(null, view, enabled) {
            if (enabled) while (isActive) {
                val current = ViewCompat.getRootWindowInsets(view)
                if (current != null) { value = current; break }
                withFrameNanos { }
            }
        }
        result
    }
    DisposableEffect(view, window, enabled, initialInsets) {
        val insets = initialInsets
        if (enabled && window != null && insets != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            val oldBehavior = controller.systemBarsBehavior
            val statusVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navigationVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            onDispose {
                controller.systemBarsBehavior = oldBehavior
                if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        } else onDispose { }
    }
}
