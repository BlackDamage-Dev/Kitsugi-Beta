package com.kitsugi.animelist.ui.components.p2p
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.core.p2p.P2pSettingsRepository
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun P2pConsentDialog(
    onDismiss: () -> Unit,
    onConsentApproved: () -> Unit,
) {
    val accentColor = LocalKitsugiAccent.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(KitsugiColors.Surface)
                    .border(1.dp, KitsugiColors.Border, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Security,
                            contentDescription = stringResource(R.string.p2p_guvenlik),
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.p2p_torrent_akisi),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.inline_user_agreement_safety_information_5517a5b),
                            style = MaterialTheme.typography.bodySmall,
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border)

                // Scrollable Warning Points
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    P2pDisclaimerItem(
                        icon = Icons.Share,
                        title = stringResource(R.string.esler_arasi_p2p_baglanti),
                        description = stringResource(R.string.bu_ozellik_harici_debrid_servislerine_gerek_kalmadan_video_akisi)
                    )

                    P2pDisclaimerItem(
                        icon = Icons.VpnKey,
                        title = stringResource(R.string.ip_adresi_gorunurlugu),
                        description = stringResource(R.string.bittorrent_protokolunun_calisma_prensibi_geregi_ip_adresiniz_swa)
                    )

                    P2pDisclaimerItem(
                        icon = Icons.Security,
                        title = stringResource(R.string.p2p_legal_compliance_title),
                        description = stringResource(R.string.kitsugi_hicbir_torrent_veya_medya_icerigi_barindirmaz_ve_kontrol)
                    )

                    P2pDisclaimerItem(
                        icon = Icons.Info,
                        title = stringResource(R.string.yukleme_seed_ayarlar),
                        description = stringResource(R.string.torrent_aginda_veri_paylasimini_upload_ve_hiz_limitlerini_ayarla)
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KitsugiColors.TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Text(stringResource(R.string.vazgec), fontWeight = FontWeight.SemiBold)
                    }

                    KitsugiButton(
                        onClick = {
                            P2pSettingsRepository.setConsentGranted(true)
                            P2pSettingsRepository.setP2pEnabled(true)
                            onConsentApproved()
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.inline_accept_and_enable_03bffee), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun P2pDisclaimerItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
