package com.kitsugi.animelist.ui.tv.manga

import android.view.KeyEvent
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.tv.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.screens.manga.MangaReaderViewModel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * TV-Native D-pad uyumlu Manga Okuyucu Ayarlar Overlay Paneli.
 *
 * — Ekranın sağ tarafında 360dp genişliğinde şık bir panel olarak açılır.
 * — Seçenekler dikey olarak hizalanmıştır ve D-pad ile gezinilebilir.
 * — Her satırda D-pad Sol/Sağ basılarak ayarlar anında döngüsel olarak değiştirilir.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMangaReaderControls(
    viewModel: MangaReaderViewModel,
    onDismiss: () -> Unit,
    autoScrollEnabled: Boolean = false,
    onAutoScrollChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editLongTapGrid by remember { mutableStateOf(false) }

    // Panel dışına tıklanınca kapatmak için arka plana tıklama veya D-pad geri tuşunu dinleme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .tvClickable(enabled = true, onClick = onDismiss)
    ) {
        // Sağ panel gövdesi
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(KitsugiColors.BackgroundElevated)
                .verticalScroll(rememberScrollState())
                .border(1.dp, Color.White.copy(alpha = 0.1f))
                .tvClickable(enabled = true, onClick = {}) // Tıklamayı arka plana geçirmesin
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.okuyucu_ayarlari),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TvReaderBooleanRow(stringResource(R.string.reader_series_mode), uiState.seriesModeOverride, viewModel::setSeriesModeOverride)
            // 1. Okuma Yönü
            val readingModes = listOf(
                ReadingMode.RightToLeft to stringResource(R.string.sagdan_sola_manga),
                ReadingMode.LeftToRight to "Soldan Sağa",
                ReadingMode.Vertical to "Dikey",
                ReadingMode.Webtoon to "Webtoon"
            )
            val currentModeIdx = readingModes.indexOfFirst { it.first == uiState.readingMode }.coerceAtLeast(0)
            TvControlCycleRow(
                title = stringResource(R.string.okuma_yonu),
                description = stringResource(R.string.manga_veya_webtoon_okuma_akisini_degistirin),
                currentValueText = readingModes[currentModeIdx].second,
                onPrevious = {
                    val prevIdx = (currentModeIdx - 1 + readingModes.size) % readingModes.size
                    viewModel.setReadingMode(readingModes[prevIdx].first)
                },
                onNext = {
                    val nextIdx = (currentModeIdx + 1) % readingModes.size
                    viewModel.setReadingMode(readingModes[nextIdx].first)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.readingMode == ReadingMode.LeftToRight || uiState.readingMode == ReadingMode.RightToLeft) {
                TvReaderBooleanRow(stringResource(R.string.reader_double_page), uiState.doublePage, viewModel::setDoublePage)
                if (uiState.doublePage) TvReaderBooleanRow(stringResource(R.string.reader_cover_alone), uiState.coverAlone, viewModel::setCoverAlone)
            }
            // 2. Görsel Sığdırma Modu
            val fitModes = listOf(
                MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                MangaFitMode.FitWidth to "Genişliğe Sığdır",
                MangaFitMode.FitHeight to "Yükseğe Sığdır"
            )
            val currentFitIdx = fitModes.indexOfFirst { it.first == uiState.fitMode }.coerceAtLeast(0)
            TvControlCycleRow(
                title = stringResource(R.string.gorsel_sigdirma),
                description = stringResource(R.string.sayfalarin_ekrana_sigdirilma_kuralini_belirleyin),
                currentValueText = fitModes[currentFitIdx].second,
                onPrevious = {
                    val prevIdx = (currentFitIdx - 1 + fitModes.size) % fitModes.size
                    viewModel.setFitMode(fitModes[prevIdx].first)
                },
                onNext = {
                    val nextIdx = (currentFitIdx + 1) % fitModes.size
                    viewModel.setFitMode(fitModes[nextIdx].first)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Renk Filtresi
            val colorFilters = listOf(
                ColorFilterType.Normal to "Normal",
                ColorFilterType.Grayscale to "Siyah-Beyaz",
                ColorFilterType.Sepia to "Sepya",
                ColorFilterType.Invert to "Ters Negatif"
            )
            val currentFilterIdx = colorFilters.indexOfFirst { it.first == uiState.colorFilterType }.coerceAtLeast(0)
            TvControlCycleRow(
                title = "Renk Filtresi",
                description = stringResource(R.string.sayfa_renklerini_goz_sagligina_gore_filtreleyin),
                currentValueText = colorFilters[currentFilterIdx].second,
                onPrevious = {
                    val prevIdx = (currentFilterIdx - 1 + colorFilters.size) % colorFilters.size
                    viewModel.setColorFilter(colorFilters[prevIdx].first)
                },
                onNext = {
                    val nextIdx = (currentFilterIdx + 1) % colorFilters.size
                    viewModel.setColorFilter(colorFilters[nextIdx].first)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val saturationValues = listOf(0, 50, 100, 150, 200)
            val saturationIndex = saturationValues.indexOf(uiState.saturation).coerceAtLeast(0)
            TvControlCycleRow(title = stringResource(R.string.reader_saturation), description = "",
                currentValueText = "${uiState.saturation}%",
                onPrevious = { viewModel.setSaturation(saturationValues[(saturationIndex - 1 + saturationValues.size) % saturationValues.size]) },
                onNext = { viewModel.setSaturation(saturationValues[(saturationIndex + 1) % saturationValues.size]) })
            val contrastValues = listOf(50, 75, 100, 125, 150, 200)
            val contrastIndex = contrastValues.indexOf(uiState.contrast).coerceAtLeast(0)
            TvControlCycleRow(title = stringResource(R.string.reader_contrast), description = "",
                currentValueText = "${uiState.contrast}%",
                onPrevious = { viewModel.setContrast(contrastValues[(contrastIndex - 1 + contrastValues.size) % contrastValues.size]) },
                onNext = { viewModel.setContrast(contrastValues[(contrastIndex + 1) % contrastValues.size]) })
            Spacer(modifier = Modifier.height(16.dp))

            val hueValues = listOf(-180, -90, 0, 90, 180)
            val hueIndex = hueValues.indexOf(uiState.hue).coerceAtLeast(0)
            TvControlCycleRow(title = stringResource(R.string.reader_hue), description = "",
                currentValueText = "${uiState.hue}°",
                onPrevious = { viewModel.setHue(hueValues[(hueIndex - 1 + hueValues.size) % hueValues.size]) },
                onNext = { viewModel.setHue(hueValues[(hueIndex + 1) % hueValues.size]) })
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Parlaklık Ayarı
            val brightnessValues = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
            val currentBrightnessIdx = brightnessValues.indexOfFirst { it >= uiState.customBrightness }.coerceAtLeast(0)
            val brightnessPercent = "${(uiState.customBrightness * 100).toInt()}%"
            TvControlCycleRow(
                title = stringResource(R.string.inline_reader_brightness_e5e9f7a),
                description = stringResource(R.string.tv_ekrani_uzerinde_yapay_karartma_uygulayin),
                currentValueText = brightnessPercent,
                onPrevious = {
                    if (currentBrightnessIdx > 0) {
                        viewModel.setCustomBrightness(brightnessValues[currentBrightnessIdx - 1])
                    }
                },
                onNext = {
                    if (currentBrightnessIdx < brightnessValues.size - 1) {
                        viewModel.setCustomBrightness(brightnessValues[currentBrightnessIdx + 1])
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            if (uiState.readingMode == ReadingMode.Webtoon) {
                val paddings = listOf(0, 16, 32, 64)
                val gaps = listOf(0, 4, 16, 32)
                val paddingIndex = paddings.indexOf(uiState.webtoonPadding).coerceAtLeast(0)
                val gapIndex = gaps.indexOf(uiState.webtoonGap).coerceAtLeast(0)
                TvControlCycleRow(
                    title = stringResource(R.string.reader_webtoon_padding), description = "",
                    currentValueText = "${uiState.webtoonPadding} dp",
                    onPrevious = { viewModel.setWebtoonPadding(paddings[(paddingIndex - 1 + paddings.size) % paddings.size]) },
                    onNext = { viewModel.setWebtoonPadding(paddings[(paddingIndex + 1) % paddings.size]) }
                )
                TvControlCycleRow(
                    title = stringResource(R.string.reader_webtoon_gap), description = "",
                    currentValueText = "${uiState.webtoonGap} dp",
                    onPrevious = { viewModel.setWebtoonGap(gaps[(gapIndex - 1 + gaps.size) % gaps.size]) },
                    onNext = { viewModel.setWebtoonGap(gaps[(gapIndex + 1) % gaps.size]) }
                )
                TvReaderBooleanRow(stringResource(R.string.reader_webtoon_tap_scroll), uiState.webtoonTapScroll, viewModel::setWebtoonTapScroll)
                val scrollAmounts = listOf(50, 80, 100)
                val amountIndex = scrollAmounts.indexOf(uiState.webtoonScrollPercent).coerceAtLeast(0)
                TvControlCycleRow(title = stringResource(R.string.reader_webtoon_scroll_amount), description = "",
                    currentValueText = "${uiState.webtoonScrollPercent}%",
                    onPrevious = { viewModel.setWebtoonScrollPercent(scrollAmounts[(amountIndex - 1 + scrollAmounts.size) % scrollAmounts.size]) },
                    onNext = { viewModel.setWebtoonScrollPercent(scrollAmounts[(amountIndex + 1) % scrollAmounts.size]) })
                TvReaderBooleanRow(stringResource(R.string.reader_webtoon_auto_next), uiState.webtoonAutoNext, viewModel::setWebtoonAutoNext)
                TvReaderBooleanRow(stringResource(R.string.reader_webtoon_auto_scroll), autoScrollEnabled, onAutoScrollChange)
                val speeds = listOf(20, 40, 80, 120)
                val speedIndex = speeds.indexOf(uiState.webtoonAutoScrollSpeed).coerceAtLeast(0)
                TvControlCycleRow(title = stringResource(R.string.reader_auto_scroll_speed), description = stringResource(R.string.reader_auto_scroll_hint),
                    currentValueText = stringResource(R.string.reader_scroll_speed_value, uiState.webtoonAutoScrollSpeed),
                    onPrevious = { viewModel.setWebtoonAutoScrollSpeed(speeds[(speedIndex - 1 + speeds.size) % speeds.size]) },
                    onNext = { viewModel.setWebtoonAutoScrollSpeed(speeds[(speedIndex + 1) % speeds.size]) })
            }
            val backgrounds = listOf("Black", "DarkGray", "White", "Auto")
            val backgroundIndex = backgrounds.indexOf(uiState.readerBackground).coerceAtLeast(0)
            val backgroundLabels = listOf(R.string.reader_background_black, R.string.reader_background_gray, R.string.reader_background_white, R.string.reader_background_auto)
            TvControlCycleRow(
                title = stringResource(R.string.reader_background), description = "",
                currentValueText = stringResource(backgroundLabels[backgroundIndex]),
                onPrevious = { viewModel.setReaderBackground(backgrounds[(backgroundIndex - 1 + backgrounds.size) % backgrounds.size]) },
                onNext = { viewModel.setReaderBackground(backgrounds[(backgroundIndex + 1) % backgrounds.size]) }
            )
            val preloadValues = listOf(-1, 0, 1, 2, 3, 5)
            val preloadIndex = preloadValues.indexOf(uiState.preloadAhead).coerceAtLeast(0)
            TvControlCycleRow(title = stringResource(R.string.reader_preload_ahead), description = "",
                currentValueText = if (uiState.preloadAhead < 0) stringResource(R.string.reader_preload_auto) else uiState.preloadAhead.toString(),
                onPrevious = { viewModel.setPreloadAhead(preloadValues[(preloadIndex - 1 + preloadValues.size) % preloadValues.size]) },
                onNext = { viewModel.setPreloadAhead(preloadValues[(preloadIndex + 1) % preloadValues.size]) })
            TvReaderBooleanRow(stringResource(R.string.reader_skip_read_chapters), uiState.skipReadChapters, viewModel::setSkipReadChapters)
            TvReaderBooleanRow(stringResource(R.string.reader_pause_progress), uiState.pauseProgressSaving, viewModel::setPauseProgressSaving)
            if (uiState.pauseProgressSaving) Text(stringResource(R.string.reader_pause_progress_hint), color = Color.White.copy(alpha = 0.6f))
            TvReaderBooleanRow(stringResource(R.string.reader_show_page_number), uiState.showPageNumber, viewModel::setShowPageNumber)
            TvReaderBooleanRow(stringResource(R.string.reader_keep_screen_on), uiState.keepScreenOn, viewModel::setKeepScreenOn)
            TvReaderBooleanRow(stringResource(R.string.reader_immersive), uiState.immersive, viewModel::setImmersive)
            TvReaderBooleanRow(stringResource(R.string.reader_crop_borders), uiState.cropBorders, viewModel::setCropBorders)
            TvReaderBooleanRow(stringResource(R.string.reader_crop_borders_webtoon), uiState.cropBordersWebtoon, viewModel::setCropBordersWebtoon)
            TvReaderBooleanRow(stringResource(R.string.reader_webtoon_zoom), uiState.webtoonZoom, viewModel::setWebtoonZoom)
            if (uiState.cropBorders || uiState.cropBordersWebtoon) Text(stringResource(R.string.reader_crop_borders_hint), color = Color.White.copy(alpha = 0.6f))
            val wideModes = listOf("Off", "Clockwise", "CounterClockwise")
            val wideLabels = listOf(R.string.reader_wide_off, R.string.reader_wide_clockwise, R.string.reader_wide_counterclockwise)
            for (webtoon in listOf(false, true)) {
                val current = if (webtoon) uiState.wideRotationWebtoon else uiState.wideRotation
                val index = wideModes.indexOf(current).coerceAtLeast(0)
                TvControlCycleRow(title = stringResource(if (webtoon) R.string.reader_wide_rotation_webtoon else R.string.reader_wide_rotation), description = "",
                    currentValueText = stringResource(wideLabels[index]),
                    onPrevious = { viewModel.setWideRotation(wideModes[(index - 1 + wideModes.size) % wideModes.size], webtoon) },
                    onNext = { viewModel.setWideRotation(wideModes[(index + 1) % wideModes.size], webtoon) })
            }
            if (uiState.wideRotation != "Off" || uiState.wideRotationWebtoon != "Off") Text(stringResource(R.string.reader_wide_rotation_hint), color = Color.White.copy(alpha = 0.6f))
            val tapLayouts = listOf("Default", "LeftHand", "RightHand", "Custom")
            val tapLabels = listOf(R.string.reader_tap_default, R.string.reader_tap_left_hand, R.string.reader_tap_right_hand, R.string.reader_tap_custom)
            val tapIndex = tapLayouts.indexOf(uiState.tapLayout).coerceAtLeast(0)
            TvControlCycleRow(
                title = stringResource(R.string.reader_tap_layout), description = stringResource(R.string.reader_tap_one_hand_hint),
                currentValueText = stringResource(tapLabels[tapIndex]),
                onPrevious = { viewModel.setTapLayout(tapLayouts[(tapIndex - 1 + tapLayouts.size) % tapLayouts.size]) },
                onNext = { viewModel.setTapLayout(tapLayouts[(tapIndex + 1) % tapLayouts.size]) }
            )
            if (uiState.tapLayout == "Custom") {
                TvReaderBooleanRow(stringResource(R.string.reader_tap_edit_long), editLongTapGrid) { editLongTapGrid = it }
                Text(stringResource(R.string.reader_tap_grid_hint), color = Color.White.copy(alpha = 0.6f))
                val grid = if (editLongTapGrid) uiState.longTapGrid else uiState.tapGrid
                for (index in 0..8) {
                    val action = when (grid[index]) { 'P' -> R.string.reader_tap_previous; 'N' -> R.string.reader_tap_next; 'M' -> R.string.reader_tap_menu; else -> R.string.reader_tap_none }
                    TvControlCycleRow(title = stringResource(R.string.reader_tap_cell, index / 3 + 1, index % 3 + 1), description = "",
                        currentValueText = stringResource(action),
                        onPrevious = { viewModel.cycleTapGridCell(index, editLongTapGrid, -1) },
                        onNext = { viewModel.cycleTapGridCell(index, editLongTapGrid, 1) })
                }
                TvControlCycleRow(title = stringResource(R.string.reader_tap_reset), description = "", currentValueText = "↺",
                    onPrevious = viewModel::resetTapGrids, onNext = viewModel::resetTapGrids)
            }
            TvReaderBooleanRow(stringResource(R.string.reader_invert_taps), uiState.invertTapZones, viewModel::setInvertTapZones)
            TvReaderBooleanRow(stringResource(R.string.reader_volume_keys), uiState.volumeKeys, viewModel::setVolumeKeys)
            if (uiState.volumeKeys) TvReaderBooleanRow(stringResource(R.string.reader_invert_volume_keys), uiState.invertVolumeKeys, viewModel::setInvertVolumeKeys)
            Spacer(modifier = Modifier.height(24.dp))

            // Kapatma butonu
            var isCloseFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCloseFocused) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.1f)
                    )
                    .tvClickable(shape = RoundedCornerShape(8.dp)) { onDismiss() }
                    .onFocusChanged { isCloseFocused = it.isFocused },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.kapat),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Segment/Döngü kontrollü D-pad satırı. Sol/Sağ tuşları ile değeri kaydırır.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvControlCycleRow(
    title: String,
    description: String,
    currentValueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onPrevious()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onNext()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Değer Değiştirici Bölge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = currentValueText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TvReaderBooleanRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    TvControlCycleRow(
        title = title,
        description = "",
        currentValueText = stringResource(if (value) R.string.reader_setting_on else R.string.reader_setting_off),
        onPrevious = { onChange(!value) },
        onNext = { onChange(!value) }
    )
}
