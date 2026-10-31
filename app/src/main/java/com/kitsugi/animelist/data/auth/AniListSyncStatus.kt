package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import java.util.Locale

/**
 * AniList eşitlemesinin **neden** durduğunu kalıcı olarak kaydeder ve kullanıcıya
 * anlaşılır tek bir cümleyle söyler (v2.4.341).
 *
 * ## Neden gerekli?
 * Kullanıcı geri bildirimi: *"Listem'de günlerdir eklediğim hiçbir içerik AniList ile
 * eşitlenmemiş ama ne olduğunu anlamıyorum."* Eski kod, bir yazma atlandığında
 * (`bağlı değil`, `kimlik çözülemedi`, `kapalı`) **hiçbir mesaj üretmiyordu**; hata
 * durumunda ise yalnızca "📵 Çevrimdışı kaydedildi" gibi nedeni gizleyen genel bir metin
 * gösteriyordu. Sonuç: sessiz veri kaybı ve kafa karışıklığı.
 *
 * Bu nesne, son başarılı yazmanın zamanını ve son başarısızlığın **sınıflandırılmış**
 * nedenini tutar. Liste ekranı bu bilgiyi bir uyarı şeridi olarak gösterir; böylece
 * kullanıcı "kota mı, oturum mu, kimlik mi, internet mi?" sorusunun cevabını görür.
 */
object AniListSyncStatus {

    /** Eşitlemenin durduğu gerekçe. */
    enum class Reason {
        /** Henüz bir sonuç yok. */
        NONE,

        /** Son yazma başarılı. */
        OK,

        /** AniList kota/429 ceza penceresi. */
        QUOTA,

        /** Oturum/anahtar geçersiz (401/403/invalid token). */
        AUTH,

        /** AniList bağlı değil (anahtar yok). */
        NOT_CONNECTED,

        /** Ayarlardan AniList eşitlemesi kapatılmış. */
        DISABLED,

        /** İnternet yok / DNS-timeout. */
        OFFLINE,

        /** AniList API geçici olarak kapalı (403 "temporarily disabled") veya 5xx. */
        SERVICE_DOWN,

        /** Medya kimliği güvenle doğrulanamadı (yanlış yapıma yazmamak için atlandı). */
        IDENTITY,

        /** Sınıflandırılamayan hata. */
        UNKNOWN
    }

    data class Snapshot(
        val lastSuccessAt: Long = 0L,
        val lastFailureAt: Long = 0L,
        val reason: Reason = Reason.NONE,
        val detail: String? = null
    ) {
        /** Kota/oturum/kimlik gibi durumlarda false: kullanıcıya gösterilir. */
        val isHealthy: Boolean
            get() = reason == Reason.NONE || reason == Reason.OK
    }

    private const val PREFS = "kitsugi_anilist_sync_status"
    private const val KEY_LAST_SUCCESS = "last_success_at"
    private const val KEY_LAST_FAILURE = "last_failure_at"
    private const val KEY_REASON = "reason"
    private const val KEY_DETAIL = "detail"

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = runCatching {
            Reason.valueOf(prefs.getString(KEY_REASON, Reason.NONE.name) ?: Reason.NONE.name)
        }.getOrDefault(Reason.NONE)
        return Snapshot(
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L),
            lastFailureAt = prefs.getLong(KEY_LAST_FAILURE, 0L),
            reason = reason,
            detail = prefs.getString(KEY_DETAIL, null)?.takeIf { it.isNotBlank() }
        )
    }

    fun reportSuccess(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .putString(KEY_REASON, Reason.OK.name)
            .remove(KEY_DETAIL)
            .apply()
    }

    fun reportFailure(context: Context, reason: Reason, detail: String? = null) {
        if (reason == Reason.NONE || reason == Reason.OK) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_FAILURE, System.currentTimeMillis())
            .putString(KEY_REASON, reason.name)
            .putString(KEY_DETAIL, detail?.take(200))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ─── Sınıflandırma ───────────────────────────────────────────────────────

    /** Hata metnini (ve/veya kota soğumasını) gerekçeye çevirir. */
    fun classify(errorText: String?, coolingDown: Boolean = false): Reason {
        if (coolingDown) return Reason.QUOTA
        val text = errorText.orEmpty()
        if (text.isBlank()) return Reason.UNKNOWN
        if (AniListGate.isQuotaMessage(text)) return Reason.QUOTA

        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("401") || lower.contains("unauthorized") ||
                lower.contains("invalid token") || lower.contains("invalid_token") ||
                lower.contains("forbidden") || lower.contains("oturum") -> Reason.AUTH

            lower.contains("offline") || lower.contains("çevrimdışı") ||
                lower.contains("unable to resolve host") || lower.contains("failed to connect") ||
                lower.contains("timeout") || lower.contains("timed out") ||
                lower.contains("unknownhost") || lower.contains("no address") -> Reason.OFFLINE

            lower.contains("503") || lower.contains("temporarily disabled") ||
                lower.contains("devre dışı") || lower.contains("gateway") -> Reason.SERVICE_DOWN

            lower.contains("kimlik") || lower.contains("identity") ||
                lower.contains("unresolved") || lower.contains("doğrulan") -> Reason.IDENTITY

            else -> Reason.UNKNOWN
        }
    }

    /** Kaynak sonucu (`outcomes`) atlanmışsa nedenini verir. */
    fun classifyOutcome(outcome: String?): Reason = when (outcome) {
        null, "" -> Reason.NONE
        ExternalListSyncManager.OUTCOME_SYNCED -> Reason.OK
        ExternalListSyncManager.OUTCOME_DISABLED -> Reason.DISABLED
        ExternalListSyncManager.OUTCOME_NOT_CONNECTED -> Reason.NOT_CONNECTED
        ExternalListSyncManager.OUTCOME_SKIPPED_IDENTITY,
        ExternalListSyncManager.OUTCOME_SKIPPED_TYPE -> Reason.IDENTITY
        ExternalListSyncManager.OUTCOME_FAILED -> Reason.UNKNOWN
        else -> Reason.NONE
    }

    /**
     * Kullanıcıya gösterilecek **tek** cümle. [pending] bekleyen değişiklik sayısıdır.
     * Kota için kalan saniye [waitSeconds] ile verilir.
     */
    fun describe(
        context: Context,
        snapshot: Snapshot,
        pending: Int,
        waitSeconds: Long = (AniListGate.cooldownRemainingMs() / 1_000L).coerceAtLeast(1L)
    ): String {
        if (snapshot.isHealthy && pending <= 0) return ""
        val count = pending.coerceAtLeast(1)
        return when (snapshot.reason) {
            Reason.QUOTA -> context.getString(R.string.anilist_sync_stalled_quota, count, waitSeconds)
            Reason.AUTH -> context.getString(R.string.anilist_sync_stalled_auth, count)
            Reason.NOT_CONNECTED -> context.getString(R.string.anilist_sync_stalled_not_connected, count)
            Reason.DISABLED -> context.getString(R.string.anilist_sync_stalled_disabled, count)
            Reason.OFFLINE -> context.getString(R.string.anilist_sync_stalled_offline, count)
            Reason.SERVICE_DOWN -> context.getString(R.string.anilist_sync_stalled_service, count)
            Reason.IDENTITY -> context.getString(R.string.anilist_sync_stalled_identity, count)
            Reason.UNKNOWN, Reason.NONE, Reason.OK -> if (pending > 0) {
                context.getString(R.string.anilist_sync_stalled_generic, count)
            } else {
                ""
            }
        }
    }
}
