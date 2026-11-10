package com.kitsugi.animelist.data.manga

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SourceHealthService(
    context: Context,
    private val stateStore: MangaSourceStateStore = MangaSourceStateStore(context),
    private val mirrorResolver: SourceMirrorResolver = SourceMirrorResolver(context),
) {

    private val tag = "SourceHealthService"

    /**
     * Kaynak diline gore olasi en yaygin eser adlari.
     *
     * NEDEN: Tek bir sabit sorgu ("one piece") ile tarama yapmak yanlis negatif uretir —
     * bir eserin her sitede bulunmasi zorunlu degildir. Bu yuzden liste sirayla denenir
     * ve ilk dolu sonuc veren sorgu ile zincirin devamina gecilir.
     */
    private fun probeQueriesFor(source: MangaSource): List<String> =
        if (source.lang.equals("tr", ignoreCase = true)) {
            listOf("oyuncu", "shadow slave", "solo leveling", "one piece", "naruto")
        } else {
            listOf("one piece", "naruto", "solo leveling")
        }

    suspend fun quickCheck(
        source: MangaSource,
        sampleQuery: String? = null,
    ): SourceHealthStatus = withContext(Dispatchers.IO) {
        val queries = sampleQuery?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            ?: probeQueriesFor(source)
        val initial = evaluate(source, queries)
        if (initial == SourceHealthStatus.Broken || initial == SourceHealthStatus.Degraded) {
            val recovered = mirrorResolver.tryResolveAndActivateMirror(source)
            if (recovered) {
                val retried = evaluate(source, queries)
                stateStore.setHealthStatus(source, retried, reason = "mirror_recheck")
                return@withContext retried
            }
        }
        stateStore.setHealthStatus(source, initial)
        initial
    }

    private suspend fun evaluate(source: MangaSource, queries: List<String>): SourceHealthStatus {
        return try {
            // Sorgulari sirayla dene; ilk dolu sonucu kullan. Bos sonuc tek basina
            // "kaynak bozuk" demek degildir (eser o sitede olmayabilir).
            var search: List<MangaDetails> = emptyList()
            for (query in queries) {
                val found = withTimeoutOrNull(12_000L) {
                    source.fetchSearchManga(page = 1, query = query).mangas
                }
                if (found == null) {
                    Log.w(tag, "${source.name}: '$query' aramasi zaman asimina ugradi")
                    continue
                }
                if (found.isNotEmpty()) {
                    search = found
                    Log.d(tag, "${source.name}: '$query' -> ${found.size} sonuc")
                    break
                }
            }

            if (search.isEmpty()) {
                search = withTimeoutOrNull(12_000L) {
                    source.fetchPopularManga(page = 1).mangas
                }.orEmpty()
            }

            if (search.isEmpty()) {
                return SourceHealthStatus.Degraded
            }

            val first = search.first()
            val details = withTimeoutOrNull(12_000L) {
                source.fetchMangaDetails(first.url)
            } ?: return SourceHealthStatus.Degraded

            val chapters = withTimeoutOrNull(15_000L) {
                source.fetchChapterList(details.url)
            } ?: return SourceHealthStatus.Degraded

            if (chapters.isEmpty()) {
                return SourceHealthStatus.Degraded
            }

            val pages = withTimeoutOrNull(15_000L) {
                source.fetchPageList(chapters.first())
            } ?: return SourceHealthStatus.Degraded

            if (pages.isEmpty()) {
                return SourceHealthStatus.Degraded
            }

            val imageUrl = withTimeoutOrNull(10_000L) {
                source.fetchImageUrl(pages.first())
            } ?: return SourceHealthStatus.Degraded

            if (imageUrl.isBlank()) return SourceHealthStatus.Degraded
            // A resolved URL is not proof of a working reader (HTTP 200 HTML,
            // hotlink denial and corrupt CDN images all occurred at this stage).
            val imageValid = withTimeoutOrNull(15_000L) {
                val page = pages.first().apply { this.imageUrl = imageUrl }
                source.getImage(page).buffered().use { stream ->
                    // Sniff before BitmapFactory, which cannot inspect AVIF/JXL on older Android.
                    stream.mark(64)
                    val native = com.kitsugi.animelist.data.manga.image.MangaNativeFormats.supports(stream)
                    stream.reset()
                    if (native) {
                        com.kitsugi.animelist.data.manga.image.MangaNativeFormats.dimensions(stream) != null
                    } else {
                        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeStream(stream, null, options)
                        options.outWidth > 0 && options.outHeight > 0
                    }
                }
            } ?: false
            if (imageValid) SourceHealthStatus.Healthy else SourceHealthStatus.Degraded
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(tag, "quickCheck failed for ${source.name}: ${e.message}")
            classify(e)
        }
    }

    private fun classify(error: Throwable): SourceHealthStatus {
        val message = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
        }.lowercase()

        return when {
            message.contains("cloudflare") || message.contains("captcha") -> SourceHealthStatus.CaptchaRequired
            message.contains("429") || message.contains("too many requests") -> SourceHealthStatus.RateLimited
            message.contains("404") || message.contains("not found") || message.contains("unable to resolve host") -> SourceHealthStatus.Broken
            else -> SourceHealthStatus.Degraded
        }
    }
}
