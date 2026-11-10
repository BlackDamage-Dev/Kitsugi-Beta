package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.MediaTitleResolver
import org.json.JSONObject

/**
 * Simkl'in birbirinden farklı başlık alanları. Ham `title`, anime kayıtlarında çoğunlukla
 * romaji'dir; İngilizce alanla aynıymış gibi iki modele de kopyalanmamalıdır.
 */
internal data class SimklTitleVariants(
    val display: String,
    val english: String?,
    val romaji: String?,
    val native: String?,
    val alternatives: List<String>
)

/** Simkl başlıklarını kaynak anlamlarını koruyarak çözer. */
internal fun resolveSimklTitleVariants(obj: JSONObject): SimklTitleVariants {
    val raw = MediaTitleResolver.nonBlank(obj.optNullableString("title"))
    val english = MediaTitleResolver.firstLatin(
        obj.optNullableString("title_en"),
        obj.optNullableString("en_title"),
        obj.optNullableString("title_english")
    )
    val rawLatin = MediaTitleResolver.latin(raw)
    val romaji = MediaTitleResolver.firstLatin(
        obj.optNullableString("title_romaji"),
        obj.optNullableString("romaji")
    ) ?: rawLatin
    val native = simklNativeTitle(obj)
    val display = MediaTitleResolver.resolve(
        localized = null,
        english = english,
        romaji = romaji,
        original = native ?: raw
    )
    val alternatives = listOfNotNull(raw, english, romaji, native)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return SimklTitleVariants(display, english, romaji, native, alternatives)
}

/** Geriye dönük çağrılar için yalnızca varsayılan gösterim başlığı. */
internal fun resolveSimklTitle(obj: JSONObject): String = resolveSimklTitleVariants(obj).display

/** Simkl kaydındaki yerel (Japonca/Çince/Korece) başlık — yoksa null. */
internal fun simklNativeTitle(obj: JSONObject): String? {
    val native = MediaTitleResolver.nonBlank(
        obj.optNullableString("title_native"),
        obj.optNullableString("ja_title"),
        obj.optNullableString("title_japanese"),
        obj.optNullableString("title")
    )
    return native?.takeIf { MediaTitleResolver.hasCjk(it) }
}

