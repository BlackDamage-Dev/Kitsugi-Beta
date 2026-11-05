package com.kitsugi.animelist.data.manga.image

import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import java.io.File
import java.io.InputStream
import tachiyomi.decoder.Format
import tachiyomi.decoder.ImageDecoder

/** The same native decoder family used by Mihon for formats absent on older Android versions. */
internal object MangaNativeFormats {
    private val formats = setOf(Format.Avif, Format.Heif, Format.Jxl)

    private fun type(stream: InputStream): tachiyomi.decoder.ImageType? = try {
        val header = ByteArray(32)
        var offset = 0
        while (offset < header.size) {
            val count = stream.read(header, offset, header.size - offset)
            if (count <= 0) break
            offset += count
        }
        if (offset == header.size) ImageDecoder.findType(header) else null
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null // A missing ABI must not crash the rest of the image pipeline.
    }

    fun supports(stream: InputStream): Boolean = type(stream)?.format?.let { it in formats } == true

    /** Reads a native format's dimensions from a stream and always recycles the decoder. */
    fun dimensions(stream: InputStream): Pair<Int, Int>? = try {
        val decoder = ImageDecoder.newInstance(stream)
        if (decoder == null) null else try {
            if (decoder.width > 0 && decoder.height > 0) decoder.width to decoder.height else null
        } finally {
            decoder.recycle()
        }
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }

    fun dimensions(file: File): Pair<Int, Int>? {
        return try {
            if (!file.inputStream().use(::supports)) return null
            file.inputStream().use(::dimensions)
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}

/**
 * Telephoto 0.14 assumes every BitmapImage except GIF/SVG supports Android
 * BitmapRegionDecoder. That assumption is false for native AVIF/HEIF/JXL on
 * older Android. A generic Image makes it draw the decoded preview instead.
 */
private class NativePreviewImage(image: Image) : Image by image

/** Native decode is restricted to AVIF/HEIF/JXL; standard and animated formats stay with Coil. */
internal class MangaNativeDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val decoder = source.source().use { ImageDecoder.newInstance(it.inputStream()) }
            ?: throw java.io.IOException("Görsel decoder başlatılamadı")
        try {
            check(decoder.width > 0 && decoder.height > 0) { "Geçersiz görsel boyutu" }
            // Bound the preview even if no target size is supplied. Telephoto may display it
            // directly when Android's region decoder cannot tile the native image format.
            val width = options.size.width.pxOrElse { decoder.width }.coerceIn(1, 4096)
            val height = options.size.height.pxOrElse { decoder.height }.coerceIn(1, 4096)
            val sample = DecodeUtils.calculateInSampleSize(
                decoder.width, decoder.height, width, height, options.scale,
            )
            val bitmap = decoder.decode(sampleSize = sample)
                ?: throw java.io.IOException("Görsel çözümlenemedi")
            return DecodeResult(NativePreviewImage(bitmap.asImage()), isSampled = sample > 1)
        } finally {
            decoder.recycle()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val supported = result.source.source().peek().inputStream().use(MangaNativeFormats::supports)
            return if (supported) MangaNativeDecoder(result.source, options) else null
        }
    }
}
