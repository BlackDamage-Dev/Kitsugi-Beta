package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import android.graphics.Color
import com.kitsugi.animelist.data.manga.CropBordersTransformation.Companion.cropUniformBorders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Kenar kırpma davranışı. Mihon'daki "Crop borders" ayarının güvenlik
 * sınırlarını doğrular: en fazla %12 kırpılır, tamamen tek renkli görseller
 * olduğu gibi bırakılır, aşırı kırpma asla yapılmaz.
 */
@RunWith(AndroidJUnit4::class)
class CropBordersTransformationTest {


    private fun bitmapWithWhiteBorders(
        width: Int = 200,
        height: Int = 300,
        left: Int = 10,
        top: Int = 10,
        right: Int = 10,
        bottom: Int = 10
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in top until height - bottom) {
            for (x in left until width - right) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
        return bitmap
    }

    @Test fun cropsUniformWhiteBorders() {
        val source = bitmapWithWhiteBorders()
        val cropped = source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.White)
        assertNotNull(cropped)
        // Örnekleme adımı (2px) kadar sapma kabul edilir.
        assertEquals(180, cropped!!.width, 6)
        assertEquals(280, cropped.height, 6)
    }

    @Test fun leavesFullBleedImagesUntouched() {
        val source = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
        // Her piksel farklı → hiçbir kenar tek renk değil.
        for (y in 0 until 160) {
            for (x in 0 until 120) {
                source.setPixel(x, y, Color.rgb(x * 2 % 256, y * 2 % 256, (x + y) % 256))
            }
        }
        assertNull(source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.White))
    }

    @Test fun neverCropsBeyondSafetyLimit() {
        // Sadece ortada 10x10 siyah kare var; %12 sınırı aşılmasın diye
        // kırpma ya hiç yapılmamalı ya da <= %12 olmalı.
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        for (y in 95 until 105) for (x in 95 until 105) source.setPixel(x, y, Color.BLACK)

        val result = source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.White)
        if (result != null) {
            val maxCrop = (200 * 0.12f).toInt() + 1
            assertEquals(true, 200 - result.width <= maxCrop * 2)
            assertEquals(true, 200 - result.height <= maxCrop * 2)
            assertEquals(true, result.width > 100)
            assertEquals(true, result.height > 100)
        }
    }

    @Test fun fullyUniformImageIsLeftAlone() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        assertNull(source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.White))
    }

    @Test fun cacheKeyChangesWithTolerance() {
        val a = CropBordersTransformation(tolerance = 10).cacheKey
        val b = CropBordersTransformation(tolerance = 20).cacheKey
        assertEquals(false, a == b)
    }

    @Test fun cacheKeyChangesWithProfile() {
        val white = CropBordersTransformation(profile = MangaCropProfile.White).cacheKey
        val black = CropBordersTransformation(profile = MangaCropProfile.Black).cacheKey
        assertEquals(false, white == black)
    }

    /** Beyaz profil, beyaz kenarı kırpar. */
    @Test fun whiteProfileCropsWhiteBorders() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        for (x in 20 until 80) for (y in 20 until 80) source.setPixel(x, y, Color.RED)
        val cropped = source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.White)
        assertNotNull(cropped)
        assertEquals(true, cropped!!.width < 100 && cropped.height < 100)
    }

    /** Siyah profil beyaz kenarı KIRPMAZ (profil filtresi çalışıyor mu?). */
    @Test fun blackProfileIgnoresWhiteBorders() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        for (x in 20 until 80) for (y in 20 until 80) source.setPixel(x, y, Color.RED)
        assertNull(source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.Black))
    }

    /** Otomatik profil iki durumu da kırpar. */
    @Test fun autoProfileCropsBoth() {
        val white = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        white.eraseColor(Color.WHITE)
        for (x in 15 until 65) for (y in 15 until 65) white.setPixel(x, y, Color.BLUE)
        val black = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        black.eraseColor(Color.BLACK)
        for (x in 15 until 65) for (y in 15 until 65) black.setPixel(x, y, Color.GREEN)

        assertNotNull(white.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.Auto))
        assertNotNull(black.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.Auto))
    }

    /** Kapalı profilde kırpma yapılmaz (erken çıkış). */
    @Test fun offProfileNeverCrops() {
        val source = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        for (x in 10 until 50) for (y in 10 until 50) source.setPixel(x, y, Color.RED)
        assertNull(source.cropUniformBorders(tolerance = 18, profile = MangaCropProfile.Off))
    }

    /** Geniş sayfa dönüşümü sayfa sayısını değiştirmez, geniş olmayana dokunmaz. */
    @Test fun rotateWidePageKeepsDimensionsForNarrowPages() {
        val narrow = Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888)
        narrow.eraseColor(Color.GRAY)
        val transformed = kotlinx.coroutines.runBlocking {
            RotateWidePageTransformation(threshold = 1.2f)
                .transform(narrow, androidx.compose.ui.geometry.Size(50f, 100f))
        }
        assertEquals(50, transformed.width)
        assertEquals(100, transformed.height)
    }

    @Test fun rotateWidePageSwapsDimensionsForWidePages() {
        val wide = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        wide.eraseColor(Color.GRAY)
        val transformed = kotlinx.coroutines.runBlocking {
            RotateWidePageTransformation(threshold = 1.2f)
                .transform(wide, androidx.compose.ui.geometry.Size(200f, 100f))
        }
        assertEquals(100, transformed.width)
        assertEquals(200, transformed.height)
    }
}
