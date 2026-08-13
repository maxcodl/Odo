package com.auto.odo.data.dao

import androidx.room.*
import com.auto.odo.data.entity.*
import kotlinx.coroutines.flow.Flow

// --- HELPER CLASS FOR ANALYTICS ---
// Room will automatically map the SQL grouping results into this object
data class MonthlySpend(
    val monthYear: String, // Will output format: "2025-06"
    val total: Double
)

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Long): VehicleEntity?

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

    // --- ANALYTICS QUERIES (PHASE 1) ---
    @Query("SELECT SUM(totalCost) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getTotalFuelCost(vehicleId: Long): Double?

    @Query("SELECT SUM(quantity) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getTotalFuelVolume(vehicleId: Long): Double?

    @Query("SELECT MIN(odometer) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getMinOdometer(vehicleId: Long): Double?

    @Query("SELECT MAX(odometer) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getMaxOdometer(vehicleId: Long): Double?

    @Query("SELECT MAX(pricePerUnit) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getMaxFuelPrice(vehicleId: Long): Double?

    @Query("SELECT MAX(quantity) FROM fuel_logs WHERE vehicleId = :vehicleId")
    suspend fun getMaxFillUpVolume(vehicleId: Long): Double?
    
    @Query("SELECT * FROM fuel_logs WHERE id = :id LIMIT 1")
    suspend fun getFuelLogById(id: Long): FuelLogEntity?

    @Update
    suspend fun updateFuelLog(log: FuelLogEntity)

    // Grouping by Month-Year for Time-Series Charts
    @Query("""
        SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as monthYear, SUM(totalCost) as total 
        FROM fuel_logs 
        WHERE vehicleId = :vehicleId 
        GROUP BY monthYear 
        ORDER BY monthYear ASC
    """)
    suspend fun getMonthlyFuelSpend(vehicleId: Long): List<MonthlySpend>
    // -----------------------------------

    @Query("SELECT COUNT(*) FROM fuel_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getFillUpCountSince(vehicleId: Long, sinceDate: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuelLog(log: FuelLogEntity): Long

    @Delete
    suspend fun deleteFuelLog(log: FuelLogEntity)

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date < :date OR (date == :date AND odometer <= :odo)) ORDER BY date DESC, odometer DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): FuelLogEntity?

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date > :date OR (date == :date AND odometer >= :odo)) ORDER BY date ASC, odometer ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): FuelLogEntity?

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


    @Query("SELECT * FROM service_logs WHERE id = :id LIMIT 1")
    suspend fun getServiceLogById(id: Long): ServiceLogEntity?

    @Update
    suspend fun updateServiceLog(log: ServiceLogEntity)

    // --- ANALYTICS QUERIES (PHASE 1) ---
    @Query("SELECT SUM(totalCost) FROM service_logs WHERE vehicleId = :vehicleId")
    suspend fun getTotalServiceCost(vehicleId: Long): Double?

    @Query("""
        SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as monthYear, SUM(totalCost) as total 
        FROM service_logs 
        WHERE vehicleId = :vehicleId 
        GROUP BY monthYear 
        ORDER BY monthYear ASC
    """)
    suspend fun getMonthlyServiceSpend(vehicleId: Long): List<MonthlySpend>
    // -----------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLog(log: ServiceLogEntity): Long

    @Delete
    suspend fun deleteServiceLog(log: ServiceLogEntity)

    @Query("SELECT * FROM service_logs ORDER BY date ASC")
    suspend fun getAllServiceLogs(): List<ServiceLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ServiceLogEntity>): List<Long>

    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date < :date OR (date == :date AND odometer <= :odo)) ORDER BY date DESC, odometer DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): ServiceLogEntity?

    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date > :date OR (date == :date AND odometer >= :odo)) ORDER BY date ASC, odometer ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): ServiceLogEntity?
}

@Dao
interface ExpenseLogDao {
    @Query("SELECT * FROM expense_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getExpenseLogsForVehicle(vehicleId: Long): Flow<List<ExpenseLogEntity>>

    @Query("SELECT SUM(totalCost) FROM expense_logs WHERE vehicleId = :vehicleId AND date >= :sinceDate")
    fun getExpenseCostSumSince(vehicleId: Long, sinceDate: Long): Flow<Double?>

    @Query("SELECT * FROM expense_logs WHERE id = :id LIMIT 1")
    suspend fun getExpenseLogById(id: Long): ExpenseLogEntity?

    @Update
    suspend fun updateExpenseLog(log: ExpenseLogEntity)

    // --- ANALYTICS QUERIES (PHASE 1) ---
    @Query("SELECT SUM(totalCost) FROM expense_logs WHERE vehicleId = :vehicleId")
    suspend fun getTotalExpenseCost(vehicleId: Long): Double?

    @Query("""
        SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as monthYear, SUM(totalCost) as total 
        FROM expense_logs 
        WHERE vehicleId = :vehicleId 
        GROUP BY monthYear 
        ORDER BY monthYear ASC
    """)
    suspend fun getMonthlyExpenseSpend(vehicleId: Long): List<MonthlySpend>
    // -----------------------------------

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

    @Query("SELECT * FROM trip_logs WHERE id = :id LIMIT 1")
    suspend fun getTripLogById(id: Long): TripLogEntity?

    @Update
    suspend fun updateTripLog(log: TripLogEntity)

    @Delete
    suspend fun deleteTripLog(log: TripLogEntity)

    @Query("SELECT * FROM trip_logs ORDER BY date ASC")
    suspend fun getAllTripLogs(): List<TripLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<TripLogEntity>): List<Long>

    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date < :date OR (date == :date AND endOdo <= :odo)) ORDER BY date DESC, endOdo DESC LIMIT 1")
    suspend fun getClosestLogBefore(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): TripLogEntity?

    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vehicleId AND id != :excludeId AND (date > :date OR (date == :date AND startOdo >= :odo)) ORDER BY date ASC, startOdo ASC LIMIT 1")
    suspend fun getClosestLogAfter(vehicleId: Long, date: Long, odo: Double, excludeId: Long = -1L): TripLogEntity?
}