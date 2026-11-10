package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.SearchHistoryItem
import com.kitsugi.animelist.ui.screens.search.SearchPlatform
import com.kitsugi.animelist.ui.screens.search.SearchScope
import com.kitsugi.animelist.ui.screens.search.SearchSourceEngine

object SearchHistoryMapper {
    const val PIN_TOKEN = "PIN"
    private const val UNPIN_TOKEN = "UNPIN"

    fun isPinnedType(type: String): Boolean = type.contains(":$PIN_TOKEN:")

    fun toDomain(entity: SearchHistoryEntity): SearchHistoryItem {
        val parts = entity.type.split(":")
        val platform = runCatching {
            SearchPlatform.valueOf(parts.getOrNull(0) ?: "All")
        }.getOrDefault(SearchPlatform.All)

        val mediaType = runCatching {
            MediaType.valueOf(parts.getOrNull(1) ?: "Anime")
        }.getOrDefault(MediaType.Anime)

        val engine = parts.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { SearchSourceEngine.valueOf(raw) }.getOrNull() }
            ?: platform.toDefaultEngine()

        val scope = parts.getOrNull(3)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { SearchScope.valueOf(raw) }.getOrNull() }
            ?: mediaType.toDefaultScope(engine)

        val isPinned = parts.getOrNull(4) == PIN_TOKEN
        val searchCount = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        return SearchHistoryItem(
            query = entity.query,
            platform = platform,
            mediaType = mediaType,
            timestamp = entity.timestamp,
            engine = engine,
            scope = scope,
            isPinned = isPinned,
            searchCount = searchCount
        )
    }

    fun toEntity(
        item: SearchHistoryItem,
        timestampOverride: Long? = null
    ): SearchHistoryEntity {
        val pinFlag = if (item.isPinned) PIN_TOKEN else UNPIN_TOKEN
        val safeCount = item.searchCount.coerceAtLeast(1)
        val effectiveTimestamp = timestampOverride
            ?: item.timestamp.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        return SearchHistoryEntity(
            query = item.query.trim(),
            timestamp = effectiveTimestamp,
            type = "${item.platform.name}:${item.mediaType.name}:${item.engine.name}:${item.scope.name}:$pinFlag:$safeCount"
        )
    }

    private fun SearchPlatform.toDefaultEngine(): SearchSourceEngine = when (this) {
        SearchPlatform.All, SearchPlatform.CS3 -> SearchSourceEngine.ALL
        SearchPlatform.AniList -> SearchSourceEngine.ANILIST
        SearchPlatform.MAL -> SearchSourceEngine.MAL
        SearchPlatform.TMDB -> SearchSourceEngine.TMDB
        SearchPlatform.Kitsu -> SearchSourceEngine.KITSU
        SearchPlatform.Shikimori -> SearchSourceEngine.SHIKIMORI
        SearchPlatform.Simkl -> SearchSourceEngine.SIMKL
        SearchPlatform.Bangumi -> SearchSourceEngine.BANGUMI
    }

    private fun MediaType.toDefaultScope(engine: SearchSourceEngine): SearchScope = when {
        engine == SearchSourceEngine.ALL && this == MediaType.Anime -> SearchScope.ALL_MIXED
        this == MediaType.Manga -> SearchScope.MANGA
        this == MediaType.Movie -> SearchScope.MOVIE
        this == MediaType.TvShow -> SearchScope.TV
        else -> SearchScope.ANIME
    }
}
