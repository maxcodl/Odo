package com.auto.odo.domain.usecase

import com.auto.odo.core.UnitConverter
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 1. Unified Log model for the feeds
sealed class LogItem {
    abstract val id: Long
    abstract val vehicleId: Long
    abstract val date: Long
    abstract val totalCost: Double
    abstract val notes: String?

    data class Fuel(
        override val id: Long,
        override val vehicleId: Long,
        override val date: Long,
        val odometer: Double,
        val quantity: Double,
        val pricePerUnit: Double,
        override val totalCost: Double,
        val isPartialTank: Boolean,
        val stationName: String?,
        override val notes: String?,
        val receiptPath: String?
    ) : LogItem()

    data class Service(
        override val id: Long,
        override val vehicleId: Long,
        override val date: Long,
        val odometer: Double,
        val serviceType: String,
        override val totalCost: Double,
        override val notes: String?
    ) : LogItem()

    data class Expense(
        override val id: Long,
        override val vehicleId: Long,
        override val date: Long,
        val category: String,
        override val totalCost: Double,
        override val notes: String?
    ) : LogItem()

    data class Trip(
        override val id: Long,
        override val vehicleId: Long,
        override val date: Long,
        val startOdo: Double,
        val endOdo: Double,
        val purpose: String,
        override val notes: String?
    ) : LogItem() {
        override val totalCost: Double get() = 0.0 // Trips don't have a cost column in schema
    }
}

// Helper to convert entities to LogItem
fun FuelLogEntity.toLogItem() = LogItem.Fuel(id, vehicleId, date, odometer, quantity, pricePerUnit, totalCost, isPartialTank, stationName, notes, receiptPath)
fun ServiceLogEntity.toLogItem() = LogItem.Service(id, vehicleId, date, odometer, serviceType, totalCost, notes)
fun ExpenseLogEntity.toLogItem() = LogItem.Expense(id, vehicleId, date, category, totalCost, notes)
fun TripLogEntity.toLogItem() = LogItem.Trip(id, vehicleId, date, startOdo, endOdo, purpose, notes)

// 2. Metrics Use Case
data class DashboardMetrics(
    val fuelCostLast30Days: Double,
    val averageEfficiency: Double,
    val fillUpCountLast30Days: Int
)

class GetRolling30DayMetricsUseCase @Inject constructor(
    private val fuelRepo: FuelLogRepository,
    private val vehicleRepo: VehicleRepository
) {
    operator fun invoke(vehicleId: Long): Flow<DashboardMetrics> {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        val fuelLogsFlow = fuelRepo.getFuelLogsForVehicle(vehicleId)
        val fuelCostFlow = fuelRepo.getFuelCostSumSince(vehicleId, thirtyDaysAgo)
        val fillUpCountFlow = fuelRepo.getFillUpCountSince(vehicleId, thirtyDaysAgo)

        return combine(fuelLogsFlow, fuelCostFlow, fillUpCountFlow) { logs, cost, count ->
            val vehicle = vehicleRepo.getVehicleById(vehicleId)
            val avgEff = calculateAverageEfficiency(logs, vehicle?.distanceUnit ?: "km", vehicle?.fuelUnit ?: "Liters")
            DashboardMetrics(
                fuelCostLast30Days = cost ?: 0.0,
                averageEfficiency = avgEff,
                fillUpCountLast30Days = count
            )
        }.flowOn(Dispatchers.Default)
    }

    private fun calculateAverageEfficiency(logs: List<FuelLogEntity>, distUnit: String, volUnit: String): Double {
        if (logs.size < 2) return 0.0
        val sortedLogs = logs.sortedBy { it.odometer }
        val firstLog = sortedLogs.first()
        val lastNonPartial = sortedLogs.lastOrNull { !it.isPartialTank } ?: return 0.0

        val firstIndex = sortedLogs.indexOf(firstLog)
        val lastIndex = sortedLogs.indexOf(lastNonPartial)
        if (lastIndex <= firstIndex) return 0.0

        val totalDistance = lastNonPartial.odometer - firstLog.odometer
        if (totalDistance <= 0 || totalDistance.isNaN() || totalDistance.isInfinite()) return 0.0

        // Sum quantities of fuel consumed after the first log up to the last non-partial fill-up
        var totalFuel = 0.0
        for (i in (firstIndex + 1)..lastIndex) {
            val qty = sortedLogs[i].quantity
            if (!qty.isNaN() && !qty.isInfinite()) {
                totalFuel += qty
            }
        }

        if (totalFuel <= 0 || totalFuel.isNaN() || totalFuel.isInfinite()) return 0.0

        val rawKmpl = totalDistance / totalFuel
        if (rawKmpl.isNaN() || rawKmpl.isInfinite()) return 0.0

        val result = if (distUnit == "miles" || volUnit == "Gallons") {
            // Convert km/L to MPG
            UnitConverter.kmToMiles(rawKmpl) / UnitConverter.litersToGallons(1.0)
        } else {
            rawKmpl
        }
        return if (result.isNaN() || result.isInfinite()) 0.0 else result
    }
}

// 3. Recent Logs Use Case
class GetRecentLogsUseCase @Inject constructor(
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val expenseRepo: ExpenseLogRepository,
    private val tripRepo: TripLogRepository
) {
    operator fun invoke(vehicleId: Long, limit: Int = 5): Flow<List<LogItem>> {
        val fuelFlow = fuelRepo.getFuelLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val serviceFlow = serviceRepo.getServiceLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val expenseFlow = expenseRepo.getExpenseLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val tripFlow = tripRepo.getTripLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }

        return combine(fuelFlow, serviceFlow, expenseFlow, tripFlow) { f, s, e, t ->
            val allLogs = f + s + e + t
            allLogs.sortedByDescending { it.date }.take(limit)
        }.flowOn(Dispatchers.Default)
    }
}

// 4. Logs Feed (Filterable) Use Case
class GetLogsFeedUseCase @Inject constructor(
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val expenseRepo: ExpenseLogRepository,
    private val tripRepo: TripLogRepository
) {
    operator fun invoke(vehicleId: Long, filterType: String?): Flow<List<LogItem>> {
        val fuelFlow = fuelRepo.getFuelLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val serviceFlow = serviceRepo.getServiceLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val expenseFlow = expenseRepo.getExpenseLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }
        val tripFlow = tripRepo.getTripLogsForVehicle(vehicleId).map { list -> list.map { it.toLogItem() } }

        return combine(fuelFlow, serviceFlow, expenseFlow, tripFlow) { f, s, e, t ->
            val unfiltered = when (filterType?.lowercase()) {
                "fuel" -> f
                "service" -> s
                "expense" -> e
                "trip" -> t
                else -> f + s + e + t
            }
            unfiltered.sortedByDescending { it.date }
        }.flowOn(Dispatchers.Default)
    }
}

// 5. Odometer Validator Use Case
sealed interface OdoValidationResult {
    data object Valid : OdoValidationResult
    data class InvalidBefore(val limit: Double) : OdoValidationResult
    data class InvalidAfter(val limit: Double) : OdoValidationResult
}

class ValidateOdometerUseCase @Inject constructor(
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val tripRepo: TripLogRepository
) {
    suspend operator fun invoke(vehicleId: Long, date: Long, odometer: Double): OdoValidationResult {
        val fuelBefore = fuelRepo.getClosestLogBefore(vehicleId, date, odometer)?.odometer ?: 0.0
        val serviceBefore = serviceRepo.getClosestLogBefore(vehicleId, date, odometer)?.odometer ?: 0.0
        val tripBefore = tripRepo.getClosestLogBefore(vehicleId, date, odometer)?.endOdo ?: 0.0

        val maxBefore = maxOf(fuelBefore, serviceBefore, tripBefore)
        if (maxBefore > 0.0 && odometer < maxBefore) {
            return OdoValidationResult.InvalidBefore(maxBefore)
        }

        val fuelAfter = fuelRepo.getClosestLogAfter(vehicleId, date, odometer)?.odometer
        val serviceAfter = serviceRepo.getClosestLogAfter(vehicleId, date, odometer)?.odometer
        val tripAfter = tripRepo.getClosestLogAfter(vehicleId, date, odometer)?.startOdo

        val limitsAfter = listOfNotNull(fuelAfter, serviceAfter, tripAfter)
        if (limitsAfter.isNotEmpty()) {
            val minAfter = limitsAfter.minOrNull() ?: Double.MAX_VALUE
            if (odometer > minAfter) {
                return OdoValidationResult.InvalidAfter(minAfter)
            }
        }

        return OdoValidationResult.Valid
    }
}
