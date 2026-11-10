# Küçük video (klip) indirmeleri — İndirmeler ekranı entegrasyonu · 2026-11-03

## 1. Kullanıcı bildirimi

> "Bu küçük videolar inerken de indirme sayfası ile bağlantılı çalışmalı; oradan görüp
> silebileyim. Bir kere indi mi bir daha üst üste inmesin. Bu küçük video indirme ve
> hataları ile ilgili tüm önlemleri al."

Üç ayrı beklenti var:

1. **Görünürlük** – detay sayfasındaki Videolar sekmesinden indirilen küçük videolar
   (fragman, kamera arkası, tema) İndirmeler ekranında görünmeli ve oradan silinebilmeli.
2. **Tekilleştirme** – aynı video ikinci kez inmemeli, üst üste kopya dosya oluşmamalı.
3. **Dayanıklılık** – indirme/çözümleme hataları sessiz kalmamalı; yarım dosya, hayalet
   kayıt, dolu disk, ağ kopması, uygulamanın kapanması gibi tüm ara durumlar ele alınmalı.

## 2. Kök neden

Küçük videolar ile bölüm indirmeleri **tamamen ayrı iki dünyaydı**:

| Konu | Bölüm indirmesi | Küçük video (eski durum) |
|---|---|---|
| Kayıt | `AnimeDownloadManager` (kalıcı, StateFlow) | **Kayıt yok** |
| Dosya | `Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` | serbest dosya adı |
| İndirmeler ekranı | Videolar sekmesinde listelenir | **hiç görünmez** |
| Silme | kayıt + dosya + MediaStore | yalnızca dosya yöneticisi |
| İlerleme | servis + bildirim | **yok** |
| Tekilleştirme | `animeId + episode` | **yok** → `_1`, `_11`, `(1)` kopyaları |

Sonuç: kullanıcı videoyu indiriyor, İndirmeler ekranında **0 video** görüyor, dosya
diskte kalıyor ve ikinci dokunuşta aynı video tekrar iniyordu. Ayrıca bir hata olduğunda
(akış yok, ses/görüntü ayrı, ağ koptu) hiçbir geri bildirim yoktu.

## 3. Çözüm mimarisi

Küçük videolar **mevcut indirme motoruna** bağlandı; ayrı bir paralel sistem kurulmadı.
Böylece kuyruk, ön plan servisi, bildirim, ilerleme, duraklat/devam, silme ve depolama
muhasebesi **tek kaynaktan** yürüyor.

```
Detay > Videolar sekmesi
   └─ indirme düğmesi (durum: indir / % / iptal / tamam / yeniden dene)
        └─ AnimeDownloadManager.addClipDownload()      ← tekilleştirme + kalıcı kayıt
             └─ AnimeDownloadService (ön plan)         ← kuyruk, bildirim
                  └─ AnimeDownloader.downloadClip()    ← çözümleme + indirme
                       ├─ ClipDownloadSource.resolve() ← YouTube → indirilebilir akış
                       └─ Download/Kitsugi/Video/Klipler/<Başlık>_<Video>_N.<ext>
                            └─ İndirmeler > Videolar sekmesi (aç / duraklat / sil)
```

### 3.1. Model (`AnimeDownload`)

Yeni alanlar (hepsi varsayılan değerli → eski kayıtlar bozulmaz):

- `kind: EPISODE | CLIP`
- `clipKey` – `clip|<video kimliği>`; tekilleştirmenin çekirdeği (medyadan bağımsız)
- `clipTitle` – satırdaki video adı
- `clipBaseName` – uzantısız dosya taban adı (uzantı kaynağa göre değişebilir: mp4/webm/ts)
- `errorMessage` – kullanıcıya gösterilen son hata nedeni

### 3.2. Tekilleştirme (üç katman)

1. **Anahtar düzeyi** – `clipKey`, YouTube video kimliğinden üretilir. `watch?v=`,
   `youtu.be/…`, `&t=` gibi farklı bağlantılar **aynı** anahtara düşer. Anahtar medya
   başlığına bağlı değildir; aynı video başka bir içerik sayfasından açılsa da tekrar inmez.
2. **Kayıt düzeyi** – kuyrukta/indiriliyor durumunda ikinci kayıt **oluşturulmaz**;
   tamamlanmış ve dosyası duran video için hiçbir şey yapılmaz.
3. **Dosya düzeyi** – indirme başlarken taban ada ait dosya diskte varsa ağa **hiç
   çıkılmaz**, kayıt doğrudan `COMPLETED` işaretlenir (kayıt kaybolsa bile).

Ayrıca aynı ada sahip **farklı** videolar `_1`, `_2`… sıra ekiyle ayrılır → üzerine yazma
veya `(1)` kopyası oluşmaz.

### 3.3. Kaynak çözümleme (`ClipDownloadSource`)

- YouTube bağlantısı → `InAppYouTubeExtractor` (oynatıcıyla aynı çözümleyici, oturum/anahtar
  önbelleği paylaşılır).
  - **Muxed HLS** → indirilebilir (segmentler birleştirilir, `.ts`).
  - **Progressive (ses+görüntü birlikte)** → indirilebilir.
  - **Yalnızca ayrı video + ses** → **indirilmez**; "ses ve görüntü ayrı akışlarda" mesajı
    gösterilir. (Uygulamada muxer yok; sessiz video üretmektense net hata daha doğru.)
- Doğrudan medya bağlantısı (`mp4/webm/m3u8…`) → olduğu gibi indirilir.
- Desteklenmeyen bağlantı → "bu video bağlantısı indirilemiyor".

Çözümleme **indirme anında** yapılır; kuyrukta bekleyen bir video için süresi geçmiş
YouTube imzalı URL'i kullanılmaz.

### 3.4. İndirme dayanıklılığı (`AnimeDownloader.downloadClip`)

- Her zaman `<ad>.part` dosyasına yazılır, başarıda **atomik olarak** son ada taşınır
  (rename başarısızsa kopyala-sil yedeği). Yarıda kalan dosya asla "indirilmiş" görünmez.
- İptal/hata → `.part` **silinir**; iptal `PAUSED` durumuna düşer (yeniden denenebilir).
- Progressive indirmede `Range` ile devam (3 deneme + artan bekleme); 4xx (408/429 hariç)
  tekrar denenmez.
- `Content-Type` doğrulaması: `text/*`, `json`, `html` dönerse indirme iptal edilir
  (catch-all/portal sayfası .mp4 olarak kaydedilmez).
- Boyut tavanı (2 GB) ve düzenli boş alan kontrolü; dolu diskte "yer yok" mesajı.
- HLS'te segment bazlı 3 deneme; master playlist → en yüksek bant genişliği; `EXT-X-MAP`
  desteklenir.
- Bitişte `MediaScanner` ile dosya sisteme bildirilir (galeri/dosya yöneticisi tutarlı).
- Hata mesajları yerelleştirilmiş ve **kayda yazılır** → İndirmeler kartında görünür.

### 3.5. Yaşam döngüsü / kendini onarma

- Uygulama açılışında `DOWNLOADING` kalan kayıtlar `PAUSED`'a çekilir (sonsuz "indiriliyor"
  hayaleti biter).
- Açılışta hiçbir servis çalışmadığı için `QUEUE` kalan **klip** kayıtları `PAUSED`'a alınır.
- Kliplerin `COMPLETED` kaydı var ama dosya yoksa (dosya yöneticisiyle silinmiş) kayıt
  `ERROR` + "dosya bulunamadı" mesajına çevrilir; kart "İndirildi" diye yalan söylemez.
- Açılışta sahipsiz `.part` kalıntıları temizlenir.

### 3.6. Gson şema koruması (`AnimeDownloadSanitizer`)

Gson nesneleri **kurucu çağırmadan** oluşturduğu için yeni eklenen non-null alan (`kind`)
eski JSON'da `null` kalır; `data class copy()` çağrısı `NullPointerException` fırlatırdı
(eski indirmeleri olan kullanıcı için çökme). `rehydrated()` her kaydı açık kurucu
argümanlarıyla yeniden üretip eksik alanlara güvenli varsayılan verir. Hem disk
(`AnimeDownloadStore`) hem yedek geri yükleme (`BackupManager`) bu yoldan geçer.

## 4. Kullanıcı arayüzü

### 4.1. Detay > Videolar sekmesi

Her video satırının sağında tek düğme, duruma göre:

| Durum | Simge | Dokunma |
|---|---|---|
| Kayıt yok | indir | indirmeyi başlat |
| Kuyrukta / indiriliyor | dairesel ilerleme | iptal |
| Tamamlandı | yeşil onay | – (satıra dokununca **yerel dosyadan** oynar) |
| Hata / duraklatıldı | kırmızı yenile | yeniden dene |

Satır alt yazısı durumu gösterir: `İndiriliyor: %42`, `İndirme sırasında bekliyor…`,
`İndirildi`, hata nedeni. İndirilen kopya varsa oynatıcı uzak YouTube yerine
`file://…` kullanır.

### 4.2. İndirmeler > Videolar sekmesi

- Klip kartları bölüm indirmeleriyle **aynı listede**: video adı (büyük), yapım adı (alt),
  "Klipler" rozeti, ilerleme çubuğu, boyut, hata nedeni.
- Oynat (yerel dosya), duraklat/devam, **sil** (dosya + MediaStore + kayıt) çalışır.
- Silme sonucu kullanıcıya bildirilir; Scoped Storage'da sistem onayı gerekiyorsa
  `createDeleteRequest` akışı (altyazı/görsel kartlarıyla aynı) devreye girer.
- Sekme sayacı (`Videolar (n)`) klipleri de sayar; depolama ölçümü özyinelemeli olduğu
  için "İndirilen dosyalar" toplamına dahildir.

### 4.3. Diğer ekranlarla uyum

- Klipler `Download/Kitsugi/Video/Klipler` altında tutulur → çevrimdışı **bölüm**
  listesi (`OfflinePlaybackHelper.listDownloadedMedia`) kirlenmez.
- Settings > Depolama > "Videoları sil" / "Tüm indirilenleri sil" klipleri de siler ve
  kayıtları temizler (klasör "içinde video var" kuralıyla yakalanır).
- Ayar ne olursa olsun (harici indirici 1DM/ADM dâhil) küçük videolar **uygulama içi**
  indirilir; harici indiriciye YouTube sayfa bağlantısı vermek anlamsız olurdu.

## 5. Hata matrisi

| Durum | Kullanıcıya görünen | Sistem davranışı |
|---|---|---|
| İndirilebilir akış yok | "Bu video için indirilebilir akış bulunamadı." | dosya oluşturulmaz, kayıt `ERROR` |
| Ses + görüntü ayrı | "sesi ve görüntüsü ayrı akışlarda…" | indirilmez |
| Ağ hatası | "Video kaynağına ulaşılamadı…" | 3 deneme, sonra `ERROR` |
| HTTP 4xx/5xx | "Sunucu hatası (kod)" | 5xx'te tekrar, 4xx'te hemen |
| HTML/JSON döndü | "Sunucu video yerine sayfa döndürdü…" | `.part` silinir |
| Dolu disk | "Yeterli boş depolama alanı yok." | indirme başlamaz / durur |
| Aşırı büyük akış | "beklenenden çok büyük…" | 2 GB tavanı |
| Kullanıcı iptali | "İndirme iptal edildi." | `.part` silinir, `PAUSED` |
| Uygulama kapandı | "Duraklatıldı" | açılışta `PAUSED`, `.part` temizliği |
| Dosya elle silindi | "İndirilen dosya bulunamadı…" | kayıt `ERROR`'a çekilir |
| Yeniden indirme | "Bu video zaten indirilmiş." | ağa çıkılmaz |

## 6. Değişen / eklenen dosyalar

| Dosya | Değişiklik |
|---|---|
| `core/download/ClipDownloadSource.kt` | **yeni** – YouTube/doğrudan bağlantı → indirilebilir akış, boş alan kontrolü |
| `core/download/AnimeDownloader.kt` | klip indirme yolu (HLS + progressive, `.part`, tekrar, tavan) |
| `core/download/DownloadExportNaming.kt` | klip klasörü ve güvenli/çakışmasız adlandırma |
| `core/download/AnimeDownloadService.kt` | klip bildirim metinleri, hata nedeninin kayda yazılması |
| `data/model/AnimeDownload.kt` | `kind`, `clipKey`, `clipTitle`, `clipBaseName`, `errorMessage` |
| `data/model/AnimeDownloadSanitizer.kt` | **yeni** – Gson şema koruması (`rehydrated()`) |
| `data/local/AnimeDownloadManager.kt` | klip ekle/iptal/sil, tekilleştirme, açılış onarımı |
| `data/local/AnimeDownloadStore.kt`, `data/backup/BackupManager.kt` | okuma yolunda `rehydrated()` |
| `core/storage/DownloadStorage.kt` | `deleteClip()` – tüm kopyalar + MediaStore + onay URI'leri |
| `core/player/OfflinePlaybackHelper.kt` | `Klipler` klasörü çevrimdışı bölüm listesine girmesin |
| `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt` | satır içi indirme düğmesi, durum metni, yerel oynatma |
| `ui/screens/offline/DownloadsScreen.kt` | klip kartı, yerel oynatma, sonuç bildirimli silme |
| `res/values/strings.xml` | 29 yeni Türkçe metin |

## 7. Test senaryoları

1. Fragmanı indir → İndirmeler > Videolar'da kart görünür, ilerleme akar, bitince oynar.
2. İndirmeyi iptal et → kart "Duraklatıldı"; diskte `.part` kalmaz.
3. Aynı videoya tekrar dokun → "Bu video zaten indirilmiş", ikinci dosya oluşmaz.
4. Aynı video bağlantısını `youtu.be` biçimiyle aç → aynı kayda düşer.
5. Aynı adlı iki farklı videoyu indir → `..._1` ve `..._2` (üzerine yazma yok).
6. Uçak modunda indir → ağ hatası mesajı, kart kırmızı yenile ikonu.
7. İndirme sırasında uygulamayı zorla kapat → açılışta "Duraklatıldı", `.part` temiz.
8. İndirilen dosyayı dosya yöneticisinden sil → İndirmeler'de "dosya bulunamadı" hatası.
9. İndirmeler'den sil → dosya, klasör kopyaları ve MediaStore kaydı gider; sayaç düşer.
10. Settings > Depolama > "Tüm indirilenleri sil" → klipler de silinir, liste boşalır.
11. Eski sürümden kalan bölüm indirmeleriyle yükseltme → çökme yok, bölümler çalışır.
