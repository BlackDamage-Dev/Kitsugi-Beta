package com.kitsugi.animelist.ui.screens.fullscreen.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiSheets
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.ControlsButton
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.CurrentChapter
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment

@Composable
fun BottomLeftPlayerControls(
    playbackSpeed: Float,
    currentChapter: IndexedSegment?,
    onLockControls: () -> Unit,
    onCycleRotation: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onOpenSheet: (KitsugiSheets) -> Unit,
    showChapters: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlsButton(
            icon = Icons.LockOpen,
            onClick = onLockControls,
        )
        ControlsButton(
            icon = Icons.ScreenRotation,
            onClick = onCycleRotation,
        )
        ControlsButton(
            text = "%.2fx".format(playbackSpeed),
            onClick = {
                val newSpeed = if (playbackSpeed >= 2f) 0.25f else playbackSpeed + 0.25f
                onPlaybackSpeedChange(newSpeed)
            },
            onLongClick = { onOpenSheet(KitsugiSheets.PlaybackSpeed) },
        )
        // ── ÇÖKME (NPE) KÖK NEDENİ ────────────────────────────────────────────
        // Eski kod: `visible = currentChapter != null` + içerikte `currentChapter!!`
        // AnimatedVisibility çıkış animasyonu boyunca içeriği kompozisyonda TUTAR.
        // Bölüm değişimi / bölüm listesi yeniden kurulurken (updateChapters) veya
        // konum güncellenirken (onPositionChanged) `currentChapter` null'a düştüğünde
        // içerik lambda'sı null değerle yeniden besteleniyordu → `!!` NullPointerException
        // fırlatıp uygulamayı düşürüyordu (video izlerken görülen çökme).
        // Çözüm: gösterilecek değeri yerel değişkene alıp null güvenli biçimde kullanmak.
        // ────────────────────────────────────────────────────────────────────────
        val chapterToShow = currentChapter
        AnimatedVisibility(
            visible = chapterToShow != null && showChapters,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (chapterToShow != null) {
                CurrentChapter(
                    chapter = chapterToShow,
                    onClick = { onOpenSheet(KitsugiSheets.Chapters) },
                )
            }
        }
    }
}
