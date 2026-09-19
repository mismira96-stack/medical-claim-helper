package com.example.meritzshortcut.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritzshortcut.data.Receipt
import com.example.meritzshortcut.data.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReceiptRepository(application.applicationContext)

    val pendingReceipts: StateFlow<List<Receipt>> = repository.receipts
        .map { list -> list.filter { !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedReceipts: StateFlow<List<Receipt>> = repository.receipts
        .map { list -> list.filter { it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _previewReceipt = MutableStateFlow<Receipt?>(null)
    val previewReceipt: StateFlow<Receipt?> = _previewReceipt.asStateFlow()

    private var currentCameraTempFile: File? = null

    fun prepareCameraCapture(): Uri {
        val (tempFile, uri) = repository.createCameraTempUri()
        currentCameraTempFile = tempFile
        return uri
    }

    fun onCameraCaptured(success: Boolean) {
        val pendingFile = repository.getPendingCameraFile() ?: currentCameraTempFile
        if (pendingFile != null && pendingFile.exists() && pendingFile.length() > 0) {
            viewModelScope.launch {
                repository.saveCameraReceipt(pendingFile)
                repository.clearPendingCameraFile()
                currentCameraTempFile = null
            }
        } else {
            pendingFile?.delete()
            repository.clearPendingCameraFile()
            currentCameraTempFile = null
        }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                repository.addReceiptFromUri(uri)
            }
        }
    }

    fun markAllPendingCompleted() {
        viewModelScope.launch {
            repository.markAllPendingCompleted()
        }
    }

    fun markReceiptCompleted(id: String) {
        viewModelScope.launch {
            repository.markCompleted(id)
        }
    }

    fun revertToPending(id: String) {
        viewModelScope.launch {
            repository.revertToPending(id)
        }
    }

    fun deleteReceipt(id: String) {
        viewModelScope.launch {
            repository.deleteReceipt(id)
            if (_previewReceipt.value?.id == id) {
                _previewReceipt.value = null
            }
        }
    }

    fun setPreviewReceipt(receipt: Receipt?) {
        _previewReceipt.value = receipt
    }

    fun launchMeritzApp(context: Context) {
        val packageName = "com.kr.meritzfire"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "메리츠화재 앱이 설치되어 있지 않습니다. 플레이스토어로 이동합니다.", Toast.LENGTH_LONG).show()
            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(playStoreIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "플레이스토어를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
