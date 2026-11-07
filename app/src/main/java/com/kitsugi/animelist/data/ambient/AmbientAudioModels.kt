package com.kitsugi.animelist.data.ambient

import java.text.Normalizer
import java.util.Locale

/** Public catalog adapters used by the manga reader's online background-audio search. */
enum class AmbientAudioSource(
    val key: String,
    val label: String,
    val openverseSource: String? = null,
) {
    FREESOUND("freesound", "Freesound · Openverse", "freesound"),
    WIKIMEDIA_AUDIO("wikimedia_audio", "Wikimedia audio · Openverse", "wikimedia_audio"),
    INTERNET_ARCHIVE("internet_archive", "Internet Archive"),
    AUDIUS("audius", "Audius"),
    WIKIMEDIA_COMMONS("wikimedia_commons", "Wikimedia Commons"),
    CCMIXTER("ccmixter", "ccMixter"),
    FREE_TO_USE("free_to_use", "Free To Use"),
    RADIO_BROWSER("radio_browser", "Radio Browser"),
}

enum class AmbientAudioMode {
    AUTO,
    MUSIC,
    AMBIENCE,
}

data class AmbientAudioContext(
    val title: String,
    val chapterTitle: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
)

data class AmbientAudioPlan(
    /** Mood/context query used after the reader-title search. */
    val query: String,
    val moodLabel: String,
    val reason: String,
    val matchTerms: Set<String>,
    /** Primary title copied from the currently open manga source and searched first. */
    val titleQuery: String = "",
    val titleMatchTerms: Set<String> = emptySet(),
)

data class AmbientAudioTrack(
    /** Provider-qualified ID, so IDs from different catalogs cannot collide. */
    val id: String,
    val title: String,
    val creator: String,
    val streamUrl: String,
    val landingUrl: String,
    val provider: String,
    val source: String,
    val durationMs: Long? = null,
    val fileType: String = "",
    val tags: List<String> = emptyList(),
    val fileSizeBytes: Long? = null,
    /** Direct audio file URL for offline use; not populated for live radio. */
    val downloadUrl: String? = null,
) {
    val sourceLabel: String
        get() = when {
            provider.equals("internet_archive", ignoreCase = true) -> "Internet Archive"
            provider.equals("audius", ignoreCase = true) -> "Audius"
            provider.equals("wikimedia_commons", ignoreCase = true) -> "Wikimedia Commons"
            provider.equals("ccmixter", ignoreCase = true) -> "ccMixter"
            provider.equals("free_to_use", ignoreCase = true) -> "Free To Use"
            provider.equals("radio_browser", ignoreCase = true) -> "Radio Browser"
            source.equals("freesound", ignoreCase = true) || provider.equals("freesound", ignoreCase = true) ->
                "Freesound · Openverse"
            source.equals("wikimedia_audio", ignoreCase = true) || provider.equals("wikimedia_audio", ignoreCase = true) ->
                "Wikimedia audio · Openverse"
            else -> "Openverse"
        }
}

/** Stable identities catch an item resurfacing through a different catalog ID. */
internal fun AmbientAudioTrack.historyKeys(): Set<String> {
    val titleKey = historyText(title)
    val creatorKey = historyText(creator)
    return buildSet {
        if (id.isNotBlank()) add(id)
        if (titleKey.isNotBlank()) add("metadata:$titleKey|$creatorKey")
    }
}

internal fun AmbientAudioTrack.deduplicationKey(): String =
    historyKeys().firstOrNull { it.startsWith("metadata:") } ?: id

private fun historyText(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
    .replace("ı", "i")
    .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

internal const val MAX_AMBIENT_AUDIO_STRING_LENGTH = 500

/** Defensive bound for every scalar or array-derived provider value kept in a result/cache. */
internal fun String.cappedAmbientAudioValue(): String = take(MAX_AMBIENT_AUDIO_STRING_LENGTH)

data class AmbientAudioSearchResult(
    val tracks: List<AmbientAudioTrack>,
    val warnings: List<String> = emptyList(),
    val fromCache: Boolean = false,
    /** Counts describe the title-first and following context segments of an automatic result queue. */
    val titleMatchCount: Int = 0,
    val contextMatchCount: Int = 0,
)
