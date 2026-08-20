package com.example.ui.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation


import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.window.*
import com.example.ui.MainViewModel
import java.io.File
import androidx.compose.ui.platform.*

import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.layout.*
import androidx.compose.foundation.gestures.*
import coil.compose.*
import com.example.ui.components.RichText
import com.example.ui.components.RichText



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(viewModel: MainViewModel, onFinish: () -> Unit, onCancel: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val timerSoundEnabled by viewModel.timerSoundEnabledState.collectAsState()
    val clickSoundEnabled by viewModel.clickSoundEnabledState.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabledState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val questions by viewModel.questions.collectAsState()
    var currentIndex by remember { mutableStateOf(viewModel.activeCurrentIndex) }
    var autoNextTrigger by remember { mutableStateOf(0L) }
    var selectedAnswers by remember { mutableStateOf(viewModel.activeSelectedAnswers) }
    val qTimes = remember { mutableStateOf(mutableMapOf<Int, Long>()) }
    var lockedAnswers by remember { mutableStateOf(viewModel.activeLockedAnswers) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDetailedExplanation by remember { mutableStateOf(false) }
    var visitedQuestions by remember { mutableStateOf(setOf(currentIndex)) }
    
    var showExplanationSheet by remember { mutableStateOf(false) }
    var explanationSheetTab by remember { mutableStateOf(0) } // 0 = Detailed, 1 = AI
    var aiExplanationStateMap by remember { mutableStateOf(mapOf<Int, String>()) }
    var aiExplanationTextMap by remember { mutableStateOf(mapOf<Int, String>()) }
    var aiExplanationStatus by remember { mutableStateOf("Initializing AI...") }
    
    val aiExplanationState = aiExplanationStateMap[currentIndex] ?: "Idle"
    val aiExplanationText = aiExplanationTextMap[currentIndex] ?: ""
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentIndex) {
        if (!visitedQuestions.contains(currentIndex)) {
            visitedQuestions = visitedQuestions + currentIndex
        }
    }

    val bookmarks by viewModel.bookmarks.collectAsState()

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No questions available.")
        }
        return
    }
    
    if (currentIndex >= questions.size) {
        currentIndex = questions.size - 1
        return
    }

    val currentQuestion = questions[currentIndex]
    val isPracticeMode = viewModel.settingsManager.quizMode == "practice" || viewModel.currentPracticeMistakesParentId != null
    val currentUseDistractors = if (viewModel.isLearnMode) viewModel.settingsManager.learnUseDistractors else viewModel.settingsManager.useDistractors
    val currentShuffleOptions = if (viewModel.isLearnMode) viewModel.settingsManager.learnShuffleOptions else viewModel.settingsManager.shuffleOptions
    val currentQuestionFlow = if (viewModel.isLearnMode) viewModel.settingsManager.learnQuestionFlow else viewModel.settingsManager.questionFlow
    var timeRemainingMs by remember { 
        mutableStateOf(if (viewModel.activeTimeRemainingMs >= 0) viewModel.activeTimeRemainingMs else {
            val examSecs = viewModel.currentExamSettings?.let {
                when (it.timerPreset) {
                    "Off" -> 0
                    "15m" -> 15 * 60
                    "30m" -> 30 * 60
                    "60m" -> 60 * 60
                    "Custom" -> it.customTimerMinutes * 60
                    "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36)
                    else -> 0
                }
            }
            val secs = examSecs ?: (viewModel.settingsManager.timerMinutes * 60)
            if (!isPracticeMode && secs > 0) secs * 1000L else 0L
        }) 
    }
    val questionTimerTarget = viewModel.settingsManager.questionTimerSeconds * 1000L
    var questionTimeRemainingMs by remember(currentIndex) { 
        mutableStateOf(if (questionTimerTarget > 0 && lockedAnswers[currentIndex] != true) questionTimerTarget else 0L)
    }

    val isSubmitted = remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        showCancelDialog = true
    }



    DisposableEffect(Unit) {
        onDispose {
            if (!isSubmitted.value) {
                viewModel.saveActiveQuizState(currentIndex, selectedAnswers, lockedAnswers, timeRemainingMs)
            }
        }
    }
    
    val submitQuiz = {
        if (hapticEnabled) viewModel.soundHapticManager.hapticQuizComplete()
        isSubmitted.value = true
        val correctMark = viewModel.currentExamSettings?.customCorrectMarks ?: viewModel.settingsManager.correctMark
        val wrongMark = viewModel.currentExamSettings?.customWrongMarks ?: viewModel.settingsManager.wrongMark
        var rawScore = 0f
        var correct = 0
        var wrong = 0
        var skipped = 0
        val gson = com.example.data.SharedGson.normal
        val typeOptionList = object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>() {}.type
        val typeStringList = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        
        questions.forEachIndexed { idx, q ->
            val ans = selectedAnswers[idx]
            if (ans.isNullOrEmpty() || ans.contains(-1)) skipped++
            else {
                val correctIds = gson.fromJson<List<String>>(q.advancedCorrectIds ?: "[]", typeStringList) ?: emptyList()
                val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(q.advancedOptions ?: "[]", typeOptionList) ?: emptyList()
                
                val correctIndices = if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                    correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                } else {
                    setOf(q.correctIndex)
                }

                if (ans == correctIndices) { correct++; rawScore += correctMark }
                else { wrong++; rawScore -= wrongMark }
            }
        }
        viewModel.saveQuizResult(
            bookId = currentQuestion.bookId,
            quizLabel = if (isPracticeMode) "Practice Mode" else "Exam Mode",
            score = rawScore,
            total = questions.size,
            correct = correct,
            wrong = wrong,
            skipped = skipped,
            timeUsed = {
            val examMins = viewModel.currentExamSettings?.let {
                when (it.timerPreset) {
                    "Off" -> 0
                    "15m" -> 15
                    "30m" -> 30
                    "60m" -> 60
                    "Custom" -> it.customTimerMinutes
                    "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36) / 60
                    else -> 0
                }
            }
            val mins = examMins ?: viewModel.settingsManager.timerMinutes
            if (!isPracticeMode && mins > 0) (mins * 60 * 1000L - timeRemainingMs) else timeRemainingMs
        }(),
            questionIds = questions.map { it.id },
            userAnswers = questions.indices.map { selectedAnswers[it] },
            questionTimes = questions.indices.map { qTimes.value[it] ?: 0L }
        )
        viewModel.clearActiveQuizState()
        onFinish()
    }
    
    LaunchedEffect(isPracticeMode, timeRemainingMs) {
        if (!isPracticeMode) {
            val secs = viewModel.currentExamSettings?.let {
                when (it.timerPreset) {
                    "Off" -> 0
                    "15m" -> 15 * 60
                    "30m" -> 30 * 60
                    "60m" -> 60 * 60
                    "Custom" -> it.customTimerMinutes * 60
                    "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36)
                    else -> 0
                }
            } ?: (viewModel.settingsManager.timerMinutes * 60)
            if (secs > 0) {
                if (timeRemainingMs > 0) {
                    kotlinx.coroutines.delay(1000)
                    timeRemainingMs -= 1000
                    qTimes.value = qTimes.value.toMutableMap().apply { put(currentIndex, getOrDefault(currentIndex, 0L) + 1000L) }
                    
                    if (timeRemainingMs in 1000L..5000L) {
                        if (hapticEnabled) viewModel.soundHapticManager.hapticWarning()
                        if (timerSoundEnabled) viewModel.soundHapticManager.playExamTick()
                    }
                    if (timeRemainingMs <= 0L) {
                        if (hapticEnabled) viewModel.soundHapticManager.hapticWarning()
                        if (timerSoundEnabled) viewModel.soundHapticManager.playExamTimeOut()
                        submitQuiz()
                    }
                }
            } else {
                kotlinx.coroutines.delay(1000)
                timeRemainingMs += 1000
                qTimes.value = qTimes.value.toMutableMap().apply { put(currentIndex, getOrDefault(currentIndex, 0L) + 1000L) }
            }
        } else {
            kotlinx.coroutines.delay(1000)
            timeRemainingMs += 1000
            qTimes.value = qTimes.value.toMutableMap().apply { put(currentIndex, getOrDefault(currentIndex, 0L) + 1000L) }
        }
    }
    
    LaunchedEffect(questionTimeRemainingMs, lockedAnswers[currentIndex]) {
        if (questionTimeRemainingMs > 0 && lockedAnswers[currentIndex] != true) {
            kotlinx.coroutines.delay(1000)
            questionTimeRemainingMs -= 1000
            
            if (questionTimeRemainingMs in 1000L..3000L) {
                if (hapticEnabled) {
                    viewModel.soundHapticManager.hapticNavigation()
                }
                val secsSound = viewModel.currentExamSettings?.let {
                when (it.timerPreset) {
                    "Off" -> 0
                    "15m" -> 15 * 60
                    "30m" -> 30 * 60
                    "60m" -> 60 * 60
                    "Custom" -> it.customTimerMinutes * 60
                    "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36)
                    else -> 0
                }
            } ?: (viewModel.settingsManager.timerMinutes * 60)
if (timerSoundEnabled && (timeRemainingMs > 1000L || isPracticeMode || secsSound == 0)) {
                    viewModel.soundHapticManager.playTick()
                }
            }

            if (questionTimeRemainingMs <= 0L) {
                if (hapticEnabled) {
                    viewModel.soundHapticManager.hapticWarning()
                }
                val secsSound = viewModel.currentExamSettings?.let {
                when (it.timerPreset) {
                    "Off" -> 0
                    "15m" -> 15 * 60
                    "30m" -> 30 * 60
                    "60m" -> 60 * 60
                    "Custom" -> it.customTimerMinutes * 60
                    "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36)
                    else -> 0
                }
            } ?: (viewModel.settingsManager.timerMinutes * 60)
if (timerSoundEnabled && (timeRemainingMs > 1000L || isPracticeMode || secsSound == 0)) {
                    viewModel.soundHapticManager.playTimeOut()
                }

                if (currentQuestionFlow) {
                    // Lock the question and move forward
                    if (!selectedAnswers.containsKey(currentIndex)) {
                         val emptyMap = selectedAnswers.toMutableMap()
                         emptyMap[currentIndex] = setOf(-1)
                         selectedAnswers = emptyMap
                    }
                    val newLocks = lockedAnswers.toMutableMap()
                    newLocks[currentIndex] = true
                    lockedAnswers = newLocks
                    
                    if (currentIndex < questions.size - 1) {
                        currentIndex++
                    } else {
                        submitQuiz()
                    }
                } else {
                    // Auto lock question as incorrect if time runs out
                    if (!selectedAnswers.containsKey(currentIndex)) {
                         // Empty selection means answered wrongly
                         val emptyMap = selectedAnswers.toMutableMap()
                         emptyMap[currentIndex] = setOf(-1)
                         selectedAnswers = emptyMap
                    }
                    val newLocks = lockedAnswers.toMutableMap()
                    newLocks[currentIndex] = true
                    lockedAnswers = newLocks
                }
            }
        }
    }

    val autoNextDelay = viewModel.settingsManager.autoNextSeconds
    LaunchedEffect(autoNextTrigger) {
        if (autoNextTrigger > 0 && isPracticeMode && autoNextDelay > 0 && currentIndex < questions.size - 1) {
            kotlinx.coroutines.delay(autoNextDelay * 1000L)
            currentIndex++
        }
    }

    val optionsCache = remember(currentUseDistractors, currentShuffleOptions) { mutableMapOf<Long, List<Pair<Int, String>>>() }


    val isBookmarked = bookmarks.any { it.bookmarkKey == "${currentQuestion.bookId}_${currentQuestion.id}" }

    if (showGridDialog) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            title = { Text("Jump to Question") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items = questions, key = { _, question -> question.globalId }) { index, question ->
                        val ans = selectedAnswers[index]
                        val isAnswered = ans != null && !(ans.contains(-1)) && ans.isNotEmpty()
                        val isCurrent = index == currentIndex
                        val isPracticing = viewModel.settingsManager.quizMode == "practice"
                        
                        val correctIndices = androidx.compose.runtime.remember(question.id) {
                            val gson = com.example.data.SharedGson.normal
                            val correctIds = gson.fromJson<List<String>>(question.advancedCorrectIds ?: "[]", object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                            val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(question.advancedOptions ?: "[]", object: com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>(){}.type) ?: emptyList()
                            if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                                correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                            } else {
                                setOf(question.correctIndex)
                            }
                        }
                        
                        val isWrong = isAnswered && ans != correctIndices && isPracticing && lockedAnswers[index] == true
                        
                        val bgColor: androidx.compose.ui.graphics.Color
                        val textColor: androidx.compose.ui.graphics.Color
                        
                        if (isCurrent) {
                            bgColor = MaterialTheme.colorScheme.primary
                            textColor = MaterialTheme.colorScheme.onPrimary
                        } else if (isWrong) {
                            bgColor = MaterialTheme.colorScheme.error
                            textColor = MaterialTheme.colorScheme.onError
                        } else if (isAnswered) {
                            bgColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            textColor = androidx.compose.ui.graphics.Color.White
                        } else if (visitedQuestions.contains(index)) {
                            bgColor = androidx.compose.ui.graphics.Color(0xFFFF9800)
                            textColor = androidx.compose.ui.graphics.Color.White
                        } else {
                            bgColor = MaterialTheme.colorScheme.surfaceVariant
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        Box(
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(enabled = !currentQuestionFlow || index >= currentIndex) {
                                    if (currentQuestionFlow && index > currentIndex) {
                                        // Also need to mark skipped questions as wrong/skipped
                                        for (i in currentIndex until index) {
                                            val newLocks = lockedAnswers.toMutableMap()
                                            newLocks[i] = true
                                            lockedAnswers = newLocks
                                        }
                                    }
                                    currentIndex = index
                                    showGridDialog = false
                                }
                        ) {
                            Surface(color = bgColor, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {}
                            Text("${index + 1}", color = textColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Q ${currentIndex + 1} / ${questions.size}", fontWeight = FontWeight.Bold)
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        val secs = viewModel.currentExamSettings?.let {
                            when (it.timerPreset) {
                                "Off" -> 0
                                "15m" -> 15 * 60
                                "30m" -> 30 * 60
                                "60m" -> 60 * 60
                                "Custom" -> it.customTimerMinutes * 60
                                "Auto" -> if (viewModel.questionLimit.value == null || viewModel.questionLimit.value == 0) 0 else (viewModel.questionLimit.value!! * 36)
                                else -> 0
                            }
                        } ?: (viewModel.settingsManager.timerMinutes * 60)
                        
                        val tSecs = timeRemainingMs / 1000
                        val tMins = tSecs / 60
                        val tRemSecs = tSecs % 60
                        val tHours = tMins / 60
                        val tRemMins = tMins % 60
                        val timeStr = if (tHours > 0) String.format("%d:%02d:%02d", tHours, tRemMins, tRemSecs) else String.format("%02d:%02d", tRemMins, tRemSecs)
                        
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isPracticeMode && secs > 0 && timeRemainingMs <= 60000) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (!isPracticeMode && secs > 0 && timeRemainingMs <= 60000) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Timer, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (questionTimerTarget > 0) {
                            val qSecs = questionTimeRemainingMs / 1000
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (questionTimeRemainingMs <= 5000) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = if (questionTimeRemainingMs <= 5000) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    text = "${qSecs}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showGridDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Questions Grid"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleBookmark(currentQuestion.bookId, currentQuestion.id) }) {
                        androidx.compose.animation.Crossfade(targetState = isBookmarked, label = "bookmark") { state ->
                            Icon(
                                imageVector = if (state) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (state) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        
                        }
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { ((currentIndex + 1).toFloat() / questions.size.coerceAtLeast(1)) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.animation.AnimatedContent<Int>(
                targetState = currentIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        (androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { it / 2 },
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        )) togetherWith (androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { -it / 2 },
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ))
                    } else {
                        (androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { -it / 2 },
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        )) togetherWith (androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { it / 2 },
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ))
                    }
                },
                modifier = Modifier.weight(1f)
            ) { animatedIndex ->
                val animatedQuestion = questions[animatedIndex]
                val displayOptions = remember(animatedQuestion.id, optionsCache) {
                    optionsCache.getOrPut(animatedQuestion.id) {
                        val baseOptions = try { com.example.data.SharedGson.normal.fromJson(animatedQuestion.options, Array<String>::class.java)?.toList() ?: emptyList() } catch(e: Exception) { emptyList() }
                        var finalOptions = baseOptions.mapIndexed { idx, text -> Pair(idx, text) }
                        
                        if (currentUseDistractors && animatedQuestion.extraOptions.isNotBlank() && animatedQuestion.extraOptions != "[]") {
                            try {
                                val extras = com.example.data.SharedGson.normal.fromJson(animatedQuestion.extraOptions, Array<String>::class.java).toList()
                                val extraPairs = extras.mapIndexed { idx, it -> Pair(-(idx + 1), it) } // negative means distractor
                                finalOptions = finalOptions + extraPairs
                            } catch(e: Exception){}
                        }
                        
                        if (currentShuffleOptions) {
                            val gson = com.example.data.SharedGson.normal
                            val correctIds = gson.fromJson<List<String>>(animatedQuestion.advancedCorrectIds ?: "[]", object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                            val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(animatedQuestion.advancedOptions ?: "[]", object: com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>(){}.type) ?: emptyList()
                            val correctIndices = if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                                correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                            } else {
                                setOf(animatedQuestion.correctIndex)
                            }
                            
                            val correctOpts = finalOptions.filter { correctIndices.contains(it.first) }
                            var mutableList = finalOptions.toMutableList()
                            val stableSeed = viewModel.activeQuizSessionId + animatedQuestion.id
                            mutableList.shuffle(kotlin.random.Random(stableSeed)) // Random shuffle that stays stable for this session
                            
                            // Limit to 4 options while ensuring ALL correct options are included
                            if (mutableList.size > 4) {
                                val selected = mutableList.take(4).toMutableList()
                                val missingCorrectOpts = correctOpts.filter { !selected.contains(it) }
                                
                                if (missingCorrectOpts.isNotEmpty()) {
                                    val fallbackList = selected.filter { !correctOpts.contains(it) }.dropLast(missingCorrectOpts.size).toMutableList()
                                    fallbackList.addAll(correctOpts.filter { selected.contains(it) })
                                    fallbackList.addAll(missingCorrectOpts)
                                    fallbackList.shuffle(kotlin.random.Random(stableSeed))
                                    mutableList = fallbackList
                                } else {
                                    mutableList = selected
                                }
                            }
                            finalOptions = mutableList
                        }
                        finalOptions
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).animateContentSize()
                ) {
                val (rangeStr, isFirstInGroup) = androidx.compose.runtime.remember(questions, animatedIndex) {
                    val q = questions[animatedIndex]
                    val header = q.questionHeader
                    if (header.isNullOrBlank()) {
                        Pair("", true)
                    } else {
                        var startIdx = animatedIndex
                        while (startIdx > 0 && questions[startIdx - 1].questionHeader == header) {
                            startIdx--
                        }
                        var endIdx = animatedIndex
                        while (endIdx < questions.size - 1 && questions[endIdx + 1].questionHeader == header) {
                            endIdx++
                        }
                        val isFirst = startIdx == animatedIndex
                        val range = if (startIdx == endIdx) "(${startIdx + 1})" else "(${startIdx + 1}-${endIdx + 1})"
                        Pair(range, isFirst)
                    }
                }

                if (!animatedQuestion.questionHeader.isNullOrBlank() || (!animatedQuestion.exam.isBlank() || !animatedQuestion.year.isBlank() || !animatedQuestion.exams.isNullOrBlank())) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        if (!animatedQuestion.questionHeader.isNullOrBlank()) {
                            com.example.ui.components.ExpandableQuestionHeader(
                                headerText = animatedQuestion.questionHeader,
                                rangeStr = rangeStr,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                                isQuizMode = true,
                                initiallyExpanded = isFirstInGroup
                            )
                        }
                        com.example.ui.components.ExamTagsRow(
                            examsJson = animatedQuestion.exams,
                            legacyExam = animatedQuestion.exam,
                            legacyYear = animatedQuestion.year,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                RichText(
                    text = "<b>Q${animatedIndex + 1}</b> ${animatedQuestion.question}",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (animatedQuestion.format == "table" && animatedQuestion.tableData != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val tableData = try {
                        val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                        com.example.data.SharedGson.normal.fromJson<List<List<String>>>(animatedQuestion.tableData, tableType)
                    } catch (e: Exception) {
                        null
                    }
                    if (tableData != null) {
                        com.example.ui.components.TableRenderer(tableData)
                    }
                }
                
                if (!animatedQuestion.bottomText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    RichText(
                        text = animatedQuestion.bottomText!!,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (!animatedQuestion.img.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val imgPath = animatedQuestion.img!!
                    var imageModel by remember(imgPath) { mutableStateOf<String?>(null) }
                    LaunchedEffect(imgPath) {
                        if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                            imageModel = imgPath
                        } else {
                            imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${animatedQuestion.bookId}/images/${imgPath.substringAfterLast("/")}")
                                val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                                when {
                                    f1.exists() -> f1.absolutePath
                                    f2.exists() -> f2.absolutePath
                                    else -> "file:///android_asset/data/$imgPath"
                                }
                            }
                        }
                    }

                    if (imageModel != null) {
                        SubcomposeAsyncImage(
                            model = imageModel,
                        contentDescription = "Question Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .heightIn(max = 300.dp),
                        contentScale = ContentScale.Fit
                    ) {
                        val state = painter.state
                        when (state) {
                            is AsyncImagePainter.State.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "\"$imgPath\" not found or corrupted",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            else -> {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                } // End of if (imageModel != null)
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                val correctIndices = remember(animatedQuestion.id) {
                    val gson = com.example.data.SharedGson.normal
                    val correctIds = gson.fromJson<List<String>>(animatedQuestion.advancedCorrectIds ?: "[]", object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                    val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(animatedQuestion.advancedOptions ?: "[]", object: com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>(){}.type) ?: emptyList()
                    if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                        correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                    } else {
                        setOf(animatedQuestion.correctIndex)
                    }
                }

                displayOptions.forEachIndexed { displayIndex, (originalIndex, option) ->
                    val isSelected = selectedAnswers[animatedIndex]?.contains(originalIndex) == true
                    val isLocked = lockedAnswers[animatedIndex] == true
                    val hasSelection = selectedAnswers[animatedIndex]?.isNotEmpty() == true
                    
                    val shouldReveal = isLocked && isPracticeMode
                    val targetContainerColor = if (shouldReveal) {
                        if (correctIndices.contains(originalIndex)) Color(0xFFDCFCE7) // Green
                        else if (isSelected && !correctIndices.contains(originalIndex)) Color(0xFFFEE2E2) // Red
                        else MaterialTheme.colorScheme.surfaceVariant
                    } else if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val containerColor by androidx.compose.animation.animateColorAsState(targetContainerColor, label = "containerColor")
                    val targetTextColor = if (shouldReveal) {
                        if (correctIndices.contains(originalIndex)) Color(0xFF166534)
                        else if (isSelected && !correctIndices.contains(originalIndex)) Color(0xFF991B1B)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val textColor by androidx.compose.animation.animateColorAsState(targetTextColor, label = "textColor")
                    val onOptionClick: () -> Unit = {
                        if (!isLocked) {
                                if (hapticEnabled) {
                                    viewModel.soundHapticManager.hapticButtonPress()
                                }
                                if (clickSoundEnabled) {
                                    viewModel.soundHapticManager.playClick()
                                }
                                val newAnswers = selectedAnswers.toMutableMap()
                                val currentSet = newAnswers[animatedIndex]?.toMutableSet() ?: mutableSetOf()
                                if (animatedQuestion.multipleCorrect) {
                                    if (currentSet.contains(originalIndex)) currentSet.remove(originalIndex) else currentSet.add(originalIndex)
                                } else {
                                    currentSet.clear()
                                    currentSet.add(originalIndex)
                                }
                                newAnswers[animatedIndex] = currentSet
                                selectedAnswers = newAnswers
                                
                                if (isPracticeMode && !animatedQuestion.multipleCorrect) {
                                    val newLocked = lockedAnswers.toMutableMap()
                                    newLocked[animatedIndex] = true
                                    lockedAnswers = newLocked
                                    
                                    if (originalIndex == animatedQuestion.correctIndex) {
                                        if (clickSoundEnabled) viewModel.soundHapticManager.playCorrect()
                                        if (hapticEnabled) viewModel.soundHapticManager.hapticCorrect()
                                    } else {
                                        if (clickSoundEnabled) viewModel.soundHapticManager.playWrong()
                                        if (hapticEnabled) viewModel.soundHapticManager.hapticIncorrect()
                                    }
                                    autoNextTrigger = System.currentTimeMillis()
                                }
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable(enabled = !isLocked) { onOptionClick() },
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (animatedQuestion.multipleCorrect) {
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = textColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Text(
                                    text = "${(65 + displayIndex).toChar()}.",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else textColor,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                            RichText(
                                text = option,
                                fontSize = 16.sp,
                                color = textColor,
                                isSelectable = false,
                                onClick = onOptionClick
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isPracticeMode && lockedAnswers[animatedIndex] == true,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    val hasDetailed = !animatedQuestion.detailedExplanation.isNullOrEmpty() || !animatedQuestion.explanationImage.isNullOrEmpty() || !animatedQuestion.explanationTable.isNullOrEmpty()
                    val hasBasic = animatedQuestion.explanation.isNotBlank()
                    
                    if (hasBasic || hasDetailed || true) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    if (hasBasic) {
                                        Text("Explanation:", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        RichText(animatedQuestion.explanation)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (hasDetailed) {
                                            OutlinedButton(onClick = { 
                                                explanationSheetTab = 0
                                                showExplanationSheet = true 
                                            }) {
                                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Detailed")
                                            }
                                        }
                                        Button(onClick = { 
                                            explanationSheetTab = 1
                                            showExplanationSheet = true 
                                        }) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("AI Explanation")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            var showSubmitDialog by remember { mutableStateOf(false) }
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isPracticeMode && currentQuestion.multipleCorrect && lockedAnswers[currentIndex] != true) {
                        Button(onClick = {
                            viewModel.playButtonPress()
                            val newLocked = lockedAnswers.toMutableMap()
                            newLocked[currentIndex] = true
                            lockedAnswers = newLocked
                            
                            val gson = com.example.data.SharedGson.normal
                            val correctIds = gson.fromJson<List<String>>(currentQuestion.advancedCorrectIds ?: "[]", object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) ?: emptyList()
                            val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(currentQuestion.advancedOptions ?: "[]", object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>() {}.type) ?: emptyList()
                            val correctIndices = if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                                correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                            } else {
                                setOf(currentQuestion.correctIndex)
                            }
                            
                            val isCorrect = selectedAnswers[currentIndex] == correctIndices
                            if (isCorrect) {
                                if (clickSoundEnabled) viewModel.soundHapticManager.playCorrect()
                                if (hapticEnabled) viewModel.soundHapticManager.hapticCorrect()
                            } else {
                                if (clickSoundEnabled) viewModel.soundHapticManager.playWrong()
                                if (hapticEnabled) viewModel.soundHapticManager.hapticIncorrect()
                            }
                            autoNextTrigger = System.currentTimeMillis()
                        }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Answer")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { 
                                viewModel.playButtonPress()
                                if (currentIndex > 0) currentIndex-- 
                            },
                            enabled = currentIndex > 0 && !currentQuestionFlow,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        
                        if (!isPracticeMode && selectedAnswers.containsKey(currentIndex)) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.playButtonPress()
                                    val newAnswers = selectedAnswers.toMutableMap()
                                    newAnswers.remove(currentIndex)
                                    selectedAnswers = newAnswers
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        if (currentIndex < questions.size - 1) {
                            val canSkip = !isPracticeMode && !selectedAnswers.containsKey(currentIndex) && !lockedAnswers.containsKey(currentIndex)
                            if (canSkip) {
                                Button(
                                    onClick = { 
                                        viewModel.playButtonPress()
                                        currentIndex++ 
                                    }, 
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Skip", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Button(onClick = { 
                                    viewModel.playButtonPress()
                                    currentIndex++ 
                                }, modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text("Next", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Button(
                                onClick = { 
                                    viewModel.playButtonPress()
                                    showSubmitDialog = true 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Submit", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showCancelDialog = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("Cancel Quiz", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                        if (currentIndex < questions.size - 1) {
                            TextButton(onClick = { 
                                viewModel.playButtonPress()
                                showSubmitDialog = true 
                            }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("Submit Early", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            
            if (showSubmitDialog) {
                AlertDialog(
                    onDismissRequest = { showSubmitDialog = false },
                    title = { Text("Submit Quiz") },
                    text = { Text("Are you sure you want to submit the quiz? You cannot review your answers after submission.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showSubmitDialog = false
                            submitQuiz()
                        }) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSubmitDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ExplanationSheet() {
        if (showExplanationSheet) {
            val currentQuestion = questions[currentIndex]
            val hasDetailed = !currentQuestion.detailedExplanation.isNullOrEmpty() || !currentQuestion.explanationImage.isNullOrEmpty() || !currentQuestion.explanationTable.isNullOrEmpty()
            val actualTabIndex = if (hasDetailed) explanationSheetTab else 1
            
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val draggableState = androidx.compose.foundation.gestures.rememberDraggableState { }
            val nestedScrollConnection = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                        return available.copy(x = 0f)
                    }
                    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                        return available.copy(x = 0f)
                    }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { showExplanationSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                    .nestedScroll(nestedScrollConnection)
                    .draggable(draggableState, orientation = Orientation.Vertical)
                ) {
                    if (hasDetailed) {
                        TabRow(selectedTabIndex = actualTabIndex) {
                            Tab(
                                selected = actualTabIndex == 0,
                                onClick = { explanationSheetTab = 0 },
                                text = { Text("Detailed") }
                            )
                            Tab(
                                selected = actualTabIndex == 1,
                                onClick = { explanationSheetTab = 1 },
                                text = { Text("AI Explanation") }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("AI Explanation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp).graphicsLayer(alpha = 0.5f))
                    }
                    
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (actualTabIndex == 0 && hasDetailed) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).animateContentSize()) {
                                if (!questions[currentIndex].explanationImage.isNullOrEmpty()) {
                                    val imgPath = questions[currentIndex].explanationImage!!
                                    var imageModel by remember(imgPath) { mutableStateOf<String?>(null) }
                                    var showZoomableImage by remember { mutableStateOf(false) }
                                    LaunchedEffect(imgPath) {
                                        if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                                            imageModel = imgPath
                                        } else {
                                            imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${questions[currentIndex].bookId}/images/${imgPath.substringAfterLast("/")}")
                                                val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                                                when {
                                                    f1.exists() -> f1.absolutePath
                                                    f2.exists() -> f2.absolutePath
                                                    else -> "file:///android_asset/data/$imgPath"
                                                }
                                            }
                                        }
                                    }
                                    if (imageModel != null) {
                                        coil.compose.SubcomposeAsyncImage(
                                            model = imageModel,
                                            contentDescription = "Explanation Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .padding(bottom = 8.dp)
                                                .clickable { showZoomableImage = true },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Inside
                                        )
                                        
                                        if (showZoomableImage) {
                                            androidx.compose.ui.window.Dialog(
                                                onDismissRequest = { showZoomableImage = false },
                                                properties = androidx.compose.ui.window.DialogProperties(
                                                    usePlatformDefaultWidth = false,
                                                    decorFitsSystemWindows = false
                                                )
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    var scale by remember { mutableStateOf(1f) }
                                                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                                                    val state = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, offsetChange, _ ->
                                                        scale = (scale * zoomChange).coerceIn(1f, 5f)
                                                        offset += offsetChange
                                                    }
                                                    
                                                    coil.compose.AsyncImage(
                                                        model = imageModel,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer(
                                                                scaleX = scale,
                                                                scaleY = scale,
                                                                translationX = offset.x,
                                                                translationY = offset.y
                                                            )
                                                            .transformable(state = state),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                    )
                                                    
                                                    IconButton(
                                                        onClick = { showZoomableImage = false },
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(16.dp)
                                                            .padding(top = 32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Close",
                                                            tint = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!questions[currentIndex].detailedExplanation.isNullOrEmpty()) {
                                    RichText(questions[currentIndex].detailedExplanation!!)
                                }
                                if (!questions[currentIndex].explanationTable.isNullOrEmpty()) {
                                    val expTableData = androidx.compose.runtime.remember(questions[currentIndex].id) {
                                        try {
                                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(questions[currentIndex].explanationTable, tableType)
                                        } catch (e: Exception) { null }
                                    }
                                    if (expTableData != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.example.ui.components.TableRenderer(expTableData, MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Spacer(modifier = Modifier.height(48.dp).navigationBarsPadding())
                            }
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).animateContentSize()) {
                                if (aiExplanationState == "Idle" || aiExplanationState == "Loading") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        if (aiExplanationState == "Loading") {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(Modifier.height(16.dp))
                                                Text(aiExplanationStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            Text("Initializing AI...")
                                        }
                                    }
                                } else if (aiExplanationState == "Error") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.height(8.dp))
                                            Text(aiExplanationText.ifEmpty { "Failed to generate explanation. Please check your Gemini API keys in Settings." }, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Spacer(Modifier.height(16.dp))
                                            Button(onClick = { aiExplanationStateMap = aiExplanationStateMap + (currentIndex to "Idle") }) {
                                                Text("Retry")
                                            }
                                        }
                                    }
                                } else {
                                    com.example.ui.components.SmartRichText(aiExplanationText, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OutlinedButton(
                                        onClick = { aiExplanationStateMap = aiExplanationStateMap + (currentIndex to "Idle") },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Regenerate")
                                    }
                                }
                                Spacer(modifier = Modifier.height(48.dp).navigationBarsPadding())
                            }
                            
                            LaunchedEffect(aiExplanationState, explanationSheetTab, currentIndex) {
                                if (aiExplanationState == "Idle" && explanationSheetTab == 1) {
                                    aiExplanationStateMap = aiExplanationStateMap + (currentIndex to "Loading")
                                }
                            }
                            
                            LaunchedEffect(aiExplanationState, currentIndex) {
                                if (aiExplanationState == "Loading") {
                                    launch {
                                        aiExplanationStatus = "Analyzing question..."
                                        kotlinx.coroutines.delay(400)
                                        aiExplanationStatus = "Reviewing answer context..."
                                        kotlinx.coroutines.delay(400)
                                        aiExplanationStatus = "Generating AI Explanation..."
                                    }
                                    
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val currentQuestion = questions[currentIndex]
                                        val gson = com.example.data.SharedGson.normal
                                        val correctIds = gson.fromJson<List<String>>(currentQuestion.advancedCorrectIds ?: "[]", object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                                        val rawOptionsList = gson.fromJson<List<com.example.data.QBookOption>>(currentQuestion.advancedOptions ?: "[]", object: com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>(){}.type) ?: emptyList()
                                        val legacyOptions = gson.fromJson<List<String>>(currentQuestion.options, object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()

                                        val currentDisplayOptions = optionsCache[currentQuestion.id] ?: emptyList()
                                        
                                        val correctIndices = if (correctIds.isNotEmpty() && rawOptionsList.isNotEmpty()) {
                                            correctIds.mapNotNull { id -> rawOptionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                                        } else {
                                            setOf(currentQuestion.correctIndex)
                                        }
                                        
                                        val displayedCorrectIndices = currentDisplayOptions.mapIndexedNotNull { displayIdx, pair ->
                                            if (correctIndices.contains(pair.first)) displayIdx else null
                                        }
                                        
                                        val correctTexts = displayedCorrectIndices.map { i -> 
                                            val opt = currentDisplayOptions[i].second
                                            "Option ${(65 + i).toChar()}: $opt" 
                                        }
                                        
                                        val userSelectedSet = selectedAnswers[currentIndex] ?: emptySet()
                                        
                                        val displayedUserSelectedIndices = currentDisplayOptions.mapIndexedNotNull { displayIdx, pair ->
                                            if (userSelectedSet.contains(pair.first)) displayIdx else null
                                        }
                                        
                                        val userSelectedTexts = displayedUserSelectedIndices.map { i -> 
                                            val opt = currentDisplayOptions[i].second
                                            "Option ${(65 + i).toChar()}: $opt" 
                                        }

                                        val tableDataString = if (currentQuestion.format == "table" && currentQuestion.tableData != null) {
                                            "\nTable Data (2D Array JSON): ${currentQuestion.tableData}"
                                        } else ""
                                        val aiPrompt = """
You are an expert tutor. Please explain the following question and its answer.
Question: ${currentQuestion.question}$tableDataString
Options:
${currentDisplayOptions.mapIndexed { i, opt -> "${(65 + i).toChar()}: ${opt.second}" }.joinToString("\n")}

Correct Answer(s): ${correctTexts.joinToString(", ")}

User selected: ${if (userSelectedTexts.isNotEmpty()) userSelectedTexts.joinToString(", ") else "None (or skipped)"}

Please provide a detailed, easy-to-understand explanation for the question.
If the user's selected answer is incorrect, explicitly explain WHY it is incorrect based on their selection.
When referring to options, ALWAYS use their letter designations (e.g. Option A, Option B) that exactly match the options provided above. Do not reference options that do not exist above.
Format the explanation nicely in Markdown. You can generate Markdown tables if needed. Use an image ONLY if illustrating a spatial, structural, or complex visual concept would significantly help understanding. If you choose to use an image, use a markdown image tag like ![illustration](https://image.pollinations.ai/prompt/describe-the-concept-here) (prompt must be URL encoded, no spaces). Do NOT use images for abstract concepts or simple factual questions. Keep it professional and visually pleasing.
                                        """.trimIndent()

                                        val result = com.example.data.GeminiHelper.generateContent(context, aiPrompt, onDevLog = { viewModel.addDevLog(it) })
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (result.startsWith("Error")) {
                                                aiExplanationStateMap = aiExplanationStateMap + (currentIndex to "Error")
                                            } else {
                                                aiExplanationTextMap = aiExplanationTextMap + (currentIndex to result)
                                                aiExplanationStateMap = aiExplanationStateMap + (currentIndex to "Success")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    ExplanationSheet()

        if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Quiz") },
            text = { Text("Would you like to save your progress to resume later, or discard this quiz? Tap outside to resume.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    isSubmitted.value = true // Prevent onDispose from saving corrupted state
                    viewModel.saveActiveQuizState(currentIndex, selectedAnswers, lockedAnswers, timeRemainingMs)
                    onCancel()
                }) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isSubmitted.value = true // prevent auto-save in DisposableEffect
                    viewModel.clearActiveQuizState()
                    viewModel.currentPracticeMistakesParentId = null
                    showCancelDialog = false
                    onCancel()
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Discard & Exit")
                }
            }
        )
    }
}
