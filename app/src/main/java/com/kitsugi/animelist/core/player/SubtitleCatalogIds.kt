package com.kitsugi.animelist.core.player

/** Decode the stream screen's legacy stable IDs without confusing provider namespaces. */
object SubtitleCatalogIds {
    data class SourceIds(
        val malId: Int?,
        val aniListId: Int?,
        val kitsuId: Int?,
        val simklId: Int? = null,
        val shikimoriId: Int? = null,
        val bangumiId: Int? = null
    )

    fun decode(malId: Int?, aniListId: Int?): SourceIds = SourceIds(
        malId = malId?.takeIf { it in 1..99_999_999 },
        aniListId = when {
            aniListId != null && aniListId in 1..99_999_999 -> aniListId
            aniListId != null && aniListId in 100_000_001..299_999_999 -> aniListId - 100_000_000
            else -> null
        },
        kitsuId = malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000),
        shikimoriId = malId?.takeIf { it in 400_000_001..499_999_999 }?.minus(400_000_000),
        bangumiId = malId?.takeIf { it in 500_000_001..599_999_999 }?.minus(500_000_000),
        simklId = when {
            malId != null && malId in 150_000_001..199_999_999 -> malId - 150_000_000
            aniListId != null && aniListId in 150_000_001..199_999_999 -> aniListId - 150_000_000
            else -> null
        }
    )

    fun imdb(value: String?): String? = value?.trim()?.takeIf { it.matches(Regex("tt[0-9]{5,12}")) }
}
