package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Listeye Ekle" akışındaki tekrar kontrolü (`MediaEntry.matches`) senaryoları.
 *
 * Hata raporu: Bangumi (CLANNAD 〜AFTER STORY〜, subject 876) detay sayfasında
 * "Listeye Ekle" → "zaten listende var" deniyor ama kullanıcı kaydı Listem'de
 * bulamıyordu. Kök nedenlerden biri, eşlemenin yalnızca ekranda GÖRÜNEN başlığa
 * (`result.title` — başlık diline göre değişir) bakmasıydı; diğeri de çapraz
 * MAL-ID eşleşmesinin aday kaydı isimlendirmeden reddetmesi.
 */
class MediaEntryMatchesTest {

    private fun entry(
        title: String,
        malId: Int? = null,
        year: Int? = null,
        source: String = "manual",
        type: MediaType = MediaType.Anime,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        tmdbId: Int? = null
    ) = MediaEntry(
        id = 1,
        title = title,
        subtitle = "",
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        year = year,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        tmdbId = tmdbId
    )

    private fun result(
        title: String,
        malId: Int,
        year: Int? = null,
        source: String = "bangumi",
        type: MediaType = MediaType.Anime,
        realMalId: Int? = null,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        titleRomaji: String? = null,
        tmdbId: Int? = null
    ) = JikanSearchResult(
        malId = malId,
        title = title,
        subtitle = "TV",
        type = type,
        total = 24,
        score = 9,
        isAdult = false,
        imageUrl = null,
        year = year,
        source = source,
        realMalId = realMalId,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        titleRomaji = titleRomaji,
        tmdbId = tmdbId
    )

    @Test
    fun `bangumi result with resolved MAL id matches the MAL copy in the list`() {
        // Rapor senaryosu: Bangumi AS kaydı (stableId 500M+876), detay zenginleştirmesiyle
        // çözülen gerçek MAL ID (5681) üzerinden listedeki MAL kopyasıyla eşleşmeli.
        val asBangumi = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681
        )
        val malCopy = entry(
            title = "Clannad: After Story",
            malId = 5681,
            year = 2008,
            source = "mal"
        )
        assertTrue(malCopy.matches(asBangumi))
    }

    @Test
    fun `title fallback matches across all title variants not just display title`() {
        // Sonuç başlığı olarak görünen (seçili dile göre değişen) alan Japonca olsa bile,
        // romaji/English varyantı listedeki kayıtla eşleşmeli.
        val asResult = result(
            title = "クラナド ～アフターストーリー～",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            titleJapanese = "クラナド ～アフターストーリー～",
            titleRomaji = "Clannad: After Story"
        )
        val storedLatin = entry(
            title = "CLANNAD 〜AFTER STORY〜",
            year = 2008,
            source = "bangumi"
        )
        assertTrue(storedLatin.matches(asResult))
    }

    @Test
    fun `season one and after story never match each other`() {
        val s1 = entry(
            title = "Clannad",
            malId = 2167,
            year = 2007,
            source = "mal",
            titleEnglish = "Clannad",
            titleJapanese = "クラナド"
        )
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681,
            titleRomaji = "Clannad: After Story",
            titleJapanese = "クラナド ～アフターストーリー～"
        )
        assertFalse(s1.matches(asResult))
    }

    @Test
    fun `tmdb id collision is an identity match`() {
        val withTmdb = entry(title = "Farklı Başlık", source = "simkl", tmdbId = 2167)
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            source = "bangumi",
            tmdbId = 2167
        )
        assertTrue(withTmdb.matches(asResult))
    }

    @Test
    fun `same bangumi stableId is an identity match regardless of title`() {
        val stored = entry(title = "Eski Kayıtlı Ad", malId = 500_000_876, source = "bangumi")
        val asResult = result(title = "Clannad: After Story", malId = 500_000_876, source = "bangumi")
        assertTrue(stored.matches(asResult))
    }

    @Test
    fun `source scoped Bangumi lookup does not borrow a matching Simkl entry`() {
        val bangumiResult = result(
            title = "Steins;Gate",
            malId = 500_000_876,
            year = 2011,
            source = "bangumi",
            realMalId = 9253,
            tmdbId = 12345
        )
        val simklCopy = entry(
            title = "Steins;Gate",
            malId = 987654,
            year = 2011,
            source = "simkl",
            tmdbId = 12345
        )

        assertTrue(simklCopy.matches(bangumiResult))
        assertFalse(simklCopy.matchesInSource(bangumiResult))
        assertNull(listOf(simklCopy).firstMatchingInSource(bangumiResult))
    }

    @Test
    fun `source scoped lookup rejects cross provider MAL and TMDB identity collisions`() {
        val bangumiResult = result(
            title = "A distinct Bangumi title",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681,
            tmdbId = 321
        )
        val simklCopy = entry(
            title = "Unrelated Simkl title",
            malId = 5681,
            year = 1999,
            source = "simkl",
            tmdbId = 321
        )

        assertTrue(simklCopy.matches(bangumiResult))
        assertFalse(simklCopy.matchesInSource(bangumiResult))
        assertNull(listOf(simklCopy).firstMatchingInSource(bangumiResult))
        assertFalse(simklCopy.matchesInSource(5681, "bangumi"))
    }

    @Test
    fun `simkl lookup never treats a tmdb id as a simkl id`() {
        val tmdbRow = entry(
            title = "Demon Slayer: Infinity Castle",
            source = "tmdb",
            type = MediaType.Movie,
            tmdbId = 12345
        )
        val simklRow = entry(
            title = "Demon Slayer: Infinity Castle",
            source = "simkl",
            type = MediaType.Movie,
            tmdbId = 12345
        ).copy(simklId = 98765)

        assertFalse(tmdbRow.matchesInSource(12345, "simkl"))
        assertFalse(simklRow.matchesInSource(12345, "simkl"))
        assertTrue(simklRow.matchesInSource(98765, "simkl"))
        assertTrue(simklRow.matchesInSource(12345, "tmdb"))
    }

    @Test
    fun `source scoped lookup accepts Bangumi aliases and exact source ids`() {
        val storedBangumi = entry(
            title = "Old title",
            malId = 500_000_876,
            source = "bgm"
        )
        val bangumiResult = result(
            title = "New title",
            malId = 500_000_876,
            source = "bangumi"
        )

        assertTrue(storedBangumi.matchesInSource(bangumiResult))
        assertTrue(listOf(storedBangumi).firstMatchingInSource(bangumiResult) === storedBangumi)
    }


    @Test
    fun `tmdb account lookup does not borrow Simkl or legacy catalog membership`() {
        val accountResult = result(
            title = "Demon Slayer: Infinity Castle",
            malId = 12345,
            source = "tmdb_account",
            type = MediaType.Movie,
            year = 2025,
            tmdbId = 12345
        )
        val simklMovie = entry(
            title = "Demon Slayer: Infinity Castle",
            source = "simkl",
            type = MediaType.Movie,
            year = 2025,
            tmdbId = 12345
        )
        val accountMovie = simklMovie.copy(source = "tmdb_account")

        assertFalse(simklMovie.matchesInSource(accountResult))
        assertFalse(simklMovie.copy(source = "tmdb").matchesInSource(accountResult))
        assertTrue(accountMovie.matchesInSource(accountResult))
    }

    @Test
    fun `tmdb simkl alias does not open Bangumi provider scope`() {
        val bangumi = entry(
            title = "Demon Slayer: Infinity Castle",
            source = "bangumi",
            type = MediaType.Movie,
            year = 2025,
            tmdbId = 12345
        )
        val tmdbResult = result(
            title = "Demon Slayer: Infinity Castle",
            malId = 12345,
            source = "tmdb",
            type = MediaType.Movie,
            year = 2025,
            tmdbId = 12345
        )
        assertFalse(bangumi.matchesInSource(tmdbResult))
    }

    @Test
    fun `same title does not override conflicting TMDB account ids`() {
        val remake = entry(
            title = "Dune",
            source = "tmdb_account",
            type = com.kitsugi.animelist.model.MediaType.Movie,
            tmdbId = 438631,
            year = 2021
        )
        val otherMovie = result(
            title = "Dune",
            source = "tmdb_account",
            type = com.kitsugi.animelist.model.MediaType.Movie,
            malId = 841,
            tmdbId = 841,
            year = 2021
        )
        assertFalse(remake.matchesInSource(otherMovie))
        assertTrue(remake.matchesInSource(otherMovie.copy(malId = 438631, tmdbId = 438631)))
    }

    @Test
    fun `unrelated entries do not match`() {
        val unrelated = entry(title = "Steins;Gate", malId = 9253, year = 2011, source = "mal")
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681
        )
        assertFalse(unrelated.matches(asResult))
    }

    @Test
    fun `matchesTargetSource detects TMDB selection already saved under Simkl`() {
        val simklMovie = entry(
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            malId = null,
            source = "simkl",
            type = MediaType.Movie,
            tmdbId = 1311031
        ).copy(total = 1)
        val tmdbResult = result(
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            malId = 1311031,
            source = "tmdb",
            type = MediaType.Movie,
            tmdbId = 1311031
        )

        assertFalse(simklMovie.matchesInSource(tmdbResult))
        assertTrue(simklMovie.matchesTargetSource(tmdbResult, "simkl"))
        assertTrue(listOf(simklMovie).firstMatchingInTargetOrResultSource(tmdbResult, "simkl") === simklMovie)
    }

    @Test
    fun `matchesTargetSource detects anime movie across Movie and single episode Anime`() {
        val simklAnimeMovie = entry(
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            malId = 59192,
            source = "simkl",
            type = MediaType.Anime,
            tmdbId = 1311031
        ).copy(total = 1, progress = 1)
        val tmdbMovieResult = result(
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            malId = 1311031,
            source = "tmdb",
            type = MediaType.Movie,
            tmdbId = 1311031
        )

        assertTrue(simklAnimeMovie.matchesTargetSource(tmdbMovieResult, "simkl"))
        assertTrue(listOf(simklAnimeMovie).firstMatchingInTargetOrResultSource(tmdbMovieResult, "simkl") === simklAnimeMovie)
    }
}
