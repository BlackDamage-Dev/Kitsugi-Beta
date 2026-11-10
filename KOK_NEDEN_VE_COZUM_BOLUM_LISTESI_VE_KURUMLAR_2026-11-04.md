# Bölüm Listesi Boş Kalması + Kurumlar Sonsuz Kaydırma — Kök Neden ve Çözüm (2026-11-04)

Bu turda iki ayrı şikâyet ele alındı:

1. **Oynatıcı → “Bölüm Listesi”** çekmecesi (3. görsel) **bomboş** açılıyor.
2. **Arama → “Kurumlar”** çekmecesi (1–2. görsel) “60 / 3802” (veya “60 / 8171”) yazıp
   **devamını getirmiyor**; kullanıcı aşağı kaydırdıkça daha fazla kurum göremiyor.

---

## 1. Bölüm Listesi her zaman dolu (bu dalda uygulandı)

### Kök neden
`KitsugiPlayerViewModel.loadEpisodes()` yalnızca şu koşulda çalışıyordu:

```kotlin
if (malId != null || aniListId != null) { … KitsugiMediaTabsClient.fetchEpisodes(…) … }
```

Yani:

| Durum | Sonuç |
|---|---|
| İçerik **TMDB / Simkl** üzerinden açıldı (yalnızca `tmdbId` var) | Liste boş |
| İçerik **doğrudan eklentiden (CS3)** açıldı (`cs3Url` + `cs3ApiName` var, MAL/AniList kimliği yok) | Liste boş |
| MAL/AniList kimliği çözülemedi ya da istek düştü | Liste boş (istisna yutuluyordu) |
| Film | Boş (doğru davranış) |

Ek olarak çekmece **hiçbir yükleme/boş/hata durumu göstermiyordu**: `episodes` boşsa
`LazyColumn` sadece boş çiziliyor, kullanıcı “yükleniyor mu, hata mı, hiç mi yok?”
anlayamıyordu.

### Çözüm
Yeni **çok kaynaklı çözücü**: `data/repository/PlayerEpisodeListResolver.kt`

Sıra (biri yeterli olursa durur, hepsi aynı listede birleştirilir):

1. **Detay sayfası önbelleği** (`DetailCache.getMediaEpisodes`) — kullanıcı detay sayfasını
   açtıysa bölümler yerelde hazır, ağ isteği yok.
2. **Meta veri API’leri** — `KitsugiMediaTabsClient.fetchEpisodes` üzerinden
   **MAL/Jikan → AniList → TMDB** (sezon bölümleri, adlar, görseller; zaman aşımı korumalı).
3. **Açık eklenti (CS3)** — içerik bir eklentiden açıldıysa o eklentinin kendi bölüm listesi
   (`LoadResponse.episodes`) okunur. Bilinen eklenti URL’si yoksa başlık + alternatif
   başlıklarla eklentilerde arama yapılır, en iyi eşleşmenin bölüm listesi alınır.
4. **Sentez** — hiçbiri yanıt vermezse, bilinen toplam bölüm sayısı ya da en azından
   o an oynatılan bölüme kadar numaralı satırlar üretilir. **Çekmece asla boş kalmaz.**

`KitsugiPlayerViewModel` tarafında:

- `loadEpisodes(force)` artık yukarıdaki çözücüyü kullanıyor; sonuç **her zaman** yazılıyor.
- `episodesLoading: StateFlow<Boolean>` eklendi (çekmecede yükleme göstergesi).
- `ensureEpisodesLoaded()` — çekmece açıldığında (`showDialog(EpisodeList)`) ve bölüm
  değiştiğinde çağrılır; liste eksikse arka planda tamamlanır.
- `refreshEpisodes()` — “Yeniden Dene” tüm kaynakları sıfırdan dener.
- `appendEpisodeIfMissing()` — bölüm geçişinde oynatılan bölüm listede yoksa yerelde eklenir
  (her bölümde yeniden ağa çıkılmaz).

`PlayerDialogsHost.EpisodeListDialog` tarafında:

- Liste hazırlanırken **plazma/spinner + “Bölüm listesi hazırlanıyor…”**,
- liste doluysa üstte küçük “güncelleniyor” satırı,
- hiç sonuç yoksa **sebep + “Yeniden Dene”**,
- film ise **“Film içeriklerinde bölüm listesi bulunmaz.”**,
- liste açıldığında **oynatılan bölüme otomatik kaydırma**.

Yeni metinler `values/strings.xml` + `values-en/strings.xml` (ikisi de 17.544 `<string>`,
parite korunuyor): `bolum_listesi_yukleniyor`, `bolum_listesi_guncelleniyor`,
`bolum_listesi_bos`, `bolum_listesi_bos_ipucu`, `bolum_listesi_film`.

### Test kontrol listesi
- [ ] MAL/AniList kimliği olan anime → liste dolu, oynatılan bölüm işaretli.
- [ ] TMDB dizisi (MAL kimliği yok) → TMDB sezon bölümleri.
- [ ] Yalnızca eklentiden (CS3) açılan dizi → eklentinin bölüm listesi.
- [ ] Hiçbiri yok (çevrimdışı) → en azından numaralı satırlar + “Yeniden Dene”.
- [ ] Film → “Film içeriklerinde bölüm listesi bulunmaz.”
- [ ] Bölüm geçişlerinde çekmece boşalmıyor, oynatılan bölüme kaydırılıyor.

---

## 2. Kurumlar çekmecesinde sonsuz kaydırma

### Durum / neden ayrı dosya
“Kurumlar” çekmecesi (`InstitutionFilterSheet.kt`, `OrgFilterSheet.kt`,
`TaxonomyCatalog.kt`, `res/raw/taxonomy_catalog.json`) **bu dalın (main) kodunda yok**;
bu özellik `feat/golge-bahcesi-integration` hattında (PR #37, v2.4.413) eklenmiş ve
yayınlanan APK’lar o hattan çıkıyor (`v2.4.414`).

Bu dal main tabanlı olduğu için çekmece dosyası buraya kopyalanırsa **ölü kod** olurdu
(hiçbir ekran onu çağırmıyor). Bu yüzden düzeltme, **yayın hattındaki dosyaya birebir
uygulanan patch** olarak verildi:

```
KURUMLAR_SINIRSIZ_KAYDIRMA_FIX_2026-11-04.patch
```

Uygulama (yayın hattında):

```bash
git apply KURUMLAR_SINIRSIZ_KAYDIRMA_FIX_2026-11-04.patch
```

### Patch’in yaptığı
- Pencere **sabit** (`take(60)` / `take(80)`) olmaktan çıkıp **kademeli** büyüyor:
  iç liste sonuna yaklaşınca (+60 satır, `CATALOG_SCROLL_THRESHOLD_PX = 600`) yeni pencere eklenir.
- Katalog artık **tamamı** erişilebilir (9.457 kayıt) — ama donma koruması korunuyor,
  hepsi tek seferde çizilmiyor.
- Bilgi satırı: “N / M kurum gösteriliyor — **devamı için kaydırın**”, hepsi yüklendiğinde
  “M kurumun tamamı listelendi” (yeni: `search_institution_scroll_hint`,
  `search_institution_all_loaded`, TR+EN).
- `snapshotFlow` ile kaydırma dinleniyor; `verticalScroll(rememberScrollState())` yerine
  tek bir `catalogScrollState` paylaşılıyor.

### Doğrulama
- [ ] Çekmeceyi aç, hiç yazmadan sona kadar kaydır → 60 → 120 → 180 … artarak geliyor.
- [ ] En alta inince “tamamı listelendi” yazıyor.
- [ ] Sekme değiştirince (Yapımcı / Yapım Şirketi) pencere baştan başlıyor.
- [ ] Arama yazınca pencere 80’den başlayıp yine kademeli büyüyor.
- [ ] 9.457 kaydı sonuna kadar kaydırmak kasmıyor (pencere kademeli).

---

## Notlar
- Bu dalda derleme yapılamadı (sandbox’ta Android SDK/JDK yok); değişiklikler kaynak
  düzeyinde ve API imzaları mevcut koda karşı doğrulanarak yazıldı.
- Bölüm listesi düzeltmesi main tabanlı olduğu için, yayın hattına taşınırken
  `KitsugiPlayerViewModel.loadEpisodes()` fonksiyonunun yerine geçirilmesi yeterli
  (yayın hattındaki sürüm bu dalınkiyle aynı iskelete sahip).
