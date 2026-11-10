package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.local.SearchHistoryDao
import com.kitsugi.animelist.data.local.SearchHistoryEntity
import com.kitsugi.animelist.data.local.SearchHistoryMapper
import com.kitsugi.animelist.ui.screens.search.SearchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Eklenti portalı arama geçmişi kayıtlarının `type` öneki. */
const val EXTENSION_HISTORY_TYPE = "EXTENSION:ALL"

class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {

    fun getRecentSearchHistory(): Flow<List<SearchHistoryItem>> {
        return searchHistoryDao.getRecentSearchHistory().map { list ->
            list.map { SearchHistoryMapper.toDomain(it) }
        }
    }

    /** Eklenti portalı aramalarına ait geçmiş (ana aramadan bağımsız). */
    fun getRecentExtensionSearchHistory(): Flow<List<SearchHistoryItem>> {
        return searchHistoryDao.getRecentExtensionSearchHistory().map { list ->
            list.map { SearchHistoryMapper.toDomain(it) }
        }
    }

    /** Eklentiler arası arama sorgusunu eklenti geçmişine kaydeder. */
    suspend fun insertExtensionSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val owner = KitsugiAccountRepository.currentUserId()
        searchHistoryDao.insertSearchQuery(
            SearchHistoryEntity(
                query = trimmed,
                timestamp = System.currentTimeMillis(),
                type = EXTENSION_HISTORY_TYPE
            )
        )
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun deleteExtensionSearchQuery(query: String) {
        val owner = KitsugiAccountRepository.currentUserId()
        searchHistoryDao.deleteExtensionSearchQuery(query)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun clearExtensionSearchHistory() {
        val owner = KitsugiAccountRepository.currentUserId()
        searchHistoryDao.clearExtensionSearchHistory()
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun insertSearchQuery(item: SearchHistoryItem) {
        val trimmed = item.query.trim()
        if (trimmed.length < 2) return
        val owner = KitsugiAccountRepository.currentUserId()
        val existingEntity = searchHistoryDao.findByQueryNormalized(trimmed)
        val existingItem = existingEntity?.let { SearchHistoryMapper.toDomain(it) }

        if (existingEntity != null && existingEntity.query != trimmed) {
            searchHistoryDao.deleteByQueryNormalized(trimmed)
        }

        val merged = item.copy(
            query = trimmed,
            isPinned = item.isPinned || (existingItem?.isPinned == true),
            searchCount = if (existingItem != null) existingItem.searchCount + 1 else item.searchCount.coerceAtLeast(1)
        )
        val entity = SearchHistoryMapper.toEntity(merged, timestampOverride = System.currentTimeMillis())
        searchHistoryDao.insertSearchQuery(entity)
        // Giriş yapılmışsa geçmişi buluta da yaz (giriş yoksa hiçbir şey yapmaz)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun restoreSearchQuery(item: SearchHistoryItem) {
        val trimmed = item.query.trim()
        if (trimmed.isBlank()) return
        val owner = KitsugiAccountRepository.currentUserId()
        val entity = SearchHistoryMapper.toEntity(
            item = item.copy(query = trimmed),
            timestampOverride = item.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        searchHistoryDao.insertSearchQuery(entity)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun togglePinSearchQuery(item: SearchHistoryItem) {
        val trimmed = item.query.trim()
        if (trimmed.isBlank()) return
        val owner = KitsugiAccountRepository.currentUserId()
        val existingEntity = searchHistoryDao.findByQueryNormalized(trimmed)
        val current = existingEntity?.let { SearchHistoryMapper.toDomain(it) } ?: item
        if (existingEntity != null && existingEntity.query != trimmed) {
            searchHistoryDao.deleteByQueryNormalized(trimmed)
        }
        val updated = current.copy(
            query = existingEntity?.query ?: trimmed,
            isPinned = !current.isPinned
        )
        val entity = SearchHistoryMapper.toEntity(
            item = updated,
            timestampOverride = current.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        searchHistoryDao.insertSearchQuery(entity)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun deleteSearchQuery(query: String) {
        val owner = KitsugiAccountRepository.currentUserId()
        searchHistoryDao.deleteSearchQuery(query)
        searchHistoryDao.deleteByQueryNormalized(query)
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }

    suspend fun clearSearchHistory(keepPinned: Boolean = false) {
        val owner = KitsugiAccountRepository.currentUserId()
        if (keepPinned) {
            searchHistoryDao.clearUnpinnedSearchHistory()
        } else {
            searchHistoryDao.clearSearchHistory()
        }
        KitsugiAccountRepository.onSearchHistoryChanged(searchHistoryDao, expectedOwner = owner)
    }
}
