package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.util.Debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** What decides which repository is used: connection, list and offline mode. */
data class ListConfig(
    val haUrl: String,
    val haToken: String,
    val todoEntity: String,
    val todoListName: String,
    val offlineMode: Boolean
) {
    companion object {
        fun observe(dataStore: SettingsDataStore): Flow<ListConfig> =
            combine(
                dataStore.haUrl,
                dataStore.haToken,
                dataStore.todoEntity,
                dataStore.todoListName,
                dataStore.offlineMode
            ) { url, token, entity, listName, offline ->
                ListConfig(url, token, entity, listName, offline)
            }.distinctUntilChanged()
    }
}

/**
 * The one repository of the process, shared by the app screen and the home screen widgets,
 * so both always show (and change) the same list over the same connection.
 */
object HaRuntime {
    private val _repository = MutableStateFlow<ShoppingRepository?>(null)
    val repositoryFlow: StateFlow<ShoppingRepository?> = _repository.asStateFlow()

    var repository: ShoppingRepository?
        get() = _repository.value
        set(value) {
            _repository.value = value
        }
    var offlineMode: Boolean = false
    var baseUrl: String? = null
    var token: String? = null
    var todoEntity: String? = null
    var todoListName: String? = null

    /** True while the app screen is visible; widgets then leave the connection to it. */
    @Volatile
    var uiVisible: Boolean = false

    /**
     * Returns the repository for [config], creating it (and dropping the previous one) when the
     * config changed. Null if there is nothing to show yet: no connection set up or no list chosen.
     * [onCreated] is called when a new repository was created.
     */
    @Synchronized
    fun obtain(
        appContext: Context,
        config: ListConfig,
        onCreated: (ShoppingRepository) -> Unit = {}
    ): ShoppingRepository? {
        if (config.offlineMode) {
            // Offline list: never talk to Home Assistant
            if (!offlineMode || repository == null) {
                Debug.log("HaRuntime: switch to offline list")
                repository?.disconnect()
                offlineMode = true
                repository = LocalShoppingRepository(appContext).also(onCreated)
            }
            return repository
        }

        if (config.haUrl.isBlank() || config.haToken.isBlank() || config.todoEntity.isBlank()) {
            return null
        }

        val repo = repository
        val configChanged =
            repo == null ||
                    offlineMode ||
                    baseUrl != config.haUrl ||
                    token != config.haToken ||
                    todoEntity != config.todoEntity ||
                    todoListName != config.todoListName
        if (!configChanged) {
            return repo
        }

        Debug.log("HaRuntime: recreate repository because config changed")
        repo?.disconnect()
        offlineMode = false
        baseUrl = config.haUrl
        token = config.haToken
        todoEntity = config.todoEntity
        todoListName = config.todoListName
        repository = HaWebSocketRepository(
            config.haUrl,
            config.haToken,
            appContext,
            config.todoEntity,
            config.todoListName
        ).also(onCreated)
        return repository
    }
}
