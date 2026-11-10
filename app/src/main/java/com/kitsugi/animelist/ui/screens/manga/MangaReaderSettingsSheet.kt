package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.data.manga.MangaTapNavigation
import com.kitsugi.animelist.data.manga.MangaZoomStart
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaReaderSettingsSheet(
    readingMode: ReadingMode,
    colorFilter: ColorFilterType,
    fitMode: MangaFitMode,
    brightness: Float,
    options: MangaReaderOptions,
    onReadingModeChange: (ReadingMode) -> Unit,
    onColorFilterChange: (ColorFilterType) -> Unit,
    onFitModeChange: (MangaFitMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onOptionsChange: (MangaReaderOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    KitsugiSheetOrDialog(onDismiss = onDismiss, innerColumnScrollState = scrollState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.inline_reader_settings_82b7841), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            ReaderSection("Okuma Modu") {
                ReaderChoices(
                    items = listOf(
                        ReadingMode.RightToLeft to "Sağdan Sola",
                        ReadingMode.LeftToRight to "Soldan Sağa",
                        ReadingMode.Vertical to "Dikey",
                        ReadingMode.Webtoon to "Webtoon",
                    ),
                    selected = readingMode,
                    onSelected = onReadingModeChange,
                )
            }
            ReaderSection("Görsel Sığdırma") {
                ReaderChoices(
                    items = listOf(
                        MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                        MangaFitMode.FitWidth to "Genişlik",
                        MangaFitMode.FitHeight to "Yükseklik",
                    ),
                    selected = fitMode,
                    onSelected = onFitModeChange,
                )
            }
            ReaderSection("Renk Filtresi") {
                ReaderChoices(
                    items = listOf(
                        ColorFilterType.Normal to "Normal",
                        ColorFilterType.Grayscale to "Siyah-Beyaz",
                        ColorFilterType.Sepia to "Sepya",
                        ColorFilterType.Invert to "Ters Negatif",
                    ),
                    selected = colorFilter,
                    onSelected = onColorFilterChange,
                )
            }

            ReaderSection("Sayfa Düzeni") {
                ReaderSwitch("Kenarları Kırp", "Taramalardaki boş beyaz kenarları otomatik temizler", options.cropBorders) {
                    onOptionsChange(options.copy(cropBorders = it))
                }
                ReaderSwitch("Sayfa Numarası", null, options.showPageNumbers) {
                    onOptionsChange(options.copy(showPageNumbers = it))
                }
                ReaderSwitch("Çift Sayfa", "İki sayfayı yan yana gösterir; yatay LTR/RTL modlarında geçerlidir", options.doublePage) {
                    onOptionsChange(options.copy(doublePage = it))
                }
                ReaderStepper("Yan Boşluk", options.sidePaddingDp) {
                    onOptionsChange(options.copy(sidePaddingDp = it))
                }
                ReaderStepper("Sayfa Aralığı", options.pageGapDp) {
                    onOptionsChange(options.copy(pageGapDp = it))
                }
                ReaderSwitch("Geniş Sayfaları Çevir", "Yatay yayılımları 90° döndürür; sayfa sayısı değişmez", options.rotateWidePages) {
                    onOptionsChange(options.copy(rotateWidePages = it))
                }
                ReaderSwitch("Yatayda Çift Sayfa", "Yalnızca ekran yatayken çift sayfayı otomatik etkinleştirir", options.landscapeDoublePage) {
                    onOptionsChange(options.copy(landscapeDoublePage = it))
                }
                ReaderSlider("Sayfa Opaklığı", options.pageOpacity, 0.1f..1f, { onOptionsChange(options.copy(pageOpacity = it)) }) {
                    onOptionsChange(options.copy(pageOpacity = 1f))
                }
                ReaderSwitch("Kitap Efekti", "Kâğıt hissi için sıcak ton ve koyu zemin uygular", options.bookEffect) {
                    onOptionsChange(options.copy(bookEffect = it))
                }
                ReaderSwitch("Yüksek Renk Modu", "ARGB_8888 ile daha doğru renkler; biraz daha fazla bellek kullanır", options.highColorMode) {
                    onOptionsChange(options.copy(highColorMode = it))
                }
            }

            ReaderSection("Gezinme ve Dokunma") {
                ReaderChoices(
                    items = listOf(
                        MangaTapNavigation.Standard to "Standart",
                        MangaTapNavigation.Inverted to "Ters",
                        MangaTapNavigation.Disabled to "Kapalı",
                    ),
                    selected = options.tapNavigation,
                    onSelected = { onOptionsChange(options.copy(tapNavigation = it)) },
                )
                ReaderSwitch("Ses Tuşlarıyla Sayfa Çevir", "Ses kısma: sonraki; ses açma: önceki sayfa", options.volumeKeys) {
                    onOptionsChange(options.copy(volumeKeys = it))
                }
                ReaderSwitch("Ses Tuşlarını Ters Çevir", null, options.reverseVolumeKeys) {
                    onOptionsChange(options.copy(reverseVolumeKeys = it))
                }
            }

            ReaderSection("Yakınlaştırma Başlangıcı") {
                ReaderChoices(
                    items = listOf(
                        MangaZoomStart.Automatic to "Otomatik",
                        MangaZoomStart.Center to "Orta",
                        MangaZoomStart.Left to "Sol",
                        MangaZoomStart.Right to "Sağ",
                    ),
                    selected = options.zoomStart,
                    onSelected = { onOptionsChange(options.copy(zoomStart = it)) },
                )
                ReaderSwitch("Webtoon İçinde Yakınlaştırma", "Kapalıysa uzun şeritte kaydırma yakınlaştırma hareketlerinden etkilenmez", options.webtoonZoom) {
                    onOptionsChange(options.copy(webtoonZoom = it))
                }
            }

            ReaderSection("Okuma Akışı") {
                ReaderSwitch("Otomatik Webtoon", "Webtoon/Manhwa içeriğini ilk açılışta uzun şerit modunda seçer", options.autoWebtoon) {
                    onOptionsChange(options.copy(autoWebtoon = it))
                }
                ReaderSwitch("Okunmuş Bölümleri Atla", "Sonraki bölüme geçerken tamamlanmış bölümleri atlar", options.skipReadChapters) {
                    onOptionsChange(options.copy(skipReadChapters = it))
                }
            }

            ReaderSection("Okuyucu Parlaklığı") {
                ReaderSlider("Parlaklık", brightness, 0.1f..1f, onBrightnessChange, showBrightnessIcons = true) {
                    onBrightnessChange(1f)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReaderSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        Text(title, color = Color.White.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ReaderChoices(items: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF7C4DFF).copy(0.25f),
                    selectedLabelColor = Color(0xFFB49CFF),
                    containerColor = Color(0xFF2A2A40),
                    labelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun ReaderSwitch(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            if (description != null) Text(description, color = Color.White.copy(0.5f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF80CBC4), checkedThumbColor = Color.Black),
        )
    }
}

@Composable
private fun ReaderStepper(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("${value}dp", color = Color.White.copy(0.65f), fontSize = 12.sp)
        IconButton(onClick = { onValueChange((value - 1).coerceIn(0, 32)) }, modifier = Modifier.size(36.dp)) {
            Text("−", color = Color.White, fontSize = 18.sp)
        }
        IconButton(onClick = { onValueChange((value + 1).coerceIn(0, 32)) }, modifier = Modifier.size(36.dp)) {
            Text("+", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ReaderSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    showBrightnessIcons: Boolean = false,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", color = Color.White.copy(0.65f), fontSize = 12.sp)
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.RestartAlt, stringResource(R.string.slider_reset_to_default), tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBrightnessIcons) Icon(Icons.Default.BrightnessLow, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF80CBC4), inactiveTrackColor = Color(0xFF2A2A40), thumbColor = Color(0xFF80CBC4)),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (showBrightnessIcons) Icon(Icons.Default.BrightnessHigh, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
}
