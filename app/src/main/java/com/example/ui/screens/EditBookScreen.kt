package com.example.ui.screens
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import com.example.ui.components.AiAssistantBottomSheet


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import com.example.ui.components.RichTextEditor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuestionEntity
import com.example.data.resolveHeaders
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {}
) {
    val currentBookId by viewModel.currentBookId.collectAsState()
    var questions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var selectedQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
    var showBulkHeaderDialog by remember { mutableStateOf(false) }
    var showBulkPathDialog by remember { mutableStateOf(false) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var showRestorePointsDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showHierarchyDialog by remember { mutableStateOf(false) }
    var hasGlobalUnsavedChanges by remember { mutableStateOf(false) }
    
    
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadQuestions() {
        if (currentBookId != "") {
            coroutineScope.launch(Dispatchers.IO) {
                questions = viewModel.repository.getQuestionsForBookSync(currentBookId)
            }
        }
    }
    
    fun autoSaveBook(changeName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            // 1. Create restore point
            createRestorePoint(context, viewModel.repository, currentBookId, changeName)
            // 2. Export and save back to original file if possible
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val targetFile = java.io.File(basePath, "Quizer/${currentBookId}.qbook")
            val res = exportQBook(context, viewModel.repository, currentBookId, targetFile)
            hasGlobalUnsavedChanges = false
            // snackbarHostState.showSnackbar("Auto-saved: $changeName")
        }
    }
    


    LaunchedEffect(currentBookId) {
        loadQuestions()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        
        floatingActionButton = {
            val pending = viewModel.pendingUpdates.collectAsState().value[currentBookId]
            val isAiProcessing = viewModel.aiProcessingStatus.collectAsState().value[currentBookId] != null
            BadgedBox(
                badge = {
                    if (isAiProcessing) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                            Text("...")
                        }
                    } else if (!pending.isNullOrEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("${pending.size}")
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 32.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showAiAssistant = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI Assistant")
                }
            }
        }
,
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            createRestorePoint(context, viewModel.repository, currentBookId, "Manual Save")
                            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                            val targetFile = java.io.File(basePath, "Quizer/${currentBookId}.qbook")
                            val res = exportQBook(context, viewModel.repository, currentBookId, targetFile)
                            hasGlobalUnsavedChanges = false
                            if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticSuccess()
                            snackbarHostState.showSnackbar(res ?: "Saved successfully")
                        }
                    }, enabled = hasGlobalUnsavedChanges) {
                        Icon(Icons.Default.Save, "Save and Export")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Book Name") },
                                onClick = {
                                    showMenu = false
                                    showEditNameDialog = true
                                }
                            )
                                                        DropdownMenuItem(
                                text = { Text("Hierarchy Editor") },
                                onClick = {
                                    showMenu = false
                                    showHierarchyDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Batch Header Editor") },
                                onClick = {
                                    showMenu = false
                                    showBulkHeaderDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Batch Path Editor") },
                                onClick = {
                                    showMenu = false
                                    showBulkPathDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Batch Category Editor") },
                                onClick = {
                                    showMenu = false
                                    showBulkCategoryDialog = true
                                }
                            )
                                                        DropdownMenuItem(
                                text = { Text("Export") },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                                        val booksDir = java.io.File(basePath, "Quizer/qbooks")
                                        if (!booksDir.exists()) booksDir.mkdirs()
                                        val book = viewModel.repository.getBookSync(currentBookId)
                                        val safeName = (book?.name ?: "ExportedBook").replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                        val targetFile = java.io.File(booksDir, "${safeName}.qbook")
                                        val res = exportQBook(context, viewModel.repository, currentBookId, targetFile)
                                        snackbarHostState.showSnackbar(res ?: "Exported successfully")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Points") },
                                onClick = {
                                    showMenu = false
                                    showRestorePointsDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalItems = questions.size
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = questions.sortedBy { it.id }, key = { it.globalId }) { question ->
                    EditQuestionItem(
                        question = question,
                        onClick = { selectedQuestion = question }
                    )
                }
            }
            
            // Fast Scroller
            if (totalItems > 0) {
                val trackHeight = constraints.maxHeight.toFloat()
                val thumbHeight = 40.dp
                val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }
                
                val firstVisible = listState.firstVisibleItemIndex
                var dragY by remember { mutableStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                
                val thumbY = if (isDragging) {
                    dragY.coerceIn(0f, trackHeight - thumbHeightPx)
                } else {
                    ((firstVisible.toFloat() / maxOf(1, totalItems - 1)) * (trackHeight - thumbHeightPx)).coerceIn(0f, trackHeight - thumbHeightPx)
                }
                
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val y = offset.y - (thumbHeightPx / 2)
                                    val targetIndex = ((y / (trackHeight - thumbHeightPx)) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragY = offset.y - (thumbHeightPx / 2)
                                },
                                onDragEnd = {
                                     isDragging = false
                                },
                                onDragCancel = {
                                     isDragging = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragY = change.position.y - (thumbHeightPx / 2)
                                    val targetIndex = ((dragY / (trackHeight - thumbHeightPx)) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(16.dp.toPx().toInt(), thumbY.toInt()) }
                            .size(6.dp, thumbHeight)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        
        
        
        if (showAiAssistant) {
            AiAssistantBottomSheet(
                mode = com.example.ui.components.AiChatMode.GLOBAL_EDITOR,
                questions = questions,
                currentBookId = currentBookId,
                viewModel = viewModel,
                onDismiss = { showAiAssistant = false },
                onNavigateToAiSettings = onNavigateToAiSettings,
                onNavigateToUsage = onNavigateToUsage,
                onQuestionsUpdated = { updatedQuestions ->
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.repository.insertQuestions(updatedQuestions)
                        loadQuestions()
                        autoSaveBook("AI Updated ${updatedQuestions.size} questions")
                    }
                },
                onQuestionsDeleted = { deletedQuestions ->
                    coroutineScope.launch(Dispatchers.IO) {
                        for (q in deletedQuestions) {
                            viewModel.repository.deleteQuestion(q)
                        }
                        // Rearrange remaining question IDs
                        val remaining = viewModel.repository.getQuestionsForBookSync(currentBookId).sortedBy { it.id }
                        val rearranged = remaining.mapIndexed { index, q -> q.copy(id = (index + 1).toLong()) }
                        viewModel.repository.deleteQuestionsForBook(currentBookId)
                        viewModel.repository.insertQuestions(rearranged)
                        loadQuestions()
                        autoSaveBook("AI Deleted ${deletedQuestions.size} questions")
                    }
                }
            )
        }


        if (showBulkHeaderDialog) {
            BulkHeaderDialog(
                onDismiss = { showBulkHeaderDialog = false },
                onSave = { header, start, end ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val startIndex = questions.indexOfFirst { it.id == start.toLong() }.takeIf { it != -1 } ?: 0
                        val endIndex = questions.indexOfFirst { it.id == end.toLong() }.takeIf { it != -1 } ?: (questions.size - 1)
                        if (startIndex <= endIndex) {
                            val headerText = header.takeIf { it.isNotBlank() }
                            val headerJson = if (headerText != null) "{\"startId\": $start, \"endId\": $end, \"text\": \"${headerText.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else null
                            
                            for (i in startIndex..endIndex) {
                                val q = questions[i]
                                viewModel.repository.updateQuestion(q.copy(questionHeader = headerJson))
                            }
                            
                            loadQuestions()
                            hasGlobalUnsavedChanges = true
                            autoSaveBook("Applied bulk header")
                        }
                        showBulkHeaderDialog = false
                    }
                }
            )
        }



        if (showBulkPathDialog) {
            BulkPathDialog(
                onDismiss = { showBulkPathDialog = false },
                onSave = { path, start, end ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val startIndex = questions.indexOfFirst { it.id == start.toLong() }.takeIf { it != -1 } ?: 0
                        val endIndex = questions.indexOfFirst { it.id == end.toLong() }.takeIf { it != -1 } ?: (questions.size - 1)
                        if (startIndex <= endIndex) {
                            for (i in startIndex..endIndex) {
                                val q = questions[i]
                                viewModel.repository.updateQuestion(q.copy(path = path.takeIf { it.isNotBlank() } ?: q.path))
                            }
                            loadQuestions()
                            hasGlobalUnsavedChanges = true
                            autoSaveBook("Applied bulk path")
                        }
                        showBulkPathDialog = false
                    }
                }
            )
        }
        
        if (showBulkCategoryDialog) {
            BulkCategoryDialog(
                onDismiss = { showBulkCategoryDialog = false },
                onSave = { category, start, end ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val startIndex = questions.indexOfFirst { it.id == start.toLong() }.takeIf { it != -1 } ?: 0
                        val endIndex = questions.indexOfFirst { it.id == end.toLong() }.takeIf { it != -1 } ?: (questions.size - 1)
                        if (startIndex <= endIndex) {
                            for (i in startIndex..endIndex) {
                                val q = questions[i]
                                viewModel.repository.updateQuestion(q.copy(category = category.takeIf { it.isNotBlank() } ?: q.category))
                            }
                            loadQuestions()
                            hasGlobalUnsavedChanges = true
                            autoSaveBook("Applied bulk category")
                        }
                        showBulkCategoryDialog = false
                    }
                }
            )
        }
        
        if (selectedQuestion != null) {
            EditQuestionDialog(
                viewModel = viewModel,
                question = selectedQuestion!!,
                bookId = currentBookId,
                onDismiss = { selectedQuestion = null },
                onSave = { updatedQuestion, startId, endId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        var finalQuestion = updatedQuestion
                        if (finalQuestion.img?.startsWith("http") == true) {
                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, finalQuestion.img!!)
                            if (downloaded != null) finalQuestion = finalQuestion.copy(img = downloaded)
                        }
                        if (finalQuestion.explanationImage?.startsWith("http") == true) {
                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, finalQuestion.explanationImage!!)
                            if (downloaded != null) finalQuestion = finalQuestion.copy(explanationImage = downloaded)
                        }
                        if (startId != null && endId != null) {
                            val sIdx = questions.indexOfFirst { it.id == startId }.takeIf { it != -1 } ?: 0
                            val eIdx = questions.indexOfFirst { it.id == endId }.takeIf { it != -1 } ?: (questions.size - 1)
                            if (sIdx <= eIdx) {
                            val text = finalQuestion.questionHeader ?: ""
                            val headerText = text.takeIf { it.isNotBlank() }
                            val headerJson = if (headerText != null) "{\"startId\": $startId, \"endId\": $endId, \"text\": \"${headerText.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else null
                            
                            for (i in sIdx..eIdx) {
                                val q = questions[i]
                                val updatedQ = if (q.id == finalQuestion.id) finalQuestion.copy(questionHeader = headerJson) else q.copy(questionHeader = headerJson)
                                viewModel.repository.updateQuestion(updatedQ)
                            }
                            if (questions.indexOfFirst { it.id == finalQuestion.id } !in sIdx..eIdx) {
                                viewModel.repository.updateQuestion(finalQuestion)
                            }
                            }
                        } else {
                            viewModel.repository.updateQuestion(finalQuestion)
                        }
                        
                        loadQuestions()
                        selectedQuestion = null
                        hasGlobalUnsavedChanges = true
                        autoSaveBook("Edited Q${finalQuestion.id}")
                    }
                }
            )
        }
        
        if (showRestorePointsDialog) {
            RestorePointsDialog(
                bookId = currentBookId,
                viewModel = viewModel,
                onDismiss = { showRestorePointsDialog = false },
                onRestored = { 
                    showRestorePointsDialog = false
                    loadQuestions()
                }
            )
        }
        
                if (showEditNameDialog) {
            val currentBook = viewModel.books.collectAsState().value.find { it.id == currentBookId }
            var newName by remember { mutableStateOf(currentBook?.name ?: "") }
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                title = { Text("Edit Book Name") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Book Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameBook(currentBookId, newName)
                            showEditNameDialog = false
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showHierarchyDialog) {
            val currentBook = viewModel.books.collectAsState().value.find { it.id == currentBookId }
            val initialLabels = androidx.compose.runtime.remember(currentBook) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    com.example.data.SharedGson.normal.fromJson<List<String>>(currentBook?.hierarchyLabels ?: "[]", type) ?: emptyList()
                } catch(e: Exception) { emptyList() }
            }
            var labels by remember { mutableStateOf(initialLabels.toMutableList()) }
            
            AlertDialog(
                onDismissRequest = { showHierarchyDialog = false },
                title = { Text("Hierarchy Editor") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Edit path hierarchy labels (e.g. Topic, Subtopic). These are used to label the headers in the reader.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        labels.forEachIndexed { index, label ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = label,
                                    onValueChange = { newLabel -> 
                                        val newList = labels.toMutableList()
                                        newList[index] = newLabel
                                        labels = newList
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Level ${index + 1}") }
                                )
                                IconButton(onClick = { 
                                    val newList = labels.toMutableList()
                                    newList.removeAt(index)
                                    labels = newList
                                }) {
                                    Icon(Icons.Default.Delete, "Remove")
                                }
                            }
                        }
                        TextButton(onClick = { 
                            val newList = labels.toMutableList()
                            newList.add("Level ${newList.size + 1}")
                            labels = newList
                        }) {
                            Text("+ Add Level")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val json = com.example.data.SharedGson.normal.toJson(labels)
                        if (currentBook != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                viewModel.repository.insertBook(currentBook.copy(hierarchyLabels = json))
                            }
                        }
                        showHierarchyDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHierarchyDialog = false }) { Text("Cancel") }
                }
            )
        }
    
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditQuestionDialog(
    viewModel: MainViewModel,
    question: QuestionEntity,
    bookId: String,
    isReviewMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (QuestionEntity, Long?, Long?) -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initialHeaderObj = androidx.compose.runtime.remember(question.questionHeader) {
        val raw = question.questionHeader ?: ""
        if (raw.trim().startsWith("{")) {
            try {
                com.example.data.SharedGson.normal.fromJson(raw, com.example.data.QBookHeader::class.java)
            } catch(e: Exception) { null }
        } else null
    }
    var qHeader by remember { mutableStateOf(initialHeaderObj?.text ?: question.questionHeader ?: "") }
    var hStartId by remember { mutableStateOf(initialHeaderObj?.startId?.toString() ?: question.id.toString()) }
    var hEndId by remember { mutableStateOf(initialHeaderObj?.endId?.toString() ?: question.id.toString()) }
    var qText by remember { mutableStateOf(question.question) }
    var qFormat by remember { mutableStateOf(question.format ?: "") }
    var qTableData by remember { mutableStateOf(question.tableData ?: "") }
    var qBottomText by remember { mutableStateOf(question.bottomText ?: "") }
    var qExams by remember { mutableStateOf(question.exams ?: "") }
    var qExam by remember { mutableStateOf(question.exam) }
    var qYear by remember { mutableStateOf(question.year) }
    var qExplanation by remember { mutableStateOf(question.explanation) }
    var qDetailedExplanation by remember { mutableStateOf(question.detailedExplanation ?: "") }
    var qExplanationTable by remember { mutableStateOf(question.explanationTable ?: "") }
    var qOptions by remember { mutableStateOf(question.options) }
    var qExtraOptions by remember { mutableStateOf(question.extraOptions) }
    var qCorrectIndex by remember { mutableStateOf(question.correctIndex.toString()) }
    var qImg by remember { mutableStateOf(question.img ?: "") }
    var qExplanationImage by remember { mutableStateOf(question.explanationImage ?: "") }
    var showAiAssistant by remember { mutableStateOf(false) }
    
    // Simple way to handle image picking and copying
    fun copyUriToImages(uri: Uri): String? {
        try {
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val quizerDir = File(android.os.Environment.getExternalStorageDirectory(), "Quizer")
            val bookImagesDir = File(quizerDir, "$bookId/images")
            if (!bookImagesDir.exists()) bookImagesDir.mkdirs()
            
            val destFile = File(bookImagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return fileName
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    val qImgLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                val fileName = copyUriToImages(uriContent)
                if (fileName != null) {
                    qImg = "images/$fileName"
                }
            }
        }
    }

    val explImgLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                val fileName = copyUriToImages(uriContent)
                if (fileName != null) {
                    qExplanationImage = "images/$fileName"
                }
            }
        }
    }

    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceVariantArgb = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val cropBgColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f).toArgb()

    val cropOptions = CropImageOptions(
        imageSourceIncludeGallery = true,
        imageSourceIncludeCamera = false,
        backgroundColor = android.graphics.Color.parseColor("#80000000"),
        activityBackgroundColor = android.graphics.Color.parseColor("#121212"),
        toolbarColor = android.graphics.Color.parseColor("#121212"),
        toolbarTitleColor = android.graphics.Color.WHITE,
        toolbarBackButtonColor = android.graphics.Color.WHITE,
        activityMenuIconColor = android.graphics.Color.WHITE,
        activityMenuTextColor = android.graphics.Color.WHITE,
        guidelinesColor = primaryArgb,
        borderLineColor = primaryArgb,
        borderCornerColor = primaryArgb
    )

    val getExistingUri = { path: String ->
        if (path.isNotBlank()) {
            if (path.startsWith("http")) android.net.Uri.parse(path)
            else {
                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$bookId/images/${path.substringAfterLast("/")}")
                val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$path")
                when {
                    f1.exists() -> android.net.Uri.fromFile(f1)
                    f2.exists() -> android.net.Uri.fromFile(f2)
                    else -> null
                }
            }
        } else null
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            floatingActionButton = {
                if (!isReviewMode) {
                    FloatingActionButton(
                        onClick = { showAiAssistant = true },
                        modifier = Modifier.padding(bottom = 32.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.Filled.AutoAwesome, "AI Assistant")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text(if (isReviewMode) "Review AI Changes" else "Edit Question ${question.id}") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        val legacyOptions = androidx.compose.runtime.remember(qOptions) {
                            val gson = com.example.data.SharedGson.normal
                            val strOptionsType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                            try { gson.fromJson<List<String>>(qOptions, strOptionsType) ?: emptyList() } catch(e: Exception) { emptyList() }
                        }
                        
                        val advancedList = legacyOptions.mapIndexed { idx, txt -> 
                            com.example.data.QBookOption(id = ('A' + idx).toString(), text = txt)
                        }
                        
                        val correctIndex = qCorrectIndex.toIntOrNull() ?: 0
                        val correctIds = if (correctIndex in advancedList.indices) listOf(advancedList[correctIndex].id) else emptyList()
                        
                        val updated = question.copy(
                            question = qText,
                            format = qFormat.takeIf { it.isNotBlank() },
                            tableData = qTableData.takeIf { it.isNotBlank() },
                            bottomText = qBottomText.takeIf { it.isNotBlank() },
                            options = qOptions,
                            extraOptions = qExtraOptions,
                            correctIndex = correctIndex,
                            explanation = qExplanation,
                            detailedExplanation = qDetailedExplanation.takeIf { it.isNotBlank() },
                            questionHeader = qHeader.takeIf { it.isNotBlank() },
                            img = qImg.takeIf { it.isNotBlank() },
                            explanationImage = qExplanationImage.takeIf { it.isNotBlank() },
                            advancedOptions = com.example.data.SharedGson.normal.toJson(advancedList),
                            advancedCorrectIds = com.example.data.SharedGson.normal.toJson(correctIds),
                            exams = qExams.takeIf { it.isNotBlank() },
                            explanationTable = qExplanationTable.takeIf { it.isNotBlank() },
                            exam = qExam,
                            year = qYear
                        )
                        val hasChanges = updated != question || hStartId.isNotBlank() || hEndId.isNotBlank()
                        TextButton(onClick = {
                            onSave(updated, hStartId.toLongOrNull(), hEndId.toLongOrNull())
                        }, enabled = hasChanges) {
                            Text(if (isReviewMode) "Apply" else "Save")
                        }
                    }
                )
            }
        ) { padding ->
            com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                RichTextEditor(
                    value = qHeader,
                    onValueChange = { qHeader = it },
                    label = "Header (Optional)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = hStartId,
                        onValueChange = { hStartId = it },
                        label = { Text("startId") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = hEndId,
                        onValueChange = { hEndId = it },
                        label = { Text("endId") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qText,
                    onValueChange = { qText = it },
                    label = "Question Text",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = qFormat,
                    onValueChange = { qFormat = it },
                    label = { Text("Format (e.g. table)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qTableData,
                    onValueChange = { qTableData = it },
                    label = "Table Data (JSON Array of String Arrays)",
                    modifier = Modifier.fillMaxWidth()
                )
                if (qFormat == "table" && qTableData.isNotBlank()) {
                    val parsedData: List<List<String>>? = androidx.compose.runtime.remember(qTableData) {
                        try {
                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(qTableData, tableType)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (parsedData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Table Preview:", style = MaterialTheme.typography.labelMedium)
                        com.example.ui.components.TableRenderer(parsedData)
                    } else {
                        Text("Invalid JSON for Table Data", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qBottomText,
                    onValueChange = { qBottomText = it },
                    label = "Bottom Text (Below table, above options)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qOptions,
                    onValueChange = { qOptions = it },
                    label = "Options (JSON string array)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qExtraOptions,
                    onValueChange = { qExtraOptions = it },
                    label = "Extra Options (JSON string array)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = qCorrectIndex,
                    onValueChange = { qCorrectIndex = it },
                    label = { Text("Correct Index (0-based)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (qExam.isNotBlank() || qYear.isNotBlank()) {
                    OutlinedTextField(
                        value = qExam,
                        onValueChange = { qExam = it },
                        label = { Text("Legacy Exam") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qYear,
                        onValueChange = { qYear = it },
                        label = { Text("Legacy Year") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    RichTextEditor(
                        value = qExams,
                        onValueChange = { qExams = it },
                        label = "Exams (JSON Array)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qExplanation,
                    onValueChange = { qExplanation = it },
                    label = "Brief Explanation",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qExplanationTable,
                    onValueChange = { qExplanationTable = it },
                    label = "Explanation Table (JSON Array of String Arrays)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                RichTextEditor(
                    value = qDetailedExplanation,
                    onValueChange = { qDetailedExplanation = it },
                    label = "Detailed Explanation",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = qImg,
                        onValueChange = { qImg = it },
                        label = { Text("Question Image (URL or path)") },
                        modifier = Modifier.weight(1f)
                    )
                    if (qImg.isNotBlank()) {
                        IconButton(onClick = { qImgLauncher.launch(CropImageContractOptions(uri = getExistingUri(qImg), cropImageOptions = cropOptions)) }) {
                            Icon(Icons.Default.Edit, "Edit Image")
                        }
                    }
                    IconButton(onClick = { qImgLauncher.launch(CropImageContractOptions(uri = null, cropImageOptions = cropOptions)) }) {
                        Icon(Icons.Default.Image, "Pick Image")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = qExplanationImage,
                        onValueChange = { qExplanationImage = it },
                        label = { Text("Explanation Image (URL or path)") },
                        modifier = Modifier.weight(1f)
                    )
                    if (qExplanationImage.isNotBlank()) {
                        IconButton(onClick = { explImgLauncher.launch(CropImageContractOptions(uri = getExistingUri(qExplanationImage), cropImageOptions = cropOptions)) }) {
                            Icon(Icons.Default.Edit, "Edit Image")
                        }
                    }
                    IconButton(onClick = { explImgLauncher.launch(CropImageContractOptions(uri = null, cropImageOptions = cropOptions)) }) {
                        Icon(Icons.Default.Image, "Pick Image")
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
                if (showAiAssistant) {
                    AiAssistantBottomSheet(
                        mode = com.example.ui.components.AiChatMode.SINGLE_EDITOR,
                        questions = listOf(question),
                        currentBookId = bookId,
                        viewModel = viewModel,
                        onDismiss = { showAiAssistant = false },
                        onNavigateToAiSettings = onNavigateToAiSettings,
                onNavigateToUsage = onNavigateToUsage,
                        onQuestionsUpdated = { updatedQuestions ->
                            if (updatedQuestions.isNotEmpty()) {
                                onSave(updatedQuestions.first(), null, null)
                            }
                        },
                        onQuestionsDeleted = { deletedQuestions ->
                            // Delete single question not typically done via this sheet but handled just in case
                            viewModel.viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                for (q in deletedQuestions) {
                                    viewModel.repository.deleteQuestion(q)
                                }
                                showAiAssistant = false
                                // A hack to force navigate back
                                onSave(question, null, null) 
                            }
                        }
                    )
                }
            }
        
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkHeaderDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit
) {
    var headerText by remember { mutableStateOf("") }
    var startNum by remember { mutableStateOf("") }
    var endNum by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Apply Header") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = startNum, onValueChange = { startNum = it }, label = { Text("Start Q#") }, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = endNum, onValueChange = { endNum = it }, label = { Text("End Q#") }, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
                Spacer(Modifier.height(8.dp))
                com.example.ui.components.RichTextEditor(value = headerText, onValueChange = { headerText = it }, label = "Header Text", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val hasInput = headerText.isNotBlank() && startNum.isNotBlank() && endNum.isNotBlank()
            TextButton(onClick = { onSave(headerText, startNum.toIntOrNull() ?: 1, endNum.toIntOrNull() ?: 1) }, enabled = hasInput) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestorePointsDialog(
    bookId: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var originalFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
        var foundFile = File(basePath, "Quizer/$bookId.qbook")
        if (!foundFile.exists()) {
            // Find bundled or renamed original
            val quizerDir = File(basePath, "Quizer")
            val possibleFiles = quizerDir.listFiles { file -> file.isFile && file.name.endsWith(".qbook") } ?: emptyArray()
            for (pf in possibleFiles) {
                try {
                    java.util.zip.ZipFile(pf).use { zip ->
                        val metaEntry = zip.getEntry("book_meta.json")
                        if (metaEntry != null) {
                            val reader = zip.getInputStream(metaEntry).bufferedReader()
                            val jsonStr = com.example.data.DataParser.autoFixJson(reader.readText())
                            val meta = com.example.data.SharedGson.lenient.fromJson(jsonStr, com.example.data.QBookMeta::class.java)
                            if (meta?.id == bookId) {
                                foundFile = pf
                            }
                        }
                    }
                } catch(e: Exception){}
                if (foundFile.exists()) break
            }
        }
        originalFile = foundFile.takeIf { it.exists() }
        
        val restoreDir = File(basePath, "Quizer/RestorePoints/$bookId")
        if (restoreDir.exists()) {
            files = restoreDir.listFiles()?.filter { it.name.endsWith(".qbook") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Points") },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    if (originalFile != null) {
                        item {
                            ListItem(
                                headlineContent = { Text("Original Import", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                                modifier = Modifier.clickable {
                                    isLoading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        viewModel.resetBookToInitial(context, bookId) {
                                            onRestored()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    if (files.isEmpty()) {
                        item {
                            Text("No restore points available yet.")
                        }
                    } else {
                        items(files, key = { it.absolutePath }) { file ->
                            val date = java.text.SimpleDateFormat("MMM dd, HH:mm:ss").format(java.util.Date(file.lastModified()))
                            val namePart = file.name.substringAfter("_").removeSuffix(".qbook").replace("_", " ")
                            ListItem(
                                headlineContent = { Text(namePart) },
                                supportingContent = { Text(date) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        file.delete()
                                        val restoreDir = File(android.os.Environment.getExternalStorageDirectory().absolutePath, "Quizer/RestorePoints/$bookId")
                                        files = restoreDir.listFiles()?.filter { it.name.endsWith(".qbook") }?.sortedByDescending { it.lastModified() } ?: emptyList()
                                    }) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Close, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    isLoading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val tempFile = File.createTempFile("restore", ".qbook", context.cacheDir)
                                        file.copyTo(tempFile, overwrite = true)
                                        val idStr = com.example.data.DataParser.parseAndInsertFromQBook(context, viewModel.repository, tempFile, true)
                                        if (idStr != null && !idStr.startsWith("Error")) {
                                            onRestored()
                                        } else {
                                            isLoading = false
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (files.isNotEmpty() || originalFile != null) {
                TextButton(
                    onClick = {
                        isLoading = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val restoreDir = File(android.os.Environment.getExternalStorageDirectory().absolutePath, "Quizer/RestorePoints/$bookId")
                            if (restoreDir.exists()) {
                                restoreDir.deleteRecursively()
                            }
                            
                            val originalDir = File(android.os.Environment.getExternalStorageDirectory().absolutePath, "Quizer/Original")
                            if (!originalDir.exists()) {
                                originalDir.mkdirs()
                            }
                            val targetFile = File(originalDir, "$bookId.qbook")
                            exportQBook(context, viewModel.repository, bookId, targetFile)
                            
                            launch(Dispatchers.Main) {
                                files = emptyList()
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text("Permanent Apply Changes", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}
@Composable
fun EditQuestionItem(question: QuestionEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Path: ${question.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.Edit, "Edit Question")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text("Q${question.id}. ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                com.example.ui.components.SmartRichText(text = question.question, color = MaterialTheme.colorScheme.onSurface)
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
                com.example.ui.components.SmartRichText(text = question.bottomText!!, color = MaterialTheme.colorScheme.onSurface)
            }
            if (!question.img.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val imgPath = question.img
                val imageModel = remember(imgPath) {
                    if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                        imgPath
                    } else {
                        val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${question.bookId}/images/${imgPath.substringAfterLast("/")}")
                        val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                        if (f1.exists()) f1 else if (f2.exists()) f2 else "file:///android_asset/data/$imgPath"
                    }
                }
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(false)
                        .build(),
                    contentDescription = "Question Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Inside
                )
            }

            
            val optionsList = remember(question) {
                val gson = com.example.data.SharedGson.normal
                val type = object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>() {}.type
                val advanced = gson.fromJson<List<com.example.data.QBookOption>>(question.advancedOptions ?: "[]", type)
                if (advanced != null && advanced.isNotEmpty()) {
                    advanced.map { it.text }
                } else {
                    try {
                        val strType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>>(question.options, strType)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            if (optionsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    optionsList.forEachIndexed { idx, opt ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("${(65 + idx).toChar()}. ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            com.example.ui.components.SmartRichText(text = opt, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit
) {
    var catText by remember { mutableStateOf("") }
    var startNum by remember { mutableStateOf("") }
    var endNum by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Category Editor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = startNum,
                    onValueChange = { startNum = it },
                    label = { Text("Start Question ID") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = endNum,
                    onValueChange = { endNum = it },
                    label = { Text("End Question ID") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = catText,
                    onValueChange = { catText = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = startNum.toIntOrNull()
                val e = endNum.toIntOrNull()
                if (s != null && e != null) {
                    onSave(catText, s, e)
                }
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkPathDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit
) {
    var pathText by remember { mutableStateOf("") }
    var startNum by remember { mutableStateOf("") }
    var endNum by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Path Editor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = startNum,
                    onValueChange = { startNum = it },
                    label = { Text("Start Question ID") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = endNum,
                    onValueChange = { endNum = it },
                    label = { Text("End Question ID") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Path (e.g. Chapter 1/Topic 2)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = startNum.toIntOrNull()
                val e = endNum.toIntOrNull()
                if (s != null && e != null) {
                    onSave(pathText, s, e)
                }
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
