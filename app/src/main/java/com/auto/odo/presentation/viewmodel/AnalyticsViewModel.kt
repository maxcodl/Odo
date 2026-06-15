package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.data.dao.*
import com.auto.odo.data.entity.VehicleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

data class MonthlyChartPoint(
    val monthYear: String, 
    val displayLabel: String, 
    val fuelCost: Double,
    val serviceCost: Double,
    val expenseCost: Double,
    val totalCost: Double
)

data class AnalyticsUiState(
    val allVehicles: List<VehicleEntity> = emptyList(),
    val activeVehicle: VehicleEntity? = null,
    val isAllVehiclesSelected: Boolean = false,
    
    // Core Totals
    val totalFuelCost: Double = 0.0,
    val totalServiceCost: Double = 0.0,
    val totalExpenseCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val costPerDistanceUnit: Double = 0.0,
    val totalDistanceTracked: Double = 0.0,
    val projectedYearlyCost: Double = 0.0,
    val costPerDay: Double = 0.0,
    
    // NEW: Detailed Fuel Economics
    val averageEfficiency: Double = 0.0,
    val fillUpsCount: Int = 0,
    val fuelCostPerDistUnit: Double = 0.0,
    val serviceCostPerDistUnit: Double = 0.0,
    val expenseCostPerDistUnit: Double = 0.0,
    val avgDistBtwnFillUps: Double = 0.0,
    val avgQtyPerFillUp: Double = 0.0,
    val avgCostPerFillUp: Double = 0.0,
    val avgPricePerUnit: Double = 0.0,
    val fillUpsPerMonth: Double = 0.0,
    val fuelCostPerMonth: Double = 0.0,
    
    // Extremes
    val maxFuelPrice: Double = 0.0,
    val maxFillUpVolume: Double = 0.0,
    val longestDistanceBetweenFills: Double = 0.0,
    val shortestDistanceBetweenFills: Double = 0.0,
    
    // Chart Data
    val allMonthlyData: List<MonthlyChartPoint> = emptyList(),
    val monthlyChartData: List<MonthlyChartPoint> = emptyList(),
    val selectedMonthWindowIndex: Int = 0,
    val hasMoreOlderMonths: Boolean = false,
    val hasMoreNewerMonths: Boolean = false,
    
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val fuelLogDao: FuelLogDao,
    private val serviceLogDao: ServiceLogDao,
    private val expenseLogDao: ExpenseLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _selectedVehicleId.collect { selectedId ->
                _uiState.update { it.copy(isLoading = true) }
                
                val vehicles = vehicleDao.getAllVehiclesList()
                
                if (vehicles.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, activeVehicle = null, allVehicles = emptyList()) }
                    return@collect
                }

                val targetVehicle = if (selectedId == -1L) null else (vehicles.find { it.id == selectedId } ?: vehicles.first())
                val isAll = selectedId == -1L || (selectedId == null && vehicles.size > 1)
                val processingList = if (isAll) vehicles else listOf(targetVehicle!!)

                val baseCurrency = vehicles.first().currency
                val baseDistUnit = vehicles.first().distanceUnit
                val baseFuelUnit = vehicles.first().fuelUnit

                var sumFuelCost = 0.0
                var sumServiceCost = 0.0
                var sumExpenseCost = 0.0
                
                var sumDisplayDistance = 0.0
                var sumDisplayVolume = 0.0

                var overallMaxFuelPrice = 0.0
                var overallMaxVolume = 0.0
                var overallShortestDelta = Double.MAX_VALUE
                var overallLongestDelta = 0.0

                var sumCostPerDay = 0.0
                var sumFuelCostPerDay = 0.0
                var sumProjectedYearly = 0.0
                var totalFillUps = 0
                var totalDaysOwned = 0.0

                val combinedMap = mutableMapOf<String, MonthlyChartPoint>()

                fun getMonthLabel(yyyyMm: String): String {
                    val parts = yyyyMm.split("-")
                    if (parts.size != 2) return ""
                    val monthMap = mapOf(
                        "01" to "Jan", "02" to "Feb", "03" to "Mar", "04" to "Apr",
                        "05" to "May", "06" to "Jun", "07" to "Jul", "08" to "Aug",
                        "09" to "Sep", "10" to "Oct", "11" to "Nov", "12" to "Dec"
                    )
                    val shortYear = parts[0].takeLast(2)
                    return "${monthMap[parts[1]]} '$shortYear"
                }

                for (v in processingList) {
                    val vFuel = fuelLogDao.getTotalFuelCost(v.id) ?: 0.0
                    val vService = serviceLogDao.getTotalServiceCost(v.id) ?: 0.0
                    val vExpense = expenseLogDao.getTotalExpenseCost(v.id) ?: 0.0

                    sumFuelCost += vFuel
                    sumServiceCost += vService
                    sumExpenseCost += vExpense

                    val minOdo = fuelLogDao.getMinOdometer(v.id) ?: 0.0
                    val maxOdo = fuelLogDao.getMaxOdometer(v.id) ?: 0.0
                    val dist = maxOdo - minOdo
                    
                    val distConverted = if (v.distanceUnit != baseDistUnit) {
                        if (baseDistUnit == "miles") UnitConverter.kmToMiles(dist) else dist 
                    } else dist
                    sumDisplayDistance += distConverted

                    val vRawVolume = fuelLogDao.getTotalFuelVolume(v.id) ?: 0.0
                    val volConverted = if (v.fuelUnit != baseFuelUnit) {
                        if (baseFuelUnit == "Gallons") UnitConverter.litersToGallons(vRawVolume) else vRawVolume
                    } else vRawVolume
                    sumDisplayVolume += volConverted

                    val vMaxPrice = fuelLogDao.getMaxFuelPrice(v.id) ?: 0.0
                    if (vMaxPrice > overallMaxFuelPrice) overallMaxFuelPrice = vMaxPrice

                    val vRawMaxVol = fuelLogDao.getMaxFillUpVolume(v.id) ?: 0.0
                    val vMaxVolConv = if (v.fuelUnit != baseFuelUnit) {
                        if (baseFuelUnit == "Gallons") UnitConverter.litersToGallons(vRawMaxVol) else vRawMaxVol
                    } else vRawMaxVol
                    if (vMaxVolConv > overallMaxVolume) overallMaxVolume = vMaxVolConv

                    val allFuelLogs = fuelLogDao.getFuelLogsSortedByOdometer(v.id)
                    totalFillUps += allFuelLogs.size
                    
                    if (allFuelLogs.size >= 2) {
                        for (i in 1 until allFuelLogs.size) {
                            val delta = allFuelLogs[i].odometer - allFuelLogs[i - 1].odometer
                            if (delta > 0) { 
                                val deltaConv = if (v.distanceUnit != baseDistUnit) {
                                    if (baseDistUnit == "miles") UnitConverter.kmToMiles(delta) else delta
                                } else delta

                                if (deltaConv < overallShortestDelta) overallShortestDelta = deltaConv
                                if (deltaConv > overallLongestDelta) overallLongestDelta = deltaConv
                            }
                        }
                    }

                    if (allFuelLogs.isNotEmpty()) {
                        val firstLogDate = allFuelLogs.first().date
                        val lastLogDate = allFuelLogs.last().date
                        val daysOwned = max(1.0, (lastLogDate - firstLogDate) / (1000.0 * 60 * 60 * 24))
                        totalDaysOwned += daysOwned
                        
                        val vTotal = vFuel + vService + vExpense
                        val vCPD = vTotal / daysOwned
                        sumCostPerDay += vCPD
                        sumFuelCostPerDay += (vFuel / daysOwned)
                        if (daysOwned >= 14) sumProjectedYearly += (vCPD * 365)
                    }

                    val monthlyFuel = fuelLogDao.getMonthlyFuelSpend(v.id)
                    val monthlyService = serviceLogDao.getMonthlyServiceSpend(v.id)
                    val monthlyExpense = expenseLogDao.getMonthlyExpenseSpend(v.id)

                    monthlyFuel.forEach { 
                        val ex = combinedMap[it.monthYear]
                        if (ex != null) combinedMap[it.monthYear] = ex.copy(fuelCost = ex.fuelCost + it.total, totalCost = ex.totalCost + it.total)
                        else combinedMap[it.monthYear] = MonthlyChartPoint(it.monthYear, getMonthLabel(it.monthYear), it.total, 0.0, 0.0, it.total)
                    }
                    monthlyService.forEach { 
                        val ex = combinedMap[it.monthYear]
                        if (ex != null) combinedMap[it.monthYear] = ex.copy(serviceCost = ex.serviceCost + it.total, totalCost = ex.totalCost + it.total)
                        else combinedMap[it.monthYear] = MonthlyChartPoint(it.monthYear, getMonthLabel(it.monthYear), 0.0, it.total, 0.0, it.total)
                    }
                    monthlyExpense.forEach { 
                        val ex = combinedMap[it.monthYear]
                        if (ex != null) combinedMap[it.monthYear] = ex.copy(expenseCost = ex.expenseCost + it.total, totalCost = ex.totalCost + it.total)
                        else combinedMap[it.monthYear] = MonthlyChartPoint(it.monthYear, getMonthLabel(it.monthYear), 0.0, 0.0, it.total, it.total)
                    }
                }

                val sumTotalCost = sumFuelCost + sumServiceCost + sumExpenseCost
                val allDataSorted = combinedMap.values.sortedBy { it.monthYear }
                
                // Advanced Math
                val costPerUnit = if (sumDisplayDistance > 0) sumTotalCost / sumDisplayDistance else 0.0
                val avgEfficiency = if (sumDisplayVolume > 0) sumDisplayDistance / sumDisplayVolume else 0.0
                val uiShortestDelta = if (overallShortestDelta == Double.MAX_VALUE) 0.0 else overallShortestDelta
                
                val avgDistBtwnFillUps = if (totalFillUps > 1) sumDisplayDistance / (totalFillUps - 1) else 0.0
                val avgQtyPerFillUp = if (totalFillUps > 0) sumDisplayVolume / totalFillUps else 0.0
                val avgCostPerFillUp = if (totalFillUps > 0) sumFuelCost / totalFillUps else 0.0
                val avgPricePerUnit = if (sumDisplayVolume > 0) sumFuelCost / sumDisplayVolume else 0.0
                val fillUpsPerMonth = if (totalDaysOwned > 0) (totalFillUps / totalDaysOwned) * 30.4 else 0.0
                
                val fuelCostPerDistUnit = if (sumDisplayDistance > 0) sumFuelCost / sumDisplayDistance else 0.0
                val serviceCostPerDistUnit = if (sumDisplayDistance > 0) sumServiceCost / sumDisplayDistance else 0.0
                val expenseCostPerDistUnit = if (sumDisplayDistance > 0) sumExpenseCost / sumDisplayDistance else 0.0
                val fuelCostPerMonth = sumFuelCostPerDay * 30.4

                val displayVehicle = if (isAll) {
                    VehicleEntity(id = -1, name = "All Vehicles", type = "All", fuelUnit = baseFuelUnit, distanceUnit = baseDistUnit, currency = baseCurrency)
                } else targetVehicle

                _uiState.update { 
                    it.copy(
                        allVehicles = vehicles,
                        activeVehicle = displayVehicle,
                        isAllVehiclesSelected = isAll,
                        totalFuelCost = sumFuelCost,
                        totalServiceCost = sumServiceCost,
                        totalExpenseCost = sumExpenseCost,
                        totalCost = sumTotalCost,
                        costPerDistanceUnit = costPerUnit,
                        projectedYearlyCost = sumProjectedYearly,
                        costPerDay = sumCostPerDay,
                        averageEfficiency = avgEfficiency,
                        totalDistanceTracked = sumDisplayDistance,
                        fillUpsCount = totalFillUps,
                        fuelCostPerDistUnit = fuelCostPerDistUnit,
                        serviceCostPerDistUnit = serviceCostPerDistUnit,
                        expenseCostPerDistUnit = expenseCostPerDistUnit,
                        avgDistBtwnFillUps = avgDistBtwnFillUps,
                        avgQtyPerFillUp = avgQtyPerFillUp,
                        avgCostPerFillUp = avgCostPerFillUp,
                        avgPricePerUnit = avgPricePerUnit,
                        fillUpsPerMonth = fillUpsPerMonth,
                        fuelCostPerMonth = fuelCostPerMonth,
                        maxFuelPrice = overallMaxFuelPrice,
                        maxFillUpVolume = overallMaxVolume,
                        longestDistanceBetweenFills = overallLongestDelta,
                        shortestDistanceBetweenFills = uiShortestDelta,
                        allMonthlyData = allDataSorted,
                        isLoading = false
                    )
                }
                
                updateMonthWindow(_uiState.value.selectedMonthWindowIndex)
            }
        }
    }

    fun selectVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    fun updateMonthWindow(indexOffset: Int) {
        _uiState.update { state ->
            val data = state.allMonthlyData
            if (data.isEmpty()) return@update state
            val totalSize = data.size
            val windowSize = 6
            val maxIndex = max(0, (totalSize - 1) / windowSize)
            val safeIndex = indexOffset.coerceIn(0, maxIndex)
            val startIndex = max(0, totalSize - ((safeIndex + 1) * windowSize))
            val endIndex = totalSize - (safeIndex * windowSize)
            val windowData = data.subList(startIndex, endIndex)
            state.copy(
                selectedMonthWindowIndex = safeIndex,
                monthlyChartData = windowData,
                hasMoreOlderMonths = safeIndex < maxIndex,
                hasMoreNewerMonths = safeIndex > 0
            )
        }
    }
}