package de.robnice.homeshoplist

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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.robnice.homeshoplist.ui.navigation.Screen
import de.robnice.homeshoplist.ui.screens.SettingsScreen
import de.robnice.homeshoplist.ui.theme.HomeAsssistantShoppingListTheme
import de.robnice.homeshoplist.ui.util.t
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.update.AppUpdateRepository
import de.robnice.homeshoplist.data.update.UpdateCheckResult
import de.robnice.homeshoplist.viewmodel.ShoppingViewModel
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
import de.robnice.homeshoplist.data.history.ProductHistoryEntity
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.model.ShoppingListDisplayMode
import de.robnice.homeshoplist.model.label
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import de.robnice.homeshoplist.util.Debug
import de.robnice.homeshoplist.data.HaRuntime
import de.robnice.homeshoplist.data.HaWebSocketRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import de.robnice.homeshoplist.ui.theme.BrandBlue
import de.robnice.homeshoplist.ui.theme.BrandBlueGlow
import de.robnice.homeshoplist.ui.theme.BrandGreen
import de.robnice.homeshoplist.ui.theme.BrandOrange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex

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
                val updateRepository = remember(context) { AppUpdateRepository(context.applicationContext) }

                val configFlow = remember(dataStore) {
                    combine(dataStore.haUrl, dataStore.haToken, dataStore.todoEntity, dataStore.todoListName) { url, token, entity, listName ->
                        arrayOf(url, token, entity, listName)
                    }
                }

                val config by configFlow.collectAsState(initial = null)
                val updateLastCheckMillis by dataStore.updateLastCheckMillis.collectAsState(initial = 0L)
                val haUrl = config?.getOrNull(0)
                val haToken = config?.getOrNull(1)
                val todoEntity = config?.getOrNull(2)
                val todoListName = config?.getOrNull(3)

                val startDestination =
                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank()) {
                        Screen.Settings.route
                    } else {
                        Screen.Shopping.route
                    }

                val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)
                var runtimeRepository by remember {
                    mutableStateOf<HaWebSocketRepository?>(HaRuntime.repository)
                }

                LaunchedEffect(updateLastCheckMillis) {
                    if (!updateRepository.isGithubUpdaterAllowed()) {
                        return@LaunchedEffect
                    }

                    val now = System.currentTimeMillis()
                    if (now - updateLastCheckMillis < AppUpdateRepository.UPDATE_CHECK_INTERVAL_MILLIS) {
                        return@LaunchedEffect
                    }

                    try {
                        when (val result = updateRepository.checkLatestRelease()) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                dataStore.saveAvailableUpdate(
                                    versionName = result.update.versionName,
                                    tagName = result.update.tagName,
                                    apkUrl = result.update.apkDownloadUrl,
                                    releaseUrl = result.update.releaseUrl,
                                    changelog = result.update.changelog
                                )
                            }

                            UpdateCheckResult.UpToDate -> {
                                dataStore.clearAvailableUpdate()
                            }
                        }

                        dataStore.saveUpdateCheckTimestamp(now)
                    } catch (_: Exception) {
                        // Silent best-effort check. Manual checks in Settings show errors.
                    }
                }

                LaunchedEffect(haUrl, haToken, todoEntity, todoListName) {
                    if (haUrl.isNullOrBlank() || haToken.isNullOrBlank() || todoEntity.isNullOrBlank()) {
                        runtimeRepository = null
                        return@LaunchedEffect
                    }

                    val repo = HaRuntime.repository
                    val configChanged =
                        repo == null ||
                                HaRuntime.baseUrl != haUrl ||
                                HaRuntime.token != haToken ||
                                HaRuntime.todoEntity != todoEntity ||
                                HaRuntime.todoListName != todoListName

                    if (configChanged) {
                        Debug.log("MainActivity: recreate repository because config changed")

                        HaRuntime.repository?.disconnect()

                        HaRuntime.baseUrl = haUrl
                        HaRuntime.token = haToken
                        HaRuntime.todoEntity = todoEntity
                        HaRuntime.todoListName = todoListName

                        HaRuntime.repository = de.robnice.homeshoplist.data.HaWebSocketRepository(
                            haUrl,
                            haToken,
                            context.applicationContext,
                            todoEntity,
                            todoListName
                        )
                        runtimeRepository = HaRuntime.repository
                    } else {
                        Debug.log("MainActivity: repository already exists with same config")
                        runtimeRepository = repo
                    }

                    runtimeRepository?.setReconnectAllowed(true)
                    runtimeRepository?.ensureConnected()
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
                        ShoppingScreen(
                            navController = navController,
                            repository = runtimeRepository
                        )
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
fun ShoppingScreen(
    navController: NavController,
    repository: HaWebSocketRepository?
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context) }
    val productHistoryRepository = remember(context) { ProductHistoryRepository.getInstance(context) }
    val configFlow = remember(dataStore) {
        combine(dataStore.haUrl, dataStore.haToken, dataStore.todoEntity, dataStore.todoListName) { url, token, entity, listName ->
            arrayOf(url, token, entity, listName)
        }
    }
    val config by configFlow.collectAsState(initial = null)
    val haUrl = config?.getOrNull(0)
    val haToken = config?.getOrNull(1)
    val todoEntity = config?.getOrNull(2)
    val todoListName = config?.getOrNull(3)
    val areaOrderRaw by dataStore.areaOrder.collectAsState(initial = "")
    val enabledAreasRaw by dataStore.enabledAreas.collectAsState(initial = "")
    val listDisplayModeRaw by dataStore.listDisplayMode.collectAsState(initial = "categorized")
    val orderedAreas = remember(areaOrderRaw) {
        ShoppingArea.orderedFromStorage(areaOrderRaw)
    }
    val enabledAreas = remember(orderedAreas, enabledAreasRaw) {
        ShoppingArea.enabledFromStorage(enabledAreasRaw, orderedAreas)
    }
    val listDisplayMode = remember(listDisplayModeRaw) {
        ShoppingListDisplayMode.fromStorage(listDisplayModeRaw)
    }
    val effectiveListDisplayMode = listDisplayMode
    val isCategorizedListMode = effectiveListDisplayMode == ShoppingListDisplayMode.CATEGORIZED
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


    if (repository == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val activeRepo = repository


    val viewModel = remember(activeRepo) { ShoppingViewModel(activeRepo) }

    val authFailed by viewModel.authFailed.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var headerPulseKey by remember { mutableStateOf(0) }

    val triggerHeaderPulse = {
        headerPulseKey += 1
    }

    LaunchedEffect(activeRepo) {
        activeRepo.setReconnectAllowed(true)
        viewModel.ensureConnection()
    }

    LaunchedEffect(activeRepo) {
        activeRepo.reconnected.collect { ts ->
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

    DisposableEffect(lifecycleOwner, notificationsEnabled, activeRepo) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    activeRepo.setReconnectAllowed(true)
                    viewModel.ensureConnection()
                }

                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    activeRepo.setReconnectAllowed(false)
                    if (!notificationsEnabled) {
                        activeRepo.disconnect()
                    }
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
    var selectedArea by remember { mutableStateOf(ShoppingArea.OTHER) }
    LaunchedEffect(enabledAreas) {
        if (selectedArea !in enabledAreas) {
            selectedArea = enabledAreas.firstOrNull() ?: ShoppingArea.OTHER
        }
    }
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
        contentWindowInsets = WindowInsets.safeDrawing,
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

                            if (isConnecting || isOffline) {
                                AssistChip(
                                    onClick = { showOfflineInfo = true },
                                    label = {
                                        Text(
                                            if (isConnecting) {
                                                t(R.string.connecting_chip)
                                            } else {
                                                t(R.string.offline_chip)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isConnecting) {
                                                Icons.Default.Sync
                                            } else {
                                                Icons.Default.CloudOff
                                            },
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
                                                viewModel.addItem(newItem.trim(), selectedArea)
                                                newItem = ""
                                            }
                                        }
                                    )
                                )

                                AreaMenuButton(
                                    selectedArea = selectedArea,
                                    areas = enabledAreas,
                                    onAreaSelected = { area ->
                                        selectedArea = area
                                    }
                                )

                                Spacer(Modifier.width(12.dp))

                                IconButton(
                                    onClick = {
                                        if (newItem.isNotBlank()) {
                                            triggerHeaderPulse()
                                            viewModel.addItem(newItem.trim(), selectedArea)
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
                                                val suggestionArea: ShoppingArea =
                                                    ShoppingArea.fromKey(suggestion.areaKey) ?: selectedArea
                                                viewModel.addItem(suggestion.displayName, suggestionArea)
                                                selectedArea = suggestionArea
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
                    var dragPreviewItems by remember {
                        mutableStateOf<List<ShoppingItem>?>(null)
                    }
                    var deferredOpenItems by remember {
                        mutableStateOf<List<ShoppingItem>?>(null)
                    }
                    var pendingCommittedOrderIds by remember {
                        mutableStateOf<List<String>?>(null)
                    }
                    val normalizedOpenItems = remember(openItems, orderedAreas, effectiveListDisplayMode) {
                        normalizeOpenItemsForDisplay(openItems, orderedAreas, effectiveListDisplayMode)
                    }

                    val lazyListState = rememberLazyListState()

                    var droppedItemId by remember { mutableStateOf<String?>(null) }
                    var draggingItemId by remember { mutableStateOf<String?>(null) }
                    var activeDropSlotKey by remember { mutableStateOf<String?>(null) }
                    var dragPointerYInRoot by remember { mutableStateOf(0f) }
                    var dragTouchOffsetY by remember { mutableStateOf(0f) }
                    var listTopInRoot by remember { mutableStateOf(0f) }
                    val itemBounds = remember { mutableStateMapOf<String, ItemDragBounds>() }
                    val slotBounds = remember { mutableStateMapOf<String, DropSlotBounds>() }
                    val headerBounds = remember { mutableStateMapOf<String, HeaderBounds>() }
                    var dragStartItemBounds by remember { mutableStateOf<Map<String, ItemDragBounds>>(emptyMap()) }
                    var dragStartSlotBounds by remember { mutableStateOf<Map<String, DropSlotBounds>>(emptyMap()) }
                    var dragStartHeaderBounds by remember { mutableStateOf<Map<String, HeaderBounds>>(emptyMap()) }
                    var dragStartItemSlotAnchors by remember { mutableStateOf<Map<String, ItemSlotAnchors>>(emptyMap()) }
                    var dragStartDropSlotsByKey by remember { mutableStateOf<Map<String, DropSlotData>>(emptyMap()) }
                    var dragStartAllowedSlotKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
                    val displayedOpenItems =
                        if (dragPreviewItems != null) {
                            dragPreviewItems ?: localOpenItems
                        } else {
                            localOpenItems
                        }
                    val openDisplayEntries = remember(
                        displayedOpenItems,
                        activeDropSlotKey,
                        draggingItemId,
                        orderedAreas,
                        effectiveListDisplayMode
                    ) {
                        when (effectiveListDisplayMode) {
                            ShoppingListDisplayMode.CATEGORIZED -> buildOpenDisplayEntries(
                                items = displayedOpenItems,
                                activeDropSlotKey = activeDropSlotKey,
                                draggingItemId = draggingItemId,
                                orderedAreas = orderedAreas
                            )
                            ShoppingListDisplayMode.SIMPLE -> buildSimpleOpenDisplayEntries(
                                items = displayedOpenItems,
                                draggingItemId = draggingItemId
                            )
                        }
                    }
                    val activeDropCandidateKeys = remember(openDisplayEntries) {
                        openDisplayEntries
                            .mapNotNull { entry ->
                                (entry as? OpenListEntry.DropSlotEntry)
                                    ?.slot
                                    ?.takeUnless { it.isNoOp }
                                    ?.key
                            }
                            .toSet()
                    }
                    val itemSlotAnchors = remember(openDisplayEntries) {
                        buildItemSlotAnchors(openDisplayEntries)
                    }
                    val currentLocalOpenItems by rememberUpdatedState(localOpenItems)
                    val currentDisplayedOpenItems by rememberUpdatedState(displayedOpenItems)
                    val currentOpenDisplayEntries by rememberUpdatedState(openDisplayEntries)
                    val currentItemSlotAnchors by rememberUpdatedState(itemSlotAnchors)
                    val currentActiveDropCandidateKeys by rememberUpdatedState(activeDropCandidateKeys)
                    val currentOrderedAreas by rememberUpdatedState(orderedAreas)
                    val currentEffectiveListDisplayMode by rememberUpdatedState(effectiveListDisplayMode)
                    LaunchedEffect(normalizedOpenItems, draggingItemId, effectiveListDisplayMode) {
                        if (draggingItemId != null) {
                            deferredOpenItems = normalizedOpenItems
                        } else {
                            val incomingItems = deferredOpenItems ?: normalizedOpenItems
                            val incomingOrderIds = incomingItems.map { it.id }
                            val pendingIds = pendingCommittedOrderIds
                            val incomingIdSet = incomingOrderIds.toSet()
                            val pendingIdSet = pendingIds?.toSet()
                            val itemSetChanged = pendingIdSet != null && pendingIdSet != incomingIdSet

                            if (pendingIds == null || pendingIds == incomingOrderIds || itemSetChanged) {
                                localOpenItems = incomingItems
                                dragPreviewItems = null
                                deferredOpenItems = null
                                pendingCommittedOrderIds = null
                                dragStartItemBounds = emptyMap()
                                dragStartSlotBounds = emptyMap()
                                dragStartHeaderBounds = emptyMap()
                                dragStartItemSlotAnchors = emptyMap()
                                dragStartDropSlotsByKey = emptyMap()
                                dragStartAllowedSlotKeys = emptySet()
                                activeDropSlotKey = null
                            }
                        }
                    }
                    LaunchedEffect(effectiveListDisplayMode) {
                        draggingItemId = null
                        activeDropSlotKey = null
                        droppedItemId = null
                        dragPreviewItems = null
                        deferredOpenItems = null
                        pendingCommittedOrderIds = null
                        dragStartItemBounds = emptyMap()
                        dragStartSlotBounds = emptyMap()
                        dragStartHeaderBounds = emptyMap()
                        dragStartItemSlotAnchors = emptyMap()
                        dragStartDropSlotsByKey = emptyMap()
                        dragStartAllowedSlotKeys = emptySet()
                    }
                    LaunchedEffect(openDisplayEntries) {
                        val itemIds = openDisplayEntries
                            .mapNotNull { entry -> (entry as? OpenListEntry.ItemEntry)?.item?.id }
                            .toSet()
                        val slotKeys = openDisplayEntries
                            .mapNotNull { entry ->
                                if (entry is OpenListEntry.DropSlotEntry) entry.slot.key else null
                            }
                            .toSet()
                        val headerKeys = openDisplayEntries
                            .mapNotNull { entry ->
                                (entry as? OpenListEntry.HeaderEntry)?.area?.key
                            }
                            .toSet()
                        itemBounds.keys.retainAll(itemIds)
                        slotBounds.keys.retainAll(slotKeys)
                        headerBounds.keys.retainAll(headerKeys)
                    }
                    LaunchedEffect(droppedItemId) {
                        if (droppedItemId != null) {
                            kotlinx.coroutines.delay(650)
                            droppedItemId = null
                        }
                    }

                    LaunchedEffect(completedExpanded) {
                        if (completedExpanded && completedItems.isNotEmpty()) {
                            val headerIndex = openDisplayEntries.size
                            kotlinx.coroutines.delay(16)
                            lazyListState.animateScrollToItem(headerIndex)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords ->
                                    listTopInRoot = coords.positionInRoot().y
                                }
                        ) {

                            items(
                                items = openDisplayEntries,
                                key = { entry -> entry.stableKey }
                            ) { entry ->
                                when (entry) {
                                    is OpenListEntry.HeaderEntry -> {
                                        AreaHeader(
                                            area = entry.area,
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                val top = coords.positionInRoot().y
                                                headerBounds[entry.area.key] = HeaderBounds(
                                                    topInRoot = top,
                                                    bottomInRoot = top + coords.size.height
                                                )
                                            }
                                        )
                                    }

                                    is OpenListEntry.DropSlotEntry -> {
                                        DropSlotSpacer(
                                            active = draggingItemId != null && !entry.slot.isNoOp && activeDropSlotKey == entry.slot.key,
                                            visible = !entry.slot.isNoOp,
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                val top = coords.positionInRoot().y
                                                slotBounds[entry.slot.key] = DropSlotBounds(
                                                    topInRoot = top,
                                                    bottomInRoot = top + coords.size.height
                                                )
                                            }
                                        )
                                    }

                                    is OpenListEntry.ItemEntry -> {
                                        val item = entry.item
                                        val isJustDropped = droppedItemId == item.id
                                        val isDraggingThisItem = draggingItemId == item.id
                                        val baseContainer = MaterialTheme.colorScheme.surfaceVariant
                                        val targetContainer = when {
                                            isDraggingThisItem -> lerp(
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
                                                isDraggingThisItem -> BrandBlue
                                                isJustDropped -> BrandGreen
                                                else -> Color.Transparent
                                            },
                                            label = "dragCardBorderColor"
                                        )
                                        val borderWidth by animateDpAsState(
                                            targetValue = when {
                                                isDraggingThisItem -> 1.5.dp
                                                isJustDropped -> 1.dp
                                                else -> 0.dp
                                            },
                                            label = "dragCardBorderWidth"
                                        )
                                        val elevation by animateDpAsState(
                                            targetValue = if (isDraggingThisItem) 16.dp else 2.dp,
                                            label = "dragElevation"
                                        )
                                        val scale by animateFloatAsState(
                                            targetValue = if (isDraggingThisItem) 1.03f else 1f,
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
                                                    alpha = if (isDraggingThisItem) 0f else 1f
                                                }
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coords ->
                                                    itemBounds[item.id] = ItemDragBounds(
                                                        topInRoot = coords.positionInRoot().y,
                                                        height = coords.size.height.toFloat()
                                                    )
                                                }
                                                .then(
                                                    if (activeDropCandidateKeys.isNotEmpty()) {
                                                        Modifier.pointerInput(item.id) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { offset ->
                                                                    val bounds = itemBounds[item.id]
                                                                        ?: return@detectDragGesturesAfterLongPress
                                                                    draggingItemId = item.id
                                                                    droppedItemId = null
                                                                    pendingCommittedOrderIds = null
                                                                    dragStartItemBounds = itemBounds.toMap()
                                                                    dragStartSlotBounds = slotBounds.toMap()
                                                                    dragStartHeaderBounds = headerBounds.toMap()
                                                                    dragStartItemSlotAnchors = currentItemSlotAnchors
                                                                    dragStartDropSlotsByKey =
                                                                        currentOpenDisplayEntries
                                                                            .mapNotNull { displayEntry ->
                                                                                (displayEntry as? OpenListEntry.DropSlotEntry)?.slot
                                                                            }
                                                                            .associateBy { it.key }
                                                                    dragStartAllowedSlotKeys = currentActiveDropCandidateKeys
                                                                    dragPointerYInRoot = bounds.topInRoot + offset.y
                                                                    dragTouchOffsetY = offset.y
                                                                    val activeSlot = when (currentEffectiveListDisplayMode) {
                                                                        ShoppingListDisplayMode.SIMPLE -> resolveSimpleDropSlot(
                                                                            items = currentDisplayedOpenItems,
                                                                            draggedItemId = item.id,
                                                                            itemBounds = itemBounds,
                                                                            pointerYInRoot = dragPointerYInRoot
                                                                        )
                                                                        ShoppingListDisplayMode.CATEGORIZED -> resolveActiveDropSlotKey(
                                                                            pointerYInRoot = dragPointerYInRoot,
                                                                            draggedItemId = item.id,
                                                                            itemBounds = dragStartItemBounds,
                                                                            headerBounds = dragStartHeaderBounds,
                                                                            itemSlotAnchors = dragStartItemSlotAnchors,
                                                                            slotBounds = dragStartSlotBounds,
                                                                            allowedSlotKeys = dragStartAllowedSlotKeys
                                                                        )?.let(dragStartDropSlotsByKey::get)
                                                                    }
                                                                    activeDropSlotKey = activeSlot?.key
                                                                    dragPreviewItems = activeSlot?.let { slot ->
                                                                        applyDropSlotToItems(
                                                                            items = currentLocalOpenItems,
                                                                            itemId = item.id,
                                                                            slot = slot,
                                                                            orderedAreas = currentOrderedAreas,
                                                                            displayMode = currentEffectiveListDisplayMode
                                                                        )
                                                                    }
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragPointerYInRoot += dragAmount.y
                                                                    val activeSlot = when (currentEffectiveListDisplayMode) {
                                                                        ShoppingListDisplayMode.SIMPLE -> resolveSimpleDropSlot(
                                                                            items = currentDisplayedOpenItems,
                                                                            draggedItemId = item.id,
                                                                            itemBounds = itemBounds,
                                                                            pointerYInRoot = dragPointerYInRoot
                                                                        )
                                                                        ShoppingListDisplayMode.CATEGORIZED -> resolveActiveDropSlotKey(
                                                                            pointerYInRoot = dragPointerYInRoot,
                                                                            draggedItemId = item.id,
                                                                            itemBounds = dragStartItemBounds,
                                                                            headerBounds = dragStartHeaderBounds,
                                                                            itemSlotAnchors = dragStartItemSlotAnchors,
                                                                            slotBounds = dragStartSlotBounds,
                                                                            allowedSlotKeys = dragStartAllowedSlotKeys
                                                                        )?.let(dragStartDropSlotsByKey::get)
                                                                    }
                                                                    activeDropSlotKey = activeSlot?.key
                                                                    dragPreviewItems = activeSlot?.let { slot ->
                                                                        applyDropSlotToItems(
                                                                            items = currentLocalOpenItems,
                                                                            itemId = item.id,
                                                                            slot = slot,
                                                                            orderedAreas = currentOrderedAreas,
                                                                            displayMode = currentEffectiveListDisplayMode
                                                                        )
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    val draggedId = draggingItemId
                                                                    val activeSlot = when (currentEffectiveListDisplayMode) {
                                                                        ShoppingListDisplayMode.SIMPLE ->
                                                                            draggedId?.let { id ->
                                                                                resolveSimpleDropSlot(
                                                                                    items = currentLocalOpenItems,
                                                                                    draggedItemId = id,
                                                                                    itemBounds = itemBounds,
                                                                                    pointerYInRoot = dragPointerYInRoot
                                                                                )
                                                                            }
                                                                        ShoppingListDisplayMode.CATEGORIZED ->
                                                                            activeDropSlotKey?.let(dragStartDropSlotsByKey::get)
                                                                    }
                                                                    val draggedItem =
                                                                        currentLocalOpenItems.firstOrNull { it.id == draggedId }
                                                                    if (draggedId != null && activeSlot != null && draggedItem != null) {
                                                                        val finalPreviewItems = applyDropSlotToItems(
                                                                            items = currentLocalOpenItems,
                                                                            itemId = draggedId,
                                                                            slot = activeSlot,
                                                                            orderedAreas = currentOrderedAreas,
                                                                            displayMode = currentEffectiveListDisplayMode
                                                                        )
                                                                        val finalDraggedItem =
                                                                            finalPreviewItems.firstOrNull { it.id == draggedId }
                                                                        val finalIndex =
                                                                            finalPreviewItems.indexOfFirst { it.id == draggedId }
                                                                        val previousItemId =
                                                                            finalPreviewItems.getOrNull(finalIndex - 1)?.id
                                                                        val changed =
                                                                            finalPreviewItems.map { it.id } != currentLocalOpenItems.map { it.id } ||
                                                                                finalDraggedItem?.area != draggedItem.area
                                                                        if (changed) {
                                                                            localOpenItems = finalPreviewItems
                                                                            dragPreviewItems = null
                                                                            deferredOpenItems = finalPreviewItems
                                                                            pendingCommittedOrderIds =
                                                                                finalPreviewItems.map { it.id }
                                                                            droppedItemId = draggedId
                                                                            triggerHeaderPulse()
                                                                            viewModel.moveItem(
                                                                                itemId = draggedId,
                                                                                previousItemId = previousItemId,
                                                                                area = finalDraggedItem?.area ?: activeSlot.area
                                                                            )
                                                                        }
                                                                    }
                                                                    draggingItemId = null
                                                                    activeDropSlotKey = null
                                                                    dragPreviewItems = null
                                                                    dragStartItemBounds = emptyMap()
                                                                    dragStartSlotBounds = emptyMap()
                                                                    dragStartHeaderBounds = emptyMap()
                                                                    dragStartItemSlotAnchors = emptyMap()
                                                                    dragStartDropSlotsByKey = emptyMap()
                                                                    dragStartAllowedSlotKeys = emptySet()
                                                                },
                                                                onDragCancel = {
                                                                    draggingItemId = null
                                                                    activeDropSlotKey = null
                                                                    dragPreviewItems = null
                                                                    dragStartItemBounds = emptyMap()
                                                                    dragStartSlotBounds = emptyMap()
                                                                    dragStartHeaderBounds = emptyMap()
                                                                    dragStartItemSlotAnchors = emptyMap()
                                                                    dragStartDropSlotsByKey = emptyMap()
                                                                    dragStartAllowedSlotKeys = emptySet()
                                                                }
                                                            )
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                        ) {
                                            ShoppingRow(
                                                item = item,
                                                orderedAreas = orderedAreas,
                                                selectableAreas = enabledAreas,
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
                                                onToggleChecked = { checked ->
                                                    localOpenItems = localOpenItems.map { openItem ->
                                                        if (openItem.id == item.id) {
                                                            openItem.copy(complete = checked)
                                                        } else {
                                                            openItem
                                                        }
                                                    }
                                                },
                                                onAreaChanged = { area ->
                                                    localOpenItems = normalizeOpenItemsForDisplay(
                                                        items = localOpenItems.map { openItem ->
                                                            if (openItem.id == item.id) {
                                                                openItem.copy(area = area)
                                                            } else {
                                                                openItem
                                                            }
                                                        },
                                                        orderedAreas = orderedAreas,
                                                        displayMode = effectiveListDisplayMode
                                                    )
                                                },
                                                onAreaPersisted = { area ->
                                                    coroutineScope.launch {
                                                        productHistoryRepository.updateStoredProductArea(
                                                            name = item.name,
                                                            area = area
                                                        )
                                                    }
                                                },
                                                showAreaMenu = true
                                            )
                                        }
                                    }
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
                                            orderedAreas = orderedAreas,
                                            selectableAreas = enabledAreas,
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
                                            onToggleChecked = {},
                                            onAreaChanged = {},
                                            onAreaPersisted = {},
                                            animateVisibility = false,
                                            showAreaMenu = false,
                                            allowRename = false,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                                }
                            }
                        }

                        val dragPreviewOffsetX = with(LocalDensity.current) { 12.dp.roundToPx() }

                        val draggedItem = remember(draggingItemId, localOpenItems) {
                            localOpenItems.firstOrNull { it.id == draggingItemId }
                        }
                        if (draggedItem != null) {
                            DragPreviewCard(
                                item = draggedItem,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = dragPreviewOffsetX,
                                            y = (dragPointerYInRoot - listTopInRoot - dragTouchOffsetY)
                                                .roundToInt()
                                        )
                                    }
                                    .zIndex(2f)
                                    .fillMaxWidth()
                            )
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
    val suggestionArea = ShoppingArea.fromKey(suggestion.areaKey) ?: ShoppingArea.OTHER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = suggestionArea.emoji,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.width(10.dp))

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

@Composable
private fun AreaHeader(
    area: ShoppingArea,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = BrandOrange.copy(alpha = 0.45f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = area.label(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = BrandOrange
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = BrandOrange.copy(alpha = 0.45f)
        )
    }
}

private fun normalizeOpenItemsForAreaGrouping(
    items: List<ShoppingItem>,
    orderedAreas: List<ShoppingArea>
): List<ShoppingItem> {
    val grouped = LinkedHashMap<ShoppingArea, MutableList<ShoppingItem>>()
    orderedAreas.forEach { grouped[it] = mutableListOf() }
    items.forEach { item ->
        grouped.getValue(item.area ?: ShoppingArea.OTHER).add(item)
    }
    return grouped.values.flatten()
}

private fun normalizeOpenItemsForDisplay(
    items: List<ShoppingItem>,
    orderedAreas: List<ShoppingArea>,
    displayMode: ShoppingListDisplayMode
): List<ShoppingItem> =
    when (displayMode) {
        ShoppingListDisplayMode.CATEGORIZED -> normalizeOpenItemsForAreaGrouping(items, orderedAreas)
        ShoppingListDisplayMode.SIMPLE -> items
    }

private data class ItemDragBounds(
    val topInRoot: Float,
    val height: Float
)

private data class DropSlotBounds(
    val topInRoot: Float,
    val bottomInRoot: Float
) {
    val centerY: Float
        get() = (topInRoot + bottomInRoot) / 2f
}

private data class HeaderBounds(
    val topInRoot: Float,
    val bottomInRoot: Float
)

private data class ItemSlotAnchors(
    val beforeSlotKey: String,
    val afterSlotKey: String
)

private data class DropSlotData(
    val key: String,
    val insertBeforeItemId: String?,
    val area: ShoppingArea,
    val isNoOp: Boolean
)

private sealed interface OpenListEntry {
    val stableKey: String

    data class HeaderEntry(
        val area: ShoppingArea
    ) : OpenListEntry {
        override val stableKey: String = "header_${area.key}"
    }

    data class DropSlotEntry(
        val slot: DropSlotData
    ) : OpenListEntry {
        override val stableKey: String = slot.key
    }

    data class ItemEntry(
        val item: ShoppingItem
    ) : OpenListEntry {
        override val stableKey: String = "open_${item.id}"
    }
}

private fun buildSimpleOpenDisplayEntries(
    items: List<ShoppingItem>,
    draggingItemId: String?
): List<OpenListEntry> {
    if (items.isEmpty()) return emptyList()

    val entries = mutableListOf<OpenListEntry>()
    val startSlot = DropSlotData(
        key = "slot_simple_start",
        insertBeforeItemId = items.first().id,
        area = ShoppingArea.OTHER,
        isNoOp = isNoOpDropSlot(
            insertBeforeItemId = items.first().id,
            draggingItemId = draggingItemId,
            previousItemId = null
        )
    )
    if (!startSlot.isNoOp) {
        entries += OpenListEntry.DropSlotEntry(startSlot)
    }

    items.forEachIndexed { index, item ->
        entries += OpenListEntry.ItemEntry(item)
        val nextItemId = items.getOrNull(index + 1)?.id
        val afterSlot = DropSlotData(
            key = "slot_simple_after_${item.id}",
            insertBeforeItemId = nextItemId,
            area = ShoppingArea.OTHER,
            isNoOp = isNoOpDropSlot(
                insertBeforeItemId = nextItemId,
                draggingItemId = draggingItemId,
                previousItemId = item.id
            )
        )
        if (!afterSlot.isNoOp) {
            entries += OpenListEntry.DropSlotEntry(afterSlot)
        }
    }

    return entries
}

private fun buildOpenDisplayEntries(
    items: List<ShoppingItem>,
    activeDropSlotKey: String?,
    draggingItemId: String?,
    orderedAreas: List<ShoppingArea>
): List<OpenListEntry> {
    if (items.isEmpty()) return emptyList()

    val entries = mutableListOf<OpenListEntry>()
    var previousItemId: String? = null
    orderedAreas.forEach { area ->
        val areaItems = items.filter { (it.area ?: ShoppingArea.OTHER) == area }
        if (areaItems.isEmpty()) return@forEach

        entries += OpenListEntry.HeaderEntry(area)
        val startSlot = DropSlotData(
            key = "slot_${area.key}_start",
            insertBeforeItemId = areaItems.first().id,
            area = area,
            isNoOp = isNoOpDropSlot(
                insertBeforeItemId = areaItems.first().id,
                draggingItemId = draggingItemId,
                previousItemId = null
            )
        )
        entries += OpenListEntry.DropSlotEntry(startSlot)

        areaItems.forEachIndexed { index, item ->
            entries += OpenListEntry.ItemEntry(item)
            previousItemId = item.id
            val nextItemId = areaItems.getOrNull(index + 1)?.id
            val afterSlot = DropSlotData(
                key = "slot_after_${item.id}",
                insertBeforeItemId = nextItemId,
                area = area,
                isNoOp = isNoOpDropSlot(
                    insertBeforeItemId = nextItemId,
                    draggingItemId = draggingItemId,
                    previousItemId = item.id
                )
            )
            entries += OpenListEntry.DropSlotEntry(afterSlot)
        }
    }
    return entries
}

private fun isNoOpDropSlot(
    insertBeforeItemId: String?,
    draggingItemId: String?,
    previousItemId: String?
): Boolean {
    if (draggingItemId == null) return false
    return insertBeforeItemId == draggingItemId || previousItemId == draggingItemId
}

private fun buildItemSlotAnchors(
    entries: List<OpenListEntry>
): Map<String, ItemSlotAnchors> {
    val anchors = mutableMapOf<String, ItemSlotAnchors>()
    entries.forEachIndexed { index, entry ->
        if (entry is OpenListEntry.ItemEntry) {
            val beforeSlotKey = (entries.getOrNull(index - 1) as? OpenListEntry.DropSlotEntry)?.slot?.key
            val afterSlotKey = (entries.getOrNull(index + 1) as? OpenListEntry.DropSlotEntry)?.slot?.key
            if (beforeSlotKey != null && afterSlotKey != null) {
                anchors[entry.item.id] = ItemSlotAnchors(
                    beforeSlotKey = beforeSlotKey,
                    afterSlotKey = afterSlotKey
                )
            }
        }
    }
    return anchors
}

private fun resolveActiveDropSlotKey(
    pointerYInRoot: Float,
    draggedItemId: String,
    itemBounds: Map<String, ItemDragBounds>,
    headerBounds: Map<String, HeaderBounds>,
    itemSlotAnchors: Map<String, ItemSlotAnchors>,
    slotBounds: Map<String, DropSlotBounds>,
    allowedSlotKeys: Set<String>
): String? {
    headerBounds.entries
        .asSequence()
        .filter { (areaKey, bounds) ->
            "slot_${areaKey}_start" in allowedSlotKeys &&
                pointerYInRoot in bounds.topInRoot..bounds.bottomInRoot
        }
        .minByOrNull { (_, bounds) -> bounds.topInRoot }
        ?.let { (areaKey, _) ->
            return "slot_${areaKey}_start"
        }

    slotBounds.entries
        .asSequence()
        .filter { (key, bounds) ->
            key in allowedSlotKeys && pointerYInRoot in bounds.topInRoot..bounds.bottomInRoot
        }
        .minByOrNull { (_, bounds) -> bounds.topInRoot }
        ?.let { return it.key }

    itemBounds.entries
        .asSequence()
        .filter { (itemId, bounds) ->
            itemId != draggedItemId &&
                pointerYInRoot in bounds.topInRoot..(bounds.topInRoot + bounds.height)
        }
        .minByOrNull { (_, bounds) -> bounds.topInRoot }
        ?.let { (itemId, bounds) ->
            val anchors = itemSlotAnchors[itemId] ?: return@let
            val midpoint = bounds.topInRoot + bounds.height / 2f
            val preferredKey = if (pointerYInRoot < midpoint) anchors.beforeSlotKey else anchors.afterSlotKey
            val fallbackKey = if (pointerYInRoot < midpoint) anchors.afterSlotKey else anchors.beforeSlotKey
            return when {
                preferredKey in allowedSlotKeys -> preferredKey
                fallbackKey in allowedSlotKeys -> fallbackKey
                else -> null
            }
        }

    return slotBounds
        .filterKeys { it in allowedSlotKeys }
        .minByOrNull { entry ->
            kotlin.math.abs(entry.value.centerY - pointerYInRoot)
        }
        ?.key
}

private fun resolveSimpleDropSlot(
    items: List<ShoppingItem>,
    draggedItemId: String,
    itemBounds: Map<String, ItemDragBounds>,
    pointerYInRoot: Float
): DropSlotData? {
    val visibleItems = items.filter { it.id != draggedItemId }
    if (visibleItems.isEmpty()) {
        return null
    }

    visibleItems.forEachIndexed { index, item ->
        val bounds = itemBounds[item.id] ?: return@forEachIndexed
        val midpoint = bounds.topInRoot + bounds.height / 2f
        if (pointerYInRoot < midpoint) {
            return if (index == 0) {
                DropSlotData(
                    key = "slot_simple_start",
                    insertBeforeItemId = item.id,
                    area = ShoppingArea.OTHER,
                    isNoOp = false
                )
            } else {
                val previousItemId = visibleItems[index - 1].id
                DropSlotData(
                    key = "slot_simple_after_$previousItemId",
                    insertBeforeItemId = item.id,
                    area = ShoppingArea.OTHER,
                    isNoOp = false
                )
            }
        }
    }

    val lastItemId = visibleItems.lastOrNull()?.id ?: return null
    return DropSlotData(
        key = "slot_simple_after_$lastItemId",
        insertBeforeItemId = null,
        area = ShoppingArea.OTHER,
        isNoOp = false
    )
}

private fun applyDropSlotToItems(
    items: List<ShoppingItem>,
    itemId: String,
    slot: DropSlotData,
    orderedAreas: List<ShoppingArea>,
    displayMode: ShoppingListDisplayMode
): List<ShoppingItem> {
    val movedItem = items.firstOrNull { it.id == itemId } ?: return items
    val remainingItems = items.filterNot { it.id == itemId }.toMutableList()
    val targetIndex =
        slot.insertBeforeItemId
            ?.let { insertBeforeId ->
                remainingItems.indexOfFirst { it.id == insertBeforeId }
                    .takeIf { it >= 0 }
            }
            ?: remainingItems.size

    val currentArea = movedItem.area ?: ShoppingArea.OTHER
    val previousArea = remainingItems.getOrNull(targetIndex - 1)?.area ?: ShoppingArea.OTHER
    val nextArea = remainingItems.getOrNull(targetIndex)?.area ?: ShoppingArea.OTHER
    val effectiveArea =
        when {
            slot.area != ShoppingArea.OTHER -> slot.area
            previousArea == currentArea || nextArea == currentArea -> currentArea
            previousArea == nextArea -> previousArea
            else -> slot.area
        }

    val movedArea = if (displayMode == ShoppingListDisplayMode.SIMPLE) movedItem.area else effectiveArea

    remainingItems.add(
        targetIndex.coerceIn(0, remainingItems.size),
        movedItem.copy(area = movedArea)
    )
    return when (displayMode) {
        ShoppingListDisplayMode.CATEGORIZED -> normalizeOpenItemsForAreaGrouping(remainingItems, orderedAreas)
        ShoppingListDisplayMode.SIMPLE -> remainingItems
    }
}

@Composable
private fun DropSlotSpacer(
    active: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (visible) 14.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {}
}

@Composable
private fun DragPreviewCard(
    item: ShoppingItem,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(18.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.5.dp, BrandBlue),
        colors = CardDefaults.cardColors(
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                if (isSystemInDarkTheme()) BrandBlueGlow else BrandBlue,
                if (isSystemInDarkTheme()) 0.22f else 0.12f
            )
        ),
        modifier = modifier
            .padding(horizontal = 0.dp)
            .graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.03f
                alpha = 0.92f
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = false,
                onCheckedChange = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ActiveDropIndicator(
    centerY: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset { IntOffset(0, (centerY - 12.dp.toPx()).roundToInt()) }
            .padding(horizontal = 18.dp)
            .height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    color = BrandOrange.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.12f),
                    shape = RoundedCornerShape(999.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = BrandOrange,
                    shape = RoundedCornerShape(999.dp)
                )
        )
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ShoppingRow(
    item: ShoppingItem,
    orderedAreas: List<ShoppingArea>,
    selectableAreas: List<ShoppingArea>,
    isEditing: Boolean,
    onStartEdit: (String) -> Unit,
    onStopEdit: () -> Unit,
    viewModel: ShoppingViewModel,
    onServerInteraction: () -> Unit = {},
    onToggleChecked: (Boolean) -> Unit = {},
    onAreaChanged: (ShoppingArea) -> Unit = {},
    onAreaPersisted: (ShoppingArea) -> Unit = {},
    modifier: Modifier = Modifier,
    animateVisibility: Boolean = true,
    showAreaMenu: Boolean = true,
    allowRename: Boolean = true
) {
    var localChecked by remember(item.id) { mutableStateOf(item.complete) }
    val scope = rememberCoroutineScope()
    var editText by remember(item.id) { mutableStateOf(item.name) }
    var displayedArea by remember(item.id) { mutableStateOf(item.area ?: ShoppingArea.OTHER) }
    var pendingAreaSelection by remember(item.id) { mutableStateOf<ShoppingArea?>(null) }
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
    LaunchedEffect(item.complete) {
        localChecked = item.complete
    }
    LaunchedEffect(item.area) {
        val latestArea = item.area ?: ShoppingArea.OTHER
        if (pendingAreaSelection == null || latestArea == pendingAreaSelection) {
            displayedArea = latestArea
            if (latestArea == pendingAreaSelection) {
                pendingAreaSelection = null
            }
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
                .padding(vertical = 2.dp),
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
                    onToggleChecked(it)
                    onServerInteraction()
                    scope.launch {
                        viewModel.toggleItem(item)
                    }
                }
            )


            if (isEditing && allowRename) {

                val saveEdit = {
                    val nameChanged = editText.isNotBlank() && editText != item.name
                    if (editText.isNotBlank() && nameChanged) {
                        onServerInteraction()
                        viewModel.updateItem(item, editText, displayedArea)
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

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (allowRename) {
                                    Modifier.clickable {
                                        onStartEdit(item.id)
                                        editText = item.name
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (localChecked)
                            MaterialTheme.typography.bodyLarge.copy(
                                color = completedTextColor,
                                textDecoration = TextDecoration.LineThrough
                            )
                        else
                            MaterialTheme.typography.bodyLarge
                    )

                    if (showAreaMenu) {
                        AreaMenuButton(
                            selectedArea = displayedArea,
                            areas = selectableAreas,
                            onAreaSelected = { area ->
                                if (area != displayedArea) {
                                    pendingAreaSelection = area
                                    displayedArea = area
                                    onStopEdit()
                                    onAreaChanged(area)
                                    onServerInteraction()
                                    viewModel.updateItem(item, item.name, area)
                                    onAreaPersisted(area)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaMenuButton(
    selectedArea: ShoppingArea,
    areas: List<ShoppingArea>,
    onAreaSelected: (ShoppingArea) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                text = selectedArea.emoji,
                style = MaterialTheme.typography.titleMedium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            areas.forEach { area ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = area.emoji,
                                modifier = Modifier.width(24.dp),
                                textAlign = TextAlign.Center
                            )
                            Text(text = area.label())
                        }
                    },
                    onClick = {
                        expanded = false
                        onAreaSelected(area)
                    }
                )
            }
        }
    }
}

