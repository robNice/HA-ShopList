package de.robnice.homeshoplist.wear

import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.wear.compose.material.MaterialTheme
import de.robnice.homeshoplist.BuildConfig
import de.robnice.homeshoplist.wear.tile.TileRefreshWorker
import de.robnice.homeshoplist.wear.ui.WearApp

class WearMainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // App icon as splash screen while starting (SplashScreen API, as recommended for Wear OS)
        val startedAt = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - startedAt < SPLASH_MIN_MILLIS
        }
        super.onCreate(savedInstanceState)
        runCatching { TileRefreshWorker.schedule(this) }
        // Debug builds only, for the store and help screenshots: opens the category choice for this name
        val demoAddName = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEMO_ADD_NAME) else null
        setContent {
            MaterialTheme {
                WearApp(viewModel = viewModel, demoAddName = demoAddName)
            }
        }
    }

    private companion object {
        /** Short enough not to delay the app noticeably, long enough to recognize the icon. */
        const val SPLASH_MIN_MILLIS = 600L
        const val EXTRA_DEMO_ADD_NAME = "demo_add_name"
    }
}
