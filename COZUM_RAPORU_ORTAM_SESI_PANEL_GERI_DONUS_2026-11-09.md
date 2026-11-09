# Çözüm Raporu — Ortam Sesi Paneli Eski Düzenine Döndürüldü + Kaynaklar Genişletildi (v2.4.480)

**Tarih:** 2026-11-09
**Sürüm:** v2.4.480
**Konu:** Manga okuyucusunun alttan açılır ortam sesi panelinin v2.4.471 öncesi
sekmeli düzenine döndürülmesi, kaynak listesinin korunup genişletilmesi (9 kaynak) ve
internetten otomatik çalmanın eski panelin **Ayarlar** sekmesine taşınması.

---

## 1. Kök neden

| # | Bulgu | Etki |
|---|-------|------|
| 1 | `v2.4.471` (`efe045bc`) commit'i, okuyucunun müzik düğmesini `MangaAmbientPanel` (8 sekmeli klasik panel) yerine yeni `MangaAmbientAudioSheet` (tek ekran) bileşenine bağladı. | Senin İçin, Ambiyans, Müzik, Karışım, Profil, Süre ve **Ayarlar** sekmeleri tamamen erişilemez hâle geldi. |
| 2 | Klasik panel dosyası (`MangaAmbientPanel.kt`) silinmemiş ama `showMusicPanel` bayrağı hiçbir yerden `true` yapılmadığı için **ölü kod** durumuna düştü. | Kullanıcı "özellikler kaldırıldı" hissi yaşadı; oysa kod duruyordu, yalnızca bağlantı kopuktu. |
| 3 | Yeni çok kaynaklı motor (`data/ambient/*` — 8 kaynak, kuyruk, çevrimdışı indirme) yalnızca yeni tek ekranlı sheet'e bağlıydı. | Kaynaklar genişletilmişti ama eski düzende görünmüyordu; iki ayrı ses mimarisi paralel yaşıyordu. |
| 4 | Eski panelin "Çevrimiçi" sekmesi 5 kaynaklı eski kataloğu (`MusicSourceId`) kullanıyor, kuyruk/sıralı çalma desteği yoktu. | "Okuduğum içeriğin adıyla en alakalı içerik sırayla çalsın" isteği karşılanamıyordu. |
| 5 | `Ayarlar → Harici Entegrasyonlar → Müzik` bölümü yalnızca eski 5 kaynağı listeliyordu. | Yeni kaynaklar (Wikimedia, ccMixter, Free To Use, Radio Browser, Wikimedia audio) ayarlarda görünmüyordu. |

---

## 2. Yapılan değişiklikler

### 2.1 Panel bağlantısı (geri dönüş)
- `MangaReaderScreen`: müzik düğmesi yeniden `showMusicPanel = true` yapıyor;
  `MangaAmbientAudioSheet` çağrısı ve `showAmbientAudio` durumu kaldırıldı.
- Yeni tek ekranlı dosya (`MangaAmbientAudioSheet.kt`) silindi; yerini panelin
  Çevrimiçi sekmesi aldı.
- Panel başlığı artık çevrimiçi kuyruğu da gösteriyor: parça adı, sanatçı ve
  `kuyruk konumu/toplam` (ör. `2/7`) ile duraklat/çal ve atla düğmeleri.

### 2.2 Yeni motorun panele taşınması
| Dosya | Görev |
|-------|-------|
| `data/ambient/AmbientAudioPreferences.kt` **(yeni)** | Panel + ayarlar için ortak SharedPreferences: açık kaynaklar, çevrimiçi akış, internetten otomatik çalma. |
| `ui/.../components/MangaAmbientOnlineController.kt` **(yeni)** | Durum tutucu: arama (başlık öncelikli), 8 kaynak seçimi, kuyruk oynatma, indirme, sıralı devam. |
| `ui/.../components/MangaAmbientOnlineTab.kt` **(yeni)** | Klasik panel görsel dilinde Çevrimiçi sekmesi + Ayarlar sekmesi kartı. |
| `ui/.../components/MangaAmbientPanel.kt` | `ambientOnline` parametresi; Çevrimiçi sekmesi yeni bileşene, Ayarlar sekmesi yeni karta bağlandı. `null` verilirse eski davranış korunur. |
| `ui/.../manga/MangaReaderScreen.kt` | Denetleyici oluşturma, bölüm değişiminde plan tazeleme, internetten otomatik başlatma, çıkışta kuyruğu durdurma. |
| `ui/components/KitsugiIntegrationsSettingsDialog.kt` | Müzik sekmesine "İnternetten otomatik çal" anahtarı ve 9 kaynağın aç/kapa listesi. |
| `data/ambient/JamendoAudioSource.kt` **(yeni)** | Jamendo v3.0 akış araması (kişisel `client_id`); indirme yok. |

### 2.3 İnternetten otomatik çalma davranışı
1. Okuyucu açılır (veya panelde "Önce manga adını ara ve en iyi eşleşmeyi çal"
   düğmesine basılır).
2. Açık manga kaynağındaki **başlık** seçili katalog(lar)da aranır.
3. Yeterli başlık eşleşmesi yoksa tür/özet ipuçlarından üretilen bağlam sorgusu
   devreye girer.
4. En iyi eşleşme çalar; sonuçlar `AmbientAudioPlanner.orderForAutoplay` ile
   sıralanır ve Media3 kuyruğu parça bitiminde **sıradakine** geçer.
5. Parça hata verirse kuyruk sonraki parçaya atlar (ölü akışa takılmaz).

Güvenlik kapıları korunur: `musicEnabled` ana anahtarı, `AutoplayGate`
(kulaklık/sessiz mod), gecikme ayarı (`musicAutoPlayDelaySeconds`) ve
"çevrimiçi akış kapalıysa yalnızca indirilmiş çalar" kuralı.

### 2.4 Çift çalma koruması
- İnternetten otomatik çalma açıkken eski cihaz içi otomatik başlatma
  (`AmbientAutoplayStarter`) atlanır — iki motor aynı anda başlamaz.
- Panel kapanınca kuyruk devam eder; okuyucudan çıkınca `stop()` + `release()`
  ile kesin durur.

---

## 3. Kullanıcıya yansıyan sonuç

| İstek | Durum |
|-------|-------|
| "Alttan açılır panel en son değişimden önceki hâline dönsün" | ✅ 8 sekme geri geldi |
| "Kaynaklar genişlesin, kaldırılmasın" | ✅ 9 kaynak panelde ve ayarlarda (Jamendo korundu, 4 yeni kaynak eklendi) |
| "Okuduğum içerik ismiyle en alakalı içerik sırayla otomatik çalsın" | ✅ Başlık öncelikli arama + sıralı kuyruk |
| "Bu özellikler eski panelin Ayarlar kısmında olsun" | ✅ Ayarlar sekmesinde kart + anahtarlar |
| "Harici Entegrasyonlar > Müzik kısmına yeni kaynaklar eklensin" | ✅ 8 kaynak anahtarı + "İnternetten otomatik çal" |

---

## 4. Cihazda doğrulama listesi

1. Manga okuyucu → müzik düğmesi → panel açılır, sekme bandında 8 sekme görünür.
2. **Çevrimiçi** sekmesi: arama kutusu, mod çipleri (Otomatik/Müzik/Ambiyans),
   `İNDİRİLENLER` şeridi, `KAYNAKLAR` listesi (9 satır), sonuç kartları.
3. "Önce manga adını ara ve en iyi eşleşmeyi çal" → ilk parça çalar, bitince
   sıradaki otomatik başlar (başlıkta `2/7` gibi ilerleme görünür).
4. Bir kaynağı kapat → yeniden aramada o kaynaktan sonuç gelmez.
5. Çevrimiçi akış oynatımını kapat → yalnızca indirilmiş parçalar çalar.
6. **Ayarlar** sekmesi → "Otomatik başlat" açıkken "Çevrimiçi kaynakları kullan"
   anahtarı internetten otomatik çalmayı yönetir.
7. `Ayarlar → Harici Entegrasyonlar → Müzik` → yeni kaynak anahtarları panel ile
   senkron çalışır.
8. Okuyucudan çık → ses kesilir; tekrar gir → kuyruk temiz başlar.
9. Uçak modu → yalnızca indirilmiş dosyalar listelenir/çalar, çökme yok.

---

## 5. Notlar

- v2.4.471–479 arasındaki indirme hattı, AAC dönüşümü, SegmentJournal ve HLS
  düzeltmeleri **korunmuştur**; yalnızca panel yüzeyi değişti.
- Eski 5 kaynaklı katalog (`MusicSourceId` + `MusicCatalogRepository`) hâlâ
  yerinde; panel `ambientOnline` verilmeden çağrılırsa (TV/önizleme yolları)
  eski davranış aynen çalışır.
- Ayar anahtarları tek dosyada (`ambient_audio_settings`) tutulduğu için
  v2.4.471 kullanıcılarının önceki tercihleri korunur.

---

## 6. v2.4.480 — ikinci geçiş (kendi kendini denetim) düzeltmeleri

İlk uygulamadan sonra yapılan statik denetimde bulunan ve düzeltilen gerçek hatalar:

| # | Hata | Düzeltme |
|---|------|----------|
| 1 | Eski otomatik başlatmaya `settings.copy(musicAutoPlayIncludeOnline = false)` geçiliyordu; bu alan `AppSettings`'in **constructor parametresi değil**, `integrations` üzerinden türetilen bir getter. **Derleme hatası** olurdu. | `copy(integrations = integrations.copy(musicAutoPlayIncludeOnline = false))` |
| 2 | Yeni "Çevrimiçi" sekmesinde `AmbientAudioDownloadManager.restoreSaved(...)` çağrısı yoktu → uygulama yeniden açıldığında **indirilmiş parçalar çevrimdışı çalmazdı**. | `LaunchedEffect(tracks.map { it.id })` içinde geri yükleme eklendi. |
| 3 | `recordPlayed` çağrılmıyordu → "son çalınanlar" geçmişi güncellenmez, aynı parçalar tekrar tekrar önerilirdi. | `LaunchedEffect(isPlaying, currentTrack?.id)` ile eklendi. |
| 4 | İnternetten otomatik çalma için **iki** anahtar vardı (AutoPlayCard içindeki "Çevrimiçi kaynakları kullan" + yeni kart). Biri gizli kalınca durum ile arayüz çelişiyordu. | Tek anahtar: yeni kart. AutoPlayCard'daki satır, çok kaynaklı motor bağlıyken gizlenir. |
| 5 | Eski motor `musicAutoPlayIncludeOnline` açıksa ikinci bir çevrimiçi arama yapabiliyordu. | Eski motor artık **yalnızca cihaz içi** sesleri başlatır (settings kopyası). |
| 6 | Jamendo eski panelde vardı, yeni motorda yoktu (kaynak "kaybı" görünürdü). | `JamendoAudioSource` + enum girdisi + BYOK `client_id` akışı eklendi; anahtarsızken sessizce atlanır, panelde sabit uyarı görünür. |

### Bilinen sınırlar (dürüst liste)

* **YouTube / anime OST'leri yoktur ve bilinçli olarak eklenmemiştir.** Ses kaynakları yalnızca açık lisanslı kataloglar (CC / kamu malı) ve kullanıcının kendi Jamendo anahtarıdır. YouTube'un şartları üçüncü taraf akışını yasaklar; anime OST'leri telifli içeriktir.
* Başlık eşleşmesi "en alakalı" sonucu **garanti etmez**: kataloglar manga adını içeren ses kayıtlarını döndürür; yeterli eşleşme yoksa tür/özetten üretilen **bağlam sorgusu** devreye girer (tasarım gereği).
* Derleme bu ortamda yapılamadı (JDK/Android SDK yok); cihazda ilk derleme sonrası yukarıdaki 9 maddelik doğrulama listesi koşulmalı.
