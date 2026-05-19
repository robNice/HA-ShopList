package de.robnice.homeshoplist.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import androidx.wear.tiles.TileService
import de.robnice.homeshoplist.wear.data.HaWearClient
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
    private val _listDisplayMode = MutableStateFlow(settingsStore.getDisplayMode())

    val items = _items.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val hasSettings = _hasSettings.asStateFlow()
    val error = _error.asStateFlow()
    val listDisplayMode = _listDisplayMode.asStateFlow()

    private var haClient: HaWearClient? = null

    private val dataListener = com.google.android.gms.wearable.DataClient.OnDataChangedListener { events ->
        events.forEach { event ->
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
                initClient(url, token, entity)
                refresh()
            }
        }
    }

    init {
        Wearable.getDataClient(app).addListener(dataListener)
        val settings = settingsStore.getSettings()
        if (settings != null) {
            initClient(settings.url, settings.token, settings.entity)
        }
        requestSettingsFromPhone()
    }

    private fun initClient(url: String, token: String, entity: String) {
        haClient = HaWearClient(url, token, entity)
        _hasSettings.value = true
    }

    fun refresh() {
        val client = haClient ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _items.value = client.fetchItems()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleItem(item: WearShoppingItem) {
        val newComplete = !item.complete
        _items.value = _items.value.map { if (it.id == item.id) it.copy(complete = newComplete) else it }
        viewModelScope.launch {
            runCatching { haClient?.toggleItem(item.id, newComplete) }
                .onFailure {
                    _items.value = _items.value.map {
                        if (it.id == item.id) it.copy(complete = item.complete) else it
                    }
                }
            requestTileUpdate()
        }
    }

    fun clearCompleted() {
        val completed = _items.value.filter { it.complete }
        if (completed.isEmpty()) return
        _items.value = _items.value.filterNot { it.complete }
        viewModelScope.launch {
            completed.forEach { item -> runCatching { haClient?.removeItem(item.id) } }
            requestTileUpdate()
        }
    }

    private fun requestTileUpdate() {
        runCatching {
            TileService.getUpdater(getApplication<Application>())
                .requestUpdate(ShoppingTileService::class.java)
        }
    }

    private fun requestSettingsFromPhone() {
        Wearable.getNodeClient(getApplication<Application>())
            .connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(getApplication())
                        .sendMessage(node.id, "/request_settings", null)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        Wearable.getDataClient(getApplication<Application>()).removeListener(dataListener)
    }
}
