# Dolby Vision / HDR oynatma incelemesi

## Kaynaklar
- Android Media3 yüzey seçimi: https://developer.android.com/media/media3/ui/surface — SurfaceView, HDR ve kare zamanlaması için tercih edilir.
- Android HDR oynatma: https://developer.android.com/media/grow/hdr-playback — TextureView HDR desteği sınırlıdır.
- Media3 sürüm notları: https://github.com/androidx/media/blob/release/RELEASENOTES.md — DV fallback düzeltmeleri; proje Media3 1.8 kullanıyor.
- mpv video çıkışı: https://github.com/mpv-player/mpv/blob/master/DOCS/man/vo.rst — MediaCodec doğrudan ve copy yolları farklıdır.
- mpv-android geçiş sorunu: https://github.com/mpv-android/mpv-android/issues/966 — doğrudan yol bazı cihazlarda sonraki videoda düşebilir; copy yolunun da maliyetleri vardır.
- mpv-android libdovi denemesi: https://github.com/Darkaxt/mpv-android-libdovi — metadata koruma ve P010 için özel native yamalar gerektirir; P7 FEL garantisi yoktur.
- Media3 profil kontrolleri örneği: https://github.com/midasvo/findroid-ce/pull/100 — cihazın profil bildirmesi tüm görüntü zincirinin çalıştığını garanti etmez.

## Kodda bulunan ve giderilen sorunlar
- Media3'te DV7 HDR10 fallback, P5 dahil başka profillerin MIME ve metadata bilgilerini de bozabiliyordu. Yalnız profil 7 işlenir; profil bilinmiyorsa tahmin edilmez.
- MKV fallback seçimi global durum yerine extractor örneğine taşındı; otomatik dönüşüm başarısız olup fallback seçilince doğru extractor modu kullanılır.
- ASS extractor etkinse oynatma için seçilen DV extractor tamamen yok sayılıyordu. DV extractor korunur ve ASS altyazı parser'ı aktarılır. Bu durumda özel MKV gömülü font toplama yolu kullanılmaz; gerçek cihazda ayrıca sınanmalıdır.
- Media3 yüzeyi TextureView yerine SurfaceView; ekran görüntüsü PixelCopy ile alınır. DRM korumalı yüzeyden ekran görüntüsü alınamaması normaldir.
- Media3 donanım önceliği, platformun gerçek video çözücülerini donanım/yazılım sırasına göre yeniden düzenler; fallback devrededir. Varsayılan listedeki aynı sınıf sırası korunur.
- MPV'de ilgisiz ayarlar değiştiğinde aynı video filtre zinciri tekrar yazılıyor ve çıkış gereksiz yeniden yapılandırılıyordu. Filtre ve deband sadece değişince uygulanır.

## Açık sınırlar
- Mevcut yerleşik libmpv 0.1.12, Dolby Vision'a özel Android MediaCodec metadata/10-bit köprü yamaları eklenmiş bir yapı olarak doğrulanmadı. `gpu-next` veya `hwdec` ayarı tek başına P5/P7/P8 için doğru renk ve donanım çözmeyi garanti etmez.
- Native libdovi derlemesi CMake'de kapalıdır; DV7 → 8.1 gerçek dönüşümü kullanılamaz. Mevcut fallback HDR10 taban katmanı sunabilir, tam Dolby Vision ya da FEL değildir.
- Media3 1.8 yanında projeye özel vendored extractor ve yerel AAR'lar var; tüm Media3 modülleri uyumlu şekilde yükseltilmeden 1.9+ düzeltmeleri güvenle alınamaz.
- Profil 5'in HDR10 taban katmanı yoktur. Desteksiz çözücüye HEVC olarak yeniden etiketlemek doğru fallback değildir. Çözücü/ekran/profil/çözünürlük ve metadata yolu ayrı ayrı uyumlu olmalıdır.
- HLS/DASH ve şifreli örneklerde extractor ve yeniden yazma davranışları cihaz ve örnek içerikle test edilmelidir. Değişiklikler tüm cihazlarda doğrulanmış değildir.

## Doğrulama
Bu ortamda Java ve Android SDK yok; Gradle testleri/Android derlemesi ve gerçek HDR görüntü doğrulaması yapılamadı. P5, P7 MEL/FEL, P8.1, HDR10, SDR; MKV/MP4/HLS; DV/HDR10/SDR ekranlar; seek, pause, altyazı/filtre değişikliği ve motorlar arasında geçiş için cihaz testleri gereklidir. Donanım çözme, kare kaybı ve gerçek renk çıktısı log ve referans görüntüyle ölçülmelidir.

## İkinci tur: kaynak kodu karşılaştırması

Kaynaklar, yalnızca özellik listesi değil ilgili implementasyon okunarak karşılaştırıldı:

| Kaynak / sürüm | İlgili davranış | Kitsugi karşılaştırması |
| --- | --- | --- |
| [Just+ v2.6.3 `Dv7Converter.java`](https://github.com/just-plus-player/just-plus-player/blob/v2.6.3/app/src/main/java/com/brouken/player/Dv7Converter.java) | Şifreli içeriği ayırır, seek'te yarım örneği siler, BlockAdditional RPU'yu ayrı ele alır ve dönüşüm kanıtlanmadan formatı değiştirmez. | Şifreli track'ler dönüşümden çıkarıldı, seek tamponu sıfırlandı. Dönüşümün ilk RPU ile doğrulanması ve BlockAdditional'ın tüm biçimleri hâlâ eksik; real libdovi bu derlemede kapalı. |
| [ExoplayerHdrUtils `HevcFrameTransformer`](https://github.com/suyash192/ExoplayerHdrUtils/blob/a791274f137b301fd9c0b7eb47d4fd18dbacbe30/library/app/src/main/java/com/suyashbelekar/exoplayerhdrutils/video/transformers/HevcFrameTransformer.kt) | libdovi ile gerçek RPU dönüşümü; bit akışını inceleyerek FEL/MEL stratejisi seçimi. | Yerel kütüphane stub; bunu gerçek dönüştürücü saymak yanlış. 0.4.1'in Media3 1.10 bağımlılığı ve örnek/format davranışı doğrudan 1.8 projesine güvenli şekilde eklenebileceğini göstermez. |
| [Kodi P7 dönüşümü PR 22546](https://github.com/xbmc/xbmc/pull/22546) ve [Kodi forumu](https://forum.kodi.tv/showthread.php?tid=371557) | libdovi mode 2, EL atma; tam FEL değil. Forum geçişlerinin hepsinin sorunsuz olmadığını bildiriyor. | Var olan AUTO mode 1 ile Kodi'nin mode 2 davranışı eşdeğer değil; test etmeden değiştirilmedi. |
| [mpv-android MPVView](https://github.com/mpv-android/mpv-android/blob/255901dd586fa750000fa0075f69d6ffdd26cd9c/app/src/main/java/is/xyz/mpv/MPVView.kt) | `mediacodec,mediacodec-copy`; renderer ve aktif hwdec ayrı seçenekler. | Ayarlar ekranına direct/copy eklendi; değişen motor tercihleri uygulanıyor, değişmemiş seçenekler tekrar yazılmıyor. Fakat bu yol yerel DV passthrough garantisi değildir. |
| [mpv-android P5 sorunu](https://github.com/mpv-android/mpv-android/issues/1081) ve [Android DV libmpv çatalı](https://github.com/Darkaxt/mpv-android-libdovi) | `gpu-next` tek başına, donanım çözücü RPU'yu renderera taşımıyorsa yeşil/mor P5'i düzeltmeyebilir; kopyalı yol için native yamalar gerekebilir. | Sırf `gpu-next` veya force-HW ayarıyla tüm cihazlar için destek varmış gibi davranılmadı. |
| [Nova DV pipeline PR](https://github.com/nova-video-player/aos-avos/pull/7) | Gerçek BL/EL eşleme, RPU ve P010 için kapsamlı FFmpeg/libplacebo ve MediaCodec değişiklikleri gerekir. | Bu kod deposunda bu pipeline yok; başka projeye özgü, cihaz üzerinde doğrulama gerektiren native değişiklikler genel çözüm diye kopyalanmadı. |

### İkinci tur kod değişiklikleri

- MP4/fMP4/TS NAL dönüştürmesi yalnızca açıkça HEVC olan ve DRM bildirmeyen track'lere uygulanır; AVC/AV1 veya bilinmeyen DV codec'leri HEVC NAL ayrıştırıcısına gönderilmez. Ek/encryption sample parçalarının sırası korunarak bu örnekler yeniden yazılmadan geçirilir. MKV şifreli track'lerde format ve sample dönüştürme kapatıldı.
- Seek ve format değişiminde bekleyen sample verisi temizlenir. Aynı anda çalışan extractor'ların JNI çıktı tamponları thread-local'dır.
- HEVC fallback'te uydurma `hvc1.2.4.L153.B0` profili yerine bilinmeyen codec bilgisi bırakılır. Native kütüphane yüklenemezse MP4/TS formatı fallback diye yanlış etiketlenmez.
- Native length-delimited NAL ayrıştırıcısında uzunluk taşması ve artakalan byte nedeniyle kısmi/truncate çıktı engellendi. `scripts/player-tests/run_dovi_bridge_host_test.sh` gerçek JNI fonksiyonunu stub modunda sınar.

### Test durumu

JNI fonksiyonuna ilişkin host C++ testleri çalıştırıldı: normal P7 RPU strip, bozuk kuyruk, büyük NAL uzunluğu, geçersiz length-field ve yetersiz çıktı tamponu geçti. JNI headerları geçici olarak OpenJDK kaynağından alındı. Kotlin/Android Gradle derlemesi, instrumentation ve cihaz üzerinde HDR renk/FEL karşılaştırması hâlâ yapılamadı: Android SDK ve Java çalışma ortamı yoktur. Encrypted-track ve ASS ile gerçek dosya entegrasyonu ayrıca doğrulanmalıdır.
