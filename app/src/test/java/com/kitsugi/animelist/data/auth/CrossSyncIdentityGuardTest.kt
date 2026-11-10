package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSyncIdentityGuardTest {

    @Test
    fun romanizationAndPunctuationVariantsAreRelated() {
        // Shikimori romaji vs AniList romaji (aynı MAL ID, aynı yıl): 2026-10-08 raporundaki vaka.
        assertTrue(
            CrossSyncIdentityGuard.titlesVariantRelated(
                "Mayoiga no Oneesan The Animation",
                "Mayohiga no Onee-san THE ANIMATION"
            )
        )
    }

    @Test
    fun numericTitleMatchesItsEnglishExpansion() {
        // Simkl "86" ↔ AniList "86: Eighty Six" (aynı MAL ID, aynı yıl): raporda yanlış biçimde izole edilmişti.
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("86", "86: Eighty Six"))
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("86 Part 2", "86: Eighty Six Part 2"))
    }

    @Test
    fun differentSeasonNumbersAreNeverRelated() {
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Re:Zero Part 2", "Re:Zero Part 3"))
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("86 Part 2", "86: Eighty Six"))
    }

    @Test
    fun aliasesIncludeNativeJapaneseTitle() {
        // Bangumi satırı: görünen başlık Çince, İngilizce yok; kimlik doğrulamasının tutunacağı
        // tek ortak başlık özgün Japonca ad. aliasesOf onu içermezse Bangumi kökenli kayıtların
        // MAL/AniList doğrulaması (ve dolayısıyla canlı yayılımı) neredeyse hep düşüyordu.
        val entry = MediaEntry(
            id = 1,
            title = "命运石之门",
            type = MediaType.Anime,
            status = WatchStatus.Watching,
            score = null,
            progress = 3,
            total = 24,
            source = "bangumi",
            malId = 500_010_380,
            titleEnglish = null,
            titleJapanese = "シュタインズ・ゲート"
        )
        val aliases = CrossSyncIdentityGuard.aliasesOf(entry)
        assertTrue(aliases.contains("シュタインズ・ゲート"))
        assertTrue(
            CrossSyncIdentityGuard.titlesAreIdentityEquivalent(
                aliases,
                listOf("Steins;Gate", "シュタインズ・ゲート")
            )
        )
    }

    @Test
    fun genuinelyDifferentTitlesStillRejected() {
        // Kitsu'daki yanlış MAL eşlemesi (Koi wo Shita no wa ↔ Koe no Katachi Specials): yazma durmalı.
        assertFalse(
            CrossSyncIdentityGuard.titlesVariantRelated(
                "Koi wo Shita no wa",
                "Koe no Katachi Specials"
            )
        )
    }

    @Test
    fun multiplicationSignAndTrailingTypeQualifierMatchOfficialAliases() {
        assertTrue(CrossSyncIdentityGuard.titlesAreIdentityEquivalent("kiss×sis", "Kiss x Sis (TV)"))
        assertTrue(CrossSyncIdentityGuard.titlesAreIdentityEquivalent("High School D×D", "High School DxD (OVA)"))
        assertFalse(CrossSyncIdentityGuard.titlesAreIdentityEquivalent("Kiss x Sis 2", "kiss×sis (TV)"))
    }

    @Test
    fun ampersandAndQKyuuVariantsDoNotRelaxSeasonChecks() {
        assertTrue(CrossSyncIdentityGuard.titlesAreIdentityEquivalent("Love & Peace", "Love and Peace"))
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("Evangelion Shin Movie: Kyuu", "Evangelion Shin Gekijouban: Q"))
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Evangelion 2: Kyuu", "Evangelion 3: Q"))
    }
}
