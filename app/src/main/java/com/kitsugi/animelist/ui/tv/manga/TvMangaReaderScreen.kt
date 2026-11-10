package com.kitsugi.animelist.ui.tv.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.ui.screens.manga.MangaReaderScreen

/** The shared reader has D-pad focus, viewport scrolling and TV-aware sheet/dialog surfaces. */
@Composable
fun TvMangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) = MangaReaderScreen(chapter, mangaDetails, source, onBack, modifier)
