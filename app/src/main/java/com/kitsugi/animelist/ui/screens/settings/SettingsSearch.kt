package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiSettingsItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import java.text.Normalizer
import java.util.Locale

internal enum class SettingsMainAction {
    WatchHistory,
    Downloads,
    About
}

private val settingsSearchCombiningMarks = Regex("\\p{Mn}+")
private val settingsSearchSeparators = Regex("[^\\p{L}\\p{N}]+")

/**
 * A searchable destination in Settings. Entries can point to a page or to a specific
 * tab/sub-page, so a search result lands near the setting instead of just opening the
 * top-level settings list.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val location: String,
    val keywords: String,
    val route: SettingsRoute? = null,
    val pageIndex: Int = 0,
    val playerSubScreen: com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen? = null,
    val action: SettingsMainAction? = null,
    val icon: ImageVector,
    val iconColor: Color
) {
    val key: String
        get() = listOf(route?.name, pageIndex.toString(), playerSubScreen?.name, action?.name, title)
            .joinToString("_")

    val searchableText: String
        get() = normalizeSettingsSearchText("$title $location $keywords")
}

/**
 * Normalize Turkish and English terms consistently: accentless queries such as "karanlik"
 * still match "Karanlık", and both dotted/dotless i are treated as the same letter.
 */
internal fun normalizeSettingsSearchText(value: String): String {
    val lower = value.lowercase(Locale.ROOT).replace('ı', 'i')
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace(settingsSearchCombiningMarks, "")
        .replace(settingsSearchSeparators, " ")
        .trim()
}

internal fun findSettingsMatches(
    entries: List<SettingsSearchEntry>,
    query: String
): List<SettingsSearchEntry> {
    val normalizedQuery = normalizeSettingsSearchText(query)
    if (normalizedQuery.isBlank()) return emptyList()

    val terms = normalizedQuery.split(' ').filter(String::isNotBlank)
    return entries
        .filter { entry ->
            val searchableText = entry.searchableText
            terms.all { term -> term in searchableText }
        }
        .sortedBy { entry ->
            val title = normalizeSettingsSearchText(entry.title)
            when {
                title == normalizedQuery -> 0
                title.startsWith(normalizedQuery) -> 1
                title.contains(normalizedQuery) -> 2
                else -> 3
            }
        }
}

@Composable
internal fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.settings_search_hint),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.ManageSearch,
                contentDescription = null,
                tint = accentColor
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                        tint = KitsugiColors.TextMuted
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = KitsugiColors.Surface,
            unfocusedContainerColor = KitsugiColors.Surface,
            focusedBorderColor = accentColor.copy(alpha = 0.75f),
            unfocusedBorderColor = KitsugiColors.Border,
            focusedTextColor = KitsugiColors.TextPrimary,
            unfocusedTextColor = KitsugiColors.TextPrimary,
            cursorColor = accentColor
        )
    )
}

@Composable
internal fun SettingsSearchResultItem(
    entry: SettingsSearchEntry,
    onClick: () -> Unit
) {
    KitsugiSettingsItem(
        title = entry.title,
        description = entry.location,
        icon = entry.icon,
        iconColor = entry.iconColor,
        onClick = onClick
    )
}

/** A curated, localized index of settings pages and individual options. */
@Composable
internal fun settingsSearchEntries(): List<SettingsSearchEntry> {
    val accountSection = stringResource(R.string.settings_section_account)
    val appSection = stringResource(R.string.settings_section_app)
    val sourceSection = stringResource(R.string.settings_section_sources)
    val dataSection = stringResource(R.string.settings_section_data)
    val supportSection = stringResource(R.string.settings_section_support)
    val diagnosticSection = stringResource(R.string.settings_section_diagnostics)
    val appearanceTitle = stringResource(R.string.settings_pref_title)
    val playerTitle = stringResource(R.string.settings_player_title)
    val addonsTitle = stringResource(R.string.settings_addons_sources)
    val integrationsTitle = stringResource(R.string.settings_integrations)
    val backupTitle = stringResource(R.string.settings_data_backup)
    val downloadsTitle = stringResource(R.string.settings_download_settings)
    val storageTitle = stringResource(R.string.storage_title)

    fun path(vararg parts: String): String = parts.filter(String::isNotBlank).joinToString("  ›  ")

    fun entry(
        title: String,
        location: String,
        keywords: String,
        icon: ImageVector,
        iconColor: Color,
        route: SettingsRoute? = null,
        pageIndex: Int = 0,
        playerSubScreen: com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen? = null,
        action: SettingsMainAction? = null
    ) = SettingsSearchEntry(
        title = title,
        location = location,
        keywords = keywords,
        route = route,
        pageIndex = pageIndex,
        playerSubScreen = playerSubScreen,
        action = action,
        icon = icon,
        iconColor = iconColor
    )

    val appearanceBase = path(appSection, appearanceTitle)
    val listBase = path(appSection, appearanceTitle, stringResource(R.string.settings_tab_list_score))
    val mangaBase = path(appSection, appearanceTitle, stringResource(R.string.settings_tab_manga_reader))
    val playerBase = path(appSection, playerTitle)
    val addonBase = path(sourceSection, addonsTitle)
    val integrationBase = path(sourceSection, integrationsTitle)
    val backupBase = path(dataSection, backupTitle)
    val downloadBase = path(dataSection, downloadsTitle)
    val storageBase = path(dataSection, storageTitle)
    val accountBase = path(accountSection, stringResource(R.string.settings_account_connections))

    return listOf(
        // Accounts and synchronization
        entry(stringResource(R.string.settings_kitsugi_account), path(accountSection, stringResource(R.string.settings_kitsugi_account)), "kitsugi hesap account login giriş profil profile cihazlar devices arama geçmişi search history sync eşitle", Icons.AccountCircle, KitsugiColors.AccentGreen, route = SettingsRoute.KitsugiAccount),
        entry(stringResource(R.string.settings_account_connections), accountBase, "hesap bağla bağlantı platform account connect anilist myanimelist mal simkl kitsu shikimori bangumi oauth giriş", Icons.Person, KitsugiColors.AccentBlue, route = SettingsRoute.AccountConnections),
        entry(stringResource(R.string.settings_cross_sync), path(accountBase, stringResource(R.string.settings_cross_sync)), "çapraz eşitleme senkronizasyon cross sync platformlar liste eşitleme", Icons.Sync, KitsugiColors.AccentOrange, route = SettingsRoute.CrossSyncSettings),
        entry("AniList", path(accountBase, "AniList"), "anilist anime manga hesap giriş login bağlantı import içe aktar otomatik eşitleme", Icons.Sync, KitsugiColors.AccentBlue, route = SettingsRoute.AniListSettings),
        entry("MyAnimeList", path(accountBase, "MyAnimeList"), "mal my anime list anime manga hesap giriş login bağlantı import içe aktar otomatik eşitleme", Icons.Sync, KitsugiColors.AccentOrange, route = SettingsRoute.MalSettings),
        entry("Simkl", path(accountBase, "Simkl"), "simkl film dizi anime hesap giriş login bağlantı import içe aktar eşitleme", Icons.Sync, KitsugiColors.AccentGreen, route = SettingsRoute.SimklSettings),
        entry("Kitsu", path(accountBase, "Kitsu"), "kitsu anime manga hesap giriş login bağlantı import içe aktar eşitleme", Icons.Sync, KitsugiColors.AccentPurple, route = SettingsRoute.KitsuSettings),
        entry("Shikimori", path(accountBase, "Shikimori"), "shikimori anime manga hesap giriş login bağlantı import içe aktar eşitleme", Icons.Sync, KitsugiColors.AccentPink, route = SettingsRoute.ShikimoriSettings),
        entry("Bangumi", path(accountBase, "Bangumi"), "bgm.tv anime manga hesap giriş login bağlantı import içe aktar eşitleme", Icons.Sync, KitsugiColors.AccentTeal, route = SettingsRoute.BangumiSettings),
        entry("TMDB Watchlist", path(accountBase, "TMDB Watchlist"), "tmdb hesap giriş login watchlist izleme listesi film dizi import içe aktar", Icons.Sync, KitsugiColors.AccentBlue, route = SettingsRoute.TmdbSettings),

        // Appearance, navigation and list preferences
        entry(appearanceTitle, appearanceBase, "görünüm tercihler tema renk arayüz kişiselleştirme settings appearance preferences", Icons.Palette, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences),
        entry(stringResource(R.string.settings_tab_appearance), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "arayüz tema görünüm dark light renk amoled kart çerçeve dil navigasyon açılış splash", Icons.Palette, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_dark_theme), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "karanlık koyu tema dark mode night gece light gündüz sistem system", Icons.DarkMode, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_theme_mode), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "tema modu renk modu sistem açık koyu light dark theme mode", Icons.Palette, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_amoled_black), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "amoled saf siyah black oled pil tasarrufu battery", Icons.Contrast, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.kart_cerceveleri), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "kart çerçeve çerçeveleri border frames kenarlık", Icons.Palette, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_theme_color), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "tema rengi vurgu accent renk palet özel hex gradient", Icons.ColorLens, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_search_default_tab), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "varsayılan başlangıç açılış sekmesi explore keşfet listem mylist search arama default startup tab", Icons.Home, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_search_home_layout), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "ana sayfa düzen yerleşim layout classic modern grid ızgara", Icons.ViewQuilt, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_app_language), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "uygulama dili türkçe ingilizce arayüz language locale system", Icons.Translate, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_adult_content), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "+18 yetişkin içerik anime manga adult mature nsfw göster gizle", Icons.Visibility, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_blur_adult), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "yetişkin bulanık blur poster görsel +18", Icons.BlurOn, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_anime_logos), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "anime logosu başlık görsel logo detail", Icons.Image, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_fixed_nav_bar), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "alt bar navigasyon sabit bottom bar navigation", Icons.ViewWeek, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_splash_animation_and_sound), path(appearanceBase, stringResource(R.string.settings_tab_appearance)), "açılış animasyonu ses splash başlangıç torii", Icons.AutoAwesome, LocalKitsugiAccent.current, route = SettingsRoute.AppearancePreferences, pageIndex = 0),
        entry(stringResource(R.string.settings_tab_list_score), listBase, "liste puan değerlendirme başlık dil çeviri bildirim arama geçmişi list score", Icons.ListAlt, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_list_layout), listBase, "liste görünüm layout kart grid listem", Icons.ViewList, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_title_language), listBase, "başlık dili anime manga romaji japonca ingilizce title language", Icons.Translate, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_score_format), listBase, "puan format rating score 100 10 yıldız stars değerlendirme", Icons.Star, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_hide_scores), listBase, "puan gizle göster hide scores list", Icons.VisibilityOff, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_search_score_step), listBase, "puan adımı yarım tam sayı step increment 0.5", Icons.Tune, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.ozel_listeler), listBase, "özel listeler custom lists anilist kategori liste oluştur", Icons.PlaylistAdd, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.varsayilan_ekleme_durumu), listBase, "varsayılan ekleme durum izliyorum izleyeceğim izledim hızlı ekle artı rozet default status add", Icons.PlaylistAddCheck, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.otomatik_tarih_doldur), listBase, "otomatik tarih başlangıç bitiş bugün doldur auto date start finish today", Icons.Event, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.otomatik_ilerleme_tamamla), listBase, "otomatik ilerleme bölüm tamamlandı progress complete episode", Icons.DoneAll, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_search_fuzzy), listBase, "bulanık akıllı arama fuzzy search relevance alaka", Icons.ManageSearch, KitsugiColors.AccentBlue, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.fandom_settings_title), listBase, "fandom wiki karakter bilgi kutusu spoiler türkçe wiki isimle eşleştirme önbellek character infobox spoiler cache", Icons.AutoStories, KitsugiColors.AccentPurple, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_auto_translate), listBase, "otomatik çeviri açıklama google deepl translator translate source target dil", Icons.Translate, KitsugiColors.AccentPurple, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_airing_notifications), listBase, "yayın takvim bölüm bildirimi notification anime episode", Icons.Notifications, KitsugiColors.AccentTeal, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_save_search_history), listBase, "arama geçmişi sakla kaydet search history suggestions", Icons.ManageSearch, KitsugiColors.AccentTeal, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_notif_interval), listBase, "bildirim sıklık aralık dakika notification interval frequency", Icons.Schedule, KitsugiColors.AccentTeal, route = SettingsRoute.AppearancePreferences, pageIndex = 1),
        entry(stringResource(R.string.settings_tab_manga_reader), mangaBase, "manga okuyucu reader okuma yön sayfa fit ekran filtre parlaklık", Icons.MenuBook, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 2),
        entry(stringResource(R.string.manga_reading_direction), mangaBase, "manga okuma yön rtl ltr sağdan sola soldan sağa dikey webtoon", Icons.SwapHoriz, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 2),
        entry(stringResource(R.string.manga_page_fit), mangaBase, "manga sayfa sığdır ekran genişlik yükseklik fit screen width height", Icons.FitScreen, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 2),
        entry(stringResource(R.string.manga_color_filter), mangaBase, "manga renk filtresi gri grayscale inverted warm cool", Icons.ColorLens, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 2),
        entry(stringResource(R.string.manga_brightness), mangaBase, "manga parlaklık brightness ekran", Icons.Brightness6, KitsugiColors.AccentOrange, route = SettingsRoute.AppearancePreferences, pageIndex = 2),

        // Player sections
        entry(playerTitle, playerBase, "oynatıcı video player altyazı ses hareket kod çözücü decoder buffer", Icons.PlayCircle, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings),
        entry(stringResource(R.string.pd_tab_internal), path(playerBase, stringResource(R.string.pd_tab_internal)), "dahili varsayılan oynatıcı otomatik oynatma intro atlama bölüm geçiş autoplay", Icons.PlayCircle, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.DahiliOynatici),
        entry(stringResource(R.string.pd_sub_gestures), path(playerBase, stringResource(R.string.pd_sub_gestures)), "hareketler jest swipe kaydırma çift dokunma seek zoom parlaklık ses gesture", Icons.Swipe, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.Hareketler),
        entry(stringResource(R.string.pd_tab_decoder), path(playerBase, stringResource(R.string.pd_tab_decoder)), "kod çözücü codec mpv gpu hwdec dolby vision hevc hdr decoder", Icons.Memory, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.KodCozucu),
        entry(stringResource(R.string.pd_tab_subtitles), path(playerBase, stringResource(R.string.pd_tab_subtitles)), "altyazılar altyazı subtitle srt dil boyut renk font kalın outline", Icons.Subtitles, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.Altyazilar),
        entry(stringResource(R.string.pd_sub_audio), path(playerBase, stringResource(R.string.pd_sub_audio)), "ses audio volume boost gecikme delay hoparlör bluetooth hdmi", Icons.VolumeUp, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.Ses),
        entry(stringResource(R.string.pd_tab_custom_buttons), path(playerBase, stringResource(R.string.pd_tab_custom_buttons)), "özel buton düğme player kontrol panel lua mpv custom buttons", Icons.SmartButton, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.OzelButonlar),
        entry(stringResource(R.string.pd_tab_keyboard), path(playerBase, stringResource(R.string.pd_tab_keyboard)), "klavye keyboard kısayol shortcut gamepad kol tuş osd gösterge arama adımı seek step", Icons.Keyboard, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.Klavye),
        entry(stringResource(R.string.pd_tab_code_editor), path(playerBase, stringResource(R.string.pd_tab_code_editor)), "kod düzenleyici lua conf input script editor", Icons.Code, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.KodDuzenleyici),
        entry(stringResource(R.string.pd_tab_advanced), path(playerBase, stringResource(R.string.pd_tab_advanced)), "gelişmiş buffer arabellek paralel mpv exoplayer frame rate çözünürlük", Icons.Tune, KitsugiColors.AccentOrange, route = SettingsRoute.PlayerSettings, playerSubScreen = com.kitsugi.animelist.ui.components.PlayerSettingsSubScreen.Gelismis),

        // Watch history and content sources
        entry(stringResource(R.string.settings_watch_history), path(dataSection, stringResource(R.string.settings_watch_history)), "izleme geçmişi history izlenen bölüm video ilerleme watch progress", Icons.History, KitsugiColors.AccentTeal, action = SettingsMainAction.WatchHistory),
        entry(addonsTitle, addonBase, "eklenti eklentiler kaynak sağlayıcı video provider torrent debrid manga plugin", Icons.Extension, KitsugiColors.AccentPurple, route = SettingsRoute.AddonsExtensions),
        entry("Stremio", path(addonBase, "Stremio"), "stremio eklenti addon debrid torrent stream kaynak", Icons.Hub, KitsugiColors.AccentPurple, route = SettingsRoute.AddonsExtensions, pageIndex = 0),
        entry("CloudStream", path(addonBase, "CloudStream"), "cloudstream eklenti eklentiler repo repository sağlayıcı video plugin", Icons.Extension, KitsugiColors.AccentPurple, route = SettingsRoute.AddonsExtensions, pageIndex = 1),
        entry(stringResource(R.string.inline_manga_sources_03288ff), path(addonBase, stringResource(R.string.inline_manga_sources_03288ff)), "manga kaynak reader eklenti extension plugin repository", Icons.MenuBook, KitsugiColors.AccentOrange, route = SettingsRoute.AddonsExtensions, pageIndex = 2),
        entry(stringResource(R.string.settings_plugin_diagnostic), path(diagnosticSection, stringResource(R.string.settings_plugin_diagnostic)), "eklenti tanılama teşhis diagnostics hata log plugin sorun", Icons.BugReport, KitsugiColors.AccentOrange, route = SettingsRoute.PluginDiagnostic),
        entry(stringResource(R.string.platform_error_catalog_open), path(diagnosticSection, stringResource(R.string.platform_error_catalog_open)), "platform hata kodları error codes cloudstream manga ağ bağlantı çözüm", Icons.Info, KitsugiColors.AccentOrange, route = SettingsRoute.PlatformErrorCodes),
        entry(stringResource(R.string.settings_integrations), integrationBase, "harici entegrasyonlar api metadata enrichment tmdb mdblist aniskip fanart", Icons.Hub, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations),
        entry(stringResource(R.string.tmdb_title), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "tmdb movie database api metadata film dizi poster kapak artwork fragman", Icons.Movie, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.tmdb_api_key), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "tmdb api key anahtar kişisel doğrula validate", Icons.Key, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.tmdb_modern_home), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "tmdb modern ana sayfa keşfet yatay raf içerik", Icons.ViewCarousel, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.tmdb_enrich_continue), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "izlemeye devam et tmdb zenginleştirme metadata continue watching", Icons.AutoAwesome, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.tmdb_preferred_language), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "tmdb tercih edilen dil türkçe english language", Icons.Translate, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.tmdb_detail_blocks), path(integrationBase, stringResource(R.string.integrations_tab_tmdb)), "tmdb detay blokları temel bilgiler oyuncu ekip yapım network bölüm trailer koleksiyon", Icons.Info, KitsugiColors.AccentBlue, route = SettingsRoute.Integrations, pageIndex = 0),
        entry(stringResource(R.string.mdblist_title), path(integrationBase, stringResource(R.string.integrations_tab_mdblist)), "mdblist imdb rotten tomatoes metacritic rating ratings puan zenginleştirme", Icons.Star, KitsugiColors.AccentPurple, route = SettingsRoute.Integrations, pageIndex = 1),
        entry(stringResource(R.string.mdblist_api_key), path(integrationBase, stringResource(R.string.integrations_tab_mdblist)), "mdblist api key anahtar doğrula validate", Icons.Key, KitsugiColors.AccentPurple, route = SettingsRoute.Integrations, pageIndex = 1),
        entry(stringResource(R.string.mdblist_quota_title), path(integrationBase, stringResource(R.string.integrations_tab_mdblist)), "mdblist api kota quota kalan istek rate limit günlük limit yenilenme", Icons.Speed, KitsugiColors.AccentPurple, route = SettingsRoute.Integrations, pageIndex = 1),
        entry(stringResource(R.string.mdblist_score_services), path(integrationBase, stringResource(R.string.integrations_tab_mdblist)), "imdb rotten tomatoes audience metacritic letterboxd tmdb trakt servis puan", Icons.Star, KitsugiColors.AccentPurple, route = SettingsRoute.Integrations, pageIndex = 1),
        entry(stringResource(R.string.aniskip_title), path(integrationBase, stringResource(R.string.integrations_tab_skip)), "aniskip anime skip intro outro op ed açılış kapanış atla timestamps", Icons.SkipNext, KitsugiColors.AccentOrange, route = SettingsRoute.Integrations, pageIndex = 2),
        entry(stringResource(R.string.aniskip_auto_skip), path(integrationBase, stringResource(R.string.integrations_tab_skip)), "otomatik atlama otomatik geçiş auto skip intro ending jenerik", Icons.SkipNext, KitsugiColors.AccentOrange, route = SettingsRoute.Integrations, pageIndex = 2),
        entry(stringResource(R.string.fanart_settings_title), path(integrationBase, "Fanart.tv"), "fanart logo karakter tasarım arka plan poster artwork api key", Icons.Image, KitsugiColors.AccentPink, route = SettingsRoute.Integrations, pageIndex = 3),
        entry(stringResource(R.string.fanart_api_key), path(integrationBase, "Fanart.tv"), "fanart api key anahtar kişisel doğrulama", Icons.Key, KitsugiColors.AccentPink, route = SettingsRoute.Integrations, pageIndex = 3),

        // Data, backup, network, downloads and storage
        entry(backupTitle, backupBase, "veri yedekleme backup geri yükle içe aktar dışa aktar json dns doh", Icons.Storage, KitsugiColors.AccentGreen, route = SettingsRoute.DataBackup, pageIndex = 0),
        entry(stringResource(R.string.inline_export_as_json_fff9b39), path(backupBase, stringResource(R.string.system_backup_reset_section)), "dışa aktar export yedek json liste backup", Icons.Upload, KitsugiColors.AccentGreen, route = SettingsRoute.DataBackup, pageIndex = 0),
        entry(stringResource(R.string.inline_import_from_json_1572af3), path(backupBase, stringResource(R.string.system_backup_reset_section)), "içe aktar import geri yükle json yedek liste", Icons.Download, KitsugiColors.AccentGreen, route = SettingsRoute.DataBackup, pageIndex = 0),
        entry(stringResource(R.string.settings_delete_all_confirm), path(backupBase, stringResource(R.string.system_backup_reset_section)), "tüm listeyi sil verileri temizle reset delete list", Icons.Delete, KitsugiColors.AccentRed, route = SettingsRoute.DataBackup, pageIndex = 0),
        entry("DNS over HTTPS (DoH)", path(backupBase, "DNS (DoH)"), "dns doh dns over https google cloudflare adguard dns.watch gizlilik privacy sağlayıcı provider", Icons.Dns, KitsugiColors.AccentTeal, route = SettingsRoute.DataBackup, pageIndex = 1),
        entry("Logcat", path(backupBase, stringResource(R.string.inline_system_data_settings_fc74ef9)), "logcat günlük log tanılama hata ayıklama developer diagnostics", Icons.BugReport, KitsugiColors.AccentOrange, route = SettingsRoute.DataBackup, pageIndex = 0),
        entry(downloadsTitle, downloadBase, "indirme ayarları downloads wifi wi-fi konum harici indirici hız limiti cbz manga", Icons.Download, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.inline_image_download_location_cfac1ca), downloadBase, "resim görsel poster image indirme konumu klasör location folder", Icons.Image, KitsugiColors.AccentBlue, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.inline_video_download_location_a645733), downloadBase, "video anime manga indirme konumu klasör location folder", Icons.Movie, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_download_only_wifi), downloadBase, "wifi wi-fi mobil veri cellular data indirmeler downloads", Icons.Wifi, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_always_external_downloader_anime), downloadBase, "harici indirici download manager anime dışarı yönlendir", Icons.OpenInNew, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_external_downloader_preference), downloadBase, "harici indirme yöneticisi 1dm adm downloader tercih", Icons.Download, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_speed_limit_setting), downloadBase, "hız limiti indirme hızı download speed bandwidth sınırsız", Icons.Speed, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_save_chapters_cbz), downloadBase, "manga cbz arşiv zip bölüm chapters save", Icons.Archive, KitsugiColors.AccentOrange, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_split_tall_images), downloadBase, "uzun resimleri böl webtoon tall images split", Icons.Crop, KitsugiColors.AccentOrange, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_simultaneous_downloads_setting), downloadBase, "aynı anda eşzamanlı parallel downloads indirme sayısı", Icons.Downloading, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_download_new_chapters), downloadBase, "otomatik manga yeni bölümler indir auto download chapters", Icons.MenuBook, KitsugiColors.AccentOrange, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_download_new_videos), downloadBase, "otomatik video anime yeni bölümler indir auto download episodes", Icons.Movie, KitsugiColors.AccentGreen, route = SettingsRoute.Downloads),
        entry(stringResource(R.string.system_remove_after_read), downloadBase, "okunduktan sonra sil otomatik manga bölüm temizleme remove after read", Icons.DeleteSweep, KitsugiColors.AccentOrange, route = SettingsRoute.Downloads),
        entry(storageTitle, storageBase, "depolama disk alanı önbellek cache temizle yer aç storage usage", Icons.CleaningServices, KitsugiColors.AccentTeal, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_images), storageBase, "görsel poster resim coil cache önbellek temizle", Icons.Image, KitsugiColors.AccentBlue, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_p2p), storageBase, "p2p torrent akış cache önbellek disk", Icons.CloudDownload, KitsugiColors.AccentPurple, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_manga), storageBase, "manga sayfa cache önbellek okuma", Icons.MenuBook, KitsugiColors.AccentOrange, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_previews), storageBase, "video önizleme thumbnail seekbar kare cache", Icons.Movie, KitsugiColors.AccentTeal, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_subtitles), storageBase, "altyazı subtitles srt archive cache önbellek", Icons.Subtitles, KitsugiColors.AccentYellow, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_database), storageBase, "veritabanı database detay çözümleme çeviri cache", Icons.Storage, KitsugiColors.AccentIndigo, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_diagnostics), storageBase, "teşhis log hata crash diagnostics cache temizle", Icons.BugReport, KitsugiColors.AccentRed, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_webview), storageBase, "webview çerez cookies oturum cache veri temizle", Icons.Public, KitsugiColors.AccentGreen, route = SettingsRoute.Storage),
        entry(stringResource(R.string.storage_bucket_extensions), storageBase, "eklenti dosyaları plugin apk alan depolama", Icons.Extension, KitsugiColors.AccentPink, route = SettingsRoute.Storage),
        entry(stringResource(R.string.settings_downloads), path(dataSection, stringResource(R.string.settings_downloads)), "indirilen videolar altyazılar resimler offline downloads", Icons.Download, KitsugiColors.AccentGreen, action = SettingsMainAction.Downloads),

        // Support and diagnostics
        entry(stringResource(R.string.settings_about), path(supportSection, stringResource(R.string.settings_about)), "hakkında sürüm version github açık kaynak contributors", Icons.Info, KitsugiColors.AccentIndigo, action = SettingsMainAction.About),
        entry(stringResource(R.string.settings_feedback), path(supportSection, stringResource(R.string.settings_feedback)), "geri bildirim hata bildirimi öneri bug report feature request", Icons.Feedback, KitsugiColors.AccentBlue, route = SettingsRoute.Feedback),
        entry(stringResource(R.string.settings_experimental_title), path(supportSection, stringResource(R.string.settings_experimental_title)), "deneysel experimental beta otomatik liste takip tracking scrobble puanlama hızlı puan quick rate izlenen bölüm", Icons.Science, KitsugiColors.AccentOrange, route = SettingsRoute.ExperimentalFeatures),
        entry(stringResource(R.string.experimental_autotrack_title), path(supportSection, stringResource(R.string.settings_experimental_title)), "otomatik liste takibi oynatınca listeye ekle tracking auto add progress bölüm ilerleme deneysel experimental", Icons.PlaylistAddCheck, KitsugiColors.AccentOrange, route = SettingsRoute.ExperimentalFeatures)
    )
}
