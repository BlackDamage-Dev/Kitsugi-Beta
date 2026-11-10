package com.kitsugi.animelist.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor'ı: 5xx, 429 ve IOException durumlarında
 * üstel geri çekilme (exponential backoff) ile yeniden deneme.
 *
 * — 5xx hatası → 300ms, 600ms gecikme ile 2 retry
 * — 429 Too Many Requests → Retry-After'ın ilk beklemesi en fazla 5 sn; Simkl gate kalan
 *   Retry-After cooldown'unu tüm Simkl çağrıları için korur.
 * — IOException → 400ms, 800ms gecikme ile 2 retry
 *
 * Bekleme yalnızca OkHttp dispatcher worker'ında yapılır, ana/UI thread'inde değil.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {

    companion object {
        private const val TAG = "RetryInterceptor"
        // Retry çağrısındaki ilk beklemeyi 5 sn ile sınırla; Simkl'ın ortak kapısı, Simkl
        // Retry-After daha uzunsa sonraki denemeyi yine de cooldown bitene kadar bekletir.
        private const val MAX_RETRY_AFTER_MS = 5_000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: java.io.IOException? = null

        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                Log.d(TAG, "Retry $attempt/${maxRetries} for ${request.url}")
            }

            if (chain.call().isCanceled()) {
                throw java.io.IOException("Canceled")
            }

            try {
                val response = chain.proceed(request)

                // 429 Too Many Requests — honor Retry-After. The Simkl gate also shares this
                // cooldown with requests from other screens and retries.
                if (response.code == 429 && attempt < maxRetries) {
                    val delayMs = SimklRateLimitPolicy.retryAfterMillis(response.header("Retry-After"))
                        ?.coerceAtMost(MAX_RETRY_AFTER_MS) ?: 3_000L
                    Log.w(TAG, "429 alındı. Retry-After: ${delayMs}ms — ${request.url.host}${request.url.encodedPath}")
                    response.close()
                    sleepBeforeRetry(chain, delayMs)
                    return@repeat // devam et, retry
                }

                // Simkl reports its per-user write lock as HTTP 400 + `rate_limit` (not 429).
                // The provider asks clients to wait and retry once, rather than immediately
                // repeating the write until it is accepted.
                if (attempt == 0 && attempt < maxRetries && isSimklWriteRateLimited(request, response)) {
                    val delayMs = SimklRateLimitPolicy.retryAfterMillis(response.header("Retry-After"))
                        ?.coerceAtMost(MAX_RETRY_AFTER_MS) ?: SimklRateLimitPolicy.DEFAULT_COOLDOWN_MS
                    Log.w(TAG, "Simkl write rate limit. Waiting ${delayMs}ms before one retry")
                    response.close()
                    sleepBeforeRetry(chain, delayMs)
                    return@repeat
                }

                // 5xx Server Error — exponential backoff (max 600ms)
                if (response.code in 500..599 && attempt < maxRetries) {
                    val delayMs = 300L * (1L shl attempt) // 300ms → 600ms
                    Log.w(TAG, "${response.code} server error. Retry after ${delayMs}ms — ${request.url}")
                    response.close()
                    if (chain.call().isCanceled()) {
                        throw java.io.IOException("Canceled")
                    }
                    Thread.sleep(delayMs)
                    return@repeat // devam et, retry
                }

                // Başarılı ya da son deneme — response'u döndür
                return response

            } catch (e: java.io.IOException) {
                if (e.message == "Canceled" || chain.call().isCanceled()) {
                    throw java.io.IOException("Canceled", e)
                }
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = 400L * (1L shl attempt) // 400ms → 800ms
                    Log.w(TAG, "IOException (attempt $attempt): ${e.message}. Retry after ${delayMs}ms — ${request.url}")
                    if (chain.call().isCanceled()) {
                        throw java.io.IOException("Canceled")
                    }
                    Thread.sleep(delayMs)
                }
            }
        }

        // Tüm denemeler bitti, son exception'ı fırlat
        throw lastException ?: IOException("Request failed after $maxRetries retries: ${request.url}")
    }

    private fun isSimklWriteRateLimited(
        request: okhttp3.Request,
        response: Response,
    ): Boolean =
        request.url.host.equals("api.simkl.com", ignoreCase = true) &&
            !request.method.equals("GET", ignoreCase = true) &&
            !request.method.equals("HEAD", ignoreCase = true) &&
            response.code == 400 &&
            SimklRateLimitPolicy.isRateLimitedResponse(response)

    private fun sleepBeforeRetry(chain: Interceptor.Chain, delayMillis: Long) {
        var remaining = delayMillis
        while (remaining > 0L) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val slice = minOf(remaining, 100L)
            try {
                Thread.sleep(slice)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Canceled", interrupted)
            }
            remaining -= slice
        }
    }
}
