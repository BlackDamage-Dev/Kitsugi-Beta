#!/usr/bin/env bash
# JVM-only regression checks for the Listem ANR fix. Compiles the real identity,
# indexed planner and merge policy; Android resources are compile-only shims.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
: "${JUNIT_JAR:?Set JUNIT_JAR to JUnit 4}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to hamcrest-core}"
KOTLIN_LIB="$(cd "$(dirname "$KOTLINC")/../lib" && pwd)"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 - "$WORK" <<'PY'
from pathlib import Path
import re, sys
work=Path(sys.argv[1]); root=Path('app/src/main/java/com/kitsugi/animelist')
names=sorted(set(re.findall(r'R\.string\.(\w+)', (root/'model/MediaEntry.kt').read_text())))
(work/'Resources.kt').write_text('package com.kitsugi.animelist\nobject R { object string {\n'+'\n'.join(f'const val {n} = {i}' for i,n in enumerate(names,1))+'\n} }\n')
(work/'StringRes.kt').write_text('package androidx.annotation\nannotation class StringRes\n')
PY
CP="$COROUTINES_JAR:$JUNIT_JAR:$HAMCREST_JAR"
"$KOTLINC" \
 "$WORK"/*.kt \
 app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt \
 app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt \
 app/src/main/java/com/kitsugi/animelist/data/local/TitleScriptPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/data/local/SameSourceMergePolicy.kt \
 app/src/main/java/com/kitsugi/animelist/data/local/SameSourceDuplicatePlanner.kt \
 app/src/test/java/com/kitsugi/animelist/data/local/SameSourceMergePolicyTest.kt \
 app/src/test/java/com/kitsugi/animelist/data/local/SameSourceDuplicatePlannerTest.kt \
 -jvm-target 17 -classpath "$CP" -include-runtime -d "$WORK/dedup-checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/dedup-checks.jar:$CP" org.junit.runner.JUnitCore \
 com.kitsugi.animelist.data.local.SameSourceMergePolicyTest \
 com.kitsugi.animelist.data.local.SameSourceDuplicatePlannerTest
