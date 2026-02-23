package de.robnice.homeasssistant_shoppinglist

import android.Manifest
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import de.robnice.homeasssistant_shoppinglist.util.Debug
import de.robnice.homeasssistant_shoppinglist.data.HaRuntime
import kotlinx.coroutines.flow.combine

class MainActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            HomeAsssistantShoppingListTheme {

                val navController = rememberNavController()
                val context = LocalContext.current
                val dataStore = remember { SettingsDataStore(context) }

                val configFlow = remember(dataStore) {
                    combine(dataStore.haUrl, dataStore.haToken) { url, token ->
                        url to token
                    }
                }

                val config by configFlow.collectAsState(initial = null)
                val haUrl = config?.first
                val haToken = config?.second

                val startDestination =
                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank()) {
                        Screen.Settings.route
                    } else {
                        Screen.Shopping.route
                    }

                val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)

                LaunchedEffect(haUrl, haToken, notificationsEnabled) {
                    if (!notificationsEnabled) {
                        Debug.log("MainActivity: notifications disabled -> disconnect repo")
                        HaRuntime.repository?.setReconnectAllowed(false)
                        HaRuntime.repository?.disconnect()
                        return@LaunchedEffect
                    }

                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank()) return@LaunchedEffect

                    val repo = HaRuntime.repository
                    if (repo == null) {
                        Debug.log("MainActivity: create repository")
                        HaRuntime.repository = de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository(
                            haUrl!!,
                            haToken!!,
                            context.applicationContext
                        )
                    } else {
                        Debug.log("MainActivity: repository already exists")
                    }
                }

                if (config == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    return@HomeAsssistantShoppingListTheme
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
    val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)

    if (haUrl.isBlank() || haToken.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Missing configuration")
        }
        return
    }


    val repo = HaRuntime.repository
    if (repo == null) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Settings.route) {
                popUpTo(Screen.Shopping.route) { inclusive = true }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }


    val viewModel = remember(repo) { ShoppingViewModel(repo) }

    val authFailed by viewModel.authFailed.collectAsState()
    val connectionErrors by viewModel.connectionErrors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(repo) {
        repo.reconnected.collect { ts ->
            if (ts == 0L) return@collect

            Toast.makeText(
                context,
                context.getString(R.string.reconnected),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner, notificationsEnabled, repo) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (notificationsEnabled) {
                        repo.setReconnectAllowed(true)
                        viewModel.ensureConnection()
                    }
                }

                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    repo.setReconnectAllowed(false)
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val items by viewModel.items.collectAsState()


    if (authFailed || connectionErrors) {
        val errorTitle: String
        val errorText: String
        if (authFailed) {
            errorTitle = t(R.string.auth_failed_title)
            errorText = t(R.string.auth_failed_text)
        } else {
            errorTitle = t(R.string.connection_errors_title)
            errorText = t(R.string.connection_errors_text)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {

            Card(
                elevation = CardDefaults.cardElevation(6.dp),
                shape = MaterialTheme.shapes.large
            ) {

                Column(
                    modifier = Modifier
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = errorTitle,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(t(R.string.settings_cta))
                    }
                }
            }
        }
        return
    }

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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .pointerInput(editingItemId) {
                            detectTapGestures {
                                editingItemId = null
                            }
                        }
                ) {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            OutlinedTextField(
                                value = newItem,
                                onValueChange = { newItem = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(t(R.string.new_item)) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newItem.isNotBlank()) {
                                            viewModel.addItem(newItem.trim())
                                            newItem = ""
                                        }
                                    }
                                )
                            )

                            Spacer(Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    if (newItem.isNotBlank()) {
                                        viewModel.addItem(newItem)
                                        newItem = ""
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = t(R.string.add),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }


                    Spacer(Modifier.height(16.dp))

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t(R.string.no_items))
                        }
                    } else {
                    val openItems = items.filter { !it.complete }
                    val completedItems = items.filter { it.complete }
                    var completedExpanded by remember { mutableStateOf(false) }

                    LaunchedEffect(items) {
                        if (items.none { it.complete }) {
                            completedExpanded = false
                        }
                    }

                    var localOpenItems by remember {
                        mutableStateOf<List<ShoppingItem>>(emptyList())
                    }

                    LaunchedEffect(openItems) {
                        localOpenItems = openItems
                    }


                    val lazyListState = rememberLazyListState()

                    var draggingOpenKey by remember { mutableStateOf<String?>(null) }

                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        localOpenItems = localOpenItems.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }

                    LaunchedEffect(completedExpanded, localOpenItems.size, completedItems.size) {
                        if (completedExpanded && completedItems.isNotEmpty()) {
                            val headerIndex = localOpenItems.size
                            kotlinx.coroutines.delay(16)
                            lazyListState.animateScrollToItem(headerIndex)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {

                        items(
                            items = localOpenItems,
                            key = { "open_${it.id}" }
                        ) { item ->

                            val openKey = "open_${item.id}"

                            ReorderableItem(reorderState, key = openKey) { isDragging ->

                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 16.dp else 2.dp,
                                    label = "dragElevation"
                                )

                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.03f else 1f,
                                    label = "dragScale"
                                )

                                Card(
                                    elevation = CardDefaults.cardElevation(elevation),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .fillMaxWidth()
                                        .longPressDraggableHandle(
                                            onDragStarted = { draggingOpenKey = openKey },
                                            onDragStopped = {
                                                val id = draggingOpenKey?.removePrefix("open_")
                                                    ?: return@longPressDraggableHandle
                                                draggingOpenKey = null

                                                val endIndex =
                                                    localOpenItems.indexOfFirst { it.id == id }
                                                if (endIndex < 0) return@longPressDraggableHandle

                                                val movedItem = localOpenItems[endIndex]
                                                val previousItemId =
                                                    if (endIndex > 0) localOpenItems[endIndex - 1].id else null

                                                viewModel.moveItem(movedItem.id, previousItemId)
                                            }
                                        )
                                ) {
                                    ShoppingRow(
                                        item = item,
                                        isEditing = editingItemId == item.id,
                                        onStartEdit = { clickedId ->

                                            if (editingItemId != null && editingItemId != clickedId) {
                                                editingItemId = null
                                                return@ShoppingRow
                                            }

                                            editingItemId = clickedId
                                        },
                                        onStopEdit = { editingItemId = null },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }



                        if (completedItems.isNotEmpty()) {

                            item {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(Modifier.height(8.dp))

                                TextButton(
                                    onClick = {
                                        completedExpanded = !completedExpanded
                                    }
                                ) {
                                    Text(
                                        text = "${t(R.string.completed)} (${completedItems.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(if (completedExpanded) "▲" else "▼")
                                }
                            }

                            if (completedExpanded) {
                                items(
                                    items = completedItems,
                                    key = { "completed_${it.id}" }
                                ) { item ->
                                    ShoppingRow(
                                        item = item,
                                        isEditing = editingItemId == item.id,
                                        onStartEdit = { clickedId ->

                                            if (editingItemId != null && editingItemId != clickedId) {
                                                editingItemId = null
                                                return@ShoppingRow
                                            }

                                            editingItemId = clickedId
                                        },
                                        onStopEdit = { editingItemId = null },
                                        viewModel = viewModel,
                                        animateVisibility = false
                                    )
                                }
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
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),

                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = t(R.string.clear_completed),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                    }
                }
            }
        }
    }
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(t(R.string.clear_completed_confirm_title )) },
            text = { Text(t(R.string.clear_completed_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.clearCompleted()
                    }
                ) {
                    Text(t(R.string.clear_completed_confirm_btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text(t(R.string.clear_completed_confirm_btn_cancel))
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
    onStartEdit: (String) -> Unit,
    onStopEdit: () -> Unit,
    viewModel: ShoppingViewModel,
    modifier: Modifier = Modifier,
    animateVisibility: Boolean = true
) {
    var localChecked by remember(item.id) { mutableStateOf(item.complete) }
    val scope = rememberCoroutineScope()
    var editText by remember(item.id) { mutableStateOf(item.name) }

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
        visible = if (animateVisibility) !item.complete else true,
        exit = exitAnimation
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = localChecked,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
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
                            onStartEdit(item.id)
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





