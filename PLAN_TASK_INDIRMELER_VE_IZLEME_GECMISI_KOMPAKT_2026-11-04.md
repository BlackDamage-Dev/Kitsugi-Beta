# Plan / Task: İndirmeler ve İzleme Geçmişi düzeni

Tarih: 4 Kasım 2026
Referans: v2.4.413 (`4a780c0142da4542284e30430859588af48ea2de`)

## İstek
- İndirmeler ve İzleme Geçmişi sayfa kontrollerini üç çizgi düğmesinden açılan, derli toplu bir alt panelde sunmak.
- Geçmiş istatistiklerini ve indirmelerin depolama özeti ile sekmelerini daha az yer kaplayacak şekilde düzenlemek.

## Yapılanlar
- Geçmişte sıralama, gruplama, filtreler, arama, çoklu seçim ve temizleme seçenekleri alt panele taşındı; özet tek satıra indirildi.
- İndirmelerde sekmeye özgü filtre, sıralama, görünüm ve toplu işlem seçenekleri alt panele taşındı; depolama özeti ve sekme şeridi sıkıştırıldı.
- Depolama özetinin kaydırma ile görünürlük davranışı ve liste test etiketleri korundu.
- Yeni metinler Türkçe ve İngilizce kaynaklara eklendi.

## Doğrulama ve kalan işler
- [x] TR/EN kaynak eşliği kontrolü geçti.
- [x] Değişen Kotlin dosyalarında tree-sitter sözdizimi kontrolü geçti; `git diff --check` temiz.
- [ ] Android derlemesi ve cihaz/emülatör testi: ortamda JDK ve Android SDK bulunmadığından çalıştırılamadı.
- [ ] Etkileşim denetimi: araç çok sayıda çözümlenmemiş kayıt raporladı; denetim ve etkileşim defteri ayrıca gözden geçirilmeli.
- [ ] Farklı ekran boyutlarında, TV odağında, alt panel kaydırmasında ve indirme/çoklu seçim işlemlerinde görsel ve işlevsel doğrulama yapılmalı.

## Paketteki dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsImagesTab.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- Bu plan/task dosyası.
