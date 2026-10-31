package com.kitsugi.animelist.ui.components

import android.content.Intent
import android.net.Uri
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsugiKitsuLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accentColor = LocalKitsugiAccent.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val kitsuBrandColor = Color(0xFFFD755C)

    // Kitsu kimlik doğrulaması API üzerinden kullanıcı adı/şifre ile yapılır; tarayıcı
    // butonu hesabı tarayıcıda doğrulama / şifre sıfırlama için bir çıkış yolu sunar.
    val openKitsuInBrowser: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://kitsu.app/login")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { err ->
            errorMessage = KitsugiStrings.get(R.string.tarayici_acilamadi_x, err.message)
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, kitsuBrandColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Title
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(kitsuBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, kitsuBrandColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiPlatformLogo(platformId = "kitsu", size = 34.dp, fallbackTint = kitsuBrandColor)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.inline_connect_to_kitsu_8ef915f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = stringResource(R.string.inline_sign_in_with_your_kitsu_dfe4b7e),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )

                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = KitsugiColors.AccentRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.ErrorOutline,
                                contentDescription = null,
                                tint = KitsugiColors.AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KitsugiColors.AccentRed
                                )
                                // Kaynağa özgü hata kodu + neden/çözüm (dil dosyasından).
                                PlatformErrorCodeHint(message = errorMessage, platform = ListPlatform.KITSU)
                            }
                        }
                    }
                }

                // Email / Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.inline_email_or_username_9afd3f0)) },
                    leadingIcon = {
                        Icon(Icons.Person, contentDescription = null, tint = kitsuBrandColor)
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = kitsuBrandColor,
                        focusedLabelColor = kitsuBrandColor,
                        cursorColor = kitsuBrandColor,
                        unfocusedBorderColor = KitsugiColors.Border
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.sifre)) },
                    leadingIcon = {
                        Icon(Icons.Lock, contentDescription = null, tint = kitsuBrandColor)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.VisibilityOff else Icons.Visibility,
                                contentDescription = stringResource(R.string.sifreyi_goster),
                                tint = KitsugiColors.TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = kitsuBrandColor,
                        focusedLabelColor = kitsuBrandColor,
                        cursorColor = kitsuBrandColor,
                        unfocusedBorderColor = KitsugiColors.Border
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KitsugiColors.TextSecondary
                        )
                    ) {
                        Text(stringResource(R.string.iptal))
                    }

                    KitsugiButton(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = KitsugiStrings.get(R.string.lutfen_tum_alanlari_doldurun)
                            } else {
                                isLoading = true
                                errorMessage = null
                                onLogin(username, password) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        errorMessage = error ?: KitsugiStrings.get(R.string.giris_yapilamadi)
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            KitsugiPlasmaLoader(size = 18.dp)
                        } else {
                            Text(stringResource(R.string.giris_yap), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Çıkış yolu: hesabı varsayılan (3. parti) tarayıcıda doğrula / şifre sıfırla.
                TextButton(
                    onClick = openKitsuInBrowser,
                    enabled = !isLoading
                ) {
                    Icon(
                        Icons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = kitsuBrandColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.inline_open_in_browser_eed20ab),
                        color = kitsuBrandColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Text(
                    text = KitsugiStrings.get(R.string.kitsu_login_tarayici_aciklamasi),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = KitsugiColors.TextSecondary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
