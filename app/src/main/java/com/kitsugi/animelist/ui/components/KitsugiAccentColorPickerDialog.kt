package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentAngle
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.accentBackgroundBrush
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.onAccentColor
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Kapsamlı vurgu rengi seçici:
 *
 * - **Palet**: Alt bardaki hazır temalar dışında KALAN tüm renk aileleri ve tüm ton
 *   varyantları (50→900) + siyah/beyaz.
 * - **Özel**: Tam HSV kare + ton (hue) şeridi ile sınırsız renk; isteğe bağlı hex girişi.
 * - **Gradyan**: İki renkli açılı lineer gradyan mekaniği (açı + yön ön ayarları).
 *
 * "+" butonunun yerini alır; hex yazmak zorunlu değildir.
 */
@Composable
fun KitsugiAccentColorPickerDialog(
    initialColor: Int,
    initialColor2: Int = 0,
    initialAngle: Int = 135,
    onDismissRequest: () -> Unit,
    onApply: (color1: Int, color2: Int, angle: Int) -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current

    // ── Durum ────────────────────────────────────────────────────────────
    val startColor = if (initialColor != 0) Color(initialColor) else Color(0xFFC8F4EF)
    var hsv1 by remember { mutableStateOf(colorToHsv(startColor)) }
    var hsv2 by remember {
        mutableStateOf(
            if (initialColor2 != 0) colorToHsv(Color(initialColor2))
            else floatArrayOf(((hsv1[0] + 140f) % 360f), hsv1[1], hsv1[2])
        )
    }
    var gradientEnabled by remember { mutableStateOf(initialColor2 != 0) }
    var angle by remember { mutableStateOf(if (initialColor2 != 0) initialAngle else 135) }
    var editTarget by remember { mutableStateOf(1) } // 1 = başlangıç, 2 = bitiş
    var selectedTab by remember { mutableStateOf(0) } // 0 = Palet, 1 = Özel, 2 = Gradyan

    val color1 = hsvToColor(hsv1)
    val color2 = hsvToColor(hsv2)
    val previewBrush = accentBackgroundBrush(
        color1,
        if (gradientEnabled) color2 else null,
        angle.toFloat()
    )
    val previewOnColor = onAccentColor(color1, if (gradientEnabled) color2 else null)

    val activeHsv = if (editTarget == 1) hsv1 else hsv2
    val setActiveHsv: (FloatArray) -> Unit = { newHsv ->
        if (editTarget == 1) hsv1 = newHsv else hsv2 = newHsv
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(
            LocalKitsugiAccent provides color1,
            LocalKitsugiAccent2 provides (if (gradientEnabled) color2 else null),
            LocalKitsugiAccentAngle provides angle.toFloat(),
            LocalKitsugiAccentBrush provides previewBrush,
            LocalKitsugiOnAccent provides previewOnColor
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.surface)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Başlık ─────────────────────────────────────────────
                Text(
                    text = stringResource(R.string.vurgu_rengini_sec),
                    color = KitsugiColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── Canlı önizleme ─────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(previewBrush)
                        .border(1.dp, KitsugiColors.border, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (gradientEnabled) {
                            "#${hexOf(color1)} → #${hexOf(color2)}   •   $angle°"
                        } else {
                            "#${hexOf(color1)}"
                        },
                        color = previewOnColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // ── Gradyan hedef seçimi (yalnız gradyan açıkken) ──────
                if (gradientEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.duzenlenen),
                            color = KitsugiColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        TargetSwatchButton(
                            label = stringResource(R.string.baslangic),
                            color = color1,
                            selected = editTarget == 1,
                            onClick = { editTarget = 1 }
                        )
                        TargetSwatchButton(
                            label = stringResource(R.string.bitis),
                            color = color2,
                            selected = editTarget == 2,
                            onClick = { editTarget = 2 }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Sekmeler ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PickerTabButton("Palet", selectedTab == 0, { selectedTab = 0 }, Modifier.weight(1f))
                    PickerTabButton("Özel", selectedTab == 1, { selectedTab = 1 }, Modifier.weight(1f))
                    PickerTabButton("Gradyan", selectedTab == 2, { selectedTab = 2 }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))

                // ── Sekme içerikleri ───────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    when (selectedTab) {
                        0 -> PaletteGrid(
                            selectedColor = if (editTarget == 1 || !gradientEnabled) color1 else color2,
                            onColorPicked = { picked -> setActiveHsv(colorToHsv(picked)) }
                        )
                        1 -> CustomHsvPicker(
                            hsv = activeHsv,
                            onHsvChange = setActiveHsv
                        )
                        2 -> GradientSettingsPanel(
                            gradientEnabled = gradientEnabled,
                            onGradientEnabledChange = { enabled ->
                                gradientEnabled = enabled
                                if (enabled) editTarget = 1
                            },
                            angle = angle,
                            onAngleChange = { angle = it },
                            color1 = color1,
                            color2 = color2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Alt butonlar ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.iptal), color = KitsugiColors.textSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onApply(
                                color1.toArgb(),
                                if (gradientEnabled) color2.toArgb() else 0,
                                angle
                            )
                        }
                    ) {
                        Text("Uygula", color = color1, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme: Palet — tüm renk aileleri ve ton varyantları
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaletteGrid(
    selectedColor: Color,
    onColorPicked: (Color) -> Unit
) {
    val allColors = remember { buildPaletteColors() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allColors) { c ->
            val selected = c.toArgb() == selectedColor.toArgb()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(
                        if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    )
                    .pointerInput(c) {
                        detectTapGestures { onColorPicked(c) }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(onAccentColor(c))
                    )
                }
            }
        }
    }
}

/**
 * Material tasarım paleti: 19 renk ailesi × 10 ton (50→900) + siyah/beyaz.
 * Alt bardaki 10 hazır temanın DIŞINDAKİ tüm renkler ve varyantları burada yer alır.
 */
private fun buildPaletteColors(): List<Color> {
    val families: List<List<Long>> = listOf(
        // Red
        listOf(0xFFFEBEE, 0xFFCDD2, 0xEF9A9A, 0xE57373, 0xEF5350, 0xF44336, 0xE53935, 0xD32F2F, 0xC62828, 0xB71C1C),
        // Pink
        listOf(0xFCE4EC, 0xF8BBD0, 0xF48FB1, 0xF06292, 0xEC407A, 0xE91E63, 0xD81B60, 0xC2185B, 0xAD1457, 0x880E4F),
        // Purple
        listOf(0xF3E5F5, 0xE1BEE7, 0xCE93D8, 0xBA68C8, 0xAB47BC, 0x9C27B0, 0x8E24AA, 0x7B1FA2, 0x6A1B9A, 0x4A148C),
        // Deep Purple
        listOf(0xEDE7F6, 0xD1C4E9, 0xB39DDB, 0x9575CD, 0x7E57C2, 0x673AB7, 0x5E35B1, 0x512DA8, 0x4527A0, 0x311B92),
        // Indigo
        listOf(0xE8EAF6, 0xC5CAE9, 0x9FA8DA, 0x7986CB, 0x5C6BC0, 0x3F51B5, 0x3949AB, 0x303F9F, 0x283593, 0x1A237E),
        // Blue
        listOf(0xE3F2FD, 0xBBDEFB, 0x90CAF9, 0x64B5F6, 0x42A5F5, 0x2196F3, 0x1E88E5, 0x1976D2, 0x1565C0, 0x0D47A1),
        // Light Blue
        listOf(0xE1F5FE, 0xB3E5FC, 0x81D4FA, 0x4FC3F7, 0x29B6F6, 0x03A9F4, 0x039BE5, 0x0288D1, 0x0277BD, 0x01579B),
        // Cyan
        listOf(0xE0F7FA, 0xB2EBF2, 0x80DEEA, 0x4DD0E1, 0x26C6DA, 0x00BCD4, 0x00ACC1, 0x0097A7, 0x00838F, 0x006064),
        // Teal
        listOf(0xE0F2F1, 0xB2DFDB, 0x80CBC4, 0x4DB6AC, 0x26A69A, 0x009688, 0x00897B, 0x00796B, 0x00695C, 0x004D40),
        // Green
        listOf(0xE8F5E9, 0xC8E6C9, 0xA5D6A7, 0x81C784, 0x66BB6A, 0x4CAF50, 0x43A047, 0x388E3C, 0x2E7D32, 0x1B5E20),
        // Light Green
        listOf(0xF1F8E9, 0xDCEDC8, 0xC5E1A5, 0xAED581, 0x9CCC65, 0x8BC34A, 0x7CB342, 0x689F38, 0x558B2F, 0x33691E),
        // Lime
        listOf(0xF9FBE7, 0xF0F4C3, 0xE6EE9C, 0xDCE775, 0xD4E157, 0xCDDC39, 0xC0CA33, 0xAFB42B, 0x9E9D24, 0x827717),
        // Yellow
        listOf(0xFFFDE7, 0xFFF9C4, 0xFFF59D, 0xFFF176, 0xFFEE58, 0xFFEB3B, 0xFDD835, 0xFBC02D, 0xF9A825, 0xF57F17),
        // Amber
        listOf(0xFFF8E1, 0xFFECB3, 0xFFE082, 0xFFD54F, 0xFFCA28, 0xFFB300, 0xFFA000, 0xFF8F00, 0xFF6F00, 0x996000),
        // Orange
        listOf(0xFFF3E0, 0xFFE0B2, 0xFFCC80, 0xFFB74D, 0xFFA726, 0xFF9800, 0xFB8C00, 0xF57C00, 0xEF6C00, 0xE65100),
        // Deep Orange
        listOf(0xFBE9E7, 0xFFCCBC, 0xFFAB91, 0xFF8A65, 0xFF7043, 0xFF5722, 0xF4511E, 0xE64A19, 0xD84315, 0xBF360C),
        // Brown
        listOf(0xEFEBE9, 0xD7CCC8, 0xBCAAA4, 0xA1887F, 0x8D6E63, 0x795548, 0x6D4C41, 0x5D4037, 0x4E342E, 0x3E2723),
        // Blue Grey
        listOf(0xECEFF1, 0xCFD8DC, 0xB0BEC5, 0x90A4AE, 0x78909C, 0x607D8B, 0x546E7A, 0x455A64, 0x37474F, 0x263238),
        // Grey
        listOf(0xFAFAFA, 0xF5F5F5, 0xEEEEEE, 0xE0E0E0, 0xBDBDBD, 0x9E9E9E, 0x757575, 0x616161, 0x424242, 0x212121)
    )
    val list = ArrayList<Color>(families.size * 10 + 2)
    list.add(Color.Black)
    list.add(Color.White)
    families.forEach { family ->
        family.forEach { rgb -> list.add(Color(0xFF000000 or rgb)) }
    }
    return list
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme: Özel — tam HSV kare + ton şeridi + hex
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomHsvPicker(
    hsv: FloatArray,
    onHsvChange: (FloatArray) -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    var svSize by remember { mutableStateOf(IntSize.Zero) }
    var hueSize by remember { mutableStateOf(IntSize.Zero) }
    // Sürükleme geri çağrıları her zaman en güncel HSV'yi görsün
    val currentHsv by rememberUpdatedState(hsv)
    val currentColor = hsvToColor(currentHsv)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Doygunluk–Değer karesi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .onSizeChanged { svSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (svSize.width > 0 && svSize.height > 0) {
                            val s = (offset.x / svSize.width).coerceIn(0f, 1f)
                            val v = 1f - (offset.y / svSize.height).coerceIn(0f, 1f)
                            onHsvChange(floatArrayOf(currentHsv[0], s, v))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        if (svSize.width > 0 && svSize.height > 0) {
                            val s = (change.position.x / svSize.width).coerceIn(0f, 1f)
                            val v = 1f - (change.position.y / svSize.height).coerceIn(0f, 1f)
                            onHsvChange(floatArrayOf(currentHsv[0], s, v))
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(currentHsv[0], 1f, 1f)))
                drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xFF000000))))
                val x = currentHsv[1] * size.width
                val y = (1f - currentHsv[2]) * size.height
                drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x, y))
                drawCircle(
                    Color.Black,
                    radius = 11.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Ton (hue) şeridi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .onSizeChanged { hueSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (hueSize.width > 0) {
                            val h = (offset.x / hueSize.width).coerceIn(0f, 1f) * 360f
                            onHsvChange(floatArrayOf(h, currentHsv[1], currentHsv[2]))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        if (hueSize.width > 0) {
                            val h = (change.position.x / hueSize.width).coerceIn(0f, 1f) * 360f
                            onHsvChange(floatArrayOf(h, currentHsv[1], currentHsv[2]))
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hueColors = (0..24).map { i ->
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 15f, 1f, 1f)))
                }
                drawRect(Brush.horizontalGradient(hueColors))
                val x = (currentHsv[0] / 360f).coerceIn(0f, 1f) * size.width
                drawLine(
                    color = Color.White,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Seçili renk + hex (isteğe bağlı elle giriş)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(1.5.dp, KitsugiColors.border, CircleShape)
            )
            HexField(
                color = currentColor,
                onHexApplied = { parsed -> onHsvChange(colorToHsv(parsed)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HexField(
    color: Color,
    onHexApplied: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val KitsugiColors = LocalKitsugiColors.current
    var text by remember(color.toArgb()) {
        mutableStateOf("#" + hexOf(color))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            val parsed = parseHex(new)
            if (parsed != null) onHexApplied(parsed)
        },
        modifier = modifier,
        singleLine = true,
        label = { Text(stringResource(R.string.hex_kodu_istege_bagli), color = KitsugiColors.textSecondary, fontSize = 12.sp) },
        placeholder = { Text("#FF66CC", color = KitsugiColors.textMuted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = KitsugiColors.textPrimary,
            unfocusedTextColor = KitsugiColors.textPrimary,
            cursorColor = color,
            focusedBorderColor = color,
            unfocusedBorderColor = KitsugiColors.border
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme: Gradyan — iki renk + açı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GradientSettingsPanel(
    gradientEnabled: Boolean,
    onGradientEnabledChange: (Boolean) -> Unit,
    angle: Int,
    onAngleChange: (Int) -> Unit,
    color1: Color,
    color2: Color
) {
    val KitsugiColors = LocalKitsugiColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .kitsugiVerticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gradyan Renk Kullan",
                    color = KitsugiColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.vurgu_arka_planlari_secilen_iki_renk_arasinda_akar),
                    color = KitsugiColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = gradientEnabled,
                onCheckedChange = onGradientEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KitsugiColors.background,
                    checkedTrackColor = color1
                )
            )
        }

        if (gradientEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            // Gradyan şerit önizleme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentBackgroundBrush(color1, color2, angle.toFloat()))
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                stringResource(R.string.yon_aci),
                color = KitsugiColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Yön ön ayarları
            val presets = listOf(
                "→" to 0, "↘" to 45, "↓" to 90, "↙" to 135,
                "←" to 180, "↖" to 225, "↑" to 270, "↗" to 315
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (arrow, deg) ->
                    val selected = angle == deg
                    val presetBrush = LocalKitsugiAccentBrush.current
                    val presetOnColor = LocalKitsugiOnAccent.current
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (selected) Modifier.background(presetBrush)
                                else Modifier.background(KitsugiColors.surfaceSoft)
                            )
                            .pointerInput(deg) {
                                detectTapGestures { onAngleChange(deg) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            arrow,
                            color = if (selected) presetOnColor else KitsugiColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Slider(
                    value = angle.toFloat(),
                    onValueChange = { onAngleChange(it.roundToInt().coerceIn(0, 359)) },
                    valueRange = 0f..359f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = color1,
                        activeTrackColor = color1,
                        inactiveTrackColor = KitsugiColors.surfaceSoft
                    )
                )
                Text(
                    "$angle°",
                    color = KitsugiColors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.ipucu_baslangic_ve_bitis_renklerini_duzenlemek_icin_palet_veya_o),
                color = KitsugiColors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.gradyani_actiginizda_iki_renkli_acili_bir_vurgu_arka_plani_olust),
                color = KitsugiColors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ortak küçük bileşenler & yardımcılar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TargetSwatchButton(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    val activeBrush = LocalKitsugiAccentBrush.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) KitsugiColors.surfaceSoft else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(width = 2.dp, brush = activeBrush, shape = RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(width = 1.dp, color = KitsugiColors.border, shape = RoundedCornerShape(12.dp))
                }
            )
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, KitsugiColors.border, CircleShape)
        )
        Text(
            label,
            color = if (selected) LocalKitsugiAccent.current else KitsugiColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PickerTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val KitsugiColors = LocalKitsugiColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) KitsugiColors.surfaceSoft else Color.Transparent)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) LocalKitsugiAccent.current else KitsugiColors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .then(
                    if (selected) Modifier.background(LocalKitsugiAccentBrush.current)
                    else Modifier.background(Color.Transparent)
                )
        )
    }
}

// ── HSV / hex yardımcıları ───────────────────────────────────────────────────

private fun colorToHsv(color: Color): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), out)
    return out
}

private fun hsvToColor(hsv: FloatArray): Color =
    Color(android.graphics.Color.HSVToColor(hsv))

private fun hexOf(color: Color): String =
    String.format("%06X", 0xFFFFFF and color.toArgb())

private fun parseHex(input: String): Color? {
    val hex = input.trim().removePrefix("#")
    return runCatching {
        when (hex.length) {
            6 -> Color(0xFF000000 or hex.toLong(16))
            8 -> Color(hex.toLong(16))
            else -> null
        }
    }.getOrNull()
}
