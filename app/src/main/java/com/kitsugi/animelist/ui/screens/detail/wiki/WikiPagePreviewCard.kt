package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import com.kitsugi.animelist.ui.utils.uniqueItems
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.fandom.FandomWikiLink
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Birleşik wiki önizleme kartı ölçüleri.
 *
 * Karakter ve yapım (medya) sayfalarındaki oyuncu kadrosu (cast), seslendirenler
 * (voice actors), ekip (staff/crew), müzik/şarkı listeleri ve ilgili wiki sayfaları
 * şeritlerinin TAMAMI bu tek kartı kullanır: sabit 280 x 144 dp. Sabit ölçü,
 * şeritteki kartların aynı hizada durmasını ve snap-scroll'un her kartta aynı
 * noktaya oturmasını sağlar.
 */
internal val WIKI_PREVIEW_CARD_WIDTH = 280.dp
internal val WIKI_PREVIEW_CARD_HEIGHT = 144.dp

/** Şeritteki komşu önizleme kartları arasındaki boşluk (snap adımı bununla hesaplanır). */
internal val WIKI_PREVIEW_CARD_SPACING = 12.dp

/**
 * Birleşik Wiki Önizleme Kartı (280 x 144 dp).
 *
 * Sol: kapak görseli (yoksa başlık harfi yer tutucusu). Sağ: başlık (en çok 2 satır),
 * kısa açıklama (en çok 2 satır) ve alt satırda kaynak rozeti (Fandom / Vikipedi)
 * + isteğe bağlı rol rozeti (Kadro, Seslendiren, Ekip, Müzik…) + "Sayfayı aç" eylemi.
 *
 * Karta dokunmak HER ZAMAN uygulama içi okuyucuyu açar; bu bileşen tarayıcı
 * (ACTION_VIEW) çalıştırmaz. Tarayıcıya çıkış yalnızca okuyucunun üst çubuğundaki
 * açık "tarayıcıda aç" düğmesindedir.
 */
@Composable
internal fun WikiPagePreviewCard(
    title: String,
    description: String?,
    imageUrl: String?,
    sourceLabel: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .size(width = WIKI_PREVIEW_CARD_WIDTH, height = WIKI_PREVIEW_CARD_HEIGHT)
            .clip(shape)
            .background(KitsugiColors.SurfaceStrong)
            .border(1.dp, accentColor.copy(alpha = 0.18f), shape)
            .tvClickable(shape = shape) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            val url = imageUrl?.takeIf { it.isNotBlank() }
            if (url != null) {
                FandomWikiImage(
                    url = url,
                    width = 240,
                    contentDescription = title,
                    accentColor = accentColor,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                WikiPreviewPlaceholder(title = title, accentColor = accentColor)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Kaynak her zaman görünür: Fandom mu Vikipedi mi olduğu karttan okunur.
                WikiPreviewChip(text = sourceLabel, accentColor = accentColor, filled = true)
                if (!badgeLabel.isNullOrBlank()) {
                    WikiPreviewChip(text = badgeLabel, accentColor = accentColor, filled = false)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.OpenInNew,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.fandom_open_linked_page),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/** "İlgili wiki sayfaları" şeritleri için [FandomRelatedPage] uyarlaması. */
@Composable
internal fun WikiPagePreviewCard(
    page: FandomRelatedPage,
    wikiId: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null
) {
    WikiPagePreviewCard(
        title = page.profile?.title?.takeIf { it.isNotBlank() } ?: page.title,
        // Vikipedi'de kısa tanım ("Japon manga sanatçısı") kartın iki satırında tam
        // görünür; uzun özet metni kesilip okunaksız olmaktan kurtulur. Fandom'da
        // kısa tanım boş olduğu için davranış değişmez.
        description = page.shortDescription?.takeIf { it.isNotBlank() }
            ?: page.profile?.intro?.takeIf { it.isNotBlank() }
            ?: page.snippet,
        imageUrl = page.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: page.profile?.images?.firstOrNull()?.url,
        sourceLabel = FandomUrls.displaySourceName(wikiId),
        accentColor = accentColor,
        onClick = onClick,
        modifier = modifier,
        badgeLabel = badgeLabel
    )
}

/**
 * Kadro / seslendiren / ekip / müzik gibi ilişkili sayfalar için [FandomWikiLink]
 * uyarlaması. Kaynak, bağlantının KENDİ wiki kimliğinden okunur; karışık kaynaklı
 * listelerde (Fandom + Vikipedi) her kart doğru rozeti taşır.
 */
@Composable
internal fun WikiPagePreviewCard(
    entity: FandomWikiLink,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null
) {
    val contextLine = listOfNotNull(entity.context, entity.language?.uppercase())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
    WikiPagePreviewCard(
        title = entity.profile?.title?.takeIf { it.isNotBlank() } ?: entity.label,
        description = entity.profile?.intro?.takeIf { it.isNotBlank() }
            ?: entity.description
            ?: contextLine,
        imageUrl = entity.imageUrl?.takeIf { it.isNotBlank() }
            ?: entity.profile?.images?.firstOrNull()?.url,
        sourceLabel = FandomUrls.displaySourceName(entity.wikiId),
        accentColor = accentColor,
        onClick = onClick,
        modifier = modifier,
        badgeLabel = badgeLabel
    )
}

@Composable
private fun WikiPreviewChip(text: String, accentColor: Color, filled: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (filled) accentColor.copy(alpha = 0.14f)
                else KitsugiColors.SurfaceSoft
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = if (filled) accentColor else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WikiPreviewPlaceholder(title: String, accentColor: Color) {
    val letter = title.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = accentColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Tüm yatay wiki şeritlerinin tek düzeni: sola hizalı + snap-scroll.
 *
 * Satır kendi [rememberLazyListState] durumunu tutar ve
 * [KitsugiScrollDefaults.rememberRowSnapFling] ile kaydırma bırakıldığında
 * sıradaki kartın sol kenarı içerik başlangıcına pürüzsüzce oturur
 * (SnapPosition.Start). Liste başında ekstra dolgu yoktur; ilk kart her
 * zaman şeridin sol kenarıyla aynı hizadadır.
 */
@Composable
internal fun <T> WikiPreviewStripRow(
    items: List<T>,
    key: ((T) -> Any)? = null,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        flingBehavior = KitsugiScrollDefaults.rememberRowSnapFling(rowState),
        horizontalArrangement = Arrangement.spacedBy(WIKI_PREVIEW_CARD_SPACING),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (key == null) {
            items(items = items) { item -> itemContent(item) }
        } else {
            // Fandom sayfaları aynı başlıklı bölüm/bağlantıyı birden çok kez içerebiliyor
            // (ör. iki "Trivia" veya aynı karaktere iki bağlantı) → çökmeyen anahtar.
            uniqueItems(items = items, key = key) { item -> itemContent(item) }
        }
    }
}
