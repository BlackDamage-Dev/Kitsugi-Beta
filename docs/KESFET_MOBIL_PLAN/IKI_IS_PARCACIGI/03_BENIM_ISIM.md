> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# Bu oturumdaki ajanın işi — İŞ 1: kaynaklar ve entegrasyon

**Durum:** Başlanmadı. Planlama, kod uygulaması değildir. Önce `00_IS_BOLUMU_VE_ENTEGRASYON` ve `01_ORTAK_SOZLESME` okunur; kullanıcı iş 2'yi ayrı kopyada başlatır.  
**Temel:** `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350`; mevcut kullanıcı değişiklikleri korunur. TV kapsam dışı.

## Sorumluluklar
- [ ] `../04_TASK_TEMEL_VE_GUVENCE.md`: durum/baseline, geri dönüş ve build/test ortamı. JDK/SDK/cihaz yoksa açık blokaj.
- [ ] `../05_TASK_MODEL_VE_KAYNAKLAR.md`: mobil-only source+category semantiğiyle 7 kaynak adaptörü; 8×4 kombinasyon testleri, TMDB anime filmi, Simkl legacy ve Bangumi REAL, AniList Kitsu fallback. Kaynak/ID/score ve cache korunur.
- [ ] `../06_TASK_GEZINME_VE_DURUM.md`: bağımsız pager, sekme/konum state'i, kaynak seçimi, eski navigasyon, pull-to-refresh ve alt bar; diğer ajanın görsel dosyalarını yazma.
- [ ] `../08_TASK_DETAY_VE_SAYFALAMA.md`: doğru kaynaklı detay/tümünü gör, gerçek endpoint ve devam/filtre uyumu, kişisel Simkl listelerinin başlığa bağlı semantiği, doğru geri dönüş. Gerekirse yanlış Tümünü gör'ü güvenli biçimde devre dışı tut.
- [ ] `../09_TASK_VERI_HATA_PERFORMANS.md`: hatalar, offline cache, request generation ve sınırlı ağ; tab geçişinde tekrar yedi kaynak çekme. Test edilmemiş hata/boş ayrımı uydurma.
- [ ] `../10_TASK_ENTEGRASYON_VE_ERISILEBILIRLIK.md`: kullanıcı ayarları/teması/yetişkin filtreleri, bildirim, arama, rastgele, takvim, okuyucu, kütüphane, mevcut skor ve kişisel rafları koru. Henüz karara bağlanmayan konularda `../12_RISK_VE_KARAR_KAYDI.md` gereği kullanıcıya sor.
- [ ] İş 2 teslimini yalnız izinli dosyalar, fonksiyon imzaları, bağımlılıklar ve lisans açısından incele. Eksik/hatalı kısmı karşı tarafa geri gönder; iki ajan aynı dosyayı düzenlemesin. Bileşen testleri/derleme kapısı geçmeden entegrasyona devam etme.
- [ ] İş 2 bileşenlerini mevcut ekranla bağla; kitapları mevcut global video hero kapısına zorla sokma; TV veya ortak sözleşme etkisi kaçınılmazsa dur/onay al. Onaylı görseli referans al ama mevcut işlevleri kaldırma.
- [ ] `../11_TASK_KABUL_VE_YAYIN.md`: 32 mod×tür, tür/kaynak kimliği, devam sayfaları, cihaz/gesture/erişilebilirlik/theme/ağ, performans ve geri dönüş. Hataları düzelt, geçmeyen kapılar varsa gerçek durumu bildir.

## Dosya sahipliği
İş 2'nin `MobileExploreVisuals.kt`, iki `mobile_explore_strings.xml`, `MobileExploreVisualsTest.kt`, `IS_2_TESLIM_RAPORU.md` dosyaları kesinlikle bu ajanın paralel geliştirme düzenleme kapsamı dışında. Bunlar ancak teslimden sonra kontrol edilerek alınır; yeniden çalışma gerekirse İş 2'ye gönderilir. Geri kalan iş için mevcut kaynak dosyaları yalnız gerektiğinde ve küçük değişikliklerle ele alınır. Test ve entegrasyon raporları ayrı dosyalara kaydedilir.

## İkinci denetimden ek entegrasyon kapıları
- [ ] `../13_IKINCI_KONTROL_VE_KAPILAR.md` bulgu 1–8 ve eylem/sahip tablosunu uygula; karara açık yerleri uygulamadan önce kullanıcıya sor. Kartın görsel sadakatini ölç, v1 sözleşmesini sessizce değiştirme.
- [ ] `rememberRetainedLazyListState` yükleme snapshot'ını ve tab×source geri dönüşünü, `KitsugiHorizontalMediaSection` başlığa bağlı kayıt durumunu ayrı doğrula; aynı isimli raflar karışmasın.
- [ ] `FullScreenMediaGridPage.kt` TR/EN kişisel liste başlık ayrımı ve `TmdbExploreContent.kt` Simkl devam/geçmiş ve planlanan/grid yönlendirmesini test et. Takvim, okuyucu, rastgele, bildirim ve Tümü kaynak retry/katlama atlanmasın.
- [ ] `AllSourcesExploreTest`, `ExploreMappingSourceScopeTest`, `HeroSelectionTest`, `HeroImageSelectionTest`, `HomeHeroPolicyTest` ve ilgili mevcut Compose UI testlerini koruyup uygun variantlarda regresyon kapısına al; source identity/adult/Kitsu fallback/TMDB/Bangumi/order/jump/cache/cancellation örnekleri korunmalı.
- [ ] FOSS/GMS görev adlarını `:app:tasks --all` ile gerçek ortamda bul; uygun debug build, unit ve cihaz testlerini çalıştır ve raporla. Çalıştırılmamış testi geçti yazma.

## İlk uygulama sonucu
Kaynak/kategori için yeni `mobile/MobileExploreMapping.kt` ve 13 izole test hazırlandı; uygulama ekranına bağlanmadı. Geçici Java/Kotlin ile sınırlı stand-in'li testler geçti; Android Gradle/SDK/cihaz kapıları bloklu. Ayrıntı ve kalan işler: `../15_IS_1_ILK_UYGULAMA_DURUMU.md`. Diğer ajanın dosyaları bekleniyor; katalog devamı endpoint kontrolü yapılmadan bağlanmaz.

## Devam sonucu
Saf ekran verisi, bağımsız mobil pager/scroll ve sınırlı sonuç grid'i hazırlandı; diğer ajanın dosyalarına veya etkin ekrana bağlanmadı. 18 izole saf kontrol geçti; 6 Android UI testi yazıldı ama derleme/cihaz ortamı yokluğunda çalıştırılmadı. `../15_IS_1_ILK_UYGULAMA_DURUMU.md` güncel durumdur.

## Sonraki uygulama sonucu
Mobil host slotları ve explicit finite-grid/izleme geçmişi navigasyon köprüsü eklendi. `AppRoot` ve `FullScreenMediaGridState` yalnız yeni mobil opt-in için değişti; varsayılan eski rota korunur, TV dosyası değişmedi. Kotlin stand-in'li 25 izole kontrol geçti; gerçek Android derlemesi ve 8 cihaz testi bloklu. Yeni Keşfet etkin değil; diğer ajanın görsel ZIP'i ve gerçek host bağlantısı bekleniyor. Ayrıntı `../15_IS_1_ILK_UYGULAMA_DURUMU.md`.

### 2026-11-08 — İş 2 teslimi alındı
5 dosyanın SHA-256 değerleri manifest ile eşleşti, çalışma ağacına eklendi. `MobileExploreVisualHost.kt` görselleri host slotlarına bağlayan, henüz etkin ekrana takılmamış bağımsız köprüdür; `MobileExploreVisualHostTest.kt` yazıldı fakat çalıştırılmadı. 25 stand-in'li izole kontrol yeniden geçti; Android build/48 cihaz testi hâlâ bloklu. Gerçek veri/ekran ve eylem entegrasyonu, endpoint/page 2 doğrulaması, cihaz görsel kontrolü ve regresyonlar açık.
