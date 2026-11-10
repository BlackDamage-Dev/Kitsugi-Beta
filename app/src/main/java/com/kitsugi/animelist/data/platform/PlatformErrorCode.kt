package com.kitsugi.animelist.data.platform

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

/**
 * Kitsugi — liste kaynağı (platform) hata kodları.
 *
 * Amaç: AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB ve Simkl
 * entegrasyonlarında oluşan **her** hatanın (bilinen ya da bilinmeyen) sabit bir
 * kodu, kısa bir başlığı ve "neden çıktı / ne yapmalı" açıklaması olsun; böylece
 * kullanıcı hangi kaynağın nerede, ne şekilde ve neden hata verdiğini görebilir.
 *
 * Kod biçimi: `KTS-<PLATFORM>-<NNNN>`
 *  - `KTS-COM-####` tüm platformlar için ortak (ağ, HTTP, oturum, ayrıştırma…)
 *  - `KTS-ANL-####` AniList      · `KTS-MAL-####` MyAnimeList/Jikan
 *  - `KTS-KIT-####` Kitsu        · `KTS-SHK-####` Shikimori
 *  - `KTS-BGM-####` Bangumi      · `KTS-TMD-####` TMDB
 *  - `KTS-SMK-####` Simkl
 *
 * Metinler dil dosyasındadır (`values/strings.xml` = TR, `values-en/strings.xml` = EN),
 * cihaz dili hangisiyse o okunur. Bu enum yalnızca koda/metne yapısal referans tutar.
 *
 * Sınıflandırma mantığı: [PlatformErrorCatalog]
 */
enum class PlatformErrorCode(
    /** Kalıcı, kullanıcıya gösterilebilir hata kodu, örn. `KTS-MAL-3001`. */
    val code: String,
    /** Hatayı üreten liste kaynağı. */
    val platform: ListPlatform,
    /** Kullanıcıya gösterilen önem derecesi. */
    val severity: PlatformErrorSeverity,
    @StringRes val titleRes: Int,
    @StringRes val whyRes: Int,
    @StringRes val fixRes: Int,
    /** Aynı istek mantıklı bir beklemeden sonra tekrar denenebilir mi? */
    val retryable: Boolean,
) {

    // ── Ortak kodlar — her platformda geçerli (ağ / HTTP / oturum / ayrıştırma) ──
    COM_1001(
        code = "KTS-COM-1001",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1001_title,
        whyRes = R.string.pf_err_com_1001_why,
        fixRes = R.string.pf_err_com_1001_fix,
        retryable = true
    ),
    COM_1002(
        code = "KTS-COM-1002",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1002_title,
        whyRes = R.string.pf_err_com_1002_why,
        fixRes = R.string.pf_err_com_1002_fix,
        retryable = true
    ),
    COM_1003(
        code = "KTS-COM-1003",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1003_title,
        whyRes = R.string.pf_err_com_1003_why,
        fixRes = R.string.pf_err_com_1003_fix,
        retryable = true
    ),
    COM_1004(
        code = "KTS-COM-1004",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1004_title,
        whyRes = R.string.pf_err_com_1004_why,
        fixRes = R.string.pf_err_com_1004_fix,
        retryable = false
    ),
    COM_1005(
        code = "KTS-COM-1005",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1005_title,
        whyRes = R.string.pf_err_com_1005_why,
        fixRes = R.string.pf_err_com_1005_fix,
        retryable = true
    ),
    COM_1006(
        code = "KTS-COM-1006",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1006_title,
        whyRes = R.string.pf_err_com_1006_why,
        fixRes = R.string.pf_err_com_1006_fix,
        retryable = true
    ),
    COM_1007(
        code = "KTS-COM-1007",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1007_title,
        whyRes = R.string.pf_err_com_1007_why,
        fixRes = R.string.pf_err_com_1007_fix,
        retryable = true
    ),
    COM_1008(
        code = "KTS-COM-1008",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1008_title,
        whyRes = R.string.pf_err_com_1008_why,
        fixRes = R.string.pf_err_com_1008_fix,
        retryable = false
    ),
    COM_1009(
        code = "KTS-COM-1009",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1009_title,
        whyRes = R.string.pf_err_com_1009_why,
        fixRes = R.string.pf_err_com_1009_fix,
        retryable = false
    ),
    COM_1010(
        code = "KTS-COM-1010",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1010_title,
        whyRes = R.string.pf_err_com_1010_why,
        fixRes = R.string.pf_err_com_1010_fix,
        retryable = false
    ),
    COM_1011(
        code = "KTS-COM-1011",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1011_title,
        whyRes = R.string.pf_err_com_1011_why,
        fixRes = R.string.pf_err_com_1011_fix,
        retryable = false
    ),
    COM_1012(
        code = "KTS-COM-1012",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1012_title,
        whyRes = R.string.pf_err_com_1012_why,
        fixRes = R.string.pf_err_com_1012_fix,
        retryable = false
    ),
    COM_1013(
        code = "KTS-COM-1013",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1013_title,
        whyRes = R.string.pf_err_com_1013_why,
        fixRes = R.string.pf_err_com_1013_fix,
        retryable = false
    ),
    COM_1014(
        code = "KTS-COM-1014",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1014_title,
        whyRes = R.string.pf_err_com_1014_why,
        fixRes = R.string.pf_err_com_1014_fix,
        retryable = false
    ),
    COM_1015(
        code = "KTS-COM-1015",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1015_title,
        whyRes = R.string.pf_err_com_1015_why,
        fixRes = R.string.pf_err_com_1015_fix,
        retryable = false
    ),
    COM_1016(
        code = "KTS-COM-1016",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1016_title,
        whyRes = R.string.pf_err_com_1016_why,
        fixRes = R.string.pf_err_com_1016_fix,
        retryable = false
    ),
    COM_1017(
        code = "KTS-COM-1017",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1017_title,
        whyRes = R.string.pf_err_com_1017_why,
        fixRes = R.string.pf_err_com_1017_fix,
        retryable = true
    ),
    COM_1018(
        code = "KTS-COM-1018",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1018_title,
        whyRes = R.string.pf_err_com_1018_why,
        fixRes = R.string.pf_err_com_1018_fix,
        retryable = true
    ),
    COM_1019(
        code = "KTS-COM-1019",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.INFO,
        titleRes = R.string.pf_err_com_1019_title,
        whyRes = R.string.pf_err_com_1019_why,
        fixRes = R.string.pf_err_com_1019_fix,
        retryable = true
    ),
    COM_1020(
        code = "KTS-COM-1020",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1020_title,
        whyRes = R.string.pf_err_com_1020_why,
        fixRes = R.string.pf_err_com_1020_fix,
        retryable = true
    ),
    COM_1021(
        code = "KTS-COM-1021",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1021_title,
        whyRes = R.string.pf_err_com_1021_why,
        fixRes = R.string.pf_err_com_1021_fix,
        retryable = false
    ),
    COM_1022(
        code = "KTS-COM-1022",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1022_title,
        whyRes = R.string.pf_err_com_1022_why,
        fixRes = R.string.pf_err_com_1022_fix,
        retryable = false
    ),
    COM_1023(
        code = "KTS-COM-1023",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_com_1023_title,
        whyRes = R.string.pf_err_com_1023_why,
        fixRes = R.string.pf_err_com_1023_fix,
        retryable = false
    ),
    COM_1024(
        code = "KTS-COM-1024",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1024_title,
        whyRes = R.string.pf_err_com_1024_why,
        fixRes = R.string.pf_err_com_1024_fix,
        retryable = true
    ),
    COM_1025(
        code = "KTS-COM-1025",
        platform = ListPlatform.GENERIC,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_com_1025_title,
        whyRes = R.string.pf_err_com_1025_why,
        fixRes = R.string.pf_err_com_1025_fix,
        retryable = true
    ),

    // ── AniList ──
    ANL_2001(
        code = "KTS-ANL-2001",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_anl_2001_title,
        whyRes = R.string.pf_err_anl_2001_why,
        fixRes = R.string.pf_err_anl_2001_fix,
        retryable = true
    ),
    ANL_2002(
        code = "KTS-ANL-2002",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_anl_2002_title,
        whyRes = R.string.pf_err_anl_2002_why,
        fixRes = R.string.pf_err_anl_2002_fix,
        retryable = true
    ),
    ANL_2003(
        code = "KTS-ANL-2003",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_anl_2003_title,
        whyRes = R.string.pf_err_anl_2003_why,
        fixRes = R.string.pf_err_anl_2003_fix,
        retryable = true
    ),
    ANL_2004(
        code = "KTS-ANL-2004",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_anl_2004_title,
        whyRes = R.string.pf_err_anl_2004_why,
        fixRes = R.string.pf_err_anl_2004_fix,
        retryable = false
    ),
    ANL_2005(
        code = "KTS-ANL-2005",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_anl_2005_title,
        whyRes = R.string.pf_err_anl_2005_why,
        fixRes = R.string.pf_err_anl_2005_fix,
        retryable = false
    ),
    ANL_2006(
        code = "KTS-ANL-2006",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_anl_2006_title,
        whyRes = R.string.pf_err_anl_2006_why,
        fixRes = R.string.pf_err_anl_2006_fix,
        retryable = false
    ),
    ANL_2007(
        code = "KTS-ANL-2007",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_anl_2007_title,
        whyRes = R.string.pf_err_anl_2007_why,
        fixRes = R.string.pf_err_anl_2007_fix,
        retryable = false
    ),
    ANL_2008(
        code = "KTS-ANL-2008",
        platform = ListPlatform.ANILIST,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_anl_2008_title,
        whyRes = R.string.pf_err_anl_2008_why,
        fixRes = R.string.pf_err_anl_2008_fix,
        retryable = true
    ),

    // ── MyAnimeList + Jikan ──
    MAL_3001(
        code = "KTS-MAL-3001",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_mal_3001_title,
        whyRes = R.string.pf_err_mal_3001_why,
        fixRes = R.string.pf_err_mal_3001_fix,
        retryable = true
    ),
    MAL_3002(
        code = "KTS-MAL-3002",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_mal_3002_title,
        whyRes = R.string.pf_err_mal_3002_why,
        fixRes = R.string.pf_err_mal_3002_fix,
        retryable = true
    ),
    MAL_3003(
        code = "KTS-MAL-3003",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.INFO,
        titleRes = R.string.pf_err_mal_3003_title,
        whyRes = R.string.pf_err_mal_3003_why,
        fixRes = R.string.pf_err_mal_3003_fix,
        retryable = false
    ),
    MAL_3004(
        code = "KTS-MAL-3004",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_mal_3004_title,
        whyRes = R.string.pf_err_mal_3004_why,
        fixRes = R.string.pf_err_mal_3004_fix,
        retryable = false
    ),
    MAL_3005(
        code = "KTS-MAL-3005",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_mal_3005_title,
        whyRes = R.string.pf_err_mal_3005_why,
        fixRes = R.string.pf_err_mal_3005_fix,
        retryable = false
    ),
    MAL_3006(
        code = "KTS-MAL-3006",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_mal_3006_title,
        whyRes = R.string.pf_err_mal_3006_why,
        fixRes = R.string.pf_err_mal_3006_fix,
        retryable = true
    ),
    MAL_3007(
        code = "KTS-MAL-3007",
        platform = ListPlatform.MAL,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_mal_3007_title,
        whyRes = R.string.pf_err_mal_3007_why,
        fixRes = R.string.pf_err_mal_3007_fix,
        retryable = false
    ),

    // ── Kitsu ──
    KIT_4001(
        code = "KTS-KIT-4001",
        platform = ListPlatform.KITSU,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_kit_4001_title,
        whyRes = R.string.pf_err_kit_4001_why,
        fixRes = R.string.pf_err_kit_4001_fix,
        retryable = true
    ),
    KIT_4002(
        code = "KTS-KIT-4002",
        platform = ListPlatform.KITSU,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_kit_4002_title,
        whyRes = R.string.pf_err_kit_4002_why,
        fixRes = R.string.pf_err_kit_4002_fix,
        retryable = true
    ),
    KIT_4003(
        code = "KTS-KIT-4003",
        platform = ListPlatform.KITSU,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_kit_4003_title,
        whyRes = R.string.pf_err_kit_4003_why,
        fixRes = R.string.pf_err_kit_4003_fix,
        retryable = false
    ),
    KIT_4004(
        code = "KTS-KIT-4004",
        platform = ListPlatform.KITSU,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_kit_4004_title,
        whyRes = R.string.pf_err_kit_4004_why,
        fixRes = R.string.pf_err_kit_4004_fix,
        retryable = true
    ),

    // ── Shikimori ──
    SHK_5001(
        code = "KTS-SHK-5001",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_shk_5001_title,
        whyRes = R.string.pf_err_shk_5001_why,
        fixRes = R.string.pf_err_shk_5001_fix,
        retryable = true
    ),
    SHK_5002(
        code = "KTS-SHK-5002",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_shk_5002_title,
        whyRes = R.string.pf_err_shk_5002_why,
        fixRes = R.string.pf_err_shk_5002_fix,
        retryable = false
    ),
    SHK_5003(
        code = "KTS-SHK-5003",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_shk_5003_title,
        whyRes = R.string.pf_err_shk_5003_why,
        fixRes = R.string.pf_err_shk_5003_fix,
        retryable = false
    ),
    SHK_5004(
        code = "KTS-SHK-5004",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_shk_5004_title,
        whyRes = R.string.pf_err_shk_5004_why,
        fixRes = R.string.pf_err_shk_5004_fix,
        retryable = false
    ),
    SHK_5005(
        code = "KTS-SHK-5005",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_shk_5005_title,
        whyRes = R.string.pf_err_shk_5005_why,
        fixRes = R.string.pf_err_shk_5005_fix,
        retryable = false
    ),
    SHK_5006(
        code = "KTS-SHK-5006",
        platform = ListPlatform.SHIKIMORI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_shk_5006_title,
        whyRes = R.string.pf_err_shk_5006_why,
        fixRes = R.string.pf_err_shk_5006_fix,
        retryable = false
    ),

    // ── Bangumi ──
    BGM_6001(
        code = "KTS-BGM-6001",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_bgm_6001_title,
        whyRes = R.string.pf_err_bgm_6001_why,
        fixRes = R.string.pf_err_bgm_6001_fix,
        retryable = false
    ),
    BGM_6002(
        code = "KTS-BGM-6002",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_bgm_6002_title,
        whyRes = R.string.pf_err_bgm_6002_why,
        fixRes = R.string.pf_err_bgm_6002_fix,
        retryable = false
    ),
    BGM_6003(
        code = "KTS-BGM-6003",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_bgm_6003_title,
        whyRes = R.string.pf_err_bgm_6003_why,
        fixRes = R.string.pf_err_bgm_6003_fix,
        retryable = false
    ),
    BGM_6004(
        code = "KTS-BGM-6004",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_bgm_6004_title,
        whyRes = R.string.pf_err_bgm_6004_why,
        fixRes = R.string.pf_err_bgm_6004_fix,
        retryable = false
    ),
    BGM_6005(
        code = "KTS-BGM-6005",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_bgm_6005_title,
        whyRes = R.string.pf_err_bgm_6005_why,
        fixRes = R.string.pf_err_bgm_6005_fix,
        retryable = false
    ),
    BGM_6006(
        code = "KTS-BGM-6006",
        platform = ListPlatform.BANGUMI,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_bgm_6006_title,
        whyRes = R.string.pf_err_bgm_6006_why,
        fixRes = R.string.pf_err_bgm_6006_fix,
        retryable = false
    ),

    // ── TMDB ──
    TMD_7001(
        code = "KTS-TMD-7001",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_tmd_7001_title,
        whyRes = R.string.pf_err_tmd_7001_why,
        fixRes = R.string.pf_err_tmd_7001_fix,
        retryable = false
    ),
    TMD_7002(
        code = "KTS-TMD-7002",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.INFO,
        titleRes = R.string.pf_err_tmd_7002_title,
        whyRes = R.string.pf_err_tmd_7002_why,
        fixRes = R.string.pf_err_tmd_7002_fix,
        retryable = false
    ),
    TMD_7003(
        code = "KTS-TMD-7003",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_tmd_7003_title,
        whyRes = R.string.pf_err_tmd_7003_why,
        fixRes = R.string.pf_err_tmd_7003_fix,
        retryable = false
    ),
    TMD_7004(
        code = "KTS-TMD-7004",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_tmd_7004_title,
        whyRes = R.string.pf_err_tmd_7004_why,
        fixRes = R.string.pf_err_tmd_7004_fix,
        retryable = true
    ),
    TMD_7005(
        code = "KTS-TMD-7005",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_tmd_7005_title,
        whyRes = R.string.pf_err_tmd_7005_why,
        fixRes = R.string.pf_err_tmd_7005_fix,
        retryable = false
    ),
    TMD_7006(
        code = "KTS-TMD-7006",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_tmd_7006_title,
        whyRes = R.string.pf_err_tmd_7006_why,
        fixRes = R.string.pf_err_tmd_7006_fix,
        retryable = false
    ),
    TMD_7007(
        code = "KTS-TMD-7007",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_tmd_7007_title,
        whyRes = R.string.pf_err_tmd_7007_why,
        fixRes = R.string.pf_err_tmd_7007_fix,
        retryable = false
    ),
    TMD_7008(
        code = "KTS-TMD-7008",
        platform = ListPlatform.TMDB,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_tmd_7008_title,
        whyRes = R.string.pf_err_tmd_7008_why,
        fixRes = R.string.pf_err_tmd_7008_fix,
        retryable = true
    ),

    // ── Simkl ──
    SMK_8001(
        code = "KTS-SMK-8001",
        platform = ListPlatform.SIMKL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_smk_8001_title,
        whyRes = R.string.pf_err_smk_8001_why,
        fixRes = R.string.pf_err_smk_8001_fix,
        retryable = false
    ),
    SMK_8002(
        code = "KTS-SMK-8002",
        platform = ListPlatform.SIMKL,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_smk_8002_title,
        whyRes = R.string.pf_err_smk_8002_why,
        fixRes = R.string.pf_err_smk_8002_fix,
        retryable = false
    ),
    SMK_8003(
        code = "KTS-SMK-8003",
        platform = ListPlatform.SIMKL,
        severity = PlatformErrorSeverity.ERROR,
        titleRes = R.string.pf_err_smk_8003_title,
        whyRes = R.string.pf_err_smk_8003_why,
        fixRes = R.string.pf_err_smk_8003_fix,
        retryable = false
    ),
    SMK_8004(
        code = "KTS-SMK-8004",
        platform = ListPlatform.SIMKL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_smk_8004_title,
        whyRes = R.string.pf_err_smk_8004_why,
        fixRes = R.string.pf_err_smk_8004_fix,
        retryable = true
    ),
    SMK_8005(
        code = "KTS-SMK-8005",
        platform = ListPlatform.SIMKL,
        severity = PlatformErrorSeverity.WARNING,
        titleRes = R.string.pf_err_smk_8005_title,
        whyRes = R.string.pf_err_smk_8005_why,
        fixRes = R.string.pf_err_smk_8005_fix,
        retryable = true
    ),

    ;

    /** Kodun platforma özel mi yoksa ortak mı olduğunu söyler. */
    val isPlatformSpecific: Boolean get() = platform != ListPlatform.GENERIC

    companion object {
        // lazy: enum sabitleri ile eşzamanlı başlatma sırası sorunlarını elemek için.
        private val byCode: Map<String, PlatformErrorCode> by lazy {
            entries.associateBy { it.code.uppercase() }
        }

        private val byPlatform: Map<ListPlatform, List<PlatformErrorCode>> by lazy {
            entries.groupBy { it.platform }
        }

        /** `KTS-MAL-3001` gibi bir kodu enum değerine çevirir. Bilinmiyorsa null. */
        fun fromCode(code: String?): PlatformErrorCode? {
            val key = code?.trim()?.uppercase() ?: return null
            byCode[key]?.let { return it }
            // Kodsuz ama numarası bilinen girdiler için toleranslı arama
            val digits = key.takeLastWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            return entries.firstOrNull { it.code.endsWith(digits) && key.startsWith(it.code.substringBeforeLast('-')) }
        }

        /** Bir platforma ait tüm kodlar (ortak kodlar için [ListPlatform.GENERIC]). */
        fun forPlatform(platform: ListPlatform): List<PlatformErrorCode> =
            byPlatform[platform].orEmpty()

        /** Platformun kendi kodları + tüm ortak kodlar. */
        fun applicableTo(platform: ListPlatform): List<PlatformErrorCode> =
            forPlatform(ListPlatform.GENERIC) + forPlatform(platform)
    }
}

/** Hata kodunun kullanıcıya gösterilen önem derecesi. */
enum class PlatformErrorSeverity(@StringRes val labelRes: Int) {
    INFO(R.string.pf_err_severity_info),
    WARNING(R.string.pf_err_severity_warning),
    ERROR(R.string.pf_err_severity_error),
    CRITICAL(R.string.pf_err_severity_critical)
}
