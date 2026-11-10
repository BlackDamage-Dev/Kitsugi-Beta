package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSyncEntity)

    @Query("SELECT * FROM pending_syncs ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_syncs WHERE id = :id")
    suspend fun deleteById(id: Int)

    // Bütçesi dolan işleri toplu silen bir metot BİLİNÇLİ OLARAK YOKTUR (eski
    // `deleteStale` kaldırıldı — hiç çağrılmıyordu): retryCount tavanı yalnız otomatik
    // denemeyi durdurur, kullanıcı niyeti kaydı asla sessizce yok edilmez;
    // `freshBudget` (yeniden bağlanma / uygulama açılışı) işleri yeniden canlandırır.

    @Query("UPDATE pending_syncs SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Int)

    /**
     * Yeniden deneme bütçesini sıfırlar (bağlantı geri geldiğinde / uygulama açıldığında).
     * `retryCount >= 5` olan kayıtlar sonsuza kadar donmasın diye: 5 deneme bir oturum
     * için caydırıcıdır, bir sonraki bağlantı/oturum için taze bütçe verilir — kullanıcı
     * niyeti asla kalıcı olarak kaybolmaz.
     */
    @Query("UPDATE pending_syncs SET retryCount = 0")
    suspend fun resetRetries()

    /** Tüm kuyruğu temizler (tüm listeyi silme / yedek geri yükleme_REPLACE sonrası bayat işler). */
    @Query("DELETE FROM pending_syncs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM pending_syncs")
    suspend fun count(): Int
}
