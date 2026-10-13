package com.example

import com.example.data.QuestionEntity
import com.example.ui.components.PendingQuestionUpdate
import com.example.ui.components.processExtractedQuestionHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionHeaderExtractionTest {

    @Test
    fun testHeaderPropagationForQuestionFrom1To10() {
        val updates = (1..10).map { i ->
            val header = if (i == 1) "Choose one word Substitution (for question from 1 to 10)" else null
            PendingQuestionUpdate(
                originalId = -1L,
                updatedQuestion = QuestionEntity(
                    id = i.toLong(),
                    bookId = "TestBook",
                    question = "Question $i",
                    options = "[]",
                    correctIndex = 0,
                    explanation = "",
                    path = "English",
                    category = "AI Extracted",
                    section = "",
                    extraOptions = "[]",
                    exam = "",
                    year = "",
                    questionHeader = header
                )
            )
        }

        val processed = processExtractedQuestionHeaders(updates)
        assertEquals(10, processed.size)

        for (item in processed) {
            val q = item.updatedQuestion
            assertNotNull(q.questionHeader)
            assertTrue("Expected JSON header, got ${q.questionHeader}", q.questionHeader!!.startsWith("{\"startId\": 1, \"endId\": 10"))
            assertTrue("Expected text in header, got ${q.questionHeader}", q.questionHeader!!.contains("Choose one word Substitution (for question from 1 to 10)"))
            assertEquals("Choose one word Substitution (for question from 1 to 10)", q.section)
        }
    }

    @Test
    fun testHeaderPropagationForPassage1To10() {
        val updates = (1..10).map { i ->
            val header = if (i == 1) "Read Passages And Answer Questions (1 -10)" else null
            PendingQuestionUpdate(
                originalId = -1L,
                updatedQuestion = QuestionEntity(
                    id = i.toLong(),
                    bookId = "TestBook",
                    question = "Question $i",
                    options = "[]",
                    correctIndex = 0,
                    explanation = "",
                    path = "English/Comprehension",
                    category = "AI Extracted",
                    section = "",
                    extraOptions = "[]",
                    exam = "",
                    year = "",
                    questionHeader = header
                )
            )
        }

        val processed = processExtractedQuestionHeaders(updates)
        assertEquals(10, processed.size)

        for (item in processed) {
            val q = item.updatedQuestion
            assertNotNull(q.questionHeader)
            assertTrue(q.questionHeader!!.startsWith("{\"startId\": 1, \"endId\": 10"))
            assertTrue(q.questionHeader!!.contains("Read Passages And Answer Questions (1 -10)"))
        }
    }

    @Test
    fun testMultipleSections() {
        val updates = (1..6).map { i ->
            val header = when (i) {
                1 -> "Choose one word Substitution (for question from 1 to 3)"
                4 -> "Read Passages And Answer Questions (4 -6)"
                else -> null
            }
            PendingQuestionUpdate(
                originalId = -1L,
                updatedQuestion = QuestionEntity(
                    id = i.toLong(),
                    bookId = "TestBook",
                    question = "Question $i",
                    options = "[]",
                    correctIndex = 0,
                    explanation = "",
                    path = "English",
                    category = "AI Extracted",
                    section = "",
                    extraOptions = "[]",
                    exam = "",
                    year = "",
                    questionHeader = header
                )
            )
        }

        val processed = processExtractedQuestionHeaders(updates)
        assertEquals(6, processed.size)

        // First 3 questions: Section 1
        for (i in 0..2) {
            val q = processed[i].updatedQuestion
            assertTrue(q.questionHeader!!.startsWith("{\"startId\": 1, \"endId\": 3"))
            assertTrue(q.questionHeader!!.contains("Choose one word Substitution (for question from 1 to 3)"))
        }

        // Next 3 questions: Section 2
        for (i in 3..5) {
            val q = processed[i].updatedQuestion
            assertTrue(q.questionHeader!!.startsWith("{\"startId\": 4, \"endId\": 6"))
            assertTrue(q.questionHeader!!.contains("Read Passages And Answer Questions (4 -6)"))
        }
    }
}
