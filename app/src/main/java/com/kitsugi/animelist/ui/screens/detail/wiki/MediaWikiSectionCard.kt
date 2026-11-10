package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomMediaQuery
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Sekmesi olmayan detay ekranları (eklenti kaynağından açılan manga, TV detayı) için
 * açılır-kapanır **Wiki** kartı. Kapalıyken yalnızca başlık satırı vardır; kullanıcı açınca
 * [MediaWikiTabSlot] (seri özeti, arama, evren rehberi) içeride yüklenir. Böylece wiki
 * çözümlemesi ve içerik isteği kullanıcı istemeden yapılmaz.
 *
 * Fandom ayarı kapalıysa kart hiç çizilmez.
 */
@Composable
fun MediaWikiSectionCard(
    viewModel: FandomMediaWikiViewModel,
    query: FandomMediaQuery?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    title: String = stringResource(R.string.fandom_manga_wiki_title),
    hint: String? = stringResource(R.string.fandom_manga_wiki_hint)
) {
    val prefs by viewModel.prefs.collectAsState()
    if (!prefs.enabled || query == null) return
    var expanded by rememberSaveable(query.cacheKey()) { mutableStateOf(initiallyExpanded) }

    // Çözümleme ancak kart açılınca başlar (bağlama ucuzdur; ağ isteği açılınca gider).
    LaunchedEffect(expanded, query, prefs.enabled, prefs.nameFallback) {
        if (expanded) viewModel.bind(query.takeIf { prefs.enabled })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = RoundedCornerShape(18.dp)) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.MenuBook,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!expanded && hint != null) {
                    Text(
                        text = hint,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            KitsugiChevronIcon(
                expanded = expanded,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                MediaWikiTabSlot(viewModel = viewModel, accentColor = accentColor)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
