# Kitsugi Beta v2.4.373

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Dolby Vision Yeşil / Mor Renk Bozulması Giderildi (NuvioTV Açık Kaynak Uyarlaması)
- **MPV Dinamik Boru Hattı (`MpvDolbyVisionPolicy` & `MpvPlayerEngine`):** MPV motorunda varsayılan `vo=gpu` çıkışı Dolby Vision RPU'sunu işlemediğinden ve donanım MediaCodec RPU'yu kareye eklemediğinden Profil 5 içerikler yeşil/mor çıkıyordu. Artık DV içerik algılandığında otomatik olarak `vo=gpu-next` (libplacebo) devreye sokulur; Profil 5 ve bilinmeyen profillerde yazılım çözme (`hwdec=no`) uygulanır. Akış bittiğinde veya standart videoya geçildiğinde kullanıcının kendi yapılandırdığı `vo` ve `hwdec` değerlerine geri dönülür.
- **Dolby Vision Başlık & Akış Algılama (`DolbyVisionStreamHints`):** Akış başlığı, eklenti adı ve URL üzerinden DV ve profil bilgisi (P5, P7, P8) akıllıca tespit edilir; DVD veya HDR10+ yanlış pozitifleri ayıklanır.
- **Ayarlar Anahtarı:** Ayarlar → MPV sekmesine "Dolby Vision Otomatik Renk Düzeltmesi" (`mpv_dolby_vision_auto_fix`) seçeneği eklendi (varsayılan açık).
- **MP4 / TS Profil 5 RPU Koruması (`DolbyVisionExtractorsFactory`):** Profil 5 pikselleri IPTPQc2 renk uzayında olduğu için RPU sıyrıldığında renkler bozuluyordu. HDR10 taban katmanı modunda bile Profil 5 RPU'su korunarak yeşil renk bozulması önlendi.
- **MKV Profil 5 MIME Koruması (`MatroskaExtractor`):** Matroska ayrıştırıcısında HDR10 modunda Profil 5 MIME tipinin `video/hevc`'ye düşürülmesi engellendi.
- **Profil 7 Tek-İz Remux Desteği (`dovi_bridge.cpp` & `DolbyVisionMatroskaTransformer`):** Tek izli P7 remux'larda çözücüye beslenen ve renk/kare bozulmasına yol açan NAL tip 63 (Enhancement Layer) paketleri taban katmana beslenmeden önce filtrelendi.
- **Gizli DV Çözücü Keşfi (`Media3PlayerEngine` & `DolbyVisionCodecFallback`):** Bazı yonga setlerinde (Amlogic, MediaTek vb.) profil kısıtlaması nedeniyle standart seçicinin bulamadığı gizli Dolby Vision çözücüleri `DolbyVisionCodecFallback` ile bağlanarak aktif hale getirildi.
- **Profil 5 → 8.1 Sinyal Düzeltmesi:** Cihazın DV çözücüsü Profil 5'i çözemiyor ancak Profil 8.1'i çözebiliyorsa (dvhe.st var, dvhe.stn yok), donanım IPTPQc2 dönüşümünü yapabilsin diye sinyal bazında kod dizesi 8.1'e uyarlanır.
- **Bayat MKV Konfigürasyonu Koruması (`DolbyVisionCompatibility`):** Renk metadatası SDR olan bayat Matroska konfigürasyonlarının yanlışlıkla DV olarak işlenmesi engellendi.

---

## 🇬🇧 English Release Notes

### 1. Dolby Vision Green / Purple Color Cast Resolution (NuvioTV Port)
- **Dynamic MPV Pipeline (`MpvDolbyVisionPolicy` & `MpvPlayerEngine`):** The default MPV `vo=gpu` output cannot apply Dolby Vision RPU metadata, and hardware decoders do not pass RPU to frames, causing green/purple tints on Profile 5 streams. The player now automatically detects DV streams, switches to `vo=gpu-next` (libplacebo), and enables software decoding (`hwdec=no`) for Profile 5 and unknown profiles. Settings revert cleanly to the user's custom `vo` and `hwdec` options once playback ends.
- **Dolby Vision Stream & Profile Detection (`DolbyVisionStreamHints`):** Accurately detects Dolby Vision and profile numbers (P5, P7, P8) from stream titles, addon names, and filenames, eliminating false positives from DVD or HDR10+ tags.
- **Settings Toggle:** Added "Dolby Vision Automatic Color Fix" toggle under Settings → MPV (`mpv_dolby_vision_auto_fix`, enabled by default).
- **MP4 / TS Profile 5 RPU Protection (`DolbyVisionExtractorsFactory`):** Profile 5 pixels are in IPTPQc2 color space and lack an independent HDR10 base layer. RPU stripping on P5 has been disabled to prevent green tinting.
- **MKV Profile 5 MIME Preservation (`MatroskaExtractor`):** Stopped the Matroska demuxer from downgrading P5 MIME types to `video/hevc` under HDR10 base-layer mode.
- **Profile 7 Single-Track Remux Cleaning (`dovi_bridge.cpp` & `DolbyVisionMatroskaTransformer`):** NAL type 63 (Enhancement Layer) packets in single-track P7 remuxes are safely dropped before reaching the base decoder, preventing corrupted frames.
- **Hidden DV Decoder Discovery (`Media3PlayerEngine` & `DolbyVisionCodecFallback`):** Integrated `DolbyVisionCodecFallback` into Media3's codec selector to discover vendor-hidden Dolby Vision decoders on specific chipsets.
- **Profile 5 → 8.1 Signal-Only Rewrite:** If a device supports decoding Profile 8.1 but not Profile 5, the stream's codec string is rewritten to 8.1 signal-only so the hardware performs IPTPQc2→RGB conversion without corrupting the RPU.
- **Stale MKV Config Guard (`DolbyVisionCompatibility`):** Prevents stale MKV DV configurations with SDR color metadata from being misidentified as Dolby Vision.
