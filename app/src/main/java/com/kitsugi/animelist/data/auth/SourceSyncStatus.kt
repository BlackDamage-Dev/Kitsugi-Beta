package com.kitsugi.animelist.data.auth

import android.content.Context
import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.diagnostics.KitsugiLog
import java.util.Locale

/**
 * **Tüm liste kaynaklarının** (AniList · MyAnimeList/Jikan · Kitsu · Shikimori · Simkl ·
 * Bangumi) arka plandaki eşitleme durumunu kalıcı olarak tutar ve kullanıcıya
 * "hangi kaynak, neden, ne yapmalı" üçlüsünü **halk diliyle** söyler (v2.4.341).
 *
 * ## Neden gerekli?
 * Kullanıcı geri bildirimi: *"Listem'de günlerdir eklediğim hiçbir içerik AniList ile
 * eşitlenmemiş ama nedenini anlamıyorum."* Eski kod; bir yazma atlandığında
 * (`bağlı değil`, `kimlik çözülemedi`, `kapalı`) **hiçbir mesaj üretmiyor**, hata
 * durumunda ise her şeyi "📵 Çevrimdışı kaydedildi" diye özetliyordu. Üstelik bu yalnızca
 * AniList için değil, altı kaynağın tamamı için geçerliydi.
 *
 * Bu nesne kaynak başına:
 *  - son **başarılı** yazmanın zamanını,
 *  - son **başarısız** yazmanın sınıflandırılmış nedenini ([Reason]),
 *  - insan dilinde açıklamayı ([describe]) tutar.
 *
 * Arayüz (Listem durum şeridi, bildirim ekranı, işlem bildirimleri) buradan beslenir;
 * böylece aynı durum her yerde aynı cümleyle anlatılır.
 */
object SourceSyncStatus {

    /** Kullanıcının bağlayabildiği liste kaynakları. */
    enum class Source(val key: String, @StringRes val labelRes: Int) {
        ANILIST("anilist", R.string.pf_err_platform_anilist),
        MAL("mal", R.string.pf_err_platform_mal),
        KITSU("kitsu", R.string.pf_err_platform_kitsu),
        SHIKIMORI("shikimori", R.string.pf_err_platform_shikimori),
        SIMKL("simkl", R.string.pf_err_platform_simkl),
        BANGUMI("bangumi", R.string.pf_err_platform_bangumi);

        companion object {
            /** `MediaEntry.source` değerinden kaynak bulur (`jikan` → MAL). */
            fun fromEntrySource(source: String?): Source? = when (source?.lowercase(Locale.ROOT)) {
                "anilist" -> ANILIST
                "mal", "myanimelist", "jikan" -> MAL
                "kitsu" -> KITSU
                "shikimori" -> SHIKIMORI
                "simkl" -> SIMKL
                "bangumi" -> BANGUMI
                else -> null
            }

            /** `SyncResult.outcomes` anahtarındaki görünen addan kaynak bulur. */
            fun fromOutcomeLabel(label: String?): Source? = when (label?.trim()?.lowercase(Locale.ROOT)) {
                "anilist", "ani list" -> ANILIST
                "myanimelist", "my anime list", "mal", "jikan" -> MAL
                "kitsu" -> KITSU
                "shikimori" -> SHIKIMORI
                "simkl" -> SIMKL
                "bangumi", "bgm.tv" -> BANGUMI
                else -> null
            }
        }
    }

    /** Eşitlemenin durduğu gerekçe. */
    enum class Reason {
        /** Henüz bir sonuç yok. */
        NONE,

        /** Son yazma başarılı. */
        OK,

        /** Kota / 429 ceza penceresi. */
        QUOTA,

        /** Oturum/anahtar geçersiz (401/403/invalid token). */
        AUTH,

        /** Hesap bağlı değil (anahtar yok). */
        NOT_CONNECTED,

        /** Ayarlardan o kaynağın eşitlemesi kapatılmış. */
        DISABLED,

        /** İnternet yok / DNS-timeout. */
        OFFLINE,

        /** Kaynağın API'si geçici olarak kapalı (403 "temporarily disabled") veya 5xx. */
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
        val isHealthy: Boolean
            get() = reason == Reason.NONE || reason == Reason.OK
    }

    /**
     * Gerekçe **gerçek bir sorun mu**, yoksa yalnızca bilgilendirme mi? (v2.4.341)
     *
     * Kullanıcı geri bildirimi: *"kapalı/bağlı değil gibi bilgiler kırmızı hata gibi
     * görünmesin"*. Arayüz buna göre renk seçer: hata → kırmızı ton, bilgi → sakin ton.
     */
    fun isError(reason: Reason): Boolean = when (reason) {
        Reason.QUOTA, Reason.AUTH, Reason.OFFLINE, Reason.SERVICE_DOWN,
        Reason.IDENTITY, Reason.UNKNOWN -> true
        // "Kapalı" ve "bağlı değil" kullanıcının kendi seçimi / henüz kurmadığı bir şey:
        // bilgi tonunda gösterilir, hata gibi bağırmaz.
        Reason.DISABLED, Reason.NOT_CONNECTED, Reason.NONE, Reason.OK -> false
    }

    private const val PREFS = "kitsugi_source_sync_status"

    private fun key(source: Source, suffix: String) = "${source.key}_$suffix"

    fun snapshot(context: Context, source: Source): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = runCatching {
            Reason.valueOf(prefs.getString(key(source, "reason"), Reason.NONE.name) ?: Reason.NONE.name)
        }.getOrDefault(Reason.NONE)
        return Snapshot(
            lastSuccessAt = prefs.getLong(key(source, "last_success_at"), 0L),
            lastFailureAt = prefs.getLong(key(source, "last_failure_at"), 0L),
            reason = reason,
            detail = prefs.getString(key(source, "detail"), null)?.takeIf { it.isNotBlank() }
        )
    }

    /** Tüm bağlı/izlenen kaynakların son durumu (kayıt yoksa [Reason.NONE]). */
    fun snapshotAll(context: Context): Map<Source, Snapshot> =
        Source.entries.associateWith { snapshot(context, it) }

    fun reportSuccess(context: Context, source: Source) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(source, "last_success_at"), System.currentTimeMillis())
            .putString(key(source, "reason"), Reason.OK.name)
            .remove(key(source, "detail"))
            .apply()
        // v2.4.342: eşitleme sonucu kalıcı günlüğe de yazılır (kaynak bazında).
        runCatching {
            KitsugiLog.i(KitsugiLog.Channel.SYNC, source.key, "eşitleme başarılı", source.name)
        }
    }

    fun reportFailure(context: Context, source: Source, reason: Reason, detail: String? = null) {
        if (reason == Reason.NONE || reason == Reason.OK) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(source, "last_failure_at"), System.currentTimeMillis())
            .putString(key(source, "reason"), reason.name)
            .putString(key(source, "detail"), detail?.take(200))
            .apply()
        // v2.4.342: "hangi kaynak neden durdu" kalıcı günlükte de durur; kullanıcı
        // ekranda gördüğü cümlenin kanıtını sonradan paylaşabilir.
        runCatching {
            val level = if (reason == Reason.UNKNOWN) KitsugiLog.Level.ERROR else KitsugiLog.Level.WARN
            KitsugiLog.log(
                level = level,
                channel = KitsugiLog.Channel.SYNC,
                tag = source.key,
                message = "eşitleme durdu: ${reason.name.lowercase()}",
                detail = detail?.take(500)
            )
        }
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
        if (AniListGate.isQuotaMessage(text) || looksRateLimited(text)) return Reason.QUOTA

        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("401") || lower.contains("unauthorized") ||
                lower.contains("invalid token") || lower.contains("invalid_token") ||
                lower.contains("forbidden") || lower.contains("oturum") ||
                lower.contains("session") && lower.contains("expired") -> Reason.AUTH

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

    /** 429/rate-limit ifadeleri (kaynak bağımsız). */
    private fun looksRateLimited(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("too many requests")) return true
        if (!lower.contains("429")) return false
        return lower.contains("rate") || lower.contains("limit") || lower.contains("hız") ||
            lower.contains("sınır") || lower.contains("kota")
    }

    /** `SyncResult.outcomes` değerini gerekçeye çevirir. */
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

    // ─── İnsan dili ──────────────────────────────────────────────────────────

    /**
     * Bekleyen değişiklik sayısıyla birlikte **tek cümlelik** durum açıklaması.
     * Sağlıklıysa ve bekleyen bir şey yoksa boş metin döner (şerit gizlenir).
     */
    fun describe(
        context: Context,
        source: Source,
        snapshot: Snapshot,
        pending: Int,
        waitSeconds: Long = (AniListGate.cooldownRemainingMs() / 1_000L).coerceAtLeast(1L)
    ): String {
        if (snapshot.isHealthy && pending <= 0) return ""
        val label = context.getString(source.labelRes)
        val count = pending.coerceAtLeast(1)
        return when (snapshot.reason) {
            Reason.QUOTA -> context.getString(R.string.sync_status_quota, label, count, waitSeconds)
            Reason.AUTH -> context.getString(R.string.sync_status_auth, label, count)
            Reason.NOT_CONNECTED -> context.getString(R.string.sync_status_not_connected, label, count)
            Reason.DISABLED -> context.getString(R.string.sync_status_disabled, label, count)
            Reason.OFFLINE -> context.getString(R.string.sync_status_offline, label, count)
            Reason.SERVICE_DOWN -> context.getString(R.string.sync_status_service, label, count)
            Reason.IDENTITY -> context.getString(R.string.sync_status_identity, label, count)
            Reason.UNKNOWN, Reason.NONE, Reason.OK ->
                if (pending > 0) context.getString(R.string.sync_status_generic, label, count) else ""
        }
    }

    /**
     * Tek bir işlemin hemen ardından gösterilen kısa mesaj (bildirim/snackbar).
     * [count] o kaynak için kuyrukta bekleyen toplam değişiklik sayısıdır.
     */
    fun describeAction(
        context: Context,
        source: Source,
        reason: Reason,
        count: Int = 1,
        waitSeconds: Long = (AniListGate.cooldownRemainingMs() / 1_000L).coerceAtLeast(1L)
    ): String {
        val label = context.getString(source.labelRes)
        return when (reason) {
            Reason.QUOTA -> context.getString(R.string.sync_snack_quota, label, count.coerceAtLeast(1), waitSeconds)
            Reason.AUTH -> context.getString(R.string.sync_snack_auth, label)
            Reason.NOT_CONNECTED -> context.getString(R.string.sync_snack_not_connected, label)
            Reason.DISABLED -> context.getString(R.string.sync_snack_disabled, label)
            Reason.OFFLINE -> context.getString(R.string.sync_snack_offline, label)
            Reason.SERVICE_DOWN -> context.getString(R.string.sync_snack_service, label)
            Reason.IDENTITY -> context.getString(R.string.sync_snack_identity, label)
            else -> context.getString(R.string.sync_snack_generic, label)
        }
    }
}
