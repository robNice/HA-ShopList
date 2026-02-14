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
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore


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

    val items = remember { mutableStateListOf("Milch", "Brot") }
    var newItem by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("settings")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            Row {
                TextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(
                        t(
                                R.string.new_item
                        )
                    ) }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    if (newItem.isNotBlank()) {
                        items.add(newItem)
                        newItem = ""
                    }
                }) {
                    Text(t(R.string.add))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            items.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

