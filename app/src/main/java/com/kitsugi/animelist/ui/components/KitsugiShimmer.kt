package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.theme.LocalIsTv

/**
 * Paylaşımlı shimmer fırçası. DEĞERİ SABİTTİR (animasyon içermez): tüketiciler
 * yalnızca renk paletini buradan alır. Tarama (sweep) hareketi [LocalShimmerOffset]
 * üzerinden ÇİZİM kapsamında uygulanır.
 */
val LocalShimmerBrush: ProvidableCompositionLocal<Brush?> = compositionLocalOf { null }

/**
 * Paylaşımlı shimmer tarama konumu (0..1). BİLİNÇLİ OLARAK `State<Float>`:
 *
 * SORUN (eski davranış): fırça, animasyon değeri okunarak HER KAREDE yeniden
 * üretiliyor ve `CompositionLocalProvider` ile aşağı veriliyordu. Her karede
 * değişen bir CompositionLocal değeri, o değeri okuyan TÜM iskelet bileşenlerini
 * her karede yeniden kompoze ediyor; ayrıca her yeni `Brush` örneği kendi
 * `LinearGradient` shader'ını yeniden üretiyordu (ShaderBrush önbelleği instance
 * başına çalışır). Cihaz ölçümünde bu, saniyede ~500.000 nesne / saniyede ~15 MB
 * çöp üreten sürekli bir GC fırtınası ve %24 janky kare demekti.
 *
 * ÇÖZÜM: animasyon değeri `drawBehind` içinde okunur → yalnızca YENİDEN ÇİZİM
 * (recomposition yok, yeni fırça/shader yok). Bu değer kompozisyonda OKUNMAMALIDIR.
 */
val LocalShimmerOffset: ProvidableCompositionLocal<State<Float>?> = compositionLocalOf { null }

// ─── Merkezi Shimmer Paleti ────────────────────────────────────────────────
// KitsugiMobile HomeSkeletonLoading.kt'den uyarlama:
// 1200ms süren sağa doğru kayan lineer gradyan efekti.
// Palet dosya düzeyinde sabittir: `remember` anahtarının her kompozisyonda
// değişip yeni fırça üretmesini engeller.

private val SHIMMER_GRADIENT_END_PX = 1300f

@Composable
private fun kitsugiShimmerColors(): List<Color> = listOf(
    KitsugiColors.SurfaceSoft,
    KitsugiColors.SurfaceStrong.copy(alpha = 0.72f),
    // A restrained center highlight keeps the sweep visible in both dark and AMOLED themes.
    KitsugiColors.TextMuted.copy(alpha = 0.22f),
    KitsugiColors.SurfaceStrong.copy(alpha = 0.72f),
    KitsugiColors.SurfaceSoft,
)

/**
 * Statik (animasyonsuz) shimmer fırçası. Tüm çağrılarda AYNI örnek döner —
 * `CompositionLocal` değeri ve `Modifier.background(brush)` girdisi kararlı kalır,
 * dolayısıyla tüketici bileşenler gereksiz yeniden kompoze olmaz.
 */
@Composable
fun rememberKitsugiShimmerBrush(): Brush {
    val colors = kitsugiShimmerColors()
    return remember(colors) {
        Brush.linearGradient(
            colors = colors,
            start = Offset(0f, 0f),
            end = Offset(SHIMMER_GRADIENT_END_PX, 0f),
        )
    }
}

/**
 * Tarama konumunu (0..1) üreten tek animasyon saati. Değer YALNIZCA çizim
 * kapsamında okunmalıdır; kompozisyonda okunursa her karede recomposition olur.
 * Birden fazla iskelet aynı ağaçtaysa [KitsugiShimmerProvider] tek örneği paylaştırır.
 */
@Composable
fun rememberKitsugiShimmerOffsetState(): State<Float> {
    val transition = rememberInfiniteTransition(label = "KitsugiShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )
}

// ─── Temel Shimmer Blok ────────────────────────────────────────────────────
// Belirli boyutlarda animasyonlu placeholder blok.
//
// PERF: blok, animasyon değerini çizim kapsamında okur. Bu sayede her karede
// yalnızca yeniden çizim olur; kompozisyon ve modifier zinciri sabit kalır.
// (TV ana sayfasındaki `placeholderCardShimmer` ile aynı yaklaşım.)

@Composable
fun KitsugiShimmerBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val provided = LocalShimmerOffset.current
    // Provider yoksa blok kendi saatini kurar (geriye dönük uyumluluk).
    val ownOffset = if (provided == null) rememberKitsugiShimmerOffsetState() else null
    val offset = provided ?: ownOffset
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val baseColor = KitsugiColors.SurfaceSoft
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                // Always paint a stable placeholder surface first. Previously the entire
                // placeholder was translated off-canvas for much of each cycle, so cards
                // appeared as empty dark blocks between shimmer passes.
                drawRect(color = baseColor)
                if (offset != null) {
                    // `.value` yalnızca burada okunur → yalnızca yeniden çizim.
                    // One component-width of travel keeps the sweep intersecting the shape
                    // for the whole cycle, regardless of portrait/landscape constraints.
                    val sweep = offset.value
                    val shift = sweep * size.width * 2f - size.width
                    translate(left = shift) { drawRect(brush = brush) }
                } else {
                    drawRect(brush = brush)
                }
            },
    )
}

// ─── Medya Kartı Shimmer ──────────────────────────────────────────────────
// KitsugiHorizontalMediaSection'daki SkeletonMediaCard'ın animasyonlu versiyonu.

@Composable
fun KitsugiShimmerMediaCard(
    brush: Brush = rememberKitsugiShimmerBrush(),
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(14.dp),
    ) {
        // Poster alanı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            cornerRadius = 20.dp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Başlık satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp),
            cornerRadius = 999.dp,
        )

        Spacer(modifier = Modifier.height(7.dp))

        // Alt başlık satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(11.dp),
            cornerRadius = 999.dp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Puan / meta satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .height(10.dp),
            cornerRadius = 999.dp,
        )
    }
}

// ─── Yatay Shimmer Satırı ─────────────────────────────────────────────────────────────
// ExploreScreen'deki her KitsugiHorizontalMediaSection'ın yükleme durumu.

/**
 * Shimmer içeren birden fazla bileşen aynı kompozisyon ağacında yer alıyorsa
 * bu provider ile tek bir InfiniteTransition paylaşılır — belirgin performans gain'i.
 *
 * [active] false iken sonsuz shimmer animasyonu HİÇ OLUŞTURULMAZ: içerik
 * yüklendikten sonra placeholder kalmadığında bile transition'ın her karede
 * çalışmaya devam etmesi (sayfanın sürekli yeniden kompoze edilmesi ve boşuna
 * pil/CPU tüketimi) önlenir. Pasif durumda statik fırça sağlanır; shimmer
 * tüketicileri yine çizilebilir ama animasyon saatini ayakta tutmaz.
 */
@Composable
fun KitsugiShimmerProvider(
    active: Boolean = true,
    content: @Composable () -> Unit
) {
    // ÖNEMLİ 1: `if` yalnızca ANİMASYON SAATİNİ seçer; içerik (`content`) `if` dalının
    // DIŞINDA kalır. Eski sürümde `if/else` içinde iki ayrı CompositionLocalProvider vardı →
    // `active` her değiştiğinde (yükleme başladı/bitti, pull-to-refresh, arama yükleniyor) tüm
    // içerik ağacı söküp baştan kuruluyordu: hero pager durumu sıfırlanıyor, tüm raflar yeniden
    // kompoze oluyor ve kaydırma sırasında takılma/sıçrama üretiyordu.
    //
    // ÖNEMLİ 2: Aşağı verilen DEĞERLER artık kararlı (sabit fırça örneği + sabit State nesnesi).
    // Eskiden fırça, animasyon değeri okunarak her karede yeniden üretiliyordu; bu hem tüm
    // iskelet bileşenlerini her karede yeniden kompoze ediyor hem de her karede yeni bir
    // `LinearGradient` shader tahsisi yapıyordu (ölçülen GC fırtınasının kaynağı).
    // Tarama hareketi artık `KitsugiShimmerBlock` içinde `drawBehind` ile uygulanır.
    val brush = rememberKitsugiShimmerBrush()
    val offset: State<Float>? = if (active) rememberKitsugiShimmerOffsetState() else null
    CompositionLocalProvider(
        LocalShimmerBrush provides brush,
        LocalShimmerOffset provides offset,
        content = content
    )
}

@Composable
fun KitsugiShimmerMediaRow(
    cardCount: Int = 5,
) {
    // LocalShimmerBrush varsa paylaş, yoksa kendi transition'unu oluştur
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cardCount) {
            KitsugiShimmerMediaCard(brush = brush)
        }
    }
}

/**
 * Arama/Keşfet yatay raflarında gerçek KitsugiExploreMediaCard düzenine uyan shimmer.
 * Arama ekranı portrede poster kartı, yatayda poster + metin kartı kullandığından
 * placeholder ölçülerini de aynı konfigürasyona göre seçer.
 */
@Composable
fun KitsugiShimmerResponsiveMediaRow(
    cardCount: Int = 5,
    cardWidth: Dp,
    isLandscape: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    cardSpacing: Dp = 12.dp,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(cardSpacing),
    ) {
        items(
            count = cardCount.coerceAtLeast(0),
            key = { index -> "responsive_shimmer_media_$index" },
            contentType = { "responsive_shimmer_media_card" },
        ) {
            KitsugiShimmerResponsiveMediaCard(
                brush = brush,
                cardWidth = cardWidth,
                isLandscape = isLandscape,
            )
        }
    }
}

@Composable
internal fun KitsugiShimmerResponsiveMediaCard(
    brush: Brush,
    cardWidth: Dp,
    isLandscape: Boolean,
) {
    val isTv = LocalIsTv.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardShape = RoundedCornerShape(if (isTv) 12.dp else 24.dp)

    when {
        isTv -> {
            // TV kartları her zaman dikey poster düzeninde kalır.
            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(cardShape)
                    .background(KitsugiColors.Surface)
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KitsugiTvTokens.Cards.posterHeight),
                    cornerRadius = 12.dp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EXPLORE_CARD_TV_TEXT_HEIGHT)
                        .padding(
                            horizontal = KitsugiTvTokens.Spacing.sm,
                            vertical = KitsugiTvTokens.Spacing.sm,
                        ),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.86f).height(12.dp),
                        cornerRadius = 999.dp,
                    )
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.58f).height(9.dp),
                        cornerRadius = 999.dp,
                    )
                }
            }
        }

        isLandscape -> {
            // Keep the skeleton's outer dimensions and poster size in sync with the
            // landscape branch of KitsugiExploreMediaCard.
            val landscapeRowHeight = exploreLandscapePosterHeight(screenWidthDp)
            val posterWidth = exploreLandscapePosterWidth(screenWidthDp)
            val posterHeight = landscapeRowHeight

            Row(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(cardShape)
                    .background(KitsugiColors.Surface)
                    .padding(12.dp)
                    .height(landscapeRowHeight),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(width = posterWidth, height = posterHeight),
                    cornerRadius = 16.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.95f).height(15.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.70f).height(12.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(34.dp).height(10.dp),
                            cornerRadius = 999.dp,
                        )
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(46.dp).height(10.dp),
                            cornerRadius = 999.dp,
                        )
                    }
                }
            }
        }

        else -> {
            // Portrede, gerçek kartın 2:3 kapak oranı ve sabit metin alanı korunur.
            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(cardShape)
                    .background(KitsugiColors.Surface)
                    .padding(14.dp),
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    cornerRadius = 20.dp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.92f).height(16.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.64f).height(16.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.82f).height(11.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.52f).height(11.dp),
                        cornerRadius = 999.dp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.42f).height(10.dp),
                        cornerRadius = 999.dp,
                    )
                }
            }
        }
    }
}

// ─── Hero Banner Shimmer ──────────────────────────────────────────────────
// ExploreScreen'de hero verileri yüklenirken gösterilir.

@Composable
fun KitsugiShimmerHeroSection(
    heroHeight: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || screenWidthDp > screenHeightDp
    val isTablet = minOf(screenWidthDp, screenHeightDp) >= 600
    val isTv = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)

    // Match the real carousel exactly: admitting the first verified image must not
    // resize the first LazyColumn item and displace the shelves underneath it.
    // Gerçek vitrinle AYNI yerleşim politikası kullanılır (HeroVitrinLayout).
    val layout = heroVitrinLayout(
        screenWidthDp = screenWidthDp.toFloat(),
        screenHeightDp = screenHeightDp.toFloat(),
        isLandscape = isLandscape,
        isTablet = isTablet,
        isTv = isTv,
        fontScale = fontScale
    )
    val finalHeight = if (heroHeight != Dp.Unspecified) heroHeight else layout.bandHeightDp.dp
    val isStacked = layout.mode == HeroVitrinMode.STACKED

    // LocalShimmerBrush varsa paylaş, yoksa kendi transition'unu oluştur
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(finalHeight)
            .background(brush),
    ) {
        // Dikey telefonda üstteki görsel şeridi gerçek vitrinle aynı yükseklikte.
        if (isStacked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.artworkHeightDp.dp)
                    .background(brush)
            )
        }
        // Metin yuvası placeholder'ları: gerçek vitrinin satırlarıyla aynı sırada.
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Kaynak rozeti satırı
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(60.dp).height(10.dp),
                cornerRadius = 999.dp,
            )
            // Başlık / logo yuvası
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth(0.78f).height(40.dp),
                cornerRadius = 8.dp,
            )
            // Alt başlık
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth(0.55f).height(16.dp),
                cornerRadius = 8.dp,
            )
            // Meta satırı
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth(0.42f).height(12.dp),
                cornerRadius = 6.dp,
            )
        }
    }
}

// ─── Detay Sayfası Başlık Shimmer ─────────────────────────────────────────
// ApiResultDetailPage / MediaEntryDetailPage yüklenirken gösterilir.

@Composable
fun KitsugiShimmerDetailHeader(
    modifier: Modifier = Modifier,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // Cover image placeholder
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .width(130.dp)
                .height(190.dp),
            cornerRadius = 18.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Başlık
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth(0.85f).height(22.dp),
            cornerRadius = 8.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Alt başlık
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth(0.60f).height(14.dp),
            cornerRadius = 999.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Meta satırı
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Açıklama satırları
        repeat(4) { i ->
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth(if (i == 3) 0.55f else 1f)
                    .height(13.dp),
                cornerRadius = 999.dp,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// ─── Karakter / Staff Avatar Satırı Shimmer ───────────────────────────────
// Karakter ve oyuncu bölümleri için yükleme durumu.

@Composable
fun KitsugiShimmerAvatarRow(
    avatarCount: Int = 6,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(avatarCount) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(62.dp),
                    cornerRadius = 999.dp, // daire
                )
                Spacer(modifier = Modifier.height(6.dp))
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(56.dp).height(10.dp),
                    cornerRadius = 999.dp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(44.dp).height(8.dp),
                    cornerRadius = 999.dp,
                )
            }
        }
    }
}

// ─── Arama Sonuçları Shimmer Listesi ───────────────────────────────────────
// SearchScreen'deki arama sonuçları yüklenirken dikey liste halinde gösterilir.
@Composable
fun KitsugiShimmerSearchResultList(
    itemCount: Int = 5,
    isPerson: Boolean = false,
    showAddButton: Boolean = true,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    val isTv = com.kitsugi.animelist.ui.theme.LocalIsTv.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isTv) com.kitsugi.animelist.ui.theme.KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(16.dp))
                    .background(KitsugiColors.Surface)
                    .padding(if (isTv) 8.dp else if (isPerson) 12.dp else 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isPerson) 14.dp else 12.dp)
            ) {
                val thumbWidth = if (isPerson) (if (isTv) 64.dp else 56.dp)
                    else if (isTv) KitsugiTvTokens.Cards.searchThumbWidth else 56.dp
                val thumbHeight = if (isPerson) thumbWidth
                    else if (isTv) KitsugiTvTokens.Cards.searchThumbHeight else 80.dp
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(width = thumbWidth, height = thumbHeight),
                    cornerRadius = if (isPerson) 14.dp else if (isTv) 12.dp else 10.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (isPerson) 4.dp else 6.dp)
                ) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.7f).height(if (isPerson) 24.dp else 14.dp),
                        cornerRadius = 999.dp
                    )
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.4f).height(if (isPerson) 16.dp else 10.dp),
                        cornerRadius = 999.dp
                    )
                    if (isPerson) {
                        KitsugiShimmerBlock(brush, Modifier.width(65.dp).height(20.dp), 6.dp)
                    } else Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(36.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(36.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(44.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                    }
                }
                if (!isPerson && showAddButton) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 12.dp
                    )
                }
            }
        }
    }
}

