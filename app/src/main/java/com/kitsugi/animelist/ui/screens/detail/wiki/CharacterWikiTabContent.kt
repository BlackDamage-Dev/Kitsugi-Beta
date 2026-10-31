package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.components.KitsugiDetailReveal
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.components.animateDetailContentSize

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import com.kitsugi.animelist.data.fandom.FandomRelationType
import com.kitsugi.animelist.data.fandom.FandomWikiLink
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomConfidence
import com.kitsugi.animelist.data.fandom.FandomInfobox
import com.kitsugi.animelist.data.fandom.FandomInfoboxField
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.KEY_DESCRIPTION
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.KEY_INFOBOX
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.infoboxFieldKey
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.sectionKey
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.SectionState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

private const val COLLAPSED_FIELD_COUNT = 8
private const val FANDOM_GALLERY_SOURCE = "Fandom"
private val PARAGRAPH_SPLIT = Regex("\\n\\s*\\n")

/**
 * "Hayır, yanlış" tıklandıktan sonra görünen geri alma kartı.
 *
 * Sekmenin en üstünde yer alır; kullanıcı hemen geri alabilir veya kartı kapatabilir.
 * Kart kapatılsa bile Ayarlar → Fandom Wiki → "Reddedilen eşleşmeleri geri getir"
 * yoluyla retler topluca geri getirilebilir.
 */
@Composable
internal fun WikiRejectedUndoCard(
    accentColor: Color,
    rejectedName: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.fandom_reject_undo_title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.fandom_reject_undo_desc, rejectedName),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = KitsugiColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.fandom_reject_undo_action),
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Karakter detayındaki "Wiki" sekmesi.
 *
 * Bilgi kutusu (spoiler alanları dokunana kadar gizli), kısa özet, açılır bölümler
 * (spoiler bölümleri uyarıyla, metin açıldıkça yüklenir), kaynak/lisans satırı ve
 * "Yanlışsa bildir" bağlantısı. Dikey düzende LazyColumn öğesi, yatay/TV düzeninde
 * kaydırılabilir Column içinde yer aldığı için kendi kaydırması yoktur.
 */
@Composable
internal fun CharacterWikiTabContent(
    page: FandomCharacterPage,
    sections: Map<Int, SectionState>,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    onConfirm: () -> Unit,
    onReportWrong: () -> Unit,
    modifier: Modifier = Modifier,
    onTranslate: ((String) -> Unit)? = null,
    onShowOriginal: ((String) -> Unit)? = null,
    onTranslateInfobox: () -> Unit = {},
    showNestedSections: Boolean = false,
    initiallyShowAllFields: Boolean = false,
    showConfirmation: Boolean = true,
    showFooter: Boolean = true,
    galleryCategory: GalleryCategory = GalleryCategory.CHARACTER,
    preferredTranslator: String = "DEFAULT",
    originalKeys: Set<String> = emptySet(),
    onToggleOriginal: (String) -> Unit = {},
    onOpenWikiPage: ((String?) -> Unit)? = null,
    /** Wiki kimliği taşıyan bağlantılar (bölüm altı + ilişkili sayfalar) için; başlığa
     *  ek olarak hedef wiki de korunur. Verilmezse [onOpenWikiPage]'e düşülür. */
    onOpenWikiLink: ((FandomWikiLink) -> Unit)? = null,
    relatedPages: List<com.kitsugi.animelist.data.fandom.FandomRelatedPage> = emptyList(),
    /** Birden çok kaynak (Fandom + Vikipedi) varken kaynak çiplerinden seçimi bildirir (indeks). */
    onSwitchSource: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val pageKey = page.wikiId + "|" + page.pageTitle
    val expanded = remember(pageKey) { mutableStateMapOf<Int, Boolean>() }
    val revealed = remember(pageKey) { mutableStateMapOf<String, Boolean>() }
    var showAllFields by remember(pageKey, initiallyShowAllFields) { mutableStateOf(initiallyShowAllFields) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (page.alternatives.isNotEmpty()) {
            WikiSourceChips(page = page, accentColor = accentColor, onSwitchSource = onSwitchSource)
        }
        if (showConfirmation && page.needsConfirmation) {
            WikiConfirmCard(accentColor = accentColor, onConfirm = onConfirm, onReject = onReportWrong)
        }

        WikiInfoboxCard(
            page = page,
            infobox = page.infobox,
            spoilerGuard = spoilerGuard,
            revealed = revealed,
            showAll = showAllFields,
            onToggleShowAll = { showAllFields = !showAllFields },
            accentColor = accentColor,
            onOpenGallery = onOpenGallery,
            galleryCategory = galleryCategory,
            translations = translations,
            infoboxTranslating = KEY_INFOBOX in translatingKeys,
            preferredTranslator = preferredTranslator,
            infoboxShowingOriginal = KEY_INFOBOX in originalKeys,
            onToggleInfoboxOriginal = { onToggleOriginal(KEY_INFOBOX) }
        )

        if (page.relatedEntities.isNotEmpty()) {
            WikiRelatedEntitiesCard(
                entities = page.relatedEntities,
                accentColor = accentColor,
                onOpenWikiLink = onOpenWikiLink,
                onOpenWikiPage = onOpenWikiPage
            )
        }

        page.description?.let { description ->
            val descriptionShowingOriginal = KEY_DESCRIPTION in originalKeys
            val descriptionHasTranslation = translations.containsKey(KEY_DESCRIPTION)
            val shownDescription = if (descriptionShowingOriginal) description else (translations[KEY_DESCRIPTION] ?: description)
            WikiCard(animateHeight = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.fandom_summary_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // Detay sayfasındaki Açıklama kartının çeviri düğmesiyle birebir aynı:
                    // her zaman görünür, dokununca orijinal metni harici çeviri uygulamasında açar,
                    // uygulama içi çeviri sürerken nabız + dönen halka animasyonu gösterir.
                    WikiTranslateButton(
                        text = description,
                        preferredTranslator = preferredTranslator,
                        accentColor = accentColor,
                        isLoading = KEY_DESCRIPTION in translatingKeys,
                        hasTranslation = descriptionHasTranslation,
                        showingOriginal = descriptionShowingOriginal,
                        onToggleOriginal = { onToggleOriginal(KEY_DESCRIPTION) }
                    )
                    CopyIconButton(
                        contentDescription = stringResource(R.string.fandom_copy_summary),
                        onClick = { copyWikiText(context, shownDescription) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                WikiCollapsibleText(text = shownDescription, accentColor = accentColor) {
                    SelectionContainer {
                        Text(
                            text = shownDescription,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        val topLevel = page.sections.minOfOrNull { it.level }
        val visibleSections = if (showNestedSections) page.sections else page.sections.filter { it.level == topLevel }
        if (visibleSections.isNotEmpty()) {
            WikiCard {
                Text(
                    text = stringResource(R.string.fandom_contents_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                visibleSections.forEach { section ->
                    val isExpanded = expanded[section.index] == true
                    val revealKey = "section:${section.index}"
                    val key = sectionKey(section.index)
                    val sectionShowingOriginal = key in originalKeys
                    WikiSectionRow(
                        section = section,
                        depth = if (showNestedSections) (section.level - (topLevel ?: section.level)).coerceAtLeast(0) else 0,
                        expanded = isExpanded,
                        gated = spoilerGuard && section.spoiler && revealed[revealKey] != true,
                        state = sections[section.index],
                        translated = translations[key],
                        translating = key in translatingKeys,
                        showingOriginal = sectionShowingOriginal,
                        onToggleOriginal = { onToggleOriginal(key) },
                        accentColor = accentColor,
                        onToggle = { expanded[section.index] = !isExpanded },
                        onReveal = { revealed[revealKey] = true },
                        onLoad = { onLoadSection(section.index) },
                        onCopy = { text -> copyWikiText(context, text) },
                        preferredTranslator = preferredTranslator,
                        onOpenGallery = onOpenGallery,
                        gallerySource = FandomUrls.displaySourceName(page.wikiId),
                        sourceWikiId = page.wikiId,
                        onOpenLink = { linkTitle ->
                            onOpenWikiPage?.invoke(linkTitle) ?: openExternal(context, com.kitsugi.animelist.data.fandom.FandomUrls.pageUrl(page.wikiId, linkTitle))
                        },
                        translations = translations,
                        onOpenWikiLink = onOpenWikiLink
                    )
                }
            }
        }

        if (relatedPages.isNotEmpty()) {
            CharacterRelatedStrip(
                pages = relatedPages,
                wikiId = page.wikiId,
                accentColor = accentColor,
                onOpenPage = { p ->
                    // Uygulama içi okuyucu bağlı değilse ölü düğme bırakma:
                    // son çare olarak tarayıcı (normal akışta hep bağlanır).
                    onOpenWikiPage?.invoke(p)
                        ?: openExternal(context, com.kitsugi.animelist.data.fandom.FandomUrls.pageUrl(page.wikiId, p))
                }
            )
        }

        if (showFooter) WikiFooter(
            page = page,
            accentColor = accentColor,
            onReportWrong = onReportWrong,
            onOpenWikiPage = onOpenWikiPage
        )
    }
}

@Composable
private fun CharacterRelatedStrip(
    pages: List<com.kitsugi.animelist.data.fandom.FandomRelatedPage>,
    wikiId: String,
    accentColor: Color,
    onOpenPage: (String) -> Unit
) {
    if (pages.isEmpty()) return
    // Başlık kaynağa göre kendiliğinden güncellenir: Vikipedi/Vikipedia kaynağında
    // "İlgili maddeler", Fandom kaynağında "İlgili wiki sayfaları".
    val wikipedia = FandomUrls.isWikipedia(wikiId)
    WikiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (wikipedia) R.string.fandom_related_pages_title_wikipedia
                    else R.string.fandom_related_pages_title
                ),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.fandom_related_pages_count, pages.size),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        WikiPreviewStripRow(
            items = pages,
            key = { it.title }
        ) { relatedPage ->
            WikiPagePreviewCard(
                page = relatedPage,
                wikiId = wikiId,
                accentColor = accentColor,
                onClick = { onOpenPage(relatedPage.title) }
            )
        }
    }
}

/** Otomatik (kesinleşmemiş) eşleşmede bir kerelik onay kartı. */
@Composable
internal fun WikiConfirmCard(accentColor: Color, onConfirm: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.fandom_confirm_title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.fandom_confirm_desc),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReject) {
                Text(
                    text = stringResource(R.string.fandom_confirm_no),
                    color = KitsugiColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.fandom_confirm_yes),
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WikiCard(
    animateHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (animateHeight) Modifier.animateDetailContentSize() else Modifier)
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun WikiInfoboxCard(
    page: FandomCharacterPage,
    infobox: FandomInfobox?,
    spoilerGuard: Boolean,
    revealed: MutableMap<String, Boolean>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    accentColor: Color,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    galleryCategory: GalleryCategory = GalleryCategory.CHARACTER,
    translations: Map<String, String> = emptyMap(),
    infoboxTranslating: Boolean = false,
    preferredTranslator: String = "DEFAULT",
    infoboxShowingOriginal: Boolean = false,
    onToggleInfoboxOriginal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val images = infobox?.images.orEmpty()
    val gallerySource = FandomUrls.displaySourceName(page.wikiId)
    val galleryItems = remember(images, galleryCategory, gallerySource) {
        images.map { image ->
            GalleryItem(
                url = image.url,
                source = gallerySource,
                category = galleryCategory,
                description = image.caption
            )
        }
    }
    val fields = remember(infobox) {
        infobox?.groups.orEmpty().flatMap { group -> group.fields.map { field -> group.header to field } }
    }
    val visibleCount = if (showAll) fields.size else minOf(fields.size, COLLAPSED_FIELD_COUNT)

    WikiCard {
        Row(verticalAlignment = Alignment.Top) {
            images.firstOrNull()?.let { image ->
                FandomWikiImage(
                    url = image.url,
                    width = 360,
                    contentDescription = infobox?.title ?: page.pageTitle,
                    accentColor = accentColor,
                    modifier = Modifier
                        .width(92.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(14.dp)) { onOpenGallery(galleryItems, 0) }
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = infobox?.title ?: page.pageTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = page.siteName ?: page.wikiId,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (images.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        images.forEachIndexed { index, image ->
                            if (index == 0) return@forEachIndexed
                            FandomWikiImage(
                                url = image.url,
                                width = 160,
                                contentDescription = image.caption,
                                accentColor = accentColor,
                                iconSize = 16.dp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(KitsugiColors.SurfaceStrong)
                                    .tvClickable(shape = RoundedCornerShape(10.dp)) { onOpenGallery(galleryItems, index) }
                            )
                        }
                    }
                }
            }
        }

        if (fields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            // Bilgi kutusu araç satırı: toplu çeviri + tümünü kopyala (detay sayfalarındaki açıklama kartıyla aynı düzen).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fandom_infobox_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val hiddenIndices = if (spoilerGuard) {
                    fields.indices.filter { fields[it].second.spoiler && revealed["field:$it"] != true }.toSet()
                } else {
                    emptySet()
                }
                // Detay sayfasındaki Açıklama kartıyla aynı çeviri düğmesi: ikon her zaman
                // durur, dokununca bilgi kutusunun orijinal metni harici uygulamada açılır.
                val infoboxHasTranslation = fields.indices.any { translations.containsKey(infoboxFieldKey(it)) }
                WikiTranslateButton(
                    text = buildInfoboxSourceText(fields = fields, hiddenIndices = hiddenIndices),
                    preferredTranslator = preferredTranslator,
                    accentColor = accentColor,
                    isLoading = infoboxTranslating,
                    hasTranslation = infoboxHasTranslation,
                    showingOriginal = infoboxShowingOriginal,
                    onToggleOriginal = onToggleInfoboxOriginal
                )
                val copyAllText = buildInfoboxCopyText(
                    fields = fields,
                    translations = translations,
                    hiddenIndices = hiddenIndices,
                    showingOriginal = infoboxShowingOriginal
                )
                CopyIconButton(
                    contentDescription = stringResource(R.string.fandom_copy_infobox),
                    onClick = { copyWikiText(context, copyAllText) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            var lastHeader: String? = null
            fields.take(visibleCount).forEachIndexed { index, (header, field) ->
                if (header != null && header != lastHeader) {
                    Text(
                        text = localizedInfoboxHeader(header),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp, bottom = 6.dp)
                    )
                }
                lastHeader = header
                val revealKey = "field:$index"
                val hidden = spoilerGuard && field.spoiler && revealed[revealKey] != true
                val displayValue = if (infoboxShowingOriginal) {
                    localizedInfoboxValue(field.value)
                } else {
                    translations[infoboxFieldKey(index)] ?: localizedInfoboxValue(field.value)
                }
                WikiFieldRow(
                    label = localizedInfoboxLabel(field),
                    value = displayValue,
                    hidden = hidden,
                    onReveal = { revealed[revealKey] = true },
                    onCopy = if (hidden) null else ({ copyWikiText(context, displayValue) })
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (fields.size > COLLAPSED_FIELD_COUNT) {
                TextButton(onClick = onToggleShowAll) {
                    Text(
                        text = stringResource(if (showAll) R.string.fandom_show_less else R.string.fandom_show_all_fields),
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WikiFieldRow(
    label: String,
    value: String,
    hidden: Boolean,
    onReveal: () -> Unit,
    onCopy: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(0.38f)
                .padding(end = 8.dp)
        )
        if (hidden) {
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong)
                    .tvClickable(shape = RoundedCornerShape(8.dp)) { onReveal() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.fandom_spoiler_hidden),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            Row(modifier = Modifier.weight(0.62f), verticalAlignment = Alignment.Top) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = value,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onCopy != null) {
                    // Minik kopyala düğmesi: değerin yanında, satır yüksekliğini büyütmeden.
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ContentCopy,
                            contentDescription = stringResource(R.string.fandom_copy_value),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 36 dp'lik kopyala düğmesi (açıklama kartlarındaki ile aynı ölçü). */
@Composable
internal fun CopyIconButton(contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.ContentCopy,
            contentDescription = contentDescription,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Detay sayfalarındaki açıklama kartıyla aynı davranış: panoya yaz + kısa "kopyalandı" bildirimi. */
internal fun copyWikiText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("fandom_wiki", text))
    android.widget.Toast.makeText(context, context.getString(R.string.toast_copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * Bilgi kutusunun **orijinal** (çevrilmemiş) metni: harici çeviri uygulamasına gönderilir.
 * Etiket ve değerler wiki'nin kendi dilinde kalır; gizli (spoiler) alanların değeri gönderilmez.
 */
private fun buildInfoboxSourceText(
    fields: List<Pair<String?, FandomInfoboxField>>,
    hiddenIndices: Set<Int>
): String {
    val lines = ArrayList<String>(fields.size + 4)
    var lastHeader: String? = null
    fields.forEachIndexed { index, (header, field) ->
        if (header != null && header != lastHeader) {
            if (lines.isNotEmpty()) lines += ""
            lines += header
        }
        lastHeader = header
        val value = if (index in hiddenIndices) "[…]" else field.value
        lines += "${field.label}: $value"
    }
    return lines.joinToString("\n")
}

/** Bilgi kutusunu "Etiket: değer" satırları hâlinde panoya kopyalanacak metne çevirir. */
@Composable
private fun buildInfoboxCopyText(
    fields: List<Pair<String?, FandomInfoboxField>>,
    translations: Map<String, String>,
    hiddenIndices: Set<Int>,
    showingOriginal: Boolean = false
): String {
    val hiddenLabel = stringResource(R.string.fandom_spoiler_badge)
    val lines = ArrayList<String>(fields.size + 4)
    var lastHeader: String? = null
    fields.forEachIndexed { index, (header, field) ->
        if (header != null && header != lastHeader) {
            if (lines.isNotEmpty()) lines += ""
            lines += localizedInfoboxHeader(header)
        }
        lastHeader = header
        val value = when {
            index in hiddenIndices -> "[$hiddenLabel]"
            showingOriginal -> localizedInfoboxValue(field.value)
            else -> translations[infoboxFieldKey(index)] ?: localizedInfoboxValue(field.value)
        }
        lines += "${localizedInfoboxLabel(field)}: $value"
    }
    return lines.joinToString("\n")
}

@Composable
private fun localizedInfoboxLabel(field: FandomInfoboxField): String {
    val resource = FandomInfoboxLabels.labelResId(field.label, field.source)
    return if (resource != null) stringResource(resource) else field.label
}

@Composable
private fun localizedInfoboxHeader(header: String): String {
    val resource = FandomInfoboxLabels.headerResId(header)
    return if (resource != null) stringResource(resource) else header
}

@Composable
private fun localizedInfoboxValue(value: String): String {
    val resource = FandomInfoboxLabels.valueResId(value)
    return if (resource != null) stringResource(resource) else value
}

@Composable
private fun WikiSectionRow(
    section: FandomSectionRef,
    depth: Int = 0,
    expanded: Boolean,
    gated: Boolean,
    state: SectionState?,
    translated: String?,
    translating: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onReveal: () -> Unit,
    onLoad: () -> Unit,
    onCopy: (String) -> Unit = {},
    preferredTranslator: String = "DEFAULT",
    showingOriginal: Boolean = false,
    onToggleOriginal: (() -> Unit)? = null,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit = { _, _ -> },
    onOpenLink: (String) -> Unit = {},
    onOpenWikiLink: ((FandomWikiLink) -> Unit)? = null,
    /** Galeri penceresindeki kaynak adı ("Fandom" / "Vikipedi" / "Wikipedia"). */
    gallerySource: String = FANDOM_GALLERY_SOURCE,
    /** Bölüm bağlantı kartlarındaki kaynak rozeti için sayfanın wiki kimliği. */
    sourceWikiId: String = "",
    /** Bölümün blok blok çevirileri (yapı koruyan çeviri). */
    translations: Map<String, String> = emptyMap()
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .tvClickable(shape = RoundedCornerShape(12.dp)) { onToggle() }
                .padding(start = (4 + depth * 14).dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KitsugiChevronIcon(
                expanded = expanded,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = localizedSectionTitle(section.title),
                color = KitsugiColors.TextPrimary,
                style = if (depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (section.spoiler) {
                Text(
                    text = stringResource(R.string.fandom_spoiler_badge),
                    color = KitsugiColors.AccentOrange,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        KitsugiDetailReveal(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp + (depth * 14).dp, end = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gated) {
                    SpoilerGate(accentColor = accentColor, onReveal = onReveal)
                } else {
                    LaunchedEffect(section.index) {
                        if (state == null) onLoad()
                    }
                    when (state) {
                        null, is SectionState.Loading -> LoadingLine(accentColor)
                        is SectionState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.fandom_section_error),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onLoad) {
                                Text(
                                    text = stringResource(R.string.action_retry_label),
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is SectionState.Loaded -> {
                            if (state.content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.fandom_section_empty),
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                // Bölüm bazlı alt harita: başka bölümlerin çevirisi akarken
                                // bu bölümün çizicisi (withTranslatedBlocks + buton taraması)
                                // yeniden hesaplanmaz; yalnızca bu bölüm değiştiğinde çalışır.
                                val sectionBaseKey = sectionKey(section.index)
                                val sectionTranslations = remember(translations, sectionBaseKey) {
                                    translations.wikiTranslationsFor(sectionBaseKey)
                                }
                                val sourceText = remember(state.content) { state.content.wikiTranslationSourceText() }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Detay sayfasındaki açıklama düğmesiyle aynı: her zaman ikon,
                                    // dokununca bölümün orijinal metni harici uygulamada açılır.
                                    WikiTranslateButton(
                                        text = sourceText,
                                        preferredTranslator = preferredTranslator,
                                        accentColor = accentColor,
                                        isLoading = translating,
                                        hasTranslation = sectionTranslations.hasWikiTranslation(sectionBaseKey),
                                        showingOriginal = showingOriginal,
                                        onToggleOriginal = onToggleOriginal
                                    )
                                    CopyIconButton(
                                        contentDescription = stringResource(R.string.fandom_copy_section),
                                        onClick = {
                                            // Kopyalanan metin de yapıyı korur: madde işaretleri,
                                            // kart sırası ve bağlantı adları orijinaldeki gibidir.
                                            onCopy(
                                                state.content.copyTextFor(
                                                    baseKey = sectionBaseKey,
                                                    translations = sectionTranslations,
                                                    showOriginal = showingOriginal || translated == null,
                                                    flatFallback = translated
                                                )
                                            )
                                        }
                                    )
                                }
                                SectionBody(
                                    content = state.content,
                                    translated = if (showingOriginal) null else translated,
                                    onOpenGallery = onOpenGallery,
                                    accentColor = accentColor,
                                    sourceWikiId = sourceWikiId,
                                    onOpenLink = onOpenLink,
                                    onOpenWikiLink = onOpenWikiLink,
                                    gallerySource = gallerySource,
                                    // Never feed translated blocks to the renderer while
                                    // Original is selected.  The old call kept the
                                    // structured translation map alive, so the toggle changed
                                    // only the button label while the body stayed translated.
                                    baseKey = if (showingOriginal) "" else sectionBaseKey,
                                    translations = if (showingOriginal) emptyMap() else sectionTranslations
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBody(
    content: FandomSectionContent,
    translated: String?,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    accentColor: Color,
    sourceWikiId: String = "",
    onOpenLink: (String) -> Unit = {},
    onOpenWikiLink: ((FandomWikiLink) -> Unit)? = null,
    gallerySource: String = FANDOM_GALLERY_SOURCE,
    /** Bölümün blok blok çevirileri (yapı koruyan çeviri). */
    baseKey: String = "",
    translations: Map<String, String> = emptyMap()
) {
    val context = LocalContext.current
    val galleryItems = remember(content, gallerySource) {
        content.blocks.mapNotNull { it.image }
            .distinctBy { it.url }
            .map { image ->
                GalleryItem(
                    url = image.url,
                    source = gallerySource,
                    category = GalleryCategory.CHARACTER,
                    description = image.caption
                )
            }
    }
    val imageBlocks = content.blocks.filter { it.kind == FandomBlockKind.IMAGE }
    // YAPI KORUMASI: Çevrilmiş içerik de orijinaliyle AYNI çiziciden geçer; görseller kendi
    // konumunda kalır, madde/kart/başlık düzeni ve bağlantı etiketleri değişmez.
    val translatedContent = remember(content, translations, baseKey) {
        if (baseKey.isEmpty()) null else content.withTranslatedBlocks(baseKey, translations)
    }
    val flatTranslation = translated?.takeIf { content.blocks.isEmpty() }?.takeUnless {
        it.isBlank() || WikiStructuredTranslationKeys.isCompleteMarker(it)
    }
    val openGalleryForImage: (com.kitsugi.animelist.data.fandom.FandomImage) -> Unit = { image ->
        val index = galleryItems.indexOfFirst { it.url == image.url }.coerceAtLeast(0)
        if (galleryItems.isNotEmpty()) onOpenGallery(galleryItems, index)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    translatedContent != null -> translatedContent.blocks.forEach { block ->
                        WikiBlock(block, openGalleryForImage)
                    }
                    flatTranslation != null -> {
                        flatTranslation.split(PARAGRAPH_SPLIT)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { paragraph ->
                                Text(
                                    text = paragraph,
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        imageBlocks.forEach { block ->
                            WikiImageBlock(block) {
                                val image = block.image ?: return@WikiImageBlock
                                openGalleryForImage(image)
                            }
                        }
                    }
                    else -> content.blocks.forEach { block ->
                        WikiBlock(block, openGalleryForImage)
                    }
                }
            }
        }
        if (content.linkedEntities.isNotEmpty()) {
            // Bölüm tablolarından çıkan kadro/ekip/karakter bağlantıları da aynı
            // birleşik önizleme kartıyla gösterilir: hedef wiki kimliği korunarak
            // uygulama içi okuyucuda açılır, tarayıcıya düşmez.
            WikiPreviewStripRow(
                items = content.linkedEntities,
                key = { link -> "${link.wikiId}:${link.pageTitle}:${link.role}" }
            ) { link ->
                WikiPagePreviewCard(
                    entity = link,
                    accentColor = accentColor,
                    onClick = {
                        if (onOpenWikiLink != null) onOpenWikiLink(link)
                        else onOpenLink(link.pageTitle)
                    }
                )
            }
        } else if (content.links.isNotEmpty()) {
            WikiPreviewStripRow(
                items = content.links,
                key = { it }
            ) { linkTitle ->
                WikiPagePreviewCard(
                    title = linkTitle,
                    description = null,
                    imageUrl = null,
                    sourceLabel = FandomUrls.displaySourceName(sourceWikiId),
                    accentColor = accentColor,
                    onClick = { onOpenLink(linkTitle) }
                )
            }
        }
    }
}

@Composable
private fun WikiBlock(
    block: FandomTextBlock,
    onOpenImage: (com.kitsugi.animelist.data.fandom.FandomImage) -> Unit = {}
) {
    when (block.kind) {
        FandomBlockKind.HEADING -> Text(
            text = localizedSectionTitle(block.text),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FandomBlockKind.PARAGRAPH -> Text(
            text = block.text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        FandomBlockKind.BULLET -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "•",
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = block.text,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        FandomBlockKind.QUOTE -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = block.text,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
        FandomBlockKind.IMAGE -> block.image?.let { image ->
            WikiImageBlock(block) { onOpenImage(image) }
        }
        FandomBlockKind.CARD -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.image?.let { image ->
                WikiImageBlock(block) { onOpenImage(image) }
            }
            if (block.text.isNotBlank()) {
                Text(
                    text = block.text,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            block.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        FandomBlockKind.LINK_GROUP -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (block.text.isNotBlank()) {
                Text(
                    text = block.text,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            val labels = block.links.map { it.label }.filter { it.isNotBlank() }
            if (labels.isNotEmpty()) {
                Text(
                    text = labels.joinToString(" · "),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun WikiImageBlock(block: FandomTextBlock, onOpenImage: () -> Unit) {
    val image = block.image ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FandomWikiImage(
            url = image.url,
            width = 720,
            contentDescription = image.caption,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.SurfaceStrong)
                .tvClickable(shape = RoundedCornerShape(12.dp)) { onOpenImage() }
        )
        image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SpoilerGate(accentColor: Color, onReveal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceStrong)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Warning,
                contentDescription = null,
                tint = KitsugiColors.AccentOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.fandom_spoiler_section_warning),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onReveal) {
            Text(
                text = stringResource(R.string.fandom_spoiler_reveal),
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingLine(accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = accentColor,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.fandom_section_loading),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Birden çok kaynak (Fandom + Vikipedi) varken kaynak çipleri. Etkin kaynak her zaman ilk sıradadır;
 * diğerine dokununca [onSwitchSource] ile geçilir (indeks 1..n).
 */
@Composable
private fun WikiSourceChips(
    page: FandomCharacterPage,
    accentColor: Color,
    onSwitchSource: (Int) -> Unit
) {
    val sources = listOf(page) + page.alternatives
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sources.forEachIndexed { index, source ->
            val selected = index == 0
            val isWikipedia = FandomUrls.isWikipedia(source.wikiId)
            val label = when {
                isWikipedia && source.wikiId == "wikipedia.tr" -> stringResource(R.string.wiki_source_wikipedia_tr)
                isWikipedia -> stringResource(R.string.wiki_source_wikipedia)
                else -> source.siteName ?: stringResource(R.string.wiki_source_fandom)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) accentColor.copy(alpha = 0.22f)
                        else KitsugiColors.Surface.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .tvClickable(enabled = !selected, shape = RoundedCornerShape(8.dp)) { onSwitchSource(index) }
            ) {
                KitsugiEmojiLabel(
                    label = label,
                    color = if (selected) accentColor else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    iconSize = 13.dp,
                    icon = if (isWikipedia) Icons.BookOpen else Icons.Globe
                )
            }
        }
    }
}

@Composable
private fun WikiFooter(
    page: FandomCharacterPage,
    accentColor: Color,
    onReportWrong: () -> Unit,
    onOpenWikiPage: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val wikipediaPage = FandomUrls.isWikipedia(page.wikiId)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (wikipediaPage) R.string.fandom_source_label_wikipedia
                    else R.string.fandom_source_label,
                    page.siteName ?: page.wikiId
                ),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onOpenWikiPage != null) {
                TextButton(onClick = { onOpenWikiPage(page.pageTitle) }) {
                    Icon(
                        imageVector = Icons.MenuBook,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (wikipediaPage) R.string.fandom_open_page_wikipedia_page
                            else R.string.fandom_open_page
                        ),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(onClick = { openExternal(context, page.pageUrl) }) {
                    Icon(
                        imageVector = Icons.OpenInNew,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.fandom_open_in_browser),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = stringResource(
                if (wikipediaPage) R.string.fandom_license_note_wikipedia
                else R.string.fandom_license_note
            ),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Kullanıcı onayladıysa (CERTAIN) etiket gösterilmez; "Yanlışsa bildir" her zaman açıktır.
            val automatic = page.confidence == FandomConfidence.PROBABLE
            if (automatic) {
                Text(
                    text = stringResource(R.string.fandom_auto_match),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onReportWrong) {
                Text(
                    text = stringResource(R.string.fandom_report_wrong),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** İngilizce wiki bölüm başlıklarını arayüz diline çevirir; bilinmeyenler aynen kalır. */
@Composable
private fun localizedSectionTitle(raw: String): String {
    val resource = FandomSectionTitles.resId(raw)
    return if (resource != null) stringResource(resource) else raw
}

private fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.fandom_open_failed), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun WikiRelatedEntitiesCard(
    entities: List<FandomWikiLink>,
    accentColor: Color,
    onOpenWikiLink: ((FandomWikiLink) -> Unit)? = null,
    onOpenWikiPage: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    // Sayfa açma: önce wiki kimlikli uygulama içi okuyucu, sonra başlıklı
    // okuyucu; ikisi de yoksa (bağımsız yeniden kullanım) tarayıcı son çaredir.
    val openEntityPage: (FandomWikiLink) -> Unit = { entity ->
        when {
            onOpenWikiLink != null -> onOpenWikiLink(entity)
            onOpenWikiPage != null -> onOpenWikiPage(entity.pageTitle)
            else -> openExternal(context, entity.pageUrl)
        }
    }
    val orderedGroups = remember(entities) {
        val priority = listOf(
            FandomRelationType.CHARACTER,
            FandomRelationType.CAST,
            FandomRelationType.VOICE_ACTOR,
            FandomRelationType.STAFF,
            FandomRelationType.CREW,
            FandomRelationType.MUSIC,
            FandomRelationType.RELATED
        )
        val grouped = entities.groupBy { it.role }
        priority.mapNotNull { role ->
            val list = grouped[role] ?: return@mapNotNull null
            role to list
        }
    }

    WikiCard {
        Text(
            text = stringResource(R.string.fandom_related_title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        orderedGroups.forEach { (role, groupEntities) ->
            Text(
                text = stringResource(relatedTitleRes(role)),
                color = accentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = stringResource(R.string.fandom_related_article_count, groupEntities.size),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            WikiPreviewStripRow(
                items = groupEntities,
                key = { entity -> "${entity.wikiId}:${entity.pageTitle}:${entity.role}:${entity.language}" }
            ) { entity ->
                WikiPagePreviewCard(
                    entity = entity,
                    accentColor = accentColor,
                    badgeLabel = stringResource(relatedTitleRes(role)),
                    onClick = { openEntityPage(entity) }
                )
            }
        }
    }
}

private fun relatedTitleRes(role: FandomRelationType): Int = when (role) {
    FandomRelationType.CHARACTER -> R.string.fandom_section_characters
    FandomRelationType.CAST -> R.string.fandom_related_cast
    FandomRelationType.VOICE_ACTOR -> R.string.fandom_section_voice_actors
    FandomRelationType.STAFF, FandomRelationType.CREW -> R.string.fandom_related_staff
    FandomRelationType.MUSIC -> R.string.fandom_section_music
    FandomRelationType.RELATED -> R.string.fandom_related_other
}
