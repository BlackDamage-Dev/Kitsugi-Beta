package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle

/**
 * [BangumiEntityNameResolver]'ın saf (ağ gerektirmeyen) yardımcıları: birebir (normalize)
 * ad eşleşmesi, eşleme anahtarları ve aliased AniList sorgusu kurulumu.
 *
 * Sözleşme: yanlış kişinin adı asla gösterilmez — yalnızca native / full / alternative
 * adlarından biri Bangumi özgün adıyla BİREBİR eşleşirse varyantlar kabul edilir.
 */
class BangumiEntityNameResolverTest {

    private fun variant(full: String, native: String?, vararg alternatives: String) =
        KitsugiAniListPersonBridge.NameVariant(
            full = full,
            native = native,
            alternatives = alternatives.toList()
        )

    @Test
    fun matchKeysFor_normalizesPunctuationWidthAndCase() {
        val keys = BangumiEntityNameResolver.matchKeysFor("ニーナ・タッカー", null)
        assertTrue(keys.isNotEmpty())
        // Noktalama / boşluk / büyük-küçük harf farkları anahtarı değiştirmez.
        assertEquals(
            BangumiEntityNameResolver.matchKeysFor(" ニーナ・タッカー ", null),
            keys
        )
        assertEquals(
            BangumiEntityNameResolver.matchKeysFor(null, "ニーナ・タッカー"),
            keys
        )
    }

    @Test
    fun matchKeysFor_blankNamesProduceNoKeys() {
        assertTrue(BangumiEntityNameResolver.matchKeysFor(null, null).isEmpty())
        assertTrue(BangumiEntityNameResolver.matchKeysFor("   ", " ").isEmpty())
    }

    @Test
    fun pickMatch_acceptsExactNativeMatch() {
        val nina = variant("Nina Tucker", "ニーナ・タッカー")
        val keys = BangumiEntityNameResolver.matchKeysFor("ニーナ・タッカー", null)
        assertEquals(nina, BangumiEntityNameResolver.pickMatch(keys, listOf(nina)))
    }

    @Test
    fun pickMatch_acceptsFullOrAlternativeMatch() {
        val kiyoko = variant("清子", "清子", "Kiyoko")
        val keys = BangumiEntityNameResolver.matchKeysFor("清子", null)
        assertEquals(kiyoko, BangumiEntityNameResolver.pickMatch(keys, listOf(kiyoko)))

        val byAlternative = variant("Kiyoko", "清子", "Kiyoko")
        val altKeys = BangumiEntityNameResolver.matchKeysFor("Kiyoko", null)
        assertEquals(byAlternative, BangumiEntityNameResolver.pickMatch(altKeys, listOf(byAlternative)))
    }

    @Test
    fun pickMatch_rejectsPartialAndSuperstringMatches() {
        val nina = variant("Nina Tucker", "ニーナ・タッカー")
        val partialKeys = BangumiEntityNameResolver.matchKeysFor("ニーナ", null)
        assertNull(BangumiEntityNameResolver.pickMatch(partialKeys, listOf(nina)))

        val superKeys = BangumiEntityNameResolver.matchKeysFor("ニーナ・タッカー (FMA)", null)
        assertNull(BangumiEntityNameResolver.pickMatch(superKeys, listOf(nina)))
    }

    @Test
    fun pickMatch_emptyKeysNeverMatch() {
        val nina = variant("Nina Tucker", "ニーナ・タッカー")
        assertNull(BangumiEntityNameResolver.pickMatch(emptySet(), listOf(nina)))
    }

    @Test
    fun aliasedQuery_buildsCharacterAndStaffPages() {
        val (characterQuery, characterVars) = BangumiEntityNameResolver.aliasedQuery(
            BangumiEntityNameResolver.Kind.CHARACTER,
            listOf(
                BangumiEntityNameResolver.Request(BangumiEntityNameResolver.Kind.CHARACTER, 1, "清子"),
                BangumiEntityNameResolver.Request(BangumiEntityNameResolver.Kind.CHARACTER, 2, "ニーナ・タッカー")
            )
        )
        assertTrue(characterQuery.contains("c0: Page(page: 1, perPage: 5) { characters(search: \$s0)"))
        assertTrue(characterQuery.contains("c1: Page(page: 1, perPage: 5) { characters(search: \$s1)"))
        assertTrue(characterQuery.contains("name { full native alternative }"))
        assertEquals("清子", characterVars.optString("s0"))
        assertEquals("ニーナ・タッカー", characterVars.optString("s1"))

        val (staffQuery, staffVars) = BangumiEntityNameResolver.aliasedQuery(
            BangumiEntityNameResolver.Kind.PERSON,
            listOf(BangumiEntityNameResolver.Request(BangumiEntityNameResolver.Kind.PERSON, 7, "氷上恭子"))
        )
        assertTrue(staffQuery.contains("p0: Page(page: 1, perPage: 5) { staff(search: \$s0)"))
        assertEquals("氷上恭子", staffVars.optString("s0"))
    }

    @Test
    fun aliasedQuery_fallsBackToListNameWhenNativeMissing() {
        val (_, vars) = BangumiEntityNameResolver.aliasedQuery(
            BangumiEntityNameResolver.Kind.CHARACTER,
            listOf(BangumiEntityNameResolver.Request(BangumiEntityNameResolver.Kind.CHARACTER, 3, null, "Okazaki Ushio"))
        )
        assertEquals("Okazaki Ushio", vars.optString("s0"))
    }

    @Test
    fun applyResolvedLatinTitle_fillsVariantsWithoutTouchingCjkWhenNoLatin() {
        val relation = KitsugiRelation(
            malId = 500_000_000 + 7947,
            title = "CLANNAD もうひとつの世界 智代編",
            relationType = "Sequel",
            imageUrl = null,
            mediaType = com.kitsugi.animelist.model.MediaType.Anime,
            source = "bangumi"
        )
        val unchanged = KitsugiBangumiDetailClient.applyResolvedLatinTitle(
            relation,
            BangumiTitleCache.LatinTitles(null, null, "CLANNAD もうひとつの世界 智代編")
        )
        assertEquals(relation, unchanged)

        val enriched = KitsugiBangumiDetailClient.applyResolvedLatinTitle(
            relation,
            BangumiTitleCache.LatinTitles(
                romaji = "Clannad: Mou Hitotsu no Sekai - Tomoyo-hen",
                english = "Clannad: Another World, Tomoyo Chapter",
                native = "CLANNAD もうひとつの世界 智代編"
            )
        )
        assertEquals("Clannad: Mou Hitotsu no Sekai - Tomoyo-hen", enriched.title)
        assertEquals("Clannad: Mou Hitotsu no Sekai - Tomoyo-hen", enriched.titleRomaji)
        assertEquals("Clannad: Another World, Tomoyo Chapter", enriched.titleEnglish)
        assertEquals("CLANNAD もうひとつの世界 智代編", enriched.titleJapanese)

        // İngilizce tercih zinciri: english → romaji; romaji tercih zinciri: romaji.
        assertEquals("Clannad: Another World, Tomoyo Chapter", enriched.getDisplayTitle("ENGLISH"))
        assertEquals("Clannad: Mou Hitotsu no Sekai - Tomoyo-hen", enriched.getDisplayTitle("ROMAJI"))
        assertEquals("CLANNAD もうひとつの世界 智代編", enriched.getDisplayTitle("NATIVE"))
    }

    @Test
    fun nameVariants_hasLatinOnlyWhenLatinPresent() {
        assertTrue(BangumiEntityNameResolver.NameVariants("Ushio", null, null).hasLatin)
        assertTrue(BangumiEntityNameResolver.NameVariants(null, "Ushio Okazaki", null).hasLatin)
        assertTrue(!BangumiEntityNameResolver.NameVariants(null, null, "岡崎うしお").hasLatin)
    }
}
