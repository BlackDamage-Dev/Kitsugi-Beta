# ÇÖZÜM RAPORU — "Kalite" sayfası HDFilmCehennemi / FilmMakinesi (rapidrame) kalitelerini göstermiyor

**Tarih:** 2026-11-02
**Dal:** `arena/ef9363d7-kitsugi-beta`
**Sürüm:** v2.4.341 + v2.4.342 (DASH) + v2.4.343 (kapsam denetimi) + v2.4.344 (kalite profili)
**Şikâyet:** Oynatıcıdaki **Kalite** sayfası *"Bu video veya oynatıcı motoru seçebilir kalite sunmuyor."* diyor; HDFilmCehennemi ve FilmMakinesi eklentilerinden gelen **rapidrame** kaynağının kaliteleri (1080p / 720p / 480p) hiç listelenmiyor.

---

## 1. Kök neden

| Katman | Gerçek | Uygulamanın gördüğü |
|--------|--------|---------------------|
| Eklenti (FilmMakinesi `PlayerResolver`, HDFilmCehennemi) | Tek bir `master.m3u8` linki üretir | Kalite = `Qualities.Unknown` |
| Rapid CDN | `https://sNNN.rapidrame.com/hls2/01/00037/xxx/master.m3u8?t=<token>` | — |
| Close CDN | `https://srvN.cdnimagesNNNN.cyou/hls/<ad>.mp4/txt/master.txt` | Uzantı `.txt` → ölçüm hiç denenmiyordu |
| Master playlist içeriği | `#EXT-X-STREAM-INF:BANDWIDTH=5300000,RESOLUTION=1920x1080,...` satırları | **Hiç okunmuyordu** |

`StreamProbe` daha önce yalnızca `RESOLUTION` değerlerini **rozet** amacıyla sayıyordu (`variantHeights`); varyant **URL'si** hiç saklanmadığı için oynatıcı "seçilebilir kalite" üretemiyordu. Üstelik ölçüm düz `KitsugiHttpClient` ile yapıldığından Referer/çerez/video-interceptor gerektiren embed'lerde 403 dönüyor, sonuç kalıcı olarak "bilgi yok" oluyordu.

## 2. Çözüm

### 2.1 `HlsMasterPlaylist` (yeni, saf Kotlin)
`app/src/main/java/com/kitsugi/animelist/core/player/HlsMasterPlaylist.kt`

- `#EXT-X-STREAM-INF` ↔ sonraki URI satırı eşleştirilir.
- Alanlar: `BANDWIDTH`, `AVERAGE-BANDWIDTH`, `RESOLUTION`, `CODECS`, `NAME`.
- **Token devralma:** göreli varyant URI'sinde sorgu yoksa master'ın `rawQuery`'si eklenir (`.../index-v1.m3u8?t=token`). Aksi hâlde rapidrame 403 verir.
- **Yalnızca-ses varyantı elenir** (`CODECS="mp4a.40.2"`).
- **Tekilleştirme:** aynı yükseklikte en yüksek `BANDWIDTH` kazanır.
- **Sıralama:** yüksekten alçağa.
- **Yükseklik ipuçları:** `RESOLUTION` yoksa `NAME` ("1080p") veya URI (`index_1080.m3u8`) okunur; bulunamazsa etiket boş kalır (tahmin yok).

### 2.2 `StreamProbe`
- `MeasuredStreamInfo.variants: List<HlsVariant>` eklendi; `probeHls` artık varyantları da döndürür.
- Yeni `suspend fun variants(stream): List<HlsVariant>` + `variantCache` (60 kayıt, LRU); `clearCache()` her ikisini temizler.
- `master.txt` gibi **m3u8 uzantısı olmayan** playlist'ler `looksLikePlaylist()` + içerik kontrolü (`#EXTM3U`) ile ayrıştırılır.
- **Oynatıcıyla aynı istemci:** CS kaynaklarında `CsVideoInterceptorFactory.buildClient()` (video interceptor + paylaşılan çerez + SSL bypass). Başarısız önbellek kaydı oynatıcı bağlamında bir kez yeniden denenir.

### 2.3 Oynatıcı UI
- `QualitySheet`: iki bölüm — **Kalite** (`Otomatik` + gerçek varyantlar, bit hızı ile) ve **Kaynak** (eski eklenti listesi). Ölçülebilir varyant yoksa bilgilendirme metni.
- `KitsugiFullscreenPlayerScreen`: kaynak/parça yüklenince master playlist bir kez ölçülür (`LaunchedEffect(currentSourceIndex, currentVideoUrl)`); bir varyant seçildiyse yeniden ölçüm yapılmaz.
- Seçim: `changeStreamSource(currentSourceIndex, src, variant.url)` → **aynı başlıklar + altyazılar**, oynatma mevcut saniyeden devam eder. `Otomatik` → master URL'ye dönüş.
- Yeni metinler `values/strings.xml` + `values-en/strings.xml`.

## 3. Kapsam — hangi kaynaklar artık kalite listeliyor?

| Kaynak türü | Durum |
|-------------|-------|
| HLS master (`master.m3u8`, çok varyantlı) — rapidrame, closeload, genel HLS | ✅ Varyantlar listelenir (URL değişir) |
| **DASH (`.mpd`)** — `AdaptationSet` → `Representation` video kaliteleri | ✅ v2.4.342: listelenir, motorun çözünürlük tavanı ile uygulanır |
| `master.txt` playlist (closeload CDN) | ✅ İçerik `#EXTM3U` ise ayrıştırılır |
| HLS medya playlist (tek kalite) | ➖ Seçilebilir kalite yok → bilgilendirme |
| Progressive `mp4/mkv/webm` | ➖ Tek kalite; kaynak eklentinin bildirdiği kalite rozet olarak kalır |
| Eklentinin ayrı linkler olarak verdiği kaliteler (ör. "HD"/"SD") | ✅ **Kaynak** bölümünde görünür |

## 4. Doğrulama
- `app/src/test/java/.../core/player/HlsMasterPlaylistTest.kt`: 9 test (rapidrame master'ı, medya playlist, token devralma, mutlak URL, ses-only eleme, tekilleştirme, isimden yükseklik, biçimlendirme, geçersiz URI).
- Değişen 7 dosya + 2 yeni dosya için parantez/blok dengesi ve import taraması yapıldı.
- **Not:** Bu ortamda Android SDK/JDK bulunmadığı için `assembleDebug` çalıştırılamadı; derleme cihazda/tarayıcı ortamında doğrulanmalıdır.

## 4.1 v2.4.342 — DASH desteği (kapsam tamamlandı)

`PlayerEngine.setMaxVideoHeight()` (Media3: `maxVideoSize`) + `DashManifest` ayrıştırıcısı +
`QualityOption` modeli eklendi. Kalite sayfası artık HLS varyantlarını **URL değiştirerek**,
DASH kalitelerini **çözünürlük tavanıyla** uygular. Kart listesinde DASH manifesti indirilmez.

## 4.2 v2.4.343 — Kapsam denetimi: kalan 4 boşluk

| # | Boşluk | Çözüm |
|---|--------|-------|
| 1 | Eklenti kaliteyi bildiriyor ama manifest ayrıştırılamıyor → "kalite yok" | `declaredQualityOption()`: kaynağın bildirdiği kalite tek, seçilemez satır olarak gösterilir (`QualityOption.declaredBySource`) |
| 2 | Uzantısız linkler (`/play/abc`) hiç denenmiyordu | `sniffManifestOptions()`: `<MPD` → DASH, `#EXTM3U` → HLS içerik koklaması (progressive uzantılarda çalışmaz) |
| 3 | Eşit yükseklikte HEVC, AVC'yi geçiyordu (çözücü yoksa siyah ekran) | `HlsMasterPlaylist.preferNewVariant()`: AVC önceliklidir |
| 4 | DASH kalite sabitleme MPV'de sessizce yutuluyordu | Media3 dışı motorlarda bilgilendirme; HLS varyant seçimi MPV'de de çalışır |
| 5 | Kaynak satırlarında kalite yoktu | Kaynak listesinde eklentinin bildirdiği kalite rozeti gösterilir |

## 4.3 v2.4.344 — Kalite profili motora bağlandı (denetim bulgusu)

`QualityProfile` yalnızca **kaydediliyor ve gösteriliyordu**; oynatıcı hiç okumuyordu.

- `QualityPreference.maxVideoHeight` (`AUTO/P1080 → null`, `P720 → 720`, `P480 → 480`,
  `DATA_SAVER → 360`) + `QualityProfile.effectiveMaxVideoHeight/effectiveMaxBitrateKbps`.
- **Media3:** `maxVideoSize` + `maxVideoBitrate` profile göre; `prepare()` başına tazelenir.
  Öncelik: elle seçim → profil → sınırsız.
- **MPV:** `hls-bitrate` = `min` (Veri tasarrufu/480p) / `max` (1080p+) / `auto`.
- Kalite sayfasında "Kalite profili: 720p" ipucu.

## 5. Değişen dosyalar
```
app/build.gradle.kts                                              (2.4.341)
app/src/main/java/com/kitsugi/animelist/core/player/HlsMasterPlaylist.kt          (YENİ)
app/src/main/java/com/kitsugi/animelist/core/player/DashManifest.kt               (YENİ)
app/src/main/java/com/kitsugi/animelist/core/player/QualityOption.kt              (YENİ)
app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/core/player/QualityProfile.kt
app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt
app/src/main/java/com/kitsugi/animelist/data/repository/StreamInfoResolver.kt
app/src/main/java/com/kitsugi/animelist/data/repository/StreamProbe.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerSheetsHost.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/test/java/com/kitsugi/animelist/core/player/HlsMasterPlaylistTest.kt     (YENİ)
app/src/test/java/com/kitsugi/animelist/core/player/DashManifestTest.kt           (YENİ)
release_notes_v2.4.341.md                                                          (YENİ)
```
