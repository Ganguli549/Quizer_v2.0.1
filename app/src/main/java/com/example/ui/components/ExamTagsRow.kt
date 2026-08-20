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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamTagsRow(examsJson: String?, legacyExam: String, legacyYear: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    val examsList: List<QBookExam> = remember(examsJson, legacyExam, legacyYear) {
        val list = mutableListOf<QBookExam>()
        if (!examsJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<QBookExam>>() {}.type
                val parsed: List<QBookExam>? = SharedGson.normal.fromJson(examsJson, type)
                if (parsed != null) {
                    list.addAll(parsed)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        if (list.isEmpty() && (legacyExam.isNotBlank() || legacyYear.isNotBlank())) {
            list.add(QBookExam(exam = legacyExam, examInfo = null, year = legacyYear.takeIf { it.isNotBlank() }))
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
                if (examData.exam.isNotBlank()) textParts.add(examData.exam)
                if (!examData.examInfo.isNullOrBlank()) textParts.add("(${examData.examInfo})")
                if (!examData.year.isNullOrBlank()) textParts.add(examData.year)
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
