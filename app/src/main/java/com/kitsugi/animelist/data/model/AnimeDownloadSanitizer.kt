package com.kitsugi.animelist.data.model

/**
 * Gson ile okunan indirme kayıtlarını **güvenli** hâle getirir.
 *
 * ## Neden gerekli?
 * [AnimeDownload] Kotlin'de non-null alanlarla tanımlıdır (`kind`, `status`, `subtitles`…).
 * Gson ise nesneyi **kurucu çağırmadan** (Unsafe) oluşturur; JSON'da bulunmayan alanlar
 * `null` kalır. Yeni bir alan eklendiğinde (ör. `kind`) eski sürümden gelen kayıtlarda bu
 * alan `null` olur ve `data class copy()` çağrısı kurucudaki non-null kontrolüne takılıp
 * `NullPointerException` fırlatır — yani kullanıcının eski indirmeleri duraklatma/silme
 * gibi işlemlerde uygulamayı çökertir.
 *
 * Bu fonksiyon her kaydı **açık kurucu argümanlarıyla** yeniden üretir; eksik alanlara
 * güvenli varsayılan değerleri yazar. Böylece:
 *  - eski kayıtlar bölüm indirmesi olarak çalışmaya devam eder (`kind = EPISODE`),
 *  - sonraki `copy()` çağrıları güvenlidir,
 *  - ilk yazmada şema güncellenir (tek seferlik maliyet).
 */
fun AnimeDownload.rehydrated(): AnimeDownload = AnimeDownload(
    animeId = animeId.orEmpty(),
    animeTitle = animeTitle.orEmpty(),
    posterUrl = posterUrl,
    episode = episode,
    season = season,
    url = url.orEmpty(),
    quality = quality.orEmpty(),
    requestHeaders = requestHeaders ?: emptyMap(),
    subtitles = subtitles ?: emptyList(),
    status = status ?: AnimeDownload.Status.QUEUE,
    progress = progress,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    localPath = localPath,
    createdAt = createdAt,
    downloadedSegments = downloadedSegments,
    malId = malId,
    aniListId = aniListId,
    tmdbId = tmdbId,
    source = source,
    streamTitle = streamTitle,
    streamName = streamName,
    kind = kind ?: AnimeDownload.Kind.EPISODE,
    clipKey = clipKey,
    clipTitle = clipTitle,
    clipBaseName = clipBaseName,
    errorMessage = errorMessage
)
