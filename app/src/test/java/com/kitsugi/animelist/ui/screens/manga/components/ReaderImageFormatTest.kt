package com.kitsugi.animelist.ui.screens.manga.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReaderImageFormatTest {
    private fun checkFormat(bytes: ByteArray, expected: Boolean) {
        val file = File.createTempFile("reader-format", ".img")
        try { file.writeBytes(bytes); assertEquals(expected, isStaticJpegOrPng(file)) }
        finally { file.delete() }
    }

    @Test fun rejectsAnimatedAndOrientationTaggedPng() {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        fun chunk(name: String) = byteArrayOf(0, 0, 0, 0) + name.toByteArray(Charsets.US_ASCII) + ByteArray(4)
        checkFormat(signature + chunk("IHDR") + chunk("IDAT"), true)
        checkFormat(signature + chunk("IHDR") + chunk("acTL") + chunk("IDAT"), false)
        checkFormat(signature + chunk("IHDR") + chunk("eXIf") + chunk("IDAT"), false)
    }

    @Test fun rejectsJpegApp1AndUnsupportedOrTruncatedImages() {
        fun jpeg(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()
        checkFormat(jpeg(0xff, 0xd8, 0xff, 0xe0, 0, 2, 0xff, 0xda), true)
        checkFormat(jpeg(0xff, 0xd8, 0xff, 0xe1, 0, 2, 0xff, 0xda), false)
        checkFormat("GIF89a!!".toByteArray(), false)
        checkFormat(jpeg(0xff, 0xd8, 0xff), false)
    }
}
