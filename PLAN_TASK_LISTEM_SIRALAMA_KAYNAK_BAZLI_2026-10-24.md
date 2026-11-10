# PLAN & TASK — Listem "Son Eklenen" Sıralaması: Kaynak Bazlı Düzeltme
**Tarih:** 2026-10-24 · **Dal:** `arena/2b4abf5f-kitsugi-beta` · **Sürüm hedefi:** 2.4.356
**İlgili:** `COZUM_RAPORU_LISTEM_SIRALAMA_KAYNAK_BAZLI_2026-10-24.md`,
`COZUM_RAPORU_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md` (v2.4.355 altyapısı)

---

## 1. Problem tanımı (ekran görüntülerinden)

| Bulgu | Kök neden |
|---|---|
| Her kaynak sekmesi farklı sıralıyor | Kaynaklar farklı zaman alanları veriyor, bir kısmı hiç vermiyor |
| Kayıtların çoğu "Eklenme Tarihi Bilinmiyor" | `addedAt` satırlarda 0 kalmış (mirror/backup/eski sürüm yolları) |
| "Tümü" karışık, sıra kaynağa göre zıplıyor | Sıralama ve dönem başlıkları farklı değer okuyor + eşitlik `updatedAt`/yerel `id` ile kırılıyor |
| Simkl'de izlenen dizi "yeni eklenmiş" gibi üste çıkıyor | `added_to_watchlist_at` hiç okunmuyordu, `last_watched_at` vekil sayılıyordu |

## 2. Kaynak → alan sözleşmesi

| Kaynak | Alan | Nitelik | Not |
|---|---|---|---|
| AniList | `MediaList.createdAt` | gerçek | GraphQL sorgusunda mevcut |
| Kitsu | `library-entries.attributes.createdAt` | gerçek | ISO-8601 → epoch sn |
| Shikimori | `anime_rates/manga_rates[].created_at` | gerçek | ISO-8601 |
| Simkl | `added_to_watchlist_at` | gerçek | **bu sürümde bağlandı** |
| MAL | — | vekil | API'de eklenme alanı yok → ilk görülme (`updated_at`) |
| Bangumi | — | vekil | `updated_at` güvenilmez → ilk görülme |
| Elle ekleme / TMDB | ekleme anı | gerçek | `now` yalnız tekil eklemede |

## 3. Görevler

- [x] **T1 — Simkl gerçek eklenme zamanı**
  `SimklImportManager`: `added_to_watchlist_at` → `addedAt`; `last_watched_at` → `updatedAt`.
- [x] **T2 — Tek çözümleyici**
  `MyListFilterHelpers.effectiveAddedSeconds()`: `addedAt` yoksa `updatedAt`;
  milisaniye → saniye normalizasyonu. `applySort` **ve** `groupMyListEntriesByAddedPeriod` aynı fonksiyonu kullanır.
- [x] **T3 — "Son/İlk eklenen" karşılaştırıcısı**
  Bilinmeyenler iki yönde de sonda; bilinmeyenler ada göre; bilinenlerde eşitlik → başlık → kimlik.
  `updatedAt` sıralama kırıcı olmaktan çıktı, yerel `id` yalnız son çare.
- [x] **T4 — "Tümü" tutarlılığı**
  `MyListLibraryGrouping`: grup zamanı = kopyalardaki en erken **etkin** değer; yoksa temsilcinin etkin değeri.
- [x] **T5 — Eksik `addedAt` onarımı**
  `MyListViewModel.repairMissingAddedAt`: `addedAt = 0 && updatedAt > 0` satırlar tek `updateAll` ile damgalanır; yazılacak satır yoksa hiç yazım yok. DB şeması değişmez (v30).
- [x] **T6 — Testler**
  `MyListFilterHelpersTest`: vekil tarih sıralaması, bilinmeyenlerin ada göre sırası, dönem bölümlerinde `updatedAt` yedeği.
- [x] **T7 — Dokümantasyon**
  Çözüm raporu + `release_notes_v2.4.356.md` + bu plan/task dosyası.
- [ ] **T8 — Cihaz doğrulaması**
  `./gradlew :app:testFossDebugUnitTest` → temiz; APK kur → Listem → onarım sonrası
  sekmeler: AniList/Kitsu/Shikimori/Simkl tarihleri **gerçek**, MAL/Bangumi **vekil**.

## 4. Kabul kriterleri

1. "Son eklenen" seçiliyken **her sekmede** en yeni kayıt en üstte; bölüm başlıkları aynı sırayla.
2. Aynı yapım AniList sekmesinde hangi dönemde görünüyorsa "Tümü"de de **aynı** dönemde görünür.
3. "Eklenme Tarihi Bilinmiyor" yalnızca iki zaman damgası da boş satırlar için görünür.
4. Simkl'de ilerleme güncellemek kaydı "Son eklenen" listesinin tepesine taşımaz.
5. Hiçbir seçimde liste sırası sekme değişiminde/kaydırmada sıçramaz (komşu pager aynı motoru kullanır).

## 5. Riskler ve geri alma

| Risk | Etki | Önlem |
|---|---|---|
| MAL/Bangumi tarihleri yaklaşık | Kullanıcı "yanlış tarih" sanabilir | Raporda açıkça belirtildi; değer ilk görülme anı |
| Onarım yanlış anı damgalar | Bazı satırlar "bugün" görünebilir | Yalnız `addedAt=0` satırlar; sonraki eşitlemede gerçek `createdAt` kazanır (`resolveAddedAt` önceliği) |
| Depo `main` dalı 50 commit geride | Build eski kodla çıkar, düzeltme "kaybolur" | v2.4.355 dalı bu dala birleştirildi; build bu daldan alınmalı |

Geri alma: `git revert af4b65f` (şema/göç değişikliği olmadığı için veri riski yok).

## 6. Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/data/auth/SimklImportManager.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt
app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt
COZUM_RAPORU_LISTEM_SIRALAMA_KAYNAK_BAZLI_2026-10-24.md
release_notes_v2.4.356.md
PLAN_TASK_LISTEM_SIRALAMA_KAYNAK_BAZLI_2026-10-24.md   (bu dosya)
```
