package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background

/**
 * Shared category navigation for every provider. The chips are generated from the exact same
 * [ExploreSourceSection] list that draws the shelves below, so a shortcut can never advertise a
 * different category (or point at the wrong provider field) than its corresponding shelf.
 */
@Composable
fun ExploreCategoriesSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    sections: List<ExploreSourceSection>,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<com.kitsugi.animelist.data.remote.JikanSearchResult>) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = listOf(
        ExploreCategoryGroup.ANIME to stringResource(R.string.media_type_anime),
        ExploreCategoryGroup.MANGA to stringResource(R.string.media_type_manga),
        ExploreCategoryGroup.TV to stringResource(R.string.media_type_tv),
        ExploreCategoryGroup.MOVIES to stringResource(R.string.media_type_movie),
        ExploreCategoryGroup.MIXED to stringResource(R.string.explore_category_tv_movies),
        ExploreCategoryGroup.OTHER to stringResource(R.string.explore_categories_title)
    )
    val sectionsByGroup = sections.groupBy { it.group }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Surface.copy(alpha = 0.4f))
                .clickable { onExpandedChange(!isExpanded) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.GridView,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.explore_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            }
            KitsugiChevronIcon(
                expanded = isExpanded,
                contentDescription = stringResource(R.string.genislet_daralt),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)) {
                groups.forEach { (group, groupTitle) ->
                    val groupSections = sectionsByGroup[group].orEmpty()
                    if (groupSections.isNotEmpty()) {
                        Text(
                            text = groupTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(groupSections, key = { it.key }) { section ->
                                val sectionTitle = localizedExploreSectionTitle(section.title)
                                ExploreCategoryChip(
                                    label = sectionTitle,
                                    onClick = {
                                        onSeeAllSection(sectionTitle, section.category, section.results)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

/** Small recovery affordance for a shelf that failed its on-demand request. */
@Composable
internal fun ExploreShelfRetryRow(
    title: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.explore_source_retry, title),
                color = KitsugiColors.Accent
            )
        }
    }
}
