# Kitsugi Beta — Manga: Çevirmen Gruplama + Arama/Listeleme İyileştirmeleri

**Tarih:** 5 Kasım 2026
**Kapsam:** Manga detay ekranı (bölüm listesi) + manga okuyucu (bölüm geçişi)
**Branch:** `arena/fa251852-kitsugi-beta`

---

## 1. Sorun

Manga eklentileri aynı bölümü birden çok çevirmen (scanlator) üzerinden sunuyor:

```
Bölüm 3   AnimeWho
Bölüm 3   WebtoonTR
Bölüm 3   Tempest Fansub
Bölüm 4   AnimeWho
```

Bu, iki ayrı kullanıcı problemine yol açıyordu:

1. **Detay ekranında gürültü:** Aynı bölüm 3 kez listeleniyor; hangisinin ne olduğu, hangisinde kaldığınız belli değil.
2. **Okuyucuda sessiz çevirmen atlaması:** "Sonraki bölüm" basıldığında sıradaki *liste elemanı* açılıyordu; bu çoğu zaman **başka bir çevirmenin** aynı bölümü ya da rastgele bir sürüm oluyordu. Kullanıcı farkında olmadan çevirmen değiştiriyor, sayfa sayısı ve konum şaşıyordu.

---

## 2. Tasarım kararları

| Karar | Gerekçe |
|---|---|
| **Grup anahtarı = bölüm numarası** (`n:3`), numara yoksa normalize edilmiş ad (`t:bölüm 3`) | Kaynaklar bölüm adını farklı yazıyor (`Chapter 3`, `Bölüm 3`, `Bölüm 3 – ...`). Numara en güvenilir ortak payda; `chapterNumber` alanı yoksa ad içindeki ilk sayı çıkarılır. |
| **Yıkıcı olmayan gruplama** (aç/kapa) | Düz listede tekilleştirme yapılsaydı diğer çevirmenlere erişim tamamen kaybolurdu. Grup satırı tek satır gösterir, dokununca sürümler açılır. |
| **Birincil sürüm = hatırlanan çevirmen → ilerlemesi olan → en yeni yükleme** | Kullanıcı bir kez çevirmen seçtiyse akış onunla devam eder; hiç seçmediyse yarım kaldığı sürüm öne çıkar. |
| **Tercih deposu: SharedPreferences** (`MangaScanlatorPreferences`) | Okuyucu ilk karesini çizerken senkron okunması gerekiyor (DataStore asenkron). Aynı gerekçe `MangaReaderPreferences` için de kullanıldı. |
| **İlerleme zaten `chapterUrl` bazlı** | Her sürümün kendi `lastPageIndex / totalPages / isCompleted` kaydı var; gruplama bunu birleştirerek gösteriyor, veri modeli değişmedi. |
| **Geçiş penceresi sessiz otomatik seçim yapmaz** | "Hangi çevirmenle devam edeyim?" sorusu ancak kullanıcı "Bir daha sorma" derse atlanır. |

---

## 3. Yeni / değişen dosyalar

| Dosya | Görev |
|---|---|
| `data/manga/MangaChapterGrouping.kt` *(yeni)* | `MangaChapterGroup` modeli + gruplama algoritması: `groupKey()`, `chapterNumberOf()`, `group()`, `pickVariant()`, `variantForScanlator()`, `normalizedScanlator()`. |
| `data/manga/MangaChapterChoice.kt` *(yeni)* | `ChapterVariantInfo` (sürüm + ilerleme), `ChapterChoiceRequest` (pencere isteği + ön seçim), `ChapterChoiceReason` (Next / Previous / Manual). |
| `data/manga/MangaScanlatorPreferences.kt` *(yeni)* | Manga bazında çevirmen tercihi: `preferredScanlator`, `groupChoice`, `autoContinue`. |
| `ui/screens/manga/components/MangaChapterChoiceDialog.kt` *(yeni)* | Ayrıntılı çevirmen seçim penceresi: sürüm kartları, sayfa ilerlemesi, yükleme tarihi, "hatırla" ve "bir daha sorma" anahtarları. |
| `ui/screens/manga/MangaDetailViewModel.kt` | `ChapterGroupRow`, `UiState.groups/visibleGroups/scanlators/scanlatorFilter/mergedScanlators/expandedGroupKeys/preferredScanlator`; `toggleGroup`, `toggleMergedScanlators`, `setScanlatorFilter`, `rememberVariant`, `clearPreferredScanlator`, `setAutoContinue`. |
| `ui/screens/manga/MangaDetailScreen.kt` | Birleşik/ayrık görünüm düğmesi, çevirmen filtre çipleri, hatırlanan tercih rozeti, açılır çevirmen satırları (`ChapterGroupListItem` + `ChapterVariantRow`). |
| `ui/screens/manga/MangaReaderViewModel.kt` | Grup tabanlı `goToNextChapter/goToPreviousChapter`, `requestGroup()` (ilerleme toplayıp pencere isteği yayınlar), `confirmChapterChoice`, `dismissChapterChoice`, `nextChapterVariantCount`. |
| `ui/screens/manga/MangaReaderScreen.kt` | Seçim penceresini bağlar, üst barda çevirmen rozeti, bölüm seçicide gruplar. |
| `ui/screens/manga/components/MangaReaderControls.kt` | `MangaChapterPickerSheet` artık gruplarla çalışıyor (dokun-aç, "Önerilen" işareti); üst bar çevirmen rozeti. |
| `ui/screens/manga/components/MangaWebtoonReader.kt` | Bölüm sonu kartında "%d çevirmen" ipucu. |
| `ui/tv/manga/TvMangaReaderScreen.kt` | TV'de HUD bölünmesin diye ön seçili sürüm sessizce onaylanır. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 24 yeni TR/EN metin. |

---

## 4. Davranış matrisi

| Durum | Sonuç |
|---|---|
| Detay ekranı, bölümün tek çevirmeni var | Tek satır; dokununca doğrudan açılır (eski davranış). |
| Detay ekranı, bölümün N>1 çevirmeni var | Tek satır + `N çevirmen` rozeti + aç/kapa oku; açılınca her sürüm ayrı satır (çevirmen adı, sayfa, tarih, okundu işareti). |
| Okuyucuda "sonraki bölüm", hedef bölüm tek çevirmenli | Doğrudan geçer. |
| Okuyucuda "sonraki bölüm", hedef bölüm çok çevirmenli, "bir daha sorma" kapalı | Ayrıntılı pencere: her çevirmenin adı, bölüm adı, yükleme tarihi, kaldığı sayfa; ön seçim hatırlanan çevirmen. |
| Okuyucuda "sonraki bölüm", "bir daha sorma" açık | Hatırlanan çevirmenle doğrudan geçer (hatırlanan yoksa pencere yine açılır). |
| Bölüm seçici (okuyucu içi) | Gruplar listelenir; çok çevirmenli grup dokunuşta açılır, sürüm seçimi doğrudan açılır. |
| TV okuyucu | Ön seçili sürüm sessizce onaylanır (HUD odak yönetimi bozulmasın). |
| Detay ekranında çevirmen filtresi | Çip ile yalnızca o çevirmenin bölümleri; filtre sonucu boşsa bilgilendirme satırı. |

---

## 5. Kenar durumları (ele alınan)

- **Bölüm numarası yok / ad sayı içermiyor:** `t:<ad>` anahtarı ile ada göre gruplanır; aynı ad = aynı bölüm.
- **`scanlator` null veya boş:** "Çevirmen belirtilmemiş" olarak gösterilir, gruplama yine çalışır.
- **Farklı yazımlar (`AnimeWho` / `animewho`):** Normalize edilmiş karşılaştırma.
- **Navigasyondan gelen bölüm listede yok (URL farklı):** `currentGroupIndex()` önce URL ile, bulamazsa bölüm numarasıyla eşleştirir; "sonraki bölüm" yine çalışır.
- **Hızlı ardışık geçiş:** İlerleme sorgusu bitmeden bölüm değişirse pencere yayınlanmaz (bayat istek koruması).
- **Aynı bölümde çevirmen değiştirme:** İlerleme `chapterUrl` bazlı olduğu için her sürüm kendi kaldığı sayfadan açılır.
- **Grup içi okundu durumu:** Satır, sürümlerden herhangi biri bitmişse "okundu" görünümü alır; sayfa ilerlemesi birincil sürümden gelir.

---

## 6. Doğrulama

Bu ortamda **Android SDK / JDK / Gradle yok**, derleme yapılamıyor. Yapılan statik doğrulamalar:

| Kontrol | Sonuç |
|---|---|
| Süslü parantez / normal parantez / köşeli parantez dengesi (yorum ve string'ler ayıklanarak), 11 dosya | 0 hata |
| String literal sayısı tekliği (odd-quote) | 0 hata |
| `R.string.*` referanslarının `values*/strings.xml` içinde varlığı (11 dosya) | 0 eksik |
| `values/strings.xml` ve `values-en/strings.xml` XML geçerliliği | İkisi de geçerli, 17.593 string, 0 tekrar |
| TR/EN string sayısı eşitliği | 17.593 / 17.593 |

> Not: `lint { abortOnError = false }` olduğu için derleme hataları yalnızca gerçek Kotlin derlemesinde görülebilir; cihaz/emülatör testi gerekiyor.

---

## 7. Cihaz test listesi

1. Detay ekranı: aynı bölümün 3 çevirmeni olan manga → **tek satır + "3 çevirmen"** görünüyor mu?
2. Satıra dokun → çevirmen listesi açılıyor, tekrar dokun → kapanıyor mu?
3. Açılan listeden bir çevirmen seç → doğru sürüm açılıyor, ilerleme o sürümün sayfasından mı geliyor?
4. Üst bardaki birleştir/ayır ikonu → tüm sürümler ayrı satır olarak listeleniyor mu?
5. Çevirmen filtre çipi → yalnız o çevirmenin bölümleri; "Tümü" ile geri dönüş; sonuç yoksa uyarı satırı.
6. "Tercih: AnimeWho" rozeti → X ile temizlenince ön seçim değişiyor mu?
7. Okuyucuda son bölüme gel → "Sonraki bölüm" → çok çevirmenli hedefte **ayrıntılı pencere** açılıyor mu (çevirmen adı, sayfa, tarih)?
8. Pencerede başka çevirmen seç → "Devam et" → o sürüm açılıyor, sayfa sayacı doğru mu?
9. "Bir daha sorma" açık + devam et → sonraki geçişte pencere çıkmadan aynı çevirmenle geçiyor mu?
10. "Vazgeç" → pencere kapanıyor, mevcut bölüm/sayfa değişmiyor mu?
11. Uzun basılan/dokunulan bölüm seçici (okuyucu içi) → çok çevirmenli grupta aç/kapa, tek sürümde doğrudan açılış.
12. Webtoon modunda bölüm sonu kartı → "%d çevirmen arasından seçim yapacaksın" ipucu görünüyor mu?
13. TV: "sonraki bölüm" → pencere açılmadan ön seçili sürümle geçiyor mu (HUD odağı bozulmuyor mu)?
14. RTL / LTR / Dikey / Webtoon modlarının hiçbiri bozulmadı mı?
15. Ayar ekranı, kütüphane, anime tarafı etkilenmedi mi (regresyon).

---

## 8. Bilinen sınırlar / sonraki adımlar

- **TV detay ekranı hâlâ düz liste:** Gruplama telefon/tablet detay ekranında; TV listesi odak (`focusRestorer`, `dpadRepeatThrottle`) davranışını bozmamak için değiştirilmedi. TV'de grup + varyant seçimi ayrı bir odak tasarımı gerektiriyor.
- **Grup bazında toplu ilerleme göstergesi** (ör. "3 sürümden 1'i okundu") satır altında tek satır özet olarak yok; rozet + sürüm satırlarındaki işaretlerle veriliyor.
- **Tarih biçimi** cihaz yereline göre (`d MMM yyyy`); kaynak saniye/milisaniye karışık gönderirse ikisi de tolere ediliyor.
- **Çevirmen tercihi manga bazında**, global "hep aynı çevirmen" ayarı yok (istenirse `MangaScanlatorPreferences` içine global anahtar eklenebilir).

---

## 9. Ek paket: arama / bulma / listeleme tarafları (aynı turda)

Kullanıcı "manga arama, listeleme alanları da tamam mı?" diye sorduğu için aynı
turda **keşfet/arama** ve **bölüm listesi** taraflarına da dokunuldu. Hepsi
mevcut akışları bozmadan eklenen, bağımsız çalışan özellikler:

### 9.1 Keşfet / Arama ekranı (`MangaBrowseScreen`)

| Özellik | Ne yapıyor |
|---|---|
| **Son aramalar** | `MangaSearchHistory` (SharedPreferences). Arama sonuçlandığında sorgu geçmişe yazılır (en yeni başta, en fazla 12, büyük/küçük harf duyarsız tekilleştirme). Arama kutusu boşken çip olarak listelenir; dokun → aramayı tekrar çalıştır, çipteki **X** → geçmişten sil, **Temizle** → tümü. |
| **Aramayı temizle** | Arama kutusunun sağındaki **X** ikonu; klavye "ara" tuşu (`ImeAction.Search`) odağı kapatır. |
| **Sıralama** | `İlgi` (başlık benzerlik skoru — mevcut davranış) / **A-Z** (harf sırası, `CASE_INSENSITIVE_ORDER`). Yalnızca sonuç varken görünür. |
| **Boş sonuç önerileri** | "Sonuç bulunamadı" ekranında: kaç kaynağın yanıt vermediği, son aramalardan hızlı tekrar deneme çipleri ve doğrudan **Kaynak ekle** (eklenti ayarları) düğmesi. |

### 9.2 Bölüm listesi (`MangaDetailScreen` + `MangaDetailViewModel`)

| Özellik | Ne yapıyor |
|---|---|
| **Bölüm arama** | 5'ten fazla bölümü olan mangalarda listenin üstünde kompakt arama alanı. `"42"` yazınca 42, 42.0, 42.5 numaralı bölümler de bulunur; metin araması ad içinde geçer. |
| **Yalnızca okunmamış** | Tek dokunuşla bitirilmiş bölümler gizlenir; çevirmen filtresi + arama ile birlikte çalışır. |
| **Okundu/okunmadı işaretleme** | Her bölüm (ve açılan listede her çevirmen sürümü) için küçük onay ikonu: başka uygulamada okunan bölümler işaretlenir, yanlışsa geri alınır. Room'a `chapterUrl` bazında yazılır. |
| **Görünenleri okundu işaretle** | Başlıktaki `DoneAll` düğmesi; **onay penceresi** kaç bölümün işaretleneceğini söyler. Filtre aktifse yalnızca görünen bölümler işaretlenir. |
| **Filtre temizleme** | Filtreler sonucu boşaltırsa "Eşleşen bölüm yok" + **Filtreleri temizle**. |

> Bu iki dosya dışında hiçbir ekran/akış değişmedi: okuyarak ilerleme kaydı,
> kaynak sağlık ekranı, eklenti yönetimi ve TV ekranları aynı kaldı.

### 9.3 Bilinçli olarak dokunulmayanlar

- **TV keşfet/arama ekranı** (`TvMangaBrowseScreen`) ve **TV bölüm listesi**: arama geçmişi/sıralama orada D-pad odak akışına göre yeniden tasarlanmalı; bu turda değiştirilmedi.
- **Çevrimdışı indirme kuyruğu / toplu indirme**: büyük bir özellik, ayrı tur.

---

## 10. Tema uyumu, düzen ve sayfa görüntüleme sağlamlığı

Kullanıcı "hepsi uygulama temamla tam uyumlu ve görsel olsun, sayfa görüntüleme
sorunsuz çalışsın, düzen kullanıcı dostu olsun" dediği için bu turda üç eksen
birlikte elden geçirildi.

### 10.1 Tema uyumu (açık / koyu / AMOLED + dinamik vurgu)

Uygulamada üç renk şeması (`KitsugiDarkColors`, `KitsugiLightColors`,
`KitsugiAmoledColors`) ve kullanıcı seçimli vurgu rengi var. Okuyucu tarafında
sabit mor (`0xFF7C4DFF`) ve sabit beyaz yazılar kullanılıyordu; açık temada
okunmuyor, farklı vurgu renginde de aykırı kalıyordu.

| Dosya | Değişiklik |
|---|---|
| `MangaReaderControls.kt` | `AccentPurple = Color(0xFF7C4DFF)` → `ReaderAccent` = **tema vurgusu** (`LocalKitsugiAccent`). Ayar paneli + bölüm seçici paneli artık `KitsugiColors.TextPrimary/Secondary/Muted`, `KitsugiColors.SurfaceStrong`, `KitsugiColors.Border` kullanıyor → açık temada da okunaklı. Okuyucunun üst/alt barları (görsel üzerine binen katman) bilinçli olarak koyu/beyaz kaldı: her görselin üstünde okunabilirlik bunu gerektiriyor. |
| `MangaChapterChoiceDialog.kt` | Tüm renkler tema tabanlı; seçili kart vurgu rengiyle, "Önerilen" rozeti vurguyla, ilerleme satırı `KitsugiColors.AccentGreen` ile. Devam düğmesi `Accent + OnAccent` (açık vurguda siyah yazı). |
| `MangaReaderScreen.kt`, `MangaPageItem.kt`, `MangaWebtoonReader.kt` | Yükleme göstergesi, "Tekrar dene", hata ikonu ve bölüm sonu düğmesi tema vurgusu + `OnAccent`. |
| `MangaBrowseScreen.kt` | Nötr çipler `KitsugiColors.SurfaceStrong`, hata renkleri `KitsugiColors.AccentRed` (sabit `Color.White/Red` yok). |

### 10.2 Düzen / alan kullanımı

| Yer | İyileştirme |
|---|---|
| Çevirmen penceresi | İçerik **tek `LazyColumn`**: başlık + sürümler + anahtarlar + düğmeler birlikte kayar. Yükseklik `ekran × 0.72` ile sınırlı → yatay telefonda (≈360 dp) taşma yok, küçük ekranda kırpılma yok. |
| Bölüm seçici paneli | Sabit `460.dp` yerine `ekran × 0.62` → yatayda sığar, büyük ekranda daha çok bölüm görünür. |
| Detay başlığı | Üç aksiyon düğmesi 36 dp'ye çekildi, başlık `maxLines = 1 + ellipsis` → dar ekranda "Bölümler (312)" satırı kaymıyor. |
| Keşfet ekranı | Ekran yüksekliği < 620 dp ise üstteki bilgi kartı gizlenir; ızgara alanı korunur (yatay telefon). |
| Bölüm içi arama | Yalnızca 5'ten fazla bölümde görünür (kısa listelerde gereksiz yer kaplamaz). |
| Paneller | `KitsugiSheetOrDialog` zaten `max 640.dp` genişlik veriyor → tablette satırlar aşırı uzamıyor. |

### 10.3 Sayfa görüntüleme sağlamlığı

| Sorun | Çözüm |
|---|---|
| **Webtoon'da yer tutucu sabit 480 dp** | `MangaPageSizeMemory`: yüklenen son sayfanın en-boy oranı hatırlanır, yer tutucu ve hata kartı `aspectRatio` ile o orana göre çizilir → kaydırma sıçraması azalır, liste yüksekliği kararlı. |
| **Arşiv (ZIP/CBZ) sayfaları hiç çizilmiyordu** | `MangaPageLoaderV2` arşiv girişini `page.stream`'e koyuyordu; görüntüleyici ise Coil'e dosya/URL veriyor. Artık arşiv sayfası diske yazılıp `archive://…` anahtarıyla önbellekten servis ediliyor (`imageUrl` dolu) → Coil dosyayı okuyabiliyor. |
| **Her yeniden çizimde disk erişimi** | `cacheFile.exists()` çağrısı `remember(imageUrl, page.status)` içine alındı; ayrıca `length() > 0` kontrolü eklendi (0 baytlık dosya "hazır" sayılmıyordu). |
| Renk filtresi / ayna / sığdırma | Değişmedi; `ZoomableAsyncImage` + Telephoto alt örnekleme (OOM riski yok), pinch/çift dokunma korunuyor. |
| Önbellek doğrulama | `MangaCache.isImageInCache` HTML/hata yanıtlarını ayıklayıp siliyor; arşiv anahtarı da aynı doğrulamadan geçiyor. |

> Not: Bu ortamda derleyici yok; doğrulama statik (parantez dengesi, string
> referansları, sembol/import tutarlılığı) ve cihaz testi gerekiyor. Test listesi §7'de,
> tema/düzen maddeleri §10.4'te.

---

## 11. Uygulama dili = arama dili (dil bağımsız arama)

Kullanıcı isteği: *"Hangi kaynaktan olduğu fark etmeksizin manga / light novel /
manhwa… benim seçtiğim dile uymalı; manga kısmı, 'Oku' butonu ve detay
sayfasındaki bölüm kısmı da uygulama içi seçtiğim dille arama yapmalı."*

### 11.1 Kök neden

| Sorun | Neden |
|---|---|
| Anime detayındaki **"Oku"** butonu boş/alakasız sonuç gösteriyordu | `openMangaBrowse(entry.title)` yalnızca **tek** başlığı (ekranda görünen başlık) kaynaklara gönderiyordu. Kaynak eseri kendi dilindeki adla listeliyorsa (ör. Manga-TR: "Sınıfın Seçkinleri", AniList: "Youkoso Jitsuryoku…") arama **0 sonuç** dönüyordu. |
| Farklı dildeki başlıkla bulunan sonuçlar ekranda kayboluyordu | Sonuçlar tekrar `SearchResultScorer` ile **orijinal sorguya** göre puanlanıyordu; çeviri başlıklar benzerlik eşiğinin altında kalıp filtreleniyordu. |
| Kaynak sırası dilden bağımsızdı | `MangaSearchCoordinator` yalnızca "Türkçe kaynak önce" kuralını biliyordu; uygulama dili İngilizce seçilse de ilk kaynak Türkçe sitede kalıyordu. |
| Manga arayüzü metinleri sabit Türkçeydi | Detay/okuyucu/kaynak sağlığı/TV ekranlarında `"Baştan Oku"`, `"Tamamlandı"`, hata mesajları… doğrudan Kotlin literal'iydi; dil değişince değişmiyordu. |

### 11.2 Yapılanlar

1. **Çok dilli arama** — yeni `MangaSearchAlternates`: sorgu + diğer başlıklar (romaji / İngilizce / Japonca / yerel) tekilleştirilip sıraya konur.
   - Navigasyon `openMangaBrowse(query, mediaId, alternates)` ile başlık varyantlarını taşır: `MediaEntry.title + titleEnglish + titleJapanese`, `JikanSearchResult.title + titleRomaji + titleEnglish + titleJapanese`.
   - `MangaBrowseViewModel.searchSourceWithFallback` sırayla dener: ham sorgu → kanonik çekirdek/ASCII → diğer başlıklar (en fazla 4 başlık). **Sonuç bulan ilk sorgu** kaynağa yazılır (`resolvedQuery`).
2. **Puanlama/filtre düzeltmesi** — sonuç, hangi sorguyla bulunduysa o sorguya göre puanlanır. Farklı dildeki başlıkla bulunduğunda başlık benzerliği doğal olarak düşük çıktığı için "kaynağa güven" yoluna geçilir (ham sonuç, URL'e göre tekilleştirilmiş).
3. **Sayfalama** — 2. sayfa artık ham sorguyla değil, **sonuç getiren sorguyla** istenir; aksi hâlde alternatif başlıkla bulunan sonuçların devamı boş dönüyordu.
4. **Sıralama** — keşfet listesi, denenen **tüm** başlıklara göre en yüksek benzerlik puanıyla sıralanır; sonra kaynak önceliği.
5. **Dile göre kaynak önceliği** — `LocaleCache.localeTag` (yoksa sistem dili) ile kaynakların `lang` alanı eşleştirilir; eşleşenler **kararlı** biçimde başa alınır (aynı grup içinde mevcut öncelik/sağlık sırası korunur). Popüler listesi ve kaynak çipleri de bu sırayı kullanır.
6. **Arayüz metinleri** — 46 yeni string (TR + EN). Manga durum etiketleri ortak `MangaStatus.labelRes()` üzerinden; okuyucu/detay hata mesajları `context.getString(...)` ile; kaynak sağlığı ekranındaki İngilizce sabitler ("Healthy", "Success / Failure", "Search"…) ve TV etiketleri de kaynaklara taşındı.
7. **Bölüm aramasında tutarlılık (mantık hatası)** — "çevirmenleri birleştir" kapatıldığında bölüm araması yalnızca **ada** bakıyordu; `42` yazınca 42.5 bulunmuyordu. Artık iki görünüm de aynı yardımcıyı (`chapterRowMatchesQuery`) kullanıyor.

### 11.3 Doğrulama

- Her iki `strings.xml` **17.657** anahtar, 0 yinelenen, TR/EN anahtar kümeleri **birebir aynı**.
- Kodda kullanılan tüm yeni `R.string.*` referansları çözümlendi (eksik referans sayısı 199 → 154; kalanlar bu değişiklikten önce de vardı ve bu turda dokunulmayan modüllere ait).
- Değişen 17 Kotlin dosyasında parantez/süslü dengesi 0; manga literal’i kalmadı (grep: manga ekranlarında yalnızca KDoc yorumları).
- Cihazda denenecekler: (a) anime detayı → "Oku" → Manga-TR sonucu görünmeli, (b) keşfet → çevrimdışı kaynak → hata metni seçili dilde, (c) Ayarlar → Dil = English → detay/okuyucu/sağlık ekranı İngilizce.
