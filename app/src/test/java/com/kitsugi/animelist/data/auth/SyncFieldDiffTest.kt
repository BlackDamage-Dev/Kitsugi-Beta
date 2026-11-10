package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ALAN BAZLI YAZIM SÖZLEŞMESİ.
 *
 * Kök neden: eşitleme her tetiklendiğinde satırın TAMAMI hedefe yazılıyordu. Yerel satırda
 * puan/not/tarih boşken bu, hedefteki değerleri siliyor ya da eziyordu:
 *  - MAL'a `score=0` gider ("0 = puanı sil").
 *  - Kitsu'ya `ratingTwenty: null` gider (puan silinir).
 *  - Simkl'de `removeRating` çağrılır.
 *  - AniList'te `startedAt/completedAt` yeniden yazılır.
 *
 * Bu testler, "sadece gerçekten değişen alan yazılır" kuralını ve sağlayıcı yetenek
 * matrisini (hangi hedef hangi alanı destekler) sabitler.
 */
class SyncFieldDiffTest {

    private fun entry(
        id: Int = 1,
        status: WatchStatus = WatchStatus.Watching,
        score: Int? = null,
        progress: Int = 0,
        volumeProgress: Int = 0,
        startDate: String? = null,
        endDate: String? = null,
        notes: String? = null,
        tags: String? = null,
        isFavorite: Boolean = false,
        isPrivate: Boolean = false,
        isRepeating: Boolean = false,
        repeatCount: Int = 0,
        repeatValue: Int = 0,
        priority: Int? = null,
        isHiddenFromStatusLists: Boolean = false
    ) = MediaEntry(
        id = id,
        title = "Test",
        type = MediaType.Anime,
        status = status,
        score = score,
        progress = progress,
        total = 12,
        volumeProgress = volumeProgress,
        startDate = startDate,
        endDate = endDate,
        notes = notes,
        tags = tags,
        isFavorite = isFavorite,
        isPrivate = isPrivate,
        isRepeating = isRepeating,
        repeatCount = repeatCount,
        repeatValue = repeatValue,
        priority = priority,
        isHiddenFromStatusLists = isHiddenFromStatusLists
    )

    @Test
    fun `previous yoksa tam yazim`() {
        assertEquals(SyncField.ALL, SyncFieldDiff.between(null, entry()))
    }

    @Test
    fun `hicbir alan degismediyse bos kume`() {
        val current = entry(score = 7, notes = "not", progress = 3)
        assertTrue(SyncFieldDiff.between(current, current.copy()).isEmpty())
    }

    /** Yalnızca puan değiştiyse yalnızca SCORE yazılır: hedefteki not/tarih korunur. */
    @Test
    fun `sadece puan degisti`() {
        val previous = entry(score = 5, notes = "eski not", startDate = "2020-01-01")
        val current = previous.copy(score = 8)
        assertEquals(setOf(SyncField.SCORE), SyncFieldDiff.between(previous, current))
    }

    @Test
    fun `sadece ilerleme degisti`() {
        val previous = entry(progress = 3)
        assertEquals(setOf(SyncField.PROGRESS), SyncFieldDiff.between(previous, previous.copy(progress = 4)))
    }

    @Test
    fun `cilt ilerlemesi ayri alan`() {
        val previous = entry(volumeProgress = 1)
        assertEquals(
            setOf(SyncField.VOLUME_PROGRESS),
            SyncFieldDiff.between(previous, previous.copy(volumeProgress = 2))
        )
    }

    /** Notu boşaltmak da bir değişikliktir: hedefteki not silinmeli. */
    @Test
    fun `not bosaltildi`() {
        val previous = entry(notes = "dolu")
        assertEquals(setOf(SyncField.NOTES), SyncFieldDiff.between(previous, previous.copy(notes = null)))
    }

    @Test
    fun `tarih ve etiket degisimi`() {
        val previous = entry(startDate = "2020-01-01", tags = "a,b")
        val current = previous.copy(startDate = "2020-02-02", tags = "a")
        assertEquals(
            setOf(SyncField.START_DATE, SyncField.TAGS),
            SyncFieldDiff.between(previous, current)
        )
    }

    /**
     * "Tekrar izleniyor" bayrağı DURUM DEĞİŞMEDEN değişebilir (Watching + isRepeating).
     * Bu durumda hedefte (MAL `is_rewatching`, Kitsu `reconsuming`) bayrağın yazılması için
     * STATUS da kirli sayılmalıdır; aksi halde hedefte eski bayrak kalır.
     */
    @Test
    fun `tekrar bayragi durumu da kirletir`() {
        val previous = entry(status = WatchStatus.Watching)
        val current = previous.copy(isRepeating = true)
        val diff = SyncFieldDiff.between(previous, current)
        assertTrue(SyncField.STATUS in diff)
        assertTrue(SyncField.REWATCH_COUNT in diff)
    }

    /** Aynı durum + aynı bayrak: yalnızca ilerleme değiştiyse durum yazılmaz. */
    @Test
    fun `ilerleme durumu kirletmez`() {
        val previous = entry(status = WatchStatus.Watching, progress = 1)
        val diff = SyncFieldDiff.between(previous, previous.copy(progress = 2))
        assertFalse(SyncField.STATUS in diff)
    }

    @Test
    fun `tekrar sayisi`() {
        val previous = entry(repeatCount = 1)
        val diff = SyncFieldDiff.between(previous, previous.copy(repeatCount = 2))
        assertTrue(SyncField.REWATCH_COUNT in diff)
    }

    // ── Sağlayıcı yetenekleri ────────────────────────────────────────────────────

    @Test
    fun `desteklenmeyen alan hedefe yazilmaz`() {
        // Simkl'de tarih/cilt/tekrar yok.
        val simkl = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS,
            SyncField.START_DATE, SyncField.END_DATE, SyncField.VOLUME_PROGRESS,
            SyncField.REWATCH_COUNT
        ).narrowTo("simkl")
        assertFalse(SyncField.START_DATE in simkl)
        assertFalse(SyncField.END_DATE in simkl)
        assertFalse(SyncField.VOLUME_PROGRESS in simkl)
        assertEquals(setOf(SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS), simkl)
    }

    @Test
    fun `kitsu cilt desteklemez`() {
        assertFalse(SyncField.VOLUME_PROGRESS in setOf(SyncField.VOLUME_PROGRESS).narrowTo("kitsu"))
    }

    @Test
    fun `mal etiket destekler`() {
        assertEquals(setOf(SyncField.TAGS), setOf(SyncField.TAGS).narrowTo("mal"))
    }

    @Test
    fun `bilinmeyen saglayici tam yazimi korur`() {
        assertEquals(SyncField.ALL, SyncField.ALL.narrowTo("gelecekteki-hedef"))
    }

    /** MAL'de "tekrar" ayrı durum değildir: sayaç değişince durum da yazılmalı. */
    @Test
    fun `mal tekrar sayisi durumu tetikler`() {
        val narrowed = setOf(SyncField.REWATCH_COUNT).narrowTo("mal")
        assertTrue(SyncField.STATUS in narrowed)
    }

    /** AniList'te REPEATING gerçek bir durum: sayaç durumu tetiklemek zorunda değil. */
    @Test
    fun `anilist tekrar sayisi durumu tetiklemez`() {
        val narrowed = setOf(SyncField.REWATCH_COUNT).narrowTo("anilist")
        assertEquals(setOf(SyncField.REWATCH_COUNT), narrowed)
    }

    /** "Listelerde gizle" yalnız AniList'te var; başka hedefe yazılmamalı. */
    @Test
    fun `listede gizleme yalniz aniliste`() {
        val previous = entry(isHiddenFromStatusLists = false)
        val current = previous.copy(isHiddenFromStatusLists = true)
        assertEquals(
            setOf(SyncField.HIDDEN_FROM_STATUS_LISTS),
            SyncFieldDiff.between(previous, current)
        )
        assertEquals(
            setOf(SyncField.HIDDEN_FROM_STATUS_LISTS),
            setOf(SyncField.HIDDEN_FROM_STATUS_LISTS).narrowTo("anilist")
        )
        assertTrue(setOf(SyncField.HIDDEN_FROM_STATUS_LISTS).narrowTo("mal").isEmpty())
        assertTrue(setOf(SyncField.HIDDEN_FROM_STATUS_LISTS).narrowTo("kitsu").isEmpty())
    }

    /** Öncelik: AniList + MAL; Kitsu/Shikimori/Bangumi/Simkl desteklemez. */
    @Test
    fun `oncelik yalniz anilist ve malde`() {
        assertEquals(setOf(SyncField.PRIORITY), setOf(SyncField.PRIORITY).narrowTo("anilist"))
        assertEquals(setOf(SyncField.PRIORITY), setOf(SyncField.PRIORITY).narrowTo("mal"))
        assertTrue(setOf(SyncField.PRIORITY).narrowTo("bangumi").isEmpty())
        assertTrue(setOf(SyncField.PRIORITY).narrowTo("simkl").isEmpty())
    }

    /** Favori yalnız AniList'te gerçek bir alandır (MAL'de favori ucu yok). */
    @Test
    fun `favori yalniz aniliste`() {
        assertEquals(setOf(SyncField.FAVORITE), setOf(SyncField.FAVORITE).narrowTo("anilist"))
        assertTrue(setOf(SyncField.FAVORITE).narrowTo("mal").isEmpty())
    }

    /** Tarih: Kitsu + AniList + MAL destekler; Bangumi/Shikimori/Simkl desteklemez. */
    @Test
    fun `tarih destegi saglayici bazli`() {
        val dates = setOf(SyncField.START_DATE, SyncField.END_DATE)
        assertEquals(dates, dates.narrowTo("anilist"))
        assertEquals(dates, dates.narrowTo("mal"))
        assertEquals(dates, dates.narrowTo("kitsu"))
        assertTrue(dates.narrowTo("shikimori").isEmpty())
        assertTrue(dates.narrowTo("bangumi").isEmpty())
        assertTrue(dates.narrowTo("simkl").isEmpty())
    }

    @Test
    fun `puan olcekleri`() {
        assertEquals(100, ProviderCapabilitiesCatalog.of("anilist").scoreScale.max)
        assertEquals(20, ProviderCapabilitiesCatalog.of("kitsu").scoreScale.max)
        assertEquals(10, ProviderCapabilitiesCatalog.of("mal").scoreScale.max)
        assertEquals(10, ProviderCapabilitiesCatalog.of("shikimori").scoreScale.max)
        assertEquals(10, ProviderCapabilitiesCatalog.of("bangumi").scoreScale.max)
        assertEquals(10, ProviderCapabilitiesCatalog.of("simkl").scoreScale.max)
    }

    /** Altı hedefin hepsi kayıtlı olmalı: hiçbiri "sessizce" atlanmaz. */
    @Test
    fun `alti saglayici tanimli`() {
        val ids = ProviderCapabilitiesCatalog.all.map { it.id }.toSet()
        assertEquals(setOf("anilist", "mal", "kitsu", "shikimori", "bangumi", "simkl"), ids)
    }
}
