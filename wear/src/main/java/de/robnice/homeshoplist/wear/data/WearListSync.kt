package de.robnice.homeshoplist.wear.data

import android.content.Context
import android.util.Log
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the list usable without a connection (e.g. phone left in the car, watch without LTE):
 * the last loaded list is cached on the watch, and changes made offline are queued and sent
 * to Home Assistant as soon as it is reachable again. Shared by app, tile and background worker.
 *
 * Cache and queue belong to one server + list; after switching either, both start empty.
 */
class WearListSync(context: Context, private val settings: WearSettingsStore.WearSettings) {

    data class Result(val items: List<WearShoppingItem>, val online: Boolean)

    private data class PendingOp(
        val type: String,
        val id: String,
        val complete: Boolean = false,
        // Only for adds: name and category of the new item
        val name: String? = null,
        val areaKey: String? = null
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val owner = "${settings.url}|${settings.entity}"

    init {
        synchronized(LOCK) {
            if (prefs.getString(KEY_OWNER, null) != owner) {
                prefs.edit()
                    .putString(KEY_OWNER, owner)
                    .remove(KEY_ITEMS)
                    .remove(KEY_PENDING)
                    .commit()
            }
        }
    }

    /** Last known list including the changes not yet sent; null if nothing was ever loaded. */
    fun cachedItems(): List<WearShoppingItem>? = synchronized(LOCK) {
        prefs.getString(KEY_ITEMS, null)?.let(::decodeItems)
    }

    fun hasPendingChanges(): Boolean = synchronized(LOCK) { readPending().isNotEmpty() }

    /** Applies a toggle locally, queues it and returns the updated list. */
    fun toggle(item: WearShoppingItem, complete: Boolean): List<WearShoppingItem> = synchronized(LOCK) {
        val pending = readPending().filterNot { it.type == TYPE_TOGGLE && it.id == item.id }
        writePending(pending + PendingOp(TYPE_TOGGLE, item.id, complete))
        val items = (cachedItems() ?: emptyList()).map {
            if (it.id == item.id) it.copy(complete = complete) else it
        }
        writeItems(items)
        items
    }

    /**
     * Adds an item locally (with a temporary id), queues it and returns the updated list.
     * It is sent with the next sync, also if there is no connection right now.
     */
    fun add(name: String, areaKey: String?): List<WearShoppingItem> = synchronized(LOCK) {
        val tempId = LOCAL_ID_PREFIX + java.util.UUID.randomUUID()
        writePending(readPending() + PendingOp(TYPE_ADD, tempId, name = name, areaKey = areaKey))
        val items = (cachedItems() ?: emptyList()) + WearShoppingItem(tempId, name, complete = false, areaKey = areaKey)
        writeItems(items)
        items
    }

    /** Removes the completed items locally, queues the removals and returns the updated list. */
    fun clearCompleted(): List<WearShoppingItem> = synchronized(LOCK) {
        val items = cachedItems() ?: emptyList()
        val removedIds = items.filter { it.complete }.map { it.id }.toSet()
        val pending = readPending().filterNot { it.id in removedIds }
        writePending(pending + removedIds.map { PendingOp(TYPE_REMOVE, it) })
        val remaining = items.filterNot { it.id in removedIds }
        writeItems(remaining)
        remaining
    }

    /** Sends the queued changes, then loads the current list. Falls back to the cache when offline. */
    suspend fun sync(client: HaWearClient, fetch: Boolean = true): Result = SYNC_MUTEX.withLock {
        val flushed = flushPending(client)
        if (!flushed || !fetch) {
            return@withLock Result(cachedItems() ?: emptyList(), online = flushed)
        }

        try {
            val fetched = client.fetchItems()
            val items = synchronized(LOCK) {
                // Changes made while loading are still queued: keep them visible
                applyPending(fetched, readPending()).also(::writeItems)
            }
            Result(items, online = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Loading list failed, using cache: ${e.message}")
            Result(cachedItems() ?: emptyList(), online = false)
        }
    }

    /** Returns false if Home Assistant was not reachable; the remaining changes stay queued. */
    private suspend fun flushPending(client: HaWearClient): Boolean {
        val ops = synchronized(LOCK) { readPending() }
        for (op in ops) {
            val result = when {
                op.type == TYPE_ADD -> client.addItem(op.name.orEmpty(), op.areaKey)
                // Items added offline only get their real id with the next load
                op.id.startsWith(LOCAL_ID_PREFIX) -> HaWearClient.WriteResult.GONE
                op.type == TYPE_REMOVE -> client.removeItem(op.id)
                else -> client.toggleItem(op.id, op.complete)
            }
            if (result == HaWearClient.WriteResult.FAILED) return false
            // Sent, or the item no longer exists on the server: either way it is done
            synchronized(LOCK) { writePending(readPending() - op) }
        }
        return true
    }

    private fun applyPending(items: List<WearShoppingItem>, pending: List<PendingOp>): List<WearShoppingItem> {
        var result = items
        pending.forEach { op ->
            result = when (op.type) {
                TYPE_ADD -> result + WearShoppingItem(op.id, op.name.orEmpty(), complete = false, areaKey = op.areaKey)
                TYPE_REMOVE -> result.filterNot { it.id == op.id }
                else -> result.map { if (it.id == op.id) it.copy(complete = op.complete) else it }
            }
        }
        return result
    }

    private fun readPending(): List<PendingOp> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val op = array.getJSONObject(index)
                PendingOp(
                    type = op.getString("type"),
                    id = op.getString("id"),
                    complete = op.optBoolean("complete"),
                    name = op.optString("name").takeIf { op.has("name") },
                    areaKey = if (op.isNull("area")) null else op.optString("area")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writePending(ops: List<PendingOp>) {
        val array = JSONArray()
        ops.forEach { op ->
            array.put(
                JSONObject()
                    .put("type", op.type)
                    .put("id", op.id)
                    .put("complete", op.complete)
                    .put("name", op.name ?: JSONObject.NULL)
                    .put("area", op.areaKey ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_PENDING, array.toString()).commit()
    }

    private fun writeItems(items: List<WearShoppingItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("complete", item.complete)
                    .put("area", item.areaKey ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).commit()
    }

    private fun decodeItems(raw: String): List<WearShoppingItem>? = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            WearShoppingItem(
                id = item.getString("id"),
                name = item.getString("name"),
                complete = item.getBoolean("complete"),
                areaKey = if (item.isNull("area")) null else item.getString("area")
            )
        }
    }.getOrNull()

    private companion object {
        private const val TAG = "WearListSync"
        private const val PREFS = "wear_list_cache"
        private const val KEY_OWNER = "owner"
        private const val KEY_ITEMS = "items"
        private const val KEY_PENDING = "pending"
        private const val TYPE_TOGGLE = "toggle"
        private const val TYPE_REMOVE = "remove"
        private const val TYPE_ADD = "add"
        private const val LOCAL_ID_PREFIX = "local:"

        private val LOCK = Any()
        private val SYNC_MUTEX = Mutex()
    }
}
