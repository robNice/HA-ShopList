package de.robnice.homeasssistant_shoppinglist

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import de.robnice.homeasssistant_shoppinglist.ui.navigation.Screen
import de.robnice.homeasssistant_shoppinglist.ui.screens.SettingsScreen
import de.robnice.homeasssistant_shoppinglist.ui.theme.HomeAsssistantShoppingListTheme
import de.robnice.homeasssistant_shoppinglist.ui.util.t
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.robnice.homeasssistant_shoppinglist.data.HaRepository
import de.robnice.homeasssistant_shoppinglist.data.HaServiceFactory
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import de.robnice.homeasssistant_shoppinglist.viewmodel.ShoppingViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeAsssistantShoppingListTheme {

                val navController = rememberNavController()
                val context = LocalContext.current
                val dataStore = remember { SettingsDataStore(context) }

                val haUrl by dataStore.haUrl.collectAsState(initial = "")
                val haToken by dataStore.haToken.collectAsState(initial = "")

                val startDestination = if (haUrl.isBlank() || haToken.isBlank()) {
                    Screen.Settings.route
                } else {
                    Screen.Shopping.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {

                    composable(Screen.Shopping.route) {
                        ShoppingScreen(navController)
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(navController)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }

    val haUrl by dataStore.haUrl.collectAsState(initial = "")
    val haToken by dataStore.haToken.collectAsState(initial = "")

    if (haUrl.isBlank() || haToken.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Missing configuration")
        }
        return
    }

    val api = remember(haUrl) {
        HaServiceFactory.create(haUrl)
    }

    val repository = remember(haToken) {
        HaRepository(api, haToken)
    }

    val viewModel = remember {
        ShoppingViewModel(repository)
    }

    val items by viewModel.items.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    var newItem by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = t(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error != null -> {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                items.isEmpty() -> {
                    Text(
                        text = t(R.string.no_items),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {

                        Row {
                            OutlinedTextField(
                                value = newItem,
                                onValueChange = { newItem = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Neues Item") }
                            )

                            Spacer(Modifier.width(8.dp))

                            Button(onClick = {
                                if (newItem.isNotBlank()) {
                                    viewModel.addItem(newItem)
                                    newItem = ""
                                }
                            }) {
                                Text("Add")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        items.forEach { item ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Checkbox(
                                    checked = item.complete,
                                    onCheckedChange = {
                                        viewModel.toggleItem(item)
                                    }
                                )

                                Text(
                                    text = item.name,
                                    modifier = Modifier.weight(1f)
                                )

                            }
                        }


                        val hasCompleted = items.any { it.complete }

                        if (hasCompleted) {
                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { viewModel.clearCompleted() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text( t(R.string.clear_completed) )
                            }
                        }


                    }
                }
            }
        }
    }
}



