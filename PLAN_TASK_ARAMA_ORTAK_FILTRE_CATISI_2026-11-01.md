# PLAN / TASK — Arama & Kaynak Filtreleri: Ortak Filtre Çatısı (tüm kaynaklarda birebir aynı davranış)

**Tarih:** 2026-10-31 → 2026-11-01
**Dal:** `arena/e6dd46af-kitsugi-beta`
**Sürüm:** v2.4.340
**Durum:** Kod uygulandı, dört denetim turu tamamlandı. Bu sandbox'ta JDK / Gradle / Android SDK **yok**; derleme çalıştırılamadı. Doğrulama yöntemi: imza karşılaştırması, parantez/kaşık denetimi, `strings.xml` XML parse, `R.string.*` anahtar varlığı, TR/EN anahtar eşitliği.
**Ayrıntılı rapor:** `COZUM_RAPORU_ARAMA_ORTAK_FILTRE_CATISI_2026-10-31.md` (§1–§9)

---

## 1. Kullanıcı talebi

- Kaynak bazlı filtre sheet'lerinde **kaynak değiştirince filtreler kayboluyordu**.
- Filtreler **tüm kaynaklarda (AniList, MAL, TMDB, Shikimori, Kitsu, Simkl, Bangumi + "Tümü") birebir aynı** çalışmalı.
- Ardından kapsam genişledi: *"aklıma gelmeyen eksikler, fazlalıklar, bug'lar, gereksiz şeyler, eklenmesi gereken özellikler — en ufak sıkıntı kalmasın."* → açık uçlu kalite denetimi + yazılı hesap verme.

---

## 2. Görev listesi (durum)

### A. Ortak filtre çatısı (kök neden)
- [x] `SearchCommonFilters` kanonik modeli (türler, etiketler, hariç tutulanlar, kurumlar, yıl aralığı, puan aralığı, sıralama).
- [x] `SearchCommonFiltersBridge.harvest()` — her motordan kanonik sete toplama (kaynak değişiminde kaybolmayı bitirir).
- [x] `SearchCommonFiltersBridge.applyTo()` — kanonik setten her motora projeksiyon.
- [x] `harvest` → `applyTo` iki yönlü akış; `updateFilters` artık yıl/puan/sezon alanlarını sıfırlamıyor.
- [x] "Tümünü Gör" akışı (`openSourceSearchWithCarry` → `SourceSearchPage` → `openSourceSearch`) çatıya bağlandı.

### B. Sıralama
- [x] Kanonik sıralama anahtarları + motor başına harita (`resolveSort`).
- [x] Motor varsayılanları: ANILIST/ALL `POPULARITY_DESC`, MAL `popularity`, TMDB `popularity.desc`, SHIKIMORI `popularity`, KITSU `trending`, SIMKL `rank`, BANGUMI `match`.
- [x] MAL yön (`sortDirection`) türetimi: kanonik `order_by`'ı değiştirdiğinde alfabetik → `asc`, diğerleri → `desc`; kullanıcının kendi seçimi korunur.
- [x] TMDB `first_air_date.*` ↔ `primary_release_date.*` takma adı ve `effectiveSortBy`.

### C. Puan / yıl ölçekleri
- [x] Kanonik puan **0–100**; MAL/TMDB/Shikimori/Bangumi **0–10** → çift yönlü çeviri (`canonicalScoreToTen`, `canonicalScoreToTenInt`, `tenToCanonical`).
- [x] Simkl'de puan filtresi **yok** (uç kabul etmiyor) → alan kaldırıldı.
- [x] Kitsu `minRating` (0–100) kanonik sete okunuyor (kaybolmuyor).
- [x] Yıl aralığı tüm motorlarda ortak `CommonRangeSliders` (1950..bu yıl).

### D. Kurumlar (stüdyo / yapımcı / yayıncı / dergi / ağ)
- [x] MAL: `producerId` (yapımcı), dergi kimliği; TMDB: `with_companies`, `with_networks` (yalnız TV).
- [x] AniList / Shikimori / Kitsu / Simkl / Bangumi: **desteklenmiyor** — sahte eşleme yok, seçim korunur ve sheet'te "diğer kaynaklardan korunan seçim" olarak görünür.
- [x] `InstitutionFilterSheet` ile arama/seçme; detay sayfasından gelen kurum mirası doğrulanmış facet'lerden yeniden kuruluyor.

### E. Hayalet / etkisiz kontroller (temizlik)
- [x] AniList "Lisansörler" → `licensedBy_in` bağlandı.
- [x] AniList "minimum etiket güveni" → `minimumTagRank`, cilt aralığı → `volumesGreater/Lesser`.
- [x] Bangumi "yalnızca çizgi roman" → `comicsOnly` gelişmiş aramada da uygulanıyor.
- [x] MAL "Güvenli içerik (SFW)" anahtarı (hiçbir isteğe yazılmıyordu) kaldırıldı.
- [x] Simkl `minScore` kaldırıldı.
- [x] "Tümü" gövdesindeki tekrar eden bilgi metinleri tek yere indirildi.

### F. Görünürlük / kaldırılabilirlik
- [x] `GenresTagsSheet`: sabit listeye bağlı kalmak yerine kanonik setteki **tüm** seçimler görünür; liste dışı olanlar "➕ Ek seçimler" bölümünde tek tek kaldırılabilir.
- [x] Eksik TMDB türleri (Crime, War vb.) listeye eklendi ve **tekilleştirildi**.
- [x] Kitsu kategori slug'ları → okunabilir ad (`slice-of-life` → `Slice of Life`) ters çevrimi.
- [x] `resetFilters` / `resetEngineFilters` davranışı netleştirildi (fazla sıfırlama giderildi).

### G. Kod hijyeni & i18n
- [x] Kullanılmayan `setCommonGenreSelection`, `hasGenreTagSelection`, `isEmpty`, `search_institution_none` kaldırıldı.
- [x] Yeni metinler TR + EN birlikte eklendi (`search_institution*`, `search_common_*`, `search_ui_release_date_oldest`).
- [x] TR ve EN anahtar kümeleri eşit (denetlendi).

---

## 3. Değişen dosyalar (16)

**Yeni**
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchCommonFilters.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/InstitutionFilterSheet.kt`

**Değişen**
- `.../ui/screens/search/SearchViewModel.kt`
- `.../ui/screens/search/SearchUiState.kt`
- `.../ui/screens/search/SearchSourceFilters.kt`
- `.../ui/screens/search/SearchScreen.kt`
- `.../ui/screens/search/SourceEngineFilterSheet.kt`
- `.../ui/screens/search/SourceSpecificFilterChipsRow.kt`
- `.../ui/screens/search/GenresTagsSheet.kt`
- `.../data/remote/JikanApiClient.kt`
- `.../data/remote/TmdbApiClient.kt`
- `.../data/remote/KitsugiBangumiClient.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

**Doküman**
- `COZUM_RAPORU_ARAMA_ORTAK_FILTRE_CATISI_2026-10-31.md`
- `PLAN_TASK_ARAMA_ORTAK_FILTRE_CATISI_2026-11-01.md` (bu dosya)

---

## 4. Kalan iş (cihaz / CI üzerinde)

- [ ] `./gradlew :app:assembleGmsDebug` — derleme doğrulaması (sandbox'ta araç yok).
- [ ] Cihazda: kaynak değiştir → filtre korunuyor mu; her motorda puan/yıl aralığı görünür ve kaldırılabilir mi.
- [ ] Cihazda: "Tümünü Gör" → sheet → "Sıfırla" sonrası hiçbir motorda artık filtre kalmamalı.
- [ ] Cihazda: MAL'de "İsim: A-Z" (A→Z) ve "En Yüksek Puanlı" sıralamaları doğru yönde mi.
