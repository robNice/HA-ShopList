package de.robnice.homeshoplist.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import de.robnice.homeshoplist.BuildConfig
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.data.HaRuntime
import de.robnice.homeshoplist.data.HaWebSocketRepository
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.backup.BackupSection
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.data.update.AppUpdateInfo
import de.robnice.homeshoplist.data.update.AppUpdateRepository
import de.robnice.homeshoplist.data.update.InstallStartResult
import de.robnice.homeshoplist.data.update.UpdateCheckResult
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingMarket
import de.robnice.homeshoplist.model.ShoppingMarkets
import de.robnice.homeshoplist.model.ShoppingListDisplayMode
import de.robnice.homeshoplist.model.label
import de.robnice.homeshoplist.ui.navigation.Screen
import de.robnice.homeshoplist.ui.util.t
import de.robnice.homeshoplist.util.normalizeHaUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableColumn

private sealed interface MarkdownBlock {
    data class H1(val text: String) : MarkdownBlock
    data class H2(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data object Spacer : MarkdownBlock
}

// Tabs show icons only, so long translations never have to shrink; the title is the content description
private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    Connection(R.string.settings_tab_connection, Icons.Default.Link),
    Lists(R.string.settings_tab_lists, Icons.Default.Checklist),
    Markets(R.string.settings_tab_markets, Icons.Default.Storefront),
    Data(R.string.settings_tab_data, Icons.Default.Tune),
    Help(R.string.settings_tab_help, Icons.AutoMirrored.Filled.HelpOutline)
}

private enum class SettingsHelpTopic(
    val titleRes: Int,
    val textRes: Int
) {
    Notifications(R.string.help_notifications_title, R.string.help_notifications_text),
    OfflineMode(R.string.help_offline_mode_title, R.string.help_offline_mode_text),
    HaUrl(R.string.help_ha_url_title, R.string.help_ha_url_text),
    Token(R.string.help_token_title, R.string.help_token_text),
    Markets(R.string.help_markets_title, R.string.help_markets_text),
    Lists(R.string.help_lists_title, R.string.help_lists_text),
    ProductHistory(R.string.help_product_history_title, R.string.help_product_history_text),
    ListDisplayMode(R.string.help_list_display_mode_title, R.string.help_list_display_mode_text),
    Backup(R.string.help_backup_title, R.string.help_backup_text),
    Updates(R.string.help_updates_title, R.string.help_updates_text)
}

@Composable
private fun AreaOrderRow(
    area: ShoppingArea,
    enabled: Boolean,
    canDisable: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    dragHandleModifier: Modifier,
    isDragging: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = area.emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = area.label(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            enabled = !enabled || canDisable
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = t(R.string.area_order_drag_handle),
                tint = if (isDragging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * @todo: prevent copying token
 * @todo: prevent screenshots
 * @todo: prevent autofill for token field
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    context: Context = LocalContext.current
) {
    val dataStore = remember { SettingsDataStore(context) }
    val productHistoryRepository = remember(context) { ProductHistoryRepository.getInstance(context) }
    val updateRepository = remember(context) { AppUpdateRepository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    val storedUrl by dataStore.haUrl.collectAsState(initial = "")
    val storedToken by dataStore.haToken.collectAsState(initial = "")
    val storedMarkets by dataStore.markets.collectAsState(initial = null)
    val storedActiveMarket by dataStore.activeMarket.collectAsState(initial = null)
    val storedListDisplayMode by dataStore.listDisplayMode.collectAsState(initial = "categorized")
    val notificationsEnabled by dataStore.notificationsEnabled.collectAsState(initial = true)
    val storedOfflineMode by dataStore.offlineMode.collectAsState(initial = false)
    val updateLastCheckMillis by dataStore.updateLastCheckMillis.collectAsState(initial = 0L)
    val updateVersionName by dataStore.updateVersionName.collectAsState(initial = "")
    val updateTagName by dataStore.updateTagName.collectAsState(initial = "")
    val updateApkUrl by dataStore.updateApkUrl.collectAsState(initial = "")
    val updateReleaseUrl by dataStore.updateReleaseUrl.collectAsState(initial = "")
    val updateChangelog by dataStore.updateChangelog.collectAsState(initial = "")

    val updateChecksAllowed = remember(updateRepository) {
        updateRepository.isGithubUpdaterAllowed()
    }
    val availableUpdate = remember(
        updateVersionName,
        updateTagName,
        updateApkUrl,
        updateReleaseUrl,
        updateChangelog
    ) {
        if (
            updateVersionName.isNotBlank() &&
            updateTagName.isNotBlank() &&
            updateApkUrl.isNotBlank() &&
            updateReleaseUrl.isNotBlank()
        ) {
            AppUpdateInfo(
                versionName = updateVersionName,
                tagName = updateTagName,
                apkDownloadUrl = updateApkUrl,
                releaseUrl = updateReleaseUrl,
                changelog = updateChangelog
            )
        } else {
            null
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.Connection) }
    // Tabs can also be changed by swiping horizontally; the tab row follows the pager
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { SettingsTab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = SettingsTab.entries[it] }
    }
    var tokenVisible by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var offlineMode by remember { mutableStateOf(false) }
    var listDisplayMode by remember { mutableStateOf(ShoppingListDisplayMode.CATEGORIZED) }
    // All markets are edited as a draft and stored together with the Save button
    var marketsDraft by remember { mutableStateOf<List<ShoppingMarket>>(emptyList()) }
    // Only selects which market is edited here; the active market is chosen on the shopping screen
    var editingMarketId by remember { mutableStateOf("") }
    var showAddMarketDialog by remember { mutableStateOf(false) }
    var showRenameMarketDialog by remember { mutableStateOf(false) }
    var showDeleteMarketDialog by remember { mutableStateOf(false) }
    val editingMarket = marketsDraft.firstOrNull { it.id == editingMarketId } ?: marketsDraft.firstOrNull()
    val areaOrderDraft = editingMarket?.areaOrder ?: ShoppingArea.entries.toList()
    val enabledAreasDraft = editingMarket?.enabledAreas ?: ShoppingArea.entries.toList()
    var showHistoryClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUpdateChangelogDialog by remember { mutableStateOf(false) }
    var selectedHelpTopic by remember { mutableStateOf<SettingsHelpTopic?>(null) }
    var privacyText by remember { mutableStateOf("") }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    // HTTP warning: only for URLs the user typed in, not for loaded or imported values
    var urlEditedByUser by remember { mutableStateOf(false) }
    var urlFieldFocused by remember { mutableStateOf(false) }
    var confirmedHttpUrl by remember { mutableStateOf("") }
    var showInsecureUrlDialog by remember { mutableStateOf(false) }
    var saveAfterHttpDialog by remember { mutableStateOf(false) }

    fun isUnconfirmedHttpUrl(): Boolean {
        val cleanedUrl = normalizeHaUrl(url)
        return !offlineMode &&
            urlEditedByUser &&
            cleanedUrl.startsWith("http://") &&
            cleanedUrl != normalizeHaUrl(storedUrl) &&
            cleanedUrl != confirmedHttpUrl
    }

    LaunchedEffect(
        storedUrl,
        storedToken,
        storedListDisplayMode,
        storedOfflineMode
    ) {
        offlineMode = storedOfflineMode
        url = storedUrl
        urlEditedByUser = false
        token = storedToken
        listDisplayMode = ShoppingListDisplayMode.fromStorage(storedListDisplayMode)
    }

    // Also refreshes the drafts after a backup import
    LaunchedEffect(storedMarkets, storedActiveMarket?.id) {
        val markets = storedMarkets ?: return@LaunchedEffect
        marketsDraft = markets
        editingMarketId = storedActiveMarket?.id ?: markets.first().id
    }

    fun updateEditingMarket(transform: (ShoppingMarket) -> ShoppingMarket) {
        val id = marketsDraft.firstOrNull { it.id == editingMarketId }?.id
            ?: marketsDraft.firstOrNull()?.id
            ?: return
        marketsDraft = ShoppingMarkets.update(marketsDraft, id, transform)
    }

    LaunchedEffect(Unit) {
        val assetName = if (java.util.Locale.getDefault().language == "de") {
            "privacy_policy_de.md"
        } else {
            "privacy_policy_en.md"
        }
        privacyText = context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun checkForUpdates(manual: Boolean) {
        if (!updateChecksAllowed || updateChecking) {
            return
        }

        coroutineScope.launch {
            updateChecking = true
            if (manual) {
                updateStatusText = context.getString(R.string.update_checking)
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
                        updateStatusText = context.getString(
                            R.string.update_available_status,
                            result.update.versionName
                        )
                    }

                    UpdateCheckResult.UpToDate -> {
                        dataStore.clearAvailableUpdate()
                        updateStatusText = context.getString(R.string.update_up_to_date)
                    }
                }

                dataStore.saveUpdateCheckTimestamp(System.currentTimeMillis())
            } catch (e: Exception) {
                if (manual) {
                    updateStatusText = context.getString(
                        R.string.update_check_failed,
                        e.message ?: context.getString(R.string.loading_failed)
                    )
                }
            } finally {
                updateChecking = false
            }
        }
    }

    LaunchedEffect(updateChecksAllowed, updateLastCheckMillis) {
        if (!updateChecksAllowed || updateChecking) {
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        if (now - updateLastCheckMillis >= AppUpdateRepository.UPDATE_CHECK_INTERVAL_MILLIS) {
            checkForUpdates(manual = false)
        }
    }

    fun saveSettings() {
        val cleanedUrl = normalizeHaUrl(url)
        val cleanedToken = token.trim()

        coroutineScope.launch {
            dataStore.setOfflineMode(offlineMode)
            dataStore.saveHaUrl(cleanedUrl)
            dataStore.saveHaToken(cleanedToken)
            if (marketsDraft.isNotEmpty()) {
                dataStore.saveMarkets(marketsDraft)
            }
            dataStore.saveListDisplayMode(listDisplayMode.storageValue)
            // The watch gets the saved values via WearSettingsPublisher.publishOnChange
            navController.navigate(Screen.Shopping.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
        ) {
            Text(
                text = t(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = pagerState.currentPage == tab.ordinal,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                        // Stable ids for the screenshot tools (store/tools), independent of the language
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        icon = { Icon(tab.icon, contentDescription = t(tab.titleRes)) }
                    )
                }
            }

            // One page per tab, each with its own scroll position
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                val pageTab = SettingsTab.entries[page]
                val pageScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollState)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (pageTab == SettingsTab.Connection) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingLabelWithHelp(
                                text = t(R.string.offline_mode),
                                onHelpClick = { selectedHelpTopic = SettingsHelpTopic.OfflineMode },
                                modifier = Modifier.weight(1f)
                            )

                            Switch(
                                checked = offlineMode,
                                onCheckedChange = { offlineMode = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (pageTab == SettingsTab.Connection && !offlineMode) {
                        SettingFieldHeader(
                            text = t(R.string.ha_url),
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.HaUrl }
                        )

                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                urlEditedByUser = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    val lostFocus = urlFieldFocused && !focusState.isFocused
                                    urlFieldFocused = focusState.isFocused
                                    if (lostFocus && isUnconfirmedHttpUrl()) {
                                        showInsecureUrlDialog = true
                                    }
                                },
                            colors = settingsTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingFieldHeader(
                            text = t(R.string.ha_token),
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Token }
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (tokenVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                val image = if (tokenVisible) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                }

                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = image,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = settingsTextFieldColors()
                        )


                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (pageTab == SettingsTab.Lists) {
                        // Display mode is part of the draft and saved with the Save button
                        SettingFieldHeader(
                            text = t(R.string.list_display_mode_label),
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.ListDisplayMode }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = listDisplayMode == ShoppingListDisplayMode.SIMPLE,
                                onClick = { listDisplayMode = ShoppingListDisplayMode.SIMPLE },
                                label = { Text(t(R.string.list_display_mode_simple)) }
                            )
                            FilterChip(
                                selected = listDisplayMode == ShoppingListDisplayMode.CATEGORIZED,
                                onClick = { listDisplayMode = ShoppingListDisplayMode.CATEGORIZED },
                                label = { Text(t(R.string.list_display_mode_categorized)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        ListsSettingsSection(
                            context = context,
                            dataStore = dataStore,
                            offlineMode = storedOfflineMode,
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Lists }
                        )
                    }

                    if (pageTab == SettingsTab.Help) {
                        // The table of contents scrolls this page to the chapters
                        HelpSection(scrollState = pageScrollState)
                    }

                    if (pageTab == SettingsTab.Markets) {
                        SectionHeaderWithHelp(
                            text = t(R.string.area_order_title),
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Markets }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = t(R.string.area_order_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        editingMarket?.let { market ->
                            MarketSelectorRow(
                                markets = marketsDraft,
                                selected = market,
                                onSelect = { editingMarketId = it.id },
                                onAdd = { showAddMarketDialog = true },
                                onRename = { showRenameMarketDialog = true },
                                onDelete = { showDeleteMarketDialog = true }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            ReorderableColumn(
                                list = areaOrderDraft,
                                onSettle = { fromIndex, toIndex ->
                                    updateEditingMarket { market ->
                                        market.copy(
                                            areaOrder = market.areaOrder.toMutableList().apply {
                                                add(toIndex, removeAt(fromIndex))
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) { index, area, isDragging ->
                                ReorderableItem(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        AreaOrderRow(
                                            area = area,
                                            enabled = area in enabledAreasDraft,
                                            canDisable = enabledAreasDraft.size > 1,
                                            onEnabledChanged = { isEnabled ->
                                                updateEditingMarket { market ->
                                                    market.copy(
                                                        enabledAreas = if (isEnabled) {
                                                            market.areaOrder.filter {
                                                                it in market.enabledAreas || it == area
                                                            }
                                                        } else {
                                                            market.enabledAreas.filterNot { it == area }
                                                        }
                                                    )
                                                }
                                            },
                                            dragHandleModifier = Modifier.draggableHandle(),
                                            isDragging = isDragging
                                        )

                                        if (index < areaOrderDraft.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                // Resets only the market being edited
                                updateEditingMarket { market ->
                                    market.copy(
                                        areaOrder = ShoppingArea.entries.toList(),
                                        enabledAreas = ShoppingArea.enabledByDefault
                                    )
                                }
                            }
                        ) {
                            Text(t(R.string.area_order_reset))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (pageTab == SettingsTab.Data) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingLabelWithHelp(
                                text = t(R.string.notifications_enabled),
                                onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Notifications },
                                modifier = Modifier.weight(1f),
                                enabled = !offlineMode
                            )

                            // The offline list never changes remotely, so there is nothing to notify about
                            Switch(
                                checked = notificationsEnabled && !offlineMode,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        dataStore.setNotificationsEnabled(it)
                                    }
                                },
                                enabled = !offlineMode
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        SectionHeaderWithHelp(
                            text = t(R.string.product_history_title),
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.ProductHistory }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = t(R.string.product_history_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(onClick = { showHistoryClearDialog = true }) {
                            ButtonIconLabel(Icons.Default.DeleteOutline, t(R.string.product_history_clear))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsBackupSection(
                            context = context,
                            onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Backup },
                            onImported = { payload, importedSections ->
                                if (BackupSection.CONNECTION in importedSections) {
                                    payload.connection?.let { importedConnection ->
                                        url = importedConnection.url
                                        urlEditedByUser = false
                                        token = importedConnection.token
                                    }
                                }

                                // Imported markets reach the drafts via storedMarkets
                            }
                        )

                        if (updateChecksAllowed) {
                            Spacer(modifier = Modifier.height(16.dp))

                            SectionHeaderWithHelp(
                                text = t(R.string.update_section_title),
                                onHelpClick = { selectedHelpTopic = SettingsHelpTopic.Updates }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            availableUpdate?.let { update ->
                                Text(
                                    text = context.getString(
                                        R.string.update_available_status,
                                        update.versionName
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (update.changelog.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))

                                    TextButton(
                                        onClick = { showUpdateChangelogDialog = true },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(t(R.string.update_show_changelog))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        val activity = context as? Activity
                                        if (activity == null) {
                                            updateStatusText = context.getString(R.string.update_install_failed_no_activity)
                                            return@Button
                                        }

                                        coroutineScope.launch {
                                            updateDownloading = true
                                            updateStatusText = context.getString(R.string.update_downloading)

                                            try {
                                                val apkFile = updateRepository.downloadApk(update)
                                                val result = updateRepository.startInstall(activity, apkFile)
                                                updateStatusText = when (result) {
                                                    InstallStartResult.Started ->
                                                        context.getString(R.string.update_installer_started)
                                                    InstallStartResult.NeedsInstallPermission ->
                                                        context.getString(R.string.update_install_permission_required)
                                                }
                                            } catch (e: Exception) {
                                                updateStatusText = context.getString(
                                                    R.string.update_download_failed,
                                                    e.message ?: context.getString(R.string.loading_failed)
                                                )
                                            } finally {
                                                updateDownloading = false
                                            }
                                        }
                                    },
                                    enabled = !updateChecking && !updateDownloading
                                ) {
                                    Text(
                                        if (updateDownloading) {
                                            t(R.string.update_downloading)
                                        } else {
                                            t(R.string.update_install_button)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedButton(
                                onClick = { checkForUpdates(manual = true) },
                                enabled = !updateChecking && !updateDownloading
                            ) {
                                ButtonIconLabel(
                                    Icons.Default.Refresh,
                                    if (updateChecking) {
                                        t(R.string.update_checking)
                                    } else {
                                        t(R.string.update_check_button)
                                    }
                                )
                            }

                            updateStatusText?.let { status ->
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            if (storedOfflineMode || (storedUrl.isNotBlank() && storedToken.isNotBlank())) {
                                navController.navigate(Screen.Shopping.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                ) {
                    ButtonIconLabel(Icons.Default.Close, t(R.string.clear_completed_confirm_btn_cancel))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isUnconfirmedHttpUrl()) {
                            saveAfterHttpDialog = true
                            showInsecureUrlDialog = true
                        } else {
                            saveSettings()
                        }
                    }
                ) {
                    ButtonIconLabel(Icons.Default.Check, t(R.string.save))
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showPrivacyDialog = true },
                    modifier = Modifier.requiredHeightIn(min = 24.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(t(R.string.privacy_policy))
                }

                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/robnice"))
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                    modifier = Modifier.requiredHeightIn(min = 24.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kofi),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(t(R.string.support_kofi))
                }

                Text(
                    text = "v${BuildConfig.VERSION_NAME.removeSuffix("-mobile")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }

    editingMarket?.let { market ->
        if (showAddMarketDialog) {
            AddMarketDialog(
                markets = marketsDraft,
                initialTemplate = market,
                onDismiss = { showAddMarketDialog = false },
                onConfirm = { name, templateId ->
                    val newId = ShoppingMarkets.newId()
                    marketsDraft = ShoppingMarkets.add(marketsDraft, name, templateId, newId)
                    editingMarketId = newId
                    showAddMarketDialog = false
                }
            )
        }

        if (showRenameMarketDialog) {
            RenameMarketDialog(
                market = market,
                markets = marketsDraft,
                onDismiss = { showRenameMarketDialog = false },
                onConfirm = { name ->
                    marketsDraft = ShoppingMarkets.rename(marketsDraft, market.id, name)
                    showRenameMarketDialog = false
                }
            )
        }

        if (showDeleteMarketDialog && marketsDraft.size > 1) {
            DeleteMarketDialog(
                market = market,
                onDismiss = { showDeleteMarketDialog = false },
                onConfirm = {
                    marketsDraft = ShoppingMarkets.delete(marketsDraft, market.id)
                    editingMarketId = marketsDraft.first().id
                    showDeleteMarketDialog = false
                }
            )
        }
    }

    if (showHistoryClearDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryClearDialog = false },
            title = { Text(t(R.string.product_history_clear_title)) },
            text = { Text(t(R.string.product_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHistoryClearDialog = false
                        coroutineScope.launch {
                            productHistoryRepository.clearAll()
                        }
                    }
                ) {
                    Text(t(R.string.product_history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryClearDialog = false }) {
                    Text(t(R.string.clear_completed_confirm_btn_cancel))
                }
            }
        )
    }

    if (showInsecureUrlDialog) {
        AlertDialog(
            // Only the buttons close this dialog, so the user has to decide
            onDismissRequest = {},
            title = { Text(t(R.string.insecure_url_title)) },
            text = { Text(t(R.string.insecure_url_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmedHttpUrl = normalizeHaUrl(url)
                        showInsecureUrlDialog = false
                        if (saveAfterHttpDialog) {
                            saveAfterHttpDialog = false
                            saveSettings()
                        }
                    }
                ) {
                    Text(t(R.string.insecure_url_keep_http))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        url = "https://" + url.trim().removePrefix("http://")
                        showInsecureUrlDialog = false
                        if (saveAfterHttpDialog) {
                            saveAfterHttpDialog = false
                            saveSettings()
                        }
                    }
                ) {
                    Text(t(R.string.insecure_url_use_https))
                }
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            markdown = privacyText,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showUpdateChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateChangelogDialog = false },
            title = {
                Text(
                    context.getString(
                        R.string.update_changelog_title,
                        availableUpdate?.versionName.orEmpty()
                    )
                )
            },
            text = {
                Text(
                    text = availableUpdate?.changelog
                        ?.takeIf { it.isNotBlank() }
                        ?: t(R.string.update_changelog_empty),
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpdateChangelogDialog = false }) {
                    Text(t(R.string.close))
                }
            }
        )
    }

    selectedHelpTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { selectedHelpTopic = null },
            title = { Text(t(topic.titleRes)) },
            text = { Text(t(topic.textRes)) },
            confirmButton = {
                TextButton(onClick = { selectedHelpTopic = null }) {
                    Text(t(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SettingFieldHeader(
    text: String,
    onHelpClick: () -> Unit
) {
    SettingLabelWithHelp(
        text = text,
        onHelpClick = onHelpClick,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
internal fun SettingLabelWithHelp(
    text: String,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = t(R.string.help_link_label)
            )
        }
    }
}

/** Button content with a leading icon, following the Material button spec. */
@Composable
internal fun ButtonIconLabel(icon: ImageVector, text: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text)
}

@Composable
internal fun SectionHeaderWithHelp(
    text: String,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = t(R.string.help_link_label)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun PrivacyPolicyDialog(
    markdown: String,
    onDismiss: () -> Unit
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = t(R.string.privacy_policy),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blocks.size) { index ->
                            MarkdownBlockView(block = blocks[index])
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(t(R.string.close))
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.H1 -> Text(
            text = block.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        is MarkdownBlock.H2 -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        is MarkdownBlock.Bullet -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "\u2022",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is MarkdownBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        MarkdownBlock.Spacer -> Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    return markdown.lines().map { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> MarkdownBlock.Spacer
            line.startsWith("# ") -> MarkdownBlock.H1(line.removePrefix("# "))
            line.startsWith("## ") -> MarkdownBlock.H2(line.removePrefix("## "))
            line.startsWith("### ") -> MarkdownBlock.H2(line.removePrefix("### "))
            line.startsWith("- ") -> MarkdownBlock.Bullet(line.removePrefix("- "))
            else -> MarkdownBlock.Paragraph(line)
        }
    }
}
