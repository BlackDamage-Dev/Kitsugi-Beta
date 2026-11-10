package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * (E77) MAL yazıcısında gönderilecek `status` değerinin kararı.
 *
 * E72 politika katmanında "MAL'de durum korunur, yalnız bayrak yazılır" demişti;
 * yazıcı ise `Repeating`i koşulsuz `completed`a çeviriyordu. Tekrar sürerken bayrak
 * taşınmak için STATUS alanı kümeye girdiğinde, yayındaki yapımın MAL durumu
 * `watching` → `completed`a dönüyordu. Artık tekrar yazımında MAL'daki mevcut durum
 * (planlanan hariç) korunur; bilinmiyorsa eski varsayılan `completed`tır.
 */
class MalRewatchStatusWriteTest {

    @Test
    fun nonRepeatingStatusesMapDirectly() {
        assertEquals(
            "watching",
            MalSyncManager.malStatusForWrite(WatchStatus.Watching, MediaType.Anime, existingMalStatus = "completed")
        )
        assertEquals(
            "reading",
            MalSyncManager.malStatusForWrite(WatchStatus.Watching, MediaType.Manga, existingMalStatus = null)
        )
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Completed, MediaType.Anime, existingMalStatus = "watching")
        )
        assertEquals(
            "plan_to_watch",
            MalSyncManager.malStatusForWrite(WatchStatus.Planned, MediaType.Anime, existingMalStatus = null)
        )
        assertEquals(
            "plan_to_read",
            MalSyncManager.malStatusForWrite(WatchStatus.Planned, MediaType.Manga, existingMalStatus = null)
        )
        assertEquals(
            "on_hold",
            MalSyncManager.malStatusForWrite(WatchStatus.Paused, MediaType.Anime, existingMalStatus = null)
        )
        assertEquals(
            "dropped",
            MalSyncManager.malStatusForWrite(WatchStatus.Dropped, MediaType.Anime, existingMalStatus = null)
        )
    }

    @Test
    fun repeatingPreservesExistingWatchingStatus() {
        // E72/E77 çekirdek senaryosu: yayında olan yapım tekrar izleniyor; MAL'daki
        // `watching` durumu `completed`a ÇEVRİLMEZ, yalnız `is_rewatching` bayrağı gider.
        assertEquals(
            "watching",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "watching")
        )
        assertEquals(
            "reading",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Manga, existingMalStatus = "reading")
        )
    }

    @Test
    fun repeatingPreservesOtherMeaningfulStatuses() {
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "completed")
        )
        assertEquals(
            "on_hold",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "on_hold")
        )
        assertEquals(
            "dropped",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "dropped")
        )
    }

    @Test
    fun repeatingWithPlannedOrUnknownExistingFallsBackToCompleted() {
        // Henüz durumu olmayan (planlanan) satırda tekrar, MAL'ın kanonik karşılığıyla
        // (completed + is_rewatching) yazılır — E72'deki `repeatingStatus` kuralıyla aynı.
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "plan_to_watch")
        )
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Manga, existingMalStatus = "plan_to_read")
        )
        // Kayıt yok (404) ya da okuma doğrulanamadı: eski varsayılan korunur.
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = null)
        )
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "")
        )
    }

    @Test
    fun unknownExistingStatusIsNeverPassedThrough() {
        // Allowlist dışı dizge API'ye olduğu gibi geçirilmez (400 üretirdi).
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "rewatching")
        )
        assertEquals(
            "completed",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = "garbage; DROP")
        )
    }

    @Test
    fun existingStatusIsNormalizedBeforeMatching() {
        assertEquals(
            "watching",
            MalSyncManager.malStatusForWrite(WatchStatus.Repeating, MediaType.Anime, existingMalStatus = " Watching ")
        )
    }
}
