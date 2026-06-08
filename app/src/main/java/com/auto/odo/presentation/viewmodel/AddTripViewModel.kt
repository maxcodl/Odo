package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.TripLogEntity
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.FuelLogRepository
import com.auto.odo.domain.repository.TripLogRepository
import com.auto.odo.domain.repository.VehicleRepository
import com.auto.odo.domain.usecase.OdoValidationResult
import com.auto.odo.domain.usecase.ValidateOdometerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTripUiState(
    val selectedVehicle: VehicleEntity? = null,
    val date: Long = System.currentTimeMillis(),
    val startOdo: String = "",
    val endOdo: String = "",
    val purpose: String = "Personal",
    val notes: String = "",
    val distanceDisplay: String = "0",
    val lastKnownOdometer: Double = 0.0,
    val odoError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class AddTripViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val fuelRepo: FuelLogRepository,
    private val tripRepo: TripLogRepository,
    private val sessionManager: UserSessionManager,
    private val validateOdometer: ValidateOdometerUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTripUiState())
    val uiState: StateFlow<AddTripUiState> = _uiState.asStateFlow()

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
                            startOdo = if (lastOdo > 0) lastOdo.toString() else ""
                        )
                    }
                }
            }
        }
    }

    fun onDateChanged(date: Long) {
        _uiState.update { it.copy(date = date) }
        validateOdometers()
    }

    fun onStartOdoChanged(odo: String) {
        _uiState.update { state ->
            val startVal = odo.replace(',', '.').toDoubleOrNull() ?: 0.0
            val endVal = state.endOdo.replace(',', '.').toDoubleOrNull() ?: 0.0
            val diff = if (endVal > startVal) (endVal - startVal) else 0.0
            state.copy(
                startOdo = odo,
                distanceDisplay = String.format(java.util.Locale.US, "%.1f", diff),
                odoError = null
            )
        }
        validateOdometers()
    }

    fun onEndOdoChanged(odo: String) {
        _uiState.update { state ->
            val startVal = state.startOdo.replace(',', '.').toDoubleOrNull() ?: 0.0
            val endVal = odo.replace(',', '.').toDoubleOrNull() ?: 0.0
            val diff = if (endVal > startVal) (endVal - startVal) else 0.0
            state.copy(
                endOdo = odo,
                distanceDisplay = String.format(java.util.Locale.US, "%.1f", diff),
                odoError = null
            )
        }
        validateOdometers()
    }

    fun onPurposeChanged(purpose: String) {
        _uiState.update { it.copy(purpose = purpose) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    private fun validateOdometers() {
        val vehicle = _uiState.value.selectedVehicle ?: return
        val startVal = _uiState.value.startOdo.replace(',', '.').toDoubleOrNull()
        val endVal = _uiState.value.endOdo.replace(',', '.').toDoubleOrNull()

        if (startVal != null && endVal != null && endVal < startVal) {
            _uiState.update { it.copy(odoError = "End Odometer cannot be less than Start Odometer") }
            return
        }

        viewModelScope.launch {
            if (startVal != null) {
                val startValidation = validateOdometer(vehicle.id, _uiState.value.date, startVal)
                if (startValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(
                            odoError = when (startValidation) {
                                is OdoValidationResult.InvalidBefore -> "Start odometer is lower than previous log (${startValidation.limit} ${vehicle.distanceUnit})"
                                is OdoValidationResult.InvalidAfter -> "Start odometer is higher than subsequent log (${startValidation.limit} ${vehicle.distanceUnit})"
                                else -> "Invalid start odometer"
                            }
                        )
                    }
                    return@launch
                }
            }

            if (endVal != null) {
                val endValidation = validateOdometer(vehicle.id, _uiState.value.date, endVal)
                if (endValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(
                            odoError = when (endValidation) {
                                is OdoValidationResult.InvalidBefore -> "End odometer is lower than previous log (${endValidation.limit} ${vehicle.distanceUnit})"
                                is OdoValidationResult.InvalidAfter -> "End odometer is higher than subsequent log (${endValidation.limit} ${vehicle.distanceUnit})"
                                else -> "Invalid end odometer"
                            }
                        )
                    }
                    return@launch
                }
            }
        }
    }

    fun saveTrip() {
        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return
        val startVal = state.startOdo.replace(',', '.').toDoubleOrNull()
        val endVal = state.endOdo.replace(',', '.').toDoubleOrNull()

        if (startVal == null || endVal == null) {
            _uiState.update { it.copy(odoError = "Please fill in all mandatory fields correctly") }
            return
        }

        if (startVal < 0 || endVal < startVal) {
            _uiState.update { it.copy(odoError = "Start Odometer must be non-negative, End Odometer must be >= Start Odometer") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Validate start
            val startValidation = validateOdometer(vehicle.id, state.date, startVal)
            if (startValidation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odoError = "Start odometer is invalid chronologically"
                    )
                }
                return@launch
            }

            // Validate end
            val endValidation = validateOdometer(vehicle.id, state.date, endVal)
            if (endValidation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odoError = "End odometer is invalid chronologically"
                    )
                }
                return@launch
            }

            val standardStart = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(startVal) else startVal
            val standardEnd = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(endVal) else endVal

            val entity = TripLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                startOdo = standardStart,
                endOdo = standardEnd,
                purpose = state.purpose,
                notes = state.notes.ifBlank { null }
            )

            tripRepo.insertTripLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
