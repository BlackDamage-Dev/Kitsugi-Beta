package com.kitsugi.animelist.data.cloudstream



import com.kitsugi.animelist.core.memory.BoundedCache



import android.util.Base64

import android.util.Log

import com.kitsugi.animelist.data.cloudstream.diag.CsTrace

import com.lagradost.cloudstream3.MainAPI

import com.lagradost.cloudstream3.SearchResponse

import com.lagradost.cloudstream3.LoadResponse

import com.lagradost.cloudstream3.AnimeLoadResponse

import com.lagradost.cloudstream3.TvSeriesLoadResponse

import com.lagradost.cloudstream3.MovieLoadResponse

import com.lagradost.cloudstream3.newAnimeSearchResponse

import com.lagradost.cloudstream3.utils.ExtractorLink

import com.lagradost.cloudstream3.utils.ExtractorLinkType

import com.lagradost.cloudstream3.utils.loadExtractor

import com.lagradost.cloudstream3.extractors.helper.AesHelper

import com.lagradost.cloudstream3.Prerelease

import com.lagradost.cloudstream3.AcraApplication

import com.lagradost.cloudstream3.network.CloudflareKiller

import com.kitsugi.animelist.data.repository.StreamSource

import com.kitsugi.animelist.data.remote.KitsugiIdResolver

import com.kitsugi.animelist.data.remote.ResolvedIds

import com.kitsugi.animelist.model.MediaType

import com.kitsugi.animelist.core.player.SubtitleInput

import com.kitsugi.animelist.core.player.ExternalAudioTrackInput

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.awaitAll

import kotlinx.coroutines.launch

import kotlinx.coroutines.async

import kotlinx.coroutines.sync.Semaphore

import kotlinx.coroutines.sync.withPermit

import kotlinx.coroutines.withContext

import kotlinx.coroutines.withTimeoutOrNull

import java.util.Locale

import com.kitsugi.animelist.utils.KitsugiStrings

import com.kitsugi.animelist.R



/**

 * Cloudflare veya ağ koruması nedeniyle içerik alınamadığında fırlatılır.

 * [KitsugiStreamScreen] bu exception'ı yakalayarak kullanıcıya "Doğrula" butonunu sunar.

 */

class CloudflareBlockException(message: String, cause: Throwable? = null) : Exception(message, cause)



object CsStreamRunner {



    interface EmbedResolveListener {

        fun onEmbedAttempt(

            providerName: String,

            rawUrl: String,

            resolved: Boolean,

            resolvedUrl: String?,

            error: String?

        )

    }



    @Volatile

    var embedResolveListener: EmbedResolveListener? = null



    private const val TAG = "CsStreamRunner"



    /**

     * Arama/stream hatalarını ayırt etmek için ayrı ERROR tag.

     * Filtre: adb logcat -s CS_SEARCH_ERR

     * Combo:  adb logcat -s PLUGIN_DIAG -s CS_SEARCH_ERR

     */

    private const val SERR = "CS_SEARCH_ERR"

    private val yearRegex = Regex("\\b(19|20)\\d{2}\\b")



    /**

     * Arama isteklerini throttle eder — aynı anda en fazla 24 sağlayıcı arama yapar.

     * loadLinks/load için ayrı semaphore kullanıyoruz (deadlock önleme).

     *

     * v2.4.226: 12 → 24. Stream ekranı 30+ eklentiyi PARALEL başlatıyor ama bu semaphore

     * GLOBAL olduğu için kuyruktaki eklentiler izin beklerken kendi 20 sn'lik arama

     * bütçeleri doluyor ve daha tek sorgu atamadan "arama sıfır" dönüyordu.

     */

    private val searchSemaphore = Semaphore(32)



    /**

     * v2.4.240 (KN-K): `searchSemaphore` kuyruğunda toplam geçiren süre (ms, tüm çağrılar).

     *

     * Neden gerekli: arama bütçesi (`searchTimeBudgetMs`) duvar saatiyle ölçülüyordu; 30+

     * eklenti paralel başladığında bir varyantın 3-6 araması kuyrukta izin beklerken bütçeyi

     * tüketiyor ve eklenti DAHA TEK BİR SORGU ATAMDAN "arama sıfır" dönüyordu. Sayaç,

     * `runGetStreams`'ta bütçe hesabından çıkarılır → bekleme, arama hakkını yiyemez.

     */

    private val searchQueueWaitMs = java.util.concurrent.atomic.AtomicLong(0L)



    /** Kuyruk bekleme sayacı anlık görüntüsü (bkz. [searchQueueWaitMs]). */

    fun searchQueueWaitSnapshot(): Long = searchQueueWaitMs.get()



    /**

     * İçerik yükleme ve stream link çekme için ayrı semaphore.

     * Cloudflare rate-limit engellerini önlemek için max 6 eşzamanlı istek.

     */

    private val loadSemaphore = Semaphore(6)



    /** Tek bir provider için stream getirme zaman aşımı.

     *  İç aşamalar: arama ≤ 20 sn + load ≤ 15 sn + loadLinks ≤ 25 sn + embed/HEAD — 40 sn

     *  bu toplamı karşılamıyordu; provider zaman aşımına düşünce BULUNMUŞ kaynaklar bile

     *  çöpe gidiyordu. 75 sn'ye çıkarıldı; yine de zaman aşımı olursa kısmi sonuçlar

     *  (partialSink) kurtarılıp döndürülür. Eklentiler paralel çalıştığı için kartlar

     *  doldukça güncellenir; bu artış kullanıcıyı bekletmez. */

    private const val PROVIDER_TIMEOUT_MS = 100_000L  // v2.4.258: 75 → 100 sn (search 20 + load 15 + loadLinks 60 + embed)



    // ─── Derin embed çözümleme (Aşama 3/4) sabitleri ──────────────────────────

    /** iframe/AES zincirinin izin verilen en fazla derinliği (sonsuz döngü koruması). */

    private const val MAX_EMBED_DEPTH = 2

    /** Tek bir embed'den kabul edilecek en fazla medya adayı. */

    private const val MAX_STREAMS_PER_EMBED = 8

    /** Takip edilecek en fazla iframe sayısı. */

    private const val MAX_IFRAME_FOLLOW = 2

    /** Embed sayfası indirilirken kabul edilen en fazla karakter (bellek koruması). */

    private const val MAX_EMBED_HTML_CHARS = 900_000

    /** Derin tarama için sayfa indirme zaman aşımı. */

    private const val EMBED_HTML_TIMEOUT_MS = 10_000L

    /** Aday medya URL'si doğrulama zaman aşımı. */

    private const val MEDIA_PROBE_TIMEOUT_MS = 6_000L

    /**

     * Paralel doğrulanacak en fazla aday sayısı. Sınırsız bırakılırsa 20+ adaylı bir sayfada

     * aynı anda onlarca HEAD isteği çıkar (site tarafında rate-limit → 403 → kaynak kaybı).

     */

    private const val MAX_PROBE_CANDIDATES = 6

    /** Doğrulanmış aday önbelleği — aynı URL tekrar denendiğinde ağ harcanmaz. */

    private val mediaProbeCache = BoundedCache<String, Boolean>("cs.mediaProbe", 300)



    /**

     * Aynı anda çözülecek embed sayısı. Sıralı çözümleme (eski davranış) 20 sn timeout × N

     * embed yüzünden kaynak listesini dakikalarca geciktiriyordu.

     */

    private const val EMBED_RESOLVE_CONCURRENCY = 3



    private data class PendingEmbedLink(

        val url: String,

        val name: String,

        val headers: Map<String, String>,

        val mediaType: com.kitsugi.animelist.core.player.StreamMediaType?,

        val quality: Int,

        val audioUrl: String?

    )



    private data class PendingEmbedResult(

        val link: PendingEmbedLink,

        val streams: List<StreamSource>,

        val allowFallback: Boolean = true,

        val inconclusiveDirect: Boolean = false

    )



    private fun streamMediaType(type: ExtractorLinkType): com.kitsugi.animelist.core.player.StreamMediaType? =

        when (type) {

            ExtractorLinkType.M3U8 -> com.kitsugi.animelist.core.player.StreamMediaType.HLS

            ExtractorLinkType.DASH -> com.kitsugi.animelist.core.player.StreamMediaType.DASH

            ExtractorLinkType.VIDEO -> com.kitsugi.animelist.core.player.StreamMediaType.PROGRESSIVE

            else -> null

        }



    private val streamProbeClient by lazy {

        CsVideoInterceptorFactory.buildClient(ignoreSSL = true).newBuilder()

            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)

            .build()

    }

    private val directMediaProbe by lazy { CsDirectMediaProbe(streamProbeClient) }



    private fun probeFor(

        api: MainAPI,

        name: String,

        url: String,

        headers: Map<String, String>,

        quality: Int?,

        mediaType: com.kitsugi.animelist.core.player.StreamMediaType?

    ): CsDirectMediaProbe {

        val interceptor = CsVideoInterceptorFactory.videoInterceptor(api, name, url, headers, quality, mediaType)

            ?: return directMediaProbe

        return CsDirectMediaProbe(streamProbeClient.newBuilder().addInterceptor(interceptor).build())

    }



    /**

     * Aşama 4 (WebView medya yakalayıcı) açık mı?

     *

     * WebView çözümlemesi yavaştır (≈12 sn) ve yalnızca JS ile üretilen oynatıcılarda gerekir;

     * bu yüzden yalnızca önceki tüm aşamalar başarısız olduğunda ve sayfa gerçekten bir

     * oynatıcı sayfası olduğunda devreye girer. Ayarlardan kapatılabilir.

     */

    @Volatile

    var enableWebViewSniffing: Boolean = true



    /**

     * Bilinen domain değişikliklerini otomatik uygular.

     * Site taşındığında plugin yeniden indirilmeden arama çalışmaya devam eder.

     *

     * Format: "eski_domain" -> "yeni_domain"

     * Kaynak: https://github.com/Kraptor123/domainListesi/blob/main/eklenti_domainleri.txt

     */

    private val KNOWN_DOMAIN_FIXES = mapOf(

        "asyaanimeleri.pw"    to "asyaanimeleri.top",

        "turkanime.co"        to "www.turkanime.tv",

        "animeler.me"         to "animeler.pw",

        "uqload.co"           to "uqload.is",

        "uqload.com"          to "uqload.is",

        "dood.la"             to "doodstream.com",

        "dood.wf"             to "doodstream.com",

        "dood.pm"             to "doodstream.com",

        "vidplay.online"      to "vidoy.com",

        "dropload.io"         to "dropload.tv",

        "vtube.to"            to "vtube.network",

        "vidmoly.to"          to "ww547.vidmoly.to",

        // Anizium domain yönlendirmeleri

        "x.anizium.co"        to "api.anizium.co",

        "x.anizium.de"        to "api.anizium.de",

        // RecTV domain geçişleri (v2.4.249: Kekik-master 2026-10-11 → a.psrectv80.xyz;

        // m.prectv72.lol artık bayat, ara basamak olarak korunuyor)

        "prectv38.sbs"        to "a.psrectv80.xyz",

        "prectv43.sbs"        to "a.psrectv80.xyz",

        "prectv50.sbs"        to "a.psrectv80.xyz",

        "a.prectv70.lol"      to "a.psrectv80.xyz",

        "b.prectv70.lol"      to "a.psrectv80.xyz",

        "m.prectv50.sbs"      to "a.psrectv80.xyz",

        "m.prectv72.lol"      to "a.psrectv80.xyz",

        // DBX (DiziBox) embed player

        "dbx.molystream.org"  to "www.molystream.org",

        // DiziPal — en güncel canlı domain (dizipal3008.com)

        "dizipal.bid"         to "dizipal3008.com",

        "dizipal1571.com"     to "dizipal3008.com",

        "dizipal1210.com"     to "dizipal3008.com",

        "dizipal1219.com"     to "dizipal3008.com",

        // v2.4.249: dağıtılan DiziPal v33 bu domain'i kullanıyor, haritasızdı

        // (park sayfası HTML'i → '<' JSON hatası). Asıl çözüm eklenti güncellemesi.

        "dizipal1214.com"     to "dizipal3008.com",

        "dizipal1565.com"     to "dizipal3008.com",

        "dizipal1570.com"     to "dizipal3008.com",

        "dizipal1573.com"     to "dizipal3008.com",

        "dizipal1574.com"     to "dizipal3008.com",

        "dizipal2106.com"     to "dizipal3008.com",

        // DiziKorea — ölü domainler; aktif olan dizikorea3.com

        "dizikorea2.com"      to "dizikorea3.com",

        "dizikorea.pw"        to "dizikorea3.com",

        // FilmMakinesi — güncel domain

        "filmmakinesi.sh"     to "filmmakinesi.to",

        "filmmakinesi.tv"     to "filmmakinesi.to",

        // Dizilla — güncel canlı domain (dizilla.now)

        "dizilla40.com"       to "dizilla.now",

        "dizillahd.com"       to "dizilla.now",

        // HDFilmCehennemi — aktif mirror

        "hdfilmcehennemi.la"  to "hdfilmcehennemi.nl",

        "hdfilmcehennemi.com" to "hdfilmcehennemi.nl",

        // DiziMom — güncel canlı domain (dizimom.help)

        "dizimom.love"        to "www.dizimom.help",

        "dizimom.ws"          to "www.dizimom.help",

        "dizimom.rest"        to "www.dizimom.help",

        "www.dizimom.rest"    to "www.dizimom.help",

        // DiziYou — aktif domain

        "diziyou.mx"          to "www.diziyou.one",

        "diziyou.to"          to "www.diziyou.one",

        // SezonlukDizi

        "sezonlukdizi8.com"   to "sezonlukdizi.cc",

        "sezonlukdizi10.com"  to "sezonlukdizi.cc",

        "sezonlukdizi12.com"  to "sezonlukdizi.cc",

        // FilmModu — aktif domain

        "filmmodu.vip"        to "www.filmmodu.one",

        // FullHDFilm

        "fullhdfilm.cx"       to "hdfilm.us",

        "fullhdfilm.pro"      to "hdfilm.us",

        // FullHDFilmizlesene

        "fullhdfilmizlesene.tv"  to "www.fullhdfilmizlesene.mx",

        "fullhdfilmizlesene.so"  to "www.fullhdfilmizlesene.mx",

        // SinemaCX — güncel domain (sinemacc.com)

        "sinema.lat"          to "sinemacc.com",

        "sinema.gg"           to "sinemacc.com",

        "www.sinema.gg"       to "sinemacc.com",

        // Diğer güncel adres yönlendirmeleri

        "dizi73.life"         to "dizi75.life",

        "dramaflix.cc"        to "dramaflix.net",

        "hdfilmizle.vip"      to "www.hdfilmizle.live",

        "dizifilm.life"       to "dizifilmizle.to",

        "selcukflix.co"       to "selcukflix.com",

        "tranimeizle.io"      to "www.tranimeizle.live",

        "asyalog.com"         to "asyalog.co",

        // v2.4.249: .gg bayat ara basamaktı (tek geçiş kuralı) → doğrudan sinemacc.com

        "sinema.cv"           to "sinemacc.com",

        // WFilmizle — güncel domain (wfilmizle.net)

        "wfilmizle.co"        to "wfilmizle.net",

        "wfilmizle.to"        to "wfilmizle.net",

        "wfilmizle.pro"       to "wfilmizle.net",

        "wfilmizle.pw"        to "wfilmizle.net",

        // Sinezy — güncel domain (sinezy.to)

        "sinezy.info"         to "sinezy.to",

        "sinezy.org"          to "sinezy.to",

        // FilmHane — güncel domain (www.filmhane.shop)

        "filmhane.org"        to "www.filmhane.shop",

        "filmhane.net"        to "www.filmhane.shop",

        "filmhane.one"        to "www.filmhane.shop",

        "filmhane.pro"        to "www.filmhane.shop",

        // DiziGecesi

        "dizigecesi.net"      to "dizigecesi.com",

        "dizigecesi.org"      to "dizigecesi.com",

        // SetFilmIzle — v2.4.249: .uk ÖLÜ (UnknownHost), uygulama eklentiyi kendisi

        // ölü domaine yönlendiriyordu. Kekik-master 2026-10-11 → setfilmizle.ltd

        "setfilmizle.my"      to "www.setfilmizle.ltd",

        "setfilmizle.uk"      to "www.setfilmizle.ltd",

        // KultFilmler — nikyokki sürümü kultfilmler.pro kullanıyor (ölü), .net aktif

        "kultfilmler.pro"     to "kultfilmler.net",

        // MirrorVerse — net52.cc ölü, net77.cc aktif (Netmirror/Dismirror ile aynı)

        "net52.cc"            to "net77.cc",

        // ─── 2026-08 İkinci tanı raporu domain düzeltmeleri ─────────────────────

        // DiziLife — v2.4.249: 73 bayat ara basamaktı (tek geçiş kuralı) → doğrudan 75

        "dizi43.life"         to "dizi75.life",

        "dizi55.life"         to "dizi75.life",

        // YeniKaynak — yenikaynak.com aktif

        "yenikaynak.net"      to "www.yenikaynak.com",

        // DiziYo — www.diziyo.so aktif

        "diziyo.nl"           to "www.diziyo.so",

        "diziyo.com"          to "www.diziyo.so",

        // DiziBox — dizibox.live aktif (eski .biz .com)

        "dizibox.com"         to "www.dizibox.live",

        "dizibox.biz"         to "www.dizibox.live",

        // Dizipod — dizipod.com (aktif domain)

        "dizipod.net"         to "dizipod.com",

        // FilmEkseni — filmekseni.vip aktif

        "filmekseni.net"      to "filmekseni.vip",

        // AsyaWatch — asyawatch.com aktif

        "asyawatch.net"       to "asyawatch.com",

        "asyawatch.pw"        to "asyawatch.com",

        // AsyaFanatiklerim — asyafanatiklerim.com aktif

        "asyafanatiklerim.net" to "asyafanatiklerim.com",

        // Filmzal — filmzal.me aktif

        "filmzal.com"         to "filmzal.me",

        // JetFilmizle — jetfilmizle.now aktif (tüm eski domainler)

        "jetfilmizle.to"       to "jetfilmizle.now",

        "jetfilmizle.pro"      to "jetfilmizle.now",

        "jetfilmizle.website"  to "jetfilmizle.now",

        "jetfilmizle.ltd"      to "jetfilmizle.now",

        // ─── 2026-08 Tanı raporu sonrası ek domain düzeltmeleri ─────────────────

        // TRasyalog — asyalog.co feroxx repoda arama yapıyor ama stream çıkmıyor;

        // maarrem sürümü asyalog.com kullanıyor — ikisini de aktif domain'e yönlendir

        "asyalog.com"          to "asyalog.co",

        // SelcukFlix — v2.4.249: .co bayat ara basamaktı; Kekik-master → selcukflix.com

        // (uygulama tek geçiş uygular, zincirleme yapmaz — doğrudan güncel hedef şart)

        "selcukflix.net"       to "selcukflix.com",

        // WebteIzle — webteizle.info (maarrem/nikyokki) 0 stream; feroxx webteizle3.xyz çalışıyor

        "webteizle.info"       to "webteizle3.xyz",

        "webteizle2.xyz"       to "webteizle3.xyz",

        // DiziKorea — tüm eski domain varyantları aktif dizikorea3.com'a yönlendirildi (2026-08)

        // (zaten yukarıda mevcut, ek varyantlar)

        "dizikorea4.com"       to "dizikorea3.com",

        "dizikorea5.com"       to "dizikorea3.com",

        // FilmMakinesi — filmmakinesi.net eski domain

        "filmmakinesi.net"     to "filmmakinesi.to",

        // DiziPalOriginal — 1572 aktif; diğer eski numaralı domainler

        "dizipal2107.com"      to "dizipal1572.com",

        "dizipal2108.com"      to "dizipal1572.com",

        // ─── 2026-08 Cs-Karma-master tanı raporu domain düzeltmeleri ────────────

        // CinemaCity — cinemacity.cc CF/403 yeni aktif domain: cinemacity.rip

        "cinemacity.cc"        to "cinemacity.rip",

        // v2.4.249: yinelenen "dizipal.bid" girdisi silindi — Kotlin mapOf'ta SON yazan

        // kazanır, bu satır yukarıdaki dizipal3008.com hedefini 1565 ile gölgeliyordu.

        // CizgiMax — aktif domain: cizgimax.online

        "cizgiduo.online"      to "cizgimax.online",

        // ─── 2026-08 Stream problemi olan eklentiler için embed düzeltmeleri ──

        // JetFilmizle — s2.videolar.biz DNS hatası; d2rs.com aktif embed (HTTP 200)

        "s2.videolar.biz"      to "d2rs.com",

        "videolar.biz"         to "d2rs.com",

        // SetFilmIzle — setplay.shop HTTP 403; vctplay.site aktif (HTTP 200)

        "setplay.shop"         to "vctplay.site"

    )



    /**

     * Eklentilerin varsayılan canlı domainleri (DEX'te boş veya hatalı mainUrl olan durumlar için).

     */

    val BUILTIN_DEFAULT_DOMAINS = mapOf(

        // Anime

        "anizium"            to "https://api.anizium.co",

        "turkanime"          to "https://www.turkanime.tv",

        "animecix"           to "https://animecix.tv",

        "animeler"           to "https://animeler.pw",

        "animely"            to "https://animely.net",

        "asyawatch"          to "https://asyawatch.com",

        "cizgimax"           to "https://cizgimax.online",

        "animeelysium"       to "https://animeelysium.com",

        "tranimeci"          to "https://tranimaci.com",

        "tranimaci"          to "https://tranimaci.com",

        "tranimeizle"        to "https://www.tranimeizle.live",

        "animpow"            to "https://animpow.com",

        "asyaanimeleri"      to "https://asyaanimeleri.top",

        "asyaanimeleri2"     to "https://asyaanimeleri.top",

        "yoturkanime"        to "https://www.yoturkanime.com",

        "animeizle"          to "https://www.animeizle.biz",

        "asyafanatiklerim"   to "https://asyafanatiklerim.com",

        "onepacetr"          to "https://www.onepacetr.net",

        "openanime"          to "https://openani.me",

        // Dizi

        "dizilla"            to "https://dizilla.now",

        "dizibox"            to "https://www.dizibox.live",

        "dizipal"            to "https://dizipal3008.com",

        "dizipaloriginal"    to "https://dizipal3008.com",

        "dizipalorijinal"    to "https://dizipal3008.com",

        "ddizi"              to "https://www.ddizi.im",

        "diziasia"           to "https://diziasia.com",

        "diziasya"           to "https://api.diziasya.com",

        "dizikorea"          to "https://dizikorea3.com",

        "dizilife"           to "https://dizi75.life",

        "dizifilmorg"        to "https://dizifilmizle.to",

        "dizigecesi"         to "https://dizigecesi.com",

        "dizipod"            to "https://dizipod.com",

        "diziyo"             to "https://www.diziyo.so",

        "sezonlukdizi"       to "https://sezonlukdizi.cc",

        "sinewix"            to "https://ydfvfdizipanel.ru",

        "sinezy"             to "https://sinezy.to",

        "dizimom"            to "https://www.dizimom.help",

        "diziyou"            to "https://www.diziyou.one",

        "dramadizilerim"     to "https://dramaflix.net/tr",

        "dramaizle"          to "https://dramaflix.net/tr",

        "turkdizileri"       to "https://turkdizileri.com",

        // Film

        "hdfilmcehennemi"    to "https://www.hdfilmcehennemi.nl",

        "hdfilmdelisi"       to "https://hdfilmdelisi.one",

        "hdfilmizle"         to "https://www.hdfilmizle.live",

        "sinemacx"           to "https://sinemacc.com",

        "filmmakinesi"       to "https://filmmakinesi.to",

        "filmmodu"           to "https://www.filmmodu.one",

        "filmbol"            to "https://filmbol.org",

        "filmbip"            to "https://filmbip.com",

        "filmekseni"         to "https://filmekseni.vip",

        "filmhane"           to "https://www.filmhane.shop",

        "filmzal"            to "https://filmzal.me",

        "fullhdfilm"         to "https://fullhdfilm.pro",

        "fullhdfilmizlesene" to "https://www.fullhdfilmizlesene.mx",

        "jetfilmizle"        to "https://jetfilmizle.now",

        "webteizle"          to "https://webteizle3.xyz",

        "izleai"             to "https://720pizle.ai",

        "setfilmizle"        to "https://www.setfilmizle.uk",

        "wfilmizle"          to "https://wfilmizle.net",

        // Diğer

        "rectv"              to "https://m.prectv72.lol",

        "koreanturk"         to "https://www.koreanturk.net",

        "kultfilmler"        to "https://kultfilmler.net",

        "belgeselx"          to "https://belgeselx.com",

        "ginikocanli"        to "https://www.giniko.com",

        "kicktr"             to "https://kick.com",

        "kraptorplus"        to "https://a.111477.xyz",

        "mirrorverse"        to "https://net77.cc",

        "seicode"            to "https://seiwatch.net",

        "selcukflix"         to "https://selcukflix.com",

        "syncler"            to "https://syncler.net",

        "torrentfilm"        to "https://torrentfilmindir.net",

        "torrential"         to "https://api.real-debrid.com",

        "watch2movies"       to "https://movies2watch.watch",

        "webdramaturkey"     to "https://webdramaturkey2.com",

        "yts"                to "https://web.yts.gg",

        "yabancidizi"        to "https://yabancidizi.news",

        "yenikaynak"         to "https://www.yenikaynak.com",

        "yesilcamtv"         to "https://yesilcamtv.com"

    )



    private val KNOWN_BROKEN_PLUGINS = emptySet<String>()





    /**

     * Yetişkin (+18) içerik sağlayıcı eklentileri.

     *

     * Bu liste dinamik olarak `showAdultContent` ayarıyla kontrol edilir:

     * - `showAdultContent = false` (varsayılan): Bu eklentiler stream çözümlemesinde engellenir.

     * - `showAdultContent = true`: Kullanıcı onayıyla eklentiler stream pipeline'ından geçer.

     *

     * Uygulama genelindeki `blurAdultMedia` ayarı ise UI katmanında bu eklentilerin

     * döndürdüğü thumbnail/artwork'leri blurlamak için kullanılır.

     *

     * @see showAdultContent

     * @see setShowAdultContent

     */

    private val ADULT_PLUGINS = setOf(

        // ─── Genel Yetişkin / Porno Siteleri ────────────────────────────────────

        "FullPorner", "Hqporner", "HQPorner", "PornoAnne", "XNXX", "Xhamster", "xHamster", "Kalite18",

        "EU", "XXXChina", "XChina", "AdultTvChannels", "Aki", "Allpornstream", "AZNude",

        "BadTv", "Beeg", "CamCaps", "CamWh", "Chatrubate", "Cloudbate",

        "CollectionOfBestPorn", "CosXPlay", "Cumlouder", "Doeda", "DoedaOrijinal",

        "Domlepen", "EFukt", "EPorner", "Erome", "EroThots", "Evooli",

        "FamilyPorn", "Fapix", "FitNakedGirls", "FreePornVideos", "FreeUsePorn",

        "Fyptt", "Girlsswallowed", "Heavy", "HentaiCity",

        "Hentaila", "HentaiWorld", "Hentaizm", "Hanime", "HanimeTV", "HentaizmManga",

        "HotLeak", "HQCollect",

        "İnfluencerChicks", "InfluencerChicks", "JavGuru", "Javseen",

        "Javtiful", "Kopeda", "Koreanpornmovie", "Koreaye", "LiveCamRips",

        "Maheir", "Mangoporn", "MilfNut", "MissAV", "Motherless", "NetFapX",

        "New", "NoodleMagazine", "Notmik", "OnScreens", "OppaiStream",

        "PerfectGirls", "PerverZija", "Pimpbunny", "Pinkueiga", "PMVHaven",

        "Porn36", "PornHat", "PornHub", "Pornocarioca", "Pornslash", "Porntrex",

        "PornWatch", "Redgifs", "RoshyTv", "RouVideo", "Rule34Video", "Rusporn",

        "Sexfilm", "Sextb", "Sxyprn", "Temel", "ThaiPorn", "ThotDeep",

        "Tubepornclassic", "TurkHub", "Turkifsahub", "TurkIfsalar", "TurkPorno",

        "Vsex", "WatchHentai", "Wumaobi", "XMoviesForYou",

        "XNalgas", "Xpaja", "XRares", "xVideos", "XXXParodyHD", "YouJizz",

        "Youperv", "YTBoob", "AnimeAV", "Stripchat", "MomLover", "PornoLandia",

        "PornoLava", "SuperErotikGeldi", "IncestFlix", "XnxxProvider", "XhamsterProvider",

        "AllClassicPorn", "Coomer", "DirtyShip", "Porn00", "SimpCity", "UnusualPornX",

        "VideoCelebs", "WatchPorn", "EPawg"

    )



    /** [CsPluginDiagnosticRunner] gibi dış sınıfların KNOWN_BROKEN_PLUGINS listesine erişimi için. */

    val KNOWN_BROKEN_PLUGINS_SET: Set<String> get() = KNOWN_BROKEN_PLUGINS



    /** Dış sınıfların ADULT_PLUGINS listesine erişimi için. */

    val ADULT_PLUGINS_SET: Set<String> get() = ADULT_PLUGINS



    /**

     * Kullanıcının "+18 içerik" tercihini yansıtır.

     *

     * - `false` (varsayılan): [ADULT_PLUGINS] listesindeki eklentiler [getStreams]'de engellenir.

     * - `true`: Adult eklentiler stream pipeline'ından geçer (kullanıcı onayladı).

     *

     * Bu değer [setShowAdultContent] ile güncellenir; [AppRootSettingsExtras.onAdultContentChanged]

     * ve [KitsugiApplication] startup'ında [SettingsDataStore]'dan okunarak senkronize edilir.

     */

    @Volatile

    var showAdultContent: Boolean = false

        private set



    /**

     * `showAdultContent` değerini günceller.

     * UI ayar değişikliğinde veya uygulama başlangıcında çağrılmalıdır.

     */

    fun setShowAdultContent(show: Boolean) {

        if (showAdultContent != show) {

            showAdultContent = show

            android.util.Log.i(TAG, "+18 içerik filtresi güncellendi: showAdultContent=$show")

        }

    }



    /**

     * Kalıcı olarak kapalı veya ölü olduğu bilinen alan adları.

     *

     * DİKKAT (v2.4.256 notu): Eklenti artık ATLANMIYOR (v2.4.240+), ama bu liste hâlâ ETKİLİ:

     * [applyDomainFix] eşleşen eklentinin mainUrl'ini "ölü domain kurtarma" ile uzak/yerleşik

     * tablodaki adrese ZORLA çevirir. Eklenti deposu bir eklentiyi buradaki bir domaine

     * taşırsa, o domain bu listeden MUTLAKA çıkarılmalıdır.

     */

    private val KNOWN_BROKEN_DOMAINS = setOf(

        // ─── DNS çözümlenemeyen / tamamen kapalı domainler ────────────────────

        "ifsalog4.club",

        "superfilmgeldi12.art",

        "superfilmgeldi13.art",

        "ugurfilm1.xyz",

        "ugurfilm3.xyz",

        "prectv38.sbs",         // RecTV eski domain

        "prectv43.sbs",         // RecTV eski domain

        "koreanturk.net",       // Origin Connection Timeout (522)

        "roketdizi.live",       // UnknownHostException — tanı 2026-08

        "wfilmizle.art",        // UnknownHostException — tanı 2026-08

        "filmizlesene.plus",    // UnknownHostException — tanı 2026-08

        "fullhdfilm.cx",        // UnknownHostException — tanı 2026-08

        "kultfilmler.pro",      // UnknownHostException — tanı 2026-08

        "hdabla.net",           // CF/WAF — hiçbir aramada sonuç yok

        // ─── 2026-08 Tanı raporu ek ölü domainler ────────────────────────────

        "net52.cc",             // MirrorVerse eski domain (domain fix ile net77'ye yönlendiriliyor)

        "tafdi.info",           // Tafdi — 0 sonuç, DNS yok

        "dizimag.mom",          // DiziMag — 0 sonuç

        // v2.4.256: "hdfilmcehennemi2.site" ÇIKARILDI — eklenti deposu HDFilmCehennemi2'yi bu

        // domaine taşıdı (canlı). Listede kaldığı sürece applyDomainFix eklentiyi "ölü domain

        // kurtarma" yoluyla BAŞKA bir siteye (ana HDFC: hdfilmcehennemi.nl) çeviriyordu.

        "hdfilmizle.to",        // HDFilmIzle — 0 sonuç

        "hdfilmsite.net",       // HDFilmSitesi — 0 sonuç

        "filmkovasi.pw",        // FilmKovasi — 0 sonuç

        "filmizleilk.vip",      // FilmIzleIlk — 0 sonuç

        "fullhdfilmizlede.org"  // FullHDFilmİzlede — 0 sonuç

    )



    /**

     * Kalıcı olarak erişilemeyen CDN sunucuları.

     * Bu CDN'lerden gelen embed URL'leri loadExtractor'a hiç gönderilmez ve

     * ÖLÜ KANAL fallback olarak da kullanıcıya sunulmaz — tamamen sessizce atlanır.

     */

    private val KNOWN_DEAD_CDN_HOSTS = setOf(

        "pichive.online",        // FourPichive / AsyaWatch / Dizilla — her zaman ÖLÜ KANAL

        "pichive.cc",            // Pichive CDN alternatif alan

        "sssrr.org",             // Abyss CDN (AsyaAnimeleri) — her zaman ÖLÜ KANAL

        "abyss.to",              // Abyss servis alanı (yedek)

        "vmnow.online"           // VidMoly CDN yeni alt domain — 0 stream; SezonlukDizi embed CDN'i

    )



    /**

     * Cloudflare / WAF challange nedeniyle yavaş çalışan eklentiler.

     * Bu eklentiler için:

     *  1. ID doğrulaması (syncData kontrolü) atlanır — her load() çağrısı CF timeout riski taşıdığından

     *  2. Timeout PROVIDER_TIMEOUT_MS yerine WEBVIEW_PROVIDER_TIMEOUT_MS kullanılır

     * TrAnimeci: tranimaci.com Custom WAF Security Verification — CloudflareKiller bile takılıyor

     */

    internal val CF_PROTECTED_PLUGINS = setOf(

        "TrAnimeci",

        "TrAnimeIzle",

        // DiziBox — molystream.org embed CDN CF korumalı; loadLinks 25s timeout'ta kilitlendi (2026-08 tanı)

        "DiziBox",

        // TurkAnime — turkanime.tv CF + AES şifreli player; 25s yeterli değil

        "TurkAnime"

    )



    /** CF korumalı eklentiler için uzatılmış timeout (v2.4.239: 240 sn = 90 sn CF gate +

     *  60 sn arama + 15 sn load + ~75 sn sezon/bölüm/loadLinks zinciri). Eski 120 sn,

     *  gate'in tek başına tükettiği süre yüzünden zincirin son halkalarını kesiyordu. */

    private const val CF_PROVIDER_TIMEOUT_MS = 240_000L



    /** CF şüpheli hostlarda aşama bazlı sabır süresi (120 sn) — safeSearch / safeLoad / loadLinks */

    private const val CF_STAGE_TIMEOUT_MS = 120_000L



    /**

     * v2.4.240 (KN-A): safeSearch / safeLoad için TABAN aşama süresi.

     *

     * Yukarıdaki CF sabır bütçesi tasarım olarak "safeSearch / safeLoad / loadLinks" üçününe

     * uygulanacaktı; koda yalnızca loadLinks bağlanmıştı (stageTimeoutMs çağrısı). Arama ve

     * load sabit 15 sn'de kaldığı için, Cloudflare/Turnstile/vDDoS çözümü 90-120 sn süren

     * her kaynakta kutu çözüm bitmeden patlıyor ve sonuç HER ZAMAN "0 sonuç" oluyordu —

     * üstelik zaman aşımı hiçbir yere yazılmadığı için ayırt edilemiyordu.

     */

    private const val SEARCH_STAGE_TIMEOUT_MS = 15_000L

    private const val LOAD_STAGE_TIMEOUT_MS = 15_000L



    /**

     * v2.4.240: safeSearch içinden çağrılan CF gate'inin bütçesi. UI akışları

     * (ensureCfClearance) tam CF_GATE_BUDGET_MS'i kullanabilir; akış boru hattında

     * provider bütçesi (75 sn) gate'e yenilmesin diye daha kısa tutulur.

     */

    private const val SEARCH_GATE_BUDGET_MS = 40_000L



    private val dynamicDomains = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val dynamicBlockedPlugins = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val isDomainListFetched = java.util.concurrent.atomic.AtomicBoolean(false)

    private val runnerScope = kotlinx.coroutines.CoroutineScope(

        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO +

        kotlinx.coroutines.CoroutineExceptionHandler { _, t ->

            Log.e(TAG, "runnerScope background error: ${t.javaClass.simpleName}: ${t.message}", t)

        }

    )



    /**

     * Uzak domain listesinin başarıyla yüklenip yüklenmediğini döndürür.

     * KitsugiApplication, warmup başlatmadan önce bu fonksiyonu bekler.

     */

    fun isDomainListReady(): Boolean = isDomainListFetched.get() && dynamicDomains.isNotEmpty()



    /**

     * Uzak domain listesini arka planda çeker (henüz çekilmediyse).

     * Uygulama açılışında ve her arama başında otomatik tetiklenir.

     */

    fun triggerRemoteDomainsFetch() {

        if (!isDomainListFetched.get()) {

            runnerScope.launch { fetchRemoteDomains() }

        }

    }



    /**

     * Uzak domain listesi önbelleğini sıfırlar ve yeniden çeker.

     * Beni ara: "Domain yenile" dediğinde bunu çağırıyoruz.

     */

    fun forceRefreshDomains() {

        isDomainListFetched.set(false)

        dynamicDomains.clear()

        dynamicBlockedPlugins.clear()

        runnerScope.launch { fetchRemoteDomains() }

        Log.i(TAG, "Remote domain cache temizlendi, yeniden çekiliyor...")

    }



    /**

     * Domain listesini GitHub'daki domain_fixes.json dosyasından çeker.

     * URL: https://raw.githubusercontent.com/KitsugiBeta-dev/Kitsugi-Beta/main/domain_fixes.json

     *

     * Dosyayı güncellemek için:

     *   1. Kitsugi-Beta/domain_fixes.json dosyasını düzenle

     *   2. git push yap (veya GitHub_Upload.bat çalıştır)

     *   3. Kullanıcılar uygulamayı kapatıp açtığında yeni domainleri otomatik çeker

     */

    private suspend fun fetchRemoteDomains() = withContext(Dispatchers.IO) {

        if (isDomainListFetched.getAndSet(true)) return@withContext

        try {

            Log.d(TAG, "Fetching remote domain list from Codeberg (BlackDamage/Kitsugi-Beta)...")

            val endpoints = listOf(

                "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Beta/main/domain_fixes.json",

                "https://codeberg.org/BlackDamage/Kitsugi-Beta/raw/branch/main/domain_fixes.json"

            )



            var jsonBody = ""

            for (endpoint in endpoints) {

                try {

                    val request = okhttp3.Request.Builder()

                        .url(endpoint)

                        .header("Cache-Control", "no-cache")

                        .build()

                    val response = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute()

                    if (response.isSuccessful) {

                        val body = response.body?.string()

                        if (!body.isNullOrBlank()) {

                            jsonBody = body

                            Log.d(TAG, "Successfully loaded domain_fixes.json from $endpoint")

                            break

                        }

                    }

                } catch (e: Exception) {

                    Log.w(TAG, "Failed to load domain fixes from $endpoint: ${e.message}")

                }

            }



            val json = jsonBody.ifEmpty { throw java.io.IOException("Unable to fetch domain_fixes.json from all endpoints") }



            // Parse JSON: { "domains": { "eklentiadi": "https://..." } }

            val jsonObj = org.json.JSONObject(json)

            val domainsObj = jsonObj.optJSONObject("domains") ?: run {

                Log.w(TAG, "domain_fixes.json içinde 'domains' anahtarı bulunamadı.")

                isDomainListFetched.set(false)

                return@withContext

            }

            var loaded = 0

            for (key in domainsObj.keys()) {

                val url = domainsObj.optString(key, "")

                if (key.isNotEmpty() && url.startsWith("http")) {

                    dynamicDomains[key.lowercase(Locale.ROOT)] = url

                    loaded++

                }

            }



            // Parse dynamic blocked plugins: { "blocked": ["eklenti1", "eklenti2"] }

            val blockedArr = jsonObj.optJSONArray("blocked")

            val blockedSet = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            if (blockedArr != null) {

                for (i in 0 until blockedArr.length()) {

                    val pName = blockedArr.optString(i, "")

                    if (pName.isNotEmpty()) {

                        blockedSet.add(pName.lowercase(Locale.ROOT))

                    }

                }

            }

            dynamicBlockedPlugins.clear()

            dynamicBlockedPlugins.addAll(blockedSet)



            Log.i(TAG, "✅ Uzak domain listesi başarıyla yüklendi: $loaded eklenti domaini güncellendi, ${dynamicBlockedPlugins.size} eklenti engellendi.")

            CsTrace.info("system", "domains", "domain_fixes.json yüklendi: $loaded domain, ${dynamicBlockedPlugins.size} engelli kayıt")

        } catch (e: Exception) {

            Log.e(TAG, "❌ Uzak domain listesi çekilemedi: ${e.message}")

            CsTrace.warn("system", "domains", "domain_fixes.json çekilemedi: ${e.message}")

            // Bayrak sıfırlanır — bir sonraki açılışta tekrar denenecek

            isDomainListFetched.set(false)

        }

    }



    /**

     * Plugin'in çalışması için gerekli tüm hazırlıkları yapar:

     * 1. DEX anti-tamper ve imza kontrollerini bypass ederek isAllowedVersion = true yapar.

     * 2. RequestBlocker sayacını sıfırlar.

     * 3. Boş veya geçersiz mainUrl'yi canlı çalışan domain ile günceller.

     */

    fun ensurePluginReady(api: MainAPI) {

        try {

            val loader = api.javaClass.classLoader

            if (loader != null) {

                CsPluginLoader.applyHelperPatches(loader, api.name, api.javaClass.name)

            }

        } catch (e: Throwable) {

            Log.w(TAG, "[${api.name}] ensurePluginReady: Helper patch uygulanamadı: ${e.message}")

        }

        try {

            applyDomainFix(api)

        } catch (e: Throwable) {

            Log.w(TAG, "[${api.name}] ensurePluginReady: Domain fix uygulanamadı: ${e.message}")

        }

    }



    /** Zorla uygulanan (yerleşik tablodan gelen) domainlerin eklentideki özgün hâli. */

    private val forcedDomainOriginals = java.util.concurrent.ConcurrentHashMap<String, String>()



    /**

     * Bu oturumda özgün domaine dönülmüş eklentiler. Dönüşten sonra [applyDomainFix] tekrar

     * tablo domainini zorlarsa geri alma işi anlamsızlaşır (ensurePluginReady her aramada çağrılır).

     * [clearUnsupportedMethodsCache] ile her yeni aramada temizlenir.

     */

    private val domainRevertedPlugins = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()



    /**

     * Bu oturumda arama öncesi CF gate'i DENENMİŞ eklentiler. Gate, arama zaman kutusunun

     * dışında koşar ama 90 sn'ye kadar sürebildiği için aynı eklentinin 18 arama varyantında

     * tekrar tekrar denenmez. [clearUnsupportedMethodsCache] ile her yeni aramada sıfırlanır.

     */

    private val cfGateTriedForPlugins = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()



    /**

     * Eklentinin domaini KNOWN_BROKEN_DOMAINS listesinde mi?

     *

     * Uzak domain_fixes.json'dan TAZE gelen bir domain, bu sabit (eski) listeyi geçersiz kılar.

     * Örn. AltıYüzAltmışAltıFilmİzle için uzak tablo 666filmizle.site'ı güncel gösterirken sabit

     * liste aynı domaini "ölü" sayıp eklentiyi hiç denemiyordu.

     */

    private fun isKnownBrokenDomainFor(api: MainAPI): Boolean {

        if (dynamicDomains.containsKey(normalizePluginKey(api.name))) return false

        val currentDomain = api.mainUrl

            .replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/')

        return KNOWN_BROKEN_DOMAINS.any { currentDomain.contains(it) }

    }



    /**

     * Yerleşik tablo yüzünden domaini değiştirilmiş eklentiyi kendi özgün domainine döndürür.

     *

     * Neden gerekli: ölçümde 9 eklentide (FullHDFilmizlesene, RecTV, DiziMom, WebteIzle,

     * SetFilmIzle, FullHDFilm, DiziPal, DiziPalOriginal …) uygulamanın zorla uyguladığı domain,

     * eklentinin kendi beyan ettiği (ve o an güncel olan) domainden farklı çıktı. Tablo eskimişse

     * eklenti hiç sonuç vermiyordu. Bu yöntem, arama boş döndüğünde özgün domaine **bir kez**

     * döner; döngüye girme riski yoktur (kayıt `remove` ile tüketilir).

     */

    private fun revertForcedDomain(api: MainAPI): Boolean {

        val original = forcedDomainOriginals.remove(api.name) ?: return false

        if (original.isBlank() || original == "/") return false

        if (original.trimEnd('/') == api.mainUrl.trimEnd('/')) return false

        Log.w(

            TAG,

            "[${api.name}] Zorla uygulanan domain sonuç vermedi → eklentinin kendi domainine dönülüyor: " +

                "'${api.mainUrl}' -> '$original'"

        )

        domainRevertedPlugins.add(api.name)

        api.mainUrl = original

        return true

    }



    /**

     * Plugin'in mainUrl'sini bilinen dinamik (GitHub) ve dahili varsayılan domainlerle günceller.

     * Boş, "/" veya bilinen geçersiz domainleri derhal canlı çalışan URL'ye taşır.

     */

    internal fun applyDomainFix(api: MainAPI) {

        // Bu oturumda özgün domaine dönüldüyse tabloyu tekrar zorlama (aksi halde geri alma etkisiz kalır).

        if (api.name in domainRevertedPlugins && api.mainUrl.isNotBlank() && api.mainUrl != "/") return

        val nameKey = normalizePluginKey(api.name)

        val builtinFallback = resolveBuiltinDomain(nameKey)

        val dynamicUrl = dynamicDomains[nameKey]

        val currentUrl = api.mainUrl

        val normalize = { u: String -> u.replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/') }



        // ── KURAL (v2.4.226): Yerleşik (hardcoded) domain tablosu, SAĞLIKLI bir

        // mainUrl'ü ASLA ezemez. Eski davranış `normalize(current) != normalize(remote)`

        // farkında tabloyu zorluyordu; tablo eskidiğinde eklentinin kendi canlı domaini

        // (veya runtime domain çözümü) ölü bir adrese taşınıyor ve arama SIFIR dönüyordu.

        // Resmi CloudStream davranışı: plugin kendi mainUrl'ini kendi yönetir.

        //

        // Tablolar yalnızca şu durumlarda devreye girer:

        //   1) mainUrl boş / "/" / bilinen zehirli adres (x.anizium.co vb.),

        //   2) mainUrl KNOWN_BROKEN_DOMAINS / eski domain eşlemesinde (ölü olduğu biliniyor),

        //   3) TAZE uzak tablo (domain_fixes.json, açılışta çekilir) sağlıklı domain'den

        //      farklı bir adres veriyorsa — o da revert güvenlik ağıyla (forcedDomainOriginals).

        val currentIsBlankOrPoisoned = currentUrl.isBlank() || currentUrl == "/" ||

            currentUrl.contains("x.anizium.co") || currentUrl.contains("anizium.de")

        val currentIsKnownDead = !currentIsBlankOrPoisoned && isKnownBrokenDomainFor(api)



        if (currentIsBlankOrPoisoned || currentIsKnownDead) {

            val rescueUrl = dynamicUrl ?: builtinFallback

            if (rescueUrl != null && normalize(currentUrl) != normalize(rescueUrl)) {

                Log.w(TAG, "[${api.name}] Boş/ölü domain kurtarıldı: '$currentUrl' -> '$rescueUrl'")

                if (currentUrl.isNotBlank() && currentUrl != "/") {

                    forcedDomainOriginals.putIfAbsent(api.name, currentUrl)

                }

                api.mainUrl = rescueUrl

                return

            }

        } else if (dynamicUrl != null && normalize(currentUrl) != normalize(dynamicUrl)) {

            // Yalnızca uygulama açılışında tazelenen uzak tablo sağlıklı domain'i geçebilir.

            // Hardcoded BUILTIN_DEFAULT_DOMAINS burada KULLANILMAZ (eskiyebilir).

            Log.w(TAG, "[${api.name}] Domain güncel uzak tabloyla hizalandı: '$currentUrl' -> '$dynamicUrl'")

            forcedDomainOriginals.putIfAbsent(api.name, currentUrl)

            api.mainUrl = dynamicUrl

            return

        }



        if (currentUrl.isBlank() || currentUrl == "/") {

            if (builtinFallback != null) {

                Log.w(TAG, "[${api.name}] Boş domain geri kazanıldı: '$builtinFallback'")

                api.mainUrl = builtinFallback

            }

            return

        }



        try {

            val uri = java.net.URI(currentUrl)

            val host = uri.host ?: ""

            if (host.isNotBlank()) {

                for ((oldDomain, newDomain) in KNOWN_DOMAIN_FIXES) {

                    val cleanOld = oldDomain.replace("www.", "")

                    val cleanHost = host.replace("www.", "")

                    if (cleanHost.equals(cleanOld, ignoreCase = true) || cleanHost.contains(cleanOld, ignoreCase = true)) {

                        val cleanNew = newDomain.replace("www.", "")

                        val preferredHost = if (host.startsWith("www.") || newDomain.startsWith("www.")) "www.$cleanNew" else cleanNew

                        

                        val scheme = uri.scheme ?: "https"

                        val path = uri.rawPath ?: ""

                        val query = uri.rawQuery?.let { "?$it" } ?: ""

                        val fragment = uri.rawFragment?.let { "#$it" } ?: ""

                        

                        val fixedUrl = "$scheme://$preferredHost$path$query$fragment"

                        Log.w(TAG, "[${api.name}] Domain lokal kuralla (Uri) düzeltildi: $currentUrl -> $fixedUrl")

                        api.mainUrl = fixedUrl

                        return

                    }

                }

            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to parse URI for domain fix: ${e.message}")

        }



        // Fallback to simple replace if URI parsing failed

        for ((oldDomain, newDomain) in KNOWN_DOMAIN_FIXES) {

            if (currentUrl.contains(oldDomain)) {

                val fixed = currentUrl.replace(oldDomain, newDomain)

                val deduplicated = fixed.replace("www.www.", "www.")

                Log.w(TAG, "[${api.name}] Domain lokal kuralla (Fallback) düzeltildi: $currentUrl -> $deduplicated")

                api.mainUrl = deduplicated

                break

            }

        }

    }



    /**

     * Eklenti adını karşılaştırma anahtarına çevirir.

     *

     * Neden gerekli: `"FullHDFilmİzlede".lowercase(Locale.ROOT)` Türkçe noktalı İ'yi

     * `"i" + U+0307` (birleşen nokta) yapar ve bu ad hiçbir anahtarla eşleşmez. Ayrıca

     * Türkçe karakterler (ş, ğ, ü, ö, ç) eklenti adında bulunup tabloda bulunmadığı için

     * eşleşme kaybolur. Burada ad ASCII'ye indirgenir.

     */

    internal fun normalizePluginKey(name: String): String {

        val sb = StringBuilder(name.length)

        for (ch in name) {

            when (ch) {

                'İ', 'I', 'ı', 'i' -> sb.append('i')

                'Ş', 'ş' -> sb.append('s')

                'Ğ', 'ğ' -> sb.append('g')

                'Ü', 'ü' -> sb.append('u')

                'Ö', 'ö' -> sb.append('o')

                'Ç', 'ç' -> sb.append('c')

                else -> sb.append(ch.lowercaseChar())

            }

        }

        return sb.toString().replace("\u0307", "")

    }



    /** Eklenti adından türetilen ekler — bir marka değişikliği anlamına GELMEYEN son ekler. */

    private val SAFE_NAME_SUFFIXES = listOf(

        "original", "orijinal", "plus", "pro", "tv", "hd", "sitesi", "provider", "official"

    )

    // v2.4.256: "2" son eki ÇIKARILDI. Numaralı eklentiler (HDFilmCehennemi2 vb.) ana eklentinin

    // aynası DEĞİL, ayrı sitelerdir; "2" güvenli sayıldığı için HDFilmCehennemi2 yerleşik tabloda

    // "hdfilmcehennemi" anahtarına düşüyor ve mainUrl'i ana HDFC sitesine çevriliyordu

    // (farklı HTML yapısı → 0 sonuç → revert → zaman aşımı).



    /**

     * Eklenti adına karşılık gelen yerleşik varsayılan domaini bulur.

     *

     * ESKİ DAVRANIŞ (HATA): `nameKey.contains(it.key) || it.key.contains(nameKey)` şeklindeki

     * bulanık arama yanlış eşleşmelere yol açabiliyordu — örneğin "animeler" anahtarı

     * "asyaanimeleri" adının İÇİNDE geçtiği için (tablo sırasına göre) bir eklentinin

     * mainUrl'si başka bir sitenin adresine çevrilebiliyordu. Yanlış domain = eklenti

      * çalışmıyor + yanlış sonuç.

     *

     * YENİ KURAL: yalnızca tam eşleşme veya "ad, anahtarla başlıyor ve geri kalan kısım

     * bilinen bir son ek" durumunda eşleşir. En uzun anahtar önce denenir (deterministik).

     */

    internal fun resolveBuiltinDomain(nameKey: String): String? {

        BUILTIN_DEFAULT_DOMAINS[nameKey]?.let { return it }



        val candidates = BUILTIN_DEFAULT_DOMAINS.entries

            .filter { it.key == nameKey }

            .map { it.value }

            .toMutableList()

        if (candidates.isNotEmpty()) return candidates.first()



        val sorted = BUILTIN_DEFAULT_DOMAINS.keys.sortedByDescending { it.length }

        for (key in sorted) {

            if (nameKey.length <= key.length) continue

            if (!nameKey.startsWith(key)) continue

            val suffix = nameKey.substring(key.length)

            if (SAFE_NAME_SUFFIXES.any { suffix == it || suffix == it.removeSuffix("s") }) {

                return BUILTIN_DEFAULT_DOMAINS[key]

            }

        }

        return null

    }



    /**

     * Herhangi bir URL'yi bilinen dinamik veya lokal domain kurallarıyla günceller.

     * CsCfWarmupManager gibi dış yardımcı sınıfların güncel domainleri ziyaret etmesini sağlar.

     */

    fun getFixedUrl(url: String): String {

        if (url.isBlank()) return url

        try {

            val uri = java.net.URI(url)

            val host = uri.host ?: ""

            if (host.isNotBlank()) {

                val cleanHost = host.replace("www.", "").lowercase(Locale.ROOT)

                

                // 1. Dinamik domain listesinden eşleşme ara (ör. "dizipal" içeren hostlar)

                for ((pluginName, newUrl) in dynamicDomains) {

                    if (cleanHost.contains(pluginName) || pluginName.contains(cleanHost)) {

                        val newUri = java.net.URI(newUrl)

                        val newHost = newUri.host ?: ""

                        if (newHost.isNotBlank()) {

                            val scheme = uri.scheme ?: "https"

                            val path = uri.rawPath ?: ""

                            val query = uri.rawQuery?.let { "?$it" } ?: ""

                            val fragment = uri.rawFragment?.let { "#$it" } ?: ""

                            val preferredHost = if (host.startsWith("www.") && !newHost.startsWith("www.")) "www.$newHost" else newHost

                            return "$scheme://$preferredHost$path$query$fragment"

                        }

                    }

                }



                // 2. Lokal domain düzeltme listesinden (KNOWN_DOMAIN_FIXES) eşleşme ara

                for ((oldDomain, newDomain) in KNOWN_DOMAIN_FIXES) {

                    val cleanOld = oldDomain.replace("www.", "")

                    if (cleanHost.equals(cleanOld, ignoreCase = true) || cleanHost.contains(cleanOld, ignoreCase = true)) {

                        val cleanNew = newDomain.replace("www.", "")

                        val preferredHost = if (host.startsWith("www.") || newDomain.startsWith("www.")) "www.$cleanNew" else cleanNew

                        val scheme = uri.scheme ?: "https"

                        val path = uri.rawPath ?: ""

                        val query = uri.rawQuery?.let { "?$it" } ?: ""

                        val fragment = uri.rawFragment?.let { "#$it" } ?: ""

                        return "$scheme://$preferredHost$path$query$fragment"

                    }

                }

            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to parse URI for getFixedUrl: ${e.message}")

        }

        

        // Basit string değişimi fallback

        var fixedUrl = url

        for ((oldDomain, newDomain) in KNOWN_DOMAIN_FIXES) {

            if (fixedUrl.contains(oldDomain)) {

                fixedUrl = fixedUrl.replace(oldDomain, newDomain).replace("www.www.", "www.")

                break

            }

        }

        return fixedUrl

    }



    // Tracks unsupported methods per provider to avoid calling them repeatedly

    // Format of key: "ProviderName:methodName"

    // NOTE: This is cleared on every new fetch (startFetch) to avoid stale state from previous anime searches.

    private val unsupportedMethods = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()



    /**

     * Her yeni anime aramasında çağrılmalı — önceki oturumdan kalan

     * "desteklenmiyor" işaretlerini temizler. Aksi takdirde bir sonraki

     * anime için o eklentinin search() metodu hiç denenmez.

     */

    fun clearUnsupportedMethodsCache() {

        val count = unsupportedMethods.size

        unsupportedMethods.clear()

        domainRevertedPlugins.clear()

        // v2.4.240: yeni arama turunda CF gate'i tekrar denenbilir (bir önceki turda

        // clearance çerezi süresi dolmuş olabilir). Gate eklenti başına yalnızca 1 kez koşar.

        cfGateTriedForPlugins.clear()

        if (count > 0) Log.d(TAG, "unsupportedMethods cache temizlendi ($count kayıt silindi)")

    }



    private fun isMethodOverridden(api: MainAPI, methodName: String, vararg parameterTypes: Class<*>): Boolean {

        return try {

            val method = api.javaClass.getMethod(methodName, *parameterTypes)

            method.declaringClass != MainAPI::class.java

        } catch (e: Exception) {

            // Fallback to true if reflection fails so we don't break compatibility

            true

        }

    }



    private fun encodePathQuery(query: String): String {

        return try {

            java.net.URLEncoder.encode(query, "UTF-8")

                .replace("+", "%20")

                .replace("%21", "!")

                .replace("%27", "'")

                .replace("%28", "(")

                .replace("%29", ")")

                .replace("%7E", "~")

        } catch (e: Exception) {

            query

        }

    }



    /** An empty result is not proof that other overridden search methods are useless. */

    internal fun shouldTryNextSearchMethod(results: List<*>?): Boolean = results.isNullOrEmpty()



    private fun hostOf(api: MainAPI): String? =

        runCatching { java.net.URI(getFixedUrl(api.mainUrl)).host }.getOrNull()



    /**

     * Bu eklenti için arama öncesi CF gate'i gerekli mi?

     * Hızlı yol koşulları (çerez var / site sağlıklı doğrulanmış) false döndürür —

     * yani sağlıklı kaynaklara gate hiçbir gecikme eklemez.

     */

    internal fun cfGateNeeded(api: MainAPI): Boolean {

        val host = hostOf(api) ?: return false

        if (CloudflareKiller.getSavedCookiesForHost(host)?.isNotEmpty() == true) return false

        if (CsCfWarmupManager.isKnownHealthy(host)) return false

        return api.name in CF_PROTECTED_PLUGINS ||

            CloudflareKiller.wasChallenged(host) ||

            CsCfWarmupManager.isWarmupListedHost(host) ||

            CsProtectionClassifier.isWafChallenge(CsPluginStatusTracker.getErrorMessage(api.name) ?: "")

    }



    /**

     * Arama öncesi clearance gate'i. Süresi arama bütçesinden DÜŞÜLMEZ:

     * çağrı, arama zaman kutusu başlamadan önce yapılır; kullanıcı tarafında

     * "kaynak zaten kendi çözüyormuş" hissi buradan gelir.

     */

    private suspend fun runCfGateIfNeeded(api: MainAPI) {

        if (!cfGateNeeded(api)) return

        val ctx = AcraApplication.context ?: run {

            Log.w(TAG, "[${api.name}] CF gate: context yok — atlanıyor")

            return

        }

        val host = hostOf(api)

        Log.w(

            TAG,

            "[${api.name}] 🔐 CF gate: $host için clearance arama ÖNCESİ çözülüyor " +

                "(bütçe ${CsCfWarmupManager.CF_GATE_BUDGET_MS}ms; arama bütçesinden düşülmez)"

        )

        CsTrace.info(

            api.name,

            "cf-gate",

            "Arama öncesi clearance gate'i (${CsCfWarmupManager.CF_GATE_BUDGET_MS}ms bütçe)"

        )

        runCatching {

            CsCfWarmupManager.ensureClearance(

                ctx,

                getFixedUrl(api.mainUrl),

                CsCfWarmupManager.CF_GATE_BUDGET_MS

            )

        }.onFailure { e ->

            Log.w(TAG, "[${api.name}] CF gate hatası: ${e.message}")

        }

    }



    /**

     * UI akışları (Eklenti Portalı içi arama / ana sayfa vitrini) için arama öncesi

     * CF gate'i. v2.4.239: eklenti sayfasındaki çıplak-isim araması da stream akışıyla

     * aynı korumayı alır — clearance yoksa ≤90 sn'de gizli WebView çözer, sağlıklı

     * sitelerde 0 ms'de döner.

     */

    suspend fun ensureCfClearance(api: MainAPI) {

        runCfGateIfNeeded(api)

    }



    /**

     * v2.4.240: `safeSearch`'e giren TÜM yollar (detay sayfası "İzle"/"Bölümler" akışı,

     * eklenti arama sayfası, `searchAllAddons`, tanı ekleri) artık arama ÖNCESİNDE

     * clearance gate'inden geçer.

     *

     * Eski durumda gate yalnızca AddonExploreDialog'un kullandığı `searchWithCfRetry` /

     * `ensureCfClearance` yolundaydı; akış boru hattı (runGetStreams) gate'i hiç görmediği

     * için çerez kabı boş kalıyor ve her arama doğrudan challenge sayfasına çarpıyordu.

     *

     * Maliyet: sağlıklı hostlarda `cfGateNeeded` false döner → tek bir istek bile atılmaz.

     * Eklenti başına oturumda bir kez denenir (18 varyant × gate olmasın diye).

     */

    private suspend fun runSearchGateOnce(api: MainAPI) {

        if (api.name in cfGateTriedForPlugins) return

        if (!cfGateNeeded(api)) return

        cfGateTriedForPlugins.add(api.name)

        val ctx = AcraApplication.context ?: return

        val url = getFixedUrl(api.mainUrl)

        if (url.isBlank()) return

        Log.w(

            TAG,

            "[${api.name}] 🔐 safeSearch öncesi CF gate: ${hostOf(api)} için clearance deneniyor " +

                "(bütçe ${minOf(CsCfWarmupManager.CF_GATE_BUDGET_MS, SEARCH_GATE_BUDGET_MS)}ms)"

        )

        CsTrace.info(api.name, "cf-gate", "Arama öncesi clearance (tek deneme, kısaltılmış bütçe)")

        runCatching {

            CsCfWarmupManager.ensureClearance(

                ctx,

                url,

                minOf(CsCfWarmupManager.CF_GATE_BUDGET_MS, SEARCH_GATE_BUDGET_MS)

            )

        }.onFailure { e ->

            Log.w(TAG, "[${api.name}] safeSearch CF gate hatası: ${e.message}")

        }

    }



    /**

     * Bir `MainAPI`, kullanıcının etkinleştirdiği eklenti kimliklerinden birine mi ait?

     *

     * ESKİ DAVRANIŞ (HATA): üç ayrı çağrı noktası üç farklı eşleştirme yazmıştı —

     * `searchAllAddons` `lowercase(Locale.ROOT)` kullanıyor, arayüz diyalogları ise hiç

     * küçültmüyordu. Türkçe noktalı İ/ş/ğ karakterlerinde `lowercase()` birleşik nokta

     * ürettiği için (ve dosya adı ile DB id'si birebir eşleşmediğinde) filtre BOŞ liste

     * döndürüyordu → "hiçbir eklentiden veri gelmiyor". Tek ve normalize edilmiş yol budur.

     */

    fun isApiEnabledFor(api: MainAPI, enabledIds: Collection<String>): Boolean {

        val fileKey = runCatching { java.io.File(api.sourcePlugin ?: "").nameWithoutExtension }.getOrNull()

        return matchesEnabledPlugin(fileKey, api.name, enabledIds)

    }



    /**

     * [isApiEnabledFor]'in Android'sız saf çekirdeği — JVM unit testiyle doğrulanabilir.

     *

     * @param fileKey `api.sourcePlugin` dosya adının uzantısız hâli (yükleme kaynağı yoksa null)

     */

    internal fun matchesEnabledPlugin(

        fileKey: String?,

        apiName: String,

        enabledIds: Collection<String>

    ): Boolean {

        if (enabledIds.isEmpty()) return false

        val keys = enabledIds.map { normalizePluginKey(it) }.filter { it.isNotBlank() }.toSet()

        if (keys.isEmpty()) return false

        val normalizedFile = fileKey?.let { normalizePluginKey(it) }

        if (!normalizedFile.isNullOrBlank() && normalizedFile in keys) return true

        return normalizePluginKey(apiName) in keys

    }



    /**

     * v2.4.239 "boş + kanıt" yeniden denemesi: ilk arama boş döner VE host'un challenge

     * gördüğüne dair kanıt varsa (CloudflareKiller.wasChallenged), tam bütçeli CF gate'i

     * çalıştırılıp arama SABIRLI (120 sn aşama bütçesi) biçimde bir kez daha denenecek.

     * Sağlıklı kaynaklarda kanıt olmayacağı için bu yol asla ekstra gecikme üretmez;

     * "her kaynak en azından veri versin" ilkesinin güvenlik ağıdır.

     */

    internal suspend fun searchWithCfRetry(api: MainAPI, query: String): List<SearchResponse> {

        val first = safeSearch(api, query)

        if (first.isNotEmpty()) return first

        val host = hostOf(api) ?: return first

        if (!CloudflareKiller.wasChallenged(host)) return first

        Log.w(TAG, "[${api.name}] 🔍 Arama boş + challenge kanıtı → CF gate + sabırlı tek yeniden deneme")

        CsTrace.info(api.name, "search", "boş sonuç + challenge kanıtı → gate ve sabırlı yeniden deneme")

        runCatching { runCfGateIfNeeded(api) }

        return safeSearch(api, query)

    }



    /**

     * Bu eklenti CF/WAF sabır bütçesine ihtiyaç duyuyor mu?

     *

     * ESKİ DAVRANIŞ (HATA): yalnızca 4 isimlik [CF_PROTECTED_PLUGINS] veya warmup

     * listesindeki (bayat domain'li) host'lar 120 sn alıyordu. Diğer herkes 15 sn

     * arama kutusuna düşüyordu. CloudflareKiller.intercept meydan okumayı görünce

     * 120 sn'lik WebView çözümünü o 15 sn'lik kutunun İÇİNDE başlatıyor — kutu

     * patlayınca çözüm iptal, çerez yok, kart "pat" diye boş. Detay sayfası İzle

     * hattının "isim gidiyor, devamı gelmiyor" şikâyetinin ana saatidir.

     *

     * YENİ: çerezi olmayan ve "sağlıklı" diye doğrulanmamış her host sabır ister.

     * Sağlıklı siteler 1-3 sn'de döner (üst sınır beklenmez); CF'li siteler

     * intercept'in WebView'sini bitirecek zamanı gerçekten alır.

     */

    internal fun needsCfPatience(api: MainAPI): Boolean {

        if (api.name in CF_PROTECTED_PLUGINS) return true

        val host = hostOf(api) ?: return false

        if (CsCfWarmupManager.isKnownHealthy(host)) return false

        if (CloudflareKiller.wasChallenged(host) || CloudflareKiller.isSolvingHost(host)) return true

        if (CloudflareKiller.getSavedCookiesForHost(host).isNullOrEmpty()) return true

        return cfGateNeeded(api)

    }



    /**

     * Aşama timeout'ı: host challenge gördüyse / çözüm sürüyorsa / çerezi yoksa

     * CF sabır bütçesi, değilse mevcut hızlı bütçe. CloudflareKiller WebView'ı

     * 90-120 sn açık tutabildiği için dış zaman aşımı ondan uzun olmak zorundadır.

     */

    private fun stageTimeoutMs(api: MainAPI, baseMs: Long): Long {

        return if (needsCfPatience(api)) CF_STAGE_TIMEOUT_MS else baseMs

    }



    /**

     * "Üst sezon barı" doğrudan gezinmesi: detay sayfasının URL'sinde sezon jetonu

     * (sezon-2 / season-2 / s2 ...) varsa, hedef sezonun sayfasını ARAMA YAPMADAN

     * yükler — sitedeki sezon sekmesine tıklamanın kaynak-bağımsız karşılığıdır.

     */

    internal fun buildSeasonUrlCandidates(url: String?, season: Int): List<String> {

        if (url.isNullOrBlank() || season < 1) return emptyList()

        val patterns = listOf(

            Regex("""sezon[-_]?\d+""", RegexOption.IGNORE_CASE),

            Regex("""season[-_]?\d+""", RegexOption.IGNORE_CASE),

            Regex("""(?<=[/-])s\d+(?=[/-])""")

        )

        val out = LinkedHashSet<String>()

        for (pattern in patterns) {

            if (!pattern.containsMatchIn(url)) continue

            out += url.replace(pattern) { m -> m.value.replace(Regex("""\d+"""), season.toString()) }

        }

        out.remove(url)

        return out.take(3)

    }



    suspend fun getStreams(

        api: MainAPI,

        title: String,

        alternativeTitles: List<String>,

        year: Int?,

        season: Int,

        episode: Int,

        malId: Int? = null,

        aniListId: Int? = null,

        tmdbId: Int? = null,

        kitsuId: Int? = null,

        isMovie: Boolean? = null,

        mediaType: MediaType? = null,

        bangumiId: Int? = null,

        simklId: Int? = null,

        // v2.4.250: cagiran ID'leri zaten cozduyse (StreamViewModel) eklenti basina

        // ARM/TMDB agini atla — aksi halde runGetStreams kendi cozer (onbellekli).

        preResolvedIds: ResolvedIds? = null

    ): List<StreamSource> = withContext(Dispatchers.IO) {

        Log.d(TAG, "━━━ getStreams: provider=${api.name} ━━━")

        Log.d(TAG, "  title='$title' season=$season ep=$episode year=$year")

        Log.d(TAG, "  alternativeTitles=${alternativeTitles.take(3)} mal=$malId aniList=$aniListId")

        val declaredTypes = api.supportedTypes.map { it.name }

        val pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin)

        val effectiveMediaType = CsPluginTypeCompatibility.infer(mediaType, isMovie == true, declaredTypes)

        val ownedBuildReason = CsOwnedPluginProfiles.episodeUnsupportedReason(

            providerName = api.name,

            pluginVersion = pluginVersion,

            mediaType = mediaType,

            isMovie = isMovie

        )

        if (ownedBuildReason != null || !CsPluginTypeCompatibility.supports(effectiveMediaType, declaredTypes)) {

            val reason = ownedBuildReason

                ?: "Bu eklenti ${declaredTypes.joinToString()} türlerini destekliyor; ${effectiveMediaType?.let(CsPluginTypeCompatibility::description)} araması atlandı."

            Log.i(TAG, "[${api.name}] $reason")

            CsTrace.info(api.name, "type-filter", reason)

            return@withContext emptyList()

        }

        val effectiveIsMovie = CsPluginTypeCompatibility.resolveIsMovie(effectiveMediaType, isMovie)



        // Fetch remote domains dynamically in background if not fetched yet

        if (!isDomainListFetched.get()) {

            runnerScope.launch {

                fetchRemoteDomains()

            }

        }



        // Bilinen domain değişikliklerini ve DEX anti-tamper patch'lerini uygula

        ensurePluginReady(api)



        // Dinamik olarak engellenmiş (ölü/bozuk) eklentiler — YALNIZCA uyarı.

        // ESKİ DAVRANIŞ (HATA): domain_fixes.json "blocked" listesi eklentiyi SESSİZCE

        // atlıyordu; liste eskidiğinde (4kfilmizlesene vb.) çalışan eklentiler bile

        // sıfıra iniyordu. YENİ DAVRANIŞ: yine de dene, sebebi tracker'a yaz ki UI

        // "akış bulunamadı" yerine gerçek durumu göstersin.

        val nameKey = api.name.lowercase(Locale.ROOT)

        if (nameKey in dynamicBlockedPlugins) {

            Log.w(TAG, "[${api.name}] Dinamik engelli listesinde (domain_fixes.json) — yine de deneniyor.")

            // Yalnızca gözlem: eklenti denenir, tracker'a "skip" durumu yazılmaz (gerçek sonuç ayrıca kaydedilir).

            CsTrace.info(api.name, "blocklist", "domain_fixes.json blocked listesinde — yine de denendi (liste eskimiş olabilir)")

        }



        // Kalıcı bozuk olduğu bilinen plugin'leri direkt atla — ağ kaynağı harcama

        if (api.name in KNOWN_BROKEN_PLUGINS) {

            Log.w(TAG, "[${api.name}] KNOWN_BROKEN_PLUGINS listesinde — atlanıyor.")

            return@withContext emptyList()

        }



        // ── +18 İçerik Filtresi ──────────────────────────────────────────────────

        // showAdultContent = false (varsayılan) → ADULT_PLUGINS listesindeki eklentiler engellenir.

        // showAdultContent = true → kullanıcı +18 içeriklere izin verdi; eklenti çalışır.

        if (!showAdultContent && api.name in ADULT_PLUGINS) {

            Log.w(TAG, "[${api.name}] ADULT_PLUGINS listesinde ve showAdultContent=false — atlanıyor. (+18 filtre aktif)")

            return@withContext emptyList()

        }

        if (showAdultContent && api.name in ADULT_PLUGINS) {

            Log.d(TAG, "[${api.name}] ADULT_PLUGINS listesinde ama showAdultContent=true — devam ediliyor.")

        }



        // Alan adı bazında ölü domain kontrolü — YALNIZCA uyarı, SESSİZ ATLAMA YOK.

        // ESKİ DAVRANIŞ (HATA): eşleşme olunca `return emptyList()` eklentiyi sessizce

        // sıfıra indiriyor, UI "Bu anime için akış bulunamadı" gösteriyordu. Domain

        // listeleri eskidiğinde (site geri açılır / domain döner) eklenti sonsuza kadar

        // ölü kalıyordu — kullanıcı "hiçbir eklentiden veri gelmiyor" olarak görüyordu.

        // YENİ DAVRANIŞ: yine de dene; domain gerçekten ölüyse arama hızlıca DNS/HTTP

        // hatası verir, hata CsPluginStatusTracker'a düşer ve UI'da gerçek sebep görünür.

        val normalizeUrl = { u: String -> u.replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/') }

        val currentDomain = normalizeUrl(api.mainUrl)

        if (KNOWN_BROKEN_DOMAINS.any { currentDomain.contains(it) }) {

            Log.w(TAG, "[${api.name}] Domain (${api.mainUrl}) ölü domain listesinde — yine de deneniyor (sessiz atlama kaldırıldı).")

        }



        // Engellenen plugin'leri atla — tekrarlı NotImplementedError veya 3+ hata sonrası oluşur

        if (CsPluginStatusTracker.isBlocked(api.name)) {

            val reason = CsPluginStatusTracker.getErrorMessage(api.name)

            Log.w(TAG, "[${api.name}] Engellendi (session block). Sebep: $reason — atlanıyor.")

            CsTrace.warn(api.name, "skip", "Engellendi (oturum bloğu): $reason")

            return@withContext emptyList()

        }



        // Çerezi olmayan / challenge görmüş host'larda 75 sn'lik dış kutu, 120 sn'lik

        // aşama kutusunu (CF WebView çözümü) kesiyordu → bulunanlar bile çöp.

        val effectiveTimeout = if (needsCfPatience(api)) CF_PROVIDER_TIMEOUT_MS else PROVIDER_TIMEOUT_MS

        if (effectiveTimeout > PROVIDER_TIMEOUT_MS) {

            Log.w(TAG, "[${api.name}] CF/WAF sabır — timeout ${effectiveTimeout}ms (çerez yok veya challenge).")

            Log.e(SERR, "🔐 CF_PATIENCE [${api.name}] — ${effectiveTimeout}ms timeout ile çalışıyor. title='$title' S${season}E${episode}")

        }



        // KRİTİK DÜZELTME: eski davranışta provider zaman aşımına uğrayınca (40 sn) daha önce

        // BULUNMUŞ tüm kaynaklar da çöpe gidiyordu (`return emptyList()`) — "hiçbir eklentiden

        // veri gelmiyor" şikâyetinin ana kaynağı. Artık bulunan kaynaklar sink'e yazılır ve

        // zaman aşımında kurtarılıp döndürülür; gerçek sebep tracker'a yazılır ki UI göstersin.

        val partialSink = java.util.concurrent.CopyOnWriteArrayList<StreamSource>()

        val result = withTimeoutOrNull(effectiveTimeout) {

            runGetStreams(

                api = api,

                title = title,

                alternativeTitles = alternativeTitles,

                year = year,

                season = season,

                episode = episode,

                malId = malId,

                aniListId = aniListId,

                tmdbId = tmdbId,

                kitsuId = kitsuId,

                isMovie = effectiveIsMovie,

                mediaType = effectiveMediaType,

                bangumiId = bangumiId,

                simklId = simklId,

                partialSink = partialSink,

                preResolvedIds = preResolvedIds

            )

        }

        if (result == null) {

            val rescued = partialSink.toList()

            if (rescued.isNotEmpty()) {

                Log.w(TAG, "[${api.name}] Provider stream lookup exceeded ${effectiveTimeout}ms; ${rescued.size} kaynak kurtarılarak döndürülüyor.")

                CsTrace.warn(api.name, "timeout", "Zaman aşımı (${effectiveTimeout} ms) — ${rescued.size} kaynak kurtarıldı")

                return@withContext rescued

            }

            Log.w(TAG, "[${api.name}] Provider stream lookup exceeded ${effectiveTimeout}ms; returning no matches.")

            CsPluginStatusTracker.recordSkip(api.name, "Zaman aşımı (${effectiveTimeout / 1000} sn) — arama/çıkarma tamamlanamadı")

            CsTrace.warn(api.name, "timeout", "Zaman aşımı (${effectiveTimeout} ms) — hiçbir kaynak çıkarılamadı")

            return@withContext emptyList()

        }

        result

    }



    private suspend fun runGetStreams(

        api: MainAPI,

        title: String,

        alternativeTitles: List<String>,

        year: Int?,

        season: Int,

        episode: Int,

        malId: Int? = null,

        aniListId: Int? = null,

        tmdbId: Int? = null,

        kitsuId: Int? = null,

        isMovie: Boolean? = null,

        mediaType: MediaType? = null,

        bangumiId: Int? = null,

        simklId: Int? = null,

        partialSink: MutableList<StreamSource>? = null,

        preResolvedIds: ResolvedIds? = null

    ): List<StreamSource> {

        // Resolve external IDs for validation. v2.4.250: cagiran cozulmus ID'leri

        // verdiyse aga CIKMA (164 eklenti x 8 istek firtinasi provider butcesini

        // eritiyordu); yoksa onbellekli cozumleyiciye dus.

        val resolvedIds = preResolvedIds ?: if (malId != null || aniListId != null || tmdbId != null || bangumiId != null || kitsuId != null) {

            try {

                KitsugiIdResolver.resolveIds(

                    malId = malId,

                    aniListId = aniListId,

                    tmdbId = tmdbId,

                    mediaType = mediaType,

                    kitsuId = kitsuId,

                    bangumiId = bangumiId

                )

            } catch (cancel: kotlinx.coroutines.CancellationException) {

                throw cancel

            } catch (e: Exception) {

                null

            }

        } else null

        

        val targetImdb = resolvedIds?.imdbId

        val targetTmdb = resolvedIds?.tmdbId ?: tmdbId

        val targetKitsu = resolvedIds?.kitsuId ?: kitsuId

        val targetMal = resolvedIds?.malId ?: malId

        val targetAniList = resolvedIds?.aniListId ?: aniListId

        // v2.4.251: TMDB tr-TR basligi varyantlara ekle — site SADECE Turkce baslik

        // tasiyorsa onsuz ESLESME YOK kacilinmazdi. Onbellekli: 164 eklenti tek

        // TMDB istegini paylasir; hata/eksik durumda normal listeyle devam edilir.

        val effectiveAlts: List<String> = runCatching {

            val trTitle = TmdbTitleLocalizer.getTurkishTitle(targetTmdb, mediaType, isMovie == true)

            val withTurkish = if (!trTitle.isNullOrBlank() && alternativeTitles.none { it.equals(trTitle, ignoreCase = true) })

                alternativeTitles + trTitle else alternativeTitles

            // 2026-11-02: KAYNAK-BAĞIMSIZ BAŞLIK EVRENİ. Şikâyet: aynı anime AniList

            // kaydından açılınca eklentiler akış veriyor, MAL kaydından açılınca vermiyordu.

            // Sebep: MAL detayı daha dar bir başlık kümesi gönderiyordu. Artık kimliklerden

            // (MAL ve/veya AniList) AniList + Jikan başlık birleşimi çıkarılıp varyantlara

            // eklenir; iki yol da aynı başlık evreniyle arar. Oturum-önbellekli: 164 eklenti

            // tek isteği paylaşır, hata/eksik durumda liste aynen kalır.

            val canonicalAliases = CanonicalTitleAliases.aliasesFor(targetMal, targetAniList, mediaType)

            if (canonicalAliases.isEmpty()) {

                withTurkish

            } else {

                (withTurkish + canonicalAliases).distinctBy { CsTitleMatcher.normalizeTitleForMatch(it) }

            }

        }.getOrDefault(alternativeTitles)

        val isWebViewPlugin = api.usesWebView ||

            api.name in CF_PROTECTED_PLUGINS ||

            api.name.contains("TrAnimeIzle", ignoreCase = true)



        // ── ORTAK KÜME KİMLİĞİ + ÖĞRENİLMİŞ URL HAFIZASI ───────────────────────

        // Eser, uygulamaya hangi katalog kaynağından gelirse gelsin aynı takma ad

        // anahtarlarına indirgenir (CsUnifiedIdentity). Bu eklenti bu eserin içerik

        // sayfasını daha önce BİR KEZ bulduysa (kendi araması / native ID / başlık

        // eşlemesi — yol önemsiz), URL'si hafızadadır: arama ve eşleme halkaları

        // hiç çalışmadan doğrudan load edilir. Torrentio'nun "kimlik → sonuç"

        // determinizminin kazıyan eklentilere uygulama tarafından kazandırılmasıdır.

        // Hafıza ıskası veya ölü URL hiçbir şeyi engellemez: native → arama sırası

        // aynen devam eder, ölü kayıt düşürülür.

        val identityAliases = CsUnifiedIdentity.aliasKeys(

            imdbId = targetImdb,

            tmdbId = targetTmdb,

            malId = targetMal,

            aniListId = targetAniList,

            kitsuId = targetKitsu,

            bangumiId = bangumiId,

            title = title,

            year = year,

            mediaType = mediaType,

            isMovie = isMovie == true

        )

        if (identityAliases.isNotEmpty()) {

            val rememberedUrl = CsProviderUrlMemory.recall(identityAliases, api.name)

            if (!rememberedUrl.isNullOrBlank()) {

                Log.d(TAG, "[${api.name}] 🧠 Ortak küme hafızası isabeti (${identityAliases.first()}): $rememberedUrl")

                CsTrace.info(api.name, "memory", "Öğrenilmiş URL doğrudan deneniyor: $rememberedUrl")

                val rememberedType = when (mediaType ?: if (isMovie == true) MediaType.Movie else MediaType.Anime) {

                    MediaType.Movie -> com.lagradost.cloudstream3.TvType.Movie

                    MediaType.TvShow -> com.lagradost.cloudstream3.TvType.TvSeries

                    else -> com.lagradost.cloudstream3.TvType.Anime

                }

                val rememberedResponse = api.newAnimeSearchResponse(

                    name = api.name,

                    url = rememberedUrl,

                    type = rememberedType,

                    fix = false

                )

                val rememberedResult = loadAndExtractStreams(

                    api = api,

                    match = rememberedResponse,

                    season = season,

                    episode = episode,

                    isMovie = isMovie == true,

                    partialSink = partialSink,

                    title = title,

                    alternativeTitles = effectiveAlts,

                    year = year

                )

                if (rememberedResult.isNotEmpty()) {

                    Log.d(TAG, "[${api.name}] 🧠 Hafıza yolu başarılı — arama atlandı (${rememberedResult.size} stream)")

                    return rememberedResult

                }

                Log.w(TAG, "[${api.name}] 🧠 Hafızadaki URL sonuç vermedi — kayıt düşürülüyor, normal çözüm sırasına geçiliyor: $rememberedUrl")

                CsTrace.warn(api.name, "memory", "Hafıza URL'si boş döndü, kayıt düşürüldü: $rememberedUrl")

                CsProviderUrlMemory.forget(identityAliases, api.name)

            }

        }



        // ── Native ID Resolution Shortcut ────────────────────────────────────

        // If the plugin declares supportedSyncNames, try getLoadUrl() first.

        // This completely bypasses fuzzy title-matching and is far more reliable.

        val nativeResult = tryNativeIdResolution(

            api       = api,

            malId     = resolvedIds?.malId ?: malId,

            aniListId = resolvedIds?.aniListId ?: aniListId,

            tmdbId    = resolvedIds?.tmdbId    ?: tmdbId,

            imdbId    = targetImdb,

            kitsuId   = targetKitsu,

            simklId   = simklId,

            season    = season,

            episode   = episode,

            isMovie   = isMovie == true,

            mediaType = mediaType,

            partialSink = partialSink,

            identityAliases = identityAliases

        )

        // v2.4.226: BOŞ native sonuç artık aramayı KİLİTLEMEZ. getLoadUrl() URL döndürüp

        // load/extract aşaması boş dönerse (site yapısı değişti, bölüm eşleşmedi vb.) eski

        // davranış `return emptyList()` ile title-search fallback'i tamamen yok sayıyordu.

        if (!nativeResult.isNullOrEmpty()) {

            Log.d(TAG, "[${api.name}] ⚡ Native getLoadUrl çözümü başarılı — arama atlandı (${nativeResult.size} stream)")

            return nativeResult

        }

        if (nativeResult != null) {

            Log.w(TAG, "[${api.name}] Native getLoadUrl boş döndü — başlık bazlı aramaya geçiliyor.")

            CsTrace.info(api.name, "search", "getLoadUrl boş → title-search fallback")

        }



        // ── ARAMA İLKESİ (film & dizi mekaniklerinin ortak kuralı) ─────────────

        // Birincil arama YALNIZCA çıplak eser adı ile yapılır — sezon/bölüm bilgisi

        // ASLA arama sorgusuna karışmaz. Hiçbir site (Türkçe/yabancı) "X 1. Sezon

        // 1. Bölüm" sorgusunu çözemez; doğru akış: başlık ara → içerik sayfasına gir

        // → (dizi ise) sezon+bölüm sayfasına in → video verilerini çek.

        // Sezon-kapsamlı sorgular yalnızca iki güvenlik ağı için vardır:

        //  (a) çıplak arama sıfır sonuç döndürürse (sezonları ayrı indeksleyen siteler),

        //  (b) içerik sayfasında hedef sezon yoksa sezon sayfasına gezinme.

        val titleVariants = buildPlainTitleVariants(title, effectiveAlts, isMovie == true)

        Log.d(TAG, "[${api.name}] Arama varyantları (${titleVariants.size}): ${titleVariants.take(6)}")

        CsTrace.info(

            api.name,

            "variants",

            "${titleVariants.size} varyant (mal=$targetMal anilist=$targetAniList tmdb=$targetTmdb): ${titleVariants.take(6)}"

        )



        // Search several strong title/alias queries and merge their results. A single early

        // provider hit can be the wrong sequel, so do not stop until a high-confidence result

        // is available or the bounded query budget is exhausted.

        //

        // DÜZELTME (zaman bütçesi): 18 varyantın SIRAYLA denenmesi (300 ms throttle + ağ × 18)

        // tek başına 40 sn'lik provider bütçesini dolduruyor; sonuçta load/extract aşamasına

        // hiç sıra gelmiyordu ve bulunanlar dış timeout'ta çöpe gidiyordu. Artık varyantlar

        // 3'lü paralel gruplarla denenir, sert bir zaman kutusu uygulanır ve "yeterince iyi"

        // bir eşleşme varsa kalan sorgular boşuna denenmez.

        val collectedResults = java.util.concurrent.ConcurrentHashMap<String, SearchResponse>()

        var searchedVariant = ""

        // Increased from 12 to 18 — Turkish sites need more query variants because they

        // often use Turkish-translated titles, ASCII-only transliterations, and different

        // naming conventions than the canonical (English/Romaji/Japanese) titles.

        val maxSearchVariants = minOf(titleVariants.size, 18)

        // v2.4.239: gate arama ÖNCESİNDE koştuğu için bu bütçe yalnızca saf arama

        // sorgularını kapsar; CF şüpheli hostlarda varyant başına sabır stageTimeoutMs

        // ile ayrıca genişletilir.

        var searchTimeBudgetMs = if (api.name in CF_PROTECTED_PLUGINS || needsCfPatience(api) || cfGateNeeded(api)) 60_000L else 20_000L

        val searchStartMs = System.currentTimeMillis()

        // v2.4.240 (KN-K): kuyrukta geçen süre bütçeden sayılmaz — aşağıdaki her bütçe

        // kontrolü bu lambda üzerinden ölçülür.

        var queuedSearchWaitMs = 0L

        var queueSnapshot = searchQueueWaitSnapshot()

        val searchElapsedMs: () -> Long = {

            val waited = searchQueueWaitSnapshot() - queueSnapshot

            kotlin.math.max(0L, System.currentTimeMillis() - searchStartMs - queuedSearchWaitMs - waited)

        }

        val searchVariants = titleVariants.take(maxSearchVariants)

        var variantIndex = 0

        var cfSearchRetried = false

        searchLoop@ while (variantIndex < searchVariants.size) {

            val elapsedNow = searchElapsedMs()

            if (elapsedNow > searchTimeBudgetMs) {

                Log.w(TAG, "[${api.name}] Arama zaman bütçesi doldu (${elapsedNow}ms) — kalan varyantlar atlandı")

                CsTrace.warn(api.name, "search", "Arama zaman bütçesi doldu (${elapsedNow} ms) — ${searchVariants.size - variantIndex} varyant atlandı")

                break

            }

            val batch = searchVariants.drop(variantIndex).take(3)

            variantIndex += batch.size

            val batchResults = kotlinx.coroutines.coroutineScope {

                batch.map { variant -> async { variant to safeSearch(api, variant) } }.awaitAll()

            }

            // Bu partinin kuyruk bekleme süresini bütçe sayacından düş (kaydırmalı pencere).

            run {

                val now = searchQueueWaitSnapshot()

                queuedSearchWaitMs += now - queueSnapshot

                queueSnapshot = now

            }

            for ((variant, variantResults) in batchResults) {

                if (variantResults.isNotEmpty()) {

                    if (searchedVariant.isBlank()) searchedVariant = variant

                    Log.d(TAG, "[${api.name}] ✓ '$variant' için ${variantResults.size} sonuç bulundu")

                    for (result in variantResults) {

                        val key = "${result.type}:${result.url.trim().lowercase(Locale.ROOT)}"

                        collectedResults.putIfAbsent(key, result)

                    }

                }

            }

            // Detay sayfası İzle hattı eskiden searchWithCfRetry'yi HİÇ kullanmıyordu

            // (yalnızca eklenti arama UI'sı kullanıyordu). İlk batch 15 sn'de CF'ye

            // çarpıp boş dönünce kalan varyantlar da aynı duvara tosluyordu.

            if (collectedResults.isEmpty() && !cfSearchRetried) {

                cfSearchRetried = true

                val host = hostOf(api)

                val cfHit = host != null && (

                    CloudflareKiller.wasChallenged(host) ||

                        CloudflareKiller.isSolvingHost(host) ||

                        isBotProtectionChallenge(CsPluginStatusTracker.getErrorMessage(api.name) ?: "")

                    )

                if (cfHit) {

                    searchTimeBudgetMs = maxOf(searchTimeBudgetMs, 60_000L)

                    Log.w(TAG, "[${api.name}] İlk arama boş + CF kanıtı — gate + sabırlı yeniden deneme")

                    CsTrace.warn(api.name, "search", "İlk batch boş + CF kanıtı → gate + retry")

                    val retryQuery = batch.firstOrNull() ?: searchVariants.firstOrNull() ?: title

                    val retryResults = searchWithCfRetry(api, retryQuery)

                    if (retryResults.isNotEmpty()) {

                        searchedVariant = "$retryQuery (cf-retry)"

                        for (result in retryResults) {

                            val key = "${result.type}:${result.url.trim().lowercase(Locale.ROOT)}"

                            collectedResults.putIfAbsent(key, result)

                        }

                    }

                }

            }

            if (collectedResults.isNotEmpty()) {

                val bestSoFar = findBestMatch(

                    collectedResults.values.toList(), title, effectiveAlts, year, season, episode, isMovie

                )

                if (bestSoFar != null && CsTitleMatcher.isHighConfidenceMatch(

                        bestSoFar, title, effectiveAlts, year, season, isMovie

                    )

                ) {

                    Log.d(TAG, "[${api.name}] High-confidence match found; remaining queries skipped")

                    break@searchLoop

                }

                // Makul bir aday varsa ve zaman kutusunun yarısı geçtiyse kalan sorguları

                // boşuna deneme — bütçenin kalanını load/extract aşamasına bırak.

                if (bestSoFar != null && searchElapsedMs() > searchTimeBudgetMs / 2) {

                    Log.d(TAG, "[${api.name}] Zaman kutusu içinde makul eşleşme bulundu — kalan sorgular atlandı")

                    break@searchLoop

                }

                if (collectedResults.size >= 40) break@searchLoop

            }

        }

        var results = collectedResults.values.toList()



        // SON ÇARE (domain): Arama hiç sonuç vermediyse ve domaini YERLEŞİK tablo yüzünden

        // değiştirdiysek, eklentinin kendi domainiyle bir kez daha dene. Tablo eskimiş olabilir.

        if (results.isEmpty() && revertForcedDomain(api)) {

            for (variant in titleVariants) {

                results = safeSearch(api, variant)

                if (results.isNotEmpty()) {

                    searchedVariant = variant

                    Log.d(TAG, "[${api.name}] ✓ Özgün domainle '${variant}' için ${results.size} sonuç bulundu")

                    break

                }

            }

        }



        // SON ÇARE 1.5 (sezon girdisi araması — YALNIZCA diziler): Çıplak başlık araması

        // hiçbir sonuç vermediyse bazı siteler sezonları AYRI girdiler olarak indeksler

        // ("X 2. Sezon" sitede bağımsız bir eserdir). Bu aşama asla birincil arama değildir;

        // yalnızca çıplak arama boş kaldığında devreye giren kaynak-bağımsız güvenlik ağıdır.

        // Filmlerde sezon mantığı olmadığından bu aşama tamamen atlanır.

        if (results.isEmpty() && isMovie != true && season > 1 &&

            searchElapsedMs() < searchTimeBudgetMs

        ) {

            val seasonVariants = buildSeasonScopedVariants(title, effectiveAlts, season)

            Log.d(TAG, "[${api.name}] Sezon girdisi araması (${seasonVariants.size} varyant): ${seasonVariants.take(4)}")

            CsTrace.info(api.name, "season-search", "Çıplak arama boş → ${seasonVariants.size} sezon varyantı deneniyor")

            var seasonVariantIndex = 0

            seasonSearch@ while (seasonVariantIndex < seasonVariants.size) {

                if (searchElapsedMs() > searchTimeBudgetMs) break@seasonSearch

                val batch = seasonVariants.drop(seasonVariantIndex).take(3)

                seasonVariantIndex += batch.size

                val batchResults = kotlinx.coroutines.coroutineScope {

                    batch.map { v -> async { v to safeSearch(api, v) } }.awaitAll()

                }

                for ((v, vr) in batchResults) {

                    if (vr.isNotEmpty()) {

                        results = vr

                        searchedVariant = "$v (sezon girdisi)"

                        Log.d(TAG, "[${api.name}] ✓ Sezon girdisi araması '$v' → ${vr.size} sonuç")

                        break@seasonSearch

                    }

                }

            }

        }



        // SON ÇARE 2 (geniş arama): Tüm varyantlar boş döndüyse, başlığın anlamlı

        // kelimeleriyle (ilk 2-3 kelime) daha geniş bir arama dene. Türkçe siteler

        // bazen tamamen farklı başlıklar kullanır (örn. "Örümcek-Adam" yerine "Spider-Man"

        // veya tam tersi). Bu fallback, jenerik ama anlamlı kelimelerle arama yapar.

        // Zaman kutusu dolduysa denenmez — kalan bütçe load/extract için saklanır.

        if (results.isEmpty() && searchElapsedMs() < searchTimeBudgetMs) {

            val broadQueries = buildBroadSearchQueries(title, effectiveAlts)

            Log.d(TAG, "[${api.name}] Geniş arama deneniyor (${broadQueries.size} sorgu): $broadQueries")

            for (broadQuery in broadQueries) {

                val broadResults = safeSearch(api, broadQuery)

                if (broadResults.isNotEmpty()) {

                    // Geniş aramada bulunan sonuçları direkt döndür — findBestMatch

                    // zaten yanlış eşleşmeleri 걸 eer (MIN_MATCH_SCORE threshold).

                    results = broadResults

                    searchedVariant = "$broadQuery (geniş)"

                    Log.d(TAG, "[${api.name}] ✓ Geniş arama '$broadQuery' için ${broadResults.size} sonuç buldu")

                    break

                }

            }

        }



        if (results.isEmpty()) {

            Log.w(TAG, "[${api.name}] ✗ Arama sonucu yok: ${titleVariants.size} varyant denendi; koruma türü ayrıca doğrulanmadıkça bilinmiyor.")

            Log.e(SERR, "🔎 ARAMA SIFIR [${api.name}] — title='$title' S${season}E${episode} — ${titleVariants.size} varyantta sonuç yok; bu tek başına CF/WAF kanıtı değildir.")

            CsTrace.warn(api.name, "search", "ARAMA SIFIR: ${titleVariants.size} varyantın hiçbiri sonuç döndürmedi (ilk varyantlar: ${titleVariants.take(4)}); CF/WAF yalnız açık imzayla sınıflandırılır")

            // ÖNEMLİ: recordSkip'ten ÖNCE oku — recordSkip errorMessages'ın üzerine yazar.

            val lastErr = CsPluginStatusTracker.getErrorMessage(api.name)

            // Boş arama CF kanıtı değildir. Yalnızca açık challenge/WAF imzasında doğrulama açılır.

            if (lastErr == null) {

                CsPluginStatusTracker.recordSkip(api.name, "Arama sonucu yok (${titleVariants.size} varyant denendi); erişim/boş sonuç nedeni doğrulanamadı")

            }

            if (lastErr != null && isBotProtectionChallenge(lastErr)) {

                Log.e(SERR, "🔐 Açık bot-koruma imzası [${api.name}] — Son hata: $lastErr")

                throw CloudflareBlockException(

                    "🔐 Bot-koruma doğrulaması tespit edildi. Doğrulama gerekiyorsa açılan sayfayı tamamlayın. (${api.name})"

                )

            }

            return emptyList()

        }



        Log.d(TAG, "[${api.name}] Arama sonuçları ('$searchedVariant' için ${results.size} adet):")

        CsTrace.info(api.name, "search", "'$searchedVariant' → ${results.size} aday; ilk 5: ${results.take(5).map { it.name }}")

        results.take(5).forEachIndexed { i, r -> Log.d(TAG, "  [$i] '${r.name}' → ${r.url}") }



        // Match title aliases together with requested media kind, year, season, and episode.

        val bestMatch = findBestMatch(results, title, effectiveAlts, year, season, episode, isMovie)



        val bestMatchScore = if (bestMatch != null) {

            getBestTitleSimilarity(bestMatch.name, title, effectiveAlts, isMovie)

        } else 0.0

        val skipIdValidation = bestMatch != null && CsTitleMatcher.isHighConfidenceMatch(

            bestMatch, title, effectiveAlts, year, season, isMovie

        )

        if (skipIdValidation && bestMatch != null) {

            Log.d(TAG, "[ID-Mapping] ⚡ High-confidence title/type/year/season match (${"%.2f".format(bestMatchScore)}); ID validation skipped for '${bestMatch.name}'.")

        }



        // Validate bestMatch using ID mapping if target IDs are available

        var validatedMatch: SearchResponse? = null

        var bestLoadResponse: LoadResponse? = null



        if (!skipIdValidation && resolvedIds != null && (targetImdb != null || targetTmdb != null || targetMal != null || targetAniList != null || targetKitsu != null)) {

            Log.d(TAG, "[ID-Mapping] Validating candidates using resolved IDs: IMDb=$targetImdb TMDB=$targetTmdb MAL=$targetMal AniList=$targetAniList Kitsu=$targetKitsu")

            

            var isSyncDataSupported = true



            // 1. Try validating the best match first

            if (bestMatch != null) {

                val resp = safeLoad(api, bestMatch.url)

                if (resp != null) {

                    isSyncDataSupported = hasSyncDataSupport(resp)

                    if (isSyncDataSupported) {

                        if (loadResponseMatches(resp, targetImdb, targetMal, targetAniList, targetTmdb, targetKitsu)) {

                            Log.d(TAG, "[ID-Mapping] ✓ Best match '${bestMatch.name}' validated successfully via ID syncData.")

                            validatedMatch = bestMatch

                            bestLoadResponse = resp

                        } else {

                            Log.w(TAG, "[ID-Mapping] ✗ Best match '${bestMatch.name}' failed ID validation.")

                        }

                    } else {

                        // Eklentide syncData desteği yoksa, doğrudan bu sonucu eşleşme olarak kabul et (Türkçe eklentiler için fallback)

                        Log.d(TAG, "[ID-Mapping] ! '${bestMatch.name}' has no syncData support, falling back to title similarity.")

                        validatedMatch = bestMatch

                        bestLoadResponse = resp

                    }

                }

            }



            // 2. If best match failed ID validation (or was null), try other candidates (skip if WebView/slow plugin to prevent timeout)

            if (validatedMatch == null && !isWebViewPlugin && isSyncDataSupported) {

                // Sort candidates by simple similarity score so we check the most promising ones first

                val candidates = results.filter {

                    it != bestMatch && CsTitleMatcher.isCandidateMetadataCompatible(

                        it, title, effectiveAlts, year, season, episode, isMovie

                    )

                }

                    .map { r ->

                        val score = getBestTitleSimilarity(r.name, title, effectiveAlts, isMovie)

                        Pair(r, score)

                    }

                    .sortedByDescending { it.second }

                    .take(3) // check top 3 alternatives at most to prevent high network overhead



                for ((candidate, score) in candidates) {

                    if (score < 0.45) continue // don't load unrelated candidates, even if a provider has sync data

                    Log.d(TAG, "[ID-Mapping] Checking candidate '${candidate.name}' (similarity: $score)...")

                    val resp = safeLoad(api, candidate.url)

                    if (resp != null) {

                        if (!hasSyncDataSupport(resp)) {

                            Log.d(TAG, "[ID-Mapping] Plugin ${api.name} does not support syncData. Using candidate directly.")

                            validatedMatch = candidate

                            bestLoadResponse = resp

                            break

                        }

                        if (loadResponseMatches(resp, targetImdb, targetMal, targetAniList, targetTmdb, targetKitsu)) {

                            Log.d(TAG, "[ID-Mapping] ✓ Candidate '${candidate.name}' matches target IDs! Using it.")

                            validatedMatch = candidate

                            bestLoadResponse = resp

                            break

                        }

                    }

                }

            }

        }



        // Final match selection

        val finalMatch = validatedMatch ?: bestMatch

        if (finalMatch == null) {

            Log.w(TAG, "[${api.name}] No title/type/year/season result passed the matching threshold; refusing unrelated fallback results.")

            val topCandidates = results

                .map { r -> Pair(r.name, getBestTitleSimilarity(r.name, title, effectiveAlts, isMovie)) }

                .sortedByDescending { pair -> pair.second }

                .take(5)

                .joinToString(separator = ", ") { pair -> "'" + pair.first + "'=" + String.format("%.2f", pair.second) }

            CsTrace.warn(api.name, "match", "EŞLEŞME YOK. Hedef='$title' S${season}E${episode} movie=$isMovie yıl=$year. En iyi adaylar: $topCandidates")

            return emptyList()

        }



        Log.d(TAG, "[${api.name}] ✓ Eşleşme: '${finalMatch.name}' → ${finalMatch.url}")

        CsTrace.info(api.name, "match", "Eşleşme: '${finalMatch.name}' skor=${"%.2f".format(getBestTitleSimilarity(finalMatch.name, title, effectiveAlts, isMovie))}")

        

        // If we already loaded the correct LoadResponse, reuse it instead of reloading!

        // Film ve dizi mekanikleri AYRI boru hatlarıdır — dispatcher türüne göre yönlendirir.

        val extractedStreams = loadAndExtractStreams(

            api = api,

            match = finalMatch,

            season = season,

            episode = episode,

            isMovie = isMovie == true,

            partialSink = partialSink,

            preloaded = if (bestLoadResponse != null && finalMatch == validatedMatch) bestLoadResponse else null,

            title = title,

            alternativeTitles = effectiveAlts,

            year = year

        )

        // Ortak küme öğrenmesi: arama+eşleme ile kanıtlanan içerik URL'si, eserin

        // tüm kimlik anahtarlarına yazılır. Aynı esere başka katalog kaynağından

        // (veya aynı kaynaktan tekrar) girildiğinde arama/eşleme halkaları

        // atlanır ve bu URL doğrudan load edilir.

        if (extractedStreams.isNotEmpty()) {

            CsProviderUrlMemory.remember(identityAliases, api.name, finalMatch.url)

        }

        return extractedStreams

    }



    /**

     * ── KAYNAK-BAĞIMSIZ BORU HATTI DİSPATCHER'I ───────────────────────────────

     * Film ve dizi/anime içerikleri için AYRI video veri çekme mekaniklerini

     * yönlendirir. Her iki mekanik de aynı ilkeyi izler:

     *   başlık ara → içerik sayfasına gir (load) → video verilerini çek (loadLinks)

     * fark yalnızca ortadaki adımdadır:

     *   - [extractMovieStreams]: filmler sezon mantığıyla depolanmaz; eşleşen

     *     içerik sayfasının kendisi video sayfasıdır.

     *   - [extractSeriesStreams]: dizi/anime/sezonlu içerik; yüklenen sayfadan

     *     sezon+bölüm eşleşmesi yapılır, hedef sezon sayfada yoksa eklentinin

     *     kendi aramasıyla sezon sayfasına gezinilir.

     *

     * @param preloaded Doğrulama aşamasında zaten yüklenmiş LoadResponse (çift load önleme).

     * @param title     Sezon sayfası gezinmesi için eser adı (doğrudan-URL modunda match.name).

     */

    private suspend fun loadAndExtractStreams(

        api: MainAPI,

        match: SearchResponse,

        season: Int,

        episode: Int,

        isMovie: Boolean = false,

        partialSink: MutableList<StreamSource>? = null,

        preloaded: LoadResponse? = null,

        title: String? = null,

        alternativeTitles: List<String> = emptyList(),

        year: Int? = null

    ): List<StreamSource> {

        val loadResponse = preloaded ?: safeLoad(api, match.url) ?: run {

            Log.w(TAG, "[${api.name}] safeLoad null döndü: ${match.url}")

            // safeLoad exception yolunda zaten recordFailure yapar; yalnızca timeout vb.

            // mesajsız başarısızlıklarda sebep yaz (var olan spesifik hatanın üzerine yazma).

            if (CsPluginStatusTracker.getErrorMessage(api.name) == null) {

                CsPluginStatusTracker.recordSkip(api.name, "Detay sayfası yüklenemedi (load null — timeout olabilir)")

            }

            return emptyList()

        }



        // Boru hattı seçiminde yalnızca bayrağa değil, yüklenen sayfanın GERÇEK tipine

        // bakılır: film bayrağıyla gelinse bile kaynak içeriği bölümlü dizi girdisi olarak

        // döndürüyorsa (Türkçe sitelerde çok yaygın: film "tek bölüm" olarak saklanır)

        // dizi mekaniği işletilir — S1E1 eşleşmesi filmi doğru yakalar. Tersi yönde

        // (dizi bayrağı + boş bölüm listesi) dizi borusu zaten sayfa-URL davranışına

        // zarifçe düşer (sayfa-tipli plugin).

        val responseIsEpisodic = (loadResponse is AnimeLoadResponse || loadResponse is TvSeriesLoadResponse) &&

            episodeCountOf(loadResponse) > 0

        if (isMovie != responseIsEpisodic) {

            Log.d(TAG, "[${api.name}] Boru hattı sayfa tipine göre düzeltildi: bayrak=${if (isMovie) "film" else "dizi"}, sayfa=${if (responseIsEpisodic) "bölümlü" else "bölümsüz"}")

        }

        return if (isMovie && !responseIsEpisodic) {

            extractMovieStreams(api, loadResponse, partialSink)

        } else {

            extractSeriesStreams(

                api = api,

                loadResponse = loadResponse,

                season = season,

                episode = episode,

                partialSink = partialSink,

                title = title ?: match.name,

                alternativeTitles = alternativeTitles,

                year = year

            )

        }

    }



    /**

     * ── FİLM MEKANİĞİ ────────────────────────────────────────────────────────

     * Filmler sezon/bölüm mantığıyla depolanmaz: eşleşen içerik sayfası doğrudan

     * video sayfasıdır. Sorguya sezon/bölüm karışmaz, bölüm eşleştirme yapılmaz;

     * sayfa verisi (dataUrl / url) doğrudan loadLinks'e verilir.

     */

    private suspend fun extractMovieStreams(

        api: MainAPI,

        loadResponse: LoadResponse,

        partialSink: MutableList<StreamSource>?

    ): List<StreamSource> {

        val movieData = when (loadResponse) {

            is MovieLoadResponse -> loadResponse.dataUrl.takeIf { !it.isNullOrBlank() } ?: loadResponse.url

            else -> loadResponse.url.takeIf { !it.isNullOrBlank() }

        }

        if (movieData.isNullOrBlank()) {

            Log.w(TAG, "[${api.name}] 🎬 Film mekaniği: sayfa URL'si boş (${loadResponse.javaClass.simpleName})")

            CsTrace.warn(api.name, "movie", "Film sayfası url/dataUrl boş (${loadResponse.javaClass.simpleName})")

            CsPluginStatusTracker.recordSkip(api.name, "Film sayfası çözülemedi (url/dataUrl boş)")

            return emptyList()

        }

        Log.d(TAG, "[${api.name}] 🎬 Film mekaniği: içerik sayfası → loadLinks ($movieData)")

        CsTrace.info(api.name, "movie", "İçerik sayfası video verisi için kullanılıyor")

        return extractStreamsFromEpisode(api, loadResponse, movieData, partialSink)

    }



    /**

     * ── DİZİ/ANİME MEKANİĞİ ──────────────────────────────────────────────────

     * Doğru akış: başlık ara → içerik sayfası → SEZON+BÖLÜM sayfası → video verisi.

     *

     * 1. Yüklenen sayfanın bölüm listesinden hedef S/E seçilir (CsEpisodeMatcher).

     * 2. Eklenti bölüm listesi doldurmuyorsa (sayfa-tipli plugin; powerDizi/XPrime

     *    tarzı) sayfa URL'si bölüm kapsayıcısı olarak kullanılır.

     * 3. Bölüm listesi DOLU ama hedef sezon/bölüm yoksa site sezonu ayrı sayfada

     *    tutuyordur: [navigateToSeasonPage] eklentinin kendi aramasıyla sezon

     *    sayfasına iner (kaynak-bağımsız genel sezona-inme adımı).

     *

     * ESKİ DAVRANIŞ (HATA): bölüm listesi dolu olup eşleşme yoksa loadLinks'e

     * DİZİ sayfasının URL'si veriliyordu — video verisi asla dizi sayfasından

     * gelmez; 25 sn'lik loadLinks bütçesi çöpe gidiyor ve kart "akış bulunamadı"

     * diyordu. Artık bu durumda gerçek sebep yazılır ve sezon gezinmesi denenir.

     */

    private suspend fun extractSeriesStreams(

        api: MainAPI,

        loadResponse: LoadResponse,

        season: Int,

        episode: Int,

        partialSink: MutableList<StreamSource>?,

        title: String,

        alternativeTitles: List<String>,

        year: Int?

    ): List<StreamSource> {

        // ── Sezon çelişkisi denetimi ────────────────────────────────────────────

        // Kullanıcı raporu (2026-10-12): Watch2Movies'te S1E1 istenirken yanlış sezon/bölüm

        // oynatılabiliyordu. Arama bazen sitenin BAŞKA sezonunun sayfasını döndürür

        // ("X 2. Sezon"); eski kod bu sayfadan sezon-bağımsız eşleşmeyle bir bölüm seçip

        // yanlış içeriği oynatıyordu. Artık önce doğru sezon sayfasına inilir.

        val pageSeason = CsEpisodeMatcher.pageSeasonOf(loadResponse)

        // EpisodeMatcher applies the same evidence rules as its direct S/E matching, including

        // explicit episode labels and ambiguous default season=1 metadata.

        val seasonConflict = CsEpisodeMatcher.hasSeasonConflict(loadResponse, season)

        if (pageSeason != null && pageSeason != season && !seasonConflict) {

            Log.d(

                TAG,

                "[${api.name}] 📺 Sayfa başlığı S$pageSeason diyor ama bölüm verisi S$season içeriyor — çelişki yok sayıldı."

            )

        }

        if (seasonConflict) {

            Log.w(

                TAG,

                "[${api.name}] 📺 Sayfa S$pageSeason sezonuna ait ama S$season istendi — doğrudan bölüm eşleşmesi atlanıyor."

            )

            CsTrace.warn(api.name, "episode", "Sayfa S$pageSeason, istenen S$season — sezon gezinmesi öncelikli")

        } else {

            val matchedStreams = extractStreamsForEpisodeDataCandidates(

                api = api,

                loadResponse = loadResponse,

                season = season,

                episode = episode,

                partialSink = partialSink

            )

            if (matchedStreams != null) return matchedStreams

        }



        val epCount = episodeCountOf(loadResponse)

        if (epCount == 0 && CsEpisodeMatcher.canUsePageUrlFallback(loadResponse, season)) {

            val pageUrl = loadResponse.url.takeIf { !it.isNullOrBlank() }

            if (pageUrl == null) {

                Log.w(TAG, "[${api.name}] 📺 Bölüm listesi boş ve sayfa URL'si de boş (${loadResponse.javaClass.simpleName})")

                CsTrace.warn(api.name, "episode", "Bölüm listesi boş, url boş (${loadResponse.javaClass.simpleName})")

                CsPluginStatusTracker.recordSkip(api.name, "S${season}E${episode} çözülemedi (bölüm listesi ve url boş)")

                return emptyList()

            }

            Log.w(TAG, "[${api.name}] 📺 Bölüm listesi boş — sayfa-tipli plugin (powerDizi/XPrime tarzı): URL fallback $pageUrl")

            CsTrace.info(api.name, "episode", "Bölüm listesi boş; sayfa URL'si bölüm kapsayıcısı olarak kullanılıyor")

            return extractStreamsFromEpisode(api, loadResponse, pageUrl, partialSink)

        }



        // Bölüm listesi DOLU ama hedef S/E yok (ya da sayfa yanlış sezona ait) → sezon sayfasına in.

        // Gezinme sorguları için en güvenilir başlık, yüklenen sayfanın KENDİ adıdır

        // (doğrudan-URL modunda match.name eklenti adı olabilir).

        val navTitle = loadResponse.name.takeIf { !it.isNullOrBlank() } ?: title

        Log.w(TAG, "[${api.name}] 📺 S${season}E${episode} yüklenen sayfada yok (${epCount} bölüm) — sezon sayfasına geziniliyor")

        CsTrace.warn(api.name, "episode", "S${season}E${episode} ${epCount} bölüm içinde yok — sezon gezinmesi ('$navTitle')")

        val navigated = withTimeoutOrNull(20_000L) {

            navigateToSeasonPage(api, loadResponse, season, episode, navTitle, alternativeTitles, year)

        }

        if (navigated != null) {

            val navigatedStreams = extractStreamsForEpisodeDataCandidates(

                api = api,

                loadResponse = navigated.second,

                season = season,

                episode = episode,

                partialSink = partialSink

            )

            if (navigatedStreams != null) return navigatedStreams

            return extractStreamsFromEpisode(api, navigated.second, navigated.first, partialSink)

        }



        CsPluginStatusTracker.recordSkip(

            api.name,

            if (seasonConflict) {

                "S${season}E${episode} için doğru sezon sayfası bulunamadı (yüklenen sayfa S$pageSeason'a aitti) — yanlış sezon oynatılmadı"

            } else {

                "S${season}E${episode} bulunamadı — sezon sayfası gezinmesi de sonuç vermedi (yüklenen sayfada ${epCount} bölüm vardı)"

            }

        )

        return emptyList()

    }



    /** Loads every distinct DubStatus-specific episode payload for the selected S/E. */

    private suspend fun extractStreamsForEpisodeDataCandidates(

        api: MainAPI,

        loadResponse: LoadResponse,

        season: Int,

        episode: Int,

        partialSink: MutableList<StreamSource>?

    ): List<StreamSource>? {

        val episodeDataCandidates = CsEpisodeMatcher.findEpisodeDataCandidates(

            response = loadResponse,

            season = season,

            episode = episode

        )

        if (episodeDataCandidates.isEmpty()) return null



        Log.d(

            TAG,

            "[${api.name}] 📺 S${season}E${episode} için ${episodeDataCandidates.size} episode-data/audio seçeneği bulundu"

        )

        val candidateStreams = mutableListOf<StreamSource>()

        episodeDataCandidates.forEachIndexed { index, episodeData ->

            val audioKind = CsEpisodeMatcher.findDubStatusForEpisodeData(loadResponse, episodeData)

            Log.d(

                TAG,

                "[${api.name}] episode-data ${index + 1}/${episodeDataCandidates.size} " +

                    "(ses=${audioKind ?: "bilinmiyor"}) yükleniyor"

            )

            candidateStreams += extractStreamsFromEpisode(

                api = api,

                loadResponse = loadResponse,

                episodeData = episodeData,

                partialSink = partialSink

            )

        }

        return candidateStreams

    }



    /**

     * Genel sezon sayfası gezinmesi: CloudStream API'sinde sezon gezinme metodu

     * yoktur; sitenin o sezona ait sayfasına ulaşmanın kaynak-bağımsız tek yolu

     * eklentinin KENDİ search+load akışını kullanmaktır (Türkçe/yabancı siteler

     * sezonları sıklıkla ayrı girdi olarak indeksler: "X 2. Sezon").

     *

     * @return (bölüm verisi, sezon sayfası LoadResponse) ikilisi; başarısızsa null.

     */

    private suspend fun navigateToSeasonPage(

        api: MainAPI,

        current: LoadResponse,

        season: Int,

        episode: Int,

        title: String,

        alternativeTitles: List<String>,

        year: Int?

    ): Pair<String, LoadResponse>? {

        val queries = buildSeasonScopedVariants(title, alternativeTitles, season).take(4)

        if (queries.isEmpty()) return null

        for (query in queries) {

            val results = safeSearch(api, query)

            if (results.isEmpty()) continue

            val candidates = results.filter { it.url != current.url }

            if (candidates.isEmpty()) continue

            val match = findBestMatch(candidates, title, alternativeTitles, year, season, episode, false)

                ?: continue

            val resp = safeLoad(api, match.url) ?: continue

            val data = findEpisodeData(resp, season, episode) ?: continue

            Log.d(TAG, "[${api.name}] ⤷ Sezon gezinmesi başarılı: '$query' → '${match.name}' → S${season}E${episode}")

            CsTrace.info(api.name, "season-nav", "'$query' → ${match.url}")

            return data to resp

        }

        CsTrace.warn(api.name, "season-nav", "${queries.size} sezon sorgusu da sezon sayfasına inemedi")

        return null

    }



    /** LoadResponse'un doldurduğu bölüm sayısı (0 = sayfa-tipli plugin). */

    private fun episodeCountOf(response: LoadResponse): Int = when (response) {

        is AnimeLoadResponse -> response.episodes.values.sumOf { it.size }

        is TvSeriesLoadResponse -> response.episodes.size

        else -> 0

    }



    /**

     * Plugin'in native getLoadUrl() metodunu kullanarak direkt URL çözümü dener.

     * Plugin `supportedSyncNames` listesinde MAL/AniList/TMDB/IMDb ID'lerinden birini

     * destekliyorsa, getLoadUrl() metodunu çağırarak search+match adımlarını atlar.

     *

     * @return Başarılı olursa stream listesi, getLoadUrl desteklenmiyor veya başarısız ise null

     */

    private suspend fun tryNativeIdResolution(

        api: MainAPI,

        malId: Int?,

        aniListId: Int?,

        tmdbId: Int?,

        imdbId: String?,

        kitsuId: Int?,

        simklId: Int?,

        season: Int,

        episode: Int,

        isMovie: Boolean = false,

        mediaType: MediaType? = null,

        partialSink: MutableList<StreamSource>? = null,

        identityAliases: List<String> = emptyList()

    ): List<StreamSource>? {

        if (api.supportedSyncNames.isEmpty()) return null



        // Build candidate (syncName → idString) pairs in priority order

        val syncCandidates = buildList {

            if (com.lagradost.cloudstream3.syncproviders.SyncIdName.MyAnimeList in api.supportedSyncNames && malId != null && malId > 0) {

                add(com.lagradost.cloudstream3.syncproviders.SyncIdName.MyAnimeList to malId.toString())

            }

            if (com.lagradost.cloudstream3.syncproviders.SyncIdName.Anilist in api.supportedSyncNames && aniListId != null && aniListId > 0) {

                add(com.lagradost.cloudstream3.syncproviders.SyncIdName.Anilist to aniListId.toString())

            }

            if (com.lagradost.cloudstream3.syncproviders.SyncIdName.Imdb in api.supportedSyncNames && !imdbId.isNullOrBlank()) {

                add(com.lagradost.cloudstream3.syncproviders.SyncIdName.Imdb to imdbId)

            }

            if (com.lagradost.cloudstream3.syncproviders.SyncIdName.Kitsu in api.supportedSyncNames && kitsuId != null && kitsuId > 0) {

                add(com.lagradost.cloudstream3.syncproviders.SyncIdName.Kitsu to kitsuId.toString())

            }

            if (com.lagradost.cloudstream3.syncproviders.SyncIdName.Simkl in api.supportedSyncNames && simklId != null && simklId > 0) {

                add(com.lagradost.cloudstream3.syncproviders.SyncIdName.Simkl to simklId.toString())

            }

            // TMDB and Bangumi are not in SyncIdName enum; only plugins that advertise them

            // through their own namespace can use those IDs via the manifest-backed Stremio path.

        }



        if (syncCandidates.isEmpty()) return null



        for ((syncName, syncId) in syncCandidates) {

            try {

                Log.d(TAG, "[${api.name}] getLoadUrl($syncName, $syncId) deneniyor...")

                val loadUrl = withTimeoutOrNull(15_000L) {

                    api.getLoadUrl(syncName, syncId)

                }

                if (!loadUrl.isNullOrBlank()) {

                    Log.d(TAG, "[${api.name}] ✓ getLoadUrl başarılı: $loadUrl")

                    val effectiveType = mediaType ?: if (isMovie) MediaType.Movie else MediaType.Anime

                    val providerType = when (effectiveType) {

                        MediaType.Movie -> com.lagradost.cloudstream3.TvType.Movie

                        MediaType.TvShow -> com.lagradost.cloudstream3.TvType.TvSeries

                        MediaType.Anime, MediaType.Manga -> com.lagradost.cloudstream3.TvType.Anime

                    }

                    val resolvedIsMovie = effectiveType == MediaType.Movie

                    val fakeSearchResponse = api.newAnimeSearchResponse(

                        name = api.name,

                        url  = loadUrl,

                        type = providerType,

                        fix  = false

                    )

                    val extracted = loadAndExtractStreams(api, fakeSearchResponse, season, episode, resolvedIsMovie, partialSink)

                    // Ortak küme öğrenmesi: native ID ile kanıtlanan URL, eserin tüm

                    // kimlik anahtarlarına yazılır — bir sonraki sefer hangi katalog

                    // kaynağından girilirse girilsin hafıza yolu bunu bulur.

                    if (extracted.isNotEmpty()) {

                        CsProviderUrlMemory.remember(identityAliases, api.name, loadUrl)

                    }

                    return extracted

                } else {

                    Log.d(TAG, "[${api.name}] getLoadUrl($syncName, $syncId) → null (desteklenmiyor veya bulunamadı)")

                }

            } catch (cancel: kotlinx.coroutines.CancellationException) {

                throw cancel

            } catch (e: Throwable) {

                Log.w(TAG, "[${api.name}] getLoadUrl($syncName, $syncId) HATA: ${e.javaClass.simpleName}: ${e.message}")

            }

        }

        return null // Hiçbir sync ID çalışmadı — normal arama akışına dön

    }

    /**

     * URL açıkça doğrudan medya mı? (uzantı veya bilinen akış işareti ile)

     *

     * Bu kontrol "kaynağı doğrudan ekle" ile "embed boru hattına gönder" kararını verir.

     * Yanlış negatif (medyayı embed sanmak) yalnızca biraz yavaşlatır; yanlış pozitif

     * (HTML sayfasını medya sanmak) ise oynatıcı hatası üretir — bu yüzden kontrol dardır.

     */

    /**

     * Signed media URLs from Drive/OK.ru often have no `.mp4` suffix. These

     * hosts are emitted only after the plugin's own metadata/media resolver,

     * so treating their CDN endpoint as a page would cause a needless HTML

     * fetch and lose the source's headers.

     */

    internal fun isKnownExtensionlessMediaUrl(url: String): Boolean {

        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false

        val host = uri.host?.lowercase(Locale.ROOT) ?: return false

        val path = uri.path.orEmpty().lowercase(Locale.ROOT)

        return when {

            host == "googlevideo.com" || host.endsWith(".googlevideo.com") ->

                path.contains("videoplayback")

            host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ->

                !path.contains("/file/") && !path.contains("/preview")

            host == "okcdn.ru" || host.endsWith(".okcdn.ru") ->

                !path.contains("/videoembed") && !path.contains("/player")

            host == "mycdn.me" || host.endsWith(".mycdn.me") ->

                !path.contains("/videoembed") && !path.contains("/player")

            host == "anizmplayer.com" || host.endsWith(".anizmplayer.com") ->

                path.contains("/cdn/hls/") || path.contains("/hls/")

            host == "sistenn.uns.bio" ||

                host == "anizm.rpmvid.com" ||

                host == "anizm.strp2p.com" ->

                path.contains("/hls/") || path.contains("/hlsmod/") || path.contains("/v4/")

            else -> false

        }

    }



    internal fun isClearlyDirectMediaUrl(url: String): Boolean {

        // SendVid's `/<id>.mp4` is often an HTML/player route, not a media

        // response. Route it through the normal embed resolver first so the

        // page can publish the real URL and required hotlink headers.

        if (com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(url)) {

            return false

        }

        val lower = url.lowercase(Locale.ROOT)

        val path = try {

            java.net.URI(lower).path ?: lower

        } catch (_: Exception) {

            lower.substringBefore('?')

        }.trimEnd('/')



        if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd") ||

            path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".flv") ||

            path.endsWith(".m4v") || path.endsWith(".mov") ||

            path.endsWith("master.txt") || path.endsWith("playlist.txt")

        ) return true



        // Uzantı sorgu/path içinde: /x.mp4/index.m3u8, ?file=abc.mp4 gibi

        return lower.contains(".m3u8?") || lower.contains(".mp4?") || lower.contains(".mpd?") ||

            lower.contains(".m3u8/") || lower.contains(".mp4/") ||

            isKnownExtensionlessMediaUrl(url)

    }



    /**

     * URL'nin bir embed/iframe video oynatıcı sayfası olup olmadığını kontrol eder.

     * Doğrudan .mp4/.m3u8/.mpd dosyaları veya bilinen video akışları false döndürür.

     */

    internal fun isEmbedUrl(url: String): Boolean {

        val clean = resolveHrefLi(url).lowercase(Locale.ROOT)

        // A SendVid page may be named `<id>.mp4`; the suffix alone must not

        // make the HTML page look like a progressive media file.

        if (com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(clean)) {

            return true

        }

        val path = try {

            java.net.URI(clean).path?.lowercase(Locale.ROOT) ?: ""

        } catch (_: Exception) {

            clean.substringBefore('?').lowercase(Locale.ROOT)

        }.trimEnd('/')

        val isDirectMedia = path.endsWith(".m3u8") ||

                            path.endsWith(".mp4") ||

                            path.endsWith(".mpd") ||

                            path.endsWith(".mkv") ||

                            path.endsWith(".avi") ||

                            path.endsWith(".webm") ||

                            path.endsWith("master.txt") ||

                            path.endsWith("playlist.txt") ||

                            path.contains(".m3u8/") ||

                            path.contains(".mp4/")

        if (isDirectMedia && !clean.contains("href.li/?")) {

            return false

        }

        return clean.contains("vk.com/video") ||

               clean.contains("vkvideo.ru") ||

               clean.contains("sibnet") ||

               // VidMoly — ana alan + tüm CDN subdomainleri (ww1.vidmoly.to, ww547.vidmoly.to, vmnow.online vb.)

               clean.contains("vidmoly") ||

               clean.contains("vmnow.online") ||      // VidMoly yeni CDN alt alanı (SezonlukDizi)

               clean.contains("filemoon") ||

               clean.contains("fmoonembed") ||        // Filemoon embed CDN

               clean.contains("ok.ru") ||

               clean.contains("odnoklassniki") ||

               clean.contains("streamtape") ||

               clean.contains("streamwish") ||

               clean.contains("swdyu") ||

               clean.contains("sfastwish") ||

               clean.contains("wishfast") ||

               clean.contains("dood") ||

               clean.contains("playmogo") ||          // Doodstream redirect

               clean.contains("ds2play") ||

               clean.contains("mixdrop") ||

               com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper.isVoeUrl(clean) ||

               clean.contains("vidplay") ||

               clean.contains("vidoy") ||             // Vidplay redirect

               clean.contains("uqload") ||            // Uqload redirect

               clean.contains("dropload") ||          // Dropload redirect

               clean.contains("vtube") ||             // Vtube redirect

               clean.contains("rapidrame") ||         // Rapidrame CDN (HDFilmCehennemi)

               clean.contains("hdplayersystem") ||    // DiziMom CDN player

               clean.contains("pichive.online") ||    // FourPichive (AsyaWatch, Dizilla)

               clean.contains("pichive.cc") ||        // Pichive CDN alternatif

               clean.contains("sssrr.org") ||         // Abyss CDN (AsyaAnimeleri)

               clean.contains("molystream.org") ||    // Molystream (DiziBox)

               clean.contains("streambox.xyz") ||     // Streambox CDN (DDizi)

               clean.contains("turkanime.tv/embed") || // TurkAnime AES embed player

               clean.contains("turkanime.co/embed") || // TurkAnime AES embed player (eski domain)

               clean.contains("closeload.top") ||     // CloseLoad (Türkiye CDN)

               clean.contains("closeload.filmmakinesi.to") || // FilmMakinesi CloseLoad (upstream v60)

               clean.contains("gstore.one") ||        // GStore (Türkiye CDN)

               clean.contains("trstx.org") ||         // TRsTX (Türkiye CDN)

               clean.contains("chillx.top") ||        // Chillx embed

               clean.contains("boosterx.stream") ||   // Boosterx embed

               clean.contains("kwik.si") ||           // Kwik (GogoAnime)

               clean.contains("kwik.cx") ||           // Kwik (GogoAnime alt)

               clean.contains("megacloud.tv") ||      // MegaCloud (HiAnime)

               clean.contains("rapidcloud.cc") ||     // Rapidcloud

               clean.contains("vudeo.net") ||         // Vudeo (Asyalog)

               clean.contains("vudeo.org") ||         // Vudeo mirror

               clean.contains("wishembed.pro") ||     // StreamWish alt (Asyalog "Wish" player)

               clean.contains("awish.pro") ||         // StreamWish alt

               clean.contains("alions.pro") ||        // AlionsPlayer (yeni Türkiye CDN)

               clean.contains("alions.net") ||        // AlionsPlayer alt domain

               clean.contains("alions.tv") ||         // AlionsPlayer alt domain

               clean.contains("lionscdn.pro") ||      // LionsCDN (AlionsPlayer yedek)

               clean.contains("lionscdn.com") ||      // LionsCDN alt domain

               // Daha fazla Türkiye CDN pattern'leri

               clean.contains("vidmoly.net") ||       // VidMoly alt domain

               clean.contains("vidmoly.me") ||        // VidMoly alt domain

               clean.contains("vidmoly.to") ||        // VidMoly ana domain

               clean.contains("trstx.com") ||         // TRsTX alt domain

               clean.contains("trstx.net") ||         // TRsTX alt domain

               clean.contains("closeload.com") ||     // CloseLoad alt domain

               clean.contains("closeload.site") ||    // CloseLoad alt domain

               clean.contains("hdplayersystem.com") || // DiziMom CDN player alt

               clean.contains("pichive.com") ||       // Pichive CDN alt domain

               clean.contains("pichive.net") ||       // Pichive CDN alt domain

               clean.contains("rapidrame.com") ||     // Rapidrame CDN alt domain

               clean.contains("vslecter.com") ||      // Vslecter (Türkiye CDN)

               clean.contains("videmo.com") ||        // Videmo (Türkiye CDN)

               clean.contains("sendvid.com") ||       // SendVid embed

               clean.contains("sendvid.net") ||       // SendVid embed alt

               clean.contains("fembed.com") ||        // Fembed alt domain

               clean.contains("fembed.net") ||        // Fembed alt domain

               clean.contains("streamango.com") ||    // Streamango (eski, hala kullanılan)

               clean.contains("yourupload.com") ||    // YourUpload embed

               clean.contains("drive.google.com") ||   // Google Drive preview/download

               clean.contains("docs.google.com") ||    // Google Docs/Drive embed

               clean.contains("drive.usercontent.google.com") ||

               clean.contains("jawcloud.co") ||       // JawCloud embed

               clean.contains("fastplay.to") ||       // FastPlay (Türkiye CDN)

               clean.contains("fastplay.cc") ||       // FastPlay alt domain

               clean.contains("uqload.is") ||         // Uqload yeni domain

               clean.contains("uqload.to") ||         // Uqload alt domain

               clean.contains("doodstream.com") ||    // Doodstream yeni domain

               clean.contains("dood.watch") ||        // Doodstream alt domain

               clean.contains("dood.re") ||           // Doodstream alt domain

               clean.contains("mixdrop.to") ||        // Mixdrop alt domain

               clean.contains("mixdrop.sx") ||        // Mixdrop alt domain

               clean.contains("streamtape.com") ||    // StreamTape alt domain

               clean.contains("streamtape.net") ||    // StreamTape alt domain

               clean.contains("filemoon.sx") ||       // Filemoon alt domain

               clean.contains("filemoon.to") ||       // Filemoon alt domain

               clean.contains("filemoon.in") ||       // Filemoon alt domain

               clean.contains("vidguard.to") ||       // VidGuard (Türkiye CDN)

               clean.contains("vidguard.net") ||      // VidGuard alt domain

               clean.contains("turbovid.net") ||      // TurboVid (Türkiye CDN)

               clean.contains("turbovid.to") ||       // TurboVid alt domain

               clean.contains("turboviplay.com") ||   // TurboViPlay (Türkiye CDN)

               clean.contains("hdvid.tv") ||          // HDVid (Türkiye CDN)

               clean.contains("hdvid.net") ||         // HDVid alt domain

               clean.contains("playtube.site") ||     // PlayTube (Türkiye CDN)

               clean.contains("vidhide.com") ||       // VidHide (Türkiye CDN)

               clean.contains("vidhidepro.com") ||    // VidHide Pro (Türkiye CDN)

               clean.contains("upstream.to") ||       // Upstream alt domain

               clean.contains("embedy.cc") ||         // Embedy (Türkiye CDN)

               clean.contains("embedy.net") ||        // Embedy alt domain

               clean.contains("embed") ||

               clean.contains("shell.php") ||

               clean.contains("video_ext.php") ||

               clean.contains("player.php") ||

               clean.contains("/player/") ||

               clean.contains("/embed/") ||

               clean.contains("/e/") ||              // Kısa embed path (Türk CDN'ler)

               clean.contains("/v/") ||              // Kısa video path (Türk CDN'ler)

               url.contains("href.li/?")

    }





    /** Derin tarama için indirilen sayfa: içerik + Content-Type. */

    internal data class EmbedPageFetch(val body: String?, val contentType: String?)



    /**

     * Embed/oynatıcı sayfasını indirir (derin tarama için).

     * Zaman aşımı ve boyut sınırı uygulanır; hata durumunda (null, null) döner (akış bozulmaz).

     */

    private suspend fun fetchEmbedPage(

        url: String,

        referer: String,

        providerName: String,

        requestHeaders: Map<String, String> = emptyMap()

    ): EmbedPageFetch = try {

        val pageHeaders = requestHeaders.toMutableMap()

        val pageReferer = pageHeaders.entries

            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }

            ?.value

            ?.takeIf { it.isNotBlank() }

            ?: referer

        if (pageHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {

            pageHeaders["Referer"] = pageReferer

        }

        if (pageHeaders.keys.none { it.equals("Range", ignoreCase = true) }) {

            pageHeaders["Range"] = "bytes=0-1048575"

        }

        if (pageHeaders.keys.none { it.equals("Accept", ignoreCase = true) }) {

            pageHeaders["Accept"] = "*/*"

        }

        val response = withTimeoutOrNull(EMBED_HTML_TIMEOUT_MS) {

            com.lagradost.cloudstream3.app.get(

                url = url,

                referer = pageReferer,

                // Gövdeyi sınırla: MP4 dönen uzantısız bir CDN'de 1 MiB'den fazlasını

                // indirmek anlamsızdır (bellek/veri tasarrufu). Sunucu Range'i yok sayarsa

                // yine de MAX_EMBED_HTML_CHARS sınırı uygulanır.

                headers = pageHeaders

            )

        }

        if (response == null) {

            Log.d(TAG, "[$providerName] Derin tarama için sayfa zaman aşımı: $url")

            EmbedPageFetch(null, null)

        } else {

            val contentType = runCatching {

                response.okhttpResponse.header("Content-Type")

            }.getOrNull()

            val body = runCatching { response.text }.getOrNull()

            val capped = body?.let {

                if (it.length > MAX_EMBED_HTML_CHARS) it.substring(0, MAX_EMBED_HTML_CHARS) else it

            }

            if (capped.isNullOrBlank()) {

                Log.d(TAG, "[$providerName] Derin tarama için sayfa içeriği alınamadı: $url")

            }

            EmbedPageFetch(capped, contentType)

        }

    } catch (e: Throwable) {

        if (e is kotlinx.coroutines.CancellationException) throw e

        Log.d(TAG, "[$providerName] Derin tarama sayfa indirme hatası: ${e.javaClass.simpleName}: ${e.message}")

        EmbedPageFetch(null, null)

    }



    /**

     * Yanıt gövdesi zaten medya mı? (uzantısız `.m3u8`/`.mp4` sunan CDN'ler)

     * Oynatıcıya HTML göndermeyi engellemenin ve uzantısız gerçek akışları kurtarmanın yolu.

     */

    private fun isMediaContentType(contentType: String?): Boolean {

        val ct = contentType?.lowercase(Locale.ROOT)?.trim() ?: return false

        return ct.startsWith("video/") ||

            ct.startsWith("audio/") ||

            ct.contains("mpegurl") ||

            ct.contains("dash+xml") ||

            ct.contains("application/octet-stream") ||

            ct.contains("application/vnd.apple") ||

            ct.contains("application/x-mpeg")

    }



    /** Gövde bir HTML sayfasına benziyor mu? */

    private fun looksLikeHtmlDocument(body: String?): Boolean {

        if (body.isNullOrBlank()) return false

        val head = body.take(4096).lowercase(Locale.ROOT)

        return head.contains("<!doctype html") || head.contains("<html") ||

            head.contains("<head") || head.contains("<body") || head.contains("<div") ||

            head.contains("<meta") || head.contains("<script")

    }



    /**

     * Bir sayfa/akış adresinin origin'ini (`scheme://host[:port]`) döndürür.

     * Çözümlenemezse null.

     */

    internal fun originOfUrl(url: String?): String? {

        if (url.isNullOrBlank()) return null

        return try {

            val uri = java.net.URI(url.trim())

            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null

            val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "https"

            val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""

            "$scheme://$host$port"

        } catch (_: Exception) {

            null

        }

    }



    /**

     * Embed/oynatıcı sayfasının origin'ini `Origin` + `Referer` olarak başlıklara işler.

     *

     * NEDEN (2026-10-18 kök neden): Türkçe CDN'ler (playmix.uno / closeload / rapidrame)

     * isteği **embed sayfasının origin'ine** göre doğruluyor. Uygulama bu başlığı

     * doldurmadığında CDN isteği reddediyordu — FilmMakinesi eklentisinin kendi kaydı:

     * "Origin embed sunucusuna sabit; eksik kalınca uygulama onu playmix.uno yapıyor ve

     * yayın 5004 ile düşüyordu." Sonuç: kaynak listeleniyor ama oynatıcı hata veriyordu.

     *

     * Kural: çağıranın açıkça verdiği `Origin`/`Referer` ASLA ezilmez; yalnızca eksikse

     * embed origin'i ile doldurulur.

     */

    internal fun applyEmbedOriginHeaders(

        headers: MutableMap<String, String>,

        embedPageUrl: String?

    ): MutableMap<String, String> {

        val origin = originOfUrl(embedPageUrl) ?: return headers

        if (!headers.keys.any { it.equals("origin", ignoreCase = true) }) {

            headers["Origin"] = origin

        }

        if (!headers.keys.any { it.equals("referer", ignoreCase = true) }) {

            headers["Referer"] = "$origin/"

        }

        return headers

    }



    /**

     * Combines inherited provider headers with extractor-specific headers. Header names are

     * case-insensitive; extractor values replace inherited values without creating duplicate

     * `Referer`, `Origin`, `Cookie`, or user-agent fields.

     */

    internal fun mergeHeadersCaseInsensitive(

        inherited: Map<String, String>,

        extractor: Map<String, String>

    ): MutableMap<String, String> {

        val merged = linkedMapOf<String, String>()

        fun putHeader(name: String, value: String) {

            merged.keys.filter { it.equals(name, ignoreCase = true) }.forEach { key -> merged.remove(key) }

            merged[name] = value

        }

        inherited.forEach { (name, value) -> putHeader(name, value) }

        extractor.forEach { (name, value) -> putHeader(name, value) }

        return merged

    }



    /**

     * Completes request metadata without replacing any explicit plugin/extractor header.

     *

     * The embed or episode page is a better fallback than the final CDN host: FilmMakinesi /

     * HDFilmCehennemi-style players can validate both Referer and Origin against that page.

     * Only when no page context exists do we fall back to the media URL's origin.

     */

    internal fun buildCloudStreamRequestHeaders(

        existingHeaders: Map<String, String>,

        linkReferer: String?,

        parentPageUrl: String?,

        sourcePageUrl: String?,

        mediaUrl: String?,

        userAgent: String

    ): MutableMap<String, String> {

        val headers = mergeHeadersCaseInsensitive(emptyMap(), existingHeaders)

        fun hasHeader(name: String) = headers.keys.any { it.equals(name, ignoreCase = true) }

        fun headerValue(name: String) = headers.entries

            .firstOrNull { it.key.equals(name, ignoreCase = true) }

            ?.value



        val pageReferer = listOf(linkReferer, parentPageUrl, sourcePageUrl)

            .firstOrNull { !originOfUrl(it).isNullOrBlank() }

        val mediaOrigin = originOfUrl(mediaUrl)

        if (!hasHeader("Referer")) {

            val referer = pageReferer ?: mediaOrigin?.let { "$it/" }

            if (!referer.isNullOrBlank()) headers["Referer"] = referer

        }

        if (!hasHeader("Origin")) {

            val origin = originOfUrl(headerValue("Referer"))

                ?: originOfUrl(pageReferer)

                ?: mediaOrigin

            if (!origin.isNullOrBlank()) headers["Origin"] = origin

        }

        if (!hasHeader("User-Agent")) headers["User-Agent"] = userAgent

        return headers

    }



    /** Builds a probe request carrying the same auth/origin headers the player will use. */

    internal fun buildStreamValidationRequest(

        url: String,

        method: String,

        headers: Map<String, String>,

        userAgent: String

    ): okhttp3.Request {

        require(method.equals("HEAD", ignoreCase = true) || method.equals("GET", ignoreCase = true)) {

            "Only HEAD and GET validation requests are supported"

        }

        val builder = okhttp3.Request.Builder().url(url).method(method.uppercase(Locale.ROOT), null)

        headers.forEach { (name, value) ->

            if (value.isNotBlank() && !name.equals("Range", ignoreCase = true)) {

                builder.header(name, value)

            }

        }

        if (!headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {

            builder.header("User-Agent", userAgent)

        }

        if (method.equals("GET", ignoreCase = true)) {

            builder.header("Range", "bytes=0-0")

        }

        return builder.build()

    }



    /**

     * Derin taramada bulunan medya adayını hafif bir HTTP isteğiyle doğrular.

     *

     * Oynatıcıya HTML sayfası göndermek "oynatıcı hata veriyor" şikâyetinin en büyük

     * kaynağıdır; bu yüzden aday önce doğrulanır. Kararsız durumlar (ağ hatası, 405/403)

     * "canlı" kabul edilir — yanlış negatif, yanlış pozitiften daha maliyetlidir.

     */

    private suspend fun verifyMediaCandidate(

        url: String,

        pageUrl: String,

        baseHeaders: Map<String, String> = emptyMap()

    ): Boolean {

        mediaProbeCache[url]?.let { return it }



        val alive = withTimeoutOrNull(MEDIA_PROBE_TIMEOUT_MS) {

            try {

                val requestReferer = baseHeaders.entries

                    .firstOrNull { it.key.equals("Referer", ignoreCase = true) }

                    ?.value

                    ?.takeIf { it.isNotBlank() }

                    ?: pageUrl

                val requestOrigin = baseHeaders.entries

                    .firstOrNull { it.key.equals("Origin", ignoreCase = true) }

                    ?.value

                    ?.takeIf { it.isNotBlank() }

                    ?: originOfUrl(requestReferer)

                val requestUserAgent = baseHeaders.entries

                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }

                    ?.value

                    ?.takeIf { it.isNotBlank() }

                    ?: com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                val requestAccept = baseHeaders.entries

                    .firstOrNull { it.key.equals("Accept", ignoreCase = true) }

                    ?.value

                    ?.takeIf { it.isNotBlank() }

                    ?: "*/*"

                val requestBuilder = okhttp3.Request.Builder()

                    .url(url)

                    .method("HEAD", null)

                    .addHeader("User-Agent", requestUserAgent)

                    .addHeader("Referer", requestReferer)

                    // CDN'ler embed origin'ini de doğruluyor; eksikse 5004/403 dönüyor ve

                    // çalışan aday "ölü" sayılıp kaynak listesi boş kalıyordu.

                    .apply { requestOrigin?.let { addHeader("Origin", it) } }

                    .addHeader("Accept", requestAccept)

                baseHeaders.forEach { (key, value) ->

                    if (value.isNotBlank() &&

                        !key.equals("User-Agent", ignoreCase = true) &&

                        !key.equals("Referer", ignoreCase = true) &&

                        !key.equals("Origin", ignoreCase = true) &&

                        !key.equals("Accept", ignoreCase = true)

                    ) {

                        requestBuilder.addHeader(key, value)

                    }

                }

                val request = requestBuilder.build()



                val response = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

                    .newCall(request).execute()

                val code = response.code

                val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT)

                response.close()



                when {

                    code == 405 || code == 501 -> true      // HEAD desteklenmiyor → GET ile oynar

                    code == 403 -> true                     // CDN imza/referer kontrolü; oynatıcıda denenmeye değer

                    code in 200..299 -> {

                        contentType == null ||

                            contentType.isBlank() ||

                            contentType.contains("video") ||

                            contentType.contains("mpegurl") ||

                            contentType.contains("octet-stream") ||

                            contentType.contains("dash") ||

                            contentType.contains("text/plain") ||

                            contentType.contains("mpeg")

                    }

                    code == 404 || code == 410 -> false

                    code in 400..499 -> false

                    else -> true

                }

            } catch (e: Exception) {

                // Ağ hatası (DNS/TLS/timeout) URL'in ölü olduğunu göstermez.

                Log.d(TAG, "Aday doğrulama ağ hatası (${e.javaClass.simpleName}): ${url.take(80)}")

                true

            }

        } ?: true



        mediaProbeCache[url] = alive

        return alive

    }



    internal fun resolveHrefLi(url: String): String {

        var target = if (url.contains("href.li/?", ignoreCase = true)) {

            val lowerUrl = url.lowercase(Locale.ROOT)

            val idx = lowerUrl.indexOf("href.li/?")

            val resolved = url.substring(idx + "href.li/?".length)

            Log.d(TAG, "[HrefLi] href.li resolved: $url → $resolved")

            resolved

        } else {

            url

        }

        

        var fixed = target

        for ((oldDomain, newDomain) in KNOWN_DOMAIN_FIXES) {

            if (fixed.contains(oldDomain)) {

                fixed = fixed.replace(oldDomain, newDomain)

            }

        }

        fixed = fixed.replace("www.www.", "www.")

        

        if (fixed != target) {

            Log.d(TAG, "[DomainFix] URL domain fixed: $target → $fixed")

        }

        return fixed

    }



    /**

     * LoadResponse nesnesinden posterUrl alanını reflection ile okur.

     * Cloudstream API sürümleri arasında alan adı farklılık gösterebileceğinden

     * birden fazla olası isim denenir.

     */

    private fun extractPosterUrl(response: LoadResponse): String? {

        val candidates = listOf("posterUrl", "poster", "posterimage", "coverImage", "coverUrl", "backgroundPosterUrl")

        for (fieldName in candidates) {

            try {

                var clazz: Class<*>? = response.javaClass

                while (clazz != null) {

                    try {

                        val field = clazz.getDeclaredField(fieldName)

                        field.isAccessible = true

                        val value = field.get(response)

                        if (value is String && value.isNotBlank()) return value

                    } catch (_: NoSuchFieldException) {}

                    clazz = clazz.superclass

                }

            } catch (_: Exception) {}

        }

        return null

    }



    /**

     * Embed URL'yi (VK, Sibnet, Vidmoly, Filemoon, Okru, StreamWish vb.) CS3 loadExtractor sistemiyle çözer.

     * CS3 ExtractorApi'leri gerçek .mp4/.m3u8 URL'lerini ve gerekli HTTP başlıklarını döndürür.

     *

     * @OptIn Prerelease: AesHelper.cryptoAESHandler TurkAnime AES-128 şifreli embed URL'leri için kullanılıyor.

     */

    /**

     * Fallback resolver for a raw Google Drive URL emitted by a plugin.

     *

     * A successful result is always a progressive URL published by Drive's

     * playback response or a real hidden-form download target. A preview page,

     * warning page, `confirm=t` guess, or a 404 is never returned as a stream.

     */

    private suspend fun resolveGoogleDriveFallback(

        providerName: String,

        url: String,

        linkName: String,

        rawHeaders: Map<String, String>,

        referer: String,

        thumbnailUrl: String?

    ): List<StreamSource> {

        val fileId = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper.fileId(url)

            ?: return emptyList()

        val driveHeaders = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

            .mediaHeaders()

            .toMutableMap()

        rawHeaders.forEach { (key, value) ->

            if (value.isNotBlank() && driveHeaders.keys.none { it.equals(key, ignoreCase = true) }) {

                driveHeaders[key] = value

            }

        }

        if (driveHeaders.keys.none { it.equals("referer", ignoreCase = true) } && referer.isNotBlank()) {

            driveHeaders["Referer"] = referer

        }

        val driveReferer = driveHeaders.entries

            .firstOrNull { it.key.equals("referer", ignoreCase = true) }

            ?.value

            ?.takeIf { it.isNotBlank() }

            ?: "https://drive.google.com/"



        return withTimeoutOrNull(18_000L) {

            try {

                fun streamFor(downloadUrl: String): StreamSource = StreamSource(

                    addonName = providerName,

                    name = "$providerName • $linkName",

                    title = linkName,

                    url = downloadUrl,

                    infoHash = null,

                    fileIndex = null,

                    requestHeaders = driveHeaders,

                    isCS = true,

                    quality = null,

                    qualityValue = null,

                    subtitles = emptyList(),

                    thumbnailUrl = thumbnailUrl,

                    isAdultContent = ADULT_PLUGINS.contains(providerName)

                )



                // The plugin may already have resolved Drive's warning page to a

                // real /download or /uc URL. Do not throw that verified candidate

                // away and restart from only its file id: quotas, cookies and

                // resource keys are part of the original URL. Probe the candidate

                // first and only fall back to the playback API when it is HTML.

                if (com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

                        .isDriveDownloadUrl(url)

                ) {

                    val direct = fetchEmbedPage(

                        url = url,

                        referer = driveReferer,

                        providerName = providerName,

                        requestHeaders = driveHeaders

                    )

                    if (isMediaContentType(direct.contentType)) {

                        return@withTimeoutOrNull listOf(streamFor(url))

                    }

                    val formUrl = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

                        .downloadUrl(direct.body.orEmpty())

                    if (!formUrl.isNullOrBlank() && formUrl != url) {

                        val confirmed = fetchEmbedPage(

                            url = formUrl,

                            referer = driveReferer,

                            providerName = providerName,

                            requestHeaders = driveHeaders

                        )

                        if (isMediaContentType(confirmed.contentType)) {

                            return@withTimeoutOrNull listOf(streamFor(formUrl))

                        }

                    }

                }



                val preview = runCatching {

                    com.lagradost.cloudstream3.app.get(

                        "https://drive.google.com/file/d/$fileId/preview",

                        headers = driveHeaders

                    ).text

                }.getOrDefault("")

                val keys = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

                    .playbackApiKeys(preview)

                for (key in keys) {

                    val body = runCatching {

                        com.lagradost.cloudstream3.app.get(

                            "https://content-workspacevideo-pa.googleapis.com/v1/drive/media/$fileId/playback" +

                                "?key=$key&auditContext=forDisplay",

                            headers = driveHeaders

                        ).text

                    }.getOrNull() ?: continue

                    val playback = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

                        .playbackStreams(body)

                    if (playback.isNotEmpty()) {

                        return@withTimeoutOrNull playback.map { item ->

                            val quality = item.height.takeIf { it > 0 }

                            StreamSource(

                                addonName = providerName,

                                name = "$providerName • $linkName" + (quality?.let { " ${it}p" } ?: ""),

                                title = linkName,

                                url = item.url,

                                infoHash = null,

                                fileIndex = null,

                                requestHeaders = driveHeaders,

                                isCS = true,

                                quality = quality?.let { "${it}p" },

                                qualityValue = quality,

                                subtitles = emptyList(),

                                thumbnailUrl = thumbnailUrl,

                                isAdultContent = ADULT_PLUGINS.contains(providerName)

                            )

                        }

                    }

                }



                val warning = runCatching {

                    com.lagradost.cloudstream3.app.get(

                        "https://drive.usercontent.google.com/download?id=$fileId&export=download",

                        headers = driveHeaders

                    ).text

                }.getOrDefault("")

                val download = com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper

                    .downloadUrl(warning)

                if (download != null) {

                    return@withTimeoutOrNull listOf(

                        StreamSource(

                            addonName = providerName,

                            name = "$providerName • $linkName",

                            title = linkName,

                            url = download,

                            infoHash = null,

                            fileIndex = null,

                            requestHeaders = driveHeaders,

                            isCS = true,

                            quality = null,

                            qualityValue = null,

                            subtitles = emptyList(),

                            thumbnailUrl = thumbnailUrl,

                            isAdultContent = ADULT_PLUGINS.contains(providerName)

                        )

                    )

                }



                // `confirm=t` alone is not trusted: it usually returns another

                // warning HTML page. It is used only as a bounded probe, and is

                // published if the response itself is a media Content-Type.

                val confirmed = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"

                val probe = fetchEmbedPage(

                    confirmed,

                    driveHeaders["Referer"] ?: "https://drive.google.com/",

                    providerName,

                    requestHeaders = driveHeaders

                )

                if (isMediaContentType(probe.contentType)) {

                    listOf(

                        StreamSource(

                            addonName = providerName,

                            name = "$providerName • $linkName",

                            title = linkName,

                            url = confirmed,

                            infoHash = null,

                            fileIndex = null,

                            requestHeaders = driveHeaders,

                            isCS = true,

                            quality = null,

                            qualityValue = null,

                            subtitles = emptyList(),

                            thumbnailUrl = thumbnailUrl,

                            isAdultContent = ADULT_PLUGINS.contains(providerName)

                        )

                    )

                } else {

                    emptyList()

                }

            } catch (cancel: kotlinx.coroutines.CancellationException) {

                throw cancel

            } catch (e: Throwable) {

                Log.w(TAG, "[$providerName] Google Drive fallback hatası: ${e.message}")

                emptyList()

            }

        } ?: emptyList()

    }



    @OptIn(Prerelease::class)

    internal suspend fun resolveEmbedUrl(

        providerName: String,

        rawUrl: String,

        rawLinkName: String,

        rawHeaders: Map<String, String>,

        referer: String,

        subtitleCallback: (SubtitleInput) -> Unit,

        thumbnailUrl: String? = null,

        /** iframe / AES zinciri derinliği — sonsuz döngüyü engeller (en fazla [MAX_EMBED_DEPTH]). */

        depth: Int = 0

    ): List<StreamSource> {

        if (depth > MAX_EMBED_DEPTH) {

            Log.w(TAG, "[$providerName] Embed zinciri çok derin ($depth) — durduruldu: $rawUrl")

            return emptyList()

        }

        val resolvedUrl = resolveHrefLi(rawUrl)

        val isSendVid = com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(resolvedUrl)

        val sendVidPageUrl = if (isSendVid) {

            com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.pageUrl(resolvedUrl)

        } else {

            resolvedUrl

        }

        val pageBaseUrl = if (isSendVid) sendVidPageUrl else resolvedUrl

        // Prefer the link's explicit Referer when one exists. For SendVid the

        // page URL is authoritative because the provider's direct-looking route

        // is itself the page that must be opened.

        val suppliedReferer = rawHeaders.entries

            .firstOrNull { it.key.equals("referer", ignoreCase = true) }

            ?.value

            ?.takeIf { it.isNotBlank() }

        val extractorReferer = if (isSendVid) sendVidPageUrl else (suppliedReferer ?: referer)

        val extractorUrl = if (isSendVid) sendVidPageUrl else resolvedUrl

        Log.d(TAG, "[$providerName] Embed URL çözümleniyor (derinlik=$depth): $resolvedUrl")



        // Google Drive'ın preview/uyarı HTML'ini Media3'e vermek yerine, burada

        // yalnızca Drive'ın kendi playback API'si veya kendi formunun yayınladığı

        // gerçek download URL'sini kabul et. Anizm ve TrAnimeIzle bunu eklenti

        // içinde de yapar; bu uygulama fallback'i eski/başka eklentilerden gelen

        // çıplak Drive kaynaklarını aynı güvenli zincire alır.

        if (com.kitsugi.animelist.data.cloudstream.extractors.GoogleDriveWrapper.isDriveUrl(resolvedUrl)) {

            val driveStreams = resolveGoogleDriveFallback(

                providerName = providerName,

                url = resolvedUrl,

                linkName = rawLinkName,

                rawHeaders = rawHeaders,

                referer = extractorReferer,

                thumbnailUrl = thumbnailUrl

            )

            if (driveStreams.isNotEmpty()) {

                embedResolveListener?.onEmbedAttempt(

                    providerName = providerName,

                    rawUrl = rawUrl,

                    resolved = true,

                    resolvedUrl = driveStreams.firstOrNull()?.url,

                    error = null

                )

                return driveStreams

            }

            val message = "Google Drive: playback/download URL alınamadı (dosya silinmiş, kota veya erişim engeli olabilir)"

            embedResolveListener?.onEmbedAttempt(

                providerName = providerName,

                rawUrl = rawUrl,

                resolved = false,

                resolvedUrl = null,

                error = message

            )

            Log.w(TAG, "[$providerName] $message: $resolvedUrl")

            return emptyList()

        }



        // ── TurkAnime AES-128 intercept ──────────────────────────────────────

        // turkanime.tv/embed/#/url/<BASE64(AES_encrypted_CDN_url)> formatını

        // orijinal plugin'in kendi iframe2AesLink() metoduyla aynı mantıkla çözer.

        val isTurkAnimeEmbed = resolvedUrl.contains("turkanime.tv/embed", ignoreCase = true) ||

                               resolvedUrl.contains("turkanime.co/embed", ignoreCase = true)

        if (isTurkAnimeEmbed) {

            Log.d(TAG, "[$providerName] TurkAnime AES embed tespit edildi — çözülüyor…")

            try {

                val aesData = resolvedUrl

                    .substringAfter("embed/#/url/")

                    .substringBefore("?status")

                    .substringBefore("#")

                val base64Decoded = String(Base64.decode(aesData, Base64.DEFAULT))

                val aesKey = "710^8A@3@>T2}#zN5xK?kR7KNKb@-A!LzYL5~M1qU0UfdWsZoBm4UUat%}ueUv6E--*hDPPbH7K2bp9^3o41hw,khL:}Kx8080@M"

                val decrypted = AesHelper.cryptoAESHandler(base64Decoded, aesKey.toByteArray(), false)

                    ?.replace("\\", "")

                    ?.replace("\"", "")

                    ?.trim()

                if (!decrypted.isNullOrBlank() && (decrypted.startsWith("http://") || decrypted.startsWith("https://"))) {

                    Log.i(TAG, "[$providerName] TurkAnime AES çözüldü → $decrypted")

                    // Çözülen URL'yi tekrar normal embed pipeline'ından geçir

                    return resolveEmbedUrl(

                        providerName    = providerName,

                        rawUrl          = decrypted,

                        rawLinkName     = rawLinkName,

                        rawHeaders      = rawHeaders,

                        referer         = "https://www.turkanime.tv/",

                        subtitleCallback = subtitleCallback,

                        thumbnailUrl    = thumbnailUrl,

                        depth           = depth + 1

                    )

                } else {

                    Log.w(TAG, "[$providerName] TurkAnime AES çözüldü ama URL geçersiz: $decrypted")

                }

            } catch (e: Throwable) {

                if (e is kotlinx.coroutines.CancellationException) throw e

                Log.e(TAG, "[$providerName] TurkAnime AES decrypt hatası: ${e.message}")

            }

        }

        // ─────────────────────────────────────────────────────────────────────



        val resolvedStreams = mutableListOf<StreamSource>()

        var errorMsg: String? = null

        try {

            val timedOut = withTimeoutOrNull(20_000L) {

                try {

                    loadExtractor(

                        url      = extractorUrl,

                        referer  = extractorReferer,

                        subtitleCallback = { sub ->

                            subtitleCallback(

                                SubtitleInput(

                                    url  = normalizeCsSubtitleUrl(sub.url),

                                    name = sub.lang,

                                    lang = sub.langTag ?: detectLanguageCode(sub.lang),

                                    isExternal = true,

                                    headers = sub.headers?.toMap().orEmpty()

                                )

                            )

                        },

                        callback = { link ->

                            Log.d(TAG, "[$providerName] Extractor çözümlendi → ${link.name}: ${link.url}")

                            // Extractor headers supplement (and may override) the provider's

                            // original headers. Do not drop provider cookies/tokens merely because

                            // an extractor supplied one unrelated header such as User-Agent.

                            val inheritedHeaders = CsPlaybackHeaders.merge(rawHeaders, link.headers)

                            val headers = buildCloudStreamRequestHeaders(

                                existingHeaders = inheritedHeaders,

                                linkReferer = link.referer,

                                parentPageUrl = resolvedUrl,

                                sourcePageUrl = referer,

                                mediaUrl = link.url,

                                userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                            ).toMutableMap()

                            if (link.referer.isNotBlank() &&
                                CsPlaybackHeaders.value(link.headers, "Referer") == null) {
                                headers.keys.firstOrNull { it.equals("referer", ignoreCase = true) }?.let { headers.remove(it) }
                                headers["Referer"] = link.referer
                            }

                            if (isSendVid) {

                                headers["Referer"] = extractorReferer

                            }

                            if (com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper.isVoeUrl(resolvedUrl)) {

                                val voeOrigin = originOfUrl(extractorUrl)

                                if (voeOrigin != null) {

                                    headers.keys

                                        .filter { it.equals("origin", ignoreCase = true) }

                                        .toList()

                                        .forEach { headers.remove(it) }

                                    headers["Origin"] = voeOrigin

                                }

                            }

                            resolvedStreams.add(

                                StreamSource(

                                    addonName      = providerName,

                                    name           = "$providerName • ${link.name}",

                                    title          = link.name,

                                    url            = link.url,

                                    infoHash       = null,

                                    fileIndex      = null,

                                    requestHeaders = headers,

                                    isCS           = true,

                                    streamTypeHint = link.type.name,

                                    quality        = getQualityString(link.quality),

                                    qualityValue   = getQualityValue(link.quality),

                                    subtitles      = emptyList(),

                                    thumbnailUrl   = thumbnailUrl,

                                    isAdultContent = ADULT_PLUGINS.contains(providerName),

                                    mediaTransport = when (link.type) {

                                        ExtractorLinkType.M3U8 -> "hls"

                                        ExtractorLinkType.DASH -> "dash"

                                        ExtractorLinkType.VIDEO -> "progressive"

                                        else -> null

                                    },

                                    csStreamType = link.type.name,

                                    csExtractorData = link.extractorData,

                                    externalAudioUrl = link.audioTracks.firstOrNull()?.url?.takeIf { it.isNotBlank() },
                                    externalAudioTrackCount = link.audioTracks.count { it.url.isNotBlank() },
                                    providerAudioKind = if (link.audioTracks.any { it.url.isNotBlank() }) "dual" else null,

                                    externalAudioTracks = externalAudioTrackInputs(link),

                                    externalAudioSources = link.audioTracks.mapNotNull { audio ->

                                        audio.url.takeIf { it.isNotBlank() }?.let { url ->

                                            com.kitsugi.animelist.data.repository.ExternalAudioSource(url, audio.headers.orEmpty().toMap())

                                        }

                                    },

                                    videoSizeBytes = extractVideoSize(link)

                                )

                            )

                        }

                    )

                } catch (e: Throwable) {

                    if (e is kotlinx.coroutines.CancellationException) throw e

                    errorMsg = "${e.javaClass.simpleName}: ${e.message}"

                    Log.e(TAG, "[$providerName] loadExtractor HATA: $errorMsg url=${resolvedUrl.take(90)}", e)

                    try { com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(providerName, "extract", "loadExtractor HATA $errorMsg url=${resolvedUrl.take(90)}", e) } catch (_: Exception) {}

                    // HDFilmCehennemi/FilmMakinesi filtreleri icin alias tag'e de yaz

                    if (providerName.contains("HDFilm", true) || providerName.contains("FilmMakinesi", true)) {

                        val alias = if (resolvedUrl.contains("closeload", true)) "Kekik_CloseLoad" else if (resolvedUrl.contains("rapidrame", true) || resolvedUrl.contains("rplayer", true)) "Rapidrame" else "HDCH"

                        Log.e(alias, "[$providerName] $errorMsg url=${resolvedUrl.take(90)}", e)

                    }

                }

            } == null

            if (timedOut && errorMsg == null) {

                errorMsg = "Timeout: 20 seconds exceeded"

                Log.w(TAG, "[$providerName] loadExtractor 20 saniyelik zaman aşımına uğradı. url=${resolvedUrl.take(90)}")

                try { com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(providerName, "extract", "Timeout 20s url=${resolvedUrl.take(90)}") } catch (_: Exception) {}

            }

        } catch (e: Throwable) {

            if (e is kotlinx.coroutines.CancellationException) throw e

            errorMsg = "${e.javaClass.simpleName}: ${e.message}"

            Log.e(TAG, "[$providerName] loadExtractor HATA: $errorMsg url=${resolvedUrl.take(90)}", e)

            try { com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(providerName, "extract", "loadExtractor HATA $errorMsg url=${resolvedUrl.take(90)}", e) } catch (_: Exception) {}

        }



        // ── Custom Wrapper Fallback — CS3 loadExtractor başarısız olduğunda ──

        // CS3 kütüphanesindeki built-in extractor'lar bazı durumlarda boş dönebilir

        // (özellikle kütüphane versiyonu eski olduğunda). Bu durumda Kitsugi'nin

        // kendi regex-tabanlı fallback wrapper'larını dener.

        // Aşama 3 (derin tarama) aynı sayfayı yeniden indirmemek için içerik burada saklanır.

        var embedPageHtml: String? = null

        var embedPageContentType: String? = null

        if (resolvedStreams.isEmpty()) {

            try {

                // v2.4.239 fix: GdriveplayerWrapper aşağıdaki when'da olmasına rağmen

                // needsHtml listesinde YOKTU → pageContent boş kalıp fallback hiç çalışmıyordu.

                val needsHtml = com.kitsugi.animelist.data.cloudstream.extractors.GdriveplayerWrapper.isGdriveplayerUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.McloudWrapper.isMcloudUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.MixdropWrapper.isMixdropUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.Mp4UploadWrapper.isMp4UploadUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.StreamTapeWrapper.isStreamTapeUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.UpstreamWrapper.isUpstreamUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper.isVoeUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(resolvedUrl) ||

                        com.kitsugi.animelist.data.cloudstream.extractors.XStreamCdnWrapper.isXStreamCdnUrl(resolvedUrl)



                val pageContent = if (needsHtml) {

                    val pageRequestUrl = if (isSendVid) sendVidPageUrl else resolvedUrl

                    val pageReferer = if (isSendVid) "https://sendvid.com/" else extractorReferer

                    Log.d(TAG, "[$providerName] Fallback için HTML/medya yanıtı çekiliyor: $pageRequestUrl")

                    val fetched = fetchEmbedPage(

                        url = pageRequestUrl,

                        referer = pageReferer,

                        providerName = providerName,

                        requestHeaders = rawHeaders

                    )

                    embedPageContentType = fetched.contentType

                    fetched.body.orEmpty()

                } else ""

                if (pageContent.isNotBlank()) embedPageHtml = pageContent

                

                val wrapperVideos: List<com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo> = when {

                    com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(resolvedUrl) -> {

                        if (com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isTemporarilyUnavailable(pageContent)) {

                            errorMsg = "SendVid: sağlayıcı videoyu geçici olarak kullanılamaz bildiriyor"

                            CsPluginStatusTracker.recordSkip(providerName, errorMsg!!)

                            Log.w(TAG, "[$providerName] SendVid erişim durumu: This video is temporarily unavailable")

                        }

                        Log.d(TAG, "[$providerName] SendVid wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper

                            .extractFromHtml(pageContent, sendVidPageUrl)

                            .map {

                                com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(

                                    it.url, it.label, it.isM3u8, it.headers

                                )

                            }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.McloudWrapper.isMcloudUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Mcloud wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.McloudWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.MixdropWrapper.isMixdropUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Mixdrop wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.MixdropWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.Mp4UploadWrapper.isMp4UploadUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Mp4Upload wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.Mp4UploadWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.StreamTapeWrapper.isStreamTapeUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] StreamTape wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.StreamTapeWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.UpstreamWrapper.isUpstreamUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Upstream wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.UpstreamWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper.isVoeUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Voe wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.VoeWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.XStreamCdnWrapper.isXStreamCdnUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] XStreamCDN wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.XStreamCdnWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    com.kitsugi.animelist.data.cloudstream.extractors.GdriveplayerWrapper.isGdriveplayerUrl(resolvedUrl) -> {

                        Log.d(TAG, "[$providerName] Gdriveplayer wrapper fallback deneniyor...")

                        com.kitsugi.animelist.data.cloudstream.extractors.GdriveplayerWrapper

                            .extractFromHtml(pageContent, resolvedUrl)

                            .map { com.kitsugi.animelist.data.cloudstream.extractors.JWPlayerWrapper.ExtractedVideo(it.url, it.label, it.isM3u8, it.headers) }

                    }

                    else -> emptyList()

                }

                wrapperVideos.forEach { vid ->

                    if (vid.url.isNotBlank() && vid.url.startsWith("http")) {

                        val displayLabel = if (

                            rawLinkName.isBlank() || rawLinkName.equals(vid.label, ignoreCase = true)

                        ) {

                            vid.label

                        } else {

                            "$rawLinkName • ${vid.label}"

                        }

                        val wHeaders = rawHeaders.toMutableMap()

                        wHeaders.putAll(vid.headers)

                        applyEmbedOriginHeaders(

                            wHeaders,

                            if (isSendVid) sendVidPageUrl else resolvedUrl

                        )

                        if (!wHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }) {

                            wHeaders["User-Agent"] = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                        }

                        resolvedStreams.add(

                            StreamSource(

                                addonName      = providerName,

                                name           = "$providerName • $displayLabel",

                                title          = displayLabel,

                                url            = vid.url,

                                infoHash       = null,

                                fileIndex      = null,

                                requestHeaders = wHeaders,

                                isCS           = true,

                                // Kalite yalnızca extractor'ın verdiği etikette AÇIKÇA yazıyorsa kullanılır.

                                quality        = labelHeight(displayLabel)?.let { com.kitsugi.animelist.data.repository.StreamInfoResolver.heightToLabel(it) },

                                qualityValue   = labelHeight(displayLabel),

                                subtitles      = emptyList(),

                                thumbnailUrl   = thumbnailUrl,

                                isAdultContent = ADULT_PLUGINS.contains(providerName),

                                mediaTransport = if (vid.isM3u8) "hls" else "progressive"

                            )

                        )

                        Log.i(TAG, "[$providerName] ✅ Custom wrapper fallback başarılı: ${vid.url.take(80)}")

                    }

                }

            } catch (wrapErr: Throwable) {

                if (wrapErr is kotlinx.coroutines.CancellationException) throw wrapErr

                Log.w(TAG, "[$providerName] Custom wrapper fallback hatası: ${wrapErr.message}")

            }

        }

        // ─────────────────────────────────────────────────────────────────────



        // ══════════════════════════════════════════════════════════════════════

        // Aşama 3: HTML derin taraması (genel / site-bağımsız)

        // ──────────────────────────────────────────────────────────────────────

        // `loadExtractor` yalnızca kütüphaneye kayıtlı extractor'ları tanır. Türkçe

        // provider'ların kullandığı onlarca küçük CDN (Alions, CloseLoad, GStore, TRsTX,

        // Molystream, StreamBox, Pichive, vmnow, rapidrame vb.) o listede yoktur; bu yüzden

        // extractor boş döner ve eklenti "boş sonuç" gibi görünür.

        // Bu aşama sayfanın HTML/JS içeriğinden gerçek medya URL'sini çıkarır ve HTTP ile

        // doğrular. Bulunan aday "ÖLÜ KANAL" gibi etiketlenmez — gerçekten oynatılabilir.

        // ══════════════════════════════════════════════════════════════════════

        if (resolvedStreams.isEmpty()) {

            try {

                val fetched = if (embedPageHtml != null) {

                    EmbedPageFetch(embedPageHtml, embedPageContentType)

                } else {

                    fetchEmbedPage(

                        pageBaseUrl,

                        if (isSendVid) "https://sendvid.com/" else extractorReferer,

                        providerName,

                        requestHeaders = rawHeaders

                    )

                }

                val html = fetched.body



                // 3a) Yanıtın kendisi medya ise (uzantısız HLS/MP4 CDN'leri) → doğrudan kabul et.

                if (isMediaContentType(fetched.contentType)) {

                    val selfHeaders = rawHeaders.toMutableMap()

                    // Referer/Origin: sayfanın KENDİ origin'i önceliklidir (CDN bunu doğrular),

                    // provider sitesi değil.

                    applyEmbedOriginHeaders(selfHeaders, pageBaseUrl)

                    if (!selfHeaders.keys.any { it.equals("referer", ignoreCase = true) }) {

                        selfHeaders["Referer"] = referer.ifBlank { pageBaseUrl }

                    }

                    if (!selfHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }) {

                        selfHeaders["User-Agent"] =

                            com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                    }

                    resolvedStreams.add(

                        StreamSource(

                            addonName      = providerName,

                            name           = "$providerName • $rawLinkName",

                            title          = rawLinkName,

                            url            = pageBaseUrl,

                            infoHash       = null,

                            fileIndex      = null,

                            requestHeaders = selfHeaders,

                            isCS           = true,

                            quality        = null,

                            qualityValue   = null,

                            subtitles      = emptyList(),

                            thumbnailUrl   = thumbnailUrl,

                            isAdultContent = ADULT_PLUGINS.contains(providerName)

                        )

                    )

                    Log.i(

                        TAG,

                        "[$providerName] ✅ Embed URL'si kendisi medya (Content-Type=${

                            fetched.contentType?.take(60)

                        })"

                    )

                }



                if (resolvedStreams.isEmpty() && !html.isNullOrBlank() && looksLikeHtmlDocument(html)) {

                    embedPageHtml = html

                    val scan = com.kitsugi.animelist.data.cloudstream.embed.EmbedMediaScanner

                        .scan(html, pageBaseUrl)

                    Log.d(

                        TAG,

                        "[$providerName] Derin tarama: ${scan.media.size} aday, " +

                            "${scan.iframes.size} iframe, oynatıcıSayfası=${scan.looksLikePlayer}"

                    )



                    // Doğrulama PARALEL yapılır: eskiden adaylar sırayla HEAD ile deneniyordu ve

                    // her biri 6 sn'ye kadar sürebildiği için 8 adaylı bir sayfa kaynak listesini

                    // ~48 sn geciktirebiliyordu ("veri aşırı geç geliyor"). Artık en iyi skorlu

                    // adaylar aynı anda doğrulanır; kabul sırası skor sırasını korur.

                    val probes = kotlinx.coroutines.coroutineScope {

                        scan.media.take(MAX_PROBE_CANDIDATES)

                            .map { c -> async { c to verifyMediaCandidate(c.url, pageBaseUrl, rawHeaders) } }

                            .awaitAll()

                    }



                    var accepted = 0

                    for ((candidate, probeOk) in probes) {

                        if (accepted >= MAX_STREAMS_PER_EMBED) break

                        if (!probeOk) {

                            Log.d(

                                TAG,

                                "[$providerName] Aday doğrulanamadı (skor=${candidate.score}, " +

                                    "${candidate.reason}): ${candidate.url.take(110)}"

                            )

                            continue

                        }

                        val scannedHeaders = rawHeaders.toMutableMap()

                        // Adayın bulunduğu sayfanın origin'i: CDN doğrulaması buna bakıyor.

                        applyEmbedOriginHeaders(scannedHeaders, pageBaseUrl)

                        if (!scannedHeaders.keys.any { it.equals("referer", ignoreCase = true) }) {

                            scannedHeaders["Referer"] = pageBaseUrl

                        }

                        if (!scannedHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }) {

                            scannedHeaders["User-Agent"] =

                                com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                        }

                        resolvedStreams.add(

                            StreamSource(

                                addonName      = providerName,

                                name           = "$providerName • $rawLinkName",

                                title          = rawLinkName,

                                url            = candidate.url,

                                infoHash       = null,

                                fileIndex      = null,

                                requestHeaders = scannedHeaders,

                                isCS           = true,

                                // Kalite uydurulmaz; gerçek değer StreamProbe tarafından ölçülür.

                                quality        = null,

                                qualityValue   = null,

                                subtitles      = emptyList(),

                                thumbnailUrl   = thumbnailUrl,

                                isAdultContent = ADULT_PLUGINS.contains(providerName)

                            )

                        )

                        accepted++

                        Log.i(TAG, "[$providerName] ✅ Derin tarama medya buldu: ${candidate.url.take(110)}")

                    }



                    // iframe zinciri — oynatıcı bir üst katmanda olabilir (ör. provider → iframe1 → iframe2)

                    if (resolvedStreams.isEmpty() && scan.iframes.isNotEmpty()) {

                        for (iframe in scan.iframes.take(MAX_IFRAME_FOLLOW)) {

                            Log.d(TAG, "[$providerName] iframe zinciri deneniyor: ${iframe.take(120)}")

                            val nested = resolveEmbedUrl(

                                providerName     = providerName,

                                rawUrl           = iframe,

                                rawLinkName      = rawLinkName,

                                rawHeaders       = rawHeaders,

                                referer          = pageBaseUrl,

                                subtitleCallback = subtitleCallback,

                                thumbnailUrl     = thumbnailUrl,

                                depth            = depth + 1

                            )

                            if (nested.isNotEmpty()) {

                                resolvedStreams.addAll(nested)

                                break

                            }

                        }

                    }



                    // ── Aşama 4: WebView medya yakalayıcı (son çare) ───────────────

                    // Yalnızca sayfa gerçekten bir oynatıcı sayfasıysa ve JS gerektiriyorsa.

                    if (resolvedStreams.isEmpty() && scan.looksLikePlayer && enableWebViewSniffing) {

                        val sniff = com.kitsugi.animelist.data.cloudstream.embed.WebViewMediaSniffer

                            .sniff(pageBaseUrl, if (isSendVid) "https://sendvid.com/" else referer)

                        Log.d(TAG, "[$providerName] WebView sniffer → ${sniff.diagnostic}")

                        val sniffedUrl = sniff.url

                        if (sniffedUrl != null) {

                            val sniffHeaders = sniff.headers.toMutableMap()

                            applyEmbedOriginHeaders(sniffHeaders, pageBaseUrl)

                            if (!sniffHeaders.keys.any { it.equals("referer", ignoreCase = true) }) {

                                sniffHeaders["Referer"] = pageBaseUrl

                            }

                            if (!sniffHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }) {

                                sniffHeaders["User-Agent"] =

                                    com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                            }

                            resolvedStreams.add(

                                StreamSource(

                                    addonName      = providerName,

                                    name           = "$providerName • $rawLinkName",

                                    title          = rawLinkName,

                                    url            = sniffedUrl,

                                    infoHash       = null,

                                    fileIndex      = null,

                                    requestHeaders = sniffHeaders,

                                    isCS           = true,

                                    quality        = null,

                                    qualityValue   = null,

                                    subtitles      = emptyList(),

                                    thumbnailUrl   = thumbnailUrl,

                                    isAdultContent = ADULT_PLUGINS.contains(providerName)

                                )

                            )

                            Log.i(TAG, "[$providerName] ✅ WebView medya yakaladı: ${sniffedUrl.take(110)}")

                        }

                    }

                }

            } catch (deepErr: Throwable) {

                if (deepErr is kotlinx.coroutines.CancellationException) throw deepErr

                Log.w(TAG, "[$providerName] Derin embed çözümleme hatası: ${deepErr.message}")

            }

        }

        // ══════════════════════════════════════════════════════════════════════



        val resolved = resolvedStreams.isNotEmpty()

        val finalError = if (resolved) null else (errorMsg ?: "No links extracted / Host unsupported or offline")

        embedResolveListener?.onEmbedAttempt(

            providerName = providerName,

            rawUrl = rawUrl,

            resolved = resolved,

            resolvedUrl = if (resolved) resolvedStreams.firstOrNull()?.url else null,

            error = finalError

        )



        if (resolvedStreams.isEmpty()) {

            // Ölü CDN'ler için ÖLÜ KANAL fallback bile gösterme

            val isDeadCdn = KNOWN_DEAD_CDN_HOSTS.any { resolvedUrl.contains(it, ignoreCase = true) }

            if (isDeadCdn) {

                Log.w(TAG, "[$providerName] Ölü CDN — ÖLÜ KANAL fallback da atlanıyor: $rawUrl")

                return resolvedStreams // empty

            }



            // Bu noktaya gelindiyse: loadExtractor, wrapper'lar, derin tarama, Content-Type kontrolü,

            // iframe zinciri VE WebView sniffer'in hepsi başarısız oldu. Yani URL büyük olasılıkla

            // bir HTML oynatıcı sayfasıdır. Böyle bir URL oynatıcıya verildiğinde Media3

            // "UnrecognizedInputFormat / Response code" hatası üretir — kullanıcı bunu

            // "kaynak oynatılamıyor" olarak görür.

            //

            // Bu yüzden fallback artık YALNIZCA uzantısı ile medya olduğu açık olan URL'ler için

            // eklenir (ör. eklenti .m3u8 döndürdü ama extractor tanımadı).

            val rawLooksPlayable = !com.kitsugi.animelist.data.cloudstream.extractors.SendVidWrapper.isSendVidUrl(rawUrl) &&

                (isClearlyDirectMediaUrl(rawUrl) ||

                    com.kitsugi.animelist.data.cloudstream.embed.EmbedMediaScanner.looksLikeMediaUrl(rawUrl))

            if (!rawLooksPlayable) {

                Log.w(

                    TAG,

                    "[$providerName] Embed çözümlenemedi ve URL oynatılabilir medya değil — " +

                        "oynatıcıya gönderilmiyor: ${rawUrl.take(120)}"

                )

                return resolvedStreams // empty

            }



            Log.w(TAG, "[$providerName] Extractor çözemedi ama URL medya gibi görünüyor — ekleniyor: $rawUrl")

            val headers = buildCloudStreamRequestHeaders(

                existingHeaders = rawHeaders,

                linkReferer = null,

                parentPageUrl = resolvedUrl,

                sourcePageUrl = referer,

                mediaUrl = rawUrl,

                userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

            )

            resolvedStreams.add(

                StreamSource(

                    addonName    = providerName,

                    name         = "$providerName • $rawLinkName [ÖLÜ KANAL / İLETİŞİM HATASI]",

                    title        = KitsugiStrings.get(R.string.rawlinkname_olu_kanal_iletisim_hatasi, rawLinkName),

                    url          = rawUrl,

                    infoHash     = null,

                    fileIndex    = null,

                    requestHeaders = headers,

                    isCS         = true,

                    // Çözümlenemeyen kaynağın kalitesi bilinmiyor — uydurma yapılmaz.

                    quality      = null,

                    qualityValue = null,

                    subtitles    = emptyList(),

                    thumbnailUrl = thumbnailUrl,

                    isAdultContent = ADULT_PLUGINS.contains(providerName)

                )

            )

        }

        return resolvedStreams

    }



    /**

     * HEAD isteği 400 (veya benzeri belirsiz) döndüğünde GET with Range header ile

     * URL'nin gerçekten çalışıp çalışmadığını kontrol eder. Bazı CDN'ler HEAD'e

     * yanıt vermez ama GET ile ilk byte'ları döndürür.

     *

     * Sadece ilk 1 byte istenir — tam dosya indirilmez (veri tasarrufu).

     */

    private suspend fun tryGetRangeValidation(url: String, headers: Map<String, String>): Boolean {

        return try {

            // HLS/CDN kaynaklarında Origin, Cookie veya plugin token'ı HEAD'den sonraki

            // Range GET'e de taşınmalı; yalnız Referer kullanılması 403/404 false-negative

            // üreterek FilmMakinesi/HDFilmCehennemi kaynaklarını listeden düşürüyordu.

            val request = buildStreamValidationRequest(

                url = url,

                method = "GET",

                headers = headers,

                userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

            )

            val response = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

                .newCall(request).execute()

            val code = response.code

            response.close()

            val alive = when (code) {

                200, 206, 401, 403, 405, 429 -> true

                in 500..599 -> true

                404, 410 -> false

                else -> true // Belirsiz cevap oynatılabilir kaynağı tek başına elememeli.

            }

            Log.d(TAG, "GET Range doğrulama: HTTP $code → ${if (alive) "✅ canlı/belirsiz" else "❌ ölü"}: ${url.take(60)}")

            alive

        } catch (e: Exception) {

            Log.w(TAG, "GET Range doğrulama hatası: ${e.javaClass.simpleName}: ${url.take(60)}")

            // Hata = belirsiz, iyimser ol

            true

        }

    }



        private suspend fun extractStreamsFromEpisode(
        api: MainAPI,
        loadResponse: LoadResponse,
        episodeData: String,
        partialSink: MutableList<StreamSource>? = null,
        fallbackPageUrl: String? = null
    ): List<StreamSource> {

        // Kaynağa özgü kapak görseli — LoadResponse.posterUrl varsa StreamSource'a aktarılır

        val posterUrl = extractPosterUrl(loadResponse)

        Log.d(TAG, "[${api.name}] posterUrl=${posterUrl ?: "(yok)"}")



        val streams = mutableListOf<StreamSource>()

        // Embed'ler artık PARALEL çözülüyor; bu yüzden altyazı listesi eşzamanlı yazıma

        // dayanıklı olmalı (aksi hâlde ConcurrentModificationException → çökme riski).

        val subtitleList = java.util.Collections.synchronizedList(mutableListOf<SubtitleInput>())

        val pendingEmbedUrls = mutableListOf<PendingEmbedLink>()



        try {

            // Uses LOAD semaphore — completely separate from searchSemaphore, no deadlock risk

            loadSemaphore.withPermit {

                // Throttling: CF korumalı eklentilerde 500 ms bekleyip istekleri seyreltiriz.

                // Korumasız eklentilerde uzun bekleme yalnızca gecikme üretir (kullanıcı şikâyeti:

                // "veri aşırı geç geliyor") — bu yüzden yalnızca kısa bir nefes payı bırakılır.

                kotlinx.coroutines.delay(if (api.name in CF_PROTECTED_PLUGINS) 500L else 120L)

                ensurePluginReady(api)

                Log.d(TAG, "[${api.name}] loadLinks çağrılıyor...")

                

                // Wrap the loadLinks in a timeout — CF korumalı/şüpheli siteler için 120s, diğerleri 25s.

                // v2.4.239: eski davranış CF_PROTECTED için provider bütçesinin (o zaman 120s)

                // tamamını loadLinks'e veriyordu; provider bütçesi 240s'ye çıkınca loadLinks'in

                // zinciri tek başına kilitlememesi için ayrı aşama bütçesi (CF_STAGE_TIMEOUT_MS) kullanılır.

                // v2.4.258: 25 sn → 60 sn. HDFilmCehennemi / FilmMakinesi gibi eklentiler her sunucu/

                // iframe için SIRAYLA istek atıyor (video API → iframe → m3u8). 25 sn dolunca kalan

                // kaynaklar hiç gelmiyordu ("iki kaynak yerine bir tane / hiç biri" belirtisi).

                val loadLinksTimeoutMs = stageTimeoutMs(api, 60_000L)

                withTimeoutOrNull(loadLinksTimeoutMs) {

                    api.loadLinks(

                        data = episodeData,

                        isCasting = false,

                        subtitleCallback = { subtitleFile ->

                            Log.d(TAG, "[${api.name}] Altyazı: ${subtitleFile.lang} → ${subtitleFile.url}")

                            subtitleList.add(

                                SubtitleInput(

                                    url = normalizeCsSubtitleUrl(subtitleFile.url),

                                    name = subtitleFile.lang,

                                    lang = subtitleFile.langTag ?: detectLanguageCode(subtitleFile.lang),

                                    isExternal = true,

                                    headers = subtitleFile.headers?.toMap().orEmpty()

                                )

                            )

                        },

                        callback = { link ->

                            val cleanUrl = resolveHrefLi(link.url)

                            Log.d(TAG, "[${api.name}] Link bulundu: ${link.name} → $cleanUrl")

                            CsTrace.info(api.name, "link", "${link.name} → $cleanUrl")



                            // Görüntü dosyalarını (logo, poster) video stream olarak ekleme — SAM lambda

                            // içinde return yasak, if-guard ile filtrele

                            val lowerPath = try {

                                java.net.URI(cleanUrl.lowercase(Locale.ROOT)).path ?: cleanUrl.lowercase(Locale.ROOT)

                            } catch (_: Exception) { cleanUrl.lowercase(Locale.ROOT) }

                            val isImageUrl = lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") ||

                                lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".gif") ||

                                lowerPath.endsWith(".webp") || lowerPath.endsWith(".svg") ||

                                lowerPath.contains("/vod/img/")



                            val clearlyDirectMedia = isClearlyDirectMediaUrl(cleanUrl)

                            val mediaType = streamMediaType(link.type)

                            val adaptive = mediaType == com.kitsugi.animelist.core.player.StreamMediaType.HLS ||

                                mediaType == com.kitsugi.animelist.core.player.StreamMediaType.DASH

                            // VIDEO is also the library's default for an unknown/embed URL;

                            // do not trust it without an extension or an HTTP media response.

                            val directlyPlayable = (clearlyDirectMedia && !isEmbedUrl(link.url)) ||

                                (adaptive && !CsMediaResponsePolicy.isObviousEmbedPage(cleanUrl))

                            val providerReferer = try { api.mainUrl } catch (_: Exception) { "" }

                            val rawHeaders = CsStreamHeaders.extracted(

                                parent = emptyMap(),

                                child = link.headers,

                                extractorReferer = link.referer,

                                fallbackReferer = if (directlyPlayable)

                                    originOfUrl(cleanUrl)?.let { "$it/" } ?: providerReferer

                                else providerReferer,

                                mediaUrl = cleanUrl,

                                userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT,

                                // For an embed, only derive Origin after the final extractor

                                // has had a chance to supply its own Referer.

                                addOrigin = directlyPlayable

                            )

                            if (isImageUrl) {

                                Log.w(TAG, "[${api.name}] Görüntü URL'si atlanıyor (video değil): $cleanUrl")

                            } else if (!directlyPlayable) {

                                Log.d(TAG, "[${api.name}] Belirsiz/embed URL çözümleme kuyruğuna alındı: $cleanUrl")

                                pendingEmbedUrls.add(

                                    PendingEmbedLink(

                                        url = link.url,

                                        name = link.name,

                                        headers = rawHeaders,

                                        mediaType = mediaType,

                                        quality = link.quality,

                                        audioUrl = link.audioTracks.firstOrNull()?.url?.takeIf { it.isNotBlank() }

                                    )

                                )

                            } else {

                                val headers = rawHeaders

                                val newStream = StreamSource(

                                        addonName    = api.name,

                                        name         = "${api.name} • ${link.name}",

                                        title        = link.name,

                                        url          = cleanUrl,

                                        infoHash     = null,

                                        fileIndex    = null,

                                        requestHeaders = headers,

                                        isCS         = true,

                                        streamTypeHint = link.type.name,

                                        quality      = getQualityString(link.quality),

                                        qualityValue = getQualityValue(link.quality),

                                        subtitles    = emptyList(),

                                        thumbnailUrl = posterUrl,

                                        isAdultContent = ADULT_PLUGINS.contains(api.name),

                                        mediaTransport = when (link.type) {

                                            ExtractorLinkType.M3U8 -> "hls"

                                            ExtractorLinkType.DASH -> "dash"

                                            ExtractorLinkType.VIDEO -> "progressive"

                                            else -> null

                                        },

                                        externalAudioUrl = link.audioTracks.firstOrNull()?.url?.takeIf { it.isNotBlank() },
                                        externalAudioTrackCount = link.audioTracks.count { it.url.isNotBlank() },
                                        providerAudioKind = if (link.audioTracks.any { it.url.isNotBlank() }) "dual" else null,

                                        externalAudioTracks = externalAudioTrackInputs(link),

                                        externalAudioSources = link.audioTracks.mapNotNull { audio ->

                                            audio.url.takeIf { it.isNotBlank() }?.let { url ->

                                                com.kitsugi.animelist.data.repository.ExternalAudioSource(url, audio.headers.orEmpty().toMap())

                                            }

                                        },

                                        videoSizeBytes = extractVideoSize(link),

                                        mediaType = mediaType

                                )

                                streams.add(newStream)

                                partialSink?.add(newStream)

                            }

                        }

                    )

                } ?: run {

                    CsTrace.warn(api.name, "loadLinks", "zaman aşımı (${loadLinksTimeoutMs / 1000}s), ${streams.size} link ile devam")

                    Log.w(TAG, "[${api.name}] loadLinks ${loadLinksTimeoutMs / 1000}s zaman aşımına uğradı, elde edilen ${streams.size} link döndürülüyor.")

                    if (streams.isEmpty()) {

                        CsPluginStatusTracker.recordSkip(api.name, "loadLinks zaman aşımı (${loadLinksTimeoutMs / 1000} sn) — hiç link alınamadı")

                    }

                }

            }

        } catch (e: Throwable) {

            if (e is kotlinx.coroutines.CancellationException) throw e

            Log.e(TAG, "[${api.name}] loadLinks HATA: ${e.javaClass.simpleName}: ${e.message}", e)

            Log.e(SERR, "💥 LOAD_LINKS CRASH [${api.name}] — ${e.javaClass.simpleName}: ${e.message}\n${android.util.Log.getStackTraceString(e)}")

            CsTrace.error(api.name, "loadLinks", "loadLinks istisnası: ${e.javaClass.simpleName}: ${e.message}", e)

        }



        // ── Embed URL'leri çözümleme aşaması (VK, Sibnet, Vidmoly, Filemoon, Okru vb.) ─────

        if (pendingEmbedUrls.isNotEmpty()) {

            Log.d(

                TAG,

                "[${api.name}] ${pendingEmbedUrls.size} embed URL'si paralel çözümleniyor " +

                    "(eşzamanlılık=$EMBED_RESOLVE_CONCURRENCY)..."

            )

            val referer = try { api.mainUrl } catch (_: Exception) { "https://google.com" }



            // Embed'ler SIRAYLA çözüldüğünde (eski davranış) 20 sn timeout × N embed yüzünden

            // kaynak listesi dakikalarca gelmiyordu. Artık sınırlı eşzamanlılıkla paralel

            // çözülür; sonuç sırası korunur (kaynak kalitesi sunum sırasını etkilemesin).

            val embedResults = kotlinx.coroutines.coroutineScope {

                val gate = Semaphore(EMBED_RESOLVE_CONCURRENCY)

                pendingEmbedUrls.map { pending ->

                    async {

                        gate.withPermit {

                            val clean = resolveHrefLi(pending.url)

                            var mediaUrl = clean

                            val driveDownload = CsMediaResponsePolicy.isDriveDownloadUrl(clean)

                            val headers = CsStreamHeaders.complete(

                                pending.headers, referer, clean,

                                com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                            )

                            val probeClient = probeFor(

                                api, pending.name, clean, headers, getQualityValue(pending.quality), pending.mediaType

                            )

                            val typedManifest = pending.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.HLS ||

                                pending.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.DASH

                            val inspection = when {

                                driveDownload -> probeClient.inspectDrive(clean, headers)

                                !isEmbedUrl(clean) || typedManifest -> CsDirectMediaProbe.Inspection(probeClient.inspect(clean, headers))

                                else -> CsDirectMediaProbe.Inspection(CsMediaResponseKind.UNKNOWN)

                            }

                            var probe = inspection.kind

                            if (driveDownload && probe == CsMediaResponseKind.HTML) {

                                val confirmed = inspection.html?.let {

                                    runCatching { CsDriveConfirmation.resolve(it, clean) }.getOrNull()

                                }

                                if (confirmed != null && confirmed != clean) {

                                    val follow = probeFor(

                                        api, pending.name, confirmed, headers, getQualityValue(pending.quality), pending.mediaType

                                    ).inspectDrive(confirmed, headers)

                                    probe = follow.kind

                                    if (probe != CsMediaResponseKind.HTML && probe != CsMediaResponseKind.DEAD) {

                                        mediaUrl = confirmed

                                    }

                                }

                            }

                            val detectedType = when (probe) {

                                CsMediaResponseKind.HLS -> com.kitsugi.animelist.core.player.StreamMediaType.HLS

                                CsMediaResponseKind.DASH -> com.kitsugi.animelist.core.player.StreamMediaType.DASH

                                CsMediaResponseKind.VIDEO -> com.kitsugi.animelist.core.player.StreamMediaType.PROGRESSIVE

                                else -> null

                            }

                            when {

                                detectedType != null || (driveDownload && probe == CsMediaResponseKind.UNKNOWN) -> {

                                    // A Drive confirmation URL is already a resolved endpoint.

                                    // Preserve every query parameter even if the probe is inconclusive.

                                    val stream = StreamSource(

                                        addonName = api.name,

                                        name = "${api.name} • ${pending.name}",

                                        title = pending.name,

                                        url = mediaUrl,

                                        infoHash = null,

                                        fileIndex = null,

                                        requestHeaders = headers,

                                        isCS = true,

                                        quality = getQualityString(pending.quality),

                                        qualityValue = getQualityValue(pending.quality),

                                        thumbnailUrl = posterUrl,

                                        isAdultContent = ADULT_PLUGINS.contains(api.name),

                                        externalAudioUrl = pending.audioUrl,

                                        mediaType = detectedType ?: pending.mediaType

                                    )

                                    PendingEmbedResult(pending, listOf(stream))

                                }

                                probe == CsMediaResponseKind.DEAD ||

                                    (driveDownload && probe == CsMediaResponseKind.HTML) -> {

                                    Log.w(TAG, "[${api.name}] Medya yerine $probe döndü: ${clean.take(100)}")

                                    PendingEmbedResult(pending, emptyList(), allowFallback = false)

                                }

                                else -> {

                                    val resolved = resolveEmbedUrl(

                                        providerName = api.name,

                                        rawUrl = pending.url,

                                        rawLinkName = pending.name,

                                        rawHeaders = pending.headers,

                                        referer = CsStreamHeaders.value(pending.headers, "Referer") ?: referer,

                                        subtitleCallback = { sub -> subtitleList.add(sub) },

                                        thumbnailUrl = posterUrl

                                    )

                                    PendingEmbedResult(

                                        pending, resolved,

                                        allowFallback = probe != CsMediaResponseKind.HTML,

                                        inconclusiveDirect = probe == CsMediaResponseKind.UNKNOWN &&

                                            !isEmbedUrl(clean) && !CsMediaResponsePolicy.isObviousEmbedPage(clean)

                                    )

                                }

                            }

                        }

                    }

                }.awaitAll()

            }



            for (result in embedResults) {

                val pending = result.link

                val rawUrl = pending.url

                val linkName = pending.name

                val resolved = result.streams

                if (resolved.isNotEmpty()) {

                    streams.addAll(resolved)

                    partialSink?.addAll(resolved)

                } else if (result.allowFallback) {

                    val clean = resolveHrefLi(rawUrl)

                    // Oynatıcıya HTML sayfası göndermek "oynatıcı hata veriyor" şikâyetinin

                    // en büyük kaynağıdır. Bu yüzden yalnızca medya gibi GÖRÜNEN URL'ler

                    // son çare olarak eklenir; HTML oynatıcı sayfaları elenir.

                    val obviouslyHtml = clean.contains("shell.php") ||

                        clean.contains("video_ext.php") ||

                        clean.contains("/embed/") ||

                        clean.contains("/player/") ||

                        clean.endsWith(".html") ||

                        clean.contains(".html?")

                    // A network error/403 is not proof of a dead video. Retain an

                    // inconclusive direct-looking URL, but never a confirmed HTML page.

                    val looksPlayable = isClearlyDirectMediaUrl(clean) || result.inconclusiveDirect ||

                        pending.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.HLS ||

                        pending.mediaType == com.kitsugi.animelist.core.player.StreamMediaType.DASH

                    if (!obviouslyHtml && looksPlayable) {

                        Log.w(TAG, "[${api.name}] Extractor çözemedi — medya URL'si olduğu için ekleniyor: $clean")

                        val fallbackHeaders = CsStreamHeaders.complete(

                            pending.headers, referer, clean,

                            com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                        )

                        val fallbackStream = StreamSource(

                                addonName      = api.name,

                                name           = "${api.name} • $linkName",

                                title          = linkName,

                                url            = clean,

                                infoHash       = null,

                                fileIndex      = null,

                                requestHeaders = fallbackHeaders,

                                isCS           = true,

                                quality        = getQualityString(pending.quality),

                                qualityValue   = getQualityValue(pending.quality),

                                subtitles      = emptyList(),

                                thumbnailUrl   = posterUrl,

                                isAdultContent = ADULT_PLUGINS.contains(api.name),

                                externalAudioUrl = pending.audioUrl,

                                mediaType      = pending.mediaType.takeUnless {

                                    result.inconclusiveDirect && it == com.kitsugi.animelist.core.player.StreamMediaType.PROGRESSIVE

                                }

                        )

                        streams.add(fallbackStream)

                        partialSink?.add(fallbackStream)

                    } else {

                        Log.w(

                            TAG,

                            "[${api.name}] Embed çözülemedi ve URL oynatılabilir medya değil — elendi: $clean"

                        )

                    }

                }

            }

        }



        Log.d(TAG, "[${api.name}] Ham sonuç: ${streams.size} stream, ${subtitleList.size} altyazı")



        // ── Stream URL Canlılık Doğrulaması (HTTP HEAD) ───────────────────────────────────────

        // Extractor URL bulsun diye ÖLÜ KANAL değil, gerçekten oynatılabilir mi kontrol et.

        // Sadece doğrudan medya URL'leri kontrol edilir (.m3u8, .mp4, .mpd).

        // Embed/iframe URL'leri (zaten çözümlenmiş olmak zorunda) atlanır.

        // Bu adım kullanıcıya "video bulunamadı" yerine gerçekten çalışan kaynakları sunar.

        val verifiedStreams = java.util.Collections.synchronizedList(mutableListOf<StreamSource>())

        val deadStreams = java.util.Collections.synchronizedList(mutableListOf<StreamSource>())

        // Hızlı ön-eleme (ağ yok): ÖLÜ KANAL etiketi, boş URL ve doğrudan-medya olmayanlar.

        val passThrough = mutableListOf<StreamSource>()

        val toCheck = mutableListOf<StreamSource>()

        val measuredSizes = java.util.concurrent.ConcurrentHashMap<String, Long>()

        for (stream in streams) {

            // ÖLÜ KANAL etiketli kaynakları doğrulama yapmadan düşür

            if (stream.name.contains("ÖLÜ KANAL", ignoreCase = true) ||

                stream.title.contains("ÖLÜ KANAL", ignoreCase = true)) {

                Log.w(TAG, "[${api.name}] ÖLÜ KANAL stream düşürülüyor: ${stream.url.orEmpty().take(80)}")

                deadStreams.add(stream)

                continue

            }



            val url = stream.url.orEmpty()

            if (url.isBlank()) {

                // URL yoksa (infoHash tabanlı?) dokunma

                passThrough.add(stream)

                continue

            }



            val isDirectMedia = url.contains(".m3u8", ignoreCase = true) ||

                                url.contains(".mp4", ignoreCase = true)  ||

                                url.contains(".mpd", ignoreCase = true)  ||

                                url.contains(".mkv", ignoreCase = true)



            if (!isDirectMedia) {

                // Embed URL'leri doğrulamadan direkt ekle (extractor zaten işledi)

                passThrough.add(stream)

                continue

            }



            // HTTP HEAD ile doğrula — PARALEL (eski davranış: sıralı × 8 sn; ölü/çözümlenemeyen

            // CDN'lerde tek başına saniyeler yutup dış provider zaman aşımını patlatıyor ve

            // o ana kadar bulunan TÜM kaynaklar çöpe gidiyordu). Eşzamanlılık 6, deneme 5 sn.

            toCheck.add(stream)

        }



        if (toCheck.isNotEmpty()) {

            kotlinx.coroutines.coroutineScope {

                val gate = Semaphore(6)

                toCheck.map { stream ->

                    async {

                        gate.withPermit {

                            val url = stream.url.orEmpty()

                            var measuredSize: Long? = null
                            // HEAD bazı CDN'lerde desteklenmez (405) veya farklı davranır; bu durumda

                            // GET with Range header ile doğrulama yap (daha güvenilir).

                            val isAlive = withTimeoutOrNull(5_000L) {

                                try {

                                    val requestHeaders = stream.requestHeaders.orEmpty()

                                    val referer = requestHeaders.entries

                                        .firstOrNull { it.key.equals("Referer", ignoreCase = true) }

                                        ?.value

                                    // HEAD doğrulamasında provider'ın tüm metadata'sı (Origin,

                                    // Cookie, token ve özel User-Agent dahil) korunur.

                                    val headRequest = buildStreamValidationRequest(

                                        url = url,

                                        method = "HEAD",

                                        headers = requestHeaders,

                                        userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT

                                    )

                                    val response = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

                                        .newCall(headRequest).execute()

                                    val code = response.code
                                    if (code in 200..299) {
                                        measuredSize = response.header("Content-Length")
                                            ?.toLongOrNull()
                                            ?.takeIf { it > 0 }
                                    }

                                    response.close()



                                    // HTTP koduna göre canlılık kararı:

                                    // ✅ 200-299: Tamam, dosya mevcut

                                    // ✅ 301/302/307/308: Yönlendirme — player takip eder

                                    // ✅ 401/403: Yetki gerektirir — header ile player açabilir

                                    // ✅ 405/501: HEAD desteklenmiyor — GET ile çalışır

                                    // ✅ 429: Rate limit — geçici, player deneyebilir

                                    // ✅ 5xx: Sunucu hatası — geçici olabilir

                                    // ❌ 400: Bad Request — URL bozuk (ama yine de GET dene)

                                    // ❌ 404/410: Dosya yok — ölü

                                    val alive = when (code) {

                                        in 200..299 -> true

                                        301, 302, 307, 308 -> true

                                        401, 403 -> true   // Auth gerekli, player header ile deneyecek

                                        405, 501 -> true   // HEAD desteklenmiyor, GET ile açılır

                                        429 -> true        // Rate limit, geçici

                                        in 500..599 -> true // Sunucu hatası, geçici olabilir

                                        410 -> false  // Kalici olarak gitmus — olu

                                        404 -> {

                                            // v2.4.304: bazi CDN'ler imzali playlist/video URL'lerine

                                            // HEAD'de 404 doner ama GET calisir (closeload/rapidrame

                                            // ailesinde gozlendi). Kaynagi dusurmeden once GET dene.

                                            Log.d(TAG, "[${api.name}] HEAD 404 dondu, GET ile deneniyor: ${url.take(60)}")

                                            tryGetRangeValidation(url, requestHeaders)

                                        }

                                        400 -> {

                                            // 400 Bad Request — bazı CDN'ler HEAD'e 400 döner ama GET çalışır.

                                            Log.d(TAG, "[${api.name}] HEAD 400 döndü, GET ile deneniyor: ${url.take(60)}")

                                            tryGetRangeValidation(url, requestHeaders)

                                        }

                                        else -> true  // Bilinmeyen kod — iyimser ol, player denesin

                                    }

                                    Log.d(TAG, "[${api.name}] HEAD ${if (alive) "✅" else "❌"} HTTP $code: ${url.take(80)}")

                                    alive

                                } catch (cancel: kotlinx.coroutines.CancellationException) {

                                    throw cancel

                                } catch (e: Exception) {

                                    Log.w(TAG, "[${api.name}] HEAD kontrol hatası (${e.javaClass.simpleName}): ${url.take(60)}")

                                    // Ağ hatası = URL'in dead olduğu anlamına gelmez (geçici), güvenli tarafta kal

                                    true

                                }

                            } ?: run {

                                Log.w(TAG, "[${api.name}] HEAD timeout (5s): ${url.take(60)} — muhtemelen yavaş CDN, ekleniyor")

                                true  // Timeout = belirsiz, oynatmayı dene

                            }



                            if (isAlive) {

                                val known = stream.videoSizeBytes ?: measuredSize
                                if (known != null) measuredSizes[url] = known
                                val sized = if (stream.videoSizeBytes == null && known != null) {
                                    stream.copy(videoSizeBytes = known)
                                } else stream
                                verifiedStreams.add(sized)

                            } else {

                                Log.w(TAG, "[${api.name}] ❌ Dead stream tespit edildi, düşürülüyor: ${url.take(80)}")

                                deadStreams.add(stream)

                            }

                        }

                    }

                }.awaitAll()

            }

        }

        verifiedStreams.addAll(passThrough)



        if (deadStreams.isNotEmpty()) {

            Log.w(TAG, "[${api.name}] ${deadStreams.size} dead stream düşürüldü, ${verifiedStreams.size} aktif stream kaldı.")

        }



        // Eger tum stream'ler dead ciktiysa: gercekten olu mu false positive mi ayirt et

        val verifiedFinal = if (verifiedStreams.isEmpty() && streams.isNotEmpty()) {

            // toCheck = dogrudan medya (HEAD ile denenenler), passThrough = embed vb. dogrulanmayanlar

            // Eger toCheck'teki HER sey dead olarak isaretlendiyse (HEAD 404 + GET 404) -> gercekten olu, oynaticiya atma

            // Sadece timeout/ag hatasi nedeniyle bos kaldiysa eski davranis gibi orijinalleri dondur

            val trulyDead = deadStreams.isNotEmpty() && toCheck.size == deadStreams.size && toCheck.isNotEmpty()

            if (trulyDead) {

                Log.w(TAG, "[${api.name}] Tum streamler dead cikti - HEAD/GET 404 ile dogrulandi, gercekten olu (sahte link degil), bos donuluyor")

                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(api.name, "verify", "Tum streamler HEAD 404 ile olu - kaynak yok, player'a gonderilmiyor")

                verifiedStreams // bos

            } else {

                Log.w(TAG, "[${api.name}] Tum streamler dead cikti - false positive olabilir, orijinaller donduruluyor.")

                if (measuredSizes.isEmpty()) {
                    streams
                } else {
                    streams.map { s ->
                        val measured = s.url?.let { measuredSizes[it] }
                        if (s.videoSizeBytes == null && measured != null) s.copy(videoSizeBytes = measured) else s
                    }
                }

            }

        } else {

            // Kalite degerine gore sirala (yuksek kalite once), null qualityValue 0 kabul et

            verifiedStreams.sortedByDescending { it.qualityValue ?: 0 }

        }



        // ── Reklam / HLS parçası / tekrar temizliği ─────────────────────────────

        // Kullanıcı raporu (2026-10-12): Dizipal bahis reklamını (`kralbet.mp4`) bölüm kaynağı

        // gibi veriyordu; Watch2Movies ise HLS parçalarını (`seg-1-v1-a1.ts`) ayrı kaynak

        // kartları olarak listeliyordu. Temizlik burada, doğrulama SONRASI yapılır ki

        // elenenler gerçekten listeden çıksın.

        val (finalStreams, sanitizedOut) = CsStreamSanitizer.sanitize(api.name, verifiedFinal)



        CsTrace.info(api.name, "result", "Sonuç: ${finalStreams.size} doğrulanmış stream (ham ${streams.size}, ölü düşen ${deadStreams.size}, reklam/parça/tekrar elenen $sanitizedOut), ${subtitleList.size} altyazı")

        // Dil bilgisi: sağlayıcının kendi meta verisinden (DubStatus) okunur — tahmin edilmez.

        val providerAudioKind = CsEpisodeMatcher.findDubStatusForEpisodeData(loadResponse, episodeData)

        Log.d(

            TAG,

            "[${api.name}] ✅ Sonuç: ${finalStreams.size} doğrulanmış stream, " +

                "${subtitleList.size} altyazı, sağlayıcı ses türü=${providerAudioKind ?: "bilinmiyor"}"

        )

                val originPageUrl = loadResponse.url?.takeIf { it.isNotBlank() } ?: fallbackPageUrl?.takeIf { it.isNotBlank() }
        return finalStreams.map { stream ->
            val measured = stream.url?.let { measuredSizes[it] }
            stream.copy(
                subtitles = subtitleList.toList(),
                providerAudioKind = stream.providerAudioKind ?: providerAudioKind,
                videoSizeBytes = stream.videoSizeBytes ?: measured,
                cs3PageUrl = stream.cs3PageUrl ?: originPageUrl,
                cs3ApiName = stream.cs3ApiName ?: api.name
            )
        }

    }



    // Delegated to CsLanguageDetector — see CsLanguageDetector.kt

    private fun detectLanguageCode(lang: String): String =

        CsLanguageDetector.detectLanguageCode(lang)

    /** Yalnızca çıplak eser adı varyantları — birincil arama sorguları (sezon/bölüm karışmaz). */

    private fun buildPlainTitleVariants(main: String, alts: List<String>, isMovie: Boolean): List<String> =

        CsTitleMatcher.buildPlainTitleVariants(main, alts, isMovie)



    /** Sezon-kapsamlı varyantlar — yalnızca güvenlik ağları (sezon girdisi araması / sezon sayfası gezinmesi). */

    private fun buildSeasonScopedVariants(main: String, alts: List<String>, season: Int): List<String> =

        CsTitleMatcher.buildSeasonScopedVariants(main, alts, season)



    /**

     * Geniş arama sorguları üretir — tüm başlık varyantları boş döndüğünde devreye girer.

     *

     * Strateji:

     * 1. Ana başlığın ilk 2 anlamlı kelimesi (örn. "Spider-Man Brand New Day" → "Spider-Man Brand")

     * 2. Ana başlığın ilk anlamlı kelimesi (örn. "Naruto Shippuden" → "Naruto")

     * 3. Alternatif başlıkların ilk anlamlı kelimeleri

     * 4. ASCII-transliterated versiyonlar (Türkçe karakterler sorun çıkarıyorsa)

     *

     * Bu sorgular jenerik olabilir ama `findBestMatch` yanlış eşleşmeleri

     * MIN_MATCH_SCORE threshold'u ile eler (filtreler).

     */

    private fun buildBroadSearchQueries(main: String, alts: List<String>): List<String> {

        val queries = linkedSetOf<String>()



        fun significantWordsOf(title: String): List<String> {

            val normalized = CsTitleMatcher.normalizeTitleForMatch(title)

            return normalized.split(Regex("\\s+"))

                .filter { it.isNotBlank() && it.length >= 3 }

                .filter { word ->

                    // Jenerik kelimeleri hariç tut

                    word !in setOf("the", "and", "of", "a", "an", "to", "in", "on", "for",

                        "sezon", "season", "bolum", "episode", "film", "dizi", "anime",

                        "izle", "watch", "full", "hd", "turkce", "turkish", "dublaj", "altyazi")

                }

        }



        // Ana başlıktan ilk 2-3 anlamlı kelime

        val mainWords = significantWordsOf(main)

        if (mainWords.size >= 2) {

            queries.add(mainWords.take(2).joinToString(" "))

        }

        if (mainWords.size >= 3) {

            queries.add(mainWords.take(3).joinToString(" "))

        }

        if (mainWords.isNotEmpty()) {

            queries.add(mainWords.first())

        }



        // Alternatif başlıklardan ilk anlamlı kelime (en fazla 3 alternatif)

        for (alt in alts.take(3)) {

            val altWords = significantWordsOf(alt)

            if (altWords.isNotEmpty()) {

                queries.add(altWords.first())

            }

            if (altWords.size >= 2) {

                queries.add(altWords.take(2).joinToString(" "))

            }

        }



        // ASCII-only versiyonlar (Türkçe karakterler sorun çıkarıyorsa)

        val asciiMain = CsTitleMatcher.toAsciiTitle(main)

        if (asciiMain != main && asciiMain.isNotBlank()) {

            val asciiWords = significantWordsOf(asciiMain)

            if (asciiWords.size >= 2) {

                queries.add(asciiWords.take(2).joinToString(" "))

            }

        }



        return queries.filter { CsTitleMatcher.normalizeTitleForMatch(it).length >= 2 }.take(6)

    }



    internal suspend fun safeSearch(api: MainAPI, query: String): List<SearchResponse> {

        ensurePluginReady(api)

        // Session bloklist kontrolü

        if (CsPluginStatusTracker.isBlocked(api.name)) {

            Log.w(TAG, "[${api.name}] safeSearch: Engellendi — atlanıyor.")

            return emptyList()

        }

        // NOT: KNOWN_BROKEN_PLUGINS kontrolü kasıtlı olarak burada YOK.

        // Bu eklentiler plugin arama sayfasında çalışabilmeli.

        // Engel sadece stream çekme aşamasında (getStreamsForUrl) uygulanır.

        val normalizeUrl = { u: String -> u.replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/') }

        val currentDomain = normalizeUrl(api.mainUrl)

        if (KNOWN_BROKEN_DOMAINS.any { currentDomain.contains(it) }) {

            // Sessiz atlama YOK — eski davranış eklentiyi kalıcı olarak sıfıra indiriyordu.

            Log.w(TAG, "[${api.name}] safeSearch: Domain (${api.mainUrl}) ölü domain listesinde — yine de deneniyor.")

        }

        // v2.4.240 (KN-A + KN-C): arama kutusu CF sabır bütçesini tanır ve gate arama

        // ZAMAN KUTUSUNUN DIŞINDA koşar (çözüm süresi arama bütçesini yemez).

        val searchStageMs = stageTimeoutMs(api, SEARCH_STAGE_TIMEOUT_MS)

        runSearchGateOnce(api)

        val queueWaitStart = System.currentTimeMillis()

        return searchSemaphore.withPermit {

            searchQueueWaitMs.addAndGet(System.currentTimeMillis() - queueWaitStart)

            withTimeoutOrNull(searchStageMs) {

                try {

                // Hafif throttle — aggressive rate-limiting'i önler

                kotlinx.coroutines.delay(300)



                val providerName = api.name



                // TRanimaci runtime override: WebView JavaScript injection ile __NEXT_DATA__ JSON parse et.

                //

                // Sorun: OkHttp + CloudflareKiller yaklaşımı → proceedWithCookies sonrası yine WAF sayfası

                // dönüyor çünkü WebView cookie'leri OkHttp HTTP Header'larına doğru aktarılamıyor.

                //

                // Çözüm: WebViewResolver'ın script parametresini kullanarak arama sayfasını doğrudan

                // WebView içinde yüklüyoruz ve JavaScript ile document.getElementById('__NEXT_DATA__')

                // içeriğini okuyoruz. Tüm WAF challenge, cookie yönetimi ve sayfa yüklemesi aynı

                // WebView session'ında gerçekleşiyor — cookie aktarım sorunu yok.

                if (providerName.equals("TrAnimeci", ignoreCase = true)) {

                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")

                    val searchUrl = "${api.mainUrl}/ara?q=$encodedQuery"

                    

                    Log.d(TAG, "[$providerName] WebView JS injection ile arama başlatılıyor: $searchUrl")

                    

                    try {

                        // WebView'dan dönen __NEXT_DATA__ JSON'unu tutacak container

                        val nextDataHolder = java.util.concurrent.atomic.AtomicReference<String?>(null)

                        val latch = java.util.concurrent.CountDownLatch(1)

                        

                        // JavaScript: Next.js App Router → DOM scraping

                        // Site CSR veya SSR kullanabilir. Arama sayfasındaki /anime/ linklerini toplayıp döndürüyoruz.

                        val encodedQueryForJs = query.replace("\"", "\\\"").replace("'", "\\'")

                        val jsScript = """

                            (function() {

                                try {

                                    var searchQuery = '$encodedQueryForJs';

                                    var results = [];

                                    var seen = {};

                                    // Sayfadaki tüm /anime/ linklerini bul

                                    var links = document.querySelectorAll('a[href*="/anime/"]');

                                    for (var i = 0; i < links.length && results.length < 40; i++) {

                                        var a = links[i];

                                        var href = a.href || a.getAttribute('href') || '';

                                        if (!href) continue;

                                        // Absolute path'e çevir

                                        if (href.startsWith('/')) {

                                            href = window.location.origin + href;

                                        }

                                        if (seen[href]) continue;

                                        seen[href] = 1;

                                        

                                        // Başlığı bul (title attribute, textContent veya h2/h3/h4/span içindeki metin)

                                        var title = (a.getAttribute('title') || '').trim();

                                        if (!title) {

                                            var header = a.querySelector('h1, h2, h3, h4, h5, h6, p, span, div');

                                            title = header ? header.textContent.trim() : a.textContent.trim();

                                        }

                                        title = title.replace(/\s+/g, ' ').trim();

                                        if (!title || title.length < 2) continue;

                                        

                                        // Görseli bul

                                        var img = a.querySelector('img');

                                        var poster = img ? (img.src || img.getAttribute('data-src') || img.getAttribute('lazy-src') || '') : '';

                                        

                                        results.push({n: title, u: href, p: poster});

                                    }

                                    

                                    if (results.length > 0) {

                                        return 'DOM:' + JSON.stringify(results);

                                    }

                                    

                                    var hasQuery = document.body.innerHTML.toLowerCase().indexOf(searchQuery.toLowerCase()) !== -1;

                                    var matchingTags = [];

                                    if (hasQuery) {

                                        var allElements = document.body.getElementsByTagName('*');

                                        for (var k = 0; k < allElements.length && matchingTags.length < 10; k++) {

                                            var elText = allElements[k].textContent || '';

                                            if (elText.toLowerCase().indexOf(searchQuery.toLowerCase()) !== -1 && allElements[k].children.length === 0) {

                                                matchingTags.push(allElements[k].tagName + ':' + elText.substring(0, 100).trim());

                                            }

                                        }

                                    }

                                    

                                    var debugLinks = [];

                                    var allLinks = document.querySelectorAll('a');

                                    for (var j = 0; j < Math.min(allLinks.length, 12); j++) {

                                        debugLinks.push(allLinks[j].outerHTML);

                                    }

                                    return 'NOT_FOUND:' + document.title + ':HTML_LEN:' + document.body.innerHTML.length + ':HAS_QUERY:' + hasQuery + ':TAGS:' + JSON.stringify(matchingTags) + ':LINKS:' + JSON.stringify(debugLinks);

                                } catch(e) {

                                    return 'JS_ERROR:' + e.message;

                                }

                            })()

                        """.trimIndent()

                        

                        // Main thread'de WebView çalıştır (Android WebView main thread zorunluluğu)

                        // resolveUsingWebView suspending fonksiyon: WebView timeout (60s) sonunda dönüyor.

                        // scriptCallback her resource yüklenince çalışıyor — en son değeri tutuyoruz.

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {

                            try {

                                com.lagradost.cloudstream3.network.WebViewResolver(

                                    interceptUrl = Regex(".^"),  // Hiçbir URL intercept etme

                                    additionalUrls = listOf(Regex(".")),

                                    userAgent = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT,

                                    useOkhttp = false,

                                    script = jsScript,

                                    scriptCallback = { result ->

                                        if (result.startsWith("DOM:")) {

                                            Log.d("TR_WV", "[$providerName] WebView JS DOM sonucu alındı: $result")

                                        } else {

                                            Log.d("TR_WV", "[$providerName] WebView JS sonucu alındı (${result.length} karakter): ${result.take(2000)}")

                                        }

                                        val prev = nextDataHolder.get()

                                        val prevLen = prev?.substringAfter("HTML_LEN:")?.substringBefore(":")?.toIntOrNull() ?: -1

                                        val currLen = result.substringAfter("HTML_LEN:")?.substringBefore(":")?.toIntOrNull() ?: -1

                                        

                                        if (result.startsWith("DOM:") || (prev == null || !prev.startsWith("DOM:")) && currLen >= prevLen) {

                                            nextDataHolder.set(result)

                                        }

                                        if (result.startsWith("DOM:")) {

                                            latch.countDown()

                                        }

                                    }

                                ).resolveUsingWebView(searchUrl) { false }

                            } catch (e: Exception) {

                                Log.e(TAG, "[$providerName] WebView resolve hatası: ${e.message}")

                            }

                            // WebView bitti — latch'i düşür (eğer heniz düşürmediyse)

                            latch.countDown()

                        }

                        

                        // WebView'ın tamamlanmasını bekle (maks 45 saniye — WAF PoW çözümü için yeterli)

                        val completed = latch.await(45, java.util.concurrent.TimeUnit.SECONDS)

                        if (!completed) {

                            Log.w(TAG, "[$providerName] WebView 45s içinde tamamlanmadı — boş sonuç dönülüyor")

                            return@withTimeoutOrNull emptyList()

                        }

                        

                        val rawResult = nextDataHolder.get()

                        val jsResult = rawResult?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")

                        Log.d("TR_WV", "[$providerName] JS sonuç preview: ${jsResult?.take(500)}")

                        

                        if (jsResult.isNullOrBlank() || jsResult.startsWith("NOT_FOUND:") || jsResult.startsWith("JS_ERROR:") || jsResult == "null") {

                            Log.w(TAG, "[$providerName] DOM scraping başarısız: $jsResult")

                            return@withTimeoutOrNull emptyList()

                        }

                        

                        // Handle DOM scraping results (DOM: prefix)

                        if (jsResult.startsWith("DOM:")) {

                            val domJson = jsResult.removePrefix("DOM:")

                            Log.d("TR_WV", "[$providerName] DOM sonuçları parse ediliyor...")

                            val parsed = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()

                            try {

                                val arr = org.json.JSONArray(domJson)

                                Log.d("TR_WV", "[$providerName] DOM array length=${arr.length()}")

                                for (i in 0 until arr.length()) {

                                    val item = arr.optJSONObject(i) ?: continue

                                    val name = item.optString("n").takeIf { it.isNotBlank() } ?: continue

                                    val itemUrl = item.optString("u").takeIf { it.isNotBlank() } ?: continue

                                    val poster = item.optString("p").takeIf { it.isNotBlank() }

                                    val sr = try {

                                        val cls = Class.forName("com.lagradost.cloudstream3.AnimeSearchResponse")

                                        val ctor = cls.constructors.firstOrNull { it.parameterCount >= 3 } ?: continue

                                        val args = Array<Any?>(ctor.parameterCount) { null }

                                        args[0] = name; args[1] = itemUrl; args[2] = api.name

                                        if (ctor.parameterCount > 3) args[3] = com.lagradost.cloudstream3.TvType.Anime

                                        if (ctor.parameterCount > 4) args[4] = poster

                                        ctor.newInstance(*args) as? com.lagradost.cloudstream3.SearchResponse

                                    } catch (ex: Exception) { null }

                                    if (sr != null) parsed.add(sr)

                                }

                            } catch (ex: Exception) {

                                Log.w(TAG, "[$providerName] DOM JSON parse hatası: ${ex.message}")

                            }

                            Log.d(TAG, "[$providerName] DOM scraping '$query' → ${parsed.size} sonuç")

                            return@withTimeoutOrNull parsed

                        }

                        

                        // nextDataJson = __NEXT_DATA__ JSON (Pages Router legacy path)

                        val nextDataJson = jsResult

                        

                        // JSON parse et ve sonuçları çıkar

                        val parsed = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()

                        try {

                            val root = org.json.JSONObject(nextDataJson)

                            val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")

                            Log.d("TR_WV", "[$providerName] pageProps keys: ${pageProps?.keys()?.asSequence()?.toList()}")

                            

                            // Tüm olası arama sonuç array key'lerini dene

                            val animeArray = pageProps?.optJSONArray("animes")

                                ?: pageProps?.optJSONArray("results")

                                ?: pageProps?.optJSONArray("data")

                                ?: pageProps?.optJSONArray("searchResults")

                                ?: pageProps?.optJSONArray("items")

                                ?: pageProps?.optJSONArray("anime")

                                ?: pageProps?.optJSONArray("list")

                            

                            if (animeArray != null) {

                                Log.d("TR_WV", "[$providerName] animeArray length=${animeArray.length()}")

                                for (i in 0 until animeArray.length()) {

                                    val item = animeArray.optJSONObject(i) ?: continue

                                    val name = item.optString("name").takeIf { it.isNotBlank() }

                                        ?: item.optString("title").takeIf { it.isNotBlank() }

                                        ?: item.optString("adi").takeIf { it.isNotBlank() }

                                        ?: item.optString("anime_adi").takeIf { it.isNotBlank() }

                                        ?: continue

                                    val slug = item.optString("slug").takeIf { it.isNotBlank() }

                                        ?: item.optString("url").takeIf { it.isNotBlank() }

                                        ?: item.optString("id").takeIf { it.isNotBlank() }

                                        ?: continue

                                    val itemUrl = if (slug.startsWith("http")) slug else "${api.mainUrl}/anime/$slug"

                                    val poster = item.optString("poster").takeIf { it.isNotBlank() }

                                        ?: item.optString("image").takeIf { it.isNotBlank() }

                                        ?: item.optString("img").takeIf { it.isNotBlank() }

                                        ?: item.optString("kapak").takeIf { it.isNotBlank() }

                                    

                                    val searchResponse = try {

                                        val cls = Class.forName("com.lagradost.cloudstream3.AnimeSearchResponse")

                                        val ctor = cls.constructors.firstOrNull { it.parameterCount >= 3 } ?: continue

                                        val args = Array<Any?>(ctor.parameterCount) { null }

                                        args[0] = name; args[1] = itemUrl; args[2] = api.name

                                        if (ctor.parameterCount > 3) args[3] = com.lagradost.cloudstream3.TvType.Anime

                                        if (ctor.parameterCount > 4) args[4] = poster

                                        ctor.newInstance(*args) as? com.lagradost.cloudstream3.SearchResponse

                                    } catch (refEx: Exception) {

                                        Log.w(TAG, "[$providerName] Reflection create failed: ${refEx.message}"); null

                                    }

                                    if (searchResponse != null) parsed.add(searchResponse)

                                }

                            } else {

                                // pageProps direkt bir item mi? (tek sonuç sayfası)

                                Log.d("TR_WV", "[$providerName] animeArray null — pageProps raw (1KB): ${pageProps?.toString()?.take(1000)}")

                            }

                        } catch (jsonEx: Exception) {

                            Log.w(TAG, "[$providerName] __NEXT_DATA__ JSON parse hatası: ${jsonEx.message}")

                            Log.d("TR_WV", "[$providerName] Ham JSON (1KB): ${nextDataJson.take(1000)}")

                        }

                        

                        Log.d(TAG, "[$providerName] WebView JS search '$query' → ${parsed.size} sonuç")

                        return@withTimeoutOrNull parsed

                        

                    } catch (e: Exception) {

                        Log.e(TAG, "[$providerName] WebView JS search HATA: ${e.message}", e)

                        return@withTimeoutOrNull emptyList()

                    }

                }

                

                // Encode the search query if the provider requires path/custom API queries

                val isTurkishPathSearch = providerName.contains("AnimeciX", ignoreCase = true) ||

                                          providerName.contains("Animely", ignoreCase = true) ||

                                          providerName.contains("Elysium", ignoreCase = true) ||

                                          providerName.contains("AnimPow", ignoreCase = true) ||

                                          providerName.contains("AsyaAnimeleri", ignoreCase = true) ||

                                          providerName.contains("AsyaWatch", ignoreCase = true) ||

                                          providerName.contains("CizgiMax", ignoreCase = true)



                suspend fun doSearchAttempt(searchQuery: String): List<SearchResponse>? {

                    var results: List<SearchResponse>? = null



                    // 1. Try paginated search if overridden and not marked unsupported

                    val supportsPaginated = isMethodOverridden(api, "search", String::class.java, Int::class.javaPrimitiveType ?: Int::class.java, kotlin.coroutines.Continuation::class.java)

                    if (supportsPaginated && !unsupportedMethods.contains("$providerName:search_paginated")) {

                        try {

                            Log.d(TAG, "[$providerName] api.search('$searchQuery', 1) çağrılıyor...")

                            results = api.search(searchQuery, 1)?.items

                        } catch (cancel: kotlinx.coroutines.CancellationException) {

                            throw cancel

                        } catch (t: Throwable) {

                            val isNotImpl = t is kotlin.NotImplementedError || t.message?.contains("NotImplementedError", ignoreCase = true) == true

                            if (isNotImpl) {

                                unsupportedMethods.add("$providerName:search_paginated")

                                Log.d(TAG, "[$providerName] search(query, 1) desteklenmiyor (NotImplementedError).")

                            } else {

                                CsPluginStatusTracker.recordSearchFailure(providerName, t)

                                Log.w(TAG, "[$providerName] search(query, 1) hata verdi: ${t.message}")

                                Log.e(SERR, "❌ SEARCH(q,1) HATA [$providerName] — ${t.javaClass.simpleName}: ${t.message}")

                                throw t

                            }

                        }

                    }



                    // 2. Try standard search if overridden and not marked unsupported

                    val supportsStandard = isMethodOverridden(api, "search", String::class.java, kotlin.coroutines.Continuation::class.java)

                    if (shouldTryNextSearchMethod(results) && supportsStandard && !unsupportedMethods.contains("$providerName:search")) {

                        try {

                            Log.d(TAG, "[$providerName] api.search('$searchQuery') çağrılıyor...")

                            results = api.search(searchQuery)

                        } catch (cancel: kotlinx.coroutines.CancellationException) {

                            throw cancel

                        } catch (t: Throwable) {

                            val isNotImpl = t is kotlin.NotImplementedError || t.message?.contains("NotImplementedError", ignoreCase = true) == true

                            if (isNotImpl) {

                                unsupportedMethods.add("$providerName:search")

                                Log.d(TAG, "[$providerName] search(query) desteklenmiyor (NotImplementedError).")

                            } else {

                                CsPluginStatusTracker.recordSearchFailure(providerName, t)

                                Log.w(TAG, "[$providerName] search(query) hata verdi: ${t.message}")

                                Log.e(SERR, "❌ SEARCH(q) HATA [$providerName] — ${t.javaClass.simpleName}: ${t.message}")

                                throw t

                            }

                        }

                    }



                    // 3. Try quickSearch if overridden and not marked unsupported

                    val supportsQuick = isMethodOverridden(api, "quickSearch", String::class.java, kotlin.coroutines.Continuation::class.java)

                    if (shouldTryNextSearchMethod(results) && supportsQuick && !unsupportedMethods.contains("$providerName:quickSearch")) {

                        try {

                            Log.d(TAG, "[$providerName] api.quickSearch('$searchQuery') çağrılıyor...")

                            results = api.quickSearch(searchQuery)

                        } catch (cancel: kotlinx.coroutines.CancellationException) {

                            throw cancel

                        } catch (t: Throwable) {

                            val isNotImpl = t is kotlin.NotImplementedError || t.message?.contains("NotImplementedError", ignoreCase = true) == true

                            if (isNotImpl) {

                                unsupportedMethods.add("$providerName:quickSearch")

                                Log.d(TAG, "[$providerName] quickSearch(query) desteklenmiyor (NotImplementedError).")

                            } else {

                                CsPluginStatusTracker.recordSearchFailure(providerName, t)

                                Log.w(TAG, "[$providerName] quickSearch(query) hata verdi: ${t.message}")

                                Log.e(SERR, "❌ QUICK_SEARCH HATA [$providerName] — ${t.javaClass.simpleName}: ${t.message}")

                                throw t

                            }

                        }

                    }



                    // 4. Fallback if reflection returned false for all methods but we haven't succeeded

                    if (results == null && !supportsPaginated && !supportsStandard && !supportsQuick) {

                        Log.d(TAG, "[$providerName] Metot tespiti yapılamadı, tüm varyantlar sırayla deneniyor...")

                        try {

                            results = api.search(searchQuery, 1)?.items

                        } catch (cancel: kotlinx.coroutines.CancellationException) {

                            throw cancel

                        } catch (t: Throwable) {

                            val isNotImpl = t is kotlin.NotImplementedError || t.message?.contains("NotImplementedError", ignoreCase = true) == true

                            if (!isNotImpl) CsPluginStatusTracker.recordSearchFailure(providerName, t)

                            try {

                                results = api.search(searchQuery)

                            } catch (cancel2: kotlinx.coroutines.CancellationException) {

                                throw cancel2

                            } catch (t2: Throwable) {

                                val isNotImpl2 = t2 is kotlin.NotImplementedError || t2.message?.contains("NotImplementedError", ignoreCase = true) == true

                                if (!isNotImpl2) CsPluginStatusTracker.recordSearchFailure(providerName, t2)

                                try {

                                    results = api.quickSearch(searchQuery)

                                } catch (cancel3: kotlinx.coroutines.CancellationException) {

                                    throw cancel3

                                } catch (t3: Throwable) {

                                    val isNotImpl3 = t3 is kotlin.NotImplementedError || t3.message?.contains("NotImplementedError", ignoreCase = true) == true

                                    if (!isNotImpl3) CsPluginStatusTracker.recordSearchFailure(providerName, t3)

                                    throw t3

                                }

                            }

                        }

                    }

                    return results

                }



                var results: List<SearchResponse>? = null

                if (isTurkishPathSearch) {

                    val encoded = encodePathQuery(query)

                    try {

                        results = doSearchAttempt(encoded)

                    } catch (cancel: kotlinx.coroutines.CancellationException) {

                        throw cancel

                    } catch (t: Throwable) {

                        Log.w(TAG, "[$providerName] Path-encoded query '$encoded' failed. Falling back to standard query '$query'. Error: ${t.message}")

                    }



                    // If results are null/empty, retry with raw query

                    if (results.isNullOrEmpty()) {

                        try {

                            results = doSearchAttempt(query)

                        } catch (cancel: kotlinx.coroutines.CancellationException) {

                            throw cancel

                        } catch (t: Throwable) {

                            Log.w(TAG, "[$providerName] Standard query search also failed: ${t.message}")

                        }

                    }

                } else {

                    try {

                        results = doSearchAttempt(query)

                    } catch (cancel: kotlinx.coroutines.CancellationException) {

                        throw cancel

                    } catch (t: Throwable) {

                        Log.w(TAG, "[$providerName] Search failed: ${t.message}")

                    }

                }



                val finalResults = results ?: emptyList()

                if (finalResults.isEmpty()) {

                    Log.d(TAG, "[$providerName] search('$query') → 0 sonuç")

                    CsTrace.info(providerName, "search", "'$query' → 0 sonuç")

                } else {

                    Log.d(TAG, "[$providerName] search('$query') → ${finalResults.size} sonuç")

                    CsTrace.info(providerName, "search", "'$query' → ${finalResults.size} sonuç")

                }

                finalResults

            } catch (cancel: kotlinx.coroutines.CancellationException) {

                throw cancel // always rethrow so coroutine scope can cancel properly

            } catch (e: Throwable) {

                // Catches both Exception AND Error subclasses (including NotImplementedError)

                val resource = throwAbleToResource<List<SearchResponse>>(e)

                val errMsg = if (resource is Resource.Failure) resource.errorString else (e.message ?: e.javaClass.simpleName)

                CsPluginStatusTracker.recordSearchFailure(api.name, e)

                Log.e(TAG, "[${api.name}] search('$query') HATA: ${e.javaClass.simpleName}: $errMsg")

                Log.e(SERR, "💥 SEARCH CRASH [${api.name}] query='$query' — ${e.javaClass.simpleName}: $errMsg\n${e.getStackTracePretty()}")

                emptyList()

            }

            } ?: run {

                // v2.4.240: zaman kutusu doldu — bu "sitede içerik yok" CEVABI DEĞİLDİR.

                // Eski davranış `?: emptyList()` ile bu durumu gerçek sıfır sonuçtan ayırt

                // edilemez hâle getiriyordu; kullanıcı "eklenti ölü", geliştirici "site

                // sonuç vermiyor" sanıyordu. Artık iz bırakılır ve UI'da gerçek sebep görünür.

                Log.w(

                    TAG,

                    "[${api.name}] ⏱️ search('$query') ${searchStageMs}ms içinde tamamlanamadı — " +

                        "boş sonuç (zaman aşımı; kuyruk bekleme dahil)."

                )

                Log.e(SERR, "⏱️ SEARCH TIMEOUT [${api.name}] query='$query' — ${searchStageMs}ms aşıldı. Bu 'içerik yok' DEĞİLDİR; arama iptal edildi.")

                CsTrace.warn(

                    api.name,

                    "search-timeout",

                    "Arama ${searchStageMs}ms'de tamamlanmadı (arama kapısı kuyruğu + ağ) — sonuç bilinmiyor"

                )

                if (CsPluginStatusTracker.getErrorMessage(api.name) == null) {

                    CsPluginStatusTracker.recordSkip(

                        api.name,

                        "Arama ${searchStageMs / 1000} sn içinde tamamlanamadı (zaman aşımı) — içerik yok sanılmasın"

                    )

                }

                emptyList()

            }

        }

    }



    internal suspend fun safeLoad(api: MainAPI, url: String): LoadResponse? {

        ensurePluginReady(api)

        // Session bloklist kontrolü

        if (CsPluginStatusTracker.isBlocked(api.name)) {

            Log.w(TAG, "[${api.name}] safeLoad: Engellendi — atlanıyor.")

            return null

        }

        // NOT: KNOWN_BROKEN_PLUGINS kontrolü kasıtlı olarak burada YOK.

        // Detay sayfası (İçerik bilgisi, bölüm listesi) erişilebilmeli.

        // Engel sadece stream çekme aşamasında (getStreamsForUrl) uygulanır.

        val normalizeUrl = { u: String -> u.replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/') }

        val currentDomain = normalizeUrl(api.mainUrl)

        if (KNOWN_BROKEN_DOMAINS.any { currentDomain.contains(it) }) {

            // Sessiz atlama YOK — detay/bölüm yüklemesi de denenmeli; hata tracker'a düşer.

            Log.w(TAG, "[${api.name}] safeLoad: Domain (${api.mainUrl}) ölü domain listesinde — yine de deneniyor.")

        }

        // v2.4.240 (KN-B): load da arama ile aynı tuzağa düşüyordu (sabit 15 sn).

        // CF çözümü sürerken kutu patlıyor → "aramada bulundu ama detay alınamadı" → 0 stream.

        val loadStageMs = stageTimeoutMs(api, LOAD_STAGE_TIMEOUT_MS)

        val loadBoxStart = System.currentTimeMillis()

        val loadOutcome = loadSemaphore.withPermit {

            withTimeoutOrNull(loadStageMs) {

                try {

                kotlinx.coroutines.delay(300)

                Log.d(TAG, "[${api.name}] load('$url') çağrılıyor...")

                val resp = api.load(url)

                Log.d(TAG, "[${api.name}] load tamamlandı: ${resp?.javaClass?.simpleName}")

                resp

            } catch (cancel: kotlinx.coroutines.CancellationException) {

                throw cancel

            } catch (e: Throwable) {

                // Catches both Exception AND Error subclasses (including NotImplementedError)

                val resource = throwAbleToResource<LoadResponse>(e)

                val errMsg = if (resource is Resource.Failure) resource.errorString else (e.message ?: e.javaClass.simpleName)

                CsPluginStatusTracker.recordFailure(api.name, e)

                Log.e(TAG, "[${api.name}] load('$url') HATA: ${e.javaClass.simpleName}: $errMsg")

                Log.e(SERR, "💥 LOAD CRASH [${api.name}] url='$url' — ${e.javaClass.simpleName}: $errMsg\n${e.getStackTracePretty()}")

                null

            }

            }  // withTimeoutOrNull

        }  // withPermit

        // null iki anlama gelebilir: gerçek "bulunamadı" veya zaman aşımı. Süre ölçümüyle

        // ayrıştırılır ve timeout iz olarak bırakılır (eski davranışta tamamen sessizdi).

        if (loadOutcome == null) {

            val spent = System.currentTimeMillis() - loadBoxStart

            if (spent >= loadStageMs) {

                Log.w(TAG, "[${api.name}] ⏱️ load('$url') ${loadStageMs}ms içinde tamamlanamadı (${spent}ms geçti) — detay alınamadı.")

                CsTrace.warn(api.name, "load-timeout", "load ${loadStageMs}ms'de tamamlanmadı — detay/bölüm verisi alınamadı (timeout)")

                if (CsPluginStatusTracker.getErrorMessage(api.name) == null) {

                    CsPluginStatusTracker.recordSkip(api.name, "Detay yükleme ${loadStageMs / 1000} sn içinde tamamlanamadı (zaman aşımı)")

                }

            }

        }

        return loadOutcome

    }



    private fun hasSyncDataSupport(resp: LoadResponse): Boolean {

        return try {

            resp.javaClass.getDeclaredField("syncData")

            true

        } catch (_: Exception) {

            try {

                resp.javaClass.getMethod("getSyncData")

                true

            } catch (_: Exception) {

                false

            }

        }

    }



    private fun loadResponseMatches(

        resp: LoadResponse?,

        targetImdb: String?,

        targetMal: Int?,

        targetAniList: Int?,

        targetTmdb: Int?,

        targetKitsu: Int?

    ): Boolean {

        if (resp == null) return false

        

        // Use reflection to access syncData from LoadResponse if available

        val syncData = try {

            val field = resp.javaClass.getDeclaredField("syncData")

            field.isAccessible = true

            @Suppress("UNCHECKED_CAST")

            field.get(resp) as? Map<String, String>

        } catch (e: Exception) {

            try {

                val method = resp.javaClass.getMethod("getSyncData")

                @Suppress("UNCHECKED_CAST")

                method.invoke(resp) as? Map<String, String>

            } catch (e2: Exception) {

                null

            }

        } ?: return false



        Log.d(TAG, "[ID-Mapping] Candidate syncData: $syncData")



        // Parse any JSON string values in syncData to support nested mappings (e.g. {"" -> "{\"Tmdb\":\"209867\"}"})

        val mergedSyncData = syncData.toMutableMap()

        for ((key, value) in syncData) {

            val trimmed = value.trim()

            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {

                try {

                    val json = org.json.JSONObject(trimmed)

                    for (jsonKey in json.keys()) {

                        mergedSyncData[jsonKey.lowercase(Locale.ROOT)] = json.optString(jsonKey)

                    }

                } catch (_: Exception) {}

            }

        }



        // Check TMDB ID

        val candidateTmdb = mergedSyncData["tmdb"]?.toIntOrNull()

        if (candidateTmdb != null && targetTmdb != null) {

            if (candidateTmdb == targetTmdb) return true

        }



        // Check MAL ID

        val candidateMal = (mergedSyncData["mal"] ?: mergedSyncData["myanimelist"])?.toIntOrNull()

        if (candidateMal != null && targetMal != null) {

            if (candidateMal == targetMal) return true

        }



        // Check AniList ID

        val candidateAniList = mergedSyncData["anilist"]?.toIntOrNull()

        if (candidateAniList != null && targetAniList != null) {

            if (candidateAniList == targetAniList) return true

        }



        // Check Kitsu ID

        val candidateKitsu = mergedSyncData["kitsu"]?.toIntOrNull()

        if (candidateKitsu != null && targetKitsu != null) {

            if (candidateKitsu == targetKitsu) return true

        }



        // Check IMDb ID

        val candidateImdb = mergedSyncData["imdb"]

        if (!candidateImdb.isNullOrBlank() && !targetImdb.isNullOrBlank()) {

            if (candidateImdb.trim().equals(targetImdb.trim(), ignoreCase = true)) return true

        }



        return false

    }



    // Delegated to CsTitleMatcher — see CsTitleMatcher.kt

    private fun getBestTitleSimilarity(

        candidateName: String,

        mainTitle: String,

        altTitles: List<String>,

        isMovie: Boolean? = null

    ): Double = CsTitleMatcher.getBestTitleSimilarity(candidateName, mainTitle, altTitles, isMovie)



    /**

     * Manual verification is offered only for explicit bot/WAF challenge signatures.

     * Generic 403/503, DNS, TLS and timeout errors must remain ordinary network failures.

     */

    private fun isBotProtectionChallenge(errorMsg: String): Boolean =

        CsProtectionClassifier.isWafChallenge(errorMsg) || CsProtectionClassifier.isDdosGuard(errorMsg)



    // Delegated to CsTitleMatcher — see CsTitleMatcher.kt

    private fun findBestMatch(

        results: List<SearchResponse>,

        mainTitle: String,

        altTitles: List<String>,

        targetYear: Int?,

        targetSeason: Int? = null,

        targetEpisode: Int? = null,

        isMovie: Boolean? = null

    ): SearchResponse? = CsTitleMatcher.findBestMatch(

        results, mainTitle, altTitles, targetYear, targetSeason, targetEpisode, isMovie

    )



    // ─── Episode extraction helpers — Delegated to CsEpisodeMatcher ──────────



    // Delegated to CsEpisodeMatcher — see CsEpisodeMatcher.kt

    private fun findEpisodeData(response: LoadResponse, season: Int, episode: Int): String? =

        CsEpisodeMatcher.findEpisodeData(response, season, episode)



    /** Preserve every audio rendition exposed by CloudStream; the legacy single URL is only a fallback. */

    private fun externalAudioTrackInputs(link: ExtractorLink): List<ExternalAudioTrackInput> =

        link.audioTracks.mapIndexedNotNull { index, audio ->

            val url = audio.url.trim().takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null

            ExternalAudioTrackInput(

                url = url,

                headers = audio.headers?.toMap().orEmpty(),

                // CloudStream AudioFile currently exposes URL + headers, not track title/language.

                name = "Harici ses ${index + 1}"

            )

        }



    /** Safely extract videoSize (bytes) from ExtractorLink without compile-time visibility issues. */

    private fun extractVideoSize(link: ExtractorLink): Long? = runCatching {

        val getter = link.javaClass.methods.firstOrNull { it.name == "getVideoSize" }

        if (getter != null) {

            getter.isAccessible = true

            getter.invoke(link) as? Long

        } else {

            val field = generateSequence(link.javaClass as Class<*>) { it.superclass }

                .flatMap { it.declaredFields.asSequence() }

                .firstOrNull { it.name == "videoSize" }

            field?.isAccessible = true

            field?.get(link) as? Long

        }

    }.getOrNull()



    /**

     * CloudStream `ExtractorLink.quality` değerini gerçek bir kalite etiketine çevirir.

     *

     * ÖNEMLİ: CloudStream'de `Qualities.Unknown.value = 400`'dür. Bu bir çözünürlük DEĞİLDİR;

     * eskiden "400p" olarak basılıyor ve kullanıcıya yanlış bilgi veriyordu. Artık bilinmeyen

     * değerler için `null` döndürülür ve arayüzde kalite rozeti hiç gösterilmez.

     */

    private fun getQualityString(quality: Int?): String? =

        com.kitsugi.animelist.data.repository.StreamInfoResolver.csQualityHeightOrNull(quality)

            ?.let { com.kitsugi.animelist.data.repository.StreamInfoResolver.heightToLabel(it) }



    /** Kaynağın bildirdiği çözünürlük; bilinmiyorsa null (tahmin yok). */

    private fun getQualityValue(quality: Int?): Int? =

        com.kitsugi.animelist.data.repository.StreamInfoResolver.csQualityHeightOrNull(quality)



    /** Extractor etiketinde ("1080p", "HD 720") AÇIKÇA yazan çözünürlük; yoksa null. */

    private fun labelHeight(label: String?): Int? =

        com.kitsugi.animelist.data.repository.StreamInfoResolver.parseHeightFromText(label)



    suspend fun searchAllAddons(context: android.content.Context, query: String): List<Pair<MainAPI, SearchResponse>> = withContext(Dispatchers.IO) {

        if (!isDomainListFetched.get()) {

            runnerScope.launch {

                fetchRemoteDomains()

            }

        }

        val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context.applicationContext)

        val enabledPlugins = db.csPluginDao().getEnabledPlugins()

        for (plugin in enabledPlugins) {

            try {

                com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, plugin.id)

            } catch (e: Exception) {

                Log.e(TAG, "Failed to load extension ${plugin.name} during global search: ${e.message}")

            }

        }

        // v2.4.240 (KN-D-2): yeni aramada oturum blokları temizlenir — StreamViewModel

        // bunu zaten yapıyordu; bu yol (vitirin/film-fragman fallback ve eklenti arama

        // sayfaları) yapmıyordu. Bir önceki aramada 3 hataya düşen eklenti burada

        // "Engellendi — atlanıyor" ile SESSİZCE es geçiliyordu.

        CsPluginStatusTracker.clear()

        clearUnsupportedMethodsCache()



        val enabledIds = enabledPlugins.map { it.id }

        val activeApis = com.lagradost.cloudstream3.APIHolder.allProviders.filter { api ->

            isApiEnabledFor(api, enabledIds)

        }

        if (activeApis.isEmpty()) {

            return@withContext emptyList()

        }

        val results = mutableListOf<Pair<MainAPI, SearchResponse>>()

        kotlinx.coroutines.supervisorScope {

            val jobs = activeApis.map { api ->

                async {

                    try {

                        ensurePluginReady(api)

                        val searchRes = safeSearch(api, query)

                        synchronized(results) {

                            searchRes.forEach { results.add(api to it) }

                        }

                    } catch (e: Throwable) {

                        if (e is kotlinx.coroutines.CancellationException) throw e

                        Log.e(TAG, "Search failed for provider ${api.name}: ${e.javaClass.simpleName}: ${e.message}")

                    }

                }

            }

            jobs.forEach {

                try {

                    it.await()

                } catch (e: Throwable) {

                    if (e is kotlinx.coroutines.CancellationException) throw e

                    Log.e(TAG, "Await failed: ${e.message}")

                }

            }

        }

        results

    }



    suspend fun getStreamsForUrl(

        api: MainAPI,

        url: String,

        season: Int,

        episode: Int,

        isMovie: Boolean = false,

        mediaType: MediaType? = null

    ): List<StreamSource> = withContext(Dispatchers.IO) {

        val ownedBuildReason = CsOwnedPluginProfiles.episodeUnsupportedReason(

            providerName = api.name,

            pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin),

            mediaType = mediaType,

            isMovie = isMovie

        )

        if (ownedBuildReason != null) {

            Log.i(TAG, "[${api.name}] $ownedBuildReason")

            CsTrace.info(api.name, "type-filter", ownedBuildReason)

            CsPluginStatusTracker.recordSkip(api.name, ownedBuildReason)

            return@withContext emptyList()

        }

        ensurePluginReady(api)

        if (api.name in KNOWN_BROKEN_PLUGINS) return@withContext emptyList()

        val resolvedMediaType = CsPluginTypeCompatibility.infer(

            mediaType = mediaType,

            isMovie = isMovie,

            supportedTypes = api.supportedTypes.map { it.name }

        )

        val providerType = when (resolvedMediaType) {

            MediaType.Movie -> com.lagradost.cloudstream3.TvType.Movie

            MediaType.TvShow -> com.lagradost.cloudstream3.TvType.TvSeries

            MediaType.Anime -> com.lagradost.cloudstream3.TvType.Anime

            MediaType.Manga, null -> com.lagradost.cloudstream3.TvType.Anime

        }

        val isResolvedMovie = CsPluginTypeCompatibility.resolveIsMovie(resolvedMediaType, isMovie)

        val searchResponse = api.newAnimeSearchResponse(

            name = api.name,

            url = url,

            type = providerType,

            fix = false

        )

        loadAndExtractStreams(api, searchResponse, season, episode, isResolvedMovie)

    }

}



/**

 * HDFilmCehennemi eklentisi göreli altyazı yollarının sonuna `/` ekliyor

 * (`"${mainUrl}${subFile}/"`). Dosya uzantılı altyazılarda bu sonek dosyayı

 * bulunamaz hale getirir; uzantıdan sonraki sondaki `/` kaldırılır.

 */

private val CS_SUB_TRAILING_SLASH_REGEX = Regex("""\.(vtt|srt|ass|ssa|webvtt)/$""", RegexOption.IGNORE_CASE)



internal fun normalizeCsSubtitleUrl(url: String): String =

    if (CS_SUB_TRAILING_SLASH_REGEX.containsMatchIn(url)) url.trimEnd('/') else url

