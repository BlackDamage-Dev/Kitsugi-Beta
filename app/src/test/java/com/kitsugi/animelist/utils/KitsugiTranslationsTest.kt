package com.kitsugi.animelist.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Bangumi (Çince/Japonca) etiket, kadro rolü ve stüdyo adı çevirileri için birim testleri.
 * Testler Türkçe cihaz dili taklidi yapar; bitişte özgün dil geri yüklenir.
 */
class KitsugiTranslationsTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setTurkishLocale() {
        Locale.setDefault(Locale("tr", "TR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ── Bangumi tür / meta etiketleri ────────────────────────────────────────

    @Test
    fun bangumiGenreTags_translateToTurkish() {
        assertEquals("Romantik", "恋爱".toTurkishGenre())
        assertEquals("Romantik", "恋愛".toTurkishGenre())
        assertEquals("Günlük Yaşam", "日常".toTurkishGenre())
        assertEquals("Oyun Uyarlaması", "游戏改".toTurkishGenre())
        assertEquals("Japonya", "日本".toTurkishGenre())
        assertEquals("TV", "TV".toTurkishGenre())
        assertEquals("Bilim Kurgu", "科幻".toTurkishGenre())
    }

    @Test
    fun bangumiGenreTags_translateBackToEnglishForSearch() {
        assertEquals("Romance", "恋爱".toEnglishGenreForSearch())
        assertEquals("Slice of Life", "日常".toEnglishGenreForSearch())
        assertEquals("Game Adaptation", "游戏改".toEnglishGenreForSearch())
        assertEquals("Mystery", "悬疑".toEnglishGenreForSearch())
    }

    @Test
    fun unknownBangumiTag_staysOriginal() {
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toTurkishGenre())
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toTurkishBangumiTag())
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toEnglishGenreForSearch())
        assertNull(bangumiTagEnglishOrNull("BilinmeyenEtiket"))
    }

    // ── Bangumi kullanıcı etiketleri ─────────────────────────────────────────

    @Test
    fun bangumiTags_translateForDisplay() {
        assertEquals("Kyoto Animation", "京阿尼".toTurkishBangumiTag())
        assertEquals("Kyoto Animation", "京都アニメーション".toTurkishBangumiTag())
        assertEquals("Başyapıt", "神作".toTurkishBangumiTag())
        assertEquals("Jun Maeda", "麻枝准".toTurkishBangumiTag())
        assertEquals("Duygusal", "催涙".toTurkishBangumiTag())
        assertEquals("İyileştirici", "治愈".toTurkishBangumiTag())
        assertEquals("Yaşam", "人生".toTurkishBangumiTag())
        assertEquals("Key", "key".toTurkishBangumiTag())
        // Eşleşen Latin etiket sözlükteki biçime normalize edilir
        assertEquals("CLANNAD", "Clannad".toTurkishBangumiTag())
        // Sözlükte olmayan Latin etiketler olduğu gibi kalır
        assertEquals("~Unknown-Tag~", "~Unknown-Tag~".toTurkishBangumiTag())
    }

    @Test
    fun bangumiTags_translateToEnglish() {
        assertEquals("Masterpiece", bangumiTagEnglishOrNull("神作"))
        assertEquals("Tearjerker", bangumiTagEnglishOrNull("催涙"))
        assertEquals("Kyoto Animation", bangumiTagEnglishOrNull("京阿尼"))
        assertEquals("Jun Maeda", bangumiTagEnglishOrNull("麻枝准"))
    }

    @Test
    fun bangumiTags_translateAdaptationShorthands() {
        assertEquals("Hafif Roman Uyarlaması", "轻小说改".toTurkishBangumiTag())
        assertEquals("Hafif Roman Uyarlaması", "轻改".toTurkishBangumiTag())
        assertEquals("Manga Uyarlaması", "漫改".toTurkishBangumiTag())
        assertEquals("Film", "剧场".toTurkishBangumiTag())
        assertEquals("Derin Anlatım", "深度".toTurkishBangumiTag())
        assertEquals("Çağının Ötesinde", "超前".toTurkishBangumiTag())
        assertEquals("Sanat", "艺术".toTurkishBangumiTag())
    }

    @Test
    fun bangumiTags_romanizePeopleAndWorks() {
        // Kişiler: basit/geleneksel Çince ve Japonca yazımlar tek Latin ada iner
        assertEquals("Tatsuya Ishihara", "石原立也".toTurkishBangumiTag())
        assertEquals("Yasuhiro Takemoto", "武本康弘".toTurkishBangumiTag())
        assertEquals("Nagaru Tanigawa", "谷川流".toTurkishBangumiTag())
        assertEquals("Yoko Kanno", "菅野洋子".toTurkishBangumiTag())
        assertEquals("Yoko Kanno", "菅野よう子".toTurkishBangumiTag())
        assertEquals("Kenji Kamiyama", "神山健治".toTurkishBangumiTag())
        assertEquals("Masamune Shirow", "士郎正宗".toTurkishBangumiTag())
        // Karakterler
        assertEquals("Yuki Nagato", "长门有希".toTurkishBangumiTag())
        assertEquals("Motoko Kusanagi", "草薙素子".toTurkishBangumiTag())
        assertEquals("Kyon", "囧虚".toTurkishBangumiTag())
        // Eserler
        assertEquals("Ghost in the Shell", "攻壳机动队".toTurkishBangumiTag())
        assertEquals("Ghost in the Shell", "攻殻機動隊".toTurkishBangumiTag())
        assertEquals("Haruhi Suzumiya", "凉宫春日".toTurkishBangumiTag())
    }

    @Test
    fun bangumiTags_matchLatinSpellingVariants() {
        // Nokta/boşluk farkları aynı çipe iner (Production I.G / Production.IG / ProductionI.G)
        assertEquals("Production I.G", "Production.IG".toLocalizedTagLabel())
        assertEquals("Production I.G", "ProductionI.G".toLocalizedTagLabel())
        assertEquals("Production I.G", "Production I.G".toLocalizedTagLabel())
        assertEquals("THE KLOCKWORX", "THE KLOCKWORX".toLocalizedTagLabel())
    }

    @Test
    fun bangumiTags_deduplicateAfterTranslation() {
        // Ghost in the Shell S.A.C. detayındaki gerçek etiket listesinden kesit
        val tags = listOf(
            "攻殻機動隊", "攻壳机动队", "Production.IG", "Production I.G", "ProductionI.G",
            "菅野洋子", "菅野よう子", "漫改", "漫画改", "科幻"
        )
        val labels = tags.localizedDistinctTags().map { it.toLocalizedTagLabel() }
        assertEquals(
            listOf("Ghost in the Shell", "Production I.G", "Yoko Kanno", "Manga Uyarlaması", "Bilim Kurgu"),
            labels
        )
    }

    @Test
    fun bangumiTags_pushUnknownCjkTagsToEnd() {
        // Sözlükte karşılığı olmayan CJK etiketler TR/EN arayüzde gösterilmeden elenir (v2.4.342)
        val tags = listOf("未知标签甲", "科幻", "未知标签乙", "日常", "Cyberpunk")
        assertEquals(
            listOf("科幻", "日常", "Cyberpunk"),
            tags.localizedDistinctTags()
        )
        assertEquals(true, isUntranslatedCjkTag("未知标签甲"))
        assertEquals(false, isUntranslatedCjkTag("科幻"))
        assertEquals(false, isUntranslatedCjkTag("Cyberpunk"))
    }

    @Test
    fun bangumiTags_useEnglishLabelsOnEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Light Novel Adaptation", "轻小说改".toTurkishBangumiTag())
        assertEquals("Profound", "深度".toTurkishBangumiTag())
        assertEquals("Ghost in the Shell", "攻壳机动队".toTurkishBangumiTag())
        assertEquals("Tatsuya Ishihara", "石原立也".toTurkishBangumiTag())
    }

    // ── Bangumi kadro / pozisyon rolleri ─────────────────────────────────────

    @Test
    fun staffRoles_translateBangumiEnglishPositionLabels() {
        assertEquals("Bölüm Yönetmeni, Storyboard", "Episode Direction, Storyboard".toTurkishStaffRole())
        assertEquals("Senarist", "Script/Screenplay".toTurkishStaffRole())
        assertEquals(
            "Animasyon Yönetmeni, Baş Animasyon Yönetmeni, Karakter Tasarımı",
            "Animation Direction, Chief Animation Director, Character Design".toTurkishStaffRole()
        )
        assertEquals("Seri Düzenlemesi, Senarist", "Series Composition, Script".toTurkishStaffRole())
        assertEquals("Yönetmen, Senarist", "Director, Script".toTurkishStaffRole())
    }

    @Test
    fun staffRoles_translateJapaneseAndChinesePositionLabels() {
        assertEquals("Yönetmen", "監督".toTurkishStaffRole())
        assertEquals("Senarist", "脚本".toTurkishStaffRole())
        assertEquals("Seri Düzenlemesi", "シリーズ構成".toTurkishStaffRole())
        assertEquals("Storyboard", "絵コンテ".toTurkishStaffRole())
        assertEquals("Animasyon Yönetmeni", "作画監督".toTurkishStaffRole())
        assertEquals("Karakter Tasarımı", "キャラクターデザイン".toTurkishStaffRole())
        assertEquals("Renk Tasarımı", "色彩設計".toTurkishStaffRole())
        assertEquals("Görüntü Yönetmeni", "撮影監督".toTurkishStaffRole())
        assertEquals("Anahtar Animasyon", "原画".toTurkishStaffRole())
        assertEquals("Müzik", "音楽".toTurkishStaffRole())
        assertEquals("Animasyon Yapımı", "アニメーション制作".toTurkishStaffRole())
        assertEquals("Orijinal Yaratıcı", "原作".toTurkishStaffRole())
    }

    @Test
    fun staffRoles_splitBangumiV0DotSeparator() {
        // v0 `/subjects/{id}/persons` görevleri " · " ile birle gelir
        assertEquals("Yönetmen, Senarist", "監督 · 脚本".toTurkishStaffRole())
        assertEquals("Yönetmen, Senarist", "監督、脚本".toTurkishStaffRole())
    }

    // ── Stüdyo adları ────────────────────────────────────────────────────────

    @Test
    fun studioNames_translateToLatin() {
        assertEquals("KyoAni", "京都アニメーション".toLatinStudioName())
        assertEquals("KyoAni", "京都动画".toLatinStudioName())
        assertEquals("PONY CANYON", "ポニーキャニオン".toLatinStudioName())
        assertEquals("Movic", "ムービック".toLatinStudioName())
        assertEquals("Toei Animation", "東映アニメーション".toLatinStudioName())
        // Bilinmeyen (kurgusal) stüdyo adı olduğu gibi kalır
        assertEquals("光坂高校演劇部", "光坂高校演劇部".toLatinStudioName())
        // Zaten Latin adlar değişmez
        assertEquals("TBS", "TBS".toLatinStudioName())
    }

    // ── Kişi/karakter detayı rol etiketleri (arayüz dili duyarlı) ────────────

    @Test
    fun bangumiRoles_turkishLocale_translateToTurkish() {
        // @Before Türkçe locale kurar.
        assertEquals("Ana Karakter", "主角".toLocalizedCharacterRole())
        assertEquals("Yardımcı Karakter", "配角".toLocalizedCharacterRole())
        assertEquals("Konuk Karakter", "客串".toLocalizedCharacterRole())
        assertEquals("Tema Şarkısı Performansı", "主題歌演出".toLocalizedStaffRole())
        assertEquals("Yönetmen", "导演".toLocalizedStaffRole())
        assertEquals("Yönetmen", "監督".toLocalizedStaffRole())
        assertEquals("Seri Düzenlemesi", "系列构成".toLocalizedStaffRole())
        // Birleşik görev listesi parça parça çevrilir
        assertEquals("Yönetmen, Senarist", "監督 · 脚本".toLocalizedStaffRole())
        // Bilinmeyen CJK rol uydurulmaz, olduğu gibi kalır
        assertEquals("未知の役割", "未知の役割".toLocalizedStaffRole())
        // İngilizce kaynak değerler TR arayüzde Türkçeleşir
        assertEquals("Ana Karakter", "Main Character".toLocalizedCharacterRole())
    }

    @Test
    fun bangumiRoles_englishLocale_translateToEnglish() {
        Locale.setDefault(Locale.US)
        try {
            assertEquals("Main Character", "主角".toLocalizedCharacterRole())
            assertEquals("Supporting Character", "配角".toLocalizedCharacterRole())
            assertEquals("Guest Character", "客串".toLocalizedCharacterRole())
            assertEquals("Theme Song Performance", "主題歌演出".toLocalizedStaffRole())
            assertEquals("Director", "导演".toLocalizedStaffRole())
            assertEquals("Series Composition", "系列构成".toLocalizedStaffRole())
            assertEquals("Director, Script", "監督 · 脚本".toLocalizedStaffRole())
            // İngilizce kaynak değerler aynen kalır
            assertEquals("Producer", "Producer".toLocalizedStaffRole())
            // Bilinmeyen CJK rol uydurulmaz
            assertEquals("未知の役割", "未知の役割".toLocalizedStaffRole())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun mediaTypeLabel_followsLocale() {
        assertEquals("Dizi", "tv".toLocalizedMediaTypeString())
        assertEquals("Anime", "anime".toLocalizedMediaTypeString())
        Locale.setDefault(Locale.US)
        try {
            assertEquals("TV Series", "tv".toLocalizedMediaTypeString())
            assertEquals("Anime", "ANIME".toLocalizedMediaTypeString())
            assertEquals("Manga", "manga".toLocalizedMediaTypeString())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
