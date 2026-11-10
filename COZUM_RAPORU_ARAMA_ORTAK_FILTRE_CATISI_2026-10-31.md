# ÇÖZÜM RAPORU — Arama Sayfası Ortak Filtre Çatısı (Common Filter Roof)

**Tarih:** 2026-10-31
**Kapsam:** Arama sayfası ("Tümü" motoru) + 7 kaynağın "Tümünü Gör" sayfaları
**Durum:** Uygulandı (kod değişiklikleri bu rapordaki dosyalarda)

---

## 1. Kullanıcı raporu (özet)

> "Tümü kısmı ile diğer kaynaklara ait Tümünü Gör sayfalarındaki filtre sayfasında ortak
> olabilecek kısımlar ortak bir küme/çatı altında çalışmalı. Tümünü Gör sayfasına girmeden
> seçtiğim türler, etiketler, kurumlar vb. o kaynağın Tümünü Gör sayfasına tıklayınca
> devam etmiş / uygulanmış olmuyor. Ayrıca bu filtre sayfası ve içindekiler, kaynaklar
> üzerinde hata/bug/eksiklik barındırmamalı."

Bu rapor; kök nedenleri, mimari çözümü, kaynak bazlı destek matrisini ve yapılan
düzeltmeleri listeler.

---

## 2. Kök nedenler (kod kanıtlarıyla)

### KN-1 — "Tümünü Gör" geçişinde filtre devri yoktu
`SearchScreen` → `onOpenSourceSearch(engine, scope, shelf)` yalnızca motor, kapsam ve
raf sonuçlarını taşıyordu. `SourceSearchPage` **yeni bir `SearchViewModel`** oluşturuyor
(`SourceSearchOwner`), dolayısıyla tür/etiket/kurum/yıl/puan/sıralama seçimleri hedef
sayfada hiç yoktu. `openSourceSearch()` de yalnızca `query/engine/scope` yazıyordu.

### KN-2 — "Tümü" filtresi yalnızca AniList rafına uygulanıyordu
`executeSearchForPage` → ALL dalında AniList sorgusu `state.genres` / `state.tags`
değerlerini geri düşüş (fallback) olarak kullanıyordu; MAL, TMDB, Shikimori, Kitsu,
Simkl ve Bangumi rafları **yalnızca kendi boş paketleriyle** sorgulanıyordu. Yani "Tümü"
ekranında seçilen tür diğer 6 kaynağa hiç ulaşmıyordu.

### KN-3 — Sıralama tüm kaynaklara yayılmıyordu
`applyEngineSort()` ALL modunda sadece `aniListSpecificFilters.sort` alanını
güncelliyordu. Diğer kaynaklar kendi varsayılan sıralarında kalıyordu ve "Tümünü Gör"
sayfası seçilen sıralamayı devralmıyordu.

### KN-4 — AniList'te "Türler & Etiketler" satırı seçimi göstermiyordu
AniList içerik bileşeni seçim sayısını **paket** üzerinden (`aniListSpecificFilters`)
gösteriyor, `GenresTagsSheet` ise **kök alanları** (`uiState.genres/tags`) düzenliyordu.
Uygula'ya basınca paket boş kaldığı için satır "0 seçili / tümü seçili" gösteriyor,
filtre uygulanmış gibi görünmüyordu. Aynı ayrışma yüzünden ikinci açılışta önceki
seçimler işaretsiz geliyordu.

### KN-5 — Kurum (stüdyo/yapımcı/şirket) filtresi hiç yoktu
`MalSpecificFilters.producerId/magazineId`, `ShikimoriSpecificFilters.studioId/publisherId`
ve TMDB `networkId` alanları sorguya bağlıydı; ancak kullanıcı bunları seçebileceği bir
arayüz yoktu. TMDB'de yapım şirketi için parametre de yoktu (`with_companies`).

### KN-6 — Sheet'te yıl/puan sessizce sıfırlanıyordu
`updateFilters()` (GenresTagsSheet'in uygulama yolu) `startYear/endYear/minScore/maxScore`
alanlarını da kopyalıyordu; sheet bu alanları bilmediği için `null` yazıp **sıfırlıyordu**.

### KN-7 — "Tümü" bilgi metni eskiydi
Arayüz 6 platform yazıyordu; motor sayısı 7 (Bangumi dahil). Ayrıca "filtreler yalnızca
hedef motorda" anlatımı yeni davranışla çelişiyordu.

---

## 3. Mimari çözüm: Ortak Filtre Çatısı

### 3.1 Yeni kanonik model — `SearchCommonFilters.kt`

```
CommonSearchFilters(genres, excludedGenres, tags, institutions,
                    startYear, endYear, minScore, maxScore, sortKey)
CommonInstitution(engine, id, name, kind)
CommonInstitutionKind { PRODUCER, COMPANY, NETWORK, MAGAZINE, STUDIO, PUBLISHER }
```

`SearchCommonFiltersBridge` iki yönlü köprüdür:

| Yön | Fonksiyon | Görev |
|---|---|---|
| durum → kanonik | `harvest(state)` | Kök alanlar + seçili motorun paketini birleştirir, kimlikleri (MAL/TMDB/Simkl) okunabilir adlara çevirir |
| kanonik → durum | `applyTo(state, target, common, scope)` | Kanonik seti hedef motorun parametrelerine çevirir; `target = ALL` ise **yedi kaynağın paketini birden** günceller |
| sıralama | `canonicalSortKey` / `sortForEngine` | AniList kanonik anahtarı ↔ MAL/TMDB/Shikimori/Kitsu/Simkl/Bangumi anahtarları |

Kanonik değerler **her zaman** `uiState.genres / excludedGenres / tags / startYear /
endYear / minScore / maxScore / institutions` alanlarında tutulur; motor paketleri bu
setten türetilir. Böylece tek doğruluk kaynağı vardır ve hiçbir ekranda seçim
"kaybolmuş" görünmez.

### 3.2 "Tümünü Gör" devri — `SearchFilterHandoff`

Arama sayfası ve kaynağa özel sayfa ayrı ViewModel kullandığı için kanonik set,
navigasyondan hemen önce `SearchFilterHandoff.publish(...)` ile bırakılır; hedef
sayfanın `openSourceSearch()` çağrısı bunu **bir kez** tüketir:

```
SearchFilterHandoff.consume()
  ?: SearchCommonFiltersBridge.harvest(mevcut durum)   // sayfa içi motor değişimi
```

Devir başarısız olsa bile ikinci kaynak (harvest) aynı ViewModel içindeki geçişlerde
filtreleri korur.

### 3.3 Kaynak bazlı uygulama matrisi

| Kanonik alan | AniList | MAL | TMDB | Shikimori | Kitsu | Simkl | Bangumi |
|---|---|---|---|---|---|---|---|
| Türler | `genres` + resmî olmayanlar `tags` | `genres` (Jikan ID) | `with_genres` | `genres` (ID) | `filter[categories]` | ilk çevrilebilen `genre` | — (yalnızca etiket) |
| Hariç türler | `excludedGenres` | `genres_exclude` | `without_genres` | `excludedGenres` | — | — | — |
| Etiketler | `tags` | tür kimliğine çevrilebilenler | tür kimliğine çevrilebilenler | tür kimliğine çevrilebilenler | `categories` | ilk çevrilebilen | `tags` |
| Yıl | `startYear/endYear` | `start_date/end_date` | `primary_release/first_air_date` | `season="YYYY"` | `seasonYear` | `year` | `yearFrom/yearTo` |
| Puan (kanonik 0–100) | `minScore/maxScore` | ÷10 → `min_score` | ÷10 → `vote_average.gte/lte` | ÷10 → `score` | `averageRating` | — (uç yok) | ÷10 → `rating` |
| Sıralama | kanonik | `order_by` | `sort_by` | `order` | `sort` | `sort` | `sort` |
| Kurumlar | ✗ (sunucu yok) | `producers` | `with_companies` | ✗ (arama ucu yok) | ✗ | ✗ | ✗ |

**Kurumlar hakkında dürüst not:** AniList, Shikimori, Kitsu, Simkl ve Bangumi'nin arama
uçları stüdyo/şirket parametresi kabul etmiyor. Bu kaynaklarda yanlış kimlik gönderip
sonucu boşaltmak yerine seçim **korunur**, sheet'te "bu kaynakta sunucu taraflı kurum
filtresi yok" bilgisi gösterilir ve MAL / TMDB sayfalarında uygulanır.

### 3.4 Sıralama çakışması: `resolveSort`

Kanonik sıralama tek bir anahtar (`sortKey`) olduğu için, motorun kendi sıralaması
"aynı anlama gelen ama farklı yazılan" bir değerse (ör. TMDB `revenue.desc`,
Simkl `votes`, Bangumi `match`) naif yayılım kullanıcının seçimini eziyordu.
Kural:

1. Hedef motorun **mevcut** sıralaması, gelen kanonik anahtarın bu motordaki
   karşılığıyla aynı anlama geliyorsa **mevcut değer korunur**.
2. Aksi hâlde kanonik anahtarın motor karşılığı yazılır.
3. Kanonik anahtarın bu motorda karşılığı yoksa (ör. `FAVOURITES_DESC` → Simkl)
   motorun mevcut değeri **değiştirilmez**.

Ek olarak iki çeviri hatası düzeltildi: Bangumi'de `SEARCH_MATCH → "match"`
(önce `"rank"` yazılıyordu ve kullanıcının "Eşleşme" seçimini eziyordu) ve TMDB'de
`REVENUE_DESC ↔ revenue.desc` çifti (önce "Hasılat" `POPULARITY_DESC`e düşüyordu).

---

## 4. Arayüz değişiklikleri

1. **`SourceEngineFilterSheet`** — "Sıralama" bölümünün altına, **her motor için** ortak
   iki giriş eklendi (ekran görüntülerindeki düzen):
   - `🎭 Türler & Etiketler (n)`
   - `🏢 Kurumlar (n)`
2. **AniList içeriğindeki kopya "Türler & Etiketler" satırı kaldırıldı** (KN-4).
3. **Yeni `InstitutionFilterSheet`** — motora duyarlı kurum seçimi:
   - MAL → yapımcı/stüdyo araması (`/producers`)
   - TMDB → yapım şirketi araması (`/search/company`)
   - Diğer kaynaklar → bilgilendirme + "diğer kaynaklardan korunan seçimler"
   - Seçim anında uygulanır; chip'ten kaldırılabilir; "Kurumları temizle" vardır.
4. **Sheet tür/etiket girişi kanonik setten beslenir** (KN-4).
5. **"Tümü" bilgi metni** 7 platforma ve yeni filtre davranışına göre güncellendi.
6. **"Tümü" gövdesi artık boş değil:** daha önce yalnızca bilgi metni gösteren
   `AllFullFiltersContent` yerine **puan aralığı** ve **yayın yılı aralığı**
   kaydırıcıları eklendi. Seçim kanonik sete yazılır, destekleyen yedi kaynağa
   çevrilerek uygulanır; slider bırakıldığında tek arama tetiklenir (sürüklerken
   istek yağmuru olmaz).
7. **Kurum sheet'i kaydırılabilir** yapıldı; küçük ekranlarda liste + seçili çipler +
   alt eylemler 85% yüksekliğe sığmadığında içerik kırpılmıyor (iç içe kaydırma
   çakışması olmaması için sonuçlar `LazyColumn` değil normal `Column`).

---

## 5. Düzeltilen davranışlar (test senaryoları)

| Senaryo | Önce | Sonra |
|---|---|---|
| Tümü'de 2 tür seç → AniList "Tümünü Gör" | Türler kaybolur | Türler AniList `genres/tags` olarak uygulanır, satırda `(2)` görünür |
| Tümü'de "Action" seç → MAL "Tümünü Gör" | Kaybolur | Jikan `genres=1` ile aranır, sonuçlar filtrelenir |
| Tümü'de "Action" → TMDB "Tümünü Gör" | Kaybolur | `with_genres=28` (TV'de 10759) |
| Tümü'de "Isekai" → Kitsu "Tümünü Gör" | Kaybolur | `filter[categories]=isekai` |
| Tümü'de sıralama "Puan" → herhangi bir kaynak | Yalnızca AniList | MAL `order_by=score`, TMDB `vote_average.desc`, Kitsu `-averageRating`, Bangumi `score`, Simkl `rating` |
| Tümü'de yıl 2020-2024 → MAL/TMDB/Kitsu/Bangumi | Kaybolur | İlgili parametrelere çevrilir |
| AniList sayfasında tür seç → Uygula → tekrar aç | "0 seçili" | Seçim işaretli gelir, `(n)` sayısı doğru |
| Sheet'te tür uygula (yıl/puan seçiliyken) | Yıl/puan sıfırlanır | Korunur (KN-6) |
| MAL sayfasında yapımcı seç | Arayüz yok | Kurumlar sheet'i → `producerId` ile arama |
| TMDB sayfasında şirket seç | Arayüz yok | `with_companies=<id>` |
| Motor değiştir (sayfa içinde) | Filtreler sıfır | Kanonik set yeni motora çevrilir |
| TMDB'de "Hasılat" sırala → tür seç (ortak sheet) | Sıralama "Popülerlik"e düşer | `revenue.desc` korunur (`resolveSort`) |
| Bangumi'de "Eşleşme" sırala → tür seç | Sıralama "Sıra"ya döner | `match` korunur |
| "Tümü" sheet'inde puan 75+, yıl 2020-2024 | Arayüzde kontrol yok | Kaydırıcılar 7 kaynağın parametrelerine çevrilir |

---

## 6. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/search/SearchCommonFilters.kt` | **YENİ** — kanonik model, iki yönlü köprü, sıralama/tür/kimlik çevirileri, `SearchFilterHandoff` |
| `ui/screens/search/InstitutionFilterSheet.kt` | **YENİ** — motora duyarlı kurum seçim sheet'i |
| `ui/screens/search/SearchUiState.kt` | `institutions` alanı; `activeFilterCount` çift sayım/saymama düzeltmeleri |
| `ui/screens/search/SearchViewModel.kt` | `openSourceSearch(carryOver)`, `setEngine` devri, `updateFilters` kanonik yönlendirme, `applySortKey`, kurum API'leri, `resetFilters` kurum temizliği, TMDB `companyId`, sayısal olmayan keyword koruması |
| `ui/screens/search/SourceEngineFilterSheet.kt` | Ortak giriş satırı, AniList kopya satırının kaldırılması |
| `ui/screens/search/SearchScreen.kt` | "Tümünü Gör" devri (7 raf), kurum sheet'i, sheet'in kanonik beslenmesi |
| `ui/screens/search/SearchSourceFilters.kt` | `TmdbSpecificFilters.companyId` |
| `ui/screens/search/SourceSpecificFilterChipsRow.kt` | `applyEngineSort` → `viewModel.applySortKey`; TMDB "En Eskiler" seçeneği; sıralama etiketleri |
| `data/remote/TmdbApiClient.kt` | `discoverAdvanced(companyId)` → `with_companies`; dizi keşfinde geçerli `sort_by` (first_air_date / revenue düşüşü) |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Kurum metinleri, 7 platform bilgisi, "Tümü" puan/yıl aralığı, "En Eskiler" ve kurum fallback metni |

---

## 7. Doğrulama notları / bilinen sınırlar

1. **Derleme bu ortamda çalıştırılamadı** (JDK/Android SDK yok). Değişiklikler statik
   olarak, mevcut API imzalarına karşı satır satır doğrulandı. Cihazda/CI'da
   `./gradlew :app:assembleGmsDebug` ile derleme doğrulanmalıdır.
2. **TMDB metin + tür birlikte:** TMDB `search` ucu tür filtresi kabul etmez,
   `discover` ucu serbest metin kabul etmez. Filtre aktifken TMDB rafı `discover`
   (filtre kataloğu) kullanır; serbest metin `with_keywords` alanına yalnızca sayısal
   ise gönderilir (önceden serbest metin gönderilip boş sonuç alınabiliyordu).
3. **Kurum filtresi** yalnızca MAL (yapımcı) ve TMDB (yapım şirketi) için sunucu
   tarafında uygulanır. Diğer kaynaklarda seçim korunur, uygulanmaz; arayüz bunu açıkça
   söyler.
4. AniList'te tür olmayan demografi/tema adları (ör. "Shounen") AniList `tags` alanına
   yazılır (AniList API'sinin kendi sözleşmesi).
5. Kitsu `filter[categories]` slug tabanlıdır; çevrilemeyen etiketler Kitsu'da
   uygulanmaz (diğer kaynaklarda uygulanmaya devam eder).
6. **"Yapışkan" motor değerleri:** Kanonik alan boşaltıldığında motorun kendi
   paketinde daha önce yazılmış bir değer varsa (ör. Simkl `year`) o değer korunur.
   Bu bilinçli bir tercihtir: kaynağın kendi çipinden yapılan seçim, ortak sheet'te
   tür değiştirildiğinde sessizce silinmesin. Tümünü sıfırlamak için sheet
   başlığındaki "Sıfırla" (`resetFilters`) kullanılmalıdır — kurumlar dahil her şeyi
   temizler.
7. **TMDB dizi kanalı (`networkId`) ve MAL dergi (`magazineId`)** için ayrı kimlik
   arama ucu olmadığından kurum sheet'inde arama açılmaz; bu türler yalnızca detay
   sayfasından gelen kimliklerle (facet) doldurulur ve korunur.

---

## 8. İkinci denetim turu (2026-11-01) — "akla gelebilecek her şey" taraması

Ortak çatı kurulduktan sonra hem arama sayfası hem filtre sheet'i, filtre → sorgu →
sıralama → sayfalama → önbellek → TV odak zinciri boyunca yeniden tarandı.
Bulunan gerçek hatalar ve düzeltmeleri:

| # | Bulgu | Etki (önce) | Düzeltme |
|---|---|---|---|
| 1 | MAL'de `order_by=title` yönü hiç yazılmıyordu (Jikan varsayılanı `desc`) | "İsim: A-Z" seçiliyor, liste **Z→A** geliyordu | `applySortKey` ve `projectMal` alfabetikte `sortDirection="asc"` yazar; kullanıcının MAL sayfasındaki asc/desc seçimi korunur |
| 2 | `/discover/tv` ucuna `primary_release_date.*` gönderiliyordu | Dizi kapsamında tarih sıralaması **400** alıyor, TMDB rafı sessizce boş kalıyordu | `TmdbApiClient.discoverAdvanced` dizi için `first_air_date.*`e çevirir |
| 3 | Dizilerde `revenue.desc` (gişe) sıralaması | Aynı şekilde geçersiz parametre → boş raf | Dizi keşfinde `popularity.desc`e düşer |
| 4 | `START_DATE_ASC` TMDB/Kitsu'da azalan sıralamaya çevriliyordu | "En Eskiler" seçilince en yeniler geliyordu | TMDB `primary_release_date.asc`, Kitsu `createdAt` (JSON:API artan) |
| 5 | TMDB sıralama listesinde artan tarih seçeneği yoktu | "En Eskiler" yalnızca "Tümü"den yayılıyordu | `📜 Çıkış Tarihi: En Eskiler` seçeneği + TR/EN metni |
| 6 | Kitsu `createdAt` (artan) kanonik karşılığı yoktu | `resolveSort` değeri `TRENDING_DESC` sanıp eziyordu | `createdAt → START_DATE_ASC` çift yönlü eşlendi |
| 7 | AniList **kaynak sayfası** sorgu varken `POPULARITY_DESC` gönderiyordu | Aynı sorgu rafta alakalılıkla, sayfada popülerlikle sıralanıyordu | Raf ile aynı kural: sorgu varken varsayılan popülerlik gönderilmez |
| 8 | `hasFiltersApplied` yalnızca motor paketine bakıyordu | Tür bu kaynağa çevrilemediyse (ör. Bangumi'de yalnız tür) boş sorgu araması **hiç çalışmıyordu** | `countForBundle`: paket boşsa kanonik kök alanlara düşer (çift sayım yok) |
| 9 | Detay sayfasından gelen yapımcı/dergi/kanal facet'leri `institutions` listesine yazılmıyordu; ayrıca önceki aramadan kalan liste temizlenmiyordu | Kurum sheet'inde chip görünmüyor, **bayat kurum seçimi** yeni aramaya sızabiliyordu | Detay akışı `institutions` alanını kaynaktan doğrulanmış facet'lerden yeniden kurar |
| 10 | TMDB detayındaki yapım şirketi facet'i yalnızca MAL `producerId`'ye yazılıyordu | TMDB `with_companies` boş kalıyordu | Yalnızca TMDB kaynaklı facet `tmdb.companyId`'ye yazılır |
| 11 | Kurum sheet'i sonuç satırları düz `clickable` | TV'de kumandayla seçilemiyordu | `tvClickable` (odak + tıklama) |
| 12 | Kurum sheet'i ipucu metni olmayan bir düğmeyi ("Sonuçları Göster") işaret ediyordu | Yanıltıcı yönlendirme | Metin TR+EN güncellendi |
| 13 | Kurum sheet'i tek parça Column'du | Küçük ekranda liste + çipler + düğmeler kırpılıyordu | İçerik kaydırılabilir, liste sınırlı yükseklikte |

**Temiz çıkan kontroller (değişiklik gerekmedi):**
- `stateCache` yalnızca sorgu/sonuç bayraklarını tutuyor → sekme değişiminde filtre kaybı yok.
- `activeFilterCount` çift sayım yapmıyor (kurum listesi varken facet kimlikleri sayılmıyor).
- 7 rafın "Tümünü Gör"ü de `openSourceSearchWithCarry` üzerinden devrediyor.
- `DetailSearchFilterRequestTest.allSourcesTreatsDetailTagAndSeasonAsActiveFiltersForBlankSearch` beklentisi (3) hâlâ doğru.
- Tüm `R.string.*` referansları kaynaklarda mevcut; TR/EN string dosyaları XML olarak geçerli.
- Dokunulan tüm Kotlin dosyalarında parantez/süslü parantez dengesi korunuyor.

**Hâlâ açık (bilinçli) sınırlar:** Shikimori'de sıralama yönü API'de parametre değildir
(`aired_on` her zaman yeniden eskiye), Simkl'de tarih sıralaması yoktur; bu iki kaynak
"En Eskiler" seçiminde en yakın davranışı gösterir. Derleme hâlâ bu ortamda yapılamıyor.

---

## 9. Üçüncü denetim turu (2026-11-01) — "eksik bağlantı" ve "hayalet kontrol" avı

Bu turda odak, **arayüzde seçilebilen ama isteğe yazılmayan** kontroller ve
**uygulanan ama görünmeyen** filtrelerdi. Bulunanlar:

| # | Bulgu | Etki (önce) | Düzeltme |
|---|---|---|---|
| 1 | **Puan ölçeği karışıklığı:** kanonik model 0–100, MAL/TMDB/Shikimori/Bangumi 0–10 bekler | "Puan 70+" seçilince Jikan'a `min_score=70`, TMDB'ye `vote_average.gte=70` gidiyor → **boş/hatalı sonuç** | `canonicalScoreToTen / canonicalScoreToTenInt / tenToCanonical` çift yönlü çeviri; dört motor da doğru ölçekte |
| 2 | AniList "Lisansörler (Licensed By)" seçimi hiçbir isteğe yazılmıyordu | Çip seçiliyor, sonuç değişmiyordu | `JikanApiClient` facade'ına `licensedBy` eklendi → `licensedBy_in` |
| 3 | AniList "Minimum etiket güveni" ve cilt filtreleri de facade'da yoktu | Kaydırıcı oynuyor, istek değişmiyordu | `minimumTagRank`, min/max volumes facade'a ve iki çağrı noktasına bağlandı |
| 4 | Simkl paketinde `minScore` alanı vardı ama Simkl ucu puan filtresi kabul etmiyor | "1 filtre" sayılıyor, hiçbir şey yapmıyordu | Alan ve sayacı kaldırıldı (dürüst model) |
| 5 | Bangumi "yalnızca çizgi roman" anahtarı yalnız `searchManga`'da uygulanıyordu | Gelişmiş aramada etkisiz | `searchMediaAdvanced(comicsOnly)` + üç çağrı noktası |
| 6 | MAL "Güvenli içerik (SFW)" anahtarı hiçbir isteğe yazılmıyordu (yetişkin davranışı global ayarla yönetiliyor) | Etkisiz kontrol | Anahtar ve `sfw` alanı kaldırıldı |
| 7 | Kitsu kategori slug'ları kanonik sete olduğu gibi (`slice-of-life`) yazılıyordu | AniList'e slug gidiyor → eşleşme yok; sheet'te çirkin çip | `kitsuSlugToName` ters çevrimi |
| 8 | Kitsu `minRating` kanonik sete okunmuyordu | Kitsu'da puan seçip başka bir ortak filtre uygulanınca puan **kayboluyordu** | `engineMinScore(KITSU)` eklendi |
| 9 | GenresTagsSheet sabit listelerine bağlıydı | Kanonik sete giren ama listede olmayan tür/etiket (TMDB "War", Bangumi yerel etiketi, Kitsu slug'ı) **görünmüyor ve kaldırılamıyordu** | "➕ Ek seçimler" bölümü + eksik TMDB türleri listeye eklendi |
| 10 | Ortak puan/yıl aralıkları yalnız "Tümü" gövdesindeydi | Kaynak sayfasında uygulanan aralık **görünmüyor/kaldırılamıyordu** ("Sıfırla" dışında) | Aralıklar sheet'in üstündeki `CommonRangeSliders` bölümüne taşındı → her motorda görünür |
| 11 | MAL sıralama yönü yalnız alfabetikte düzeltiliyordu; yön `asc` kalmışken "En Yüksek Puanlı" seçilirse en düşükler geliyordu | Yanlış yön | Kanonik `order_by`'ı değiştirdiğinde yön kanonik anahtardan türetilir (alfabetik → asc, diğerleri → desc) |
| 12 | "Tümü" gövdesinde aynı bilgi metinleri iki kez | Fazlalık | Tek yerde toplandı |
| 13 | Kullanılmayan kod: `setCommonGenreSelection`, `hasGenreTagSelection`, `isEmpty`, `search_institution_none` | Ölü kod | Kaldırıldı |

**Derleme hatırlatması:** bu ortamda JDK/Android SDK yok; tüm değişiklikler
imza karşılaştırması + parantez/metin kaynağı denetimiyle doğrulandı.
CI'da `./gradlew :app:assembleGmsDebug` ile derlenmesi gerekiyor.

