package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaEntryDao {
    @Query("SELECT * FROM media_entries ORDER BY id DESC")
    fun observeAll(): Flow<List<MediaEntryEntity>>

    @Query("SELECT * FROM media_entries ORDER BY id DESC")
    suspend fun getAll(): List<MediaEntryEntity>

    @Query("SELECT * FROM media_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): MediaEntryEntity?

    /**
     * Yalnızca bir kaynağın satırları. `getAll()` + Kotlin tarafında süzme, her yenilemede
     * tüm kütüphaneyi (1000+ satır) kopyalıyordu; liste içe aktarımı kaynak kapsamlıdır.
     * Şema değişikliği yok — yalnızca yeni sorgu.
     */
    @Query("SELECT * FROM media_entries WHERE LOWER(source) = LOWER(:source) ORDER BY id DESC")
    suspend fun getBySource(source: String): List<MediaEntryEntity>

    @Query("SELECT COUNT(*) FROM media_entries WHERE LOWER(source) = LOWER(:source)")
    suspend fun countBySource(source: String): Int

    @Query("SELECT * FROM media_entries WHERE malId = :malId LIMIT 1")
    suspend fun getByMalId(malId: Int): MediaEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MediaEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: MediaEntryEntity)

    @Update
    suspend fun updateAll(entities: List<MediaEntryEntity>)

    @Query("DELETE FROM media_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM media_entries WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM media_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MediaEntryEntity>): List<Long>

    @Query("DELETE FROM media_entries")
    suspend fun deleteAll()

    /** Restore is all-or-nothing; a failed insert must not erase the old library. */
    @Transaction
    suspend fun replaceAllTransaction(entities: List<MediaEntryEntity>) {
        deleteAll()
        if (entities.isNotEmpty()) insertAll(entities)
    }

    /** Atomically replaces a single provider's rows so concurrent readers/workers never see half-states. */
    @Transaction
    suspend fun replaceSourceTransaction(source: String, entities: List<MediaEntryEntity>) {
        deleteBySource(source)
        if (entities.isNotEmpty()) insertAll(entities)
    }

    /**
     * Tüm import işlemini tek atomik transaction olarak gerçekleştirir.
     * Bu sayede Room Flow yalnızca BİR KEZ tetiklenir ve tüm liste
     * yeniden render edilmez — sadece değişen kayıtlar güncellenir.
     */
    @Transaction
    suspend fun smartImportTransaction(
        toInsert: List<MediaEntryEntity>,
        toUpdate: List<MediaEntryEntity>,
        toDeleteIds: List<Int>
    ) {
        if (toInsert.isNotEmpty()) upsertAll(toInsert)
        if (toUpdate.isNotEmpty()) updateAll(toUpdate)
        if (toDeleteIds.isNotEmpty()) deleteByIds(toDeleteIds)
    }

    @Query("DELETE FROM pending_syncs WHERE id IN (:ids)")
    suspend fun deletePendingSyncJobs(ids: List<Int>)

    /** Legacy duplicate cleanup is atomic across the library and its stale queued jobs. */
    @Transaction
    suspend fun deduplicateTransaction(
        toUpdate: List<MediaEntryEntity>,
        toDeleteIds: List<Int>,
        pendingJobIds: List<Int>
    ) {
        if (toUpdate.isNotEmpty()) updateAll(toUpdate)
        if (toDeleteIds.isNotEmpty()) deleteByIds(toDeleteIds)
        if (pendingJobIds.isNotEmpty()) deletePendingSyncJobs(pendingJobIds)
    }
}