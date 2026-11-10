# Listem — "Son eklenen / İlk eklenen" ve Tarih Bölümleri Kök Neden & Çözüm Raporu

**Tarih:** 2026-10-24  
**Kapsam:** Listem sekmesi (Tümü + AniList / MAL / Simkl / Kitsu / Shikimori / Bangumi), sıralama çipleri, tarih bölümleri, veri tabanı göçü, 6 sağlayıcının içe aktarma eşlemesi.

---

## 1. Kullanıcının gördüğü belirtiler

- "Son eklenen" seçiliyken aynı yapım bir sekmede **Bugün**, bir başka sekmede **Dün** altında görünüyor
  (örn. *Weathering with You* bugün eklendiği hâlde başka kaynaklarda "Dün").
- Bazı kaynak sekmelerinde yeni eklenen yapım **hiç en üstte değil**; sıralama her yenilemede değişiyor.
- "Ada göre / Puan / İlerleme" gibi çiplerle birlikte liste düzeninin tutarsız olması.

---

## 2. Kök nedenler (kod kanıtlı)

| # | Kök neden | Kanıt |
|---|-----------|-------|
| 1 | **"Son eklenen" yerel satır `id`'sine göre sıralanıyordu.** `id` Room'un otomatik artan anahtarıdır; satırın ne zaman eklendiğini değil, içe aktarmanın hangi sırada yazıldığını gösterir. | `MyListFilterHelpers.applySort`: `"newest" -> entries.sortedByDescending { it.id }` |
| 2 | **`MediaEntry` içinde "koleksiyona eklenme" alanı yoktu.** Yalnızca `updatedAt` (son değişiklik) vardı. Bu yüzden "eklenme tarihi" hiçbir sağlayıcıdan okunamıyordu. | `model/MediaEntry.kt`, `data/local/MediaEntryEntity.kt` |
| 3 | **Gece senkronu ve bildirim işçisi kaynağı silip yeniden yazıyor.** `replaceSourcePreservingMetadata()` → `dao.deleteBySource()` + `insertAll()`; her satır yeni bir autoincrement `id` alır. Sıralama id'ye bağlı olduğu için liste her senkronda yeniden diziliyordu. | `MediaEntryRepository.replaceSourcePreservingMetadata()`, `KitsugiApplication` (anilist/mal/simkl), `AiringNotificationWorker` (simkl) |
| 4 | **Bangumi zaman damgası milisaniye olarak yazılıyordu.** `parseBangumiTimestamp` `toEpochMilli()` döndürürken `updatedAt` (saniye bekleniyor) alanına atanıyordu → Bangumi satırları "Son güncelleme" sıralamasında yapay olarak en üste çıkıyordu. | `BangumiImportManager.parseBangumiTimestamp` |

> Not: Kullanıcının cihazındaki tarih bölümleri (Bugün / Dün / Bu Hafta / Geçen Hafta / Eklendiği Tarih
> Bilinmiyor) mevcut kod tabanında **yoktu**. Bu çalışmada bölümler de bu depoya eklendi; böylece
> bölümlerin sırası ve etiketi aynı `addedAt` sözleşmesinden besleniyor.

---

## 3. Veri modeli değişikliği

`MediaEntry` / `MediaEntryEntity` alanı eklendi:

```kotlin
/** Koleksiyona eklenme anı, epoch SANİYE. 0 = bilinmiyor. */
val addedAt: Long = 0L
```

- `updatedAt` **değişmez**: durum/puan/ilerleme güncellemeleri `addedAt`i kaydırmaz.
- `update()` kural: veritabanındaki `addedAt` esastır; çağıran yalnızca **daha eski** bir tarih
  biliyorsa (içe aktarma düzeltmesi) onu geçirebilir. Böylece sıradan bir düzenleme tarihi ileri
  taşımaz, ama gerçek `created_at` bulunduğunda değer geriye (gerçeğe) düzelir.
- Yedek JSON'u (`MediaEntryBackup`) `addedAt` alanını yazar/okur; eski yedekler `0` ile açılır ve
  `updatedAt`e düşer (geriye dönük uyumlu).
- Bekleyen senkron kuyruğu (`PendingSyncDrainer`) aynı JSON'u kullandığı için alan otomatik taşınır.

### Veri tabanı göçü `v29 → v30`

```sql
ALTER TABLE media_entries ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0;

-- Eski satırlarda gerçek eklenme anı yok: en yakın sinyal `updatedAt`.
-- Bangumi'de eski sürümün yazdığı milisaniye değerleri saniyeye çevrilir.
UPDATE media_entries
SET addedAt = CASE WHEN updatedAt > 100000000000 THEN updatedAt / 1000 ELSE updatedAt END
WHERE updatedAt > 0;

-- Yan etki düzeltmesi: `updatedAt`in kendisi de milisaniye kalmış Bangumi satırları.
UPDATE media_entries SET updatedAt = updatedAt / 1000 WHERE updatedAt > 100000000000;
```

---

## 4. Araştırma notları (API sözleşmeleri)

- **AniList GraphQL:** `MediaList` tipinde `createdAt` (kaydın oluşturulma) ve `updatedAt`
  (son değişiklik) alanları ayrı ayrı vardır. `createdAt` = "eklendi" zamanı.
- **Kitsu JSON:API:** `libraryEntries` kaynağının `attributes` bloğunda `createdAt` ve
  `updatedAt` bulunur; ikisi de ISO-8601'dir.
- **Shikimori REST:** `/api/users/{id}/anime_rates` ve `manga_rates` yanıtlarında
  `created_at` + `updated_at` birlikte gelir.
- **Simkl:** `/sync/all-items/{type}` yanıtındaki her öğe `added_to_watchlist_at`
  (ISO-8601, ofsetli) taşır. Resmî "extended info" dokümanı bu alanı varsayılan yanıtta
  listeler. `status`/`user_rating` güncellemeleri bu alanı değiştirmez.
- **MAL API v2:** `list_status` yalnızca `updated_at` döndürür; `added_at` **yoktur**.
  Bu yüzden MAL satırlarında eklenme zamanı ilk aktarımda `updated_at`ten türetilir.
- **Bangumi v0:** `UserSubjectCollection` şemasında `updated_at` vardır, `created_at`
  **yoktur**; doküman `updated_at`in puan/yorum değişikliklerinde güncellenmeyebileceğini
  de not eder. Aynı "en iyi çaba" yaklaşımı uygulanır.

---

## 5. Sağlayıcı eşlemesi (gerçek "eklenme" alanı)

| Kaynak | Alan | Durum |
|--------|------|-------|
| AniList | `MediaList.createdAt` (GraphQL) | ✅ Gerçek eklenme anı |
| Kitsu | `libraryEntries.attributes.createdAt` | ✅ Gerçek eklenme anı |
| Shikimori | `user_rates.created_at` | ✅ Gerçek eklenme anı |
| Simkl | `sync/all-items` → `added_to_watchlist_at` | ✅ Gerçek eklenme anı |
| MAL | `list_status.updated_at` (API `added_at` sunmuyor) | ⚠️ En iyi çaba, ilk aktarımda yazılır ve korunur |
| Bangumi | `collections.updated_at` (API `created_at` sunmuyor) | ⚠️ En iyi çaba, ilk aktarımda yazılır ve korunur |

**Koruma kuralı (repository):** bir satır yeniden içe aktarıldığında `addedAt` **asla ileri kaymaz**;
yereldeki değer ile sağlayıcının verdiği değerden **en eskisi** yazılır. Böylece:

1. Gece senkronu / her açılışta yapılan içe aktarma tarihi değiştirmez.
2. v30 göçünde `updatedAt` ile doldurulmuş satırlar, sağlayıcı gerçek `created_at` verdiğinde
   bir sonraki senkronda gerçek (daha eski) tarihe düzelir.
3. MAL/Bangumi gibi yalnızca `updated_at` veren kaynaklarda değer ilk aktarımda sabitlenir.

---

## 6. Sıralama ve bölümleme

### Sıralama (`MyListFilterHelpers`)

```kotlin
"newest", "added", "added_desc" ->
    compareByDescending { addedSortTimestamp(it) }.thenBy { it.title.lowercase() }.thenBy { it.id }
"oldest", "added_asc" ->
    compareBy { addedSortKey(it) }.thenBy { it.title.lowercase() }.thenBy { it.id }  // bilinmeyen en sonda
```

Eşit zaman damgalarında (aynı saniyede içe aktarılan toplu kayıtlar) sıra önce **başlığa** göre
belirlenir. Önceden yalnızca yerel `id` kullanılıyordu; id'ler kaynak başına ayrı sayı uzaylarında
olduğu için (Bangumi 500 milyon+, Kitsu 300 milyon+, Simkl 150 milyon+, AniList 100 milyon+) aynı
saniyedeki kayıtlar sekmeden sekmeye farklı sıralanabiliyordu. Başlık anahtarı sekmeler arasında
kararlıdır; `id` yalnızca son çare eşitlik bozucudur.

`addedSortTimestamp` sırası: `addedAt` → `updatedAt` → `0`. Değerler `normalizeEpochSeconds()` ile
korunur (eski milisaniye kayıtları/backuplar için).

### Tarih bölümleri (`MyListDateGrouping.kt`, yeni)

`Son eklenen / İlk eklenen` seçiliyken liste, durum grupları yerine şu bölümlere ayrılır:

```
Bugün → Dün → Bu Hafta → Geçen Hafta → Daha Eski → Eklendiği Tarih Bilinmiyor
```

- Hafta başlangıcı **Pazartesi** (ISO-8601).
- "İlk eklenen" seçiliyken bölüm sırası tersine döner, "Bilinmiyor" yine en sonda.
- Saat dilimi cihazın yerel dilimidir; gelecekteki damgalar (cihaz saati kayması) "Bugün" sayılır.
- `Ayrılmış liste tarzı` kapalıysa düz akış korunur (bölüm yok); sıralama yine `addedAt` iledir.
- **"Tamamlandı" filtresi:** Eskiden bu filtre listeyi koşulsuz düz akışa düşürüyordu, yani
  kullanıcı "Son eklenen"i seçse bile tarih başlıklarını göremiyordu. Tarih bölümleri artık bu
  kısayoldan ÖNCE değerlendirilir.
- **Sekme kaydırma:** Pasif (komşu) pager sayfaları kendi satırlarını da aynı sıralama kimliğiyle
  dizer ve tarih bölümlerini gösterir. Önceden komşu sayfa ham `pageEntries` sırasıyla/durum
  gruplarıyla görünüyor, sekme geçişinde bölümler "kayboluyor" gibi algılanıyordu.
- Metinler `values/` (TR) ve `values-en/` (EN) kaynaklarına eklendi.
- **Tümü sekmesi:** birleşik kart, aynı yapımın kopyaları arasından **en yeni** `addedAt`
  değerini kullanır (`MyListLibraryGrouping`). Kullanıcı bir yapımı bugün herhangi bir
  kaynağa eklediyse Tümü'de "Son eklenen"in en üstünde görünür; sekme bazlı listeler
  kendi satırlarının sağlayıcı tarihini korur.

### Sıralama menüsü tek kaynak (`myListSortEntries`)

Sıralama seçenekleri ÜÇ ayrı yerde listeleniyordu ve birbirinden kopmuştu:

| Yüzey | Önce | Şimdi |
|---|---|---|
| Satır içi çipler | 8 ölçüt (başlık literal, TR sabit) | `myListSortEntries` (11 ölçüt, TR/EN) |
| Sıralama çekmecesi | 8 ölçüt | `myListSortEntries` |
| Filtre sayfası | 15 ölçüt (`sortOptions`) | `myListSortEntries` |

Eski durumda "Favoriler", "Yayın yılı" ve "Öncelik" yalnızca filtre sayfasında vardı; "Tekrar
sayısı" ise yalnızca çekmecede. Artık tek liste var, etiketler `@StringRes` üzerinden geliyor
(TR/EN ayrışamaz) ve seçili ölçüte dokunmak yönü çeviriyor (azalan → artan → azalan). Ters yönü
olmayan ölçütlerde (`Favoriler`) `ascId = null`.

### UI

`MyListGridContent.kt` içindeki kart/ızgara gövdesi tek bir `myListEntrySection()` fonksiyonuna
taşındı; hem durum grupları hem tarih bölümleri aynı düzeni kullanıyor (iki kopya arasında tasarım
kayması olmaz). Başlık metni `item { }` içinde `stringResource` ile çözülüyor.

---

## 7. Değişen dosyalar

**Model / veri**
- `model/MediaEntry.kt` — `addedAt` alanı + `normalizeEpochSeconds()`
- `data/local/MediaEntryEntity.kt` — kolon + mapper
- `data/local/KitsugiDatabase.kt` — `version = 30`, `MIGRATION_29_30`
- `data/local/MediaEntryBackup.kt` — yedek alanı + milisaniye normalizasyonu
- `data/local/MediaEntryRepository.kt` — `insert/insertAll/smartImport/replaceAll/replaceSourcePreservingMetadata/mirrorUserFieldsToSiblings` içinde `addedAt` üretimi ve koruması

**Sağlayıcılar**
- `data/auth/AniListImportManager.kt` — GraphQL `createdAt`
- `data/auth/KitsuApiClient.kt`, `KitsuImportManager.kt` — `createdAt`
- `data/auth/ShikimoriApiClient.kt`, `ShikimoriImportManager.kt` — `created_at`
- `data/auth/SimklImportManager.kt` — `added_to_watchlist_at`
- `data/auth/MalImportManager.kt` — `updated_at` (en iyi çaba)
- `data/auth/BangumiImportManager.kt` — `updated_at` (en iyi çaba) + milisaniye hatası düzeltildi

**UI**
- `ui/screens/mylist/MyListFilterHelpers.kt` — `addedAt` tabanlı sıralama
- `ui/screens/mylist/MyListDateGrouping.kt` — yeni: bölüm hesabı
- `ui/screens/mylist/MyListGridContent.kt` — `MyListDateGroupedContent` + ortak bölüm gövdesi
- `ui/screens/mylist/MyListComponents.kt` — tarih bölümü dalı
- `ui/screens/profile/KitsugiUserMediaListScreen.kt`, `ui/app/KitsugiProfileViewModel.kt` — başka kullanıcı listelerinde `createdAt` → `addedAt`
- `core/recommendations/TvChannelSyncService.kt` — "son güncellenen önce" artık `updatedAt`
- `res/values/strings.xml`, `res/values-en/strings.xml` — bölüm etiketleri

**Test**
- `app/src/test/.../mylist/MyListDateGroupingTest.kt` — bölüm sınırları, `addedAt` tabanlı sıralama,
  eski kayıt (`updatedAt`) geri düşüşü, **her sıralama kimliğinin `applySort` tarafından
  karşılandığı**, tarih bölümlerinin yalnızca eklenme sıralamalarında açıldığı, yön çevirme
- `app/src/test/.../mylist/MyListLibraryGroupingTest.kt` — "Tümü" kartının kopyalar arasından en
  yeni `addedAt` değerini aldığı; bilinmeyen kopyanın bilinen tarihi ezmediği

---

## 8. Doğrulama adımları

1. Uygulamayı güncelle → açılışta `v29 → v30` göçü çalışır (veri kaybı yok).
2. Listem → her sekmede aşağı çekip yenile (veya Ayarlar → Hesap → Senkronize et).
3. **Son eklenen**: bugün eklenen yapım, tüm sekmelerde "Bugün" bölümünün en üstünde olmalı.
4. Uygulamayı kapat/aç veya gece senkronunu tetikle → sıra **değişmemeli**.
5. Bir kaydın puanını/ilerlemesini değiştir → yapım "Bugün" bölümünde kalmalı (tarih kaymamalı).
6. **İlk eklenen** → en eski kayıtlar önce, "Eklendiği Tarih Bilinmiyor" en sonda.
7. Ada göre / Puan / İlerleme seçildiğinde eski durum grupları (Ayrılmış liste tarzı) geri gelir.
8. "Tamamlandı" filtresi açıkken de tarih bölümleri görünür.
9. Sekmeler arasında kaydırırken komşu sekme de tarih bölümleriyle gelir (bölümler "kaybolmaz").
10. Sıralama çekmecesi, satır içi çipler ve filtre sayfası **aynı** ölçütleri listeler.

---

## 9. Notlar / sınırlamalar

- Bu ortamda JDK ve Android SDK bulunmadığı için derleme (`./gradlew`) çalıştırılamadı; değişiklikler
  statik olarak ve birim test koduyla doğrulandı.
- MAL ve Bangumi API'leri gerçek "eklenme" alanı sunmadığı için bu iki kaynakta tarih, ilk aktarımda
  `updated_at`ten türetilir ve sonrasında sabitlenir. AniList/Kitsu/Shikimori/Simkl'de değer birebir
  sağlayıcının kendi alanıdır.
- **Room şeması:** `app/schemas/.../30.json` bu ortamda ÜRETİLMEDİ (JDK/Android SDK yok, Gradle
  çalıştırılamıyor). KSP ilk derlemede dosyayı kendisi yazacaktır; `exportSchema = true` olduğu için
  şema doğrulaması derleme sırasında yapılır. Depoya elle yazılmış bir `30.json` KONMADI —
  `identityHash` yanlış olursa göç testleri yanıltıcı kırılır.
- Depoda `MigrationTestHelper` tabanlı bir göç testi yok; göç SQL'i bu nedenle cihazda
  `Adb`/`Studio` ile doğrulanmalı (bkz. bölüm 8).
