package com.auto.odo.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

enum class NavBarStyle {
    SOLID, BLURRY, GLASSY
}

enum class AppThemeMode {
    STANDARD, AMOLED, MONET, MONET_AMOLED
}

class UserSessionManager(private val context: Context) {
    companion object {
        private val CURRENT_VEHICLE_ID = longPreferencesKey("current_vehicle_id")
        private val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        private val FULL_SCREEN_STATUS_BAR = booleanPreferencesKey("full_screen_status_bar")
        private val AUTO_HIDE_TITLE_BAR = booleanPreferencesKey("auto_hide_title_bar")
        private val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        
        // NEW: Key for the vehicle icon toggle
        private val SHOW_VEHICLE_ICON = booleanPreferencesKey("show_vehicle_icon")
        private val PUMP_LOOKUP = booleanPreferencesKey("pump_lookup")
        private val AUTO_TRIPS = booleanPreferencesKey("auto_trips")
    }

    /** Fill in the station name from GPS at fill-up time. */
    val pumpLookupEnabled: Flow<Boolean> = context.dataStore.data.map { it[PUMP_LOOKUP] ?: true }

    /** Record trips automatically when driving is detected. Off by default: costs battery. */
    val autoTripsEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_TRIPS] ?: false }

    suspend fun setPumpLookupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PUMP_LOOKUP] = enabled }
    }

    suspend fun setAutoTripsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_TRIPS] = enabled }
    }

    val currentVehicleId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_VEHICLE_ID]
    }

    val navBarStyle: Flow<NavBarStyle> = context.dataStore.data.map { preferences ->
        val styleStr = preferences[NAV_BAR_STYLE] ?: NavBarStyle.SOLID.name
        try { NavBarStyle.valueOf(styleStr) } catch (e: Exception) { NavBarStyle.SOLID }
    }

    val fullScreenStatusBar: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FULL_SCREEN_STATUS_BAR] ?: false
    }

    val autoHideTitleBar: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_HIDE_TITLE_BAR] ?: true
    }

    val appThemeMode: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[APP_THEME_MODE] ?: AppThemeMode.STANDARD.name
        try { AppThemeMode.valueOf(modeStr) } catch (e: Exception) { AppThemeMode.STANDARD }
    }

    // NEW: Flow to read the icon setting, defaults to true
    val showVehicleIcon: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_VEHICLE_ICON] ?: true
    }

    suspend fun setCurrentVehicleId(vehicleId: Long) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun setNavBarStyle(style: NavBarStyle) {
        context.dataStore.edit { preferences ->
            preferences[NAV_BAR_STYLE] = style.name
        }
    }

    suspend fun setFullScreenStatusBar(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FULL_SCREEN_STATUS_BAR] = enabled
        }
    }

    suspend fun setAutoHideTitleBar(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_HIDE_TITLE_BAR] = enabled
        }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME_MODE] = mode.name
        }
    }

    // NEW: Suspend function to update the icon setting
    suspend fun setShowVehicleIcon(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_VEHICLE_ICON] = show
        }
    }
}