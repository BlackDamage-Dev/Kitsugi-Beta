# Kitsugi Beta v2.4.442

## 🇹🇷 Türkçe

### 🎬 Oynatıcı "Donanım Kod Çözücü Modu" Düzenlemesi (Tek Auto + SW/HW/HW+ Çift Motor)
1. **Tek "Auto" Butonu:**
   - Oynatıcı "Daha Fazla" panelindeki birden fazla "Auto" seçeneğine yol açan mükerrer kod çözücü tanımları (`AutoCopy`, `AutoSafe`) `Decoder` enum'ından kaldırıldı.
   - Panelde artık net ve sade olarak 4 seçenek yer alıyor: **Auto**, **SW**, **HW**, **HW+**.
   - Eski kayıtlı değerler ("auto-copy", "auto-safe") otomatik olarak varsayılan **Auto** moduna eşlenir; geriye dönük uyumluluk tamdır.

2. **SW / HW / HW+ Seçimi Artık Her İki Motorda da (MPV + Media3) Aktif:**
   - **MPV Motoru:** `hwdec` özelliği doğrudan uygulanmaya devam eder (`auto`, `no` [SW], `mediacodec-copy` [HW], `mediacodec` [HW+]).
   - **Media3 Motoru:** `Media3PlayerEngine` içerisine özel `MediaCodecSelector` entegre edildi:
     - **SW (`no`):** Yalnızca yazılım çözücüler (`OMX.google.*` / `c2.android.*`).
     - **HW/HW+:** Donanım çözücüler öncelikli.
     - **Güvenli Geri Dönüş:** Çözücü bulunamazsa otomatik varsayılana düşer, siyah ekran riski oluşmaz.
     - **Kesintisiz Geçiş:** Kod çözücü modu değiştirildiğinde oynatıcı kaldığı konumu ve tüm akış parametrelerini (başlıklar, altyazılar, harici sesler) koruyarak ExoPlayer'ı anında yeniden başlatır.
     - **Dolby Vision Uyumu:** Profil-bağımsız Dolby Vision yedeği ile tam uyumlu çalışır.

3. **Kalite & Doğrulama:**
   - `PlayerDecoderModeTest` ile 5 test senaryosu %100 doğrulandı.
   - Pager performans denetimi temiz; `androidx.compose.material.icons` bağımlılığı sıfır.

---

## 🇬🇧 English

### 🎬 Player "Hardware Decoder Mode" Refinement (Single Auto + SW/HW/HW+ Dual Engine)
1. **Unified "Auto" Option:**
   - Eliminated redundant "Auto" entries (`AutoCopy`, `AutoSafe`) from the `Decoder` enum that resulted in multiple duplicate chips in the player "More" bottom sheet.
   - The panel now displays four distinct options: **Auto**, **SW**, **HW**, **HW+**.
   - Legacy stored values ("auto-copy", "auto-safe") gracefully fall back to **Auto**.

2. **SW / HW / HW+ Support Across Both Engines (MPV + Media3):**
   - **MPV Engine:** Continues instant application via the `hwdec` property (`auto`, `no`, `mediacodec-copy`, `mediacodec`).
   - **Media3 Engine:** Added custom `MediaCodecSelector` support in `Media3PlayerEngine`:
     - **SW (`no`):** Strictly uses software decoders (`OMX.google.*` / `c2.android.*`).
     - **HW/HW+:** Prioritizes hardware decoders.
     - **Safe Fallback:** Reverts to default decoders if none match, preventing black screens.
     - **Seamless Position Resume:** Changing decoder mode safely restarts ExoPlayer with identical playback parameters (subtitles, audio, headers) and resumes from the exact millisecond.
     - **Dolby Vision Preserved:** Integrates alongside profile-agnostic Dolby Vision fallback seamlessly.

3. **Verification:**
   - 100% passing tests in `PlayerDecoderModeTest`.
   - Zero `androidx.compose.material.icons` imports and passed pager performance checks.
