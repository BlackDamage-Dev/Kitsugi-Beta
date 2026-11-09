package com.kitsugi.animelist.core.player

/** Reject explicit contradictions, but do not claim metadata proves a release is in sync. */
object SubtitleEpisodeValidator {
    private val imdbSeries = Regex("^tt[0-9]+:(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
    private val tmdbSeries = Regex("^tmdb:[0-9]+:(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
    private val seasonEpisode = Regex("(?i)(?<![a-z0-9])s(\\d{1,3})[ ._-]*e(\\d{1,4})(?![0-9])")
    private val xEpisode = Regex("(?i)(?<![a-z0-9])(\\d{1,3})x(\\d{1,4})(?![0-9])")

    fun isCompatible(type: String, id: String, season: Int?, episode: Int?, fileName: String?, release: String?): Boolean {
        if (!type.equals("series", true)) return true
        val query = imdbSeries.matchEntire(id) ?: tmdbSeries.matchEntire(id) ?: return true
        val expectedSeason = query.groupValues[1].toIntOrNull() ?: return true
        val expectedEpisode = query.groupValues[2].toIntOrNull() ?: return true
        if (season != null && season != expectedSeason) return false
        if (episode != null && episode != expectedEpisode) return false
        // Never infer from isolated digits: 1080p, codec names, years and absolute
        // anime episode numbers are not reliable season-relative episode markers.
        for (value in listOfNotNull(fileName, release)) {
            val markers = (seasonEpisode.findAll(value) + xEpisode.findAll(value)).toList()
            if (markers.any {
                it.groupValues[1].toIntOrNull() != expectedSeason || it.groupValues[2].toIntOrNull() != expectedEpisode
            }) return false
        }
        return true
    }
}
