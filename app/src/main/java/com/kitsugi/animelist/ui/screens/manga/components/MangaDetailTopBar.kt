package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiStickyHeaderReveal
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

/** Aşağı kaydırınca beliren manga detay üst barının yüksekliği. */
val MangaDetailTopBarHeight = 56.dp

/**
 * Manga bölüm detay sayfasının **kaydırmaya duyarlı üst barı**.
 *
 * Anime/film detay sayfasındaki mekanikle (bkz. DetailPageScaffold → KitsugiStickyHeaderReveal)
 * birebir aynı mantık: sayfa tepedeyken yalnızca yüzen geri düğmesi görünür; kullanıcı
 * hero + aksiyon + açıklama + wiki kartını geçtikten sonra bar yumuşakça aşağı süzülür ve
 * başlığı, bölüm sayısını, yenile düğmesini taşır. Geri dönüldüğünde sessizce kaybolur.
 *
 * Görsel dil, eklenti izleme (stream) sayfasının üst barıyla aynıdır: yuvarlak kutu içinde
 * geri oku, iki satır başlık/alt başlık, sağda ikon düğmeleri.
 */
@Composable
fun MangaDetailTopBar(
    title: String,
    subtitle: String,
    visible: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    KitsugiStickyHeaderReveal(visible = visible, modifier = modifier) {
        Surface(
            color = KitsugiColors.Background.copy(alpha = 0.96f),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MangaDetailTopBarHeight)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f),
                            contentColor = KitsugiColors.TextPrimary
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = KitsugiColors.TextSecondary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.45f))
                )
            }
        }
    }
}

/**
 * Sayfa tepesindeyken görünen yüzen geri düğmesi.
 *
 * Bölüm başlığı şeridi (sticky chapter header) ile çakışmaması için yalnızca üst bar
 * gizliyken çizilir.
 */
@Composable
fun MangaFloatingBackButton(
    visible: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.75f))
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = KitsugiColors.TextPrimary
            )
        }
    }
}

/** Manga detay ekranındaki yuvarlak ikon düğmesi (sıralama / okunmamış vb.). */
@Composable
fun MangaHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) accentColor.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (active) accentColor else KitsugiColors.TextSecondary
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(19.dp))
    }
}

/** Boş liste / filtre sonucu yok durumu için ortak yerleşim. */
@Composable
fun MangaEmptyChapters(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
