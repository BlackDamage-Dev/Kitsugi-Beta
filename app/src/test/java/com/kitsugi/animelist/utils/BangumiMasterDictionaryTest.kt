package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi şirket/etiket sözlüğü — MASTER 2026-10-11 sözleşmesi.
 *
 * Kaynak: `diag/bangumi_master/orgs.tsv` + `tags.tsv` (üretici: `gen_bangumi_master.py`).
 * Bu testler ekran görüntüsüyle doğrulanan boşlukların (`催淚`, `京都`, `AS`, `続編`) kapandığını
 * ve şirket adı eşlemesinin TR/EN dil dosyası anahtarlarına doğru gittiğini sabitler.
 */
class BangumiMasterDictionaryTest {

    // ─── §7.4 Ekran görüntüsü boşlukları ─────────────────────────────────────

    @Test
    fun `geleneksel Cince 催淚 artik tearjerker olarak cozulur`() {
        val entry = BangumiTagDictionary.entryFor("催淚")
        assertNotNull("催淚 sözlükte olmalı", entry)
        assertEquals("tearjerker", entry!!.key)
        // Basit/geleneksel yazımlar aynı girişte buluşur.
        assertEquals(entry.key, BangumiTagDictionary.entryFor("催泪")?.key)
        assertEquals(entry.key, BangumiTagDictionary.entryFor("催涙")?.key)
    }

    @Test
    fun `studyo etiketi olarak kullanilan 京都 Kyotoya cozulur`() {
        val entry = BangumiTagDictionary.entryFor("京都")
        assertNotNull("京都 sözlükte olmalı", entry)
        assertEquals("kyoto_animation", entry!!.key)
    }

    @Test
    fun `Clannad AS cipi After Story anahtarina gider`() {
        val entry = BangumiTagDictionary.entryFor("AS")
        assertNotNull("AS sözlükte olmalı", entry)
        assertEquals("after_story", entry!!.key)
        // Kısa etiket yalnızca tam sözcük eşleşir; içeren adlar etkilenmez.
        assertNull(BangumiTagDictionary.entryFor("ASOBI"))
        assertEquals("after_story", BangumiTagDictionary.entryFor("After Story")?.key)
    }

    @Test
    fun `devam anlamindaki 続編 sequel anahtarina eklenir`() {
        val entry = BangumiTagDictionary.entryFor("続編")
        assertNotNull("続編 sözlükte olmalı", entry)
        assertEquals("sequel", entry!!.key)
    }

    // ─── §7.2 yeni meta etiketler ────────────────────────────────────────────

    @Test
    fun `yeni meta etiketler cozulur`() {
        val cases = mapOf(
            "华语" to "area_chinese_speaking",
            "加拿大" to "area_canada",
            "捷克" to "area_czech",
            "马来西亚" to "area_malaysia",
            "新西兰" to "area_new_zealand",
            "布袋戏" to "theme_budaixi",
            "少儿" to "theme_children",
            "ARPG" to "genre_arpg",
            "EDU" to "genre_edu",
            "Roguelike" to "genre_roguelike",
            "沙盒" to "genre_sandbox",
            "VN" to "genre_vn",
            "卡牌对战" to "genre_card_battle"
        )
        for ((label, expected) in cases) {
            assertEquals("$label → $expected", expected, BangumiTagDictionary.entryFor(label)?.key)
        }
    }

    @Test
    fun `mevcut gruplara eklenen kullanici etiketleri cozulur`() {
        val cases = mapOf(
            "修仙" to "xianxia",
            "打脸" to "face_slapping",
            "青梅竹马" to "childhood_friends",
            "物语系列" to "monogatari_series",
            "VTuber" to "vtuber",
            "浪人" to "wandering_ronin"
        )
        for ((label, expected) in cases) {
            assertEquals("$label → $expected", expected, BangumiTagDictionary.entryFor(label)?.key)
        }
    }

    // ─── Şirket / ağ adları (BangumiOrgDictionary) ───────────────────────────

    @Test
    fun `studyo adlari kaynak anahtarina ve Latin ada eslesir`() {
        val cases = mapOf(
            "京アニ" to "bangumi_studio_kyoto_animation",
            "京都动画" to "bangumi_studio_kyoto_animation",
            "スタジオコロリド" to "bangumi_studio_colorido",
            "ボンズ" to "bangumi_studio_bones",
            "骨头社" to "bangumi_studio_bones",
            "キネマシトラス" to "bangumi_studio_kinema_citrus"
        )
        for ((label, expected) in cases) {
            assertEquals("$label → $expected", expected, BangumiOrgDictionary.resourceKeyFor(label))
        }
    }

    @Test
    fun `yayinci aglar ve platformlar eslesir`() {
        val cases = mapOf(
            "木棉花" to "bangumi_network_muse",
            "TOKYO MX" to "bangumi_network_tokyo_mx",
            "BS-i" to "bangumi_network_bs_tbs",
            "フジテレビ" to "bangumi_network_fuji_tv",
            "dアニメストア" to "bangumi_network_d_anime_store",
            "ニコニコ動画" to "bangumi_network_niconico"
        )
        for ((label, expected) in cases) {
            assertEquals("$label → $expected", expected, BangumiOrgDictionary.resourceKeyFor(label))
        }
    }

    @Test
    fun `yapimci ve yayimcilar eslesir`() {
        // Aynı şirket birden çok rolde listelenebilir (ör. 白泉社 hem yapımcı hem yayımcı).
        // Bu durumda ilk kayıt kazanır; ekranda görünen ad aynı olduğu için sorun değildir.
        val cases = mapOf(
            "アニプレックス" to setOf("bangumi_producer_aniplex"),
            "ポニーキャニオン" to setOf("bangumi_producer_pony_canyon"),
            "集英社" to setOf("bangumi_producer_shueisha", "bangumi_publisher_shueisha"),
            "芳文社" to setOf("bangumi_producer_houbunsha", "bangumi_publisher_houbunsha"),
            "白泉社" to setOf("bangumi_producer_hakusensha", "bangumi_publisher_hakusensha"),
            "週刊少年ジャンプ" to setOf("bangumi_magazine_weekly_shonen_jump")
        )
        for ((label, expected) in cases) {
            val got = BangumiOrgDictionary.resourceKeyFor(label)
            assertTrue("$label → $got (beklenen: $expected)", got in expected)
        }
    }

    @Test
    fun `Latin adlar da anahtara geri eslesir`() {
        // Detay istemcisi CJK adı zaten Latin ada sabitlediği için ikinci tur şart.
        val cases = mapOf(
            "Kyoto Animation" to "bangumi_studio_kyoto_animation",
            "BONES" to "bangumi_studio_bones",
            // Production I.G hem stüdyo hem yapımcı kaydı (aynı Latin ad) — ilk kayıt kazanır.
            "Production I.G" to "bangumi_producer_production_ig",
            "Muse Communication" to "bangumi_network_muse",
            "Pony Canyon" to "bangumi_producer_pony_canyon"
        )
        for ((name, expected) in cases) {
            assertEquals("$name → $expected", expected, BangumiOrgDictionary.resourceKeyFor(name))
        }
    }

    // ─── Politika: uydurma çeviri yok ────────────────────────────────────────

    @Test
    fun `uretim komitesi adlari cevrilmez`() {
        val committees = listOf("光坂高校演劇部", "PROJECT-XXX", "Clannad製作委員会")
        for (name in committees) {
            assertNull("$name eşleşmemeli", BangumiOrgDictionary.resourceKeyFor(name))
            assertEquals("$name Latin adına sabitlenmemeli", name, name.toLatinStudioName())
            assertEquals("$name yerelleştirilmemeli", name, name.toLocalizedOrgName())
        }
    }

    @Test
    fun `studioLatinNameMap hala calisir ve org sozlugu onu tamamlar`() {
        assertEquals("KyoAni", "京都アニメーション".toLatinStudioName())
        assertEquals("GONZO", "ゴンゾ".toLatinStudioName())
        assertEquals("Science SARU", "サイエンスSARU".toLatinStudioName())
        // Eşleşmeyen ad olduğu gibi kalır.
        assertEquals("未知のスタジオ", "未知のスタジオ".toLatinStudioName())
    }

    @Test
    fun `sozluk boyutu beklendigi gibi`() {
        assertEquals("tag sözlüğü 58338 grup içermeli", 58338, BangumiTagDictionary.size)
        assertEquals("org sözlüğü 52644 eşleme içermeli", 52644, BangumiOrgDictionary.size)
        // sözlük girişlerinin Latin adı boş olamaz
        for (key in listOf("bangumi_studio_kyoto_animation", "bangumi_network_muse")) {
            assertTrue("$key için Latin ad olmalı", !BangumiOrgDictionary.canonicalName(key).isNullOrBlank())
        }
    }

    @Test
    fun `vault 001_009 kavram etiketleri ve kuresel kurum adlari TR ve EN arayuzde dogru cozulur`() {
        val prev = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            assertEquals("Sessiz Film", "默片".toLocalizedTagLabel())
            assertEquals("Gotik Lolita", "哥特萝莉".toLocalizedTagLabel())
            assertEquals("Doğa Belgeseli", "自然纪录片".toLocalizedTagLabel())
            assertEquals("Taktiksel Nişancı", "Tactical_shooter".toLocalizedTagLabel())
            assertEquals("Souls Benzeri", "Souls-like".toLocalizedTagLabel())
            assertEquals("Hafif Roman", "轻小说".toLocalizedTagLabel())
            assertEquals("KyoAni", "京アニ".toLocalizedOrgName())
            assertEquals("Aoyagi Masayuki", "青柳昌行".toLocalizedOrgName())
            assertEquals("Raku Etsu Tomonori", "落越友則".toLocalizedOrgName())
            assertEquals("Overlap Bunko", "オーバーラップ文庫".toLocalizedOrgName())
            assertEquals("Monthly Wings", "月刊ウィングス".toLocalizedOrgName())
            assertEquals("CoMix Wave Films", "コミックス・ウェーブ・フィルム".toLocalizedOrgName())
            assertEquals("Morishita Katsuji", "森下勝司".toLocalizedOrgName())
            assertEquals("Clannad製作委員会", "Clannad製作委員会".toLocalizedOrgName())

            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            assertEquals("Silent Film", "默片".toLocalizedTagLabel())
            assertEquals("Gothic Lolita", "哥特萝莉".toLocalizedTagLabel())
            assertEquals("Nature Documentary", "自然纪录片".toLocalizedTagLabel())
            assertEquals("Tactical Shooter", "Tactical_shooter".toLocalizedTagLabel())
            assertEquals("Souls-like", "Souls-like".toLocalizedTagLabel())
            assertEquals("Light Novel", "轻小说".toLocalizedTagLabel())
            assertEquals("KyoAni", "京アニ".toLocalizedOrgName())
            assertEquals("Aoyagi Masayuki", "青柳昌行".toLocalizedOrgName())
            assertEquals("Raku Etsu Tomonori", "落越友則".toLocalizedOrgName())
            assertEquals("Overlap Bunko", "オーバーラップ文庫".toLocalizedOrgName())
            assertEquals("Monthly Wings", "月刊ウィングス".toLocalizedOrgName())
            assertEquals("CoMix Wave Films", "コミックス・ウェーブ・フィルム".toLocalizedOrgName())
            assertEquals("Morishita Katsuji", "森下勝司".toLocalizedOrgName())
            assertEquals("Clannad製作委員会", "Clannad製作委員会".toLocalizedOrgName())
        } finally {
            java.util.Locale.setDefault(prev)
        }
    }
}
