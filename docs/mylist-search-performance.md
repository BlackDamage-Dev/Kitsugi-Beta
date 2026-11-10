# Listem: yazarken donma / arama optimizasyonu (2026-10-22)

## Kök neden

2.603 kayıtlık Listem ekranında arama, her harfte `remember { ... }` içinde senkron
çalışıyordu. `remember` hesabı başka bir iş parçacığına taşımaz: filtreleme, NFKC/regex
normalizasyonu, fuzzy alt-dizi/Levenshtein eşleşmeleri, sıralama ve durum gruplama Main
üzerindeydi. 20 Ekim düzeltmesi bazı normalizasyonları tembelleştirmişti, ancak bu arama
zinciri hâlâ klavye ve çizimle aynı iş parçacığını kullanıyordu.

Ayrıca sekme verisinin `remember` anahtarında ham sorgu vardı; `r → re → rez` geçişleri
aynı kaynak listesini ve istatistiklerini gereksiz yere yeniden hesaplatıyordu. Grid
oluşturucuları da `chunked()` ile görünmeyen satırlar dahil bütün sonucu kopyalıyordu.

## Yeni akış

- Ham metin arama alanına anında gider; sonuç hesabı ayrı bir akıştır. İçerik sayfasına
  yalnızca **tamamlanmış** sorgu/sonuç aktarılır, her tuşta ham sorgu aktarılmaz.
- Yeni metin için **180 ms** yazma gecikmesi vardır. Temizleme, sadece filtre değiştirme,
  aynı sorguyla veri güncelleme ve detaydan dönüş bu gecikmeyi beklemez.
- `transformLatest` yeni harf geldiğinde eski işi hemen iptal eder. Yalnızca
  `debounce().mapLatest()` kullanmak eski CPU işini debounce bitene kadar çalıştırırdı.
- Filtreleme ve gruplama `Dispatchers.Default` üzerindedir. Tarama ve indeks hazırlığı
  her **32** kayıtta iptal kontrolü yapar. Liste ve grupları tek sonuç olarak yayımlanır.
- Başlık/İngilizce/Japonca adlar, NFKC anahtarları, metadata ve fuzzy kelime/baş harfleri
  veri anlık görüntüsü için önceden hazırlanır. Sorgu bir kez normalleştirilir.
- Sıralama sorgudan önce, yalnızca veri/sıralama değişiminde yapılır. Filtreleme sırayı
  koruduğu için sonuç sıralaması değişmez. Sonuç ve indeks ViewModel'de düz önbellektir;
  bu önbelleklere yazmak Compose'u tekrar tekrar geçersiz kılmaz.
- Compose efektleri kaynak listesi için referans eşitlikli anahtar kullanır. İçeriği
  eşit ama yeni bir liste de hazırlık referansını günceller; sekme değişince arama
  toplayıcısının eski indeks referansında takılı kalması önlenir. Pahalı içerik eşitliği
  ile önbellek doğrulaması yalnız arka planda yapılır.
- Değişen satırlar, sıralama veya uygulama dili indeksi geçersiz kılar. Belgeler kaynak
  ID'sine göre değil, aynı liste anlık görüntüsündeki konuma göre eşleştirilir.
- Sekme veri seçimi ham sorguya değil `isNotBlank()` sonucuna bağlıdır. Mevcut Tümü
  birleştirmesi, kaynaklar arası arama ve Bangumi kaynak sınırlaması korunur. Birleşik
  önbellek yenilenirken de güncel +18 gizleme tercihi arama/sayfa girdisine uygulanır.
- Kaydırma konumu geri yükleme/yazma, aktif sayfada gerçek arama sonucu hazır olana
  kadar bekler. Geçici boş yükleme listesi kayıtlı derin konumu sıfıra yazamaz; sekme
  hazırlanırken gerekiyorsa konum sonuç hazır olduğunda yeniden yüklenir.
- Durum gruplama tek geçiştir. Grid satırları `items(count = …)` ve görünür satır
  indeksleriyle çizilir; bütün liste için `chunked()` ara koleksiyonları üretilmez.

### Bilinçli arama davranışı değişiklikleri

1–2 harfte literal/NFKC eşleşme kullanılır; aşırı geniş alt-dizi/yazım hatası eşleşmesi
çalıştırılmaz. 3+ harfte fuzzy ayarı, kısaltmalar ve yazım hatası toleransı devam eder.
Boş Latin normalizasyonu (ör. Japonca veya sadece noktalama içeren sorgu) artık fuzzy
jokeri olup bütün kayıtları eşleştirmez. Yerel başlıklar literal/NFKC ile aranır.
Levenshtein hesabı uzunluk sınırı, dar bant, erken çıkış ve sorgu başına tekrar
kullanılan iki tamponla sınırlandırılmıştır.

## Doğrulama

`scripts/run_mylist_search_checks.sh`: **36 JUnit testi geçti.** Gerçek model, kimlik,
filtre/sıralama, indeks, gruplama ve coroutine akışı derlenip çalıştırıldı. Kapsam:

- hızlı yazmada yalnız son sorgunun hesaplanması ve hesabın toplayıcı iş parçacığından
  ayrı çalışması;
- bekleyen/dispatched ve hâlihazırda CPU'da çalışan eski sorgunun iptali;
- temizlemenin uzun bir typing-delay'i bile beklememesi;
- 2.603 kayıt, indeks yeniden kullanımı/geçersizliği, dil değişimi ve ID çakışmaları;
- boş yükleme önbelleğinin yeni kayıtlar geldiğinde kaydırma için hazır sayılmaması,
  kaynak/+18/dil kapsamı farklı önbelleğin gösterilmemesi;
- 21 sıralama seçeneği, filtreler, sonuç/grup tutarlılığı;
- kısa sorgular, CJK/noktalama, başlık varyantları, fuzzy tercihi, Türkçe locale;
- dar bantlı mesafenin 5.000 deterministik örnekte tam Levenshtein ile karşılaştırılması.

JVM kontrolü Android kaynakları/Compose değer API'leri ve ViewModel önbellek taşıyıcısı
İÇİN küçük derleme şimleri kullanır. Android arayüzü/IME/recomposition testi değildir.
Android CI test seçimine `com.kitsugi.animelist.ui.screens.mylist.*` eklendi.

```bash
# Normal Android geliştirme ortamı:
./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.mylist.*'

# SDK olmadan, Kotlin 2.0.21+, Java 17+, JUnit 4.13+, Hamcrest 1.3 ile:
KOTLINC=/path/to/kotlinc JUNIT_JAR=/path/to/junit.jar \
HAMCREST_JAR=/path/to/hamcrest-core-1.3.jar scripts/run_mylist_search_checks.sh
```

Bu ortamda Gradle 8.13 dağıtımı ağ/SSL engeli nedeniyle indirilemedi ve Android SDK/cihaz
bulunmuyor. Tam APK derlemesi, gerçek Compose çizimi ve telefon kare süreleri burada
**doğrulanmadı**; JVM çalışma süresi cihaz performans ölçümü olarak sunulmaz.

Cihazda son kontrol: 2.603+ kayıtla `r → re → rezero` hızlı yaz/sil; X/Temizle, filtre ve
sıralama değiştir; detaydan geri dön; Tümü/AniList/Bangumi ve +18 gizleme durumlarını dene.
Metin anında görünmeli, yalnız son sorgu listede yerini almalı, IME odağı korunmalıdır.
