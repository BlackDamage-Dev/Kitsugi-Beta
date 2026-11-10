# Plan Task — Oynatıcı kaydırma, video-bazlı devam, bölüm ön yükleme ve klavye

**Durum:** Uygulandı; otomatik derleme/test çalıştırılamadı (ortamda Java/JDK yok).

## Amaç

Tam ekran oynatıcıdaki sürükleme sırasında kontrollerin kaybolmasını önlemek; kaldığın yeri medya/bölüm/kaynak/video kimliğine özgü saklamak; sonraki bölümü izlenen eklenti üzerinden önceden arayıp hızlı geçiş sağlamak; oynatıcı özelliklerini fiziksel klavye ve medya tuşlarıyla kullanılabilir hâle getirmek.

## Uygulama kapsamı

1. **Seekbar / sürükleme**
   - Aktif sürükleme boyunca OSD gizlenme sayacı durdurulur.
   - Kullanıcı bırakınca hedef konuma seek edilir, önizleme temizlenir ve yeni gizlenme sayacı başlatılır.
   - Kullanıcı etkileşimi, still-watching denetleyicisine bildirilir.

2. **Video-kimlikli devam konumu**
   - Her kayıt; medya, sezon, bölüm, eklenti, kaynak adı ve kararlı kaynak URL’si/torrent hash’i ile SHA-256 anahtarına bağlanır.
   - İmzalı bağlantılardaki geçici token/süre parametreleri anahtar oluşturulurken dışarıda bırakılır.
   - İlerleme oynatma sırasında, kaynak değişiminde ve oynatıcı kapanırken saklanır; tamamlanmış videolar için eski devam noktası temizlenir.
   - Aynı bölümde başka bir kaynak seçildiğinde başka videonun konumu devralınmaz.

3. **Bölüm uyumu ve ön yükleme**
   - Sonraki bölüm akışları oynatılan Stremio eklentisi veya CloudStream sağlayıcısı önceliklendirilerek arka planda aranır ve oturum içi önbelleğe alınır.
   - Bölüm geçişi, varsa bu sonuçları kullanır; tercih edilen sağlayıcı sonuç vermezse tüm kaynaklara genişleyen yedek arama çalışır.
   - Yeni bölümün kaynak listesi ancak kaynak başarıyla çözümlenince etkinleştirilir; eşzamanlı bölüm isteklerinde eski cevapların durumu ezmesi engellenir.

4. **Klavye / medya kısayolları**
   - Oynat/duraklat, seek, bölüm geçişi, yüzdeye atlama, ses/parlaklık, sessize alma, hız, ses/altyazı parçaları ve gecikmeleri, görüntü oranı, yön ve kontrol kilidi kısayolları eklenir.
   - Klavye tuş bırakma olayları ve tekrarlı tuş basışları güvenli biçimde ele alınır; diyalog ve paneller kendi klavye odağını korur.

## Kabul ölçütleri

- Seekbar sürüklemesi sürerken OSD kapanmamalı, bırakma sonrası doğru konuma gitmelidir.
- Farklı bölüm veya farklı video/kaynak birbirinin devam konumunu kullanmamalıdır.
- Sonraki bölüm akışı mümkün olduğunda aktif eklenti üzerinden önceden hazır olmalı; bulunamazsa genel arama yedeği çalışmalıdır.
- Klavye kısayolları menü/dialog odağını bozmadan ses, parlaklık, oynatma, altyazı/ses parçaları ve temel görüntü ayarlarını yönetmelidir.

## Değiştirilen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/repository/AddonStreamRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerControls.kt`
- `app/src/main/java/com/kitsugi/animelist/core/player/PlayerResumeStore.kt` (yeni)
- `app/src/test/java/com/kitsugi/animelist/core/player/PlayerResumeIdentityTest.kt` (yeni)

## Doğrulama

- `git diff --check`: başarılı.
- Gradle derleme ve birim testleri: çalıştırılamadı; sandbox ortamında `java`/`JAVA_HOME` bulunmuyor.
