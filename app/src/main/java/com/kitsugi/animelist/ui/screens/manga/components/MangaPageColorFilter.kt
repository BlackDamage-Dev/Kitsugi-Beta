package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaImageFilters

internal fun mangaPageColorFilter(type: ColorFilterType): ColorFilter? = when (type) {
    ColorFilterType.Normal -> null
    else -> MangaImageFilters.filterMatrix(type)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
}
