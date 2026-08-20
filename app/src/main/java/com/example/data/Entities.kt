package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

data class AiFileAttachment(val bytes: ByteArray, val mimeType: String)

data class TopicNode(
    val name: String,
    val path: String, // Full path up to this node, delimited by /
    val children: MutableMap<String, TopicNode> = mutableMapOf(),
    var isLeaf: Boolean = false,
    var questionCount: Int = 0,
    var attemptedCount: Int = 0,
    var correctCount: Int = 0,
    var totalAttempts: Int = 0,
    val questionIds: MutableList<Long> = mutableListOf()
) {
    val accuracy: Float get() = if (totalAttempts > 0) correctCount.toFloat() / totalAttempts else 0f
    
    val masteryLevel: String get() {
        if (questionCount == 0 || attemptedCount == 0) return "New"
        val coverage = attemptedCount.toFloat() / questionCount
        
        if (coverage < 0.2f) return "Started"
        
        return when {
            accuracy < 0.5f -> "Learning"
            accuracy >= 0.5f && accuracy < 0.75f -> "Developing"
            accuracy >= 0.75f && coverage < 0.7f -> "Learnt"
            accuracy >= 0.75f && accuracy < 0.90f -> "Strong"
            accuracy >= 0.90f && coverage >= 0.9f -> "Mastered"
            else -> "Strong"
        }
    }
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val totalQuestions: Int = 0,
    val totalCategories: Int = 0,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val dataVariable: String = "",
    val updateId: String? = null,
    val bookVersion: String? = null,
    val updateVersions: String = "[]",
    val questionIdStart: Long = 0L,
    val questionIdEnd: Long = 0L,
    val hierarchyLabels: String = "[]",
    val roadmapLeafLevel: Int = 0
)

@Entity(
    tableName = "questions",
    indices = [
        androidx.room.Index(value = ["bookId"]),
        androidx.room.Index(value = ["path"]),
        androidx.room.Index(value = ["category"])
    ]
)
data class QuestionEntity(
    @PrimaryKey val globalId: String = UUID.randomUUID().toString(),
    val id: Long,
    val bookId: String,
    val question: String,
    val options: String, // JSON Array
    val correctIndex: Int,
    val explanation: String,
    val path: String,
    val category: String,
    val section: String,
    val extraOptions: String, // JSON Array
    val exam: String,
    val year: String,
    val img: String? = null,
    val questionHeader: String? = null,
    
    // --- NEW ADVANCED FIELDS FOR QBOOK ---
    val advancedOptions: String? = null, // JSON string of List<{id, text}>
    val advancedCorrectIds: String? = null, // JSON string of List<String>
    val detailedExplanation: String? = null,
    val explanationImage: String? = null,
    val multipleCorrect: Boolean = false,
    val format: String? = null,
    val tableData: String? = null,
    val bottomText: String? = null,
    val exams: String? = null,
    val explanationTable: String? = null
)

@Entity(
    tableName = "question_stats",
    indices = [
        androidx.room.Index(value = ["bookId"]),
        androidx.room.Index(value = ["questionId"]),
        androidx.room.Index(value = ["nextReview"])
    ]
)
data class QuestionStatEntity(
    @PrimaryKey val questionKey: String, // bookId_questionId
    val bookId: String,
    val questionId: Long,
    val attempts: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val lastSeen: Long = 0L,
    val easeFactor: Float = 2.5f,
    val interval: Int = 1,
    val streak: Int = 0,
    val nextReview: Long = 0L
)

@Entity(
    tableName = "quiz_history",
    indices = [
        androidx.room.Index(value = ["bookId"])
    ]
)
data class QuizHistoryEntity(
    @PrimaryKey val historyId: Long,
    val bookId: String,
    val date: String,
    val status: String,
    val quizLabel: String,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val accuracy: Float,
    val rawScore: Float,
    val finalScore: Float,
    val timeUsed: Long,
    val activeQuizData: String, // JSON string for active quiz state
    val questionIds: String, // JSON array of Long
    val userAnswers: String // JSON array of Int
)

data class QuestionSummary(
    val id: Long,
    val bookId: String,
    val category: String,
    val path: String,
    val exam: String,
    val exams: String? = null,
    val questionHeader: String? = null
)

@Entity(
    tableName = "bookmarks",
    indices = [
        androidx.room.Index(value = ["bookId"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey val bookmarkKey: String, // bookId_questionId
    val bookId: String,
    val questionId: Long
)

@Entity(tableName = "learning_plan")
data class LearningPlanEntity(
    @PrimaryKey val bookId: String,
    val mode: String = "adaptive",
    val topicOrder: String, // JSON array string
    val unlockedTopics: String, // JSON array string
    val lockedTopics: String, // JSON array string
    val completedTopics: String, // JSON array string
    val lastActiveTopic: String? = null,
    val parentPath: String? = null,
    val leafLevel: Int? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val role: String, // "user", "model"
    val content: String,
    val timestamp: Long
)


fun List<QuestionEntity>.resolveHeaders(): List<QuestionEntity> {
    if (this.isEmpty()) return this
    val resolved = this.toMutableList()
    val idToIndex = this.withIndex().associate { it.value.id to it.index }
    val gson = com.example.data.SharedGson.normal
    
    // Forward pass to resolve JSON headers
    for (i in resolved.indices) {
        val q = resolved[i]
        val header = q.questionHeader
        if (header != null && header.isNotBlank() && header.trimStart().startsWith("{")) {
            try {
                val headerObj = gson.fromJson(header, com.example.data.QBookHeader::class.java)
                if (headerObj.text.isNotBlank()) {
                    val sIdx = idToIndex[headerObj.startId] ?: i
                    val eIdx = idToIndex[headerObj.endId] ?: i
                    if (sIdx <= eIdx) {
                        for (j in sIdx..eIdx) {
                            resolved[j] = resolved[j].copy(questionHeader = headerObj.text)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }
    return resolved
}
