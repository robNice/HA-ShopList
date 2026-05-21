package de.robnice.homeshoplist.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import de.robnice.homeshoplist.wear.tile.ShoppingTileService

class WearSettingsListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.dataItem.uri.path?.startsWith("/ha_settings") != true) return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val url = dataMap.getString("ha_url") ?: return@forEach
            val token = dataMap.getString("ha_token") ?: return@forEach
            val entity = dataMap.getString("todo_entity") ?: return@forEach
            if (url.isBlank() || token.isBlank()) return@forEach
            val store = WearSettingsStore(applicationContext)
            store.saveSettings(url, token, entity)
            dataMap.getString("list_display_mode")?.let { store.saveDisplayMode(it) }
            runCatching {
                TileService.getUpdater(applicationContext)
                    .requestUpdate(ShoppingTileService::class.java)
            }
        }
    }
}
