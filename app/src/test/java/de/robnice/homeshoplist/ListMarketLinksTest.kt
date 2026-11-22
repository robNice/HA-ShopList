package de.robnice.homeshoplist

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import de.robnice.homeshoplist.data.HaListAdminException
import de.robnice.homeshoplist.data.HaListAdminParser
import de.robnice.homeshoplist.data.ListKeys
import de.robnice.homeshoplist.data.TodoListRegistryInfo
import de.robnice.homeshoplist.data.marketIdForList
import de.robnice.homeshoplist.data.readListMarketLinks
import de.robnice.homeshoplist.data.readMarketState
import de.robnice.homeshoplist.data.syncActiveMarketWithList
import de.robnice.homeshoplist.data.setListMarketLink
import de.robnice.homeshoplist.data.writeMarketState
import de.robnice.homeshoplist.model.ListMarketLinks
import de.robnice.homeshoplist.model.ShoppingMarkets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListMarketLinksTest {

    private fun prefs(entity: String = "todo.a"): MutablePreferences =
        mutablePreferencesOf().apply {
            this[ListKeys.TODO_ENTITY] = entity
            writeMarketState(
                listOf(ShoppingMarkets.create("Rewe", id = "rewe"), ShoppingMarkets.create("Aldi", id = "aldi"))
            )
        }

    @Test
    fun encodeDecodeAndTolerantParsing() {
        val links = mapOf("todo.a" to "rewe", ListMarketLinks.OFFLINE_LIST_KEY to "aldi")
        assertEquals(links, ListMarketLinks.decode(ListMarketLinks.encode(links)))
        assertTrue(ListMarketLinks.decode("nope").isEmpty())
        assertEquals(mapOf("x" to "y"), ListMarketLinks.decode("""{"x":"y","empty":""}"""))
    }

    @Test
    fun listKeyUsesOfflineKeyInOfflineMode() {
        assertEquals(ListMarketLinks.OFFLINE_LIST_KEY, ListMarketLinks.listKey(true, "todo.a"))
        assertEquals("todo.a", ListMarketLinks.listKey(false, " todo.a "))
        assertNull(ListMarketLinks.listKey(false, ""))
    }

    @Test
    fun listWithoutStoreUsesTheFirstStore() {
        val p = prefs()
        assertEquals("rewe", p.readMarketState().activeMarketId)
        assertEquals("rewe", p.marketIdForList("todo.unknown"))
    }

    @Test
    fun storeOfTheCurrentListBecomesActiveAndFollowsListSwitches() {
        val p = prefs()
        p.setListMarketLink("todo.a", "aldi")
        assertEquals("aldi", p.readMarketState().activeMarketId)

        // other list without store -> first store
        p[ListKeys.TODO_ENTITY] = "todo.b"
        p.syncActiveMarketWithList()
        assertEquals("rewe", p.readMarketState().activeMarketId)

        // back to list a -> Aldi again
        p[ListKeys.TODO_ENTITY] = "todo.a"
        p.syncActiveMarketWithList()
        assertEquals("aldi", p.readMarketState().activeMarketId)
    }

    @Test
    fun storeOfAnotherListDoesNotChangeTheActiveStore() {
        val p = prefs()
        p.setListMarketLink("todo.other", "aldi")
        assertEquals("rewe", p.readMarketState().activeMarketId)
        assertEquals("aldi", p.marketIdForList("todo.other"))
    }

    @Test
    fun deletedStoreFallsBackToTheFirstStore() {
        val p = prefs()
        p.setListMarketLink("todo.a", "aldi")
        p.writeMarketState(p.readMarketState().markets.filterNot { it.id == "aldi" })
        assertTrue(p.readListMarketLinks().isEmpty())
        assertEquals("rewe", p.readMarketState().activeMarketId)

        // unknown store ids are not stored
        p.setListMarketLink("todo.x", "unknown")
        assertTrue(p.readListMarketLinks().isEmpty())
    }

    @Test
    fun onlyLocalTodoListsAreDeletable() {
        assertTrue(TodoListRegistryInfo("todo.a", "local_todo", "entry1").isDeletable)
        assertFalse(TodoListRegistryInfo("todo.b", "shopping_list", "entry2").isDeletable)
        assertFalse(TodoListRegistryInfo("todo.c", "local_todo", null).isDeletable)
        assertFalse(TodoListRegistryInfo("todo.d", "mealie", "entry3").isDeletable)
    }

    @Test
    fun parsesRegistryAndAdminResponses() {
        val info = HaListAdminParser.parseRegistryInfo(
            "todo.a",
            JSONObject("""{"entity_id":"todo.a","platform":"local_todo","config_entry_id":"abc"}""")
        )
        assertEquals(TodoListRegistryInfo("todo.a", "local_todo", "abc"), info)
        assertNull(HaListAdminParser.parseRegistryInfo("todo.a", JSONObject("""{"platform":"x","config_entry_id":null}"""))?.configEntryId)

        assertEquals(true, HaListAdminParser.parseIsAdmin(JSONObject("""{"is_admin":true}""")))
        assertEquals(false, HaListAdminParser.parseIsAdmin(JSONObject("""{"is_admin":false}""")))
        assertNull(HaListAdminParser.parseIsAdmin(JSONObject("""{}""")))
    }

    @Test
    fun parsesConfigFlowSteps() {
        assertEquals("f1", HaListAdminParser.parseFlowStart("""{"type":"form","flow_id":"f1","step_id":"user"}"""))
        HaListAdminParser.parseFlowResult("""{"type":"create_entry","flow_id":"f1","title":"Neu"}""")

        val abort = runCatching {
            HaListAdminParser.parseFlowResult("""{"type":"abort","flow_id":"f1","reason":"already_configured"}""")
        }.exceptionOrNull()
        assertTrue(abort is HaListAdminException)
        assertEquals("already_configured", abort?.message)

        val formError = runCatching {
            HaListAdminParser.parseFlowResult("""{"type":"form","flow_id":"f1","errors":{"todo_list_name":"invalid"}}""")
        }.exceptionOrNull()
        assertEquals("invalid", formError?.message)
    }
}
