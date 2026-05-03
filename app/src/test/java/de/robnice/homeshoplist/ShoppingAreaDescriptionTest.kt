package de.robnice.homeshoplist

import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.encodeMetaItemName
import de.robnice.homeshoplist.model.isMetaItemName
import de.robnice.homeshoplist.model.parseMetaItemName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingAreaDescriptionTest {

    @Test
    fun `parse meta item name with known areas`() {
        val name = """.__ha_shoplist_meta__:{"type":"ha-shoplist-meta","version":1,"areas":{"uid-1":"dairy_eggs","uid-2":"drinks"}}"""

        val meta = parseMetaItemName(name)

        assertEquals(ShoppingArea.DAIRY_EGGS, meta?.itemAreas?.get("uid-1"))
        assertEquals(ShoppingArea.DRINKS, meta?.itemAreas?.get("uid-2"))
        assertTrue(isMetaItemName(name))
    }

    @Test
    fun `ignore broken meta item names`() {
        assertNull(parseMetaItemName(".__ha_shoplist_meta__:{broken"))
        assertFalse(isMetaItemName(".__ha_shoplist_meta__:{broken"))
    }

    @Test
    fun `ignore unknown areas in meta item names`() {
        val name = """.__ha_shoplist_meta__:{"type":"ha-shoplist-meta","version":1,"areas":{"uid-1":"unknown"}}"""

        val meta = parseMetaItemName(name)

        assertTrue(isMetaItemName(name))
        assertTrue(meta?.itemAreas?.isEmpty() == true)
    }

    @Test
    fun `encode meta item names deterministically`() {
        val name = encodeMetaItemName(
            mapOf(
                "uid-2" to ShoppingArea.DRINKS,
                "uid-1" to ShoppingArea.DAIRY_EGGS
            )
        )

        assertEquals(
            """.__ha_shoplist_meta__:{"type":"ha-shoplist-meta","version":1,"areas":{"uid-1":"dairy_eggs","uid-2":"drinks"}}""",
            name
        )
    }
}
