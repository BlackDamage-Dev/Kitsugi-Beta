package com.kitsugi.animelist.data.remote.anisub

import java.text.Normalizer
import java.util.Locale

/**
 * AniSub paket (ZIP/RAR) içinden ve AniSub kayıt meta verilerinden
 * doğru bölüm dosyasını seçmek için kullanılan saf (yan etkisiz) mantık.
 *
 * Bu sınıf Android bağımlılığı taşımaz; JVM birim testleriyle doğrulanır.
 *
 * Temel ilkeler:
 *  - Yanlış bölümü döndürmektense hiç döndürme. (Çok dosyalı pakette
 *    bölüm eşleşmezse ilk dosyaya asla düşülmez.)
 *  - Bilinmezlikte eleme yapma. (Meta veri bölüm hakkında hiçbir şey
 *    söylemiyorsa adayı tamamen dışlama; kararı Dosya adına bırak.)
 */
object AniSubEpisodeMatcher {

    /** Dosya adından çıkarılabilen sezon+bölüm bilgisi. */
    data class EpisodeRef(val season: Int?, val episode: Int)

    private val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt", "sub", "ttml", "dfxp")

    /** Tercih sırası: ASS (fansub standardı) > SSA > SRT > VTT > diğerleri. */
    private val EXTENSION_PRIORITY = mapOf("ass" to 0, "ssa" to 1, "srt" to 2, "vtt" to 3)

    fun subtitleExtension(fileName: String): String? {
        val clean = fileName.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext.takeIf { it in SUBTITLE_EXTENSIONS }
    }

    fun isSubtitleFileName(fileName: String): Boolean = subtitleExtension(fileName) != null

    /**
     * Dosya adından bölüm numarasını çıkarır.
     *
     * Bilinen bütün fansub/release kalıpları öncelik sırasıyla denenir:
     * SxxEyy, NxNN, "Ep 05", "Bölüm 05", "- 05", "[05]", "(05)", tek başına sayı.
     * Başlıktaki sayılar (örn. "86 - Eighty Six - 05") yanlışlıkla bölüm
     * sanılmasın diye [mediaTitle] içindeki sayılar adaylardan ayıklanır;
     * başlıkta bir kez geçen sayı Dosya adında ikinci kez geçerse ancak
     * o zaman bölüm adayı sayılır (örn. "Re:Zero ... - 05" değil ama
     * başlıktaki tek "0", orijinal mantık korunur).
     */
    fun extractEpisodeRef(fileName: String, mediaTitle: String? = null): EpisodeRef? {
        val nameWithoutExt = fileName.substringBeforeLast('.', fileName)
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .lowercase(Locale.ROOT)

        // Bölümle ilgisiz sayıları üretebilecek şablonları temizle.
        val cleaned = nameWithoutExt
            .replace(Regex("""\[[0-9a-f]{6,10}]"""), " ")            // CRC hash [3CB81308]
            .replace(Regex("""\b\d{3,4}\s*x\s*\d{3,4}\b"""), " ")     // 1280x720, 1920x1080
            .replace(Regex("""\b(240|360|480|576|720|1080|1440|2160|4320)\s*p?\b"""), " ")
            .replace(Regex("""\b(?:h\.?|x)?26[45]\b"""), " ")          // h264, x265, h.265
            .replace(Regex("""\b(?:10|8)bit\b|\bhevc\b|\bhdr10\+?\b|\bdolby\b"""), " ")
            .replace(Regex("""\b(?:19\d{2}|20\d{2})\b"""), " ")        // yayın yılı
            .replace(Regex("""\b\d{2,3}[.,]\d{3}\b"""), " ")           // fps 23.976 vb.
            .replace(Regex("""\b\d{1,4}[.,]\d\b"""), " ")              // 12.5 gibi ara bölümler (tam bölüm değil)
            .replace(Regex("""\bv\d+\b(?=\s*(?:$|[)\]]))"""), " ")     // sona ekli v2 sürümü

        val titleNumbers = mediaTitle
            ?.lowercase(Locale.ROOT)
            ?.let { Regex("""\d+""").findAll(it).mapNotNull { m -> m.value.toIntOrNull() }.toList() }
            .orEmpty()
        val fileNumbers = Regex("""\d+""").findAll(cleaned)
            .mapNotNull { it.value.toIntOrNull() }.toList()

        fun isEpisodeCandidate(num: Int): Boolean {
            if (num !in titleNumbers) return true
            // Başlıktaki sayı ancak Dosya adında fazladan tekrar ediyorsa bölüm sayılır.
            return fileNumbers.count { it == num } > titleNumbers.count { it == num }
        }

        // Sayının hemen ardından ".5" geliyorsa o bir ara bölümdür; tam bölüm sayılmaz.
        val notHalfEpisode = """(?![.,]5\b)"""

        // NOT: "_S01E05_" gibi adlarda \b, alt çizgi de sözcük karakteri olduğu
        // için sınır oluşturmaz; bu yüzden "öncesi harf/rakam değil" şartı kullanılır.

        // 1) S01E05 / Season 1 Episode 5
        Regex("""(?:^|[^a-z0-9])s(?:eason)?\s*(\d{1,2})\s*e(?:pisode)?\s*(\d{1,4})$notHalfEpisode""")
            .find(cleaned)?.let { m ->
                val season = m.groupValues[1].toIntOrNull()
                val ep = m.groupValues[2].toIntOrNull()
                if (ep != null && isEpisodeCandidate(ep)) return EpisodeRef(season, ep)
            }

        // 2) 1x05 kalıbı
        Regex("""(?:^|[^a-z0-9])(\d{1,2})\s*x\s*(\d{1,4})$notHalfEpisode""")
            .find(cleaned)?.let { m ->
                val season = m.groupValues[1].toIntOrNull()
                val ep = m.groupValues[2].toIntOrNull()
                if (ep != null && isEpisodeCandidate(ep)) return EpisodeRef(season, ep)
            }

        // 3) Açık bölüm öneki: Ep 05, Episode 05, Bölüm 05, E05
        Regex("""(?:^|[^a-z])(?:ep(?:isode)?\.?|bölüm|bolum|e)\s*#?\s*(\d{1,4})$notHalfEpisode""")
            .findAll(cleaned).forEach { m ->
                val ep = m.groupValues[1].toIntOrNull()
                if (ep != null && isEpisodeCandidate(ep)) return EpisodeRef(null, ep)
            }

        // 4) Ayraçlı/köşeli sayılar: "- 05", "[05]", "(05)", "_05_"
        Regex("""[\[(]_?\s*(\d{1,4})\s*[\])]|[-_]\s+(\d{1,4})$notHalfEpisode|[-_](\d{2,4})[-_ ]""")
            .findAll(cleaned).forEach { m ->
                val ep = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                if (ep != null && isEpisodeCandidate(ep)) return EpisodeRef(null, ep)
            }

        // 5) Tek başına sayılar: başlığa ait olmayan sayılardan SONUNCUSU
        //    (fansub adlarında bölüm hemen her zaman son anlamlı sayıdır;
        //    örn. "Anime 2nd Season - 05" sonundaki 05 bölüm, 2 sezon bilgisidir).
        val standalone = fileNumbers.filter { isEpisodeCandidate(it) }
        val single = standalone.lastOrNull() ?: return null
        // Birden çok adayı olan isimlerde ilk aday genellikle sezon/part sayısı
        // olabileceğinden yalnızca son adayı kabul etmek daha güvenlidir.
        return EpisodeRef(null, single)
    }

    fun extractEpisode(fileName: String, mediaTitle: String? = null): Int? =
        extractEpisodeRef(fileName, mediaTitle)?.episode

    fun matchesEpisode(fileName: String, episode: Int, mediaTitle: String? = null): Boolean =
        extractEpisode(fileName, mediaTitle) == episode

    /**
     * Paket içindeki altyazı dosyaları arasından [episode] için doğru dosyayı seçer.
     *
     * @param fileNames    Paket içindeki altyazı Dosya adları (veya yolları)
     * @param episode      Hedeflenen bölüm; null ise (film/bilinmiyor) ilk uygun Dosya
     * @param mediaTitle   Başlıktaki sayıların ayıklanması için anime/film başlığı
     *
     * Kurallar:
     *  - Bölümle bire bir eşleşen Dosya varsa en iyisi (ASS önceliği + revizyon) döner.
     *  - Tek subtitle Dosyası varsa ve bir bölüm numarası TAŞIMIYORSA o döner
     *    (örn. film altyazısı). Taşıyorsa ve uyuşmuyorsa null döner.
     *  - Çok dosya varsa ve hiçbiri eşleşmiyorsa null döner; ASLA ilk dosyaya düşülmez.
     */
    fun pickEpisodeFile(
        fileNames: List<String>,
        episode: Int?,
        mediaTitle: String? = null
    ): String? {
        val subtitles = fileNames.filter { isSubtitleFileName(it) }
        if (subtitles.isEmpty()) return null

        if (episode == null) {
            return rank(subtitles).firstOrNull()
        }

        val matching = subtitles.filter { extractEpisode(it, mediaTitle) == episode }
        if (matching.isNotEmpty()) return rank(matching).firstOrNull()

        if (subtitles.size == 1) {
            val only = subtitles.first()
            val onlyEpisode = extractEpisode(only, mediaTitle)
            // Numarasız tek Dosya (film veya isimsiz tek bölüm) kabul edilir;
            // numaralı ama yanlış numaralı tek Dosya kabul edilmez.
            return if (onlyEpisode == null) only else null
        }

        // Birden çok Dosya var, hiçbiri bu bölüm için numara taşımıyorsa paket,
        // adlandırma şeması çözülemeyen bir pakettir — yine de tahmin yürütülmez.
        val allUnnumbered = subtitles.all { extractEpisode(it, mediaTitle) == null }
        if (allUnnumbered) {
            // Numarasız çoklu pakette (örn. isim yerine bölüm başlığı kullanılmış)
            // yanlış dosyayı vermektense hiç vermemek doğrudur; yalnızca
            // bölüm sayısı hedef bölümle dizisel olarak örtüşebiliyorsa
            // sıralı (n-1). dosyayı almak bazı paketlerde doğru olur ancak
            // bu tahmin riskli olduğundan burada da null dönülür.
            return null
        }
        return null
    }

    /** Aynı bölümün birden çok varyantı arasında tercih sırası uygular. */
    fun rank(fileNames: List<String>): List<String> {
        return fileNames.sortedWith(
            compareBy<String> { if (isSecondaryTrack(it)) 1 else 0 }
                .thenBy { EXTENSION_PRIORITY[subtitleExtension(it)] ?: 9 }
                .thenByDescending { revisionOf(it) }
                .thenBy { it.length }
        )
    }

    /** "Signs"/"forced"/"commentary" benzeri yan parçaları ana diyalogdan ayırır. */
    private fun isSecondaryTrack(fileName: String): Boolean {
        val n = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        return n.contains("signs") || n.contains("forced") ||
            n.contains("commentary") || n.contains("song") || n.contains("songs")
    }

    /** "v2", "v3", "rev2" gibi revizyon ipuçlarından revizyon sayısını çıkarır. */
    private fun revisionOf(fileName: String): Int {
        val m = Regex("""(?:^|[^a-z])(?:v|rev|version)\s*(\d+)""")
            .find(fileName.lowercase(Locale.ROOT)) ?: return 1
        return m.groupValues[1].toIntOrNull() ?: 1
    }

    /**
     * AniSub kayıt meta verisinin (content_type / episode_description /
     * release adı) hedef bölümle uyumlu olup olmadığına karar verir.
     *
     * - "Paket"/"batch" kayıtları her zaman uyumlu (bölüm, içindeki dosyada çözülür).
     * - "Bölüm: 12" yalnızca 12. bölümle uyumlu.
     * - "1-13" aralığı, bölüm aralıkta ise uyumlu.
     * - Hiçbir ipucu yoksa bilinmezlik sebebiyle uyumlu sayılır (yanlış eleme yapma).
     */
    fun isReleaseCompatibleWithEpisode(
        contentType: String?,
        episodeDescription: String?,
        releaseName: String?,
        episode: Int?,
        season: Int? = null
    ): Boolean {
        if (episode == null) return true

        val ct = contentType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (ct.contains("paket") || ct.contains("batch") || ct.contains("komple") ||
            ct.contains("complete") || ct.contains("film") || ct.contains("movie")
        ) {
            return true
        }

        // content_type kesin bölüm söylüyorsa ("Bölüm: 12") o kesindir.
        parseSingleEpisode(contentType)?.let { return it == episode }
        parseEpisodeRange(contentType)?.let { return episode in it }

        // episode_description da aynı şekilde kesindir ("1-3", "Bölüm 12").
        parseEpisodeRange(episodeDescription)?.let { return episode in it }
        parseSingleEpisode(episodeDescription)?.let { return it == episode }

        // Release adındaki SxxEyy hem sezon hem bölüm için kesindir.
        if (!releaseName.isNullOrBlank()) {
            val ref = extractEpisodeRef(releaseName, mediaTitle = null)
            if (ref != null) {
                if (ref.episode != episode) {
                    // Ad tek bölümlük release'e benzemiyorsa (aralık/paket) eleme yapma.
                    if (parseEpisodeRange(releaseName) == null && !looksLikePackage(releaseName)) {
                        return false
                    }
                } else if (season != null && ref.season != null && ref.season != season) {
                    return false
                }
            }
            parseEpisodeRange(releaseName)?.let { return episode in it }
        }
        return true
    }

    fun looksLikePackage(text: String?): Boolean {
        val t = text?.lowercase(Locale.ROOT) ?: return false
        return t.contains("paket") || t.contains("batch") || t.contains("complete") ||
            t.contains("komple") || t.contains("tüm") || t.contains("tum bolum") ||
            t.contains("tüm bölümler") || parseEpisodeRange(t) != null
    }

    /** "1-13", "01~13", "1 - 135" biçimindeki aralıkları yakalar. */
    fun parseEpisodeRange(text: String?): IntRange? {
        if (text.isNullOrBlank()) return null
        val m = Regex("""(?:^|\D)(\d{1,4})\s*[-~–]\s*(\d{1,4})(?:\D|$)""")
            .find(text) ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[2].toIntOrNull() ?: return null
        // Yıl aralıkları (2013-2024) bölüm aralığı değildir.
        if (start >= 1900 || end >= 1900) return null
        if (end < start) return null
        return start..end
    }

    /** "Bölüm: 12", "Bölüm 12", "Ep 12", "E12" biçimindeki tekil bölümü yakalar. */
    fun parseSingleEpisode(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        Regex("""(?:bölüm|bolum|ep(?:isode)?)\s*:?\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE)
            .find(t)?.let { return it.groupValues[1].toIntOrNull() }
        // Metnin tamamı sadece sayı ise o da tekil bölümdür.
        return t.toIntOrNull()
    }

    // ------------------------------------------------------------------
    // Başlık eşleştirme
    // ------------------------------------------------------------------

    /** Başlığı karşılaştırılabilir forma indirir: küçük harf, aksansız, alfanumerik + boşluk. */
    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return season_noise(decomposed.lowercase(Locale.ROOT))
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    /**
     * Türkçe harfleri sadeleştirir. "İ" Locale.ROOT ile küçük harfe çevrilince
     * i + birleşik nokta (U+0307) oluşur; bu işaret başlıkta fazladan boşluk
     * olarak görünmeden önce temizlenir.
     */
    private fun season_noise(value: String): String =
        value.replace("\u0307", "")
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

    /**
     * Kullanıcının oynattığı başlığın AniSub kaydındaki romaji/İngilizce
     * başlıklarla aynı yapımı gösterip göstermediğine karar verir.
     *
     * Kesin kimlik (AniList ID) varken bu fonksiyon kullanılmaz; yalnızca
     * kimlik yokken bulanık yedeği oluşturur. Bu yüzden eşik yüksek tutulur:
     * yanlış animeye ait altyazıyı göstermek, hiç göstermemekten kötüdür.
     */
    fun titleMatches(query: String?, vararg candidates: String?): Boolean {
        val q = normalizeTitle(query)
        if (q.isEmpty()) return false
        val qTokens = q.split(' ').filter { it.isNotEmpty() }.toSet()
        if (qTokens.isEmpty()) return false

        return candidates.filterNotNull().any { candidate ->
            val c = normalizeTitle(candidate)
            if (c.isEmpty()) return@any false
            if (c == q) return@any true
            val cTokens = c.split(' ').filter { it.isNotEmpty() }.toSet()
            if (cTokens.isEmpty()) return@any false
            // Tam token kümelerinden biri diğerini içeriyorsa aynı yapım:
            // "Shingeki no Kyojin" <-> "Shingeki no Kyojin Season 2"
            // durumunda alt küme ilişkisi olsa bile sezonun ayrışması
            // AniList ID/sezon kontrolüne bırakılır; burada yalnızca
            // küçük kümenin tamamı büyükte ve fazladan sözcük az ise kabul.
            val intersection = qTokens.intersect(cTokens).size
            val union = qTokens.union(cTokens).size
            val jaccard = if (union == 0) 0.0 else intersection.toDouble() / union
            val coverage = intersection.toDouble() / minOf(qTokens.size, cTokens.size)
            jaccard >= 0.6 || (coverage >= 0.99 && union - intersection <= 2)
        }
    }

    /**
     * Arama sorgusu varyantları üretir. İlk sorguda AniList eşleşmesi
     * bulunamazsa daha kısa/ana başlıkla tekrar denenir:
     * "Başlık: The Final Season - Kanketsu-hen" -> "Başlık"
     */
    fun searchQueryVariants(title: String?): List<String> {
        val raw = title?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val variants = LinkedHashSet<String>()
        variants += raw
        raw.substringBefore(':').trim().takeIf { it.length >= 3 }?.let { variants += it }
        val withoutSeason = raw
            .replace(Regex("""(?i)\bseason\s*\d+\b"""), "")
            .replace(Regex("""(?i)\bpart\s*\d+\b"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '-', ':')
        if (withoutSeason.length >= 3) variants += withoutSeason
        return variants.toList()
    }
}
