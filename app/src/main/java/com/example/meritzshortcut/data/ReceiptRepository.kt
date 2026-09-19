package com.example.meritzshortcut.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ReceiptRepository(private val context: Context) {

    private val storageDir = File(context.filesDir, "receipts").apply {
        if (!exists()) mkdirs()
    }
    private val dataFile = File(context.filesDir, "receipts_data.json")

    private val _receipts = MutableStateFlow<List<Receipt>>(emptyList())
    val receipts: StateFlow<List<Receipt>> = _receipts.asStateFlow()

    init {
        loadReceipts()
    }

    private fun loadReceipts() {
        if (!dataFile.exists()) {
            _receipts.value = emptyList()
            return
        }
        try {
            val content = dataFile.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<Receipt>()
            for (i in 0 until jsonArray.length()) {
                list.add(Receipt.fromJson(jsonArray.getJSONObject(i)))
            }

            var hasChanges = false

            // Auto-recover any orphaned camera receipts from cacheDir
            val orphanedPhotos = context.cacheDir.listFiles { f ->
                f.name.startsWith("camera_receipt_") && f.length() > 0
            } ?: emptyArray()

            for (orphan in orphanedPhotos) {
                try {
                    val id = UUID.randomUUID().toString()
                    val targetFile = File(storageDir, "$id.jpg")
                    orphan.copyTo(targetFile, overwrite = true)
                    orphan.delete()
                    val mediaUri = publishToMediaStore(targetFile)
                    list.add(
                        Receipt(
                            id = id,
                            filePath = targetFile.absolutePath,
                            mediaUri = mediaUri?.toString(),
                            createdAt = System.currentTimeMillis(),
                            isCompleted = false
                        )
                    )
                    hasChanges = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Sync any existing pending receipts that lack a MediaStore URI into DCIM/Meritz
            val syncedList = list.map { receipt ->
                if (!receipt.isCompleted && receipt.mediaUri == null) {
                    val f = File(receipt.filePath)
                    if (f.exists()) {
                        val newUri = publishToMediaStore(f)
                        if (newUri != null) {
                            hasChanges = true
                            receipt.copy(mediaUri = newUri.toString())
                        } else receipt
                    } else receipt
                } else receipt
            }

            if (hasChanges) {
                persistReceipts(syncedList)
            } else {
                _receipts.value = syncedList.sortedByDescending { it.createdAt }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _receipts.value = emptyList()
        }
    }

    private fun persistReceipts(list: List<Receipt>) {
        _receipts.value = list.sortedByDescending { it.createdAt }
        try {
            val jsonArray = JSONArray()
            for (r in list) {
                jsonArray.put(r.toJson())
            }
            dataFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts the file into MediaStore (DCIM/Meritz) so Samsung Gallery creates a dedicated 'Meritz' album
     * and external apps like Meritz Fire & Marine see it at the very top of recent photos.
     */
    private fun publishToMediaStore(file: File): Uri? {
        return try {
            val now = System.currentTimeMillis()
            val filename = "meritz_receipt_${now}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, now)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Meritz")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val mediaUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(mediaUri, values, null, null)
            }

            // Notify MediaScanner for instant Gallery indexing in DCIM/Meritz album
            try {
                val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf("$dcimPath/Meritz/$filename"),
                    arrayOf("image/jpeg"),
                    null
                )
            } catch (ignored: Exception) {}

            mediaUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun removeFromMediaStore(mediaUriString: String?) {
        if (mediaUriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(mediaUriString)
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addReceiptFromUri(uri: Uri): Receipt? = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val targetFile = File(storageDir, "$id.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Publish copy to DCIM/Meritz album
            val mediaUri = publishToMediaStore(targetFile)

            val newReceipt = Receipt(
                id = id,
                filePath = targetFile.absolutePath,
                mediaUri = mediaUri?.toString(),
                createdAt = System.currentTimeMillis(),
                isCompleted = false
            )
            persistReceipts(_receipts.value + newReceipt)
            newReceipt
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val prefs = context.getSharedPreferences("meritz_prefs", Context.MODE_PRIVATE)

    fun createCameraTempUri(): Pair<File, Uri> {
        val tempFile = File.createTempFile("camera_receipt_", ".jpg", context.cacheDir)
        prefs.edit().putString("pending_camera_path", tempFile.absolutePath).apply()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(tempFile, uri)
    }

    fun getPendingCameraFile(): File? {
        val path = prefs.getString("pending_camera_path", null)
        if (path != null) {
            val f = File(path)
            if (f.exists() && f.length() > 0) return f
        }
        // Fallback: check cacheDir for latest camera_receipt_*.jpg
        val cachedFiles = context.cacheDir.listFiles { f ->
            f.name.startsWith("camera_receipt_") && f.length() > 0
        }
        return cachedFiles?.maxByOrNull { it.lastModified() }
    }

    fun clearPendingCameraFile() {
        prefs.edit().remove("pending_camera_path").apply()
    }

    suspend fun saveCameraReceipt(tempFile: File): Receipt? = withContext(Dispatchers.IO) {
        if (!tempFile.exists() || tempFile.length() == 0L) return@withContext null
        try {
            val id = UUID.randomUUID().toString()
            val targetFile = File(storageDir, "$id.jpg")
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()

            // Register into MediaStore (DCIM/Meritz)
            val mediaUri = publishToMediaStore(targetFile)

            val newReceipt = Receipt(
                id = id,
                filePath = targetFile.absolutePath,
                mediaUri = mediaUri?.toString(),
                createdAt = System.currentTimeMillis(),
                isCompleted = false
            )
            persistReceipts(_receipts.value + newReceipt)
            newReceipt
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun markAllPendingCompleted() = withContext(Dispatchers.IO) {
        // Remove from public DCIM/Meritz album so it disappears from gallery
        for (r in _receipts.value) {
            if (!r.isCompleted) {
                removeFromMediaStore(r.mediaUri)
            }
        }

        val updated = _receipts.value.map {
            if (!it.isCompleted) it.copy(isCompleted = true, mediaUri = null) else it
        }
        persistReceipts(updated)
    }

    suspend fun markCompleted(id: String) = withContext(Dispatchers.IO) {
        val target = _receipts.value.find { it.id == id }
        target?.let {
            removeFromMediaStore(it.mediaUri)
        }
        val updated = _receipts.value.map {
            if (it.id == id) it.copy(isCompleted = true, mediaUri = null) else it
        }
        persistReceipts(updated)
    }

    suspend fun revertToPending(id: String) = withContext(Dispatchers.IO) {
        val target = _receipts.value.find { it.id == id } ?: return@withContext
        val f = File(target.filePath)
        val newMediaUri = if (f.exists()) publishToMediaStore(f) else null

        val updated = _receipts.value.map {
            if (it.id == id) it.copy(isCompleted = false, mediaUri = newMediaUri?.toString()) else it
        }
        persistReceipts(updated)
    }

    suspend fun deleteReceipt(id: String) = withContext(Dispatchers.IO) {
        val target = _receipts.value.find { it.id == id }
        target?.let {
            removeFromMediaStore(it.mediaUri)
            val f = File(it.filePath)
            if (f.exists()) f.delete()
        }
        val updated = _receipts.value.filterNot { it.id == id }
        persistReceipts(updated)
    }
}
