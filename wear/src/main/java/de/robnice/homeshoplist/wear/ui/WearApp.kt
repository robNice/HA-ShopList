package de.robnice.homeshoplist.wear.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SyncProblem
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
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.scrollAway
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.compose.material.dialog.Alert
import de.robnice.homeshoplist.wear.WearViewModel
import de.robnice.homeshoplist.wear.model.AREA_EMOJIS
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import kotlinx.coroutines.delay

@Composable
fun WearApp(viewModel: WearViewModel) {
    val hasSettings by viewModel.hasSettings.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
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

    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val listName = remember(hasSettings) {
        WearSettingsStore(context).getSettings()?.entity?.let(::todoEntityTitle)
    }

    Scaffold(
        // Time text slides away while scrolling so it never overlaps list content
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (!hasSettings) {
            SetupScreen(listState = listState)
        } else {
            ShoppingListScreen(
                listName = listName,
                items = items,
                isLoading = isLoading,
                error = error,
                listDisplayMode = listDisplayMode,
                areaOrder = areaOrder,
                onToggle = viewModel::toggleItem,
                onClearCompleted = viewModel::clearCompleted,
                listState = listState
            )
        }
    }
}

@Composable
private fun ShoppingListScreen(
    listName: String?,
    items: List<WearShoppingItem>,
    isLoading: Boolean,
    error: String?,
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
                    coroutineScope.launch { listState.scrollToItem(1) }
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.delete))
                }
            }
        )
        return
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScroll(listState),
        autoCentering = AutoCenteringParams(),
        state = listState
    ) {
        item(key = "status_row") {
            BrandHeader(listName = listName, uncheckedCount = unchecked.size, isLoading = isLoading, error = error)
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

/** Same as the phone app's top bar: ShopList wordmark, colored status line with cart, list name. */
@Composable
private fun BrandHeader(listName: String?, uncheckedCount: Int, isLoading: Boolean, error: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.brand_wordmark),
            contentDescription = "ShopList",
            modifier = Modifier.width(HEADER_WIDTH).aspectRatio(600f / 168f)
        )
        Row(
            modifier = Modifier.width(HEADER_WIDTH),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandStatusLine(modifier = Modifier.weight(1f).height(4.dp))
            Spacer(Modifier.width(2.dp))
            Image(
                painter = painterResource(R.drawable.brand_cart),
                contentDescription = null,
                modifier = Modifier.width(22.dp).aspectRatio(100f / 58f)
            )
        }
        if (listName != null) {
            Text(
                text = listName,
                style = MaterialTheme.typography.title3,
                color = BrandOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(modifier = Modifier.padding(top = 2.dp)) {
            StatusRowContent(uncheckedCount, isLoading, error)
        }
    }
}

/** Blue track with orange and green segments, like the phone header. */
@Composable
private fun BrandStatusLine(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val y = size.height / 2
        val stroke = size.height
        var x = 0f
        BRAND_LINE_SEGMENTS.forEach { (fraction, color) ->
            val end = x + size.width * fraction
            drawLine(color, Offset(x + stroke / 2, y), Offset(end - stroke / 2, y), stroke, StrokeCap.Round)
            x = end
        }
    }
}

private val HEADER_WIDTH = 124.dp
private val BrandOnSurface = Color(0xFFF3EFFA)
private val BRAND_LINE_SEGMENTS = listOf(
    0.08f to Color(0x574EA8F6),
    0.22f to Color(0xFFFF9E1A),
    0.06f to Color(0x574EA8F6),
    0.10f to Color(0xFF34C759),
    0.20f to Color(0x574EA8F6),
    0.22f to Color(0xFFFF9E1A),
    0.12f to Color(0x574EA8F6)
)

@Composable
private fun StatusRowContent(uncheckedCount: Int, isLoading: Boolean, error: String?) {
    when {
        isLoading && uncheckedCount == 0 -> CircularProgressIndicator(
            modifier = Modifier
                .padding(4.dp)
                .size(20.dp)
        )
        error != null -> Icon(
            imageVector = Icons.Default.SyncProblem,
            contentDescription = null,
            tint = Color(0xFFFFAA00),
            modifier = Modifier.size(20.dp)
        )
        else -> Text(
            text = stringResource(R.string.status_open, uncheckedCount),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center
        )
    }
}

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
