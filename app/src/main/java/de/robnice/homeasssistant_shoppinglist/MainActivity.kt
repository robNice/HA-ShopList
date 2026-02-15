package de.robnice.homeasssistant_shoppinglist

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import de.robnice.homeasssistant_shoppinglist.ui.navigation.Screen
import de.robnice.homeasssistant_shoppinglist.ui.screens.SettingsScreen
import de.robnice.homeasssistant_shoppinglist.ui.theme.HomeAsssistantShoppingListTheme
import de.robnice.homeasssistant_shoppinglist.ui.util.t
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import de.robnice.homeasssistant_shoppinglist.viewmodel.ShoppingViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

class MainActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeAsssistantShoppingListTheme {

                val navController = rememberNavController()
                val context = LocalContext.current
                val dataStore = remember { SettingsDataStore(context) }
                val haUrl by dataStore.haUrl.collectAsState(initial = "")
                val haToken by dataStore.haToken.collectAsState(initial = "")

                val startDestination = if (haUrl.isBlank() || haToken.isBlank()) {
                    Screen.Settings.route
                } else {
                    Screen.Shopping.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {

                    composable(Screen.Shopping.route) {
                        ShoppingScreen(navController)
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(navController)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(navController: NavController) {

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val haUrl by dataStore.haUrl.collectAsState(initial = "")
    val haToken by dataStore.haToken.collectAsState(initial = "")
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (haUrl.isBlank() || haToken.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Missing configuration")
        }
        return
    }

    val repository = remember(haUrl, haToken) {
        HaWebSocketRepository(
            baseUrl = haUrl,
            token = haToken
        )
    }

    val viewModel = remember(repository) {
        ShoppingViewModel(repository)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.ensureConnection()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val items by viewModel.items.collectAsState()

    var newItem by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = t(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            if (items.isEmpty()) {
                Text(
                    text = t(R.string.no_items),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {

                        Row {
                            OutlinedTextField(
                                value = newItem,
                                onValueChange = { newItem = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text( t(R.string.new_item) ) }
                            )

                            Spacer(Modifier.width(8.dp))

                            Button(onClick = {
                                if (newItem.isNotBlank()) {
                                    viewModel.addItem(newItem)
                                    newItem = ""
                                }
                            }) {
                                Text(t(R.string.add))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        val openItems = items.filter { !it.complete }
                        val completedItems = items.filter { it.complete }

                        var completedExpanded by remember { mutableStateOf(false) }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {

                            // 🔵 Offene Items
                            items(
                                items = openItems,
                                key = { it.id }
                            ) { item ->
                                ShoppingRow(
                                    item = item,
                                    isEditing = editingItemId == item.id,
                                    onStartEdit = { editingItemId = item.id },
                                    onStopEdit = { editingItemId = null },
                                    viewModel = viewModel
                                )
                            }

                            // 🟡 Completed Header
                            if (completedItems.isNotEmpty()) {

                                item {
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))

                                    TextButton(
                                        onClick = { completedExpanded = !completedExpanded }
                                    ) {
                                        Text(
                                            if (completedExpanded)
                                                "${t(R.string.completed)} (${completedItems.size}) ▲"
                                            else
                                                "${t(R.string.completed)} (${completedItems.size}) ▼"
                                        )
                                    }
                                }

                                // 🟡 Completed Items (nur wenn expanded)
                                if (completedExpanded) {
                                    items(
                                        items = completedItems,
                                        key = { it.id }
                                    ) { item ->
                                        ShoppingRow(
                                            item = item,
                                            isEditing = editingItemId == item.id,
                                            onStartEdit = { editingItemId = item.id },
                                            onStopEdit = { editingItemId = null },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }


                        val hasCompleted = items.any { it.complete }

                        if (hasCompleted) {
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )
                            Spacer(Modifier.height(12.dp))

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                //onClick = { viewModel.clearCompleted() },
                                onClick = { showConfirmDialog = true  },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text( t(R.string.clear_completed) )
                            }
                        }
                    }
                }
            }
        }
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Bestätigung") },
            text = { Text("Alle erledigten Einträge wirklich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.clearCompleted()
                    }
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }
    }


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ShoppingRow(
    item: ShoppingItem,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onStopEdit: () -> Unit,
    viewModel: ShoppingViewModel
) {
    var visible by remember { mutableStateOf(true) }
    var localChecked by remember(item.id) { mutableStateOf(item.complete) }
    val scope = rememberCoroutineScope()
    var editText by remember(item.id) { mutableStateOf(item.name) }

    val wasCompleted = remember { mutableStateOf(item.complete) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(item.name) {
        if (!isEditing) {
            editText = item.name
        }
    }


    val exitAnimation =
        if (item.complete) {
            slideOutVertically(
                targetOffsetY = { fullHeight -> -fullHeight / 3 },
                animationSpec = tween(250)
            )
        } else {
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = tween(250)
            )
        } + fadeOut(animationSpec = tween(250))

    AnimatedVisibility(
        visible = visible,
        exit = exitAnimation
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = localChecked,
                onCheckedChange = {
                    localChecked = it
                    scope.launch {
                        viewModel.toggleItem(item)
                    }
                }
            )


            if (isEditing) {

                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (editText.isNotBlank() && editText != item.name) {
                                viewModel.renameItem(item, editText)
                            }
                            onStopEdit()
                        }
                    )
                )


            } else {

                Text(
                    text = item.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onStartEdit()
                            editText = item.name
                        },
                    style = if (localChecked)
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    else
                        MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}





