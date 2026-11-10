package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Query("SELECT translatedText FROM translation_cache WHERE originalHash = :hash LIMIT 1")
    suspend fun getTranslation(hash: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(cache: TranslationCacheEntity)

    /** Sayfa çevirisi paketlerinin tek işlemde yazılması (metin başına ayrı sorgu yok). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(caches: List<TranslationCacheEntity>)

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int

    /**
     * Tabloyu en yeni [keep] satıra indirir.
     *
     * Neden gerekli: bu tabloda daha önce **hiç** silme yolu yoktu, çevrilen her özet/başlık
     * kalıcı olarak birikiyordu (uygulamanın zamanla şişmesinin kalemlerinden biri).
     * Tabloda zaman damgası kolonu bulunmadığı için yaş yerine rowid sırası kullanılır:
     * `INSERT OR REPLACE` satırı yeniden yazdığından en yeni kullanılan kayıt en yüksek rowid'i alır.
     *
     * @return silinen satır sayısı
     */
    @Query(
        "DELETE FROM translation_cache WHERE originalHash NOT IN " +
            "(SELECT originalHash FROM translation_cache ORDER BY rowid DESC LIMIT :keep)"
    )
    suspend fun pruneToLimit(keep: Int): Int

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()
}
