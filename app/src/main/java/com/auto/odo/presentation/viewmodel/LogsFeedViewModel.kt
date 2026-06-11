package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import com.auto.odo.domain.usecase.GetLogsFeedUseCase
import com.auto.odo.domain.usecase.LogItem
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LogsFeedUiState(
    val selectedVehicle: VehicleEntity? = null,
    val logs: List<LogItem> = emptyList(),
    val activeFilter: String? = null,
    val isLoading: Boolean = true,
    val pendingDeleteLog: LogItem? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    private var undoJob: Job? = null
    private val _pendingDeleteLog = MutableStateFlow<LogItem?>(null)

    init {
        viewModelScope.launch {
            // FIX: Previously this called getAllVehicles() and then scanned the whole list
            // every time any vehicle changed. Now it uses getVehicleByIdFlow() which is a
            // targeted query that only fires when THIS vehicle's data changes.
            val activeVehicleFlow = sessionManager.currentVehicleId.flatMapLatest { vehicleId ->
                if (vehicleId == null) flowOf(null)
                else vehicleRepo.getVehicleByIdFlow(vehicleId)
            }.distinctUntilChanged()

            activeVehicleFlow.flatMapLatest { vehicle ->
                if (vehicle == null) {
                    flowOf(LogsFeedUiState(isLoading = false))
                } else {
                    _filter.flatMapLatest { filterType ->
                        combine(
                            getLogsFeed(vehicle.id, filterType),
                            _pendingDeleteLog
                        ) { logs, pending ->
                            val visibleLogs = logs.filter { log ->
                                pending == null || log.id != pending.id || log.javaClass.simpleName != pending.javaClass.simpleName
                            }
                            LogsFeedUiState(
                                selectedVehicle = vehicle,
                                logs = visibleLogs,
                                activeFilter = filterType,
                                pendingDeleteLog = pending,
                                isLoading = false
                            )
                        }
                    }
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setFilter(filter: String?) {
        _filter.value = filter
    }

    fun deleteLog(log: LogItem) {
        undoJob?.cancel()
        val existing = _uiState.value.pendingDeleteLog
        if (existing != null && existing.id != log.id) {
            viewModelScope.launch { commitDelete(existing) }
        }

        _pendingDeleteLog.value = log

        undoJob = viewModelScope.launch {
            delay(4_000L)
            _pendingDeleteLog.value = null
            commitDelete(log)
        }
    }

    fun undoDelete() {
        undoJob?.cancel()
        undoJob = null
        _pendingDeleteLog.value = null
    }

    private suspend fun commitDelete(log: LogItem) {
        when (log) {
            is LogItem.Fuel -> fuelRepo.deleteFuelLog(
                FuelLogEntity(
                    id = log.id, vehicleId = log.vehicleId, date = log.date,
                    odometer = log.odometer, quantity = log.quantity,
                    pricePerUnit = log.pricePerUnit, totalCost = log.totalCost,
                    isPartialTank = log.isPartialTank, stationName = log.stationName,
                    notes = log.notes, receiptPath = log.receiptPath
                )
            )
            is LogItem.Service -> serviceRepo.deleteServiceLog(
                ServiceLogEntity(
                    id = log.id, vehicleId = log.vehicleId, date = log.date,
                    odometer = log.odometer, serviceType = log.serviceType,
                    totalCost = log.totalCost, notes = log.notes
                )
            )
            is LogItem.Expense -> expenseRepo.deleteExpenseLog(
                ExpenseLogEntity(
                    id = log.id, vehicleId = log.vehicleId, date = log.date,
                    category = log.category, totalCost = log.totalCost, notes = log.notes
                )
            )
            is LogItem.Trip -> tripRepo.deleteTripLog(
                TripLogEntity(
                    id = log.id, vehicleId = log.vehicleId, date = log.date,
                    startOdo = log.startOdo, endOdo = log.endOdo,
                    purpose = log.purpose, notes = log.notes
                )
            )
        }
    }
}