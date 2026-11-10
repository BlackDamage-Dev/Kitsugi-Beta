package com.kitsugi.animelist.data.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/** The integer is a Select position; the extension owns its site-specific value mapping. */
internal fun FilterList.selectFilterDescriptions(): List<MangaSelectFilter> =
    mapIndexedNotNull { index, filter ->
        (filter as? Filter.Select<*>)?.let {
            MangaSelectFilter(index, it.name, it.values.map { value -> value.toString() }, it.state)
        }
    }

internal fun FilterList.applySelectFilterPositions(selections: Map<Int, Int>) {
    selections.forEach { (index, selected) ->
        val filter = getOrNull(index) as? Filter.Select<*>
        if (filter != null && selected in filter.values.indices) filter.state = selected
    }
}
