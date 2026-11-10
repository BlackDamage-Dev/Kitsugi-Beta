package com.kitsugi.animelist.ui.screens.manga.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ReaderColorMatrixTest {
    private fun color(matrix: FloatArray, r: Float, g: Float, b: Float): List<Float> = (0..2).map { row ->
        matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b + matrix[row * 5 + 4]
    }
    private fun near(actual: Float, expected: Float) { assertTrue(abs(actual - expected) < 0.01f) }

    @Test fun defaultsPreservePresetAndAlpha() {
        assertNull(readerColorMatrix("Normal", 100, 100))
        val inv = readerColorMatrix("Invert", 100, 100)!!
        assertEquals(listOf(245f, 235f, 225f), color(inv, 10f, 20f, 30f))
        near(inv[18], 1f); near(inv[19], 0f)
        val gray = color(readerColorMatrix("Grayscale", 100, 100)!!, 10f, 20f, 30f)
        near(gray[0], gray[1]); near(gray[1], gray[2])
    }
    @Test fun saturationAndContrastComposeInOrder() {
        val gray = color(readerColorMatrix("Normal", 0, 100)!!, 200f, 100f, 50f)
        near(gray[0], gray[1]); near(gray[1], gray[2]); near(gray[0], 117.65f)
        val contrast = color(readerColorMatrix("Normal", 100, 200)!!, 128f, 0f, 255f)
        near(contrast[0], 128f); near(contrast[1], -128f); near(contrast[2], 382f)
        val clamped = readerColorMatrix("Normal", -5, 999)!!
        assertTrue(clamped.all { it.isFinite() })
    }
    @Test fun hueRotationPreservesNeutralGrayAndChangesColor() {
        for (angle in listOf(-180, -90, 90, 180)) {
            val matrix = readerColorMatrix("Normal", 100, 100, angle)!!
            val gray = color(matrix, 128f, 128f, 128f)
            gray.forEach { near(it, 128f) }
            val red = color(matrix, 255f, 0f, 0f)
            assertTrue(abs(red[0] - 255f) > 0.01f || abs(red[1]) > 0.01f || abs(red[2]) > 0.01f)
            near(matrix[18], 1f)
        }
        assertNull(readerColorMatrix("Normal", 100, 100, 0))
    }
}
