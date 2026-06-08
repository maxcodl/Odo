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

data class UpdateOdometerUiState(
    val selectedVehicle: VehicleEntity? = null,
    val date: Long = System.currentTimeMillis(),
    val odometer: String = "",
    val lastKnownOdometer: Double = 0.0,
    val odometerError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class UpdateOdometerViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val fuelRepo: FuelLogRepository,
    private val serviceRepo: ServiceLogRepository,
    private val sessionManager: UserSessionManager,
    private val validateOdometer: ValidateOdometerUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateOdometerUiState())
    val uiState: StateFlow<UpdateOdometerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.currentVehicleId.collectLatest { vehicleId ->
                if (vehicleId != null) {
                    val vehicle = vehicleRepo.getVehicleById(vehicleId)
                    val logs = fuelRepo.getFuelLogsSortedByOdometer(vehicleId)
                    val lastOdoKm = logs.lastOrNull()?.odometer ?: 0.0
                    val lastOdoDisplay = if (vehicle?.distanceUnit == "miles")
                        UnitConverter.kmToMiles(lastOdoKm) else lastOdoKm
                    _uiState.update {
                        it.copy(
                            selectedVehicle = vehicle,
                            lastKnownOdometer = lastOdoDisplay,
                            odometer = if (lastOdoDisplay > 0)
                                String.format(java.util.Locale.US, "%.1f", lastOdoDisplay) else ""
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

    private fun validateOdometerChronologically() {
        val vehicle = _uiState.value.selectedVehicle ?: return
        val odoDisplayVal = _uiState.value.odometer.replace(',', '.').toDoubleOrNull() ?: return
        val odoKm = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(odoDisplayVal) else odoDisplayVal
        viewModelScope.launch {
            val result = validateOdometer(vehicle.id, _uiState.value.date, odoKm)
            _uiState.update {
                when (result) {
                    is OdoValidationResult.Valid -> it.copy(odometerError = null)
                    is OdoValidationResult.InvalidBefore -> {
                        val lim = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(result.limit) else result.limit
                        it.copy(odometerError = "Reading is lower than a previous log (%.1f ${vehicle.distanceUnit})".format(lim))
                    }
                    is OdoValidationResult.InvalidAfter -> {
                        val lim = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(result.limit) else result.limit
                        it.copy(odometerError = "Reading is higher than a subsequent log (%.1f ${vehicle.distanceUnit})".format(lim))
                    }
                }
            }
        }
    }

    fun saveOdometer() {
        val state = _uiState.value
        val vehicle = state.selectedVehicle ?: return
        val odoVal = state.odometer.replace(',', '.').toDoubleOrNull()

        if (odoVal == null) {
            _uiState.update { it.copy(odometerError = "Please fill in odometer field correctly") }
            return
        }

        if (odoVal < 0) {
            _uiState.update { it.copy(odometerError = "Odometer must be non-negative value") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val standardOdo = if (vehicle.distanceUnit == "miles") UnitConverter.milesToKm(odoVal) else odoVal
            val validation = validateOdometer(vehicle.id, state.date, standardOdo)
            if (validation !is OdoValidationResult.Valid) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        odometerError = when (validation) {
                            is OdoValidationResult.InvalidBefore -> {
                                val lim = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(validation.limit) else validation.limit
                                "Must be >= previous odometer (%.1f ${vehicle.distanceUnit})".format(lim)
                            }
                            is OdoValidationResult.InvalidAfter -> {
                                val lim = if (vehicle.distanceUnit == "miles") UnitConverter.kmToMiles(validation.limit) else validation.limit
                                "Must be <= subsequent odometer (%.1f ${vehicle.distanceUnit})".format(lim)
                            }
                            else -> "Invalid odometer reading"
                        }
                    )
                }
                return@launch
            }

            val entity = ServiceLogEntity(
                vehicleId = vehicle.id,
                date = state.date,
                odometer = standardOdo,
                serviceType = "Odometer Update",
                totalCost = 0.0,
                notes = "Manual Odometer Update"
            )

            serviceRepo.insertServiceLog(entity)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
