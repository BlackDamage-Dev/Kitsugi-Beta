package com.kitsugi.animelist.ui.screens.manga.components

/** Compose the existing preset with saturation, hue and contrast; alpha is unchanged. */
internal fun readerColorMatrix(preset: String, saturationPercent: Int, contrastPercent: Int, hueDegrees: Int = 0): FloatArray? {
    val saturation = saturationPercent.coerceIn(0, 200) / 100f
    val contrast = contrastPercent.coerceIn(50, 200) / 100f
    val degrees = hueDegrees.coerceIn(-180, 180)
    if (preset == "Normal" && saturation == 1f && contrast == 1f && degrees == 0) return null
    val base = when (preset) {
        "Grayscale" -> floatArrayOf(
            .2126f,.7152f,.0722f,0f,0f, .2126f,.7152f,.0722f,0f,0f,
            .2126f,.7152f,.0722f,0f,0f, 0f,0f,0f,1f,0f)
        "Sepia" -> floatArrayOf(
            .393f,.769f,.189f,0f,0f, .349f,.686f,.168f,0f,0f,
            .272f,.534f,.131f,0f,0f, 0f,0f,0f,1f,0f)
        "Invert" -> floatArrayOf(
            -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f,
            0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)
        else -> floatArrayOf(
            1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)
    }
    val weights = floatArrayOf(.2126f, .7152f, .0722f)
    val saturated = FloatArray(20)
    for (row in 0..2) {
        for (col in 0..4) {
            var value = 0f
            for (channel in 0..2) {
                val mix = (1f - saturation) * weights[channel] + if (channel == row) saturation else 0f
                value += mix * base[channel * 5 + col]
            }
            saturated[row * 5 + col] = value
        }
    }
    val c = kotlin.math.cos(Math.toRadians(degrees.toDouble())).toFloat()
    val s = kotlin.math.sin(Math.toRadians(degrees.toDouble())).toFloat()
    val hue = if (degrees == 0) floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f) else floatArrayOf(
        .213f + .787f*c - .213f*s, .715f - .715f*c - .715f*s, .072f - .072f*c + .928f*s,
        .213f - .213f*c + .143f*s, .715f + .285f*c + .140f*s, .072f - .072f*c - .283f*s,
        .213f - .213f*c - .787f*s, .715f - .715f*c + .715f*s, .072f + .928f*c + .072f*s)
    val out = FloatArray(20)
    for (row in 0..2) for (col in 0..4) {
        var value = 0f
        for (channel in 0..2) value += hue[row * 3 + channel] * saturated[channel * 5 + col]
        out[row * 5 + col] = contrast * value + if (col == 4) 128f * (1f - contrast) else 0f
    }
    out[18] = 1f
    return out
}
