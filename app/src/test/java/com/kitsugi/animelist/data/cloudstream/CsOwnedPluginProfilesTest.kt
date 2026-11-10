package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsOwnedPluginProfilesTest {
    @Test
    fun profilePinsExactlyTheEightAuditedBuilds() {
        assertEquals(
            mapOf(
                "AnimeciX" to 24,
                "Anizium" to 25,
                "FilmMakinesi" to 72,
                "HDFilmCehennemi" to 63,
                "TrAnimeIzle" to 27,
                "DiziBox" to 27,
                "HDFilmIzle" to 11,
                "Anizm" to 2
            ),
            CsOwnedPluginProfiles.auditedBuilds.associate { it.internalName to it.version }
        )
    }

    @Test
    fun verifiedFilmMakinesiMovieBuildIsSkippedForAnimeEpisodesButAllowedForMovies() {
        val movieTypes = listOf("Movie")

        assertFalse(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = 72,
                mediaType = MediaType.Anime,
                isMovie = false,
                supportedTypes = movieTypes
            )
        )
        assertEquals(
            "FilmMakinesi v72 yalnızca film sayfası döndürüyor; bölüm araması atlandı.",
            CsOwnedPluginProfiles.episodeUnsupportedReason(
                providerName = "FilmMakinesi",
                pluginVersion = 72,
                mediaType = MediaType.Anime,
                isMovie = false
            )
        )
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = 72,
                mediaType = MediaType.Anime,
                isMovie = true,
                supportedTypes = movieTypes
            )
        )
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = 72,
                mediaType = MediaType.Movie,
                isMovie = false,
                supportedTypes = movieTypes
            )
        )
    }

    @Test
    fun versionMismatchFailsOpenAndDoesNotReuseTheMovieOnlyOverride() {
        assertNull(CsOwnedPluginProfiles.findVerifiedBuild("FilmMakinesi", 73))
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = 73,
                mediaType = MediaType.Anime,
                isMovie = false,
                supportedTypes = listOf("Movie")
            )
        )
    }

    @Test
    fun hdfilmIzleV11IsNotIncorrectlyTreatedAsMovieOnly() {
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "HDFilmİzle",
                pluginVersion = 11,
                mediaType = MediaType.TvShow,
                isMovie = false,
                supportedTypes = listOf("Movie", "TvSeries")
            )
        )
    }

    @Test
    fun unknownInstalledVersionKeepsTheRuntimeTypeDeclarationBehavior() {
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = null,
                mediaType = MediaType.Anime,
                isMovie = false,
                supportedTypes = listOf("Movie")
            )
        )
    }

    @Test
    fun unknownContentTypeDoesNotTreatDefaultFalseMovieBitAsEpisodeEvidence() {
        assertTrue(
            CsOwnedPluginProfiles.supportsRequest(
                providerName = "FilmMakinesi",
                pluginVersion = 72,
                mediaType = null,
                isMovie = false,
                supportedTypes = listOf("Movie")
            )
        )
    }

    @Test
    fun aniziumAuthShimIsRestrictedToTheAuditedV25Build() {
        assertTrue(CsOwnedPluginProfiles.shouldApplyAniziumV25AuthShim(25))
        assertFalse(CsOwnedPluginProfiles.shouldApplyAniziumV25AuthShim(24))
        assertFalse(CsOwnedPluginProfiles.shouldApplyAniziumV25AuthShim(26))
        assertFalse(CsOwnedPluginProfiles.shouldApplyAniziumV25AuthShim(null))
    }
}
