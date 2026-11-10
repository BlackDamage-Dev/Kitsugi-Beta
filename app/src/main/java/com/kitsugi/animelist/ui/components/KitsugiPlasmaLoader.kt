package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renk döngüsünün kare başına yeniden kurulacak adım sayısı (kuantalama). */
private const val PlasmaColorSteps = 32f

/** Bu boyut ve altındaki kürelerde hafif (kompakt) çizim yolu kullanılır. */
private val PlasmaCompactBelow: Dp = 30.dp

/**
 * Kutunun en kısa kenarına oturan, ortalanmış TAM daire klipsi.
 *
 * `CircleShape` genişliği ve yüksekliği farklı bir kutuda elips üretir; yüklenme
 * göstergesi hiçbir durumda oval/kare görünmemeli. Bu şekil her zaman daire keser.
 */
private object PlasmaOrbShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val diameter = size.minDimension
        val path = Path().apply {
            addOval(
                Rect(
                    left = (size.width - diameter) / 2f,
                    top = (size.height - diameter) / 2f,
                    right = (size.width + diameter) / 2f,
                    bottom = (size.height + diameter) / 2f
                )
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * Uygulamanın ORTAK yüklenme göstergesi: organik sıvı lav/plazma küresi
 * (Amber Plasma Lava Orb).
 *
 * ## Neden her şey daire?
 * Eski sürümde gövde `drawRect` ile dolduruluyor ve gövde kenarı ayrıca
 * `Modifier.border(...)` ile çiziliyordu. Bazı çağrı noktalarında (oynatıcı ortası,
 * tam ekran yüklenme) kırpma katmanı devre dışı kaldığı için ekranda **turuncu
 * kare çerçeve + içinde daire** görünüyordu. Artık:
 * - dikdörtgen çizim YOK (`drawRect` kullanılmaz),
 * - kenarlık/çember YOK (`Modifier.border` kaldırıldı),
 * - tüm katmanlar `drawCircle` ile ve kürenin kendi yarıçapı içinde çizilir,
 * - kutu ayrıca daire şeklinde kırpılır (çift güvence).
 *
 * Böylece bileşen nerede kullanılırsa kullanılsın (oynatıcı, liste, sheet, buton,
 * pull-to-refresh, TV) yalnızca tek bir yuvarlak küre görünür.
 *
 * ## Boyut
 * Çizim her zaman ÖLÇÜLEN kutuya göre yapılır. Çağıran `Modifier.size(80.dp)`
 * verirse küre 80 dp'ye büyür; sadece `size` parametresi verilirse o kullanılır.
 * (Eskiden iki ayrı `size` modifier'ı üst üste biniyor ve kutu ile çizim farklı
 * boyutlarda kalıyordu.)
 *
 * @param modifier  Bileşenin dış modifier'ı (boyut verebilir).
 * @param size      Kürenin çap boyutu (varsayılan: 42.dp).
 * @param color     İsteğe bağlı birincil renk (null ise tema vurgu rengi).
 * @param secondaryColor İsteğe bağlı ikincil renk (null ise tema ikinci vurgu rengi).
 */
@Composable
fun KitsugiPlasmaLoader(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    color: Color? = null,
    secondaryColor: Color? = null
) {
    val baseOne = color ?: com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current
    val baseTwo = secondaryColor ?: com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2.current ?: Color(0xFFBE4A1D)

    val transition = rememberInfiniteTransition(label = "kitsugiPlasmaAnim")

    // 1. Ana Dönüş (2.0s loop)
    val mainRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mainRot"
    )

    // 2. Renk Döngüsü (6.0s loop)
    val colorPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorPhase"
    )

    // 3. Akışkan Çekirdek Nabzı (1.0s loop)
    val coreGlowPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // KARE BAŞINA churn önlemi: yavaş ilerleyen renk kayması 32 adıma kuantalanır,
    // gradyan renkleri her karede yeniden kurulmaz. Çekirdek nabzı sürekli kalır.
    val colorStep = (colorPhase * PlasmaColorSteps).roundToInt() / PlasmaColorSteps

    val c1 = remember(baseOne, colorStep) {
        interpolateColor(baseOne, Color(0xFFFF8A3D), colorStep)
    }
    val c2 = remember(baseTwo, colorStep) {
        interpolateColor(baseTwo, Color(0xFF8E1B00), colorStep)
    }
    val c3 = c1.copy(alpha = 0.50f)
    val c4 = c2.copy(alpha = 0.70f)
    val c5 = c1.copy(alpha = 0.42f)

    // Grafyenler kare başına yeniden üretilmesin: renk adımı değişmedikçe aynı
    // Brush nesneleri kullanılır (LMKD/GC kaynaklı çökmelerin önlemi).
    val bodyBrush = remember(c5, c4) {
        Brush.verticalGradient(colors = listOf(c5, c4))
    }
    val haloBrush = remember(c3, c4) {
        Brush.radialGradient(
            colors = listOf(c3, c4.copy(alpha = 0.35f), Color.Transparent)
        )
    }
    // Dikey gradyanlar varsayılan olarak çizim alanının tamamını (0..yükseklik)
    // kapsar; bu yüzden boyut bilgisine ihtiyaç duymadan önbelleklenebilirler.
    val highlightBrush = remember(c1) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.55f to Color.Transparent,
            1f to Color.Transparent
        )
    }
    val shadeBrush = remember(c2) {
        Brush.verticalGradient(colors = listOf(Color.Transparent, c2.copy(alpha = 0.45f)))
    }
    val lobeBrush = remember(c1, c2) {
        Brush.radialGradient(
            colors = listOf(c1.copy(alpha = 0.55f), c2.copy(alpha = 0.30f), Color.Transparent)
        )
    }
    val coreBrush = remember(c1, c2) {
        Brush.radialGradient(
            colors = listOf(c1, c2.copy(alpha = 0.70f), Color.Transparent)
        )
    }
    // Aktif merkez (beyaz-sıcak nokta) — sıvı lavın kaynama hissi.
    val hotCoreBrush = remember(c1) {
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.52f), c1.copy(alpha = 0.32f), Color.Transparent)
        )
    }
    // Küre kenarını koyulaştıran iç vinyet (3B hacim hissi). Merkez ve yarıçap
    // varsayılan bırakılır; Compose bunları çizim alanının merkezi ve
    // minDimension/2 olarak çözer — yani tam olarak kürenin kendisi.
    val vignetteBrush = remember {
        Brush.radialGradient(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.55f)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            // Küre dışına taşan hiçbir piksel görünmesin: kutu TAM daire olarak kırpılır.
            .clip(PlasmaOrbShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = this.size.minDimension
            if (diameter <= 0f) return@Canvas
            val radius = diameter / 2f
            val orbCenter = center
            val compact = diameter <= PlasmaCompactBelow.toPx()

            // ── 1. Gövde: tek, dolu daire (kare dolgu / çerçeve yok) ─────────────
            drawCircle(
                brush = bodyBrush,
                radius = radius,
                center = orbCenter
            )

            if (compact) {
                // ── Kompakt ışık küresi (rozet, satır içi gösterge, buton içi) ──
                // 30 dp altında metaball simülasyonu görsel olarak boğuluyor;
                // aynı kimlik üç daireyle korunur. ShaderBrush önbelleği bozulmasın diye
                // ölçekleme Canvas matrisinde yapılır (kare başına sıfır JNI Shader tahsisi).
                drawCircle(
                    brush = haloBrush,
                    radius = radius,
                    center = orbCenter
                )

                val compactCoreScale = (0.52f + 0.12f * (coreGlowPulse - 1f)).coerceAtLeast(0.1f)
                withTransform({
                    scale(scaleX = compactCoreScale, scaleY = compactCoreScale, pivot = orbCenter)
                }) {
                    drawCircle(
                        brush = coreBrush,
                        radius = radius,
                        center = orbCenter
                    )
                }

                val orbitRad = Math.toRadians(mainRotation.toDouble())
                drawCircle(
                    color = c1.copy(alpha = 0.90f),
                    radius = radius * 0.16f,
                    center = Offset(
                        (orbCenter.x + cos(orbitRad) * radius * 0.60f).toFloat(),
                        (orbCenter.y + sin(orbitRad) * radius * 0.60f).toFloat()
                    )
                )
                return@Canvas
            }

            // ── 2. Dönen plazma odakları (metaball simülasyonu) ─────────────────
            // Her odak önbellekli radyal fırçayla Canvas matrisi üzerinde ötelenip
            // ölçeklenerek çizilir; kare başına yeni JNI RadialGradient üretilmez.
            repeat(3) { index ->
                val direction = if (index % 2 == 0) 1f else -1f
                val angleDeg = mainRotation * direction + index * 120f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val orbitRadius = radius * 0.34f
                val dx = (cos(angleRad) * orbitRadius).toFloat()
                val dy = (sin(angleRad) * orbitRadius).toFloat()
                withTransform({
                    translate(left = dx, top = dy)
                    scale(scaleX = 0.72f, scaleY = 0.72f, pivot = orbCenter)
                }) {
                    drawCircle(
                        brush = lobeBrush,
                        radius = radius,
                        center = orbCenter,
                        blendMode = BlendMode.Screen
                    )
                }
            }

            // ── 3. Akkor çekirdek (nefes alan merkez) ────────────────────────────
            val coreScale = 0.48f * coreGlowPulse.coerceIn(0.8f, 1.2f)
            withTransform({
                scale(scaleX = coreScale, scaleY = coreScale, pivot = orbCenter)
            }) {
                drawCircle(
                    brush = coreBrush,
                    radius = radius,
                    center = orbCenter,
                    blendMode = BlendMode.Screen
                )
            }

            // ── 4. Beyaz-sıcak merkez (kaynama hissi) ───────────────────────────
            // Sadece daire içinde kalır; dışarıya ışıma taşmaz.
            withTransform({
                scale(scaleX = 0.17f, scaleY = 0.17f, pivot = orbCenter)
            }) {
                drawCircle(
                    brush = hotCoreBrush,
                    radius = radius,
                    center = orbCenter,
                    blendMode = BlendMode.Screen
                )
            }

            // ── 5. Cam küre hissi: iç highlight, alt gölge, kenar vinyeti ───────
            drawCircle(
                brush = highlightBrush,
                radius = radius,
                center = orbCenter
            )
            drawCircle(
                brush = shadeBrush,
                radius = radius,
                center = orbCenter
            )
            // Kenara doğru koyulaşma: küre hacimli görünür, kare algısı kırılır.
            drawCircle(
                brush = vignetteBrush,
                radius = radius,
                center = orbCenter
            )
        }
    }
}

/**
 * Renkler arasında yumuşak geçiş hesaplar (renk döngüsü interpolasyonu için).
 */
private fun interpolateColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}
