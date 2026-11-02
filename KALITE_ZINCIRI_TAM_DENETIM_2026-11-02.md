# Kalite Zinciri — Tam Denetim ve Toplu Düzeltme (2026-11-02)

**Amaç:** Tek tek eksik kapatmak yerine, kaynak → kalite listesi → seçim → oynatma
zincirinin **tamamını** masaya yatırıp oluşabilecek her sorunu önceden yakalamak.
**Sürüm:** v2.4.341 → v2.4.345

---

## 1. Zincir haritası (hangi katman ne yapıyor)

```
eklenti/CŞ kaynağı
   └─ StreamSource (url, quality, qualityValue, requestHeaders, isCS)
        └─ StreamProbe.qualityOptions(stream)          ← tek giriş noktası
             ├─ HLS  (master.m3u8 / master.txt) → HlsMasterPlaylist → HlsVariant(lar)
             ├─ DASH (.mpd)                     → DashManifest      → QualityOption(lar)
             └─ uzantısız URL                   → içerik koklama (beyaz liste filtreli)
                  └─ ölçüm boşsa → kaynağın bildirdiği kalite (declaredBySource)
        └─ QualitySheet (Kalite bölümü + Kaynak bölümü)
             └─ seçim → QualityOption
                  ├─ switchUrl var  → changeStreamSource(varyant URL)  (konum korunur)
                  └─ switchUrl yok  → PlayerEngine.setMaxVideoHeight() (Media3 tavanı)
        └─ PlayerEngine (Media3 / MPV) + QualityProfile (ayarlar) tavanı
```

---

## 2. Denetimde bulunan ve **düzeltilen** sorunlar

| # | Öngörülen sorun | Neden olurdu | Düzeltme | Sürüm |
|---|-----------------|--------------|----------|-------|
| 1 | HLS master'da **ayrı ses grubu** (`#EXT-X-MEDIA:TYPE=AUDIO` + `URI`) varken varyant URL'sine geçmek | Video gelir, **ses gelmez** (ses ayrı playlist'te) | `HlsVariant.requiresMasterAudio` → `QualityOption.switchUrl = null` → seçim **tavanla** uygulanır, master (ve ses) korunur | 2.4.345 |
| 2 | Seçilen varyant URL'sinin **imzalı token'ı süresi dolmuş / 403** | Oynatıcı hatası ekranında **kilitlenme** | Hata dinleyicisinde tek seferlik otomatik geri dönüş: master + aynı çözünürlük tavanı + `clearPlayerError()` + bilgilendirme | 2.4.345 |
| 3 | Başarısız/boş ölçümün **önbelleğe yazılması** | Geçici ağ hatası → uygulama yeniden açılana kadar "kalite yok" | Boş sonuç **önbelleğe yazılmaz**; kalite sayfası her açılışta boşsa yeniden ölçer | 2.4.345 |
| 4 | Manifest **koklamasının** P2P/localhost, segment, mp4/mp3 linklerinde de çalışması | Boşa istek + oynatma başında gecikme | `shouldSniff()` + `NON_MANIFEST_SUFFIXES` (`.ts/.m4s/.mp4/.mkv/.mp3/...`), `127.0.0.1`/`localhost` hariç | 2.4.345 |
| 5 | MPV'de **yalnızca tavanla** uygulanabilen kalite (DASH / ayrı ses gruplu HLS) seçilince | Arayüz "seçili" gösterir, oynatıcı hiçbir şey yapmaz | Seçim işaretlenmez + açıklayıcı uyarı; HLS varyantı (URL değişimli) MPV'de çalışmaya devam eder | 2.4.345 |
| 6 | Motor değişiminde (Media3 ↔ MPV) çözünürlük tavanının kaybolması | Seçilen kalite sessizce eski hâline döner | `LaunchedEffect(playerEngine, selectedQuality)` tavanı yeni motora yeniden uygular | 2.4.345 |
| 7 | **Ayarlardaki Kalite Profili** hiçbir motora uygulanmıyordu | "720p / Veri tasarrufu" seçimi etkisizdi | `QualityPreference.maxVideoHeight` + Media3 `maxVideoSize`/`maxVideoBitrate` (her `prepare()`'da DataStore'dan taze) + MPV `hls-bitrate` | 2.4.344 |
| 8 | Aynı çözünürlükte **HEVC**'nin AVC'yi geçmesi | HEVC çözücüsü olmayan cihazda "ses var, görüntü yok" | Eşit yükseklikte **AVC öncelikli** | 2.4.343 |
| 9 | Manifest ayrıştırılamazsa **eklentinin bildirdiği kalite** kaybolması | Sayfa boş görünür | `QualityOption.declaredBySource` → "Kaynağın bildirdiği kalite" satırı | 2.4.343 |
| 10 | Uzantısız akış linkleri (`/play/abc123`) hiç denenmemesi | Kalite okunamaz | İçerik koklama: `<MPD` → DASH, `#EXTM3U` → HLS | 2.4.343 |
| 11 | `master.txt` (m3u8 uzantısız) playlist | closeload kaliteleri görünmez | `looksLikePlaylist()` + içerik kontrolü | 2.4.341 |
| 12 | DASH'in **tek satır (minified)** gelmesi | Hiçbir `Representation` bulunamaz | `normalizedLines()` (`><` → satır) | 2.4.342 |
| 13 | DASH'te **ses AdaptationSet**'inin video kalitesi sayılması | Listede sahte "128 kbps" kalitesi | `mimeType="audio/*"` + kodek filtresi | 2.4.342 |
| 14 | İmzalı CDN'de göreli varyant satırında **token'ın kaybolması** | Varyant 403 → "oynatıcı hatası" | `resolveUri()` master'ın `rawQuery`'sini devralır | 2.4.341 |
| 15 | CS kaynaklarında ölçümün **düz istemciyle** yapılması | Referer/çerez/video-interceptor yok → 403 → kalite yok | `CsVideoInterceptorFactory.buildClient()` (paylaşılan çerez + SSL bypass) + başarısız kaydı bir kez yenileme | 2.4.341 |
| 16 | Yalnızca ses içeren varyantlar | "1080p" diye ses akışı listelenir | `CODECS` ses-only filtresi | 2.4.341 |
| 17 | X-ışını yok: kod içinde okunması zor kaçışlı regex'ler | Bakım hatası riski | `DashManifest` regex'leri sade kaçışlı string'e çevrildi + davranış Python ile doğrulandı | 2.4.344 |

---

## 3. Denetlenip **sorun olmadığı doğrulanan** noktalar

- **CRLF / BOM / boş satırlar**: `trim()` + `contains("#EXTM3U")` ile tolere edilir.
- **Attr içinde virgül** (`CODECS="avc1.640028,mp4a.40.2"`): tırnak farkındalıklı regex → bölünmez.
- **Çok yüksek bit hızlı ama aynı yükseklik** varyantlar: tekilleştirme → en yüksek BANDWIDTH.
- **`RESOLUTION` yoksa**: `NAME` / URI ipucu (`index_1080.m3u8`); hiçbiri yoksa etiket uydurulmaz.
- **Local/`content://` kaynak**: `qualityOptions` boş döner (çökme yok).
- **Torrent/P2P**: `isTorrent` yolu + localhost filtresi → gereksiz ölçüm yok.
- **Altyazı/ses parçaları**: kalite seçimi bunlara dokunmaz; `changeStreamSource` aynı başlık ve
  altyazı listesini korur, oynatma `pendingSourceSwitchResumeMs` ile kaldığı saniyeden sürer.
- **LazyColumn anahtarları**: `option.key` ve kaynak anahtarları benzersiz → çakışma yok.
- **`PlayerEngine` arayüzü**: `setMaxVideoHeight` varsayılan no-op → mevcut motorlar (MPV,
  harici) bozulmaz.
- **`QualityProfile` serileştirme**: hesaplanan alanlar constructor'da değil → kayıtlı JSON geriye dönük uyumlu.

---

## 4. Bilinçli olarak **yapılmayanlar** (kaynağın doğasında)

| Durum | Neden yapılamıyor |
|-------|-------------------|
| Tek kaliteli kaynak (`mp4`, tek varyantlı HLS) | Kaynakta seçilecek başka kalite **yok**; uydurma kalite basılmıyor. |
| DRM'li (Widevine) manifest | Liste okunur, anahtar kaynakta yok → oynatma mümkün değil. |
| MPV'de DASH kalite sabitleme | libmpv'de çözünürlük tavanı yok (`hls-bitrate` yalnızca HLS). MPV'de HLS varyantları URL değişimiyle çalışır. |
| `EXT-X-STREAM-INF` içermeyen canlı/radyo playlist | Kalite kavramı yok. |

---

## 5. Test kapsamı

| Dosya | Test |
|-------|------|
| `HlsMasterPlaylistTest` | 12 test — rapidrame master'ı, token devralma, mutlak URL, ses-only eleme, **ayrı ses grubu (URL değişmez)**, kodek önceliği, tekilleştirme, isimden yükseklik, biçimlendirme |
| `DashManifestTest` | 7 test — sıralama, ses ayrımı, kodek filtresi, tekilleştirme, minified MPD, DASH olmayan gövde |
| `QualityProfileTest` | +2 test — tercih → tavan eşlemesi, etkin limitler |

> Not: Bu ortamda Android SDK/JDK bulunmadığı için `assembleDebug`/`testDebugUnitTest`
> çalıştırılamadı; tüm değişen dosyalar derleyici farkındalıklı denge denetiminden geçirildi
> (`/tmp/kt_balance.py`: 13 dosya → ok) ve regex davranışı Python karşılığıyla doğrulandı.
