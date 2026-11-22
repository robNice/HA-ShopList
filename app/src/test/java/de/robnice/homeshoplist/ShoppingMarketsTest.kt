package de.robnice.homeshoplist

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import de.robnice.homeshoplist.data.MarketKeys
import de.robnice.homeshoplist.data.MarketsMigration
import de.robnice.homeshoplist.data.readMarketState
import de.robnice.homeshoplist.data.writeMarketState
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingMarket
import de.robnice.homeshoplist.model.ShoppingMarkets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingMarketsTest {

    @Test
    fun fromLegacyWithoutStoredValuesUsesDefaultsAndName() {
        val market = ShoppingMarkets.fromLegacy("", "")

        assertEquals("Default", market.name)
        assertEquals(ShoppingArea.entries.toList(), market.areaOrder)
        assertEquals(ShoppingArea.enabledByDefault, market.enabledAreas)
    }

    @Test
    fun fromLegacyKeepsCustomOrderAndEnabledAreas() {
        val market = ShoppingMarkets.fromLegacy("bakery,produce,unknown", "produce,bakery")

        assertEquals(listOf(ShoppingArea.BAKERY, ShoppingArea.PRODUCE), market.areaOrder.take(2))
        assertEquals(ShoppingArea.entries.size, market.areaOrder.size)
        assertEquals(listOf(ShoppingArea.BAKERY, ShoppingArea.PRODUCE), market.enabledAreas)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val markets = listOf(
            ShoppingMarkets.fromLegacy("bakery,produce", "produce", id = "a"),
            ShoppingMarkets.create("Aldi", id = "b")
        )

        assertEquals(markets, ShoppingMarkets.decode(ShoppingMarkets.encode(markets)))
    }

    @Test
    fun decodeIsTolerant() {
        assertTrue(ShoppingMarkets.decode(null).isEmpty())
        assertTrue(ShoppingMarkets.decode("not json").isEmpty())

        val decoded = ShoppingMarkets.decode(
            """[42, {"id":"a","name":"  Rewe  ","order":["frozen","nope"],"enabled":["nope"]},
               {"id":"a","name":"duplicate"}, {"name":""}]"""
        )

        assertEquals(2, decoded.size)
        val rewe = decoded[0]
        assertEquals("Rewe", rewe.name)
        assertEquals(ShoppingArea.FROZEN, rewe.areaOrder.first())
        assertEquals(ShoppingArea.entries.size, rewe.areaOrder.size)
        // Only unknown keys enabled -> all enabled, never zero categories
        assertEquals(rewe.areaOrder.filterNot { it in ShoppingArea.disabledByDefault }, rewe.enabledAreas)
        assertEquals("Default", decoded[1].name)
        assertTrue(decoded[1].id.isNotBlank())
    }

    @Test
    fun sanitizeNeverReturnsEmptyListAndFixesActiveId() {
        val empty = ShoppingMarkets.sanitize(emptyList(), "missing")
        assertEquals(1, empty.markets.size)
        assertEquals("Default", empty.activeMarket.name)

        val markets = listOf(ShoppingMarkets.create("A", id = "a"), ShoppingMarkets.create("B", id = "b"))
        assertEquals("a", ShoppingMarkets.sanitize(markets, "missing").activeMarketId)
        assertEquals("b", ShoppingMarkets.sanitize(markets, "b").activeMarketId)
    }

    @Test
    fun normalizeKeepsEnabledAreasInOrderAndNeverEmpty() {
        val market = ShoppingMarkets.normalize(
            ShoppingMarket(
                id = "x",
                name = "x".repeat(100),
                areaOrder = listOf(ShoppingArea.FROZEN, ShoppingArea.BAKERY),
                enabledAreas = listOf(ShoppingArea.BAKERY, ShoppingArea.FROZEN)
            )
        )
        assertEquals(listOf(ShoppingArea.FROZEN, ShoppingArea.BAKERY), market.enabledAreas)
        assertEquals(ShoppingMarkets.MAX_NAME_LENGTH, market.name.length)

        val noneEnabled = ShoppingMarkets.normalize(market.copy(enabledAreas = emptyList()))
        assertEquals(noneEnabled.areaOrder.filterNot { it in ShoppingArea.disabledByDefault }, noneEnabled.enabledAreas)
    }

    @Test
    fun migrationTurnsLegacyCategoriesIntoDefaultMarket() = runTest {
        val legacy = preferencesOf(
            MarketKeys.LEGACY_AREA_ORDER to "frozen,bakery",
            MarketKeys.LEGACY_ENABLED_AREAS to "bakery"
        )
        assertTrue(MarketsMigration.shouldMigrate(legacy))

        val migrated = MarketsMigration.migrate(legacy)
        val state = migrated.readMarketState()

        assertFalse(MarketsMigration.shouldMigrate(migrated))
        assertNull(migrated[MarketKeys.LEGACY_AREA_ORDER])
        assertNull(migrated[MarketKeys.LEGACY_ENABLED_AREAS])
        assertEquals(1, state.markets.size)
        assertEquals(state.markets.single().id, migrated[MarketKeys.ACTIVE_MARKET_ID])
        assertEquals("Default", state.activeMarket.name)
        assertEquals(listOf(ShoppingArea.FROZEN, ShoppingArea.BAKERY), state.activeMarket.areaOrder.take(2))
        assertEquals(listOf(ShoppingArea.BAKERY), state.activeMarket.enabledAreas)
    }

    @Test
    fun migrationOnFreshInstallCreatesDefaultMarketWithAllCategories() = runTest {
        val state = MarketsMigration.migrate(preferencesOf()).readMarketState()

        assertEquals("Default", state.activeMarket.name)
        assertEquals(ShoppingArea.enabledByDefault, state.activeMarket.enabledAreas)
    }

    @Test
    fun writeMarketStateEnforcesInvariants() {
        val preferences = mutablePreferencesOf()
        preferences.writeMarketState(emptyList())

        val state = preferences.readMarketState()
        assertEquals(1, state.markets.size)
        assertEquals(state.markets.single().id, preferences[MarketKeys.ACTIVE_MARKET_ID])
    }

    @Test
    fun addCreatesNewMarketWithDefaultsOrAsCopy() {
        val custom = ShoppingMarkets.fromLegacy("frozen,bakery", "frozen", id = "a")

        val fresh = ShoppingMarkets.add(listOf(custom), " Lidl ", templateId = null, id = "b").last()
        assertEquals("Lidl", fresh.name)
        assertEquals(ShoppingArea.entries.toList(), fresh.areaOrder)
        assertEquals(ShoppingArea.enabledByDefault, fresh.enabledAreas)

        val copy = ShoppingMarkets.add(listOf(custom), "Aldi", templateId = "a", id = "c").last()
        assertEquals("c", copy.id)
        assertEquals(custom.areaOrder, copy.areaOrder)
        assertEquals(custom.enabledAreas, copy.enabledAreas)
    }

    @Test
    fun validateNameRejectsEmptyAndDuplicateIgnoringCase() {
        val markets = listOf(ShoppingMarkets.create("Rewe", id = "a"), ShoppingMarkets.create("Aldi", id = "b"))

        assertEquals(ShoppingMarkets.NameError.EMPTY, ShoppingMarkets.validateName("  ", markets))
        assertEquals(ShoppingMarkets.NameError.DUPLICATE, ShoppingMarkets.validateName(" rewe ", markets))
        assertNull(ShoppingMarkets.validateName("Lidl", markets))
        // Renaming a market to its own name (other case) is fine
        assertNull(ShoppingMarkets.validateName("REWE", markets, excludeId = "a"))
        assertEquals(ShoppingMarkets.NameError.DUPLICATE, ShoppingMarkets.validateName("aldi", markets, excludeId = "a"))
    }

    @Test
    fun renameAndDeleteNeverRemoveTheLastMarket() {
        val markets = listOf(ShoppingMarkets.create("A", id = "a"), ShoppingMarkets.create("B", id = "b"))

        assertEquals("C", ShoppingMarkets.rename(markets, "b", " C ").last().name)

        val one = ShoppingMarkets.delete(markets, "a")
        assertEquals(listOf("b"), one.map { it.id })
        assertEquals(one, ShoppingMarkets.delete(one, "b"))
    }
}
