package com.auto.odo.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_logs",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId"])]
)
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometer: Double, // Always stored in kilometers
    val quantity: Double, // Always stored in liters
    val pricePerUnit: Double, // Per Liter
    val totalCost: Double,
    val isPartialTank: Boolean,
    val stationName: String?,
    val notes: String?,
    val receiptPath: String?
)
