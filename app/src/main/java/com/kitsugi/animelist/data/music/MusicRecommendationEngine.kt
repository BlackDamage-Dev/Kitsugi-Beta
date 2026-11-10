package com.kitsugi.animelist.data.music


/**
 * 🧠 Öneri Motoru
 *
 * Manga türlerinden hesaplanan [MoodProfile] ile her sesin/presetin
 * duygu profilini karşılaştırır ve sıralı öneri listesi üretir.
 *
 * Tamamen çevrimdışı ve deterministiktir. Aynı manga her açılışta aynı
 * önerileri verir (kullanıcı beğeni geçmişi eklendiğinde bu değişir).
 *
 * Algoritma:
 *  1. Tür → duygu vektörü ([GenreMoodMapper])
 *  2. Her aday için kosinüs benzerliği (ağırlık %70)
 *  3. Tür ipucu doğrudan eşleşme bonusu (ağırlık %20)
 *  4. Vokal/odak cezası (okurken sözlü müzik dikkat dağıtır)
 *  5. Çeşitlilik filtresi — aynı aileden en fazla 2 öneri
 */
object MusicRecommendationEngine {

    /** Ağırlıklar — tek yerde toplandı, ayar gerekirse buradan. */
    private const val W_SIMILARITY = 0.70f
    private const val W_GENRE_HINT = 0.20f
    private const val W_FOCUS = 0.10f

    /** Çeşitlilik: aynı `varietyKey` grubundan en fazla bu sayıda öneri. */
    private const val MAX_PER_VARIETY_GROUP = 2

    /**
     * Ana giriş noktası.
     *
     * @param genres Manga tür listesi
     * @param pool   Aday sesler (prosedürel + uzak katalog birleşimi)
     * @param presetPool Aday presetler (varsayılan: tüm presetler)
     * @param soundLimit Döndürülecek ses önerisi sayısı
     * @param presetLimit Döndürülecek preset önerisi sayısı
     */
    /**
     * İçeriğe göre öneri üretir.
     *
     * **Üç katmanlı sinyal:** tür → etiket → açıklama. Açıklama ve başlık
     * parametreleri opsiyoneldir; verilmezse yalnızca türler kullanılır
     * (eski davranış korunur).
     *
     * Hiçbir sinyal eşleşmezse `confidence = NONE` döner ve panel bunu
     * kullanıcıya söyler — sessizce rastgele öneri yapmaz.
     */
    fun recommend(
        genres: List<String>,
        pool: List<AmbientSound> = ProceduralSoundCatalog.sounds,
        presetPool: List<AtmospherePreset> = AtmospherePresets.presets,
        soundLimit: Int = 6,
        presetLimit: Int = 6,
        title: String? = null,
        description: String? = null
    ): RecommendationResult {
        val resolved = ContentMoodResolver.resolve(
            genres = genres,
            title = title,
            description = description
        )
        val mood = resolved.mood
        val detected = resolved.matchedGenres.toSet()

        val soundRecs = rankSounds(pool, mood, detected, soundLimit)
        val presetRecs = rankPresets(presetPool, mood, detected, presetLimit)

        return RecommendationResult(
            mood = mood,
            detectedGenres = resolved.matchedGenres,
            sounds = soundRecs,
            presets = presetRecs,
            reason = resolved.reason,
            confidence = resolved.confidence
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Ses sıralaması
    // ─────────────────────────────────────────────────────────────────────

    private fun rankSounds(
        pool: List<AmbientSound>,
        mood: MoodProfile,
        detectedGenres: Set<String>,
        limit: Int
    ): List<MusicRecommendation> {
        val scored = pool
            .filter { it.isPlayable }
            .map { sound ->
                val similarity = mood.similarityTo(sound.mood)
                val genreBonus = genreHintScore(sound, detectedGenres)
                val focusBonus = sound.mood.focus
                val total = similarity * W_SIMILARITY +
                    genreBonus * W_GENRE_HINT +
                    focusBonus * W_FOCUS
                MusicRecommendation(
                    sound = sound,
                    score = total.coerceIn(0f, 1f),
                    reason = buildReason(sound, detectedGenres, similarity)
                )
            }
            .sortedByDescending { it.score }

        return applyDiversity(scored, limit)
    }

    /**
     * Ses ailesine göre tür ipucu uyumu. Örneğin korku türünde
     * drone/fırtına ailesi, romantizmde yağmur/piyano ailesi öne çıkar.
     */
    private fun genreHintScore(sound: AmbientSound, detectedGenres: Set<String>): Float {
        if (detectedGenres.isEmpty()) return 0f

        val preferredFamilies: Set<SoundFamily> = buildSet {
            if (detectedGenres.any { it in HORROR_GENRES }) {
                add(SoundFamily.INSTRUMENTAL); add(SoundFamily.WEATHER)
            }
            if (detectedGenres.any { it in ROMANCE_GENRES }) {
                add(SoundFamily.WEATHER); add(SoundFamily.FIRE); add(SoundFamily.INSTRUMENTAL)
            }
            if (detectedGenres.any { it in ACTION_GENRES }) {
                add(SoundFamily.INSTRUMENTAL); add(SoundFamily.URBAN)
            }
            if (detectedGenres.any { it in NATURE_GENRES }) {
                add(SoundFamily.FOREST); add(SoundFamily.WATER); add(SoundFamily.WIND)
            }
            if (detectedGenres.any { it in CALM_GENRES }) {
                add(SoundFamily.NOISE); add(SoundFamily.URBAN); add(SoundFamily.FIRE)
            }
            if (detectedGenres.any { it in SCIFI_GENRES }) {
                add(SoundFamily.INSTRUMENTAL)
            }
        }
        return if (sound.family in preferredFamilies) 1f else 0.25f
    }

    private fun buildReason(
        sound: AmbientSound,
        detectedGenres: Set<String>,
        similarity: Float
    ): String = when {
        detectedGenres.isEmpty() -> "Nötr başlangıç — senin için sakin bir seçim"
        similarity >= 0.90f -> "Türüne tam uyum sağlıyor"
        similarity >= 0.75f -> "Türüne yakın bir atmosfer"
        similarity >= 0.55f -> "Tamamlayıcı bir seçenek"
        else -> "Farklı bir hava denemek istersen"
    }

    /**
     * Çeşitlilik filtresi: aynı `varietyKey` grubundan en fazla
     * [MAX_PER_VARIETY_GROUP] adet alır. Böylece öneri listesi
     * "3 farklı yağmur" gibi tekdüze olmaz.
     */
    private fun applyDiversity(
        ranked: List<MusicRecommendation>,
        limit: Int
    ): List<MusicRecommendation> {
        val result = mutableListOf<MusicRecommendation>()
        val groupCounts = mutableMapOf<String, Int>()

        for (rec in ranked) {
            val key = rec.sound.varietyKey
            val current = groupCounts[key] ?: 0
            if (current >= MAX_PER_VARIETY_GROUP) continue
            groupCounts[key] = current + 1
            result += rec
            if (result.size >= limit) break
        }
        return result
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Preset sıralaması
    // ─────────────────────────────────────────────────────────────────────

    private fun rankPresets(
        pool: List<AtmospherePreset>,
        mood: MoodProfile,
        detectedGenres: Set<String>,
        limit: Int
    ): List<MusicRecommendation> {
        val scored = pool.map { preset ->
            val similarity = mood.similarityTo(preset.mood)
            // Tür ipucu doğrudan eşleşmesi — güçlü sinyal
            val hintMatches = preset.genreHints.count { it in detectedGenres }
            val hintScore = when {
                hintMatches >= 2 -> 1.0f
                hintMatches == 1 -> 0.75f
                else -> 0f
            }
            val total = similarity * 0.6f + hintScore * 0.4f
            MusicRecommendation(
                sound = presetToSound(preset),
                score = total.coerceIn(0f, 1f),
                reason = when {
                    hintMatches >= 2 -> "Tam türün için hazırlanmış"
                    hintMatches == 1 -> "Türünle uyumlu bir sahne"
                    similarity >= 0.85f -> "Atmosferine çok yakın"
                    else -> "Farklı bir deneyim"
                }
            )
        }.sortedByDescending { it.score }

        // Presetler için aile bazlı çeşitlilik
        val result = mutableListOf<MusicRecommendation>()
        val familyCounts = mutableMapOf<SoundFamily, Int>()
        for (rec in scored) {
            val family = rec.sound.family
            val count = familyCounts[family] ?: 0
            if (count >= MAX_PER_VARIETY_GROUP) continue
            familyCounts[family] = count + 1
            result += rec
            if (result.size >= limit) break
        }
        return result
    }

    /**
     * Preseti [AmbientSound] biçimine çevirir — böylece UI tek tip
     * veriyle çalışır. `id` preset kimliğidir, `family` birincil ailedir.
     */
    fun presetToSound(preset: AtmospherePreset): AmbientSound = AmbientSound(
        id = preset.id,
        title = "${preset.emoji} ${preset.name}",
        subtitle = preset.description,
        family = preset.family,
        origin = AmbientOrigin.PROCEDURAL,
        mood = preset.mood,
        varietyKey = "preset_${preset.id}"
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Tür kümeleri
    // ─────────────────────────────────────────────────────────────────────
    private val HORROR_GENRES = setOf(
        "horror", "korku", "thriller", "gerilim", "psychological",
        "psikolojik", "mystery", "gizem", "suspense", "tragedy"
    )
    private val ROMANCE_GENRES = setOf(
        "romance", "romantik", "shoujo", "shojo", "josei", "drama"
    )
    private val ACTION_GENRES = setOf(
        "action", "aksiyon", "shounen", "martial arts", "savaş",
        "war", "military", "sports", "spor", "mecha"
    )
    private val NATURE_GENRES = setOf(
        "fantasy", "fantezi", "adventure", "macera", "isekai",
        "historical", "tarihi", "samurai"
    )
    private val CALM_GENRES = setOf(
        "slice of life", "günlük yaşam", "iyileşme", "healing",
        "comedy", "komedi", "school", "okul", "cooking"
    )
    private val SCIFI_GENRES = setOf(
        "sci-fi", "bilim kurgu", "space", "uzay", "cyberpunk"
    )
}
