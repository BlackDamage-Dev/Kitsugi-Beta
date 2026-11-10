#!/usr/bin/env bash
# Compile real SDK-independent player policies; does not simulate Android or JNI.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
bash "$KOTLINC" \
 app/src/main/java/com/kitsugi/animelist/core/player/PlayerChapter.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/ScrobblePolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/AniSkipPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/PlayerGesturePolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlaybackPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionStreamHints.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvDolbyVisionPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvTlsPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/SubtitleInput.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvTrack.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/PlayerSubtitleUtils.kt \
 app/src/main/java/com/kitsugi/animelist/core/player/SubtitleSelectionPolicy.kt \
 scripts/tests/PlayerPolicyChecks.kt \
 -include-runtime -d "$WORK/checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar "$WORK/checks.jar"
