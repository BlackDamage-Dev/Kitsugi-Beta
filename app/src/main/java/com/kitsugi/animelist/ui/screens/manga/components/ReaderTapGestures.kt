package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Okuyucu dokunma / uzun basma algılayıcısı — zoom'lu çocukların ÜSTÜNDE çalışır.
 *
 * NEDEN INITIAL PASS?
 * -------------------
 * Telephoto `ZoomableAsyncImage`, Main pass'ta pointer olaylarını tüketir.
 * Bu yüzden bir ebeveyne (ör. iki sayfa yan yana `Row`'una) kurulan
 * `detectTapGestures` hiçbir zaman tüketilmemiş down/up göremez ve dokunmalar
 * sessizce ölür. Bu gözlemci olayları **Initial** pass'ta (çocuklardan ÖNCE,
 * `requireUnconsumed = false`) izler ve kendisi hiçbir olayı tüketmez:
 *
 *  • Tek parmak, hareket eşiğini aşmayan temiz dokunuş → bölge/ızgara eylemi
 *  • Çoklu dokunuş (pinch zoom) veya sürükleme (pager kaydırması) → yok sayılır,
 *    çocuk zoom/kaydırma hareketleri aynen çalışmaya devam eder
 *  • Uzun basma → gerçek zaman aşımında tetiklenir (parmağın kıpırdaması gerekmez);
 *    Custom düzeninde uzun-basma ızgarası varsa o eylem, yoksa [onLongPress]
 *
 * [onAction] tek dokunuş eylemini, [onLongPress] varsayılan uzun basmayı
 * (dokunulan noktanın yatay oranı ile) bildirir. [yFraction], Webtoon'daki uzun
 * görsellerde dokunma Y'sini görünür pencereye oranlamak için kullanılır.
 *
 * Not: Geri çağrırmalar pointerInput bloğu yeniden başladığında eski kapanışı
 * koruyabilir; çağıranlar güncel lambda'ları `rememberUpdatedState` üzerinden
 * okumalıdır (MangaPageItem / MangaPagerReader böyle yapar).
 */
internal fun Modifier.readerTapGestures(
    tapZonesEnabled: Boolean,
    rtl: Boolean,
    invertTapZones: Boolean,
    tapLayout: String,
    tapGrid: String,
    longTapGrid: String,
    haptics: HapticFeedback,
    yFraction: (Offset, Int) -> Float = { offset, height -> offset.y / height.coerceAtLeast(1) },
    onAction: (ReaderTapAction) -> Unit,
    onLongPress: (Float) -> Unit,
): Modifier = pointerInput(tapZonesEnabled, rtl, invertTapZones, tapLayout, tapGrid, longTapGrid) {
    val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        while (true) {
            awaitReaderTapGesture(
                tapZonesEnabled, rtl, invertTapZones, tapLayout, tapGrid, longTapGrid,
                longPressTimeoutMillis, touchSlop, haptics, yFraction, onAction, onLongPress,
            )
        }
    }
}

/**
 * Tek bir jesti bekler ve sonuçlandırır.
 *
 * [AwaitPointerEventScope] `@RestrictsSuspension` olduğundan zaman aşımı yarışı
 * (`withTimeoutOrNull`) doğrudan `awaitPointerEventScope { }` lambda'sında
 * çağrılamaz; androidx'in kendi `waitForLongPress` kalıbındaki gibi scope'un
 * extension fonksiyonu gövdesinde serbestçe çağrılabilir.
 */
private suspend fun AwaitPointerEventScope.awaitReaderTapGesture(
    tapZonesEnabled: Boolean,
    rtl: Boolean,
    invertTapZones: Boolean,
    tapLayout: String,
    tapGrid: String,
    longTapGrid: String,
    longPressTimeoutMillis: Long,
    touchSlop: Float,
    haptics: HapticFeedback,
    yFraction: (Offset, Int) -> Float,
    onAction: (ReaderTapAction) -> Unit,
    onLongPress: (Float) -> Unit,
) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val downPosition = down.position
    var multiTouch = false
    var moved = false
    var released = false
    var upPosition = downPosition

    // Parmağın kalkmasını / hareketi / ikinci parmağı bekle; süre dolarsa
    // withTimeoutOrNull null döner ve uzun basma tetiklenir.
    withTimeoutOrNull(longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) {
                multiTouch = true
                break
            }
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                // Sistem hareketi iptal etti; ne dokunuş ne uzun basma üret.
                moved = true
                released = true
                break
            }
            if ((change.position - downPosition).getDistance() > touchSlop) {
                moved = true
                break
            }
            if (!change.pressed) {
                released = true
                upPosition = change.position
                break
            }
        }
    }

    fun xFraction(offset: Offset): Float = offset.x / size.width.coerceAtLeast(1)

    if (!released && !multiTouch && !moved) {
        // Parmak hâlâ basılı ve süre doldu → uzun basma.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val longAction = readerLongTapAction(
            tapLayout, longTapGrid, rtl, invertTapZones,
            xFraction(downPosition), yFraction(downPosition, size.height),
        )
        if (longAction != null) onAction(longAction) else onLongPress(xFraction(downPosition))
    } else if (released && !multiTouch && !moved) {
        val action = if (!tapZonesEnabled || size.width <= 0) {
            ReaderTapAction.MENU
        } else {
            readerTapAction(
                tapLayout, rtl, invertTapZones,
                xFraction(upPosition), yFraction(upPosition, size.height), tapGrid,
            )
        }
        onAction(action)
    }

    if (!released) {
        // Sürükleme / pinch / uzun basma: tüm parmaklar kalkana kadar yok say.
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.all { !it.pressed }) break
        }
    }
}
