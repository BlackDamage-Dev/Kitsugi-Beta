package com.kitsugi.animelist.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Kullanıcının **manga kitaplığı** — takip ettiği seriler.
 *
 * ## Neden ayrı tablo?
 * `MediaEntry` (AniList listesi) AniList kimliğine bağlıdır; eklenti kaynaklarından gelen
 * mangaların çoğunun AniList eşleşmesi yoktur (ve olmak zorunda da değildir). Mihon'da
 * kitaplık kavramı "kaynağa eklenmiş seriler"dir; bu tablo onun karşılığıdır:
 *
 * - [sourceName] + [url] → seriyi yeniden açmak için gereken tek bilgi.
 * - [category] → kullanıcı etiketi ("Okunacak", "Devam Eden"…), Mihon kategorileri gibi.
 * - [totalChapters]/[readChapters] → kitaplık kartında ilerleme çubuğu için önbellek.
 *
 * Birincil anahtar `url + sourceName` birleşimidir; aynı seri iki farklı kaynaktan
 * eklenebilir ve ikisi ayrı satır olur (Mihon'daki "merge" davranışının basit hâli).
 */
@Entity(
    tableName = "manga_library",
    indices = [
        Index(value = ["updatedAt"], name = "idx_manga_library_updatedAt"),
        Index(value = ["category"], name = "idx_manga_library_category")
    ]
)
data class MangaLibraryEntity(
    @PrimaryKey
    val id: String,
    val sourceName: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val author: String?,
    /** Kullanıcı etiketi; boşsa "Kitaplık" varsayılan kategorisindedir. */
    @ColumnInfo(defaultValue = "")
    val category: String = "",
    /** Kaynaktaki toplam bölüm sayısı (son taramada). */
    @ColumnInfo(defaultValue = "0")
    val totalChapters: Int = 0,
    /** Okundu işaretli bölüm sayısı (son taramada). */
    @ColumnInfo(defaultValue = "0")
    val readChapters: Int = 0,
    /** En son okunan bölümün adı — "devam et" satırında gösterilir. */
    val lastChapterName: String? = null,
    /** En son okunan bölümün URL'si — "devam et" doğrudan buraya gider. */
    val lastChapterUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun key(sourceName: String, url: String): String = "$sourceName|$url"
    }
}

@Dao
interface MangaLibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MangaLibraryEntity)

    @Query("SELECT * FROM manga_library ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MangaLibraryEntity>

    @Query("SELECT * FROM manga_library WHERE id = :id")
    suspend fun getById(id: String): MangaLibraryEntity?

    @Query("SELECT * FROM manga_library WHERE sourceName = :sourceName AND url = :url")
    suspend fun get(sourceName: String, url: String): MangaLibraryEntity?

    @Query("SELECT DISTINCT category FROM manga_library WHERE category != '' ORDER BY category ASC")
    suspend fun categories(): List<String>

    @Query("DELETE FROM manga_library WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE manga_library SET category = :category WHERE id = :id")
    suspend fun setCategory(id: String, category: String)

    @Query("UPDATE manga_library SET totalChapters = :total, readChapters = :read, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, total: Int, read: Int, now: Long)
}
