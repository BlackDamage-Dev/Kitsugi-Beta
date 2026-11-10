#!/usr/bin/env bash
# JVM-only checks for exit classification, ANR trace selection and Bangumi miss-cache policy.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
: "${JUNIT_JAR:?Set JUNIT_JAR to JUnit 4}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to hamcrest-core}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CP="$JUNIT_JAR:$HAMCREST_JAR"
"$KOTLINC" \
 app/src/main/java/com/kitsugi/animelist/core/diagnostics/PreviousExitClassifier.kt \
 app/src/main/java/com/kitsugi/animelist/core/diagnostics/AnrTraceSelector.kt \
 app/src/main/java/com/kitsugi/animelist/data/remote/BangumiSubjectLookupPolicy.kt \
 app/src/test/java/com/kitsugi/animelist/core/diagnostics/PreviousExitClassifierTest.kt \
 app/src/test/java/com/kitsugi/animelist/core/diagnostics/AnrTraceSelectorTest.kt \
 app/src/test/java/com/kitsugi/animelist/data/remote/BangumiSubjectLookupPolicyTest.kt \
 -jvm-target 17 -classpath "$CP" -include-runtime -d "$WORK/diagnostics-checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/diagnostics-checks.jar:$CP" org.junit.runner.JUnitCore \
 com.kitsugi.animelist.core.diagnostics.PreviousExitClassifierTest \
 com.kitsugi.animelist.core.diagnostics.AnrTraceSelectorTest \
 com.kitsugi.animelist.data.remote.BangumiSubjectLookupPolicyTest
