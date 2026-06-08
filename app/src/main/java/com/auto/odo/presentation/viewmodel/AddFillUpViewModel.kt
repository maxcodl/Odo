package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.FuelLogEntity
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.FuelLogRepository
import com.auto.odo.domain.repository.VehicleRepository
import com.auto.odo.domain.usecase.OdoValidationResult
import com.auto.odo.domain.usecase.ValidateOdometerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
