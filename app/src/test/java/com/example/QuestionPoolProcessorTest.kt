package com.example

import com.example.data.QuestionStatEntity
import com.example.data.QuestionSummary
import com.example.ui.QuestionPoolProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPoolProcessorTest {

    private fun makePool(count: Int, bookId: String = "book1"): List<QuestionSummary> {
        return (1..count).map { id ->
            QuestionSummary(
                id = id.toLong(),
                bookId = bookId,
                category = "Cat",
                path = "Cat/Sub",
                exam = "",
                exams = null,
                questionHeader = null
            )
        }
    }

    @Test
    fun testHighWrongQuestionGraduatesOnceLearnedAndStopsRepeatingEveryQuiz() {
        val now = 1_700_000_000_000L
        val dayMs = 86_400_000L
        val pool = makePool(50)

        // Q1 has the highest wrong count in the book (wrong = 10), but the user has since
        // learned it (streak = 4, nextReview = 7 days in the future).
        val masteredHighWrong = QuestionStatEntity(
            questionKey = "book1_1",
            bookId = "book1",
            questionId = 1L,
            attempts = 14,
            correct = 4,
            wrong = 10,
            lastSeen = now - dayMs,
            easeFactor = 2.2f,
            interval = 7,
            streak = 4,
            nextReview = now + 6 * dayMs
        )
        assertTrue("Q1 with streak=4 must be recognized as mastered", QuestionPoolProcessor.isQuestionMastered(masteredHighWrong))

        // Q2..Q7 are recently wronged questions (wrong = 1, streak = 0)
        val statsMap = mutableMapOf<Long, QuestionStatEntity>(1L to masteredHighWrong)
        for (id in 2L..7L) {
            statsMap[id] = QuestionStatEntity(
                questionKey = "book1_$id",
                bookId = "book1",
                questionId = id,
                attempts = 1,
                correct = 0,
                wrong = 1,
                lastSeen = now - 30 * 60_000L,
                streak = 0,
                interval = 1,
                nextReview = now - 20 * 60_000L
            )
        }
        // Q8..Q11 are overdue SRS review questions (wrong = 0, attempts = 1, nextReview <= now)
        for (id in 8L..11L) {
            statsMap[id] = QuestionStatEntity(
                questionKey = "book1_$id",
                bookId = "book1",
                questionId = id,
                attempts = 1,
                correct = 1,
                wrong = 0,
                lastSeen = now - 2 * dayMs,
                streak = 1,
                interval = 1,
                nextReview = now - dayMs
            )
        }

        val result = QuestionPoolProcessor.process(
            pool = pool,
            flowMode = "progressive",
            isShuffle = false,
            limit = 25,
            offset = 1,
            statsMap = statsMap,
            nowMs = now
        )

        val resultIds = result.map { it.id }.toSet()
        assertEquals(25, result.size)
        // Q1 (wrong = 10, streak = 4, not due for 6 days) MUST NOT be pulled into the quiz
        // ahead of active mistakes (Q2..Q7) and overdue SRS items (Q8..Q11)
        assertFalse("Graduated high-wrong Q1 must not repeat when not due", 1L in resultIds)
        // All 6 active mistakes (24% of 25 = 6) must be included
        for (id in 2L..7L) {
            assertTrue("Active recent mistake Q$id must be included", id in resultIds)
        }
        // All 4 overdue SRS items (16% of 25 = 4) must be included
        for (id in 8L..11L) {
            assertTrue("Overdue SRS item Q$id must be included", id in resultIds)
        }
        // 15 progressive new questions (60% of 25 = 15 -> Q12..Q26) must be included in sequence
        for (id in 12L..26L) {
            assertTrue("Progressive frontier Q$id must be included", id in resultIds)
        }
    }

    @Test
    fun testBrandNewBookSpillsOverRepetitionQuotaToProgressiveFrontier() {
        val pool = makePool(40)
        val result = QuestionPoolProcessor.process(
            pool = pool,
            flowMode = "progressive",
            isShuffle = false,
            limit = 25,
            offset = 1,
            statsMap = emptyMap()
        )
        assertEquals(25, result.size)
        assertEquals((1L..25L).toList(), result.map { it.id })
    }
}
