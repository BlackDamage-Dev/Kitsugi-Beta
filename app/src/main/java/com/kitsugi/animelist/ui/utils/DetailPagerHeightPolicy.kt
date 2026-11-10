package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Detay sayfalarındaki sekme pager'ının yükseklik politikası.
 *
 * ## Neden var? (KASMA FIX — 2026-11-05)
 *
 * Detay ekranlarında (API kaynaklı, liste kaynaklı, karakter, ekip) sekme içeriği
 * kaydırılabilir bir `LazyColumn`'ın tek bir öğesi içinde yaşıyor ve
 * `HorizontalPager` içerik yüksekliğine göre boyutlandırılıyor.
 *
 * Eski uygulamada yükseklik şöyle hesaplanıyordu:
 *
 * ```
 * val offset = pagerState.currentPageOffsetFraction   // ← KOMPOZİSYONDA okuma
 * val targetPage = if (offset > 0f) current + 1 else current - 1
 * val interpolated = remember(...) { ... }
 * HorizontalPager(modifier = Modifier.layout { ... interpolated ... })
 * ```
 *
 * `currentPageOffsetFraction` parmak hareket ettikçe **her karede** değişir.
 * Kompozisyonda okunduğu için kaydırma boyunca her karede şunlar tekrar çalışıyordu:
 *
 * 1. `item(key = "content")` kapsamının yeniden kompozisyonu,
 * 2. dolayısıyla `HorizontalPager`'ın yeniden çalışması ve sayfa içerik
 *    lambda'larının yeniden oluşması,
 * 3. dolayısıyla görünür sekmenin (245 görselli Resimler galerisi gibi) yeniden
 *    kompozisyonu,
 * 4. `Modifier.layout` içinde sınırsız (`Constraints.Infinity`) yükseklikle
 *    yeniden ölçüm.
 *
 * Kullanıcı raporlarındaki takılmalar bu zincirden geliyordu.
 *
 * ## Yeni yaklaşım
 *
 * Yükseklik bir [derivedStateOf] içinde tutulur ve **yalnızca yerleşim (layout)
 * fazında** okunur. Compose snapshot sistemi faz ile okumayı eşleştirir:
 * layout fazındaki okuma sadece layout'u geçersiz kılar, kompozisyonu atlar.
 * Böylece sekme geçişleri yalnızca yeniden yerleşim üretir; sekme içerikleri
 * kompoze edilmez.
 */
@Composable
fun rememberDetailPagerContentHeightPx(
    pagerState: PagerState,
    pageHeights: Map<Int, Int>
): State<Int> = remember(pagerState, pageHeights) {
    derivedStateOf {
        val currentPage = pagerState.currentPage
        val offsetFraction = pagerState.currentPageOffsetFraction
        val targetPage = when {
            offsetFraction > 0f -> currentPage + 1
            offsetFraction < 0f -> currentPage - 1
            else -> currentPage
        }
        val currentHeightPx = pageHeights[currentPage] ?: 0
        val targetHeightPx = pageHeights[targetPage] ?: currentHeightPx
        when {
            currentHeightPx > 0 && targetHeightPx > 0 ->
                (currentHeightPx + (targetHeightPx - currentHeightPx) * kotlin.math.abs(offsetFraction)).toInt()
            currentHeightPx > 0 -> currentHeightPx
            targetHeightPx > 0 -> targetHeightPx
            else -> 0
        }
    }
}

/**
 * Pager'ın asgari yüksekliği: ekran yüksekliği eksi 64 dp (üst bar payı).
 * Dp → px dönüşümü her kompozisyonda tekrar yapılmasın diye burada hatırlanır.
 */
@Composable
fun rememberDetailPagerMinHeightPx(): Int {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    return remember(screenHeightDp, density) {
        with(density) { (screenHeightDp - 64).dp.roundToPx() }.coerceAtLeast(0)
    }
}

/**
 * Pager'ı ölçerken sınırsız yükseklik verir (içerik doğal yüksekliğini bildirsin),
 * ardından yerleşim fazında okunan [heightPx] değerine göre boyutlandırır.
 * Layout fazında okuma yapıldığı için kaydırma sırasında kompozisyon tetiklenmez.
 */
fun Modifier.detailPagerHeight(heightPx: State<Int>, minHeightPx: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = minHeightPx,
                maxHeight = Constraints.Infinity
            )
        )
        val targetHeightPx = heightPx.value
        val height = (if (targetHeightPx > 0) targetHeightPx else placeable.height)
            .coerceAtLeast(minHeightPx)
            .coerceAtMost(16_000_000)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
