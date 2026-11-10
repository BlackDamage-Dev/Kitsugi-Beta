package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
internal fun localizedSourceSearchLabel(label: String): String {
    val resourceId = when (label) {
        "📺 TV Dizi" -> R.string.search_chip_tv_series
        "📺 TV Dizileri" -> R.string.search_chip_tv_series_plural
        "📺 Dizi" -> R.string.search_chip_tv_show
        "🎬 Film" -> R.string.search_chip_movie
        "🎬 Filmler" -> R.string.search_chip_movies
        "⏱️ Kısa Dizi" -> R.string.search_chip_short_series
        "✨ Özel" -> R.string.search_chip_special
        "🏁 Tamamlandı" -> R.string.search_chip_finished
        "⏳ Yakında" -> R.string.search_chip_upcoming
        "🚫 İptal" -> R.string.search_chip_cancelled
        "🟢 Devam Ediyor" -> R.string.search_chip_ongoing
        "🎵 Müzik" -> R.string.search_chip_music
        "🇨🇳 Çin" -> R.string.search_chip_china
        "👶 Tüm Yaşlar (G)" -> R.string.search_chip_all_ages
        "🧒 Çocuklar (PG)" -> R.string.search_chip_children
        "🧑 13+ (PG-13)" -> R.string.search_chip_rating_13
        "🔞 17+ (R)" -> R.string.search_chip_rating_17
        "🇹🇷 Türkiye" -> R.string.search_country_turkey
        "🇺🇸 ABD" -> R.string.search_country_us
        "🇰🇷 G. Kore" -> R.string.search_country_korea
        "🇰🇷 Kore" -> R.string.search_country_korea_short
        "🇬🇧 Birleşik Krallık" -> R.string.search_country_uk
        "🇯🇵 Japonya" -> R.string.search_country_japan
        "🇫🇷 Fransa" -> R.string.search_country_france
        "🇰🇷 Manhwa" -> R.string.search_chip_manhwa
        "🔥 Bugün" -> R.string.search_period_today
        "📅 Bu Hafta" -> R.string.search_period_week
        "🗓️ Bu Ay" -> R.string.search_period_month
        "👥 Popülerlik" -> R.string.search_ui_popularity
        "🔥 Trendler" -> R.string.search_ui_trending
        "🔥 Trend Olanlar" -> R.string.search_ui_trending_items
        "💖 Favoriler" -> R.string.search_ui_favorites
        "📅 Yeni Çıkanlar" -> R.string.search_ui_new_releases
        "📅 Yeni Eklenenler" -> R.string.search_ui_new_additions
        "📅 En Yeni Eklenenler" -> R.string.search_ui_newest_additions
        "🔤 İsim: A-Z" -> R.string.search_ui_title_az
        "🔥 Popülerlik" -> R.string.search_sort_popularity_chip
        "🔤 İsim (A-Z)" -> R.string.search_sort_title_asc
        "🔤 İsim (Z-A)" -> R.string.search_sort_title_desc
        "📅 Yayın Tarihi" -> R.string.search_ui_air_date
        "💰 Hasılat" -> R.string.search_ui_revenue
        "🔤 İsim" -> R.string.search_ui_title
        "🏆 Sıralama" -> R.string.search_ui_ranking
        "👥 Oylar" -> R.string.search_ui_votes
        "🎯 Eşleşme" -> R.string.search_ui_match
        "🔥 İlgi" -> R.string.search_ui_interest
        "🏆 Sıra" -> R.string.search_ui_rank
        "👥 En Popüler" -> R.string.search_ui_most_popular
        "🔥 Şimdi Trend Olanlar" -> R.string.search_ui_trending_now
        "💖 En Çok Favorilenenler" -> R.string.search_ui_most_favorited
        "📅 En Yeniler (Yayın Tarihi)" -> R.string.search_ui_newest_by_release
        "📜 En Eskiler (Klasikler)" -> R.string.search_ui_oldest_classics
        "👥 Popülerlik (Üye Sayısı)" -> R.string.search_ui_popularity_members
        "📅 Yayın Tarihi: Yeniden Eskiye" -> R.string.search_ui_release_date_new_old
        "📅 Çıkış Tarihi: En Yeniler" -> R.string.search_ui_release_date_newest
        "📜 Çıkış Tarihi: En Eskiler" -> R.string.search_ui_release_date_oldest
        "💰 Gişe / Hasılat" -> R.string.search_ui_box_office
        "👥 Kullanıcı Sayısı (Popülerlik)" -> R.string.search_ui_user_count
        "🏆 Genel Sıralama" -> R.string.search_ui_overall_ranking
        "👥 Oy Sayısı" -> R.string.search_ui_vote_count
        "🎯 En İyi Eşleşme" -> R.string.search_ui_best_match
        "🔥 İlgi (收藏热度)" -> R.string.search_ui_interest_rank
        "🏆 Sıra (Rank)" -> R.string.search_ui_rank_label
        "📅 Tüm Yıllar" -> R.string.search_all_years
        "🔞 +18 Dahil" -> R.string.search_include_adult
        "Tümü" -> R.string.search_scope_all
        "TV Dizisi" -> R.string.search_scope_tv
        "Film" -> R.string.media_type_movie
        "Özel Bölüm" -> R.string.search_chip_special
        "Müzik Videosu" -> R.string.search_filter_music_video
        "Yayınlanıyor" -> R.string.search_status_releasing
        "Tamamlandı" -> R.string.search_status_finished
        "Yakında" -> R.string.search_status_upcoming
        "❤️ Favoriler" -> R.string.search_ui_favorites
        "📅 Çıkış Tarihi" -> R.string.search_ui_release_date_newest
        "🔤 Başlık" -> R.string.search_ui_title
        "⬇️ Azalan (Yüksekten Düşüğe)" -> R.string.search_sort_descending
        "⬆️ Artan (Düşükten Yükseğe)" -> R.string.search_sort_ascending
        "Genel (G)" -> R.string.search_chip_all_ages
        "Çocuk (PG)" -> R.string.search_chip_children
        "Genç (PG-13)" -> R.string.search_chip_rating_13
        "R+ (Hafif Çıplaklık)" -> R.string.search_rating_r_plus
        "Rx (Hentai)" -> R.string.search_rating_rx
        "50+ Oy" -> R.string.search_vote_count_50
        "200+ Oy" -> R.string.search_vote_count_200
        "1.000+ Oy" -> R.string.search_vote_count_1000
        "Rehberlik (PG)" -> R.string.search_rating_guidance
        "Devam Eden" -> R.string.search_status_ongoing
        "Duyurulmadı" -> R.string.search_status_not_released
        "Kısa (< 10 dk)" -> R.string.search_duration_short
        "Standart (10-30 dk)" -> R.string.search_duration_standard
        "Uzun (> 30 dk / Film)" -> R.string.search_duration_long
        "Özel" -> R.string.search_chip_special
        "Novel" -> R.string.search_subtype_novel
        "🔥 Trend" -> R.string.search_ui_trending
        "🔢 Bölüm Sayısı" -> R.string.search_filter_episode_count
        "🆕 En Yeni" -> R.string.search_ui_new_releases
        "⏳ En Eski" -> R.string.search_ui_oldest_classics
        "✨ Son Eklenen" -> R.string.search_ui_new_additions
        "Yayında (Ongoing)" -> R.string.search_status_airing
        "Tamamlandı (Released)" -> R.string.search_status_finished
        "Yakında (Anons)" -> R.string.search_status_upcoming
        "17+ (R)" -> R.string.search_rating_17_plus
        else -> return SearchTranslation.displayLabel(label)
    }
    return stringResource(resourceId)
}

// ─────────────────────────────────────────────────────────────────────────────
// MOTOR HIZLI ÇİP BİLEŞENLERİ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AniListQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.aniListSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Formatlar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formats = listOf(
                "TV" to "📺 TV Dizi",
                "MOVIE" to "🎬 Film",
                "TV_SHORT" to "⏱️ Kısa Dizi",
                "OVA" to "💿 OVA",
                "ONA" to "🌐 ONA",
                "SPECIAL" to "✨ Özel",
                "MANGA" to "📖 Manga",
                "NOVEL" to "📚 Light Novel",
                "ONE_SHOT" to "🎯 One-Shot"
            )
            formats.forEach { (key, label) ->
                val isSelected = filters.formats.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newFormats = if (isSelected) filters.formats - key else filters.formats + key
                        viewModel.updateAniListFilters(filters.copy(formats = newFormats))
                    }
                )
            }
        }

        // Durumlar & Köken
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "RELEASING" to "🟢 Devam Ediyor",
                "FINISHED" to "🏁 Tamamlandı",
                "NOT_YET_RELEASED" to "⏳ Yakında",
                "CANCELLED" to "🚫 İptal"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStatuses = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateAniListFilters(filters.copy(statuses = newStatuses))
                    }
                )
            }

            // Ülke
            val countries = listOf(
                "JP" to "🇯🇵 Japonya",
                "KR" to "🇰🇷 Kore",
                "CN" to "🇨🇳 Çin"
            )
            countries.forEach { (code, label) ->
                val isSelected = filters.country == code
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateAniListFilters(filters.copy(country = if (isSelected) null else code))
                    }
                )
            }
        }
    }
}

@Composable
fun MalQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.malSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formats = listOf(
                "tv" to "📺 TV", "movie" to "🎬 Film", "ova" to "💿 OVA",
                "special" to "✨ Özel", "ona" to "🌐 ONA", "music" to "🎵 Müzik",
                "manga" to "📖 Manga", "novel" to "📚 Light Novel"
            )
            formats.forEach { (key, label) ->
                val isSelected = filters.type == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(type = if (isSelected) null else key))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "airing" to "🟢 Devam Ediyor", "complete" to "🏁 Tamamlandı", "upcoming" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.status == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(status = if (isSelected) null else key))
                    }
                )
            }

            val ratings = listOf(
                "g" to "👶 Tüm Yaşlar (G)",
                "pg" to "🧒 Çocuklar (PG)",
                "pg13" to "🧑 13+ (PG-13)",
                "r17" to "🔞 17+ (R)"
            )
            ratings.forEach { (key, label) ->
                val isSelected = filters.rating == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(rating = if (isSelected) null else key))
                    }
                )
            }
        }
    }
}

@Composable
fun TmdbQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.tmdbSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val types = listOf(
                true to "🎬 Filmler",
                false to "📺 TV Dizileri"
            )
            types.forEach { (isMovie, label) ->
                val isSelected = filters.isMovie == isMovie
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(isMovie = isMovie))
                    }
                )
            }

            val countries = listOf(
                "TR" to "🇹🇷 Türkiye", "US" to "🇺🇸 ABD", "KR" to "🇰🇷 G. Kore",
                "JP" to "🇯🇵 Japonya", "GB" to "🇬🇧 Birleşik Krallık", "FR" to "🇫🇷 Fransa"
            )
            countries.forEach { (code, label) ->
                val isSelected = filters.originCountry == code
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(originCountry = if (isSelected) null else code))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val scores = listOf(
                7.0 to "⭐ 7.0+", 8.0 to "🌟 8.0+", 8.5 to "🏆 8.5+"
            )
            scores.forEach { (minSc, label) ->
                val isSelected = filters.minScore == minSc
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(minScore = if (isSelected) null else minSc))
                    }
                )
            }

            val years = listOf(
                2024 to "📅 2024", 2023 to "📅 2023", 2020 to "📅 2020+"
            )
            years.forEach { (yr, label) ->
                val isSelected = filters.startYear == yr
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(startYear = if (isSelected) null else yr))
                    }
                )
            }
        }
    }
}

@Composable
fun ShikimoriQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.shikimoriSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val kinds = listOf(
                "tv" to "📺 TV", "movie" to "🎬 Film", "ova" to "💿 OVA",
                "ona" to "🌐 ONA", "manga" to "📖 Manga", "manhwa" to "🇰🇷 Manhwa",
                "light_novel" to "📚 Light Novel"
            )
            kinds.forEach { (key, label) ->
                val isSelected = filters.kinds.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newKinds = if (isSelected) filters.kinds - key else filters.kinds + key
                        viewModel.updateShikimoriFilters(filters.copy(kinds = newKinds))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "ongoing" to "🟢 Devam Ediyor", "released" to "🏁 Tamamlandı", "anons" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStatuses = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateShikimoriFilters(filters.copy(statuses = newStatuses))
                    }
                )
            }
        }
    }
}

@Composable
fun KitsuQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.kitsuSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val subtypes = listOf(
                "TV" to "📺 TV", "movie" to "🎬 Film", "OVA" to "💿 OVA",
                "ONA" to "🌐 ONA", "special" to "✨ Özel", "manga" to "📖 Manga", "novel" to "📚 Novel"
            )
            subtypes.forEach { (key, label) ->
                val isSelected = filters.subtypes.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newSubs = if (isSelected) filters.subtypes - key else filters.subtypes + key
                        viewModel.updateKitsuFilters(filters.copy(subtypes = newSubs))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "current" to "🟢 Devam Ediyor", "finished" to "🏁 Tamamlandı", "upcoming" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStats = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateKitsuFilters(filters.copy(statuses = newStats))
                    }
                )
            }
        }
    }
}

@Composable
fun SimklQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.simklSpecificFilters

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val periods = listOf(
            "today" to "🔥 Bugün",
            "week" to "📅 Bu Hafta",
            "month" to "🗓️ Bu Ay"
        )
        periods.forEach { (period, label) ->
            val isSelected = filters.trendingPeriod == period
            QuickFilterToggleChip(
                label = label,
                selected = isSelected,
                onClick = {
                    viewModel.updateSimklFilters(filters.copy(trendingPeriod = period))
                }
            )
        }

        val types = listOf(
            "anime" to "⚡ Anime",
            "tv" to "📺 Dizi",
            "movies" to "🎬 Film"
        )
        types.forEach { (typ, label) ->
            val isSelected = filters.subtype == typ
            QuickFilterToggleChip(
                label = label,
                selected = isSelected,
                onClick = {
                    viewModel.updateSimklFilters(filters.copy(subtype = typ))
                }
            )
        }
    }
}

@Composable
private fun AllEnginesQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.search_all_sources_progress),
            style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GENEL YARDIMCI COMPOSABLE VE METODLAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickFilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.2f)
                else KitsugiColors.SurfaceElevated.copy(alpha = 0.6f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        KitsugiEmojiLabel(
            label = localizedSourceSearchLabel(label),
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Verilen motor için varsayılan sıralama anahtarı.
 * Özet çipi ve filtre sheet'i "varsayılandan farklı mı?" kontrolünde kullanır.
 */
fun defaultSortKeyForEngine(engine: SearchSourceEngine): String = when (engine) {
    SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> "POPULARITY_DESC"
    SearchSourceEngine.MAL -> "popularity"
    SearchSourceEngine.TMDB -> "popularity.desc"
    SearchSourceEngine.SHIKIMORI -> "popularity"
    SearchSourceEngine.KITSU -> "trending"
    SearchSourceEngine.SIMKL -> "rank"
    SearchSourceEngine.BANGUMI -> "match"
}

fun getSortDisplayLabel(uiState: SearchUiState): String {
    return when (uiState.selectedEngine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> {
            when (uiState.aniListSpecificFilters.sort) {
                "POPULARITY_DESC" -> "👥 Popülerlik"
                "SCORE_DESC" -> "⭐ Puan: Yüksek"
                "TRENDING_DESC" -> "🔥 Trendler"
                "FAVOURITES_DESC" -> "💖 Favoriler"
                "START_DATE_DESC" -> "📅 Yeni Çıkanlar"
                "START_DATE_ASC" -> "📜 En Eskiler (Klasikler)"
                "TITLE_ROMAJI" -> "🔤 İsim: A-Z"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.MAL -> {
            when (uiState.malSpecificFilters.orderBy) {
                "members" -> "👥 Popülerlik"
                "score" -> "⭐ Puan"
                "title" -> "🔤 İsim: A-Z"
                "start_date" -> "📅 Yayın Tarihi"
                "favorites" -> "💖 Favoriler"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.TMDB -> {
            when (uiState.tmdbSpecificFilters.sortBy) {
                "popularity.desc" -> "👥 Popülerlik"
                "vote_average.desc" -> "⭐ Puan"
                "primary_release_date.desc", "first_air_date.desc" -> "📅 Yeni Çıkanlar"
                "primary_release_date.asc", "first_air_date.asc" -> "📜 En Eskiler (Klasikler)"
                "revenue.desc" -> "💰 Hasılat"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.SHIKIMORI -> {
            when (uiState.shikimoriSpecificFilters.order) {
                "popularity" -> "👥 Popülerlik"
                "ranked" -> "⭐ Puan"
                "aired_on" -> "📅 Yayın Tarihi"
                "name" -> "🔤 İsim"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.KITSU -> {
            when (uiState.kitsuSpecificFilters.sort) {
                "trending" -> "🔥 Trendler"
                "-userCount" -> "👥 Popülerlik"
                "-averageRating" -> "⭐ Puan"
                "-createdAt" -> "📅 Yeni Eklenenler"
                "createdAt" -> "📜 En Eskiler (Klasikler)"
                else -> "🔥 Trendler"
            }
        }
        SearchSourceEngine.SIMKL -> {
            when (uiState.simklSpecificFilters.sort) {
                "rank" -> "🏆 Sıralama"
                "votes" -> "👥 Oylar"
                "rating" -> "⭐ Puan"
                else -> "🏆 Sıralama"
            }
        }
        SearchSourceEngine.BANGUMI -> {
            when (uiState.bangumiSpecificFilters.sort) {
                "match" -> "🎯 Eşleşme"
                "heat" -> "🔥 İlgi"
                "rank" -> "🏆 Sıra"
                "score" -> "⭐ Puan"
                else -> "🎯 Eşleşme"
            }
        }
    }
}

fun getCurrentSortKey(uiState: SearchUiState): String {
    return when (uiState.selectedEngine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> uiState.aniListSpecificFilters.sort
        SearchSourceEngine.MAL -> uiState.malSpecificFilters.orderBy ?: "members"
        SearchSourceEngine.TMDB -> uiState.tmdbSpecificFilters.sortBy
        SearchSourceEngine.SHIKIMORI -> uiState.shikimoriSpecificFilters.order
        SearchSourceEngine.KITSU -> uiState.kitsuSpecificFilters.sort
        SearchSourceEngine.SIMKL -> uiState.simklSpecificFilters.sort
        SearchSourceEngine.BANGUMI -> uiState.bangumiSpecificFilters.sort
    }
}

fun applyEngineSort(engine: SearchSourceEngine, sortKey: String, viewModel: SearchViewModel) {
    // Sıralama artık ViewModel üzerinden uygulanır: seçilen anahtar hem motorun
    // paketine hem de kanonik (ortak) sıralamaya yazılır. Önceden yalnızca
    // AniList paketi güncelleniyordu; "Tümü" aramasında diğer 6 kaynak kendi
    // popülerlik sırasında kalıyor ve "Tümünü Gör" sayfası seçilen sıralamayı
    // devralmıyordu.
    viewModel.applySortKey(engine, sortKey)
}

/**
 * Motor bazlı sıralama seçenekleri listesi. Hem sıralama picker sheet'i
 * hem de birleşik filtre sheet'i (SourceEngineFilterSheet) kullanır.
 */
fun sortOptionsForEngine(engine: SearchSourceEngine): List<Pair<String, String>> = when (engine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> listOf(
            "POPULARITY_DESC" to "👥 En Popüler",
            "SCORE_DESC" to "⭐ En Yüksek Puanlı",
            "TRENDING_DESC" to "🔥 Şimdi Trend Olanlar",
            "FAVOURITES_DESC" to "💖 En Çok Favorilenenler",
            "START_DATE_DESC" to "📅 En Yeniler (Yayın Tarihi)",
            "START_DATE_ASC" to "📜 En Eskiler (Klasikler)",
            "TITLE_ROMAJI" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.MAL -> listOf(
            "members" to "👥 Popülerlik (Üye Sayısı)",
            "score" to "⭐ Puan: Yüksekten Düşüğe",
            "favorites" to "💖 En Çok Favorilenenler",
            "start_date" to "📅 Yayın Tarihi: Yeniden Eskiye",
            "title" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.TMDB -> listOf(
            "popularity.desc" to "👥 Popülerlik",
            "vote_average.desc" to "⭐ Puan: Yüksekten Düşüğe",
            "primary_release_date.desc" to "📅 Çıkış Tarihi: En Yeniler",
            "primary_release_date.asc" to "📜 Çıkış Tarihi: En Eskiler",
            "revenue.desc" to "💰 Gişe / Hasılat"
        )
        SearchSourceEngine.SHIKIMORI -> listOf(
            "popularity" to "👥 Popülerlik",
            "ranked" to "⭐ Puan Sıralaması",
            "aired_on" to "📅 Yayın Tarihi",
            "name" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.KITSU -> listOf(
            "trending" to "🔥 Trend Olanlar",
            "-userCount" to "👥 Kullanıcı Sayısı (Popülerlik)",
            "-averageRating" to "⭐ Ortalama Puan",
            "-createdAt" to "📅 En Yeni Eklenenler"
        )
        SearchSourceEngine.SIMKL -> listOf(
            "rank" to "🏆 Genel Sıralama",
            "votes" to "👥 Oy Sayısı",
            "rating" to "⭐ Puan"
        )
        SearchSourceEngine.BANGUMI -> listOf(
            "match" to "🎯 En İyi Eşleşme",
            "heat" to "🔥 İlgi (收藏热度)",
            "rank" to "🏆 Sıra (Rank)",
            "score" to "⭐ Puan: Yüksekten Düşüğe"
        )
    }

/**
 * Sıralama seçenekleri listesi gövdesi — picker sheet ve birleşik filtre
 * sheet'i tarafından ortak kullanılır.
 */
@Composable
fun SortOptionsContent(
    engine: SearchSourceEngine,
    sortOptions: List<Pair<String, String>>,
    currentSort: String,
    onSelectSort: (String) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Kaynak emojisi yerine platformun orijinal logosu.
            KitsugiPlatformLogo(
                platformId = engine.id,
                size = 26.dp,
                fallbackTint = KitsugiColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.inline_1_s_sorting_options_f94d7e1, engine.localizedLabel()),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        sortOptions.forEach { (key, label) ->
            val isSelected = key == currentSort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.15f)
                        else KitsugiColors.SurfaceElevated.copy(alpha = 0.4f)
                    )
                    .clickable { onSelectSort(key) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KitsugiEmojiLabel(
                    label = localizedSourceSearchLabel(label),
                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Check,
                        contentDescription = stringResource(R.string.secili),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎌 Bangumi (bgm.tv) Hızlı Filtre Çipleri
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bangumi için hızlı filtre çipleri.
 *
 * Bangumi'nin `POST /v0/search/subjects` gövdesi Shikimori/AniList kadar zengin
 * filtre alanı sunmaz; kullanılabilir olanlar sıralama, etiket (tag), yayın yılı
 * aralığı, puan alt sınırı ve NSFW'dir.
 */
@Composable
fun BangumiQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.bangumiSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val sorts = listOf(
                "match" to "🎯 Eşleşme", "heat" to "🔥 İlgi", "rank" to "🏆 Sıra", "score" to "⭐ Puan"
            )
            sorts.forEach { (key, label) ->
                QuickFilterToggleChip(
                    label = label,
                    selected = filters.sort == key,
                    onClick = { viewModel.updateBangumiFilters(filters.copy(sort = key)) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Tam yıl seçenekleri yearFrom == yearTo, "+" seçenekleri yalnız alt sınır koyar.
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val years = buildList<Pair<Int?, Int?>> {
                add(null to null)
                (0 until 4).forEach { add((currentYear - it) to (currentYear - it)) }
                add((currentYear - 6) to null)
                add((currentYear - 16) to null)
                add((currentYear - 26) to null)
            }
            val yearLabels = listOf(
                "📅 Tüm Yıllar",
                currentYear.toString(), (currentYear - 1).toString(), (currentYear - 2).toString(),
                (currentYear - 3).toString(), "${currentYear - 6}+", "${currentYear - 16}+", "${currentYear - 26}+"
            )
            years.forEachIndexed { index, (from, to) ->
                QuickFilterToggleChip(
                    label = yearLabels.getOrElse(index) { "?" },
                    selected = filters.yearFrom == from && filters.yearTo == to,
                    onClick = { viewModel.updateBangumiFilters(filters.copy(yearFrom = from, yearTo = to)) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val scores = listOf(null to "⭐ Tüm Puanlar", 6 to "6+", 7 to "7+", 8 to "8+", 9 to "9+")
            scores.forEach { (score, label) ->
                QuickFilterToggleChip(
                    label = label,
                    selected = filters.minScore == score,
                    onClick = { viewModel.updateBangumiFilters(filters.copy(minScore = score)) }
                )
            }
            QuickFilterToggleChip(
                label = stringResource(R.string.search_include_adult),
                selected = filters.nsfw,
                onClick = { viewModel.updateBangumiFilters(filters.copy(nsfw = !filters.nsfw)) }
            )
        }
    }
}
