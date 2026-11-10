package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale


/**
 * Çapraz senkronizasyonda **yanlış içerik eklenmesine** karşı kimlik güvencesi.
 *
 * Sorun: Bir grubun MAL kimliği tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`,
 * AniList `idMal`) ya da ARM/Jikan çıkarımından geliyorsa ve bu eşleme hatalıysa, hem yeni kayıt
 * eklemek hem de hedef hesaptaki mevcut kayda durum/ilerleme/puan yazmak yanlış içeriği etkileyebilir.
 * Bu nedenle her çapraz hesaplı yazma yolu, yeni veya mevcut hedef olmasından bağımsız olarak hem
 * kaynak MAL kimliğini hem de hedef sağlayıcının native kaydını yeniden doğrulamalıdır.
 *
 * Kurallar:
 *  - Kaynağın veya birden çok sağlayıcının ID iddiası tek başına yeterli değildir.
 *  - Kaynak MAL kimliği, MAL'ın resmî kataloğunda medya türü, başlık ve bilinen yıl ile doğrulanır.
 *  - Hedef sağlayıcı ID'si kendi namespace'inde doğrulanır; Shikimori `target_id` MAL ID sayılmaz.
 *  - Uyuşmazlıkta, eksik yıl/başlıkta veya katalog erişilemediğinde mutasyon durur.
 */
object CrossSyncIdentityGuard {

    private const val TAG = "CrossSyncIdentityGuard"

    /** Gerçek kaynak kaydı kendi MAL ID'siyle tanımlanır; Shikimori ID'leri bu kümeye girmez. */
    val NATIVE_MAL_SOURCES: Set<String> = setOf("mal", "myanimelist")

    sealed class Verdict {
        /** Tanı/geriye dönük uyumluluk için tutulur; cross-account eklemeyi asla yetkilendirmez. */
        data class Trusted(val reason: String) : Verdict()

        /** MAL kataloğu ile başlık, tür ve yıl uyuştu; yazma aramalarında yalnız bu yerel alias'lar kullanılmalı. */
        data class Verified(
            val remoteTitle: String,
            val remoteYear: Int?,
            val matchedLocalTitles: List<String> = emptyList()
        ) : Verdict()

        /** MAL kataloğundaki içerik yerel kayıtla uyuşmuyor — kimlik büyük olasılıkla yanlış. */
        data class Rejected(val reason: String, val remoteTitle: String?, val remoteYear: Int?) : Verdict()

        /** Katalog ulaşılamadı / yanıt vermedi; güvenli taraf: ekleme yapılmaz. */
        data class Unverifiable(val reason: String) : Verdict()

        val allowsAdditions: Boolean get() = this is Verified

        fun describe(): String = when (this) {
            is Trusted -> KitsugiStrings.get(R.string.cross_sync_identity_trusted, reason)
            is Verified -> KitsugiStrings.get(
                R.string.cross_sync_identity_verified,
                remoteTitle,
                remoteYear?.let { KitsugiStrings.get(R.string.cross_sync_identity_year_suffix, it) }.orEmpty()
            )
            is Rejected -> KitsugiStrings.get(R.string.cross_sync_identity_rejected, reason)
            is Unverifiable -> KitsugiStrings.get(R.string.cross_sync_identity_unverifiable, reason)
        }
    }

    /** MAL kataloğundan çekilen hafif kimlik bilgisi. */
    data class RemoteIdentity(
        val malId: Int,
        val titles: List<String>,
        val year: Int?,
        val mediaType: String?
    ) {
        val primaryTitle: String get() = titles.firstOrNull().orEmpty()
    }

    // ───────────────────────────── Başlık / yıl karşılaştırma ─────────────────────────────

    /**
     * Yapısal kelimeler: sezon/bölüm/film ekleri ve Japonca/İngilizce edatlar. Bunlar tek başına iki
     * başlığı "akraba" yapmaz (aksi halde her "Season 2" her "Movie" ile eşleşirdi).
     */
    private val structuralTokens = setOf(
        "the", "a", "an", "of", "and", "or", "in", "on", "at", "for", "with", "from", "by",
        "no", "gou", "wa", "ga", "to", "ni", "de", "wo", "o", "e", "ha", "mo", "ya", "da", "na", "ne", "yo", "ka",
        "x", "vs", "feat",
        "season", "seasons", "part", "parts", "cour", "arc", "chapter", "chapters", "volume",
        "movie", "movies", "film", "films", "eiga", "gekijouban", "gekijou", "ban", "hen",
        "animation", "anime", "tv", "ova", "oad", "ona", "special", "specials", "series", "edition",
        "final", "first", "second", "third", "fourth", "fifth", "new", "shin", "zoku", "kai", "re", "remake",
        "1st", "2nd", "3rd", "4th", "5th", "6th", "nd", "rd", "th", "i", "ii", "iii", "iv", "v", "vi"
    )

    private val nonWordCharacters = Regex("[^\\p{L}\\p{N}]+")
    private val yearRegex = Regex("^(\\d{4})")
    private val trailingMediaTypeQualifier = Regex(
        "\\s*[\\(\\[]\\s*(?:tv|movie|film|ova|oad|ona|special|specials|anime)\\s*[\\)\\]]\\s*$",
        RegexOption.IGNORE_CASE
    )

    /** Küçük harf + NFKC; noktalama boşluğa dönüşür, kelime sınırları korunur (tokenizasyon için). */
    fun tokenNormalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        // Resmî alias yazımları: `kiss×sis` ↔ `Kiss x Sis`, `&` ↔ `and`.
        .replace("×", " x ")
        .replace("&", " and ")
        .replace(nonWordCharacters, " ")
        .trim()

    /** Yapısal kelimeler ve çıplak sayılar atıldıktan sonra kalan ayırt edici kelimeler. */
    fun significantTokens(title: String): Set<String> = tokenNormalize(title)
        .split(' ')
        .asSequence()
        .filter { it.isNotBlank() }
        .filterNot { it in structuralTokens }
        .filterNot { tok -> tok.all { it.isDigit() } }
        .toSet()

    /** Görünen + İngilizce + özgün Japonca başlıklar (bkz. aşağıdaki not). */
    fun aliasesOf(entry: MediaEntry): List<String> =
        // titleJapanese ŞART: Bangumi satırlarında görünen başlık çoğu zaman Çince (name_cn),
        // İngilizce başlık da çoğu zaman yoktur; satırın taşıdığı tek ortak başlık özgün
        // Japonca addır. O dahil edilmediğinde MAL/AniList kimlik doğrulaması Bangumi
        // kökenli kayıtlarda neredeyse her zaman düşüyor ve canlı yayılım sessizce atlanıyordu.
        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Cross-sync writes require two known, positive, exactly matching release years. */
    fun yearsCompatible(a: Int?, b: Int?): Boolean = a != null && a > 0 && b != null && b > 0 && a == b

    /**
     * Cluster-level year compatibility: an unknown year (`null` / `<= 0`) on one provider (e.g.
     * Simkl or Kitsu entries without air year) or a ±1 calendar-year boundary (Winter anime / JP-CN
     * release date split) is NOT a contradiction; only two known years > 1 apart conflict.
     */
    fun clusterYearsCompatible(a: Int?, b: Int?): Boolean =
        a == null || a <= 0 || b == null || b <= 0 || kotlin.math.abs(a - b) <= 1

    /** A catalog result from another MAL media namespace can never authorize a write. */
    fun mediaTypesCompatible(local: MediaType, remoteType: String?): Boolean {
        val normalized = remoteType?.lowercase(Locale.ROOT)?.filter { it.isLetterOrDigit() } ?: return false
        return when (local) {
            MediaType.Manga -> normalized in setOf("manga", "manhwa", "manhua", "oneshot", "doujinshi", "novel", "lightnovel")
            MediaType.Anime -> normalized in setOf("tv", "movie", "ova", "ona", "special", "music", "pv", "cm", "anime")
            MediaType.Movie -> normalized == "movie"
            MediaType.TvShow -> normalized in setOf("tv", "series")
        }
    }

    /**
     * Güçlü akrabalık: normalize başlıklar eşit, biri diğerinin ön eki ya da (yeterince uzunsa) içinde.
     * CJK başlıklarda boşluk olmadığı için kelime bazlı karşılaştırma çalışmaz; bu kontrol onu da kapsar.
     */
    fun titlesStronglyRelated(a: String, b: String): Boolean {
        val na = MediaIdentity.normalizedTitle(a)
        val nb = MediaIdentity.normalizedTitle(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        val shorter = if (na.length <= nb.length) na else nb
        val longer = if (na.length <= nb.length) nb else na
        // CJK başlıklar çok kısa olabilir (ör. 鬼父); Latin alfabesinde en az 3, CJK'da en az 2 karakter aranır.
        val isCjk = shorter.any { it.code >= 0x2E80 }
        val minPrefixLength = if (isCjk) 2 else 3
        val minContainsLength = if (isCjk) 3 else 5
        if (shorter.length < minPrefixLength) return false
        return longer.startsWith(shorter) || (shorter.length >= minContainsLength && longer.contains(shorter))
    }

    /** Zayıf akrabalık: en az bir ayırt edici kelime ortak (ör. "Oni Chichi" ↔ "Oni Chichi: Re-born"). */
    fun titlesShareDistinctiveToken(a: String, b: String): Boolean {
        val ta = significantTokens(a)
        val tb = significantTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        return ta.any { it in tb }
    }

    /**
     * İki alias kümesi akraba mı? [strict] = true ise yalnızca güçlü akrabalık kabul edilir
     * (yıl bilinmediğinde kullanılır: kelime ortaklığı tek başına yeterli kanıt değildir).
     */
    fun titlesLookRelated(a: Collection<String>, b: Collection<String>, strict: Boolean = false): Boolean {
        val left = a.filter { it.isNotBlank() }
        val right = b.filter { it.isNotBlank() }
        if (left.isEmpty() || right.isEmpty()) return false
        for (x in left) for (y in right) {
            if (titlesStronglyRelated(x, y)) return true
        }
        if (strict) return false
        for (x in left) for (y in right) {
            if (titlesShareDistinctiveToken(x, y)) return true
        }
        // Gevşek ama güvenli varyant kontrolü (yalnızca yıllar biliniyorken): çeviri/romanizasyon farkları
        // (Mayoiga ↔ Mayohiga, Onee-san ↔ Oneesan), sayısal başlığın İngilizce biçimi (86 ↔ 86: Eighty Six)
        // ve kelime eklemesi (86 Part 2 ↔ 86: Eighty Six Part 2). Sezon/bölüm numaraları birebir aynı olmalı.
        for (x in left) for (y in right) {
            if (titlesVariantRelated(x, y)) return true
        }
        return false
    }

    private fun rawTokens(title: String): List<String> =
        tokenNormalize(title).split(' ').filter { it.isNotBlank() }

    private val digitRunRegex = Regex("\\d+")

    /** Sayısal belirteçler ("Part 2", "2nd Season", "8-gou"): sıra ekli sayılar dahil tüm rakam dizileri. */
    private fun numericTokens(title: String): Set<String> =
        digitRunRegex.findAll(tokenNormalize(title)).map { it.value }.toSet()

    /** Yapısal kelimeler atılmış, boşluksuz karşılaştırma anahtarı; Hepburn romanizasyon farklarını katlar. */
    private fun compactKey(title: String): String = rawTokens(title)
        .filterNot { it in structuralTokens }
        .map { if (it == "kyuu") "q" else it }
        .joinToString("")
        .replace("ou", "o")
        .replace("oo", "o")
        .replace("uu", "u")
        .replace("ei", "e")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    /** Yalnızca varyant/çeviri farkı olan iki başlık (sayısal kimlikler aynı olmak zorunda). */
    fun titlesVariantRelated(a: String, b: String): Boolean {
        if (numericTokens(a) != numericTokens(b)) return false
        val ka = compactKey(a)
        val kb = compactKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true

        // (1) Sayısal başlık + İngilizce açılımı: "86" ↔ "86 Eighty Six" (tüm kelimeler sırayla bulunmalı)
        val ta = rawTokens(a)
        val tb = rawTokens(b)
        val (shortT, longT) = if (ta.size <= tb.size) ta to tb else tb to ta
        if (shortT.isNotEmpty()) {
            var idx = 0
            for (tok in longT) {
                if (idx < shortT.size && tok == shortT[idx]) idx++
            }
            if (idx == shortT.size && (longT.size - shortT.size) <= 4) return true
        }

        // (2) Kısa romanizasyon/yazım farkı: en fazla 1 karakter ve uzunluğun %15'i kadar fark
        if (ka.length >= 6 && kb.length >= 6) {
            val dist = levenshtein(ka, kb)
            if (dist <= 1 || (dist <= 2 && dist.toDouble() / maxOf(ka.length, kb.length) <= 0.15)) return true
        }
        return false
    }

    /**
     * Cross-sync yazma/cluster kararı için katı başlık eşdeğerliği. Tek bir ortak kelime veya genel
     * prefix yeterli değildir; numaralı sezonlar/remake'ler de sayısal belirteçleri aynı olmalıdır.
     */
    fun titlesAreIdentityEquivalent(a: String, b: String): Boolean {
        val na = MediaIdentity.normalizedTitle(a)
        val nb = MediaIdentity.normalizedTitle(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        if (isKnown86TitleExpansion(na, nb)) return true
        if (numericTokens(a) != numericTokens(b)) return false

        // Keep every word, including articles and season/movie descriptors. Removing common
        // tokens can collapse distinct titles ("No Game No Life" vs "Game Life").
        // The only special spelling normalization is the audited Mayoiga/Mayohiga alias.
        fun strictCompactKey(value: String): String = rawTokens(trailingMediaTypeQualifier.replace(value, ""))
            .map {
                when (it) {
                    "mayohiga" -> "mayoiga"
                    "kyuu" -> "q"
                    else -> it
                }
            }
            .joinToString("")

        val ka = strictCompactKey(a)
        val kb = strictCompactKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        // One explicitly narrow romaji variant: an inserted intervocalic "h" (Mayoiga/Mayohiga).
        // General edit-distance matches are intentionally rejected for cross-account identity.
        val intervocalicH = Regex("(?<=[aeiou])h(?=[aeiou])")
        return intervocalicH.replace(ka, "") == intervocalicH.replace(kb, "")
    }

    private fun isKnown86TitleExpansion(a: String, b: String): Boolean {
        val marker = "86eightysix"
        fun collapseKnownExpansion(value: String): String =
            if (value.startsWith(marker)) "86${value.removePrefix(marker)}" else value
        return (a.startsWith(marker) || b.startsWith(marker)) && collapseKnownExpansion(a) == collapseKnownExpansion(b)
    }

    fun matchingLocalTitleAliases(localTitles: Collection<String>, remoteTitles: Collection<String>): List<String> {
        val remote = remoteTitles.filter { it.isNotBlank() }
        return localTitles.filter { local ->
            local.isNotBlank() && remote.any { title -> titlesAreIdentityEquivalent(local, title) }
        }.distinct()
    }

    fun titlesAreIdentityEquivalent(a: Collection<String>, b: Collection<String>): Boolean =
        matchingLocalTitleAliases(a, b).isNotEmpty()

    /** Same-ID cluster relation; unlike the UI/detail heuristic this never accepts one shared word. */
    fun entriesLookRelated(a: MediaEntry, b: MediaEntry): Boolean =
        titlesAreIdentityEquivalent(aliasesOf(a), aliasesOf(b))

    /**
     * Evaluates whether [incoming] can join an existing cluster ([existingCandidates]) without
     * triggering `identityReviewRequired`.
     *
     * Key invariant (`E97`):
     * - Previously, `clusterEntry` compared `incoming` against each candidate pairwise using only
     *   `title` + `titleEnglish` and strict non-null `yearsCompatible`. For `Kaiju No. 8`
     *   (`MAL 52588`), AniList carries `("Kaijuu 8-gou", "Kaiju No. 8", "怪獣８号")`, Simkl carries
     *   `("Kaiju No. 8")` only, Shikimori carries `("Kaijuu 8-gou")` only, and Bangumi carries
     *   `("怪獣8号")` only. Comparing Shikimori or Bangumi pairwise against Simkl ignored AniList's
     *   bridge inside the very same cluster and poisoned the entire group as `identityReviewRequired`.
     * - A cluster's combined alias pool (`existingCandidates.flatMap(::aliasesOf)`) represents the
     *   known names of that single production. An incoming row sharing a provider ID is accepted when
     *   its years do not conflict (`clusterYearsCompatible`) AND at least one alias connects to the
     *   cluster's alias pool via `titlesAreIdentityEquivalent` or `titlesVariantRelated` (which still
     *   rejects unrelated titles like `"Koi wo Shita no wa"` vs `"Koe no Katachi Specials"` and
     *   mismatched season numbers like `"Re:Zero Part 2"` vs `"Re:Zero Part 3"`).
     *
     * @return empty list when safe to merge; otherwise the conflicting candidates for diagnostics.
     */
    fun findSuspiciousSharedIdCandidates(
        existingCandidates: List<MediaEntry>,
        incoming: MediaEntry,
        sharedIdCandidates: List<MediaEntry>
    ): List<MediaEntry> {
        val yearConflicts = existingCandidates.filter { candidate ->
            !clusterYearsCompatible(candidate.year, incoming.year)
        }
        if (yearConflicts.isNotEmpty()) return yearConflicts

        if (sharedIdCandidates.isEmpty()) return emptyList()

        val clusterAliases = existingCandidates.flatMap(::aliasesOf)
        val incomingAliases = aliasesOf(incoming)
        if (clusterAliases.isEmpty() || incomingAliases.isEmpty()) return emptyList()

        val matchesClusterPool = titlesAreIdentityEquivalent(clusterAliases, incomingAliases) ||
            clusterAliases.any { ca -> incomingAliases.any { ia -> titlesVariantRelated(ca, ia) } }
        if (matchesClusterPool) return emptyList()

        // Cross-script crosswalk bridge (e.g. Bangumi Simplified Chinese `"怪兽8号"` paired with
        // Shikimori/MAL/AniList Latin `"Kaijuu 8-gou"` via verified `bgm_a_<malId>` crosswalk):
        // Simplified Chinese shares zero characters with romaji/English, whereas two poisoned rows
        // in Latin script (`"Kaijuu 8-gou"` vs `"Unrelated Mystery Show"`) both have Latin letters.
        val incomingHasLatin = incomingAliases.any(::hasLatinLetters)
        val clusterHasLatin = clusterAliases.any(::hasLatinLetters)
        if (incomingHasLatin != clusterHasLatin) {
            val episodeConflict = existingCandidates.any { candidate ->
                candidate.total != null && incoming.total != null &&
                    candidate.total > 0 && incoming.total > 0 && candidate.total != incoming.total
            }
            if (!episodeConflict) return emptyList()
        }

        return sharedIdCandidates
    }

    private fun hasLatinLetters(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }

    /**
     * Bulk cross-sync (`AuthViewModel.syncPlatforms`) addition gate (`E96`):
     * matches single-entry live sync (`ExternalListSyncManager`) — only a catalog contradiction
     * ([Verdict.Rejected]) blocks additions, whereas native/multi-provider/verified/uncontradicted
     * entries proceed to each target platform's own official ID/title verification.
     */
    fun allowsBulkAddition(verdict: Verdict?): Boolean =
        verdict == null || !blocksCrossWrite(verdict)

    // ───────────────────────────── MAL kimlik doğrulama ─────────────────────────────

    private val identityCache = BoundedCache<String, RemoteIdentity>("crossSync.identity.officialMal.v2", 500)

    /**
     * Grubun MAL kimliğini kataloğa karşı doğrular. Ağ hatalarında istisna fırlatmaz; [Verdict.Unverifiable] döner.
     *
     * @param localTitles  gruptaki tüm kayıtların başlık/alias'ları
     * @param localYear    grupta bilinen yıl (varsa)
     */
    suspend fun verifyMalId(
        malId: Int,
        mediaType: MediaType,
        localTitles: Collection<String>,
        localYear: Int?
    ): Verdict {
        val titles = localTitles.filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_no_local_title))

        val remote = fetchRemoteIdentity(malId, mediaType)
            ?: return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_remote_title_unavailable, malId))
        if (remote.titles.isEmpty()) return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_remote_empty_title, malId))

        val typeOk = mediaTypesCompatible(mediaType, remote.mediaType)
        val yearOk = yearsCompatible(localYear, remote.year)
        // Yılın taraflardan birinde bilinmediği durum (yazmayı engellemeyelim).
        val yearUnknown = (localYear == null || localYear <= 0) || (remote.year == null || remote.year <= 0)
        // AniList "sezon yılı" ile MAL "başlangıç yılı" zaman zaman 1 yıl kayar
        // (Aralık/Ocak ayında başlayan yapımlar). Bariz fark (≥2 yıl) gerçekten farklı
        // içeriktir; 1 yıllık kayma kimlik ayrımı değildir.
        val yearNear = localYear != null && remote.year != null &&
            localYear > 0 && remote.year > 0 &&
            kotlin.math.abs(localYear - remote.year) <= 1
        val matchedLocalTitles = matchingLocalTitleAliases(titles, remote.titles)
        val related = matchedLocalTitles.isNotEmpty()
        // Tam (birebir) başlık eşdeğerliği + aynı MAL türü, yıl bilgisinden BAĞIMSIZ kanıttır:
        // aynı türdeki iki kayıt aynı normalize başlığı taşıyorsa bu, yanlış yapıma yazma
        // riskini doğurmaz. Yıl farkı çoğu zaman yayın/yayın-başlangıcı ya da sezon (TV ↔ film)
        // farkından gelir; eskiden bu yüzden `Unverifiable` dönüyor ve çapraz yayılımın TAMAMI
        // sessizce atlanıyordu ("MAL'a ekledim, hiçbir yere gitmiyor").
        val identityEquivalent = titlesAreIdentityEquivalent(titles, remote.titles)

        return when {
            !typeOk -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_type_mismatch,
                    malId,
                    remote.mediaType ?: "unknown",
                    mediaType.name,
                    remote.primaryTitle
                ),
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
            identityEquivalent -> Verdict.Verified(remote.primaryTitle, remote.year, matchedLocalTitles)
            // Başlık eşleşiyorsa yıl BİLİNMİYOR olması yazmayı engellememeli.
            // ESKİ DAVRANIŞ: `yearOk` iki tarafın da yılı bilmesini ve BİREBİR eşit olmasını
            // istiyordu; yerel yılı boş olan (Simkl/TMDB/manuel eklenen) veya MAL'ın başlangıç
            // yılı AniList sezon yılından bir farklı olan kayıtlar "Unverifiable" dönüp
            // çapraz yazımı (AniList dahil) TAMAMEN atlıyordu → kullanıcı "listeye ekleme/
            // çıkarma çalışmıyor" görüyordu. Kimliğin asıl kanıtı MAL kimliğidir; başlık
            // eşleşmesi de doğrulandıysa yıl bilinmiyorsa/yakınsa yazmaya izin verilir.
            related && (yearOk || yearUnknown || yearNear) ->
                Verdict.Verified(remote.primaryTitle, remote.year, matchedLocalTitles)
            !related -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_title_mismatch,
                    malId,
                    remote.primaryTitle,
                    remote.year?.let { KitsugiStrings.get(R.string.cross_sync_identity_year_suffix, it) }.orEmpty(),
                    titles.first()
                ),
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
            // Yalnızca ZAYIF başlık akrabalığı var ve yıllar çelişiyor → gerçek çelişki.
            else -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_year_mismatch,
                    malId,
                    remote.primaryTitle,
                    remote.year ?: 0,
                    localYear ?: 0
                ),
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
        }
    }

    /**
     * Kararın çapraz yazımı **engelleyip engellemediği**.
     *
     * Eski kural `Verified` dışındaki her sonucu engelliyordu. Bu, ağ/kota nedeniyle katalog
     * okunamadığında ([Unverifiable]) veya yerel satırda yıl bulunmadığında **hiçbir** kaynağa
     * yazılmaması demekti — kullanıcı "MAL'a ekledim, AniList/Kitsu/Shikimori/Bangumi'ye hiç
     * gitmiyor" diyordu. Doğru ayrım: yalnızca katalog **çelişki** gösterdiğinde
     * ([Rejected]) yazım durur; kanıt yokluğu ([Unverifiable]) hedefin KENDİ resmî
     * kimliğiyle doğrulamasına bırakılır (AniList `idMal`, Kitsu `mappings`,
     * Simkl `ids.mal`, Shikimori `target_id` = MAL ID).
     */
    fun blocksCrossWrite(verdict: Verdict): Boolean = verdict is Verdict.Rejected

    /** Test/teşhis amaçlı: önbelleği boşaltır. */
    fun clearCache() = identityCache.clear()

    private suspend fun fetchRemoteIdentity(malId: Int, mediaType: MediaType): RemoteIdentity? {
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        val key = "$endpoint:$malId"
        identityCache[key]?.let { return it }

        // Cross-account writes require the authoritative MAL catalog. Jikan is a useful metadata
        // mirror, but it is not strong enough to authorize an account mutation when MAL is down or
        // its client configuration is missing. Fail closed instead of substituting a second source.
        val fetched = fetchFromOfficialMal(malId, endpoint)
            ?.takeIf { it.titles.isNotEmpty() && !it.mediaType.isNullOrBlank() }
        // Cache successful official responses only; transient failures may be retried later.
        if (fetched != null) identityCache[key] = fetched
        return fetched
    }

    private suspend fun fetchFromOfficialMal(malId: Int, endpoint: String): RemoteIdentity? {
        val clientId = runCatching { com.kitsugi.animelist.BuildConfig.MAL_CLIENT_ID }.getOrNull()
        if (clientId.isNullOrBlank()) return null
        PlatformRateLimiter.acquire("mal")
        val url = "https://api.myanimelist.net/v2/$endpoint/$malId?fields=id,title,alternative_titles,start_date,media_type"
        val request = Request.Builder()
            .url(url)
            .header("X-MAL-CLIENT-ID", clientId)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.code == 429) PlatformRateLimiter.notifyRateLimited("mal")
                if (!response.isSuccessful) {
                    Log.w(TAG, "official MAL identity HTTP ${response.code} for $endpoint/$malId")
                    return null
                }
                val text = response.body?.string()
                if (text.isNullOrBlank()) return null
                val json = JSONObject(text)
                if (json.optInt("id", 0) != malId) {
                    Log.w(TAG, "official MAL returned a different ID than requested for $endpoint/$malId")
                    return null
                }
                val titles = mutableListOf<String>()
                json.optString("title").takeIf { it.isNotBlank() }?.let { titles += it }
                json.optJSONObject("alternative_titles")?.let { alt ->
                    alt.optString("en").takeIf { it.isNotBlank() }?.let { titles += it }
                    alt.optString("ja").takeIf { it.isNotBlank() }?.let { titles += it }
                    alt.optJSONArray("synonyms")?.let { arr ->
                        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { titles += it }
                    }
                }
                RemoteIdentity(
                    malId = malId,
                    titles = titles.distinct(),
                    year = yearRegex.find(json.optString("start_date"))?.groupValues?.get(1)?.toIntOrNull(),
                    mediaType = json.optString("media_type").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "official MAL identity fetch failed for $endpoint/$malId: ${e.message}")
            null
        }
    }


}
