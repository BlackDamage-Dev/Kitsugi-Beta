# PLAN / TASK — Arama "Kurumlar" Çekmecesi Boş (0 Kayıt) Kök Neden ve Düzeltme

**Tarih:** 2026-11-04
**Dal:** `arena/298900ca-kitsugi-beta`
**PR:** #37 (base: `feat/golge-bahcesi-integration`)
**Sürüm hattı:** v2.4.4xx (bu ekran `main`'de yok)
**Durum:** Kod uygulandı. Bu sandbox'ta JDK / Gradle / Android SDK **yok** → derleme çalıştırılamadı, cihaz testi bekliyor.
**Doğrulama yöntemi:** `check_localization_resource_parity.py`, `check_search_catalog.py`, köşeli parantez dengesi, sembol/imza varlığı taraması, gerçek katalog verisiyle iddia simülasyonu.

---

## 1. Kullanıcı raporu

> "Arama sayfasındaki yapımcı/stüdyo – yapım şirketi yani **kurumlar** kısmı hem **Tümü** hem **kaynaklar** üstünde **0**, neden yok, nereye gitti, bir sürü kayıt dosyası vardı — sorun ne?"

Ekran görüntüsü kanıtı: çekmece açık, *"Kurum ara (stüdyo, yapımcı, şirket)"* alanı boş, altında yalnızca *"Aramak için bir kurum adı yazın"* satırı. Sonuç sayısı / liste yok.

---

## 2. Kök neden analizi (5 bağımsız hata)

| # | Hata | Dosya | Etki |
|---|------|-------|------|
| **R1** | Sheet listesi yalnızca MAL/TMDB **ağ araması** ile doluyordu. 9.457 kayıtlık yerel katalog (`res/raw/taxonomy_catalog.json`) hiçbir yoldan okunmuyordu. Sorgu boşken liste tanım gereği boştu. | `InstitutionFilterSheet.kt` | "0 kayıt" ekranı |
| **R2** | `showOrgFilterSheet` bayrağı hiçbir yerde `true` yapılmıyordu. | `SearchScreen.kt` | `OrgFilterSheet` (katalog çekmecesi) **ölü kod** — hiç açılamıyordu |
| **R3** | `uiState.organizations` listesini tüketen tek yer çip satırıydı. Arama uçları `producerId` / `companyId` (kimlik) okur; katalogda yalnızca **ad** vardı. | `SearchViewModel.kt`, `SearchFilterBridge.kt` | Seçim yapılsa bile **hiçbir sonuç değişmiyordu** (metin bunu itiraf ediyordu: *"sonuçlara uygulama sonraki sürümde eklenecek"*) |
| **R4** | `TaxonomyCatalog.load()` içinde `runCatching` çağıranlarıyla birleşince başarısız okuma da **kalıcı** olarak "yüklenemedi" durumuna düşüyordu. | `TaxonomyCatalog.kt` | İlk I/O/bellek hatası → oturum boyunca 0 kayıt |
| **R5** | `canonicalKey.substringBefore('_')` → her anahtar `bangumi_…` olduğu için daima `"bangumi"` döner. | `SearchViewModel.kt` (yeni kod) | Kaldırmada facet kimliği hiç temizlenmiyordu |

**Ayrıca (düzeltme sırasında bulundu):**
- **R6** — 9.457 satırı `Column + verticalScroll` içinde çizmek sheet'i dondurur (Lazy değil).
- **R7** — Projede Material3 `CircularProgressIndicator` yerine `KitsugiPlasmaLoader` sarmalayıcısı kullanılıyor; yanlış import görsel kimliği bozar.
- **R8** — 28 `kitsugi_clip_*` anahtarı EN'de eksikti (parity denetimi kırmızıydı, bu düzeltmeden bağımsız borç).

---

## 3. Görev listesi

### A. Katalog erişimi
- [x] `InstitutionFilterSheet` yerel kataloğu `produceState` + `Dispatchers.IO` ile yükler.
- [x] Tür → rol eşlemesi: *Yapımcı / Stüdyo* → `producer`; *Yapım Şirketi* → `studio + producer + publisher`.
- [x] Sonuç sayısı başlığı **her durumda** görünür (boş ekranda ne sayı ne ipucu kalmasın).
- [x] Yükleme durumu (plazma loader + "Kurum kataloğu yükleniyor…") ve okuma hatası metni.
- [x] `TaxonomyCatalog.invalidate()` + başarısız yüklemenin önbelleğe alınmaması.

### B. Donma koruması
- [x] Görünür pencere: boş sorguda 60, aramada 80 satır (`CATALOG_IDLE_LIMIT` / `CATALOG_SEARCH_LIMIT`).
- [x] "N / M kurum gösteriliyor — daraltmak için yazın" ipucu.
- [x] `remember(selectedKind)` ile rol kümesi sabitlendi (her recomposition'da 9.457 kayıt yeniden taranmıyordu).

### C. Seçimin aramaya uygulanması
- [x] `toggleOrganization()` — seçim anında uygulanır, `requestSearch(resetPage = true)`.
- [x] `resolveOrganizationAndSearch()` — ad → **MAL `/producers`** → bulunamazsa **TMDB `/search/company`** ile kimlik çözümü; sonuç `producerId` / `companyId` alanına yazılır.
- [x] Kimlik çözümleme ayrı iş kolu (`organizationResolveJob`); sheet arama işi onu iptal etmiyor.
- [x] Kaldırmada `clearOrganizationFacets()` — rol anahtarın **2. parçasından** okunur.

### D. Temizlik / tutarlılık
- [x] Ölü `showOrgFilterSheet` bağlandı; katalog çekmecesinde *Filtreleri Uygula* sheet'i kapatıyor.
- [x] Bayat **"henüz etkin değil"** çipi kaldırıldı; `search_org_pending_*` ölü stringleri silindi.
- [x] `search_org_apply_notice` eklendi (TR + EN).
- [x] Yeni stringler: `search_institution_loading`, `_results`, `_narrow_hint`, `_online_results`.
- [x] `CircularProgressIndicator` → `ui.theme.gradient` sarmalayıcısı.
- [x] 28 eksik `kitsugi_clip_*` EN stringi tamamlandı → parity yeşil.

### E. Test
- [x] `OrganizationSelectionTest` (10 test): katalog 9.457 · her rolde kayıt · anahtar benzersiz · MAPPA/ufotable/Wit/A-1/Ghibli/Toei bulunur · **boş sorgu tüm kataloğu döndürür** · aksan/harf duyarsız · rol 2. parçada · ekranda CJK yok · adlar boş değil.

---

## 4. Değişen dosyalar

| Dosya | Değişim |
|-------|---------|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/InstitutionFilterSheet.kt` | +232 / −13 — katalog yükleme, sınırlı pencere, katalog satırı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt` | +138 / −3 — `toggleOrganization`, kimlik çözümü, facet temizliği |
| `app/src/test/java/com/kitsugi/animelist/ui/screens/search/OrganizationSelectionTest.kt` | **yeni** +173 |
| `app/src/main/java/com/kitsugi/animelist/utils/TaxonomyCatalog.kt` | +17 — `invalidate`, `isLoaded`, önbellek notu |
| `app/src/main/res/values/strings.xml` | +5 / −2 |
| `app/src/main/res/values-en/strings.xml` | +33 / −2 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt` | +9 / −1 — ölü bayrak bağlandı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchFilterBar.kt` | +4 / −1 — bayat çip etiketi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/OrgFilterSheet.kt` | +1 / −1 — not metni |
| `domain_fixes.json` | v2.4.410 commit'inden gelen bot değişikliği (bu düzeltmeye ait değil) |

---

## 5. Doğrulama çıktıları

```
$ python3 scripts/check_localization_resource_parity.py
Localization resources are in sync: 18427 strings and 4 arrays
  (including matching format placeholders).

$ python3 scripts/check_search_catalog.py
Search catalog OK: 114 genres, 1325 tags, 9457 Latin orgs;
  TR/EN/RU aliases and UI wiring present.
```

Katalog verisiyle iddia simülasyonu (tümü geçti): 9.457 kayıt · rol dağılımı
`producer 3802 / publisher 2381 / studio 1988 / magazine 889 / network 397` ·
anahtarlar benzersiz · anahtarın 2. parçası = rol · görünen adlarda CJK **0** · boş ad **0**.

**Yapılamayan:** `./gradlew :app:compileDebugKotlin` — sandbox'ta JDK/Android SDK yok, paket depoları kapalı.
Statik olarak doğrulananlar: köşeli parantez dengesi, kullanılan tüm sembollerin varlığı
(`TaxonomyCatalog.load/fold/matchesFolded`, `CatalogOrganization.name/role`, `KitsugiColors.*`,
`tvClickable(shape,onClick)`, `KitsugiSheetOrDialog(heightFraction)`, gradient `CircularProgressIndicator` imzası),
tüm `R.string.*` anahtarlarının TR+EN'de bulunması.

---

## 6. Cihaz testi adımları

1. **Arama** → sağ üstteki filtre (slider) düğmesi → **🏢 Kurumlar**.
2. Çekmece açılır açılmaz **"9.457 kurum bulundu"** ve ilk 60 kurum görünmeli (boş ekran YOK).
3. Arama alanına `mappa` yaz → `MAPPA` listelenmeli; dokun → çip belirmeli, arama yenilenmeli.
4. `Yapım Şirketi` sekmesine geç → liste dolu olmalı (stüdyo + yapımcı + yayıncı).
5. Çipteki **×** → seçim ve sunucu tarafı süzgeç birlikte temizlenmeli.
6. Uçak modunda aç → "Kurum kataloğu yüklenemedi" yerine **katalog yine listelenmeli** (yerel veri), yalnızca çevrim içi kimlik çözümü atlanmalı.

---

## 7. Notlar / riskler

- **Dal uyarısı:** Kurumlar çekmecesi `main`'de yok; PR #37 base'i `feat/golge-bahcesi-integration`. `main`'e doğrudan merge edilmemelidir.
- Kimlik çözümü ağ gerektirir; başarısız olursa seçim **çipte kalır** ve metin araması kullanılır (kullanıcı seçimini kaybetmez).
- Katalogdaki 45 kayıt kırpılmış/parçalı ad taşıyor (ör. `( Terebi Toukyou )`, `b_works`, `IG`). Görünür adlar Latin olduğu için parity'yi bozmuyor; ayrı bir veri temizliği işi olarak `BangumiOrgDictionary`/`orgs.tsv` tarafında ele alınmalı.
