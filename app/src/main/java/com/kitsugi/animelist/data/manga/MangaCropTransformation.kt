package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import coil3.size.Size
import coil3.transform.Transformation
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mihon'daki "Crop borders" (kenar kırpma) özelliğinin Kitsugi uyarlaması.
 *
 * Tarama sayfalarında sık görülen tek renkli boş kenarları (genellikle beyaz)
 * kırpar. Coil pipeline'ında çalışır; sonuç normal bellek önbelleğine yazılır,
 * dolayısıyla sayfa bir kez kırpılır.
 *
 * Güvenlik kuralları:
 *  - Aşırı kırpmayı önlemek için her kenarda en fazla %12 kırpılır.
 *  - Kenar algısı birkaç satır/kolon örneklemesiyle yapılır (tam tarama yok).
 *  - Çok uzun webtoon şeritlerinde maliyet sabit kalır (sadece kenarlar taranır).
 */
class CropBordersTransformation(
    private val tolerance: Int = 18,
    /** Hangi kenar rengi kırpılacak (beyaz tarama / siyah arka plan / otomatik). */
    private val profile: MangaCropProfile = MangaCropProfile.Auto
) : Transformation() {

    override val cacheKey: String = "kitsugi:cropBorders:v2:${profile.key}:t$tolerance"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input.cropUniformBorders(tolerance, profile) ?: input
    }

    companion object {
        private const val MAX_CROP_RATIO = 0.12f
        private const val SAMPLE_STEP = 2

        /** Kotatsu'daki standart/webtoon ayrı kırpma ayarının karşılığı. */
        fun forOptions(options: MangaReaderOptions, isWebtoon: Boolean): CropBordersTransformation? {
            val profile = options.cropProfile
            if (profile == MangaCropProfile.Off) return null
            // Beyaz kırpma uzun şeritte genelde zararlıdır (paneller arası boşluk
            // sayfa kenarı sanılır). Bu yüzden webtoon'da yalnızca **otomatik/siyah**
            // profil uygulanır; beyaz profil açıkça seçilse bile atlanır.
            if (isWebtoon && profile == MangaCropProfile.White) return null
            return CropBordersTransformation(profile = profile)
        }

        /** Kenarlar tek renkse kırpılmış bitmap, değilse `null`. */
        internal fun Bitmap.cropUniformBorders(
            tolerance: Int,
            profile: MangaCropProfile = MangaCropProfile.Auto
        ): Bitmap? {
            val maxX = (width * MAX_CROP_RATIO).roundToInt().coerceAtLeast(0)
            val maxY = (height * MAX_CROP_RATIO).roundToInt().coerceAtLeast(0)
            if (maxX <= 0 && maxY <= 0) return null

            val left = scanEdge(
                limit = maxX,
                tolerance = tolerance,
                read = { x, y -> getPixel(x, y) },
                fixedMax = height,
                step = SAMPLE_STEP,
                profile = profile
            )
            val right = scanEdge(
                limit = maxX,
                tolerance = tolerance,
                read = { x, y -> getPixel(width - 1 - x, y) },
                fixedMax = height,
                step = SAMPLE_STEP,
                profile = profile
            )
            val top = scanEdge(
                limit = maxY,
                tolerance = tolerance,
                read = { y, x -> getPixel(x, y) },
                fixedMax = width,
                step = SAMPLE_STEP,
                profile = profile
            )
            val bottom = scanEdge(
                limit = maxY,
                tolerance = tolerance,
                read = { y, x -> getPixel(x, height - 1 - y) },
                fixedMax = width,
                step = SAMPLE_STEP,
                profile = profile
            )

            val newWidth = width - left - right
            val newHeight = height - top - bottom
            if (newWidth <= width / 2 || newHeight <= height / 2) return null
            if (newWidth == width && newHeight == height) return null

            return Bitmap.createBitmap(this, left, top, newWidth, newHeight)
        }

        /**
         * `read(depth, along)` verilen derinlikte bir pikseli döndürür.
         * Tüm örnekler ilk pikselin rengine yakınsa o derinlik kırpılabilir.
         */
        private inline fun scanEdge(
            limit: Int,
            tolerance: Int,
            read: (Int, Int) -> Int,
            fixedMax: Int,
            step: Int,
            profile: MangaCropProfile = MangaCropProfile.Auto
        ): Int {
            if (limit <= 0) return 0
            var depth = 0
            while (depth < limit) {
                var reference: Int? = null
                var uniform = true
                var along = 0
                while (along < fixedMax) {
                    if (reference == null) {
                        reference = read(depth, along)
                    } else if (!isSimilar(reference, read(depth, along), tolerance)) {
                        uniform = false
                        break
                    }
                    along += step
                }
                val referenceColor = reference
                if (!uniform || referenceColor == null ||
                    !profileAccepts(referenceColor, profile)
                ) break
                depth++
            }
            // Tüm sayfa tek renkse hiç kırpma
            if (depth >= limit) return 0
            return depth
        }

        /** Profil yalnızca ilgili kenar rengini kırpar (beyaz / siyah / ikisi). */
        private fun profileAccepts(color: Int, profile: MangaCropProfile): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val brightness = (r + g + b) / 3
            return when (profile) {
                MangaCropProfile.Off -> false
                MangaCropProfile.White -> brightness >= 235
                MangaCropProfile.Black -> brightness <= 22
                MangaCropProfile.Auto -> brightness >= 235 || brightness <= 22
            }
        }

        private fun isSimilar(a: Int, b: Int, tolerance: Int): Boolean {
            val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
            val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
            val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
            return max(dr, max(dg, db)) <= tolerance
        }
    }
}

/**
 * Geniş sayfayı 90° çevirir (Mihon "Rotate wide pages" paritesi).
 *
 * Sayfa **sayısını değiştirmez** — yalnızca çizim yönünü değiştirir. Bu yüzden
 * ilerleme kaydı, geri yükleme ve çift sayfa eşlemesi hiç etkilenmez;
 * yan etkisiz (side-effect free) bir görsel dönüşümdür.
 */
class RotateWidePageTransformation(
    private val threshold: Float = 1.2f,
    /** true = saat yönünde (90°), false = saat yönünün tersi (-90°). */
    private val clockwise: Boolean = true
) : Transformation() {

    override val cacheKey: String =
        "kitsugi:rotateWide:v2:t$threshold:c$clockwise"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= 0 || input.height <= 0) return input
        val ratio = input.width.toFloat() / input.height.toFloat()
        // Geniş olmayan sayfa dokunulmadan döner (kimlik dönüşümü).
        if (ratio < threshold) return input
        return Bitmap.createBitmap(
            input, 0, 0, input.width, input.height,
            android.graphics.Matrix().apply { postRotate(if (clockwise) 90f else -90f) },
            true
        )
    }
}

/**
 * Sayfa geometrisi yardımcıları (otomatik webtoon algılama).
 * Yalnızca görsel başlıklarını okur (`inJustDecodeBounds`), piksel çözmez.
 */
object MangaPageMetrics {

    /**
     * Oran önbelleği (genişlik/yükseklik). Sayfa listesi pager yuvalarına
     * bölünürken her kompozisyonda disk okuması yapılmasın diye tutulur.
     */
    private val ratioCache = ConcurrentHashMap<String, Float>()

    /** Önbellekli genişlik/yükseklik oranı; bilinmiyorsa 0. */
    fun cachedAspectRatio(key: String, resolver: () -> File? = { null }): Float {
        ratioCache[key]?.let { return it }
        val file = runCatching { resolver() }.getOrNull()
        val ratio = if (file != null && file.exists()) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outHeight <= 0) 0f
                else bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
            }.getOrDefault(0f)
        } else 0f
        if (ratio > 0f) ratioCache[key] = ratio
        return ratio
    }

    /** Yükseklik / genişlik oranı; okunamazsa 0. */
    fun aspectRatio(file: File?): Float {
        if (file == null || !file.exists()) return 0f
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return 0f
            bounds.outHeight.toFloat() / bounds.outWidth.toFloat()
        }.getOrDefault(0f)
    }
}

/**
 * TachiyomiSY'nin "akıllı arka plan" özelliği için sayfa tonu örnekleyici.
 *
 * Görselin tamamını çözmez: `inSampleSize` ile küçültülmüş bir önizleme açar ve
 * ortalama parlaklık hesaplar. Sonuç `manga_decodedLuminance` benzeri küçük bir
 * LRU haritada tutulur (en fazla [MAX_ENTRIES] kayıt).
 */
object MangaPageLuminance {

    private const val MAX_ENTRIES = 24
    private val cache = ConcurrentHashMap<String, Float>()

    /** Bilinmeyen sayfa için kullanılan güvenli varsayılan (siyah zemin). */
    const val DEFAULT_UNKNOWN = 0.0f

    fun cached(key: String): Float? = cache[key]

    /** Arka planda çağrılmalıdır (Disk IO). */
    fun sample(file: File?): Float {
        if (file == null || !file.exists()) return DEFAULT_UNKNOWN
        val key = file.absolutePath
        cache[key]?.let { return it }
        val value = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(key, bounds)
            var sample = 1
            val minSide = min(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            while (minSide / sample > 64) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(key, opts) ?: return DEFAULT_UNKNOWN
            val average = bitmap.averageLuminance()
            bitmap.recycle()
            average
        }.getOrDefault(DEFAULT_UNKNOWN)

        if (cache.size >= MAX_ENTRIES) cache.clear()
        cache[key] = value
        return value
    }

    private fun Bitmap.averageLuminance(): Float {
        var sum = 0.0
        var count = 0
        val stepX = max(1, width / 24)
        val stepY = max(1, height / 24)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                val color = Color(pixel)
                sum += color.luminance()
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }
}
