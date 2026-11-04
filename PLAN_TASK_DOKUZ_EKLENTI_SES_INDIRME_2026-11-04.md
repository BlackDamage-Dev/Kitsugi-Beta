# PLAN / TASK â€” Dokuz eklentiden seÃ§ilen sesi dosyaya indirme

**Tarih:** 4 KasÄ±m 2026
**Depo/dal:** `f26901964-eng/Kitsugi-Beta` / `arena/85719e9b-kitsugi-beta`
**KarÅŸÄ±laÅŸtÄ±rma tabanÄ±:** `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`
**AyrÄ±ntÄ±lÄ± kaynak envanteri:** [`audio-export-provider-audit.md`](audio-export-provider-audit.md)

## Ä°stek ve kabul Ã¶lÃ§Ã¼tÃ¼

KullanÄ±cÄ±, **etkin dokuz Kitsugi eklentisinin** sunduÄŸu video kaynaklarÄ±ndan, videoyu saÄŸlayan hosttan baÄŸÄ±msÄ±z olarak **seÃ§ilen ses parÃ§asÄ±nÄ± doÄŸru biÃ§imli bir ses dosyasÄ±** olarak indirmek istiyor. Bir video dosyasÄ±nÄ±n uzantÄ±sÄ±nÄ± `.mp3` yapmak veya yalnÄ±zca yaygÄ±n HLS/MP4 Ã¶rneklerini desteklemek bu isteÄŸi tamamlamaz. SeÃ§ilen dil/parÃ§a gÃ¼venle saptanamÄ±yorsa yanlÄ±ÅŸ sesi yayÄ±nlamak yerine iÅŸlem durmalÄ±dÄ±r.

## YapÄ±lan iÅŸler

| Kimlik | Durum | Ä°ÅŸ / sonuÃ§ |
| --- | --- | --- |
| SES-01 | TamamlandÄ± â€” statik envanter | AnimeciX, Anizium, FilmMakinesi, HDFilmCehennemi, TrAnimeIzle, DiziBox, HDFilmÄ°zle, Anizm, DiziPal iÃ§in yayÄ±mlanan katalog, etkin liste ve Ã§Ã¶zÃ¼cÃ¼ zincirleri karÅŸÄ±laÅŸtÄ±rÄ±ldÄ±. Anizium'un Ã¶nceden derlenmiÅŸ saÄŸlayÄ±cÄ±sÄ± nedeniyle canlÄ± biÃ§im envanteri kanÄ±tlanmÄ±ÅŸ deÄŸildir. |
| SES-02 | KodlandÄ± | CloudStream `ExtractorLink` tÃ¼rÃ¼, ses URL'leri ve her ayrÄ± ses URL'sinin kendi HTTP baÅŸlÄ±klarÄ± `StreamSource` Ã¼zerinden indirme yoluna taÅŸÄ±ndÄ±. AynÄ± URL'li fakat farklÄ± kimlik bilgili kaynaklarda tam saÄŸlayÄ±cÄ± indeksi korunuyor. |
| SES-03 | KodlandÄ± | Ses sayfasÄ±na parÃ§a baÅŸÄ±na ve oynatÄ±cÄ± satÄ±rlarÄ±nda bulunmayan saÄŸlayÄ±cÄ± sesleri iÃ§in indirme eylemleri eklendi. Android 8â€“9 depolama izni, kaynak deÄŸiÅŸiminde eski seÃ§imi reddetme, WorkManager foreground gÃ¶revi, ilerleme/iptal bildirimi ve `Downloads/Kitsugi/Audio` kaydÄ± eklendi. |
| SES-04 | KodlandÄ±; cihaz testi gerekli | Video/ses konteynerlerinden seÃ§ili ses `MediaExtractor`/`MediaMuxer` ile Ã§Ä±karÄ±lÄ±yor; AAC iÃ§in M4A, Opus/Vorbis iÃ§in WebM, uygun ham kareler iÃ§in MP3/AC3/EAC3, cihaz Ã§Ã¶zÃ¼cÃ¼sÃ¼ gerekiyorsa PCM16 WAV, doÄŸrulanan baÄŸÄ±msÄ±z FLAC/WAV iÃ§in Ã¶zgÃ¼n dosya kullanÄ±lÄ±yor. Birden fazla fiziksel parÃ§ada dil/kimlik belirsizse tahmin edilmiyor. |
| SES-05 | KodlandÄ±; canlÄ± kaynak testi gerekli | Sonlu HLS'de ses rendition grubu/dil/ad, segment/byte-range, `EXT-X-MAP`, AES-128 anahtarÄ± ve IV iÅŸleniyor; segmentlerden sonra ayrÄ±ca ses demux/remux yapÄ±lÄ±yor. BaÅŸlÄ±ÄŸÄ± `VIDEO` olsa da uzantÄ±sÄ±z veya `master.txt` manifesti tek HTTP yanÄ±tÄ±nda tanÄ±nabiliyor. |
| SES-06 | KodlandÄ±; canlÄ± kaynak testi gerekli | Statik, tek dÃ¶nemli DASH'de ses AdaptationSet/Representation seÃ§imi, SegmentTemplate/Timeline/List, byte-range ve tam baÄŸÄ±msÄ±z dosyalÄ± SegmentBase iÅŸleniyor. Belirsiz Ã§oklu temsil, DRM, canlÄ± ve Ã§ok dÃ¶nemli MPD reddediliyor. |
| SES-07 | KodlandÄ±; canlÄ± kaynak testi gerekli | FLV AAC Main/LC/SSR ses etiketleri ADTS AAC'a, MP3 ses etiketleri MP3'e Ã§Ä±karÄ±lÄ±yor; video/script etiketleri atÄ±lÄ±yor. DiÄŸer FLV kodeklerinde native Ã§Ä±karma deneniyor, garanti verilmiyor. |
| SES-08 | DoÄŸrulandÄ± â€” sentetik/JVM | **35/35** yerel ses birim testi geÃ§ti. HLS AES-128 rendition, DASH ÅŸablon ve tam dosya SegmentBase, HTTP redirect/range/eksik yanÄ±t, uzantÄ±sÄ±z HLS/DASH ile FLV'den tek GET Ã¼zerinden ses elde edilmesi sahte HTTP'yle sÄ±nandÄ±. `git diff --check`, Kotlin sÃ¶zdizimi ve Android XML denetimleri geÃ§ti. |
| SES-09 | DoÄŸrulandÄ± â€” CI | [Android Actions #41870051589](https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41870051589), kod SHA `ff52e3c5`: seÃ§ili `:app:testFossDebugUnitTest`, FOSS debug/release APK derlemeleri ve her iki APK iÃ§in DEX doÄŸrulamasÄ± **baÅŸarÄ±lÄ±; VerifyError 0**. `kitsugi-foss-apks` artifact'i bu Ã§alÄ±ÅŸtÄ±rmada bulunuyor. Bu sonuÃ§ gerÃ§ek cihaz veya canlÄ± provider indirmesi deÄŸildir. |
| SES-10 | AÃ§Ä±k â€” gerÃ§ek ortam | Android cihazda her eklentiden temsilÃ® bÃ¶lÃ¼m ve her fiilÃ® host/rendition iÃ§in indirme, doÄŸru parÃ§a/dil, aÃ§Ä±labilir dosya, oturum/cookie sÃ¼resi ve depolama/izin kontrolleri yapÄ±lmalÄ±. YalnÄ±zca statik kod envanteriyle 'her hostta sorunsuz' hÃ¼kmÃ¼ verilemez. |
| SES-11 | AÃ§Ä±k â€” ileri kapsam | DRM/SAMPLE-AES, canlÄ±/kesintili HLS, canlÄ±/Ã§ok dÃ¶nemli DASH, ayrÄ± dosyaya indirgenemeyen indeksli DASH, RTSP/RTMP/SmoothStreaming, torrent/magnet, desteklenmeyen kodekler ve sÃ¼reli kimlik doÄŸrulamasÄ±na iliÅŸkin ayrÄ± teknik Ã§Ã¶zÃ¼m ve cihaz doÄŸrulamasÄ± gerekir. |

## DoÄŸrulama ve teslim notu

- **GerÃ§ekleÅŸtirilen:** Host adÄ±na sabitlenmeyen ortak ses indirme mimarisi; seÃ§ili parÃ§a kimliÄŸi, doÄŸru dosya biÃ§imi ve belirsizlikte gÃ¼venli ret; yerel testler ve tam FOSS debug/release CI derlemesi.
- **GerÃ§ekleÅŸtirildiÄŸi iddia edilmeyen:** Dokuz eklentinin gÃ¼ncel bÃ¼tÃ¼n canlÄ± video hostlarÄ±nda koÅŸulsuz baÅŸarÄ±lÄ± indirme. Anizium kapalÄ±/prebuilt, DRM ve sÃ¼resi dolmuÅŸ eriÅŸim gibi sÄ±nÄ±rlar ayrÄ±ca korunuyor.
- **Paketleme:** Bu plan/task belgesi ile taban commit'e gÃ¶re deÄŸiÅŸen/yeni kaynak, kaynak yapÄ±landÄ±rmasÄ±, test ve denetim dosyalarÄ± **orijinal repo yollarÄ±yla** ZIP'e konur. ZIP iÃ§indeki `ZIP_MANIFEST.txt` dosya sayÄ±sÄ±nÄ±, taban/son commit'i ve SHA-256 Ã¶zetlerini gÃ¶sterir. ZIP tam depo veya APK deÄŸildir.
