package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.SyncField
import com.kitsugi.animelist.data.local.PendingSyncPolicy.RetryDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kuyruk karar kurallarının davranış sözleşmesi (T02).
 *
 * Bu kurallar çevrimdışı değişikliğin kaybolmaması (A6) ve uygulama yeniden
 * başlatılsa da bekleyen işin yaşaması (A7) ölçütlerinin çekirdeğidir; daha önce
 * yalnız kod okumasıyla doğrulanıyorlardı.
 */
class PendingSyncPolicyTest {

    @Test
    fun retryBudgetIsFiveAndEligibilityStopsAtTheCap() {
        assertEquals(5, PendingSyncPolicy.MAX_RETRIES)
        assertTrue(PendingSyncPolicy.isEligible(0))
        assertTrue(PendingSyncPolicy.isEligible(4))
        assertFalse(PendingSyncPolicy.isEligible(5))
        assertFalse(PendingSyncPolicy.isEligible(9))
    }

    @Test
    fun deliveredJobIsDropped() {
        val decision = PendingSyncPolicy.decision(
            hasResult = true,
            needsRetry = false,
            errorTexts = emptyList(),
            thrownMessage = null
        )
        assertEquals(RetryDecision.DROP, decision)
    }

    @Test
    fun transientFailureKeepsJobAndBurnsOneRetry() {
        val decision = PendingSyncPolicy.decision(
            hasResult = true,
            needsRetry = true,
            errorTexts = listOf("MAL API hatası: 503"),
            thrownMessage = null
        )
        assertEquals(RetryDecision.KEEP_INCREMENT, decision)
    }

    @Test
    fun missingResultIsNeverSuccess() {
        // Tanınmayan işlem / null sonuç: iş "gönderildi" sayılamaz.
        val decision = PendingSyncPolicy.decision(
            hasResult = false,
            needsRetry = true,
            errorTexts = emptyList(),
            thrownMessage = null
        )
        assertEquals(RetryDecision.KEEP_INCREMENT, decision)
    }

    @Test
    fun authErrorInResultErrorsDoesNotBurnBudget() {
        listOf("AniList API hatası: 401", "token geçersiz", "HTTP Unauthorized").forEach { text ->
            val decision = PendingSyncPolicy.decision(
                hasResult = true,
                needsRetry = true,
                errorTexts = listOf(text),
                thrownMessage = null
            )
            assertEquals(text, RetryDecision.KEEP_WITHOUT_INCREMENT, decision)
        }
    }

    // (E74) Yetki hatası çağrının kendisinden fırlatıldığında da sayaç yanmaz; eski
    // karar yalnız sonuçtaki hata metinlerine baktığı için istisna yolu bütçe yakıyordu.
    @Test
    fun authErrorFromThrownExceptionDoesNotBurnBudget() {
        val decision = PendingSyncPolicy.decision(
            hasResult = false,
            needsRetry = true,
            errorTexts = emptyList(),
            thrownMessage = "HTTP 401 Unauthorized"
        )
        assertEquals(RetryDecision.KEEP_WITHOUT_INCREMENT, decision)
    }

    @Test
    fun nonAuthExceptionBurnsBudget() {
        val decision = PendingSyncPolicy.decision(
            hasResult = false,
            needsRetry = true,
            errorTexts = emptyList(),
            thrownMessage = "Unable to resolve host api.myanimelist.net"
        )
        assertEquals(RetryDecision.KEEP_INCREMENT, decision)
    }

    @Test
    fun authDetectionCoversNullAndBlank() {
        assertFalse(PendingSyncPolicy.isAuthErrorText(null))
        assertFalse(PendingSyncPolicy.isAuthErrorText(""))
        assertFalse(PendingSyncPolicy.isAuthErrorText("zaman aşımı"))
        assertTrue(PendingSyncPolicy.isAuthError(listOf(null, "hata", "401")))
    }

    @Test
    fun enqueueFieldMergeIsAUnion() {
        val merged = PendingSyncPolicy.mergedFields(
            newFields = setOf(SyncField.NOTES),
            olderFieldSets = listOf(setOf(SyncField.PROGRESS), setOf(SyncField.SCORE, SyncField.NOTES))
        )
        assertEquals(setOf(SyncField.PROGRESS, SyncField.NOTES, SyncField.SCORE), merged)
        assertEquals(
            setOf(SyncField.PROGRESS),
            PendingSyncPolicy.mergedFields(setOf(SyncField.PROGRESS), emptyList())
        )
    }

    // (E73) Doğrudan yazım yalnız kendi alanlarını düşürür; gönderilmemiş alanlar kalır.
    @Test
    fun remainingFieldsSubtractOnlyWhatWasSynced() {
        assertEquals(
            setOf(SyncField.NOTES),
            PendingSyncPolicy.remainingFields(
                queuedFields = setOf(SyncField.PROGRESS, SyncField.NOTES),
                syncedFields = setOf(SyncField.PROGRESS)
            )
        )
        assertTrue(
            PendingSyncPolicy.remainingFields(
                queuedFields = setOf(SyncField.PROGRESS),
                syncedFields = SyncField.ALL
            ).isEmpty()
        )
        assertEquals(
            setOf(SyncField.NOTES),
            PendingSyncPolicy.remainingFields(
                queuedFields = setOf(SyncField.NOTES),
                syncedFields = emptySet()
            )
        )
    }

    @Test
    fun sameRowRequiresPositiveIdSourceAndType() {
        assertTrue(
            PendingSyncPolicy.sameRow(
                entryId = 7, entrySource = "anilist", entryTypeName = "Anime",
                queuedId = 7, queuedSource = "anilist", queuedTypeName = "Anime"
            )
        )
        // Henüz DB kimliği olmayan satır hiçbir kuyruk kaydıyla eşleşmez.
        assertFalse(
            PendingSyncPolicy.sameRow(
                entryId = 0, entrySource = "anilist", entryTypeName = "Anime",
                queuedId = 0, queuedSource = "anilist", queuedTypeName = "Anime"
            )
        )
        // Aynı sayısal kimlik, farklı kaynak/tür: farklı satırdır.
        assertFalse(
            PendingSyncPolicy.sameRow(
                entryId = 7, entrySource = "anilist", entryTypeName = "Anime",
                queuedId = 7, queuedSource = "mal", queuedTypeName = "Anime"
            )
        )
        assertFalse(
            PendingSyncPolicy.sameRow(
                entryId = 7, entrySource = "anilist", entryTypeName = "Anime",
                queuedId = 7, queuedSource = "anilist", queuedTypeName = "Manga"
            )
        )
    }
}
