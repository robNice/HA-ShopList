package de.robnice.homeshoplist.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.robnice.homeshoplist.util.Debug

/**
 * Tells a paired watch how many items are open, so its tile updates right away
 * instead of waiting for its own polling. The data layer only delivers changed values.
 */
class WearListStatePublisher(context: Context) {

    private val appContext = context.applicationContext

    fun publish(todoEntity: String, openCount: Int, totalCount: Int) {
        runCatching {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("todo_entity", todoEntity)
                dataMap.putInt("open_count", openCount)
                dataMap.putInt("total_count", totalCount)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(appContext).putDataItem(request)
        }.onFailure { Debug.log("WEAR: publishing list state failed: ${it.message}") }
    }

    companion object {
        const val PATH = "/list_state"
    }
}
