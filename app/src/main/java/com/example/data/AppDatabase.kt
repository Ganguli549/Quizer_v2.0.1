package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        QuestionEntity::class,
        QuestionStatEntity::class,
        QuizHistoryEntity::class,
        BookmarkEntity::class,
        LearningPlanEntity::class,
        ChatMessageEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun questionDao(): QuestionDao
    abstract fun statDao(): QuizStatDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun learningPlanDao(): LearningPlanDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`bookmarkKey` TEXT NOT NULL, `bookId` TEXT NOT NULL, `questionId` INTEGER NOT NULL, PRIMARY KEY(`bookmarkKey`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
                    }
                }
                val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `roadmapLeafLevel` INTEGER NOT NULL DEFAULT 0")
                    }
                }
                val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `dataVariable` TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `updateId` TEXT")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `bookVersion` TEXT")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `updateVersions` TEXT NOT NULL DEFAULT '[]'")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `questionIdStart` INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `questionIdEnd` INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE `books` ADD COLUMN `hierarchyLabels` TEXT NOT NULL DEFAULT '[]'")
                    }
                }
                val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `advancedOptions` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `advancedCorrectIds` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `detailedExplanation` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `explanationImage` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `multipleCorrect` INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `format` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `tableData` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `bottomText` TEXT")
                    }
                }
                val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `learning_plan` (`bookId` TEXT NOT NULL, `mode` TEXT NOT NULL DEFAULT 'adaptive', `topicOrder` TEXT NOT NULL, `unlockedTopics` TEXT NOT NULL, `lockedTopics` TEXT NOT NULL, `completedTopics` TEXT NOT NULL, `lastActiveTopic` TEXT, `parentPath` TEXT, `leafLevel` INTEGER, PRIMARY KEY(`bookId`))")
                    }
                }
                val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                    }
                }
                val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_bookId` ON `questions` (`bookId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_path` ON `questions` (`path`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_category` ON `questions` (`category`)")
                    }
                }
                val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_stats_bookId` ON `question_stats` (`bookId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_stats_questionId` ON `question_stats` (`questionId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_stats_nextReview` ON `question_stats` (`nextReview`)")
                    }
                }
                val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_history_bookId` ON `quiz_history` (`bookId`)")
                    }
                }
                val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `exams` TEXT")
                        db.execSQL("ALTER TABLE `questions` ADD COLUMN `explanationTable` TEXT")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quizer_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                INSTANCE = null
            }
        }

        fun checkpoint(context: Context? = null): Boolean {
            return synchronized(this) {
                try {
                    val db = INSTANCE ?: (context?.let { getDatabase(it) }) ?: return false
                    var success = false
                    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                        if (cursor.moveToFirst()) {
                            // Column 0 is 'busy' status: 0 indicates success (not busy)
                            val busy = if (cursor.columnCount > 0) cursor.getInt(0) else 0
                            success = (busy == 0)
                        }
                    }
                    success
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }
}
