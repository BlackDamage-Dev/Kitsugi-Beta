package com.kitsugi.animelist.ui.screens.stream
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Renders stream result items directly into an existing [LazyListScope] (e.g. for sticky header integration).
 */
fun LazyListScope.renderStreamResultsItems(
    addonStates: List<AddonFetchState>,
    accentColor: Color,
    onStreamSelected: (StreamSource) -> Unit,
    onDownloadSelected: (StreamSource) -> Unit,
    onVerifyPlugin: ((addonName: String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    /** v2.4.256: CloudStream eklentisini yalnızca o eklenti için yeniden çalıştırır. */
    onRetryPlugin: ((addonName: String) -> Unit)? = null,
    /** v2.4.319: Eklenti kapağı yoksa kart görseli olarak kullanılacak medya posteri. */
    posterFallbackUrl: String? = null,
    /**
     * Listenin kendi durumu. Verilirse ölçüm (ağ isteği) YALNIZCA görüş alanındaki kartlar
     * için başlatılır; null ise tüm kartlar ölçebilir (eski davranış).
     */
    listState: androidx.compose.foundation.lazy.LazyListState? = null
) {
    addonStates.forEachIndexed { addonIdx, addonState ->
        if (addonState.isLoading) {
            item(key = "skeleton_${addonIdx}_${addonState.addonName}") {
                StreamSkeletonCard(addonName = addonState.addonName)
            }
        } else if (addonState.streams.isNotEmpty()) {
            item(key = "group_${addonState.addonName}") {
                StreamGroupHeader(
                    addonName = addonState.addonName,
                    streamCount = addonState.streams.size,
                    accentColor = accentColor
                )
            }
            addonState.streams.forEachIndexed { idx, source ->
                val itemKey = "${addonIdx}_${addonState.addonName}_$idx"
                item(key = itemKey) {
                    // PERF: Eskiden her öğe `AnimatedVisibility(visible = true, enter = fadeIn + slideIn)`
                    // ile sarılıydı. Görünür değeri sabit olduğu için liste HER kaydırıldığında yeni
                    // compose edilen öğeler 300 ms'lik giriş animasyonu çalıştırıyordu — LazyColumn'un
                    // birincil kasma sebebi buydu. Giriş animasyonu kaldırıldı.
                    //
                    // Ölçüm izni yalnızca kart görüş alanındayken verilir; derivedStateOf sayesinde
                    // görünürlük değişmeyen kartlar yeniden compose edilmez.
                    val shouldProbe by remember(itemKey, listState) {
                        derivedStateOf {
                            val state = listState
                            state == null || state.layoutInfo.visibleItemsInfo.any { it.key == itemKey }
                        }
                    }
                    StreamCard(
                        source      = source,
                        accentColor = accentColor,
                        onClick     = { onStreamSelected(source) },
                        onDownloadClick = { onDownloadSelected(source) },
                        posterFallbackUrl = posterFallbackUrl,
                        probeEnabled = shouldProbe
                    )
                }
            }
        } else if (addonState.error != null) {
            item(key = "err_${addonIdx}_${addonState.addonName}") {
                StreamErrorCard(
                    addonState = addonState,
                    accentColor = accentColor,
                    onVerify = if (addonState.requiresVerification) {
                        onVerifyPlugin?.let { callback -> { callback(addonState.addonName) } }
                    } else null,
                    onRetry = if (addonState.addonName.startsWith("⚡ ")) {
                        onRetryPlugin?.let { callback -> { callback(addonState.addonName) } }
                    } else null
                )
            }
        }
    }

    if (onOpenSettings != null) {
        item(key = "settings_shortcut") {
            SettingsShortcutCard(accentColor = accentColor, onClick = onOpenSettings)
        }
    }
}

/**
 * Standalone LazyColumn wrapper for stream results list.
 */
@Composable
fun StreamResultsList(
    addonStates: List<AddonFetchState>,
    accentColor: Color,
    onStreamSelected: (StreamSource) -> Unit,
    onDownloadSelected: (StreamSource) -> Unit,
    onVerifyPlugin: ((addonName: String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onRetryPlugin: ((addonName: String) -> Unit)? = null,
    posterFallbackUrl: String? = null
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        renderStreamResultsItems(
            addonStates = addonStates,
            accentColor = accentColor,
            onStreamSelected = onStreamSelected,
            onDownloadSelected = onDownloadSelected,
            onVerifyPlugin = onVerifyPlugin,
            onOpenSettings = onOpenSettings,
            onRetryPlugin = onRetryPlugin,
            posterFallbackUrl = posterFallbackUrl,
            listState = listState
        )
    }
}

@Composable
private fun StreamErrorCard(
    addonState: AddonFetchState,
    accentColor: Color = Color.Unspecified,
    onVerify: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Text(
                    text = addonState.addonName,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = addonState.error ?: stringResource(R.string.ui_error_unknown),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            addonState.errorCode?.let { code ->
                PlatformErrorExplanationBlock(code = code)
            }

            if (onVerify != null || onRetry != null) {
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onVerify != null) {
                    KitsugiButton(
                        onClick = onVerify,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.copy(alpha = 0.2f),
                            contentColor = accentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.inline_verify_extension_captcha_02452f6),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (onRetry != null) {
                    KitsugiButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.copy(alpha = 0.2f),
                            contentColor = accentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_retry),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Eklenti grubu başlığı: vurgu renkli dikey çubuk + eklenti adı + canlı video sayacı.
 * v2.4.319: önceki sürümdeki düz metin satırı yerine görsel hiyerarşi eklendi.
 */
@Composable
fun StreamGroupHeader(
    addonName: String,
    streamCount: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        androidx.compose.material3.Text(
            text = addonName,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            androidx.compose.material3.Text(
                text = streamCount.toString(),
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SettingsShortcutCard(
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daha_fazla_kaynak_ekleyin),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.inline_go_to_settings_to_manage_b9d82ba),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                imageVector = Icons.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun StreamSkeletonCard(addonName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "skel")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue  = 0.5f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skelAlpha"
    )

    // v2.4.319: İskelet, gerçek kartın (74×104 görsel + metin sütunu) düzenini taklit eder;
    // yükleme bittiğinde liste sıçramaz.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface.copy(alpha = alpha)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.8f))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.8f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.55f))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp, 16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp, 16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp, 16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.4f))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.7f))
            )
        }
    }
}
