package com.kitsugi.animelist.data.manga

import androidx.compose.ui.graphics.luminance
import com.kitsugi.animelist.ui.screens.manga.MangaReaderPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Okuyucu ayarlarının tek doğruluk kaynağı testleri.
 *
 * Bu testler özellikle **geriye dönük uyum** için kritik: eski sürümlerden gelen
 * kimliklerin (`WebtoonZoom`, `GrayScale`, `Warm`, `Inverted`...) doğru çözülmesi
 * gerekiyor. Aksi halde kullanıcının kayıtlı ayarı sessizce "Normal"a düşer.
 */
class MangaReaderOptionsTest {

    // ─── Kimlik çözümleme ────────────────────────────────────────────────────

    @Test fun legacyReadingModeKeysResolve() {
        assertEquals(ReadingMode.Webtoon, ReadingMode.fromKey("WebtoonZoom"))
        assertEquals(ReadingMode.Webtoon, ReadingMode.fromKey("Webtoon"))
        assertEquals(ReadingMode.LeftToRight, ReadingMode.fromKey("LeftToRight"))
        assertEquals(ReadingMode.Vertical, ReadingMode.fromKey("Dikey"))
        assertEquals(ReadingMode.RightToLeft, ReadingMode.fromKey(null))
        assertEquals(ReadingMode.RightToLeft, ReadingMode.fromKey("bilinmeyen"))
    }

    @Test fun legacyColorFilterKeysResolve() {
        assertEquals(ColorFilterType.Grayscale, ColorFilterType.fromKey("GrayScale"))
        assertEquals(ColorFilterType.Grayscale, ColorFilterType.fromKey("Grayscale"))
        assertEquals(ColorFilterType.Invert, ColorFilterType.fromKey("Inverted"))
        assertEquals(ColorFilterType.Warm, ColorFilterType.fromKey("Warm"))
        assertEquals(ColorFilterType.Cool, ColorFilterType.fromKey("Cool"))
        assertEquals(ColorFilterType.Normal, ColorFilterType.fromKey("Normal"))
    }

    @Test fun legacyFitModeKeysResolve() {
        assertEquals(MangaFitMode.FitWidth, MangaFitMode.fromKey("FitWidth"))
        assertEquals(MangaFitMode.Original, MangaFitMode.fromKey("OriginalSize"))
        assertEquals(MangaFitMode.Stretch, MangaFitMode.fromKey("Fill"))
        assertEquals(MangaFitMode.FitScreen, MangaFitMode.fromKey(null))
    }

    @Test fun everyColorFilterIsInCycleOrder() {
        ColorFilterType.entries.forEach { filter ->
            assertTrue("${filter.name} döngüde olmalı", ColorFilterType.cycleOrder.contains(filter))
        }
        ColorFilterType.cycleOrder.forEach { filter ->
            assertEquals(filter, ColorFilterType.fromKey(filter.key))
        }
    }

    @Test fun everyFitModeIsInCycleOrder() {
        MangaFitMode.entries.forEach { mode ->
            assertTrue("${mode.name} döngüde olmalı", MangaFitMode.cycleOrder.contains(mode))
            assertEquals(mode, MangaFitMode.fromKey(mode.key))
        }
    }

    // ─── JSON gidiş-dönüş ────────────────────────────────────────────────────

    @Test fun jsonRoundTripPreservesEverything() {
        val options = MangaReaderOptions(
            readingMode = ReadingMode.Webtoon,
            colorFilter = ColorFilterType.HighContrast,
            fitMode = MangaFitMode.FitHeight,
            brightness = 0.6f,
            contrast = 1.4f,
            saturation = 1.6f,
            blueLight = 0.35f,
            background = MangaPageBackground.Smart,
            cropProfile = MangaCropProfile.White,
            sidePadding = 24,
            pageGap = 12,
            showPageNumber = false,
            keepScreenOn = false,
            immersive = false,
            tapZone = MangaTapZone.Inverted,
            volumeKeys = true,
            invertVolumeKeys = true,
            webtoonZoom = false,
            dualPage = true,
            autoWebtoon = false,
            autoNextChapterSeconds = 15,
            chapterTransitionCard = false,
            autoScrollSpeed = 240,
            einkFlash = true,
            longTapActions = false
        )
        val restored = MangaReaderOptions.fromJson(options.toJson())
        assertEquals(options, restored)
    }

    @Test fun missingJsonFallsBackToLegacyValues() {
        // Eski sürümde yalnızca tekil anahtarlar vardı: JSON yok.
        val legacy = MangaReaderOptions(
            readingMode = ReadingMode.LeftToRight,
            colorFilter = ColorFilterType.Sepia,
            fitMode = MangaFitMode.FitWidth,
            brightness = 0.8f
        )
        assertEquals(legacy, MangaReaderOptions.fromJson(null, legacy))
        assertEquals(legacy, MangaReaderOptions.fromJson("", legacy))
        assertEquals(legacy, MangaReaderOptions.fromJson("bu-json-degil", legacy))
    }

    @Test fun partialJsonKeepsFallbackForUnspecifiedFields() {
        val legacy = MangaReaderOptions(fitMode = MangaFitMode.FitHeight, brightness = 0.5f)
        val json = """{"readingMode":"Vertical"}"""
        val restored = MangaReaderOptions.fromJson(json, legacy)
        assertEquals(ReadingMode.Vertical, restored.readingMode)
        // Belirtilmeyen alanlar fallback'ten gelir → kullanıcı ayarı kaybolmaz.
        assertEquals(MangaFitMode.FitHeight, restored.fitMode)
        assertEquals(0.5f, restored.brightness, 0.001f)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val json = """{"brightness":5,"contrast":99,"saturation":-3,"blueLight":4,"sidePadding":900,"pageGap":-10,"autoScrollSpeed":9999}"""
        val restored = MangaReaderOptions.fromJson(json)
        assertEquals(1f, restored.brightness, 0.001f)
        assertEquals(2f, restored.contrast, 0.001f)
        assertEquals(0f, restored.saturation, 0.001f)
        assertEquals(1f, restored.blueLight, 0.001f)
        assertEquals(80, restored.sidePadding)
        assertEquals(0, restored.pageGap)
        assertEquals(600, restored.autoScrollSpeed)
    }

    // ─── Görüntü filtresi ────────────────────────────────────────────────────

    @Test fun neutralSettingsAreReportedAsIdentity() {
        assertTrue(MangaImageFilters.isIdentity(ColorFilterType.Normal))
        assertTrue(MangaImageFilters.isIdentity(ColorFilterType.Normal, 1f, 1f, 0f))
        // Küçük yuvarlama farkları da "değişiklik yok" sayılır (gereksiz GPU işi yok).
        assertTrue(MangaImageFilters.isIdentity(ColorFilterType.Normal, 1.004f, 0.997f, 0.005f))
    }

    @Test fun nonNeutralSettingsAreNotIdentity() {
        assertFalse(MangaImageFilters.isIdentity(ColorFilterType.Grayscale))
        assertFalse(MangaImageFilters.isIdentity(ColorFilterType.Normal, contrast = 1.3f))
        assertFalse(MangaImageFilters.isIdentity(ColorFilterType.Normal, saturation = 1.4f))
        assertFalse(MangaImageFilters.isIdentity(ColorFilterType.Normal, blueLight = 0.4f))
    }

    @Test fun matricesAreWellFormed() {
        ColorFilterType.entries.forEach { filter ->
            assertEquals("${filter.name} 4x5 matris olmalı", 20, MangaImageFilters.filterMatrix(filter).size)
        }
        assertEquals(20, MangaImageFilters.pipeline(1.2f, 1.4f, 0.3f).size)
    }

    @Test fun matrixMultiplicationIsIdentityForIdentityMatrices() {
        val identity = MangaImageFilters.filterMatrix(ColorFilterType.Normal)
        val result = identity * identity
        for (i in 0 until 20) {
            val expected = if (i % 5 == i / 5) 1f else 0f
            assertEquals("index $i", expected, result[i], 0.0001f)
        }
    }

    // ─── Sayfa geometrisi / akıllı zemin ─────────────────────────────────────

    @Test fun smartBackgroundStaysDarkForDarkPages() {
        val dark = smartBackground(0.1f)
        val light = smartBackground(0.9f)
        assertTrue("koyu sayfada açık zemin", light.luminance() > dark.luminance())
    }

    @Test fun everyBackgroundKeyRoundTrips() {
        MangaPageBackground.cycleOrder.forEach { bg ->
            assertEquals(bg, MangaPageBackground.fromKey(bg.key))
        }
        assertEquals(MangaPageBackground.Smart, MangaPageBackground.fromKey("Automatic"))
    }

    // ─── 2. tur enum'ları (animasyon, bar gizleme, kırpma profili) ───────────

    @Test fun pageAnimationKeysResolve() {
        MangaPageAnimation.entries.forEach { assertEquals(it, MangaPageAnimation.fromKey(it.key)) }
        // Kotatsu `ReaderAnimation` eski değerleri
        assertEquals(MangaPageAnimation.None, MangaPageAnimation.fromKey("NONE"))
        assertEquals(MangaPageAnimation.Default, MangaPageAnimation.fromKey("DEFAULT"))
        assertEquals(MangaPageAnimation.Fast, MangaPageAnimation.fromKey("ADVANCED"))
        // bilinmeyen → güvenli varsayılan
        assertEquals(MangaPageAnimation.Default, MangaPageAnimation.fromKey(null))
        assertEquals(MangaPageAnimation.Default, MangaPageAnimation.fromKey("saçma"))
    }

    @Test fun pageAnimationCyclesThroughEveryValue() {
        var current = MangaPageAnimation.Default
        val seen = mutableSetOf(current)
        repeat(MangaPageAnimation.cycleOrder.size - 1) {
            val idx = MangaPageAnimation.cycleOrder.indexOf(current)
            current = MangaPageAnimation.cycleOrder[(idx + 1) % MangaPageAnimation.cycleOrder.size]
            seen += current
        }
        assertEquals(MangaPageAnimation.entries.toSet(), seen)
    }

    @Test fun scrollHideSensitivityKeysResolve() {
        MangaScrollHideSensitivity.entries.forEach {
            assertEquals(it, MangaScrollHideSensitivity.fromKey(it.key))
        }
        assertEquals(MangaScrollHideSensitivity.Off, MangaScrollHideSensitivity.fromKey("false"))
        assertEquals(MangaScrollHideSensitivity.Off, MangaScrollHideSensitivity.fromKey(null))
    }

    @Test fun cropProfileKeysResolve() {
        MangaCropProfile.entries.forEach { assertEquals(it, MangaCropProfile.fromKey(it.key)) }
        // eski boolean ayar `cropBorders=true` → beyaz kenar
        assertEquals(MangaCropProfile.White, MangaCropProfile.fromKey("true"))
        assertEquals(MangaCropProfile.White, MangaCropProfile.fromKey("On"))
        assertEquals(MangaCropProfile.Off, MangaCropProfile.fromKey(null))
    }

    @Test fun cropProfileSurvivesJsonRoundTrip() {
        val options = MangaReaderOptions(
            cropProfile = MangaCropProfile.Black,
            cropTolerance = 27,
            rotateWidePages = true,
            widePageThreshold = 1.75f,
            pageOpacity = 0.6f,
            bookEffect = true,
            highResColors = true,
            pageAnimation = MangaPageAnimation.Fast,
            autoHideMenuOnScroll = MangaScrollHideSensitivity.High,
            infoBar = true,
            zoomButtons = true,
            showSlider = false,
            showChapterButtons = false,
            showPagesSheet = true,
            showScrollTimer = false,
            showBookmarkButton = true,
            dualPageOnLandscape = true,
            perMangaMode = false,
        )
        val restored = MangaReaderOptions.fromJson(options.toJson(), fallback = MangaReaderOptions())
        assertEquals(options.cropProfile, restored.cropProfile)
        assertEquals(27, restored.cropTolerance)
        assertTrue(restored.rotateWidePages)
        assertEquals(1.75f, restored.widePageThreshold, 0.0001f)
        assertEquals(0.6f, restored.pageOpacity, 0.0001f)
        assertTrue(restored.bookEffect)
        assertTrue(restored.highResColors)
        assertEquals(MangaPageAnimation.Fast, restored.pageAnimation)
        assertEquals(MangaScrollHideSensitivity.High, restored.autoHideMenuOnScroll)
        assertTrue(restored.infoBar)
        assertTrue(restored.zoomButtons)
        assertFalse(restored.showSlider)
        assertFalse(restored.showChapterButtons)
        assertTrue(restored.showPagesSheet)
        assertFalse(restored.showScrollTimer)
        assertTrue(restored.showBookmarkButton)
        assertTrue(restored.dualPageOnLandscape)
        assertFalse(restored.perMangaMode)
    }

    @Test fun legacyCropBordersJsonUpgradesToWhiteProfile() {
        val legacy = """{"cropBorders":true}"""
        val restored = MangaReaderOptions.fromJson(legacy, fallback = MangaReaderOptions())
        assertEquals(MangaCropProfile.White, restored.cropProfile)
        assertTrue(restored.cropEnabled)
    }

    @Test fun pageOpacityIsClamped() {
        val tooLow = MangaReaderOptions.fromJson("""{"pageOpacity":0.01}""", fallback = MangaReaderOptions())
        assertEquals(0.2f, tooLow.pageOpacity, 0.0001f)
        val tooHigh = MangaReaderOptions.fromJson("""{"pageOpacity":5}""", fallback = MangaReaderOptions())
        assertEquals(1f, tooHigh.pageOpacity, 0.0001f)
    }

    @Test fun cropToleranceIsClamped() {
        val negative = MangaReaderOptions.fromJson("""{"cropTolerance":-10}""", fallback = MangaReaderOptions())
        assertEquals(0, negative.cropTolerance)
        val huge = MangaReaderOptions.fromJson("""{"cropTolerance":900}""", fallback = MangaReaderOptions())
        assertEquals(60, huge.cropTolerance)
    }

    @Test fun dualPageActivatesOnLandscapeOnlyWhenRequested() {
        val options = MangaReaderOptions(dualPage = false, dualPageOnLandscape = true)
        assertFalse(options.dualPageActive(isLandscape = false))
        assertTrue(options.dualPageActive(isLandscape = true))
        // webtoon modunda hiçbir zaman çift sayfa
        assertFalse(options.copy(readingMode = ReadingMode.Webtoon).dualPageActive(true))
    }

    @Test fun bookEffectIsNotIdentity() {
        assertFalse(MangaImageFilters.isIdentity(ColorFilterType.Normal, bookEffect = true))
        assertTrue(MangaImageFilters.isIdentity(ColorFilterType.Normal, bookEffect = false))
        val base = MangaImageFilters.pipeline(1f, 1f, 0f)
        val effect = MangaImageFilters.pipeline(1f, 1f, 0f, bookEffect = true)
        assertFalse("book effect matrisi farklı olmalı", base.contentEquals(effect))
    }

    // ─── Dedup (2026-11-07): tek kaynak doğrulaması ─────────────────────────

    @Test fun cropEnabledIsSingleSourceOfTruth() {
        assertFalse(MangaReaderOptions().cropEnabled)
        assertTrue(MangaReaderOptions(cropProfile = MangaCropProfile.White).cropEnabled)
        assertTrue(MangaReaderOptions(cropProfile = MangaCropProfile.Auto).cropEnabled)
        assertFalse(MangaReaderOptions(cropProfile = MangaCropProfile.Off).cropEnabled)
    }

    @Test fun legacyCropBordersFalseDisablesCrop() {
        val restored = MangaReaderOptions.fromJson(
            """{"cropBorders":false}""",
            fallback = MangaReaderOptions(cropProfile = MangaCropProfile.Black)
        )
        assertEquals(MangaCropProfile.Off, restored.cropProfile)
    }

    @Test fun autoNextEnabledDerivesFromSecondsOnly() {
        assertFalse(MangaReaderOptions().autoNextEnabled)
        assertTrue(MangaReaderOptions(autoNextChapterSeconds = 10).autoNextEnabled)
    }

    @Test fun resetCalibrationRestoresNeutralPipeline() {
        val dirty = MangaReaderOptions(
            colorFilter = ColorFilterType.Sepia,
            contrast = 1.6f,
            saturation = 1.4f,
            blueLight = 0.5f,
            bookEffect = true,
            brightness = 0.6f
        )
        assertTrue(dirty.hasCalibration)
        val clean = dirty.resetCalibration()
        assertFalse(clean.hasCalibration)
        assertEquals(ColorFilterType.Normal, clean.colorFilter)
        assertEquals(1f, clean.contrast, 0.0001f)
        assertEquals(1f, clean.saturation, 0.0001f)
        assertEquals(0f, clean.blueLight, 0.0001f)
        assertFalse(clean.bookEffect)
        assertEquals(1f, clean.brightness, 0.0001f)
    }

    @Test fun readingModeCycleOrderCoversAllModes() {
        assertEquals(ReadingMode.entries.toSet(), ReadingMode.cycleOrder.toSet())
    }

    @Test fun resolvePageBackgroundMatchesManualPipeline() {
        val smart = resolvePageBackground(MangaPageBackground.Smart, 0.9f, bookEffect = false)
        assertEquals(smartBackground(0.9f), smart)
        val tinted = resolvePageBackground(MangaPageBackground.Black, 0f, bookEffect = true)
        assertEquals(bookTintedBackground(MangaPageBackground.Black.color(), true), tinted)
    }

    @Test fun bookTintedBackgroundIsNeutralWhenDisabled() {
        val base = androidx.compose.ui.graphics.Color(0xFF161622)
        assertEquals(base, bookTintedBackground(base, bookEffect = false))
        val tinted = bookTintedBackground(base, bookEffect = true)
        assertTrue("kitap efektinde mavi kanal azalmalı", tinted.blue < base.blue)
        assertEquals(base.alpha, tinted.alpha, 0.0001f)
    }

    // ─── 3. tur: Mihon kod paritesi (zoom start, flaş, skipRead, SmartFit) ───

    @Test fun smartFitModeResolvesAndCycles() {
        assertEquals(MangaFitMode.SmartFit, MangaFitMode.fromKey("SmartFit"))
        assertEquals(MangaFitMode.SmartFit, MangaFitMode.fromKey("SMART_FIT"))
        assertEquals(MangaFitMode.FitScreen, MangaFitMode.fromKey("bilinmeyen"))
        assertEquals(
            "döngü tüm modları kapsamalı",
            MangaFitMode.entries.toSet(),
            MangaFitMode.cycleOrder.toSet()
        )
    }

    @Test fun longTapActionKeysResolve() {
        MangaLongTapAction.cycleOrder.forEach {
            assertEquals(it, MangaLongTapAction.fromKey(it.key))
        }
        assertEquals(MangaLongTapAction.SavePage, MangaLongTapAction.fromKey("Save"))
        assertEquals(MangaLongTapAction.CopyLink, MangaLongTapAction.fromKey("Link"))
        assertEquals(null, MangaLongTapAction.fromKey("yok"))
    }

    @Test fun zoomStartKeysResolve() {
        MangaZoomStart.cycleOrder.forEach { assertEquals(it, MangaZoomStart.fromKey(it.key)) }
        assertEquals(MangaZoomStart.Automatic, MangaZoomStart.fromKey("Auto"))
        assertEquals(null, MangaZoomStart.fromKey(null))
    }

    @Test fun flashColorKeysResolve() {
        MangaFlashColor.cycleOrder.forEach { assertEquals(it, MangaFlashColor.fromKey(it.key)) }
    }

    @Test fun thirdRoundOptionsSurviveJsonRoundTrip() {
        val options = MangaReaderOptions(
            fitMode = MangaFitMode.SmartFit,
            longTapAction = MangaLongTapAction.SharePage,
            zoomStart = MangaZoomStart.Left,
            flashColor = MangaFlashColor.Black,
            flashDurationMs = 250,
            flashInterval = 3,
            skipReadChapters = true,
            rotateWidePagesInvert = true
        )
        val restored = MangaReaderOptions.fromJson(options.toJson(), fallback = MangaReaderOptions())
        assertEquals(MangaFitMode.SmartFit, restored.fitMode)
        assertEquals(MangaLongTapAction.SharePage, restored.longTapAction)
        assertEquals(MangaZoomStart.Left, restored.zoomStart)
        assertEquals(MangaFlashColor.Black, restored.flashColor)
        assertEquals(250, restored.flashDurationMs)
        assertEquals(3, restored.flashInterval)
        assertTrue(restored.skipReadChapters)
        assertTrue(restored.rotateWidePagesInvert)
    }

    @Test fun flashValuesAreClamped() {
        val tooFast = MangaReaderOptions.fromJson("""{"flashDurationMs":1}""", fallback = MangaReaderOptions())
        assertEquals(30, tooFast.flashDurationMs)
        val tooSlow = MangaReaderOptions.fromJson("""{"flashDurationMs":99999}""", fallback = MangaReaderOptions())
        assertEquals(1000, tooSlow.flashDurationMs)
        val badInterval = MangaReaderOptions.fromJson("""{"flashInterval":0}""", fallback = MangaReaderOptions())
        assertEquals(1, badInterval.flashInterval)
    }

    @Test fun defaultsKeepExistingBehaviour() {
        val defaults = MangaReaderOptions()
        assertEquals(MangaFitMode.FitScreen, defaults.fitMode)
        assertEquals("varsayılan menü = eski davranış", MangaLongTapAction.Menu, defaults.longTapAction)
        assertEquals(MangaFlashColor.White, defaults.flashColor)
        assertEquals(90, defaults.flashDurationMs)
        assertEquals(1, defaults.flashInterval)
        assertFalse(defaults.skipReadChapters)
        assertFalse(defaults.rotateWidePagesInvert)
    }

    // ─── Bar düzeni (hızlı okuma kademeleri) ────────────────────────────────

    @Test fun barStyleDefaultsToFullSoLayoutIsUnchanged() {
        assertEquals(MangaBarStyle.Full, MangaReaderOptions().barStyle)
    }

    @Test fun barStyleKeysResolve() {
        MangaBarStyle.cycleOrder.forEach { assertEquals(it, MangaBarStyle.fromKey(it.key)) }
        assertEquals(MangaBarStyle.Compact, MangaBarStyle.fromKey("Slim"))
        assertEquals(MangaBarStyle.Minimal, MangaBarStyle.fromKey("thin"))
        assertEquals(MangaBarStyle.Full, MangaBarStyle.fromKey(null))
        assertEquals(MangaBarStyle.Full, MangaBarStyle.fromKey("bilinmeyen"))
    }

    @Test fun barStyleSurvivesJsonRoundTrip() {
        val compact = MangaReaderOptions(barStyle = MangaBarStyle.Compact)
        assertEquals(
            MangaBarStyle.Compact,
            MangaReaderOptions.fromJson(compact.toJson(), MangaReaderOptions()).barStyle
        )
        // Eski JSON'da barStyle yoksa varsayılan korunur (mevcut düzen bozulmaz)
        assertEquals(
            MangaBarStyle.Full,
            MangaReaderOptions.fromJson("""{"readingMode":"Webtoon"}""", MangaReaderOptions()).barStyle
        )
    }

    @Test fun barStyleCyclesThroughAllLevels() {
        var current = MangaBarStyle.Full
        val seen = mutableSetOf(current)
        repeat(MangaBarStyle.cycleOrder.size - 1) {
            val idx = MangaBarStyle.cycleOrder.indexOf(current)
            current = MangaBarStyle.cycleOrder[(idx + 1) % MangaBarStyle.cycleOrder.size]
            seen += current
        }
        assertEquals(MangaBarStyle.entries.toSet(), seen)
    }

    // ─── Çift sayfa eşleştirmesi (alakasız sayfa birleştirme hatası) ────────

    private val dual = MangaReaderOptions(readingMode = ReadingMode.RightToLeft, dualPage = true)

    @Test fun dualGroupsPairNeighbouringPages() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            MangaReaderPaging.groups(5, dual)
        )
    }

    @Test fun firstPageSingleLeavesCoverAlone() {
        val options = dual.copy(firstPageSingle = true)
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            MangaReaderPaging.groups(5, options)
        )
        assertEquals(3, MangaReaderPaging.pagerCount(5, options))
    }

    @Test fun wideSpreadPageIsNeverPairedWithAnother() {
        // Sayfa 2 zaten iki sayfalık yayılım → yanına sayfa konmaz.
        assertEquals(
            listOf(listOf(0, 1), listOf(2), listOf(3, 4)),
            MangaReaderPaging.groups(5, dual, spreadPages = setOf(2))
        )
    }

    @Test fun spreadDetectionUsesWidePageThreshold() {
        // 1.2 eşik → genişlik/yükseklik >= 0.833 yayılımdır.
        assertTrue(MangaReaderPaging.isSpread(1.4f, dual))
        assertTrue(MangaReaderPaging.isSpread(0.9f, dual))
        assertFalse(MangaReaderPaging.isSpread(0.6f, dual))
        assertFalse(MangaReaderPaging.isSpread(0f, dual))
    }

    @Test fun pageIndexAndPagerIndexRoundTripWithCoverSingle() {
        val options = dual.copy(firstPageSingle = true)
        val count = 7
        for (page in 0 until count) {
            val slot = MangaReaderPaging.pagerIndexFor(page, count, options)
            assertTrue(
                "sayfa $page yuva $slot içinde olmalı",
                page in MangaReaderPaging.pageIndicesFor(slot, count, options)
            )
        }
    }

    @Test fun singlePageModeKeepsOnePagePerSlot() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), MangaReaderPaging.groups(3, MangaReaderOptions()))
        assertEquals(3, MangaReaderPaging.pagerCount(3, MangaReaderOptions()))
    }

    @Test fun dualPageOptionsDefaultToBackwardCompatibleValues() {
        val options = MangaReaderOptions()
        assertFalse(options.firstPageSingle)
        assertFalse(options.dualPageInvert)
    }

    @Test fun dualPageFlagsSurviveJsonRoundTrip() {
        val options = dual.copy(firstPageSingle = true, dualPageInvert = true)
        val restored = MangaReaderOptions.fromJson(options.toJson(), MangaReaderOptions())
        assertTrue(restored.firstPageSingle)
        assertTrue(restored.dualPageInvert)
    }

    @Test fun pageLuminanceStartsUnknownAndIsPerPage() {
        val a = MangaPage(index = 0, url = "a")
        val b = MangaPage(index = 1, url = "b")
        assertNull(a.luminance)
        a.luminance = 0.8f
        assertEquals(0.8f, a.luminance!!, 0.0001f)
        // Diğer sayfa etkilenmez: Smart zeminde her sayfa kendi tonunu kullanır.
        assertNull(b.luminance)
    }
}
