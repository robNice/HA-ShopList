package de.robnice.homeshoplist.wear

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import androidx.wear.tiles.TileService
import de.robnice.homeshoplist.wear.data.HaWearClient
import de.robnice.homeshoplist.wear.data.WearListSync
import de.robnice.homeshoplist.wear.tile.TileRefreshWorker
import de.robnice.homeshoplist.wear.model.WearAreaOption
import de.robnice.homeshoplist.wear.model.WearListOption
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import de.robnice.homeshoplist.wear.model.todoEntityTitle
import de.robnice.homeshoplist.wear.tile.ShoppingTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WearViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** Must match WearSettingsPublisher on the phone. */
        const val SELECT_LIST_PATH = "/select_list"
        private const val MAX_ADD_SUGGESTIONS = 10
    }

    private val settingsStore = WearSettingsStore(app)

    private val _items = MutableStateFlow<List<WearShoppingItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _hasSettings = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isOffline = MutableStateFlow(false)
    private val _listDisplayMode = MutableStateFlow(settingsStore.getDisplayMode())
    private val _areaOrder = MutableStateFlow(settingsStore.getAreaOrder())
    private val _listName = MutableStateFlow(currentListName())
    private val _knownLists = MutableStateFlow(settingsStore.getKnownLists())

    val items = _items.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val hasSettings = _hasSettings.asStateFlow()
    val error = _error.asStateFlow()
    /** Home Assistant not reachable: the cached list is shown and changes are queued. */
    val isOffline = _isOffline.asStateFlow()
    val listDisplayMode = _listDisplayMode.asStateFlow()
    val areaOrder = _areaOrder.asStateFlow()
    /** Title of the current list; follows list switches on the phone. */
    val listName = _listName.asStateFlow()
    /** Lists and stores of the phone; switching them on the watch is synced back to the phone. */
    val knownLists = _knownLists.asStateFlow()
    val currentEntity: String? get() = activeSettings?.entity

    private var haClient: HaWearClient? = null
    private var listSync: WearListSync? = null
    private var activeSettings: WearSettingsStore.WearSettings? = null

    // SharedPreferences backing WearSettingsStore — must match the name used there.
    private val prefs: SharedPreferences =
        app.getSharedPreferences("wear_ha_settings", android.content.Context.MODE_PRIVATE)

    // Fires on the main thread immediately after WearSettingsListenerService writes
    // new settings, so the app reacts without waiting for the next 8-second refresh tick.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in setOf(
                "ha_url", "ha_token", "todo_entity", "todo_list_name", "list_display_mode", "area_order",
                "known_lists"
            )
        ) return@OnSharedPreferenceChangeListener
        _listName.value = currentListName()
        _knownLists.value = settingsStore.getKnownLists()
        val latest = settingsStore.getSettings() ?: return@OnSharedPreferenceChangeListener
        if (latest != activeSettings) {
            initClient(latest.url, latest.token, latest.entity)
            val newMode = settingsStore.getDisplayMode()
            if (_listDisplayMode.value != newMode) _listDisplayMode.value = newMode
            refresh()
            requestTileUpdate()
        }
        val newOrder = settingsStore.getAreaOrder()
        if (_areaOrder.value != newOrder) _areaOrder.value = newOrder
    }

    private val dataListener = com.google.android.gms.wearable.DataClient.OnDataChangedListener { events ->
        if (settingsStore.isScreenshotMode()) return@OnDataChangedListener
        events.forEach { event ->
            // The phone reports a changed list: reload now instead of waiting for the next tick
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearSettingsListenerService.LIST_STATE_PATH
            ) {
                refresh()
                return@forEach
            }
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path?.startsWith("/ha_settings") == true
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                if (!settingsStore.applyFromPhone(dataMap)) return@forEach
                applyStoredSettings()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        runCatching { Wearable.getDataClient(app).addListener(dataListener) }
        val settings = settingsStore.getSettings()
        if (settings != null) {
            initClient(settings.url, settings.token, settings.entity)
        }
        if (!settingsStore.isScreenshotMode()) runCatching { loadSettingsFromDataLayer() }
    }

    private fun initClient(url: String, token: String, entity: String) {
        val settings = WearSettingsStore.WearSettings(url, token, entity)
        haClient = HaWearClient(url, token, entity)
        val sync = WearListSync(getApplication(), settings)
        listSync = sync
        activeSettings = settings
        _listName.value = currentListName()
        // Show the last known list right away, also without any connection
        _items.value = sync.cachedItems() ?: emptyList()
        _hasSettings.value = true
    }

    /** Takes over everything the store holds after an update from the phone. */
    private fun applyStoredSettings() {
        val settings = settingsStore.getSettings() ?: return
        _listDisplayMode.value = settingsStore.getDisplayMode()
        _areaOrder.value = settingsStore.getAreaOrder()
        _knownLists.value = settingsStore.getKnownLists()
        initClient(settings.url, settings.token, settings.entity)
        refresh()
    }

    /** List switched on the watch: switch right away and let the phone follow. */
    fun selectList(option: WearListOption) {
        val settings = activeSettings ?: return
        if (option.id == settings.entity) return
        settingsStore.saveSettings(settings.url, settings.token, option.id, option.name)
        sendToPhone(SELECT_LIST_PATH, option.id)
    }

    private fun sendToPhone(path: String, value: String) {
        runCatching {
            Wearable.getNodeClient(getApplication<Application>())
                .connectedNodes
                .addOnSuccessListener { nodes ->
                    nodes.forEach { node ->
                        runCatching {
                            Wearable.getMessageClient(getApplication())
                                .sendMessage(node.id, path, value.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
        }
    }

    private fun currentListName(): String? =
        settingsStore.getListName() ?: settingsStore.getSettings()?.entity?.let(::todoEntityTitle)

    fun refresh() {
        runSync(fetch = true)
    }

    fun toggleItem(item: WearShoppingItem) {
        val sync = listSync ?: return
        _items.value = sync.toggle(item, !item.complete)
        publishOpenCount()
        runSync(fetch = false)
    }

    /** Products offered when adding: most used first, without items that are already open. */
    fun addSuggestions(): List<String> {
        val open = _items.value.filterNot { it.complete }.map { it.name.lowercase() }.toSet()
        return settingsStore.getProductSuggestions()
            .map { it.name }
            .filterNot { it.lowercase() in open }
            .take(MAX_ADD_SUGGESTIONS)
    }

    /** Category of a product known from the phone's history; null if unknown or without category. */
    fun knownArea(name: String): String? =
        settingsStore.getProductSuggestions()
            .firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?.areaKey

    /** Categories to choose from when adding: active in the current list's store, in its order. */
    fun addAreaOptions(): List<WearAreaOption> = settingsStore.getMarketAreas()

    /** Adds an item with the given category (null = none). */
    fun addItem(text: String, areaKey: String?) {
        val name = text.trim()
        if (name.isEmpty()) return
        val sync = listSync ?: return
        _items.value = sync.add(name, areaKey)
        publishOpenCount()
        runSync(fetch = true)
    }

    fun clearCompleted() {
        val sync = listSync ?: return
        if (_items.value.none { it.complete }) return
        _items.value = sync.clearCompleted()
        publishOpenCount()
        runSync(fetch = false)
    }

    /** Sends queued changes (and reloads); if that fails, they are sent once a connection is back. */
    private fun runSync(fetch: Boolean) {
        val client = haClient ?: return
        val sync = listSync ?: return
        viewModelScope.launch {
            if (fetch) _isLoading.value = true
            try {
                val result = sync.sync(client, fetch)
                _items.value = result.items
                _isOffline.value = !result.online
                _error.value = null
                publishOpenCount()
                if (sync.hasPendingChanges()) {
                    runCatching { TileRefreshWorker.scheduleSync(getApplication()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                if (fetch) _isLoading.value = false
            }
        }
    }

    /** Shares the open item count with the tile; refreshes the tile only when it changed. */
    private fun publishOpenCount() {
        val items = _items.value
        if (settingsStore.saveOpenCount(items.count { !it.complete }, items.size)) requestTileUpdate()
    }

    private fun requestTileUpdate() {
        runCatching {
            TileService.getUpdater(getApplication<Application>())
                .requestUpdate(ShoppingTileService::class.java)
        }
    }

    private fun loadSettingsFromDataLayer() {
        Wearable.getDataClient(getApplication<Application>())
            .getDataItems(Uri.parse("wear://*/ha_settings"))
            .addOnSuccessListener { dataItems ->
                var found = false
                dataItems.forEach { dataItem ->
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    if (!settingsStore.applyFromPhone(dataMap)) return@forEach
                    applyStoredSettings()
                    found = true
                }
                dataItems.release()
                if (!found) runCatching { requestSettingsFromPhone() }
            }
            .addOnFailureListener { runCatching { requestSettingsFromPhone() } }
    }

    private fun requestSettingsFromPhone() {
        runCatching {
            Wearable.getNodeClient(getApplication<Application>())
                .connectedNodes
                .addOnSuccessListener { nodes ->
                    nodes.forEach { node ->
                        runCatching {
                            Wearable.getMessageClient(getApplication())
                                .sendMessage(node.id, "/request_settings", null)
                        }
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { Wearable.getDataClient(getApplication<Application>()).removeListener(dataListener) }
    }
}
