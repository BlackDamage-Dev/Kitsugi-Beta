package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

/**
 * 🌌 Kitsugi Premium Açılır Panel (Bottom Sheet)
 *
 * Uygulamanın cam/gradyan yüzey diliyle uyumlu, modern ve "premium" hissettiren
 * yeni nesil açılır panel:
 * - Yay fiziği (spring) ile hafif sekerek ("egzotik") açılıp kapanma
 * - Vurgu rengiyle parlayan kenarlık + aurora saçılımı (glow shadow)
 * - Gradyan sürükleme kulpu, gradyan ikon karosu ve saç çizgisi (hairline) ayracı
 * - Android TV'de ortalanmış dialog olarak aynı yüzey diliyle açılır
 *
 * Ayarlar sekmelerindeki açılır menülerle birlikte uygulamanın ortak
 * "açılır panel" dilini oluşturur (bkz. [KitsugiDropdownMenu]).
 */
@Composable
fun KitsugiPremiumSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val isDismissing = remember { mutableStateOf(false) }

    val accent = LocalKitsugiAccent.current
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Giriş: hafif sekme payı olan yay animasyonu — panel yerine otururken
    // küçük bir "bounce" ile egzotik bir açılış hissi verir.
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    fun dismissAnimated() {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
            onDismiss()
        }
    }

    val p = progress.value.coerceIn(0f, 1f)
    val borderBrush = remember(accent) {
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = 0.6f),
                accent.copy(alpha = 0.12f),
                Color.Transparent
            )
        )
    }

    if (isTv) {
        // ── Android TV: ortalanmış premium dialog ─────────────────────────────
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocusAfterFrames(frames = 3)
        }

        Dialog(
            onDismissRequest = { dismissAnimated() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PremiumScrim(
                    alpha = 0.6f * p,
                    onClick = { dismissAnimated() }
                )

                val dialogShape = RoundedCornerShape(28.dp)

                Column(
                    modifier = modifier
                        .widthIn(max = 640.dp)
                        .wrapContentHeight()
                        .heightIn(max = screenHeight * 0.85f)
                        .graphicsLayer {
                            alpha = p
                            val s = 0.92f + 0.08f * p
                            scaleX = s
                            scaleY = s
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                        .premiumSheetSurface(dialogShape, accent, borderBrush)
                        .padding(bottom = 8.dp)
                ) {
                    PremiumSheetBody(
                        title = title,
                        icon = icon,
                        badgeText = badgeText,
                        accentBrush = accentBrush,
                        onAccent = onAccent,
                        onDismiss = { dismissAnimated() },
                        content = content
                    )
                }
            }
        }
    } else {
        // ── Mobil: alttan yaylanarak çıkan premium sheet ──────────────────────
        Dialog(
            onDismissRequest = { dismissAnimated() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PremiumScrim(
                    alpha = 0.55f * p,
                    onClick = { dismissAnimated() }
                )

                val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

                Column(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.86f)
                        .graphicsLayer {
                            // Spring 1'i aştığında translationY negatife geçer →
                            // panel hafifçe yukarı sekerek yerine oturur.
                            translationY = size.height * (1f - progress.value)
                            alpha = progress.value.coerceIn(0f, 1f)
                        }
                        .premiumSheetSurface(sheetShape, accent, borderBrush)
                        .navigationBarsPadding()
                ) {
                    PremiumSheetBody(
                        title = title,
                        icon = icon,
                        badgeText = badgeText,
                        accentBrush = accentBrush,
                        onAccent = onAccent,
                        onDismiss = { dismissAnimated() },
                        content = content
                    )
                }
            }
        }
    }
}

/** Dokununca kapatılan, animasyonlu karartma katmanı. */
@Composable
private fun PremiumScrim(
    alpha: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

/** Panel yüzeyi: parlak gölge (aurora glow) + opak zemin + gradyan kenarlık. */
@Composable
private fun Modifier.premiumSheetSurface(
    shape: Shape,
    accent: Color,
    borderBrush: Brush
): Modifier = this
    .shadow(
        elevation = 24.dp,
        shape = shape,
        clip = false,
        ambientColor = accent.copy(alpha = 0.28f),
        spotColor = accent.copy(alpha = 0.38f)
    )
    .clip(shape)
    .background(KitsugiColors.SurfaceStrong, shape)
    .border(width = 1.dp, brush = borderBrush, shape = shape)

/**
 * Panel gövdesi: gradyan sürükleme kulpu + başlık satırı + aurora saç çizgisi +
 * kaydırılabilir içerik.
 */
@Composable
private fun PremiumSheetBody(
    title: String?,
    icon: ImageVector?,
    badgeText: String?,
    accentBrush: Brush,
    onAccent: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val handleShape = RoundedCornerShape(999.dp)

    // ── Gradyan sürükleme kulpu ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(5.dp)
                .clip(handleShape)
                .background(accentBrush, handleShape)
        )
    }

    if (title != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                val tileShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(tileShape)
                        .background(accentBrush, tileShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (badgeText != null) {
                Spacer(modifier = Modifier.width(10.dp))
                val badgeShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(accent.copy(alpha = 0.16f), badgeShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.SurfaceSoft, CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = null,
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Aurora saç çizgisi ayracı ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        content = content
    )
}
