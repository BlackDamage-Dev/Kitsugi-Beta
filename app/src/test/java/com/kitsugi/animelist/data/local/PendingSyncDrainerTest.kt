package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.data.auth.SyncField
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Bekleyen eşitleme kuyruğunun sahte DAO ile uçtan uca sözleşmesi (T02).
 *
 * Kapsam: `enqueue` alan birleştirme + satır eşleştirme, `drain` başarı / yeniden
 * deneme / yetki hatası / `MAX_RETRIES` / `freshBudget` / `DISABLED` davranışları ve
 * doğrudan yazım sonrası alan düşme (E73). Ağ yazıcıları, `PendingSyncDrainer`ın
 * iç `drain` dikişine verilen sahte çalıştırıcıyla değiştirilir; `Context` gerekmez.
 */
class PendingSyncDrainerTest {

    /** Room'un `pending_syncs` tablosunun bellek-içi karşılığı (sıra: createdAt, sonra id). */
    private class FakePendingSyncDao : PendingSyncDao {
        private val store = LinkedHashMap<Int, PendingSyncEntity>()
        private var nextId = 1

        override suspend fun insert(entity: PendingSyncEntity) {
            val stored = if (entity.id == 0) entity.copy(id = nextId++) else entity
            if (stored.id >= nextId) nextId = stored.id + 1
            store[stored.id] = stored
        }

        override suspend fun getAll(): List<PendingSyncEntity> =
            store.values.sortedWith(compareBy({ it.createdAt }, { it.id }))

        override suspend fun deleteById(id: Int) {
            store.remove(id)
        }

        override suspend fun incrementRetry(id: Int) {
            store[id]?.let { store[id] = it.copy(retryCount = it.retryCount + 1) }
        }

        override suspend fun resetRetries() {
            store.keys.toList().forEach { id -> store[id] = store.getValue(id).copy(retryCount = 0) }
        }

        override suspend fun clearAll() {
            store.clear()
        }

        override suspend fun count(): Int = store.size
    }

    private fun entry(
        id: Int = 7,
        source: String = "anilist",
        type: MediaType = MediaType.Anime,
        progress: Int = 5,
        notes: String? = null
    ) = MediaEntry(
        id = id,
        title = "Queue Entry",
        type = type,
        status = WatchStatus.Watching,
        score = null,
        progress = progress,
        total = 12,
        source = source,
        notes = notes
    )

    private fun syncResult(
        messages: List<String> = emptyList(),
        errors: List<String> = emptyList(),
        outcomes: Map<String, String> = emptyMap(),
        connectionGapSources: List<String> = emptyList()
    ) = ExternalListSyncManager.SyncResult(
        messages = messages,
        errors = errors,
        outcomes = outcomes,
        connectionGapSources = connectionGapSources
    )

    private fun queuedFields(entity: PendingSyncEntity): Set<String> {
        val names = JSONObject(entity.entryJson).optJSONArray("_syncFields") ?: return emptySet()
        return (0 until names.length()).map { names.getString(it) }.toSet()
    }

    // ── enqueue ─────────────────────────────────────────────────────────────────────

    @Test
    fun enqueueStoresJobWithItsFieldSet() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(notes = "notum"), setOf(SyncField.NOTES))
            val jobs = dao.getAll()
            assertEquals(1, jobs.size)
            assertEquals("UPDATE", jobs.single().operation)
            assertEquals(0, jobs.single().retryCount)
            assertEquals(setOf("NOTES"), queuedFields(jobs.single()))
        }
    }

    @Test
    fun enqueueMergesFieldSetsForSameRowAndKeepsNewestSnapshot() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(progress = 5), setOf(SyncField.PROGRESS))
            PendingSyncDrainer.enqueue(
                dao, "UPDATE", entry(progress = 6, notes = "yeni"), setOf(SyncField.NOTES)
            )
            val jobs = dao.getAll()
            assertEquals(1, jobs.size)
            assertEquals(setOf("PROGRESS", "NOTES"), queuedFields(jobs.single()))
            // Anlık görüntü en güncel satırdır: eski ilerleme (5) yeniden yazılmaz.
            assertEquals(6, JSONObject(jobs.single().entryJson).optInt("progress"))
        }
    }

    @Test
    fun enqueueDoesNotMergeDifferentRows() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 7), setOf(SyncField.PROGRESS))
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 8), setOf(SyncField.NOTES))
            // Aynı sayısal kimlik, farklı kaynak: ayrı satırdır, birleşmez.
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 7, source = "mal"), setOf(SyncField.SCORE))
            assertEquals(3, dao.getAll().size)
        }
    }

    @Test
    fun enqueueNeverMergesRowsWithoutDatabaseId() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 0), setOf(SyncField.PROGRESS))
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 0), setOf(SyncField.NOTES))
            assertEquals(2, dao.getAll().size)
        }
    }

    @Test
    fun enqueueTreatsLegacyJobWithoutFieldsAsFullWrite() {
        val dao = FakePendingSyncDao()
        runBlocking {
            val legacyJson = JSONObject()
                .put("id", 7).put("source", "anilist").put("type", "Anime")
                .put("title", "Queue Entry").toString()
            dao.insert(PendingSyncEntity(operation = "UPDATE", entryJson = legacyJson))
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val jobs = dao.getAll()
            assertEquals(1, jobs.size)
            assertEquals(SyncField.entries.map { it.name }.toSet(), queuedFields(jobs.single()))
        }
    }

    // ── removeSyncedFields (E73) ────────────────────────────────────────────────────

    @Test
    fun directProgressSyncDoesNotDiscardQueuedNoteChange() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(notes = "kuyrukta"), setOf(SyncField.NOTES))
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val jobs = dao.getAll()
            assertEquals(1, jobs.size)
            assertEquals(setOf("NOTES"), queuedFields(jobs.single()))
        }
    }

    @Test
    fun removeSyncedFieldsDropsJobOnlyWhenNothingRemains() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(
                dao, "UPDATE", entry(), setOf(SyncField.PROGRESS, SyncField.NOTES)
            )
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            assertEquals(1, dao.getAll().size)
            assertEquals(setOf("NOTES"), queuedFields(dao.getAll().single()))
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), setOf(SyncField.NOTES))
            assertTrue(dao.getAll().isEmpty())
        }
    }

    @Test
    fun removeSyncedFieldsPreservesRetryCountOfRemainingJob() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(
                dao, "UPDATE", entry(), setOf(SyncField.PROGRESS, SyncField.SCORE)
            )
            dao.incrementRetry(dao.getAll().single().id)
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val job = dao.getAll().single()
            assertEquals(setOf("SCORE"), queuedFields(job))
            assertEquals(1, job.retryCount)
        }
    }

    @Test
    fun removeSyncedFieldsRespectsOperationAndRow() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "DELETE", entry())
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(id = 9).copy(title = "Other Entry"), setOf(SyncField.NOTES))
            // UPDATE düşmesi DELETE işine ve başka satıra dokunmaz.
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), SyncField.ALL)
            assertEquals(2, dao.getAll().size)
            // DELETE, ALL ile bütünüyle düşer.
            PendingSyncDrainer.removeSyncedFields(dao, "DELETE", entry(), SyncField.ALL)
            val jobs = dao.getAll()
            assertEquals(1, jobs.size)
            assertEquals("UPDATE", jobs.single().operation)
        }
    }

    @Test
    fun removeSyncedFieldsWithEmptySetIsNoOp() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.NOTES))
            PendingSyncDrainer.removeSyncedFields(dao, "UPDATE", entry(), emptySet())
            assertEquals(1, dao.getAll().size)
            assertEquals(setOf("NOTES"), queuedFields(dao.getAll().single()))
        }
    }

    // ── drain ───────────────────────────────────────────────────────────────────────

    @Test
    fun drainSuccessDropsJobAndPassesDecodedFieldsAndEntry() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(notes = "merhaba"), setOf(SyncField.NOTES))
            var seenOperation: String? = null
            var seenEntry: MediaEntry? = null
            var seenFields: Set<SyncField>? = null
            val sent = PendingSyncDrainer.drain(dao) { operation, queuedEntry, fields ->
                seenOperation = operation
                seenEntry = queuedEntry
                seenFields = fields
                syncResult(
                    messages = listOf("AniList eşitlendi"),
                    outcomes = mapOf("AniList" to ExternalListSyncManager.OUTCOME_SYNCED)
                )
            }
            assertEquals(1, sent)
            assertTrue(dao.getAll().isEmpty())
            assertEquals("UPDATE", seenOperation)
            assertEquals(7, seenEntry?.id)
            assertEquals("merhaba", seenEntry?.notes)
            assertEquals(setOf(SyncField.NOTES), seenFields)
        }
    }

    @Test
    fun drainTransientFailureKeepsJobAndIncrementsRetry() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                syncResult(
                    errors = listOf("MAL API hatası: 503"),
                    outcomes = mapOf("MyAnimeList" to ExternalListSyncManager.OUTCOME_FAILED)
                )
            }
            assertEquals(0, sent)
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainDisabledProvidersCountAsDelivered() {
        // Kullanıcı kaynağı kendisi kapattı: iş kuyruğu şişirmez, düşer (sözleşme).
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                syncResult(
                    outcomes = mapOf(
                        "AniList" to ExternalListSyncManager.OUTCOME_DISABLED,
                        "Simkl" to ExternalListSyncManager.OUTCOME_DISABLED
                    )
                )
            }
            assertEquals(1, sent)
            assertTrue(dao.getAll().isEmpty())
        }
    }

    @Test
    fun drainNotConnectedWithGapKeepsJobForReconnect() {
        // "Bağlı değil" başarı DEĞİLDİR: değişiklik, hesap bağlanınca gönderilmek üzere kalır.
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                syncResult(
                    outcomes = mapOf("Kitsu" to ExternalListSyncManager.OUTCOME_NOT_CONNECTED),
                    connectionGapSources = listOf("Kitsu")
                )
            }
            assertEquals(0, sent)
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainAuthErrorResultDoesNotBurnRetryBudget() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                syncResult(
                    errors = listOf("AniList API hatası: 401"),
                    outcomes = mapOf("AniList" to ExternalListSyncManager.OUTCOME_FAILED)
                )
            }
            assertEquals(0, sent)
            assertEquals(0, dao.getAll().single().retryCount)
        }
    }

    // (E74) Yetki hatası sonuç yerine istisna olarak gelse de bütçe yanmaz.
    @Test
    fun drainAuthExceptionDoesNotBurnRetryBudget() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                throw IllegalStateException("HTTP 401 Unauthorized")
            }
            assertEquals(0, sent)
            assertEquals(0, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainNetworkExceptionBurnsRetryBudget() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                throw IOException("Unable to resolve host")
            }
            assertEquals(0, sent)
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainSkipsExhaustedJobsWithoutDeletingThem() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            repeat(PendingSyncPolicy.MAX_RETRIES) { dao.incrementRetry(dao.getAll().single().id) }
            var executed = false
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                executed = true
                syncResult()
            }
            assertEquals(0, sent)
            assertFalse(executed)
            // Bütçesi dolan iş silinmez; kullanıcı niyeti korunur.
            assertEquals(PendingSyncPolicy.MAX_RETRIES, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainFreshBudgetRevivesExhaustedJob() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            repeat(PendingSyncPolicy.MAX_RETRIES) { dao.incrementRetry(dao.getAll().single().id) }
            var executed = false
            val sent = PendingSyncDrainer.drain(dao, freshBudget = true) { _, _, _ ->
                executed = true
                syncResult(errors = listOf("MAL API hatası: 503"))
            }
            assertEquals(0, sent)
            assertTrue(executed)
            // Sayaç önce sıfırlandı, başarısız deneme 1'e çıkardı.
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainFreshBudgetSuccessDropsExhaustedJob() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
            repeat(PendingSyncPolicy.MAX_RETRIES) { dao.incrementRetry(dao.getAll().single().id) }
            val sent = PendingSyncDrainer.drain(dao, freshBudget = true) { _, _, _ ->
                syncResult(
                    messages = listOf("Kitsu eşitlendi"),
                    outcomes = mapOf("Kitsu" to ExternalListSyncManager.OUTCOME_SYNCED)
                )
            }
            assertEquals(1, sent)
            assertTrue(dao.getAll().isEmpty())
        }
    }

    @Test
    fun drainMalformedJobIsKeptAndCountsAsAttempt() {
        val dao = FakePendingSyncDao()
        runBlocking {
            dao.insert(PendingSyncEntity(operation = "UPDATE", entryJson = "{bozuk-json"))
            var executed = false
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                executed = true
                syncResult()
            }
            assertEquals(0, sent)
            assertFalse(executed)
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainUnknownOperationIsKeptWithoutExecuting() {
        val dao = FakePendingSyncDao()
        runBlocking {
            PendingSyncDrainer.enqueue(dao, "BIRLESTIR", entry(), setOf(SyncField.PROGRESS))
            var executed = false
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                executed = true
                syncResult()
            }
            assertEquals(0, sent)
            assertFalse(executed)
            assertEquals(1, dao.getAll().single().retryCount)
        }
    }

    @Test
    fun drainEmptyQueueReturnsZero() {
        val dao = FakePendingSyncDao()
        runBlocking {
            assertEquals(0, PendingSyncDrainer.drain(dao) { _, _, _ -> syncResult() })
        }
    }

    @Test
    fun drainCancellationPropagatesWithoutBurningBudgetOrLockingQueue() {
        val dao = FakePendingSyncDao()
        var propagated = false
        try {
            runBlocking {
                PendingSyncDrainer.enqueue(dao, "UPDATE", entry(), setOf(SyncField.PROGRESS))
                PendingSyncDrainer.drain(dao) { _, _, _ ->
                    throw CancellationException("oturum iptal edildi")
                }
            }
        } catch (expected: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
        runBlocking {
            assertEquals(0, dao.getAll().single().retryCount)
            // Kilit bırakılmış olmalı: sonraki boşaltma normal çalışır.
            val sent = PendingSyncDrainer.drain(dao) { _, _, _ ->
                syncResult(
                    messages = listOf("AniList eşitlendi"),
                    outcomes = mapOf("AniList" to ExternalListSyncManager.OUTCOME_SYNCED)
                )
            }
            assertEquals(1, sent)
            assertTrue(dao.getAll().isEmpty())
        }
    }
}
