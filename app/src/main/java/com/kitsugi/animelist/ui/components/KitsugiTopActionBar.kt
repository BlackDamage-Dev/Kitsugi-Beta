package com.kitsugi.animelist.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * KITSUGI ORTAK ÜST ÇUBUK AKSİYONLARI
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uygulamadaki üst çubuk (sağ üst) aksiyon butonları **tek bir kaynaktan** üretilir:
 * sıraları, boyutları, köşe yarıçapları, arka planı ve ikon seti her ekranda aynıdır.
 *
 * Sıra kuralı (kullanıcı beklentisi):
 *   Filtre → Yenile → Geçmiş → İndirmeler → Eklentiler → İstatistik → Paylaş → Favori
 *   → Rastgele → **Bildirim (her zaman EN SAĞDA)**
 *
 * Bir ekranda hangi aksiyonlar varsa yalnızca onlar çizilir; kalanlar boşluk
 * bırakmaz. Bildirim butonu olan her ekranda bildirim en sağdaki buton olur.
 * Böylece kullanıcı ekran değiştirdiğinde kas hafızası bozulmaz.
 */
enum class KitsugiTopAction(
    @DrawableRes val iconRes: Int,
    /** Kanonik (soldan sağa) sıra. Büyük değer daha sağda. */
    val order: Int
) {
    FILTER(R.drawable.ic_kitsugi_top_filter, order = 10),
    SEARCH(R.drawable.ic_kitsugi_top_search, order = 15),
    REFRESH(R.drawable.ic_kitsugi_top_refresh, order = 20),
    HISTORY(R.drawable.ic_kitsugi_top_history, order = 30),
    DOWNLOADS(R.drawable.ic_kitsugi_top_download, order = 40),
    EXTENSIONS(R.drawable.ic_kitsugi_top_extension, order = 50),
    STATS(R.drawable.ic_kitsugi_top_stats, order = 60),
    SHARE(R.drawable.ic_kitsugi_top_share, order = 70),
    FAVORITE(R.drawable.ic_kitsugi_top_favorite, order = 75),
    GALLERY(R.drawable.ic_kitsugi_top_gallery, order = 80),
    RANDOM(R.drawable.ic_kitsugi_top_random, order = 90),

    /** Her zaman en sağda durur. */
    NOTIFICATIONS(R.drawable.ic_kitsugi_top_bell, order = 100);

    companion object {
        /** Verilen aksiyonları kanonik sıraya göre dizer (bildirim daima en sağda). */
        fun canonicalOrder(actions: Collection<KitsugiTopAction>): List<KitsugiTopAction> =
            actions.distinct().sortedBy { it.order }
    }
}

/** Üst çubuk butonlarının ortak ölçüleri — tüm ekranlarda birebir aynı. */
object KitsugiTopActionTokens {
    val ButtonSize: Dp = 40.dp
    val IconSize: Dp = 20.dp
    val Spacing: Dp = 8.dp
    val CornerRadius: Dp = 14.dp

    /** Satır içi (kaydırılan başlık) kullanım için biraz daha kompakt ölçü. */
    val CompactButtonSize: Dp = 38.dp
    val CompactCornerRadius: Dp = 12.dp
}

/** Aksiyonun varsayılan erişilebilirlik etiketi. */
@Composable
fun KitsugiTopAction.defaultLabel(): String = when (this) {
    KitsugiTopAction.FILTER -> stringResource(R.string.filtreler)
    KitsugiTopAction.SEARCH -> stringResource(R.string.stream_action_manual_search)
    KitsugiTopAction.REFRESH -> stringResource(R.string.yenile)
    KitsugiTopAction.HISTORY -> stringResource(R.string.stream_action_history)
    KitsugiTopAction.DOWNLOADS -> stringResource(R.string.stream_action_downloads)
    KitsugiTopAction.EXTENSIONS -> stringResource(R.string.eklenti_portali)
    KitsugiTopAction.STATS -> stringResource(R.string.kutuphane_istatistikleri)
    KitsugiTopAction.SHARE -> stringResource(R.string.paylas)
    KitsugiTopAction.FAVORITE -> stringResource(R.string.action_favourite_add)
    KitsugiTopAction.GALLERY -> stringResource(R.string.action_gallery)
    KitsugiTopAction.RANDOM -> stringResource(R.string.top_action_random)
    KitsugiTopAction.NOTIFICATIONS -> stringResource(R.string.notif_title)
}

/**
 * Tek bir üst çubuk aksiyon butonu.
 *
 * Görsel: yuvarlatılmış kare (14dp), vurgu renginin hafif gradyanı + ince vurgu
 * kenarlığı, şeffaf arka planlı HD vektör ikon. Tema vurgusu değiştiğinde
 * (gradyan modu dâhil) buton otomatik olarak o renge boyanır.
 */
@Composable
fun KitsugiTopActionButton(
    action: KitsugiTopAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accentColor: Color = LocalKitsugiAccent.current,
    size: Dp = KitsugiTopActionTokens.ButtonSize,
    cornerRadius: Dp = KitsugiTopActionTokens.CornerRadius,
    iconSize: Dp = KitsugiTopActionTokens.IconSize,
    showBadge: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.6f),
        label = "kitsugiTopActionPress"
    )
    val surface = KitsugiColors.Surface
    val borderColor = accentColor.copy(alpha = if (selected) 0.65f else 0.30f)
    val iconTint = if (selected) accentColor else KitsugiColors.TextPrimary

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = if (selected) 0.28f else 0.14f),
                        surface.copy(alpha = 0.94f)
                    )
                )
            )
            .border(width = 1.dp, color = borderColor, shape = shape)
            .tvClickable(
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(action.iconRes),
            contentDescription = contentDescription ?: action.defaultLabel(),
            tint = if (enabled) iconTint else KitsugiColors.TextMuted,
            modifier = Modifier.size(iconSize)
        )

        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-7).dp, y = 7.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(1.5.dp, surface, CircleShape)
            )
        }
    }
}

/**
 * Sağa hizalı **ortak** üst çubuk aksiyon satırı.
 *
 * Sıralama [KitsugiTopAction.order] ile garanti edilir: aynı butonlar her ekranda
 * aynı göreli konumda durur, bildirim daima en sağdadır.
 *
 * @param actions o ekranda gösterilecek aksiyonlar (sıra verilse bile yeniden dizilir)
 * @param onActionClick tıklanan aksiyon
 */
@Composable
fun KitsugiTopActionRow(
    actions: Collection<KitsugiTopAction>,
    onActionClick: (KitsugiTopAction) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current,
    size: Dp = KitsugiTopActionTokens.ButtonSize,
    cornerRadius: Dp = KitsugiTopActionTokens.CornerRadius,
    spacing: Dp = KitsugiTopActionTokens.Spacing,
    notificationBadge: Boolean = false,
    selectedActions: Set<KitsugiTopAction> = emptySet(),
    enabledActions: Set<KitsugiTopAction> = KitsugiTopAction.entries.toSet()
) {
    val ordered = remember(actions) { KitsugiTopAction.canonicalOrder(actions) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        ordered.forEach { action ->
            KitsugiTopActionButton(
                action = action,
                onClick = { onActionClick(action) },
                accentColor = accentColor,
                size = size,
                cornerRadius = cornerRadius,
                showBadge = notificationBadge && action == KitsugiTopAction.NOTIFICATIONS,
                selected = action in selectedActions,
                enabled = action in enabledActions
            )
        }
    }
}

/**
 * Detay sayfalarının sağ üst aksiyonları (Paylaş · Favori · Galeri).
 *
 * Her iki detay sayfası (API sonucu ve kütüphane kaydı) bu bileşeni kullanır;
 * böylece buton sırası, boyutu ve görünümü ana sekmelerdeki üst çubukla aynıdır.
 */
@Composable
fun KitsugiDetailHeaderActions(
    onShareClick: () -> Unit,
    accentColor: Color = LocalKitsugiAccent.current,
    showFavourite: Boolean = false,
    isFavourite: Boolean = false,
    onFavouriteClick: (() -> Unit)? = null,
    showGallery: Boolean = false,
    galleryLoading: Boolean = false,
    onGalleryClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KitsugiTopActionTokens.Spacing)
    ) {
        KitsugiTopActionButton(
            action = KitsugiTopAction.SHARE,
            onClick = onShareClick,
            accentColor = accentColor
        )
        if (showFavourite && onFavouriteClick != null) {
            KitsugiTopActionButton(
                action = KitsugiTopAction.FAVORITE,
                onClick = onFavouriteClick,
                contentDescription = stringResource(
                    if (isFavourite) R.string.action_favourite_remove else R.string.action_favourite_add
                ),
                accentColor = accentColor,
                selected = isFavourite
            )
        }
        if (showGallery && onGalleryClick != null) {
            KitsugiTopActionButton(
                action = KitsugiTopAction.GALLERY,
                onClick = onGalleryClick,
                accentColor = accentColor,
                enabled = !galleryLoading
            )
        }
    }
}
