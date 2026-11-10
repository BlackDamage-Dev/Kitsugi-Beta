package com.kitsugi.animelist.ui.screens.search

/**
 * Arama filtre sayfasının (GenresTagsSheet) ve kaynak köprüsünün ORTAK taksonomisi.
 *
 * Eskiden tür/etiket listesi yalnızca Composable'ın içinde yerel bir `listOf` idi;
 * "Tümü" araması ise bu değerleri doğrulayamadığı için AniList dışındaki kaynaklara
 * ya hiç filtre göndermiyor ya da tanınmayan etiketleri API'ye yolluyordu (HTTP 400 →
 * `KTS-COM-1016`). Tek kaynak liste burada: UI ne gösteriyorsa köprü de onu bilir.
 *
 * Tüm değerler API'nin beklediği kanonik İngilizce yazımdır; görünen etiket
 * [SearchTranslation] ile yerelleştirilir.
 */
object SearchTaxonomy {

    /** AniList resmî türleri + demografiler. */
    val anilistGenres: List<String> = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
        "Hentai", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
        "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
        "Supernatural", "Thriller",
        "Shounen", "Shoujo", "Seinen", "Josei", "Kids"
    )

    /** AniList `tag_in` için uygulamanın desteklediği etiketler. */
    val anilistTags: List<String> = listOf(
        // Aksiyon
        "Battle Royale", "Espionage", "Guns", "Martial Arts", "Swordplay", "Archery",
        // Fantazi
        "Isekai", "Reverse Isekai", "Magic", "Super Power", "Superhero", "Mythology",
        "Alchemy", "Cultivation", "Wuxia", "Fairy Tale", "Youkai", "Steampunk",
        "Body Swapping", "Shapeshifting", "Curses", "Exorcism", "Necromancy", "Henshin",
        // Sci-Fi
        "Cyberpunk", "Space Opera", "Time Loop", "Time Manipulation", "Time Travel", "Post-Apocalyptic",
        // Korku / Gerilim
        "Gore", "Body Horror", "Cosmic Horror", "Survival", "Torture",
        // Romantizm
        "Love Triangle", "Love Polygon", "Harem", "Reverse Harem", "Unrequited Love",
        "Fake Relationship", "Boys' Love", "Yuri", "Cohabitation",
        // Dram
        "Coming of Age", "Revenge", "Tragedy", "Conspiracy", "Bullying",
        "Class Struggle", "Rehabilitation", "Kingdom Management",
        // Günlük Hayat
        "Iyashikei", "Otaku Culture", "Gourmet", "Family Life", "Parenthood",
        "Agriculture", "School Club", "CGDCT", "Childcare",
        // Sanat / Müzik
        "Band", "Dancing", "Idol", "Idols", "Showbiz", "Performing Arts",
        "Fashion", "Photography", "Modeling",
        // Spor / Oyun
        "Football", "Basketball", "Baseball", "Volleyball", "Tennis", "Swimming",
        "Boxing", "Judo", "Cycling", "Surfing", "Rugby", "Skateboarding",
        "Table Tennis", "Ice Skating", "Sumo", "Wrestling", "Fishing",
        "E-Sports", "Video Games", "Board Game", "Card Battle", "Mahjong",
        // Karakterler
        "Vampire", "Zombie", "Werewolf", "Witch", "Samurai", "Ninja", "Pirates",
        "Vikings", "Demon", "Tsundere", "Yandere", "Villainess", "Tomboy",
        "VTuber", "Twins", "Teacher", "Orphan",
        // Organizasyonlar / Kurumlar
        "Mafia", "Yakuza", "Police", "Assassins", "Gangs", "Military", "Firefighters",
        // Ayarlar
        "School", "Workplace", "Dungeon", "Prison", "Medieval", "Historical",
        "Dystopian", "Space", "Virtual World", "Urban Fantasy", "Afterlife",
        "Alternate Universe", "Ancient China", "Camping", "Restaurant", "College",
        // Diğer temalar
        "Reincarnation", "Death Game", "Gambling", "Politics", "Philosophy",
        "Religion", "War", "Crime", "Drugs", "Medicine", "Pandemic",
        "Environmental", "Economics", "Marriage", "Travel", "Educational",
        "Delinquents", "Gender Bending", "LGBTQ+ Themes", "Found Family",
        "Memory Manipulation", "Noir", "Prophecy", "Crossover", "Chibi"
    )

    /** AniList'in `genre_in` alanına gönderilebilecek resmî 19 tür. */
    private val officialApiGenres: Set<String> = setOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Hentai", "Horror",
        "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
        "Slice of Life", "Sports", "Supernatural", "Thriller"
    )

    private val knownTags: Set<String> by lazy {
        (anilistTags + SearchTranslation.englishToTurkish.keys).toSet()
    }

    private val knownTagsLower: Set<String> by lazy { knownTags.map { it.lowercase() }.toSet() }

    /** `genre_in` için geçerli mi? Demografiler AniList'te tür değil etikettir. */
    fun isOfficialApiGenre(value: String): Boolean = officialApiGenres.contains(value.trim())

    /** `tag_in` için geçerli mi? Bilinmeyen etiket API'ye gönderilmez (HTTP 400 koruması). */
    fun isKnownAniListTag(value: String): Boolean =
        value.trim().lowercase() in knownTagsLower

    /** Kanonik yazımı döndürür (TR/Çince etiket → İngilizce), bilinmiyorsa null. */
    fun canonical(value: String?): String? {
        val term = value?.let(SearchTranslation::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return knownTags.firstOrNull { it.equals(term, ignoreCase = true) }
            ?: anilistGenres.firstOrNull { it.equals(term, ignoreCase = true) }
            ?: term
    }
}
