package com.kitsugi.animelist.audit

import com.kitsugi.animelist.core.player.*
import com.kitsugi.animelist.core.player.engine.*

fun main() {
    var count = 0
    fun test(name: String, body: () -> Unit) { body(); count++; println("PASS $name") }
    test("mute and normal volume preserved") {
        for (v in listOf(0f, 0.25f, 0.5f, 1f)) check(MpvPlaybackPolicy.volume(v, 200) == v)
    }
    test("volume boost capped") {
        check(MpvPlaybackPolicy.volume(8f, 150) == 1.5f)
        check(MpvPlaybackPolicy.volume(-1f, 200) == 0f)
        check(MpvPlaybackPolicy.volume(3f, 0) == 1f)
    }
    test("non-finite volume never reaches JNI") {
        for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) check(MpvPlaybackPolicy.volume(v, 200) == 1f)
    }
    test("CPU debanding and format conversion coexist") {
        check(MpvPlaybackPolicy.videoFilters("cpu", true) == "gradfun=radius=12,format=yuv420p")
    }
    test("filter reset clears old chain") {
        check(MpvPlaybackPolicy.videoFilters("none", false) == "")
        check(MpvPlaybackPolicy.videoFilters("gpu", false) == "")
    }
    test("all 32 ready/cache/seek combinations") {
        for (file in listOf(false, true)) for (cache in listOf(false, true))
            for (idle in listOf(false, true)) for (playing in listOf(false, true))
                for (seeking in listOf(false, true))
                    check(MpvPlaybackPolicy.isReady(file, cache, idle, playing, seeking) ==
                        (file && !cache && !seeking && !(idle && playing)))
    }
    test("14 unique Lua leaf commands") {
        check(MpvPlaybackPolicy.scriptProperties.size == 14)
        check(MpvPlaybackPolicy.scriptProperties.distinct().size == 14)
        check("user-data/aniyomi" !in MpvPlaybackPolicy.scriptProperties)
    }
    test("TLS never disabled for missing paths") {
        for (path in listOf(null, "", " ", "\t")) {
            check(mpvTlsOptions(path).toMap() == mapOf("tls-verify" to "yes"))
        }
    }
    test("explicit Android trust store") {
        check(mpvTlsOptions("/private/ca.pem").toMap() == mapOf("tls-verify" to "yes", "tls-ca-file" to "/private/ca.pem"))
    }
    test("old mpv loadfile argument order") {
        check(MpvPlaybackPolicy.loadfile("a", "start=1.234", "mpv 0.37.0").toList() == listOf("loadfile", "a", "replace", "start=1.234"))
    }
    test("new mpv loadfile argument order") {
        for (version in listOf("mpv 0.38.0", "mpv 0.40.0", "1.0.0"))
            check(MpvPlaybackPolicy.loadfile("a", "start=1.234", version).toList() == listOf("loadfile", "a", "replace", "-1", "start=1.234"))
    }
    test("load from beginning has no start option") {
        check(MpvPlaybackPolicy.loadfile("a", null, "mpv 0.40").toList() == listOf("loadfile", "a", "replace"))
    }
    test("chapter ordering bounds and duplicates") {
        val result = PlayerChapterPolicy.normalize(listOf(PlayerChapter("B", 2000), PlayerChapter("bad", -1),
            PlayerChapter("A", 0), PlayerChapter("duplicate", 2000), PlayerChapter("end", 3000)), 3000)
        check(result == listOf(PlayerChapter("A", 0), PlayerChapter("B", 2000)))
        check(PlayerChapterPolicy.currentIndex(result, 1999) == 0)
        check(PlayerChapterPolicy.currentIndex(result, 2000) == 1)
        check(PlayerChapterPolicy.currentIndex(emptyList(), 0) == -1)
    }
    test("merge native chapters and skip ranges") {
        val result = mergePlayerChapters(listOf(PlayerChapter("Native", 10500), PlayerChapter("Main", 40000)),
            listOf(PlayerChapterRange("Opening", 10000, 30000)), 60000)
        check(result == listOf(PlayerChapter("", 0), PlayerChapter("Opening", 10000), PlayerChapter("", 30000), PlayerChapter("Main", 40000)))
    }
    test("adjacent intervals and duration end do not create redundant gaps") {
        val result = mergePlayerChapters(emptyList(), listOf(PlayerChapterRange("A", 0, 10000),
            PlayerChapterRange("B", 10000, 20000), PlayerChapterRange("invalid", 3, 2)), 20000)
        check(result == listOf(PlayerChapter("A", 0), PlayerChapter("B", 10000)))
    }
    fun candidate(id: String, lang: String?, label: String, external: Boolean = false) =
        SubtitleSelectionPolicy.Candidate(id, lang, label, external)
    test("subtitle blacklist excludes preferred language track") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("bad", "tr", "Turkish commentary"),
            candidate("good", "tr", "Turkish full")), listOf("tr"), blacklist = listOf("COMMENTARY")) == "good")
    }
    test("subtitle whitelist ranks eligible same-language tracks") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("a", "tr", "Group A"),
            candidate("b", "tr", "Group B")), listOf("tr"), whitelist = listOf("group b")) == "b")
    }
    test("whitelist does not bypass source priority") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("external", "tr", "Favorite", true),
            candidate("embedded", "tur", "Other")), listOf("tr"), whitelist = listOf("Favorite")) == "embedded")
    }
    test("blank blacklist entries do not remove tracks") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("a", "tr", "Turkish")), listOf("tr"),
            blacklist = listOf("", "  ")) == "a")
    }
    test("all blocked subtitles yield none") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("a", "tr", "Commentary")), listOf("tr"),
            blacklist = listOf("commentary")) == null)
    }
    test("unwanted foreign language never becomes subtitle fallback") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("it", "ita", "Italian")), listOf("tr", "en")) == null)
    }
    test("regional Turkish and label-only Turkish recognized") {
        for (lang in listOf("tr-TR", "tur", "TR", "tr_TR"))
            check(SubtitleSelectionPolicy.pick(listOf(candidate("a", lang, "Track")), listOf("tr")) == "a")
        check(SubtitleSelectionPolicy.pick(listOf(candidate("a", null, "[TR] Türkçe")), listOf("tr")) == "a")
    }
    test("other preferred subtitle languages retain priority") {
        check(SubtitleSelectionPolicy.pick(listOf(candidate("de", "deu", "German"),
            candidate("en", "eng", "English")), listOf("en", "de")) == "en")
    }
    test("Vorbis/Matroska chapter timestamp parsing and comment pairing") {
        check(PlayerChapterPolicy.parseTimestampMs("00:01:30.250") == 90_250L)
        check(PlayerChapterPolicy.parseTimestampMs("02:05") == 125_000L)
        check(PlayerChapterPolicy.parseTimestampMs("invalid") == null)
        val chapters = PlayerChapterPolicy.parseVorbisComments(
            listOf(
                "CHAPTER001" to "00:00:00.000",
                "CHAPTER001NAME" to "Prologue",
                "CHAPTER002" to "00:01:30.500",
                "CHAPTER002NAME" to "Opening"
            )
        )
        check(chapters == listOf(PlayerChapter("Prologue", 0L), PlayerChapter("Opening", 90_500L)))
    }
    test("scrobble threshold policy and clamping") {
        check(!ScrobblePolicy.shouldScrobble(0.84f))
        check(ScrobblePolicy.shouldScrobble(0.85f))
        check(!ScrobblePolicy.shouldScrobble(Float.NaN))
    }
    test("AniSkip chapter disable policy") {
        check(AniSkipPolicy.shouldDisableOnChapters(hasChapters = true, disableOnChaptersSetting = true))
        check(!AniSkipPolicy.shouldDisableOnChapters(hasChapters = false, disableOnChaptersSetting = true))
        check(!AniSkipPolicy.shouldDisableOnChapters(hasChapters = true, disableOnChaptersSetting = false))
    }
    test("OSD timeout, panel opacity, and Netflix-style skip countdown policy") {
        check(PlayerUiPolicy.clampOsdHideDelayMs(500) == 1000L)
        check(PlayerUiPolicy.clampOsdHideDelayMs(5000) == 5000L)
        check(PlayerUiPolicy.panelAlpha(95) == 0.95f)
        check(PlayerUiPolicy.computeSkipCountdownRemainingSec(true, true, 5, 2) == 3)
        check(PlayerUiPolicy.computeSkipCountdownRemainingSec(true, false, 5, 2) == 0)
        check(PlayerUiPolicy.computeSkipCountdownRemainingSec(false, true, 5, 2) == null)
        check(DoubleTapGestureAction.fromString("PLAY_PAUSE", DoubleTapGestureAction.SEEK) == DoubleTapGestureAction.PLAY_PAUSE)
    }
    test("Dolby Vision stream hint detection") {
        check(DolbyVisionStreamHints.detect("2160p DSNP WEB-DL DDP5.1 Atmos DV HDR10")?.profile == null)
        check(DolbyVisionStreamHints.detect("show.dvh1.05.mkv")?.profile == 5)
        check(DolbyVisionStreamHints.detect("remux dvhe.07.06")?.profile == 7)
        check(DolbyVisionStreamHints.detect("4K DV5 DDP5.1")?.profile == 5)
        check(DolbyVisionStreamHints.detect("Movie 2004 DVDRip XviD") == null)
        check(DolbyVisionStreamHints.detect("Show 2160p WEB-DL HDR10+ DDP5.1") == null)
    }
    test("MPV Dolby Vision vo/hwdec plan") {
        check(MpvDolbyVisionPolicy.resolve(null, true, "gpu", "auto-safe", true) == MpvDolbyVisionOptions.NONE)
        val p5 = MpvDolbyVisionPolicy.resolve(
            DolbyVisionHint(5, "dv"), true, "gpu", "auto-safe", true
        )
        check(p5.vo == "gpu-next" && p5.hwdec == "no")
        val p8 = MpvDolbyVisionPolicy.resolve(
            DolbyVisionHint(8, "dv"), true, "gpu", "auto-safe", true
        )
        check(p8.vo == "gpu-next" && p8.hwdec == null)
        val noGpuNext = MpvDolbyVisionPolicy.resolve(
            DolbyVisionHint(5, "dv"), true, "gpu", "auto-safe", false
        )
        check(noGpuNext.vo == null && noGpuNext.hwdec == null)
        val disabled = MpvDolbyVisionPolicy.resolve(
            DolbyVisionHint(5, "dv"), false, "gpu", "auto-safe", true
        )
        check(disabled.vo == null && disabled.hwdec == null)
    }
    println("$count player policy checks passed (real production Kotlin; not Android/JNI/device tests).")
}
