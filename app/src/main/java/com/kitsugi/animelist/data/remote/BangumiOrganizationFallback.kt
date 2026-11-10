package com.kitsugi.animelist.data.remote

/** V0 kurum kaydını korur; p1 yalnızca eksik alanları ve yapım bölümlerini tamamlar. */
internal fun mergeBangumiOrganizationDetails(
    v0: KitsugiStaffDetail?,
    p1: KitsugiStaffDetail?
): KitsugiStaffDetail? {
    if (v0 == null) return p1
    if (p1 == null) return v0
    fun preferred(primary: String?, fallback: String?): String? =
        primary?.trim()?.takeIf { it.isNotEmpty() } ?: fallback?.trim()?.takeIf { it.isNotEmpty() }

    return v0.copy(
        nativeName = preferred(v0.nativeName, p1.nativeName),
        alternativeNames = (v0.alternativeNames + p1.alternativeNames).distinct(),
        imageUrl = preferred(v0.imageUrl, p1.imageUrl),
        biography = preferred(v0.biography, p1.biography),
        occupation = preferred(v0.occupation, p1.occupation),
        birthday = preferred(v0.birthday, p1.birthday),
        age = preferred(v0.age, p1.age),
        gender = preferred(v0.gender, p1.gender),
        homeTown = preferred(v0.homeTown, p1.homeTown),
        characterRoles = if (v0.characterRoles.isNotEmpty()) v0.characterRoles else p1.characterRoles,
        mediaWorks = (v0.mediaWorks + p1.mediaWorks).distinctBy { it.mediaId },
        isFavourite = v0.isFavourite || p1.isFavourite,
        aniListId = v0.aniListId ?: p1.aniListId,
        romanizedName = preferred(v0.romanizedName, p1.romanizedName),
        englishName = preferred(v0.englishName, p1.englishName)
    )
}

/** ID'nin gerçekten tıklanan kuruma ait olduğunu bütün bilinen ad varyantlarıyla doğrular. */
internal fun matchesBangumiOrganization(expectedName: String?, detail: KitsugiStaffDetail): Boolean {
    if (expectedName.isNullOrBlank()) return true
    return buildList {
        add(detail.name)
        detail.nativeName?.let(::add)
        detail.romanizedName?.let(::add)
        detail.englishName?.let(::add)
        addAll(detail.alternativeNames)
    }.any { StudioSourceSupport.namesMatch(expectedName, it) }
}

