# PLAN / TASK — İndirilen Videoda Ses Gömülü Olması (Tek Dosya)

**Tarih:** 2026-11-20
**Dal:** `arena/2f0ed9cd-kitsugi-beta`
**Temel commit:** `97b5aa4` → **Çalışma commit:** `a91980c`
**Durum:** Kod tamamlandı, statik kontroller geçti; **derleme/birim testi çalıştırılamadı**
(sandbox'ta Android SDK / JDK / Gradle yok).

---

## 1. Kullanıcı sözleşmesi (kabul kriterleri)

| # | Kriter |
|---|---|
| K1 | Video indirirken ses **videoya gömülü** olmalı (tek `.mp4`). |
| K2 | Ses ayrı dosya olarak **yalnızca** kullanıcı 🎧 "sesi ayrı indir" derse iner. |
| K3 | HDFilmCehennemi / FilmMakinesi (Close CDN `master.txt`, Rapidrame) ayrık sesli HLS akışlarında da K1 geçerli. |
| K4 | MP4'ün kabul etmediği sesler (E-AC-3 / AC-3 / DTS / MP3 / FLAC / Opus / 5.1-7.1) otomatik **AAC-LC stereo**'ya çevrilip gömülür. |
| K5 | A/V dudak senkronu kaymaz (Packed-Audio `ID3` zaman damgası korunur). |
| K6 | Ses izi olmayan (sessiz) dosya "indirildi" sayılmaz; net hata gösterilir. |
| K7 | Küçük videolar (fragman / kamera arkası / tema) da tek dosya olarak iner. |

---

## 2. Görev tablosu

| # | Görev | Dosya | Durum |
|---|---|---|---|
| T1 | Ayrık ses indirildiğinde önce `MediaTrackRemuxer.remuxVideoWithAudios` çağrılsın; `MediaRemuxer` yalnızca güvenlik ağı olsun | `AnimeDownloader.finalizeDownloadedMedia` | ✅ |
| T2 | Mux ara dosyaları `cacheDir/kitsugi_mux_*` altında üretilsin (Video klasörü temiz kalsın) | `AnimeDownloader` | ✅ |
| T3 | Gömülü sesli tekil akışlarda (özellikle `.ts`) ses kodeki uygun değilse AAC-LC'ye çevirip aynı `.mp4`'e göm | `AnimeDownloader.remuxEmbeddedAudioIfNeeded` + `isMp4MuxFriendlyAudio` | ✅ |
| T4 | Mux çıktısında hem görüntü hem ses izi doğrulaması (`hasBothTracks`) | `MediaTrackRemuxer` | ✅ |
| T5 | Sessiz dosya kontrolü: `DownloadVerifier` + track probe; ses yoksa dosyayı sil + kalıcı hata | `AnimeDownloader` | ✅ |
| T6 | `UnsupportedMediaException` → `ErrorKind.OTHER` (gereksiz retry yok) | `DownloadResilience` | ✅ |
| T7 | A/V senkron: `audioStartPtsUs` parametresi metordan mux'a taşınsın; `alignAudioPtsUs` (+5 sn eşik) | `MediaTrackRemuxer`, `AnimeDownloader` | ✅ |
| T8 | Klip indirmesinde `SEPARATE_AUDIO` reddi kaldırılsın; ses indirilip mux edilsin, eski `.ts` artığı silinsin | `ClipDownloadSource`, `AnimeDownloader.downloadClip` | ✅ |
| T9 | Yeni metin: `indirme_hata_ses_izi_yok` (tr + en), XML string sayısı minimum artış | `res/values/strings.xml`, `values-en` | ✅ |
| T10 | `alignAudioPtsUs` için birim testleri (3 vaka) | `ClipDownloadTest` | ✅ |
| T11 | `./gradlew testDebugUnitTest` + `assembleDebug` | — | ⛔ ortam yok |
| T12 | Değişen dosyalar + plan task zip → Drive | — | ✅ (bu paket) |

---

## 3. Kök neden özeti

1. **KN-1 (asıl):** `finalizeDownloadedMedia` yalnızca `MediaRemuxer` (platform `MediaExtractor`)
   kullanıyordu. O motor E-AC-3/AC-3/DTS/MP3/FLAC'ı MP4'e yazamayınca hiç dönüştürme yapmadan
   `CompanionAudioRequired` dönüyor → `...audio.m4a` ayrı dosya. Depodaki doğru motor
   (`MediaTrackRemuxer` + `UniversalAudioTranscoder`) **ölü koddu**, indirme yolundan çağrılmıyordu.
2. **KN-2:** Gömülü sesli `.ts → .mp4` adımı da aynı zayıf motora bağlıydı.
3. **KN-3:** Video yolunda hiç `DownloadVerifier` yoktu → sessiz dosya `COMPLETED` görünüyordu.
4. **KN-4:** `audioFirstId3PtsUs` yalnızca zayıf motora veriliyordu → senkron riski.
5. **KN-5:** `ClipDownloadSource` ses ayrıysa indirmeyi tamamen reddediyordu.

---

## 4. Değişen dosyalar (8)

```
app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt      (+250/-…)
app/src/main/java/com/kitsugi/animelist/core/download/MediaTrackRemuxer.kt    (+77)
app/src/main/java/com/kitsugi/animelist/core/download/ClipDownloadSource.kt   (+38/-…)
app/src/main/java/com/kitsugi/animelist/core/download/DownloadResilience.kt   (+6)
app/src/main/res/values/strings.xml                                          (+1)
app/src/main/res/values-en/strings.xml                                       (+1)
app/src/test/java/com/kitsugi/animelist/core/download/ClipDownloadTest.kt    (+48)
COZUM_RAPORU_INDIRME_SES_GOMULU_2026-11-20.md                               (yeni rapor)
```

---

## 5. Uygulama / doğrulama adımları

```bash
# 1) Yamayı uygula (zip kök dizininden)
unzip -o Kitsugi_Indirme_Ses_Gomulu_Fix_2026-11-20.zip -d .

# 2) Derle
./gradlew :app:assembleDebug

# 3) Birim testleri
./gradlew :app:testDebugUnitTest --tests "*ClipDownloadTest*"

# 4) Cihazda doğrulama (logcat)
adb logcat | grep -E "AnimeDownloader|MediaTrackRemuxer"
#   Beklenen: "Video + ayrık ses tek dosyada birleştirildi: <ad>.mp4 (mp4)"
#   Beklenen: "Gömülü ses korunarak yeniden paketlendi: <ad>.mp4 (sesGömülü=true)"
#   Beklenmemesi gereken: "...audio.m4a" (yan ses) ve sessiz dosya
```

**Manuel senaryolar**

| Senaryo | Beklenen |
|---|---|
| HDFC/FM 1080p Türkçe dublaj (ayrık ses) | `Download/Kitsugi/Video/<Bölüm>.mp4` — tek dosya, ses gömülü |
| Ses E-AC-3 / 5.1 olan kaynak | AAC-LC stereo gömülü `.mp4` |
| Gömülü sesli TS kaynak | `.mp4`, `.ts` artığı yok |
| Ses izi hiç olmayan kaynak | Hata: "İndirilen videoda ses izi bulunamadı…" + dosya silinir |
| 🎧 sesi ayrı indir | `Download/Kitsugi/Audio/<Bölüm>.<dil>.m4a` (davranış değişmedi) |
| Detay sayfası fragmanı (YouTube adaptive) | `Downloads/Kitsugi/Klipler/<ad>_1.mp4` — ses gömülü |

---

## 6. Risk ve geri alma

- Her yeni adım `runCatching` + doğrulama ile sarıldı; evrensel motor başarısız olursa eski
  `MediaRemuxer` yolu aynen çalışır → davranış gerilemez, en fazla eski (yan ses) davranışa döner.
- Geri alma: `git revert a91980c`.
- DEX 64K `R$string` sınırı: yalnızca **+2** string (tr + en).
