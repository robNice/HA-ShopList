package de.robnice.homeshoplist.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.robnice.homeshoplist.util.Debug
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import de.robnice.homeshoplist.data.history.ProductHistoryEntity
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import org.json.JSONArray
import org.json.JSONObject
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingMarket
import de.robnice.homeshoplist.model.labelRes
import de.robnice.homeshoplist.model.ShoppingLists

/**
 * Sends the current connection, list (id and name) and category order of the active market to the watch.
 * Values are read from [SettingsDataStore].
 */
object WearSettingsPublisher {

    private const val MAX_SUGGESTIONS = 20

    /** Most used products first; only name and category are needed on the watch. */
    internal fun topSuggestions(history: List<ProductHistoryEntity>): List<Pair<String, String?>> =
        history
            .sortedWith(compareByDescending<ProductHistoryEntity> { it.useCount }.thenByDescending { it.lastUsedAt })
            .take(MAX_SUGGESTIONS)
            .map { it.displayName to it.areaKey }

    private fun encodeAreas(context: Context, areas: List<ShoppingArea>): String =
        areasJson(context, areas).toString()

    private fun areasJson(context: Context, areas: List<ShoppingArea>): JSONArray {
        val array = JSONArray()
        areas.forEach { area ->
            array.put(JSONObject().put("key", area.key).put("label", context.getString(area.labelRes())))
        }
        return array
    }

    /**
     * Active categories of every store plus the list → store links. Lists without a link use the first
     * store, like on the phone. Lets the watch show the right categories right after switching the list.
     */
    private fun encodeListMarketAreas(
        context: Context,
        markets: List<ShoppingMarket>,
        links: Map<String, String>
    ): String {
        val marketsJson = JSONObject()
        markets.forEach { marketsJson.put(it.id, areasJson(context, it.enabledAreas)) }
        val linksJson = JSONObject()
        links.forEach { (list, marketId) -> linksJson.put(list, marketId) }
        return JSONObject()
            .put("markets", marketsJson)
            .put("links", linksJson)
            .put("default", markets.firstOrNull()?.id ?: JSONObject.NULL)
            .toString()
    }

    internal fun encodeSuggestions(suggestions: List<Pair<String, String?>>): String {
        val array = JSONArray()
        suggestions.forEach { (name, area) ->
            array.put(JSONObject().put("name", name).put("area", area ?: JSONObject.NULL))
        }
        return array.toString()
    }

    /** Path of the message the watch sends when the list is switched there. */
    const val SELECT_LIST_PATH = "/select_list"

    private suspend fun knownListsWithCurrent(dataStore: SettingsDataStore): List<ShoppingList> {
        val entity = dataStore.todoEntity.first()
        val name = dataStore.todoListName.first().ifBlank { entity }
        return ShoppingLists.withCurrent(dataStore.knownLists.first(), ShoppingList(entity, name))
    }


    /**
     * Publishes whenever one of the shared settings changes (list switch, store switch, saved settings,
     * automatic list selection, ...). Runs in a long-lived scope, so a switch is never lost because the
     * screen that triggered it was rebuilt in the meantime.
     */
    @OptIn(FlowPreview::class)
    suspend fun publishOnChange(context: Context, dataStore: SettingsDataStore) {
        combine(
            listOf(
                dataStore.haUrl,
                dataStore.haToken,
                dataStore.todoEntity,
                dataStore.todoListName,
                dataStore.listDisplayMode,
                dataStore.areaOrder,
                dataStore.enabledAreas,
                // All stores and the list links: the watch picks the store itself when the list is switched there
                dataStore.markets,
                dataStore.listMarketLinks,
                dataStore.knownLists,
                ProductHistoryRepository.getInstance(context).observeHistory().map(::topSuggestions),
                // must stay last, see below
                dataStore.offlineMode
            )
        ) { it.toList() }
            // Also once at app start, so the watch gets data added by an app update (lists, stores)
            .distinctUntilChanged()
            // A list switch can also switch the store: send the combined result once
            .debounce(300)
            .collect { values ->
                val offlineMode = values.last() as Boolean
                if (!offlineMode) publish(context, dataStore)
            }
    }

    suspend fun publish(context: Context, dataStore: SettingsDataStore = SettingsDataStore(context)) {
        val url = dataStore.haUrl.first()
        val token = dataStore.haToken.first()
        if (url.isBlank() || token.isBlank()) return

        val request = PutDataMapRequest.create("/ha_settings").apply {
            dataMap.putString("ha_url", url)
            dataMap.putString("ha_token", token)
            dataMap.putString("todo_entity", dataStore.todoEntity.first())
            // Friendly name from Home Assistant, shown as title on the watch
            dataMap.putString("todo_list_name", dataStore.todoListName.first())
            dataMap.putString("list_display_mode", dataStore.listDisplayMode.first())
            dataMap.putString("area_order", dataStore.areaOrder.first())
            // For switching the list on the watch; the order above already belongs to the list's market
            dataMap.putString("known_lists", ShoppingLists.encode(knownListsWithCurrent(dataStore)))
            // Categories offered when adding on the watch: active ones of the list's market, in its order
            dataMap.putString("market_areas", encodeAreas(context, dataStore.activeMarket.first().enabledAreas))
            dataMap.putString(
                "list_market_areas",
                encodeListMarketAreas(context, dataStore.markets.first(), dataStore.listMarketLinks.first())
            )
            // Suggestions (with category) for adding items on the watch
            dataMap.putString(
                "product_suggestions",
                encodeSuggestions(topSuggestions(ProductHistoryRepository.getInstance(context).getHistorySnapshot()))
            )
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        // Best effort: without a paired watch or Play services this simply does nothing
        runCatching {
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
                .addOnSuccessListener { Debug.log("WEAR: settings published") }
                .addOnFailureListener { Debug.log("WEAR: publishing settings failed: ${it.message}") }
        }
    }
}
