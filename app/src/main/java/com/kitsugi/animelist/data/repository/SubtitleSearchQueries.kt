package com.kitsugi.animelist.data.repository

import java.util.Locale

internal data class SubtitleResolverIds(val malId: Int?, val aniListId: Int?, val kitsuId: Int?)

/** Decode the same stable-ID namespaces used by the stream picker. */
internal fun subtitleResolverIds(malId: Int?, aniListId: Int?): SubtitleResolverIds = SubtitleResolverIds(
    malId = malId?.takeIf { it in 1 until 100_000_000 },
    aniListId = when {
        aniListId == null || aniListId <= 0 -> null
        aniListId in 100_000_001 until 200_000_000 -> aniListId - 100_000_000
        aniListId < 100_000_000 -> aniListId
        else -> null
    },
    kitsuId = malId?.takeIf { it in 300_000_001 until 400_000_000 }?.minus(300_000_000)
)

internal data class SubtitleQuery(val type: String, val id: String, val title: String? = null)

/** Series requests must retain season/episode: movie fallbacks can return a different release. */
internal fun buildSubtitleQueries(
    imdbId: String?, kitsuId: Int?, titles: List<String>, isMovie: Boolean, season: Int, episode: Int
): List<SubtitleQuery> {
    val type = if (isMovie) "movie" else "series"
    val s = season.coerceAtLeast(0)
    val e = episode.coerceAtLeast(1)
    val result = mutableListOf<SubtitleQuery>()
    imdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }?.let {
        result += SubtitleQuery(type, if (isMovie) it else "$it:$s:$e")
    }
    kitsuId?.takeIf { it > 0 }?.let {
        result += SubtitleQuery(type, if (isMovie) "kitsu:$it" else "kitsu:$it:$e")
    }
    titles.asSequence().map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }.take(6).forEach {
            result += SubtitleQuery(type, if (isMovie) it else "$it:$s:$e", it)
        }
    return result
}

/** Name-only fallback requires an exact normalized alias, not a substring. */
internal fun subtitleTitleMatches(query: String, titles: List<String>): Boolean {
    fun normalize(value: String): String = java.text.Normalizer
        .normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFKC)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    val target = normalize(query)
    return target.isNotEmpty() && titles.any { normalize(it) == target }
}

/** If a release names a season explicitly, do not offer it for another season. */
internal fun subtitleSeasonMatches(name: String, season: Int?): Boolean {
    if (season == null) return true
    val patterns = listOf(
        Regex("(?i)(?<![a-z0-9])s(\\d{1,2})(?:e\\d{1,3}|\\b)"),
        Regex("(?i)(?<![a-z0-9])(\\d{1,2})x\\d{1,3}\\b"),
        Regex("(?i)\\b(?:season|sezon)\\s*(\\d{1,2})\\b")
    )
    val found = patterns.firstNotNullOfOrNull { it.find(name)?.groupValues?.get(1)?.toIntOrNull() }
    return found == null || found == season
}

object SubtitleSearchQueries {
    fun latinQueries(titles: List<String>): List<String> {
        val nonCjk = titles.filter { t ->
            t.isNotBlank() &&
                t.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } &&
                !t.any { ch ->
                    val block = Character.UnicodeBlock.of(ch)
                    block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                        block == Character.UnicodeBlock.HIRAGANA ||
                        block == Character.UnicodeBlock.KATAKANA ||
                        block == Character.UnicodeBlock.HANGUL_SYLLABLES
                }
        }
        return nonCjk.ifEmpty {
            titles.filter { t -> t.isNotBlank() && t.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }
        }
    }
}
