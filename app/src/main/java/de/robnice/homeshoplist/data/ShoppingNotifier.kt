package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.util.NotificationHelper

interface ShoppingNotifier {
    fun showNewItemNotification(item: ShoppingItem)
}

class SystemShoppingNotifier(
    private val context: Context
) : ShoppingNotifier {
    override fun showNewItemNotification(item: ShoppingItem) {
        NotificationHelper.showNewItemNotification(context, item)
    }
}
