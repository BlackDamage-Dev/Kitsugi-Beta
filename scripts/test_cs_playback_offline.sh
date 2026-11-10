#!/usr/bin/env bash
# Lightweight JVM policy tests; not a replacement for Gradle, Media3 or device tests.
# Requires a Kotlin/JVM compiler and Java. Android/model/JUnit are deliberately stubbed.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
: "${JAVA:=java}"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/Log.kt" <<'STUB'
package android.util
object Log { @JvmStatic fun w(tag:String, msg:String):Int = 0 }
STUB
cat > "$tmp/CsTrace.kt" <<'STUB'
package com.kitsugi.animelist.data.cloudstream.diag
object CsTrace { fun info(provider:String, stage:String, msg:String) {} }
STUB
cat > "$tmp/StreamSource.kt" <<'STUB'
package com.kitsugi.animelist.data.repository
data class StreamSource(val addonName:String, val name:String, val title:String, val url:String?, val infoHash:String?, val fileIndex:Int?, val isCS:Boolean=false, val requestHeaders:Map<String,String>?=null, val externalAudioUrl:String?=null, val quality:String?=null, val qualityValue:Int?=null, val csStreamType:String?=null, val csExtractorData:String?=null)
STUB
cat > "$tmp/JUnit.kt" <<'STUB'
package org.junit
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
fun assertEquals(expected:Any?, actual:Any?) { if (expected != actual) throw AssertionError("Expected $expected but got $actual") }
fun assertTrue(value:Boolean) { if (!value) throw AssertionError("Expected true") }
fun assertFalse(value:Boolean) { if (value) throw AssertionError("Expected false") }
fun assertNull(value:Any?) { if (value != null) throw AssertionError("Expected null but got $value") }
STUB
cat > "$tmp/RunTests.kt" <<'STUB'
fun main() {
    val classes = listOf(com.kitsugi.animelist.data.cloudstream.CsPlaybackHeadersTest::class.java, com.kitsugi.animelist.data.cloudstream.CsStreamFormatTest::class.java, com.kitsugi.animelist.data.cloudstream.CsStreamSanitizerTest::class.java)
    var count = 0
    for (c in classes) for (m in c.declaredMethods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }) {
        try { m.invoke(c.getDeclaredConstructor().newInstance()); println("PASS ${c.simpleName}.${m.name}"); count++ }
        catch (e:java.lang.reflect.InvocationTargetException) { throw RuntimeException("FAIL ${c.simpleName}.${m.name}", e.targetException) }
    }
    println("$count policy tests passed (JVM with stubs; no Android/player/network).")
}
STUB
for name in CsPlaybackHeadersTest CsStreamFormatTest CsStreamSanitizerTest; do
    sed -e 's/import org.junit.Assert.\*/import org.junit.*/' -e 's/import org.junit.Assert\./import org.junit./' "app/src/test/java/com/kitsugi/animelist/data/cloudstream/$name.kt" > "$tmp/$name.kt"
done
"$KOTLINC" "$tmp"/*.kt \
    app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsPlaybackHeaders.kt \
    app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamFormat.kt \
    app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamSanitizer.kt \
    -include-runtime -d "$tmp/tests.jar"
"$JAVA" -jar "$tmp/tests.jar"
