package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Switch
import androidx.compose.material3.SwitchDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ---------------------------------------------------------------------------
//  Paylaşılan Sheet Satır Bileşeni
// ---------------------------------------------------------------------------
@Composable
internal fun SheetRow(
    icon: ImageVector,
    label: String,
    value: String = "",
    onValueChange: ((String) -> Unit)? = null,
    valuePlaceholder: String = "",
    valueSuffix: String = "",
    onIncrement: (() -> Unit)? = null,
    onDecrement: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    switchChecked: Boolean? = null,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(rowShape)
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.25f), rowShape)
            // Do not install a disabled click target over child inputs. In particular,
            // that parent pointer node can make the tiny progress field difficult to focus.
            .then(
                if (onClick != null) {
                    Modifier.tvClickable(shape = rowShape) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            com.kitsugi.animelist.ui.theme.gradient.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (onValueChange != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                onValueChange(newValue)
                            }
                        },
                        // Give the editable number a comfortable hit target so a user can
                        // tap the current count and replace it directly (instead of
                        // repeatedly pressing the +/- buttons).
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 32.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = KitsugiColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        cursorBrush = SolidColor(accentColor),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (value.isEmpty() && valuePlaceholder.isNotEmpty()) {
                                    Text(
                                        text = valuePlaceholder,
                                        color = KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (valueSuffix.isNotEmpty()) {
                        Text(
                            text = valueSuffix,
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (value.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onClearClick != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.SurfaceSoft)
                                .tvClickable(shape = RoundedCornerShape(6.dp)) { onClearClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            com.kitsugi.animelist.ui.theme.gradient.Icon(
                                imageVector = Icons.Close,
                                contentDescription = stringResource(R.string.temizle),
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (onIncrement != null && onDecrement != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onDecrement),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onIncrement),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = KitsugiColors.Background,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (switchChecked != null && onSwitchCheckedChange != null) {
            Switch(
                checked = switchChecked,
                onCheckedChange = onSwitchCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KitsugiColors.Background,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = KitsugiColors.TextSecondary,
                    uncheckedTrackColor = KitsugiColors.SurfaceSoft
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Paylaşılan Metin Alanı Bileşeni
// ---------------------------------------------------------------------------
@Composable
internal fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        label = {
            Text(text = label, color = KitsugiColors.TextSecondary)
        },
        placeholder = {
            Text(text = placeholder, color = KitsugiColors.TextMuted)
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = KitsugiColors.TextPrimary,
            unfocusedTextColor = KitsugiColors.TextPrimary,
            focusedContainerColor = KitsugiColors.SurfaceSoft,
            unfocusedContainerColor = KitsugiColors.SurfaceSoft,
            focusedIndicatorColor = accentColor,
            unfocusedIndicatorColor = KitsugiColors.Border,
            cursorColor = accentColor
        )
    )
}

// ---------------------------------------------------------------------------
//  Tarih Doğrulama Yardımcısı
// ---------------------------------------------------------------------------
internal fun String.isValidDateOrBlank(): Boolean {
    if (isBlank()) return true

    val regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    if (!matches(regex)) return false

    val parts = split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return false
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return false

    return year in 1900..2200 &&
            month in 1..12 &&
            day in 1..31
}

// ---------------------------------------------------------------------------
//  Paylaşılan Sayısal Giriş İletişim Kutusu
// ---------------------------------------------------------------------------
@Composable
internal fun KitsugiNumericInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val accentColor = LocalKitsugiAccent.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.inline_enter_the_new_value_e7185a9),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            textValue = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedContainerColor = KitsugiColors.SurfaceSoft,
                        unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = KitsugiColors.Border,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(textValue)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.kaydet), color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.iptal), color = KitsugiColors.TextSecondary)
            }
        },
        containerColor = KitsugiColors.Surface,
        shape = RoundedCornerShape(24.dp)
    )
}
