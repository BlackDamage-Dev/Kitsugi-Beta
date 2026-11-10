package com.kitsugi.animelist.data.remote

/**
 * AniList kimlikleri MDBList/ARM sorgusunda kendi ad alanlarında tutulur.
 *
 * AniList arama sonuçları `idMal` bilindiğinde onu doğrudan `malId` alanında taşır; MAL
 * eşleşmesi yoksa bunun yerine `100_000_000 + aniListId` biçimindeki stable ID kullanılır.
 * Bu iki biçimi ayırmadan AniList kaynağının MAL kimliğini koşulsuz boşaltmak, MDBList
 * isteğinin hiç başlamamasına neden olur.
 */
internal data class MdbListAniListIds(
    val malId: Int?,
    val aniListId: Int?
)

internal fun resolveMdbListAniListIds(
    stableId: Int,
    realMalId: Int?
): MdbListAniListIds {
    val malId = realMalId?.takeIf { it in 1 until 100_000_000 }
        ?: stableId.takeIf { it in 1 until 100_000_000 }
    val aniListId = stableId
        .takeIf { it in 100_000_001 until 200_000_000 }
        ?.minus(100_000_000)

    return MdbListAniListIds(malId = malId, aniListId = aniListId)
}
