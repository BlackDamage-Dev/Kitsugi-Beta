package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import java.text.Normalizer
import java.util.Locale

/**
 * Verified capabilities for the eight Kitsugi-owned CloudStream builds audited on 2026-10-26.
 *
 * A profile is only applied when both the provider name and the installed `.cs3` manifest version
 * match. Unknown/newer builds deliberately fall back to their runtime `supportedTypes`; this keeps
 * a future provider update from inheriting a stale restriction without being re-audited.
 */
internal object CsOwnedPluginProfiles {
    internal data class AuditedBuild(
        val internalName: String,
        val version: Int,
        /** Whether the inspected build can return episode/series data from `load()`. */
        val supportsEpisodes: Boolean
    )

    val auditedBuilds: List<AuditedBuild> = listOf(
        AuditedBuild("AnimeciX", 24, supportsEpisodes = true),
        AuditedBuild("Anizium", 25, supportsEpisodes = true),
        AuditedBuild("FilmMakinesi", 72, supportsEpisodes = false),
        AuditedBuild("HDFilmCehennemi", 63, supportsEpisodes = true),
        AuditedBuild("TrAnimeIzle", 27, supportsEpisodes = true),
        AuditedBuild("DiziBox", 27, supportsEpisodes = true),
        AuditedBuild("HDFilmIzle", 11, supportsEpisodes = true),
        AuditedBuild("Anizm", 2, supportsEpisodes = true)
    )

    /**
     * Applies the one audited behavior that is narrower than the manifest declaration: FilmMakinesi
     * v72 is a Movie-only build and must not be queried for an episodic title. Anime movies remain
     * eligible because the app can route them through the movie pipeline.
     */
    fun supportsRequest(
        providerName: String,
        pluginVersion: Int?,
        mediaType: MediaType?,
        isMovie: Boolean?,
        supportedTypes: Iterable<String>
    ): Boolean {
        val build = findVerifiedBuild(providerName, pluginVersion)
        if (build?.supportsEpisodes == false && isEpisodicRequest(mediaType, isMovie)) return false
        return CsPluginTypeCompatibility.supports(mediaType, supportedTypes)
    }

    fun episodeUnsupportedReason(
        providerName: String,
        pluginVersion: Int?,
        mediaType: MediaType?,
        isMovie: Boolean?
    ): String? {
        val build = findVerifiedBuild(providerName, pluginVersion) ?: return null
        if (build.supportsEpisodes || !isEpisodicRequest(mediaType, isMovie)) return null
        return "${build.internalName} v${build.version} yalnızca film sayfası döndürüyor; bölüm araması atlandı."
    }

    internal fun findVerifiedBuild(providerName: String, pluginVersion: Int?): AuditedBuild? {
        if (pluginVersion == null) return null
        val normalizedName = normalize(providerName)
        return auditedBuilds.firstOrNull {
            it.version == pluginVersion && normalize(it.internalName) == normalizedName
        }
    }

    /** The runtime Cf-Control shim is based on the inspected Anizium v25 binary only. */
    fun shouldApplyAniziumV25AuthShim(pluginVersion: Int?): Boolean =
        findVerifiedBuild("Anizium", pluginVersion)?.version == 25

    private fun isEpisodicRequest(mediaType: MediaType?, isMovie: Boolean?): Boolean = when {
        isMovie == true -> false
        mediaType == MediaType.Movie -> false
        mediaType == MediaType.Anime || mediaType == MediaType.TvShow -> true
        // `false` is also the default for some direct-URL/diagnostic callers. Do not infer a
        // series request from that bit alone when the content category itself is unknown.
        else -> false
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .filter { it.isLetterOrDigit() }
        .lowercase(Locale.ROOT)
}
