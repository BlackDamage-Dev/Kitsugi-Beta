package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.flow.collectLatest
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Sade, tema uyumlu (koyu/açık) arama çubuğu.
 *
 * Metin girişi için Compose'un yeniden yazılmış durum tabanlı
 * [BasicTextField]'ını kullanır: tek satırlık metin taştığında parmakla
 * sağa/sola kaydırarak gezinmeye (pan) izin verir, böylece uzun
 * aramalarda metni silmeden başına/sonuna ulaşılabilir.
 */
@Composable
fun KitsugiCosmicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tüm platformlarda ara...",
    leadingContent: (@Composable () -> Unit)? = null,
    onFilterClick: (() -> Unit)? = null,
    isFilterActive: Boolean = false,
    height: Dp = 56.dp,
    debounceMillis: Long = 0L,
    focusRequester: FocusRequester? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = LocalKitsugiAccent.current

    // Durum tabanlı metin alanı: yatay kaydırma (pan) desteği sağlar.
    val textFieldState = remember { TextFieldState(query) }
    val latestQuery by rememberUpdatedState(query)
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)
    // Ebeveyn yeniden kompozisyonu tuş vuruşlarının gerisinden geldiğinde eski `query`
    // değerinin kullanıcının yazdığı/sildiği metni geri ezmesini (feedback loop) önler.
    var lastLocalText by remember { mutableStateOf(query) }

    val hasText by remember {
        androidx.compose.runtime.derivedStateOf { textFieldState.text.isNotEmpty() }
    }
    val hasNonBlankText by remember {
        androidx.compose.runtime.derivedStateOf { textFieldState.text.isNotBlank() }
    }

    // Yalnızca dışarıdan programatik olarak gelen (geçmiş seçimi, geri tuşu sıfırlaması vb.)
    // gerçek değişiklikleri metin alanına yansıt.
    LaunchedEffect(query) {
        val currentFieldText = textFieldState.text.toString()
        if (query != lastLocalText && query != currentFieldText) {
            lastLocalText = query
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }

    // İçeride yazılan metni dışarıya aktar: boşaltma ("") her zaman 0 ms anında iletilir;
    // art arda yazma/silme sırasında isteğe bağlı debounce ara kare yığılmasını keser.
    LaunchedEffect(debounceMillis) {
        snapshotFlow { textFieldState.text.toString() }
            .collectLatest { current ->
                lastLocalText = current
                if (current == latestQuery) return@collectLatest
                if (debounceMillis > 0L && current.isNotEmpty()) {
                    kotlinx.coroutines.delay(debounceMillis)
                }
                if (current != latestQuery) {
                    latestOnQueryChange(current)
                }
            }
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .onFocusChanged { isFocused = it.hasFocus }
            .background(KitsugiColors.Surface, shape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) accentColor else KitsugiColors.Border,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                Icon(
                    imageVector = Icons.Search,
                    contentDescription = stringResource(R.string.ara),
                    tint = if (isFocused) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            BasicTextField(
                state = textFieldState,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(vertical = 4.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(accentColor),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = {
                    onSearch()
                    keyboardController?.hide()
                },
                decorator = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (textFieldState.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Arama Butonu (onay) — aramayı başlatan tek dokunmatik yol.
            // Klavyesi olmayan/donanım klavyesi "Ara" tuşu göndermeyen cihazlarda
            // da arama tetiklenebilsin diye metin doluyken görünür.
            AnimatedVisibility(
                visible = hasNonBlankText,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        onSearch()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Search,
                        contentDescription = stringResource(R.string.ara),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Temizle Butonu ("X")
            AnimatedVisibility(
                visible = hasText,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        lastLocalText = ""
                        textFieldState.clearText()
                        onClearQuery()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.temizle),
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onFilterClick != null) {
                SimpleFilterIconButton(
                    onClick = onFilterClick,
                    isActive = isFilterActive
                )
            }
        }
    }
}

/**
 * Sade, tema uyumlu filtre butonu.
 */
@Composable
fun SimpleFilterIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val filterShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(filterShape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft,
                filterShape
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accentColor else KitsugiColors.Border,
                shape = filterShape
            )
            .clip(filterShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Tune,
            contentDescription = stringResource(R.string.filtreler),
            tint = if (isActive) accentColor else KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Arama çubuğunun yanındaki kare yardımcı buton (Eklenti Portalı vb.).
 * Sade, tema uyumlu yüzey + kenarlık.
 */
@Composable
fun CosmicCompanionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isActive: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(KitsugiColors.Surface, shape)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accentColor else KitsugiColors.Border,
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

// Geriye uyumluluk: eski isim hâlâ referanslanıyorsa diye basit yönlendirme.
@Composable
fun CosmicFilterIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    SimpleFilterIconButton(onClick = onClick, isActive = isActive, modifier = modifier)
}
