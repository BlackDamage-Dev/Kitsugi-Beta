package com.kitsugi.animelist.ui.screens.manga

import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt

/** Login is interactive; retry only after the user closes the dialog. */
@Composable
fun MangaTrLoginDialog(onDismiss: () -> Unit) {
    KitsugiWebViewDialog(
        title = "Manga-TR üye girişi",
        url = "https://forum.manga-tr.com/giris.php",
        onDismiss = {
            CookieManager.getInstance().flush()
            runCatching {
                val jar = Injekt.get(NetworkHelper::class.java).cookieJar
                // CookieManager only exposes cookies visible to each URL. Do not copy
                // host-only forum cookies to the main site.
                jar.syncFromWebView("https://forum.manga-tr.com/")
                jar.syncFromWebView("https://manga-tr.com/")
            }
            onDismiss()
        },
    )
}
