# Kitsugi Beta — Plan & Değişiklik Dosyası
**Tarih:** 24 Ekim 2026
**Branch:** `arena/d11651d6-kitsugi-beta` (taban: `3d3242f`)
**Hedef sürüm:** 2.4.341 (henüz `build.gradle.kts` sürümü artırılmadı — 2.4.340'ta duruyor)

---

## 0. Özet (tek paragraf)

Bu turda iki ayrı iş yapıldı. **(1)** Bangumi detay sayfasındaki stüdyo/yapımcı/yayıncı çiplerinde "Stüdyo detayları yüklenemedi. / KTS-COM-1020" hatası ve To LOVE-Ru gibi sayfalarda **ham Japonca** kurum adları görünmesi kökten giderildi: kök neden, infobox'ın kurumu `ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル…)` gibi **parantezli + uzun ünlü farklı** yazımla vermesi, sözlüğün ise `…エンタテイメント` yazımını bilmesiydi. Sözlük artık çok yazımlı arama yapıyor, aynı şirketin iki yazımı tek çipte birleşiyor, p1 (`next.bgm.tv`) boş dönerse V0 ucu devreye giriyor ve üretim komiteleri (kurum kaydı olmadığı için) tıklanamaz hâle geldi. **(2)** Listem sıralaması "tek seçim" modelinden çıkarılıp **iki bağımsız eksene** ayrıldı: *gruplama* (Varsayılan = eklenme tarihine göre Bugün/Dün/Bu Hafta…, Duruma göre, Düz liste) ve *sıralama* (Ada göre, Puan, İlerleme, Eklenme tarihi, Favoriler …). Sıralama artık gruplamayı silmiyor. "Son eklenen / İlk eklenen" iki ayrı çip olmaktan çıkıp tek çipin iki yönü oldu ve **gerçek eklenme zamanı** için veritabanına `createdAt` kolonu eklendi (v29 → v30).

---

## 1. İŞ A — Bangumi stüdyo / yapımcı / yayıncı

### 1.1 Bulunan kök nedenler

| # | Belirti | Kök neden |
|---|---------|-----------|
| A1 | Çiplerde ham Japonca (`ジェネオン・ユニバーサル・エンターテイメント`, `NBCユニバーサル…`) | `BangumiOrgDictionary` yalnızca **birebir** etiketi arıyordu. Infobox `…エンターテイメント(NBC…)` gönderiyor, sözlükte `…エンタテイメント` var → eşleşme yok → ham metin gösteriliyordu. |
| A2 | Aynı şirket iki kez, biri Türkçe/İngilizce biri Japonca | `distinct()` yalnızca ham dizeye bakıyordu; `SHUEISHA` ve `集英社` farklı kayıt sayılıyordu. |
| A3 | "Stüdyo detayları yüklenemedi." + **KTS-COM-1020** ("Sınıflandırılamayan hata") | p1 (`next.bgm.tv/p1/persons/{id}`) bazı **şirket** kayıtlarında boş dönüyordu; kod bunu `null` → hata ekranı yapıyordu. Ayrıca isim doğrulaması `namesMatch` yalnızca normalize metin karşılaştırdığı için **doğru** kurumu (ör. `集英社` döndü, çip `SHUEISHA` diyor) reddediyordu. |
| A4 | Üretim komitesi çiplerine tıklayınca hata ekranı | `とらぶるダークネス2nd製作委員会` gibi adlar Bangumi'de **kurum kaydı değil**; tıklanınca çözümlenemiyor ve COM-1020 çıkıyordu. |
| A5 | Kurum sayfası açılıyor ama "Yapımlar" boş | Şirket kayıtlarında p1 `/persons/{id}/works` çoğu zaman boş; V0 `/persons/{id}/subjects` bu veriyi veriyor. |

### 1.2 Yapılan değişiklikler

**`utils/BangumiOrgDictionary.kt`**
- `lookupVariants` genişletildi: parantez dışı ad, parantez içi takma ad(lar), **kapanmamış parantez** içeriği, `・/·/boşluk` ayraçları kaldırılmış yazım.
- Katakana yazım grubu: `エンタテイメント / エンタテインメント / エンターテイメント / エンターテインメント`.
- Unvan kırpma: `ジャパン`, `株式会社`, `有限会社`, `Co., Ltd.`, `Inc.`, `LLC` … (Latin ekler **yalnızca sözcük sınırında** — `Zinc → Z` hatası engellendi).
- `byEnglishCompact` ters indeksi + `localNamesForLatin` (Latin → CJK köprüsü, 256 girişlik cache).
- Yeni: `identityKey(raw)` — aynı şirketin tüm yazımları için tek sözlük anahtarı (`bangumi_producer_shueisha` gibi).
- `rowFor` artık `compact` eşleşmesini ≥2 karakterde de deniyor.

**`data/remote/StudioSourceSupport.kt`**
- `namesMatch` artık üç kademeli: normalize eşitlik → **sözlük kimliği eşitliği** → İngilizce ad eşitliği. `SHUEISHA ↔ 集英社` artık eşleşiyor; `Aniplex ↔ BONES` hâlâ eşleşmiyor.
- `orgIdentityKey()` yardımcı fonksiyonu.
- `orgNameVariants` / `isCommitteeName` / `localNamesForLatin` (önceki turdan) korunuyor.

**`data/remote/KitsugiBangumiDetailClient.kt`**
- `buildNativeDetail` içinde `distinctCompanies()`: sözlük kimliği (yoksa normalize ad) üzerinden tekilleştirme — stüdyo, yapımcı, kanal ve dergi listelerinin tamamında.
- `findStudioPersonId`: **gevşek eşleşme** (`MIN_FUZZY_ORG_KEY = 6`, içerir/ters içerir) + tüm yazımların önbelleğe yazılması.
- `fetchCompanyFallback(personId)` (V0 yedeği) ve `parseCompanySubjectWorks` — korunuyor/güçlendirildi.

**`data/remote/KitsugiStudioClient.kt`**
- `fetchBangumiStudioDetail`: p1 `null` → V0 yedeği; ayrıca **p1 açıldı ama `mediaWorks` boş ya da `about` null** ise V0'dan tamamlama (A5).
- `fetchStudioDetail` Bangumi dalı: kimlik yoksa/başarısızsa ad ile çözümleme, sonra V0 yedeği.
- `recoverStudioByName(names: List<String>, excludeSource)` — aday adlar sırayla Jikan → AniList → TMDB; dönen kurumun adı da adaylardan biriyle eşleşmek zorunda (yanlış kurum gösterme koruması). `RECOVERY_NAME_LIMIT = 4`.

**`ui/screens/detail/KitsugiDetailComponents.kt`**
- `isStudioChipClickable(chip) = chip.id > 0 || !isCommitteeName(chip.name)` → üretim komitesi çipleri tıklanamaz (hata ekranı yerine bilgi çipi), kimliği çözülmüş her kayıt tıklanabilir.

**`ui/screens/detail/StudioDetailViewModel.kt`**
- Hata kodu seçimi düzeltildi: son gerçek platform hatası varsa o, yoksa **KTS-COM-1020 yerine KTS-COM-1021 "Eşleşme bulunamadı"**. Yani "ağ hatası mı, kayıt yok mu?" ayrımı kullanıcıya doğru söyleniyor.

### 1.3 Doğrulama (bu turda yapılabilenler)

Sözlük (**52.626 satır**) Python ile birebir simüle edilerek yeni `lookupVariants` mantığı test edildi:

| Girdi (infobox) | Sonuç |
|---|---|
| `ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)` | `ジェネオン・ユニバーサル・エンタテイメント` → `bangumi_producer_nbcuniversal` / **NBCUniversal Entertainment Japan** ✅ |
| `NBCユニバーサル・エンターテイメント` | aynı anahtar ✅ |
| `SHUEISHA` | `集英社` → `bangumi_producer_shueisha` ✅ |
| `集英社` | aynı anahtar ✅ |
| `TBS` | `bangumi_network_tbs` ✅ |
| `XEBEC` | `bangumi_studio_xebec` ✅ |
| `京都アニメーション`, `京アニ` | `bangumi_studio_kyoto_animation` ✅ |
| `とらぶるダークネス2nd製作委員会` | eşleşme yok → **doğru** (komite, tıklanamaz) ✅ |

---

## 2. İŞ B — Listem sıralama / gruplama

### 2.1 Tasarım kararı: iki bağımsız eksen

Eski modelde sıralama **tek seçimdi** ve seçim yapılınca liste düz akışa düşüyordu; yani "Varsayılan" görünüm ile "Son eklenen" birbirini eziyordu. Yeni model:

```
GRUPLAMA (bağımsız)                 SIRALAMA (bölüm içinde)
├─ Varsayılan (eklenme tarihi)      ├─ Eklenme tarihi  ⇅   ← "Son eklenen" / "İlk eklenen"
│    Bugün / Dün / Bu Hafta /       ├─ Ada göre        ⇅
│    Bu Ay / Bu Yıl / Daha Eski /   ├─ Puan            ⇅
│    Tarihi Bilinmeyen              ├─ İlerleme        ⇅
├─ Duruma göre (İzleniyor …)        ├─ Favoriler
└─ Düz liste                        └─ Daha fazla: Güncelleme, Başlangıç, Bitiş, Yıl, Öncelik, Tekrar
```

Kullanıcının istediği "açıp kapatmak gibi bağımsız çalışma" tam olarak budur: gruplama açıkken de sıralama seçilebilir, sıralama yalnızca her bölümün **içini** sıralar.

### 2.2 Veritabanı: `createdAt` (v29 → v30)

`MediaEntry` / `MediaEntryEntity` içinde yalnızca `updatedAt` vardı; bu her düzenlemede değiştiği için "ne zaman eklendi" sorusunun cevabı yoktu. Eklenenler:

- `model/MediaEntry.kt`: `createdAt: Long = 0L` (saniye, `updatedAt` ile aynı birim).
- `data/local/MediaEntryEntity.kt`: kolon + `toDomain` / `toEntity` eşlemeleri.
- `data/local/KitsugiDatabase.kt`: **v30**, `MIGRATION_29_30` → `ALTER TABLE media_entries ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0` + `UPDATE … SET createdAt = updatedAt WHERE createdAt = 0 AND updatedAt > 0`.
- `app/schemas/…/30.json`: Room şeması (checked-in, `exportSchema = true`).
- `data/local/MediaEntryRepository.kt`:
  - `insert` / `insertAll` → `withCreatedAtIfMissing(now)`.
  - `update` → `createdAt` **korunur** (çağıran 0 gönderse bile satırdaki değer okunur), `updatedAt` her zaman yenilenir.
  - `replaceAll` → damga korunur.
  - `preserveResolvedTitles` → yeniden içe aktarımda `createdAt` değişmez.
  - `replaceSourcePreservingAdultFlags` → sil-yeniden-yaz döngüsünde `createdAt` **kimlik üzerinden** taşınır (yoksa her senkron tüm kütüphaneyi "Bugün"e taşırdı).
- `data/local/MediaEntryBackup.kt`: yedeğe `createdAt` yazılır/okunur (schemaVersion 2 uyumlu, alan yoksa 0).

### 2.3 Yeni dosya: `ui/screens/mylist/MyListSections.kt`

- `MyListGroupingIds` (`added` / `status` / `none`), `MyListDateBucket` (7 kova), `MyListSection`, `myListStatusOrder`.
- `MediaEntry.addedAtMillis()`: `createdAt` → `updatedAt` → 0; milisaniye/saniye karışıklığına karşı koruma.
- `myListDateBucket(entry, now)`: takvim tabanlı kova + gelecek tarih koruması.
- `groupMyListEntries(entries, groupId)`: girdi sırasını **korur** (sıralama bölüm içinde kalır), boş bölüm üretmez.

### 2.4 UI değişiklikleri

- `MyListGridContent.kt`: `MyListSectionedContent` (yeni, tek çizim yolu), `MyListGroupedContent` ve `MyListFlatContent` aynı çekirdeğe delege eder. Bölüm başlığında **ad + kayıt sayısı**.
- `KitsugiMyListFilterComponents.kt`: `MyListSortChipsRow` (kompakt, tek kavram = tek çip, seçili çipe dokun → yön döner, "Daha fazla/Daha az" ile genişler) + `MyListGroupingRow` (Varsayılan / Duruma göre / Düz liste).
- `components/KitsugiMyListFilterSheet.kt`: "Gruplama" bölümü eklendi; sıralama bölümü kompakt çip satırına geçti.
- `components/KitsugiMyListSortMenu.kt`: tek kavram = tek satır (Varsayılan, Eklenme tarihi ⇅, Ada göre ⇅ …), `ascId == descId` olan satırlar yön değiştirmez.
- `MyListComponents.kt`: `MyListContentPage` artık `sections: List<MyListSection>` alıyor; düz akış kararı gruplamaya bağlı (sıralamaya değil).
- `MyListScreen.kt`, `AppRootTabPages.kt`, `ui/app/AppViewModel.kt`: `myListGroupId` state'i + `Kitsugi_list_filters` içinde `group` anahtarı (reset/scroll davranışı diğer filtrelerle aynı).
- `MyListFilterHelpers.kt`: `applySort` → `"default"` doğal sıra, `newest`/`oldest` artık **eklenme zamanına** göre (id'ye göre değil); `getSortTitle` + `myListGroupingLabel`; tamamlanmış "aktif filtre" hesabı.
- `screens/profile/KitsugiUserMediaListScreen.kt`: aynı model (gruplama + sıralama) profil listesinde de çalışıyor → iki ekran tutarlı.
- `res/values/strings.xml` + `values-en/strings.xml`: 36 yeni anahtar (kova başlıkları, durum başlıkları, gruplama, sıralama etiketleri) — TR ve EN birlikte.

### 2.5 Testler

- **Yeni:** `app/src/test/.../mylist/MyListSectionsTest.kt` — 6 test: tarih kovaları, "sıralama başlıkları kaldırmaz", varsayılan sıra + durum gruplaması, `newest/oldest` id değil `createdAt` kullanır, tarihsiz kayıt sonda, düz liste tek başlıksız bölüm.
- **Genişletilen:** `StudioSourceSupportTest` — `dictionaryBridgeMakesCrossLanguageNamesMatch`, `sameCompanyHasSingleDictionaryIdentity` (+ önceki turun 3 testi).

---

## 3. Değişen / eklenen dosyalar (28 kalem)

**Kaynak (22)**
1. `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt`
2. `…/data/local/KitsugiDatabase.kt` — v30 + `MIGRATION_29_30`
3. `…/data/local/MediaEntryBackup.kt`
4. `…/data/local/MediaEntryEntity.kt`
5. `…/data/local/MediaEntryRepository.kt`
6. `…/data/remote/KitsugiBangumiDetailClient.kt`
7. `…/data/remote/KitsugiStudioClient.kt`
8. `…/data/remote/StudioSourceSupport.kt`
9. `…/model/MediaEntry.kt`
10. `…/ui/app/AppViewModel.kt`
11. `…/ui/screens/detail/KitsugiDetailComponents.kt`
12. `…/ui/screens/detail/StudioDetailViewModel.kt`
13. `…/ui/screens/mylist/KitsugiMyListFilterComponents.kt`
14. `…/ui/screens/mylist/MyListComponents.kt`
15. `…/ui/screens/mylist/MyListFilterHelpers.kt`
16. `…/ui/screens/mylist/MyListGridContent.kt`
17. `…/ui/screens/mylist/MyListScreen.kt`
18. **YENİ** `…/ui/screens/mylist/MyListSections.kt`
19. `…/ui/screens/mylist/components/KitsugiMyListFilterSheet.kt`
20. `…/ui/screens/mylist/components/KitsugiMyListSortMenu.kt`
21. `…/ui/screens/profile/KitsugiUserMediaListScreen.kt`
22. `…/utils/BangumiOrgDictionary.kt`
23. `…/utils/KitsugiTranslations.kt`

**Kaynak (res)**
24. `app/src/main/res/values/strings.xml`
25. `app/src/main/res/values-en/strings.xml`

**Test**
26. `app/src/test/…/data/remote/StudioSourceSupportTest.kt`
27. **YENİ** `app/src/test/…/ui/screens/mylist/MyListSectionsTest.kt`

**Şema**
28. **YENİ** `app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/30.json`

Toplam: **25 dosya değişti, 3 dosya yeni** (~1.300 satır eklendi).

---

## 4. Doğrulama durumu — DÜRÜST TABLO

| Kontrol | Durum |
|---|---|
| Sözlük eşleşme mantığı (52.626 satır üzerinde mantık simülasyonu) | ✅ Yapıldı |
| Blok/parantez dengesi (tüm değişen Kotlin dosyaları, HEAD ile karşılaştırmalı) | ✅ Fark yok |
| Yeni string anahtarlarının TR + EN karşılıkları | ✅ Tam |
| **Kotlin derlemesi (`./gradlew assembleDebug`)** | ❌ **YAPILAMADI** — bu ortamda JDK/Gradle yok |
| **Birim testleri (`./gradlew test`)** | ❌ **YAPILAMADI** — aynı nedenle |
| Room `30.json` şemasının Room tarafından üretilmiş hâliyle birebir uyumu | ⚠️ Elle üretildi (29 → 30 kopyası + yeni alan). Room derlemede şemayı **kendisi yazar**; ilk build'de `30.json` üzerine yazılacak ve `identityHash` değişecek. Bu normaldir, sadece diff'te görünecek. |

> ⚠️ **İlk build'de beklenen tek uyarı:** `30.json` içindeki `identityHash` elle üretildiği için Room build sırasında dosyayı yeniden yazacak. Bunu commit'e eklemek yeterli.

---

## 5. Kalan işler (öncelik sırasıyla)

### P0 — mutlaka
1. `./gradlew assembleDebug` + `./gradlew test` çalıştır; derleme hatalarını düzelt (özellikle: `MyListContentPage` yeni parametreleri, `KitsugiMyListFilterSheet` yeni parametreleri, `myListGroupId` çağrı zinciri).
2. `schemas/30.json` dosyasını Room'un ürettiği sürümle değiştir.
3. Cihazda duman testi:
   - To LOVE-Ru detayı → yapımcı çipleri Türkçe/İngilizce, komite çipleri **tıklanamaz**.
   - `SHUEISHA` çipi → `集英社` kurum sayfası açılıyor, "Yapımlar" dolu.
   - Listem → Gruplama "Varsayılan" iken "Puan" sıralaması: **başlıklar duruyor**, bölüm içi puan sırası.
   - Listem → "Son eklenen" / "İlk eklenen" tek çip, dokununca ok yönü dönüyor.
   - Uygulamayı kapat-aç → gruplama + sıralama hatırlanıyor.

### P1 — cila
4. Liste üst satırındaki özet ("N sonuç") artık gruplama + sıralama bilgisini de göstersin: `N sonuç · Bugün/Bu Hafta · Puan ⇅`. (`myListGroupingLabel` ve `getSortTitle` hazır, bağlanmadı.)
5. Boş sonuç kartında (`EmptyListResultCard`) gruplama bilgisi.
6. Kova başlıklarında "Bu Hafta" ile "Dün" çakışması: 2–6 gün arası kayıtlar şu an "Bu Hafta"; istenirse "Bu Hafta" yerine "Son 7 Gün" etiketi daha doğru olur (metin kararı senin).
7. `TvLibraryScreen` (TV arayüzü) hâlâ eski `applySort` sözleşmesini kullanıyor — `newest/oldest` davranışı orada da değişti, kontrol edilmeli.

### P2 — sürüm
8. `app/build.gradle.kts` → `appVersionName = "2.4.341"`.
9. `release_notes_v2.4.341.md` yaz (bu dosyanın 0–2. bölümleri taslak olarak kullanılabilir).
10. Commit + PR (`arena/d11651d6-kitsugi-beta` → `main`).

---

## 6. Riskler ve geri alma

| Risk | Etki | Önlem |
|---|---|---|
| `createdAt` göçü | v29 kullanıcıları için tek seferlik `ALTER TABLE`; eski satırlar `updatedAt` ile doldurulur (yanlış "Bugün" görünmez) | Destructive fallback yalnız DEBUG; production'da göç çalışır |
| `updatedAt` damgası ileride yeniden kullanılırsa | "Eklenme tarihi" kayar | `update` artık `createdAt`'i koruyor; senkron yolları da korumalı |
| Gevşek kurum eşleşmesi (`MIN_FUZZY_ORG_KEY = 6`) | Çok uzun adlarda yanlış kurum | Sınır 6 karakter + komite filtresi + Jikan/AniList doğrulaması |
| Sözlük varyant patlaması | Ağ isteği sayısı | `MAX_LOOKUP_VARIANTS=16`, `MAX_ORG_VARIANTS=8`, `MAX_ORG_QUERY_VARIANTS=4` |
| Geri alma | — | `MIGRATION_29_30` tek `ALTER`; geri dönüş için v29'a düşürmek yeterli (kolon fazlalığı Room'u bozmaz) |

---

## 7. Kabul kriterleri (kullanıcı isteği → durum)

| İstek | Durum |
|---|---|
| Bangumi stüdyo bilgisi eksikse giderilsin | ✅ Kod tamam (build bekliyor) |
| "Stüdyo detayları yüklenemedi + COM-1020" çıkmasın | ✅ p1 → V0 yedeği + doğru hata kodu (COM-1021) |
| Ham Japonca yapımcı çipleri görünmesin | ✅ Sözlük 52.626 satır simülasyonuyla doğrulandı |
| Varsayılan görünüm + diğer sıralamalar birlikte çalışsın | ✅ İki bağımsız eksen |
| "Son eklenen / İlk eklenen" kompakt olsun | ✅ Tek çip, tek satır, ⇅ yön |
| Listem'de daha kullanışlı olsun | ✅ Tarih bölümleri + sayı sayaçları + kompakt panel |
| Tüm düzenlemeler titizlikle | ⚠️ Build/test bu ortamda yapılamadı — madde 5/P0 |
