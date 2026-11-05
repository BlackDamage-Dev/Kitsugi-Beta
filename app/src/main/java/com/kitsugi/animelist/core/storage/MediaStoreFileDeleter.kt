package com.kitsugi.animelist.core.storage

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Paylaşılan depolamadaki (Download/Kitsugi/… ve Pictures/Kitsugi/…) dosyaları silmenin
 * **tek güvenilir yolu**.
 *
 * ## Neden bu nesne var?
 * Android 10+ (Scoped Storage) döneminde bir dosya `MediaStore` üzerinden oluşturulduysa
 * `File.delete()` çağrısı **sessizce `false` döner** (izin hatası fırlatmaz). İndirilen
 * görseller `MediaStore.Downloads` / `MediaStore.Images`, altyazılar ise
 * `MediaStore.Downloads` üzerinden yazıldığı için İndirilenler ekranındaki "sil" düğmeleri
 * hiçbir şey silmiyordu: dosya diskte kalıyor, liste tazelenince kart geri geliyordu.
 *
 * Doğru yol: kaydı `MediaStore` içinde **DISPLAY_NAME** (ve mümkünse `RELATIVE_PATH`) ile
 * bulup `ContentResolver.delete(uri)` çağırmak. Bu nesne:
 *  - dosya adı + göreli klasör ile kaydı arar (`DATA` sütunu Android 11+'da kullanımdan
 *    kaldırıldığı için artık yola göre arama yapmıyoruz),
 *  - uygulamanın sahibi olduğu kayıtları siler,
 *  - başka bir kuruluma/uygulamaya ait kayıtları **sistem onayı** için listeler
 *    (Android 11+ `MediaStore.createDeleteRequest`),
 *  - hiçbir adımda istisna fırlatmaz; sonucu [Outcome] ile bildirir.
 *
 * Tüm fonksiyonlar bloklayan disk/ContentResolver işi yapar → **IO** bağlamında çağırın.
 */
internal object MediaStoreFileDeleter {

    private const val TAG = "MediaStoreFileDeleter"

    /** Hangi klasör ailesinde arama yapılacağını belirler. */
    enum class Mode { IMAGE, SUBTITLE, AUDIO, ANY }

    data class Outcome(
        /** En az bir MediaStore kaydı kaldırıldı mı? */
        val deleted: Boolean = false,
        /** Uygulamanın sahibi olmadığı, sistem onayı gerektiren kayıtlar. */
        val foreignUris: List<Uri> = emptyList(),
        /** Silme denemesi hata mesajı (yalnızca gerçek bir hata varsa). */
        val message: String? = null
    )

    private fun collectionsFor(mode: Mode): List<Uri> = when (mode) {
        Mode.IMAGE -> listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        Mode.SUBTITLE -> listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            // .srt/.ass gibi medya olmayan dosyalar yalnızca Files koleksiyonunda görünür.
            MediaStore.Files.getContentUri("external")
        )
        Mode.AUDIO -> listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )
        Mode.ANY -> listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )
    }

    /**
     * `Download/Kitsugi/Images/Kitsugi_Foo.jpg` → `Download/Kitsugi/Images`
     * (birincil harici depolama dışındaki yollar için `null`).
     */
    fun relativePathOf(file: File): String? {
        val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
            ?: return null
        val absolute = file.absolutePath
        if (!absolute.startsWith(root)) return null
        val relative = absolute.removePrefix(root).trimStart('/')
        val parent = relative.substringBeforeLast('/', "")
        return parent.ifBlank { null }
    }

    /**
     * Verilen [fileName] için MediaStore kayıtlarını siler.
     *
     * @param relativePath dosyanın bulunduğu göreli klasör (varsa arama daraltılır).
     * @param mode         hangi koleksiyonların taranacağı.
     */
    fun deleteByName(
        context: Context,
        fileName: String,
        mode: Mode,
        relativePath: String? = null
    ): Outcome {
        if (fileName.isBlank()) return Outcome()
        val appContext = context.applicationContext
        var deleted = false
        val foreign = LinkedHashSet<Uri>()
        var message: String? = null

        collectionsFor(mode).forEach { collection ->
            val rows = queryRows(appContext, collection, fileName, relativePath)
            rows.forEach { row ->
                val uri = ContentUris.withAppendedId(collection, row.id)
                if (!row.owner.isNullOrBlank() && row.owner != appContext.packageName) {
                    // Başka paketin kaydı → doğrudan silinemez, kullanıcı onayı gerekir.
                    foreign += uri
                    return@forEach
                }
                val ok = runCatching { appContext.contentResolver.delete(uri, null, null) > 0 }
                    .getOrElse { throwable ->
                        if (throwable is SecurityException) {
                            foreign += uri
                        } else {
                            message = throwable.localizedMessage
                            Log.w(TAG, "MediaStore silme hatası ($uri): ${throwable.message}")
                        }
                        false
                    }
                if (ok) deleted = true
            }
        }

        if (deleted) notifyCollectionsChanged(appContext)
        return Outcome(deleted = deleted, foreignUris = foreign.toList(), message = message)
    }

    /** Bir dosya yolundan ad + göreli klasör türeterek siler (DATA sütununa dokunmaz). */
    fun deleteByPath(context: Context, absolutePath: String, mode: Mode): Outcome {
        if (absolutePath.isBlank()) return Outcome()
        val file = File(absolutePath)
        return deleteByName(context, file.name, mode, relativePathOf(file))
    }

    /**
     * Android 11+ için sistem silme onayı isteği üretir ([MediaStore.createDeleteRequest]).
     * Kullanıcı onaylarsa kayıtlar sistem tarafından silinir; uygulamanın ek bir şey
     * yapmasına gerek kalmaz (yalnızca listeyi tazelemesi yeterlidir).
     */
    fun systemDeleteRequestSender(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }.onFailure { Log.w(TAG, "createDeleteRequest hatası: ${it.message}") }.getOrNull()
    }

    // ─── İç yardımcılar ────────────────────────────────────────────────────────

    private data class Row(val id: Long, val owner: String?)

    /**
     * Kayıtları ad (ve mümkünse göreli klasör) ile arar. Göreli klasörle eşleşme
     * bulunamazsa yalnızca ada göre tekrar dener — MediaStore bazı sürümlerde yolu
     * sonundaki `/` ile ya da klasör adı büyük/küçük harf duyarsız saklayabiliyor.
     */
    private fun queryRows(
        context: Context,
        collection: Uri,
        fileName: String,
        relativePath: String?
    ): List<Row> {
        val normalized = relativePath?.trimEnd('/')?.takeIf { it.isNotBlank() }
        if (normalized != null) {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
            val rows = rawQuery(context, collection, selection, arrayOf(fileName, "$normalized/", normalized))
            if (rows.isNotEmpty()) return rows
        }
        return rawQuery(
            context,
            collection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName)
        )
    }

    private fun rawQuery(
        context: Context,
        collection: Uri,
        selection: String,
        args: Array<String>
    ): List<Row> {
        val withOwner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = if (withOwner) {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        } else {
            arrayOf(MediaStore.MediaColumns._ID)
        }
        val result = runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val ownerIndex = if (withOwner) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                } else {
                    -1
                }
                buildList {
                    while (cursor.moveToNext()) {
                        add(Row(cursor.getLong(idIndex), if (ownerIndex >= 0) cursor.getString(ownerIndex) else null))
                    }
                }
            }
        }.getOrNull()

        if (result != null) return result

        // OWNER_PACKAGE_NAME projeksiyonu bazı koleksiyonlarda (Files) reddedilebiliyor →
        // yalnızca _ID ile tekrar dene, sahiplik bilinmiyorsa silme sırasında anlaşılır.
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                buildList {
                    while (cursor.moveToNext()) add(Row(cursor.getLong(idIndex), null))
                }
            }
        }.getOrNull().orEmpty()
    }

    /** Galeri/dosya yöneticisi gibi uygulamaların listeyi hemen tazelemesini sağlar. */
    private fun notifyCollectionsChanged(context: Context) {
        val resolver = context.contentResolver
        runCatching {
            resolver.notifyChange(MediaStore.Files.getContentUri("external"), null)
        }
        runCatching {
            resolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        }
        runCatching {
            resolver.notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null)
        }
        runCatching {
            resolver.notifyChange(MediaStore.Downloads.EXTERNAL_CONTENT_URI, null)
        }
    }
}
