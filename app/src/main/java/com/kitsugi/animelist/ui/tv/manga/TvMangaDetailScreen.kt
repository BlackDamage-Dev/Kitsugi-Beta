package com.kitsugi.animelist.ui.tv.manga

import androidx.activity.compose.BackHandler
import com.kitsugi.animelist.ui.utils.uniqueItemsIndexed
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiSectionCard
import com.kitsugi.animelist.ui.screens.manga.MangaDetailViewModel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.dpadRepeatThrottle
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * WP-13 — TV-optimize edilmiş Manga Detail ve Bölüm listesi ekranı.
 *
 * TV / 10-foot experience düzenlemeleri:
 * - İki panelli düzen: Solda büyük poster, başlık ve metadata. Sağda kaydırılabilir bölümler listesi.
 * - Kumanda ile kolay erişim için butonlar ve bölümler listesi D-pad focus grubu içinde yönetilir.
 * - `focusRestorer` ile listeye odaklanıldığında son kalınan bölüm vurgulanır.
 * - TV GPU performansı için bulanık arka plan yerine hafif alpha overlay kullanılır.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvMangaDetailScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    onOpenChapter: (MangaChapter) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: MangaDetailViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}",
        factory = MangaDetailViewModel.Factory(context, source, mangaDetails)
    )
    val uiState by viewModel.uiState.collectAsState()
    val details = uiState.details

    val wikiViewModel: FandomMediaWikiViewModel = viewModel(
        key = "fandom_tv_manga_${source.name}_${mangaDetails.url}"
    )
    val wikiQuery = remember(details.title, details.url) {
        FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf(details.title)
        )
    }

    val listState = rememberLazyListState()
    val backButtonFocusRequester = remember { FocusRequester() }
    val actionButtonFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(Unit) {
        // requestFocusAfterFrames: composable node'un layout tree'ye attach olmasını
        // bekler, ardından 4 deneme yapar — try-catch susturmak yerine gerçek timing fix.
        actionButtonFocusRequester.requestFocusAfterFrames(frames = 2)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // ── Arka Plan Backdrop (TV Optimized) ──────────────────────────────────
        if (!details.thumbnailUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = null,
                isAdult = details.isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.15f }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KitsugiColors.Background.copy(0.6f),
                            KitsugiColors.Background
                        )
                    )
                )
        )

        // ── Ana İçerik Grid (İki Panelli TV Düzeni) ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = KitsugiTvTokens.Spacing.screenHorizontal,
                    end = KitsugiTvTokens.Spacing.screenHorizontal,
                    top = KitsugiTvTokens.Spacing.screenVertical,
                    bottom = KitsugiTvTokens.Spacing.screenVertical
                ),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ── SOL PANEL: Poster, Başlık ve Açıklamalar ───────────────────────
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .tvClickable(shape = CircleShape) { onBack() }
                            .focusRequester(backButtonFocusRequester),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.inline_manga_details_e11e2d8),
                        style = MaterialTheme.typography.titleMedium,
                        color = KitsugiColors.TextSecondary
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                        model = details.thumbnailUrl,
                        contentDescription = details.title,
                        isAdult = details.isAdultContent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KitsugiColors.SurfaceSoft)
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = details.title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!details.author.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = details.author!!,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KitsugiColors.AccentBlue.copy(0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = details.status.label(),
                                    color = KitsugiColors.AccentBlue,
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
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Açıklama
                if (!details.description.isNullOrBlank()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Text(
                                text = details.description!!,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // ── Oku / Devam Et Aksiyon Butonu ──────────────────────────────
                val resume = uiState.resumeChapter
                val canRead = uiState.chapters.isNotEmpty()
                Button(
                    onClick = {
                        viewModel.chapterToOpen()?.let(onOpenChapter)
                    },
                    enabled = canRead,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitsugiColors.AccentBlue,
                        contentColor = Color.White,
                        disabledContainerColor = KitsugiColors.SurfaceStrong,
                        disabledContentColor = KitsugiColors.TextMuted
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(actionButtonFocusRequester)
                ) {
                    Icon(
                        imageVector = if (resume != null) Icons.PlayArrow else Icons.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            resume != null -> stringResource(R.string.devam_et_x, resume.name)
                            else -> "Baştan Oku"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── SAĞ PANEL: Bölümler + Wiki ─────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .background(KitsugiColors.Surface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .focusRequester(listFocusRequester)
                    .focusGroup()
                    .focusRestorer()
                    .dpadRepeatThrottle(horizontalGateMs = Long.MAX_VALUE, verticalGateMs = 80L),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item(key = "chapter_header") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.bolumler) + if (uiState.chapters.isNotEmpty()) " (${uiState.chapters.size})" else "",
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.loadChapters() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Refresh,
                                    contentDescription = stringResource(R.string.yenile),
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                when {
                    uiState.isLoadingChapters -> item(key = "chapters_loading") {
                        Box(
                            Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiPlasmaLoader(size = 48.dp)
                        }
                    }
                    uiState.error != null -> item(key = "chapters_error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                uiState.error!!,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadChapters() },
                                colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.AccentBlue)
                            ) {
                                Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> uniqueItemsIndexed(
                        items = uiState.chapters,
                        key = { _, ch -> ch.chapter.url }
                    ) { _, row ->
                        TvChapterListItem(
                            row = row,
                            onClick = { onOpenChapter(row.chapter) }
                        )
                    }
                }

                item(key = "fandom_wiki") {
                    MediaWikiSectionCard(
                        viewModel = wikiViewModel,
                        query = wikiQuery,
                        accentColor = KitsugiColors.AccentBlue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvChapterListItem(
    row: MangaDetailViewModel.ChapterRow,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = cardShape) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.chapter.name,
                color = if (row.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = remember(row) {
                buildString {
                    if (row.isInProgress && row.totalPages > 0) {
                        append("Sayfa ${row.lastPageIndex + 1}/${row.totalPages}")
                    } else if (!row.chapter.scanlator.isNullOrBlank()) {
                        append(row.chapter.scanlator)
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (row.isInProgress) KitsugiColors.AccentBlue else KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
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
                modifier = Modifier.size(16.dp)
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
