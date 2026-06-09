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

class UserSessionManager(private val context: Context) {
    companion object {
        private val CURRENT_VEHICLE_ID = longPreferencesKey("current_vehicle_id")
        private val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        private val FULL_SCREEN_STATUS_BAR = booleanPreferencesKey("full_screen_status_bar")
        private val AUTO_HIDE_TITLE_BAR = booleanPreferencesKey("auto_hide_title_bar")
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
}
