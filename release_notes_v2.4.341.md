# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🚦 "AniList kota doldu" — Kök Neden Düzeltmesi
- **Sorunun kaynağı uygulamaydı, AniList değil.** AniList'in fiilî limiti **30 istek/dk**
  (dokümante edilen 90 değil; API yıllardır "degraded"). Kitsugi bu kotayı birbirinden
  habersiz **dört ayrı kapıdan** harcıyordu: okuma yolu 350 ms aralıkla (~171 istek/dk),
  liste yazmaları (ekleme/düzenleme/silme) hız sınırı **olmadan**, liste yenileme hız sınırı
  **olmadan**, profil ekranları yine sınırsız.
- **Tek kota kapısı (`AniListGate`):** 60 sn'lik kayan pencere (limit − güvenlik payı),
  400 ms asgari aralık, 2 sn'de en fazla 5 istek, `Retry-After` / `X-RateLimit-Reset`
  destekli 429 soğuması ve `X-RateLimit-Remaining: 0` erken uyarısı. Artık **tüm** AniList
  trafiği bu kapıdan geçiyor.
- **Öncelik:** arka plan (bildirimler, kişi görselleri, Fandom zenginleştirmesi) kullanıcıya
  ayrılmış kotayı yiyemez; liste yenileme sürerken yapılan düzenlemeler beklemez.
- **429 artık thread kilitlemiyor:** eskiden ceza penceresinde çağıran thread 60–120 sn
  uyutuluyordu (kilit altında). Artık iş **bekleyen-senkron kuyruğuna** giriyor ve ceza
  bitince **kendiliğinden** gönderiliyor.

### ✍️ Düzenleme Başına İstek Sayısı 4–7 → 1
- **Kimlik doğrulama önbelleği (6 saat):** her düzenlemede tekrar eden `Media(id)` sorgusu
  kaldırıldı.
- **Favori sorgusu gereksizse atlanıyor:** yalnızca bölüm/puan değiştiren düzenlemelerde
  artık `isFavourite` sorgusu ve `ToggleFavourite` mutasyonu çıkmıyor.
- **Yazma birleştirme:** aynı kayda 1,2 sn içinde gelen düzenlemeler (bölüm +1'e art arda
  basmak, puan kaydırıcısı) **tek** uzak yazmaya indirgeniyor; tüm uzak yazmalar sırayla
  çalışıyor. Yerel kayıt her zaman anında yazılıyor.
- **Düzenleyici açılışı:** 4 AniList isteği → ilk açılışta 1, sonraki açılışlarda 0–1
  (30 dk Viewer/liste seçenekleri önbelleği, 5 dk liste meta önbelleği).

### 🔄 Liste Yenileme
- Profil bilgisi her yenilemede yeniden çekilmiyor (15 dk önbellek), `Viewer` kimliği
  24 saat önbellekte. Sayfalar arası istekler tempolu gidiyor: 429 yerine biraz daha
  yavaş ama **tamamlanan** bir yenileme.
- Kota nedeniyle yarıda kalan yenilemede mesaj artık doğru:
  **"⏳ AniList istek sınırı doldu — N sn sonra tekrar denenebilir"** (eskiden
  "çevrimdışı" deniyordu).

### ✅ Hata Mesajları
- Kota hatası ile çevrimdışı olma artık ayrı mesajlarla gösteriliyor.
- Kota nedeniyle ertelenen kayıtlar ceza bitiminde kendiliğinden gönderiliyor; kullanıcının
  aynı düğmeye tekrar basmasına gerek yok.

### 🧩 "Günlerdir hiçbir şey AniList'e gitmiyor" — Sessiz Veri Kaybı Düzeltmesi
- **24 saatlik otomatik yenileme yerel kayıtları siliyordu:** açılışta listeler
  `replaceSourcePreservingAdultFlags` ile **sil-yeniden-yaz** yapıyordu. Uzak listede
  bulunmayan (yani henüz AniList'e gönderilememiş) **yeni kayıtlar** ve o kaynak için
  bekleyen **kuyruk işleri** her 24 saatte bir yok oluyordu. Artık birleştirme yapılıyor
  (`smartImport`, `allowDelete = false`) ve **önce bekleyen değişiklikler gönderiliyor**.
- **Bekleyen değişiklik uzak kopyayla ezilmiyor:** kuyrukta işi olan satır, liste
  yenilemesinde/otomatik senkronda korunur.
- **Deneme sayacı artık "ölüm cezası" değil:** 5 denemeyi aşmış işler uygulama açılışında
  ve **"Şimdi dene"** ile tazelenir; kota hatası sayacı hiç artırmaz.
- **Kimlik doğrulaması her şeyi sessizce kilitleyemez:** MAL kataloğuna ulaşılamadığında
  (429/403/timeout) yazma sessizce atlanıyordu; artık yalnızca guard'ın **açıkça
  reddettiği** eşleşmeler durdurur, katalog erişilemezse native MAL satırlarında yazma
  devam eder ve durum kullanıcıya bildirilir.

### 💬 "Neden eşitlenmiyor?" — Doğru ve tek anlamlı uyarılar
- Listem ekranına **durum şeridi** eklendi: kota · oturum geçersiz · bağlı değil ·
  eşitleme kapalı · internet yok · AniList API kapalı · kimlik doğrulanamadı.
  Şerit bekleyen değişiklik sayısını ve (kotada) kalan saniyeyi gösterir, **"Şimdi dene"**
  düğmesiyle kuyruğu zorlar.
- Tek tek işlemlerde çıkan mesajlar da nedene göre ayrıldı; eskiden her hata
  "📵 Çevrimdışı kaydedildi" diyordu. Aynı gerekçe 2 dakikada birden sık gösterilmez.
- Başarılı görünen ama AniList'in **atlandığı** turlar (bağlı değil / kimlik / kapalı)
  artık sessiz değil.

---

## 🇬🇧 English

### 🚦 "AniList rate limit reached" — Root Cause Fix
- **The app was the cause, not AniList.** AniList's real limit is **30 req/min** (not the
  documented 90; the API has run degraded for a long time). Kitsugi was spending that budget
  through **four independent doors**: the read path at 350 ms intervals (~171 req/min), list
  writes (add/edit/delete) with **no** pacing at all, list refresh with **no** pacing, and
  profile screens also unpaced.
- **One quota gate (`AniListGate`):** rolling 60 s window (limit − safety margin), 400 ms
  minimum spacing, at most 5 requests per 2 s, `Retry-After` / `X-RateLimit-Reset` aware
  429 cool-down, and an early `X-RateLimit-Remaining: 0` guard. **All** AniList traffic now
  flows through it.
- **Priority:** background work (notifications, person images, Fandom enrichment) can no
  longer consume the budget reserved for the user, and a list refresh no longer blocks edits.
- **429 no longer blocks a thread:** previously the caller was slept for 60–120 s (while
  holding a lock). Now the write is queued and **retried automatically** when the penalty
  window ends.

### 🧩 "Nothing has synced to AniList for days" — Silent Data Loss Fix
- **The 24-hour auto refresh was deleting local rows:** on startup, lists were written with
  `replaceSourcePreservingAdultFlags`, a **delete-and-reinsert** cycle. New entries that were
  not yet on AniList (i.e. never pushed) and the **pending queue jobs** for that source were
  wiped every 24 hours. The auto refresh now **merges** (`smartImport`, `allowDelete = false`)
  and **pushes pending changes first**.
- **Pending changes are no longer overwritten** by the remote copy during refresh/auto sync.
- **The retry counter is no longer a death sentence:** jobs past 5 attempts are refreshed on
  app start and via **"Retry now"**; quota errors never consume attempts.
- **Identity verification can no longer block everything silently:** when the MAL catalog is
  unreachable (429/403/timeout) writes used to be skipped with no message. Now only explicit
  *rejections* stop a write; unreachable-catalog cases proceed for native MAL rows and the
  reason is surfaced to the user.

### 💬 "Why isn't it syncing?" — Accurate, single-cause warnings
- New **status banner** in the Library screen: quota · invalid session · not connected ·
  sync disabled · no internet · AniList API down · identity unverified. It shows the pending
  change count (and remaining seconds for quota) and offers **"Retry now"**.
- Per-action messages are now reason-specific (the old build always said
  "📵 Saved offline"), and the same reason is not repeated more than once every 2 minutes.
- Turns that *look* successful but skipped AniList (not connected / identity / disabled) are
  no longer silent.

### ✍️ Requests per Edit: 4–7 → 1
- **Identity verification cache (6 h):** the repeated `Media(id)` lookup per edit is gone.
- **Favourite sync skipped when unnecessary:** edits that only change progress/score no
  longer trigger `isFavourite` + `ToggleFavourite`.
- **Write coalescing:** edits arriving within 1.2 s (rapid "+1", score slider) collapse into
  a **single** remote write, and all remote writes are serialized. Local writes stay instant.
- **Editor open:** 4 AniList requests → 1 on first open, 0–1 afterwards (30 min viewer
  options cache, 5 min list-metadata cache).

### 🔄 List Refresh
- Profile info is no longer re-fetched on every refresh (15 min cache) and the `Viewer` id is
  cached for 24 h. Pages are paced: a slightly slower refresh that **finishes** instead of
  hitting 429.
- If a refresh is interrupted by the quota window, the message is now accurate:
  **"⏳ AniList rate limit reached — retry in N s."**

### ✅ Error Messages
- Rate limiting and being offline are now reported separately.
- Edits deferred by the quota are pushed automatically once the penalty window ends — no
  repeated tapping required.
