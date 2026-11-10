# PLAN/TASK — FilmMakinesi Dizi Bölüm Çıkarma Düzeltmesi (Eklenti Tarafı)

**Tarih:** 2026-11-04 · **Kapsam:** `Kitsugi-Plugins` reposu, `FilmMakinesi` eklentisi (bu görev kullanıcı tarafından eklenti tarafında uygulanacak) · **İlgili uygulama sürümü:** Kitsugi Beta v2.4.341 (ortak küme)
**Teşhis kaynağı:** `KOK_NEDEN_RAPORU_EKLENTI_KAYNAK_BAGIMLILIGI_VE_ORTAK_KUME_2026-11-04.md`

---

## 0. Teşhis özeti (kanıtlı)

FilmMakinesi filmlerde çalışıp dizilerde video verisi veremiyor; HDFilmCehennemi ikisinde de çalışıyor. Video çıkarımı iki eklentide de aynı (CloseLoad/Rapidrame + `PlayerResolver`), fark `load()` içindeki **bölüm çıkarma** mantığında:

| Nokta | HDFilmCehennemi (çalışan) | FilmMakinesi v72 (bozuk) |
|---|---|---|
| Bölüm çıkarma katmanları | 3 katman: `div.seasons-tab-content a` → `div#seasonsTabs-tabContent div.card-list-item` → genel `a[href*='/bolum-']` | Tek seçici: `a[href*='bolum'], div.episodes a, div.partlar a` |
| Sezon/bölüm numarası kaynağı | Bağlantı metni **ve URL** (`sezon-(\d+)`, `bolum-(\d+)`) | Yalnızca bağlantı metni (`(\d+)\.\s*Sezon`, `(\d+)\.\s*Bölüm`); tutmazsa **hepsi S1E1** |
| Bölüm listesi boşsa | `TvSeriesLoadResponse` yine de dönülür (uygulama sayfa-URL yedeğini deneyebilir) | `load()` **null** döner → uygulama için içerik yok hükmünde |

Sonuç: FilmMakinesi, bölüm metni beklenen kalıba uymadığında tüm bölümleri S1E1 etiketler veya hiç bölüm vermez. Uygulama (CsStreamRunner/CsEpisodeMatcher) eklentinin verdiği sezon/bölüm numaralarına güvenmek zorundadır; yanlış/eksik numarayı telafi edemez. Bu yüzden düzeltme **eklenti tarafında** yapılmalıdır.

Not: Uygulama tarafındaki ortak küme (v2.4.341) bu sorunu kısmen hafifletir — eklenti diziyi kendi aramasından bir kez açarsa adres öğrenilir ve katalog kaynağından bağımsız doğrudan denenir. Ancak eklenti hiçbir yoldan bölüm verisi üretemezse hafıza da üretemez; kök düzeltme bu görevdir.

---

## 1. Görevler

### T1 — Bölüm numarasını URL'den de çıkar (kritik)
`FilmMakinesi.kt` → `parseSeriesEpisodes`: sezon/bölüm çıkarımına HDFilmCehennemi paritesinde URL yedekleri ekle:
- Bölüm: metin `(\d+)\.\s*B[öo]l[üu]m` → tutmazsa href `bolum-(\d+)` → tutmazsa href sonundaki sayı kalıpları.
- Sezon: metin `(\d+)\.\s*Sezon` → tutmazsa href `sezon-(\d+)` / href içindeki sezon segmenti → tutmazsa üst sekme/kapsayıcıdan (parent) sezon bilgisi.
- Varsayılan `episode = 1` ataması yalnızca tek bölümlü sayfada kabul edilebilir; birden çok bölüm bağlantısı aynı (sezon, bölüm) çiftine indirgeniyorsa logla (sessizce yanlış eşleme yapma).

### T2 — Bölüm boşsa `null` dönme (kritik)
`load()` içindeki `if (episodes.isEmpty()) return null` davranışını kaldır:
- Sayfa dizi sayfasıysa (`/dizi/` veya bölüm izi varsa) bölümsüz de olsa `newTvSeriesLoadResponse(...)` dön. Uygulama tarafı bölüm listesi boşken sayfa URL'sini bölüm kapsayıcısı olarak denemektedir (sayfa-tipli yedek).
- Başlık bulunamıyorsa (gerçek 404/boş sayfa) `null` dönmeye devam edilebilir.

### T3 — Çıkarma katmanlarını genişlet
Tek seçiciyi katmanlı hâle getir (HDFilmCehennemi sırası örnek alınabilir):
1. Mevcut seçici (`a[href*='bolum'], div.episodes a, div.partlar a`)
2. Sezon sekmeleri/kart listeleri için ek seçiciler (sitenin güncel DOM'una göre doğrulanacak)
3. Son çare: sayfadaki tüm `a[href*='bolum']` bağlantıları (yalnızca `/dizi/` bağlamındakiler)

Her katmanda sonuçlar `distinctBy { it.data }` ile tekilleştirilir.

### T4 — Tür/cevap tutarlılığı denetimi
- `toSearchResult()` içindeki `/dizi/` → `TvType.TvSeries` ayrımının güncel site URL'leriyle uyumunu doğrula (dizi arama sonuçları film sanılırsa uygulama tür filtresi eler).
- `supportedTypes` içinde `TvType.Movie + TvType.TvSeries` ve `supportedSyncNames = Imdb` beyanlarının derlenen `.cs3`'ye aynen yansıdığını manifest üzerinden kontrol et.

### T5 — Test
- `FilmMakinesi/src/test` altına, temsili dizi sayfası HTML fikstürleriyle `parseSeriesEpisodes` birim testleri ekle (fonksiyon gerekirse `internal` test edilebilir bir yardımcıya bölünür):
  - Metni kalıba uyan bölümler
  - Metni boş/ikonlu, numarası yalnızca URL'de olan bölümler
  - Çok sezonlu sayfa (S2 bölümlerinin S1E* olarak etiketlenmediğinin kanıtı)
  - Bölümsüz dizi sayfası → `null` olmayan TvSeries cevabı

### T6 — Sürüm ve yayın
- `FilmMakinesi/build.gradle.kts`: `version = 72 → 73` (veya sıradaki numara).
- `.cs3` derle, `builds` dalına yayınla; uygulama reposundaki gömülü `app/src/main/assets/plugins/plugins.json` kaydını (sürüm + boyut + hash) yeni `.cs3` ile senkronla — aksi hâlde çevrimdışı kurulumlar eski sürüme düşer (geçmişte yaşanan KN-E hatası).

---

## 2. Kabul kriterleri

1. FilmMakinesi ile bir dizide (örn. The Mentalist / Supernatural) S1E1 dışı bir bölüm (S2E5 gibi) doğru bölüm olarak bulunur ve Close/Rapid kaynakları listelenir; logda yanlış sezon/bölüm oynatma yok.
2. Aynı içerik, eklentinin kendi aramasından VE TMDB detayından girildiğinde aynı kaynakları verir (ikinci yol v2.4.341 ortak küme hafızasıyla `🧠 Ortak küme hafızası isabeti` logu üretmelidir).
3. Film akışı gerilemez: bir filmde iki kaynak (Close + Rapid) aynen gelir.
4. Yeni birim testleri geçer: `./gradlew :FilmMakinesi:test` (eklenti reposu düzeni) veya eşdeğeri.

## 3. Kapsam dışı

- Uygulama tarafı değişiklikleri (ortak küme v2.4.341 ile tamamlandı; bu paketteki dosyalarda).
- HDFilmCehennemi'nde değişiklik (referans/parite kaynağı olarak kullanılır).
- CloseLoad/Rapidrame extractor mantığı (çalışıyor; dokunulmaz).
