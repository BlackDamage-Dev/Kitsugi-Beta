package com.kitsugi.animelist.data.manga

import eu.kanade.tachiyomi.source.model.SChapter

/** Reconstructing only url/name discards metadata required by some modern KeiSource plugins. */
internal fun MangaChapter.toExtensionChapter(): SChapter = SChapter.create().also {
    it.url = url
    it.name = name
    it.chapter_number = chapterNumber
    it.scanlator = scanlator
    it.date_upload = uploadDate
    it.memo = extensionMemo
}
