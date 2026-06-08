package com.auto.odo.data.repository

import com.auto.odo.data.dao.*
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val dao: VehicleDao
) : VehicleRepository {
    override fun getAllVehicles(): Flow<List<VehicleEntity>> = dao.getAllVehicles()
    override suspend fun getVehicleById(id: Long): VehicleEntity? = dao.getVehicleById(id)
    override suspend fun insertVehicle(vehicle: VehicleEntity): Long = dao.insertVehicle(vehicle)
    override suspend fun deleteVehicle(vehicle: VehicleEntity) = dao.deleteVehicle(vehicle)
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
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long): FuelLogEntity? = dao.getClosestLogBefore(vehicleId, date)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long): FuelLogEntity? = dao.getClosestLogAfter(vehicleId, date)
}

class ServiceLogRepositoryImpl @Inject constructor(
    private val dao: ServiceLogDao
) : ServiceLogRepository {
    override fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLogEntity>> = dao.getServiceLogsForVehicle(vehicleId)
    override fun getServiceCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?> = dao.getServiceCostSumSince(vehicleId, sinceDate)
    override suspend fun insertServiceLog(log: ServiceLogEntity): Long = dao.insertServiceLog(log)
    override suspend fun deleteServiceLog(log: ServiceLogEntity) = dao.deleteServiceLog(log)
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long): ServiceLogEntity? = dao.getClosestLogBefore(vehicleId, date)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long): ServiceLogEntity? = dao.getClosestLogAfter(vehicleId, date)
}

class ExpenseLogRepositoryImpl @Inject constructor(
    private val dao: ExpenseLogDao
) : ExpenseLogRepository {
    override fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>> = dao.getExpenseLogsForVehicle(vehicleId)
    override fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?> = dao.getExpenseCostSumSince(vehicleId, sinceDate)
    override suspend fun insertExpenseLog(log: ExpenseLogEntity): Long = dao.insertExpenseLog(log)
    override suspend fun deleteExpenseLog(log: ExpenseLogEntity) = dao.deleteExpenseLog(log)
}

class TripLogRepositoryImpl @Inject constructor(
    private val dao: TripLogDao
) : TripLogRepository {
    override fun getTripLogsForVehicle(vehicleId: Long): Flow<List<TripLogEntity>> = dao.getTripLogsForVehicle(vehicleId)
    override suspend fun insertTripLog(log: TripLogEntity): Long = dao.insertTripLog(log)
    override suspend fun deleteTripLog(log: TripLogEntity) = dao.deleteTripLog(log)
    override suspend fun getClosestLogBefore(vehicleId: Long, date: Long): TripLogEntity? = dao.getClosestLogBefore(vehicleId, date)
    override suspend fun getClosestLogAfter(vehicleId: Long, date: Long): TripLogEntity? = dao.getClosestLogAfter(vehicleId, date)
}
