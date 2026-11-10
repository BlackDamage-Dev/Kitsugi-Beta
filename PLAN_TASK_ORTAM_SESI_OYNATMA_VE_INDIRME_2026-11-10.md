# Ortam Sesi: oynatma kontrolleri, indirme nedenleri, kaynak listesi

## Kök nedenler
- **İndirilemeyen parçalar:** `AmbientAudioDownloadManager.start()` reddedilen durumlarda sessizce `false` dönüyordu; UI dönüş değerini kullanmıyordu. Kullanıcı tıklayınca hiçbir şey olmuyordu.
- **Desteklenmeyen biçim:** Dönüştürme başarısız olunca veya AAC kodlayıcı yoksa özgün dosya kaydediliyordu; cihaz çözemiyorsa dosya kaydedilip çalınmıyordu.
- **MediaStore hatası:** Ekleme başarısız olunca indirme tamamen düşüyordu (uygulama klasörüne yedek yoktu).
- **Tekrar/kuyruk:** `playTrack` kuyruğu tek parçaya indiriyordu; arama kuyruğu ise `repeatQueue = true` ile sabit tekrar ediyordu.
- **Çevrimiçi sekmesinde kaynak listesi:** Ayarlar kartıyla çift; kaldırıldı.

## Yapılanlar (v2.4.487 hedefi)
1. `AmbientQueueRules.kt`: saf kurallar (tekrar döngüsü, sıralı devam kuyruğu, aday seçimi, yenileme eşiği, indirme ret nedeni).
2. `AmbientAudioPreferences.kt`: `repeatMode` (varsayılan liste tekrarı) ve `continuousPlay` (varsayılan açık).
3. `AmbientAudioDownloadManager.kt`: `start()` ret nedenini `String?` döndürür, `FAILED` mesajı yazılır; biçim kontrolü; çözülemeyen biçim için dönüştürme zorunlu; MediaStore hatasında uygulama klasörüne yedek.
4. `MangaAmbientAudioPlayback.kt`: `repeatMode: Int`, `setRepeatMode`, `queueSnapshot`, `queueIndex`, `onNearQueueEnd`.
5. `MangaAmbientOnlineController.kt`: tekrar/sıralı devam durumu, `playTrack` continuation kuyruğu, `resumeAutoQueue`, yerel-önce yenileme (yeni aramada 60 sn bekleme), indirme nedeni Toast.
6. `MangaAmbientOnlineTab.kt`: KAYNAKLAR bloğu kaldırıldı; gizlilik ve Jamendo notu Ayarlar kartına taşındı; tekrar / sıralı devam / sıraya dön çipleri; indirilemeyen parçada soluk CloudOff düğmesi ve FAILED mesajı.
7. `AmbientQueueRulesTest.kt`: JUnit4 birim testleri.

## Doğrulama
- Yerel derleme yok (sandbox'ta JDK/Android SDK yok). Doğrulama için GitHub Actions `android-build.yml` (arena/** push) kullanılır.
- Cihaz testi: indirilemeyen parçada neden mesajı; tekrar modu döngüsü; sıralı devam açık/kapalı; "Sıraya dön"; m3u8 ve ape/wv gibi biçimlerde net hata.

## Açık konular
- YouTube indirmesi (`kitsugi-yt://`) YouTube Hizmet Şartları ile çelişebilir; kaldırılmadı, kullanıcıya bildirildi.
- YouTube akışında reklam: mevcut wiki reklam engelleyici yalnızca WebView sayfalarına uygulanır, ExoPlayer medya akışına uygulanmaz.
