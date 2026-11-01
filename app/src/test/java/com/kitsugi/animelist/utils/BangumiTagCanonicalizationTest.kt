package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `BangumiTagDictionary.canonicalTagFor()` — arayüz tür çiplerinin sunucuya giden
 * **Bangumi yazımı**.
 *
 * Beklenen değerler tahmin değil: 1 Kasım 2026 tarihli canlı `POST /v0/search/subjects`
 * probu (`probe/result.txt`, K/L/N blokları) ile ölçüldü. Boş ya da çok zayıf yazımlar
 * (`悬疑惊悚` = 0, `运动系` = 8, `军事` = 63) yerine aynı grubun dolu yazımı seçilir.
 */
class BangumiTagCanonicalizationTest {

    private fun canonical(label: String?): String? = BangumiTagDictionary.canonicalTagFor(label)

    @Test
    fun `zayif yazimlar yerine dolu Bangumi etiketi secilir`() {
        // Canlı ölçüm: 运动系=8 → 运动=516, 悬疑惊悚=0 → 惊悚=114, 军事=63 → 战争=445.
        assertEquals("Spor", "运动", canonical("Sports"))
        assertEquals("Gerilim", "惊悚", canonical("Thriller"))
        assertEquals("Askeri", "战争", canonical("Military"))
        assertEquals("Doğaüstü", "灵异", canonical("Supernatural"))
        assertEquals("Psikolojik", "心理", canonical("Psychological"))
    }

    @Test
    fun `turkce cip adlari da sunucu yazimina cevrilir`() {
        assertEquals("Aksiyon", "战斗", canonical("Aksiyon"))
        assertEquals("Gerilim", "惊悚", canonical("Gerilim"))
        assertEquals("Spor", "运动", canonical("Spor"))
        assertEquals("Romantizm", "恋爱", canonical("Romantizm"))
        assertEquals("Psikoloji", "心理", canonical("Psikoloji"))
        assertEquals("Bilim kurgu", "科幻", canonical("Bilim Kurgu"))
        assertEquals("Savaş", "战争", canonical("Savaş"))
    }

    @Test
    fun `ingilizce cip adlari sunucu yazimina cevrilir`() {
        assertEquals("Sci-Fi", "科幻", canonical("Sci-Fi"))
        assertEquals("Science Fiction", "科幻", canonical("Science Fiction"))
        assertEquals("Slice of Life", "日常", canonical("Slice of Life"))
        assertEquals("School", "校园", canonical("School"))
        assertEquals("Historical", "历史", canonical("Historical"))
    }

    @Test
    fun `zaten Bangumi yaziminda olan etiket korunur`() {
        assertEquals("剧情", canonical("剧情"))
        assertEquals("机战", canonical("机战"))
    }

    @Test
    fun `bilinmeyen latin terim sunucuya gonderilmez`() {
        assertNull(canonical("kitsugi-unknown-genre"))
        assertNull(canonical(""))
        assertNull(canonical("   "))
    }

    @Test
    fun `hicbir tur cipi latin yazimla sunucuya gitmez`() {
        // Bangumi `filter.tag` birebir karşılaştırır; Latin etiket = 0 sonuç.
        val chips = listOf(
            "Drama", "Action", "Romance", "Comedy", "Fantasy", "Sci-Fi", "Mystery",
            "Horror", "Slice of Life", "Adventure", "Supernatural", "Psychological",
            "Sports", "Thriller", "Mecha", "Music", "Historical", "Military", "School"
        )
        for (chip in chips) {
            val tag = canonical(chip)
            assertTrue("$chip → null", tag != null)
            assertTrue(
                "$chip → $tag (Latin yazım sunucuda 0 sonuç verir)",
                BangumiTagDictionary.looksLikeNativeTag(tag!!)
            )
        }
    }
}
