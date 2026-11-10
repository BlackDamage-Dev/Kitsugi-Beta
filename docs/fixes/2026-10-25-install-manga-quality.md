# APK compatibility, manga bulk deletion, CDN quality preservation

## APK diagnosis
The checked-out app configuration requires API 26 (Android 8.0) and packages
armeabi-v7a and arm64-v8a native code. It does not support every Android version
or CPU. The screenshot alone cannot distinguish an unsupported OS, incomplete
APK, invalid signature or other package parsing failure. This checkout identifies
itself as 2.4.340, whereas the screenshot names 2.4.368: the exact distributed APK
must be inspected before attributing its failure to these build settings.

Required follow-up: device model/Android version and the actual APK; inspect with
`apksigner verify --verbose`, `aapt dump badging`, compare SHA-256 against release,
and capture the package installer error via adb. No speculative minSdk decrease.

## Manga
Installed tab has selection, select/deselect all, package-count delete button and
confirmation. Selection is by loaded extension ID, so a multi-source APK is removed
once and all affected rows are selected together. Built-in sources without an
extension file cannot be selected. The ViewModel executes deletions sequentially
on IO, refreshes once, and reports failed package count. Both Settings and Manga
Browse callback paths are wired. Turkish and English strings added.

## Rapidrame / Close
The app-bundled JW CDN extractor previously discarded all but the highest variant
and labeled every URL 1080p. Standard HLS masters are now retained as masters so
video renditions remain within the current media instead of becoming provider
links. Legacy bare-link lists still expose their independent source URLs. Unknown
resolution stays unknown.

The fullscreen player now has separate Quality and Sources buttons/sheets:
- Sources retains the existing provider/link switch workflow, including episode
  resolution failure and playback error recovery routes.
- Quality accepts only the active engine's video TrackOptions, not StreamSources.
  Media3 exposes supported video tracks with known height, applies a VIDEO-only
  TrackSelectionOverride in place, and clears it for Automatic selection.
- Source/episode preparation clears old VIDEO overrides. Stale selections are
  checked against current track groups. Audio and subtitle overrides are untouched.
- Standard HLS masters retain external audio/subtitle renditions.
- Zero/one available track shows an explanatory message, not fake resolution choices.
- MPV currently does not expose this quality API and shows the unsupported message;
  no unverified MPV quality switching is claimed. No separate-source URLs are
  inferred to be qualities based solely on provider name or title.

Scope: the bundled extractor only. Kitsugi-Plugins is empty in this checkout;
plugin-provided extractors can take precedence and may still discard variants.
Live FilmMakinesi/HDFilmCehennemi URLs and independently labeled JW source arrays
have not been validated. This is not a claim that every deployed plugin now exposes
all qualities. Cross-host master/segment authorization also needs device testing.

## Validation
- `git diff --check`: passed.
- TR/EN resource XML parsing and duplicate-name checks: passed.
- Added JVM regression tests for multiple resolutions, extensionless variant URLs,
  relative URLs, duplicates, unknown resolution, media playlists and error pages.
- `bash gradlew :app:testFossDebugUnitTest --offline`: blocked; no Java/JAVA_HOME
  installed in the sandbox. Android compilation, tests and device playback not run.
- No APK was built or published.
