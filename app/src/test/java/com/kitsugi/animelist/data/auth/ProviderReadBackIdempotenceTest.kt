package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GERİ-OKUMA KOŞUMU (T05): her sağlayıcı için `dirtyFieldsFor(readBack, merge)` BOŞ olmalı.
 *
 * Akış: adaylardan birleşim hesaplanır → sağlayıcıya `dirtyFieldsFor` kümesi yazılır →
 * sunucunun o yazımı kabul edip geri okunduğu satır (`readBack`) modellenir → aynı
 * birleşime karşı kirli alan YENİDEN hesaplanır. Boş değilse sağlayıcı her toplu turda
 * aynı isteği atar (sonsuz yazım, A5 ihlali). E72 ve E76 bu sınıftandı; bu dosya
 * sınıfın tamamını 6 sağlayıcı × senaryo matrisinde kilitler.
 *
 * `readBack` modeli kanonik `MediaEntry` düzeyindedir: yazılan alan hedefin kendi
 * sözlüğündeki karşılığıyla (`ProviderCapabilities.statusFor`) geri döner, bayrak
 * `AS_FLAG` hedeflerde birleşik bayrağa oturur, desteklenmeyen alan yazılmadığı için
 * eski değerinde kalır. HTTP gövde dönüşümleri (puan ölçeği vb.) bu koşumun konusu
 * değildir: puan için `SyncScores` sınır testleri, MAL durum gövdesi için
 * `MalRewatchStatusWriteTest` (E77) ayrıca vardır. Sunucu taraflı kırpmalar
 * (hedefin kendi toplamı gibi) da modellenmez — onlar hata/yeniden deneme
 * semantiğine girer, sessiz döngü üretmez.
 */
class ProviderReadBackIdempotenceTest {

    private fun row(
        id: Int,
        source: String,
        status: WatchStatus = WatchStatus.Watching,
        progress: Int = 0,
        volumeProgress: Int = 0,
        total: Int? = 12,
        score: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
        repeatCount: Int = 0,
        isRepeating: Boolean = false,
        isFavorite: Boolean = false,
        type: MediaType = MediaType.Anime,
        updatedAt: Long = 100L
    ) = MediaEntry(
        id = id,
        title = "Test",
        type = type,
        status = status,
        score = score,
        progress = progress,
        total = total,
        source = source,
        volumeProgress = volumeProgress,
        startDate = startDate,
        endDate = endDate,
        repeatCount = repeatCount,
        isRepeating = isRepeating,
        isFavorite = isFavorite,
        updatedAt = updatedAt
    )

    private val providers = listOf("anilist", "mal", "kitsu", "shikimori", "bangumi", "simkl")

    /** Birleşimi üreten "diğer kaynak" satırı: sağlayıcının kendisiyle karışmasın diye farklı kaynak. */
    private fun otherSource(provider: String): String = if (provider == "anilist") "mal" else "anilist"

    /**
     * Yazımın hedefte kabul edilip geri okunmuş hâli. Yalnız `dirtyFieldsFor` kümesindeki
     * alanlar değişir; durum, hedefin KENDİ sözlüğündeki karşılığıyla döner.
     */
    private fun readBackAfterWrite(
        current: MediaEntry,
        merged: CrossSyncMergePolicy.Merged,
        provider: String
    ): MediaEntry {
        val capabilities = ProviderCapabilitiesCatalog.of(provider)
        val fields = CrossSyncMergePolicy.dirtyFieldsFor(current, merged, provider)
        var readBack = current
        if (SyncField.STATUS in fields) {
            readBack = readBack.copy(
                status = capabilities.statusFor(current, merged.status),
                isRepeating = when (capabilities.rewatchModel) {
                    ProviderCapabilities.RewatchModel.AS_FLAG -> merged.isRepeating
                    else -> readBack.isRepeating
                }
            )
        }
        if (SyncField.PROGRESS in fields) readBack = readBack.copy(progress = merged.progress)
        if (SyncField.VOLUME_PROGRESS in fields) {
            readBack = readBack.copy(volumeProgress = merged.volumeProgress)
        }
        if (SyncField.SCORE in fields) readBack = readBack.copy(score = merged.score)
        if (SyncField.START_DATE in fields) readBack = readBack.copy(startDate = merged.startDate)
        if (SyncField.END_DATE in fields) readBack = readBack.copy(endDate = merged.endDate)
        if (SyncField.REWATCH_COUNT in fields) readBack = readBack.copy(repeatCount = merged.repeatCount)
        if (SyncField.FAVORITE in fields) readBack = readBack.copy(isFavorite = merged.isFavorite)
        return readBack
    }

    private fun assertConverges(scenario: String, candidates: List<MediaEntry>, current: MediaEntry, provider: String) {
        val merged = CrossSyncMergePolicy.merge(candidates)
        val readBack = readBackAfterWrite(current, merged, provider)
        val secondPass = CrossSyncMergePolicy.dirtyFieldsFor(readBack, merged, provider)
        assertEquals(
            "$scenario / $provider: yazım sonrası hâlâ kirli alan var (sonsuz yazım): $secondPass",
            emptySet<SyncField>(),
            secondPass
        )
    }

    // ── E76: narrow() STATUS enjeksiyonu yalnız bayrak-taşıyan hedeflerde ──────────

    @Test
    fun rewatchCountNarrowsToStatusOnlyForFlagProviders() {
        val countOnly = setOf(SyncField.REWATCH_COUNT)
        // Bayrak taşıyanlar: sayaç + durum (bayrak durum yazımıyla gider).
        assertEquals(
            setOf(SyncField.REWATCH_COUNT, SyncField.STATUS),
            countOnly.narrowTo("mal")
        )
        assertEquals(
            setOf(SyncField.REWATCH_COUNT, SyncField.STATUS),
            countOnly.narrowTo("kitsu")
        )
        // Ayrı durum taşıyanlar: yalnız sayaç.
        assertEquals(setOf(SyncField.REWATCH_COUNT), countOnly.narrowTo("anilist"))
        assertEquals(setOf(SyncField.REWATCH_COUNT), countOnly.narrowTo("shikimori"))
        // (E76) Tekrarı HİÇ ifade edemeyenler: boş küme. Eskiden STATUS enjekte ediliyordu;
        // bu satırlarda sayaç hep 0 kaldığı için her toplu turda aynı istek atılırdı.
        assertTrue(countOnly.narrowTo("bangumi").isEmpty())
        assertTrue(countOnly.narrowTo("simkl").isEmpty())
    }

    // ── Senaryo matrisi ─────────────────────────────────────────────────────────────

    @Test
    fun behindRowConvergesAfterOneWrite() {
        for (provider in providers) {
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Watching,
                progress = 8, score = 8, startDate = "2024-01-01", isFavorite = true,
                updatedAt = 200L
            )
            val current = row(id = 2, source = provider, status = WatchStatus.Watching, progress = 3)
            assertConverges("geride-satir", listOf(truth, current), current, provider)
        }
    }

    @Test
    fun completionConvergesAfterOneWrite() {
        for (provider in providers) {
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Completed,
                progress = 12, score = 10, endDate = "2024-02-01", updatedAt = 200L
            )
            val current = row(
                id = 2, source = provider, status = WatchStatus.Watching,
                progress = 12, score = 7
            )
            assertConverges("tamamlanma", listOf(truth, current), current, provider)
        }
    }

    @Test
    fun activeRewatchConvergesForEveryProvider() {
        for (provider in providers) {
            val other = otherSource(provider)
            // Tekrarı bildiren satır, o kaynağın KENDİ sözlüğündedir.
            val truth = if (other == "mal") {
                row(
                    id = 1, source = other, status = WatchStatus.Watching, progress = 2,
                    isRepeating = true, repeatCount = 1, updatedAt = 200L
                )
            } else {
                row(
                    id = 1, source = other, status = WatchStatus.Repeating, progress = 2,
                    repeatCount = 1, updatedAt = 200L
                )
            }
            val current = row(
                id = 2, source = provider, status = WatchStatus.Completed, progress = 12
            )
            assertConverges("aktif-tekrar", listOf(truth, current), current, provider)
        }
    }

    /** (E76 regresyonu) Bitmiş tekrarın SAYACI: Bangumi/Simkl'de yazılacak alan hiç yoktur. */
    @Test
    fun finishedRewatchCountConvergesAndWritesNothingOnUnsupported() {
        for (provider in providers) {
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Completed,
                progress = 12, repeatCount = 2, updatedAt = 200L
            )
            val current = row(
                id = 2, source = provider, status = WatchStatus.Completed, progress = 12
            )
            val merged = CrossSyncMergePolicy.merge(listOf(truth, current))
            if (provider == "bangumi" || provider == "simkl") {
                assertEquals(
                    "$provider: tekrar sayacı ifade edilemez, alan istenmemeli",
                    emptySet<SyncField>(),
                    CrossSyncMergePolicy.dirtyFieldsFor(current, merged, provider)
                )
            }
            assertConverges("bitmis-tekrar-sayaci", listOf(truth, current), current, provider)
        }
    }

    @Test
    fun stickyStatusesConvergeAfterOneWrite() {
        for (sticky in listOf(WatchStatus.Dropped, WatchStatus.Paused)) {
            for (provider in providers) {
                val truth = row(
                    id = 1, source = otherSource(provider), status = sticky,
                    progress = 4, updatedAt = 200L
                )
                val current = row(
                    id = 2, source = provider, status = WatchStatus.Watching, progress = 4
                )
                assertConverges("yapiskan-$sticky", listOf(truth, current), current, provider)
            }
        }
    }

    @Test
    fun scoreAndDateFillConvergesAfterOneWrite() {
        for (provider in providers) {
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Completed,
                progress = 12, score = 9,
                startDate = "2024-01-01", endDate = "2024-02-01", updatedAt = 200L
            )
            val current = row(
                id = 2, source = provider, status = WatchStatus.Completed, progress = 12
            )
            assertConverges("puan-tarih-doldurma", listOf(truth, current), current, provider)
        }
    }

    @Test
    fun mangaVolumeProgressConvergesWhereSupported() {
        // Simkl'de manga yoktur; tür kapısı üst katmandadır (`supportsSimkl`), koşuma girmez.
        for (provider in providers - "simkl") {
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Watching,
                progress = 40, volumeProgress = 5, total = null,
                type = MediaType.Manga, updatedAt = 200L
            )
            val current = row(
                id = 2, source = provider, status = WatchStatus.Watching,
                progress = 40, volumeProgress = 2, total = null, type = MediaType.Manga
            )
            assertConverges("manga-cilt", listOf(truth, current), current, provider)
        }
    }

    @Test
    fun plannedOnlyGroupConverges() {
        for (provider in providers) {
            val current = row(id = 2, source = provider, status = WatchStatus.Planned, progress = 0)
            val truth = row(
                id = 1, source = otherSource(provider), status = WatchStatus.Planned,
                progress = 0, updatedAt = 200L
            )
            assertConverges("planlandi", listOf(truth, current), current, provider)
        }
    }
}
