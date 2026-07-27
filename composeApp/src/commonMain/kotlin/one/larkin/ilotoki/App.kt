package one.larkin.ilotoki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import one.larkin.ilotoki.model.DownloadProgress
import one.larkin.ilotoki.model.ModelSpec
import one.larkin.ilotoki.model.ModelState
import one.larkin.ilotoki.model.ModelStatus
import one.larkin.ilotoki.resources.Res
import one.larkin.ilotoki.resources.sitelen_pona_pona
import one.larkin.ilotoki.resources.swap_horiz
import one.larkin.ilotoki.ui.theme.IloTokiTheme
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0

@Composable
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    IloTokiTheme {
        val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onStart() }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            when (val status = modelStatus) {
                ModelStatus.Ready -> TranslatorScreen(state, viewModel)
                is ModelStatus.Failed -> ModelErrorScreen(status.message, viewModel)
                else -> ModelLoadingScreen(status, viewModel)
            }
        }
    }
}

@Composable
private fun ModelLoadingScreen(status: ModelStatus, viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (status) {
                is ModelStatus.Downloading -> {
                    Text("Downloading model…")
                    Spacer(Modifier.height(8.dp))
                    DownloadIndicator(status.progress)
                }

                else -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(if (status is ModelStatus.Loading) "Loading model into memory…" else "Preparing…")
                }
            }
            Spacer(Modifier.height(16.dp))
            ModelPickerButton(viewModel)
        }
    }
}

@Composable
private fun DownloadIndicator(progress: DownloadProgress) {
    val hasTotal = progress.total > 0
    if (hasTotal) {
        LinearProgressIndicator(
            progress = { (progress.downloaded.toFloat() / progress.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (hasTotal) {
            "${formatGiB(progress.downloaded)} / ${formatGiB(progress.total)} GiB"
        } else {
            "${formatGiB(progress.downloaded)} GiB"
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Two decimal places without java.util.Locale, which is not available in common code. */
private fun formatGiB(bytes: Long): String {
    val hundredths = ((bytes / BYTES_PER_GIB) * 100).toLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

@Composable
private fun ModelErrorScreen(message: String, viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Could not prepare the model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::retryModel) { Text("Try again") }
            Spacer(Modifier.height(8.dp))
            ModelPickerButton(viewModel)
        }
    }
}

@Composable
private fun TranslatorScreen(state: TranslatorState, viewModel: MainViewModel) {
    val sitelenPona = TextStyle(
        fontFamily = FontFamily(Font(Res.font.sitelen_pona_pona)),
        fontSize = 32.sp,
    )

    Scaffold(
        bottomBar = { LanguageBar(state, viewModel) },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Query") },
                textStyle = if (state.fromTokiPona && state.useSitelenPona) sitelenPona else LocalTextStyle.current,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = false,
                // Toki Pona is written entirely in lower case and none of its words
                // are in the keyboard's dictionary, so capitalization and autocorrect
                // only corrupt the input. Typing the other language keeps both.
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = if (state.fromTokiPona) {
                        KeyboardCapitalization.None
                    } else {
                        KeyboardCapitalization.Sentences
                    },
                    autoCorrectEnabled = !state.fromTokiPona,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.translate() }),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::translate,
                enabled = state.query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isTranslating) "Translating…" else "Translate")
            }

            Spacer(Modifier.height(24.dp))

            if (state.result.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    SelectionContainer {
                        Text(
                            text = state.result,
                            style = if (state.fromTokiPona || !state.useSitelenPona) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                sitelenPona
                            },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (state.isTranslating) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                Switch(
                    checked = state.useSitelenPona,
                    onCheckedChange = viewModel::onSitelenPonaChange,
                )
                Spacer(Modifier.width(8.dp))
                Text("sitelen pona")
            }

            ModelPickerButton(viewModel)
        }
    }
}

/**
 * Reachable from every screen, not just the translator: if the selected model
 * cannot be downloaded, switching to one already on the device is the only way
 * out, and being stuck on a failing download with no way back is worse than
 * a spare button.
 */
@Composable
private fun ModelPickerButton(viewModel: MainViewModel) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }

    TextButton(onClick = { pickerOpen = true }) {
        // Repository names are long; the dialog shows them in full.
        Text(
            text = models.firstOrNull { it.selected }?.spec?.displayName ?: "Model",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (pickerOpen) {
        ModelPicker(
            models = models,
            onSelect = { pickerOpen = false; viewModel.selectModel(it) },
            onDelete = viewModel::deleteModel,
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ModelPicker(
    models: List<ModelState>,
    onSelect: (ModelSpec) -> Unit,
    onDelete: (ModelSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Model") },
        text = {
            Column {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model.spec) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = model.selected,
                            onClick = { onSelect(model.spec) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            // Repository names are long and have no spaces to break
                            // on, so let them wrap on the hyphens instead of mid-word.
                            Text(
                                text = model.spec.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                softWrap = true,
                            )
                            Text(
                                text = buildString {
                                    append(model.spec.quantization)
                                    append(" · ")
                                    append(formatGiB(model.spec.sizeBytes))
                                    append(" GiB")
                                    if (model.downloaded) append(" · downloaded")
                                    if (model.spec.deprecated) append(" · superseded")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Only offer to free space that is actually taken.
                        if (model.downloaded) {
                            TextButton(onClick = { onDelete(model.spec) }) { Text("Delete") }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LanguageBar(state: TranslatorState, viewModel: MainViewModel) {
    Surface(
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
    ) {
        Surface(modifier = Modifier.imePadding().navigationBarsPadding()) {
            AnimatedContent(
                targetState = state.fromTokiPona,
                transitionSpec = {
                    (slideInVertically { it / 8 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 8 } + fadeOut())
                },
                label = "LanguageBarSlide",
            ) { fromTokiPona ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Source on the left, target on the right, swapping sides with the direction.
                    if (fromTokiPona) {
                        TokiPonaLabel(Modifier.weight(1f), Alignment.Center)
                        SwapButton(viewModel::swapDirection)
                        LanguageChips(state.target, viewModel::onTargetChange, Modifier.weight(1f), Alignment.CenterEnd)
                    } else {
                        LanguageChips(
                            state.target,
                            viewModel::onTargetChange,
                            Modifier.weight(1f),
                            Alignment.CenterStart,
                        )
                        SwapButton(viewModel::swapDirection)
                        TokiPonaLabel(Modifier.weight(1f), Alignment.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TokiPonaLabel(modifier: Modifier, alignment: Alignment) {
    Box(modifier = modifier, contentAlignment = alignment) {
        Text(TOKI_PONA, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(Res.drawable.swap_horiz),
            contentDescription = "Swap languages",
        )
    }
}

@Composable
private fun LanguageChips(
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier,
    alignment: Alignment,
) {
    Box(modifier = modifier, contentAlignment = alignment) {
        Row {
            Language.entries.forEach { language ->
                FilterChip(
                    selected = language == selected,
                    onClick = { onSelect(language) },
                    label = { Text(language.flag) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
    }
}
