#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK with include/jni.h}"
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/android"
printf '#pragma once\n#define ANDROID_LOG_INFO 4\n#define ANDROID_LOG_WARN 5\nint __android_log_print(int, const char *, const char *, ...);\n' > "$tmp/android/log.h"
c++ -std=c++17 -O2 -I"$tmp" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$root/scripts/player-tests/dovi_bridge_host_test.cpp" -o "$tmp/test"
"$tmp/test"
echo 'Dolby Vision native host regression tests passed.'
