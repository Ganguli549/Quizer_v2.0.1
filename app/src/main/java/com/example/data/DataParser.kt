package com.example.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson


import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock

object SharedGson {
    val lenient: com.google.gson.Gson = com.google.gson.Gson().newBuilder().setLenient().create()
    val normal: com.google.gson.Gson = com.google.gson.Gson()
}


data class RawBookIdentity(
    val id: String?,
    val name: String?,
    val dataVariable: String?,
    val updateId: String?,
    val bookVersion: String?,
    @com.google.gson.annotations.SerializedName(value = "updateVersion", alternate = ["updateVersions"])
    val updateVersion: List<Map<String, List<Long>>>?,
    val questionIdStart: Long?,
    val questionIdEnd: Long?,
    val hierarchyLabels: List<String>?,
    val roadmapLeafLevel: Int?
)

data class RawQuestion(
    val id: Long?,
    val section: String?,
    val path: String?,
    val category: String?,
    val exam: String?,
    val year: String?,
    val question: String?,
    val options: List<String>?,
    val correct: Int?,
    val explanation: String?,
    val extraOptions: List<String>?,
    val img: String?,
    @com.google.gson.annotations.SerializedName(value = "question_header", alternate = ["questionheader"])
    val questionHeader: com.google.gson.JsonElement?,
    val header: RawHeader?
)

data class RawHeader(val startId: Long?, val endId: Long?, val text: String?)


data class HeaderRange(val startId: Long, val endId: Long, val headerText: String)

class JsonSanitizingReader(source: java.io.Reader) : java.io.Reader() {
    private val bufferedReader = if (source is java.io.BufferedReader) source else java.io.BufferedReader(source, 65536)
    private val buffer = java.lang.StringBuilder(131072)
    private var readPos = 0
    private var inString = false
    private var isEscaped = false

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (readPos >= buffer.length) {
            buffer.setLength(0)
            readPos = 0
            fillBuffer(maxOf(len, 32768))
        }
        if (readPos >= buffer.length) return -1
        val toRead = minOf(len, buffer.length - readPos)
        buffer.getChars(readPos, readPos + toRead, cbuf, off)
        readPos += toRead
        return toRead
    }

    private fun fillBuffer(targetLen: Int) {
        while (buffer.length < targetLen) {
            val cInt = bufferedReader.read()
            if (cInt == -1) break
            val c = cInt.toChar()

            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                    val isValidEscape = c == '"' || c == '\\' || c == '/' || c == 'b' ||
                            c == 'f' || c == 'n' || c == 'r' || c == 't' || c == 'u'
                    if (isValidEscape) {
                        buffer.append(c)
                    } else {
                        buffer.append('\\').append(c)
                    }
                } else {
                    if (c == '\\') {
                        isEscaped = true
                        buffer.append('\\')
                    } else {
                        if (c == '"') {
                            inString = false
                        }
                        buffer.append(c)
                    }
                }
            } else {
                if (c == '"') {
                    inString = true
                    buffer.append(c)
                } else if (c == ',') {
                    bufferedReader.mark(64)
                    var nextNonWs: Char? = null
                    var peekInt = bufferedReader.read()
                    while (peekInt != -1) {
                        val peekChar = peekInt.toChar()
                        if (!peekChar.isWhitespace()) {
                            nextNonWs = peekChar
                            break
                        }
                        peekInt = bufferedReader.read()
                    }
                    if (nextNonWs == '}' || nextNonWs == ']') {
                        bufferedReader.reset()
                    } else {
                        bufferedReader.reset()
                        buffer.append(c)
                    }
                } else {
                    buffer.append(c)
                }
            }
        }
    }

    override fun close() {
        bufferedReader.close()
    }
}

class DataParser {



    companion object {
        fun autoFixJson(jsonStr: String): String {
            if (jsonStr.isBlank()) return jsonStr

            var fixed = jsonStr.trim()
            
            // 1. Remove markdown code fences if present (e.g. ```json ... ```)
            if (fixed.startsWith("```json", ignoreCase = true)) {
                fixed = fixed.removePrefix("```json").removePrefix("```JSON").trim()
            } else if (fixed.startsWith("```")) {
                fixed = fixed.removePrefix("```").trim()
            }
            if (fixed.endsWith("```")) {
                fixed = fixed.removeSuffix("```").trim()
            }

            // 2. Remove trailing commas in objects and arrays
            fixed = fixed.replace(Regex(""",(\s*[}\]])"""), "$1")

            // 3. Fix unescaped backslashes in JSON strings, especially LaTeX commands.
            // In valid JSON: \" is escaped quote, \\ is escaped backslash, \/ is escaped slash.
            // \uXXXX is valid ONLY if followed by exactly 4 hex digits [0-9a-fA-F]{4}.
            // Control escapes \n, \r, \t are valid for actual linebreaks/tabs, but when followed by
            // LaTeX command names (like \neq, \nu, \times, \theta, \text, \right, etc.), or for \b (beta, bar, etc.)
            // and \f (frac, forall, etc.), they are LaTeX commands and MUST have their backslash escaped as \\!
            val sb = StringBuilder(fixed.length + 128)
            var inString = false
            var i = 0
            val len = fixed.length
            
            while (i < len) {
                val c = fixed[i]
                if (!inString) {
                    if (c == '"') {
                        inString = true
                    }
                    sb.append(c)
                    i++
                } else {
                    if (c == '"') {
                        inString = false
                        sb.append(c)
                        i++
                    } else if (c == '\\') {
                        if (i + 1 >= len) {
                            sb.append("\\\\")
                            i++
                        } else {
                            val next = fixed[i + 1]
                            if (next == '\\') {
                                // Check if AI double-escaped a plain newline as \\n or \\r\\n inside JSON
                                val afterSecondSlash = if (i + 2 < len) fixed.substring(i + 2) else ""
                                if (afterSecondSlash.startsWith("r\\n")) {
                                    val restAfterN = afterSecondSlash.substring(2)
                                    val isLatexN = restAfterN.startsWith("neq") || restAfterN.startsWith("nabla") ||
                                            restAfterN.startsWith("nearrow") || restAfterN.startsWith("neg") ||
                                            restAfterN.startsWith("nu") || restAfterN.startsWith("natural") ||
                                            restAfterN.startsWith("nless") || restAfterN.startsWith("ngtr") ||
                                            restAfterN.startsWith("nleq") || restAfterN.startsWith("ngeq") ||
                                            restAfterN.startsWith("nsubseteq") || restAfterN.startsWith("nsupseteq") ||
                                            restAfterN.startsWith("nmid") || restAfterN.startsWith("nparallel") ||
                                            restAfterN.startsWith("nwarrow") || restAfterN.startsWith("notin")
                                    if (!isLatexN) {
                                        sb.append("\\n")
                                        i += 5
                                        continue
                                    }
                                } else if (afterSecondSlash.startsWith("n")) {
                                    val restAfterN = afterSecondSlash
                                    val isLatexN = (restAfterN.startsWith("neq") && (restAfterN.length == 3 || !restAfterN[3].isLetter())) ||
                                            (restAfterN.startsWith("nabla") && (restAfterN.length == 5 || !restAfterN[5].isLetter())) ||
                                            (restAfterN.startsWith("nearrow") && (restAfterN.length == 7 || !restAfterN[7].isLetter())) ||
                                            (restAfterN.startsWith("neg") && (restAfterN.length == 3 || !restAfterN[3].isLetter())) ||
                                            (restAfterN.startsWith("nu") && (restAfterN.length == 2 || !restAfterN[2].isLetter())) ||
                                            (restAfterN.startsWith("natural") && (restAfterN.length == 7 || !restAfterN[7].isLetter())) ||
                                            (restAfterN.startsWith("nless") || restAfterN.startsWith("ngtr") ||
                                            restAfterN.startsWith("nleq") || restAfterN.startsWith("ngeq") ||
                                            restAfterN.startsWith("nsubseteq") || restAfterN.startsWith("nsupseteq") ||
                                            restAfterN.startsWith("nmid") || restAfterN.startsWith("nparallel") ||
                                            restAfterN.startsWith("nwarrow")) ||
                                            (restAfterN.startsWith("notin") && (restAfterN.length == 5 || !restAfterN[5].isLetter())) ||
                                            (restAfterN.startsWith("ne") && (restAfterN.length == 2 || (!restAfterN[2].isLetter() && restAfterN[2] != '.' && restAfterN[2] != ')'))) ||
                                            (restAfterN.startsWith("ni") && (restAfterN.length == 2 || (!restAfterN[2].isLetter() && restAfterN[2] != '.' && restAfterN[2] != ')'))) ||
                                            (restAfterN.startsWith("not") && (restAfterN.length == 3 || !restAfterN[3].isLetter()))
                                    if (!isLatexN) {
                                        sb.append("\\n")
                                        i += 3
                                        continue
                                    }
                                }
                                sb.append("\\\\")
                                i += 2
                            } else if (next == '"' || next == '/') {
                                // Valid JSON escape: \" or \/
                                sb.append('\\').append(next)
                                i += 2
                            } else if (next == 'u') {
                                // Check if followed by 4 hex digits
                                val isHex4 = (i + 5 < len) && (2..5).all { offset ->
                                    val h = fixed[i + offset]
                                    (h in '0'..'9') || (h in 'a'..'f') || (h in 'A'..'F')
                                }
                                if (isHex4) {
                                    sb.append("\\u")
                                    i += 2
                                } else {
                                    // LaTeX command starting with \u (e.g. \underbrace, \uparrow, \upsilon)
                                    sb.append("\\\\u")
                                    i += 2
                                }
                            } else if (next == 'f') {
                                // In AI question text \f is ALWAYS LaTeX (\frac, \forall, \flat)
                                sb.append("\\\\f")
                                i += 2
                            } else if (next == 'b') {
                                // In AI question text \b is ALWAYS LaTeX (\beta, \bar, \begin, \binom, \bullet, etc.)
                                sb.append("\\\\b")
                                i += 2
                            } else if (next == 't') {
                                val rest = fixed.substring(i + 1)
                                val isLatexT = rest.startsWith("times") || rest.startsWith("theta") ||
                                        rest.startsWith("text") || rest.startsWith("tan") ||
                                        rest.startsWith("tau") || rest.startsWith("to") ||
                                        rest.startsWith("tilde") || rest.startsWith("therefore") ||
                                        rest.startsWith("triangle") || rest.startsWith("tfrac") ||
                                        rest.startsWith("top")
                                if (isLatexT) {
                                    sb.append("\\\\t")
                                } else {
                                    sb.append("\\t")
                                }
                                i += 2
                            } else if (next == 'r') {
                                val rest = fixed.substring(i + 1)
                                val isLatexR = rest.startsWith("right") || rest.startsWith("rho") ||
                                        rest.startsWith("rightarrow") || rest.startsWith("Rightarrow") ||
                                        rest.startsWith("rangle") || rest.startsWith("rfloor") ||
                                        rest.startsWith("rceil") || rest.startsWith("Re") ||
                                        rest.startsWith("root")
                                if (isLatexR) {
                                    sb.append("\\\\r")
                                } else {
                                    sb.append("\\r")
                                }
                                i += 2
                            } else if (next == 'n') {
                                val rest = fixed.substring(i + 1)
                                val isLatexN = rest.startsWith("neq") || rest.startsWith("nabla") ||
                                        rest.startsWith("nearrow") || rest.startsWith("neg") ||
                                        rest.startsWith("nu") ||
                                        (rest.startsWith("ne") && (rest.length == 2 || !rest[2].isLetter())) ||
                                        (rest.startsWith("not") && (rest.length == 3 || !rest[3].isLetter()))
                                if (isLatexN) {
                                    sb.append("\\\\n")
                                } else {
                                    sb.append("\\n")
                                }
                                i += 2
                            } else {
                                // Any other character (e.g. \s, \a, \d, \p, \alpha, \sqrt, \[, \], \(, \), \$, etc.)
                                sb.append("\\\\").append(next)
                                i += 2
                            }
                        }
                    } else {
                        sb.append(c)
                        i++
                    }
                }
            }
            return sb.toString()
        }

        suspend fun loadInitialData(context: Context, repository: QuizRepository) {
            withContext(Dispatchers.IO) {
                try {
                    val prefs = com.example.data.SecurePrefs.get(context)
                    val isFirstRun = prefs.getBoolean("isFirstRun", true)
                    
                    val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    
                    // Cleanup stale temporary cache files
                    try {
                        val cacheDir = context.cacheDir
                        val tempFiles = cacheDir.listFiles { file -> 
                            file.isFile && (file.name.startsWith("peek") || file.name.startsWith("import")) && file.name.endsWith(".qbook")
                        } ?: emptyArray()
                        tempFiles.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600000) it.delete() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val quizerDir = File(basePath, "Quizer")
                    if (!quizerDir.exists()) {
                        quizerDir.mkdirs()
                    }
                    
                    // Copy bundled books from assets on first run or if missing
                    try {
                        val assetManager = context.assets
                        val bundledFiles = assetManager.list("bundled_books") ?: emptyArray()
                        for (filename in bundledFiles) {
                            val outFile = File(quizerDir, filename)
                            if (!outFile.exists()) {
                                assetManager.open("bundled_books/$filename").use { input ->
                                    java.io.FileOutputStream(outFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    if (isFirstRun) {
                        prefs.edit().putBoolean("isFirstRun", false).apply()
                    }
                    
                    syncBooksFromDirectory(context, repository)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val syncMutex = kotlinx.coroutines.sync.Mutex()

        fun extractImagesFromQBook(file: File, destDir: File, context: Context? = null) {
            try {
                if (!destDir.exists()) destDir.mkdirs()
                val extractedFiles = mutableListOf<File>()
                java.util.zip.ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    val buffer = ByteArray(65536)
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        val isImage = name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
                                      name.endsWith(".jpeg", true) || name.endsWith(".webp", true) ||
                                      name.endsWith(".gif", true) || name.endsWith(".svg", true)
                        if (isImage) {
                            val fileName = name.substringAfterLast("/")
                            val destFile = File(destDir, fileName)
                            if (!destFile.exists() || destFile.length() == 0L) {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                            extractedFiles.add(destFile)
                        }
                    }
                }
                if (context != null && extractedFiles.isNotEmpty()) {
                    MediaScannerHelper.scanFiles(context, extractedFiles)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        suspend fun syncBooksFromDirectory(context: Context, repository: QuizRepository) {
            withContext(Dispatchers.IO) {
                syncMutex.withLock {
                    try {
                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    
                    // Cleanup stale temporary cache files
                    try {
                        val cacheDir = context.cacheDir
                        val tempFiles = cacheDir.listFiles { file -> 
                            file.isFile && (file.name.startsWith("peek") || file.name.startsWith("import")) && file.name.endsWith(".qbook")
                        } ?: emptyArray()
                        tempFiles.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600000) it.delete() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val quizerDir = File(basePath, "Quizer")
                    if (!quizerDir.exists()) {
                        quizerDir.mkdirs()
                    }
                    
                    val rawFiles = quizerDir.listFiles() ?: return@withContext
                    val files = rawFiles.filter { file -> file.isFile && file.name.endsWith(".qbook", ignoreCase = true) }
                    
                    val settingsManager = SettingsManager(context)
                    val updatedRemoved = settingsManager.removedBooks.toMutableSet()
                    
                    val folderBookIds = mutableListOf<String>()
                    val fileToBookId = mutableMapOf<File, String>()
                    val fileToVersion = mutableMapOf<File, String>()
                    
                    for (file in files) {
                        var id = file.nameWithoutExtension
                        var version = "1.0"
                        try {
                            java.util.zip.ZipFile(file).use { zip ->
                                var metaEntry = zip.getEntry("book_meta.json")
                                if (metaEntry == null) {
                                    val entries = zip.entries()
                                    while (entries.hasMoreElements()) {
                                        val entry = entries.nextElement()
                                        if (entry.name.equals("book_meta.json", ignoreCase = true) || entry.name.endsWith("/book_meta.json", ignoreCase = true)) {
                                            metaEntry = entry
                                            break
                                        }
                                    }
                                }
                                if (metaEntry != null) {
                                    val reader = zip.getInputStream(metaEntry).bufferedReader()
                                    val jsonStr = autoFixJson(reader.readText())
                                    val gson = SharedGson.lenient
                                    val meta = gson.fromJson(jsonStr, QBookMeta::class.java)
                                    if (!meta?.id.isNullOrBlank()) id = meta!!.id
                                    if (!meta?.version.isNullOrBlank()) version = meta!!.version
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        folderBookIds.add(id)
                        fileToBookId[file] = id
                        fileToVersion[file] = version
                        
                        // If file is physically present in Quizer folder, remove from removedBooks blacklist
                        updatedRemoved.remove(id)
                        updatedRemoved.remove(file.nameWithoutExtension)
                    }
                    settingsManager.removedBooks = updatedRemoved
                    
                    val currentBooksInDb = repository.getAllBooksSync().associateBy { it.id }
                    
                    for (file in files) {
                        val bookId = fileToBookId[file] ?: file.nameWithoutExtension
                        val existingBook = currentBooksInDb[bookId]
                        val fileVersionStr = fileToVersion[file] ?: "1.0"
                        val dbVersionStr = existingBook?.bookVersion ?: "1.0"
                        
                        // Parse if not in DB, has 0 questions, or version changed
                        if (existingBook == null || existingBook.totalQuestions == 0 || fileVersionStr != dbVersionStr) {
                            parseAndInsertFromQBook(context, repository, file, allowAutoFix = true)
                        } else {
                            // Ensure image assets exist even if questions already parsed
                            val bookImagesDir = File(quizerDir, "$bookId/images")
                            if (!bookImagesDir.exists() || bookImagesDir.listFiles().isNullOrEmpty()) {
                                extractImagesFromQBook(file, bookImagesDir, context)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

        @Suppress("SENSELESS_COMPARISON")
        suspend fun processFileContent(context: Context, repository: QuizRepository, fileContent: String, isExplicitUserImport: Boolean = false, allowAutoFix: Boolean = false, onProgress: ((String) -> Unit)? = null): Pair<String?, String?> {
            return withContext(Dispatchers.IO) {
                try {
                    onProgress?.invoke("Parsing JavaScript content...")
                    val gson = SharedGson.lenient
                    
                    var identityJsonStr = extractJsArray(fileContent, "window.BOOK_IDENTITY")
                    if (identityJsonStr == null) {
                        identityJsonStr = extractJsArray(fileContent, "window.INDENTITY")
                    }
                    if (identityJsonStr == null) return@withContext Pair("Missing window.BOOK_IDENTITY or window.INDENTITY array.", null)
                    
                    onProgress?.invoke("Verifying Book Identity...")
                    val identityType = object : TypeToken<List<RawBookIdentity>>() {}.type
                    val identities: List<RawBookIdentity> = gson.fromJson(identityJsonStr, identityType) ?: emptyList()
                    val identity = identities.firstOrNull() ?: return@withContext Pair("Identity array is empty.", null)

                    val bookId = identity.id ?: return@withContext Pair("Book Identity lacks 'id'.", null)
                    
                    val settingsManager = SettingsManager(context)
                    if (settingsManager.removedBooks.contains(bookId)) {
                        if (isExplicitUserImport) {
                            // User is explicitly adding this book back, so un-remove it.
                            val updatedRemoved = settingsManager.removedBooks.toMutableSet()
                            updatedRemoved.remove(bookId)
                            settingsManager.removedBooks = updatedRemoved
                        } else {
                            return@withContext Pair(null, bookId) // Silently ignore removed books
                        }
                    }

                    if (identity.dataVariable == null) return@withContext Pair("Book Identity lacks 'dataVariable'.", bookId)
                    if (identity.updateId == null) return@withContext Pair("Book Identity lacks 'updateId'.", bookId)

                    val dataVar = identity.dataVariable
                    onProgress?.invoke("Extracting questions from window.$dataVar...")
                    val questionsJsonStr = extractJsArray(fileContent, "window.$dataVar") 
                        ?: return@withContext Pair("Missing window.$dataVar array.", bookId)
                    
                    onProgress?.invoke("Verifying Questions Data...")
                    
                    val isUpdate = dataVar == "UPDATE"
                    var existingBook: BookEntity? = null
                    var existingUpdatesList = mutableListOf<Map<String, List<Long>>>()

                    if (isUpdate) {
                        onProgress?.invoke("Verifying Book Update parameters...")
                        val targetBookId = identity.id
                        existingBook = repository.getAllBooksSync().find { it.id == targetBookId } 
                        if (existingBook == null) {
                            return@withContext Pair("Target book for update does not exist.", bookId)
                        }
                        if (existingBook.updateId != identity.updateId) {
                            return@withContext Pair("Update ID does not match the target book.", bookId)
                        }
                        if (identity.questionIdEnd == null) return@withContext Pair("Update Identity lacks 'questionIdEnd'.", bookId)
                        
                        val existingUpdatesType = object : TypeToken<List<Map<String, List<Long>>>>() {}.type
                        existingUpdatesList = 
                            if (existingBook.updateVersions.isNotEmpty() && existingBook.updateVersions != "[]") {
                                try {
                                    gson.fromJson(existingBook.updateVersions, existingUpdatesType) ?: mutableListOf()
                                } catch (e: Exception) { mutableListOf() }
                            } else mutableListOf()

                        val pendingUpdateIds = mutableSetOf<String>()
                        if (identity.updateVersion != null) {
                            pendingUpdateIds.addAll(identity.updateVersion.flatMap { it.keys })
                        }
                        if (identity.updateId != null) {
                            pendingUpdateIds.add(identity.updateId)
                        }

                        val existingUpdateIds = existingUpdatesList.flatMap { it.keys }.toSet()
                        for (pending in pendingUpdateIds) {
                            if (existingUpdateIds.contains(pending)) {
                                return@withContext Pair("$pending already applied.", bookId)
                            }
                        }
                    } else {
                        // New book registration
                        onProgress?.invoke("Verifying New Book parameters...")
                        if (identity.name == null) return@withContext Pair("Book Identity lacks 'name'.", bookId)
                        if (identity.questionIdStart == null) return@withContext Pair("Book Identity lacks 'questionIdStart'.", bookId)
                        if (identity.questionIdEnd == null) return@withContext Pair("Book Identity lacks 'questionIdEnd'.", bookId)
                    }

                    onProgress?.invoke("Parsing and Saving Questions...")
                    val jsonReader = com.google.gson.stream.JsonReader(java.io.StringReader(questionsJsonStr))
                    jsonReader.isLenient = true
                    
                    try {
                        jsonReader.beginArray()
                    } catch (e: Exception) {
                        return@withContext Pair("Questions data is not a valid JSON array.", bookId)
                    }

                    val existingQuestionsMap = repository.getQuestionsForBookSync(bookId).associateBy { it.id }
                    val questionIds = mutableSetOf<Long>()
                    val questionEntities = mutableListOf<QuestionEntity>()
                    val categorySet = mutableSetOf<String>()
                    val headerRanges = mutableListOf<HeaderRange>()
                    var totalParsedQuestions = 0
                    
                    while (jsonReader.hasNext()) {
                        val raw: RawQuestion = gson.fromJson(jsonReader, RawQuestion::class.java)
                        
                        if (raw.id == null) return@withContext Pair("A question lacks 'id'.", bookId)
                        if (!questionIds.add(raw.id)) return@withContext Pair("Duplicate Question ID: ${raw.id} found.", bookId)
                        if (raw.path == null) return@withContext Pair("Question ${raw.id} lacks 'path'.", bookId)
                        if (raw.question == null) return@withContext Pair("Question ${raw.id} lacks 'question'.", bookId)
                        if (raw.options == null) return@withContext Pair("Question ${raw.id} lacks 'options'.", bookId)
                        if (raw.correct == null) return@withContext Pair("Question ${raw.id} lacks 'correct'.", bookId)
                        
                        var selfHeader: String? = null
                        if (raw.header != null) {
                            val startId = raw.header.startId ?: raw.id
                            val endId = raw.header.endId ?: raw.id
                            val text = raw.header.text
                            if (!text.isNullOrEmpty()) {
                                headerRanges.add(HeaderRange(startId, endId, text))
                                if (startId == endId && startId == raw.id) {
                                    selfHeader = text
                                }
                            }
                        }
                        if (raw.questionHeader != null) {
                            if (raw.questionHeader.isJsonArray) {
                                val arr = raw.questionHeader.asJsonArray
                                if (arr.size() >= 3) {
                                    try {
                                        val startId = arr.get(0).asLong
                                        val endId = arr.get(1).asLong
                                        val text = arr.get(2).asString
                                        if (text.isNotEmpty()) {
                                            headerRanges.add(HeaderRange(startId, endId, text))
                                            if (startId == endId && startId == raw.id) {
                                                selfHeader = text
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else if (raw.questionHeader.isJsonPrimitive && raw.questionHeader.asJsonPrimitive.isString) {
                                val text = raw.questionHeader.asString
                                if (text.isNotEmpty()) {
                                    selfHeader = text
                                }
                            }
                        }
                        
                        val resolvedHeaderRange = headerRanges.lastOrNull { raw.id in it.startId..it.endId }; val resolvedHeader = if (selfHeader != null) "{\"startId\": ${raw.id}, \"endId\": ${raw.id}, \"text\": \"${selfHeader.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else if (resolvedHeaderRange != null) "{\"startId\": ${resolvedHeaderRange.startId}, \"endId\": ${resolvedHeaderRange.endId}, \"text\": \"${resolvedHeaderRange.headerText.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else null
                        
                        val cat = raw.category ?: raw.path.split(">").lastOrNull()?.trim() ?: "Unknown"
                        categorySet.add(cat)
                        
                        val existingGlobalId = existingQuestionsMap[raw.id]?.globalId ?: UUID.randomUUID().toString()
                        questionEntities.add(
                            QuestionEntity(
                                globalId = existingGlobalId,
                                id = raw.id,
                                bookId = if(isUpdate) existingBook?.id ?: bookId else bookId,
                                path = raw.path,
                                category = cat,
                                question = raw.question,
                                options = gson.toJson(raw.options),
                                correctIndex = raw.correct,
                                explanation = raw.explanation?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                section = raw.section?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                extraOptions = gson.toJson(raw.extraOptions ?: emptyList<String>()),
                                exam = raw.exam?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                year = raw.year?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                img = raw.img,
                                questionHeader = resolvedHeader
                            )
                        )
                        totalParsedQuestions++
                    }
                    jsonReader.endArray()
                    
                    if (totalParsedQuestions == 0) return@withContext Pair("Questions array is empty.", bookId)
                    
                    onProgress?.invoke("Committing Book and Questions...")
                    val db = AppDatabase.getDatabase(context)
                    db.withTransaction {
                        if (isUpdate && existingBook != null) {
                            val newBookVersion = identity.bookVersion ?: existingBook.bookVersion
                            
                            if (identity.updateVersion != null) {
                                existingUpdatesList.addAll(identity.updateVersion)
                            } else if (identity.updateId != null && identity.questionIdStart != null && identity.questionIdEnd != null) {
                                existingUpdatesList.add(mapOf<String, List<Long>>((identity.updateId ?: "") to listOf<Long>((identity.questionIdStart ?: 0L).toLong(), (identity.questionIdEnd ?: 0L).toLong())))
                            }

                            val updatedBook = existingBook.copy(
                                totalQuestions = existingBook.totalQuestions + totalParsedQuestions,
                                bookVersion = newBookVersion,
                                updateVersions = gson.toJson(existingUpdatesList),
                                questionIdEnd = identity.questionIdEnd ?: existingBook.questionIdEnd
                            )
                            for (chunk in questionEntities.chunked(1000)) {
                                repository.insertQuestions(chunk)
                            }
                            repository.insertBook(updatedBook)
                        } else {
                            val existingCheck = repository.getBookSync(bookId)
                            if (existingCheck != null) {
                                repository.deleteQuestionsForBook(bookId)
                                repository.deleteAssociatedDataForBook(bookId)
                            }
                            for (chunk in questionEntities.chunked(1000)) {
                                repository.insertQuestions(chunk)
                            }
                            repository.insertBook(
                                BookEntity(
                                    id = bookId,
                                    name = identity.name ?: "Unknown Book",
                                    totalQuestions = totalParsedQuestions,
                                    totalCategories = categorySet.size,
                                    dataVariable = identity.dataVariable,
                                    updateId = identity.updateId,
                                    bookVersion = identity.bookVersion,
                                    updateVersions = if (identity.updateVersion != null) gson.toJson(identity.updateVersion) else "[]",
                                    questionIdStart = identity.questionIdStart ?: 0L,
                                    questionIdEnd = identity.questionIdEnd ?: 0L,
                                    hierarchyLabels = if (identity.hierarchyLabels != null) gson.toJson(identity.hierarchyLabels) else "[]",
                                    roadmapLeafLevel = identity.roadmapLeafLevel ?: 0
                                )
                            )
                        }
                    }
                    return@withContext Pair(null, bookId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext Pair("Exception occurred during parsing: ${e.message}", null)
                }
            }
        }
        
        private fun extractJsArray(fileContent: String, varName: String): String? {
            val idx = fileContent.indexOf(varName)
            if (idx == -1) return null
            val startIdx = fileContent.indexOf('[', idx)
            if (startIdx == -1) return null
            
            var depth = 0
            var endIdx = -1
            for (i in startIdx until fileContent.length) {
                if (fileContent[i] == '[') depth++
                else if (fileContent[i] == ']') {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
            if (endIdx == -1) return null
            
            var jsonString = fileContent.substring(startIdx, endIdx + 1)
            jsonString = jsonString.replace(Regex(""";\s*(?=[\n\r}”"'])"""), ",")
            jsonString = jsonString.replace(Regex("""^\s*([a-zA-Z0-9_]+)\s*:""", RegexOption.MULTILINE), "\"$1\":")
            jsonString = jsonString.replace(Regex("""[{,]\s*([a-zA-Z0-9_]+)\s*:""")) { matchResult ->
                matchResult.value.replace(matchResult.groupValues[1], "\"${matchResult.groupValues[1]}\"")
            }
            return jsonString
        }

        
        suspend fun parseAndInsertFromQBook(context: Context, repository: QuizRepository, file: File, allowAutoFix: Boolean = false, isPermanentUpdate: Boolean = false): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val gson = SharedGson.lenient
                    var meta: QBookMeta? = null
                    
                    val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    
                    // Cleanup stale temporary cache files
                    try {
                        val cacheDir = context.cacheDir
                        val tempFiles = cacheDir.listFiles { f -> 
                            f.isFile && (f.name.startsWith("peek") || f.name.startsWith("import")) && f.name.endsWith(".qbook")
                        } ?: emptyArray()
                        tempFiles.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600000) it.delete() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val quizerDir = File(basePath, "Quizer")
                    var bookIdStr = ""
                    var totalQuestions = 0
                    var qIdStart = -1L
                    var qIdEnd = -1L
                    val categorySet = mutableSetOf<String>()
                    val headerRanges = mutableListOf<HeaderRange>()
                    val parsedQuestions = ArrayList<QuestionEntity>()
                    
                    java.util.zip.ZipFile(file).use { zip ->
                        var metaEntry = zip.getEntry("book_meta.json")
                        if (metaEntry == null) {
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (entry.name.equals("book_meta.json", ignoreCase = true) || entry.name.endsWith("/book_meta.json", ignoreCase = true)) {
                                    metaEntry = entry
                                    break
                                }
                            }
                        }
                        if (metaEntry != null) {
                            val reader = zip.getInputStream(metaEntry).bufferedReader()
                            val originalJson = reader.readText()
                            val jsonStr = autoFixJson(originalJson)
                            if (originalJson != jsonStr && !allowAutoFix) {
                                return@withContext "FIX_REQUIRED|Syntax errors found in book_meta.json. We can auto-fix them."
                            }
                            meta = gson.fromJson(jsonStr, QBookMeta::class.java)
                            bookIdStr = if (!meta?.id.isNullOrBlank()) meta!!.id else file.nameWithoutExtension
                        } else {
                            bookIdStr = file.nameWithoutExtension
                        }
                        
                        var qEntry = zip.getEntry("questions.json")
                        if (qEntry == null) {
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (entry.name.equals("questions.json", ignoreCase = true) || entry.name.endsWith("/questions.json", ignoreCase = true)) {
                                    qEntry = entry
                                    break
                                }
                            }
                        }
                        if (qEntry != null) {
                            val isUpdate = meta?.type == "UPDATE" || meta?.type == "update"
                            
                            // Ensure backup_questions.json exists on disk for potential editing
                            if (bookIdStr.isNotEmpty()) {
                                val bookDir = File(quizerDir, bookIdStr)
                                if (!bookDir.exists()) bookDir.mkdirs()
                                val backupFile = File(bookDir, "backup_questions.json")
                                if (!backupFile.exists() || backupFile.length() == 0L) {
                                    try {
                                        zip.getInputStream(qEntry).use { input ->
                                            java.io.FileOutputStream(backupFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            
                            val existingQuestionsMap = repository.getQuestionsForBookSync(bookIdStr).associateBy { it.id }
                            var parseSuccess = false
                            try {
                                zip.getInputStream(qEntry).use { stream ->
                                    val sanitizingReader = JsonSanitizingReader(stream.bufferedReader())
                                    val jsonReader = com.google.gson.stream.JsonReader(sanitizingReader)
                                    jsonReader.isLenient = true
                                    jsonReader.beginArray()
                                    
                                    val seenQuestionIds = mutableSetOf<Long>()
                                    while (jsonReader.hasNext()) {
                                        val q = gson.fromJson<QBookQuestion>(jsonReader, QBookQuestion::class.java)
                                        if (!seenQuestionIds.add(q.id)) {
                                            return@withContext "Invalid .qbook: Duplicate question ID ${q.id} found."
                                        }
                                        totalQuestions++
                                        if (qIdStart == -1L) qIdStart = q.id
                                        qIdEnd = q.id
                                        
                                        val cat = q.metadata?.category ?: ""
                                        categorySet.add(cat)
                                        
                                        var selfHeader: String? = null
                                        if (q.header != null) {
                                            val startId = q.header.startId ?: q.id
                                            val endId = q.header.endId ?: q.id
                                            val text = q.header.text ?: ""
                                            if (text.isNotEmpty()) {
                                                headerRanges.add(HeaderRange(startId, endId, text))
                                                if (startId == endId && startId == q.id) {
                                                    selfHeader = text
                                                }
                                            }
                                        }
                                        val resolvedHeaderRange = headerRanges.lastOrNull { q.id in it.startId..it.endId }
                                        val resolvedHeader = if (selfHeader != null) "{\"startId\": ${q.id}, \"endId\": ${q.id}, \"text\": \"${selfHeader.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else if (resolvedHeaderRange != null) "{\"startId\": ${resolvedHeaderRange.startId}, \"endId\": ${resolvedHeaderRange.endId}, \"text\": \"${resolvedHeaderRange.headerText.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else null
                                        headerRanges.removeAll { it.endId < q.id }
                                        
                                        val correctIndexStr = if (q.correctOptionIds.isNotEmpty()) {
                                            val firstCorrectId = q.correctOptionIds[0]
                                            val idx = q.options.indexOfFirst { it.id == firstCorrectId }
                                            if (idx != -1) idx else 0
                                        } else 0
                                        
                                        val existingGlobalId = existingQuestionsMap[q.id]?.globalId ?: UUID.randomUUID().toString()
                                        parsedQuestions.add(
                                            QuestionEntity(
                                                globalId = existingGlobalId,
                                                id = q.id,
                                                bookId = bookIdStr,
                                                path = q.metadata?.path ?: "",
                                                category = cat,
                                                question = q.question.text,
                                                options = gson.toJson(q.options.map { it.text }),
                                                correctIndex = correctIndexStr,
                                                explanation = q.explanation?.brief?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                                section = "",
                                                extraOptions = gson.toJson(q.extraOptions?.map { it.text } ?: emptyList<String>()),
                                                exam = q.metadata?.exam?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                                exams = q.metadata?.exams?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                                                explanationTable = q.explanation?.table?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                                                year = q.metadata?.year?.toString()?.trim()?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                                                img = q.question.image?.trim()?.takeIf { !it.equals("null", ignoreCase = true) },
                                                questionHeader = resolvedHeader,
                                                advancedOptions = gson.toJson(q.options),
                                                advancedCorrectIds = gson.toJson(q.correctOptionIds),
                                                detailedExplanation = q.explanation?.detailed?.trim()?.takeIf { !it.equals("null", ignoreCase = true) },
                                                explanationImage = q.explanation?.image?.trim()?.takeIf { !it.equals("null", ignoreCase = true) },
                                                multipleCorrect = q.correctOptionIds.size > 1,
                                                format = q.question.format,
                                                tableData = if (q.question.table != null) gson.toJson(q.question.table) else null,
                                                bottomText = q.question.bottomText
                                            )
                                        )
                                    }
                                    jsonReader.endArray()
                                    parseSuccess = true
                                }
                            } catch (e: Exception) {
                                Log.e("DataParser", "Error streaming questions for $bookIdStr", e)
                                return@withContext "Error reading questions from $bookIdStr: ${e.message}"
                            }
                        }
                        
                        // Extract images
                        if (bookIdStr.isNotEmpty()) {
                            val bookImagesDir = File(quizerDir, "$bookIdStr/images")
                            extractImagesFromQBook(file, bookImagesDir)
                        }
                    } // end zip
                    
                    if (meta == null || totalQuestions == 0) {
                        return@withContext "Invalid .qbook format: Missing meta or questions."
                    }
                    
                    val isUpdate = meta?.type == "UPDATE" || meta?.type == "update"
                    val existingBook = repository.getBookSync(bookIdStr)

                    if (isUpdate && existingBook == null) {
                        return@withContext "Cannot apply this update: install the base book first."
                    }

                    if (isUpdate && existingBook != null) {
                        val existingUpdatesType = object : TypeToken<List<Map<String, List<Long>>>>() {}.type
                        val existingUpdatesList: MutableList<Map<String, List<Long>>> = try {
                            gson.fromJson(existingBook.updateVersions, existingUpdatesType) ?: mutableListOf()
                        } catch (e: Exception) { mutableListOf() }
                        
                        val pendingUpdateIds = mutableSetOf<String>()
                        if (meta?.updateVersion != null) {
                            pendingUpdateIds.addAll((meta?.updateVersion ?: emptyList()).flatMap { it.keys })
                        }
                        if (meta?.updateId != null) {
                            pendingUpdateIds.add((meta?.updateId ?: ""))
                        }
                        
                        val existingUpdateIds = existingUpdatesList.flatMap { it.keys }.toSet()
                        for (pending in pendingUpdateIds) {
                            if (existingUpdateIds.contains(pending)) {
                                return@withContext "Invalid .qbook: Update $pending already applied."
                            }
                        }
                        
                        if (!isPermanentUpdate) {
                            if (meta?.updateVersion != null) {
                                existingUpdatesList.addAll((meta?.updateVersion ?: emptyList()))
                            } else if (meta?.updateId != null && meta?.questionIdStart != null && meta?.questionIdEnd != null) {
                                existingUpdatesList.add(mapOf<String, List<Long>>((meta?.updateId ?: "") to listOf<Long>((meta?.questionIdStart ?: 0L).toLong(), (meta?.questionIdEnd ?: 0L).toLong())))
                            }
                        }
                        
                        val updatedBook = existingBook.copy(
                            name = if (!meta?.name.isNullOrBlank() && meta?.name != "Unknown Book") meta!!.name else existingBook.name,
                            totalQuestions = existingBook.totalQuestions + totalQuestions,
                            bookVersion = meta?.version ?: existingBook.bookVersion,
                            updateVersions = gson.toJson(existingUpdatesList),
                            questionIdEnd = meta?.questionIdEnd ?: existingBook.questionIdEnd,
                            hierarchyLabels = if (meta?.hierarchyLabels != null) gson.toJson(meta.hierarchyLabels) else existingBook.hierarchyLabels
                        )

                        AppDatabase.getDatabase(context).withTransaction {
                            for (chunk in parsedQuestions.chunked(1000)) {
                                repository.insertQuestions(chunk)
                            }
                            repository.insertBook(updatedBook)
                        }
                        return@withContext bookIdStr
                    } else {
                        val bookEntity = BookEntity(
                            id = bookIdStr,
                            name = if (!meta?.name.isNullOrBlank() && meta?.name != "Unknown Book") meta!!.name else file.nameWithoutExtension.replace("_", " "),
                            totalQuestions = totalQuestions,
                            totalCategories = categorySet.size,
                            dataVariable = "QBOOK",
                            updateId = "QBOOK_INITIAL",
                            bookVersion = meta?.version,
                            updateVersions = "[]",
                            questionIdStart = qIdStart,
                            questionIdEnd = qIdEnd,
                            hierarchyLabels = if (meta?.hierarchyLabels != null) gson.toJson(meta.hierarchyLabels) else "[]",
                            roadmapLeafLevel = 3
                        )

                        AppDatabase.getDatabase(context).withTransaction {
                            if (!isUpdate) {
                                repository.deleteQuestionsForBook(bookIdStr)
                            }
                            for (chunk in parsedQuestions.chunked(1000)) {
                                repository.insertQuestions(chunk)
                            }
                            repository.insertBook(bookEntity)
                        }
                        return@withContext bookIdStr
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext "Error extracting QBook: ${e.message}"
                }
            }
        }

        private suspend fun parseAndInsertFromFile(context: Context, repository: QuizRepository, bookId: String, bookName: String, file: File) {
            withContext(Dispatchers.IO) {
                try {
                    val fileContent = file.readText()
                    processFileContent(context, repository, fileContent, false, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun peekIdentity(context: Context, uri: android.net.Uri): RawBookIdentity? {
            try {
                val resolver = context.contentResolver
                val type = resolver.getType(uri)
                var fileName = ""
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: ""
                    }
                }
                val isZip = fileName.endsWith(".qbook", ignoreCase = true) || fileName.endsWith(".zip", ignoreCase = true) || uri.toString().endsWith(".qbook", ignoreCase = true) || uri.toString().endsWith(".zip", ignoreCase = true) || type == "application/zip" || type == "application/x-zip-compressed" || type == "application/octet-stream"
                
                if (isZip) {
                    var tempFile: File? = null
                    try {
                        tempFile = File.createTempFile("peek", ".qbook", context.cacheDir)
                        resolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        var identity: RawBookIdentity? = null
                        try {
                            java.util.zip.ZipFile(tempFile).use { zip ->
                                val metaEntry = zip.getEntry("book_meta.json")
                                if (metaEntry != null) {
                                    val reader = zip.getInputStream(metaEntry).bufferedReader()
                                    val jsonStr = autoFixJson(reader.readText())
                                    val gson = SharedGson.lenient
                                    val meta = gson.fromJson(jsonStr, QBookMeta::class.java)
                                    if (meta != null) {
                                        identity = RawBookIdentity(
                                            id = meta.id,
                                            name = meta.name,
                                            dataVariable = if (meta.type.equals("UPDATE", ignoreCase = true)) "UPDATE" else "QBOOK",
                                            updateId = meta.updateId,
                                            bookVersion = meta.version,
                                            updateVersion = meta.updateVersion,
                                            questionIdStart = meta.questionIdStart,
                                            questionIdEnd = meta.questionIdEnd,
                                            hierarchyLabels = meta.hierarchyLabels,
                                            roadmapLeafLevel = null
                                        )
                                    }
                                }
                            }
                        } catch(e:Exception){}
                        return identity
                    } finally {
                        tempFile?.delete()
                    }
                }

                return null
            } catch (e: Exception) {
                return null
            }
        }

        suspend fun parseAndInsertFromUri(context: Context, repository: QuizRepository, uri: android.net.Uri, allowAutoFix: Boolean = false, isPermanentUpdate: Boolean = false, onProgress: ((String) -> Unit)? = null): String? {
            return withContext(Dispatchers.IO) {
                var tempFile: File? = null
                try {
                    onProgress?.invoke("Opening file...")
                    val resolver = context.contentResolver
                    val type = resolver.getType(uri)
                    var fileName = ""
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: ""
                        }
                    }
                    val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    
                    // Cleanup stale temporary cache files
                    try {
                        val cacheDir = context.cacheDir
                        val tempFiles = cacheDir.listFiles { file -> 
                            file.isFile && (file.name.startsWith("peek") || file.name.startsWith("import")) && file.name.endsWith(".qbook")
                        } ?: emptyArray()
                        tempFiles.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600000) it.delete() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val quizerDir = File(basePath, "Quizer")
                    if (!quizerDir.exists()) quizerDir.mkdirs()
                    
                    // Unconditionally treat it as a qbook archive
                    onProgress?.invoke("Extracting qbook archive...")
                    tempFile = File.createTempFile("import", ".qbook", context.cacheDir)
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val identity = peekIdentity(context, android.net.Uri.fromFile(tempFile))
                    val isUpdate = identity?.dataVariable == "UPDATE"
                    val updateId = identity?.updateId ?: System.currentTimeMillis().toString()

                    val bookIdStr = parseAndInsertFromQBook(context, repository, tempFile, allowAutoFix, isPermanentUpdate)

                    if (bookIdStr != null && !bookIdStr.startsWith("Error") && !bookIdStr.startsWith("Invalid") && !bookIdStr.startsWith("FIX_REQUIRED")) {
                        if (isUpdate) {
                            if (isPermanentUpdate) {
                                onProgress?.invoke("Applying permanent update...")
                                // Export the combined DB back to original qbook file
                                val targetFile = File(quizerDir, "${bookIdStr}.qbook")
                                com.example.ui.screens.exportQBook(context, repository, bookIdStr, targetFile)
                            } else {
                                val updatesDir = File(quizerDir, "Updates")
                                if (!updatesDir.exists()) updatesDir.mkdirs()
                                val targetFile = File(updatesDir, "${bookIdStr}_${updateId}.qbook")
                                tempFile.copyTo(targetFile, overwrite = true)
                            }
                        } else {
                            val targetFile = File(quizerDir, "${bookIdStr}.qbook")
                            tempFile.copyTo(targetFile, overwrite = true)
                        }
                        
                        try {
                            val settingsManager = SettingsManager(context)
                            val updatedRemoved = settingsManager.removedBooks.toMutableSet()
                            if (updatedRemoved.remove(bookIdStr)) {
                                settingsManager.removedBooks = updatedRemoved
                            }
                            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            val prefs = context.getSharedPreferences("book_uris", Context.MODE_PRIVATE)
                            prefs.edit().putString(bookIdStr, uri.toString()).apply()
                        } catch(e: Exception){}
                        return@withContext null
                    } else {
                        return@withContext bookIdStr ?: "Unknown error"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext "Error loading file: ${e.message}"
                } finally {
                    tempFile?.delete()
                }
            }
        }

    }
}
