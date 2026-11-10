package com.kitsugi.animelist.data.manga

/** Çevirmen seçim penceresinin açılma nedeni. */
enum class ChapterChoiceReason {
    /** "Sonraki bölüm" (ileri) — normal akış. */
    Next,

    /** "Önceki bölüm" (geri) akışı. */
    Previous,

    /** Kullanıcı bölüm listesinden birden çok çevirmenli bir bölüme dokundu. */
    Manual,
}

/**
 * Seçim penceresinde listelenen tek bir çevirmen sürümü.
 *
 * @property chapter kaynağın döndürdüğü bölüm nesnesi
 * @property scanlator çevirmen adı (null = kaynak belirtmemiş)
 * @property lastPageIndex kaldığı sayfa (0 tabanlı)
 * @property totalPages bölümdeki toplam sayfa
 * @property isCompleted bu sürüm okunup bitirilmiş mi
 * @property updatedAt ilerlemenin son güncellenme zamanı
 */
data class ChapterVariantInfo(
    val chapter: MangaChapter,
    val scanlator: String?,
    val lastPageIndex: Int = 0,
    val totalPages: Int = 0,
    val isCompleted: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val hasProgress: Boolean
        get() = totalPages > 0 && (isCompleted || lastPageIndex > 0)
}

/**
 * "Aynı bölümün birden çok çevirmeni var" bilgisini UI'a taşıyan istek.
 *
 * Okuyucu, sonraki/önceki bölüme geçerken hedef bölümün birden fazla
 * çevirmeni varsa bu isteği yayınlar; UI ayrıntılı bir seçim penceresi açar.
 */
data class ChapterChoiceRequest(
    val mangaUrl: String,
    val groupKey: String,
    val chapterName: String,
    val variants: List<ChapterVariantInfo>,
    val preferredScanlator: String?,
    val reason: ChapterChoiceReason,
    val isForward: Boolean,
) {
    /** Ön seçili sürüm: hatırlanan çevirmen → ilerlemesi olan → ilk sıradaki. */
    val preselected: ChapterVariantInfo?
        get() {
            val preferred = preferredScanlator?.trim()?.lowercase()
            if (!preferred.isNullOrEmpty()) {
                variants.firstOrNull { it.scanlator?.trim()?.lowercase() == preferred }?.let { return it }
            }
            variants.firstOrNull { it.hasProgress }?.let { return it }
            return variants.firstOrNull()
        }
}
