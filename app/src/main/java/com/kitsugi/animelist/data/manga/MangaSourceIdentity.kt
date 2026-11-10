package com.kitsugi.animelist.data.manga

import android.net.Uri
import java.util.Locale

/**
 * Bir kaynağın SharedPreferences / DB anahtarlarında kullanılan SABİT kimliği.
 *
 * ⚠️ ÇÖKME KORUMASI (StackOverflowError — 2026-11-10 raporu)
 * ─────────────────────────────────────────────────────────
 * Bu fonksiyonun sonucu `SourceConfigStore` ve `MangaSourceStateStore` anahtarlarını üretir:
 *
 *     key(source) → stableSourceKey() → prefs.getString(...)
 *
 * Burada `baseUrl` (dinamik getter) okunursa döngü kapanır:
 *
 *     getActiveDomain() → key() → stableSourceKey() → source.baseUrl
 *         → SourceConfigStore.getPreferredBaseUrl() → getActiveDomain() → …
 *
 * `MihonSourceWrapper.baseUrl` aktif alan adını `SourceConfigStore`'dan okuduğu için
 * bu zincir sonu gelmeyen bir özyinelemeye dönüşüyor ve süreç
 * `java.lang.StackOverflowError: stack size 1039KB` ile ölüyordu.
 *
 * Bu yüzden burada ASLA `baseUrl` okunmaz; yalnızca değişmez `originalBaseUrl` kullanılır.
 */
fun MangaSource.stableSourceKey(): String {
    val pkg = pkgName.trim().lowercase(Locale.ROOT)
    if (pkg.isNotEmpty()) return pkg

    // `originalBaseUrl` varsayılan olarak `baseUrl`'e düşer; dinamik getter'ı olan
    // kaynaklar (MihonSourceWrapper) bunu sabit değerle override eder.
    // runCatching: eklenti tarafında patlayan bir getter tüm listelemeyi düşürmesin.
    val rawBase = runCatching { originalBaseUrl }.getOrDefault("")

    val host = runCatching { Uri.parse(rawBase).host.orEmpty() }
        .getOrDefault("")
        .trim()
        .lowercase(Locale.ROOT)
    if (host.isNotEmpty()) return "${lang.lowercase(Locale.ROOT)}|$host"

    return "${lang.lowercase(Locale.ROOT)}|${name.trim().lowercase(Locale.ROOT)}"
}

fun MangaSource.searchSignature(): String {
    return buildString {
        append(name)
        append(' ')
        // Burada da dinamik `baseUrl` yerine sabit adres kullanılır (aynı döngü riski).
        append(runCatching { originalBaseUrl }.getOrDefault(""))
        if (pkgName.isNotBlank()) {
            append(' ')
            append(pkgName)
        }
    }.lowercase(Locale.ROOT)
}
