package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId AND episode = :episode LIMIT 1")
    suspend fun getProgress(mediaId: Int, episode: Int): HistoryEntity?

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId ORDER BY episode DESC")
    suspend fun getHistoryForMedia(mediaId: Int): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: HistoryEntity)

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId AND episode = :episode")
    suspend fun deleteProgress(mediaId: Int, episode: Int)

    /** Aynı bölüm için yeni satır açmadan "izlendi" bayrağını işaretler. */
    @Query("UPDATE watch_history SET watched = 1, lastWatchedAt = :atMs WHERE mediaId = :mediaId AND episode = :episode")
    suspend fun markWatched(mediaId: Int, episode: Int, atMs: Long)

    /**
     * Bölüm bittiğinde konumu sıfırlar ama satırı **silmez**.
     *
     * Eskiden oynatıcı `deleteProgress` çağırıyordu: satır silinince "izlendi" bayrağı da
     * kayboluyor, geçmişten geri doldurma (`backfill`) ve "devam et" satırı aynı bölümü
     * yeniden öneriyordu. Konumu sıfırlamak, bölüm bittiğinde baştan başlatma davranışını
     * korurken bayrağı kalıcı kılar.
     */
    @Query("UPDATE watch_history SET lastPositionMs = 0, watched = 1, lastWatchedAt = :atMs WHERE mediaId = :mediaId AND episode = :episode")
    suspend fun resetPositionAsWatched(mediaId: Int, episode: Int, atMs: Long)

    /** Bu medya için izlenmiş sayılan en yüksek bölüm (yoksa `null`). */
    @Query("SELECT MAX(episode) FROM watch_history WHERE mediaId = :mediaId AND watched = 1")
    suspend fun maxWatchedEpisode(mediaId: Int): Int?

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun deleteAllForMedia(mediaId: Int)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}
