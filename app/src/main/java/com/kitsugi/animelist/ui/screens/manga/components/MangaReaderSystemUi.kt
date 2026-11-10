package com.kitsugi.animelist.ui.screens.manga.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Manga okuyucusu için **gerçek** tam ekran (immersive) yönetimi.
 *
 * Önceki durum: alt bardaki "Sığdırma Modu" ikonu tam ekran sanılıyordu ama
 * yalnızca `ContentScale` değiştiriyordu; sistem çubukları (durum çubuğu,
 * gezinme çubuğu, tablet görev çubuğu) hiç gizlenmiyordu. Bu yüzden "tam ekran
 * butonuna basıyorum, üst/alt bar gizlenmiyor" şikayeti oluşuyordu.
 *
 * Bu composable:
 *  • `WindowInsetsControllerCompat.hide(systemBars())` ile durum + gezinme +
 *    görev çubuklarını gizler,
 *  • `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` sayesinde kenardan kaydırınca
 *    çubuklar geçici olarak görünür (kalıcı olarak açılmaz),
 *  • okuyucudan çıkıldığında (`onDispose`) çubukları geri getirir — yani
 *    uygulamanın başka ekranlarında tam ekran "yapışıp kalmaz",
 *  • diyalog/kaydırma sonrası çubuklar geri gelirse tekrar gizler,
 *  • istenirse okuma boyunca ekranı açık tutar (`FLAG_KEEP_SCREEN_ON`).
 *
 * @param enabled true → tam ekran (çubuklar gizli), false → normal mod
 * @param keepScreenOn okuma sırasında ekran kapanmasın
 * @param reassertKey değeri değiştiğinde (ör. menü/diyalog kapanınca) gizleme
 *        yeniden uygulanır
 */
@Composable
fun MangaReaderSystemUi(
    enabled: Boolean,
    keepScreenOn: Boolean,
    reassertKey: Any? = Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val window = remember(context) { context.findActivityOrNull()?.window }

    val controller = remember(window, view) {
        window?.let { WindowInsetsControllerCompat(it, view) }
    }

    // ── Gizle / göster + çıkışta geri yükle ─────────────────────────────────
    DisposableEffect(enabled, controller) {
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // Okuyucudan çıkılıyor: tam ekranı MUTLAKA bırak.
            if (enabled) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    // ── Diyalog / pencere odağı sonrası tekrar gizle ────────────────────────
    LaunchedEffect(enabled, reassertKey) {
        if (enabled) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(enabled, lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_RESUME) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Ekranı açık tut ─────────────────────────────────────────────────────
    DisposableEffect(keepScreenOn, window) {
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

/**
 * Context sarmalayıcıları arasından [Activity] bulur.
 *
 * Not: Aynı yardımcı daha önce `MangaReaderScreen.kt` ve
 * `KitsugiPlayerViewModel.kt` içinde ayrı ayrı (kopyala-yapıştır) duruyordu;
 * okuyucu tarafı artık tek bu tanımı kullanıyor.
 */
internal fun Context.findActivityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
