> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# 12 — Risk kaydı ve uygulama öncesi kararlar

## P0 riskleri
| Risk / kanıt | Etki | Önlem ve doğrulama |
|---|---|---|
| Legacy kategori adları türle aynı değil (`AllSourcesExplore.sourceSections`) | Film/dizi manga rafına karışır | Source+category semantiğiyle tablo, 8×4 test; gerçek kimlik ve type. |
| TMDB anime rafı TV + film sorgular (`TmdbDiscoverClient`) | Anime filmi Filmler'e yanlış taşınır | Raf semantiği + `type`, film/anime fixture; tür tek başına karar vermez. |
| Kitsu fallback AniList tek kaynakta dolgu (`ExploreViewModel.loadAniListData`) | Kitsu kartı AniList diye etiket/sayfalanır | Gerçek source korunur; test ve doğru davranış seçilir, kararsızsa sonlu/uyarılı. |
| `FullScreenMediaGridPage` Simkl kişisel listeleri başlık metninden algılıyor | Yeni başlık/çeviri yanlış pagination | Başlığı bozma; explicit origin ihtiyacını planla ve tüm diğer çağıranları test et. |
| Simkl `getBestMedia` ile grid'de başka çağrı; `MANHWA_MANHUA` AniList grid'de boş | Tümünü gör ilk ve sonraki sayfada farklı içerik | Eşleme testi; gerçek destek yoksa güvenli sonlu liste veya doğru endpoint. |
| `isHomeHeroMedia` manga/kitabı reddeder | Manga hero boş/yanlış ya da genel vitrin politikası bozulur | İlk aşamada manga hero yok; ileride ayrı mobil kitap hero. |
| API hataları bazen boş listeye çevriliyor | Boş sonucu yanlış 'desteklenmiyor' veya başarılı diye gösterme | Destek/boş/hata ayrımı yalnız kanıta dayanır; belirsizde dürüst uyarı. |
| 1 scroll index vs 4 sekme (`AppViewModel`) | Geri dönüş/dönme yanlış konum | Her tab için mobile state ve uyumlu adaptör; cihaz testi. |
| Ortak model/ViewModel diğer ekranlarca kullanılıyor | Mobil değişiklik diğer kullanıcıları etkiler | Eklemeli mobil adapter, imzayı değiştirmeme; kaçınılmazsa dur/onay iste. TV özellik geliştirmesi kapsam dışı. |
| Hızlı filtre/yenileme/istek yarışları ve eski cache | Eski kaynak veya kullanıcı verisi görünür | generation/cancel, kaynak/dil cache, gecikmeli fixture; kullanıcı verisi güvenliği. |
| İç içe yatay pager, hero ve LazyRow | Kaydırma yanlış bileşeni hareket ettirir | Sekme dokunması alternatif; gerçek cihazda pointer/nested scroll testi. |
| Çok sayıda kaynak/raf + backdrop | 429, bellek ve donma | Tek fetch, mevcut limit/batching/cache, frame ve istek sayısı ölçümü. |

## Uygulamadan önce açık kararlar (öneri bağlayıcı değildir)
1. İlk açılış: onaylı görselde Diziler seçili; kullanıcının son seçili sekmesini de kalıcı hatırlayalım mı? Öneri: ilk açılış Diziler, aynı oturumda son tab; kalıcı tercih ayrıca onay.
2. Tümü modunda kaç kaynak rafı ilk ekranda? Öneri: her kaynağı tür içerisinde kaynak etiketli ve eşit erişilebilir biçimde sırala; ekranı sınırlandırma/özetleme gerekirse ölçüm ve kullanıcı kararı. Yedi kaynağın görünürlüğü kaybolmamalı.
3. Manga sayfasında ayrı poster hero mu, ilk sürümde hero olmadan raflar mı? Mevcut video hero manga kabul etmiyor; öneri ilk sürümde hero yok ve kartlı tasarım, sonradan ayrı güvenli kitap hero.
4. `tmdbModernHomeEnabled=false` iken yeni tasarım mı yoksa klasik yol mu? Ayar başka ekranlarda da etkili; kullanıcı tercihini kaldırmadan karar verilmeli.
5. Rastgele içerik aktif sekmeden mi yoksa tüm katalogdan mı? Önceki davranış korunarak karar alınmalı.
6. Sekme geri tuşunun geçmişine girer mi? İlk öneri: sekme durumdur, geri önce detay/grid'i kapatır; mevcut sistemle doğrula.
7. Kategori ve sıralama kısayolları, yayın takvimi ve manga okuyucu kısayolu yeni görünümde nereye taşınacak? Silinmez; yerleşim kullanıcı onayına sunulur.

## Durum notu
Bu plan hiçbir regresyonu mutlak olarak engelleyemez. Eksik SDK/cihaz, üçüncü taraf API arızası, eksik lisans bilgisi ve değişen repo durumu açıkça kayda geçirilir. Canlı servisler/cihazlar doğrulanmadan uygulama tamamlandı denmez.

## İkinci denetim
Somut kart ölçüsü, afiş ağ yüklemesi, boş raf, kaynak aksanı, TR/EN kişisel liste başlıkları, eylem konumları ve build varyantı riskleri `13_IKINCI_KONTROL_VE_KAPILAR.md` içinde kanıt ve sahipleriyle genişletildi.
