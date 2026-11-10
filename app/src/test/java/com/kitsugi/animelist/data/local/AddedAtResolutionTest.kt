package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Son eklenen" düzeltmesinin veri-katmanı sözleşmesi: eklenme zamanı hangi
 * yoldan yazılırsa yazılsın kaybolmaz / uydurulmaz. Bkz. [resolveAddedAt].
 */
class AddedAtResolutionTest {

    @Test
    fun providerCreatedAt_winsOverExistingEstimate() {
        // AniList gerçek createdAt (1000), yerelde ilk tahmin (5000) → gerçek kazanır.
        val entry = entry(addedAt = 1_000L, updatedAt = 9_000L)

        assertEquals(1_000L, resolveAddedAt(entry, existingAddedAt = 5_000L))
    }

    @Test
    fun existingAddedAt_isPreservedWhenProviderHasNone() {
        // MAL/Simkl/Bangumi eklenme zamanı vermez; yerelde sabitlenmiş değer korunur.
        val entry = entry(addedAt = 0L, updatedAt = 9_000L)

        assertEquals(5_000L, resolveAddedAt(entry, existingAddedAt = 5_000L))
    }

    @Test
    fun firstWrite_fallsBackToUpdatedAt() {
        val entry = entry(addedAt = 0L, updatedAt = 7_000L)

        assertEquals(7_000L, resolveAddedAt(entry))
    }

    @Test
    fun bulkWrite_neverFabricatesNow() {
        // Toplu içe aktarım "şimdi" damgalasaydı yüzlerce kayıt aynı saniyeye
        // yığılıp Son eklenen'in tepesine çıkardı; bilinmiyor (0) kalmalı.
        val entry = entry(addedAt = 0L, updatedAt = 0L)

        assertEquals(0L, resolveAddedAt(entry))
        assertEquals(123L, resolveAddedAt(entry, nowSeconds = 123L))
    }

    @Test
    fun millisecondTimestamps_areNormalized() {
        val entry = entry(addedAt = 1_700_000_000_000L, updatedAt = 0L)

        assertEquals(1_700_000_000L, resolveAddedAt(entry))
    }

    private fun entry(addedAt: Long, updatedAt: Long) = MediaEntry(
        id = 1,
        title = "Test",
        type = MediaType.Anime,
        status = WatchStatus.Watching,
        score = null,
        progress = 0,
        total = null,
        addedAt = addedAt,
        updatedAt = updatedAt
    )
}
