package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.*
import com.auto.odo.domain.repository.*
import com.auto.odo.domain.usecase.GetLogsFeedUseCase
import com.auto.odo.domain.usecase.LogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsFeedUiState(
    val selectedVehicle: VehicleEntity? = null,
    val logs: List<LogItem> = emptyList(),
    val activeFilter: String? = null,
    val isLoading: Boolean = true,
    /** Non-null while the undo window is open for a recently swiped log. */
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

    /** Tracks the coroutine running the 4-second undo countdown. */
    private var undoJob: Job? = null

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

    // ── Delete with undo ─────────────────────────────────────────────────────

    /**
     * Called when the user swipes a log. Stores it as [pendingDeleteLog]
     * and starts a 4-second countdown. If the user doesn't tap Undo before
     * the countdown finishes, the item is permanently deleted.
     */
    fun deleteLog(log: LogItem) {
        // Cancel any previous pending-delete countdown first
        undoJob?.cancel()
        // If there was already a pending delete (user swiped a second item),
        // commit the previous one immediately before accepting the new one.
        val existing = _uiState.value.pendingDeleteLog
        if (existing != null && existing.id != log.id) {
            viewModelScope.launch { commitDelete(existing) }
        }

        _uiState.update { it.copy(pendingDeleteLog = log) }

        undoJob = viewModelScope.launch {
            delay(4_000L) // 4-second undo window
            _uiState.update { it.copy(pendingDeleteLog = null) }
            commitDelete(log)
        }
    }

    /** User tapped Undo — cancel the countdown and restore the item to the list. */
    fun undoDelete() {
        undoJob?.cancel()
        undoJob = null
        _uiState.update { it.copy(pendingDeleteLog = null) }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

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
