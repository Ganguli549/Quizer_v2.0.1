package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(viewModel: MainViewModel, onHome: () -> Unit, onStartQuiz: () -> Unit) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    val bookmarkedQuestions by viewModel.bookmarkedQuestions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
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
            if (bookmarkedQuestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No bookmarks yet for this book.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = {
                        viewModel.setQuizQuestionsForBookmarks()
                        onStartQuiz()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("Practice Bookmarked Questions (${bookmarkedQuestions.size})")
                }
                
LazyColumn(modifier = Modifier.weight(1f)) {
                    val grouped = mutableListOf<Pair<String?, List<com.example.data.QuestionEntity>>>()
                    var currentHeader: String? = null
                    var currentGroup = mutableListOf<com.example.data.QuestionEntity>()
                    
                    for (q in bookmarkedQuestions) {
                        val h = q.questionHeader
                        val isPassage = !h.isNullOrBlank() && Regex("<p(?:\\s+name=\"[^\"]*\")?\\s*>").containsMatchIn(h)
                        if (isPassage) {
                            if (h == currentHeader) {
                                currentGroup.add(q)
                            } else {
                                if (currentGroup.isNotEmpty()) {
                                    grouped.add(Pair(currentHeader, currentGroup))
                                }
                                currentHeader = h
                                currentGroup = mutableListOf(q)
                            }
                        } else {
                            if (currentGroup.isNotEmpty()) {
                                grouped.add(Pair(currentHeader, currentGroup))
                                currentGroup = mutableListOf()
                            }
                            currentHeader = null
                            grouped.add(Pair(null, listOf(q)))
                        }
                    }
                    if (currentGroup.isNotEmpty()) {
                        grouped.add(Pair(currentHeader, currentGroup))
                    }
                    
                    grouped.forEach { (header, qs) ->
                        if (header != null && qs.size > 1) {
                            item(key = "group_${qs.first().globalId}") {
                                BookmarkPassageGroupCard(qs, header, viewModel)
                            }
                        } else {
                            items(qs, key = { it.globalId }) { q ->
                                BookmarkQuestionCard(q, viewModel)
                            }
                        }
                    }
                }
            }
        }
    
        }
    }
}

@Composable
fun BookmarkQuestionCard(q: com.example.data.QuestionEntity, viewModel: com.example.ui.MainViewModel, header: String?) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column {
        if (!header.isNullOrBlank()) {
            Text(header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        com.example.ui.components.RichText(text = q.question, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = { expanded = !expanded })
                        
                        if (q.format == "table" && q.tableData != null) {
                            val tableData = androidx.compose.runtime.remember(q.id) {
                        try {
                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.tableData, tableType)
                        } catch (e: Exception) {
                            null
                        }
                    }
                            if (tableData != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                com.example.ui.components.TableRenderer(tableData)
                            }
                        }
                        
                        if (!q.bottomText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            com.example.ui.components.RichText(text = q.bottomText, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = { expanded = !expanded })
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Path: ${q.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.example.ui.components.ExamTagsRow(
                            examsJson = q.exams,
                            legacyExam = q.exam,
                            legacyYear = q.year,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleBookmark(q.bookId, q.id) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Remove Bookmark",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (!q.img.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val imgPath = q.img!!
                    var imageModel by androidx.compose.runtime.remember(imgPath) { androidx.compose.runtime.mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(imgPath) {
                        if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                            imageModel = imgPath
                        } else {
                            imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                                val f2 = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Quizer/$imgPath")
                                when {
                                    f1.exists() -> f1.absolutePath
                                    f2.exists() -> f2.absolutePath
                                    else -> null
                                }
                            }
                        }
                    }
                    if (imageModel != null) {
                        coil.compose.SubcomposeAsyncImage(
                            model = imageModel,
                            contentDescription = "Question Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        ) {
                        val state = painter.state
                        when (state) {
                            is coil.compose.AsyncImagePainter.State.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }
                            is coil.compose.AsyncImagePainter.State.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
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
                } // End of if (imageModel != null)
                }
                
                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Options:", style = MaterialTheme.typography.labelMedium)
                    val options = androidx.compose.runtime.remember(q.id) {
                        try {
                            com.example.data.SharedGson.normal.fromJson(q.options, Array<String>::class.java).toList()
                        } catch(e: Exception) { emptyList() }
                    }
                    options.forEachIndexed { index, opt ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (index == q.correctIndex) androidx.compose.material.icons.Icons.Filled.Check else androidx.compose.material.icons.Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (index == q.correctIndex) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            com.example.ui.components.RichText(text = opt, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = { expanded = !expanded })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (q.explanation.isNotBlank() || !q.detailedExplanation.isNullOrBlank() || !q.explanationTable.isNullOrBlank()) {
                        if (q.explanation.isNotBlank()) {
                            Text("Explanation:", style = MaterialTheme.typography.labelMedium)
                            com.example.ui.components.RichText(text = q.explanation, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = { expanded = !expanded })
                        }
                        if (!q.detailedExplanation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Detailed:", style = MaterialTheme.typography.labelMedium)
                            com.example.ui.components.RichText(text = q.detailedExplanation, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = { expanded = !expanded })
                        }
                        if (!q.explanationTable.isNullOrBlank()) {
                            val expTableData = androidx.compose.runtime.remember(q.id) {
                                try {
                                    val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                                    com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.explanationTable, tableType)
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



@Composable
fun BookmarkPassageGroupCard(qs: List<com.example.data.QuestionEntity>, header: String, viewModel: com.example.ui.MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            com.example.ui.components.ExpandableQuestionHeader(
                headerText = header,
                textColor = MaterialTheme.colorScheme.onSurface,
                isQuizMode = true,
                initiallyExpanded = true,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            qs.forEachIndexed { index, q ->
                BookmarkQuestionContent(q = q, viewModel = viewModel)
                if (index < qs.size - 1) {
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun BookmarkQuestionCard(q: com.example.data.QuestionEntity, viewModel: com.example.ui.MainViewModel) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize()
    ) {
        Column(Modifier.padding(16.dp)) {
            val header = q.questionHeader
            if (!header.isNullOrBlank()) {
                com.example.ui.components.ExpandableQuestionHeader(
                    headerText = header,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    isQuizMode = true,
                    initiallyExpanded = true,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            BookmarkQuestionContent(q, viewModel, expanded) { expanded = !expanded }
        }
    }
}

@Composable
fun BookmarkQuestionContent(q: com.example.data.QuestionEntity, viewModel: com.example.ui.MainViewModel, isExpanded: Boolean? = null, onExpandedToggle: (() -> Unit)? = null) {
    var internalExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val expanded = isExpanded ?: internalExpanded
    val toggle = onExpandedToggle ?: { internalExpanded = !internalExpanded }
    
    Column(modifier = Modifier.fillMaxWidth().clickable { toggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.example.ui.components.RichText(text = q.question, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = toggle)
                    
                if (q.format == "table" && q.tableData != null) {
                    val tableData = androidx.compose.runtime.remember(q.id) {
                try {
                    val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                    com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.tableData, tableType)
                } catch (e: Exception) {
                    null
                }
            }
                    if (tableData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.TableRenderer(tableData)
                    }
                }
                    
                if (!q.bottomText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.ui.components.RichText(text = q.bottomText, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = toggle)
                }
                    
                Spacer(modifier = Modifier.height(4.dp))
                Text("Path: ${q.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.example.ui.components.ExamTagsRow(
                    examsJson = q.exams,
                    legacyExam = q.exam,
                    legacyYear = q.year,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.toggleBookmark(q.bookId, q.id) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Remove Bookmark",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
           
        if (!q.img.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            val imgPath = q.img!!
            var imageModel by androidx.compose.runtime.remember(imgPath) { androidx.compose.runtime.mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(imgPath) {
                if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                    imageModel = imgPath
                } else {
                    imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                        val f2 = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Quizer/$imgPath")
                        when {
                            f1.exists() -> f1.absolutePath
                            f2.exists() -> f2.absolutePath
                            else -> null
                        }
                    }
                }
            }
            if (imageModel != null) {
                coil.compose.SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = "Question Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                ) {
                val state = painter.state
                when (state) {
                    is coil.compose.AsyncImagePainter.State.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    is coil.compose.AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium),
                            contentAlignment = androidx.compose.ui.Alignment.Center
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
        } // End of if (imageModel != null)
        }
           
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Options:", style = MaterialTheme.typography.labelMedium)
            val options = androidx.compose.runtime.remember(q.id) {
                try {
                    com.example.data.SharedGson.normal.fromJson(q.options, Array<String>::class.java).toList()
                } catch(e: Exception) { emptyList() }
            }
            options.forEachIndexed { index, opt ->
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (index == q.correctIndex) androidx.compose.material.icons.Icons.Filled.Check else androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (index == q.correctIndex) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.example.ui.components.RichText(text = opt, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = toggle)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (q.explanation.isNotBlank() || !q.detailedExplanation.isNullOrBlank() || !q.explanationTable.isNullOrBlank()) {
                if (q.explanation.isNotBlank()) {
                    Text("Explanation:", style = MaterialTheme.typography.labelMedium)
                    com.example.ui.components.RichText(text = q.explanation, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = toggle)
                }
                if (!q.detailedExplanation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Detailed:", style = MaterialTheme.typography.labelMedium)
                    com.example.ui.components.RichText(text = q.detailedExplanation, color = MaterialTheme.colorScheme.onSurface, isSelectable = expanded, onClick = toggle)
                }
                if (!q.explanationTable.isNullOrBlank()) {
                    val expTableData = androidx.compose.runtime.remember(q.id) {
                        try {
                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.explanationTable, tableType)
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
