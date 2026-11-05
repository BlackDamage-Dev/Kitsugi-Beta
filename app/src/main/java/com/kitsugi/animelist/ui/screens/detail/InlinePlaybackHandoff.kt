package com.kitsugi.animelist.ui.screens.detail

/**
 * Detay sayfasındaki satır içi (inline) video oynatıcıları için **tek oynatıcı** kuralı.
 *
 * NEDEN (2026-11-05 saha raporu — "Videolar" sekmesi, AnimeThemes OP/ED listesi):
 * Kullanıcı açılış/kapanış satırlarına hızlı hızlı arka arkaya dokunduğunda videolar
 * "kilitleniyor", sonrasında hangi satıra dokunursa dokunsun video açılmıyor ve hata
 * ekranı görünüyordu.
 *
 * Kök neden, oynatıcının yaşam döngüsünün Compose composition'ına bağlı olmasıydı:
 * seçili satır değiştiğinde **yeni** `Media3PlayerEngine`/`MpvPlayerEngine` (ve dolayısıyla
 * yeni bir `ExoPlayer`, yeni bir MediaCodec video çözücüsü, yeni AudioTrack, yeni
 * PlayerView/TextureView ve yeni OkHttp istemcisi) *eski motor serbest bırakılmadan önce*
 * kuruluyordu. `remember { }` composition sırasında çalışır, eski düğümün `onDispose`
 * bloğu ise aynı karenin uygulama (apply) fazında çalışır — yani iki tam oynatıcı yığını
 * kısa süre aynı anda yaşar. Hızlı dokunmalarda bu yığınlar üst üste biner; cihazın
 * sınırlı MediaCodec örneği havuzu tükenir ve yeni video
 * `DecoderInitializationException` ("decoder init failed") ile açılmaz. Aynı anda birden
 * çok oynatıcı ses odağı (audio focus) istediği için yeni oynatıcı odak kaybıyla
 * duraklatılmış hâlde kalabilir. Sonuç: "video kilitlendi, diğerlerine basınca oynamıyor".
 *
 * ÇÖZÜM: bir motor serbest bırakıldığında [INLINE_PLAYER_HANDOFF_MS] boyunca yeni motor
 * kurulmaz. Motorlar ayrıca composition sırasında değil, [awaitEngineSlot] sonrasında,
 * giriş (LaunchedEffect) içinde kurulur. Böylece iki oynatıcı yığını asla üst üste binmez.
 */
internal const val INLINE_PLAYER_HANDOFF_MS = 220L

/**
 * Hızlı dokunmalarda satır seçimi (vurgu) anında değişir; **oynatıcının taşındığı satır**
 * ise bu süre kadar beklenir. Arka arkaya 4-5 satıra dokunulduğunda tek bir motor kurulur.
 */
internal const val INLINE_PLAYER_TAP_COALESCE_MS = 320L

/**
 * Son motor serbest bırakıldıktan sonra, yeni motor kurulmadan önce beklenmesi gereken süre.
 *
 * Saf fonksiyon: cihaz saati enjekte edilir, böylece birim testlerle doğrulanabilir.
 * [lastReleaseAtMs] `0` ya da negatifse (hiç motor kapatılmadıysa) bekleme yoktur.
 */
internal fun inlineEngineHandoffDelayMs(lastReleaseAtMs: Long, nowMs: Long): Long {
    if (lastReleaseAtMs <= 0L) return 0L
    val waitMs = (lastReleaseAtMs + INLINE_PLAYER_HANDOFF_MS) - nowMs
    return waitMs.coerceAtLeast(0L)
}

/**
 * Süreç genelinde tek bir "inline oynatıcı el değiştirme" kapısı.
 *
 * Not: Sayaç/kilit değil, zaman damgası kullanılır. Böylece iptal edilen bir
 * `LaunchedEffect` (satır değişti, kullanıcı sekmeyi kapattı) kapıyı asla kilitleyemez;
 * en kötü durumda bir sonraki oynatıcı birkaç yüz milisaniye geç kurulur.
 */
internal object InlinePlaybackHandoff {

    /** Hiç motor kapatılmadı anlamında; 0 cihaz açılışından önce bir zaman damgasıdır. */
    private const val NO_RELEASE = 0L

    @Volatile
    private var lastReleaseAtMs: Long = NO_RELEASE

    /** Bir inline motor (Media3 veya MPV) serbest bırakıldığında çağrılır. */
    fun onInlineEngineReleased() {
        lastReleaseAtMs = android.os.SystemClock.elapsedRealtime()
    }

    /**
     * Yeni bir inline motor kurmadan önce çağrılır; önceki motorun çözücü (MediaCodec),
     * ses yolu ve yüzey kaynakları serbest kalana kadar kısa bir el değiştirme aralığı bekler.
     */
    suspend fun awaitEngineSlot() {
        // Kısa döngü: saat okuması ile bekleme arasında yeni bir release damgası
        // gelebilir; tekrar hesaplayıp beklemeyi uzatırız. Sınırlı sayıda tur, sonsuz
        // bekleme riskini ortadan kaldırır.
        repeat(6) {
            val waitMs = inlineEngineHandoffDelayMs(
                lastReleaseAtMs = lastReleaseAtMs,
                nowMs = android.os.SystemClock.elapsedRealtime()
            )
            if (waitMs <= 0L) return
            kotlinx.coroutines.delay(waitMs)
        }
    }
}
