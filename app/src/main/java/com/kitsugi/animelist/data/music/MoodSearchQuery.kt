package com.kitsugi.animelist.data.music

/**
 * 🔎 Duygu profilinden **çevrimiçi arama metni** üretir.
 *
 * Neden gerekli: çevrimiçi kaynakların (Openverse, Internet Archive, Audius,
 * Jamendo, Freesound) etiketleri İngilizce. "Senin İçin" sekmesinde boş
 * sorguyla `"ambient"` aramak, içerikle ilgisi olmayan rastgele sonuçlar
 * getiriyordu. Burada içeriğin [MoodProfile]'ı kısa bir İngilizce sorguya
 * çevriliyor; böylece otomatik başlatma ve öneri listesi okunan içeriğe
 * gerçekten uyuyor.
 *
 * Tasarım: kural tabanlı (LLM/ağ yok), deterministik ve çevrimdışı
 * hesaplanabilir. Sorgu kısa tutulur (2–3 kelime) çünkü arşiv API'leri
 * uzun sorgularda isabeti düşürüyor.
 */
object MoodSearchQuery {

    /**
     * Birincil sorgu (kaynaklarda en yüksek isabet oranına sahip ifadeler).
     */
    fun forMood(mood: MoodProfile): String = when {
        // Karanlık / gerilim
        mood.tension >= 0.70f -> "dark ambient drone"
        mood.tension >= 0.55f && mood.valence <= 0.35f -> "horror ambient"

        // Sakin / odak
        mood.energy <= 0.22f && mood.warmth >= 0.60f -> "soft rain ambience"
        mood.energy <= 0.25f && mood.focus >= 0.85f -> "calm ambient"
        mood.energy <= 0.30f -> "relaxing ambient"

        // Aydınlık / sıcak
        mood.valence >= 0.75f && mood.warmth >= 0.65f -> "warm acoustic instrumental"
        mood.valence >= 0.72f -> "uplifting instrumental"

        // Doğa / dış mekân
        mood.warmth >= 0.70f && mood.energy >= 0.35f -> "forest nature ambience"
        mood.warmth <= 0.35f && mood.energy >= 0.55f -> "electronic ambient"

        // Enerjik
        mood.energy >= 0.75f -> "epic cinematic instrumental"
        mood.energy >= 0.55f -> "upbeat instrumental"

        else -> "ambient instrumental"
    }

    /**
     * İkinci deneme sorgusu — birincil sonuç vermezse kullanılır.
     */
    fun fallbackForMood(mood: MoodProfile): String = when {
        mood.focus >= 0.80f -> "instrumental focus music"
        mood.warmth >= 0.70f -> "piano ambience"
        mood.tension >= 0.60f -> "suspense soundscape"
        else -> "ambient soundscape"
    }

    /**
     * Sorguyu bir tür ipucuyla zenginleştirir.
     *
     * Örn. `("dark ambient drone", "Horror")` → `"dark ambient drone horror"`.
     * Yalnızca ilk tür eklenir; uzun sorgular isabeti düşürür.
     */
    fun withGenreHint(query: String, genres: List<String>): String {
        val hint = genres.firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: return query
        // Tür adı zaten sorguda geçiyorsa tekrar eklemeyelim.
        if (hint.isNotEmpty() && !query.lowercase().contains(hint)) return "$query $hint"
        return query
    }
}
