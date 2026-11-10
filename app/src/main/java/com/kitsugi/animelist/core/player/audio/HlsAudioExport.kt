package com.kitsugi.animelist.core.player.audio

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A finite HLS rendition. The server's embed page has already been resolved by the plugin. */
internal object HlsAudioExport {
    data class Part(val url: String, val range: AudioExportHttp.Range?, val keyUrl: String?,
                    val iv: ByteArray?, val sequence: Long)
    data class Plan(val parts: List<Part>, val isSeparateRendition: Boolean)
    data class Selection(val url: String, val isSeparateRendition: Boolean)

    fun attributes(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && (raw[i] == ',' || raw[i].isWhitespace())) i++
            if (i >= raw.length) break
            val start = i
            while (i < raw.length && (raw[i] == '-' || raw[i].isLetterOrDigit())) i++
            require(i > start && i < raw.length && raw[i] == '=') { "HLS niteliği geçersiz" }
            val key = raw.substring(start, i++)
            val value = if (i < raw.length && raw[i] == '"') {
                i++
                val begin = i
                while (i < raw.length && raw[i] != '"') i++
                require(i < raw.length) { "HLS niteliği eksik kapatılmış" }
                raw.substring(begin, i++)
            } else {
                val begin = i
                while (i < raw.length && raw[i] != ',') i++
                raw.substring(begin, i).trim()
            }
            require(result.put(key, value) == null) { "Tekrarlanan HLS niteliği" }
            require(i >= raw.length || raw[i] == ',' || raw[i].isWhitespace()) { "HLS niteliği geçersiz" }
        }
        return result
    }

    fun selectMaster(content: String, base: String, identity: AudioTrackIdentity,
                     playerTracks: List<AudioTrackIdentity>): Selection? {
        val lines = content.lines().map { it.trim() }
        require(lines.none { it.startsWith("#EXT-X-SESSION-KEY:") || it.startsWith("#EXT-X-DEFINE:") }) {
            "HLS oturum anahtarı/değişkenleri desteklenmiyor"
        }
        val variants = lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
            // The URI must belong to THIS STREAM-INF, not a later variant after a missing line.
            val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() }
                ?.takeIf { !it.startsWith('#') } ?: error("HLS varyant adresi eksik")
            attributes(line.substringAfter(':')) to AudioExportHttp.resolve(base, uri)
        }
        if (variants.isEmpty()) return null
        val groups = variants.mapNotNull { it.first["AUDIO"] }.toSet()
        val renditions = lines.filter { it.startsWith("#EXT-X-MEDIA:") }
            .map { attributes(it.substringAfter(':')) }
            .filter { it["TYPE"] == "AUDIO" && it["GROUP-ID"] in groups }
        val wanted = AudioExportSpec.language(identity.language)
        val name = identity.name?.trim()?.takeIf { it.isNotEmpty() }
        val named = renditions.filter { item ->
            name != null && item["NAME"]?.equals(name, ignoreCase = true) == true &&
                (wanted.isEmpty() || item["LANGUAGE"].isNullOrBlank() ||
                    AudioExportSpec.language(item["LANGUAGE"]) == wanted)
        }
        val languageMatched = renditions.filter { item ->
            wanted.isNotEmpty() && AudioExportSpec.language(item["LANGUAGE"]) == wanted
        }
        val needsName = AudioExportSpec.needsManifestName(identity, playerTracks)
        val sameFileRows = playerTracks.count { it.externalUrl == identity.externalUrl }
        val chosen = when {
            named.size == 1 -> named.single()
            named.size > 1 || needsName -> error("HLS ses adı tek bir rendition ile eşleşmiyor")
            languageMatched.size == 1 -> languageMatched.single()
            languageMatched.size > 1 -> error("Aynı dilde birden çok HLS sesi var")
            renditions.size == 1 && sameFileRows == 1 &&
                (wanted.isEmpty() || renditions.single()["LANGUAGE"].isNullOrBlank() ||
                    AudioExportSpec.language(renditions.single()["LANGUAGE"]) == wanted) -> renditions.single()
            renditions.isNotEmpty() -> error("Seçilen ses HLS manifestiyle eşleşmiyor")
            else -> null
        }
        if (chosen != null) {
            val audioUri = chosen["URI"]
            if (!audioUri.isNullOrBlank()) return Selection(AudioExportHttp.resolve(base, audioUri), true)
            // EXT-X-MEDIA without URI is muxed into a variant belonging to GROUP-ID.
            val group = chosen["GROUP-ID"]
            val variant = variants.filter { it.first["AUDIO"] == group }
                .minByOrNull { it.first["BANDWIDTH"]?.toLongOrNull() ?: Long.MAX_VALUE }
                ?: error("HLS ses grubunun video varyantı yok")
            return Selection(variant.second, false)
        }
        require(sameFileRows == 1) { "HLS varyantlarında ayrı ses kimliği yok" }
        return Selection(variants.minByOrNull { it.first["BANDWIDTH"]?.toLongOrNull() ?: Long.MAX_VALUE }!!.second, false)
    }

    private fun range(raw: String, target: String, last: Pair<String, Long>?): Pair<AudioExportHttp.Range, Pair<String, Long>> {
        val length = raw.substringBefore('@').toLongOrNull() ?: error("HLS byte aralığı geçersiz")
        val offset = if ('@' in raw) raw.substringAfter('@').toLongOrNull()
            ?: error("HLS byte başlangıcı geçersiz") else last?.takeIf { it.first == target }?.second
            ?: error("HLS byte başlangıcı belirsiz")
        require(length > 0 && offset >= 0 && offset <= Long.MAX_VALUE - length) { "HLS byte aralığı taşmış" }
        return AudioExportHttp.Range(offset, offset + length - 1) to (target to (offset + length))
    }

    private fun iv(raw: String): ByteArray {
        val digits = raw.removePrefix("0x").removePrefix("0X")
        require(digits.isNotEmpty() && digits.length <= 32 && digits.all { it in "0123456789abcdefABCDEF" }) {
            "HLS IV geçersiz"
        }
        val padded = digits.padStart(32, '0')
        return ByteArray(16) { padded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun sequenceIv(value: Long): ByteArray = ByteArray(16).also { result ->
        var number = value
        for (i in 15 downTo 8) { result[i] = number.toByte(); number = number ushr 8 }
    }

    fun plan(content: String, base: String, separate: Boolean): Plan {
        val lines = content.lines().map { it.trim() }
        require(lines.firstOrNull() == "#EXTM3U" && lines.any { it == "#EXT-X-ENDLIST" }) {
            "HLS canlı veya geçersiz: sonlu kayıt gerekli"
        }
        require(lines.none { line ->
            listOf("#EXT-X-DISCONTINUITY", "#EXT-X-PART", "#EXT-X-PRELOAD-HINT", "#EXT-X-SKIP",
                "#EXT-X-GAP", "#EXT-X-I-FRAMES-ONLY", "#EXT-X-SESSION-KEY", "#EXT-X-DEFINE",
                "#EXT-X-STREAM-INF").any { line.startsWith(it) }
        }) { "Bu HLS bölümleme veya koruma yöntemi desteklenmiyor" }
        val sequenceTags = lines.filter { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
        require(sequenceTags.size <= 1) { "Birden çok HLS sıra numarası var" }
        val start = sequenceTags.singleOrNull()?.substringAfter(':')?.toLongOrNull()
            ?: if (sequenceTags.isEmpty()) 0L else error("HLS sıra numarası geçersiz")
        require(start >= 0) { "HLS sıra numarası geçersiz" }
        var sequence = start
        var key: String? = null
        var keyIv: ByteArray? = null
        var segmentRange: String? = null
        var previousRange: Pair<String, Long>? = null
        var mapFound = false
        var hasInf = false
        val parts = mutableListOf<Part>()
        var mediaCount = 0
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val attr = attributes(line.substringAfter(':'))
                    when (attr["METHOD"]) {
                        "NONE" -> { key = null; keyIv = null }
                        "AES-128" -> {
                            require(attr["KEYFORMAT"] == null || attr["KEYFORMAT"] == "identity") {
                                "HLS korumalı anahtar desteklenmiyor"
                            }
                            key = AudioExportHttp.resolve(base, attr["URI"] ?: error("HLS anahtarı eksik"))
                            keyIv = attr["IV"]?.let(::iv)
                        }
                        else -> error("HLS DRM/SAMPLE-AES ses çıkarımı desteklenmiyor")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    require(!mapFound && mediaCount == 0) { "HLS başlangıç parçası değişiyor" }
                    val attr = attributes(line.substringAfter(':'))
                    val target = AudioExportHttp.resolve(base, attr["URI"] ?: error("HLS başlangıç adresi eksik"))
                    val bytes = attr["BYTERANGE"]?.let { range(it, target, null).first }
                    require(key == null || keyIv != null) { "Şifreli HLS init için sabit IV gerekli" }
                    parts += Part(target, bytes, key, keyIv, sequence)
                    mapFound = true
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> segmentRange = line.substringAfter(':')
                line.startsWith("#EXTINF:") -> hasInf = true
                line.isNotEmpty() && !line.startsWith('#') -> {
                    require(hasInf) { "HLS parçasının süresi eksik" }
                    require(mediaCount < 25_000) { "HLS parça sınırı aşıldı" }
                    val target = AudioExportHttp.resolve(base, line)
                    val bytes = segmentRange?.let { spec ->
                        range(spec, target, previousRange).also { previousRange = it.second }.first
                    } ?: run { previousRange = null; null }
                    parts += Part(target, bytes, key, keyIv ?: sequenceIv(sequence), sequence)
                    mediaCount++
                    sequence = Math.addExact(sequence, 1)
                    hasInf = false; segmentRange = null
                }
            }
        }
        require(mediaCount > 0 && !hasInf && segmentRange == null) { "HLS parça listesi eksik" }
        return Plan(parts, separate)
    }

    suspend fun materialize(url: String, headers: Map<String, String>, identity: AudioTrackIdentity,
                            playerTracks: List<AudioTrackIdentity>, output: File, onProgress: suspend (Int) -> Unit,
                            initial: Pair<String, String>? = null): Boolean {
        val (masterUrl, master) = initial ?: AudioExportHttp.text(url, url, headers)
        val first = selectMaster(master, masterUrl, identity, playerTracks)
        val selected = first?.let { AudioExportHttp.text(url, it.url, headers) }
        val (playlistUrl, content) = selected ?: (masterUrl to master)
        require(first != null || !AudioExportSpec.needsManifestName(identity, playerTracks)) {
            "HLS parçası birden çok dil/isim arasında ayırt edilemiyor"
        }
        val plan = plan(content, playlistUrl, first?.isSeparateRendition == true)
        var total = 0L
        var lastKeyUrl: String? = null
        var lastKey: ByteArray? = null
        try {
            output.outputStream().buffered().use { out ->
                plan.parts.forEachIndexed { index, part ->
                    currentCoroutineContext().ensureActive()
                    if (part.keyUrl != lastKeyUrl) {
                        lastKey?.fill(0)
                        lastKey = part.keyUrl?.let { address ->
                            AudioExportHttp.withResponse(url, address, headers) { response ->
                                val input = response.body?.byteStream() ?: error("HLS anahtarı boş")
                                val value = ByteArray(17)
                                var count = 0
                                input.use {
                                    while (count < value.size) {
                                        val read = it.read(value, count, value.size - count)
                                        if (read < 0) break
                                        count += read
                                    }
                                }
                                require(count == 16) { "HLS anahtar boyutu geçersiz" }
                                value.copyOf(16)
                            }
                        }
                        lastKeyUrl = part.keyUrl
                    }
                    val keyBytes = lastKey
                    total += AudioExportHttp.append(url, part.url, headers, part.range, out, output.parentFile!!, total) { stream ->
                        if (keyBytes == null) stream else {
                            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"),
                                IvParameterSpec(part.iv ?: error("HLS IV eksik")))
                            CipherInputStream(stream, cipher)
                        }
                    }
                    onProgress(5 + (index + 1) * 65 / plan.parts.size)
                }
            }
        } finally { lastKey?.fill(0) }
        return plan.isSeparateRendition
    }
}
