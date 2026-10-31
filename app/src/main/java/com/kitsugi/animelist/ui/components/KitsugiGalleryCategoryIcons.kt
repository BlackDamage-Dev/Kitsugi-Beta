package com.kitsugi.animelist.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons

/**
 * Galeri kategorisi → HD ikon eşlemesi.
 *
 * Daha önce üç ayrı dosyada emoji olarak kopyalanmıştı (📋 🖼️ 🎨 ✨ 🎭 👤 🌐 🎫 🟩 📁).
 * Tek kaynak burasıdır: galeri diyaloğu, giriş detay galeri sekmesi ve tam ekran
 * galeri aynı ikonu kullanır.
 */
internal fun galleryCategoryIcon(category: GalleryCategory): ImageVector = when (category) {
    GalleryCategory.POSTER    -> Icons.ClipboardList
    GalleryCategory.BACKDROP  -> Icons.Image
    GalleryCategory.LOGO      -> Icons.Palette
    GalleryCategory.CLEARART  -> Icons.Sparkles
    GalleryCategory.CHARACTER -> Icons.Theater
    GalleryCategory.PERSON    -> Icons.UserRound
    GalleryCategory.THUMBNAIL -> Icons.Globe
    GalleryCategory.BANNER    -> Icons.Ticket
    GalleryCategory.SQUARE    -> Icons.Square
    GalleryCategory.OTHER     -> Icons.Folder
}
