# Plan / Task — Manga toplu silme ve kalite–kaynak ayrımı

## Amaç
Manga eklentilerini toplu kaldırmak; oynatıcıdaki gerçek video kalitelerini alternatif sağlayıcı/kaynak bağlantılarından ayırmak; APK uyumluluk hatasının sınırlarını belirlemek.

## Yapılanlar
- [x] Yüklü manga eklentilerinde paket bazlı seçim, tümünü seç/bırak ve onaylı toplu silme.
- [x] Silme işlemlerini IO üzerinde sırayla çalıştırma, listeyi yenileme, başarısız paket sayısını gösterme.
- [x] Ayarlar ve manga ekranı callback bağlantıları.
- [x] Kalite ve Kaynaklar için ayrı oynatıcı butonları/pencereleri.
- [x] Kalite listesine yalnızca açık videonun Media3 tarafından desteklenen video parçalarını alma.
- [x] Video parçasını URL/kaynak değiştirmeden seçme; Otomatik seçimiyle video override temizleme.
- [x] Kaynak/bölüm değişiminde eski video override temizleme ve eski seçimleri doğrulama.
- [x] Alternatif kaynak ve hata kurtarma akışlarını Kaynaklar penceresine yönlendirme.
- [x] Yerleşik Rapidrame/Close çözücüsünde standart HLS master listesini koruma; sahte 1080p etiketini kaldırma.
- [x] Türkçe/İngilizce metinler, extractor regresyon testleri ve teknik rapor.

## Kontroller / açık işler
- [x] git diff --check; TR/EN XML ve yinelenen kaynak adı kontrolleri.
- [x] Kalite/kaynak bağlantıları ve kalite seçiminde yeniden oynatma başlatılmaması için yapısal kontroller.
- [ ] JVM testlerini çalıştır: Java/JAVA_HOME olmadığı için Gradle başlatılamadı.
- [ ] Android derlemesi ve gerçek cihazda manga silme/kalite değiştirme testi.
- [ ] MPV gerçek kalite API desteği: şu anda desteklenmiyor; açıklama gösteriliyor.
- [ ] Canlı FilmMakinesi/HDFilmCehennemi akışlarını ve kendi çözücüsü öncelik alan eklentileri doğrula.
- [ ] Arkadaşın cihaz modeli, Android sürümü ve dağıtılmış APK ile kurulum hatasını teşhis et.

## APK notu
Depo ayarları minSdk 26 (Android 8.0), ARM 32/64 bit. Ekrandaki APK 2.4.368,
depodaki sürüm ayarı 2.4.340; dağıtılan APK incelenmeden kesin neden söylenemez.
Bu paket APK içermez, derleme veya cihaz testinden geçmiş sürüm olarak sunulmaz.

## Uygulama
ZIP içindeki dosyaları depo köküne aynı yolları koruyarak uygulayın.
Paket değişen/yeni dosyaların tam içeriğini içerir; tam depo değildir.
Teknik ayrıntılar: docs/fixes/2026-10-25-install-manga-quality.md.
