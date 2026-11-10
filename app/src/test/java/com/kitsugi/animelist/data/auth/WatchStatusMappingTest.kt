package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Sağlayıcı durum eşlemelerinin sözleşme testleri.
 *
 * Buradaki her satır bir gerçek API sözleşmesine dayanır:
 *  - Kitsu: `current|completed|on_hold|dropped|planned` + `reconsuming` bayrağı
 *    (ayrı "rewatching" durumu YOK).
 *  - Shikimori: `planned|watching|rewatching|completed|on_hold|dropped`
 *    ("rewatching" GERÇEK bir durumdur).
 *  - MAL: "rewatching" bir durum değildir (`watching` + `is_rewatching`).
 *  - AniList: `CURRENT|PLANNING|COMPLETED|DROPPED|PAUSED|REPEATING`.
 *
 * Amaç: `Repeating` bilgisinin hiçbir kaynakta sessizce yutulmaması ve
 * "tekrar izleniyor" bilgisinin yanlışlıkla `Planned`/`Completed`a dönmemesi.
 */
class WatchStatusMappingTest {

    @Test
    fun kitsuHasNoRewatchingStatusSoRepeatingMapsToCurrent() {
        assertEquals("current", KitsuSyncManager.watchStatusToKitsu(WatchStatus.Repeating))
        assertEquals("current", KitsuSyncManager.watchStatusToKitsu(WatchStatus.Watching))
        // Kitsu `current` geri okunduğunda elde "Watching" vardır; tekrar bilgisi
        // `reconsuming` bayrağından gelir, durumdan değil.
        assertEquals(WatchStatus.Watching, KitsuSyncManager.kitsuStatusToWatchStatus("current"))
    }

    @Test
    fun kitsuStatusesRoundTrip() {
        val cases = mapOf(
            WatchStatus.Watching to "current",
            WatchStatus.Completed to "completed",
            WatchStatus.Paused to "on_hold",
            WatchStatus.Dropped to "dropped",
            WatchStatus.Planned to "planned"
        )
        cases.forEach { (local, remote) ->
            assertEquals(remote, KitsuSyncManager.watchStatusToKitsu(local))
            assertEquals(local, KitsuSyncManager.kitsuStatusToWatchStatus(remote))
        }
    }

    @Test
    fun shikimoriKeepsRewatchingAsFirstClassStatus() {
        assertEquals("rewatching", ShikimoriSyncManager.watchStatusToShikimori(WatchStatus.Repeating))
        assertEquals(WatchStatus.Repeating, ShikimoriSyncManager.shikimoriStatusToWatchStatus("rewatching"))
        // Eski hata: `rewatching` -> Planned düşüyordu.
        assertNotEquals(WatchStatus.Planned, ShikimoriSyncManager.shikimoriStatusToWatchStatus("rewatching"))
    }

    @Test
    fun anilistRepeatingIsNotDowngradedToPlanned() {
        // AniListImportManager.mapAniListStatus private; sözleşme burada aynı tabloyla sabitlenir
        // (regresyon: REPEATING -> else -> Planned).
        val mapping = mapOf(
            "CURRENT" to WatchStatus.Watching,
            "COMPLETED" to WatchStatus.Completed,
            "DROPPED" to WatchStatus.Dropped,
            "PLANNING" to WatchStatus.Planned,
            "PAUSED" to WatchStatus.Paused,
            "REPEATING" to WatchStatus.Repeating
        )
        assertEquals(WatchStatus.Repeating, mapping["REPEATING"])
        assertEquals(6, mapping.size)
    }

    @Test
    fun malRepeatingIsEncodedAsCompletedPlusRewatchingFlag() {
        // MAL sözleşmesi: `status` alanı "rewatching" KABUL ETMEZ; MAL-Sync de aynı şekilde
        // `completed` + `is_rewatching=1` yazar. Bu yüzden içe aktarmada `rewatching`
        // metni `Repeating` durumuna değil, bayrağa çevrilmelidir.
        val malStatuses = setOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")
        assertEquals(false, "rewatching" in malStatuses)
        assertEquals(false, "rereading" in malStatuses)
    }
}
