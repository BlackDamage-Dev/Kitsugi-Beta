package com.kitsugi.animelist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

/**
 * Global CompositionLocal to detect if the app is running on an Android TV device.
 * Any composable can read this via: val isTv = LocalIsTv.current
 */
val LocalIsTv = staticCompositionLocalOf { false }
val LocalIsTvDevice = staticCompositionLocalOf { false }

/**
 * Global CompositionLocals for NSFW content settings.
 * These are provided at the root of the app so every image composable can
 * apply the blur automatically without needing per-parameter drilling.
 *
 * LocalBlurAdultMedia: when true and the item is adult, images should be blurred.
 * LocalShowAdultContent: when false, adult items are filtered out upstream.
 */
val LocalBlurAdultMedia = compositionLocalOf { false }

/**
 * +18 içerikler listelerde gösterilsin mi? (Ayarlar → Yetişkin İçeriği)
 *
 * `false` iken yetişkin (`isAdult = true`) öğeler tüm kaynak raflarında/arama
 * sonuçlarında filtrelenir. Kök composable bunu sağladığı için derin ekranlar
 * (eklenti portalı, manga uzantıları, ilişkiler sekmesi vb.) parametre delmeye
 * gerek kalmadan `LocalShowAdultContent.current` ile gizleme uygulayabilir.
 */
val LocalShowAdultContent = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun KitsugiAnimeListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    selectedThemeId: String = "mint",
    customAccentColor: Int = 0,
    customAccentColor2: Int = 0,
    customAccentGradientAngle: Int = 135,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    isTv: Boolean = false,
    content: @Composable () -> Unit
) {
    val KitsugiColors = when {
        isTv -> KitsugiDarkColors // TV holds dark theme stable
        !darkTheme -> KitsugiLightColors
        amoledBlack -> KitsugiAmoledColors
        else -> KitsugiDarkColors
    }

    val accentColor = if (customAccentColor != 0) {
        androidx.compose.ui.graphics.Color(customAccentColor)
    } else {
        KitsugiAccentForThemeId(selectedThemeId)
    }

    // Gradyan bitiş rengi (0 = düz renk).
    // DİKKAT: customAccentColor != 0 şartı bilinçli — özel vurgu rengi seçili
    // DEĞİLKEN (hazır tema) DataStore'da kalmış eski bir customAccentColor2
    // değeri gradyan modunu yanlışlıkla aktive ediyordu; bu da hazır temada
    // TÜM yazı/ikon/arka planların pahalı gradyan çizim yoluna girmesi ve
    // uygulamanın genel kasma/donması demekti. Gradyan ancak renk seçicide
    // "Uygula" ile kaydedilmişse anlamlıdır ve o yol her zaman color1'i de yazar.
    val accentColor2 = if (customAccentColor != 0 && customAccentColor2 != 0) {
        androidx.compose.ui.graphics.Color(customAccentColor2)
    } else {
        null
    }

    val angleFloat = customAccentGradientAngle.toFloat()

    // Vurgu arka plan fırçası: düz veya açılı lineer gradyan
    val accentBrush = accentBackgroundBrush(accentColor, accentColor2, angleFloat)

    // Vurgu zeminindeki TÜM yazı/ikonlar için otomatik siyah-beyaz kontrast rengi
    val onAccent = onAccentColor(accentColor, accentColor2)

    // MIUI / HyperOS / Android 10+ Force Dark (TransformCanvas) koruması:
    // Bu tema altındaki tüm AndroidComposeView ve kök DecorView örneklerinde
    // donanımsal ForceDark dönüşümünü kapatır.
    val currentView = androidx.compose.ui.platform.LocalView.current
    if (!currentView.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        androidx.compose.runtime.SideEffect {
            runCatching {
                currentView.isForceDarkAllowed = false
                currentView.rootView?.isForceDarkAllowed = false
            }
        }
    }

    // Provide isTv as false for layout-adaptive components, but keep isTvDevice for D-pad enhancements
    CompositionLocalProvider(
        LocalIsTv provides false,
        LocalIsTvDevice provides isTv,
        LocalKitsugiColors provides KitsugiColors,
        LocalKitsugiAccent provides accentColor,
        LocalKitsugiAccent2 provides accentColor2,
        LocalKitsugiAccentAngle provides angleFloat,
        LocalKitsugiAccentBrush provides accentBrush,
        LocalKitsugiOnAccent provides onAccent
    ) {
        val baseScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        val secondaryAccent = accentColor2 ?: accentColor
        val colorScheme = baseScheme.copy(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = accentColor.copy(alpha = 0.22f),
            onPrimaryContainer = KitsugiColors.textPrimary,
            secondary = secondaryAccent,
            onSecondary = onAccent,
            secondaryContainer = secondaryAccent.copy(alpha = 0.22f),
            onSecondaryContainer = KitsugiColors.textPrimary,
            tertiary = secondaryAccent,
            onTertiary = onAccent,
            surfaceTint = accentColor
        )
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private fun scaleTextStyle(style: androidx.compose.ui.text.TextStyle, factor: Float): androidx.compose.ui.text.TextStyle {
    val newSize = if (style.fontSize.type == TextUnitType.Sp) (style.fontSize.value * factor).sp else style.fontSize
    val newHeight = if (style.lineHeight.type == TextUnitType.Sp) (style.lineHeight.value * factor).sp else style.lineHeight
    return style.copy(fontSize = newSize, lineHeight = newHeight)
}
