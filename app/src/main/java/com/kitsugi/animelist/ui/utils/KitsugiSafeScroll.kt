package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * ÇÖKME KORUMALI DİKEY KAYDIRMA
 * ─────────────────────────────────────────────────────────────────────────────
 * `Modifier.verticalScroll` sınırsız (Infinity) yükseklik kısıtı aldığında Compose
 * şu istisnayı fırlatır ve **süreç ölür**:
 *
 *   java.lang.IllegalStateException: Vertically scrollable component was measured
 *   with an infinity maximum height constraints, which is disallowed...
 *
 * Tipik sebep: bir `Column(Modifier.verticalScroll(...))` içine (doğrudan ya da
 * dolaylı olarak) ikinci bir dikey kaydırma konteyneri koymak — ör. bir alt sayfa /
 * sheet içeriğinin kendisi kaydırılabilirken, onu barındıran sayfanın da kaydırılabilir
 * olması, ya da `LazyColumn`/`Row` içindeki bir öğenin kaydırılabilir bir sütun
 * çizmesi. Hangi ekranın bu hataya düştüğünü tek tek takip etmek yerine, kaydırma
 * düğümünün **hiçbir zaman sınırsız yükseklik görmemesini** garanti ediyoruz.
 *
 * Nasıl çalışır?
 *  - `verticalScroll`'un **dışına** bir layout düğümü eklenir.
 *  - Gelen kısıt sınırlıysa (normal durum) hiçbir şey değişmez; ölçüm birebir aynıdır.
 *  - Gelen kısıt sınırsızsa, yükseklik ekran yüksekliğine kırpılarak alt düğüme
 *    iletilir. Böylece kaydırma çalışmaya devam eder, uygulama çökmez.
 *
 * Davranış farkı yalnızca "zaten çökecek olan" yerleşimlerde ortaya çıkar.
 */
fun Modifier.kitsugiVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = composed {
    val fallbackMaxHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.roundToPx()
    }

    this
        .layout { measurable, constraints ->
            val safeConstraints = if (constraints.hasBoundedHeight) {
                constraints
            } else {
                constraints.copy(
                    maxHeight = maxOf(constraints.minHeight, fallbackMaxHeightPx)
                )
            }
            val placeable = measurable.measure(safeConstraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .verticalScroll(
            state = state,
            enabled = enabled,
            flingBehavior = flingBehavior,
            reverseScrolling = reverseScrolling,
        )
}

/**
 * Sınırsız yükseklik kısıtını ekran yüksekliğine kırpar; kaydırma eklemeyen sürüm.
 * `LazyColumn`/`LazyVerticalGrid` gibi kendi kaydırmasını yapan bileşenleri, kaydırılabilir
 * bir üst konteynerin içinde güvenli hâle getirmek için kullanılır.
 */
fun Modifier.kitsugiClampUnboundedHeight(): Modifier = composed {
    val fallbackMaxHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.roundToPx()
    }

    this.layout { measurable, constraints ->
        val safeConstraints = if (constraints.hasBoundedHeight) {
            constraints
        } else {
            constraints.copy(maxHeight = maxOf(constraints.minHeight, fallbackMaxHeightPx))
        }
        val placeable = measurable.measure(safeConstraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
