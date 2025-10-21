package one.larkin.ilotoki
import one.larkin.ilotoki.ui.theme.IloTokiTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    IloTokiTheme {
        var fromTokiPona by remember { mutableStateOf(true) }
        var targetLanguage by remember { mutableStateOf("English") }
        var query by remember { mutableStateOf("") }
        var result by remember { mutableStateOf("") }
        var isTranslating by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val model = remember { SmolLM() }
        var isLoading by remember { mutableStateOf(true) }
        var progress by remember { mutableStateOf("Preparing...") }
        var downloadProgress by remember { mutableStateOf(0f) }
        var sizeText by remember { mutableStateOf("") }

        // --- Загрузка модели ---
        LaunchedEffect(Unit) {
            val modelDir = File(context.filesDir, "models").apply { mkdirs() }
            val modelFile = File(modelDir, "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf")
            try {
                val hfUrl = "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf"

                if (!modelFile.exists()) {
                    progress = "Downloading model…"
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
                                                progress = "Downloading model…"
                                                downloadProgress = percent / 100f
                                                sizeText = sizeTextValue
                                            }
                                        }
                                    }
                                }
                                // Обновляем прогресс до 100% после завершения скачивания
                                withContext(Dispatchers.Main) {
                                    downloadProgress = 1f
                                }
                            }
                        }
                    }
                } else {
                    progress = "Model already on disk"
                    downloadProgress = 1f
                }

                progress = "Loading model into memory…"
                withContext(Dispatchers.IO) {
                    model.load(
                        modelFile.absolutePath,
                        params = SmolLM.InferenceParams(
                            temperature = 0.5f,
                            storeChats = false
                        )
                    )
                }

                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                progress = "Error: ${e.localizedMessage}. Retrying..."
                // Удаляем папку models полностью
                modelDir.deleteRecursively()
                // Повторная попытка загрузки
                withContext(Dispatchers.Main) {
                    isLoading = true
                    progress = "Retrying download..."
                }
                // Запускаем повторно загрузку модели
                this.launch {
                    try {
                        val modelDirRetry = File(context.filesDir, "models").apply { mkdirs() }
                        val modelFileRetry = File(modelDirRetry, "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf")
                        val hfUrl = "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf"
                        progress = "Downloading model…"
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
                                                    progress = "Downloading model…"
                                                    downloadProgress = percent / 100f
                                                    sizeText = sizeTextValue
                                                }
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = 1f
                                    }
                                }
                            }
                        }
                        progress = "Loading model into memory…"
                        withContext(Dispatchers.IO) {
                            model.load(
                                modelFileRetry.absolutePath,
                                params = SmolLM.InferenceParams(
                                    temperature = 0.5f,
                                    storeChats = false
                                )
                            )
                        }
                        isLoading = false
                    } catch (inner: Exception) {
                        progress = "Retry download error: ${inner.message}"
                    }
                }
            }
        }

        if (isLoading) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (progress.startsWith("Downloading")) {
                            Text(progress)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(sizeText, style = MaterialTheme.typography.bodySmall)
                        } else {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(progress)
                        }
                    }
                }
            }
        } else

        Scaffold(
            bottomBar = {
                @OptIn(ExperimentalAnimationApi::class)
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.imePadding()
                ) {
                    AnimatedContent(
                        targetState = fromTokiPona,
                        transitionSpec = {
                            (slideInVertically { it / 8 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 8 } + fadeOut())
                        },
                        label = "LanguageBarSlide"
                    ) { state ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Toki Pona", style = MaterialTheme.typography.titleMedium)
                                }

                                IconButton(onClick = {
                                    fromTokiPona = !fromTokiPona
                                    if (result.isNotEmpty()) {
                                        query = result
                                        result = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Swap languages")
                                }

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row {
                                        listOf(
                                            Pair("🇺🇸", "English"),
                                            Pair("🇷🇺", "Russian"),
                                            Pair("🇻🇳", "Vietnamese")
                                        ).forEach { lang ->
                                            val selected = targetLanguage == lang.second
                                            FilterChip(
                                                selected = selected,
                                                onClick = { targetLanguage = lang.second },
                                                label = { Text(lang.first) },
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // TO Toki Pona: [Chips] [Swap] [Toki Pona]
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row {
                                        listOf(
                                            Pair("🇺🇸", "English"),
                                            Pair("🇷🇺", "Russian"),
                                            Pair("🇻🇳", "Vietnamese")
                                        ).forEach { lang ->
                                            val selected = targetLanguage == lang.second
                                            FilterChip(
                                                selected = selected,
                                                onClick = { targetLanguage = lang.second },
                                                label = { Text(lang.first) },
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(onClick = {
                                    fromTokiPona = !fromTokiPona
                                    if (result.isNotEmpty()) {
                                        query = result
                                        result = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Swap languages")
                                }

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Toki Pona", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val coroutineScope = rememberCoroutineScope()
                OutlinedTextField(
                    placeholder = { Text("Query") },
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            coroutineScope.launch {
                                result = ""
                                isTranslating = true
                                withContext(Dispatchers.IO) {
                                    translate(model, query, fromTokiPona, targetLanguage)
                                        .collect { token ->
                                            withContext(Dispatchers.Main) {
                                                result += token
                                            }
                                        }
                                }
                                isTranslating = false
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (result.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        SelectionContainer {
                            Text(
                                result,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else if (isTranslating) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

fun translate(model: SmolLM, query: String, fromToki: Boolean, other: String): Flow<String> {
    val source = if (fromToki) "Toki Pona" else other
    val target = if (fromToki) other else "Toki Pona"
    return model.getResponseAsFlow("Translate $source to $target.\nQuery: ${query.removeSuffix(" ")}\nAnswer:")
}
