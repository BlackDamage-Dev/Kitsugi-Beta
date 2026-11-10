package com.kitsugi.animelist.ui.screens.fullscreen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.core.player.input.PlayerKeyBinding
import com.kitsugi.animelist.core.player.input.PlayerShortcutAction
import com.kitsugi.animelist.core.player.input.PlayerShortcutGroup
import com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Klavye kısayolunun tetiklediği eylemin **kısa ömürlü** göstergesi.
 *
 * Kullanıcı geri bildirimi (2026-11-04): "klavye ile oynatıcımda bir şeyleri
 * yönetirken sürekli oynatıcı arayüzüm gelip gitmesin, sadece tetiklediğim tuşun
 * özelliği görünüp yok olsun."
 *
 * Önceki davranış: her klavye olayında `viewModel.showControls()` çağrılıyor,
 * tam OSD (üst bar, alt bar, seekbar, orta kontroller) 5 saniyeliğine açılıyordu.
 * Artık tuş basımı yalnızca bu küçük göstergeyi açar; OSD'ye dokunulmaz.
 *
 * Tasarım kararları
 * ─────────────────
 *  - Gösterge **ekranın ortasında değil**, alt-orta bölgede durur: altyazıların ve
 *    oynatma kontrollerinin üstünde kalmaz (mpv OSD'sinin yerleşim mantığı).
 *  - Yalnızca "büyük" eylemler (oynat/duraklat, hız, sessize alma) merkezde kısa
 *    bir darbe (pulse) olarak gösterilir — YouTube/Netflix davranışı.
 *  - Aynı eylem tekrar tetiklenirse gösterge **yeniden başlar**, üst üste yığılmaz;
 *    arama ve ses gibi eylemlerde değer birikir ve tek göstergede toplam gösterilir
 *    (Just Player'ın `keySeekStart` birikimi).
 *  - Metin ölçeklenebilir; göstergenin kendisi dokunma yakalamaz (`pointerInput` yok),
 *    bu yüzden jestler (çift dokunma, kaydırma) aynen çalışmaya devam eder.
 *
 * Tema uyumu (2026-11-04 düzeltmesi)
 * ─────────────────────────────────
 * Önce yüzey `Color(0xFF050510)` sabit koyu lacivertti; uygulama temasının vurgu rengi
 * hiç dikkate alınmıyordu ve AMOLED/canlı tema seçen kullanıcıda "oynatıcıyla uyumsuz
 * kutu" gibi duruyordu. Artık:
 *   - yüzey  → `playerSurfaceColor()` (oynatıcı panelleriyle **aynı** formül)
 *   - kenarlık + ilerleme çubuğu → o anki vurgu rengi (gradyan destekli)
 *   - metin  → beyaz tonları (koyu yüzeyde her temada okunur)
 *
 * @param message gösterilecek içerik; `null` ise tamamen kaybolur.
 * @param imageVector eyleme karşılık gelen ikon.
 * @param accentColor vurgu rengi; `null` ise o anki tema vurgusu kullanılır.
 */
@Composable
fun KeyboardActionIndicator(
    message: KeyboardIndicatorMessage?,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    /**
     * Vurgu rengi. `null` ise o anki tema vurgusu kullanılır.
     *
     * Varsayılan olarak `KitsugiColors.Accent` YAZILAMAZ: o bir `@Composable` getter
     * olduğu için varsayılan parametre ifadesinde çağrılamaz (derleme hatası).
     * Bu yüzden varsayılan `null`, çözümleme gövdede yapılıyor.
     */
    accentColor: Color? = null,
) {
    val accent = accentColor ?: KitsugiColors.Accent
    AnimatedVisibility(
        visible = message != null,
        enter = if (reduceMotion) fadeIn(tween(120)) else
            fadeIn(tween(120)) + scaleIn(initialScale = 0.92f) + slideInVertically(tween(140)) { it / 4 },
        exit = if (reduceMotion) fadeOut(tween(160)) else
            fadeOut(tween(160)) + scaleOut(targetScale = 0.96f) + slideOutVertically(tween(160)) { it / 4 },
        modifier = modifier,
    ) {
        val current = message ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(if (current.big) 20.dp else 14.dp)

        // Gösterge yüzeyi oynatıcının kendi yüzey renginden türetilir; kenarlık ve
        // ilerleme çubuğu uygulamanın **seçili vurgu rengini** kullanır. Böylece kullanıcı
        // hangi temayı seçerse seçsin gösterge oynatıcıyla aynı görsel dili konuşur.
        val surface = playerSurfaceColor(alpha = 0.94f)
        Box {
            Column(
                modifier = Modifier
                    .widthIn(min = 156.dp, max = 440.dp)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                surface,
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.75f),
                                accent.copy(alpha = 0.22f),
                            )
                        ),
                        shape = shape,
                    )
                    .padding(
                        horizontal = if (current.big) 22.dp else 16.dp,
                        vertical = if (current.big) 14.dp else 10.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (current.progress != null) 8.dp else 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (current.big) 12.dp else 9.dp),
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (current.big) 30.dp else 20.dp),
                    )
                    Text(
                        text = current.title,
                        color = Color.White,
                        style = if (current.big) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                current.detail?.let { detail ->
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }

                // İlerleme çubuğu (isteğe bağlı): sarma / ses gibi ölçülebilir eylemlerde
                // nerede olduğunu tek bakışta gösterir. mpv'nin OSD bar'ının mobil karşılığı.
                current.progress?.let { raw ->
                    val fraction = raw.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accent)
                            )
                        }
                    }
                }
            }

            // Tuş rozeti: hangi tuşun tetiklediğini gösterir (keşfedilebilirlik).
            current.keyLabel?.let { label ->
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 7.dp),
                )
            }
        }
    }
}

private val GlassLine = Color.White.copy(alpha = 0.20f)


/**
 * Klavye göstergesinin taşıdığı bilgi.
 *
 * [detail] eylemin ölçülebilir sonucunu taşır (ör. "Ses %45", "1:12 / 24:00").
 * Bilinçli olarak ilerleme çubuğu YOK: gösterge mümkün olduğunca küçük kalsın ve
 * oynatma kontrollerinin (seekbar) yerini almasın; kullanıcı isteği "sadece
 * tetiklediğim tuşun özelliği görünüp yok olsun" idi.
 */
data class KeyboardIndicatorMessage(
    val title: String,
    val detail: String? = null,
    val big: Boolean = false,
    val action: PlayerShortcutAction? = null,
    /**
     * 0..1 arası ilerleme (sarma → konum/süre, ses → seviye/maks).
     * `null` ise çubuk çizilmez (sarma/ses dışındaki eylemler).
     */
    val progress: Float? = null,
    /**
     * Tetikleyen tuşun etiketi (ör. "→", "Shift + →"). Göstergenin sağ üst köşesinde
     * küçük bir rozet olarak çizilir: kullanıcı hangi tuşun ne yaptığını **anında**
     * öğrenir (mpv OSD'sinin tuş adı gösterme davranışı). `null` = rozet yok.
     */
    val keyLabel: String? = null,
    /** Her tetiklemede artan sayaç: aynı eylem tekrar basıldığında süre sıfırlanır. */
    val seq: Long = 0L,
)

/**
 * Klavye yardım ekranı (kısayol listesi).
 *
 * mpv'nin `--input-test` / VLC'nin "?" ekranı gibi: kullanıcı hangi tuşun ne
 * yaptığını oynatıcıdan çıkmadan görebilsin. Yalnızca okunur; oynatmayı durdurmaz.
 */
@Composable
fun KeyboardShortcutsHelpOverlay(
    visible: Boolean,
    effectiveBindings: Map<PlayerKeyBinding, PlayerShortcutAction>,
    modifier: Modifier = Modifier,
    disabledActions: Set<PlayerShortcutAction> = emptySet(),
    onDismiss: () -> Unit = {},
    accentColor: Color? = null,
) {
    val accent = accentColor ?: KitsugiColors.Accent
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.86f))
                // Dokunarak kapatma: fiziksel klavyesi olmayan kullanıcı da bu ekrandan
                // çıkabilmeli. Yalnızca arka plan yakalar; liste kendi kaydırmasını korur.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .widthIn(max = 880.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                playerSurfaceColor(alpha = 0.96f),
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.55f),
                                accent.copy(alpha = 0.16f),
                            )
                        ),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Klavye Kısayolları",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Kapatmak için herhangi bir tuş / dokun",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Hiçbir kısayol etkin değilse (hepsi kapatıldıysa) boş bir katman
                // göstermek yerine nedenini söyle.
                val totalRows = PlayerShortcutGroup.entries.sumOf { group ->
                    groupActions(group, effectiveBindings, disabledActions).size
                }
                if (totalRows == 0) {
                    Text(
                        text = "Etkin kısayol yok. Oynatıcı Ayarları → Klavye bölümünden " +
                            "kısayolları açabilirsiniz.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    PlayerShortcutGroup.entries.forEach { group ->
                        val rows = groupActions(group, effectiveBindings, disabledActions)
                        if (rows.isEmpty()) return@forEach
                        item(key = "group-${group.name}") {
                            Text(
                                text = group.title.uppercase(),
                                color = accent.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(rows.size, key = { "${group.name}-$it" }) { index ->
                            val (action, keys) = rows[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = action.label,
                                    color = Color.White.copy(alpha = 0.92f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = keys.joinToString("  /  ") { it.displayLabel() },
                                    color = accent,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bir grup için (eylem → tuşlar) listesi; her eylem yalnızca bir kez görünür. */
private fun groupActions(
    group: PlayerShortcutGroup,
    effective: Map<PlayerKeyBinding, PlayerShortcutAction>,
    disabled: Set<PlayerShortcutAction>,
): List<Pair<PlayerShortcutAction, List<PlayerKeyBinding>>> {
    val byAction = effective.entries
        .filter { (_, action) -> action.group == group && action !in disabled }
        .groupBy({ it.value }, { it.key })
    return PlayerShortcutAction.entries
        .filter { it.group == group && it in byAction }
        .sortedBy { it.ordinal }
        .map { it to byAction.getValue(it) }
}

