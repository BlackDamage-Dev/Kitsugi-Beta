> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# Mobil Keşfet — İki yapay zekâ ile çakışmasız iş bölümü

**Durum:** Plan; uygulama kodu henüz değiştirilmedi. **Temel:** `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350` ve bunun üzerindeki, kullanıcının koruduğu mevcut değişiklikler.  
**Tasarım:** [Kullanıcının onayladığı görsel](https://drive.google.com/file/d/12-pwLJS1rRMvVUPvCkuwB3c0I6fa52Q0/view).  
**Kapsam:** Telefon/tablet Keşfet. TV ekranına, TV dosyalarına, TV navigasyonuna veya TV'ye özel testlere dokunulmaz. Ortak davranışta TV dahil başka tüketiciyi etkileyecek zorunlu değişiklik çıkarsa dur ve kullanıcıyla kapsamı görüş.

## İş paylaşımı

| İş | Sorumlu | Ne üretir? | Dokunmaması gerekenler |
|---|---|---|---|
| **1 — Kaynaklar, işleyiş, gezinme ve entegrasyon** | **Bu oturumdaki Arena ajanı (ben)** | Mevcut davranış baseline'ı; 7 kaynak × 4 tür eşlemesi; state, sekmeler arası gerçek pager, cache/hata ve source kimliği; kart ve Tümünü gör bağlantısı; eski özelliklerin korunması; entegrasyon ve nihai testler. | İş 2'nin aşağıda ayrılan üç üretim/iki test yolu; TV; başka görevlinin devam eden dosyaları. |
| **2 — Görsel Compose bileşenleri** | **Diğer yapay zekâ** | Sözleşmede birebir tarif edilen, bağımsız ve gerçek API'ye bağlı olmayan mobil başlık/kaynak seçici, tür sekmeleri ve kaynak etiketli raf bileşenleri; TR/EN kaynakları, mobil UI testleri ve teslim raporu. | `ExploreScreen`, ViewModel, veri, cache, navigation, grid, temanın ortak bileşenleri, TV ve iş 1'in dosyaları. Ekranı aktif etmez. |

İş 2 yalnızca aşağıdaki yolları **yeni dosya** olarak oluşturur (başka bir yol gerekiyorsa önce sorar):
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisuals.kt`
2. `app/src/main/res/values/mobile_explore_strings.xml`
3. `app/src/main/res/values-en/mobile_explore_strings.xml`
4. `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisualsTest.kt`
5. `docs/KESFET_MOBIL_PLAN/IKI_IS_PARCACIGI/IS_2_TESLIM_RAPORU.md` (test sonucu/atlanan test/görsel notları).

İş 1 bu beş yolu **yazmaz**, sadece son entegrasyonda sözleşme uyumunu okuyup kullanır. İş 2 yeni dosyalar mevcutsa üzerine yazmadan önce durur. Diğer tüm gerekli mevcut dosyalarda değişiklik, yalnız iş 1 sorumluluğundadır. Üçüncü taraf kod/asset kopyalanmaz; lisanslı kodlar fikir/araştırma içindir.

## Önkoşul ve paralellik
- Önce iki taraf bu belgeyi ve `01_ORTAK_SOZLESME.md` dosyasını değiştirmeden kabul eder; **sözleşme donduktan sonra** paralel başlar. İş 2 bağımsız arayüz bileşenlerini hazırlarken iş 1 baseline, mapper ve kaynak testlerini çalışır. İş 2 geçici kopya DTO veya kendi veri sınıflarını icat etmez; sadece mevcut model türleriyle çalışır.
- Aynı çalışma dizininde eşzamanlı düzenleme yapılmaz. Diğer AI ayrı çalışma kopyasında aynı temel kodu kullanır; teslimi yalnız yukarıdaki beş dosya olarak ZIP/dosya seti ile getirir. Git işlemi/dal değiştirme/push/merge yapması istenmez. Bu oturumun sabit branch'i korunur. Aynı repo'yu görüyorsa kullanıcıya söylemeden dosya/index değiştirmesin; ayrı kopya erişimi yoksa durup kullanıcıya bildirsin.
- Daha önceki ana plandaki sıralı aşama kapıları **entegrasyon ve etkinleştirme için hâlâ geçerlidir**. Bağımsız, kullanılmayan UI bileşenleri üzerinde paralel hazırlık bu kapıları aşmak sayılmaz. Baseline ve testler olmadan yeni ekran varsayılan yapılmaz.
- Diğer AI Drive belgelerine erişemeyebilir: ona tüm `02_DIGER_YAPAY_ZEKAYA_TALIMAT.md` içeriği, `01_ORTAK_SOZLESME.md`, onaylı görsel linki ve mevcut repo sağlanmalı. Erişemiyorsa davranış varsaymaz; gerekli dosyayı ister.

## Devir protokolü ve entegrasyon sırası
1. **İş 1** Aşama 04/05: mevcut kaynak/sayfalama/ayarlar için fixture ve baseline çıkarır; test ortamı yoksa bloke yazar. `source + category` semantiğine göre eşleme ve testler üretir; diğer AI'ın dosyalarına dokunmaz.
2. **İş 2** sınırlandırılmış bileşenleri ve UI testlerini üretir; uygulamaya bağlamaz. Testleri çalıştırır veya ortam yetersizse çalışmadığını raporlar. Yalnız izin verilen beş yolu, base commit'i ve diff özetini teslim eder.
3. **İş 1**, önce dosya yolu/içeriği/lisans, sabit fonksiyon imzaları, tema ve callback davranışını inceler. Görevli kodu uygunsuzsa iki tarafta aynı dosyayı düzeltmek yerine iş 2'ye geri gönderir. Sonra yalnız izinli dosyalar alınır; kullanıcı değişiklikleri ezilmez. Bileşen testi geçmeden son entegrasyon başlamaz; araç yoksa bu kapı açıkça **bloklu** kalır.
4. **İş 1** mevcut `ExploreScreen`/geri dönüş/`onSeeAllSection` bağlantılarını yapar. Kaynak seçimi ayrı, tab pager ayrı; kaydırma/görsel ile veri kaynağı arasında tek yönlü veri akışı. Kullanıcının ayarları, hata, takvim, kişisel listeler ve diğer özellikler korunur. İlk deneme eski mobil akışa dönülebilir biçimde yapılır; tasarım kararları netleşmeden belirsiz işlevler silinmez.
5. Önce birim ve Compose UI testleri, sonra bütün `:app` derleme ve 8 kaynak modu × 4 tür (32 kombinasyon) mobil kabul testleri; ardından onaylı görselle karşılaştırma. Android cihaz/SDK yoksa entegrasyonu veya yayını başarılı sayma. Çıkan her hata sahibine göre ayrı dosyada düzeltilir.

## Önlenmesi gereken yanlış varsayımlar
- TMDB ve Simkl'de enum/adı manga olan alanlar dizi veya film taşıyabilir; TMDB anime film sonuçları `Movie` olabilir. İş 2 tür sınıflandırmaz.
- Kaynak seçimi `ALL` dâhil 8 moddur ama gerçek katalog 7 kaynaktır. Kaynak rozeti ve detay/sayfalama **gerçek kaynağa** göre olmalı. Kitsu fallback'ını AniList gibi etiketleme.
- Mevcut `isHomeHeroMedia` kitapları dışlar. İş 2 bunu gevşetmez; manga için yeni hero, ürün kararı ve ayrı test olmadan eklenmez.
- `Tümünü gör` Simkl'de başlığa bağlı kişisel liste davranışı içerir; görünüm metnini değiştirerek veri semantiği değiştirilmez. Boş/hata/desteklenmiyor mevcut kodda her zaman ayırt edilemediği için sahte iddia yazılmaz.
- `tmdbModernHomeEnabled`, `separateNovelsManga`, adult/blur, başlık ve puan seçenekleri, bildirim, rastgele, takvim, okuyucu, geri, refresh ve alt bar özellikleri korunur; UX kararları için `../12_RISK_VE_KARAR_KAYDI.md`.

## Tamamlandı koşulları
Her iş için sahip olduğu dosyalarda derleme/test kanıtı veya dürüst blokaj raporu; iş 2'nin çağrı sözleşmesiyle uyumu; çakışmasız devir; hiçbir yeni ağ/sahte veri; kaynak/kimlik/sayfalama doğruluğu; geri dönüşlü entegrasyon; kullanıcı cihazında kapsamlı mobil kabul. Mutlak sıfır hata garanti edilmez. Bu plan hazırlanırken bu ortamda JDK/SDK yoktu; gerçek çalıştırma yapılmadı.
