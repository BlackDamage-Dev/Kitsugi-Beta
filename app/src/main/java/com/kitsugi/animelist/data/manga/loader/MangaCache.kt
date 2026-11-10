package com.kitsugi.animelist.data.manga.loader

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manga sayfası resimlerini diske önbellekleyen (cache) sınıf.
 *
 * Her sayfa resmi URL'nin SHA-1 hash'i ile adlandırılarak `filesDir/manga_cache/` altına kaydedilir.
 * Önbellek boyutu aşıldığında en eski dosyalar silinir (LRU yaklaşımı).
 *
 * ## Performans notu (2026-10-11 düzeltmesi)
 * Önceki sürümde `trimIfNeeded()` **her** `putImageToCache()` çağrısında tüm dizini
 * `listFiles()` ile listeliyor ve `sumOf { it.length() }` ile boyutlarını topluyordu. Erken çıkış
 * ("bütçe aşılmadı") ancak bu pahalı ölçümden **sonra** yapılabildiği için, önbellek 150 MB'ın
 * altındayken bile her manga sayfasında on binlerce dosya taranıyordu. Üstelik metod
 * `@Synchronized` olduğundan eşzamanlı sayfa indirmeleri birbirini bekliyordu. Biriken önbellek
 * ile birlikte bu tarama, okuyucuda gözle görülür kasmanın ana sebebiydi.
 *
 * Yeni sürüm toplam boyutu **artımlı sayaçla** bellekte tutar: ölçüm yalnızca ilk erişimde bir kez
 * ve bütçe aşıldığında yapılır. Davranış (dosya adlandırma, LRU, geçerlilik kontrolü) aynıdır.
 */
class MangaCache(private val context: Context) {

    private val cacheDir = File(context.filesDir, "manga_cache").also {
        if (!it.exists()) it.mkdirs()
    }

    companion object {
        private const val TAG          = "MangaCache"
        private const val MAX_SIZE_MB  = 150L               // Maksimum 150 MB önbellek
        private const val MAX_BYTES    = MAX_SIZE_MB * 1024 * 1024

        @Volatile
        private var singleton: MangaCache? = null

        /** Process-wide cache object for source-owned streaming readers. */
        fun getInstance(context: Context): MangaCache {
            return singleton ?: synchronized(this) {
                singleton ?: MangaCache(context.applicationContext).also { singleton = it }
            }
        }

        /**
         * Artımlı toplam boyut — **süreç genelinde paylaşılır**.
         *
         * MangaCache birden çok yerde (okuyucu ViewModel'i, kaynak deposu, StorageJanitor)
         * ayrı ayrı örnekleniyor ve hepsi AYNI dizini kullandığı için sayaç örnek başına
         * tutulursa örnekler birbirinin yazdığını göremez ve bütçe kontrolü gecikir.
         * -1 → henüz ölçülmedi.
         */
        @Volatile
        private var cachedTotalBytes: Long = -1L

        /** Aynı anda tek okuma/hesaplama yapılsın; sayaç tutarlılığı korunur. */
        private val sizeLock = Any()
    }

    // ─── Kontrol ─────────────────────────────────────────────────────────────

    /** Verilen [imageUrl] için önbellekte resim dosyası var mı? */
    fun isImageInCache(imageUrl: String): Boolean {
        val file = cacheFile(imageUrl)
        if (!file.isFile) return false
        if (isDecodableImage(file)) return true
        // Evict old HTML/error responses, not just empty downloads.
        val size = file.length()
        if (file.delete()) adjustSize(-size)
        return false
    }

    private fun imageDimensions(file: File): Pair<Int, Int>? {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            com.kitsugi.animelist.data.manga.image.MangaNativeFormats.dimensions(file)
        }
    }

    private fun isDecodableImage(file: File): Boolean = imageDimensions(file) != null

    /** Call off the UI thread; needed to give webtoon pages their actual aspect ratio. */
    fun getImageDimensions(imageUrl: String): Pair<Int, Int>? = imageDimensions(cacheFile(imageUrl))

    /** Remove a failed decode before asking the source for a fresh copy. */
    @Synchronized
    fun removeImage(imageUrl: String) {
        val file = cacheFile(imageUrl)
        val size = file.length()
        if (file.delete()) adjustSize(-size)
    }

    // ─── Okuma ───────────────────────────────────────────────────────────────

    /**
     * Önbellekteki resim dosyasını döndürür.
     * Bulunamazsa null döner.
     */
    fun getImageFile(imageUrl: String): File {
        val file = cacheFile(imageUrl)
        // Son erişim zamanını güncelle (LRU için)
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    // ─── Yazma ───────────────────────────────────────────────────────────────

    /**
     * [stream] içinden okunan veriyi önbelleğe yazar.
     * Yazma işlemi atomik olarak gerçekleşir (önce geçici dosyaya yazılır).
     */
    @Synchronized
    fun putImageToCache(imageUrl: String, stream: InputStream) {
        val target = cacheFile(imageUrl)
        val temp = File.createTempFile(target.name, ".tmp", cacheDir)
        try {
            FileOutputStream(temp).use { out -> stream.copyTo(out) }
            if (!isDecodableImage(temp)) throw java.io.IOException("Kaynak geçerli bir görsel yerine boş/HTML veya desteklenmeyen veri döndürdü")

            val previousLength = if (target.isFile) target.length() else 0L
            // Linux/Android'de renameTo hedefin üzerine atomik olarak yazar.
            if (!temp.renameTo(target)) throw java.io.IOException("Görsel önbelleğe taşınamadı")

            // PERF: tam dizin taraması yerine yalnızca farkı işle.
            adjustSize(target.length() - previousLength)
            Log.v(TAG, "Önbelleğe kaydedildi: ${target.name} (${target.length()} bytes)")

            // ERKEN ÇIKIŞ: sayaç artık bellekte olduğu için bütçe kontrolü bedavadır.
            if (totalBytes() > MAX_BYTES) trimToBudget(MAX_BYTES, protectedFile = target)
        } catch (e: Exception) {
            Log.e(TAG, "Önbellek yazma hatası: ${e.javaClass.simpleName}")
            if (temp.exists()) temp.delete()
            throw e
        }
    }

    // ─── Temizleme ────────────────────────────────────────────────────────────

    /**
     * Önbelleği [budget] bayta indirir (bütçenin %85'ine kadar). Silinen bayt miktarını döner.
     *
     * [com.kitsugi.animelist.core.storage.StorageJanitor] arka plan bakımında da çağırır.
     */
    @Synchronized
    fun trimToBudget(budget: Long): Long = trimToBudget(budget, protectedFile = null)

    private fun trimToBudget(budget: Long, protectedFile: File?): Long {
        val files = cacheDir.listFiles()?.filterNot { it.name.endsWith(".tmp") }?.toMutableList() ?: return 0L
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= budget) {
            setSize(totalSize)
            return 0L
        }

        // En eskiden en yeniye sırala
        files.sortBy { it.lastModified() }
        var freed = 0L
        for (file in files) {
            if (totalSize <= budget * 0.85) break   // %85'e düş
            // The newly written page must still exist when the caller marks it Ready.
            if (file == protectedFile) continue
            val size = file.length()
            if (file.delete()) {
                totalSize -= size
                freed += size
                Log.v(TAG, "Önbellek kırpıldı: ${file.name}")
            }
        }
        setSize(totalSize)
        return freed
    }

    /** Tüm önbelleği temizler. */
    @Synchronized
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        setSize(0L)
        Log.i(TAG, "Önbellek tamamen temizlendi")
    }

    /** Önbelleğin o anki boyutunu MB cinsinden döndürür (bellekteki sayaçtan, G/Ç yok). */
    fun currentSizeMb(): Float = totalBytes().toFloat() / (1024f * 1024f)

    // ─── Artımlı boyut sayacı ─────────────────────────────────────────────────

    /** Sayaç henüz yoksa bir kez hesaplanır; sonraki çağrılar G/Ç yapmaz. */
    private fun totalBytes(): Long = synchronized(sizeLock) {
        if (cachedTotalBytes < 0) cachedTotalBytes = computeTotalBytes()
        cachedTotalBytes
    }

    private fun computeTotalBytes(): Long =
        cacheDir.listFiles()?.filterNot { it.name.endsWith(".tmp") }?.sumOf { it.length() } ?: 0L

    /** Sayaç biliniyorsa [delta] kadar kaydır; bilinmiyorsa bir sonraki erişimde hesaplanır. */
    private fun adjustSize(delta: Long) = synchronized(sizeLock) {
        if (cachedTotalBytes >= 0) cachedTotalBytes = (cachedTotalBytes + delta).coerceAtLeast(0L)
    }

    /** Sayaç o an için kesin bilinen bir değere ayarlanır (tarama sonrası). */
    private fun setSize(known: Long) = synchronized(sizeLock) {
        cachedTotalBytes = known.coerceAtLeast(0L)
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    // F8: Dosya uzantısı ekle — Android bazı JPEG dosyalarını uzantısız okuyamayabilir
    private fun cacheFile(imageUrl: String): File {
        val hash = sha1(imageUrl)
        val ext = imageUrl.substringBefore("?").substringAfterLast(".").lowercase()
            .takeIf { it.length in 2..4 && it in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heif", "heic", "jxl") }
            ?: "jpg"
        return File(cacheDir, "$hash.$ext")
    }

    private fun sha1(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
