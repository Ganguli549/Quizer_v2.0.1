package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE bookId = :bookId ORDER BY timestamp ASC")
    suspend fun getChatsForBook(bookId: String): List<ChatMessageEntity>

    @Insert
    suspend fun insertChat(chat: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE bookId = :bookId")
    suspend fun clearChatsForBook(bookId: String)
}
