package com.kitsugi.animelist.data.manga

/**
 * Bölüm adından ("Bölüm 12", "Ch. 12.5", "Chapter 9") veya beyan edilmiş numaradan
 * bölüm numarasını çözer.
 *
 * Kaynaklar chapterNumber alanını -1f döndürdüğünde veya eksik bıraktığında,
 * [ChapterRecognition] motorunu kullanarak bölüm başlığından doğru sayıyı ayıklar.
 */
object MangaChapterNumberResolver {

    fun resolve(
        mangaTitle: String = "",
        chapterName: String,
        declaredNumber: Float = -1f
    ): Float {
        if (declaredNumber >= 0f) return declaredNumber
        val parsed = ChapterRecognition.parseChapterNumber(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            chapterNumber = declaredNumber.toDouble()
        )
        return if (parsed >= 0.0) parsed.toFloat() else -1f
    }
}
