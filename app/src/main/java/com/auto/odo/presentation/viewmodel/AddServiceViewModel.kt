package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.ServiceLogEntity
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.FuelLogRepository
import com.auto.odo.domain.repository.ServiceLogRepository
import com.auto.odo.domain.repository.VehicleRepository
import com.auto.odo.domain.usecase.OdoValidationResult
import com.auto.odo.domain.usecase.ValidateOdometerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddServiceUiState(
    val selectedVehicle: VehicleEntity? = null,
    val date: Long = System.currentTimeMillis(),
    val odometer: String = "",
    val serviceType: String = "",
    val totalCost: String = "",
    val notes: String = "",
    val lastKnownOdometer: Double = 0.0,
    val odometerError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class AddServiceViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val sessionManager: UserSessionManager,
    private val validateOdometer: ValidateOdometerUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddServiceUiState())
    val uiState: StateFlow<AddServiceUiState> = _uiState.asStateFlow()

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

    fun onServiceTypeChanged(type: String) {
        _uiState.update { it.copy(serviceType = type) }
    }

    fun onTotalCostChanged(cost: String) {
        _uiState.update { it.copy(totalCost = cost) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
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

    fun saveService() {
        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return
        val odoVal = state.odometer.replace(',', '.').toDoubleOrNull()
        val costVal = state.totalCost.replace(',', '.').toDoubleOrNull()
        val typeVal = state.serviceType

        if (odoVal == null || costVal == null || typeVal.isBlank()) {
            _uiState.update { it.copy(odometerError = "Please fill in all mandatory fields correctly") }
            return
        }

        if (odoVal < 0 || costVal < 0) {
            _uiState.update { it.copy(odometerError = "Odometer and Cost must be non-negative values") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

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

            val standardOdo = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(odoVal) else odoVal

            val entity = ServiceLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                odometer = standardOdo,
                serviceType = typeVal,
                totalCost = costVal,
                notes = state.notes.ifBlank { null }
            )

            serviceRepo.insertServiceLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
