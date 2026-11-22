package de.robnice.homeshoplist

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.WearSettingsPublisher
import de.robnice.homeshoplist.model.ShoppingList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearSettingsSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val store = SettingsDataStore(applicationContext)
        when (messageEvent.path) {
            "/request_settings" -> scope.launch {
                WearSettingsPublisher.publish(applicationContext, store)
            }

            // List switched on the watch: same as switching it in the phone's header
            WearSettingsPublisher.SELECT_LIST_PATH -> scope.launch {
                val entity = messageEvent.data.toString(Charsets.UTF_8).trim()
                if (entity.isEmpty()) return@launch
                val list = store.knownLists.first().firstOrNull { it.id == entity }
                    ?: ShoppingList(entity, entity)
                store.selectTodoList(list)
                WearSettingsPublisher.publish(applicationContext, store)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}