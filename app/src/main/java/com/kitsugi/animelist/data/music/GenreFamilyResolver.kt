package com.kitsugi.animelist.data.music

/**
 * 🧩 Tür Ailesi Çözümleyici — kapsam garantisi katmanı
 *
 * ## Neden var?
 *
 * [GenreMoodMapper] elle bakımlı bir tablo tutar (128 tür). Ama uygulamanın
 * çeviri altyapısı (`SearchTranslation.englishToTurkish`, 266 tür) ve
 * `BangumiTagDictionary` (58.338 satır) çok daha geniş. Ölçtük: zincirden
 * çıkan **266 İngilizce türün 221'i** tabloda yoktu → o türler sessizce
 * `Neutral`'a düşüyordu (%83 kör).
 *
 * 221 türü tek tek tabloya eklemek kırılgan: yeni kaynak = yeni tür = yeni
 * kör nokta. Bunun yerine her tür adı **14 semantik aileden** birine
 * çözümlenir. Tanınmayan bir tür bile ailesi üzerinden makul bir profile
 * düşer — tablo bakımı gerekmez.
 *
 * ## Sıra
 *
 * 1. [GenreMoodMapper] birebir eşleşme (en isabetli, elle ayarlanmış)
 * 2. Bu sınıfın aile eşleşmesi (kapsam garantisi)
 * 3. `Neutral` (yalnızca gerçekten tanınmayan)
 *
 * ## Aile profilleri
 *
 * Profiller mevcut [GenreMoodMapper] girişlerinin ortalaması alınarak
 * seçildi; böylece aynı türün hem birebir hem aile yoluyla çözülmesi
 * birbirine yakın sonuç verir.
 */
object GenreFamilyResolver {

    /**
     * Tür ailesi. Her aile 5 boyutlu tek bir duygu profiline karşılık gelir.
     */
    enum class Family(val profile: MoodProfile) {
        /** Dövüş, rekabet, yüksek tempo. */
        ACTION(MoodProfile(0.88f, 0.60f, 0.55f, 0.35f, 0.50f)),

        /** Duygusal yakınlık, sıcaklık. */
        ROMANCE(MoodProfile(0.28f, 0.78f, 0.15f, 0.85f, 0.60f)),

        /** Dehşet, tehdit, yüksek gerilim. */
        HORROR(MoodProfile(0.35f, 0.12f, 0.92f, 0.20f, 0.65f)),

        /** Büyü, keşif, hayal gücü. */
        FANTASY(MoodProfile(0.55f, 0.70f, 0.32f, 0.55f, 0.70f)),

        /** Sakin, sıcak, düşük tempo gündelik yaşam. */
        COZY(MoodProfile(0.20f, 0.75f, 0.10f, 0.70f, 0.55f)),

        /** Hüzün, ağır duygusal yük. */
        DRAMA(MoodProfile(0.30f, 0.28f, 0.45f, 0.40f, 0.65f)),

        /** Merak, çözülmeyi bekleyen sır, zihinsel geren. */
        MYSTERY(MoodProfile(0.40f, 0.35f, 0.70f, 0.35f, 0.80f)),

        /** Mücadele, antrenman, takım ruhu. */
        SPORT(MoodProfile(0.80f, 0.68f, 0.45f, 0.45f, 0.70f)),

        /** Teknoloji, uzay, distopya, soğuk atmosfer. */
        SCIFI(MoodProfile(0.45f, 0.58f, 0.40f, 0.30f, 0.85f)),

        /** Geçmiş, savaş, onur, epik ölçek. */
        HISTORICAL(MoodProfile(0.62f, 0.45f, 0.55f, 0.40f, 0.75f)),

        /** Müzik, resim, sahne, yaratıcılık. */
        ART(MoodProfile(0.45f, 0.70f, 0.25f, 0.65f, 0.65f)),

        /** Yemek, gündelik emek, sıcak rutinler. */
        FOOD(MoodProfile(0.35f, 0.75f, 0.12f, 0.80f, 0.50f)),

        /** Oyun dünyası, seviye atlama, macera döngüsü. */
        GAME(MoodProfile(0.60f, 0.62f, 0.35f, 0.45f, 0.70f)),

        /** Doğaüstü varlıklar, ruhani gerilim. */
        SUPERNATURAL(MoodProfile(0.45f, 0.50f, 0.55f, 0.35f, 0.60f));

        companion object {
            /** Aile bulunamadığında kullanılan varsayılan. */
            val FALLBACK = SUPERNATURAL
        }
    }

    /**
     * Aile → anahtar kelimeler.
     *
     * **Önemli:** her liste *kendi* ailesinin tipik sözcüklerini içerir.
     * Sıralama önemlidir — ilk eşleşen aile kazanır, bu yüzden daha
     * belirleyici aileler (HORROR, ROMANCE) önce gelir.
     */
    private val signatures: List<Pair<Family, List<String>>> = listOf(
        Family.HORROR to listOf(
            "horror", "korku", "terror", "horreur", "orrore", "horror", "ужас",
            "gore", "body horror", "cosmic horror", "creepy", "zombie", "zombi",
            "vampire", "vampir", "ghost", "hayalet", "demon", "şeytan", "curse",
            "lanet", "curses", "occult", "exorcism", "şeytani", "monster",
            "canavar", "kaiju", "werewolf", "kurt adam", "succubus", "skeleton",
            "necromancy", "torture", "işkence", "death game", "ölüm oyunu",
            "survival", "hayatta kalma", "pandemic", "salgın", "cannibal",
            "splatter", "psikopat", "creature", "yaratık", "yokai", "youkai",
            "grim", "dark", "karanlık", "kabus", "nightmare", "suspiria"
        ),
        Family.ROMANCE to listOf(
            "romance", "romantik", "romantik", "romantizm", "romantico", "romantique",
            "liebe", "amore", "amor", "романтика", "romantis", "romantika",
            "love", "aşk", "sevgi", "shoujo", "shojo", "josei", "shoujo ai",
            "love triangle", "aşk üçgeni", "love polygon", "unrequited",
            "karşılıksız", "harem", "reverse harem", "ters harem", "marriage",
            "evlilik", "fake relationship", "sahte ilişki", "childhood friend",
            "çocukluk arkadaşı", "tsundere", "yandere", "kuudere", "crush",
            "flirt", "couple", "çift", "sweet", "tatlı", "boys' love", "girls love",
            "yaoi", "yuri", "bl", "gl", "polyamorous", "metres", "hentai",
            "nudity", "çıplaklık", "heterosexual", "heteroseksüel",
            "lgbtq+ themes", "lgbtq", "villainess", "kötü kadın"
        ),
        Family.SCIFI to listOf(
            "sci-fi", "sci fi", "science fiction", "science-fiction", "bilim kurgu",
            "fantascienza", "ficção científica", "ciencia ficción", "ciecia",
            "научная фантастика", "фантастика", "cyberpunk", "siberpunk",
            "dystopia", "distopya", "dystopian", "post-apocalyptic",
            "post apocalyptic", "kıyamet", "apocalypse", "apokaliptik",
            "space", "uzay", "space opera", "spaceship", "astronaut", "astronomi",
            "astronomy", "alien", "uzaylı", "aliens", "robot", "android", "cyborg",
            "mecha", "mek", "ai", "yapay zeka", "virtual reality", "sanal gerçeklik",
            "virtual world", "sanal dünya", "time travel", "zaman yolculuğu",
            "time loop", "zaman döngüsü", "alternate universe", "paralel evren",
            "clone", "klon", "clones", "steampunk", "future", "gelecek",
            "technology", "teknoloji", "hologram", "simulation", "simülasyon",
            "apocalíptico", "ficção", "sf", "サイエンス", "科幻", "artificial intelligence"
        ),
        Family.FANTASY to listOf(
            "fantasy", "fantastik", "fantezi", "fantasía", "fantasie", "fantasia",
            "fantastique", "фэнтези", "ファンタジー", "奇幻", "玄幻", "魔幻",
            "magic", "büyü", "sihir", "wizard", "büyücü", "witch", "cadı",
            "alchemy", "simya", "dragon", "ejderha", "elf", "dwarf", "cüce",
            "fairy", "peri", "fairy tale", "masal", "mythology", "mitoloji",
            "isekai", "reincarnation", "reankarnasyon", "yeniden doğuş",
            "reincarnated", "regression", "regresyon", "cultivation", "murim",
            "wuxia", "武侠", "xianxia", "sword", "kılıç", "swordplay", "spearplay",
            "adventure", "macera", "aventura", "abenteuer", "avventura",
            "приключения", "petualangan", "quest", "görev", "dungeon", "zindan",
            "kingdom", "krallık", "royal", "kraliyet", "shapeshifting", "şekil değiştirme",
            "creature taming", "kanat", "angel", "melek", "demon lord",
            "magi", "arcane", "spell", "büyülü", "sihirli"
        ),
        Family.MYSTERY to listOf(
            "mystery", "gizem", "misterio", "mistero", "mystère", "mystery",
            "мистика", "ミステリー", "悬疑", "غموض", "misteri", "mystère",
            "detective", "dedektif", "crime", "suç", "police", "polis", "cop",
            "investigation", "soruşturma", "murder", "cinayet", "killer", "katil",
            "thriller", "gerilim", "suspense", "suspenso", "thriller", "триллер",
            "スリラー", "惊悚", "إثارة", "psychological", "psikolojik",
            "psicologico", "psychologisch", "психология", "психологический", "نفسي",
            "conspiracy", "komplo", "espionage", "casusluk", "spy", "casus",
            "mafia", "mafya", "yakuza", "gang", "çete", "gangster", "gangs",
            "noir", "heist", "soygun", "prison", "hapishane", "fugitive", "kaçak",
            "assassins", "suikastçı", "brainwashing", "beyin yıkama",
            "memory manipulation", "hafıza", "amnesia", "hafıza kaybı",
            "death note", "torture", "stalking", "sır", "dedektiflik"
        ),
        Family.ACTION to listOf(
            "action", "aksiyon", "acción", "azione", "aktion", "action",
            "боевик", "アクション", "动作", "أكشن", "aksi",
            "fight", "dövüş", "savaş", "war", "battle", "savaş", "combat",
            "martial arts", "dövüş sanatları", "kılıç sanatları", "kampspart",
            "kampfkunst", "arti marziali", "artes marciales", "боевые искусства",
            "seni bela diri", "arts martiaux", "武術", "武侠", "samurai", "samuray",
            "ninja", "swordsman", "battle royale", "turnuva", "tournament",
            "military", "askeri", "militar", "militare", "militär", "военное",
            "militer", "army", "ordu", "gun", "silah", "guns", "silah",
            "super power", "süper güç", "superhero", "süper kahraman",
            "delinquents", "kabadayı", "bullying", "zorbalık", "revenge",
            "intikam", "betrayal", "ihanet", "hunting", "avcılık", "boxing",
            "boks", "wrestling", "güreş", "mma", "sumo", "fencing", "eskrim",
            "archery", "okçuluk", "assassin", "katliam", "şiddet", "violence",
            "savaşçı", "warrior", "glory", "zafer", "victory", "yenilmez",
            "judo", "karate", "kung fu", "taekwondo", "muay thai", "aikido",
            "hensin", "dönüşüm", "transformation", "human experimentation",
            "insan deneyi", "criminal organization", "suç örgütü"
        ),
        Family.SPORT to listOf(
            "sports", "spor", "deportes", "deportes", "sport", "esporte",
            "спорт", "スポーツ", "运动", "رياضة", "olahraga", "sports",
            "football", "futbol", "soccer", "basketball", "basketbol",
            "baseball", "beyzbol", "volleyball", "voleybol", "tennis", "tenis",
            "badminton", "golf", "rugby", "cycling", "bisiklet", "swimming",
            "yüzme", "athletics", "atletizm", "running", "koşu", "marathon",
            "skating", "buz pateni", "skateboarding", "kaykay", "surfing",
            "sörf", "fishing", "balıkçılık", "archery", "e-sports", "esports",
            "eスポーツ", "training", "antrenman", "coach", "antrenör",
            "championship", "şampiyona", "team", "takım", "league", "lig"
        ),
        Family.ART to listOf(
            "music", "müzik", "musik", "musica", "musique", "музыка",
            "музыка", "音楽", "音乐", "موسيقى", "musik", "band", "grup",
            "idol", "idols", " idols", "singer", "şarkıcı", "song", "şarkı",
            "piano", "piyano", "guitar", "gitar", "violin", "keman",
            "classical music", "klasik müzik", "jazz", "caz", "rock", "pop",
            "hip-hop", "rap", "orchestra", "orkestra", "concert", "konser",
            "acting", "oyunculuk", "theater", "tiyatro", "musical theater",
            "performing arts", "sahne sanatları", "ballet", "balet", "dance",
            "dans", "dancing", "art", "sanat", "painting", "resim", "drawing",
            "çizim", "calligraphy", "hat sanatı", "photography", "fotoğrafçılık",
            "filmmaking", "film yapımı", "animation", "animasyon", "manga",
            "writing", "yazarlık", "poetry", "şiir", "literature", "edebiyat",
            "classic literature", "vtuber", "cosplay", "otaku", "otaku culture",
            "makeup", "makyaj", "fashion", "moda", "modeling", "modellik"
        ),
        Family.FOOD to listOf(
            "cooking", "yemek", "gourmet", "gastronomi", "food", "yiyecek",
            "cuisine", "mutfak", "chef", "şef", "baking", "fırın", "pastry",
            "restaurant", "restoran", "cafe", "kafe", "bar", "pub", "tea",
            "çay", "coffee", "kahve", "sweets", "tatlı", "recipe", "tarif",
            "agriculture", "tarım", "farming", "çiftlik", "farm", "harvest",
            "hasat", "inn", "han", "ryokan", "izakaya", "ramen", "sushi",
            "camping", "kamp", "barbecue", "mangal", "wine", "şarap", "beer",
            "bira", "eat", "yemek yeme", "delicious", "lezzet"
        ),
        Family.COZY to listOf(
            "slice of life", "günlük yaşam", "yaşam dilimi", "günlük",
            "recuentos de la vida", "fetta di vita", "tranche de vie", "alltag",
            "повседневность", "kehidupan sehari-hari", "cotidiano", "日常",
            "生活", "شريحة من الحياة", "daily life", "everyday", "iyashikei",
            "healing", "iyileşme", "iyileştirici", "wholesome", "huzur",
            "relaxing", "rahatlatıcı", "calm", "sakin", "peaceful", "dingin",
            "cozy", "sıcak", "comedy", "komedi", "comedia", "commedia",
            "komödie", "комедия", "コメディ", "喜剧", "كوميدي", "komedi",
            "gag humor", "slapstick", "parody", "parodi", "satire", "hiciv",
            "school life", "okul hayatı", "school", "okul", "escolar", "scuola",
            "schule", "школа", "sekolah", "学校", "校园", "مدرسة", "college",
            "üniversite", "boarding school", "yatılı okul", "school club",
            "kulüp", "everyday", "family", "aile", "family life", "found family",
            "childcare", "çocuk bakımı", "parenthood", "ebeveynlik", "kids",
            "çocuklar", "marriage", "evlilik", "workplace", "iş yeri", "office",
            "ofis", "work", "iş", "travel", "seyahat", "rural", "kırsal",
            "coastal", "sahil", "island", "ada", "camping", "kamp", "tomboy",
            "childhood friend", "çocukluk arkadaşı", "coming of age", "büyüme",
            "movie", "tv movie", "documentary", "belgesel", "educational",
            "eğitici", "children", "çocuk", "cgdct", "chibi", "animals",
            "hayvanlar", "forest", "orman", "nature", "doğa", "twins", "ikizler",
            "teacher", "öğretmen", "rescue", "kurtarma", "firefighters",
            "itfaiye", "rehabilitation", "rehabilitasyon", "fitness", "circus",
            "sirk", "showbiz", "şov dünyası", "crossover", "ensemble cast",
            "topluluk kadrosu", "economics", "ekonomi"
        ),
        Family.DRAMA to listOf(
            "drama", "dram", "dramma", "drame", "драма", "ドラマ", "剧情",
            "دراما", "drama", "tragedy", "trajedi", "tragedia", "trajedi",
            "sad", "hüzün", "melancholy", "melankoli", "depressing", "iç karartıcı",
            "emotional", "duygusal", "tearjerker", "gözyaşı", "grief", "yas",
            "loss", "kayıp", "death", "ölüm", "illness", "hastalık", "medical",
            "tıp", "medicine", "doctor", "doktor", "hospital", "hastane",
            "suicide", "intihar", "bullying", "zorbalık", "abuse", "istismar",
            "poverty", "yoksulluk", "addiction", "bağımlılık", "drugs", "uyuşturucu",
            "divorce", "boşanma", "orphan", "yetim", "slavery", "kölelik",
            "holocaust", "soykırım", "class struggle", "sınıf mücadelesi",
            "politics", "siyaset", "philosophy", "felsefe", "religion", "din",
            "biographical", "biyografik", "autobiographical", "otobiyografik",
            "aging", "yaşlılık", "growing up", "büyüme"
        ),
        Family.HISTORICAL to listOf(
            "historical", "tarihi", "tarih", "historique", "historisch",
            "historico", "storico", "исторический", "sejarah", "歴史", "历史",
            "تاريخي", "ancient", "antik", "ancient china", "antik çin",
            "medieval", "ortaçağ", "edo period", "edo dönemi", "meiji", "sengoku",
            "samurai", "samuray", "ronin", "ninja", "vikings", "vikingler",
            "pirates", "korsanlar", "knight", "şövalye", "chivalry", "şövalyelik",
            "war", "savaş", "military", "askeri", "empire", "imparatorluk",
            "kingdom", "krallık", "royal affairs", "kraliyet", "monarchy",
            "monarşi", "revolution", "devrim", "dynasty", "hanedan", "tribal",
            "kabile", "kabuki", "shogun", "sumo",
            "japan", "japonya", "feudal", "feodal", "imperial",
            "western", "kovboy", "cowboy", "frontier", "sınır", "american civil war"
        ),
        Family.GAME to listOf(
            "gaming", "oyun", "video games", "video game", "video oyunu",
            "games", "game world", "oyun dünyası", "e-sports", "esports",
            "board game", "kutu oyunu", "card battle", "kart oyunu",
            "satranç", "chess", "shogi", "go", "gambling", "kumar", "betting",
            "leveling", "seviye atlama", "level up", "stats", "istatistik",
            "dungeon", "zindan", "rpg", "mmorpg", "guild", "lonca", "quest",
            "görev", "game", "oyun", "play", "oyna", "player", "oyuncu",
            "virtual world", "sanal dünya", "isekai", "dungeon crawl",
            "npc", "boss", "raid", "crafting", "üretim", "trading", "takas",
            "mahjong", "shogi", "go", "tavla", "backsammon", "poker", "rulet"
        ),
        Family.SUPERNATURAL to listOf(
            "supernatural", "doğaüstü", "sobrenatural", "soprannaturale",
            "surnaturel", "übernatürlich", "сверхъестественное", "supranatural",
            "超自然", "超能力", "خارق للطبيعة", "super power", "süper güç",
            "psychic", "medyum", "telepathy", "telepati", "telekinesis",
            "telekinezi", "spirit", "ruh", "afterlife", "ölümden sonra",
            "reincarnation", "reankarnasyon", "regression", "regresyon",
            "time travel", "zaman yolculuğu", "time loop", "zaman döngüsü",
            "body swapping", "beden değiştirme", "gender bending", "cinsiyet",
            "shapeshifting", "şekil değiştirme", "immortality", "ölümsüzlük",
            "healing", "iyileşme", "prophecy", "kehanet", "curse", "lanet",
            "ritual", "ritüel", "paranormal", "paranormal", "occultist",
            "yokai", "youkai", "onmyoji", "shrine", "tapınak", "kitsune",
            "tanrı", "god", "myth", "mit", "legend", "efsane", "omen", "alamet",
            "time manipulation", "zaman manipülasyonu"
        )
    )

    /**
     * Tür adını aileye çözer.
     *
     * Ada tam kelime/ifade olarak bakılır — alt dize eşleşmesi **yoktur**,
     * böylece "art" gibi bir kelimenin "martial arts" içinde yanlışlıkla
     * tetiklenmesi önlenir.
     *
     * @param normalized [GenreMoodMapper.normalize] uygulanmış İngilizce tür adı
     * @return Aile, tanınmazsa `null`
     */
    fun familyOf(normalized: String): Family? {
        if (normalized.isBlank()) return null
        val key = normalized.trim().lowercase()

        // 1) Tam ifade eşleşmesi
        for ((family, words) in signatures) {
            if (words.any { it == key }) return family
        }

        // 2) Kelime bazlı eşleşme — çok kelimeli tür adlarında
        //    ("dark fantasy", "science fiction" gibi) yalnızca belirleyici
        //    kelimeye bakılır. Tek harfli/çok kısa kelimeler atlanır.
        val tokens = key.split(' ').filter { it.length >= 4 }
        if (tokens.isEmpty()) return null
        for ((family, words) in signatures) {
            for (w in words) {
                if (w.length < 4) continue
                if (tokens.any { t -> t == w || (w.length >= 5 && t.startsWith(w)) }) {
                    return family
                }
            }
        }
        return null
    }

    /**
     * Tür için duygu profili.
     *
     * Önce [GenreMoodMapper] birebir tablosuna, sonra aile profiline bakar.
     *
     * @param normalized [GenreMoodMapper.normalize] uygulanmış İngilizce tür adı
     * @return Profil, hiçbir şekilde çözülemezse `null`
     */
    fun profileOf(normalized: String): MoodProfile? {
        GenreMoodMapper.profileOf(normalized)?.let { return it }
        return familyOf(normalized)?.profile
    }

    /** Aile sayısı (teşhis için). */
    val familyCount: Int get() = Family.entries.size
}
