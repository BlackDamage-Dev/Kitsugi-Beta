package com.kitsugi.animelist.ui.screens.more

import androidx.compose.ui.res.stringResource

import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiButton

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.KitsugiFlySendButton
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    embeddedMode: Boolean = false,
    onSubmit: (title: String, type: String, description: String) -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    val accentColor = LocalKitsugiAccent.current

    var feedbackTitle by remember { mutableStateOf("") }
    var feedbackDescription by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableStateOf(0) }
    val feedbackTypes = listOf(stringResource(R.string.hata_bildirimi), stringResource(R.string.ozellik_onerisi), "Genel")

    val scrollState = rememberScrollState()

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = scrollState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .kitsugiVerticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = if (embeddedMode) 8.dp else 16.dp)
        ) {
            if (!embeddedMode) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.inline_send_feedback_af8214a),
                        color = KitsugiColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Type Selector Label
            Text(
                text = stringResource(R.string.inline_feedback_type_9aac7bc),
                color = KitsugiColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                feedbackTypes.forEachIndexed { index, type ->
                    val isSelected = selectedTypeIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) accentColor else KitsugiColors.surface
                            )
                            .tvClickable(shape = RoundedCornerShape(20.dp), onClick = { selectedTypeIndex = index })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) KitsugiColors.background else KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title TextField
            Text(
                text = stringResource(R.string.inline_subject_630f47a),
                color = KitsugiColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = feedbackTitle,
                onValueChange = { feedbackTitle = it },
                placeholder = { Text(stringResource(R.string.inline_briefly_summarize_the_topic_ac0775a), color = KitsugiColors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = KitsugiColors.surface,
                    focusedTextColor = KitsugiColors.textPrimary,
                    unfocusedTextColor = KitsugiColors.textPrimary,
                    focusedContainerColor = KitsugiColors.surface,
                    unfocusedContainerColor = KitsugiColors.surface
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Description TextField
            Text(
                text = "Detaylar",
                color = KitsugiColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = feedbackDescription,
                onValueChange = { feedbackDescription = it },
                placeholder = { Text(stringResource(R.string.inline_enter_details_and_any_steps_16ecc2e), color = KitsugiColors.textMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = KitsugiColors.surface,
                    focusedTextColor = KitsugiColors.textPrimary,
                    unfocusedTextColor = KitsugiColors.textPrimary,
                    focusedContainerColor = KitsugiColors.surface,
                    unfocusedContainerColor = KitsugiColors.surface
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action KitsugiButton(Animated Uiverse Fly Send)
            KitsugiFlySendButton(
                onClick = {
                    if (feedbackTitle.isNotBlank() && feedbackDescription.isNotBlank()) {
                        onSubmit(feedbackTitle, feedbackTypes[selectedTypeIndex], feedbackDescription)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = feedbackTitle.isNotBlank() && feedbackDescription.isNotBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

