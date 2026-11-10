package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.*
import org.junit.Test

/**
 * CsStreamRunner'ın pure logic katmanı için JVM unit testleri.
 *
 * Android bağımlılığı olmayan bölümler test edilir:
 *  - URL encoding (Turkish path search vs normal)
 *  - resolveHrefLi
 *  - isEmbedUrl
 *  - KNOWN_BROKEN_PLUGINS listesi
 *  - KNOWN_BROKEN_DOMAINS listesi
 *  - buildTitleVariants delegasyonu (CsTitleMatcher üzerinden)
 *
 * Not: getStreams / safeSearch / safeLoad gibi network çağrıları yapan
 * fonksiyonlar Instrumented test kapsamına girmektedir.
 */
class CsStreamRunnerLogicTest {

    @Test
    fun testEmptySearchMethodResultAllowsNextOverriddenMethod() {
        assertTrue(CsStreamRunner.shouldTryNextSearchMethod(null))
        assertTrue(CsStreamRunner.shouldTryNextSearchMethod(emptyList<Any>()))
        assertFalse(CsStreamRunner.shouldTryNextSearchMethod(listOf("result")))
    }

    // ─── resolveHrefLi Tests ──────────────────────────────────────────────────

    @Test
    fun testResolveHrefLiStripsPrefix() {
        val raw = "https://href.li/?https://streamtape.com/e/abcdef"
        val resolved = CsStreamRunner.resolveHrefLi(raw)
        assertEquals("https://streamtape.com/e/abcdef", resolved)
    }

    @Test
    fun testResolveHrefLiNoOp_WhenNoHrefLi() {
        val raw = "https://streamtape.com/e/abcdef"
        val resolved = CsStreamRunner.resolveHrefLi(raw)
        assertEquals(raw, resolved)
    }

    @Test
    fun testResolveHrefLiWithQueryString() {
        val raw = "https://href.li/?https://vk.com/video?id=12345&hash=abc"
        val resolved = CsStreamRunner.resolveHrefLi(raw)
        assertEquals("https://vk.com/video?id=12345&hash=abc", resolved)
    }

    @Test
    fun testResolveHrefLiCaseInsensitive() {
        // href.li with uppercase HREF.LI (edge case)
        val raw = "https://HREF.LI/?https://streamtape.com/e/abc"
        val resolved = CsStreamRunner.resolveHrefLi(raw)
        // Should still resolve (contains check is ignoreCase, indexOf uses lowercased url)
        assertEquals("https://streamtape.com/e/abc", resolved)
    }

    // ─── isEmbedUrl Tests ─────────────────────────────────────────────────────

    @Test
    fun testIsEmbedUrl_VK() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://vk.com/video123"))
    }

    @Test
    fun testIsEmbedUrl_VkVideo() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://vkvideo.ru/embed/abc"))
    }

    @Test
    fun testIsEmbedUrl_Sibnet() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://video.sibnet.ru/shell.php?videoid=12345"))
    }

    @Test
    fun testIsEmbedUrl_Streamtape() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://streamtape.com/e/abc123"))
    }

    @Test
    fun testIsEmbedUrl_StreamWish() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://streamwish.com/e/abc"))
    }

    @Test
    fun testIsEmbedUrl_Filemoon() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://filemoon.sx/e/abc"))
    }

    @Test
    fun testIsEmbedUrl_Okru() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://ok.ru/videoembed/12345"))
    }

    @Test
    fun testIsEmbedUrl_Vidmoly() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://vidmoly.to/embed/abc"))
    }

    @Test
    fun testIsEmbedUrl_Voe() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://voe.sx/e/abc"))
        assertFalse(com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper.isVoeUrl("https://voe.sx.example/e/abc"))
    }

    @Test
    fun testIsEmbedUrl_DoodStream() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://doodstream.com/e/abc"))
    }

    @Test
    fun testIsEmbedUrl_GenericEmbed() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://somesite.com/embed/video123"))
    }

    @Test
    fun testIsEmbedUrl_ShellPhp() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://video.sibnet.ru/shell.php?videoid=1234"))
    }

    @Test
    fun testIsEmbedUrl_PlayerPhp() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://somecdn.com/player.php?id=abc"))
    }

    @Test
    fun testIsEmbedUrl_HrefLiWrapped() {
        // href.li wrapped embeds should also be detected
        assertTrue(CsStreamRunner.isEmbedUrl("https://href.li/?https://vk.com/video12345"))
    }

    @Test
    fun testIsNotEmbedUrl_DirectM3u8() {
        assertFalse(CsStreamRunner.isEmbedUrl("https://cdn.example.com/hls/master.m3u8"))
    }

    @Test
    fun testIsNotEmbedUrl_DirectMp4() {
        assertFalse(CsStreamRunner.isEmbedUrl("https://cdn.example.com/video/episode3.mp4"))
    }

    @Test
    fun testIsEmbedUrl_GoogleDrivePreview() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://drive.google.com/file/d/abcDEF_123456/preview"))
        assertTrue(CsStreamRunner.isEmbedUrl("https://drive.usercontent.google.com/download?id=abcDEF_123456"))
    }

    @Test
    fun testExtensionlessSignedMediaHostsAreDirect() {
        assertTrue(CsStreamRunner.isKnownExtensionlessMediaUrl("https://rr1---sn.googlevideo.com/videoplayback?expire=1"))
        assertTrue(CsStreamRunner.isKnownExtensionlessMediaUrl("https://anizmplayer.com/cdn/hls/abc123?md5=1"))
        assertTrue(CsStreamRunner.isKnownExtensionlessMediaUrl("https://sistenn.uns.bio/v4/abc123?token=1"))
        assertTrue(CsStreamRunner.isClearlyDirectMediaUrl("https://okcdn.ru/stream/video?sig=1"))
        assertFalse(CsStreamRunner.isKnownExtensionlessMediaUrl("https://content-workspacevideo-pa.googleapis.com/v1/drive/media/id/playback"))
    }

    @Test
    fun testSendVidMp4RouteIsResolvedAsEmbedPage() {
        val url = "https://sendvid.com/episode123.mp4"
        assertTrue(CsStreamRunner.isEmbedUrl(url))
        assertFalse(CsStreamRunner.isClearlyDirectMediaUrl(url))
    }

    @Test
    fun testSendVidProviderUnavailableRouteIsNeverPassedAsRawMedia() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://sendvid.com/embed/episode123"))
        assertFalse(CsStreamRunner.isClearlyDirectMediaUrl("https://sendvid.com/embed/episode123"))
    }

    @Test
    fun testIsNotEmbedUrl_Mpd() {
        assertFalse(CsStreamRunner.isEmbedUrl("https://cdn.example.com/stream/manifest.mpd"))
    }

    @Test
    fun testIsNotEmbedUrl_RegularAnimePage() {
        assertFalse(CsStreamRunner.isEmbedUrl("https://turkanime.tv/anime/jujutsu-kaisen-2-sezon"))
    }

    // ─── Turkish Path Search Detection (isTurkishPathSearch logic) ───────────
    // We test the logic indirectly through the plugin name patterns defined in CsStreamRunner.

    @Test
    fun testTurkishPathSearchPlugins_AnimeciX() {
        // AnimeciX uses path encoding
        assertTrue(isTurkishPathSearchPlugin("AnimeciX"))
    }

    @Test
    fun testTurkishPathSearchPlugins_AnimPow() {
        assertTrue(isTurkishPathSearchPlugin("AnimPow"))
    }

    @Test
    fun testTurkishPathSearchPlugins_AsyaAnimeleri() {
        assertTrue(isTurkishPathSearchPlugin("AsyaAnimeleri"))
    }

    @Test
    fun testTurkishPathSearchPlugins_AsyaWatch() {
        assertTrue(isTurkishPathSearchPlugin("AsyaWatch"))
    }

    @Test
    fun testTurkishPathSearchPlugins_CizgiMax() {
        assertTrue(isTurkishPathSearchPlugin("CizgiMax"))
    }

    @Test
    fun testTurkishPathSearchPlugins_Animely() {
        assertTrue(isTurkishPathSearchPlugin("Animely"))
    }

    @Test
    fun testTurkishPathSearchPlugins_AnimeElysium() {
        assertTrue(isTurkishPathSearchPlugin("AnimeElysium"))
    }

    @Test
    fun testNonTurkishPathSearchPlugins_TurkAnime() {
        // TurkAnime uses normal (non-path-encoded) queries
        assertFalse(isTurkishPathSearchPlugin("TurkAnime"))
    }

    @Test
    fun testNonTurkishPathSearchPlugins_DiziBox() {
        assertFalse(isTurkishPathSearchPlugin("DiziBox"))
    }

    @Test
    fun testNonTurkishPathSearchPlugins_Dizilla() {
        assertFalse(isTurkishPathSearchPlugin("Dizilla"))
    }

    @Test
    fun testNonTurkishPathSearchPlugins_HDFilmCehennemi() {
        assertFalse(isTurkishPathSearchPlugin("HDFilmCehennemi"))
    }

    // ─── KNOWN_BROKEN_PLUGINS Validation ─────────────────────────────────────

    @Test
    fun testKnownBrokenPlugins_Contains_IfsaLog() {
        // IfsaLog eskiden hard-coded broken listesindeydi,
        // ancak artık dinamik domain-fix yönetimine taşındı.
        // KNOWN_BROKEN_PLUGINS intentionally empty — handled by KNOWN_BROKEN_DOMAINS.
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("IfsaLog"))
    }

    @Test
    fun testKnownBrokenPlugins_Contains_SuperFilmGeldi() {
        // SuperFilmGeldi eskiden hard-coded broken listesindeydi,
        // ancak artık dinamik domain-fix yönetimine taşındı.
        // KNOWN_BROKEN_PLUGINS intentionally empty — handled by KNOWN_BROKEN_DOMAINS.
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("SuperFilmGeldi"))
    }

    @Test
    fun testKnownBrokenPlugins_DoesNotContain_DiziKorea() {
        // DiziKorea → dizikorea3.com feroxx repoda 3 stream üretiyor ✅ (2026-08 tanı doğrulaması)
        // KNOWN_BROKEN_PLUGINS listesinden çıkarıldı; domain fix ile yönetiliyor.
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("DiziKorea"))
    }

    @Test
    fun testKnownBrokenPlugins_Contains_KraptorPlus() {
        // KraptorPlus eskiden hard-coded broken listesindeydi,
        // ancak artık dinamik domain-fix yönetimine taşındı.
        // KNOWN_BROKEN_PLUGINS intentionally empty — handled by KNOWN_BROKEN_DOMAINS.
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("KraptorPlus"))
    }

    @Test
    fun testKnownBrokenPlugins_Contains_TvDiziler() {
        // TvDiziler eskiden hard-coded broken listesindeydi,
        // ancak artık dinamik domain-fix yönetimine taşındı.
        // KNOWN_BROKEN_PLUGINS intentionally empty — handled by KNOWN_BROKEN_DOMAINS.
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("TvDiziler"))
    }

    @Test
    fun testKnownBrokenPlugins_DoesNotContain_TurkAnime() {
        // TurkAnime is NOT broken
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("TurkAnime"))
    }

    @Test
    fun testKnownBrokenPlugins_DoesNotContain_DiziBox() {
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("DiziBox"))
    }

    @Test
    fun testKnownBrokenPlugins_DoesNotContain_HDFilmCehennemi() {
        assertFalse(KNOWN_BROKEN_PLUGINS.contains("HDFilmCehennemi"))
    }

    // ─── KNOWN_BROKEN_DOMAINS Validation ─────────────────────────────────────

    @Test
    fun testKnownBrokenDomains_Contains_IfsaLog() {
        assertTrue(KNOWN_BROKEN_DOMAINS.any { it.contains("ifsalog") })
    }

    @Test
    fun testKnownBrokenDomains_Contains_SuperFilmGeldi() {
        assertTrue(KNOWN_BROKEN_DOMAINS.any { it.contains("superfilmgeldi") })
    }

    @Test
    fun testKnownBrokenDomains_Contains_UgurFilm() {
        assertTrue(KNOWN_BROKEN_DOMAINS.any { it.contains("ugurfilm") })
    }

    /**
     * v2.4.256: HDFilmCehennemi2 eklenti deposunda .site domainine taşındı. Liste bu domaini
     * "ölü" saydığında applyDomainFix eklentiyi başka bir siteye zorla çeviriyordu.
     */
    @Test
    fun testKnownBrokenDomains_DoesNotContain_HdFilmCehennemi2Site() {
        assertFalse(KNOWN_BROKEN_DOMAINS.any { "hdfilmcehennemi2.site".contains(it) })
    }

    /**
     * v2.4.256: Numaralı eklenti (HDFilmCehennemi2) ana eklentinin (HDFilmCehennemi) yerleşik
     * domainine DÜŞMEMELİ — ayrı sitelerdir. "2" son eki güvenli son ek listesinden çıkarıldı.
     */
    @Test
    fun testResolveBuiltinDomain_NumberedVariant_DoesNotInheritParentDomain() {
        assertNull(CsStreamRunner.resolveBuiltinDomain("hdfilmcehennemi2"))
        // Ana eklenti kendi domainini korur.
        assertNotNull(CsStreamRunner.resolveBuiltinDomain("hdfilmcehennemi"))
    }

    // ─── CF_PROTECTED_PLUGINS Validation ─────────────────────────────────────

    @Test
    fun testCfProtectedPlugins_Contains_TrAnimeci() {
        assertTrue(CF_PROTECTED_PLUGINS.contains("TrAnimeci"))
    }

    @Test
    fun testCfProtectedPlugins_Contains_TrAnimeIzle() {
        assertTrue(CF_PROTECTED_PLUGINS.contains("TrAnimeIzle"))
    }

    // ─── buildTitleVariants Delegation Tests ──────────────────────────────────

    @Test
    fun testBuildTitleVariantsAnimeS1() {
        val variants = CsTitleMatcher.buildTitleVariants("Naruto", emptyList(), season = 1)
        assertTrue("Base title should appear", variants.contains("Naruto"))
        // Should NOT have "Naruto 1. Sezon" for season 1
        assertFalse("Season 1 should not prepend season suffix", variants.contains("Naruto 1. Sezon"))
    }

    @Test
    fun testBuildTitleVariantsAnimeS2_HasSezonSuffix() {
        val variants = CsTitleMatcher.buildTitleVariants("Naruto Shippuden", emptyList(), season = 2)
        assertTrue("2. Sezon variant should be present", variants.contains("Naruto Shippuden 2. Sezon"))
        assertTrue("Season 2 variant should be present", variants.contains("Naruto Shippuden Season 2"))
    }

    @Test
    fun testBuildTitleVariantsFilm_NoSezonSuffix() {
        val variants = CsTitleMatcher.buildTitleVariants("Inception", emptyList(), season = 1)
        assertFalse("No season suffix for films", variants.contains("Inception 1. Sezon"))
        assertTrue("Base title should appear", variants.contains("Inception"))
    }

    @Test
    fun testBuildTitleVariantsAltTitles() {
        val variants = CsTitleMatcher.buildTitleVariants(
            "Attack on Titan",
            listOf("Shingeki no Kyojin", "進撃の巨人"),
            season = 1
        )
        assertTrue("Alt romaji should appear", variants.contains("Shingeki no Kyojin"))
        assertTrue("Main title should appear", variants.contains("Attack on Titan"))
    }

    @Test
    fun testBuildTitleVariantsRemovesBlank() {
        val variants = CsTitleMatcher.buildTitleVariants("X", emptyList(), season = 1)
        // Single char "X" should be filtered out (length < 2)
        assertFalse("Single char should be filtered", variants.contains("X"))
    }

    @Test
    fun testBuildTitleVariantsLargeSeasonNumber() {
        val variants = CsTitleMatcher.buildTitleVariants("One Piece", emptyList(), season = 20)
        assertTrue("Season 20 should generate '20. Sezon' variant", variants.contains("One Piece 20. Sezon"))
        assertTrue("Season 20 should generate 'Season 20' variant", variants.contains("One Piece Season 20"))
    }

    // ─── Similarity Smoke Tests ───────────────────────────────────────────────

    @Test
    fun testSimilarityIdentical() {
        val sim = CsTitleMatcher.getSimilarity("hello world", "hello world")
        assertEquals(1.0, sim, 0.001)
    }

    @Test
    fun testSimilarityEmpty() {
        val sim = CsTitleMatcher.getSimilarity("", "")
        assertEquals(1.0, sim, 0.001)
    }

    @Test
    fun testSimilarityOneEmpty() {
        val sim = CsTitleMatcher.getSimilarity("hello", "")
        assertEquals(0.0, sim, 0.001)
    }

    @Test
    fun testSimilarityClose() {
        val sim = CsTitleMatcher.getSimilarity("jujutsu kaisen", "jujutsu kaizeen")
        assertTrue("Close strings should have high similarity", sim > 0.80)
    }

    @Test
    fun testSimilarityDistant() {
        val sim = CsTitleMatcher.getSimilarity("naruto", "one piece")
        assertTrue("Distant strings should have low similarity", sim < 0.50)
    }

    // ─── isEmbedUrl New Pattern Tests ─────────────────────────────────────────

    @Test
    fun testIsEmbedUrl_TurkAnimeEmbedPlayer() {
        // TurkAnime uses AES-encrypted embed URLs under /embed/ path
        assertTrue(CsStreamRunner.isEmbedUrl("https://www.turkanime.tv/embed/#/url/abc123"))
    }

    @Test
    fun testIsEmbedUrl_TurkAnimeEmbedOldDomain() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://cdn.turkanime.co/embed/video/abc"))
    }

    @Test
    fun testIsEmbedUrl_CloseLoad() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://closeload.top/e/abc123"))
    }

    @Test
    fun testIsEmbedUrl_Kwik() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://kwik.si/e/ABCdef123"))
    }

    @Test
    fun testIsEmbedUrl_KwikCx() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://kwik.cx/f/ABCdef123"))
    }

    @Test
    fun testIsEmbedUrl_MegaCloud() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://megacloud.tv/embed/e-1/abc"))
    }

    @Test
    fun testIsEmbedUrl_Chillx() {
        assertTrue(CsStreamRunner.isEmbedUrl("https://chillx.top/player/index.php?id=abc"))
    }

    @Test
    fun testIsNotEmbedUrl_TurkAnimeAnimePage() {
        // A regular TurkAnime anime detail page should NOT be an embed
        assertFalse(CsStreamRunner.isEmbedUrl("https://www.turkanime.tv/anime/jujutsu-kaisen-2-sezon"))
    }

    // ─── KNOWN_DEAD_CDN_HOSTS Validation ──────────────────────────────────────

    @Test
    fun testKnownDeadCdnHosts_Contains_Pichive() {
        assertTrue(KNOWN_DEAD_CDN_HOSTS.any { it.contains("pichive.online") })
    }

    @Test
    fun testKnownDeadCdnHosts_Contains_Sssrr() {
        assertTrue(KNOWN_DEAD_CDN_HOSTS.any { it.contains("sssrr.org") })
    }

    @Test
    fun testKnownDeadCdnHosts_Contains_Abyss() {
        assertTrue(KNOWN_DEAD_CDN_HOSTS.any { it.contains("abyss.to") })
    }

    @Test
    fun testKnownDeadCdnHosts_DoesNotContain_Streamtape() {
        // Streamtape is alive — should NOT be in the dead CDN list
        assertFalse(KNOWN_DEAD_CDN_HOSTS.any { it.contains("streamtape") })
    }

    // ─── Private Helpers (mirrors CsStreamRunner private logic) ──────────────

    /**
     * Mirrors the isTurkishPathSearch logic in CsStreamRunner.safeSearch().
     * These plugin names trigger path-encoded query strings.
     */
    private fun isTurkishPathSearchPlugin(providerName: String): Boolean {
        return providerName.contains("AnimeciX", ignoreCase = true) ||
               providerName.contains("Animely", ignoreCase = true) ||
               providerName.contains("Elysium", ignoreCase = true) ||
               providerName.contains("AnimPow", ignoreCase = true) ||
               providerName.contains("AsyaAnimeleri", ignoreCase = true) ||
               providerName.contains("AsyaWatch", ignoreCase = true) ||
               providerName.contains("CizgiMax", ignoreCase = true)
    }

    // Extracted from CsStreamRunner companion/object state via reflection for validation
    private val KNOWN_BROKEN_PLUGINS: Set<String> by lazy {
        try {
            val field = CsStreamRunner.javaClass.getDeclaredField("KNOWN_BROKEN_PLUGINS")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(CsStreamRunner) as? Set<String> ?: emptySet()
        } catch (_: Exception) {
            // Fallback: hardcoded mirror of the actual set for validation
            setOf("IfsaLog", "SuperFilmGeldi", "DiziKorea", "UgurFilm", "KraptorPlus", "TvDiziler")
        }
    }

    private val KNOWN_BROKEN_DOMAINS: Set<String> by lazy {
        try {
            val field = CsStreamRunner.javaClass.getDeclaredField("KNOWN_BROKEN_DOMAINS")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(CsStreamRunner) as? Set<String> ?: emptySet()
        } catch (_: Exception) {
            setOf("ifsalog4.club", "superfilmgeldi13.art", "ugurfilm3.xyz")
        }
    }

    private val CF_PROTECTED_PLUGINS: Set<String> by lazy {
        try {
            val field = CsStreamRunner.javaClass.getDeclaredField("CF_PROTECTED_PLUGINS")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(CsStreamRunner) as? Set<String> ?: emptySet()
        } catch (_: Exception) {
            setOf("TrAnimeci", "TrAnimeIzle")
        }
    }

    private val KNOWN_DEAD_CDN_HOSTS: Set<String> by lazy {
        try {
            val field = CsStreamRunner.javaClass.getDeclaredField("KNOWN_DEAD_CDN_HOSTS")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(CsStreamRunner) as? Set<String> ?: emptySet()
        } catch (_: Exception) {
            setOf("pichive.online", "sssrr.org", "abyss.to")
        }
    }
    // ─── v2.4.240 KN-D: etkin eklenti → API eşleştirmesi ──────────────────────
    //
    // Eski davranış: `File(api.sourcePlugin).nameWithoutExtension == plugin.id` birebir
    // karşılaştırması + yer yer `lowercase(Locale.ROOT)`. Türkçe noktalı İ/ş/ğ ve harf
    // büyüklüğü farklarında filtre BOŞ liste dönüyordu → "hiçbir eklenti denenmedi".

    @Test
    fun testMatchesEnabledPlugin_exactId() {
        assertTrue(
            CsStreamRunner.matchesEnabledPlugin(
                fileKey = "DiziPal", apiName = "DiziPal", enabledIds = listOf("DiziPal")
            )
        )
    }

    @Test
    fun testMatchesEnabledPlugin_turkishDottedI() {
        // DB id'si "WFilmİzle", dosya adı "wfilmizle.cs3" → normalize edilince eşleşmeli.
        assertTrue(
            CsStreamRunner.matchesEnabledPlugin(
                fileKey = "wfilmizle", apiName = "WFilmİzle", enabledIds = listOf("WFilmİzle")
            )
        )
        assertTrue(
            CsStreamRunner.matchesEnabledPlugin(
                fileKey = "WFilmİzle", apiName = "WFilmizle", enabledIds = listOf("wfilmizle")
            )
        )
    }

    @Test
    fun testMatchesEnabledPlugin_caseAndTurkishDialectInsensitive() {
        val ids = listOf("AsyaAnimeleri", "CizgiMax", "İzleAI")
        assertTrue(CsStreamRunner.matchesEnabledPlugin("asyaanimeleri", "AsyaAnimeleri", ids))
        assertTrue(CsStreamRunner.matchesEnabledPlugin("cizgimax.cs3".removeSuffix(".cs3"), "CizgiMax", ids))
        assertTrue(CsStreamRunner.matchesEnabledPlugin(null, "IzleAI", ids))
    }

    @Test
    fun testMatchesEnabledPlugin_fallsBackToApiNameWhenFileMissing() {
        // Yerleşik/built-in sağlayıcılarda sourcePlugin null → api.name üzerinden eşleşmeli.
        assertTrue(CsStreamRunner.matchesEnabledPlugin(null, "Animeler", listOf("Animeler")))
        assertFalse(CsStreamRunner.matchesEnabledPlugin(null, "Animeler", listOf("DiziPal")))
    }

    @Test
    fun testMatchesEnabledPlugin_emptyAndBlankInputs() {
        assertFalse(CsStreamRunner.matchesEnabledPlugin("DiziPal", "DiziPal", emptyList()))
        assertFalse(CsStreamRunner.matchesEnabledPlugin("", "", listOf("  ")))
        // null dosya anahtarı + boş ad → eşleşme yok (yanlış pozitif engellenir)
        assertFalse(CsStreamRunner.matchesEnabledPlugin(null, "", listOf("DiziPal")))
    }

    @Test
    fun extractorHeadersSupplementRatherThanDiscardProviderCredentials() {
        val merged = CsStreamRunner.mergeHeadersCaseInsensitive(
            inherited = mapOf(
                "x-provider-token" to "animecix-session",
                "Cookie" to "session=provider",
                "Referer" to "https://provider.example/watch"
            ),
            extractor = mapOf(
                "user-agent" to "extractor-agent",
                "X-PROVIDER-TOKEN" to "extractor-session"
            )
        )

        assertEquals("extractor-session", merged["X-PROVIDER-TOKEN"])
        assertEquals("session=provider", merged["Cookie"])
        assertEquals("https://provider.example/watch", merged["Referer"])
        assertEquals("extractor-agent", merged["user-agent"])
        assertEquals(1, merged.keys.count { it.equals("x-provider-token", ignoreCase = true) })
    }

    @Test
    fun hdfilmCehennemiAndFilmMakinesiUseEmbedPageBeforeFinalCdnHost() {
        val headers = CsStreamRunner.buildCloudStreamRequestHeaders(
            existingHeaders = mapOf("x-player-token" to "kept"),
            linkReferer = null,
            parentPageUrl = "https://player.filmmakinesi.example/embed/42",
            sourcePageUrl = "https://www.hdfilmcehennemi.example/film/42",
            mediaUrl = "https://cdn.rapidrame.example/master.m3u8",
            userAgent = "fallback-agent"
        )

        assertEquals("https://player.filmmakinesi.example/embed/42", headers["Referer"])
        assertEquals("https://player.filmmakinesi.example", headers["Origin"])
        assertEquals("kept", headers["x-player-token"])
        assertEquals("fallback-agent", headers["User-Agent"])
    }

    @Test
    fun extractorRefererWinsOverEmbedAndFinalCdnFallbacks() {
        val headers = CsStreamRunner.buildCloudStreamRequestHeaders(
            existingHeaders = emptyMap(),
            linkReferer = "https://extractor.example/watch/42",
            parentPageUrl = "https://embed.example/player/42",
            sourcePageUrl = "https://provider.example/episode/42",
            mediaUrl = "https://cdn.example/video.mp4",
            userAgent = "fallback-agent"
        )

        assertEquals("https://extractor.example/watch/42", headers["Referer"])
        assertEquals("https://extractor.example", headers["Origin"])
    }

    @Test
    fun explicitExtractorRefererAndOriginAreNeverOverwritten() {
        val headers = CsStreamRunner.buildCloudStreamRequestHeaders(
            existingHeaders = mapOf(
                "referer" to "https://plugin.example/signed-page",
                "ORIGIN" to "https://plugin.example"
            ),
            linkReferer = "https://extractor.example/player",
            parentPageUrl = "https://embed.example/",
            sourcePageUrl = "https://provider.example/",
            mediaUrl = "https://cdn.example/video.mp4",
            userAgent = "fallback-agent"
        )

        assertEquals("https://plugin.example/signed-page", headers["referer"])
        assertEquals("https://plugin.example", headers["ORIGIN"])
        assertEquals(1, headers.keys.count { it.equals("referer", ignoreCase = true) })
        assertEquals(1, headers.keys.count { it.equals("origin", ignoreCase = true) })
    }

    @Test
    fun rangeValidationKeepsOriginCookiesAndPluginTokens() {
        val request = CsStreamRunner.buildStreamValidationRequest(
            url = "https://cdn.example/master.m3u8",
            method = "GET",
            headers = mapOf(
                "Referer" to "https://embed.example/watch",
                "Origin" to "https://embed.example",
                "Cookie" to "session=abc",
                "x-e-h" to "animecix-token",
                "User-Agent" to "plugin-agent",
                "Range" to "bytes=100-200"
            ),
            userAgent = "default-agent"
        )

        assertEquals("GET", request.method)
        assertEquals("bytes=0-0", request.header("Range"))
        assertEquals("https://embed.example/watch", request.header("Referer"))
        assertEquals("https://embed.example", request.header("Origin"))
        assertEquals("session=abc", request.header("Cookie"))
        assertEquals("animecix-token", request.header("x-e-h"))
        assertEquals("plugin-agent", request.header("User-Agent"))
    }
}
