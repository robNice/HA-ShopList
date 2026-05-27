package de.robnice.homeshoplist.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.wear.compose.material.MaterialTheme
import de.robnice.homeshoplist.wear.tile.TileRefreshWorker
import de.robnice.homeshoplist.wear.ui.WearApp

class WearMainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()
    private val settingsStore by lazy { WearSettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TileRefreshWorker.schedule(this)
        setContent {
            MaterialTheme {
                WearApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsStore.setAppForeground(true)
    }

    override fun onPause() {
        super.onPause()
        settingsStore.setAppForeground(false)
    }
}
