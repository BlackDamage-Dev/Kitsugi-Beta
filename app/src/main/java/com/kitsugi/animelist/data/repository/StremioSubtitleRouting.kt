package com.kitsugi.animelist.data.repository

import java.util.Locale

/** Use the subtitle resource's declared type, not the stream resource's type. */
internal object StremioSubtitleRouting {
    fun requestType(types: String?, contentType: String): String? {
        val desired = contentType.lowercase(Locale.ROOT)
        val declared = types?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }.orEmpty()
        if (declared.isEmpty() || "*" in declared) return desired
        if (desired in declared) return desired
        val aliases = if (desired == "movie") listOf("animemovie", "anime") else listOf("anime")
        return aliases.firstOrNull { it in declared }
    }
}
