package com.kitsugi.animelist.ui.components.addons

/**
 * Manga > Kurulu sekmesindeki "Toplu Sil" aksiyonunun tek taşıyıcısı.
 *
 * Diyalog zaten onlarca parametre aldığı için (DEX yazmaç sınırı riski) yeni
 * alanlar tek bir nesnede toplandı. Nesne `null` ise buton **gösterilmez**:
 * callback'siz (ölü) bir "Toplu Sil" butonunun ekranda kalması, kullanıcının
 * "basılmıyor" şikâyetinin tam nedeniydi.
 */
data class MangaBulkDeleteState(
    val isDeleting: Boolean = false,
    val hiddenBuiltInCount: Int = 0,
    val onDeleteAll: () -> Unit,
    val onRestoreBuiltIns: () -> Unit = {},
)
