package de.robnice.homeasssistant_shoppinglist.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val HA_URL = stringPreferencesKey("ha_url")
        private val HA_TOKEN = stringPreferencesKey("ha_token")
    }

    val haUrl: Flow<String> = context.dataStore.data
        .map { it[HA_URL] ?: "" }

    val haToken: Flow<String> = context.dataStore.data
        .map { it[HA_TOKEN] ?: "" }

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
}
