package com.kitsugi.animelist.data.manga.download

import android.content.Context
import android.net.Uri
import com.kitsugi.animelist.data.manga.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** No extension, HTTP client or cache is required to read a completed chapter. */
class DownloadedMangaSource(context: Context, private val selected: MangaDownload) : MangaSource {
    private val context = context.applicationContext
    private val store = MangaDownloadStore.get(context)
    override val name = selected.sourceName
    override val baseUrl = "offline://manga/${selected.id}"
    override val lang = selected.sourceLang
    override val pkgName = "offline.${selected.sourceKey}"
    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchMangaDetails(mangaUrl: String) = selected.details()
    override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        store.load()
        store.downloads.value.filter {
            it.sourceKey == selected.sourceKey && it.sourceName == selected.sourceName &&
                it.sourceLang == selected.sourceLang && it.mangaUrl == mangaUrl && it.status == MangaDownloadStatus.Complete
        }.sortedByDescending { ChapterRecognition.parseChapterNumber(it.mangaTitle, it.chapterName, it.chapterNumber.toDouble()) }
            .map { it.chapter() }
    }
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        store.load()
        val record = store.downloads.value.firstOrNull {
            it.sourceKey == selected.sourceKey && it.sourceName == selected.sourceName && it.sourceLang == selected.sourceLang &&
                it.mangaUrl == selected.mangaUrl && it.chapterUrl == chapter.url && it.status == MangaDownloadStatus.Complete
        } ?: throw IOException("Bu bölüm cihazda bulunamadı")
        if (!record.hasAllPages || record.pages.any { !MangaPageExport.readable(context, it) }) {
            throw IOException("İndirilen sayfalardan bazıları silinmiş veya erişilemiyor. İndirmeler > Manga alanından bölümü yeniden indirin.")
        }
        record.pages.sortedBy { it.index }.mapIndexed { index, page ->
            MangaPage(index, page.uri, imageUrl = page.uri).apply { localUri = page.uri }
        }
    }
    override suspend fun getImage(page: MangaPage) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(page.localUri ?: page.url))
            ?: throw IOException("Yerel manga sayfası açılamadı")
    }
}
