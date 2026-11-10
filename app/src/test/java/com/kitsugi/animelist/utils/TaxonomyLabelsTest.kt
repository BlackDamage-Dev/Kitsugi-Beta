package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.KitsugiStudio
import com.kitsugi.animelist.data.remote.KitsugiTaxonomyMerge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * **Ortak taksonomi (tür / etiket / kurum) sözleşme testleri.**
 *
 * Kapsam:
 *  • Shikimori'nin Rusça türleri TR/EN arayüzde doğru karşılığa çözülür,
 *  • Ekranda **hiçbir koşulda** Kiril metin kalmaz,
 *  • `Экшен` · `Action` · `アクション` · `动作` · `Aksiyon` tek kanonik anahtara iner,
 *  • Kaynaklardan gelen tür/etiket/kurum listeleri **birleşir** (biri diğerini ezmez),
 *  • Kurum adlarındaki infobox çöpü (kaomoji, `;` notları, tırnak artığı) temizlenir.
 */
class TaxonomyLabelsTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setTurkishLocale() {
        Locale.setDefault(Locale("tr", "TR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ── Rusça (Shikimori) → gösterim dili ────────────────────────────────────

    @Test
    fun `rusca turler turkce arayuzde turkce karsilik bulur`() {
        assertEquals("Aksiyon", "Экшен".toLocalizedTaxonomyLabel())
        assertEquals("Günlük Yaşam", "Повседневность".toLocalizedTaxonomyLabel())
        assertEquals("Bilim Kurgu", "Фантастика".toLocalizedTaxonomyLabel())
        assertEquals("Sihirli Kız", "Махо-сёдзё".toLocalizedTaxonomyLabel())
        assertEquals("Shōnen", "Сёнен".toLocalizedTaxonomyLabel())
    }

    @Test
    fun `rusca turler ingilizce arayuzde ingilizce karsilik bulur`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Action", "Экшен".toLocalizedTaxonomyLabel())
        assertEquals("Slice of Life", "Повседневность".toLocalizedTaxonomyLabel())
        assertEquals("Romance", "Романтика".toLocalizedTaxonomyLabel())
    }

    @Test
    fun `sozlukte olmayan kiril etiketi latin harflerine cevrilir`() {
        val label = "НеизвестныйТег".toLocalizedTaxonomyLabel()
        assertFalse("Ekranda Kiril kalmamalı: $label", label.hasCyrillicCharacters())
        assertTrue("Boş olmayan bir değer beklenir", label.isNotBlank())
    }

    @Test
    fun `turkce degerlerde kiril harf bulunmaz`() {
        val leaking = RussianTaxonomyDictionary.allEntries()
            .filter { it.turkish.hasCyrillicCharacters() || it.english.hasCyrillicCharacters() }
        assertTrue("Kiril sızıntısı: ${leaking.map { it.key }}", leaking.isEmpty())
    }

    // ── Kanonik anahtar (diller arası tekilleştirme) ─────────────────────────

    @Test
    fun `ayni anlama gelen farkli diller tek kanonik anahtara iner`() {
        val expected = "action".taxonomyCanonicalKey()
        assertEquals("action", expected)
        assertEquals(expected, "Экшен".taxonomyCanonicalKey())
        assertEquals(expected, "Aksiyon".taxonomyCanonicalKey())
        assertEquals(expected, "Action".taxonomyCanonicalKey())
        assertEquals(expected, "アクション".taxonomyCanonicalKey())
        assertEquals(expected, "动作".taxonomyCanonicalKey())
    }

    @Test
    fun `gunluk yasam farkli dillerde ayni anahtar`() {
        val expected = "Slice of Life".taxonomyCanonicalKey()
        assertEquals(expected, "Повседневность".taxonomyCanonicalKey())
        assertEquals(expected, "日常".taxonomyCanonicalKey())
        assertEquals(expected, "Günlük Yaşam".taxonomyCanonicalKey())
    }

    // ── Resmî Shikimori kapsamı ve MAL kimlikleri ────────────────────────────

    @Test
    fun `shikimori resmi turlerinin tamami sozlukte`() {
        val official = listOf(
            "Экшен", "Приключения", "Гонки", "Комедия", "Авангард", "Мифология", "Тайна",
            "Драма", "Этти", "Фэнтези", "Стратегические игры", "Хентай", "Исторический",
            "Ужасы", "Детское", "Магия", "Боевые искусства", "Меха", "Музыка", "Пародия",
            "Самураи", "Романтика", "Школа", "Фантастика", "Сёдзё", "Сёдзё-ай", "Сёнен",
            "Сёнен-ай", "Космос", "Спорт", "Супер сила", "Вампиры", "Яой", "Юри", "Гарем",
            "Повседневность", "Сверхъестественное", "Военное", "Детектив", "Психологическое",
            "Сэйнэн", "Дзёсей", "Смена пола", "Командный спорт", "Видеоигры",
            "Взрослые персонажи", "Жестокость", "Реинкарнация", "Любовный многоугольник",
            "Изобразительное искусство", "Путешествие во времени", "Гэг-юмор",
            "Удостоено наград", "Триллер", "Спортивные единоборства", "CGDCT", "Махо-сёдзё",
            "Реверс-гарем", "Исэкай", "Хулиганы", "Забота о детях", "Магическая смена пола",
            "Шоу-бизнес", "Культура отаку", "Организованная преступность", "Работа",
            "Иясикэй", "Выживание", "Исполнительское искусство", "Антропоморфизм",
            "Кроссдрессинг", "Идолы (Жен.)", "Игра с высокими ставками", "Медицина",
            "Питомцы", "Образовательное", "Идолы (Муж.)", "Романтический подтекст", "Гурман"
        )
        assertEquals(79, official.size)
        val missing = official.filter { RussianTaxonomyDictionary.entryFor(it) == null }
        assertTrue("Sözlükte eksik resmî tür: $missing", missing.isEmpty())
    }

    @Test
    fun `mal tur kimligiyle de cozum yapilir`() {
        assertEquals("action", RussianTaxonomyDictionary.entryForMalGenreId(1)?.key)
        assertEquals("slice_of_life", RussianTaxonomyDictionary.entryForMalGenreId(36)?.key)
        assertNull(RussianTaxonomyDictionary.entryForMalGenreId(0))
    }

    // ── Çözülemeyen yabancı etiketler gösterilmez ────────────────────────────

    @Test
    fun `cozulemeyen cince etiket gosterimden cikarilir`() {
        assertTrue(isUnresolvedForeignLabel("非常识的な独自タグzzz"))
        assertFalse(isUnresolvedForeignLabel("Повседневность"))
        assertFalse(isUnresolvedForeignLabel("Action"))
    }

    @Test
    fun `etiket listesi rusca ve cince sizinti icermez`() {
        val labels = listOf(
            "Экшен",
            "Повседневность",
            "Action",
            "Aksiyon",
            "非常识的な独自タグzzz"
        ).localizedDistinctTags()

        // `Экшен` ≡ `Action` ≡ `Aksiyon` tek çip, CJK etiket hiç yok.
        assertEquals(2, labels.size)
        assertEquals("Экшен", labels[0])
        assertEquals("Повседневность", labels[1])
        assertTrue(
            "Çince/Japonca etiket listeye girmemeli: $labels",
            labels.none { it.hasCjkCharacters() }
        )
        val displayed = labels.map { it.toLocalizedTaxonomyLabel() }
        assertTrue("Gösterimde Kiril kalmamalı: $displayed", displayed.none { it.hasCyrillicCharacters() })
        assertTrue("Gösterimde CJK kalmamalı: $displayed", displayed.none { it.hasCjkCharacters() })
    }

    // ── Tür birleştirme (yerine koyma değil) ─────────────────────────────────

    @Test
    fun `iki kaynagin turleri birlestirilir`() {
        val merged = KitsugiTaxonomyMerge.mergeGenres(
            listOf("Action", "Adventure"),
            listOf("Экшен", "Drama", "Повседневность")
        )
        // `Экшен` ≡ `Action` → tek çip; diğerleri eklenir.
        assertEquals(4, merged.size)
        assertEquals("Action", merged[0])
        assertTrue(merged.contains("Adventure"))
        assertTrue(merged.contains("Drama"))
        assertTrue(merged.contains("Повседневность"))
    }

    @Test
    fun `tur sinirlari asilmaz`() {
        val many = (1..200).map { "Genre $it" }
        assertEquals(160, KitsugiTaxonomyMerge.mergeGenres(many).size)
    }

    // ── Kurum birleştirme ve temizlik ────────────────────────────────────────

    @Test
    fun `ayni kurum tek kez kalir ve kimlikli kayit tercih edilir`() {
        val withoutId = KitsugiStudio(id = 0, name = "MAPPA", source = "bangumi")
        val withId = KitsugiStudio(id = 569, name = "MAPPA", source = "anilist")
        val merged = KitsugiTaxonomyMerge.mergeOrgs(listOf(withoutId), listOf(withId))
        assertEquals(1, merged.size)
        assertEquals(569, merged[0].id)
    }

    @Test
    fun `studyo olan yapimci listesinde tekrar etmez`() {
        val studio = KitsugiStudio(id = 1, name = "Kyoto Animation")
        val producer = KitsugiStudio(id = 2, name = "kyoto animation")
        val filtered = KitsugiTaxonomyMerge.dropProducersThatAreStudios(
            listOf(producer, KitsugiStudio(id = 3, name = "Lantis")),
            listOf(studio)
        )
        assertEquals(listOf("Lantis"), filtered.map { it.name })
    }

    @Test
    fun `kurum adindaki infobox copu temizlenir`() {
        assertEquals("アフタヌーン", "\"\"\"アフタヌーン".sanitizeOrgName())
        assertEquals("芳文社", "芳文社; B6版".sanitizeOrgName())
        assertEquals("", "っ´Д`)っゼロ式の家".sanitizeOrgName())
        // Gerçek kısa kısaltmalar korunur.
        assertEquals("CR", "CR".sanitizeOrgName())
        assertEquals("B站", "B站".sanitizeOrgName())
        assertEquals("Production +h.", "Production +h.".sanitizeOrgName())
    }
}
