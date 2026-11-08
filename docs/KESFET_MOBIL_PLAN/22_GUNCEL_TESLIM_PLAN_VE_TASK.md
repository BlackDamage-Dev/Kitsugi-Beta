# Mobil Keşfet — güncel teslim ve görev listesi

Tarih: 2026-11-08 · Dal: `arena/7e4f350c-kitsugi-beta` · Başlangıç commit'i: `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350` · Yalnız mobil; TV kapsam dışı.

## Önce oku: mevcut durum ve paket kullanımı

- Onaylı referans, ilham değil, mobil ana Keşfet için hedef tasarımdır. Poster üstünde yıldız/puan ya da liste-durum rozeti yoktur; alt satırda gerçek tür/yıl bulunabilir. Kullanıcının tema, vurgu ve dil ayarları korunur. Görsel, plan paketindeki `REFERANS/ONAYLANAN_GORSEL.png` dosyasıdır.
- Güncel kod ve testler ayrı kod ZIP'inde **depo yollarıyla** yer alır. Bu ZIP'teki dosyalar iki ajanın birleştirilmiş ve sonradan düzeltilmiş çalışma sürümüdür. ZIP tam depo değildir; iki zaten takip edilen dosyanın tam son hâli ve ilgili yeni dosyalar, ayrıca izlenen dosyalar için fark patch'i vardır. Tam dosyaları kopyalayıp aynı patch'i ikinci kez uygulamayın.
- Diğer AI'nın beş dosyalı **orijinal** teslim ZIP'i, ayrı arşiv olarak pakete korunarak eklenmiştir. Arşiv tarihî referanstır: içindeki dosyaları güncel kodun üstüne yazmayın; sonradan yapılan hata ve görsel düzeltmelerini geri alır. Orijinal SHA-256: `ab92db7dc2eb2bd1ed5772e60264ccca56fa4731c5b4a6bbcb2f3d715b21a5c8`.
- Bu dosya ve `21_ONAYLI_GORSEL_KARSILASTIRMASI.md`, eski plan/teslim raporlarına göre güncel durum açısından üstündür. Eski belgeler geçmiş ve araştırma referansıdır.

## Gerçek ilerleme ve eksikler

- Yeni mobil görsel bileşenler, veri eşleme, dört sekmeli pager, host ve finite-grid köprüsü hazırlanmış; **gerçek `ExploreScreen` tarafından henüz çağrılmıyor**. Dolayısıyla gerçek uygulamada yeni görünüm etkin değildir.
- 7 kaynak (AniList, MAL/Jikan, TMDB, Simkl, Kitsu, Shikimori, Bangumi) ve Tümü için 4 tür (Diziler, Filmler, Anime, Manga) model kapsamı sentetik verilerle denetlendi; gerçek API'lerin tümü doğrulanmadı.
- 32 saf/izole kontrol geçti; model deklarasyonları gerçek koddan çıkarılır ama bazı bağımlılıklar stub'dır. Bu koşum Android/Compose çalıştırmaz. 56 cihaz testi yazılı, **hiçbiri çalıştırılmadı**. Android SDK/cihaz bulunmadığı ve Gradle indirmesi başarısız olduğu için Android build, screenshot ve görsel kabul yok.
- Yeni poster üzerinde liste durumu ve puan görsel rozet olarak gösterilmez; yalnız ekran okuyucu semantiğinde sunulur. Bu, görsel sadakat ile görerek kullanım arasında bilinçli mevcut sınırlamadır; kullanıcıya/ürüne göre görünür alternatifi ve erişilebilirliği test edin. Ayrı finite grid eski ortak kartı kullanır.

## Yapılacak işler — bitmiş gibi işaretlemeyin

1. **Gerçek ekran entegrasyonu:** Mobil `ExploreScreen`/`AppRootTabPages` akışını inceleyip `MobileExploreVisualHost`u gerçek veriler ve gerçek callback'lerle kur; TV'ye dokunma. Seçili kaynak ve payload atomik eşleşsin. Tümü modunda her kaynağın state'i ayrı, Simkl kişisel rafları yalnız doğru hesap yükünden beslensin. Eski üst bar ve alt gezinme çift çizilmesin.
2. **Davranışların korunması:** Kaynak seçici, geri, detay, uzun basma/listeler, hero İncele, rastgele, bildirim, takvim/okuyucu, pull-to-refresh, retry, loading/error/cache, yetişkin filtre/blur, dil/tema/puan tercihleri, FAB ve barları mevcut uygulama davranışlarına bağla. Desteklenmeyen ve hatalı kaynakları boş başarı gibi göstermeden doğrula.
3. **Raf gezinmesi ve sayfalama:** Simkl devam için doğru geçmiş rotasını, planlanan ve katalog listeleri için doğru hedefleri bağla. Mevcut `mobileFiniteResults` yalnız yüklenmiş öğelerdir; API sayfa 2 değildir. Kaynak/tür/kategori kimliğine göre gerçek devam ve eski ekranların tüm özelliklerini doğrulamadan tamamlandı sayma.
4. **Görsel doğrulama:** Aynı viewport/tema/yazı/yoğunluk ve inset ile gerçek Compose/Android screenshot al; referansla üst bar, sekmeler, vitrin, iki raf, poster ölçüsü/köşe/border, metin, renk, aralık ve alt barı karşılaştır. Kapaklar gerçek sağlayıcıdan gelir; referanstaki uydurma içerikler üretime gömülmez. Farkları cihaz renderına göre tek tek düzelt.
5. **Hareket ve erişilebilirlik:** Dört sekme tıklama/swipe; dikey liste; yatay rafta başlangıca snap; üst/alt bar ve FAB animasyonları; geri jesti; 48dp dokunma hedefi çakışması, TalkBack, büyük yazı, dar/geniş/yatay ekran ve farklı temalar gerçek cihazda kontrol edilmeli. Son kompakt başlık ve raf hitbox'ları özellikle doğrulanmalı.
6. **Test ve güvence:** Erişilebilir Gradle ve Android SDK ile projenin FOSS/GMS görevlerini keşfet, derle; unit ve tüm Compose/cihaz testlerini çalıştırıp hatalarını düzelt. 7 kaynak × 4 tür + Tümü, canlı endpoint, kimlik, refresh/retry ve gerçek sayfa 2 doğrulanmalı. İlgili eski uygulama regresyonları ve ağ/boş/hata/erişilebilirlik halleri geçmeden hazır ilan etmeyin.
7. **Teslim:** Doğrulanmış güncel kod ve raporları Drive'a yedekle; orijinal İş 2 arşivini ayrıca ve değişmeden tut. Kullanıcıya gerçek doğrulanmış durum ve varsa kalan engelleri açıkça ilet.
