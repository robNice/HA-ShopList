package de.robnice.homeshoplist.wear.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.model.todoEntityTitle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.WearableRemoteInputExtender
import android.view.inputmethod.EditorInfo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robnice.homeshoplist.R
import kotlinx.coroutines.launch
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.wear.compose.foundation.lazy.ScalingParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.scrollAway
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.compose.material.dialog.Alert
import de.robnice.homeshoplist.wear.WearViewModel
import de.robnice.homeshoplist.wear.model.AREA_EMOJIS
import de.robnice.homeshoplist.wear.model.WearAreaOption
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import kotlinx.coroutines.delay

@Composable
fun WearApp(viewModel: WearViewModel, demoAddName: String? = null) {
    val hasSettings by viewModel.hasSettings.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val listDisplayMode by viewModel.listDisplayMode.collectAsState()
    val areaOrder by viewModel.areaOrder.collectAsState()

    LaunchedEffect(hasSettings) {
        if (hasSettings) {
            while (true) {
                viewModel.refresh()
                delay(8_000)
            }
        }
    }

    // Start a bit below the first item, so the header buttons sit fully below the time text
    val headerOffsetPx = with(LocalDensity.current) { HEADER_START_OFFSET.roundToPx() }
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 1,
        initialCenterItemScrollOffset = -headerOffsetPx
    )
    val context = LocalContext.current
    val listName by viewModel.listName.collectAsState()
    val knownLists by viewModel.knownLists.collectAsState()
    var picker by remember { mutableStateOf<WearPicker?>(null) }
    // Name entered for a new item whose category is still to be chosen
    var pendingAddName by remember { mutableStateOf(demoAddName) }
    val addItemLabel = stringResource(R.string.add_item)
    val addInputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(ADD_ITEM_INPUT_KEY)
            ?.toString()
        if (!text.isNullOrBlank()) {
            // Known products keep their category; for new ones the category is chosen
            val known = viewModel.knownArea(text)
            if (known != null || viewModel.addAreaOptions().isEmpty()) {
                viewModel.addItem(text, known)
            } else {
                pendingAddName = text.trim()
            }
        }
    }

    val screenshotMode = remember { WearSettingsStore(context).isScreenshotMode() }

    CompositionLocalProvider(LocalScreenshotMode provides screenshotMode) {
    Scaffold(
        // Time text slides away while scrolling so it never overlaps list content
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        // No vignette on the store screenshots (debug-only screenshot mode)
        vignette = {
            if (!screenshotMode) Vignette(vignettePosition = VignettePosition.TopAndBottom)
        },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        // Faded house of the logo behind everything
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_app_icon_foreground_brand),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.95f),
                alpha = 0.09f
            )
        }
        if (!hasSettings) {
            SetupScreen(listState = listState)
        } else if (pendingAddName != null) {
            val name = pendingAddName.orEmpty()
            // Back cancels adding
            BackHandler { pendingAddName = null }
            CategoryGrid(
                title = name,
                options = viewModel.addAreaOptions(),
                noCategoryLabel = stringResource(R.string.no_category),
                onSelect = { key ->
                    viewModel.addItem(name, key)
                    pendingAddName = null
                }
            )
        } else if (picker != null) {
            BackHandler { picker = null }
            when (picker) {
                WearPicker.Lists -> SelectionScreen(
                    title = stringResource(R.string.pick_list),
                    options = knownLists.map { it.id to it.name },
                    selectedId = viewModel.currentEntity,
                    onSelect = { id ->
                        knownLists.firstOrNull { it.id == id }?.let(viewModel::selectList)
                        picker = null
                    }
                )
                null -> Unit
            }
        } else {
            ShoppingListScreen(
                // Switching needs at least two entries to choose from
                onListClick = if (knownLists.size >= 2) ({ picker = WearPicker.Lists }) else null,
                onAddClick = {
                    // System input picker: voice, keyboard, handwriting (device dependent), suggestions
                    addInputLauncher.launch(addItemInputIntent(addItemLabel, viewModel.addSuggestions()))
                },
                listName = listName,
                items = items,
                isLoading = isLoading,
                error = error,
                isOffline = isOffline,
                listDisplayMode = listDisplayMode,
                areaOrder = areaOrder,
                onToggle = viewModel::toggleItem,
                onClearCompleted = viewModel::clearCompleted,
                listState = listState
            )
        }
    }
    }
}

@Composable
private fun ShoppingListScreen(
    onListClick: (() -> Unit)?,
    onAddClick: () -> Unit,
    listName: String?,
    items: List<WearShoppingItem>,
    isLoading: Boolean,
    error: String?,
    isOffline: Boolean,
    listDisplayMode: String,
    areaOrder: String,
    onToggle: (WearShoppingItem) -> Unit,
    onClearCompleted: () -> Unit,
    listState: ScalingLazyListState
) {
    val unchecked = items.filter { !it.complete }
    val checked = items.filter { it.complete }
    val categorized = listDisplayMode == "categorized"
    var showConfirmDelete by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val headerOffsetPx = with(LocalDensity.current) { HEADER_START_OFFSET.roundToPx() }

    // Shown in place of the list instead of in a Dialog: on some watches (Galaxy Watch) the
    // dialog window was not removed after confirming and left a black screen.
    if (showConfirmDelete) {
        BackHandler { showConfirmDelete = false }
        Alert(
            title = { Text(stringResource(R.string.confirm_delete_title), textAlign = TextAlign.Center) },
            negativeButton = {
                Button(
                    onClick = { showConfirmDelete = false },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            positiveButton = {
                Button(onClick = {
                    onClearCompleted()
                    showConfirmDelete = false
                    // The list got shorter: go back to the start position below the header
                    coroutineScope.launch { listState.scrollToItem(1, -headerOffsetPx) }
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.delete))
                }
            }
        )
        return
    }

    ScalingLazyColumn(
        scalingParams = listScalingParams(),
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState),
        // itemIndex 0: the list may start below "first item centered", see HEADER_START_OFFSET
        autoCentering = AutoCenteringParams(itemIndex = 0),
        state = listState
    ) {
        item(key = "status_row") {
            BrandHeader(
                onListClick = onListClick,
                onAddClick = onAddClick,
                listName = listName,
                uncheckedCount = unchecked.size,
                // Spinner only while there is nothing to show yet; periodic refreshes stay invisible
                isLoading = isLoading && items.isEmpty(),
                // Offline with a cached list: show the list, not an error
                error = error ?: if (isOffline && items.isEmpty()) "offline" else null,
                isOffline = isOffline
            )
        }

        if (categorized) {
            // Sort area keys by the phone's configured order; unknown areas append at end
            val syncedOrder = areaOrder
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val itemAreaKeys = unchecked.map { it.areaKey }.distinct()
            val orderedAreaKeys: List<String?> = if (syncedOrder.isEmpty()) {
                itemAreaKeys
            } else {
                val inOrder = syncedOrder.filter { key -> itemAreaKeys.any { it == key } }
                val remaining = itemAreaKeys.filter { it == null || it !in syncedOrder }
                inOrder + remaining
            }

            orderedAreaKeys.forEach { areaKey ->
                val groupItems = unchecked.filter { it.areaKey == areaKey }

                // Show emoji header only for named areas (null = no area = no header).
                // The header belongs to the group's first item, so the list initially
                // centers on a real item instead of on a header.
                val emoji = areaKey?.let { AREA_EMOJIS[it] }
                itemsIndexed(groupItems, key = { _, item -> "unchecked_${item.id}" }) { index, item ->
                    if (index == 0 && emoji != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AreaEmojiHeader(emoji = emoji)
                            ItemChip(item = item, onToggle = { onToggle(item) })
                        }
                    } else {
                        ItemChip(item = item, onToggle = { onToggle(item) })
                    }
                }
            }
        } else {
            items(unchecked, key = { "unchecked_${it.id}" }) { item ->
                ItemChip(item = item, onToggle = { onToggle(item) })
            }
        }

        // Completed items always flat at the bottom
        items(checked, key = { "checked_${it.id}" }) { item ->
            ItemChip(item = item, onToggle = { onToggle(item) })
        }

        if (checked.isNotEmpty()) {
            item(key = "clear_button") {
                // Full-size chip (52 dp high) with label: clear tap target well above 48 dp
                Chip(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = ChipDefaults.secondaryChipColors(),
                    label = {
                        Text(
                            text = stringResource(R.string.cd_delete_completed),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(ChipDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AreaEmojiHeader(emoji: String) {
    Text(
        text = emoji,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun Modifier.rotaryScroll(listState: ScalingLazyListState): Modifier {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return this
        .focusRequester(focusRequester)
        .onRotaryScrollEvent { event ->
            coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
            true
        }
        .focusable()
}

/** List name (tap: switch list) and store (tap: switch store); the logo is only in the background. */
@Composable
private fun BrandHeader(
    onListClick: (() -> Unit)?,
    onAddClick: () -> Unit,
    listName: String?,
    uncheckedCount: Int,
    isLoading: Boolean,
    error: String?,
    isOffline: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // One row: switch list | open items | switch store. All three 48 dp (Wear OS minimum
        // touch target); the current list/store is shown (and changed) after tapping.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onListClick != null) {
                HeaderIconButton(
                    icon = Icons.Default.Checklist,
                    contentDescription = listName?.let { "${stringResource(R.string.pick_list)}: $it" }
                        ?: stringResource(R.string.pick_list),
                    onClick = onListClick
                )
            } else {
                // Placeholder keeps the count circle centered
                Spacer(Modifier.size(MIN_TOUCH_TARGET))
            }
            OpenCountCircle(uncheckedCount, isLoading, error, isOffline)
            HeaderIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_item),
                onClick = onAddClick
            )
        }
    }
}

/** Number of open items in a circle; also shows loading, error and offline state. */
@Composable
private fun OpenCountCircle(uncheckedCount: Int, isLoading: Boolean, error: String?, isOffline: Boolean) {
    val warning = Color(0xFFFFAA00)
    val description = stringResource(R.string.status_open, uncheckedCount)
    Box(
        modifier = Modifier
            .size(MIN_TOUCH_TARGET)
            .clip(CircleShape)
            .border(2.dp, if (isOffline || error != null) warning else BrandAccent, CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = if (isOffline) "$description, offline" else description
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            error != null -> Icon(
                imageVector = Icons.Default.SyncProblem,
                contentDescription = null,
                tint = warning,
                modifier = Modifier.size(22.dp)
            )
            else -> Text(
                text = uncheckedCount.toString(),
                style = MaterialTheme.typography.title3,
                color = BrandOnSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

private enum class WearPicker { Lists }

/** Debug-only screenshot mode: store screenshots without vignette and without faded list edges. */
private val LocalScreenshotMode = staticCompositionLocalOf { false }

@Composable
private fun listScalingParams(): ScalingParams =
    if (LocalScreenshotMode.current) {
        ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f)
    } else {
        ScalingLazyColumnDefaults.scalingParams()
    }

private const val ADD_ITEM_INPUT_KEY = "new_item"
// Four 48 dp buttons only fit in the middle third of a round screen, three fit almost everywhere
private const val CATEGORIES_PER_ROW = 3

/**
 * Category of a new item: round 48 dp emoji buttons, several per row, row after row.
 * The first button adds the item without category.
 */
@Composable
private fun CategoryGrid(
    title: String,
    options: List<WearAreaOption>,
    noCategoryLabel: String,
    onSelect: (String?) -> Unit
) {
    // First row in the middle, the name above it
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val cells: List<WearAreaOption?> = listOf<WearAreaOption?>(null) + options
    ScalingLazyColumn(
        scalingParams = listScalingParams(),
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(state),
        state = state,
        autoCentering = AutoCenteringParams(itemIndex = 1)
    ) {
        item(key = "title") {
            Text(
                text = title,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 4.dp, start = 24.dp, end = 24.dp)
            )
        }
        items(cells.chunked(CATEGORIES_PER_ROW)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { option ->
                    Button(
                        onClick = { onSelect(option?.key) },
                        modifier = Modifier.size(MIN_TOUCH_TARGET),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        if (option == null) {
                            Icon(Icons.Default.Close, contentDescription = noCategoryLabel, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = AREA_EMOJIS[option.key] ?: "❔",
                                fontSize = 22.sp,
                                modifier = Modifier.semantics { contentDescription = option.label }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Intent for the system input picker of Wear OS, with the most used products as suggestions. */
private fun addItemInputIntent(label: String, suggestions: List<String>): Intent {
    val builder = RemoteInput.Builder(ADD_ITEM_INPUT_KEY)
        .setLabel(label)
        .setChoices(suggestions.toTypedArray())
        .setAllowFreeFormInput(true)
    // Emojis make no sense for shopping items; "Done" instead of "Send" on the keyboard
    val input = WearableRemoteInputExtender(builder)
        .setEmojisAllowed(false)
        .setInputActionType(EditorInfo.IME_ACTION_DONE)
        .get()
        .build()
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also {
        RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(input))
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(MIN_TOUCH_TARGET),
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

/** Header text that opens a selection when tapped (with a small triangle). */
@Composable
private fun SelectableHeaderText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    onClick: (() -> Unit)?,
    clickLabel: String,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            // Real 48 dp height: Compose would enlarge the touch area anyway, but then the
            // areas of list and store row would overlap
            .heightIn(min = MIN_TOUCH_TARGET)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = clickLabel, onClick = onClick)
                        .padding(horizontal = 6.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = BrandOnSurface,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = style,
            color = BrandOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = BrandOnSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Full-screen choice of a list or store; the current one is marked. */
@Composable
private fun SelectionScreen(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val state = rememberScalingLazyListState()
    ScalingLazyColumn(
        scalingParams = listScalingParams(),
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(state),
        state = state,
        autoCentering = AutoCenteringParams()
    ) {
        item(key = "title") {
            Text(
                text = title,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 4.dp)
            )
        }
        items(options, key = { it.first }) { (id, name) ->
            val selected = id == selectedId
            Chip(
                onClick = { onSelect(id) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                label = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                icon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize)) }
                } else {
                    null
                }
            )
        }
    }
}


private val HEADER_WIDTH = 124.dp
/** Wear OS minimum touch target; the two header rows must not share their touch areas. */
private val MIN_TOUCH_TARGET = 48.dp
private val HEADER_START_OFFSET = 8.dp
private val BrandOnSurface = Color(0xFFF3EFFA)
private val BrandAccent = Color(0xFF4EA8F6)


@Composable
private fun ItemChip(item: WearShoppingItem, onToggle: () -> Unit) {
    Chip(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.chipColors(
            backgroundColor = if (item.complete) Color(0xFF222222) else MaterialTheme.colors.surface
        ),
        label = {
            Text(
                text = item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.complete) TextDecoration.LineThrough else null,
                color = if (item.complete) Color.Gray else Color.White
            )
        },
        icon = {
            Icon(
                imageVector = if (item.complete) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = stringResource(
                    if (item.complete) R.string.cd_item_done else R.string.cd_item_open
                ),
                modifier = Modifier.size(20.dp),
                tint = if (item.complete) Color.Gray else MaterialTheme.colors.primary
            )
        }
    )
}

@Composable
private fun SetupScreen(listState: ScalingLazyListState) {
    val context = LocalContext.current
    var showOpenedOnPhone by remember { mutableStateOf(false) }

    // Shown in place of the screen instead of in a Dialog (see ShoppingListScreen)
    if (showOpenedOnPhone) {
        BackHandler { showOpenedOnPhone = false }
        Confirmation(
            onTimeout = { showOpenedOnPhone = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        ) {
            Text(
                text = stringResource(R.string.continue_on_phone),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    ScalingLazyColumn(
        scalingParams = listScalingParams(),
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState),
        autoCentering = AutoCenteringParams(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "setup_icon") {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colors.primary
            )
        }
        item(key = "setup_hint") {
            Text(
                text = stringResource(R.string.setup_hint),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        item(key = "setup_open_phone") {
            Chip(
                onClick = {
                    openOnPhone(context)
                    showOpenedOnPhone = true
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = stringResource(R.string.open_on_phone),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

/**
 * Opens the app's Play Store page on the paired phone. From there the user can
 * open (or install) the phone app, which then transfers the settings.
 */
private fun openOnPhone(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse("market://details?id=${context.packageName}"))
    runCatching { RemoteActivityHelper(context).startRemoteActivity(intent) }
}
