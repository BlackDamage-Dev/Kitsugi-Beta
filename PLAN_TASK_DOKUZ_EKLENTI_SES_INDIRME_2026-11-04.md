# PLAN / TASK — Dokuz eklentiden seçilen sesi dosyaya indirme

**Tarih:** 4 Kasım 2026
**Depo/dal:** `f26901964-eng/Kitsugi-Beta` / `arena/85719e9b-kitsugi-beta`
**Karşılaştırma tabanı:** `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`
**Ayrıntılı kaynak envanteri:** [`audio-export-provider-audit.md`](audio-export-provider-audit.md)

## İstek ve kabul ölçütü

Kullanıcı, **etkin dokuz Kitsugi eklentisinin** sunduğu video kaynaklarından, videoyu sağlayan hosttan bağımsız olarak **seçilen ses parçasını doğru biçimli bir ses dosyası** olarak indirmek istiyor. Bir video dosyasının uzantısını `.mp3` yapmak veya yalnızca yaygın HLS/MP4 örneklerini desteklemek bu isteği tamamlamaz. Seçilen dil/parça güvenle saptanamıyorsa yanlış sesi yayınlamak yerine işlem durmalıdır.

## Yapılan işler

| Kimlik | Durum | İş / sonuç |
| --- | --- | --- |
| SES-01 | Tamamlandı — statik envanter | AnimeciX, Anizium, FilmMakinesi, HDFilmCehennemi, TrAnimeIzle, DiziBox, HDFilmİzle, Anizm, DiziPal için yayımlanan katalog, etkin liste ve çözücü zincirleri karşılaştırıldı. Anizium'un önceden derlenmiş sağlayıcısı nedeniyle canlı biçim envanteri kanıtlanmış değildir. |
| SES-02 | Kodlandı | CloudStream `ExtractorLink` türü, ses URL'leri ve her ayrı ses URL'sinin kendi HTTP başlıkları `StreamSource` üzerinden indirme yoluna taşındı. Aynı URL'li fakat farklı kimlik bilgili kaynaklarda tam sağlayıcı indeksi korunuyor. |
| SES-03 | Kodlandı | Ses sayfasına parça başına ve oynatıcı satırlarında bulunmayan sağlayıcı sesleri için indirme eylemleri eklendi. Android 8–9 depolama izni, kaynak değişiminde eski seçimi reddetme, WorkManager foreground görevi, ilerleme/iptal bildirimi ve `Downloads/Kitsugi/Audio` kaydı eklendi. |
| SES-04 | Kodlandı; cihaz testi gerekli | Video/ses konteynerlerinden seçili ses `MediaExtractor`/`MediaMuxer` ile çıkarılıyor; AAC için M4A, Opus/Vorbis için WebM, uygun ham kareler için MP3/AC3/EAC3, cihaz çözücüsü gerekiyorsa PCM16 WAV, doğrulanan bağımsız FLAC/WAV için özgün dosya kullanılıyor. Birden fazla fiziksel parçada dil/kimlik belirsizse tahmin edilmiyor. |
| SES-05 | Kodlandı; canlı kaynak testi gerekli | Sonlu HLS'de ses rendition grubu/dil/ad, segment/byte-range, `EXT-X-MAP`, AES-128 anahtarı ve IV işleniyor; segmentlerden sonra ayrıca ses demux/remux yapılıyor. Başlığı `VIDEO` olsa da uzantısız veya `master.txt` manifesti tek HTTP yanıtında tanınabiliyor. |
| SES-06 | Kodlandı; canlı kaynak testi gerekli | Statik, tek dönemli DASH'de ses AdaptationSet/Representation seçimi, SegmentTemplate/Timeline/List, byte-range ve tam bağımsız dosyalı SegmentBase işleniyor. Belirsiz çoklu temsil, DRM, canlı ve çok dönemli MPD reddediliyor. |
| SES-07 | Kodlandı; canlı kaynak testi gerekli | FLV AAC Main/LC/SSR ses etiketleri ADTS AAC'a, MP3 ses etiketleri MP3'e çıkarılıyor; video/script etiketleri atılıyor. Diğer FLV kodeklerinde native çıkarma deneniyor, garanti verilmiyor. |
| SES-08 | Doğrulandı — sentetik/JVM | **35/35** yerel ses birim testi geçti. HLS AES-128 rendition, DASH şablon ve tam dosya SegmentBase, HTTP redirect/range/eksik yanıt, uzantısız HLS/DASH ile FLV'den tek GET üzerinden ses elde edilmesi sahte HTTP'yle sınandı. `git diff --check`, Kotlin sözdizimi ve Android XML denetimleri geçti. |
| SES-09 | Doğrulandı — CI | [Android Actions #41870051589](https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41870051589), kod SHA `ff52e3c5`: seçili `:app:testFossDebugUnitTest`, FOSS debug/release APK derlemeleri ve her iki APK için DEX doğrulaması **başarılı; VerifyError 0**. `kitsugi-foss-apks` artifact'i bu çalıştırmada bulunuyor. Bu sonuç gerçek cihaz veya canlı provider indirmesi değildir. |
| SES-10 | Açık — gerçek ortam | Android cihazda her eklentiden temsilî bölüm ve her fiilî host/rendition için indirme, doğru parça/dil, açılabilir dosya, oturum/cookie süresi ve depolama/izin kontrolleri yapılmalı. Yalnızca statik kod envanteriyle 'her hostta sorunsuz' hükmü verilemez. |
| SES-11 | Açık — ileri kapsam | DRM/SAMPLE-AES, canlı/kesintili HLS, canlı/çok dönemli DASH, ayrı dosyaya indirgenemeyen indeksli DASH, RTSP/RTMP/SmoothStreaming, torrent/magnet, desteklenmeyen kodekler ve süreli kimlik doğrulamasına ilişkin ayrı teknik çözüm ve cihaz doğrulaması gerekir. |

## Doğrulama ve teslim notu

- **Gerçekleştirilen:** Host adına sabitlenmeyen ortak ses indirme mimarisi; seçili parça kimliği, doğru dosya biçimi ve belirsizlikte güvenli ret; yerel testler ve tam FOSS debug/release CI derlemesi.
- **Gerçekleştirildiği iddia edilmeyen:** Dokuz eklentinin güncel bütün canlı video hostlarında koşulsuz başarılı indirme. Anizium kapalı/prebuilt, DRM ve süresi dolmuş erişim gibi sınırlar ayrıca korunuyor.
- **Paketleme:** Bu plan/task belgesi ile taban commit'e göre değişen/yeni kaynak, kaynak yapılandırması, test ve denetim dosyaları **orijinal repo yollarıyla** ZIP'e konur. ZIP içindeki `ZIP_MANIFEST.txt` dosya sayısını, taban/son commit'i ve SHA-256 özetlerini gösterir. ZIP tam depo veya APK değildir.
