package com.kitsugi.animelist.core.notifications

import android.content.Context

/**
 * "Bildirim gerçekten düştü mü?" sorusunun kanıtı.
 *
 * [AiringNotificationWorker] her çalıştığında buraya **kaynak başına** tek satır yazar:
 *   • son çalışma zamanı,
 *   • o çalıştırmada gönderilen (cihaza düşen) bildirim sayısı,
 *   • görülen (yeni olmayan dâhil) kayıt sayısı,
 *   • varsa hata metni.
 *
 * Ayrıca hat bir bütün olarak engelliyse (izin yok / bildirimler kullanıcı tarafından
 * kapalı) sebep kaydedilir. Bildirimler sayfasındaki teşhis paneli bu kaydı okur; böylece
 * kullanıcı "ağ çalışıyor ama telefonumda hiçbir şey yok" durumunu ayırt edebilir.
 *
 * Depolama bilinçli olarak SharedPreferences: WorkManager çalıştırması sırasında dosya
 * yazımı yok, panelden okunması senkron ve ucuz.
 */
object NotificationDeliveryTracker {

    const val BLOCKED_PERMISSION = "PERMISSION"
    const val BLOCKED_DISABLED = "DISABLED"

    private const val PREFS = "kitsugi_notif_delivery"
    private const val KEY_LAST_RUN = "last_run"
    private const val KEY_TOTAL_POSTED = "total_posted"
    private const val KEY_BLOCKED = "blocked_reason"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tek kaynağın son teslim özeti. */
    data class SourceDelivery(
        val source: String,
        val lastRunAtMs: Long,
        val lastPosted: Int,
        val lastSeen: Int,
        val lastError: String?
    )

    /** Panelin okuduğu tam durum. */
    data class Snapshot(
        val lastRunAtMs: Long,
        val totalPosted: Int,
        val blockedReason: String?,
        val sources: List<SourceDelivery>
    )

    fun recordRun(
        context: Context,
        source: String,
        posted: Int,
        seen: Int,
        error: String? = null
    ) {
        val now = System.currentTimeMillis()
        val key = sourceKey(source)
        val p = prefs(context)
        p.edit()
            .putLong(KEY_LAST_RUN, now)
            .putLong("src_${key}_at", now)
            .putInt("src_${key}_posted", posted)
            .putInt("src_${key}_seen", seen)
            .apply {
                if (error == null) remove("src_${key}_error") else putString("src_${key}_error", error)
                val previousTotal = p.getInt(KEY_TOTAL_POSTED, 0)
                putInt(KEY_TOTAL_POSTED, previousTotal + posted)
                // recordRun yalnızca hat AÇIKKEN çağrılır; eski engel notu bayat kalmasın.
                remove(KEY_BLOCKED)
            }
            .apply()
    }

    /** Hat bütün olarak engellendiğinde çağrılır; son çalışma zamanı da güncellenir. */
    fun recordBlocked(context: Context, reason: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putString(KEY_BLOCKED, reason)
            .apply()
    }

    fun clearBlocked(context: Context) {
        prefs(context).edit().remove(KEY_BLOCKED).apply()
    }

    fun snapshot(context: Context, sources: List<String>): Snapshot {
        val p = prefs(context)
        return Snapshot(
            lastRunAtMs = p.getLong(KEY_LAST_RUN, 0L),
            totalPosted = p.getInt(KEY_TOTAL_POSTED, 0),
            blockedReason = p.getString(KEY_BLOCKED, null),
            sources = sources.map { source ->
                val key = sourceKey(source)
                SourceDelivery(
                    source = source,
                    lastRunAtMs = p.getLong("src_${key}_at", 0L),
                    lastPosted = p.getInt("src_${key}_posted", 0),
                    lastSeen = p.getInt("src_${key}_seen", 0),
                    lastError = p.getString("src_${key}_error", null)
                )
            }
        )
    }

    /** Kaynak adını sabit anahtara çevirir ("TMDB & Simkl" → "simkl"). */
    fun sourceKey(source: String): String = when {
        source.contains("anilist", ignoreCase = true) -> "anilist"
        source.contains("mal", ignoreCase = true) ||
            source.contains("myanimelist", ignoreCase = true) -> "mal"
        source.contains("simkl", ignoreCase = true) ||
            source.contains("tmdb", ignoreCase = true) -> "simkl"
        source.contains("kitsu", ignoreCase = true) -> "kitsu"
        source.contains("shikimori", ignoreCase = true) -> "shikimori"
        source.contains("bangumi", ignoreCase = true) -> "bangumi"
        else -> source.lowercase().replace(" ", "_")
    }
}
