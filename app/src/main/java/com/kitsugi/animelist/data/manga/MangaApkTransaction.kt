package com.kitsugi.animelist.data.manga

import java.io.File
import java.io.IOException

/** Restore the previous APK if a download, trust decision, or load fails. */
internal fun withMangaApkRollback(target: File, install: () -> Boolean): Boolean {
    val staging = File(requireNotNull(target.parentFile), ".staging")
    if (!staging.isDirectory && !staging.mkdirs()) throw IOException("Eklenti geçici dizini oluşturulamadı")
    val backup = if (target.isFile) {
        File.createTempFile("previous_", ".apk", staging).also { file ->
            try {
                target.copyTo(file, overwrite = true)
                if (!file.setReadOnly()) throw IOException("Eklenti yedeği salt okunur yapılamadı")
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    } else null

    var committed = false
    try {
        committed = install()
        return committed
    } finally {
        if (committed) {
            backup?.let {
                it.setWritable(true)
                it.delete()
            }
        } else if (backup != null) {
            // Same filesystem: replace the failed candidate atomically. Retain the
            // backup if rename fails, so it can still be recovered manually.
            if (target.exists()) target.setWritable(true)
            if (!backup.renameTo(target)) throw IOException("Önceki eklenti geri yüklenemedi: ${backup.absolutePath}")
        } else if (target.exists()) {
            target.setWritable(true)
            if (!target.delete()) throw IOException("Başarısız eklenti kurulumu temizlenemedi: ${target.absolutePath}")
        }
    }
}
