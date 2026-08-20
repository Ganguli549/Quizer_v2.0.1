package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY isFavorite DESC, addedAt ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSync(): List<BookEntity>

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateBookFavorite(id: String, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)
    
    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookSync(id: String): BookEntity?

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    @Query("UPDATE books SET totalQuestions = (SELECT COUNT(*) FROM questions WHERE bookId = :id), totalCategories = (SELECT COUNT(DISTINCT category) FROM questions WHERE bookId = :id) WHERE id = :id")
    suspend fun updateBookStats(id: String)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE bookId = :bookId ORDER BY id ASC")
    fun getQuestionsForBook(bookId: String): Flow<List<QuestionEntity>>

    @Query("SELECT id, bookId, category, path, exam, exams, questionHeader FROM questions WHERE bookId = :bookId ORDER BY id ASC")
    fun getQuestionSummariesForBook(bookId: String): Flow<List<QuestionSummary>>

    @Query("SELECT * FROM questions WHERE bookId = :bookId AND id IN (:ids) ORDER BY id ASC")
    suspend fun getQuestionsByBookAndIds(bookId: String, ids: List<Long>): List<QuestionEntity>

    @androidx.room.RawQuery
    suspend fun searchQuestionsRaw(query: androidx.sqlite.db.SupportSQLiteQuery): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE bookId = :bookId ORDER BY id ASC")
    suspend fun getQuestionsForBookSync(bookId: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)
    
    @Update
    suspend fun updateQuestion(question: QuestionEntity)
    
    @Delete
    suspend fun deleteQuestion(question: QuestionEntity)
    
    @Query("DELETE FROM questions WHERE bookId = :bookId AND id BETWEEN :startId AND :endId")
    suspend fun deleteQuestionsInRange(bookId: String, startId: Long, endId: Long)

    @Query("DELETE FROM questions WHERE bookId = :bookId")
    suspend fun deleteQuestionsForBook(bookId: String)
}

@Dao
interface QuizStatDao {
    @Query("SELECT * FROM question_stats WHERE bookId = :bookId")
    fun getStatsForBook(bookId: String): Flow<List<QuestionStatEntity>>

    @Query("SELECT * FROM question_stats")
    fun getAllStats(): Flow<List<QuestionStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(stat: QuestionStatEntity)

    @Query("DELETE FROM question_stats WHERE bookId = :bookId")
    suspend fun deleteStatsForBook(bookId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM quiz_history ORDER BY historyId DESC LIMIT 500")
    fun getAllHistory(): Flow<List<QuizHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QuizHistoryEntity)

    @Query("DELETE FROM quiz_history WHERE bookId = :bookId")
    suspend fun deleteHistoryForBook(bookId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT q.* FROM questions q INNER JOIN bookmarks b ON q.id = b.questionId AND q.bookId = b.bookId WHERE b.bookId = :bookId")
    fun getBookmarkedQuestionsForBook(bookId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM bookmarks")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksForBook(bookId: String)
}

@Dao
interface LearningPlanDao {
    @Query("SELECT * FROM learning_plan WHERE bookId = :bookId LIMIT 1")
    fun getPlan(bookId: String): Flow<LearningPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: LearningPlanEntity)

    @Query("DELETE FROM learning_plan WHERE bookId = :bookId")
    suspend fun deleteLearningPlanForBook(bookId: String)
}
