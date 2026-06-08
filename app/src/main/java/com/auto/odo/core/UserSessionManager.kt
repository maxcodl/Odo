package com.auto.odo.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class UserSessionManager(private val context: Context) {
    companion object {
        private val CURRENT_VEHICLE_ID = longPreferencesKey("current_vehicle_id")
    }

    val currentVehicleId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_VEHICLE_ID]
    }

    suspend fun setCurrentVehicleId(vehicleId: Long) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_VEHICLE_ID] = vehicleId
        }
    }
}
