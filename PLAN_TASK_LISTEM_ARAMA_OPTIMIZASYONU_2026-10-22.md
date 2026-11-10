# PLAN / TASK — Listem arama donması ve optimizasyonu

**Tarih:** 2026-10-22  
**Depo:** f26901964-eng/Kitsugi-Beta  
**Çalışma dalı:** arena/4459b246-kitsugi-beta  
**Taban commit:** 26bb0444028e1209ffa7cf64d9c270a330471363  
**Durum:** Kod değişiklikleri ve JVM doğrulamaları tamamlandı; tam Android derlemesi ve cihaz kabulü bekliyor.

## 1. Sorun ve hedef

Kullanıcının 2.603 sonuç içeren Listem ekranında arama alanına harf yazması ciddi
kasma/donma oluşturuyordu. Arama filtreleri `remember` içinde senkron çalışıyor;
normalizasyon, fuzzy eşleştirme, sıralama ve gruplama ana iş parçacığını meşgul ediyordu.
Ham sorgunun sekme seçimi anahtarında olması ve gridde bütün listeye `chunked()`
uygulanması da gereksiz iş/tahsis üretiyordu.

Hedef: metin girişini liste hesabından ayırmak, yalnız en güncel sorguyu işlemek,
başlık hazırlığı/sıralamayı her harfte tekrarlamamak; filtre, kaynak, +18 ve kaydırma
konumu davranışlarını korumak.

## 2. Tamamlanan görevler

- [x] T01 — Listem arama zincirindeki ana iş parçacığı hesaplarını ve gereksiz tekrarları tespit et.
- [x] T02 — Ham metni başlıkta anında güncelle; içerik sayfasına tamamlanmış sorgu/sonuç aktar.
- [x] T03 — Sonuç hesabına 180 ms yazma beklemesi ekle; temizleme/filtre değişimi ve uygulanmış aynı sorguya bu beklemeyi ekleme.
- [x] T04 — `transformLatest` ile yeni girişte önceki işi hemen iptal et; filtre/gruplamayı `Dispatchers.Default` üzerinde çalıştır.
- [x] T05 — İndeks hazırlığı ve taramaya 32 kayıtta bir kooperatif iptal kontrolü ekle; sonuç ve grupları birlikte yayımla.
- [x] T06 — Başlık, İngilizce/Japonca varyantlar, NFKC anahtarları, metadata ve fuzzy kelime/baş harflerini önceden hazırla.
- [x] T07 — Sıralamayı sorgudan bağımsızlaştır; değişmeyen anlık görüntüde hazırlanmış indeksi yeniden kullan.
- [x] T08 — Kaynak listesi için referans eşitlikli Compose efekt anahtarı kullan; eşit içerikli yeni listelerde toplayıcının takılı kalmasını önle.
- [x] T09 — Ham sorgu yerine yalnız `isNotBlank()` değişimini sekme veri seçimine bağla; mevcut kaynaklar arası arama ve Bangumi sınırını koru.
- [x] T10 — 1–2 harfte geniş fuzzy taramayı kapat; boş Latin normalizasyonunu joker olmaktan çıkar; uzunluk/bant/erken çıkışla Levenshtein hesabını sınırla.
- [x] T11 — Durum gruplamasını tek geçişe indir; grid satırlarını `items(count = …)` ile üret, bütün listeyi `chunked()` ile kopyalama.
- [x] T12 — İndeks ve son tamamlanmış sonucu düz ViewModel önbelleğinde tut; kaynak/+18/dil kapsamı farklı önbelleği gösterme.
- [x] T13 — Asenkron yükleme satırları hazır olmadan kaydırma konumunu geri yükleme/yazma; geçici boş listenin kayıtlı konumu sıfırlamasını engelle.
- [x] T14 — Birleşik kütüphane yenilenirken de güncel +18 gizleme tercihini arama/sayfa girdilerine uygula.
- [x] T15 — İndeks ve coroutine akışı regresyon testlerini ekle; JVM kontrol betiğini hazırla ve mevcut filtre/gruplama testleriyle çalıştır.
- [x] T16 — Android CI test seçimine `com.kitsugi.animelist.ui.screens.mylist.*` ekle (CI çalıştırıldığı anlamına gelmez).
- [x] T17 — Teknik açıklama, test kapsamı ve doğrulanmayan noktaları `docs/mylist-search-performance.md` içinde belgele.

## 3. Doğrulama durumu

### Geçen kontroller

- **36 JUnit testi geçti:** gerçek model, kimlik, filtre/sıralama, gruplama, indeks ve coroutine akışı.
- Hızlı yazmada yalnız son sorgunun hesabı; işlem toplayıcı iş parçacığından ayrı çalışıyor.
- Bekleyen/dispatched ve hâlihazırda çalışan eski CPU taramasının iptali.
- Temizleme, filtre/fuzzy tercih değişimi, aynı sorguyla veri güncelleme ve detay dönüşü senaryoları.
- 2.603 kayıt, 21 sıralama seçeneği, başlık varyantları, CJK/noktalama, Türkçe locale ve kaynak ID çakışmaları.
- Dar bantlı mesafenin 5.000 deterministik örnekte tam Levenshtein ile karşılaştırılması.
- Yeni kaynak verisi geldiğinde boş yükleme önbelleğinin kaydırma için hazır sayılmaması ve kapsam dışı önbellek güvenliği.
- Değişen dört mevcut UI/ViewModel Kotlin dosyasında sözdizimi kontrolü geçti. Bu, Android API/type doğrulaması değildir.
- `bash -n scripts/run_mylist_search_checks.sh` ve `git diff --check` geçti.

JVM kontrol betiği Android kaynakları, Compose değer API'leri ve ViewModel önbellek
taşıyıcısı için küçük derleme şimleri kullanır. Gerçek Compose çizimi, IME veya gerçek
ViewModel/Android yaşam döngüsü bu testlerle doğrulanmış değildir.

### Bekleyen görevler / kabul

- [ ] T18 — SDK ve ağ erişimi olan ortamda gerçek Android birim testlerini çalıştır:

  ```bash
  ./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.mylist.*'
  ```

- [ ] T19 — Debug APK derle:

  ```bash
  ./gradlew :app:assembleFossDebug
  ```

  Windows PowerShell'de `./gradlew` yerine `.\gradlew.bat` kullanılabilir.

- [ ] T20 — 2.603+ kayıt bulunan cihazda hızlı `r → re → rezero` yaz/sil; klavye odağını, son sorgu sonucunu ve donma/kare sürelerini kontrol et.
- [ ] T21 — X/Temizle, durum/tür/puan/yıl/favori filtreleri, tüm sıralamalar ve fuzzy tercih aç/kapa kabulünü yap.
- [ ] T22 — Tümü/AniList/Bangumi kaynakları, +18 gizleme, detaydan geri dönüş, derin kaydırma konumu ve süreç yeniden oluşturma kabulünü yap.

**Engel:** Bu ortamda Gradle 8.13 dağıtımı ağ/SSL engeli nedeniyle indirilemedi;
Android SDK/cihaz bulunmuyor. Tam APK derlemesi ve telefon performansı doğrulanmadı.
Paket APK içermez; kaynak kodu güncelleme paketidir.

## 4. Değişen / yeni proje dosyaları

1. `.github/workflows/android-build.yml`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchIndex.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchPipeline.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchState.kt`
10. `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchIndexTest.kt`
11. `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchPipelineTest.kt`
12. `docs/mylist-search-performance.md`
13. `scripts/run_mylist_search_checks.sh`
14. `PLAN_TASK_LISTEM_ARAMA_OPTIMIZASYONU_2026-10-22.md` (bu dosya)

## 5. Paketi uygulama

1. Mevcut projeyi yedekle; taban commit bilgisini dikkate al. Daha yeni kodlarda dosyaları körlemesine ezmek yerine farkları birleştir.
2. ZIP içindeki proje dosyaları depo köküyle aynı göreli yolları taşır; mevcut proje köküne uygula. Yalnız bu değişen/yeni dosyalar teslim edilir, deponun tamamı değildir.
3. `.github/` dahil dosya yollarını koru. `paket-bilgileri/` yalnız teslim/sağlama bilgilerini içerir, projeye eklenmesi zorunlu değildir.
4. Yukarıdaki gerçek Gradle testleri ve APK derlemesini çalıştır; cihaz kabul görevlerini tamamla.

SDK olmadan sınırlı JVM kontrolünü tekrar çalıştırmak için Java 17+, Kotlin 2.0.21+,
JUnit 4.13+, Hamcrest 1.3 ve Kotlin kurulumundaki `kotlinx-coroutines-core-jvm.jar`
gereklidir:

```bash
KOTLINC=/path/to/kotlinc JUNIT_JAR=/path/to/junit.jar \
HAMCREST_JAR=/path/to/hamcrest-core-1.3.jar scripts/run_mylist_search_checks.sh
```

Paket API anahtarı, yerel ayarlar, keystore, `.git`, SDK/JDK, bağımlılık önbelleği veya
büyük veri deposu içermez; bunlar mevcut proje/geliştirme ortamında kalır.
