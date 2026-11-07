package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Oturumluk otomatik kaydırma — ekranın kare saatiyle (frame clock) sürülür.
 *
 * Eski sürüm `delay(50)` + ayrık `scrollBy` sıçramalarıyla en fazla 20 Hz
 * "merdiven" üretiyordu; ağır webtoon sayfalarında gözle görülür şekilde
 * tekliyordu. Yeni sürüm tek bir scroll mutasyonu içinde HER karede
 * (60–120 Hz) `hız × geçen süre` kadar ilerler → kesintisiz, pürüzsüz akış.
 *
 * Kullanıcı dokunup kaydırırsa (UserInput önceliği) mutasyonumuz iptal edilir;
 * döngü ölmez, hareket yatışınca kaldığı yerden devam eder. Kesirli pikseller
 * elde tutulur (carry), kare gecikmeleri 100 ms'e kelepçelenir; böylece yavaş
 * hızlar da şaşmaz, uzun bir duraksama sonrası sayfa fırlamaz.
 */
@Composable
internal fun WebtoonAutoScroll(
    enabled: Boolean,
    listState: LazyListState,
    speedDpPerSecond: Int,
    onEnd: () -> Unit,
) {
    val pixelsPerSecond = autoScrollPixelsPerSecond(speedDpPerSecond, LocalDensity.current.density)
    val currentOnEnd by rememberUpdatedState(onEnd)
    LaunchedEffect(enabled, listState, pixelsPerSecond) {
        if (!enabled || pixelsPerSecond <= 0f) return@LaunchedEffect
        while (isActive) {
            // Kullanıcı kaydırması/fling'i listeye sahip; yatışmasını bekle.
            if (listState.isScrollInProgress) {
                delay(80)
                continue
            }
            if (!listState.canScrollForward) {
                currentOnEnd()
                break
            }
            var reachedEnd = false
            // Mutasyonu çocuk korutine izole et: kullanıcı hareketi mutasyonu
            // iptal ettiğinde bu döngü hayatta kalır ve sonrasında devam eder.
            coroutineScope {
                launch {
                    listState.scroll(MutatePriority.Default) {
                        var previousFrame = withFrameNanos { it }
                        var carry = 0f
                        while (isActive) {
                            val frame = withFrameNanos { it }
                            carry += autoScrollFramePixels(pixelsPerSecond, frame - previousFrame)
                            previousFrame = frame
                            if (carry > 0f) {
                                val consumed = scrollBy(carry)
                                carry = (carry - consumed).coerceAtLeast(0f)
                            }
                            if (!listState.canScrollForward) {
                                reachedEnd = true
                                return@scroll
                            }
                        }
                    }
                }.join()
            }
            if (reachedEnd) {
                currentOnEnd()
                break
            }
        }
    }
}

/** Ekran yoğunluğundan bağımsız, sınırlanmış hız (piksel/sn). */
internal fun autoScrollPixelsPerSecond(speedDpPerSecond: Int, density: Float): Float =
    speedDpPerSecond.coerceIn(10, 500) * density.coerceAtLeast(0f)

/**
 * Bir karede ilerlenecek piksel: hız × geçen süre. Süre [0, 100 ms] aralığına
 * kelepçelenir; GC/duraksama sonrası tek karede büyük sıçrama olmaz.
 */
internal fun autoScrollFramePixels(pixelsPerSecond: Float, elapsedNanos: Long): Float =
    pixelsPerSecond.coerceAtLeast(0f) * (elapsedNanos.coerceIn(0L, 100_000_000L) / 1_000_000_000f)
