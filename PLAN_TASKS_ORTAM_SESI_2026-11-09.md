# PLAN & TASK — Okuyucu Ortam Sesi (Manga Reader Ambient Audio)

**Paket:** v2.4.485 · **Tarih:** 9 Kasım 2026 · **Dal:** `arena/330bdeae-kitsugi-beta`
**Taban:** `origin/release` v2.4.479 · **Son sürüm:** `2.4.485`

---

## 1. Amaç

Okuyucudaki ortam sesi panelini **(a)** v2.4.471 öncesi sekmeli düzene döndürmek,
**(b)** kaynakları kaldırmadan genişletmek, **(c)** internetten otomatik çalma ve
sıralı kuyruğu eski panelin Ayarlar sekmesinde sunmak, **(d)** harici entegrasyonlar
ekranına yeni kaynakları eklemek.

---

## 2. Görev tablosu

| # | Görev | Durum | Sürüm | Dosya(lar) |
|---|-------|-------|-------|------------|
| T1 | Tek ekranlı "Okuma müziği" sayfasını kaldır, 8 sekmeli paneli geri bağla | ✅ | 2.4.480 | `MangaAmbientAudioSheet.kt` (silindi), `MangaReaderScreen.kt` |
| T2 | Çok kaynaklı motoru panelin **Çevrimiçi** sekmesine taşı (arama, indirilenler, kuyruk) | ✅ | 2.4.480 | `MangaAmbientOnlineTab.kt`, `MangaAmbientOnlineController.kt` |
| T3 | Jamendo BYOK kaynağını geri getir (Client ID yoksa sessiz atlama + panel uyarısı) | ✅ | 2.4.480 | `JamendoAudioSource.kt`, `AmbientAudioRepository.kt` |
| T4 | İnternetten otomatik çalma: başlıkla ara → en alakalı → sırayla, liste bitince başa dön | ✅ | 2.4.480 | `MangaAmbientPanel.kt`, `MangaReaderScreen.kt` |
| T5 | Ayarlar sekmesinde tek anahtar (mükerrer anahtar kaldırıldı) + kaynak seçimi | ✅ | 2.4.480 | `MangaAmbientOnlineTab.kt` |
| T6 | Harici Entegrasyonlar → Müzik: kaynak listesi + Jamendo senkronu | ✅ | 2.4.480 | `KitsugiIntegrationsSettingsDialog.kt` |
| T7 | Çift çalma koruması: eski motor artık yalnızca cihaz içi sesleri başlatır | ✅ | 2.4.480 | `MangaReaderScreen.kt` |
| T8 | **Anime OP/ED** kaynağı (AnimeThemes `.ogg`, indirilebilir, anahtarsız) | ✅ | 2.4.481 | `AnimeThemesAudioSource.kt` |
| T9 | **YouTube Music** kaynağı (InnerTube arama + çalma anında çözüm) | ✅ | 2.4.482 | `YouTubeMusicAudioSource.kt`, `MangaAmbientAudioPlayback.kt` |
| T10 | **Ses-only garanti**: video izi devre dışı, indirilen dosya ses-only | ✅ | 2.4.483 | `MangaAmbientAudioPlayback.kt`, `AmbientAudioDownloadManager.kt` |
| T11 | **Tek kuyruk**: en alakalıdan en alakasıza, tüm kaynaklar arası sıralı çalma | ✅ | 2.4.483 | `AmbientAudioRepository.kt`, `AmbientAudioPlanner.kt` |
| T12 | Moda duyarlı uygunluk filtresi (konuşma / vokal / SFX) | ✅ | 2.4.483 | `AmbientAudioPlanner.kt` |
| T13 | **YouTube indirme** (kalıcı, AAC 128k, imzalı adresten bağımsız) | ✅ | 2.4.484 | `AmbientAudioDownloadManager.kt` |
| T14 | **"Bir daha önerme"** kalıcı kara liste + **karıştır** | ✅ | 2.4.484 | `AmbientAudioPreferences.kt`, `MangaAmbientOnlineController.kt`, tab |
| T15 | **Uyku zamanlayıcısı** çevrimiçi kuyruğu da susturur; **yumuşak ses girişi** | ✅ | 2.4.485 | `AmbientAudioEngine.kt`, `MangaAmbientAudioPlayback.kt` |
| T16 | Cihazda derleme + doğrulama | ⏳ BEKLİYOR | — | (JDK/Android SDK bu ortamda yok) |

---

## 3. Kaynak envanteri (11)

| Kaynak | Anahtar | Akış | İndirme | Not |
|---|---|---|---|---|
| YouTube Music | — | ✅ (çalma anında çözülür) | ✅ (v2.4.484) | Deneysel; HLS-only parçalarda indirme reddedilir |
| Anime OP/ED | — | ✅ doğrudan `.ogg` | ✅ | AnimeThemes CDN, çözücü yok |
| Jamendo | Kişisel Client ID | ✅ | ✗ | Anahtar yoksa sessiz atlanır |
| Freesound · Openverse | — | ✅ | ✅ | |
| Wikimedia audio | — | ✅ | ✅ | |
| Internet Archive | — | ✅ | ✅ | |
| Audius | — | ✅ | ✅ | |
| Wikimedia Commons | — | ✅ | ✅ | |
| ccMixter | — | ✅ | ✅ | |
| Free To Use | — | ✅ | ✅ | |
| Radio Browser | — | ✅ (canlı) | ✗ | Süre bilgisi yok |

---

## 4. Değişen dosyalar (21)

**Yeni (6)**
```
app/src/main/java/com/kitsugi/animelist/data/ambient/AmbientAudioPreferences.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/AnimeThemesAudioSource.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/JamendoAudioSource.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/YouTubeMusicAudioSource.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaAmbientOnlineController.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaAmbientOnlineTab.kt
```

**Değişen (14)**
```
RELEASE_NOTES.md
app/src/main/java/com/kitsugi/animelist/core/music/AmbientAudioEngine.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/AmbientAudioDownloadManager.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/AmbientAudioModels.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/AmbientAudioPlanner.kt
app/src/main/java/com/kitsugi/animelist/data/ambient/AmbientAudioRepository.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaAmbientAudioPlayback.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaAmbientPanel.kt
domain_fixes.json
version.properties
ARASTIRMA_MUZIK_KAYNAKLARI_2026-11-09.md   (yeni belge)
COZUM_RAPORU_ORTAM_SESI_PANEL_GERI_DONUS_2026-11-09.md   (yeni belge)
```

**Silinen (1)**
```
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaAmbientAudioSheet.kt
```

---

## 5. Cihazda doğrulama listesi

1. Okuyucu → müzik düğmesi → **8 sekme** görünüyor mu? (Senin İçin, Ambiyans, Müzik, Çevrimiçi, Karışım, Profil, Süre, Ayarlar)
2. Çevrimiçi → "Başlıkla ara" → sonuç geliyor mu? Kaynak rozetleri doğru mu?
3. Ayarlar → "Açılışta otomatik çal" aç → okuyucuyu kapat/aç → müzik kendiliğinden başlıyor mu?
4. Kuyruk sırası başlık eşleşmeleriyle başlıyor mu? Parça bitince sıradakine geçiyor mu?
5. Bir YouTube parçası çalarken **video görüntüsü/pil** tüketimi yok mu (ses-only)?
6. YouTube parçasında **indir** → uygulamayı kapat/aç → indirilenler şeridinde görünüyor ve **internet kapalıyken** çalıyor mu?
7. Parça satırında **×** → parça listeden çıkıyor, sonraki aramada gelmiyor mu? (Ayarlar'da "Temizle" çalışıyor mu?)
8. Mod çiplerinde **Karıştır** sırayı değiştiriyor mu?
9. Süre sekmesinden **uyku zamanlayıcısı** → süre dolunca çevrimiçi kuyruk da susuyor mu?
10. Parça geçişlerinde **pat sesi** yok mu (fade-in)?

---

## 6. Bilinen riskler

| Risk | Etki | Azaltma |
|---|---|---|
| YouTube InnerTube şeması değişir | Arama boş döner | Ağaç taraması (`videoRenderer` her yerde aranır) + kaynak uyarısı, diğer 10 kaynak devam eder |
| YouTube imzalı adres 403 | Parça atlanır | 4 client profili + HLS fallback + önceden çözümleme |
| HLS-only parçada indirme | "Çözülemedi" durumu | Açık hata mesajı; tek dosyalık akışlarda çalışır |
| AnimeThemes'te başlık yok | Boş liste | Diğer kaynaklar + cihaz içi motor |
| `AppSettings.copy` yanlış kullanımı | Derleme hatası | `integrations.copy(...)` kullanıldı (v2.4.480 düzeltmesi) |
| ExoPlayer thread kuralı | Çalışma anı çökmesi | Ses rampası `Dispatchers.Main.immediate` üzerinden |

---

## 7. Sürüm geçmişi

| Sürüm | Özet |
|---|---|
| 2.4.480 | Sekmeli panel geri döndü · 9 kaynak (Jamendo dahil) · internetten otomatik çalma Ayarlar'da |
| 2.4.481 | Anime OP/ED kaynağı (10) |
| 2.4.482 | YouTube Music kaynağı (11) |
| 2.4.483 | Ses-only garanti · tek kuyruk (alakalı→alakasız) · moda duyarlı filtre |
| 2.4.484 | YouTube indirme · kara liste · karıştır |
| 2.4.485 | Uyku zamanlayıcısı tüm kuyruğu susturur · yumuşak ses girişi |
