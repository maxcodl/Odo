package com.auto.odo.data.dao

import androidx.room.*
import com.auto.odo.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Long): VehicleEntity?

    // NEW: Flow version so we can watch a single vehicle without scanning the whole table
    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    fun getVehicleByIdFlow(id: Long): Flow<VehicleEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehiclesList(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vehicles: List<VehicleEntity>): List<Long>

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}

@Dao
interface FuelLogDao {
    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getFuelLogsForVehicle(vehicleId: Long): Flow<List<FuelLogEntity>>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY odometer ASC")
    suspend fun getFuelLogsSortedByOdometer(vehicleId: Long): List<FuelLogEntity>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getFuelLogsSince(vehicleId: Long, sinceDate: Long): Flow<List<FuelLogEntity>>

    @Query("SELECT SUM(totalCost) FROM fuel_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getFuelCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM fuel_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getFillUpCountSince(vehicleId: Long, sinceDate: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuelLog(log: FuelLogEntity): Long

    @Delete
    suspend fun deleteFuelLog(log: FuelLogEntity)

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND (date < :date OR (date == :date AND odometer <= :odo)) ORDER BY date DESC, odometer DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): FuelLogEntity?

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND (date > :date OR (date == :date AND odometer >= :odo)) ORDER BY date ASC, odometer ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): FuelLogEntity?

    @Query("SELECT * FROM fuel_logs ORDER BY date ASC")
    suspend fun getAllFuelLogs(): List<FuelLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<FuelLogEntity>): List<Long>
}

@Dao
interface ServiceLogDao {
    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLogEntity>>

    @Query("SELECT SUM(totalCost) FROM service_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getServiceCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLog(log: ServiceLogEntity): Long

    @Delete
    suspend fun deleteServiceLog(log: ServiceLogEntity)

    @Query("SELECT * FROM service_logs ORDER BY date ASC")
    suspend fun getAllServiceLogs(): List<ServiceLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ServiceLogEntity>): List<Long>

    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId AND (date < :date OR (date == :date AND odometer <= :odo)) ORDER BY date DESC, odometer DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity?

    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId AND (date > :date OR (date == :date AND odometer >= :odo)) ORDER BY date ASC, odometer ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): ServiceLogEntity?
}

@Dao
interface ExpenseLogDao {
    @Query("SELECT * FROM expense_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>>

    @Query("SELECT SUM(totalCost) FROM expense_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseLog(log: ExpenseLogEntity): Long

    @Delete
    suspend fun deleteExpenseLog(log: ExpenseLogEntity)

    @Query("SELECT * FROM expense_logs ORDER BY date ASC")
    suspend fun getAllExpenseLogs(): List<ExpenseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ExpenseLogEntity>): List<Long>
}

@Dao
interface TripLogDao {
    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getTripLogsForVehicle(vehicleId: Long): Flow<List<TripLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripLog(log: TripLogEntity): Long

    @Delete
    suspend fun deleteTripLog(log: TripLogEntity)

    @Query("SELECT * FROM trip_logs ORDER BY date ASC")
    suspend fun getAllTripLogs(): List<TripLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<TripLogEntity>): List<Long>

    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vehicleId AND (date < :date OR (date == :date AND endOdo <= :odo)) ORDER BY date DESC, endOdo DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double): TripLogEntity?

    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vehicleId AND (date > :date OR (date == :date AND startOdo >= :odo)) ORDER BY date ASC, startOdo ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double): TripLogEntity?
}