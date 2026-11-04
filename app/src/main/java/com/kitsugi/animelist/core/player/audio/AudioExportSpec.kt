package com.kitsugi.animelist.core.player.audio

import java.util.Locale

/** Identifies a player row without assuming its UI index (or mpv aid) is a demuxer stream index. */
internal data class AudioTrackIdentity(
    val language: String?,
    val name: String?,
    val externalUrl: String? = null
)

/** A physical stream is present, but it does not uniquely represent the chosen player row. */
internal class AudioTrackSelectionException(message: String) : IllegalArgumentException(message)

internal object AudioExportSpec {
    fun language(raw: String?): String {
        val code = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.substringBefore('-').orEmpty()
        return when (code) {
            "und", "unknown", "", "mul", "zxx" -> ""
            "tur", "turkish", "türkçe", "turkce" -> "tr"
            "eng", "english", "ingilizce" -> "en"
            "jpn", "japanese", "japonca" -> "ja"
            "kor", "korean", "korece" -> "ko"
            "deu", "ger" -> "de"; "fra", "fre" -> "fr"; "spa" -> "es"
            "ita" -> "it"; "rus" -> "ru"; "zho", "chi" -> "zh"
            "ara" -> "ar"; "por" -> "pt"; "hin" -> "hi"
            else -> code.takeIf { it.matches(Regex("[a-z]{2,3}")) }.orEmpty()
        }
    }

    /** A separate URL is never the selected row merely because only one row is visible. */
    fun source(videoUrl: String?, selected: AudioTrackIdentity): String =
        selected.externalUrl?.takeIf { it.isNotBlank() }
            ?: videoUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Ses kaynağı bulunamadı")

    /** A name can distinguish equal-language HLS/DASH renditions, but not progressive demux tracks. */
    fun needsManifestName(selected: AudioTrackIdentity, playerTracks: List<AudioTrackIdentity>): Boolean {
        // A distinct external URL identifies its own file. Two player rows sharing the
        // SAME external playlist do not: their language/name still has to be matched.
        val sameFile = playerTracks.filter { it.externalUrl == selected.externalUrl }
        val lang = language(selected.language)
        return sameFile.size > 1 &&
            (lang.isEmpty() || sameFile.count { language(it.language) == lang } > 1)
    }

    data class Candidate(val index: Int, val language: String)

    /** No guessing from mpv aid / UI index when multiple physical streams can match. */
    fun select(candidates: List<Candidate>, wantedLanguage: String?, requireKnownLanguage: Boolean = false): Int {
        require(candidates.isNotEmpty()) { "Kaynakta ses parçası bulunamadı" }
        val wanted = language(wantedLanguage)
        if (candidates.size == 1) {
            val actual = language(candidates.single().language)
            if (requireKnownLanguage && actual.isEmpty()) {
                throw AudioTrackSelectionException("Birden çok oynatıcı sesi var, kaynak dilini doğrulayamıyor")
            }
            if (wanted.isNotEmpty() && actual.isNotEmpty() && wanted != actual) {
                throw AudioTrackSelectionException("Seçilen ses dili kaynakta bulunamadı")
            }
            return candidates.single().index
        }
        if (wanted.isEmpty()) throw AudioTrackSelectionException(
            "Ses dili belirtilmemiş: birden çok parça arasından seçim yapılamıyor"
        )
        val matches = candidates.filter { language(it.language) == wanted }
        if (matches.isEmpty()) throw AudioTrackSelectionException("Seçilen ses dili kaynakta bulunamadı")
        if (matches.size > 1) throw AudioTrackSelectionException("Aynı dilde birden çok ses var; yanlış parça indirilmedi")
        return matches.single().index
    }

    fun filenamePart(value: String, fallback: String): String = value
        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
        .trim(' ', '.')
        .take(90)
        .ifBlank { fallback }
}
