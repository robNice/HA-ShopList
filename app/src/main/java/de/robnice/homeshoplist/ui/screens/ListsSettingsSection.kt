package de.robnice.homeshoplist.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.data.HaListAdminClient
import de.robnice.homeshoplist.data.HaServiceFactory
import de.robnice.homeshoplist.data.HaTodoListRepository
import de.robnice.homeshoplist.data.HaListAdminQueries
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.SharedPreferencesPendingSyncStore
import de.robnice.homeshoplist.data.TodoListRegistryInfo
import de.robnice.homeshoplist.model.ListMarketLinks
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingLists
import de.robnice.homeshoplist.ui.components.HeaderDropdown
import de.robnice.homeshoplist.ui.util.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AdminState { Checking, Admin, NotAdmin, Unknown }

/** One entry of the store dropdown per list; [marketId] null means "no store". */
private data class MarketOption(val marketId: String?, val name: String)

/**
 * Settings tab "Lists": remember store per list, store per list, and for admins creating
 * and deleting "Local To-do" lists in Home Assistant. Changes are saved immediately.
 */
@Composable
internal fun ListsSettingsSection(
    context: Context,
    dataStore: SettingsDataStore,
    offlineMode: Boolean,
    onHelpClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val storedUrl by dataStore.haUrl.collectAsState(initial = "")
    val storedToken by dataStore.haToken.collectAsState(initial = "")
    val currentEntity by dataStore.todoEntity.collectAsState(initial = "")
    val currentListName by dataStore.todoListName.collectAsState(initial = "")
    val knownLists by dataStore.knownLists.collectAsState(initial = emptyList())
    val links by dataStore.listMarketLinks.collectAsState(initial = emptyMap())
    val markets by dataStore.markets.collectAsState(initial = emptyList())

    var reloadKey by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var adminState by remember { mutableStateOf(AdminState.Checking) }
    var registry by remember { mutableStateOf<Map<String, TodoListRegistryInfo>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ShoppingList?>(null) }

    // Every list has a market: its own one or, without assignment, the first market
    val marketOptions = remember(markets) { markets.map { MarketOption(it.id, it.name) } }

    // REST, so it does not depend on the shopping screen's connection
    suspend fun loadLists(): List<ShoppingList>? =
        runCatching {
            withContext(Dispatchers.IO) {
                HaTodoListRepository(HaServiceFactory.create(storedUrl)).loadTodoLists(storedToken)
            }
        }.getOrNull()

    suspend fun applyLists(lists: List<ShoppingList>) {
        dataStore.applyAvailableLists(lists)
    }

    LaunchedEffect(reloadKey, storedUrl, storedToken, offlineMode) {
        if (offlineMode || storedUrl.isBlank() || storedToken.isBlank()) return@LaunchedEffect
        loading = true
        try {
            val lists = loadLists()
            if (!lists.isNullOrEmpty()) applyLists(lists)

            val info = HaListAdminQueries.probe(storedUrl, storedToken, lists.orEmpty().map { it.id })
            adminState = when (info?.isAdmin) {
                true -> AdminState.Admin
                false -> AdminState.NotAdmin
                null -> AdminState.Unknown
            }
            registry = info?.registry.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            adminState = AdminState.Unknown
        } finally {
            loading = false
        }
    }

    // --- Lists -------------------------------------------------------------------------------
    SectionHeaderWithHelp(
        text = t(R.string.lists_title),
        onHelpClick = onHelpClick
    )
    Spacer(modifier = Modifier.height(8.dp))

    val displayedLists: List<ShoppingList> = if (offlineMode) {
        listOf(ShoppingList(ListMarketLinks.OFFLINE_LIST_KEY, t(R.string.offline_list_title)))
    } else {
        ShoppingLists.withCurrent(
            knownLists,
            currentEntity.takeIf { it.isNotBlank() }
                ?.let { ShoppingList(it, currentListName.ifBlank { it }) }
        )
    }
    val currentKey = if (offlineMode) ListMarketLinks.OFFLINE_LIST_KEY else currentEntity

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (displayedLists.isEmpty()) {
                Text(
                    text = if (loading) t(R.string.loading_lists) else t(R.string.lists_none),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            displayedLists.forEachIndexed { index, list ->
                ListRow(
                    list = list,
                    isCurrent = list.id == currentKey,
                    marketOptions = marketOptions,
                    linkedMarketId = links[list.id],
                    canDelete = !offlineMode && adminState == AdminState.Admin &&
                        registry[list.id]?.isDeletable == true && !busy,
                    onMarketSelected = { option ->
                        coroutineScope.launch {
                            dataStore.setListMarketLink(list.id, option.marketId)
                        }
                    },
                    onDelete = { deleteTarget = list }
                )
                if (index < displayedLists.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (offlineMode) {
        Text(
            text = t(R.string.lists_offline_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // --- Create / delete (admins only) -------------------------------------------------------
    if (adminState == AdminState.Admin) {
        OutlinedButton(onClick = { showAddDialog = true }, enabled = !busy) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(t(R.string.list_add))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (busy || loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(t(R.string.loading_lists), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    errorText?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
    }

    Text(
        text = t(R.string.lists_admin_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    when (adminState) {
        AdminState.NotAdmin -> StatusText(t(R.string.lists_not_admin))
        AdminState.Unknown -> StatusText(t(R.string.lists_admin_unknown))
        else -> Unit
    }
    Spacer(modifier = Modifier.height(16.dp))

    if (showAddDialog) {
        AddListDialog(
            existingNames = displayedLists.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                busy = true
                errorText = null
                coroutineScope.launch {
                    try {
                        val before = loadLists().orEmpty().map { it.id }.toSet()
                        HaListAdminClient(storedUrl, storedToken).createLocalTodoList(name)
                        // The new entity appears shortly after the config entry was created
                        var created: ShoppingList? = null
                        var lists: List<ShoppingList> = emptyList()
                        for (attempt in 1..10) {
                            delay(700)
                            lists = loadLists().orEmpty()
                            created = lists.firstOrNull { it.id !in before }
                                ?: lists.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            if (created != null) break
                        }
                        if (lists.isNotEmpty()) dataStore.saveKnownLists(lists)
                        created?.let {
                            // A new list becomes the current list right away
                            dataStore.selectTodoList(it)
                        }
                        reloadKey++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorText = context.getString(R.string.list_add_failed, e.message.orEmpty())
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t(R.string.list_delete)) },
            text = { Text(stringResource(R.string.list_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    val entryId = registry[target.id]?.configEntryId ?: return@TextButton
                    busy = true
                    errorText = null
                    coroutineScope.launch {
                        try {
                            HaListAdminClient(storedUrl, storedToken).deleteConfigEntry(entryId)
                            dataStore.setListMarketLink(target.id, null)
                            SharedPreferencesPendingSyncStore(context, target.id).clearPendingChanges()
                            var lists: List<ShoppingList> = emptyList()
                            for (attempt in 1..10) {
                                delay(500)
                                lists = loadLists().orEmpty()
                                if (lists.isNotEmpty() && lists.none { it.id == target.id }) break
                            }
                            val remaining = lists.filterNot { it.id == target.id }
                            dataStore.saveKnownLists(remaining)
                            // If the current list was deleted, the first remaining list takes over
                            applyLists(remaining)
                            reloadKey++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorText = context.getString(R.string.list_delete_failed, e.message.orEmpty())
                        } finally {
                            busy = false
                        }
                    }
                }) {
                    Text(t(R.string.clear_completed_confirm_btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(t(R.string.clear_completed_confirm_btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ListRow(
    list: ShoppingList,
    isCurrent: Boolean,
    marketOptions: List<MarketOption>,
    linkedMarketId: String?,
    canDelete: Boolean,
    onMarketSelected: (MarketOption) -> Unit,
    onDelete: () -> Unit
) {
    val selected = marketOptions.firstOrNull { it.marketId == linkedMarketId } ?: marketOptions.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = list.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCurrent) {
                Text(
                    text = t(R.string.lists_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (selected != null) {
            HeaderDropdown(
                label = selected.name,
                items = marketOptions,
                itemLabel = { it.name },
                isSelected = { it.marketId == selected.marketId },
                onSelect = onMarketSelected,
                onClickLabel = stringResource(R.string.lists_store_for, list.name),
                modifier = Modifier.widthIn(max = 150.dp),
                leadingIcon = Icons.Default.Storefront,
                textStyle = MaterialTheme.typography.bodyMedium,
                fontWeight = null
            )
        }

        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("list_delete")) {
                Icon(Icons.Default.DeleteOutline, contentDescription = t(R.string.list_delete))
            }
        }
    }
}

@Composable
private fun AddListDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.list_add)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text(t(R.string.list_name)) },
                singleLine = true,
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text(t(R.string.list_name_duplicate)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty() && !duplicate) {
                Text(t(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t(R.string.clear_completed_confirm_btn_cancel))
            }
        }
    )
}
