# Dolby Vision / HDR — Plan ve görev durumu

## Yapılanlar
- [x] Just+, ExoplayerHdrUtils, Kodi, mpv-android ve Nova yaklaşımları incelendi; kaynak ve sınırlamalar araştırma raporuna yazıldı.
- [x] Media3'te SurfaceView, video çözücü önceliği ve DV7 dışındaki profilleri koruma düzenlemeleri yapıldı.
- [x] ASS ve DV extractor etkileşimi, MKV işlem durumunun oynatma örneğine bağlanması düzenlendi.
- [x] HEVC dışı ve DRM bildirilmiş track'lerin HEVC bit akışı dönüştürmesine girmesi önlendi; seek tamponu ve eşzamanlı native çıktı tamponları düzenlendi.
- [x] MPV'de renderer/hwdec ayar değişikliklerinin uygulanması, tekrarlı filtre yazımlarının önlenmesi ve doğrudan/kopyalı çözücü seçenekleri eklendi.
- [x] Native JNI sample işleyicisi için host testleri eklendi ve çalıştırıldı; `git diff --check` kontrol edildi.

## Kalan görevler
- [ ] Android SDK/JDK ortamında uygulamayı derle; JVM ve Android testlerini çalıştır, olası entegrasyon hatalarını gider.
- [ ] Güvenilir libdovi derleme ve paketleme zinciri kur; ABI'ler ve lisansları doğrula. Şu an dönüşüm derlemesi stub, P7→8.1 gerçek dönüşümü etkin değil.
- [ ] Dönüştürmeden önce gerçek RPU varlığı/dönüşüm başarısını doğrula; BlockAdditional, in-band ve şifreli içerikler için testleri tamamla. P7 FEL dönüşümü enhancement layer'ı atar; tam FEL değildir.
- [ ] MPV native FFmpeg/MediaCodec/libplacebo yolunu ve RPU/P010 metadata aktarımını değerlendir; yükseltme veya yamaları ABI ve lisanslarıyla yeniden üretilebilir biçimde test et.
- [ ] P5, P7 MEL/FEL, P8.1, P10; MKV/MP4/HLS/DASH; HDR10/SDR/DV ekranlar ve ses formatlarıyla seek, altyazı, ayar değişimi ve motor geçişlerini gerçek cihazlarda ölç.
- [ ] Yeşil/mor görüntüyü ve performansı referans kare, gerçek aktif decoder, düşen kareler ve günlüklerle karşılaştır; desteklenmeyen yolları kullanıcıya doğru bildir.
- [ ] ASS gömülü fontları, PixelCopy ekran görüntüsü, DRM, PiP ve küçük oynatıcı regresyonlarını doğrula.

## Dürüst durum
Bütün cihazlarda sorunsuz oynatma veya tüm DV profilleri için destek doğrulanmış değildir. Android/cihaz testleri yapılmadı. Ayrıntılı kaynak karşılaştırması: `DOLBY_VISION_ARASTIRMA_2026-11-04.md`.
