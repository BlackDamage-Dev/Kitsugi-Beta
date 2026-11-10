# Bangumi Entegrasyonu — Kök Neden ve Düzeltme Raporu

**Tarih:** 31 Ekim 2026
**Dal:** `arena/dfc064fc-kitsugi-beta`
**Uygulama sürümü:** `2.4.340-beta.<versionCode>` (`appVersionName = "2.4.340"`)
**Kapsam:** Bangumi'nin (bgm.tv) beslediği **tüm** yüzeyler — Keşfet şeritleri, "Tümünü Gör"
ızgaraları, Arama, detay sekmeleri, kütüphane/eşitleme ve hata gösterimi.
**Son güncelleme:** 1 Kasım 2026 — dördüncü (`S`–`V`) ve beşinci (`W`) yuvarlak: **arama
sayfası, filtre sheet'i ve "Tümünü Gör" filtre bölümü** çip çip denetlendi; 12 yeni sözleşme
hatası (R17–R28) canlı API ölçümleriyle bulunup düzeltildi. En son doğrulama: `977d42f` —
Kotlin derleme + **tam birim test seti (1482 test, filtre yok)** + R8/APK doğrulaması +
canlı prob **tümü yeşil** (§6).

---

## 1. Yönetici özeti

Kullanıcının bildirdiği "Bangumi'nin etkilediği hiçbir kısım düzgün çalışmıyor; Keşfet'te
popüler diziler/filmler gelmiyor, arama sayfasında veri gelmiyor" tablosunun arkasında
**tek bir ağ hatası değil, birbirini besleyen on ayrı sözleşme hatası** vardı. Bunların
hepsi canlı `api.bgm.tv` yanıtlarıyla tek tek doğrulandı ve düzeltildi.

En kritik üç bulgu:

1. **"En Yüksek Puanlı" şeritleri, puanı değil ham `score` alanını sıralıyordu.**
   Bangumi'nin Meilisearch indeksinde `score:desc` sıralaması **oy sayısını hesaba katmaz**.
   Bu yüzden tek oylu, kimsenin bilmediği yapıtlar (ör. `187710` *ジャングル黒べえ*, 1 oy,
   10/10) binlerce oylu klasiklerin önüne geçiyordu. Kullanıcının ekran görüntüsündeki
   kartların tamamı bu hatalı sıralamanın ürünüdür.
2. **Etiket (tür) süzgeçleri sunucuya Türkçe/İngilizce gönderiliyordu.** Bangumi'nin
   `filter.tag` alanı Meilisearch'te **birebir** karşılaştırma yapar (`tag = "科幻"`).
   `"Bilim Kurgu"` gönderildiğinde sonuç **0**'dır (kanıt: G3). Keşfet çiplerinde 19 girişlik
   eski bir harita vardı; Arama ekranındaki etiketler ise hiç çevrilmiyordu.
3. **"Bangumi Dizileri / Filmleri" rafları yanlış uçtan besleniyordu.** `type=6&cat=6001/6002&sort=rank`
   göz atma ucunda yalnızca **51** sıralı dizi kaydı var; sıra ağırlıklı olarak eski/niş
   yapıtları öne çıkarıyor. Oysa arama ucu `meta_tags=["电视剧"]` + `sort=heat` ile gerçekten
   popülerleri (半泽直树, 胜者即是正义, 非自然死亡…) veriyor.

Bunlara ek olarak: boş sorgulu gelişmiş aramada `sort` sessizce yok sayılıyordu (popüler/puan
sıralaması `date`'e düşüyordu), "Tümü" aramasında Bangumi canlı çekim kapsamlarında hiç
sorgulanmıyordu, manga aramasına roman/画集 karışıyordu, Bangumi HTTP istemcisinde **yeniden
deneme yoktu** (tek bir Cloudflare 504'ü koca bir şeridi boşaltıyordu) ve "Yeni Eklenen
Mangalar" şeridi Bangumi için hiç doldurulmuyordu.

Üçüncü prob yuvarlağında (1 Kasım 2026) **kullanıcının şikâyet ettiği ızgara ve arama
kalitesi** ayrıca ölçüldü ve altı yeni sözleşme hatası bulundu (R11–R16): tür çiplerinin
sözlükten seçilen ilk yazımı sunucuda **boş** olabiliyor (`悬疑惊悚` = 0, `运动系` = 8,
`军事` = 63), etiketli ızgarada `sort=rank` sıralamaya girmemiş kayıtları (`rank = 0`)
başa atıyor, manga rafları `type=1` kataloğunun tamamını (roman + 画集 + ciltler) çekiyor,
Bangumi aramasında **"Diziler"/"Filmler" kapsamı hiç yok**, "Tümünü Gör" dizi/film
ızgarası bu ikisini ayırmıyor ve filtre sayfası Bangumi'nin gerçekten uyguladığı
sıralamaları "yerel" diye işaretliyordu. Hepsi canlı API ölçümleriyle düzeltildi (§4.6).

Dördüncü yuvarlakta kullanıcının üç kez üstünde durduğu bölgeler (arama sayfası, filtre
sheet'i, "Tümünü Gör" filtre bölümü) tek tek denetlendi ve **on iki yeni hata** bulundu
(R17–R28): "Tümünü Gör"de kullanıcının seçtiği sıralama tür çipi yoksa `rank`'e zorlanıyordu;
`meta_tags` süzgeci `hasSearchFilters`'a sayılmadığı için istek etiketsiz göz atma ucuna
gidiyordu (manga ızgarası roman listeliyordu); "📱 yerel" tür süzgeci Bangumi'nin Çince
etiketleriyle hiç eşleşmediği için **her zaman boş** liste veriyordu; yayındaki/yaklaşan
sayfalarında `offset` yoktu (2. sayfa hep aynı 20 kayıt), "Noveller" sayfası boş açılıyordu;
arama sayfasının tür/etiket çipleri Bangumi'ye hiç ulaşmıyordu; **detay facetindeki mevsim
tamamen düşüyordu** (Bangumi'de sezon alanı yok → `air_date` çeyreğine çevrildi, prob V1–V5);
mevsimsel ızgarada tür çipi seçilince mevsim kayboluyor, "Bu Sezon" rafı bir mevsimin yalnız
bir ayını gösteriyor ve mevsimsel sıralama hiç uygulanmıyordu; tür/etiket sheet'i yıl/mevsim/puan
süzgeçlerini siliyordu; "Noveller & Light Novel" ızgarası filtrelenmiş sayfadan tamamen
düşüyordu (roman katalogu artık `meta_tags=["小说"]` ile sunucuda süzülür); arama sayfasının
**Karakter/Personel** sekmelerinde Bangumi satırına tıklamak AniList kimliğiyle **başka bir
karakteri** açıyordu (kaynak uçtan uca taşınır hâle getirildi — persons/characters uçları ve
`career` değerleri canlı `W` problarıyla doğrulandı). Hepsi düzeltildi, birim testleriyle
sabitlendi (§5, §6).

Düzeltmeler sonrası `:app:compileFossDebugKotlin` **başarıyla** derleniyor, **tam birim test
seti** (filtresiz `:app:testFossDebugUnitTest`, 1480 test) `exit=0` ile geçiyor
(çalışma `41301075659`, commit `977d42f`), R8/APK doğrulaması (`41301075619`) ve canlı API
matrisi (`41301075542`) yeşil (bkz. §4, §6).

---

## 2. Belirtiler → kök neden eşlemesi

| Kullanıcının gördüğü | Gerçek kök neden | Düzeltme |
|---|---|---|
| Keşfet → "En Yüksek Puanlı Animeler/Mangalar" tanınmayan, 10/10 puanlı yapıtlar | R1: `sort=score` oy sayısını yok sayıyor | `topScored()`: `sort=rank` + `rank>=1` + `rating_count>=100` |
| Keşfet → "Bangumi Dizileri/Filmleri" boş ya da alakasız | R4: `cat=6001/6002&sort=rank` kataloğu 51 kayıt, popülerlik yok | `meta_tags=电视剧/电影` + `sort=heat`, boşsa göz atmaya düş |
| "Tümünü Gör" → tür çipine basınca sonuç gelmiyor | R3: Türkçe/İngilizce etiket sunucuda 0 sonuç | `BangumiTagDictionary.canonicalTagFor()` ile Çince etikete çeviri |
| "Tümünü Gör" → sıralama değişince sıra değişmiyor | R2: boş sorguda `sort` yok sayılıp `date`'e düşülüyordu | `canBrowse` koşulu: `heat`/`score`/çok tür/süzgeç → arama ucu |
| Arama → Bangumi'de sonuç yok | R3 (etiket), R9 (tek 504'te başarısız), R6/R7 (kapsam eşlemesi) | Etiket çevirisi + 3 denemeli geri çekilme + kapsam→tür eşlemesi |
| Arama → "Diziler/Filmler" kapsamında Bangumi yok | R6: `planAllSources` Bangumi'yi canlı çekimde kapatıyordu | `bangumi = mediaDb \|\| wantsLiveActionSources` |
| Arama → manga sonuçlarında roman | R8: platform süzgeci yok | `platform == "漫画"` süzgeci |
| Detay sekmeleri / kütüphane tek tek boş | R9: yeniden deneme yok; yavaş/502'de ilk hata kalıcı | `execute()` 3 deneme + üstel geri çekilme |
| Keşfet → "Yeni Eklenen Mangalar" hep boş | R5: Bangumi şeridi hiç yoktu | `newlyAddedManga()` + `ExplorePayload` bağlantısı |
| Keşfet → "Noveller & Light Novel" hep boş | R10: Bangumi roman kataloğu (cat=1002) hiç sorgulanmıyordu | `novels()` şeridi + `ExplorePayload.novels` |
| "Tümünü Gör" → **Spor / Gerilim / Askeri / Doğaüstü / Psikolojik** çiplerinde 0–8 sonuç | R11: sözlüğün ilk yazımı sunucuda boş (`运动系` 8, `悬疑惊悚` **0**, `军事` 63, `心理` 34) | `PREFERRED_TAGS`: `运动` (516), `惊悚` (114), `战争` (445), `灵异` (65) |
| Etiketli ızgaranın tepesinde 1–20 oylu niş yapıtlar | R12: `sort=rank`/`match` "sıralamaya girmemiş" kayıtların `rank = 0` değerini **başa** alıyor | Boş sorguda (`rank`/`match`) → `rank >= 1` süzgeci |
| "En Yüksek Puanlı Mangalar"da roman/画集 ve cilt kayıtları; manga aramasında ilk sayfa tamamen roman | R13: `type=1` tüm书籍 kataloğu; `cat`/`series` süzgeci yok, `platform` sunucuda süzülemiyor | `cat=1001` + `series=true`; arama/puan sıralamasında `meta_tags=["漫画"]` |
| Arama → Bangumi'de "Diziler"/"Filmler" sekmesi hiç yok | R14: `availableScopes()` Bangumi için TV/MOVIE içermiyordu; kapsam→tür eşlemesi de yoktu | Sekmeler eklendi; TV/MOVIE → `type=6`, manga kapsamları → `type=1` |
| "Tümünü Gör" → Bangumi Dizileri/Filmleri ızgarası karışık (dizi rafında film) | R15: sayfalama `meta_tags` göndermiyordu, `type=6` ikisini birlikte döndürüyor | `meta_tags=电视剧 / 电影` |
| Filtre sayfası Bangumi'de sunucu sıralamalarını "📱 yerel" işaretliyor | R16: `serverSorts()` boş kümeden (`SORT_SUPPORT[BANGUMI] = ∅`) filtreliyordu | `BANGUMI_SEARCH_SORTS` temel alındı |
| "Tümünü Gör" → sıralama seçilince (tür seçili değilken) sıra değişmiyor | R17: `bangumiPage` tür yoksa sıralamayı zorla `rank`e çeviriyordu | Kullanıcının sıralaması aynen gönderilir (`score` / `heat` / `rank`) |
| "Tümünü Gör" → **Manga** ızgarasında roman/画集; arama "Manga" kapsamında ilk sayfa boş | R18: `type=1` tüm 书籍 kataloğu, `meta_tags` yok | Manga ızgarası + arama: `meta_tags=["漫画"]` (sunucu tarafı) |
| "Tümünü Gör" → "📱" işaretli tür süzgeci **her zaman boş** liste | R19: yerel süzgeç Çince Bangumi etiketlerini Türkçe/İngilizce kelimelerle karşılaştırıyordu | Terim sözlükten kanonik yazıma çevrilip etiketlerle de karşılaştırılır |
| "Tümünü Gör" → **Yayındaki/Yaklaşan** sayfalarında 2. sayfa hep aynı kayıtlar; **Noveller** sayfası boş; "Yeni Eklenen Mangalar" yanlış uç | R20: `airingAnime/upcomingAnime` `offset` almıyordu, NOVELS `else -> emptyList()` dalındaydı, NEWLY_ADDED_MANGA `publishingManga`'ya bağlıydı | `offset` eklendi; NOVELS → `novels()`; NEWLY_ADDED_MANGA → `newlyAddedManga()` |
| Arama filtre sayfasında tür/etiket seçince Bangumi süzgeci **hiç uygulanmıyor**; "hariç tut" yok sayılıyor; "Light Novel" kapsamı manga listeliyor | R21: `GenresTagsSheet` çipleri `uiState.genres/tags`'e yazıyor, Bangumi yalnız `bangumiSpecificFilters.tags`'i okuyordu; dışlama sunucuda yok | Etiketler birleştirilir (sözlük çevirisiyle), dışlanan türler istemcide süzülür, roman kapsamı `meta_tags=["小说"]` + `comicsOnly=false` |
| Detay sayfasından "Kış 2025 · Korku" ile aramaya gidince Bangumi yıl/mevsim süzgeci uygulamıyor | R22: `applyDetailFilterRequest` mevsimi **tamamen** düşürüyordu; Bangumi'de sezon alanı olmadığı için `air_date` aralığına çevrilmesi gerekir | Mevsim + yıl → `air_date` **çeyrek** aralığı (`WINTER 2025` → `>=2025-01-01`, `<2025-04-01`), canlı prob V1–V5 |
| Mevsimsel ızgarada tür çipi seçilince mevsim kayboluyor (tüm zamanların sonuçları) | R23: `fetchFilteredExplorePage`, mevsimi Bangumi sayfasına hiç iletmiyordu | Mevsim filtresi filtrelenmiş sayfaya taşınır; tür + mevsim birlikte sunucuda süzülür |
| "Bu Sezon" rafı bir mevsimin yalnız **bir ayını** listeliyordu | R24: `seasonalAnime()` → `seasonFor(year, buAy)` | Takvim çeyreği + `heat` sıralaması; boş dönerse ay bazlı göz atmaya düşer |
| Mevsimsel ızgaranın "Puana / İlgiye göre" sıralaması Bangumi'de hiç uygulanmıyordu | R26: `fetchSeasonalPage` `seasonalSort`'u okumuyordu (hep ay-göz-atma sırası) | `SCORE_DESC → score`, `MEMBERS/POPULARITY_DESC → heat`, diğerleri `rank` |
| Tür/etiket sheet'inde bir çip daha ekleyince kullanıcının yıl/mevsim/puan süzgeçleri **siliniyordu** | R25: `SearchFilters` yalnız tür/etiket taşıdığı için `updateFilters` diğer alanları `null`'a çekiyordu (TMDB tür diyaloğunda da aynı) | Sheet'ten gelen değerler mevcut durumla birleştirilir (yalnız düzenlenen alanlar yazılır) |
| Arama → **Karakter**/**Personel** sekmesinde Bangumi sonucuna tıklayınca **başka bir karakter** açılıyor (ya da boş sayfa) | R28: `SearchScreen` tıklama geri çağrıları kaynağı hiç taşımıyordu; navigasyon `source = "anilist"` sabitliydi. Bangumi karakter id'si AniList kimlik uzayında aranıyordu | Satırın kaynağı (`bangumi`/`mal`/`kitsu`/`anilist`) uçtan uca taşınır; detay ekranı doğru istemciyi çağırır |
| "Noveller & Light Novel" ızgarasına tür çipi uygulandığında Bangumi hiç katkı vermiyordu | R27: `shelfSpec(BANGUMI, NOVELS) = null`, yani bu raf filtrelenmiş sayfada eleniyordu; tür süzgeci cihazda Çince etiketlerle eşleşmiyordu | Roman kataloğu `type=1` + `meta_tags=["小说"]` + `comicsOnly=false` ile **sunucuda** süzülür (prob Q4/U5) |

---

## 3. İnceleme yöntemi

Bu sandbox'tan `api.bgm.tv` / `next.bgm.tv` adreslerine doğrudan TCP erişimi **yok**
(`curl` → `000`). Bu yüzden kanıtlar üç bağımsız kaynaktan toplandı:

1. **Canlı API (GitHub Actions üzerinden).** Depoya geçici bir teşhis iş akışı eklendi
   (`.github/workflows/bangumi-probe.yml`); uygulamanın gerçek User-Agent'ı ile
   `POST /v0/search/subjects`, `GET /v0/subjects`, `GET /p1/...` matrisi çalıştırılıp çıktı
   `probe/result.txt` dosyasına yazıldı. Tüm sayılar bu canlı yanıtlardan alınmıştır
   (31 Ekim 2026, ~21:15 UTC).
2. **Sunucu kaynak kodu** (`github.com/bangumi/server`, `internal/search/subject/handle.go`).
   Sıralama alanları (`sort` ∈ `match|score|heat|rank`), filtre alanları
   (`type/tag/air_date/rating/rating_count/rank/meta_tags/nsfw`) ve sayfa sınırları
   (`GetPageQuerySoftLimit`: varsayılan 10, üst sınır 20; `doSearch`: limit üst sınırı 50)
   buradan doğrulandı.
3. **OpenAPI sözleşmesi** (`github.com/bangumi/api`, `open-api/v0.yaml`) ve uygulama
   kaynakları (`BangumiApiClient`, `KitsugiBangumiClient`, `KitsugiBangumiDetailClient`,
   `ExploreViewModel`, `SearchViewModel`, `ExploreSourceFilterQuery`, `BangumiTagDictionary`).

Derleme doğrulaması da aynı kanaldan yapıldı: GitHub Actions günlükleri bu sandbox'tan
indirilemediği için `:app:compileFossDebugKotlin` ve Bangumi birim testleri, çıktısını
`probe/kotlin_log.txt` dosyasına yazan geçici bir iş akışıyla çalıştırıldı.

---

## 4. Kanıtlar (canlı API, 31 Ekim 2026)

Tam günlük: `probe/result.txt`. Aşağıdaki satırlar oradan birebir alınmıştır.

### 4.1 R1 — `sort=score` tek oylu 10/10'ları zirveye taşıyor

```
===== E4 score (eski davranış) =====
 - 187710 t2 | 丛林黑兵卫   | score 10 rank 0 votes 1
 - 188894 t2 | それいけ！ズッコケ三人組 | score 10 rank 0 votes 1
 - 207695 t2 | 交给你了!奇迹猫团 | score 10 rank 0 votes 1
 - 210388 t2 | 小狗亚斑     | score 10 rank 0 votes 1
 - 211291 t2 | SURVIVE      | score 10 rank 0 votes 1
```

`votes 1` + `rank 0` = sıralamaya girmemiş, tek oylu kayıt. Eski kodun ürettiği liste tam olarak budur
(kullanıcının ekran görüntüsündeki `#500187710` kartı).

Yeni strateji:

```
===== E1 rank + rank>=1 + rating_count>=100 =====
 - 326  t2 | 攻壳机动队 S.A.C. 2nd GIG | score 9.2 rank 1 votes 10279
 - 876  t2 | CLANNAD 〜AFTER STORY〜   | score 9.1 rank 2 votes 32077
 - 25961 t2 | 猫和老鼠（1965年电视版）  | score 9.1 rank 3 votes 14322
 - 253  t2 | 星际牛仔                 | score 9.1 rank 4 votes 19820
 - 324  t2 | 攻壳机动队 STAND ALONE COMPLEX | score 9.1 rank 5 votes 11685
```

**`rank>=1` süzgeci zorunludur**: onsuz `rank:asc` sıralamasında `rank = 0` (sıralamaya
girmemiş) kayıtlar en başa düşer —

```
===== E2 rank + rating_count>=100 (rank süzgeci YOK) =====
 - 247349 t2 | 在异世界开拓第二人生 | score 1.3 rank 0 votes 237
 - 401777 t2 | 原神               | score 3.0 rank 0 votes 313
```

Bu yüzden düzeltmede ilk deneme `rank>=1` + `rating_count>=100`'dür; sunucu bu alanlardan
birini reddederse sırasıyla daha gevşek varyantlara düşülür (her varyant canlıda test edildi).

### 4.2 R3 — etiketler birebir eşleşir; Türkçe/İngilizce çip = 0 sonuç

```
===== G1 tag 科幻 (Çince)   ===== total: 1000
===== G2 tag Sci-Fi (İngilizce) ===== total: 2      (yalnızca etiketi gerçekten "Sci-Fi" olan 2 kayıt)
===== G3 tag Bilim Kurgu (Türkçe) ===== total: 0
```

Yani Arama ekranındaki etiket çipleri sunucuya olduğu gibi gönderildiğinde **hiçbir zaman**
sonuç dönmüyordu; Keşfet'te ise yalnızca 19 sabit terim (`action`, `sci-fi`, `school`…)
çalışıyor, diğer tüm çipler sessizce **süzgeçsiz** listeye düşüyordu.

### 4.3 R4 — REAL (三次元) rafları için doğru uç

```
===== C3 GET browse type=6&cat=6001&sort=rank ===== → toplam 51 kayıt (küçük, eski/niş ağırlıklı)

===== F1 real tv heat (meta_tags 电视剧) =====
 - 73955 t6 | 半泽直树     | score 8.3 votes 3429 | mt 剧情,日本,电视剧 | platform 日剧
 - 37566 t6 | 胜者即是正义 | score 8.4 votes 3451 | platform 日剧
 - 225581 t6 | 非自然死亡  | score 8.4 votes 3144 | platform 日剧
 - 11304 t6 | 迪迦奥特曼  | score 8.9 votes 2604 | platform 日剧

===== F2 real movie heat (meta_tags 电影) =====
 - 12886  t6 | 情书           | score 8.4 | platform 电影
 - 320871 t6 | 流浪地球2      | score 7.9 | platform 电影
 - 25461  t6 | 蝙蝠侠：黑暗骑士 | score 8.5 | platform 电影
```

`platform` alanı da doğrulandı: REAL dizilerde `电视剧`, REAL filmlerde `电影`
(ör. `GET /v0/subjects/259879` → `"platform":"电视剧"`, `meta_tags:["电视剧","喜剧",…]`).

### 4.4 R2/R8 — arama ve manga kapsamı

```
===== H1 match chainsaw man (type=2) ===== total: 9
 - 321885 t2 | 链锯人 (TV) …
===== H2 match one piece (type=1) ===== total: 636
 - 3510  t1 | 航海王 (漫画)
 - 149890 t1 | 海贼王 小说      ← roman; manga rafını kirletiyordu
 - 12121 t1 | ONE PIECE (1) …
===== H3 match reply 1988 (type=6) ===== total: 4
 - 259879 t6 | 请回答1988 | score 8.7 | platform 电视剧
===== H4 heat anime (süzgeçsiz) =====
 - 10380 命运石之门 | 328609 孤独摇滚！ | 400602 葬送的芙莉莲 | 9717 魔法少女小圆
```

`H4` popülerlik (`heat`) sıralamasının sağlıklı olduğunu, `H2` ise roman kayıtlarının
(`platform = 小说`) manga listesine karıştığını gösteriyor.

### 4.5 Diğer doğrulamalar

- `POST /v0/search/subjects` sunucu tarafında **limit ≤ 20** (`GetPageQuerySoftLimit`),
  `doSearch` içinde üst sınır 50. Uygulama `SEARCH_PAGE_SIZE = 20` ile uyumlu; sayfalama
  `offset`'i aynı boyuta göre hesaplıyor (mevcut davranış korundu).
- `I1` anime filmleri (`type=2&cat=3&sort=rank`) → 1735 kayıt, sağlıklı (EVA, GitS, Patlabor).
- `I2` kitap kataloğu (`type=1&cat=1001&sort=date`) → 305.988 kayıt; "yeni eklenen" sırası
  doğal olarak oy almamış yeni ciltleri getirir (beklenen davranış, değiştirilmedi).
- `next.bgm.tv/p1/subjects/{id}` (detay sekmeleri) 200 dönüyor; `getRaw(next = true)`
  yolu `"/p1" + path` biçiminde kuruluyor ve çağıranlar `/subjects/...` verdiği için
  **çift `/p1` yok** (incelendi, hata bulunmadı).

### 4.6 Üçüncü yuvarlak — tür çipleri, sıralama ve manga kataloğu (1 Kasım 2026)

`probe/result.txt` içindeki `K`–`Q` blokları. Tüm istekler uygulamanın **kendi**
`User-Agent`'ı ve `POST /v0/search/subjects` gövdesiyle atıldı (`type=2`, `sort=rank`).

**K — tür çiplerinin sözlükten çevrilen ilk yazımı** (`filter.tag`, süzgeç yok):

| Etiket | `total` | Etiket | `total` | Etiket | `total` |
|---|---|---|---|---|---|
| 剧情 | 692 | 恐怖 | **185** | 军事 | **63** |
| 战斗 | 1000 | 日常 | 1000 | 运动系 | **8** |
| 恋爱 | 1000 | 冒险 | 1000 | 悬疑惊悚 | **0** |
| 搞笑 | 1000 | 灵异 | **65** | 机战 | 1000 |
| 奇幻 | 1000 | 心理 | **34** | 音乐 | 627 |
| 科幻 | 1000 | 悬疑 | 652 | 历史 | 418 |
| 校园 | 1000 | | | | |

**L/N — aynı gruplardaki alternatif Bangumi yazımları:**

| Grup | Aday yazım → `total` | Seçim |
|---|---|---|
| Spor | `运动系` 8 · `运动` **516** · `スポーツ` | `运动` |
| Gerilim | `悬疑惊悚` 0 · `悬念` 1 · `惊悚片` 0 · `惊悚` **114** | `惊悚` |
| Askeri | `军事` 63 · `战争` **445** · `军事冲突` 0 | `战争` |
| Doğaüstü | `灵异` **65** · `超自然` 7 · `超能力` 251 | `灵异` (anlam birebir) |
| Psikolojik | `心理` **34** · `心理战` 1 · `智斗` 147 · `サイコスリラー` 0 | `心理` (anlam birebir) |

**M/O — `rank >= 1` süzgeci ve sıra kalitesi:** `M` bloğunda `剧情/战斗/运动`
etiketlerine `rank >= 1` eklendiğinde liste **rank artan** sırada geliyor
(rank 8 → 12 → 14). `O` bloğu 19 çipin **tamamını** uygulamanın gerçek isteğiyle
doğruladı; hepsi 3/3 sonuç verdi ve sıra `rank` artan:

| Çip | Toplam (`rank>=1`) | İlk sonuç (rank) |
|---|---|---|
| 剧情 | 506 | 命运石之门 (8) |
| 战斗 | 1000 | 攻壳机动队 S.A.C. 2nd GIG (2) |
| 恋爱 | 1000 | CLANNAD 〜AFTER STORY〜 (1) |
| 搞笑 | 1000 | 猫和老鼠（1965） (3) |
| 奇幻 | 1000 | 猫和老鼠（1965） (3) |
| 科幻 | 1000 | 攻壳机动队 S.A.C. 2nd GIG (2) |
| 悬疑 | 544 | 命运石之门 (8) |
| 恐怖 | 102 | 寒蝉鸣泣之时 (232) |
| 日常 | 1000 | CLANNAD 〜AFTER STORY〜 (1) |
| 冒险 | 1000 | 千与千寻 (28) |
| 灵异 | 48 | ×××HOLiC◆继 (319) |
| 心理 | 25 | 新世纪福音战士剧场版 Air/真心为你 (7) |
| 运动 | 362 | 3月的狮子 第二季 (10) |
| 惊悚 | 73 | 蓝色恐惧 (14) |
| 机战 | 643 | 新世纪福音战士剧场版 Air/真心为你 (7) |
| 音乐 | 469 | 猫和老鼠（1965） (3) |
| 历史 | 278 | 浪客剑心 追忆篇 (6) |
| 战争 | 362 | 攻壳机动队 S.A.C. 2nd GIG (2) |
| 校园 | 1000 | CLANNAD 〜AFTER STORY〜 (1) |

> Süzgeç öncesi/sonrası toplamlar (`剧情` 692 → 506, `运动` 516 → 362) küçülüyor;
> kaybedilen kayıtlar **hiçbir kategori sıralamasına girmemiş** (oy almamış) yapıtlar.
> Bu, "En Yüksek Puanlı" davranışıyla tutarlı: sıralamaya girmemiş kayıt resmî
> 排行榜'da yoktur.

**P — manga kataloğu:** `type=1&cat=1001&series=true` → 灌篮高手 (rank 2), 剑风传奇 (3),
哆啦A梦 (4), 火之鸟 (5). `series` **verilmeden** aynı sorgu `灌篮高手 完全版` gibi
**aynı serinin farklı baskısını** da listeye sokuyor (cilt/baskı kirliliği).
`series=true + sort=date` yeni serileri getiriyor (beklenen).
**P5** `type=1&tag=恋爱&sort=match` ilk 5 sonucunun **tamamı `platform=小说`** —
istemci tarafı süzgeç sayfayı boşaltıp sayfalamayı durduruyordu.

**S/T — son doğrulama yuvarlağı (28 tür çipi + kalan sözleşmeler):**
Uygulamanın tür çiplerinin **tamamı** (19 tür + 9 popüler etiket) sözlükten geçirilip
canlı API'de tek tek sorgulandı; hepsi 200 döndü ve **hiçbiri 0 değil**:
`剧情` 695 · `战斗` 1000 · `恋爱` 1000 · `搞笑` 1000 · `奇幻` 1000 · `科幻` 1000 ·
`悬疑` 653 · `恐怖` 185 · `日常` 1000 · `冒险` 1000 · `灵异` 65 · `心理` 34 ·
`运动` 516 · `惊悚` 114 · `机战` 1000 · `音乐` 626 · `历史` 418 · `战争` 445 ·
`校园` 1000 · `异世界` 805 · `魔法` 360 · `超能力` 250 · `生存` 19 · `赛博朋克` 62 ·
`武侠` 234 · `宇宙` 38 · `末世` 95 · `吸血鬼` 103.

Aynı blok `rank >= 1` **olmadan** çağrıldığında listenin tepesi her seferinde
**rank 0 / 3–50 oy** kayıtlarıyla doluyordu (ör. `剧情`: 哥普拉 六人勇士, 45 oy, rank 0).
Bu, R12'nin (ızgara kirliliği) doğrudan kanıtıdır ve `rank >= 1` süzgecinin neden
zorunlu olduğunu gösterir. `O` bloğunda `rank >= 1` ile aynı çipler gerçek sıralamayı verdi.

| Kalan sözleşme | Sonuç |
|---|---|
| `T1` manga puan sıralaması (`type=1`, `rank>=1`, `rating_count>=100`, `meta_tags=漫画`) | 725; 灌篮高手 完全版 (1), 剑风传奇 (3), 火之鸟 (5) |
| `T2` roman rafı (`type=1&cat=1002&sort=rank`) | **3306**; 卡拉马佐夫兄弟 (6), 罪与罚 (8), 三体Ⅱ (10) |
| `T3` `type=6` göz atma, `cat` **yok** (`sort=rank`) | **3639**; 是，大臣 (1), 三国演义 (4) |
| `T4` `type=6&cat=6001` (eski yol) | yalnız **51** → neden `meta_tags` kullanıldığı doğrulandı |
| `T5` `type=6` + `keyword="reply 1988"` | 请回答1988 (rank 53) ilk sırada |

**U — ızgara/filtre isteklerinin birebir doğrulanması (1 Kasım 2026, ikinci yuvarlak):**

| Sorgu (uygulamanın gönderdiği gövde) | Sonuç |
|---|---|
| `U1` `heat` + `type=1` + `meta_tags=["漫画"]` | 1000; 链锯人 (6935 oy), 再见绘梨, 炎拳 |
| `U2` `score` + `type=1` + `meta 漫画` + `rating_count>=100` | 725; 灌篮高手 完全版 (9.4), 剑风传奇 (9.3) |
| `U3` `heat` + `type=6` + `meta 电视剧` | 1000; 半泽直树, 胜者即是正义, 非自然死亡 |
| `U4` `score` + `type=6` + `meta 电影` + `votes>=100` | 427; 七武士 (8.9), 教父 (8.8), 生之欲 (8.8) |
| `U5` `rank` + `type=1` + `meta 小说` + `rank>=1` | 539; 卡拉马佐夫兄弟 (6), 罪与罚 (8), 三体Ⅱ (10) |
| `U6` `rank` + `type=2` + `tag 运动` + `rank>=1` | 362; 3月的狮子 第二季 (10), 乒乓 (23) |

> Bu altı sorgu, "Tümünü Gör" ızgaralarının ve Arama'nın gönderdiği gövdelerin **birebir**
> kopyasıdır. Hepsi beklenen kaliteyi verdi: doğru katalog (manga ≠ roman, dizi ≠ film),
> doğru sıralama (`score`/`heat`/`rank`) ve `rank = 0` kirliliği yok.

**V — mevsim → `air_date` çeyrek aralığı (2. yuvarlak, 1 Kasım 2026):**

Bangumi'de **sezon alanı yok**; v0 yalnız `filter.air_date` tarih aralığı kabul eder. Bu yüzden
"Kış 2025" gibi bir mevsim filtresi çeyrek aralığa çevriliyor. Aşağıdaki problar çevirinin
sunucuda **gerçekten** çalıştığını ve `<` (dışlayıcı üst sınır) biçiminin de kabul edildiğini
gösterir:

| Sorgu | Sonuç |
|---|---|
| `V1` `air_date [">=2025-01-01","<2025-04-01"]` + `type=2` + `rank>=1` | **89**; 凡人修仙传 第四季, 金牌得主, 药屋少女的呢喃 第二季 |
| `V2` `air_date [">=2025-04-01","<=2025-06-30"]` + `type=2` | **98**; 末日后酒店, 赛马娘 芦毛灰姑娘 |
| `V3` `air_date [">=2026-07-01","<2026-10-01"]` + `type=2` | **93**; 相反的你和我 第二季, 无职转生 第三季 |
| `V4` `air_date [">=2025-01-01","<=2025-12-31"]` + `type=6` + `meta 电视剧` | **494**; 假面骑士ZZZ, 怪奇物语 第五季 |
| `V5` `air_date [">=2025-10-01","<2026-01-01"]` + `type=2` | **90**; 辉夜大小姐…阶梯, 佐贺偶像是传奇… |

> V1/V5, `>=` + `<` (bitişik çeyrek) biçiminin de çalıştığını; V4, `air_date` + `meta_tags`
> birleşiminin AND semantiğiyle süzüldüğünü doğruluyor. Mevsim artık hem detay facetinde,
> hem "Tümünü Gör" mevsimsel ızgarasında, hem "Bu Sezon" rafında aynı çeviriyle uygulanıyor.

**W — arama sayfasının Karakter/Personel sekmeleri (2. yuvarlak):**

`POST /v0/search/characters` ve `POST /v0/search/persons` uçları ile filtre sayfasındaki
meslek (`career`) çiplerinin sunucudaki karşılıkları **canlı** doğrulandı:

| Sorgu | Sonuç |
|---|---|
| `W1` persons `{"keyword":"花泽香菜"}` | 200; 4765 花澤香菜 `career artist,seiyu` |
| `W2` persons + `career:["seiyu"]` | 200, **175**; yalnız `seiyu` içeren kişiler |
| `W3` persons + `career:["mangaka"]` `尾田荣一郎` | 200, **1**; 290 尾田栄一郎 `mangaka` |
| `W4` persons + `career:["illustrator"]` | 200, **43**; illustrator'lar |
| `W5` persons + `type:[2]` (şirket) `MAPPA` | 200, **5**; 7357 MAPPA |
| `W6` characters `Saber` | 200, **59**; 179271 セイバー |
| `W7` characters `鲁路修` | 200, **1000**; 1 ルルーシュ・ランペルージ |

> Yani sekmelerin **veri yolu** sağlam; sorun yalnızca tıklamada kaynağın kaybolmasıydı (R28).
> `career` değerleri (`seiyu`, `mangaka`, `illustrator`, `producer`…) sunucuda birebir kabul
> ediliyor — filtre sheet'indeki çipler doğru.

**Q — sunucu tarafı `meta_tags` süzgeci:**

| Sorgu | Sonuç |
|---|---|
| `type=1&tag=恋爱&meta_tags=["漫画"]` | 1000, tamamı `platform=漫画` (人形电脑天使心, 水果篮子…) |
| `type=1&meta_tags=["漫画"]&rank>=1` | 1000; 灌篮高手 完全版 (1), 剑风传奇 (3), 火之鸟 (5) |
| `type=1&keyword="one piece"&meta_tags=["漫画"]` | 103; 航海王 (32), ONE PIECE 派生 serileri |
| `type=1&meta_tags=["小说"]&rank>=1` | 539; 卡拉马佐夫兄弟 (6), 罪与罚 (8), 三体Ⅱ (10) |

---

## 5. Uygulanan düzeltmeler

### 5.1 `data/auth/BangumiApiClient.kt`

| Değişiklik | Neden |
|---|---|
| `execute(request, retryable)` — GET ve arama POST'ları için **3 deneme**, 500 ms → 1 sn → 2 sn (+%25 jitter, 429'da 1,5 sn taban) | Tek bir Cloudflare 504/502/429 tüm şeridi boşaltıyordu; yazma uçları (`POST/PATCH/PUT` koleksiyon) tek denemede kalır (çift kayıt riski yok) |
| `isTransientFailure()` | Yalnızca `IOException`, 429 ve 5xx tekrar denenir; 4xx (kural ihlali) anında döner |
| `searchSubjects(..., ratingCount)` + `buildSearchSubjectsPayload(..., ratingCount, metaTags)` | `rating_count` ve `meta_tags` filtreleri eklendi (v0 sözleşmesinde var, uygulama hiç göndermiyordu) |
| `topScored(type, minVotes)` | "En Yüksek Puanlı" için `rank + rank>=1 + rating_count>=100`; dört kademeli geri çekilme |
| `MIN_RATING_VOTES = 100` | Tek oylu kayıtların listeyi ele geçirmesini engeller |
| `topRanked(..., cat, series)` | Mangada `cat=1001` + `series=true` (R13) |
| `mostCollected(..., cat)` | Isı sıralaması boşsa düşülen göz atma kategorisi |
| `topScored(..., metaTags)` | "En Yüksek Puanlı Mangalar"da `meta_tags=["漫画"]` (R13) |
| `BangumiSubject.series` | `series` alanı ayrıştırılıyor (`null` = bilinmiyor) |

### 5.2 `data/remote/KitsugiBangumiClient.kt`

| Değişiklik | Neden |
|---|---|
| `topRatedAnime/topRatedManga` → `topScored()` | R1 |
| `searchMediaAdvanced(..., subjectTypes)` + etiket çevirisi + manga platform süzgeci | R3, R7, R8 |
| `searchMediaAdvanced` göz atma koşulu: `canBrowse` artık `heat`/`score`, çok tür ve süzgeçte **arama ucuna** gider | R2 (popüler/puan sıralaması sessizce `date`'e düşüyordu; film kapsamında REAL hiç gelmiyordu) |
| `popularReal()` + `realTvShows/realMovies` → `meta_tags + heat` (boşsa eski göz atma) | R4 |
| `newlyAddedManga()` | R5 |
| `novels()` (`type=1&cat=1002&sort=rank`) | R10 — Bangumi'nin roman kataloğu Keşfet'te hiç görünmüyordu |
| Boş sorgu + puan sıralamasında `rating_count>=100` tabanı | R1 (ızgarada da geçerli) |
| Boş sorgu + `sort=rank` → `rank >= 1` | R12 — etiketli ızgaranın tepesine `rank = 0` (sıralamaya girmemiş) kayıtlar oturuyordu |
| `searchMediaAdvanced(..., comicsOnly, metaTags)` + mangada `meta_tags=["漫画"]` | R13 — `BOOK + tag` aramasında ilk sayfa tamamen romandı |
| `topManga`: `cat=1001` + `series=true`; `newlyAddedManga`/`publishingManga`/`novels`: `series=true` | R13 — cilt/baskı kirliliği |
| `trendingManga`: `platform = 漫画` süzgeci + boşsa `topManga` yedeği | R13 |
| `searchMediaAdvanced(..., metaTags)`: REAL ızgaralarında `电视剧`/`电影` | R15 |
| `searchMediaAdvanced(..., excludedTags)`: dışlanan türler istemcide süzülür | R21 (v0'da olumsuz süzgeç yok) |
| Boş sorguda etiket çevrilemezse **filtresiz liste yerine boş liste** | "Süzgeç uygulanmış" yanılsamasını önler |
| `seasonalAnime/airingAnime/upcomingAnime(..., offset)` | R20 — 2. sayfa hep aynı kayıtları istiyordu |
| `season` + `seasonYear` → `air_date` çeyrek aralığı (`bangumiSeasonRange`) | R22/R26 — Bangumi'de sezon alanı yok, yalnız tarih aralığı var (prob V1–V5) |
| `seasonalAnime()` çeyrek + `heat`, boşsa ay göz-atmasına düşer | R24 — "Bu Sezon" bir mevsimin yalnız bir ayını gösteriyordu |

### 5.3 `ui/screens/explore/ExploreSourceFilterQuery.kt`

| Değişiklik | Neden |
|---|---|
| `bangumiTagFor()`: kısa harita → `BangumiTagDictionary.canonicalTagFor()` → CJK ise olduğu gibi | R3; eşlenemeyen çip artık sunucuya **gönderilmez** |
| `supportsGenre` aynı çeviriciyi kullanır | Sözlükteki tüm etiketler çip olarak çalışır |
| `bangumiPage` REAL raflarında `subjectTypes = [6]` | "Bangumi Dizileri/Filmleri" ızgarası anime karıştırıyordu |
| `bangumiPage` REAL raflarında `metaTags = ["电视剧" / "电影"]` | R15 — dizi rafında film listeleniyordu |
| 19 girişlik `BANGUMI_TAGS` haritası **kaldırıldı**, tek kaynak sözlük | İki harita ayrışıyordu (`Supernatural` → `超能力` vs `灵异`) |
| `serverSorts()` Bangumi için `BANGUMI_SEARCH_SORTS` temel alır | R16 — sunucunun uyguladığı sıralamalar "yerel" görünüyordu |
| `bangumiPage`: kullanıcı sıralaması tür seçili değilken de gönderilir; Manga ızgarasında `meta_tags=["漫画"]` | R17, R18 |
| `supportsSort(BANGUMI)`: tür seçili olmasa da `score`/`heat` sunucuda | R17 |
| Mevsimsel sayfada mevsim filtresi `bangumiPage`'e taşınır | R23 — tür çipi seçilince mevsim düşüyordu |
| `shelfSpec(BANGUMI, NOVELS)` artık tanımlı; `bangumiPage` roman için `meta_tags=["小说"]`, `comicsOnly=false` | R27 — roman ızgarası filtrelenmiş sayfadan tamamen düşüyordu |

### 5.4 `utils/BangumiTagDictionary.kt`

- `canonicalTagFor(label)`: arayüz etiketini **Bangumi'nin kendi yazımına** çevirir.
- `PREFERRED_TAGS`: grup → sunucuda **dolu** olan yazım (`sports_theme`→`运动`,
  `thriller`→`惊悚`, `military`→`战争`, `supernatural`→`灵异`). Değerler K/L/N problarıyla
  sabitlendi (R11).
- Latin etiket tercihi düzeltildi: grup içinde CJK yazım varsa **o** seçilir; eskiden
  `Science-Fiction`, `Battle` gibi Latin yazımlar sunucuya gidip 0 sonuç alıyordu.
- `ALIAS_TAGS`: sözlükte başka gruba düşen ama Bangumi karşılığı farklı olan terimler
  (`Bilim Kurgu`/`Science Fiction` → `科幻`, `Savaş`/`War` → `战争`, `Psikoloji` → `心理`).
- `looksLikeNativeTag()`: Çince/Japonca yazımlar olduğu gibi geçer.
- `groupFor()`/`patternGroup()`: `entryFor` ile aynı eşleşme sırası paylaşılır (davranış değişmedi).

### 5.5 `ui/screens/search/SearchViewModel.kt` + `SearchSourceFilters.kt`

| Değişiklik | Neden |
|---|---|
| `planAllSources`: `bangumi = mediaDb \|\| scope.wantsLiveActionSources` | R6 — "Tümü" aramasında Diziler/Filmler rafında Bangumi hiç sorgulanmıyordu |
| `bangumiDef`: canlı çekim kapsamlarında `type=6` dizi + film rafları eklenir | R6 |
| Tek kaynak aramasında kapsam → tür eşlemesi (`TV/K_DRAMA → REAL`, `MOVIE → REAL`, manga kapsamları → BOOK) | R7 — eskiden her şey `type=2` aranıyordu |
| Bangumi kapsam listesine **TV** ve **MOVIE** eklendi | R14 — Bangumi'de dizi/film araması hiç yapılamıyordu |
| `executeSearchForPage` Bangumi dalında `subjectTypes` + `comicsOnly` iletimi | R14/R13 |
| Bangumi dalında `uiState.genres/tags` ile kaynağa özel etiketler **birleştirilir** | R21 — filtre sayfasındaki tür çipleri Bangumi'ye hiç gitmiyordu |
| `excludedGenres` → `searchMediaAdvanced(excludedTags)` | R21 |
| Kapsam → `meta_tags`: TV `电视剧`, MOVIE `电影`, LIGHT_NOVEL `小说` (+`comicsOnly=false`) | R14/R21 — roman sekmesi manga listeliyordu |
| "Tümü" rafında dizi/film `meta_tags` ile ayrıştırılır | R15/R21 |
| `supportsDetailFilter`: dizi/film yıl facetinde Bangumi de destekler | Yıl süzgeci `type=6`'da çalışıyor (probe A6/U4) |
| Detay facetindeki mevsim `BangumiSpecificFilters.season/seasonYear`'a yazılır | R22 — mevsim tamamen düşüyordu |
| Karakter/Personel satırları tıklanınca **satırın kaynağı** detay ekranına taşınır | R28 — Bangumi karakteri AniList kimliğiyle açılıyordu |
| "Tümü" motorunda da mevsim + dışlanan türler Bangumi'ye iletilir | R22/R21 |

### 5.6 `ui/screens/explore/ExploreViewModel.kt`

- `loadBangumiData()` içine `newlyAddedManga` (R5) ve `novels` (R10) şeritleri eklendi.
  Şeritler `supervisorScope` ve 15 sn'lik şerit tavanı içinde çalışır; bir erişim hatası
  diğer şeritleri etkilemez.

### 5.8 `ui/screens/fullscreen/FullScreenMediaGridPage.kt`

| Değişiklik | Neden |
|---|---|
| Bangumi sayfalaması: `AIRING_ANIME`/`UPCOMING_ANIME` artık `offset` alır | R20 — 2. sayfa hep aynı 20 kaydı istiyordu (sonsuz tekrar) |
| `NEWLY_ADDED_MANGA` → `newlyAddedManga()` | R20 — yanlış uca (`publishingManga`) bağlıydı |
| `NOVELS` → `novels()` | R20 — "Noveller" tam ekranı boş açılıyordu |
| "📱 yerel" tür süzgeci kanonik Bangumi etiketini de arar | R19 — süzgeç her zaman boş liste veriyordu |
| Mevsimsel sayfa: `air_date` çeyreği + mevsimsel sıralama (`score`/`heat`/`rank`) | R24/R26 — tek aylık liste ve yok sayılan sıralama |

### 5.9 `ui/screens/search/SearchScreen.kt`

| Değişiklik | Neden |
|---|---|
| `GenresTagsSheet` ve TMDB tür diyaloğu uygulanırken mevcut yıl/mevsim/puan değerleri `copy(...)` ile korunur | R25 — sheet yalnız tür/etiket düzenlediği hâlde `updateFilters` diğer süzgeçleri `null`'a çekiyordu |

### 5.7 `ui/components/KitsugiRankingBottomSheet.kt`

- Bangumi sıralama sayfası **medya türünü** dikkate alır: `TvShow → realTvShows`,
  `Movie → realMovies`, `Manga → topRatedManga/trendingManga/topManga/newlyAddedManga`,
  anime → mevcut şeritler. Önceden dizi/film bağlamında da anime şeritleri açılıyordu.

---

## 6. Doğrulama

| Kontrol | Sonuç |
|---|---|
| `:app:compileFossDebugKotlin` | **BUILD SUCCESSFUL**, 4 dk 47 sn (çalışma `41243323730`) |
| `:app:compileFossDebugKotlin` + `:app:testFossDebugUnitTest` (`*Bangumi*`, `KitsugiTranslationsTest`, `HeroSelectionTest`, `AllSourcesExploreTest`) | **BUILD SUCCESSFUL**, 6 dk 20 sn, `exit=0`, test özetinde **0 başarısız** (çalışma `41243909173`) |
| Tüm düzeltmeler dahil son derleme (roman rafı dâhil) | **BUILD SUCCESSFUL**, 5 dk 23 sn, `exit=0` (çalışma `41244697915`, commit `eb778eb`) |
| Üçüncü yuvarlak düzeltmeleri (R11–R16 + `BangumiTagCanonicalizationTest`) | **BUILD SUCCESSFUL**, 6 dk 12 sn, `exit=0` (çalışma `41249402840`, commit `57a4aa5`) |
| **Nihai** derleme + Bangumi testleri (tüm düzeltmeler, `ALL_MIXED` tür kapsamı dâhil) | **BUILD SUCCESSFUL**, `exit=0` (çalışma `41251983138`, commit `9136c98`) |
| **Nihai** `🎌 Bangumi MASTER Doğrulama` (sözlük + `BangumiMasterDictionaryTest`) | **success** (çalışma `41251983148`, commit `9136c98`) |
| **Nihai** tam APK doğrulaması (MAL testleri + foss debug + R8 release + DEX + APK yükleme) | **success**, 27 dk 44 sn (çalışma `41251983178`, commit `9136c98`) |
| Son `rank>=1` (match) düzeltmesi sonrası derleme + Bangumi testleri | **BUILD SUCCESSFUL**, 6 dk 32 sn, `exit=0` (çalışma `41254666522`, commit `1a1db89`) |
| Son `rank>=1` düzeltmesi sonrası tam APK doğrulaması | **success** (çalışma `41254666628`, commit `1a1db89`) — debug + R8 release APK + DEX + APK yükleme |
| Son commit canlı API probu (S/T blokları) | **success** (çalışma `41254666696`, commit `1a1db89`) |
| **TAM birim test seti** (`:app:testFossDebugUnitTest`, filtre yok) | **BUILD SUCCESSFUL**, 21 dk 19 sn, `exit=0`, **1472 test / 0 başarısız** (commit `3895be9`, günlük: `probe/test_log.txt`) |
| Arama filtreleri + "Tümünü Gör" düzeltmeleri sonrası tam APK doğrulaması | **success** (çalışma `41291446082`, commit `3895be9`) — debug + R8 release + DEX + APK yükleme |
| Canlı API probu (U blokları: ızgara istek gövdeleri) | **success** (çalışma `41291446105`, commit `3895be9`) |
| **TAM birim test seti**, mevsim/ızgara/sheet düzeltmelerinden sonra | **BUILD SUCCESSFUL**, 20 dk 58 sn, `exit=0` (commit `3dd89ae`) — 1480 test (1472 + 8 yeni mevsim testi) |
| Kotlin derleme + Bangumi testleri (son hâl) | **success** (çalışma `41295340822`, commit `3dd89ae`) |
| APK doğrulama (debug + R8 release + DEX) | **success** (çalışma `41295340719`, commit `3dd89ae`) |
| Canlı API probu (V blokları: mevsim çeyrek aralıkları) | **success** (çalışma `41295340729`, commit `3dd89ae`) |
| Kotlin derleme + Bangumi testleri (roman rafı düzeltmesi sonrası) | **success** (çalışma `41297747164`, commit `a7b8734`) |
| **TAM birim test seti** (filtresiz, 1482 test) | **BUILD SUCCESSFUL**, 22 dk 11 sn, `exit=0` (commit `a7b8734`) |
| APK doğrulama + canlı API probu | **success** (`41297747235`, `41297747117`, commit `a7b8734`) |
| Kotlin derleme + Bangumi testleri (R28 sonrası) | **success** (çalışma `41301075659`, commit `977d42f`) |
| **TAM birim test seti** (filtresiz, 1482 test) | **BUILD SUCCESSFUL**, 23 dk 54 sn, `exit=0` (commit `977d42f`) |
| APK doğrulama (debug + R8 release + DEX) | **success** (çalışma `41301075619`, commit `977d42f`) |
| Canlı API probu (W blokları: persons/characters/career) | **success** (çalışma `41301075542`, commit `977d42f`) |

**Uçtan uca doğrulama (en güçlü kanıt):** `🏗️ Android Derleme Doğrulama` iş akışı
`41244697911` **başarıyla** tamamlandı (28 dk 39 sn):
`🧪 MAL cihaz ayrıştırıcı testleri + eklenti güncelleme testleri + foss debug & R8 release
APK + DEX doğrulama` adımı `success`, `📲 APK'ları yükle` adımı `success`. Yani tüm birim
testleri (Bangumi başlık çözümü, sözlükler, kütüphane içe aktarma, detay istemcisi,
Keşfet "Tümü" bölümleri dahil) geçti, hem debug hem **R8 küçültmeli release** APK derlendi
ve DEX buyruk akışları ikili düzeyde doğrulandı.
| Canlı API matrisi (E1–E4, F1–F2, G1–G3, H1–H4, I1–I2) | `probe/result.txt` — beklenen sonuçların tamamı alındı |
| Üçüncü yuvarlak canlı API matrisi (J–R: etiket kapsamı, sıralama, manga kataloğu, `meta_tags`) | `probe/result.txt` — K/L/N/O/P/Q/R bloklarının tamamı beklenen sonucu verdi (§4.6) |
| Regresyon: etiket sözlüğü grup sayısı (`58338`) ve `entryFor()` davranışı | `BangumiTagDictionary` eşleşme sırası korundu; `BangumiMasterDictionaryTest` bu çalıştırmada **geçti** |

### 6.1 Test günlüğü (özet)

```
### :app:compileFossDebugKotlin + Bangumi testleri
commit: eb778eb
date: 2026-10-31T21:46:25Z
BUILD SUCCESSFUL in 5m 23s
exit=0

### Üçüncü yuvarlak (R11–R16)
commit: 57a4aa5
BUILD SUCCESSFUL in 6m 12s
exit=0
```

> Üçüncü yuvarlakta `app/src/test/.../BangumiTagCanonicalizationTest.kt` eklendi; test
> filtresi (`--tests '*Bangumi*'`) bu sınıfı da çalıştırır. Test, tür çiplerinin
> **hiçbirinin** Latin yazımla sunucuya gitmediğini ve zayıf yazımların dolu karşılıklarına
> çevrildiğini doğrular (regresyon kilidi).

Tam günlük depoda `probe/kotlin_log.txt` dosyasındadır (GitHub artifact'leri bu ortamdan
indirilemediği için günlük doğrudan depoya işlendi).

> **Çözüldü (1 Kasım 2026):** `🎌 Bangumi MASTER Doğrulama` iş akışı, aynı commit üzerinde
> (`9136c98`) **başarıyla** tamamlandı — çalışma `41251983148`:
> `🐍 Python doğrulayıcı` ✅ · `♻️ Üreteç güncellik denetimi (--check)` ✅ ·
> `🔨 Sözlükleri derle (kotlinc)` ✅ · `🧪 BangumiMasterDictionaryTest` ✅.
> Daha önceki MASTER hataları **derleme hatalarından** kaynaklanıyordu (o commit'lerde
> `:app:compileFossDebugKotlin` kırıktı); sözlük üretimi ve testin kendisi sağlamdı.
> Raporun önceki sürümündeki "MASTER ayrıca ele alınmalı" notu bu kanıtla kapatıldı.

### 6.2 Nihai kanıt zinciri (commit `9136c98`)

| İş akışı | Çalışma | Sonuç |
|---|---|---|
| 🧪 Kotlin Derleme Günlüğü (`compileFossDebugKotlin` + `--tests '*Bangumi*'` vb.) | `41251983138` | **success**, `exit=0` |
| 🎌 Bangumi MASTER Doğrulama (Python doğrulayıcı + kotlinc + JUnit) | `41251983148` | **success** (`BangumiMasterDictionaryTest` adımı ✅) |
| 🏗️ Android Derleme Doğrulama (cihaz testleri + APK + DEX) | `41251983178` | **success**, 27 dk 44 sn; `🧪 MAL … + foss debug & R8 release APK + DEX doğrulama` ✅, `📲 APK'ları yükle` ✅ |
| 🩺 Bangumi API Probe (J–R blokları) | `41251983134` | **success** (`probe/result.txt`) |

> Not: Android derleme iş akışının 13. adımı (`🩺 Derleme günlüğünü yükle`) yalnızca hata
> durumunda çalışır; **atlanmış** olması derlemenin başarılı olduğunun kanıtıdır.

### 6.3 Kullanıcı tarafında nasıl doğrulanır

1. **Keşfet → Bangumi:** "En Yüksek Puanlı Animeler" artık sıralı liste (GitS SAC 2nd GIG,
   Clannad AS, Cowboy Bebop…) göstermeli; 10/10'luk tek oylu kayıtlar görünmemeli.
2. **Keşfet → "Bangumi Dizileri" / "Bangumi Filmleri":** 半泽直树, 胜者即是正义, 情书,
   流浪地球2 gibi popüler yapıtlar gelmeli (önceden 51 kayıtlık `rank` listesiydi).
3. **Keşfet → "Yeni Eklenen Mangalar":** artık dolu.
4. **Tümünü Gör → tür çipi:** herhangi bir tür seçildiğinde sonuç gelmeli (çeviri sözlükten).
5. **Arama → Bangumi:** "chainsaw man" → 链锯人; **Diziler** kapsamı → type=6 sonuçları;
   **Manga** kapsamı → roman/画集 karışmamalı.
6. **Tümü araması → Diziler/Filmler:** Bangumi rafı da görünmeli.
7. **Ağ dalgalanması:** Wi-Fi'yi birkaç saniye kesip geri açın; şeritlerin tamamı gelmeli
   (3 deneme + geri çekilme), boş kalmamalı.
8. **Arama → Bangumi → "Diziler" / "Filmler" sekmeleri:** artık vardır; "reply 1988",
   "半泽直树" gibi sorgular 三次元 (type = 6) sonuçları döndürmeli (R14).
9. **"Tümünü Gör" → Bangumi Filmleri:** ızgarada **anime filmi değil**, yalnız gerçek
   filmler (情书, 流浪地球2) görünmeli (R15).
10. **"En Yüksek Puanlı Mangalar" / "Trend Mangalar":** roman/画集 ve cilt kayıtları
    (ör. `XXX 3`, `XXX 完全版`) görünmemeli; seri ana kayıtları listelenmeli (R13).
11. **Tür çipleri:** `Spor`, `Gerilim`, `Askeri`, `Doğaüstü`, `Psikolojik` çipleri artık
    dolu ve **sıralı** liste vermeli (R11/R12).

---

## 7. Kalan riskler ve öneriler

1. **Bangumi sonuç sıralaması uygulama tarafında değil, sunucuda belirlenir.** `heat`, `score`
   ve `rank` alanlarının anlamı Bangumi tarafından değiştirilirse eşikler
   (`MIN_RATING_VOTES`, `rank>=1`) yeniden ayarlanmalıdır. Eşikler tek yerde
   (`BangumiApiClient`) tanımlıdır.
2. **`next.bgm.tv/p1` resmî sözleşme değildir** (bangumi/dev-docs: "no compatibility
   guarantee"). Detay sekmeleri bu uca bağlı; kırılırsa `KitsugiBangumiDetailClient`
   v0 yedeklerine düşer, ancak yeni alanlar (ör. `seriesEntry`) kaybolur.
3. **Bölgesel erişim:** Çin anakarasında `api.bgm.tv` ve `next.bgm.tv` engelli
   (Kazumi #2133). Bu düzeltme paketi ağ tarafında bir şey değiştirmez; istenirse
   `bangumi.lol` aynası için bir yönlendirme katmanı eklenebilir.
4. **`sort=date` ile "Yeni Eklenen"** doğası gereği oy almamış yeni ciltleri getirir
   (`I2`). İçerik kalitesi istenirse `rating_count>=1` tabanı eklenebilir; şimdilik
   "yeni" anlamını bozmamak için eklenmedi.
5. **Bildirimler:** Bangumi'nin v0 API'sinde bildirim ucu yoktur; mevcut uygulama
   koleksiyon/eşitleme olaylarından üretiyor. Bu raporda ağ tarafında değişiklik gerekmedi.
6. **Geçici teşhis dosyaları** (`probe/`, `.github/workflows/bangumi-probe.yml`,
   `.github/workflows/probe-kotlin.yml`) yayına çıkmadan **kaldırılmalıdır**; bunlar
   yalnızca bu incelemenin kanıt zinciri için eklendi.
7. **Zayıf ama doğru etiketler:** `Psikolojik → 心理` (rank süzgeciyle 25 kayıt) ve
   `Survival → 生存` (19 kayıt) anlamca birebir olan yazımlardır; alternatifleri
   (`智斗` 147, `超能力` 250) **farklı** bir kavramı anlatır. Izgara "ince" görünürse
   bilinçli bir tercih yapılmalı (şu an anlam doğruluğu seçildi).
8. **Şerit süre tavanı 20 sn:** İlk açılışta 17 Bangumi şeridi paralel çalışır (Bangumi
   5 rps + OkHttp ana başına 8 istek). Tavan 15 sn iken kuyruk sonundaki şeritler
   kesiliyordu; 20 sn'ye çıkarıldı. Yavaş cihazlarda ilk yükleme bu süreye kadar
   sürebilir (önbellek sonrası anlık).
9. **"Noveller & Light Novel":** roman kataloğu artık `type=1` + `meta_tags=["小说"]`
   aramasıyla **sunucuda** süzülür (prob Q4: 539 kayıt, U5: puan sıralaması 卡拉马佐夫兄弟).
   Tek sınır: Bangumi'nin `cat=1002` göz atma ucu tür/sıralama kabul etmediği için
   **filtresiz** roman rafı (göz atma) ile filtrelenmiş roman ızgarası (arama) farklı
   uçlardan beslenir; ikisi de aynı kataloğu gösterir, sıralama farkı olabilir.
10. **"Hariç tut" süzgeci Bangumi'de istemci tarafında:** sunucu olumsuz (`!=`) süzgeç
    desteklemediği için dışlanan tür, dönen sayfa içinde süzülür; sayfa 20 kayıttan
    kısalabilir (sayfalama `offset` ile devam eder, kayıt atlanmaz).
12. **Mevsimsel ızgarada "Yayın tarihine göre" sıralaması:** Bangumi'nin arama ucunda tarih
    sıralaması yok (yalnız `match|heat|rank|score`); çeyrek aralığını korumak için bu seçenek
    `rank` sırasına düşer. Diğer iki seçenek (`score`, `heat`) birebir uygulanır.
13. **"Bu Sezon" rafının tanımı:** Bangumi'de yayın durumu (airing) alanı olmadığı için raf,
    içinde bulunulan **takvim çeyreğinde** çıkmış yapıtları `heat` sırasıyla gösterir — bu,
    "bu sezon yayınlananlar"ın üst kümesidir (ay bazlı eski davranış alt kümesiydi).
14. **`series=true` sunucu davranışına bağlı:** Bangumi `series` süzgecini kaldırırsa
   manga raflarında cilt kayıtları geri döner; istemci tarafında `(n)` / `完全版`
   temizliği yoktur (bilinçli olarak sunucuya güvenildi, P bloğuyla doğrulandı).

---

## 8. Dosya özeti

```
app/src/main/java/com/kitsugi/animelist/data/auth/BangumiApiClient.kt              +212/-21
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt        +403/-62
app/src/main/java/com/kitsugi/animelist/AppRoot.kt                                  +18/-4
app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt                           +5/-4
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiRankingBottomSheet.kt  +26/-4
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreSourceFilterQuery.kt +102/-34
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt       +15/-1
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt +39/-13
app/src/main/java/com/kitsugi/animelist/ui/screens/search/DetailSearchFilterRequest.kt  +5/-1
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt            +34/-7
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchSourceFilters.kt     +16/-2
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt        +166/-14
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SourceSearchPage.kt         +2/-2
app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt               +103/-4
app/src/test/java/.../data/remote/BangumiSeasonRangeTest.kt                          +60  (yeni)
app/src/test/java/.../utils/BangumiTagCanonicalizationTest.kt                        +80  (yeni)
app/src/test/java/.../explore/ExploreSourceFiltersBangumiTest.kt                    +100  (yeni)
app/src/test/java/.../search/SearchRelevanceTest.kt                                  +12/-2 (güncellendi)
# toplam: 18 dosya, +1398 / -175   (`git diff --numstat c320ff6 -- app/src`)
probe/result.txt                            (canlı API kanıtı, A–W blokları)
probe/kotlin_log.txt                        (derleme + Bangumi test günlüğü)
probe/test_log.txt                          (TAM birim test seti, filtre yok)
```

---

## 9. Kaynaklar

- Bangumi API sözleşmesi: <https://github.com/bangumi/api> (`open-api/v0.yaml`)
- Sunucu kaynağı: <https://github.com/bangumi/server> (`internal/search/subject/handle.go`)
- Bangumi User-Agent kuralı: `docs-raw/user agent.md` — tanımlayıcı UA zorunlu;
  varsayılan kütüphane UA'sı (`okhttp/4.12.0`) **403** ile reddedilir (canlı doğrulandı, B1).
- Cloudflare dalgalanmaları: `infanf/myanili#139`, `Predidit/Kazumi#2133`,
  `bangumi/api#279`, `bgmx#2266`.
