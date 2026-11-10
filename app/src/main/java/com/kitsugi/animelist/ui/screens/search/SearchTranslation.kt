package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.utils.toLocalizedBangumiTagOrNull
import com.kitsugi.animelist.utils.isTurkish

/**
 * Merkezi Türkçe ↔ İngilizce çeviri nesnesi.
 * AniList, MAL (Jikan) ve TMDB'nin tüm tür/etiketlerini kapsar.
 * UI'da Türkçe gösterir, API'lere İngilizce gönderir.
 */
object SearchTranslation {

    // ─────────────────────────────────────────────────────────────────────────
    // İngilizce → Türkçe
    // ─────────────────────────────────────────────────────────────────────────
    val englishToTurkish: Map<String, String> = mapOf(
        // ── AniList resmi türler (19) ────────────────────────────────
        "Action"            to "Aksiyon",
        "Adventure"         to "Macera",
        "Comedy"            to "Komedi",
        "Drama"             to "Dram",
        "Ecchi"             to "Ecchi",
        "Fantasy"           to "Fantastik",
        "Hentai"            to "Hentai",
        "Horror"            to "Korku",
        "Mahou Shoujo"      to "Sihirli Kız",
        "Mecha"             to "Mecha",
        "Music"             to "Müzik",
        "Mystery"           to "Gizem",
        "Psychological"     to "Psikolojik",
        "Romance"           to "Romantik",
        "Sci-Fi"            to "Bilim Kurgu",
        "Slice of Life"     to "Günlük Yaşam",
        "Sports"            to "Spor",
        "Supernatural"      to "Doğaüstü",
        "Thriller"          to "Gerilim",

        // ── Demografi ────────────────────────────────────────────────
        "Shounen"           to "Shōnen",
        "Shoujo"            to "Shōjo",
        "Seinen"            to "Seinen",
        "Josei"             to "Josei",
        "Kids"              to "Çocuklara Yönelik",

        // ── Ortak küresel etiketler (AniList / MAL / Kitsu / Shikimori) ──
        // BangumiTagDictionary ile birebir aynı karşılıklar (yedek eşleme).
        "Heterosexual"      to "Heteroseksüel",
        "Male Protagonist"  to "Erkek Baş Karakter",
        "Female Protagonist" to "Kadın Baş Karakter",
        "Female Harem"      to "Ters Harem",
        "Primarily Male Cast" to "Ağırlıklı Erkek Kadro",
        "Primarily Female Cast" to "Ağırlıklı Kadın Kadro",
        "Primarily Teen Cast" to "Ağırlıklı Genç Kadro",
        "Primarily Adult Cast" to "Ağırlıklı Yetişkin Kadro",
        "Primarily Child Cast" to "Ağırlıklı Çocuk Kadro",
        "Ensemble Cast"     to "Ansambl Kadro",
        "Clones"            to "Klonlar",
        "Clone"             to "Klon",
        "Aliens"            to "Uzaylılar",
        "Polyamorous"       to "Poliamori",
        "Nudity"            to "Çıplaklık",
        "Kuudere"           to "Kuudere",

        // ── Aksiyon etiketleri ───────────────────────────────────────
        "Archery"           to "Okçuluk",
        "Battle Royale"     to "Battle Royale",
        "Espionage"         to "Casusluk",
        "Fugitive"          to "Kaçak",
        "Guns"              to "Silahlar",
        "Martial Arts"      to "Dövüş Sanatları",
        "Spearplay"         to "Mızrak Dövüşü",
        "Swordplay"         to "Kılıç Dövüşü",

        // ── Sanat etiketleri ─────────────────────────────────────────
        "Acting"            to "Oyunculuk",
        "Ballet"            to "Bale",
        "Calligraphy"       to "Hat Sanatı",
        "Classic Literature" to "Klasik Edebiyat",
        "Drawing"           to "Çizim",
        "Fashion"           to "Moda",
        "Food"              to "Yemek",
        "Kabuki"            to "Kabuki",
        "Makeup"            to "Makyaj",
        "Modeling"          to "Modacılık",
        "Photography"       to "Fotoğrafçılık",
        "Writing"           to "Yazarlık",
        "Band"              to "Müzik Grubu",
        "Classical Music"   to "Klasik Müzik",
        "Dancing"           to "Dans",
        "Hip-hop Music"     to "Hip-hop Müzik",
        "Jazz Music"        to "Caz Müzik",
        "Metal Music"       to "Metal Müzik",
        "Musical Theater"   to "Müzikal Tiyatro",
        "Rock Music"        to "Rock Müzik",

        // ── Komedi etiketleri ────────────────────────────────────────
        "Parody"            to "Parodi",
        "Satire"            to "Hiciv",
        "Slapstick"         to "Kaba Komedi",
        "Surreal Comedy"    to "Absürt Komedi",
        "Gag Humor"         to "Saçma Komedi",

        // ── Dram etiketleri ─────────────────────────────────────────
        "Bullying"          to "Zorbalık",
        "Class Struggle"    to "Sınıf Çatışması",
        "Coming of Age"     to "Olgunlaşma",
        "Conspiracy"        to "Komplo",
        "Fake Relationship" to "Sahte İlişki",
        "Kingdom Management" to "Krallık Yönetimi",
        "Rehabilitation"    to "Rehabilitasyon",
        "Revenge"           to "İntikam",
        "Tragedy"           to "Trajedi",
        "Suicide"           to "İntihar",

        // ── Fantazi etiketleri ───────────────────────────────────────
        "Alchemy"           to "Simya",
        "Body Swapping"     to "Beden Değişimi",
        "Cultivation"       to "Kültivasyon",
        "Curses"            to "Lanetler",
        "Exorcism"          to "Şeytan Kovma",
        "Fairy Tale"        to "Peri Masalı",
        "Henshin"           to "Henshin",
        "Isekai"            to "Isekai",
        "Kaiju"             to "Kaiju",
        "Magic"             to "Büyü",
        "Mythology"         to "Mitoloji",
        "Necromancy"        to "Nekromansi",
        "Reverse Isekai"    to "Ters Isekai",
        "Shapeshifting"     to "Dönüşüm",
        "Steampunk"         to "Steampunk",
        "Super Power"       to "Süper Güç",
        "Superhero"         to "Süper Kahraman",
        "Wuxia"             to "Wuxia",
        "Youkai"            to "Youkai",

        // ── Oyun etiketleri ──────────────────────────────────────────
        "Board Game"        to "Masa Oyunu",
        "E-Sports"          to "E-Spor",
        "Video Games"       to "Video Oyunları",
        "Card Battle"       to "Kart Savaşı",
        "Mahjong"           to "Mahjong",
        "Shogi"             to "Shogi",

        // ── Spor etiketleri ──────────────────────────────────────────
        "American Football" to "Amerikan Futbolu",
        "Athletics"         to "Atletizm",
        "Badminton"         to "Badminton",
        "Baseball"          to "Beyzbol",
        "Basketball"        to "Basketbol",
        "Boxing"            to "Boks",
        "Cycling"           to "Bisiklet",
        "Fencing"           to "Eskrim",
        "Fishing"           to "Balıkçılık",
        "Fitness"           to "Fitness",
        "Football"          to "Futbol",
        "Golf"              to "Golf",
        "Ice Skating"       to "Buz Pateni",
        "Judo"              to "Judo",
        "Rugby"             to "Rugby",
        "Skateboarding"     to "Kaykay",
        "Sumo"              to "Sumo",
        "Surfing"           to "Sörf",
        "Swimming"          to "Yüzme",
        "Table Tennis"      to "Masa Tenisi",
        "Tennis"            to "Tenis",
        "Volleyball"        to "Voleybol",
        "Wrestling"         to "Güreş",

        // ── Diğer / Genel etiketler ──────────────────────────────────
        "Animals"           to "Hayvanlar",
        "Astronomy"         to "Astronomi",
        "Autobiographical"  to "Otobiyografik",
        "Biographical"      to "Biyografik",
        "Body Horror"       to "Vücut Korkusu",
        "Brainwashing"      to "Beyin Yıkama",
        "Chibi"             to "Chibi",
        "Cosmic Horror"     to "Kozmik Korku",
        "Creature Taming"   to "Yaratık Eğitimi",
        "Crime"             to "Suç",
        "Death Game"        to "Ölüm Oyunu",
        "Drugs"             to "Uyuşturucu",
        "Economics"         to "Ekonomi",
        "Educational"       to "Eğitici",
        "Environmental"     to "Çevre",
        "Filmmaking"        to "Film Yapımı",
        "Found Family"      to "Seçilmiş Aile",
        "Gambling"          to "Kumar",
        "Gender Bending"    to "Cinsiyet Değişimi",
        "Gore"              to "Kan ve Şiddet",
        "Harem"             to "Harem",
        "Reverse Harem"     to "Ters Harem",
        "Human Experimentation" to "İnsan Deneyi",
        "LGBTQ+ Themes"     to "LGBTQ+ Temalı",
        "Marriage"          to "Evlilik",
        "Medicine"          to "Tıp",
        "Memory Manipulation" to "Bellek Manipülasyonu",
        "Military"          to "Askeri",
        "Noir"              to "Noir",
        "Otaku Culture"     to "Otaku Kültürü",
        "Pandemic"          to "Pandemi",
        "Philosophy"        to "Felsefe",
        "Politics"          to "Siyaset",
        "Prophecy"          to "Kehanet",
        "Reincarnation"     to "Reenkarnasyon",
        "Religion"          to "Din",
        "Rescue"            to "Kurtarma",
        "Royal Affairs"     to "Kraliyet Meseleleri",
        "Samurai"           to "Samuray",
        "Slavery"           to "Kölelik",
        "Space"             to "Uzay",
        "Space Opera"       to "Uzay Operası",
        "Survival"          to "Hayatta Kalma",
        "Terrorism"         to "Terör",
        "Time Loop"         to "Zaman Döngüsü",
        "Time Manipulation" to "Zaman Manipülasyonu",
        "Time Travel"       to "Zaman Yolculuğu",
        "Torture"           to "İşkence",
        "Travel"            to "Gezi",
        "War"               to "Savaş",
        "Post-Apocalyptic"  to "Kıyamet Sonrası",
        "Cyberpunk"         to "Cyberpunk",
        "Iyashikei"         to "Iyashikei",
        "Love Triangle"     to "Aşk Üçgeni",
        "Love Polygon"      to "Aşk Çokgeni",
        "Unrequited Love"   to "Karşılıksız Aşk",
        "Yuri"              to "Yuri",
        "Boys' Love"        to "Boys Love",
        "Historical"        to "Tarihi",
        "School"            to "Okul",
        "School Club"       to "Okul Kulübü",
        "Workplace"         to "İş Yeri",
        "Work"              to "İş",
        "Office"            to "Ofis",
        "Delinquents"       to "Asi Gençler",
        "Vampire"           to "Vampir",
        "Zombie"            to "Zombi",
        "Werewolf"          to "Kurt Adam",
        "Witch"             to "Cadı",
        "Skeleton"          to "İskelet",
        "Succubus"          to "Sukkubus",
        "Demon"             to "İblis",
        "Angel"             to "Melek",
        "Ghost"             to "Hayalet",
        "Orphan"            to "Yetim",
        "Twins"             to "İkizler",
        "Tomboy"            to "Tomboy",
        "Tsundere"          to "Tsundere",
        "Yandere"           to "Yandere",
        "Villainess"        to "Kötü Kadın",
        "Transgender"       to "Transgender",
        "VTuber"            to "VTuber",
        "Teacher"           to "Öğretmen",
        "Doctor"            to "Doktor",
        "Vikings"           to "Vikingler",
        "Pirates"           to "Korsanlar",
        "Ninja"             to "Ninja",
        "Kuudere"           to "Kuudere",
        "Childhood Friend"  to "Çocukluk Arkadaşı",
        "Assassins"         to "Suikastçılar",
        "Mafia"             to "Mafya",
        "Yakuza"            to "Yakuza",
        "Police"            to "Polis",
        "Gangs"             to "Çeteler",
        "Criminal Organization" to "Suç Örgütü",
        "Firefighters"      to "İtfaiyeciler",
        "Dystopian"         to "Distopik",
        "Medieval"          to "Orta Çağ",
        "Ancient China"     to "Antik Çin",
        "Afterlife"         to "Öte Dünya",
        "Alternate Universe" to "Alternatif Evren",
        "Virtual World"     to "Sanal Dünya",
        "Urban Fantasy"     to "Şehir Fantezisi",
        "Camping"           to "Kamp",
        "Dungeon"           to "Zindan",
        "Prison"            to "Hapishane",
        "Restaurant"        to "Restoran",
        "Rural"             to "Kırsal",
        "Desert"            to "Çöl",
        "Forest"            to "Orman",
        "Island"            to "Ada",
        "Coastal"           to "Kıyı",
        "College"           to "Üniversite",
        "Boarding School"   to "Yatılı Okul",
        "Inn"               to "Han / Otel",
        "Bar"               to "Bar",
        "Circus"            to "Sirk",
        "Gourmet"           to "Gurme",
        "Performing Arts"   to "Sahne Sanatları",
        "Showbiz"           to "Şov Dünyası",
        "Idols"             to "İdoller",
        "Otaku"             to "Otaku",
        "Detective"         to "Dedektif",
        "Crossover"         to "Crossover",
        "CGDCT"             to "CGDCT",
        "Childcare"         to "Çocuk Bakımı",
        "Farming"           to "Çiftçilik",
        "Agriculture"       to "Tarım",
        "Family Life"       to "Aile Yaşamı",
        "Parenthood"        to "Ebeveynlik",

        // ── TMDB'ye özgü ────────────────────────────────────────────
        "Animation"         to "Animasyon",
        "Science Fiction"   to "Bilim Kurgu",
        "Family"            to "Aile",
        "Documentary"       to "Belgesel",
        "Western"           to "Western",
        "TV Movie"          to "TV Filmi",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Türkçe → İngilizce (otomatik ters harita + alias'lar)
    // ─────────────────────────────────────────────────────────────────────────
    private val turkishToEnglish: Map<String, String> =
        englishToTurkish.entries.associate { it.value.lowercase() to it.key }

    private val aliases: Map<String, String> = mapOf(
        "bilim kurgu" to "Sci-Fi",
        "sci-fi"      to "Sci-Fi",
        "sihirli kız" to "Mahou Shoujo",
        "mahou shoujo" to "Mahou Shoujo",
        "yaşamdan kesitler" to "Slice of Life",
        "slice of life" to "Slice of Life",
        "süper güç" to "Super Power",
        "dövüş sanatları" to "Martial Arts",
        "zaman yolculuğu" to "Time Travel",
        "hayatta kalma" to "Survival",
        "ters harem" to "Reverse Harem",
        "kan ve şiddet" to "Gore",
        "kan" to "Gore",
        "aşk üçgeni" to "Love Triangle",
        "aşk çokgeni" to "Love Polygon",
        "video oyunu" to "Video Games",
        "video game" to "Video Games",
        "otaku kültürü" to "Otaku Culture",
        "post-apokaliptik" to "Post-Apocalyptic",
        "siberpunk" to "Cyberpunk",
        "büyü" to "Magic",
        "sihir" to "Magic",
        "korku" to "Horror",
        "gizem" to "Mystery",
        "romantizm" to "Romance",
        "spor" to "Sports",
        "doğaüstü" to "Supernatural",
        "gerilim" to "Thriller",
        "suspense" to "Thriller",
        "psikoloji" to "Psychological",
        "psikolojik" to "Psychological",
        "müzik" to "Music",
        "okul" to "School",
        "tarihi" to "Historical",
        "askeri" to "Military",
        "uzay" to "Space",
        "vampir" to "Vampire",
        "reenkarnasyon" to "Reincarnation",
        "mitoloji" to "Mythology",
        "parodi" to "Parody",
        "samuray" to "Samurai",
        "çocuk" to "Kids",
        "dedektif" to "Detective",
        "iş yeri" to "Workplace",
        "idoller" to "Idols",
        "gurme" to "Gourmet",
        "asi gençler" to "Delinquents",
        "eğitici" to "Educational",
        "tıp" to "Medicine",
        "şovbiz" to "Showbiz",
        "zombi" to "Zombie",
        "şeytan" to "Demon",
        "melek" to "Angel",
        "hayalet" to "Ghost",
        "yetim" to "Orphan",
        "ikizler" to "Twins",
        "cadı" to "Witch",
        "korsanlar" to "Pirates",
        "ninja" to "Ninja",
        "vikingler" to "Vikings",
        "polis" to "Police",
        "mafya" to "Mafia",
        "yakuza" to "Yakuza",
        "çeteler" to "Gangs",
        "distopik" to "Dystopian",
        "ortaçağ" to "Medieval",
        "zindan" to "Dungeon",
        "hapishane" to "Prison",
        "kamp" to "Camping",
        "kumar" to "Gambling",
        "savaş" to "War",
        "terör" to "Terrorism",
        "din" to "Religion",
        "felsefe" to "Philosophy",
        "siyaset" to "Politics",
        "evlilik" to "Marriage",
        "animasyon" to "Animation",
        "aile" to "Family",
        "belgesel" to "Documentary",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    private val englishToTurkishByLower: Map<String, Pair<String, String>> by lazy {
        val map = HashMap<String, Pair<String, String>>(englishToTurkish.size * 2)
        for ((en, tr) in englishToTurkish) {
            map.putIfAbsent(en.lowercase(java.util.Locale.ROOT), en to tr)
        }
        map
    }

    fun String.normalizeToResourceKey(): String {
        if (this.isEmpty()) return ""
        // ROOT yerel + karakter döngüsü: her etiket için 2× Regex derlemesini sıfırlar.
        val sb = StringBuilder(this.length)
        var prevUnderscore = true
        for (ch in this) {
            val lower = if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch
            val isAsciiWord = (lower in 'a'..'z') || (lower in '0'..'9')
            if (isAsciiWord) {
                sb.append(lower)
                prevUnderscore = false
            } else if (!prevUnderscore) {
                sb.append('_')
                prevUnderscore = true
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == '_') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    /**
     * `genre_*` / `tag_*` / `staff_role_*` dil dosyası karşılığı.
     */
    private fun getLocalizedStringFromResource(prefix: String, label: String): String? {
        val normalized = label.normalizeToResourceKey()
        if (normalized.isEmpty()) return null
        return com.kitsugi.animelist.utils.cachedStringResourceByName("${prefix}_$normalized")
    }

    private fun getLocalizedFromAnyPrefix(normalizedKey: String): String? {
        if (normalizedKey.isEmpty()) return null
        com.kitsugi.animelist.utils.cachedStringResourceByName("genre_$normalizedKey")?.let { return it }
        com.kitsugi.animelist.utils.cachedStringResourceByName("tag_$normalizedKey")?.let { return it }
        com.kitsugi.animelist.utils.cachedStringResourceByName("staff_role_$normalizedKey")?.let { return it }
        return null
    }

    fun translateToTurkishForDisplay(label: String): String {
        val cleaned = label.trim()
        if (cleaned.isEmpty()) return cleaned
        val normKey = cleaned.normalizeToResourceKey()
        getLocalizedFromAnyPrefix(normKey)?.let { return it }
        // Bangumi Çince/Japonca etiketleri (dil dosyası + statik harita)
        cleaned.toLocalizedBangumiTagOrNull()?.let { return it }
        // Shikimori (Rusça) tür/etiketleri — `taxonomy_tag_*` override'ı varsa o kullanılır.
        com.kitsugi.animelist.utils.RussianTaxonomyDictionary.entryFor(cleaned)?.let { entry ->
            com.kitsugi.animelist.utils.taxonomyStringOverride(entry.key)?.let { return it }
            return entry.turkish
        }

        englishToTurkish[cleaned]?.let { return it }
        val lower = cleaned.lowercase(java.util.Locale.ROOT)
        englishToTurkishByLower[lower]?.second?.let { return it }
        return cleaned
    }

    fun translateToEnglishForSearch(label: String): String {
        val cleaned = label.trim()
        if (englishToTurkish.containsKey(cleaned)) return cleaned
        // Bangumi Çince/Japonca etiket → İngilizce arama terimi
        com.kitsugi.animelist.utils.bangumiTagEnglishOrNull(cleaned)?.let { return it }
        // Shikimori (Rusça) etiket → İngilizce arama terimi
        com.kitsugi.animelist.utils.RussianTaxonomyDictionary.entryFor(cleaned)?.let { return it.english }
        val lower = cleaned.lowercase(java.util.Locale.ROOT)
        aliases[lower]?.let { return it }
        turkishToEnglish[lower]?.let { return it }
        englishToTurkishByLower[lower]?.first?.let { return it }
        return cleaned
    }

    fun displayLabel(label: String): String {
        val cleaned = label.trim()
        if (cleaned.isEmpty()) return cleaned
        val normKey = cleaned.normalizeToResourceKey()
        getLocalizedFromAnyPrefix(normKey)?.let { return it }
        // Bangumi Çince/Japonca etiketleri (dil dosyası + statik harita)
        cleaned.toLocalizedBangumiTagOrNull()?.let { return it }
        // Shikimori (Rusça) tür/etiketleri
        com.kitsugi.animelist.utils.RussianTaxonomyDictionary.entryFor(cleaned)?.let { entry ->
            com.kitsugi.animelist.utils.taxonomyStringOverride(entry.key)?.let { return it }
            return if (isTurkish()) entry.turkish else entry.english
        }
        if (!isTurkish()) return translateToEnglishForSearch(cleaned)

        englishToTurkish[cleaned]?.let { return it }
        val eng = translateToEnglishForSearch(cleaned)
        if (eng != cleaned) {
            getLocalizedFromAnyPrefix(eng.normalizeToResourceKey())?.let { return it }
        }

        englishToTurkish[eng]?.let { return it }
        val lower = eng.lowercase(java.util.Locale.ROOT)
        englishToTurkishByLower[lower]?.second?.let { return it }
        return cleaned
    }
}
