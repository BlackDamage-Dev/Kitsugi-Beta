# PLAN TASK — Dolby Vision Yeşil/Mor Renk Bozulması (DV AutoFix)

**Tarih:** 2026-10-25
**Durum:** ✅ Tamamlandı (kod + testler + rapor)
**Commit:** `291ffad` — `fix(player): Dolby Vision yeşil/mor renk bozulması — NuvioTV çözümü uyarlandı`
**Dal:** `arena/c7647b7f-kitsugi-beta`

**Kullanıcı isteği:** "Torrentio/Stremio kaynaklı `4K DV` işaretli videolar oynuyor ama renkler aşırı
yeşilimsi/tuhaf. Kapsamlı araştır, ek kod/özellik gerekiyorsa ve sorun Nuvio'da çözülmüşse açık kaynak
Nuvio kodlarından mantığı alıp uygulamaya entegre et."

**Kabul kriterleri**

| # | Kriter | Durum |
|---|---|---|
| K1 | DV içerikte renklerin doğru çıkması (yeşil/mor artefaktının kalkması) | ✅ (MPV `vo=gpu-next` + P5'te `hwdec=no`; cihaz testi CI/cihazda) |
| K2 | NuvioTV ile parite | ✅ (DV5 SEI kararı, tip-63 EL, stale config, gizli DV çözücü, 8.1 signal-only) |
| K3 | Mevcut davranışların/ayarların bozulmaması | ✅ (yeni ayar varsayılan açık ama kapatılabilir; DV yoksa hiçbir seçenek değişmiyor; 29 kontrol PASS) |

---

## 1. Hedefler

| # | Hedef | Durum |
|---|-------|-------|
| H1 | Kök neden analizi (MPV vo/hwdec, P5 RPU sıyırma, MKV mime düşürme, P7 EL, ölü codec fallback, stale config) | ✅ |
| H2 | NuvioTV açık kaynak kodunun incelenmesi ve mantığın uyarlanması | ✅ |
| H3 | MPV motoru: DV ipucu → `vo=gpu-next`, P5/bilinmeyen profilde `hwdec=no` | ✅ |
| H4 | DV5 koruması (RPU sıyırma/mime düşürmenin profile duyarlı hâle getirilmesi) | ✅ |
| H5 | DV5 → 8.1 "signal-only" dönüşüm altyapısı | ✅ (yapı hazır; gerçek dönüşüm libdovi stub) |
| H6 | P7 tek-iz remux EL (NAL tip 63) düşürme | ✅ |
| H7 | Gizli DV çözücü keşfinin (`DolbyVisionCodecFallback`) bağlanması | ✅ |
| H8 | Bayat MKV DV konfigürasyonunun reddi | ✅ |
| H9 | Kullanıcı ayarı: "Dolby Vision Otomatik Renk Düzeltmesi" (varsayılan açık) | ✅ |
| H10 | JUnit + üretim kodu politika kontrolleri, dokümantasyon, rapor | ✅ |
| H11 | Değişen dosyalar + plan task dosyasının ziple Drive'a yüklenmesi | ✅ |

---

## 2. Kök Nedenler (özet)

1. **Varsayılan MPV çıkışı `vo=gpu`** — DV RPU'sunu uygulamaz; `gpu-next` (libplacebo) uygular.
   Kitsugi'de DV için hiçbir otomatik vo/hwdec müdahalesi yoktu.
2. **MP4/TS yolunda P5 RPU'su koşulsuz sıyrılıyor** ve codec dizesi `hvc1`'e düşürülüyordu.
3. **MKV'de "HDR10 base layer" modu P5 mime'ını `video/hevc` yapıyordu** (IPTPQc2 pikselleri → yeşil).
4. **P7 tek-iz remux'larında EL (NAL tip 63)** `layerId > 0` koşulu yüzünden düşürülmüyordu.
5. **`DolbyVisionCodecFallback` ölü koddu** — gizli DV çözücüleri hiç keşfedilmiyordu.
6. **Bayat MKV DV config** (renk transferi SDR) DV sayılıyordu.
7. **P5 cihazda çözülemiyorsa** hiçbir geri dönüş yolu yoktu.

---

## 3. Uygulama Planı ve Yapılanlar (faz faz)

### Faz 1 — Araştırma
- [x] NuvioTV (`NuvioMedia/NuvioTV`, dev) DV dosyalarının ağaç çıkarımı ve indirilmesi
  (`DolbyVisionExtractorsFactory.kt`, `DoviBridge.kt`, `dovi_bridge.cpp`,
  `DolbyVisionMatroskaTransformer.kt`, `HevcDvRpuStripper.kt`, `MatroskaExtractor.java`,
  `DolbyVisionCompatibility.java`, `PlayerRuntimeController*`, `PlaybackVideoSettings.kt`).
- [x] Nuvio Wiki (troubleshooting / player) ve `quietvoid/dovi_tool` README incelendi.
- [x] mpv/libplacebo davranışı: `gpu` DV uygulamaz, `gpu-next` uygular; MediaCodec RPU taşımaz.
- [x] Kitsugi ↔ Nuvio diff'lerinin çıkarılması ve yedi kök nedenin doğrulanması.

### Faz 2 — MPV motoru (KÖK NEDEN 1)
- [x] `DolbyVisionStreamHints.kt` (yeni): başlık/ad/URL'den DV + profil (5/7/8) ipucu;
      `DVD`, `DVDRip`, `HDR10+` yanlış pozitifleri ayıklanır.
- [x] `MpvDolbyVisionPolicy.kt` (yeni): saf Kotlin politika; DV → `vo=gpu-next`,
      P5/bilinmeyen profilde ek `hwdec=no`; `gpu-next` yoksa müdahale yok + OSD uyarısı.
- [x] `MpvPlayerEngine.kt`: `prepare()`'da ipucu, akış başına plan (`appliedDvPlanKey` ile
      idempotent), `probeGpuNextSupport()` (tek sefer), `syncDolbyVisionOptions()`.
      DV → normal içerik geçişinde kullanıcının kendi vo/hwdec değerleri geri yüklenir.
- [x] Ayar: `mpvDolbyVisionAutoFix` (varsayılan `true`) — `AppSettings`, `SettingsDataStore`,
      `KitsugiPlayerSettingsDialog`, TR/EN string'ler.

### Faz 3 — DV5 koruması (KÖK NEDEN 2, 3, 7)
- [x] `HevcDvRpuStripper.kt`: `containsHdr10StaticMetadataSei()` ve `...AnnexB()`
      (SEI 137 mastering display / 144 content light level).
- [x] `DolbyVisionMatroskaTransformer.kt`: P5 RPU'su yalnızca taban katman HDR10 statik
      metadata taşıyorsa sıyrılır (`DV5_STRIP_DECISION` logu); tip-63 EL düşürme;
      BlockAdditional RPU dönüşümü tek noktada.
- [x] `DolbyVisionExtractorsFactory.kt`: `DV5_PROTECT` (P5'te sıyırma/mime düşürme yok),
      `stripThisTrack` sınıf alanı, `dv5ToDv81SignalOnly` (RPU korunarak yalnızca sinyal 8.1).
- [x] `dvmkv/MatroskaExtractor.java`: P5 izleri HDR10 base-layer modunda `video/hevc`'ye düşmez;
      bayat DV config DV sayılmaz.
- [x] `dvmkv/DolbyVisionCompatibility.java`: `isDolbyVisionProfile5()`,
      `isStaleContainerDolbyVisionConfig()`.

### Faz 4 — P7 remux (KÖK NEDEN 4)
- [x] `app/src/main/cpp/dovi_bridge.cpp`: iki NAL tarama yolunda
      `bool isEl = (layerId > 0) || (nalType == 63);`

### Faz 5 — Codec keşfi (KÖK NEDEN 5)
- [x] `Media3PlayerEngine.kt`: `DefaultRenderersFactory.setMediaCodecSelector`,
      `dv81CodecFallbackActive` yalnızca gerçek dönüştürme oturumunda etkin,
      `DolbyVisionCodecFallback.findDvDecodersIgnoringProfile()` ile gizli DV çözücü keşfi.

### Faz 6 — Doğrulama ve teslim
- [x] JUnit: `DolbyVisionStreamHintsTest` (11), `MpvDolbyVisionPolicyTest` (10),
      `HevcDvRpuStripperSeiTest` (8).
- [x] `scripts/run_player_policy_checks.sh` + `scripts/tests/PlayerPolicyChecks.kt` DV kontrolleri
      → **29 PASS**.
- [x] `docs/PLAYER_ARCHITECTURE.md` DV tablosu ve ayar→motor haritası güncellendi.
- [x] `COZUM_RAPORU_DOLBY_VISION_YESIL_RENK_2026-10-25.md` yazıldı.
- [x] Commit + push (`arena/c7647b7f-kitsugi-beta`).
- [x] Değişen dosyalar + bu plan task dosyası ziple Drive'a yüklendi.

---

## 4. Değişen Dosyalar (22)

**Yeni (5)**
1. `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionStreamHints.kt`
2. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvDolbyVisionPolicy.kt`
3. `app/src/test/java/com/kitsugi/animelist/core/player/DolbyVisionStreamHintsTest.kt`
4. `app/src/test/java/com/kitsugi/animelist/core/player/HevcDvRpuStripperSeiTest.kt`
5. `app/src/test/java/com/kitsugi/animelist/core/player/engine/MpvDolbyVisionPolicyTest.kt`

**Değişen (15)**
6. `app/src/main/cpp/dovi_bridge.cpp`
7. `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionExtractorsFactory.kt`
8. `app/src/main/java/com/kitsugi/animelist/core/player/DolbyVisionMatroskaTransformer.kt`
9. `app/src/main/java/com/kitsugi/animelist/core/player/HevcDvRpuStripper.kt`
10. `app/src/main/java/com/kitsugi/animelist/core/player/dvmkv/DolbyVisionCompatibility.java`
11. `app/src/main/java/com/kitsugi/animelist/core/player/dvmkv/MatroskaExtractor.java`
12. `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
13. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
14. `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt`
15. `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt`
16. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`
17. `app/src/main/res/values/strings.xml`
18. `app/src/main/res/values-en/strings.xml`
19. `scripts/run_player_policy_checks.sh`
20. `scripts/tests/PlayerPolicyChecks.kt`
21. `docs/PLAYER_ARCHITECTURE.md`
22. `COZUM_RAPORU_DOLBY_VISION_YESIL_RENK_2026-10-25.md`

---

## 5. Kullanıcı Tarafı (aynı sorun tekrar görülürse)

1. **Ayarlar → Oynatıcı → MPV** → "Dolby Vision Otomatik Renk Düzeltmesi" **açık** (varsayılan).
2. Kalıcı çözüm için GPU Renderer = **`gpu-next`**.
3. Renk hâlâ bozuksa HW Decode = **`no`** (P5 için şart; 4K'da CPU gücü gerekir).
4. ExoPlayer'da DV7 İşleme Modu = **"HDR10 Base Layer"** (P5 artık bu modda da korunuyor).

---

## 6. Riskler / Sınırlar

- `gpu-next` libmpv derlemesinde yoksa müdahale edilmez, tek seferlik OSD uyarısı gösterilir.
- P5'te `hwdec=no` 4K HEVC'yi yazılımda çözer → zayıf cihazlarda takılma olabilir; ayar kapatılabilir.
- libdovi hâlâ **stub** (`DOVI_ENABLE_LIBDOVI=OFF`) → gerçek P7→8.1 dönüştürme cihazda çalışmaz;
  DV5/P8/P7-HDR10 yolları etkilenmez. Gerçek dönüşüm için `DV7/libdovi` ön-derlemeleri eklenmeli.
- Android SDK/JDK bu ortamda yok → tam APK derlemesi CI'da yapılacak; burada modül bazlı
  Kotlin derlemesi + politika kontrolleri koşuldu.
