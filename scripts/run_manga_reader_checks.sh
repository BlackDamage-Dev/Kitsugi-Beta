#!/usr/bin/env bash
# Real production geometry/preferences/shortcut policies. No Android/Compose stubs or rendering.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
bash "$KOTLINC" \
 app/src/main/java/com/kitsugi/animelist/data/manga/MangaReaderPreferences.kt \
 app/src/main/java/com/kitsugi/animelist/data/manga/MangaImageInfo.kt \
 app/src/main/java/com/kitsugi/animelist/data/manga/WebtoonLayout.kt \
 app/src/main/java/com/kitsugi/animelist/data/manga/WebtoonViewportTransform.kt \
 app/src/main/java/com/kitsugi/animelist/data/manga/MangaReadingProgressPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/manga/reader/MangaReaderShortcutPolicy.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderRegressionCases.kt \
 scripts/tests/MangaReaderPolicyChecks.kt -jvm-target 17 -include-runtime -d "$WORK/checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar "$WORK/checks.jar"
python3 scripts/tests/manga_reader_contracts.py
