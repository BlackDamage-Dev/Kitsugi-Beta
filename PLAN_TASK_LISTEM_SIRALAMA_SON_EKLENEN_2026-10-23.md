# Task Plan — Listem Sıralaması: "Son Eklenen" + Tüm Sıralama Mantıkları (2026-10-23)

**Tarih:** 2026-10-23
**Sürüm:** v2.4.341
**Branch / Commit:** `arena/09a4c1cf-kitsugi-beta` / `4c98e5d` (+ bu plan dosyası)
**Kapsam:** Listem filtre panelindeki "Sıralama" seçeneklerinin tamamı; özellikle
"Son eklenen"in eklenme tarihine göre ve durum bölümlerinden bağımsız (global)
çalışması. Aynı düzeltme başkasının listesi ekranı ve TV kütüphanesine de yansır.
**İlgili rapor:** `COZUM_RAPORU_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md`

---

## 1. Kullanıcı bildirimi

> "Listem sayfasında şu son eklenen özelliği düzgün çalışmıyor. Bunu seçtiğimiz
> zaman artık özel bir mantığı olsun; Listem sayfası eklenme tarihine göre
> listeleyip gösterebilsin verileri. Hani bu seçili olduğu zaman
> planlandı/tamamlandı/izleniyor'a göre sıralanması bozuyor tüm işi. Ayrıca
> bu tarz mantıklar diğer seçeneklere özgü de eklenmeli — ilk eklenen, ada göre vs."

(Ekran görüntüsü: Filtre panelinde Durum = Tamamlandı, Sıralama = "Son eklenen"
işaretli; yatay/tablet görünümünde Listem ızgarası.)

---

## 2. Kök nedenler

1. **Gerçek eklenme tarihi yoktu.** `applySort("newest")` yerel satır `id`'sine
   bakıyordu. `id` = yerel DB'ye yazım sırası; AniList içe aktarımı
   `sort: [MEDIA_ID]` ile sayfaladığı için "Son eklenen" fiilen "medya ID'si
   en büyük" demekti. Sil-yeniden-yaz senkronları sırayı ayrıca karıştırıyordu.
2. **Durum bölümleri sıralamayı eziyordu.** Liste sıralandıktan sonra her zaman
   İzleniyor → Tekrar → Planlandı → Durduruldu → Bırakıldı → Tamamlandı
   bölümlerine ayrılıyordu; seçilen sıralama yalnızca bölüm içinde geçerliydi.
   Tek istisna durum filtresi = "completed" özel durumu idi.
3. **Seçenek kusurları:** puansızlar artan puanda başa kaçabiliyordu; İlerleme
   ham bölüm sayısıydı (100/1000 > 12/12); Ada göre ekrandaki değil depolanan
   romaji başlığı sıralıyordu; Bangumi `updatedAt` milisaniye, Simkl `0`
   yazıyordu (Son Güncelleme bozuktu); komşu pager sekmeleri sıralamasız
   çizildiği için sekme yerleşince sıra sıçrıyordu.

---

## 3. Plan / Yapılan işler

### 3.1 Veri katmanı — `addedAt` (DB v29 → v30)
- [x] `model/MediaEntry.kt`: `addedAt: Long = 0L` (epoch sn, 0 = bilinmiyor),
  kurucuda son parametre. `normalizeToEpochSeconds()` yardımcısı (ms → sn).
- [x] `data/local/MediaEntryEntity.kt`: kolon + toDomain/toEntity eşlemesi.
- [x] `data/local/KitsugiDatabase.kt`: sürüm 30, `MIGRATION_29_30`
  (ADD COLUMN + `updatedAt`'den geri dolgu, milisaniyeler saniyeye çevrilerek).
- [ ] İlk yerel derlemede KSP'nin üreteceği
  `app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/30.json`
  commit'lenecek (bu ortamda Android SDK/JDK yok; dosya üretimde
  beklenmiyor, yalnızca şema arşivi/testler içindir).

### 3.2 Kaynaklardan gerçek eklenme zamanı
- [x] `data/auth/AniListImportManager.kt`: GraphQL'e `createdAt` eklendi → `addedAt`.
- [x] `data/auth/KitsuApiClient.kt` + `KitsuImportManager.kt`: `attributes.createdAt`.
- [x] `data/auth/ShikimoriApiClient.kt` + `ShikimoriImportManager.kt`: `created_at`.
- [x] `data/auth/SimklImportManager.kt`: `last_watched_at` → `updatedAt` (vekil;
  Simkl eklenme zamanı vermez). MAL/Bangumi: alan yok, ilk-görülme korunur.

### 3.3 Repository — eklenme zamanı asla kaybolmaz / uydurulmaz
- [x] `data/local/MediaEntryRepository.kt`: `resolveAddedAt()` — öncelik:
  kaynağın gerçek değeri > yerelde sabitlenmiş değer > ilk yazımda `updatedAt`
  vekili > yalnızca tekil kullanıcı eklemesinde "şimdi".
- [x] `insert` / `insertAll` / `smartImport` (ekleme + güncelleme kolu) /
  `update` (mevcut değeri korur, "şimdi" yazmaz) / `replaceAll`.
- [x] `replaceSourcePreservingAdultFlags`: +18 işaretleri gibi `addedAt` de
  kimlik üzerinden yeni satırlara geri yazılır (sil-yeniden-yaz kaybı yok).
- [x] `data/local/MediaEntryBackup.kt`: yedeğe `addedAt` yazılır/okunur;
  birleştirme kolunda korunur. (Eski yedekler 0 ile okunur, vekil devreye girer.)
- [x] `ui/screens/mylist/MyListLibraryGrouping.kt`: birleşik "Tümü" kartında
  `addedAt` = kopyalardaki en erken bilinen değer.

### 3.4 Sıralama motoru (`ui/screens/mylist/MyListFilterHelpers.kt`)
- [x] `applySort` yeniden yazıldı: anahtarlar satır başına bir kez (Schwartzian),
  her seçeneğe kendi mantığı + deterministik eşitlik zinciri:
  - newest/oldest → `addedAt` (bilinmeyen iki yönde de sonda)
  - title/title_desc → başlık dili uygulanmış ekrandaki başlık; eşit adda yıl artan
  - score/score_asc → puansız sonda
  - progress/progress_asc → tamamlanma oranı (progress/total, 0..1)
  - favorites → favoriler önde, küme içinde son eklenen üste
  - start/end date, year, priority → değersiz sonda; tarihler `yyyy-MM-dd` normalize
  - updated_desc/asc → normalize edilmiş zaman
- [x] `MY_LIST_SORT_DEFAULT = "default"`, `isExplicitMyListSort()`,
  `myListUsesFlatSortedLayout()` (sunum sözleşmesi, §3.5),
  `myListSortUsesAddedDateSections()`.
- [x] `MyListAddedPeriod` + `groupMyListEntriesByAddedPeriod()`:
  Bugün / Dün / Bu Hafta / Geçen Hafta / Bu Ay / Geçen Ay / Son 3 Ay /
  Bu Yıl / Geçen Yıl / Daha Eski / Bilinmiyor.
- [x] `getSortTitle`: "default" → Varsayılan; eski "added" takma adı korunur.

### 3.5 Sunum sözleşmesi (isteğin özü)
- [x] Kullanıcı bir sıralama seçerse → **düz global akış; sıralama kazanır.**
  Durum bölümleri yalnızca "Varsayılan" sıralama + Durum = Tümü + ayrılmış
  liste tarzı açıkken gösterilir. Tek durum filtresi de düz çizer.
- [x] "Son eklenen / İlk eklenen" düz akışta **dönem başlıklarıyla** çizilir
  (`MyListAddedPeriodContent`).
- [x] `ui/screens/mylist/MyListGridContent.kt`: ortak bölüm çizicisi
  (`myListSectionContent`); durum bölümleri ve dönem bölümleri paylaşır.
- [x] `ui/screens/mylist/MyListComponents.kt` (`MyListContentPage`): karar
  merkezi kuraldan; komşu pager sekmeleri de aynı sıralamayı uygular
  (sıçrama yok); boş-durum kartındaki sabit `"newest"` etiketi düzeltildi.
- [x] `ui/screens/mylist/MyListScreen.kt`: sıralamaya `titleLanguage` geçilir;
  aktif-filtre hesabı `isExplicitMyListSort` kullanır; içerik sayfasına
  `selectedSortId` taşınır.
- [x] `ui/screens/mylist/KitsugiMyListFilterComponents.kt` + 
  `components/KitsugiMyListSortMenu.kt`: seçeneklere "Varsayılan" eklendi
  (yönsüz satırda ok gösterilmez); "Temizle" artık varsayılana döner.
- [x] `ui/screens/profile/KitsugiUserMediaListScreen.kt`: aynı sözleşme;
  AniList `createdAt` modele `addedAt` olarak akar; ekrana özel newest/oldest
  dalı silindi; varsayılan başlangıç sıralaması "default".
- [x] TV kütüphanesi (`TvLibraryScreen`) yeni motordan otomatik faydalanır
  (çağrı biçimi uyumlu, kod değişikliği gerekmedi).

### 3.6 Kayıtlı seçim göçü (`ui/app/AppViewModel.kt`)
- [x] Varsayılan başlangıç değeri "newest" → `MY_LIST_SORT_DEFAULT`.
- [x] `migrateLegacyDefaultSort()`: kayıtlı eski "newest"/"added" yalnızca
  İLK okumada varsayılana taşınır (güncelleme sonrası ani görünüm değişimi
  olmasın). `updateMyListSort` bilinçli seçim bayrağı
  (`sort_default_v2_done`) yazar; kayıt yoksa prefs'e hiç yazılmaz
  (`MyListTabRestoreTest`'in "geri yükleme yazmaz" sözleşmesi korunur).

### 3.7 Metinler
- [x] `res/values/strings.xml` + `res/values-en/strings.xml`:
  `mylist_sort_default`, `mylist_added_*` (11 dönem etiketi).

---

## 4. Değişen dosyalar (27 dosya; bu plan dahil)

```
COZUM_RAPORU_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md
PLAN_TASK_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md        (bu dosya)
release_notes_v2.4.341.md
app/src/main/java/com/kitsugi/animelist/data/auth/AniListImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/KitsuApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/auth/KitsuImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/SimklImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/local/KitsugiDatabase.kt
app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryBackup.kt
app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryEntity.kt
app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt
app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt
app/src/main/java/com/kitsugi/animelist/ui/app/AppViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/KitsugiMyListFilterComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/components/KitsugiMyListSortMenu.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/test/java/com/kitsugi/animelist/data/local/AddedAtResolutionTest.kt
app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt
```

---

## 5. Doğrulama

- [x] Kod gözden geçirme: dosya dosya diff denetimi; parantez/süslü dengesi
  eski=yeni karşılaştırması (tüm dosyalarda korundu).
- [x] Paralel düzenleme ezilmesi denetimi yapılıp eksikler sırayla yeniden
  uygulandı; `addedAt`/`createdAt` referanslarının tamamı doğrulandı.
- [x] Birim testleri yazıldı:
  - `MyListFilterHelpersTest`: addedAt vs yerel id, bilinmeyen tarihin iki
    yönde de sona düşmesi, puansız-sonda, ilerleme oranı, ms→sn normalize,
    başlık dili, düz-akış karar tablosu, dönem gruplaması. (Mevcut üç test
    + puan testi beklentisi aynen korunuyor.)
  - `AddedAtResolutionTest`: `resolveAddedAt` öncelik zinciri (5 senaryo).
- [ ] **Yerelde derleme + test koşumu:** bu ortamda Android SDK/JDK yok
  (ağ çıkışı asset hostunu kapsamıyor; JDK indirilemedi).
  Komut: `./gradlew :app:testFossDebugUnitTest` — ardından üretilen
  `30.json` şemasını commit'le.
- [ ] **Cihazda el ile kontrol:**
  1. Listem → Filtre → Sıralama = "Son eklenen": dönem başlıkları + global tarih sırası.
  2. Durum = Tamamlandı + "Son eklenen" (ekran görüntüsündeki durum): düz, bölümsüz.
  3. Bir kez senkron → AniList kayıtlarında sıralamanın gerçek eklenme tarihine oturması.
  4. Sıralama = Varsayılan: klasik İzleniyor/Planlandı/… bölümlü gezinme geri gelir.
  5. Yeni kayıt ekle → "Son eklenen → Bugün" bölümünün en üstünde görünür.

## 6. Bilinen sınırlar / takip önerileri

- Göç geri dolgusu `updatedAt` tahminidir; gerçek tarihler
  AniList/Kitsu/Shikimori'de **ilk senkron sonrası** netleşir.
  MAL/Simkl/Bangumi eklenme zamanı vermez → "ilk görülme" esastır.
- İlerleme'nin oran mantığı bilinçli davranış değişikliğidir; istenirse
  `MyListFilterHelpers.applySort` içinden tek yerden ham sayıya dönülebilir.
- Dönem başlıkları yalnızca Son/İlk eklenen'dedir. İleride istenirse Puan
  için skor kovaları, Başlangıç tarihi için yıl başlıkları aynı bölüm
  çizicisiyle eklenebilir (takip işi, bu görevin kapsamında değildi).
