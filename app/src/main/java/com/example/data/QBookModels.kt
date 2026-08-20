package com.example.data

data class QBookMeta(
    val id: String,
    val name: String,
    val version: String,
    val hierarchyLabels: List<String>,
    val type: String? = null,
    val updateId: String? = null,
    val updateVersion: List<Map<String, List<Long>>>? = null,
    val questionIdStart: Long? = null,
    val questionIdEnd: Long? = null
)

data class QBookHeader(
    val startId: Long,
    val endId: Long,
    val text: String
)

data class QBookQuestionDetails(
    val text: String,
    val format: String? = null,
    val table: List<List<String>>? = null,
    val bottomText: String? = null,
    val image: String?
)

data class QBookOption(
    val id: String,
    val text: String
)

data class QBookExplanation(
    val brief: String,
    val detailed: String,
    val image: String?,
    val table: List<List<String>>? = null
)

data class QBookExam(
    val exam: String,
    val examInfo: String?,
    val year: String?
)

data class QBookMetadata(
    val path: String,
    val category: String,
    val exam: String?,
    val year: String?,
    val exams: List<QBookExam>? = null
)

data class QBookQuestion(
    val id: Long,
    val header: QBookHeader?,
    val question: QBookQuestionDetails,
    val options: List<QBookOption>,
    val extraOptions: List<QBookOption>?,
    val correctOptionIds: List<String>,
    val explanation: QBookExplanation?,
    val metadata: QBookMetadata
)
