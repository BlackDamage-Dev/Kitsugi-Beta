# Manga-TR Entegrasyonu — Plan ve Görev Durumu

**Paket zamanı:** 2026-11-04 20:37 UTC  
**Depo:** f26901964-eng/Kitsugi-Beta  
**Çalışma dalı:** arena/c62dcb13-kitsugi-beta  
**Başlangıç commit:** a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe  
**Paketlenen commit:** 14ff2de8651845d9e7348b4d6befcfd30554aa1f  
**Kapsam:** Bu oturumda başlangıç commit'ine göre değişen 51 dosya; eklenti deposunun kaynak kodu veya derlenmiş APK'lar dahil değildir.

## Hedef ve sınırlar

Manga-TR eklentisinin yüklenmesi, ağ ve hata davranışı, filtreli arama, manga detayı, bölüm listeleri ve okuyucu/görsel akışının uygulama tarafında uyumlaştırılması. Görseller WebView'dan alınmaz; eklentinin OkHttp istemcisi ve URL fragment'i kullanılır. Ek Cloudflare bypass veya grup seçimi modalı eklenmedi.

## Tamamlanan görevler

- [x] Mihon/KeiSource uyumluluğu, gerekli interceptor adlarının R8 tarafından korunması, APK paket/sürüm doğrulaması ve Android 14 salt okunur dinamik DEX yüklemesi.
- [x] Kaynak hatası ile boş arama sonucunun ayrılması; doğru hata mesajı/kodunun gösterilmesi ve kullanıcının açık yeniden denemesinin cooldown filtresini aşması.
- [x] Eklentinin filtre indekslerinin ve sayfalama bilgisinin korunması; detay/bölüm istekleri için aynı manga URL'sinde eşzamanlı çağrıların serileştirilmesi.
- [x] Eklentinin URL atamadığı detay yanıtında istenen manga URL'sinin korunması; bölümlerde URL bazlı tekrar temizliği ve çeviri grubu bilgisinin korunması.
- [x] Eklentinin kendi görsel istemcisi ve fragment'ının kullanılması; işlenmiş yerel dosyanın okuyucuda gösterilmesi, fragment'ı koruyan önbellek ve hatalı sayfayı yeniden deneme.
- [x] Eklentinin bozuk sayısal parça sırasıyla karşılaşması halinde, önceden belirlenebilen geçersiz sıralamaların hata olarak bildirilmesi; geçerli fragment'lar değiştirilmez.
- [x] Çerez deposunda gerçek Domain/host-only/path eşlemesi; WebView'dan niteliği bilinmeyen çerezleri yalnız sorgulanan host için alma ve etkileşimli üyelik girişi.
- [x] İndirilen APK güven/başlatma denetiminde hata olursa önceki dosya ve bellek kaynağını geri alma; kullanıcının onayladığı imza ile indirilen imzayı karşılaştırma.
- [x] Manga-TR için spekülatif, doğrulanmamış otomatik alan adı tahminini kapatma; kullanıcı tarafından daha önce yapılandırılmış alan adını silmeme.
- [x] Android CI, kaynak/okuyucu testleri, gerçek eklenti APK'sını sabit SHA-256 ile alan emülatör fixture testleri ve entegrasyon notları.

## Doğrulama ve mevcut durum

- GitHub Android CI `41902427290`, commit `cc855b1`: seçili Gradle birim testleri, FOSS debug ve R8 release derlemeleri ve iki APK için DEX yapısal doğrulaması başarılı; her ikisinde VerifyError sayısı 0.
- Yerel sınırlı JVM/stub testleri: kaynak hata sınıflandırması, filtre/arama yeniden denemesi, çerez kuralı ve APK geri alma kontrolleri çalıştırıldı. Bu harness gerçek Android veya canlı site testi değildir.
- GitHub emülatör koşusu `41902427376` gerçek enstrümantasyon testlerine ulaştı; eski v1.6.24 eklenti testi, fixture'da zorunlu bölüm anahtarı bulunmadığı için başarısız oldu. Fixture ardından her iki sürümün beklediği anahtar, şifreli gate/config ve parça sırası verisiyle güncellendi.
- Güncel emülatör koşusu `41906017371` ve güncel Android derleme koşusu `41906015721` paket oluşturulurken **devam ediyor**. Sonuçları henüz başarılı sayılmamalıdır. Durum: https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41906017371 ve https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41906015721
- Yayın engeli ilk incelemeden sonra düzeltildi: builds commit `bca61cbff15930394f3c87fac9ce556b041c7bac` itibarıyla katalog ve APK manifesti 1.6.26 / 106026 ile eşleşiyor. Önceden aynı isimli APK aslında 1.6.24 idi.

## Kalan işler / manuel kabul

- [ ] Güncel CI ve emülatör koşularının sonuçlarını kontrol et; hata varsa rapor ve logdaki özgül nedeni düzelt.
- [ ] Minify edilmiş release uygulamasında gerçek APK yükleme ve siteye erişimi Android cihazda doğrula.
- [ ] Canlı Manga-TR üzerinde Chainsaw Man araması, filtreler ve sonraki sayfa; manga detayı, uzun bölüm listesi ve grup/çeviri satırlarını kontrol et.
- [ ] Şifreli/fragment'lı gerçek bölüm sayfalarını eklentinin kendi OkHttp hattı ile doğrula; dikey/webtoon, yatay/RTL, TV, önbellek ve retry davranışını kontrol et.
- [ ] Gerçek hesapla forum girişi ve ana site çerezlerinin davranışını test et. WebView CookieManager orijinal çerez niteliklerini geri vermediğinden canlı site davranışı garanti edilmemiştir.
- [ ] Eklenti güncellemesinde hatalı imza, uyumsuz APK ve kesilen işlem senaryolarını cihazda kontrol et. Başarılı yüklenip sadece sonraki site isteğinde bozulan eklenti otomatik geri alınmaz; işlem sırasında süreç ölümü için kurtarma uygulanmadı.
- [ ] Görsel parça sayısının indirilen görsel yüksekliğinden büyük olması gibi, indirme öncesi belirlenemeyen bozuk eklenti yanıtlarını ayrıca değerlendir.

Ayrıntılar ve bilinen sınırlar: `docs/manga-tr-integration.md`.
