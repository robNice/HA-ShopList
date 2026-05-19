package de.robnice.homeshoplist.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.items
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

    LaunchedEffect(hasSettings) {
        if (hasSettings) {
            while (true) {
                viewModel.refresh()
                delay(8_000)
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        if (!hasSettings) {
            SetupScreen()
        } else {
            ShoppingListScreen(
                items = items,
                isLoading = isLoading,
                error = error,
                listDisplayMode = listDisplayMode,
                onToggle = viewModel::toggleItem,
                onClearCompleted = viewModel::clearCompleted
            )
        }
    }
}

@Composable
private fun ShoppingListScreen(
    items: List<WearShoppingItem>,
    isLoading: Boolean,
    error: String?,
    listDisplayMode: String,
    onToggle: (WearShoppingItem) -> Unit,
    onClearCompleted: () -> Unit
) {
    val unchecked = items.filter { !it.complete }
    val checked = items.filter { it.complete }
    val categorized = listDisplayMode == "categorized"
    var showConfirmDelete by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        autoCentering = AutoCenteringParams(itemIndex = 0)
    ) {
        item(key = "status_row") {
            StatusRow(uncheckedCount = unchecked.size, isLoading = isLoading, error = error)
        }

        if (categorized) {
            // Preserve the order areas first appear in the list
            val orderedAreaKeys = mutableListOf<String?>()
            unchecked.forEach { if (it.areaKey !in orderedAreaKeys) orderedAreaKeys += it.areaKey }

            orderedAreaKeys.forEach { areaKey ->
                val groupItems = unchecked.filter { it.areaKey == areaKey }

                // Show emoji header only for named areas (null = no area = no header)
                val emoji = areaKey?.let { AREA_EMOJIS[it] }
                if (emoji != null) {
                    item(key = "header_$areaKey") {
                        AreaEmojiHeader(emoji = emoji)
                    }
                }

                items(groupItems, key = { "unchecked_${it.id}" }) { item ->
                    ItemChip(item = item, onToggle = { onToggle(item) })
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
                CompactButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Erledigte löschen",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    Dialog(
        showDialog = showConfirmDelete,
        onDismissRequest = { showConfirmDelete = false }
    ) {
        Alert(
            title = { Text("Erledigte löschen?") },
            negativeButton = {
                Button(
                    onClick = { showConfirmDelete = false },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                }
            },
            positiveButton = {
                Button(onClick = {
                    onClearCompleted()
                    showConfirmDelete = false
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Löschen")
                }
            }
        )
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
private fun StatusRow(uncheckedCount: Int, isLoading: Boolean, error: String?) {
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
            text = "$uncheckedCount offen",
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.complete) Color.Gray else MaterialTheme.colors.primary
            )
        }
    )
}

@Composable
private fun SetupScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colors.primary
        )
        Text(
            text = "Handy-App öffnen – Einstellungen werden automatisch übertragen.",
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
