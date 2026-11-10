# AniSub.co Altyazı Eklentisi — Kök Neden ve Çözüm Raporu (2026-10-23)

## Şikâyet
AniSub.co eklentisi listede görünüyor ve açık, ancak hiçbir içerikte Türkçe
altyazı getirmiyor. AniSub'da altyazıların çoğu **Paket** (ZIP/RAR içinde
bölüm bölüm .ass/.srt) hâlindedir; uygulama paketi indirip açmalı ve
oynatılan bölüme uyan dosyayı göstermelidir.

## Canlı doğrulama ile bulunan gerçekler (AniSub.co, 2026-10-23)
- `GET /manifest.json` çalışıyor: AniSub'un resmî Stremio manifesti var.
- `GET /subtitles/series/tt2560140:1:1.json` (Shingeki no Kyojin) altyazı dönüyor.
  **Ancak** aynı cevap `:1:2` ve hatta `:2:1` için de dönüyor ve
  `/stremio/subtitle/1109` her durumda paketin **1. bölüm** dosyasını veriyor.
  Yani resmî Stremio indirme ucu paketlerde bölümden bağımsız ve düz
  Stremio akışıyla doğru bölüm alınamaz.
- Stremio ucu yalnızca IMDb (`tt`) kimliğini tanıyor; `kitsu:`, `anilist:`,
  `mal:` kimlikleri boş dönüyor.
- `GET /api/subtitles?q=...&per_page=50` çalışan resmî arama API'sidir.
  Kayıtlar `subtitle_anilist_id`, `media_info`, `content_type`
  ("Paket" / "Bölüm: 12"), `episode_description` ("1-3") taşır.
  AniList ID ile doğrudan filtre parametresi yoktur (denendi).
- `GET /api/subtitle/{id}/archive` ZIP paketlerin içeriğini listeler ve
  `GET /api/subtitle/{id}/archive/download?file={path}` paketin içindeki
  **tek bölümlük dosyayı** doğrudan verir (Bölüm 02 ile doğrulandı).
- Arşiv API'si RAR paketlerde "Desteklenmeyen arşiv formatı" döner;
  örn. ID 1703 `.rar` paketidir. RAR bu yüzden cihazda açılmalıdır.
- `GET /api/subtitles/{id}/download` gerçek CDN adresini verir; tek
  dosyalık kayıtlarda bu adres doğrudan `.ass/.srt` dosyasıdır.

## Eski kod neden hiç çalışmıyordu?
1. Uygulama, AniSub'un resmî API/Stremio uçlarını kullanmak yerine
   Inertia (Laravel) sayfasını (`/tum-altyazilar`) kazıyordu. OkHttp
   istemcisinde CookieJar yoktu ve DTO alanları (`mediaInfo` camelCase)
   güncel cevap biçimiyle uyumsuzdu; başlık filtresi boş alanlar
   yüzünden adayların tamamını eliyordu.
2. Arşiv açıcı yalnızca `ZipInputStream` kullanıyordu:
   - RAR paketler (yaygın) açılamıyordu.
   - Tek dosyalık `.ass/.srt` indirmeleri de ZIP sanılıp açılmaya
     çalışıldığı için boş dönüyordu.
3. Paket açılsa bile sonuç `file:///...` URL'si olarak dönüyordu;
   `SubtitleFileCache.cacheSubtitle` bu adresi OkHttp ile indirmeye
   çalıştığı için istisna fırlatıyor, oyuncu altyazıyı sessizce
   düşürüyordu ("Altyazı indirilemedi").
4. Uzantısız URL'lerde biçim, varsayılan olarak `srt` sanılıyordu;
   ASS dosyaları yanlış çözümleyiciye gidiyordu.
5. Bölüm eşleşmesi bulunamazsa paketteki **ilk Dosya** dönüyordu
   (yanlış bölümün altyazısı).
6. IMDb/başlık yoksa (örn. sadece kitsu kimliği) Arama hiç yapılmıyordu.

## Yapılan çözüm

Yeni paket: `data/remote/anisub/`
- `AniSubSubtitleClient` — Keşif + çözümleme + önbellek.
  - Keşif: IMDb varsa resmî Stremio ucundan, ayrıca başlıkla
    `/api/subtitles?q=` aramasından (başlık varyantları + en çok 2 sayfa).
  - Kimlik: AniList ID tam eşleşmesi öncelikli; yoksa başlık benzerliği.
    Stremio adayları sezon doğruluğu için detay API'siyle tamamlanır.
  - Bölüm çözümü sırası:
    1. Cihazda daha önce açılmış paket (`cacheDir/anisub_subs/{id}`)
    2. Sunucu arşiv API'sinden bölüme uyan tek Dosya (ZIP için hızlı yol)
    3. Tek dosyalık kayıtta CDN adresinin kendisi
    4. Paketin tamamını indirip cihazda açma (ZIP/RAR/GZIP/düz metin,
       magic-byte tanıma) ve bölüme uyan dosyayı `file://` döndürme
    5. Son çare olarak `/stremio/subtitle/{id}` (yalnızca film/1. bölüm)
  - Yanlış bölüm asla dönmez: çok dosyalı pakette eşleşme yoksa aday elenir.
  - Dil: Türkçe aday varsa yalnız Türkçe; dil adları oyuncunun sevdiği
    etiketlere çevrilir ("Türkçe" → "Turkish").
  - Oyuncunun paralel 4-6 sorgusu kanonik anahtarla (AniList>başlık>IMDb)
    tek iş + 10 dk sonuç önbelleği ile birleştirilir.
- `AniSubEpisodeMatcher` — Dosya adı/kayıt metasından bölüm çıkarma:
  SxxEyy, NxNN, "Ep/Bölüm NN", "- NN", "[NN]", başlık sayıları ayıklaması,
  12.5 ara bölüm koruması, "Paket"/"Bölüm: NN"/"1-13" meta uygunluğu,
  başlık benzerliği ve Arama varyantları. Birim testli.
- `SubtitleArchiveExtractor` — ZIP (UTF-8 → windows-1254 → Shift_JIS
  karakter kodu zinciri), RAR (Junrar), GZIP; boyut/No sınırları ve
  yol düzleştirme (zip-slip koruması).

Diğer değişiklikler:
- `AddonStreamClient`: eski AniSub kazıma akışı (348 satır) tamamen
  silindi; AniSub delegasyonu yeni istemciye bağlandı. Manifest artık
  sahte değil: gerçek manifest okunur (ağ hatasında ihtiyati tanım),
  `logo` alanı icon olarak kullanılır. `SubtitleResponseItem.format`
  eklendi (Addon'un beyan ettiği biçim).
- `SubtitleRepositoryImpl`: beyan edilen format, URL tahmininden önce gelir.
- `SubtitleFileCache`: `file://` ve mutlak yerel yolları indirmeden kabul
  eder (RAR/ZIP yerel açılışlarının oyuncuya ulaşması için şarttır).
- `AnimeDownloader`: çevrimdışı indirmede `file://` altyazıları videonun
  yanına kopyalar.
- `app/build.gradle.kts`: `com.github.junrar:junrar:7.5.5` eklendi.
- CI (`android-build.yml`): AniSub birim testleri de çalıştırılıyor.

Testler:
- `AniSubEpisodeMatcherTest` — gerçek fansub Dosya adları, paket seçimi,
  meta uygunluğu, başlık eşleştirme.
- `SubtitleArchiveExtractorTest` — magic-byte tanıma, yol temizleme.

## Kalan sınırlar
- 7z paketler cihazda açılamıyor; meydana gelirse film/1. bölümde son
  çare Stremio ucu kullanılır, diğer bölümlerde sonuç üretilmez.
- Bölüm numaralandırması söz konusu AniList kaydındaki numaralandırmayla
  uyumlu kabul edilir (mutlak numaralandırmalı paketlerde ayrı eşleştirme
  gerekebilir).
- AniSub, AniList ID ile Arama sunmadığından, başlığı bilinmeyen ama
  AniList ID'si olan başlıksız çağrılarda yalnızca IMDb tabanlı keşif çalışır.
