# Plan / Task — Shikimori ve diğer kaynaklarda eklenti video eşlemesi

**Tarih:** 2026-10-25  
**Dal:** `arena/43a0b5c2-kitsugi-beta`  
**Kapsam:** Kaynak arama/detay → İzle ekranı → eklenti sorgusu → yeniden dene / sonraki bölüm / izleme geçmişi. Bu belge ZIP içindeki çalışma dosyalarını açıklar; APK veya canlı eklenti sonucu değildir.

## Sorun / kök neden

1. Shikimori kaydının kendi ID'si İzle ekranına taşınmıyordu. MAL ID henüz çözümlenmemişse Stremio/CloudStream için kimlik araması eksiliyor ve yalnızca başlık üzerinden eşleşme kalıyordu. Hatalı/429 eşleme yanıtı olumsuz olarak önbelleğe alındığında sonraki denemeler de bundan etkileniyordu.
2. Shikimori arama kartındaki `titleEnglish` bazı durumlarda gerçek İngilizce başlık yerine romaji içeriyordu; ayrıntıdaki İngilizce başlık ve Shikimori `synonyms` arama varyantlarına her yoldan ulaşmıyordu.
3. Simkl kaydının özgün Simkl ID'si de eşleme yoksa düşüyordu. Üstelik kimliksiz `*_best_*` yanıtlarındaki sıra numarası geçici kart ID'si olduğu halde gerçek Simkl ID'si sanılabilirdi.
4. Listeye ekleme sırasında başka kaynağın sayısal ID'si (TMDB/Simkl/Shikimori/Bangumi) MAL alanına düşebiliyordu. İzleme geçmişinden geri dönüş, özellikle kimlik çözümlenmeyen kaynaklarda, kaynak ID ve türü kaybediyordu.

## Uygulanan işler

- [x] `StreamMediaIds`: Shikimori ve Simkl için ayrı kimlik alanları; ham ve Shikimori 400M stable ID ayrımı, Simkl 150M legacy stable ID desteği; yabancı ID'ler MAL slotuna aktarılmaz.
- [x] Mobil/TV detay, bölüm, indirme, İzle, sonraki bölüm, doğrulama sonrası/manuel yeniden deneme yollarında Shikimori ve Simkl ID'lerini taşı.
- [x] Shikimori → doğrulanmış `myanimelist_id` yoksa başlık yedeği; geçici olumsuz eşleme sonucu kalıcı önbelleğe yazılmaz. Ayrıntıdaki İngilizce başlığa öncelik ver, `synonyms` varyantlarını ekle.
- [x] Simkl → kendi API'sindeki çapraz kimlikleri (MAL/TMDB/AniList/Kitsu/IMDb) mevcut çözümleyiciyle tamamla; Simkl ID'sini doğrudan MAL/TMDB ID olarak asla kullanma.
- [x] Simkl geçici kart ID'sini `sourceIdIsProvisional` ile işaretle; ağ sorgusunda kaynak ID gibi kullanma.
- [x] Listeye ekleme için `storedIdForSource`: kaynak ID'sini yalnızca kendi kaynak satırına veya doğrulanmış MAL eşlemesine kaydet. Gerçek Simkl ID'si ayrı `simklId` sütununda kalır. TMDB sonucunun gerçek TMDB ID'sini koru.
- [x] İzleme geçmişinde Bangumi/Shikimori/Simkl ID'leri ve medya türünü saklayıp geri dönüşte aktar; harici oynatıcı ilerleme anahtarını İzle ekranındaki anahtarla hizala.
- [x] `StreamMediaIdsTest`, `SimklExploreParsingTest`, `ListSourceMediaIdTest` ile kimlik ayrımı ve yanlış kimlik regresyon senaryolarını ekle.

## Doğrulama durumu / yapılacaklar

- [x] `git diff --check` temiz; mobil/TV İzle girişleri için kimlik alanı geçişi statik olarak karşılaştırıldı.
- [x] Değişen Kotlin dosyalarının sözdizimi tree-sitter ile temel sürüme karşı kontrol edildi; yeni sözdizimi hatası bulunmadı (SimklApiClient'teki iki parser işareti temel sürümde de mevcut).
- [ ] **Android derlemesi ve JUnit çalıştırılması:** Bu çalışma ortamında `java`/`JAVA_HOME` ve Android SDK bulunmadığından `./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.data.remote.*' --offline` çalıştırılamadı. JDK + SDK bulunan ortamda test ve uygulama derlemesini çalıştır.
- [ ] **Gerçek cihaz doğrulaması:** Aynı eser/bölüm için Shikimori, Simkl, AniList, MAL, Kitsu, Bangumi ve TMDB detaylarından İzle'yi aç; eklenti kaynak sayıları ve hata kartlarını karşılaştır. Yeniden dene, indirme, sonraki bölüm, TV ve geçmişten dönüşü de dene.
- [ ] 403/429, ağ kesintisi, Cloudflare, kaldırılmış video veya eklentinin kendi ayrıştırma hatası gibi sağlayıcı kaynaklı sonuçları uygulama kimlik hatasından ayrı teşhis et. **Tüm eklentilerin her başlıkta aynı sayıda video döndüreceği garanti edilemez.**

## ZIP içeriği

Arşiv kökünde bu plan/task dosyası ve bu daldaki değişen/yeni kaynak ile test dosyaları kendi `app/src/...` yollarında yer alır. Dosyalar tam halleriyle eklenmiştir; ayrıca yama uygulanması gerekmez. Depodaki diğer dosyalar, `node_modules`, `.venv`, SDK/derleme artıkları ve kullanıcı kimlik bilgileri arşive alınmaz.
