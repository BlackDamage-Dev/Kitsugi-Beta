package com.kitsugi.animelist.core.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Small, safe helpers shared by the Simkl request gate, retry interceptor, and error reporter. */
internal object SimklRateLimitPolicy {
    const val DEFAULT_COOLDOWN_MS = 3_000L
    const val MAX_COOLDOWN_MS = 30 * 60_000L
    const val MAX_ERROR_BODY_BYTES = 8 * 1024L

    fun isRateLimitMessage(message: String): Boolean {
        val normalized = message.lowercase(Locale.ROOT)
        return "rate_limit" in normalized ||
            "rate limit" in normalized ||
            "too many requests" in normalized
    }

    /** Parses both the delta-seconds and HTTP-date forms allowed by Retry-After. */
    fun retryAfterMillis(value: String?, nowEpochMillis: Long = System.currentTimeMillis()): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        raw.toLongOrNull()?.let { seconds ->
            if (seconds < 0L) return null
            return seconds.coerceAtMost(MAX_COOLDOWN_MS / 1_000L) * 1_000L
        }
        return runCatching {
            val until = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            (until - nowEpochMillis).coerceAtLeast(0L).coerceAtMost(MAX_COOLDOWN_MS)
        }.getOrNull()
    }

    fun isRateLimitedResponse(response: Response): Boolean {
        if (response.code == 429) return true
        if (response.code != 400) return false
        val message = runCatching {
            response.peekBody(MAX_ERROR_BODY_BYTES).string()
        }.getOrDefault("")
        return isRateLimitMessage(message)
    }
}

/**
 * Process-wide Simkl pacing state. It is deliberately shared by all SimklApiClient instances
 * and all features using KitsugiHttpClient; an instance-local delay lets parallel screens evade
 * the provider's client-id/user limits.
 */
internal class SimklRequestRateGate(
    private val nowElapsedMillis: () -> Long,
    private val sleep: (Long) -> Unit,
    private val getIntervalMillis: Long = 350L,
    private val writeIntervalMillis: Long = 1_250L,
    private val defaultCooldownMillis: Long = SimklRateLimitPolicy.DEFAULT_COOLDOWN_MS,
    private val maxCooldownMillis: Long = SimklRateLimitPolicy.MAX_COOLDOWN_MS,
) {
    private val stateLock = Any()
    private var nextGetAt = 0L
    private var nextWriteAt = 0L
    private var cooldownUntil = 0L

    /**
     * Blocks only an OkHttp worker thread, never the UI thread. The loop rechecks shared state
     * after each short sleep so a 429 from another in-flight request extends the wait immediately.
     */
    fun acquire(method: String, isCancelled: () -> Boolean = { false }) {
        val isWrite = !method.equals("GET", ignoreCase = true) &&
            !method.equals("HEAD", ignoreCase = true)
        val interval = if (isWrite) writeIntervalMillis else getIntervalMillis

        while (true) {
            if (isCancelled()) throw IOException("Canceled")
            val waitMillis = synchronized(stateLock) {
                val now = nowElapsedMillis()
                val nextForMethod = if (isWrite) nextWriteAt else nextGetAt
                val allowedAt = maxOf(cooldownUntil, nextForMethod)
                if (allowedAt <= now) {
                    if (isWrite) nextWriteAt = now + interval else nextGetAt = now + interval
                    0L
                } else {
                    allowedAt - now
                }
            }

            if (waitMillis <= 0L) return
            if (isCancelled()) throw IOException("Canceled")
            try {
                // Check cancellation/cooldown frequently without busy waiting.
                sleep(minOf(waitMillis, CANCELLATION_CHECK_INTERVAL_MS))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Canceled", interrupted)
            }
        }
    }

    /** Extend a shared cooldown after Simkl says this client/user must slow down. */
    fun noteRateLimited(retryAfterMillis: Long? = null) {
        val requestedWait = retryAfterMillis
            ?.takeIf { it > 0L }
            ?.coerceAtMost(maxCooldownMillis)
            ?: defaultCooldownMillis.coerceAtMost(maxCooldownMillis)
        synchronized(stateLock) {
            cooldownUntil = max(cooldownUntil, nowElapsedMillis() + requestedWait)
        }
    }

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL_MS = 250L
    }
}

/**
 * Centralized request pacing for every `api.simkl.com` call made through KitsugiHttpClient.
 * GETs are held below the documented 10/sec ceiling with margin; writes are serialized and
 * spaced at 1.25s. Any 429 (or Simkl's 400 `rate_limit` write-lock response) pauses all Simkl
 * API traffic and respects Retry-After.
 */
private object SimklRateLimitSharedState {
    val writeSemaphore = Semaphore(1, true)
    val gate = SimklRequestRateGate(
        nowElapsedMillis = { SystemClock.elapsedRealtime() },
        sleep = { millis -> Thread.sleep(millis) },
    )
}

internal class SimklRateLimitInterceptor : Interceptor {
    private val gate = SimklRateLimitSharedState.gate

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.equals(API_HOST, ignoreCase = true)) {
            return chain.proceed(request)
        }

        val call = chain.call()
        return if (isWrite(request.method)) {
            // The server's sync endpoints also serialize writes per account. Keep writes from
            // separate app flows from overlapping locally, not just from starting in the same ms.
            acquireWriteSlot(call)
            try {
                gate.acquire(request.method) { call.isCanceled() }
                chain.proceed(request).also(::recordRateLimit)
            } finally {
                SimklRateLimitSharedState.writeSemaphore.release()
            }
        } else {
            gate.acquire(request.method) { call.isCanceled() }
            chain.proceed(request).also(::recordRateLimit)
        }
    }

    private fun recordRateLimit(response: Response) {
        if (SimklRateLimitPolicy.isRateLimitedResponse(response)) {
            gate.noteRateLimited(
                SimklRateLimitPolicy.retryAfterMillis(response.header("Retry-After"))
            )
        }
    }

    private fun acquireWriteSlot(call: okhttp3.Call) {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")
            try {
                if (SimklRateLimitSharedState.writeSemaphore.tryAcquire(250L, TimeUnit.MILLISECONDS)) return
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Canceled", interrupted)
            }
        }
    }

    private fun isWrite(method: String): Boolean =
        !method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)

    private companion object {
        const val API_HOST = "api.simkl.com"
    }
}
