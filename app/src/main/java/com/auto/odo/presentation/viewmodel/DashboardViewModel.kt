package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.FuelLogRepository
import com.auto.odo.domain.repository.VehicleRepository
import com.auto.odo.domain.usecase.DashboardMetrics
import com.auto.odo.domain.usecase.GetRecentLogsUseCase
import com.auto.odo.domain.usecase.GetRolling30DayMetricsUseCase
import com.auto.odo.domain.usecase.LogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChartPoint(val date: Long, val value: Double)

data class DashboardUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val selectedVehicle: VehicleEntity? = null,
    val metrics: DashboardMetrics? = null,
    val recentLogs: List<LogItem> = emptyList(),
    val chartPoints: List<ChartPoint> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val fuelRepo: FuelLogRepository,
    private val sessionManager: UserSessionManager,
    private val getRollingMetrics: GetRolling30DayMetricsUseCase,
    private val getRecentLogs: GetRecentLogsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            combine(
                vehicleRepo.getAllVehicles(),
                sessionManager.currentVehicleId
            ) { vehicles, selectedId ->
                Pair(vehicles, selectedId)
            }.collectLatest { (vehicles, selectedId) ->
                if (vehicles.isEmpty()) {
                    _uiState.update { it.copy(vehicles = emptyList(), selectedVehicle = null, isLoading = false) }
                    return@collectLatest
                }

                val activeVehicle = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.first()
                if (activeVehicle.id != selectedId) {
                    sessionManager.setCurrentVehicleId(activeVehicle.id)
                }

                _uiState.update { it.copy(vehicles = vehicles, selectedVehicle = activeVehicle, isLoading = true) }

                combine(
                    getRollingMetrics(activeVehicle.id),
                    getRecentLogs(activeVehicle.id),
                    flow { emit(calculateChartPoints(activeVehicle.id, activeVehicle.distanceUnit, activeVehicle.fuelUnit)) }
                ) { metrics, logs, chart ->
                    Triple(metrics, logs, chart)
                }.collect { (metrics, logs, chart) ->
                    _uiState.update {
                        it.copy(
                            metrics = metrics,
                            recentLogs = logs,
                            chartPoints = chart,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun selectVehicle(vehicleId: Long) {
        viewModelScope.launch {
            sessionManager.setCurrentVehicleId(vehicleId)
        }
    }

    fun addVehicle(name: String, type: String, fuelUnit: String, distanceUnit: String, currency: String) {
        viewModelScope.launch {
            val newVehicle = VehicleEntity(
                name = name,
                type = type,
                fuelUnit = fuelUnit,
                distanceUnit = distanceUnit,
                currency = currency
            )
            val newId = vehicleRepo.insertVehicle(newVehicle)
            sessionManager.setCurrentVehicleId(newId)
        }
    }

    private suspend fun calculateChartPoints(vehicleId: Long, distUnit: String, volUnit: String): List<ChartPoint> {
        val fuelLogs = fuelRepo.getFuelLogsSortedByOdometer(vehicleId)
        if (fuelLogs.size < 2) return emptyList()

        val points = mutableListOf<ChartPoint>()
        for (i in 1 until fuelLogs.size) {
            val prev = fuelLogs[i - 1]
            val curr = fuelLogs[i]
            if (curr.isPartialTank) continue

            val distance = curr.odometer - prev.odometer
            if (distance > 0 && curr.quantity > 0) {
                var efficiency = distance / curr.quantity
                if (distUnit == "miles" || volUnit == "Gallons") {
                    efficiency = UnitConverter.kmToMiles(efficiency) / UnitConverter.litersToGallons(1.0)
                }
                points.add(ChartPoint(curr.date, efficiency))
            }
        }
        return points.sortedBy { it.date }
    }
}
