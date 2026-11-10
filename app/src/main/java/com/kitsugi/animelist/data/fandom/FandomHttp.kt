package com.kitsugi.animelist.data.fandom

import android.util.Log
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fandom / Wikidata istekleri için ortak HTTP katmanı.
 *
 *  - Uygulamanın bağlantı havuzunu paylaşır; tek çağrı en fazla 12 sn sürer.
 *  - Aynı anda en fazla 4 istek (toplu tarama yok; Fandom ve Wikidata'ya karşı nazik).
 *  - Dürüst User-Agent (Wikimedia kullanıcı aracısı politikası).
 *  - Ağ/sunucu hataları [failureCount] ile sayılır: çözümleme sırasında hata olduysa
 *    "bulunamadı" sonucu kalıcı önbelleğe yazılmaz (geçici kesinti bir gün boyunca
 *    sekmeyi gizlemesin diye).
 */
internal object FandomHttp {

    private const val TAG = "FandomHttp"

    private val client: OkHttpClient by lazy {
        KitsugiHttpClient.client.newBuilder()
            .callTimeout(12, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val permits = Semaphore(4)
    private val failures = AtomicInteger(0)

    val userAgent: String by lazy {
        "Kitsugi/${BuildConfig.VERSION_NAME} (Android; +https://github.com/BlackDamage-Dev/Kitsugi-Beta)"
    }

    fun failureCount(): Int = failures.get()

    /** Fandom/Wikidata dışındaki bağımlılıklarda (ör. AniList doğrulama) yaşanan geçici hatayı sayar. */
    fun markFailure() {
        failures.incrementAndGet()
    }

    suspend fun get(url: String, accept: String = "application/json"): String? = withContext(Dispatchers.IO) {
        permits.withPermit {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", accept)
                    .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body?.string()
                        else -> {
                            // 403/408/429/5xx bu uçlarda hiçbir zaman "veri yok" anlamına gelmez
                            // (Cloudflare/WMF bot koruması, zaman aşımı, sunucu hatası): geçici
                            // sayılır ki çağıran "bulunamadı" sonucunu kalıcı önbelleğe yazmasın.
                            // 404 bilinçli olarak sayılmaz: olmayan wiki gerçekten 404 döner.
                            if (response.code == 403 || response.code == 408 || response.code == 429 || response.code >= 500) {
                                failures.incrementAndGet()
                            }
                            Log.w(TAG, "HTTP ${response.code} ← ${shorten(url)}")
                            null
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failures.incrementAndGet()
                Log.w(TAG, "İstek başarısız: ${shorten(url)} (${e.javaClass.simpleName})")
                null
            } catch (e: Exception) {
                failures.incrementAndGet()
                Log.w(TAG, "İstek hatası: ${shorten(url)} (${e.javaClass.simpleName})")
                null
            }
        }
    }

    private fun shorten(url: String): String = if (url.length <= 160) url else url.take(157) + "…"
}
