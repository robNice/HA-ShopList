package de.robnice.homeshoplist.wear.tile

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TileRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        runCatching {
            TileService.getUpdater(applicationContext)
                .requestUpdate(ShoppingTileService::class.java)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "tile_refresh_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TileRefreshWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
