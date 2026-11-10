package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import com.kitsugi.animelist.data.remote.KitsugiStaffCharacterRole
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomCharacterQueriesTest {

    private fun detail(
        source: String,
        id: Int = 1,
        aniListId: Int? = null,
        appearances: List<KitsugiCharacterMediaAppearance> = emptyList()
    ) = KitsugiCharacterDetail(
        id = id,
        name = "Satoru Gojo",
        nativeName = "五条悟",
        alternativeNames = listOf("Gojo-sensei", "五条先生"),
        imageUrl = null,
        gender = null,
        age = null,
        birthday = null,
        bloodType = null,
        biography = null,
        mediaAppearances = appearances,
        aniListId = aniListId,
        source = source,
        romanizedName = "Gojou Satoru",
        englishName = "Satoru Gojo"
    )

    @Test
    fun `tmdb character without media appearances is skipped`() {
        assertNull(FandomCharacterQueries.from(detail("tmdb"), "tmdb", 5))
    }

    @Test
    fun `anilist character builds ids names and appearances`() {
        val appearances = listOf(
            KitsugiCharacterMediaAppearance(
                mediaId = 40748, title = "Jujutsu Kaisen", imageUrl = null, mediaType = "TV Dizisi",
                characterRole = "Ana Karakter", source = "anilist",
                titleEnglish = "JUJUTSU KAISEN", titleRomaji = "Jujutsu Kaisen"
            ),
            KitsugiCharacterMediaAppearance(
                mediaId = 100_101_517, title = "Jujutsu Kaisen", imageUrl = null, mediaType = "Manga",
                characterRole = "Yardımcı Karakter", source = "anilist"
            ),
            KitsugiCharacterMediaAppearance(
                mediaId = 0, title = "Bilinmeyen", imageUrl = null, mediaType = "Anime",
                characterRole = "MAIN", source = "anilist"
            )
        )
        val query = FandomCharacterQueries.from(detail("anilist", id = 127691, appearances = appearances), "anilist", 127691)!!
        assertEquals(127691, query.aniListCharacterId)
        assertNull(query.malCharacterId)
        assertEquals("al:127691", query.cacheKey())
        assertEquals(listOf("Satoru Gojo", "Gojou Satoru"), query.names)
        assertEquals("五条悟", query.nativeName)
        assertEquals(listOf("Gojo-sensei"), query.alternatives)

        assertEquals(2, query.appearances.size)
        assertEquals(FandomSourceIds(malId = 40748), query.appearances[0].ids)
        assertTrue(query.appearances[0].isMainRole)
        assertFalse(query.appearances[0].isManga)
        assertEquals(listOf("JUJUTSU KAISEN"), query.appearances[0].titles)
        assertEquals(FandomSourceIds(aniListId = 101517), query.appearances[1].ids)
        assertTrue(query.appearances[1].isManga)
    }

    @Test
    fun `movie title in character and staff appearances overrides coarse anime type`() {
        val movieTitle = "Chainsaw Man – The Movie: Reze Arc"
        val characterAppearance = KitsugiCharacterMediaAppearance(
            mediaId = 57555,
            title = movieTitle,
            imageUrl = null,
            mediaType = "Anime",
            characterRole = "Main",
            source = "jikan",
            titleEnglish = movieTitle
        )
        val character = FandomCharacterQueries.from(
            detail("jikan", appearances = listOf(characterAppearance)),
            "jikan",
            77
        )!!

        val staffRole = KitsugiStaffCharacterRole(
            characterId = 2,
            characterName = "Reze",
            characterImageUrl = null,
            mediaId = 57555,
            mediaTitle = movieTitle,
            mediaImageUrl = null,
            mediaType = "Anime",
            characterRole = "Main",
            mediaSource = "jikan",
            mediaTitleEnglish = movieTitle
        )
        val staff = FandomCharacterQueries.fromStaff(staff(roles = listOf(staffRole)), "jikan", 7)!!

        listOf(character.appearances.single(), staff.appearances.single()).forEach { appearance ->
            val mediaQuery = FandomMediaQuery(
                ids = appearance.ids,
                isManga = appearance.isManga,
                isMovie = appearance.isMovie,
                fallbackTitles = appearance.titles,
                isTvShow = true
            ).normalizedMediaKind()
            assertTrue(mediaQuery.isMovie)
            assertFalse(mediaQuery.isTvShow)
        }
    }

    @Test
    fun `mal and shikimori character ids map to mal`() {
        val mal = FandomCharacterQueries.from(detail("jikan"), "jikan", 164471)!!
        assertEquals(164471, mal.malCharacterId)
        assertEquals("mal:164471", mal.cacheKey())

        val shikimori = FandomCharacterQueries.from(detail("shikimori", id = 164471), "shikimori", 164471)!!
        assertEquals(164471, shikimori.malCharacterId)
    }

    @Test
    fun `title-only appearance from an unrecognized provider is retained`() {
        val appearance = KitsugiCharacterMediaAppearance(
            mediaId = 55,
            title = "異世界おじさん",
            imageUrl = null,
            mediaType = "Anime",
            characterRole = "Main",
            source = "plugin-provider"
        )
        val query = FandomCharacterQueries.from(
            detail("plugin-provider", appearances = listOf(appearance)),
            "plugin-provider",
            77
        )!!
        assertTrue(query.appearances.single().ids.isEmpty)
        assertEquals(listOf("異世界おじさん"), query.appearances.single().titles)
    }

    @Test
    fun `characters without ids names or appearances are skipped`() {
        val bare = detail("bangumi").copy(name = "五条悟", englishName = null, romanizedName = null)
        assertNull(FandomCharacterQueries.from(bare, "bangumi", 77))
    }

    private fun staff(
        roles: List<KitsugiStaffCharacterRole> = emptyList(),
        works: List<KitsugiStaffMediaWork> = emptyList(),
        name: String = "Yuki Kaji"
    ) = KitsugiStaffDetail(
        id = 7,
        name = name,
        nativeName = "梶裕貴",
        alternativeNames = listOf("Kaji Yuuki"),
        imageUrl = null,
        biography = null,
        occupation = null,
        birthday = null,
        age = null,
        gender = null,
        homeTown = null,
        characterRoles = roles,
        mediaWorks = works
    )

    @Test
    fun `staff query uses voiced main roles first and dedups works`() {
        val roles = listOf(
            KitsugiStaffCharacterRole(
                characterId = 1, characterName = "Side", characterImageUrl = null,
                mediaId = 200, mediaTitle = "Other Show", mediaImageUrl = null,
                mediaType = "TV", characterRole = "Supporting", mediaSource = "jikan"
            ),
            KitsugiStaffCharacterRole(
                characterId = 2, characterName = "Eren Yeager", characterImageUrl = null,
                mediaId = 16498, mediaTitle = "Shingeki no Kyojin", mediaImageUrl = null,
                mediaType = "TV", characterRole = "Main", mediaSource = "jikan",
                mediaTitleEnglish = "Attack on Titan"
            )
        )
        val works = listOf(
            KitsugiStaffMediaWork(
                mediaId = 16498, mediaTitle = "Shingeki no Kyojin", mediaImageUrl = null,
                mediaType = "TV", staffRole = "Theme Song Performance", source = "jikan"
            )
        )
        val query = FandomCharacterQueries.fromStaff(staff(roles, works), "jikan", 7)!!
        assertEquals("staff-jikan", query.source)
        assertEquals(7, query.characterId)
        assertEquals("staff-jikan:7", query.cacheKey())
        assertNull(query.aniListCharacterId)
        assertEquals(listOf("Yuki Kaji"), query.names)
        assertEquals("梶裕貴", query.nativeName)
        assertEquals(2, query.appearances.size)
        assertEquals(16498, query.appearances[0].ids.malId)
        assertTrue(query.appearances[0].isMainRole)
        assertTrue(query.appearances[0].titles.contains("Attack on Titan"))
        assertFalse(query.appearances[1].isMainRole)
    }

    @Test
    fun `staff can use a native name and title-only provider appearance`() {
        assertNull(FandomCharacterQueries.fromStaff(staff(), "jikan", 7))
        val roles = listOf(
            KitsugiStaffCharacterRole(
                characterId = 2, characterName = "X", characterImageUrl = null,
                mediaId = 1, mediaTitle = "Jujutsu Kaisen", mediaImageUrl = null,
                mediaType = "TV", characterRole = "Main", mediaSource = "custom-source"
            )
        )
        val query = FandomCharacterQueries.fromStaff(staff(roles, name = "梶裕貴"), "jikan", 7)
        assertEquals(listOf("Jujutsu Kaisen"), query?.appearances?.single()?.titles)
        assertEquals("梶裕貴", query?.nativeName)
        assertEquals("staff-tmdb", FandomCharacterQueries.fromStaff(staff(roles), "tmdb", 7)?.source)
    }

    @Test
    fun `staff identity ids come from AniList and MAL sources`() {
        val roles = listOf(
            KitsugiStaffCharacterRole(
                characterId = 2, characterName = "X", characterImageUrl = null,
                mediaId = 1, mediaTitle = "Jujutsu Kaisen", mediaImageUrl = null,
                mediaType = "TV", characterRole = "Main", mediaSource = "anilist"
            )
        )
        val fromAniList = FandomCharacterQueries.fromStaff(staff(roles).copy(aniListId = 96870), "anilist", 96870)!!
        assertTrue(fromAniList.isStaff)
        assertEquals(96870, fromAniList.aniListStaffId)
        assertNull(fromAniList.malPeopleId)

        val fromShikimori = FandomCharacterQueries.fromStaff(staff(roles), "shikimori", 65)!!
        assertNull(fromShikimori.aniListStaffId)
        assertEquals(65, fromShikimori.malPeopleId)

        // Karakter sorguları ekip değildir ve ekip kimliği taşımaz.
        val character = FandomCharacterQueries.from(detail("anilist"), "anilist", 1)!!
        assertFalse(character.isStaff)
        assertNull(character.aniListStaffId)
        assertNull(character.malPeopleId)
    }

    @Test
    fun `tmdb slash separated multi role character extracts primary name and role alternatives`() {
        val simpsonsAppearance = KitsugiCharacterMediaAppearance(
            mediaId = 456,
            title = "The Simpsons",
            imageUrl = null,
            mediaType = "TV Dizisi",
            characterRole = "Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown",
            source = "tmdb"
        )
        val homerDetail = KitsugiCharacterDetail(
            id = 198,
            name = "Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown",
            nativeName = null,
            alternativeNames = emptyList(),
            imageUrl = null,
            gender = null,
            age = null,
            birthday = null,
            bloodType = null,
            biography = null,
            mediaAppearances = listOf(simpsonsAppearance),
            source = "tmdb"
        )
        val query = FandomCharacterQueries.from(homerDetail, "tmdb", 198)!!
        assertEquals("Homer Simpson", query.names.first())
        assertEquals(listOf("Abe Simpson", "Barney Gumble", "Krusty the Clown"), query.alternatives)
        assertEquals("tmdb:198:homer simpson", query.cacheKey())
    }
}
