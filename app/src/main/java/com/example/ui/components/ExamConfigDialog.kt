package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var settings by remember(bookId) {
        mutableStateOf(
            settingsManager.getExamSettings(bookId, questionLimit)?.let {
                try {
                    com.example.data.SharedGson.normal.fromJson(it, ExamSettings::class.java)
                } catch (_: Exception) {
                    null
                }
            } ?: ExamSettings(
                markingScheme = "Off",
                timerPreset = "Auto"
            )
        )
    }

    var correctMarksText by remember(bookId, settings.markingScheme) {
        mutableStateOf(
            if (settings.customCorrectMarks % 1f == 0f) settings.customCorrectMarks.toInt().toString()
            else settings.customCorrectMarks.toString()
        )
    }
    var wrongMarksText by remember(bookId, settings.markingScheme) {
        mutableStateOf(
            if (settings.customWrongMarks % 1f == 0f) settings.customWrongMarks.toInt().toString()
            else settings.customWrongMarks.toString()
        )
    }
    var customTimerText by remember(bookId, settings.timerPreset) {
        mutableStateOf(if (settings.customTimerMinutes > 0) settings.customTimerMinutes.toString() else "")
    }

    fun persistSettings(updated: ExamSettings) {
        settings = updated
        val settingsJson = com.example.data.SharedGson.normal.toJson(updated)
        settingsManager.setExamSettings(bookId, questionLimit, settingsJson)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exam Configuration") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Marking Scheme
                Text("Marking Scheme", style = MaterialTheme.typography.titleSmall)
                val schemes = listOf("Off", "CGL Tier 1", "CGL Tier 2", "SSC CHSL", "Custom")
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schemes.forEach { scheme ->
                        FilterChip(
                            selected = settings.markingScheme == scheme,
                            onClick = {
                                val updated = when (scheme) {
                                    "CGL Tier 1", "SSC CHSL" -> settings.copy(
                                        markingScheme = scheme,
                                        customCorrectMarks = 2f,
                                        customWrongMarks = 0.5f
                                    )
                                    "CGL Tier 2" -> settings.copy(
                                        markingScheme = scheme,
                                        customCorrectMarks = 3f,
                                        customWrongMarks = 1f
                                    )
                                    "Off" -> settings.copy(
                                        markingScheme = scheme,
                                        customCorrectMarks = 1f,
                                        customWrongMarks = 0f
                                    )
                                    else -> settings.copy(markingScheme = scheme)
                                }
                                persistSettings(updated)
                            },
                            label = { Text(scheme) }
                        )
                    }
                }

                if (settings.markingScheme == "Custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = correctMarksText,
                            onValueChange = { raw ->
                                correctMarksText = raw
                                raw.toFloatOrNull()?.let { parsed ->
                                    persistSettings(settings.copy(customCorrectMarks = parsed))
                                }
                            },
                            label = { Text("+ Correct") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = wrongMarksText,
                            onValueChange = { raw ->
                                wrongMarksText = raw
                                raw.toFloatOrNull()?.let { parsed ->
                                    persistSettings(settings.copy(customWrongMarks = parsed))
                                }
                            },
                            label = { Text("- Wrong") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // Timer
                Text("Exam Timer", style = MaterialTheme.typography.titleSmall)
                val timers = listOf("Off", "Auto", "15m", "30m", "60m", "Custom")
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timers.forEach { t ->
                        FilterChip(
                            selected = settings.timerPreset == t,
                            onClick = { persistSettings(settings.copy(timerPreset = t)) },
                            label = {
                                Text(
                                    if (t == "Auto") {
                                        "Auto (${if (questionLimit <= 0) "No Limit" else "${questionLimit * 36}s"})"
                                    } else {
                                        t
                                    }
                                )
                            }
                        )
                    }
                }

                if (settings.timerPreset == "Custom") {
                    OutlinedTextField(
                        value = customTimerText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }
                            customTimerText = filtered
                            persistSettings(settings.copy(customTimerMinutes = filtered.toIntOrNull() ?: 0))
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                persistSettings(settings)
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
