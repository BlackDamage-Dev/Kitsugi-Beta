# Plan / Task — İzle sayfası kaynak paneli "tek bar" düzeni

Tarih: 2026-11-04
Temel commit: a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe
Ekran: `ui/screens/stream/StreamScreenContent.kt` (Kaynak seçim paneli / `AddonChipsRow`)

## Amaç
İzle (kaynak seçim) sayfasında kaynak panelinin başlık bloğu — başlık + özet,
"Sıralama" etiketi + çipleri, "Eklentiler" başlığı + ayarlar, "Hataları yenile",
tam genişlik arama ve "Yalnızca sonuç verenler" filtresi — dikeyde çok yer kaplıyor
ve kaynak listesini daraltıyordu. Tüm bu kontrolleri TEK bir kompakt kontrol barına
indirgeyip listeye maksimum alan bırakmak; daha kullanıcı dostu, derli toplu bir
görünüm sağlamak.

## Tamamlanan görevler
- [x] Tetikleyici kartı tek satıra indir (kart + altındaki ayrı sıralama satırı birleşti).
- [x] Panelde başlık + özet + sıralama + arama + yenileme + ayarlar + sonuç filtresini
      48 dp'lik TEK kontrol barında topla (`SourcePickerSheet`).
- [x] Sıralamayı 3 moda çıkar: Kaynak sırası / Kalite / Boyut (`StreamSortMode`).
      Sıralama çipi mevcut modu gösterir, dokununca 3 seçenekli menü açılır.
- [x] Boyut sıralaması için `StreamInfoResolver.resolveSizeBytes` ekle (önce gerçek
      ölçüm `Content-Length`, yoksa dosya/ad başlığındaki açık boyut etiketi; kanıt
      yoksa 0 → sıralamanın sonu).
- [x] Arama açılınca bar aynı yükseklikte arama alanına dönüşsün (`BasicTextField`);
      ikinci bir satır açılmasın. Otomatik odak (`FocusRequester`).
- [x] Yenile düğmesi: hata varsa rozetle "Hataları yenile (n)" ve yalnızca hatalıları,
      yoksa "Tüm kaynakları yenile" ve tümünü yeniden denesin.
- [x] ⋮ taşma menüsü: "Yalnızca sonuç verenler" (aktifken bar üzerinde nokta) +
      "Eklenti ayarları".
- [x] Hatalı / doğrulama bekleyen eklenti satırlarına satır içi yeniden dene / doğrula
      eylemi ekle (`SheetRowAction`).
- [x] Kaynak listesini barın hemen altında başlat; satır dolgusunu 14→10 dp'ye indir.
- [x] Yeni metinlere Türkçe (`values/`) ve İngilizce (`values-en/`) kaynak ekle.
- [x] Tema renklerini kullan (`KitsugiColors`); hata durumunda `AccentRed`.

## Değişen dosyalar
| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt` | Tek-bar panel, 3 modlu sıralama, arama/yenile/⋮ menü, satır içi eylemler |
| `app/src/main/java/com/kitsugi/animelist/data/repository/StreamInfoResolver.kt` | `resolveSizeBytes` (boyut sıralaması) |
| `app/src/main/res/values/strings.xml` | Yeni TR metinler (stream_sources_title, stream_sort_*, stream_only_with_results, …) |
| `app/src/main/res/values-en/strings.xml` | Yeni EN karşılıklar |
| `docs/stream-kaynak-tek-bar-onizleme.html` | Eski/Yeni karşılaştırmalı görsel önizleme |
| `PLAN_TASK_STREAM_KAYNAK_TEK_BAR_2026-11-04.md` | Bu belge |

## Kontroller
- [x] Değişen Kotlin dosyalarında sözdizimi / kapsam / import incelemesi.
- [x] Süslü parantez ve parantez dengesi kontrolü.
- [x] Türkçe ve İngilizce XML parse kontrolü; `stream_` kaynak adı benzersizliği (duplike yok).
- [x] `git diff --check` temiz.
- [x] Yeni dizelerin her iki dilde de mevcut olduğu doğrulandı.
- [ ] Android derleme: ortamda Java/Android SDK yok ve Google Maven ağ erişimi kapalı;
      yapılmadı. Hedefte `gradlew assembleDebug` ile derlenmeli.
- [ ] Cihaz/emülatör: dikey, yatay, tablet görsel kontrolü.
- [ ] Arama, sıralama menüsü, hataları yenile, yalnızca sonuç verenler, eklenti ayarları
      ve satır içi doğrula/yeniden dene etkileşimlerinin cihazda testi.
- [ ] Ekran döndürülünce sıralama modu / arama / filtre durumunun korunması (rememberSaveable).

## Paketi uygulama
ZIP içindeki `app/` ve `docs/` yolları depo köküne göredir.
Önce mevcut dosyalarını yedekle; dosyaları aynı yollara kopyala.
Bu paket tam proje veya APK değildir. `strings.xml` dosyaları tam dosyalardır;
hedef projede sonradan farklı metin değişiklikleri varsa körlemesine üzerine
kopyalamak yerine karşılaştırarak birleştir.

## Not
Depodaki panel, kullanıcı ekran görüntüsündeki sürümden biraz daha eskiydi. Panel
tek bara indirilirken görüntüdeki TÜM kontroller (sıralama, arama, hataları yenile,
eklenti ayarları, yalnızca sonuç verenler) bu bara taşındı; böylece bu depodan
derlendiğinde kompakt ve tam özellikli hâl elde edilir.
