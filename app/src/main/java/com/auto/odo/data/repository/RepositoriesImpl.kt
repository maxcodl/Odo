package com.auto.odo.data.repository

import com.auto.odo.data.dao.*
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val dao: VehicleDao
) : VehicleRepository {
    override fun getAllVehicles(): Flow<List<VehicleEntity>> = dao.getAllVehicles()
    override suspend fun getVehicleById(id: Long): VehicleEntity? = dao.getVehicleById(id)
    // NEW: uses the targeted DAO query instead of loading all vehicles
    override fun getVehicleByIdFlow(id: Long): Flow<VehicleEntity?> =
        dao.getVehicleByIdFlow(id).distinctUntilChanged()
    override suspend fun insertVehicle(vehicle: VehicleEntity): Long = dao.insertVehicle(vehicle)
    override suspend fun updateVehicle(vehicle: VehicleEntity) = dao.updateVehicle(vehicle)
    override suspend fun deleteVehicle(vehicle: VehicleEntity) = dao.deleteVehicle(vehicle)
    override suspend fun getAllVehiclesList(): List<VehicleEntity> = dao.getAllVehiclesList()
    override suspend fun insertAllVehicles(vehicles: List<VehicleEntity>): List<Long> = dao.insertAll(vehicles)
    override suspend fun deleteAllVehicles() = dao.deleteAll()
}

class FuelLogRepositoryImpl @Inject constructor(
    private val dao: FuelLogDao
) : FuelLogRepository {
    override fun getFuelLogsForVehicle(vehicleId: Long): Flow<List<FuelLogEntity>> = dao.getFuelLogsForVehicle(vehicleId)
    override suspend fun getFuelLogsSortedByOdometer(vehicleId: Long): List<FuelLogEntity> = dao.getFuelLogsSortedByOdometer(vehicleId)
    override fun getFuelLogsSince(vehicleId: Long, sinceDate: Long): Flow<List<FuelLogEntity>> = dao.getFuelLogsSince(vehicleId, sinceDate)
    override fun getFuelCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?> = dao.getFuelCostSumSince(vehicleId, sinceDate)
    override fun getFillUpCountSince(vehicleId: Long, sinceDate: Long): Flow<Int> = dao.getFillUpCountSince(vehicleId, sinceDate)
    override suspend fun insertFuelLog(log: FuelLogEntity): Long = dao.insertFuelLog(log)
    override suspend fun deleteFuelLog(log: FuelLogEntity) = dao.deleteFuelLog(log)
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): FuelLogEntity? = dao.getClosestLogBefore(vehicleId, date, odo)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): FuelLogEntity? = dao.getClosestLogAfter(vehicleId, date, odo)
    override suspend fun getAllFuelLogs(): List<FuelLogEntity> = dao.getAllFuelLogs()
    override suspend fun insertAllFuelLogs(logs: List<FuelLogEntity>): List<Long> = dao.insertAll(logs)
}

class ServiceLogRepositoryImpl @Inject constructor(
    private val dao: ServiceLogDao
) : ServiceLogRepository {
    override fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLogEntity>> = dao.getServiceLogsForVehicle(vehicleId)
    override fun getServiceCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?> = dao.getServiceCostSumSince(vehicleId, sinceDate)
    override suspend fun insertServiceLog(log: ServiceLogEntity): Long = dao.insertServiceLog(log)
    override suspend fun deleteServiceLog(log: ServiceLogEntity) = dao.deleteServiceLog(log)
    override suspend fun getAllServiceLogs(): List<ServiceLogEntity> = dao.getAllServiceLogs()
    override suspend fun insertAllServiceLogs(logs: List<ServiceLogEntity>): List<Long> = dao.insertAll(logs)
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity? = dao.getClosestLogBefore(vehicleId, date, odo)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity? = dao.getClosestLogAfter(vehicleId, date, odo)
}

class ExpenseLogRepositoryImpl @Inject constructor(
    private val dao: ExpenseLogDao
) : ExpenseLogRepository {
    override fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>> = dao.getExpenseLogsForVehicle(vehicleId)
    override fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?> = dao.getExpenseCostSumSince(vehicleId, sinceDate)
    override suspend fun insertExpenseLog(log: ExpenseLogEntity): Long = dao.insertExpenseLog(log)
    override suspend fun deleteExpenseLog(log: ExpenseLogEntity) = dao.deleteExpenseLog(log)
    override suspend fun getAllExpenseLogs(): List<ExpenseLogEntity> = dao.getAllExpenseLogs()
    override suspend fun insertAllExpenseLogs(logs: List<ExpenseLogEntity>): List<Long> = dao.insertAll(logs)
}

class TripLogRepositoryImpl @Inject constructor(
    private val dao: TripLogDao
) : TripLogRepository {
    override fun getTripLogsForVehicle(vehicleId: Long): Flow<List<TripLogEntity>> = dao.getTripLogsForVehicle(vehicleId)
    override suspend fun insertTripLog(log: TripLogEntity): Long = dao.insertTripLog(log)
    override suspend fun deleteTripLog(log: TripLogEntity) = dao.deleteTripLog(log)
    override suspend fun getAllTripLogs(): List<TripLogEntity> = dao.getAllTripLogs()
    override suspend fun insertAllTripLogs(logs: List<TripLogEntity>): List<Long> = dao.insertAll(logs)
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): TripLogEntity? = dao.getClosestLogBefore(vehicleId, date, odo)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): TripLogEntity? = dao.getClosestLogAfter(vehicleId, date, odo)
}