package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Desteklenen 6 Profil Kaynak Platformu.
 */
enum class ProfilePlatform(
    val id: String,
    val label: String,
    val description: String
) {
    ANILIST(
        id = "anilist",
        label = "AniList",
        description = KitsugiStrings.get(R.string.aktiviteler_takipciler_anime_manga_istatistikleri_favoriler_ve_d)
    ),
    MAL(
        id = "mal",
        label = "MyAnimeList",
        description = KitsugiStrings.get(R.string.klasik_mal_profil_gorunumu_anime_manga_durumlari_puanlama_ve_kul)
    ),
    SIMKL(
        id = "simkl",
        label = "Simkl",
        description = KitsugiStrings.get(R.string.tv_dizileri_filmler_anime_takibi_izleme_gecmisi_ve_hesap_detayla)
    ),
    KITSU(
        id = "kitsu",
        label = "Kitsu",
        description = KitsugiStrings.get(R.string.kutuphane_kayitlari_biyografi_takipci_istatistikleri_ve_favori_k)
    ),
    SHIKIMORI(
        id = "shikimori",
        label = "Shikimori",
        description = KitsugiStrings.get(R.string.anime_ve_manga_listeleri_bolum_sayilari_puan_istatistikleri_ve_k)
    ),
    BANGUMI(
        id = "bangumi",
        label = "Bangumi",
        description = KitsugiStrings.get(R.string.anime_manga_oyun_ve_muzik_koleksiyonlari_ilerleme_ve_puanlar_fav)
    )
}

/**
 * Arama ve Keşfet Sayfalarındaki gibi Profil Kaynak Motoru Seçici Hap Buton.
 * Orijinal logoyu gösterir, emoji içermez.
 */
@Composable
fun ProfileSourceSelectorPill(
    selectedPlatform: ProfilePlatform,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KitsugiPlatformLogo(
            platformId = selectedPlatform.id,
            size = 20.dp
        )
        Text(
            text = selectedPlatform.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.AccentGreen)
            )
        }
        Icon(
            imageVector = Icons.ArrowDropDown,
            contentDescription = stringResource(R.string.profil_kaynagi_sec),
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 5 Platformlu Profil Kaynak Seçim Bottom Sheet Diyalogu.
 * Arama ve Keşfet sayfalarındaki seçim paneli tarzında:
 * AniList, MyAnimeList, Simkl, Kitsu ve Shikimori'yi
 * orijinal logoları, bağlantı durumları ve özellik açıklamalarıyla listeler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSourcePickerSheet(
    selectedPlatformIndex: Int,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean,
    isShikimoriConnected: Boolean,
    aniListUsername: String? = null,
    malUsername: String? = null,
    simklUsername: String? = null,
    kitsuUsername: String? = null,
    shikimoriUsername: String? = null,
    isBangumiConnected: Boolean = false,
    bangumiUsername: String? = null,
    onSelectPlatform: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.profil_kaynagi_sec),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                text = stringResource(R.string.inline_choose_the_profile_source_you_9de9c06),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KitsugiColors.TextMuted
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            ProfilePlatform.entries.forEachIndexed { index, platform ->
                val isSelected = index == selectedPlatformIndex
                val isConnected = when (platform) {
                    ProfilePlatform.ANILIST -> isAniListConnected
                    ProfilePlatform.MAL -> isMalConnected
                    ProfilePlatform.SIMKL -> isSimklConnected
                    ProfilePlatform.KITSU -> isKitsuConnected
                    ProfilePlatform.SHIKIMORI -> isShikimoriConnected
                    ProfilePlatform.BANGUMI -> isBangumiConnected
                }
                val username = when (platform) {
                    ProfilePlatform.ANILIST -> aniListUsername
                    ProfilePlatform.MAL -> malUsername
                    ProfilePlatform.SIMKL -> simklUsername
                    ProfilePlatform.KITSU -> kitsuUsername
                    ProfilePlatform.SHIKIMORI -> shikimoriUsername
                    ProfilePlatform.BANGUMI -> bangumiUsername
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.12f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectPlatform(index)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(
                        platformId = platform.id,
                        size = 32.dp,
                        modifier = Modifier.padding(end = 14.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                                )
                            )
                            if (isConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.AccentGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(KitsugiColors.AccentGreen)
                                    )
                                    Text(
                                        text = if (!username.isNullOrBlank()) "@$username" else stringResource(R.string.notif_diag_connected),
                                        color = KitsugiColors.AccentGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceStrong)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.bagli_degil),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = platform.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = stringResource(R.string.secili),
                                tint = KitsugiColors.Background,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
