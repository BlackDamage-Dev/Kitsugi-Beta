package com.kitsugi.animelist.data.local

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {
    val allHistoryFlow: Flow<List<HistoryEntity>> = dao.getAllHistoryFlow()

    suspend fun getProgress(mediaId: Int, episode: Int): HistoryEntity? {
        return dao.getProgress(mediaId, episode)
    }

    suspend fun getHistoryForMedia(mediaId: Int): List<HistoryEntity> {
        return dao.getHistoryForMedia(mediaId)
    }

    suspend fun saveProgress(
        mediaId: Int,
        episode: Int,
        lastPositionMs: Long,
        durationMs: Long,
        addonName: String? = null,
        watched: Boolean? = null
    ) {
        val existing = dao.getProgress(mediaId, episode)
        val entity = HistoryEntity(
            id = existing?.id ?: 0,
            mediaId = mediaId,
            episode = episode,
            lastPositionMs = lastPositionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
            addonName = addonName,
            // Bayrak verilmezse mevcut değer korunur: ilerleme tikleri "izlendi"yi
            // yanlışlıkla temizlememeli (bölüm sonunda açıkça `true` yazılır).
            watched = watched ?: existing?.watched ?: false
        )
        dao.insertOrUpdate(entity)
    }

    /** Bölümü "izlendi" olarak işaretler (satır yoksa konum bilgisiyle birlikte oluşturur). */
    suspend fun markEpisodeWatched(
        mediaId: Int,
        episode: Int,
        durationMs: Long,
        addonName: String? = null
    ) {
        val existing = dao.getProgress(mediaId, episode)
        if (existing == null) {
            dao.insertOrUpdate(
                HistoryEntity(
                    mediaId = mediaId,
                    episode = episode,
                    lastPositionMs = durationMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    lastWatchedAt = System.currentTimeMillis(),
                    addonName = addonName,
                    watched = true
                )
            )
        } else {
            dao.markWatched(mediaId, episode, System.currentTimeMillis())
        }
    }

    /**
     * Bölümlü içerikte bu medya için izlenmiş en yüksek bölüm numarası.
     * Liste ilerlemesinin gerisinde kalmış yerel ilerlemeyi düzeltmekte kullanılır.
     */
    suspend fun maxWatchedEpisode(mediaId: Int): Int? = dao.maxWatchedEpisode(mediaId)

    /**
     * Bölüm bitti: konum sıfırlanır, "izlendi" bayrağı kalıcı olur (satır silinmez).
     * Satır yoksa bayrağı taşıyan yeni bir satır açılır.
     */
    suspend fun resetAfterFinish(mediaId: Int, episode: Int, durationMs: Long, addonName: String? = null) {
        val existing = dao.getProgress(mediaId, episode)
        if (existing == null) {
            dao.insertOrUpdate(
                HistoryEntity(
                    mediaId = mediaId,
                    episode = episode,
                    lastPositionMs = 0L,
                    durationMs = durationMs.coerceAtLeast(0L),
                    lastWatchedAt = System.currentTimeMillis(),
                    addonName = addonName,
                    watched = true
                )
            )
        } else {
            dao.resetPositionAsWatched(mediaId, episode, System.currentTimeMillis())
        }
    }

    suspend fun deleteProgress(mediaId: Int, episode: Int) {
        dao.deleteProgress(mediaId, episode)
    }

    suspend fun deleteAllForMedia(mediaId: Int) {
        dao.deleteAllForMedia(mediaId)
    }

    suspend fun clearAllHistory() {
        dao.clearAllHistory()
    }
}
