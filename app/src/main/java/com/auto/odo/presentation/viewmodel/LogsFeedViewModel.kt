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

enum class FeedType(val label: String) {
    ALL("All"), FUEL("Fuel"), SERVICE("Service"), EXPENSE("Expense"), TRIP("Trips");

    fun matches(log: LogItem) = when (this) {
        ALL -> true
        FUEL -> log is LogItem.Fuel
        SERVICE -> log is LogItem.Service
        EXPENSE -> log is LogItem.Expense
        TRIP -> log is LogItem.Trip
    }
}

enum class FeedSort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), COST_HIGH("Highest cost"), COST_LOW("Lowest cost")
}

@Immutable
data class FeedFilters(
    val month: String? = null, // "yyyy-MM"
    val minCost: Double? = null,
    val maxCost: Double? = null,
    val query: String = "",
    val sort: FeedSort = FeedSort.NEWEST
) {
    val isActive: Boolean get() = month != null || minCost != null || maxCost != null || query.isNotBlank() || sort != FeedSort.NEWEST
}

private val monthKeyFormat get() = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)

fun monthKey(date: Long): String = monthKeyFormat.format(java.util.Date(date))

private fun LogItem.searchText(): String = when (this) {
    is LogItem.Fuel -> listOfNotNull(stationName, notes, "fuel fill-up")
    is LogItem.Service -> listOfNotNull(serviceType, notes, "service")
    is LogItem.Expense -> listOfNotNull(category, notes, "expense")
    is LogItem.Trip -> listOfNotNull(purpose, startPlace, endPlace, notes, "trip")
}.joinToString(" ")

/** Everything except the type filter, which each pager page applies itself. */
fun applyFeedFilters(logs: List<LogItem>, f: FeedFilters): List<LogItem> {
    val q = f.query.trim()
    val filtered = logs.filter { log ->
        (f.month == null || monthKey(log.date) == f.month) &&
            (f.minCost == null || log.totalCost >= f.minCost) &&
            (f.maxCost == null || log.totalCost <= f.maxCost) &&
            (q.isEmpty() || log.searchText().contains(q, ignoreCase = true))
    }
    return when (f.sort) {
        FeedSort.NEWEST -> filtered.sortedByDescending { it.date }
        FeedSort.OLDEST -> filtered.sortedBy { it.date }
        FeedSort.COST_HIGH -> filtered.sortedByDescending { it.totalCost }
        FeedSort.COST_LOW -> filtered.sortedBy { it.totalCost }
    }
}

@Immutable
data class LogsFeedUiState(
    val selectedVehicle: VehicleEntity? = null,
    val allLogs: List<LogItem> = emptyList(), // unfiltered: efficiency needs every fill-up
    val logs: List<LogItem> = emptyList(),    // after month / cost / search / sort
    val months: List<String> = emptyList(),   // "yyyy-MM" with any logs, newest first
    val filters: FeedFilters = FeedFilters(),
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

    private val _filters = MutableStateFlow(FeedFilters())
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
                    combine(
                        getLogsFeed(vehicle.id, null),
                        _filters,
                        _pendingDeleteLog
                    ) { logs, filters, pending ->
                        val visibleLogs = logs.filter { log ->
                            pending == null || log.id != pending.id || log.javaClass.simpleName != pending.javaClass.simpleName
                        }
                        LogsFeedUiState(
                            selectedVehicle = vehicle,
                            allLogs = visibleLogs,
                            logs = applyFeedFilters(visibleLogs, filters),
                            months = visibleLogs.map { monthKey(it.date) }.distinct().sortedDescending(),
                            filters = filters,
                            pendingDeleteLog = pending,
                            isLoading = false
                        )
                    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setMonth(month: String?) = _filters.update { it.copy(month = month) }
    fun setCostRange(min: Double?, max: Double?) = _filters.update { it.copy(minCost = min, maxCost = max) }
    fun setQuery(query: String) = _filters.update { it.copy(query = query) }
    fun setSort(sort: FeedSort) = _filters.update { it.copy(sort = sort) }
    fun clearFilters() { _filters.value = FeedFilters() }

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