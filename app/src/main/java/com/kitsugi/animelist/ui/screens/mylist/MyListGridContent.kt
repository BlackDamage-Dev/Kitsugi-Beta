package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text

/**
 * Bölümlere ayrılmış liste içeriği (Listem).
 *
 * Gruplama ekseni ([MyListSection]) tek yerde çizilir: varsayılan görünüm (Bugün / Dün /
 * Bu Hafta …), durum grupları ve düz liste **aynı** kart/satır çizimini kullanır. Aktif
 * sıralama bölümlerin İÇİNDE geçerlidir; başlıklar sıralama değişince kaybolmaz.
 */
internal fun LazyListScope.MyListSectionedContent(
    sections: List<MyListSection>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int = 2,
    sourceBadgesByEntryId: Map<Int, List<String>> = emptyMap(),
    onEntryClick: (MediaEntry) -> Unit,
    onEditEntry: ((MediaEntry) -> Unit)? = null,
    onPosterLongClick: (String) -> Unit
) {
    sections.forEach { section ->
        if (!section.isHeaderless) {
            item(key = "section_header_${section.id}") {
                MyListSectionHeader(section)
            }
        }
        MyListSectionEntries(
            items = section.entries,
            keyPrefix = section.id,
            selectedListLayoutId = selectedListLayoutId,
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia,
            gridColumns = gridColumns,
            sourceBadgesByEntryId = sourceBadgesByEntryId,
            onEntryClick = onEntryClick,
            onEditEntry = onEditEntry,
            onPosterLongClick = onPosterLongClick
        )
    }
}

/**
 * Durum gruplarına göre içerik (eski çağrı biçimi). Profil listesi ve eski çağrı yerleri
 * bunu kullanır; davranış [MyListSectionedContent] ile aynıdır.
 */
internal fun LazyListScope.MyListGroupedContent(
    groupedEntries: List<Pair<WatchStatus, List<MediaEntry>>>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int = 3,
    sourceBadgesByEntryId: Map<Int, List<String>> = emptyMap(),
    onEntryClick: (MediaEntry) -> Unit,
    onEditEntry: ((MediaEntry) -> Unit)? = null,
    onPosterLongClick: (String) -> Unit
) {
    MyListSectionedContent(
        sections = groupedEntries.map { (status, entries) ->
            MyListSection(id = status.name, entries = entries, status = status)
        },
        selectedListLayoutId = selectedListLayoutId,
        titleLanguage = titleLanguage,
        scoreFormat = scoreFormat,
        hideScores = hideScores,
        blurAdultMedia = blurAdultMedia,
        gridColumns = gridColumns,
        sourceBadgesByEntryId = sourceBadgesByEntryId,
        onEntryClick = onEntryClick,
        onEditEntry = onEditEntry,
        onPosterLongClick = onPosterLongClick
    )
}

/**
 * Düz (başlıksız) liste içeriği. "Düz liste" gruplaması, Tamamlandı filtresi ve
 * [com.kitsugi.animelist.data.settings.AppSettings.separatedListStyle] kapalıyken kullanılır.
 */
internal fun LazyListScope.MyListFlatContent(
    visibleEntries: List<MediaEntry>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int = 3,
    sourceBadgesByEntryId: Map<Int, List<String>> = emptyMap(),
    onEntryClick: (MediaEntry) -> Unit,
    onEditEntry: ((MediaEntry) -> Unit)? = null,
    onPosterLongClick: (String) -> Unit
) {
    MyListSectionEntries(
        items = visibleEntries,
        keyPrefix = "flat",
        selectedListLayoutId = selectedListLayoutId,
        titleLanguage = titleLanguage,
        scoreFormat = scoreFormat,
        hideScores = hideScores,
        blurAdultMedia = blurAdultMedia,
        gridColumns = gridColumns,
        sourceBadgesByEntryId = sourceBadgesByEntryId,
        onEntryClick = onEntryClick,
        onEditEntry = onEditEntry,
        onPosterLongClick = onPosterLongClick
    )
}

/** Bölüm başlığı: tarih kovası ya da izleme durumu. */
@androidx.compose.runtime.Composable
private fun MyListSectionHeader(section: MyListSection) {
    val title = section.bucket?.let { stringResource(it.titleRes) }
        ?: when (section.status) {
            WatchStatus.Watching -> stringResource(R.string.mylist_status_watching)
            WatchStatus.Repeating -> stringResource(R.string.mylist_status_repeating)
            WatchStatus.Planned -> stringResource(R.string.mylist_status_planned)
            WatchStatus.Paused -> stringResource(R.string.mylist_status_paused)
            WatchStatus.Dropped -> stringResource(R.string.mylist_status_dropped)
            WatchStatus.Completed -> stringResource(R.string.mylist_status_completed)
            null -> ""
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = section.entries.size.toString(),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Tek bir bölümün kartları — grid ya da tek sütun. */
private fun LazyListScope.MyListSectionEntries(
    items: List<MediaEntry>,
    keyPrefix: String,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int,
    sourceBadgesByEntryId: Map<Int, List<String>>,
    onEntryClick: (MediaEntry) -> Unit,
    onEditEntry: ((MediaEntry) -> Unit)? = null,
    onPosterLongClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    val isGrid = selectedListLayoutId == "grid_2col"

    if (isGrid) {
        val rows = items.chunked(gridColumns)
        items(
            items = rows,
            key = { row -> "${keyPrefix}_grid_row_${row.firstOrNull()?.let { "${it.source}_${it.id}" }}" }
        ) { row ->
            val hasExtra = if (sourceBadgesByEntryId.isNotEmpty()) {
                row.any { entry ->
                    val badges = sourceBadgesByEntryId[entry.id]
                    !badges.isNullOrEmpty() &&
                        com.kitsugi.animelist.ui.components.additionalLibraryBadgeSources(entry.source, badges)
                            .any { com.kitsugi.animelist.ui.components.KitsugiPlatformLogos.resFor(it) != null }
                }
            } else false
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    KitsugiMediaEntryCard(
                        entry = entry,
                        layoutId = selectedListLayoutId,
                        modifier = Modifier.weight(1f),
                        onClick = { onEntryClick(entry) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                        sourceBadges = sourceBadgesByEntryId[entry.id],
                        reserveSourceStripSpace = hasExtra
                    )
                }
                if (row.size < gridColumns) {
                    repeat(gridColumns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    } else {
        items(
            items = items,
            key = { entry -> "${keyPrefix}_${entry.source}_${entry.id}" }
        ) { entry ->
            KitsugiMediaEntryCard(
                entry = entry,
                layoutId = selectedListLayoutId,
                onClick = { onEntryClick(entry) },
                onEditClick = onEditEntry?.let { edit -> { edit(entry) } },
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                sourceBadges = sourceBadgesByEntryId[entry.id]
            )
            Spacer(modifier = Modifier.height(cardSpacingForLayout(selectedListLayoutId)))
        }
    }
}
