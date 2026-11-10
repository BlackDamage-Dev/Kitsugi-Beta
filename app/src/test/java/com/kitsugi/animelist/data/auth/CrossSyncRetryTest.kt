package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CrossSyncRetry] sınıflandırıcısının geçici (tekrarlanabilir) vs. kalıcı hata ayrımını doğrular.
 * Bu sınıflandırma, çapraz eşitleme yazma sırasında hangi hataların yeniden deneneceğini belirler.
 */
class CrossSyncRetryTest {

    /** Ülke extention üyesini [CrossSyncRetry] bağlamında çağırmak için yardımcı. */
    private fun Throwable.transientError(): Boolean {
        val self = this
        return with(CrossSyncRetry) { self.isTransientSyncError() }
    }

    @Test
    fun httpTransientCodesInTextAreRetryable() {
        assertTrue(CrossSyncRetry.isTransientErrorText("HTTP 429 (hız sınırı)"))
        assertTrue(CrossSyncRetry.isTransientErrorText("Kitsu güncellenemedi: HTTP 500"))
        assertTrue(CrossSyncRetry.isTransientErrorText("HTTP 503"))
        assertTrue(CrossSyncRetry.isTransientErrorText("HTTP 408"))
        assertTrue(CrossSyncRetry.isTransientErrorText("HTTP 425"))
        assertTrue(CrossSyncRetry.isTransientErrorText("MAL API hatası: 503"))
        assertTrue(CrossSyncRetry.isTransientErrorText("Bangumi istek sınırına takıldı (429)"))
        assertTrue(CrossSyncRetry.isTransientErrorText("Bangumi sunucusu geçici hata verdi (HTTP 502)"))
        assertTrue(CrossSyncRetry.isTransientErrorText("HTTP 504 Gateway Timeout"))
    }

    @Test
    fun httpPersistentCodesInTextAreNotRetryable() {
        assertFalse(CrossSyncRetry.isTransientErrorText("HTTP 400"))
        assertFalse(CrossSyncRetry.isTransientErrorText("HTTP 401"))
        assertFalse(CrossSyncRetry.isTransientErrorText("HTTP 403"))
        assertFalse(CrossSyncRetry.isTransientErrorText("HTTP 404"))
        assertFalse(CrossSyncRetry.isTransientErrorText("HTTP 422: cannot exceed length of media"))
        assertFalse(CrossSyncRetry.isTransientErrorText("Kitsu ID bulunamadı"))
        assertFalse(CrossSyncRetry.isTransientErrorText(""))
        assertFalse(CrossSyncRetry.isTransientErrorText("   "))
    }

    @Test
    fun networkAndServerKeywordsAreRetryable() {
        assertTrue(CrossSyncRetry.isTransientErrorText("AniList bağlantı hatası: Failed to connect"))
        assertTrue(CrossSyncRetry.isTransientErrorText("service down: temporarily disabled due to severe stability issues"))
        assertTrue(CrossSyncRetry.isTransientErrorText("timeout"))
        assertTrue(CrossSyncRetry.isTransientErrorText("connection refused"))
        assertTrue(CrossSyncRetry.isTransientErrorText("temporarily disabled"))
    }

    @Test
    fun ioExceptionIsTransient() {
        assertTrue(java.io.IOException("connection reset").transientError())
        assertTrue(java.io.IOException("unable to resolve host").transientError())
    }

    @Test
    fun cancellationIsNotTransient() {
        assertFalse(java.util.concurrent.CancellationException("cancelled").transientError())
    }

    @Test
    fun bangumiExceptionClassificationUsesHttpCode() {
        assertTrue(BangumiApiClient.BangumiApiException(429, null, null, "HTTP 429").transientError())
        assertTrue(BangumiApiClient.BangumiApiException(500, null, null, "HTTP 500").transientError())
        assertTrue(BangumiApiClient.BangumiApiException(503, null, null, "HTTP 503").transientError())
        assertTrue(BangumiApiClient.BangumiApiException(408, null, null, "HTTP 408").transientError())
        assertFalse(BangumiApiClient.BangumiApiException(400, null, null, "HTTP 400").transientError())
        assertFalse(BangumiApiClient.BangumiApiException(401, null, null, "HTTP 401").transientError())
        assertFalse(BangumiApiClient.BangumiApiException(403, null, null, "HTTP 403").transientError())
        assertFalse(BangumiApiClient.BangumiApiException(404, null, null, "HTTP 404").transientError())
    }

    @Test
    fun genericIllegalStateWithTransientMessageIsRetryable() {
        assertTrue(IllegalStateException("AniList API hatası: 429 hız sınırı (3 deneme)").transientError())
        assertTrue(IllegalStateException("MAL API hatası: 503 <html>").transientError())
        assertFalse(IllegalStateException("MAL API hatası: 400 Bad Request").transientError())
        assertFalse(IllegalStateException("AniList medya kimliği çözülemedi").transientError())
    }
}
