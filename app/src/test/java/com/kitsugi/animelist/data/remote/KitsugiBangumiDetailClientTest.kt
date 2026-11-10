package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * Bangumi detay hattının saf (ağ gerektirmeyen) yardımcıları: kimlik dönüşümü, galeri
 * tekilleştirme, başlık/yıl eşleştirme puanı, durum/sezon/tarih çözümü ve metin temizliği.
 */
class KitsugiBangumiDetailClientTest {

    @Before
    fun useEnglishLocale() {
        // Çeviri yardımcıları cihaz diline bakar; saf birim testleri İngilizce çıktıyı doğrular.
        Locale.setDefault(Locale.US)
    }

    private fun subject(
        date: String? = "2008-10-02",
        platform: String? = "TV",
        eps: Int = 24,
        name: String = "CLANNAD 〜AFTER STORY〜"
    ) = BangumiSubject(
        id = 876,
        type = 2,
        name = name,
        nameCn = "",
        summary = "",
        date = date,
        platform = platform,
        nsfw = false,
        locked = false,
        eps = eps,
        totalEpisodes = eps,
        volumes = 0,
        images = null,
        rating = null,
        collection = null,
        tags = emptyList(),
        metaTags = emptyList(),
        infobox = emptyMap()
    )

    // ── Kimlik ───────────────────────────────────────────────────────────────

    @Test
    fun rawIdOf_convertsStableAndRawIds() {
        assertEquals(876, KitsugiBangumiDetailClient.rawIdOf(500_000_876))
        assertEquals(876, KitsugiBangumiDetailClient.rawIdOf(876))
        assertNull(KitsugiBangumiDetailClient.rawIdOf(0))
        assertNull(KitsugiBangumiDetailClient.rawIdOf(-5))
        assertNull(KitsugiBangumiDetailClient.rawIdOf(null))
        // Bangumi pencerisinin dışındaki (örn. Kitsu 300M / AniList 100M değil, 700M) değerler reddedilir.
        assertNull(KitsugiBangumiDetailClient.rawIdOf(700_000_000))
    }

    @Test
    fun sanitizeMalId_rejectsStableIds() {
        assertEquals(4181, KitsugiBangumiDetailClient.sanitizeMalId(4181))
        assertNull(KitsugiBangumiDetailClient.sanitizeMalId(500_000_876))
        assertNull(KitsugiBangumiDetailClient.sanitizeMalId(100_000_042))
        assertNull(KitsugiBangumiDetailClient.sanitizeMalId(0))
        assertNull(KitsugiBangumiDetailClient.sanitizeMalId(null))
    }

    // ── Galeri ───────────────────────────────────────────────────────────────

    @Test
    fun galleryDedupKey_collapsesBangumiSizeVariants() {
        val large = "https://lain.bgm.tv/pic/cover/l/67/d1/876_dCfrd.jpg"
        val common = "https://lain.bgm.tv/r/400/pic/cover/l/67/d1/876_dCfrd.jpg"
        val medium = "https://lain.bgm.tv/r/800/pic/cover/l/67/d1/876_dCfrd.jpg"
        val grid = "https://lain.bgm.tv/r/100/pic/cover/l/67/d1/876_dCfrd.jpg"
        val key = KitsugiBangumiDetailClient.galleryDedupKey(large)
        assertEquals(key, KitsugiBangumiDetailClient.galleryDedupKey(common))
        assertEquals(key, KitsugiBangumiDetailClient.galleryDedupKey(medium))
        assertEquals(key, KitsugiBangumiDetailClient.galleryDedupKey(grid))
        // Farklı görsel → farklı anahtar
        assertNotEquals(key, KitsugiBangumiDetailClient.galleryDedupKey("https://lain.bgm.tv/pic/cover/l/aa/bb/1_x.jpg"))
        // Diğer kaynakların URL'leri olduğu gibi kalır
        val tmdb = "https://image.tmdb.org/t/p/original/abc.jpg"
        assertEquals(tmdb, KitsugiBangumiDetailClient.galleryDedupKey(tmdb))
    }

    // ── Başlık / yıl eşleştirme ──────────────────────────────────────────────

    @Test
    fun normalizeTitle_ignoresPunctuationWidthAndCase() {
        assertEquals(
            KitsugiBangumiDetailClient.normalizeTitle("CLANNAD 〜AFTER STORY〜"),
            KitsugiBangumiDetailClient.normalizeTitle("Clannad: After Story")
        )
        assertEquals(
            KitsugiBangumiDetailClient.normalizeTitle("ＣＬＡＮＮＡＤ ～AFTER STORY～"),
            KitsugiBangumiDetailClient.normalizeTitle("clannad after story")
        )
    }

    @Test
    fun scoreCandidate_acceptsExactTitleWithMatchingYear() {
        val titles = setOf(KitsugiBangumiDetailClient.normalizeTitle("CLANNAD 〜AFTER STORY〜"))
        val score = KitsugiBangumiDetailClient.scoreCandidate(
            subjectTitles = titles,
            subjectYear = 2008,
            subjectEpisodes = 24,
            candidateTitles = listOf("Clannad: After Story", null, "CLANNAD ～AFTER STORY～"),
            candidateYear = 2008,
            candidateEpisodes = 24,
            formatCompatible = true
        )
        assertTrue("puan eşikten düşük: $score", score >= 5)
    }

    @Test
    fun scoreCandidate_rejectsFarYearAndUnrelatedTitles() {
        val titles = setOf(KitsugiBangumiDetailClient.normalizeTitle("CLANNAD"))
        // Aynı başlık ama 5 yıl sonra çıkan başka yapım (yeniden yapım/yan ürün) → elenir
        assertEquals(
            0,
            KitsugiBangumiDetailClient.scoreCandidate(titles, 2007, 22, listOf("Clannad"), 2012, 22, true)
        )
        // Alakasız başlık → 0
        assertEquals(
            0,
            KitsugiBangumiDetailClient.scoreCandidate(titles, 2007, 22, listOf("Another Show"), 2007, 22, true)
        )
    }

    @Test
    fun formatCompatible_mapsBangumiPlatformsToAniListFormats() {
        assertEquals(true, KitsugiBangumiDetailClient.formatCompatible("TV", "TV"))
        assertEquals(true, KitsugiBangumiDetailClient.formatCompatible("剧场版", "MOVIE"))
        assertEquals(false, KitsugiBangumiDetailClient.formatCompatible("剧场版", "TV"))
        assertEquals(true, KitsugiBangumiDetailClient.formatCompatible("OVA", "OVA"))
        assertNull(KitsugiBangumiDetailClient.formatCompatible(null, "TV"))
        assertNull(KitsugiBangumiDetailClient.formatCompatible("TV", null))
    }

    // ── Tarih / durum / sezon ────────────────────────────────────────────────

    @Test
    fun isoDate_parsesBangumiDateFormats() {
        assertEquals("2008-10-02", KitsugiBangumiDetailClient.isoDate("2008年10月2日"))
        assertEquals("2009-03-26", KitsugiBangumiDetailClient.isoDate("2009-03-26"))
        assertEquals("2008-10", KitsugiBangumiDetailClient.isoDate("2008年10月"))
        assertEquals("2008", KitsugiBangumiDetailClient.isoDate("2008"))
        assertNull(KitsugiBangumiDetailClient.isoDate(""))
        assertNull(KitsugiBangumiDetailClient.isoDate(null))
        assertNull(KitsugiBangumiDetailClient.isoDate("bilinmiyor"))
    }

    @Test
    fun seasonOf_mapsMonthsToSeasons() {
        assertEquals("Winter 2020", KitsugiBangumiDetailClient.seasonOf("2020-01-10", MediaType.Anime))
        assertEquals("Spring 2020", KitsugiBangumiDetailClient.seasonOf("2020-04-01", MediaType.Anime))
        assertEquals("Summer 2020", KitsugiBangumiDetailClient.seasonOf("2020-07-05", MediaType.Anime))
        assertEquals("Fall 2008", KitsugiBangumiDetailClient.seasonOf("2008-10-02", MediaType.Anime))
        assertNull(KitsugiBangumiDetailClient.seasonOf("2008-10-02", MediaType.Manga))
        assertNull(KitsugiBangumiDetailClient.seasonOf("2008", MediaType.Anime))
    }

    @Test
    fun statusOf_usesEndDateAndAirDate() {
        val tv = subject()
        assertEquals("Finished Airing", KitsugiBangumiDetailClient.statusOf(tv, "2008-10-02", "2009-03-26", MediaType.Anime))

        val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
        assertEquals("Not yet aired", KitsugiBangumiDetailClient.statusOf(subject(date = "$nextYear-04-01"), "$nextYear-04-01", null, MediaType.Anime))
        // Tarih hiç yoksa (duyurulmuş ama tarihsiz)
        assertEquals("Not yet aired", KitsugiBangumiDetailClient.statusOf(subject(date = null), null, null, MediaType.Anime))

        // Film: yayın tarihi geçmişse tamamlandı
        val movie = subject(date = "2015-01-01", platform = "剧场版", eps = 1)
        assertEquals("Finished Airing", KitsugiBangumiDetailClient.statusOf(movie, "2015-01-01", null, MediaType.Anime))

        // Haftalık 12 bölüm, 3 hafta önce başladı, bitiş tarihi bilinmiyor → devam ediyor
        val threeWeeksAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -21) }
        val iso = "%04d-%02d-%02d".format(
            Locale.ROOT,
            threeWeeksAgo.get(Calendar.YEAR),
            threeWeeksAgo.get(Calendar.MONTH) + 1,
            threeWeeksAgo.get(Calendar.DAY_OF_MONTH)
        )
        assertEquals("Currently Airing", KitsugiBangumiDetailClient.statusOf(subject(date = iso, eps = 12), iso, null, MediaType.Anime))

        // Manga: bitiş tarihi yoksa durum belirsiz (null)
        assertNull(KitsugiBangumiDetailClient.statusOf(subject(date = "2010-05-01", platform = null), "2010-05-01", null, MediaType.Manga))
        assertEquals("Finished", KitsugiBangumiDetailClient.statusOf(subject(date = "2010-05-01", platform = null), "2010-05-01", "2012-01-01", MediaType.Manga))
    }

    // ── Metin yardımcıları ───────────────────────────────────────────────────

    @Test
    fun splitCompanies_stripsLabelsAndSeparators() {
        val result = KitsugiBangumiDetailClient.splitCompanies(
            listOf("光坂高校演劇部×TBS / 製作協力：ポニーキャニオン、ムービック、京都アニメーション")
        )
        assertTrue(result.contains("TBS"))
        assertTrue(result.contains("ポニーキャニオン"))
        assertTrue(result.contains("ムービック"))
        assertTrue(result.contains("京都アニメーション"))
        assertFalse(result.any { it.contains("：") })
        assertEquals(result.size, result.distinct().size)
    }

    @Test
    fun splitNetworks_splitsOnWhitespaceAndSlash() {
        val result = KitsugiBangumiDetailClient.splitNetworks(listOf("MBS RKB CBC BS-i/BS-TBS"))
        assertEquals(listOf("MBS", "RKB", "CBC", "BS-i", "BS-TBS"), result)
    }

    @Test
    fun cleanMarkup_removesBbcodeHtmlAndEmoticons() {
        assertEquals("Merhaba dünya", KitsugiBangumiDetailClient.cleanMarkup("[b]Merhaba[/b] (bgm38)<br/>dünya".replace("<br/>", " ")))
        assertEquals("a\n\nb", KitsugiBangumiDetailClient.cleanMarkup("a\r\n\r\n\r\n\r\nb"))
        assertEquals("link", KitsugiBangumiDetailClient.cleanMarkup("[url=https://bgm.tv]link[/url]"))
    }

    @Test
    fun relationLabel_translatesChineseRelationNames() {
        assertEquals("Sequel", KitsugiBangumiDetailClient.relationLabel("续集", "Sequel"))
        assertEquals("Prequel", KitsugiBangumiDetailClient.relationLabel("前传", null))
        assertEquals("Side story", KitsugiBangumiDetailClient.relationLabel("番外篇", "Side Story"))
        // Bilinmeyen Çince ad → İngilizce karşılığı, o da yoksa özgün metin
        assertEquals("Foo", KitsugiBangumiDetailClient.relationLabel("某关系", "Foo"))
        assertEquals("某关系", KitsugiBangumiDetailClient.relationLabel("某关系", null))
    }

    @Test
    fun weekdayAndDurationParsing() {
        assertEquals("Thursdays", KitsugiBangumiDetailClient.weekdayEnglish("星期四"))
        assertEquals("Sundays", KitsugiBangumiDetailClient.weekdayEnglish("星期日"))
        assertNull(KitsugiBangumiDetailClient.weekdayEnglish("不定期"))

        assertEquals("24 min per ep", KitsugiBangumiDetailClient.durationText("24分钟", false))
        assertEquals("95 min", KitsugiBangumiDetailClient.durationText("1:35:00", true))
        assertNull(KitsugiBangumiDetailClient.durationText("bilinmiyor", false))
    }

    @Test
    fun roleAndLanguageMappings() {
        assertEquals("Main", KitsugiBangumiDetailClient.characterRole(1))
        assertEquals("Supporting", KitsugiBangumiDetailClient.characterRole(2))
        assertEquals("Background", KitsugiBangumiDetailClient.characterRole(3))
        assertEquals("Japanese", KitsugiBangumiDetailClient.castLanguage(0))
        assertEquals("Chinese", KitsugiBangumiDetailClient.castLanguage(3))
        assertEquals("English", KitsugiBangumiDetailClient.castLanguage(5))
    }

    @Test
    fun formatOf_normalizesPlatformNames() {
        assertEquals("TV", KitsugiBangumiDetailClient.formatOf("TV"))
        assertEquals("Movie", KitsugiBangumiDetailClient.formatOf("剧场版"))
        assertEquals("ONA", KitsugiBangumiDetailClient.formatOf("WEB"))
        assertEquals("Manga", KitsugiBangumiDetailClient.formatOf("漫画"))
        assertNull(KitsugiBangumiDetailClient.formatOf(null))
    }

    // ── Gerçek yanıt biçimleriyle ayrıştırma ─────────────────────────────────

    /** `GET https://api.bgm.tv/v0/subjects/876` (CLANNAD ~AFTER STORY~) — kısaltılmış gerçek yanıt. */
    private val clannadAfterStoryJson = """
        {"date":"2008-10-02","platform":"TV",
         "images":{"small":"https://lain.bgm.tv/r/200/pic/cover/l/67/d1/876_dCfrd.jpg","grid":"https://lain.bgm.tv/r/100/pic/cover/l/67/d1/876_dCfrd.jpg","large":"https://lain.bgm.tv/pic/cover/l/67/d1/876_dCfrd.jpg","medium":"https://lain.bgm.tv/r/800/pic/cover/l/67/d1/876_dCfrd.jpg","common":"https://lain.bgm.tv/r/400/pic/cover/l/67/d1/876_dCfrd.jpg"},
         "summary":"在某个小镇，主角冈崎朋也因为家庭的因素成为不良少年。\r\n\r\n前半为主角和女主角们之间所发生的事件。",
         "name":"CLANNAD 〜AFTER STORY〜","name_cn":"",
         "tags":[{"name":"Clannad","count":9625,"total_count":21084},{"name":"京阿尼","count":9116,"total_count":216451},{"name":"催泪","count":7428,"total_count":61189}],
         "infobox":[{"key":"话数","value":"24"},{"key":"放送开始","value":"2008年10月2日"},{"key":"放送星期","value":"星期四"},
                    {"key":"官方网站","value":"http://www.tbs.co.jp/clannad"},{"key":"播放电视台","value":"TBS"},
                    {"key":"其他电视台","value":"MBS RKB CBC BS-i/BS-TBS"},{"key":"播放结束","value":"2009年3月26日"},
                    {"key":"导演","value":"石原立也"},{"key":"原作","value":"Key/VISUAL ARTS"},
                    {"key":"动画制作","value":"京都アニメーション"},
                    {"key":"製作","value":"光坂高校演劇部×TBS / 製作協力：ポニーキャニオン、ムービック、京都アニメーション"}],
         "rating":{"rank":1,"total":31864,"count":{"1":162,"2":55,"3":53,"4":87,"5":258,"6":626,"7":1540,"8":3907,"9":7976,"10":17200},"score":9.2},
         "total_episodes":24,"collection":{"on_hold":1360,"dropped":390,"wish":8980,"collect":47652,"doing":1953},
         "id":876,"eps":24,"meta_tags":["TV","恋爱","日本","游戏改","日常"],"volumes":0,"series":false,"locked":false,"nsfw":false,"type":2}
    """.trimIndent()

    @Test
    fun buildNativeDetail_fillsInfoTabFromBangumiSubject() {
        val subject = BangumiApiClient.parseSubject(JSONObject(clannadAfterStoryJson))
        val detail = KitsugiBangumiDetailClient.buildNativeDetail(subject, MediaType.Anime)

        assertEquals("CLANNAD 〜AFTER STORY〜", detail.title)
        assertEquals("CLANNAD 〜AFTER STORY〜", detail.titleNative)
        assertTrue(detail.synopsis!!.startsWith("在某个小镇"))
        assertFalse("özet CRLF içermemeli", detail.synopsis!!.contains('\r'))
        assertEquals("Finished Airing", detail.status)
        assertEquals("Fall 2008", detail.season)
        assertEquals("2008-10-02", detail.startDate)
        assertEquals("2009-03-26", detail.endDate)
        assertEquals(2008, detail.year)
        assertEquals(24, detail.total)
        assertEquals("Thursdays (TBS)", detail.broadcast)
        assertEquals("Key/VISUAL ARTS", detail.sourceMaterial)
        // Stüdyo/şirket adları Japonca infobox'tan Latin ada çevrilir
        assertEquals(listOf("KyoAni"), detail.studios.map { it.name })
        assertEquals(
            listOf("光坂高校演劇部", "TBS", "PONY CANYON", "Movic"),
            detail.producers.map { it.name }
        )
        // Ana + diğer kanallar birleşir
        assertEquals(
            listOf("TBS", "Mainichi Broadcasting", "RKB Mainichi Broadcasting", "CBC", "BS-TBS"),
            detail.networks.map { it.name }
        )
        assertEquals(9, detail.score)
        assertEquals(92, detail.meanScore)
        assertEquals(1, detail.rank)
        assertEquals(31864, detail.scoredBy)
        assertEquals(8980 + 47652 + 1953 + 1360 + 390, detail.members)
        assertEquals(listOf("TV", "恋爱", "日本", "游戏改", "日常"), detail.genres)
        assertEquals(listOf("Clannad", "京阿尼", "催泪"), detail.tags.map { it.name })
        assertEquals("TV", detail.format)
        assertFalse(detail.isAdult)
        // Ana görsel tek bir (büyük) Bangumi kapağıdır; galeri kopya üretmez
        assertEquals("https://lain.bgm.tv/pic/cover/l/67/d1/876_dCfrd.jpg", detail.imageUrl)
        assertEquals("Bangumi", detail.externalLinks.first().site)
        assertTrue(detail.externalLinks.any { it.url == "http://www.tbs.co.jp/clannad" })
    }

    @Test
    fun parseSubject_treatsJsonNullDateAsMissing() {
        val json = """{"id":5,"type":2,"name":"X","name_cn":"","summary":"","date":null,"platform":null,"nsfw":false,"eps":0,"images":null}"""
        val subject = BangumiApiClient.parseSubject(JSONObject(json))
        assertNull(subject.date)
        assertNull(subject.platform)
    }

    @Test
    fun buildStats_mapsCollectionAndScoreDistribution() {
        val subject = BangumiApiClient.parseSubject(JSONObject(clannadAfterStoryJson))
        val stats = KitsugiBangumiDetailClient.buildStats(subject)!!
        assertEquals(1953, stats.watching)
        assertEquals(47652, stats.completed)
        assertEquals(8980, stats.planned)
        assertEquals(390, stats.dropped)
        assertEquals(1360, stats.paused)
        assertEquals(10, stats.scoreDistribution.size)
        assertEquals(17200, stats.scoreDistribution.first { it.score == 10 }.amount)
        assertEquals(162, stats.scoreDistribution.first { it.score == 1 }.amount)
        assertEquals(1, stats.rankings.single().rank)
        assertTrue(stats.rankings.single().allTime)
    }

    @Test
    fun parseCharacters_mapsRolesVoiceActorsAndImages() {
        val root = JSONObject(
            """
            {"data":[
              {"character":{"id":3,"name":"古河渚","nameCN":"古河渚","role":1,"info":"","images":{"large":"https://lain.bgm.tv/pic/crt/l/aa/bb/3_x.jpg","medium":"https://lain.bgm.tv/r/400/pic/crt/m/aa/bb/3_x.jpg","small":"https://lain.bgm.tv/r/200/pic/crt/s/aa/bb/3_x.jpg","grid":"https://lain.bgm.tv/r/100/pic/crt/g/aa/bb/3_x.jpg"},"comment":1,"lock":false,"nsfw":false},
               "casts":[{"person":{"id":9,"name":"中原麻衣","nameCN":"","type":1,"info":"","career":["seiyu"],"images":{"large":"https://lain.bgm.tv/pic/crt/l/xx/yy/9_z.jpg","medium":"https://lain.bgm.tv/r/400/pic/crt/m/xx/yy/9_z.jpg"},"comment":1,"lock":false,"nsfw":false},"relation":0,"summary":""},
                        {"person":{"id":10,"name":"Test EN","nameCN":"","type":1,"images":null},"relation":5,"summary":""}],
               "type":1,"order":1},
              {"character":{"id":4,"name":"岡崎朋也","nameCN":"冈崎朋也","role":1,"images":null},"casts":[],"type":2,"order":2},
              {"character":{"id":5,"name":"ゲスト","nameCN":"","role":1},"casts":[],"type":3,"order":3},
              {"character":{"id":0,"name":"bad"},"casts":[],"type":1,"order":4}
            ],"total":4}
            """.trimIndent()
        )
        val chars = KitsugiBangumiDetailClient.parseCharacters(root)
        assertEquals(3, chars.size) // id=0 elenir
        val nagisa = chars[0]
        assertEquals("古河渚", nagisa.name)
        assertEquals("Main", nagisa.role)
        assertEquals("bangumi", nagisa.source)
        assertEquals("https://lain.bgm.tv/r/400/pic/crt/m/aa/bb/3_x.jpg", nagisa.imageUrl)
        assertEquals(listOf("中原麻衣", "Test EN"), nagisa.voiceActors.map { it.name })
        assertEquals(listOf("Japanese", "English"), nagisa.voiceActors.map { it.language })
        assertEquals("https://lain.bgm.tv/r/400/pic/crt/m/xx/yy/9_z.jpg", nagisa.voiceActors[0].imageUrl)
        assertNull(nagisa.voiceActors[1].imageUrl)
        // Özgün Japonca ad korunur; Çince yerelleştirme ana adı ezmez.
        assertEquals("岡崎朋也", chars[1].name)
        assertEquals("Supporting", chars[1].role)
        assertEquals("ゲスト", chars[2].name)
        assertEquals("Background", chars[2].role)
        assertTrue(chars[2].voiceActors.isEmpty())
    }

    @Test
    fun parseStaff_joinsPositionsAndSkipsInvalid() {
        val root = JSONObject(
            """
            {"data":[
              {"staff":{"id":1,"name":"石原立也","nameCN":"石原立也","type":1,"images":{"medium":"https://lain.bgm.tv/r/400/pic/crt/m/a/b/1_x.jpg"}},
               "positions":[{"type":{"id":1,"en":"Director","cn":"导演","jp":"監督"},"summary":"","appearEps":""},
                            {"type":{"id":2,"en":"Storyboard","cn":"分镜","jp":"絵コンテ"},"summary":"","appearEps":"1,22"}]},
              {"staff":{"id":2,"name":"麻枝准","nameCN":"","type":1},"positions":[{"type":{"id":3,"en":"","cn":"原作","jp":""}}]},
              {"staff":{"id":0,"name":"bad"},"positions":[]}
            ],"total":3}
            """.trimIndent()
        )
        val staff = KitsugiBangumiDetailClient.parseStaff(root)
        assertEquals(2, staff.size)
        assertEquals("Director, Storyboard", staff[0].role)
        assertEquals("bangumi", staff[0].source)
        assertEquals("https://lain.bgm.tv/r/400/pic/crt/m/a/b/1_x.jpg", staff[0].imageUrl)
        // İngilizce ad yoksa Çince pozisyon adı kullanılır
        assertEquals("原作", staff[1].role)
        assertEquals("麻枝准", staff[1].name)
    }

    @Test
    fun parseRelations_filtersUnsupportedTypesAndLabelsRelations() {
        val root = JSONObject(
            """
            {"data":[
              {"subject":{"id":51,"name":"CLANNAD","nameCN":"CLANNAD","type":2,"nsfw":false,"images":{"common":"https://lain.bgm.tv/r/400/pic/cover/l/aa/bb/51_x.jpg","large":"https://lain.bgm.tv/pic/cover/l/aa/bb/51_x.jpg"}},
               "relation":{"id":2,"en":"Prequel","cn":"前传","desc":""},"order":1},
              {"subject":{"id":777,"name":"CLANNAD (漫画)","nameCN":"","type":1,"images":null},"relation":{"id":1,"en":"Adaptation","cn":"改编","desc":""},"order":2},
              {"subject":{"id":9999,"name":"CLANNAD OST","nameCN":"","type":3},"relation":{"id":99,"en":"Other","cn":"其他","desc":""},"order":3},
              {"subject":{"id":8888,"name":"CLANNAD (Game)","nameCN":"","type":4},"relation":{"id":99,"en":"Other","cn":"其他","desc":""},"order":4}
            ],"total":4}
            """.trimIndent()
        )
        val relations = KitsugiBangumiDetailClient.parseRelations(root)
        assertEquals(2, relations.size) // müzik + oyun elenir
        assertEquals(500_000_051, relations[0].malId)
        assertEquals("Prequel", relations[0].relationType)
        assertEquals(MediaType.Anime, relations[0].mediaType)
        assertEquals("bangumi", relations[0].source)
        assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/aa/bb/51_x.jpg", relations[0].imageUrl)
        assertEquals(500_000_777, relations[1].malId)
        assertEquals(MediaType.Manga, relations[1].mediaType)
        assertEquals("Adaptation", relations[1].relationType)
        assertEquals("CLANNAD (漫画)", relations[1].title)
    }

    @Test
    fun parseRecommendations_usesRecommendationLabel() {
        val root = JSONObject(
            """{"data":[{"subject":{"id":1,"name":"Cowboy Bebop","nameCN":"星际牛仔","type":2,"images":null},"sim":0.4,"count":3}],"total":1}"""
        )
        val recs = KitsugiBangumiDetailClient.parseRecommendations(root)
        assertEquals(1, recs.size)
        assertEquals("Cowboy Bebop", recs[0].title)
        assertEquals("Cowboy Bebop", recs[0].titleJapanese)
        assertEquals("Cowboy Bebop", recs[0].titleRomaji)
        assertEquals("Recommendation", recs[0].relationType)
        assertEquals(500_000_001, recs[0].malId)
    }

    @Test
    fun parseEpisodes_keepsMainStoryUniqueNumbersOnly() {
        val root = JSONObject(
            """
            {"data":[
              {"id":100,"subjectID":876,"sort":1,"type":0,"name":"桜の下で","nameCN":"樱花树下","airdate":"2008-10-02"},
              {"id":101,"subjectID":876,"sort":2,"type":0,"name":"","nameCN":"","airdate":"2008-10-09"},
              {"id":102,"subjectID":876,"sort":2,"type":0,"name":"duplicate","nameCN":""},
              {"id":103,"subjectID":876,"sort":0.5,"type":0,"name":"half"},
              {"id":104,"subjectID":876,"sort":0,"type":0,"name":"zero"}
            ],"total":5}
            """.trimIndent()
        )
        val episodes = KitsugiBangumiDetailClient.parseEpisodes(root)
        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        // API'nin Çince yerelleştirmesi yerine özgün bölüm adı önceliklidir.
        assertEquals("#1 – 桜の下で", episodes[0].title)
        assertEquals("Episode 2", episodes[1].title)
        assertEquals("https://bgm.tv/ep/100", episodes[0].url)
        assertEquals("Bangumi", episodes[0].site)
    }

    @Test
    fun reviews_mapBlogEntriesAndComments() {
        val blog = JSONObject(
            """
            {"id":1,"user":{"id":7,"username":"u7","nickname":"Nick","avatar":{"small":"s","medium":"https://lain.bgm.tv/pic/user/m/a/b/7.jpg","large":"l"}},
             "entry":{"id":55,"title":"Harika","summary":"Kısa [b]özet[/b] (bgm38)","replies":4,"public":true,"createdAt":1700049600,"updatedAt":1700049600}}
            """.trimIndent()
        )
        val review = KitsugiBangumiDetailClient.reviewFromBlog(blog, "Uzun [i]metin[/i] burada.")!!
        assertEquals("Nick", review.username)
        assertEquals("Harika", review.summary)
        assertTrue(review.fullText.contains("Uzun metin burada."))
        assertEquals("https://lain.bgm.tv/pic/user/m/a/b/7.jpg", review.avatarUrl)
        assertEquals(4, review.helpfulCount)
        assertNull(review.id) // Bangumi incelemeleri için "faydalı" oyu butonu gösterilmez
        assertEquals("2023-11-15", review.dateText)

        val comment = JSONObject(
            """{"id":9,"user":{"id":8,"username":"u8","nickname":"","avatar":{"large":"https://lain.bgm.tv/pic/user/l/a/b/8.jpg"}},"type":2,"rate":10,"comment":"神作","updatedAt":1700000000}"""
        )
        val short = KitsugiBangumiDetailClient.reviewFromComment(comment)!!
        assertEquals("u8", short.username)
        assertEquals(10, short.score)
        assertEquals("神作", short.summary)

        // Boş yorum atlanır; geçersiz puan (0) gösterilmez
        assertNull(KitsugiBangumiDetailClient.reviewFromComment(JSONObject("""{"id":1,"comment":"  ","rate":0}""")))
        val noScore = KitsugiBangumiDetailClient.reviewFromComment(
            JSONObject("""{"id":1,"comment":"x","rate":0,"user":{"username":"a"}}""")
        )!!
        assertNull(noScore.score)
        assertEquals("a", noScore.username)
    }

    @Test
    fun parseP1Infobox_readsKeyValueArrays() {
        val info = KitsugiBangumiDetailClient.parseP1Infobox(
            org.json.JSONArray().put(JSONObject("""{"key":"性别","values":[{"v":"女"}]}""")).put(
                JSONObject("""{"key":"别名","values":[{"k":"简体中文名","v":"古河渚"},{"v":"Nagisa"}]}""")
            )
        )
        assertEquals(listOf("女"), info["性别"])
        assertEquals(listOf("古河渚", "Nagisa"), info["别名"])
    }

    @Test
    fun mergeDetail_keepsBangumiFieldsAndFillsGapsFromCompanion() {
        val subject = BangumiApiClient.parseSubject(JSONObject(clannadAfterStoryJson))
        val base = KitsugiBangumiDetailClient.buildNativeDetail(subject, MediaType.Anime)
        val companion = KitsugiMediaDetail(
            synopsis = null,
            genres = listOf("Drama", "Romance"),
            studios = listOf(KitsugiStudio(id = 44, name = "Kyoto Animation")),
            rating = "PG-13",
            titleEnglish = "Clannad: After Story",
            titleRomaji = "Clannad: After Story",
            trailerUrl = "https://youtube.com/watch?v=abc",
            imageUrl = "https://cdn.myanimelist.net/images/anime/1299/110774l.jpg",
            pictures = listOf("https://lain.bgm.tv/r/400/pic/cover/l/67/d1/876_dCfrd.jpg"),
            realMalId = 4181,
            tags = listOf(KitsugiTag(name = "Cyberpunk", rank = 90, isSpoiler = false))
        )
        val cross = KitsugiBangumiDetailClient.CrossIds(aniListId = 4181, malId = 4181, tmdbId = 4235, imdbId = "tt1234567")
        val merged = KitsugiBangumiDetailClient.mergeDetail(base, companion, cross, MediaType.Anime)

        // Bangumi alanları korunur
        assertEquals("CLANNAD 〜AFTER STORY〜", merged.title)
        assertEquals(9, merged.score)
        assertEquals(1, merged.rank)
        assertEquals("https://lain.bgm.tv/pic/cover/l/67/d1/876_dCfrd.jpg", merged.imageUrl)
        assertTrue(merged.synopsis!!.startsWith("在某个小镇"))
        // Boşluklar diğer kaynaktan tamamlanır (Bangumi türleriyle birleşir)
        assertEquals(listOf("TV", "恋爱", "日本", "游戏改", "日常", "Drama"), merged.genres)
        assertEquals("PG-13", merged.rating)
        assertEquals("Clannad: After Story", merged.titleEnglish)
        assertEquals("https://youtube.com/watch?v=abc", merged.trailerUrl)
        assertEquals(listOf("KyoAni"), merged.studios.map { it.name })
        // Çapraz kimlikler detaya yazılır (galeri / bölüm puanı / logo bunları kullanır)
        assertEquals(4181, merged.realMalId)
        assertEquals(4235, merged.tmdbId)
        val sites = merged.externalLinks.map { it.site }
        assertTrue(sites.containsAll(listOf("Bangumi", "MyAnimeList", "AniList", "IMDb")))
        // Galeri: Bangumi kapağının kopyası eklenmez, MAL kapağı eklenir
        assertEquals(listOf("https://cdn.myanimelist.net/images/anime/1299/110774l.jpg"), merged.pictures)
        // Etiketler: diğer kaynak + Bangumi etiketleri, tekrarsız
        assertTrue(merged.tags.any { it.name == "Cyberpunk" })
        assertTrue(merged.tags.any { it.name == "京阿尼" })
    }

    @Test
    fun mergeDetail_withoutCompanionStillPublishesCrossIds() {
        val subject = BangumiApiClient.parseSubject(JSONObject(clannadAfterStoryJson))
        val base = KitsugiBangumiDetailClient.buildNativeDetail(subject, MediaType.Anime)
        val cross = KitsugiBangumiDetailClient.CrossIds(malId = 4181, tmdbId = 4235)
        val merged = KitsugiBangumiDetailClient.mergeDetail(base, null, cross, MediaType.Anime)
        assertEquals(4181, merged.realMalId)
        assertEquals(4235, merged.tmdbId)
        assertEquals("CLANNAD 〜AFTER STORY〜", merged.title)
        assertTrue(merged.externalLinks.any { it.site == "MyAnimeList" })
    }

    @Test
    fun bangumiRealSubjectsMapToDistinctLiveActionTypesAndLabels() {
        val realTv = KitsugiBangumiClient.run {
            subject(platform = "电视剧").copy(type = BangumiApiClient.SubjectType.REAL)
                .toSearchResult(MediaType.TvShow)
        }
        val realMovie = KitsugiBangumiClient.run {
            subject(platform = "电影").copy(type = BangumiApiClient.SubjectType.REAL)
                .toSearchResult(MediaType.Movie)
        }

        assertEquals(MediaType.TvShow, realTv.type)
        assertEquals("DİZİ", realTv.subtitle)
        assertEquals(MediaType.Movie, realMovie.type)
        assertEquals("FİLM", realMovie.subtitle)
        assertEquals(realTv.malId, realMovie.malId)
    }

    @Test
    fun bangumiLocalizedName_keepsEnglishRomajiAndNativeIndependent() {
        val localized = BangumiNameLocalizer.subject(
            name = "進撃の巨人",
            nameCn = "进击的巨人",
            infobox = mapOf(
                "英文名" to listOf("Attack on Titan"),
                "罗马字" to listOf("Shingeki no Kyojin"),
                "别名" to listOf("AoT")
            )
        )

        assertEquals("Attack on Titan", localized.displayFor("ENGLISH"))
        assertEquals("Shingeki no Kyojin", localized.displayFor("ROMAJI"))
        assertEquals("進撃の巨人", localized.displayFor("NATIVE"))
        assertEquals("Shingeki no Kyojin", localized.display)
        assertEquals(
            "Attack on Titan",
            PreferenceHelpers.getDisplayTitle(
                localized.display, localized.english, localized.native, "ENGLISH", localized.romaji
            )
        )
        assertEquals(
            "Shingeki no Kyojin",
            PreferenceHelpers.getDisplayTitle(
                localized.display, localized.english, localized.native, "ROMAJI", localized.romaji
            )
        )
        assertEquals(
            "進撃の巨人",
            PreferenceHelpers.getDisplayTitle(
                localized.display, localized.english, localized.native, "NATIVE", localized.romaji
            )
        )
        assertTrue(localized.alternatives.contains("进击的巨人"))
    }

    @Test
    fun bangumiLocalizedName_usesSafeFallbackWhenRomajiIsUnavailable() {
        val localized = BangumiNameLocalizer.entity(
            name = "古河渚",
            nameCn = "古河渚",
            englishAliases = listOf("Nagisa Furukawa")
        )

        // Kaynak romaji alanı vermiyorsa açık English alanı güvenilir kalır; ROMAJI ekranı
        // bu güvenli English fallback'i kullanır ve sahte kanji okunuşu oluşturulmaz.
        assertEquals("Nagisa Furukawa", localized.english)
        assertNull(localized.romaji)
        assertTrue(
            BangumiSubjectTitleResolver.hasReliableEnglish(
                BangumiTitleCache.LatinTitles(localized.romaji, localized.english, localized.native)
            )
        )
        assertEquals("Nagisa Furukawa", localized.displayFor("ENGLISH"))
        assertEquals("Nagisa Furukawa", localized.displayFor("ROMAJI"))
        assertEquals("古河渚", localized.displayFor("NATIVE"))
    }

    @Test
    fun latinNativeNameIsDisplayedAsFallbackButNotStoredAsEnglish() {
        val localized = BangumiNameLocalizer.entity(name = "Shingeki no Kyojin")

        assertNull(localized.english)
        assertEquals("Shingeki no Kyojin", localized.displayFor("ENGLISH"))
        assertFalse(
            BangumiSubjectTitleResolver.hasReliableEnglish(
                BangumiTitleCache.LatinTitles(localized.romaji, localized.english, localized.native)
            )
        )
    }

    @Test
    fun romajiInfoboxIsNotMisreportedAsEnglish() {
        val localized = BangumiNameLocalizer.subject(
            name = "進撃の巨人",
            nameCn = "进击的巨人",
            infobox = mapOf("罗马字" to listOf("Shingeki no Kyojin"))
        )

        assertNull(localized.english)
        assertEquals("Shingeki no Kyojin", localized.romaji)
        // English tercihi, gerçek İngilizce bulunana kadar güvenli Latin fallback kullanır.
        assertEquals("Shingeki no Kyojin", localized.displayFor("ENGLISH"))
    }

    @Test
    fun bangumiEntityInfobox_usesEnglishAndRomajiAliasesForPeople() {
        val localized = BangumiNameLocalizer.entityFromInfobox(
            name = "古河渚",
            nameCn = "古河渚",
            infobox = mapOf(
                "英文名称" to listOf("Nagisa Furukawa"),
                "罗马拼写" to listOf("Nagisa Furukawa"),
                "别名" to listOf("Furukawa Nagisa")
            )
        )

        assertEquals("Nagisa Furukawa", localized.display)
        assertEquals("Nagisa Furukawa", localized.displayFor("ROMAJI"))
        assertEquals("Nagisa Furukawa", localized.displayFor("ENGLISH"))
        assertEquals("古河渚", localized.displayFor("NATIVE"))
        assertTrue(localized.alternatives.contains("Furukawa Nagisa"))
    }

    @Test
    fun pickSynopsis_prefersLatinOverCjkForBetterTranslation() {
        val cjk = "在某个小镇，主角经历了一段感人的故事..."
        val latin = "In a certain town, the protagonist experiences an emotional story..."
        assertEquals(latin, KitsugiBangumiDetailClient.pickSynopsis(cjk, latin))
        assertEquals(cjk, KitsugiBangumiDetailClient.pickSynopsis(cjk, null))
        assertEquals(latin, KitsugiBangumiDetailClient.pickSynopsis(null, latin))
        assertEquals(cjk, KitsugiBangumiDetailClient.pickSynopsis(cjk, "另一个中文描述"))
    }

    @Test
    fun buildNativeDetail_mapsVaultPublisherMagazineAndBroadcastStation() {
        val prevLocale = java.util.Locale.getDefault()
        try {
            val mangaSubject = subject(name = "テスト漫画", platform = "漫画", eps = 0).copy(
                type = BangumiApiClient.SubjectType.BOOK,
                infobox = mapOf(
                    "出版社" to listOf("新書館"),
                    "發行" to listOf("オーバーラップ文庫"),
                    "連載雑誌" to listOf("月刊ウィングス")
                )
            )
            val mangaDetail = KitsugiBangumiDetailClient.buildNativeDetail(mangaSubject, MediaType.Manga)
            assertTrue(mangaDetail.producers.any { it.name == "Shinshokan" && it.role == StudioRole.PUBLISHER })
            assertTrue(mangaDetail.producers.any { it.name == "Overlap Bunko" && it.role == StudioRole.PUBLISHER })
            assertEquals(listOf("Monthly Wings"), mangaDetail.serializations.map { it.name })
            assertEquals(listOf(StudioRole.MAGAZINE), mangaDetail.serializations.map { it.role })

            val animeSubject = subject().copy(
                infobox = mapOf(
                    "放送星期" to listOf("星期六"),
                    "播放电视台" to listOf("フジテレビ")
                )
            )
            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            assertEquals(
                "Saturdays (Fuji Television)",
                KitsugiBangumiDetailClient.buildNativeDetail(animeSubject, MediaType.Anime).broadcast
            )
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            assertEquals(
                "Cumartesi (Fuji Television)",
                KitsugiBangumiDetailClient.buildNativeDetail(animeSubject, MediaType.Anime).broadcast
            )
        } finally {
            java.util.Locale.setDefault(prevLocale)
        }
    }
}
