package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import com.auto.odo.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==========================================
// 1. DASHBOARD VIEW MODEL
// ==========================================

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
            // First, listen to vehicles list
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

                // Fetch metrics, logs, and chart for active vehicle
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

// ==========================================
// 2. LOGS FEED VIEW MODEL
// ==========================================

data class LogsFeedUiState(
    val selectedVehicle: VehicleEntity? = null,
    val logs: List<LogItem> = emptyList(),
    val activeFilter: String? = null, // null for All, "fuel", "service", "expense", "trip"
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogsFeedViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val sessionManager: UserSessionManager,
    private val getLogsFeed: GetLogsFeedUseCase,
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val expenseRepo: ExpenseLogRepository,
    private val tripRepo: TripLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsFeedUiState())
    val uiState: StateFlow<LogsFeedUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            sessionManager.currentVehicleId.flatMapLatest { vehicleId ->
                if (vehicleId == null) {
                    flowOf(Pair(null, emptyList<LogItem>()))
                } else {
                    val vehicle = vehicleRepo.getVehicleById(vehicleId)
                    _filter.flatMapLatest { filterType ->
                        getLogsFeed(vehicleId, filterType).map { logs -> Pair(vehicle, logs) }
                    }
                }
            }.collect { (vehicle, logs) ->
                _uiState.update {
                    it.copy(
                        selectedVehicle = vehicle,
                        logs = logs,
                        activeFilter = _filter.value,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setFilter(filter: String?) {
        _filter.value = filter
    }

    fun deleteLog(log: LogItem) {
        viewModelScope.launch {
            when (log) {
                is LogItem.Fuel -> fuelRepo.deleteFuelLog(
                    FuelLogEntity(
                        id = log.id,
                        vehicleId = log.vehicleId,
                        date = log.date,
                        odometer = log.odometer,
                        quantity = log.quantity,
                        pricePerUnit = log.pricePerUnit,
                        totalCost = log.totalCost,
                        isPartialTank = log.isPartialTank,
                        stationName = log.stationName,
                        notes = log.notes,
                        receiptPath = log.receiptPath
                    )
                )
                is LogItem.Service -> serviceRepo.deleteServiceLog(
                    ServiceLogEntity(
                        id = log.id,
                        vehicleId = log.vehicleId,
                        date = log.date,
                        odometer = log.odometer,
                        serviceType = log.serviceType,
                        totalCost = log.totalCost,
                        notes = log.notes
                    )
                )
                is LogItem.Expense -> expenseRepo.deleteExpenseLog(
                    ExpenseLogEntity(
                        id = log.id,
                        vehicleId = log.vehicleId,
                        date = log.date,
                        category = log.category,
                        totalCost = log.totalCost,
                        notes = log.notes
                    )
                )
                is LogItem.Trip -> tripRepo.deleteTripLog(
                    TripLogEntity(
                        id = log.id,
                        vehicleId = log.vehicleId,
                        date = log.date,
                        startOdo = log.startOdo,
                        endOdo = log.endOdo,
                        purpose = log.purpose,
                        notes = log.notes
                    )
                )
            }
        }
    }
}

// ==========================================
// 3. ADD FILL-UP VIEW MODEL
// ==========================================

data class AddFillUpUiState(
    val selectedVehicle: VehicleEntity? = null,
    val date: Long = System.currentTimeMillis(),
    val odometer: String = "",
    val quantity: String = "",
    val pricePerUnit: String = "",
    val totalCost: String = "",
    val isPartialTank: Boolean = false,
    val stationName: String = "",
    val notes: String = "",
    val receiptPath: String? = null,
    val lastKnownOdometer: Double = 0.0,
    val odometerError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class AddFillUpViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val fuelRepo: FuelLogRepository,
    private val sessionManager: UserSessionManager,
    private val validateOdometer: ValidateOdometerUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddFillUpUiState())
    val uiState: StateFlow<AddFillUpUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.currentVehicleId.collectLatest { vehicleId ->
                if (vehicleId != null) {
                    val vehicle = vehicleRepo.getVehicleById(vehicleId)
                    val logs = fuelRepo.getFuelLogsSortedByOdometer(vehicleId)
                    val lastOdo = logs.lastOrNull()?.odometer ?: 0.0
                    _uiState.update {
                        it.copy(
                            selectedVehicle = vehicle,
                            lastKnownOdometer = lastOdo,
                            odometer = if (lastOdo > 0) lastOdo.toString() else ""
                        )
                    }
                }
            }
        }
    }

    fun onDateChanged(date: Long) {
        _uiState.update { it.copy(date = date) }
        validateOdometerChronologically()
    }

    fun onOdometerChanged(odo: String) {
        _uiState.update { it.copy(odometer = odo, odometerError = null) }
        validateOdometerChronologically()
    }

    // Bidirectional Calculation Engine
    fun onQuantityChanged(qty: String) {
        _uiState.update { state ->
            val qtyVal = qty.replace(',', '.').toDoubleOrNull() ?: 0.0
            val pPUVal = state.pricePerUnit.replace(',', '.').toDoubleOrNull() ?: 0.0
            val costVal = state.totalCost.replace(',', '.').toDoubleOrNull() ?: 0.0

            when {
                qtyVal > 0 && pPUVal > 0 -> {
                    state.copy(quantity = qty, totalCost = String.format(java.util.Locale.US, "%.2f", qtyVal * pPUVal))
                }
                qtyVal > 0 && costVal > 0 -> {
                    state.copy(quantity = qty, pricePerUnit = String.format(java.util.Locale.US, "%.2f", costVal / qtyVal))
                }
                else -> state.copy(quantity = qty)
            }
        }
    }

    fun onPricePerUnitChanged(ppu: String) {
        _uiState.update { state ->
            val pPUVal = ppu.replace(',', '.').toDoubleOrNull() ?: 0.0
            val qtyVal = state.quantity.replace(',', '.').toDoubleOrNull() ?: 0.0

            if (pPUVal > 0 && qtyVal > 0) {
                state.copy(pricePerUnit = ppu, totalCost = String.format(java.util.Locale.US, "%.2f", qtyVal * pPUVal))
            } else {
                state.copy(pricePerUnit = ppu)
            }
        }
    }

    fun onTotalCostChanged(cost: String) {
        _uiState.update { state ->
            val costVal = cost.replace(',', '.').toDoubleOrNull() ?: 0.0
            val qtyVal = state.quantity.replace(',', '.').toDoubleOrNull() ?: 0.0

            if (costVal > 0 && qtyVal > 0) {
                state.copy(totalCost = cost, pricePerUnit = String.format(java.util.Locale.US, "%.2f", costVal / qtyVal))
            } else {
                state.copy(totalCost = cost)
            }
        }
    }

    fun onPartialTankChanged(partial: Boolean) {
        _uiState.update { it.copy(isPartialTank = partial) }
    }

    fun onStationNameChanged(name: String) {
        _uiState.update { it.copy(stationName = name) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onReceiptAttached(path: String?) {
        _uiState.update { it.copy(receiptPath = path) }
    }

    private fun validateOdometerChronologically() {
        val vehicle = _uiState.value.selectedVehicle ?: return
        val odoVal = _uiState.value.odometer.replace(',', '.').toDoubleOrNull() ?: return
        viewModelScope.launch {
            val result = validateOdometer(vehicle.id, _uiState.value.date, odoVal)
            _uiState.update {
                when (result) {
                    is OdoValidationResult.Valid -> it.copy(odometerError = null)
                    is OdoValidationResult.InvalidBefore -> it.copy(
                        odometerError = "Reading is lower than a previous log on this date (${result.limit} ${vehicle.distanceUnit})"
                    )
                    is OdoValidationResult.InvalidAfter -> it.copy(
                        odometerError = "Reading is higher than a subsequent log on this date (${result.limit} ${vehicle.distanceUnit})"
                    )
                }
            }
        }
    }

    fun saveFillUp() {
        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return
        val odoVal = state.odometer.replace(',', '.').toDoubleOrNull()
        val qtyVal = state.quantity.replace(',', '.').toDoubleOrNull()
        val ppuVal = state.pricePerUnit.replace(',', '.').toDoubleOrNull()
        val costVal = state.totalCost.replace(',', '.').toDoubleOrNull()

        if (odoVal == null || qtyVal == null || ppuVal == null || costVal == null) {
            _uiState.update { it.copy(odometerError = "Please fill in all mandatory numerical fields correctly") }
            return
        }

        if (odoVal < 0 || qtyVal <= 0 || ppuVal <= 0 || costVal <= 0) {
            _uiState.update { it.copy(odometerError = "Odometer must be non-negative. Quantity, Price, and Cost must be greater than zero.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Re-validate odometer
            val validation = validateOdometer(vehicle.id, state.date, odoVal)
            if (validation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odometerError = when (validation) {
                            is OdoValidationResult.InvalidBefore -> "Must be >= previous odometer (${validation.limit})"
                            is OdoValidationResult.InvalidAfter -> "Must be <= subsequent odometer (${validation.limit})"
                            else -> "Invalid odometer reading"
                        }
                    )
                }
                return@launch
            }

            // Convert to base standard if required
            // Database stores distance in km, volume in Liters.
            val standardOdo = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(odoVal) else odoVal
            val standardQty = if (vehicle.fuelUnit == "Gallons") UnitConverter.gallonsToLiters(qtyVal) else qtyVal
            val standardPpu = if (vehicle.fuelUnit == "Gallons") costVal / standardQty else ppuVal

            val entity = FuelLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                odometer = standardOdo,
                quantity = standardQty,
                pricePerUnit = standardPpu,
                totalCost = costVal,
                isPartialTank = state.isPartialTank,
                stationName = state.stationName.ifBlank { null },
                notes = state.notes.ifBlank { null },
                receiptPath = state.receiptPath
            )

            fuelRepo.insertFuelLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
