package com.example.ui
import com.example.data.resolveHeaders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    val repository: QuizRepository,
    val settingsManager: SettingsManager,
    val soundHapticManager: SoundHapticManager
) : ViewModel() {
    private val _isAppReady = MutableStateFlow(false)
    val isAppReady = _isAppReady.asStateFlow()
    
    private val _showBackupRestoreDialog = MutableStateFlow(false)
    val showBackupRestoreDialog = _showBackupRestoreDialog.asStateFlow()

    fun initializeApp(context: android.content.Context) {
        if (_isAppReady.value) return
        viewModelScope.launch {
            val prefs = com.example.data.SecurePrefs.get(context)
            val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
            
            if (isFirstLaunch && com.example.data.BackupManager.hasBackup()) {
                _showBackupRestoreDialog.value = true
                return@launch
            }
            
            finishInitialization(context)
        }
    }

    private suspend fun finishInitialization(context: android.content.Context) {
        val startTime = System.currentTimeMillis()
        com.example.data.DataParser.loadInitialData(context, repository)
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = 3000L - elapsedTime
        if (remainingTime > 0) {
            kotlinx.coroutines.delay(remainingTime)
        }
        val prefs = com.example.data.SecurePrefs.get(context)
        prefs.edit().putBoolean("is_first_launch", false).apply()
        _isAppReady.value = true
    }
    
    fun onRestoreBackupDecision(context: android.content.Context, restore: Boolean) {
        _showBackupRestoreDialog.value = false
        viewModelScope.launch {
            if (restore) {
                val success = com.example.data.BackupManager.restoreBackup(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (success) {
                        android.widget.Toast.makeText(context, "Backup restored successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to restore backup", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                com.example.data.BackupManager.clearBackup()
            }
            finishInitialization(context)
        }
    }
    private var quizGenJob: kotlinx.coroutines.Job? = null
    var pendingQuizGenerationTrigger = true
    var isLearnMode = false
    var isActiveQuizStandardSequence = true

    var currentExamSettings: ExamSettings? = null
    var currentLearnSettings: ExamSettings? = null

    private val _chatHistory = MutableStateFlow<List<com.example.data.ChatMessageEntity>>(emptyList())
    val chatHistory = _chatHistory.asStateFlow()

    private val _pendingUpdates = MutableStateFlow<Map<String, List<com.example.ui.components.PendingQuestionUpdate>>>(emptyMap())
    val pendingUpdates = _pendingUpdates.asStateFlow()
    
    private val _devLogs = MutableStateFlow<List<String>>(emptyList())
    val devLogs = _devLogs.asStateFlow()

    private val _sessionAttachments = MutableStateFlow<Map<String, android.net.Uri>>(emptyMap())
    val sessionAttachments = _sessionAttachments.asStateFlow()

    fun setSessionAttachment(chatContextId: String, uri: android.net.Uri?) {
        val current = _sessionAttachments.value.toMutableMap()
        if (uri == null) {
            current.remove(chatContextId)
        } else {
            current[chatContextId] = uri
        }
        _sessionAttachments.value = current
    }
    
    fun clearDevLogs() {
        _devLogs.value = emptyList()
    }
    
    fun addDevLog(log: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _devLogs.value = _devLogs.value + "[$timestamp] $log"
    }
    
    private val _aiProcessingStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiProcessingStatus = _aiProcessingStatus.asStateFlow()

    private val _aiProcessingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val aiProcessingProgress = _aiProcessingProgress.asStateFlow()
    
    private val _aiPromptTexts = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiPromptTexts = _aiPromptTexts.asStateFlow()

    fun setAiPromptText(chatContextId: String, text: String) {
        val current = _aiPromptTexts.value.toMutableMap()
        current[chatContextId] = text
        _aiPromptTexts.value = current
    }

    private val aiProcessingJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    fun setAiProcessingStatus(chatContextId: String, status: String?, progress: Float = 0f) {
        val current = _aiProcessingStatus.value.toMutableMap()
        val currentProgress = _aiProcessingProgress.value.toMutableMap()
        if (status == null) {
            current.remove(chatContextId)
            currentProgress.remove(chatContextId)
        } else {
            current[chatContextId] = status
            currentProgress[chatContextId] = progress
        }
        _aiProcessingStatus.value = current
        _aiProcessingProgress.value = currentProgress
    }
    
    fun cancelAiProcessing(chatContextId: String) {
        aiProcessingJobs[chatContextId]?.cancel()
        aiProcessingJobs.remove(chatContextId)
        setAiProcessingStatus(chatContextId, null)
        addChatMessage(
            ChatMessageEntity(bookId = chatContextId, role = "model", content = "Operation cancelled by user.", timestamp = System.currentTimeMillis())
        )
    }
    
    fun startAiProcessing(
        context: android.content.Context,
        chatContextId: String,
        prompt: String,
        allQuestions: List<QuestionEntity>,
        mode: com.example.ui.components.AiChatMode,
        currentContextInfo: String,
        pdfUri: android.net.Uri? = null
    ) {
        addChatMessage(
            ChatMessageEntity(bookId = chatContextId, role = "user", content = prompt, timestamp = System.currentTimeMillis())
        )
        setAiProcessingStatus(chatContextId, "Analyzing request...", 0f)
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                var pdfBytes: ByteArray? = null
                var extractedTextFromFile: String? = null
                val attachments = mutableListOf<com.example.data.AiFileAttachment>()
                
                if (pdfUri != null) {
                    val contentResolver = context.contentResolver
                    val mimeType = contentResolver.getType(pdfUri) ?: ""
                    val name = pdfUri.lastPathSegment ?: ""
                    
                    val isZip = mimeType == "application/zip" || mimeType == "application/x-zip-compressed" || name.endsWith(".zip", true)
                    val isText = mimeType.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".md", true)
                    val isImage = mimeType.startsWith("image/")
                    
                    if (isZip) {
                        setAiProcessingStatus(chatContextId, "Extracting ZIP...", 0.1f)
                        val zis = java.util.zip.ZipInputStream(contentResolver.openInputStream(pdfUri))
                        val textBuilder = StringBuilder()
                        val availableImages = mutableListOf<String>()
                        var entry = zis.nextEntry
                        val imagesDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$chatContextId/images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()

                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val entryName = entry.name.substringAfterLast("/")
                                if (entryName.endsWith(".txt", true) || entryName.endsWith(".json", true) || entryName.endsWith(".md", true)) {
                                    val bytes = zis.readBytes()
                                    textBuilder.append(String(bytes)).append("\n\n")
                                } else if (entryName.endsWith(".png", true) || entryName.endsWith(".jpg", true) || entryName.endsWith(".jpeg", true)) {
                                    val file = java.io.File(imagesDir, entryName)
                                    java.io.FileOutputStream(file).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    availableImages.add("images/$entryName")
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                        zis.close()
                        
                        if (textBuilder.isNotEmpty()) {
                            extractedTextFromFile = textBuilder.toString()
                            if (availableImages.isNotEmpty()) {
                                extractedTextFromFile += "\n\n[System Note: The attached ZIP archive contains these image files: ${availableImages.joinToString(", ")}.]"
                            }
                        }
                    } else if (isText) {
                        setAiProcessingStatus(chatContextId, "Reading Text File...", 0.1f)
                        extractedTextFromFile = contentResolver.openInputStream(pdfUri)?.use { String(it.readBytes()) }
                    } else if (isImage) {
                        setAiProcessingStatus(chatContextId, "Reading Image...", 0.1f)
                        val bytes = contentResolver.openInputStream(pdfUri)?.use { it.readBytes() }
                        if (bytes != null) attachments.add(com.example.data.AiFileAttachment(bytes, mimeType))
                    } else {
                        setAiProcessingStatus(chatContextId, "Reading PDF...", 0.1f)
                        pdfBytes = contentResolver.openInputStream(pdfUri)?.use { it.readBytes() }
                    }
                }
                
                val (updates, responseText) = com.example.ui.components.processAiChatCommand(
                    context = context,
                    command = prompt,
                    allQuestions = allQuestions,
                    mode = mode,
                    chatHistory = chatHistory.value,
                    currentContextInfo = currentContextInfo,
                    pdfBytes = pdfBytes,
                    extractedText = extractedTextFromFile,
                    attachments = attachments,
                    onStatusUpdate = { status, progress ->
                        setAiProcessingStatus(chatContextId, status, progress)
                    },
                    onDevLog = { log ->
                        addDevLog(log)
                    }
                )
                
                if (updates.isNotEmpty()) {
                    setPendingUpdates(chatContextId, updates)
                    addChatMessage(
                        ChatMessageEntity(bookId = chatContextId, role = "model", content = responseText + "\n\nPlease review the pending updates above.", timestamp = System.currentTimeMillis())
                    )
                } else {
                    addChatMessage(
                        ChatMessageEntity(bookId = chatContextId, role = "model", content = responseText, timestamp = System.currentTimeMillis())
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                addChatMessage(
                    ChatMessageEntity(bookId = chatContextId, role = "model", content = "Error: ${e.message}", timestamp = System.currentTimeMillis())
                )
            } finally {
                setAiProcessingStatus(chatContextId, null)
                aiProcessingJobs.remove(chatContextId)
            }
        }
        aiProcessingJobs[chatContextId] = job
    }

    
    fun startAiPdfProcessing(
        context: android.content.Context,
        chatContextId: String,
        uri: android.net.Uri,
        allQuestions: List<com.example.data.QuestionEntity>
    ) {
        addChatMessage(
            com.example.data.ChatMessageEntity(bookId = chatContextId, role = "user", content = "Uploaded a PDF for AI extraction.", timestamp = System.currentTimeMillis())
        )
        setAiProcessingStatus(chatContextId, "Reading PDF...", 0f)
        
        val job = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                val name = uri.lastPathSegment ?: ""
                val isZip = mimeType == "application/zip" || mimeType == "application/x-zip-compressed" || name.endsWith(".zip", true)
                val isText = mimeType.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".md", true)
                val isImage = mimeType.startsWith("image/")
                
                var bytes: ByteArray? = null
                var extractedText: String? = null
                val extractedAttachments = mutableListOf<com.example.data.AiFileAttachment>()
                
                if (isZip) {
                    setAiProcessingStatus(chatContextId, "Extracting ZIP...", 0.1f)
                    val zis = java.util.zip.ZipInputStream(contentResolver.openInputStream(uri))
                    val textBuilder = StringBuilder()
                    val availableImages = mutableListOf<String>()
                    var entry = zis.nextEntry
                    val imagesDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$chatContextId/images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name.substringAfterLast("/")
                            if (entryName.endsWith(".txt", true) || entryName.endsWith(".json", true) || entryName.endsWith(".md", true)) {
                                val b = zis.readBytes()
                                textBuilder.append(String(b)).append("\n\n")
                            } else if (entryName.endsWith(".png", true) || entryName.endsWith(".jpg", true) || entryName.endsWith(".jpeg", true)) {
                                val file = java.io.File(imagesDir, entryName)
                                java.io.FileOutputStream(file).use { fos ->
                                    zis.copyTo(fos)
                                }
                                availableImages.add("images/$entryName")
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    zis.close()
                    
                    if (textBuilder.isNotEmpty()) {
                        extractedText = textBuilder.toString()
                        if (availableImages.isNotEmpty()) {
                            extractedText += "\n\n[System Note: The attached ZIP archive contains these image files: ${availableImages.joinToString(", ")}.]"
                        }
                    }
                } else if (isText) {
                    setAiProcessingStatus(chatContextId, "Reading Text File...", 0.1f)
                    extractedText = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                } else if (isImage) {
                    val imageBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (imageBytes != null) extractedAttachments.add(com.example.data.AiFileAttachment(imageBytes, mimeType))
                } else {
                    bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                
                setAiProcessingStatus(chatContextId, "Extracting questions via AI...", 0.3f)
                
                val sourcePromptStr = if (extractedText != null) "Analyze this uploaded text document:\n$extractedText\n\nExtract all educational questions you can find." else "Analyze this uploaded PDF. Extract all educational questions you can find."
                
                val prompt = """
                    $sourcePromptStr
                    Format the output strictly as a JSON array containing objects matching this schema:
                    {
                      "question": "question text",
                      "options": ["A", "B", "C", "D"],
                      "correctIndex": integer index of correct option,
                      "explanation": "brief explanation",
                      "detailedExplanation": "detailed explanation if present, or generate one",
                      "path": "Topic/Subtopic based on PDF headers",
                      "format": "text"
                    }
                    If explanations are missing, write them based on the text. If it is a textbook without explicit questions, generate good multiple choice questions from the content.
                    Return ONLY the JSON array. Do not use markdown blocks like ```json.
                """.trimIndent()
                
                val result = com.example.data.GeminiHelper.generateContent(
                    context = context, 
                    prompt = prompt, 
                    pdfBytes = bytes,
                    requireJson = true,
                    attachments = extractedAttachments,
                    onDevLog = { addDevLog(it) }
                )
                
                if (result.startsWith("Error")) {
                    addChatMessage(com.example.data.ChatMessageEntity(bookId = chatContextId, role = "model", content = result, timestamp = System.currentTimeMillis()))
                    setAiProcessingStatus(chatContextId, null)
                    return@launch
                }
                
                setAiProcessingStatus(chatContextId, "Parsing AI response...", 0.8f)
                
                // Parse JSON array
                val updates = mutableListOf<com.example.ui.components.PendingQuestionUpdate>()
                val cleanedResult = result.replace(Regex("""(?<!\\)\\(?!["\\/bfnrtu])""")) { "\\\\" }
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(com.example.data.DataParser.autoFixJson(cleanedResult)).jsonArray
                
                val gson = com.example.data.SharedGson.normal
                val baseBookId = allQuestions.firstOrNull()?.bookId ?: chatContextId.substringBefore("_")
                
                for (item in jsonArray) {
                    val obj = item.jsonObject
                    val qOptions = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    
                    val updatedQ = com.example.data.QuestionEntity(
                        globalId = java.util.UUID.randomUUID().toString(),
                        id = System.currentTimeMillis() + updates.size,
                        bookId = baseBookId,
                        category = "AI Extracted",
                        question = obj["question"]?.jsonPrimitive?.content ?: "",
                        options = gson.toJson(qOptions),
                        extraOptions = "[]",
                        correctIndex = obj["correctIndex"]?.jsonPrimitive?.int ?: 0,
                        explanation = obj["explanation"]?.jsonPrimitive?.content ?: "",
                        detailedExplanation = obj["detailedExplanation"]?.jsonPrimitive?.content,
                        format = obj["format"]?.jsonPrimitive?.content ?: "text",
                        section = "",
                        exam = "",
                        year = "",
                        path = obj["path"]?.jsonPrimitive?.content ?: ""
                    )
                    
                    updates.add(com.example.ui.components.PendingQuestionUpdate(-1L, updatedQ))
                }
                
                if (updates.isNotEmpty()) {
                    setPendingUpdates(chatContextId, updates)
                    addChatMessage(
                        com.example.data.ChatMessageEntity(bookId = chatContextId, role = "model", content = "I extracted ${updates.size} questions from the PDF. Please review the pending updates above.", timestamp = System.currentTimeMillis())
                    )
                } else {
                    addChatMessage(
                        com.example.data.ChatMessageEntity(bookId = chatContextId, role = "model", content = "I couldn't find or generate any questions from the PDF.", timestamp = System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addDevLog("[JSON PARSE ERROR] ${e.message}")
                addChatMessage(com.example.data.ChatMessageEntity(bookId = chatContextId, role = "model", content = "The AI returned invalid JSON. Check Developer Logs for details.", timestamp = System.currentTimeMillis()))
            } finally {
                setAiProcessingStatus(chatContextId, null)
            }
        }
        
        aiProcessingJobs[chatContextId] = job
    }
fun setPendingUpdates(chatContextId: String, updates: List<com.example.ui.components.PendingQuestionUpdate>?) {
        val current = _pendingUpdates.value.toMutableMap()
        if (updates == null) {
            current.remove(chatContextId)
        } else {
            current[chatContextId] = updates
        }
        _pendingUpdates.value = current
    }


    fun loadChatHistory(bookId: String) {
        viewModelScope.launch {
            _chatHistory.value = repository.getChatsForBook(bookId)
        }
    }

    fun addChatMessage(chat: com.example.data.ChatMessageEntity) {
        viewModelScope.launch {
            repository.insertChat(chat)
            _chatHistory.value = repository.getChatsForBook(chat.bookId)
        }
    }
    
    fun clearChatHistory(bookId: String) {
        viewModelScope.launch {
            repository.clearChatsForBook(bookId)
            _chatHistory.value = emptyList()
        }
    }



    val books = repository.books.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _verificationStatus = MutableStateFlow<String?>(null)
    val verificationStatus = _verificationStatus.asStateFlow()
    private val _verificationMessage = MutableStateFlow<String?>(null)
    private val _fixRequiredMessage = MutableStateFlow<String?>(null)
    val fixRequiredMessage = _fixRequiredMessage.asStateFlow()
    private var pendingBookUri: android.net.Uri? = null

    val verificationMessage = _verificationMessage.asStateFlow()

    private val initialBookId = settingsManager.lastSelectedBookId ?: "BMW_Vol_1"
    private val _currentBookId = MutableStateFlow(initialBookId)
    val currentBookId = _currentBookId.asStateFlow()

    private val _activeQuizTrigger = MutableStateFlow(0)
    val hasActiveQuiz = combine(_currentBookId, _activeQuizTrigger) { bookId, _ ->
        settingsManager.getActiveQuizState(bookId) != null
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _allBookQuestionSummaries = MutableStateFlow<List<com.example.data.QuestionSummary>>(emptyList())
    val allBookQuestionSummaries = _allBookQuestionSummaries.asStateFlow()
    
    private val _selectedCategories = MutableStateFlow<Set<String>>(settingsManager.getSelectedCategories(initialBookId))
    val selectedCategories = _selectedCategories.asStateFlow()
    
    private val _questionLimit = MutableStateFlow<Int?>(run {
        val limit = settingsManager.getQuestionLimit(initialBookId)
        if (limit == 0) null else limit
    }) // null means all
    val questionLimit = _questionLimit.asStateFlow()

    private val _onlyBookmarks = MutableStateFlow(false)
    val onlyBookmarks = _onlyBookmarks.asStateFlow()

    fun setOnlyBookmarks(value: Boolean) {
        _onlyBookmarks.value = value
        generateQuizQuestions()
    }

    fun setQuizQuestionsForBookmarks() {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        val bookmarkedIds = bookmarks.value.map { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val bookmarkedQs = allQs.filter { it.id in bookmarkedIds }
        applySortingAndLimitToQuestions(bookmarkedQs)
    }

    private val _quizQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val questions = _quizQuestions.asStateFlow()
    
    private val _selectedPaths = MutableStateFlow<Set<String>>(settingsManager.getSelectedPaths(initialBookId))
    val selectedPaths = _selectedPaths.asStateFlow()

    fun clearVerificationStatus() {
        _verificationStatus.value = null
        _verificationMessage.value = null
    }

    fun addCustomBook(context: android.content.Context, uri: android.net.Uri, allowAutoFix: Boolean = false, isPermanentUpdate: Boolean = false) {
        if (_verificationStatus.value == "verifying") return
        viewModelScope.launch {
            _verificationStatus.value = "verifying"
            _verificationMessage.value = "Verifying book format..."
            
            val errorMsg = com.example.data.DataParser.parseAndInsertFromUri(context, repository, uri, allowAutoFix, isPermanentUpdate) { progressMsg ->
                _verificationMessage.value = progressMsg
            }
            
            if (errorMsg != null && errorMsg.startsWith("FIX_REQUIRED|")) {
                _verificationStatus.value = null
                pendingBookUri = uri
                _fixRequiredMessage.value = errorMsg.substringAfter("FIX_REQUIRED|")
            } else if (errorMsg == null) {
                _verificationStatus.value = "success"
                _verificationMessage.value = "Verified and added to book list"
                if (_currentBookId.value.isNotEmpty()) {
                    selectBook(_currentBookId.value, forceRefresh = true)
                }
            } else {
                _verificationStatus.value = "error"
                _verificationMessage.value = "Verification failed: $errorMsg"
            }
        }
    }
    fun renameBook(bookId: String, newName: String) {
        viewModelScope.launch {
            val dbBook = repository.getBookSync(bookId) ?: return@launch
            val updated = dbBook.copy(name = newName)
            repository.insertBook(updated)
        }
    }

    fun makeUpdatePermanent(context: android.content.Context, bookId: String, updateId: String) {
        viewModelScope.launch {
            val dbBook = books.value.find { it.id == bookId } ?: return@launch
            val updatesStr = dbBook.updateVersions
            val gson = com.example.data.SharedGson.normal
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, List<Long>>>>() {}.type
            val updateList: MutableList<Map<String, List<Long>>> = gson.fromJson(updatesStr, type) ?: mutableListOf()
            
            val updateEntryIdx = updateList.indexOfFirst { it.containsKey(updateId) }
            if (updateEntryIdx != -1) {
                // Remove from updates list
                updateList.removeAt(updateEntryIdx)
                
                // Update book in DB
                val updatedBook = dbBook.copy(
                    updateVersions = gson.toJson(updateList)
                )
                repository.insertBook(updatedBook)
                
                // Export base file
                val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                val targetFile = java.io.File(basePath, "Quizer/${bookId}.qbook")
                com.example.ui.screens.exportQBook(context, repository, bookId, targetFile)
                
                // Delete update file
                try {
                    val updatesDir = java.io.File(basePath, "Quizer/Updates")
                    val updateFile = java.io.File(updatesDir, "${bookId}_${updateId}.qbook")
                    if (updateFile.exists()) {
                        updateFile.delete()
                    }
                } catch(e: Exception) {}
                
                // Refresh book data and sync to app
                if (currentBookId.value == bookId) {
                    selectBook(bookId, forceRefresh = true)
                }
            }
        }
    }

    fun reverseUpdate(bookId: String, updateId: String) {
        viewModelScope.launch {
            val dbBook = books.value.find { it.id == bookId } ?: return@launch
            val updatesStr = dbBook.updateVersions
            val gson = com.example.data.SharedGson.normal
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, List<Long>>>>() {}.type
            val updateList: MutableList<Map<String, List<Long>>> = gson.fromJson(updatesStr, type) ?: mutableListOf()
            
            // Find the update map containing the updateId
            val updateEntryIdx = updateList.indexOfFirst { it.containsKey(updateId) }
            if (updateEntryIdx != -1) {
                val updateEntry = updateList[updateEntryIdx]
                val range = updateEntry[updateId]
                if (range != null && range.size == 2) {
                    val startId = range[0].toLong()
                    val endId = range[1].toLong()
                    
                    // Delete questions from DB
                    repository.deleteQuestionsInRange(bookId, startId, endId)
                    
                    // Remove from updates list
                    updateList.removeAt(updateEntryIdx)
                    
                    // Update book
                    val removedCount = (endId - startId + 1).toInt()
                    val updatedBook = dbBook.copy(
                        totalQuestions = maxOf(0, dbBook.totalQuestions - removedCount),
                        updateVersions = gson.toJson(updateList)
                    )
                    repository.insertBook(updatedBook)
                    
                    // Also delete from Updates folder
                    try {
                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                        val updatesDir = java.io.File(basePath, "Quizer/Updates")
                        val updateFile = java.io.File(updatesDir, "${bookId}_${updateId}.qbook")
                        if (updateFile.exists()) {
                            updateFile.delete()
                        }
                    } catch(e: Exception) {}
                    
                    // Refresh
                    if (_currentBookId.value == bookId) {
                        selectBook(bookId, forceRefresh = true)
                    }
                }
            }
        }
    }
    
    fun togglePath(path: String) {
        val current = _selectedPaths.value.toMutableSet()
        val isSelected = current.any { path == it || path.startsWith("$it/") }
        
        val root = _topicTreeRoot.value
        
        if (isSelected) {
            // Uncheck
            if (current.contains(path)) {
                current.remove(path)
            } else if (root != null) {
                // An ancestor is selected. Remove all ancestors and add their other children.
                val ancestors = current.filter { path.startsWith("$it/") }
                current.removeAll(ancestors.toSet())
                
                for (ancestorPath in ancestors) {
                    // Find the ancestor node in the tree
                    fun findNode(node: com.example.data.TopicNode, target: String): com.example.data.TopicNode? {
                        if (node.path == target) return node
                        for (child in node.children.values) {
                            if (target.startsWith(child.path)) {
                                val found = findNode(child, target)
                                if (found != null) return found
                            }
                        }
                        return null
                    }
                    
                    val ancestorNode = findNode(root, ancestorPath) ?: continue
                    
                    // We need to add all descendants of ancestorNode that are NOT on the path to the removed node
                    // Wait, a simpler way is just to add all immediate children of all nodes along the path from ancestor to the removed node, EXCEPT the ones on the path.
                    var currNode: com.example.data.TopicNode? = ancestorNode
                    val pathParts = path.removePrefix(ancestorPath + "/").split("/")
                    
                    for (part in pathParts) {
                        if (currNode == null) break
                        for (child in currNode.children.values) {
                            if (child.name != part) {
                                current.add(child.path)
                            }
                        }
                        currNode = currNode.children[part]
                    }
                }
            }
        } else {
            // Check
            current.add(path)
            current.removeIf { it.startsWith("$path/") } // Remove redundant descendants
            
            // Optional: Check if all siblings are now checked, and if so, replace them with parent
            // But this is good enough.
        }
        _selectedPaths.value = current
        settingsManager.setSelectedPaths(_currentBookId.value, current)
    }
    
    private val _selectedTopic = MutableStateFlow<TopicNode?>(null)

    fun setQuizQuestionsByTopic(topic: TopicNode) {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        _selectedTopic.value = topic
        _onlyBookmarks.value = false
        val allQs = _allBookQuestionSummaries.value
        val questionsInTopic = allQs.filter { it.id in topic.questionIds }
        applySortingAndLimitToQuestions(questionsInTopic)
    }

    fun setQuizQuestionsForDailyRevision() {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val now = System.currentTimeMillis()
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val revisionQs = allQs.filter { q ->
            val stat = statsMap[q.id]
            stat != null && stat.nextReview <= now
        }
        applySortingAndLimitToQuestions(revisionQs)
    }

    fun setQuizQuestionsForWeakTopics() {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val weakQs = allQs.filter { q ->
            val stat = statsMap[q.id]
            stat != null && stat.attempts > 0 && (stat.correct.toFloat() / stat.attempts) < 0.5f
        }
        applySortingAndLimitToQuestions(weakQs)
    }

    fun setQuizQuestionsForCritical() {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        val statsMap = allStats.value.associateBy { it.questionId }
        val allQs = _allBookQuestionSummaries.value
        val criticalQs = allQs.sortedByDescending { q ->
            statsMap[q.id]?.wrong ?: 0
        }.take(30) // Take top 30 most mistaken
        applySortingAndLimitToQuestions(criticalQs)
    }

    private fun processQuestionPool(pool: List<com.example.data.QuestionSummary>, flowMode: String, isShuffle: Boolean, limit: Int?, offset: Int = 1): List<com.example.data.QuestionSummary> {
        val grouped = mutableListOf<List<com.example.data.QuestionSummary>>()
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

        if (flowMode == "progressive" && isShuffle) {
            grouped.shuffle()
        }

        if (flowMode == "progressive") {
            val statsMap = allStats.value.associateBy { it.questionId }
            val groupScores = grouped.associateWith { g ->
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
            grouped.sortBy { groupScores[it] ?: 0f }
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
                val secondPart = mutableListOf<List<com.example.data.QuestionSummary>>()
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

        val finalGrouped = mutableListOf<List<com.example.data.QuestionSummary>>()
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

        val finalQuestions = mutableListOf<com.example.data.QuestionSummary>()
        for (g in finalGrouped) {
            if (isShuffle) {
                finalQuestions.addAll(g.shuffled())
            } else {
                finalQuestions.addAll(g)
            }
        }
        
        return finalQuestions
    }

    private fun applySortingAndLimitToQuestions(baseList: List<com.example.data.QuestionSummary>) {
        val currentFlow = if (isLearnMode) settingsManager.learnFlowMode else settingsManager.quizFlowMode
        val currentShuffle = if (isLearnMode) settingsManager.learnShuffleQuestions else settingsManager.shuffleQuestions
        val currentLimit = if (isLearnMode) settingsManager.learnQuestionLimit else -1
        
        val filtered = processQuestionPool(baseList, currentFlow, currentShuffle, currentLimit)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val idsToFetch = filtered.map { it.id }
            val indexMap = idsToFetch.withIndex().associate { it.value to it.index }
            val fullQuestions = repository.getQuestionsByBookAndIds(_currentBookId.value, idsToFetch).resolveHeaders()
            if (isActive) {
                _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                activeQuizSessionId = System.currentTimeMillis()
            } // Preserve order
        }
    }
    
    private val _sequenceOffset = MutableStateFlow(1)
    val sequenceOffset = _sequenceOffset.asStateFlow()
    
    fun setSequenceOffset(offset: Int) {
        _sequenceOffset.value = offset
        generateQuizQuestions()
    }
    
    fun generateQuizQuestions() {
        isActiveQuizStandardSequence = true
        quizGenJob?.cancel()
        quizGenJob = viewModelScope.launch(Dispatchers.Default) {
            _selectedTopic.value = null // clear strict topic filter
            val allQs = _allBookQuestionSummaries.value
            val paths = _selectedPaths.value
            val categories = _selectedCategories.value
            val limit = _questionLimit.value
            val isBookmarks = _onlyBookmarks.value
            val bookmarkedIds = bookmarks.value.map { it.questionId }
            val offset = _sequenceOffset.value
            
            var filtered = if (isBookmarks) {
                allQs.filter { it.id in bookmarkedIds }
            } else if (paths.isNotEmpty()) {
                val root = _topicTreeRoot.value
                if (root != null) {
                    val matchingQuestionIds = mutableSetOf<Long>()
                    fun traverse(node: TopicNode) {
                        if (paths.contains(node.path)) {
                            matchingQuestionIds.addAll(node.questionIds)
                        } else {
                            for (child in node.children.values) {
                                traverse(child)
                            }
                        }
                    }
                    traverse(root)
                    allQs.filter { it.id in matchingQuestionIds }
                } else {
                    allQs.filter { q ->
                        paths.any { p -> 
                            val qPath = if (q.path.isNotBlank()) q.path else q.category
                            val delimiter = if (qPath.contains(">")) ">" else if (qPath.contains("/")) "/" else "-"
                            val normalizedQPath = qPath.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }.joinToString("/")
                            normalizedQPath == p || normalizedQPath.startsWith("$p/")
                        }
                    }
                }
            } else if (categories.isNotEmpty()) {
                allQs.filter { it.category in categories }
            } else {
                allQs
            }
            
            val currentFlowMode = if (isLearnMode) settingsManager.learnFlowMode else settingsManager.quizFlowMode
            val isShuffle = if (isLearnMode) settingsManager.learnShuffleQuestions else settingsManager.shuffleQuestions
            
            filtered = processQuestionPool(filtered, currentFlowMode, isShuffle, limit, offset)

            val idsToFetch = filtered.map { it.id }
            val indexMap = idsToFetch.withIndex().associate { it.value to it.index }
            val fullQuestions = repository.getQuestionsByBookAndIds(_currentBookId.value, idsToFetch).resolveHeaders()
            if (isActive) {
                _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                activeQuizSessionId = System.currentTimeMillis()
            }
        }
    }
    
    fun setQuizQuestionsByIds(bookId: String, ids: List<Long>) {
        isActiveQuizStandardSequence = false
        currentExamSettings = null
        _selectedTopic.value = null
        _onlyBookmarks.value = false
        quizGenJob?.cancel()
        quizGenJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val indexMap = ids.withIndex().associate { it.value to it.index }
            val fullQuestions = repository.getQuestionsByBookAndIds(bookId, ids).resolveHeaders()
            if (isActive) {
                _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                activeQuizSessionId = System.currentTimeMillis()
            }
        }
    }

    fun practiceWrongQuiz(historyId: Long?) {
        isActiveQuizStandardSequence = false
        currentPracticeMistakesParentId = historyId
        currentExamSettings = null
        val entry = history.value.find { it.historyId == historyId } ?: return
        val qIdsRaw = com.example.data.SharedGson.normal.fromJson(entry.questionIds, Array<Double>::class.java).map { it.toLong() }
        val gson = com.example.data.SharedGson.normal
        val rawList = gson.fromJson<List<Any?>>(entry.userAnswers, object : com.google.gson.reflect.TypeToken<List<Any?>>() {}.type)
        val uAnsSets = rawList.map { item ->
            when (item) {
                is Number -> setOf(item.toInt())
                is List<*> -> item.mapNotNull { (it as? Number)?.toInt() }.toSet()
                else -> emptySet()
            }
        }
        
        quizGenJob?.cancel()
        quizGenJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val allQs = repository.getQuestionsByBookAndIds(entry.bookId, qIdsRaw).resolveHeaders().associateBy { it.id }
            val wrongQIds = mutableListOf<Long>()
            
            for (i in qIdsRaw.indices) {
                val qId = qIdsRaw[i]
                val ansSet = uAnsSets.getOrNull(i) ?: emptySet()
                val q = allQs[qId]
                if (q != null) {
                    val correctIds = gson.fromJson<List<String>>(q.advancedCorrectIds ?: "[]", object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) ?: emptyList()
                    val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(q.advancedOptions ?: "[]", object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>() {}.type) ?: emptyList()
                    val correctIndices = if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                        correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                    } else {
                        setOf(q.correctIndex)
                    }
                    if (ansSet != correctIndices) {
                        wrongQIds.add(qId)
                    }
                }
            }
            // Cannot call setQuizQuestionsByIds from here directly as it launches another job without waiting, but it's safe since it just launches
            val indexMap = wrongQIds.withIndex().associate { it.value to it.index }
            val fullQuestions = repository.getQuestionsByBookAndIds(entry.bookId, wrongQIds).resolveHeaders()
            _selectedTopic.value = null
            _onlyBookmarks.value = false
            if (isActive) {
                _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                activeQuizSessionId = System.currentTimeMillis()
            }
        }
    }
    
    val allStats = _currentBookId.flatMapLatest { repository.getStatsForBook(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val history = repository.history.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val bookmarks = _currentBookId.flatMapLatest { repository.getBookmarksForBook(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val bookmarkedQuestions = kotlinx.coroutines.flow.combine(
        _currentBookId.flatMapLatest { repository.getBookmarksForBook(it) },
        _currentBookId.flatMapLatest { repository.getQuestionsForBook(it) }
    ) { bks, allQs ->
        val resolved = allQs.resolveHeaders()
        val ids = bks.map { it.questionId }
        resolved.filter { it.id in ids }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _selectedResultIdForReview = MutableStateFlow<Long?>(null)
    val selectedResultIdForReview = _selectedResultIdForReview.asStateFlow()
    
    private val _reviewQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val reviewQuestions = _reviewQuestions.asStateFlow()
    
    fun selectResultForReview(id: Long?) {
        _selectedResultIdForReview.value = id
        if (id != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                val entry = history.value.find { it.historyId == id }
                if (entry != null) {
                    val qIdsRaw = com.example.data.SharedGson.normal.fromJson(entry.questionIds, Array<Double>::class.java).map { it.toLong() }
                    val indexMap = qIdsRaw.withIndex().associate { it.value to it.index }
                    val questions = repository.getQuestionsByBookAndIds(entry.bookId, qIdsRaw).resolveHeaders().sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                    _reviewQuestions.value = questions
                }
            }
        }
    }
    
    private val _themeState = MutableStateFlow(settingsManager.appTheme)
    val themeState = _themeState.asStateFlow()

    private val _themeModeState = MutableStateFlow(settingsManager.appThemeMode)
    val themeModeState = _themeModeState.asStateFlow()

    private val _timerSoundEnabledState = MutableStateFlow(settingsManager.timerSoundEnabled)
    val timerSoundEnabledState = _timerSoundEnabledState.asStateFlow()

    private val _clickSoundEnabledState = MutableStateFlow(settingsManager.clickSoundEnabled)
    val clickSoundEnabledState = _clickSoundEnabledState.asStateFlow()

    private val _hapticFeedbackEnabledState = MutableStateFlow(settingsManager.hapticFeedbackEnabled)
    val hapticFeedbackEnabledState = _hapticFeedbackEnabledState.asStateFlow()

    private val _appIconState = MutableStateFlow(settingsManager.appIcon)
    val appIconState = _appIconState.asStateFlow()
    
    var currentPracticeMistakesParentId: Long? = null
    var activeCurrentIndex = 0
    var activeQuizSessionId = 0L
    var activeSelectedAnswers = mutableMapOf<Int, Set<Int>>()
    var activeLockedAnswers = mutableMapOf<Int, Boolean>()
    var activeTimeRemainingMs = -1L

    fun saveActiveQuizState(currentIndex: Int, selectedAnswers: Map<Int, Set<Int>>, lockedAnswers: Map<Int, Boolean>, timeRemainingMs: Long) {
        val state = mapOf(
            "bookId" to currentBookId.value,
            "questions" to _quizQuestions.value.map { it.id },
            "currentIndex" to currentIndex,
            "selectedAnswers" to selectedAnswers,
            "lockedAnswers" to lockedAnswers,
            "timeRemainingMs" to timeRemainingMs,
            "sessionId" to activeQuizSessionId
        )
        settingsManager.setActiveQuizState(currentBookId.value, com.example.data.SharedGson.normal.toJson(state))
        _activeQuizTrigger.value++
    }

    fun clearActiveQuizState() {
        settingsManager.setActiveQuizState(currentBookId.value, null)
        _activeQuizTrigger.value++
        activeCurrentIndex = 0
        activeSelectedAnswers = mutableMapOf()
        activeLockedAnswers = mutableMapOf()
        activeTimeRemainingMs = -1L
        activeQuizSessionId = System.currentTimeMillis()
        currentExamSettings = null
    }

    suspend fun loadActiveQuizState(): Boolean {
        pendingQuizGenerationTrigger = false
        quizGenJob?.cancelAndJoin()
        val stateStr = settingsManager.getActiveQuizState(currentBookId.value)
        if (stateStr != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                val state: Map<String, Any> = com.example.data.SharedGson.normal.fromJson(stateStr, type)
                
                val bookId = state["bookId"] as String
                
                // Add validation to prevent loading orphaned states
                val bookExists = books.value.any { it.id == bookId }
                
                if (!bookExists) {
                    clearActiveQuizState()
                    return false
                }
                
                _currentBookId.value = bookId

                val qIdsRaw = (state["questions"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
                val qIds = qIdsRaw.map { it.toLong() }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val indexMap = qIds.withIndex().associate { it.value to it.index }
                    val fullQuestions = repository.getQuestionsByBookAndIds(bookId, qIds).resolveHeaders()
                    _quizQuestions.value = fullQuestions.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                }
                
                activeCurrentIndex = (state["currentIndex"] as? Number)?.toInt() ?: 0
                
                val selAnsRaw = (state["selectedAnswers"] as? Map<*, *>) ?: emptyMap<Any, Any>()
                activeSelectedAnswers = selAnsRaw.entries.associate { (k, v) -> 
                    k.toString().toInt() to when (v) {
                        is Number -> setOf(v.toInt())
                        is List<*> -> v.mapNotNull { (it as? Number)?.toInt() }.toSet()
                        else -> emptySet()
                    }
                }.toMutableMap()
                
                val lockAnsRaw = (state["lockedAnswers"] as? Map<*, *>) ?: emptyMap<Any, Any>()
                activeLockedAnswers = lockAnsRaw.entries.associate { (k, v) -> 
                    k.toString().toInt() to (v as? Boolean ?: false) 
                }.toMutableMap()
                
                activeTimeRemainingMs = (state["timeRemainingMs"] as Double).toLong()
                activeQuizSessionId = (state["sessionId"] as? Double)?.toLong() ?: 0L
                if (settingsManager.quizMode == "exam") {
                    val limit = qIds.size
                    val settingsStr = settingsManager.getExamSettings(bookId, limit)
                    if (settingsStr != null) {
                        currentExamSettings = com.example.data.SharedGson.normal.fromJson(settingsStr, ExamSettings::class.java)
                    }
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                clearActiveQuizState()
            }
        }
        return false
    }

    fun updateTheme(themeStr: String) {
        settingsManager.appTheme = themeStr
        _themeState.value = themeStr
    }

    fun updateThemeMode(modeStr: String) {
        settingsManager.appThemeMode = modeStr
        _themeModeState.value = modeStr
    }

    fun updateTimerSound(enabled: Boolean) {
        settingsManager.timerSoundEnabled = enabled
        _timerSoundEnabledState.value = enabled
    }

    fun updateClickSound(enabled: Boolean) {
        settingsManager.clickSoundEnabled = enabled
        _clickSoundEnabledState.value = enabled
    }

    fun updateHapticFeedback(enabled: Boolean) {
        settingsManager.hapticFeedbackEnabled = enabled
        _hapticFeedbackEnabledState.value = enabled
    }

    fun playButtonPress() {
        if (_clickSoundEnabledState.value) soundHapticManager.playClick()
        if (_hapticFeedbackEnabledState.value) soundHapticManager.hapticButtonPress()
    }

    fun playNavigation() {
        if (_clickSoundEnabledState.value) soundHapticManager.playClick()
        if (_hapticFeedbackEnabledState.value) soundHapticManager.hapticNavigation()
    }

    fun playToggle() {
        if (_clickSoundEnabledState.value) soundHapticManager.playClick()
        if (_hapticFeedbackEnabledState.value) soundHapticManager.hapticToggle()
    }

    fun playBookmark() {
        if (_clickSoundEnabledState.value) soundHapticManager.playClick()
        if (_hapticFeedbackEnabledState.value) soundHapticManager.hapticBookmark()
    }

    fun updateAppIcon(iconStr: String) {
        settingsManager.appIcon = iconStr
        _appIconState.value = iconStr
    }
    
    private val _topicTreeRoot = MutableStateFlow<TopicNode?>(null)
    val topicTreeRoot = _topicTreeRoot.asStateFlow()

    private val _learningPlan = MutableStateFlow<LearningPlanEntity?>(null)
    val learningPlan = _learningPlan.asStateFlow()

    private val _favoriteTopics = MutableStateFlow(settingsManager.favoriteTopics)
    val favoriteTopics = _favoriteTopics.asStateFlow()

    fun toggleFavoriteTopic(path: String) {
        val currentFavorites = settingsManager.favoriteTopics.toMutableSet()
        if (currentFavorites.contains(path)) {
            currentFavorites.remove(path)
        } else {
            currentFavorites.add(path)
        }
        settingsManager.favoriteTopics = currentFavorites
        _favoriteTopics.value = currentFavorites
    }

    private suspend fun calculateTopicTreeAsync(questions: List<com.example.data.QuestionSummary>, statsMap: Map<String, QuestionStatEntity>) {
        val root = withContext(Dispatchers.Default) {
            val rootNode = TopicNode("Root", "")
            
            questions.forEach { q ->
                val stat = statsMap["${q.bookId}_${q.id}"]
                
                val rawPath = if (q.path.isNotBlank()) q.path else q.category
                val delimiter = if (rawPath.contains(">")) ">" else if (rawPath.contains("/")) "/" else "-"
                val parts = rawPath.split(delimiter).map { it.trim() }.filter { it.isNotBlank() }
                
                var current = rootNode
                var currentPathStr = ""
                
                for (i in parts.indices) {
                    val part = parts[i]
                    currentPathStr = if (currentPathStr.isEmpty()) part else "$currentPathStr/$part"
                    
                    if (!current.children.containsKey(part)) {
                        current.children[part] = TopicNode(part, currentPathStr)
                    }
                    current = current.children[part]!!
                    
                    current.questionCount++
                    if (stat != null) {
                        if (stat.attempts > 0) current.attemptedCount++
                        current.correctCount += stat.correct
                        current.totalAttempts += stat.attempts
                    }
                    
                    current.questionIds.add(q.id)
                    
                    if (i == parts.size - 1) {
                        current.isLeaf = true
                    }
                }
            }
            rootNode
        }
        _topicTreeRoot.value = root
    }

    private fun calculateTopicTree(questions: List<com.example.data.QuestionSummary>, statsMap: Map<String, QuestionStatEntity>) {
        viewModelScope.launch {
            calculateTopicTreeAsync(questions, statsMap)
        }
    }

    fun toggleTopicCompleted(path: String) {
        viewModelScope.launch {
            val bookId = _currentBookId.value
            val existing = _learningPlan.value
            
            val completedList = if (existing != null && existing.completedTopics.isNotBlank()) {
                com.example.data.SharedGson.normal.fromJson(existing.completedTopics, Array<String>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
            
            if (completedList.contains(path)) {
                completedList.remove(path)
            } else {
                completedList.add(path)
            }
            
            val updatedJson = com.example.data.SharedGson.normal.toJson(completedList)
            
            if (existing != null) {
                repository.insertLearningPlan(existing.copy(completedTopics = updatedJson))
            } else {
                repository.insertLearningPlan(
                    LearningPlanEntity(
                        bookId = bookId,
                        topicOrder = "[]",
                        unlockedTopics = "[]",
                        lockedTopics = "[]",
                        completedTopics = updatedJson
                    )
                )
            }
        }
    }

    init {
        // Load questions when book changes
        viewModelScope.launch {
            _currentBookId.flatMapLatest { repository.getQuestionSummariesForBook(it) }.collect { qList ->
                _allBookQuestionSummaries.value = qList
                if (pendingQuizGenerationTrigger) {
                    pendingQuizGenerationTrigger = false
                    generateQuizQuestions()
                }
            }
        }
        
        viewModelScope.launch {
            _currentBookId.flatMapLatest { repository.getLearningPlan(it) }.collect { plan ->
                _learningPlan.value = plan
            }
        }
        
        viewModelScope.launch {
            combine(_allBookQuestionSummaries, allStats) { questions, stats ->
                if (questions.isNotEmpty()) {
                    val statsMap = stats.associateBy { it.questionKey }
                    calculateTopicTreeAsync(questions, statsMap)
                }
            }.collect()
        }
    }

    fun selectBook(bookId: String, forceRefresh: Boolean = false) {
        if (_currentBookId.value != bookId || forceRefresh) {
            pendingQuizGenerationTrigger = true
            _currentBookId.value = bookId
            settingsManager.lastSelectedBookId = bookId
            
            val paths = settingsManager.getSelectedPaths(bookId)
            _selectedPaths.value = paths
            
            val categories = settingsManager.getSelectedCategories(bookId)
            _selectedCategories.value = categories
            
            val limit = settingsManager.getQuestionLimit(bookId)
            _questionLimit.value = if (limit == 0) null else limit
            
            _onlyBookmarks.value = false
            _sequenceOffset.value = 1
            _selectedTopic.value = null
            currentExamSettings = null
            generateQuizQuestions()
            if (lastSearchQuery.isNotBlank()) {
                searchQuestions(lastSearchQuery)
            }
        }
    }

    fun toggleBookFavorite(bookId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleBookFavorite(bookId, isFavorite)
        }
    }
    
    fun toggleCategory(category: String) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) current.remove(category)
        else current.add(category)
        _selectedCategories.value = current
        settingsManager.setSelectedCategories(_currentBookId.value, current)
        generateQuizQuestions()
    }
    
    fun setQuestionLimit(limit: Int?) {
        _questionLimit.value = limit
        settingsManager.setQuestionLimit(_currentBookId.value, limit ?: 0)
        generateQuizQuestions()
    }

    fun saveQuizResult(
        bookId: String,
        quizLabel: String,
        score: Float,
        total: Int,
        correct: Int,
        wrong: Int,
        skipped: Int,
        timeUsed: Long,
        questionIds: List<Long>,
        userAnswers: List<Set<Int>?>,
        questionTimes: List<Long> = emptyList()
    ) {
        val parentId = currentPracticeMistakesParentId
        currentPracticeMistakesParentId = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val historyEntry = QuizHistoryEntity(
                historyId = System.currentTimeMillis(),
                bookId = bookId,
                date = java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date()),
                status = if (parentId != null) "practice_mistakes_for_$parentId" else "completed",
                quizLabel = quizLabel,
                total = total,
                correct = correct,
                wrong = wrong,
                skipped = skipped,
                accuracy = if (total > 0) (correct.toFloat() / total) * 100f else 0f,
                rawScore = score,
                finalScore = score,
                timeUsed = timeUsed,
                activeQuizData = com.example.data.SharedGson.normal.toJson(questionTimes),
                questionIds = com.example.data.SharedGson.normal.toJson(questionIds),
                userAnswers = com.example.data.SharedGson.normal.toJson(userAnswers)
            )
            repository.insertHistory(historyEntry)

            // Update question stats
            val now = System.currentTimeMillis()
            val fullQuestions = repository.getQuestionsByBookAndIds(bookId, questionIds).resolveHeaders().associateBy { it.id }
            val gson = com.example.data.SharedGson.normal
            
            // Only update stats if it's a real quiz, not practice mistakes
            if (parentId == null) {
                questionIds.forEachIndexed { index, qId ->
                    val ans = userAnswers[index]
                    if (ans != null) {
                        val key = "${bookId}_${qId}"
                        var existing = allStats.value.find { it.questionKey == key }
                        if (existing == null) {
                            existing = QuestionStatEntity(questionKey = key, bookId = bookId, questionId = qId)
                        }
                        val currentQ = fullQuestions[qId]
                        val isCorrect = if (currentQ != null) {
                            val correctIds = gson.fromJson<List<String>>(currentQ.advancedCorrectIds ?: "[]", object: com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                            val optionsList = gson.fromJson<List<com.example.data.QBookOption>>(currentQ.advancedOptions ?: "[]", object: com.google.gson.reflect.TypeToken<List<com.example.data.QBookOption>>(){}.type) ?: emptyList()
                            val correctIndices = if (correctIds.isNotEmpty() && optionsList.isNotEmpty()) {
                                correctIds.mapNotNull { id -> optionsList.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
                            } else {
                                setOf(currentQ.correctIndex)
                            }
                            ans == correctIndices
                        } else false
                        
                        val q = if (isCorrect == true) 4 else 1
                        var newEase = existing.easeFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
                        if (newEase < 1.3f) newEase = 1.3f
                        
                        val newStreak = if (q < 3) 0 else existing.streak + 1
                        val newInterval = when (newStreak) {
                            0 -> 1
                            1 -> 1
                            2 -> 6
                            else -> kotlin.math.round(existing.interval * newEase).toInt()
                        }
                        val newNextReview = now + newInterval * 24L * 60L * 60L * 1000L

                        val updated = existing.copy(
                            attempts = existing.attempts + 1,
                            correct = if (isCorrect == true) existing.correct + 1 else existing.correct,
                            wrong = if (isCorrect == false) existing.wrong + 1 else existing.wrong,
                            lastSeen = now,
                            easeFactor = newEase,
                            streak = newStreak,
                            interval = newInterval,
                            nextReview = newNextReview
                        )
                        repository.insertStat(updated)
                    }
                }
            }
            
            // Advance sequence if autoSequence is on, but ONLY if not practice mistakes
            if (isActiveQuizStandardSequence && parentId == null && settingsManager.autoSequence && settingsManager.quizFlowMode == "sequence") {
                val limit = _questionLimit.value
                val offset = _sequenceOffset.value
                if (limit != null) {
                    val nextOffset = offset + limit
                    val maxValue = _allBookQuestionSummaries.value.size
                    if (nextOffset <= maxValue) {
                        _sequenceOffset.value = nextOffset
                    } else {
                        _sequenceOffset.value = 1
                    }
                }
            }
        }
    }

    fun toggleBookmark(bookId: String, questionId: Long) {
        if (_hapticFeedbackEnabledState.value) soundHapticManager.hapticBookmark()
        if (_clickSoundEnabledState.value) soundHapticManager.playClick()
        viewModelScope.launch {
            val key = "${bookId}_${questionId}"
            val exists = bookmarks.value.any { it.bookmarkKey == key }
            if (exists) {
                repository.removeBookmark(bookId, questionId)
            } else {
                repository.addBookmark(bookId, questionId)
            }
        }
    }

    
    fun confirmFix(context: android.content.Context) {
        val uri = pendingBookUri ?: return
        pendingBookUri = null
        _fixRequiredMessage.value = null
        addCustomBook(context, uri, true)
    }
    
    fun cancelFix() {
        pendingBookUri = null
        _fixRequiredMessage.value = null
        _verificationStatus.value = "error"
        _verificationMessage.value = "Import cancelled due to syntax errors."
    }

    fun deleteBook(context: android.content.Context, bookId: String) {
        viewModelScope.launch {
            val updatedRemoved = settingsManager.removedBooks.toMutableSet()
            updatedRemoved.add(bookId)
            settingsManager.removedBooks = updatedRemoved
            
            // Move core book files to .trash, but permanently delete cache/restore points
            withContext(Dispatchers.IO) {
                try {
                    val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    val quizerDir = java.io.File(basePath, "Quizer")
                    val qbooksDir = java.io.File(quizerDir, "qbooks")
                    val bookDir = java.io.File(quizerDir, bookId)
                    val trashDir = java.io.File(quizerDir, ".trash")
                    
                    if (!trashDir.exists()) trashDir.mkdirs()
                    val dateFormat = java.text.SimpleDateFormat("yy.MM.dd_HH.mm.ss", java.util.Locale.getDefault())
                    val timestamp = dateFormat.format(java.util.Date())
                    
                    try {
                        // Delete the specific book directory (which contains images, backups, cached state) permanently
                        if (bookDir.exists()) {
                            bookDir.deleteRecursively()
                        }
                    } catch(e: Exception) {}
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            repository.deleteBook(bookId)
            repository.deleteQuestionsForBook(bookId)
            repository.deleteAssociatedDataForBook(bookId)
            settingsManager.clearBookSettings(bookId)
            if (currentBookId.value == bookId) {
                val newBooks = repository.getAllBooksSync()
                if (newBooks.isNotEmpty()) {
                    selectBook(newBooks.first().id)
                } else {
                    _currentBookId.value = ""
                    settingsManager.lastSelectedBookId = ""
                    _allBookQuestionSummaries.value = emptyList()
                    _topicTreeRoot.value = null
                    generateQuizQuestions()
                }
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private var lastSearchQuery = ""

    fun searchQuestions(query: String) {
        lastSearchQuery = query
        viewModelScope.launch(Dispatchers.IO) {
            val bookId = _currentBookId.value
            if (query.trim().isBlank()) {
                _searchResults.value = emptyList()
            } else {
                val matched = repository.searchQuestionsSync(bookId, query).resolveHeaders()
                _searchResults.value = matched
            }
        }
    }

    fun resetApp(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDatabase()
            withContext(Dispatchers.Main) {
                settingsManager.clearAll()
                com.example.data.SecurePrefs.get(context).edit().clear().apply()
                _themeState.value = settingsManager.appTheme
                _currentBookId.value = ""
                _allBookQuestionSummaries.value = emptyList()
                _topicTreeRoot.value = null
                
                // Re-initialize default data
                DataParser.loadInitialData(context, repository)
                
                val newBooks = repository.getAllBooksSync()
                if (newBooks.isNotEmpty()) {
                    selectBook(newBooks.first().id)
                }
            }
        }
    }

    fun resetBookToInitial(context: android.content.Context, bookId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val quizerDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer")
                val bookDir = java.io.File(quizerDir, bookId)
                val backupFile = java.io.File(bookDir, "backup_questions.json")
                if (backupFile.exists()) {
                    val fileContent = backupFile.readText()
                    val type = object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookQuestion>>() {}.type
                    val questions: List<com.example.data.QBookQuestion> = com.example.data.SharedGson.normal.fromJson(fileContent, type)
                    
                    val book = repository.getBookSync(bookId)
                    if (book != null) {
                        repository.deleteBook(bookId)
                        repository.deleteQuestionsForBook(bookId)
                        repository.insertBook(book) 

                        val gsonForExport = com.example.data.SharedGson.normal
                        val entities = questions.map { q ->
                            val advancedOptionsStr = gsonForExport.toJson(q.options)
                            val advancedCorrectIdsStr = gsonForExport.toJson(q.correctOptionIds)
                            val legacyOptions = q.options.map { it.text }
                            val legacyOptionsStr = gsonForExport.toJson(legacyOptions)
                            val legacyExtraOptions = q.extraOptions?.map { it.text } ?: emptyList()
                            val legacyExtraOptionsStr = gsonForExport.toJson(legacyExtraOptions)
                            val legacyCorrectIndex = if (q.correctOptionIds.isNotEmpty()) {
                                q.options.indexOfFirst { it.id == q.correctOptionIds.first() }.coerceAtLeast(0)
                            } else 0
                            
                            com.example.data.QuestionEntity(
                                id = q.id,
                                bookId = book.id,
                                question = q.question.text,
                                options = legacyOptionsStr,
                                correctIndex = legacyCorrectIndex,
                                explanation = q.explanation?.brief ?: "",
                                path = q.metadata.path,
                                category = q.metadata.category,
                                section = q.metadata.path,
                                extraOptions = legacyExtraOptionsStr,
                                exam = q.metadata.exam ?: "",
                                year = q.metadata.year?.toString() ?: "",
                                img = q.question.image,
                                questionHeader = q.header?.text,
                                advancedOptions = advancedOptionsStr,
                                advancedCorrectIds = advancedCorrectIdsStr,
                                detailedExplanation = q.explanation?.detailed,
                                explanationImage = q.explanation?.image,
                                exams = q.metadata.exams?.let { gsonForExport.toJson(it) },
                                explanationTable = q.explanation?.table?.let { gsonForExport.toJson(it) },
                                format = q.question.format,
                                tableData = q.question.table?.let { gsonForExport.toJson(it) },
                                bottomText = q.question.bottomText
                            )
                        }
                        repository.insertQuestions(entities)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onComplete(true)
                            selectBook(bookId) 
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
    
        override fun onCleared() {
        super.onCleared()
        soundHapticManager.release()
    }
}


@androidx.annotation.Keep
data class ExamSettings(
    val markingScheme: String = "Off",
    val customCorrectMarks: Float = 1f,
    val customWrongMarks: Float = 0f,
    val timerPreset: String = "Off",
    val customTimerMinutes: Int = 0,
    val seriousMode: Boolean = false
)
