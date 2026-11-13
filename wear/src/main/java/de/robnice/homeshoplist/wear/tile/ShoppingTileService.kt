package de.robnice.homeshoplist.wear.tile

import android.text.format.DateFormat
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.wear.WearMainActivity
import de.robnice.homeshoplist.wear.WearSettingsStore
import de.robnice.homeshoplist.wear.data.HaWearClient
import de.robnice.homeshoplist.wear.model.todoEntityTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Tile with the phone app's branding: ShopList wordmark, colored status line with cart,
 * list name, number of open items and a progress ring. Tapping anywhere opens the list.
 *
 * The counts come from a cache that the phone app, the watch app, this tile and
 * [TileRefreshWorker] keep up to date, so the tile renders instantly; it only asks
 * Home Assistant when the cache is older than [CACHE_MAX_AGE_MS].
 */
class ShoppingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            val device = requestParams.deviceConfiguration
            scope.launch { completer.set(loadTile(device)) }
            "ShoppingTileService.onTileRequest"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .addIdToImageMapping(RES_WORDMARK, imageResource(R.drawable.brand_wordmark))
                    .addIdToImageMapping(RES_CART, imageResource(R.drawable.brand_cart))
                    .build()
            )
            "ShoppingTileService.onTileResourcesRequest"
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        runCatching { TileRefreshWorker.schedule(this) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun loadTile(device: DeviceParametersBuilders.DeviceParameters): TileBuilders.Tile {
        val store = WearSettingsStore(this)
        val settings = store.getSettings()
            ?: return messageTile(device, getString(R.string.tile_setup))
        val listName = todoEntityTitle(settings.entity)

        val cached = store.getOpenCount()
        if (cached != null && System.currentTimeMillis() - cached.updatedAtMillis < CACHE_MAX_AGE_MS) {
            return countTile(device, listName, cached.count, cached.total, stale = false)
        }

        return try {
            val items = HaWearClient(settings.url, settings.token, settings.entity).fetchItems()
            val open = items.count { !it.complete }
            store.saveOpenCount(open, items.size)
            countTile(device, listName, open, items.size, stale = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Loading tile failed", e)
            // Offline: show the last known count dimmed instead of an error
            if (cached != null) countTile(device, listName, cached.count, cached.total, stale = true)
            else messageTile(device, getString(R.string.tile_error))
        }
    }

    // ---------------------------------------------------------------- layouts

    private fun countTile(
        device: DeviceParametersBuilders.DeviceParameters,
        listName: String,
        open: Int,
        total: Int,
        stale: Boolean
    ): TileBuilders.Tile {
        val scale = scaleFor(device)
        val ts = textScaleFor(device)
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.dp(contentWidth(device)))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header(scale, ts, listName))
            .addContent(spacer(4f * scale))
            .addContent(
                text(
                    if (open == 0) "✓" else open.toString(),
                    BIG_SP * ts,
                    if (stale) COLOR_STALE else COLOR_COUNT,
                    bold = true
                )
            )
            .addContent(
                text(
                    getString(if (open == 0) R.string.tile_empty else R.string.tile_open_items),
                    LABEL_SP * ts,
                    COLOR_LABEL
                )
            )
            .addContent(spacer(6f * scale))
            .addContent(text(getString(R.string.tile_show_list), HINT_SP * ts, COLOR_BLUE_SOFT))
            .build()

        // Share of checked items, shown as a green arc on the ring
        val progress = if (total > 0) (total - open).coerceAtLeast(0).toFloat() / total else null
        return tileOf(device, content, progress)
    }

    private fun messageTile(device: DeviceParametersBuilders.DeviceParameters, message: String): TileBuilders.Tile {
        val scale = scaleFor(device)
        val ts = textScaleFor(device)
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.dp(contentWidth(device)))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header(scale, ts, listName = null))
            .addContent(spacer(10f * scale))
            .addContent(text(message, LABEL_SP * ts, COLOR_LABEL, maxLines = 2))
            .addContent(spacer(6f * scale))
            .addContent(text(getString(R.string.tile_show_list), HINT_SP * ts, COLOR_BLUE_SOFT))
            .build()
        return tileOf(device, content, progress = null)
    }

    /** Same as the phone app's top bar: wordmark, colored status line with cart, list name. */
    private fun header(scale: Float, textScale: Float, listName: String?): LayoutElementBuilders.LayoutElement {
        val width = HEADER_WIDTH_DP * scale
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(image(RES_WORDMARK, width, width * WORDMARK_ASPECT))
            .addContent(statusLine(width, scale))
        if (listName != null) {
            column.addContent(spacer(2f * scale))
            column.addContent(text(listName, LIST_SP * textScale, COLOR_LABEL, bold = true))
        }
        return column.build()
    }

    /** Blue track with orange and green segments, followed by the cart (like the phone header). */
    private fun statusLine(width: Float, scale: Float): LayoutElementBuilders.LayoutElement {
        val cartWidth = CART_WIDTH_DP * scale
        val lineWidth = width - cartWidth - 2f * scale
        val thickness = LINE_DP * scale
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        LINE_SEGMENTS.forEach { (fraction, color) ->
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.dp(lineWidth * fraction))
                    .setHeight(DimensionBuilders.dp(thickness))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(ColorBuilders.argb(color))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(DimensionBuilders.dp(thickness / 2))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }
        row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(2f * scale)).build())
        row.addContent(image(RES_CART, cartWidth, cartWidth * CART_ASPECT))
        return row.build()
    }

    /**
     * Current time at the top edge, like on other tiles. The renderer keeps it up to date;
     * the static text is only a fallback for renderers without dynamic values.
     */
    private fun timeText(textScale: Float): LayoutElementBuilders.LayoutElement {
        val is24h = DateFormat.is24HourFormat(this)
        val now = DynamicBuilders.DynamicInstant.platformTimeWithSecondsPrecision()
        val zone = ZoneId.systemDefault()
        val hour = if (is24h) {
            now.getHour(zone).format(TWO_DIGITS)
        } else {
            now.getHour(zone).plus(11).rem(12).plus(1).format()
        }
        val dynamicTime = hour
            .concat(DynamicBuilders.DynamicString.constant(":"))
            .concat(now.getMinute(zone).format(TWO_DIGITS))
        val fallback = SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date())

        return LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(fallback).setDynamicValue(dynamicTime).build())
            .setLayoutConstraintsForDynamicText(TypeBuilders.StringLayoutConstraint.Builder("00:00").build())
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(TIME_SP * textScale))
                    .setColor(ColorBuilders.argb(COLOR_LABEL))
                    .build()
            )
            .build()
    }

    /** Thin ring along the screen edge; the green part shows how much is already checked. */
    private fun progressRing(progress: Float?): LayoutElementBuilders.LayoutElement {
        val ring = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(RING_INSET_DP)).build()
                    )
                    .build()
            )
            .addContent(arc(360f, COLOR_RING_TRACK))
        if (progress != null && progress > 0f) {
            ring.addContent(arc(360f * progress.coerceAtMost(1f), COLOR_GREEN))
        }
        return ring.build()
    }

    private fun arc(degrees: Float, color: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.degrees(degrees))
                    .setThickness(DimensionBuilders.dp(RING_DP))
                    .setColor(ColorBuilders.argb(color))
                    .build()
            )
            .build()

    /** Ring, time at the top, content centered; the whole tile is one tap target that opens the list. */
    private fun root(
        device: DeviceParametersBuilders.DeviceParameters,
        content: LayoutElementBuilders.LayoutElement,
        progress: Float?
    ): LayoutElementBuilders.LayoutElement {
        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearMainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val timeOnTop = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .setModifiers(topPadding(device.screenHeightDp * TIME_TOP_FRACTION))
            .addContent(timeText(textScaleFor(device)))
            .build()

        val centered = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(topPadding(device.screenHeightDp * CONTENT_TOP_FRACTION))
            .addContent(content)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .addContent(progressRing(progress))
            .addContent(timeOnTop)
            .addContent(centered)
            .build()
    }

    // ---------------------------------------------------------------- helpers

    private fun scaleFor(device: DeviceParametersBuilders.DeviceParameters): Float =
        (device.screenHeightDp / REFERENCE_SCREEN_DP).coerceIn(0.8f, 1.1f)

    /**
     * Factor for text sizes (given in sp, which the system multiplies by its font scale).
     * Text grows with a larger system font only up to [MAX_FONT_GROWTH], so the fixed
     * tile layout still fits the round screen.
     */
    private fun textScaleFor(device: DeviceParametersBuilders.DeviceParameters): Float {
        val fontScale = device.fontScale.takeIf { it > 0f } ?: 1f
        return scaleFor(device) * minOf(fontScale, MAX_FONT_GROWTH) / fontScale
    }

    /** Widest text line that stays inside the circle around the center rows. */
    private fun contentWidth(device: DeviceParametersBuilders.DeviceParameters): Float =
        device.screenWidthDp * CONTENT_WIDTH_FRACTION

    private fun imageResource(resId: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(
            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build()
        )
        .build()

    private fun image(id: String, widthDp: Float, heightDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(DimensionBuilders.dp(widthDp))
            .setHeight(DimensionBuilders.dp(heightDp))
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
            .build()

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = 1
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.argb(color))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL
                    )
                    .build()
            )
            .build()

    private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(heightDp)).build()

    private fun topPadding(dp: Float) = ModifiersBuilders.Modifiers.Builder()
        .setPadding(ModifiersBuilders.Padding.Builder().setTop(DimensionBuilders.dp(dp)).build())
        .build()

    private fun tileOf(
        device: DeviceParametersBuilders.DeviceParameters,
        content: LayoutElementBuilders.LayoutElement,
        progress: Float?
    ): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root(device, content, progress)))
            .build()

    companion object {
        private const val TAG = "ShoppingTile"
        private const val RESOURCES_VERSION = "6"
        private const val RES_WORDMARK = "brand_wordmark"
        private const val RES_CART = "brand_cart"

        /**
         * The system asks for a new tile after this time (only while the tile is in use).
         * Wear OS rebuilds a tile at most about every 20 s, and every rebuild uses up that
         * window. Changes made on the phone are pushed and trigger their own rebuild, so this
         * is only a fallback for changes made elsewhere and kept long to leave the window free.
         */
        private const val FRESHNESS_INTERVAL_MS = 5 * 60_000L

        /** Counts younger than this are shown without asking Home Assistant. */
        private const val CACHE_MAX_AGE_MS = 30_000L

        // Layout, designed for a 227 dp screen and scaled for others
        private const val REFERENCE_SCREEN_DP = 227f
        private const val MAX_FONT_GROWTH = 1.15f
        private const val CONTENT_WIDTH_FRACTION = 0.78f
        private const val TIME_TOP_FRACTION = 0.075f
        private const val CONTENT_TOP_FRACTION = 0.09f
        private const val HEADER_WIDTH_DP = 140f
        private const val WORDMARK_ASPECT = 168f / 600f   // height / width of brand_wordmark.png
        private const val CART_WIDTH_DP = 24f
        private const val CART_ASPECT = 58f / 100f        // height / width of brand_cart.png
        private const val LINE_DP = 3.5f
        private const val RING_DP = 4f
        private const val RING_INSET_DP = 3f

        private const val BIG_SP = 46f
        private const val LABEL_SP = 14f
        private const val LIST_SP = 14f
        private const val HINT_SP = 13f
        private const val TIME_SP = 15f

        // Phone app brand colors (ui/theme/Color.kt)
        private val COLOR_BLUE_SOFT = 0xFF8FD2FF.toInt()
        private val COLOR_ORANGE = 0xFFFF9E1A.toInt()
        private val COLOR_GREEN = 0xFF34C759.toInt()
        private val COLOR_TRACK = 0x574EA8F6           // BrandBlue, 34 % alpha like the phone header
        private val COLOR_RING_TRACK = 0x404EA8F6
        private val COLOR_COUNT = 0xFFF3EFFA.toInt()   // DarkOnSurface
        private val COLOR_LABEL = 0xFFD4CDDA.toInt()   // DarkOnSurfaceMuted
        private val COLOR_STALE = 0xFF757575.toInt()

        /** Status line pattern: (share of the line width, color), like the phone header. */
        private val LINE_SEGMENTS = listOf(
            0.08f to COLOR_TRACK,
            0.22f to COLOR_ORANGE,
            0.06f to COLOR_TRACK,
            0.10f to COLOR_GREEN,
            0.20f to COLOR_TRACK,
            0.22f to COLOR_ORANGE,
            0.12f to COLOR_TRACK
        )

        private val TWO_DIGITS =
            DynamicBuilders.DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
    }
}
