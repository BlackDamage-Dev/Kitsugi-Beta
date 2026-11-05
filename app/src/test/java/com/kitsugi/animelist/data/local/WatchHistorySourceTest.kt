package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.data.repository.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistorySourceTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private fun entry() = WatchHistoryEntry(
        animeId = "anilist:42", animeTitle = "Example", posterUrl = null, season = 1, episode = 2
    )
    private fun source(
        addonId: String? = "stremio:a", addonName: String = "Addon",
        name: String = "Server", title: String = "Release", url: String? = "https://cdn.test/video.mp4?token=old",
        isCS: Boolean = false, hash: String? = null, fileIndex: Int? = null
    ) = StreamSource(addonName, name, title, url, hash, fileIndex, isCS = isCS, addonId = addonId)

    @Test fun freshSignedUrlAndHeadersAreUsedInsteadOfTheExpiredCopy() {
        val saved = WatchHistorySource.withSource(entry(), source(), "https://cdn.test/video.mp4?token=old")
        val fresh = source(url = "https://cdn.test/video.mp4?token=new").copy(
            requestHeaders = mapOf("Authorization" to "fresh"),
            subtitles = listOf(SubtitleInput("https://cdn.test/sub.srt?new", "English", "en")),
            externalAudioUrl = "https://cdn.test/audio.mp3?new"
        )
        val matched = WatchHistorySource.findSavedStream(saved, listOf(fresh))
        assertSame(fresh, matched)
        assertEquals("fresh", matched?.requestHeaders?.get("Authorization"))
        assertEquals(fresh.subtitles, matched?.subtitles)
        assertEquals(fresh.externalAudioUrl, matched?.externalAudioUrl)
        assertEquals(WatchHistorySource.urlKey(source().url), WatchHistorySource.urlKey(fresh.url))
        assertFalse(saved.streamUrlKey!!.contains("token"))
    }

    @Test fun sourceBelongingToAnotherAddonIsNeverAutoSelected() {
        val saved = WatchHistorySource.withSource(entry(), source(addonId = "stremio:a"), "url")
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(source(addonId = "stremio:b"))))
        assertNull(WatchHistorySource.findSavedStream(saved, emptyList()))
    }

    @Test fun addonManifestIdentityHidesCredentialsAndDistinguishesProviders() {
        val one = WatchHistorySource.stremioAddonId("https://provider.test/secret/manifest.json")
        val two = WatchHistorySource.stremioAddonId("https://provider.test/other/manifest.json")
        assertFalse(one.contains("secret"))
        assertNotEquals(one, two)
        assertEquals("provider", WatchHistorySource.cloudStreamPluginId(WatchHistorySource.cloudStreamAddonId("provider")))
        assertNull(WatchHistorySource.cloudStreamPluginId(one))
    }

    @Test fun torrentHashAndFileIndexMustBothMatch() {
        val original = source(url = null, hash = hash, fileIndex = 2)
        val saved = WatchHistorySource.withSource(entry(), original, "http://127.0.0.1/play")
        val wrongFile = source(url = null, hash = hash, fileIndex = 3)
        val wrongHash = source(url = null, hash = "f".repeat(40), fileIndex = 2)
        val right = source(url = null, hash = hash.uppercase(), fileIndex = 2)
        assertSame(right, WatchHistorySource.findSavedStream(saved, listOf(wrongFile, wrongHash, right)))
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(wrongFile, wrongHash)))
    }

    @Test fun torrentSchemeIndexIsRetainedEvenIfNotExposedAsAField() {
        val saved = WatchHistorySource.withSource(entry(), source(url = "torrent://$hash/7"), "local")
        assertEquals(7, saved.streamFileIndex)
        val wrong = source(url = "torrent://$hash/8")
        val right = source(url = "torrent://$hash/7")
        assertSame(right, WatchHistorySource.findSavedStream(saved, listOf(wrong, right)))
    }

    @Test fun cloudStreamOriginIsReplacedOrClearedWithTheActualProvider() {
        val old = entry().copy(cs3Url = "https://old/page", cs3ApiName = "Old API")
        val cs = source(addonId = "cs:provider", addonName = "New API", isCS = true)
            .copy(cs3PageUrl = "https://new/page", cs3ApiName = "New API")
        val recorded = WatchHistorySource.withSource(old, cs, "video")
        assertEquals("https://new/page", recorded.cs3Url)
        assertEquals("New API", recorded.cs3ApiName)
        val stremio = WatchHistorySource.withSource(recorded, source(), "video")
        assertNull(stremio.cs3Url)
        assertNull(stremio.cs3ApiName)
    }

    @Test fun cloudStreamServerCanBeFoundAfterTheCdnPathChanges() {
        val original = source(addonId = "cs:p", isCS = true, title = "Old title")
        val saved = WatchHistorySource.withSource(entry(), original, "video")
        val fresh = original.copy(url = "https://different.test/new-path", title = "New title")
        assertSame(fresh, WatchHistorySource.findSavedStream(saved, listOf(fresh)))
    }

    @Test fun unrelatedStremioReleaseIsNotSelectedByAddonNameAlone() {
        val original = source(title = "Release A")
        val saved = WatchHistorySource.withSource(entry(), original, "video")
        val other = source(title = "Release B", url = "https://other.test/different")
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(other)))
    }

    @Test fun legacyUrlCanMatchWithoutSendingTheOldTokenToThePlayer() {
        val legacy = entry().copy(source = "Addon", streamUrl = "https://cdn.test/video.mp4?token=old")
        val other = source(url = "https://cdn.test/other.mp4?token=new")
        val fresh = source(url = "https://cdn.test/video.mp4?token=new")
        assertSame(fresh, WatchHistorySource.findSavedStream(legacy, listOf(other, fresh)))
    }

    @Test fun legacyRecordWithNoFingerprintDoesNotGuessBetweenSeveralStreams() {
        val legacy = entry().copy(source = "Addon")
        val first = source(url = "https://cdn.test/a")
        val second = source(url = "https://cdn.test/b")
        assertNull(WatchHistorySource.findSavedStream(legacy, listOf(first, second)))
        assertSame(first, WatchHistorySource.findSavedStream(legacy, listOf(first)))
    }

    @Test fun legacyNameDoesNotGuessBetweenDifferentAddonIdentities() {
        val legacy = entry().copy(source = "Addon", streamTitle = "Release")
        assertNull(WatchHistorySource.findSavedStream(legacy, listOf(source(addonId = "a"), source(addonId = "b"))))
        val matching = source(addonId = "a")
        assertSame(matching, WatchHistorySource.findSavedStream(legacy, listOf(matching)))
    }

    @Test fun qualityAndAudioKindDisambiguateTheSameServer() {
        val original = source(isCS = true).copy(quality = "1080p", providerAudioKind = "dub")
        val saved = WatchHistorySource.withSource(entry(), original, "video")
        val low = original.copy(url = "https://other.test/low", quality = "480p", providerAudioKind = "sub")
        val right = original.copy(url = "https://other.test/high")
        assertSame(right, WatchHistorySource.findSavedStream(saved, listOf(low, right)))
    }

    @Test fun contentQueryIdentifiesTheVideoWhileExpiringTokensCanChange() {
        val original = source(url = "https://cdn.test/play?id=1&token=old")
        val saved = WatchHistorySource.withSource(entry(), original, "old")
        val other = source(url = "https://cdn.test/play?token=new&id=2")
        val fresh = source(url = "https://cdn.test/play?token=new&id=1")
        assertNotEquals(WatchHistorySource.urlKey(original.url), WatchHistorySource.urlKey(other.url))
        assertEquals(WatchHistorySource.urlKey(original.url), WatchHistorySource.urlKey(fresh.url))
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(other)))
        assertSame(fresh, WatchHistorySource.findSavedStream(saved, listOf(other, fresh)))
    }

    @Test fun multipleSignedUrlsWithIdenticalLabelsAreAmbiguous() {
        val saved = WatchHistorySource.withSource(entry(), source(), "old")
        val first = source(url = "https://cdn.test/video.mp4?token=one")
        val second = source(url = "https://cdn.test/video.mp4?token=two")
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(first, second)))
    }

    @Test fun sameApiNameOnAnotherCloudStreamPluginCannotReuseOldPage() {
        val old = entry().copy(addonId = "cs:one", cs3Url = "https://old/page", cs3ApiName = "Shared")
        val other = source(addonId = "cs:two", addonName = "Shared", isCS = true).copy(cs3ApiName = "Shared")
        assertNull(WatchHistorySource.withSource(old, other, "video").cs3Url)
        val same = other.copy(addonId = "cs:one")
        assertEquals("https://old/page", WatchHistorySource.withSource(old, same, "video").cs3Url)
    }

    @Test fun tiedReleaseNamesWithDifferentUrlsAreNotGuessed() {
        val saved = WatchHistorySource.withSource(entry(), source(), "old")
        val first = source(url = "https://other.test/a")
        val second = source(url = "https://other.test/b")
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(first, second)))
        assertFalse(WatchHistorySource.hasExactFingerprint(saved, first))
    }

    @Test fun sameTitleWithADifferentStremioNameDoesNotMatch() {
        val saved = WatchHistorySource.withSource(entry(), source(name = "Provider A"), "old")
        val other = source(name = "Provider B", url = "https://other.test/new")
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(other)))
    }

    @Test fun audioLanguagesDisambiguateOtherwiseIdenticalSources() {
        val original = source().copy(providerLanguages = listOf("tr"))
        val saved = WatchHistorySource.withSource(entry(), original, "old")
        val other = original.copy(url = "https://other.test/en", providerLanguages = listOf("en"))
        val matching = original.copy(url = "https://other.test/tr")
        assertSame(matching, WatchHistorySource.findSavedStream(saved, listOf(other, matching)))
    }

    @Test fun torrentWithoutAFileIndexDoesNotGuessBetweenSeveralFiles() {
        val saved = WatchHistorySource.withSource(entry(), source(url = null, hash = hash), "local")
        val first = source(url = null, hash = hash, fileIndex = 1)
        val second = source(url = null, hash = hash, fileIndex = 2)
        assertNull(WatchHistorySource.findSavedStream(saved, listOf(first, second)))
    }

    @Test fun localUrlsAreNotUsedAsPermanentSourceFingerprints() {
        assertNull(WatchHistorySource.urlKey("http://proxy.localhost/stream"))
        assertNull(WatchHistorySource.urlKey("http://0.0.0.0/stream"))
        assertNull(WatchHistorySource.urlKey("http://127.0.0.1:6881/play"))
        assertNull(WatchHistorySource.urlKey("http://127.0.0.2:6881/play"))
        assertNull(WatchHistorySource.urlKey("http://localhost:6881/play"))
        assertNull(WatchHistorySource.urlKey("http://[::1]:6881/play"))
        assertNull(WatchHistorySource.urlKey("magnet:?xt=urn:btih:$hash"))
        assertTrue(WatchHistorySource.urlKey("https://provider.test/video")!!.isNotBlank())
    }
}
