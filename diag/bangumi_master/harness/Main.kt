// Sözlük sözleşmesi — JUnit testinin bağımlılıksız kopyası (derleme + çalıştırma doğrulaması)
var fail = 0
fun eq(what: String, expected: Any?, got: Any?) {
    if (expected != got) { println("FAIL $what → beklenen=$expected, gelen=$got"); fail++ }
}
fun main() {
    // §7.4 boşluklar
    eq("催淚", "tearjerker", BangumiTagDictionary.entryFor("催淚")?.key)
    eq("催泪", "tearjerker", BangumiTagDictionary.entryFor("催泪")?.key)
    eq("催涙", "tearjerker", BangumiTagDictionary.entryFor("催涙")?.key)
    eq("京都", "kyoto_animation", BangumiTagDictionary.entryFor("京都")?.key)
    eq("AS", "after_story", BangumiTagDictionary.entryFor("AS")?.key)
    eq("After Story", "after_story", BangumiTagDictionary.entryFor("After Story")?.key)
    eq("ASOBI → null", null, BangumiTagDictionary.entryFor("ASOBI")?.key)
    eq("続編", "sequel", BangumiTagDictionary.entryFor("続編")?.key)
    // yeni meta
    eq("华语", "area_chinese_speaking", BangumiTagDictionary.entryFor("华语")?.key)
    eq("加拿大", "area_canada", BangumiTagDictionary.entryFor("加拿大")?.key)
    eq("捷克", "area_czech", BangumiTagDictionary.entryFor("捷克")?.key)
    eq("马来西亚", "area_malaysia", BangumiTagDictionary.entryFor("马来西亚")?.key)
    eq("新西兰", "area_new_zealand", BangumiTagDictionary.entryFor("新西兰")?.key)
    eq("布袋戏", "theme_budaixi", BangumiTagDictionary.entryFor("布袋戏")?.key)
    eq("少儿", "theme_children", BangumiTagDictionary.entryFor("少儿")?.key)
    for ((l, k) in listOf("ARPG" to "genre_arpg","EDU" to "genre_edu","Roguelike" to "genre_roguelike","沙盒" to "genre_sandbox","VN" to "genre_vn","卡牌对战" to "genre_card_battle","JRPG" to "genre_jrpg","MOBA" to "genre_moba"))
        eq(l, k, BangumiTagDictionary.entryFor(l)?.key)
    // mevcut gruba eklenenler
    for ((l, k) in listOf("修仙" to "xianxia","打脸" to "face_slapping","青梅竹马" to "childhood_friends","物语系列" to "monogatari_series","VTuber" to "vtuber","浪人" to "wandering_ronin","大逃杀" to "death_game","获奖" to "award_winning"))
        eq(l, k, BangumiTagDictionary.entryFor(l)?.key)
    // şirket sözlüğü
    eq("京アニ", "bangumi_studio_kyoto_animation", BangumiOrgDictionary.resourceKeyFor("京アニ"))
    eq("京都动画", "bangumi_studio_kyoto_animation", BangumiOrgDictionary.resourceKeyFor("京都动画"))
    eq("ボンズ", "bangumi_studio_bones", BangumiOrgDictionary.resourceKeyFor("ボンズ"))
    eq("骨头社", "bangumi_studio_bones", BangumiOrgDictionary.resourceKeyFor("骨头社"))
    eq("スタジオコロリド", "bangumi_studio_colorido", BangumiOrgDictionary.resourceKeyFor("スタジオコロリド"))
    eq("キネマシトラス", "bangumi_studio_kinema_citrus", BangumiOrgDictionary.resourceKeyFor("キネマシトラス"))
    eq("木棉花", "bangumi_network_muse", BangumiOrgDictionary.resourceKeyFor("木棉花"))
    eq("TOKYO MX", "bangumi_network_tokyo_mx", BangumiOrgDictionary.resourceKeyFor("TOKYO MX"))
    eq("BS-i", "bangumi_network_bs_tbs", BangumiOrgDictionary.resourceKeyFor("BS-i"))
    eq("dアニメストア", "bangumi_network_d_anime_store", BangumiOrgDictionary.resourceKeyFor("dアニメストア"))
    eq("ニコニコ動画", "bangumi_network_niconico", BangumiOrgDictionary.resourceKeyFor("ニコニコ動画"))
    eq("アニプレックス", "bangumi_producer_aniplex", BangumiOrgDictionary.resourceKeyFor("アニプレックス"))
    eq("ポニーキャニオン", "bangumi_producer_pony_canyon", BangumiOrgDictionary.resourceKeyFor("ポニーキャニオン"))
    eq("週刊少年ジャンプ", "bangumi_magazine_weekly_shonen_jump", BangumiOrgDictionary.resourceKeyFor("週刊少年ジャンプ"))
    eq("Kyoto Animation", "bangumi_studio_kyoto_animation", BangumiOrgDictionary.resourceKeyFor("Kyoto Animation"))
    eq("Production I.G", "bangumi_producer_production_ig", BangumiOrgDictionary.resourceKeyFor("Production I.G"))
    eq("Production.IG (sıkı, tam etiket)", "bangumi_studio_ig", BangumiOrgDictionary.resourceKeyFor("Production.IG"))
    eq("latin(京アニ)", "KyoAni", BangumiOrgDictionary.latinNameFor("京アニ"))
    // politika: komite adları
    eq("光坂高校演劇部 → null", null, BangumiOrgDictionary.resourceKeyFor("光坂高校演劇部"))
    eq("PROJECT-XXX → null", null, BangumiOrgDictionary.resourceKeyFor("PROJECT-XXX"))
    // VAULT 001–009 & Batch 008 kapsam kontrolü
    eq("默片 → Sessiz Film", "Sessiz Film", BangumiTagDictionary.entryFor("默片")?.turkish)
    eq("默片 → Silent Film", "Silent Film", BangumiTagDictionary.entryFor("默片")?.english)
    eq("哥特萝莉 → Gotik Lolita", "Gotik Lolita", BangumiTagDictionary.entryFor("哥特萝莉")?.turkish)
    eq("哥特萝莉 → Gothic Lolita", "Gothic Lolita", BangumiTagDictionary.entryFor("哥特萝莉")?.english)
    eq("自然纪录片 → Doğa Belgeseli", "Doğa Belgeseli", BangumiTagDictionary.entryFor("自然纪录片")?.turkish)
    eq("自然纪录片 → Nature Documentary", "Nature Documentary", BangumiTagDictionary.entryFor("自然纪录片")?.english)
    eq("Tactical_shooter → Taktiksel Nişancı", "Taktiksel Nişancı", BangumiTagDictionary.entryFor("Tactical_shooter")?.turkish)
    eq("Tactical_shooter → Tactical Shooter", "Tactical Shooter", BangumiTagDictionary.entryFor("Tactical_shooter")?.english)
    eq("Souls-like → Souls Benzeri", "Souls Benzeri", BangumiTagDictionary.entryFor("Souls-like")?.turkish)
    eq("Souls-like → Souls-like", "Souls-like", BangumiTagDictionary.entryFor("Souls-like")?.english)
    eq("青柳昌行 → Aoyagi Masayuki", "Aoyagi Masayuki", BangumiOrgDictionary.latinNameFor("青柳昌行"))
    eq("落越友則 → Raku Etsu Tomonori", "Raku Etsu Tomonori", BangumiOrgDictionary.latinNameFor("落越友則"))
    eq("オーバーラップ文庫 → Overlap Bunko", "Overlap Bunko", BangumiOrgDictionary.latinNameFor("オーバーラップ文庫"))
    eq("月刊ウィングス → Monthly Wings", "Monthly Wings", BangumiOrgDictionary.latinNameFor("月刊ウィングス"))
    eq("CoMix Wave Films → CoMix Wave Films", "CoMix Wave Films", BangumiOrgDictionary.latinNameFor("CoMix Wave Films"))
    eq("森下勝司 → Morishita Katsuji", "Morishita Katsuji", BangumiOrgDictionary.latinNameFor("森下勝司"))
    eq("tag boyut == 58338", 58338, BangumiTagDictionary.size)
    eq("org boyut == 52644", 52644, BangumiOrgDictionary.size)
    if (fail == 0) {
        println("✅ TÜM KONTROLLER GEÇTİ (0 hata · tag=${BangumiTagDictionary.size} · org=${BangumiOrgDictionary.size})")
    } else {
        println("❌ $fail hata")
        kotlin.system.exitProcess(1)
    }
}
