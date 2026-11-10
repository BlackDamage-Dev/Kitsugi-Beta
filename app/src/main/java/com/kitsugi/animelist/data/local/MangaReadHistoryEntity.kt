package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Okunan manga / novel bölümlerinin geçmiş kaydı.
 *
 * ## Neden ayrı tablo?
 * `manga_chapter_progress` yalnızca "nerede kaldım" bilgisini tutar (bölüm başına tek satır)
 * ve bir bölüm en son ne zaman okunduğunu **kronolojik sırada** tutmaz — üstelik
 * `mangaUrl` ile bir mangaya bağlıdır, kaynak adı/kapağı yoktur. Geçmiş sayfası ise
 * "en son ne okudum" listesini kaynak + kapak + ilerleme ile göstermek zorundadır.
 *
 * Kayıt anahtarı `mangaUrl + chapterUrl` çiftidir; aynı bölüm yeniden okunduğunda
 * satır REPLACE edilir ve `updatedAt` yenilenir → liste doğal olarak en yeniye göre sıralanır.
 */
@Entity(
    tableName = "manga_read_history",
    indices = [
        Index(value = ["mangaUrl"], name = "idx_read_history_mangaUrl"),
        Index(value = ["updatedAt"], name = "idx_read_history_updatedAt")
    ]
)
data class MangaReadHistoryEntity(
    @PrimaryKey
    val id: String,
    /** Eklenti kaynağının görünen adı (ör. "MangaciX"). */
    val sourceName: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val thumbnailUrl: String?,
    val chapterUrl: String,
    val chapterName: String,
    val lastPageIndex: Int,
    val totalPages: Int,
    val isCompleted: Boolean,
    val updatedAt: Long
) {
    companion object {
        /** Aynı bölüm iki kez listelenmesin diye kararlı anahtar. */
        fun key(mangaUrl: String, chapterUrl: String): String = "$mangaUrl|$chapterUrl"
    }
}

@Dao
interface MangaReadHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MangaReadHistoryEntity)

    /** Okunan tüm bölümler — en yeni önce. */
    @Query("SELECT * FROM manga_read_history ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MangaReadHistoryEntity>

    @Query("SELECT * FROM manga_read_history WHERE mangaUrl = :mangaUrl ORDER BY updatedAt DESC")
    suspend fun getAllForManga(mangaUrl: String): List<MangaReadHistoryEntity>

    @Query("DELETE FROM manga_read_history WHERE id = :id")
    suspend fun remove(id: String)

    /** Bir manga/dizinin tüm okuma geçmişini siler. */
    @Query("DELETE FROM manga_read_history WHERE mangaUrl = :mangaUrl")
    suspend fun removeForManga(mangaUrl: String)

    @Query("DELETE FROM manga_read_history")
    suspend fun clearAll()

    @Query("SELECT MAX(updatedAt) FROM manga_read_history")
    suspend fun latestUpdatedAt(): Long?

    /**
     * Geçmiş listesi şişmesin: en yeni [keep] kayıt dışındaki eski satırları siler.
     */
    @Query(
        "DELETE FROM manga_read_history WHERE id NOT IN " +
            "(SELECT id FROM manga_read_history ORDER BY updatedAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("SELECT COUNT(*) FROM manga_read_history")
    suspend fun count(): Int
}
