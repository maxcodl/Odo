package com.auto.odo.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "Bike" or "Car"
    val fuelUnit: String, // "Liters" or "Gallons"
    val distanceUnit: String, // "km" or "miles"
    val currency: String, // "INR", "USD", etc.
    val createdAt: Long = System.currentTimeMillis()
)
