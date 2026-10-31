package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon

/**
 * Otomatik (uygulama içi) çevirinin sürüp sürmediği.
 *
 * Detay sayfalarının kökünde `CompositionLocalProvider` ile sağlanır: sayfa otomatik
 * çeviri yaparken (açıklama / biyografi / stüdyo "Hakkında" metni) içerideki tüm çeviri
 * butonları, tıpkı galeri butonunun görsel yüklenirken gösterdiği gibi, etrafında dönen
 * halka + nabız animasyonu gösterir. Böylece kullanıcı metnin hâlâ çevrildiğini anlar.
 *
 * Butonların kendi yerel yüklenme durumu varsa ([KitsugiTranslateIconButton]'un
 * `isLoading` parametresi) bu değer yerel değeri geçersiz kılar.
 */
val LocalKitsugiTranslationLoading = compositionLocalOf { false }

/**
 * Çeviri ikon butonu — galeri ikon butonuyla ([KitsugiGalleryIconButton]) birebir aynı
 * yüklenme animasyonunu kullanır.
 *
 * - Yükleniyor ([isLoading] = true): ikon nabız gibi yanıp söner, etrafında dönen bir halka
 *   görünür ve buton varsayılan olarak dokunulamaz ([enabled] = false).
 * - Hazır: ikon normal parlaklıkta, dokunulabilir.
 *
 * Boyutlar çağrı yerine göre ayarlanır: `iconSize` ikon boyutu, `ringSize` yüklenme
 * halkasının çapıdır (buton kutusunu büyütmemesi için kutudan küçük seçilmelidir).
 */
@Composable
fun KitsugiTranslateIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = LocalKitsugiTranslationLoading.current,
    tint: Color = LocalKitsugiAccent.current,
    icon: ImageVector = Icons.Translate,
    contentDescription: String? = null,
    iconSize: Dp = 24.dp,
    ringSize: Dp = 32.dp,
    strokeWidth: Dp = 2.dp,
    enabled: Boolean = !isLoading
) {
    val resolvedDescription = contentDescription ?: stringResource(R.string.action_translate)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            if (isLoading) {
                // Nabız (pulse) animasyonu yalnızca yüklenirken VE yalnızca kendi küçük
                // alt ağacında çalışır: eski yapıda sonsuz transition her butonda (boşta
                // bile) sürekli kare üretiyor, okuma yapan butonun TÜM scope'u her karede
                // yenileniyordu — otomatik çeviri açıkken sayfa kaydırmada kasıyordu.
                PulsingLoadingTranslateIcon(
                    tint = tint,
                    icon = icon,
                    contentDescription = resolvedDescription,
                    iconSize = iconSize,
                    ringSize = ringSize,
                    strokeWidth = strokeWidth
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = resolvedDescription,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Yüklenirken nabız atan çeviri ikonu + dönen halka. Ayrı bir composable olmasının
 * nedeni: `pulseAlpha` okuması bu küçük scope'u her karede yeniler; butonun üst scope'u
 * (parametre karşılaştırması, enabled, vb.) kare boyunca atlanır.
 */
@Composable
private fun PulsingLoadingTranslateIcon(
    tint: Color,
    icon: ImageVector,
    contentDescription: String?,
    iconSize: Dp,
    ringSize: Dp,
    strokeWidth: Dp
) {
    val transition = rememberInfiniteTransition(label = "translateLoadingTransition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateLoadingPulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .alpha(pulseAlpha * 0.6f)
        )
        CircularProgressIndicator(
            modifier = Modifier.size(ringSize),
            color = tint,
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Uygulama içi (otomatik) çeviri ekranda gösterilirken orijinal metne geçiş düğmesi.
 *
 * Çeviri gösterilirken "Orijinal" yazar ve dokununca orijinal metin geri gelir; orijinal
 * gösterilirken "Çeviri" yazar ve dokununca çeviri tekrar gösterilir. Çeviri çoğunlukla
 * otomatik olarak yapıldığı için, bu düğme kullanıcıya her zaman orijinal metne dönme
 * imkânı verir. Yalnızca çeviri mevcutken çağrılmalıdır.
 *
 * @param showingOriginal şu an orijinal metin gösteriliyor mu.
 * @param onClick geçişi yapan callback (durumu çağıran taraf tutar).
 */
@Composable
fun KitsugiOriginalToggleButton(
    showingOriginal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalKitsugiAccent.current
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(
                if (showingOriginal) R.string.translate_show_translation else R.string.translate_show_original
            ),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Profil kartlarındaki başlık satırı gibi dar alanlar için kompakt
 * orijinal/çeviri geçiş çipi. [KitsugiOriginalToggleButton] ile aynı sözleşmeyi
 * kullanır; yalnızca daha küçük yazı ve sıkı iç boşlukla çizilir.
 */
@Composable
fun KitsugiOriginalToggleChip(
    showingOriginal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalKitsugiAccent.current
) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (showingOriginal) Icons.Translate else Icons.Restore,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(
                if (showingOriginal) R.string.translate_show_translation else R.string.translate_show_original
            ),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
