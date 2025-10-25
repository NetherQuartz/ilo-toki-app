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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun translate(model: SmolLM, query: String, fromToki: Boolean, other: String): Flow<String> {
    return try {
        val source = if (fromToki) "Toki Pona" else other
        val target = if (fromToki) other else "Toki Pona"
        val prompt = "Translate $source to $target.\nQuery: ${query.trim()}\nAnswer:"
        model.getResponseAsFlow(prompt)
    } catch (e: Exception) {
        // Возвращаем Flow с ошибкой
        kotlinx.coroutines.flow.flow {
            emit("Error: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    IloTokiTheme {
        var fromTokiPona by remember { mutableStateOf(true) }
        var targetLanguage by remember { mutableStateOf("English") }
        var query by remember { mutableStateOf("") }
        var result by remember { mutableStateOf("") }
        var isTranslating by remember { mutableStateOf(false) }
        var useSitelenPona by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val modelState by viewModel.modelState.collectAsState()

        val sitelenPonaFontFamily = FontFamily(
            Font(
                R.font.sitelen_pona_pona,
                FontWeight.Normal
            )
        )

        val sitelenPona = TextStyle(
            fontFamily = sitelenPonaFontFamily,
            fontSize = 32.sp
        )

        // Инициализация модели при первом запуске
        LaunchedEffect(Unit) {
            viewModel.initializeModel(context)
        }

        if (modelState.isLoading) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (modelState.progress.startsWith("Downloading")) {
                            Text(modelState.progress)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { modelState.downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(modelState.sizeText, style = MaterialTheme.typography.bodySmall)
                        } else {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(modelState.progress)
                        }
                        
                        // Показываем ошибку если есть
                        modelState.error?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else if (modelState.isModelLoaded) {
            Scaffold(
            bottomBar = {
                @OptIn(ExperimentalAnimationApi::class)
                Surface(
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
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
                            )
                            {
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
                                        Icon(
                                            painter = painterResource(R.drawable.swap_horiz),
                                            contentDescription = "Swap languages"
                                        )
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
                                                    modifier = Modifier.padding(horizontal = 2.dp),
                                                    shape = RoundedCornerShape(8.dp)
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
                                                    modifier = Modifier.padding(horizontal = 2.dp),
                                                    shape = RoundedCornerShape(8.dp)
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
                                        Icon(
                                            painter = painterResource(R.drawable.swap_horiz),
                                            contentDescription = "Swap languages"
                                        )
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
                    textStyle =
                        if (!fromTokiPona || !useSitelenPona) LocalTextStyle.current else sitelenPona,
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
                                try {
                                    val model = viewModel.getModel()
                                    if (model != null && query.isNotBlank()) {
                                        withContext(Dispatchers.IO) {
                                            translate(model, query.trim(), fromTokiPona, targetLanguage)
                                                .collect { token: String ->
                                                    withContext(Dispatchers.Main) {
                                                        result += token
                                                    }
                                                }
                                        }
                                    } else if (model == null) {
                                        result = "Model not loaded"
                                    } else {
                                        result = "Please enter a query"
                                    }
                                } catch (e: Exception) {
                                    result = "Translation error: ${e.message}"
                                } finally {
                                    isTranslating = false
                                }
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
                                style = if (fromTokiPona || !useSitelenPona) MaterialTheme.typography.bodyLarge else sitelenPona,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else if (isTranslating) {
                    CircularProgressIndicator()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Switch(
                        checked = useSitelenPona,
                        onCheckedChange = { useSitelenPona = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "sitelen pona")
                }
            }
        }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    App()
}
