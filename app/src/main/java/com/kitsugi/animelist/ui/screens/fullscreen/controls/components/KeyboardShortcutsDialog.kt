package com.kitsugi.animelist.ui.screens.fullscreen.controls.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor

private data class ShortcutEntry(val keys: List<String>, val description: String)
private data class ShortcutSection(val title: String, val entries: List<ShortcutEntry>)

private val shortcutSections = listOf(
    ShortcutSection(
        "Oynatma ve gezinme",
        listOf(
            ShortcutEntry(listOf("Space", "/", "K"), "Oynat / duraklat"),
            ShortcutEntry(listOf("←", "/", "→"), "10 saniye geri / ileri"),
            ShortcutEntry(listOf("Shift", "+", "← / →"), "60 saniye geri / ileri"),
            ShortcutEntry(listOf("J", "/", "L"), "Alternatif 10 saniye geri / ileri"),
            ShortcutEntry(listOf("N", "/", "P"), "Sonraki / önceki bölüm"),
            ShortcutEntry(listOf("E"), "Bölüm listesini aç"),
            ShortcutEntry(listOf("C"), "Bölüm/chapter listesini aç"),
            ShortcutEntry(listOf("Tab"), "Oynatıcı kontrollerini göster / gizle"),
            ShortcutEntry(listOf("Esc"), "Paneli veya kontrolleri kapat; tekrarında oynatıcıdan çık"),
        ),
    ),
    ShortcutSection(
        "Ses ve parlaklık",
        listOf(
            ShortcutEntry(listOf("↑", "/", "↓"), "Kontroller kapalıyken sistem sesini artır / azalt"),
            ShortcutEntry(listOf("0", "/", "9"), "Kontroller açıkken de sesi artır / azalt (MPV uyumlu)"),
            ShortcutEntry(listOf("Ses ±"), "Cihazın donanım ses tuşları"),
            ShortcutEntry(listOf("M"), "Sessize al / önceki ses seviyesine dön"),
            ShortcutEntry(listOf("Ctrl", "+", "↑ / ↓"), "Ekran parlaklığını %5 artır / azalt"),
            ShortcutEntry(listOf("Parlaklık ±"), "Klavyedeki özel parlaklık tuşları"),
        ),
    ),
    ShortcutSection(
        "Hız, altyazı ve ses parçaları",
        listOf(
            ShortcutEntry(listOf("[", "/", "]"), "Oynatma hızını 0,25x azalt / artır"),
            ShortcutEntry(listOf("\\"), "Oynatma hızını 1,00x yap"),
            ShortcutEntry(listOf("U"), "Ayrıntılı oynatma hızı paneli"),
            ShortcutEntry(listOf("S"), "Altyazı parçaları ve biçim etiketleri"),
            ShortcutEntry(listOf("D"), "Altyazıyı aç / kapat"),
            ShortcutEntry(listOf("A"), "Ses parçaları ve codec etiketleri"),
            ShortcutEntry(listOf("#"), "Sonraki ses parçasına geç"),
            ShortcutEntry(listOf("Q"), "Kalite / kaynak listesi"),
        ),
    ),
    ShortcutSection(
        "Diğer oynatıcı özellikleri",
        listOf(
            ShortcutEntry(listOf("Z"), "Görüntü oranı / yakınlaştırma modunu değiştir"),
            ShortcutEntry(listOf("R"), "Ekran yönünü değiştir"),
            ShortcutEntry(listOf("X"), "Kontrolleri kilitle / kilidi aç"),
            ShortcutEntry(listOf("O"), "Ekran görüntüsü araçları"),
            ShortcutEntry(listOf("G"), "Video filtreleri"),
            ShortcutEntry(listOf("I"), "Zamanlayıcı, çözücü, istatistik ve diğer araçlar"),
            ShortcutEntry(listOf("F"), "Resim içinde resim (PiP) moduna geç"),
            ShortcutEntry(listOf("H", "veya", "?"), "Bu kısayol rehberini aç"),
        ),
    ),
)

@Composable
fun KeyboardShortcutsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 780.dp)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = playerSurfaceColor(0.97f),
                contentColor = Color.White,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Keyboard,
                        contentDescription = null,
                        tint = LocalKitsugiAccent.current,
                        modifier = Modifier.size(30.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = "Klavye kısayolları",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Oynatıcının tüm temel araçları klavyeden yönetilebilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shortcutSections.forEach { section ->
                        item(key = "title_${section.title}") {
                            Text(
                                text = section.title,
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                            )
                        }
                        items(section.entries) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.widthIn(min = 150.dp, max = 245.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    entry.keys.forEach { key ->
                                        if (key == "+" || key == "/" || key == "veya") {
                                            Text(
                                                text = key,
                                                color = Color.White.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        } else {
                                            KeyCap(key)
                                        }
                                    }
                                }
                                Text(
                                    text = entry.description,
                                    color = Color.White.copy(alpha = 0.86f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Not: Kontroller kapalıyken eşlenmemiş tuşlar MPV'nin özel input.conf yapılandırmasına da iletilir.",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(value: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}
