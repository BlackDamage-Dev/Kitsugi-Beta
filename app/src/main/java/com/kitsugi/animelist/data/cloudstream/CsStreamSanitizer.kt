package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.kitsugi.animelist.data.cloudstream.diag.CsTrace
import com.kitsugi.animelist.data.repository.StreamSource
import java.util.Locale

/**
 * CS3 akış listesi temizleyici.
 *
 * NEDEN VAR (2026-10-12 kullanıcı raporu):
 *  1. Dizipal eklentisi site içi bahis/reklam videosunu (`diziroom.com/kralbet.mp4`) gerçek
 *     bölüm kaynağı gibi döndürüyordu; oynatıcıda reklam oynuyordu.
 *  2. Watch2Movies (vmp4.online) akışları HLS **parça** dosyaları (`seg-1-v1-a1.ts`,
 *     `seg-2-v1-a1.ts`) olarak ayrı "kaynak" kartları hâlinde listeleniyordu. Parça URL'si
 *     tek başına oynatılamaz (bölümün birkaç saniyesi) ve kullanıcı "yanlış sezon/bölüm
 *     oynuyor" görüyordu.
 *
 * Kural: burada YALNIZCA kanıtı açık olan girdiler elenir; emin olunmayan URL'ye dokunulmaz.
 */
object CsStreamSanitizer {

    private const val TAG = "CsStreamSanitizer"

    /** Reklam/bahis/çöp medya markası işaretleri (host etiketi veya dosya adı). */
    private val AD_BRANDS = listOf(
        "kralbet", "mostbet", "1xbet", "onexbet", "betcio", "matbet", "aresbet", "romabet",
        "vdcasino", "casinomaxi", "betboo", "bilyoner", "nesine", "tipobet", "holiganbet",
        "sahabet", "betasus", "anadolubet", "melbet", "betwinner", "pinbahis", "truvabet",
        "marsbahis", "betpalace", "celtabet", "hiltonbet", "jokerbet", "lidyabet", "modabet",
        "mrbet", "perabet", "rokobet", "sekabet", "supertotobet", "tempobet", "timebet",
        "tulipbet", "turbobet", "vaycasino", "vestibet", "yakabet", "youwin", "zirvebet"
    )

    /**
     * Reklam içerik işaretleri — YALNIZCA URL üzerinde aranır.
     *
     * Kasıtlı olarak dar tutuldu: "casino", "slot", "bahis" gibi genel kelimeler gerçek içerik
     * adlarında geçebilir (ör. "Casino" filmi). Bu yüzden yalnızca reklam olduğu açık olan
     * kalıplar listelenir.
     */
    private val AD_URL_TOKENS = listOf(
        "reklam", "advert", "preroll", "pre-roll", "midroll", "mid-roll",
        "deneme-bonusu", "bonus-veren", "advertorial", "/ads/", "sponsor-"
    )

    /** HLS/DASH parça (fragment) uzantıları — tek başına oynatılabilir akış DEĞİL. */
    private val FRAGMENT_EXTENSIONS = listOf(".ts", ".m4s", ".cmfv", ".cmfa", ".aac", ".vtt", ".key")

    /**
     * Bozuk/çift şema önekleri. Eklentilerin `substringAfter("https")` gibi kısayolları
     * `httpshttp://…` üretiyor; böyle bir adres oynatıcıya verildiğinde ExoPlayer şemayı
     * çözemeyip anında hata veriyor ("kaynak var ama oynatmıyor" şikâyetinin bir kolu).
     */
    private val BROKEN_SCHEME_PREFIXES = listOf("httpshttp://", "httpshttps://", "httphttps://")

    /** Adresin herhangi bir yerinde çift şema kalıntısı. */
    private val BROKEN_SCHEME_REGEX = Regex("""https?https?://""", RegexOption.IGNORE_CASE)

    /** Parça adı kalıpları: seg-1, segment01, chunk3, frag_00042, 00042.ts ... */
    private val FRAGMENT_NAME_REGEX = Regex(
        """(^|[^a-z0-9])(seg|segment|chunk|frag|part|fragment|slice)[-_]?\d{1,6}([^a-z0-9]|$)""",
        RegexOption.IGNORE_CASE
    )

    /** Yalnızca sayıdan oluşan parça dosyaları: 1234567.ts (HLS segmentleri böyledir). */
    private val NUMERIC_FRAGMENT_REGEX = Regex("""^\d{3,}(-\d+)?$""")

    /**
     * URL, oynatıcıya verildiğinde **kesin** hata üretecek kadar bozuk mu?
     *
     * Kapsam: çift/bozuk şema, şemasız adres, host'u çözümlenemeyen adres.
     * Kasıtlı olarak dar tutuldu — yalnızca kanıtı açık olanlar elenir, çünkü çalışan
     * bir kaynağı yanlışlıkla elemek kullanıcı için daha büyük bir kayıptır.
     */
    fun isBrokenStreamUrl(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return true
        val lower = raw.lowercase(Locale.ROOT)
        if (BROKEN_SCHEME_PREFIXES.any { lower.startsWith(it) }) return true
        if (BROKEN_SCHEME_REGEX.containsMatchIn(lower)) return true
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return true
        return try {
            java.net.URI(raw).host.isNullOrBlank()
        } catch (_: Exception) {
            true
        }
    }

    private fun hostOf(url: String): String = try {
        java.net.URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun pathOf(url: String): String = try {
        java.net.URI(url.lowercase(Locale.ROOT)).path.orEmpty()
    } catch (_: Exception) {
        url.lowercase(Locale.ROOT).substringBefore('?')
    }

    private fun fileNameOf(path: String): String =
        path.substringAfterLast('/').substringBefore('?')

    /**
     * URL/ad, bilinen bahis-reklam içeriğine mi işaret ediyor?
     *
     * Kanıt yoksa false döner — çalışan bir kaynağı reklam diye elemek daha büyük bir hatadır.
     */
    fun isLikelyAdStream(name: String?, url: String?): Boolean {
        val urlLower = url?.lowercase(Locale.ROOT).orEmpty()
        val urlEvidence = buildString {
            if (urlLower.isNotBlank()) {
                append(urlLower)
                append(' ')
                append(hostOf(urlLower))
                append(' ')
                append(fileNameOf(pathOf(urlLower)))
            }
        }
        // 1) Bahis/reklam markası: hem URL hem görünen ad üzerinden (marka adı gerçek içerik adı olamaz)
        val brandHaystack = "$urlEvidence ${name?.lowercase(Locale.ROOT).orEmpty()}"
        if (AD_BRANDS.any { brand -> brandHaystack.contains(brand) }) return true
        // 2) Açık reklam kalıpları yalnızca URL'de aranır (görünen ad gerçek içerik adı olabilir)
        return urlEvidence.isNotBlank() && AD_URL_TOKENS.any { token -> urlEvidence.contains(token) }
    }

    /**
     * URL bir HLS/DASH **parçası** mı? (tek başına bölüm oynatılamaz)
     *
     * Kanıt: parça uzantısı + parça adı kalıbı (seg-1, chunk02, 00042). Yalnızca uzantıya
     * bakmak yanlış olurdu; bazı CDN'ler bütün bölümü tek `.ts` dosyası olarak sunar
     * ("film.ts" gibi) ve o dosya oynatılabilir.
     */
    fun isHlsFragment(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = pathOf(url)
        if (FRAGMENT_EXTENSIONS.none { path.endsWith(it) }) return false

        val base = fileNameOf(path)
        val stem = base.substringBeforeLast('.')
        if (FRAGMENT_NAME_REGEX.containsMatchIn(base)) return true
        if (NUMERIC_FRAGMENT_REGEX.matches(stem)) return true
        // Saf uzantı: ".ts" tek başına da parçadır
        if (stem.isBlank()) return true
        return false
    }

    /**
     * Aynı sağlayıcıdan gelen akışları temizler:
     *  1. Bahis/reklam akışları elenir,
     *  2. Aynı host için gerçek bir playlist (.m3u8/.mpd) varken parça URL'leri elenir,
     *  3. Yine kalan parçalar (tek başına kalıp oynatılamayacak olanlar) elenir,
     *  4. URL bazlı tekrarlar kaldırılır.
     *
     * @return (temizlenmiş liste, elenen akış sayısı)
     */
    fun sanitize(providerName: String, streams: List<StreamSource>): Pair<List<StreamSource>, Int> {
        if (streams.isEmpty()) return streams to 0

        val kept = ArrayList<StreamSource>(streams.size)
        val dropped = ArrayList<String>()
        var droppedCount = 0

        // 0) Bozuk adres elemesi — oynatıcıya asla verilmemesi gerekenler.
        //    Bunlar "kaynak var ama hata veriyor" şikâyetinin doğrudan nedeni;
        //    elenmezse aşağıdaki hiçbir doğrulama onları kurtaramaz.
        val afterBroken = ArrayList<StreamSource>(streams.size)
        for (stream in streams) {
            val url = stream.url
            if (!url.isNullOrBlank() && isBrokenStreamUrl(url)) {
                dropped += "bozuk adres (çift şema / host yok): ${url.take(90)}"
                droppedCount++
                continue
            }
            afterBroken += stream
        }
        if (afterBroken.isEmpty() && streams.isNotEmpty()) {
            // TÜM kaynaklar bozuk adres: geri dönmek oynatıcıyı yine çökertir.
            Log.w(TAG, "[$providerName] Tüm kaynaklar bozuk adres — oynatılabilir kaynak yok.")
            dropped.take(6).forEach { CsTrace.info(providerName, "sanitize", it) }
            return emptyList<StreamSource>() to droppedCount
        }

        // 1) Reklam ve tek başına parça elemeleri
        val afterAds = ArrayList<StreamSource>(afterBroken.size)
        for (stream in afterBroken) {
            if (isLikelyAdStream(stream.title, stream.url) || isLikelyAdStream(stream.name, stream.url)) {
                dropped += "reklam/bahis: ${stream.url.orEmpty().take(90)}"
                droppedCount++
                continue
            }
            afterAds += stream
        }

        // 2) Playlist varken aynı host'un parçalarını at
        val hostsWithPlaylist = afterAds
            .mapNotNull { it.url?.takeIf { u -> !u.isBlank() } }
            .filter { url ->
                val path = pathOf(url)
                path.endsWith(".m3u8") || path.endsWith(".mpd") || path.contains(".m3u8")
            }
            .map { hostOf(it) }
            .filter { it.isNotBlank() }
            .toSet()

        for (stream in afterAds) {
            val url = stream.url.orEmpty()
            if (url.isBlank()) {
                kept += stream
                continue
            }
            if (isHlsFragment(url)) {
                val path = pathOf(url)
                val file = fileNameOf(path)
                val stem = file.substringBeforeLast('.')
                val isNamedFragment = FRAGMENT_NAME_REGEX.containsMatchIn(file) ||
                    NUMERIC_FRAGMENT_REGEX.matches(stem)
                val host = hostOf(url)
                // Aynı hostta gerçek bir playlist varsa parça kesinlikle gereksiz;
                // playlist yoksa parça olduğu adından belli olanlar da elenir.
                if (host in hostsWithPlaylist || isNamedFragment) {
                    dropped += "HLS parçası (tek başına oynatılamaz): ${url.take(90)}"
                    droppedCount++
                    continue
                }
            }
            kept += stream
        }

        // 3) URL ve oynatma koşulları aynı olan tekrarlar
        val seen = HashSet<List<Any?>>()
        val deduped = ArrayList<StreamSource>(kept.size)
        for (stream in kept) {
            val key = stream.url.orEmpty().substringBefore('#').trim()
            if (key.isBlank()) {
                deduped += stream
                continue
            }
            val identity = listOf(
                key,
                CsPlaybackHeaders.identity(stream.requestHeaders),
                stream.externalAudioUrl,
                stream.quality,
                stream.qualityValue,
                stream.csStreamType,
                stream.csExtractorData,
                stream.mediaType
            )
            if (seen.add(identity)) deduped += stream else droppedCount++
        }

        if (dropped.isNotEmpty()) {
            Log.w(TAG, "[$providerName] ${dropped.size} akış elendi (reklam/parça/tekrar)")
            dropped.take(6).forEach { CsTrace.info(providerName, "sanitize", it) }
        }
        if (deduped.isEmpty() && streams.isNotEmpty()) {
            // Eklenti YALNIZCA reklam döndürdüyse (Dizipal/kralbet vakası) orijinal listeye
            // dönmek reklamı yeniden oynatmak olurdu: bu durumda boş liste döndürülür ve
            // kullanıcı "kaynak yok" görür — reklam asla oynatılabilir akış olarak sunulmaz.
            if (afterAds.isEmpty()) {
                Log.w(TAG, "[$providerName] Tüm kaynaklar reklam/çöp olarak elendi — oynatılabilir kaynak yok.")
                return emptyList<StreamSource>() to droppedCount
            }
            // Parça/tekrar süzgeci her şeyi sildiyse çalışan bir kaynağı kaybetmemek adına
            // orijinal listeye dönülür (reklam elemeleri zaten uygulanmış listeye).
            Log.w(TAG, "[$providerName] Parça/tekrar süzgeci tüm kaynakları eledi — orijinal liste korunuyor.")
            return afterAds to droppedCount
        }
        return deduped to droppedCount
    }
}
