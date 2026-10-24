package com.example.data

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class QuizRepository(context: Context) {
    private val appContext = context.applicationContext
    val db: AppDatabase get() = AppDatabase.getDatabase(appContext)

    private val dbVersion = MutableStateFlow(0)
    fun notifyDatabaseReplaced() {
        dbVersion.value++
    }

    suspend fun getChatsForBook(bookId: String): List<ChatMessageEntity> {
        return db.chatDao().getChatsForBook(bookId)
    }

    suspend fun insertChat(chat: ChatMessageEntity) {
        db.chatDao().insertChat(chat)
    }

    suspend fun clearChatsForBook(bookId: String) {
        db.chatDao().clearChatsForBook(bookId)
    }

    suspend fun deleteChat(id: Long) {
        db.chatDao().deleteChatById(id)
    }

    val books: Flow<List<BookEntity>> = dbVersion.flatMapLatest { db.bookDao().getAllBooks() }
    val allStats: Flow<List<QuestionStatEntity>> = dbVersion.flatMapLatest { db.statDao().getAllStats() }
    val history: Flow<List<QuizHistoryEntity>> = dbVersion.flatMapLatest { db.historyDao().getAllHistory() }
    fun getHistoryForBook(bookId: String): Flow<List<QuizHistoryEntity>> = dbVersion.flatMapLatest { db.historyDao().getHistoryForBook(bookId) }
    val bookmarks: Flow<List<BookmarkEntity>> = dbVersion.flatMapLatest { db.bookmarkDao().getAllBookmarks() }

    fun getStatsForBook(bookId: String): Flow<List<QuestionStatEntity>> = dbVersion.flatMapLatest { db.statDao().getStatsForBook(bookId) }
    
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> = dbVersion.flatMapLatest { db.bookmarkDao().getBookmarksForBook(bookId) }
    
    fun getBookmarkedQuestionsForBook(bookId: String): Flow<List<QuestionEntity>> = dbVersion.flatMapLatest { db.bookmarkDao().getBookmarkedQuestionsForBook(bookId) }
    
    fun getQuestionsForBook(bookId: String): Flow<List<QuestionEntity>> {
        return dbVersion.flatMapLatest { db.questionDao().getQuestionsForBook(bookId) }
    }
    
    fun getQuestionSummariesForBook(bookId: String): Flow<List<QuestionSummary>> {
        return dbVersion.flatMapLatest { db.questionDao().getQuestionSummariesForBook(bookId) }
    }
    
    suspend fun getQuestionsByBookAndIds(bookId: String, ids: List<Long>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()
        for (chunk in ids.chunked(900)) {
            result.addAll(db.questionDao().getQuestionsByBookAndIds(bookId, chunk))
        }
        return result
    }
    
    suspend fun getAllBooksSync(): List<BookEntity> {
        return db.bookDao().getAllBooksSync()
    }
    
    suspend fun toggleBookFavorite(bookId: String, isFavorite: Boolean) {
        db.bookDao().updateBookFavorite(bookId, isFavorite)
    }
    
    suspend fun getQuestionsForBookSync(bookId: String): List<QuestionEntity> {
        return db.questionDao().getQuestionsForBookSync(bookId)
    }

    suspend fun searchQuestionsSync(bookId: String, query: String): List<QuestionEntity> {
        val trimmedQuery = query.trim()
        val terms = trimmedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        
        var queryString = "SELECT * FROM questions WHERE bookId = ?"
        val bindArgs = mutableListOf<Any>(bookId)
        
        for (term in terms) {
            queryString += " AND (question LIKE ? OR explanation LIKE ? OR exam LIKE ? OR year LIKE ? OR path LIKE ? OR category LIKE ?)"
            val likeTerm = "%$term%"
            bindArgs.addAll(listOf(likeTerm, likeTerm, likeTerm, likeTerm, likeTerm, likeTerm))
        }
        
        // Exact match ranking: if the raw query appears exactly in any text, rank it higher (0 instead of 1)
        queryString += " ORDER BY (CASE WHEN question LIKE ? OR explanation LIKE ? OR exam LIKE ? OR path LIKE ? OR category LIKE ? THEN 0 ELSE 1 END) ASC LIMIT 100"
        val exactMatchLike = "%$trimmedQuery%"
        bindArgs.addAll(listOf(exactMatchLike, exactMatchLike, exactMatchLike, exactMatchLike, exactMatchLike))
        
        return db.questionDao().searchQuestionsRaw(androidx.sqlite.db.SimpleSQLiteQuery(queryString, bindArgs.toTypedArray()))
    }

    suspend fun insertBook(book: BookEntity) {
        db.bookDao().insertBook(book)
    }
    
    suspend fun getBookSync(bookId: String): BookEntity? {
        return db.bookDao().getBookSync(bookId)
    }

    suspend fun insertQuestions(questions: List<QuestionEntity>) {
        if (questions.isEmpty()) return
        db.questionDao().insertQuestions(questions)
        questions.map { it.bookId }.distinct().filter { it.isNotBlank() }.forEach { bId ->
            db.bookDao().updateBookStats(bId)
        }
    }

    suspend fun updateBookStats(bookId: String) {
        db.bookDao().updateBookStats(bookId)
    }

    suspend fun updateQuestion(question: QuestionEntity) {
        db.questionDao().updateQuestion(question)
        db.bookDao().updateBookStats(question.bookId)
    }

    suspend fun deleteQuestion(question: QuestionEntity) {
        db.questionDao().deleteQuestion(question)
        db.bookDao().updateBookStats(question.bookId)
    }

    suspend fun insertStat(stat: QuestionStatEntity) {
        db.statDao().insertStat(stat)
    }

    suspend fun insertHistory(historyEntry: QuizHistoryEntity) {
        // Automatically prune quiz history older than 30 days on every new history insert to save storage
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        db.historyDao().deleteHistoryOlderThan(thirtyDaysAgo)
        db.historyDao().insertHistory(historyEntry)
    }

    suspend fun deleteHistoryEntry(historyId: Long) {
        db.historyDao().deleteHistoryEntry(historyId, "practice_mistakes_for_$historyId")
    }

    suspend fun clearHistoryForBook(bookId: String) {
        db.historyDao().deleteHistoryForBook(bookId)
    }

    suspend fun cleanupOldHistory(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep.coerceAtLeast(1).toLong() * 24L * 60L * 60L * 1000L)
        db.historyDao().deleteHistoryOlderThan(cutoff)
    }

    suspend fun addBookmark(bookId: String, questionId: Long) {
        db.bookmarkDao().insertBookmark(BookmarkEntity("${bookId}_${questionId}", bookId, questionId))
    }

    suspend fun removeBookmark(bookId: String, questionId: Long) {
        db.bookmarkDao().deleteBookmark(BookmarkEntity("${bookId}_${questionId}", bookId, questionId))
    }
    
    fun getLearningPlan(bookId: String): Flow<LearningPlanEntity?> {
        return dbVersion.flatMapLatest { db.learningPlanDao().getPlan(bookId) }
    }
    
    suspend fun insertLearningPlan(plan: LearningPlanEntity) {
        db.learningPlanDao().insertPlan(plan)
    }

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

    suspend fun clearDatabase() {
        db.clearAllTables()
    }
}
