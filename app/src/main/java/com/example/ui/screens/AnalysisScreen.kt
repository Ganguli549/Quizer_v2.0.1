package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QBookExam
import com.example.data.QuestionEntity
import com.example.data.QuestionStatEntity
import com.example.data.QuestionSummary
import com.example.data.QuizHistoryEntity
import com.example.data.SharedGson
import com.example.data.TIMEOUT_SKIPPED_SENTINEL
import com.example.data.resolveCorrectIndices
import com.example.data.resolveHeaders
import com.example.ui.MainViewModel
import com.example.ui.QuestionPoolProcessor
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

data class TopicDiagnosticItem(
    val key: String,
    val title: String,
    val parentPath: String,
    val totalQuestions: Int,
    val visitedCount: Int,
    val masteredCount: Int,
    val activeMistakeCount: Int,
    val totalAttempts: Int,
    val totalCorrect: Int,
    val allQuestionIds: List<Long>,
    val mistakeQuestionIds: List<Long>,
    val unmasteredQuestionIds: List<Long>,
    val weaknessPriorityScore: Float
) {
    val accuracyPct: Float
        get() = if (totalAttempts > 0) (totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f else 0f
    val coveragePct: Float
        get() = if (totalQuestions > 0) (visitedCount.toFloat() / totalQuestions.toFloat()) * 100f else 0f
    val masteryPct: Float
        get() = if (totalQuestions > 0) (masteredCount.toFloat() / totalQuestions.toFloat()) * 100f else 0f

    val statusLabel: String
        get() = when {
            visitedCount == 0 -> "Unvisited"
            accuracyPct < 50f || activeMistakeCount >= 5 -> "Critical"
            accuracyPct < 75f -> "Review"
            masteryPct >= 80f && accuracyPct >= 85f -> "Mastered"
            else -> "Strong"
        }
}

data class PacingQuadrantAnalysis(
    val totalTimedAnswers: Int,
    val avgCorrectTimeSec: Float,
    val avgWrongTimeSec: Float,
    val avgSkippedTimeSec: Float,
    val fastAccurateCount: Int,
    val carefulAccurateCount: Int,
    val rushedMistakeCount: Int,
    val timeSinkTrapCount: Int,
    val rushedMistakeIds: List<Long>,
    val timeSinkQuestionIds: List<Long>
)

data class CriticalQuestionDiagnostic(
    val stat: QuestionStatEntity,
    val question: QuestionEntity,
    val urgencyScore: Float,
    val isMastered: Boolean,
    val isOverdue: Boolean
)

data class DiagnosticInsightItem(
    val title: String,
    val description: String,
    val severity: String,
    val actionLabel: String? = null,
    val actionQuestionIds: List<Long> = emptyList()
)

data class AnalysisData(
    val readinessScore: Int,
    val readinessLabel: String,
    val coverageScorePct: Float,
    val masteryScorePct: Float,
    val accuracyScorePct: Float,
    val retentionHealthPct: Float,
    val dueToday: Int,
    val overdue: Int,
    val activeMistakesCount: Int,
    val totalMistakenCount: Int,
    val consolidatingCount: Int,
    val weakTopicQuestionsCount: Int,
    val dueQuestionIds: List<Long>,
    val weakTopicQuestionIds: List<Long>,
    val criticalQuestionIds: List<Long>,
    val consolidatingQuestionIds: List<Long>,
    val insights: List<DiagnosticInsightItem>,
    val topicDiagnostics: List<TopicDiagnosticItem>,
    val categoryDiagnostics: List<TopicDiagnosticItem>,
    val examDiagnostics: List<TopicDiagnosticItem>,
    val pacingAnalysis: PacingQuadrantAnalysis,
    val criticalQuestions: List<CriticalQuestionDiagnostic>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: MainViewModel,
    onHome: () -> Unit,
    onStartQuiz: () -> Unit,
    onNavigateStats: (() -> Unit)? = null
) {
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    val stats by viewModel.allStats.collectAsState()
    val history by viewModel.history.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    val books by viewModel.books.collectAsState()

    val bookmarkedIdSet = remember(bookmarks) { bookmarks.map { it.questionId }.toSet() }
    val currentBookName = remember(books, currentBookId) {
        books.find { it.id == currentBookId }?.name ?: currentBookId.ifBlank { "Current Book" }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDrillLimit by remember { mutableStateOf<Int?>(25) }
    var matrixDimension by remember { mutableStateOf("Topics") }
    var matrixSort by remember { mutableStateOf("Weakest") }
    var criticalFilter by remember { mutableStateOf("Active") }

    val analysisData by produceState<AnalysisData?>(initialValue = null, allQuestions, stats, history, currentBookId) {
        value = withContext(Dispatchers.Default) {
            buildDiagnosticAnalysisData(
                viewModel = viewModel,
                currentBookId = currentBookId,
                allQuestions = allQuestions,
                stats = stats,
                history = history
            )
        }
    }

    fun launchDrill(ids: List<Long>, customLimit: Int? = selectedDrillLimit) {
        if (ids.isEmpty()) return
        viewModel.playNavigation()
        viewModel.startTargetedAnalysisDrill(questionIds = ids, limit = customLimit, onReady = onStartQuiz)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Analysis Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentBookName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onNavigateStats != null) {
                        FilledTonalButton(
                            onClick = onNavigateStats,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Statistics",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            val data = analysisData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 4-Tab Diagnostic Navigation Bar with Icons
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    ) {
                        val tabs = listOf(
                            Triple("Overview", Icons.Default.Dashboard, null),
                            Triple("Topics", Icons.Default.AccountTree, data.topicDiagnostics.size),
                            Triple("Pacing", Icons.Default.Speed, null),
                            Triple("Critical", Icons.Default.ErrorOutline, data.activeMistakesCount)
                        )
                        tabs.forEachIndexed { idx, (label, icon, badgeNum) ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (badgeNum != null) "$label ($badgeNum)" else label,
                                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> OverviewDiagnosticTab(
                            data = data,
                            selectedDrillLimit = selectedDrillLimit,
                            onSelectDrillLimit = { selectedDrillLimit = it },
                            onLaunchDrill = { ids -> launchDrill(ids, selectedDrillLimit) },
                            onJumpToTab = { selectedTab = it }
                        )
                        1 -> TopicAndExamMatrixTab(
                            data = data,
                            dimension = matrixDimension,
                            onDimensionChange = { matrixDimension = it },
                            sortMode = matrixSort,
                            onSortModeChange = { matrixSort = it },
                            onLaunchDrill = { ids -> launchDrill(ids, selectedDrillLimit) }
                        )
                        2 -> SpeedAndPacingTab(
                            pacing = data.pacingAnalysis,
                            onLaunchDrill = { ids -> launchDrill(ids, selectedDrillLimit) }
                        )
                        3 -> CriticalQuestionsInspectorTab(
                            criticalList = data.criticalQuestions,
                            filterMode = criticalFilter,
                            onFilterModeChange = { criticalFilter = it },
                            bookmarkedIds = bookmarkedIdSet,
                            onToggleBookmark = { qId -> viewModel.toggleBookmark(currentBookId, qId) },
                            onLaunchDrill = { ids -> launchDrill(ids, selectedDrillLimit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewDiagnosticTab(
    data: AnalysisData,
    selectedDrillLimit: Int?,
    onSelectDrillLimit: (Int?) -> Unit,
    onLaunchDrill: (List<Long>) -> Unit,
    onJumpToTab: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Exam Readiness Index & 4-Factor Diagnostic Card
        item(key = "exam_readiness_card") {
            ExamReadinessHeroCard(data = data)
        }

        // 2. Automated Diagnostic Insights
        if (data.insights.isNotEmpty()) {
            item(key = "diagnostic_insights_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnostic Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    data.insights.forEach { insight ->
                        DiagnosticInsightCard(
                            insight = insight,
                            onActionClick = {
                                if (insight.actionQuestionIds.isNotEmpty()) {
                                    onLaunchDrill(insight.actionQuestionIds)
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Smart Targeted Practice Launchpad (Title Row separated from Drill Size Selector Row)
        item(key = "targeted_practice_launchpad") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Targeted Remediation Drills",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Select question limit and launch a focused practice session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Segmented Drill Size Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Drill Size:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        listOf(15 to "15 Qs", 25 to "25 Qs", 50 to "50 Qs", null to "All").forEach { (limitVal, label) ->
                            val isSelected = selectedDrillLimit == limitVal
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectDrillLimit(limitVal) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                TargetedDrillActionCard(
                    title = "Daily Spaced Revision",
                    subtitle = if (data.dueToday > 0) {
                        "${data.dueToday} due now (${data.overdue} overdue >24h)"
                    } else {
                        "All scheduled spaced reviews are up to date!"
                    },
                    badgeCount = data.dueToday,
                    icon = Icons.Default.Schedule,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    enabled = data.dueQuestionIds.isNotEmpty(),
                    onClick = { onLaunchDrill(data.dueQuestionIds) }
                )

                TargetedDrillActionCard(
                    title = "Weak Topics Remediation",
                    subtitle = if (data.weakTopicQuestionIds.isNotEmpty()) {
                        "${data.weakTopicQuestionIds.size} unmastered Qs in low-accuracy topics"
                    } else {
                        "No weak topics (<65% accuracy) detected yet"
                    },
                    badgeCount = data.weakTopicQuestionIds.size,
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    enabled = data.weakTopicQuestionIds.isNotEmpty(),
                    onClick = { onLaunchDrill(data.weakTopicQuestionIds) }
                )

                TargetedDrillActionCard(
                    title = "Critical Mistakes Drill",
                    subtitle = if (data.criticalQuestionIds.isNotEmpty()) {
                        "${data.activeMistakesCount} active mistakes (${data.totalMistakenCount} historical)"
                    } else {
                        "Zero unmastered mistakes in this book!"
                    },
                    badgeCount = data.criticalQuestionIds.size,
                    icon = Icons.Default.WarningAmber,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    enabled = data.criticalQuestionIds.isNotEmpty(),
                    onClick = { onLaunchDrill(data.criticalQuestionIds) }
                )

                TargetedDrillActionCard(
                    title = "Consolidation Boost",
                    subtitle = if (data.consolidatingQuestionIds.isNotEmpty()) {
                        "${data.consolidatingQuestionIds.size} Qs at Streak 1–2 ready to reach Mastered"
                    } else {
                        "Attempt new questions to build your consolidation queue"
                    },
                    badgeCount = data.consolidatingQuestionIds.size,
                    icon = Icons.Default.Bolt,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    enabled = data.consolidatingQuestionIds.isNotEmpty(),
                    onClick = { onLaunchDrill(data.consolidatingQuestionIds) }
                )
            }
        }

        // 4. Top Weakest Topics Quick Preview
        val topWeakTopics = data.topicDiagnostics.filter { it.visitedCount > 0 }.take(3)
        if (topWeakTopics.isNotEmpty()) {
            item(key = "top_weak_topics_preview") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Priority Topics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onJumpToTab(1) }) {
                            Text(
                                text = "View All (${data.topicDiagnostics.size})",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    topWeakTopics.forEach { item ->
                        TopicDiagnosticCard(
                            item = item,
                            onDrillTopic = { onLaunchDrill(item.unmasteredQuestionIds.ifEmpty { item.allQuestionIds }) },
                            onDrillMistakes = { onLaunchDrill(item.mistakeQuestionIds) }
                        )
                    }
                }
            }
        }

        item(key = "overview_bottom_spacer") {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ExamReadinessHeroCard(data: AnalysisData) {
    val animatedReadiness by animateFloatAsState(
        targetValue = data.readinessScore.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "readinessAnim"
    )

    val scoreColor = when {
        data.readinessScore >= 80 -> Color(0xFF16A34A)
        data.readinessScore >= 55 -> Color(0xFF2563EB)
        data.readinessScore >= 30 -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.primary
    }

    val readinessIcon = when {
        data.readinessScore >= 80 -> Icons.Default.Verified
        data.readinessScore >= 55 -> Icons.AutoMirrored.Filled.TrendingUp
        data.readinessScore >= 30 -> Icons.Default.Bolt
        else -> Icons.Default.Flag
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exam Readiness Index",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Coverage, mastery, accuracy & retention",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconStatTag(
                    icon = readinessIcon,
                    text = data.readinessLabel,
                    containerColor = scoreColor.copy(alpha = 0.14f),
                    contentColor = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    ) {
                        val strokeW = 11.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = scoreColor,
                            startAngle = 135f,
                            sweepAngle = (animatedReadiness / 100f).coerceIn(0f, 1f) * 270f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${animatedReadiness.roundToInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadinessSubBar("Coverage", Icons.Default.DonutLarge, data.coverageScorePct, Color(0xFF2563EB))
                    ReadinessSubBar("Mastery", Icons.Default.Verified, data.masteryScorePct, Color(0xFF16A34A))
                    ReadinessSubBar("Accuracy", Icons.Default.TrackChanges, data.accuracyScorePct, MaterialTheme.colorScheme.primary)
                    ReadinessSubBar("Retention", Icons.Default.Psychology, data.retentionHealthPct, Color(0xFF7C3AED))
                }
            }
        }
    }
}

@Composable
private fun ReadinessSubBar(
    label: String,
    icon: ImageVector,
    pct: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "${pct.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun DiagnosticInsightCard(
    insight: DiagnosticInsightItem,
    onActionClick: () -> Unit
) {
    val (accentColor, icon) = when (insight.severity) {
        "critical" -> MaterialTheme.colorScheme.error to Icons.Default.ErrorOutline
        "warning" -> Color(0xFFD97706) to Icons.Default.WarningAmber
        "positive" -> Color(0xFF16A34A) to Icons.Default.Verified
        else -> MaterialTheme.colorScheme.primary to Icons.Default.Lightbulb
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!insight.actionLabel.isNullOrBlank() && insight.actionQuestionIds.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = insight.actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetedDrillActionCard(
    title: String,
    subtitle: String,
    badgeCount: Int,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Drill",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicAndExamMatrixTab(
    data: AnalysisData,
    dimension: String,
    onDimensionChange: (String) -> Unit,
    sortMode: String,
    onSortModeChange: (String) -> Unit,
    onLaunchDrill: (List<Long>) -> Unit
) {
    val activeList = remember(data, dimension, sortMode) {
        val raw = when (dimension) {
            "Categories" -> data.categoryDiagnostics
            "Exams" -> data.examDiagnostics
            else -> data.topicDiagnostics
        }
        when (sortMode) {
            "Most Mistakes" -> raw.sortedWith(
                compareByDescending<TopicDiagnosticItem> { it.activeMistakeCount }
                    .thenByDescending { it.weaknessPriorityScore }
            )
            "Strongest" -> raw.sortedWith(
                compareByDescending<TopicDiagnosticItem> { it.visitedCount > 0 }
                    .thenByDescending { it.accuracyPct }
                    .thenByDescending { it.masteryPct }
            )
            else -> raw.sortedByDescending { it.weaknessPriorityScore }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "matrix_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Topics", Icons.Default.AccountTree, data.topicDiagnostics.size),
                            Triple("Categories", Icons.Default.Category, data.categoryDiagnostics.size),
                            Triple("Exams", Icons.Default.WorkspacePremium, data.examDiagnostics.size)
                        ).forEach { (key, icon, count) ->
                            val selected = dimension == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onDimensionChange(key) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$key ($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple("Weakest", Icons.AutoMirrored.Filled.TrendingDown, "Weakest"),
                        Triple("Most Mistakes", Icons.Default.ErrorOutline, "Most Mistakes"),
                        Triple("Strongest", Icons.Default.Verified, "Strongest")
                    ).forEach { (mode, icon, label) ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { onSortModeChange(mode) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        if (activeList.isEmpty()) {
            item(key = "empty_matrix") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No $dimension metadata found in this book.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(activeList, key = { "${dimension}_${it.key}" }) { item ->
                TopicDiagnosticCard(
                    item = item,
                    onDrillTopic = { onLaunchDrill(item.unmasteredQuestionIds.ifEmpty { item.allQuestionIds }) },
                    onDrillMistakes = { onLaunchDrill(item.mistakeQuestionIds) }
                )
            }
        }

        item(key = "matrix_bottom_spacer") {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TopicDiagnosticCard(
    item: TopicDiagnosticItem,
    onDrillTopic: () -> Unit,
    onDrillMistakes: () -> Unit
) {
    val (statusColor, statusIcon) = when (item.statusLabel) {
        "Critical" -> MaterialTheme.colorScheme.error to Icons.Default.ErrorOutline
        "Review" -> Color(0xFFD97706) to Icons.Default.WarningAmber
        "Mastered" -> Color(0xFF16A34A) to Icons.Default.Verified
        "Strong" -> Color(0xFF2563EB) to Icons.AutoMirrored.Filled.TrendingUp
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.RadioButtonUnchecked
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (item.parentPath.isNotBlank()) {
                        Text(
                            text = item.parentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconStatTag(
                    icon = statusIcon,
                    text = if (item.visitedCount > 0) "${item.accuracyPct.roundToInt()}%" else "New",
                    containerColor = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Icon-based Telemetry Pills (replaces long text "Coverage: ... Mastered: ... Mistakes: ...")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconStatTag(
                    icon = Icons.Default.DonutLarge,
                    text = "${item.visitedCount}/${item.totalQuestions} (${item.coveragePct.roundToInt()}%)",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconStatTag(
                    icon = Icons.Default.Verified,
                    text = "${item.masteredCount}",
                    containerColor = Color(0xFF16A34A).copy(alpha = 0.12f),
                    contentColor = Color(0xFF16A34A)
                )
                if (item.activeMistakeCount > 0) {
                    IconStatTag(
                        icon = Icons.Default.Cancel,
                        text = "${item.activeMistakeCount}",
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (item.coveragePct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.mistakeQuestionIds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onDrillMistakes,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${item.mistakeQuestionIds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                FilledTonalButton(
                    onClick = onDrillTopic,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Drill (${item.unmasteredQuestionIds.ifEmpty { item.allQuestionIds }.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedAndPacingTab(
    pacing: PacingQuadrantAnalysis,
    onLaunchDrill: (List<Long>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "pacing_comparison_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Response Time vs. Accuracy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pacing.totalTimedAnswers > 0) {
                            "Analyzed across ${pacing.totalTimedAnswers} timed responses in recent quizzes"
                        } else {
                            "Complete quizzes to capture per-question speed & pacing diagnostics"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PacingMetricPill(
                            label = "Correct",
                            icon = Icons.Default.CheckCircle,
                            value = "${String.format(Locale.US, "%.1f", pacing.avgCorrectTimeSec)}s",
                            color = Color(0xFF16A34A),
                            modifier = Modifier.weight(1f)
                        )
                        PacingMetricPill(
                            label = "Wrong",
                            icon = Icons.Default.Cancel,
                            value = "${String.format(Locale.US, "%.1f", pacing.avgWrongTimeSec)}s",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        PacingMetricPill(
                            label = "Skipped",
                            icon = Icons.Default.RemoveCircleOutline,
                            value = "${String.format(Locale.US, "%.1f", pacing.avgSkippedTimeSec)}s",
                            color = Color(0xFFD97706),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. 4-Quadrant Speed vs. Accuracy Matrix with Equalized Card Heights
        item(key = "pacing_quadrant_grid") {
            val total = pacing.totalTimedAnswers.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "4-Quadrant Pacing Matrix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuadrantBoxCard(
                        title = "Fast & Accurate",
                        subtitle = "< 15s • ✓",
                        count = pacing.fastAccurateCount,
                        pct = ((pacing.fastAccurateCount.toFloat() / total) * 100f).roundToInt(),
                        accentColor = Color(0xFF16A34A),
                        icon = Icons.Default.Bolt,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuadrantBoxCard(
                        title = "Careful & Right",
                        subtitle = "≥ 15s • ✓",
                        count = pacing.carefulAccurateCount,
                        pct = ((pacing.carefulAccurateCount.toFloat() / total) * 100f).roundToInt(),
                        accentColor = Color(0xFF2563EB),
                        icon = Icons.Default.GpsFixed,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuadrantBoxCard(
                        title = "Rushed Mistakes",
                        subtitle = "< 10s • ✗",
                        count = pacing.rushedMistakeCount,
                        pct = ((pacing.rushedMistakeCount.toFloat() / total) * 100f).roundToInt(),
                        accentColor = Color(0xFFD97706),
                        icon = Icons.Default.Speed,
                        actionLabel = if (pacing.rushedMistakeIds.isNotEmpty()) "Drill (${pacing.rushedMistakeIds.size})" else null,
                        onAction = { onLaunchDrill(pacing.rushedMistakeIds) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuadrantBoxCard(
                        title = "Time-Sink Traps",
                        subtitle = "≥ 30s • ✗ / –",
                        count = pacing.timeSinkTrapCount,
                        pct = ((pacing.timeSinkTrapCount.toFloat() / total) * 100f).roundToInt(),
                        accentColor = MaterialTheme.colorScheme.error,
                        icon = Icons.Default.HourglassBottom,
                        actionLabel = if (pacing.timeSinkQuestionIds.isNotEmpty()) "Drill (${pacing.timeSinkQuestionIds.size})" else null,
                        onAction = { onLaunchDrill(pacing.timeSinkQuestionIds) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        item(key = "pacing_bottom_spacer") {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PacingMetricPill(
    label: String,
    icon: ImageVector,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun QuadrantBoxCard(
    title: String,
    subtitle: String,
    count: Int,
    pct: Int,
    accentColor: Color,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CriticalQuestionsInspectorTab(
    criticalList: List<CriticalQuestionDiagnostic>,
    filterMode: String,
    onFilterModeChange: (String) -> Unit,
    bookmarkedIds: Set<Long>,
    onToggleBookmark: (Long) -> Unit,
    onLaunchDrill: (List<Long>) -> Unit
) {
    val filteredList = remember(criticalList, filterMode) {
        when (filterMode) {
            "Active" -> criticalList.filter { !it.isMastered }
            "Overdue" -> criticalList.filter { it.isOverdue }
            else -> criticalList
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "critical_header_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Active", "All Mistakes", "Overdue").forEach { mode ->
                        FilterChip(
                            selected = filterMode == mode,
                            onClick = { onFilterModeChange(mode) },
                            label = {
                                Text(
                                    text = mode,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                if (filteredList.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onLaunchDrill(filteredList.map { it.question.id }) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Drill (${filteredList.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item(key = "empty_critical_list") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No questions match the '$filterMode' filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredList, key = { it.stat.questionKey }) { item ->
                ExpandableCriticalQuestionCard(
                    item = item,
                    isBookmarked = item.question.id in bookmarkedIds,
                    onToggleBookmark = { onToggleBookmark(item.question.id) }
                )
            }
        }

        item(key = "critical_bottom_spacer") {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ExpandableCriticalQuestionCard(
    item: CriticalQuestionDiagnostic,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val q = item.question
    val st = item.stat

    val baseOptions = remember(q.options) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            SharedGson.normal.fromJson<List<String>>(q.options, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    val extraOptions = remember(q.extraOptions) {
        if (q.extraOptions.isBlank() || q.extraOptions == "[]") emptyList()
        else {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                SharedGson.normal.fromJson<List<String>>(q.extraOptions, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    val allCorrectIndices = remember(q) {
        q.resolveCorrectIndices(includeExtraOptions = true)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Icon Badges (Failed count, Recovery Streak / Mastered, and Overdue)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconStatTag(
                        icon = Icons.Default.Cancel,
                        text = "${st.wrong}x",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                    IconStatTag(
                        icon = if (item.isMastered) Icons.Default.Verified else Icons.Default.LocalFireDepartment,
                        text = "${st.streak}/3",
                        containerColor = if (item.isMastered) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (item.isMastered) Color(0xFF166534) else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (item.isOverdue) {
                        IconStatTag(
                            icon = Icons.Default.Schedule,
                            text = "Due",
                            containerColor = Color(0xFFD97706).copy(alpha = 0.14f),
                            contentColor = Color(0xFFD97706)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (q.path.isNotBlank() || q.category.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Q.${q.id} • ${q.path.ifBlank { q.category }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            com.example.ui.components.SmartRichText(
                text = q.question,
                color = MaterialTheme.colorScheme.onSurface,
                isSelectable = expanded,
                onClick = { expanded = !expanded }
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(10.dp))

                    baseOptions.forEachIndexed { idx, optText ->
                        val isCorrect = allCorrectIndices.contains(idx)
                        val optColor = if (isCorrect) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(65 + idx).toChar()}. ",
                                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                                color = optColor
                            )
                            com.example.ui.components.SmartRichText(text = optText, color = optColor)
                        }
                    }

                    val correctExtraPairs = extraOptions.mapIndexedNotNull { extIdx, extTxt ->
                        if (allCorrectIndices.contains(-(extIdx + 1))) "ext$extIdx" to extTxt else null
                    }
                    if (correctExtraPairs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Correct Extra Options:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                        correctExtraPairs.forEach { (extId, extTxt) ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$extId: ",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A),
                                    fontSize = 13.sp
                                )
                                com.example.ui.components.SmartRichText(
                                    text = extTxt,
                                    color = Color(0xFF16A34A),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    val explanationText = q.detailedExplanation?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        ?: q.explanation.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                    if (!explanationText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Explanation",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                com.example.ui.components.SmartRichText(
                                    text = explanationText,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun buildDiagnosticAnalysisData(
    viewModel: MainViewModel,
    currentBookId: String,
    allQuestions: List<QuestionSummary>,
    stats: List<QuestionStatEntity>,
    history: List<QuizHistoryEntity>
): AnalysisData {
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L

    val bookStats = stats.filter { it.bookId == currentBookId && it.attempts > 0 }
    val statsByQId = bookStats.associateBy { it.questionId }
    val totalQuestions = allQuestions.size.coerceAtLeast(bookStats.size)

    val visitedCount = bookStats.size
    val masteredCount = bookStats.count { QuestionPoolProcessor.isQuestionMastered(it) }
    val totalAttempts = bookStats.sumOf { it.attempts }
    val totalCorrect = bookStats.sumOf { it.correct }

    val coveragePct = if (totalQuestions > 0) (visitedCount.toFloat() / totalQuestions.toFloat()) * 100f else 0f
    val masteryPct = if (totalQuestions > 0) (masteredCount.toFloat() / totalQuestions.toFloat()) * 100f else 0f
    val accuracyPct = if (totalAttempts > 0) (totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f else 0f

    val dueList = allQuestions.filter { q ->
        val st = statsByQId[q.id]
        st != null && st.nextReview in 1L..now
    }.sortedBy { q -> statsByQId[q.id]?.nextReview ?: Long.MAX_VALUE }
    val dueQuestionIds = dueList.map { it.id }
    val dueToday = dueQuestionIds.size
    val overdue = bookStats.count { it.nextReview in 1L..(now - dayMs) }

    val retentionHealthPct = if (visitedCount > 0) {
        (((visitedCount - overdue).coerceAtLeast(0).toFloat() / visitedCount.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 100f

    val readinessScore = if (visitedCount == 0) {
        0
    } else {
        (coveragePct * 0.25f + masteryPct * 0.35f + accuracyPct * 0.25f + retentionHealthPct * 0.15f).roundToInt()
            .coerceIn(1, 100)
    }
    val readinessLabel = when {
        readinessScore >= 80 -> "Exam Ready"
        readinessScore >= 60 -> "Strong"
        readinessScore >= 35 -> "Building"
        readinessScore > 0 -> "Starting"
        else -> "New"
    }

    val mistakenQuestionsSorted = allQuestions.filter { q -> (statsByQId[q.id]?.wrong ?: 0) > 0 }.sortedWith(
        compareByDescending<QuestionSummary> { q ->
            val st = statsByQId[q.id]
            st != null && !QuestionPoolProcessor.isQuestionMastered(st)
        }.thenByDescending { q ->
            val st = statsByQId[q.id]
            if (st != null) QuestionPoolProcessor.computeMistakeUrgencyScore(st, now) else 0f
        }
    )
    val totalMistakenCount = mistakenQuestionsSorted.size
    val activeMistakeIds = mistakenQuestionsSorted.filter { q ->
        val st = statsByQId[q.id]
        st != null && !QuestionPoolProcessor.isQuestionMastered(st)
    }.map { it.id }
    val activeMistakesCount = activeMistakeIds.size
    val criticalQuestionIds = activeMistakeIds.ifEmpty { mistakenQuestionsSorted.map { it.id } }

    val consolidatingQuestionIds = allQuestions.filter { q ->
        val st = statsByQId[q.id]
        st != null && !QuestionPoolProcessor.isQuestionMastered(st) && st.streak in 1..2
    }.sortedByDescending { q -> statsByQId[q.id]?.streak ?: 0 }.map { it.id }

    fun buildDiagnosticGroups(
        groups: Map<String, List<QuestionSummary>>,
        parseTitleAndParent: (String) -> Pair<String, String>
    ): List<TopicDiagnosticItem> {
        return groups.mapNotNull { (key, qs) ->
            if (key.isBlank() || qs.isEmpty()) return@mapNotNull null
            val (title, parent) = parseTitleAndParent(key)
            val qStats = qs.mapNotNull { statsByQId[it.id] }
            val vis = qStats.size
            val mast = qStats.count { QuestionPoolProcessor.isQuestionMastered(it) }
            val actMistakes = qs.filter { q ->
                val st = statsByQId[q.id]
                st != null && st.wrong > 0 && !QuestionPoolProcessor.isQuestionMastered(st)
            }.map { it.id }
            val unmasteredIds = qs.filter { q ->
                val st = statsByQId[q.id]
                st == null || !QuestionPoolProcessor.isQuestionMastered(st)
            }.map { it.id }
            val att = qStats.sumOf { it.attempts }
            val corr = qStats.sumOf { it.correct }
            val acc = if (att > 0) (corr.toFloat() / att.toFloat()) * 100f else 0f
            val weaknessScore = if (vis == 0) {
                15f
            } else {
                ((100f - acc) * 1.2f) + (actMistakes.size * 8f) + ((1f - mast.toFloat() / qs.size.coerceAtLeast(1)) * 20f)
            }
            TopicDiagnosticItem(
                key = key,
                title = title,
                parentPath = parent,
                totalQuestions = qs.size,
                visitedCount = vis,
                masteredCount = mast,
                activeMistakeCount = actMistakes.size,
                totalAttempts = att,
                totalCorrect = corr,
                allQuestionIds = qs.map { it.id },
                mistakeQuestionIds = actMistakes,
                unmasteredQuestionIds = unmasteredIds,
                weaknessPriorityScore = weaknessScore
            )
        }.sortedByDescending { it.weaknessPriorityScore }
    }

    val topicGroups = allQuestions.groupBy { q ->
        q.path.trim().ifBlank { q.category.trim().ifBlank { "General" } }
    }
    val topicDiagnostics = buildDiagnosticGroups(topicGroups) { rawPath ->
        val delimiter = if (rawPath.contains(">")) ">" else if (rawPath.contains("/")) "/" else "-"
        val parts = rawPath.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 2) {
            parts.last() to parts.dropLast(1).joinToString(" › ")
        } else {
            rawPath to ""
        }
    }

    val categoryGroups = allQuestions.groupBy { q -> q.category.trim().ifBlank { "General" } }
    val categoryDiagnostics = buildDiagnosticGroups(categoryGroups) { cat -> cat to "" }

    val examQuestionMap = LinkedHashMap<String, MutableList<QuestionSummary>>()
    for (q in allQuestions) {
        val tags = LinkedHashSet<String>()
        val cleanLegacy = q.exam.trim()
        if (cleanLegacy.isNotEmpty() && !cleanLegacy.equals("null", ignoreCase = true)) {
            tags.add(cleanLegacy)
        }
        if (!q.exams.isNullOrBlank() && q.exams != "[]") {
            try {
                val type = object : TypeToken<List<QBookExam>>() {}.type
                val parsed: List<QBookExam>? = SharedGson.normal.fromJson(q.exams, type)
                parsed?.forEach { exObj ->
                    val eName = exObj.exam?.trim().orEmpty()
                    if (eName.isNotEmpty() && !eName.equals("null", ignoreCase = true)) {
                        tags.add(eName)
                    }
                }
            } catch (_: Exception) {}
        }
        tags.forEach { tag ->
            examQuestionMap.getOrPut(tag) { mutableListOf() }.add(q)
        }
    }
    val examDiagnostics = buildDiagnosticGroups(examQuestionMap) { ex -> ex to "Exam Tag" }

    val weakTopicKeys = topicDiagnostics
        .filter { it.visitedCount > 0 && (it.accuracyPct < 65f || it.activeMistakeCount >= 2) }
        .map { it.key }
        .toSet()
    val weakTopicQuestionIds = allQuestions.filter { q ->
        val st = statsByQId[q.id]
        val tKey = q.path.trim().ifBlank { q.category.trim().ifBlank { "General" } }
        val inWeakTopic = tKey in weakTopicKeys && (st == null || !QuestionPoolProcessor.isQuestionMastered(st))
        val isIndividualWeak = st != null && st.attempts > 0 && !QuestionPoolProcessor.isQuestionMastered(st) &&
            ((st.correct.toFloat() / st.attempts.toFloat()) < 0.65f)
        inWeakTopic || isIndividualWeak
    }.map { it.id }

    val pacingAnalysis = computePacingQuadrantAnalysis(
        viewModel = viewModel,
        currentBookId = currentBookId,
        history = history.filter { it.bookId == currentBookId }
    )

    val topMistakenSummaries = mistakenQuestionsSorted.take(40)
    val fullCriticalMap = if (topMistakenSummaries.isNotEmpty()) {
        viewModel.repository.getQuestionsByBookAndIds(currentBookId, topMistakenSummaries.map { it.id })
            .resolveHeaders()
            .associateBy { it.id }
    } else {
        emptyMap()
    }
    val criticalDiagnostics = topMistakenSummaries.mapNotNull { qSum ->
        val st = statsByQId[qSum.id] ?: return@mapNotNull null
        val fullQ = fullCriticalMap[qSum.id] ?: return@mapNotNull null
        val mastered = QuestionPoolProcessor.isQuestionMastered(st)
        CriticalQuestionDiagnostic(
            stat = st,
            question = fullQ,
            urgencyScore = if (mastered) 0f else QuestionPoolProcessor.computeMistakeUrgencyScore(st, now),
            isMastered = mastered,
            isOverdue = st.nextReview in 1L..now
        )
    }

    val insights = mutableListOf<DiagnosticInsightItem>()
    val worstTopic = topicDiagnostics.firstOrNull { it.visitedCount >= 3 && it.accuracyPct < 60f }
    if (worstTopic != null) {
        insights.add(
            DiagnosticInsightItem(
                title = "Bottleneck Topic: ${worstTopic.title}",
                description = "Your accuracy in '${worstTopic.title}' is ${worstTopic.accuracyPct.roundToInt()}% with ${worstTopic.activeMistakeCount} active mistakes.",
                severity = "critical",
                actionLabel = "Drill Topic",
                actionQuestionIds = worstTopic.unmasteredQuestionIds.ifEmpty { worstTopic.allQuestionIds }
            )
        )
    }
    if (overdue > 0) {
        insights.add(
            DiagnosticInsightItem(
                title = "$overdue Questions Overdue for Spaced Review",
                description = "Reviewing overdue questions prevents memory decay and locks concepts into long-term retention.",
                severity = "warning",
                actionLabel = "Review Now",
                actionQuestionIds = dueQuestionIds
            )
        )
    }
    if (pacingAnalysis.rushedMistakeCount >= 3) {
        insights.add(
            DiagnosticInsightItem(
                title = "${pacingAnalysis.rushedMistakeCount} Rushed Mistakes (< 10s)",
                description = "You answered ${pacingAnalysis.rushedMistakeCount} questions wrong in under 10 seconds. Slowing down on stem keywords can recover easy marks.",
                severity = "warning",
                actionLabel = "Retry Rushed",
                actionQuestionIds = pacingAnalysis.rushedMistakeIds
            )
        )
    }
    if (consolidatingQuestionIds.size >= 5) {
        insights.add(
            DiagnosticInsightItem(
                title = "${consolidatingQuestionIds.size} Questions Ready to Graduate",
                description = "These questions have a 1–2 correct streak and need just 1–2 more correct answers to reach Mastered status.",
                severity = "info",
                actionLabel = "Consolidate",
                actionQuestionIds = consolidatingQuestionIds
            )
        )
    }
    if (insights.isEmpty() && visitedCount > 0) {
        insights.add(
            DiagnosticInsightItem(
                title = "Strong Diagnostic Health (${accuracyPct.roundToInt()}% Accuracy)",
                description = "You have mastered $masteredCount of $visitedCount visited questions with zero critical bottlenecks.",
                severity = "positive"
            )
        )
    }

    return AnalysisData(
        readinessScore = readinessScore,
        readinessLabel = readinessLabel,
        coverageScorePct = coveragePct,
        masteryScorePct = masteryPct,
        accuracyScorePct = accuracyPct,
        retentionHealthPct = retentionHealthPct,
        dueToday = dueToday,
        overdue = overdue,
        activeMistakesCount = activeMistakesCount,
        totalMistakenCount = totalMistakenCount,
        consolidatingCount = consolidatingQuestionIds.size,
        weakTopicQuestionsCount = weakTopicQuestionIds.size,
        dueQuestionIds = dueQuestionIds,
        weakTopicQuestionIds = weakTopicQuestionIds,
        criticalQuestionIds = criticalQuestionIds,
        consolidatingQuestionIds = consolidatingQuestionIds,
        insights = insights,
        topicDiagnostics = topicDiagnostics,
        categoryDiagnostics = categoryDiagnostics,
        examDiagnostics = examDiagnostics,
        pacingAnalysis = pacingAnalysis,
        criticalQuestions = criticalDiagnostics
    )
}

private suspend fun computePacingQuadrantAnalysis(
    viewModel: MainViewModel,
    currentBookId: String,
    history: List<QuizHistoryEntity>
): PacingQuadrantAnalysis {
    val recentSessions = history.take(20)
    if (recentSessions.isEmpty()) {
        return PacingQuadrantAnalysis(0, 0f, 0f, 0f, 0, 0, 0, 0, emptyList(), emptyList())
    }

    val gson = SharedGson.normal
    val allSessionQIds = LinkedHashSet<Long>()
    recentSessions.forEach { entry ->
        try {
            val ids = gson.fromJson(entry.questionIds, Array<Double>::class.java)?.map { it.toLong() }.orEmpty()
            allSessionQIds.addAll(ids)
        } catch (_: Exception) {}
    }

    val questionMap = if (allSessionQIds.isNotEmpty()) {
        viewModel.repository.getQuestionsByBookAndIds(currentBookId, allSessionQIds.toList()).associateBy { it.id }
    } else {
        emptyMap()
    }

    var correctTimeTotalMs = 0L
    var correctTimeCount = 0
    var wrongTimeTotalMs = 0L
    var wrongTimeCount = 0
    var skippedTimeTotalMs = 0L
    var skippedTimeCount = 0

    var fastAccurate = 0
    var carefulAccurate = 0
    var rushedMistakes = 0
    var timeSinkTraps = 0
    val rushedIds = LinkedHashSet<Long>()
    val timeSinkIds = LinkedHashSet<Long>()

    for (entry in recentSessions) {
        try {
            val qIds = gson.fromJson(entry.questionIds, Array<Double>::class.java)?.map { it.toLong() } ?: continue
            val rawAnswers = gson.fromJson<List<Any?>>(entry.userAnswers, object : TypeToken<List<Any?>>() {}.type) ?: continue
            val userAnswerSets = rawAnswers.map { item ->
                when (item) {
                    is Number -> setOf(item.toInt())
                    is List<*> -> item.mapNotNull { (it as? Number)?.toInt() }.toSet()
                    else -> emptySet()
                }
            }

            val trimmedMeta = entry.activeQuizData.trim()
            val (timesList, dispOptionsList) = if (trimmedMeta.startsWith("{")) {
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(trimmedMeta, mapType)
                val tList = (map["times"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
                val dList = (map["displayedOptions"] as? List<*>)?.map { row ->
                    (row as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
                }.orEmpty()
                tList to dList
            } else {
                val tList = gson.fromJson<List<Long>>(trimmedMeta, object : TypeToken<List<Long>>() {}.type).orEmpty()
                tList to emptyList()
            }

            for (i in qIds.indices) {
                val qId = qIds[i]
                val q = questionMap[qId] ?: continue
                val timeMs = timesList.getOrNull(i) ?: 0L
                if (timeMs <= 0L) continue
                val timeSec = timeMs.toFloat() / 1000f

                val ansSet = userAnswerSets.getOrNull(i).orEmpty()
                val isSkipped = ansSet.isEmpty() || ansSet.contains(TIMEOUT_SKIPPED_SENTINEL)

                if (isSkipped) {
                    skippedTimeTotalMs += timeMs
                    skippedTimeCount++
                    if (timeSec >= 30f) {
                        timeSinkTraps++
                        timeSinkIds.add(qId)
                    }
                } else {
                    val allCorrect = q.resolveCorrectIndices(includeExtraOptions = true)
                    val baseCorrect = q.resolveCorrectIndices(includeExtraOptions = false)
                    val dispSet = dispOptionsList.getOrNull(i)?.toSet()
                    val expectedCorrect = if (!dispSet.isNullOrEmpty()) {
                        allCorrect.intersect(dispSet).ifEmpty { baseCorrect }
                    } else {
                        if (ansSet.any { it < 0 }) allCorrect else baseCorrect
                    }

                    val isCorrect = ansSet == expectedCorrect
                    if (isCorrect) {
                        correctTimeTotalMs += timeMs
                        correctTimeCount++
                        if (timeSec < 15f) fastAccurate++ else carefulAccurate++
                    } else {
                        wrongTimeTotalMs += timeMs
                        wrongTimeCount++
                        if (timeSec < 10f) {
                            rushedMistakes++
                            rushedIds.add(qId)
                        }
                        if (timeSec >= 30f) {
                            timeSinkTraps++
                            timeSinkIds.add(qId)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val totalTimed = correctTimeCount + wrongTimeCount + skippedTimeCount
    return PacingQuadrantAnalysis(
        totalTimedAnswers = totalTimed,
        avgCorrectTimeSec = if (correctTimeCount > 0) (correctTimeTotalMs.toFloat() / 1000f) / correctTimeCount else 0f,
        avgWrongTimeSec = if (wrongTimeCount > 0) (wrongTimeTotalMs.toFloat() / 1000f) / wrongTimeCount else 0f,
        avgSkippedTimeSec = if (skippedTimeCount > 0) (skippedTimeTotalMs.toFloat() / 1000f) / skippedTimeCount else 0f,
        fastAccurateCount = fastAccurate,
        carefulAccurateCount = carefulAccurate,
        rushedMistakeCount = rushedMistakes,
        timeSinkTrapCount = timeSinkTraps,
        rushedMistakeIds = rushedIds.toList(),
        timeSinkQuestionIds = timeSinkIds.toList()
    )
}
