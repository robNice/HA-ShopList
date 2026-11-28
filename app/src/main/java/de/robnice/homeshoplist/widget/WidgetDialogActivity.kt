package de.robnice.homeshoplist.widget

import android.content.Intent
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.clip
import de.robnice.homeshoplist.AreaMenuButton
import de.robnice.homeshoplist.HistorySuggestionRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.robnice.homeshoplist.MainActivity
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.data.HaWebSocketRepository
import de.robnice.homeshoplist.data.ListConfig
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.ShoppingRepository
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingLists
import de.robnice.homeshoplist.model.label
import de.robnice.homeshoplist.ui.theme.HomeAsssistantShoppingListTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Small dialog opened from a widget: widgets cannot hold a text field or a menu,
 * so typing an item, choosing the category and switching the list happen here.
 */
class WidgetDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetUpdater.ensureStarted(applicationContext)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ADD
        // Full screen window, so the screen coordinates of the widget match the window and the
        // keyboard arrives as inset instead of resizing the window
        enableEdgeToEdge()
        // Bounds of the tapped widget view on screen (set by the launcher); the dialog opens right there
        val anchor = intent.sourceBounds
        setContent {
            HomeAsssistantShoppingListTheme {
                CompositionLocalProvider(LocalAnchor provides anchor) {
                    when (mode) {
                        MODE_AREA -> AreaDialog(onClose = ::finish)
                        MODE_LIST -> ListDialog(onClose = ::finish)
                        MODE_CLEAR_COMPLETED -> ClearCompletedDialog(onClose = ::finish)
                        else -> AddItemDialog(onClose = ::finish, onOpenApp = ::openApp)
                    }
                }
            }
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_MODE = "widget_dialog_mode"
        const val MODE_ADD = "add"
        const val MODE_AREA = "area"
        const val MODE_LIST = "list"
        const val MODE_CLEAR_COMPLETED = "clear_completed"
    }
}

private val LocalAnchor = staticCompositionLocalOf<Rect?> { null }

/**
 * Dialog frame. Opened from a widget it sits on the tapped widget view (see [LocalAnchor]):
 * with [alignContentToAnchor] the first content row is centred on it (input field over the
 * widget's input bar), otherwise the dialog starts at its top. It moves up for the keyboard.
 * Without anchor it is a normal centred dialog.
 */
@Composable
private fun DialogCard(
    title: String?,
    onClose: () -> Unit,
    alignContentToAnchor: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val anchor = LocalAnchor.current
    val padding = if (anchor != null) 16.dp else 20.dp
    val card: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = if (anchor != null) 8.dp else 0.dp
        ) {
            Column(modifier = Modifier.padding(padding)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.size(12.dp))
                }
                content()
            }
        }
    }

    if (anchor == null) {
        Dialog(onDismissRequest = onClose) { card() }
        return
    }

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    )
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)
    Layout(content = card, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val maxWidth = constraints.maxWidth - 2 * margin
        val width = anchor.width().coerceIn(minOf(320.dp.roundToPx(), maxWidth), maxWidth)
        val bottomLimit = constraints.maxHeight - maxOf(imeBottom, navBottom) - margin
        val topLimit = statusTop + margin
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                maxHeight = (bottomLimit - topLimit).coerceAtLeast(0)
            )
        )
        val x = anchor.left.coerceIn(margin, (constraints.maxWidth - width - margin).coerceAtLeast(margin))
        val desiredY = if (alignContentToAnchor) {
            // Centre of the first row (input field, 56dp) on the centre of the widget view
            anchor.centerY() - padding.roundToPx() - 28.dp.roundToPx()
        } else {
            anchor.top
        }
        val y = desiredY.coerceAtMost(bottomLimit - placeable.height).coerceAtLeast(topLimit)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(x, y)
        }
    }
}

/** Selected category for new items: the last used one, if it is enabled in the store of the list. */
private fun resolveArea(lastUsed: String?, enabled: List<ShoppingArea>): ShoppingArea =
    ShoppingArea.fromKey(lastUsed)?.takeIf { it in enabled } ?: enabled.firstOrNull() ?: ShoppingArea.OTHER

@Composable
private fun AddItemDialog(onClose: () -> Unit, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context.applicationContext) }
    val history = remember { ProductHistoryRepository.getInstance(context.applicationContext) }
    val config by remember { ListConfig.observe(dataStore) }.collectAsState(initial = null)
    val market by dataStore.activeMarket.collectAsState(initial = null)
    val lastUsedArea by dataStore.lastUsedArea.collectAsState(initial = null)

    var repo by remember { mutableStateOf<ShoppingRepository?>(null) }
    var repoChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repo = WidgetConnection.connect(context)
        repoChecked = true
    }

    val enabledAreas = market?.enabledAreas.orEmpty()
    var selectedArea by remember { mutableStateOf<ShoppingArea?>(null) }
    LaunchedEffect(market, lastUsedArea) {
        if (selectedArea == null && market != null && lastUsedArea != null) {
            selectedArea = resolveArea(lastUsedArea, enabledAreas)
        }
    }

    val currentItems by remember(repo) { repo?.items ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    val suggestions by remember(text) {
        if (text.isBlank()) flowOf(emptyList()) else history.observeSuggestions(text, limit = 5)
    }.collectAsState(initial = emptyList())
    val focusRequester = remember { FocusRequester() }

    fun add(name: String, area: ShoppingArea) {
        val target = repo ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        target.addItem(trimmed, area)
        selectedArea = area
        lastAdded = trimmed
        text = ""
        scope.launch {
            dataStore.saveLastUsedArea(area.key)
            // Redraw now, while the app is still in the foreground
            WidgetUpdater.updateAll(context)
        }
    }

    val listTitle = config?.let { WidgetDataSource.listTitle(context, it) }.orEmpty()
    val notConfigured = repoChecked && repo == null
    DialogCard(
        title = if (notConfigured) stringResource(R.string.app_name) else null,
        onClose = onClose,
        alignContentToAnchor = !notConfigured
    ) {
        if (notConfigured) {
            Text(stringResource(R.string.widget_not_configured), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                Button(onClick = onOpenApp) { Text(stringResource(R.string.widget_open_app)) }
            }
            return@DialogCard
        }

        val area = selectedArea ?: resolveArea(lastUsedArea, enabledAreas)
        // Same input row as in the app: field, category, add button
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    val hint = stringResource(R.string.new_item)
                    Text(
                        if (listTitle.isNotBlank()) "$hint · $listTitle" else hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { add(text, area) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            AreaMenuButton(
                selectedArea = area,
                areas = enabledAreas,
                onAreaSelected = { selectedArea = it }
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { add(text, area) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        if (text.isNotBlank() && suggestions.isNotEmpty()) {
            val currentItemNames = currentItems.map { ProductHistoryRepository.normalizeName(it.name) }.toSet()
            Spacer(Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    HistorySuggestionRow(
                        suggestion = suggestion,
                        canDelete = suggestion.normalizedName !in currentItemNames,
                        onSelect = {
                            add(suggestion.displayName, ShoppingArea.fromKey(suggestion.areaKey) ?: area)
                        },
                        onDelete = {
                            scope.launch { history.deleteProduct(suggestion.normalizedName) }
                        }
                    )
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        lastAdded?.let {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.widget_added, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun AreaDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context.applicationContext) }
    val market by dataStore.activeMarket.collectAsState(initial = null)
    val lastUsedArea by dataStore.lastUsedArea.collectAsState(initial = null)
    val areas = market?.enabledAreas.orEmpty()
    val selected = resolveArea(lastUsedArea, areas)

    DialogCard(title = stringResource(R.string.widget_pick_area), onClose = onClose) {
        if (market == null || lastUsedArea == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            return@DialogCard
        }
        LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
            items(areas, key = { it.key }) { area ->
                ChoiceRow(
                    text = "${area.emoji}  ${area.label()}",
                    selected = area == selected,
                    onClick = {
                        scope.launch {
                            dataStore.saveLastUsedArea(area.key)
                            WidgetUpdater.updateAll(context)
                            onClose()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ListDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { SettingsDataStore(context.applicationContext) }
    val config by remember { ListConfig.observe(dataStore) }.collectAsState(initial = null)
    val knownLists by dataStore.knownLists.collectAsState(initial = emptyList())
    var refreshing by remember { mutableStateOf(true) }
    var switchingTo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Show the cached lists right away and refresh them from Home Assistant
        val repo = WidgetConnection.connect(context) as? HaWebSocketRepository
        val loaded = repo?.loadAvailableLists(timeoutMillis = 10_000L)
        if (!loaded.isNullOrEmpty()) dataStore.applyAvailableLists(loaded)
        refreshing = false
    }

    DialogCard(title = stringResource(R.string.list_switch), onClose = onClose) {
        val current = config ?: run {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            return@DialogCard
        }
        if (current.offlineMode) {
            // The offline list is the only list in this mode
            ChoiceRow(text = stringResource(R.string.offline_list_title), selected = true, onClick = onClose)
            return@DialogCard
        }
        val lists = ShoppingLists.withCurrent(
            knownLists,
            current.todoEntity.takeIf { it.isNotBlank() }
                ?.let { ShoppingList(it, WidgetDataSource.listTitle(context, current)) }
        )
        if (refreshing && lists.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
            items(lists, key = { it.id }) { list ->
                ChoiceRow(
                    text = list.name,
                    selected = (switchingTo ?: current.todoEntity) == list.id,
                    loading = switchingTo == list.id,
                    onClick = {
                        if (switchingTo != null) return@ChoiceRow
                        if (list.id == current.todoEntity) {
                            onClose()
                            return@ChoiceRow
                        }
                        switchingTo = list.id
                        scope.launch {
                            dataStore.selectTodoList(list)
                            // Load the new list while this dialog is still visible: once the app is in
                            // the background, some devices (e.g. Samsung) cut its network and freeze it
                            WidgetConnection.connectLoaded(context, timeoutMillis = 10_000L)
                            WidgetUpdater.updateAll(context)
                            onClose()
                        }
                    }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun ClearCompletedDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.clear_completed_confirm_title)) },
        text = { Text(stringResource(R.string.clear_completed_confirm_msg)) },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        // Needs the loaded list, otherwise there is nothing to clear yet
                        WidgetConnection.connectLoaded(context)?.clearCompleted()
                        // Redraw now, while the app is still in the foreground
                        WidgetUpdater.updateAll(context)
                        onClose()
                    }
                }
            ) { Text(stringResource(R.string.clear_completed_confirm_btn_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.clear_completed_confirm_btn_cancel)) }
        }
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
    }
}
