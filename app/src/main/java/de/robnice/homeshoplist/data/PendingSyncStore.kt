package de.robnice.homeshoplist.data

import android.content.Context

interface PendingSyncStore {
    fun readPendingChanges(): String?
    fun writePendingChanges(raw: String)
    fun clearPendingChanges()
}

/**
 * Pending offline changes are stored per todo list, so switching lists never
 * sends changes of one list to another. They are synced when the list is used again.
 */
class SharedPreferencesPendingSyncStore(
    context: Context,
    todoEntity: String
) : PendingSyncStore {
    private val prefs = context.getSharedPreferences(PENDING_SYNC_PREFS, Context.MODE_PRIVATE)
    private val key = pendingSyncKey(todoEntity)

    init {
        // Before lists could be switched there was one key for the configured list. The first
        // repository after the update is created for exactly that list, so it takes them over.
        prefs.getString(LEGACY_PENDING_SYNC_KEY, null)?.let { legacy ->
            val editor = prefs.edit().remove(LEGACY_PENDING_SYNC_KEY)
            if (!prefs.contains(key)) {
                editor.putString(key, legacy)
            }
            editor.apply()
        }
    }

    override fun readPendingChanges(): String? = prefs.getString(key, null)

    override fun writePendingChanges(raw: String) {
        prefs.edit().putString(key, raw).apply()
    }

    override fun clearPendingChanges() {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PENDING_SYNC_PREFS = "pending_shopping_list_sync"
        private const val LEGACY_PENDING_SYNC_KEY = "pending_changes"

        fun pendingSyncKey(todoEntity: String): String = "pending_changes_${todoEntity.trim()}"
    }
}
