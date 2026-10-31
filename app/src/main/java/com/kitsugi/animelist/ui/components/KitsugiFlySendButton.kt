package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Uiverse (adamgiebl) referanslı, dokunmatik ekranlara ve TV'ye tam uyarlanmış
 * animasyonlu uçan kağıt uçak gönderme (Send / Fly) butonu.
 *
 * Dokunmatik çalışma prensibi:
 * 1. Durağan durumda (Idle): Kağıt uçak simgesi CSS `fly-1` animasyonundaki gibi
 *    havada hafifçe süzülür (±2 dp yumuşak dikey nefes alma).
 * 2. Basıldığı an (Touch-down / Press):
 *    - Buton ölçeği CSS `button:active { transform: scale(0.95) }` gibi 0.95f'e esner.
 *    - Uçak 45 derece eğilerek havalanmaya hazır konuma gelir.
 * 3. Gönderilme anı (Launch / OnClick):
 *    - Uçak sağa doğru roket gibi fırlar (translateX + rotate + scale).
 *    - Metin yana kayarak uçağa yol verir.
 *    - ~300 ms sonra `onClick` tetiklenir ve buton zarifçe sıfırlanır.
 * 4. Android TV / Fare (Hover & D-pad Focus):
 *    - Odak veya fare üzerine geldiğinde de uçak kalkış açısına geçer.
 */
@Composable
fun KitsugiFlySendButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.action_send),
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(16.dp),
    height: Dp = 46.dp
) {
    val accentColor = LocalKitsugiAccent.current
    val effectiveColor = containerColor ?: accentColor

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val isInteracting = isPressed || isHovered || isFocused || isLaunching

    // Durağan hafif süzülme animasyonu (@keyframes fly-1)
    val infiniteTransition = rememberInfiniteTransition(label = "flyIdle")
    val idleFloatY by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "planeIdleY"
    )

    // Basılma scale'i (button:active scale 0.95)
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "btnActiveScale"
    )

    // Uçağın kalkış açısı (45 derece)
    val planeRotation by animateFloatAsState(
        targetValue = if (isInteracting) 45f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "planeRotation"
    )

    // Uçağın kalkışta sağa fırlaması (translateX)
    val planeTranslationX by animateFloatAsState(
        targetValue = if (isLaunching) 48f else if (isInteracting) 4f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "planeTranslationX"
    )

    // Metnin kalkışta kayması
    val textTranslationX by animateFloatAsState(
        targetValue = if (isLaunching) 35f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "textTranslationX"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isLaunching) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "textAlpha"
    )

    Box(
        modifier = modifier
            .scale(buttonScale)
            .shadow(
                elevation = if (isInteracting) 10.dp else 4.dp,
                shape = shape,
                spotColor = effectiveColor.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(effectiveColor)
            .height(height)
            .onFocusChanged { isFocused = it.isFocused }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                onClick = {
                    coroutineScope.launch {
                        isLaunching = true
                        delay(280)
                        onClick()
                        delay(200)
                        isLaunching = false
                    }
                }
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            KitsugiPlasmaLoader(size = 20.dp)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Uçak Simgesi
                Box(
                    modifier = Modifier.graphicsLayer {
                        translationY = if (!isInteracting) idleFloatY else 0f
                        translationX = planeTranslationX
                        rotationZ = planeRotation
                        scaleX = if (isInteracting) 1.12f else 1.0f
                        scaleY = if (isInteracting) 1.12f else 1.0f
                    }
                ) {
                    Icon(
                        imageVector = Icons.Send,
                        contentDescription = text,
                        tint = contentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Gönder / Send Metni
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.graphicsLayer {
                        translationX = textTranslationX
                        alpha = textAlpha
                    }
                )
            }
        }
    }
}

/**
 * Resim galerisi araç çubuğu ve dar alanlar için kompakt yuvarlak/kapsül varyantı.
 */
@Composable
fun KitsugiFlySendIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.action_send),
    size: Dp = 40.dp,
    containerColor: Color? = null,
    contentColor: Color = Color.White
) {
    val accentColor = LocalKitsugiAccent.current
    val effectiveColor = containerColor ?: accentColor

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isHovered by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val isInteracting = isPressed || isHovered || isLaunching

    val planeRotation by animateFloatAsState(
        targetValue = if (isInteracting) 45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "iconPlaneRot"
    )

    val planeTranslationX by animateFloatAsState(
        targetValue = if (isLaunching) 30f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "iconPlaneTrans"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(if (isInteracting) 6.dp else 2.dp, RoundedCornerShape(12.dp), spotColor = effectiveColor.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(12.dp))
            .background(effectiveColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                onClick = {
                    coroutineScope.launch {
                        isLaunching = true
                        delay(250)
                        onClick()
                        delay(150)
                        isLaunching = false
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Send,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier
                .size((size.value * 0.5f).dp)
                .graphicsLayer {
                    translationX = planeTranslationX
                    rotationZ = planeRotation
                    scaleX = if (isInteracting) 1.15f else 1.0f
                    scaleY = if (isInteracting) 1.15f else 1.0f
                }
        )
    }
}
