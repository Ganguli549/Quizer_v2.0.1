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
                val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE questions ADD COLUMN exams TEXT")
                        db.execSQL("ALTER TABLE questions ADD COLUMN explanationTable TEXT")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quizer_database"
                )
                .addMigrations(MIGRATION_10_11)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
