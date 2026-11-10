package com.kitsugi.animelist.data.backup

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.data.local.AnimeDownloadStore
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.rehydrated
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Kitsugi uygulama verilerinin yedekleme ve geri yükleme yöneticisi.
 *
 * Yedeklenen Öğeler:
 *  - Room veritabanı (Kitsugi_database: izleme/okuma geçmişi, eklentiler, eşleşmeler)
 *  - DataStore ayarları (Kitsugi_settings.preferences_pb)
 *  - İndirme listesi (AnimeDownloadStore)
 *
 * Dosya formatı: ZIP. İçerikte:
 *  - db/Kitsugi_database
 *  - settings/Kitsugi_settings.preferences_pb
 *  - downloads/downloads.json
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        private const val DB_NAME = "Kitsugi_database"
        private const val SETTINGS_PB = "datastore/Kitsugi_settings.preferences_pb"
    }

    private val gson = Gson()

    /** Tüm uygulamayı [backupFile] adresine yedekler. */
    fun backup(backupFile: File) {
        backupFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
            // 1. Veritabanı
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                zos.putNextEntry(ZipEntry("db/$DB_NAME"))
                FileInputStream(dbFile).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
            }

            // 2. Ayarlar (DataStore preferences protobuf)
            val settingsFile = File(context.filesDir, SETTINGS_PB)
            if (settingsFile.exists()) {
                zos.putNextEntry(ZipEntry("settings/Kitsugi_settings.preferences_pb"))
                FileInputStream(settingsFile).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
            }

            // 3. İndirme listesi
            val downloadStore = AnimeDownloadStore(context)
            val downloads = downloadStore.getDownloads()
            val downloadsJson = gson.toJson(downloads)
            zos.putNextEntry(ZipEntry("downloads/downloads.json"))
            zos.write(downloadsJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        Log.i(TAG, "Yedekleme tamamlandı: ${backupFile.absolutePath}")
    }

    /** [backupFile] adresinden tüm uygulamayı geri yükler. */
    fun restore(backupFile: File) {
        ZipInputStream(FileInputStream(backupFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "db/$DB_NAME" -> {
                        val dbFile = context.getDatabasePath(DB_NAME)
                        dbFile.parentFile?.mkdirs()
                        FileOutputStream(dbFile).use { fos -> zis.copyTo(fos) }
                        Log.i(TAG, "Veritabanı geri yüklendi: ${dbFile.absolutePath}")
                    }
                    "settings/Kitsugi_settings.preferences_pb" -> {
                        val settingsFile = File(context.filesDir, SETTINGS_PB)
                        settingsFile.parentFile?.mkdirs()
                        FileOutputStream(settingsFile).use { fos -> zis.copyTo(fos) }
                        Log.i(TAG, "Ayarlar geri yüklendi: ${settingsFile.absolutePath}")
                    }
                    "downloads/downloads.json" -> {
                        val json = String(zis.readBytes(), Charsets.UTF_8)
                        runCatching {
                            val type = object : TypeToken<List<AnimeDownload>>() {}.type
                            val restoredList: List<AnimeDownload> = gson
                                .fromJson<List<AnimeDownload>>(json, type)
                                .orEmpty()
                                .map { it.rehydrated() }
                            val downloadStore = AnimeDownloadStore(context)
                            downloadStore.saveDownloads(restoredList)
                            Log.i(TAG, "İndirme listesi geri yüklendi: ${restoredList.size} öğe")
                        }.onFailure { e ->
                            Log.e(TAG, "İndirme listesi geri yükleme hatası: ${e.message}")
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        Log.i(TAG, "Geri yükleme tamamlandı: ${backupFile.absolutePath}")
    }
}
