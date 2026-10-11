package com.example.ui

import com.example.data.QuestionStatEntity
import com.example.data.QuestionSummary

object QuestionPoolProcessor {

    fun process(
        pool: List<QuestionSummary>,
        flowMode: String,
        isShuffle: Boolean,
        limit: Int?,
        offset: Int = 1,
        statsMap: Map<Long, QuestionStatEntity> = emptyMap()
    ): List<QuestionSummary> {
        val grouped = mutableListOf<List<QuestionSummary>>()
        var i = 0
        while (i < pool.size) {
            val q = pool[i]
            if (!q.questionHeader.isNullOrBlank() && q.questionHeader.contains("<p>")) {
                val passage = mutableListOf(q)
                var j = i + 1
                while (j < pool.size && pool[j].questionHeader == q.questionHeader && pool[j].path == q.path) {
                    passage.add(pool[j])
                    j++
                }
                grouped.add(passage)
                i = j
            } else {
                grouped.add(listOf(q))
                i++
            }
        }

        if (flowMode == "progressive") {
            val targetTotal = if (limit != null && limit > 0) limit.coerceAtMost(pool.size) else pool.size
            val targetMistakenCount = Math.round(targetTotal * 0.4f).toInt()

            val chosenReviewGroups = mutableListOf<List<QuestionSummary>>()
            val chosenReviewIds = mutableSetOf<Long>()
            var chosenReviewCount = 0

            // 1. Pick up to 40% most mistaken questions (wrong > 0)
            if (targetMistakenCount > 0) {
                val mistakenCandidates = grouped.filter { g -> g.sumOf { statsMap[it.id]?.wrong ?: 0 } > 0 }
                    .let { list ->
                        if (isShuffle) {
                            // When shuffle is ON, randomize among candidates with equal mistake counts so pickup is never sequential
                            list.shuffled().sortedWith(
                                compareByDescending<List<QuestionSummary>> { g -> g.sumOf { statsMap[it.id]?.wrong ?: 0 } }
                                    .thenBy { g ->
                                        val att = g.sumOf { statsMap[it.id]?.attempts ?: 0 }
                                        if (att > 0) (g.sumOf { statsMap[it.id]?.correct ?: 0 }.toFloat() / att) else 0f
                                    }
                            )
                        } else {
                            list.sortedWith(
                                compareByDescending<List<QuestionSummary>> { g -> g.sumOf { statsMap[it.id]?.wrong ?: 0 } }
                                    .thenBy { g ->
                                        val att = g.sumOf { statsMap[it.id]?.attempts ?: 0 }
                                        if (att > 0) (g.sumOf { statsMap[it.id]?.correct ?: 0 }.toFloat() / att) else 0f
                                    }
                                    .thenByDescending { g -> g.maxOfOrNull { statsMap[it.id]?.lastSeen ?: 0L } ?: 0L }
                            )
                        }
                    }

                for (g in mistakenCandidates) {
                    if (chosenReviewCount >= targetMistakenCount && chosenReviewGroups.isNotEmpty()) break
                    chosenReviewGroups.add(g)
                    chosenReviewCount += g.size
                    chosenReviewIds.addAll(g.map { it.id })
                }
            }

            // 2. If 0 or less than 40% mistaken questions, fill the deficit:
            // When shuffle is ON: completely random pickup without repetition.
            // When shuffle is OFF: follow sequence in the pool/scope.
            if (chosenReviewCount < targetMistakenCount) {
                val deficitCandidates = grouped
                    .filter { g -> g.none { it.id in chosenReviewIds } }
                    .let { if (isShuffle) it.shuffled() else it }

                for (g in deficitCandidates) {
                    if (chosenReviewCount >= targetMistakenCount && chosenReviewGroups.isNotEmpty()) break
                    chosenReviewGroups.add(g)
                    chosenReviewCount += g.size
                    chosenReviewIds.addAll(g.map { it.id })
                }
            }

            // 3. Pick remaining questions (at least 60%, or up to 100% if brand-new book) using progressive logic
            val remainingNeed = targetTotal - chosenReviewCount
            val remainingGroups = grouped.filter { g -> g.none { it.id in chosenReviewIds } }

            val groupScores = remainingGroups.associateWith { g ->
                var sum = 0f
                for (q in g) {
                    val stat = statsMap[q.id]
                    if (stat == null || stat.attempts == 0) {
                        sum += -1f
                    } else {
                        sum += (stat.correct.toFloat() / stat.attempts.toFloat()) + (stat.attempts * 0.0001f)
                    }
                }
                sum / g.size
            }

            // If shuffle is ON, randomize among equal score tiers (e.g. unattempted questions) so pickup is completely random and non-sequential
            val sortedRemaining = if (isShuffle) {
                remainingGroups.shuffled().sortedBy { groupScores[it] ?: 0f }
            } else {
                remainingGroups.sortedBy { groupScores[it] ?: 0f }
            }

            val chosenProgressiveGroups = mutableListOf<List<QuestionSummary>>()
            var chosenProgressiveCount = 0
            if (remainingNeed > 0) {
                for (g in sortedRemaining) {
                    if (limit != null && limit > 0 && chosenProgressiveCount >= remainingNeed && chosenProgressiveGroups.isNotEmpty()) break
                    chosenProgressiveGroups.add(g)
                    chosenProgressiveCount += g.size
                }
            }

            val progressiveResult = mutableListOf<List<QuestionSummary>>()
            progressiveResult.addAll(chosenReviewGroups)
            progressiveResult.addAll(chosenProgressiveGroups)

            // Trim to target total first to ensure exact question count
            val trimmedResult = mutableListOf<List<QuestionSummary>>()
            if (limit != null && limit > 0) {
                var count = 0
                for (g in progressiveResult) {
                    if (count >= limit && trimmedResult.isNotEmpty()) break
                    trimmedResult.add(g)
                    count += g.size
                }
            } else {
                trimmedResult.addAll(progressiveResult)
            }

            // 4. Ordering based on isShuffle:
            // Priority: Scope > Logics > Shuffling > Sequence
            // If shuffle is ON: completely randomize order (never follow sequence).
            // If shuffle is OFF: follow natural sequence in the book/scope.
            if (isShuffle) {
                trimmedResult.shuffle()
            } else {
                val originalIndexMap = pool.mapIndexed { idx, q -> q.id to idx }.toMap()
                trimmedResult.sortBy { g -> g.minOfOrNull { originalIndexMap[it.id] ?: 0 } ?: 0 }
            }

            grouped.clear()
            grouped.addAll(trimmedResult)
        }

        if (flowMode == "sequence" && offset > 1) {
            val dropCount = (offset - 1) % Math.max(1, pool.size)
            // sequence offset drops individual questions, which might break a passage group.
            // For simplicity, we drop GROUPS.
            val dropGroupsCount = dropCount % Math.max(1, grouped.size)
            val firstPart = grouped.drop(dropGroupsCount)
            val remainingNeed = (limit ?: 0) - firstPart.sumOf { it.size }
            if (remainingNeed > 0 && limit != null && limit > 0) {
                var added = 0
                val secondPart = mutableListOf<List<QuestionSummary>>()
                for (g in grouped.take(dropGroupsCount)) {
                    if (added >= remainingNeed) break
                    secondPart.add(g)
                    added += g.size
                }
                grouped.clear()
                grouped.addAll(firstPart)
                grouped.addAll(secondPart)
            } else {
                grouped.clear()
                grouped.addAll(firstPart)
            }
        }

        val finalGrouped = mutableListOf<List<QuestionSummary>>()
        if (limit != null && limit > 0) {
            var qCount = 0
            for (g in grouped) {
                if (qCount >= limit && finalGrouped.isNotEmpty()) break
                finalGrouped.add(g)
                qCount += g.size
            }
        } else {
            finalGrouped.addAll(grouped)
        }

        if (isShuffle && flowMode != "progressive") {
            finalGrouped.shuffle()
        }

        val finalQuestions = mutableListOf<QuestionSummary>()
        for (g in finalGrouped) {
            if (isShuffle) {
                finalQuestions.addAll(g.shuffled())
            } else {
                finalQuestions.addAll(g)
            }
        }

        return finalQuestions
    }
}
