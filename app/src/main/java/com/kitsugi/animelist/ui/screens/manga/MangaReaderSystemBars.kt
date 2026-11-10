package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Fullscreen is independent of reader controls; edge swipes reveal transient bars. */
@Composable
internal fun MangaReaderSystemBars(fullscreen: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val enabled by androidx.compose.runtime.rememberUpdatedState(fullscreen)
    DisposableEffect(activity, lifecycleOwner) {
        val window = activity.window
        val decor = window.decorView
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, decor)
        val originalBehavior = controller.systemBarsBehavior
        val originalOrientation = activity.requestedOrientation
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
        val statusVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) ?: true
        val navVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()) ?: true
        fun applyMode() {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) applyMode()
        }
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) applyMode()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        decor.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        applyMode()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            controller.systemBarsBehavior = originalBehavior
            if (statusVisible) controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            else controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            if (navVisible) controller.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            else controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            activity.requestedOrientation = originalOrientation
        }
    }
    LaunchedEffect(fullscreen) {
        val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

