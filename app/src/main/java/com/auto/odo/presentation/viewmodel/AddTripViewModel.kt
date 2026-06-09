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
                    val lastOdoKm = logs.lastOrNull()?.odometer ?: 0.0
                    val displayLastOdo = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(lastOdoKm) else lastOdoKm
                    _uiState.update {
                        it.copy(
                            selectedVehicle = vehicle,
                            lastKnownOdometer = displayLastOdo,
                            startOdo = if (displayLastOdo > 0) String.format(java.util.Locale.US, "%.1f", displayLastOdo) else ""
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
        val startValRaw = _uiState.value.startOdo.replace(',', '.').toDoubleOrNull()
        val endValRaw = _uiState.value.endOdo.replace(',', '.').toDoubleOrNull()

        if (startValRaw != null && endValRaw != null && endValRaw < startValRaw) {
            _uiState.update { it.copy(odoError = "End Odometer cannot be less than Start Odometer") }
            return
        }

        viewModelScope.launch {
            if (startValRaw != null) {
                val startValKm = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(startValRaw) else startValRaw
                val startValidation = validateOdometer(vehicle.id, _uiState.value.date, startValKm)
                if (startValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(
                            odoError = when (startValidation) {
                                is OdoValidationResult.InvalidBefore -> {
                                    val limitDisplay = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(startValidation.limit) else startValidation.limit
                                    "Start odometer is lower than previous log (${String.format(java.util.Locale.US, "%.1f", limitDisplay)} ${vehicle.distanceUnit})"
                                }
                                is OdoValidationResult.InvalidAfter -> {
                                    val limitDisplay = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(startValidation.limit) else startValidation.limit
                                    "Start odometer is higher than subsequent log (${String.format(java.util.Locale.US, "%.1f", limitDisplay)} ${vehicle.distanceUnit})"
                                }
                                else -> "Invalid start odometer"
                            }
                        )
                    }
                    return@launch
                }
            }

            if (endValRaw != null) {
                val endValKm = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(endValRaw) else endValRaw
                val endValidation = validateOdometer(vehicle.id, _uiState.value.date, endValKm)
                if (endValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(
                            odoError = when (endValidation) {
                                is OdoValidationResult.InvalidBefore -> {
                                    val limitDisplay = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(endValidation.limit) else endValidation.limit
                                    "End odometer is lower than previous log (${String.format(java.util.Locale.US, "%.1f", limitDisplay)} ${vehicle.distanceUnit})"
                                }
                                is OdoValidationResult.InvalidAfter -> {
                                    val limitDisplay = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(endValidation.limit) else endValidation.limit
                                    "End odometer is higher than subsequent log (${String.format(java.util.Locale.US, "%.1f", limitDisplay)} ${vehicle.distanceUnit})"
                                }
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
        val startValRaw = state.startOdo.replace(',', '.').toDoubleOrNull()
        val endValRaw = state.endOdo.replace(',', '.').toDoubleOrNull()

        if (startValRaw == null || endValRaw == null) {
            _uiState.update { it.copy(odoError = "Please fill in all mandatory fields correctly") }
            return
        }

        if (startValRaw < 0 || endValRaw < startValRaw) {
            _uiState.update { it.copy(odoError = "Start Odometer must be non-negative, End Odometer must be >= Start Odometer") }
            return
        }

        val startValKm = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(startValRaw) else startValRaw
        val endValKm = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(endValRaw) else endValRaw

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Validate start
            val startValidation = validateOdometer(vehicle.id, state.date, startValKm)
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
            val endValidation = validateOdometer(vehicle.id, state.date, endValKm)
            if (endValidation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odoError = "End odometer is invalid chronologically"
                    )
                }
                return@launch
            }

            val entity = TripLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                startOdo = startValKm,
                endOdo = endValKm,
                purpose = state.purpose,
                notes = state.notes.ifBlank { null }
            )

            tripRepo.insertTripLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
