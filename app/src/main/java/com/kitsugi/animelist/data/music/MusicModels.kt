package com.kitsugi.animelist.data.music

import kotlin.math.sqrt

/**
 * 🎵 Kitsugi Ortam Sesi (Ambient) — Veri Modeli
 *
 * Okuyucu içi müzik/ambiyans sisteminin tüm veri tiplerini barındırır.
 *
 * Tasarım kararı: Ses kaynakları iki sınıfa ayrılır
 *  - [AmbientOrigin.PROCEDURAL] → cihaz içinde DSP ile üretilir (lisans riski yok, çevrimdışı)
 *  - [AmbientOrigin.REMOTE]     → kişisel API anahtarıyla çekilen akış (BYOK)
 *
 * Kişisel API anahtarı modeli sayesinde ticari/telif kısıtı olan kaynaklar
 * (Jamendo, Freesound, Audius …) tamamen kullanıcının kendi hesabı üzerinden çalışır.
 * Uygulama hiçbir ortak anahtar taşımaz, hiçbir lisans sözleşmesini ihlal etmez.
 */

// ─────────────────────────────────────────────────────────────────────────────
//  Duygu Vektörü
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 5 boyutlu duygu profili. Öneri motorunun temel birimi.
 *
 * Tüm alanlar 0.0f – 1.0f aralığındadır.
 *
 * @param energy  0 = sakin/uysal            → 1 = hareketli/enerjik
 * @param valence 0 = karanlık/hüzünlü       → 1 = aydınlık/neşeli
 * @param tension 0 = huzurlu                → 1 = gergin/tehditkâr
 * @param warmth  0 = soğuk/elektronik/uzay  → 1 = sıcak/akustik/ahşap
 * @param focus   0 = dikkat dağıtıcı        → 1 = odak dostu (vokalsiz, sabit)
 */
data class MoodProfile(
    val energy: Float = 0.5f,
    val valence: Float = 0.5f,
    val tension: Float = 0.3f,
    val warmth: Float = 0.5f,
    val focus: Float = 0.8f
) {
    /**
     * Kosinüs benzerliği — iki duygu profilinin ne kadar örtüştüğünü verir (0..1).
     * Sıfır vektör tehlikesine karşı normalize edilmiş değerler kullanılır.
     */
    fun similarityTo(other: MoodProfile): Float {
        val dot = energy * other.energy +
            valence * other.valence +
            tension * other.tension +
            warmth * other.warmth +
            focus * other.focus
        val na = sqrt(
            energy * energy + valence * valence + tension * tension +
                warmth * warmth + focus * focus
        )
        val nb = sqrt(
            other.energy * other.energy + other.valence * other.valence +
                other.tension * other.tension + other.warmth * other.warmth +
                other.focus * other.focus
        )
        if (na <= 0.0001f || nb <= 0.0001f) return 0f
        return (dot / (na * nb)).coerceIn(0f, 1f)
    }

    companion object {
        /** Tür bilgisi yoksa kullanılan güvenli başlangıç profili. */
        val Neutral = MoodProfile(
            energy = 0.35f,
            valence = 0.55f,
            tension = 0.25f,
            warmth = 0.55f,
            focus = 0.85f
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ses Aileleri
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sesin ait olduğu aile. Panelde gruplama ve çeşitlilik filtresi için kullanılır.
 */
enum class SoundFamily {
    NOISE,        // beyaz / pembe / kahverengi gürültü
    WEATHER,      // yağmur, fırtına, gök gürültüsü
    FIRE,         // şömine, çıtırtı
    WATER,        // dalga, dere, damlama
    WIND,         // rüzgar, fırtına rüzgarı
    FOREST,       // orman, kuş, cırcır böceği
    URBAN,        // kafe, şehir, tren
    INSTRUMENTAL, // piyano, koto, müzik kutusu, pad, drone
    VOCAL,        // (uzak kaynaklardan gelebilir — öneri motoru cezalandırır)
    OTHER
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ses Kaynağı
// ─────────────────────────────────────────────────────────────────────────────

/** Sesin nereden geldiğini belirtir. */
enum class AmbientOrigin {
    /** Cihaz içinde DSP ile üretilir. İnternet gerekmez, lisans riski yok. */
    PROCEDURAL,
    /** Uzak katalogdan akış. Kişisel API anahtarı gerektirebilir. */
    REMOTE
}

/**
 * Tek bir çalınabilir ses tanımı.
 *
 * @param id          Benzersiz kimlik (prosedürel sesler için DSP anahtarı)
 * @param title       Görünen ad
 * @param subtitle    Alt açıklama (üretici, etiket vb.)
 * @param family      Ses ailesi (gruplama/çeşitlilik için)
 * @param origin      Üretim biçimi
 * @param mood        Duygu profili (öneri motoru kullanır)
 * @param streamUrl   Uzak sesler için oynatma adresi
 * @param durationMs  Süre (0 = bilinmiyor / sonsuz döngü)
 * @param attribution Lisans metni — CC/BY kaynaklarında gösterilmesi ZORUNLU
 * @param licenseUrl  Lisans bağlantısı
 * @param pageUrl     Kaynak sayfası (atıf bağlantısı)
 * @param sourceId    Hangi uzak kaynaktan geldi ([MusicSourceId])
 */
data class AmbientSound(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val family: SoundFamily = SoundFamily.OTHER,
    val origin: AmbientOrigin = AmbientOrigin.PROCEDURAL,
    val mood: MoodProfile = MoodProfile.Neutral,
    val streamUrl: String? = null,
    val durationMs: Long = 0L,
    val attribution: String = "",
    val licenseUrl: String = "",
    val pageUrl: String = "",
    val sourceId: MusicSourceId = MusicSourceId.BUILTIN,
    /** Öneri motorunun çeşitlilik filtresi için kullanılan grup anahtarı. */
    val varietyKey: String = family.name
) {
    /**
     * Ses çalınabilir mi?
     *
     * Internet Archive özel durumu: arama API'si yalnızca öğe kimliği
     * döndürür, oynatılabilir dosya adı `/metadata/<id>` çağrısıyla
     * çözülür. Bu yüzden `streamUrl` **başlangıçta null** olur —
     * [com.kitsugi.animelist.data.music.MusicCatalogRepository.resolveUrl]
     * çalma anında doldurur.
     *
     * Önceki hâli yalnızca `streamUrl`'e bakıyordu ve Archive sesleri
     * `isPlayable = false` olduğu için öneri havuzundan **tamamen**
     * eleniyordu (6 kaynaktan biri ölüydü).
     */
    val isPlayable: Boolean
        get() = when {
            origin == AmbientOrigin.PROCEDURAL -> true
            !streamUrl.isNullOrBlank() -> true
            // Archive: adres çalma anında çözülür
            sourceId == MusicSourceId.ARCHIVE -> true
            else -> false
        }

    /** Atıf metnini tek satırda birleştirir. Boşsa null. */
    fun attributionLine(): String? {
        val parts = buildList {
            if (attribution.isNotBlank()) add(attribution)
            if (licenseUrl.isNotBlank()) add(licenseUrl)
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Atmosfer Preset (Katmanlı Karışım)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bir preset içindeki tek katman.
 *
 * @param soundId Hedef ses kimliği
 * @param volume  0.0–1.0 arası hedef ses seviyesi
 * @param delayMs Çalma başladıktan kaç ms sonra devreye girsin (katmanlı giriş)
 */
data class SoundLayer(
    val soundId: String,
    val volume: Float,
    val delayMs: Long = 0L
)

/**
 * Hazır "atmosfer" — birden fazla sesin karışımı.
 *
 * Örnek: 🌧️ Romantik Yağmur = yağmur %70 + piyano %35
 *
 * Bu yapı tekil seslerden daha zengin bir deneyim sunar ve rakip
 * okuyucularda bulunmayan ayırt edici özelliktir.
 */
data class AtmospherePreset(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val mood: MoodProfile,
    val layers: List<SoundLayer>,
    /** Bu presetin önerileceği manga türleri (küçük harf). */
    val genreHints: List<String> = emptyList(),
    /** Birincil katmanın ses ailesi (kart ikonu/grafiği için). */
    val family: SoundFamily = SoundFamily.OTHER
) {
    /** Presetin ortalama ses seviyesi (karıştırıcı başlangıç değeri). */
    val averageVolume: Float
        get() = if (layers.isEmpty()) 0.5f
        else layers.sumOf { it.volume.toDouble() }.toFloat() / layers.size
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kanal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Karıştırıcıdaki bağımsız ses kanalı.
 *
 * Eskiden iki sabit kanal vardı (MÜZİK / AMBİYANS). Artık kaynak türü ne
 * olursa olsun (prosedürel, Jamendo, Openverse, Archive, Freesound, Audius)
 * her ses kendi kanalını alır.
 *
 * `MUSIC` / `AMBIENT` yalnızca geriye dönük uyumluluk ve varsayılan
 * atama ipucu olarak korunur.
 */
enum class MusicChannel {
    MUSIC,
    AMBIENT
}

/** Eşzamanlı çalınabilecek maksimum ses sayısı. */
const val MAX_AMBIENT_CHANNELS = 6

/** Bir kanalın çalma durumu. */
enum class ChannelPlayState {
    /** Sessiz — yüklendi ama çalmıyor. */
    IDLE,
    /** Hazırlanıyor / tampon dolduruyor. */
    BUFFERING,
    PLAYING,
    PAUSED,
    /** Hata — yeniden denenebilir. */
    ERROR
}

/**
 * Karıştırıcıdaki tek bir aktif kanalın anlık durumu.
 *
 * Kaynak türünden BAĞIMSIZDIR: prosedürel ses de, uzak akış da aynı
 * yapıyla temsil edilir. Böylece paneldeki her buton (ses seviyesi,
 * mute, favori, kaldır) her kanalda aynı şekilde çalışır.
 *
 * @param index    Kanal yuvası (0 .. MAX_AMBIENT_CHANNELS-1)
 * @param sound    Çalan ses tanımı
 * @param volume   0..1 kanal ses seviyesi (mute'tan bağımsız)
 * @param muted    Sessize alınmış mı
 * @param state    Çalma durumu
 * @param errorMessage Hata varsa kısa açıklama
 */
data class ChannelState(
    val index: Int,
    val sound: AmbientSound,
    val volume: Float = 0.6f,
    val muted: Boolean = false,
    val state: ChannelPlayState = ChannelPlayState.IDLE,
    val errorMessage: String? = null
) {
    /** Master ve mute uygulandıktan sonra gerçek duyulan seviye. */
    fun effectiveVolume(master: Float): Float =
        if (muted) 0f else (volume * master).coerceIn(0f, 1f)

    val isRemote: Boolean get() = sound.origin == AmbientOrigin.REMOTE

    /** UI'da gösterilecek tek satırlık etiket. */
    val displayLabel: String
        get() = if (sound.subtitle.isNotBlank()) "${sound.title} · ${sound.subtitle}"
        else sound.title
}

// ─────────────────────────────────────────────────────────────────────────────
//  Uzak Kaynak Tanımı
// ─────────────────────────────────────────────────────────────────────────────

/** Desteklenen uzak ses katalogları. */
enum class MusicSourceId(val storageKey: String) {
    BUILTIN("builtin"),
    JAMENDO("jamendo"),
    FREESOUND("freesound"),
    OPENVERSE("openverse"),
    ARCHIVE("archive"),
    AUDIUS("audius");

    companion object {
        fun fromStorageKey(key: String?): MusicSourceId? =
            entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * Bir uzak kaynağın tanımı ve API anahtarı gereksinimi.
 *
 * @param id                Kaynak kimliği
 * @param displayName       Görünen ad
 * @param requiresClientId  `client_id` biçiminde anahtar mı (Jamendo)
 * @param requiresApiKey    Klasik API anahtarı mı (Freesound)
 * @param keyHint           Kullanıcıya anahtarı nereden alacağını anlatan kısa metin
 * @param signupUrl         Anahtar edinme adresi
 * @param keyless           Anahtarsız çalışıp çalışmadığı
 * @param nonCommercialOnly Kaynağın yalnızca ticari olmayan kullanıma izin verip vermediği
 * @param notes             Lisans/kullanım notu (UI'da gösterilir)
 */
data class MusicSourceDef(
    val id: MusicSourceId,
    val displayName: String,
    val requiresClientId: Boolean = false,
    val requiresApiKey: Boolean = true,
    val keyless: Boolean = false,
    /**
     * Anahtar **zorunlu değil, opsiyonel**.
     *
     * Openverse ve Audius anahtarsız çalışır; anahtar yalnızca kota/limit
     * yükseltir. `keyless` ile farkı: `keyless = true` kaynak için anahtar
     * alanı anlamsızdır (Archive), `optionalKey = true` ise anahtar alanı
     * gösterilir ve "isteğe bağlı" denir.
     */
    val optionalKey: Boolean = false,
    val nonCommercialOnly: Boolean = false,
    val keyHint: String = "",
    val signupUrl: String = "",
    val notes: String = ""
)

/** Kaynağın çalışma zamanındaki durumu (UI için). */
data class MusicSourceState(
    val def: MusicSourceDef,
    val enabled: Boolean = false,
    val keyConfigured: Boolean = false,
    /** null = henüz denenmedi */
    val keyValid: Boolean? = null,
    val lastCheckedAt: Long = 0L,
    /** Kota/limit bilgisi varsa son görülen değer. */
    val quotaNote: String = ""
) {
    /** Bu kaynak şu anda arama yapabilir mi? */
    val usable: Boolean
        get() = enabled && (def.keyless || keyConfigured)

    /** Paneldeki renk noktasını belirleyen durum. */
    val status: SourceStatus
        get() = when {
            !enabled -> SourceStatus.DISABLED
            !def.keyless && !keyConfigured -> SourceStatus.NO_KEY
            keyValid == false -> SourceStatus.INVALID_KEY
            keyValid == true -> SourceStatus.READY
            def.keyless -> SourceStatus.READY
            else -> SourceStatus.UNCHECKED
        }
}

/** Kaynak durumu — panel nokta rengini belirler. */
enum class SourceStatus { READY, UNCHECKED, NO_KEY, INVALID_KEY, DISABLED }

// ─────────────────────────────────────────────────────────────────────────────
//  Öneri Sonucu
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Öneri motorunun ürettiği tek bir öneri.
 *
 * @param sound  Önerilen ses/preset kaynağı
 * @param score  0..1 arası uygunluk puanı
 * @param reason Kullanıcıya gösterilecek kısa gerekçe ("Romance türüne uygun")
 */
data class MusicRecommendation(
    val sound: AmbientSound,
    val score: Float,
    val reason: String
)

/** Öneri motorunun döndürdüğü paket. */
data class RecommendationResult(
    val mood: MoodProfile,
    val detectedGenres: List<String>,
    val sounds: List<MusicRecommendation>,
    val presets: List<MusicRecommendation>,
    /** Kullanıcıya gösterilen eşleşme gerekçesi ("romance, drama türüne göre"). */
    val reason: String = "",
    /** Eşleşme güveni — düşükse öneri "tahmin"dir, bunu gizlemiyoruz. */
    val confidence: ContentMoodResolver.Confidence = ContentMoodResolver.Confidence.NONE
) {
    val isEmpty: Boolean get() = sounds.isEmpty() && presets.isEmpty()
}
