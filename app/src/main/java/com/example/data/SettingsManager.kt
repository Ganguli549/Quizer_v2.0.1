package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quiz_settings", Context.MODE_PRIVATE)

    // --- Global Default Fallbacks ---
    var shuffleQuestions: Boolean
        get() = prefs.getBoolean("shuffle_questions", true)
        set(value) = prefs.edit().putBoolean("shuffle_questions", value).apply()

    var shuffleOptions: Boolean
        get() = prefs.getBoolean("shuffle_options", true)
        set(value) = prefs.edit().putBoolean("shuffle_options", value).apply()

    var useDistractors: Boolean
        get() = prefs.getBoolean("use_distractors", false)
        set(value) = prefs.edit().putBoolean("use_distractors", value).apply()

    var timerMinutes: Int
        get() = prefs.getInt("timer_minutes", 0)
        set(value) = prefs.edit().putInt("timer_minutes", value).apply()

    var questionTimerSeconds: Int
        get() = prefs.getInt("question_timer_seconds", 0)
        set(value) = prefs.edit().putInt("question_timer_seconds", value).apply()

    var lastSelectedBookId: String?
        get() = prefs.getString("last_selected_book_id", null)
        set(value) = prefs.edit().putString("last_selected_book_id", value).apply()

    var autoNextSeconds: Int
        get() = prefs.getInt("auto_next_seconds", 0)
        set(value) = prefs.edit().putInt("auto_next_seconds", value).apply()

    var autoSequence: Boolean
        get() = prefs.getBoolean("auto_sequence", true)
        set(value) = prefs.edit().putBoolean("auto_sequence", value).apply()

    var questionFlow: Boolean
        get() = prefs.getBoolean("question_flow", false)
        set(value) = prefs.edit().putBoolean("question_flow", value).apply()

    var quizMode: String
        get() = prefs.getString("quiz_mode", "practice") ?: "practice"
        set(value) = prefs.edit().putString("quiz_mode", value).apply()

    var quizFlowMode: String
        get() = prefs.getString("quiz_flow_mode", "sequence") ?: "sequence"
        set(value) = prefs.edit().putString("quiz_flow_mode", value).apply()

    var correctMark: Float
        get() = prefs.getFloat("correct_mark", 1f)
        set(value) = prefs.edit().putFloat("correct_mark", value).apply()

    var wrongMark: Float
        get() = prefs.getFloat("wrong_mark", 0f)
        set(value) = prefs.edit().putFloat("wrong_mark", value).apply()

    // --- Book-Isolated Main Quiz (Home Screen) Settings ---
    fun getQuizMode(bookId: String): String {
        if (bookId.isBlank()) return quizMode
        return prefs.getString("quiz_mode_$bookId", null) ?: quizMode
    }

    fun setQuizMode(bookId: String, value: String) {
        quizMode = value
        if (bookId.isNotBlank()) {
            prefs.edit().putString("quiz_mode_$bookId", value).apply()
        }
    }

    fun getQuizFlowMode(bookId: String): String {
        if (bookId.isBlank()) return quizFlowMode
        return prefs.getString("quiz_flow_mode_$bookId", null) ?: quizFlowMode
    }

    fun setQuizFlowMode(bookId: String, value: String) {
        quizFlowMode = value
        if (bookId.isNotBlank()) {
            prefs.edit().putString("quiz_flow_mode_$bookId", value).apply()
        }
    }

    fun getShuffleQuestions(bookId: String): Boolean {
        if (bookId.isBlank()) return shuffleQuestions
        return if (prefs.contains("shuffle_questions_$bookId")) {
            prefs.getBoolean("shuffle_questions_$bookId", true)
        } else {
            shuffleQuestions
        }
    }

    fun setShuffleQuestions(bookId: String, value: Boolean) {
        shuffleQuestions = value
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("shuffle_questions_$bookId", value).apply()
        }
    }

    fun getShuffleOptions(bookId: String): Boolean {
        if (bookId.isBlank()) return shuffleOptions
        return if (prefs.contains("shuffle_options_$bookId")) {
            prefs.getBoolean("shuffle_options_$bookId", true)
        } else {
            shuffleOptions
        }
    }

    fun setShuffleOptions(bookId: String, value: Boolean) {
        shuffleOptions = value
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("shuffle_options_$bookId", value).apply()
        }
    }

    fun getUseDistractors(bookId: String): Boolean {
        if (bookId.isBlank()) return useDistractors
        return if (prefs.contains("use_distractors_$bookId")) {
            prefs.getBoolean("use_distractors_$bookId", false)
        } else {
            useDistractors
        }
    }

    fun setUseDistractors(bookId: String, value: Boolean) {
        useDistractors = value
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("use_distractors_$bookId", value).apply()
        }
    }

    fun getQuestionFlow(bookId: String): Boolean {
        if (bookId.isBlank()) return questionFlow
        return if (prefs.contains("question_flow_$bookId")) {
            prefs.getBoolean("question_flow_$bookId", false)
        } else {
            questionFlow
        }
    }

    fun setQuestionFlow(bookId: String, value: Boolean) {
        questionFlow = value
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("question_flow_$bookId", value).apply()
        }
    }

    fun getSequenceOffset(bookId: String): Int {
        if (bookId.isBlank()) return 1
        return prefs.getInt("sequence_offset_$bookId", 1).coerceAtLeast(1)
    }

    fun setSequenceOffset(bookId: String, value: Int) {
        if (bookId.isNotBlank()) {
            prefs.edit().putInt("sequence_offset_$bookId", value.coerceAtLeast(1)).apply()
        }
    }

    fun getAutoSequence(bookId: String): Boolean {
        if (bookId.isBlank()) return autoSequence
        return if (prefs.contains("auto_sequence_$bookId")) {
            prefs.getBoolean("auto_sequence_$bookId", true)
        } else {
            autoSequence
        }
    }

    fun setAutoSequence(bookId: String, value: Boolean) {
        autoSequence = value
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("auto_sequence_$bookId", value).apply()
        }
    }

    fun getCorrectMark(bookId: String): Float {
        if (bookId.isBlank()) return correctMark
        return if (prefs.contains("correct_mark_$bookId")) {
            prefs.getFloat("correct_mark_$bookId", 1f)
        } else {
            correctMark
        }
    }

    fun setCorrectMark(bookId: String, value: Float) {
        correctMark = value
        if (bookId.isNotBlank()) {
            prefs.edit().putFloat("correct_mark_$bookId", value).apply()
        }
    }

    fun getWrongMark(bookId: String): Float {
        if (bookId.isBlank()) return wrongMark
        return if (prefs.contains("wrong_mark_$bookId")) {
            prefs.getFloat("wrong_mark_$bookId", 0f)
        } else {
            wrongMark
        }
    }

    fun setWrongMark(bookId: String, value: Float) {
        wrongMark = value
        if (bookId.isNotBlank()) {
            prefs.edit().putFloat("wrong_mark_$bookId", value).apply()
        }
    }

    fun getAutoNextSeconds(bookId: String): Int {
        if (bookId.isBlank()) return autoNextSeconds
        return if (prefs.contains("auto_next_seconds_$bookId")) {
            prefs.getInt("auto_next_seconds_$bookId", 0)
        } else {
            autoNextSeconds
        }
    }

    fun setAutoNextSeconds(bookId: String, value: Int) {
        autoNextSeconds = value
        if (bookId.isNotBlank()) {
            prefs.edit().putInt("auto_next_seconds_$bookId", value).apply()
        }
    }

    fun getQuestionTimerSeconds(bookId: String): Int {
        if (bookId.isBlank()) return questionTimerSeconds
        return if (prefs.contains("question_timer_seconds_$bookId")) {
            prefs.getInt("question_timer_seconds_$bookId", 0)
        } else {
            questionTimerSeconds
        }
    }

    fun setQuestionTimerSeconds(bookId: String, value: Int) {
        questionTimerSeconds = value
        if (bookId.isNotBlank()) {
            prefs.edit().putInt("question_timer_seconds_$bookId", value).apply()
        }
    }

    fun getHomeExpandedPaths(bookId: String): Set<String> {
        if (bookId.isBlank()) return emptySet()
        return prefs.getStringSet("home_expanded_paths_$bookId", emptySet())?.toSet() ?: emptySet()
    }

    fun setHomeExpandedPaths(bookId: String, value: Set<String>) {
        if (bookId.isNotBlank()) {
            prefs.edit().putStringSet("home_expanded_paths_$bookId", value.toSet()).apply()
        }
    }

    // --- Book-Isolated Learn Mode Configuration (Isolated from Home Start Quiz) ---
    var learnFlowMode: String
        get() = prefs.getString("learn_flow_mode", "progressive") ?: "progressive"
        set(value) = prefs.edit().putString("learn_flow_mode", value).apply()

    var learnQuestionLimit: Int
        get() = prefs.getInt("learn_question_limit", -1)
        set(value) = prefs.edit().putInt("learn_question_limit", value).apply()

    var learnShuffleQuestions: Boolean
        get() = prefs.getBoolean("learn_shuffle_questions", false)
        set(value) = prefs.edit().putBoolean("learn_shuffle_questions", value).apply()

    var learnShuffleOptions: Boolean
        get() = prefs.getBoolean("learn_shuffle_options", true)
        set(value) = prefs.edit().putBoolean("learn_shuffle_options", value).apply()

    var learnUseDistractors: Boolean
        get() = prefs.getBoolean("learn_use_distractors", true)
        set(value) = prefs.edit().putBoolean("learn_use_distractors", value).apply()

    var learnQuestionFlow: Boolean
        get() = prefs.getBoolean("learn_question_flow", false)
        set(value) = prefs.edit().putBoolean("learn_question_flow", value).apply()

    var learnSelectedPaths: Set<String>
        get() = prefs.getStringSet("learn_selected_paths", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("learn_selected_paths", value).apply()

    fun getLearnFlowMode(bookId: String): String {
        if (bookId.isBlank()) return learnFlowMode
        return prefs.getString("learn_flow_mode_$bookId", null) ?: learnFlowMode
    }

    fun setLearnFlowMode(bookId: String, value: String) {
        if (bookId.isNotBlank()) {
            prefs.edit().putString("learn_flow_mode_$bookId", value).apply()
        } else {
            learnFlowMode = value
        }
    }

    fun getLearnQuestionLimit(bookId: String): Int {
        if (bookId.isBlank()) return learnQuestionLimit
        return if (prefs.contains("learn_question_limit_$bookId")) {
            prefs.getInt("learn_question_limit_$bookId", -1)
        } else {
            learnQuestionLimit
        }
    }

    fun setLearnQuestionLimit(bookId: String, value: Int) {
        if (bookId.isNotBlank()) {
            prefs.edit().putInt("learn_question_limit_$bookId", value).apply()
        } else {
            learnQuestionLimit = value
        }
    }

    fun getLearnShuffleQuestions(bookId: String): Boolean {
        if (bookId.isBlank()) return learnShuffleQuestions
        return if (prefs.contains("learn_shuffle_questions_$bookId")) {
            prefs.getBoolean("learn_shuffle_questions_$bookId", false)
        } else {
            learnShuffleQuestions
        }
    }

    fun setLearnShuffleQuestions(bookId: String, value: Boolean) {
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("learn_shuffle_questions_$bookId", value).apply()
        } else {
            learnShuffleQuestions = value
        }
    }

    fun getLearnShuffleOptions(bookId: String): Boolean {
        if (bookId.isBlank()) return learnShuffleOptions
        return if (prefs.contains("learn_shuffle_options_$bookId")) {
            prefs.getBoolean("learn_shuffle_options_$bookId", true)
        } else {
            learnShuffleOptions
        }
    }

    fun setLearnShuffleOptions(bookId: String, value: Boolean) {
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("learn_shuffle_options_$bookId", value).apply()
        } else {
            learnShuffleOptions = value
        }
    }

    fun getLearnUseDistractors(bookId: String): Boolean {
        if (bookId.isBlank()) return learnUseDistractors
        return if (prefs.contains("learn_use_distractors_$bookId")) {
            prefs.getBoolean("learn_use_distractors_$bookId", true)
        } else {
            learnUseDistractors
        }
    }

    fun setLearnUseDistractors(bookId: String, value: Boolean) {
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("learn_use_distractors_$bookId", value).apply()
        } else {
            learnUseDistractors = value
        }
    }

    fun getLearnQuestionFlow(bookId: String): Boolean {
        if (bookId.isBlank()) return learnQuestionFlow
        return if (prefs.contains("learn_question_flow_$bookId")) {
            prefs.getBoolean("learn_question_flow_$bookId", false)
        } else {
            learnQuestionFlow
        }
    }

    fun setLearnQuestionFlow(bookId: String, value: Boolean) {
        if (bookId.isNotBlank()) {
            prefs.edit().putBoolean("learn_question_flow_$bookId", value).apply()
        } else {
            learnQuestionFlow = value
        }
    }

    fun getLearnExpandedPaths(bookId: String): Set<String> {
        if (bookId.isBlank()) return emptySet()
        return prefs.getStringSet("learn_expanded_paths_$bookId", emptySet())?.toSet() ?: emptySet()
    }

    fun setLearnExpandedPaths(bookId: String, value: Set<String>) {
        if (bookId.isNotBlank()) {
            prefs.edit().putStringSet("learn_expanded_paths_$bookId", value.toSet()).apply()
        }
    }

    // --- Book-Isolated Exam Mode Configuration ---
    /**
     * Retrieves the remembered Exam Mode configuration for [bookId].
     * Stored per-book under `"exam_settings_$bookId"` so changing the question count
     * (10 / 25 / 50 / All) never resets the book's marking scheme or timer settings.
     * Falls back to legacy `"exam_settings_${bookId}_${limit}"` if present.
     */
    fun getExamSettings(bookId: String?, limit: Int = 0): String? {
        if (bookId.isNullOrBlank()) return null
        val bookScoped = prefs.getString("exam_settings_$bookId", null)
        if (bookScoped != null) return bookScoped

        // Fallback to any legacy per-limit key and migrate it to the book-isolated key
        val legacyCandidates = listOf(
            "exam_settings_${bookId}_${limit}",
            "exam_settings_${bookId}_0",
            "exam_settings_${bookId}_10",
            "exam_settings_${bookId}_25",
            "exam_settings_${bookId}_50"
        )
        for (legacyKey in legacyCandidates) {
            val found = prefs.getString(legacyKey, null)
            if (found != null) {
                prefs.edit().putString("exam_settings_$bookId", found).apply()
                return found
            }
        }
        return null
    }

    fun setExamSettings(bookId: String?, limit: Int = 0, settingsJson: String) {
        if (bookId.isNullOrBlank()) return
        prefs.edit()
            .putString("exam_settings_$bookId", settingsJson)
            .putString("exam_settings_${bookId}_${limit}", settingsJson)
            .apply()
    }

    fun getActiveQuizState(bookId: String): String? {
        val allStatesStr = prefs.getString("active_quiz_states_map", "{}") ?: "{}"
        try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val map: Map<String, String> = com.example.data.SharedGson.normal.fromJson(allStatesStr, type)
            return map[bookId]
        } catch (e: Exception) {
            return null
        }
    }

    fun setActiveQuizState(bookId: String, stateStr: String?) {
        val allStatesStr = prefs.getString("active_quiz_states_map", "{}") ?: "{}"
        try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val map: MutableMap<String, String> = com.example.data.SharedGson.normal.fromJson(allStatesStr, type) ?: mutableMapOf()
            if (stateStr == null) {
                map.remove(bookId)
            } else {
                map[bookId] = stateStr
            }
            prefs.edit().putString("active_quiz_states_map", com.example.data.SharedGson.normal.toJson(map)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var isMatteMode: Boolean
        get() = prefs.getBoolean("is_matte_mode", false)
        set(value) = prefs.edit().putBoolean("is_matte_mode", value).apply()

    var appThemeMode: String
        get() = prefs.getString("app_theme_mode", "system") ?: "system"
        set(value) { prefs.edit().putString("app_theme_mode", value).commit() }

    var appTheme: String
        get() = prefs.getString("app_theme", "dynamic") ?: "dynamic"
        set(value) { prefs.edit().putString("app_theme", value).commit() }

    var appIcon: String
        get() = prefs.getString("app_icon", "ocean") ?: "ocean"
        set(value) { prefs.edit().putString("app_icon", value).commit() }

    var favoriteTopics: Set<String>
        get() = prefs.getStringSet("favorite_topics", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("favorite_topics", value).apply()

    fun getFavoriteTopics(bookId: String): Set<String> {
        if (bookId.isBlank()) return favoriteTopics
        return if (prefs.contains("favorite_topics_$bookId")) {
            prefs.getStringSet("favorite_topics_$bookId", emptySet())?.toSet() ?: emptySet()
        } else {
            favoriteTopics
        }
    }

    fun setFavoriteTopics(bookId: String, value: Set<String>) {
        if (bookId.isNotBlank()) {
            prefs.edit().putStringSet("favorite_topics_$bookId", value.toSet()).apply()
        } else {
            favoriteTopics = value
        }
    }

    var removedBooks: Set<String>
        get() = prefs.getStringSet("removed_books", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("removed_books", value).apply()

    var timerSoundEnabled: Boolean
        get() = prefs.getBoolean("timer_sound_enabled", true)
        set(value) = prefs.edit().putBoolean("timer_sound_enabled", value).apply()

    var clickSoundEnabled: Boolean
        get() = prefs.getBoolean("click_sound_enabled", false)
        set(value) = prefs.edit().putBoolean("click_sound_enabled", value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean("haptic_feedback_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback_enabled", value).apply()

    fun getSelectedPaths(bookId: String): Set<String> {
        return prefs.getStringSet("selected_paths_$bookId", emptySet())?.toSet() ?: emptySet()
    }
    fun setSelectedPaths(bookId: String, value: Set<String>) {
        prefs.edit().putStringSet("selected_paths_$bookId", value.toSet()).apply()
    }

    fun getSelectedCategories(bookId: String): Set<String> {
        return prefs.getStringSet("selected_categories_$bookId", emptySet())?.toSet() ?: emptySet()
    }
    fun setSelectedCategories(bookId: String, value: Set<String>) {
        prefs.edit().putStringSet("selected_categories_$bookId", value.toSet()).apply()
    }

    fun getQuestionLimit(bookId: String): Int {
        return prefs.getInt("question_limit_$bookId", 0)
    }
    fun setQuestionLimit(bookId: String, value: Int) {
        prefs.edit().putInt("question_limit_$bookId", value).apply()
    }

    var readBookQuizMode: Boolean
        get() = prefs.getBoolean("read_book_quiz_mode", false)
        set(value) = prefs.edit().putBoolean("read_book_quiz_mode", value).apply()

    fun getBookScrollIndex(bookId: String): Int {
        return prefs.getInt("scroll_index_$bookId", 0)
    }

    fun getBookScrollOffset(bookId: String): Int {
        return prefs.getInt("scroll_offset_$bookId", 0)
    }

    fun setBookScrollPosition(bookId: String, index: Int, offset: Int) {
        prefs.edit().putInt("scroll_index_$bookId", index).putInt("scroll_offset_$bookId", offset).apply()
    }

    fun getShowBookmarks(bookId: String): Boolean {
        return prefs.getBoolean("show_bookmarks_$bookId", false)
    }

    fun setShowBookmarks(bookId: String, value: Boolean) {
        prefs.edit().putBoolean("show_bookmarks_$bookId", value).apply()
    }

    fun getBookOrder(): List<String> {
        val raw = prefs.getString("custom_book_order", null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setBookOrder(order: List<String>) {
        val jsonArray = org.json.JSONArray()
        order.forEach { jsonArray.put(it) }
        prefs.edit().putString("custom_book_order", jsonArray.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        try {
            context.getSharedPreferences("book_uris", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
    }

    fun getRetainedQuestionOptions(bookId: String): Map<Long, List<Int>> {
        if (bookId.isBlank()) return emptyMap()
        val json = prefs.getString("retained_question_options_$bookId", null) ?: return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<Int>>>() {}.type
            val raw: Map<String, List<Int>> = SharedGson.normal.fromJson(json, type) ?: emptyMap()
            raw.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveRetainedQuestionOptions(bookId: String, newEntries: Map<Long, List<Int>>) {
        if (bookId.isBlank() || newEntries.isEmpty()) return
        try {
            val merged = getRetainedQuestionOptions(bookId).toMutableMap()
            merged.putAll(newEntries)
            val serialized = merged.entries.associate { (k, v) -> k.toString() to v }
            prefs.edit().putString("retained_question_options_$bookId", SharedGson.normal.toJson(serialized)).apply()
        } catch (_: Exception) {}
    }

    /**
     * Comprehensively purges all remembered settings, active quiz states, scroll positions,
     * expanded scopes, exam configurations, learn configurations, and persisted URI mappings
     * for [bookId] when a book is deleted or replaced.
     */
    fun clearBookSettings(bookId: String) {
        if (bookId.isBlank()) return

        // 1. Remove active quiz state entry from the JSON map
        setActiveQuizState(bookId, null)

        // 2. Remove all SharedPreferences keys belonging to this bookId
        val allKeys = prefs.all.keys.toList()
        val editor = prefs.edit()
        val suffix = "_$bookId"
        val examPrefix = "exam_settings_${bookId}_"
        val learnExamPrefix = "exam_settings_${bookId}_learn"

        for (key in allKeys) {
            if (key.endsWith(suffix) ||
                key.startsWith(examPrefix) ||
                key.startsWith(learnExamPrefix)
            ) {
                editor.remove(key)
            }
        }

        // Also explicitly remove known keys in case any weren't yet flushed
        editor
            .remove("selected_paths_$bookId")
            .remove("selected_categories_$bookId")
            .remove("question_limit_$bookId")
            .remove("scroll_index_$bookId")
            .remove("scroll_offset_$bookId")
            .remove("show_bookmarks_$bookId")
            .remove("exam_settings_$bookId")
            .remove("quiz_mode_$bookId")
            .remove("quiz_flow_mode_$bookId")
            .remove("shuffle_questions_$bookId")
            .remove("shuffle_options_$bookId")
            .remove("use_distractors_$bookId")
            .remove("question_flow_$bookId")
            .remove("sequence_offset_$bookId")
            .remove("auto_sequence_$bookId")
            .remove("correct_mark_$bookId")
            .remove("wrong_mark_$bookId")
            .remove("auto_next_seconds_$bookId")
            .remove("question_timer_seconds_$bookId")
            .remove("home_expanded_paths_$bookId")
            .remove("learn_flow_mode_$bookId")
            .remove("learn_question_limit_$bookId")
            .remove("learn_shuffle_questions_$bookId")
            .remove("learn_shuffle_options_$bookId")
            .remove("learn_use_distractors_$bookId")
            .remove("learn_question_flow_$bookId")
            .remove("learn_expanded_paths_$bookId")
            .remove("favorite_topics_$bookId")
            // Legacy key names
            .remove("paths_$bookId")
            .remove("cats_$bookId")
            .remove("limit_$bookId")
            .remove("active_quiz_state_$bookId")
            .remove("retained_question_options_$bookId")
            .remove("scroll_idx_$bookId")
            .remove("scroll_off_$bookId")

        if (lastSelectedBookId == bookId) {
            editor.remove("last_selected_book_id")
        }
        editor.apply()

        val currentOrder = getBookOrder()
        if (currentOrder.contains(bookId)) {
            setBookOrder(currentOrder.filter { it != bookId })
        }

        // 3. Remove persisted document URI mapping for this book
        try {
            context.getSharedPreferences("book_uris", Context.MODE_PRIVATE)
                .edit()
                .remove(bookId)
                .apply()
        } catch (_: Exception) {}
    }
}
