package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.FilledTonalButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiFavoriteItem
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiUserCollection
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiNameLocalizer
import com.kitsugi.animelist.data.remote.BangumiSubjectTitleResolver
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.BangumiProfileState
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.R

/**
 * Bangumi profil sekmesi (koleksiyon özeti, tür/durum filtreleri, liste, favori karakter ve kişiler).
 *
 * Tasarım ve kapsam notları:
 *  - Anime, manga/kitap ve REAL film/dizi kayıtları Kitsugi'nin Bangumi detay akışına
 *    (`onOpenAnimeOrManga` → ApiResultDetail, source = "bangumi") yönlendirilir.
 *  - Oyun, müzik, karakter ve kişi favorileri bgm.tv sayfasında açılır.
 *  - Bildirimler (bgm.tv/notify/all) web oturumu gerektirdiğinden uygulama içi WebView'de açılır.
 */

private val BANGUMI_TYPE_FILTERS: List<Int> = listOf(
    BangumiApiClient.SubjectType.ANIME,
    BangumiApiClient.SubjectType.BOOK,
    BangumiApiClient.SubjectType.GAME,
    BangumiApiClient.SubjectType.MUSIC,
    BangumiApiClient.SubjectType.REAL
)

/** Durum filtresi: 0 = tümü, diğerleri Bangumi `CollectionType` değerleri. */
private val BANGUMI_STATUS_FILTERS: List<Pair<Int, Int>> = listOf(
    0 to R.string.notif_filter_all,
    BangumiApiClient.CollectionType.WISH to R.string.profile_status_planned,
    BangumiApiClient.CollectionType.DOING to R.string.profile_status_in_progress,
    BangumiApiClient.CollectionType.DONE to R.string.profile_status_completed,
    BangumiApiClient.CollectionType.ON_HOLD to R.string.profile_status_on_hold,
    BangumiApiClient.CollectionType.DROPPED to R.string.profile_status_dropped
)

private fun subjectTypeLabel(subjectType: Int): String = when (subjectType) {
    BangumiApiClient.SubjectType.ANIME -> KitsugiStrings.get(R.string.media_type_anime)
    BangumiApiClient.SubjectType.BOOK -> KitsugiStrings.get(R.string.bangumi_subject_manga_book)
    BangumiApiClient.SubjectType.GAME -> KitsugiStrings.get(R.string.bangumi_subject_game)
    BangumiApiClient.SubjectType.MUSIC -> KitsugiStrings.get(R.string.bangumi_subject_music)
    BangumiApiClient.SubjectType.REAL -> KitsugiStrings.get(R.string.bangumi_subject_tv_film)
    else -> KitsugiStrings.get(R.string.bangumi_tag_other)
}

private fun collectionStatusLabel(collectionType: Int): String =
    BANGUMI_STATUS_FILTERS.firstOrNull { it.first == collectionType }
        ?.let { KitsugiStrings.get(it.second) }
        ?: KitsugiStrings.get(R.string.profile_status_unknown)

/** Short labels for the summary cards. */
private fun shortStatusLabel(collectionType: Int): String = when (collectionType) {
    BangumiApiClient.CollectionType.WISH -> KitsugiStrings.get(R.string.profile_status_planned)
    BangumiApiClient.CollectionType.DOING -> KitsugiStrings.get(R.string.profile_status_in_progress)
    BangumiApiClient.CollectionType.DONE -> KitsugiStrings.get(R.string.profile_status_completed)
    BangumiApiClient.CollectionType.ON_HOLD -> KitsugiStrings.get(R.string.profile_status_on_hold)
    BangumiApiClient.CollectionType.DROPPED -> KitsugiStrings.get(R.string.profile_status_dropped)
    else -> "-"
}

private fun progressLabel(item: BangumiUserCollection): String? {
    val subject = item.subject
    return when (item.subjectType) {
        BangumiApiClient.SubjectType.BOOK -> {
            val total = subject?.volumes ?: 0
            when {
                item.volStatus > 0 && total > 0 -> KitsugiStrings.get(R.string.profile_volume_progress, item.volStatus, total)
                item.volStatus > 0 -> KitsugiStrings.get(R.string.profile_volume_progress_single, item.volStatus)
                else -> null
            }
        }
        BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.REAL -> {
            val total = subject?.eps ?: 0
            when {
                item.epStatus > 0 && total > 0 -> KitsugiStrings.get(R.string.profile_episodes_progress, item.epStatus, total)
                item.epStatus > 0 -> KitsugiStrings.get(R.string.profile_episode_progress_single, item.epStatus)
                else -> null
            }
        }
        else -> null
    }
}

@Composable
fun BangumiProfileContent(
    state: BangumiProfileState,
    accentColor: Color,
    titleLanguage: String = "ROMAJI",
    onOpenAnimeOrManga: (subjectId: Int, mediaType: MediaType, title: String, imageUrl: String?) -> Unit,
    onCharacterClick: (charId: Int, name: String?, imageUrl: String?) -> Unit,
    onPersonClick: (personId: Int, name: String?, imageUrl: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf(BangumiApiClient.SubjectType.ANIME) }
    var statusFilter by rememberSaveable { mutableStateOf(0) }

    val typeCounts = remember(state.collections) {
        state.collections.groupingBy { it.subjectType }.eachCount()
    }
    val itemsOfType = remember(state.collections, typeFilter) {
        state.collections.filter { it.subjectType == typeFilter }
    }
    val statusCounts = remember(itemsOfType) {
        itemsOfType.groupingBy { it.type }.eachCount()
    }
    val visibleItems = remember(itemsOfType, statusFilter) {
        if (statusFilter == 0) itemsOfType else itemsOfType.filter { it.type == statusFilter }
    }

    // Bangumi koleksiyon ucu yalnızca özgün adı döndürür. Latin ad daha önce hiçbir yerde
    // (kütüphane içe aktarma, detay, keşfet) çözülmediyse satır CJK kalırdı; bu efekt
    // eksikleri arka planda çözer (resolver sonucu kalıcı önbelleğe yazar) ve [resolvedTitles]
    // üzerinden satırları yeniden çizer — kullanıcı artık hangi ekranda olursa olsun seçtiği
    // başlık dilini görür.
    val resolvedTitles = remember { mutableStateMapOf<Int, String>() }
    LaunchedEffect(state.collections, titleLanguage) {
        resolvedTitles.clear()
        val wantsLatin = !titleLanguage.equals("NATIVE", ignoreCase = true) &&
            !titleLanguage.equals("JAPANESE_STAFF", ignoreCase = true)
        if (!wantsLatin || state.collections.isEmpty()) return@LaunchedEffect
        val pending = state.collections.mapNotNull { collection ->
            val subject = collection.subject ?: return@mapNotNull null
            val localized = BangumiNameLocalizer.entity(subject.name, subject.nameCn)
            val base = localized.displayFor(titleLanguage)
            val cached = BangumiTitleCache.get(collection.subjectId)
            val english = localized.english ?: cached?.english
            val romaji = localized.romaji ?: cached?.romaji
            val hasReliableEnglish = BangumiSubjectTitleResolver.hasReliableEnglish(
                BangumiTitleCache.LatinTitles(romaji, english, localized.native ?: cached?.native)
            )
            val needsEnglish = titleLanguage.equals("ENGLISH", ignoreCase = true) && !hasReliableEnglish
            val needsAnyLatin = PreferenceHelpers.hasCjkCharacters(base) &&
                BangumiTitleCache.localizedTitleFor(collection.subjectId, base, titleLanguage) == null
            if (!needsEnglish && !needsAnyLatin) return@mapNotNull null

            BangumiSubjectTitleResolver.Request(
                rawId = collection.subjectId,
                nativeTitle = subject.name.ifBlank { subject.nameCn },
                mediaType = BangumiIdNamespace.mediaTypeFor(collection.subjectType, subject.platform),
                subjectType = collection.subjectType
            )
        }
        if (pending.isEmpty()) return@LaunchedEffect
        runCatching {
            com.kitsugi.animelist.data.auth.BangumiApiClient.runAsBackground {
                BangumiSubjectTitleResolver.resolve(pending, background = true, tabMode = true)
            }
        }
        // Çözülen adları yayınla → satırlar yeniden çizilir (önbellek zaten kalıcı).
        for (collection in state.collections) {
            val subject = collection.subject ?: continue
            val base = BangumiNameLocalizer.entity(subject.name, subject.nameCn).displayFor(titleLanguage)
            BangumiTitleCache.localizedTitleFor(collection.subjectId, base, titleLanguage)
                ?.let { resolvedTitles[collection.subjectId] = it }
        }
    }

    val typeOptions = BANGUMI_TYPE_FILTERS.map { type ->
        type to "${subjectTypeLabel(type)} (${typeCounts[type] ?: 0})"
    }
    val statusOptions = BANGUMI_STATUS_FILTERS.map { (value, labelRes) ->
        val count = if (value == 0) itemsOfType.size else (statusCounts[value] ?: 0)
        value to "${KitsugiStrings.get(labelRes)} ($count)"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BangumiHeaderCard(
                state = state,
                onOpenProfile = { uriHandler.openUri("https://bgm.tv/user/${state.userName}") },
                onOpenNotifications = { showNotifications = true }
            )
        }

        item {
            BangumiChipRow(
                options = typeOptions,
                selected = typeFilter,
                accentColor = accentColor,
                onSelect = {
                    typeFilter = it
                    statusFilter = 0
                }
            )
        }

        item {
            BangumiSummaryCard(
                typeLabel = subjectTypeLabel(typeFilter),
                statusCounts = statusCounts,
                accentColor = accentColor
            )
        }

        item {
            BangumiChipRow(
                options = statusOptions,
                selected = statusFilter,
                accentColor = accentColor,
                onSelect = { statusFilter = it }
            )
        }

        if (visibleItems.isEmpty()) {
            item {
                Text(
                    text = if (state.collections.isEmpty()) {
                        stringResource(R.string.bangumi_de_henuz_koleksiyonunuz_yok)
                    } else {
                        stringResource(R.string.profile_filter_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextMuted),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        } else {
            items(
                visibleItems,
                key = { "${it.subjectType}-${it.subjectId}" }
            ) { collection ->
                val resolvedTitle = resolvedTitles[collection.subjectId]
                BangumiCollectionRow(
                    collection = collection,
                    accentColor = accentColor,
                    resolvedTitle = resolvedTitle,
                    titleLanguage = titleLanguage,
                    onClick = {
                        if (BangumiIdNamespace.supportsSubjectType(collection.subjectType)) {
                            onOpenAnimeOrManga(
                                collection.subjectId,
                                BangumiIdNamespace.mediaTypeFor(collection.subjectType, collection.subject?.platform),
                                resolvedTitle
                                    ?: BangumiTitleCache.localizedTitleFor(
                                        collection.subjectId,
                                        collection.subject?.let {
                                            BangumiNameLocalizer.entity(it.name, it.nameCn).displayFor(titleLanguage)
                                        },
                                        titleLanguage
                                    )
                                    ?: collection.subject?.let {
                                    BangumiNameLocalizer.entity(it.name, it.nameCn).displayFor(titleLanguage)
                                } ?: "#${collection.subjectId}",
                                collection.subject?.images?.poster
                            )
                        } else {
                            uriHandler.openUri("https://bgm.tv/subject/${collection.subjectId}")
                        }
                    }
                )
            }
        }

        if (state.characterFavorites.isNotEmpty()) {
            item {
                BangumiSectionTitle(stringResource(R.string.profile_favorite_characters_count, state.characterFavorites.size))
            }
            item {
                BangumiFavoriteRow(
                    favorites = state.characterFavorites,
                    titleLanguage = titleLanguage,
                    onClick = { item ->
                        onCharacterClick(item.id, BangumiNameLocalizer.entity(item.name).displayFor(titleLanguage), item.imageUrl)
                    }
                )
            }
        }

        if (state.personFavorites.isNotEmpty()) {
            item {
                BangumiSectionTitle(stringResource(R.string.profile_favorite_people_count, state.personFavorites.size))
            }
            item {
                BangumiFavoriteRow(
                    favorites = state.personFavorites,
                    titleLanguage = titleLanguage,
                    onClick = { item ->
                        onPersonClick(item.id, BangumiNameLocalizer.entity(item.name).displayFor(titleLanguage), item.imageUrl)
                    }
                )
            }
        }
    }

    if (showNotifications) {
        KitsugiWebViewDialog(
            title = stringResource(R.string.profile_platform_notifications, "Bangumi"),
            url = "https://bgm.tv/notify/all",
            onDismiss = { showNotifications = false }
        )
    }
}

@Composable
private fun BangumiHeaderCard(
    state: BangumiProfileState,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = state.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.SurfaceElevated)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = state.nickname.ifBlank { state.userName },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.userName.isNotBlank()) {
                    Text(
                        text = "@${state.userName}",
                        style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                state.sign?.takeIf { it.isNotBlank() }?.let { sign ->
                    Text(
                        text = sign,
                        style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextSecondary),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onOpenProfile) {
                Icon(
                    imageVector = Icons.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.profile_platform_profile, "bgm.tv"), fontSize = 13.sp)
            }
            FilledTonalButton(onClick = onOpenNotifications) {
                Icon(
                    imageVector = Icons.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.notif_title), fontSize = 13.sp)
            }
        }

        Text(
            text = stringResource(R.string.inline_notifications_use_the_bgm_tv_582ff93),
            style = MaterialTheme.typography.labelSmall.copy(color = KitsugiColors.TextMuted)
        )
    }
}

@Composable
private fun BangumiSummaryCard(
    typeLabel: String,
    statusCounts: Map<Int, Int>,
    accentColor: Color
) {
    val total = statusCounts.values.sum()
    val statuses = listOf(
        BangumiApiClient.CollectionType.WISH,
        BangumiApiClient.CollectionType.DOING,
        BangumiApiClient.CollectionType.DONE,
        BangumiApiClient.CollectionType.ON_HOLD,
        BangumiApiClient.CollectionType.DROPPED
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_1_s_2_s_entries_4308608, typeLabel, total),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = KitsugiColors.TextPrimary
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            statuses.forEach { status ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${statusCounts[status] ?: 0}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    )
                    Text(
                        text = shortStatusLabel(status),
                        style = MaterialTheme.typography.labelSmall.copy(color = KitsugiColors.TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiChipRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { option ->
            BangumiChip(
                label = option.second,
                selected = option.first == selected,
                accentColor = accentColor,
                onClick = { onSelect(option.first) }
            )
        }
    }
}

@Composable
private fun BangumiChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else KitsugiColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else KitsugiColors.TextSecondary
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun BangumiCollectionRow(
    collection: BangumiUserCollection,
    accentColor: Color,
    titleLanguage: String,
    resolvedTitle: String?,
    onClick: () -> Unit
) {
    val subject = collection.subject
    val baseTitle = subject?.let { BangumiNameLocalizer.entity(it.name, it.nameCn).displayFor(titleLanguage) }
        ?.takeIf { it.isNotBlank() }
    // Bangumi koleksiyon ucu yalnızca özgün adı döndürür; Latin ad (romaji/İngilizce) öğrenildiyse
    // (bkz. BangumiLibraryTitleHydrator / keşfet / detay) seçilen başlık diline uyulur.
    val title = resolvedTitle
        ?: BangumiTitleCache.localizedTitleFor(collection.subjectId, baseTitle, titleLanguage)
        ?: baseTitle
        ?: "#${collection.subjectId}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = subject?.images?.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceElevated)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            progressLabel(collection)?.let { progress ->
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextSecondary),
                    maxLines = 1
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = collectionStatusLabel(collection.type),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    maxLines = 1
                )
                if (collection.rate > 0) {
                    Icon(
                        imageVector = Icons.Star,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${collection.rate}/10",
                        style = MaterialTheme.typography.labelSmall.copy(color = KitsugiColors.TextSecondary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = KitsugiColors.TextPrimary
        ),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun BangumiFavoriteRow(
    favorites: List<BangumiFavoriteItem>,
    titleLanguage: String,
    onClick: (BangumiFavoriteItem) -> Unit
) {
    val snapRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(
        state = snapRowState,
        flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(snapRowState),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(favorites, key = { it.id }) { favorite ->
            val displayName = BangumiNameLocalizer.entity(favorite.name).displayFor(titleLanguage)
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clickable { onClick(favorite) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsyncImage(
                    model = favorite.imageUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceElevated)
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = KitsugiColors.TextPrimary,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
