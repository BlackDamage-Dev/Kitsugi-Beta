# PLAN_TASK — Listem: "Son eklenen / İlk eklenen" sıralaması ve eklenme tarihi bölümleri

**Tarih:** 2026-10-24
**Durum:** ✅ Tamamlandı (statik doğrulama — ortamda JDK/Android SDK yok, Gradle çalıştırılamadı)
**Branch:** `arena/c202c056-kitsugi-beta`
**İlgili rapor:** `COZUM_RAPORU_LISTEM_EKLENME_TARIHI_2026-10-24.md`

**Kullanıcı isteği (özet):** "Listem'de 'Son eklenen / İlk eklenen' gerçek eklenme zamanına göre
çalışsın; bugün eklediğim yapım 'Bugün' altında ve en üstte olsun. Ada göre / Puan / İlerleme /
Favoriler doğru sıralansın. AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi ve Tümü sekmelerinde
sonuç sayıları ve sıra tutarlı olsun. 'Eklenme Tarihi Bilinmiyor' yalnızca gerçekten bilinmeyenlerde
çıksın. Aynı API'leri kullanan açık kaynak uygulamalarla karşılaştırarak, geri düşmeyen kesin bir
çözüm istiyorum."

---

## 1. Hedefler

| # | Hedef | Durum |
|---|---|---|
| H1 | "Son eklenen" gerçek eklenme zamanını (`addedAt`) kullansın, yerel satır `id`'sini değil | ✅ |
| H2 | Bugün eklenen yapım **Bugün** bölümünde ve "Son eklenen"in en üstünde olsun | ✅ |
| H3 | Aynı yapım her sekmede (AniList/MAL/Simkl/Kitsu/Shikimori/Bangumi/Tümü) aynı bölümde | ✅ |
| H4 | Puan/ilerleme/durum düzenlemesi eklenme tarihini kaydırmasın | ✅ |
| H5 | Ada göre / Puan / İlerleme / Favoriler sıralamaları tutarlı ve kararlı olsun | ✅ |
| H6 | "Eklendiği Tarih Bilinmiyor" yalnızca gerçekten bilinmeyen satırlarda görünsün | ✅ |
| H7 | Sonuç sayısı ile gösterilen kart sayısı tüm sekmelerde tutarlı kalsın | ✅ |
| H8 | Eski (`v29`) veritabanı ve eski JSON yedekleri kayıpsız yükseltilsin | ✅ |
| H9 | Sıralama seçenekleri üç ayrı yüzeyde (çipler / çekmece / filtre sayfası) aynı olsun | ✅ |
| H10 | Açık kaynak uygulamaların (AniHyou vb.) kullandığı API sözleşmeleri doğrulansın | ✅ |

---

## 2. Kök neden özeti

1. **Sıralama yanlış alana bağlıydı.** `applySort` içinde `"newest" -> sortedByDescending { it.id }`,
   `"oldest" -> sortedBy { it.id }` yazıyordu; UI'ın varsayılan kimliği (`"added"`) hiçbir dala
   uymadığı için sessizce `id` desc'e düşüyordu. Yani "eklenme" diye gösterilen sıra, satırın
   veritabanına yazılma sırasıydı.
2. **Eklenme zamanı diye bir alan yoktu.** Yalnızca `updatedAt` vardı ve `update()` her
   puan/ilerleme değişikliğinde onu "şimdi"ye çekiyordu. Sonuç: bir diziyi 2024'te ekleyip bugün
   5. bölüme geçen kullanıcının kaydı "Bugün" bölümüne düşüyordu.
3. **Gece senkronu `deleteBySource + insertAll` yapıyordu.** Yerel `id`'ler her senkronda değiştiği
   için sıra kendiliğinden bozuluyor, aynı yapım bir sekmede "Bugün" diğerinde "Dün" görünüyordu.
4. **Bangumi zaman damgası milisaniye yazılıyordu.** `parseBangumiTimestamp` milisaniye döndürüp
   saniye bekleyen alana yazıyordu → kayıt 50 bin yıl sonrasına ait görünüp "Bugün"ün tepesine
   yapışıyordu.
5. **Tümü sekmesi kopyaları birleştirirken tarih seçimi tanımsızdı.** Temsilci kopya (AniList
   önceliği) eski tarihliyse, kullanıcı yapımı dün Simkl'e eklemiş olsa bile kart eski bölümde
   kalıyordu.
6. **Sıralama listeleri üç yüzeyde ayrışmıştı.** Çipler 8, çekmece 8, filtre sayfası 15 ölçüt
   gösteriyordu; "Favoriler / Yayın yılı / Öncelik" yalnızca filtre sayfasında, "Tekrar sayısı"
   yalnızca çekmecede vardı.
7. **"Tamamlandı" filtresi tarih bölümlerini eziyordu** (liste koşulsuz düz akışa düşüyordu).

---

## 3. Yapılan değişiklikler

### 3.1 Veri modeli ve veritabanı
- [x] `MediaEntry` / `MediaEntryEntity`: `addedAt: Long = 0L` (epoch **saniye**, `0` = bilinmiyor)
- [x] `model/MediaEntry.kt`: `normalizeEpochSeconds()` (milisaniye → saniye, tek yerden)
- [x] `KitsugiDatabase`: `version = 30` + `MIGRATION_29_30`
  - kolon ekleme, `updatedAt`'ten geri doldurma (milisaniye CASE düzeltmesiyle)
  - `updatedAt` içindeki eski milisaniye değerlerinin saniyeye çevrilmesi
- [x] `MediaEntryBackup`: `addedAt` dışa/içe aktarımı + içe aktarımda normalizasyon

### 3.2 Repository sözleşmesi
- [x] `insert` / `insertAll` / `replaceAll`: `addedAt` yoksa `now`, varsa normalize edilmiş değer
- [x] `smartImport`: `addedAt = min(local, provider)` → değer senkronla **ileri kaymaz**, yalnızca gerçeğe yaklaşır
- [x] `replaceSourcePreservingMetadata` (eski adı `...AdultFlags`): kanonik kimlik anahtarları üzerinden en eski tarih korunur
- [x] `update()`: veritabanındaki `addedAt` esastır; yalnızca **daha eski** bir değer kabul edilir
- [x] `mirrorUserFieldsToSiblings`: kardeş satır tamamlanır, bilinen tarih ezilmez

### 3.3 Sağlayıcı eşlemesi (gerçek "eklenme" alanı)
| Kaynak | Alan | Not |
|---|---|---|
| AniList | `createdAt` | birebir |
| Kitsu | `createdAt` | birebir |
| Shikimori | `created_at` | birebir |
| Simkl | `added_to_watchlist_at` | birebir |
| Bangumi | `updated_at` | API `created_at` sunmuyor → en iyi çaba |
| MAL | `list_status.updated_at` | API `added_at` sunmuyor → en iyi çaba |

- [x] Altı içe aktarma yöneticisi + `KitsuApiClient` / `ShikimoriApiClient` alan taşıyor
- [x] `KitsugiProfileViewModel`: başka kullanıcının listesinde AniList `MediaList.createdAt` → `addedAt`
- [x] Bangumi milisaniye hatası düzeltildi

### 3.4 Sıralama ve bölümleme
- [x] `MyListFilterHelpers.addedSortTimestamp`: `addedAt` → `updatedAt` → `0` (normalize edilmiş)
- [x] `applySort` "newest/oldest": `addedAt` + başlık + `id` (kararlı eşitlik bozucu)
- [x] `MyListDateGrouping.kt` (yeni): Bugün / Dün / Bu Hafta / Geçen Hafta / Daha Eski / Bilinmiyor
  - hafta başı Pazartesi, cihaz saat dilimi, gelecek damga → Bugün
  - "Bilinmiyor" her iki yönde de en sonda
- [x] `myListSortEntries` (yeni, tek kaynak): 11 ölçüt, `@StringRes` etiket
- [x] `nextSortId()`: seçili çipe dokunmak yönü çevirir; ters yönü olmayanlar `ascId = null`

### 3.5 UI
- [x] `MyListGridContent`: `myListEntrySection()` ortak gövdesi + `MyListDateGroupedContent`
- [x] `MyListComponents`: tarih bölümü dalı düz akıştan **önce**; "Tamamlandı" filtresi artık ezmiyor
- [x] `MyListScreen` / `MyListComponents`: pasif pager sayfaları da kendi sıralamasını ve bölümlerini hesaplıyor
- [x] `MyListLibraryGrouping`: Tümü kartı kopyalar arasından **en yeni** `addedAt`
- [x] `KitsugiMyListSortMenu` + `KitsugiMyListFilterComponents`: üç yüzey tek listeden besleniyor
- [x] `res/values/strings.xml`, `res/values-en/strings.xml`: bölüm ve sıralama etiketleri
- [x] `TvChannelSyncService`: "son güncellenen" sıralaması `updatedAt`'e göre

### 3.6 Testler
- [x] `MyListDateGroupingTest`: bölüm sınırları, `addedAt` sıralaması, eski kayıt geri düşüşü,
      **her sıralama kimliği `applySort`'ta karşılanıyor mu**, yön çevirme
- [x] `MyListLibraryGroupingTest`: Tümü kartında en yeni `addedAt`; bilinmeyen kopya bilineni ezmiyor

---

## 4. Kabul kriterleri (kullanıcı doğrulaması)

1. Güncelle → açılışta `v29 → v30` göçü çalışır, veri kaybı olmaz.
2. Her sekmede aşağı çekip yenile.
3. **Son eklenen**: bugün eklenen yapım tüm sekmelerde "Bugün"ün en üstünde.
4. Uygulamayı kapat/aç, gece senkronunu tetikle → sıra **değişmez**.
5. Puan/ilerleme değiştir → kayıt yerinde kalır (tarih kaymaz).
6. **İlk eklenen**: en eskiler önce, "Bilinmiyor" en sonda.
7. Ada göre / Puan / İlerleme seçilince eski durum grupları geri gelir.
8. "Tamamlandı" filtresi açıkken tarih bölümleri görünür.
9. Sekmeler arasında kaydırırken komşu sekme de bölümlerle gelir.
10. Çipler, çekmece ve filtre sayfası aynı sıralama ölçütlerini listeler.

---

## 5. Kapsam dışı / bilinen sınırlar

- MAL ve Bangumi gerçek eklenme alanı sunmadığı için ilk aktarımda `updated_at` türetilir; sonraki
  aktarımlarda değer korunur (asla ileri kaymaz).
- Bu ortamda derleme yapılamadı; `app/schemas/…/30.json` ilk Gradle derlemesinde KSP tarafından
  üretilecek (elle şema eklenmedi).
- `separatedListStyle` kapalıyken bölüm başlıkları gösterilmez (kullanıcı tercihi); sıralama yine
  `addedAt` iledir.

---

## 6. Değişen dosyalar

Toplam **31 dosya** (2 yeni kaynak/test dosyası + rapor + bu plan):

```
app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt
app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt
app/src/main/java/com/kitsugi/animelist/core/notifications/AiringNotificationWorker.kt
app/src/main/java/com/kitsugi/animelist/core/recommendations/TvChannelSyncService.kt
app/src/main/java/com/kitsugi/animelist/data/auth/{AniList,Bangumi,Kitsu,Mal,Shikimori,Simkl}ImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/KitsuApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/local/{KitsugiDatabase,MediaEntryBackup,MediaEntryEntity,MediaEntryRepository}.kt
app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListDateGrouping.kt        (yeni)
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/KitsugiMyListFilterComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/components/KitsugiMyListSortMenu.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListDateGroupingTest.kt       (yeni)
app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGroupingTest.kt
COZUM_RAPORU_LISTEM_EKLENME_TARIHI_2026-10-24.md                                      (yeni, rapor)
PLAN_TASK_LISTEM_EKLENME_TARIHI_2026-10-24.md                                         (bu dosya)
```
