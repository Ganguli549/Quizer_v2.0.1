package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quiz_settings", Context.MODE_PRIVATE)

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
        get() = prefs.getString("quiz_flow_mode", "sequence") ?: "sequence" // "sequence" or "progressive"
        set(value) = prefs.edit().putString("quiz_flow_mode", value).apply()

    var correctMark: Float
        get() = prefs.getFloat("correct_mark", 1f)
        set(value) = prefs.edit().putFloat("correct_mark", value).apply()

    var wrongMark: Float
        get() = prefs.getFloat("wrong_mark", 0f)
        set(value) = prefs.edit().putFloat("wrong_mark", value).apply()

    
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

    fun getExamSettings(bookId: String?, limit: Int): String? {
        return prefs.getString("exam_settings_${bookId}_${limit}", null)
    }

    fun setExamSettings(bookId: String?, limit: Int, settingsJson: String) {
        prefs.edit().putString("exam_settings_${bookId}_${limit}", settingsJson).apply()
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

    var appThemeMode: String
        get() = prefs.getString("app_theme_mode", "system") ?: "system" // "system", "light", "dark"
        set(value) { prefs.edit().putString("app_theme_mode", value).commit() }

    var appTheme: String
        get() = prefs.getString("app_theme", "dynamic") ?: "dynamic" // "dynamic", "ocean", "forest", "sunset", "lavender", "amoled"
        set(value) { prefs.edit().putString("app_theme", value).commit() }

    var appIcon: String
        get() = prefs.getString("app_icon", "ocean") ?: "ocean" // "ocean", "forest", "sunset", "lavender", "dark", "gold"
        set(value) { prefs.edit().putString("app_icon", value).commit() }
        
    var favoriteTopics: Set<String>
        get() = prefs.getStringSet("favorite_topics", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("favorite_topics", value).apply()

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
        return prefs.getStringSet("selected_paths_$bookId", emptySet()) ?: emptySet()
    }
    fun setSelectedPaths(bookId: String, value: Set<String>) {
        prefs.edit().putStringSet("selected_paths_$bookId", value).apply()
    }

    fun getSelectedCategories(bookId: String): Set<String> {
        return prefs.getStringSet("selected_categories_$bookId", emptySet()) ?: emptySet()
    }
    fun setSelectedCategories(bookId: String, value: Set<String>) {
        prefs.edit().putStringSet("selected_categories_$bookId", value).apply()
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

    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun clearBookSettings(bookId: String) {
        prefs.edit()
            .remove("paths_$bookId")
            .remove("cats_$bookId")
            .remove("limit_$bookId")
            .remove("active_quiz_state_$bookId")
            .remove("scroll_idx_$bookId")
            .remove("scroll_off_$bookId")
            .apply()
    }
}
