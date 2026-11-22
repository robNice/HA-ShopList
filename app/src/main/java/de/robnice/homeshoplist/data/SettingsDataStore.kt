package de.robnice.homeshoplist.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.robnice.homeshoplist.model.ListMarketLinks
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.ShoppingLists
import de.robnice.homeshoplist.model.ShoppingMarket
import de.robnice.homeshoplist.model.ShoppingMarkets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(MarketsMigration) }
)

/** Keys shared by [SettingsDataStore] and [MarketsMigration]. */
internal object MarketKeys {
    /** Category settings before markets existed; only read by the migration. */
    val LEGACY_AREA_ORDER = stringPreferencesKey("area_order")
    val LEGACY_ENABLED_AREAS = stringPreferencesKey("enabled_areas")
    val MARKETS = stringPreferencesKey("markets")
    val ACTIVE_MARKET_ID = stringPreferencesKey("active_market_id")
}

/** Keys needed for the store of each list (see [ListMarketLinks]). */
internal object ListKeys {
    val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    val TODO_ENTITY = stringPreferencesKey("todo_entity")
    val LIST_MARKET_LINKS = stringPreferencesKey("list_market_links")
}

/**
 * Turns the single category set of older app versions into the market "Default".
 * Runs exactly once, on the first access of the DataStore after the update (and on a fresh install).
 */
internal object MarketsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[MarketKeys.MARKETS] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val market = ShoppingMarkets.fromLegacy(
            currentData[MarketKeys.LEGACY_AREA_ORDER],
            currentData[MarketKeys.LEGACY_ENABLED_AREAS]
        )
        return currentData.toMutablePreferences().apply {
            this[MarketKeys.MARKETS] = ShoppingMarkets.encode(listOf(market))
            this[MarketKeys.ACTIVE_MARKET_ID] = market.id
            remove(MarketKeys.LEGACY_AREA_ORDER)
            remove(MarketKeys.LEGACY_ENABLED_AREAS)
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

internal fun Preferences.readMarketState(): ShoppingMarkets.State =
    ShoppingMarkets.sanitize(
        ShoppingMarkets.decode(this[MarketKeys.MARKETS]),
        this[MarketKeys.ACTIVE_MARKET_ID]
    )

/**
 * Stores the markets. The active market is not chosen freely: it is always the market of the
 * current list (see [syncActiveMarketWithList]).
 */
internal fun MutablePreferences.writeMarketState(markets: List<ShoppingMarket>): ShoppingMarkets.State {
    val sanitized = ShoppingMarkets.sanitize(markets, null)
    this[MarketKeys.MARKETS] = ShoppingMarkets.encode(sanitized.markets)
    // Links to deleted markets are dropped; those lists fall back to the first market
    this[ListKeys.LIST_MARKET_LINKS]?.let { raw ->
        this[ListKeys.LIST_MARKET_LINKS] = ListMarketLinks.encode(
            ListMarketLinks.prune(ListMarketLinks.decode(raw), sanitized.markets.map { it.id }.toSet())
        )
    }
    return syncActiveMarketWithList()
}

/** The active market is the market of the current list, or the first market if it has none. */
internal fun MutablePreferences.syncActiveMarketWithList(): ShoppingMarkets.State {
    val markets = ShoppingMarkets.decode(this[MarketKeys.MARKETS])
    val linked = currentListKey()?.let { readListMarketLinks()[it] }
    val state = ShoppingMarkets.sanitize(markets, linked ?: markets.firstOrNull()?.id)
    this[MarketKeys.ACTIVE_MARKET_ID] = state.activeMarketId
    return state
}

/** Market of a list: its own one, or the first market. */
internal fun Preferences.marketIdForList(listKey: String): String {
    val state = readMarketState()
    return readListMarketLinks()[listKey] ?: state.markets.first().id
}

/** Links to existing markets only. */
internal fun Preferences.readListMarketLinks(): Map<String, String> =
    ListMarketLinks.prune(
        ListMarketLinks.decode(this[ListKeys.LIST_MARKET_LINKS]),
        readMarketState().markets.map { it.id }.toSet()
    )

internal fun Preferences.currentListKey(): String? =
    ListMarketLinks.listKey(this[ListKeys.OFFLINE_MODE] == true, this[ListKeys.TODO_ENTITY])

internal fun MutablePreferences.writeListMarketLinks(links: Map<String, String>) {
    this[ListKeys.LIST_MARKET_LINKS] = ListMarketLinks.encode(links)
}

/** Sets or removes the market of one list; for the current list the market switches right away. */
internal fun MutablePreferences.setListMarketLink(listKey: String, marketId: String?) {
    val markets = readMarketState().markets
    val validMarketId = marketId?.takeIf { id -> markets.any { it.id == id } }
    writeListMarketLinks(ListMarketLinks.with(readListMarketLinks(), listKey, validMarketId))
    syncActiveMarketWithList()
}

class SettingsDataStore(private val context: Context) {

    data class BackupSettings(
        val haUrl: String,
        val haToken: String,
        val markets: List<ShoppingMarket>,
        val activeMarketId: String,
        val listMarketLinks: Map<String, String>
    )

    companion object {
        private val HA_URL = stringPreferencesKey("ha_url")
        private val HA_TOKEN = stringPreferencesKey("ha_token")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val OFFLINE_MODE = ListKeys.OFFLINE_MODE
        private val TODO_ENTITY = ListKeys.TODO_ENTITY
        private val TODO_LIST_NAME = stringPreferencesKey("todo_list_name")
        /** Last loaded Home Assistant lists, so the list menu also works without a connection. */
        private val KNOWN_LISTS = stringPreferencesKey("known_lists")
        private val LAST_USED_AREA = stringPreferencesKey("last_used_area")
        private val LIST_DISPLAY_MODE = stringPreferencesKey("list_display_mode")
        private val UPDATE_LAST_CHECK_MILLIS = longPreferencesKey("update_last_check_millis")
        private val UPDATE_VERSION_NAME = stringPreferencesKey("update_version_name")
        private val UPDATE_TAG_NAME = stringPreferencesKey("update_tag_name")
        private val UPDATE_APK_URL = stringPreferencesKey("update_apk_url")
        private val UPDATE_RELEASE_URL = stringPreferencesKey("update_release_url")
        private val UPDATE_CHANGELOG = stringPreferencesKey("update_changelog")

    }

    val notificationsEnabled = context.dataStore.data
        .map { it[KEY_NOTIFICATIONS] ?: true }
    /** Offline list: the list is kept only on this device, no Home Assistant involved. */
    val offlineMode: Flow<Boolean> = context.dataStore.data
        .map { it[OFFLINE_MODE] ?: false }

    val haUrl: Flow<String> = context.dataStore.data
        .map { it[HA_URL] ?: "" }

    val haToken: Flow<String> = context.dataStore.data
        .map { it[HA_TOKEN] ?: "" }

    val todoEntity: Flow<String> = context.dataStore.data
        .map { it[TODO_ENTITY] ?: "todo.einkaufsliste" }

    val todoListName: Flow<String> = context.dataStore.data
        .map { it[TODO_LIST_NAME] ?: "" }

    val knownLists: Flow<List<ShoppingList>> = context.dataStore.data
        .map { ShoppingLists.decode(it[KNOWN_LISTS]) }
        .distinctUntilChanged()

    val lastUsedArea: Flow<String> = context.dataStore.data
        .map { it[LAST_USED_AREA] ?: "" }

    private val marketState: Flow<ShoppingMarkets.State> = context.dataStore.data
        .map { it.readMarketState() }
        .distinctUntilChanged()

    /** All markets, never empty. */
    val markets: Flow<List<ShoppingMarket>> = marketState
        .map { it.markets }
        .distinctUntilChanged()

    val activeMarket: Flow<ShoppingMarket> = marketState
        .map { it.activeMarket }
        .distinctUntilChanged()

    /** Remembered store per list key (todo entity or [ListMarketLinks.OFFLINE_LIST_KEY]). */
    val listMarketLinks: Flow<Map<String, String>> = context.dataStore.data
        .map { it.readListMarketLinks() }
        .distinctUntilChanged()

    /** Category order of the active market (comma separated keys). */
    val areaOrder: Flow<String> = activeMarket
        .map { ShoppingArea.serializeOrder(it.areaOrder) }
        .distinctUntilChanged()

    /** Enabled categories of the active market (comma separated keys). */
    val enabledAreas: Flow<String> = activeMarket
        .map { ShoppingArea.serializeEnabledAreas(it.enabledAreas) }
        .distinctUntilChanged()

    val listDisplayMode: Flow<String> = context.dataStore.data
        .map { it[LIST_DISPLAY_MODE] ?: "categorized" }

    val updateLastCheckMillis: Flow<Long> = context.dataStore.data
        .map { it[UPDATE_LAST_CHECK_MILLIS] ?: 0L }

    val updateVersionName: Flow<String> = context.dataStore.data
        .map { it[UPDATE_VERSION_NAME] ?: "" }

    val updateTagName: Flow<String> = context.dataStore.data
        .map { it[UPDATE_TAG_NAME] ?: "" }

    val updateApkUrl: Flow<String> = context.dataStore.data
        .map { it[UPDATE_APK_URL] ?: "" }

    val updateReleaseUrl: Flow<String> = context.dataStore.data
        .map { it[UPDATE_RELEASE_URL] ?: "" }

    val updateChangelog: Flow<String> = context.dataStore.data
        .map { it[UPDATE_CHANGELOG] ?: "" }

    suspend fun saveHaUrl(url: String) {
        context.dataStore.edit {
            it[HA_URL] = url
        }
    }

    suspend fun saveHaToken(token: String) {
        context.dataStore.edit {
            it[HA_TOKEN] = token
        }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit {
            val changed = (it[OFFLINE_MODE] ?: false) != enabled
            it[OFFLINE_MODE] = enabled
            // Switching between offline list and Home Assistant list is a list switch as well
            if (changed) it.syncActiveMarketWithList()
        }
    }

    suspend fun setListMarketLink(listKey: String, marketId: String?) {
        context.dataStore.edit { it.setListMarketLink(listKey, marketId) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun saveTodoEntity(entity: String) {
        context.dataStore.edit {
            it[TODO_ENTITY] = entity
        }
    }

    suspend fun saveTodoListName(name: String) {
        context.dataStore.edit {
            it[TODO_LIST_NAME] = name
        }
    }

    /** Switches the list in one step, so the repository is rebuilt only once. */
    suspend fun selectTodoList(list: ShoppingList) {
        context.dataStore.edit {
            it[TODO_ENTITY] = list.id
            it[TODO_LIST_NAME] = list.name
            it.syncActiveMarketWithList()
        }
    }

    /**
     * Stores the lists just loaded from Home Assistant and makes sure the selected list is one of them
     * (see [ShoppingLists.reconcile]). Returns the newly selected list if the selection changed.
     */
    suspend fun applyAvailableLists(available: List<ShoppingList>): ShoppingList? {
        var switchedTo: ShoppingList? = null
        context.dataStore.edit { preferences ->
            if (available.isNotEmpty()) {
                preferences[KNOWN_LISTS] = ShoppingLists.encode(available)
            }
            val target = ShoppingLists.reconcile(
                preferences[TODO_ENTITY],
                preferences[TODO_LIST_NAME],
                available
            ) ?: return@edit
            val switched = target.id != preferences[TODO_ENTITY]
            preferences[TODO_ENTITY] = target.id
            preferences[TODO_LIST_NAME] = target.name
            if (switched) {
                switchedTo = target
                preferences.syncActiveMarketWithList()
            }
        }
        return switchedTo
    }

    suspend fun saveKnownLists(lists: List<ShoppingList>) {
        context.dataStore.edit {
            it[KNOWN_LISTS] = ShoppingLists.encode(lists)
        }
    }

    suspend fun saveLastUsedArea(areaKey: String) {
        context.dataStore.edit {
            it[LAST_USED_AREA] = areaKey
        }
    }

    /**
     * Replaces all markets atomically; invariants are enforced by [ShoppingMarkets.sanitize].
     * Lists whose market was deleted fall back to the first market.
     */
    suspend fun saveMarkets(markets: List<ShoppingMarket>) {
        context.dataStore.edit { it.writeMarketState(markets) }
    }

    suspend fun saveListDisplayMode(mode: String) {
        context.dataStore.edit {
            it[LIST_DISPLAY_MODE] = mode
        }
    }

    suspend fun saveUpdateCheckTimestamp(timestampMillis: Long) {
        context.dataStore.edit {
            it[UPDATE_LAST_CHECK_MILLIS] = timestampMillis
        }
    }

    suspend fun saveAvailableUpdate(
        versionName: String,
        tagName: String,
        apkUrl: String,
        releaseUrl: String,
        changelog: String
    ) {
        context.dataStore.edit {
            it[UPDATE_VERSION_NAME] = versionName
            it[UPDATE_TAG_NAME] = tagName
            it[UPDATE_APK_URL] = apkUrl
            it[UPDATE_RELEASE_URL] = releaseUrl
            it[UPDATE_CHANGELOG] = changelog
        }
    }

    suspend fun clearAvailableUpdate() {
        context.dataStore.edit {
            it.remove(UPDATE_VERSION_NAME)
            it.remove(UPDATE_TAG_NAME)
            it.remove(UPDATE_APK_URL)
            it.remove(UPDATE_RELEASE_URL)
            it.remove(UPDATE_CHANGELOG)
        }
    }

    suspend fun readBackupSettings(): BackupSettings {
        val preferences = context.dataStore.data.first()
        val marketState = preferences.readMarketState()
        return BackupSettings(
            haUrl = preferences[HA_URL].orEmpty(),
            haToken = preferences[HA_TOKEN].orEmpty(),
            markets = marketState.markets,
            activeMarketId = marketState.activeMarketId,
            listMarketLinks = preferences.readListMarketLinks()
        )
    }

    /** Imported markets replace all existing ones, including the remembered store per list. */
    data class ImportedMarkets(
        val state: ShoppingMarkets.State,
        val listMarketLinks: Map<String, String>
    )

    suspend fun importBackupSettings(
        connection: Pair<String, String>?,
        markets: ImportedMarkets?
    ) {
        context.dataStore.edit { preferences ->
            connection?.let { (url, token) ->
                preferences[HA_URL] = url
                preferences[HA_TOKEN] = token
            }
            markets?.let {
                preferences.writeListMarketLinks(it.listMarketLinks)
                preferences.writeMarketState(it.state.markets)
            }
        }
    }
}
