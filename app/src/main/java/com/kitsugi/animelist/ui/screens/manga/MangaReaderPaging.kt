package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Okuyucu sayfa gezinme yardımcıları.
 *
 * Çift sayfa modunda pager konumu ile gerçek sayfa indeksi farklıdır. Bu eşleme
 * **tek yerde** (burada) tanımlıdır; ekranlar kendi aritmetiğini yazmaz.
 */
object MangaReaderPaging {

    fun isDualPage(options: MangaReaderOptions, isLandscape: Boolean = false): Boolean =
        options.dualPageActive(isLandscape)

    /**
     * Sayfaları pager "yuvalarına" böler — **tek doğruluk kaynağı**.
     *
     * Çift sayfa (yayılım) modunda komşu sayfaları körlemesine eşleştirmek
     * yanlıştır: kapak, tek sayfalık renk sayfası ya da **zaten yayılım olan
     * geniş sayfa** tek başına durmalıdır. Aksi hâlde alakasız görseller yan
     * yana getirilir ("Sayfaları birleştir" şikâyeti).
     *
     * Kurallar:
     * - Çift sayfa kapalı → her sayfa kendi yuvasında.
     * - [MangaReaderOptions.firstPageSingle] → 0. sayfa (kapak) tek başına.
     * - [spreadPages] içindeki sayfa (genişlik/yükseklik > eşik, yani zaten
     *   iki sayfalık yayılım) tek başına; yanına sayfa eklenmez.
     * - Kalan sayfalar sırayla ikişerli eşleşir.
     *
     * Böylece yuvanın içeriği her zaman "okunabilir" bir bütündür.
     */
    fun groups(
        pageCount: Int,
        options: MangaReaderOptions,
        isLandscape: Boolean = false,
        spreadPages: Set<Int> = emptySet()
    ): List<List<Int>> {
        if (pageCount <= 0) return emptyList()
        if (!isDualPage(options, isLandscape)) return (0 until pageCount).map { listOf(it) }

        val result = ArrayList<List<Int>>((pageCount + 1) / 2)
        var i = 0
        while (i < pageCount) {
            val alone = (i == 0 && options.firstPageSingle) || i in spreadPages
            if (alone) {
                result += listOf(i)
                i += 1
            } else if (i + 1 < pageCount && (i + 1) !in spreadPages) {
                result += listOf(i, i + 1)
                i += 2
            } else {
                result += listOf(i)
                i += 1
            }
        }
        return result
    }

    /** Pager öğe sayısı. */
    fun pagerCount(
        pageCount: Int,
        options: MangaReaderOptions,
        isLandscape: Boolean = false,
        spreadPages: Set<Int> = emptySet()
    ): Int = groups(pageCount, options, isLandscape, spreadPages).size

    /** Verilen pager yuvasının gösterdiği sayfa indeksleri (1 veya 2). */
    fun pageIndicesFor(
        pagerIndex: Int,
        pageCount: Int,
        options: MangaReaderOptions,
        isLandscape: Boolean = false,
        spreadPages: Set<Int> = emptySet()
    ): List<Int> = groups(pageCount, options, isLandscape, spreadPages).getOrElse(pagerIndex) { emptyList() }

    /** Pager yuvası → ekranda gösterilen **ilk** sayfanın indeksi. */
    fun pageIndexFor(
        pagerIndex: Int,
        pageCount: Int,
        options: MangaReaderOptions,
        isLandscape: Boolean = false,
        spreadPages: Set<Int> = emptySet()
    ): Int = pageIndicesFor(pagerIndex, pageCount, options, isLandscape, spreadPages)
        .firstOrNull() ?: pagerIndex

    /** Sayfa indeksi → onu içeren pager yuvası. */
    fun pagerIndexFor(
        pageIndex: Int,
        pageCount: Int,
        options: MangaReaderOptions,
        isLandscape: Boolean = false,
        spreadPages: Set<Int> = emptySet()
    ): Int {
        val all = groups(pageCount, options, isLandscape, spreadPages)
        val found = all.indexOfFirst { pageIndex in it }
        return if (found >= 0) found else pageIndex
    }

    /**
     * Bir sayfanın "zaten yayılım" (iki sayfalık) olup olmadığı.
     *
     * Genişlik/yükseklik oranı eşiği aşarsa sayfa kendi başına bir yayılımdır;
     * yanına ikinci sayfa konmaz. Eşik [MangaReaderOptions.widePageThreshold]
     * ile aynıdır (tek ayar, ikinci bir eşik icat edilmez).
     */
    fun isSpread(aspectRatio: Float, options: MangaReaderOptions): Boolean =
        aspectRatio > 0f && aspectRatio >= 1f / options.widePageThreshold.coerceAtLeast(0.1f)
}

/**
 * Mihon'daki uzun dokunma eylem menüsünün ("Save / Share / Copy link") Kitsugi
 * uyarlaması. Sayfa görseli `filesDir/manga_cache` altına indirildiği için
 * paylaşım FileProvider üzerinden yapılır.
 */
object MangaPageActions {

    /** Sayfayı paylaş: indirilmiş dosya varsa onu, yoksa görsel bağlantısını paylaşır. */
    suspend fun share(context: Context, page: MangaPage) {
        val imageUrl = page.imageUrl ?: page.url
        val file = page.archivePath?.let { File(it) }?.takeIf { it.exists() }
        val intent = if (file != null && file.exists()) {
            val uri = runCatching {
                FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", file)
            }.getOrNull()
            if (uri != null) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                shareLink(imageUrl)
            }
        } else {
            shareLink(imageUrl)
        }
        launchChooser(context, intent, "Sayfayı paylaş")
    }

    /** Sayfa görseli bağlantısını paylaşır. */
    fun sharePageLink(context: Context, page: MangaPage) {
        launchChooser(context, shareLink(page.imageUrl ?: page.url), "Bağlantıyı paylaş")
    }

    /**
     * Sayfayı kullanıcının seçtiği yere kaydeder.
     */
    suspend fun saveTo(
        context: Context,
        page: MangaPage,
        destinationBaseName: String
    ): Boolean {
        val file = page.archivePath?.let { File(it) }?.takeIf { it.exists() }
        if (file == null || !file.exists()) {
            val imageUrl = page.imageUrl ?: page.url
            if (imageUrl.isNotBlank()) {
                launchChooser(context, shareLink(imageUrl), "Görseli Kaydet / Paylaş")
                return true
            }
            Toast.makeText(context, "Sayfa resmi henüz hazır değil", Toast.LENGTH_SHORT).show()
            return false
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "saved_manga")
                if (!dir.exists()) dir.mkdirs()
                val ext = file.extension.ifBlank { "jpg" }
                val safeName = destinationBaseName
                    .replace(Regex("[^A-Za-z0-9._\\-]+"), "_")
                    .take(60)
                    .ifBlank { "page_${page.index + 1}" }
                val target = File(dir, "$safeName.$ext")
                file.copyTo(target, overwrite = true)
                Toast.makeText(context, "Kaydedildi: ${target.name}", Toast.LENGTH_SHORT).show()
                true
            }.getOrElse {
                Toast.makeText(context, "Kaydedilemedi: ${it.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun shareLink(url: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        runCatching {
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Paylaşım başlatılamadı", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Bölüm içi yer imi (Kotatsu "bookmarks" paritesi).
 * Sayfa indeksi bazlıdır; bölüm değişse de ilgili bölüme ait kalır.
 */
data class MangaReaderBookmark(
    val chapterUrl: String,
    val chapterName: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
)
