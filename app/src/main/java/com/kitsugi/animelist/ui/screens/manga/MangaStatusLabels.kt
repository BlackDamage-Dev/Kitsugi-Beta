package com.kitsugi.animelist.ui.screens.manga

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaStatus

/**
 * Manga durum etiketleri.
 *
 * Önceden ekranların içinde sabit Türkçe metin olarak duruyordu; uygulama dili
 * değiştirildiğinde (Ayarlar → Dil) etiketler Türkçe kalıyordu. Artık tek bir
 * kaynak üzerinden geliyor, böylece telefon ve TV ekranları aynı dili konuşuyor.
 */
@StringRes
fun MangaStatus.labelRes(): Int = when (this) {
    MangaStatus.Ongoing -> R.string.manga_status_ongoing
    MangaStatus.Completed -> R.string.manga_status_completed
    MangaStatus.Licensed -> R.string.manga_status_licensed
    MangaStatus.PublicationComplete -> R.string.manga_status_publication_complete
    MangaStatus.Cancelled -> R.string.manga_status_cancelled
    MangaStatus.OnHiatus -> R.string.manga_status_hiatus
    MangaStatus.Unknown -> R.string.manga_status_unknown
}
