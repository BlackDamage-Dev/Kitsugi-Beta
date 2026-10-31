package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.components.animateDetailContentSize
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.toLocalizedOrgName
import com.kitsugi.animelist.utils.toTurkishGenre
import com.kitsugi.animelist.utils.toEnglishGenreForSearch
import com.kitsugi.animelist.data.remote.KitsugiExternalLink
import com.kitsugi.animelist.data.remote.KitsugiStudio
import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.toLocalizedTagLabel

// ---------------------------------------------------------------------------
//  Stüdyo / Yapımcı chips bölümü
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiStudiosCard(
    studios: List<KitsugiStudio>,
    producers: List<KitsugiStudio>,
    onStudioClick: (KitsugiStudio) -> Unit,
    onProducerClick: (KitsugiStudio) -> Unit,
    networks: List<KitsugiStudio> = emptyList(),
    onNetworkClick: (KitsugiStudio) -> Unit = onProducerClick
) {
    if (studios.isEmpty() && producers.isEmpty() && networks.isEmpty()) return
    val accentColor = LocalKitsugiAccent.current

    // Manga ayrıntılarında yazar/dergi/yayıncı kayıtları bu kartın listelerini kullanır;
    // başlıklar role göre doğru etiketi gösterir (örn. "Stüdyolar" yerine "Yazarlar").
    val studiosLabel = when {
        studios.all { it.role == com.kitsugi.animelist.data.remote.StudioRole.AUTHOR } ->
            stringResource(R.string.detail_section_authors)
        studios.all { it.role == com.kitsugi.animelist.data.remote.StudioRole.PUBLISHER } ->
            stringResource(R.string.detail_section_publishers)
        else -> stringResource(R.string.detail_section_studios)
    }
    val producersLabel = when {
        producers.all { it.role == com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE } ->
            stringResource(R.string.detail_section_magazines)
        producers.all { it.role == com.kitsugi.animelist.data.remote.StudioRole.PUBLISHER } ->
            stringResource(R.string.detail_section_publishers)
        else -> stringResource(R.string.detail_section_producers)
    }
    val networksLabel = when {
        networks.all { it.role == com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE } ->
            stringResource(R.string.detail_section_magazines)
        else -> stringResource(R.string.detail_section_networks)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (studios.isNotEmpty()) {
            SectionLabel(studiosLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                studios.forEach { studio ->
                    KitsugiChip(
                        // Ad TR/EN dil dosyasından gelir (bangumi_studio_*); eşleşmeyen
                        // ad (komite vb.) Latin/orijinal hâliyle kalır — bkz. MASTER 2026-10-11.
                        text = studio.name.toLocalizedOrgName(),
                        color = accentColor,
                        solid = true,
                        // Kimliği henüz çözülmemiş Bangumi yerel kayıtları da tıklanabilir:
                        // ad taşınır, hedef sayfa kurumu adıyla çözümler (bkz. KitsugiStudioClient).
                        onClick = if (studio.name.isNotBlank()) {
                            { onStudioClick(studio) }
                        } else null
                    )
                }
            }
        }
        if (producers.isNotEmpty()) {
            SectionLabel(producersLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                producers.forEach { producer ->
                    KitsugiChip(
                        text = producer.name.toLocalizedOrgName(),
                        color = accentColor,
                        solid = false,
                        onClick = if (producer.name.isNotBlank()) {
                            { onProducerClick(producer) }
                        } else null
                    )
                }
            }
        }
        if (networks.isNotEmpty()) {
            SectionLabel(networksLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                networks.forEach { network ->
                    KitsugiChip(
                        text = network.name.toLocalizedOrgName(),
                        color = accentColor,
                        solid = false,
                        // Yayıncı ağlar da Bangumi'de şirket (type=2) kaydıdır; kimlik
                        // çözülmemişse ad ile çözümleme hedef sayfada yapılır.
                        onClick = if (network.name.isNotBlank()) {
                            { onNetworkClick(network) }
                        } else null
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Etiketler (Tags) - Spoiler toggle + daha fazla expander
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiTagsCard(
    tags: List<KitsugiTag>,
    onTagClick: (KitsugiTag) -> Unit
) {
    if (tags.isEmpty()) return
    val accentColor = LocalKitsugiAccent.current

    var showSpoilers by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Çeviriden sonra aynı anlama gelen yazımlar tek çipte toplanır; kopyalardan
    // herhangi biri spoiler ise `isSpoiler = true` ve `rank` korunur.
    val distinctTags = tags.distinctByLocalizedLabel()
    val nonSpoilerTags = distinctTags.filter { !it.isSpoiler }
    val spoilerTags = distinctTags.filter { it.isSpoiler }

    val visibleTags = when {
        expanded && showSpoilers -> distinctTags
        expanded -> nonSpoilerTags
        showSpoilers -> nonSpoilerTags.take(10) + spoilerTags
        else -> nonSpoilerTags.take(10)
    }

    val hasSpoilers = spoilerTags.isNotEmpty()
    val hasMore = nonSpoilerTags.size > 10

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .animateDetailContentSize()
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.detail_section_tags))
            if (hasSpoilers) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { showSpoilers = !showSpoilers }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Lock,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (showSpoilers) stringResource(R.string.ui_spoiler_hide) else stringResource(R.string.ui_spoiler_show),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleTags.forEach { tag ->
                TagChip(tag = tag, accentColor = accentColor, onClick = { onTagClick(tag) })
            }
        }

        if (hasMore) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .tvClickable(shape = RoundedCornerShape(12.dp)) { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KitsugiChevronIcon(
                    expanded = expanded,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (expanded) stringResource(R.string.action_see_less) else stringResource(R.string.action_see_more),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TagChip(tag: KitsugiTag, accentColor: Color, onClick: (() -> Unit)? = null) {
    val chipColor = if (tag.isSpoiler) KitsugiColors.AccentRed else accentColor
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(chipColor.copy(alpha = 0.10f))
            .border(0.5.dp, chipColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .then(
                if (onClick != null) Modifier.tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (tag.rank != null) {
            Text(
                text = "${tag.rank}%",
                color = chipColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        Text(
            // Bangumi tür/etiketleri Çince/Japonca gelir; arayüz diline çevrilir, eşleme
            // yoksa kaynak değeri gösterilir (arama her zaman orijinal etiketle yapılır).
            text = tag.name.toLocalizedTagLabel(),
            color = chipColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Gösterim etiketi aynı olan çipleri tekilleştirir (`isSpoiler = true` ve `rank` korunur). */
private fun List<KitsugiTag>.distinctByLocalizedLabel(): List<KitsugiTag> =
    com.kitsugi.animelist.utils.KitsugiSpoilerRules.normalizeAndMergeTags(this)

private data class GenreChipItem(
    val rawGenre: String,
    val displayLabel: String,
    val isSpoiler: Boolean
)

/**
 * Türler (Genres) kartı — AniList ile aynı spoiler gizleme/gösterme mantığını uygular.
 *
 * MAL/Jikan, Bangumi, Shikimori, Kitsu gibi kaynaklarda `genres` listesine dahil edilen
 * alt temalar (ör. Tragedy/Trajedi, Time Loop, Survival, Reincarnation vb.) doğal spoiler
 * olduğunda veya bu yapım için AniList'te spoiler etiketi olarak işaretlendiğinde
 * varsayılan olarak gizlenir ve "Spoiler'ları Göster / Gizle" anahtarıyla kırmızı çip
 * olarak açılır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiGenresCard(
    genres: List<String>,
    tags: List<KitsugiTag> = emptyList(),
    onGenreClick: (String) -> Unit = {}
) {
    if (genres.isEmpty()) return
    val accentColor = LocalKitsugiAccent.current
    var showSpoilers by remember { mutableStateOf(false) }

    val genreItems = remember(genres, tags) {
        val list = ArrayList<GenreChipItem>(genres.size)
        val indexByLabel = HashMap<String, Int>(genres.size)
        for (raw in genres) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val display = trimmed.toTurkishGenre().trim()
            if (display.isEmpty()) continue
            val key = display.lowercase(java.util.Locale.ROOT)
            val spoiler = com.kitsugi.animelist.utils.KitsugiSpoilerRules.isGenreSpoiler(trimmed, tags)
            val existingIdx = indexByLabel[key]
            if (existingIdx == null) {
                indexByLabel[key] = list.size
                list.add(GenreChipItem(rawGenre = trimmed, displayLabel = display, isSpoiler = spoiler))
            } else if (spoiler && !list[existingIdx].isSpoiler) {
                list[existingIdx] = list[existingIdx].copy(isSpoiler = true)
            }
        }
        list
    }

    if (genreItems.isEmpty()) return

    val hasSpoilers = genreItems.any { it.isSpoiler }
    val visibleGenres = if (showSpoilers) genreItems else genreItems.filter { !it.isSpoiler }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .animateDetailContentSize()
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.detail_section_genres),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (hasSpoilers) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { showSpoilers = !showSpoilers }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Lock,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (showSpoilers) stringResource(R.string.ui_spoiler_hide) else stringResource(R.string.ui_spoiler_show),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (visibleGenres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleGenres.forEach { item ->
                    val chipColor = if (item.isSpoiler) KitsugiColors.AccentRed else accentColor
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipColor.copy(alpha = if (item.isSpoiler) 0.10f else 0.15f))
                            .then(
                                if (item.isSpoiler) {
                                    Modifier.border(0.5.dp, chipColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .tvClickable(shape = RoundedCornerShape(999.dp)) {
                                onGenreClick(item.rawGenre.toEnglishGenreForSearch())
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.displayLabel,
                            color = chipColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Yayıncı Platformlar + Harici Bağlantılar
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiLinksCard(
    streamingLinks: List<KitsugiExternalLink>,
    externalLinks: List<KitsugiExternalLink>
) {
    if (streamingLinks.isEmpty() && externalLinks.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (streamingLinks.isNotEmpty()) {
            SectionLabel(stringResource(R.string.detail_section_streaming))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                streamingLinks.forEach { link ->
                    LinkChip(
                        link = link,
                        color = accentColor,
                        solid = true,
                        onClick = { runCatching { uriHandler.openUri(link.url) } }
                    )
                }
            }
        }
        if (externalLinks.isNotEmpty()) {
            SectionLabel(stringResource(R.string.detail_section_external))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                externalLinks.forEach { link ->
                    LinkChip(
                        link = link,
                        color = KitsugiColors.TextSecondary,
                        solid = false,
                        onClick = { runCatching { uriHandler.openUri(link.url) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkChip(
    link: KitsugiExternalLink,
    color: Color,
    solid: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) color.copy(alpha = 0.14f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = link.site,
            color = if (solid) color else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (!link.language.isNullOrBlank()) {
            Text(
                text = link.language,
                color = (if (solid) color else KitsugiColors.TextSecondary).copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.OpenInBrowser,
            contentDescription = null,
            tint = (if (solid) color else KitsugiColors.TextSecondary).copy(alpha = 0.6f),
            modifier = Modifier.size(13.dp)
        )
    }
}

// ---------------------------------------------------------------------------
//  Shared helpers
// ---------------------------------------------------------------------------
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = KitsugiColors.TextPrimary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun KitsugiChip(
    text: String,
    color: Color,
    solid: Boolean,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) color.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft)
            .border(
                width = if (!solid) 0.5.dp else 0.dp,
                color = if (!solid) color.copy(alpha = 0.30f) else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .then(
                if (onClick != null) Modifier.tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (solid) color else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
