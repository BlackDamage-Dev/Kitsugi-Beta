package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import java.text.NumberFormat
import java.util.Locale

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * "TÜMÜNÜ GÖR" KART İSTATİSTİK ROZETLERİ (TEK KAYNAK)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Aynı keşfet sonucu iki farklı kartta çiziliyor:
 *  - **Liste görünümü** → [KitsugiRankingMediaCard] (poster solda, bilgi sağda)
 *  - **Kare (ızgara) görünümü** → [KitsugiExploreMediaCard] dikey varyantı
 *
 * Bu iki kart bölüm ilerlemesini ("1/25 bölüm"), puanı ("★ 8/10") ve üye sayısını
 * farklı biçimlerde gösteriyordu: liste görünümünde kutucuk/rozet dilinde, ızgarada
 * ise tek satırlık birleşik metin ("ANIME • 2013 • ★ 8/10 • MAL #1535") olarak.
 * Kullanıcı aynı öğeyi görünüm değiştirince farklı okuduğu için bu dosyadaki ORTAK
 * bileşen tek doğru kaynak hâline getirildi: iki görünüm de [KitsugiMediaCardStatBlock]
 * çizer, dolayısıyla rozetler (sayı + puan değeri) birebir aynıdır.
 *
 * Rozet sözleşmesi — **referans liste görünümüdür**, kare görünüm birebir uyar:
 *  - Biçim satırı: "Anime (25 bölüm)" / raf alt başlığı — soluk, tek satır.
 *  - Bölüm bilgisi: "0/12 bölüm" — durum renginde (izliyorum mavi, tamamlandı yeşil…).
 *  - Puan: sarı yıldız (16 dp) + kalın sayı — kutucuk/dolgu YOK ([KitsugiScoreBadge]).
 *  - Üye sayısı: kişi ikonu + sayı (liste dışı öğelerde).
 *  - İlerleme çubuğu: 4 dp, yuvarlak (yalnız kullanıcının listesinde olan öğelerde).
 *
 * Kare görünümde metin alanı sabit yükseklikte olduğu için `compact = true` ile aynı
 * bileşenler tek satıra (bölüm + puan) ve 4 dp çubuğa sığdırılır; görünüm aynı kalır.
 */

/**
 * Liste görünümünün (referans tasarım) ÖLÇÜLERİ. Kare görünüm bu değerleri birebir
 * kullanır; böylece iki görünümdeki sayı/puan rozetleri aynı görünür.
 */
internal object KitsugiMediaStatSpec {
    /** Puan yıldızı — liste kartındaki `Icons.Star` rengi. */
    val ScoreStarTint = Color(0xFFFFB800)
    val ScoreStarSize = 16.dp
    val ScoreStarSizeCompact = 14.dp
    val ProgressBarHeight = 4.dp
    val RowGap = 6.dp
    val SectionGap = 4.dp
}

private val statLocale = Locale("tr", "TR")
private val statNumberFormat = NumberFormat.getInstance(statLocale)

/** Sayıları liste kartıyla aynı biçimde kullanır ("1.234.567"). */
internal fun kitsugiFormatStatNumber(number: Int): String = statNumberFormat.format(number)

/** Kullanıcı listesindeki durumun rengi (bölüm metni ve ilerleme çubuğu). */
internal fun kitsugiStatusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.Watching -> KitsugiColors.AccentBlue
    WatchStatus.Completed -> KitsugiColors.AccentGreen
    WatchStatus.Planned -> KitsugiColors.AccentOrange
    WatchStatus.Dropped -> KitsugiColors.AccentRed
    WatchStatus.Paused -> KitsugiColors.AccentPurple
    WatchStatus.Repeating -> KitsugiColors.AccentBlue
}

/** "1/25 bölüm" — bölüm bilgisinin metni. */
internal fun kitsugiEntryProgressText(entry: MediaEntry): String {
    val unit = if (entry.type == MediaType.Manga) "chapter" else "bölüm"
    val totalText = entry.total?.toString() ?: "?"
    return "${entry.progress}/$totalText $unit"
}

/** "Anime (25 bölüm)" — liste kartının biçim satırı. */
internal fun kitsugiFormatTypeText(result: JikanSearchResult): String {
    val typeName = when (result.type) {
        MediaType.Anime -> "Anime"
        MediaType.Manga -> "Manga"
        MediaType.Movie -> "Film"
        MediaType.TvShow -> "Dizi"
    }
    return if (result.total != null && result.total > 0) {
        "$typeName (${result.total} bölüm)"
    } else {
        result.subtitle.ifBlank { typeName }
    }
}

/** İnce, yuvarlak ilerleme çubuğu (liste kartının 4 dp çubuğu). */
@Composable
internal fun KitsugiStatProgressBar(
    progress: Int,
    total: Int?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = remember(progress, total) {
        val maxVal = if (total != null && total > 0) total else if (progress > 0) progress else 1
        (progress.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    }
    val shape = remember { RoundedCornerShape(999.dp) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(KitsugiMediaStatSpec.ProgressBarHeight)
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(KitsugiMediaStatSpec.ProgressBarHeight)
                .clip(shape)
                .background(color)
        )
    }
}

/**
 * TEK puan rozeti — **her iki görünümde de aynı**.
 *
 * Referans, liste görünümüdür: sarı yıldız (`Icons.Star`, 0x FFB800) + kalın puan metni.
 * Kare görünümde puan daha önce posterin üzerinde siyah zeminli ayrı bir kutu olarak
 * çiziliyordu ("★ 8/10"); bu yüzden aynı öğe görünüm değiştirince rozet farklı görünüyordu.
 * Artık iki kart da bu bileşeni kullanır: dolgu/kontur yok, yalnız yıldız + sayı.
 */
@Composable
internal fun KitsugiScoreBadge(
    scoreText: String,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    starSize: androidx.compose.ui.unit.Dp = KitsugiMediaStatSpec.ScoreStarSize,
    textColor: Color = KitsugiColors.TextPrimary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KitsugiMediaStatSpec.RowGap)
    ) {
        Icon(
            imageVector = Icons.Star,
            contentDescription = null,
            tint = KitsugiMediaStatSpec.ScoreStarTint,
            modifier = Modifier.size(starSize)
        )
        Text(
            text = scoreText,
            color = textColor,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Liste **ve** kare görünümün paylaştığı istatistik bloğu (liste kartı = referans).
 *
 *  - Biçim satırı "Anime (25 bölüm)"  (kare görünümde kapatılır; alt başlık zaten var)
 *  - Bölüm metni "0/12 bölüm" — durum renginde, kutu YOK
 *  - Puan: yıldız + sayı — her iki görünümde aynı bileşen ([KitsugiScoreBadge])
 *  - Üye sayısı (liste dışı öğelerde)
 *  - 4 dp ilerleme çubuğu (kullanıcının listesindeki öğelerde)
 */
@Composable
internal fun KitsugiMediaCardStatBlock(
    result: JikanSearchResult,
    mediaEntry: MediaEntry?,
    scoreFormat: String,
    hideScores: Boolean,
    modifier: Modifier = Modifier,
    showTypeLine: Boolean = true,
    /** Kare görünümde bölüm + puan tek satırda çizilir (metin alanı sabit yükseklikte). */
    compact: Boolean = false,
) {
    val typeText = remember(result) { kitsugiFormatTypeText(result) }
    val scoreText = remember(result, mediaEntry, scoreFormat, hideScores) {
        kitsugiPublicScoreLabel(result, mediaEntry, scoreFormat, hideScores)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showTypeLine) {
            Text(
                text = typeText,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(KitsugiMediaStatSpec.SectionGap))
        }

        if (mediaEntry != null) {
            val statusColor = kitsugiStatusColor(mediaEntry.status)
            if (compact) {
                // Kare görünüm: [0/12 bölüm] ★ 8
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KitsugiMediaStatSpec.RowGap)
                ) {
                    Text(
                        text = kitsugiEntryProgressText(mediaEntry),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (scoreText != null) {
                        KitsugiScoreBadge(
                            scoreText = scoreText,
                            textStyle = MaterialTheme.typography.labelSmall,
                            starSize = KitsugiMediaStatSpec.ScoreStarSizeCompact,
                            textColor = KitsugiColors.TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(KitsugiMediaStatSpec.RowGap))
                KitsugiStatProgressBar(
                    progress = mediaEntry.progress,
                    total = mediaEntry.total,
                    color = statusColor,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Liste görünümü (referans): satır satır, kutusuz.
                Text(
                    text = kitsugiEntryProgressText(mediaEntry),
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (scoreText != null) {
                    Spacer(modifier = Modifier.height(KitsugiMediaStatSpec.SectionGap))
                    KitsugiScoreBadge(scoreText = scoreText)
                }
                Spacer(modifier = Modifier.height(KitsugiMediaStatSpec.RowGap))
                KitsugiStatProgressBar(
                    progress = mediaEntry.progress,
                    total = mediaEntry.total,
                    color = statusColor,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        } else {
            if (scoreText != null) {
                KitsugiScoreBadge(
                    scoreText = scoreText,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    textColor = KitsugiColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(KitsugiMediaStatSpec.SectionGap))
            }
            val membersVal = result.members ?: result.favorites
            if (membersVal != null && membersVal > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KitsugiMediaStatSpec.RowGap)
                ) {
                    Icon(
                        imageVector = Icons.Group,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = kitsugiFormatStatNumber(membersVal),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** "unrated"/"Puan yok" gibi etiketler rozet olarak çizilmez. */
internal fun kitsugiIsRatedLabel(label: String): Boolean {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    return lower != "unrated" && lower != "-" && lower != "—" &&
        lower != "puanlanmamış" && lower != "puan yok" && lower != "n/a" &&
        lower != "null" && lower != "0" && lower != "0/10"
}

/**
 * Kartta gösterilecek puan metni; yoksa `null`.
 *
 *  - Kullanıcının kendi puanı varsa: seçili puan biçiminde (ör. "8/10").
 *  - Yoksa kaynağın ortalaması: liste görünümünün kullandığı iki ondalık biçim ("8.00").
 */
internal fun kitsugiPublicScoreLabel(
    result: JikanSearchResult,
    mediaEntry: MediaEntry?,
    scoreFormat: String,
    hideScores: Boolean
): String? {
    if (hideScores) return null
    if (mediaEntry != null && (mediaEntry.score ?: 0) > 0) {
        return mediaEntry.getDisplayScore(scoreFormat, hideScores).takeIf { kitsugiIsRatedLabel(it) }
    }
    val scoreVal = result.rawScoreDouble ?: result.score?.toDouble()
    if (scoreVal == null || scoreVal <= 0.0) return null
    return String.format(Locale.US, "%.2f", scoreVal)
}
