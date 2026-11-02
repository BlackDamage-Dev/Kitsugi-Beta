package com.kitsugi.animelist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.data.local.TranslationManager
import kotlinx.coroutines.CancellationException

/**
 * Wiki sayfalarındaki otomatik çeviri mantığının kullanıcı biyografileri/açıklamaları
 * için ortak hâli.
 *
 * Kurallar (wiki çevirisiyle birebir):
 *  - Yalnızca **otomatik çeviri** ayarı açıksa çalışır; ayar kapalıysa metin olduğu gibi kalır.
 *  - Çeviri uygulama içinde ([TranslationManager]) yapılır; çeviri bellekte tutulur, tekrar
 *    istek atılmaz.
 *  - Çeviri sürerken [isLoading] true olur; çağrı yerleri bunu
 *    [LocalKitsugiTranslationLoading] üzerinden çeviri ikonuna aktarır.
 *  - Çeviri varsa kullanıcı "Orijinal" / "Çeviri" düğmesiyle geçiş yapabilir.
 *  - Çeviri başarısız olursa orijinal metin gösterilir (boş ekran yok).
 */
@Stable
data class AutoTranslationState(
    /** Ekranda gösterilecek metin (çeviri veya orijinal). */
    val displayText: String,
    /** Uygulama içi çeviri sürüyor mu? */
    val isLoading: Boolean,
    /** Ekranda gösterilebilir bir çeviri var mı? */
    val hasTranslation: Boolean,
    /** Şu an orijinal metin mi gösteriliyor? */
    val showingOriginal: Boolean,
    /** Orijinal / çeviri geçişi. */
    val toggleOriginal: () -> Unit
)

/** Düz metin (Kitsu bio, MAL "Hakkında" vb.) için otomatik çeviri. */
@Composable
fun rememberAutoTranslatedText(
    text: String?,
    autoTranslateEnabled: Boolean
): AutoTranslationState {
    val context = LocalContext.current
    val source = text.orEmpty()

    var translated by remember(source) { mutableStateOf<String?>(null) }
    var isLoading by remember(source) { mutableStateOf(false) }
    var showingOriginal by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source, autoTranslateEnabled) {
        if (!autoTranslateEnabled || source.isBlank() || translated != null) return@LaunchedEffect
        isLoading = true
        try {
            val result = TranslationManager(context).translateToTarget(source)
            if (result.isNotBlank() && !result.trim().equals(source.trim(), ignoreCase = true)) {
                translated = result
                showingOriginal = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Çeviri başarısızsa orijinal metin gösterilir.
        } finally {
            isLoading = false
        }
    }

    val translation = translated
    return AutoTranslationState(
        displayText = if (translation != null && !showingOriginal) translation else source,
        isLoading = isLoading,
        hasTranslation = translation != null,
        showingOriginal = showingOriginal,
        toggleOriginal = { if (translated != null) showingOriginal = !showingOriginal }
    )
}

/**
 * HTML gövde (AniList/MAL biyografileri) için otomatik çeviri.
 *
 * Etiketler olduğu gibi korunur; yalnızca metin düğümleri çevrilir. Böylece
 * biyografideki bağlantılar, görseller, renkler ve <br> düzeni bozulmaz.
 */
@Composable
fun rememberAutoTranslatedHtml(
    html: String?,
    autoTranslateEnabled: Boolean
): AutoTranslationState {
    val context = LocalContext.current
    val source = html.orEmpty()

    var translated by remember(source) { mutableStateOf<String?>(null) }
    var isLoading by remember(source) { mutableStateOf(false) }
    var showingOriginal by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source, autoTranslateEnabled) {
        if (!autoTranslateEnabled || source.isBlank() || translated != null) return@LaunchedEffect
        isLoading = true
        try {
            val manager = TranslationManager(context)
            val result = com.kitsugi.animelist.utils.KitsugiHtmlTranslation.translateHtml(source) { segment ->
                manager.translateWebPageText(
                    text = segment,
                    // Wiki sayfa çevirisiyle aynı ayarlar: kaynak/hedef dil tercihi.
                    sourceLang = com.kitsugi.animelist.utils.KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" },
                    targetLang = com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget()
                )
            }
            if (!result.isNullOrBlank() && !result.trim().equals(source.trim(), ignoreCase = true)) {
                translated = result
                showingOriginal = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Çeviri başarısızsa orijinal HTML gösterilir.
        } finally {
            isLoading = false
        }
    }

    val translation = translated
    return AutoTranslationState(
        displayText = if (translation != null && !showingOriginal) translation else source,
        isLoading = isLoading,
        hasTranslation = translation != null,
        showingOriginal = showingOriginal,
        toggleOriginal = { if (translated != null) showingOriginal = !showingOriginal }
    )
}
