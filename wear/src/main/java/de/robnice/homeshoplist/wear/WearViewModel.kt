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
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import de.robnice.homeshoplist.wear.tile.ShoppingTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = WearSettingsStore(app)

    private val _items = MutableStateFlow<List<WearShoppingItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _hasSettings = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isOffline = MutableStateFlow(false)
    private val _listDisplayMode = MutableStateFlow(settingsStore.getDisplayMode())
    private val _areaOrder = MutableStateFlow(settingsStore.getAreaOrder())

    val items = _items.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val hasSettings = _hasSettings.asStateFlow()
    val error = _error.asStateFlow()
    /** Home Assistant not reachable: the cached list is shown and changes are queued. */
    val isOffline = _isOffline.asStateFlow()
    val listDisplayMode = _listDisplayMode.asStateFlow()
    val areaOrder = _areaOrder.asStateFlow()

    private var haClient: HaWearClient? = null
    private var listSync: WearListSync? = null
    private var activeSettings: WearSettingsStore.WearSettings? = null

    // SharedPreferences backing WearSettingsStore — must match the name used there.
    private val prefs: SharedPreferences =
        app.getSharedPreferences("wear_ha_settings", android.content.Context.MODE_PRIVATE)

    // Fires on the main thread immediately after WearSettingsListenerService writes
    // new settings, so the app reacts without waiting for the next 8-second refresh tick.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in setOf("ha_url", "ha_token", "todo_entity", "list_display_mode", "area_order")) return@OnSharedPreferenceChangeListener
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
                val url = dataMap.getString("ha_url") ?: return@forEach
                val token = dataMap.getString("ha_token") ?: return@forEach
                val entity = dataMap.getString("todo_entity") ?: return@forEach
                if (url.isBlank() || token.isBlank()) return@forEach
                settingsStore.saveSettings(url, token, entity)
                dataMap.getString("list_display_mode")?.let {
                    settingsStore.saveDisplayMode(it)
                    _listDisplayMode.value = it
                }
                dataMap.getString("area_order")?.let {
                    settingsStore.saveAreaOrder(it)
                    _areaOrder.value = it
                }
                initClient(url, token, entity)
                refresh()
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
        // Show the last known list right away, also without any connection
        _items.value = sync.cachedItems() ?: emptyList()
        _hasSettings.value = true
    }

    fun refresh() {
        runSync(fetch = true)
    }

    fun toggleItem(item: WearShoppingItem) {
        val sync = listSync ?: return
        _items.value = sync.toggle(item, !item.complete)
        publishOpenCount()
        runSync(fetch = false)
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
                    val url = dataMap.getString("ha_url")?.takeIf { it.isNotBlank() } ?: return@forEach
                    val token = dataMap.getString("ha_token")?.takeIf { it.isNotBlank() } ?: return@forEach
                    val entity = dataMap.getString("todo_entity") ?: return@forEach
                    settingsStore.saveSettings(url, token, entity)
                    dataMap.getString("list_display_mode")?.let {
                        settingsStore.saveDisplayMode(it)
                        _listDisplayMode.value = it
                    }
                    dataMap.getString("area_order")?.let {
                        settingsStore.saveAreaOrder(it)
                        _areaOrder.value = it
                    }
                    initClient(url, token, entity)
                    refresh()
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
