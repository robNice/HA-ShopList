package de.robnice.homeasssistant_shoppinglist.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import de.robnice.homeasssistant_shoppinglist.data.SettingsDataStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun SettingsScreen(
    navController: NavController,
    context: Context = LocalContext.current
) {

    val dataStore = remember { SettingsDataStore(context) }
    val coroutineScope = rememberCoroutineScope()

    val storedUrl by dataStore.haUrl.collectAsState(initial = "")
    val storedToken by dataStore.haToken.collectAsState(initial = "")
    var tokenVisible by remember { mutableStateOf(false) }

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    LaunchedEffect(storedUrl, storedToken) {
        url = storedUrl
        token = storedToken
    }

    Column(modifier = Modifier.padding(16.dp)) {

        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Home Assistant URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))


        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Long-Lived Token") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (tokenVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (tokenVisible)
                    Icons.Default.Visibility
                else
                    Icons.Default.VisibilityOff

                IconButton(onClick = {
                    tokenVisible = !tokenVisible
                }) {
                    Icon(
                        imageVector = image,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                dataStore.saveHaUrl(url)
                dataStore.saveHaToken(token)
                navController.popBackStack()
            }
        }) {
            Text("Save")
        }
    }
}

