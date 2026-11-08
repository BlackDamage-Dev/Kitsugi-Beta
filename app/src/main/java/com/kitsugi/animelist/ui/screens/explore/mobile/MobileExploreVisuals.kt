package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.toTurkishGenre

/**
 * İŞ 2 — MOBİL KEŞFET GÖRSEL BİLEŞENLERİ (V2, ONAYLI TASARIM)
 * ============================================================
 * Bu dosya, onaylanan görsel referanstaki (02 — ONAYLANAN GÖRSEL REFERANS.png)
 * mobil telefon/tablet Keşfet ekranının İZOLE Compose görsel parçalarını içerir.
 *
 * Dondurulan dört bileşenli ortak sözleşme (değiştirilemez):
 *  - [MobileExploreHeader]  : KITSUGI + actions slotu + büyük Keşfet başlığı + kaynak pili
 *  - [MobileExploreTabs]    : Diziler / Filmler / Anime / Manga sekme sırası
 *  - [MobileExploreHero]    : ~2.3:1 vitrin, "Yeni hikayeler keşfet" + "İncele"
 *  - [MobileExploreShelf]   : başlık + kaynak adı + Tümünü gör + yatay poster rafı
 *
 * SINIRLAR: Gerçek ekran, pager, kaynak/veri seçimi, navigasyon, sheet'ler ve TV
 * İŞ 1'in (entegrasyon) sorumluluğundadır. Bu dosya ağ isteği, katalog API çağrısı
 * veya yönlendirme YAPMAZ; yalnızca parametre olarak gelen veriyi çizer ve
 * callback'leri aynen iletir. Coil'in gerçek resim indirmesi normaldir.
 *
 * Renkler [KitsugiColors] / [LocalKitsugiAccent] tema altyapısından alınır:
 * onaylı koyu/mint kombinasyonunda referansa sadık, diğer temalarda uygulama
 * tercihine uyumlu. Referans ölçüleri (384dp genişlik): yatay kenar 13dp,
 * kart 84×135dp, kart aralığı 7dp, hero ~2.3:1, köşeler 8–10dp.
 */

// ── Referanstan ölçülen sabitler ─────────────────────────────────────────────
private val MobileExploreEdgePadding = 13.dp
private val MobileExploreCardWidth = 84.dp
private const val MobileExploreCardAspect = 84f / 135f
private val MobileExploreCardGap = 7.dp
private val MobileExploreCardShape = RoundedCornerShape(8.dp)
private val MobileExploreHeroShape = RoundedCornerShape(10.dp)
private const val MobileExploreHeroAspect = 2.3f

/** Instrumented testlerin kullandığı kararlı test etiketleri. */
internal object MobileExploreTestTags {
    const val HEADER = "mobile_explore_header"
    const val SOURCE_PILL = "mobile_explore_source_pill"
    const val TABS = "mobile_explore_tabs"
    const val TAB_PREFIX = "mobile_explore_tab_"
    const val HERO = "mobile_explore_hero"
    const val HERO_CTA = "mobile_explore_hero_cta"
    const val HERO_LOADING = "mobile_explore_hero_loading"
    const val SHELF = "mobile_explore_shelf"
    const val SHELF_LOADING = "mobile_explore_shelf_loading"
    const val SHELF_LIST = "mobile_explore_shelf_list"
    const val SEE_ALL = "mobile_explore_see_all"
    const val CARD = "mobile_explore_card"
    const val CARD_SCORE = "mobile_explore_card_score"
    const val CARD_STATUS = "mobile_explore_card_status"
}

/** Kaynak görünen adı: ALL yerelleştirilmiş "Tümü"/"All", diğerleri kısa marka adı. */
@Composable
private fun exploreSourceLabel(source: ExplorePlatform): String =
    if (source == ExplorePlatform.ALL) stringResource(R.string.mobile_explore_source_all)
    else source.shortName

/** Referanstaki ince açık çerçeve; tema tokenından türetilir (tema uyumlu). */
@Composable
private fun mobileExploreBorderColor(): Color = KitsugiColors.TextMuted.copy(alpha = 0.38f)

// ─────────────────────────────────────────────────────────────────────────────
// 1) HEADER — KITSUGI + gerçek eylemler için actions slotu + Keşfet + kaynak pili
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Üst başlık bloğu. [actions] İŞ 1'in verdiği GERÇEK eylemleri çizer; bu bileşen
 * kendi başına sahte ikon/rota üretmez. Dar ekranda marka yazısı küçülür/kesilir,
 * actions slotu yerini korur. Global üst barla mükerrer çizim entegrasyonda denetlenir.
 */
@Composable
internal fun MobileExploreHeader(
    selectedPlatform: ExplorePlatform,
    onSourceClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceLabel = exploreSourceLabel(selectedPlatform)
    val selectSourceCd = stringResource(R.string.mobile_explore_source_select)
    Column(modifier = modifier.fillMaxWidth().testTag(MobileExploreTestTags.HEADER)) {
        // Marka + gerçek eylemler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileExploreEdgePadding)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mobile_explore_brand),
                color = KitsugiColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions,
            )
        }
        // Büyük Keşfet başlığı + ince çerçeveli kaynak pili
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileExploreEdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.kesfet),
                color = KitsugiColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Compact visual bounds; Compose can expand small touch targets. Verify
            // hit areas and overlap with adjacent controls on a real device.
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .widthIn(min = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSourceClick)
                    .semantics { contentDescription = "$selectSourceCd: $sourceLabel" }
                    .testTag(MobileExploreTestTags.SOURCE_PILL),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .border(1.dp, mobileExploreBorderColor(), CircleShape)
                        .clip(CircleShape)
                        .background(KitsugiColors.Surface)
                        .padding(start = 14.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceLabel,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) TABS — Diziler / Filmler / Anime / Manga
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dört sekmeli yatay sıra. Seçili sekme vurgu rengi + ince alt çizgi alır,
 * altında tam genişlik ayırıcı çizilir. Geçersiz [selectedIndex] çökme üretmez
 * (hiçbir sekme seçili görünür). Yatay swipe/pager bu bileşenin işi DEĞİLDİR (İŞ 1).
 * Büyük font / dar ekranda sıra yatay kaydırılabilir, erişim kaybolmaz.
 */
@Composable
internal fun MobileExploreTabs(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.mobile_explore_tab_series),
        stringResource(R.string.mobile_explore_tab_movies),
        stringResource(R.string.mobile_explore_tab_anime),
        stringResource(R.string.mobile_explore_tab_manga),
    )
    val accent = LocalKitsugiAccent.current
    Column(modifier = modifier.fillMaxWidth().testTag(MobileExploreTestTags.TABS)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MobileExploreEdgePadding),
            horizontalArrangement = Arrangement.spacedBy(21.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) },
                        )
                        .testTag(MobileExploreTestTags.TAB_PREFIX + index),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // İç yatay boşluk: alt çizgi referanstaki gibi metinden geniş olur.
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) accent else KitsugiColors.TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = KitsugiColors.Border)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3) HERO — geniş yatay vitrin (~2.3:1)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Onaylı tasarımdaki vitrin: hafif yuvarlatılmış köşe + ince sınır, alttan siyaha
 * gradient, solda yerelleştirilmiş "Yeni hikayeler keşfet", sağda "İncele".
 *
 * [item] != null ise önce `backdropUrl`, yoksa `imageUrl` gösterilir (backdrop
 * yüklenemezse poster'e düşülür); tıklama `onItemClick(item)` çağırır. Tür/kaynak
 * değiştirilmez. Veri yoksa placeholder, yükleniyorsa shimmer; her iki durumda da
 * buton tıklanamaz. NSFW için mevcut güvenli [KitsugiNsfwImage] altyapısı kullanılır.
 */
@Composable
internal fun MobileExploreHero(
    item: JikanSearchResult?,
    isLoading: Boolean,
    blurAdultMedia: Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val caption = stringResource(R.string.mobile_explore_hero_caption)
    val cta = stringResource(R.string.mobile_explore_hero_cta)
    val loadingCd = stringResource(R.string.mobile_explore_loading)
    val interactive = item != null && !isLoading
    val onArtwork = item != null && !isLoading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MobileExploreEdgePadding)
            .aspectRatio(MobileExploreHeroAspect)
            .clip(MobileExploreHeroShape)
            .background(KitsugiColors.Surface)
            .border(1.dp, mobileExploreBorderColor(), MobileExploreHeroShape)
            .clickable(
                enabled = interactive,
                role = Role.Button,
                onClickLabel = cta,
                onClick = { item?.let(onItemClick) },
            )
            .semantics {
                // The artwork is decorative, but the selected media must still be
                // announced; the generic caption does not identify the item.
                if (item != null && !isLoading) contentDescription = item.title
            }
            .testTag(MobileExploreTestTags.HERO),
    ) {
        when {
            isLoading -> {
                KitsugiShimmerBlock(
                    brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush(),
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = loadingCd }
                        .testTag(MobileExploreTestTags.HERO_LOADING),
                    cornerRadius = 0.dp,
                )
            }
            item != null -> {
                val backdrop = item.backdropUrl?.takeIf { it.isNotBlank() }
                val poster = item.imageUrl?.takeIf { it.isNotBlank() }
                var backdropFailed by remember(item.malId, backdrop, poster) { mutableStateOf(false) }
                val model = if (backdrop != null && !backdropFailed) backdrop else poster
                KitsugiNsfwImage(
                    model = model,
                    // Dekoratif: metinler semantik taşır, başlık kartta değil vitrin
                    // metninde okunur; mükerrer okumayı önlemek için CD null.
                    contentDescription = null,
                    isAdult = item.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    modifier = Modifier.fillMaxSize(),
                    highQuality = true,
                    onLoadingFailed = {
                        if (backdrop != null && poster != null) backdropFailed = true
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                            ),
                        ),
                )
            }
            // item == null && !isLoading: boş placeholder — yüzey + pasif CTA.
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption,
                color = if (onArtwork) Color.White else KitsugiColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 48dp dokunma alanı; görsel buton referanstaki gibi kompakt.
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = interactive,
                        role = Role.Button,
                        onClick = { item?.let(onItemClick) },
                    )
                    .testTag(MobileExploreTestTags.HERO_CTA),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = if (interactive) KitsugiColors.AccentBrush
                            else SolidColor(KitsugiColors.TextMuted.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = cta,
                        color = if (interactive) KitsugiColors.OnAccent
                        else KitsugiColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4) SHELF — başlık + kaynak + Tümünü gör + yatay poster rafı
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Onaylı tasarımdaki raf: solda beyaz kalın başlık + yanında küçük kaynak adı,
 * sağda [onSeeAllClick] null değilse "Tümünü gör". Posterler yatay kaydırılır;
 * 384dp'de dört tam + beşincinin bir kısmı görünür (84×135dp, 7dp aralık).
 * Telefonda landscape'de de kartlar daima dikeydir.
 *
 * Boş/hata politikası parent'ındır (İŞ 1): sonuç boş ve yüklenmiyorsa raf hiç
 * çizilmez; boş+yükleniyorsa shimmer; dolu+yükleniyorsa mevcut (stale) veri
 * kaybolmadan gösterilmeye devam eder.
 */
@Composable
internal fun MobileExploreShelf(
    title: String,
    source: ExplorePlatform,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty() && !isLoading) return
    val sourceLabel = exploreSourceLabel(source)
    val loadingCd = stringResource(R.string.mobile_explore_loading)
    Column(modifier = modifier.fillMaxWidth().testTag(MobileExploreTestTags.SHELF)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileExploreEdgePadding)
                .heightIn(min = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sourceLabel,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (onSeeAllClick != null) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onSeeAllClick)
                        .padding(horizontal = 4.dp)
                        .testTag(MobileExploreTestTags.SEE_ALL),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.mobile_explore_see_all),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (isLoading && results.isEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MobileExploreEdgePadding)
                    .semantics { contentDescription = loadingCd }
                    .testTag(MobileExploreTestTags.SHELF_LOADING),
                horizontalArrangement = Arrangement.spacedBy(MobileExploreCardGap),
            ) {
                val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
                repeat(5) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier
                            .width(MobileExploreCardWidth)
                            .aspectRatio(MobileExploreCardAspect),
                        cornerRadius = 8.dp,
                    )
                }
            }
        } else {
            // Callers scope the composable with a stable shelf identity. A translated
            // title can change independently and must not reset the row position.
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            LazyRow(
                state = listState,
                flingBehavior = KitsugiScrollDefaults.rememberRowSnapFling(listState),
                modifier = Modifier.fillMaxWidth().testTag(MobileExploreTestTags.SHELF_LIST),
                contentPadding = PaddingValues(horizontal = MobileExploreEdgePadding),
                horizontalArrangement = Arrangement.spacedBy(MobileExploreCardGap),
            ) {
                itemsIndexed(
                    items = results,
                    // Kaynak+tür+kimlik çakışmasını önleyen merkezi anahtar; index
                    // aynı öğenin tekrarlarında da benzersizlik sağlar.
                    key = { index, result -> result.kitsugiLazyKey("mex", index) },
                    contentType = { _, _ -> "mex_poster" },
                ) { _, result ->
                    MobileExplorePosterCard(
                        result = result,
                        inList = alreadyInList(result),
                        mediaEntry = getMediaEntry(result),
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        onClick = { onItemClick(result) },
                        onLongClick = { onLongClickItem(result) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Poster kartı — bu dosyaya özel, ortak kart dosyalarına dokunmaz
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileExplorePosterCard(
    result: JikanSearchResult,
    inList: Boolean,
    mediaEntry: MediaEntry?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val displayTitle = remember(result, titleLanguage) { result.getDisplayTitle(titleLanguage) }
    var imageFailed by remember(result.imageUrl) { mutableStateOf(false) }
    val hasArtwork = !result.imageUrl.isNullOrBlank() && !imageFailed
    // Gerçek tür/yıl: eksik parça ve ayraç atlanır, uydurma veri yok.
    val genre = result.genres.firstOrNull { it.isNotBlank() }?.trim()?.toTurkishGenre()
        ?.takeIf { it.isNotEmpty() }
    val year = result.year?.takeIf { it > 0 }
    val meta = listOfNotNull(genre, year?.toString()).joinToString(" • ")
    val inListCd = stringResource(R.string.mobile_explore_in_list)
    // The approved poster is unobstructed: score/status are available to accessibility
    // services but never drawn as badges over the image. Keep hideScores effective.
    val displayScore = if (hideScores) null else mobileExplorePreferredDisplayScore(mediaEntry?.score, result.score)
    val scoreText = displayScore?.let {
        if (scoreFormat == "POINT_5" || scoreFormat == "STARS" || scoreFormat == "POINT_3") "$it/10"
        else PreferenceHelpers.formatScore(it, scoreFormat, false)
    }
    val scoreDescription = scoreText?.let { stringResource(R.string.mobile_explore_score_cd, it) }
    val listDescription = if (mediaEntry != null) mediaEntry.status.localizedLabel() else if (inList) inListCd else null
    val stateText = listOfNotNull(listDescription, scoreDescription).joinToString(", ")
    Box(
        modifier = Modifier
            .width(MobileExploreCardWidth)
            .aspectRatio(MobileExploreCardAspect)
            .clip(MobileExploreCardShape)
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, mobileExploreBorderColor(), MobileExploreCardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = displayTitle
                if (stateText.isNotEmpty()) stateDescription = stateText
            }
            .testTag(MobileExploreTestTags.CARD),
    ) {
        if (hasArtwork) {
            KitsugiNsfwImage(
                model = result.imageUrl,
                // Başlık semantiği kart düzeyinde taşınır (mükerrer okuma yok).
                contentDescription = null,
                isAdult = result.isAdult,
                blurAdultMedia = blurAdultMedia,
                modifier = Modifier.fillMaxSize(),
                onLoadingFailed = { imageFailed = true },
            )
        } else {
            // Artwork yoksa/yüklenemediyse GÖRÜNEN başlık fallback'i.
            Text(
                text = displayTitle,
                color = KitsugiColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
        }
        if (meta.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.34f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    ),
            )
            Text(
                text = meta,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
}
