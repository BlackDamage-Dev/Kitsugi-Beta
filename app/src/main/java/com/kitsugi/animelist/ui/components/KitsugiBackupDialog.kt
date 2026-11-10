package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.local.MediaEntryBackup
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsugiExportBackupDialog(
    backupText: String,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val clipboardManager = LocalClipboardManager.current

    var copied by rememberSaveable {
        mutableStateOf(false)
    }

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current

    if (isTvDevice) {
        com.kitsugi.animelist.ui.tv.components.TvDialog(
            onDismiss = onDismiss,
            title = "Yedek metni",
            width = 520.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.asagidaki_json_metnini_kopyalayip_guvenli_bi),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                TextField(
                    value = backupText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .kitsugiVerticalScroll(rememberScrollState()),
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

                Text(
                    text = if (copied) {
                        stringResource(R.string.ui_backup_copied_notice)
                    } else {
                        "İpucu: Kopyala butonuna basıp metni notlara veya dosyaya kaydedebilirsin."
                    },
                    color = if (copied) {
                        KitsugiColors.AccentGreen
                    } else {
                        KitsugiColors.TextMuted
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (copied) FontWeight.SemiBold else FontWeight.Normal
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = stringResource(R.string.kapat),
                            color = KitsugiColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextButton(
                        enabled = backupText.isNotBlank(),
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(backupText)
                            )
                            copied = true
                        }
                    ) {
                        Text(
                            text = if (copied) stringResource(R.string.ui_action_copied) else stringResource(R.string.action_copy),
                            color = if (backupText.isNotBlank()) {
                                accentColor
                            } else {
                                KitsugiColors.TextMuted
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            textContentColor = KitsugiColors.TextSecondary,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = "Yedek metni",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.asagidaki_json_metnini_kopyalayip_guvenli_bi),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = backupText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                            .kitsugiVerticalScroll(rememberScrollState()),
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (copied) {
                            stringResource(R.string.ui_backup_copied_notice)
                        } else {
                            "İpucu: Kopyala butonuna basıp metni notlara, buluta veya dosyaya kaydedebilirsin."
                        },
                        color = if (copied) {
                            KitsugiColors.AccentGreen
                        } else {
                            KitsugiColors.TextMuted
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (copied) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupText.isNotBlank(),
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(backupText)
                        )
                        copied = true
                    }
                ) {
                    Text(
                        text = if (copied) stringResource(R.string.ui_action_copied) else stringResource(R.string.action_copy),
                        color = if (backupText.isNotBlank()) {
                            accentColor
                        } else {
                            KitsugiColors.TextMuted
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.kapat),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
fun KitsugiImportBackupDialog(
    importText: String,
    importMode: BackupImportMode,
    onImportModeChange: (BackupImportMode) -> Unit,
    onImportTextChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    val validationResult = remember(importText) {
        if (importText.isBlank()) {
            BackupValidationResult.Empty
        } else {
            runCatching {
                MediaEntryBackup.previewJson(importText)
            }.fold(
                onSuccess = { preview ->
                    BackupValidationResult.Valid(
                        totalCount = preview.totalCount,
                        animeCount = preview.animeCount,
                        mangaCount = preview.mangaCount,
                        apiCount = preview.apiCount,
                        favoriteCount = preview.favoriteCount
                    )
                },
                onFailure = { error ->
                    BackupValidationResult.Invalid(
                        message = error.message ?: KitsugiStrings.get(R.string.yedek_metni_okunamadi)
                    )
                }
            )
        }
    }

    val canImport = validationResult is BackupValidationResult.Valid

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current

    if (isTvDevice) {
        com.kitsugi.animelist.ui.tv.components.TvDialog(
            onDismiss = onDismiss,
            title = stringResource(R.string.yedekten_ice_aktar),
            width = 520.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.daha_once_olusturdugun_yedek_json_metnini_bu),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                ImportModeRow(
                    title = stringResource(R.string.mevcut_listeyi_degistir),
                    description = stringResource(R.string.once_tum_kayitlari_siler_sonra_yedegi_yukler),
                    selected = importMode == BackupImportMode.Replace,
                    onClick = {
                        onImportModeChange(BackupImportMode.Replace)
                    }
                )

                ImportModeRow(
                    title = stringResource(R.string.mevcut_listeye_ekle),
                    description = stringResource(R.string.yedegi_mevcut_listenin_ustune_ekler_ayni_api),
                    selected = importMode == BackupImportMode.Append,
                    onClick = {
                        onImportModeChange(BackupImportMode.Append)
                    }
                )

                TextField(
                    value = importText,
                    onValueChange = onImportTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp)
                        .kitsugiVerticalScroll(rememberScrollState()),
                    placeholder = {
                        Text(
                            text = "{ ... }",
                            color = KitsugiColors.TextMuted
                        )
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

                BackupValidationInfo(
                    validationResult = validationResult
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = stringResource(R.string.iptal),
                            color = KitsugiColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextButton(
                        enabled = canImport,
                        onClick = onImportClick
                    ) {
                        Text(
                            text = stringResource(R.string.ice_aktar),
                            color = if (canImport) {
                                accentColor
                            } else {
                                KitsugiColors.TextMuted
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            textContentColor = KitsugiColors.TextSecondary,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = stringResource(R.string.yedekten_ice_aktar),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.daha_once_olusturdugun_yedek_json_metnini_bu),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ImportModeRow(
                        title = stringResource(R.string.mevcut_listeyi_degistir),
                        description = stringResource(R.string.once_tum_kayitlari_siler_sonra_yedegi_yukler),
                        selected = importMode == BackupImportMode.Replace,
                        onClick = {
                            onImportModeChange(BackupImportMode.Replace)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ImportModeRow(
                        title = stringResource(R.string.mevcut_listeye_ekle),
                        description = stringResource(R.string.yedegi_mevcut_listenin_ustune_ekler_ayni_api),
                        selected = importMode == BackupImportMode.Append,
                        onClick = {
                            onImportModeChange(BackupImportMode.Append)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = importText,
                        onValueChange = onImportTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp)
                            .kitsugiVerticalScroll(rememberScrollState()),
                        placeholder = {
                            Text(
                                text = "{ ... }",
                                color = KitsugiColors.TextMuted
                            )
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

                    Spacer(modifier = Modifier.height(12.dp))

                    BackupValidationInfo(
                        validationResult = validationResult
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canImport,
                    onClick = onImportClick
                ) {
                    Text(
                        text = stringResource(R.string.ice_aktar),
                        color = if (canImport) {
                            accentColor
                        } else {
                            KitsugiColors.TextMuted
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.iptal),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun BackupValidationInfo(
    validationResult: BackupValidationResult
) {
    when (validationResult) {
        BackupValidationResult.Empty -> {
            Text(
                text = stringResource(R.string.yedek_metni_bekleniyor),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        is BackupValidationResult.Invalid -> {
            Text(
                text = stringResource(R.string.gecersiz_yedek_validationresult_message, validationResult.message),
                color = KitsugiColors.AccentRed,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        is BackupValidationResult.Valid -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KitsugiColors.SurfaceSoft)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.yedek_gecerli),
                    color = KitsugiColors.AccentGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.toplam_x_anime_x_manga_x, validationResult.totalCount, validationResult.animeCount, validationResult.mangaCount),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = stringResource(R.string.inline_api_sourced_1_s_favorites_5fcda8b, validationResult.apiCount, validationResult.favoriteCount),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ImportModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    KitsugiColors.SurfaceSoft
                } else {
                    KitsugiColors.Surface
                }
            )
            .tvClickable(shape = RoundedCornerShape(18.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor,
                unselectedColor = KitsugiColors.TextMuted
            )
        )
    }
}

private sealed class BackupValidationResult {
    data object Empty : BackupValidationResult()

    data class Valid(
        val totalCount: Int,
        val animeCount: Int,
        val mangaCount: Int,
        val apiCount: Int,
        val favoriteCount: Int
    ) : BackupValidationResult()

    data class Invalid(
        val message: String
    ) : BackupValidationResult()
}

enum class BackupImportMode {
    Replace,
    Append
}