package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.*
import com.kitsugi.animelist.ui.screens.manga.reader.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/** Real production policies, runnable with JUnit or the SDK-independent CLI harness. */
object MangaReaderRegressionCases {
    fun runAll(report: (String) -> Unit = {}): Int {
        var count = 0
        fun test(name: String, body: () -> Unit) { body(); count++; report(name) }
        fun close(a: Float, b: Float, tolerance: Float = .002f) = check(abs(a - b) <= tolerance) { "$a != $b" }
        val web = ReaderKeyContext(ReadingMode.Webtoon)
        val ltr = ReaderKeyContext(ReadingMode.LeftToRight)
        val rtl = ReaderKeyContext(ReadingMode.RightToLeft)
        fun key(key: ReaderKey, context: ReaderKeyContext = web, shift: Boolean = false, ctrl: Boolean = false, repeat: Int = 0, up: Boolean = false) =
            MangaReaderShortcutPolicy.resolve(ReaderKeyStroke(key, shift = shift, ctrl = ctrl, repeatCount = repeat, keyUp = up), context)

        test("legacy WebtoonZoom is canonical webtoon, not RTL") {
            check(MangaReaderPreferences.readingMode("WebtoonZoom") == ReadingMode.Webtoon)
            check(MangaReaderPreferences.readingMode("Webtoon") == ReadingMode.Webtoon)
        }
        test("all canonical directions and invalid preference fallback") {
            ReadingMode.entries.forEach { check(MangaReaderPreferences.readingMode(it.name) == it) }
            check(MangaReaderPreferences.readingMode("corrupt") == ReadingMode.RightToLeft)
            check(MangaReaderPreferences.nextReadingMode(ReadingMode.RightToLeft) == ReadingMode.LeftToRight)
            check(MangaReaderPreferences.nextReadingMode(ReadingMode.Webtoon) == ReadingMode.RightToLeft)
        }
        test("old grayscale and inversion preference aliases") {
            check(MangaReaderPreferences.colorFilter("GrayScale") == ColorFilterType.Grayscale)
            check(MangaReaderPreferences.colorFilter("Inverted") == ColorFilterType.Invert)
            check(MangaReaderPreferences.colorFilter("Warm") == ColorFilterType.Sepia)
            check(MangaReaderPreferences.colorFilter("Cool") == ColorFilterType.Normal)
            listOf(ColorFilterType.Normal, ColorFilterType.Grayscale, ColorFilterType.Sepia, ColorFilterType.Invert).forEach {
                check(MangaReaderPreferences.colorFilter(it.name) == it)
            }
        }
        test("seamless defaults and sanitized persistent limits") {
            val defaults = MangaWebtoonPreferences()
            check(defaults.sidePaddingDp == 0 && defaults.pageGapDp == 0 && !defaults.volumeKeys)
            val bad = defaults.copy(sidePaddingDp = -1, pageGapDp = 500, scrollPercent = 0, autoScrollDpPerSecond = Int.MAX_VALUE, background = "bad").sanitized()
            check(bad.sidePaddingDp == 0 && bad.pageGapDp == 32 && bad.scrollPercent == 25 && bad.autoScrollDpPerSecond == 160 && bad.background == "Black")
        }
        test("nonfinite brightness/offsets cannot leak into UI or persistence") {
            for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                check(MangaReaderPreferences.finite(bad, 1f, .1f, 1f) == 1f)
            }
        }
        test("natural aspect ratios, variable source widths, contiguous boundaries") {
            val layout = WebtoonLayout(listOf(MangaImageInfo(800, 9600), MangaImageInfo(1200, 3600)))
            check(layout.tiles.size == 15)
            check(layout.tiles.take(12).sumOf { it.heightPx(900) } == 10800)
            check(layout.tiles.drop(12).sumOf { it.heightPx(900) } == 2700)
            check(layout.firstTile(1) == 12)
            layout.tiles.groupBy { it.pageIndex }.values.forEach { tiles ->
                check(tiles.first().top == 0)
                tiles.zipWithNext().forEach { (a, b) -> check(a.bottom == b.top) }
            }
        }
        test("very tall 1:1000 image uses bounded lazy drawing units") {
            val layout = WebtoonLayout(listOf(MangaImageInfo(100, 100000)))
            check(layout.tiles.size == 1000)
            check(layout.tiles.all { it.heightPx(1080) == 1080 })
        }
        test("non-square tail is retained without stretching") {
            val layout = WebtoonLayout(listOf(MangaImageInfo(777, 2000)))
            check(layout.tiles.size == 3)
            check(layout.tiles.last().bottom == 2000 && layout.tiles.last().top == 1554)
            check(layout.tiles.sumOf { it.heightPx(333) } == kotlin.math.round(2000.0 * 333 / 777).toInt())
        }
        test("pending/error placeholders cannot report the footer as a real page") {
            val layout = WebtoonLayout(listOf(null, MangaImageInfo(100, 250), null))
            check(layout.firstTile(1) == 1 && layout.firstTile(2) == 4)
            check(layout.position(4, 200, 500) == WebtoonLayout.Position(2, 0f))
            check(layout.position(5, 0, 500).pageIndex == 2)
        }
        test("empty layout is safe") {
            val layout = WebtoonLayout(emptyList())
            check(layout.tiles.isEmpty())
            check(layout.target(-1, Float.NaN, 100) == WebtoonLayout.Target(0, 0))
        }
        test("late image headers preserve stable page-and-tile keys") {
            val old = WebtoonLayout(listOf(null, MangaImageInfo(500, 1600)))
            val loaded = WebtoonLayout(listOf(MangaImageInfo(600, 6000), MangaImageInfo(500, 1600)))
            check(old.tiles.filter { it.pageIndex == 1 }.map { it.key } == loaded.tiles.filter { it.pageIndex == 1 }.map { it.key })
        }
        test("fractional progress roundtrip across viewport widths") {
            val layout = WebtoonLayout(listOf(MangaImageInfo(923, 31017), MangaImageInfo(600, 15999)))
            for (width in listOf(192, 320, 1080, 1920)) for (page in 0..1) for (fraction in listOf(0f, .01f, .333f, .5f, .97f, 1f)) {
                val target = layout.target(page, fraction, width)
                val result = layout.position(target.itemIndex, target.offsetPx, width)
                check(result.pageIndex == page)
                close(result.fraction, fraction)
            }
        }
        test("randomized tile geometry has no cumulative rounding drift") {
            val random = Random(27)
            repeat(300) {
                val w = random.nextInt(300, 2001); val h = random.nextInt(1, 100001); val display = random.nextInt(200, 2001)
                val layout = WebtoonLayout(listOf(MangaImageInfo(w, h)))
                val total = layout.tiles.sumOf { it.heightPx(display) }
                check(abs(total - kotlin.math.round(h.toDouble() * display / w).toInt()) <= 1)
                val fraction = random.nextFloat()
                val target = layout.target(0, fraction, display)
                close(layout.position(target.itemIndex, target.offsetPx, display).fraction, fraction)
            }
        }
        test("corrupt saved fractions and invalid page indices are clamped") {
            val layout = WebtoonLayout(listOf(MangaImageInfo(500, 5000)))
            check(layout.target(-5, Float.NaN, 500) == WebtoonLayout.Target(0, 0))
            check(layout.target(500, -1f, 500) == WebtoonLayout.Target(0, 0))
            check(layout.target(0, 9f, 500) == WebtoonLayout.Target(9, 500))
        }
        test("normal/mirrored EXIF strips map to exact encoded rectangles") {
            check(MangaImageInfo(800, 2000, 1).sourceRegion(100, 500) == ImageRegion(0, 100, 800, 500))
            check(MangaImageInfo(800, 2000, 2).sourceRegion(100, 500) == ImageRegion(0, 100, 800, 500))
            for (o in listOf(3, 4)) check(MangaImageInfo(800, 2000, o).sourceRegion(100, 500) == ImageRegion(0, 1500, 800, 1900))
        }
        test("90/270 degree EXIF pages have upright dimensions and strip coordinates") {
            for (o in 5..8) {
                val info = MangaImageInfo(2000, 800, o)
                check(info.width == 800 && info.height == 2000)
                val expected = if (o in 5..6) ImageRegion(100, 0, 500, 800) else ImageRegion(1500, 0, 1900, 800)
                check(info.sourceRegion(100, 500) == expected)
            }
        }
        test("decoded texture and bitmap edges stay bounded for huge source regions") {
            for ((w, h) in listOf(100 to 100000, 40000 to 40000, 65000 to 1200, Int.MAX_VALUE to Int.MAX_VALUE)) {
                for (target in listOf(1, 320, 1080, 5000)) {
                    val sample = WebtoonLayout.sampleSize(w, h, target)
                    check(sample > 0 && sample and (sample - 1) == 0)
                    check(ceil(w.toDouble() / sample) <= 2048 && ceil(h.toDouble() / sample) <= 2048)
                }
            }
        }
        test("screenful advance uses viewport, not original image height") {
            check(WebtoonLayout.scrollDistance(1000, 90) == 900f)
            check(WebtoonLayout.scrollDistance(400, 90) == 360f)
            check(WebtoonLayout.scrollDistance(1000, 100) == 1000f)
        }
        test("strip zoom preserves the source point under the fingers") {
            val t = WebtoonViewportTransform.calculate(1f, 0f, 2f, 500f, 700f, 0f, 0f, 1000)
            close(t.zoom, 2f); close(t.panX, 0f)
            close(t.scrollDelta + 700f / t.zoom, 700f)
        }
        test("strip zoom limits and horizontal pan clamp are global") {
            val t = WebtoonViewportTransform.calculate(2f, 0f, 8f, 500f, 400f, 99999f, 0f, 1000)
            check(t.zoom == 3f && t.panX == 1000f)
            val reset = WebtoonViewportTransform.calculate(3f, 1000f, 1f / 3f, 500f, 400f, 0f, 0f, 1000)
            check(reset.zoom == 1f && reset.panX == 0f)
        }
        test("invalid pointer transformations are ignored") {
            for (factor in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
                check(WebtoonViewportTransform.calculate(1f, 0f, factor, 1f, 1f, 0f, 0f, 1000) == WebtoonViewportTransform.Result(1f, 0f, 0f))
            }
        }
        test("webtoon last-image entry does not mark a chapter read") {
            check(!MangaReadingProgressPolicy.isComplete(ReadingMode.Webtoon, 9, 10, null, false))
            check(!MangaReadingProgressPolicy.isComplete(ReadingMode.Webtoon, 9, 10, false, false))
            check(MangaReadingProgressPolicy.isComplete(ReadingMode.Webtoon, 9, 10, true, false))
        }
        test("paged completion and sticky completed status remain compatible") {
            check(MangaReadingProgressPolicy.isComplete(ReadingMode.RightToLeft, 9, 10, null, false))
            check(!MangaReadingProgressPolicy.isComplete(ReadingMode.RightToLeft, 8, 10, null, false))
            check(MangaReadingProgressPolicy.isComplete(ReadingMode.Webtoon, 0, 10, false, true))
            check(!MangaReadingProgressPolicy.isComplete(ReadingMode.Webtoon, -1, 0, true, false))
        }
        test("LTR and RTL horizontal keys follow reading direction") {
            check(key(ReaderKey.Left, rtl).action == ReaderAction.Forward && key(ReaderKey.Right, rtl).action == ReaderAction.Backward)
            check(key(ReaderKey.Right, ltr).action == ReaderAction.Forward && key(ReaderKey.Left, ltr).action == ReaderAction.Backward)
        }
        test("webtoon/vertical directions never inherit RTL semantics") {
            for (context in listOf(web, ReaderKeyContext(ReadingMode.Vertical))) {
                check(key(ReaderKey.Down, context).action == ReaderAction.Forward)
                check(key(ReaderKey.Up, context).action == ReaderAction.Backward)
            }
        }
        test("Space and Shift+Space advance opposite screenfuls") {
            check(key(ReaderKey.Space).action == ReaderAction.Forward)
            check(key(ReaderKey.Space, shift = true).action == ReaderAction.Backward)
        }
        test("chapter aliases and Ctrl arrows are semantic, not RTL reversed") {
            for (context in listOf(web, ltr, rtl)) {
                for (k in listOf(ReaderKey.N, ReaderKey.RightBracket)) check(key(k, context).action == ReaderAction.NextChapter)
                for (k in listOf(ReaderKey.P, ReaderKey.LeftBracket)) check(key(k, context).action == ReaderAction.PreviousChapter)
                check(key(ReaderKey.Right, context, ctrl = true).action == ReaderAction.NextChapter)
                check(key(ReaderKey.Left, context, ctrl = true).action == ReaderAction.PreviousChapter)
            }
        }
        test("key up is consumed without dispatching another command") {
            ReaderKey.entries.filter { key(it).consumed }.forEach { check(key(it, up = true).action == null) }
        }
        test("held toggle/chapter/retry keys do not repeatedly fire") {
            for (k in listOf(ReaderKey.N, ReaderKey.P, ReaderKey.S, ReaderKey.M, ReaderKey.A, ReaderKey.R, ReaderKey.D, ReaderKey.Home, ReaderKey.End)) {
                val result = key(k, repeat = 12)
                check(result.consumed && result.action == null)
            }
        }
        test("held movement and zoom keys repeat") {
            for (k in listOf(ReaderKey.Down, ReaderKey.Up, ReaderKey.PageDown, ReaderKey.PageUp, ReaderKey.Plus, ReaderKey.Minus)) {
                check(key(k, repeat = 4).action?.repeatable == true)
            }
        }
        test("open overlays do not leak shortcuts into text fields") {
            val context = web.copy(overlayOpen = true)
            ReaderKey.entries.filter { it !in listOf(ReaderKey.Back, ReaderKey.Escape) }.forEach { check(!key(it, context).consumed) }
            check(key(ReaderKey.Escape, context).action == ReaderAction.Back)
        }
        test("a panel's own shortcut closes it, other keys stay with the panel") {
            // Aç/kapa: panel açıkken YALNIZCA onu açan tuş eylem üretir; host bu
            // eylemi toggle ederek paneli kapatır. Diğer tuşlar panele bırakılır.
            val settings = web.copy(overlayOpen = true, openPanel = ReaderPanel.Settings)
            check(key(ReaderKey.S, settings).action == ReaderAction.Settings)
            for (k in listOf(ReaderKey.C, ReaderKey.G, ReaderKey.H, ReaderKey.M, ReaderKey.F)) {
                check(!key(k, settings).consumed)
            }
            check(key(ReaderKey.C, web.copy(overlayOpen = true, openPanel = ReaderPanel.Chapters)).action == ReaderAction.Chapters)
            check(key(ReaderKey.G, web.copy(overlayOpen = true, openPanel = ReaderPanel.GoToPage)).action == ReaderAction.GoToPage)
            check(key(ReaderKey.F1, web.copy(overlayOpen = true, openPanel = ReaderPanel.Shortcuts)).action == ReaderAction.Help)
            check(key(ReaderKey.H, web.copy(overlayOpen = true, openPanel = ReaderPanel.Shortcuts)).action == ReaderAction.Help)
            // Tuş bırakılması (keyUp) tüketilir ama ikinci bir kapatma eylemi üretmez.
            check(key(ReaderKey.S, settings, up = true).consumed)
            check(key(ReaderKey.S, settings, up = true).action == null)
        }
        test("focused HUD buttons/sliders own all non-back keys including Enter") {
            val context = web.copy(contentFocused = false, menuVisible = true)
            for (k in listOf(ReaderKey.Enter, ReaderKey.Space, ReaderKey.Left, ReaderKey.Right, ReaderKey.S, ReaderKey.R)) check(!key(k, context).consumed)
            check(key(ReaderKey.Back, context).action == ReaderAction.Back)
        }
        test("volume keys are opt-in and never hijack visible controls") {
            check(!key(ReaderKey.VolumeDown).consumed)
            check(key(ReaderKey.VolumeDown, web.copy(volumeKeys = true)).action == ReaderAction.Forward)
            check(key(ReaderKey.VolumeUp, web.copy(volumeKeys = true)).action == ReaderAction.Backward)
            check(!key(ReaderKey.VolumeDown, web.copy(volumeKeys = true, menuVisible = true)).consumed)
        }
        test("alt/meta shortcuts and unknown ctrl combos remain system-owned") {
            check(!MangaReaderShortcutPolicy.resolve(ReaderKeyStroke(ReaderKey.F, alt = true), web).consumed)
            check(!MangaReaderShortcutPolicy.resolve(ReaderKeyStroke(ReaderKey.M, meta = true), web).consumed)
            check(!key(ReaderKey.S, ctrl = true).consumed)
            check(!key(ReaderKey.Other).consumed)
        }
        test("webtoon-only actions cannot silently affect a manga pager") {
            for (k in listOf(ReaderKey.A, ReaderKey.Plus, ReaderKey.Minus)) check(!key(k, rtl).consumed)
            check(!key(ReaderKey.I).consumed)
            check(key(ReaderKey.I, rtl).action == ReaderAction.CycleFit)
        }
        test("all toolbar/panel actions have a reachable keyboard command") {
            val actions = ReaderKey.entries.mapNotNull { key(it).action }.toSet() + ReaderKey.entries.mapNotNull { key(it, ltr).action }
            check(actions.containsAll(ReaderAction.entries))
        }
        test("home/end are chapter-local, not chapter changes") {
            check(key(ReaderKey.Home).action == ReaderAction.FirstPage)
            check(key(ReaderKey.End).action == ReaderAction.LastPage)
        }
        return count
    }
}
