package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Brave Tarzı Arama Motoru Seçici Hap Buton.
 * Arama çubuğunun içinde gösterilir ve kullanıcının tek dokunuşla
 * 7 farklı katalog arama motoru arasında geçiş yapmasını sağlar.
 */
@Composable
fun SourceEngineSelectorPill(
    selectedEngine: SearchSourceEngine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `true` ise motor adı gizlenir, yalnızca logo + ok kalır.
     *
     * Kullanıcı yazarken arama çubuğunda yer açmak için: arama butonu eklendiğinde
     * küçük ekranlarda metin alanı neredeyse sıfıra iniyordu. Ad gizlenince açılan
     * ~30-60 dp doğrudan metin alanına kalır. Motor adı zaten çubuğun altındaki
     * özet satırında ve seçici sayfada görünür.
     */
    compact: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KitsugiPlatformLogo(
            platformId = selectedEngine.id,
            size = 18.dp,
            fallbackTint = accentColor
        )
        if (!compact) {
            Text(
                text = selectedEngine.localizedLabel(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
            )
        }
        Icon(
            imageVector = Icons.ArrowDropDown,
            contentDescription = stringResource(R.string.search_engine_picker_accessibility),
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Motor seçim Bottom Sheet dialog'u.
 * 7 kaynak motorunu ve özelliklerini detaylı biçimde listeler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEnginePickerSheet(
    selectedEngine: SearchSourceEngine,
    onSelectEngine: (SearchSourceEngine) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.search_engine_picker_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                text = stringResource(R.string.search_engine_picker_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KitsugiColors.TextMuted
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            SearchSourceEngine.entries.forEach { engine ->
                val isSelected = engine == selectedEngine
                val description = stringResource(
                    when (engine) {
                        SearchSourceEngine.ALL -> R.string.search_engine_desc_all
                        SearchSourceEngine.ANILIST -> R.string.search_engine_desc_anilist
                        SearchSourceEngine.MAL -> R.string.search_engine_desc_mal
                        SearchSourceEngine.TMDB -> R.string.search_engine_desc_tmdb
                        SearchSourceEngine.SHIKIMORI -> R.string.search_engine_desc_shikimori
                        SearchSourceEngine.KITSU -> R.string.search_engine_desc_kitsu
                        SearchSourceEngine.SIMKL -> R.string.search_engine_desc_simkl
                        SearchSourceEngine.BANGUMI -> R.string.search_engine_desc_bangumi
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.12f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectEngine(engine)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(
                        platformId = engine.id,
                        size = 30.dp,
                        fallbackTint = accentColor,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = engine.localizedLabel(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                            )
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextMuted
                            )
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Check,
                            contentDescription = stringResource(R.string.secili),
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
