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
    val source: String = "",
    val alternativeTitles: List<String> = emptyList()
) {
    /**
     * +18 içerik mi? Manga kaynaklarında net bir API bayrağı olmadığı için
     * tür/source ipuçlarıyla kontrol edilir (ecchi hariç tutulur).
     */
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
) {
    /** Opaque source metadata (e.g. Mangitto mangaSlug). Not a signed page URL. */
    var extensionMemo: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
}

/** Search the fully loaded extension list without collapsing translations with equal numbers. */
fun MangaChapter.matchesChapterQuery(query: String): Boolean {
    val term = query.trim()
    return term.isEmpty() || name.contains(term, ignoreCase = true) ||
        scanlator?.contains(term, ignoreCase = true) == true ||
        (chapterNumber >= 0f && chapterNumber.toString().removeSuffix(".0").contains(term, ignoreCase = true))
}

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

    /** Stable source error for page-level actions such as re-authentication. */
    var errorCode: String? by mutableStateOf(null)

    // A URL supplied in the page list cannot necessarily be re-resolved with getImageUrl.
    val sourceProvidedImageUrl: Boolean = !imageUrl.isNullOrBlank()

    /** Chapter identity isolates source responses that depend on referer or chapter context. */
    var cacheContext: String = ""

    /** Source-processed image file; the UI must never refetch imageUrl independently. */
    var localFile: java.io.File? = null
    var aspectRatio: Float? by mutableStateOf(null)
    var imageInfo: MangaImageInfo? by mutableStateOf(null)
    var cachedImageFile: java.io.File? = null

    /** Yerel arşiv dosyası yolu (ZIP/CBZ/RAR içindeki sayfa) — null ise normal HTTP akışı. */
    var archivePath: String? = null

    /**
     * Ready olduğunda diske yazılmış gerçek görsel dosyası.
     * `imageUrl` her zaman mevcut olmayabilir (ZIP/CBZ arşivinde sadece entry adı var);
     * UI bu dosyayı doğrudan kullanmalı, URL'den yeniden türetmemelidir.
     */
    var cachedFile: java.io.File? = null

    /** Durable downloaded page (content/file URI), outside the evictable reader cache. */
    var localUri: String? = null
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

/**
 * Sayfaya uygulanan renk filtresi (Mihon/Aniyomi paritesi).
 *
 * Not: Bu enum'un metin kimlikleri [MangaSettingIds] üzerinden çözülür; eski
 * sürümlerde yazılmış "GrayScale", "Inverted", "Warm", "Cool" gibi değerler
 * geriye dönük uyumlu olarak okunur.
 */
enum class ColorFilterType {
    Normal,
    Warm,
    Cool,
    Grayscale,
    Sepia,
    Invert
}

/**
 * Sayfanın ekrana nasıl oturtulacağı.
 *
 * — FitScreen / FitWidth / FitHeight: kırpma yok, oran korunur
 * — Crop:  ekranı tamamen doldurur, taşan kısım kırpılır (tam ekran hissi)
 * — Original: hiç ölçeklenmez, sayfa gerçek piksel boyutunda çizilir
 */
enum class MangaFitMode {
    FitScreen,
    FitWidth,
    FitHeight,
    Crop,
    Original
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
