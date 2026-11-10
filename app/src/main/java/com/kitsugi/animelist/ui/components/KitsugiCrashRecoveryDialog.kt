package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger
import com.kitsugi.animelist.ui.screens.crash.KitsugiCrashActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsugiCrashRecoveryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val crashLog = remember { KitsugiCrashLogger.readCrashLog(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = KitsugiColors.Surface,
            border = BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Çökme Animasyonu
                KitsugiCrashAnimation(
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Beklenmedik Kapanma Tespit Edildi",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.inline_kitsugi_closed_or_crashed_during_7f646e0),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(12.dp))

                // Hata Özeti Kutusu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KitsugiColors.SurfaceSoft,
                    border = BorderStroke(1.dp, KitsugiColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Text(
                        text = crashLog.take(1500),
                        color = KitsugiColors.AccentRed.copy(alpha = 0.9f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .kitsugiVerticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Butonlar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Satır 1: Kopyala + Hata dosyasını paylaş
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kitsugi Crash", crashLog))
                                Toast.makeText(context, KitsugiStrings.get(R.string.hata_raporu_panoya_kopyalandi), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KitsugiColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.ContentCopy, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_copy), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        KitsugiButton(
                            onClick = {
                                // Hata raporu metin olarak değil, DOSYA (.txt) olarak paylaşılır.
                                val file = KitsugiCrashLogger.ensureCrashLogFile(context)
                                if (file == null) {
                                    Toast.makeText(context, KitsugiStrings.get(R.string.hata_raporu_dosyasi_olusturulamadi), Toast.LENGTH_SHORT).show()
                                    return@KitsugiButton
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Kitsugi Hata Raporu v${BuildConfig.VERSION_NAME}")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Hata Dosyasını Paylaş"))
                                KitsugiCrashLogger.markCrashAsRead(context)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Share, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.inline_share_file_6e19b1d), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Satır 2: Detaylı Rapor Ekranı
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, KitsugiCrashActivity::class.java).apply {
                                putExtra("has_crash", true)
                                putExtra("crash_summary", crashLog.take(4096))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            KitsugiCrashLogger.markCrashAsRead(context)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitsugiColors.AccentTeal.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.AccentTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.OpenInNew, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.inline_open_detailed_crash_screen_ee67637), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Satır 3: İndirilenlere Kaydet
                    OutlinedButton(
                        onClick = {
                            val location = KitsugiCrashLogger.exportReportToDownloads(context)
                            if (location != null) {
                                Toast.makeText(context, "Rapor kaydedildi: $location ✓", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Kaydedilemedi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitsugiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Download, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.inline_save_to_downloads_txt_0bd21ef), fontSize = 11.5.sp)
                    }

                    // Kapat
                    TextButton(
                        onClick = {
                            KitsugiCrashLogger.markCrashAsRead(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.inline_close_and_don_t_show_38bb57d), color = KitsugiColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
