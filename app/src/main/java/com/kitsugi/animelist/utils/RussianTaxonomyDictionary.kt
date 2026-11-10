package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Rusça (Shikimori) **tür ve etiket sözlüğü**.
 *
 * Neden gerekli:
 *  Shikimori bir Rus servisidir; `genres[]` ve `themes[]` alanları **Rusça** gelir
 *  (`Экшен`, `Фэнтези`, `Повседневность`, `Сёнен`, `Махо-сёдзё` ...). Uygulamanın geri
 *  kalanı İngilizce (AniList/MAL/TMDB/Simkl/Kitsu) ve Çince/Japonca (Bangumi) etiketlerle
 *  çalışır. Bu tablo Rusça değerleri **aynı kanonik anahtara** bağlar; böylece
 *  `Экшен` · `Action` · `アクション` · `动作` ekranda **tek çip** olur.
 *
 * Kurallar (Bangumi sözlüğüyle birebir aynı):
 *  • **Uydurma çeviri yok.** Bilinmeyen Rusça etiket olduğu gibi döner; gösterim katmanı
 *    ([com.kitsugi.animelist.utils.toLocalizedTaxonomyLabel]) Kiril alfabesini Latin harflere
 *    çevirir, böylece ekranda **asla Kiril görünmez**.
 *  • `key` alanı `taxonomy_tag_<key>` dil dosyası kaynağıyla eşleşir; kaynak varsa (tr/en
 *    arayüzünde) o tercih edilir, yoksa bu tablo kullanılır.
 *  • Eşleştirme normalize edilir: büyük/küçük harf, `ё`↔`е`, `э`↔`е`, boşluk ve tırnak
 *    farkları yok sayılır (`Сёдзё` = `Седзе` = `«Сёдзё»`).
 *  • `malGenreId` alanı MAL/Shikimori'nin **sayısal tür kimliğidir**. Rusça ad beklenmedik bir
 *    yazımla gelse bile kimlik üzerinden doğru karşılık bulunur.
 */
internal data class RussianTaxonomyEntry(
    /** `taxonomy_tag_*` dil dosyası anahtarı (snake_case). */
    val key: String,
    val turkish: String,
    val english: String,
    /** MAL / Shikimori sayısal tür kimliği (bilinmiyorsa null). */
    val malGenreId: Int? = null
)

internal object RussianTaxonomyDictionary {

    private class Group(val entry: RussianTaxonomyEntry, val labels: List<String>)

    private fun g(id: Int?, key: String, turkish: String, english: String, vararg labels: String) =
        Group(RussianTaxonomyEntry(key, turkish, english, id), labels.toList())

    // ── Tablo ─────────────────────────────────────────────────────────────────
    // Kaynak: Shikimori `/api/genres` resmî listesi (id ↔ Rusça ad) + MAL/AniList kanonik adı.
    // Aynı anlamın farklı yazımları TEK girişte toplanır.

    private val groups: List<Group> = listOf(
        // ── Ana türler ──
        g(1, "action", "Aksiyon", "Action", "Экшен", "Экшн", "Боевик", "Акшен"),
        g(2, "adventure", "Macera", "Adventure", "Приключения", "Приключение", "Приключенческое"),
        g(3, "racing", "Yarış", "Racing", "Гонки", "Гонка", "Автогонки"),
        g(4, "comedy", "Komedi", "Comedy", "Комедия", "Комедии", "Юмор"),
        g(5, "avant_garde", "Avangart", "Avant Garde", "Авангард", "Авангардный"),
        g(6, "mythology", "Mitoloji", "Mythology", "Мифология", "Мифы"),
        g(7, "mystery", "Gizem", "Mystery", "Тайна", "Мистика", "Загадка", "Детективная тайна"),
        g(8, "drama", "Dram", "Drama", "Драма", "Драмы"),
        g(9, "ecchi", "Ecchi", "Ecchi", "Этти", "Эчи", "Этчи"),
        g(10, "fantasy", "Fantastik", "Fantasy", "Фэнтези", "Фентези", "Фэнтэзи"),
        g(11, "strategy_game", "Strateji Oyunu", "Strategy Game", "Стратегические игры", "Стратегия", "Стратегическая игра"),
        g(12, "hentai", "Hentai", "Hentai", "Хентай", "Хентайный"),
        g(13, "historical", "Tarihi", "Historical", "Исторический", "История", "Историческое"),
        g(14, "horror", "Korku", "Horror", "Ужасы", "Хоррор", "Ужас"),
        g(15, "kids", "Çocuklara Yönelik", "Kids", "Детское", "Детский", "Для детей"),
        g(16, "magic", "Büyü", "Magic", "Магия", "Волшебство"),
        g(17, "martial_arts", "Dövüş Sanatları", "Martial Arts", "Боевые искусства", "Боевые исскуства", "Единоборства"),
        g(18, "mecha", "Mecha", "Mecha", "Меха", "Роботы", "Мехи"),
        g(19, "music", "Müzik", "Music", "Музыка", "Музыкальный"),
        g(20, "parody", "Parodi", "Parody", "Пародия", "Пародийный"),
        g(21, "samurai", "Samuray", "Samurai", "Самураи", "Самурай", "Самурайский"),
        g(22, "romance", "Romantik", "Romance", "Романтика", "Романтическое", "Романтичный"),
        g(23, "school", "Okul", "School", "Школа", "Школьный", "Школьная жизнь"),
        g(24, "sci_fi", "Bilim Kurgu", "Sci-Fi", "Фантастика", "Научная фантастика", "Фантастическое"),
        g(25, "shoujo", "Shōjo", "Shoujo", "Сёдзё", "Сёдзе", "Шёдзё", "Седзе"),
        g(26, "shoujo_ai", "Shoujo Ai", "Shoujo Ai", "Сёдзё-ай", "Сёдзе-ай", "Шёдзё-ай"),
        g(27, "shounen", "Shōnen", "Shounen", "Сёнен", "Сёнэн", "Шонен", "Сенен"),
        g(28, "shounen_ai", "Shounen Ai", "Shounen Ai", "Сёнен-ай", "Сёнэн-ай", "Шонен-ай"),
        g(29, "space", "Uzay", "Space", "Космос", "Космический", "Космоопера"),
        g(30, "sports", "Spor", "Sports", "Спорт", "Спортивный", "Спортивные"),
        g(31, "super_power", "Süper Güç", "Super Power", "Супер сила", "Суперсила", "Сверхспособности", "Сверхсила"),
        g(32, "vampire", "Vampir", "Vampire", "Вампиры", "Вампир"),
        g(33, "yaoi", "Yaoi", "Yaoi", "Яой"),
        g(34, "yuri", "Yuri", "Yuri", "Юри"),
        g(35, "harem", "Harem", "Harem", "Гарем", "Гаремный"),
        g(36, "slice_of_life", "Günlük Yaşam", "Slice of Life", "Повседневность", "Слайс оф лайф", "Повседневная жизнь"),
        g(37, "supernatural", "Doğaüstü", "Supernatural", "Сверхъестественное", "Мистическое", "Паранормальное"),
        g(38, "military", "Askeri", "Military", "Военное", "Военный", "Война", "Армия"),
        g(39, "detective", "Dedektif", "Detective", "Детектив", "Детективный"),
        g(40, "psychological", "Psikolojik", "Psychological", "Психологическое", "Психология", "Психологический"),
        g(42, "seinen", "Seinen", "Seinen", "Сэйнэн", "Сейнен", "Сэйнэн"),
        g(43, "josei", "Josei", "Josei", "Дзёсей", "Джосей", "Дзёсэй", "Джосэй"),
        g(44, "gender_bender", "Cinsiyet Değişimi", "Gender Bender", "Смена пола", "Гендерная интрига"),

        // ── Temalar / alt türler ──
        g(102, "team_sports", "Takım Sporu", "Team Sports", "Командный спорт"),
        g(103, "video_game", "Video Oyunu", "Video Game", "Видеоигры", "Видеоигра", "Игры", "Игра", "Game"),
        g(104, "adult_cast", "Yetişkin Kadro", "Adult Cast", "Взрослые персонажи", "Взрослый состав"),
        g(105, "gore", "Kan ve Şiddet", "Gore", "Жестокость", "Кровь", "Гор", "Насилие"),
        g(106, "reincarnation", "Reenkarnasyon", "Reincarnation", "Реинкарнация", "Перерождение", "Реинкарнация в другом мире"),
        g(107, "love_polygon", "Aşk Çokgeni", "Love Polygon", "Любовный многоугольник", "Любовный треугольник"),
        g(108, "visual_arts", "Görsel Sanatlar", "Visual Arts", "Изобразительное искусство", "Живопись"),
        g(111, "time_travel", "Zaman Yolculuğu", "Time Travel", "Путешествие во времени", "Тайм-тревел", "Перемещение во времени"),
        g(112, "gag_humor", "Şakacı Mizah", "Gag Humor", "Гэг-юмор", "Гэги"),
        g(114, "award_winning", "Ödüllü", "Award Winning", "Удостоено наград", "Отмечено наградами"),
        g(117, "thriller", "Gerilim", "Thriller", "Триллер", "Триллеры"),
        g(118, "combat_sports", "Dövüş Sporları", "Combat Sports", "Спортивные единоборства", "Боевой спорт"),
        g(119, "cgdct", "CGDCT", "CGDCT", "CGDCT"),
        g(124, "mahou_shoujo", "Sihirli Kız", "Mahou Shoujo", "Махо-сёдзё", "Махо-сёдзе", "Девочка-волшебница"),
        g(125, "reverse_harem", "Ters Harem", "Reverse Harem", "Реверс-гарем", "Обратный гарем"),
        g(130, "isekai", "Isekai", "Isekai", "Исэкай", "Исекай", "Другой мир"),
        g(131, "delinquents", "Asi Gençler", "Delinquents", "Хулиганы", "Правонарушители"),
        g(134, "childcare", "Çocuk Bakımı", "Childcare", "Забота о детях", "Воспитание детей"),
        g(135, "magical_sex_shift", "Büyülü Cinsiyet Değişimi", "Magical Sex Shift", "Магическая смена пола"),
        g(136, "showbiz", "Şov Dünyası", "Showbiz", "Шоу-бизнес", "Сценическая жизнь"),
        g(137, "otaku_culture", "Otaku Kültürü", "Otaku Culture", "Культура отаку", "Отаку"),
        g(138, "organized_crime", "Organize Suç", "Organized Crime", "Организованная преступность", "Мафия"),
        g(139, "workplace", "İş Yeri", "Workplace", "Работа", "Офис", "Рабочие будни"),
        g(140, "iyashikei", "Iyashikei", "Iyashikei", "Иясикэй", "Ияшикей", "Исцеляющее"),
        g(141, "survival", "Hayatta Kalma", "Survival", "Выживание", "На выживание"),
        g(142, "performing_arts", "Sahne Sanatları", "Performing Arts", "Исполнительское искусство", "Сценическое искусство"),
        g(143, "anthropomorphic", "Antropomorfik", "Anthropomorphic", "Антропоморфизм", "Очеловечивание"),
        g(144, "crossdressing", "Zıt Cins Kılığı", "Crossdressing", "Кроссдрессинг", "Переодевание"),
        g(145, "idols_female", "İdoller (Kadın)", "Idols (Female)", "Идолы (Жен.)", "Идолы женские", "Женские идолы"),
        g(146, "high_stakes_game", "Yüksek Riskli Oyun", "High Stakes Game", "Игра с высокими ставками", "Азартные игры"),
        g(147, "medical", "Tıp", "Medical", "Медицина", "Медицинский"),
        g(148, "pets", "Evcil Hayvanlar", "Pets", "Питомцы", "Домашние животные"),
        g(149, "educational", "Eğitici", "Educational", "Образовательное", "Обучающее"),
        g(150, "idols_male", "İdoller (Erkek)", "Idols (Male)", "Идолы (Муж.)", "Идолы мужские", "Мужские идолы"),
        g(151, "romantic_subtext", "Romantik Alt Metin", "Romantic Subtext", "Романтический подтекст"),
        g(543, "gourmet", "Gurme", "Gourmet", "Гурман", "Кулинария", "Еда"),

        // ── Shikimori'de ek olarak görülen / sık kullanılan etiketler ──
        g(null, "erotica", "Erotik", "Erotica", "Эротика", "Эротическое"),
        g(null, "demons", "İblisler", "Demons", "Демоны", "Демон"),
        g(null, "police", "Polis", "Police", "Полиция", "Полицейский"),
        g(null, "post_apocalypse", "Kıyamet Sonrası", "Post-Apocalyptic", "Постапокалипсис", "Апокалипсис"),
        g(null, "cyberpunk", "Cyberpunk", "Cyberpunk", "Киберпанк", "Сайберпанк"),
        g(null, "steampunk", "Steampunk", "Steampunk", "Стимпанк"),
        g(null, "idol", "Idol", "Idol", "Идолы", "Идол"),
        g(null, "ninja", "Ninja", "Ninja", "Ниндзя"),
        g(null, "pirate", "Korsan", "Pirate", "Пираты", "Пират"),
        g(null, "zombie", "Zombi", "Zombie", "Зомби"),
        g(null, "ghost", "Hayalet", "Ghost", "Призраки", "Призрак"),
        g(null, "dragon", "Ejderha", "Dragon", "Драконы", "Дракон"),
        g(null, "angel", "Melek", "Angel", "Ангелы", "Ангел"),
        g(null, "aliens", "Uzaylılar", "Aliens", "Пришельцы", "Инопланетяне"),
        g(null, "military_academy", "Askeri Akademi", "Military Academy", "Военная академия"),
        g(null, "fake_relationship", "Sahte İlişki", "Fake Relationship", "Фальшивые отношения"),
        g(null, "childhood_friend", "Çocukluk Arkadaşı", "Childhood Friend", "Друг детства"),
        g(null, "episodic", "Vaka Anlatısı", "Episodic", "Эпизодическое"),
        g(null, "coming_of_age", "Olgunlaşma", "Coming of Age", "Взросление", "Становление личности"),
        g(null, "tragedy", "Trajedi", "Tragedy", "Трагедия", "Трагическое"),
        g(null, "betrayal", "İhanet", "Betrayal", "Предательство"),
        g(null, "amnesia", "Amnezi", "Amnesia", "Амнезия", "Потеря памяти"),
        g(null, "suicide", "İntihar", "Suicide", "Самоубийство"),
        g(null, "bullying", "Zorbalık", "Bullying", "Травля", "Издевательства"),
        g(null, "nudity", "Çıplaklık", "Nudity", "Нагота", "Обнажённость"),
        g(null, "proxy_battle", "Vekil Dövüş", "Proxy Battle", "Битва призывателей"),
        g(null, "travel", "Gezi", "Travel", "Путешествие", "Путешествия"),
        g(null, "cooking", "Yemek Pişirme", "Cooking", "Готовка", "Приготовление еды", "Cookery"),
        g(null, "video_game_isekai", "Oyun Dünyası Isekai", "Video Game Isekai", "Исэкай в игре"),
        g(null, "hollywood", "Hollywood", "Hollywood", "Голливуд"),
        g(null, "short_film", "Kısa Film", "Short Film", "Короткометражка", "Короткометражный фильм"),
        g(null, "web", "Web", "Web", "Веб", "Онлайн", "ОНА", "ONA"),
        g(null, "ova", "OVA", "OVA", "ОВА", "OVA"),
        g(null, "movie", "Film", "Movie", "Фильм", "Полнометражный фильм"),
        g(null, "tv", "TV", "TV", "ТВ", "Сериал", "ТВ-сериал")
    )

    // ── İndeksler ─────────────────────────────────────────────────────────────

    private val labelIndex: Map<String, RussianTaxonomyEntry> by lazy {
        val index = HashMap<String, RussianTaxonomyEntry>(groups.size * 4)
        for (group in groups) {
            for (label in group.labels) {
                index.putIfAbsent(normalize(label), group.entry)
            }
        }
        index
    }

    private val byMalId: Map<Int, RussianTaxonomyEntry> by lazy {
        groups.mapNotNull { group -> group.entry.malGenreId?.let { it to group.entry } }.toMap()
    }

    private val byKey: Map<String, RussianTaxonomyEntry> by lazy {
        groups.associate { it.entry.key to it.entry }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Rusça/İngilizce etiketin sözlük girişi; bilinmiyorsa null. */
    fun entryFor(label: String?): RussianTaxonomyEntry? {
        val key = normalize(label)
        if (key.isEmpty()) return null
        labelIndex[key]?.let { return it }
        // Kiril `ё/е`, `э/е` ve bitişik yazım farklarını tolere et.
        val collapsed = key.replace('ё', 'е').replace('э', 'е')
        return labelIndex[collapsed]
    }

    /** MAL/Shikimori sayısal tür kimliğinin karşılığı; yoksa null. */
    fun entryForMalGenreId(id: Int?): RussianTaxonomyEntry? = id?.let { byMalId[it] }

    /** Anahtar (`taxonomy_tag_*`) üzerinden giriş. */
    fun entryForKey(key: String?): RussianTaxonomyEntry? =
        key?.trim()?.lowercase(Locale.ROOT)?.let { byKey[it] }

    /** Tüm girişler (dil dosyası üreteci ve testler için). */
    fun allEntries(): List<RussianTaxonomyEntry> = groups.map { it.entry }

    /** Etiket normalizasyonu: küçük harf, `ё→е`, noktalama/tırnak temizliği, boşluk sadeleştirme. */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder(raw.length)
        var lastSpace = true
        for (ch in raw.trim()) {
            when {
                ch.isWhitespace() || ch == '\u00A0' -> {
                    if (!lastSpace) {
                        sb.append(' ')
                        lastSpace = true
                    }
                }
                ch == '«' || ch == '»' || ch == '"' || ch == '\'' || ch == '`' ||
                    ch == '“' || ch == '”' || ch == '„' || ch == '‐' || ch == '–' -> Unit
                else -> {
                    val lower = ch.lowercaseChar()
                    sb.append(if (lower == 'ё') 'е' else lower)
                    lastSpace = false
                }
            }
        }
        return sb.toString().trim()
    }
}
