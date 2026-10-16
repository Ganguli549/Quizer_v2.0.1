package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QBookExam
import com.example.data.SharedGson
import com.google.gson.reflect.TypeToken

private fun String?.cleanValidExamField(): String? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
        return null
    }
    return trimmed
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamTagsRow(
    examsJson: String?,
    legacyExam: String,
    legacyYear: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val examsList: List<QBookExam> = remember(examsJson, legacyExam, legacyYear) {
        val list = mutableListOf<QBookExam>()
        val cleanJson = examsJson.cleanValidExamField()
        if (cleanJson != null && cleanJson != "[]") {
            try {
                val type = object : TypeToken<List<QBookExam>>() {}.type
                val parsed: List<QBookExam>? = SharedGson.normal.fromJson(cleanJson, type)
                if (parsed != null) {
                    list.addAll(parsed)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        val cleanLegacyExam = legacyExam.cleanValidExamField()
        val cleanLegacyYear = legacyYear.cleanValidExamField()
        if (list.isEmpty() && (cleanLegacyExam != null || cleanLegacyYear != null)) {
            list.add(
                QBookExam(
                    exam = cleanLegacyExam ?: "",
                    examInfo = null,
                    year = cleanLegacyYear
                )
            )
        }
        list
    }

    if (examsList.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            examsList.forEach { examData ->
                val textParts = mutableListOf<String>()
                @Suppress("SENSELESS_COMPARISON")
                val safeExam = if (examData.exam != null) examData.exam.cleanValidExamField() else null
                val safeInfo = examData.examInfo.cleanValidExamField()
                val safeYear = examData.year.cleanValidExamField()

                if (safeExam != null) textParts.add(safeExam)
                if (safeInfo != null) textParts.add("($safeInfo)")
                if (safeYear != null) textParts.add(safeYear)

                val fullText = textParts.joinToString(" ")
                if (fullText.isNotBlank()) {
                    Text(
                        text = fullText,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}
