package com.kitsugi.animelist.ui.theme.gradient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background as foundationBackground
import androidx.compose.foundation.border as foundationBorder
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalInsideAccentSurface
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentAngle
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.accentBrushWithAlpha
import com.kitsugi.animelist.ui.theme.isSameAccentRgb
import com.kitsugi.animelist.ui.theme.resolveAccentBrush
import com.kitsugi.animelist.ui.theme.resolveCurrentAccentBrush

// ─────────────────────────────────────────────────────────────────────────────
// 1. Modifier.background & Modifier.border (Gradyan Duyarlı, Non-Composable)
// ─────────────────────────────────────────────────────────────────────────────

fun Modifier.background(
    color: Color,
    shape: Shape = RectangleShape
): Modifier = this.then(AccentAwareBackgroundElement(color = color, shape = shape))

fun Modifier.background(
    brush: Brush,
    shape: Shape = RectangleShape,
    alpha: Float = 1.0f
): Modifier = this.foundationBackground(brush = brush, shape = shape, alpha = alpha)

private data class AccentAwareBackgroundElement(
    val color: Color,
    val shape: Shape
) : ModifierNodeElement<AccentAwareBackgroundNode>() {
    override fun create(): AccentAwareBackgroundNode = AccentAwareBackgroundNode(color, shape)
    override fun update(node: AccentAwareBackgroundNode) {
        node.color = color
        node.shape = shape
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "background"
        properties["color"] = color
        properties["shape"] = shape
    }
}

private class AccentAwareBackgroundNode(
    var color: Color,
    var shape: Shape
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    // PERF: fırça çözümünü her çizimde tekrarlamak yerine girdiler değişene kadar
    // önbellekte tut. (Gradyan aktif değilken çözüm zaten null döner; yine de
    // yüzlerce kart × kare başına tekrarlanan çağrıları sıfırlıyoruz.)
    private var cachedInputColor = Color.Unspecified
    private var cachedAccent = Color.Unspecified
    private var cachedAccent2: Color? = Color.Unspecified
    private var cachedAngle = Float.NaN
    private var cachedBrush: Brush? = null
    private var cacheValid = false

    override fun ContentDrawScope.draw() {
        if (color != Color.Unspecified && color.alpha > 0f && size.width > 0f && size.height > 0f) {
            val accent = currentValueOf(LocalKitsugiAccent)
            val accent2 = currentValueOf(LocalKitsugiAccent2)
            val angle = currentValueOf(LocalKitsugiAccentAngle)
            val brush = if (
                cacheValid &&
                color == cachedInputColor &&
                accent == cachedAccent &&
                accent2 == cachedAccent2 &&
                angle == cachedAngle
            ) {
                cachedBrush
            } else {
                val resolved = resolveAccentBrush(color, accent, accent2, angle)
                cachedInputColor = color
                cachedAccent = accent
                cachedAccent2 = accent2
                cachedAngle = angle
                cachedBrush = resolved
                cacheValid = true
                resolved
            }
            if (shape === RectangleShape) {
                if (brush != null) {
                    drawRect(brush = brush)
                } else {
                    drawRect(color = color)
                }
            } else {
                val outline = shape.createOutline(size, layoutDirection, this)
                if (brush != null) {
                    drawOutline(outline = outline, brush = brush)
                } else {
                    drawOutline(outline = outline, color = color)
                }
            }
        }
        drawContent()
    }
}

fun Modifier.border(
    width: Dp,
    color: Color,
    shape: Shape = RectangleShape
): Modifier = this.then(AccentAwareBorderElement(width = width, color = color, brush = null, shape = shape))

fun Modifier.border(
    width: Dp,
    brush: Brush,
    shape: Shape = RectangleShape
): Modifier = this.foundationBorder(width = width, brush = brush, shape = shape)

fun Modifier.border(
    border: BorderStroke,
    shape: Shape = RectangleShape
): Modifier = when (val b = border.brush) {
    is SolidColor -> this.then(AccentAwareBorderElement(width = border.width, color = b.value, brush = null, shape = shape))
    else -> this.foundationBorder(border = border, shape = shape)
}

private data class AccentAwareBorderElement(
    val width: Dp,
    val color: Color,
    val brush: Brush?,
    val shape: Shape
) : ModifierNodeElement<AccentAwareBorderNode>() {
    override fun create(): AccentAwareBorderNode = AccentAwareBorderNode(width, color, brush, shape)
    override fun update(node: AccentAwareBorderNode) {
        node.width = width
        node.color = color
        node.brush = brush
        node.shape = shape
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "border"
        properties["width"] = width
        properties["color"] = color
        properties["shape"] = shape
    }
}

private class AccentAwareBorderNode(
    var width: Dp,
    var color: Color,
    var brush: Brush?,
    var shape: Shape
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    // PERF: bkz. AccentAwareBackgroundNode — aynı girdiyle her karede yeniden
    // fırça çözmek yerine sonucu önbellekle.
    private var cachedInputColor = Color.Unspecified
    private var cachedAccent = Color.Unspecified
    private var cachedAccent2: Color? = Color.Unspecified
    private var cachedAngle = Float.NaN
    private var cachedResolvedBrush: Brush? = null
    private var cacheValid = false

    override fun ContentDrawScope.draw() {
        drawContent()
        if (width <= 0.dp || size.minDimension <= 0f) return
        val activeBrush = brush ?: run {
            if (color == Color.Unspecified || color.alpha <= 0f) return
            val accent = currentValueOf(LocalKitsugiAccent)
            val accent2 = currentValueOf(LocalKitsugiAccent2)
            val angle = currentValueOf(LocalKitsugiAccentAngle)
            if (
                cacheValid &&
                color == cachedInputColor &&
                accent == cachedAccent &&
                accent2 == cachedAccent2 &&
                angle == cachedAngle
            ) {
                cachedResolvedBrush
            } else {
                val resolved = resolveAccentBrush(color, accent, accent2, angle)
                cachedInputColor = color
                cachedAccent = accent
                cachedAccent2 = accent2
                cachedAngle = angle
                cachedResolvedBrush = resolved
                cacheValid = true
                resolved
            }
        }
        val strokePx = width.toPx().coerceAtMost(size.minDimension / 2f)
        if (strokePx <= 0f) return
        val half = strokePx / 2f
        val insetTopLeft = Offset(half, half)
        val insetSize = Size(
            (size.width - strokePx).coerceAtLeast(0f),
            (size.height - strokePx).coerceAtLeast(0f)
        )
        val stroke = Stroke(width = strokePx)
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> {
                if (activeBrush != null) {
                    drawRect(brush = activeBrush, topLeft = insetTopLeft, size = insetSize, style = stroke)
                } else {
                    drawRect(color = color, topLeft = insetTopLeft, size = insetSize, style = stroke)
                }
            }
            is Outline.Rounded -> {
                val rr = outline.roundRect
                val tl = rr.topLeftCornerRadius
                if (tl == rr.topRightCornerRadius && tl == rr.bottomRightCornerRadius && tl == rr.bottomLeftCornerRadius) {
                    val cr = CornerRadius((tl.x - half).coerceAtLeast(0f), (tl.y - half).coerceAtLeast(0f))
                    if (activeBrush != null) {
                        drawRoundRect(brush = activeBrush, topLeft = insetTopLeft, size = insetSize, cornerRadius = cr, style = stroke)
                    } else {
                        drawRoundRect(color = color, topLeft = insetTopLeft, size = insetSize, cornerRadius = cr, style = stroke)
                    }
                } else {
                    if (activeBrush != null) {
                        drawOutline(outline = outline, brush = activeBrush, style = stroke)
                    } else {
                        drawOutline(outline = outline, color = color, style = stroke)
                    }
                }
            }
            is Outline.Generic -> {
                if (activeBrush != null) {
                    drawOutline(outline = outline, brush = activeBrush, style = stroke)
                } else {
                    drawOutline(outline = outline, color = color, style = stroke)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Text & Icon (Gradyan Yazı & İkon + Otomatik Kontrast)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val insideAccent = LocalInsideAccentSurface.current
    val bg = LocalKitsugiColors.current.background
    val onAccent = LocalKitsugiOnAccent.current
    val rawColor = if (color != Color.Unspecified) color else style.color
    val isBgAsOnAccent = rawColor != Color.Unspecified && rawColor.alpha >= 0.99f && rawColor == bg

    val adjustedColor = when {
        insideAccent && (rawColor == Color.White || rawColor == Color.Black || isBgAsOnAccent) -> onAccent
        isBgAsOnAccent -> onAccent
        else -> color
    }

    val effectiveForGradient = when {
        adjustedColor != Color.Unspecified -> adjustedColor
        style.color != Color.Unspecified -> style.color
        else -> LocalContentColor.current
    }
    val accentBrush = if (insideAccent) null else resolveCurrentAccentBrush(effectiveForGradient)

    if (accentBrush != null) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = Color.Unspecified,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style.copy(brush = accentBrush)
        )
    } else {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = adjustedColor,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style
        )
    }
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val insideAccent = LocalInsideAccentSurface.current
    val bg = LocalKitsugiColors.current.background
    val onAccent = LocalKitsugiOnAccent.current
    val rawColor = if (color != Color.Unspecified) color else style.color
    val isBgAsOnAccent = rawColor != Color.Unspecified && rawColor.alpha >= 0.99f && rawColor == bg

    val adjustedColor = when {
        insideAccent && (rawColor == Color.White || rawColor == Color.Black || isBgAsOnAccent) -> onAccent
        isBgAsOnAccent -> onAccent
        else -> color
    }

    val effectiveForGradient = when {
        adjustedColor != Color.Unspecified -> adjustedColor
        style.color != Color.Unspecified -> style.color
        else -> LocalContentColor.current
    }
    val accentBrush = if (insideAccent) null else resolveCurrentAccentBrush(effectiveForGradient)

    if (accentBrush != null) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = Color.Unspecified,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
            style = style.copy(brush = accentBrush)
        )
    } else {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = adjustedColor,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
            style = style
        )
    }
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val insideAccent = LocalInsideAccentSurface.current
    val bg = LocalKitsugiColors.current.background
    val onAccent = LocalKitsugiOnAccent.current
    val isBgAsOnAccent = tint != Color.Unspecified && tint.alpha >= 0.99f && tint == bg

    val adjustedTint = when {
        insideAccent && (tint == Color.White || tint == Color.Black || isBgAsOnAccent) -> onAccent
        isBgAsOnAccent -> onAccent
        else -> tint
    }
    val accentBrush = if (insideAccent) null else resolveCurrentAccentBrush(adjustedTint)

    if (accentBrush != null) {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            tint = Color.White
        )
    } else {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = adjustedTint
        )
    }
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val insideAccent = LocalInsideAccentSurface.current
    val bg = LocalKitsugiColors.current.background
    val onAccent = LocalKitsugiOnAccent.current
    val isBgAsOnAccent = tint != Color.Unspecified && tint.alpha >= 0.99f && tint == bg

    val adjustedTint = when {
        insideAccent && (tint == Color.White || tint == Color.Black || isBgAsOnAccent) -> onAccent
        isBgAsOnAccent -> onAccent
        else -> tint
    }
    val accentBrush = if (insideAccent) null else resolveCurrentAccentBrush(adjustedTint)

    if (accentBrush != null) {
        androidx.compose.material3.Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            tint = Color.White
        )
    } else {
        androidx.compose.material3.Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = adjustedTint
        )
    }
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val insideAccent = LocalInsideAccentSurface.current
    val bg = LocalKitsugiColors.current.background
    val onAccent = LocalKitsugiOnAccent.current
    val isBgAsOnAccent = tint != Color.Unspecified && tint.alpha >= 0.99f && tint == bg

    val adjustedTint = when {
        insideAccent && (tint == Color.White || tint == Color.Black || isBgAsOnAccent) -> onAccent
        isBgAsOnAccent -> onAccent
        else -> tint
    }
    val accentBrush = if (insideAccent) null else resolveCurrentAccentBrush(adjustedTint)

    if (accentBrush != null) {
        androidx.compose.material3.Icon(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            tint = Color.White
        )
    } else {
        androidx.compose.material3.Icon(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = adjustedTint
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Switch (Açılır/Kapanır Buton — Gradyan Kanal + Otomatik Kontrast Başlık)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    checkedBrush: Brush? = null
) {
    val activeBrush = checkedBrush ?: LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    val accent = LocalKitsugiAccent.current

    val switchModifier = if (checked) {
        modifier.drawBehind {
            val trackWidth = 52.dp.toPx()
            val trackHeight = 32.dp.toPx()
            val left = (size.width - trackWidth) / 2f
            val top = (size.height - trackHeight) / 2f
            drawRoundRect(
                brush = activeBrush,
                topLeft = Offset(left, top),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
                alpha = if (enabled) 1f else 0.38f
            )
        }
    } else {
        modifier
    }

    val resolvedColors = SwitchDefaults.colors(
        checkedThumbColor = onAccent,
        checkedTrackColor = Color.Transparent,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = accent,
        uncheckedThumbColor = KitsugiColors.TextSecondary,
        uncheckedTrackColor = KitsugiColors.SurfaceStrong,
        uncheckedBorderColor = KitsugiColors.Border,
        disabledCheckedThumbColor = onAccent.copy(alpha = 0.5f),
        disabledCheckedTrackColor = Color.Transparent,
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = KitsugiColors.TextMuted.copy(alpha = 0.4f),
        disabledUncheckedTrackColor = KitsugiColors.SurfaceSoft.copy(alpha = 0.4f),
        disabledUncheckedBorderColor = KitsugiColors.Border.copy(alpha = 0.4f)
    )

    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = switchModifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = resolvedColors,
        interactionSource = interactionSource
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Slider & RangeSlider (Gradyan Aktif Şerit & Başlık)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    customBrush: Brush? = null
) {
    val activeBrush = customBrush ?: LocalKitsugiAccentBrush.current
    val inactiveColor = KitsugiColors.SurfaceStrong
    val onAccent = LocalKitsugiOnAccent.current
    val span = (valueRange.endInclusive - valueRange.start).let { if (it <= 0f) 1f else it }
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val transparentColors = SliderDefaults.colors(
        thumbColor = Color.Transparent,
        activeTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = Color.Transparent,
        disabledActiveTrackColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTrackColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent
    )

    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.drawWithContent {
            val thumbRadius = 10.dp.toPx()
            val trackHeight = 6.dp.toPx()
            val trackStart = thumbRadius
            val trackEnd = (size.width - thumbRadius).coerceAtLeast(trackStart)
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val cy = size.height / 2f
            val thumbX = trackStart + trackWidth * fraction
            val alpha = if (enabled) 1f else 0.4f

            // Pasif şerit
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(trackStart, cy - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
                alpha = alpha
            )

            // Aktif gradyan şerit
            val activeWidth = (thumbX - trackStart).coerceAtLeast(0f)
            if (activeWidth > 0f) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(trackStart, cy - trackHeight / 2f),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                    alpha = alpha
                )
            }

            // Adım noktaları
            if (steps > 0 && trackWidth > 0f) {
                val totalIntervals = steps + 1
                for (i in 1..steps) {
                    val stepFrac = i.toFloat() / totalIntervals.toFloat()
                    val sx = trackStart + trackWidth * stepFrac
                    drawCircle(
                        color = if (stepFrac <= fraction) onAccent.copy(alpha = 0.65f) else onAccent.copy(alpha = 0.25f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(sx, cy)
                    )
                }
            }

            // Gradyan tutamaç (thumb) + kontrast iç nokta
            drawCircle(
                brush = activeBrush,
                radius = thumbRadius,
                center = Offset(thumbX, cy),
                alpha = alpha
            )
            drawCircle(
                color = onAccent,
                radius = 3.5.dp.toPx(),
                center = Offset(thumbX, cy),
                alpha = alpha
            )

            drawContent()
        },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = transparentColors,
        interactionSource = interactionSource
    )
}

@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") colors: SliderColors = SliderDefaults.colors()
) {
    val activeBrush = LocalKitsugiAccentBrush.current
    val inactiveColor = KitsugiColors.SurfaceStrong
    val onAccent = LocalKitsugiOnAccent.current
    val span = (valueRange.endInclusive - valueRange.start).let { if (it <= 0f) 1f else it }
    val startFrac = ((value.start - valueRange.start) / span).coerceIn(0f, 1f)
    val endFrac = ((value.endInclusive - valueRange.start) / span).coerceIn(startFrac, 1f)

    val transparentColors = SliderDefaults.colors(
        thumbColor = Color.Transparent,
        activeTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = Color.Transparent,
        disabledActiveTrackColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTrackColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent
    )

    androidx.compose.material3.RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.drawWithContent {
            val thumbRadius = 10.dp.toPx()
            val trackHeight = 6.dp.toPx()
            val trackStart = thumbRadius
            val trackEnd = (size.width - thumbRadius).coerceAtLeast(trackStart)
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val cy = size.height / 2f
            val startX = trackStart + trackWidth * startFrac
            val endX = trackStart + trackWidth * endFrac
            val alpha = if (enabled) 1f else 0.4f

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(trackStart, cy - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
                alpha = alpha
            )

            val activeWidth = (endX - startX).coerceAtLeast(0f)
            if (activeWidth > 0f) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(startX, cy - trackHeight / 2f),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                    alpha = alpha
                )
            }

            drawCircle(brush = activeBrush, radius = thumbRadius, center = Offset(startX, cy), alpha = alpha)
            drawCircle(color = onAccent, radius = 3.5.dp.toPx(), center = Offset(startX, cy), alpha = alpha)

            drawCircle(brush = activeBrush, radius = thumbRadius, center = Offset(endX, cy), alpha = alpha)
            drawCircle(color = onAccent, radius = 3.5.dp.toPx(), center = Offset(endX, cy), alpha = alpha)

            drawContent()
        },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = transparentColors
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. RadioButton & Checkbox
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val accent = LocalKitsugiAccent.current
    val isGradient = LocalKitsugiAccent2.current != null
    val accentBrush = LocalKitsugiAccentBrush.current

    if (selected && isGradient) {
        androidx.compose.material3.RadioButton(
            selected = true,
            onClick = onClick,
            modifier = modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = KitsugiColors.TextSecondary
            ),
            interactionSource = interactionSource
        )
    } else {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = KitsugiColors.TextSecondary
            ),
            interactionSource = interactionSource
        )
    }
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current

    val boxModifier = if (checked) {
        modifier.drawBehind {
            val boxSize = 20.dp.toPx()
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            drawRoundRect(
                brush = accentBrush,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(3.dp.toPx()),
                alpha = if (enabled) 1f else 0.38f
            )
        }
    } else {
        modifier
    }

    androidx.compose.material3.Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = boxModifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = Color.Transparent,
            uncheckedColor = KitsugiColors.TextSecondary,
            checkmarkColor = onAccent,
            disabledCheckedColor = Color.Transparent,
            disabledUncheckedColor = KitsugiColors.TextMuted.copy(alpha = 0.4f)
        ),
        interactionSource = interactionSource
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. CircularProgressIndicator & LinearProgressIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Uygulamanın ORTAK belirsiz (dönen) yüklenme göstergesi.
 *
 * Standart "pat pat dönen halka" kaldırıldı: çağrının yapıldığı her yerde artık
 * [KitsugiPlasmaLoader] ışık küresi çizilir (büyük boyutta tam plazma simülasyonu,
 * 30 dp ve altında kompakt ışık küresi). Böylece oynatıcı, liste satırları, rozetler,
 * buton içi durumlar ve sheet'ler tek bir yüklenme kimliği paylaşır.
 *
 * `strokeWidth`, `trackColor` ve `strokeCap` parametreleri yalnızca eski çağrı
 * noktalarının bozulmaması için taşınır; halka çizilmediği için görünüme etkisi yoktur.
 * Boyut, çağıranın `Modifier.size(...)` değeriyle belirlenir (verilmemişse 40 dp).
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalKitsugiAccent.current,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap
) {
    KitsugiPlasmaLoader(
        modifier = modifier,
        size = 40.dp,
        color = color
    )
}

@Composable
fun CircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalKitsugiAccent.current,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap
) {
    val accentBrush = resolveCurrentAccentBrush(color)
    if (accentBrush != null) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = progress,
            modifier = modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            color = Color.White,
            strokeWidth = strokeWidth,
            trackColor = Color.Transparent,
            strokeCap = strokeCap
        )
    } else {
        androidx.compose.material3.CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap
        )
    }
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalKitsugiAccent.current,
    trackColor: Color = KitsugiColors.SurfaceStrong,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
) {
    val accentBrush = resolveCurrentAccentBrush(color)
    if (accentBrush != null) {
        val trackBrush = resolveCurrentAccentBrush(trackColor)
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier
                .drawBehind {
                    if (size.width > 0f && size.height > 0f) {
                        if (trackBrush != null) {
                            drawRoundRect(brush = trackBrush, cornerRadius = CornerRadius(size.height / 2f))
                        } else if (trackColor != Color.Transparent) {
                            drawRoundRect(color = trackColor, cornerRadius = CornerRadius(size.height / 2f))
                        }
                    }
                }
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (size.width > 0f && size.height > 0f) {
                        drawRect(brush = accentBrush, blendMode = BlendMode.SrcIn)
                    }
                },
            color = Color.White,
            trackColor = Color.Transparent,
            strokeCap = strokeCap
        )
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap
        )
    }
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalKitsugiAccent.current,
    trackColor: Color = KitsugiColors.SurfaceStrong,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
) {
    val accentBrush = resolveCurrentAccentBrush(color)
    if (accentBrush != null) {
        val trackBrush = resolveCurrentAccentBrush(trackColor)
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = modifier
                .drawBehind {
                    val cr = CornerRadius(size.height / 2f)
                    if (trackBrush != null) {
                        drawRoundRect(brush = trackBrush, cornerRadius = cr)
                    } else if (trackColor != Color.Transparent) {
                        drawRoundRect(color = trackColor, cornerRadius = cr)
                    }
                    val f = progress().coerceIn(0f, 1f)
                    if (f > 0f) {
                        drawRoundRect(
                            brush = accentBrush,
                            size = Size(size.width * f, size.height),
                            cornerRadius = cr
                        )
                    }
                },
            color = Color.Transparent,
            trackColor = Color.Transparent,
            strokeCap = strokeCap
        )
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. TabRow & ScrollableTabRow (Gradyan Sekme Çizgisi)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = LocalKitsugiAccent.current,
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit = @Composable { tabPositions ->
        if (selectedTabIndex in tabPositions.indices) {
            Box(
                Modifier
                    .tabIndicatorOffset(tabPositions[selectedTabIndex])
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(LocalKitsugiAccentBrush.current)
            )
        }
    },
    divider: @Composable () -> Unit = @Composable {
        HorizontalDivider(color = KitsugiColors.Border)
    },
    tabs: @Composable () -> Unit
) {
    androidx.compose.material3.TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        indicator = indicator,
        divider = divider,
        tabs = tabs
    )
}

@Composable
fun ScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = LocalKitsugiAccent.current,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit = @Composable { tabPositions ->
        if (selectedTabIndex in tabPositions.indices) {
            Box(
                Modifier
                    .tabIndicatorOffset(tabPositions[selectedTabIndex])
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(LocalKitsugiAccentBrush.current)
            )
        }
    },
    divider: @Composable () -> Unit = @Composable {
        HorizontalDivider(color = KitsugiColors.Border)
    },
    tabs: @Composable () -> Unit
) {
    androidx.compose.material3.ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        edgePadding = edgePadding,
        indicator = indicator,
        divider = divider,
        tabs = tabs
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. FilterChip (Gradyan Seçili Yüzey, Kenarlık & Yazı)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val isGradient = LocalKitsugiAccent2.current != null
    val accent = LocalKitsugiAccent.current

    if (selected && isGradient) {
        val bgBrush = accentBrushWithAlpha(0.22f)
        val borderBrush = accentBrushWithAlpha(0.65f)
        androidx.compose.material3.FilterChip(
            selected = true,
            onClick = onClick,
            label = label,
            modifier = modifier
                .clip(shape)
                .background(bgBrush, shape),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                selectedContainerColor = Color.Transparent,
                labelColor = accent,
                selectedLabelColor = accent,
                iconColor = accent,
                selectedLeadingIconColor = accent
            ),
            elevation = elevation,
            border = BorderStroke(1.dp, borderBrush),
            interactionSource = interactionSource
        )
    } else {
        androidx.compose.material3.FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border ?: FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
            interactionSource = interactionSource
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Button, FloatingActionButton & ExtendedFloatingActionButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LocalKitsugiAccent.current,
        contentColor = LocalKitsugiOnAccent.current
    ),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val onAccent = LocalKitsugiOnAccent.current
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    val isAccentFill = enabled && (container == Color.Unspecified || container.isSameAccentRgb(accent))
    val accentBrush = if (isAccentFill) LocalKitsugiAccentBrush.current else null

    if (accentBrush != null) {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier
                .clip(shape)
                .background(accentBrush, shape),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = onAccent,
                disabledContainerColor = KitsugiColors.SurfaceStrong,
                disabledContentColor = KitsugiColors.TextMuted
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        ) {
            CompositionLocalProvider(
                LocalContentColor provides onAccent,
                LocalInsideAccentSurface provides true
            ) {
                content()
            }
        }
    } else {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = LocalKitsugiAccent.current,
    contentColor: Color = LocalKitsugiOnAccent.current,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val onAccent = LocalKitsugiOnAccent.current
    val isAccentFill = containerColor == Color.Unspecified || containerColor.isSameAccentRgb(accent)

    if (isAccentFill) {
        androidx.compose.material3.FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .clip(shape)
                .background(LocalKitsugiAccentBrush.current, shape),
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = onAccent,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            interactionSource = interactionSource
        ) {
            CompositionLocalProvider(
                LocalContentColor provides onAccent,
                LocalInsideAccentSurface provides true
            ) {
                content()
            }
        }
    } else {
        androidx.compose.material3.FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content
        )
    }
}
