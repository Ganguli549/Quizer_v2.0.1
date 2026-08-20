package com.example.ui.screens

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.*
import com.google.gson.Gson

suspend fun exportQBook(context: Context, repository: QuizRepository, bookId: String, targetFileParam: File? = null): String? {
    return withContext(Dispatchers.IO) {
        try {
            val book = repository.getBookSync(bookId) ?: return@withContext "Book not found"
            val questions = repository.getQuestionsForBookSync(bookId).resolveHeaders()
            
            val meta = QBookMeta(
                id = book.id,
                name = book.name,
                version = book.bookVersion ?: "1.0",
                hierarchyLabels = emptyList(),
                updateVersion = try {
                    com.example.data.SharedGson.normal.fromJson(book.updateVersions, object : com.google.gson.reflect.TypeToken<List<Map<String, List<Long>>>>() {}.type)
                } catch(e: Exception) { null },
                questionIdStart = book.questionIdStart.takeIf { it > 0 },
                questionIdEnd = book.questionIdEnd.takeIf { it > 0 }
            )
            
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            
            val qBookQuestions = questions.map { q ->
                val typeOptionList = object : com.google.gson.reflect.TypeToken<List<QBookOption>>() {}.type
                val typeStringList = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                val extraStrType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                
                var extraOpts: List<QBookOption>? = null
                try {
                    val extras = gson.fromJson<List<String>>(q.extraOptions, extraStrType)
                    if (extras != null && extras.isNotEmpty()) {
                        extraOpts = extras.mapIndexed { idx, txt -> QBookOption(id = "E\$idx", text = txt) }
                    }
                } catch(e: Exception){}
                
                QBookQuestion(
                    id = q.id,
                    header = if (q.questionHeader != null) { val raw = q.questionHeader; if (raw.trim().startsWith("{")) { try { gson.fromJson(raw, QBookHeader::class.java) } catch(e: Exception) { QBookHeader(startId = q.id, endId = q.id, text = raw) } } else { QBookHeader(startId = q.id, endId = q.id, text = raw) } } else null,
                    question = QBookQuestionDetails(text = q.question, image = q.img, format = q.format, table = if (q.tableData != null) gson.fromJson(q.tableData, object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type) else null, bottomText = q.bottomText),
                    options = gson.fromJson(q.advancedOptions ?: "[]", typeOptionList) ?: emptyList(),
                    extraOptions = extraOpts,
                    correctOptionIds = gson.fromJson(q.advancedCorrectIds ?: "[]", typeStringList) ?: emptyList(),
                    explanation = QBookExplanation(
                        brief = q.explanation,
                        detailed = q.detailedExplanation ?: "",
                        image = q.explanationImage,
                        table = if (q.explanationTable != null) gson.fromJson(q.explanationTable, object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type) else null
                    ),
                    metadata = QBookMetadata(
                        path = q.path,
                        category = q.category,
                        exam = q.exam,
                        year = q.year.takeIf { it.isNotBlank() },
                        exams = if (q.exams != null) gson.fromJson(q.exams, object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookExam>>() {}.type) else null
                    )
                )
            }
            
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val exportDir = File(basePath, "Quizer/qbooks")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val exportFile = targetFileParam ?: File(exportDir, "${book.name.replace(" ", "_")}.qbook")
            
            val metaJson = gson.toJson(meta)
            val questionsJson = gson.toJson(qBookQuestions)
            
            ZipOutputStream(FileOutputStream(exportFile)).use { zipOut ->
                val metaEntry = ZipEntry("book_meta.json")
                zipOut.putNextEntry(metaEntry)
                zipOut.write(metaJson.toByteArray())
                zipOut.closeEntry()
                
                val qEntry = ZipEntry("questions.json")
                zipOut.putNextEntry(qEntry)
                zipOut.write(questionsJson.toByteArray())
                zipOut.closeEntry()
                
                // Add images
                val quizerDir = File(basePath, "Quizer")
                val bookImagesDir = File(quizerDir, "$bookId/images")
                if (bookImagesDir.exists() && bookImagesDir.isDirectory) {
                    bookImagesDir.listFiles()?.forEach { file ->
                        val entry = ZipEntry("images/${file.name}")
                        zipOut.putNextEntry(entry)
                        file.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }
            
            
            var uriMessage = ""
            if (targetFileParam == null) {
                try {
                    val prefs = context.getSharedPreferences("book_uris", Context.MODE_PRIVATE)
                    val uriStr = prefs.getString(book.id, null)
                    if (uriStr != null) {
                        val uri = android.net.Uri.parse(uriStr)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            exportFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        uriMessage = " and updated original file"
                    }
                } catch(e: Exception) {
                    uriMessage = " (Failed to update original file)"
                }
                
                try {
                    val quizerOriginal = File(basePath, "Quizer/${book.id}.qbook")
                    if (quizerOriginal.exists()) {
                        exportFile.copyTo(quizerOriginal, overwrite = true)
                    } else {
                        val quizerDir = File(basePath, "Quizer")
                        val possibleFiles = quizerDir.listFiles { file -> file.isFile && file.name.endsWith(".qbook") } ?: emptyArray()
                        for (pf in possibleFiles) {
                            try {
                                java.util.zip.ZipFile(pf).use { zip ->
                                    val metaEntry = zip.getEntry("book_meta.json")
                                    if (metaEntry != null) {
                                        val reader = zip.getInputStream(metaEntry).bufferedReader()
                                        val jsonStr = com.example.data.DataParser.autoFixJson(reader.readText())
                                        val m = com.example.data.SharedGson.lenient.fromJson(jsonStr, QBookMeta::class.java)
                                        if (m?.id == book.id) {
                                            exportFile.copyTo(pf, overwrite = true)
                                            break
                                        }
                                    }
                                }
                            } catch(e: Exception){}
                        }
                    }
                } catch(e: Exception){}
            }
            
            "Exported to ${exportFile.absolutePath}$uriMessage"
        } catch (e: Exception) {
            e.printStackTrace()
            "Error exporting: ${e.message}"
        }
    }
}


suspend fun createRestorePoint(context: Context, repository: QuizRepository, bookId: String, changeName: String = "Auto Save") {
    withContext(Dispatchers.IO) {
        try {
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val restoreDir = File(basePath, "Quizer/RestorePoints/$bookId")
            if (!restoreDir.exists()) restoreDir.mkdirs()
            
            val timestamp = System.currentTimeMillis()
            val fileName = "${timestamp}_${changeName.replace(Regex("[^a-zA-Z0-9]"), "_")}.qbook"
            val targetFile = File(restoreDir, fileName)
            
            exportQBook(context, repository, bookId, targetFile)
            
            // Delete old ones
            val files = restoreDir.listFiles()?.filter { it.name.endsWith(".qbook") }?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (files.size > 10) {
                files.drop(10).forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
