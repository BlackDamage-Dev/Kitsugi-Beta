# Okuyucu geliştirme planı ve görev durumu

**Dal:** `arena/f7ab753e-kitsugi-beta`  
**Taban:** `b78cbc4cdc39808cb0ec475026edef6b464b71ed`  
**Kapsam:** Manga/manhwa/manhua ve yerel roman okuyucusu; çevrimiçi novel kaynakları kapsam dışıdır.

Ayrıntılı açık kaynak kaynak kodu karşılaştırması ve özellik matrisi: `OKUYUCU_ACIK_KAYNAK_ARASTIRMA_2026-11-06.md`.

## Çalışma ağacına uygulanmış işlerin özeti

- [x] Görsel okuyucu: Webtoon boşluğu, otomatik kaydırma, sayfa numarası, ön yükleme, ses tuşları, dokunma düzenleri ve kısa/uzun basma için özel 3×3 ızgara.
- [x] Okuma yönü, çift sayfa/tek kapak, seri yönü tercihi, bölüm geçişleri ve ilerleme kaydetmeyi oturumluk durdurma.
- [x] Temkinli kenar kırpma, isteğe bağlı geniş görüntü döndürme, renk matrisi kontrolleri, sayfa kenarı renkli arka plan ve Webtoon zoom tercihi.
- [x] Yerel TXT, EPUB, FB2/FB2.ZIP ve görsel CBZ/ZIP, sınırlı CBR/RAR ve PDF açma yolları.
- [x] Yerel metin okuyucularında tipografi, tema, arama, çoklu yer imi/not, dışa aktarma ve belgeye bağlı yer imi yedeği.
- [x] TTS ses/perde/hız, duraklatma, parça geçişleri ve uyku zamanlayıcısı; otomatik kaydırma ve ayrı zamanlayıcı.
- [x] TXT/FB2 için tahmini ilerleme, konuma gitme, arama vurgusu ve geri alınabilir oturumluk birebir metin değiştirme; EPUB bölüm gezintisi ve isteğe bağlı otomatik sonraki bölüm.
- [x] Mobil ve TV görsel okuyucuda ortak ayar entegrasyonu; TV'de yerel çizgi roman ve PDF açma girişleri.

Bu işaretler kaynak kodu değişikliklerini ifade eder; Android derleme veya cihaz testi tamamlandığı anlamına gelmez.

## Kalan işler ve doğrulamalar

- [ ] Android SDK ve Gradle dağıtımıyla uygulamanın tamamını derle; Room/KSP, Compose, Junrar bağımlılığı ve tüm varyantları doğrula. Bu ortamda Gradle indirmesi SSL hatasıyla durdu.
- [ ] Telefon ve TV üzerinde SAF, yerel biçimler, WebView, PdfRenderer, CBR, TTS, lifecycle, ses tuşları ve D-pad etkileşimini test et.
- [ ] Görsel okuyucuda geniş sayfayı çift sayfada otomatik tek gösterme ve ikiye bölmeyi uygula; RTL, ilerleme, ön yükleme ve yakınlaştırmayı test et.
- [ ] EPUB CFI ve seçili metne bağlı kalıcı vurgulama/anotasyonları uygula; konumların tipografi değişimine dayanıklılığını doğrula.
- [ ] DRM, şifreli/solid/çok parçalı CBR, MOBI ve gelişmiş EPUB yayıncı stili gibi desteklenmeyen biçim/kapsamları ürün gereksinimlerine göre ele al.
- [ ] Gelişmiş metin değiştirme/regex, tam seri profilleri, arka plan TTS medya kontrolü, sözlük/OCR ve sunucu eşitlemesi dahil araştırma matrisindeki diğer uygun özellikleri değerlendir ve uygula.
- [ ] Uzun Webtoon görselleri, görüntü dönüşümü/bellek, çift sayfa/RTL, ilerleme yarışları ve erişilebilirlik için cihaz üzerinde regresyon testleri yap.

## Test özeti ve sınırlamalar

Saf Kotlin/JVM politika testleri, EPUB ayrıştırma/arama testleri, yerel arşiv testleri ve CBR için Junrar 8.1.2 kaynaklarıyla gerçek JVM arşiv testleri çalıştırıldı. Android SDK/Gradle derlemesi ve cihaz testi yapılamadı; sözdizimi kontrolleri derlemenin yerine geçmez. PDF ve EPUB'un destek sınırları, test sonuçları ve ayrıntılı kalan işler araştırma raporundadır.
