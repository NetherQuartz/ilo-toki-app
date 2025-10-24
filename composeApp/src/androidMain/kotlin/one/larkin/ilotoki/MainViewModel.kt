package one.larkin.ilotoki

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class ModelState(
    val isLoading: Boolean = false,
    val isModelLoaded: Boolean = false,
    val progress: String = "",
    val downloadProgress: Float = 0f,
    val sizeText: String = "",
    val error: String? = null
)

class MainViewModel : ViewModel() {
    private var _modelState = MutableStateFlow(ModelState())
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    
    private var smolLM: SmolLM? = null
    
    fun initializeModel(context: Context) {
        viewModelScope.launch {
            try {
                _modelState.value = _modelState.value.copy(
                    isLoading = true,
                    progress = "Preparing...",
                    error = null
                )
                
                val modelDir = File(context.filesDir, "models").apply { mkdirs() }
                val modelFile = File(modelDir, "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf")
                val hfUrl = "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf"

                if (!modelFile.exists()) {
                    _modelState.value = _modelState.value.copy(
                        progress = "Downloading model…"
                    )
                    
                    withContext(Dispatchers.IO) {
                        val connection = URL(hfUrl).openConnection()
                        val totalSize = connection.contentLengthLong
                        connection.getInputStream().use { input ->
                            modelFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var downloaded: Long = 0
                                var lastPercent = 0
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloaded += bytesRead
                                    if (totalSize > 0) {
                                        val percent = ((downloaded * 100) / totalSize).toInt()
                                        if (percent >= lastPercent + 1) {
                                            lastPercent = percent
                                            val downloadedGB = downloaded / (1024.0 * 1024.0 * 1024.0)
                                            val totalGB = totalSize / (1024.0 * 1024.0 * 1024.0)
                                            val sizeTextValue = String.format(java.util.Locale.getDefault(), "%.2f / %.2f GiB", downloadedGB, totalGB)
                                            withContext(Dispatchers.Main) {
                                                _modelState.value = _modelState.value.copy(
                                                    progress = "Downloading model…",
                                                    downloadProgress = percent / 100f,
                                                    sizeText = sizeTextValue
                                                )
                                            }
                                        }
                                    }
                                }
                                // Обновляем прогресс до 100% после завершения скачивания
                                withContext(Dispatchers.Main) {
                                    _modelState.value = _modelState.value.copy(
                                        downloadProgress = 1f
                                    )
                                }
                            }
                        }
                    }
                } else {
                    _modelState.value = _modelState.value.copy(
                        progress = "Model already on disk",
                        downloadProgress = 1f
                    )
                }

                _modelState.value = _modelState.value.copy(
                    progress = "Loading model into memory…"
                )
                
                withContext(Dispatchers.IO) {
                    smolLM = SmolLM()
                    smolLM?.load(
                        modelFile.absolutePath,
                        params = SmolLM.InferenceParams(
                            temperature = 0.5f,
                            storeChats = false
                        )
                    )
                }

                _modelState.value = _modelState.value.copy(
                    isLoading = false,
                    isModelLoaded = true,
                    progress = "Model ready"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _modelState.value = _modelState.value.copy(
                    progress = "Error: ${e.localizedMessage}. Retrying...",
                    error = e.localizedMessage
                )
                
                // Удаляем папку models полностью
                val modelDir = File(context.filesDir, "models")
                modelDir.deleteRecursively()
                
                // Повторная попытка загрузки
                retryDownload(context)
            }
        }
    }
    
    private suspend fun retryDownload(context: Context) {
        try {
            _modelState.value = _modelState.value.copy(
                isLoading = true,
                progress = "Retrying download..."
            )
            
            val modelDirRetry = File(context.filesDir, "models").apply { mkdirs() }
            val modelFileRetry = File(modelDirRetry, "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf")
            val hfUrl = "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf"
            
            _modelState.value = _modelState.value.copy(
                progress = "Downloading model…"
            )
            
            withContext(Dispatchers.IO) {
                val connection = URL(hfUrl).openConnection()
                val totalSize = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    modelFileRetry.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded: Long = 0
                        var lastPercent = 0
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                val percent = ((downloaded * 100) / totalSize).toInt()
                                if (percent >= lastPercent + 1) {
                                    lastPercent = percent
                                    val downloadedGB = downloaded / (1024.0 * 1024.0 * 1024.0)
                                    val totalGB = totalSize / (1024.0 * 1024.0 * 1024.0)
                                    val sizeTextValue = String.format(java.util.Locale.getDefault(), "%.2f / %.2f GiB", downloadedGB, totalGB)
                                    withContext(Dispatchers.Main) {
                                        _modelState.value = _modelState.value.copy(
                                            progress = "Downloading model…",
                                            downloadProgress = percent / 100f,
                                            sizeText = sizeTextValue
                                        )
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            _modelState.value = _modelState.value.copy(
                                downloadProgress = 1f
                            )
                        }
                    }
                }
            }
            
            _modelState.value = _modelState.value.copy(
                progress = "Loading model into memory…"
            )
            
            withContext(Dispatchers.IO) {
                smolLM = SmolLM()
                smolLM?.load(
                    modelFileRetry.absolutePath,
                    params = SmolLM.InferenceParams(
                        temperature = 0.5f,
                        storeChats = false
                    )
                )
            }
            
            _modelState.value = _modelState.value.copy(
                isLoading = false,
                isModelLoaded = true,
                progress = "Model ready"
            )
        } catch (inner: Exception) {
            _modelState.value = _modelState.value.copy(
                isLoading = false,
                progress = "Retry download error: ${inner.message}",
                error = inner.message
            )
        }
    }
    
    fun getModel(): SmolLM? {
        return smolLM
    }
    
    override fun onCleared() {
        super.onCleared()
        smolLM?.close()
        smolLM = null
    }
}
