package com.kitsugi.animelist.ui.screens.manga
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.utils.uniqueItems

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.matchesChapterQuery
import androidx.compose.material3.OutlinedTextField
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.safeBlur
import androidx.compose.runtime.remember
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiSectionCard

/**
 * Manga Detay + "Oku" Ekranı.
 *
 * — Üstte bulanık kapak arkaplanı + kapak görseli + başlık/yazar/durum/türler
 * — Büyük "Baştan Oku" / "Devam Et" butonu
 * — Bölüm listesi (okunan bölümler işaretli, devam edilen bölüm vurgulu)
 *
 * Bir bölüme (veya Oku butonuna) tıklanınca [onOpenChapter] ile okuyucu açılır.
 */
@Composable
fun MangaDetailScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    onOpenChapter: (MangaChapter) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    var showMarkAllConfirm by remember { mutableStateOf(false) }
    val viewModel: MangaDetailViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}",
        factory = MangaDetailViewModel.Factory(context, source, mangaDetails)
    )
    val uiState by viewModel.uiState.collectAsState()
    val details = uiState.details
    // Fandom Wiki: eklenti kaynağında kimlik yoktur; seri başlığıyla (isim yolu) çözülür.
    val wikiViewModel: FandomMediaWikiViewModel = viewModel(key = "fandom_manga_${source.name}_${mangaDetails.url}")
    val wikiQuery = remember(details.title) {
        FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf(details.title)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // ─── Hero / Kapak Başlık ───────────────────────────────────────────
            item {
                MangaDetailHeader(details = details, accent = accent)
            }

            // ─── Oku / Devam Et Butonu ─────────────────────────────────────────
            item {
                val resume = uiState.resumeChapter
                val canRead = uiState.chapters.isNotEmpty()
                KitsugiDetailActionButton(
                    text = when {
                        resume != null -> stringResource(R.string.devam_et_x, resume.name)
                        else -> stringResource(R.string.ui_manga_resume_from_beginning)
                    },
                    icon = if (resume != null) Icons.PlayArrow else Icons.MenuBook,
                    onClick = {
                        viewModel.chapterToOpen()?.let(onOpenChapter)
                    },
                    enabled = canRead,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // ─── Açıklama ──────────────────────────────────────────────────────
            if (!details.description.isNullOrBlank()) {
                item {
                    Text(
                        text = details.description!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ─── Fandom Wiki (açılır kart; içerik kullanıcı açınca yüklenir) ─────
            item(key = "fandom_wiki") {
                MediaWikiSectionCard(
                    viewModel = wikiViewModel,
                    query = wikiQuery,
                    accentColor = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ─── Bölümler Başlığı + Çevirmen Birleştirme ──────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bolumler) +
                                if (uiState.groups.isNotEmpty()) " (${uiState.visibleGroups.size})" else "",
                            color = KitsugiColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Aynı bölümün kaç farklı çevirmeni var? (birleşik görünümde özet)
                        if (uiState.mergedScanlators && uiState.chapters.size > uiState.groups.size) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.manga_chapter_summary,
                                    uiState.groups.size,
                                    uiState.chapters.size
                                ),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.toggleMergedScanlators() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.mergedScanlators) Icons.Rounded.CallMerge else Icons.Rounded.CallSplit,
                            contentDescription = if (uiState.mergedScanlators) {
                                stringResource(R.string.manga_merge_on)
                            } else {
                                stringResource(R.string.manga_merge_off)
                            },
                            tint = if (uiState.mergedScanlators) accent else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showMarkAllConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = stringResource(R.string.manga_mark_all_read),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.loadChapters() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ─── Hatırlanan çevirmen tercihi ───────────────────────────────────
            if (uiState.preferredScanlator != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(
                                        R.string.manga_scanlator_preference,
                                        uiState.preferredScanlator ?: ""
                                    ),
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { viewModel.clearPreferredScanlator() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.manga_scanlator_clear),
                                        tint = accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Çevirmen filtresi ─────────────────────────────────────────────
            if (uiState.scanlators.size > 1) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "scanlator_all") {
                            ScanlatorChip(
                                label = stringResource(R.string.manga_scanlator_all),
                                selected = uiState.scanlatorFilter == null,
                                accent = accent,
                                onClick = { viewModel.setScanlatorFilter(null) }
                            )
                        }
                        items(uiState.scanlators, key = { "scanlator:$it" }) { name ->
                            ScanlatorChip(
                                label = name,
                                selected = uiState.scanlatorFilter.equals(name, ignoreCase = true),
                                accent = accent,
                                onClick = {
                                    viewModel.setScanlatorFilter(
                                        if (uiState.scanlatorFilter.equals(name, ignoreCase = true)) null else name
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ─── Bölüm içi arama + okunmamış filtresi ──────────────────────────
            if (uiState.groups.size > 5) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.chapterQuery,
                            onValueChange = { viewModel.setChapterQuery(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.manga_chapter_search_hint),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.chapterQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.setChapterQuery("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.manga_search_clear),
                                            tint = KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = KitsugiColors.Border,
                                cursorColor = accent,
                                focusedTextColor = KitsugiColors.TextPrimary,
                                unfocusedTextColor = KitsugiColors.TextPrimary,
                                focusedContainerColor = KitsugiColors.Surface.copy(0.4f),
                                unfocusedContainerColor = KitsugiColors.Surface.copy(0.25f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (uiState.onlyUnread) accent.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceStrong
                                )
                                .tvClickable(shape = RoundedCornerShape(50)) { viewModel.toggleOnlyUnread() }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manga_chapter_only_unread),
                                color = if (uiState.onlyUnread) accent else KitsugiColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (uiState.onlyUnread) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ─── Bölüm Durumları ───────────────────────────────────────────────
            when {
                uiState.isLoadingChapters -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 46.dp)
                    }
                }

                uiState.error != null -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            uiState.error!!,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        uiState.errorCode?.let { code -> com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code) }
                        Spacer(Modifier.height(12.dp))
                        KitsugiButton(
                            onClick = { viewModel.loadChapters() }
                        ) {
                            Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                uiState.mergedScanlators && uiState.groups.isNotEmpty() && uiState.visibleGroups.isEmpty() -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(
                                if (uiState.chapterQuery.isNotBlank() || uiState.onlyUnread) {
                                    R.string.manga_chapter_no_match
                                } else {
                                    R.string.manga_scanlator_filter_empty
                                }
                            ),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp
                        )
                        if (uiState.hasActiveChapterFilter) {
                            Spacer(Modifier.height(10.dp))
                            KitsugiButton(onClick = { viewModel.clearChapterFilters() }) {
                                Text(stringResource(R.string.manga_chapter_clear_filters), fontSize = 12.sp)
                            }
                        }
                    }
                }

                uiState.mergedScanlators && uiState.groups.isNotEmpty() -> items(
                    items = uiState.visibleGroups,
                    key = { "group:${it.group.key}" }
                ) { groupRow ->
                    ChapterGroupListItem(
                        row = groupRow,
                        accent = accent,
                        expanded = groupRow.group.key in uiState.expandedGroupKeys,
                        onToggle = { viewModel.toggleGroup(groupRow.group.key) },
                        onOpenChapter = { chapter ->
                            viewModel.rememberVariant(groupRow.group.key, chapter)
                            onOpenChapter(chapter)
                        },
                        onToggleCompleted = { chapter -> viewModel.toggleChapterCompleted(chapter) }
                    )
                }

                else -> items(
                    items = uiState.visibleChapters,
                    key = { it.chapter.url }
                ) { row ->
                    ChapterListItem(
                        row = row,
                        accent = accent,
                        onClick = { onOpenChapter(row.chapter) },
                        onToggleCompleted = { viewModel.toggleChapterCompleted(row.chapter) }
                    )
                }
            }
        }

        // ─── Geri Butonu (üstte sabit) ─────────────────────────────────────────
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.35f))
        ) {
            Icon(
                Icons.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = Color.White
            )
        }
    }

    // ─── "Görünenleri okundu işaretle" onayı ──────────────────────────────────
    if (showMarkAllConfirm) {
        val pendingCount = uiState.visibleGroups.count { !it.isCompleted }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMarkAllConfirm = false },
            containerColor = KitsugiColors.SurfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.manga_mark_all_read),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.manga_mark_all_read_desc, pendingCount),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                KitsugiButton(
                    onClick = {
                        showMarkAllConfirm = false
                        viewModel.markVisibleAsCompleted()
                    }
                ) {
                    Text(stringResource(R.string.onayla), fontSize = 13.sp)
                }
            },
            dismissButton = {
                Text(
                    text = stringResource(R.string.settings_cancel),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { showMarkAllConfirm = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        )
    }
}

@Composable
private fun MangaDetailHeader(details: MangaDetails, accent: Color) {
    // +18 türü/source → bulanıklık her koşulda uygulanır
    val isAdultContent = details.isAdultContent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Bulanık arkaplan kapak
        if (!details.thumbnailUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = "",
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // ÇÖKME ÖNLEMİ: koşulsuz `.blur(24.dp)` → güvenli sarmalayıcı.
                    // 24dp, MIUI libhwui'de RenderThread SIGSEGV üreten yarıçaplardan;
                    // safeBlur hem API 31 altını atlar hem yarıçapı 16dp'ye sınırlar.
                    .safeBlur(16.dp)
            )
        }
        // Karartma gradyanı
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KitsugiColors.Background.copy(0.55f),
                            KitsugiColors.Background.copy(0.85f),
                            KitsugiColors.Background
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Kapak
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = details.title,
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!details.author.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details.author!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(details.status.labelRes()),
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (details.source.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = details.source,
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                if (details.genre.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = details.genre.take(3).joinToString(" • "),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanlatorChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.22f) else KitsugiColors.SurfaceStrong)
            .tvClickable(shape = RoundedCornerShape(50)) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ChapterGroupListItem(
    row: MangaDetailViewModel.ChapterGroupRow,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenChapter: (MangaChapter) -> Unit,
    onToggleCompleted: (MangaChapter) -> Unit
) {
    val primary = row.primary
    val multiple = row.group.hasMultipleVariants
    val selectedUrl = primary.chapter.url

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = RoundedCornerShape(8.dp)) {
                    if (multiple) onToggle() else onOpenChapter(primary.chapter)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.group.displayName,
                    color = if (row.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (row.isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = buildString {
                    append(primary.chapter.scanlator?.takeIf { it.isNotBlank() } ?: "—")
                    if (primary.isInProgress && primary.totalPages > 0) {
                        append(" • Sayfa ${primary.lastPageIndex + 1}/${primary.totalPages}")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (primary.isInProgress) accent else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (multiple) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.manga_chapter_variants, row.group.variantCount),
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                CompletedToggle(
                    row = primary,
                    onToggleCompleted = { onToggleCompleted(primary.chapter) }
                )
            }
        }

        // ─── Açılan çevirmen listesi ───────────────────────────────────────────
        // Alt liste hafif bir yüzeyle ayrılır: kullanıcı hangi satırların
        // "aynı bölümün sürümleri" olduğunu bir bakışta görür.
        if (expanded && multiple) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.5f))
                    .padding(vertical = 2.dp)
            ) {
                row.variants.forEach { variant ->
                    ChapterVariantRow(
                        row = variant,
                        accent = accent,
                        isSelected = variant.chapter.url == selectedUrl,
                        onClick = { onOpenChapter(variant.chapter) },
                        onToggleCompleted = { onToggleCompleted(variant.chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterVariantRow(
    row: MangaDetailViewModel.ChapterRow,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .tvClickable(shape = RoundedCornerShape(8.dp)) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isSelected) accent else KitsugiColors.TextMuted.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.chapter.scanlator?.takeIf { it.isNotBlank() } ?: "—",
                color = if (isSelected) accent else KitsugiColors.TextPrimary.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val info = buildString {
                if (row.isInProgress && row.totalPages > 0) {
                    append("Sayfa ${row.lastPageIndex + 1}/${row.totalPages}")
                } else if (row.isCompleted) {
                    append("Okundu")
                }
                val date = formatChapterDate(row.chapter.uploadDate)
                if (date != null) {
                    if (isNotEmpty()) append(" • ")
                    append(date)
                }
            }
            if (info.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = info,
                    color = if (row.isInProgress) accent.copy(alpha = 0.85f) else KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CompletedToggle(row = row, onToggleCompleted = onToggleCompleted)
    }
}

private fun formatChapterDate(raw: Long): String? {
    if (raw <= 0L) return null
    val millis = if (raw > 10_000_000_000L) raw else raw * 1000L
    return runCatching {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }.getOrNull()
}

@Composable
private fun ChapterListItem(
    row: MangaDetailViewModel.ChapterRow,
    accent: Color,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(8.dp)) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.chapter.name,
                color = if (row.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (row.isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (row.isInProgress && row.totalPages > 0) {
                    append("Sayfa ${row.lastPageIndex + 1}/${row.totalPages}")
                } else if (!row.chapter.scanlator.isNullOrBlank()) {
                    append(row.chapter.scanlator)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (row.isInProgress) accent else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CompletedToggle(row = row, onToggleCompleted = onToggleCompleted)
    }
}

/** Küçük "okundu / okunmadı" düğmesi — başka yerde okunan bölümleri işaretlemek için. */
@Composable
private fun CompletedToggle(
    row: MangaDetailViewModel.ChapterRow,
    onToggleCompleted: () -> Unit
) {
    IconButton(
        onClick = onToggleCompleted,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (row.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(
                if (row.isCompleted) R.string.manga_mark_unread else R.string.manga_mark_read
            ),
            tint = if (row.isCompleted) KitsugiColors.AccentGreen.copy(0.85f)
            else KitsugiColors.TextMuted.copy(0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

