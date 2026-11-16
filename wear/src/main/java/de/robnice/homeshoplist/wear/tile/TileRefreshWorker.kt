package de.robnice.homeshoplist.wear.tile

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.data.HaWearClient
import de.robnice.homeshoplist.wear.data.WearListSync
import java.util.concurrent.TimeUnit

/**
 * Sends changes made offline on the watch and fetches the open item count in the background
 * (e.g. after changes on the phone), then refreshes the tile when the count has changed.
 */
class TileRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WearSettingsStore(applicationContext)
        val settings = store.getSettings() ?: return Result.success()
        return try {
            val sync = WearListSync(applicationContext, settings)
            val result = sync.sync(HaWearClient(settings.url, settings.token, settings.entity))
            if (!result.online) return Result.retry()
            val items = result.items
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
        private const val SYNC_WORK_NAME = "pending_changes_sync"

        /**
         * Sends changes made offline as soon as the watch has a connection again
         * (via the phone, Wi-Fi or LTE), even if the app is closed by then.
         */
        fun scheduleSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<TileRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

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
