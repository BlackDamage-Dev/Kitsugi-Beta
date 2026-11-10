package com.kitsugi.animelist.core.player.engine

import com.kitsugi.animelist.core.player.PlayerManagerListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Kurtarma zinciri yalnızca DAHİLİ motorlar arasındadır: MEDIA3 ↔ MPV.
 * Harici uygulama (EXTERNAL) zincirde yer almaz.
 */
class PlayerFallbackCoordinatorTest {

    @Test
    fun testFallbackChainDoesNotReturnToFailedInitialEngine() {
        val listener = mock<PlayerManagerListener>()
        val coordinator = PlayerFallbackCoordinator(maxAttempts = 3, listener = listener)

        // 1. adım: MEDIA3 -> MPV (ikinci dahili motor)
        val next1 = coordinator.getFallbackEngine(
            currentEngine = PlayerEngineType.MEDIA3,
            errorCode = 1001,
            mpvEnabled = true
        )
        assertEquals(PlayerEngineType.MPV, next1)
        verify(listener).onPlayerSwitched(PlayerEngineType.MEDIA3, PlayerEngineType.MPV)

        // 2. adım: başarısız MEDIA3'e geri dönme; her iki motor da denendi, zincir biter.
        val next2 = coordinator.getFallbackEngine(
            currentEngine = PlayerEngineType.MPV,
            errorCode = 1002,
            mpvEnabled = true
        )
        assertNull(next2)
        verify(listener).onFatalError(
            errorCode = 1002,
            errorMsg = "Fallback zinciri tükendi (son motor: MPV). Hata kodu: 1002"
        )
    }

    @Test
    fun testFallbackChainMpvUnavailable() {
        val listener = mock<PlayerManagerListener>()
        val coordinator = PlayerFallbackCoordinator(maxAttempts = 3, listener = listener)

        // MPV kullanılamıyorsa MEDIA3'ten sonra harici uygulamaya GEÇİLMEZ; zincir biter.
        val next = coordinator.getFallbackEngine(
            currentEngine = PlayerEngineType.MEDIA3,
            errorCode = 1001,
            mpvEnabled = false
        )
        assertNull(next)
        verify(listener).onFatalError(
            errorCode = 1001,
            errorMsg = "Fallback zinciri tükendi (son motor: MEDIA3). Hata kodu: 1001"
        )
    }

    @Test
    fun testMaxAttemptsLimit() {
        val listener = mock<PlayerManagerListener>()
        val coordinator = PlayerFallbackCoordinator(maxAttempts = 1, listener = listener)

        val next1 = coordinator.getFallbackEngine(PlayerEngineType.MEDIA3, 1001, mpvEnabled = true)
        assertEquals(PlayerEngineType.MPV, next1)
        assertEquals(1, coordinator.attemptCount)

        // Deneme limiti aşıldı: fatal, sayaç artmaz
        val next2 = coordinator.getFallbackEngine(PlayerEngineType.MPV, 1002, mpvEnabled = true)
        assertNull(next2)
        assertEquals(1, coordinator.attemptCount)
        verify(listener).onFatalError(
            errorCode = 1002,
            errorMsg = "Tüm dahili motorlar (1 deneme) başarısız oldu. Hata kodu: 1002"
        )
    }

    @Test
    fun testMpvFirstAlsoStopsAfterMedia3Fails() {
        val coordinator = PlayerFallbackCoordinator(maxAttempts = 3)
        assertEquals(
            PlayerEngineType.MEDIA3,
            coordinator.getFallbackEngine(PlayerEngineType.MPV, 1001, mpvEnabled = true)
        )
        assertNull(coordinator.getFallbackEngine(PlayerEngineType.MEDIA3, 1002, mpvEnabled = true))
    }

    @Test
    fun testResetClearsTriedEngines() {
        val coordinator = PlayerFallbackCoordinator(maxAttempts = 3)
        coordinator.getFallbackEngine(PlayerEngineType.MEDIA3, 1001, mpvEnabled = true)
        assertEquals(1, coordinator.attemptCount)

        coordinator.reset()
        assertEquals(0, coordinator.attemptCount)

        // Yeni kaynakta MEDIA3 -> MPV tekrar mümkün olmalı
        assertEquals(
            PlayerEngineType.MPV,
            coordinator.getFallbackEngine(PlayerEngineType.MEDIA3, 1001, mpvEnabled = true)
        )
    }

    @Test
    fun testStaticCompanionFallback() {
        assertEquals(PlayerEngineType.MPV, PlayerFallbackCoordinator.nextEngine(PlayerEngineType.MEDIA3, mpvEnabled = true))
        assertNull(PlayerFallbackCoordinator.nextEngine(PlayerEngineType.MEDIA3, mpvEnabled = false))
        assertEquals(PlayerEngineType.MEDIA3, PlayerFallbackCoordinator.nextEngine(PlayerEngineType.MPV, mpvEnabled = true))
        assertNull(PlayerFallbackCoordinator.nextEngine(PlayerEngineType.EXTERNAL, mpvEnabled = true))
    }
}
