package de.robnice.homeshoplist.data

import android.content.Context

interface PendingSyncStore {
    fun readPendingChanges(): String?
    fun writePendingChanges(raw: String)
    fun clearPendingChanges()
}

class SharedPreferencesPendingSyncStore(
    context: Context
) : PendingSyncStore {
    private val prefs = context.getSharedPreferences(PENDING_SYNC_PREFS, Context.MODE_PRIVATE)

    override fun readPendingChanges(): String? = prefs.getString(PENDING_SYNC_KEY, null)

    override fun writePendingChanges(raw: String) {
        prefs.edit().putString(PENDING_SYNC_KEY, raw).apply()
    }

    override fun clearPendingChanges() {
        prefs.edit().remove(PENDING_SYNC_KEY).apply()
    }

    private companion object {
        private const val PENDING_SYNC_PREFS = "pending_shopping_list_sync"
        private const val PENDING_SYNC_KEY = "pending_changes"
    }
}
