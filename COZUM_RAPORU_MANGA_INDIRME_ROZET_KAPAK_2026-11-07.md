# Manga İndirmeleri — Kaynak / Çevirmen Rozetleri ve Kapak Görseli (2026-11-07)

## 1. İstek

İndirmeler > **Manga** sekmesindeki bölüm kartlarında:

1. Her klasöre (bölüm satırına) ait **minik rozetler** olsun; böylece aynı manga
   **aynı kaynaktan** inse bile **hangi çevirmenden** (scanlator) indiği görünsün ve
   aynı bölüm numarasının farklı çevirmen/kaynak kopyaları karışmasın.
2. İndirilen her manganın **ilk sayfası**, ilgili "bar"larda **kapak görseli** olarak
   kullanılsın.

## 2. Mevcut Durum (kök neden)

`MangaDownloadsTab.kt` içindeki kart, bilgi olarak yalnızca:

- bölüm adı (`chapterName`),
- durum etiketi + `tamamlanan/toplam` sayfa,
- ilerleme çubuğu

çiziyordu. Grup başlığı ise tek satır `mangaTitle` + `sourceName · hazır/toplam`
metninden oluşuyordu.

Sonuç:
- Kaynak ve çevirmen bilgisi **modelde vardı** (`MangaDownload.sourceName`,
  `sourceLang`, `scanlator`) ama **hiçbir yerde gösterilmiyordu**.
- Aynı bölüm iki farklı çevirmenden indirildiğinde satırlar birebir aynı görünüyordu.
- Kapak görseli yoktu; liste görsel olarak kimliksizdi.
- Sayfa URI'leri (`content://` MediaStore veya `file://`) modelde duruyordu, yani kapak
  için ek ağ/indirme gerekmiyordu — sadece kullanılmıyordu.

## 3. Yapılan Değişiklikler

### 3.1 Veri katmanı (UI içinde türetme, diske yazma yok)

`MangaOfflineGroup` (özel veri sınıfı) eklendi: aynı **kaynak + manga URL'si** için
indirilen bölümleri toplar ve şunları hesaplar:

| Alan | Anlamı |
| --- | --- |
| `readyChapters` | Tamamı indirilmiş (çevrimdışı hazır) bölüm sayısı |
| `sizeBytes` | Grubun disk kullanımı |
| `scanlators` | Bölümlerin çevirmenleri — tekrarsız, indirme sırasıyla |
| `hasScanlatorInfo` | Hiç çevirmen bilgisi yoksa rozet "Bilinmeyen" olarak çizilir |
| `coverUri` | Serinin **ilk sayfası**: en küçük bölüm numarasından başlar, ilk bölüm hâlâ iniyorsa sıradaki tamamlanmış bölümün ilk sayfası |

Ek yardımcılar:

- `MangaDownload.sortKey` — `ChapterRecognition.parseChapterNumber` ile bölüm sırası
  (çözülemeyen adlar listenin sonuna).
- `MangaDownload.firstPageUri` — sıralı ilk **tamamlanmış** sayfanın URI'si.

Sıralama: bölüm numarası → çevirmen adı. Böylece aynı numaralı iki bölüm (farklı
çevirmen) **alt alta** durur ve rozetlerden ayırt edilir.

### 3.2 Arayüz

**Seri başlığı ("bar")** — `MangaOfflineGroupHeader`

```
┌────────┐  Chainsaw Man
│ kapak  │  [ 🧩 Manga-TR ] [ 🌐 TR ] [ 🎙 Şımarık Fansub ] [+1]
│ 52×70  │  2 bölüm · 47.4 MB · 2/2 hazır
└────────┘
```

- Kapak: serinin ilk sayfası (`ContentScale.Crop`, 10 dp köşe, ince çerçeve).
  Görsel yoksa/erişilemezse yer tutucu kitap ikonu (boş kutu kalmaz).
- Kaynak rozeti **sabit renkli**: renk, kaynak adının karmasından 8 renkli paletten
  seçilir → aynı kaynak her zaman aynı rengi alır, gözle ayırt edilir.
- Dil rozeti kaynak dili (TR/EN/…).
- Çevirmen rozetleri tema vurgu renginde (kaynak rozetiyle karışmaz), en fazla 2 tanesi
  + kalanı `+N`.
- Alt satır: toplam bölüm · boyut, eksik indirme varsa `hazır/toplam`.

**Bölüm kartı** — `MangaOfflineChapterCard`

```
┌────────┐  Bölüm 1 - Köpek ve Testere
│ thumb  │  [ 🧩 Manga-TR ] [ 🎙 Şımarık Fansub ]
│ 44×60  │  Çevrimdışı hazır · 52/52
└────────┘
            ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ (4 dp, vurgu rengi)
                     [📖 Çevrimdışı oku] [📂] [⟳] [🗑]
```

- Sol tarafta **bölümün ilk sayfası** küçük kapak olarak, yanında bölüm adı.
- **Kaynak rozeti** + **çevirmen rozeti** her satırda ayrı ayrı; aynı bölüm farklı
  çevirmenden inse bile iki satır rozetlerinden ayrılır. Çevirmen bilgisi yoksa
  "Bilinmeyen" (soluk) rozeti çizilir — bilgi eksikliği gizlenmez.
- Tamamlanan bölümlerde kart çerçevesi vurgu renginin %22 saydam tonuna döner ve
  durum satırı kalın vurgu renginde yazılır (çevrimdışı hazır vurgusu).
- İlerleme çubuğu 4 dp, yuvarlak uçlu, iz rengi `SurfaceStrong`.
- Eylemler korunmuştur: çevrimdışı oku · klasörü göster · duraklat/devam et · sil.

**Minik rozet** — `MangaOfflineTag`

- ~18 dp yükseklik, 6 dp köşe, %14 saydam zemin + %32 saydam 0.75 dp çerçeve,
  10 sp yarı kalın metin, 10 dp ikon.
- `clearAndSetSemantics` ile ekran okuyucuya **"Kaynak: Manga-TR"** /
  **"Çevirmen: Şımarık Fansub"** şeklinde tek parça okunur.

### 3.3 Yeni metin anahtarları (TR + EN)

`app/src/main/res/values(-en)/manga_download_strings.xml`

| Anahtar | TR | EN |
| --- | --- | --- |
| `manga_download_cover` | Manga kapak görseli | Manga cover image |
| `manga_download_source_badge` | Kaynak | Source |
| `manga_download_scanlator_badge` | Çevirmen | Translator |
| `manga_download_group_ready_suffix` | ` · %1$d/%2$d hazır` | ` · %1$d/%2$d ready` |

## 4. Tasarım Önizlemesi

`docs/design_preview/manga_indirme_rozet_kapak.png` — yeni düzenin birebir ölçekli
maketi (koyu tema, gerçek renk tokenları): kapak + kaynak/dil/çevirmen rozetleri,
bölüm kartlarında ayrı kaynak ve çevirmen rozetleri, ilerleme çubuğu ve eylemler.

## 5. Değişen Dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/MangaDownloadsTab.kt`
   — yeniden yazıldı (grup modeli, kapak, rozetler, sıralama, a11y).
2. `app/src/main/res/values/manga_download_strings.xml` — yeni anahtarlar.
3. `app/src/main/res/values-en/manga_download_strings.xml` — yeni anahtarlar.
4. `docs/design_preview/manga_indirme_rozet_kapak.png` — tasarım önizlemesi.
5. `COZUM_RAPORU_MANGA_INDIRME_ROZET_KAPAK_2026-11-07.md` — bu rapor.

Başka hiçbir ekran/dosya değişmedi: `MangaDownload` modeli, indirme işçisi
(`MangaDownloadWorker`), kayıt defteri (`MangaDownloadStore`) ve klasör düzeni
(`Pictures/Kitsugi/Manga/<Kaynak_hash>/<Manga_hash>/<Bölüm>/`) **aynı kaldı**.

## 6. Neden Model/Klasör Düzeni Değişmedi

- Rozetler zaten kayıtta duran `sourceName`, `sourceLang`, `scanlator` alanlarından
  beslenir; yeni alan eklemek eski kayıtları geçersiz kılardı.
- Kapak, sayfa listesindeki ilk tamamlanmış sayfadan gelir; ek dosya/indirme yok.
- Çevirmen bilgisi grup anahtarına **eklenmedi**: aksi hâlde aynı seri, çevirmen sayısı
  kadar ayrı başlığa bölünürdü. Ayrım satır seviyesinde rozetlerle sağlanıyor.

## 7. Cihaz Test Listesi

1. Aynı manga, aynı kaynak, **iki farklı çevirmen** ile indirilmiş bölümler:
   iki satır alt alta, çevirmen rozetleri farklı renk/metinde görünmeli.
2. Aynı manga **iki farklı kaynakta** indirilmiş: iki ayrı seri başlığı, kaynak rozetleri
   farklı renkte olmalı.
3. Seri başlığında kapak, serinin ilk sayfası olmalı; ilk bölüm silinirse sıradaki
   bölümün ilk sayfası kapak olmalı.
4. İndirme sürerken (ör. 12/52) kart ilerlemeyi canlı göstermeli; `hazır/toplam`
   eki yalnızca eksik bölüm varken görünmeli.
5. Karanlık/aydınlık tema ve gradyan vurgu modunda rozet kontrastı okunur olmalı.
6. TalkBack: rozetler "Kaynak: …", "Çevirmen: …" olarak tek parça okunmalı.
7. `Çevrimdışı oku`, klasörü göster, duraklat/devam, sil akışları eskisi gibi çalışmalı.

## 8. Notlar / Sınırlar

- Kaynak kodunda derleme doğrulaması bu ortamda yapılamadı (Android SDK/JVM ve Gradle
  önbelleği yok, Maven erişimi kapalı). Değişiklikler mevcut dosyanın birebir API'leri
  (`MangaDownload`, `MangaDownloadManager`, `ChapterRecognition`, gradyan `Text`/`Icon`,
  `KitsugiColors`, `LinearProgressIndicator`) üzerine kuruldu.
- Bu çalışma `feat/golge-bahcesi-integration` (v2.4.462) tabanına oturtuldu; çünkü
  5 sekmeli yeni İndirmeler ekranı ve manga indirme altyapısı `main`'de henüz yok.
  `main`'e göre PR açılırsa dal, o entegrasyon dalının tüm commit'lerini de taşır.
- Kapak görselleri, MediaStore kaydı silinmiş/eski UID'ye aitse yer tutucu ikona düşer;
  bu durumda "klasörü göster" ile dosyalar kontrol edilebilir.
