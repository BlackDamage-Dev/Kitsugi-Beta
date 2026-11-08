# 21 — Onaylı görsel ve mobil kod karşılaştırması

2026-11-08 · Yalnız mobil · Referans: [Onaylanan Keşfet görseli](https://drive.google.com/file/d/12-pwLJS1rRMvVUPvCkuwB3c0I6fa52Q0/view). Bu, kaynak koduna karşı elle yapılmış kontroldür; cihaz renderı veya piksel eşitliği testi değildir.

## Saptanan sapmalar ve bu turda yapılanlar

- Referansta poster üstünde **yıldız/puan rozeti veya liste-durum rozeti yok**. Eski yeni `MobileExplorePosterCard` bu rozetleri çiziyordu; ikisi de bu mobil rafın görünür katmanından kaldırıldı. Geri kalan afiş, alt gradient ve gerçek tür/yıl korunuyor. Başlık, liste durumu ve gösterimi gizlenmemiş puan ekran okuyucu semantiğinde sağlanıyor; listede olma bilgisi artık bu kartın görsel yüzeyinde görünmez. Puan ayarı gizliyse semantikte de gösterilmez. Kart tıklama/uzun basma callback'leri ve kaynak veri modeli değiştirilmedi. Bu henüz test edilmiş gerçek Keşfet işlevi değildir. Ayrı finite grid bu poster tasarımını kullanmaz ve mevcut kart ayarlarını korur.
- Referansta hero ile ilk poster yaklaşık 36 dp (768px görüntü yaklaşık 384dp kabul edildiğinde). Eski host alt hero boşluğu 15dp ve raf eylem alanı 48dp nedeniyle belirgin daha yüksek bir boşluk öngörüyordu. Host hero alt boşluğu 5dp, raf başlığı en az 26dp, sonraki raf boşluğu 14dp olarak yaklaştırıldı. Üst satır/kaynak/sekme yüksekliğinde de referansa yaklaşmak için minimumlar küçültüldü. Bunlar **ölçülmemiş Compose tasarımıdır**, gerçek font, sistem inseti ve slot ikonları toplam yüksekliği etkiler.
- Kompakt görsel alanlar Android/Compose otomatik dokunma genişletmesine dayanabilir; gerçek cihazda erişilebilir 48dp hedeflerin, poster ve sekmelerle çakışmamasının ve büyük yazı ölçeğinin doğrulanması zorunludur. Erişilebilirlikten ödün verildiği ortaya çıkarsa yerleşim ve hitbox beraber düzeltilmeli.

## Görselde ve kodda karşılaştırılan öğeler

| Bölge | Referans ve mevcut hazırlık | Açık kontrol |
|---|---|---|
| Üst bölüm | KITSUGI, büyük Keşfet, sağda kaynak seçici; header action slotu gerçek eylemler için boş | Gerçek üst bar ve ikonlar bağlanmadı; tekrarlı bar/inset, konum ve davranış doğrulanmadı |
| Sekmeler | Diziler / Filmler / Anime / Manga ve seçili alt çizgi; pager altyapısı var | Gerçek ekranda geçiş, uzun metin ve dokunma alanı test edilmedi |
| Vitrin | Yaklaşık 2,3:1, yanal ~13dp boşluk, alt metin ve İncele; gerçek görselden yükleme | Gerçek seçilmiş içerik, crop, tint, font ve tıklama çalışır ekranla doğrulanmadı |
| Raflar | Başlık + kaynak + Tümünü gör; 84×135dp, yaklaşık 7dp kart aralığı ve yatay snap; görünür skor/durum rozetleri yok | Gerçek veri, kapak, başlık, uzun basma ve ritim/snap cihazda doğrulanmadı |
| Alt bar | Görselde dört rota ve seçili Keşfet | Yeni host gerçek AppRoot içine bağlanmadı; gerçek alt barın stili/konumu/animasyonu incelenip test edilmeli |

Referanstaki poster başlıkları resmin kendisinde; üretim kodu var olmayan başlık veya artwork uydurmaz. Referanstaki görseller katalogda bulunmadığında aynı görüntü dosyaları beklenemez; hedef düzen/stildir.

## Testler ve sınırlamalar

- 32/32 izole Kotlin mapping/navigation/score/label kontrolü geçti. Bu koşum Compose'u veya cihaz görünümünü **test etmez**.
- 23/23 Kotlin dosyası tree-sitter sözdizimi kontrolünden geçti; `git diff --check` geçti. Bu, gerçek derleme/type-check değildir.
- Rozetlerin yokluğu ve ekran okuyucu semantiği için mevcut cihaz testleri güncellendi; kart ölçü/boşluk ve hero-ilk raf aralığı için iki cihaz testi eklendi. **56 cihaz testi yazılı; 0 çalıştırılmış.** Testlerin kendileri de Android derlemesinden geçmemiştir.
- Android SDK/cihaz bulunmuyor; önceki Gradle dağıtım indirmesi ağda SSL hatası verdi. Gerçek screenshot/overlay, büyük yazı ve dokunma testi, tema, alt bar, cihaz uyumu ve görsel kabul hâlâ açık.
- `MobileExploreVisualHost` gerçek `ExploreScreen` tarafından çağrılmıyor. Entegrasyon, refresh/retry, tüm mevcut eylemler ve gerçek Tümünü gör sayfalaması bitmeden ekran hazır değil.
