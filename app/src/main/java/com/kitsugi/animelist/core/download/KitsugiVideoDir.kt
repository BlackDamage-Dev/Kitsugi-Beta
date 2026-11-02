package com.kitsugi.animelist.core.download

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Videolar sekmesinden indirilen dosyaların konumu.
 *
 * Sıra:
 *  1. `Download/Kitsugi/Video` (herkese açık, dosya yöneticisinde görünür),
 *  2. uygulamanın dış `Download/Kitsugi/Video` klasörü (scoped storage engellerse),
 *  3. uygulama içi `filesDir/Kitsugi/Video` (son çare — her zaman yazılabilir).
 *
 * Aynı düzen `DownloadStorage`'ın video kökleriyle uyumludur; İndirilenler ekranı
 * "Tümünü sil" derken bu dosyaları da görür.
 */
internal object KitsugiVideoDir {

    private const val TAG = "KitsugiVideoDir"
    private const val PUBLIC_KITSUGI = "Kitsugi"

    fun resolve(context: Context): File {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$PUBLIC_KITSUGI/Video"
        )
        if (runCatching { publicDir.mkdirs(); publicDir.isDirectory && publicDir.canWrite() }.getOrDefault(false)) {
            return publicDir
        }

        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (external != null) {
            val dir = File(external, "$PUBLIC_KITSUGI/Video")
            if (runCatching { dir.mkdirs(); dir.isDirectory }.getOrDefault(false)) return dir
        }

        val fallback = File(context.filesDir, "Kitsugi/Video")
        runCatching { fallback.mkdirs() }
        Log.w(TAG, "Herkese açık klasöre yazılamadı, uygulama klasörü kullanılıyor: ${fallback.absolutePath}")
        return fallback
    }
}
