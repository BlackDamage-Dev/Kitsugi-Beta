# Arama Ekranındaki Sahte Hata Rozetleri — Kök Neden ve Çözüm Raporu

**Tarih:** 2026-10-22
**Sürüm:** v2.4.340 (beta)
**Kullanıcı raporu:** "Ayrıca bu hatalar niye geliyor? Kitsu sorunsuz çalışıyor gibi görünüyor; kontrolleri sağlayıp uygun şekilde düzelt."
**Belirti:** "kaiju no 8" aramasında AniList, MAL, Shikimori, Kitsu ve Simkl rafları sorunsuz doluyor; buna rağmen sonuçların üstünde

- `KTS-COM-1009 · Kayıt bulunamadı (HTTP 404)`
- `KTS-KIT-4001 · Kitsu API adresi değişti`

rozetleri görünüyor.

---

## 1. Kök neden

Kitsu API'si sağlıklıydı. Rozetler **API hatalarından değil, poster/görsel CDN'lerinden** geliyordu. Üç ayrı hata zinciri birleşmişti:

### 1.1 Görsel isteklerinin platform API hatası sayılması (asıl neden)

- Coil görsel yükleyici, `KitsugiApplication.buildImageLoader()` içinde
  `OkHttpNetworkFetcherFactory(callFactory = { KitsugiHttpClient.client })` kullanıyor.
  Yani **her poster/kapak isteği** `KitsugiHttpClient` üzerinden gidiyor.
- Aynı istemcide `PlatformFailureInterceptor` var ve **her 4xx/5xx** yanıtını
  `PlatformFailureRegistry.reportHttp()` ile kaydediyor.
- `ListPlatform.fromHost()` alan adı **son ekiyle** eşleşiyor:

  | Host | Eşleştiği platform |
  |---|---|
  | `media.kitsu.app`, `media.kitsu.io` | KITSU |
  | `s4.anilist.co` | ANILIST |
  | `image.tmdb.org` | TMDB |
  | `cdn.myanimelist.net` | MAL |
  | `simkl.in` | SIMKL |

  Yani süresi geçmiş/bozuk **tek bir poster 404'ü**, kaynağın API'si komple çöküyormuş gibi kaydediliyordu.
  Ekranda sonuçlar göründüğü hâlde rozet basılmasının nedeni tam olarak buydu.

### 1.2 Kitsu sınıflandırmasında "host adı geçiyor" yanılgısı

`PlatformErrorCatalog.platformQuirk()` içinde Kitsu kolu şöyleydi:

```kotlin
t.contains("kitsu.app") || t.contains("kitsu.io") || (t.contains("host") && ...) -> KIT_4001
```

Ağ katmanı ham nedeni **her zaman** `"HTTP <kod> · <host><yol>"` biçiminde yazdığı için
(`HTTP 404 · media.kitsu.app/anime/poster_images/.../original.jpg`) bu koşul **her Kitsu hatasında** tutuyordu.
Sonuç: 404, 429 hatta 503 bile kullanıcıya "Kitsu API adresi değişti" olarak gösteriliyordu — ne hatalı ne de
kullanıcının yapabileceği bir şey olan bir mesaj.

### 1.3 Rozetlerin sonuç durumundan bağımsız gösterilmesi

`SearchViewModel`, arama bitince `PlatformFailureRegistry.failuresSince(searchStartedAt)` haritasını
**olduğu gibi** `sourceErrorCodes` alanına yazıp ekrana basıyordu. Harita süreç geneli olduğu için:

- rafı başarıyla dolan kaynağın yedek-host/sayfa sondası istekleri,
- aynı saniyelerde çalışan bildirim/takvim arka plan istekleri,
- görsel 404'leri

"bu arama başarısız oldu" gibi görünüyordu. Kullanıcı ise aynı ekranda o kaynağın sonuçlarını görüyordu → çelişki.

---

## 2. Yapılan düzeltmeler

### 2.1 `PlatformFailureRegistry` — CDN istekleri artık kaydedilmiyor

Yeni `isMediaAssetRequest(host, path)`:

- Bilinen medya host'ları: `image.tmdb.org`, `api-cdn.myanimelist.net`, `cdn.myanimelist.net`,
  `media.kitsu.app`, `media.kitsu.io`, `simkl.in`, `img.anili.st`, `lain.bgm.tv`
- Ön ekten anlaşılan CDN'ler: `media.`, `img*`, `cdn.`, `static.`, `images.`, `image.`, `assets.`
- Numaralı AniList CDN'i: `^s\d+\.anilist\.co$` (`s4.anilist.co` …)
- Statik dosya uzantıları: `.jpg .jpeg .png .webp .gif .avif .bmp .svg .ico .mp4 .webm .m3u8 .mpd .vtt .srt .ass`

`reportHttp()` bu istekleri **hiç kaydetmez**; API yolları (uzantısız `/graphql`, `/v4/anime/1`, `/api/edge/...`)
etkilenmez.

Ek güvence: `PlatformFailureInterceptor`, `Accept: image/*` / `video/*` / `audio/*` gönderen istekleri de
kaydetmez (uzantısız CDN adresleri için).

### 2.2 `PlatformErrorCatalog` — KIT_4001 yalnızca gerçek host taşınmasında

- `isKitsuHostMigrationText()` eklendi: taşınma/yönlendirme ifadeleri (`host changed`, `api adresi değiş`,
  `adres değiş`, `domain değiş`, `moved permanently`, `yönlendir`) **veya** eski host'a ulaşılamaması
  (`UnknownHost`, `unable to resolve`, `no address associated with hostname`, `SSLHandshake`, `certificate`).
- KIT_4001 artık yalnızca `http == null` / `301` / `308` iken döner.
- Kitsu 404 → ortak eşleme → `KTS-COM-1009` (Kayıt bulunamadı), 5xx → `KTS-COM-1007` (Sunucu hatası),
  429 → `KTS-KIT-4002` (Kitsu hız sınırı). Yanıltıcı "adres değişti" mesajı ortadan kalktı.

### 2.3 `SearchViewModel` — rozet yalnızca gerçekten boş kalan kaynak için

Yeni `visibleSourceErrorCodes(state, sinceMillis)`:

- **"Tümü" (raf) araması:** `planAllSourcesForState()` ile planlanan kaynaklardan **sonuç üretmiş** olanlar
  "sağlıklı" sayılır ve rozetleri süzülür. Rozet yalnızca planlandığı hâlde **boş kalan** kaynak için kalır.
- **Tek kaynak sekmesi:** rozet yalnızca o kaynağın sonucu hiç yoksa gösterilir.
- Karakter/Personel kapsamlarında da sonuç varsa rozet gösterilmez.

### 2.4 `PlatformErrorCodeChip` — rozet artık kaynak adını da yazıyor

`KTS-COM-…` gibi genel kodlarda hangi kaynağın başarısız olduğu belirsizdi. Rozet artık
`Kitsu · Kitsu hız sınırı` / `AniList · Kayıt bulunamadı (HTTP 404)` biçiminde kaynak adıyla gösteriliyor
(`platform` parametresi opsiyonel; diğer çağrı yerleri değişmedi).

---

## 3. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/platform/PlatformFailureRegistry.kt` | `isMediaAssetRequest()` + CDN/uzantı filtreleri |
| `core/network/PlatformFailureInterceptor.kt` | Medya `Accept` başlıklı istekler kaydedilmez |
| `data/platform/PlatformErrorCatalog.kt` | `isKitsuHostMigrationText()` + Kitsu sınıflandırma sırası |
| `ui/screens/search/SearchViewModel.kt` | `visibleSourceErrorCodes()` süzgeci |
| `ui/screens/search/SearchScreen.kt` | Rozetlere kaynak eşlemesi aktarımı |
| `ui/components/PlatformErrorCodeChip.kt` | Opsiyonel `platform` etiketi |
| `data/platform/PlatformFailureRegistryTest.kt` | 5 yeni regresyon testi |

---

## 4. Testler

`PlatformFailureRegistryTest` içine eklenenler:

1. `kitsuApi404IsReportedAsNotFoundNotAsHostChange` → `kitsu.app/api/edge/anime` + 404 = `COM_1009`
2. `kitsuRateLimitAndServerErrorsKeepTheirOwnCodes` → 429 = `KIT_4002`, 503 = `COM_1007`
3. `kitsuLegacyHostUnreachableIsStillHostChange` → `UnknownHost(kitsu.io)` = `KIT_4001`
4. `mediaCdnFailuresAreNotRecordedAsPlatformFailures` → `media.kitsu.app`, `s4.anilist.co`,
   `image.tmdb.org`, `cdn.myanimelist.net`, `simkl.in` kaydedilmez
5. `apiRequestsWithoutMediaExtensionAreStillRecorded` → `graphql.anilist.co/graphql` + 404 = `COM_1009`

> Not: Bu ortamda JDK/Gradle bulunmadığı için derleme ve testler çalıştırılamadı; değişiklikler statik
> inceleme (parantez/dil denetimi, çağrı yolları) ile doğrulandı.

---

## 5. Beklenen davranış (düzeltme sonrası)

- Bütün raflar doluyorsa **hiçbir hata rozeti çıkmaz** (poster 404'leri artık API hatası sayılmıyor).
- Bir kaynak gerçekten çökerse rozet çıkar, **kaynak adı + doğru kod** ile:
  `Kitsu · Kitsu hız sınırı`, `Bangumi · …`, `AniList · Kayıt bulunamadı (HTTP 404)`.
- "Kitsu API adresi değişti" yalnızca host gerçekten çözümlenemediğinde/yönlendirildiğinde görünür.
- Görsel/video/altyazı CDN hataları detay ekranlarında da artık "kayıt bulunamadı" gibi yorumlanmaz.
