package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiVideoFilters
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.ControlsButton
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun VideoFiltersPanel(
    filterValues: Map<KitsugiVideoFilters, Int>,
    onFilterChange: (KitsugiVideoFilters, Int) -> Unit,
    onResetAll: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val filtersCard = createRef()

        Card(
            colors = panelCardsColors(),
            modifier = Modifier
                .constrainAs(filtersCard) {
                    linkTo(parent.top, parent.bottom, bias = 0.8f)
                    end.linkTo(parent.end)
                }
                .widthIn(max = CARDS_MAX_WIDTH),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Video Filtreleri", style = MaterialTheme.typography.headlineMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onResetAll) { Text(stringResource(R.string.sifirla)) }
                    ControlsButton(icon = Icons.Close, onClick = onDismissRequest)
                }
            }

            LazyColumn {
                items(KitsugiVideoFilters.entries) { filter ->
                    val value = filterValues[filter] ?: 0
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(filter.label, style = MaterialTheme.typography.bodyMedium)
                            Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { onFilterChange(filter, it.toInt()) },
                            valueRange = -100f..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Icon(Icons.Info, null)
                        Text(
                            stringResource(R.string.inline_color_filters_work_accurately_when_085d36c),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
