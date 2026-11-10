package com.kitsugi.animelist.ui.screens.search

import java.text.Normalizer
import java.util.Locale

private val pluginSearchWhitespaceRegex = Regex("\\s+")

/** Accent- and Turkish-I-insensitive normalization shared by every field in the portal search. */
internal fun normalizePluginPortalSearchText(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
    return buildString(decomposed.length) {
        decomposed.forEach { char ->
            when (Character.getType(char)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> Unit
                else -> append(if (char.isLetterOrDigit()) char else ' ')
            }
        }
    }.trim().replace(pluginSearchWhitespaceRegex, " ")
}
