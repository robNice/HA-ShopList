package de.robnice.homeasssistant_shoppinglist.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import de.robnice.homeasssistant_shoppinglist.BuildConfig
import de.robnice.homeasssistant_shoppinglist.R
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import de.robnice.homeasssistant_shoppinglist.data.history.ProductHistoryRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingList
import de.robnice.homeasssistant_shoppinglist.ui.util.t
import de.robnice.homeasssistant_shoppinglist.util.normalizeHaUrl
import kotlinx.coroutines.launch

private sealed interface MarkdownBlock {
    data class H1(val text: String) : MarkdownBlock
    data class H2(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data object Spacer : MarkdownBlock
}

/**
 * @todo: prevent copying token
 * @todo: prevent screenshots
 * @todo: prevent autofill for token field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    context: Context = LocalContext.current
) {
    val dataStore = remember { SettingsDataStore(context) }
    val productHistoryRepository = remember(context) { ProductHistoryRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val storedUrl by dataStore.haUrl.collectAsState(initial = "")
    val storedToken by dataStore.haToken.collectAsState(initial = "")
    val storedTodoEntity by dataStore.todoEntity.collectAsState(initial = "todo.einkaufsliste")
    val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)

    var tokenVisible by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var todoEntity by remember { mutableStateOf("") }
    var todoOptions by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var todoExpanded by remember { mutableStateOf(false) }
    var todoLoading by remember { mutableStateOf(false) }
    var todoLoadError by remember { mutableStateOf<String?>(null) }
    var todoReloadKey by remember { mutableStateOf(0) }
    var showHistoryClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var privacyText by remember { mutableStateOf("") }

    LaunchedEffect(storedUrl, storedToken, storedTodoEntity) {
        url = storedUrl
        token = storedToken
        todoEntity = storedTodoEntity
    }

    LaunchedEffect(Unit) {
        val assetName = if (java.util.Locale.getDefault().language == "de") {
            "privacy_policy_de.md"
        } else {
            "privacy_policy_en.md"
        }
        privacyText = context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    LaunchedEffect(url, token, todoReloadKey) {
        todoLoadError = null

        val cleanedUrl = normalizeHaUrl(url)
        val cleanedToken = token.trim()

        if (cleanedUrl.isBlank() || cleanedToken.isBlank()) {
            return@LaunchedEffect
        }

        todoLoading = true

        try {
            val api = de.robnice.homeasssistant_shoppinglist.data.HaServiceFactory.create(cleanedUrl)
            val repo = de.robnice.homeasssistant_shoppinglist.data.HaTodoListRepository(api)
            val loaded = repo.loadTodoLists(cleanedToken)

            todoOptions = loaded

            if (loaded.isEmpty()) {
                todoLoadError = context.getString(R.string.error_no_lists_found)
            } else if (todoEntity.isBlank()) {
                todoEntity = loaded.firstOrNull()?.id.orEmpty()
            } else if (loaded.none { it.id == todoEntity }) {
                todoEntity = loaded.firstOrNull()?.id.orEmpty()
            }
        } catch (e: Exception) {
            todoLoadError = e.message ?: context.getString(R.string.loading_failed)
        } finally {
            todoLoading = false
        }
    }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            color = Color.Black
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = t(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t(R.string.notifications_enabled),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            coroutineScope.launch {
                                dataStore.setNotificationsEnabled(it)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(t(R.string.ha_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(t(R.string.ha_token)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        val image = if (tokenVisible) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        }

                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = image,
                                contentDescription = null
                            )
                        }
                    },
                    colors = settingsTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    todoLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t(R.string.loading_lists))
                        }
                    }

                    todoOptions.isNotEmpty() -> {
                        ExposedDropdownMenuBox(
                            expanded = todoExpanded,
                            onExpandedChange = { todoExpanded = !todoExpanded }
                        ) {
                            val selectedName = todoOptions
                                .firstOrNull { it.id == todoEntity }
                                ?.name
                                ?: todoEntity

                            OutlinedTextField(
                                value = selectedName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(t(R.string.settings_list)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = todoExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = settingsTextFieldColors()
                            )

                            ExposedDropdownMenu(
                                expanded = todoExpanded,
                                onDismissRequest = { todoExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                todoOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.name,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            todoEntity = option.id
                                            todoExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val cleanedUrl = normalizeHaUrl(url)
                            val cleanedToken = token.trim()

                            coroutineScope.launch {
                                dataStore.saveHaUrl(cleanedUrl)
                                dataStore.saveHaToken(cleanedToken)
                                dataStore.saveTodoEntity(todoEntity)
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Text(t(R.string.save))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        Text(t(R.string.clear_completed_confirm_btn_cancel))
                    }
                }

                if (todoLoadError != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = t(R.string.error_list_not_loaded),
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = todoLoadError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(onClick = { todoReloadKey++ }) {
                        Text(t(R.string.retry))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = t(R.string.product_history_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = t(R.string.product_history_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(onClick = { showHistoryClearDialog = true }) {
                    Text(t(R.string.product_history_clear))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showPrivacyDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(t(R.string.privacy_policy))
                }

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }

    if (showHistoryClearDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryClearDialog = false },
            title = { Text(t(R.string.product_history_clear_title)) },
            text = { Text(t(R.string.product_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHistoryClearDialog = false
                        coroutineScope.launch {
                            productHistoryRepository.clearAll()
                        }
                    }
                ) {
                    Text(t(R.string.product_history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryClearDialog = false }) {
                    Text(t(R.string.clear_completed_confirm_btn_cancel))
                }
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            markdown = privacyText,
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun PrivacyPolicyDialog(
    markdown: String,
    onDismiss: () -> Unit
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = t(R.string.privacy_policy),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blocks.size) { index ->
                            MarkdownBlockView(block = blocks[index])
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(t(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.H1 -> Text(
            text = block.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        is MarkdownBlock.H2 -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        is MarkdownBlock.Bullet -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "\u2022",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is MarkdownBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        MarkdownBlock.Spacer -> Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    return markdown.lines().map { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> MarkdownBlock.Spacer
            line.startsWith("# ") -> MarkdownBlock.H1(line.removePrefix("# "))
            line.startsWith("## ") -> MarkdownBlock.H2(line.removePrefix("## "))
            line.startsWith("- ") -> MarkdownBlock.Bullet(line.removePrefix("- "))
            else -> MarkdownBlock.Paragraph(line)
        }
    }
}
