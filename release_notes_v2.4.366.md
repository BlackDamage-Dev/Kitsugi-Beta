# Kitsugi Beta v2.4.366

## [TR] Coraline Bangumi/Simkl Film Eşleşmesi & Kompakt Tarayıcı Arama Geçmişi (Omnibox)

Bu sürümde Simkl film kayıtlarının Bangumi ile çapraz eşitlenmesini engelleyen kök nedenler çözülmüş, ayrıca Arama sayfasındaki Son Aramalar bölümü tarayıcı arama geçmişi (omnibox) mantığında ultra-kompakt ve akıllı özelliklerle baştan tasarlanmıştır.

### 1. Coraline Bangumi & Simkl Film Çapraz Eşitleme Düzeltmesi
- **Film Kapsamı Genişletildi:** Coraline (#103470, type=ANIME, platform=剧场版) gibi animasyon filmleri Bangumi'de `ANIME` türü altında yer aldığından yalnızca `REAL` türünde arama yapılması engellendi; `BangumiIdNamespace.subjectTypesFor(MediaType.Movie)` ile hem `ANIME` hem `REAL` taranır.
- **Platform Doğrulaması:** Anime/dizi veya gerçek çekim dizi adaylarının filmlere yanlış yazılması engellendi (`ANIME + 剧场版` ve `REAL + 电影` kabul edilir).
- **Erken Tamamlanma Engeli:** Simkl/TMDB kimliği bulunsa bile bağlı Bangumi hesabı varken `isIdentityComplete` erken `true` dönmez; Bangumi subject kimliği çözülene kadar tam sayılmaz.
- **Önbellek Tür Koruması:** `BangumiLocalMappingCache` artık varsayılan türü değil, sunucudan dönen gerçek subject türünü öğrenir ve saklar.

### 2. Kompakt Tarayıcı Arama Geçmişi (Omnibox) & Akıllı Özellikler
- **Tarayıcı Omnibox Stili Kompakt Kart:** Dağınık çok satırlı çip yığını yerine `KitsugiCosmicSearchBar` ile birebir hizalanan `RoundedCornerShape(16.dp)`, `Surface` zemin ve `Border` kenarlıklı bütünleşik bir kart yapısına geçildi. Varsayılan olarak 5 ince (`38dp`) satır gösterilir; "Daha fazla göster (+N)" / "Daha az göster" ile yumuşak animasyonla açılır. Başlıktaki görünüm butonuyla "Tarayıcı Listesi" ↔ "Ultra-Kompakt Etiketler (Çip)" arasında tek dokunuşla geçiş yapılabilir (tercih cihazda hatırlanır).
- **Yazarken Canlı Geçmiş Önerileri & Vurgulama:** Arama çubuğuna yazmaya başlandığında geçmiş kartı otomatik olarak omnibox moduna geçer; Türkçe karakter duyarsız 1'e 1 karakter katlama (`foldForMatch`) ile eşleşen harfleri tema rengi veya gradyan fırçasıyla vurgular. Eşleşme yoksa otomatik gizlenir.
- **↖ Arama Çubuğuna Aktar:** Her geçmiş satırının sağındaki `↖` butonuyla sorgu başlatılmadan metin arama çubuğuna aktarılır, imleç sona taşınır ve klavye açılır (yazım hatası düzeltme veya kelime ekleme kolaylığı).
- **Sabitleme & Uzun Basma Çekmecesi:** Satıra uzun basıldığında Sabitle / Sabitlemeyi kaldır, Arama çubuğuna aktar, [Kaynak] ile ara (kaydedildiği motor ve kapsamı geri yükler), Kopyala ve Kaldır eylemleri sunulur.
- **Zaman Damgası, Kaynak Rozeti & Sık Aranan Sayacı:** Göreceli zaman (Az önce, 5dk, 2sa, Dün vb.), mikro kaynak logosu ve 3× ve üzeri aramalar için tekrar sayacı gösterilir.
- **50 Kayıt Kapasitesi & Mükerrer Birleştirme:** Büyük/küçük harf duyarsız normalize birleştirme yapılır; Room şema göçü gerektirmeden geriye uyumlu `:PIN:` kodlamasıyla kapasite 50 kayda çıkarıldı. Bulut eşitlemesinde sabitlenen kayıtlar korunur.
- **Tam Tarayıcı Geçmişi Paneli (Tüm Geçmiş):** Başlıktan veya kart altından açılan alt sayfada "Geçmişte ara..." çubuğu, Tümü / Sabitlenenler / Sık Arananlar / Kaynak filtre çipleri ve Bugün (tam saatle), Dün, Bu Hafta ve Daha Eski tarih grupları sunulur.
- **Geri Al (Undo) & Akıllı Temizleme Onayı:** Tekil silmelerde 5 saniyelik "Geri Al" şeridi çıkar; çöp kutusuna dokunulduğunda "Sabitlenen aramaları koru" seçeneğiyle onay penceresi açılır.
- **Gizli Arama Modu:** Başlıktaki göz butonundan tek dokunuşla arama geçmişi kaydı duraklatılabilir veya yeniden başlatılabilir.

---

## [EN] Coraline Bangumi/Simkl Movie Sync Fix & Compact Browser Omnibox Search History

This release fixes movie cross-synchronization between Simkl and Bangumi (resolving Coraline matching) and completely redesigns Search History into an ultra-compact browser-style omnibox with live matching, pinning, undo, and smart query actions.

### 1. Coraline Bangumi & Simkl Movie Cross-Sync Matching Fix
- **Expanded Subject Types for Movies:** Animated movies such as Coraline (#103470, type=ANIME, platform=剧场版) are cataloged under `ANIME` in Bangumi; searches now query both `ANIME` and `REAL` types via `subjectTypesFor(MediaType.Movie)`.
- **Platform Verification:** TV series decoys are strictly rejected during movie writes (`ANIME + 剧场版` and `REAL + 电影` are accepted).
- **Prevent Early Completion:** `isIdentityComplete` no longer exits early when a Simkl/TMDB id is present while Bangumi is connected; Bangumi resolution is awaited.
- **Real Subject Type Caching:** `BangumiLocalMappingCache` stores the server's actual subject type rather than a hardcoded default.

### 2. Compact Browser-Style Omnibox Search History & Smart Features
- **Browser Omnibox Card:** Replaced scattered multi-line chip clouds with a sleek, unified card matching `KitsugiCosmicSearchBar` surface, border, and 16dp rounded corners. Displays 5 compact (`38dp`) rows by default, expandable via smooth animation with "Show more (+N)" / "Show less". Includes a single-tap toggle between "Browser List" and "Ultra-Compact Tags" (remembered across sessions).
- **Live Autocomplete & Match Highlighting:** Typing in the search bar triggers omnibox mode with 1:1 Turkish character-folded matching (`foldForMatch`) and accent/gradient text highlighting on matched characters. Automatically hides when there are no matches.
- **↖ Fill Into Search Bar:** Tapping `↖` on any history row copies the query into the search bar without triggering a search, places the cursor at the end, and shows the keyboard.
- **Pinning & Long-Press Action Drawer:** Long-pressing any row reveals quick actions: Pin/Unpin, Fill into bar, Search in [Source] (restores original engine/scope), Copy, and Remove.
- **Relative Timestamps, Source Badges & Search Counters:** Shows relative time (Just now, 5m, 2h, Yesterday), micro provider icons, and frequency badges (3× and above).
- **50-Item Capacity & Case-Insensitive Merging:** Deduplicates queries case-insensitively without requiring Room migrations via backward-compatible `:PIN:` type encoding. Cloud sync preserves pinned entries.
- **Full History Sheet:** Bottom sheet with an inline filter bar, quick category chips (All, Pinned, Frequent, Source), and date grouping (Today with HH:mm, Yesterday, This Week, Older).
- **Undo Strip & Safe Clear Confirmation:** 5-second in-card Undo strip after deleting a record; trash button displays a confirmation dialog with "Keep pinned searches" checked by default.
- **Incognito Search Mode:** Toggle search history recording directly from the card header icon.
