# KÖK NEDEN RAPORU — HDFilmCehennemi & FilmMakinesi indirmelerinde "Segment alınamadı: HTTP 429"

**Tarih:** 2026-11-09
**Şikâyet (ekran görüntüsü):** İndirmeler > Videolar sekmesinde HDFilmCehennemi bölümü
`Segment alınamadı: HTTP 429` ile duruyor; aynı kaynaktan yapılan diğer bölüm
"Duraklatıldı" durumunda kalıyor. Kullanıcı: _"indirme yarıya kadar gelip hata vermesin,
kaynaklara özel indirme özelliği olması lazım"_.

**Sonuç:** Uygulamanın indirme mekaniği genel olarak sağlamdı; ancak **HLS indirici bu
CDN ailesine karşı tarayıcıdan daha agresif** davranıyordu. 429 bir hata değil, sunucunun
"yavaşla" sinyalidir — indirici bu sinyali hem üretiyor hem de yanlış yorumluyordu.

---

## 1. Kanıt: kod ne yapıyordu?

### KN-1 — Segmentler arasında hiç bekleme yok (ana neden)

`AnimeDownloader.kt` (v2.4.340) HLS kolu:

```kotlin
for (index in startSegmentIndex until totalSegments) {
    val segmentUrl = segments[index]
    bytesDownloaded += downloadSegmentToStream(segmentUrl, download.requestHeaders, outputStream)
    ...
}
```

`downloadSegmentToStream` başarılı olduğunda **hiç `delay` yok**. Yani indirici, boşta kalan
tüm bant genişliğiyle saniyede onlarca segment isteği üretiyor. Tarayıcı oynatıcı ise aynı
akışı tampon kadar (genelde 2-6 segment) ve saniyede 3-5 istek hızıyla çekiyor; bu yüzden
limit hiç tetiklenmiyor.

> Bu CDN ailesi (closeload / rapidrame / rplayer / playmix.uno) IP bazlı istek sayacı
> tutuyor. Limit aşıldığında `429` dönüyor. **Aynı dosyayı tarayıcıda izlemek çalışırken
> indirmenin yarıda düşmesi** tam olarak bu farkın sonucudur.

### KN-2 — `Retry-After` okunmuyor

```kotlin
for (attempt in 1..3) {
    try { ... } catch (e: Exception) {
        if (attempt < 3) delay(1000L * attempt)   // sabit 1-2 sn
    }
}
```

Sunucu `Retry-After: 30` dediğinde bile 2 saniye sonra tekrar istek atılıyor. Bu, cezayı
**uzatıyor** ve 429'un 403'e dönüşmesine yol açıyor.

### KN-3 — Gizli yeniden denemeler (kota çarpanı)

`KitsugiHttpClient.client` içinde `RetryInterceptor(maxRetries = 2)` var. Her segment
isteği 429 aldığında bu interceptor **kendi içinde 2 kez** daha deniyor (üstelik
`Thread.sleep` ile). İndiricinin 3 denemesiyle birleşince tek bir segment için **9 gerçek
HTTP isteği** üretilebiliyor. `jikanClient` / `aniListClient` için bu sorun daha önce
çözülmüştü (interceptor kaldırılmıştı) ama **indirme istemcisi için yapılmamıştı**.

### KN-4 — "Devam et" aslında sıfırdan başlıyordu

```kotlin
val startSegmentIndex = if (hasFile) download.downloadedSegments else 0
```

`downloadedSegments` yalnızca `onProgress` ile güncelleniyordu; hata anında bu değer
kolayca `0` kalıyordu. Kullanıcı ▶ düğmesine bastığında indirme **sıfırdan** başlıyor,
yani zaten dolu olan kota yeniden tüketiliyor ve 429 tekrar geliyordu. Ekran görüntüsündeki
"Duraklatıldı" satırının nedeni budur.

### KN-5 — Segment token'ları süresi dolunca playlist tazelenmiyordu

CDN segment adreslerine kısa ömürlü token koyuyor. Uzun bir bölümde (45 dk) token
süresi dolduğunda segment `403/404` dönüyor ve indirme **kalıcı** olarak düşüyordu.

### KN-6 — Eksik `Origin` başlığı

Oynatıcı yolu (`PlayerMediaSourceFactory`) `Origin`'i Referer'dan türetiyor; indirme yolu
ise ham `requestHeaders` kullanıyordu. CS3 eklentileri çoğu zaman yalnızca `Referer`
veriyor. Bu CDN'ler `Origin`'i embed host'una göre doğruluyor → eksikken `403` **veya**
`429` (WAF sayacı) dönebiliyor.

### KN-7 — Uzantısız HLS akışları "düz dosya" sanılıyordu

```kotlin
val isHls = download.url.contains(".m3u8", ignoreCase = true)
```

Close/Rapid akışları `.../hls/xxxx.mp4/master.txt` biçiminde geliyor. Bu adresler `.m3u8`
içermediği için **tek parça dosya** gibi indirilmeye çalışılıyor, sonuçta m3u8 metni `.mp4`
olarak kaydediliyordu.

### KN-8 — Şifreli (AES-128) ve byte-range'li playlistler

`EXT-X-KEY` / `EXT-X-BYTERANGE` / `EXT-X-MAP` etiketleri hiç okunmuyordu. Şifreli
akışlarda indirme "tamamlandı" görünüp dosya oynatılamıyordu.

---

## 2. Düzeltme (v2.4.341)

| Dosya | Değişiklik |
|---|---|
| `core/download/HlsPlaylist.kt` **(yeni)** | Saf Kotlin HLS ayrıştırıcı: master/medya playlist, `EXT-X-KEY` (AES-128 + IV), `EXT-X-BYTERANGE` (zincirleme), `EXT-X-MAP`, `EXT-X-MEDIA-SEQUENCE`, `ENDLIST`, ses-only varyant filtresi, göreli URL çözümü. |
| `core/download/HlsDownloadPolicy.kt` **(yeni)** | Host bazlı indirme politikası. `CLOSE_RAPID_CDN` = 250 ms/istek, 8 deneme, 2 sn geri çekilme tabanı, 45 sn tek bekleme tavanı, 2,5 sn'ye kadar uyarlanabilir aralık, 400 ms ısınma. `Retry-After` ayrıştırma (saniye + HTTP tarihi, 120 sn tavan). |
| `core/download/HlsSegmentFetcher.kt` **(yeni)** | Tüm indirmelerin paylaştığı host kotası; 429 → soğuma + aralık büyütme; başarıda yavaş toparlanma; 403/404/410 kalıcı hata; 416'da Range'siz tekrar; segment bellekte tamamlanmadan diske yazılmaz. |
| `core/download/HlsKeyResolver.kt` **(yeni)** | AES-128 CBC/PKCS7 çözme, IV yoksa medya sırasından türetme, anahtar önbelleği (anahtar başına tek istek). |
| `core/download/DownloadHeaderPolicy.kt` **(yeni)** | Eksik `Origin`/`Referer`/`User-Agent`/`Accept-Language` tamamlama (mevcut başlıkları ezmeden). |
| `core/download/AnimeDownloader.kt` | HLS kolu tamamen yenilendi; `isHlsUrl` yol belirteçleriyle; token tazeleme (5×); kalıcı segment sayacı; kuyruk kırpma ile güvenli devam; `RateLimitedException`/`PermanentDownloadException`; tip taşıyan `DownloadFailure`; Cloudflare çerezli indirme istemcisi; düz indirmede de 429 protokolü ve Range sahtelemesine karşı koruma. |
| `core/download/AnimeDownloadService.kt` | 429'da otomatik yeniden deneme (5→20→60 sn), bekleyen iş varken servisin kapanmaması, "kaynak yavaşlattı" bildirimi, hata nedeninin bildirimde gösterilmesi. |
| `data/model/AnimeDownload.kt` | `errorMessage`, `retryAtMs`, `autoRetryCount` alanları. |
| `data/local/AnimeDownloadManager.kt` | `scheduleRetry`, `clearRetryState`; `getNextDownload` bekleyenleri atlar; `updateStatus` hata mesajını taşır; kullanıcı "devam" dediğinde sayaç sıfırlanır. |
| `ui/screens/offline/DownloadsScreen.kt` | Hata nedeninin kartta gösterilmesi; bekleyen işte saniyelik geri sayım. |
| `res/values/strings.xml`, `values-en/strings.xml` | 11 yeni metin (TR + EN, birebir eşleşen format argümanları). |

---

## 3. Neden bu, "yarıda kalma"yı bitirir?

1. **Limitin oluşmasını engeller:** istek hızı tarayıcı seviyesine (≈4 req/sn) çekildi.
2. **Limit oluşursa uzatmaz:** `Retry-After` + soğuma + üstel geri çekilme.
3. **Kotayı boşa harcamaz:** kalıcı hatalarda denemez; gizli `RetryInterceptor` denemeleri
   devre dışı; "devam et" gerçekten kaldığı yerden başlar.
4. **Token ömrünü yönetir:** 403/404'te playlist tazelenir.
5. **Kullanıcıya doğru bilgi verir:** "hata" değil "kaynak yavaşlattı, X sn sonra devam".

---

## 4. Doğrulama

⚠️ Bu sandbox'ta Android SDK/Gradle yok; `./gradlew :app:testDebugUnitTest` **çalıştırılamadı**.
Bu yüzden:

- Tüm yeni/değişen Kotlin dosyaları süslü parantez/parantez dengesi ve `import` eksiksizliği
  açısından elle + betikle denetlendi,
- TR/EN string eşleşmesi `scripts/check_localization_resource_parity.py` ile doğrulandı
  (**17707 string, senkron**),
- Saf JVM testleri eklendi (`HlsPlaylistParserTest`, `HlsDownloadPolicyTest`); Android
  bağımlılığı olan `Uri` kullanımı `java.net.URI`'ye çevrildi ki testler Robolectric'siz koşsun.

Cihazda doğrulama için öneri: HDFC'den 45 dk'lık bir bölüm indirip `logcat | grep AnimeDownloader`
ile şu satırları izleyin: `HLS: N segment ... aralık=250ms`, gerekiyorsa
`429 → host soğuma ...`, `Devam: kuyruk kırpıldı`.

---

## 5. Ek: "Cihazın desteklemediği formatta inerse bir anlamı kalmaz" (v2.4.342)

**Kullanıcı isteği:** _torrent videoları hariç, indirilen her şey cihazın çalabildiği formatta olsun._

### Önce: mevcut durumun tespiti

Kodda **hiçbir dönüştürme yoktu**: ffmpeg/ffmpeg-kit binary'si, Media3 Transformer
bağımlılığı, remux adımı yok. İndirici segmentleri birleştirip **her zaman `.mp4`** adıyla
yazıyordu. Yani "kaynak ne verirse o" iniyordu.

| Katman | Eski durum |
|---|---|
| Kodek dönüşümü | ❌ yok |
| Kapsayıcı remux (TS→MP4) | ❌ yok (TS içerik `.mp4` adıyla) |
| Cihaz codec kontrolü | ❌ yok (yalnızca oynatıcıda Dolby Vision politikası) |
| Varyant seçimi | En yüksek bitrate (AV1/HEVC/AC-3 olabilir) |
| İndirme sonrası doğrulama | ❌ yok |

### Neden "transcode" yerine "doğru kaynağı seç"?

- Telefonda 45 dk'lık bölümü H.264+AAC'ye çevirmek **15-40 dakika** sürer, cihazı ısıtır,
  bataryayı bitirir; APK'ya ffmpeg-kit eklemek **+35-60 MB** demek (üstelik ffmpeg-kit
  arşivlenmiş durumda, lisansı LGPL/GPL).
- Aynı bölüm kaynakta genelde **birden çok codec'le** zaten var (HDFC/FilmMakinesi
  master playlist'te H.264 + HEVC + AV1 sunuyor). Doğru olanı seçmek: kalite kaybı yok,
  ek süre yok, dosya boyutu artışı yok.

### Uygulanan çözüm

| Dosya | İş |
|---|---|
| `DeviceCodecSupport.kt` **(yeni)** | `MediaCodecList(REGULAR_CODECS)` ile cihazın decoder envanteri; codec kimliği (`avc1`, `hvc1`, `av01`, `ec-3`, `dts*`) → MIME → destekleniyor mu. Sonuçlar önbellekli. |
| `HlsPlaylist.kt` | `HlsVariant` artık `videoCodec`/`audioCodec` taşır; yeni `selectVariant(master, allowUnsupported)` **desteklenen > bilinmeyen > desteklenmeyen** sırasına göre, sonra bitrate'e göre seçer. `selectBestVariant` geriye dönük uyum için korunur. |
| `DownloadVerifier.kt` **(yeni)** | `MediaExtractor` ile dosyayı açar, gerçek track'leri okur, decoder mevcudiyetiyle karşılaştırır. Oynatılamıyorsa dosya **silinir** ve nedeni döner (yalnızca başlık okunur, ~20-80 ms). |
| `AnimeDownloader.kt` | Varyant seçimi cihaza göre; HLS ve düz indirme sonunda doğrulama; desteklenmeyen çıktı silinip `UnsupportedMediaException`; `.ts`/`.mp4` kapsayıcı uzantısı, playlist'ten gerçek uzantı öğrenilirse otomatik yeniden adlandırma. |
| `DownloadExportNaming.kt` | `exportFileName(..., extension)`; silme akışı `mp4/ts/mkv/webm/m4v/avi/m4a` uzantılarını tanır (eski `.mp4` kayıtları dahil). |
| `strings.xml` (TR/EN) | `indirme_hata_desteklenmeyen_format`, `indirme_format_uyarisi`. |
| `DeviceCompatibilitySelectionTest.kt` **(yeni)** | AV1'siz cihazda H.264 seçimi, AV1 destekleyende en yüksek kalite, HEVC tercihi, `CODECS` yoksa engellememe, hiçbiri desteklenmiyorsa davranış, uzantı türetme, MIME adlandırma. |

### Davranış özeti

1. Kaynak H.264 varyantı da sunuyorsa → **H.264 indirilir** (cihaz kesin oynatır).
2. Cihaz HEVC destekliyorsa → HEVC tercih edilir (daha küçük dosya, aynı kalite).
3. Hiçbir varyant desteklenmiyorsa → indirme denenir, sonunda doğrulama başarısız olur,
   dosya silinir ve _"Bu bölüm cihazınızın çözemediği bir görüntü formatında (AV1)…"_ denir.
4. Ses AC-3/E-AC-3 ise ve cihaz desteklemiyorsa → _"Görüntü oynatılabilir ama ses formatı
   (E-AC-3) desteklenmiyor"_ uyarısı (sessiz video izletmek yerine açık bilgi).
5. Torrent/P2P akışları bu akışa girmez (kullanıcı isteği) — onlar kendi motorunda.

### Sınırlar (dürüstçe)

- Codec **dönüştürme** yok: AV1-only bir kaynak H.264'e çevrilmez, indirilemez/oynatılamaz
  olarak raporlanır. İstenirse ayrı bir iş olarak ffmpeg-kit/Media3 Transformer eklenebilir
  (APK +35-60 MB, bölüm başına 15-40 dk işlem).
- `CODECS` bilgisi vermeyen kaynaklarda seçim tahmine dayanır; **son doğrulama** bunu
  yakalar ve kullanıcıya söyler.
