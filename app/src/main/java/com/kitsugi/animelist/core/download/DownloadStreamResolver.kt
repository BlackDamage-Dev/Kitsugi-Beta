package com.kitsugi.animelist.core.download

import android.util.Log
import com.kitsugi.animelist.data.cloudstream.extractors.KitsugiCdnJwExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.withTimeoutOrNull

/**
 * T-IND-1 (2026-11-09): İndirme sırasında ÖLEN video adresini embed sayfasından
 * TAZELEME çözücüsü.
 *
 * NEDEN: HDFilmCehennemi / FilmMakinesi CDN host'ları (srvN.cdnimagesNNNN.cyou,
 * sNNN.rapidrame.com …) rotasyonlu. İndirme kuyruğa girerken çözülen URL, servis onu
 * işlerken (kuyruk beklemesi, yeniden deneme, "devam et") ölmüşse indirme
 * `UnknownHostException` ile yarıda kalıyordu. Çözüm: indirme kaydıyla birlikte saklanan
 * EMBED adresinden ([com.kitsugi.animelist.data.model.AnimeDownload.recoverUrl]) aynı
 * zinciri bir kez daha çalıştırıp canlı host'u almak.
 *
 * Zincir (oynatma ile aynı sıra):
 *  1) CS çalışma zamanı `loadExtractor` → eklentinin kendi çözücüsü öncelikli,
 *     yoksa uygulama yerleşikleri (KitsugiCdnJwExtractor …),
 *  2) önek eşleşmesi kaçtıysa yerleşik Close/Rapid çözücüsü DOĞRUDAN.
 */
object DownloadStreamResolver {

    private const val TAG = "DownloadStreamResolver"
    private const val RESOLVE_TIMEOUT_MS = 25_000L

    data class ResolvedStream(val url: String, val headers: Map<String, String>)

    /**
     * @param recoverUrl    indirme kuyruğa eklenirken saklanan embed/oynatıcı sayfası adresi.
     * @param recoverReferer embed sayfasının referer'ı (film/bölüm sayfası).
     * @param currentUrl     hâlâ denenen (ölmüş) adres — aynı adres tekrar üretilirse anlamsız.
     */
    suspend fun reResolve(
        recoverUrl: String?,
        recoverReferer: String?,
        currentUrl: String?
    ): ResolvedStream? {
        if (recoverUrl.isNullOrBlank() || !recoverUrl.startsWith("http")) return null
        val links = mutableListOf<ExtractorLink>()

        // 1) CS runtime: eklenti çözücüleri + yerleşikler (loadExtractor önek eşleşmesi).
        runCatching {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                loadExtractor(
                    url = recoverUrl,
                    referer = recoverReferer ?: "",
                    subtitleCallback = { },
                    callback = { link -> links.add(link) }
                )
            }
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "loadExtractor yeniden çözümü başarısız: ${e.message}")
        }

        // 2) Önek eşleşmesi kaçtıysa (host ailesi yerleşikte ama kayıt yok) doğrudan yerleşik.
        if (links.isEmpty()) {
            val builtin = KitsugiCdnJwExtractor.REGISTRATIONS
                .firstOrNull { recoverUrl.startsWith(it.second) }
            if (builtin != null) {
                runCatching {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                        KitsugiCdnJwExtractor(builtin.first, builtin.second).getUrl(
                            recoverUrl,
                            recoverReferer,
                            { },
                            { link -> links.add(link) }
                        )
                    }
                }.onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "yerleşik çözücü yeniden çözümü başarısız: ${e.message}")
                }
            }
        }

        val chosen = pickBest(links) ?: run {
            Log.w(TAG, "yeniden çözüm link üretmedi: embed=${recoverUrl.take(90)}")
            return null
        }
        val freshUrl = chosen.url
        if (freshUrl.isBlank() || freshUrl == currentUrl) {
            Log.w(TAG, "yeniden çözüm aynı/boş adresi üretti — kullanılmıyor: ${freshUrl.take(90)}")
            return null
        }

        val headers = LinkedHashMap<String, String>()
        headers.putAll(chosen.headers)
        if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            val ref = chosen.referer.takeIf { it.isNotBlank() } ?: recoverReferer
            if (!ref.isNullOrBlank()) headers["Referer"] = ref
        }
        if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
            headers["User-Agent"] = KitsugiCdnJwExtractor.USER_AGENT
        }
        Log.i(TAG, "video adresi tazelenildi: ${freshUrl.take(110)} (embed=${recoverUrl.take(70)})")
        return ResolvedStream(freshUrl, headers)
    }

    /** HLS (M3U8) tercih edilir; eşitse en yüksek kalite kazanır. */
    private fun pickBest(links: List<ExtractorLink>): ExtractorLink? =
        links.filter { it.url.startsWith("http") }
            .sortedWith(
                compareByDescending<ExtractorLink> { it.type == ExtractorLinkType.M3U8 }
                    .thenByDescending { it.quality }
            )
            .firstOrNull()
}
