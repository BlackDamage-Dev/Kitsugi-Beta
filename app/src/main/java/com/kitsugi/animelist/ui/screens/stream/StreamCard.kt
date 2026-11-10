package com.kitsugi.animelist.ui.screens.stream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.MeasuredStreamInfo
import com.kitsugi.animelist.data.repository.StreamAudioKind
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.delay

/**
 * Kart görüş alanına girdikten sonra ölçüm başlatmadan önce beklenen süre.
 * Hızlı kaydırma sırasında kartlar bu süre dolmadan görüş alanından çıkar; ölçüm
 * hiç başlamaz (iptal edilen coroutine = sıfır ağ isteği).
 */
private const val PROBE_SETTLE_DELAY_MS = 350L

/** Kart görselinin sabit yüksekliği — dikey/yatay düzende aynı kalır. */
private val CARD_THUMB_HEIGHT = 104.dp
private val CARD_THUMB_WIDTH = 74.dp

/**
 * Tek bir video kaynağı satırı.
 *
 * Tasarım kararı (v2.4.319): kartlar artık **önceki sürümdeki gibi görselsiz değil**;
 * sol tarafta videoya/bölüme ait küçük bir kapak görseli (yoksa medyanın posteri) durur.
 * Görsel her iki düzende de aynı boyutta kalır (74×104 dp, 2:3 oran) ve kart içeriği
 * buna göre kırpılmaz.
 *
 * **Tüm veriler her zaman görünür:** kalite, ses/altyazı türü, boyut, önbellek/P2P durumu
 * ve kaynak host'u ayrı ayrı dokunmayı gerektirmeden satırda yer alır. Önceki sürümdeki
 * "Ayrıntılar" akordiyonu kaldırıldı (kullanıcı her kartı tek tek açmak zorunda kalıyordu).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamCard(
    source: StreamSource,
    accentColor: Color,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAudioDownloadClick: (() -> Unit)? = null,
    /** Eklenti kapağı yoksa gösterilecek medya posteri (dizi/film afişi). */
    posterFallbackUrl: String? = null,
    /**
     * Ölçüm (ağ isteği) izni. Liste, kartı YALNIZCA görüş alanındayken true geçer.
     *
     * NEDEN: 60+ akış sonucu olan listelerde her kart compose edilir edilmez ölçüm
     * başlatıyordu. Hızlı kaydırmada onlarca ölçüm aynı anda kuyruğa girip OkHttp
     * havuzunu ve eşzamanlılık kapısını dolduruyor, liste kilitleniyordu.
     */
    probeEnabled: Boolean = true
) {
    // ── Gerçek (ölçülen) akış bilgisi ────────────────────────────────────────
    // Kaynak kalite/dil bildirmediyse tahmin ETMİYORUZ; bunun yerine akışın kendisini
    // ölçüyoruz (HLS master playlist → gerçek çözünürlükler ve ses/altyazı dilleri).
    // Ölçüm yalnızca (1) kart görünürken ve (2) kaynak meta verisi eksikken yapılır.
    val measured by produceState<MeasuredStreamInfo?>(
        initialValue = StreamProbe.cached(source),
        key1 = listOf(source.url, source.streamTypeHint, source.requestHeaders, probeEnabled)
    ) {
        if (value == null && probeEnabled && StreamProbe.isProbeNeeded(source)) {
            // Yerleşme gecikmesi: hızlı kaydırmada kart görüş alanından çıkar ve coroutine
            // iptal edilir — hiç ağ isteği başlatılmamış olur (boşa ölçüm = kasma + veri).
            delay(PROBE_SETTLE_DELAY_MS)
            value = runCatching { StreamProbe.probe(source) }.getOrNull()
        }
    }

    val qualityInfo = remember(source, measured) { resolveStreamQuality(source, measured) }
    // Host/dosya etiketi her zaman görünür — kullanıcı "bu hangi veriye ait" sorusunu
    // kartı açmadan yanıtlayabilsin.
    val hostLabel = remember(source.url, source.title, source.infoHash) { getCleanVideoSourceLabel(source) }

    val imageModel = remember(source.thumbnailUrl, posterFallbackUrl) {
        source.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: posterFallbackUrl?.takeIf { it.isNotBlank() }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        border = BorderStroke(1.dp, KitsugiColors.Border.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamThumbnail(
                model = imageModel,
                isAdult = source.isAdultContent,
                accentColor = accentColor,
                qualityLabel = qualityInfo.label ?: if (qualityInfo.isAdaptive) "HLS" else null
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f).heightIn(min = CARD_THUMB_HEIGHT),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    // Bazı eklentiler `name` boş bırakabiliyor; o durumda dosya/bölüm
                    // başlığına düşülür — kart hiçbir zaman başlıksız görünmesin.
                    // Ayrıca isim HTML/CSS artığı taşıyabiliyor ("… .fa-secondary{opacity:.4}")
                    // (2026-11-01): gösterimden önce temizlenir.
                    text = remember(source.name, source.title) {
                        com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.name)
                            .ifBlank {
                                com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.title)
                            }
                            .ifBlank { "—" }
                    },
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.addonName,
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " · $hostLabel",
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(5.dp))
                // Tüm bilgi rozetleri TEK bileşenden gelir (kalite, ses, boyut, seeder/peer,
                // altyazı dilleri, codec/HDR, önbellek). Kanıt yoksa rozet basılmaz.
                StreamInfoChips(
                    stream = source,
                    measured = measured,
                    accentColor = accentColor,
                    showQuality = false // kalite zaten görselin üzerinde yazıyor
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = accentColor,
                        contentColor = KitsugiColors.OnAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.PlayArrow,
                        contentDescription = stringResource(R.string.oynat),
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f),
                            contentColor = KitsugiColors.TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Download,
                            contentDescription = stringResource(R.string.indir),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (onAudioDownloadClick != null) {
                        val audioDesc = if (com.kitsugi.animelist.utils.isTurkish()) "Sesi İndir" else "Download Audio"
                        IconButton(
                            onClick = onAudioDownloadClick,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = accentColor.copy(alpha = 0.16f),
                                contentColor = accentColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Headphones,
                                contentDescription = audioDesc,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartın solundaki kapak görseli. Görsel yoksa vurgu renginde bir gradyan + film ikonu
 * gösterilir; böylece kart hiçbir zaman "boş kutu" gibi görünmez.
 */
@Composable
private fun StreamThumbnail(
    model: String?,
    isAdult: Boolean,
    accentColor: Color,
    qualityLabel: String?
) {
    Box(
        modifier = Modifier
            .width(CARD_THUMB_WIDTH)
            .height(CARD_THUMB_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.26f),
                        KitsugiColors.SurfaceStrong.copy(alpha = 0.85f)
                    )
                )
            )
    ) {
        if (model != null) {
            KitsugiNsfwImage(
                model = model,
                contentDescription = null,
                isAdult = isAdult,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Movie,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
        // Alt kısımda okunabilirlik için hafif karartma + kalite etiketi.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        if (!qualityLabel.isNullOrBlank()) {
            Text(
                text = qualityLabel,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Küçük bilgi rozeti. [isVerified] true ise bilgi kaynağın meta verisinden ya da gerçek akış
 * ölçümünden gelir (kullanıcıya "✓" ile doğrulanmış olduğu gösterilir).
 */
@Composable
fun MetaChip(
    text: String,
    color: Color,
    bg: Color,
    isVerified: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            KitsugiEmojiLabel(
                label = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                iconSize = 9.dp
            )
            if (isVerified) {
                Icon(
                    imageVector = Icons.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}
