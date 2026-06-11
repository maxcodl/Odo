package com.auto.odo.presentation.viewmodel

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ChartPoint(val date: Long, val value: Double)

@Immutable
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
                vehicleRepo.getAllVehicles().distinctUntilChanged(),
                sessionManager.currentVehicleId.distinctUntilChanged()
            ) { vehicles, selectedId ->
                Pair(vehicles, selectedId)
            }.flatMapLatest { (vehicles, selectedId) ->
                if (vehicles.isEmpty()) {
                    flowOf(DashboardUiState(vehicles = emptyList(), selectedVehicle = null, isLoading = false))
                } else {
                    val activeVehicle = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.first()
                    if (activeVehicle.id != selectedId) {
                        sessionManager.setCurrentVehicleId(activeVehicle.id)
                    }

                    // FIX: One single fuel log query shared by both chart and metrics.
                    val fuelLogsFlow = fuelRepo.getFuelLogsForVehicle(activeVehicle.id)
                        .distinctUntilChanged()

                    // Optimization: Move chart points calculation to Default dispatcher
                    val chartPointsFlow = fuelLogsFlow
                        .map { logs ->
                            val sorted = logs.sortedBy { it.odometer }
                            buildChartPoints(sorted, activeVehicle.distanceUnit, activeVehicle.fuelUnit)
                        }
                        .flowOn(Dispatchers.Default)
                        .distinctUntilChanged()

                    val metricsFlow = getRollingMetrics(
                        vehicleId = activeVehicle.id,
                        distanceUnit = activeVehicle.distanceUnit,
                        fuelUnit = activeVehicle.fuelUnit
                    ).distinctUntilChanged()

                    val recentLogsFlow = getRecentLogs(activeVehicle.id)
                        .distinctUntilChanged()

                    combine(
                        metricsFlow,
                        recentLogsFlow,
                        chartPointsFlow
                    ) { metrics, logs, chartPoints ->
                        DashboardUiState(
                            vehicles = vehicles,
                            selectedVehicle = activeVehicle,
                            metrics = metrics,
                            recentLogs = logs,
                            chartPoints = chartPoints,
                            isLoading = false
                        )
                    }
                }
            }.collect { newState ->
                _uiState.value = newState
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

    private fun buildChartPoints(
        fuelLogs: List<com.auto.odo.data.entity.FuelLogEntity>,
        distUnit: String,
        volUnit: String
    ): List<ChartPoint> {
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