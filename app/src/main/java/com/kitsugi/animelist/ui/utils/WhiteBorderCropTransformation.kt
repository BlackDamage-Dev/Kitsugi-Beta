package com.kitsugi.animelist.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import coil3.transform.Transformation

/** Removes only nearly-white, contiguous outer borders; artwork inside the page is never centre-cropped. */
class WhiteBorderCropTransformation : Transformation() {
    override val cacheKey: String = "manga_white_border_crop_v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width < 16 || input.height < 16) return input
        val maxHorizontalCrop = (input.width * 0.2f).toInt()
        val maxVerticalCrop = (input.height * 0.2f).toInt()
        var left = 0
        var right = input.width - 1
        var top = 0
        var bottom = input.height - 1

        while (left < maxHorizontalCrop && isWhiteVerticalLine(input, left, top, bottom)) left++
        while (right > input.width - 1 - maxHorizontalCrop && isWhiteVerticalLine(input, right, top, bottom)) right--
        while (top < maxVerticalCrop && isWhiteHorizontalLine(input, top, left, right)) top++
        while (bottom > input.height - 1 - maxVerticalCrop && isWhiteHorizontalLine(input, bottom, left, right)) bottom--

        val width = right - left + 1
        val height = bottom - top + 1
        if (left == 0 && top == 0 && width == input.width && height == input.height) return input
        if (width < input.width / 2 || height < input.height / 2) return input
        return Bitmap.createBitmap(input, left, top, width, height)
    }

    private fun isWhiteVerticalLine(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        val pixels = IntArray(bottom - top + 1)
        bitmap.getPixels(pixels, 0, 1, x, top, 1, pixels.size)
        return pixels.areMostlyWhite()
    }

    private fun isWhiteHorizontalLine(bitmap: Bitmap, y: Int, left: Int, right: Int): Boolean {
        val pixels = IntArray(right - left + 1)
        bitmap.getPixels(pixels, 0, pixels.size, left, y, pixels.size, 1)
        return pixels.areMostlyWhite()
    }

    private fun IntArray.areMostlyWhite(): Boolean {
        var colored = 0
        // A small tolerance preserves antialiasing and scanner noise at the content edge.
        val tolerance = (size * 0.015f).toInt() + 1
        for (pixel in this) {
            if (Color.red(pixel) < 242 || Color.green(pixel) < 242 || Color.blue(pixel) < 242) {
                colored++
                if (colored > tolerance) return false
            }
        }
        return true
    }
}
