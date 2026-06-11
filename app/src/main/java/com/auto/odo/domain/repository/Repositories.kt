package com.auto.odo.domain.repository

import com.auto.odo.data.entity.*
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    suspend fun getVehicleById(id: Long): VehicleEntity?
    fun getVehicleByIdFlow(id: Long): Flow<VehicleEntity?>  // NEW
    suspend fun insertVehicle(vehicle: VehicleEntity): Long
    suspend fun updateVehicle(vehicle: VehicleEntity)
    suspend fun deleteVehicle(vehicle: VehicleEntity)
    suspend fun getAllVehiclesList(): List<VehicleEntity>
    suspend fun insertAllVehicles(vehicles: List<VehicleEntity>): List<Long>
    suspend fun deleteAllVehicles()
}

interface FuelLogRepository {
    fun getFuelLogsForVehicle(vehicleId: Long): Flow<List<FuelLogEntity>>
    suspend fun getFuelLogsSortedByOdometer(vehicleId: Long): List<FuelLogEntity>
    fun getFuelLogsSince(vehicleId: Long, sinceDate: Long): Flow<List<FuelLogEntity>>
    fun getFuelCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    fun getFillUpCountSince(vehicleId: Long, sinceDate: Long): Flow<Int>
    suspend fun insertFuelLog(log: FuelLogEntity): Long
    suspend fun deleteFuelLog(log: FuelLogEntity)
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): FuelLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): FuelLogEntity?
    suspend fun getAllFuelLogs(): List<FuelLogEntity>
    suspend fun insertAllFuelLogs(logs: List<FuelLogEntity>): List<Long>
}

interface ServiceLogRepository {
    fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLogEntity>>
    fun getServiceCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    suspend fun insertServiceLog(log: ServiceLogEntity): Long
    suspend fun deleteServiceLog(log: ServiceLogEntity)
    suspend fun getAllServiceLogs(): List<ServiceLogEntity>
    suspend fun insertAllServiceLogs(logs: List<ServiceLogEntity>): List<Long>
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity?
}

interface ExpenseLogRepository {
    fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>>
    fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>
    suspend fun insertExpenseLog(log: ExpenseLogEntity): Long
    suspend fun deleteExpenseLog(log: ExpenseLogEntity)
    suspend fun getAllExpenseLogs(): List<ExpenseLogEntity>
    suspend fun insertAllExpenseLogs(logs: List<ExpenseLogEntity>): List<Long>
}

interface TripLogRepository {
    fun getTripLogsForVehicle(vehicleId: Long): Flow<List<TripLogEntity>>
    suspend fun insertTripLog(log: TripLogEntity): Long
    suspend fun deleteTripLog(log: TripLogEntity)
    suspend fun getAllTripLogs(): List<TripLogEntity>
    suspend fun insertAllTripLogs(logs: List<TripLogEntity>): List<Long>
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): TripLogEntity?
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): TripLogEntity?
}