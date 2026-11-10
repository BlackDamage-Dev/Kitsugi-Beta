package com.kitsugi.animelist.core.p2p

enum class P2pTorrentProfile {
    SOFT,
    BALANCED,
    FAST,
}

enum class P2pCacheSize(val bytes: Long) {
    NONE(0L),
    GB_2(2L * 1024L * 1024L * 1024L),
    GB_5(5L * 1024L * 1024L * 1024L),
    GB_10(10L * 1024L * 1024L * 1024L),
}

data class P2pSettingsUiState(
    val p2pEnabled: Boolean = false,
    val enableUpload: Boolean = true,
    val uploadLimitKbps: Long = 0L, // 0 = Unlimited
    val hideTorrentStats: Boolean = false,
    val torrentProfile: P2pTorrentProfile = P2pTorrentProfile.BALANCED,
    val cacheSize: P2pCacheSize = P2pCacheSize.GB_2,
    val consentGranted: Boolean = false,
    /** Oynatıcı kapanınca / yeni akış başlayınca torrent önbelleğini otomatik boşalt. */
    val clearCacheOnClose: Boolean = true,
)

data class P2pCacheUiState(
    val usedBytes: Long = 0L,
    val protectedBytes: Long = 0L,
    val isClearing: Boolean = false,
    val hasMeasurement: Boolean = false,
)

data class P2pCacheClearResult(
    val reclaimedBytes: Long,
    val remainingBytes: Long,
    val protectedBytes: Long,
)

data class P2pStreamRequest(
    val infoHash: String,
    val fileIdx: Int? = null,
    val filename: String? = null,
    val trackers: List<String> = emptyList(),
)

sealed class P2pStreamingState {
    data object Idle : P2pStreamingState()

    data class Connecting(
        val phase: String = "starting_engine",
        val downloadSpeed: Long = 0L,
        val uploadSpeed: Long = 0L,
        val peers: Int = 0,
        val seeds: Int = 0,
    ) : P2pStreamingState()

    data class Streaming(
        val localUrl: String,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val peers: Int,
        val seeds: Int,
        val bufferProgress: Float,
        val totalProgress: Float,
        val downloadedBytes: Long = 0L,
        val verifiedBytes: Long = 0L,
        val deliveredBytes: Long = 0L,
    ) : P2pStreamingState()

    data class Error(val message: String) : P2pStreamingState()
}

val P2pStreamingState.isStreaming: Boolean
    get() = this is P2pStreamingState.Streaming || this is P2pStreamingState.Connecting

val P2pStreamingState.downloadSpeed: Long
    get() = when (this) {
        is P2pStreamingState.Streaming -> downloadSpeed
        is P2pStreamingState.Connecting -> downloadSpeed
        else -> 0L
    }

val P2pStreamingState.uploadSpeed: Long
    get() = when (this) {
        is P2pStreamingState.Streaming -> uploadSpeed
        is P2pStreamingState.Connecting -> uploadSpeed
        else -> 0L
    }

val P2pStreamingState.peers: Int
    get() = when (this) {
        is P2pStreamingState.Streaming -> peers
        is P2pStreamingState.Connecting -> peers
        else -> 0
    }

val P2pStreamingState.seeds: Int
    get() = when (this) {
        is P2pStreamingState.Streaming -> seeds
        is P2pStreamingState.Connecting -> seeds
        else -> 0
    }

val P2pStreamingState.bufferProgress: Float
    get() = when (this) {
        is P2pStreamingState.Streaming -> normalizeP2pBufferProgress(bufferProgress) ?: 0f
        else -> 0f
    }

/**
 * Nuvio versions have reported this metric as either a fraction (0..1) or a percent
 * (0..100). Normalize both formats and reject NaN/invalid samples rather than letting
 * them make the progress indicator jump to an arbitrary value.
 */
internal fun normalizeP2pBufferProgress(value: Float?): Float? {
    if (value == null || !value.isFinite() || value < 0f) return null
    val fraction = when {
        value <= 1f -> value
        value <= 100f -> value / 100f
        else -> return null
    }
    return fraction.coerceIn(0f, 1f)
}

fun bufferPercent(value: Float): Int =
    ((normalizeP2pBufferProgress(value) ?: 0f) * 100f).toInt().coerceIn(0, 100)

class P2pStreamingException(message: String) : Exception(message)

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val HEX_DIGITS_LOWER = "0123456789abcdef"

/**
 * Normalizes a raw info hash (40-char hex, 64-char hex v2, or 32-char base32)
 * into a canonical lowercase hex string accepted by BitTorrent engines.
 *
 * @return normalized lowercase hex hash, or null if invalid.
 */
fun normalizeInfoHashToHex(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val lower = value.lowercase()

    // 40 or 64 char hex
    if ((lower.length == 40 || lower.length == 64) &&
        lower.all { it in '0'..'9' || it in 'a'..'f' }
    ) {
        return lower
    }

    // 32 char base32 (RFC 4648) -> 20 bytes -> 40 char hex
    if (value.length == 32 && value.uppercase().all { it in BASE32_ALPHABET }) {
        return base32ToHex(value.uppercase())
    }

    return null
}

/** RFC 4648 base32 -> lowercase hex */
private fun base32ToHex(base32: String): String? = runCatching {
    var buffer = 0
    var bitsLeft = 0
    val out = StringBuilder(40)
    for (ch in base32) {
        val value = BASE32_ALPHABET.indexOf(ch)
        if (value < 0) return@runCatching null
        buffer = (buffer shl 5) or value
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bitsLeft -= 8
            val byteVal = (buffer shr bitsLeft) and 0xff
            out.append(HEX_DIGITS_LOWER[(byteVal shr 4) and 0x0f])
            out.append(HEX_DIGITS_LOWER[byteVal and 0x0f])
        }
    }
    out.toString().takeIf { it.length == 40 }
}.getOrNull()

fun canonicalP2pInfoHash(infoHash: String): String {
    return normalizeInfoHashToHex(infoHash)
        ?: throw P2pStreamingException("Torrent info hash 40 veya 64 karakterli onaltılık (hex) dize veya 32 karakterli base32 olmalıdır: $infoHash")
}

fun buildP2pMagnetUri(infoHash: String, trackers: List<String>): String {
    val canonicalHash = canonicalP2pInfoHash(infoHash)
    val topic = if (canonicalHash.length == 40) {
        "urn:btih:$canonicalHash"
    } else {
        "urn:btmh:1220$canonicalHash"
    }
    val trackerParameters = trackers.filter(String::isNotBlank).distinct().joinToString("") { tracker ->
        "&tr=${tracker.encodeP2pQueryValue()}"
    }
    return "magnet:?xt=$topic$trackerParameters"
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun String.encodeP2pQueryValue(): String = buildString {
    for (byte in this@encodeP2pQueryValue.encodeToByteArray()) {
        val value = byte.toInt() and 0xff
        if ((value in 'a'.code..'z'.code) ||
            (value in 'A'.code..'Z'.code) ||
            (value in '0'.code..'9'.code) ||
            value == '-'.code || value == '.'.code || value == '_'.code || value == '~'.code
        ) {
            append(value.toChar())
        } else {
            append('%')
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

fun formatP2pSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_048_576 -> "${(bytesPerSec / 1_048_576.0).formatOneDecimal()} MB/s"
        bytesPerSec >= 1_024 -> "${(bytesPerSec / 1_024.0).formatNoDecimal()} KB/s"
        else -> "$bytesPerSec B/s"
    }
}

fun formatP2pMegabytes(bytes: Long): String =
    "${(bytes / (1024.0 * 1024.0)).formatOneDecimal()} MB"

fun formatP2pBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> "${(bytes / (1024.0 * 1024.0 * 1024.0)).formatOneDecimal()} GB"
        bytes >= 1024L * 1024L -> "${(bytes / (1024.0 * 1024.0)).formatOneDecimal()} MB"
        bytes >= 1024L -> "${(bytes / 1024.0).formatNoDecimal()} KB"
        else -> "$bytes B"
    }
}

private fun Double.formatOneDecimal(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    val whole = rounded.toLong()
    val fraction = ((rounded - whole) * 10.0).toInt()
    return "$whole.$fraction"
}

private fun Double.formatNoDecimal(): String =
    kotlin.math.round(this).toInt().toString()
