package com.kitsugi.animelist.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.diagnostics.PlatformErrorPlatform
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.data.platform.PlatformErrorCode as ListErrorCode
import com.kitsugi.animelist.data.platform.PlatformErrorSeverity as ListSeverity
import com.kitsugi.animelist.ui.components.KitsugiSettingsSegmentedTabs
import com.kitsugi.animelist.ui.components.KitsugiSettingsTabOption
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch

/**
 * Searchable, localized reference list for ALL stable diagnostic codes:
 * Manga Source + CloudStream plugin codes AND the list-source (AniList,
 * MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl + common) codes that the
 * Cross-Sync "Error Code Reference" dialog shows. Everything resolves from the
 * locale resource files (values/ = TR, values-en/ = EN).
 *
 * Kategoriler artık "Görünüm & Tercihler" ile aynı üst sekme bandında:
 * sekmeye dokunarak veya sağa/sola kaydırarak kategori değişir. Bant ekrana
 * sığmazsa **kendi içinde** kaydırılır, ekranın sağına/soluna taşmaz.
 */
@Composable
fun PlatformErrorCatalogScreen() {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    val tabs = listOf(
        ErrorCatalogTab(
            key = "ALL",
            label = stringResource(R.string.platform_error_filter_all),
            icon = Icons.Apps,
        ),
        ErrorCatalogTab(
            key = "MANGA",
            label = stringResource(R.string.platform_error_filter_manga),
            icon = Icons.MenuBook,
        ),
        ErrorCatalogTab(
            key = "CLOUDSTREAM",
            label = stringResource(R.string.platform_error_filter_cloudstream),
            icon = Icons.Extension,
        ),
    ) + ListPlatform.entries.map { platform ->
        ErrorCatalogTab(
            key = platform.name,
            label = stringResource(platform.labelRes),
            icon = Icons.Public,
            platformId = platformLogoId(platform),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val normalizedQuery = query.trim().lowercase()
    val currentTabKey = tabs[pagerState.currentPage].key

    // Görünür kategorinin kayıt sayısı (arama süzgeci uygulanmış)
    val resultCount = remember(currentTabKey, normalizedQuery, context) {
        categoryDiagnosticCodes(context, currentTabKey, normalizedQuery).size +
            categoryListCodes(context, currentTabKey, normalizedQuery).size
    }

    Surface(modifier = Modifier.fillMaxSize(), color = KitsugiColors.Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.platform_error_catalog_count, resultCount),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text(stringResource(R.string.platform_error_catalog_search)) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))

            KitsugiSettingsSegmentedTabs(
                tabs = tabs.map { tab ->
                    KitsugiSettingsTabOption(
                        title = tab.label,
                        icon = tab.icon,
                        platformId = tab.platformId,
                    )
                },
                selectedIndex = pagerState.currentPage,
                accentColor = accentColor,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                ErrorCatalogPage(
                    filterKey = tabs[page].key,
                    normalizedQuery = normalizedQuery,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Rehber üst bandındaki bir kategori sekmesi. */
private data class ErrorCatalogTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val platformId: String? = null,
)

/** Liste kaynağının orijinal logosu (KitsugiPlatformLogos) için kimlik. */
private fun platformLogoId(platform: ListPlatform): String? = when (platform) {
    ListPlatform.GENERIC -> null
    ListPlatform.ANILIST -> "anilist"
    ListPlatform.MAL -> "mal"
    ListPlatform.KITSU -> "kitsu"
    ListPlatform.SHIKIMORI -> "shikimori"
    ListPlatform.BANGUMI -> "bangumi"
    ListPlatform.TMDB -> "tmdb"
    ListPlatform.SIMKL -> "simkl"
}

/** Kategoriye ait tanılama kodları (Manga / CloudStream); liste kaynağı sekmelerinde boş. */
private fun categoryDiagnosticCodes(
    context: Context,
    filterKey: String,
    normalizedQuery: String,
): List<PlatformErrorCode> {
    val platform = when (filterKey) {
        "ALL" -> null
        "MANGA" -> PlatformErrorPlatform.Manga
        "CLOUDSTREAM" -> PlatformErrorPlatform.CloudStream
        else -> return emptyList()
    }
    return PlatformErrorCode.all(platform)
        .filter { definition ->
            if (normalizedQuery.isBlank()) return@filter true
            val searchable = listOf(
                definition.id,
                definition.category,
                context.getString(definition.titleRes),
                context.getString(definition.causeRes),
                context.getString(definition.actionRes),
            ).joinToString(" ").lowercase()
            searchable.contains(normalizedQuery)
        }
        .sortedBy { it.id }
}

/** Kategoriye ait liste kaynağı kodları (AniList, MAL, Kitsu, …); "Tümü" hepsini verir. */
private fun categoryListCodes(
    context: Context,
    filterKey: String,
    normalizedQuery: String,
): List<ListErrorCode> {
    val platform: ListPlatform? = if (filterKey == "ALL") {
        null
    } else {
        ListPlatform.entries.firstOrNull { it.name == filterKey } ?: return emptyList()
    }
    return ListErrorCode.entries
        .filter { code -> platform == null || code.platform == platform }
        .filter { code ->
            if (normalizedQuery.isBlank()) return@filter true
            val searchable = listOf(
                code.code,
                context.getString(code.platform.labelRes),
                context.getString(code.titleRes),
                context.getString(code.whyRes),
                context.getString(code.fixRes),
            ).joinToString(" ").lowercase()
            searchable.contains(normalizedQuery)
        }
        .sortedBy { it.code }
}

/** Tek bir kategori sayfası: kayıt yoksa boş durum, varsa kaydırılabilir liste. */
@Composable
private fun ErrorCatalogPage(
    filterKey: String,
    normalizedQuery: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val diagEntries = remember(filterKey, normalizedQuery, context) {
        categoryDiagnosticCodes(context, filterKey, normalizedQuery)
    }
    val listEntries = remember(filterKey, normalizedQuery, context) {
        categoryListCodes(context, filterKey, normalizedQuery)
    }

    if (diagEntries.isEmpty() && listEntries.isEmpty()) {
        Text(
            stringResource(R.string.platform_error_catalog_empty),
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            color = KitsugiColors.TextMuted,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(listEntries, key = { it.code }) { code ->
            ListSourceErrorBlock(code = code, modifier = Modifier.fillMaxWidth())
        }
        items(diagEntries, key = { it.id }) { definition ->
            PlatformErrorExplanationBlock(
                code = definition.id,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Liste kaynağı (AniList, MAL, …) hata kodları için yerelleştirilmiş açıklama kartı. */
@Composable
private fun ListSourceErrorBlock(code: ListErrorCode, modifier: Modifier = Modifier) {
    val tint = code.severity.tint()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.07f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = code.code,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tint.copy(alpha = 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text(
                text = stringResource(code.platform.labelRes),
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(code.severity.labelRes),
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
            )
        }
        Text(
            text = stringResource(code.titleRes),
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        ListExplanationLine(
            label = stringResource(R.string.pf_err_why_label),
            value = stringResource(code.whyRes),
        )
        ListExplanationLine(
            label = stringResource(R.string.pf_err_fix_label),
            value = stringResource(code.fixRes),
        )
        Text(
            text = stringResource(
                if (code.retryable) R.string.pf_err_retryable_yes else R.string.pf_err_retryable_no
            ),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ListExplanationLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

private fun ListSeverity.tint(): Color = when (this) {
    ListSeverity.INFO -> Color(0xFF64B5F6)
    ListSeverity.WARNING -> Color(0xFFFFB74D)
    ListSeverity.ERROR, ListSeverity.CRITICAL -> KitsugiColors.AccentRed
}
