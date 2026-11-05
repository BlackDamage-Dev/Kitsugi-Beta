# PLAN & TASK — v2.4.341
# İzle Sayfası: Seeder/Peer Rozetleri · Tüm Kaynaklarda İndirme Boyutu · Ses/Altyazı Rozetleri

- **Tarih:** 2026-11-05
- **Dal (branch):** `arena/676dad00-kitsugi-beta`
- **Temel commit:** `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe` (main)
- **Teslim commit'leri:** `63694de`, `700609a`, `8b377dd` (+ sürüm notu commit'i)
- **Sürüm:** `appVersionName` 2.4.340 → **2.4.341**

---

## 1. Kullanıcı isteği (özet)

Kullanıcının iki ekran görüntüsüyle bildirdiği üç istek:

| # | İstek | Sonuç |
|---|-------|-------|
| 1 | Torrent eklentilerinden gelen kaynaklarda **seeders / leechers** sayısı, uygun kısaltılmış **logo (ikon)** + **sayısal değer** ile her zaman gösterilsin | ✅ Yapıldı |
| 2 | **Tüm** desteklenen video eklentilerinin **tüm** kaynaklarında **dosya boyutu** indirme butonuna basmadan önce görünsün | ✅ Yapıldı |
| 3 | Yine tüm eklenti kaynaklarında **ses (dublaj) ve altyazı rozetleri** uygun şekilde gösterilsin | ✅ Yapıldı |

Ek kural (kullanıcının önceki kararlarıyla tutarlı): **kanıt yoksa rozet basılmaz** — uydurma
kalite/boyut/seed sayısı yazılmaz.

---

## 2. Kök neden analizi (neden görünmüyordu?)

1. **Seeder/peer hiç yoktu.** Stremio şemasında `seeders`/`peers` alanı **zorunlu değil**;
   Torrentio bunları `title` metnine gömüyor (`👤 124 💾 1.7 GB ⚙ YTS`). Kodda bu metni
   ayrıştıran tek bir satır bile yoktu (`// Extra details like dual audio or seeders` yorumu
   vardı, kod yoktu).
2. **Boyut yalnızca metinden** (regex `1.2 GB`) okunuyordu. `behaviorHints.videoSize` (gerçek
   bayt) alanı DTO'da vardı ama `StreamSource`'a **hiç taşınmıyordu**; CloudStream tarafında
   zaten yapılan HTTP `HEAD` yanıtındaki `Content-Length` de atılıyordu.
3. **İki ayrı Stremio eşlemesi** vardı (`AddonStreamRepository` ve `StreamViewModel`). Birinde
   alan eklenince diğerinde kayboluyordu → ekranlar arası tutarsızlık.
4. **Rozet üretimi 4 farklı yerde kopyalanmıştı** (mobil kart, TV satırı, detay seçici,
   oynatıcı içi "Kalite/Kaynak"). Oynatıcı içi sayfada yalnızca eklenti adı + isim vardı.
5. **CloudStream `audioTracks`** (ayrı ses parçası) yalnızca ilk URL için saklanıyordu; sayı
   tutulmuyordu → "Çift dil" kanıtı kayboluyordu.

---

## 3. Yapılan işler (dosya dosya)

### 3.1 Veri katmanı

| Dosya | Değişiklik |
|-------|-----------|
| `data/repository/TorrentSwarmStats.kt` **(yeni)** | Seed/peer ayrıştırıcı. Biçimler: `👤/👥/🌱/⬆️/⬇️/📥`, `Seeders: 124`, `S:124`, `L:12`, `1.2K↑ 300↓`, `seeders/peers/leechers` JSON. `1.2K → 1200` kısaltma çözümü, `formatCompact` (1.2K/1.5M). Sayı yoksa `NONE` → rozet basılmaz. |
| `data/repository/AddonStreamRepository.kt` | `StreamSource`'a 5 yeni alan: `videoSizeBytes`, `fileName`, `swarmStats`, `externalAudioTrackCount`, (+ `metadataText` yardımcısı). **Tek** Stremio dönüşümü `toStreamSource()` eklendi; iki eski kopya eşleme kaldırıldı. |
| `data/remote/AddonStreamClient.kt` | `StreamResponseItem`'a `seeders`, `peers`, `leechers`, `description` alanları (bazı eklentiler JSON'da gönderiyor). |
| `data/repository/StreamInfoResolver.kt` | Boyut: **ölçülen bayt** > metin. Kalite/dil/codec ayrıştırması `fileName`'ı da tarar. `StreamLangInfo.hasAudioTracks/hasInternalSubtitles`. Harici ses parçası → "Çift dil". Yeni `StreamTechInfo` (codec/HDR/kaynak türü). |
| `data/repository/StreamProbe.kt` | HLS master playlist'ten `CODECS` + `BANDWIDTH` okunur (ek istek yok). Boyutu bilinmeyen progressive (.mp4/.mkv) kaynaklarda HEAD ölçümü tetiklenir. Ölçüm tamamlanma sayacı (`completions` Flow) → liste bayat kalmaz. |
| `data/repository/StreamSorter.kt` | Aynı kalitede seeder sayısı tie-break (seeder bilinmiyorsa etkisiz). Sıralamada gerçek bayt kullanılır. |
| `data/cloudstream/CsStreamRunner.kt` | HEAD doğrulamasındaki `Content-Length` → `videoSizeBytes` (fallback ve sanitize yolları da taşır, tek `ConcurrentHashMap`). `audioTracks` sayısı + `providerAudioKind="dual"`. |

### 3.2 Arayüz katmanı

| Dosya | Değişiklik |
|-------|-----------|
| `ui/screens/stream/StreamInfoChips.kt` **(yeni)** | **Tek** rozet bileşeni: kalite, ses türü, boyut (gerçek/ölçülen/bilinmiyor), seeder (renkli), peer, ses dilleri, altyazı dilleri, codec, HDR, kaynak türü, önbellek/P2P. Ayrıca `StreamGroupSummary` (toplam bilinen boyut, en yüksek seeder). |
| `ui/screens/stream/StreamCard.kt` | Karttaki 60 satırlık kopya rozet bloğu → tek bileşen. |
| `ui/screens/stream/StreamScreenContent.kt` | 3 kademeli sıralama (Varsayılan → Kalite → Tohum), "x/y boyut biliniyor" özeti, toplam bilinen boyut, `İndirme boyutu` filtresi, aramada boyut/dosya adı eşleşmesi. |
| `ui/screens/stream/StreamViewModel.kt` | İkinci Stremio eşlemesi → `toStreamSource()`. |
| `ui/tv/stream/TvStreamScreen.kt` | TV satırında yalnızca eklenti adı + boyut vardı → tüm rozetler. |
| `ui/components/KitsugiStreamSelectorBottomSheet.kt` | Detay sayfası kaynak seçici: `StreamInfoChips` + boyut bilinmiyorsa açık "Boyut bilinmiyor". |
| `ui/screens/fullscreen/controls/components/sheets/QualitySheet.kt` | **Oynatıcı içi "Kalite / Kaynak"** sayfasına boyut + seeder/peer + dil/codec rozetleri (önbellekten, ek ağ isteği yok). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 13 yeni metin (TR + EN): `stream_badge_seeders`, `stream_size_unknown`, `stream_sort_seeders`, `stream_filter_download_size`, `stream_size_coverage`, … |

### 3.3 Test

| Dosya | Kapsam |
|-------|--------|
| `app/src/test/.../StreamBadgesTest.kt` **(yeni)** | Swarm ayrıştırma (6 test), boyut önceliği (4), dil/altyazı rozetleri (5), codec/HDR (3), sıralama (2). "1080p seeder sanılmaz", "sayı yoksa rozet yok" dahil. |
| `app/src/test/.../StremioStreamMappingTest.kt` **(yeni)** | `toStreamSource` sözleşmesi: `behaviorHints.videoSize/filename`, JSON seed alanının metinden önceliği, bilgi yoksa alanların `null` kalması. |

---

## 4. Davranış kuralları (değişmez kabul)

1. **Seed/peer:** yalnızca kaynak sayı verdiyse. `0 seeder` gibi uydurma değer yok.
2. **Boyut:** ölçülen bayt > dosya adı/başlık etiketi > **bilinmiyor** ("Boyut bilinmiyor").
   HLS'te toplam boyut playlist'te olmadığı için tahmin edilmez.
3. **Altyazı:** ya eklentinin gerçek altyazı dosyası listesi ya HLS `#EXT-X-MEDIA` ölçümü.
   "Türkçe eklenti ⇒ altyazılı" varsayımı **geri getirilmedi**.
4. **Çift dil:** eklentinin verdiği ayrı ses parçası (video sesi + harici parça) veya HLS'te
   birden fazla ses dili.
5. **Performans:** görüş alanı dışındaki kartlar ölçüm yapmaz; HLS'te boyut için ek istek yok.

---

## 5. Doğrulama durumu ve kalan iş

- [x] Import/API taraması (Compose Material3, `Icons.Rounded.*`, `collectAsStateWithLifecycle`)
- [x] Kotlin dil sürümü uyumu (2.0.21 — `enum.entries`, `data object` kullanılmadı)
- [ ] **`./gradlew :app:assembleDebug`** → bu sanal ortamda JDK/Android SDK **yok**, çalıştırılamadı
- [ ] **`./gradlew :app:testDebugUnitTest`** → aynı nedenle çalıştırılamadı
- [ ] Cihazda gözle kontrol: Torrentio kaynaklarında `↑124 ↓12 · 1.72 GB`, FilmMakinesi/HDFilmCehennemi
      kaynaklarında `CC EN/FR/PT/ES · Çift dil`

### Uygulama adımları (yerelde)
1. ZIP içindeki `02_DEGISEN_DOSYALAR/` klasörünü depo köküne kopyala (ya da `03_PATCH/degisiklikler.patch`
   dosyasını `git apply` ile uygula).
2. `./gradlew :app:testDebugUnitTest` → yeşil olmalı.
3. `./gradlew :app:assembleDebug` (+ istersen `assembleRelease`).

---

## 6. Riskler / geri alma

| Risk | Etki | Önlem |
|------|------|-------|
| Yanlış seed ayrıştırma (ör. `1080p` → 1080 seeder) | Az | Testlerle korunuyor (`1080p gibi degerler seeder sanilmaz`) |
| HEAD ölçümünün artması (progressive dosyalar) | Düşük | Yalnızca boyut bilinmiyorsa, görüş alanındayken, 2 eşzamanlı izin |
| `StreamSource` yeni alanları | Yok | `Serializable` + varsayılan `null`/`NONE`; eski JSON kayıtları okunabilir |
| Geri alma | — | `git revert 8b377dd 700609a 63694de` |
