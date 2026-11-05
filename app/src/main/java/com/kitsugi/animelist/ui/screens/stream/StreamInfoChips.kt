package com.kitsugi.animelist.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.MeasuredStreamInfo
import com.kitsugi.animelist.data.repository.StreamAudioKind
import com.kitsugi.animelist.data.repository.StreamInfoResolver
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text

/**
 * Akış rozetlerinin **tek** tanımı.
 *
 * NEDEN TEK YER: Aynı bilgi (boyut, ses, altyazı, seeder) mobil kartta, TV satırında ve
 * oynatıcı içi "Kalite / Kaynak" sayfasında farklı görünüyordu; bazı ekranlarda hiç
 * görünmüyordu. Tüm ekranlar artık buradaki bileşenleri kullanır.
 *
 * TASARIM KURALI (değişmez): Bir rozet yalnızca KANIT varsa basılır.
 *  - Boyut: kaynağın bildirdiği ya da ölçülen gerçek bayt değeri yoksa "Boyut bilinmiyor"
 *    gösterilir — uydurma bir GB/MB değeri YAZILMAZ.
 *  - Seeder/peer: yalnızca kaynak sayı verdiyse.
 *  - Altyazı/ses dilleri: yalnızca eklenti listesi ya da HLS ölçümü verdiyse.
 */
object StreamBadgeSpec {

    /** Kart üzerindeki seeder rozeti rengi: sağlığa göre yeşil → sarı → kırmızı. */
    @Composable
    fun seederColor(seeders: Int?): Color = when {
        seeders == null -> KitsugiColors.TextMuted
        seeders >= 20 -> KitsugiColors.AccentGreen
        seeders >= 5 -> KitsugiColors.AccentYellow
        seeders >= 1 -> KitsugiColors.AccentOrange
        else -> KitsugiColors.AccentRed
    }
}

/**
 * İkonlu ve sayısal değerli rozet. Seeder/peer gibi sayaçlar için kullanılır;
 * ikon tek başına anlam taşımadığı için metin HER ZAMAN yazılır.
 */
@Composable
fun StreamIconChip(
    icon: ImageVector,
    text: String,
    color: Color,
    background: Color = KitsugiColors.SurfaceStrong.copy(alpha = 0.6f),
    contentDescription: String? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Seeder rozeti — sayı + yukarı ok ikonu (yeşilden kırmızıya sağlık rengi). */
@Composable
fun SeederChip(seeders: Int?) {
    val value = seeders ?: return
    val color = StreamBadgeSpec.seederColor(value)
    StreamIconChip(
        icon = Icons.Rounded.ArrowUpward,
        text = formatSwarmCount(value),
        color = color,
        contentDescription = stringResource(R.string.stream_badge_seeders, value.toString())
    )
}

/** Peer/leecher rozeti — sayı + aşağı ok ikonu. */
@Composable
fun PeerChip(peers: Int?) {
    val value = peers ?: return
    StreamIconChip(
        icon = Icons.Rounded.ArrowDownward,
        text = formatSwarmCount(value),
        color = KitsugiColors.AccentBlue,
        contentDescription = stringResource(R.string.stream_badge_peers, value.toString())
    )
}

/** "1.2K" gibi kısa sayı biçimi (tek kaynak: [com.kitsugi.animelist.data.repository.TorrentSwarmStats]). */
fun formatSwarmCount(value: Int): String =
    com.kitsugi.animelist.data.repository.TorrentSwarmStats.formatCompact(value) ?: value.toString()

/**
 * Akışın tüm bilgi rozetlerini tek satırda üretir.
 *
 * Sıra: kalite → ses türü → boyut → seeder → peer → ses dilleri → altyazı dilleri →
 * codec/HDR → önbellek durumu. Her rozet bağımsız olarak "kanıt yoksa gizlenir".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StreamInfoChips(
    stream: StreamSource,
    measured: MeasuredStreamInfo?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    showQuality: Boolean = false,
    /** Oynatıcı gibi dar alanlarda ikon+sayı yerine yalnızca sayı gösterilir. */
    compactSwarm: Boolean = true,
    /** Boyut bilinmiyorsa "Boyut bilinmiyor" rozeti basılsın mı (torrent akışlarında). */
    showUnknownSize: Boolean = true,
    gap: androidx.compose.ui.unit.Dp = 4.dp
) {
    val qualityInfo = remember(stream, measured) { StreamInfoResolver.resolveQuality(stream, measured) }
    val langInfo = remember(stream, measured) { StreamInfoResolver.resolveLang(stream, measured) }
    val size = remember(stream, measured) { StreamInfoResolver.resolveSizeLabel(stream, measured) }
    val tech = remember(stream, measured) { StreamInfoResolver.resolveTechInfo(stream, measured) }
    val cacheState = remember(stream) { getCacheState(stream) }
    val swarm = stream.swarmStats

    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (showQuality && qualityInfo.label != null) {
            val qColor = qualityChipColor(qualityInfo.height)
            MetaChip(
                text = qualityInfo.label,
                color = qColor,
                bg = qColor.copy(alpha = 0.14f)
            )
        }

        // Ses türü (kanıt varsa)
        when (langInfo.kind) {
            StreamAudioKind.DUB -> MetaChip(
                text = stringResource(R.string.stream_lang_dub),
                color = accentColor,
                bg = accentColor.copy(alpha = 0.14f)
            )
            StreamAudioKind.SUB -> MetaChip(
                text = stringResource(R.string.stream_lang_sub),
                color = accentColor,
                bg = accentColor.copy(alpha = 0.14f)
            )
            StreamAudioKind.DUAL -> MetaChip(
                text = stringResource(R.string.stream_lang_dual),
                color = accentColor,
                bg = accentColor.copy(alpha = 0.14f)
            )
            StreamAudioKind.UNKNOWN -> Unit
        }

        // Boyut — indirme kararı için en kritik bilgi; her zaman görünür konumda.
        if (size != null) {
            StreamIconChip(
                icon = Icons.Rounded.Storage,
                text = size,
                color = if (measured?.sizeBytes != null || stream.videoSizeBytes != null) {
                    KitsugiColors.AccentTeal
                } else {
                    KitsugiColors.TextSecondary
                },
                contentDescription = stringResource(R.string.stream_size_short)
            )
        } else if (showUnknownSize && stream.isTorrent) {
            // Torrent akışlarında boyut indirme kararı için kritiktir; bilinmiyorsa bunu
            // AÇIKÇA söylüyoruz (uydurma GB/MB yazmıyoruz). Embed/CS akışlarında ise
            // oynatma öncesi boyut ölçülemez, gereksiz gürültü olmasın diye basılmaz.
            MetaChip(
                text = stringResource(R.string.stream_size_unknown),
                color = KitsugiColors.TextMuted,
                bg = KitsugiColors.SurfaceStrong.copy(alpha = 0.45f)
            )
        }

        // Torrent swarm: seeder ve peer sayıları (yalnızca kaynak verdiyse)
        if (stream.isTorrent) {
            if (swarm.seeders != null) {
                if (compactSwarm) SeederChip(swarm.seeders) else StreamIconChip(
                    icon = Icons.Rounded.ArrowUpward,
                    text = stringResource(R.string.stream_badge_seeders, formatSwarmCount(swarm.seeders)),
                    color = StreamBadgeSpec.seederColor(swarm.seeders)
                )
            }
            if (swarm.leechers != null) {
                if (compactSwarm) PeerChip(swarm.leechers) else StreamIconChip(
                    icon = Icons.Rounded.ArrowDownward,
                    text = stringResource(R.string.stream_badge_peers, formatSwarmCount(swarm.leechers)),
                    color = KitsugiColors.AccentBlue
                )
            }
        }

        // Ses dilleri (eklenti/HLS ölçümü)
        if (langInfo.audioLanguages.isNotEmpty()) {
            StreamIconChip(
                icon = Icons.Rounded.VolumeUp,
                text = langInfo.audioLanguages.joinToString("/").take(18),
                color = accentColor,
                background = accentColor.copy(alpha = 0.10f),
                contentDescription = stringResource(
                    R.string.stream_badge_audio_tracks,
                    langInfo.audioLanguages.joinToString("/")
                )
            )
        } else if (langInfo.hasAudioTracks) {
            StreamIconChip(
                icon = Icons.Rounded.VolumeUp,
                text = stringResource(R.string.stream_badge_audio_tracks_present),
                color = accentColor,
                background = accentColor.copy(alpha = 0.10f)
            )
        }

        // Altyazı dilleri (eklenti listesi / HLS `#EXT-X-MEDIA`)
        if (langInfo.subtitleLanguages.isNotEmpty()) {
            StreamIconChip(
                icon = Icons.Rounded.Subtitles,
                text = langInfo.subtitleLanguages.joinToString("/").take(18),
                color = KitsugiColors.TextSecondary,
                contentDescription = stringResource(
                    R.string.stream_badge_subtitle_tracks,
                    langInfo.subtitleLanguages.joinToString("/")
                )
            )
        } else if (langInfo.hasInternalSubtitles) {
            StreamIconChip(
                icon = Icons.Rounded.Subtitles,
                text = stringResource(R.string.stream_badge_internal_sub),
                color = KitsugiColors.TextSecondary
            )
        }

        // Codec / HDR / kaynak türü — dosya adında açıkça yazıyorsa
        tech.codec?.let { codec ->
            StreamIconChip(
                icon = Icons.Rounded.Memory,
                text = codec,
                color = KitsugiColors.TextSecondary
            )
        }
        tech.hdr?.let { hdr ->
            MetaChip(
                text = hdr,
                color = KitsugiColors.AccentYellow,
                bg = KitsugiColors.AccentYellow.copy(alpha = 0.14f)
            )
        }
        tech.source?.let { src ->
            MetaChip(
                text = src,
                color = KitsugiColors.TextSecondary,
                bg = KitsugiColors.SurfaceStrong.copy(alpha = 0.6f)
            )
        }

        // Önbellek / P2P durumu — yalnızca torrent akışlarında anlamlı
        if (stream.isTorrent) {
            when (cacheState) {
                DebridCacheState.CACHED -> StreamIconChip(
                    icon = Icons.Rounded.FlashOn,
                    text = stringResource(R.string.stream_cached),
                    color = KitsugiColors.AccentGreen,
                    background = KitsugiColors.AccentGreen.copy(alpha = 0.12f)
                )
                DebridCacheState.NOT_CACHED -> MetaChip(
                    text = "⚡ Debrid",
                    color = KitsugiColors.AccentOrange,
                    bg = KitsugiColors.AccentOrange.copy(alpha = 0.12f)
                )
                DebridCacheState.P2P -> MetaChip(
                    text = "🔗 P2P",
                    color = KitsugiColors.TextSecondary,
                    bg = KitsugiColors.SurfaceStrong.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun qualityChipColor(height: Int?): Color = when {
    height == null -> KitsugiColors.TextMuted
    height >= 2160 -> KitsugiColors.AccentRed
    height >= 1440 -> KitsugiColors.AccentPurple
    height >= 1080 -> KitsugiColors.AccentBlue
    height >= 720 -> KitsugiColors.AccentGreen
    else -> KitsugiColors.AccentOrange
}

/**
 * Bir eklentinin getirdiği kaynak grubu için özet satır üretir:
 * bilinen boyutların toplamı ve varsa en yüksek seeder sayısı.
 *
 * Kaynak seçim sayfasında "3 eklenti · 30 video" bilgisinin yanında kullanıcı
 * "bu eklentiden gelen dosyalar toplam kaç GB / kaç kişi indiriyor" görebilsin diye.
 * Hiçbir veri yoksa null döner (boş satır basılmaz).
 */
object StreamGroupSummary {

    fun totalKnownBytesLabel(streams: List<StreamSource>): String? {
        val bytes = streams.sumOf { stream ->
            stream.videoSizeBytes ?: 0L
        }
        val textual = streams.mapNotNull { stream ->
            if (stream.videoSizeBytes != null) null else StreamInfoResolver.resolveSizeLabel(stream)
        }
        // Metinsel ("1.4 GB") boyutlar da toplanabilir; birimleri normalize ediyoruz.
        val textualBytes = textual.sumOf { parseSizeLabelToBytes(it) ?: 0L }
        val total = bytes + textualBytes
        return if (total > 0) StreamInfoResolver.formatBytes(total) else null
    }

    fun maxSeeders(streams: List<StreamSource>): Int? =
        streams.mapNotNull { it.swarmStats.seeders }.maxOrNull()

    private fun parseSizeLabelToBytes(label: String): Long? {
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|GIB|MB|MIB|TB)""", RegexOption.IGNORE_CASE).find(label)
            ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2].uppercase()) {
            "TB" -> (value * 1_000_000_000_000L).toLong()
            "GB", "GIB" -> (value * 1_000_000_000L).toLong()
            "MB", "MIB" -> (value * 1_000_000L).toLong()
            else -> null
        }
    }
}
