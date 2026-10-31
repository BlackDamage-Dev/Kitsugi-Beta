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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.manga.MangaChapter
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
                        else -> "Baştan Oku"
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

            // ─── Bölümler Başlığı ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.bolumler) + if (uiState.chapters.isNotEmpty()) " (${uiState.chapters.size})" else "",
                        color = KitsugiColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.loadChapters() }) {
                        Icon(
                            Icons.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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

                // Bazı kaynaklar aynı bölüm URL'sini (farklı çeviri grupları) tekrar listeler.
                else -> uniqueItems(
                    items = uiState.chapters,
                    key = { it.chapter.url }
                ) { row ->
                    ChapterListItem(
                        row = row,
                        accent = accent,
                        onClick = { onOpenChapter(row.chapter) }
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

@Composable
private fun ChapterListItem(
    row: MangaDetailViewModel.ChapterRow,
    accent: Color,
    onClick: () -> Unit
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
        if (row.isCompleted) {
            Icon(
                Icons.CheckCircle,
                contentDescription = stringResource(R.string.okundu),
                tint = KitsugiColors.AccentGreen.copy(0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
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
