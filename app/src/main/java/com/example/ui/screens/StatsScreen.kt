package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookEntity
import com.example.data.QuestionStatEntity
import com.example.data.QuestionSummary
import com.example.data.QuizHistoryEntity
import com.example.data.resolvedSourceTag
import com.example.ui.MainViewModel
import com.example.ui.QuestionPoolProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class DailyActivityBucket(
  val dayStartMs: Long, val shortDayLabel: String, val fullDateLabel: String, val questionsStudied: Int,
  val quizzesCount: Int, val correctCount: Int, val totalAnswered: Int, val studyTimeMs: Long
) {
  val accuracy: Float
    get() = if (totalAnswered > 0) (correctCount.toFloat() / totalAnswered.toFloat()) * 100f else 0f
}

private data class SectionStatSummary(
  val title: String, val subtitle: String, val totalQuestions: Int, val visitedQuestions: Int,
  val masteredQuestions: Int, val activeMistakes: Int, val totalAttempts: Int, val totalCorrect: Int
) {
  val coveragePct: Float
    get() = if (totalQuestions > 0) (visitedQuestions.toFloat() / totalQuestions.toFloat()) * 100f else 0f
  val masteryPct: Float
    get() = if (totalQuestions > 0) (masteredQuestions.toFloat() / totalQuestions.toFloat()) * 100f else 0f
  val accuracyPct: Float
    get() = if (totalAttempts > 0) (totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f else 0f
}

private data class ComputedTelemetry(
  val totalQuestionsPool: Int, val uniqueVisited: Int, val masteredCount: Int, val consolidatingCount: Int,
  val activeRemediationCount: Int, val unvisitedCount: Int, val totalAttempts: Int, val totalCorrect: Int,
  val totalWrong: Int, val overallAccuracy: Float, val coveragePct: Float, val masteryPct: Float,
  val cleanFirstTryCount: Int, val avgEaseFactor: Float, val bestAnswerStreak: Int, val activeStudyDaysStreak: Int,
  val studiedTodayCount: Int, val totalQuizzes: Int, val mainQuizCount: Int, val learnQuizCount: Int,
  val searchQuizCount: Int, val totalStudyTimeMs: Long, val avgSecondsPerQuestion: Float, val dueNowCount: Int,
  val interval1To3Days: Int, val interval4To7Days: Int, val interval8To20Days: Int, val intervalMature21Plus: Int,
  val last7DaysActivity: List<DailyActivityBucket>, val filteredHistoryChronological: List<QuizHistoryEntity>,
  val sectionSummaries: List<SectionStatSummary>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
  viewModel: MainViewModel,
  onHome: () -> Unit,
  onNavigateAnalysis: (() -> Unit)? = null,
  onReviewQuiz: (() -> Unit)? = null
) {
  val currentBookId by viewModel.currentBookId.collectAsState()
  val books by viewModel.books.collectAsState()
  val currentBookQuestions by viewModel.allBookQuestionSummaries.collectAsState()
  val currentBookStats by viewModel.allStats.collectAsState()
  val globalStats by viewModel.repository.allStats.collectAsState(initial = emptyList())
  val globalHistory by viewModel.repository.history.collectAsState(initial = emptyList())

  var isGlobalScope by remember { mutableStateOf(false) }
  var trendSourceFilter by remember { mutableStateOf("All") } // "All", "Quiz", "Learn", "Search"
  var sectionSortMode by remember { mutableStateOf("Coverage") } // "Coverage", "Accuracy", "Volume"
  var selectedDayIndex by remember { mutableIntStateOf(6) } // Default to Today (index 6)
  var selectedTrendPointIndex by remember { mutableStateOf<Int?>(null) }

  val currentBookName = remember(books, currentBookId) {
    books.find { it.id == currentBookId }?.name ?: currentBookId.ifBlank { "Current Book" }
  }

  val telemetry by produceState<ComputedTelemetry?>(
    initialValue = null, isGlobalScope, currentBookId, books, currentBookQuestions, currentBookStats, globalStats,
    globalHistory
  ) {
    value = withContext(Dispatchers.Default) {
      computeStatisticsTelemetry(
        isGlobalScope = isGlobalScope, currentBookId = currentBookId,
        books = books, currentBookQuestions = currentBookQuestions,
        currentBookStats = currentBookStats, globalStats = globalStats, globalHistory = globalHistory
      )
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(text = "Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (isGlobalScope) Icons.Default.Layers else Icons.Default.Book,
                contentDescription = null, modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isGlobalScope) "All Books (${books.size})" else currentBookName,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
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
          if (onNavigateAnalysis != null) {
            FilledTonalButton(
              onClick = onNavigateAnalysis, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.padding(end = 8.dp).height(34.dp)
            ) {
              Icon(imageVector = Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = "Analysis", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
          }
        }
      )
    }
  ) { padding ->
    com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
      val data = telemetry
      if (data == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        val sortedSections = remember(data.sectionSummaries, sectionSortMode) {
          when (sectionSortMode) {
            "Accuracy" -> data.sectionSummaries.sortedWith(
              compareByDescending<SectionStatSummary> { it.totalAttempts > 0 }.thenByDescending { it.accuracyPct }
                .thenByDescending { it.coveragePct }
            )
            "Volume" -> data.sectionSummaries.sortedByDescending { it.totalQuestions }
            else -> data.sectionSummaries.sortedWith(
              compareByDescending<SectionStatSummary> { it.coveragePct }.thenByDescending { it.masteryPct }
            )
          }
        }

        LazyColumn(
          modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // 1. Scope Switcher (Current Book vs. All Books Library)
          if (books.size > 1) {
            item(key = "scope_switcher") {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(4.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  ScopeTogglePill(
                    title = "Current Book", selected = !isGlobalScope, onClick ={
                      isGlobalScope = false
                      selectedTrendPointIndex = null
                    },
                    modifier = Modifier.weight(1f)
                  )
                  ScopeTogglePill(
                    title = "All Books (${books.size})",
                    selected = isGlobalScope, onClick ={
                      isGlobalScope = true
                      selectedTrendPointIndex = null
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }

          // 2. Hero Dual-Ring Mastery & Accuracy Telemetry Card
          item(key = "hero_mastery_accuracy_card") {
            HeroMasteryAndAccuracyCard(data = data)
          }

          // 3. 6-Card Key Performance & Pace Telemetry Grid
          item(key = "kpi_telemetry_grid") {
            KpiTelemetryGrid(data = data)
          }

          // 4. 7-Day Study Activity & Accuracy Bar Chart
          item(key = "seven_day_activity_chart") {
            SevenDayActivityCard(
              days = data.last7DaysActivity, selectedIndex = selectedDayIndex.coerceIn(0, (data.last7DaysActivity.size - 1).coerceAtLeast(0)),
              onSelectDay = { selectedDayIndex = it },
              activeStreakDays = data.activeStudyDaysStreak
            )
          }

          // 5. Quiz Accuracy & Score Trend Line Chart
          item(key = "quiz_accuracy_trend_card") {
            QuizAccuracyTrendCard(
              allHistoryChronological = data.filteredHistoryChronological, sourceFilter = trendSourceFilter,
              onSourceFilterChange = {
                trendSourceFilter = it
                selectedTrendPointIndex = null
              },
              selectedPointIndex = selectedTrendPointIndex, onSelectPoint ={ selectedTrendPointIndex = it },
              onInspectQuizInReview = { historyEntry ->
                viewModel.selectResultForReview(historyEntry.historyId)
                onReviewQuiz?.invoke()
              }
            )
          }

          // 6. Spaced Repetition (SRS) Interval Maturity Distribution
          item(key = "srs_interval_distribution_card") {
            SrsIntervalDistributionCard(data = data)
          }

          // 7. Chapter / Topic (or Book) Coverage & Mastery Leaderboard
          if (sortedSections.isNotEmpty()) {
            item(key = "section_leaderboard_header") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = if (isGlobalScope) "Book-by-Book Breakdown" else "Chapter & Topic Telemetry",
                      style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "${sortedSections.size} ${if (isGlobalScope) "books" else "sections"} tracked",
                      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Coverage", "Accuracy", "Volume").forEach { mode ->
                      FilterChip(
                        selected = sectionSortMode == mode, onClick ={ sectionSortMode = mode },
                        label = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(10.dp)
                      )
                    }
                  }
                }
              }
            }

            items(sortedSections, key = { "${it.title}_${it.subtitle}" }) { sec ->
              SectionTelemetryRowCard(section = sec)
            }
          }

          item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(28.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ScopeTogglePill(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = title, style = MaterialTheme.typography.labelLarge, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun HeroMasteryAndAccuracyCard(data: ComputedTelemetry) {
  val animatedAccuracy by animateFloatAsState(
    targetValue = data.overallAccuracy.coerceIn(0f, 100f),
    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
    label = "heroAccuracy"
  )
  val animatedMastery by animateFloatAsState(
    targetValue = data.masteryPct.coerceIn(0f, 100f),
    animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
    label = "heroMastery"
  )

  val masteredColor = Color(0xFF16A34A)
  val consolidatingColor = Color(0xFF2563EB)
  val remediationColor = Color(0xFFD97706)
  val unvisitedColor = MaterialTheme.colorScheme.surfaceVariant
  val primaryColor = MaterialTheme.colorScheme.primary

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Mastery & Accuracy Overview", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${data.uniqueVisited} of ${data.totalQuestionsPool} questions visited (${String.format(Locale.US, "%.1f", data.coveragePct)}%)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Surface(
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
          Text(
            text = "${data.masteredCount} Mastered",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
      ) {
        // Dual Concentric Ring Gauge (Outer = Accuracy %, Inner = Mastery %)
        Box(
          modifier = Modifier.size(144.dp),
          contentAlignment = Alignment.Center
        ) {
          val trackColor = MaterialTheme.colorScheme.surfaceVariant
          Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val outerStroke = 13.dp.toPx()
            val innerStroke = 10.dp.toPx()
            val gap = 6.dp.toPx()

            // Outer Ring Track (Accuracy)
            drawArc(
              color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
            // Outer Ring Progress (Accuracy)
            drawArc(brush = Brush.sweepGradient( listOf(primaryColor.copy(alpha = 0.65f), primaryColor)),
              startAngle = -90f, sweepAngle =(animatedAccuracy / 100f) * 360f,
              useCenter = false, style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )

            // Inner Ring Track (Mastery)
            val innerInset = outerStroke + gap
            val innerSize = Size(
              width = (size.width - innerInset * 2).coerceAtLeast(10f),
              height = (size.height - innerInset * 2).coerceAtLeast(10f)
            )
            val innerTopLeft = Offset(innerInset, innerInset)
            drawArc(
              color = trackColor.copy(alpha = 0.65f),
              startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = innerTopLeft,
              size = innerSize, style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )
            drawArc(
              color = masteredColor, startAngle = -90f, sweepAngle =(animatedMastery / 100f) * 360f,
              useCenter = false, topLeft = innerTopLeft, size = innerSize, style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "${animatedAccuracy.roundToInt()}%",
              style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Accuracy", style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "${animatedMastery.roundToInt()}% Mastered",
              style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
              color = masteredColor, fontSize = 10.sp
            )
          }
        }

        // 4-Stage Cognitive Breakdown Legend
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CognitiveStageRow(
            label = "Mastered (Streak 3+)",
            count = data.masteredCount, total = data.totalQuestionsPool, dotColor = masteredColor
          )
          CognitiveStageRow(
            label = "Consolidating", count = data.consolidatingCount,
            total = data.totalQuestionsPool, dotColor = consolidatingColor
          )
          CognitiveStageRow(
            label = "Active Remediation", count = data.activeRemediationCount,
            total = data.totalQuestionsPool, dotColor = remediationColor
          )
          CognitiveStageRow(
            label = "Unvisited / New",
            count = data.unvisitedCount, total = data.totalQuestionsPool, dotColor = MaterialTheme.colorScheme.outline
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Stacked 4-State Book Progress Bar
      val safeTotal = data.totalQuestionsPool.coerceAtLeast(1).toFloat()
      val masteredFrac = (data.masteredCount / safeTotal).coerceIn(0f, 1f)
      val consolidatingFrac = (data.consolidatingCount / safeTotal).coerceIn(0f, 1f)
      val remediationFrac = (data.activeRemediationCount / safeTotal).coerceIn(0f, 1f)

      Canvas(
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
      ) {
        drawRect(color = unvisitedColor)
        var currentX = 0f
        val w1 = size.width * masteredFrac
        if (w1 > 0f) {
          drawRect(color = masteredColor, topLeft = Offset(currentX, 0f), size = Size(w1, size.height))
          currentX += w1
        }
        val w2 = size.width * consolidatingFrac
        if (w2 > 0f) {
          drawRect(color = consolidatingColor, topLeft = Offset(currentX, 0f), size = Size(w2, size.height))
          currentX += w2
        }
        val w3 = size.width * remediationFrac
        if (w3 > 0f) {
          drawRect(color = remediationColor, topLeft = Offset(currentX, 0f), size = Size(w3, size.height))
        }
      }
    }
  }
}

@Composable
private fun CognitiveStageRow(
  label: String,
  count: Int,
  total: Int,
  dotColor: Color
) {
  val pct = if (total > 0) ((count.toFloat() / total.toFloat()) * 100f).roundToInt() else 0
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
    ) {
      Box(modifier = Modifier .size(9.dp) .background(dotColor, CircleShape))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = label, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = "$count ($pct%)",
      style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun KpiTelemetryGrid(data: ComputedTelemetry) {
  val cleanRate = if (data.uniqueVisited > 0) {
    ((data.cleanFirstTryCount.toFloat() / data.uniqueVisited.toFloat()) * 100f).roundToInt()
  } else 0

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      TelemetryMiniCard(
        title = "Total Answers", value = "${data.totalAttempts}",
        subtitle = "${data.totalCorrect} ✓  •  ${data.totalWrong} ✗",
        icon = Icons.Default.CheckCircle, accentColor = Color(0xFF16A34A),
        modifier = Modifier.weight(1f)
      )
      TelemetryMiniCard(
        title = "Study Time & Pace", value = formatDurationCompact(data.totalStudyTimeMs),
        subtitle = if (data.avgSecondsPerQuestion > 0f) {
          "Avg ${String.format(Locale.US, "%.1f", data.avgSecondsPerQuestion)}s / question"
        } else {
          "Timed across quizzes"
        },
        icon = Icons.Default.Speed, accentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      TelemetryMiniCard(
        title = "Study Streak", value = "${data.activeStudyDaysStreak} ${if (data.activeStudyDaysStreak == 1) "Day" else "Days"}",
        subtitle = "Best streak: ${data.bestAnswerStreak} correct  •  Today: ${data.studiedTodayCount}",
        icon = Icons.Default.LocalFireDepartment, accentColor = Color(0xFFEA580C),
        modifier = Modifier.weight(1f)
      )
      TelemetryMiniCard(
        title = "Quizzes Completed", value = "${data.totalQuizzes}",
        subtitle = "Quiz ${data.mainQuizCount} • Learn ${data.learnQuizCount} • Search ${data.searchQuizCount}",
        icon = Icons.Default.History, accentColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      TelemetryMiniCard(
        title = "Zero-Mistake Rate", value = "$cleanRate%", subtitle = "${data.cleanFirstTryCount} of ${data.uniqueVisited} visited Qs never missed",
        icon = Icons.Default.EmojiEvents, accentColor = Color(0xFF0D9488),
        modifier = Modifier.weight(1f)
      )
      TelemetryMiniCard(
        title = "Memory Ease & Due", value = String.format(Locale.US, "%.2f", data.avgEaseFactor),
        subtitle = "${data.dueNowCount} due now  •  ${data.intervalMature21Plus} mature (21d+)",
        icon = Icons.Default.Psychology, accentColor = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun TelemetryMiniCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color, modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier, shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier.size(32.dp).background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Text(
          text = title, style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
          maxLines = 1, overflow = TextOverflow.Ellipsis
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = value, style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun SevenDayActivityCard(
  days: List<DailyActivityBucket>,
  selectedIndex: Int,
  onSelectDay: (Int) -> Unit,
  activeStreakDays: Int
) {
  if (days.isEmpty()) return
  val selectedDay = days.getOrNull(selectedIndex) ?: days.last()
  val maxQuestions = days.maxOfOrNull { it.questionsStudied }?.coerceAtLeast(10) ?: 10

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(text = "Last 7 Days Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            text = "Tap any day bar to inspect volume & accuracy", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Surface(
          shape = RoundedCornerShape(50),
          color = Color(0xFFEA580C).copy(alpha = 0.14f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFEA580C),
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${activeStreakDays}d streak",
              style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 7-Day Interactive Bar Chart
      Row(
        modifier = Modifier.fillMaxWidth().height(136.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
      ) {
        days.forEachIndexed { idx, bucket ->
          val isSelected = idx == selectedIndex
          val heightFraction = (bucket.questionsStudied.toFloat() / maxQuestions.toFloat()).coerceIn(0.06f, 1f)
          val barColor = when {
            bucket.questionsStudied == 0 -> MaterialTheme.colorScheme.surfaceVariant
            bucket.accuracy >= 75f -> Color(0xFF16A34A)
            bucket.accuracy >= 50f -> Color(0xFFD97706)
            else -> MaterialTheme.colorScheme.primary
          }

          Column(
            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
              .clickable { onSelectDay(idx) }.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
          ) {
            Text(
              text = if (bucket.questionsStudied > 0) "${bucket.questionsStudied}" else "",
              style = MaterialTheme.typography.labelSmall, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              contentAlignment = Alignment.BottomCenter
            ) {
              Box(
                modifier = Modifier.fillMaxWidth(0.68f).fillMaxHeight(heightFraction)
                  .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                  .background(
                    if (isSelected && bucket.questionsStudied == 0)
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else barColor
                  )
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = bucket.shortDayLabel, style = MaterialTheme.typography.labelSmall, fontWeight = if(isSelected) FontWeight.ExtraBold else FontWeight.Medium,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      Spacer(modifier = Modifier.height(10.dp))

      // Selected Day Summary Strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = selectedDay.fullDateLabel, style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = buildString {
            append("${selectedDay.questionsStudied} Qs")
            if (selectedDay.quizzesCount > 0) {
              append(" • ${selectedDay.quizzesCount} ${if (selectedDay.quizzesCount == 1) "quiz" else "quizzes"}")
              append(" • ${selectedDay.accuracy.roundToInt()}% acc")
            }
            if (selectedDay.studyTimeMs > 0L) {
              append(" • ${formatDurationCompact(selectedDay.studyTimeMs)}")
            }
          },
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun QuizAccuracyTrendCard(
  allHistoryChronological: List<QuizHistoryEntity>,
  sourceFilter: String,
  onSourceFilterChange: (String) -> Unit,
  selectedPointIndex: Int?,
  onSelectPoint: (Int?) -> Unit,
  onInspectQuizInReview: (QuizHistoryEntity) -> Unit
) {
  val filtered = remember(allHistoryChronological, sourceFilter) {
    if (sourceFilter == "All") {
      allHistoryChronological
    } else {
      allHistoryChronological.filter { (it.resolvedSourceTag() ?: "Quiz") == sourceFilter }
    }.takeLast(15)
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Quiz Accuracy Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            text = if (filtered.isEmpty()) "Complete quizzes to unlock trend chart"
            else "Last ${filtered.size} completed sessions",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (filtered.size >= 2) {
          val half = (filtered.size / 2).coerceAtLeast(1)
          val earlierAvg = filtered.take(half).map { it.accuracy }.average().toFloat()
          val recentAvg = filtered.takeLast(filtered.size - half).map { it.accuracy }.average().toFloat()
          val delta = recentAvg - earlierAvg
          val isPositive = delta >= 0f
          val badgeColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)

          Surface(
            shape = RoundedCornerShape(50),
            color = badgeColor.copy(alpha = 0.14f)
          ) {
            Text(
              text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", delta)}%",
              style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
              color = badgeColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Source Filter Pills
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf("All", "Quiz", "Learn", "Search").forEach { tag ->
          FilterChip(
            selected = sourceFilter == tag, onClick ={ onSourceFilterChange(tag) },
            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(30.dp),
            shape = RoundedCornerShape(10.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(110.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No completed $sourceFilter sessions yet.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        val primaryColor = MaterialTheme.colorScheme.primary
        val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        val targetLineColor = Color(0xFF16A34A).copy(alpha = 0.45f)
        val activeIdx = (selectedPointIndex ?: (filtered.size - 1)).coerceIn(0, filtered.lastIndex)
        val selectedEntry = filtered[activeIdx]

        Canvas(
          modifier = Modifier.fillMaxWidth().height(150.dp).pointerInput(filtered) {
              detectTapGestures { tapOffset ->
                if (filtered.size == 1) {
                  onSelectPoint(0)
                } else {
                  val padH = 16.dp.toPx()
                  val usableW = (size.width - padH * 2).coerceAtLeast(1f)
                  val stepX = usableW / (filtered.size - 1).toFloat()
                  val rawIdx = ((tapOffset.x - padH) / stepX).roundToInt()
                  onSelectPoint(rawIdx.coerceIn(0, filtered.lastIndex))
                }
              }
            }
        ) {
          val padH = 16.dp.toPx()
          val padV = 14.dp.toPx()
          val chartW = (size.width - padH * 2).coerceAtLeast(1f)
          val chartH = (size.height - padV * 2).coerceAtLeast(1f)

          // Horizontal reference lines (0%, 50%, 75% target, 100%)
          listOf(0f, 0.5f, 0.75f, 1f).forEach { frac ->
            val y = padV + chartH * (1f - frac)
            drawLine(
              color = if (frac == 0.75f) targetLineColor else gridLineColor,
              start = Offset(padH, y),
              end = Offset(padH + chartW, y),
              strokeWidth = if (frac == 0.75f) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (frac == 0.75f) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
            )
          }

          val points = filtered.mapIndexed { i, item ->
            val x = if (filtered.size == 1) {
              padH + chartW * 0.5f
            } else {
              padH + (i.toFloat() / (filtered.size - 1).toFloat()) * chartW
            }
            val y = padV + chartH * (1f - (item.accuracy.coerceIn(0f, 100f) / 100f))
            Offset(x, y)
          }

          if (points.size >= 2) {
            val linePath = Path().apply {
              moveTo(points.first().x, points.first().y)
              for (k in 1 until points.size) {
                lineTo(points[k].x, points[k].y)
              }
            }
            val fillPath = Path().apply {
              addPath(linePath)
              lineTo(points.last().x, padV + chartH)
              lineTo(points.first().x, padV + chartH)
              close()
            }

            drawPath(
              path = fillPath, brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent),
                startY = padV, endY = padV + chartH
              )
            )
            drawPath(
              path = linePath, color = primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
          }

          points.forEachIndexed { idx, pt ->
            val isSel = idx == activeIdx
            if (isSel) {
              drawCircle(color = primaryColor.copy(alpha = 0.22f), radius = 9.dp.toPx(), center = pt)
            }
            drawCircle(
              color = if (isSel) primaryColor else Color.White,
              radius = if (isSel) 5.dp.toPx() else 4.dp.toPx(),
              center = pt
            )
            drawCircle(
              color = primaryColor, radius = if(isSel) 5.dp.toPx() else 4.dp.toPx(),
              center = pt, style = Stroke(width = 2.dp.toPx())
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected Quiz Session Detail Pill
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = "${selectedEntry.resolvedSourceTag() ?: "Quiz"} • ${selectedEntry.quizLabel}",
                  style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${selectedEntry.accuracy.roundToInt()}% (${selectedEntry.correct}/${selectedEntry.total})",
                  style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              Text(
                text = selectedEntry.date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            TextButton(
              onClick = { onInspectQuizInReview(selectedEntry) },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
              Text("Review", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SrsIntervalDistributionCard(data: ComputedTelemetry) {
  val totalStudied = data.uniqueVisited.coerceAtLeast(1)
  val buckets = listOf(
    Triple("Due / Overdue Now", data.dueNowCount, Color(0xFFDC2626)),
    Triple("Short-Term (1–3 Days)", data.interval1To3Days, Color(0xFFD97706)),
    Triple("Medium-Term (4–7 Days)", data.interval4To7Days, Color(0xFF2563EB)),
    Triple("Consolidating (8–20 Days)", data.interval8To20Days, Color(0xFF7C3AED)),
    Triple("Long-Term Mature (21+ Days)", data.intervalMature21Plus, Color(0xFF16A34A))
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Spaced Repetition Memory Maturity", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Distribution of your ${data.uniqueVisited} studied questions across SRS intervals",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(14.dp))

      buckets.forEach { (label, count, color) ->
        val frac = if (data.uniqueVisited > 0) (count.toFloat() / totalStudied.toFloat()).coerceIn(0f, 1f) else 0f
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
              text = "$count Qs (${(frac * 100f).roundToInt()}%)",
              style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun SectionTelemetryRowCard(section: SectionStatSummary) {
  val accColor = when {
    section.totalAttempts == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    section.accuracyPct >= 75f -> Color(0xFF16A34A)
    section.accuracyPct >= 50f -> Color(0xFFD97706)
    else -> MaterialTheme.colorScheme.error
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = section.title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${section.visitedQuestions}/${section.totalQuestions} visited • ${section.masteredQuestions} mastered",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = if (section.totalAttempts > 0) "${section.accuracyPct.roundToInt()}% Acc" else "Unvisited",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accColor
          )
          Text(
            text = "${section.coveragePct.roundToInt()}% covered",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      LinearProgressIndicator(
        progress = { (section.coveragePct / 100f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
      )
    }
  }
}

internal fun formatDurationCompact(durationMs: Long): String {
  if (durationMs <= 0L) return "0m"
  val totalSeconds = durationMs / 1000L
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L
  return when {
    hours > 0L -> "${hours}h ${minutes}m"
    minutes > 0L -> "${minutes}m ${seconds}s"
    else -> "${seconds}s"
  }
}

private fun computeStatisticsTelemetry(
  isGlobalScope: Boolean,
  currentBookId: String,
  books: List<BookEntity>,
  currentBookQuestions: List<QuestionSummary>,
  currentBookStats: List<QuestionStatEntity>,
  globalStats: List<QuestionStatEntity>,
  globalHistory: List<QuizHistoryEntity>
): ComputedTelemetry {
  val now = System.currentTimeMillis()
  val dayMs = 86_400_000L

  val statsPool = if (isGlobalScope) {
    globalStats
  } else {
    currentBookStats.filter { it.bookId == currentBookId }
  }
  val attemptedStats = statsPool.filter { it.attempts > 0 }

  val totalQuestionsPool = if (isGlobalScope) {
    books.sumOf { it.totalQuestions }.coerceAtLeast(attemptedStats.size)
  } else {
    currentBookQuestions.size.coerceAtLeast(attemptedStats.size)
  }

  val uniqueVisited = attemptedStats.size
  val masteredCount = attemptedStats.count { QuestionPoolProcessor.isQuestionMastered(it) }
  val activeRemediationCount = attemptedStats.count { it.wrong > 0 && !QuestionPoolProcessor.isQuestionMastered(it) }
  val consolidatingCount = (uniqueVisited - masteredCount - activeRemediationCount).coerceAtLeast(0)
  val unvisitedCount = (totalQuestionsPool - uniqueVisited).coerceAtLeast(0)

  val totalAttempts = attemptedStats.sumOf { it.attempts }
  val totalCorrect = attemptedStats.sumOf { it.correct }
  val totalWrong = attemptedStats.sumOf { it.wrong }
  val overallAccuracy = if (totalAttempts > 0) (totalCorrect.toFloat() / totalAttempts.toFloat()) * 100f else 0f
  val coveragePct = if (totalQuestionsPool > 0) (uniqueVisited.toFloat() / totalQuestionsPool.toFloat()) * 100f else 0f
  val masteryPct = if (totalQuestionsPool > 0) (masteredCount.toFloat() / totalQuestionsPool.toFloat()) * 100f else 0f

  val cleanFirstTryCount = attemptedStats.count { it.wrong == 0 }
  val avgEaseFactor = if (attemptedStats.isNotEmpty()) {
    attemptedStats.map { it.easeFactor }.average().toFloat()
  } else 2.5f
  val bestAnswerStreak = attemptedStats.maxOfOrNull { it.streak } ?: 0

  // History filtered to valid completed Quiz / Learn / Search entries
  val validHistory = globalHistory.filter { entry ->
    val matchesBook = isGlobalScope || entry.bookId == currentBookId
    matchesBook && entry.resolvedSourceTag() in setOf("Quiz", "Learn", "Search")
  }
  val chronologicalHistory = validHistory.sortedBy { it.historyId }

  val mainQuizCount = validHistory.count { (it.resolvedSourceTag() ?: "Quiz") == "Quiz" }
  val learnQuizCount = validHistory.count { it.resolvedSourceTag() == "Learn" }
  val searchQuizCount = validHistory.count { it.resolvedSourceTag() == "Search" }
  val totalStudyTimeMs = validHistory.sumOf { it.timeUsed.coerceAtLeast(0L) }
  val totalHistoryQuestionsAnswered = validHistory.sumOf { (it.correct + it.wrong).coerceAtLeast(0) }
  val avgSecondsPerQuestion = if (totalHistoryQuestionsAnswered > 0 && totalStudyTimeMs > 0L) {
    (totalStudyTimeMs.toFloat() / 1000f) / totalHistoryQuestionsAnswered.toFloat()
  } else 0f

  // SRS Interval Maturity Buckets
  val dueNowCount = attemptedStats.count { it.nextReview in 1L..now }
  val interval1To3Days = attemptedStats.count { it.nextReview > now && it.interval in 1..3 }
  val interval4To7Days = attemptedStats.count { it.nextReview > now && it.interval in 4..7 }
  val interval8To20Days = attemptedStats.count { it.nextReview > now && it.interval in 8..20 }
  val intervalMature21Plus = attemptedStats.count { it.interval >= 21 }

  // 7-Day Activity & Study Day Streak calculation
  val cal = Calendar.getInstance()
  cal.timeInMillis = now
  cal.set(Calendar.HOUR_OF_DAY, 0)
  cal.set(Calendar.MINUTE, 0)
  cal.set(Calendar.SECOND, 0)
  cal.set(Calendar.MILLISECOND, 0)
  val todayStartMs = cal.timeInMillis

  val shortDayFmt = SimpleDateFormat("EEE", Locale.US)
  val fullDateFmt = SimpleDateFormat("EEE, dd MMM", Locale.US)

  val last7Days = (6 downTo 0).map { daysAgo ->
    val startMs = todayStartMs - daysAgo * dayMs
    val endMs = startMs + dayMs
    val dayQuizzes = validHistory.filter { it.historyId in startMs until endMs }
    val dayStatsTouched = attemptedStats.filter { it.lastSeen in startMs until endMs }

    val quizTotalAnswered = dayQuizzes.sumOf { it.correct + it.wrong }
    val quizCorrect = dayQuizzes.sumOf { it.correct }
    val questionsCount = maxOf(quizTotalAnswered, dayStatsTouched.size)
    val correctCount = if (quizTotalAnswered > 0) quizCorrect else dayStatsTouched.count { it.streak > 0 }
    val totalAnswered = if (quizTotalAnswered > 0) quizTotalAnswered else dayStatsTouched.size

    DailyActivityBucket(
      dayStartMs = startMs, shortDayLabel = if(daysAgo == 0) "Today" else shortDayFmt.format(Date(startMs)),
      fullDateLabel = if (daysAgo == 0) "Today (${fullDateFmt.format(Date(startMs))})" else fullDateFmt.format(Date(startMs)),
      questionsStudied = questionsCount, quizzesCount = dayQuizzes.size,
      correctCount = correctCount, totalAnswered = totalAnswered,
      studyTimeMs = dayQuizzes.sumOf { it.timeUsed.coerceAtLeast(0L) }
    )
  }

  val studiedTodayCount = last7Days.lastOrNull()?.questionsStudied ?: 0

  // Compute consecutive active study day streak
  val activeDayIndices = HashSet<Long>()
  attemptedStats.forEach { st ->
    if (st.lastSeen > 0L) {
      val dIdx = (todayStartMs - st.lastSeen).let { diff ->
        if (diff <= 0L) 0L else (diff + dayMs - 1L) / dayMs
      }
      // Exact midnight bucket index:
      val bucketIdx = ((st.lastSeen - todayStartMs).toDouble() / dayMs.toDouble()).let {
        kotlin.math.floor(it).toLong()
      }
      activeDayIndices.add(bucketIdx)
    }
  }
  validHistory.forEach { h ->
    val bucketIdx = kotlin.math.floor((h.historyId - todayStartMs).toDouble() / dayMs.toDouble()).toLong()
    activeDayIndices.add(bucketIdx)
  }

  var streakCursor = if (0L in activeDayIndices) 0L else if (-1L in activeDayIndices) -1L else Long.MIN_VALUE
  var activeStudyDaysStreak = 0
  while (streakCursor != Long.MIN_VALUE && streakCursor in activeDayIndices) {
    activeStudyDaysStreak++
    streakCursor -= 1L
  }

  // Chapter / Topic (or Book) breakdown
  val sectionSummaries = if (isGlobalScope) {
    val statsByBook = attemptedStats.groupBy { it.bookId }
    books.map { book ->
      val bStats = statsByBook[book.id].orEmpty()
      SectionStatSummary(
        title = book.name, subtitle = book.id, totalQuestions = book.totalQuestions.coerceAtLeast(bStats.size),
        visitedQuestions = bStats.size, masteredQuestions = bStats.count{ QuestionPoolProcessor.isQuestionMastered(it) },
        activeMistakes = bStats.count { it.wrong > 0 && !QuestionPoolProcessor.isQuestionMastered(it) },
        totalAttempts = bStats.sumOf { it.attempts },
        totalCorrect = bStats.sumOf { it.correct }
      )
    }
  } else {
    val statsByQId = attemptedStats.associateBy { it.questionId }
    val groupedBySection = currentBookQuestions.groupBy { q ->
      val rawPath = q.path.trim()
      if (rawPath.isNotBlank()) {
        val delimiter = if (rawPath.contains(">")) ">" else if (rawPath.contains("/")) "/" else "-"
        val parts = rawPath.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }
        when {
          parts.size >= 2 -> "${parts[0]} › ${parts[1]}"
          parts.size == 1 -> parts[0]
          else -> q.category.ifBlank { "General" }
        }
      } else {
        q.category.ifBlank { "General" }
      }
    }
    groupedBySection.map { (secTitle, qs) ->
      val secStats = qs.mapNotNull { statsByQId[it.id] }
      SectionStatSummary(
        title = secTitle, subtitle = "${qs.size} Qs",
        totalQuestions = qs.size, visitedQuestions = secStats.size, masteredQuestions = secStats.count{ QuestionPoolProcessor.isQuestionMastered(it) },
        activeMistakes = secStats.count { it.wrong > 0 && !QuestionPoolProcessor.isQuestionMastered(it) },
        totalAttempts = secStats.sumOf { it.attempts },
        totalCorrect = secStats.sumOf { it.correct }
      )
    }
  }

  return ComputedTelemetry(
    totalQuestionsPool = totalQuestionsPool, uniqueVisited = uniqueVisited,
    masteredCount = masteredCount, consolidatingCount = consolidatingCount,
    activeRemediationCount = activeRemediationCount, unvisitedCount = unvisitedCount,
    totalAttempts = totalAttempts, totalCorrect = totalCorrect,
    totalWrong = totalWrong, overallAccuracy = overallAccuracy, coveragePct = coveragePct, masteryPct = masteryPct,
    cleanFirstTryCount = cleanFirstTryCount, avgEaseFactor = avgEaseFactor,
    bestAnswerStreak = bestAnswerStreak, activeStudyDaysStreak = activeStudyDaysStreak,
    studiedTodayCount = studiedTodayCount, totalQuizzes = validHistory.size,
    mainQuizCount = mainQuizCount, learnQuizCount = learnQuizCount,
    searchQuizCount = searchQuizCount, totalStudyTimeMs = totalStudyTimeMs,
    avgSecondsPerQuestion = avgSecondsPerQuestion, dueNowCount = dueNowCount,
    interval1To3Days = interval1To3Days, interval4To7Days = interval4To7Days,
    interval8To20Days = interval8To20Days, intervalMature21Plus = intervalMature21Plus,
    last7DaysActivity = last7Days, filteredHistoryChronological = chronologicalHistory,
    sectionSummaries = sectionSummaries
  )
}
