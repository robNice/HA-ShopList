package de.robnice.homeasssistant_shoppinglist

import android.Manifest
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import de.robnice.homeasssistant_shoppinglist.viewmodel.ShoppingViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import de.robnice.homeasssistant_shoppinglist.data.history.ProductHistoryEntity
import de.robnice.homeasssistant_shoppinglist.data.history.ProductHistoryRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.StrokeCap
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import de.robnice.homeasssistant_shoppinglist.util.Debug
import de.robnice.homeasssistant_shoppinglist.data.HaRuntime
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import de.robnice.homeasssistant_shoppinglist.ui.theme.BrandBlue
import de.robnice.homeasssistant_shoppinglist.ui.theme.BrandBlueGlow
import de.robnice.homeasssistant_shoppinglist.ui.theme.BrandGreen
import de.robnice.homeasssistant_shoppinglist.ui.theme.BrandOrange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
                    combine(dataStore.haUrl, dataStore.haToken, dataStore.todoEntity) { url, token, entity ->
                        Triple(url, token, entity)
                    }
                }

                val config by configFlow.collectAsState(initial = null)
                val haUrl = config?.first
                val haToken = config?.second
                val todoEntity = config?.third

                val startDestination =
                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank()) {
                        Screen.Settings.route
                    } else {
                        Screen.Shopping.route
                    }

                val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)

                LaunchedEffect(haUrl, haToken, todoEntity, notificationsEnabled) {
                    if (!notificationsEnabled) {
                        Debug.log("MainActivity: notifications disabled -> disconnect repo")
                        HaRuntime.repository?.setReconnectAllowed(false)
                        HaRuntime.repository?.disconnect()
                        return@LaunchedEffect
                    }

                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank() || todoEntity.isNullOrBlank()) {
                        return@LaunchedEffect
                    }

                    val repo = HaRuntime.repository
                    val configChanged =
                        repo == null ||
                                HaRuntime.baseUrl != haUrl ||
                                HaRuntime.token != haToken ||
                                HaRuntime.todoEntity != todoEntity

                    if (configChanged) {
                        Debug.log("MainActivity: recreate repository because config changed")

                        HaRuntime.repository?.disconnect()

                        HaRuntime.baseUrl = haUrl
                        HaRuntime.token = haToken
                        HaRuntime.todoEntity = todoEntity

                        HaRuntime.repository = de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository(
                            haUrl,
                            haToken,
                            context.applicationContext,
                            todoEntity
                        )
                    } else {
                        Debug.log("MainActivity: repository already exists with same config")
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
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context) }
    val productHistoryRepository = remember(context) { ProductHistoryRepository.getInstance(context) }
    val configFlow = remember(dataStore) {
        combine(dataStore.haUrl, dataStore.haToken, dataStore.todoEntity) { url, token, entity ->
            Triple(url, token, entity)
        }
    }
    val config by configFlow.collectAsState(initial = null)
    val haUrl = config?.first
    val haToken = config?.second
    val todoEntity = config?.third
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineInfo by remember { mutableStateOf(false) }
    val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)

    if (config == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank()) {
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


    val repo = HaRuntime.repository
    if (repo == null) {
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
    val isOffline by viewModel.isOffline.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var headerPulseKey by remember { mutableStateOf(0) }

    val triggerHeaderPulse = {
        headerPulseKey += 1
    }

    LaunchedEffect(repo, notificationsEnabled) {
        if (notificationsEnabled) {
            repo.setReconnectAllowed(true)
            viewModel.ensureConnection()
        }
    }

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

    LaunchedEffect(viewModel) {
        viewModel.remoteActivity.collect {
            headerPulseKey += 1
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


    if (authFailed) {
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
                        text = t(R.string.auth_failed_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = t(R.string.auth_failed_text),
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
    val autocompleteFlow = remember(newItem, productHistoryRepository) {
        if (newItem.isBlank()) {
            flowOf(emptyList())
        } else {
            productHistoryRepository.observeSuggestions(newItem, limit = 5)
        }
    }
    val autocompleteSuggestions by autocompleteFlow.collectAsState(initial = emptyList())
    val listTitle = remember(todoEntity) { todoEntity.orEmpty().toDisplayListTitle() }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 76.dp,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(x = (-10).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeaderWordmark(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                pulseKey = headerPulseKey
                            )

                            if (isOffline) {
                                AssistChip(
                                    onClick = { showOfflineInfo = true },
                                    label = { Text(t(R.string.offline_chip)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }

                        Text(
                            text = listTitle,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .offset(y = (-8).dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
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
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
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
                                                triggerHeaderPulse()
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
                                            triggerHeaderPulse()
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

                            if (newItem.isNotBlank() && autocompleteSuggestions.isNotEmpty()) {
                                val currentItemNames = items
                                    .map { ProductHistoryRepository.normalizeName(it.name) }
                                    .toSet()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(vertical = 4.dp)
                                ) {
                                    autocompleteSuggestions.forEachIndexed { index, suggestion ->
                                        HistorySuggestionRow(
                                            suggestion = suggestion,
                                            canDelete = suggestion.normalizedName !in currentItemNames,
                                            onSelect = {
                                                triggerHeaderPulse()
                                                viewModel.addItem(suggestion.displayName)
                                                newItem = ""
                                            },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    productHistoryRepository.deleteProduct(suggestion.normalizedName)
                                                }
                                            }
                                        )

                                        if (index < autocompleteSuggestions.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }


                    Spacer(Modifier.height(16.dp))

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isOffline) {
                                    t(R.string.offline_empty_list)
                                } else {
                                    t(R.string.no_items)
                                },
                                textAlign = TextAlign.Center
                            )
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
                    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
                    var droppedItemId by remember { mutableStateOf<String?>(null) }

                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        localOpenItems = localOpenItems.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }

                    val currentDragIndex = remember(draggingOpenKey, localOpenItems) {
                        val draggedId = draggingOpenKey?.removePrefix("open_") ?: return@remember -1
                        localOpenItems.indexOfFirst { it.id == draggedId }
                    }
                    val hasActiveDropPreview =
                        draggingOpenKey != null &&
                            dragStartIndex != null &&
                            currentDragIndex >= 0 &&
                            currentDragIndex != dragStartIndex
                    val insertLineBeforeIndex =
                        if (hasActiveDropPreview && currentDragIndex < localOpenItems.lastIndex) {
                            currentDragIndex + 1
                        } else {
                            null
                        }
                    val showInsertLineAtEnd =
                        hasActiveDropPreview && currentDragIndex == localOpenItems.lastIndex

                    LaunchedEffect(droppedItemId) {
                        if (droppedItemId != null) {
                            kotlinx.coroutines.delay(650)
                            droppedItemId = null
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

                        itemsIndexed(
                            items = localOpenItems,
                            key = { _, openItem -> "open_${openItem.id}" }
                        ) { index, item ->

                            val openKey = "open_${item.id}"

                            ReorderableItem(reorderState, key = openKey) { isDragging ->
                                val isJustDropped = droppedItemId == item.id
                                val baseContainer = MaterialTheme.colorScheme.surfaceVariant
                                val targetContainer = when {
                                    isDragging -> lerp(
                                        baseContainer,
                                        if (isSystemInDarkTheme()) BrandBlueGlow else BrandBlue,
                                        if (isSystemInDarkTheme()) 0.22f else 0.12f
                                    )
                                    isJustDropped -> lerp(
                                        baseContainer,
                                        BrandGreen,
                                        if (isSystemInDarkTheme()) 0.18f else 0.12f
                                    )
                                    else -> baseContainer
                                }
                                val containerColor by animateColorAsState(
                                    targetValue = targetContainer,
                                    label = "dragCardColor"
                                )
                                val borderColor by animateColorAsState(
                                    targetValue = when {
                                        isDragging -> BrandBlue
                                        isJustDropped -> BrandGreen
                                        else -> Color.Transparent
                                    },
                                    label = "dragCardBorderColor"
                                )
                                val borderWidth by animateDpAsState(
                                    targetValue = when {
                                        isDragging -> 1.5.dp
                                        isJustDropped -> 1.dp
                                        else -> 0.dp
                                    },
                                    label = "dragCardBorderWidth"
                                )

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
                                    border = if (borderWidth > 0.dp) {
                                        BorderStroke(borderWidth, borderColor)
                                    } else {
                                        null
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = containerColor
                                    ),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .fillMaxWidth()
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                draggingOpenKey = openKey
                                                dragStartIndex = index
                                                droppedItemId = null
                                            },
                                            onDragStopped = {
                                                val id = draggingOpenKey?.removePrefix("open_")
                                                    ?: return@longPressDraggableHandle
                                                val startIndex = dragStartIndex
                                                draggingOpenKey = null
                                                dragStartIndex = null

                                                val endIndex =
                                                    localOpenItems.indexOfFirst { it.id == id }
                                                if (endIndex < 0) return@longPressDraggableHandle

                                                val movedItem = localOpenItems[endIndex]
                                                if (startIndex != null && startIndex != endIndex) {
                                                    droppedItemId = movedItem.id
                                                    triggerHeaderPulse()
                                                }
                                                val previousItemId =
                                                    if (endIndex > 0) localOpenItems[endIndex - 1].id else null

                                                viewModel.moveItem(movedItem.id, previousItemId)
                                            }
                                        )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        if (insertLineBeforeIndex == index) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .padding(horizontal = 18.dp, vertical = 2.dp)
                                                    .background(
                                                        color = BrandOrange,
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                            )
                                        }

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
                                            onServerInteraction = triggerHeaderPulse
                                        )
                                    }
                                }
                            }
                        }

                        if (showInsertLineAtEnd) {
                            item(key = "open_drop_indicator_end") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .padding(horizontal = 18.dp, vertical = 2.dp)
                                        .background(
                                            color = BrandOrange,
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                )
                            }
                        }



                        if (completedItems.isNotEmpty()) {

                            item {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = BrandOrange.copy(alpha = if (isSystemInDarkTheme()) 0.45f else 0.35f)
                                )
                                Spacer(Modifier.height(8.dp))

                                TextButton(
                                    onClick = {
                                        completedExpanded = !completedExpanded
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = BrandOrange
                                    )
                                ) {
                                    Text(
                                        text = "${t(R.string.completed)} (${completedItems.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(if (completedExpanded) "▲" else "▼")
                                }
                            }

                            if (completedExpanded) {
                                items(
                                    items = completedItems,
                                    key = { "completed_${it.id}" }
                                ) { item ->
                                    Card(
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(
                                            containerColor = lerp(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                BrandGreen,
                                                if (isSystemInDarkTheme()) 0.14f else 0.10f
                                            )
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            BrandGreen.copy(alpha = if (isSystemInDarkTheme()) 0.36f else 0.24f)
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
                                            viewModel = viewModel,
                                            onServerInteraction = triggerHeaderPulse,
                                            animateVisibility = false,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
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
                                containerColor = if (isSystemInDarkTheme()) {
                                    BrandOrange.copy(alpha = 0.24f)
                                } else {
                                    BrandOrange.copy(alpha = 0.18f)
                                },
                                contentColor = if (isSystemInDarkTheme()) {
                                    Color(0xFFFFE7C2)
                                } else {
                                    Color(0xFF7A4A00)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSystemInDarkTheme()) {
                                    BrandOrange.copy(alpha = 0.48f)
                                } else {
                                    BrandOrange.copy(alpha = 0.38f)
                                }
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
                        triggerHeaderPulse()
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

    if (showOfflineInfo) {
        AlertDialog(
            onDismissRequest = { showOfflineInfo = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null
                )
            },
            title = { Text(t(R.string.offline_dialog_title)) },
            text = { Text(t(R.string.offline_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showOfflineInfo = false }
                ) {
                    Text(t(R.string.offline_dialog_confirm))
                }
            }
        )
    }
}

private data class HeaderLineSegment(
    val startPx: Float,
    val endPx: Float,
    val color: Color,
    val laps: Int,
    val durationMillis: Int
)

@Composable
private fun HeaderWordmark(
    modifier: Modifier = Modifier,
    pulseKey: Int = 0
) {
    val isDarkTheme = isSystemInDarkTheme()
    val assetName = if (isDarkTheme) {
        "ha-shoplist-wordmark.svg"
    } else {
        "ha-shoplist-wordmark-light.svg"
    }
    val cartAssetName = if (isDarkTheme) {
        "ha-shoplist-cart-dark.svg"
    } else {
        "ha-shoplist-cart-light.svg"
    }
    val segmentProgress = remember {
        listOf(
            Animatable(0f),
            Animatable(0f),
            Animatable(0f),
            Animatable(0f)
        )
    }

    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) {
            return@LaunchedEffect
        }

        segmentProgress.forEach { it.snapTo(0f) }
        launch {
            segmentProgress[0].animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1680, easing = LinearEasing)
            )
            segmentProgress[0].snapTo(0f)
        }
        launch {
            segmentProgress[1].animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 980, easing = LinearEasing)
            )
            segmentProgress[1].snapTo(0f)
        }
        launch {
            segmentProgress[2].animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1320, easing = LinearEasing)
            )
            segmentProgress[2].snapTo(0f)
        }
        launch {
            segmentProgress[3].animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 860, easing = LinearEasing)
            )
            segmentProgress[3].snapTo(0f)
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val y = size.height * 0.80f
            val stroke = 4.dp.toPx()
            val backgroundStart = 22.dp.toPx()
            val backgroundEnd = size.width
            val trackLength = backgroundEnd - backgroundStart
            val cartStart = size.width - 34.dp.toPx()
            drawLine(
                color = BrandBlue.copy(alpha = if (isDarkTheme) 0.34f else 0.22f),
                start = Offset(backgroundStart, y),
                end = Offset(backgroundEnd, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val segments = listOf(
                HeaderLineSegment(
                    startPx = 42.dp.toPx(),
                    endPx = 108.dp.toPx(),
                    color = BrandOrange,
                    laps = 1,
                    durationMillis = 1680
                ),
                HeaderLineSegment(
                    startPx = 126.dp.toPx(),
                    endPx = 156.dp.toPx(),
                    color = BrandGreen,
                    laps = 2,
                    durationMillis = 980
                ),
                HeaderLineSegment(
                    startPx = 244.dp.toPx(),
                    endPx = 310.dp.toPx(),
                    color = BrandOrange,
                    laps = 1,
                    durationMillis = 1320
                ),
                HeaderLineSegment(
                    startPx = cartStart - 74.dp.toPx(),
                    endPx = cartStart - 14.dp.toPx(),
                    color = BrandOrange,
                    laps = 2,
                    durationMillis = 860
                )
            )

            segments.forEachIndexed { index, segment ->
                val offsetPx = (segmentProgress[index].value * segment.laps * trackLength) % trackLength
                drawWrappedHeaderSegment(
                    trackStart = backgroundStart,
                    trackLength = trackLength,
                    y = y,
                    strokeWidth = stroke,
                    segment = segment,
                    offsetPx = offsetPx
                )
            }
        }
        AsyncImage(
            modifier = Modifier.matchParentSize(),
            model = "file:///android_asset/$assetName",
            contentDescription = "ShopList wordmark",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart
        )
        AsyncImage(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 8.dp, y = 2.dp)
                .size(width = 42.dp, height = 28.dp),
            model = "file:///android_asset/$cartAssetName",
            contentDescription = "Shopping cart",
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWrappedHeaderSegment(
    trackStart: Float,
    trackLength: Float,
    y: Float,
    strokeWidth: Float,
    segment: HeaderLineSegment,
    offsetPx: Float
) {
    val segmentWidth = segment.endPx - segment.startPx
    val shiftedStart = ((segment.startPx - trackStart) + offsetPx) % trackLength
    val shiftedEnd = shiftedStart + segmentWidth

    if (shiftedEnd <= trackLength) {
        drawLine(
            color = segment.color,
            start = Offset(trackStart + shiftedStart, y),
            end = Offset(trackStart + shiftedEnd, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        return
    }

    drawLine(
        color = segment.color,
        start = Offset(trackStart + shiftedStart, y),
        end = Offset(trackStart + trackLength, y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = segment.color,
        start = Offset(trackStart, y),
        end = Offset(trackStart + (shiftedEnd - trackLength), y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun String.toDisplayListTitle(): String {
    val rawName = substringAfter("todo.", this)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()

    if (rawName.isBlank()) {
        return "Shopping List"
    }

    return rawName
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}

@Composable
private fun HistorySuggestionRow(
    suggestion: ProductHistoryEntity,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = suggestion.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        if (canDelete) {
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = t(R.string.delete_history_item)
                )
            }
        }
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
    onServerInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
    animateVisibility: Boolean = true
) {
    var localChecked by remember(item.id) { mutableStateOf(item.complete) }
    val scope = rememberCoroutineScope()
    var editText by remember(item.id) { mutableStateOf(item.name) }
    val isDarkTheme = isSystemInDarkTheme()
    val completedTextColor = if (isDarkTheme) {
        lerp(MaterialTheme.colorScheme.onSurface, BrandGreen, 0.48f)
    } else {
        lerp(MaterialTheme.colorScheme.onSurface, BrandGreen, 0.62f)
    }

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
                    checkedColor = BrandGreen,
                    checkmarkColor = if (isDarkTheme) Color.Black else Color.White,
                    uncheckedColor = BrandBlue.copy(alpha = if (isDarkTheme) 0.9f else 0.72f)
                ),
                onCheckedChange = {
                    onStopEdit()
                    localChecked = it
                    onServerInteraction()
                    scope.launch {
                        viewModel.toggleItem(item)
                    }
                }
            )


            if (isEditing) {

                val saveEdit = {
                    if (editText.isNotBlank() && editText != item.name) {
                        onServerInteraction()
                        viewModel.renameItem(item, editText)
                    }
                    onStopEdit()
                }

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
                            saveEdit()
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = saveEdit,
                            enabled = editText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = t(R.string.save_edit)
                            )
                        }
                    }
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
                            color = completedTextColor,
                            textDecoration = TextDecoration.LineThrough
                        )
                    else
                        MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}





