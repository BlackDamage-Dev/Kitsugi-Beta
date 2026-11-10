package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.model.SourceQueryMode
import com.kitsugi.animelist.data.manga.model.SourceSearchPolicy

object TurkishSourceRegistry {

    private val trustedTurkishTokens = mapOf(
        // Tier 1 — En aktif TR kaynaklar (gunluk bolum)
        "manga-tr"          to 65,
        "mangatr"           to 65,
        "webtoonhatti"      to 60,
        "trmanga"           to 55,
        "mangadenizi"       to 50,
        "juratempe"         to 45,
        "juratempe.st"      to 45,

        // Tier 2 — Buyuk TR site aglarindan
        "mangawow"          to 40,
        "mangazure"         to 40,
        "asurascans"        to 38,
        "asurascanstr"      to 38,
        "hayalistic"        to 36,
        "uzaymanga"         to 35,
        "mangadiyari"       to 35,
        "limonmanga"        to 33,
        "mangakusu"         to 33,
        "mangaportali"      to 33,
        "mangawt"           to 32,
        "diamondfansub"     to 32,
        "tenshi"            to 30,
        "tenshimanga"       to 30,
        "eldermanga"        to 30,
        "summertoon"        to 28,
        "summertoons"       to 28,
        "anikiga"           to 28,
        "merlin"            to 26,
        "merlintoon"        to 26,
        "tortuga"           to 25,
        "tortugaceviri"     to 25,

        // Tier 3 — Diger TR kaynaklar (Keiyoushi 77 TR eklentisinin tamami)
        "afrodit"           to 22,
        "alucard"           to 22,
        "araznovel"         to 22,
        "arcura"            to 22,
        "caprazmanga"       to 22,
        "domal"             to 22,
        "eskimangalar"      to 22,
        "gafeland"          to 22,
        "gaiatoon"          to 22,
        "garcia"            to 22,
        "ghostoon"          to 22,
        // Kitsugi varsayılan aktif kaynak — en yüksek öncelik
        "golgebahcesi"      to 90,
        "gölge bahçesi"     to 90,
        "hattori"           to 20,
        "hattorimanga"      to 20,
        "hattoriscans"      to 20,
        "holyscans"         to 20,
        "koreli"            to 20,
        "korelimanga"       to 20,
        "kuroiyang"         to 18,
        "kuroimanga"        to 18,
        "lavinia"           to 18,
        "luna"              to 18,
        "mangabahcesi"      to 18,
        "manga-sehri"       to 18,
        "mangasehri"        to 18,
        "mangadusleri"      to 18,
        "mangawy"           to 18,
        "mangatatilkisi"    to 18,
        "mangatt"           to 18,
        "mangitto"          to 18,
        "mikrokosmos"       to 18,
        "milasub"           to 18,
        "millascan"         to 18,
        "monomanga"         to 18,
        "moondaisy"         to 18,
        "nemesis"           to 18,
        "nirvana"           to 18,
        "nivera"            to 18,
        "okutoon"           to 18,
        "opiatoon"          to 18,
        "orimanga"          to 18,
        "paradox"           to 18,
        "pati"              to 18,
        "ragnar"            to 18,
        "raindrop"          to 18,
        "ruyamanga"         to 18,
        "serein"            to 18,
        "shadow"            to 18,
        "shijie"            to 18,
        "siyahmelek"        to 18,
        "sleptmanga"        to 18,
        "stray"             to 18,
        "sunset"            to 18,
        "tarot"             to 18,
        "tonizu"            to 18,
        "toontaku"          to 18,
        "trmangaoku"        to 18,
        "turkce manga"      to 18,
        "turkcemanga"       to 18,
        "webtoonoku"        to 18,
        "yaoimangaoku"      to 18,
        "yaoiflix"          to 16,
        "amangaplanet"      to 16,
        // Eski token'lar -- geriye donuk uyumluluk icin
        "sadscans"          to 20,
        "manga denizi"      to 50,
    )

    private val trustedGlobalFallbackTokens = mapOf(
        // MangaDex: 3.450 TR manga -- en kapsamli global kaynak
        "mangadex"          to 35,
        // Diger global (TR icerik destekleyen)
        "weebcentral"       to 14,
        "weeb central"      to 14,
        "namicomi"          to 10,
        "globalcomix"       to 8,
        // NOT: comick/comick live kaldirildi -- comick.dev API 404 (TR icin gecersiz)
    )

    private val adultTokens = listOf(
        "hentai", "porn", "doujin", "adult", "18+", "xxx"
    )

    fun policyFor(source: MangaSource): SourceSearchPolicy {
        val signature = source.searchSignature()
        val isTurkish = source.lang.equals("tr", ignoreCase = true)
        val isAdult = adultTokens.any { it in signature }

        var priority = when {
            isTurkish -> 100
            source.lang.equals("en", ignoreCase = true) -> 20
            else -> 10
        }

        var isTrusted = false
        var reason: String? = null

        trustedTurkishTokens.forEach { (token, bonus) ->
            if (token in signature) {
                priority += bonus
                isTrusted = true
                reason = "trusted_tr:$token"
            }
        }
        trustedGlobalFallbackTokens.forEach { (token, bonus) ->
            if (token in signature) {
                priority += bonus
                isTrusted = true
                reason = reason ?: "trusted_global:$token"
            }
        }

        if (source.lang.equals("all", ignoreCase = true) && isTrusted) {
            priority += 8
        }

        if (isAdult) {
            priority -= 300
        }

        val isGlobal = source.isGlobalCatalog
        val isFallbackOnly = !isTurkish && !isGlobal
        val allowGlobalSearch = !isAdult
        val queryMode = when {
            isTurkish -> SourceQueryMode.TurkishTitleFirst
            isFallbackOnly -> SourceQueryMode.FallbackOnly
            else -> SourceQueryMode.Default
        }

        return SourceSearchPolicy(
            sourceKey = source.stableSourceKey(),
            priority = priority,
            isTurkishPreferred = isTurkish,
            isTrusted = isTrusted || isTurkish,
            isFallbackOnly = isFallbackOnly,
            allowGlobalSearch = allowGlobalSearch,
            queryMode = queryMode,
            reason = reason,
        )
    }
}
