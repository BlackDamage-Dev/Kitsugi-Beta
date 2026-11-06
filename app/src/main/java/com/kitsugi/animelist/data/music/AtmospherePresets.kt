package com.kitsugi.animelist.data.music

/**
 * 🌌 Atmosfer Preset Kataloğu
 *
 * Hazır katmanlı karışımlar. Her preset birden fazla sesi farklı
 * seviyelerde birleştirir ve ayırt edici "sahne" hissi yaratır.
 *
 * Örnek: 🌧️ Romantik Yağmur = hafif yağmur %70 + ambiyans piyano %35 + şömine %15
 *
 * Kullanıcı hazır preset seçebileceği gibi karıştırıcıdan kendi
 * kombinasyonunu da kurabilir; bu durumda özel preset olarak saklanır.
 */
object AtmospherePresets {

    object Ids {
        const val ROMANTIC_RAIN = "preset_romantic_rain"
        const val TRAINING = "preset_training"
        const val HORROR_NIGHT = "preset_horror_night"
        const val CAFE_MORNING = "preset_cafe_morning"
        const val SEASIDE = "preset_seaside"
        const val DEEP_FOREST = "preset_deep_forest"
        const val FIREPLACE = "preset_fireplace"
        const val DEEP_SPACE = "preset_deep_space"
        const val CITY_NIGHTS = "preset_city_nights"
        const val STUDY_MODE = "preset_study_mode"
        const val JAPAN = "preset_japan"
        const val SLEEP = "preset_sleep"
    }

    val presets: List<AtmospherePreset> = listOf(

        AtmospherePreset(
            id = Ids.ROMANTIC_RAIN,
            name = "Romantik Yağmur",
            emoji = "🌧️",
            description = "Cama vuran yağmur, uzaktan piyano",
            mood = MoodProfile(0.22f, 0.72f, 0.12f, 0.88f, 0.88f),
            genreHints = listOf("romance", "drama", "shoujo", "slice of life", "josei"),
            family = SoundFamily.WEATHER,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.RAIN_SOFT, 0.70f),
                SoundLayer(ProceduralSoundCatalog.Ids.PIANO_AMBIENT, 0.35f, delayMs = 1_500L),
                SoundLayer(ProceduralSoundCatalog.Ids.FIRE_CRACKLE, 0.15f, delayMs = 3_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.TRAINING,
            name = "Antrenman",
            emoji = "⚔️",
            description = "Yükselen enerji, dövüş öncesi gerilim",
            mood = MoodProfile(0.90f, 0.65f, 0.65f, 0.42f, 0.55f),
            genreHints = listOf("action", "shounen", "sports", "martial arts", "war"),
            family = SoundFamily.INSTRUMENTAL,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.DARK_DRONE, 0.30f),
                SoundLayer(ProceduralSoundCatalog.Ids.WIND, 0.25f),
                SoundLayer(ProceduralSoundCatalog.Ids.CITY_NIGHT, 0.20f, delayMs = 2_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.HORROR_NIGHT,
            name = "Korku Gecesi",
            emoji = "👻",
            description = "Derin drone, fırtına ve kalp atışı",
            mood = MoodProfile(0.28f, 0.10f, 0.96f, 0.12f, 0.72f),
            genreHints = listOf("horror", "thriller", "mystery", "psychological", "suspense"),
            family = SoundFamily.INSTRUMENTAL,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.DARK_DRONE, 0.55f),
                SoundLayer(ProceduralSoundCatalog.Ids.RAIN_HEAVY, 0.40f),
                SoundLayer(ProceduralSoundCatalog.Ids.HEARTBEAT, 0.12f, delayMs = 4_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.CAFE_MORNING,
            name = "Kafe Sabahı",
            emoji = "☕",
            description = "Sıcak uğultu, kuş sesleri, hafif müzik",
            mood = MoodProfile(0.32f, 0.82f, 0.08f, 0.90f, 0.82f),
            genreHints = listOf("slice of life", "comedy", "school", "cooking", "romance"),
            family = SoundFamily.URBAN,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.CAFE, 0.60f),
                SoundLayer(ProceduralSoundCatalog.Ids.PIANO_AMBIENT, 0.30f, delayMs = 2_000L),
                SoundLayer(ProceduralSoundCatalog.Ids.FOREST_BIRDS, 0.12f, delayMs = 4_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.SEASIDE,
            name = "Sahilde",
            emoji = "🌊",
            description = "Yavaş dalgalar, uzaktan gitar tınısı",
            mood = MoodProfile(0.24f, 0.70f, 0.10f, 0.72f, 0.90f),
            genreHints = listOf("slice of life", "romance", "adventure", "healing"),
            family = SoundFamily.WATER,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.OCEAN_WAVES, 0.75f),
                SoundLayer(ProceduralSoundCatalog.Ids.KOTO, 0.22f, delayMs = 3_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.DEEP_FOREST,
            name = "Derin Orman",
            emoji = "🌲",
            description = "Kuşlar, esinti, akan su",
            mood = MoodProfile(0.30f, 0.78f, 0.12f, 0.82f, 0.86f),
            genreHints = listOf("fantasy", "adventure", "historical", "isekai"),
            family = SoundFamily.FOREST,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.FOREST_BIRDS, 0.55f),
                SoundLayer(ProceduralSoundCatalog.Ids.WIND, 0.25f),
                SoundLayer(ProceduralSoundCatalog.Ids.STREAM, 0.30f, delayMs = 2_500L)
            )
        ),

        AtmospherePreset(
            id = Ids.FIREPLACE,
            name = "Şömine Başı",
            emoji = "🔥",
            description = "Ateş çıtırtısı ve yumuşak piyano",
            mood = MoodProfile(0.22f, 0.72f, 0.08f, 0.95f, 0.90f),
            genreHints = listOf("drama", "historical", "slice of life", "romance"),
            family = SoundFamily.FIRE,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.FIRE_CRACKLE, 0.80f),
                SoundLayer(ProceduralSoundCatalog.Ids.PIANO_AMBIENT, 0.25f, delayMs = 2_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.DEEP_SPACE,
            name = "Derin Uzay",
            emoji = "🌌",
            description = "Sonsuz pad, hafif drone",
            mood = MoodProfile(0.25f, 0.45f, 0.35f, 0.12f, 0.94f),
            genreHints = listOf("sci-fi", "space", "mecha", "cyberpunk"),
            family = SoundFamily.INSTRUMENTAL,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.SYNTH_PAD, 0.60f),
                SoundLayer(ProceduralSoundCatalog.Ids.DARK_DRONE, 0.20f, delayMs = 3_000L),
                SoundLayer(ProceduralSoundCatalog.Ids.SINGING_BOWL, 0.10f, delayMs = 6_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.CITY_NIGHTS,
            name = "Gece Şehri",
            emoji = "🌃",
            description = "Uzak trafik, ıslak asfalt, lo-fi",
            mood = MoodProfile(0.32f, 0.38f, 0.30f, 0.45f, 0.84f),
            genreHints = listOf("seinen", "psychological", "mystery", "cyberpunk", "drama"),
            family = SoundFamily.URBAN,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.CITY_NIGHT, 0.55f),
                SoundLayer(ProceduralSoundCatalog.Ids.RAIN_SOFT, 0.30f),
                SoundLayer(ProceduralSoundCatalog.Ids.SYNTH_PAD, 0.25f, delayMs = 2_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.STUDY_MODE,
            name = "Odak Modu",
            emoji = "📚",
            description = "Pembe gürültü, nötr uğultu",
            mood = MoodProfile(0.12f, 0.55f, 0.06f, 0.40f, 0.98f),
            genreHints = listOf("seinen", "mystery", "slice of life", "psychological"),
            family = SoundFamily.NOISE,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.PINK_NOISE, 0.45f),
                SoundLayer(ProceduralSoundCatalog.Ids.CAFE, 0.18f, delayMs = 2_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.JAPAN,
            name = "Kyoto Akşamı",
            emoji = "🎐",
            description = "Koto, rüzgar çanı, hafif yağmur",
            mood = MoodProfile(0.26f, 0.66f, 0.18f, 0.76f, 0.88f),
            genreHints = listOf("historical", "samurai", "romance", "supernatural", "fantasy"),
            family = SoundFamily.INSTRUMENTAL,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.KOTO, 0.38f),
                SoundLayer(ProceduralSoundCatalog.Ids.WIND_CHIME, 0.22f),
                SoundLayer(ProceduralSoundCatalog.Ids.RAIN_SOFT, 0.28f, delayMs = 2_000L)
            )
        ),

        AtmospherePreset(
            id = Ids.SLEEP,
            name = "Uyku",
            emoji = "💤",
            description = "Derin kahverengi gürültü, yumuşak yağmur",
            mood = MoodProfile(0.05f, 0.45f, 0.05f, 0.50f, 0.95f),
            genreHints = listOf("slice of life", "healing"),
            family = SoundFamily.NOISE,
            layers = listOf(
                SoundLayer(ProceduralSoundCatalog.Ids.BROWN_NOISE, 0.55f),
                SoundLayer(ProceduralSoundCatalog.Ids.RAIN_SOFT, 0.22f, delayMs = 2_000L)
            )
        )
    )

    /** Kimliğe göre preset bulur. */
    fun byId(id: String): AtmospherePreset? = presets.firstOrNull { it.id == id }

    /** Bir presetin katmanlarını gerçek ses tanımlarına çözer. */
    fun resolveLayers(preset: AtmospherePreset): List<Pair<AmbientSound, SoundLayer>> =
        preset.layers.mapNotNull { layer ->
            ProceduralSoundCatalog.byId(layer.soundId)?.let { it to layer }
        }
}
