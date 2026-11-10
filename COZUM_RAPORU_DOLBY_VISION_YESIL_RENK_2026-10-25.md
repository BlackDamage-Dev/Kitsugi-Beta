# Dolby Vision Yeşil/Mor Renk Bozulması — Kök Neden ve Çözüm Raporu

**Tarih:** 2026-10-25
**Konu:** Torrentio / Stremio kaynaklı `4K DV` akışlarının yeşilimsi (bazen mor) renklerle oynaması
**Durum:** ✅ Kök nedenler tespit edildi, NuvioTV açık kaynak çözümü uyarlandı ve entegre edildi

---

## 1. Belirti

Ekran görüntüsündeki kaynaklar: `Torrentio · 4K DV | HDR10+`, `2160p DSNP WEB-DL DDP5.1 Atmos DV`.
Videolar **oynuyor**, ses geliyor, ilerleme çalışıyor; ancak görüntü **aşırı yeşilimsi** (klasik
"yeşil/mor" Dolby Vision artefaktı). Bu, "dosya bozuk" değil; **DV sinyalinin yanlış boru hattından
geçmesi** sonucudur.

---

## 2. Teknik arka plan — renk neden bozulur?

| Profil | Yapı | HDR10 yedeği | Yanlış oynatılırsa |
|---|---|---|---|
| **P5** (`dvhe.05`, `dvh1.05`) | Tek katman, pikseller **IPTPQc2** renk uzayında | **YOK** | IPT pikselleri YCbCr sanılır → **yeşil/mor** |
| **P7** (`dvhe.07`, UHD-BD remux) | BL + EL (FEL/MEL) | Var | EL beslenirse kare bozulur; RPU dönüştürülmezse renk kayar |
| **P8.x** (`dvhe.08`) | Tek katman, BT.2020/PQ | Var (HDR10) | Genelde sorunsuz; cihaz P8 çözemezse HDR10'a düşer |

Kritik nokta: **P5'in pikselleri tek başına anlamlı değildir.** RPU'daki yeniden şekillendirme
(reshaping) uygulanmadan hiçbir "codec dizesi oyunu" rengi düzeltmez. Çözüm ya
(a) RPU'yu uygulayan bir renderer (`vo=gpu-next` / libplacebo) ya da
(b) DV donanım çözücüsünün kendisidir.

---

## 3. Kitsugi'de tespit edilen kök nedenler

### KÖK NEDEN 1 — Varsayılan MPV çıkışı `vo=gpu` (en olası sebep)
`AppSettings.playerPreference` varsayılanı **MPV**, `mpvGpuRenderer` varsayılanı **`gpu`**
(`AppSettings.kt:85`, `AppSettings.kt:161`). mpv'nin eski `gpu` çıkışı **DV RPU'sunu hiç uygulamaz**;
`gpu-next` (libplacebo) uygular. Ayrıca MediaCodec donanım çözücüsü RPU'yu kareye eklemez; bu yüzden
`gpu-next` bile `hwdec` açıkken P5'i düzeltemez (mpv-android upstream: "gpu-next + hwdec=no" şartı).

➡️ Kitsugi'de DV içerik için **hiçbir otomatik vo/hwdec düzeltmesi yoktu**.

### KÖK NEDEN 2 — "HDR10 base layer" modunda P5 körlemesine sıyrılıyordu
`DolbyVisionExtractorsFactory.NativeOptimizedVideoTrackOutput.format()` (MP4/TS yolu) `stripDvRpu`
açıkken **profil ayrımı yapmadan** RPU'yu siliyor ve codec dizesini `hvc1...`'e indiriyordu.
P5'te bu, görüntüyü garantili olarak yeşile çevirir. MKV yolu P5'i koruyordu, MP4/TS korumuyordu →
tutarsız davranış.

### KÖK NEDEN 3 — MKV'de P5 mime'ı HEVC'ye düşürülüyordu
`MatroskaExtractor.initializeFormat()` içinde `isHdr10BaseLayerModeActive()` **tüm** DV izlerini
`video/hevc` yapıyordu. P5 için bu, IPTPQc2 piksellerini HEVC gibi çözmek demektir → yeşil.

### KÖK NEDEN 4 — P7 tek-iz remux'larında EL (NAL tip 63) atılmıyordu
`dovi_bridge.cpp` yalnızca `layerId > 0` olan NAL'ları EL sayıyordu. Tek izli P7 remux'larda EL,
**layer 0'daki tip-63 NAL'larında** taşınır. Bunlar temel katman çözücüsüne gidince renkli/bozuk
kareler oluşur. (NuvioTV bu düzeltmeyi `dovi_bridge.cpp`'ye eklemiş.)

### KÖK NEDEN 5 — Gizli DV çözücüleri hiç keşfedilmiyordu
`DolbyVisionCodecFallback` sınıfı projede vardı ama **hiçbir yere bağlı değildi** (ölü kod).
Bazı Amlogic/MTK cihazlar `video/dolby-vision` çözücüsünü yalnızca P7 olarak bildirir; libdovi 8.1'e
çevirdiğinde ExoPlayer'ın standart seçicisi çözücüyü reddeder → HDR10'a düşer ya da hata verir.

### KÖK NEDEN 6 — Bayat MKV DV konfigürasyonu
Bazı remux'larda `BlockAdditionMapping` hâlâ "Dolby Vision" der, oysa izin renk metadatası SDR'dir.
Kitsugi bunu DV sayıp `video/dolby-vision` yoluna sokuyordu → ya renk bozulması ya hiç oynamama.

### KÖK NEDEN 7 (tasarım boşluğu) — Cihaz P8 çözüp P5 çözemiyorsa hiçbir şey yapılmıyordu
NuvioTV'nin "Convert DV5 to DV8.1" özelliğinin otomatik karşılığı Kitsugi'de yoktu
(`dv5Enabled = false` iki yerde sabit kodlanmış).

---

## 4. Yapılan değişiklikler (NuvioTV uyarlaması)

### 4.1 MPV motoru — DV otomatik renk düzeltmesi (KÖK NEDEN 1)
| Dosya | Değişiklik |
|---|---|
| `core/player/DolbyVisionStreamHints.kt` **(yeni)** | Akış başlığı/eklenti adı/dosya adından DV ve profil (5/7/8) ipucu çıkarır. `DVD`, `DVDRip`, `HDR10+` yanlış pozitifleri ayıklanır. |
| `core/player/engine/MpvDolbyVisionPolicy.kt` **(yeni)** | Android/JNI'den bağımsız politika: DV ise `vo=gpu-next`; **P5 veya profili bilinmeyen DV** ise ek olarak `hwdec=no` (RPU'nun kareye eklenmesi için tek yol); P7/P8'de donanım çözme korunur (4K'da gereksiz yazılım çözme = takılma). `gpu-next` derlemede yoksa **hiçbir şey değiştirilmez** ve OSD ile uyarılır. |
| `core/player/engine/MpvPlayerEngine.kt` | `prepare()`'da ipucu çıkarılır; `syncDolbyVisionOptions()` planı uygular. Plan akış başına yeniden hesaplandığı için **DV → normal içerik geçişinde kullanıcının kendi vo/hwdec ayarına dönülür**. `gpu-next` desteği bir kez yoklanır (`set` + geri okuma + eski değeri geri yazma). |
| `data/settings/AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiPlayerSettingsDialog.kt`, `res/values(-en)/strings.xml` | **"Dolby Vision Otomatik Renk Düzeltmesi"** anahtarı (varsayılan **açık**) — MPV bölümünde, GPU Renderer seçiminin altında. Kapatılırsa eski davranış aynen korunur. |

### 4.2 DV5 koruması (KÖK NEDEN 2, 3, 7)
| Dosya | Değişiklik |
|---|---|
| `HevcDvRpuStripper.kt` | `containsHdr10StaticMetadataSei()` (length-delimited) ve `containsHdr10StaticMetadataSeiAnnexB()` eklendi. Mastering Display (SEI 137) / Content Light Level (144) zinciri taranır. |
| `DolbyVisionMatroskaTransformer.kt` | NuvioTV mantığı: P5 RPU'su **yalnızca** taban katman kendi HDR10 statik metadatasını taşıyorsa sıyrılır (`DV5_STRIP_DECISION` logu, 15 örnek tarama penceresi). Ayrıca P7 tip-63 EL NAL'ları düşürülür, BlockAdditional RPU'su tek noktada dönüştürülür. |
| `DolbyVisionExtractorsFactory.kt` | **DV5 koruması**: `stripDvRpu` açıkken bile `profile == 5` ise RPU sıyrılmaz ve codec dizesi `hvc1`'e indirilmez (`DV5_PROTECT` logu). Yeni `dv5ToDv81SignalOnly` bayrağı: cihaz P5 çözemiyor ama P8.1 çözebiliyorsa **yalnızca sinyal** 8.1'e çevrilir (RPU korunur). |
| `dvmkv/MatroskaExtractor.java` | P5 izleri HDR10 base-layer modunda artık `video/hevc`'ye düşürülmez. Bayat DV konfigürasyonu (renk metadatası SDR) DV sayılmaz. |
| `dvmkv/DolbyVisionCompatibility.java` | `isDolbyVisionProfile5()`, `isStaleContainerDolbyVisionConfig()` eklendi. |

### 4.3 P7 remux düzeltmesi (KÖK NEDEN 4)
`app/src/main/cpp/dovi_bridge.cpp` — iki NAL tarama yolunda da:
```cpp
bool isEl = (layerId > 0) || (nalType == 63); // tip 63 = P7 EL (tek-iz remux)
```

### 4.4 Gizli DV çözücü keşfi (KÖK NEDEN 5)
`Media3PlayerEngine.initializePlayer()` → `DefaultRenderersFactory.setMediaCodecSelector { ... }`:
standart seçici `video/dolby-vision` için **boş** dönerse ve dönüştürme aktifse
`DolbyVisionCodecFallback.findDvDecodersIgnoringProfile()` devreye girer. Bayrak
`dv81CodecFallbackActive` yalnızca gerçek dönüştürme oturumunda `true` olur (sıfır ek yük).

---

## 5. NuvioTV'den alınan mantık (atıf)

Açık kaynak (GPL-3.0) `NuvioMedia/NuvioTV` deposundan uyarlanan fikirler:

| NuvioTV dosyası | Kitsugi'ye uyarlanan |
|---|---|
| `core/player/dovi_bridge.cpp` (tip-63 EL düşürme) | `app/src/main/cpp/dovi_bridge.cpp` |
| `core/player/HevcDvRpuStripper.kt` (`containsHdr10StaticMetadataSei`) | `HevcDvRpuStripper.kt` |
| `core/player/DolbyVisionMatroskaTransformer.kt` (DV5 SEI kararı, tek dönüşüm noktası) | `DolbyVisionMatroskaTransformer.kt` |
| `core/player/dvmkv/DolbyVisionCompatibility.java` (bayat config, P5 yardımcısı) | `dvmkv/DolbyVisionCompatibility.java` |
| `core/player/dvmkv/MatroskaExtractor.java` (stale DV config) | `dvmkv/MatroskaExtractor.java` |
| DV5 → 8.1 "signal-only" davranışı + dokümanı | `DolbyVisionConversionConfig.dv5ToDv81SignalOnly` |
| Gizli DV çözücü seçicisi (`createDolbyVisionFallbackCodecSelector`) | `Media3PlayerEngine` içindeki `setMediaCodecSelector` yaması |

> NuvioTV'nin `gpu-next` + `hwdec` davranışı Kitsugi'nin MPV motoruna **yeni** olarak eklendi;
> Kitsugi'de daha önce DV için hiçbir vo/hwdec müdahalesi yoktu.

---

## 6. Kullanıcı için kısa yol (aynı sorunu tekrar görürsen)

1. **Ayarlar → Oynatıcı → MPV** bölümü:
   - "Dolby Vision Otomatik Renk Düzeltmesi" **açık** olsun (varsayılan).
   - İstersen GPU Renderer'ı elle **`gpu-next`** yap (kalıcı çözüm).
2. Renk hâlâ bozuksa: **HW Decode → `no`** (P5 için şart; 4K'da işlemci gücü gerekir).
3. ExoPlayer'da ısrar edilecekse: **DV7 İşleme Modu → "HDR10 Base Layer"** (P7 için doğru,
   P5 artık bu modda bile korunuyor).
4. Kesin çözüm her zaman DV'yi (P5) destekleyen donanım/DV TV'dir; P5 yazılımla "dönüştürülerek"
   renkleri düzeltilemez — sadece RPU'yu uygulayan bir renderer (libplacebo) düzeltir.

---

## 7. Testler ve doğrulama

Eklenen JUnit testleri (`:app:test*UnitTest` hattında koşar):

| Test | Kapsam |
|---|---|
| `DolbyVisionStreamHintsTest` | 11 durum: `DV`, `Dolby Vision`, `DoVi`, `dvh1.05`, `dvhe.07/08`, `DV5`, `DVD/DVDRip` yanlış pozitifi, `HDR10+` sadece, boş/null |
| `MpvDolbyVisionPolicyTest` | 10 durum: DV değil / kapalı / gpu-next yok / P5 / bilinmeyen profil / P7 / P8 / zaten gpu-next / zaten hwdec=no / plan anahtarı |
| `HevcDvRpuStripperSeiTest` | 8 durum: length-delimited + Annex-B'de SEI 137/144 tespiti, HDR10+ SEI 4 reddi, bozuk uzunluk alanı, boş örnek |

Ayrıca depodaki **gerçek üretim kodu** doğrulama hattı genişletildi:

- `scripts/run_player_policy_checks.sh` artık `DolbyVisionStreamHints.kt` ve
  `MpvDolbyVisionPolicy.kt` dosyalarını derleyip `scripts/tests/PlayerPolicyChecks.kt` içindeki
  DV kontrollerini koşuyor → **29 kontrol PASS**.
- Aynı iki modül + `HevcDvRpuStripper` bağımsız Kotlin derleyicisiyle derlenip sahte NAL'lerle
  koşturuldu: ipucu/politika için **38/38**, SEI taraması için **12/12** doğrulama geçti
  (zincirlenmiş SEI mesajları ve "RPU NAL'i SEI sanılmamalı" durumu dahil).
- Yerelleştirme paritesi: `scripts/check_localization_resource_parity.py` →
  **17.698 string senkron** (yeni TR/EN anahtarlar dahil).

> Not: Android SDK/JDK bu ortamda bulunmadığı için tam APK derlemesi burada yapılamadı;
> derleme CI'daki Android Derleme Doğrulama hattında yapılacak.

---

## 8. Sınırlar / riskler

- `gpu-next` **libmpv derlemesinde yoksa** hiçbir şey zorlanmaz; kullanıcıya tek seferlik OSD
  uyarısı gösterilir (yeşil görüntü devam eder — yazılımsal çözüm mümkün değildir).
- P5 için `hwdec=no` 4K HEVC'yi yazılımda çözer: zayıf cihazlarda takılma olabilir. Bu yüzden
  ayar kapatılabilir; P7/P8'de donanım çözme korunur.
- Aktif DV dönüştürme (libdovi) yolu Kitsugi'de hâlâ **stub** modda derleniyor
  (`DOVI_ENABLE_LIBDOVI=OFF`). Bu, P7→8.1 dönüştürmenin cihazda çalışmadığı anlamına gelir;
  DV5/P8/P7-HDR10 yolları bundan etkilenmez. Gerçek libdovi için `DV7/libdovi` ön-derlemeleri
  eklenip `-DDOVI_ENABLE_LIBDOVI=ON` verilmelidir (NuvioTV aynı CMake yapısını kullanıyor).
