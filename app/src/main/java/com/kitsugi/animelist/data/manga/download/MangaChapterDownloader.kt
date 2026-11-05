package com.kitsugi.animelist.data.manga.download

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.loader.MangaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manga bölümlerini **çevrimdışı okunabilir CBZ arşivi** olarak indirir.
 *
 * ## Neden CBZ?
 * Tachiyomi/Mihon ve TachiyomiSY'nin kullandığı biçim budur: bir klasör dolusu JPG yerine
 * tek dosya. Avantajları:
 *  - Sayfa sırası dosya adına değil arşiv girdi sırasına bağlı → isim çakışması yok.
 *  - Yüzlerce küçük dosyanın dosya sistemi yükü yok.
 *  - [com.kitsugi.animelist.data.manga.loader.ArchivePageLoader] zaten CBZ okuyabiliyor;
 *    okuyucu, indirilen bölümü kaynaktan bağımsız açıyor.
 *
 * ## Neden ayrı bir WorkManager işi yok?
 * İndirme, okuyucu ile aynı süreçte ve aynı [MangaCache]'i kullanarak yapılır. Kullanıcı
 * uygulamadan çıkarsa indirme durur; Mihon'da da varsayılan davranış "ön planda indir"
 * şeklindedir. Bu, pil ve veri tüketimini öngörülebilir tutar.
 *
 * ## Nereye yazılır?
 * `filesDir/manga_downloads/<source>/<mangaSlug>/<chapter>.cbz`
 * Kaynak ve manga adları dosya sistemi için güvenli hâle getirilir (bkz. [safeName]).
 */
class MangaChapterDownloader(private val context: Context) {

    companion object {
        private const val TAG = "MangaDownloader"
        private const val ROOT_DIR = "manga_downloads"
        /** Aynı anda indirilen sayfa sayısı — sunucuyu boğmamak için 3. */
        private const val PARALLEL_PAGES = 3
        private const val PAGE_TIMEOUT_MS = 30_000L
        /** Tek bölüm için üst sınır; bozuk bir kaynakta sonsuz döngüyü engeller. */
        private const val MAX_PAGES = 400
    }

    private val root: File = File(context.filesDir, ROOT_DIR).also { if (!it.exists()) it.mkdirs() }
    private val cache = MangaCache(context)

    // ─── Durum takibi ────────────────────────────────────────────────────────

    data class DownloadState(
        val /** İndirilen bölümün URL'si. */ chapterUrl: String,
        val /** 0f..1f arası ilerleme. */ progress: Float,
        val isRunning: Boolean,
        val error: String? = null
    )

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    /**
     * Bölüm URL'si → indirme durumu. Detay ekranı bu haritayı dinleyerek satırdaki
     * "indiriliyor / indirildi" göstergesini çizer.
     */
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    // ─── Yol yardımcıları ────────────────────────────────────────────────────

    /** Dosya sistemi için güvenli isim üretir (Türkçe karakterler korunur). */
    fun safeName(raw: String, fallback: String = "manga"): String {
        val cleaned = raw.trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim('.', ' ')
            .take(80)
        return cleaned.ifBlank { fallback }
    }

    /** Manga klasör adı — başlık değişse bile aynı seriye düşmek için ayrı yardımcı. */
    fun mangaSlug(mangaTitle: String, mangaUrl: String): String =
        safeName(mangaTitle) + "_" + mangaUrl.hashCode().toUInt().toString(16)

    private fun mangaDir(source: MangaSource, mangaTitle: String, mangaUrl: String): File {
        // Aynı başlıklı iki seri çakışmasın diye URL'nin kısa karma değeri klasör adına eklenir.
        return File(File(root, safeName(source.name, "source")), mangaSlug(mangaTitle, mangaUrl)).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun chapterFile(source: MangaSource, mangaTitle: String, mangaUrl: String, chapter: MangaChapter, index: Int): File {
        val label = chapterLabel(chapter, index)
        return File(mangaDir(source, mangaTitle, mangaUrl), "$label.cbz")
    }

    /** Bölüm dosyası etiketi: numaralı serilerde "0001", numarasızsa sıra numarası. */
    private fun chapterLabel(chapter: MangaChapter, index: Int): String =
        when {
            chapter.chapterNumber >= 0f -> {
                val whole = chapter.chapterNumber.toInt()
                val decimal = chapter.chapterNumber - whole
                if (decimal < 0.001f) String.format(java.util.Locale.US, "%04d", whole)
                else String.format(java.util.Locale.US, "%04d.%02d", whole, (decimal * 100).toInt())
            }
            else -> String.format(java.util.Locale.US, "x%04d_%s", index, safeName(chapter.name, "bolum").take(40))
        }

    // ─── Sorgular ────────────────────────────────────────────────────────────

    /** Bölüm daha önce indirildi mi? (Detay ekranındaki indirildi işareti.) */
    fun isDownloaded(source: MangaSource, mangaTitle: String, mangaUrl: String, chapter: MangaChapter, index: Int = 0): Boolean {
        val file = chapterFile(source, mangaTitle, mangaUrl, chapter, index)
        if (file.isFile && file.length() > 1024L) return true
        // Numarasız bölümlerde dosya adı sıraya bağlı olduğu için klasörde arama yapılır.
        if (chapter.chapterNumber < 0f) {
            val dir = mangaDir(source, mangaTitle, mangaUrl)
            return dir.listFiles()?.any { it.isFile && it.name.contains(safeName(chapter.name, "bolum").take(20)) } == true
        }
        return false
    }

    /** Bir manga için indirilmiş bölümlerin **sayısı** (kitaplık göstergesi). */
    fun downloadedCount(source: MangaSource, mangaTitle: String, mangaUrl: String): Int =
        mangaDir(source, mangaTitle, mangaUrl)
            .listFiles()
            ?.count { it.isFile && it.name.endsWith(".cbz") } ?: 0

    /** Bir bölümün indirilmiş dosyası (okuyucu doğrudan bunu açar); yoksa null. */
    fun existingArchive(source: MangaSource, mangaTitle: String, mangaUrl: String, chapter: MangaChapter, index: Int = 0): File? {
        val file = chapterFile(source, mangaTitle, mangaUrl, chapter, index)
        return file.takeIf { it.isFile && it.length() > 1024L }
    }

    /** İndirilen tüm bölümlerin toplam boyutu (MB) — depolama ayarlarında gösterilir. */
    fun totalSizeMb(): Float {
        val bytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return bytes.toFloat() / (1024f * 1024f)
    }

    /** Bir mangaya ait tüm indirmeleri siler. */
    fun deleteForManga(source: MangaSource, mangaTitle: String, mangaUrl: String): Int {
        val dir = mangaDir(source, mangaTitle, mangaUrl)
        val files = dir.listFiles() ?: return 0
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        dir.delete()
        return deleted
    }

    // ─── İndirme ─────────────────────────────────────────────────────────────

    /**
     * Tek bir bölümü CBZ olarak indirir.
     *
     * Sayfalar [PARALLEL_PAGES] eşzamanlılıkla çekilir, her sayfa indirildiği anda arşive
     * yazılır (bellekte biriktirilmez → 300 sayfalık bölümde OOM riski yok). Başarısız
     * olursa yarım dosya **silinir**; yarım CBZ okuyucuda bozuk sayfa gösterirdi.
     *
     * @return başarılıysa oluşturulan dosya, değilse null.
     */
    suspend fun download(
        source: MangaSource,
        mangaTitle: String,
        mangaUrl: String,
        chapter: MangaChapter,
        index: Int = 0
    ): File? = withTimeout(10 * 60_000L) {
        val target = chapterFile(source, mangaTitle, mangaUrl, chapter, index)
        _states.update {
            it + (chapter.url to DownloadState(chapter.url, 0f, isRunning = true))
        }
        val temp = File.createTempFile("dl_", ".cbz.part", target.parentFile)
        try {
            val pages = withTimeout(PAGE_TIMEOUT_MS) { source.fetchPageList(chapter) }
            if (pages.isEmpty()) throw java.io.IOException("Bölümde sayfa bulunamadı")
            val limited = pages.take(MAX_PAGES)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val total = limited.size

            // Sayfaları sırayla arşive yazmak için önce hepsini paralel çekip bellekte
            // tutmak gerekirdi; onun yerine her sayfa indirilir indirilmez diske geçici
            // dosya olarak yazılır ve sonra tek geçişte arşive eklenir.
            val tempPages = ArrayList<File>(total)
            val semaphore = Semaphore(PARALLEL_PAGES)
            coroutineScope {
                limited.forEachIndexed { pageIndex, page ->
                    launch {
                        semaphore.withPermit {
                            val bytes = fetchPageBytes(source, page)
                            if (bytes != null) {
                                val f = File(temp.parentFile, "${temp.name}.$pageIndex")
                                FileOutputStream(f).use { it.write(bytes) }
                                synchronized(tempPages) { tempPages.add(f) }
                            }
                            val finished = done.incrementAndGet()
                            _states.update { map ->
                                val st = map[chapter.url] ?: return@update map
                                map + (chapter.url to st.copy(progress = (finished.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
            }

            if (tempPages.isEmpty()) throw java.io.IOException("Hiçbir sayfa indirilemedi")

            // Arşive sıralı yaz: sayfa sırası okuyucuda bozulmasın.
            val ordered = tempPages.sortedBy { it.name.substringAfterLast('.').toIntOrNull() ?: 0 }
            ZipOutputStream(FileOutputStream(temp)).use { zip ->
                ordered.forEachIndexed { i, pageFile ->
                    val entryName = String.format(java.util.Locale.US, "%04d.jpg", i)
                    zip.putNextEntry(ZipEntry(entryName))
                    pageFile.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            ordered.forEach { it.delete() }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                // renameTo farklı dosya sistemlerinde başarısız olabilir; kopyala.
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            _states.update { it - chapter.url }
            Log.d(TAG, "İndirildi: ${target.name} (${total} sayfa)")
            target
        } catch (e: Exception) {
            runCatching { temp.delete() }
            Log.e(TAG, "İndirme hatası: ${e.message}")
            _states.update {
                it + (chapter.url to DownloadState(chapter.url, 0f, isRunning = false, error = e.message))
            }
            null
        }
    }

    /**
     * Sayfa görselini bayt olarak çeker.
     *
     * Önce disk önbelleğine bakar: kullanıcı bölümü okuduktan sonra "indir" derse ağ
     * trafiği **hiç** oluşmaz (Mihon'daki cache-first davranışı).
     */
    private suspend fun fetchPageBytes(source: MangaSource, page: MangaPage): ByteArray? =
        withTimeout(PAGE_TIMEOUT_MS) {
            runCatching {
                val url = page.imageUrl?.takeIf { it.isNotBlank() }
                    ?: source.fetchImageUrl(page)?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                if (cache.isImageInCache(url)) {
                    return@runCatching cache.getImageFile(url).readBytes()
                }
                val bytes = source.getImage(page).use { it.readBytes() }
                if (bytes.isEmpty()) null else bytes
            }.getOrNull()
        }
}
