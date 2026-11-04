package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.cloudstream3.utils.extractorApis
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

object CsRuntimeInit {
    private var wrappedContext: Context? = null

    @OptIn(Prerelease::class, UnsafeSSL::class)
    fun init(context: Context) {
        try {
            val wrapped = com.kitsugi.animelist.KitsugiApplication.getDynamicContext(context)
            com.lagradost.api.setContext(java.lang.ref.WeakReference(wrapped))

            // Pre-seed "DomainListesi" in SharedPreferences with current domains.
            // Turkish plugins (Anizium, Animeler, DiziPal, etc.) read this SharedPreferences
            // in their constructors. Setting last_domain_update prevents dead Kraptor123 network requests
            // and guarantees plugins have valid domains immediately without blocking or 404s.
            try {
                val sp = wrapped.getSharedPreferences("DomainListesi", Context.MODE_PRIVATE)
                val editor = sp.edit()
                val initialDomains = mapOf(
                    "Animeler"           to "https://animeler.pw",
                    "Animely"            to "https://animely.net",
                    "AnimPow"            to "https://animpow.com",
                    "Anizium"            to "https://api.anizium.co",
                    "AsyaFanatiklerim"   to "https://asyafanatiklerim.com",
                    "DiziAsia"           to "https://diziasia.com",
                    "DiziAsya"           to "https://api.diziasya.com",
                    "DiziBox"            to "https://www.dizibox.live",
                    "DiziFilmORG"        to "https://dizifilmizle.to",
                    "Dizigecesi"         to "https://dizigecesi.com",
                    "DiziGecesi"         to "https://dizigecesi.com",
                    "DiziKorea"          to "https://dizikorea3.com",
                    "DiziLife"           to "https://dizi75.life",
                    "Dizilla"            to "https://dizilla.now",
                    "DiziMom"            to "https://www.dizimom.help",
                    "DiziPal"            to "https://dizipal3008.com",
                    "DiziPalOriginal"    to "https://dizipal3008.com",
                    "DiziPalOrijinal"    to "https://dizipal3008.com",
                    "Dizipod"            to "https://dizipod.com",
                    "DiziPod"            to "https://dizipod.com",
                    "DiziYo"             to "https://www.diziyo.so",
                    "DiziYou"            to "https://www.diziyou.one",
                    "DramaDizilerim"     to "https://dramaflix.net/tr",
                    "Dramaizle"          to "https://dramaflix.net/tr",
                    "FilmBOL"            to "https://filmbol.org",
                    "FilmBip"            to "https://filmbip.com",
                    "FilmEkseni"         to "https://filmekseni.vip",
                    "FilmHane"           to "https://www.filmhane.shop",
                    "FilmMakinesi"       to "https://filmmakinesi.to",
                    "FilmModu"           to "https://www.filmmodu.one",
                    "Filmzal"            to "https://filmzal.me",
                    "FullHDFilm"         to "https://fullhdfilm.pro",
                    "FullHDFilmizlesene" to "https://www.fullhdfilmizlesene.mx",
                    "GinikoCanli"        to "https://www.giniko.com",
                    "HDFilmCehennemi"    to "https://www.hdfilmcehennemi.nl",
                    "HDFilmDelisi"       to "https://hdfilmdelisi.one",
                    "HDFilmIzle"         to "https://www.hdfilmizle.live",
                    "JetFilmizle"        to "https://jetfilmizle.now",
                    "KickTR"             to "https://kick.com",
                    "KraptorPlus"        to "https://a.111477.xyz",
                    "KultFilmler"        to "https://kultfilmler.net",
                    "MirrorVerse"        to "https://net77.cc",
                    "OnePaceTr"          to "https://www.onepacetr.net",
                    "OnePaceTR"          to "https://www.onepacetr.net",
                    "OpenAnime"          to "https://openani.me",
                    "RecTV"              to "https://m.prectv72.lol",
                    "recTV"              to "https://m.prectv72.lol/api",
                    "SeiCode"            to "https://seiwatch.net",
                    "SelcukFlix"         to "https://selcukflix.com",
                    "SetFilmIzle"        to "https://www.setfilmizle.uk",
                    "SezonlukDizi"       to "https://sezonlukdizi.cc",
                    "SinemaCX"           to "https://sinemacc.com",
                    "Sinewix"            to "https://ydfvfdizipanel.ru",
                    "Sinezy"             to "https://sinezy.to",
                    "Syncler"            to "https://syncler.net",
                    "TorrentFilm"        to "https://torrentfilmindir.net",
                    "Torrential"         to "https://api.real-debrid.com",
                    "TRanimaci"          to "https://tranimaci.com",
                    "TrAnimeIzle"        to "https://www.tranimeizle.live",
                    "TurkAnime"          to "https://www.turkanime.tv",
                    "Turkdizileri"       to "https://turkdizileri.com",
                    "WFilmizle"          to "https://wfilmizle.net",
                    "WFilmİzle"          to "https://wfilmizle.net",
                    "WebDramaTurkey"     to "https://webdramaturkey2.com",
                    "WebteIzle"          to "https://webteizle3.xyz",
                    "YTS"                to "https://web.yts.gg",
                    "YabanciDizi"        to "https://yabancidizi.news",
                    "YeniKaynak"         to "https://www.yenikaynak.com",
                    "YesilCamTv"         to "https://yesilcamtv.com",
                    "Youtube"            to "https://www.youtube.com"
                )
                for ((k, v) in initialDomains) {
                    editor.putString(k, v)
                }
                editor.putLong("last_domain_update", System.currentTimeMillis())
                editor.apply()
                android.util.Log.d("CsRuntimeInit", "Pre-seeded ${initialDomains.size} domains into DomainListesi SharedPreferences")
            } catch (spEx: Exception) {
                android.util.Log.w("CsRuntimeInit", "Failed to pre-seed DomainListesi: ${spEx.message}")
            }

            // Build a standard OkHttp client with timeouts and redirect handling.
            // Android 10+ already includes Conscrypt as the default TLS provider natively;
            // no separate registration needed.
            //
            // v2.4.226: OkHttp Cache KALDIRILDI. Eklenti istemcisi bir SCRAPER istemcisidir:
            // arama/detay sayfaları sunucu tarafında `Cache-Control: max-age` taşıyabildiği için
            // 50 MiB'lik disk önbelleği bayat (bazen boş/CF-challenge anında donmuş) arama
            // sonuçlarını tekrar tekrar servis ediyor, eklentiler süresiz "0 sonuç" dönüyordu.
            // Resmi CloudStream istemcisinde disk cache yoktur; tazelik esastır.
            val okHttpClient = OkHttpClient.Builder()
                .dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // First in the chain: also protects interceptors appended by plugins.
                .addInterceptor(PluginNetworkFailureGuard)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val urlStr = original.url.toString()
                    val allowAniziumV25Shim = CsOwnedPluginProfiles.shouldApplyAniziumV25AuthShim(
                        CsPluginLoader.manifestVersionForPluginId("Anizium")
                    )
                    var newUrl = urlStr
                    var hostHeader: String? = null

                    // Kraptor / DomainListesi dead repository stub (33 eklentinin tamamı için güncel adresler)
                    if (urlStr.contains("Kraptor123/domainListesi") || urlStr.contains("eklenti_domainleri.txt")) {
                        val domainList = listOf(
                            "Animeler:https://animeler.pw",
                            "Animely:https://animely.net",
                            "AnimPow:https://animpow.com",
                            "Anizium:https://api.anizium.co",
                            "AsyaFanatiklerim:https://asyafanatiklerim.com",
                            "DiziAsia:https://diziasia.com",
                            "DiziAsya:https://api.diziasya.com",
                            "DiziFilmORG:https://dizifilmizle.to",
                            "Dizigecesi:https://dizigecesi.com",
                            "DiziGecesi:https://dizigecesi.com",
                            "DiziLife:https://dizi75.life",
                            "DiziPal:https://dizipal3008.com",
                            "DiziPalOrijinal:https://dizipal3008.com",
                            "DiziPalOriginal:https://dizipal3008.com",
                            "Dizipod:https://dizipod.com",
                            "DiziPod:https://dizipod.com",
                            "DiziYo:https://www.diziyo.so",
                            "FilmEkseni:https://filmekseni.vip",
                            "FilmHane:https://www.filmhane.shop",
                            "Filmzal:https://filmzal.me",
                            "GinikoCanli:https://www.giniko.com",
                            "HDFilmDelisi:https://hdfilmdelisi.one",
                            "KickTR:https://kick.com",
                            "KraptorPlus:https://a.111477.xyz",
                            "MirrorVerse:https://net77.cc",
                            "OnePaceTr:https://www.onepacetr.net",
                            "OnePaceTR:https://www.onepacetr.net",
                            "OpenAnime:https://openani.me",
                            "SeiCode:https://seiwatch.net",
                            "Sinezy:https://sinezy.to",
                            "Syncler:https://syncler.net",
                            "Torrential:https://api.real-debrid.com",
                            "TrAnimeIzle:https://www.tranimeizle.live",
                            "Turkdizileri:https://turkdizileri.com",
                            "WFilmizle:https://wfilmizle.net",
                            "YeniKaynak:https://www.yenikaynak.com",
                            "YesilCamTv:https://yesilcamtv.com",
                            "YTS:https://web.yts.gg"
                        ).joinToString("|")

                        return@addInterceptor okhttp3.Response.Builder()
                            .request(original)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(domainList.toResponseBody("text/plain".toMediaTypeOrNull()))
                            .build()
                    }

                    // The route was decoded from Anizium v25. Do not apply it to a newer/unknown
                    // plugin build until its host contract has been re-audited.
                    if (allowAniziumV25Shim &&
                        (urlStr.contains("anizium.co") || urlStr.contains("anizium.de"))
                    ) {
                        if (urlStr.contains("x.anizium.co/page") || urlStr.contains("x.anizium.co/anime")) {
                            newUrl = urlStr.replace("x.anizium.co", "api.anizium.co")
                        } else if (urlStr.contains("x.anizium.de/page") || urlStr.contains("x.anizium.de/anime")) {
                            newUrl = urlStr.replace("x.anizium.de", "api.anizium.de")
                        }
                    }

                    if (urlStr.contains("boxyz.cfd/CDN/001_STR/boxyz.cfd/spor_v2.php")) {
                        newUrl = "https://diziboxen.help/CDN/001/002/dizibox/tv/list1.php"
                        hostHeader = "diziboxen.help"
                    } else if (urlStr.contains("dizibox.cfd/tv/cable.php")) {
                        newUrl = "https://diziboxen.help/CDN/001/002/dizibox/tv/list1.php"
                        hostHeader = "diziboxen.help"
                    } else if (urlStr.contains("dizibox.cfd")) {
                        newUrl = urlStr.replace("dizibox.cfd", "diziboxen.help/CDN/001/002/dizibox")
                        hostHeader = "diziboxen.help"
                    } else if (urlStr.contains("boxyz.cfd")) {
                        newUrl = urlStr.replace("boxyz.cfd", "diziboxen.help/CDN/001/002/dizibox")
                        hostHeader = "diziboxen.help"
                    }

                    val requestBuilder = if (newUrl != urlStr) {
                        original.newBuilder().url(newUrl)
                    } else {
                        original.newBuilder()
                    }

                    if (hostHeader != null) {
                        requestBuilder.header("Host", hostHeader)
                    }

                    // v25's Cf-Control and companion headers are binary-derived. Scope the
                    // compatibility shim to the actual loaded v25 manifest; an unknown build
                    // may have changed its authentication contract.
                    if (allowAniziumV25Shim &&
                        (urlStr.contains("anizium.co") || urlStr.contains("anizium.de") ||
                            newUrl.contains("anizium.co") || newUrl.contains("anizium.de"))
                    ) {
                        val cf = AniziumAuthHelper.generateCfControl()
                        if (cf.isNotBlank()) {
                            requestBuilder.header("Cf-Control", cf)
                        }
                        requestBuilder.header("device", "browser")
                        requestBuilder.header("site", "main")
                        if (original.header("language") == null) {
                            requestBuilder.header("language", "tr")
                        }
                    }

                    val hasUserAgent = original.header("User-Agent") != null
                    if (!hasUserAgent) {
                        requestBuilder.header("User-Agent", com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT)
                    }
                    // Eklenti hatları TÜM uygulamayı çökertmemeli. Bu en dıştaki uygulama
                    // interceptor'ı, içteki eklenti interceptor'larının (örn. DiziBox'ın
                    // DiziBoxWire'ı) TÜM isteklerini sarar. Eklentinin companion/static
                    // init'i bozuk bir regex yüzünden patlarsa (ExceptionInInitializerError
                    // → sonraki çağrılarda NoClassDefFoundError) veya bir RuntimeException
                    // fırlatırsa, bunu bir AĞ HATASINA çeviriyoruz: OkHttp ve eklentinin
                    // kendi ağ katmanı IOException'ı zaten zarifçe karşılar (boş sonuç döner),
                    // böylece tek bir bozuk eklenti süreci öldüremez.
                    // Not: IOException burada YAKALANMAZ — gerçek ağ hataları normal akışta
                    // ilerlemeye devam eder (RuntimeException, IOException'ı kapsamaz).
                    try {
                        chain.proceed(requestBuilder.build())
                    } catch (e: LinkageError) {
                        android.util.Log.e("CsRuntimeInit", "Eklenti sınıf/init hatası ($urlStr) — ağ hatası olarak işleniyor", e)
                        throw java.io.IOException("Eklenti hatası (linkage): ${e.javaClass.simpleName}", e)
                    } catch (e: RuntimeException) {
                        android.util.Log.e("CsRuntimeInit", "Eklenti çalışma zamanı hatası ($urlStr) — ağ hatası olarak işleniyor", e)
                        throw java.io.IOException("Eklenti hatası: ${e.javaClass.simpleName}", e)
                    }
                }
                .addInterceptor(com.lagradost.cloudstream3.network.CloudflareKiller())
                .addInterceptor(com.lagradost.cloudstream3.network.DdosGuardKiller(alwaysBypass = false))
                .build()

            // Build insecure OkHttp client to ignore SSL errors for insecureApp.
            // Some Turkish anime providers have expired/self-signed certificates.
            val insecureOkHttpClient = okHttpClient.newBuilder()
                .ignoreAllSSLErrors()
                .build()

            // Set both default app.baseClient and insecureApp.baseClient to use
            // the SSL-ignoring client. This is crucial for Turkish plugins that fetch
            // API endpoints from self-signed or expired SSL domains using default app calls.
            app.baseClient = insecureOkHttpClient
            insecureApp.baseClient = insecureOkHttpClient

            // v2.4.304: Close/Rapid (closeload.*, rapidrame.*, rplayer.*) CDN ailesi için
            // uygulama yerleşik extractor'ları kaydet. HDFilmCehennemi ve FilmMakinesi aynı
            // iki gömülü kaynaktan besleniyor; eklentiler bayat kaldığında veya eklentinin
            // hiç bilmediği host'larda (örn. FilmMakinesi "DUAL Rapid") oynatma zinciri
            // bundan sonra eklenti sürümünden bağımsız çalışır. Eklentiler sonradan kayıt
            // olup kendi host'larında öncelik almaya devam eder.
            com.kitsugi.animelist.data.cloudstream.extractors.KitsugiCdnJwExtractor.registerAll()

            val extractorsCount = extractorApis.size
            android.util.Log.d(
                "CsRuntimeInit",
                "Cloudstream runtime ready. Clients initialized. $extractorsCount built-in extractors registered."
            )
        } catch (e: Exception) {
            android.util.Log.e("CsRuntimeInit", "Failed to initialize Cloudstream runtime", e)
        }
    }
}

/**
 * Anizium API (api.anizium.co) için günlük XOR token üreteci.
 * Anizium backend'i 'Cf-Control' başlığı bekler, aksi halde 401 Unauthorized döner.
 */
object AniziumAuthHelper {
    private const val TOKEN_KEY = "hlxjl1c2w281ax473rt1ofgrvhyjvi"

    fun generateCfControl(): String {
        return try {
            val tz = java.util.TimeZone.getTimeZone("Europe/Istanbul")
            val sdf = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).apply {
                timeZone = tz
            }
            val weekday = sdf.format(java.util.Date()).lowercase()
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val r6 = (1..6).map { chars.random() }.joinToString("")
            val timestamp = System.currentTimeMillis()
            encodeCfControlPayload("{\"$r6\":$timestamp}", weekday)
        } catch (e: Exception) {
            ""
        }
    }

    /** Deterministic encoder used to verify the app algorithm against the audited v25 DEX token. */
    internal fun encodeCfControlPayload(payload: String, weekday: String): String {
        val key = "${TOKEN_KEY}_$weekday".toByteArray(Charsets.UTF_8)
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val hex = StringBuilder(payloadBytes.size * 2)
        for (i in payloadBytes.indices) {
            val byte = (payloadBytes[i].toInt() xor key[i % key.size].toInt()) and 0xFF
            hex.append(String.format("%02x", byte))
        }
        return hex.toString()
    }
}

