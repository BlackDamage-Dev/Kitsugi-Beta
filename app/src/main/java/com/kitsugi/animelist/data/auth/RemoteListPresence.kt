package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/**
 * HEDEF LİSTEDE KAYDIN VAR OLDUĞU BİLGİSİ (kalıcı).
 *
 * Neden gerekli: eşitleme v2.4.34x'ten sonra **alan bazlı** çalışıyor — yalnızca yerelde
 * değişen alanlar hedefe yazılıyor. Bu doğru bir tasarım (hedefte elle girilmiş puan/not
 * ezilmesin) ama tek başına **eksik kaydı onarmıyordu**:
 *
 *  - Kullanıcı yalnızca "favori" ya da "etiket" değiştirdiğinde Simkl/Kitsu/Shikimori/Bangumi
 *    için yazılacak alan kalmıyor, arayüz "değişiklik yok" diyordu — oysa kayıt bu
 *    kaynaklarda **hiç yoktu** (ör. Kitsu'dan içe aktarılmış bir satır) veya eskimişti.
 *  - Kısmi yazım hedefte kayıt yokken **yarım kayıt** oluşturuyordu (Simkl'de puan var,
 *    liste kaydı yok → kullanıcı Simkl'de hiçbir şey görmüyor).
 *
 * Çözüm: her (yapıt, sağlayıcı) çifti için "kayıt orada VAR" bilgisi kalıcı tutulur.
 *  - VAR değilse (hiç denenmemiş ya da başarısız olmuş) → alan süzmesi UYGULANMAZ, hedefe
 *    **tam yazım** gider. Tam yazımda boş alanlar gönderilmez, yani hedefteki değerler
 *    silinmez; kayıt yoksa oluşturulur, varsa güncellenir.
 *  - VAR ise → yalnızca gerçekten değişen alanlar yazılır; değişen alan yoksa istek atılmaz
 *    ve sonuç dürüstçe "zaten eşit" olarak raporlanır.
 *
 * Kayıtlar yapıtın **kanonik kimliğiyle** (MAL → AniList → Kitsu → … sırasıyla ilk dolu kimlik)
 * tutulur; böylece aynı yapının Kitsu/AniList/Bangumi satırları aynı varlık kaydını paylaşır.
 */
object RemoteListPresence {

    private const val PREFS_NAME = "remote_list_presence_v1"

    /** Bir satırın varlığı doğrulanmadan önce, aynı satır için en fazla bu sıklıkla onarım denenir. */
    internal const val REPAIR_THROTTLE_MS = 10L * 60L * 1000L

    private const val SEP = '|'

    /** Eşitlemenin yazabildiği altı sağlayıcının kanonik anahtarları. */
    val PROVIDERS: List<String> = listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Yapıtın kanonik anahtarı. Kimlik alanları tercih sırasıyla kullanılır; hiçbiri yoksa
     * normalize edilmiş başlık + yıl kullanılır (yalnızca son çare).
     *
     * @param identity çözülmüş çapraz kimlikler ([CrossProviderIdentityResolver] / [CrossProviderIds.decode]).
     */
    fun keyFor(entry: MediaEntry, identity: CrossProviderIds): String = keyFor(
        typeName = entry.type.name,
        malId = identity.malId,
        aniListId = identity.aniListId,
        kitsuId = identity.kitsuId,
        shikimoriId = identity.shikimoriId,
        bangumiSubjectId = identity.bangumiSubjectId,
        simklId = identity.simklId,
        tmdbId = identity.tmdbId,
        title = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title,
        year = entry.year
    )

    /** Ağa çıkmadan (yalnızca satırın kendi kimlikleriyle) anahtar üretir. */
    fun keyFor(entry: MediaEntry): String = keyFor(
        entry,
        CrossProviderIds.decode(
            storedId = entry.malId,
            source = entry.source,
            type = entry.type,
            entrySimklId = entry.simklId,
            entryTmdbId = entry.tmdbId
        )
    )

    internal fun keyFor(
        typeName: String?,
        malId: Int?,
        aniListId: Int?,
        kitsuId: Int?,
        shikimoriId: Int?,
        bangumiSubjectId: Int?,
        simklId: Int?,
        tmdbId: Int?,
        title: String?,
        year: Int?
    ): String {
        val bestId = listOfNotNull(
            malId?.takeIf { it > 0 }?.let { "mal:$it" },
            aniListId?.takeIf { it > 0 }?.let { "anilist:$it" },
            kitsuId?.takeIf { it > 0 }?.let { "kitsu:$it" },
            shikimoriId?.takeIf { it > 0 }?.let { "shikimori:$it" },
            bangumiSubjectId?.takeIf { it > 0 }?.let { "bangumi:$it" },
            simklId?.takeIf { it > 0 }?.let { "simkl:$it" },
            tmdbId?.takeIf { it > 0 }?.let { "tmdb:$it" }
        ).firstOrNull() ?: run {
            val normalized = MediaIdentity.normalizedTitle(title.orEmpty())
            "title:$normalized:${year ?: 0}"
        }
        return "${typeName ?: "Unknown"}:$bestId"
    }

    private fun slot(key: String, provider: String) = "$key$SEP${provider.lowercase()}"

    /** Hedefte kaydın VAR olduğu doğrulandı mı? (doğrulanmadıysa tam yazım gerekir) */
    fun isPresent(context: Context, key: String, provider: String): Boolean =
        runCatching { prefs(context).getBoolean(slot(key, provider), false) }.getOrDefault(false)

    /**
     * Kaydı günceller. [present] = false → kayıt yok/başarısız; sonraki turda tam yazım denenir.
     */
    fun record(context: Context, key: String, provider: String, present: Boolean) {
        runCatching {
            prefs(context).edit()
                .putBoolean(slot(key, provider), present)
                .apply()
        }
    }

    /**
     * Satırın KENDİ kaynağında kayıt zaten var (içe aktarılmış satır oradan geldi).
     * Yalnızca ilk kez yazılır; daha önce "yok" olarak işaretlenmişse ezilmez.
     */
    fun seedOwnSource(context: Context, key: String, source: String?) {
        val provider = CrossProviderIds.normalizeSource(source)
        if (provider !in PROVIDERS) return
        val slotKey = slot(key, provider)
        runCatching {
            val p = prefs(context)
            if (!p.contains(slotKey)) {
                p.edit().putBoolean(slotKey, true).apply()
            }
        }
    }

    /**
     * Değişen alan yokken bile onarım turu çalıştırılmalı mı?
     * Aynı satır için [REPAIR_THROTTLE_MS] içinde ikinci kez ağa çıkılmaz; damga burada atılır.
     */
    fun beginRepairIfDue(context: Context, key: String): Boolean {
        val now = System.currentTimeMillis()
        return runCatching {
            val p = prefs(context)
            val stampSlot = "$key$SEP" + "repair-at"
            val last = p.getLong(stampSlot, 0L)
            if (now - last < REPAIR_THROTTLE_MS) return false
            p.edit().putLong(stampSlot, now).apply()
            true
        }.getOrDefault(false)
    }

    /** Tüm varlık kayıtlarını temizler (hesap değişimi / tüm verileri silme). */
    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }
}

/**
 * Tek bir sağlayıcı için yazım planı.
 *
 * @param fields hedefe yazılacak alanlar
 * @param fullWrite tam yazım mı (boş yerel alanlar GÖNDERİLMEZ; hedefteki değer korunur)
 */
internal data class SyncWritePlan(
    val fields: Set<SyncField>,
    val fullWrite: Boolean
)

/**
 * Alan süzmesi + varlık bilgisinden yazım planı üretir (saf fonksiyon; birim testleriyle sınanır).
 *
 * Kural: hedefte kaydın VAR olduğu doğrulanmadıysa alan süzmesi uygulanmaz. Aksi hâlde
 * (eski hata) yalnızca bir alan değiştiğinde diğer beş kaynağa hiç dokunulmuyor ya da hedefte
 * yarım kayıt oluşuyordu — kullanıcı ise o kaynaklarda hiçbir değişiklik görmüyordu.
 */
internal fun planSyncWrite(
    requested: Set<SyncField>,
    isFullWrite: Boolean,
    presenceConfirmed: Boolean
): SyncWritePlan = if (presenceConfirmed) {
    SyncWritePlan(fields = requested, fullWrite = isFullWrite)
} else {
    // Kayıt yok/doğrulanmadı: tam yazım (mevcut değerler boş alanlarla silinmez).
    SyncWritePlan(fields = SyncField.ALL, fullWrite = true)
}
