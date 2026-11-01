package com.kitsugi.animelist.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.delay

/**
 * ORTAK FİLTRE ÇATISI — Kurumlar (stüdyo / yapımcı / yapım şirketi) sheet'i.
 *
 * Tasarım kararı:
 *  - Seçim ANINDA uygulanır (tür/etiket sheet'i ile aynı dil). "Sonuçları
 *    Göster" düğmesi aramayı yeniler.
 *  - Kurum kimlikleri kaynağa bağlıdır: MAL yapımcı ID'si ile TMDB şirket
 *    ID'si aynı sayı olsa da farklı kurumdur. Bu yüzden liste
 *    [SearchCommonFiltersBridge.institutionKindsFor] üzerinden motor duyarlı
 *    çizilir ve her kurum kendi kaynağıyla birlikte saklanır.
 *  - Kaynağın arama ucu kurum filtresi kabul etmiyorsa (AniList, Shikimori,
 *    Kitsu, Simkl, Bangumi) yanlış kimlik yazıp aramayı bozmak yerine bunun
 *    desteklenmediği açıkça söylenir; önceki seçimler korunur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstitutionFilterSheet(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit
) {
    val engine = uiState.selectedEngine
    val accentColor = LocalKitsugiAccent.current
    val kinds = SearchCommonFiltersBridge.institutionKindsFor(engine)
    val searchableKinds = kinds.filter { SearchCommonFiltersBridge.canSearchInstitutionKind(it) }
    val results by viewModel.institutionSearchResults.collectAsState()

    var selectedKind by remember(engine) {
        mutableStateOf<CommonInstitutionKind?>(searchableKinds.firstOrNull())
    }
    var query by remember(engine) { mutableStateOf("") }

    LaunchedEffect(selectedKind, query) {
        val kind = selectedKind
        if (kind == null || query.isBlank()) {
            viewModel.clearInstitutionSearch()
            return@LaunchedEffect
        }
        delay(300)
        viewModel.searchInstitutions(kind, query)
    }

    val visibleInstitutions = if (engine == SearchSourceEngine.ALL) {
        uiState.institutions
    } else {
        uiState.institutions.filter { it.engine == engine }
    }
    val carriedInstitutions = if (engine == SearchSourceEngine.ALL) {
        emptyList()
    } else {
        uiState.institutions.filter { it.engine != engine }
    }

    KitsugiSheetOrDialog(onDismiss = onDismiss, heightFraction = 0.85f) {
        // Sheet içeriği kaydırılabilir: küçük ekranlarda kurum listesi + seçili
        // çipler + alt eylemler birlikte 85%'lik yüksekliğe sığmayabilir.
        // Liste bilinçli olarak LazyColumn değil; kaydırılabilir bir Column içinde
        // iç içe kaydırma çakışması yaratmamak için normal Column kullanılır.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            // ── Başlık ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🏢 ${stringResource(R.string.search_institutions)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.search_institution_apply_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Seçili kurumlar ─────────────────────────────────────────────
            if (visibleInstitutions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_institution_selected),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleInstitutions.forEach { institution ->
                        SelectedInstitutionChip(
                            institution = institution,
                            accentColor = accentColor,
                            onRemove = { viewModel.removeInstitution(institution) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Desteklenmeyen kaynak bilgisi ───────────────────────────────
            if (searchableKinds.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.7f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_institution_unsupported_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KitsugiColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.search_institution_unsupported_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextMuted
                    )
                }
            } else {
                // ── Kurum türü (yapımcı / şirket) ───────────────────────────
                if (searchableKinds.size > 1) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchableKinds.forEach { kind ->
                            val selected = kind == selectedKind
                            val shape = RoundedCornerShape(12.dp)
                            val label = when (kind) {
                                CommonInstitutionKind.PRODUCER ->
                                    stringResource(R.string.search_institution_kind_producer)
                                CommonInstitutionKind.COMPANY ->
                                    stringResource(R.string.search_institution_kind_company)
                                else -> kind.name
                            }
                            Box(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(
                                        if (selected) accentColor.copy(alpha = 0.18f)
                                        else KitsugiColors.SurfaceElevated.copy(alpha = 0.7f)
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) accentColor else Color.Transparent,
                                        shape = shape
                                    )
                                    .tvClickable(shape = shape) {
                                        selectedKind = kind
                                        query = ""
                                        viewModel.clearInstitutionSearch()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) accentColor else KitsugiColors.TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // ── Arama alanı ─────────────────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_institution_hint),
                            color = KitsugiColors.TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KitsugiColors.Surface,
                        unfocusedContainerColor = KitsugiColors.Surface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Sonuçlar ────────────────────────────────────────────────
                if (query.isBlank()) {
                    Text(
                        text = stringResource(R.string.search_institution_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextMuted
                    )
                } else if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sonuc_bulunamadi),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        results.take(20).forEach { result ->
                            InstitutionResultRow(
                                result = result,
                                accentColor = accentColor,
                                onClick = {
                                    val kind = selectedKind
                                    if (kind != null) {
                                        viewModel.selectInstitution(
                                            engine = engine,
                                            kind = kind,
                                            id = result.malId,
                                            name = result.title
                                        )
                                        query = ""
                                        viewModel.clearInstitutionSearch()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Korunan (diğer kaynak) seçimler ─────────────────────────────
            if (carriedInstitutions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.search_institution_carried),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    carriedInstitutions.forEach { institution ->
                        SelectedInstitutionChip(
                            institution = institution,
                            accentColor = KitsugiColors.TextMuted,
                            onRemove = { viewModel.removeInstitution(institution) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Alt eylemler ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.institutions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearInstitutions() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Text(
                            text = stringResource(R.string.search_institution_clear),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = stringResource(R.string.kapat),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedInstitutionChip(
    institution: CommonInstitution,
    accentColor: Color,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.55f), shape)
            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${institution.engine.emoji} ${institution.name}",
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onRemove)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.temizle),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun InstitutionResultRow(
    result: JikanSearchResult,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.55f))
            // TV'de kumanda ile seçilebilmesi için odaklanabilir tıklama kullanılır;
            // düz `clickable` yalnızca dokunmayla çalışıyordu.
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.subtitle.isNotBlank()) {
                Text(
                    text = result.subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "+",
            color = accentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
