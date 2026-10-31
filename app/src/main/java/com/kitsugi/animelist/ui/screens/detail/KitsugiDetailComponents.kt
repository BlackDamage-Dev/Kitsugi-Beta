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
    onNetworkClick: (KitsugiStudio) -> Unit = onProducerClick,
    /** Dergi / yayınevi kayıtları. Ayrı bölümde gösterilir; `networks`e karıştırılmaz. */
    serializations: List<KitsugiStudio> = emptyList(),
    onSerializationClick: (KitsugiStudio) -> Unit = onNetworkClick
) {
    // Gösterimde adı temizlenince boşalan kayıtlar (kaomoji/infobox artığı) çizilmez:
    // `sanitizeOrgName()` böyle adlar için boş döner — bkz. toLocalizedOrgName().
    val localizedStudios = remember(studios) {
        studios.mapNotNull { s ->
            val loc = s.name.toLocalizedOrgName()
            if (loc.isNotBlank()) s to loc else null
        }
    }
    val localizedProducers = remember(producers) {
        producers.mapNotNull { p ->
            val loc = p.name.toLocalizedOrgName()
            if (loc.isNotBlank()) p to loc else null
        }
    }
    val localizedNetworks = remember(networks) {
        networks.mapNotNull { n ->
            val loc = n.name.toLocalizedOrgName()
            if (loc.isNotBlank()) n to loc else null
        }
    }
    val localizedSerials = remember(serializations) {
        serializations.mapNotNull { s ->
            val loc = s.name.toLocalizedOrgName()
            if (loc.isNotBlank()) s to loc else null
        }
    }

    if (localizedStudios.isEmpty() && localizedProducers.isEmpty() &&
        localizedNetworks.isEmpty() && localizedSerials.isEmpty()
    ) {
        return
    }
    val accentColor = LocalKitsugiAccent.current

    // Manga ayrıntılarında yazar/dergi/yayıncı kayıtları bu kartın listelerini kullanır;
    // başlıklar role göre doğru etiketi gösterir (örn. "Stüdyolar" yerine "Yazarlar").
    val studiosLabel = when {
        localizedStudios.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.AUTHOR } ->
            stringResource(R.string.detail_section_authors)
        localizedStudios.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.PUBLISHER } ->
            stringResource(R.string.detail_section_publishers)
        else -> stringResource(R.string.detail_section_studios)
    }
    val producersLabel = when {
        localizedProducers.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE } ->
            stringResource(R.string.detail_section_magazines)
        localizedProducers.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.PUBLISHER } ->
            stringResource(R.string.detail_section_publishers)
        else -> stringResource(R.string.detail_section_producers)
    }
    val networksLabel = stringResource(R.string.detail_section_networks)
    // Dergi / yayınevi bölümü: tek rolde geliyorsa o adı, karışıksa genel başlığı kullanır.
    val serialsLabel = when {
        localizedSerials.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE } ->
            stringResource(R.string.detail_section_magazines)
        localizedSerials.all { it.first.role == com.kitsugi.animelist.data.remote.StudioRole.PUBLISHER } ->
            stringResource(R.string.detail_section_publishers)
        else -> stringResource(R.string.detail_section_serials)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (localizedStudios.isNotEmpty()) {
            SectionLabel(studiosLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                localizedStudios.forEach { (studio, label) ->
                    KitsugiChip(
                        // Ad TR/EN dil dosyasından gelir (bangumi_studio_*); eşleşmeyen
                        // ad (komite vb.) Latin/orijinal hâliyle kalır — bkz. MASTER 2026-10-11.
                        text = label,
                        color = accentColor,
                        solid = true,
                        // Kimliği henüz çözülmemiş Bangumi yerel kayıtları da tıklanabilir:
                        // ad taşınır, hedef sayfa kurumu adıyla çözümler (bkz. KitsugiStudioClient).
                        onClick = if (studio.name.isNotBlank() && isStudioChipClickable(studio)) {
                            { onStudioClick(studio) }
                        } else null
                    )
                }
            }
        }
        if (localizedProducers.isNotEmpty()) {
            SectionLabel(producersLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                localizedProducers.forEach { (producer, label) ->
                    KitsugiChip(
                        text = label,
                        color = accentColor,
                        solid = false,
                        onClick = if (producer.name.isNotBlank() && isStudioChipClickable(producer)) {
                            { onProducerClick(producer) }
                        } else null
                    )
                }
            }
        }
        if (localizedSerials.isNotEmpty()) {
            SectionLabel(serialsLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                localizedSerials.forEach { (serial, label) ->
                    KitsugiChip(
                        text = label,
                        color = accentColor,
                        solid = false,
                        onClick = if (serial.name.isNotBlank() && isStudioChipClickable(serial)) {
                            { onSerializationClick(serial) }
                        } else null
                    )
                }
            }
        }
        if (localizedNetworks.isNotEmpty()) {
            SectionLabel(networksLabel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                localizedNetworks.forEach { (network, label) ->
                    KitsugiChip(
                        text = label,
                        color = accentColor,
                        solid = false,
                        // Yayıncı ağlar da Bangumi'de şirket (type=2) kaydıdır; kimlik
                        // çözülmemişse ad ile çözümleme hedef sayfada yapılır.
                        onClick = if (network.name.isNotBlank() && isStudioChipClickable(network)) {
                            { onNetworkClick(network) }
                        } else null
                    )
                }
            }
        }
    }
}

/**
 * Çip kurum sayfasına gidebilir mi?
 *
 * Bangumi infobox'ındaki üretim komiteleri (`とらぶるダークネス2nd製作委員会` gibi) ayrı bir
 * kurum kaydı **değildir**; tıklanınca hedef sayfa hiçbir kaynakta bulunamıyor ve kullanıcı
 * "Stüdyo detayları yüklenemedi" (KTS-COM-1020) ekranına düşüyordu. Kimliği çözülmüş
 * kayıtlar (id > 0) her zaman tıklanabilir kalır.
 */
internal fun isStudioChipClickable(chip: KitsugiStudio): Boolean =
    chip.id > 0 || !com.kitsugi.animelist.data.remote.StudioSourceSupport.isCommitteeName(chip.name)

private data class PreparedTagChipItem(
    val tag: KitsugiTag,
    val displayLabel: String
)

// ---------------------------------------------------------------------------
//  Etiketler (Tags) - Spoiler toggle + daha fazla expander
// ---------------------------------------------------------------------------
/** Daraltılmış görünümde gösterilen etiket sayısı. Genişletilince tümü listelenir. */
private const val VISIBLE_TAG_COUNT = 30

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

    // PERF: etiket normalleştirme + yerelleştirilmiş etiket çözümü YALNIZCA `tags` değiştiğinde
    // bir kez hesaplanır; her kaydırma/spoiler/genişletme rekompozisyonunda 50+ etiketi baştan
    // birleştirmek önlenir.
    val (distinctTags, nonSpoilerTags, spoilerTags) = remember(tags) {
        val merged = tags.distinctByLocalizedLabel().map { t ->
            PreparedTagChipItem(tag = t, displayLabel = t.name.toLocalizedTagLabel())
        }
        Triple(
            merged,
            merged.filter { !it.tag.isSpoiler },
            merged.filter { it.tag.isSpoiler }
        )
    }

    val visibleTags = remember(distinctTags, nonSpoilerTags, spoilerTags, expanded, showSpoilers) {
        when {
            expanded && showSpoilers -> distinctTags
            expanded -> nonSpoilerTags
            showSpoilers -> nonSpoilerTags.take(VISIBLE_TAG_COUNT) + spoilerTags
            else -> nonSpoilerTags.take(VISIBLE_TAG_COUNT)
        }
    }

    val hasSpoilers = spoilerTags.isNotEmpty()
    val hasMore = nonSpoilerTags.size > VISIBLE_TAG_COUNT

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
            visibleTags.forEach { item ->
                TagChip(
                    tag = item.tag,
                    displayLabel = item.displayLabel,
                    accentColor = accentColor,
                    onClick = { onTagClick(item.tag) }
                )
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
private fun TagChip(
    tag: KitsugiTag,
    displayLabel: String = tag.name.toLocalizedTagLabel(),
    accentColor: Color,
    onClick: (() -> Unit)? = null
) {
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
            text = displayLabel,
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
