package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ChapterDisplayMode
import com.kitsugi.animelist.data.manga.ChapterSortMode
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Manga detay ekranındaki "Bölüm ara" alanı + okunmamış filtresi.
 *
 * Görsel dil, eklenti izleme sayfasındaki arama alanıyla aynıdır (yuvarlak köşeli koyu
 * alan, arama ikonu, temizle düğmesi); sağında tek dokunuşla açılıp kapanan durum düğmesi
 * durur — 232 bölümlük listede "okunmamış olanı bul" en sık yapılan iş.
 */
@Composable
fun MangaChapterSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    unreadOnly: Boolean,
    onToggleUnreadOnly: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.manga_chapter_search_hint)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KitsugiColors.SurfaceSoft,
                unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                focusedBorderColor = accentColor.copy(alpha = 0.6f),
                unfocusedBorderColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary,
                cursorColor = accentColor
            )
        )

        // Okunmamış filtresi — çiplerle aynı görsel dilde, tek dokunuşla aç/kapa.
        val shape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .widthIn(min = 84.dp)
                .clip(shape)
                .background(if (unreadOnly) accentColor.copy(alpha = 0.18f) else KitsugiColors.SurfaceSoft)
                .tvClickable(shape = shape, onClick = onToggleUnreadOnly)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (unreadOnly) {
                    stringResource(R.string.manga_filter_unread)
                } else {
                    stringResource(R.string.manga_filter_all)
                },
                color = if (unreadOnly) accentColor else KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Kaynak / tarayıcı (scanlator) çipleri: "Tümü | MangaciX | Şımarık Fansub | …".
 *
 * Aynı bölüm birden fazla grupta yayınlanabildiği için (ör. "Şımarık Fansub, MangaciX"
 * gibi birleşik etiketler) çip etiketleri eklentiden gelen `scanlator` alanından **olduğu
 * gibi** türetilir; isim uydurulmaz.
 */
@Composable
fun MangaScanlatorChipsRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    /**
     * Sağdaki ek çip (ör. "Yinelenenleri gizle"). Etiket null ise çip hiç çizilmez;
     * böylece yinelenen bölüm yokken gereksiz yer kaplamaz.
     */
    extraChipLabel: String? = null,
    extraChipSelected: Boolean = false,
    onToggleExtraChip: (() -> Unit)? = null
) {
    // Yalnızca ek çip varsa ve scanlator etiketi yoksa da satır çizilir.
    if (labels.isEmpty() && extraChipLabel == null) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (labels.isNotEmpty()) {
            item(key = "__all__") {
                ScanlatorChip(
                    text = stringResource(R.string.tumu),
                    selected = selected == null,
                    accentColor = accentColor,
                    onClick = { onSelect(null) }
                )
            }
            items(labels, key = { it }) { label ->
                ScanlatorChip(
                    text = label,
                    selected = selected == label,
                    accentColor = accentColor,
                    onClick = { onSelect(if (selected == label) null else label) }
                )
            }
        }
        if (extraChipLabel != null && onToggleExtraChip != null) {
            item(key = "__extra__") {
                ScanlatorChip(
                    text = extraChipLabel,
                    selected = extraChipSelected,
                    accentColor = accentColor,
                    onClick = onToggleExtraChip
                )
            }
        }
    }
}

@Composable
private fun ScanlatorChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accentColor.copy(alpha = 0.18f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Bölüm listesinin (sticky) başlığı — "Bölümler (232)" + sıralama / okundu filtresi / yenile.
 *
 * Sağdaki ikonlar ekran görüntüsündeki düzenle aynı sıradadır:
 * arama · sıralama (⇅) · okunanları filtrele (✓) · tümünü okundu · yenile (↻).
 *
 * Sıralama düğmesi tek dokunuşla çevirmek yerine **menü** açar: eskiden olduğu gibi
 * döngüyle "Yeni → Eski → Son güncellenen" arasında dolaşmak, kullanıcı "1'den başlasın"
 * isterken yanlışlıkla üç kez dokunmayı gerektiriyordu. Menüde hedefler tek dokunuşta.
 */
@Composable
fun MangaChaptersHeader(
    title: String,
    count: Int,
    sortLabel: String,
    readFilterActive: Boolean,
    accentColor: Color,
    highlightReadFilter: Boolean,
    onCycleSort: () -> Unit,
    onToggleReadFilter: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Sticky başlıktaki arama düğmesi: verilirse sticy başlığın içinde kompakt bir
     * "Bölüm ara" alanı açılır (null ise düğme gizlenir). Uzun bölüm listelerinde
     * aşağı kaydıktan sonra da aramaya erişim.
     */
    onToggleSearch: (() -> Unit)? = null,
    /** Arama alanı şu an açık mı (düğme vurgusu ve ikon değişimi için). */
    searchExpanded: Boolean = false,
    /**
     * Görünen bölümleri toplu okundu/okunmadı işaretleme. Uzun serilerde (200+ bölüm)
     * tek tek tik atmak yerine tek dokunuşla liste temizlenir.
     */
    onToggleAllRead: (() -> Unit)? = null,
    /** Tümü şu an "okundu" mu (düğme ikonu ve vurgusu). */
    allReadActive: Boolean = false,
    /**
     * Sıralama menüsü: verilirse ⇅ düğmesi tek dokunuşluk döngü yerine açılır menü açar.
     * Menüde her mod ayrı satır olur; "varsayılana dön" seçeneği de bulunur.
     */
    sortMode: ChapterSortMode? = null,
    onSelectSort: ((ChapterSortMode) -> Unit)? = null,
    /** Yer imli (★) bölümlere hızlı filtre. */
    bookmarkedOnly: Boolean = false,
    onToggleBookmarked: (() -> Unit)? = null,
    /** Kaç bölüm yer imli — düğme etiketinde gösterilir. */
    bookmarkedCount: Int = 0,
    /** Okunmamış bölüm sayısı — başlığın alt satırında özet. */
    unreadCount: Int = 0,
    /** "Kaldığım yere git" düğmesi (uzun listede konumu kaydırır). */
    onJumpToCurrent: (() -> Unit)? = null,
    /** Bölüm görünümü menüsü (numara / başlık). */
    displayMode: ChapterDisplayMode? = null,
    onSelectDisplayMode: ((ChapterDisplayMode) -> Unit)? = null
) {
    val sortMenuExpanded = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val displayMenuExpanded = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$title ($count)",
                color = KitsugiColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            // İkinci satır: sıralama etiketi + okunmamış özeti. Kullanıcı listenin hangi
            // sırada olduğunu ve kaç bölüm kaldığını tek bakışta görür.
            Text(
                text = buildString {
                    append(sortLabel)
                    if (unreadCount > 0) {
                        append(" · ")
                        append(stringResource(R.string.manga_unread_count, unreadCount))
                    }
                },
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onJumpToCurrent != null) {
            MangaHeaderIconButton(
                icon = Icons.Rounded.MyLocation,
                contentDescription = stringResource(R.string.manga_jump_to_current),
                active = false,
                accentColor = accentColor,
                onClick = onJumpToCurrent
            )
            Spacer(Modifier.width(2.dp))
        }
        if (onToggleSearch != null) {
            // Uzun bölüm listesinde aşağı kaydıktan sonra da aramaya erişim:
            // başlıktaki bu düğme kompakt arama alanını açar/kapatır.
            MangaHeaderIconButton(
                icon = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = stringResource(R.string.manga_chapter_search_hint),
                active = searchExpanded,
                accentColor = accentColor,
                onClick = onToggleSearch
            )
            Spacer(Modifier.width(2.dp))
        }
        Box {
            MangaHeaderIconButton(
                icon = Icons.Rounded.SwapVert,
                contentDescription = stringResource(R.string.manga_sort_chapters),
                active = sortMode != null && sortMode != ChapterSortMode.DEFAULT,
                accentColor = accentColor,
                onClick = {
                    if (onSelectSort != null) sortMenuExpanded.value = true else onCycleSort()
                }
            )
            if (onSelectSort != null) {
                KitsugiDropdownMenu(
                    expanded = sortMenuExpanded.value,
                    onDismissRequest = { sortMenuExpanded.value = false },
                    dialogTitle = stringResource(R.string.manga_sort_title)
                ) {
                    KitsugiDropdownItem(
                        text = stringResource(R.string.manga_sort_oldest_first),
                        selected = sortMode == ChapterSortMode.OldestFirst,
                        onClick = {
                            onSelectSort(ChapterSortMode.OldestFirst)
                            sortMenuExpanded.value = false
                        }
                    )
                    KitsugiDropdownItem(
                        text = stringResource(R.string.manga_sort_newest_first),
                        selected = sortMode == ChapterSortMode.NewestFirst,
                        onClick = {
                            onSelectSort(ChapterSortMode.NewestFirst)
                            sortMenuExpanded.value = false
                        }
                    )
                    KitsugiDropdownItem(
                        text = stringResource(R.string.manga_sort_recently_updated),
                        selected = sortMode == ChapterSortMode.RecentlyUpdated,
                        onClick = {
                            onSelectSort(ChapterSortMode.RecentlyUpdated)
                            sortMenuExpanded.value = false
                        }
                    )
                    if (displayMode != null && onSelectDisplayMode != null) {
                        KitsugiDropdownItem(
                            text = stringResource(R.string.manga_display_title),
                            onClick = {
                                sortMenuExpanded.value = false
                                displayMenuExpanded.value = true
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(2.dp))
        MangaHeaderIconButton(
            icon = Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.manga_read_filter),
            active = highlightReadFilter && readFilterActive,
            accentColor = accentColor,
            onClick = onToggleReadFilter
        )
        if (onToggleBookmarked != null) {
            Spacer(Modifier.width(2.dp))
            MangaHeaderIconButton(
                icon = if (bookmarkedOnly) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (bookmarkedCount > 0) {
                    stringResource(R.string.manga_bookmarked_count, bookmarkedCount)
                } else {
                    stringResource(R.string.manga_filter_bookmarked)
                },
                active = bookmarkedOnly,
                accentColor = accentColor,
                onClick = onToggleBookmarked
            )
        }
        Spacer(Modifier.width(2.dp))
        if (onToggleAllRead != null) {
            Spacer(Modifier.width(2.dp))
            MangaHeaderIconButton(
                icon = if (allReadActive) Icons.Rounded.Undo else Icons.Rounded.DoneAll,
                contentDescription = stringResource(R.string.manga_mark_all_read),
                active = allReadActive,
                accentColor = accentColor,
                onClick = onToggleAllRead
            )
        }
        MangaHeaderIconButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.yenile),
            active = false,
            accentColor = accentColor,
            onClick = onRefresh
        )
    }

    // Görünüm menüsü — sıralama menüsünden ayrı açılır (iç içe menü Popup'ta açılmaz).
    if (displayMode != null && onSelectDisplayMode != null) {
        KitsugiDropdownMenu(
            expanded = displayMenuExpanded.value,
            onDismissRequest = { displayMenuExpanded.value = false },
            dialogTitle = stringResource(R.string.manga_display_title)
        ) {
            KitsugiDropdownItem(
                text = stringResource(R.string.manga_display_number),
                selected = displayMode == ChapterDisplayMode.Number,
                onClick = {
                    onSelectDisplayMode(ChapterDisplayMode.Number)
                    displayMenuExpanded.value = false
                }
            )
            KitsugiDropdownItem(
                text = stringResource(R.string.manga_display_title_mode),
                selected = displayMode == ChapterDisplayMode.Title,
                onClick = {
                    onSelectDisplayMode(ChapterDisplayMode.Title)
                    displayMenuExpanded.value = false
                }
            )
        }
    }

    // Başlığın altındaki ince ayırıcı, sticky başlık kayarken içeriği ayırır.
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.35f))
    )
}

/**
 * Bölüm listesinin üstündeki ince ilerleme şeridi.
 *
 * Mihon/Tachiyomi arayüzünde "kaç bölüm okundu" bilgisi kitaplıkta değil seri detayında
 * görünür; 386 bölümlük bir seride listenin neresinde olduğunu bilmek için tek bakış
 * yeterli olur. Ayrıca kaynakta **eksik** bölüm numaraları varsa (ör. 12, 13 yok) tek
 * satırda bildirilir — kullanıcı "kaynak bölüm atlıyor mu" diye düşünmez.
 */
@Composable
fun MangaChapterProgressSummary(
    readCount: Int,
    totalCount: Int,
    missingNumbers: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (totalCount <= 0) return
    val fraction = (readCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.manga_progress_summary, readCount, totalCount),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (fraction >= 1f) {
                Text(
                    text = stringResource(R.string.history_read_completed),
                    color = KitsugiColors.AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        // İnce çubuk: M3 LinearProgressIndicator yerine iki Box — mevcut listede de
        // aynı desen kullanılıyor (animasyon maliyeti ve tema uyumu için).
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor)
            )
        }
        if (missingNumbers.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.manga_missing_numbers,
                    missingNumbers.take(8).joinToString(", ") +
                        if (missingNumbers.size > 8) "…" else ""
                ),
                color = KitsugiColors.TextMuted.copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
