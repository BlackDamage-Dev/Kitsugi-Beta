package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.BangumiOrgDictionary
import java.util.Locale

/** Identity helpers for studio/company IDs shared by detail chips and detail loading. */
internal object StudioSourceSupport {
    /** TMDB network IDs live in a different namespace from production-company IDs. */
    const val TMDB_NETWORK_SOURCE = "tmdb_network"

    private val detailSources = setOf("jikan", "anilist", "bangumi", "tmdb", TMDB_NETWORK_SOURCE, "shikimori")
    private val legalCompanySuffixes = setOf(
        "inc", "incorporated", "co", "company", "ltd", "limited", "corp", "corporation", "llc", "gmbh"
    )

    /**
     * Üretim komiteleri (製作委員会 / 制作委员会 / …) Bangumi'de **kurum kaydı değildir**;
     * yalnızca infobox metnidir. Bu adlar için kurum sayfası açılamaz, bu yüzden çipler
     * tıklanabilir olmamalıdır (tıklanınca "Stüdyo detayları yüklenemedi" ekranı çıkıyordu).
     */
    private val committeeMarkers = listOf(
        "製作委員会", "制作委员会", "制作委員會", "製作委員會",
        "製作委员会", "制作委员会", "委员会", "委員會",
        "production committee", "committee"
    )

    fun canonicalSource(source: String?): String? {
        val value = source?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (value) {
            "shiki" -> "shikimori"
            "bgm" -> "bangumi"
            "themoviedb" -> "tmdb"
            "tmdb-network", "tmdbnetwork" -> TMDB_NETWORK_SOURCE
            TMDB_NETWORK_SOURCE -> TMDB_NETWORK_SOURCE
            else -> MalJikanMediaSupport.canonicalSource(value)
        }
    }

    /**
     * Prefer the source attached to the studio itself. Studio IDs are provider-specific,
     * and cross-source detail enrichment can attach a TMDB studio to an AniList title (or
     * vice versa). Only fall back to the parent media source for older records that have no
     * studio source metadata.
     */
    fun resolveClickSource(
        studioSource: String?,
        mediaSource: String,
        mediaType: MediaType,
        role: StudioRole? = null
    ): String {
        val studioProvider = canonicalSource(studioSource)
        val mediaProvider = canonicalSource(mediaSource)
        val resolvedProvider = when {
            studioProvider != null && studioProvider in detailSources -> studioProvider
            mediaProvider != null && mediaProvider in detailSources -> mediaProvider
            else -> {
                // Legacy sources that don't expose a studio endpoint can still carry
                // cross-provider records; use the conventional media fallback only when
                // both records omit a supported provider namespace.
                when (mediaType) {
                    MediaType.Movie, MediaType.TvShow -> "tmdb"
                    MediaType.Anime, MediaType.Manga -> "jikan"
                }
            }
        }

        // TMDB TV networks and production companies have independent IDs and endpoints.
        // Passing a network's ID to /company/{id} can 404 or show a different organization.
        // Older persisted Simkl/AniList/etc. details may omit the chip source; their network
        // IDs came from TMDB enrichment, except Bangumi's own person-linked companies.
        return when {
            role == StudioRole.NETWORK && resolvedProvider == "tmdb" -> TMDB_NETWORK_SOURCE
            role == StudioRole.NETWORK && studioProvider == null && mediaProvider != "bangumi" -> TMDB_NETWORK_SOURCE
            else -> resolvedProvider
        }
    }

    /**
     * Compare names before showing a fetched/cached Jikan detail for a clicked chip. Legal
     * company suffixes such as "Inc." are normalized away, while distinct companies such as
     * Aniplex and BONES remain different.
     */
    fun namesMatch(expected: String?, actual: String?): Boolean {
        val expectedName = normalizeName(expected)
        if (expectedName.isEmpty()) return true
        val actualName = normalizeName(actual)
        if (actualName.isEmpty()) return false
        if (expectedName == actualName) return true
        // Aynı kurumun farklı dildeki yazımı (SHUEISHA ↔ 集英社, NBCUniversal Entertainment
        // Japan ↔ ジェネオン・ユニバーサル・エンターテイメント): sözlük aynı satıra
        // bağlıyorsa bu bir uyuşmazlık değildir. Bu kontrol olmadan doğru kurum sayfası
        // "isim uyuşmadı" diye reddediliyor ve gereksiz yere başka sağlayıcıya düşülüyordu.
        val expectedKey = orgIdentityKey(expected)
        val actualKey = orgIdentityKey(actual)
        if (expectedKey != null && expectedKey == actualKey) return true
        val expectedLatin = BangumiOrgDictionary.latinNameFor(expected)
        val actualLatin = BangumiOrgDictionary.latinNameFor(actual)
        if (expectedLatin != null && actualLatin != null) {
            return normalizeName(expectedLatin) == normalizeName(actualLatin)
        }
        return false
    }

    /**
     * Sözlükteki kurum kimliği (`bangumi_producer_shueisha` gibi); bilinmiyorsa `null`.
     *
     * Çiplerin yinelenmesini engellemek için kullanılır: infobox aynı şirketi hem `集英社`
     * hem `SHUEISHA` olarak verebiliyor.
     */
    fun orgIdentityKey(value: String?): String? = BangumiOrgDictionary.identityKey(value)

    fun normalizeName(value: String?): String = value.orEmpty()
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it !in legalCompanySuffixes }
        .joinToString(separator = "")

    /** Ad üretim komitesi mi (kurum kaydı yok → tıklanamaz çip)? */
    fun isCommitteeName(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return false
        val lower = text.lowercase(Locale.ROOT)
        return committeeMarkers.any { marker ->
            if (marker.first().code < 0x300) lower.contains(marker) else text.contains(marker)
        }
    }

    /**
     * Bir kurum adının farklı kaynaklarda aranabilecek yazımları.
     *
     * Bangumi infobox'ı aynı şirketi bazen Japonca (`ジェネオン・ユニバーサル・エンターテイメント`),
     * bazen Latin (`SHUEISHA`) yazımla verir. Bangumi/Jikan/AniList aramaları bu yüzden tek bir
     * ham dizeyle sınırlandırılmamalıdır: parantez içi takma adlar, sözlükteki küresel ad ve
     * o küresel adın Bangumi'deki lokal karşılığı da denenir. Uydurma değer üretilmez; hepsi
     * ya ham metinden ya da sözlükten (orgs.tsv) gelir.
     */
    fun orgNameVariants(value: String?): List<String> {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val variants = LinkedHashSet<String>()
        variants += raw
        val stripped = stripBrackets(raw)
        // Sözlük anahtarına bağlı tüm bilinen adlar (Latin + CJK)
        val key = BangumiOrgDictionary.identityKey(raw)
            ?: if (stripped.isNotEmpty()) BangumiOrgDictionary.identityKey(stripped) else null
        if (key != null) {
            variants += BangumiOrgDictionary.labelsForKey(key)
        }
        BangumiOrgDictionary.latinNameFor(raw)?.let { latin ->
            variants += latin
            variants += BangumiOrgDictionary.localNamesForLatin(latin)
        }
        if (stripped.isNotEmpty() && stripped != raw) {
            BangumiOrgDictionary.latinNameFor(stripped)?.let { latin ->
                variants += latin
                variants += BangumiOrgDictionary.localNamesForLatin(latin)
            }
        }
        variants += BangumiOrgDictionary.lookupVariants(raw)
        return variants
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 120 }
            .distinct()
            .take(MAX_ORG_VARIANTS)
    }

    /** Latin ad → Bangumi lokal ad köprüsü (kayıt yoksa boş). */
    fun localNamesForLatin(latinName: String?): List<String> =
        BangumiOrgDictionary.localNamesForLatin(latinName)

    private fun stripBrackets(value: String): String {
        val sb = StringBuilder(value.length)
        var depth = 0
        for (ch in value) {
            when (ch) {
                '（', '(', '[', '【', '《', '「', '『' -> depth++
                '）', ')', ']', '】', '》', '」', '』' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /** Tek bir ad için denenecek en fazla arama yazımı (ağ isteği sayısını sınırlar). */
    private const val MAX_ORG_VARIANTS = 16
}
