package com.kitsugi.animelist.core.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object KitsugiHttpClient {

    /**
     * Meta veri API'leri (Jikan, Shikimori, ARM, Kitsu sekme verileri) için toplam çağrı süresi.
     * Retry'lar dâhil tek bir çağrı bu sürenin üstüne çıkamaz; böylece askıda kalan bir uç nokta
     * detay sekmesini dakikalarca skeleton'da bırakmaz.
     */
    private const val METADATA_CALL_TIMEOUT_SECONDS = 20L

    /**
     * [client] ile aynı bağlantı havuzu ve dispatcher'ı paylaşır; yalnızca `callTimeout` eklenir.
     */
    val metadataClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(METADATA_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Jikan istemcisi: [metadataClient] ile aynı, ancak [RetryInterceptor] YOK.
     *
     * Neden: RetryInterceptor 429/5xx/ağ hatasında isteği kota kapısı (JikanGateway) dışında
     * anında tekrar gönderiyordu. Her Jikan çağrısı böylece 3'e kadar gerçek HTTP isteği üretip
     * dakikalık limiti tüketiyor, 429'u daha da artırıyordu. Jikan yeniden denemesi yalnızca
     * JikanGateway içinde, kota ve soğuma kurallarına uyarak yapılır.
     */
    val jikanClient: OkHttpClient by lazy {
        metadataClient.newBuilder()
            .apply { interceptors().removeAll { it is RetryInterceptor } }
            .build()
    }

    /**
     * AniList GraphQL istemcisi: [RetryInterceptor] YOK ve sıkı zaman tavanı uygulanır.
     *
     * Neden: Ana [client] içindeki [RetryInterceptor] (3 deneme) ile [KitsugiApiBase]
     * yeniden deneme döngüsü (4 deneme) üst üste binip tek sorguda 12 gerçek HTTP
     * denemesine ve 90 sn'ye varan kuyruk kilitlenmesine yol açıyordu. AniList kota,
     * soğuma ve yeniden deneme yönetimi tek noktadan ([KitsugiApiBase]) yapılır.
     */
    val aniListClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is RetryInterceptor } }
            .build()
    }

    /**
     * MDBList istemcisi: 429'da **yeniden deneme yok**.
     *
     * Neden: MDBList'te her istek hesabın günlük kotasından (Free: 1.000/gün) düşer ve
     * ana [client] içindeki [RetryInterceptor] 429 aldığında aynı isteği 2 kez daha
     * gönderiyordu. Bu hem kotayı boşa tüketiyor hem de `X-RateLimit-Remaining`
     * değerinin uygulamada görünen değerden hızlı düşmesine yol açıyordu. Kota zaten
     * 429 ile kesinleştiği için yeniden denemek yalnızca zarar verir.
     */
    val mdbListClient: OkHttpClient by lazy {
        metadataClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is RetryInterceptor } }
            .build()
    }

    /**
     * Bangumi istemcisi: [RetryInterceptor] YOK ve 14 sn toplam çağrı tavanı uygulanır.
     */
    val bangumiClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(14, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is RetryInterceptor } }
            .build()
    }

    /**
     * Çapraz liste eşitleme yazımları için istemci (MAL, Kitsu, Shikimori, Simkl).
     * 25 sn toplam çağrı tavanı ile asılı kalan istekleri keser.
     */
    val syncClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }


    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 10,
                    keepAliveDuration  = 5,
                    timeUnit           = TimeUnit.MINUTES
                )
            )
            .dispatcher(
                Dispatcher().apply {
                    maxRequests        = 64
                    maxRequestsPerHost = 8
                }
            )
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            // PlatformFailureInterceptor wraps retries so a transient 429 that succeeds on retry
            // is not surfaced as a failed source in the current screen.
            .addInterceptor(PlatformFailureInterceptor())
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .addInterceptor(
                Interceptor { chain ->
                    val original = chain.request()
                    val reqBuilder = original.newBuilder()
                    if (original.header("User-Agent") == null) {
                        reqBuilder.header("User-Agent", NuvioOkHttpProvider.USER_AGENT)
                    }
                    if (original.header("Accept-Language") == null) {
                        reqBuilder.header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                    }
                    // Fandom görsel CDN'i (static.wikia.nocookie.net): tarayıcı dışı istemcilere
                    // Referer/Accept olmadan 403 dönebiliyor ya da içerik pazarlığında AVIF seçebiliyor
                    // (eski Android sürümleri AVIF çözemez). Tarayıcıya yakın başlıklar eklenir;
                    // Accept listesi AVIF içermez (WebP/PNG/JPEG ile sınırlı).
                    if (FandomImageHeaders.applies(original.url.host)) {
                        if (original.header("Referer") == null) {
                            reqBuilder.header("Referer", FandomImageHeaders.REFERER)
                        }
                        if (original.header("Accept") == null) {
                            reqBuilder.header("Accept", FandomImageHeaders.ACCEPT)
                        }
                    }
                    chain.proceed(reqBuilder.build())
                }
            )
            // This is inside RetryInterceptor, so every actual retry is paced as well.
            .addInterceptor(SimklRateLimitInterceptor())
            // En içte: her deneme (RetryInterceptor dahil) için gerçek HTTP kodunu görür.
            .addInterceptor(PlatformFailureInterceptor())
            // v2.4.342: her deneme kalıcı günlüğe yazılır (host, yol, kod, süre, kota başlıkları).
            // Kullanıcı "hangi kaynak ne zaman ne döndü" sorusunu sonradan cevaplayabilsin.
            .addInterceptor(KitsugiNetworkLogInterceptor())
            .build()
    }
}
