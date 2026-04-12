package de.robnice.homeasssistant_shoppinglist.ui.screens

import de.robnice.homeasssistant_shoppinglist.R
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import de.robnice.homeasssistant_shoppinglist.ui.util.t
import de.robnice.homeasssistant_shoppinglist.util.normalizeHaUrl

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
    val coroutineScope = rememberCoroutineScope()

    val storedUrl by dataStore.haUrl.collectAsState(initial = "")
    val storedToken by dataStore.haToken.collectAsState(initial = "")
    var tokenVisible by remember { mutableStateOf(false) }
    val storedTodoEntity by dataStore.todoEntity.collectAsState(initial = "todo.einkaufsliste")

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)
    var todoEntity by remember { mutableStateOf("") }
    var todoOptions by remember { mutableStateOf<List<de.robnice.homeasssistant_shoppinglist.model.ShoppingList>>(emptyList()) }
    var todoExpanded by remember { mutableStateOf(false) }
    var todoLoading by remember { mutableStateOf(false) }
    var todoLoadError by remember { mutableStateOf<String?>(null) }
    var todoReloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(storedUrl, storedToken, storedTodoEntity) {
        url = storedUrl
        token = storedToken
        todoEntity = storedTodoEntity
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
        Column(modifier = Modifier.padding(16.dp)) {

            Text(t(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(8.dp))


            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(t(R.string.ha_token)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (tokenVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (tokenVisible)
                        Icons.Default.Visibility
                    else
                        Icons.Default.VisibilityOff

                    IconButton(onClick = {
                        tokenVisible = !tokenVisible
                    }) {
                        Icon(
                            imageVector = image,
                            contentDescription = null
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
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
                            label = { Text(t(R.string.settings_list )) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = todoExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = todoExpanded,
                            onDismissRequest = { todoExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            todoOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(
                                        text = option.name,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ) },
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

                OutlinedButton(
                    onClick = { todoReloadKey++ }
                ) {
                    Text(t(R.string.retry))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val cleanedUrl = normalizeHaUrl(url)
                val cleanedToken = token.trim()

                coroutineScope.launch {
                    dataStore.saveHaUrl(cleanedUrl)
                    dataStore.saveHaToken(cleanedToken)
                    dataStore.saveTodoEntity(todoEntity)
                    navController.popBackStack()
                }
            }) {
                Text(t(R.string.save))
            }
        }
    }
}

