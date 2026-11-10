package com.kitsugi.animelist.data.platform

import android.content.Context
import android.content.res.Configuration
import com.kitsugi.animelist.R
import java.util.Locale

/**
 * Kitsugi — liste kaynağı hata sınıflandırıcısı.
 *
 * Görevi: bir platform isteği başarısız olduğunda ortaya çıkan ham sinyali
 * (HTTP kodu, istisna sınıfı, sunucu mesajı) **sabit bir [PlatformErrorCode]**'a
 * çevirmek ve kullanıcıya "hangi kaynak, neden, ne yapmalı" üçlüsünü gösterebilmek.
 *
 * Tasarım kuralları:
 *  - Önce **platforma özgü** imzalar denenir (ör. Bangumi'nin `nsfw` → 400 tuhaflığı,
 *    Shikimori `redirect_uri` hatası, Simkl PIN süresi). Bunlar kullanıcıya en çok
 *    bilgi veren, en somut nedenlerdir.
 *  - Sonra **ortak HTTP** kodları (401/403/404/429/5xx) eşlenir.
 *  - Sonra **istisna sınıfı ve mesaj kalıpları** (DNS, TLS, timeout, ayrıştırma…).
 *  - Hiçbiri tutmazsa [PlatformErrorCode.COM_1020] ("sınıflandırılamayan hata") döner;
 *    bu durumda UI ham ayrıntıyı gösterir ki hata kaybolmasın.
 *
 * Metinler dil dosyasında tutulur (`values/` = TR, `values-en/` = EN); bu nesne
 * yalnızca kodu ve dili birleştirir.
 */
object PlatformErrorCatalog {

    private val httpCodePatterns = listOf(
        Regex("""\bhttps?(?:/\d+(?:\.\d+)?)?\s*[:=#-]?\s*(\d{3})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:status|statuscode|responsecode|httpstatus|kod)\s*[:=#-]?\s*(\d{3})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:isteği reddetti|istegi reddetti|request rejected|request failed|response)\s*\(\s*(\d{3})\s*\)""", RegexOption.IGNORE_CASE)
    )

    // ────────────────────────────────────────────────────────────────────────
    // Sınıflandırma
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ham hatayı sınıflandırır.
     *
     * @param platform  Hatayı üreten liste kaynağı (bilinmiyorsa [ListPlatform.GENERIC]).
     * @param error     Yakalanan istisna (cause zinciri de taranır).
     * @param httpCode  Biliniyorsa HTTP durum kodu.
     * @param rawMessage Ek bağlam: günlük mesajı, sunucu gövdesi vb.
     */
    fun classify(
        platform: ListPlatform = ListPlatform.GENERIC,
        error: Throwable? = null,
        httpCode: Int? = null,
        rawMessage: String? = null,
    ): PlatformErrorCode {
        val text = buildString {
            rawMessage?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < 6) {
                append(current.javaClass.name).append(' ')
                current.message?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                if (current is PlatformHttpException) append("http ").append(current.status).append(' ')
                current = current.cause
                depth++
            }
        }
        return classifyText(platform, text, httpCode)
    }

    /** Günlük etiketi (`"AniList"`) + serbest metinden sınıflandırma yapar. */
    fun classifyMessage(
        platformLabel: String?,
        message: String?,
        details: String? = null,
    ): PlatformErrorCode {
        val blob = listOfNotNull(message, details).joinToString(" ")
        val declared = ListPlatform.fromLabel(platformLabel)
        val platform = if (declared != ListPlatform.GENERIC) declared else ListPlatform.detect(blob)
        // Olayın kendi durum kodu, ayrıntı bloklarındaki geçmiş hata kodlarından önce gelir.
        val structuredHttp = extractHttpCode(message.orEmpty()) ?: extractHttpCode(details.orEmpty())
        return classifyText(platform, blob, structuredHttp)
    }

    /** Metin ve (varsa) HTTP kodu üzerinden kod seçer. */
    fun classifyText(
        platform: ListPlatform = ListPlatform.GENERIC,
        text: String?,
        httpCode: Int? = null,
    ): PlatformErrorCode {
        val raw = text.orEmpty()
        val t = raw.lowercase(Locale.ROOT)
        val resolvedPlatform = if (platform == ListPlatform.GENERIC) ListPlatform.detect(raw) else platform
        val http = httpCode ?: extractHttpCode(raw)

        // ── 1) Platforma özgü, belgelenmiş imzalar ──────────────────────────
        platformQuirk(resolvedPlatform, t, http)?.let { return it }

        // ── 2) Ortak HTTP durum kodları ─────────────────────────────────────
        if (http != null) {
            when (http) {
                400 -> return PlatformErrorCode.COM_1016
                401 -> return PlatformErrorCode.COM_1010
                403 -> return PlatformErrorCode.COM_1008
                404, 410 -> return PlatformErrorCode.COM_1009
                422 -> return PlatformErrorCode.COM_1017
                429 -> return PlatformErrorCode.COM_1006
                in 500..599 -> return PlatformErrorCode.COM_1007
            }
        }

        // ── 3) İstisna sınıfları ve mesaj kalıpları ─────────────────────────
        return keywordMatch(t) ?: PlatformErrorCode.COM_1020
    }

    private fun platformQuirk(
        platform: ListPlatform,
        t: String,
        http: Int?,
    ): PlatformErrorCode? = when (platform) {

        ListPlatform.BANGUMI -> when {
            t.contains("nsfw") || http == 400 -> PlatformErrorCode.BGM_6001
            t.contains("user-agent") || t.contains("user agent") -> PlatformErrorCode.BGM_6006
            http == 401 -> PlatformErrorCode.BGM_6002
            http == 404 -> PlatformErrorCode.BGM_6003
            http == 429 -> PlatformErrorCode.COM_1006
            else -> null
        }

        ListPlatform.SHIKIMORI -> when {
            t.contains("redirect uri") || t.contains("redirect_uri") || t.contains("callback") ->
                PlatformErrorCode.SHK_5003
            t.contains("messages") && (t.contains("scope") || t.contains("izin") || t.contains("permission")) ->
                PlatformErrorCode.SHK_5002
            http == 422 -> PlatformErrorCode.SHK_5004
            http == 401 -> PlatformErrorCode.SHK_5006
            t.contains("cloudflare") || t.contains("waf") || t.contains("captcha") ->
                PlatformErrorCode.SHK_5005
            http == 429 || t.contains("rate limit") || t.contains("hız sınırı") ->
                PlatformErrorCode.SHK_5001
            else -> null
        }

        ListPlatform.SIMKL -> when {
            t.contains("pin") && (t.contains("expired") || t.contains("doldu") || t.contains("süresi")) ->
                PlatformErrorCode.SMK_8001
            t.contains("polling") || http == 401 -> PlatformErrorCode.SMK_8002
            t.contains("client_id") || t.contains("client id") -> PlatformErrorCode.SMK_8003
            t.contains("rate_limit") || t.contains("rate limit") || t.contains("too many requests") ||
                http == 429 -> PlatformErrorCode.SMK_8004
            t.contains("calendar") || t.contains("takvim") -> PlatformErrorCode.SMK_8005
            else -> null
        }

        ListPlatform.ANILIST -> when {
            isAniListServiceDownText(t) -> PlatformErrorCode.ANL_2008
            t.contains("too many requests") || http == 429 ||
                t.contains("hız sınırı") || t.contains("rate limit") -> PlatformErrorCode.ANL_2002
            t.contains("score format") || t.contains("puan format") -> PlatformErrorCode.ANL_2006
            t.contains("private") || t.contains("gizli") -> PlatformErrorCode.ANL_2005
            (http != null && http in 500..599) || t.contains("internal server error") ||
                t.contains("bad gateway") || t.contains("service unavailable") || t.contains("gateway timeout") ->
                PlatformErrorCode.ANL_2003
            http == 401 -> PlatformErrorCode.ANL_2004
            else -> null
        }

        ListPlatform.MAL -> when {
            t.contains("jikan") && (http == 429 || t.contains("too many requests")) -> PlatformErrorCode.MAL_3001
            t.contains("jikan") && (t.contains("service unavailable") || t.contains("bakım") || t.contains("maintenance")) ->
                PlatformErrorCode.MAL_3006
            t.contains("private") || t.contains("gizli") -> PlatformErrorCode.MAL_3004
            http == 429 || t.contains("rate limit") -> PlatformErrorCode.MAL_3002
            http == 401 -> PlatformErrorCode.MAL_3007
            http == 404 -> PlatformErrorCode.MAL_3005
            else -> null
        }

        ListPlatform.KITSU -> when {
            // "Kitsu API adresi değişti" yalnızca GERÇEK bir host taşınması/yönlendirmesi
            // şüphesinde gösterilir. Ham kayıt her zaman host adını taşıdığı için
            // ("HTTP 404 · kitsu.app/anime") host adının geçmesi tek başına imza DEĞİLDİR;
            // eski kod bu yüzden her Kitsu hatasını (404, 429, 5xx) yanıltıcı biçimde
            // "adres değişti" olarak sınıflandırıyordu.
            isKitsuHostMigrationText(t) && (http == null || http == 301 || http == 308) ->
                PlatformErrorCode.KIT_4001
            t.contains("relationship") || t.contains("ilişki") ->
                PlatformErrorCode.KIT_4004
            http == 429 || t.contains("rate limit") ->
                PlatformErrorCode.KIT_4002
            // 404 / 5xx ve diğer durumlar ortak HTTP eşlemesine bırakılır
            // (COM_1009 "Kayıt bulunamadı", COM_1007 "Sunucu hatası"...).
            else -> null
        }

        ListPlatform.TMDB -> when {
            t.contains("tmdb-33:") -> PlatformErrorCode.TMD_7005
            t.contains("tmdb-41:") || t.contains("tmdb-36:") ||
                t.contains("tmdb-3:") || t.contains("tmdb-14:") || t.contains("tmdb-16:") ||
                t.contains("tmdb-17:") || t.contains("tmdb-30:") || t.contains("tmdb-31:") ||
                t.contains("tmdb-32:") || t.contains("tmdb-37:") || t.contains("tmdb-38:") ||
                t.contains("tmdb-39:") -> PlatformErrorCode.TMD_7006
            t.contains("tmdb-7:") || t.contains("tmdb-10:") || http == 401 || t.contains("401") ||
                t.contains("api_key") || t.contains("api key") -> PlatformErrorCode.TMD_7001
            t.contains("tmdb-25:") || http == 429 || t.contains("429") -> PlatformErrorCode.TMD_7004
            t.contains("tmdb-34:") || http == 404 || t.contains("404") -> PlatformErrorCode.TMD_7003
            t.contains("tmdb-8:") || t.contains("tmdb-18:") -> PlatformErrorCode.TMD_7007
            t.contains("tmdb-9:") || t.contains("tmdb-46:") -> PlatformErrorCode.TMD_7008
            t.contains("language") || t.contains("dil") -> PlatformErrorCode.TMD_7002
            else -> null
        }

        else -> null
    }

    /**
     * Kitsu host taşınması (kitsu.io ↔ kitsu.app) belirtisi arar.
     *
     * Yalnızca taşınma/yönlendirme ifadeleri ya da eski host'a ulaşılamaması
     * (DNS/bağlantı/TLS) imza sayılır. Sadece "kitsu.app" geçmesi yeterli değildir;
     * aksi hâlde her HTTP hata kaydı KIT_4001'e dönüşüyordu.
     */
    private fun isKitsuHostMigrationText(t: String): Boolean {
        val migrationMarkers = listOf(
            "host changed", "host değiş", "api adresi değiş", "adres değiş",
            "domain değiş", "moved permanently", "yönlendir",
        )
        if (migrationMarkers.any { t.contains(it) }) return true
        val hostUnreachable = t.contains("unknownhost") || t.contains("unable to resolve") ||
            t.contains("no address associated with hostname") || t.contains("sslhandshake") ||
            t.contains("certificate")
        return hostUnreachable && (t.contains("kitsu.io") || t.contains("kitsu.app"))
    }

    private fun isAniListServiceDownText(t: String): Boolean =
        t.contains("anilistservicedown") ||
            t.contains("temporarily disabled") ||
            t.contains("stability issues") ||
            t.contains("servis kesintisi") ||
            (t.contains("anilist") && t.contains("devre d"))

    private fun keywordMatch(t: String): PlatformErrorCode? = when {
        // ── AniList sunucu şalteri (platform GENERIC geçilse bile yakala) ───
        isAniListServiceDownText(t) -> PlatformErrorCode.ANL_2008

        // ── Ağ / taşıma ─────────────────────────────────────────────────────
        t.contains("unable to resolve host") || t.contains("unknownhost") ||
            t.contains("no address associated with hostname") -> PlatformErrorCode.COM_1002
        t.contains("timeout") || t.contains("timed out") || t.contains("sockettimeout") ->
            PlatformErrorCode.COM_1003
        t.contains("sslhandshake") || t.contains("certpathvalidator") ||
            t.contains("certificate") || t.contains("ssl") -> PlatformErrorCode.COM_1004
        t.contains("connection reset") || t.contains("broken pipe") ||
            t.contains("unexpected end of stream") || t.contains("eofexception") ->
            PlatformErrorCode.COM_1005
        t.contains("connectexception") || t.contains("econnrefused") ||
            t.contains("failed to connect") || t.contains("connection refused") ->
            PlatformErrorCode.COM_1001

        // ── Oturum / yetki ──────────────────────────────────────────────────
        t.contains("invalid_grant") || t.contains("refresh token") -> PlatformErrorCode.COM_1011
        t.contains("redirect_uri") || t.contains("redirect uri") -> PlatformErrorCode.COM_1015
        t.contains("invalid_client") || t.contains("client id") || t.contains("client_id") ||
            t.contains("api key") || t.contains("api_key") -> PlatformErrorCode.COM_1013
        t.contains("scope") || t.contains("insufficient permission") -> PlatformErrorCode.COM_1012
        t.contains("token") && (t.contains("expired") || t.contains("invalid") ||
            t.contains("doldu") || t.contains("geçersiz")) -> PlatformErrorCode.COM_1010
        t.contains("hesap bağlı değil") || t.contains("not connected") ||
            t.contains("login required") || t.contains("oturum yok") -> PlatformErrorCode.COM_1014

        // ── Ayrıştırma / sözleşme ───────────────────────────────────────────
        t.contains("jsonmapping") || t.contains("malformedjson") || t.contains("jsonparse") ||
            (t.contains("expected") && t.contains("json")) || t.contains("ayrıştırılamadı") ->
            PlatformErrorCode.COM_1017
        t.contains("unknown field") || t.contains("schema") || t.contains("şema") ||
            t.contains("not defined") -> PlatformErrorCode.COM_1016
        t.contains("empty response") || t.contains("boş yanıt") || t.contains("empty body") ->
            PlatformErrorCode.COM_1018

        // ── Akış kontrolü ───────────────────────────────────────────────────
        t.contains("cancellationexception") || t.contains("cancelled") || t.contains("iptal") ->
            PlatformErrorCode.COM_1019
        t.contains("cooldown") || t.contains("soğuma") -> PlatformErrorCode.COM_1024
        t.contains("proxy") && (t.contains("key") || t.contains("anahtar")) -> PlatformErrorCode.COM_1023
        t.contains("identity") || t.contains("kimlik uyuşmazlığı") -> PlatformErrorCode.COM_1022
        t.contains("no match") || t.contains("eşleşme bulunamadı") || t.contains("match not found") ->
            PlatformErrorCode.COM_1021
        t.contains("payload too large") || t.contains("content too large") || t.contains("çok büyük") ->
            PlatformErrorCode.COM_1025

        // ── Kalan 429/5xx izleri ────────────────────────────────────────────
        t.contains("too many requests") -> PlatformErrorCode.COM_1006
        t.contains("internal server error") || t.contains("bad gateway") ||
            t.contains("service unavailable") || t.contains("gateway timeout") -> PlatformErrorCode.COM_1007

        else -> null
    }

    /**
     * Yalnız açık HTTP/status bağlamındaki 4xx/5xx kodunu çıkarır.
     * `kitsu=42919`, `MAL ID=500012426` veya yıl/başlık sayıları durum kodu değildir.
     */
    private fun extractHttpCode(text: String): Int? {
        if (text.isBlank()) return null
        return httpCodePatterns.asSequence()
            .flatMap { pattern -> pattern.findAll(text) }
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .firstOrNull { it in 400..599 }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Kullanıcı metinleri
    // ────────────────────────────────────────────────────────────────────────

    /** Bir hata kodunun çözülmüş, gösterilebilir hali. */
    data class Explanation(
        val code: PlatformErrorCode,
        val codeText: String,
        val platformLabel: String,
        val severityLabel: String,
        val title: String,
        val why: String,
        val fix: String,
        val retryHint: String,
    )

    /** Kodu cihaz dilindeki metinlerle açar. Bilinmeyen kod → [PlatformErrorCode.COM_1020]. */
    fun explain(context: Context, code: PlatformErrorCode?): Explanation {
        val resolved = code ?: PlatformErrorCode.COM_1020
        return Explanation(
            code = resolved,
            codeText = resolved.code,
            platformLabel = context.getString(resolved.platform.labelRes),
            severityLabel = context.getString(resolved.severity.labelRes),
            title = context.getString(resolved.titleRes),
            why = context.getString(resolved.whyRes),
            fix = context.getString(resolved.fixRes),
            retryHint = context.getString(
                if (resolved.retryable) R.string.pf_err_retryable_yes else R.string.pf_err_retryable_no
            ),
        )
    }

    /** Kod metninden (`"KTS-MAL-3001"`) açıklama üretir. Bilinmeyen kod → [PlatformErrorCode.COM_1020]. */
    fun explainCode(context: Context, codeText: String?): Explanation =
        explain(context, PlatformErrorCode.fromCode(codeText))

    /** Profil hata kartları gibi dar alanlar için kısa, iki dilli hata özeti. */
    fun conciseBilingual(context: Context, code: PlatformErrorCode): String {
        if (code == PlatformErrorCode.ANL_2002) {
            val tr = localizedContext(context, "tr").getString(R.string.pf_err_anl_2002_profile_short)
            val en = localizedContext(context, "en").getString(R.string.pf_err_anl_2002_profile_short)
            return "TR — $tr\nEN — $en"
        }
        if (code == PlatformErrorCode.ANL_2008) {
            val tr = localizedContext(context, "tr").getString(R.string.pf_err_anl_2008_profile_short)
            val en = localizedContext(context, "en").getString(R.string.pf_err_anl_2008_profile_short)
            return "TR — $tr\nEN — $en"
        }
        val tr = localized(context, code, "tr")
        val en = localized(context, code, "en")
        return "TR — ${tr.first}: ${tr.third}\nEN — ${en.first}: ${en.third}"
    }

    /**
     * Rapor/paylaşım için **iki dilli** blok üretir: hem Türkçe hem İngilizce.
     * Kullanıcı raporu geliştiriciye gönderdiğinde hangi dilde olursa olsun anlaşılsın.
     */
    fun bilingual(context: Context, code: PlatformErrorCode): String {
        val tr = localized(context, code, "tr")
        val en = localized(context, code, "en")
        return buildString {
            appendLine("${code.code} · ${code.severity.name} · ${code.platform.codePrefix}")
            appendLine("TR — ${tr.first}")
            appendLine("     Neden: ${tr.second}")
            appendLine("     Çözüm: ${tr.third}")
            appendLine("EN — ${en.first}")
            appendLine("     Why: ${en.second}")
            appendLine("     Fix: ${en.third}")
        }
    }

    /** Raporun sonuna eklenen "bu koşuda görülen kodlar" bölümü. */
    fun reportSection(context: Context, codes: Collection<PlatformErrorCode>): String {
        val unique = codes.distinct().sortedBy { it.code }
        if (unique.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("=== ${context.getString(R.string.pf_err_report_section)} (${unique.size}) ===")
            unique.forEach { code -> append(bilingual(context, code)) }
        }
    }

    /** Eşitleme günlüğü/rapor satırlarına eklenecek kısa etiket. */
    fun shortLabel(context: Context, code: PlatformErrorCode): String =
        "[${code.code} · ${context.getString(code.platform.labelRes)}] ${context.getString(code.titleRes)}"

    private fun localized(
        context: Context,
        code: PlatformErrorCode,
        languageTag: String,
    ): Triple<String, String, String> {
        val localized = localizedContext(context, languageTag)
        return Triple(
            localized.getString(code.titleRes),
            localized.getString(code.whyRes),
            localized.getString(code.fixRes),
        )
    }

    private fun localizedContext(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

/** HTTP durum kodunu taşıyan, katalog tarafından tanınan işaretçi istisna. */
open class PlatformHttpException(
    val status: Int,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
