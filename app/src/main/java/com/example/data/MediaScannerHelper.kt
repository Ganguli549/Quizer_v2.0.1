package com.example.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object MediaScannerHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Ensures that [dir] exists and contains a `.nomedia` marker file.
     * Also ensures `.nomedia` exists in ancestor directories up to the `Quizer` root folder.
     */
    fun ensureNoMediaInDir(dir: File?): File? {
        if (dir == null) return null
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (!dir.isDirectory) return null

            var current: File? = dir
            var targetNoMedia: File? = null
            while (current != null && current.isDirectory) {
                val nm = File(current, ".nomedia")
                if (!nm.exists()) {
                    try {
                        nm.createNewFile()
                    } catch (_: Exception) {}
                }
                if (targetNoMedia == null && nm.exists()) {
                    targetNoMedia = nm
                }
                if (current.name.equals("Quizer", ignoreCase = true)) {
                    break
                }
                val parent = current.parentFile
                if (parent == null || !parent.absolutePath.contains("Quizer", ignoreCase = true)) {
                    break
                }
                current = parent
            }
            targetNoMedia
        } catch (e: Exception) {
            Log.w("MediaScannerHelper", "Failed to create .nomedia in ${dir.absolutePath}", e)
            null
        }
    }

    /**
     * Ensures `.nomedia` exists in [file]'s parent directory and notifies Android's MediaScanner
     * of the `.nomedia` marker so the image is hidden from Gallery apps.
     */
    fun scanFile(context: Context?, file: File) {
        if (context == null) return
        val parentDir = if (file.isDirectory) file else file.parentFile
        if (parentDir != null) {
            hideDirectoryFromGallery(context, parentDir)
        }
    }

    /**
     * Ensures `.nomedia` exists in all parent directories of [files] and notifies MediaScanner
     * so extracted/downloaded images are hidden from Gallery apps.
     */
    fun scanFiles(context: Context?, files: List<File>) {
        if (context == null || files.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val noMediaPaths = LinkedHashSet<String>()
                files.mapNotNull { if (it.isDirectory) it else it.parentFile }
                    .distinctBy { it.absolutePath }
                    .forEach { dir ->
                        ensureNoMediaInDir(dir)?.let { nm ->
                            noMediaPaths.add(nm.absolutePath)
                            noMediaPaths.add(dir.absolutePath)
                        }
                    }

                val rootQuizer = File(Environment.getExternalStorageDirectory(), "Quizer")
                ensureNoMediaInDir(rootQuizer)?.let { nm ->
                    noMediaPaths.add(nm.absolutePath)
                    noMediaPaths.add(rootQuizer.absolutePath)
                }

                purgeIndexedQuizerImagesFromMediaStore(appContext)

                if (noMediaPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        appContext,
                        noMediaPaths.toTypedArray(),
                        null
                    ) { path, _ ->
                        Log.d("MediaScannerHelper", "Enforced .nomedia for $path")
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaScannerHelper", "Error hiding files from gallery", e)
            }
        }
    }

    fun hideDirectoryFromGallery(context: Context?, dir: File) {
        if (context == null) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val noMediaPaths = LinkedHashSet<String>()
                ensureNoMediaInDir(dir)?.let { nm ->
                    noMediaPaths.add(nm.absolutePath)
                    noMediaPaths.add(dir.absolutePath)
                }
                val rootQuizer = File(Environment.getExternalStorageDirectory(), "Quizer")
                ensureNoMediaInDir(rootQuizer)?.let { nm ->
                    noMediaPaths.add(nm.absolutePath)
                    noMediaPaths.add(rootQuizer.absolutePath)
                }
                purgeIndexedQuizerImagesFromMediaStore(appContext)
                if (noMediaPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        appContext,
                        noMediaPaths.toTypedArray(),
                        null,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.w("MediaScannerHelper", "Error hiding directory from gallery", e)
            }
        }
    }

    /**
     * Recursively places `.nomedia` across all Quizer folders, demotes any already-indexed
     * Quizer images in Android's MediaStore to `MEDIA_TYPE_NONE` (without deleting physical files),
     * and triggers MediaScanner on the `.nomedia` markers so Gallery apps hide all Quizer images.
     */
    fun ensureNoMediaAndHideFromGallery(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val rootDirs = listOf(
                    File(Environment.getExternalStorageDirectory(), "Quizer"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Quizer"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Quizer")
                )

                val pathsToScan = LinkedHashSet<String>()
                val now = System.currentTimeMillis()

                for (rootDir in rootDirs) {
                    if (!rootDir.exists()) {
                        if (rootDir.parentFile == Environment.getExternalStorageDirectory()) {
                            rootDir.mkdirs()
                        } else {
                            continue
                        }
                    }
                    if (rootDir.exists() && rootDir.isDirectory) {
                        rootDir.walkTopDown().forEach { dir ->
                            if (dir.isDirectory) {
                                val nm = ensureNoMediaInDir(dir)
                                if (nm != null) {
                                    try {
                                        nm.setLastModified(now)
                                        dir.setLastModified(now)
                                    } catch (_: Exception) {}
                                    pathsToScan.add(nm.absolutePath)
                                    pathsToScan.add(dir.absolutePath)
                                }
                            }
                        }
                    }
                }

                // Demote any existing MediaStore image records under /Quizer/ to MEDIA_TYPE_NONE
                // so Gallery apps immediately stop showing them without deleting physical files on disk.
                purgeIndexedQuizerImagesFromMediaStore(appContext)

                if (pathsToScan.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        appContext,
                        pathsToScan.toTypedArray(),
                        null
                    ) { path, _ ->
                        Log.d("MediaScannerHelper", "Verified .nomedia hidden state for $path")
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaScannerHelper", "Error enforcing .nomedia across Quizer folders", e)
            }
        }
    }

    /**
     * Kept as an alias so existing callers automatically enforce `.nomedia` hiding.
     */
    fun scanAllQuizerFolderImages(context: Context?) {
        ensureNoMediaAndHideFromGallery(context)
    }

    fun hideAllQuizerImagesFromGallery(context: Context?) {
        ensureNoMediaAndHideFromGallery(context)
    }

    /**
     * Demotes any already-indexed `/Quizer/` image entries in Android's `MediaStore.Files` table
     * from `MEDIA_TYPE_IMAGE` (1) to `MEDIA_TYPE_NONE` (0) so they disappear from Gallery apps
     * while leaving all physical image files on disk untouched.
     * Also deletes any 0-byte orphan rows previously inserted into `Pictures/Quizer`.
     */
    @Suppress("DEPRECATION")
    fun purgeIndexedQuizerImagesFromMediaStore(context: Context) {
        val resolver = context.contentResolver
        val filesUri = MediaStore.Files.getContentUri("external")

        // 1. Delete 0-byte orphan rows that previous code inserted into Pictures/Quizer
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.SIZE} = 0",
                    arrayOf("%Pictures/Quizer%")
                )
            }
        } catch (_: Exception) {}

        // 2. Demote indexed /Quizer/ images in MediaStore.Files from MEDIA_TYPE_IMAGE to MEDIA_TYPE_NONE (0)
        // Updating MEDIA_TYPE to MEDIA_TYPE_NONE removes them from MediaStore.Images (Gallery)
        // without deleting the underlying files on disk!
        try {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    filesUri,
                    values,
                    "(${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?) AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                    arrayOf(
                        "%/Quizer/%",
                        "Quizer/%",
                        "%Pictures/Quizer%",
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
                    )
                )
            } else {
                resolver.update(
                    filesUri,
                    values,
                    "${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                    arrayOf(
                        "%/Quizer/%",
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
                    )
                )
            }
        } catch (e: Exception) {
            Log.d("MediaScannerHelper", "MediaStore MEDIA_TYPE_NONE update skipped: ${e.message}")
        }
    }
}
