#!/usr/bin/env bash
# Production repositories + in-memory DAO/network boundaries. Not a Room/SQLite or device test.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC}" "${JSON_JAR:?Set JSON_JAR}" "${COROUTINES_JAR:?Set COROUTINES_JAR}"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
"$KOTLINC" \
 app/src/main/java/com/kitsugi/animelist/model/{MediaEntry,MediaIdentity}.kt \
 app/src/main/java/com/kitsugi/animelist/data/auth/SyncSafety.kt \
 app/src/main/java/com/kitsugi/animelist/data/local/{MediaEntryBackup,MediaEntryRepository,MediaEntryDao,MediaEntryEntity,PendingSyncDrainer,PendingSyncDao,PendingSyncEntity,SameSourceDuplicatePlanner,SameSourceMergePolicy,TitleScriptPolicy}.kt \
 scripts/tests/fixtures/*.kt scripts/tests/SyncRepositoryChecks.kt \
 -classpath "$JSON_JAR:$COROUTINES_JAR" -include-runtime -d "$WORK/checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/checks.jar:$JSON_JAR:$COROUTINES_JAR" com.kitsugi.animelist.audit.SyncRepositoryChecksKt
