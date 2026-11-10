package com.kitsugi.animelist.ui.screens.home.components

import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import kotlin.math.ceil

/**
 * V2-D03 – GridHomeContent
 *
 * Ana ekran grid görünümü — 2-3 sütunlu lazy vertical grid.
 * Her kart için KitsugiExploreMediaCard kullanılır.
 *
 * GÖMÜLEBİLİR GÜVENLİK: Bu bileşen bir LazyColumn/LazyRow `item { }` içine
 * gömülebilir. LazyList, item'larını maxHeight = ∞ ile ölçer; dikey kaydırılabilir
 * bir LazyVerticalGrid ∞ maxHeight ile ölçülürse Compose şu istisnayı atar:
 *   "Vertically scrollable component was measured with an infinity maximum height
 *    constraints"
 * `Modifier.wrapContentHeight()` bu durumda ∞'ü SINIRLAMAZ (yalnızca minHeight'ı
 * 0 yapar) — çökme aynen devam eder. Bu yüzden ızgara yüksekliği satır sayısından
 * hesaplanıp kesin bir `height(...)` olarak verilir (bkz.
 * KOK_NEDEN_RAPORU_LAZY_GRID_SONSUZ_YUKSEKLIK_2026-10-22.md).
 *
 * @param title         Bölüm başlığı
 * @param results       Gösterilecek Jikan/TMDB medya listesi
 * @param columns       Sütun sayısı (2 = telefon, 3 = tablet/yatay)
 * @param isLoading     Yükleme durumu
 * @param onItemClick   Kart tıklama geri bildirimi
 * @param alreadyInList Kartın listede işaretli gösterilip gösterilmediğini belirler
 * @param modifier      Dışarıdan gelen modifier
 */
@Composable
fun GridHomeContent(
    title: String,
    results: List<JikanSearchResult>,
    isLoading: Boolean = false,
    columns: Int = 2,
    alreadyInList: (JikanSearchResult) -> Boolean = { false },
    onItemClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when {
            isLoading && results.isEmpty() -> {
                // İskelet kart: aspectRatio(0.67) yalnızca poster bloğudur (metin yok).
                val shimmerHeight = rememberGridHomeEmbedHeight(columns = columns, rowCount = 3) { cellW ->
                    cellW / 0.67f
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(shimmerHeight)
                ) {
                    items(List(columns * 3) { it.toString() }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = KitsugiColors.SurfaceSoft,
                            modifier = Modifier
                                .aspectRatio(0.67f)
                                .fillMaxWidth()
                        ) {}
                    }
                }
            }

            results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.gosterilecek_icerik_yok),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KitsugiColors.TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            else -> {
                val rowCount = ceil(results.size / columns.toFloat()).toInt().coerceAtLeast(1)
                val gridHeight = rememberGridHomeEmbedHeight(columns = columns, rowCount = rowCount, ::gridHomeCardHeightDp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                ) {
                    itemsIndexed(results, key = { idx, item -> "${idx}_${item.source}_${item.type}_${item.malId}_${item.tmdbId ?: 0}" }) { _, item ->
                        KitsugiExploreMediaCard(
                            result = item,
                            alreadyInList = alreadyInList(item),
                            onClick = { onItemClick(item) },
                            forceVertical = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Izgara kart yükseklığı — [KitsugiExploreMediaCard] `forceVertical` düzeni:
 * 14.dp iç dolgu (üst+alt) + 2:3 poster + 182.dp sabit metin bloğu.
 * Metin bloğu sabit yükseklikli olduğu için fontScale'den etkilenmez.
 */
private fun gridHomeCardHeightDp(cellWidthDp: Float): Float {
    val inner = (cellWidthDp - 28f).coerceAtLeast(1f)
    return 28f + inner * 1.5f + 182f
}

/**
 * `userScrollEnabled = false` ızgaranın SONSUZ OLMAYAN yüksekliği.
 * Satır sayısı × satır yüksekliği + satır aralığı; hücre genişliği ekran
 * genişliğinden (yatay 16+16 dolgu, 8.dp sütun arası) türetilir.
 */
@Composable
private fun rememberGridHomeEmbedHeight(
    columns: Int,
    rowCount: Int,
    perRowHeightDp: (cellWidthDp: Float) -> Float
): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val cols = columns.coerceAtLeast(1)
    val cellWidthDp = (screenWidthDp - 32f - 8f * (cols - 1)) / cols
    val rows = rowCount.coerceAtLeast(1)
    val total = rows * perRowHeightDp(cellWidthDp) + (rows - 1) * 8f
    // Güvenlik sınırı: hesap sapması olsa bile sonuç asla ∞ olmaz.
    return total.coerceAtMost(screenWidthDp * 6f).dp
}

// Geriye dönük uyumluluk — String tabanlı eski çağrı imzası
@Composable
fun GridHomeContent(
    items: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_popular_discoveries_8e51823),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KitsugiColors.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Card(
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(text = item, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
