# Quizer App (`v3.0.1`) — Complete Fix & Improvement Specification

This specification contains the exact root-cause analysis and drop-in code changes for:
1. **Exam Mode Quiz Configuration (Book-Isolated & Remembered)**: Persists Marking Scheme (`Off`, `CGL Tier 1`, `CGL Tier 2`, `SSC CHSL`, `Custom`), `+Correct` / `-Wrong` marks, Exam Timer preset (`Off`, `Auto`, `15m`, `30m`, `60m`, `Custom`), and custom timer duration **per book (`bookId`)**—independent of question count changes.
2. **Learn Mode Quiz Configuration & Expanded Topic Scopes (Book-Isolated & Remembered)**: Persists Learn Mode `Question Count` (`10`, `25`, `50`, `All`), `Flow Mode` (`Progressive` / `Sequence`), `Shuffle Questions`, `Shuffle Options`, `Dynamic Distractors`, `Question Flow`, `Expanded Topic Scopes`, and `Favorite Topics` **per book (`bookId`)**.
3. **Learn Mode Quiz Configuration Effective Application & Complete Isolation from Home `Start Quiz`**: Fixes the bug where selecting `10` questions in Learn Mode still loaded all questions of that scope, and isolates Learn Mode completely from the Home Screen's Main Quiz (`Start Quiz`).
4. **Improved Cache & Settings Management on Book Delete or Replace**: Fixes broken key names in `SettingsManager.clearBookSettings(bookId)` and cleans up all book-scoped settings, active quiz states, AI chat history, ViewModel state maps, extracted book folders, and rendering caches when a book is deleted or replaced.
5. **ReadBook Search Highlight Retention Fix (Without Fix 3)**: Fixes searched words staying highlighted in yellow in `ReadBookScreen` while keeping `ReadBookQuestionItem` quiz selection reset on scroll (`remember(question.id, inBookQuizMode)`).

---

## Part 1: Root-Cause Diagnosis

### 1. Why Learn Mode Quiz Configuration (e.g., 10 Questions) Did Not Apply (`LearnScreen.kt` & `MainViewModel.kt`)
1. **`viewModel.isLearnMode` Was Never Set to `true`**:
   - In `MainViewModel.kt` (line 165), `var isLearnMode = false` is declared, and `HomeScreen.kt` sets `viewModel.isLearnMode = false`, but **`LearnScreen.kt` and `setQuizQuestionsByTopic()` never set `viewModel.isLearnMode = true` anywhere in the codebase**.
   - Consequently, inside `MainViewModel.applySortingAndLimitToQuestions()` (lines 1163–1165):
     ```kotlin
     val currentFlow = if (isLearnMode) settingsManager.learnFlowMode else _quizFlowMode.value
     val currentShuffle = if (isLearnMode) settingsManager.learnShuffleQuestions else _shuffleQuestions.value
     val currentLimit = if (isLearnMode) settingsManager.learnQuestionLimit else -1
     ```
     Because `isLearnMode` was always `false`, `currentLimit` **always evaluated to `-1` (All questions)**! Even when `10` was selected in Learn Settings, `processQuestionPool` received `limit = -1` and put **all questions of that topic scope** into the quiz.
   - Similarly, in `QuizScreen.kt` (lines 95–97), `currentUseDistractors`, `currentShuffleOptions`, and `currentQuestionFlow` all checked `if (viewModel.isLearnMode)`—which was always `false`, so `QuizScreen` ignored Learn Settings and used Home Screen's settings instead.
2. **`LearnScreen` Mutated Home Screen's `selectedCategories` and Triggered a Competing `generateQuizQuestions()` Job (`LearnScreen.kt` line 199)**:
   - In `LearnScreen.kt` line 199, `onPlayTopic` ran:
     ```kotlin
     viewModel.selectedCategories.value.forEach { c -> viewModel.toggleCategory(c) }
     ```
     Calling `toggleCategory(c)` modified Home Screen's `_selectedCategories` and launched `generateQuizQuestions()` (`quizGenJob`), which raced with `applySortingAndLimitToQuestions()` and overwrote `_quizQuestions.value`.
3. **Expanded Scopes in `LearnScreen` Used Ephemeral `remember` (`LearnScreen.kt` line 45)**:
   - `var expandedPaths by remember { mutableStateOf(setOf<String>()) }` was not saved in `SettingsManager` per `bookId`, so starting a topic quiz and returning to `LearnScreen` (or switching screens) collapsed all expanded topics.

### 2. Why Exam Mode Configuration Was Not Properly Remembered Per Book (`SettingsManager.kt` & `ExamConfigDialog.kt`)
- `SettingsManager.getExamSettings(bookId, limit)` stored settings under `"exam_settings_${bookId}_${limit}"`. Changing the question count on `HomeScreen` (`10` $\rightarrow$ `25` $\rightarrow$ `50` $\rightarrow$ `All`) changed `limit` and caused `ExamConfigDialog` to look up a different key and reset Marking Scheme to `"Off"` and Timer to `"Auto"`.
- In `MainViewModel.loadActiveQuizState()` (line 1463), `val limit = qIds.size` looked up `"exam_settings_${bookId}_${qIds.size}"`, which failed whenever `"All"` (`limit = 0`) was used or when a scope had fewer questions than the limit.

### 3. Why Book Delete / Replace Left Orphaned Settings & Stale Caches (`SettingsManager.kt`, `HomeScreen.kt`, `MainViewModel.kt`)
- In `SettingsManager.clearBookSettings(bookId)` (lines 211–220):
  ```kotlin
  prefs.edit()
      .remove("paths_$bookId")             // Actual key is "selected_paths_$bookId"!
      .remove("cats_$bookId")              // Actual key is "selected_categories_$bookId"!
      .remove("limit_$bookId")             // Actual key is "question_limit_$bookId"!
      .remove("active_quiz_state_$bookId") // Actual storage is JSON map "active_quiz_states_map"!
      .remove("scroll_idx_$bookId")        // Actual key is "scroll_index_$bookId"!
      .remove("scroll_off_$bookId")        // Actual key is "scroll_offset_$bookId"!
      .remove("show_bookmarks_$bookId")
      .apply()
  ```
  Almost every key prefix in `clearBookSettings` mismatched the actual preference key names, leaving stale scope selections, active quiz states, scroll positions, and exam/learn settings behind when a book was deleted or replaced.

---

## Part 2: File-by-File Code Changes

### File 1: `app/src/main/java/com/example/data/SettingsManager.kt`

**What this fixes:**
1. Makes **Exam Mode configuration** book-isolated (`getExamSettings(bookId, limit)` / `setExamSettings(bookId, limit, settingsJson)`) under `"exam_settings_$bookId"` (with automatic fallback to legacy `"exam_settings_${bookId}_${limit}"`), so changing the question count never resets the book's Exam Mode configuration.
2. Makes **all Learn Mode settings** (`learnQuestionLimit`, `learnFlowMode`, `learnShuffleQuestions`, `learnShuffleOptions`, `learnUseDistractors`, `learnQuestionFlow`, `learnExpandedPaths`, `favoriteTopics`) **book-isolated** per `bookId` and completely separate from Home Screen's Main Quiz settings.
3. Makes **Home Screen Main Quiz settings** (`quizMode`, `quizFlowMode`, `shuffleQuestions`, `shuffleOptions`, `useDistractors`, `questionFlow`, `sequenceOffset`, `autoSequence`, `correctMark`, `wrongMark`, `autoNextSeconds`, `questionTimerSeconds`, `homeExpandedPaths`) **book-isolated** per `bookId`.
4. Rewrites `clearBookSettings(bookId: String)` to remove `bookId` from `"active_quiz_states_map"`, remove `bookId` from `"book_uris"`, clean up any matching keys in `prefs.all.keys` (`*_bookId`, `exam_settings_${bookId}_*`), and clear `lastSelectedBookId` if it matched `bookId`.

**Action — Replace the entire contents of `app/src/main/java/com/example/data/SettingsManager.kt` with:**

```kotlin
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

    fun clearAll() {
        prefs.edit().clear().apply()
        try {
            context.getSharedPreferences("book_uris", Context.MODE_PRIVATE).edit().clear().apply()
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
            .remove("scroll_idx_$bookId")
            .remove("scroll_off_$bookId")

        if (lastSelectedBookId == bookId) {
            editor.remove("last_selected_book_id")
        }
        editor.apply()

        // 3. Remove persisted document URI mapping for this book
        try {
            context.getSharedPreferences("book_uris", Context.MODE_PRIVATE)
                .edit()
                .remove(bookId)
                .apply()
        } catch (_: Exception) {}
    }
}
```

---

### File 2: `app/src/main/java/com/example/ui/components/ExamConfigDialog.kt`

**What this fixes:**
1. Keys `remember(bookId)` on `bookId` so each book's Exam Mode configuration (`markingScheme`, `customCorrectMarks`, `customWrongMarks`, `timerPreset`, `customTimerMinutes`) is loaded and isolated per book.
2. Uses local text states (`correctMarksText`, `wrongMarksText`, `customTimerText`) so typing decimal marks like `0.25` or `0.5` works smoothly without `Float.toString()` overwriting the cursor mid-keystroke.
3. Auto-saves changes to `settingsManager.setExamSettings(bookId, questionLimit, settingsJson)` immediately as the user configures the dialog as well as when tapping `Start Exam` / `Save Settings`, so even if the user closes the dialog or switches screens, the book's Exam configuration is remembered.

**Action — Replace the entire contents of `app/src/main/java/com/example/ui/components/ExamConfigDialog.kt` with:**

```kotlin
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
```

---

### File 3: `app/src/main/java/com/example/ui/screens/LearnScreen.kt`

**What this fixes:**
1. **Book-Isolated Expanded Scopes (`expandedPaths`)**: Loads `expandedPaths` via `viewModel.settingsManager.getLearnExpandedPaths(currentBookId)` and saves every expand/collapse toggle via `viewModel.settingsManager.setLearnExpandedPaths(currentBookId, updated)`. Your expanded topic hierarchy in `LearnScreen` now stays expanded across quizzes, screen changes, and app restarts, and is isolated per book.
2. **Book-Isolated Learn Quiz Configuration**: Loads and saves `limit` (`10`, `25`, `50`, `All`), `flowMode` (`Progressive` / `Sequence`), `shuffleQ`, `shuffleOpts`, `useDistractors`, and `questionFlow` per `currentBookId`.
3. **Effective Application & Isolation from Home `Start Quiz`**:
   - Removes `viewModel.selectedCategories.value.forEach { c -> viewModel.toggleCategory(c) }` (which was mutating Home Screen's category selection and launching a competing `generateQuizQuestions()` job).
   - Calls `viewModel.startLearnTopicQuiz(item.node) { onStartQuiz() }`, which sets `viewModel.isLearnMode = true`, applies the exact book-isolated Learn question count (`10`, `25`, `50`, or `All`), flow mode, and shuffle setting, and navigates to `QuizScreen` once the topic question list is ready.
   - When backing out of `LearnScreen` to `HomeScreen`, resets `viewModel.isLearnMode = false` and calls `viewModel.generateQuizQuestions()` so the Home Screen's Main Quiz question pool is completely unaffected.

**Action — In `app/src/main/java/com/example/ui/screens/LearnScreen.kt`, replace `fun LearnScreen(...)` (lines 38–216) with:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(viewModel: MainViewModel, onHome: () -> Unit, onStartQuiz: () -> Unit) {
    val topicTreeRoot by viewModel.topicTreeRoot.collectAsState()
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    val stats by viewModel.allStats.collectAsState()
    val favoriteTopics by viewModel.favoriteTopics.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()

    var expandedPaths by remember(currentBookId) {
        mutableStateOf(viewModel.settingsManager.getLearnExpandedPaths(currentBookId))
    }
    var showLearnConfigDialog by remember { mutableStateOf(false) }

    val handleBackToHome = {
        viewModel.isLearnMode = false
        viewModel.generateQuizQuestions()
        onHome()
    }

    androidx.activity.compose.BackHandler {
        handleBackToHome()
    }

    val toggleExpand = { path: String ->
        val updated = if (expandedPaths.contains(path)) expandedPaths - path else expandedPaths + path
        expandedPaths = updated
        viewModel.settingsManager.setLearnExpandedPaths(currentBookId, updated)
    }

    if (showLearnConfigDialog) {
        var limit by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnQuestionLimit(currentBookId))
        }
        var flow by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnFlowMode(currentBookId))
        }
        var shuffleQ by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnShuffleQuestions(currentBookId))
        }
        var shuffleOpts by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnShuffleOptions(currentBookId))
        }
        var useDistractors by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnUseDistractors(currentBookId))
        }
        var questionFlow by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getLearnQuestionFlow(currentBookId))
        }

        AlertDialog(
            onDismissRequest = { showLearnConfigDialog = false },
            title = { Text("Learn Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Question Count", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(selected = limit == 10, onClick = { limit = 10 }, label = { Text("10") })
                        FilterChip(selected = limit == 25, onClick = { limit = 25 }, label = { Text("25") })
                        FilterChip(selected = limit == 50, onClick = { limit = 50 }, label = { Text("50") })
                        FilterChip(selected = limit == -1, onClick = { limit = -1 }, label = { Text("All") })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Question Flow Mode", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        RadioButton(selected = flow == "progressive", onClick = { flow = "progressive" })
                        Text("Progressive", modifier = Modifier.clickable { flow = "progressive" }.padding(end = 12.dp))
                        RadioButton(selected = flow == "sequence", onClick = { flow = "sequence" })
                        Text("Sequence", modifier = Modifier.clickable { flow = "sequence" })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Advanced Quiz Options", style = MaterialTheme.typography.labelLarge)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = shuffleQ,
                            onClick = { shuffleQ = !shuffleQ },
                            label = { Text("Shuffle Questions") }
                        )
                        FilterChip(
                            selected = shuffleOpts,
                            onClick = { shuffleOpts = !shuffleOpts },
                            label = { Text("Shuffle Options") }
                        )
                        FilterChip(
                            selected = useDistractors,
                            onClick = { useDistractors = !useDistractors },
                            label = { Text("Dynamic Distractors") }
                        )
                        FilterChip(
                            selected = questionFlow,
                            onClick = { questionFlow = !questionFlow },
                            label = { Text("Question Flow") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Learn configuration is remembered per book and isolated from Home Start Quiz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.settingsManager.setLearnQuestionLimit(currentBookId, limit)
                    viewModel.settingsManager.setLearnFlowMode(currentBookId, flow)
                    viewModel.settingsManager.setLearnShuffleQuestions(currentBookId, shuffleQ)
                    viewModel.settingsManager.setLearnShuffleOptions(currentBookId, shuffleOpts)
                    viewModel.settingsManager.setLearnUseDistractors(currentBookId, useDistractors)
                    viewModel.settingsManager.setLearnQuestionFlow(currentBookId, questionFlow)
                    showLearnConfigDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLearnConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learn Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLearnConfigDialog = true }) {
                        Icon(Icons.Default.Settings, "Learn Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Roadmap & Topics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (topicTreeRoot == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val flatList = buildFlatList(topicTreeRoot!!.children.values.toList(), 0, expandedPaths)
                    if (flatList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No topics available in this book.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(flatList, key = { it.node.path }) { item ->
                                TopicNodeCard(
                                    node = item.node,
                                    level = item.level,
                                    isExpanded = expandedPaths.contains(item.node.path),
                                    onToggleExpand = { toggleExpand(item.node.path) },
                                    onPlayTopic = {
                                        viewModel.startLearnTopicQuiz(item.node) {
                                            onStartQuiz()
                                        }
                                    },
                                    isFavorite = favoriteTopics.contains(item.node.path),
                                    onToggleFavorite = { viewModel.toggleFavoriteTopic(item.node.path) },
                                    isCompleted = item.node.masteryLevel == "Mastered"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

### File 4: `app/src/main/java/com/example/ui/MainViewModel.kt`

**What this fixes:**
1. **`startLearnTopicQuiz(topic, onReady)` & `setQuizQuestionsByTopic(topic)`**:
   - Explicitly sets `isLearnMode = true`, `currentExamSettings = null`, `currentPracticeMistakesParentId = null`, and resets transient quiz state (`resetTransientQuizSessionState()`) **without** erasing Home Screen's saved `"RESUME ACTIVE QUIZ"` state.
   - Cancels any running `quizGenJob?.cancel()` and applies the book-isolated Learn settings (`getLearnFlowMode(bookId)`, `getLearnShuffleQuestions(bookId)`, `getLearnQuestionLimit(bookId)`—converting `10` / `25` / `50` to `Int` limit and `-1` to `null` for All).
   - Populates `_quizQuestions.value` and invokes `onReady()` on `Dispatchers.Main` so `QuizScreen` always opens with the exact configured question count (`10`, `25`, `50`, or `All`).
2. **Book-Isolated State Synchronization in `selectBook(bookId, forceRefresh)`**:
   - Loads book-isolated `_selectedPaths`, `_selectedCategories`, `_questionLimit`, `_quizFlowMode`, `_shuffleQuestions`, `_sequenceOffset`, and `_favoriteTopics` for `bookId`, and restores `_selectedTopic.value = null`.
3. **Comprehensive Book Delete & Replace Cache Cleanup (`purgeBookDataAndCaches`, `deleteBook`, `replaceBook`)**:
   - Cleans up all Room tables (including `chat_messages` for `bookId`, `${bookId}_read`, `${bookId}_edit`), all `SettingsManager` keys for `bookId`, all ViewModel runtime maps (`_chatHistory`, `_pendingUpdates`, `_sessionAttachments`, `_aiPromptTexts`, `_aiProcessingStatus`, `_aiProcessingProgress`), `MathFormattingCache` / `LatexDrawableCache`, and extracted disk directories (`Quizer/$bookId`, `Quizer/RestorePoints/$bookId`, `Quizer/Updates/${bookId}_*`).

**Action 4A — In `app/src/main/java/com/example/ui/MainViewModel.kt` (around lines 850–927), update `_quizFlowMode`, `_shuffleQuestions`, `setQuizQuestionsForBookmarks`, and `addCustomBook`:**

```kotlin
    private val _quizFlowMode = MutableStateFlow(settingsManager.getQuizFlowMode(initialBookId))
    val quizFlowMode = _quizFlowMode.asStateFlow()

    fun setQuizFlowMode(mode: String) {
        if (_quizFlowMode.value != mode) {
            _quizFlowMode.value = mode
            settingsManager.setQuizFlowMode(_currentBookId.value, mode)
            generateQuizQuestions()
        }
    }

    private val _shuffleQuestions = MutableStateFlow(settingsManager.getShuffleQuestions(initialBookId))
    val shuffleQuestions = _shuffleQuestions.asStateFlow()

    fun setShuffleQuestions(shuffle: Boolean) {
        if (_shuffleQuestions.value != shuffle) {
            _shuffleQuestions.value = shuffle
            settingsManager.setShuffleQuestions(_currentBookId.value, shuffle)
            generateQuizQuestions()
        }
    }

    fun setOnlyBookmarks(value: Boolean) {
        if (_onlyBookmarks.value != value) {
            _onlyBookmarks.value = value
            generateQuizQuestions()
        }
    }

    fun setQuizQuestionsForBookmarks() {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        resetTransientQuizSessionState()
        val bookmarkedIds = bookmarks.value.map { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val bookmarkedQs = allQs.filter { it.id in bookmarkedIds }
        applySortingAndLimitToQuestions(bookmarkedQs)
    }
```

**Action 4B — In `app/src/main/java/com/example/ui/MainViewModel.kt` (lines 1085–1190), replace `setQuizQuestionsByTopic`, `setQuizQuestionsForDailyRevision`, `setQuizQuestionsForWeakTopics`, `setQuizQuestionsForCritical`, `applySortingAndLimitToQuestions`, and `_sequenceOffset` with:**

```kotlin
    private val _selectedTopic = MutableStateFlow<TopicNode?>(null)

    fun resetTransientQuizSessionState() {
        activeCurrentIndex = 0
        activeQTimes = mutableMapOf()
        activeSelectedAnswers = mutableMapOf()
        activeLockedAnswers = mutableMapOf()
        activeTimeRemainingMs = -1L
        activeQuizSessionId = System.currentTimeMillis()
    }

    fun setQuizQuestionsByTopic(topic: TopicNode) {
        startLearnTopicQuiz(topic, onReady = null)
    }

    fun startLearnTopicQuiz(topic: TopicNode, onReady: (() -> Unit)? = null) {
        isLearnMode = true
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        currentPracticeMistakesParentId = null
        resetTransientQuizSessionState()
        _selectedTopic.value = topic
        _onlyBookmarks.value = false
        val allQs = _allBookQuestionSummaries.value
        val questionsInTopic = allQs.filter { it.id in topic.questionIds }
        applySortingAndLimitToQuestions(questionsInTopic, onReady)
    }

    fun setQuizQuestionsForDailyRevision() {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        resetTransientQuizSessionState()
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val now = System.currentTimeMillis()
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val revisionQs = allQs.filter { q ->
            val stat = statsMap[q.id]
            stat != null && stat.nextReview <= now
        }
        applySortingAndLimitToQuestions(revisionQs)
    }

    fun setQuizQuestionsForWeakTopics() {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        resetTransientQuizSessionState()
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val weakQs = allQs.filter { q ->
            val stat = statsMap[q.id]
            stat != null && stat.attempts > 0 && (stat.correct.toFloat() / stat.attempts) < 0.5f
        }
        applySortingAndLimitToQuestions(weakQs)
    }

    fun setQuizQuestionsForCritical() {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        resetTransientQuizSessionState()
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val now = System.currentTimeMillis()
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val criticalQs = allQs
            .filter { q -> (statsMap[q.id]?.wrong ?: 0) > 0 }
            .sortedWith(
                compareByDescending<com.example.data.QuestionSummary> { q ->
                    val st = statsMap[q.id]
                    st != null && !QuestionPoolProcessor.isQuestionMastered(st)
                }.thenByDescending { q ->
                    val st = statsMap[q.id]
                    if (st != null) QuestionPoolProcessor.computeMistakeUrgencyScore(st, now) else 0f
                }
            )
            .take(30)
        applySortingAndLimitToQuestions(criticalQs)
    }

    @androidx.annotation.VisibleForTesting
    internal fun processQuestionPool(
        pool: List<com.example.data.QuestionSummary>,
        flowMode: String,
        isShuffle: Boolean,
        limit: Int?,
        offset: Int = 1,
        customStatsMap: Map<Long, com.example.data.QuestionStatEntity>? = null
    ): List<com.example.data.QuestionSummary> {
        val statsMap = customStatsMap ?: allStats.value.associateBy { it.questionId }
        return QuestionPoolProcessor.process(pool, flowMode, isShuffle, limit, offset, statsMap)
    }

    private fun applySortingAndLimitToQuestions(
        baseList: List<com.example.data.QuestionSummary>,
        onReady: (() -> Unit)? = null
    ) {
        val bookId = _currentBookId.value
        val currentFlow = if (isLearnMode) settingsManager.getLearnFlowMode(bookId) else _quizFlowMode.value
        val currentShuffle = if (isLearnMode) settingsManager.getLearnShuffleQuestions(bookId) else _shuffleQuestions.value
        val rawLearnLimit = settingsManager.getLearnQuestionLimit(bookId)
        val currentLimit: Int? = if (isLearnMode) {
            if (rawLearnLimit > 0) rawLearnLimit else null
        } else {
            null
        }

        quizGenJob?.cancel()
        quizGenJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val filtered = processQuestionPool(baseList, currentFlow, currentShuffle, currentLimit)
            val idsToFetch = filtered.map { it.id }
            val indexMap = idsToFetch.withIndex().associate { it.value to it.index }
            val fullQuestions = repository.getQuestionsByBookAndIds(bookId, idsToFetch).resolveHeaders()
            if (isActive) {
                _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                activeQuizSessionId = System.currentTimeMillis()
                if (onReady != null) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onReady.invoke()
                    }
                }
            }
        }
    }

    private val _sequenceOffset = MutableStateFlow(settingsManager.getSequenceOffset(initialBookId))
    val sequenceOffset = _sequenceOffset.asStateFlow()

    fun setSequenceOffset(offset: Int) {
        val coerced = offset.coerceAtLeast(1)
        _sequenceOffset.value = coerced
        settingsManager.setSequenceOffset(_currentBookId.value, coerced)
        generateQuizQuestions()
    }
```

**Action 4C — In `app/src/main/java/com/example/ui/MainViewModel.kt`, inside `generateQuizQuestions()`, `setQuizQuestionsByIds()`, `practiceWrongQuiz()`, `saveActiveQuizState()`, `loadActiveQuizState()`, `toggleFavoriteTopic()`, and `selectBook()`, update as follows:**

1. Inside `generateQuizQuestions()` (around lines 1234–1236), use the Main Quiz settings directly since `generateQuizQuestions()` is exclusively for the Home Screen's Main Quiz:
```kotlin
            val currentFlowMode = _quizFlowMode.value
            val isShuffle = _shuffleQuestions.value

            filtered = processQuestionPool(filtered, currentFlowMode, isShuffle, limit, offset)
```

2. Inside `setQuizQuestionsByIds` (line 1249) and `practiceWrongQuiz` (line 1265), ensure `isLearnMode = false` and `resetTransientQuizSessionState()` are called:
```kotlin
    fun setQuizQuestionsByIds(bookId: String, ids: List<Long>) {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        resetTransientQuizSessionState()
        _selectedTopic.value = null
        _onlyBookmarks.value = false
```
```kotlin
    fun practiceWrongQuiz(historyId: Long?) {
        isLearnMode = false
        isActiveQuizStandardSequence = false
        currentPracticeMistakesParentId = historyId
        currentExamSettings = null
        resetTransientQuizSessionState()
```

3. Inside `saveActiveQuizState()` and `loadActiveQuizState()` (lines 1383–1470), persist `"examSettings"` in the active quiz state and load book-isolated `getExamSettings(bookId, limit)`:
```kotlin
    fun saveActiveQuizState(currentIndex: Int, selectedAnswers: Map<Int, Set<Int>>, lockedAnswers: Map<Int, Boolean>, timeRemainingMs: Long, qTimes: Map<Int, Long> = emptyMap()) {
        if (isLearnMode || currentPracticeMistakesParentId != null) return
        val state = mutableMapOf<String, Any>(
            "bookId" to currentBookId.value,
            "questions" to _quizQuestions.value.map { it.id },
            "currentIndex" to currentIndex,
            "selectedAnswers" to selectedAnswers,
            "lockedAnswers" to lockedAnswers,
            "timeRemainingMs" to timeRemainingMs,
            "qTimes" to qTimes,
            "sessionId" to activeQuizSessionId,
            "quizMode" to settingsManager.getQuizMode(currentBookId.value)
        )
        currentExamSettings?.let {
            state["examSettingsJson"] = com.example.data.SharedGson.normal.toJson(it)
        }
        settingsManager.setActiveQuizState(currentBookId.value, com.example.data.SharedGson.normal.toJson(state))
        _activeQuizTrigger.value++
    }
```
And inside `loadActiveQuizState()` (around lines 1462–1468), replace the `if (settingsManager.quizMode == "exam")` block with:
```kotlin
                val savedQuizMode = (state["quizMode"] as? String) ?: settingsManager.getQuizMode(bookId)
                val savedExamJson = state["examSettingsJson"] as? String
                if (savedExamJson != null) {
                    currentExamSettings = com.example.data.SharedGson.normal.fromJson(savedExamJson, ExamSettings::class.java)
                } else if (savedQuizMode == "exam") {
                    val limit = qIds.size
                    val settingsStr = settingsManager.getExamSettings(bookId, limit)
                    if (settingsStr != null) {
                        currentExamSettings = com.example.data.SharedGson.normal.fromJson(settingsStr, ExamSettings::class.java)
                    }
                } else {
                    currentExamSettings = null
                }
```

4. Inside `toggleFavoriteTopic` (lines 1539–1551), make favorite topics book-isolated:
```kotlin
    private val _favoriteTopics = MutableStateFlow(settingsManager.getFavoriteTopics(initialBookId))
    val favoriteTopics = _favoriteTopics.asStateFlow()

    fun toggleFavoriteTopic(path: String) {
        val bookId = _currentBookId.value
        val currentFavorites = settingsManager.getFavoriteTopics(bookId).toMutableSet()
        if (currentFavorites.contains(path)) {
            currentFavorites.remove(path)
        } else {
            currentFavorites.add(path)
        }
        settingsManager.setFavoriteTopics(bookId, currentFavorites)
        _favoriteTopics.value = currentFavorites
    }
```

5. Inside `selectBook(bookId: String, forceRefresh: Boolean = false)` (lines 1676–1716), load all book-isolated settings for `bookId`:
```kotlin
    fun selectBook(bookId: String, forceRefresh: Boolean = false) {
        if (_currentBookId.value != bookId || forceRefresh) {
            pendingQuizGenerationTrigger = true
            _currentBookId.value = bookId
            settingsManager.lastSelectedBookId = bookId

            val paths = settingsManager.getSelectedPaths(bookId)
            _selectedPaths.value = paths

            val categories = settingsManager.getSelectedCategories(bookId)
            _selectedCategories.value = categories

            val limit = settingsManager.getQuestionLimit(bookId)
            _questionLimit.value = if (limit == 0) null else limit

            _onlyBookmarks.value = false
            _quizFlowMode.value = settingsManager.getQuizFlowMode(bookId)
            _shuffleQuestions.value = settingsManager.getShuffleQuestions(bookId)
            _sequenceOffset.value = settingsManager.getSequenceOffset(bookId)
            _favoriteTopics.value = settingsManager.getFavoriteTopics(bookId)
            _selectedTopic.value = null
            isLearnMode = false
            currentExamSettings = null

            // Automatically repair any previously extracted questions that had $
...$ or $...<strong>...$ corruption
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val existingQs = repository.getQuestionsForBookSync(bookId)
                    val repairedQs = existingQs.mapNotNull { q ->
                        val fixed = com.example.ui.components.sanitizeCorruptedQuestionEntity(q)
                        if (fixed != q) fixed else null
                    }
                    if (repairedQs.isNotEmpty()) {
                        repository.insertQuestions(repairedQs)
                    }
                } catch (_: Exception) {}
            }

            generateQuizQuestions()
            if (lastSearchQuery.isNotBlank()) {
                searchQuestions(lastSearchQuery)
            }
        }
    }
```

6. Inside `saveQuizResult()` (around line 1850), persist the updated `_sequenceOffset` per book:
```kotlin
            // Advance sequence if autoSequence is on, but ONLY if not practice mistakes and not Learn Mode
            if (isActiveQuizStandardSequence && !isLearnMode && parentId == null &&
                settingsManager.getAutoSequence(bookId) && settingsManager.getQuizFlowMode(bookId) == "sequence"
            ) {
                val limit = _questionLimit.value
                val offset = _sequenceOffset.value
                if (limit != null) {
                    val nextOffset = offset + limit
                    val maxValue = _allBookQuestionSummaries.value.size
                    val updatedOffset = if (nextOffset <= maxValue) nextOffset else 1
                    _sequenceOffset.value = updatedOffset
                    settingsManager.setSequenceOffset(bookId, updatedOffset)
                }
            }
```

**Action 4D — In `app/src/main/java/com/example/ui/MainViewModel.kt`, replace `deleteBook` (lines 1899–1973) with `purgeBookDataAndCaches`, `deleteBook`, and `replaceBook`:**

```kotlin
    private fun clearBookRuntimeCaches(context: android.content.Context, bookId: String) {
        val contextIds = listOf(bookId, "${bookId}_read", "${bookId}_edit")
        for (cid in contextIds) {
            activeAiTasks.remove(cid)
            cancelNotification(context, cid.hashCode())
            aiProcessingJobs[cid]?.cancel()
            aiProcessingJobs.remove(cid)
        }
        _chatHistory.value = _chatHistory.value.toMutableMap().apply { contextIds.forEach { remove(it) } }
        _pendingUpdates.value = _pendingUpdates.value.toMutableMap().apply { contextIds.forEach { remove(it) } }
        _sessionAttachments.value = _sessionAttachments.value.toMutableMap().apply { contextIds.forEach { remove(it) } }
        _aiPromptTexts.value = _aiPromptTexts.value.toMutableMap().apply { contextIds.forEach { remove(it) } }
        _aiProcessingStatus.value = _aiProcessingStatus.value.toMutableMap().apply { contextIds.forEach { remove(it) } }
        _aiProcessingProgress.value = _aiProcessingProgress.value.toMutableMap().apply { contextIds.forEach { remove(it) } }

        com.example.ui.components.MathFormattingCache.clearCache()
        com.example.ui.components.LatexDrawableCache.clearCache()
    }

    private suspend fun purgeBookDataAndCaches(context: android.content.Context, bookId: String) {
        withContext(Dispatchers.IO) {
            try {
                val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                val quizerDir = java.io.File(basePath, "Quizer")
                val bookDir = java.io.File(quizerDir, bookId)
                val trashDir = java.io.File(quizerDir, ".trash")

                if (!trashDir.exists()) trashDir.mkdirs()
                val dateFormat = java.text.SimpleDateFormat("yy.MM.dd_HH.mm.ss", java.util.Locale.getDefault())
                val timestamp = dateFormat.format(java.util.Date())

                val possibleFiles = quizerDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".qbook", ignoreCase = true) || file.name.endsWith(".js", ignoreCase = true))
                } ?: emptyArray()

                for (pf in possibleFiles) {
                    var match = false
                    if (pf.name.endsWith(".qbook", ignoreCase = true)) {
                        try {
                            java.util.zip.ZipFile(pf).use { zip ->
                                val metaEntry = zip.getEntry("book_meta.json")
                                if (metaEntry != null) {
                                    val reader = zip.getInputStream(metaEntry).bufferedReader()
                                    val jsonStr = com.example.data.DataParser.autoFixJson(reader.readText())
                                    val meta = com.example.data.SharedGson.lenient.fromJson(jsonStr, com.example.data.QBookMeta::class.java)
                                    if (meta?.id == bookId) match = true
                                }
                            }
                        } catch (_: Exception) {}
                    } else if (pf.nameWithoutExtension == bookId) {
                        match = true
                    }

                    if (match) {
                        pf.copyTo(java.io.File(trashDir, "${timestamp}_${pf.name}"), overwrite = true)
                        pf.delete()
                    }
                }

                // Permanently delete extracted book folder (images, backup_questions.json), RestorePoints, and Updates
                if (bookDir.exists()) {
                    bookDir.deleteRecursively()
                }
                val restoreDir = java.io.File(quizerDir, "RestorePoints/$bookId")
                if (restoreDir.exists()) {
                    restoreDir.deleteRecursively()
                }
                val updatesDir = java.io.File(quizerDir, "Updates")
                if (updatesDir.exists()) {
                    updatesDir.listFiles { f -> f.name.startsWith("${bookId}_") }?.forEach { it.delete() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            repository.deleteBook(bookId)
            repository.deleteQuestionsForBook(bookId)
            repository.deleteAssociatedDataForBook(bookId)
            settingsManager.clearBookSettings(bookId)
        }

        clearBookRuntimeCaches(context, bookId)
        _activeQuizTrigger.value++
    }

    fun deleteBook(context: android.content.Context, bookId: String) {
        viewModelScope.launch {
            val updatedRemoved = settingsManager.removedBooks.toMutableSet()
            updatedRemoved.add(bookId)
            settingsManager.removedBooks = updatedRemoved

            purgeBookDataAndCaches(context, bookId)

            if (currentBookId.value == bookId) {
                val newBooks = withContext(Dispatchers.IO) { repository.getAllBooksSync() }
                if (newBooks.isNotEmpty()) {
                    selectBook(newBooks.first().id, forceRefresh = true)
                } else {
                    _currentBookId.value = ""
                    settingsManager.lastSelectedBookId = ""
                    _allBookQuestionSummaries.value = emptyList()
                    _topicTreeRoot.value = null
                    _selectedPaths.value = emptySet()
                    _selectedCategories.value = emptySet()
                    _questionLimit.value = null
                    _favoriteTopics.value = emptySet()
                    currentExamSettings = null
                    generateQuizQuestions()
                }
            }
        }
    }

    fun replaceBook(context: android.content.Context, bookId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            purgeBookDataAndCaches(context, bookId)
            _currentBookId.value = bookId
            addCustomBook(context, uri)
        }
    }
```

---

### File 5: `app/src/main/java/com/example/ui/screens/HomeScreen.kt`

**What this fixes:**
1. **Replace Book Dialog (`HomeScreen.kt` lines 118–159)**: Calls `viewModel.replaceBook(context, id, uri)` so replacing a book purges all old settings, active quiz states, chat history, extracted images, and caches before importing the replacement `.qbook`.
2. **Book-Isolated Exam Mode & Home Quiz Configuration**:
   - Syncs `mode`, `questionTimer`, `autoSequence`, `shuffleOpts`, `useDistractors`, `questionFlow`, `autoNextSeconds`, `correctMark`, and `wrongMark` with `currentBookId` using the book-isolated `SettingsManager` methods.
   - Passes `questionLimit = questionLimit ?: questions.size` to `ExamConfigDialog` so `"Auto"` timer calculates accurately even when `"All"` questions is selected.
   - Persists `expandedPaths` in the `"Select Scope"` dialog (`showCategoryDialog`) per book via `viewModel.settingsManager.getHomeExpandedPaths(currentBookId)` / `setHomeExpandedPaths(currentBookId, ...)`.

**Action 5A — In `app/src/main/java/com/example/ui/screens/HomeScreen.kt`, update `mode` and `questionTimer` declarations (lines 53–56) to key on `currentBookId`:**

```kotlin
    val sequenceOffset by viewModel.sequenceOffset.collectAsState()
    var mode by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuizMode(currentBookId)) }
    var showExamConfigDialog by remember { mutableStateOf(false) }
    val flowMode by viewModel.quizFlowMode.collectAsState()
    var questionTimer by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuestionTimerSeconds(currentBookId)) }
```

**Action 5B — In `app/src/main/java/com/example/ui/screens/HomeScreen.kt`, replace the `if (showReplaceDialog != null)` block (lines 105–164) with:**

```kotlin
    if (showReplaceDialog != null) {
        val (uri, identity) = showReplaceDialog!!
        AlertDialog(
            onDismissRequest = { showReplaceDialog = null },
            title = { Text("Book Already Exists") },
            text = {
                Column {
                    Text("A book with ID '${identity.id}' already exists.")
                    Text("Name: ${identity.name}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do you want to replace the existing book?", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = identity.id ?: ""
                    showReplaceDialog = null
                    viewModel.replaceBook(context, id, uri)
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = null }) { Text("Cancel") }
            }
        )
    }
```

**Action 5C — In `app/src/main/java/com/example/ui/screens/HomeScreen.kt`, update `if (showExamConfigDialog)` and `if (showCategoryDialog)` (lines 395–475) as follows:**

```kotlin
    if (showExamConfigDialog) {
        com.example.ui.components.ExamConfigDialog(
            bookId = currentBookId,
            questionLimit = questionLimit ?: questions.size,
            settingsManager = viewModel.settingsManager,
            onDismiss = { showExamConfigDialog = false },
            onStart = { settings ->
                showExamConfigDialog = false
                viewModel.clearActiveQuizState()
                viewModel.currentExamSettings = settings
                viewModel.setOnlyBookmarks(false)
                viewModel.isLearnMode = false
                onStartQuiz()
            }
        )
    }

    if (showCategoryDialog) {
        var expandedPaths by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getHomeExpandedPaths(currentBookId))
        }
```
And inside `showCategoryDialog`'s expand `IconButton` (around line 461), save `expandedPaths` per book:
```kotlin
                                    IconButton(
                                        onClick = {
                                            val updated = if (isExpanded) expandedPaths - item.node.path else expandedPaths + item.node.path
                                            expandedPaths = updated
                                            viewModel.settingsManager.setHomeExpandedPaths(currentBookId, updated)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
```

**Action 5D — In `app/src/main/java/com/example/ui/screens/HomeScreen.kt` (lines 644–845), update the `Auto` sequence checkbox, `Mode` radio buttons, and `Advanced Quiz Options` card to use the book-isolated `SettingsManager` methods:**

```kotlin
                                    Spacer(modifier = Modifier.width(8.dp))
                                    var autoSequence by remember(currentBookId) {
                                        mutableStateOf(viewModel.settingsManager.getAutoSequence(currentBookId))
                                    }
                                    Checkbox(
                                        checked = autoSequence,
                                        onCheckedChange = {
                                            autoSequence = it
                                            viewModel.settingsManager.setAutoSequence(currentBookId, it)
                                        }
                                    )
                                    Text("Auto")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mode", style = MaterialTheme.typography.labelLarge)
                        Row {
                            RadioButton(
                                selected = mode == "practice",
                                onClick = { mode = "practice"; viewModel.settingsManager.setQuizMode(currentBookId, "practice") }
                            )
                            Text("Practice", modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 16.dp))

                            RadioButton(
                                selected = mode == "exam",
                                onClick = { mode = "exam"; viewModel.settingsManager.setQuizMode(currentBookId, "exam") }
                            )
                            Text("Exam", modifier = Modifier.padding(top = 12.dp, start = 8.dp))
                        }
                    }
                }
            }
```
And inside `if (showOptions)` in the `Advanced Quiz Options` card (lines 715–835):
```kotlin
                        if (showOptions) {
                            Spacer(modifier = Modifier.height(8.dp))

                            val shuffleQ by viewModel.shuffleQuestions.collectAsState()
                            var shuffleOpts by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getShuffleOptions(currentBookId)) }
                            var useDistractors by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getUseDistractors(currentBookId)) }
                            var questionFlow by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuestionFlow(currentBookId)) }

                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = shuffleQ,
                                    onClick = { viewModel.setShuffleQuestions(!shuffleQ) },
                                    label = { Text("Shuffle Questions") }
                                )
                                FilterChip(
                                    selected = shuffleOpts,
                                    onClick = { shuffleOpts = !shuffleOpts; viewModel.settingsManager.setShuffleOptions(currentBookId, shuffleOpts) },
                                    label = { Text("Shuffle Options") }
                                )
                                FilterChip(
                                    selected = useDistractors,
                                    onClick = { useDistractors = !useDistractors; viewModel.settingsManager.setUseDistractors(currentBookId, useDistractors) },
                                    label = { Text("Dynamic Distractors") }
                                )
                                FilterChip(
                                    selected = questionFlow,
                                    onClick = { questionFlow = !questionFlow; viewModel.settingsManager.setQuestionFlow(currentBookId, questionFlow) },
                                    label = { Text("Question Flow") }
                                )
                            }

                            var autoNextSeconds by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getAutoNextSeconds(currentBookId)) }

                            if (mode != "exam") {
                                var correctMark by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getCorrectMark(currentBookId)) }
                                var wrongMark by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getWrongMark(currentBookId)) }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Marking Scheme", style = MaterialTheme.typography.labelLarge)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val correctMarks = listOf(1f, 2f, 3f, 4f)
                                    correctMarks.forEach { m ->
                                        FilterChip(
                                            selected = correctMark == m,
                                            onClick = { correctMark = m; viewModel.settingsManager.setCorrectMark(currentBookId, m) },
                                            label = { Text("+$m") }
                                        )
                                    }
                                    VerticalDivider(modifier = Modifier.height(32.dp))
                                    val wrongMarks = listOf(0f, 0.25f, 0.5f, 0.66f, 1f)
                                    wrongMarks.forEach { m ->
                                        FilterChip(
                                            selected = wrongMark == m,
                                            onClick = { wrongMark = m; viewModel.settingsManager.setWrongMark(currentBookId, m) },
                                            label = { Text("-$m") }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Timers", style = MaterialTheme.typography.labelLarge)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val autoNexts = listOf(0, 1, 2, 3, 5)
                                    Text("Auto-Next:", modifier = Modifier.padding(top = 10.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                    autoNexts.forEach { t ->
                                        FilterChip(
                                            selected = autoNextSeconds == t,
                                            onClick = { autoNextSeconds = t; viewModel.settingsManager.setAutoNextSeconds(currentBookId, t) },
                                            label = { Text(if (t == 0) "Off" else "${t}s") }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Per Question:", modifier = Modifier.padding(top = 10.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                val qTimes = listOf(0, 10, 15, 30) // 0 = Off
                                qTimes.forEach { t ->
                                    FilterChip(
                                        selected = questionTimer == t,
                                        onClick = {
                                            if (questionTimer == 0 && t > 0) {
                                                questionFlow = true
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, true)
                                            } else if (t == 0) {
                                                questionFlow = false
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, false)
                                            }
                                            questionTimer = t
                                            viewModel.settingsManager.setQuestionTimerSeconds(currentBookId, t)
                                        },
                                        label = { Text(if (t == 0) "Off" else "${t}s") }
                                    )
                                }
                                var customTime by remember(questionTimer) { mutableStateOf(if (qTimes.contains(questionTimer)) "" else questionTimer.toString()) }
                                OutlinedTextField(
                                    value = customTime,
                                    onValueChange = { newVal ->
                                        val filtered = newVal.filter { it.isDigit() }
                                        customTime = filtered
                                        filtered.toIntOrNull()?.let {
                                            if (questionTimer == 0 && it > 0) {
                                                questionFlow = true
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, true)
                                            } else if (it == 0) {
                                                questionFlow = false
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, false)
                                            }
                                            questionTimer = it
                                            viewModel.settingsManager.setQuestionTimerSeconds(currentBookId, it)
                                        }
                                    },
                                    placeholder = { Text("Custom") },
                                    modifier = Modifier.width(90.dp).height(50.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                                )
                            }
                        }
```

---

### File 6: `app/src/main/java/com/example/ui/screens/QuizScreen.kt`

**What this fixes:**
1. **Isolates Learn Mode from Home Screen Quiz Mode & Settings (`QuizScreen.kt` lines 93–145 & 314)**:
   - `val bookId = currentQuestion.bookId`
   - `val isPracticeMode = viewModel.isLearnMode || viewModel.settingsManager.getQuizMode(bookId) == "practice" || viewModel.currentPracticeMistakesParentId != null` (so starting a Learn quiz never gets forced into Exam Mode even if Home Screen's radio button is set to `"exam"`).
   - Reads `getLearnUseDistractors(bookId)`, `getLearnShuffleOptions(bookId)`, and `getLearnQuestionFlow(bookId)` when `viewModel.isLearnMode == true`, and reads the book-isolated Home settings otherwise.
   - Isolates `questionTimerTarget` and `autoNextDelay` so Home Screen's timers do not bleed into Learn Mode quizzes.
   - Computes `"Auto"` exam timer using `(viewModel.questionLimit.value ?: questions.size) * 36` so `"Auto"` works even when `"All"` questions is selected.

**Action — In `app/src/main/java/com/example/ui/screens/QuizScreen.kt`, replace lines 93–145 and line 314 with:**

```kotlin
    val currentQuestion = questions[currentIndex]
    val bookId = currentQuestion.bookId
    val isPracticeMode = viewModel.isLearnMode ||
        viewModel.settingsManager.getQuizMode(bookId) == "practice" ||
        viewModel.currentPracticeMistakesParentId != null
    val currentUseDistractors = if (viewModel.isLearnMode) {
        viewModel.settingsManager.getLearnUseDistractors(bookId)
    } else {
        viewModel.settingsManager.getUseDistractors(bookId)
    }
    val currentShuffleOptions = if (viewModel.isLearnMode) {
        viewModel.settingsManager.getLearnShuffleOptions(bookId)
    } else {
        viewModel.settingsManager.getShuffleOptions(bookId)
    }
    val currentQuestionFlow = if (viewModel.isLearnMode) {
        viewModel.settingsManager.getLearnQuestionFlow(bookId)
    } else {
        viewModel.settingsManager.getQuestionFlow(bookId)
    }
    val effectiveExamQuestionCount = viewModel.questionLimit.value?.takeIf { it > 0 } ?: questions.size
    var timeRemainingMs by remember {
        mutableStateOf(
            if (viewModel.activeTimeRemainingMs >= 0) viewModel.activeTimeRemainingMs else {
                val examSecs = viewModel.currentExamSettings?.let {
                    when (it.timerPreset) {
                        "Off" -> 0
                        "15m" -> 15 * 60
                        "30m" -> 30 * 60
                        "60m" -> 60 * 60
                        "Custom" -> it.customTimerMinutes * 60
                        "Auto" -> effectiveExamQuestionCount * 36
                        else -> 0
                    }
                }
                val secs = examSecs ?: (viewModel.settingsManager.timerMinutes * 60)
                if (!isPracticeMode && secs > 0) secs * 1000L else 0L
            }
        )
    }
    val questionTimerTarget = if (viewModel.isLearnMode) {
        0L
    } else {
        viewModel.settingsManager.getQuestionTimerSeconds(bookId) * 1000L
    }
    var questionTimeRemainingMs by remember(currentIndex) {
        mutableStateOf(if (questionTimerTarget > 0 && lockedAnswers[currentIndex] != true) questionTimerTarget else 0L)
    }

    val isSubmitted = remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        showCancelDialog = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isSubmitted.value && !viewModel.isLearnMode && viewModel.currentPracticeMistakesParentId == null) {
                viewModel.saveActiveQuizState(currentIndex, selectedAnswers, lockedAnswers, timeRemainingMs, qTimes.value)
            }
        }
    }

    val submitQuiz = {
        if (!isSubmitted.value) {
            isSubmitted.value = true
            if (hapticEnabled) viewModel.soundHapticManager.hapticQuizComplete()
            val correctMark = if (viewModel.isLearnMode) {
                1f
            } else {
                viewModel.currentExamSettings?.customCorrectMarks ?: viewModel.settingsManager.getCorrectMark(bookId)
            }
            val wrongMark = if (viewModel.isLearnMode) {
                0f
            } else {
                viewModel.currentExamSettings?.customWrongMarks ?: viewModel.settingsManager.getWrongMark(bookId)
            }
```
And at line 314 of `QuizScreen.kt`, update `val autoNextDelay` and `val isPracticing` (line 340):
```kotlin
    val autoNextDelay = if (viewModel.isLearnMode) 0 else viewModel.settingsManager.getAutoNextSeconds(bookId)
```
```kotlin
    val isPracticing = isPracticeMode
```

---

### File 7: `app/src/main/java/com/example/data/QuizRepository.kt`

**What this fixes:**
- Cleans up AI chat messages (`bookId`, `"${bookId}_read"`, `"${bookId}_edit"`) in `deleteBook(bookId)` and `deleteAssociatedDataForBook(bookId)` so deleting or replacing a book leaves zero orphaned chat rows in Room.

**Action — In `app/src/main/java/com/example/data/QuizRepository.kt` (lines 146–170), update `deleteBook` and `deleteAssociatedDataForBook`:**

```kotlin
    suspend fun deleteBook(bookId: String) {
        db.bookDao().deleteBook(bookId)
        db.questionDao().deleteQuestionsForBook(bookId)
        deleteAssociatedDataForBook(bookId)
    }

    suspend fun deleteQuestionsInRange(bookId: String, startId: Long, endId: Long) {
        db.questionDao().deleteQuestionsInRange(bookId, startId, endId)
        db.bookDao().updateBookStats(bookId)
    }

    suspend fun deleteQuestionsForBook(bookId: String) {
        db.questionDao().deleteQuestionsForBook(bookId)
        db.bookDao().updateBookStats(bookId)
    }

    suspend fun deleteAssociatedDataForBook(bookId: String) {
        db.statDao().deleteStatsForBook(bookId)
        db.historyDao().deleteHistoryForBook(bookId)
        db.bookmarkDao().deleteBookmarksForBook(bookId)
        db.learningPlanDao().deleteLearningPlanForBook(bookId)
        db.chatDao().clearChatsForBook(bookId)
        db.chatDao().clearChatsForBook("${bookId}_read")
        db.chatDao().clearChatsForBook("${bookId}_edit")
    }
```

---

### File 8: `app/src/main/java/com/example/ui/components/RichText.kt` & `app/src/main/java/com/example/ui/screens/ReadBookScreen.kt` (ReadBook Search Highlight Fix + Cache Eviction)

**What this fixes:**
1. Adds `clearCache()` to `LatexDrawableCache` and `MathFormattingCache` so book deletion/replacement evicts cached drawables and spannables.
2. Returns a fresh `SpannableString(cached)` copy from `MathFormattingCache.getSpannable()` and tracks `boundSearchQuery` on `RichTextView` so searched words in `ReadBookScreen` never stay highlighted after changing or clearing the search query.
3. Adds `BackHandler(enabled = isSearchActive)` in `ReadBookScreen.kt` and keeps `ReadBookQuestionItem` option selection reset on scroll (`remember(question.id, inBookQuizMode)` — Fix 3 excluded as requested).

**Action 8A — In `app/src/main/java/com/example/ui/components/RichText.kt`, add `clearCache()` to `LatexDrawableCache` (around line 185) and replace `MathFormattingCache` (lines 1269–1296) with:**

```kotlin
    // Inside LatexDrawableCache (around line 185):
    fun clearCache() {
        cache.evictAll()
    }
```

```kotlin
object MathFormattingCache {
    private val formattedTextCache = android.util.LruCache<String, String>(1000)
    private val spannableCache = android.util.LruCache<Triple<String, Int, Int>, SpannableString>(600)

    fun clearCache() {
        formattedTextCache.evictAll()
        spannableCache.evictAll()
    }

    fun getFormattedText(raw: String): String {
        if (raw.isBlank()) return raw
        val cached = formattedTextCache.get(raw)
        if (cached != null) return cached
        val formatted = formatRichTextLineBreaks(raw)
        formattedTextCache.put(raw, formatted)
        return formatted
    }

    /**
     * Caches the clean base SpannableString produced by Markwon and ALWAYS returns a fresh
     * SpannableString copy so caller-added spans (such as search BackgroundColorSpans or
     * image ClickableSpans) never mutate or pollute the cached template.
     */
    fun getSpannable(
        markwon: Markwon,
        processedText: String,
        markwonHash: Int,
        textColorArgb: Int
    ): Spannable {
        val key = Triple(processedText, markwonHash, textColorArgb)
        val cached = spannableCache.get(key)
        if (cached != null) {
            return SpannableString(cached)
        }

        val markdownNode = markwon.toMarkdown(processedText)
        val cleanBase = SpannableString(markdownNode)
        spannableCache.put(key, cleanBase)
        return SpannableString(cleanBase)
    }
}
```

**Action 8B — In `app/src/main/java/com/example/ui/components/RichText.kt`, add `var boundSearchQuery: String = ""` to `RichTextView` (line 1329) and update `bindRichTextView` (line 1419) and `AndroidView` (lines 1555–1606):**

```kotlin
    // Inside RichTextView (line 1329):
    var parsedSpannableTag: Any? = null
    var boundSearchQuery: String = ""
```
```kotlin
    // Inside bindRichTextView (add `searchQuery: String` parameter and replace lines 1419-1422):
    if (textView.parsedSpannableTag !== spannableToDisplay || textView.boundSearchQuery != searchQuery) {
        markwon.setParsedMarkdown(textView, spannableToDisplay)
        textView.parsedSpannableTag = spannableToDisplay
        textView.boundSearchQuery = searchQuery
    }
```
```kotlin
    // Inside AndroidView onReset (line 1594):
    textView.parsedSpannableTag = null
    textView.boundSearchQuery = ""
```

**Action 8C — In `app/src/main/java/com/example/ui/screens/ReadBookScreen.kt` (lines 415–417), add `BackHandler(enabled = isSearchActive)` right below `var searchQuery`:**

```kotlin
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }
```
