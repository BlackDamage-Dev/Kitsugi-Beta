package com.kitsugi.animelist.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.request.allowHardware
import coil3.request.allowRgb565
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Detay sayfası **vitrin (hero)** görselleri için tek doğruluk kaynağı.
 *
 * NEDEN VAR?
 * ──────────
 * Uygulama genelindeki [coil3.ImageLoader] bellek tasarrufu için
 * `allowRgb565(true)` + `precision(INEXACT)` ile kuruludur (`KitsugiApplication`).
 * Liste/grid kartlarında bu doğru tercihtir (posterlerde ~%50 bellek tasarrufu), ANCAK
 * vitrin gibi ekranda **tek ve büyük** duran görsellerde görsel kalitesini düşürür:
 *
 *  1. `Bitmap.Config.RGB_565` = kanal başına 5/6/5 bit (ARGB_8888'de 8/8/8/8) ve alfa
 *     yok. Gökyüzü, sakura, duman, ışık geçişleri gibi **yumuşak gradyanlarda bantlaşma
 *     (banding)** ve renk kaybı oluşur. Anime key visual'ları neredeyse tamamen gradyan
 *     olduğu için hasar gözle görülür seviyede.
 *  2. `AsyncImage`, istekte ölçü tanımlı değilse `SizeResolver.ORIGINAL` seçer; yani
 *     TMDB `/original` 4K kapaklarının tamamı decode edilir (~33 MB ARGB_8888). Bu hem
 *     bellek hem de ilk kare gecikmesi açısından israfdır.
 *
 * Bu politika vitrin isteklerinde:
 *  - `allowRgb565(false)` → tam 8-bit renk, gradyanlar pürüzsüz,
 *  - ekrandan türetilmiş **sınırlı** ölçü → bellek tavanı korunur,
 *  - sabit `memoryCacheKey` → [preload] ile ekrandaki istek AYNI anahtarı paylaşır;
 *    boyut farkından ötürü önbellek ıskalaması yaşanmaz.
 *
 * Yalnızca vitrin kullanır; liste/grid kartları eskisi gibi RGB_565 ile devam eder.
 */
internal object HeroArtworkImagePolicy {

    /** Vitrin görseli için istenecek en yüksek piksel genişliği (4K decode'ı engeller). */
    const val MAX_TARGET_WIDTH_PX = 2560

    /** Vitrin görseli için istenecek en yüksek piksel yüksekliği. */
    const val MAX_TARGET_HEIGHT_PX = 1440

    /**
     * Vitrin bandı ekran genişliği kadar olduğu için hedef genişliği ekran genişliğinden
     * türetilir (1.5x pay: kırpma/büyütme ve yüksek yoğunluklu ekranlar için pay).
     */
    fun targetWidthPx(context: Context): Int {
        val metrics = context.resources.displayMetrics
        // Yatay modda vitrin SOL PANEL içinde durur; ekranın uzun kenarını baz almak
        // panel için gereğinden büyük decode'a yol açar. Bu yüzden KISA kenar kullanılır.
        return clampHeroTargetWidth(minOf(metrics.widthPixels, metrics.heightPixels))
    }

    /** Hedef yükseklik: 16:9 sinematik bant oranı. */
    fun targetHeightPx(context: Context): Int = clampHeroTargetHeight(targetWidthPx(context))

    /**
     * Vitrin görseli için **tam kalite** isteği üretir.
     *
     * @param url Tam çözünürlüklü görsel adresi (TMDB `/original`, Fanart.tv tam boy,
     *   galeri en yüksek çözünürlüklü görseli).
     */
    fun request(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            // Dönüşümsüz vitrin GPU'da hardware bitmap olarak çizilebilir. Bitmap blur
            // uygulanan istekler KitsugiNsfwImage içinde ayrıca software moda alınır.
            .allowHardware(true)
            .allowRgb565(false)
            .precision(Precision.EXACT)
            .size(targetWidthPx(context), targetHeightPx(context))
            .memoryCacheKey(url)
            .build()

    /**
     * Vitrin görselini arka planda indirip decode eder.
     *
     * Amaç: yüksek kaliteli aday seçildiğinde ekrandaki istekle aynı bellek önbelleğini
     * hazırlamak; birincil detayın ilk karesini bu işlem için bekletmemek.
     *
     * Başarısızlık veya uzun sürme sessizce yok sayılır — vitrin kendi isteğiyle de
     * yüklenmeye devam eder.
     *
     * @return true → bitmap bellek önbelleğe girdi (ilk kare anında gelecek).
     */
    suspend fun preload(context: Context, urls: List<String>, timeoutMillis: Long = 3_000L): Boolean {
        val url = urls.firstOrNull { it.isNotBlank() } ?: return false
        val loader: ImageLoader = SingletonImageLoader.get(context)
        return try {
            withTimeoutOrNull(timeoutMillis) {
                withContext(Dispatchers.IO) { loader.execute(request(context, url)) }
                true
            } ?: false
        } catch (ce: CancellationException) {
            // Kapsam iptali asla yutulmaz.
            throw ce
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Vitrin görseli için hedef genişliği ekranın EN KISA kenarından sınırlar.
 *
 * Saf fonksiyon (Context gerektirmez) — birim testleriyle doğrulanır.
 * Taban [MIN_TARGET_WIDTH_PX] (çok küçük ekranlarda bile yeterli çözünürlük),
 * tavan [HeroArtworkImagePolicy.MAX_TARGET_WIDTH_PX] (4K decode'ı bellek taşmasını engeller).
 */
internal fun clampHeroTargetWidth(shortestScreenSidePx: Int): Int =
    (shortestScreenSidePx.coerceAtLeast(1) * 1.5f).toInt()
        .coerceIn(MIN_TARGET_WIDTH_PX, HeroArtworkImagePolicy.MAX_TARGET_WIDTH_PX)

/**
 * Vitrin görseli için hedef yüksekliği 16:9 bant oranından üretir ve sınırlar.
 */
internal fun clampHeroTargetHeight(targetWidthPx: Int): Int =
    (targetWidthPx.coerceAtLeast(1) * 9f / 16f).toInt()
        .coerceIn(MIN_TARGET_HEIGHT_PX, HeroArtworkImagePolicy.MAX_TARGET_HEIGHT_PX)

/** Vitrin görselinde kabul edilebilecek en düşük hedef genişlik (px). */
internal const val MIN_TARGET_WIDTH_PX = 720

/** Vitrin görselinde kabul edilebilecek en düşük hedef yükseklik (px). */
internal const val MIN_TARGET_HEIGHT_PX = 405
