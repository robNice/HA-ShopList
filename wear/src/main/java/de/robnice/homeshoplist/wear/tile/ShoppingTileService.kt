package de.robnice.homeshoplist.wear.tile

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.data.HaWearClient
import de.robnice.homeshoplist.wear.model.AREA_EMOJIS
import de.robnice.homeshoplist.wear.model.WearShoppingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShoppingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        scope.launch {
            try {
                val store = WearSettingsStore(this@ShoppingTileService)
                val settings = store.getSettings()
                if (settings == null) {
                    future.set(buildSimpleTile("Handy-App öffnen", COLOR_HINT))
                    return@launch
                }
                val client = HaWearClient(settings.url, settings.token, settings.entity)
                var items = client.fetchItems()

                // Apply pending toggle action from previous interaction
                val toggledId = requestParams.currentState.keyToValueMapping.keys
                    .firstOrNull { it.key.startsWith("toggle:") }
                    ?.key?.removePrefix("toggle:")
                if (toggledId != null) {
                    val target = items.find { it.id == toggledId }
                    if (target != null) {
                        runCatching { client.toggleItem(toggledId, !target.complete) }
                        items = items.map {
                            if (it.id == toggledId) it.copy(complete = !target.complete) else it
                        }
                    }
                }

                future.set(buildTile(items, store.getDisplayMode()))
            } catch (e: Exception) {
                future.set(buildSimpleTile("Fehler beim Laden", COLOR_ERROR))
            }
        }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
        return future
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildTile(items: List<WearShoppingItem>, displayMode: String): TileBuilders.Tile {
        val categorized = displayMode == "categorized"
        val unchecked = items.filter { !it.complete }
        val checked = items.filter { it.complete }

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        var itemsShown = 0

        if (items.isEmpty()) {
            column.addContent(simpleText("Keine Einträge", COLOR_HINT, 14f))
        } else {
            // Unchecked items — categorized with emoji headers or flat
            if (categorized) {
                val orderedAreaKeys = mutableListOf<String?>()
                unchecked.forEach { if (it.areaKey !in orderedAreaKeys) orderedAreaKeys += it.areaKey }

                for (areaKey in orderedAreaKeys) {
                    if (itemsShown >= MAX_VISIBLE_ITEMS) break
                    val groupItems = unchecked.filter { it.areaKey == areaKey }
                    val emoji = areaKey?.let { AREA_EMOJIS[it] }
                    // Only add header if there is at least one item of this area still to show
                    if (emoji != null && groupItems.isNotEmpty()) {
                        column.addContent(emojiHeader(emoji))
                    }
                    for (item in groupItems) {
                        if (itemsShown >= MAX_VISIBLE_ITEMS) break
                        column.addContent(itemRow(item.id, item.name, isChecked = false))
                        itemsShown++
                    }
                }
            } else {
                for (item in unchecked) {
                    if (itemsShown >= MAX_VISIBLE_ITEMS) break
                    column.addContent(itemRow(item.id, item.name, isChecked = false))
                    itemsShown++
                }
            }

            // Checked items flat at the bottom
            for (item in checked) {
                if (itemsShown >= MAX_VISIBLE_ITEMS) break
                column.addContent(itemRow(item.id, item.name, isChecked = true))
                itemsShown++
            }

            val overflow = items.size - itemsShown
            if (overflow > 0) {
                column.addContent(simpleText("+$overflow weitere", COLOR_HINT, 11f))
            }
        }

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    private fun emojiHeader(emoji: String): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(5f))
                            .setBottom(DimensionBuilders.dp(1f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(emoji)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(16f))
                            .setColor(ColorBuilders.ColorProp.Builder(COLOR_WHITE).build())
                            .build()
                    )
                    .build()
            )
            .build()

    private fun itemRow(itemId: String, name: String, isChecked: Boolean): LayoutElementBuilders.LayoutElement {
        val label = (if (isChecked) "☑ " else "☐ ") + name
        val color = if (isChecked) COLOR_CHECKED else COLOR_WHITE

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LoadAction.Builder()
                    .setRequestState(
                        StateBuilders.State.Builder()
                            .addKeyToValueMapping(
                                AppDataKey<DynamicBuilders.DynamicBool>("toggle:$itemId"),
                                DynamicDataBuilders.DynamicDataValue.fromBool(true)
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(2f))
                            .setBottom(DimensionBuilders.dp(2f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(13f))
                            .setColor(ColorBuilders.ColorProp.Builder(color).build())
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun simpleText(text: String, color: Int, sizeSp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.ColorProp.Builder(color).build())
                    .build()
            )
            .build()

    private fun buildSimpleTile(message: String, color: Int): TileBuilders.Tile {
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(simpleText(message, color, 14f))
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    companion object {
        private const val MAX_VISIBLE_ITEMS = 7
        private const val FRESHNESS_INTERVAL_MS = 10L * 60 * 1000
        private val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private val COLOR_CHECKED = 0xFF888888.toInt()
        private val COLOR_HINT = 0xFFAAAAAA.toInt()
        private val COLOR_ERROR = 0xFFFF6666.toInt()
    }
}
