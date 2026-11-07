package com.kitsugi.animelist.data.music

/**
 * 🎯 İçerik → Duygu Eşleme Motoru
 *
 * **Sorun:** Yalnızca `GenreMoodMapper`'ın tablosuna bakmak yetmiyordu.
 * Gerçek dünyada manga kaynakları:
 *  - Türkçe/İngilizce karışık tür verir ("Romantizm", "Slice of Life")
 *  - Çoğu zaman tür vermez, yalnızca **açıklama** verir
 *  - Tür yerine etiket verir ("Manhwa", "Regresyon", "Seviye atlama")
 *  - "Webtoon", "Manhwa" gibi **format** adları verir (tür değil)
 *
 * **Çözüm:** üç katmanlı sinyal toplama. Hepsi aynı `MoodProfile`'a
 * katkı verir, ağırlıkları farklıdır:
 *
 * ```
 * 1. TÜRLER        ağırlık 2.0  (en güvenilir — editör etiketi)
 * 2. ETİKETLER     ağırlık 1.0  (tür listesindeki bilinmeyenler)
 * 3. AÇIKLAMA      ağırlık 0.6  (anahtar kelime taraması)
 * ```
 *
 * **Hiçbiri eşleşmezse** `Neutral` döner ve çağıran taraf bunu bilir
 * (`confidence = 0`). Sessizce "rastgele" öneri yapmaz.
 */
object ContentMoodResolver {

    /**
     * Eşleşme güveni.
     *
     * - `NONE`     hiçbir sinyal yok → nötr başlangıç
     * - `LOW`      yalnızca açıklama eşleşti
     * - `MEDIUM`   etiket eşleşti
     * - `HIGH`     tür eşleşti
     */
    enum class Confidence { NONE, LOW, MEDIUM, HIGH }

    data class Result(
        val mood: MoodProfile,
        val matchedGenres: List<String>,
        val matchedKeywords: List<String>,
        val confidence: Confidence,
        /** Kullanıcıya gösterilecek kısa gerekçe. */
        val reason: String
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Anahtar kelime tablosu
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Açıklama/etiket içinde geçen ifade → duygu profili.
     *
     * **Neden tür tablosundan ayrı:** bunlar tür değil, *içerik işareti*.
     * "Ölümünden sonra geri döner" cümlesi tür değil ama güçlü bir
     * "regresyon/intikam" sinyali.
     */
    private val keywordTable: Map<String, MoodProfile> = mapOf(
        // ── Aksiyon / dövüş ──────────────────────────────────────────────
        "savaş" to MoodProfile(0.90f, 0.35f, 0.80f, 0.35f, 0.75f),
        "dövüş" to MoodProfile(0.88f, 0.38f, 0.75f, 0.35f, 0.78f),
        "kılıç" to MoodProfile(0.80f, 0.42f, 0.65f, 0.40f, 0.80f),
        "güç" to MoodProfile(0.82f, 0.45f, 0.60f, 0.40f, 0.75f),
        "seviye" to MoodProfile(0.78f, 0.50f, 0.55f, 0.40f, 0.85f),
        "zindan" to MoodProfile(0.85f, 0.35f, 0.75f, 0.30f, 0.80f),
        "canavar" to MoodProfile(0.80f, 0.30f, 0.70f, 0.30f, 0.75f),
        "avcı" to MoodProfile(0.82f, 0.35f, 0.70f, 0.32f, 0.78f),
        "turnuva" to MoodProfile(0.85f, 0.50f, 0.65f, 0.40f, 0.80f),
        "savaşçı" to MoodProfile(0.88f, 0.38f, 0.75f, 0.35f, 0.78f),
        "ordu" to MoodProfile(0.85f, 0.30f, 0.80f, 0.35f, 0.70f),
        "kral" to MoodProfile(0.70f, 0.45f, 0.65f, 0.55f, 0.70f),
        "imparator" to MoodProfile(0.68f, 0.45f, 0.65f, 0.55f, 0.70f),

        // ── Karanlık / gerilim ───────────────────────────────────────────
        "ölüm" to MoodProfile(0.45f, 0.15f, 0.85f, 0.25f, 0.60f),
        "intikam" to MoodProfile(0.70f, 0.20f, 0.85f, 0.30f, 0.65f),
        "ihanet" to MoodProfile(0.55f, 0.15f, 0.85f, 0.28f, 0.62f),
        "kan" to MoodProfile(0.55f, 0.12f, 0.88f, 0.25f, 0.58f),
        "kabus" to MoodProfile(0.40f, 0.12f, 0.90f, 0.20f, 0.55f),
        "zombi" to MoodProfile(0.65f, 0.12f, 0.90f, 0.20f, 0.60f),
        "kıyamet" to MoodProfile(0.60f, 0.15f, 0.88f, 0.22f, 0.60f),
        "hayalet" to MoodProfile(0.35f, 0.18f, 0.85f, 0.30f, 0.55f),
        "lanet" to MoodProfile(0.45f, 0.18f, 0.82f, 0.30f, 0.58f),
        "gizem" to MoodProfile(0.45f, 0.32f, 0.75f, 0.35f, 0.72f),
        "cinayet" to MoodProfile(0.50f, 0.15f, 0.88f, 0.28f, 0.62f),
        "vampir" to MoodProfile(0.55f, 0.25f, 0.78f, 0.35f, 0.60f),

        // ── Romantik / duygusal ──────────────────────────────────────────
        "aşk" to MoodProfile(0.35f, 0.80f, 0.25f, 0.72f, 0.55f),
        "sevgi" to MoodProfile(0.32f, 0.82f, 0.20f, 0.75f, 0.55f),
        "kalp" to MoodProfile(0.35f, 0.75f, 0.28f, 0.78f, 0.55f),
        "evlilik" to MoodProfile(0.38f, 0.70f, 0.30f, 0.75f, 0.58f),
        "düğün" to MoodProfile(0.40f, 0.75f, 0.25f, 0.78f, 0.55f),
        "özlem" to MoodProfile(0.30f, 0.45f, 0.40f, 0.60f, 0.60f),
        "hasret" to MoodProfile(0.28f, 0.40f, 0.42f, 0.58f, 0.60f),
        "gözyaşı" to MoodProfile(0.30f, 0.30f, 0.55f, 0.65f, 0.55f),
        "dostluk" to MoodProfile(0.45f, 0.70f, 0.22f, 0.72f, 0.65f),

        // ── Huzur / günlük yaşam ─────────────────────────────────────────
        "okul" to MoodProfile(0.35f, 0.70f, 0.28f, 0.70f, 0.80f),
        "günlük" to MoodProfile(0.22f, 0.72f, 0.10f, 0.72f, 0.75f),
        "yemek" to MoodProfile(0.30f, 0.75f, 0.12f, 0.78f, 0.70f),
        "kahve" to MoodProfile(0.25f, 0.72f, 0.15f, 0.78f, 0.75f),
        "kafe" to MoodProfile(0.25f, 0.72f, 0.15f, 0.75f, 0.75f),
        "huzur" to MoodProfile(0.18f, 0.75f, 0.08f, 0.72f, 0.80f),
        "dinlenme" to MoodProfile(0.15f, 0.70f, 0.08f, 0.70f, 0.75f),

        // ── Fantastik / macera ───────────────────────────────────────────
        "büyü" to MoodProfile(0.60f, 0.72f, 0.40f, 0.55f, 0.85f),
        "sihir" to MoodProfile(0.62f, 0.70f, 0.40f, 0.55f, 0.85f),
        "ejderha" to MoodProfile(0.75f, 0.60f, 0.55f, 0.45f, 0.75f),
        "krallık" to MoodProfile(0.55f, 0.60f, 0.45f, 0.55f, 0.75f),
        "rehber" to MoodProfile(0.55f, 0.55f, 0.40f, 0.55f, 0.75f),
        "yolculuk" to MoodProfile(0.55f, 0.65f, 0.35f, 0.55f, 0.75f),
        "macera" to MoodProfile(0.68f, 0.70f, 0.42f, 0.52f, 0.78f),
        "reankarnasyon" to MoodProfile(0.55f, 0.55f, 0.45f, 0.55f, 0.78f),
        "yeniden doğ" to MoodProfile(0.55f, 0.55f, 0.45f, 0.55f, 0.78f),
        "geri dön" to MoodProfile(0.55f, 0.45f, 0.55f, 0.50f, 0.75f),
        "gelecek" to MoodProfile(0.60f, 0.50f, 0.50f, 0.40f, 0.82f),
        "uzay" to MoodProfile(0.45f, 0.45f, 0.45f, 0.32f, 0.88f),
        "robot" to MoodProfile(0.60f, 0.45f, 0.50f, 0.35f, 0.85f),

        // ── Spor / rekabet ───────────────────────────────────────────────
        "spor" to MoodProfile(0.88f, 0.72f, 0.55f, 0.50f, 0.82f),
        "futbol" to MoodProfile(0.90f, 0.70f, 0.55f, 0.50f, 0.80f),
        "basketbol" to MoodProfile(0.88f, 0.70f, 0.52f, 0.50f, 0.80f),
        "oyun" to MoodProfile(0.70f, 0.60f, 0.45f, 0.42f, 0.85f),
        "müzik" to MoodProfile(0.45f, 0.75f, 0.22f, 0.72f, 0.72f),
        "dans" to MoodProfile(0.60f, 0.78f, 0.25f, 0.70f, 0.68f),

        // ── Komedi ───────────────────────────────────────────────────────
        "komik" to MoodProfile(0.55f, 0.85f, 0.15f, 0.72f, 0.55f),
        "eğlence" to MoodProfile(0.60f, 0.85f, 0.15f, 0.70f, 0.55f),
        "parodi" to MoodProfile(0.58f, 0.88f, 0.12f, 0.68f, 0.52f),

        // ── İngilizce / ek Türkçe manga anahtar kelimeleri (Başlık + Açıklama) ──
        "chainsaw" to MoodProfile(0.92f, 0.20f, 0.90f, 0.25f, 0.72f),
        "devil" to MoodProfile(0.78f, 0.20f, 0.88f, 0.25f, 0.68f),
        "demon" to MoodProfile(0.80f, 0.22f, 0.85f, 0.28f, 0.70f),
        "iblis" to MoodProfile(0.80f, 0.22f, 0.85f, 0.28f, 0.70f),
        "şeytan" to MoodProfile(0.75f, 0.18f, 0.88f, 0.25f, 0.68f),
        "fiend" to MoodProfile(0.78f, 0.20f, 0.86f, 0.25f, 0.68f),
        "hunter" to MoodProfile(0.82f, 0.35f, 0.72f, 0.32f, 0.78f),
        "action" to MoodProfile(0.88f, 0.42f, 0.75f, 0.35f, 0.75f),
        "horror" to MoodProfile(0.55f, 0.12f, 0.92f, 0.20f, 0.60f),
        "gore" to MoodProfile(0.75f, 0.12f, 0.92f, 0.20f, 0.62f),
        "blood" to MoodProfile(0.65f, 0.15f, 0.88f, 0.25f, 0.60f),
        "supernatural" to MoodProfile(0.60f, 0.32f, 0.78f, 0.35f, 0.72f),
        "shounen" to MoodProfile(0.82f, 0.55f, 0.65f, 0.45f, 0.75f),
        "shonen" to MoodProfile(0.82f, 0.55f, 0.65f, 0.45f, 0.75f),
        "seinen" to MoodProfile(0.65f, 0.32f, 0.75f, 0.35f, 0.78f),
        "dark fantasy" to MoodProfile(0.75f, 0.22f, 0.88f, 0.28f, 0.72f),
        "monster" to MoodProfile(0.80f, 0.28f, 0.78f, 0.28f, 0.72f),
        "fight" to MoodProfile(0.88f, 0.38f, 0.75f, 0.35f, 0.78f),
        "battle" to MoodProfile(0.90f, 0.35f, 0.80f, 0.35f, 0.75f),
        "war" to MoodProfile(0.88f, 0.28f, 0.85f, 0.30f, 0.72f),
        "death" to MoodProfile(0.50f, 0.15f, 0.86f, 0.25f, 0.62f),
        "revenge" to MoodProfile(0.72f, 0.18f, 0.86f, 0.28f, 0.68f),
        "murder" to MoodProfile(0.52f, 0.15f, 0.88f, 0.25f, 0.65f),
        "mystery" to MoodProfile(0.45f, 0.32f, 0.76f, 0.35f, 0.75f),
        "thriller" to MoodProfile(0.65f, 0.22f, 0.88f, 0.28f, 0.70f),
        "psychological" to MoodProfile(0.48f, 0.20f, 0.86f, 0.28f, 0.75f),
        "zombie" to MoodProfile(0.68f, 0.12f, 0.90f, 0.20f, 0.60f),
        "vampire" to MoodProfile(0.58f, 0.25f, 0.78f, 0.35f, 0.62f),
        "ghost" to MoodProfile(0.38f, 0.18f, 0.85f, 0.30f, 0.58f),
        "curse" to MoodProfile(0.52f, 0.18f, 0.84f, 0.28f, 0.62f),
        "apocalypse" to MoodProfile(0.62f, 0.15f, 0.88f, 0.22f, 0.62f),
        "dungeon" to MoodProfile(0.85f, 0.35f, 0.75f, 0.30f, 0.80f),
        "magic" to MoodProfile(0.62f, 0.70f, 0.42f, 0.55f, 0.82f),
        "dragon" to MoodProfile(0.78f, 0.58f, 0.58f, 0.45f, 0.75f),
        "fantasy" to MoodProfile(0.65f, 0.68f, 0.45f, 0.55f, 0.78f),
        "adventure" to MoodProfile(0.70f, 0.70f, 0.42f, 0.52f, 0.78f),
        "isekai" to MoodProfile(0.65f, 0.65f, 0.45f, 0.52f, 0.76f),
        "reincarnat" to MoodProfile(0.58f, 0.58f, 0.45f, 0.55f, 0.78f),
        "romance" to MoodProfile(0.35f, 0.80f, 0.25f, 0.75f, 0.58f),
        "love" to MoodProfile(0.35f, 0.80f, 0.25f, 0.75f, 0.58f),
        "comedy" to MoodProfile(0.58f, 0.85f, 0.15f, 0.72f, 0.55f),
        "school" to MoodProfile(0.38f, 0.70f, 0.28f, 0.70f, 0.78f),
        "slice of life" to MoodProfile(0.22f, 0.75f, 0.12f, 0.75f, 0.75f),
        "drama" to MoodProfile(0.42f, 0.35f, 0.62f, 0.58f, 0.65f),
        "cyberpunk" to MoodProfile(0.75f, 0.30f, 0.78f, 0.28f, 0.82f),
        "samurai" to MoodProfile(0.78f, 0.40f, 0.72f, 0.38f, 0.82f),
        "ninja" to MoodProfile(0.82f, 0.42f, 0.72f, 0.35f, 0.80f),
        "martial arts" to MoodProfile(0.88f, 0.45f, 0.72f, 0.38f, 0.80f)
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Çok dilli tür eşlemesi
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Kaynak sitelerin **kendi dilinde** döndürdüğü tür adları.
     *
     * **Neden gerekli:** Kotatsu/Mihon kaynaklarından gelen `MangaTag.title`
     * kaynağın dilinde olur. Rusça sitede "Романтика", Endonezce sitede
     * "Romantis" gelir. `SearchTranslation` yalnızca TR+EN bildiği ve
     * `BangumiTagDictionary` CJK ağırlıklı olduğu için (58k satırda
     * yalnızca 15 Kiril kaydı, 0 Arapça) bu diller **boşta kalıyordu**.
     *
     * Tablo **küçük ve sabit** tutuldu: her dilde en çok kullanılan 25 tür.
     * Amaç sözlük olmak değil, en sık 10 dili köprülemek.
     */
    /**
     * [multilingualGenres] tablosunu **tamamlayan** ek eşlemeler.
     *
     * Ayrı tutulur ki ana tablo (200 giriş) okunabilir kalsın ve kapsam
     * testlerinde kaçtığı ölçülen türler tek yerde toplansın.
     * Anahtar çakışırsa ana tablo kazanır (`+` sağdakinin üzerine yazar —
     * bu yüzden eklemeler yalnızca ana tabloda OLMAYAN anahtarları içerir).
     */
    private val extraGenres: Map<String, String> = mapOf(
        // Reenkarnasyon / yeniden doğuş varyantları
        "reincarnation" to "Reincarnation",
        "reenkarnasyon" to "Reincarnation",
        "yeniden dogus" to "Reincarnation",
        "реинкарнация" to "Reincarnation",
        "reencarnacion" to "Reincarnation",
        "reencarnacao" to "Reincarnation",
        "reencarnação" to "Reincarnation",
        "réincarnation" to "Reincarnation",
        "reincarnazione" to "Reincarnation",
        "wiedergeburt" to "Reincarnation",
        "reinkarnasi" to "Reincarnation",
        "تناسخ" to "Reincarnation",
        // Kader
        "kader" to "Destiny",
        "destiny" to "Destiny",
        "fate" to "Destiny",
        // Harem varyantları
        "harém" to "Harem",
        "harem" to "Harem",
        "حريم" to "Harem",
        // Alt türler
        "wuxia" to "Wuxia",
        "武侠" to "Wuxia",
        "steampunk" to "Steampunk",
        "space opera" to "Space Opera",
        "body horror" to "Body Horror",
        "cosmic horror" to "Cosmic Horror",
        "coming of age" to "Coming of Age",
        "found family" to "Found Family",
        "time loop" to "Time Loop",
        "time travel" to "Time Travel",
        "zaman yolculugu" to "Time Travel",
        "sanal dunya" to "Virtual World",
        "oyun dunyasi" to "Game World",
        // Bangumi sözlüğünde BULUNMAYAN Japonca/Çince/Arapça karşılıklar
        // (tags.tsv taraması: スリラー ve アドベンチャー hiç yok)
        "スリラー" to "Thriller",
        "サスペンス" to "Thriller",
        "アドベンチャー" to "Adventure",
        "冒険" to "Adventure",
        "歴史" to "Historical",
        "史実" to "Historical",
        "历史" to "Historical",
        "アクション" to "Action",
        "コメディ" to "Comedy",
        "ギャグ" to "Comedy",
        "恋愛" to "Romance",
        "ファンタジー" to "Fantasy",
        "ミステリー" to "Mystery",
        "サスペンス" to "Thriller",
        "ホラー" to "Horror",
        "日常" to "Slice of Life",
        "スポーツ" to "Sports",
        "音楽" to "Music",
        "학원" to "School",
        "액션" to "Action",
        "로맨스" to "Romance",
        "판타지" to "Fantasy",
        "공포" to "Horror",
        "미스터리" to "Mystery",
        "드라마" to "Drama",
        "코미디" to "Comedy",
        "일상" to "Slice of Life",
        "스릴러" to "Thriller",
        // Arapça varyantlar (farklı yazım)
        "خيال علمي" to "Sci-Fi",
        "خيال علمى" to "Sci-Fi",
        "شريحة من الحياة" to "Slice of Life",
        "نفسي" to "Psychological",
        "علم النفس" to "Psychological",
        "حريم" to "Harem",
        "تاريخي" to "Historical",
        "مغامرة" to "Adventure",
        "رعب" to "Horror",
        "كوميدي" to "Comedy",
        "غموض" to "Mystery",
        "إثارة" to "Thriller",
        "رياضة" to "Sports",
        "موسيقى" to "Music",
        "مدرسي" to "School",
        "خارق للطبيعة" to "Supernatural",
        "فنون قتالية" to "Martial Arts",
        "عسكري" to "Military"
    )

    private val multilingualGenres: Map<String, String> = extraGenres + mapOf(
        // ── RUSÇA ────────────────────────────────────────────────────────
        "романтика" to "Romance",
        "боевик" to "Action",
        "приключения" to "Adventure",
        "комедия" to "Comedy",
        "драма" to "Drama",
        "фэнтези" to "Fantasy",
        "ужасы" to "Horror",
        "мистика" to "Mystery",
        "триллер" to "Thriller",
        "психология" to "Psychological",
        "спорт" to "Sports",
        "школа" to "School",
        "повседневность" to "Slice of Life",
        "сверхъестественное" to "Supernatural",
        "исторический" to "Historical",
        "военное" to "Military",
        "космос" to "Space",
        "меха" to "Mecha",
        "музыка" to "Music",
        "вампиры" to "Vampire",
        "зомби" to "Zombie",
        "postапокалипсис" to "Post-Apocalyptic",
        "постапокалипсис" to "Post-Apocalyptic",
        "боевые искусства" to "Martial Arts",
        "сэйнэн" to "Seinen",
        "сёнэн" to "Shounen",
        "сёдзё" to "Shoujo",
        "дзёсэй" to "Josei",
        "этти" to "Ecchi",
        "гарем" to "Harem",

        // ── ENDONEZCE ────────────────────────────────────────────────────
        "romantis" to "Romance",
        "aksi" to "Action",
        "petualangan" to "Adventure",
        "komedi" to "Comedy",
        "drama" to "Drama",
        "fantasi" to "Fantasy",
        "horor" to "Horror",
        "misteri" to "Mystery",
        "thriller" to "Thriller",
        "psikologis" to "Psychological",
        "olahraga" to "Sports",
        "sekolah" to "School",
        "kehidupan sehari-hari" to "Slice of Life",
        "supranatural" to "Supernatural",
        "sejarah" to "Historical",
        "militer" to "Military",
        "luar angkasa" to "Space",
        "mecha" to "Mecha",
        "musik" to "Music",
        "vampir" to "Vampire",
        "zombi" to "Zombie",
        "pasca-apokaliptik" to "Post-Apocalyptic",
        "seni bela diri" to "Martial Arts",
        "sein" to "Seinen",
        "shonen" to "Shounen",
        "shojo" to "Shoujo",
        "josei" to "Josei",
        "ekusi" to "Ecchi",

        // ── İSPANYOLCA ───────────────────────────────────────────────────
        "romance" to "Romance",
        "acción" to "Action",
        "aventura" to "Adventure",
        "comedia" to "Comedy",
        "fantasía" to "Fantasy",
        "terror" to "Horror",
        "misterio" to "Mystery",
        "suspenso" to "Thriller",
        "psicológico" to "Psychological",
        "deportes" to "Sports",
        "escolar" to "School",
        "recuentos de la vida" to "Slice of Life",
        "sobrenatural" to "Supernatural",
        "histórico" to "Historical",
        "militar" to "Military",
        "espacio" to "Space",
        "música" to "Music",
        "vampiro" to "Vampire",
        "apocalíptico" to "Post-Apocalyptic",
        "artes marciales" to "Martial Arts",
        "seinen" to "Seinen",

        // ── PORTEKİZCE ───────────────────────────────────────────────────
        "ação" to "Action",
        "comédia" to "Comedy",
        "fantasia" to "Fantasy",
        "mistério" to "Mystery",
        "suspense" to "Thriller",
        "esportes" to "Sports",
        "escola" to "School",
        "espaço" to "Space",
        "zumbi" to "Zombie",
        "artes marciais" to "Martial Arts",

        // ── FRANSIZCA ────────────────────────────────────────────────────
        "action" to "Action",
        "aventure" to "Adventure",
        "comédie" to "Comedy",
        "drame" to "Drama",
        "fantastique" to "Fantasy",
        "horreur" to "Horror",
        "mystère" to "Mystery",
        "psychologique" to "Psychological",
        "sport" to "Sports",
        "école" to "School",
        "tranche de vie" to "Slice of Life",
        "surnaturel" to "Supernatural",
        "historique" to "Historical",
        "militaire" to "Military",
        "espace" to "Space",
        "musique" to "Music",
        "vampire" to "Vampire",
        "zombie" to "Zombie",
        "arts martiaux" to "Martial Arts",

        // ── ALMANCA ──────────────────────────────────────────────────────
        "aktion" to "Action",
        "abenteuer" to "Adventure",
        "komödie" to "Comedy",
        "fantasie" to "Fantasy",
        "horror" to "Horror",
        "mystery" to "Mystery",
        "psychologisch" to "Psychological",
        "schule" to "School",
        "alltag" to "Slice of Life",
        "übernatürlich" to "Supernatural",
        "historisch" to "Historical",
        "militär" to "Military",
        "weltraum" to "Space",
        "kampfkunst" to "Martial Arts",

        // ── İTALYANCA ────────────────────────────────────────────────────
        "azione" to "Action",
        "avventura" to "Adventure",
        "commedia" to "Comedy",
        "dramma" to "Drama",
        "fantasy" to "Fantasy",
        "orrore" to "Horror",
        "mistero" to "Mystery",
        "psicologico" to "Psychological",
        "scuola" to "School",
        "fetta di vita" to "Slice of Life",
        "soprannaturale" to "Supernatural",
        "storico" to "Historical",
        "militare" to "Military",
        "spazio" to "Space",
        "musica" to "Music",
        "arti marziali" to "Martial Arts",

        // ── ARAPÇA ───────────────────────────────────────────────────────
        "رومانسي" to "Romance",
        "أكشن" to "Action",
        "مغامرة" to "Adventure",
        "كوميدي" to "Comedy",
        "دراما" to "Drama",
        "فانتازيا" to "Fantasy",
        "رعب" to "Horror",
        "غموض" to "Mystery",
        "إثارة" to "Thriller",
        "رياضة" to "Sports",
        "مدرسة" to "School",
        "خارق للطبيعة" to "Supernatural",
        "تاريخي" to "Historical",
        "عسكري" to "Military",
        "فضاء" to "Space",
        "موسيقى" to "Music",
        "فنون قتالية" to "Martial Arts",

        // ── TÜRKÇE (SearchTranslation'ın kaçırdığı varyantlar) ───────────
        "aşk" to "Romance",
        "romantizm" to "Romance",
        "bilimkurgu" to "Sci-Fi",
        "bilim kurgu" to "Sci-Fi",
        "yaşam dilimi" to "Slice of Life",
        "gündelik yaşam" to "Slice of Life",
        "macera" to "Adventure",
        "psikolojik" to "Psychological",
        "gerilim" to "Thriller",
        "gizem" to "Mystery",
        "korku" to "Horror",
        "aksiyon" to "Action",
        "fantastik" to "Fantasy",
        "fantezi" to "Fantasy",
        "dövüş sanatları" to "Martial Arts",
        "doğaüstü" to "Supernatural",
        "okul hayatı" to "School",
        "günlük yaşam" to "Slice of Life",
        "tarihi" to "Historical",
        "askeri" to "Military",
        "uzay" to "Space",
        "müzik" to "Music",
        "trajedi" to "Tragedy",
        "intikam" to "Revenge",
        "ihanet" to "Betrayal",
        "büyü" to "Magic",
        "sihir" to "Magic",
        "ejderha" to "Fantasy",
        "zindan" to "Fantasy",
        "seviye atlama" to "Leveling",
        "reankarnasyon" to "Reincarnation",
        "regresyon" to "Regression",
        "zamanda geri" to "Regression",
        "gurme" to "Gourmet",
        "yemek" to "Gourmet",
        "spor" to "Sports",
        "futbol" to "Sports",
        "okul" to "School",
        "lise" to "School",
        "üniversite" to "School",
        "dilim" to "Slice of Life",
        "acı" to "Tragedy",

        "melankoli" to "Tragedy",

    ) + extraGenres

    /**
     * Tür adını İngilizce karşılığına çözer.
     *
     * **Neden 4 kademe:** her kaynak farklı dilde etiket veriyor. Tek bir
     * tabloya güvenmek, o tablonun kapsamadığı dilde tüm sinyali
     * kaybetmek demek. Kademeler ucuzdan pahalıya sıralı; ilk eşleşen
     * döner.
     */
    /**
     * Türkçe büyük `İ` tuzağı: Kotlin `lowercase()` bunu `i` + U+0307
     * (birleşik nokta) olarak üretir. `"İhanet".lowercase()` = `"i̇hanet"`
     * (2 kod noktası) ve hiçbir tablo anahtarıyla eşleşmez.
     *
     * Çözüm: yalnızca **arama anahtarı** üretilirken ASCII'ye indirgenir.
     * Görüntülenen değer (matchedGenres) bozulmaz — ham etiket korunur.
     */
    private fun foldKey(raw: String): String {
        val lower = raw.trim().lowercase().replace("\u0307", "")
        // Latin aksanlarını indir (é→e, ã→a, ñ→n ...). Kiril/Arap dokunulmaz:
        // NFKD onların harflerini değiştirmez, ayrıştırma yalnızca birleşik
        // işaretleri ayırır ve sonra süzülür.
        val decomposed = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFKD)
        return buildString(decomposed.length) {
            for (ch in decomposed) {
                if (ch.code in 0x0300..0x036F) continue   // birleşik işaret
                append(ch)
            }
        }
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')
    }

    /** [foldKey] uygulanmış halde mi? Boşsa false. */
    private fun String.hasFoldVariant(): Boolean = isNotBlank()

    private fun resolveToEnglish(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return cleaned

        // ── Sıralama ÖNEMLİ ────────────────────────────────────────────────
        // Bangumi sözlüğü tek başına güvenilir değil: aynı Türkçe etiket
        // birden fazla İngilizce anahtara bağlanabiliyor. Gerçek örnek:
        //     betrayal  → İhanet → Betrayal
        //     infidelity→ İhanet → Infidelity
        // `byLabel` "son kazanan" olduğu için "İhanet" → "Infidelity"
        // dönüyordu (aldatma). Yanlış duygu profili = yanlış müzik.
        //
        // Bu yüzden sözlük "öneri" olarak kullanılır, "karar" olarak değil:
        // yalnızca ürettiği İngilizce ad [GenreMoodMapper] tarafından
        // TANINIYORSA kabul edilir.

        val folded = foldKey(cleaned)

        // 1) Çok dilli tablo — en güvenilir (elle, tek amaçla yazıldı)
        multilingualGenres[cleaned.lowercase()]?.let { return it }
        multilingualGenres[folded]?.let { return it }

        // 2) Uygulamanın kendi çeviri altyapısı (TR ↔ EN, elle bakımlı)
        searchTranslation(cleaned)?.let { return it }
        if (folded != cleaned.lowercase()) searchTranslation(folded)?.let { return it }

        // 3) Bangumi sözlüğü — yalnızca tanınan bir tür üretiyorsa
        runCatching {
            com.kitsugi.animelist.utils.BangumiTagDictionary.entryFor(cleaned)
        }.getOrNull()?.let { entry ->
            val english = entry.english
            if (english.isNotBlank() && isKnownGenre(english)) return english
        }

        // 4) Dokunma — zaten İngilizce
        return cleaned
    }

    /**
     * [SearchTranslation]'ı güvenli biçimde çağırır.
     *
     * @return Çevrilmiş İngilizce ad; değişmediyse `null`
     */
    private fun searchTranslation(label: String): String? = runCatching {
        com.kitsugi.animelist.ui.screens.search.SearchTranslation
            .translateToEnglishForSearch(label)
    }.getOrNull()?.takeIf { !it.equals(label, ignoreCase = true) }

    /**
     * Üretilen İngilizce adın gerçekten bir duygu profiline çözülüp
     * çözülmediğini söyler. [GenreMoodMapper] + [GenreFamilyResolver] kapsar.
     */
    private fun isKnownGenre(english: String): Boolean =
        GenreMoodMapper.profileOf(english) != null

    /**
     * Format/eşanlam kelimeleri — **duygu katkısı yok**.
     *
     * ⚠️ DİKKAT: `manhwa`, `webtoon`, `manhua` burada **DEĞİL**.
     * Denetimde bulundu: `GenreMoodMapper` bunları hafif olumlu bir
     * profile eşliyor (0.50/0.60/0.35/0.55/0.80). Bu listeye koymak o
     * eşleşmeyi ezer ve Korean/Çin çizgi romanlarında tüm sinyali
     * kaybettirirdi — Türkçe kaynaklarda çok yaygın bir etiket.
     *
     * Buradakiler gerçekten **içerik sinyali taşımayan** idari etiketler.
     */
    private val formatWords = setOf(
        "manga", "comic", "çizgi roman", "colored", "renkli", "türkçe",
        "tamamlandı", "devam ediyor", "ongoing", "completed", "oneshot",
        "one shot", "web comic", "full", "tamamlanmış"
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Ana giriş
    // ─────────────────────────────────────────────────────────────────────

    /**
     * İçerikten duygu profili çıkarır.
     *
     * @param genres      MangaDetails.genre
     * @param title       MangaDetails.title
     * @param description MangaDetails.description
     */
    fun resolve(
        genres: List<String>,
        title: String? = null,
        description: String? = null
    ): Result {
        var e = 0f; var v = 0f; var t = 0f; var w = 0f; var f = 0f
        var totalWeight = 0f
        val matchedGenres = mutableListOf<String>()
        val matchedKeywords = mutableListOf<String>()
        var confidence = Confidence.NONE

        // ── KATMAN 1: türler (ağırlık 2.0, ilk 3 sıralı) ─────────────────
        //
        // 🔑 Uygulamanın MEVCUT çeviri altyapısı kullanılır:
        //    SearchTranslation.translateToEnglishForSearch()
        //    → "Romantizm" = "Romance", "Gerilim" = "Thriller", ...
        //
        // Böylece Türkçe/İngilizce/Çince (Bangumi) tür adlarının hepsi
        // tek bir İngilizce anahtara düşer ve GenreMoodMapper tablosu
        // YALNIZCA İngilizce tutabilir. Ayrı bir Türkçe tablo yok.
        val expandedGenres = genres
            .flatMap { it.split(',', ';', '/', '|', '\n') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        expandedGenres.take(8).forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed

            // ── Tür adını İngilizceye çevir (4 kademeli) ────────────────
            // 1. Bangumi sözlüğü (58k satır, CJK + TR + EN)
            // 2. Çok dilli tablo (RU/ID/ES/PT/FR/DE/IT/AR/TR)
            // 3. SearchTranslation (uygulamanın TR↔EN altyapısı)
            // 4. Olduğu gibi (zaten İngilizce olabilir)
            val english = resolveToEnglish(raw)

            val normalized = GenreMoodMapper.normalize(english)
            if (normalized in formatWords) return@forEachIndexed
            if (normalized.isBlank()) return@forEachIndexed

            val profile = GenreMoodMapper.profileOf(english)
            if (profile != null) {
                // İlk türler daha belirleyici (kaynaklar önem sırasına dizer)
                val weight = when (index) {
                    0 -> 2.0f
                    1 -> 1.5f
                    else -> 1.2f
                }
                // Kullanıcıya gösterilecek ad: çevrilmiş Türkçe hâli
                matchedGenres += raw.trim()
                e += profile.energy * weight
                v += profile.valence * weight
                t += profile.tension * weight
                w += profile.warmth * weight
                f += profile.focus * weight
                totalWeight += weight
                if (confidence.ordinal < Confidence.HIGH.ordinal) confidence = Confidence.HIGH
            } else {
                // Tür tablosunda yok → anahtar kelime olarak dene
                keywordTable[normalized]?.let { kw ->
                    val weight = 1.2f
                    matchedKeywords += normalized
                    e += kw.energy * weight
                    v += kw.valence * weight
                    t += kw.tension * weight
                    w += kw.warmth * weight
                    f += kw.focus * weight
                    totalWeight += weight
                    if (confidence.ordinal < Confidence.MEDIUM.ordinal) {
                        confidence = Confidence.MEDIUM
                    }
                }
            }
        }

        // ── KATMAN 2: açıklama + başlık anahtar kelimeleri (ağırlık 0.6) ──
        // Açıklama uzun; her kelimeyi değil, tablodaki ifadeleri arıyoruz.
        val haystack = buildString {
            if (!title.isNullOrBlank()) append(title.lowercase()).append(' ')
            if (!description.isNullOrBlank()) append(description.lowercase())
        }

        if (haystack.isNotBlank()) {
            var descHits = 0
            keywordTable.forEach { (keyword, profile) ->
                if (descHits >= 4) return@forEach   // aşırıya kaçma
                if (keyword in haystack && keyword !in matchedKeywords) {
                    val weight = 0.6f
                    matchedKeywords += keyword
                    e += profile.energy * weight
                    v += profile.valence * weight
                    t += profile.tension * weight
                    w += profile.warmth * weight
                    f += profile.focus * weight
                    totalWeight += weight
                    descHits++
                    if (confidence.ordinal < Confidence.LOW.ordinal) {
                        confidence = Confidence.LOW
                    }
                }
            }
        }

        // ── Sonuç ────────────────────────────────────────────────────────
        if (totalWeight <= 0f) {
            return Result(
                mood = MoodProfile.Neutral,
                matchedGenres = emptyList(),
                matchedKeywords = emptyList(),
                confidence = Confidence.NONE,
                reason = "Tür/açıklama tanınmadı — dengeli başlangıç"
            )
        }

        val mood = MoodProfile(
            energy = (e / totalWeight).coerceIn(0f, 1f),
            valence = (v / totalWeight).coerceIn(0f, 1f),
            tension = (t / totalWeight).coerceIn(0f, 1f),
            warmth = (w / totalWeight).coerceIn(0f, 1f),
            focus = (f / totalWeight).coerceIn(0f, 1f)
        )

        val reason = when {
            matchedGenres.isNotEmpty() ->
                "${matchedGenres.take(2).joinToString(", ")} türüne göre"
            matchedKeywords.isNotEmpty() ->
                "\"${matchedKeywords.take(2).joinToString(", ")}\" içeriğine göre"
            else -> "Dengeli başlangıç"
        }

        return Result(
            mood = mood,
            matchedGenres = matchedGenres,
            matchedKeywords = matchedKeywords,
            confidence = confidence,
            reason = reason
        )
    }
}
