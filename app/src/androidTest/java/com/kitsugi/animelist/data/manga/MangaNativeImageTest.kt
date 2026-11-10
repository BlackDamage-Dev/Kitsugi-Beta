package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import coil3.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kitsugi.animelist.data.manga.image.MangaNativeDecoder
import com.kitsugi.animelist.data.manga.loader.MangaCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Runs on an ARM Android device/emulator, not on the host JVM. */
class MangaNativeImageTest {
    @Test fun avifAndHeifAreCachedAndActuallyDecoded() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val cache = MangaCache(context)
        val loader = ImageLoader.Builder(context).components { add(MangaNativeDecoder.Factory()) }.build()
        for (name in listOf("solid.avif", "solid.heic")) {
            val url = "https://cache-test.invalid/${System.nanoTime()}/$name"
            try {
                instrumentation.context.assets.open("manga/$name").use { cache.putImageToCache(url, it) }
                assertTrue("$name should pass cache validation", cache.isImageInCache(url))
                val result = loader.execute(ImageRequest.Builder(context).data(cache.getImageFile(url)).build())
                assertTrue("$name must decode: $result", result is SuccessResult)
                val image = (result as SuccessResult).image
                val bitmap: Bitmap = image.toBitmap()
                assertEquals(16, bitmap.width)
                assertEquals(16, bitmap.height)
                val pixel = bitmap.getPixel(8, 8)
                assertTrue("red test pixel expected", android.graphics.Color.red(pixel) > 150)
                assertTrue("not an empty or green test pixel", android.graphics.Color.green(pixel) < 110)
            } finally {
                cache.removeImage(url)
            }
        }
        loader.shutdown()
    }
}
