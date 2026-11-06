package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.model.ScoredSearchResult
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus

class MangaSearchCoordinator(
    private val stateStore: MangaSourceStateStore,
    /** Null ise geriye dönük davranış (health != Disabled) korunur. */
    private val enabledStore: MangaSourceEnabledStore? = null,
) {

    /** Kullanıcı tarafından açık bırakılmış kaynaklar; ayarlar listesi bundan beslenir. */
    fun isSourceEnabled(source: MangaSource): Boolean =
        enabledStore?.isEnabled(source) ?: (stateStore.getHealthStatus(source) != SourceHealthStatus.Disabled)

    fun getAvailableSources(sources: List<MangaSource>): List<MangaSource> {
        return sources
            .filter { isSourceEnabled(it) }
            .filter { stateStore.getHealthStatus(it) != SourceHealthStatus.Disabled }
            .sortedWith(
                compareByDescending<MangaSource> { getSourcePriority(it) }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun getSearchCandidateSources(
        sources: List<MangaSource>,
        includeTrustedFallbacks: Boolean = true,
    ): List<MangaSource> {
        val enabledGlobal = getAvailableSources(sources)
            .filter { isSourceEnabled(it) }
            .filter { TurkishSourceRegistry.policyFor(it).allowGlobalSearch }

        val eligible = enabledGlobal.filter { stateStore.isSearchEligible(it) }
        // Tüm etkin kaynaklar soğutmada olsa bile kullanıcı açıkça arama başlattığında
        // "0 kaynak" döndürmek yerine etkin kaynakları son çare olarak dene.
        val available = eligible.ifEmpty { enabledGlobal }

        val turkish = available.filter { TurkishSourceRegistry.policyFor(it).isTurkishPreferred }
        if (turkish.isNotEmpty()) {
            val extras = if (includeTrustedFallbacks) {
                available
                    .filter {
                        val policy = TurkishSourceRegistry.policyFor(it)
                        !policy.isTurkishPreferred && policy.isTrusted && (it.isGlobalCatalog || !policy.isFallbackOnly)
                    }
                    .sortedByDescending { TurkishSourceRegistry.policyFor(it).priority }
                    .take(4)
            } else {
                emptyList()
            }
            return (turkish + extras).distinctBy { it.stableSourceKey() }
        }
        return available
    }

    fun postProcess(
        source: MangaSource,
        query: String,
        mangas: List<MangaDetails>,
        relaxScoring: Boolean = false
    ): List<MangaDetails> = postProcess(source, listOf(query), mangas, relaxScoring)

    /**
     * Çok adlı (yerel + orijinal) arama sonrası eleme.
     *
     * Kaynak "역대급 영지 설계사" ile arandığında dönen kart "The Greatest Estate Developer"
     * olabilir (veya tersi). Tek sorguya göre eleme yapılırsa bu kartlar düşüyordu; burada
     * KABUL EDİLEN ad listesinin **herhangi biriyle** eşleşen sonuç tutulur ve en iyi skor
     * kullanılır. Böylece "Yerel ad" ile arayıp orijinal adla dönen sonuç kaybolmaz.
     */
    fun postProcess(
        source: MangaSource,
        queries: List<String>,
        mangas: List<MangaDetails>,
        relaxScoring: Boolean = false,
    ): List<MangaDetails> {
        val canonicalQueries = queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .map { CanonicalMangaResolver.resolve(it) }
        if (canonicalQueries.isEmpty()) return emptyList()

        val strictMin = canonicalQueries.maxOf { SearchResultScorer.minAcceptedScore(source, it) }
        val minScore = if (relaxScoring) 0.05 else strictMin

        return mangas
            .map { manga ->
                val best = canonicalQueries.maxOf { query -> SearchResultScorer.score(source, query, manga) }
                ScoredSearchResult(source = source, manga = manga, score = best)
            }
            .filter { it.score >= minScore }
            .sortedWith(
                compareByDescending<ScoredSearchResult> { it.score }
                    .thenBy { it.manga.title.lowercase() }
            )
            .distinctBy { scored ->
                val titleKey = CanonicalMangaResolver.resolve(scored.manga.title).compact.ifBlank { scored.manga.title.lowercase() }
                "${source.stableSourceKey()}|${scored.manga.url}|$titleKey"
            }
            .map { it.manga.copy(source = source.name) }
    }

    fun getSourcePriority(source: MangaSource): Int {
        val policy = TurkishSourceRegistry.policyFor(source)
        val healthDelta = when (stateStore.getHealthStatus(source)) {
            SourceHealthStatus.Healthy -> 16
            SourceHealthStatus.Degraded -> -15
            SourceHealthStatus.Broken -> -120
            SourceHealthStatus.CaptchaRequired -> -35
            SourceHealthStatus.RateLimited -> -25
            SourceHealthStatus.Disabled -> -1_000
            SourceHealthStatus.Unknown -> 0
        }
        val cooldownPenalty = if (stateStore.isCoolingDown(source)) -180 else 0
        val streakPenalty = (stateStore.getFailureStreak(source).coerceAtMost(5)) * -12
        return policy.priority + healthDelta + cooldownPenalty + streakPenalty
    }
}
