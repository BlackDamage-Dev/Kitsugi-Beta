# 11 — Görev H: Mobil kabul, kontrollü geçiş ve geri dönüş
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 04–10 + açık kararların çözümü.

## Otomatik test kapısı
- [ ] CI'da `./gradlew :app:testDebugUnitTest` ve `./gradlew :app:assembleDebug` veya repo/variant gereği doğrulanmış eşdeğer komutlar. İlk aşamada test filtreleri, son aşamada tüm ilgili unit testleri + build. Ağ bağımlı/flaky testler açıkça not edilir, sonuç uydurulmaz.
- [ ] `:app:connectedDebugAndroidTest` uygun emulator veya cihazda; yeni mobil UI testleri + mevcut hero testleri. Eğer cihaz yoksa bloklu. Cihaz, API, komut, sonuç ve loglar saklanır (gizli anahtar/kişisel veriler çıkarılır).

## Kabul matrisi
- [ ] 8 kaynak modu × 4 sekme = 32 kombinasyon: doğru kaynak, tür, gerçek raf, destek yok/boş/hata/cache ve detay; destek olmayan yerin boş olması başarıdır, yanlış kategori başarısızlıktır.
- [ ] 7 kaynaklı Tümü: bir hata/sürede yalnız o kaynağın durumu, diğerleri görünür; retry tek kaynak. Hızlı sekme ve kaynak geçişi, yavaş ağ/offline/geri dönüş, boş cache, adult on/off ve eski cache.
- [ ] 7 kaynak için en az bir gerçek raf → tümünü gör → 2. sayfa/sonlu gösterim → filtre/sıralama → detay/geri. TMDB anime filmi, Simkl kişisel listeler ve farklı uç nokta, Bangumi REAL, AniList Kitsu fallback ve kaynak kimliği ayrı kontrol.
- [ ] Görsel onay: referans kompozisyonuna yakın telefon, TR/EN, koyu/açık/AMOLED ve custom gradient; büyük font/TalkBack, raf vs sayfa swipe, tablet/landscape, offline, yeni/kayıtlı kullanıcı, bildirim/okuyucu/takvim/arama/rastgele/listeler.
- [ ] Performans: eski ve yeni aynı koşulda ölçülür. Belirgin regresyon varsa kapı kapalı; eşik değerleri baseline sonrası ekipçe belirlenir.

## Yayın/geri dönüş
- [ ] Eski mobil akışın build-time veya mevcut iç kontrollü rollout anahtarıyla güvenli dönüşü test edilmiş olsun; veri/cache/ayarların eski kodla okunabilirliği doğrulansın. Sadece kullanıcı onayı + tüm zorunlu kapılar sonrası yeni akış varsayılan olur.
- [ ] Hata kaydı: senaryo, kaynak/tür, sürüm, cihaz/OS, beklenen/gerçek, log, çözüm ve tekrar test. Kişisel veri ve token silinir. Kritik hata → dağıtımı durdur, eski ekranı geri al, veri kaybı şüphesi varsa ek yazma/migration yapma.
- [ ] Kullanıcıya yapılan/değişmeyen alanlar, çalıştırılan/çalıştırılmayan testler ve sınırlamalar dürüst raporlanır. TV çalışması yapılmaz.
