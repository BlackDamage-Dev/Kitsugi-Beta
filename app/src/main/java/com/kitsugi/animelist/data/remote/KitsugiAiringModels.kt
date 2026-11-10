package com.kitsugi.animelist.data.remote

import java.util.Calendar

/**
 * Tek bir anime bölümünün yayın takvimi kaydı.
 * AniList `airingSchedules` endpoint'inden gelir.
 */
data class AiringEntry(
    val aniListId: Int,
    val malId: Int?,
    /** Romaji başlık (tercih edilen) */
    val title: String,
    val titleEnglish: String?,
    val titleNative: String?,
    val coverUrl: String?,
    /** Bu bölümün numarası (1-indexed) */
    val episode: Int,
    /** Unix epoch saniye cinsinden yayın zamanı */
    val airingAt: Long,
    /**
     * Haftanın günü: Calendar.MONDAY (2) … Calendar.SUNDAY (1).
     * Kullanıcının yerel saat dilimine göre hesaplanır.
     */
    val dayOfWeek: Int,
    val averageScore: Int? = null,
    val countryOfOrigin: String? = null,
    /** +18 içerik mi? (AniList `media.isAdult`) — bulanıklık için kullanılır */
    val isAdult: Boolean = false,
    /**
     * Kaynağın kendisi: "anilist" (varsayılan) veya "tmdb".
     * "Tümü" (birleşik takvim) modunda aynı haftada hem AniList hem TMDB kaydı
     * bulunabilir → öğe tıklanınca detay rotasının doğru kimlik uzayından
     * çözülmesi için kayıt kendi kaynağını taşır.
     */
    val source: String = "anilist"
) {
    /** Yayın saatini okunabilir "HH:mm" formatında döndürür. */
    fun formattedTime(): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = airingAt * 1000L
        }
        return String.format(
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    /** Bölümün yayınlanıp yayınlanmadığı (şu anki zamana göre). */
    fun hasAired(): Boolean = airingAt * 1000L < System.currentTimeMillis()

    /**
     * Başlık tercihi ve yapım ülkesine göre görüntülenecek başlığı hesaplar.
     * Kore ("KR") ve Çin ("CN") yapımı anime/donghua/awe için "Dogul Wang" gibi anlamsız
     * harf çevirileri yerine İngilizce başlık ("Tomb Raider King") önceliklendirilir.
     */
    fun getDisplayTitle(titleLanguage: String = "ENGLISH"): String {
        val isNonJapanese = countryOfOrigin != null && !countryOfOrigin.equals("JP", ignoreCase = true)
        return when {
            isNonJapanese && !titleEnglish.isNullOrBlank() -> titleEnglish
            titleLanguage == "ENGLISH" -> titleEnglish?.takeIf { it.isNotBlank() } ?: title
            titleLanguage == "NATIVE" -> titleNative?.takeIf { it.isNotBlank() } ?: title
            // TMDB kaynaklı kayıtlarda CJK başlık yerine Latin alternatif gösterilir
            else -> com.kitsugi.animelist.utils.MediaTitleResolver.latin(title)
                ?: com.kitsugi.animelist.utils.MediaTitleResolver.latin(titleEnglish)
                ?: title
        }
    }

    fun toJikanSearchResult(preferredSource: String? = null): JikanSearchResult {
        val finalSource = when (preferredSource) {
            "jikan" -> if (malId != null) "jikan" else "anilist"
            "tmdb" -> "tmdb"
            // Birleşik ("Tümü") takvim: kayıt hangi kaynaktan geldiyse o kimlik
            // uzayını kullan — TMDB kaydı tmdb id'siyle, AniList kaydı mal/anilist
            // id'siyle detay sayfasına çözülür.
            "all" -> if (source.equals("tmdb", true)) "tmdb" else "anilist"
            else -> "anilist"
        }
        val finalId = if (finalSource == "tmdb") aniListId else if (finalSource == "jikan") malId!! else (malId ?: aniListId)
        val finalType = if (finalSource == "tmdb") {
            if (episode == 0) com.kitsugi.animelist.model.MediaType.Movie else com.kitsugi.animelist.model.MediaType.TvShow
        } else {
            com.kitsugi.animelist.model.MediaType.Anime
        }
        val isNonJapanese = countryOfOrigin != null && !countryOfOrigin.equals("JP", ignoreCase = true)
        val effectiveTitle = if (isNonJapanese && !titleEnglish.isNullOrBlank()) {
            titleEnglish
        } else {
            com.kitsugi.animelist.utils.MediaTitleResolver.latin(title)
                ?: com.kitsugi.animelist.utils.MediaTitleResolver.latin(titleEnglish)
                ?: title
        }
        return JikanSearchResult(
            malId = finalId,
            title = effectiveTitle,
            subtitle = titleEnglish ?: "",
            type = finalType,
            total = null,
            score = averageScore,
            isAdult = isAdult,
            imageUrl = coverUrl,
            year = null,
            source = finalSource,
            realMalId = malId,
            titleEnglish = titleEnglish,
            titleJapanese = titleNative,
            nextAiringEpisode = "$episode|$airingAt",
            tmdbId = if (finalSource == "tmdb") finalId else null
        )
    }
}
