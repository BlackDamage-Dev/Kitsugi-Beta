package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.AiringEntry
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Haftalık yayın takvimi tam ekran görünümü.
 * Anime tabına "Bu Hafta Yayında" kart widget'ından açılır.
 *
 * @param currentEntries Kullanıcının listesindeki girişler (vurgu için).
 * @param titleLanguage  Başlık dil tercihi (ROMAJI / ENGLISH / NATIVE).
 * @param onOpenAiringEntry Karta tıklandığında detay sayfasını aç.
 * @param onBackClick     Geri butonuna tıklandığında tetiklenir.
 * @param preferredSource Veri kaynağı (anilist / mal / simkl vb.)
 * @param viewModel      Inject edilmiş veya varsayılan ViewModel.
 */
@Composable
fun KitsugiAiringCalendarScreen(
    currentEntries: List<MediaEntry> = emptyList(),
    titleLanguage: String = "ENGLISH",
    onOpenAiringEntry: (AiringEntry) -> Unit = {},
    onBackClick: () -> Unit = {},
    preferredSource: String = "anilist",
    viewModel: KitsugiAiringCalendarViewModel = viewModel(key = "calendar_$preferredSource")
) {
    LaunchedEffect(preferredSource) {
        viewModel.loadSchedule(preferredSource = preferredSource)
    }

    val accentColor = KitsugiColors.Accent
    var showOnlyMyList by rememberSaveable { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(true) }

    // +18 UYUMU (2026-10-14): yayın takvimi de göster/gizle sözleşmesine uyar —
    // yetişkin (isAdult) kayıtlar ayar kapalıyken gün raflarında listelenmez.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    // ── KRİTİK DÜZELTME (2026-10-21) — "takvim ilk açılışta boş" ────────────────
    // `weekSchedule` bir SnapshotStateMap ve YERİNDE güncelleniyor
    // (ViewModel: clear() + putAll()). Map referansı hiç değişmediği için
    // remember(viewModel.weekSchedule, …) anahtarı Compose tarafından "değişmedi"
    // sayılıyordu (aynı örnek → referans eşitliği). Sonuç: ilk açılışta, veri henüz
    // gelmeden hesaplanan BOŞ harita önbelleğe alınıyor; ağdan veri gelip
    // `weekSchedule` dolduktan sonra bile remember eski boş sonucu döndürüyordu.
    // Ekran yalnızca yeniden kurulduğunda (geri çıkıp tekrar girince) boş olmayan
    // map ile yeniden hesaplandığı için veri "ikinci/üçüncü açılışta" görünüyordu.
    // Çözüm: haritanın içerik kopyasını (toMap) anahtar olarak kullanmak; bu hem
    // snapshot okuması kaydeder (veri gelince recomposition) hem de içerik
    // değiştiğinde remember'ı geçersiz kılar.
    val scheduleSnapshot = viewModel.weekSchedule.toMap()
    val filteredScheduleMap = remember(scheduleSnapshot, showOnlyMyList, currentEntries, showAdultContent) {
        val base = if (showOnlyMyList) {
            scheduleSnapshot.mapValues { (_, entries) ->
                entries.filter { entry ->
                    currentEntries.any { me ->
                        (entry.malId != null && me.malId == entry.malId) ||
                                me.malId == entry.aniListId ||
                                me.tmdbId == entry.aniListId
                    }
                }
            }
        } else {
            // Canlı map yerine kopya kullanılır: remember sonucu yalnızca anahtarlara
            // (scheduleSnapshot) bağlı kalsın, arka planda map değişip eski sonuç dönmesin.
            scheduleSnapshot
        }
        base.mapValues { (_, entries) -> entries.filter { showAdultContent || !it.isAdult } }
    }

    val initialPage = remember { DAYS_ORDERED.indexOf(viewModel.selectedDay).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { DAYS_ORDERED.size }
    )

    // Pager swipe -> ViewModel update
    LaunchedEffect(pagerState.currentPage) {
        val targetDay = DAYS_ORDERED[pagerState.currentPage]
        if (viewModel.selectedDay != targetDay) {
            viewModel.selectDay(targetDay)
        }
    }

    // ViewModel update -> Pager animate
    LaunchedEffect(viewModel.selectedDay) {
        val targetPage = DAYS_ORDERED.indexOf(viewModel.selectedDay).coerceAtLeast(0)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // ── Başlık çubuğu ──────────────────────────────────────────────────
        AiringCalendarHeader(
            totalCount = filteredScheduleMap.values.sumOf { it.size },
            isLoading = viewModel.isLoading,
            showOnlyMyList = showOnlyMyList,
            onShowOnlyMyListChange = { showOnlyMyList = it },
            isGridView = isGridView,
            onGridViewChange = { isGridView = it },
            onRefresh = { viewModel.loadSchedule(preferredSource = preferredSource, force = true) },
            onBackClick = onBackClick,
            // Birleşik ("all") takvimde film/dizi kayıtları da olduğundan sayaç
            // etiketi TMDB modundaki gibi "içerik" olur.
            isTmdb = preferredSource == "tmdb" || preferredSource == "all"
        )

        // ── Gün sekmeleri ──────────────────────────────────────────────────
        AiringDayTabRow(
            days = DAYS_ORDERED,
            selectedDay = viewModel.selectedDay,
            scheduleMap = filteredScheduleMap,
            accentColor = accentColor,
            onDaySelected = { viewModel.selectDay(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── İçerik listesi ─────────────────────────────────────────────────
        when {
            (viewModel.isLoading || !viewModel.hasLoaded) && viewModel.weekSchedule.isEmpty() -> {
                Box(modifier = Modifier.weight(1f)) {
                    AiringCalendarSkeleton(isGridView = isGridView)
                }
            }
            viewModel.errorMessage != null && viewModel.weekSchedule.isEmpty() -> {
                AiringErrorState(
                    message = viewModel.errorMessage.orEmpty(),
                    onRetry = { viewModel.loadSchedule(preferredSource = preferredSource, force = true) },
                    platformErrorCode = viewModel.platformErrorCode
                )
            }
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val day = DAYS_ORDERED[page]
                    val entries = filteredScheduleMap[day] ?: emptyList()
                    if (isGridView) {
                        AiringEntryGridList(
                            entries = entries,
                            currentEntries = currentEntries,
                            titleLanguage = titleLanguage,
                            onEntryClick = onOpenAiringEntry
                        )
                    } else {
                        AiringEntryList(
                            entries = entries,
                            currentEntries = currentEntries,
                            titleLanguage = titleLanguage,
                            onEntryClick = onOpenAiringEntry
                        )
                    }
                }
            }
        }
    }
}
