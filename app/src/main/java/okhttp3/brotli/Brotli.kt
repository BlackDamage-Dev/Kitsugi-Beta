package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source
import okio.source
import org.brotli.dec.BrotliInputStream

/**
 * Real `okhttp-brotli` (OkHttp 5.x) implementation backed by `org.brotli:dec`.
 *
 * Keiyoushi `KeiSource` registers `CompressionInterceptor(Brotli, Gzip, Zstd)` and
 * Manga-TR / Cloudflare / DDoS-Guard endpoints frequently respond with `Content-Encoding: br`.
 */
object Brotli : CompressionInterceptor.DecompressionAlgorithm {

    override val encoding: String get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source =
        BrotliInputStream(compressedSource.inputStream()).source()
}
