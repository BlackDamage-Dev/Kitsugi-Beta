package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SyncResult.needsRetry` sözleşmesi.
 *
 * Kuyruk (`PendingSyncDrainer`) bir işi "başarılı" sayarken bu bayrağa bakar: `false` ise
 * iş kuyruktan SİLİNİR. Bu yüzden "bağlı değil" durumunun burada `true` olması kritiktir;
 * aksi hâlde token'ı süresi dolmuşken yapılan bir düzenleme o sağlayıcıya bir daha hiç
 * gönderilmez (sessiz veri kaybı).
 */
class SyncResultRetrySemanticsTest {

    private fun result(
        errors: List<String> = emptyList(),
        skipped: List<String> = emptyList(),
        outcomes: Map<String, String> = emptyMap(),
        connectionGapSources: List<String> = emptyList()
    ) = ExternalListSyncManager.SyncResult(
        messages = emptyList(),
        errors = errors,
        skipped = skipped,
        outcomes = outcomes,
        connectionGapSources = connectionGapSources
    )

    @Test
    fun identitySkipDoesNotRetry() {
        val res = result(
            skipped = listOf("MAL kimliği doğrulanamadı"),
            outcomes = mapOf("mal" to ExternalListSyncManager.OUTCOME_SKIPPED_IDENTITY)
        )
        assertFalse(res.needsRetry)
        assertFalse(res.anySynced)
    }

    @Test
    fun transientErrorRetries() {
        val res = result(
            errors = listOf("MAL API hatası: 503"),
            outcomes = mapOf("mal" to ExternalListSyncManager.OUTCOME_FAILED)
        )
        assertTrue(res.needsRetry)
    }

    @Test
    fun notConnectedRetriesSoTheChangeIsNotLost() {
        val res = result(
            outcomes = mapOf(
                "anilist" to ExternalListSyncManager.OUTCOME_SYNCED,
                "kitsu" to ExternalListSyncManager.OUTCOME_NOT_CONNECTED
            ),
            connectionGapSources = listOf("Kitsu")
        )
        assertTrue(res.hasConnectionGap)
        assertTrue(res.needsRetry)
        assertTrue(res.anySynced)
    }

    @Test
    fun disabledProviderIsNotARetry() {
        val res = result(
            outcomes = mapOf(
                "anilist" to ExternalListSyncManager.OUTCOME_SYNCED,
                "simkl" to ExternalListSyncManager.OUTCOME_DISABLED
            )
        )
        assertFalse(res.needsRetry)
    }

    @Test
    fun unconnectedProviderTheUserNeverUsedIsNotARetry() {
        // Kullanıcının Bangumi hesabı yok: "bağlı değil" sonucu kuyruğa iş yazmamalı,
        // aksi hâlde her düzenlemede "hesap bağlı değil" uyarısı çıkardı.
        val res = result(
            outcomes = mapOf(
                "anilist" to ExternalListSyncManager.OUTCOME_SYNCED,
                "bangumi" to ExternalListSyncManager.OUTCOME_NOT_CONNECTED
            )
        )
        assertFalse(res.hasConnectionGap)
        assertFalse(res.needsRetry)
    }

    @Test
    fun upToDateIsNotARetry() {
        val res = result(
            outcomes = mapOf(
                "mal" to ExternalListSyncManager.OUTCOME_UP_TO_DATE,
                "bangumi" to ExternalListSyncManager.OUTCOME_SKIPPED_TYPE
            )
        )
        assertFalse(res.needsRetry)
    }

    @Test
    fun timeoutIsNotSilentlyIgnored() {
        // Zaman aşımı gerçek bir hatadır (errors listesine yazılır) → yeniden denenir.
        val res = result(
            errors = listOf("zaman aşımı"),
            outcomes = mapOf("kitsu" to ExternalListSyncManager.OUTCOME_TIMEOUT)
        )
        assertTrue(res.needsRetry)
    }
}
