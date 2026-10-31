package com.kitsugi.animelist.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.app.AniListStats
import com.kitsugi.animelist.ui.app.FormatStatItem
import com.kitsugi.animelist.ui.app.MalActivityEntry
import com.kitsugi.animelist.ui.app.MalClub
import com.kitsugi.animelist.ui.app.MalExternalLink
import com.kitsugi.animelist.ui.app.MalProfileState
import com.kitsugi.animelist.ui.app.MalRecommendation
import com.kitsugi.animelist.ui.app.MalReview
import com.kitsugi.animelist.ui.app.RankedStatItem
import com.kitsugi.animelist.ui.app.ScoreStatItem
import com.kitsugi.animelist.ui.app.StatusStatItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/*
 * MAL profilinin cihaz üzeri HTML ayrıştırma / miribyou ile gelen ek bölümleri:
 * "Hakkında" bilgileri (cinsiyet, doğum günü, son görülme, hakkında yazısı) ve
 * AniList tarzı "Aktivite" akışı (son güncellemeler + bölüm bölüm geçmiş).
 */

private fun openMalUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun formatLocalizedDate(value: OffsetDateTime, pattern: String): String =
    value.atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, KitsugiStrings.locale()))

private fun parseIso(iso: String?): OffsetDateTime? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** Localized relative timestamp or localized calendar date. */
internal fun malRelativeTime(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    val now = OffsetDateTime.now()
    val d = Duration.between(t, now)
    return when {
        d.isNegative || d.toMinutes() < 1 -> KitsugiStrings.get(R.string.profile_time_just_now)
        d.toMinutes() < 60 -> KitsugiStrings.get(R.string.profile_time_minutes_ago, d.toMinutes())
        d.toHours() < 24 -> KitsugiStrings.get(R.string.profile_time_hours_ago, d.toHours())
        d.toDays() < 2 -> KitsugiStrings.get(R.string.profile_time_yesterday)
        d.toDays() < 7 -> KitsugiStrings.get(R.string.profile_time_days_ago, d.toDays())
        t.year == now.year -> formatLocalizedDate(t, "d MMM")
        else -> formatLocalizedDate(t, "d MMM yyyy")
    }
}

private fun malDate(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    return formatLocalizedDate(t, "d MMMM yyyy")
}

private fun malAge(iso: String?): Int? {
    val birth = parseIso(iso)?.toLocalDate() ?: return null
    val today = LocalDate.now()
    // Yılı gizli doğum günlerinde ayrıştırıcı içinde bulunulan yılı kullanır → yaş anlamsız.
    if (birth.year >= today.year) return null
    var age = today.year - birth.year
    if (today.dayOfYear < birth.dayOfYear) age--
    return age.takeIf { it in 1..120 }
}

/** Localized MAL list status. */
internal fun malStatusLabel(status: String, isManga: Boolean): String = when (status.lowercase(Locale.US)) {
    "watching" -> KitsugiStrings.get(R.string.profile_status_watching)
    "reading" -> KitsugiStrings.get(R.string.profile_status_reading)
    "completed" -> KitsugiStrings.get(R.string.profile_status_completed)
    "on-hold" -> KitsugiStrings.get(R.string.profile_status_on_hold)
    "dropped" -> KitsugiStrings.get(R.string.profile_status_dropped)
    "plan to watch" -> KitsugiStrings.get(R.string.profile_status_plan_to_watch)
    "plan to read" -> KitsugiStrings.get(R.string.profile_status_plan_to_read)
    "rewatching" -> KitsugiStrings.get(R.string.profile_status_rewatching)
    "rereading" -> KitsugiStrings.get(R.string.profile_status_rereading)
    else -> KitsugiStrings.get(if (isManga) R.string.profile_status_reading else R.string.profile_status_watching)
}

private fun genderLabel(raw: String): String = when (raw.lowercase(Locale.US)) {
    "male" -> KitsugiStrings.get(R.string.profile_gender_male)
    "female" -> KitsugiStrings.get(R.string.profile_gender_female)
    "non-binary" -> "Non-binary"
    else -> when {
        raw.equals(KitsugiStrings.get(R.string.profile_gender_male), ignoreCase = true) -> KitsugiStrings.get(R.string.profile_gender_male)
        raw.equals(KitsugiStrings.get(R.string.profile_gender_female), ignoreCase = true) -> KitsugiStrings.get(R.string.profile_gender_female)
        else -> raw
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** "Hakkında" sekmesine eklenen kart: kişisel bilgiler + hakkında yazısı. */
@Composable
fun MalAboutExtras(state: MalProfileState, accentColor: Color) {
    val rows = buildList {
        if (state.gender.isNotBlank()) add(KitsugiStrings.get(R.string.profile_label_gender) to genderLabel(state.gender))
        malDate(state.birthday)?.let { date ->
            val age = malAge(state.birthday)
            add(KitsugiStrings.get(R.string.profile_label_birthday) to if (age != null) "$date ($age)" else date.substringBeforeLast(" "))
        }
        if (state.location.isNotBlank()) add(KitsugiStrings.get(R.string.profile_label_location) to state.location)
        malDate(state.joinedAt)?.let { add(KitsugiStrings.get(R.string.profile_label_joined) to it) }
        malRelativeTime(state.lastOnline)?.let { add(KitsugiStrings.get(R.string.profile_label_last_seen) to it) }
        if (state.animeDaysWatched > 0.0) {
            add(KitsugiStrings.get(R.string.profile_label_watch_time) to KitsugiStrings.get(R.string.profile_stat_days_watched_decimal, state.animeDaysWatched))
        }
        state.animeStats?.let { s ->
            if (s.episodesWatched > 0) add(KitsugiStrings.get(R.string.profile_label_episodes_watched) to s.episodesWatched.toString())
        }
        if (state.mangaDaysRead > 0.0) {
            add(KitsugiStrings.get(R.string.profile_mal_days_read) to KitsugiStrings.get(R.string.profile_stat_days_watched_decimal, state.mangaDaysRead))
        }
        state.mangaStats?.let { s ->
            if (s.episodesWatched > 0) add(KitsugiStrings.get(R.string.profile_label_chapters_read) to s.episodesWatched.toString())
        }
    }

    if (rows.isEmpty() && state.about.isBlank() && state.externalLinks.isEmpty()) {
        if (state.isExtraLoading) LoadingHint()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(18.dp)
    ) {
        if (rows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.profile_label_information),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            rows.forEach { (label, value) -> InfoRow(label, value) }
        }
        if (state.about.isNotBlank()) {
            if (rows.isNotEmpty()) Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.profile_label_about),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            var expanded by rememberSaveable { mutableStateOf(false) }
            if (expanded && state.aboutHtml.isNotBlank()) {
                com.kitsugi.animelist.ui.components.KitsugiHtmlWebView(
                    html = state.aboutHtml,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            } else {
                Text(
                    text = state.about,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.about.length > 400 || state.about.count { it == '\n' } > 7) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (expanded) stringResource(R.string.profile_show_less) else stringResource(R.string.profile_show_more),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
        if (state.externalLinks.isNotEmpty()) {
            if (rows.isNotEmpty() || state.about.isNotBlank()) Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.profile_mal_external_links),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.externalLinks.forEach { link ->
                    Card(
                        onClick = { openMalUrl(context, link.url) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(link.name, color = accentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.OpenInNew, contentDescription = null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = KitsugiColors.TextMuted)
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(R.string.profile_mal_loading), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = KitsugiColors.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/** AniList aktivite kartına benzeyen tek satır. */
@Composable
private fun MalActivityCard(
    entry: MalActivityEntry,
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!entry.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = entry.title,
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        malRelativeTime(entry.dateIso)?.let { time ->
            Spacer(Modifier.width(8.dp))
            Text(text = time, color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun updateText(e: MalActivityEntry): String {
    val label = malStatusLabel(e.status, e.isManga)
    val progress = when {
        e.progress != null && e.total != null && e.total > 0 -> {
            val count = "${e.progress}/${e.total}"
            KitsugiStrings.get(if (e.isManga) R.string.profile_chapter_progress else R.string.profile_episode_progress, count)
        }
        e.progress != null && e.progress > 0 -> {
            KitsugiStrings.get(if (e.isManga) R.string.profile_chapter_progress else R.string.profile_episode_progress, e.progress.toString())
        }
        else -> null
    }
    val score = if (e.score > 0) KitsugiStrings.get(R.string.profile_score_label, e.score.toString()) else null
    return listOfNotNull(label, progress, score).joinToString(" • ")
}

private fun historyText(e: MalActivityEntry): String {
    val n = e.progress
    return when {
        e.isManga && n != null -> KitsugiStrings.get(R.string.profile_chapter_read_history, n)
        n != null -> KitsugiStrings.get(R.string.profile_episode_watched_history, n)
        e.isManga -> KitsugiStrings.get(R.string.profile_status_read)
        else -> KitsugiStrings.get(R.string.profile_status_watched_past)
    }
}

/**
 * "Aktivite" sekmesi. MAL verisi (son güncellemeler / geçmiş) varsa onu gösterir,
 * yoksa [fallback] (yerel liste kayıtları) çizilir.
 */
@Composable
fun MalActivitySection(
    state: MalProfileState,
    accentColor: Color,
    onOpenMedia: (entry: MalActivityEntry) -> Unit,
    fallback: @Composable () -> Unit
) {
    if (state.recentUpdates.isEmpty() && state.history.isEmpty()) {
        if (state.isExtraLoading) LoadingHint() else fallback()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.recentUpdates.isNotEmpty()) {
            SectionTitle(stringResource(R.string.profile_last_updates))
            state.recentUpdates.forEach { e ->
                MalActivityCard(e, updateText(e), accentColor) { onOpenMedia(e) }
            }
        }
        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionTitle(stringResource(R.string.profile_history))
            state.history.take(40).forEach { e ->
                MalActivityCard(e, historyText(e), accentColor) { onOpenMedia(e) }
            }
        }
    }
}

/** MAL statistics view: exposes the distributions MAL actually provides, without inventing AniList-only fields. */
@Composable
fun MalStatisticsSection(
    state: MalProfileState,
    statsMediaType: Int,
    onStatsMediaTypeChange: (Int) -> Unit,
    statsSubTab: Int,
    onStatsSubTabChange: (Int) -> Unit,
    accentColor: Color,
    onGenreClick: (String) -> Unit,
    onStudioClick: ((Int, String, String?, String?) -> Unit)?,
    onVoiceActorClick: (Int, String, String?, String?) -> Unit
) {
    val isAnime = statsMediaType == 0
    val stats = if (isAnime) state.animeStats else state.mangaStats
    val scores = if (isAnime) state.animeScoreDistribution else state.mangaScoreDistribution
    val types = if (isAnime) state.animeTypeDistribution else state.mangaTypeDistribution
    val detailedStatuses = if (isAnime) state.animeStatusDistribution else state.mangaStatusDistribution
    val ratings = if (isAnime) state.animeRatingDistribution else state.mangaRatingDistribution
    val genres = if (isAnime) state.animeGenreStats else state.mangaGenreStats
    val studios = if (isAnime) state.animeStudioStats else state.mangaStudioStats
    val voiceActors = state.animeVoiceActorStats
    val selectedSubTab = if (!isAnime && statsSubTab >= 2) 1 else statsSubTab

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.SurfaceStrong)
                .padding(4.dp)
        ) {
            listOf(stringResource(R.string.profile_anime_entries), stringResource(R.string.profile_manga_entries)).forEachIndexed { index, label ->
                val selected = statsMediaType == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accentColor else Color.Transparent)
                        .clickable { onStatsMediaTypeChange(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) KitsugiColors.Background else KitsugiColors.TextMuted,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        val statTabs = listOf(
            0 to stringResource(R.string.profile_stats_general),
            1 to stringResource(R.string.profile_mal_distributions)
        ) +
            (if (isAnime && studios.isNotEmpty()) listOf(2 to stringResource(R.string.profile_mal_studios)) else emptyList()) +
            (if (isAnime && voiceActors.isNotEmpty()) listOf(3 to stringResource(R.string.profile_mal_voice_actors)) else emptyList())
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statTabs.forEach { (index, label) ->
                ProfileFilterChip(
                    text = label,
                    isSelected = selectedSubTab == index,
                    accentColor = accentColor,
                    onClick = { onStatsSubTabChange(index) }
                )
            }
        }

        when (selectedSubTab) {
            0 -> {
                if (stats == null) {
                    if (state.isExtraLoading) LoadingHint() else MalEmptyHint(stringResource(R.string.profile_mal_statistics_unavailable))
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(stringResource(R.string.profile_stat_total_entries), stats.count.toString())
                        StatCard(
                            if (isAnime) stringResource(R.string.profile_stat_episodes_watched)
                            else stringResource(R.string.profile_stat_chapters_read),
                            stats.episodesWatched.toString()
                        )
                        StatCard(
                            if (isAnime) stringResource(R.string.profile_stat_days_watched_short)
                            else stringResource(R.string.profile_mal_days_read),
                            String.format(KitsugiStrings.locale(), "%.1f", if (isAnime) state.animeDaysWatched else state.mangaDaysRead)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            if (isAnime) stringResource(R.string.profile_mal_rewatched)
                            else stringResource(R.string.profile_mal_reread),
                            (if (isAnime) state.animeRepeatCount else state.mangaRepeatCount).toString()
                        )
                        if (!isAnime) {
                            StatCard(stringResource(R.string.profile_stat_volumes_read), stats.minutesWatched.toString())
                        } else {
                            StatCard(stringResource(R.string.profile_status_planned), stats.planned.toString())
                        }
                        StatCard(stringResource(R.string.profile_average_score), String.format(KitsugiStrings.locale(), "%.2f", stats.meanScore))
                    }

                    val defaultStatusColor = KitsugiColors.TextMuted
                    val statusItems = if (detailedStatuses.isNotEmpty()) {
                        detailedStatuses.map { item ->
                            Triple(malStatisticStatusLabel(item.status, isAnime), item.count, malStatisticStatusColor(item.status, defaultStatusColor))
                        }
                    } else {
                        listOf(
                            Triple(if (isAnime) stringResource(R.string.profile_status_watching) else stringResource(R.string.profile_status_reading), stats.watching, Color(0xFF81C784)),
                            Triple(stringResource(R.string.profile_status_completed), stats.completed, Color(0xFF64B5F6)),
                            Triple(if (isAnime) stringResource(R.string.profile_status_plan_to_watch_short) else stringResource(R.string.profile_status_plan_to_read_short), stats.planned, Color(0xFFA1887F)),
                            Triple(stringResource(R.string.profile_status_on_hold), stats.paused, Color(0xFFFFB74D)),
                            Triple(stringResource(R.string.profile_status_dropped), stats.dropped, Color(0xFFE57373))
                        ).filter { it.second > 0 }
                    }
                    if (statusItems.isNotEmpty()) {
                        HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                        SectionTitle(stringResource(R.string.durum_dagilimi))
                        HorizontalStatsBar(statusItems)
                    }
                    if (scores.isNotEmpty()) {
                        HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                        SectionTitle(stringResource(R.string.detail_stat_score))
                        VerticalStatsBar(scores.map { it.score.toString() to it.count.toFloat() }, accentColor)
                    }
                    if (ratings.isNotEmpty()) {
                        HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                        SectionTitle(stringResource(R.string.profile_mal_rating_distribution))
                        VerticalStatsBar(ratings.map { it.format to it.count.toFloat() }, accentColor)
                    }
                }
            }
            1 -> {
                if (types.isEmpty() && genres.isEmpty()) {
                    if (state.isExtraLoading) LoadingHint() else MalEmptyHint(stringResource(R.string.profile_mal_statistics_unavailable))
                } else {
                    if (types.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.profile_mal_type_distribution))
                        VerticalStatsBar(types.map { it.format to it.count.toFloat() }, accentColor)
                    }
                    if (genres.isNotEmpty()) {
                        if (types.isNotEmpty()) HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                        SectionTitle(stringResource(R.string.profile_mal_top_genres))
                        genres.take(12).forEachIndexed { index, genre ->
                            PositionalStatItemCard(
                                rank = index + 1,
                                title = genre.name,
                                count = genre.count,
                                meanScore = genre.meanScore,
                                accentColor = accentColor,
                                onClick = { onGenreClick(genre.name) }
                            )
                        }
                    }
                }
            }
            2 -> {
                if (studios.isEmpty()) MalEmptyHint(stringResource(R.string.profile_mal_statistics_unavailable))
                else {
                    SectionTitle(stringResource(R.string.profile_mal_studios))
                    studios.take(12).forEachIndexed { index, studio ->
                        PositionalStatItemCard(
                            rank = index + 1,
                            title = studio.name,
                            count = studio.count,
                            meanScore = studio.meanScore,
                            accentColor = accentColor,
                            onClick = if (studio.id != null && onStudioClick != null) {
                                { onStudioClick(studio.id, "jikan", studio.name, studio.imageUrl) }
                            } else null
                        )
                    }
                }
            }
            3 -> {
                if (voiceActors.isEmpty()) MalEmptyHint(stringResource(R.string.profile_mal_statistics_unavailable))
                else {
                    SectionTitle(stringResource(R.string.profile_mal_voice_actors))
                    voiceActors.take(12).forEachIndexed { index, person ->
                        PositionalStatItemCard(
                            rank = index + 1,
                            title = person.name,
                            count = person.count,
                            meanScore = person.meanScore,
                            imageUrl = person.imageUrl,
                            accentColor = accentColor,
                            onClick = if (person.id != null) {
                                { onVoiceActorClick(person.id, "jikan", person.name, person.imageUrl) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

private fun malStatisticStatusLabel(status: String, isAnime: Boolean): String = when (status.lowercase(Locale.US).replace('_', ' ')) {
    "watching", "current" -> KitsugiStrings.get(if (isAnime) R.string.profile_status_watching else R.string.profile_status_reading)
    "reading" -> KitsugiStrings.get(R.string.profile_status_reading)
    "completed", "complete" -> KitsugiStrings.get(R.string.profile_status_completed)
    "on-hold", "on hold", "paused" -> KitsugiStrings.get(R.string.profile_status_on_hold)
    "dropped" -> KitsugiStrings.get(R.string.profile_status_dropped)
    "plan to watch", "planning" -> KitsugiStrings.get(if (isAnime) R.string.profile_status_plan_to_watch_short else R.string.profile_status_plan_to_read_short)
    "plan to read" -> KitsugiStrings.get(R.string.profile_status_plan_to_read_short)
    else -> status
}

private fun malStatisticStatusColor(status: String, fallbackColor: Color = Color(0xFF9E9E9E)): Color = when (status.lowercase(Locale.US).replace('_', ' ')) {
    "watching", "reading", "current" -> Color(0xFF81C784)
    "completed", "complete" -> Color(0xFF64B5F6)
    "plan to watch", "plan to read", "planning" -> Color(0xFFA1887F)
    "on-hold", "on hold", "paused" -> Color(0xFFFFB74D)
    "dropped" -> Color(0xFFE57373)
    else -> fallbackColor
}

@Composable
private fun MalEmptyHint(text: String) {
    Text(
        text = text,
        color = KitsugiColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
    )
}

@Composable
internal fun MalLoadMoreRetry(accentColor: Color, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onRetry)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_mal_load_more_retry),
            color = accentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MalReviewsSection(
    state: MalProfileState,
    accentColor: Color,
    onRetry: (() -> Unit)? = null,
    onOpenMedia: (MalReview) -> Unit
) {
    when {
        state.reviews.isEmpty() && state.isExtraLoading -> LoadingHint()
        state.reviews.isEmpty() && state.reviewsLoadMoreFailed && onRetry != null -> MalLoadMoreRetry(accentColor, onRetry)
        state.reviews.isEmpty() -> MalEmptyHint(stringResource(R.string.profile_mal_no_reviews))
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.profile_mal_reviews))
            state.reviews.forEach { review ->
                MalReviewCard(review, accentColor, onOpenMedia)
            }
            if (state.isLoadingMoreReviews) LoadingHint()
            if (state.reviewsLoadMoreFailed && onRetry != null) MalLoadMoreRetry(accentColor, onRetry)
        }
    }
}

@Composable
private fun MalReviewCard(
    review: MalReview,
    accentColor: Color,
    onOpenMedia: (MalReview) -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = { onOpenMedia(review) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (!review.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = review.imageUrl,
                    contentDescription = review.title,
                    modifier = Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { openMalUrl(context, review.url) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.OpenInNew, contentDescription = stringResource(R.string.cd_open_gallery), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.profile_mal_review_score_helpful, review.score, review.helpfulCount),
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (review.textPreview.isNotBlank()) {
                    Text(
                        review.textPreview,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                malRelativeTime(review.dateIso)?.let {
                    Text(it, color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MalRecommendationsSection(
    state: MalProfileState,
    accentColor: Color,
    onRetry: (() -> Unit)? = null,
    onOpenMedia: (MalRecommendation) -> Unit
) {
    when {
        state.recommendations.isEmpty() && state.isExtraLoading -> LoadingHint()
        state.recommendations.isEmpty() && state.recommendationsLoadMoreFailed && onRetry != null -> MalLoadMoreRetry(accentColor, onRetry)
        state.recommendations.isEmpty() -> MalEmptyHint(stringResource(R.string.profile_mal_no_recommendations))
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.profile_mal_recommendations))
            state.recommendations.forEach { recommendation ->
                MalRecommendationCard(recommendation, accentColor, onOpenMedia)
            }
            if (state.isLoadingMoreRecommendations) LoadingHint()
            if (state.recommendationsLoadMoreFailed && onRetry != null) MalLoadMoreRetry(accentColor, onRetry)
        }
    }
}

@Composable
private fun MalRecommendationCard(
    recommendation: MalRecommendation,
    accentColor: Color,
    onOpenMedia: (MalRecommendation) -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = { onOpenMedia(recommendation) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (!recommendation.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = recommendation.imageUrl,
                    contentDescription = recommendation.title,
                    modifier = Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        recommendation.title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { openMalUrl(context, recommendation.url) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.OpenInNew, contentDescription = stringResource(R.string.cd_open_gallery), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                if (recommendation.recommendationText.isNotBlank()) {
                    Text(
                        recommendation.recommendationText,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                malRelativeTime(recommendation.dateIso)?.let {
                    Text(it, color = accentColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MalClubsSection(state: MalProfileState, accentColor: Color, onRetry: (() -> Unit)? = null) {
    when {
        state.clubs.isEmpty() && state.isExtraLoading -> LoadingHint()
        state.clubs.isEmpty() && state.clubsLoadMoreFailed && onRetry != null -> MalLoadMoreRetry(accentColor, onRetry)
        state.clubs.isEmpty() -> MalEmptyHint(stringResource(R.string.profile_mal_no_clubs))
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.profile_mal_clubs))
            state.clubs.forEach { club -> MalClubCard(club) }
            if (state.isLoadingMoreClubs) LoadingHint()
            if (state.clubsLoadMoreFailed && onRetry != null) MalLoadMoreRetry(accentColor, onRetry)
        }
    }
}

@Composable
private fun MalClubCard(club: MalClub) {
    val context = LocalContext.current
    Card(
        onClick = { openMalUrl(context, club.url) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!club.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = club.imageUrl,
                    contentDescription = club.name,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(club.name, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val metadata = listOfNotNull(
                    club.category?.takeIf { it.isNotBlank() },
                    if (club.memberCount > 0) stringResource(R.string.profile_mal_club_members, club.memberCount) else null
                ).joinToString(" • ")
                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(metadata, color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.OpenInNew, contentDescription = stringResource(R.string.cd_open_gallery), tint = KitsugiColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
