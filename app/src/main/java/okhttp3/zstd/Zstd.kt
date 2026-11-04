package okhttp3.zstd

import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okio.BufferedSource
import okio.Source

/**
 * Binary-compatible `okhttp3.zstd.Zstd` adapter for `KeiSource`'s
 * `CompressionInterceptor(Brotli, Gzip, Zstd)` call.
 *
 * Because OkHttp 5.x's real `CompressionInterceptor` builds its `Accept-Encoding`
 * header directly from `algorithms.map { it.encoding }`, returning `"gzip"` here
 * prevents advertising `"zstd"` without bundling native `zstd-jni`, while keeping
 * `Lokhttp3/zstd/Zstd;->INSTANCE` intact for extension DEX bytecode.
 */
object Zstd : CompressionInterceptor.DecompressionAlgorithm {

    override val encoding: String get() = "gzip"

    override fun decompress(compressedSource: BufferedSource): Source =
        Gzip.decompress(compressedSource)
}
