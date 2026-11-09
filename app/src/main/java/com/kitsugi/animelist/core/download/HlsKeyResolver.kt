package com.kitsugi.animelist.core.download

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS `EXT-X-KEY` (AES-128) şifre çözücü.
 *
 * Eski indirici şifreli segmentleri **olduğu gibi** yazıyordu; indirme "tamamlandı"
 * görünüp dosya oynatılamıyordu. Bu sınıf RFC 8216 §5 standardını uygular:
 *
 *  - `METHOD=NONE` → düz geçiş
 *  - `METHOD=AES-128` → 16 baytlık anahtar ile CBC/PKCS7
 *  - `IV` bildirilmemişse → segmentin **medya sırası** 16 baytlık big-endian IV olarak kullanılır
 *
 * Anahtarlar bellekte (host+uri başına) önbelleklenir; aynı playlist'teki yüzlerce
 * segment için anahtar tekrar tekrar indirilmez — bu da 429 riskini azaltır.
 */
class HlsKeyResolver(
    private val fetcher: HlsSegmentFetcher
) {

    private val keyCache = ConcurrentHashMap<String, ByteArray>()

    /**
     * @param bytes segmentin sunucudan gelen (şifreli) içeriği
     * @param key   segmentin `EXT-X-KEY` bilgisi (`null` veya `METHOD=NONE` ise düz geçiş)
     * @param sequence segmentin medya sırası (IV türetimi için)
     * @param headers istek başlıkları (CDN anahtarı da Referer/Origin isteyebilir)
     * @param policy  hız politikası (anahtar indirmesi de sınırlı olmalı)
     */
    suspend fun decrypt(
        bytes: ByteArray,
        key: HlsKey?,
        sequence: Long,
        headers: Map<String, String>,
        policy: HlsDownloadPolicy
    ): ByteArray {
        if (key == null || !key.isEncrypted) return bytes

        val method = key.method.uppercase()
        if (method != "AES-128") {
            // SAMPLE-AES / SAMPLE-AES-CTR: tam segment şifrelemesi değil, örnek bazlı.
            // Desteklenmiyor → sessizce bozuk dosya üretmek yerine açık hata ver.
            throw UnsupportedOperationException(
                "Desteklenmeyen HLS şifreleme yöntemi: ${key.method}. Bu akış indirilemez, oynatıcıda izleyin."
            )
        }

        val keyBytes = resolveKeyBytes(key, headers, policy)
        val iv = key.iv ?: ivFromSequence(sequence)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(bytes)
    }

    /** Init segment (fMP4) şifreliyse aynı anahtarla çözer; sequence 0 kullanılır. */
    suspend fun decryptInitSegment(
        bytes: ByteArray,
        init: HlsInitSegment,
        defaultKey: HlsKey?,
        headers: Map<String, String>,
        policy: HlsDownloadPolicy
    ): ByteArray = decrypt(bytes, init.key ?: defaultKey, sequence = 0L, headers = headers, policy = policy)

    private suspend fun resolveKeyBytes(
        key: HlsKey,
        headers: Map<String, String>,
        policy: HlsDownloadPolicy
    ): ByteArray {
        val uri = key.uri ?: throw IllegalStateException("AES anahtarı URI'si yok")
        keyCache[uri]?.let { return it }

        val raw = fetcher.fetchBytes(uri, headers, byteRange = null, policy = policy)
        if (raw.size < 16) {
            throw IllegalStateException("Geçersiz AES anahtarı (${raw.size} bayt): $uri")
        }
        val normalized = if (raw.size == 16) raw else raw.copyOfRange(0, 16)
        keyCache[uri] = normalized
        return normalized
    }

    /** IV verilmemişse: segment sırası 128-bit big-endian. */
    private fun ivFromSequence(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (i in 15 downTo 8) {
            iv[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        return iv
    }

    /** Teşhis için: anahtar parmak izi (anahtarın kendisi loglanmaz). */
    fun keyFingerprint(uri: String): String? = keyCache[uri]?.let {
        MessageDigest.getInstance("SHA-256").digest(it).take(4).joinToString("") { b ->
            "%02x".format(b)
        }
    }
}
