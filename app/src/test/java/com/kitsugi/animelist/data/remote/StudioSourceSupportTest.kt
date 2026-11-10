package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioSourceSupportTest {
    @Test
    fun explicitStudioNamespaceWinsOverParentMediaSource() {
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource("jikan", "anilist", MediaType.Anime)
        )
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource("mal", "anilist", MediaType.Anime)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource("tmdb", "anilist", MediaType.Movie)
        )
        assertEquals(
            "shikimori",
            StudioSourceSupport.resolveClickSource("shikimori", "jikan", MediaType.Anime)
        )
        assertEquals(
            "bangumi",
            StudioSourceSupport.resolveClickSource("bangumi", "jikan", MediaType.Anime)
        )
    }

    @Test
    fun tmdbNetworksUseTheirOwnNamespaceInsteadOfCompanyIds() {
        assertEquals(StudioSourceSupport.TMDB_NETWORK_SOURCE, StudioSourceSupport.canonicalSource("TMDB-Network"))
        assertEquals(
            StudioSourceSupport.TMDB_NETWORK_SOURCE,
            StudioSourceSupport.resolveClickSource("tmdb", "simkl", MediaType.TvShow, StudioRole.NETWORK)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource("tmdb", "simkl", MediaType.TvShow, StudioRole.PRODUCER)
        )
        assertEquals(
            StudioSourceSupport.TMDB_NETWORK_SOURCE,
            StudioSourceSupport.resolveClickSource(StudioSourceSupport.TMDB_NETWORK_SOURCE, "tmdb", MediaType.TvShow)
        )
        assertEquals(
            "tmdb_network",
            StudioSourceSupport.resolveClickSource(null, "anilist", MediaType.TvShow, StudioRole.NETWORK)
        )
        assertEquals(
            "bangumi",
            StudioSourceSupport.resolveClickSource(null, "bangumi", MediaType.Anime, StudioRole.NETWORK)
        )
    }

    @Test
    fun providerFallbackIsUsedOnlyWhenStudioHasNoNamespace() {
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource(null, "MyAnimeList", MediaType.Anime)
        )
        assertEquals(
            "anilist",
            StudioSourceSupport.resolveClickSource("", "AniList", MediaType.Manga)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource(null, "unknown", MediaType.Movie)
        )
        // Simkl's direct details contain no company IDs; its studio chips are enriched from TMDB.
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource(null, "simkl", MediaType.TvShow, StudioRole.PRODUCER)
        )
    }

    @Test
    fun identityGuardAcceptsLegalSuffixesButRejectsDifferentCompanies() {
        assertTrue(StudioSourceSupport.namesMatch("BONES", "BONES, Inc."))
        assertTrue(StudioSourceSupport.namesMatch("Kyoto Animation Co., Ltd.", "Kyoto Animation"))
        assertFalse(StudioSourceSupport.namesMatch("Aniplex", "BONES"))
    }

    @Test
    fun productionCommitteesAreNotCompanyPages() {
        assertTrue(
            StudioSourceSupport.isCommitteeName(
                "とらぶるダークネス2nd製作委員会"
            )
        )
        assertTrue(StudioSourceSupport.isCommitteeName("とある魔術の禁書目録製作委員会"))
        assertTrue(StudioSourceSupport.isCommitteeName("某动画制作委员会"))
        assertFalse(StudioSourceSupport.isCommitteeName("XEBEC"))
        assertFalse(StudioSourceSupport.isCommitteeName("ジェネオン・ユニバーサル・エンターテイメント"))
    }

    @Test
    fun orgNameVariantsBridgeJapaneseAndLatinSpellings() {
        // Parantez içi takma ad ayrı bir arama yazımı olarak sunulur.
        val variants = StudioSourceSupport.orgNameVariants(
            "ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)"
        )
        assertTrue(variants.any { it.contains("ジェネオン") })
        assertTrue(variants.any { it.contains("NBCユニバーサル") })
        // Sözlük köprüsü: bilinen kurumun küresel İngilizce adı da denenir.
        assertTrue(variants.any { it.equals("NBCUniversal Entertainment Japan", ignoreCase = true) })

        // Infobox'ın kapattığı parantez eksik olsa bile takma ad yakalanır.
        val unterminated = StudioSourceSupport.orgNameVariants(
            "ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン"
        )
        assertTrue(unterminated.any { it.contains("NBCユニバーサル") })
        assertTrue(unterminated.any { it.equals("NBCUniversal Entertainment Japan", ignoreCase = true) })
    }

    @Test
    fun dictionaryBridgeMakesCrossLanguageNamesMatch() {
        // Aynı kurumun farklı dildeki yazımları "farklı şirket" sayılmamalı; aksi halde
        // doğru kurum sayfası "isim uyuşmadı" diye reddediliyordu.
        assertTrue(StudioSourceSupport.namesMatch("SHUEISHA", "集英社"))
        assertTrue(StudioSourceSupport.namesMatch("集英社", "Shueisha"))
        assertTrue(
            StudioSourceSupport.namesMatch(
                "ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)",
                "NBCUniversal Entertainment Japan"
            )
        )
        // Farklı şirketler eşleşmemeye devam eder.
        assertFalse(StudioSourceSupport.namesMatch("SHUEISHA", "XEBEC"))
    }

    @Test
    fun sameCompanyHasSingleDictionaryIdentity() {
        val shueisha = StudioSourceSupport.orgIdentityKey("SHUEISHA")
        val shueishaJp = StudioSourceSupport.orgIdentityKey("集英社")
        assertEquals(shueisha, shueishaJp)
        assertTrue(shueisha != null && shueisha.startsWith("bangumi_"))

        // Üretim komitesi sözlükte yok → kendi kimliği de yok (tıklanamaz çip).
        assertEquals(null, StudioSourceSupport.orgIdentityKey("とらぶるダークネス2nd製作委員会"))
    }

    @Test
    fun latinCompanyNameResolvesToBangumiLocalName() {
        // Infobox kurumu Latin adla verir (SHUEISHA); Bangumi kaydı 集英社 adıyla açılır.
        val variants = StudioSourceSupport.orgNameVariants("SHUEISHA")
        assertTrue(variants.any { it.contains("集英社") })
    }
}
