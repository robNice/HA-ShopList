package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.util.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Stores and persists the list of the offline list mode. */
interface LocalListStore {
    fun read(): String?
    fun write(raw: String)
}

class SharedPreferencesLocalListStore(context: Context) : LocalListStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(raw: String) {
        prefs.edit().putString(KEY, raw).apply()
    }

    private companion object {
        private const val PREFS = "offline_shopping_list"
        private const val KEY = "items"
    }
}

/**
 * The list of the offline list mode. Nothing is read from or written to Home Assistant,
 * so it is always editable and never triggers notifications.
 */
class LocalShoppingRepository(
    private val store: LocalListStore,
    private val productHistoryRecorder: ProductHistoryRecorder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ShoppingRepository {

    constructor(appContext: Context) : this(
        store = SharedPreferencesLocalListStore(appContext),
        productHistoryRecorder = RepositoryProductHistoryRecorder(appContext)
    )

    private val lock = Any()
    private val _items = MutableStateFlow(restore())

    override val items = _items.asStateFlow()
    override val loaded = MutableStateFlow(true).asStateFlow()
    override val authFailed = MutableStateFlow(false).asStateFlow()
    override val connectionErrors = MutableStateFlow(false).asStateFlow()
    override val isOffline = MutableStateFlow(false).asStateFlow()
    override val isConnecting = MutableStateFlow(false).asStateFlow()
    override val newItems = MutableSharedFlow<ShoppingItem>().asSharedFlow()
    override val remoteActivity = MutableSharedFlow<Unit>().asSharedFlow()
    override val reconnected = MutableStateFlow(0L).asStateFlow()

    override fun addItem(name: String, area: ShoppingArea?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        change { it + ShoppingItem(id = newId(), name = trimmed, complete = false, area = area) }
        scope.launch { productHistoryRecorder.recordProductUse(trimmed, area) }
    }

    override fun toggleItem(item: ShoppingItem) {
        change { items ->
            items.map { if (it.id == item.id) it.copy(complete = !item.complete) else it }
        }
    }

    override fun updateItem(item: ShoppingItem, newName: String, area: ShoppingArea?) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return

        change { items ->
            items.map { if (it.id == item.id) it.copy(name = trimmed, area = area) else it }
        }
        if (trimmed != item.name) {
            scope.launch { productHistoryRecorder.recordProductUse(trimmed, area) }
        }
    }

    override fun moveItem(itemId: String, previousItemId: String?, area: ShoppingArea?) {
        change { items ->
            val mutableItems = items.toMutableList()
            val fromIndex = mutableItems.indexOfFirst { it.id == itemId }
            if (fromIndex < 0) return@change items

            val moved = mutableItems.removeAt(fromIndex).copy(area = area)
            val targetIndex = if (previousItemId == null) {
                0
            } else {
                mutableItems.indexOfFirst { it.id == previousItemId }
                    .takeIf { it >= 0 }
                    ?.plus(1)
                    ?: mutableItems.size
            }
            mutableItems.add(targetIndex.coerceIn(0, mutableItems.size), moved)
            mutableItems
        }
    }

    override fun clearCompleted() {
        change { items -> items.filterNot { it.complete } }
    }

    // No server: connection handling does nothing
    override fun ensureConnected() = Unit
    override fun setReconnectAllowed(allowed: Boolean) = Unit
    override fun disconnect() = Unit

    private fun change(transform: (List<ShoppingItem>) -> List<ShoppingItem>) {
        synchronized(lock) {
            val updated = transform(_items.value)
            if (updated == _items.value) return
            _items.value = updated
            store.write(encode(updated))
        }
    }

    private fun restore(): List<ShoppingItem> {
        val raw = store.read() ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ShoppingItem(
                    id = id,
                    name = item.optString("name"),
                    complete = item.optBoolean("complete"),
                    area = ShoppingArea.fromKey(
                        if (item.isNull("area")) null else item.optString("area")
                    )
                )
            }
        } catch (e: Exception) {
            Debug.log("LOCAL REPOSITORY: failed to restore offline list: ${e.message}")
            emptyList()
        }
    }

    private fun encode(items: List<ShoppingItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("complete", item.complete)
                    .put("area", item.area?.key ?: JSONObject.NULL)
            )
        }
        return array.toString()
    }
}
