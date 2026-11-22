package de.robnice.homeshoplist.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A set of category settings (order and enabled categories) for one supermarket.
 */
data class ShoppingMarket(
    val id: String,
    val name: String,
    val areaOrder: List<ShoppingArea>,
    val enabledAreas: List<ShoppingArea>
)

object ShoppingMarkets {
    /** Name of the market created from the existing category settings on update. Stored, not translated. */
    const val DEFAULT_MARKET_NAME = "Default"
    const val MAX_NAME_LENGTH = 60

    data class State(
        val markets: List<ShoppingMarket>,
        val activeMarketId: String
    ) {
        val activeMarket: ShoppingMarket
            get() = markets.first { it.id == activeMarketId }
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun create(
        name: String,
        id: String = newId(),
        areaOrder: List<ShoppingArea> = ShoppingArea.entries.toList(),
        enabledAreas: List<ShoppingArea> = ShoppingArea.enabledByDefault
    ): ShoppingMarket = normalize(ShoppingMarket(id, name, areaOrder, enabledAreas))

    /** Builds the "Default" market from the comma separated values stored before markets existed. */
    fun fromLegacy(
        storedOrder: String?,
        storedEnabled: String?,
        id: String = newId()
    ): ShoppingMarket {
        val ordered = ShoppingArea.orderedFromStorage(storedOrder)
        return ShoppingMarket(
            id = id,
            name = DEFAULT_MARKET_NAME,
            areaOrder = ordered,
            enabledAreas = ShoppingArea.enabledFromStorage(storedEnabled, ordered)
        )
    }

    /**
     * Completes the order with missing categories and keeps the enabled categories in that order.
     * No enabled category means all are enabled, like the stored values before markets existed.
     */
    fun normalize(market: ShoppingMarket): ShoppingMarket {
        val ordered = ShoppingArea.orderedFromStorage(ShoppingArea.serializeOrder(market.areaOrder))
        val enabledSet = market.enabledAreas.toSet()
        val enabled = ordered.filter { it in enabledSet }
            .ifEmpty { ordered.filterNot { it in ShoppingArea.disabledByDefault } }
        return market.copy(
            id = market.id.trim().ifEmpty { newId() },
            name = normalizeName(market.name).ifEmpty { DEFAULT_MARKET_NAME },
            areaOrder = ordered,
            enabledAreas = enabled
        )
    }

    fun normalizeName(name: String): String = name.trim().take(MAX_NAME_LENGTH)

    /**
     * Guarantees the invariants: at least one market, unique ids, and an active id that exists.
     */
    fun sanitize(markets: List<ShoppingMarket>, activeMarketId: String?): State {
        val normalized = markets
            .map(::normalize)
            .distinctBy { it.id }
            .ifEmpty { listOf(create(DEFAULT_MARKET_NAME)) }
        val activeId = activeMarketId
            ?.takeIf { id -> normalized.any { it.id == id } }
            ?: normalized.first().id
        return State(normalized, activeId)
    }

    enum class NameError { EMPTY, DUPLICATE }

    /** Validates a market name; [excludeId] is the market being renamed. */
    fun validateName(
        name: String,
        markets: List<ShoppingMarket>,
        excludeId: String? = null
    ): NameError? {
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return NameError.EMPTY
        val duplicate = markets.any {
            it.id != excludeId && it.name.equals(normalized, ignoreCase = true)
        }
        return if (duplicate) NameError.DUPLICATE else null
    }

    /**
     * Appends a new market. With [templateId] it starts as a copy of that market,
     * otherwise with the default order and all categories enabled.
     */
    fun add(
        markets: List<ShoppingMarket>,
        name: String,
        templateId: String?,
        id: String = newId()
    ): List<ShoppingMarket> {
        val template = templateId?.let { templateId -> markets.firstOrNull { it.id == templateId } }
        val market = if (template != null) {
            create(name, id, template.areaOrder, template.enabledAreas)
        } else {
            create(name, id)
        }
        return markets + market
    }

    fun rename(markets: List<ShoppingMarket>, id: String, name: String): List<ShoppingMarket> =
        markets.map { if (it.id == id) normalize(it.copy(name = name)) else it }

    /** The last remaining market is never removed. */
    fun delete(markets: List<ShoppingMarket>, id: String): List<ShoppingMarket> =
        if (markets.size <= 1) markets else markets.filterNot { it.id == id }

    /** Applies [transform] to the market with [id]. */
    fun update(
        markets: List<ShoppingMarket>,
        id: String,
        transform: (ShoppingMarket) -> ShoppingMarket
    ): List<ShoppingMarket> = markets.map { if (it.id == id) transform(it) else it }

    fun encode(markets: List<ShoppingMarket>): String {
        val array = JSONArray()
        markets.forEach { market ->
            array.put(
                JSONObject()
                    .put("id", market.id)
                    .put("name", market.name)
                    .put("order", JSONArray(market.areaOrder.map { it.key }))
                    .put("enabled", JSONArray(market.enabledAreas.map { it.key }))
            )
        }
        return array.toString()
    }

    /** Tolerant parsing: malformed entries are skipped, unknown category keys are ignored. */
    fun decode(raw: String?): List<ShoppingMarket> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val ordered = ShoppingArea.orderedFromStorage(item.optJSONArray("order").joinKeys())
            normalize(
                ShoppingMarket(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    areaOrder = ordered,
                    enabledAreas = ShoppingArea.enabledFromStorage(
                        item.optJSONArray("enabled").joinKeys(),
                        ordered
                    )
                )
            )
        }.distinctBy { it.id }
    }

    private fun JSONArray?.joinKeys(): String {
        if (this == null) return ""
        return (0 until length()).mapNotNull { optString(it).takeIf { key -> key.isNotBlank() } }
            .joinToString(",")
    }
}
