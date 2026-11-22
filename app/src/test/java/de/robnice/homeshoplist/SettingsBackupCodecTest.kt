package de.robnice.homeshoplist

import de.robnice.homeshoplist.data.backup.BackupCategories
import de.robnice.homeshoplist.data.backup.BackupConnection
import de.robnice.homeshoplist.data.backup.BackupMarket
import de.robnice.homeshoplist.data.backup.BackupSection
import de.robnice.homeshoplist.data.backup.SettingsBackupCodec
import de.robnice.homeshoplist.data.backup.SettingsBackupException
import de.robnice.homeshoplist.data.backup.SettingsBackupPayload
import de.robnice.homeshoplist.data.history.ProductHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    private val payload = SettingsBackupPayload(
        connection = BackupConnection("https://ha.example", "secret-token"),
        categories = BackupCategories(
            markets = listOf(
                BackupMarket("Default", listOf("produce", "bakery"), listOf("produce")),
                BackupMarket("Aldi", listOf("bakery", "produce"), listOf("bakery", "produce"))
            ),
            activeIndex = 1
        ),
        productHistory = listOf(ProductHistoryEntity("apfel", "Apfel", "produce", 3, 1234L))
    )

    @Test
    fun roundTripPreservesAllSections() {
        val encrypted = SettingsBackupCodec.encrypt(payload, "correct horse".toCharArray())
        val restored = SettingsBackupCodec.decrypt(encrypted, "correct horse".toCharArray())

        assertEquals(BackupSection.entries.toSet(), restored.sections)
        assertEquals(payload, restored)
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("secret-token"))
    }

    @Test
    fun saltAndNonceMakeEveryExportDifferent() {
        val first = SettingsBackupCodec.encrypt(payload, "same password".toCharArray())
        val second = SettingsBackupCodec.encrypt(payload, "same password".toCharArray())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test(expected = SettingsBackupException::class)
    fun wrongPasswordIsRejected() {
        val encrypted = SettingsBackupCodec.encrypt(payload, "right".toCharArray())
        SettingsBackupCodec.decrypt(encrypted, "wrong".toCharArray())
    }

    @Test(expected = SettingsBackupException::class)
    fun tamperedCiphertextIsRejected() {
        val encrypted = SettingsBackupCodec.encrypt(payload, "password".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        SettingsBackupCodec.decrypt(encrypted, "password".toCharArray())
    }

    @Test
    fun onlyPresentSectionsAreOffered() {
        val categoriesOnly = payload.copy(connection = null, productHistory = null)
        val encrypted = SettingsBackupCodec.encrypt(categoriesOnly, "password".toCharArray())
        val restored = SettingsBackupCodec.decrypt(encrypted, "password".toCharArray())
        assertEquals(setOf(BackupSection.CATEGORIES), restored.sections)
        assertTrue(restored.productHistory == null)
    }

    @Test
    fun exportKeepsActiveMarketCategoriesForOlderAppVersions() {
        val json = org.json.JSONObject(SettingsBackupCodec.toJson(payload))
        val categories = json.getJSONObject("categories")

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(listOf("bakery", "produce"), categories.getJSONArray("order").let { a -> List(a.length()) { a.getString(it) } })
        assertEquals(2, categories.getJSONArray("markets").length())
        assertEquals(1, categories.getInt("activeMarket"))
    }

    @Test
    fun backupWithoutMarketsBecomesDefaultMarket() {
        val restored = SettingsBackupCodec.fromJson(
            """{"schemaVersion":1,"categories":{"order":["frozen","produce"],"enabled":["frozen"]}}"""
        )

        val categories = restored.categories!!
        assertEquals(
            listOf(BackupMarket("Default", listOf("frozen", "produce"), listOf("frozen"))),
            categories.markets
        )
        assertEquals(0, categories.activeIndex)
    }

    @Test
    fun invalidActiveMarketIndexFallsBackToFirst() {
        val restored = SettingsBackupCodec.fromJson(
            """{"schemaVersion":1,"categories":{"order":[],"enabled":[],"activeMarket":7,
               "markets":[{"name":"A","order":["produce"],"enabled":["produce"]}]}}"""
        )
        assertEquals(0, restored.categories!!.activeIndex)
    }

    @Test
    fun rememberedStorePerListSurvivesRoundTrip() {
        val withLinks = payload.copy(
            categories = payload.categories!!.copy(
                listLinks = mapOf("todo.a" to 1, "local:offline_list" to 0)
            )
        )
        val restored = SettingsBackupCodec.fromJson(SettingsBackupCodec.toJson(withLinks))
        assertEquals(withLinks.categories, restored.categories)
    }
}
