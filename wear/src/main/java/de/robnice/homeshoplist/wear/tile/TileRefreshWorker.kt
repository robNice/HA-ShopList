package de.robnice.homeshoplist.wear.tile

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.data.HaWearClient
import java.util.concurrent.TimeUnit

/**
 * Fetches the open item count in the background (e.g. after changes on the phone)
 * and refreshes the tile when the count has changed.
 */
class TileRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WearSettingsStore(applicationContext)
        val settings = store.getSettings() ?: return Result.success()
        return try {
            val items = HaWearClient(settings.url, settings.token, settings.entity).fetchItems()
            if (store.saveOpenCount(items.count { !it.complete }, items.size)) {
                TileService.getUpdater(applicationContext).requestUpdate(ShoppingTileService::class.java)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "tile_refresh_periodic"

        fun schedule(context: Context) {
            // 15 minutes is the shortest interval WorkManager allows for periodic work
            val request = PeriodicWorkRequestBuilder<TileRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
