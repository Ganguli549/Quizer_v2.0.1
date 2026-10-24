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

const val TIMEOUT_SKIPPED_SENTINEL: Int = Int.MIN_VALUE

/**
 * Normalizes a list of option IDs from `correctOptionIds` (e.g. `["opt3", "ext0", "ext2", "opt0"]`),
 * trimming whitespace and removing empty or duplicate entries.
 */
fun normalizeCorrectOptionIdsList(rawIds: List<String>?): List<String> {
    if (rawIds.isNullOrEmpty()) return emptyList()
    return rawIds
        .flatMap { it.split(",") }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        .distinct()
}

fun parseNormalizedCorrectOptionIds(rawCorrectIdsJson: String?): List<String> {
    if (rawCorrectIdsJson.isNullOrBlank() || rawCorrectIdsJson == "[]") return emptyList()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        val rawList = SharedGson.normal.fromJson<List<String>>(rawCorrectIdsJson, type) ?: emptyList()
        normalizeCorrectOptionIdsList(rawList)
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Resolves the option indices marked correct for this [QuestionEntity]:
 * - Base options (`"opt0"`, `"opt1"`, ...) map to `0 until baseOptions.size`.
 * - Extra options (`"ext0"`, `"ext1"`, ...) map to negative indices `-(extraIdx + 1)` (i.e. `-1, -2, ...`)
 *   when [includeExtraOptions] is `true`.
 */
fun QuestionEntity.resolveCorrectIndices(includeExtraOptions: Boolean = false): Set<Int> {
    val gson = SharedGson.normal
    val normalizedIds = parseNormalizedCorrectOptionIds(this.advancedCorrectIds)
    val advancedOpts: List<QBookOption> = try {
        val type = object : com.google.gson.reflect.TypeToken<List<QBookOption>>() {}.type
        gson.fromJson<List<QBookOption>>(this.advancedOptions ?: "[]", type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    val baseOpts: List<String> = try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(this.options, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    val extraOpts: List<String> = if (includeExtraOptions && this.extraOptions.isNotBlank() && this.extraOptions != "[]") {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(this.extraOptions, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    val optionCount = if (advancedOpts.isNotEmpty()) advancedOpts.size else baseOpts.size
    val resolved = LinkedHashSet<Int>()

    for (rawId in normalizedIds) {
        val id = rawId.trim()
        // 1. Exact match in advancedOptions by id (e.g. "opt0", "opt3")
        val advIdx = advancedOpts.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (advIdx != -1) {
            resolved.add(advIdx)
            continue
        }
        // 2. Match "opt0", "opt1", ...
        if (id.startsWith("opt", ignoreCase = true)) {
            val idx = id.substring(3).trim().toIntOrNull()
            if (idx != null && idx in 0 until optionCount) {
                resolved.add(idx)
                continue
            }
        }
        // 3. Match extraOptions IDs ("ext0", "ext1", ... or legacy "E0", "E1") when includeExtraOptions == true
        if (includeExtraOptions && extraOpts.isNotEmpty()) {
            val extIdx = when {
                id.startsWith("ext", ignoreCase = true) -> id.substring(3).trim().toIntOrNull()
                id.startsWith("E", ignoreCase = true) -> id.substring(1).trim().toIntOrNull()
                else -> null
            }
            if (extIdx != null && extIdx in extraOpts.indices) {
                resolved.add(-(extIdx + 1))
                continue
            }
        }
        // 4. Match single letter "A", "B", "C", "D" ...
        if (id.length == 1 && id[0].uppercaseChar() in 'A'..'Z') {
            val idx = id[0].uppercaseChar() - 'A'
            if (idx in 0 until optionCount) {
                resolved.add(idx)
                continue
            }
        }
        // 5. Match direct 0-based integer string "0", "1", ...
        val numIdx = id.toIntOrNull()
        if (numIdx != null && numIdx in 0 until optionCount) {
            resolved.add(numIdx)
            continue
        }
    }

    // Every question with base options must have at least one base option index (0 until optionCount)
    // marked correct. If advancedCorrectIds only contained extra option IDs ("ext0", "ext1", ...)
    // while the base correct option was stored in `correctIndex`, always include `correctIndex`!
    if (resolved.none { it >= 0 }) {
        val fallback = if (optionCount > 0) this.correctIndex.coerceIn(0, optionCount - 1) else this.correctIndex.coerceAtLeast(0)
        val withBase = LinkedHashSet<Int>()
        withBase.add(fallback)
        withBase.addAll(resolved)
        return withBase
    }
    return resolved
}

/**
 * Returns the canonical list of correct option IDs (e.g. `["opt0", "ext0", "ext1"]`) for this
 * [QuestionEntity], guaranteeing that if [advancedCorrectIds] only stored `"ext*"` IDs while the
 * base correct option was stored in [correctIndex], `"opt$correctIndex"` is included at the front.
 */
fun QuestionEntity.resolvedCorrectOptionIds(): List<String> {
    val parsed = parseNormalizedCorrectOptionIds(this.advancedCorrectIds)
    val baseFallback = "opt${this.correctIndex.coerceAtLeast(0)}"
    if (parsed.isEmpty()) return listOf(baseFallback)
    if (parsed.none { it.startsWith("opt", ignoreCase = true) }) {
        return listOf(baseFallback) + parsed
    }
    return parsed
}

/**
 * Returns true if the question is marked `multipleCorrect` OR has more than 1 resolved correct option.
 */
fun QuestionEntity.isEffectivelyMultipleCorrect(includeExtraOptions: Boolean = false): Boolean {
    return this.multipleCorrect || this.resolveCorrectIndices(includeExtraOptions).size > 1
}

/**
 * Builds the list of `(originalOptionIndex, optionText)` pairs to display in `QuizScreen`:
 * 1. If [retainedOptionIndices] is non-empty (e.g. during Review / Practice Mistakes / Resumed Quiz
 *    or when revisiting a question whose 4 quiz options were already selected), it retains those
 *    exact options from the quiz without pulling in new `extraOptions` or other `options`. If
 *    [shuffleOptions] is `true`, only those retained options are shuffled.
 * 2. If `useDistractors` is ON and `shuffleOptions` is OFF (only dynamic distractors enabled):
 *    shows ALL options (`baseOptions + extraOptions`) and shuffles them.
 * 3. If `shuffleOptions` is ON (with or without `useDistractors`):
 *    shuffles the candidate options, caps to 4 options, and guarantees AT LEAST ONE correct option
 *    is included in those 4 options.
 */
fun QuestionEntity.buildQuizDisplayOptions(
    useDistractors: Boolean,
    shuffleOptions: Boolean,
    sessionSeed: Long,
    retainedOptionIndices: List<Int>? = null
): List<Pair<Int, String>> {
    val baseOptions = try {
        SharedGson.normal.fromJson(this.options, Array<String>::class.java)?.toList() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    val basePairs = baseOptions.mapIndexed { idx, text -> Pair(idx, text) }

    val extraPairs = if (this.extraOptions.isNotBlank() && this.extraOptions != "[]") {
        try {
            val extras = SharedGson.normal.fromJson(this.extraOptions, Array<String>::class.java)?.toList() ?: emptyList()
            extras.mapIndexed { idx, txt -> Pair(-(idx + 1), txt) }
        } catch (_: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    val stableSeed = sessionSeed + this.id
    val rng = kotlin.random.Random(stableSeed)

    // If this question has retained option indices from the quiz, retain those exact options
    // and only shuffle them if shuffleOptions is enabled.
    if (!retainedOptionIndices.isNullOrEmpty()) {
        val allLookup = (basePairs + extraPairs).toMap()
        val retainedPairs = retainedOptionIndices.mapNotNull { origIdx ->
            allLookup[origIdx]?.let { txt -> Pair(origIdx, txt) }
        }
        if (retainedPairs.isNotEmpty()) {
            return if (shuffleOptions) retainedPairs.shuffled(rng) else retainedPairs
        }
    }

    val hasExtras = useDistractors && extraPairs.isNotEmpty()
    var finalOptions = if (hasExtras) basePairs + extraPairs else basePairs

    if (shuffleOptions) {
        val allCorrectIndices = this.resolveCorrectIndices(includeExtraOptions = useDistractors)
        val correctOpts = finalOptions.filter { allCorrectIndices.contains(it.first) }
        var mutableList = finalOptions.toMutableList()
        mutableList.shuffle(rng)

        if (mutableList.size > 4) {
            val selected = mutableList.take(4).toMutableList()
            val hasAtLeastOneCorrect = selected.any { allCorrectIndices.contains(it.first) }

            if (!hasAtLeastOneCorrect && correctOpts.isNotEmpty()) {
                // Guarantee at least 1 correct option is present in the 4 options
                val pickedCorrect = correctOpts.random(kotlin.random.Random(stableSeed xor 0x5DEECE66DL))
                selected[selected.lastIndex] = pickedCorrect
                selected.shuffle(kotlin.random.Random(stableSeed + 31L))
            }
            mutableList = selected
        }
        finalOptions = mutableList
    } else if (hasExtras) {
        // When ONLY dynamic distractors is enabled (shuffleOptions is disabled),
        // show all options (base + distractors) and shuffle them!
        val mutableList = finalOptions.toMutableList()
        mutableList.shuffle(rng)
        finalOptions = mutableList
    }

    return finalOptions
}

/**
 * Matches a [QuestionSummary] against [selectedPaths] using the exact same hierarchy delimiter
 * and prefix construction as `calculateTopicTreeAsync`, without requiring `_topicTreeRoot` to be built first.
 */
fun QuestionSummary.matchesSelectedPaths(selectedPaths: Set<String>): Boolean {
    if (selectedPaths.isEmpty()) return true
    val rawPath = if (this.path.isNotBlank()) this.path else this.category
    val delimiter = if (rawPath.contains(">")) ">" else if (rawPath.contains("/")) "/" else "-"
    val parts = rawPath.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }
    var currentPathStr = ""
    for (part in parts) {
        currentPathStr = if (currentPathStr.isEmpty()) part else "$currentPathStr/$part"
        if (selectedPaths.contains(currentPathStr)) return true
    }
    return false
}

/**
 * Extracts the quiz source tag (`"Quiz"`, `"Learn"`, or `"Search"`) for a [QuizHistoryEntity].
 * Returns `null` if the entry belongs to an excluded source (`"Bookmarks"`, `"Review"`, `"Revision"`, or `"practice_mistakes"`).
 */
fun QuizHistoryEntity.resolvedSourceTag(): String? {
    if (this.status.startsWith("practice_mistakes")) return null
    if (this.status.startsWith("completed|")) {
        val tag = this.status.substringAfter("completed|").trim()
        return if (tag in setOf("Quiz", "Learn", "Search")) tag else null
    }
    if (this.status != "completed") return null
    return try {
        val trimmed = this.activeQuizData.trim()
        if (trimmed.startsWith("{")) {
            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = SharedGson.normal.fromJson(trimmed, mapType)
            val tag = (map["sourceTag"] as? String)?.trim()
            if (tag != null) {
                return if (tag in setOf("Quiz", "Learn", "Search")) tag else null
            }
        }
        when {
            this.quizLabel.contains("Learn", ignoreCase = true) -> "Learn"
            this.quizLabel.contains("Search", ignoreCase = true) -> "Search"
            else -> "Quiz"
        }
    } catch (_: Exception) {
        "Quiz"
    }
}
