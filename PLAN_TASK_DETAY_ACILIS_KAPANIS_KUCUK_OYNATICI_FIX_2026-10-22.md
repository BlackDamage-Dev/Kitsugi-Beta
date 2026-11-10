# PLAN / TASK — Detay Açılış/Kapanış Müzikleri Küçük Oynatıcı ve Açılma Animasyonu Düzeltmesi

**Tarih:** 2026-10-22  
**Dal:** `arena/8b017ea7-kitsugi-beta`  
**Kapsam:** Medya detay sayfasındaki Videolar sekmesi; fragman, açılış (OP) ve kapanış (ED) satır içi video oynatıcıları.

## Kullanıcı isteği ve belirti

Açılış ve kapanış müzikleri, fragmanla aynı şekilde satıra dokunulduğu anda küçük oynatıcıda çalışmalıdır. Oynatıcı, ilgili satırın altında fragmandaki gibi aşağı doğru animasyonla açılmalıdır. Kullanıcıyı doğrudan harici YouTube aramasına gönderen davranış kaldırılmalıdır.

## Doğrulanan kök neden

`KitsugiMediaVideosTab` içindeki satır davranışı `videoUrl` alanının dolu olmasına bağlıydı. Sağlayıcı yalnızca şarkı etiketi verdiğinde (`videoUrl == null` veya boş), satır küçük oynatıcıyı açmak yerine harici YouTube aramasına yönlendiriliyordu. Ayrıca OP/ED oynatıcı alanı fragmanın kullandığı ortak `KitsugiDetailReveal` açılma geçişi yerine farklı bir ölçü değişimi yapısı kullanıyordu.

## Uygulanan düzeltmeler

- [x] Bütün video satırları, bağlantı olsun ya da olmasın aynı aç/kapat davranışını kullanıyor.
- [x] Bağlantısı olmayan OP/ED etiketleri uygulama içinde YouTube aramasıyla gerçek bir video kimliğine çözülüyor.
- [x] Çözülen video kimliği, fragmanın kullandığı `KitsugiYouTubePlayer` bileşenine aktarılıyor.
- [x] Doğrudan medya/YouTube bağlantısı olan satırlar mevcut `ThemePlayerContainer` akışını kullanmaya devam ediyor.
- [x] OP/ED oynatıcı alanı, fragmanla aynı `KitsugiDetailReveal` animasyonuyla satırın altına aşağı doğru açılıyor.
- [x] Kapanış animasyonunun ölçülebilir alanı korunurken eski oynatıcı motoru hemen kaldırılıyor; hızlı satır değişiminde iki ağır oynatıcı aynı anda canlı kalmıyor.
- [x] Arama veya oynatma çözümü başarısız olursa küçük oynatıcı içinde **Yeniden Dene** ve yalnızca yedek seçenek olarak **YouTube'da ara** eylemleri gösteriliyor.
- [x] Başarılı YouTube arama sonuçları sınırlı bellek önbelleğinde tutuluyor; aynı satırı yeniden açmak gereksiz ağ isteğini önlüyor.
- [x] Arama ayrıştırıcısı kanal ve oynatma listesi sonuçlarını atlayıp gerçek `videoRenderer` sonucunu seçiyor; bozuk/eksik veride güvenli biçimde `null` dönüyor.

## Değişen ve eklenen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/trailer/InAppYouTubeExtractor.kt`
   - İptal edilebilir YouTube arama isteği, sonuç önbelleği ve ilk video kimliğini çözen API eklendi.
2. `app/src/main/java/com/kitsugi/animelist/data/trailer/YouTubeSearchResultParser.kt`
   - Android veya JSON kütüphanesi bağımlılığı olmayan, ayrı test edilebilir `ytInitialData` ayrıştırıcısı eklendi.
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
   - Satır dokunma davranışı, küçük oynatıcı çözümleme akışı, hata/yeniden deneme arayüzü ve ortak aşağı açılma animasyonu düzeltildi.
4. `app/src/test/java/com/kitsugi/animelist/data/trailer/YouTubeSearchResultParserTest.kt`
   - İlk gerçek video sonucu, kanal/oynatma listesi eleme, kaçışlı metin ve süslü parantezler, geçersiz video kimliği ile bozuk/eksik veri durumları için birim testleri eklendi.
5. `PLAN_TASK_DETAY_ACILIS_KAPANIS_KUCUK_OYNATICI_FIX_2026-10-22.md`
   - Bu plan/task ve doğrulama raporu.

## Yapılan kontroller

- [x] Üretim ayrıştırıcısı Kotlin 2.0.21 ile bağımsız derlendi ve 6 çalışma zamanı kontrolünün tamamı geçti.
- [x] Değişen 4 Kotlin kaynak/test dosyası tree-sitter Kotlin sözdizimi kontrolünden hatasız geçti.
- [x] `git diff --check` temiz tamamlandı.
- [x] ZIP arşivi 5 dosya içeriyor; `unzip -t` bütünlük kontrolü hatasız tamamlandı.
- [ ] `:app:compileDebugKotlin` tamamlanamadı: Gradle wrapper, `https://services.gradle.org/distributions/gradle-8.13-bin.zip` dağıtımını sandbox ağ kısıtı nedeniyle indiremedi (SSL el sıkışması sonlandı).
- [ ] Gerçek Android cihazda YouTube/AnimeThemes oynatma ve animasyon kontrolü bu ortamda yapılamadı.

## Beklenen kullanıcı davranışı

1. Kullanıcı herhangi bir OP/ED satırına dokunur.
2. Satır etkin görünüme geçer ve küçük oynatıcı alanı satırın altında aşağı doğru animasyonla açılır.
3. Doğrudan bağlantı varsa mevcut motor; yalnızca etiket varsa önce uygulama içi YouTube çözümlemesi devreye girer.
4. Video, fragmanla aynı küçük oynatıcı deneyiminde başlar; harici uygulamaya yönlendirme yalnızca kullanıcının hata ekranındaki yedek seçeneği açıkça seçmesiyle olur.
