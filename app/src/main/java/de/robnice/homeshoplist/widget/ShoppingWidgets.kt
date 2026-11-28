package de.robnice.homeshoplist.widget

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import de.robnice.homeshoplist.MainActivity
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.model.labelRes
import de.robnice.homeshoplist.ui.theme.DarkColorScheme
import de.robnice.homeshoplist.ui.theme.LightColorScheme

private val WidgetColors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)

private val ItemIdKey = ActionParameters.Key<String>("widget_item_id")

/** Ticks an item off (or back on) right in the widget. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        val repo = WidgetConnection.connectLoaded(context) ?: return
        repo.items.value.firstOrNull { it.id == id }?.let { repo.toggleItem(it) }
        // Redraw right away: the app process may be frozen before the debounced updater fires
        WidgetUpdater.updateAll(context)
    }
}

/** Shared base: live content from [WidgetDataSource], app colours. */
abstract class ShoppingWidgetBase : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdater.ensureStarted(context)
        val data = WidgetDataSource.observe(context)
        provideContent {
            val state by data.collectAsState(initial = WidgetData.Initial)
            GlanceTheme(colors = WidgetColors) {
                Content(state)
            }
        }
    }

    @Composable
    protected abstract fun Content(data: WidgetData)
}

/** List with header (list switch) and input bar with category choice. */
class ShoppingListInputWidget : ShoppingWidgetBase() {
    @Composable
    override fun Content(data: WidgetData) {
        WidgetSurface {
            ListHeader(data, switchable = true)
            HeaderLine()
            Spacer(GlanceModifier.height(8.dp))
            InputBar(data, showListName = false)
            Spacer(GlanceModifier.height(4.dp))
            ItemList(data)
        }
    }
}

/** Just the list. */
class ShoppingListOnlyWidget : ShoppingWidgetBase() {
    @Composable
    override fun Content(data: WidgetData) {
        WidgetSurface {
            ListHeader(data, switchable = false)
            HeaderLine()
            Spacer(GlanceModifier.height(4.dp))
            ItemList(data)
        }
    }
}

/** Just the input bar with category choice. */
class QuickAddWidget : ShoppingWidgetBase() {
    @Composable
    override fun Content(data: WidgetData) {
        WidgetSurface(padding = 0, centerVertically = true, transparent = true) {
            if (data.status == WidgetData.Status.NOT_CONFIGURED) {
                Message(R.string.widget_not_configured)
            } else {
                InputBar(data, showListName = true, standalone = true)
            }
        }
    }
}

/** Just the list switch: current list and its open items. */
class ListSwitcherWidget : ShoppingWidgetBase() {
    @Composable
    override fun Content(data: WidgetData) {
        val context = LocalContext.current
        val colors = GlanceTheme.colors
        WidgetSurface(padding = 0, centerVertically = true, transparent = true) {
            if (data.status == WidgetData.Status.NOT_CONFIGURED) {
                Message(R.string.widget_not_configured)
                return@WidgetSurface
            }
            // Plain RemoteViews, so the list dialog opens right at the widget (see InputBar)
            val views = RemoteViews(context.packageName, R.layout.widget_list_switcher).apply {
                setTextViewText(R.id.widget_list_title, data.listTitle.ifBlank { context.getString(R.string.app_name) })
                if (data.status == WidgetData.Status.READY) {
                    setTextViewText(
                        R.id.widget_list_subtitle,
                        context.resources.getQuantityString(R.plurals.widget_open_items, data.openCount, data.openCount)
                    )
                    setViewVisibility(R.id.widget_list_subtitle, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_list_subtitle, View.GONE)
                }
                setContentDescription(R.id.widget_list_switcher, context.getString(R.string.list_switch))
                setOnClickPendingIntent(
                    R.id.widget_list_switcher,
                    dialogPendingIntent(context, WidgetDialogActivity.MODE_LIST)
                )
            }
            AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxWidth().height(52.dp))
        }
    }
}

abstract class ShoppingWidgetReceiverBase : GlanceAppWidgetReceiver() {
    /** Periodic system update or widget just placed: load the current state from Home Assistant. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetConnection.connectInBackground(context)
    }
}

class ShoppingListInputWidgetReceiver : ShoppingWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingListInputWidget()
}

class ShoppingListOnlyWidgetReceiver : ShoppingWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingListOnlyWidget()
}

class QuickAddWidgetReceiver : ShoppingWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

class ListSwitcherWidgetReceiver : ShoppingWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = ListSwitcherWidget()
}

// ---- building blocks ----

private fun dialogAction(mode: String): Action =
    actionStartActivity<WidgetDialogActivity>(
        actionParametersOf(ActionParameters.Key<String>(WidgetDialogActivity.EXTRA_MODE) to mode)
    )

private fun openAppAction(): Action = actionStartActivity<MainActivity>()

@Composable
private fun WidgetSurface(
    padding: Int = 10,
    centerVertically: Boolean = false,
    /** No card around the content: a launcher cell is never lower than one row, so slim widgets draw their own pill. */
    transparent: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = GlanceModifier.fillMaxSize().appWidgetBackground()
    Column(
        verticalAlignment = if (centerVertically) Alignment.CenterVertically else Alignment.Top,
        modifier = (if (transparent) base else base.background(GlanceTheme.colors.background).cornerRadius(20.dp))
            .padding(padding.dp)
    ) {
        content()
    }
}

@Composable
private fun Icon(res: Int, size: Int, tint: androidx.glance.unit.ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size.dp)
    )
}

/** Still version of the line in the app header; RemoteViews because Glance rows only know equal weights. */
@Composable
private fun HeaderLine() {
    val context = LocalContext.current
    Spacer(GlanceModifier.height(2.dp))
    AndroidRemoteViews(
        remoteViews = RemoteViews(context.packageName, R.layout.widget_header_line),
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).padding(horizontal = 4.dp)
    )
}

@Composable
private fun ListHeader(data: WidgetData, switchable: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val canSwitch = switchable && data.status != WidgetData.Status.NOT_CONFIGURED
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val title = data.listTitle.ifBlank { context.getString(R.string.app_name) }
        if (canSwitch) {
            // Plain RemoteViews, so the list dialog opens right at the list name (see InputBar)
            val views = RemoteViews(context.packageName, R.layout.widget_list_title).apply {
                setTextViewText(R.id.widget_list_title, title)
                setContentDescription(R.id.widget_list_title_row, context.getString(R.string.list_switch))
                setOnClickPendingIntent(
                    R.id.widget_list_title_row,
                    dialogPendingIntent(context, WidgetDialogActivity.MODE_LIST)
                )
            }
            AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.defaultWeight())
        } else {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clickable(openAppAction()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(R.drawable.ic_widget_list, size = 18, tint = colors.primary)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
        // App logo + "open" icon, together one button that opens the app
        Row(
            modifier = GlanceModifier
                .padding(horizontal = 4.dp, vertical = 5.dp)
                .clickable(openAppAction()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(R.drawable.ic_widget_logo, size = 26, tint = colors.primary)
            Spacer(GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_open),
                contentDescription = context.getString(R.string.widget_open_app),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                modifier = GlanceModifier.size(20.dp)
            )
        }
    }
}

/**
 * Input bar, same order as in the app: input field, category, add button.
 * Built as plain RemoteViews instead of Glance: Glance only creates immutable click intents,
 * into which the launcher cannot put the bounds of the tapped view. With these mutable ones
 * the dialog learns where the bar is and opens right over it.
 */
@Composable
private fun InputBar(data: WidgetData, showListName: Boolean, standalone: Boolean = false) {
    val context = LocalContext.current
    val hint = context.getString(R.string.new_item).let { base ->
        if (showListName && data.listTitle.isNotBlank()) "$base · ${data.listTitle}" else base
    }
    val views = RemoteViews(context.packageName, R.layout.widget_input_bar).apply {
        setTextViewText(R.id.widget_input_hint, hint)
        setTextViewText(R.id.widget_input_area_emoji, data.selectedArea.emoji)
        setContentDescription(R.id.widget_input_add, context.getString(R.string.add))
        setInt(
            R.id.widget_input_bar,
            "setBackgroundResource",
            if (standalone) R.drawable.widget_input_bar_bg_standalone else R.drawable.widget_input_bar_bg
        )
        val add = dialogPendingIntent(context, WidgetDialogActivity.MODE_ADD)
        setOnClickPendingIntent(R.id.widget_input_bar, add)
        setOnClickPendingIntent(R.id.widget_input_add, add)
        setOnClickPendingIntent(R.id.widget_input_area, dialogPendingIntent(context, WidgetDialogActivity.MODE_AREA))
    }
    AndroidRemoteViews(
        remoteViews = views,
        modifier = GlanceModifier.fillMaxWidth().height((if (standalone) 52 else 44).dp)
    )
}

/**
 * Mutable on purpose (see [InputBar]); the intent is explicit, so nothing but the fill-in
 * extras of the launcher (the source bounds) can be changed.
 */
private fun dialogPendingIntent(context: Context, mode: String): PendingIntent {
    val intent = Intent(context, WidgetDialogActivity::class.java)
        .setData(Uri.parse("homeshoplist-widget://dialog/$mode"))
        .putExtra(WidgetDialogActivity.EXTRA_MODE, mode)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    return PendingIntent.getActivity(context, mode.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
}

@Composable
private fun ItemList(data: WidgetData) {
    when {
        data.status == WidgetData.Status.NOT_CONFIGURED -> Message(R.string.widget_not_configured)
        data.status == WidgetData.Status.AUTH_FAILED -> Message(R.string.auth_failed_title)
        data.status == WidgetData.Status.LOADING -> Message(R.string.widget_loading)
        data.rows.isEmpty() -> Message(R.string.no_items)
        else -> {
            val context = LocalContext.current
            val colors = GlanceTheme.colors
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(data.rows, itemId = { it.key }) { row ->
                    when (row) {
                        is WidgetRow.Header -> Text(
                            text = "${row.area.emoji}  ${context.getString(row.area.labelRes())}",
                            maxLines = 1,
                            style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                        )

                        is WidgetRow.CompletedHeader -> CompletedHeader(row.count)

                        is WidgetRow.Item -> ItemRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedHeader(count: Int) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${context.getString(R.string.completed)} ($count)",
            maxLines = 1,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight()
        )
        // Asks for confirmation in the dialog, like the app does
        Row(
            modifier = GlanceModifier
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .clickable(dialogAction(WidgetDialogActivity.MODE_CLEAR_COMPLETED)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(R.drawable.ic_widget_delete, size = 16, tint = colors.primary)
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = context.getString(R.string.clear_completed),
                maxLines = 1,
                style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun ItemRow(row: WidgetRow.Item) {
    val colors = GlanceTheme.colors
    val item = row.item
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .clickable(actionRunCallback<ToggleItemAction>(actionParametersOf(ItemIdKey to item.id))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.complete) R.drawable.ic_widget_checkbox_checked else R.drawable.ic_widget_checkbox_blank,
            size = 20,
            tint = if (item.complete) colors.onSurfaceVariant else colors.primary
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = item.name,
            maxLines = 2,
            style = TextStyle(
                color = if (item.complete) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 14.sp,
                textDecoration = if (item.complete) TextDecoration.LineThrough else TextDecoration.None
            )
        )
    }
}

@Composable
private fun Message(textRes: Int) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(openAppAction()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(textRes),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
        )
    }
}
