# Plan Task — Listem Kartlarında Köşe Rozetleri

**Tarih:** 2026-10-23  
**Kapsam:** Listem sayfasındaki poster kartlarında durum ve puan rozetlerinin görselleri daha az kaplayacak biçimde köşelere yapıştırılması.

## İstek
Ekran görüntüsünde işaretlenen durum ve puan rozetleri, kaynak rozetleri gibi posterin en dış köşelerine oturmalı. Rozetlerle poster kenarları arasında boşluk kalmamalı; görseller daha belirgin görünmeli.

## Kapsam içindeki dosya
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaEntryCard.kt`

## Uygulama
- Durum rozeti sağ üstte; posterin üst ve sağ kenarlarına boşluksuz hizalanır. Yalnızca görsele bakan iç köşesi yumuşak bırakılır.
- Puan rozeti sağ altta; posterin alt ve sağ kenarlarına boşluksuz hizalanır. Yalnızca iç köşe yumuşak bırakılır.
- Rozet metninin iç boşlukları azaltılarak poster üzerinde kapladığı alan küçültülür.
- Kaynak rozetlerinin yeri ve davranışı değiştirilmez.

## Kabul ölçütleri
- Durum rozetinin arka planı posterin sağ üst sınırına, puan rozetinin arka planı posterin sağ alt sınırına kadar uzanır.
- Poster kenarı ile rozet arasında dış boşluk/padding görünmez.
- Rozet metinleri tek satır kalır ve taşma mevcut ellipsis davranışıyla sınırlanır.
- Kaynak rozetleri ve diğer kart düzenleri etkilenmez.

## Doğrulama
- `git diff --check` başarılı.
- Gradle derlemesi ortamda Java/JDK bulunmadığı için çalıştırılamadı; cihazda görsel kontrol derleme sonrasında yapılmalı.
