package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.ExpenseLogEntity
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.ExpenseLogRepository
import com.auto.odo.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddExpenseUiState(
    val selectedVehicle: VehicleEntity? = null,
    val date: Long = System.currentTimeMillis(),
    val category: String = "",
    val totalCost: String = "",
    val notes: String = "",
    val costError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val expenseRepo: ExpenseLogRepository,
    private val sessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.currentVehicleId.collectLatest { vehicleId ->
                if (vehicleId != null) {
                    val vehicle = vehicleRepo.getVehicleById(vehicleId)
                    _uiState.update {
                        it.copy(selectedVehicle = vehicle)
                    }
                }
            }
        }
    }

    fun onDateChanged(date: Long) {
        _uiState.update { it.copy(date = date) }
    }

    fun onCategoryChanged(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onTotalCostChanged(cost: String) {
        _uiState.update { it.copy(totalCost = cost, costError = null) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun saveExpense() {
        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return
        val costVal = state.totalCost.replace(',', '.').toDoubleOrNull()
        val categoryVal = state.category

        if (costVal == null || categoryVal.isBlank()) {
            _uiState.update { it.copy(costError = "Please fill in all mandatory fields correctly") }
            return
        }

        if (costVal <= 0) {
            _uiState.update { it.copy(costError = "Cost must be greater than zero") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val entity = ExpenseLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                category = categoryVal,
                totalCost = costVal,
                notes = state.notes.ifBlank { null }
            )

            expenseRepo.insertExpenseLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
