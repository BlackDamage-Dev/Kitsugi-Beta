package com.kitsugi.animelist.data.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/**
 * Bir manga başlığına (title) ait meta bilgileri taşır.
 * Eklenti tarafından döndürülür.
 */
data class MangaDetails(
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val status: MangaStatus = MangaStatus.Unknown,
    val source: String = ""
) {
    /**
     * +18 içerik mi? Manga kaynaklarında net bir API bayrağı olmadığı için
     * tür/source ipuçlarıyla kontrol edilir (ecchi hariç tutulur).
     */
    val isNovelContent: Boolean
        get() {
            val tokens = listOf("novel", "light novel", "web novel", "ranobe", "roman")
            return (genre + source).any { value ->
                val normalized = value.lowercase()
                tokens.any(normalized::contains)
            }
        }

    val isAdultContent: Boolean
        get() {
            val adultTokens = listOf("hentai", "porn", "doujin", "adult", "18+", "xxx")
            return genre.any { g -> adultTokens.any { it in g.lowercase() } } ||
                adultTokens.any { it in source.lowercase() }
        }
}

/**
 * Bir bölümün (chapter) temel bilgilerini taşır.
 */
data class MangaChapter(
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val uploadDate: Long = 0L,
    val mangaUrl: String = ""
)

/**
 * Bir bölümdeki tek bir sayfayı temsil eder.
 */
class MangaPage(
    val index: Int,
    val url: String,
    var imageUrl: String? = null,
    var stream: (() -> java.io.InputStream)? = null,
    status: MangaPageStatus = MangaPageStatus.Queue
) {
    // Pages retain identity while downloads run. StateFlow suppresses equal lists;
    // Compose must observe the status itself, not a shallow copy of that list.
    var status: MangaPageStatus by mutableStateOf(status)

    /** Yerel arşiv dosyası yolu (ZIP/CBZ/RAR içindeki sayfa) — null ise normal HTTP akışı. */
    var archivePath: String? = null

    /** HTML/EPUB/metin bölümleri için ayrıştırılmış paragraflar; görsel sayfalarda null. */
    var textParagraphs: List<String>? = null
}

/**
 * Okuma modunu belirleyen enum.
 * Pager: Yatay/Dikey sayfalama (Manga, Manhua)
 * Webtoon: Sürekli dikey kaydırma (Manhwa, Webtoon)
 */
enum class ReadingMode {
    LeftToRight,   // Soldan Sağa (Manhua)
    RightToLeft,   // Sağdan Sola (Manga - Japonca)
    Vertical,      // Dikey Pager
    Webtoon        // Kesintisiz dikey kaydırma (Manhwa)
}

enum class ColorFilterType {
    Normal,
    Grayscale,
    Sepia,
    Invert
}

enum class MangaFitMode {
    FitScreen,
    FitWidth,
    FitHeight
}

enum class MangaStatus {
    Ongoing,
    Completed,
    Licensed,
    PublicationComplete,
    Cancelled,
    OnHiatus,
    Unknown
}

enum class MangaPageStatus {
    Queue,          // İndirilmeyi bekliyor
    LoadPage,       // Sayfa URL'si çözümleniyor
    DownloadImage,  // Resim indiriliyor
    Ready,          // Hazır, okunabilir
    Error           // Hata oluştu
}
