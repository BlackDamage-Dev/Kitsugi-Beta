# Kitsugi Beta v2.4.348

## 🇹🇷 Türkçe

### 🔄 Liste Kaynakları Arası Çapraz Senkronizasyon ve Alan-Bazlı Eşitleme Motoru (Full Cross-Sync Engine)
- **Shikimori - MAL Kimlik Birleşimi:** Shikimori ve MyAnimeList kimlikleri (`myanimelist_id = object.id`) tekil kimlik havuzunda birleştirildi. Artık Shikimori kayıtları tekil grupta izole kalmıyor; MAL, AniList, Kitsu, Bangumi ve Simkl ile ortak eşitleme grubunda senkronize ediliyor.
- **Alan-Bazlı Akıllı Yazım (`SyncField`):** Her eşitlemede tüm kaydı körlemesine karşıya yazmak yerine, yalnızca yerelde değişen alanlar (`STATUS`, `PROGRESS`, `SCORE`, `DATES`, `NOTES`, `REWATCH`) tespit edilerek hedefe iletiliyor. Ağ trafiği ve gereksiz API istekleri en aza indirildi.
- **Son Kullanıcı Hareketi Önceliği (`CrossSyncMergePolicy`):** Bir serviste bölüm ilerlettiğinizde veya bir yapımı bıraktığınızda (`Dropped`/`Paused`), diğer servislerdeki bayat verilerin bunu geriye çekmesi engellendi. Deterministik `NEWEST_FIRST` kuralı ile en güncel işlem daima kazanıyor.
- **Tekrar İzleme (Rewatch) ve Döngü Koruması:** MAL ve Kitsu'nun bayrak modeli (`is_rewatching=true`) ile AniList'in durum modeli (`REPEATING`) ayrıştırıldı. MAL'de dizilerin yanlışlıkla "Tamamlandı"ya dönmesi ve sonsuz yazım döngüleri giderildi.
- **Kardeş Satır Aynalama (`SiblingMirrorPolicy`):** Yerel veritabanında aynı yapımın farklı kaynaklardaki kopyaları güncellenirken ilerleme gerilemesi, tarih kayıpları ve gereksiz yazımlar önlendi.
- **Simkl Çift Yönlü Senkronizasyon & Notlar:** Simkl'de silinen kayıtların doğru listeye çekilmesi (`notinteresting`), `?memos=yes` parametresiyle iki yönlü not senkronizasyonu ve durum normalizasyonu tamamlandı.
- **Çevrimdışı Kuyruk ve Güvenli Tahliye (`PendingSyncDrainer`):** İnternet yokken yapılan değişiklikler yerel kuyrukta saklanıyor ve bağlantı kurulduğunda platform hız limitlerine uygun olarak sırayla hedeflere işleniyor.
- **256 Senkronizasyon Birim Testi:** MAL, AniList, Shikimori, Kitsu, Bangumi ve Simkl sağlayıcıları arasındaki tüm çapraz durumlar ve geriye-yazım testleri firesiz doğrulandı.

---

## 🇬🇧 English

### 🔄 Multi-Provider Cross-Sync & Field-Level Synchronization Engine
- **Shikimori - MAL Identity Unification:** Unified Shikimori and MyAnimeList entry identities (`myanimelist_id = object.id`). Shikimori entries no longer remain isolated in single-source groups and now cross-sync seamlessly across MAL, AniList, Kitsu, Bangumi, and Simkl.
- **Field-Level Diffing (`SyncField`):** Replaced blind full-object writes with granular field diffing (`STATUS`, `PROGRESS`, `SCORE`, `DATES`, `NOTES`, `REWATCH`). Only dirty fields are dispatched to target APIs, eliminating redundant requests and server throttling.
- **Newest Action Priority (`CrossSyncMergePolicy`):** Prevented stale tracker states from rolling back recent progress or overriding `Dropped`/`Paused` statuses. Deterministic `NEWEST_FIRST` resolution ensures the latest user update always wins.
- **Rewatch Semantics & Loop Prevention:** Decoupled flag-based rewatch models (MAL/Kitsu `is_rewatching`) from status-based models (AniList `REPEATING`), eliminating infinite synchronization loops and premature "Completed" transitions on MAL.
- **Deterministic Sibling Mirroring (`SiblingMirrorPolicy`):** Guaranteed monotonic progress and non-destructive metadata propagation across multi-source siblings in local storage.
- **Simkl Two-Way Sync & Memos:** Added bidirectional note synchronization via `?memos=yes` and normalized list status handling (`notinteresting`).
- **Offline Sync Queue & Safe Drain (`PendingSyncDrainer`):** Offline mutations are safely persisted in Room and drained with rate-limiter backoff upon reconnection.
- **256 Unit Tests Passing:** Validated read-back idempotence, merge policies, and cross-provider conversions across all 6 supported platforms.
