package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail

/** Karakterin geçtiği bir yapım (wiki'yi bulmak için). */
data class FandomAppearanceRef(
    val ids: FandomSourceIds,
    val isManga: Boolean,
    val isMovie: Boolean,
    val isMainRole: Boolean,
    val titles: List<String>
)

/** Karakter → Fandom sayfası çözümlemesi için girdi. */
data class FandomCharacterQuery(
    val source: String,
    val characterId: Int,
    /** AniList karakter kimliği (Wikidata P11736). */
    val aniListCharacterId: Int?,
    /** MAL karakter kimliği (Wikidata P4085). */
    val malCharacterId: Int?,
    /** Latin ad yazımları: İngilizce, Romaji, görünen ad (sıra = öncelik). */
    val names: List<String>,
    /** Kaynaktaki yerel ad (Kanji/Hangul) — en güçlü doğrulama kanıtı. */
    val nativeName: String?,
    val alternatives: List<String>,
    val appearances: List<FandomAppearanceRef>,
    /** AniList kişi kimliği (Wikidata P11227) — yalnızca ekip/seslendirme sorgularında. */
    val aniListStaffId: Int? = null,
    /** MyAnimeList kişi kimliği (Wikidata P4084) — yalnızca ekip/seslendirme sorgularında. */
    val malPeopleId: Int? = null
) {
    /** Ekip / seslendirme sanatçısı sorgusu mu? (`fromStaff` `staff-` öneki koyar.) */
    val isStaff: Boolean
        get() = source.startsWith("staff-")

    fun cacheKey(): String = when {
        aniListCharacterId != null -> "al:$aniListCharacterId"
        malCharacterId != null -> "mal:$malCharacterId"
        source.equals("tmdb", ignoreCase = true) && names.isNotEmpty() ->
            "tmdb:$characterId:${FandomNameVariants.normalizeForCompare(names.first())}"
        else -> "${source.lowercase()}:$characterId"
    }
}

/** [KitsugiCharacterDetail] → [FandomCharacterQuery] (saf fonksiyon). */
object FandomCharacterQueries {

    private const val MAX_APPEARANCES = 6

    /**
     * @return doğrulanabilir hiçbir kimlik/ad/appearance yoksa null.
     */
    fun from(detail: KitsugiCharacterDetail, source: String, characterId: Int): FandomCharacterQuery? {
        val canonical = source.trim().lowercase()
        val isMal = FandomMediaIds.isMalSource(canonical)

        // CharacterDetailViewModel ile aynı kurallar: Shikimori karakter kimliği MAL'i aynalar;
        // Kitsu dalı Jikan detayına düştüyse detayın kimliği gerçek MAL kimliğidir.
        val malCharacterId = when {
            isMal -> characterId.takeIf { it > 0 }
            canonical == "shikimori" -> detail.id.takeIf { it > 0 }
            canonical == "kitsu" -> detail
                .takeIf { FandomMediaIds.isMalSource(it.source) && it.id != characterId }
                ?.id?.takeIf { it > 0 }
            else -> null
        }
        val aniListCharacterId = detail.aniListId?.takeIf { it > 0 }
            ?: if (canonical == "anilist") characterId.takeIf { it > 0 } else null

        val rawNames = listOfNotNull(detail.englishName, detail.romanizedName, detail.name)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
        val names = buildList {
            for (raw in rawNames) {
                val primary = raw.substringBefore("/").trim()
                if (primary.isNotEmpty() && FandomNameVariants.isMostlyLatin(primary)) add(primary)
                if (FandomNameVariants.isMostlyLatin(raw)) add(raw)
            }
        }.distinct()
        val slashAlternatives = rawNames.flatMap { raw ->
            if (raw.contains("/")) raw.split("/").drop(1).map { it.trim() } else emptyList()
        }

        val appearances = detail.mediaAppearances.mapNotNull { appearance ->
            val ids = FandomMediaIds.fromStableId(appearance.source, appearance.mediaId)
            val titles = mediaTitles(appearance.titleEnglish, appearance.titleRomaji, appearance.title)
            if (ids.isEmpty && titles.isEmpty()) return@mapNotNull null
            FandomAppearanceRef(
                ids = ids,
                isManga = FandomMediaIds.isMangaType(appearance.mediaType),
                isMovie = FandomMediaIds.isMovieType(appearance.mediaType),
                isMainRole = FandomMediaIds.isMainRole(appearance.characterRole),
                titles = titles
            )
        }.take(MAX_APPEARANCES)

        val hasCharacterId = aniListCharacterId != null || malCharacterId != null
        val hasCharacterName = names.isNotEmpty() || !detail.nativeName.isNullOrBlank()
        if (!hasCharacterId && (!hasCharacterName || appearances.isEmpty())) return null

        return FandomCharacterQuery(
            source = canonical,
            characterId = characterId,
            aniListCharacterId = aniListCharacterId,
            malCharacterId = malCharacterId,
            names = names,
            nativeName = detail.nativeName?.trim()?.takeIf { it.isNotEmpty() },
            alternatives = (detail.alternativeNames + slashAlternatives)
                .map { it.trim() }
                .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
                .distinct()
                .take(3),
            appearances = appearances
        )
    }

    /**
     * Ekip / seslendirme sanatçısı → sorgu. Kişi sayfaları Wikidata üzerinden Fandom'a bağlı
     * olmadığı için kimlik yolu yoktur; kişi, çalıştığı yapımların wiki'sinde adıyla aranır ve
     * bilgi kutusundaki Japonca/Korece adla doğrulanır (karakterlerle aynı kural).
     *
     * @return doğrulanabilir bir kişi adı veya çalıştığı yapım yoksa null.
     */
    fun fromStaff(detail: KitsugiStaffDetail, source: String, staffId: Int): FandomCharacterQuery? {
        val canonical = source.trim().lowercase()

        val names = listOfNotNull(detail.englishName, detail.romanizedName, detail.name)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
            .distinct()
        if (names.isEmpty() && detail.nativeName.isNullOrBlank()) return null

        // Seslendirdiği ana karakterlerin yapımları önce (wiki'de kişi sayfası olma olasılığı en yüksek),
        // sonra diğer roller ve ekip çalışmaları. Aynı yapım bir kez alınır.
        val seen = HashSet<String>()
        val appearances = buildList {
            val roleMedia = detail.characterRoles
                .sortedByDescending { FandomMediaIds.isMainRole(it.characterRole) }
            for (role in roleMedia) {
                val ids = FandomMediaIds.fromStableId(role.mediaSource, role.mediaId)
                val isManga = FandomMediaIds.isMangaType(role.mediaType)
                val isMovie = FandomMediaIds.isMovieType(role.mediaType)
                val titles = staffTitles(role.mediaTitleEnglish, role.mediaTitleRomaji, role.mediaTitle)
                val key = FandomMediaQuery(ids, isManga, isMovie, titles).cacheKey() ?: continue
                if (!seen.add(key)) continue
                add(
                    FandomAppearanceRef(
                        ids = ids,
                        isManga = isManga,
                        isMovie = isMovie,
                        isMainRole = FandomMediaIds.isMainRole(role.characterRole),
                        titles = titles
                    )
                )
            }
            for (work in detail.mediaWorks) {
                val ids = FandomMediaIds.fromStableId(work.source, work.mediaId)
                val isManga = FandomMediaIds.isMangaType(work.mediaType)
                val isMovie = FandomMediaIds.isMovieType(work.mediaType)
                val titles = staffTitles(work.titleEnglish, work.titleRomaji, work.mediaTitle)
                val key = FandomMediaQuery(ids, isManga, isMovie, titles).cacheKey() ?: continue
                if (!seen.add(key)) continue
                add(
                    FandomAppearanceRef(
                        ids = ids,
                        isManga = isManga,
                        isMovie = isMovie,
                        isMainRole = false,
                        titles = titles
                    )
                )
            }
        }.take(MAX_APPEARANCES)
        if (appearances.isEmpty()) return null

        // Kişi kimlikleri: AniList kişi kimliği (P11227) ve MAL kişi kimliği (P4084).
        // Shikimori kişi kimliği MAL ile aynıdır (karakter yolundaki kuralla aynı).
        val aniListStaff = detail.aniListId?.takeIf { it > 0 }
            ?: if (canonical == "anilist") staffId.takeIf { it > 0 } else null
        val malPeople = if (FandomMediaIds.isMalSource(canonical) || canonical == "shikimori") {
            staffId.takeIf { it > 0 }
        } else null

        return FandomCharacterQuery(
            source = "staff-$canonical",
            characterId = staffId,
            aniListCharacterId = null,
            malCharacterId = null,
            aniListStaffId = aniListStaff,
            malPeopleId = malPeople,
            names = names,
            nativeName = detail.nativeName?.trim()?.takeIf { it.isNotEmpty() },
            alternatives = detail.alternativeNames
                .map { it.trim() }
                .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
                .distinct()
                .take(3),
            appearances = appearances
        )
    }

    private fun staffTitles(vararg titles: String?): List<String> = mediaTitles(*titles)

    private fun mediaTitles(vararg titles: String?): List<String> = titles.filterNotNull()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter {
            it.isNotEmpty() && it.length <= 200 &&
                !it.equals("Bilinmeyen", ignoreCase = true) &&
                !it.equals("Unknown", ignoreCase = true) &&
                !it.equals("N/A", ignoreCase = true)
        }
        .distinctBy(FandomNameVariants::normalizeForCompare)
        .take(8)
}
