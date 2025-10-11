package one.larkin.ilotoki
import one.larkin.ilotoki.ui.theme.IloTokiTheme
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.with
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import ilotoki.composeapp.generated.resources.Res
import ilotoki.composeapp.generated.resources.compose_multiplatform

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    IloTokiTheme {
        var fromTokiPona by remember { mutableStateOf(true) }
        var targetLanguage by remember { mutableStateOf("en") }
        var query by remember { mutableStateOf("") }
        var result by remember { mutableStateOf("") }

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
                                // FROM Toki Pona: [Toki Pona] [Swap] [Chips]
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
                                            Pair("🇺🇸", "en"),
                                            Pair("🇷🇺", "ru"),
                                            Pair("🇻🇳", "vi")
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
                                            Pair("🇺🇸", "en"),
                                            Pair("🇷🇺", "ru"),
                                            Pair("🇻🇳", "vi")
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Enter text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            result = translate(query, fromTokiPona, targetLanguage)
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
                }
            }
        }
    }
}

fun translate(query: String, fromTokiPona: Boolean, target: String): String {
    return when {
        query.lowercase().trim() == "toki a!" && target == "Russian" -> "Привет!"
        else -> "(${if (fromTokiPona) "From TP" else "To TP"}) → $target: $query"
    }
}
