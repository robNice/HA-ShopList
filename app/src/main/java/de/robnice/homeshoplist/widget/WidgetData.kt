package de.robnice.homeshoplist.widget

import android.content.Context
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.data.HaRuntime
import de.robnice.homeshoplist.data.ListConfig
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.ShoppingRepository
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.model.ShoppingListDisplayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One line of the widget list. */
sealed interface WidgetRow {
    val key: Long

    data class Header(val area: ShoppingArea) : WidgetRow {
        override val key: Long = ("h:" + area.key).hashCode().toLong()
    }

    data class Item(val item: ShoppingItem) : WidgetRow {
        override val key: Long = ("i:" + item.id).hashCode().toLong()
    }

    data class CompletedHeader(val count: Int) : WidgetRow {
        override val key: Long = "completed".hashCode().toLong()
    }
}

/** Everything a widget shows, independent of its layout. */
data class WidgetData(
    val status: Status,
    val listTitle: String = "",
    val offlineList: Boolean = false,
    val openCount: Int = 0,
    val rows: List<WidgetRow> = emptyList(),
    val selectedArea: ShoppingArea = ShoppingArea.OTHER
) {
    enum class Status { NOT_CONFIGURED, LOADING, AUTH_FAILED, READY }

    companion object {
        val Initial = WidgetData(Status.LOADING)
    }
}

object WidgetDataSource {

    private data class Settings(
        val enabledAreas: List<ShoppingArea>,
        val orderedAreas: List<ShoppingArea>,
        val categorized: Boolean,
        val lastUsedArea: String
    )

    private data class RepoState(
        val items: List<ShoppingItem>,
        val loaded: Boolean,
        val authFailed: Boolean
    )

    /**
     * Live widget content. Opens the repository of the current list if the app has none yet;
     * a repository opened only for the widgets releases its connection again (see [WidgetConnection]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(context: Context): Flow<WidgetData> {
        val appContext = context.applicationContext
        val dataStore = SettingsDataStore(appContext)
        val settings = combine(
            dataStore.activeMarket,
            dataStore.listDisplayMode,
            dataStore.lastUsedArea
        ) { market, mode, lastArea ->
            Settings(
                enabledAreas = market.enabledAreas,
                orderedAreas = market.areaOrder,
                categorized = ShoppingListDisplayMode.fromStorage(mode) == ShoppingListDisplayMode.CATEGORIZED,
                lastUsedArea = lastArea
            )
        }

        return ListConfig.observe(dataStore).flatMapLatest { config ->
            val repo = WidgetConnection.obtain(appContext, config)
                ?: return@flatMapLatest flowOf(WidgetData(WidgetData.Status.NOT_CONFIGURED))
            val title = listTitle(appContext, config)
            val repoState = combine(repo.items, repo.loaded, repo.authFailed) { items, loaded, authFailed ->
                RepoState(items, loaded, authFailed)
            }
            combine(repoState, settings) { state, s ->
                build(state, s, title, config.offlineMode)
            }
        }
    }

    private fun build(state: RepoState, settings: Settings, title: String, offlineList: Boolean): WidgetData {
        val selectedArea = ShoppingArea.fromKey(settings.lastUsedArea)
            ?.takeIf { it in settings.enabledAreas }
            ?: settings.enabledAreas.firstOrNull()
            ?: ShoppingArea.OTHER
        val status = when {
            state.authFailed -> WidgetData.Status.AUTH_FAILED
            !state.loaded && state.items.isEmpty() -> WidgetData.Status.LOADING
            else -> WidgetData.Status.READY
        }
        val open = state.items.filter { !it.complete }
        val completed = state.items.filter { it.complete }
        val rows = buildList {
            if (settings.categorized) {
                // Same grouping as the app: categories in the order of the store of this list
                val grouped = LinkedHashMap<ShoppingArea, MutableList<ShoppingItem>>()
                settings.orderedAreas.forEach { grouped[it] = mutableListOf() }
                open.forEach { item -> grouped.getOrPut(item.area ?: ShoppingArea.OTHER) { mutableListOf() }.add(item) }
                grouped.forEach { (area, items) ->
                    if (items.isNotEmpty()) {
                        add(WidgetRow.Header(area))
                        items.forEach { add(WidgetRow.Item(it)) }
                    }
                }
            } else {
                open.forEach { add(WidgetRow.Item(it)) }
            }
            if (completed.isNotEmpty()) {
                add(WidgetRow.CompletedHeader(completed.size))
                completed.forEach { add(WidgetRow.Item(it)) }
            }
        }
        return WidgetData(
            status = status,
            listTitle = title,
            offlineList = offlineList,
            openCount = open.size,
            rows = rows,
            selectedArea = selectedArea
        )
    }

    fun listTitle(context: Context, config: ListConfig): String = when {
        config.offlineMode -> context.getString(R.string.offline_list_title)
        config.todoListName.isNotBlank() -> config.todoListName
        else -> config.todoEntity.substringAfter("todo.").replace('_', ' ').trim()
            .replaceFirstChar { it.titlecase() }
    }
}

/**
 * Connection handling for the widgets. A repository opened or woken up for a widget connects
 * for a short while (load, send changes) and then behaves like the app in the background:
 * no automatic reconnects, and disconnected if notifications are off.
 */
object WidgetConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var releaseJob: Job? = null

    fun obtain(appContext: Context, config: ListConfig): ShoppingRepository? =
        HaRuntime.obtain(appContext, config) { scheduleRelease(appContext) }

    /** Repository of the current list, connected; null if the app is not set up. */
    suspend fun connect(context: Context): ShoppingRepository? {
        val appContext = context.applicationContext
        val config = ListConfig.observe(SettingsDataStore(appContext)).first()
        val repo = obtain(appContext, config) ?: return null
        repo.setReconnectAllowed(true)
        repo.ensureConnected()
        scheduleRelease(appContext)
        return repo
    }

    fun connectInBackground(context: Context) {
        val appContext = context.applicationContext
        scope.launch { connect(appContext) }
    }

    /** Like [connect], and waits (briefly) until the list is loaded. */
    suspend fun connectLoaded(context: Context, timeoutMillis: Long = 10_000L): ShoppingRepository? {
        val repo = connect(context) ?: return null
        withTimeoutOrNull(timeoutMillis) { repo.loaded.first { it } }
        return repo
    }

    @Synchronized
    private fun scheduleRelease(appContext: Context) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(RELEASE_AFTER_MILLIS)
            if (HaRuntime.uiVisible) return@launch
            val repo = HaRuntime.repository ?: return@launch
            repo.setReconnectAllowed(false)
            if (!SettingsDataStore(appContext).notificationsEnabled.first()) {
                repo.disconnect()
            }
        }
    }

    private const val RELEASE_AFTER_MILLIS = 60_000L
}
