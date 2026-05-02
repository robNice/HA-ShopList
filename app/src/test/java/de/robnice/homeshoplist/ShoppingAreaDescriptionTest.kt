package de.robnice.homeshoplist

import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.buildDescriptionWithArea
import de.robnice.homeshoplist.model.isNativeWayMeta
import de.robnice.homeshoplist.model.parseAreaFromDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingAreaDescriptionTest {

    @Test
    fun parsesValidAreaJson() {
        val description = """{"type":"ha-shoplist-meta","version":1,"area":"dairy_eggs"}"""

        assertEquals(ShoppingArea.DAIRY_EGGS, parseAreaFromDescription(description))
        assertTrue(isNativeWayMeta(description))
    }

    @Test
    fun ignoresBrokenJson() {
        assertNull(parseAreaFromDescription("{broken"))
        assertFalse(isNativeWayMeta("{broken"))
    }

    @Test
    fun ignoresUnknownAreaKeys() {
        val description = """{"type":"ha-shoplist-meta","version":1,"area":"unknown"}"""

        assertNull(parseAreaFromDescription(description))
        assertTrue(isNativeWayMeta(description))
    }

    @Test
    fun preservesExistingPlainDescription() {
        val description = buildDescriptionWithArea("already there", ShoppingArea.DRINKS)

        assertTrue(description!!.contains("already there"))
        assertEquals(ShoppingArea.DRINKS, parseAreaFromDescription(description))
    }

    @Test
    fun preservesEmbeddedTextWhenAreaIsCleared() {
        val existing = """{"type":"ha-shoplist-meta","version":1,"area":"other","text":"keep"}"""

        assertEquals("keep", buildDescriptionWithArea(existing, null))
    }

    @Test
    fun keepsForeignDescriptionWhenAreaIsCleared() {
        assertEquals("note", buildDescriptionWithArea("note", null))
    }
}
