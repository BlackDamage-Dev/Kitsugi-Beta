package com.kitsugi.animelist.data.remote

import java.io.File

/** Pure matching rules for AniSub releases and files contained in multi-episode archives. */
internal object AniSubSubtitleMatcher {
    private val rangePattern = Regex("(?<!\\d)(\\d{1,3})\\s*(?:-|~|–|—|to)\\s*(\\d{1,3})(?!\\d)", RegexOption.IGNORE_CASE)
    private val seasonEpisodePattern = Regex("\\bS(?:EASON)?\\s*\\d{1,2}\\s*E(?:PISODE)?\\s*(\\d{1,3})\\b", RegexOption.IGNORE_CASE)
    private val xEpisodePattern = Regex("\\b\\d{1,2}\\s*[xX]\\s*(\\d{1,3})\\b")
    private val namedEpisodePattern = Regex("\\b(?:episode|ep|bölüm|bolum)\\s*[.#-]?\\s*(\\d{1,3})\\b", RegexOption.IGNORE_CASE)
    private val bracketedEpisodePattern = Regex("[\\[(]\\s*(\\d{1,3})\\s*[\\])]" )
    private val technicalNoise = Regex("\\b(?:240|360|480|576|720|1080|1440|2160)p?\\b|\\b(?:h|x)?26[45]\\b|\\b(?:10|8)bit\\b|\\b(?:hevc|hdr|web-dl|webrip|bluray)\\b|\\b(?:19|20)\\d{2}\\b", RegexOption.IGNORE_CASE)
    private val numberPattern = Regex("(?<!\\d)\\d{1,3}(?!\\d)")

    /** A release is eligible when it explicitly names the requested episode or is a batch/range. */
    fun releaseMatchesEpisode(releaseName: String, episode: Int): Boolean {
        if (episode < 1) return false
        val name = releaseName.lowercase()
        if (rangePattern.findAll(name).any { match ->
                val start = match.groupValues[1].toIntOrNull()
                val end = match.groupValues[2].toIntOrNull()
                start != null && end != null && episode in minOf(start, end)..maxOf(start, end)
            }) return true

        val explicit = sequenceOf(seasonEpisodePattern, xEpisodePattern, namedEpisodePattern, bracketedEpisodePattern)
            .flatMap { pattern -> pattern.findAll(name).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() } }
            .toList()
        if (explicit.isNotEmpty()) return episode in explicit

        val isBatch = listOf("batch", "paket", "complete", "tümü", "tumu", "all episodes", "full season", "sezon paketi", "season pack", "season ", "sezon ")
            .any(name::contains)
        if (isBatch) return true

        val numbers = numberPattern.findAll(technicalNoise.replace(name, " "))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        return numbers.isEmpty() || episode in numbers
    }

    /**
     * Select every matching subtitle in an archive, not just the first one. For an archive with
     * exactly one file, the release's episode match is sufficient even if its filename is generic.
     * Batch archives with ambiguous filenames intentionally return no arbitrary fallback subtitle.
     */
    fun selectFiles(files: List<File>, episode: Int?, mediaTitle: String?, releaseName: String): List<File> {
        val supported = files.filter { it.extension.lowercase() in setOf("ass", "ssa", "srt", "vtt") }
        if (episode == null) return supported

        val exact = supported.filter { filenameMatchesEpisode(it.name, episode, mediaTitle) }
        if (exact.isNotEmpty()) return exact
        if (supported.size == 1 && releaseMatchesEpisode(releaseName, episode)) return supported
        return emptyList()
    }

    fun filenameMatchesEpisode(fileName: String, episode: Int, mediaTitle: String? = null): Boolean {
        if (episode < 1) return false
        val name = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("\\[[0-9a-fA-F]{8}\\]"), " ")
            .replace(technicalNoise, " ")
        val titleNumbers = numberPattern.findAll(mediaTitle.orEmpty())
            .mapNotNull { it.value.toIntOrNull() }
            .toList()

        val matches = sequenceOf(seasonEpisodePattern, xEpisodePattern, namedEpisodePattern, bracketedEpisodePattern)
            .flatMap { pattern -> pattern.findAll(name).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() } }
            .toList()
        if (matches.isNotEmpty()) return episode in matches

        if (rangePattern.findAll(name).any { match ->
                val start = match.groupValues[1].toIntOrNull()
                val end = match.groupValues[2].toIntOrNull()
                start != null && end != null && episode in minOf(start, end)..maxOf(start, end)
            }) return true

        val filenameNumbers = numberPattern.findAll(name).mapNotNull { it.value.toIntOrNull() }.toList()
        return filenameNumbers.any { number ->
            number == episode && (number !in titleNumbers ||
                filenameNumbers.count { it == number } > titleNumbers.count { it == number })
        }
    }
}
