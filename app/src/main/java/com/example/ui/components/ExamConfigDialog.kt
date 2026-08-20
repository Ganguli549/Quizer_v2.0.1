package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.ExamSettings
import com.example.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamConfigDialog(
    isSettingsOnly: Boolean = false,
    bookId: String?,
    questionLimit: Int,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onStart: (ExamSettings) -> Unit
) {
    var settings by remember {
        mutableStateOf(
            settingsManager.getExamSettings(bookId, questionLimit)?.let {
                com.example.data.SharedGson.normal.fromJson(it, ExamSettings::class.java)
            } ?: ExamSettings(
                markingScheme = "Off",
                timerPreset = "Auto"
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exam Configuration") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Marking Scheme
                Text("Marking Scheme", style = MaterialTheme.typography.titleSmall)
                val schemes = listOf("Off", "CGL Tier 1", "CGL Tier 2", "SSC CHSL", "Custom")
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class) androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schemes.forEach { scheme ->
                        FilterChip(
                            selected = settings.markingScheme == scheme,
                            onClick = { 
                                settings = settings.copy(markingScheme = scheme)
                                if (scheme == "CGL Tier 1" || scheme == "SSC CHSL") {
                                    settings = settings.copy(customCorrectMarks = 2f, customWrongMarks = 0.5f)
                                } else if (scheme == "CGL Tier 2") {
                                    settings = settings.copy(customCorrectMarks = 3f, customWrongMarks = 1f)
                                } else if (scheme == "Off") {
                                    settings = settings.copy(customCorrectMarks = 1f, customWrongMarks = 0f)
                                }
                            },
                            label = { Text(scheme) }
                        )
                    }
                }

                if (settings.markingScheme == "Custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.customCorrectMarks.toString(),
                            onValueChange = { settings = settings.copy(customCorrectMarks = it.toFloatOrNull() ?: 0f) },
                            label = { Text("+ Correct") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.customWrongMarks.toString(),
                            onValueChange = { settings = settings.copy(customWrongMarks = it.toFloatOrNull() ?: 0f) },
                            label = { Text("- Wrong") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // Timer
                Text("Exam Timer", style = MaterialTheme.typography.titleSmall)
                val timers = listOf("Off", "Auto", "15m", "30m", "60m", "Custom")
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class) androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timers.forEach { t ->
                        FilterChip(
                            selected = settings.timerPreset == t,
                            onClick = { settings = settings.copy(timerPreset = t) },
                            label = { Text(if (t == "Auto") "Auto (${if (questionLimit == 0) "No Limit" else "${questionLimit * 36}s"})" else t) }
                        )
                    }
                }

                if (settings.timerPreset == "Custom") {
                    OutlinedTextField(
                        value = settings.customTimerMinutes.toString(),
                        onValueChange = { settings = settings.copy(customTimerMinutes = it.toIntOrNull() ?: 0) },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            }
        },
        confirmButton = {
            Button(onClick = {
                val settingsJson = com.example.data.SharedGson.normal.toJson(settings)
                settingsManager.setExamSettings(bookId, questionLimit, settingsJson)
                

                
                onStart(settings)
            }) {
                Text(if (isSettingsOnly) "Save Settings" else "Start Exam")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
