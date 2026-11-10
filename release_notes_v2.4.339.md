# Kitsugi Beta v2.4.339

## 🇹🇷 Türkçe

### 🔍 Kalan son kurum/tür/etiket boşlukları — kaynak kaynak denetim

v2.4.338'den sonra yedi kaynağın **API sözleşmeleri tek tek koda karşı** yeniden okundu.
Dört gerçek eksik bulundu ve kapatıldı; hiçbiri tahmin değil, API yanıt alanı ↔ kod satırı eşleşmesi.

#### 1) Shikimori — `licensors` ve `publishers` **hiç okunmuyordu**

Shikimori anime yanıtı `licensors` (lisans veren), manga yanıtı `publishers` (yayınevi) alanı
döndürür. Kod yalnızca `genres` + `studios` okuyordu → Shikimori kaynaklı sayfalarda lisans
veren/yayınevi listesi **her zaman boştu**.

- Yeni `parseShikimoriOrgArray()`: dizi elemanları **düz string** (`"licensors": ["Crunchyroll"]`)
  veya **nesne** (`{"id":1,"name":"Kodansha"}`) olabilir — ikisi de okunur.
- Hem Shikimori'nin kendi detay sayfasında hem çapraz toplayıcıda devrede.
- Rusça adlar `toLatinStudioName()` ile Latin harflerine çevrilir (ekranda Kiril yok).

#### 2) Simkl — `network` (kanal) okunmuyordu, `studios` **sabit boş** yazılıydı

`KitsugiSimklDetailClient` içinde `studios = emptyList(), producers = emptyList()` sabitti.
Simkl stüdyo/yapımcı yayınlamaz **ama `network` alanını döndürür** (dizi/anime kanalı).

- `network` (string) ve `networks` (dizi/nesne) → **Kanal** bölümü.
- Simkl artık çapraz toplayıcıda **7. kaynak**: başlık araması → normalize tam başlık eşleşmesi
  (+ yıl ±1) → `genres` + `network`.

#### 3) MAL/Jikan manga — dergi ve yayınevi çapraz toplayıcıda yoktu

`fetchJikan` yalnız `studios` / `producers` / `licensors` okuyordu. Manga tarafında
`serializations` (dergi) ve `publishers` (yayınevi) eklendi; **Dergiler** bölümü artık
MAL kaynaklı mangalarda da doluyor.

#### 4) Kitsu manga — `mangaProductions` istenmiyordu

Kitsu manga detayı `include=categories` ile açılıyordu; yayınevi/dergi bağlı olduğu
`mangaProductions` ilişkisi hiç istenmiyordu. Artık `categories,mangaProductions.producer`
istenir; uç reddederse (400) ilişkisiz isteğe düşülür.

#### 5) Denetim artık 7 kaynağı ve 35 alanı kapsıyor

```
$ python3 scripts/audit_taxonomy_sources.py --strict
Toplam 35 alan denetlendi · 7 kaynak
Kaynak          Çapraz toplayıcıda
Jikan ✔  AniList ✔  Shikimori ✔  Kitsu ✔  Bangumi ✔  TMDB ✔  Simkl ✔
TÜM KAYNAKLAR BEKLENEN ALANLARI OKUYOR — eksik yok.
```

Denetim; Shikimori `licensors`/`publishers`, Simkl `network`, MAL manga `serializations`/
`publishers` alanları **çapraz toplayıcıda** da okunmazsa CI'ı kırıyor.

#### Kaynak başına kurum katkısı (v2.4.339 sonrası)

| Kaynak | Stüdyo | Yapımcı | Lisans | Yayıncı | Kanal | Dergi |
|---|---|---|---|---|---|---|
| AniList | ✔ | — | — | — | — | — |
| MAL/Jikan | ✔ | ✔ | ✔ | ✔ (manga) | — | ✔ (manga) |
| Bangumi | ✔ | ✔ (komite/製作協力/レーベル/配給) | — | ✔ (manga) | ✔ | ✔ |
| Shikimori | ✔ | — | ✔ | ✔ (manga) | — | — |
| Kitsu | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| TMDB | ✔ | ✔ | — | — | ✔ | — |
| Simkl | — | — | — | — | ✔ | — |

Hiçbir kaynakta "alan var, kod okumuyor" durumu kalmadı.

---

## 🇬🇧 English

### 🔍 The last remaining organization/genre/tag gaps — source-by-source audit

After v2.4.338 the API contracts of all seven sources were re-read **against the code**.
Four real gaps were found and closed; each is an API field ↔ code line match, not a guess.

#### 1) Shikimori — `licensors` and `publishers` were never read

The anime response carries `licensors`, the manga response carries `publishers`. The code only
read `genres` + `studios`, so licensor/publisher lists were **always empty** on Shikimori pages.

- New `parseShikimoriOrgArray()` accepts both plain strings (`"licensors": ["Crunchyroll"]`)
  and objects (`{"id":1,"name":"Kodansha"}`).
- Wired into both the Shikimori detail page and the cross-source collector.
- Cyrillic names are transliterated (`toLatinStudioName()`), so no Russian leaks on screen.

#### 2) Simkl — `network` ignored, `studios` hard-coded empty

Simkl publishes no studios/producers **but does return `network`** (the broadcast channel).

- `network` (string) and `networks` (array/objects) → **Networks** section.
- Simkl is now the **7th source** in the collector: title search → normalized exact match
  (+ year ±1) → `genres` + `network`.

#### 3) MAL/Jikan manga — magazine and publisher missing from the collector

`fetchJikan` read only `studios`/`producers`/`licensors`. Manga `serializations` (magazine) and
`publishers` are now included, so the **Magazines** section fills for MAL-sourced manga too.

#### 4) Kitsu manga — `mangaProductions` never requested

Kitsu manga detail used `include=categories`, so the publisher/magazine relationship was never
fetched. It now requests `categories,mangaProductions.producer` and falls back to the plain
request on 400.

#### 5) The audit now covers 7 sources and 35 fields

Denetim; Shikimori `licensors`/`publishers`, Simkl `network`, MAL manga `serializations`/
`publishers` alanları **çapraz toplayıcıda** da okunmazsa CI kırılır.

#### Per-source organization coverage (after v2.4.339)

| Source | Studio | Producer | Licensor | Publisher | Network | Magazine |
|---|---|---|---|---|---|---|
| AniList | ✔ | — | — | — | — | — |
| MAL/Jikan | ✔ | ✔ | ✔ | ✔ (manga) | — | ✔ (manga) |
| Bangumi | ✔ | ✔ | — | ✔ (manga) | ✔ | ✔ |
| Shikimori | ✔ | — | ✔ | ✔ (manga) | — | — |
| Kitsu | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| TMDB | ✔ | ✔ | — | — | ✔ | — |
| Simkl | — | — | — | — | ✔ | — |

No source is left with "the API has the field, the code ignores it".
