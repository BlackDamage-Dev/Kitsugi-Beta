package com.kitsugi.animelist.ui.screens.search

import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.utils.uniqueItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.CatalogOrganization
import com.kitsugi.animelist.utils.TaxonomyCatalog
import com.kitsugi.animelist.utils.TaxonomyOrgRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Searchable, lazy selector: 9,457 resource-backed Latin names, with invisible source aliases. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OrgFilterSheet(
    currentFilters: SearchFilters,
    onApplyFilters: (List<OrgFilter>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalKitsugiAccent.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val prefs = remember(context) {
        context.getSharedPreferences("search_taxonomy_orgs", android.content.Context.MODE_PRIVATE)
    }
    var recentKeys by remember(prefs) {
        mutableStateOf(prefs.getString("recent_keys", "").orEmpty().split('|').filter { it.isNotEmpty() })
    }
    var query by remember { mutableStateOf("") }
    var role by remember { mutableStateOf<TaxonomyOrgRole?>(null) }
    var selected by remember(currentFilters.organizations) { mutableStateOf(currentFilters.organizations) }
    val catalogResult by produceState<Result<TaxonomyCatalog>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { TaxonomyCatalog.load(context.applicationContext) }
        }
    }
    val catalog = catalogResult?.getOrNull()
    // The generator already sorts the names once; no 9,457-row sort on the UI thread.
    val sorted = catalog?.organizations.orEmpty()
    val matches = remember(sorted, query, role) {
        val needle = TaxonomyCatalog.fold(query)
        sorted.filter { organization ->
            (role == null || organization.role == role) &&
                (needle.isEmpty() || organization.matchesFolded(needle))
        }
    }
    val recents = remember(catalog, recentKeys) {
        recentKeys.mapNotNull { catalog?.organization(it) }.distinctBy { it.canonicalKey }
    }

    KitsugiSheetOrDialog(onDismiss = onDismiss, heightFraction = 0.85f, innerScrollState = listState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.search_org_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = KitsugiColors.TextPrimary)
                    Text(stringResource(R.string.search_org_selected_count, selected.size),
                        style = MaterialTheme.typography.labelMedium, color = KitsugiColors.TextMuted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_org_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = role == null, onClick = { role = null },
                    label = { Text(stringResource(R.string.filter_all)) })
                TaxonomyOrgRole.entries.forEach { option ->
                    FilterChip(selected = role == option, onClick = { role = option },
                        label = { Text(orgRoleLabel(option)) })
                }
            }
            Text(stringResource(R.string.search_org_apply_notice),
                color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (catalogResult == null) {
                    item(key = "loading") { CircularProgressIndicator(color = accent) }
                } else if (catalog == null) {
                    item(key = "error") {
                        Text(stringResource(R.string.search_catalog_load_error),
                            color = KitsugiColors.TextSecondary)
                    }
                } else {
                    if (query.isBlank() && recents.isNotEmpty()) {
                        item(key = "recent_title") {
                            Text(stringResource(R.string.search_org_recents),
                                color = KitsugiColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold)
                        }
                        uniqueItems(recents, key = { "recent_${it.canonicalKey}" }) { organization ->
                            OrganizationRow(organization, organization.asFilter() in selected) {
                                selected = selected.toggle(organization.asFilter())
                            }
                        }
                    }
                    item(key = "all_title") {
                        Text(stringResource(R.string.search_org_results, matches.size),
                            color = KitsugiColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold)
                    }
                    uniqueItems(matches, key = { it.canonicalKey }) { organization ->
                        OrganizationRow(organization, organization.asFilter() in selected) {
                            selected = selected.toggle(organization.asFilter())
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { selected = emptyList() }, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, KitsugiColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextSecondary)) {
                    Text(stringResource(R.string.search_taxonomy_clear_all))
                }
                KitsugiButton(onClick = {
                    onApplyFilters(selected)
                    recentKeys = (selected.asReversed().map { it.canonicalKey } + recentKeys)
                        .distinct().take(10)
                    prefs.edit().putString("recent_keys", recentKeys.joinToString("|")).apply()
                    onDismiss()
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.search_filter_apply))
                }
            }
        }
    }
}

private fun CatalogOrganization.asFilter(): OrgFilter = OrgFilter(role, canonicalKey)

private fun List<OrgFilter>.toggle(filter: OrgFilter): List<OrgFilter> =
    if (filter in this) this - filter else this + filter

@Composable
internal fun orgRoleLabel(role: TaxonomyOrgRole): String = stringResource(when (role) {
    TaxonomyOrgRole.STUDIO -> R.string.search_org_role_studio
    TaxonomyOrgRole.PRODUCER -> R.string.search_org_role_producer
    TaxonomyOrgRole.PUBLISHER -> R.string.search_org_role_publisher
    TaxonomyOrgRole.NETWORK -> R.string.search_org_role_network
    TaxonomyOrgRole.MAGAZINE -> R.string.search_org_role_magazine
})

@Composable
private fun OrganizationRow(organization: CatalogOrganization, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tvClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(organization.name, modifier = Modifier.weight(1f),
            color = KitsugiColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(orgRoleLabel(organization.role),
            color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = LocalKitsugiAccent.current)
    }
}
