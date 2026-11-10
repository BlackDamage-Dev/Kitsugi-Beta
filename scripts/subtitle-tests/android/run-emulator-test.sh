#!/usr/bin/env bash
./gradlew -p scripts/subtitle-tests/android connectedDebugAndroidTest --stacktrace --console=plain 2>&1 | tee -a archive-device.log
rc=${PIPESTATUS[0]}
adb logcat -d > archive-logcat.txt
exit "$rc"

# Runs the same archive extraction code as the application against live AniSub data.
