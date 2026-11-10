package com.kitsugi.animelist.ui.screens.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.background

/** Shared loading geometry for media, character and staff details.
 * Keep the hero, tabs and content in the positions they occupy after the request completes. */
@Composable
internal fun DetailPageLoadingSkeleton(
    onBackClick: () -> Unit,
    person: Boolean = false
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heroHeight = if (isLandscape) 280.dp else if (person) 380.dp else 420.dp
    KitsugiShimmerProvider {
        val brush = LocalShimmerBrush.current ?: return@KitsugiShimmerProvider
        LazyColumn(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    KitsugiShimmerBlock(brush, Modifier.fillMaxSize(), 0.dp)
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 24.dp)
                    ) {
                        Icon(Icons.ArrowBack,
                            contentDescription = stringResource(R.string.geri), tint = KitsugiColors.TextPrimary)
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KitsugiShimmerBlock(brush, Modifier.width(64.dp).height(24.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.6f).height(30.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.4f).height(16.dp))
                    }
                }
            }
            item(key = "tabs") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        KitsugiShimmerBlock(brush, Modifier.width(96.dp).height(36.dp), 20.dp)
                    }
                }
            }
            item(key = "overview") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitsugiShimmerBlock(brush, Modifier.width(115.dp).height(20.dp))
                    repeat(4) { line ->
                        KitsugiShimmerBlock(brush,
                            Modifier.fillMaxWidth(if (line == 3) 0.5f else 1f).height(13.dp))
                    }
                }
            }
            items(3) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp)).background(KitsugiColors.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    KitsugiShimmerBlock(brush, Modifier.size(width = 72.dp, height = 96.dp), 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.75f).height(16.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.55f).height(12.dp))
                    }
                }
            }
        }
    }
}
