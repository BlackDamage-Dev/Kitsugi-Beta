package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.data.manga.ChapterSortMode
import com.kitsugi.animelist.data.manga.ChapterDisplayMode
import com.kitsugi.animelist.data.manga.displayLabel
import com.kitsugi.animelist.data.manga.duplicatedChapterNumbers
import com.kitsugi.animelist.data.manga.missingChapterNumbers
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.data.manga.sortedForMode
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.safeBlur
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiSectionCard
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterSearchRow
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterProgressSummary
import com.kitsugi.animelist.ui.screens.manga.components.MangaChaptersHeader
import com.kitsugi.animelist.ui.screens.manga.components.MangaDetailTopBar
import com.kitsugi.animelist.ui.screens.manga.components.MangaDetailTopBarHeight
import com.kitsugi.animelist.ui.screens.manga.components.MangaEmptyChapters
import com.kitsugi.animelist.ui.screens.manga.components.MangaFloatingBackButton
import com.kitsugi.animelist.ui.screens.manga.components.MangaScanlatorChipsRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.components.MangaSourceAuthBanner
import com.kitsugi.animelist.ui.components.MangaSourceLoginDialog
import com.kitsugi.animelist.ui.components.rememberDetailStickyHeaderVisible

/**
 * Manga Detay + "Oku" Ekranı.
 *
 * — Üstte bulanık kapak arkaplanı + kapak görseli + başlık/yazar/durum/türler
 * — Büyük "Baştan Oku" / "Devam Et" butonu
 * — Fandom Wiki kartı (açılır; içerik kullanıcı açınca yüklenir)
 * — Bölüm araç çubuğu: sıralama, okunmamış filtresi, yenile, bölüm arama, kaynak/tarayıcı çipleri
 * — Bölüm listesi (okunan bölümler işaretli, devam edilen bölüm vurgulu, yarım kalan
 *   bölümlerde sayfa ilerleme çubuğu)
 *
 * ## Kaydırmaya duyarlı üst bar
 * Anime/film detay sayfasıyla aynı mekanik: sayfa tepedeyken yalnızca yüzen geri düğmesi
 * görünür; hero + aksiyon + açıklama + wiki kartı geçildiğinde üst bar yumuşakça süzülür
 * ve başlık + bölüm sayısı + yenile düğmesini taşır (bkz. [MangaDetailTopBar]).
 *
 * Bir bölüme (veya Oku butonuna) tıklanınca [onOpenChapter] ile okuyucu açılır.
 *
 * Sticky "Bölümler" başlığı listenin 4. öğesidir: hero · aksiyon · (açıklama) · wiki ·
 * başlık. Açıklama boş olduğunda liste bir öğe kısalır; üst bar eşikleri bu yüzden
 * içerikten bağımsız olarak güvenli aralıkta tutulur (bkz. [rememberDetailStickyHeaderVisible]).
 */
private const val CHAPTERS_HEADER_INDEX = 4

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MangaDetailScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    onOpenChapter: (MangaChapter) -> Unit,
    onBack: () -> Unit,
    /**
     * Okuyucudan dönüşte artan sayaç. Değiştiğinde bölüm listesi yeniden çekilmez;
     * yalnızca Room'daki okuma ilerlemesi tazelenir (bkz. [MangaDetailViewModel.refreshProgress]).
     */
    refreshTick: Int = 0
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    val viewModel: MangaDetailViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}",
        factory = MangaDetailViewModel.Factory(context, source, mangaDetails)
    )
    val uiState by viewModel.uiState.collectAsState()
    // Bölüm indirme: durum haritası + indirilmiş URL'ler.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedUrls by viewModel.downloadedUrls.collectAsState()
    val details = uiState.details

    // Fandom Wiki: eklenti kaynağında kimlik yoktur; seri başlığıyla (isim yolu) çözülür.
    val wikiViewModel: FandomMediaWikiViewModel = viewModel(
        key = "fandom_manga_${source.name}_${mangaDetails.url}"
    )
    val wikiQuery = remember(details.title) {
        FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf(details.title)
        )
    }

    // ── Bölüm araç çubuğu durumu (döndürülünce/yeniden çizilince korunur) ─────
    var query by rememberSaveable { mutableStateOf("") }
    // Sticky başlığa gömülü hızlı arama alanı (uzun listelerde erişim için).
    var headerSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedScanlator by rememberSaveable { mutableStateOf<String?>(null) }
    // Yer imli (★) bölümlere hızlı filtre — Mihon'daki "bookmarked" filtresi.
    var bookmarkedOnly by rememberSaveable { mutableStateOf(false) }
    // Yinelenen bölümleri gizle (aynı numara birden fazla grupta yayınlanmışsa).
    var hideDuplicates by rememberSaveable { mutableStateOf(false) }
    var statusFilterName by rememberSaveable { mutableStateOf(ChapterStatusFilter.All.name) }
    // ⚠️ Sıralama artık `rememberSaveable` DEĞİL: ViewModel + DataStore üzerinden gelir.
    // Ekran her açıldığında "en yeni" ile başlamak, bölüm listesini 386 → 381 diye
    // gösteren hatanın kaynağıydı. Kalıcı tercih varsayılan olarak 1 → N (artan).
    val sortMode = uiState.sortMode
    val displayMode = uiState.displayMode
    val activeStatusFilter = runCatching { ChapterStatusFilter.valueOf(statusFilterName) }
        .getOrDefault(ChapterStatusFilter.All)

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showSourceLogin by remember { mutableStateOf(false) }

    // ── Kitaplık durumu ──────────────────────────────────────────────────────
    // Detay ekranı kitaplığa ekleme/çıkarma için ayrı bir ViewModel kullanır: kitaplık
    // tablosu seri bazlı olduğundan burada yalnızca "bu seri takipte mi" bilgisi gerekir.
    val libraryViewModel: com.kitsugi.animelist.ui.screens.manga.MangaLibraryViewModel = viewModel(
        key = "manga_library_detail",
        factory = com.kitsugi.animelist.ui.screens.manga.MangaLibraryViewModel.Factory(
            context
        ) { listOf(source) }
    )
    val libraryState by libraryViewModel.uiState.collectAsState()
    val libraryRow = remember(libraryState.rows, details.url, source.name) {
        libraryState.rows.firstOrNull {
            it.entity.url == details.url && it.entity.sourceName == source.name
        }
    }
    LaunchedEffect(details.url, source.name) { libraryViewModel.reload() }

    // Okuyucudan dönüş: ağ isteği yok, yalnızca okundu/ilerleme durumları tazelenir.
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) viewModel.refreshProgress()
    }

    // Üst bar: hero + aksiyon + açıklama + wiki kartı geçilince görünür.
    // Bölüm listesi sanallaştırıldığı için index eşikleri sabittir (bkz. aşağıdaki item'lar).
    val topBarVisible = rememberDetailStickyHeaderVisible(
        listState = listState,
        showAtIndex = 4,
        hideAtIndex = 2
    )

    // ── Kaynak (tarayıcı) etiketleri ─────────────────────────────────────────
    // Aynı bölüm birden çok gruplamada yayınlanabildiği için etiket listesi
    // eklentiden gelen scanlator alanından olduğu gibi türetilir.
    val scanlatorLabels = remember(uiState.chapters, source.name) {
        uiState.chapters
            .map { it.chapter.scanlatorLabel(source.name) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    // ── Görünen bölümler ─────────────────────────────────────────────────────
    val rowByUrl = remember(uiState.chapters) { uiState.chapters.associateBy { it.chapter.url } }

    val visibleChapters = remember(
        uiState.chapters,
        query,
        selectedScanlator,
        activeStatusFilter,
        sortMode,
        bookmarkedOnly,
        hideDuplicates,
        uiState.bookmarked,
        source.name
    ) {
        val needle = query.trim().lowercase()
        val duplicateNumbers = if (hideDuplicates) {
            uiState.chapters.map { it.chapter }.duplicatedChapterNumbers()
        } else {
            emptySet()
        }
        uiState.chapters
            .asSequence()
            .filter { row -> !bookmarkedOnly || row.chapter.url in uiState.bookmarked }
            .let { seq ->
                if (!hideDuplicates) seq
                else {
                    // Numara başına ilk kayıt tutulur; diğer grupların aynı bölümü gizlenir.
                    val seen = HashSet<Float>()
                    seq.filter { row ->
                        val number = row.chapter.chapterNumber
                        number !in duplicateNumbers || seen.add(number)
                    }
                }
            }
            .filter { row ->
                selectedScanlator == null ||
                    row.chapter.scanlatorLabel(source.name) == selectedScanlator
            }
            .filter { row -> activeStatusFilter.matches(row.isCompleted, row.isInProgress) }
            .filter { row ->
                if (needle.isEmpty()) return@filter true
                val number = if (row.chapter.chapterNumber > 0f) {
                    com.kitsugi.animelist.data.manga.formatChapterNumber(row.chapter.chapterNumber)
                } else {
                    ""
                }
                row.chapter.name.lowercase().contains(needle) ||
                    number.contains(needle) ||
                    row.chapter.scanlatorLabel(source.name).lowercase().contains(needle)
            }
            .map { it.chapter }
            .toList()
            .sortedForMode(sortMode)
    }

    // Kitaplık kartındaki ilerleme: bölüm listesi yüklendiğinde bir kez yazılır.
    LaunchedEffect(uiState.chapters.size, uiState.resumeChapter?.url, libraryRow?.entity?.id) {
        if (uiState.chapters.isNotEmpty() && libraryRow != null) {
            libraryViewModel.updateProgress(
                sourceName = source.name,
                mangaUrl = details.url,
                total = uiState.chapters.size,
                read = uiState.chapters.count { it.isCompleted },
                lastChapterName = uiState.resumeChapter?.name,
                lastChapterUrl = uiState.resumeChapter?.url
            )
        }
    }

    val readFilterActive = activeStatusFilter == ChapterStatusFilter.Read
    val unreadCount = remember(uiState.chapters) { uiState.chapters.count { !it.isCompleted } }
    val readCount = uiState.chapters.size - unreadCount
    val missingNumbers = remember(uiState.chapters) {
        uiState.chapters.map { it.chapter.chapterNumber }.missingChapterNumbers()
    }
    val duplicateChapterNumbers = remember(uiState.chapters) {
        uiState.chapters.map { it.chapter }.duplicatedChapterNumbers()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // ─── Hero / Kapak Başlık ───────────────────────────────────────────
            item(key = "hero") {
                MangaDetailHeader(details = details, accent = accent)
            }

            // ─── Oku / Devam Et + Kitaplık ─────────────────────────────────────
            // Kitaplık düğmesi AYRI bir liste öğesi DEĞİL: sticky başlık ve üst bar
            // eşikleri liste indekslerine bağlı (hero · aksiyon · açıklama · wiki ·
            // başlık). Yeni öğe eklemek eşikleri kaydırıp üst barın yanlış yerde
            // görünmesine yol açıyordu. Bu yüzden kitaplık düğmesi "Oku" satırının
            // sağında kompakt bir kare düğme.
            item(key = "read_action") {
                val resume = uiState.resumeChapter
                val canRead = uiState.chapters.isNotEmpty()
                val inLibrary = libraryRow != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiDetailActionButton(
                        text = when {
                            resume != null -> stringResource(R.string.devam_et_x, resume.name)
                            else -> stringResource(R.string.manga_read_from_start)
                        },
                        icon = if (resume != null) Icons.Rounded.PlayArrow
                               else Icons.AutoMirrored.Rounded.MenuBook,
                        onClick = {
                            viewModel.chapterToOpen()?.let(onOpenChapter)
                        },
                        enabled = canRead,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    // Takip et / takipten çıkar — tek dokunuş, durum renkle belli.
                    val libShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(libShape)
                            .background(
                                if (inLibrary) accent.copy(alpha = 0.18f)
                                else KitsugiColors.SurfaceSoft
                            )
                            .tvClickable(shape = libShape) {
                                if (inLibrary) {
                                    libraryRow?.let(libraryViewModel::removeFromLibrary)
                                } else {
                                    libraryViewModel.addToLibrary(source, details)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (inLibrary) Icons.Rounded.Bookmark
                            else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (inLibrary) {
                                stringResource(R.string.manga_library_remove)
                            } else {
                                stringResource(R.string.manga_library_add)
                            },
                            tint = if (inLibrary) accent else KitsugiColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ─── Açıklama ──────────────────────────────────────────────────────
            // Uzun özetler (bazı kaynaklar 2000+ karakter veriyor) bölüm listesini
            // ekranın çok altına itiyordu: artık 4 satırla sınırlı ve
            // "Devamını göster / Daralt" ile açılıyor.
            if (!details.description.isNullOrBlank()) {
                item(key = "description") {
                    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
                    // Kısa özetlerde "Devamını göster" gereksiz: metin gerçekten 4 satırı
                    // aşıyorsa gösterilir (onTextLayout ile ölçülür).
                    var descriptionOverflows by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = details.description!!,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!descriptionExpanded) {
                                    descriptionOverflows = result.hasVisualOverflow
                                }
                            }
                        )
                        if (descriptionOverflows || descriptionExpanded) {
                            Text(
                                text = if (descriptionExpanded) {
                                    stringResource(R.string.daha_az)
                                } else {
                                    stringResource(R.string.profile_show_more)
                                },
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .tvClickable(shape = RoundedCornerShape(6.dp)) {
                                        descriptionExpanded = !descriptionExpanded
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
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

            // ─── Bölümler Başlığı (sticky) ─────────────────────────────────────
            stickyHeader(key = "chapters_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.Background.copy(alpha = 0.97f))
                ) {
                    // Üst bar görünürken sticky başlık barın ALTINA düşmeli; aksi hâlde
                    // bölüm başlığı barın arkasında kalıyor. Bar gizliyken boşluk verilmez.
                    if (topBarVisible) Spacer(Modifier.height(MangaDetailTopBarHeight))
                    val headerTitle = stringResource(R.string.bolumler)
                    val headerCount = visibleChapters.size
                    // Sıralama değişince liste başına dön: kullanıcı yeni sıranın ilk
                    // bölümünü (özellikle "1 → N" seçildiğinde Bölüm 1'i) hemen görsün.
                    val scrollToFirstChapter: () -> Unit = {
                        val headerIdx = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "chapters_header" }
                            ?.index
                        if (headerIdx != null) {
                            scope.launch { listState.animateScrollToItem(headerIdx + 1) }
                        }
                    }
                    val onCycleSort: () -> Unit = {
                        viewModel.setSortMode(sortMode.next())
                        scrollToFirstChapter()
                    }
                    val onToggleReadFilter: () -> Unit = {
                        statusFilterName = if (readFilterActive) {
                            ChapterStatusFilter.All.name
                        } else {
                            ChapterStatusFilter.Read.name
                        }
                    }
                    val onRefreshChapters: () -> Unit = { viewModel.loadChapters() }
                    MangaChaptersHeader(
                        title = headerTitle,
                        count = headerCount,
                        sortLabel = sortMode.label(),
                        readFilterActive = readFilterActive,
                        highlightReadFilter = true,
                        accentColor = accent,
                        onCycleSort = onCycleSort,
                        onToggleReadFilter = onToggleReadFilter,
                        onRefresh = onRefreshChapters,
                        onToggleSearch = { headerSearchExpanded = !headerSearchExpanded },
                        searchExpanded = headerSearchExpanded,
                        // Sıralama menüsü: 1 → N / N → 1 / Son güncellenen tek dokunuşta.
                        // Sabit "NewestFirst" varsayılanı kaldırıldı; tercih kalıcı.
                        sortMode = sortMode,
                        onSelectSort = { mode ->
                            viewModel.setSortMode(mode)
                            scrollToFirstChapter()
                        },
                        displayMode = displayMode,
                        onSelectDisplayMode = { viewModel.setDisplayMode(it) },
                        bookmarkedOnly = bookmarkedOnly,
                        bookmarkedCount = uiState.bookmarked.size,
                        onToggleBookmarked = { bookmarkedOnly = !bookmarkedOnly },
                        unreadCount = unreadCount,
                        onJumpToCurrent = {
                            // "Kaldığım yere git": devam edilen bölümü bul ve listeyi oraya
                            // kaydır. Mihon issue #1698'deki talebin karşılığı — jest yerine
                            // açık bir düğme.
                            val target = uiState.resumeChapter ?: visibleChapters.firstOrNull()
                            val targetUrl = target?.url
                            if (targetUrl != null) {
                                val idx = visibleChapters.indexOfFirst { it.url == targetUrl }
                                if (idx >= 0) {
                                    val headerIdx = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == "chapters_header" }
                                        ?.index ?: 0
                                    scope.launch { listState.animateScrollToItem(headerIdx + 1 + idx) }
                                }
                            }
                        },
                        // Görünen bölümlerin tamamı okunduysa düğme "geri al" olur.
                        onToggleAllRead = {
                            if (visibleChapters.isNotEmpty()) {
                                val allRead = visibleChapters.all { ch ->
                                    rowByUrl[ch.url]?.isCompleted == true
                                }
                                viewModel.markAllRead(visibleChapters, !allRead)
                            }
                        },
                        allReadActive = visibleChapters.isNotEmpty() &&
                            visibleChapters.all { ch -> rowByUrl[ch.url]?.isCompleted == true }
                    )
                    // Sticky başlığın içinde açılan arama alanı: uzun listede
                    // aşağıdayken de bölüm araması tek dokunuş uzakta.
                    AnimatedVisibility(visible = headerSearchExpanded) {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                            MangaChapterSearchRow(
                                query = query,
                                onQueryChange = { query = it },
                                unreadOnly = activeStatusFilter == ChapterStatusFilter.Unread,
                                onToggleUnreadOnly = {
                                    statusFilterName =
                                        if (activeStatusFilter == ChapterStatusFilter.Unread) {
                                            ChapterStatusFilter.All.name
                                        } else {
                                            ChapterStatusFilter.Unread.name
                                        }
                                },
                                accentColor = accent
                            )
                        }
                    }
                }
            }

            // ─── Bölüm Araçları (arama + durum + kaynak çipleri) ───────────────
            item(key = "chapter_tools") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Okuma ilerlemesi + eksik bölüm uyarısı, araç çubuğunun içinde:
                    // ayrı bir liste öğesi eklemek sticky başlık eşiklerini kaydırıyordu.
                    MangaChapterProgressSummary(
                        readCount = readCount,
                        totalCount = uiState.chapters.size,
                        missingNumbers = missingNumbers,
                        accentColor = accent
                    )
                    MangaChapterSearchRow(
                        query = query,
                        onQueryChange = { query = it },
                        unreadOnly = activeStatusFilter == ChapterStatusFilter.Unread,
                        onToggleUnreadOnly = {
                            statusFilterName = if (activeStatusFilter == ChapterStatusFilter.Unread) {
                                ChapterStatusFilter.All.name
                            } else {
                                ChapterStatusFilter.Unread.name
                            }
                        },
                        accentColor = accent
                    )
                    MangaScanlatorChipsRow(
                        labels = scanlatorLabels,
                        selected = selectedScanlator,
                        onSelect = { selectedScanlator = it },
                        accentColor = accent,
                        // Yinelenen bölüm çipi: aynı numara birden fazla grupta varsa
                        // tek dokunuşla yalnızca bir tanesi gösterilir.
                        extraChipLabel = if (duplicateChapterNumbers.isNotEmpty()) {
                            stringResource(R.string.manga_hide_duplicates)
                        } else {
                            null
                        },
                        extraChipSelected = hideDuplicates,
                        onToggleExtraChip = { hideDuplicates = !hideDuplicates }
                    )
                    // Filtre sonucu boşsa kullanıcıya neden boş olduğunu söyle ve
                    // tek dokunuşla filtreleri sıfırlamasına izin ver (sessiz boş liste yok).
                    if (uiState.chapters.isNotEmpty() && visibleChapters.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.manga_no_matching_chapters),
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            KitsugiButton(
                                onClick = {
                                    query = ""
                                    selectedScanlator = null
                                    statusFilterName = ChapterStatusFilter.All.name
                                    bookmarkedOnly = false
                                    viewModel.setSortMode(ChapterSortMode.DEFAULT)
                                }
                            ) {
                                Text(stringResource(R.string.manga_clear_filters), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ─── Bölüm Durumları ───────────────────────────────────────────────
            when {
                uiState.isLoadingChapters -> item(key = "chapters_loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 46.dp)
                    }
                }

                uiState.error != null -> item(key = "chapters_error") {
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
                        uiState.errorCode?.let { code ->
                            com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (PlatformErrorCode.fromId(uiState.errorCode) == PlatformErrorCode.MangaAuth) {
                            MangaSourceAuthBanner(
                                source = source,
                                onLogin = { showSourceLogin = true },
                                onRetry = { viewModel.loadChapters() },
                            )
                        } else {
                            KitsugiButton(onClick = { viewModel.loadChapters() }) {
                                Text(stringResource(R.string.manga_retry), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                visibleChapters.isEmpty() -> item(key = "chapters_empty") {
                    MangaEmptyChapters(message = stringResource(R.string.manga_no_chapters_found))
                }

                else -> items(
                    items = visibleChapters,
                    key = { it.url }
                ) { chapter ->
                    val row = rowByUrl[chapter.url]
                    if (row != null) {
                        ChapterListItem(
                            row = row,
                            accent = accent,
                            onClick = { onOpenChapter(row.chapter) },
                            onToggleRead = { viewModel.setChapterRead(row.chapter, !row.isCompleted) },
                            // Uzun basış: bu bölümden öncekileri okundu işaretle
                            // (Mihon "mark previous as read" davranışı).
                            onLongPress = { viewModel.markPreviousRead(row.chapter) },
                            displayMode = displayMode,
                            isBookmarked = row.chapter.url in uiState.bookmarked,
                            onToggleBookmark = { viewModel.toggleBookmark(row.chapter) },
                            isDownloaded = row.chapter.url in downloadedUrls,
                            downloadProgress = downloadStates[row.chapter.url]?.progress,
                            onDownload = {
                                if (row.chapter.url in downloadedUrls) {
                                    viewModel.deleteDownload(row.chapter)
                                } else {
                                    viewModel.downloadChapter(row.chapter)
                                }
                            }
                        )
                    }
                }
            }

            item(key = "chapters_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }

        // ─── Kaydırmaya duyarlı üst bar ────────────────────────────────────────
        MangaDetailTopBar(
            title = details.title,
            subtitle = buildString {
                // Yazar/kaynak bilgisi hero'dan üst bara taşındı: kaydırırken hangi seride
                // ve hangi kaynakta olduğun her zaman görünür.
                if (!details.author.isNullOrBlank()) append(details.author)
                if (details.source.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(details.source)
                }
                if (isNotEmpty()) append(" · ")
                append("${stringResource(R.string.bolumler)} (${uiState.chapters.size})")
            },
            visible = topBarVisible,
            onBack = onBack,
            onRefresh = { viewModel.loadChapters() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ─── Hızlı Yukarı Kaydırma (Keşfet ile birebir aynı bileşen) ──────────
        // 232 bölümlük listede en alta inen kullanıcı tek dokunuşla başa döner.
        val showScrollToTop by remember {
            androidx.compose.runtime.derivedStateOf {
                listState.firstVisibleItemIndex > 2 || listState.firstVisibleItemScrollOffset > 400
            }
        }
        com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab(
            visible = showScrollToTop,
            isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            bottomBarScrollState = null,
            fallbackBottomBarVisible = false,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        // ─── Sayfa tepesindeki yüzen geri düğmesi ─────────────────────────────
        MangaFloatingBackButton(
            visible = !topBarVisible,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (showSourceLogin) {
            MangaSourceLoginDialog(
                source = source,
                onSessionUpdated = { viewModel.loadChapters() },
                onDismiss = { showSourceLogin = false },
            )
        }
    }
}

@Composable
private fun MangaDetailHeader(details: MangaDetails, accent: Color) {
    // +18 türü/source → bulanıklık her koşulda uygulanır
    val isAdultContent = details.isAdultContent
    // Hero yüksekliği ekrana göre: küçük telefonlarda bölüm listesine yer kalsın,
    // tablet/yatayda kapak büyüsün. Sabit 300dp küçük ekranlarda listenin yarısını yiyordu.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val heroHeight = when {
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE -> 200.dp
        configuration.screenHeightDp < 700 -> 260.dp
        else -> 292.dp
    }
    val coverWidth = if (configuration.screenHeightDp < 700) 104.dp else 116.dp
    val coverHeight = coverWidth * 1.42f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
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
                    .width(coverWidth)
                    .height(coverHeight)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = details.status.label(),
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

/**
 * Bölüm satırı.
 *
 * — Okunan bölümler soluklaşır ve sağda tik alır.
 * — Yarım kalan bölümde ince ilerleme çubuğu + "Sayfa 12/40" bilgisi gösterilir.
 * — Tik'e dokunmak bölümü okundu/okunmadı olarak işaretler (uzun listede tek tek
 *   okumaya geri dönmeden işaretleme yapılabilir).
 */
@Composable
private fun ChapterListItem(
    row: MangaDetailViewModel.ChapterRow,
    accent: Color,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    /** Bölüm satırı etiket biçimi (numara / başlık). */
    displayMode: ChapterDisplayMode = ChapterDisplayMode.DEFAULT,
    /** Bu bölüm yer iminde mi (★). */
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    /** Uzun basışta çalışır (öncekileri okundu işaretle). */
    onLongPress: (() -> Unit)? = null,
    /** Bölüm çevrimdışı okunabilir mi (CBZ indirilmiş). */
    isDownloaded: Boolean = false,
    /** İndirme sürüyorsa 0f..1f ilerleme, aksi hâlde null. */
    downloadProgress: Float? = null,
    /** İndir / indirmeyi sil. */
    onDownload: (() -> Unit)? = null
) {
    val numberPrefix = stringResource(R.string.manga_chapter_number_prefix)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(
                shape = RoundedCornerShape(8.dp),
                onLongClick = onLongPress,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.chapter.displayLabel(displayMode, numberPrefix),
                color = if (row.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (row.isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (row.isInProgress && row.totalPages > 0) {
                    append(stringResource(R.string.manga_page_of_total, row.lastPageIndex + 1, row.totalPages))
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
            // Yarım kalan bölümlerde ilerleme çubuğu — "nerede kaldım" tek bakışta görünür.
            if (row.isInProgress && row.totalPages > 0) {
                Spacer(Modifier.height(6.dp))
                val progress = ((row.lastPageIndex + 1).toFloat() / row.totalPages.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = KitsugiColors.SurfaceStrong
                )
            }
        }
        // ── İndirme göstergesi (Mihon: bölüm indirme rozetleri) ─────────────
        // İndirilmemiş → soluk ok; indiriliyor → halka; indirildi → dolu ikon.
        if (onDownload != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onDownload),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Dairesel ilerleme göstergesi: M3 CircularProgressIndicator'ın
                    // determinate hâli (sayfa yükleniyor göstergesiyle aynı görsel dil).
                    downloadProgress != null -> androidx.compose.material3.CircularProgressIndicator(
                        progress = { downloadProgress },
                        color = accent,
                        trackColor = KitsugiColors.SurfaceStrong,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(17.dp)
                    )
                    isDownloaded -> Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(R.string.manga_download_delete),
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                    else -> Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.manga_download_chapter),
                        tint = KitsugiColors.TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        // Yer imi (★) — Mihon'daki bölüm yer imiyle aynı davranış: satırı okumadan
        // işaretle, sonra yalnızca yer imlileri filtreleyerek bul.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .tvClickable(shape = CircleShape, onClick = onToggleBookmark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (isBookmarked) {
                    stringResource(R.string.manga_unbookmark_chapter)
                } else {
                    stringResource(R.string.manga_bookmark_chapter)
                },
                tint = if (isBookmarked) accent else KitsugiColors.TextMuted.copy(alpha = 0.35f),
                modifier = Modifier.size(17.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .tvClickable(shape = CircleShape, onClick = onToggleRead),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.okundu),
                tint = if (row.isCompleted) {
                    KitsugiColors.AccentGreen.copy(0.85f)
                } else {
                    KitsugiColors.TextMuted.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Başlık altındaki sıralama etiketi.
 *
 * Varsayılan mod (1 → N) açıkça "1 → N" yazar: kullanıcı listenin neden bu sırada
 * olduğunu ve nasıl değiştireceğini ilk bakışta anlar.
 */
private fun ChapterSortMode.label(): String = when (this) {
    ChapterSortMode.OldestFirst -> KitsugiStrings.get(R.string.manga_sort_label_oldest)
    ChapterSortMode.NewestFirst -> KitsugiStrings.get(R.string.manga_sort_label_newest)
    ChapterSortMode.RecentlyUpdated -> KitsugiStrings.get(R.string.manga_sort_label_recent)
}

private fun MangaStatus.label(): String = when (this) {
    MangaStatus.Ongoing -> "Devam Ediyor"
    MangaStatus.Completed -> "Tamamlandı"
    MangaStatus.Licensed -> "Lisanslı"
    MangaStatus.PublicationComplete -> "Yayın Tamamlandı"
    MangaStatus.Cancelled -> "İptal Edildi"
    MangaStatus.OnHiatus -> "Ara Verildi"
    MangaStatus.Unknown -> "Bilinmiyor"
}
