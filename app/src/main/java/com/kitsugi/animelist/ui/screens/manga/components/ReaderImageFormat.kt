package com.kitsugi.animelist.ui.screens.manga.components

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/** Avoid replacing animated images with a single still frame. */
internal fun isStaticJpegOrPng(file: File): Boolean {
    return try {
    DataInputStream(FileInputStream(file)).use { input ->
        val signature = ByteArray(8)
        input.readFully(signature)
        if (signature[0] == 0xff.toByte() && signature[1] == 0xd8.toByte()) return jpegHasNoApp1(file)
        if (!signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return false
        var scanned = 8L
        while (scanned < 16L * 1024 * 1024) {
            val length = input.readInt()
            val type = ByteArray(4)
            input.readFully(type)
            if (length < 0 || length > 16 * 1024 * 1024) return false
            val tag = String(type, Charsets.US_ASCII)
            if (tag == "acTL" || tag == "eXIf") return false
            if (tag == "IDAT") return true
            var remaining = length + 4
            while (remaining > 0) {
                val skipped = input.skipBytes(remaining)
                if (skipped <= 0) return false
                remaining -= skipped
            }
            scanned += 12L + length
        }
        false
    }
    } catch (_: Exception) { false }
}

/** Coil honors EXIF orientation, while BitmapRegionDecoder does not. Do not
 * transform any JPEG with APP1 metadata rather than risk rotating its content. */
private fun jpegHasNoApp1(file: File): Boolean {
    return try {
        DataInputStream(FileInputStream(file)).use { input ->
            if (input.readUnsignedShort() != 0xffd8) return false
            var scanned = 2L
            while (scanned < 16L * 1024 * 1024) {
                if (input.readUnsignedByte() != 0xff) return false
                scanned += 2
                var marker = input.readUnsignedByte()
                while (marker == 0xff) {
                    if (++scanned >= 16L * 1024 * 1024) return false
                    marker = input.readUnsignedByte()
                }
                if (marker == 0xda) return true // Start of compressed image data.
                if (marker == 0xd9 || marker == 0x00 || marker == 0xe1) return false
                if (marker == 0x01 || marker in 0xd0..0xd8) continue
                val length = input.readUnsignedShort()
                if (length < 2) return false
                var remaining = length - 2
                while (remaining > 0) {
                    val skipped = input.skipBytes(remaining)
                    if (skipped <= 0) return false
                    remaining -= skipped
                }
                scanned += length
            }
            false
        }
    } catch (_: Exception) { false }
}
