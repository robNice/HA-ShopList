package de.robnice.homeasssistant_shoppinglist.util

import android.app.NotificationChannel
import android.content.Context
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import de.robnice.homeasssistant_shoppinglist.R

object NotificationHelper {

    private const val CHANNEL_ID = "shopping_channel"

    fun showNewItemNotification(context: Context, item: ShoppingItem) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shopping Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Neuer Eintrag")
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.ic_app_icon
                )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(item.name)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(item.name)
            )
            .setAutoCancel(true)
            .build()


        manager.notify(item.id.hashCode(), notification)
    }
}
