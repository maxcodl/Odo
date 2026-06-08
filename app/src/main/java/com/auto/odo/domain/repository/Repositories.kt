package com.auto.odo.domain.repository

import com.auto.odo.data.entity.*
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    suspend fun getVehicleById(id: Long): VehicleEntity?
    suspend fun insertVehicle(vehicle: VehicleEntity): Long
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}

interface FuelLogRepository {
    fun getFuelLogsForVehicle(vehicleId: Long): Flow<List<FuelLogEntity>>
    suspend fun getFuelLogsSortedByOdometer(vehicleId: Long): List<FuelLogEntity>
    fun getFuelLogsSince(vehicleId: Long, sinceDate: Long): Flow<List<FuelLogEntity>>
    fun getFuelCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    fun getFillUpCountSince(vehicleId: Long, sinceDate: Long): Flow<Int>
    suspend fun insertFuelLog(log: FuelLogEntity): Long
    suspend fun deleteFuelLog(log: FuelLogEntity)
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long): FuelLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long): FuelLogEntity?
}

interface ServiceLogRepository {
    fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLogEntity>>
    fun getServiceCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    suspend fun insertServiceLog(log: ServiceLogEntity): Long
    suspend fun deleteServiceLog(log: ServiceLogEntity)
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long): ServiceLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long): ServiceLogEntity?
}

interface ExpenseLogRepository {
    fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>>
    fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    suspend fun insertExpenseLog(log: ExpenseLogEntity): Long
    suspend fun deleteExpenseLog(log: ExpenseLogEntity)
}

interface TripLogRepository {
    fun getTripLogsForVehicle(vehicleId: Long): Flow<List<TripLogEntity>>
    suspend fun insertTripLog(log: TripLogEntity): Long
    suspend fun deleteTripLog(log: TripLogEntity)
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long): TripLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long): TripLogEntity?
}
