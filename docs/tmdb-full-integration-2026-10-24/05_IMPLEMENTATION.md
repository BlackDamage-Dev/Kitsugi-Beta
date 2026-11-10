# Uygulama ve dosya envanteri — TMDB tam entegrasyon

Tarih: 2026-10-24  
Branch: `arena/0d16774b-kitsugi-beta`

## Mimari kararlar

### Hesap ile katalog aynı kaynak değildir

- `tmdb`: eski katalog/detay kaynağı; gerektiğinde Simkl detay köprüsü davranışı korunur.
- `tmdb_account`: gerçek kullanıcı session’ıyla okunan/yazılan hesap üyeliği.
- Hesap-sekmeli `matchesInSource` sorgusu Simkl veya eski katalog satırını hesap üyeliği saymaz.
- Birleşik “Tümü” görünümü ise aynı TMDB kimliğini taşıyan satırları kullanıcıya tek kart olarak gruplar.

### Üç native alan bağımsızdır

- Watchlist → `tmdbWatchlist: Boolean?`
- Favorite → mevcut `isFavorite`
- Rating → `tmdbRating: Double?`; ortak Kitsugi `score: Int?` alanı yuvarlanmış görünüm/diğer sağlayıcılar için kalır.
- Null rating resmî delete-rating çağrısıdır. Değerler 0,5–10 aralığına ve yarım adıma normalize edilir.
- `WatchStatus.Planned → Watchlist` yalnız native değeri olmayan eski/yeni cross-sync kayıtlarında fallback’tir; mevcut native üyeliği ezmez.
- Status, bölüm/ilerleme, tarih, not, etiket ve benzeri alanlar için sahte TMDB verisi üretilmez.

### Alan niyeti, hesap kilidi ve bildirim teslimi

- Repository önce kardeş satırlara yansıtılmamış eski/yeni değerlerden `TmdbWriteFields` diff’i üretir; bu maske pending JSON ile taşınır.
- Pending `_tmdbAccountId` pozitif hedef hesabı, `0` hesapsız işlemi, yokluk legacy kaydı belirtir. Yanlış hesapta uzak yazma atlanır.
- Snapshot, native write, production login/logout aynı operation mutex’ini kullanır. Coordinator snapshot sonrası ve import öncesi session/account eşleşmesini yeniden doğrular.
- Sync manager her native yazmadan önce resmî `account_states` okur; yalnız uzaktan farklı alanlara yazma yapar. Null rating, uzakta rating varsa DELETE olur.
- Bildirim polleri hesap-bazlı fingerprint baseline kullanır. Snapshot → sistem/arşiv teslimi → baseline commit sırası kilitlidir; teslim başarısızsa baseline ilerlemez.

### Yazma sırası ve yarım puan

1. TMDB hesap satırı kendi native değerlerini doğrudan yazar.
2. Başka sağlayıcıdan yazımda aynı TMDB kimliğine sahip yerel hesap satırı bulunur.
3. Ortak Int puan değişmemişse mevcut 0,5’lik rating korunur.
4. Puan gerçekten değiştiyse eski native rating geçersiz sayılır ve yeni puan yazılır.
5. Watchlist mevcut hesap satırından korunur; yalnız hesap satırı yoksa status fallback’i kullanılır.

## Yeni ana dosyalar

| Dosya | Sorumluluk |
|---|---|
| `data/auth/TmdbAccountContract.kt` | Kaynak sabitleri, native merge, rating/token politikaları, yazma resolver’ı |
| `data/auth/TmdbAuthStore.kt` | v3 session ve süreli/tek kullanımlık pending request-token deposu |
| `data/auth/TmdbAccountOperationMutex.kt` | Snapshot, write ve production auth değişimleri arasında ortak sıralama |
| `data/auth/TmdbAccountSyncCoordinator.kt` | Tek snapshot/refresh yolu, session guard ve deletion guard |
| `data/auth/TmdbSyncManager.kt` | Account-state preflight ile Watchlist/favorite/rating yazma ve silme |
| `data/auth/CrossWriteVerificationGate.kt` | İşlem başına hedef-güvenli kimlik doğrulama önbelleği |
| `data/remote/TmdbAccountClient.kt` | Resmî v3 auth/session, sayfalı koleksiyonlar, account-state, rating ve film listeleri |
| `data/remote/TmdbAccountSnapshot.kt` | Snapshot ve liste metadata modelleri |
| `core/notifications/NotificationWorkPolicy.kt` | Saf scheduler gate’i; TMDB-only hesabı airing feed sayılmaz |
| `core/notifications/TmdbAccountNotificationPolicy.kt` | Account-scoped, type-sensitive baseline/fingerprint ve yeni-öğe politikası |
| `core/notifications/TmdbAccountNotificationPoller.kt` | Snapshot → await edilen teslim → durable baseline sırası |
| `ui/components/KitsugiTmdbListsDialog.kt` | `item_status`, liste oluşturma, film ekleme/çıkarma dialogu |
| `ui/screens/mylist/TmdbLibraryEntryMigration.kt` | Eski sahte `source=tmdb` liste satırlarını güvenli onarım |
| `ui/screens/profile/TmdbProfileContent.kt` | Watchlist/favorite/rated profil görünümü ve native yarım puan |
| `app/schemas/.../30.json` | Room 30 şeması ve doğrulanmış identity hash |

Yeni testler: `TmdbAccountContractTest`, `TmdbAccountNotificationPolicyTest`, `TmdbLibraryEntryMigrationTest`, `CrossWriteVerificationGateTest`, `MediaEntryBackupTmdbTest`.

## Önemli genişletilen dosyalar

- `MediaEntry`, `MediaEntryEntity`, `KitsugiDatabase`: native alanlar ve Room 30.
- `MediaEntryBackup`, dolaylı olarak `PendingSyncDrainer`: native alanların JSON ile taşınması.
- `KitsugiEditMediaSheet`, `KitsugiMediaEntryEditorDialog`, `AppDialogHost`, `AppRoot`, `TvRootScreen`: native editör ve callback zinciri.
- `AppViewModel`, `AppRootTabPages`, `MyListScreen`: hedef-kaynak bilinçli API ekleme ve ortak fan-out.
- `ExternalListSyncManager`, `AuthViewModel`: TMDB yazma/silme ve çapraz senkron.
- `MediaEntryRepository`: sibling alan yansımasında yarım puanın korunması.
- `KitsugiModels`, `MediaIdentity`, `MyListLibraryGrouping`: hesap/katalog kapsamı ve birleşik kimlik.
- `ExternalAuthManager`, `KitsugiOAuthWebViewDialog`: callback-bound tek kullanımlık login ve aynı URL’yle tarayıcı geçişi.
- `PlatformErrorCode`, `PlatformErrorCatalog`, `strings.xml`: kodlu TMDB hata yüzeyi ve TR/EN metinler.
- Ayarlar, profil, bildirim ve TV dosyaları: bağlantı/senkron/bildirim parametreleri.

## Resmî sınırlar

- v3 özel liste item mutation bu entegrasyonda film içindir.
- TV/karışık/private v4 liste yazımı için v4 kullanıcı access tokenı gerekir; v3 session bu yetki gibi gösterilmez.
- TMDB’nin resmî bildirim feed’i yoktur; uygulama yeni hesap eklemelerini cihaz snapshot farkıyla arşivler.
- Katalog API anahtarı kullanıcı yazma yetkisi değildir.
