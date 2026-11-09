# Altyazı canlı sözleşme kontrolü

`python3 scripts/subtitle-tests/live_probe.py` genel örnek film/dizi kimlikleriyle
OpenSubtitles v3, YTS ve AltyazıDB manifest/sorgu/dosya yanıtlarını ve AniSub
API/örnek paketini kontrol eder. Kurulu başka Stremio kaynakları da
`--manifest 'https://.../manifest.json'` ile eklenebilir (birden çok kez).
Çıktıda manifest adresleri veya özel sorgu parametreleri yazdırılmaz. Canlı servis
kesintileri build hatası sayılmaz; çıktı ayrıca incelenmelidir. Test video dosyası
veya kullanıcı kimlik bilgisi göndermez. Sonucun var olması, belirli bir video
sürümüyle zamanlama uyumluluğunu garanti etmez.

## 2026-11-09 gözlemleri

- OpenSubtitles v3 ve AltyazıDB, `tt0111161` filmi ve `tt0903747:1:1`
  için altyazı listesi döndürdü; ikisinden de birer Türkçe SRT açıldı.
  AltyazıDB `tmdb:278` ve `tmdb:1396:1:1` için de sonuç verdi.
- AniSub manifest ve açık API erişilebilir. API'de `media_info`, `content_type`,
  `episode_description` ve sayfalama kullanılıyor. `tt13293588:1:1`, `:1:2`
  ve `:1:11` sorgularında aynı paket (`anisub-3605`) URL'si döndü; bu URL'nin
  sunduğu ASS dosyasında video dosyası 12. bölüm olarak belirtilmişti. Bu nedenle
  paketlerde Stremio URL'si rastgele bölüm seçebilir; uygulama ZIP'te eşleşen
  bölüm dosyasını arar, eşleşme yoksa paketi sunmaz.
- Örnek `3607` bir RAR indirmesi; Stremio dosya ucu kontrol sırasında HTTP 500
  verdi. Açık indirme API'sinden alınan gerçek arşiv GitHub Linux runner'ında
  libarchive ile açıldı: tek dosya `FX 06.ass`, tespit edilen içerik ASS.
  Android'de libarchive JNI ile RAR4/RAR5 okuma ve ZIP ile aynı güvenli bölüm
  seçimi eklendi. Gerçek `3607` RAR dosyası Android 10 x86_64 emülatöründe
  üretimdeki kodla çıkarıldı; ASS içeriği ve 6. bölüm eşleşmesi doğrulandı.
  Şifreli, çok parçalı ve kütüphanenin desteklemediği RAR'lar atlanır.
- Varsayılan `yts-subtitles.strem.io` manifest kontrolü hem fetch aracında hem
  GitHub runner'ında URLError ile başarısız oldu. Google DNS'te bu alt alan adı
  için A veya AAAA kaydı dönmüyor. Ayrı YIFY eklentisinin Türkçe sorguları iki
  film için sonuç verdi ancak bağlantıları Stremio'nun `127.0.0.1:11470` yerel
  proxy'sine yönleniyor; Kitsugi bu proxy'ye sahip değil. Asıl ZIP indirme uçları
  iki örnekte HTTP 500 döndürdü; bazı istekler de HTTP 504 verdi. Bu nedenle
  henüz çalıştığı doğrulanmamış alternatifle otomatik değiştirme yapılmadı. Eski
  `opensubtitles.strem.io/manifest.json` kontrolü JSON manifest yerine HTML
  kurulum sayfası döndürdü; uygulama bu yanıtı eklenti manifesti olarak kullanamaz.
- Projede yalnızca dört varsayılan kaynak bulunuyor. Kullanıcının cihazında
  kurulu diğer kaynakların manifest adresleri burada yoktur; bu nedenle o
  kaynakların sağlığı ve kimlik/format gereksinimleri bu rapordan çıkarılamaz.

- Android emülatöründe gerçek RAR testi, libarchive-android 1.1.6'nın heap
  ByteBuffer kullanımı sonrasında `Archive.free` içinde silinmiş JNI yerel
  referansa erişip SIGABRT ile çöktüğünü gösterdi. Kod direct ByteBuffer ve
  arşiv kapanana kadar güçlü referans kullanacak şekilde düzeltildi. Yeniden
  test girişimleri GitHub Actions genel kesintisine denk geldi; doğrudan tampon
  düzeltmesi GitHub Actions kesintisi sonrasında aynı gerçek arşivle Android
  emülatöründe başarıyla doğrulandı (çalışma 42673913523).

## Ek sonuçlar: kaynak, karakter kodlaması ve emülatör

- Türkçe yapılandırılmış SubSense alternatifinde `tt0111161` için 25 ve
  `tt0903747:1:1` için 9 Türkçe sonuç geldi; birer dosya HTTP 200 ile indirildi.
  İki örneğin ham baytları geçerli UTF-8 değildi; Windows-1254 çözümlemesinde
  sırasıyla 5070 ve 1372 Türkçe karakter bulundu. Bu alternatif ayrı bir
  sağlayıcıdır; YTS'nin kendisi olarak etiketlenmez veya otomatik kurulmaz.
- Genel altyazı önbelleği ve çevrimdışı indirme, geçerli UTF-8'i değiştirmeden
  korur; BOM'lu UTF-16 ve dil/kaynak kodlamasıyla eski metinleri UTF-8'e
  dönüştürür. Eski önbellekler de yeniden değerlendirilir. NUL içeren BOM'suz
  ikili dosyalar karakter kodlaması dönüşümünden geçirilmez.
- `95005.srt` gerçek dosyası Android 10 emülatörüne geçici indirilip uygulama
  koduyla çözüldü; Türkçe ifade ve bozuk karakter yokluğu kontrol edildi.
  Bu test ve gerçek AniSub RAR testi aynı çalışmada başarılı oldu: 42678740031.
- OpenSubtitles v3 aynı kontrol sırasında Türkçe dizi dosyasını HTTP 200 ile
  döndürürken, seçilen film dosyası HTTP 520 verdi. Önceki denemede aynı film
  için dosya indirilebilmişti; bu nedenle sağlayıcı tarafında değişken hata
  görülebilir, tüm dosyaların her an erişilebilir olduğu varsayılmaz.

## Catalog identity and episode investigation (2026-11-09; incomplete)

The seven catalogs are not seven subtitle addons. Frieren season 1 is a real
cross-source fixture: MAL 52991, AniList 154587, Kitsu 46474, Simkl 1990194,
TMDB TV 209867, IMDb tt22248376, Shikimori 52991 (its API explicitly exposes
`myanimelist_id`), and Bangumi subject 400602. ARM's MAL, AniList and Kitsu
lookups returned the same first six IDs; the Bangumi subject was checked on
Bangumi and Simkl's public page identifies its entry. This is evidence about
catalog records, not a test of the application's end-to-end launch paths.

Sources:
- https://arm.haglund.dev/api/v2/ids?source=myanimelist&id=52991
- https://arm.haglund.dev/api/v2/ids?source=anilist&id=154587
- https://arm.haglund.dev/api/v2/ids?source=kitsu&id=46474
- https://shikimori.one/api/animes/52991
- https://kitsu.io/api/edge/anime/46474
- https://bgm.tv/subject/400602
- https://simkl.com/anime/1990194/sousou-no-frieren

A critical counterexample is Attack on Titan season 2: ARM's MAL 25777 maps
AniList 20958 and Kitsu 8671 to IMDb tt2560140, TMDB 1429, TMDB season 2.
The anime catalog episode 1 must not be blindly queried as IMDb season 1
instead of season 2. OpenSubtitles returned different S01E01 and S02E01 result
sets on real queries. Its S01E01 response also included at least one file named
as episode 06 despite the returned metadata saying episode 1: provider result
metadata itself cannot prove correctness or synchronization.

- https://arm.haglund.dev/api/v2/ids?source=myanimelist&id=25777
- https://opensubtitles-v3.strem.io/subtitles/series/tt2560140:1:1.json
- https://opensubtitles-v3.strem.io/subtitles/series/tt2560140:2:1.json

ARM's TMDB 209867 response also lists Frieren's first three AniList records
with `themoviedb-season` 1 while their `thetvdb-season` values are 1, 2 and 3.
Do not assume the first cross-reference or even a single season field is an
infallible mapping. Episode offsets, split seasons, specials, remakes, dubbing
and release timing require additional evidence.

Current gaps: `SubtitleQueryPlanner` still uses the same season/episode for
IMDb and TMDB and assumes the episode is valid for a Kitsu work. The resolver
may choose the first anime relation for a TMDB show; Simkl and Shikimori native
IDs are lost if detail enrichment cannot resolve MAL/TMDB first. The TV and
phone player now retain Bangumi, and the phone path retains an already known
IMDb ID, but those changes alone do not establish correct multi-season mapping.
External-player direct launch bypasses the built-in automatic subtitle search.
No seven-catalog end-to-end Android playback/sync test has been completed.

Further real counterexamples (2026-11-09): AniZip maps MAL 25777 episode 1 to
TVDB season 2 episode 1, and AniList 182255 episode 1 to TVDB 2x01. However,
Fribb's data currently maps the latter to TMDB 1x29, whereas TMDB's own season
2 page lists that episode as 2x01. AltyaziDB currently returned an S01E29 pack
for `tmdb:209867:1:29`, while its S02E01 query was empty. This demonstrates
that even independent mapping feeds and add-on catalogs may be stale or use
different numbering; do not blindly equate TVDB, IMDb, TMDB and an add-on's
interpretation of a TMDB ID. Per-provider and per-episode verification remains
unimplemented.

- https://api.ani.zip/mappings?mal_id=25777
- https://api.ani.zip/mappings?anilist_id=182255
- https://www.themoviedb.org/tv/209867/season/2
- https://github.com/Fribb/anime-lists
- https://altyazidb.online/subtitles/series/tmdb:209867:1:29.json
- https://altyazidb.online/subtitles/series/tmdb:209867:2:1.json
