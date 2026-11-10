# 10 — Görev G: Mevcut özellikler, ayarlar ve erişilebilirlik
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 06–09  **Bitiş kapısı:** tüm korunacak özellikler işaretli.

## Özellik koruma listesi
- [ ] `ExploreScreen` rastgele içerik, bildirim, arama/kaynak seçimi, yayın takvimi, manga okuyucu, sıralama/kategori kısayolları: yeni ekranda nerede olduklarını açıkça tanımla, görünür ve çalışır tut. Rastgele seçimin kaynak/tür kapsamı (mevcut karma davranış veya aktif sekme) kullanıcı kararı; sessiz değiştirme yok.
- [ ] Simkl kişisel "devam"/"planlanan" listeleri ve TMDB bağlamındaki Simkl listeleri: oturum, içerik tipi ve çıkış/yeniden giriş senaryosu; başlık/locale ile kişisel listelerin yanlış saptanması önlenir; boşta sessiz yanlış içerik yok.
- [ ] `tmdbModernHomeEnabled` açık/kapalı: mevcut iki ekran yolundan hangisinin yeni tasarımda yer alacağına kullanıcıyla karar ver; ayarı yok sayma veya silme. Aynı ayarın detay ekranlarına bağlantısı vardır. `showAnimeLogos`, roman ayrımı, score/gizleme, dil, adult ve bulanıklaştırma korunur.
- [ ] `values`/`values-en` kaynaklarına Türkçe/İngilizce sekme, boş/hata, kaynak ve erişilebilirlik etiketleri ekle; hardcoded eski raf başlıklarını yeni UI'da çoğaltma. Düşük API ve mevcut desteklenen cihaz düzeninde font büyütme, satır taşması, RTL ve uzun kaynak adlarını kontrol et.
- [ ] Touch target 48dp; TalkBack'te sekme rolü + seçili, kaynak + sonuç + tıklama açıklaması; only-swipe navigasyon olmasın. Görünüm/animasyon ayarları sistemin azaltılmış hareket tercihiyle çatışmasın. Telefon/tablet için portre/landscape ve inset testleri yap.
- [ ] Kart ve liste puanları kaynağa ait; puan gizleme/formatı ve görsel provider'a ait etiket; bunlar global birleştirme yapılmadan korunur.

## Test ve çıkış
Bu listenin her maddesi eski-yeni ekran karşılaştırması, TR/EN ve ekran okuyucu test kaydıyla doğrulanır; belirsiz ürün kararı olmadan 'tamamlandı' denmez. Ekran değişimi ile etkisiz olması beklenen diğer sayfalarda paylaşılan bileşen ve ayar regresyonu kontrol edilir; TV özelliği üzerinde çalışma yapılmaz.
