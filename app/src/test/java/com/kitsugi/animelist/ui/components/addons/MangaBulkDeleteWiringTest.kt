package com.kitsugi.animelist.ui.components.addons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Manga > Kurulu > Toplu Sil basılmıyor" hatasının regresyon kalkanı.
 *
 * Kök neden iki katmanlıydı:
 *  - buton ya devre dışı (enabled = false) ya da varsayılan no-op callback'liydi,
 *  - görünür listenin tamamı uygulama içi Kotatsu kaynağı olduğunda `deleteExtension`
 *    her kaynak için false dönüyordu, dolayısıyla basınca hiçbir şey olmuyordu.
 *
 * Bu test kaynak dosyaları okuyarak (derleme gerektirmeyen sözleşme kontrolü)
 * butonun gerçek callback'e bağlı kaldığını ve silmenin diski esas aldığını doğrular.
 */
class MangaBulkDeleteWiringTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: throw AssertionError("Kaynak dosya bulunamadı: $relative")
    }

    private fun javaSource(path: String) = source("src/main/java/com/kitsugi/animelist/$path")

    /** Verilen çağrının açılış parantezinden kapanışına kadar olan bloğu döner. */
    private fun callBlock(text: String, marker: String, mustContainAt: Int): String {
        val start = text.lastIndexOf(marker, mustContainAt)
        assertTrue("$marker çağrısı bulunamadı", start >= 0 && mustContainAt - start < 300)
        val open = text.indexOf('(', start)
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw AssertionError("$marker çağrısı kapanmadı")
    }

    @Test
    fun `button is always clickable and dispatches to the real callback`() {
        val tab = javaSource("ui/components/addons/MangaExtensionTab.kt")
        assertTrue("Buton yalnızca state mevcutsa çizilmeli", tab.contains("if (bulkDelete != null)"))
        assertTrue("Buton tıklanınca onay diyaloğu açılmalı", tab.contains("onClick = { showBulkDeleteConfirm = true }"))
        assertTrue("Onay verildiğinde gerçek callback çağrılmalı", tab.contains("bulkDelete.onDeleteAll()"))
        assertTrue("Geri getir aksiyonu da bağlı olmalı", tab.contains("bulkDelete.onRestoreBuiltIns"))

        val clickIndex = tab.indexOf("onClick = { showBulkDeleteConfirm = true }")
        val button = callBlock(tab, "KitsugiButton(", clickIndex)
        assertTrue("Bulunan blok Toplu Sil butonu olmalı", button.contains("showBulkDeleteConfirm"))
        assertFalse(
            "Toplu Sil butonu enabled = false ile dondurulamaz (kullanıcı basamıyor)",
            button.contains("enabled"),
        )
    }

    @Test
    fun `settings dialog and both call sites forward the state instead of a no-op`() {
        val dialog = javaSource("ui/components/KitsugiAddonsSettingsDialog.kt")
        assertTrue(dialog.contains("mangaBulkDelete: MangaBulkDeleteState? = null"))
        assertTrue(dialog.contains("bulkDelete = mangaBulkDelete"))

        val settings = javaSource("AppRootSettingsExtras.kt")
        assertTrue(
            "Ayarlar ekranı onDeleteAll'ı ViewModel'e bağlamalı",
            settings.contains("onDeleteAll = { mangaViewModel.deleteAllMangaExtensions() }"),
        )
        val browse = javaSource("ui/screens/manga/MangaBrowseScreen.kt")
        assertTrue(
            "Manga taraflı diyalog da aynı callback'i almalı",
            browse.contains("onDeleteAll = { mangaViewModel.deleteAllMangaExtensions() }"),
        )
    }

    @Test
    fun `delete-all uses disk truth and hides undeletable built-in sources`() {
        val repository = javaSource("data/manga/MangaSourceRepository.kt")
        assertTrue(repository.contains("MangaExtensionLoader.getInstalledExtensionFiles(context)"))
        assertTrue(repository.contains("setHealthStatusForAll(notYetDisabled, SourceHealthStatus.Disabled)"))
        assertTrue(repository.contains("fun restoreBulkDeletedBuiltIns(): Int"))

        val loader = javaSource("data/manga/MangaExtensionLoader.kt")
        assertTrue(loader.contains("fun getInstalledExtensionFiles(context: Context): List<File>"))
        assertTrue(loader.contains("fun getBuiltInSources(): List<MangaSource>"))

        val viewModel = javaSource("ui/app/MangaViewModel.kt")
        assertTrue("Hata durumunda dahi busy bayrağı sıfırlanmalı", viewModel.contains("finally {"))
        assertTrue(viewModel.contains("mangaIsBulkDeleting = false"))
    }

    @Test
    fun `both locales contain every bulk-delete string`() {
        val keys = listOf(
            "manga_bulk_delete_action",
            "manga_bulk_delete_hint",
            "manga_bulk_delete_progress",
            "manga_bulk_restore_action",
            "manga_bulk_delete_dialog_title",
            "manga_bulk_delete_dialog_message",
            "manga_bulk_delete_builtin_note",
            "manga_bulk_delete_result",
            "manga_bulk_delete_partial",
            "manga_bulk_delete_empty",
            "manga_bulk_delete_failed",
            "manga_bulk_restore_result",
        )
        val tr = source("src/main/res/values/strings.xml")
        val en = source("src/main/res/values-en/strings.xml")
        keys.forEach { key ->
            val needle = "name=\"$key\""
            assertTrue("TR eksik: $key", tr.contains(needle))
            assertTrue("EN eksik: $key", en.contains(needle))
        }
    }
}
