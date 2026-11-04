package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.data.settings.AppSettings

/**
 * Compact previews should display their first frame quickly without changing the
 * fullscreen player's buffer policy. Large startup buffers are especially noticeable
 * on AnimeThemes' high-bitrate WebM files on slow connections.
 */
internal fun AppSettings.forCompactInlinePlayback(): AppSettings {
    val compactMinBufferMs = minOf(playback.minBufferMs, 5_000)
    val compactMaxBufferMs = minOf(playback.maxBufferMs, 20_000)
        .coerceAtLeast(compactMinBufferMs)
    return copy(
        playback = playback.copy(
            minBufferMs = compactMinBufferMs,
            maxBufferMs = compactMaxBufferMs,
            bufferForPlaybackMs = minOf(playback.bufferForPlaybackMs, 1_000),
            bufferForPlaybackAfterRebufferMs = minOf(
                playback.bufferForPlaybackAfterRebufferMs,
                2_000
            )
        )
    )
}

internal const val INLINE_PLAYER_STALL_TIMEOUT_MS = 25_000L

/** Tıkanma sayacı ne olursa olsun bu süreden sonra vazgeç (sonsuz skeleton olmaz). */
internal const val INLINE_PLAYER_START_HARD_CAP_MS = 120_000L

/** Geriye dönük ad: artık sabit süre değil, üst sınır. */
internal const val INLINE_PLAYER_START_TIMEOUT_MS = INLINE_PLAYER_START_HARD_CAP_MS

/**
 * Hazırlık aşaması bırakılmalı mı?
 *
 * @param elapsedMs hazırlığın başlamasından bu yana geçen süre
 * @param sinceProgressMs en son konum/tampon ilerlemesinden bu yana geçen süre
 * @param state motorun anlık durumu
 * @param positionMs oynatma konumu
 */
internal fun inlineStartShouldAbandon(
    elapsedMs: Long,
    sinceProgressMs: Long,
    state: PlayerEngine.State,
    positionMs: Long
): Boolean {
    if (positionMs > 0L) return false
    if (elapsedMs >= INLINE_PLAYER_START_HARD_CAP_MS) return true
    // READY olsa bile konum 0 ve tampon büyümüyorsa çözücü kare üretmiyor demektir:
    // yedek motor denenmeli. Tampon büyüdükçe `sinceProgressMs` sıfırlandığı için
    // yavaş ama sağlıklı bir indirme asla "bozuk" sayılmaz.
    if (state == PlayerEngine.State.ENDED) return true
    return sinceProgressMs >= INLINE_PLAYER_STALL_TIMEOUT_MS
}

internal fun PlayerEngineType.alternateInlineEngine(): PlayerEngineType = when (this) {
    PlayerEngineType.MEDIA3 -> PlayerEngineType.MPV
    PlayerEngineType.MPV, PlayerEngineType.EXTERNAL -> PlayerEngineType.MEDIA3
}

internal enum class InlinePlaybackFailure {
    /** HTTP, TLS, stalled or invalid media: another signed URL may repair playback. */
    SOURCE,
    /** Audio advances without a rendered frame: try the other decoder on the same URL first. */
    AUDIO_ONLY
}

internal data class InlinePlaybackAttempt(
    val sourceIndex: Int,
    val engine: PlayerEngineType
)

internal const val MAX_INLINE_PLAYBACK_ATTEMPTS = 4

/**
 * Chooses a genuinely new source/engine attempt and never retries the same broken pair.
 * Source errors move to another URL before changing engines; decoder-like audio-only
 * failures keep the URL and change the engine first. A hard cap prevents fallback loops.
 */
internal fun nextInlinePlaybackAttempt(
    failed: InlinePlaybackAttempt,
    failure: InlinePlaybackFailure,
    sourceCount: Int,
    attempted: Set<InlinePlaybackAttempt>
): InlinePlaybackAttempt? {
    if (sourceCount <= 0 || attempted.size >= MAX_INLINE_PLAYBACK_ATTEMPTS) return null
    val media3Attempts = (0 until sourceCount).map { index ->
        InlinePlaybackAttempt(index, PlayerEngineType.MEDIA3)
    }
    val mpvAttempts = (0 until sourceCount).map { index ->
        InlinePlaybackAttempt(index, PlayerEngineType.MPV)
    }
    val sameSourceMpv = InlinePlaybackAttempt(failed.sourceIndex, PlayerEngineType.MPV)
    val ordered = when (failure) {
        InlinePlaybackFailure.SOURCE ->
            media3Attempts + listOf(sameSourceMpv) + mpvAttempts
        InlinePlaybackFailure.AUDIO_ONLY ->
            listOf(sameSourceMpv) + media3Attempts + mpvAttempts
    }
    return ordered.firstOrNull { candidate ->
        candidate != failed && candidate !in attempted
    }
}

/**
 * YouTube'un çıkarılmış imzalı Googlevideo akışları Media3'ün istek/veri kaynağı politikasını
 * gerektirir. Bu adreslerde küçük oynatıcı Media3 ile başlar (tam ekranla aynı yol).
 *
 * NOT: Doğrudan OP/ED dosyaları bu fonksiyonu artık kullanmıyor; onlar
 * [initialCompactThemeEngineType] üzerinden Media3 ile başlar (cihazda VP9 çözücüsü yoksa
 * doğrudan MPV). Bu fonksiyon yalnızca YouTube inline akışı ve mevcut çağrı yerleriyle
 * geriye dönük uyumluluk için genel tercih eşlemesini korur.
 */
internal fun initialInlineEngineType(
    configuredPreference: String?,
    isYouTubeSource: Boolean
): PlayerEngineType = when {
    isYouTubeSource -> PlayerEngineType.MEDIA3
    configuredPreference.equals("MPV", ignoreCase = true) -> PlayerEngineType.MPV
    else -> PlayerEngineType.MEDIA3
}

/**
 * Kompakt (detay sayfası içindeki) tema oynatıcısı her zaman Media3 ile başlar.
 *
 * NEDEN (2026-10-21 saha raporu): aynı sayfada fragman (Media3 + TextureView) sorunsuz
 * oynarken OP/ED videoları sesli ama siyah kalıyordu. Fark motordu: varsayılan ayar
 * "MPV" olduğu için OP/ED satırları MPV'nin SurfaceView'ını kullanıyordu. SurfaceView,
 * Compose'un kırpılmış/ölçüsü animasyonla değişen kabında (kart `clip()` +
 * `animateDetailContentSize()`) görünür bir yüzeye bağlanamıyor; mpv sesi çalıyor,
 * kare üretmiyor. Media3'ün PlayerView'ı TextureView kullandığı için aynı kabın içinde
 * doğru çiziliyor ve tam ekran oynatıcıyla aynı motoru kullanıyor.
 *
 * Kullanıcının genel motor tercihi tam ekranda korunur; kompakt oynatıcıda Media3
 * başarısız olursa MPV yalnızca bir kez yedek olarak denenir.
 */
internal fun initialCompactThemeEngineType(url: String? = null): PlayerEngineType {
    // AnimeThemes açılış/kapanış dosyaları çoğunlukla VP9 `.webm`. Cihazda VP9 çözücüsü
    // yoksa Media3 sesi çalar ama hiç kare üretmez; bekçi [INLINE_AUDIO_ONLY_GRACE_MS]
    // sonunda MPV'ye geçene kadar kullanıcı siyah ekran görür. Çözücü yokluğu ÖNCEDEN
    // bilinebildiği için doğrudan MPV (yazılım çözücü) ile başlanır.
    val looksWebm = url?.contains(".webm", ignoreCase = true) == true
    if (looksWebm && !deviceHasVideoDecoder(VP9_MIME)) return PlayerEngineType.MPV
    return PlayerEngineType.MEDIA3
}

private const val VP9_MIME = "video/x-vnd.on2.vp9"

/**
 * Cihazda verilen MIME türü için bir çözücü kayıtlı mı?
 *
 * Bilinmeyen/okunamayan durumda `true` döner: yanlışlıkla MPV'ye düşmek yerine Media3
 * ile başlanır (Media3 render edemezse bekçi zaten motoru değiştirir).
 */
private fun deviceHasVideoDecoder(mime: String): Boolean = runCatching {
    android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
}.getOrDefault(true)

/** READY only means prepared; it does not prove that inline playback actually advanced. */
internal fun hasInlinePlaybackProgress(state: PlayerEngine.State, positionMs: Long): Boolean =
    state == PlayerEngine.State.ENDED || positionMs > 0L

/**
 * Kare gerçekten ekranda mı? Seçili video parçası TEK BAŞINA yetmez:
 * Media3'te çözücü kare üretmese de `currentTracks` parçayı seçili gösterir; MPV'de
 * `vo-configured` yüzeyin görünür olduğunu garanti etmez. Bu yüzden hem parça hem
 * "kare çizildi" bilgisi birlikte okunur.
 */
internal fun inlineVideoVisible(engine: PlayerEngine): Boolean =
    engine.hasVideoTrack && engine.hasRenderedVideoFrame

/**
 * Sessiz "sesi var, görüntüsü yok" durumunu yakalamak için tanınan süre.
 *
 * Akış başladıktan sonra video parçasının seçilmesi birkaç yüz ms gecikebilir; bu yüzden
 * ilk [INLINE_AUDIO_ONLY_GRACE_MS] boyunca hüküm verilmez.
 */
internal const val INLINE_AUDIO_ONLY_GRACE_MS = 3_500L

/**
 * Ses-only şüphesinde motordan ayrılmadan önce yapılan "dürtme" (seek) sonrası
 * kare üretmesi için tanınan ek süre.
 *
 * Neden gerekli: bazı donanım çözücüler (özellikle VP9/AV1 `.webm`) ilk kareyi
 * üretmeden takılı kalıyor, ancak aynı akışta 1 ms'lik bir seek çözücüyü yeniden
 * kurup görüntüyü getiriyor. Motoru değiştirmek yerine önce bu ucuz çare denenir;
 * yalnızca işe yaramazsa yedek motora geçilir.
 */
internal const val INLINE_NUDGE_GRACE_MS = 2_500L

/**
 * Görüntü gelene kadar bekçinin çalışabileceği üst sınır.
 *
 * ESKİ DAVRANIŞ (hata): bekçi `graceMs + 4 sn` sonunda sessizce çıkıyordu. Yavaş
 * açılan bir 1080p `.webm` (AnimeThemes OP/ED) 12. saniyede oynatmaya başladığında
 * hiçbir kontrol kalmıyor, video sonsuza kadar siyah/sessiz kalıyordu. Artık bekçi
 * kare gerçekten çizilene kadar (en fazla [INLINE_VIDEO_FRAME_MAX_WAIT_MS]) dinler.
 */
internal const val INLINE_VIDEO_FRAME_MAX_WAIT_MS = 45_000L

/**
 * Oynatım ilerlediği hâlde hiç video parçası seçilmediyse motor değiştirilmelidir.
 *
 * Neden gerekli: hem Media3 hem libmpv, video parçası çözülemediğinde hata vermez.
 * Ses çalar, süre ilerler, ekran siyah kalır — [hasInlinePlaybackProgress] "başladı"
 * der ve yedek motor bekçisi hiç tetiklenmez. Kaynak iki farklı yerde bozulabiliyor:
 * cihazın çözemediği VP9/AV1 `.webm` (AnimeThemes açılış/kapanış dosyaları) ve imzalı
 * YouTube adaptif video akışının 403 dönmesi. İkisi de aynı bekçiyle yakalanır.
 */
internal fun audioOnlyPlaybackDetected(
    elapsedMs: Long,
    state: PlayerEngine.State,
    positionMs: Long,
    hasVisibleVideo: Boolean
): Boolean = elapsedMs >= INLINE_AUDIO_ONLY_GRACE_MS &&
    hasVisibleVideo.not() &&
    hasInlinePlaybackProgress(state, positionMs)


/** Bekçinin bir sonraki adımda ne yapacağı. Saf fonksiyon: cihaz/oynatıcı gerektirmez. */
internal enum class InlineVideoVerdict {
    /** Kare görünür: oynatım sağlıklı, bekçi çıkabilir. */
    HEALTHY,

    /** Henüz hüküm için erken: beklemeye devam. */
    WAITING,

    /** Ses ilerliyor ama kare yok: çözücüyü seek ile dürt (bir kez). */
    NUDGE_RENDERER,

    /** Dürtme de işe yaramadı ya da üst sınır doldu: yedek motora geç. */
    SWITCH_ENGINE
}

/**
 * "Ses var, görüntü yok" bekçisinin karar fonksiyonu.
 *
 * Ayrı tutulmasının nedeni test edilebilirlik: [PlayerEngine] olmadan, yalnızca
 * zaman/konum/bayraklarla aynı karar yeniden üretilebiliyor.
 */
internal fun inlineVideoVerdict(
    elapsedMs: Long,
    state: PlayerEngine.State,
    positionMs: Long,
    hasVisibleVideo: Boolean,
    nudgeUsed: Boolean,
    graceMs: Long = INLINE_AUDIO_ONLY_GRACE_MS,
    nudgeGraceMs: Long = INLINE_NUDGE_GRACE_MS,
    maxWaitMs: Long = INLINE_VIDEO_FRAME_MAX_WAIT_MS
): InlineVideoVerdict {
    if (hasVisibleVideo) return InlineVideoVerdict.HEALTHY
    if (elapsedMs >= maxWaitMs) return InlineVideoVerdict.SWITCH_ENGINE
    if (!hasInlinePlaybackProgress(state, positionMs)) return InlineVideoVerdict.WAITING
    val threshold = graceMs + if (nudgeUsed) nudgeGraceMs else 0L
    if (elapsedMs < threshold) return InlineVideoVerdict.WAITING
    return if (nudgeUsed) InlineVideoVerdict.SWITCH_ENGINE else InlineVideoVerdict.NUDGE_RENDERER
}

/**
 * Oynatım başladıktan sonra video karesinin gerçekten çizilip çizilmediğini izler.
 *
 * `true` dönerse çağıran taraf diğer motora geçmelidir: ses çalıyor ama görüntü hiç
 * gelmiyor. Video karesi çizildiği anda izleme biter; uygulama arka plana geçerse
 * yanlış yedek geçişi tetiklenmez.
 *
 * Bekçi artık sabit bir süre sonunda pes etmiyor: [INLINE_VIDEO_FRAME_MAX_WAIT_MS]
 * boyunca çalışır ve ses-only şüphesinde motoru değiştirmeden önce [nudgeRenderer]
 * ile bir kez çözücüyü dürter. Bu, yavaş açılan dosyalarda "sonsuza kadar siyah
 * ekran" ve gereksiz motor değişimi kaynaklı "video açılmıyor" hatalarını kapatır.
 */
internal suspend fun waitForInlineVideoTrack(
    engine: PlayerEngine,
    isResumed: () -> Boolean,
    graceMs: Long = INLINE_AUDIO_ONLY_GRACE_MS,
    pollMs: Long = 250L,
    maxWaitMs: Long = INLINE_VIDEO_FRAME_MAX_WAIT_MS,
    nudgeRenderer: (() -> Unit)? = null
): Boolean {
    val startedAt = android.os.SystemClock.elapsedRealtime()
    var nudgeUsed = false
    while (true) {
        if (!isResumed()) return false
        if (inlineVideoVisible(engine)) return false
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
        if (!engine.isPlaying) {
            // Kullanıcı duraklattı: hüküm verilmeden beklenir. Bekçi yine de sonsuza
            // kadar yaşamaz; üst sınır dolunca sessizce çıkar.
            if (elapsedMs >= maxWaitMs) return false
            kotlinx.coroutines.delay(pollMs)
            continue
        }
        when (
            inlineVideoVerdict(
                elapsedMs = elapsedMs,
                state = engine.currentState,
                positionMs = engine.currentPosition,
                hasVisibleVideo = false,
                nudgeUsed = nudgeUsed,
                graceMs = graceMs,
                maxWaitMs = maxWaitMs
            )
        ) {
            InlineVideoVerdict.HEALTHY -> return false
            InlineVideoVerdict.WAITING -> Unit
            InlineVideoVerdict.NUDGE_RENDERER -> {
                nudgeUsed = true
                runCatching { nudgeRenderer?.invoke() }
            }
            InlineVideoVerdict.SWITCH_ENGINE -> return true
        }
        kotlinx.coroutines.delay(pollMs)
    }
}
