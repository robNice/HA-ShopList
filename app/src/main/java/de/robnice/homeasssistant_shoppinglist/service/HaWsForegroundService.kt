package de.robnice.homeasssistant_shoppinglist.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.robnice.homeasssistant_shoppinglist.R
import de.robnice.homeasssistant_shoppinglist.data.HaRuntime
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.util.NotificationHelper
import kotlinx.coroutines.*

class HaWsForegroundService : Service() {

    companion object {
        private const val SERVICE_CHANNEL_ID = "ws_service_channel"
        private const val SERVICE_NOTIFICATION_ID = 1

        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_TOKEN = "token"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        de.robnice.homeasssistant_shoppinglist.util.Debug.log("SERVICE onCreate()")
        ensureServiceChannel()
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        de.robnice.homeasssistant_shoppinglist.util.Debug.log("SERVICE onStartCommand(startId=$startId flags=$flags)")
        val baseUrl = intent?.getStringExtra(EXTRA_BASE_URL) ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return START_NOT_STICKY

        if (HaRuntime.repository == null) {
            HaRuntime.repository = HaWebSocketRepository(baseUrl, token)

            scope.launch {
                HaRuntime.repository!!.newItems.collect { item ->
                    NotificationHelper.showNewItemNotification(this@HaWsForegroundService, item)
                }
            }
        } else {
            HaRuntime.repository!!.ensureConnected()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        de.robnice.homeasssistant_shoppinglist.util.Debug.log("SERVICE onDestroy()")
        HaRuntime.repository?.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Shopping List Background",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildServiceNotification() =
        NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.appIsRunning))
            .setContentText(getString(R.string.alwaysActiveMsg))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
}
