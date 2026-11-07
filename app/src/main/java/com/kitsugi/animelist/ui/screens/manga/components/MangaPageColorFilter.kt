package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.kitsugi.animelist.data.manga.ColorFilterType

internal fun mangaPageColorFilter(type: ColorFilterType): ColorFilter? = when (type) {
    ColorFilterType.Normal, ColorFilterType.Cool -> null
    ColorFilterType.Grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    ColorFilterType.Sepia, ColorFilterType.Warm -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        .393f, .769f, .189f, 0f, 0f,
        .349f, .686f, .168f, 0f, 0f,
        .272f, .534f, .131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    ColorFilterType.Invert -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )))
}
