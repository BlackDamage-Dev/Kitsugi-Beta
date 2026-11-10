package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiStudioBangumiFallbackTest {
    @Test
    fun officialV0DetailSurvivesWhenExperimentalP1IsUnavailable() {
        val v0 = person(id = 1020, name = "BONES", works = listOf(work(1, "Cowboy Bebop")))

        val merged = mergeBangumiOrganizationDetails(v0, p1 = null)

        assertSame(v0, merged)
        assertEquals(listOf(1), merged?.mediaWorks?.map { it.mediaId })
    }

    @Test
    fun p1DetailStillLoadsWhenV0RootIsUnavailable() {
        val p1 = person(id = 1020, name = "BONES", works = listOf(work(1, "Cowboy Bebop")))

        val merged = mergeBangumiOrganizationDetails(v0 = null, p1 = p1)

        assertSame(p1, merged)
    }

    @Test
    fun p1OnlyFillsMissingWorksAndMetadata() {
        val v0 = person(id = 1020, name = "BONES", biography = "Official summary")
        val p1 = person(
            id = 1020,
            name = "BONES",
            biography = "Experimental summary",
            englishName = "BONES Inc.",
            works = listOf(work(1, "Cowboy Bebop"), work(2, "Fullmetal Alchemist"))
        )

        val merged = mergeBangumiOrganizationDetails(v0, p1)!!

        assertEquals(1020, merged.id)
        assertEquals("Official summary", merged.biography)
        assertEquals("BONES Inc.", merged.englishName)
        assertEquals(listOf(1, 2), merged.mediaWorks.map { it.mediaId })
    }

    @Test
    fun organizationIdentityAcceptsAliasesButRejectsAnotherCompany() {
        val detail = person(
            id = 1020,
            name = "株式会社ボンズ",
            alternativeNames = listOf("BONES", "Bones Animation Studio")
        )

        assertTrue(matchesBangumiOrganization("BONES, Inc.", detail))
        assertFalse(matchesBangumiOrganization("Kyoto Animation", detail))
        assertTrue(matchesBangumiOrganization(null, detail))
    }

    private fun person(
        id: Int,
        name: String,
        biography: String? = null,
        englishName: String? = null,
        alternativeNames: List<String> = emptyList(),
        works: List<KitsugiStaffMediaWork> = emptyList()
    ) = KitsugiStaffDetail(
        id = id,
        name = name,
        nativeName = null,
        alternativeNames = alternativeNames,
        imageUrl = null,
        biography = biography,
        occupation = null,
        birthday = null,
        age = null,
        gender = null,
        homeTown = null,
        mediaWorks = works,
        englishName = englishName
    )

    private fun work(id: Int, title: String) = KitsugiStaffMediaWork(
        mediaId = id,
        mediaTitle = title,
        mediaImageUrl = null,
        mediaType = "anime",
        staffRole = "Animation Production",
        source = "bangumi"
    )
}
