package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus

/**
 * ÇAPRAZ EŞİTLEMEDE TAŞINAN VERİ ALANLARI.
 *
 * Neden alan bazlı? Referans uygulamalar (MAL-Sync `SingleAbstract.getStateDiff/isValueDirty`,
 * Aniyomi `Tracker` arayüzü) **yalnızca değişen alanı** hedefe yazar. Tüm satırı her seferinde
 * yazmak (bizim eski davranış) şu veri kayıplarını üretiyordu:
 *
 *  - `score = null` olan bir satır MAL'a `score=0` gönderiyor → **MAL'deki puan siliniyor**
 *    (MAL API: "Set this value to 0 to remove the user's rating").
 *  - Kitsu'da `ratingTwenty: null` gönderiliyor → Kitsu'daki puan siliniyor.
 *  - Simkl'de `removeRating` çağrılıyor → Simkl puanı siliniyor.
 *  - Notu boş olan satır hedefteki notu silmiyor ama dolu olan her seferinde yeniden yazıyor;
 *    hedefte yapılan düzenlemeler sessizce eziliyor.
 *
 * Bu yüzden eşitleme artık `değişen alanlar` kümesiyle çalışır; boş değer ancak kullanıcı o
 * alanı **bilerek boşalttıysa** (diff'te görünüyorsa) hedefe yazılır.
 */
enum class SyncField {
    STATUS,
    SCORE,
    PROGRESS,
    VOLUME_PROGRESS,
    START_DATE,
    END_DATE,
    NOTES,
    TAGS,
    FAVORITE,
    PRIVATE,
    REWATCH_COUNT,
    REPEAT_VALUE,
    PRIORITY,

    /** AniList `hiddenFromStatusLists` — yalnız AniList destekler. */
    HIDDEN_FROM_STATUS_LISTS;

    companion object {
        val ALL: Set<SyncField> = entries.toSet()
    }
}

/** İki yerel satır kopyası arasındaki kullanıcı-alanı farkı. */
object SyncFieldDiff {

    /**
     * @param previous DB'deki ÖNCEKİ hâl. `null` ise satır yeni eklenmiş ya da önceki hâli
     * bilinmiyor demektir → [SyncField.ALL] döner (ilk yazım tam olmalı, aksi halde hedefte
     * kayıt oluşmaz).
     */
    fun between(previous: MediaEntry?, current: MediaEntry): Set<SyncField> {
        if (previous == null) return SyncField.ALL
        if (previous.id != current.id) return SyncField.ALL
        val changed = mutableSetOf<SyncField>()
        if (previous.status != current.status) changed += SyncField.STATUS
        if (previous.score != current.score) changed += SyncField.SCORE
        if (previous.progress != current.progress) changed += SyncField.PROGRESS
        if (previous.volumeProgress != current.volumeProgress) changed += SyncField.VOLUME_PROGRESS
        if (!previous.startDate.sameDate(current.startDate)) changed += SyncField.START_DATE
        if (!previous.endDate.sameDate(current.endDate)) changed += SyncField.END_DATE
        // Boşaltma da bir değişikliktir: `null` → "" farkı hedefe "sil" olarak yansımalı.
        if ((previous.notes ?: "") != (current.notes ?: "")) changed += SyncField.NOTES
        if ((previous.tags ?: "") != (current.tags ?: "")) changed += SyncField.TAGS
        if (previous.isFavorite != current.isFavorite) changed += SyncField.FAVORITE
        if (previous.isPrivate != current.isPrivate) changed += SyncField.PRIVATE
        if (previous.repeatCount != current.repeatCount) changed += SyncField.REWATCH_COUNT
        // "Tekrar izleniyor" bayrağı ayrı bir alandır: MAL'de `is_rewatching`, Kitsu'da
        // `reconsuming`, Shikimori'de `rewatching` durumu. Bayrak değiştiğinde durum da
        // yazılmalıdır, aksi halde hedefte eski bayrak kalır.
        if (previous.isRepeating != current.isRepeating) {
            changed += SyncField.REWATCH_COUNT
            changed += SyncField.STATUS
        }
        if (previous.repeatValue != current.repeatValue) changed += SyncField.REPEAT_VALUE
        if (previous.priority != current.priority) changed += SyncField.PRIORITY
        if (previous.isHiddenFromStatusLists != current.isHiddenFromStatusLists) {
            changed += SyncField.HIDDEN_FROM_STATUS_LISTS
        }
        return changed
    }

    private fun String?.sameDate(other: String?): Boolean =
        this?.trim().orEmpty() == other?.trim().orEmpty()
}

/**
 * SAĞLAYICI YETENEK MATRİSİ — resmî API sözleşmelerinden çıkarılmıştır.
 *
 * | alan | AniList | MAL | Kitsu | Shikimori | Bangumi | Simkl |
 * |---|---|---|---|---|---|---|
 * | durum | ✓ CURRENT/PLANNING/COMPLETED/DROPPED/PAUSED/REPEATING | ✓ watching/reading/completed/on_hold/dropped/plan_to_* (+is_rewatching) | ✓ current/planned/completed/on_hold/dropped (+reconsuming) | ✓ planned/watching/rewatching/completed/on_hold/dropped | ✓ 1..5 | ✓ watching/plantowatch/completed/hold/notinteresting |
 * | puan | ✓ `scoreRaw` 0-100 | ✓ 0-10 (0 = puanı sil) | ✓ `ratingTwenty` 2-20 | ✓ 0-10 | ✓ 0-10 | ✓ 1-10 |
 * | ilerleme | ✓ progress | ✓ num_watched_episodes / num_chapters_read | ✓ progress | ✓ episodes / chapters | ✓ ep_status (kitap) + bölüm işaretleme | ✓ /sync/history |
 * | cilt | ✓ progressVolumes | ✓ num_volumes_read | ✗ (`volumesOwned` farklı anlam) | ✓ volumes | ✓ vol_status | ✗ |
 * | başlangıç/bitiş tarihi | ✓ FuzzyDate | ✓ start_date/finish_date | ✓ startedAt/finishedAt | ✗ | ✗ | ✗ |
 * | not | ✓ notes | ✓ comments | ✓ notes | ✓ text | ✓ comment (≤380) | ✓ private_memo |
 * | etiket | ✗ | ✓ tags | ✗ | ✗ | ✓ tags (≤10) | ✗ |
 * | favori | ✓ ToggleFavourite | ✗ | ✗ | ✗ | ✗ | ✗ |
 * | gizli | ✓ private | ✗ | ✓ private | ✗ | ✓ private | ✗ |
 * | tekrar sayısı | ✓ repeat | ✓ num_times_rewatched / num_times_reread | ✓ reconsumeCount | ✓ rewatches | ✗ | ✗ |
 * | tekrar değeri | ✗ | ✓ rewatch_value / reread_value | ✗ | ✗ | ✗ | ✗ |
 * | öncelik | ✓ priority 0-255 | ✓ priority 0-2 | ✗ | ✗ | ✗ | ✗ |
 * | listede gizli | ✓ hiddenFromStatusLists | ✗ | ✗ | ✗ | ✗ | ✗ |
 *
 * Kaynaklar: AniList GraphQL şeması (`SaveMediaListEntry`, `MediaListStatus`), MAL API v2
 * (`PATCH /anime|manga/{id}/my_list_status`), Kitsu JSON:API (`library-entries` —
 * `ratingTwenty`, `reconsuming`, `reconsumeCount`), Shikimori API v2 (`user_rates` —
 * `episodes`, `chapters`, `volumes`, `rewatches`, `text`), Bangumi API v0
 * (`POST/PATCH /v0/users/-/collections/{subject_id}` — `type`, `rate`, `ep_status`,
 * `vol_status`, `comment`, `private`, `tags`), Simkl (`/sync/add-to-list`, `/sync/history`,
 * `/sync/ratings`).
 *
 * Ek olarak referans uygulamalar (kaynak kodları incelendi):
 *  - MAL-Sync `_provider/AniList/helper.ts` → `statusTranslate` (CURRENT→Watching, REPEATING→Rewatching)
 *  - MAL-Sync `_provider/MyAnimeList_api/single.ts` → "Rewatching" **durum değildir**:
 *    `status = completed` + `is_rewatching = true`; sayaç `num_times_rewatched`.
 *  - MAL-Sync `_provider/Kitsu/single.ts` → `reconsuming` + `reconsumeCount`, puan `ratingTwenty`.
 *  - MAL-Sync `_provider/Simkl/helper.ts` → `dropped` karşılığı **`notinteresting`**.
 *  - Aniyomi `data/track/{sağlayıcı}/` → aynı eşlemeler; `supportsReadingDates`, `supportsPrivateTracking`.
 */
data class ProviderCapabilities(
    val id: String,
    val supports: Set<SyncField>,
    /** Puan ölçeği: hedefe gönderilen aralık. */
    val scoreScale: ScoreScale,
    /** Hedefin durum sözlüğü (dokümantasyon + test için). */
    val statusVocabulary: Set<String>,
    /**
     * `true` ise "tekrar izleniyor" AYRI bir durumdur (AniList: REPEATING, Kitsu: reconsuming,
     * Shikimori: rewatching). `false` ise durum + sayaç/flag ile ifade edilir (MAL) ya da
     * hiç desteklenmez (Bangumi, Simkl).
     */
    val rewatchingAsStatus: Boolean = false,
    /**
     * "Şu an tekrar izleniyor" durumunun bu hedefte NASIL ifade edildiği.
     *
     *  - [RewatchModel.AS_STATUS]    : ayrı bir durum değeri vardır (AniList `REPEATING`,
     *                                  Shikimori `rewatching`).
     *  - [RewatchModel.AS_FLAG]      : durum ayrı değildir, bayrakla taşınır (MAL
     *                                  `is_rewatching`, Kitsu `reconsuming`). [repeatingStatus]
     *                                  o hedefteki "durum" karşılığıdır.
     *  - [RewatchModel.UNSUPPORTED]  : hiç ifade edilemez (Bangumi, Simkl). Tekrar yüzünden
     *                                  bu hedefin durumu DEĞİŞTİRİLMEZ.
     */
    val rewatchModel: RewatchModel = if (rewatchingAsStatus) RewatchModel.AS_STATUS
    else RewatchModel.UNSUPPORTED,
    /** [RewatchModel.AS_FLAG] hedeflerde `Repeating` durumunun gönderilecek karşılığı. */
    val repeatingStatus: WatchStatus = WatchStatus.Completed,
    /**
     * (E72) `true` ise bu hedefte "tekrar izleniyor" bayrağı YALNIZ belirli bir durumda
     * kabul edilir (Kitsu: `reconsuming` alanı yalnız `status == current` iken gönderilir,
     * bkz. E26). Böyle bir hedefte tekrar başlarken durum da [repeatingStatus]a çekilmelidir,
     * aksi hâlde bayrak sunucuda oluşmaz ve her turda yeniden yazılır (sonsuz döngü).
     *
     * MAL'da böyle bir kısıt YOKTUR: `is_rewatching` durumdan bağımsızdır. Orada durumu
     * korumak gerekir; aksi hâlde yayında olan bir yapım tekrar izlenirken `completed`a
     * çevrilir (veri bozulması) ve içe aktarma `watching` döndüğü için satır her turda
     * yeniden yazılır.
     */
    val rewatchFlagNeedsRepeatingStatus: Boolean = false
) {
    /**
     * Verilen hedef sözlüğünde [merged] durumunun karşılığı.
     *
     * Amaç, "tekrar izleniyor" bilgisini ifade EDEMEYEN hedeflerde gereksiz durum yazımını
     * (ve sonsuz yazım döngüsünü) engellemek: Simkl'de kayıt `completed` ise, kullanıcı
     * AniList'te tekrar izlemeye başladı diye Simkl kaydı `watching`e çekilmemelidir.
     */
    fun statusFor(current: MediaEntry, mergedStatus: WatchStatus): WatchStatus = when {
        mergedStatus != WatchStatus.Repeating -> mergedStatus
        rewatchModel == RewatchModel.AS_STATUS -> WatchStatus.Repeating
        // ── E72 ──────────────────────────────────────────────────────────────────
        // AS_FLAG hedeflerde (MAL `is_rewatching`, Kitsu `reconsuming`) DURUM ile TEKRAR
        // BAYRAĞI birbirinden bağımsızdır. Tekrar yüzünden durumu [repeatingStatus]a
        // zorlamak iki hata üretiyordu:
        //   1. Yayında olan bir yapım tekrar izlenirken MAL'de `watching` iken bizim
        //      yazdığımız `completed` ile **bozuluyordu** (E22'nin yazma tarafındaki
        //      karşılığı eksikti: içe aktarma `watching` + bayrak üretiyor, yazıcı
        //      `completed` istiyordu).
        //   2. Aynı satır her turda yeniden yazılıyordu: içe aktarılan satır
        //      (`watching` + bayrak) ile hedef durum (`completed`) hiç eşitlenmediği
        //      için `dirtyFields` her seferinde STATUS üretiyordu — sonsuz yazım.
        // Doğru davranış: durum KORUNUR, yalnız bayrak yazılır. `repeatingStatus` yalnız
        // henüz bir durumu olmayan (`Planned`) satırda gerekir.
        rewatchModel == RewatchModel.AS_FLAG && !rewatchFlagNeedsRepeatingStatus &&
            current.status != WatchStatus.Planned -> current.status
        rewatchModel == RewatchModel.AS_FLAG -> repeatingStatus
        else -> current.status
    }

    enum class RewatchModel { AS_STATUS, AS_FLAG, UNSUPPORTED }

    fun supports(field: SyncField): Boolean = field in supports

    /** Verilen değişiklik kümesinden bu sağlayıcıya yazılabilecek olanları süzer. */
    fun narrow(fields: Set<SyncField>): Set<SyncField> {
        if (fields.isEmpty()) return emptySet()
        val allowed = fields.filterTo(mutableSetOf()) { it in supports }
        // Bayrak-tabanlı hedeflerde (MAL `is_rewatching`, Kitsu `reconsuming`) tekrar
        // sayısı değişikliği, bayrağı taşıyan durum yazımıyla birlikte gitmelidir; aksi
        // hâlde yalnız sayaç gider, hedef "şu an tekrar" bilgisini hiç öğrenemez.
        //
        // (E76) Bu ekleme YALNIZ `AS_FLAG` içindir. Eski koşul `!rewatchingAsStatus`
        // diyor ve tekrarı HİÇ ifade edemeyen Bangumi/Simkl'i de kapsıyordu: o satırlarda
        // `repeatCount` hep 0 kaldığı (API'de alan yok) için birleşik sayaç büyük olduğu
        // sürece her toplu turda aynı STATUS isteği üretiliyordu — sonsuz yazım (A5).
        if (rewatchModel == RewatchModel.AS_FLAG &&
            SyncField.REWATCH_COUNT in fields && SyncField.STATUS in supports
        ) {
            allowed += SyncField.STATUS
        }
        return allowed
    }

    enum class ScoreScale(val max: Int) {
        /** AniList `scoreRaw`: 0..100 */
        HUNDRED(100),

        /** Kitsu `ratingTwenty`: 2..20 */
        TWENTY(20),

        /** MAL / Shikimori / Bangumi / Simkl: 0..10 (Simkl alt sınırı 1) */
        TEN(10)
    }
}

object ProviderCapabilitiesCatalog {

    private val aniList = ProviderCapabilities(
        id = "anilist",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS, SyncField.VOLUME_PROGRESS,
            SyncField.START_DATE, SyncField.END_DATE, SyncField.NOTES, SyncField.FAVORITE,
            SyncField.PRIVATE, SyncField.REWATCH_COUNT, SyncField.PRIORITY,
            SyncField.HIDDEN_FROM_STATUS_LISTS
        ),
        scoreScale = ProviderCapabilities.ScoreScale.HUNDRED,
        statusVocabulary = setOf("CURRENT", "PLANNING", "COMPLETED", "DROPPED", "PAUSED", "REPEATING"),
        rewatchingAsStatus = true
    )

    private val mal = ProviderCapabilities(
        id = "mal",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS, SyncField.VOLUME_PROGRESS,
            SyncField.START_DATE, SyncField.END_DATE, SyncField.NOTES, SyncField.TAGS,
            SyncField.REWATCH_COUNT, SyncField.REPEAT_VALUE, SyncField.PRIORITY
        ),
        scoreScale = ProviderCapabilities.ScoreScale.TEN,
        // MAL'de tekrar ayrı durum değildir: `completed` + `is_rewatching` (MAL-Sync aynı).
        rewatchModel = ProviderCapabilities.RewatchModel.AS_FLAG,
        repeatingStatus = WatchStatus.Completed,
        statusVocabulary = setOf(
            "watching", "reading", "completed", "on_hold", "dropped", "plan_to_watch", "plan_to_read"
        ),
        rewatchingAsStatus = false
    )

    private val kitsu = ProviderCapabilities(
        id = "kitsu",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS,
            SyncField.START_DATE, SyncField.END_DATE, SyncField.NOTES, SyncField.PRIVATE,
            SyncField.REWATCH_COUNT
        ),
        scoreScale = ProviderCapabilities.ScoreScale.TWENTY,
        // Kitsu'da tekrar ayrı durum değildir: `current` + `reconsuming`.
        rewatchModel = ProviderCapabilities.RewatchModel.AS_FLAG,
        repeatingStatus = WatchStatus.Watching,
        statusVocabulary = setOf("current", "planned", "completed", "on_hold", "dropped"),
        rewatchingAsStatus = false,
        // Kitsu `reconsuming` yalnız `status == current` iken gönderilir (E26) → durum
        // "current"a çekilmeden bayrak sunucuda oluşmaz.
        rewatchFlagNeedsRepeatingStatus = true
    )

    private val shikimori = ProviderCapabilities(
        id = "shikimori",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS,
            SyncField.VOLUME_PROGRESS, SyncField.NOTES, SyncField.REWATCH_COUNT
        ),
        scoreScale = ProviderCapabilities.ScoreScale.TEN,
        statusVocabulary = setOf("planned", "watching", "rewatching", "completed", "on_hold", "dropped"),
        rewatchingAsStatus = true
    )

    private val bangumi = ProviderCapabilities(
        id = "bangumi",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS, SyncField.VOLUME_PROGRESS,
            SyncField.NOTES, SyncField.TAGS, SyncField.PRIVATE
        ),
        scoreScale = ProviderCapabilities.ScoreScale.TEN,
        statusVocabulary = setOf("1", "2", "3", "4", "5"),
        rewatchingAsStatus = false
    )

    private val simkl = ProviderCapabilities(
        id = "simkl",
        supports = setOf(
            SyncField.STATUS, SyncField.SCORE, SyncField.PROGRESS, SyncField.NOTES
        ),
        scoreScale = ProviderCapabilities.ScoreScale.TEN,
        statusVocabulary = setOf("watching", "plantowatch", "completed", "hold", "notinteresting"),
        rewatchingAsStatus = false
    )

    private val byId: Map<String, ProviderCapabilities> = listOf(aniList, mal, kitsu, shikimori, bangumi, simkl)
        .associateBy { it.id }

    /**
     * @param provider kanonik sağlayıcı anahtarı ("anilist", "mal", "kitsu", "shikimori",
     * "bangumi", "simkl"). Bilinmeyen sağlayıcı için tüm alanlar açık kabul edilir: kimliği
     * henüz tanımlanmamış yeni bir hedef, eski (tam yazım) davranışını korur.
     */
    fun of(provider: String): ProviderCapabilities =
        byId[CrossProviderIds.normalizeSource(provider)] ?: ProviderCapabilities(
            id = provider,
            supports = SyncField.ALL,
            scoreScale = ProviderCapabilities.ScoreScale.TEN,
            statusVocabulary = emptySet()
        )

    val all: List<ProviderCapabilities> get() = byId.values.toList()
}

/** [SyncField] kümesini [provider] hedefine yazılabilecek alanlara indirger. */
fun Set<SyncField>.narrowTo(provider: String): Set<SyncField> =
    ProviderCapabilitiesCatalog.of(provider).narrow(this)
