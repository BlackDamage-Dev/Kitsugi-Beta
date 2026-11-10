package com.kitsugi.animelist.ui.screens.manga.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun Context.readerActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/** Restore host window/orientation when leaving; don't leak reader preferences to the app. */
@Composable
internal fun MangaReaderWindowEffects(fullscreen: Boolean, menuVisible: Boolean, keepScreenOn: Boolean) {
    val view = LocalView.current
    val activity = view.context.readerActivity()
    DisposableEffect(view, activity) {
        val originalKeepAwake = view.keepScreenOn
        val originalOrientation = activity?.requestedOrientation
        val originallyVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
        onDispose {
            view.keepScreenOn = originalKeepAwake
            if (activity != null) {
                originalOrientation?.let { activity.requestedOrientation = it }
                val controller = WindowCompat.getInsetsController(activity.window, view)
                // Okuyucudan her çıkışta (geri tuşu, navigasyon, kapanış) sistem çubukları
                // her koşulda geri getirilir — tam ekranda takılı kalma bug'ı engellendi.
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(view, keepScreenOn) { view.keepScreenOn = keepScreenOn }
    LaunchedEffect(view, activity, fullscreen, menuVisible) {
        activity?.let {
            val controller = WindowCompat.getInsetsController(it.window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen && !menuVisible) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
