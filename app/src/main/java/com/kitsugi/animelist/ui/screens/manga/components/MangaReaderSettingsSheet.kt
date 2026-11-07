package com.kitsugi.animelist.ui.screens.manga.components

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
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPageBackground
import com.kitsugi.animelist.data.manga.MangaBarStyle
import com.kitsugi.animelist.data.manga.MangaCropProfile
import com.kitsugi.animelist.data.manga.MangaFlashColor
import com.kitsugi.animelist.data.manga.MangaLongTapAction
import com.kitsugi.animelist.data.manga.MangaZoomStart
import com.kitsugi.animelist.data.manga.MangaPageAnimation
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.data.manga.MangaScrollHideSensitivity
import com.kitsugi.animelist.data.manga.MangaTapZone
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SliderDefaults

/**
 * Manga okuyucunun kendi ayar sayfası.
 *
 * Not: Bu panel **yalnızca okuyucuya özel** ayarları içerir. Uygulama geneli
 * tercihler (Ayarlar → Manga Okuyucu) aynı [MangaReaderOptions] modeline yazar;
 * böylece iki yerde farklı seçenek listesi oluşmaz.
 *
 * Tüm çipler/satırlar ortak `label` fonksiyonlarını kullanır; yeni bir filtre
 * eklemek tek satırlık bir değişikliktir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaReaderSettingsSheet(
    options: MangaReaderOptions,
    onOptionsChange: ((MangaReaderOptions) -> MangaReaderOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipsContainer = Color(0xFF2A2A40)

    @Composable
    fun SectionTitle(text: String) {
        Text(
            text = text,
            color = Color.White.copy(0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
    }

    @Composable
    fun <T> ChipRow(
        values: List<T>,
        selected: T,
        label: (T) -> String,
        onSelect: (T) -> Unit
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value), fontSize = 11.sp, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF7C4DFF).copy(0.25f),
                        selectedLabelColor = Color(0xFFB39DFF),
                        containerColor = chipsContainer,
                        labelColor = Color.White
                    )
                )
            }
        }
    }

    @Composable
    fun ToggleRow(
        title: String,
        description: String?,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Color.White, fontSize = 13.sp)
                if (description != null) {
                    Text(description, color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    fun SliderRow(
        title: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        displayValue: String,
        defaultValue: Float,
        onChange: (Float) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    text = displayValue,
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp
                )
                IconButton(onClick = { onChange(defaultValue) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.RestartAlt,
                        contentDescription = stringResource(R.string.slider_reset_to_default),
                        tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF7C4DFF),
                    inactiveTrackColor = chipsContainer,
                    thumbColor = Color(0xFF7C4DFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun StepRow(
        title: String,
        value: Int,
        range: IntRange,
        step: Int,
        suffix: String,
        onChange: (Int) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${value}$suffix", color = Color.White.copy(0.6f), fontSize = 12.sp)
            IconButton(
                onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
                modifier = Modifier.size(36.dp)
            ) { Text("−", color = Color.White, fontSize = 18.sp) }
            IconButton(
                onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
                modifier = Modifier.size(36.dp)
            ) { Text("+", color = Color.White, fontSize = 18.sp) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.inline_reader_settings_82b7841),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ── Okuma yönü ──────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.manga_reading_direction))
        ChipRow(
            values = listOf(ReadingMode.RightToLeft, ReadingMode.LeftToRight, ReadingMode.Vertical, ReadingMode.Webtoon),
            selected = options.readingMode,
            label = { it.label() },
            onSelect = { mode -> onOptionsChange { it.copy(readingMode = mode) } }
        )
        Spacer(Modifier.height(12.dp))

        // ── Görsel sığdırma ─────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.inline_image_fit_mode_3eed46c))
        ChipRow(
            values = MangaFitMode.cycleOrder,
            selected = options.fitMode,
            label = { it.label() },
            onSelect = { fit -> onOptionsChange { it.copy(fitMode = fit) } }
        )
        Spacer(Modifier.height(12.dp))

        // ── Renk filtresi ───────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.manga_color_filter))
        ChipRow(
            values = ColorFilterType.cycleOrder,
            selected = options.colorFilter,
            label = { it.label() },
            onSelect = { filter -> onOptionsChange { it.copy(colorFilter = filter) } }
        )
        Spacer(Modifier.height(12.dp))

        // ── Zemin rengi ─────────────────────────────────────────────────────
        SectionTitle(stringResource(R.string.manga_reader_background))
        ChipRow(
            values = MangaPageBackground.cycleOrder,
            selected = options.background,
            label = { it.label() },
            onSelect = { bg -> onOptionsChange { it.copy(background = bg) } }
        )
        Spacer(Modifier.height(12.dp))

        // ── Görüntü kalibrasyonu ────────────────────────────────────────────
        SliderRow(
            title = stringResource(R.string.inline_reader_brightness_e5e9f7a),
            value = options.brightness,
            range = 0.1f..1f,
            displayValue = "${(options.brightness * 100).toInt()}%",
            defaultValue = 1f,
            onChange = { v -> onOptionsChange { it.copy(brightness = v) } }
        )
        SliderRow(
            title = stringResource(R.string.manga_reader_contrast),
            value = (options.contrast - 0.5f) / 1.5f,
            range = 0f..1f,
            displayValue = "${(options.contrast * 100).toInt()}%",
            defaultValue = 1f / 3f,
            onChange = { v -> onOptionsChange { it.copy(contrast = 0.5f + v * 1.5f) } }
        )
        SliderRow(
            title = stringResource(R.string.manga_reader_saturation),
            value = options.saturation / 2f,
            range = 0f..1f,
            displayValue = "${(options.saturation * 100).toInt()}%",
            defaultValue = 0.5f,
            onChange = { v -> onOptionsChange { it.copy(saturation = v * 2f) } }
        )
        SliderRow(
            title = stringResource(R.string.manga_reader_blue_light),
            value = options.blueLight,
            range = 0f..1f,
            displayValue = "${(options.blueLight * 100).toInt()}%",
            defaultValue = 0f,
            onChange = { v -> onOptionsChange { it.copy(blueLight = v) } }
        )

        // Kalibrasyon var mı? Varsa tek tuşla sıfırlama (4 slider'ı tek tek
        // elle geri almaya gerek yok — tek "sıfırla" yolu).
        if (options.hasCalibration) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.manga_reader_reset_calibration),
                    color = Color.White.copy(0.75f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onOptionsChange { it.resetCalibration() } },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.RestartAlt,
                        contentDescription = stringResource(R.string.manga_reader_reset_calibration),
                        tint = Color(0xFFB39DFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── Sayfa düzeni ────────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_page_layout))
        // Kenar kırpma: TEK kontrol (aç/kapa + profil çipleri aynı alanı yazar).
        ToggleRow(
            title = stringResource(R.string.manga_reader_crop_borders),
            description = stringResource(R.string.manga_reader_crop_borders_desc),
            checked = options.cropEnabled,
            onCheckedChange = { v ->
                onOptionsChange {
                    it.copy(cropProfile = if (v) MangaCropProfile.White else MangaCropProfile.Off)
                }
            }
        )
        if (options.cropEnabled) {
            ChipRow(
                values = MangaCropProfile.cycleOrder,
                selected = options.cropProfile,
                label = { it.label() },
                onSelect = { profile -> onOptionsChange { it.copy(cropProfile = profile) } }
            )
            SliderRow(
                title = stringResource(R.string.manga_reader_crop_tolerance),
                value = options.cropTolerance.toFloat(),
                // Model 0..60 aralığına kırpıyor; slider aynı aralığı göstermeli
                // (aksi halde 40 üstü değerler slider'da "taşmış" görünüyordu).
                range = 0f..60f,
                displayValue = "${options.cropTolerance}",
                defaultValue = 18f,
                onChange = { v -> onOptionsChange { it.copy(cropTolerance = v.toInt()) } }
            )
        }
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_page_number),
            description = null,
            checked = options.showPageNumber,
            onCheckedChange = { v -> onOptionsChange { it.copy(showPageNumber = v) } }
        )
        // Çift sayfa yalnızca pager modlarında anlamlıdır.
        if (options.isPaged) {
            ToggleRow(
                title = stringResource(R.string.manga_reader_dual_page),
                description = stringResource(R.string.manga_reader_dual_page_desc),
                checked = options.dualPage,
                onCheckedChange = { v -> onOptionsChange { it.copy(dualPage = v) } }
            )
            // Çift sayfa açıkken hangi sayfaların eşleşeceğini belirler.
            // Varsayılanlar KAPALI → mevcut davranış korunur.
            if (options.dualPageActive()) {
                ToggleRow(
                    title = stringResource(R.string.manga_reader_first_page_single),
                    description = stringResource(R.string.manga_reader_first_page_single_desc),
                    checked = options.firstPageSingle,
                    onCheckedChange = { v -> onOptionsChange { it.copy(firstPageSingle = v) } }
                )
                ToggleRow(
                    title = stringResource(R.string.manga_reader_dual_invert),
                    description = stringResource(R.string.manga_reader_dual_invert_desc),
                    checked = options.dualPageInvert,
                    onCheckedChange = { v -> onOptionsChange { it.copy(dualPageInvert = v) } }
                )
            }
        }
        StepRow(
            title = stringResource(R.string.manga_reader_side_padding),
            value = options.sidePadding,
            range = 0..80,
            step = 4,
            suffix = "dp",
            onChange = { v -> onOptionsChange { it.copy(sidePadding = v) } }
        )
        StepRow(
            title = stringResource(R.string.manga_reader_page_gap),
            value = options.pageGap,
            range = 0..40,
            step = 2,
            suffix = "dp",
            onChange = { v -> onOptionsChange { it.copy(pageGap = v) } }
        )
        // Geniş sayfa çevirme (Mihon "Rotate wide pages"): sayfa sayısı değişmez.
        ToggleRow(
            title = stringResource(R.string.manga_reader_rotate_wide),
            description = stringResource(R.string.manga_reader_rotate_wide_desc),
            checked = options.rotateWidePages,
            onCheckedChange = { v -> onOptionsChange { it.copy(rotateWidePages = v) } }
        )
        if (options.rotateWidePages) {
            ToggleRow(
                title = stringResource(R.string.manga_reader_rotate_invert),
                description = null,
                checked = options.rotateWidePagesInvert,
                onCheckedChange = { v -> onOptionsChange { it.copy(rotateWidePagesInvert = v) } }
            )
            SliderRow(
                title = stringResource(R.string.manga_reader_wide_threshold),
                value = options.widePageThreshold,
                range = 1f..2f,
                displayValue = String.format(java.util.Locale.US, "%.2f", options.widePageThreshold),
                defaultValue = 1.2f,
                onChange = { v -> onOptionsChange { it.copy(widePageThreshold = v) } }
            )
        }
        if (options.isPaged) {
            ToggleRow(
                title = stringResource(R.string.manga_reader_dual_landscape),
                description = stringResource(R.string.manga_reader_dual_landscape_desc),
                checked = options.dualPageOnLandscape,
                onCheckedChange = { v -> onOptionsChange { it.copy(dualPageOnLandscape = v) } }
            )
        }
        SliderRow(
            title = stringResource(R.string.manga_reader_page_opacity),
            value = options.pageOpacity,
            range = 0.2f..1f,
            displayValue = "${(options.pageOpacity * 100).toInt()}%",
            defaultValue = 1f,
            onChange = { v -> onOptionsChange { it.copy(pageOpacity = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_book_effect),
            description = stringResource(R.string.manga_reader_book_effect_desc),
            checked = options.bookEffect,
            onCheckedChange = { v -> onOptionsChange { it.copy(bookEffect = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_high_res_colors),
            description = stringResource(R.string.manga_reader_high_res_colors_desc),
            checked = options.highResColors,
            onCheckedChange = { v -> onOptionsChange { it.copy(highResColors = v) } }
        )

        // ── Dokunma ve gezinme ──────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_navigation))
        ChipRow(
            values = MangaTapZone.entries.toList(),
            selected = options.tapZone,
            label = { it.label() },
            onSelect = { zone -> onOptionsChange { it.copy(tapZone = zone) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_volume_keys),
            description = stringResource(R.string.manga_reader_volume_keys_desc),
            checked = options.volumeKeys,
            onCheckedChange = { v -> onOptionsChange { it.copy(volumeKeys = v) } }
        )
        if (options.volumeKeys) {
            ToggleRow(
                title = stringResource(R.string.manga_reader_invert_volume_keys),
                description = null,
                checked = options.invertVolumeKeys,
                onCheckedChange = { v -> onOptionsChange { it.copy(invertVolumeKeys = v) } }
            )
        }
        // Mihon `ZoomStartType`: yakınlaştırılmış sayfanın hizası
        SectionTitle(stringResource(R.string.manga_reader_zoom_start))
        ChipRow(
            values = MangaZoomStart.cycleOrder,
            selected = options.zoomStart,
            label = { it.label() },
            onSelect = { s -> onOptionsChange { it.copy(zoomStart = s) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_webtoon_zoom),
            description = stringResource(R.string.manga_reader_webtoon_zoom_desc),
            checked = options.webtoonZoom,
            onCheckedChange = { v -> onOptionsChange { it.copy(webtoonZoom = v) } }
        )

        // ── Okuma akışı ─────────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_reading_flow))
        ToggleRow(
            title = stringResource(R.string.manga_reader_auto_webtoon),
            description = stringResource(R.string.manga_reader_auto_webtoon_desc),
            checked = options.autoWebtoon,
            onCheckedChange = { v -> onOptionsChange { it.copy(autoWebtoon = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_skip_read),
            description = stringResource(R.string.manga_reader_skip_read_desc),
            checked = options.skipReadChapters,
            onCheckedChange = { v -> onOptionsChange { it.copy(skipReadChapters = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_chapter_transition),
            description = null,
            checked = options.chapterTransitionCard,
            onCheckedChange = { v -> onOptionsChange { it.copy(chapterTransitionCard = v) } }
        )
        // 0 = kapalı. Bu tek değer hem geçişi hem geri sayım kartını yönetir
        // (ikinci bir "sayacı göster" anahtarı bilinçli olarak YOK).
        StepRow(
            title = stringResource(R.string.manga_reader_auto_next),
            value = options.autoNextChapterSeconds,
            range = 0..60,
            step = 5,
            suffix = "s",
            onChange = { v -> onOptionsChange { it.copy(autoNextChapterSeconds = v) } }
        )
        StepRow(
            title = stringResource(R.string.manga_reader_auto_scroll_speed),
            value = options.autoScrollSpeed,
            range = 0..600,
            step = 20,
            suffix = "dp/s",
            onChange = { v -> onOptionsChange { it.copy(autoScrollSpeed = v) } }
        )

        // ── Ekran ───────────────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_display))
        ToggleRow(
            title = stringResource(R.string.manga_reader_keep_screen_on),
            description = null,
            checked = options.keepScreenOn,
            onCheckedChange = { v -> onOptionsChange { it.copy(keepScreenOn = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_immersive),
            description = stringResource(R.string.manga_reader_immersive_desc),
            checked = options.immersive,
            onCheckedChange = { v -> onOptionsChange { it.copy(immersive = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_eink_flash),
            description = stringResource(R.string.manga_reader_eink_flash_desc),
            checked = options.einkFlash,
            onCheckedChange = { v -> onOptionsChange { it.copy(einkFlash = v) } }
        )
        if (options.einkFlash) {
            // Mihon: flaş rengi / süresi / kaç sayfada bir
            ChipRow(
                values = MangaFlashColor.cycleOrder,
                selected = options.flashColor,
                label = { it.label() },
                onSelect = { c -> onOptionsChange { it.copy(flashColor = c) } }
            )
            SliderRow(
                title = stringResource(R.string.manga_reader_flash_duration),
                value = options.flashDurationMs.toFloat(),
                range = 30f..300f,
                displayValue = "${options.flashDurationMs}ms",
                defaultValue = 90f,
                onChange = { v -> onOptionsChange { it.copy(flashDurationMs = v.toInt()) } }
            )
            StepRow(
                title = stringResource(R.string.manga_reader_flash_interval),
                value = options.flashInterval,
                range = 1..10,
                step = 1,
                suffix = stringResource(R.string.manga_reader_flash_interval_suffix),
                onChange = { v -> onOptionsChange { it.copy(flashInterval = v) } }
            )
        }
        ToggleRow(
            title = stringResource(R.string.manga_reader_long_tap),
            description = stringResource(R.string.manga_reader_long_tap_desc),
            checked = options.longTapActions,
            onCheckedChange = { v -> onOptionsChange { it.copy(longTapActions = v) } }
        )
        if (options.longTapActions) {
            // Mihon "Long tap action": menü yerine doğrudan eylem
            ChipRow(
                values = MangaLongTapAction.cycleOrder,
                selected = options.longTapAction,
                label = { it.label() },
                onSelect = { action -> onOptionsChange { it.copy(longTapAction = action) } }
            )
        }

        // ── Animasyon ───────────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_animation))
        ChipRow(
            values = MangaPageAnimation.cycleOrder,
            selected = options.pageAnimation,
            label = { it.label() },
            onSelect = { anim -> onOptionsChange { it.copy(pageAnimation = anim) } }
        )
        Spacer(Modifier.height(8.dp))
        SectionTitle(stringResource(R.string.manga_reader_scroll_hide))
        ChipRow(
            values = MangaScrollHideSensitivity.cycleOrder,
            selected = options.autoHideMenuOnScroll,
            label = { it.label() },
            onSelect = { s -> onOptionsChange { it.copy(autoHideMenuOnScroll = s) } }
        )

        // ── Bar yerleşimi ────────────────────────────────────────────────────
        // Hızlı okurken bar ekranı kaplamasın: Full → Compact → Minimal.
        // Full varsayılan olduğu için mevcut düzen birebir korunur.
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_bar_style))
        ChipRow(
            values = MangaBarStyle.cycleOrder,
            selected = options.barStyle,
            label = { it.label() },
            onSelect = { style -> onOptionsChange { it.copy(barStyle = style) } }
        )
        Text(
            text = stringResource(R.string.manga_reader_bar_style_desc),
            color = Color.White.copy(0.45f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        // ── Okuyucu barları (Kotatsu "reader controls") ──────────────────────
        // Tek bölüm: hangi düğmenin alt barda görüneceği burada belirlenir.
        // Varsayılanlar mevcut düzeni birebir korur (slider/bölüm düğmeleri açık,
        // ızgara/yer imi kapalı), yani kullanıcı hiçbir şey yapmazsa görünüm aynı.
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.manga_reader_controls))
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_slider),
            description = null,
            checked = options.showSlider,
            onCheckedChange = { v -> onOptionsChange { it.copy(showSlider = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_chapter_buttons),
            description = null,
            checked = options.showChapterButtons,
            onCheckedChange = { v -> onOptionsChange { it.copy(showChapterButtons = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_pages_sheet),
            description = null,
            checked = options.showPagesSheet,
            onCheckedChange = { v -> onOptionsChange { it.copy(showPagesSheet = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_scroll_timer),
            description = null,
            checked = options.showScrollTimer,
            onCheckedChange = { v -> onOptionsChange { it.copy(showScrollTimer = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_show_bookmark),
            description = null,
            checked = options.showBookmarkButton,
            onCheckedChange = { v -> onOptionsChange { it.copy(showBookmarkButton = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_info_bar),
            description = stringResource(R.string.manga_reader_info_bar_desc),
            checked = options.infoBar,
            onCheckedChange = { v -> onOptionsChange { it.copy(infoBar = v) } }
        )
        ToggleRow(
            title = stringResource(R.string.manga_reader_zoom_buttons),
            description = stringResource(R.string.manga_reader_zoom_buttons_desc),
            checked = options.zoomButtons,
            onCheckedChange = { v -> onOptionsChange { it.copy(zoomButtons = v) } }
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Etiketler (tek yerden) ──────────────────────────────────────────────────

fun ReadingMode.label(): String = when (this) {
    ReadingMode.RightToLeft -> "Sağdan Sola"
    ReadingMode.LeftToRight -> "Soldan Sağa"
    ReadingMode.Vertical -> "Dikey"
    ReadingMode.Webtoon -> "Webtoon"
}

fun MangaFitMode.label(): String = when (this) {
    MangaFitMode.FitScreen -> "Ekran"
    MangaFitMode.FitWidth -> "Genişlik"
    MangaFitMode.FitHeight -> "Yükseklik"
    MangaFitMode.Crop -> "Kırp"
    MangaFitMode.Original -> "Orijinal"
    MangaFitMode.Stretch -> "Uzat"
    MangaFitMode.SmartFit -> "Akıllı"
}

fun ColorFilterType.label(): String = when (this) {
    ColorFilterType.Normal -> "Normal"
    ColorFilterType.Grayscale -> "Siyah-Beyaz"
    ColorFilterType.Sepia -> "Sepya"
    ColorFilterType.Invert -> "Ters"
    ColorFilterType.Warm -> "Sıcak"
    ColorFilterType.Cool -> "Soğuk"
    ColorFilterType.HighContrast -> "Yüksek Kontrast"
    ColorFilterType.Soft -> "Yumuşak"
}

fun MangaPageBackground.label(): String = when (this) {
    MangaPageBackground.Black -> "Siyah"
    MangaPageBackground.DarkGray -> "Koyu Gri"
    MangaPageBackground.White -> "Beyaz"
    MangaPageBackground.Sepia -> "Sepya"
    MangaPageBackground.Smart -> "Otomatik"
}

fun MangaTapZone.label(): String = when (this) {
    MangaTapZone.Default -> "Standart"
    MangaTapZone.Inverted -> "Ters"
    MangaTapZone.Disabled -> "Kapalı"
}

fun MangaCropProfile.label(): String = when (this) {
    MangaCropProfile.Off -> "Kapalı"
    MangaCropProfile.White -> "Beyaz Kenar"
    MangaCropProfile.Black -> "Siyah Kenar"
    MangaCropProfile.Auto -> "Otomatik"
}

fun MangaPageAnimation.label(): String = when (this) {
    MangaPageAnimation.None -> "Yok"
    MangaPageAnimation.Default -> "Normal"
    MangaPageAnimation.Fast -> "Hızlı"
}

fun MangaScrollHideSensitivity.label(): String = when (this) {
    MangaScrollHideSensitivity.Off -> "Kapalı"
    MangaScrollHideSensitivity.Low -> "Az Hassas"
    MangaScrollHideSensitivity.High -> "Çok Hassas"
}

fun MangaLongTapAction.label(): String = when (this) {
    MangaLongTapAction.Menu -> "Menü"
    MangaLongTapAction.SavePage -> "Sayfayı Kaydet"
    MangaLongTapAction.SharePage -> "Sayfayı Paylaş"
    MangaLongTapAction.CopyLink -> "Bağlantıyı Kopyala"
}

fun MangaZoomStart.label(): String = when (this) {
    MangaZoomStart.Automatic -> "Otomatik"
    MangaZoomStart.Left -> "Sol"
    MangaZoomStart.Right -> "Sağ"
    MangaZoomStart.Center -> "Orta"
}

fun MangaFlashColor.label(): String = when (this) {
    MangaFlashColor.White -> "Beyaz"
    MangaFlashColor.Black -> "Siyah"
    MangaFlashColor.WhiteBlack -> "Beyaz-Siyah"
}

fun MangaBarStyle.label(): String = when (this) {
    MangaBarStyle.Full -> "Tam"
    MangaBarStyle.Compact -> "Kompakt"
    MangaBarStyle.Minimal -> "Minimal"
}
