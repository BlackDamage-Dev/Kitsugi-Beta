# ══════════════════════════════════════════════════════════════════════════════
# Kitsugi — ProGuard / R8 keep rules
# T3-02: isMinifyEnabled = true için kapsamlı kural seti
# ══════════════════════════════════════════════════════════════════════════════

# ── Debug bilgisi (crash report için satır numaraları) ────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations

# ── Kotlin ────────────────────────────────────────────────────────────────────
# Coroutines & reflection (extensions yükleme için)
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory
-keep class kotlin.coroutines.Continuation

# Kotlinx Serialization runtime is a public ABI for dynamically loaded CloudStream .cs3 DEX files.
# R8 cannot see references from downloaded plugin DEX files; without these rules it may rename or
# remove host-side symbols such as kotlinx.serialization.KSerializer, causing NoClassDefFoundError
# in otherwise valid plugins at search/loadLinks time.
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }

# Kotlinx Serialization generated serializers
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    <fields>;
}

# DataStore protobuf stubs
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclassmembers class * {
    @dagger.* <methods>;
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ── OkHttp / OkIO ─────────────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclassmembers class okio.** { *; }
# Eklenti (extensionLib 1.6 / KeiSource) uyum stub'lari: bu siniflar eklenti JAR'i
# tarafindan isimle baglanir; R8 ad degistirirse NoClassDefFoundError olusur.
# okhttp3.CompressionInterceptor / okhttp3.Gzip / okhttp3.brotli.* / okhttp3.zstd.*
# yukaridaki `okhttp3.**` kuraliyla zaten korunur. Asagidaki zstd stub'i icin ayrica:
-keep class com.squareup.zstd.okio.** { *; }
-keepclassmembers class com.squareup.zstd.okio.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.internal.sse.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# ── Gson ──────────────────────────────────────────────────────────────────────
# v2.4.249 (CS-TANI-2026-10-11): CS3 eklentileri Gson'u isimle bağlıyor
# (or. Torrential: `NoClassDefFoundError: Lcom/google/gson/Gson`). R8 ulaşılabilir
# olduğu için sınıfı silmez ama ADINI DEĞİŞTİRİR; eklenti DEX'i orijinal adı
# arar ve patlar. kotlinx.serialization ile aynı kural: tam keep.
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Jackson ───────────────────────────────────────────────────────────────────
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# ── Supabase / Ktor ───────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
# Supabase Auth's generated serializers reference this object by its runtime class name.
-keep class kotlinx.datetime.serializers.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── ExoPlayer / Media3 ────────────────────────────────────────────────────────
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class androidx.media.** { *; }
# Kitsugi Engine native JNI handles (custom Media3 forks)
-keep class androidx.media3.exoplayer.upstream.DefaultAllocatorNative {
    native <methods>;
}
-keep class androidx.media3.exoplayer.source.SampleDataQueueNative {
    native <methods>;
}
-keep class androidx.media3.exoplayer.upstream.Allocation {
    <init>(java.nio.ByteBuffer, int, long);
    public long nativeHandle;
}

# ── Kitsugi Native Crash Handler (JNI) ────────────────────────────────────────
# JNI sembol adı sınıf + metot adından türetilir
# (Java_com_kitsugi_animelist_core_diagnostics_NativeCrashBridge_nativeInstall).
# R8 bu sınıfı yeniden adlandırırsa native çökme yakalayıcı sessizce devre dışı kalır.
-keep class com.kitsugi.animelist.core.diagnostics.NativeCrashBridge { *; }
-keepclasseswithmembernames class com.kitsugi.animelist.core.diagnostics.NativeCrashBridge {
    native <methods>;
}
# Genel güvenlik ağı: native metodu olan sınıfların adları korunmalı.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── MPV (native JNI callbacks) ────────────────────────────────────────────────
# is.xyz.mpv'nin tüm native callback yöntemleri reflection ile çözülür.
-keep class is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }
# KitsugiMpvSurfaceView — native bridge
-keep class com.kitsugi.animelist.core.player.engine.KitsugiMpvSurfaceView { *; }
-keep class com.kitsugi.animelist.core.player.engine.MpvPlayerEngine { *; }

# ── CloudStream / Lagradost (plugin system — DexClassLoader) ──────────────────
# Eklentiler runtime'da host classloader'dan bu sınıfları isimle çözer.
# R8 bunları yeniden adlandırmamalı veya silmemeli.
-keep class com.lagradost.cloudstream3.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-keepclassmembers class com.lagradost.nicehttp.** { *; }
-keep class com.lagradost.api.** { *; }
-keepclassmembers class com.lagradost.api.** { *; }
# CloudStream plugins are loaded through DexClassLoader; the host has no static reference
# to this transitive library, but plugin bytecode may call it by its public FuzzySearch API.
-keep class me.xdrop.fuzzywuzzy.** { *; }
# Jsoup (eklentilerin kullandığı)
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn com.google.re2j.**
-dontwarn javax.script.**
-dontwarn org.jsoup.helper.Re2jRegex
# RxJava (manga extension compat)
-keep class io.reactivex.** { *; }
-keepclassmembers class io.reactivex.** { *; }
-keep class rx.** { *; }
-keepclassmembers class rx.** { *; }
# Mihon / Tachiyomi / Keiyoushi extension host ABI & WebView boundary bridges
-keep class eu.kanade.tachiyomi.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keepclassmembers class uy.kohesive.injekt.** { *; }
-keep class androidx.webkit.** { *; }
-keep interface org.chromium.support_lib_boundary.** { *; }
-keep class org.chromium.support_lib_boundary.** { *; }
# Kotlin reflect (extension loading)
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.reflect.** { *; }

# ── Kotatsu Parsers Redo (manga kaynakları) ───────────────────────────────────
# Kotatsu manga parsers kendi sınıflarını reflection ile yükler.
-keep class org.koitharu.kotatsu.parsers.** { *; }
-keepclassmembers class org.koitharu.kotatsu.parsers.** { *; }

# ── NanoHTTPD (TV Companion Server) ───────────────────────────────────────────
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }

# ── ZXing (QR Code) ───────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keepclassmembers class com.google.zxing.** { *; }

# ── Coil (image loader) ───────────────────────────────────────────────────────
-dontwarn coil.**

# ── androidx.preference (manga extension ConfigurableSource) ──────────────────
-keep class androidx.preference.** { *; }
-keepclassmembers class androidx.preference.** { *; }

# ── AndroidX / Compose genel ──────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.fragment.** { *; }

# ── Uygulama veri sınıfları (Gson/Jackson ile JSON'a yazılanlar) ──────────────
-keep class com.kitsugi.animelist.data.** { *; }
-keepclassmembers class com.kitsugi.animelist.data.** { *; }

# ── FileProvider manifest bağlantısı ──────────────────────────────────────────
-keep class androidx.core.content.FileProvider

# ── WebView JS interface (splash animasyonu) ──────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Missing class stubs ───────────────────────────────────────────────────────
-dontwarn org.mozilla.javascript.**
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.checkerframework.**
-dontwarn kotlinx.datetime.**

# ── FFmpeg Kit / Smart Exception ──────────────────────────────────────────────
-dontwarn com.arthenica.smartexception.**
-dontwarn com.arthenica.ffmpegkit.**
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }

# ── Cloudstream compatibility stubs ──────────────────────────────────────────
-keep class com.lagradost.cloudstream3.R { *; }
-keep class com.lagradost.cloudstream3.R$* { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class androidx.appcompat.** { *; }

# KeiSource checks this interceptor's *simple class name* at runtime in external DEX.
# R8 cannot see that string comparison, so release builds must keep the name and class.
-keep class com.kitsugi.animelist.core.network.CloudflareInterceptor { *; }
