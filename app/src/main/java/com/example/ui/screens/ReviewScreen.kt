package com.example.ui.screens
import androidx.compose.material.icons.filled.Timer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: MainViewModel, onHome: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by viewModel.history.collectAsState()
    val allQuestions by viewModel.reviewQuestions.collectAsState()
    val selectedId by viewModel.selectedResultIdForReview.collectAsState()
    
    val lastResult = history.find { it.historyId == selectedId } ?: history.firstOrNull()
    var filter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("All") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Questions") },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (lastResult == null || allQuestions.isEmpty()) {
                Text("No data to review.")
                return@Column
            }

            val questionIds = com.example.data.SharedGson.normal.fromJson<List<Double>>(lastResult.questionIds, object : TypeToken<List<Double>>() {}.type).map { it.toLong() }
            // Handle both legacy (List<Double?>) and new (List<List<Double>?>) formats
            val gson = com.example.data.SharedGson.normal
            val rawList = gson.fromJson<List<Any?>>(lastResult.userAnswers, object : TypeToken<List<Any?>>() {}.type)
            val userAnswers = rawList.map { item ->
                when (item) {
                    is Number -> setOf(item.toInt())
                    is List<*> -> item.mapNotNull { (it as? Number)?.toInt() }.toSet()
                    else -> emptySet()
                }
            }
            

            val questionTimes = try {
                gson.fromJson<List<Long>>(lastResult.activeQuizData, object : TypeToken<List<Long>>() {}.type) ?: emptyList()
            } catch(e: Exception) { emptyList() }
            val filters = listOf("All", "Correct", "Wrong", "Skipped", "Bookmarked")
            ScrollableTabRow(
                selectedTabIndex = filters.indexOf(filter).coerceAtLeast(0),
                edgePadding = 0.dp
            ) {
                filters.forEach { f ->
                    Tab(
                        selected = filter == f,
                        onClick = { filter = f },
                        text = { Text(f) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val bookmarks by viewModel.bookmarks.collectAsState()
            val bookmarkedIds = bookmarks.map { it.questionId }.toSet()

            val reviewItems = androidx.compose.runtime.remember(questionIds, allQuestions, filter, userAnswers, bookmarkedIds, questionTimes) {
                val list = mutableListOf<Any>()
                var currentHeader: String? = null
                
                val processedQuestions = questionIds.mapIndexedNotNull { index, qId ->
                    val q = allQuestions.find { it.id == qId }
                    if (q != null) index to q else null
                }.map { (index, q) ->
                    val userAnswerSet = userAnswers.getOrNull(index) ?: emptySet()
                    val correctIndices = run {
                        val correctIds = gson.fromJson<List<String>>(q.advancedCorrectIds ?: "[]", object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) ?: emptyList()
                        val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(q.advancedOptions ?: "[]", object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>() {}.type) ?: emptyList()
                        if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                            correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                        } else {
                            setOf(q.correctIndex)
                        }
                    }
                    val isSkipped = userAnswerSet.isEmpty() || userAnswerSet.contains(-1)
                    val isCorrect = userAnswerSet == correctIndices
                    val isWrong = !isSkipped && !isCorrect
                    val isBookmarked = bookmarkedIds.contains(q.id)
                    val show = when (filter) {
                        "All" -> true
                        "Correct" -> isCorrect
                        "Wrong" -> isWrong
                        "Skipped" -> isSkipped
                        "Bookmarked" -> isBookmarked
                        else -> true
                    }
                    val qTimeMs = questionTimes.getOrNull(index) ?: 0L
                    Triple(index, q, mapOf("show" to show, "isCorrect" to isCorrect, "isSkipped" to isSkipped, "isWrong" to isWrong, "isBookmarked" to isBookmarked, "qTimeMs" to qTimeMs, "correctIndices" to correctIndices, "userAnswerSet" to userAnswerSet))
                }

                var i = 0
                while (i < processedQuestions.size) {
                    val (index, q, data) = processedQuestions[i]
                    val show = data["show"] as Boolean
                    
                    if (q.questionHeader != currentHeader) {
                        currentHeader = q.questionHeader
                        if (!currentHeader.isNullOrBlank()) {
                            var anyVisible = false
                            val startIdx = index + 1
                            var endIdx = startIdx
                            for (j in i until processedQuestions.size) {
                                if (processedQuestions[j].second.questionHeader == currentHeader) {
                                    endIdx = processedQuestions[j].first + 1
                                    if (processedQuestions[j].third["show"] as Boolean) anyVisible = true
                                } else break
                            }
                            if (anyVisible) {
                                val rangeStr = if (startIdx == endIdx) "($startIdx)" else "($startIdx-$endIdx)"
                                list.add(Pair("HEADER_${q.id}", mapOf("text" to currentHeader, "range" to rangeStr)))
                            }
                        }
                    }
                    
                    if (show) {
                        list.add(processedQuestions[i])
                    }
                    i++
                }
                list
            }

            LazyColumn {
                items(reviewItems) { item ->
                    if (item is Pair<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val headerData = item.second as Map<String, String?>
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                            com.example.ui.components.ExpandableQuestionHeader(
                                headerText = headerData["text"] ?: "",
                                rangeStr = headerData["range"] ?: "",
                                textColor = MaterialTheme.colorScheme.onSurface,
                                isQuizMode = true
                            )
                        }
                    } else if (item is Triple<*, *, *>) {
                        val index = item.first as Int
                        val question = item.second as com.example.data.QuestionEntity
                        @Suppress("UNCHECKED_CAST")
                        val data = item.third as Map<String, Any>
                        val isCorrect = data["isCorrect"] as Boolean
                        val isSkipped = data["isSkipped"] as Boolean
                        val isWrong = data["isWrong"] as Boolean
                        val isBookmarked = data["isBookmarked"] as Boolean
                        val qTimeMs = data["qTimeMs"] as Long
                        @Suppress("UNCHECKED_CAST")
                        val correctIndices = data["correctIndices"] as Set<Int>
                        @Suppress("UNCHECKED_CAST")
                        val userAnswerSet = data["userAnswerSet"] as Set<Int>
                        
                        val qMins = qTimeMs / 60000
                        val qSecs = (qTimeMs % 60000) / 1000
                        val timeStr = if (qTimeMs > 0) String.format("%02d:%02d", qMins, qSecs) else "--:--"
                        
                        val borderColor = if (isCorrect) Color(0xFF16A34A) else if (isSkipped) Color(0xFFF97316) else Color(0xFFDC2626)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                            if (isSkipped) {
                                                Surface(color = Color(0xFFFFF7ED), contentColor = Color(0xFFC2410C), shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))) {
                                                    Text("Skipped", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (isCorrect) {
                                                Surface(color = Color(0xFFF0FDF4), contentColor = Color(0xFF15803D), shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7))) {
                                                    Text("Correct", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Surface(color = Color(0xFFFEF2F2), contentColor = Color(0xFFB91C1C), shape = androidx.compose.foundation.shape.RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEE2E2))) {
                                                    Text("Wrong", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Row {
                                            Text("${index + 1}. ", fontWeight = FontWeight.Bold)
                                            com.example.ui.components.SmartRichText(text = question.question, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                                        IconButton(onClick = { viewModel.toggleBookmark(question.bookId, question.id) }, modifier = Modifier.size(24.dp).minimumInteractiveComponentSize()) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (qTimeMs > 0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Timer, contentDescription = "Time Taken", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(text = timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                
                                if (question.format == "table" && question.tableData != null) {
                                    val tableData = androidx.compose.runtime.remember(question.id) {
                                    try {
                                        val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                                        com.example.data.SharedGson.normal.fromJson<List<List<String>>>(question.tableData, tableType)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                    if (tableData != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.example.ui.components.TableRenderer(tableData)
                                    }
                                }
                                
                                if (!question.bottomText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    com.example.ui.components.SmartRichText(text = question.bottomText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                if (!question.img.isNullOrBlank()) {
                                    val imgPath = question.img!!
                                    var imageModel by androidx.compose.runtime.remember(imgPath) { androidx.compose.runtime.mutableStateOf<String?>(null) }
                                    androidx.compose.runtime.LaunchedEffect(imgPath) {
                                        if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                                            imageModel = imgPath
                                        } else {
                                            imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${question.bookId}/images/${imgPath.substringAfterLast("/")}")
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
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SubcomposeAsyncImage(
                                            model = imageModel,
                                            contentDescription = "Question Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        ) {
                                            val state = painter.state
                                            when (state) {
                                                is coil.compose.AsyncImagePainter.State.Loading -> {
                                                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                                is coil.compose.AsyncImagePainter.State.Error -> {
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
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.padding(16.dp)
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    SubcomposeAsyncImageContent()
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val displayOptions = androidx.compose.runtime.remember(question.id) {
                                    try {
                                        com.example.data.SharedGson.normal.fromJson<List<String>>(question.options, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) ?: emptyList()
                                    } catch (e: Exception) { emptyList() }
                                }
                                
                                displayOptions.forEachIndexed { optIndex, opt ->
                                    val isThisUserAnswer = userAnswerSet.contains(optIndex)
                                    val isThisCorrectAnswer = correctIndices.contains(optIndex)
                                    
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val icon = if (isThisUserAnswer && isThisCorrectAnswer) {
                                            Icons.Filled.CheckCircle
                                        } else if (isThisUserAnswer && !isThisCorrectAnswer) {
                                            Icons.Filled.Cancel
                                        } else if (!isThisUserAnswer && isThisCorrectAnswer) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            null
                                        }
                                        
                                        val tint = if (isThisCorrectAnswer) Color(0xFF16A34A) else if (isThisUserAnswer) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                        
                                        val iconDesc = if (isThisUserAnswer && isThisCorrectAnswer) {
                                            "Correct Answer"
                                        } else if (isThisUserAnswer && !isThisCorrectAnswer) {
                                            "Your Wrong Answer"
                                        } else if (!isThisUserAnswer && isThisCorrectAnswer) {
                                            "Correct Answer"
                                        } else {
                                            null
                                        }
                                        if (icon != null) {
                                            Icon(imageVector = icon, contentDescription = iconDesc, tint = tint, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Spacer(modifier = Modifier.width(28.dp))
                                        }
                                        
                                        Text("${(65 + optIndex).toChar()}. ", fontWeight = if (isThisCorrectAnswer || isThisUserAnswer) FontWeight.Bold else FontWeight.Normal, color = tint)
                                        com.example.ui.components.SmartRichText(text = opt, color = tint)
                                    }
                                }
                                
                                if (question.explanation.isNotBlank() || !question.detailedExplanation.isNullOrBlank()) {
                                    val hasBrief = question.explanation.isNotBlank()
                                    val hasDetailed = !question.detailedExplanation.isNullOrBlank()
                                    
                                    Text("Explanation:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    
                                    if (hasBrief && hasDetailed) {
                                        Text("Brief:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        com.example.ui.components.SmartRichText(text = question.explanation, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Detailed:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        com.example.ui.components.SmartRichText(text = question.detailedExplanation!!, color = MaterialTheme.colorScheme.onSurface)
                                    } else if (hasBrief) {
                                        com.example.ui.components.SmartRichText(text = question.explanation, color = MaterialTheme.colorScheme.onSurface)
                                    } else if (hasDetailed) {
                                        com.example.ui.components.SmartRichText(text = question.detailedExplanation!!, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (!question.explanationTable.isNullOrBlank()) {
                                        val expTableData = androidx.compose.runtime.remember(question.id) {
                                            try {
                                                val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                                                com.example.data.SharedGson.normal.fromJson<List<List<String>>>(question.explanationTable, tableType)
                                            } catch (e: Exception) { null }
                                        }
                                        if (expTableData != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.example.ui.components.TableRenderer(expTableData, MaterialTheme.colorScheme.onSurface)
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
}
