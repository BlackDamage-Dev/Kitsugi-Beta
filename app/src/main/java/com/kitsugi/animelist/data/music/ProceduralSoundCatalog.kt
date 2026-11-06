package com.kitsugi.animelist.data.music

/**
 * 🎧 Prosedürel Ses Kataloğu
 *
 * Cihaz içinde DSP ile üretilen seslerin tanımları. Hiçbiri internet
 * gerektirmez, hiçbiri telifli değildir — tamamı uygulamanın kendi
 * sentezleyicisi tarafından gerçek zamanlı üretilir.
 *
 * Bu katalog, uygulamanın **her koşulda çalışan** temel katmanıdır:
 * internet yoksa, API anahtarı girilmemişse, kota bitmişse bile okuyucu
 * sessiz kalmaz.
 *
 * DSP implementasyonu: [com.kitsugi.animelist.core.music.ProceduralSoundSynth]
 */
object ProceduralSoundCatalog {

    // ─────────────────────────────────────────────────────────────────────
    //  Kimlik sabitleri — synth tarafındaki anahtarlarla BİREBİR eşleşmeli
    // ─────────────────────────────────────────────────────────────────────
    object Ids {
        const val WHITE_NOISE = "proc_white_noise"
        const val PINK_NOISE = "proc_pink_noise"
        const val BROWN_NOISE = "proc_brown_noise"
        const val RAIN_SOFT = "proc_rain_soft"
        const val RAIN_HEAVY = "proc_rain_heavy"
        const val THUNDER = "proc_thunder"
        const val FIRE_CRACKLE = "proc_fire_crackle"
        const val WIND = "proc_wind"
        const val OCEAN_WAVES = "proc_ocean_waves"
        const val STREAM = "proc_stream"
        const val FOREST_BIRDS = "proc_forest_birds"
        const val CRICKETS = "proc_crickets"
        const val CAFE = "proc_cafe"
        const val CITY_NIGHT = "proc_city_night"
        const val TRAIN = "proc_train"
        const val PIANO_AMBIENT = "proc_piano_ambient"
        const val MUSIC_BOX = "proc_music_box"
        const val KOTO = "proc_koto"
        const val SYNTH_PAD = "proc_synth_pad"
        const val DARK_DRONE = "proc_dark_drone"
        const val HEARTBEAT = "proc_heartbeat"
        const val CLOCK = "proc_clock"
        const val SINGING_BOWL = "proc_singing_bowl"
        const val WIND_CHIME = "proc_wind_chime"

        // ── Binaural serisi (kulaklık gerekir) ──────────────────────────
        const val BINAURAL_DELTA = "proc_binaural_delta"
        const val BINAURAL_THETA = "proc_binaural_theta"
        const val BINAURAL_ALPHA = "proc_binaural_alpha"
        const val BINAURAL_BETA = "proc_binaural_beta"
        const val BINAURAL_GAMMA = "proc_binaural_gamma"
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Katalog
    // ─────────────────────────────────────────────────────────────────────
    val sounds: List<AmbientSound> = listOf(

        // ── Gürültü ailesi ───────────────────────────────────────────────
        AmbientSound(
            id = Ids.WHITE_NOISE,
            title = "Beyaz Gürültü",
            subtitle = "Odak ve maskeleme",
            family = SoundFamily.NOISE,
            mood = MoodProfile(0.15f, 0.50f, 0.10f, 0.20f, 0.95f),
            varietyKey = "noise_white"
        ),
        AmbientSound(
            id = Ids.PINK_NOISE,
            title = "Pembe Gürültü",
            subtitle = "Daha yumuşak, kulak dostu",
            family = SoundFamily.NOISE,
            mood = MoodProfile(0.12f, 0.55f, 0.08f, 0.30f, 0.95f),
            varietyKey = "noise_pink"
        ),
        AmbientSound(
            id = Ids.BROWN_NOISE,
            title = "Kahverengi Gürültü",
            subtitle = "Derin, sıcak uğultu",
            family = SoundFamily.NOISE,
            mood = MoodProfile(0.10f, 0.45f, 0.10f, 0.55f, 0.92f),
            varietyKey = "noise_brown"
        ),

        // ── Hava / yağmur ────────────────────────────────────────────────
        AmbientSound(
            id = Ids.RAIN_SOFT,
            title = "Hafif Yağmur",
            subtitle = "Cama vuran yağmur",
            family = SoundFamily.WEATHER,
            mood = MoodProfile(0.20f, 0.60f, 0.15f, 0.70f, 0.90f),
            varietyKey = "rain_soft"
        ),
        AmbientSound(
            id = Ids.RAIN_HEAVY,
            title = "Şiddetli Yağmur",
            subtitle = "Sağanak ve gök gürültüsü",
            family = SoundFamily.WEATHER,
            mood = MoodProfile(0.40f, 0.30f, 0.55f, 0.50f, 0.80f),
            varietyKey = "rain_heavy"
        ),
        AmbientSound(
            id = Ids.THUNDER,
            title = "Uzak Gök Gürültüsü",
            subtitle = "Aralıklı, derinden",
            family = SoundFamily.WEATHER,
            mood = MoodProfile(0.30f, 0.20f, 0.80f, 0.25f, 0.78f),
            varietyKey = "thunder"
        ),

        // ── Ateş ─────────────────────────────────────────────────────────
        AmbientSound(
            id = Ids.FIRE_CRACKLE,
            title = "Şömine",
            subtitle = "Çıtırdayan odun ateşi",
            family = SoundFamily.FIRE,
            mood = MoodProfile(0.25f, 0.70f, 0.10f, 0.95f, 0.88f),
            varietyKey = "fire"
        ),

        // ── Su ───────────────────────────────────────────────────────────
        AmbientSound(
            id = Ids.OCEAN_WAVES,
            title = "Okyanus Dalgaları",
            subtitle = "Yavaş, düzenli kabarma",
            family = SoundFamily.WATER,
            mood = MoodProfile(0.22f, 0.65f, 0.12f, 0.60f, 0.92f),
            varietyKey = "ocean"
        ),
        AmbientSound(
            id = Ids.STREAM,
            title = "Dere Şırıltısı",
            subtitle = "Taşlar arasında akan su",
            family = SoundFamily.WATER,
            mood = MoodProfile(0.25f, 0.72f, 0.08f, 0.70f, 0.90f),
            varietyKey = "stream"
        ),

        // ── Rüzgar ───────────────────────────────────────────────────────
        AmbientSound(
            id = Ids.WIND,
            title = "Rüzgar",
            subtitle = "Dağ esintisi",
            family = SoundFamily.WIND,
            mood = MoodProfile(0.30f, 0.48f, 0.35f, 0.40f, 0.85f),
            varietyKey = "wind"
        ),

        // ── Orman ────────────────────────────────────────────────────────
        AmbientSound(
            id = Ids.FOREST_BIRDS,
            title = "Orman Kuşları",
            subtitle = "Sabah cıvıltısı",
            family = SoundFamily.FOREST,
            mood = MoodProfile(0.35f, 0.85f, 0.05f, 0.80f, 0.80f),
            varietyKey = "birds"
        ),
        AmbientSound(
            id = Ids.CRICKETS,
            title = "Cırcır Böcekleri",
            subtitle = "Yaz gecesi",
            family = SoundFamily.FOREST,
            mood = MoodProfile(0.20f, 0.60f, 0.15f, 0.75f, 0.85f),
            varietyKey = "crickets"
        ),

        // ── Şehir ────────────────────────────────────────────────────────
        AmbientSound(
            id = Ids.CAFE,
            title = "Kafe Uğultusu",
            subtitle = "Uzak sohbetler, bardak sesleri",
            family = SoundFamily.URBAN,
            mood = MoodProfile(0.35f, 0.72f, 0.10f, 0.85f, 0.80f),
            varietyKey = "cafe"
        ),
        AmbientSound(
            id = Ids.CITY_NIGHT,
            title = "Gece Şehri",
            subtitle = "Uzak trafik, ıslak asfalt",
            family = SoundFamily.URBAN,
            mood = MoodProfile(0.30f, 0.35f, 0.30f, 0.40f, 0.82f),
            varietyKey = "city"
        ),
        AmbientSound(
            id = Ids.TRAIN,
            title = "Tren",
            subtitle = "Ray üstünde ritmik ilerleme",
            family = SoundFamily.URBAN,
            mood = MoodProfile(0.25f, 0.55f, 0.15f, 0.60f, 0.88f),
            varietyKey = "train"
        ),

        // ── Enstrümantal ─────────────────────────────────────────────────
        AmbientSound(
            id = Ids.PIANO_AMBIENT,
            title = "Ambiyans Piyano",
            subtitle = "Yavaş, rastgele arpejler",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.20f, 0.60f, 0.15f, 0.90f, 0.88f),
            varietyKey = "piano"
        ),
        AmbientSound(
            id = Ids.MUSIC_BOX,
            title = "Müzik Kutusu",
            subtitle = "Nostaljik, kırılgan melodiler",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.22f, 0.68f, 0.20f, 0.85f, 0.85f),
            varietyKey = "musicbox"
        ),
        AmbientSound(
            id = Ids.KOTO,
            title = "Koto",
            subtitle = "Japon telli çalgı",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.30f, 0.65f, 0.25f, 0.75f, 0.85f),
            varietyKey = "koto"
        ),
        AmbientSound(
            id = Ids.SYNTH_PAD,
            title = "Synth Pad",
            subtitle = "Yumuşak elektronik yatak",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.28f, 0.55f, 0.30f, 0.15f, 0.92f),
            varietyKey = "pad"
        ),
        AmbientSound(
            id = Ids.DARK_DRONE,
            title = "Karanlık Drone",
            subtitle = "Tehditkâr, düşük frekans",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.18f, 0.10f, 0.92f, 0.10f, 0.85f),
            varietyKey = "drone"
        ),
        AmbientSound(
            id = Ids.SINGING_BOWL,
            title = "Şarkı Söyleyen Kâse",
            subtitle = "Tibet çanı, uzun rezonans",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.10f, 0.60f, 0.08f, 0.70f, 0.95f),
            varietyKey = "bowl"
        ),
        AmbientSound(
            id = Ids.WIND_CHIME,
            title = "Rüzgar Çanı",
            subtitle = "Hafif, rastgele çınlamalar",
            family = SoundFamily.INSTRUMENTAL,
            mood = MoodProfile(0.15f, 0.72f, 0.10f, 0.72f, 0.88f),
            varietyKey = "chime"
        ),

        // ── Ritim / vücut ────────────────────────────────────────────────
        AmbientSound(
            id = Ids.HEARTBEAT,
            title = "Kalp Atışı",
            subtitle = "Yavaş, sakinleştirici",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.20f, 0.30f, 0.55f, 0.55f, 0.85f),
            varietyKey = "heartbeat"
        ),
        AmbientSound(
            id = Ids.CLOCK,
            title = "Saat Tıkırtısı",
            subtitle = "Düzenli saniye sesi",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.22f, 0.45f, 0.35f, 0.60f, 0.90f),
            varietyKey = "clock"
        ),

        // ── Binaural serisi ──────────────────────────────────────────────
        // İki kulağa farklı frekans → beyin farkı duyar (vuruş).
        // Kulaklık şart: hoparlörde iki kanal havada karışır, vuruş kaybolur.
        AmbientSound(
            id = Ids.BINAURAL_DELTA,
            title = "Binaural · Delta",
            subtitle = "2,5 Hz — derin uyku (kulaklık)",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.05f, 0.42f, 0.05f, 0.45f, 0.55f),
            varietyKey = "binaural"
        ),
        AmbientSound(
            id = Ids.BINAURAL_THETA,
            title = "Binaural · Theta",
            subtitle = "6 Hz — uykuya geçiş (kulaklık)",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.10f, 0.50f, 0.10f, 0.50f, 0.65f),
            varietyKey = "binaural"
        ),
        AmbientSound(
            id = Ids.BINAURAL_ALPHA,
            title = "Binaural · Alfa",
            subtitle = "10 Hz — gevşeme (kulaklık)",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.18f, 0.62f, 0.08f, 0.58f, 0.70f),
            varietyKey = "binaural"
        ),
        AmbientSound(
            id = Ids.BINAURAL_BETA,
            title = "Binaural · Beta",
            subtitle = "18 Hz — odak (kulaklık)",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.45f, 0.60f, 0.25f, 0.45f, 0.95f),
            varietyKey = "binaural"
        ),
        AmbientSound(
            id = Ids.BINAURAL_GAMMA,
            title = "Binaural · Gama",
            subtitle = "40 Hz — yoğun dikkat (kulaklık)",
            family = SoundFamily.OTHER,
            mood = MoodProfile(0.62f, 0.58f, 0.40f, 0.40f, 0.90f),
            varietyKey = "binaural"
        )
    )

    /** Kimliğe göre ses bulur. */
    fun byId(id: String): AmbientSound? = sounds.firstOrNull { it.id == id }

    /** Aileye göre filtreler (panel gruplaması için). */
    fun byFamily(family: SoundFamily): List<AmbientSound> =
        sounds.filter { it.family == family }
}
