package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.data.remote.ShikimoriAdultResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * +18 onarımının "bu kaydı hangi kaynakla çözeceğiz" planı.
 *
 * Simkl/Shikimori/Bangumi liste API'leri `adult` alanını taşımadığı için blur
 * yalnızca bu plan doğru çalışırsa uygulanabiliyor; plan bozulursa ya hiç
 * sorgu yapılmaz (bulanıklık kaybolur) ya da yanlış kimliğe sorulur
 * (alakasız yapım bulanıklaşır).
 */
class AdultFlagBackfillMigrationTest {

    private fun entity(
        id: Int,
        source: String,
        type: String = "Anime",
        malId: Int? = null,
        tmdbId: Int? = null,
        isAdult: Boolean = false
    ) = MediaEntryEntity(
        id = id,
        title = "Kayıt $id",
        subtitle = "Anime",
        type = type,
        status = "Watching",
        score = null,
        progress = 0,
        total = null,
        isFavorite = false,
        isAdult = isAdult,
        source = source,
        malId = malId,
        imageUrl = null,
        year = 2024,
        synopsis = null,
        startDate = null,
        endDate = null,
        notes = null,
        tags = null,
        priority = null,
        isRepeating = false,
        repeatCount = 0,
        repeatValue = 0,
        volumeProgress = 0,
        isPrivate = false,
        isHiddenFromStatusLists = false,
        tmdbId = tmdbId
    )

    @Test
    fun simklAnimeWithMalIdIsResolvedThroughShikimoriGraphql() {
        val plan = AdultFlagBackfillMigration.plan(
            entity(id = 1, source = "simkl", malId = 1234)
        )
        assertEquals(
            AdultFlagBackfillMigration.Lookup.Shikimori(ShikimoriAdultResolver.Kind.ANIME, 1234),
            plan
        )
    }

    @Test
    fun mangaTypesUseTheMangaKind() {
        val plan = AdultFlagBackfillMigration.plan(
            entity(id = 2, source = "simkl", type = "Manga", malId = 45_123)
        )
        assertEquals(
            AdultFlagBackfillMigration.Lookup.Shikimori(ShikimoriAdultResolver.Kind.MANGA, 45_123),
            plan
        )
    }

    @Test
    fun simklMovieAndShowFallBackToTmdb() {
        val movie = AdultFlagBackfillMigration.plan(
            entity(id = 3, source = "simkl", type = "Movie", tmdbId = 550)
        )
        assertEquals(AdultFlagBackfillMigration.Lookup.Tmdb(550, isMovie = true), movie)

        val show = AdultFlagBackfillMigration.plan(
            entity(id = 4, source = "simkl", type = "TvShow", tmdbId = 1396)
        )
        assertEquals(AdultFlagBackfillMigration.Lookup.Tmdb(1396, isMovie = false), show)
    }

    @Test
    fun tmdbSourcedRowsNeverMisuseMalId() {
        // TMDB keşif kayıtları malId alanına TMDB kimliğini yazar (MediaGalleryIdentity).
        // Bu değer MAL sanılırsa alakasız bir anime +18 sayılabilir.
        val plan = AdultFlagBackfillMigration.plan(
            entity(id = 5, source = "tmdb", type = "Movie", malId = 27_205, tmdbId = 27205)
        )
        assertEquals(AdultFlagBackfillMigration.Lookup.Tmdb(27205, isMovie = true), plan)
    }

    @Test
    fun bangumiStableIdIsConvertedToRawSubjectId() {
        val plan = AdultFlagBackfillMigration.plan(
            entity(id = 6, source = "bangumi", malId = 500_000_876)
        )
        assertEquals(AdultFlagBackfillMigration.Lookup.Bangumi(876), plan)
        assertEquals("bgm:876", AdultFlagBackfillMigration.checkedKey(plan))
    }

    @Test
    fun legacyBangumiRawIdIsResolvedOnlyInBangumiNamespace() {
        assertEquals(
            AdultFlagBackfillMigration.Lookup.Bangumi(321),
            AdultFlagBackfillMigration.plan(entity(id = 7, source = "bgm.tv", malId = 321))
        )
        assertEquals(
            AdultFlagBackfillMigration.Lookup.None,
            AdultFlagBackfillMigration.plan(entity(id = 8, source = "simkl", malId = 500_000_321))
        )
    }

    @Test
    fun rowsWithoutResolvableIdentityAreSkipped() {
        val plan = AdultFlagBackfillMigration.plan(entity(id = 9, source = "simkl"))
        assertEquals(AdultFlagBackfillMigration.Lookup.None, plan)
        assertNull(AdultFlagBackfillMigration.checkedKey(plan))
    }

    @Test
    fun checkedKeysDistinguishProvidersAndNamespaces() {
        assertEquals(
            "sk:ANIME:1234",
            AdultFlagBackfillMigration.checkedKey(
                AdultFlagBackfillMigration.Lookup.Shikimori(ShikimoriAdultResolver.Kind.ANIME, 1234)
            )
        )
        assertEquals(
            "tm:m:550",
            AdultFlagBackfillMigration.checkedKey(AdultFlagBackfillMigration.Lookup.Tmdb(550, isMovie = true))
        )
        assertEquals(
            "tm:t:1396",
            AdultFlagBackfillMigration.checkedKey(AdultFlagBackfillMigration.Lookup.Tmdb(1396, isMovie = false))
        )
    }

    @Test
    fun outOfRangeMalIdIsNotTreatedAsMalIdentity() {
        // Simkl kendi kimliğini malId alanına yazamaz; 100M üstü ofsetler MAL değildir.
        val plan = AdultFlagBackfillMigration.plan(
            entity(id = 7, source = "simkl", malId = 150_123_456)
        )
        assertEquals(AdultFlagBackfillMigration.Lookup.None, plan)
    }
}
