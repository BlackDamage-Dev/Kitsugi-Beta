package com.lagradost.cloudstream3.network

import android.util.Log
import androidx.annotation.AnyThread
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.core.memory.boundedSet
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * DDoS-Guard korumasındaki siteler için `__ddg*` çerezini çalan bypass.
 *
 * ## v2.4.240 — "hiçbir eklenti sonuç döndürmüyor" regressyonunun kök düzeltmesi
 *
 * Bu interceptor, eklenti (scraper) OkHttp istemcisine **küresel** olarak takılıyor; yani
 * 1300+ eklentinin TÜM ağ istekleri buradan geçiyor. Eski gövde:
 *
 *   her 403 → TEK bir `ReentrantLock` altında `check.ddos-guard.net/check.js` + bypass
 *   isteği (bloklayan, timeout'suz, başarısızlığı hiçbir yere not edilmeyen tur).
 *
 * 403, bot koruması olan sitelerde (Cloudflare / vDDoS / WAF) **sıradan** bir yanıttır —
 * DDoS-Guard ile ilgisi olsun olmasın her 403 bu dansı tetikliyordu. Global kilit yüzünden
 * paralel çalışan tüm eklenti aramaları tek tek bu kilidin arkasına sıraya giriyor, her biri
 * `check.ddos-guard.net` erişemeyene kadar bloklanıyordu. Bu süre `CsStreamRunner.safeSearch`
 * zaman kutularını patlatınca **eklenti başına sonuç sıfıra iniyordu.** ID tabanlı kaynaklar
 * (Torrentio/Anizium) arama yapmadığı için bu tuzağa hiç düşmüyordu — kullanıcının
 * "sadece onlar çalışıyor" diye tarif ettiği tablo buydu.
 *
 * YENİ KURAL (dört katman):
 *  1. **Kanıt yoksa probe yok:** host'un DDoS-Guard kullandığına dair açık kanıt
 *     (`Server: ddos-guard`, `Set-Cookie: __ddg*` veya gövdede `ddos-guard`/`__ddg` imzası)
 *     aranır; kanıt yoksa yanıt OLDUĞU GİBİ döner.
 *  2. **Negatif önbellek:** kanıtsız hata yanıtı gören host oturum boyunca `skipHosts`'a
 *     yazılır → boşuna tek bir istek daha atılmaz.
 *  3. **Host başına seri, süre tavanlı probe:** sabit stripe kilitleri sınırlandırır; aynı host
 *     seri kalır. Probe `PROBE_BUDGET_MS` (8 sn) ile sınırlı ve `Dispatchers.IO`'ya devredilmiş.
 *     Başarısız probe aynı host için `REPROBE_INTERVAL_MS` boyunca tekrarlanmaz.
 *  4. **Çerezler paylaşılır:** eskiden instance alanıydı; artık CloudflareKiller gibi
 *     companion düzeyinde — tek çözüm hem scraper hem manga istemcisine yeter.
 *
 * `alwaysBypass = true` (yalnızca bilinçli çağrılar) eski davranışı korur: kanıt aranmadan
 * önemptif çerez alınır.
 */
@AnyThread
class DdosGuardKiller(private val alwaysBypass: Boolean) : Interceptor {

    companion object {
        private const val TAG = "DdosGuardKiller"

        /** DDoS-Guard bypass'ını tetikleyebilecek durum kodları. */
        private val BYPASS_CODES = setOf(403, 429, 503)

        /** check.js + bypass isteği için AZAMİ süre. Üzerinde = vazgeç, yanıtı geçir. */
        private const val PROBE_BUDGET_MS = 8_000L

        /** Başarısız probe'tan sonra aynı host için beklenecek en az süre. */
        private const val REPROBE_INTERVAL_MS = 10 * 60_000L

        /** Kanıt yoklamasında okunacak en fazla gövde baytı (body tüketilmez). */
        private const val PEEK_BYTES = 8L * 1024L

        /** Host başına çerezler — session cache; don't retain every provider host forever. */
        private val savedCookiesMap = BoundedCache<String, Map<String, String>>("cloudstream.ddos.cookies", 256)

        /** DDoS-Guard kanıtı görülmüş hostlar — bypass burada MEŞRU, denenir. */
        private val confirmedHosts = boundedSet<String>("cloudstream.ddos.confirmed", 512)

        /** Kanıt bulunamayan / probe'u başarısız olan hostlar — boşuna denenmez. */
        private val skipHosts = boundedSet<String>("cloudstream.ddos.skipped", 512)

        /** Son probe zamanı (başarı ya da başarısızlık) — probe spam'ini tavanlar. */
        private val lastProbeMs = BoundedCache<String, Long>("cloudstream.ddos.lastProbe", 512)

        /** Fixed striped locks bound memory while keeping concurrent probes for one host serialized. */
        private val hostLocks = Array(128) { ReentrantLock() }
        private fun lockFor(host: String): ReentrantLock =
            hostLocks[(host.hashCode() and Int.MAX_VALUE) % hostLocks.size]

        @Volatile
        private var ddosBypassPath: String? = null

        /**
         * Bu yanıt gerçekten bir DDoS-Guard meydan okuması mı?
         * Yalnızca başlıklar ve ilk 8 KB üzerinden karar verilir (bedeli ihmal edilebilir).
         */
        private fun hasDdosGuardEvidence(response: Response): Boolean {
            if (response.header("Server")?.contains("ddos-guard", ignoreCase = true) == true) return true

            if (response.headers.values("Set-Cookie").any { cookie ->
                    cookie.contains("__ddg", ignoreCase = true) ||
                        cookie.contains("ddg_cache", ignoreCase = true) ||
                        cookie.contains("ddos-guard", ignoreCase = true)
                }
            ) return true

            val contentType = response.body?.contentType()?.toString()?.lowercase()
            val isTextLike = contentType == null || contentType.contains("html") || contentType.contains("text")
            if (isTextLike) {
                val body = runCatching { response.peekBody(PEEK_BYTES).string() }.getOrNull() ?: return false
                if (body.contains("ddos-guard", ignoreCase = true)) return true
                if (body.contains("__ddg", ignoreCase = true)) return true
            }
            return false
        }

        /** Host için kayıtlı DDoS-Guard çerezi var mı? (tanılama/test) */
        fun hasCookiesFor(host: String): Boolean = savedCookiesMap.containsKey(host)

        /** Host bu oturumda DDoS-Guard kullanıyor olarak doğrulandı mı? (tanılama) */
        fun isConfirmedHost(host: String): Boolean = host in confirmedHosts

        /** Host bu oturumda bypass'a kapalı mı? (tanılama) */
        fun isSkippedHost(host: String): Boolean = host in skipHosts

        /** Oturum bazlı kararları sıfırlar (kullanıcı "Yenile" derse). */
        fun clearSessionState() {
            skipHosts.clear()
            confirmedHosts.clear()
            savedCookiesMap.clear()
            lastProbeMs.clear()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (com.kitsugi.animelist.data.manga.isMangaTrHost(request.url.host)) return chain.proceed(request)
        val host = request.url.host

        // DDoS-Guard'ın kendi altyapısı — sonsuz döngü koruması.
        if (host.contains("ddos-guard", ignoreCase = true)) {
            return chain.proceed(request)
        }

        // Daha önce alınmış çerez varsa doğrudan giydir.
        savedCookiesMap[host]?.let { cookies ->
            return proceedWithCookies(chain, request, cookies)
        }

        if (alwaysBypass) {
            val cookies = getOrFetchCookies(request)
            return if (cookies.isEmpty()) chain.proceed(request)
            else proceedWithCookies(chain, request, cookies)
        }

        val response = chain.proceed(request)
        if (response.code !in BYPASS_CODES) return response

        // --- Yalnızca hata kodunda ve yalnızca KANIT ÜZERİNE devreye gir ---
        if (hasDdosGuardEvidence(response)) {
            confirmedHosts.add(host)
            skipHosts.remove(host)
            response.close()

            val cookies = getOrFetchCookies(request)
            if (cookies.isEmpty()) return chain.proceed(request)
            return proceedWithCookies(chain, request, cookies)
        }

        // Hata var ama DDoS-Guard imzası yok (Cloudflare/WAF/erişim listesi/bot koruması…).
        // Bu host'u bypass'a kapat: eskiden burada global kilit + timeout'suz probe çalışıyordu.
        skipHosts.add(host)
        Log.d(TAG, "[$host] ${response.code} yanıtı DDoS-Guard imzası taşımıyor — bypass atlandı.")
        return response
    }

    private fun getOrFetchCookies(request: Request): Map<String, String> {
        val host = request.url.host
        savedCookiesMap[host]?.let { return it }

        // Son probe başarısız olduysa aynı host için REPROBE_INTERVAL_MS boyunca tekrar denemez.
        val now = System.currentTimeMillis()
        if (host in skipHosts && now - (lastProbeMs[host] ?: 0L) < REPROBE_INTERVAL_MS) return emptyMap()

        return lockFor(host).withLock {
            // Kilit beklenirken başka bir istek çözmüş olabilir.
            savedCookiesMap[host]?.let { return@withLock it }
            lastProbeMs[host] = System.currentTimeMillis()

            val cookies = try {
                // runBlocking yalnızca bu kısa probe için; iş Dispatchers.IO'ya devredilir ve
                // PROBE_BUDGET_MS ile tavanlanır → OkHttp iş parçacığı sınırsız bekletilmez.
                runBlocking {
                    withTimeout(PROBE_BUDGET_MS) {
                        withContext(Dispatchers.IO) { probeCookies(request.url) }
                    }
                }
            } catch (t: Throwable) {
                // TimeoutCancellationException de buraya düşer ve CASİT bir "çerez yok"tur:
                // istisna interceptor zincirine taşınırsa eklentinin tüm istekleri kırılır.
                Log.w(
                    TAG,
                    "[$host] DDoS-Guard çerezi alınamadı (${t.javaClass.simpleName}: ${t.message}) — " +
                        "yanıt olduğu gibi geçiriliyor."
                )
                emptyMap()
            }

            if (cookies.isNotEmpty()) {
                savedCookiesMap[host] = cookies
                confirmedHosts.add(host)
                skipHosts.remove(host)
            } else {
                // NEGATİF ÖNBELLEK: eski davranışta burası boş döner ve HER istekte aynı ölü
                // probe yeniden yapılıyordu — kitlesel arama zaman aşımlarının kaynağı buydu.
                skipHosts.add(host)
            }
            cookies
        }
    }

    private suspend fun probeCookies(url: HttpUrl): Map<String, String> {
        val path = ddosBypassPath ?: Regex("'(.*?)'").find(
            app.get("https://check.ddos-guard.net/check.js").text
        )?.groupValues?.get(1)
        ddosBypassPath = path

        val bypassUrl = url.scheme + "://" + url.host + (path ?: "")
        // Kendi istemcimiz DEĞİL: interceptor zincirine geri girmemek için taze Requests().
        return Requests().get(bypassUrl).cookies
    }

    private fun proceedWithCookies(
        chain: Interceptor.Chain,
        request: Request,
        cookies: Map<String, String>
    ): Response {
        val headers = getHeaders(request.headers.toMap(), cookies + request.cookies)
        val newRequest = request.newBuilder()
            .headers(headers)
            .build()
        return chain.proceed(newRequest)
    }
}
