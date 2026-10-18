package com.example.ui

import com.example.data.QuestionStatEntity
import com.example.data.QuestionSummary
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

object QuestionPoolProcessor {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    /**
     * Determines whether a question has graduated into the "Learned / Mastered" state.
     *
     * A question is mastered when:
     * - The user has achieved 3+ consecutive correct answers in a row (streak >= 3), OR
     * - The user has at least 3 attempts, a current consecutive streak >= 2, and >= 75% accuracy.
     *
     * Once mastered, even a question with a high historical `wrong` count graduates out of
     * the Active Mistake Remediation pool into the normal Spaced Repetition (SRS) pool.
     * If the user ever answers it wrong again in a future review, `streak` resets to 0 and
     * it immediately re-enters Active Mistake Remediation.
     */
    fun isQuestionMastered(stat: QuestionStatEntity?): Boolean {
        if (stat == null || stat.attempts <= 0) return false
        val accuracy = stat.correct.toFloat() / stat.attempts.toFloat()
        return stat.streak >= 3 || (stat.attempts >= 3 && stat.streak >= 2 && accuracy >= 0.75f)
    }

    /**
     * Active Mistake Urgency Score for Bucket 2A (60% of the 40% Repetition Pool = 24% of total quiz).
     * Evaluated ONLY on unmastered questions with `wrong > 0 && !isQuestionMastered(stat)`.
     *
     * Combines:
     * 1. Recency of failure (`streak == 0` means the very last attempt was wrong -> highest priority).
     * 2. Exponential recency decay (72-hour characteristic window) so recently wronged questions
     *    are remediated promptly.
     * 3. Log-damped net struggle (`ln(1 + max(0.5, wrong - 1.25 * streak))`) + error rate so
     *    chronic mistakes get extra weight without trapping high-wrong questions forever.
     * 4. Intra-session cooldown penalty when `streak >= 1` and the question was just answered
     *    correctly within the last 20 minutes, allowing true spaced repetition across quizzes.
     */
    internal fun computeMistakeUrgencyScore(stat: QuestionStatEntity, nowMs: Long): Float {
        val streakWeight = when (stat.streak) {
            0 -> 55.0f // Answered wrong on most recent attempt -> urgent remediation
            1 -> 28.0f // Answered right once since last mistake -> needs 2nd confirmation
            else -> 14.0f // streak == 2 -> 1 correct answer away from graduating to Learned!
        }

        val elapsedHours = if (stat.lastSeen > 0L && nowMs > stat.lastSeen) {
            (nowMs - stat.lastSeen).toFloat() / HOUR_MS.toFloat()
        } else {
            24.0f
        }

        // Exponential recency boost: recently wronged questions in recent quizzes score up to +20
        val recencyBoost = (20.0 * exp(-elapsedHours.toDouble() / 72.0)).toFloat()

        // Log-damped effective wrong count reduced by current recovery streak
        val effectiveWrong = (stat.wrong.toFloat() - stat.streak * 1.25f).coerceAtLeast(0.5f)
        val dampedWrongBoost = (ln(1.0 + effectiveWrong.toDouble()) * 6.0).toFloat()

        val accuracy = if (stat.attempts > 0) stat.correct.toFloat() / stat.attempts.toFloat() else 0f
        val errorRateBoost = (1.0f - accuracy) * 12.0f

        val overdueBoost = if (stat.nextReview in 1L..nowMs) 10.0f else 0.0f

        // Cooldown guard: if the user already got this mistake right in a quiz less than 20 minutes ago
        // (streak >= 1) and its nextReview is still in the future, dampen immediate back-to-back repetition.
        val cooldownPenalty = if (stat.streak >= 1 && elapsedHours < 0.33f && stat.nextReview > nowMs) {
            35.0f
        } else if (stat.streak == 0 && elapsedHours < 0.05f) {
            // Tiny 3-minute damper if other urgent mistakes exist
            8.0f
        } else {
            0.0f
        }

        return streakWeight + recencyBoost + dampedWrongBoost + errorRateBoost + overdueBoost - cooldownPenalty
    }

    /**
     * Spaced Repetition (SRS) Priority Score for Bucket 2B (40% of the 40% Repetition Pool = 16% of total quiz).
     * Evaluated on previously attempted questions (`attempts > 0`) that are either:
     * - Consolidating (`wrong == 0 && streak < 3`), or
     * - Graduated / Learned (`isQuestionMastered(stat) == true`), or
     * - Remaining attempted questions not already selected in Bucket 2A.
     *
     * Prioritizes:
     * 1. Overdue SRS questions (`nextReview <= nowMs`), ranked by relative overdue ratio.
     * 2. Consolidating questions (`streak == 1` or `2`) so they can reach `streak = 3` (Learned).
     * 3. Lower easeFactor (harder learned items) and higher elapsed fraction of scheduled interval.
     * 4. Applies a rotation cooldown penalty to questions just seen within the last 15 minutes so
     *    consecutive quizzes in the same sitting cycle through different attempted questions.
     */
    internal fun computeSrsPriorityScore(stat: QuestionStatEntity, nowMs: Long): Float {
        val elapsedMs = if (stat.lastSeen > 0L && nowMs > stat.lastSeen) {
            nowMs - stat.lastSeen
        } else {
            DAY_MS
        }
        val elapsedMinutes = elapsedMs.toFloat() / MINUTE_MS.toFloat()

        val scheduledWindowMs = when {
            stat.nextReview > stat.lastSeen && stat.lastSeen > 0L ->
                (stat.nextReview - stat.lastSeen).coerceAtLeast(HOUR_MS)
            else ->
                (stat.interval.coerceAtLeast(1) * DAY_MS)
        }

        // Consolidation bonus helps streak 1 -> 2 -> 3 graduate to "Learned" status
        val consolidationBonus = when (stat.streak) {
            0 -> 22.0f
            1 -> 18.0f
            2 -> 12.0f
            3 -> 4.0f
            else -> 0.0f
        }

        val difficultyBonus = ((3.0f - stat.easeFactor).coerceIn(0f, 1.7f)) * 8.0f

        // Rotation guard: avoid picking the exact same attempted question in back-to-back quizzes (< 15 mins)
        val justSeenPenalty = if (elapsedMinutes < 15.0f) 45.0f else 0.0f

        return if (stat.nextReview <= nowMs) {
            val overdueMs = if (stat.nextReview > 0L) (nowMs - stat.nextReview).coerceAtLeast(0L) else elapsedMs
            val overdueRatio = (overdueMs.toFloat() / scheduledWindowMs.toFloat()).coerceIn(0f, 5.0f)
            100.0f + (overdueRatio * 25.0f) + consolidationBonus + difficultyBonus - justSeenPenalty
        } else {
            val elapsedRatio = (elapsedMs.toFloat() / scheduledWindowMs.toFloat()).coerceIn(0f, 1.0f)
            (elapsedRatio * 50.0f) + consolidationBonus + difficultyBonus - justSeenPenalty
        }
    }

    fun process(
        pool: List<QuestionSummary>,
        flowMode: String,
        isShuffle: Boolean,
        limit: Int?,
        offset: Int = 1,
        statsMap: Map<Long, QuestionStatEntity> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): List<QuestionSummary> {
        if (pool.isEmpty()) return emptyList()

        // Group reading-comprehension / passage questions that share a <p> header and path
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

            // Target Quotas:
            // - 60% Progressive Frontier (Stage 1: Unvisited Discovery -> Stage 2: Unmastered Consolidation)
            // - 40% Repetition Pool, subdivided into:
            //     * 60% of Repetition (24% of total) -> Bucket 2A: Active Mistake Remediation (wrong > 0 && !mastered)
            //     * 40% of Repetition (16% of total) -> Bucket 2B: Spaced Repetition of Attempted/Learned Questions
            val targetRepetitionTotal = (targetTotal * 0.40f).roundToInt().coerceIn(0, targetTotal)
            val targetActiveMistakes = (targetRepetitionTotal * 0.60f).roundToInt()
            val targetSrsReview = (targetRepetitionTotal - targetActiveMistakes).coerceAtLeast(0)

            val chosenIds = mutableSetOf<Long>()
            val chosenGroups = mutableListOf<List<QuestionSummary>>()
            var chosenCount = 0

            fun addGroupsFromCandidates(
                candidates: List<List<QuestionSummary>>,
                maxCountToAdd: Int
            ): Int {
                if (maxCountToAdd <= 0) return 0
                var added = 0
                for (g in candidates) {
                    if (g.any { it.id in chosenIds }) continue
                    if (added >= maxCountToAdd && added > 0) break
                    if (chosenCount >= targetTotal && chosenGroups.isNotEmpty()) break
                    chosenGroups.add(g)
                    chosenIds.addAll(g.map { it.id })
                    added += g.size
                    chosenCount += g.size
                }
                return added
            }

            // Classify all groups into:
            // 1. Active Mistake Groups: at least one question has wrong > 0 AND !isQuestionMastered(stat)
            // 2. SRS Attempted Groups: all questions attempted (or partially attempted) and not in Active Mistakes
            // 3. Unvisited Frontier Groups: unattempted questions (attempts == 0)
            val activeMistakeGroups = grouped.filter { g ->
                g.any { q ->
                    val st = statsMap[q.id]
                    st != null && st.wrong > 0 && !isQuestionMastered(st)
                }
            }

            val activeMistakeScores = activeMistakeGroups.associateWith { g ->
                val scores = g.mapNotNull { q ->
                    val st = statsMap[q.id]
                    if (st != null && st.wrong > 0 && !isQuestionMastered(st)) {
                        computeMistakeUrgencyScore(st, nowMs)
                    } else null
                }
                if (scores.isNotEmpty()) scores.average().toFloat() else 0f
            }

            val sortedActiveMistakes = (if (isShuffle) activeMistakeGroups.shuffled() else activeMistakeGroups)
                .sortedWith(
                    compareByDescending<List<QuestionSummary>> { g ->
                        // Quantize score to 0.5f bands when shuffle is ON so near-equal urgency items randomize
                        val raw = activeMistakeScores[g] ?: 0f
                        if (isShuffle) (raw * 2f).roundToInt() / 2f else raw
                    }.thenByDescending { g ->
                        g.maxOfOrNull { statsMap[it.id]?.lastSeen ?: 0L } ?: 0L
                    }
                )

            val srsCandidateGroups = grouped.filter { g ->
                g.any { q -> (statsMap[q.id]?.attempts ?: 0) > 0 }
            }

            val srsScores = srsCandidateGroups.associateWith { g ->
                val scores = g.mapNotNull { q ->
                    val st = statsMap[q.id]
                    if (st != null && st.attempts > 0) {
                        computeSrsPriorityScore(st, nowMs)
                    } else null
                }
                if (scores.isNotEmpty()) scores.average().toFloat() else 0f
            }

            // For SRS candidates, prefer groups that are NOT active mistakes first (graduated or wrong == 0 consolidating),
            // then rank by SRS Priority Score (overdue ratio, consolidation bonus, forgetting curve).
            val sortedSrsCandidates = (if (isShuffle) srsCandidateGroups.shuffled() else srsCandidateGroups)
                .sortedWith(
                    compareByDescending<List<QuestionSummary>> { g ->
                        // Pure SRS / Consolidating / Graduated groups get first preference in Bucket 2B
                        g.none { q ->
                            val st = statsMap[q.id]
                            st != null && st.wrong > 0 && !isQuestionMastered(st)
                        }
                    }.thenByDescending { g ->
                        val raw = srsScores[g] ?: 0f
                        if (isShuffle) (raw * 2f).roundToInt() / 2f else raw
                    }.thenBy { g ->
                        g.minOfOrNull { statsMap[it.id]?.nextReview ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
                    }
                )

            // STEP 1: Fill Sub-Bucket 2A — Active Mistake Remediation (target = 60% of 40% = 24% of total quiz)
            val addedMistakes = addGroupsFromCandidates(sortedActiveMistakes, targetActiveMistakes)

            // STEP 2: Fill Sub-Bucket 2B — Spaced Repetition of Attempted Questions (target = 40% of 40% = 16% + any unused slots from 2A)
            val mistakeDeficit = (targetActiveMistakes - addedMistakes).coerceAtLeast(0)
            val srsTargetWithSpillover = targetSrsReview + mistakeDeficit
            val addedSrs = addGroupsFromCandidates(sortedSrsCandidates, srsTargetWithSpillover)

            // STEP 3: If Sub-Bucket 2B had fewer SRS items than needed, allow Sub-Bucket 2A (Active Mistakes) to absorb the remaining repetition quota
            val remainingRepetitionQuota = (targetRepetitionTotal - (addedMistakes + addedSrs)).coerceAtLeast(0)
            if (remainingRepetitionQuota > 0) {
                addGroupsFromCandidates(sortedActiveMistakes, remainingRepetitionQuota)
            }

            // STEP 4: Fill Bucket 1 — Progressive Frontier (60% of quiz + any unused repetition quota if the book is new!)
            // Stage 1 Frontier: Unvisited questions (attempts == 0) in natural book sequence (or shuffled if isShuffle is ON)
            val remainingNeedForFrontier = (targetTotal - chosenCount).coerceAtLeast(0)
            if (remainingNeedForFrontier > 0) {
                val unvisitedGroups = grouped.filter { g ->
                    g.none { it.id in chosenIds } && g.any { (statsMap[it.id]?.attempts ?: 0) == 0 }
                }.let { list ->
                    if (isShuffle) list.shuffled() else list
                }

                addGroupsFromCandidates(unvisitedGroups, remainingNeedForFrontier)
            }

            // STEP 5: Post-100%-Visited Multi-Pass Mastery Ladder (when fewer than 60% or 0 unvisited questions remain)
            // Stage 2 Frontier (Pass 2 & Pass 3 Consolidation): Advance sequentially through visited-but-unmastered
            // questions (wrong == 0 && !isQuestionMastered, i.e., streak 1 -> 2 -> 3) so every visited question
            // in the book progresses to "Learned" status while respecting a 15-minute cooldown.
            if (chosenCount < targetTotal) {
                val consolidatingFrontierGroups = grouped.filter { g ->
                    g.none { it.id in chosenIds } && g.any { q ->
                        val st = statsMap[q.id]
                        st != null && st.attempts > 0 && st.wrong == 0 && !isQuestionMastered(st)
                    }
                }.let { list ->
                    if (isShuffle) list.shuffled() else list
                }.sortedWith(
                    compareBy<List<QuestionSummary>> { g ->
                        // Prefer questions not seen in the last 15 minutes first
                        val lastSeen = g.maxOfOrNull { statsMap[it.id]?.lastSeen ?: 0L } ?: 0L
                        if (lastSeen > 0L && (nowMs - lastSeen) < 15L * MINUTE_MS) 1 else 0
                    }.thenBy { g ->
                        // Pass 2 (streak == 1) before Pass 3 (streak == 2)
                        g.minOfOrNull { statsMap[it.id]?.streak ?: 0 } ?: 0
                    }
                )
                addGroupsFromCandidates(consolidatingFrontierGroups, targetTotal - chosenCount)
            }

            // Stage 3 Mastery & Remediation Spillover: Fill any still-open slots from Active Mistakes, then SRS Review
            if (chosenCount < targetTotal) {
                addGroupsFromCandidates(sortedActiveMistakes, targetTotal - chosenCount)
            }
            if (chosenCount < targetTotal) {
                addGroupsFromCandidates(sortedSrsCandidates, targetTotal - chosenCount)
            }
            if (chosenCount < targetTotal) {
                val fallbackRemaining = grouped.filter { g -> g.none { it.id in chosenIds } }
                    .let { if (isShuffle) it.shuffled() else it }
                addGroupsFromCandidates(fallbackRemaining, targetTotal - chosenCount)
            }

            // Trim to targetTotal to ensure exact question count
            val trimmedResult = mutableListOf<List<QuestionSummary>>()
            if (limit != null && limit > 0) {
                var count = 0
                for (g in chosenGroups) {
                    if (count >= limit && trimmedResult.isNotEmpty()) break
                    trimmedResult.add(g)
                    count += g.size
                }
            } else {
                trimmedResult.addAll(chosenGroups)
            }

            // Ordering based on isShuffle:
            // - If Shuffle is ON: randomize question group order.
            // - If Shuffle is OFF: order selected review + new frontier questions in natural book sequence.
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
