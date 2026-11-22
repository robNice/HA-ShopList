package de.robnice.homeshoplist

import de.robnice.homeshoplist.data.SharedPreferencesPendingSyncStore
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListsTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val lists = listOf(ShoppingList("todo.a", "A"), ShoppingList("todo.b", "Bäckerei"))
        assertEquals(lists, ShoppingLists.decode(ShoppingLists.encode(lists)))
    }

    @Test
    fun decodeIsTolerant() {
        assertTrue(ShoppingLists.decode(null).isEmpty())
        assertTrue(ShoppingLists.decode("{broken").isEmpty())
        assertEquals(
            listOf(ShoppingList("todo.a", "todo.a")),
            ShoppingLists.decode("""[{"name":"no id"}, {"id":"todo.a"}, {"id":"todo.a","name":"dup"}]""")
        )
    }

    @Test
    fun withCurrentAddsMissingCurrentListAndSortsByName() {
        val known = listOf(ShoppingList("todo.z", "Zoo"), ShoppingList("todo.a", "apotheke"))

        assertEquals(
            listOf("apotheke", "Markt", "Zoo"),
            ShoppingLists.withCurrent(known, ShoppingList("todo.m", "Markt")).map { it.name }
        )
        assertEquals(2, ShoppingLists.withCurrent(known, ShoppingList("todo.a", "x")).size)
        assertEquals(2, ShoppingLists.withCurrent(known, null).size)
    }

    @Test
    fun pendingChangesAreStoredPerList() {
        assertNotEquals(
            SharedPreferencesPendingSyncStore.pendingSyncKey("todo.a"),
            SharedPreferencesPendingSyncStore.pendingSyncKey("todo.b")
        )
    }

    private val available = listOf(ShoppingList("todo.z", "Zoo"), ShoppingList("todo.a", "Apotheke"))

    @Test
    fun reconcilePicksFirstListWhenNoneSelected() {
        assertEquals(ShoppingList("todo.a", "Apotheke"), ShoppingLists.reconcile(null, null, available))
    }

    @Test
    fun reconcileReplacesDeletedOrForeignList() {
        assertEquals("todo.a", ShoppingLists.reconcile("todo.deleted", "Alt", available)?.id)
    }

    @Test
    fun reconcileKeepsValidSelectionAndOnlyUpdatesRenamedList() {
        assertEquals(null, ShoppingLists.reconcile("todo.z", "Zoo", available))
        assertEquals(ShoppingList("todo.z", "Zoo"), ShoppingLists.reconcile("todo.z", "Old name", available))
    }

    @Test
    fun reconcileDoesNothingWithoutAvailableLists() {
        assertEquals(null, ShoppingLists.reconcile("todo.a", "A", emptyList()))
        assertEquals(null, ShoppingLists.reconcile(null, null, emptyList()))
    }
}
