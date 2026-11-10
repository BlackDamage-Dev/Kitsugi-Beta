package com.kitsugi.animelist.data.manga

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object MangaReaderStorage {
    const val MAX_IMPORT_BYTES = 512L * 1024L * 1024L
    const val MAX_RENDER_CACHE_BYTES = 384L * 1024L * 1024L
    val supportedExtensions = setOf("zip", "cbz", "rar", "cbr", "epub", "pdf", "txt", "md", "markdown", "fb2")

    suspend fun importDocument(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(0)
                val size = if (cursor.isNull(1)) null else cursor.getLong(1)
                name to size
            }
        }
        val name = metadata?.first ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in supportedExtensions) { "Desteklenen biçimler: ZIP, CBZ, CBR, RAR, EPUB, PDF, TXT, MD, FB2" }
        metadata?.second?.let { require(it in 0..MAX_IMPORT_BYTES) { "Dosya 512 MB sınırını aşıyor" } }
        val directory = File(context.filesDir, "local-archives").apply { mkdirs() }
        val temporary = File(directory, "import-${System.nanoTime()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            (resolver.openInputStream(uri) ?: error("Dosya açılamadı")).use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMPORT_BYTES) error("Dosya 512 MB sınırını aşıyor")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(total > 0L) { "Boş dosya açılamaz" }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val output = File(directory, "$hash.$extension")
            if (output.exists()) temporary.delete()
            else if (!temporary.renameTo(output)) error("Dosya güvenli depolamaya taşınamadı")
            output
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    /** LRU cleanup for derived images only; imported books are never removed by this function. */
    @Synchronized
    fun pruneDerivedCache(directory: File, maxBytes: Long = MAX_RENDER_CACHE_BYTES, keep: File? = null) {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        var bytes = 0L
        files.forEach { file ->
            val length = file.length()
            if (file == keep) {
                file.setLastModified(System.currentTimeMillis())
                bytes += length
            } else if (bytes + length <= maxBytes) bytes += length
            else file.delete()
        }
    }
}
