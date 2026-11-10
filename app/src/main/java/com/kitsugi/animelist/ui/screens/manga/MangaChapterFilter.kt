package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaChapter

/**
 * Bölüm listesine uygulanan okuma durumu filtresi (araç çubuğundaki "Okunmamış / Tümü"
 * düğmesi ve bölümler başlığındaki tik düğmesi bunu değiştirir).
 *
 * Hedeflenen davranış, eklenti izleme sayfasındaki kaynak filtresiyle aynı mantıktır:
 *  - [All]        → tüm bölümler
 *  - [Unread]     → henüz tamamlanmamış bölümler
 *  - [Read]       → okundu olarak işaretlenmiş bölümler
 *  - [InProgress] → yarım bırakılmış bölümler (ilerleme kaydı var, tamamlanmamış)
 *
 * Kaynak ("MangaciX", "Şımarık Fansub" gibi) bilgisi ayrı bir eksende filtrelenir; çünkü
 * aynı bölüm numarası birden fazla grupta yayınlanabiliyor ve bölüm listesi gerçekten
 * çoğalabiliyor (ekran görüntüsündeki 232 bölüm / çoklu tarayıcı etiketi).
 */
enum class ChapterStatusFilter {
    All,
    Unread,
    Read,
    InProgress;

    /** Bölüm satırı bu filtreye uyuyor mu? */
    fun matches(isCompleted: Boolean, isInProgress: Boolean): Boolean = when (this) {
        All -> true
        Unread -> !isCompleted
        Read -> isCompleted
        InProgress -> isInProgress
    }

    /** Araç çubuğundaki ikinci düğmenin çevrim sırası. */
    fun next(): ChapterStatusFilter = when (this) {
        All -> Unread
        Unread -> Read
        Read -> InProgress
        InProgress -> All
    }
}

/**
 * Tarayıcı (scanlator) adı — çip etiketi olarak kullanılır.
 *
 * Eklentiler bu alanı bazen boş bırakır; o durumda eklentinin kaynak adı kullanılır.
 * Asla boş string dönmeyiz: boş etiketli çipler görünmez ama dokunulabilir olurdu.
 */
fun MangaChapter.scanlatorLabel(fallback: String): String =
    scanlator?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
