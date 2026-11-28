package de.robnice.homeshoplist.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import de.robnice.homeshoplist.data.HaRuntime
import de.robnice.homeshoplist.data.ListConfig
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.util.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps placed widgets up to date while the app process lives: every change of the items
 * or of the settings (list, store, display mode, category) redraws them.
 * Connection state is left out on purpose, so releasing a connection never triggers a redraw.
 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    val widgets: List<GlanceAppWidget>
        get() = listOf(ShoppingListInputWidget(), ShoppingListOnlyWidget(), QuickAddWidget(), ListSwitcherWidget())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    @Synchronized
    fun ensureStarted(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        val dataStore = SettingsDataStore(appContext)
        val repoChanges = HaRuntime.repositoryFlow.flatMapLatest { repo ->
            if (repo == null) flowOf(Unit)
            else combine(repo.items, repo.loaded, repo.authFailed) { _, _, _ -> Unit }
        }
        val settingsChanges = merge(
            ListConfig.observe(dataStore).map { },
            dataStore.activeMarket.map { },
            dataStore.listDisplayMode.map { },
            dataStore.lastUsedArea.map { },
            dataStore.knownLists.map { }
        )
        scope.launch {
            merge(repoChanges, settingsChanges)
                .drop(1)
                .debounce(UPDATE_DEBOUNCE_MILLIS)
                .collect { updateAll(appContext) }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        widgets.forEach { widget ->
            runCatching {
                if (manager.getGlanceIds(widget.javaClass).isNotEmpty()) {
                    widget.updateAll(context)
                }
            }.onFailure { Debug.log("WIDGET: update failed: ${it.message}") }
        }
    }

    private const val UPDATE_DEBOUNCE_MILLIS = 500L
}
