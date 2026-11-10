package com.kitsugi.animelist.data.remote.anisub

import android.util.Log
import com.github.junrar.Junrar
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * AniSub paketlerini (ZIP / RAR / GZIP) güvenle açar ve içindeki
 * altyazı dosyalarını düz bir dizine çıkarır.
 *
 * Güvenlik sınırları (zip bomb / zip slip koruması):
 *  - Her Dosya en çok [MAX_SINGLE_FILE_BYTES], toplam en çok [MAX_TOTAL_BYTES]
 *  - En çok [MAX_FILE_COUNT] altyazı dosyası
 *  - yollar her zaman düzleştirilir: yalnızca Dosya adı kullanılır
 */
object SubtitleArchiveExtractor {

    private const val TAG = "SubtitleArchive"
    private const val MAX_SINGLE_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
    private const val MAX_FILE_COUNT = 400

    enum class ArchiveKind { ZIP, RAR, SEVEN_ZIP, GZIP, PLAIN_TEXT, UNKNOWN }

    /** Dosyanın ilk baytlarından (magic number) türünü belirler; uzantıya güvenmez. */
    fun sniffKind(file: File): ArchiveKind {
        return try {
            FileInputStream(file).use { input ->
                // Magic baytlar icin 8 bayt yeter; ancak duz metin altyazi tanisi
                // ("[Script Info]" vb.) daha uzun bir onizleme gerektirir.
                val head = ByteArray(512)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 2) return ArchiveKind.UNKNOWN
                sniffKind(head.copyOf(read))
            }
        } catch (e: Exception) {
            Log.w(TAG, "sniffKind başarısız: ${e.message}")
            ArchiveKind.UNKNOWN
        }
    }

    fun sniffKind(head: ByteArray): ArchiveKind {
        fun eq(offset: Int, vararg bytes: Int): Boolean {
            if (head.size < offset + bytes.size) return false
            return bytes.indices.all { head[offset + it].toInt() and 0xFF == bytes[it] }
        }
        return when {
            // ZIP: PK\x03\x04 / PK\x05\x06 (boş) / PK\x07\x08 (spanned)
            eq(0, 0x50, 0x4B, 0x03, 0x04) || eq(0, 0x50, 0x4B, 0x05, 0x06) ||
                eq(0, 0x50, 0x4B, 0x07, 0x08) -> ArchiveKind.ZIP
            // RAR: "Rar!" 1A 07 (00 = v4, 01 00 = v5)
            eq(0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> ArchiveKind.RAR
            // 7z: 37 7A BC AF 27 1C
            eq(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ArchiveKind.SEVEN_ZIP
            // GZIP: 1F 8B
            eq(0, 0x1F, 0x8B) -> ArchiveKind.GZIP
            isPlainSubtitleText(head) -> ArchiveKind.PLAIN_TEXT
            else -> ArchiveKind.UNKNOWN
        }
    }

    /** İndirilen ham içeriğin arşiv değil doğrudan altyazı metni olup olmadığını anlar. */
    fun isPlainSubtitleText(head: ByteArray): Boolean {
        // BOM'u atlayıp ilk kısmı metin olarak yorumla.
        val text = try {
            String(head, Charsets.UTF_8)
        } catch (_: Exception) {
            return false
        }
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return trimmed.startsWith("[Script Info]", ignoreCase = true) ||
            trimmed.startsWith("WEBVTT") ||
            trimmed.startsWith("[Events]", ignoreCase = true) ||
            // SRT: "1\n00:00:.." veya doğrudan zaman kodu
            Regex("""^\d+\s*\r?\n\d{2}:\d{2}:\d{2}""").find(trimmed) != null ||
            trimmed.contains("-->")
    }

    /**
     * Arşivi [destinationDir] içine açar ve çıkarılan ALTYAZI dosyalarını döner.
     * Arşiv değilse veya açılamazsa boş liste döner (çağıran uygun yola düşer).
     */
    fun extractSubtitles(archiveFile: File, destinationDir: File): List<File> {
        if (!destinationDir.exists()) destinationDir.mkdirs()
        return when (sniffKind(archiveFile)) {
            ArchiveKind.ZIP -> extractZip(archiveFile, destinationDir)
            ArchiveKind.RAR -> extractRar(archiveFile, destinationDir)
            ArchiveKind.GZIP -> extractGzip(archiveFile, destinationDir)
            ArchiveKind.SEVEN_ZIP -> {
                Log.w(TAG, "7z paketi cihazda açılamıyor (desteklenmiyor): ${archiveFile.name}")
                emptyList()
            }
            else -> emptyList()
        }
    }

    // ---------------------------------------------------------------- ZIP

    private fun extractZip(zipFile: File, destinationDir: File): List<File> {
        // Fansub ZIP'leri çoğu zaman UTF-8 bayrağı taşımadan yerel kod sayfasıyla
        // (Windows-1254, Shift_JIS) yazılır; ilk açılış Dosya adları bozuk
        // olduğu için patlarsa sonraki karakter kodlamasıyla tekrar denenir.
        val charsets = listOfNotNull(
            Charsets.UTF_8,
            runCatching { Charset.forName("windows-1254") }.getOrNull(),
            runCatching { Charset.forName("Shift_JIS") }.getOrNull(),
            runCatching { Charset.forName("windows-1252") }.getOrNull()
        )
        var lastError: Exception? = null
        for (charset in charsets) {
            try {
                return extractZipWithCharset(zipFile, destinationDir, charset)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "ZIP açılamadı (charset=$charset): ${e.message}")
            }
        }
        Log.e(TAG, "ZIP hiçbir karakter kodlamasıyla açılamadı: ${lastError?.message}")
        return emptyList()
    }

    private fun extractZipWithCharset(zipFile: File, destinationDir: File, charset: Charset): List<File> {
        val extracted = mutableListOf<File>()
        var totalBytes = 0L
        ZipFile(zipFile, charset).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val cleanName = sanitizeFileName(entry.name) ?: continue
                if (!AniSubEpisodeMatcher.isSubtitleFileName(cleanName)) continue
                if (entry.size > MAX_SINGLE_FILE_BYTES) {
                    Log.w(TAG, "Aşırı büyük altyazı girişi atlandı: $cleanName (${entry.size})")
                    continue
                }
                if (extracted.size >= MAX_FILE_COUNT || totalBytes >= MAX_TOTAL_BYTES) break

                val target = uniqueFile(destinationDir, cleanName)
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() > 0) {
                    extracted += target
                    totalBytes += target.length()
                } else {
                    target.delete()
                }
            }
        }
        return extracted
    }

    // ---------------------------------------------------------------- RAR

    private fun extractRar(rarFile: File, destinationDir: File): List<File> {
        // Junrar iç içe dizin yapısını koruyarak çıkarır; geçici alana çıkarıp
        // altyazıları düzleştirerek hedef dizine taşırız.
        val tempDir = File(destinationDir, ".rar-tmp")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()
        return try {
            Junrar.extract(rarFile, tempDir)
            val result = mutableListOf<File>()
            var totalBytes = 0L
            tempDir.walkTopDown()
                .filter { it.isFile && AniSubEpisodeMatcher.isSubtitleFileName(it.name) }
                .sortedBy { it.absolutePath }
                .forEach { source ->
                    if (result.size >= MAX_FILE_COUNT || totalBytes >= MAX_TOTAL_BYTES) return@forEach
                    if (source.length() > MAX_SINGLE_FILE_BYTES) return@forEach
                    val target = uniqueFile(destinationDir, source.name)
                    source.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.length() > 0) {
                        result += target
                        totalBytes += target.length()
                    } else {
                        target.delete()
                    }
                }
            result
        } catch (e: Exception) {
            Log.e(TAG, "RAR açılamadı (${rarFile.name}): ${e.message}", e)
            emptyList()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- GZIP

    private fun extractGzip(gzipFile: File, destinationDir: File): List<File> {
        // Tek dosyalı gzip: Dosya adı "...ass.gz" ise iç ad o kabul edilir.
        val innerName = sanitizeFileName(gzipFile.name.removeSuffix(".gz").removeSuffix(".GZ"))
            ?.takeIf { AniSubEpisodeMatcher.isSubtitleFileName(it) }
            ?: return emptyList()
        return try {
            val target = uniqueFile(destinationDir, innerName)
            GZIPInputStream(FileInputStream(gzipFile)).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0) listOf(target) else {
                target.delete()
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "GZIP açılamadı (${gzipFile.name}): ${e.message}", e)
            emptyList()
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Girdi yolunu güvenli tek Dosya adına indirir.
     * "../" ve alt dizinler atılır; (__MACOSX / gizli girdiler yok sayılır.)
     */
    fun sanitizeFileName(entryName: String): String? {
        val base = entryName.replace('\\', '/').substringAfterLast('/').trim()
        if (base.isEmpty() || base.startsWith(".")) return null
        if (entryName.contains("__MACOSX")) return null
        // Dosya sisteminde sorun çıkaran karakterler
        val cleaned = base.replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
        return cleaned.takeIf { it.isNotBlank() }?.let {
            // Aşırı uzun isimler Android ext4'te sorun çıkarmasın
            if (it.length <= 180) it else {
                val ext = it.substringAfterLast('.', "")
                val stem = it.substringBeforeLast('.').take(160)
                if (ext.isEmpty()) stem else "$stem.$ext"
            }
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (candidate.exists()) {
            val next = if (ext.isEmpty()) "$stem-$i" else "$stem-$i.$ext"
            candidate = File(dir, next)
            i++
        }
        return candidate
    }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
}
