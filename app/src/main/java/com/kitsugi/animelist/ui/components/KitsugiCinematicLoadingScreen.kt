package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.PlatformErrorCode

@Composable
fun KitsugiCinematicLoadingScreen(
    title: String,
    imageUrl: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    isAdult: Boolean = false,
    blurAdultMedia: Boolean = false,
    source: String? = null,
    /** Company marks (for example TMDB studio logos) should be fitted instead of cropped. */
    isLogoArtwork: Boolean = false,
    /** Son detay isteğinin kaynak hatası (`KTS-…`); yeniden denemede de görünmeye devam eder. */
    platformErrorCode: PlatformErrorCode? = null,
) {
    val accentColor = LocalKitsugiAccent.current

    // Keep the existing slow pulse, but size the foreground content for short landscape screens.
    val infiniteTransition = rememberInfiniteTransition(label = "cinematic_loading")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        val compactLayout = maxHeight < 560.dp
        val artworkWidth = when {
            isLogoArtwork -> if (compactLayout) 108.dp else 132.dp
            compactLayout -> 94.dp
            else -> 130.dp
        }
        val artworkHeight = when {
            isLogoArtwork -> if (compactLayout) 68.dp else 92.dp
            compactLayout -> 132.dp
            else -> 190.dp
        }
        val artworkGap = if (compactLayout) 14.dp else 24.dp
        val titleGap = if (compactLayout) 14.dp else 20.dp
        val logoHeight = if (compactLayout) 50.dp else 65.dp
        val loaderSize = if (compactLayout) 44.dp else 52.dp
        val loaderGap = if (compactLayout) 12.dp else 18.dp

        // Fullscreen image backdrop: use the actual artwork passed from the clicked detail item.
        if (!imageUrl.isNullOrBlank()) {
            KitsugiNsfwImage(
                model = imageUrl,
                contentDescription = null,
                isAdult = isAdult,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scalePulse * 1.05f
                        scaleY = scalePulse * 1.05f
                        alpha = if (isLogoArtwork) 0.24f else 0.35f
                    },
                // Avoid adding another unconditional RenderEffect blur layer here. Adult-content
                // blur still follows the app setting inside KitsugiNsfwImage.
                contentScale = ContentScale.Crop,
                blurRadius = 16.dp,
                blurAdultMedia = blurAdultMedia
            )
        }

        // Dark gradient keeps source, artwork and title readable over any provider image.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.8f),
                            KitsugiColors.Background
                        )
                    )
                )
        )

        // Back remains available while an API call is in flight, including on TV/D-pad devices.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .tvClickable(shape = RoundedCornerShape(99.dp)) { onBackClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = KitsugiColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.geri),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scalePulse
                            scaleY = scalePulse
                        }
                        .shadow(24.dp, shape = RoundedCornerShape(20.dp), spotColor = accentColor)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, KitsugiColors.Border.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .background(KitsugiColors.Surface)
                        .size(width = artworkWidth, height = artworkHeight)
                ) {
                    KitsugiNsfwImage(
                        model = imageUrl,
                        contentDescription = title,
                        isAdult = isAdult,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (isLogoArtwork) ContentScale.Fit else ContentScale.Crop,
                        blurAdultMedia = blurAdultMedia
                    )
                }
                Spacer(modifier = Modifier.height(artworkGap))
            }

            // Provider name + original logo (Bangumi, AniList, TMDB, Jikan/MAL, etc.).
            if (!source.isNullOrBlank()) {
                KitsugiSourceNamePill(source = source)
                Spacer(modifier = Modifier.height(if (compactLayout) 8.dp else 12.dp))
            }

            var logoFailed by remember(logoUrl) { mutableStateOf(false) }
            val showLogo = !logoUrl.isNullOrBlank() && !logoFailed
            if (showLogo) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(logoHeight),
                    onError = { logoFailed = true }
                )
                Spacer(modifier = Modifier.height(titleGap))
            } else if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
                Spacer(modifier = Modifier.height(titleGap))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                KitsugiPlasmaLoader(size = loaderSize)
                Spacer(modifier = Modifier.height(loaderGap))
                Text(
                    text = stringResource(R.string.yukleniyor),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            platformErrorCode?.let { code ->
                Spacer(modifier = Modifier.height(12.dp))
                PlatformErrorCodeChip(code = code)
            }
        }
    }
}
