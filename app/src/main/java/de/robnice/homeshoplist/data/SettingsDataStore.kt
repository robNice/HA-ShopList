package de.robnice.homeshoplist.data

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
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val TODO_ENTITY = stringPreferencesKey("todo_entity")
        private val LAST_USED_AREA = stringPreferencesKey("last_used_area")
        private val AREA_ORDER = stringPreferencesKey("area_order")
        private val ENABLED_AREAS = stringPreferencesKey("enabled_areas")
        private val LIST_DISPLAY_MODE = stringPreferencesKey("list_display_mode")
        private val UPDATE_LAST_CHECK_MILLIS = longPreferencesKey("update_last_check_millis")
        private val UPDATE_VERSION_NAME = stringPreferencesKey("update_version_name")
        private val UPDATE_TAG_NAME = stringPreferencesKey("update_tag_name")
        private val UPDATE_APK_URL = stringPreferencesKey("update_apk_url")
        private val UPDATE_RELEASE_URL = stringPreferencesKey("update_release_url")
        private val UPDATE_CHANGELOG = stringPreferencesKey("update_changelog")

    }

    val notificationsEnabled = context.dataStore.data
        .map { it[KEY_NOTIFICATIONS] ?: true }
    val haUrl: Flow<String> = context.dataStore.data
        .map { it[HA_URL] ?: "" }

    val haToken: Flow<String> = context.dataStore.data
        .map { it[HA_TOKEN] ?: "" }

    val todoEntity: Flow<String> = context.dataStore.data
        .map { it[TODO_ENTITY] ?: "todo.einkaufsliste" }

    val lastUsedArea: Flow<String> = context.dataStore.data
        .map { it[LAST_USED_AREA] ?: "" }

    val areaOrder: Flow<String> = context.dataStore.data
        .map { it[AREA_ORDER] ?: "" }

    val enabledAreas: Flow<String> = context.dataStore.data
        .map { it[ENABLED_AREAS] ?: "" }

    val listDisplayMode: Flow<String> = context.dataStore.data
        .map { it[LIST_DISPLAY_MODE] ?: "categorized" }

    val updateLastCheckMillis: Flow<Long> = context.dataStore.data
        .map { it[UPDATE_LAST_CHECK_MILLIS] ?: 0L }

    val updateVersionName: Flow<String> = context.dataStore.data
        .map { it[UPDATE_VERSION_NAME] ?: "" }

    val updateTagName: Flow<String> = context.dataStore.data
        .map { it[UPDATE_TAG_NAME] ?: "" }

    val updateApkUrl: Flow<String> = context.dataStore.data
        .map { it[UPDATE_APK_URL] ?: "" }

    val updateReleaseUrl: Flow<String> = context.dataStore.data
        .map { it[UPDATE_RELEASE_URL] ?: "" }

    val updateChangelog: Flow<String> = context.dataStore.data
        .map { it[UPDATE_CHANGELOG] ?: "" }

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

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun saveTodoEntity(entity: String) {
        context.dataStore.edit {
            it[TODO_ENTITY] = entity
        }
    }

    suspend fun saveLastUsedArea(areaKey: String) {
        context.dataStore.edit {
            it[LAST_USED_AREA] = areaKey
        }
    }

    suspend fun saveAreaOrder(areaOrder: String) {
        context.dataStore.edit {
            it[AREA_ORDER] = areaOrder
        }
    }

    suspend fun saveEnabledAreas(enabledAreas: String) {
        context.dataStore.edit {
            it[ENABLED_AREAS] = enabledAreas
        }
    }

    suspend fun saveListDisplayMode(mode: String) {
        context.dataStore.edit {
            it[LIST_DISPLAY_MODE] = mode
        }
    }

    suspend fun saveUpdateCheckTimestamp(timestampMillis: Long) {
        context.dataStore.edit {
            it[UPDATE_LAST_CHECK_MILLIS] = timestampMillis
        }
    }

    suspend fun saveAvailableUpdate(
        versionName: String,
        tagName: String,
        apkUrl: String,
        releaseUrl: String,
        changelog: String
    ) {
        context.dataStore.edit {
            it[UPDATE_VERSION_NAME] = versionName
            it[UPDATE_TAG_NAME] = tagName
            it[UPDATE_APK_URL] = apkUrl
            it[UPDATE_RELEASE_URL] = releaseUrl
            it[UPDATE_CHANGELOG] = changelog
        }
    }

    suspend fun clearAvailableUpdate() {
        context.dataStore.edit {
            it.remove(UPDATE_VERSION_NAME)
            it.remove(UPDATE_TAG_NAME)
            it.remove(UPDATE_APK_URL)
            it.remove(UPDATE_RELEASE_URL)
            it.remove(UPDATE_CHANGELOG)
        }
    }
}
