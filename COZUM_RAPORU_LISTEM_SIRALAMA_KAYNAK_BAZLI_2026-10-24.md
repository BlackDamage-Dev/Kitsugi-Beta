# ÇÖZÜM RAPORU — Listem "Son eklenen" ve Kaynak Bazlı Sıralama (2026-10-24)

## Şikâyet (ekran görüntüleriyle)

- "Son eklenen" seçili ama **her kaynak sekmesi farklı** sıralıyor.
- Her sekmede yalnızca *bugün elle eklenen* kayıt tarihli görünüyor; geri kalan
  neredeyse tamamı **"Eklenme Tarihi Bilinmiyor"** kovasında toplanıyor
  (MAL, Shikimori, Kitsu, Simkl).
- "Tümü" sekmesi tamamen karışık: tarih bölümleri anlamsız, sıra kaynaktan
  kaynağa zıplıyor.

Yani 2026-10-23'te eklenen `addedAt` altyapısı (DB v30, dönem başlıkları, düz
global akış) **çalışıyor**; sorun verinin kendisinde ve sıralama anahtarının
kaynak başına farklı anlamlara gelmesinde.

## Kök nedenler

### 1. Satırlarda `addedAt` boş kalabiliyordu
`addedAt` v30 göçünde `updatedAt`'ten geri dolduruldu, ama göçten SONRA yazılan
satırlarda üç ayrı yol alanı boş bırakabiliyordu:

| Yol | Neden 0 kalıyordu |
|---|---|
| Çapraz eşitlemenin kardeş satır aynası (`ExternalListSyncManager` → `dao.insert`) | `baseEntry` 0 taşıyorsa aynen kopyalanıyordu |
| Yedek geri yükleme (eski yedeklerde alan hiç yok) | `addedAt = 0` |
| Çok eski sürümlerden kalan satırlar | `updatedAt` de 0 |

Sonuç: o satırlar ne sıralamaya ne de dönem bölümlerine girebiliyor, hepsi
"Bilinmiyor" kovasında birikiyor.

### 2. Simkl'de "eklenme" hiç okunmuyordu
Simkl `/sync/all-items` yanıtı **gerçek** eklenme zamanını verir
(`added_to_watchlist_at`); kod bunu yok sayıp `last_watched_at`'i "en iyi
vekil" sayıyordu. Yani Simkl'de bir bölüm izlemek, diziyi **listeye yeni
eklenmiş** gibi en üste taşıyordu. (Kaynak: simkl API referansı,
`GET /sync/all-items` → `added_to_watchlist_at`.)

### 3. Sıralama anahtarı ile dönem bölümleri farklı değer okuyordu
`applySort` çıplak `addedAt`e, "Tümü" kartı ise kopyalardan **en erken**
`addedAt`'e bakıyordu. Bir kopyada alan boşsa aynı yapım bir sekmede tarihli,
"Tümü"de bilinmiyor olabiliyordu.

### 4. Bilinmeyenlerin kendi içindeki sırası kaynaklara göre rastgeleydi
`newest` zinciri eşitlikte `updatedAt` ve **yerel `id`** ile kırılıyordu. Yerel
`id` her kaynak için ayrı bir uzaydan gelir (Simkl 150M+, Kitsu 300M+, AniList
autoincrement) → "Tümü"de aynı kovadaki kartlar kaynak karışımına göre
anlamsız sıralanıyordu.

## Kaynak kaynak: hangi alan gerçekten "eklenme" zamanı?

| Kaynak | Kullanılan alan | Nitelik |
|---|---|---|
| **AniList** | `MediaList.createdAt` (GraphQL) | ✅ gerçek eklenme |
| **Kitsu** | `library-entries.attributes.createdAt` | ✅ gerçek eklenme |
| **Shikimori** | `anime_rates/manga_rates[].created_at` | ✅ gerçek eklenme |
| **Simkl** | `added_to_watchlist_at` | ✅ gerçek (bu sürümde bağlandı) |
| **MAL** | API'de YOK → ilk-görülme (`updated_at`) | ⚠️ vekil |
| **Bangumi** | API'de YOK (`updated_at` de güvenilmez) → ilk-görülme | ⚠️ vekil |
| **TMDB / elle ekleme** | kullanıcının eklediği an | ✅ gerçek |

MAL ve Bangumi gerçekten eklenme zamanı yayınlamıyor (MAL v2 liste ucu yalnızca
`updated_at` döner; Bangumi `/v0/users/{u}/collections` yalnızca `updated_at`
taşır ve bu alan puan/yorum değişiminde güncellenmeyebilir). Bu iki kaynakta
"ilk gördüğümüz an" en dürüst değerdir ve satır bir kez damgalandıktan sonra
her eşitlemede korunur (`resolveAddedAt` önceliği).

## Yapılan değişiklikler

### 1. Simkl: gerçek eklenme zamanı
`data/auth/SimklImportManager.kt`
- `added_to_watchlist_at` → `addedAt`
- `last_watched_at` → `updatedAt` (artık iki alan ayrı)
- Alan yoksa (legacy Simkl satırları) `addedAt = 0` kalır, `resolveAddedAt`
  ilk-görülme vekilini devreye alır.

### 2. Tek çözümleyici: `effectiveAddedSeconds()`
`ui/screens/mylist/MyListFilterHelpers.kt`
- `addedAt` → yoksa `updatedAt` (normalize edilmiş: Bangumi'nin milisaniye
  yazdığı değer ×1000 ile üste çıkmasın).
- `applySort` ve `groupMyListEntriesByAddedPeriod` **aynı** değeri kullanır;
  böylece bir satır bir sekmede tarihli, diğerinde "bilinmiyor" olamaz.

### 3. "Son eklenen / İlk eklenen" karşılaştırıcısı yeniden yazıldı
- Zamanı bilinmeyenler **iki yönde de sonda**.
- Bilinmeyenler kendi aralarında **ada göre** (yerel `id` karışımı bitti).
- Bilinenlerde eşitlik → başlık → kimlik (deterministik).
- `updatedAt` artık sıralamayı bozacak bir kırıcı değil.

### 4. "Tümü" kartı aynı çözümleyiciyi kullanıyor
`ui/screens/mylist/MyListLibraryGrouping.kt`
- Grup zamanı = kopyalardaki **en erken bilinen** etkin eklenme zamanı
  (yapının listeye ilk girişi), yoksa temsilcinin etkin değeri.

### 5. Eksik `addedAt` satırlarının onarımı
`ui/screens/mylist/MyListViewModel.kt` → `repairMissingAddedAt`
- Listem açılışında `addedAt = 0` + `updatedAt > 0` satırlar tek `updateAll`
  ile damgalanır (milisaniye → saniye normalize edilerek).
- Düzeltilecek satır yoksa **hiç yazım yapılmaz** (Room invalidation yok).
- DB şeması değişmedi (v30 kalıyor) → yeni göç/schema dosyası gerekmez.

### 6. Testler
`app/src/test/java/.../MyListFilterHelpersTest.kt`
- `missingAddedAt_fallsBackToUpdatedAtInsteadOfUnknownBucket`
- `unknownAddedAt_isOrderedByTitleForDeterministicOutputAcrossSources`
- `addedPeriodGrouping_usesUpdatedAtFallback`

## Davranış sözleşmesi (özet)

1. Kullanıcı bir sıralama seçerse liste **düz global akıştır**; durum bölümleri
   sırayı ezmez (yalnızca "Varsayılan"da durum bölümleri vardır).
2. "Son eklenen / İlk eklenen"de dönem başlıkları: Bugün · Dün · Bu Hafta ·
   Geçen Hafta · Bu Ay · Geçen Ay · Son 3 Ay · Bu Yıl · Geçen Yıl · Daha Eski ·
   Eklenme Tarihi Bilinmiyor (yalnızca iki zaman damgası da boş satırlar).
3. "Tümü"de bir yapım tek karttır; zamanı kopyalar arasındaki **en erken**
   bilinen değerdir (listeye ilk giriş anı) — kaynak sekmeleriyle aynı
   çözümleyici üzerinden hesaplanır.
4. Komşu pager sekmeleri de aynı sıralamayı uygular (kaydırırken sıra sıçramaz).

## Kalan dürüst sınırlar

- **MAL / Bangumi** gerçek eklenme zamanı yayınlamadığı için bu iki kaynakta
  değer "ilk görülme"dir; kulübün eski satırları ilk eşitlemede o anla damgalanır.
- Bu ortamda Android SDK/JDK yok → derleme/`unit test` burada koşulamadı.
  Doğrulama: `./gradlew :app:testFossDebugUnitTest` ve
  `scripts/run_mylist_search_checks.sh`.
- **Depo durumu uyarısı:** GitHub'daki `main` dalı, yayındaki `v2.4.355`
  kodunun 50 commit gerisinde (yayın `arena/dac8a4b1-kitsugi-beta` dalından
  çıkmış). Bu çalışma o dalı kendi dalına birleştirdikten sonra yapıldı; aksi
  halde "düzeltme" eski kodun üstüne yazılırdı.
