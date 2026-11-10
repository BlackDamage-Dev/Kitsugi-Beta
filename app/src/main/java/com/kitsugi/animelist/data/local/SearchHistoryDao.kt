package com.kitsugi.animelist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /**
     * Ana arama geçmişi — eklenti portalı aramaları (type 'EXTENSION' ile başlar)
     * burada görünmez; iki kapsam birbirine karışmaz.
     */
    @Query(
        "SELECT * FROM search_history " +
            "WHERE type NOT LIKE 'EXTENSION%' " +
            "ORDER BY CASE WHEN type LIKE '%:PIN:%' THEN 1 ELSE 0 END DESC, timestamp DESC " +
            "LIMIT 50"
    )
    fun getRecentSearchHistory(): Flow<List<SearchHistoryEntity>>

    /** Eklenti portalı (eklentiler arası arama) geçmişi — yalnızca EXTENSION kayıtları. */
    @Query("SELECT * FROM search_history WHERE type LIKE 'EXTENSION%' ORDER BY timestamp DESC LIMIT 20")
    fun getRecentExtensionSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE LOWER(TRIM(`query`)) = LOWER(TRIM(:query)) LIMIT 1")
    suspend fun findByQueryNormalized(query: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query AND type NOT LIKE 'EXTENSION%'")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history WHERE LOWER(TRIM(`query`)) = LOWER(TRIM(:query)) AND type NOT LIKE 'EXTENSION%'")
    suspend fun deleteByQueryNormalized(query: String)

    /** Eklenti geçmişinden tek sorgu siler (yalnızca EXTENSION kayıtları). */
    @Query("DELETE FROM search_history WHERE `query` = :query AND type LIKE 'EXTENSION%'")
    suspend fun deleteExtensionSearchQuery(query: String)

    @Query("DELETE FROM search_history WHERE type NOT LIKE 'EXTENSION%'")
    suspend fun clearSearchHistory()

    @Query("DELETE FROM search_history WHERE type NOT LIKE 'EXTENSION%' AND type NOT LIKE '%:PIN:%'")
    suspend fun clearUnpinnedSearchHistory()

    /** Eklenti geçmişini tamamen temizler (yalnızca EXTENSION kayıtları). */
    @Query("DELETE FROM search_history WHERE type LIKE 'EXTENSION%'")
    suspend fun clearExtensionSearchHistory()

    /**
     * Arama geçmişini en yeni [keep] kayda indirir; sabitlenen aramalar korunur.
     */
    @Query(
        "DELETE FROM search_history WHERE type NOT LIKE '%:PIN:%' AND rowid NOT IN " +
            "(SELECT rowid FROM search_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun pruneToLimit(keep: Int): Int
}
