package de.robnice.homeasssistant_shoppinglist.ui.screens

import de.robnice.homeasssistant_shoppinglist.R
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
import de.robnice.homeasssistant_shoppinglist.ui.util.t
import de.robnice.homeasssistant_shoppinglist.util.normalizeHaUrl

/**
 * @todo: prevent copying token
 * @todo: prevent screenshots
 * @todo: prevent autofill for token field
 */
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

        Text(t(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(t(R.string.ha_url)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))


        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(t(R.string.ha_token)) },
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

                val cleanedUrl = normalizeHaUrl(url)
                val cleanedToken = token.trim()

                dataStore.saveHaUrl(cleanedUrl)
                dataStore.saveHaToken(cleanedToken)

                navController.popBackStack()
            }
        }) {
            Text(t(R.string.save))
        }
    }
}

