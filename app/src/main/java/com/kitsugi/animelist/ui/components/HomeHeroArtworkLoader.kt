package com.kitsugi.animelist.ui.components

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Admission gate, not a poster fallback. Native banners are tried first; MAL/Bangumi/etc.
 * without one can obtain a real backdrop from the shared TMDB/Fanart resolver. A small
 * decode verifies dimensions and warms the disk cache without keeping dozens of full-size
 * ALL-mode images in memory. The selected URL is then frozen for the lifetime of the page.
 */
internal class HomeHeroArtworkLoader(context: Context) {
    private val context = context.applicationContext
    private val imageLoader by lazy { SingletonImageLoader.get(this.context) }
    // URL-keyed results outlive a single list item; bounded LRU prevents long browsing sessions growing indefinitely.
    private val decodedBackdrops = BoundedCache<String, Boolean>("homeHero.decodedBackdrops", 256)
    private val resolvedBackdrops = BoundedCache<String, String>("homeHero.resolvedBackdrops", 256) // empty = no usable artwork

    fun retryFailures() {
        // BoundedCache exposes snapshot views; remove through the map itself, not entries.removeAll.
        resolvedBackdrops.keys.toList().forEach { key ->
            if (resolvedBackdrops[key].isNullOrBlank()) resolvedBackdrops.remove(key)
        }
        decodedBackdrops.keys.toList().forEach { key ->
            if (decodedBackdrops[key] == false) decodedBackdrops.remove(key)
        }
    }

    suspend fun prepare(item: JikanSearchResult): JikanSearchResult? {
        if (!isHomeHeroMedia(item)) return null
        val key = "${KitsugiHeroArtworkResolver.cacheKey(item)}:${item.imageUrl}:${item.backdropUrl}"
        resolvedBackdrops[key]?.let { cached ->
            return cached.takeIf { it.isNotBlank() }?.let { backdrop ->
                item.withHeroArtwork(backdrop, KitsugiHeroArtworkResolver.getCached(item))
            }
        }
        val tried = mutableSetOf<String>()
        suspend fun firstUsable(urls: List<String?>): String? {
            for (candidate in urls) {
                val url = candidate?.let { homeHeroBackdropUrl(item.copy(backdropUrl = it)) } ?: continue
                if (!tried.add(url)) continue
                if (verifyBackdrop(url)) return url
            }
            return null
        }

        var artwork: KitsugiHeroArtworkResolver.HeroArtwork? = null
        val url = try {
            withTimeoutOrNull(18_000L) {
                val cached = KitsugiHeroArtworkResolver.getCached(item)
                artwork = cached
                firstUsable(listOf(item.backdropUrl, cached?.bestBackdropUrl) + cached?.backdropCandidates.orEmpty())
                    ?: KitsugiHeroArtworkResolver.resolveArtwork(item).also { artwork = it }.let { resolved ->
                        firstUsable(listOf(resolved.bestBackdropUrl) + resolved.backdropCandidates)
                    }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        resolvedBackdrops[key] = url.orEmpty()
        // Do not replace provider IDs: only the verified backdrop and the best poster change.
        return url?.let { item.withHeroArtwork(it, artwork) }
    }

    /**
     * Doğrulanmış backdrop'a ek olarak dikey vitrin bandı için en kaliteli posteri de
     * taşır: önce resolver'ın puanlanmış poster adayları (TMDB/Fanart `original`),
     * yoksa kaynağın kendi posterinin en yüksek çözünürlüklü varyantı. Backdrop ile
     * aynı görsel kimliğine sahip bir URL asla poster sayılmaz.
     */
    private fun JikanSearchResult.withHeroArtwork(
        backdrop: String,
        artwork: KitsugiHeroArtworkResolver.HeroArtwork?
    ): JikanSearchResult {
        val backdropIdentity = KitsugiHeroArtworkResolver.normalizeImageIdentity(backdrop)
        val poster = buildList {
            artwork?.bestPosterUrl?.let { add(it) }
            addAll(artwork?.posterCandidates.orEmpty())
            KitsugiHeroArtworkResolver.upgradeNativePosterUrl(imageUrl, source)?.let { add(it) }
        }.firstOrNull { candidate ->
            candidate.isNotBlank() &&
                (candidate.startsWith("https://", true) || candidate.startsWith("http://", true)) &&
                KitsugiHeroArtworkResolver.normalizeImageIdentity(candidate) != backdropIdentity
        }
        return copy(backdropUrl = backdrop, imageUrl = poster ?: imageUrl)
    }

    private suspend fun verifyBackdrop(url: String): Boolean {
        decodedBackdrops[url]?.let { return it }
        val valid = withTimeoutOrNull(5_000L) {
            val result = imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(480, 270)
                    .scale(Scale.FIT)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
            result is SuccessResult && isHorizontalHomeHeroArtwork(result.image.width, result.image.height)
        } ?: false
        decodedBackdrops[url] = valid
        return valid
    }
}
