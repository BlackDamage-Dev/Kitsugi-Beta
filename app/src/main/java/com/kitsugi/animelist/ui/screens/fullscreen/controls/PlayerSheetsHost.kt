package com.kitsugi.animelist.ui.screens.fullscreen.controls



import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.slideInVertically

import androidx.compose.animation.slideOutVertically

import com.kitsugi.animelist.ui.theme.gradient.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.size

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp

import com.kitsugi.animelist.data.repository.StreamSource

import com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels

import com.kitsugi.animelist.ui.screens.fullscreen.Decoder

import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels

import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiSheets

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.ChaptersSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.PlaybackSpeedSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.SubtitleTracksSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.SubtitleTrackInfo

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.AudioTracksSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.AudioTrackInfo

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.QualitySheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.SourceSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.SourcesSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.VideoQualitySheet

import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.MoreSheet

import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.ScreenshotSheet

import com.kitsugi.animelist.ui.screens.fullscreen.ArtType

import com.kitsugi.animelist.data.local.CustomButton

import java.io.InputStream



/**

 * Tüm sheet'leri merkezi olarak yöneten koordinatör.

 * [sheetShown] değerine göre ilgili sheet gösterilir.

 */

@Composable

fun PlayerSheetsHost(

    sheetShown: KitsugiSheets,

    dismissSheet: Boolean,

    onDismissRequest: () -> Unit,

    onOpenPanel: (KitsugiPanels) -> Unit,

    /** Sheet'ler arası geçiş (Kalite ↔ Kaynak). */

    onShowSheet: (KitsugiSheets) -> Unit = {},



    // Playback Speed

    currentSpeed: Float = 1f,

    onSpeedChange: (Float) -> Unit = {},

    onSetSpeedAsDefault: (Float) -> Unit = {},



    // Chapters

    currentChapter: IndexedSegment? = null,

    chapters: List<IndexedSegment> = emptyList(),

    onSeekToChapter: (IndexedSegment) -> Unit = {},



    // Subtitle Tracks

    subtitleTracks: List<SubtitleTrackInfo> = emptyList(),

    selectedSubtitleIndex: Int = -1,

    onSelectSubtitle: (Int) -> Unit = {},

    selectedSecondarySubtitleIndex: Int = -1,

    supportsSecondarySubtitles: Boolean = false,

    onSelectSecondarySubtitle: (Int) -> Unit = {},

    onAddSubtitleFile: () -> Unit = {},

    /** Oynatıcıdaki SubtitleInput listesi — altyazı başına indirme butonu için */

    subtitleInputs: List<com.kitsugi.animelist.core.player.SubtitleInput> = emptyList(),

    onDownloadSubtitle: (com.kitsugi.animelist.core.player.SubtitleInput) -> Unit = {},



    // Audio Tracks

    audioTracks: List<AudioTrackInfo> = emptyList(),

    audioTrackLabels: List<String> = emptyList(),

    selectedAudioIndex: Int = -1,

    onSelectAudio: (Int) -> Unit = {},

    onDownloadAudio: (Int) -> Unit = {},

    onDownloadProviderAudio: (Int) -> Unit = {},

    externalAudioUrlsInPlayer: Set<String> = emptySet(),

    onAddAudioFile: () -> Unit = {},



    // Only tracks belonging to the currently prepared video, never provider links.

    videoQualityTracks: List<TrackOption> = emptyList(),

    isVideoQualityAuto: Boolean = true,

    onSelectVideoQuality: (TrackOption?) -> Unit = {},



    // Stream Sources

    streamSources: List<StreamSource> = emptyList(),

    selectedSourceIndex: Int = -1,

    onSelectSource: (Int) -> Unit = {},

    isQualityLoading: Boolean = false,



    // Kalite (açık akışın kendi manifestinden okunan gerçek kaliteler)

    /** HLS varyantları / DASH Representation'ları (yüksekten alçağa). */

    qualityOptions: List<com.kitsugi.animelist.core.player.QualityOption> = emptyList(),

    /** Seçili kalite; `null` = Otomatik. */

    selectedQuality: com.kitsugi.animelist.core.player.QualityOption? = null,

    isQualityVariantsLoading: Boolean = false,

    /** `null` → Otomatik, aksi hâlde seçilen kalite. */

    onSelectQuality: (com.kitsugi.animelist.core.player.QualityOption?) -> Unit = {},

    /** "Otomatik" satırı açıklaması (kalite profili ipucu). */

    qualityAutoSubtitle: String? = null,



    // Sleep Timer

    sleepTimerSecondsLeft: Int = 0,

    onStartSleepTimer: (Int) -> Unit = {},



    // Decoder selection

    selectedDecoder: Decoder = Decoder.Auto,

    onSelectDecoder: (Decoder) -> Unit = {},



    // Screenshot actions

    isLocalSource: Boolean = false,

    hasSubTracks: Boolean = false,

    showSubtitles: Boolean = true,

    onToggleShowSubtitles: (Boolean) -> Unit = {},

    cachePath: String = "",

    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit = { _, _ -> },

    onSaveScreenshot: (() -> InputStream) -> Unit = {},

    onShareScreenshot: (() -> InputStream) -> Unit = {},

    takeScreenshot: suspend (String, Boolean) -> InputStream? = { _, _ -> null },



    customButtons: List<CustomButton> = emptyList(),

    onClickCustomButton: (CustomButton) -> Unit = {},

    onLongClickCustomButton: (CustomButton) -> Unit = {},



    // Statistics Page

    statisticsPage: Int = 0,

    onSelectStatisticsPage: (Int) -> Unit = {},



    // Audio Channels

    audioChannels: AudioChannels = AudioChannels.Auto,

    onSelectAudioChannels: (AudioChannels) -> Unit = {},



    // External Video Player handoff

    onOpenExternalPlayer: (() -> Unit)? = null,

    reduceMotion: Boolean = false,



    modifier: Modifier = Modifier,

) {

    val isVisible = sheetShown != KitsugiSheets.None



    Box(modifier = modifier) {

        // Dim background overlay

        AnimatedVisibility(

            visible = isVisible,

            enter = fadeIn(),

            exit = fadeOut(),

        ) {

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .background(Color.Black.copy(alpha = 0.5f))

                    .clickable(

                        interactionSource = remember { MutableInteractionSource() },

                        indication = null,

                        onClick = onDismissRequest,

                    )

            )

        }



        // Slide-up sheet container

        AnimatedVisibility(

            visible = isVisible,

            enter = if (reduceMotion) fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),

            exit = if (reduceMotion) fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),

            modifier = Modifier.align(Alignment.BottomCenter),

        ) {

            when (sheetShown) {

                KitsugiSheets.None -> Box(Modifier.size(0.dp))



                KitsugiSheets.PlaybackSpeed -> {

                    PlaybackSpeedSheet(

                        speed = currentSpeed,

                        onSpeedChange = onSpeedChange,

                        onSetAsDefault = onSetSpeedAsDefault,

                        onDismissRequest = onDismissRequest,

                    )

                }



                KitsugiSheets.Chapters -> {

                    val chapter = currentChapter ?: return@AnimatedVisibility

                    if (chapters.isEmpty()) return@AnimatedVisibility

                    ChaptersSheet(

                        chapters = chapters,

                        currentChapter = chapter,

                        onClick = onSeekToChapter,

                        onDismissRequest = onDismissRequest,

                        dismissSheet = dismissSheet,

                    )

                }



                KitsugiSheets.SubtitleTracks -> {

                    val selectedIndices = if (selectedSubtitleIndex >= 0) listOf(selectedSubtitleIndex) else emptyList()

                    SubtitleTracksSheet(

                        tracks = subtitleTracks,

                        selectedIndices = selectedIndices,

                        selectedSecondaryIndex = selectedSecondarySubtitleIndex,

                        supportsSecondary = supportsSecondarySubtitles,

                        onSelectSecondary = onSelectSecondarySubtitle,

                        onSelect = onSelectSubtitle,

                        onAddSubtitleFile = onAddSubtitleFile,

                        onOpenSubtitleSettings = { onOpenPanel(KitsugiPanels.SubtitleSettings) },

                        onOpenSubtitleDelay = { onOpenPanel(KitsugiPanels.SubtitleDelay) },

                        onDismissRequest = onDismissRequest,

                        subtitleInputs = subtitleInputs,

                        onDownloadSubtitle = onDownloadSubtitle,

                    )

                }



                KitsugiSheets.AudioTracks -> {

                    val providerAudios = streamSources.getOrNull(selectedSourceIndex)?.externalAudioSources.orEmpty()

                    val audioTrackInfos = if (audioTracks.isNotEmpty()) {
                        audioTracks
                    } else {
                        audioTrackLabels.mapIndexed { index, label ->
                            AudioTrackInfo(
                                id = index,
                                label = label
                            )
                        }
                    }

                    AudioTracksSheet(

                        tracks = audioTrackInfos,

                        selectedId = selectedAudioIndex,

                        onSelect = onSelectAudio,

                        onDownload = onDownloadAudio,

                        providerAudioIndices = providerAudios.mapIndexedNotNull { index, source ->

                            val distinctHeaders = providerAudios.filter { it.url == source.url }

                                .map { it.requestHeaders }.distinct().size

                            index.takeIf { source.url.isNotBlank() &&

                                (source.url !in externalAudioUrlsInPlayer || distinctHeaders > 1) }

                        },

                        onDownloadProvider = onDownloadProviderAudio,

                        onAddAudioFile = onAddAudioFile,

                        onOpenDelayPanel = { onOpenPanel(KitsugiPanels.AudioDelay) },

                        onDismissRequest = onDismissRequest,

                    )

                }



                // ── KALİTE: yalnızca kalite seçenekleri ──────────────────────

                // v2.4.341: Bu sheet eskiden kaynak listesini de içeriyordu

                // ("Kalite / Kaynak"). Artık kalite ve kaynak AYRI sheet'ler.

                KitsugiSheets.QualityTracks -> {

                    QualitySheet(

                        streamSources = streamSources,

                        selectedIndex = selectedSourceIndex,

                        isLoading = isQualityLoading,

                        // Zaten oynayan kaynak seçildiyse videoyu yeniden yükleme.

                        onSelectQuality = { index ->

                            if (index != selectedSourceIndex) onSelectSource(index) else onDismissRequest()

                        },

                        onOpenSourceSheet = {

                            onShowSheet(KitsugiSheets.SourceTracks)

                        },

                        onDismissRequest = onDismissRequest,

                        dismissSheet = dismissSheet,

                        options = qualityOptions,

                        selectedOption = selectedQuality,

                        isLoadingVariants = isQualityVariantsLoading,

                        onSelectVariant = onSelectQuality,

                        autoSubtitle = qualityAutoSubtitle,

                    )

                }



                // ── KAYNAK: yalnızca sunucu (eklenti akışı) listesi ──────────

                KitsugiSheets.SourceTracks,

                KitsugiSheets.Sources -> {

                    SourceSheet(

                        streamSources = streamSources,

                        selectedIndex = selectedSourceIndex,

                        isLoading = isQualityLoading,

                        onSelect = { index ->

                            if (index != selectedSourceIndex) onSelectSource(index) else onDismissRequest()

                        },

                        onOpenQualitySheet = {

                            onShowSheet(KitsugiSheets.QualityTracks)

                        },

                        onDismissRequest = onDismissRequest,

                        dismissSheet = dismissSheet,

                    )

                }



                KitsugiSheets.More -> {

                    MoreSheet(

                        selectedDecoder = selectedDecoder,

                        onSelectDecoder = onSelectDecoder,

                        remainingTime = sleepTimerSecondsLeft,

                        onStartTimer = onStartSleepTimer,

                        onDismissRequest = onDismissRequest,

                        onEnterFiltersPanel = { onOpenPanel(KitsugiPanels.VideoFilters) },

                        customButtons = customButtons,

                        onClickCustomButton = onClickCustomButton,

                        onLongClickCustomButton = onLongClickCustomButton,

                        statisticsPage = statisticsPage,

                        onSelectStatisticsPage = onSelectStatisticsPage,

                        audioChannels = audioChannels,

                        onSelectAudioChannels = onSelectAudioChannels,

                        onOpenExternalPlayer = onOpenExternalPlayer,

                    )

                }



                KitsugiSheets.Screenshot -> {

                    ScreenshotSheet(

                        isLocalSource = isLocalSource,

                        hasSubTracks = hasSubTracks,

                        showSubtitles = showSubtitles,

                        onToggleShowSubtitles = onToggleShowSubtitles,

                        cachePath = cachePath,

                        onSetAsArt = onSetAsArt,

                        onSave = onSaveScreenshot,

                        onShare = onShareScreenshot,

                        takeScreenshot = takeScreenshot,

                        onDismissRequest = onDismissRequest,

                    )

                }

            }

        }

    }

}
