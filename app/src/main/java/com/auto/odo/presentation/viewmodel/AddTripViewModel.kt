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
    val vehicle = vehicleRepo.getVehicleById(vehicleId) ?: return@collectLatest
    val logs = fuelRepo.getFuelLogsSortedByOdometer(vehicleId)
    val lastOdoKm = logs.lastOrNull()?.odometer ?: 0.0

    val lastOdoDisplay =
        if (vehicle.distanceUnit == "miles")
            UnitConverter.kmToMiles(lastOdoKm)
        else
            lastOdoKm

    _uiState.update {
        it.copy(
            selectedVehicle = vehicle,
            lastKnownOdometer = lastOdoDisplay,
            startOdo = if (lastOdoDisplay > 0)
                String.format(java.util.Locale.US, "%.1f", lastOdoDisplay)
            else ""
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
                val startKm = toStoredDistance(startVal, vehicle)
                val startValidation = validateOdometer(vehicle.id, _uiState.value.date, startKm)
                if (startValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(odoError = formatTripValidationError("Start", startValidation, vehicle))
                    }
                    return@launch
                }
            }

            if (endVal != null) {
                val endKm = toStoredDistance(endVal, vehicle)
                val endValidation = validateOdometer(vehicle.id, _uiState.value.date, endKm)
                if (endValidation !is OdoValidationResult.Valid) {
                    _uiState.update {
                        it.copy(odoError = formatTripValidationError("End", endValidation, vehicle))
                    }
                    return@launch
                }
            }
        }
    }

    private fun toStoredDistance(value: Double, vehicle: VehicleEntity): Double =
        if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(value) else value

    private fun fromStoredDistance(value: Double, vehicle: VehicleEntity): Double =
        if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(value) else value

    private fun formatTripValidationError(
        label: String,
        validation: OdoValidationResult,
        vehicle: VehicleEntity
    ): String = when (validation) {
        is OdoValidationResult.InvalidBefore ->
            String.format(
                java.util.Locale.US,
                "$label odometer is lower than previous log (%.1f ${vehicle.distanceUnit})",
                fromStoredDistance(validation.limit, vehicle)
            )
        is OdoValidationResult.InvalidAfter ->
            String.format(
                java.util.Locale.US,
                "$label odometer is higher than subsequent log (%.1f ${vehicle.distanceUnit})",
                fromStoredDistance(validation.limit, vehicle)
            )
        else -> "Invalid ${label.lowercase()} odometer"
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

            val standardStart = toStoredDistance(startVal, vehicle)
            val standardEnd = toStoredDistance(endVal, vehicle)

            // Validate start
            val startValidation = validateOdometer(vehicle.id, state.date, standardStart)
            if (startValidation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odoError = formatTripValidationError("Start", startValidation, vehicle)
                    )
                }
                return@launch
            }

            // Validate end
            val endValidation = validateOdometer(vehicle.id, state.date, standardEnd)
            if (endValidation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odoError = formatTripValidationError("End", endValidation, vehicle)
                    )
                }
                return@launch
            }

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
