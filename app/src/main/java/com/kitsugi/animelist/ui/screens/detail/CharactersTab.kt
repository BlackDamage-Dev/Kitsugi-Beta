package com.kitsugi.animelist.ui.screens.detail

import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.displayPersonName
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import androidx.compose.runtime.LaunchedEffect
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.data.remote.KitsugiVoiceActor
import com.kitsugi.animelist.data.remote.KitsugiCharacterClient
import com.kitsugi.animelist.data.remote.DetailCache
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.toTurkishCharacterRole
import com.kitsugi.animelist.utils.toLocalizedStaffRole
import com.kitsugi.animelist.utils.toTurkishLanguage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement

@Composable
fun CharactersTabContent(
    state: DetailTabState<List<KitsugiCharacter>>,
    onCharacterClick: (KitsugiCharacter) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onMediaClick: (Int, String, String) -> Unit,
    titleLanguage: String = "ENGLISH",
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage
) {
    var selectedCharacterForVoiceActors by remember { mutableStateOf<KitsugiCharacter?>(null) }

    // Seslendirmen sheet'i: liste düzeyindeki veri çoğu kaynakta tek seslendirmen taşır
    // (Jikan/MAL yalnızca Japonca, TMDB yalnızca oyuncu). Karakter detay sayfasının
    // gösterdiği TAM çok-dilli listeyi burada da göstermek için açılışta zenginleştirilir.
    var sheetVoiceActors by remember { mutableStateOf<List<KitsugiVoiceActor>>(emptyList()) }
    var sheetVaLoading by remember { mutableStateOf(false) }
    val characterClient = remember { KitsugiCharacterClient() }

    LaunchedEffect(selectedCharacterForVoiceActors) {
        val char = selectedCharacterForVoiceActors
        if (char == null) {
            sheetVoiceActors = emptyList()
            sheetVaLoading = false
            return@LaunchedEffect
        }
        sheetVoiceActors = char.voiceActors
        if (char.id <= 0 || char.source.isBlank()) return@LaunchedEffect
        sheetVaLoading = true
        try {
            // Önbellek sıcaksa (karakter detayı daha önce açıldıysa) tam liste anında gelir.
            val cacheIdentity = com.kitsugi.animelist.data.remote.CharacterDetailIdentity.cacheDiscriminator(
                source = char.source,
                name = char.name,
                isRealMediaRole = char.isRealMediaRole
            )
            var full = DetailCache.getCharacterDetail(char.source, char.id, cacheIdentity)?.voiceActors.orEmpty()
            if (full.isEmpty()) {
                val detail = characterClient.fetchCharacterDetail(
                    char.source,
                    char.id,
                    char.name,
                    isRealMediaRole = char.isRealMediaRole
                )
                if (detail != null) {
                    DetailCache.putCharacterDetail(char.source, char.id, detail, cacheIdentity)
                    full = detail.voiceActors
                }
            }
            if (full.size > sheetVoiceActors.size) {
                sheetVoiceActors = full.map { va ->
                    va.copy(
                        // Seslendirmen adları da "personel adı dili" ayarına uyar; başlık
                        // dili bu adları etkilememeli (tutarsızlık raporu).
                        name = displayPersonName(
                            va.name, va.romanizedName, va.nativeName, staffNameLanguage, va.englishName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CharactersTab", "Seslendirmen listesi zenginleştirilemedi: ${e.message}")
        } finally {
            sheetVaLoading = false
        }
    }

    when (state) {
        is DetailTabState.Loading -> {
            DetailCharactersLoadingSkeleton(itemCount = 4)
        }
        is DetailTabState.Error -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_characters_load_error),
                    color = KitsugiColors.AccentRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
            }
        }
        is DetailTabState.Success -> {
            val list = remember(state.data, staffNameLanguage) {
                state.data.map { character ->
                    character.copy(
                        name = displayPersonName(
                            character.name, character.romanizedName, character.nativeName, staffNameLanguage, character.englishName
                        ),
                        voiceActors = character.voiceActors.map { va ->
                            va.copy(name = displayPersonName(va.name, va.romanizedName, va.nativeName, staffNameLanguage, va.englishName))
                        }
                    )
                }
            }
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_characters_empty),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                // Bangumi kadrosu topluluk katkılı bir kaynaktır; liste gizlenmez ama kaynağı
                // belirtilir (canlı çekim kayıtlarda animasyon uyarlaması sanatı/rolü görülebiliyor).
                val fromBangumi = remember(list) {
                    list.any {
                        it.source.equals("bangumi", ignoreCase = true) || it.source.equals("bgm", ignoreCase = true)
                    }
                }
                if (fromBangumi) {
                    Text(
                        text = stringResource(R.string.characters_bangumi_source_note),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }
                var visibleLimit by remember(list) { mutableIntStateOf(24) }
                val displayedList = remember(list, visibleLimit) { list.take(visibleLimit) }
                val remainingCount = (list.size - displayedList.size).coerceAtLeast(0)
                val accentColor = LocalKitsugiAccent.current
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLandscape) {
                        val chunks = remember(displayedList) { displayedList.chunked(2) }
                        chunks.forEachIndexed { rowIdx, rowItems ->
                            key("char_row_${rowIdx}_${rowItems.first().id}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { char ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            CharacterVoiceActorCard(
                                                char = char,
                                                onCharacterClick = onCharacterClick,
                                                onVoiceActorClick = { va -> onStaffClick(va.id, va.source, va.name, va.imageUrl) },
                                                onShowVoiceActors = { c ->
                                                    sheetVoiceActors = c.voiceActors
                                                    selectedCharacterForVoiceActors = c
                                                }
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        displayedList.forEachIndexed { idx, char ->
                            key("char_${idx}_${char.source}_${char.id}") {
                                CharacterVoiceActorCard(
                                    char = char,
                                    onCharacterClick = onCharacterClick,
                                    onVoiceActorClick = { va -> onStaffClick(va.id, va.source, va.name, va.imageUrl) },
                                    onShowVoiceActors = { c ->
                                        sheetVoiceActors = c.voiceActors
                                        selectedCharacterForVoiceActors = c
                                    }
                                )
                            }
                        }
                    }
                    if (remainingCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                    visibleLimit += 36
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.x_daha, remainingCount),
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    selectedCharacterForVoiceActors?.let { character ->
        val vaListState = rememberLazyListState()
        KitsugiSheetOrDialog(
            onDismiss = { selectedCharacterForVoiceActors = null },
            innerScrollState = vaListState
        ) {
            Text(
                text = "${character.name} - ${stringResource(R.string.detail_voice_actors)}",
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            LazyColumn(
                state = vaListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sheetVoiceActors.size) { index ->
                    val va = sheetVoiceActors[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KitsugiColors.Surface)
                            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = {
                                selectedCharacterForVoiceActors = null
                                onStaffClick(va.id, va.source, va.name, va.imageUrl)
                            })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KitsugiColors.SurfaceSoft)
                        ) {
                            if (!va.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = va.imageUrl,
                                    contentDescription = va.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(va.name.take(2).uppercase(), color = KitsugiColors.TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = va.name,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // Dil bilgisi yoksa (kaynak API vermediyse) dil uydurulmaz.
                            val labelText = when {
                                va.language.isBlank() -> stringResource(R.string.detail_role_voice_actor_plain)
                                va.language.equals("oyuncu", ignoreCase = true) -> stringResource(R.string.detail_role_actor)
                                else -> va.language
                            }
                            Text(
                                text = labelText,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                // Tam çok-dilli liste arka planda yüklenirken küçük bir gösterge.
                if (sheetVaLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = KitsugiColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }


}

@Composable
fun CharacterVoiceActorCard(
    char: KitsugiCharacter,
    onCharacterClick: (KitsugiCharacter) -> Unit,
    onVoiceActorClick: (com.kitsugi.animelist.data.remote.KitsugiVoiceActor) -> Unit,
    onShowVoiceActors: (KitsugiCharacter) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    // Önce Japonca seslendirici ara (Türkçe karşılığı: "Japonca"), yoksa TMDB oyuncusu, yoksa ilki
    val va = char.voiceActors.firstOrNull { it.language.equals("Japonca", ignoreCase = true) }
        ?: char.voiceActors.firstOrNull { it.language.equals("oyuncu", ignoreCase = true) }
        ?: char.voiceActors.firstOrNull()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .padding(12.dp)
    ) {
        // Character Row (Top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = RoundedCornerShape(16.dp), onClick = { onCharacterClick(char) }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft)
            ) {
                if (!char.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = char.imageUrl,
                        contentDescription = char.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(char.name.take(2).uppercase(), color = KitsugiColors.TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = char.name,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Rol zaten veri katmanında Türkçeye çevrilmiş gelir ("Ana Karakter", "Yardımcı Karakter", vb.)
                val isMainRole = char.role.contains("Ana", ignoreCase = true) || char.role.contains("Main", ignoreCase = true)
                Text(
                    text = char.role,
                    color = if (isMainRole) accentColor else KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (char.voiceActors.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .tvClickable(shape = RoundedCornerShape(10.dp)) {
                            onShowVoiceActors(char)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.RecordVoiceOver,
                        contentDescription = stringResource(R.string.detail_voice_actors),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        if (va != null) {
            // A subtle connecting vertical line
            Box(
                modifier = Modifier
                    .padding(start = 21.dp, top = 2.dp, bottom = 2.dp)
                    .width(2.dp)
                    .height(10.dp)
                    .background(KitsugiColors.SurfaceSoft)
            )
            
            // Voice Actor Row (Bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvClickable(shape = RoundedCornerShape(16.dp), onClick = { onVoiceActorClick(va) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KitsugiColors.SurfaceSoft)
                ) {
                    if (!va.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = va.imageUrl,
                            contentDescription = va.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(va.name.take(2).uppercase(), color = KitsugiColors.TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = va.name,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Dil zaten Türkçe geliyor: "Japonca", "Korece", "oyuncu" vb.
                    val labelText = when {
                        va.language.isBlank() -> stringResource(R.string.detail_role_voice_actor_plain)
                        va.language.equals("oyuncu", ignoreCase = true) -> stringResource(R.string.detail_role_actor)
                        else -> stringResource(R.string.detail_role_voice_actor, va.language)
                    }
                    Text(
                        text = labelText,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun StaffTabContent(
    state: DetailTabState<List<KitsugiStaff>>,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    titleLanguage: String = "ENGLISH",
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage
) {
    when (state) {
        is DetailTabState.Loading -> {
            DetailStaffLoadingSkeleton(itemCount = 5)
        }
        is DetailTabState.Error -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_staff_load_error),
                    color = KitsugiColors.AccentRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
            }
        }
        is DetailTabState.Success -> {
            val list = remember(state.data, staffNameLanguage) {
                state.data.map { staff ->
                    staff.copy(
                        name = displayPersonName(
                            staff.name, staff.romanizedName, staff.nativeName, staffNameLanguage, staff.englishName
                        ),
                        role = staff.role.toLocalizedStaffRole()
                    )
                }
            }
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_staff_empty),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                var visibleLimit by remember(list) { mutableIntStateOf(24) }
                val displayedList = remember(list, visibleLimit) { list.take(visibleLimit) }
                val remainingCount = (list.size - displayedList.size).coerceAtLeast(0)
                val accentColor = LocalKitsugiAccent.current
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLandscape) {
                        val chunks = remember(displayedList) { displayedList.chunked(2) }
                        chunks.forEachIndexed { rowIdx, rowItems ->
                            key("staff_row_${rowIdx}_${rowItems.first().id}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { staff ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            StaffRow(staff = staff, rolePreLocalized = true, onStaffClick = onStaffClick)
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        displayedList.forEachIndexed { idx, staff ->
                            key("staff_${idx}_${staff.source}_${staff.id}") {
                                StaffRow(staff = staff, rolePreLocalized = true, onStaffClick = onStaffClick)
                            }
                        }
                    }
                    if (remainingCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                    visibleLimit += 36
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.x_daha, remainingCount),
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffRow(
    staff: KitsugiStaff,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    rolePreLocalized: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = { onStaffClick(staff.id, staff.source, staff.name, staff.imageUrl) })
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft)
        ) {
            if (!staff.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = staff.imageUrl,
                    contentDescription = staff.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(staff.name.take(2).uppercase(), color = KitsugiColors.TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = staff.name,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (rolePreLocalized) staff.role else staff.role.toLocalizedStaffRole(),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
