package de.robnice.homeshoplist

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.robnice.homeshoplist.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WearSettingsSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != "/request_settings") return

        val store = SettingsDataStore(applicationContext)
        scope.launch {
            val url = store.haUrl.first()
            val token = store.haToken.first()
            val entity = store.todoEntity.first()
            val displayMode = store.listDisplayMode.first()

            if (url.isBlank() || token.isBlank()) return@launch

            val request = PutDataMapRequest.create("/ha_settings").apply {
                dataMap.putString("ha_url", url)
                dataMap.putString("ha_token", token)
                dataMap.putString("todo_entity", entity)
                dataMap.putString("list_display_mode", displayMode)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(applicationContext).putDataItem(request)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}