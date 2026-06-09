package com.auto.odo.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.NavBarStyle
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.repository.VehicleRepository
import com.auto.odo.domain.usecase.ExportDataUseCase
import com.auto.odo.domain.usecase.ExportSummary
import com.auto.odo.domain.usecase.ImportDataUseCase
import com.auto.odo.domain.usecase.ImportExportResult
import com.auto.odo.domain.usecase.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────

/** All supported currencies. Each entry is Pair(code, symbol). */
val SUPPORTED_CURRENCIES: List<Pair<String, String>> = listOf(
    "INR" to "₹",
    "USD" to "$",
    "EUR" to "€",
    "GBP" to "£",
    "AED" to "د.إ",
    "BRL" to "R$"
)

fun currencySymbol(code: String): String =
    SUPPORTED_CURRENCIES.firstOrNull { it.first == code }?.second ?: code

// ─────────────────────────────────────────────────────────────────────────────
//  UI state
// ─────────────────────────────────────────────────────────────────────────────

data class SettingsUiState(
    // Vehicle list
    val vehicles: List<VehicleEntity> = emptyList(),
    val currentVehicleId: Long? = null,
    val activeVehicle: VehicleEntity? = null,  // convenience — vehicles.first { id == currentVehicleId }

    // Add-vehicle sheet
    val showAddVehicleSheet: Boolean = false,
    val newVehicleName: String = "",
    val newVehicleType: String = "Bike",
    val newVehicleFuelUnit: String = "Liters",
    val newVehicleDistUnit: String = "km",
    val newVehicleCurrency: String = "INR",
    val addVehicleError: String? = null,

    // Edit currency picker
    val vehiclePendingCurrencyEdit: VehicleEntity? = null,
    val editCurrencySelected: String = "INR",

    // UI Preferences
    val navBarStyle: NavBarStyle = NavBarStyle.SOLID,
    val fullScreenStatusBar: Boolean = false,
    val autoHideTitleBar: Boolean = true,

    // Delete confirmation
    val vehiclePendingDelete: VehicleEntity? = null,

    // Import / Export
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val showConfirmImportDialog: Boolean = false,
    val pendingImportUri: Uri? = null,
    val importResult: ImportSummary? = null,
    val exportResult: ExportSummary? = null,

    // Messages
    val errorMessage: String? = null,
    val successMessage: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val sessionManager: UserSessionManager,
    private val importDataUseCase: ImportDataUseCase,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            vehicleRepo.getAllVehicles().collect { vehicles ->
                _uiState.update { s ->
                    s.copy(
                        vehicles = vehicles,
                        activeVehicle = vehicles.firstOrNull { it.id == s.currentVehicleId }
                    )
                }
            }
        }
        viewModelScope.launch {
            sessionManager.currentVehicleId.collect { id ->
                _uiState.update { s ->
                    s.copy(
                        currentVehicleId = id,
                        activeVehicle = s.vehicles.firstOrNull { it.id == id }
                    )
                }
            }
        }
        viewModelScope.launch {
            sessionManager.navBarStyle.collect { style ->
                _uiState.update { it.copy(navBarStyle = style) }
            }
        }
        viewModelScope.launch {
            sessionManager.fullScreenStatusBar.collect { enabled ->
                _uiState.update { it.copy(fullScreenStatusBar = enabled) }
            }
        }
        viewModelScope.launch {
            sessionManager.autoHideTitleBar.collect { enabled ->
                _uiState.update { it.copy(autoHideTitleBar = enabled) }
            }
        }
    }

    // ── UI Preferences ──────────────────────────────────────────────────────

    fun setNavBarStyle(style: NavBarStyle) {
        viewModelScope.launch { sessionManager.setNavBarStyle(style) }
    }

    fun setFullScreenStatusBar(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setFullScreenStatusBar(enabled) }
    }

    fun setAutoHideTitleBar(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setAutoHideTitleBar(enabled) }
    }

    // ── Add vehicle ──────────────────────────────────────────────────────────

    fun openAddVehicleSheet() {
        _uiState.update {
            it.copy(
                showAddVehicleSheet = true,
                newVehicleName = "",
                newVehicleType = "Bike",
                newVehicleFuelUnit = "Liters",
                newVehicleDistUnit = "km",
                newVehicleCurrency = "INR",
                addVehicleError = null
            )
        }
    }

    fun closeAddVehicleSheet() =
        _uiState.update { it.copy(showAddVehicleSheet = false, addVehicleError = null) }

    fun onNewVehicleNameChanged(name: String) =
        _uiState.update { it.copy(newVehicleName = name, addVehicleError = null) }

    fun onNewVehicleTypeChanged(type: String) =
        _uiState.update { it.copy(newVehicleType = type) }

    fun onNewVehicleFuelUnitChanged(unit: String) =
        _uiState.update { it.copy(newVehicleFuelUnit = unit) }

    fun onNewVehicleDistUnitChanged(unit: String) =
        _uiState.update { it.copy(newVehicleDistUnit = unit) }

    fun onNewVehicleCurrencyChanged(currency: String) =
        _uiState.update { it.copy(newVehicleCurrency = currency) }

    fun saveNewVehicle() {
        val state = _uiState.value
        val name = state.newVehicleName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(addVehicleError = "Vehicle name cannot be empty") }
            return
        }
        if (state.vehicles.any { it.name.equals(name, ignoreCase = true) }) {
            _uiState.update { it.copy(addVehicleError = "A vehicle with this name already exists") }
            return
        }
        viewModelScope.launch {
            val vehicle = VehicleEntity(
                name = name,
                type = state.newVehicleType,
                fuelUnit = state.newVehicleFuelUnit,
                distanceUnit = state.newVehicleDistUnit,
                currency = state.newVehicleCurrency
            )
            val newId = vehicleRepo.insertVehicle(vehicle)
            if (state.vehicles.isEmpty()) sessionManager.setCurrentVehicleId(newId)
            _uiState.update {
                it.copy(showAddVehicleSheet = false, successMessage = "\"$name\" added successfully")
            }
        }
    }

    // ── Edit currency ────────────────────────────────────────────────────────

    fun openCurrencyEdit(vehicle: VehicleEntity) {
        _uiState.update {
            it.copy(
                vehiclePendingCurrencyEdit = vehicle,
                editCurrencySelected = vehicle.currency
            )
        }
    }

    fun dismissCurrencyEdit() =
        _uiState.update { it.copy(vehiclePendingCurrencyEdit = null) }

    fun onEditCurrencySelected(currency: String) =
        _uiState.update { it.copy(editCurrencySelected = currency) }

    fun saveCurrencyEdit() {
        val vehicle = _uiState.value.vehiclePendingCurrencyEdit ?: return
        val newCurrency = _uiState.value.editCurrencySelected
        _uiState.update { it.copy(vehiclePendingCurrencyEdit = null) }
        viewModelScope.launch {
            // Use UPDATE (not insertVehicle/INSERT OR REPLACE) to avoid
            // the SQLite REPLACE-then-CASCADE-DELETE bug that wipes all child logs.
            vehicleRepo.updateVehicle(vehicle.copy(currency = newCurrency))
            _uiState.update {
                it.copy(successMessage = "${vehicle.name} currency updated to $newCurrency")
            }
        }
    }

    // ── Delete vehicle ───────────────────────────────────────────────────────

    fun requestDeleteVehicle(vehicle: VehicleEntity) =
        _uiState.update { it.copy(vehiclePendingDelete = vehicle) }

    fun dismissDeleteDialog() =
        _uiState.update { it.copy(vehiclePendingDelete = null) }

    fun confirmDeleteVehicle() {
        val vehicle = _uiState.value.vehiclePendingDelete ?: return
        _uiState.update { it.copy(vehiclePendingDelete = null) }
        viewModelScope.launch {
            vehicleRepo.deleteVehicle(vehicle)
            if (_uiState.value.currentVehicleId == vehicle.id) {
                val remaining = _uiState.value.vehicles.firstOrNull { it.id != vehicle.id }
                sessionManager.setCurrentVehicleId(remaining?.id ?: -1L)
            }
            _uiState.update {
                it.copy(successMessage = "\"${vehicle.name}\" and all its records deleted")
            }
        }
    }

    // ── Active vehicle ───────────────────────────────────────────────────────

    fun selectVehicle(vehicleId: Long) {
        viewModelScope.launch { sessionManager.setCurrentVehicleId(vehicleId) }
    }

    // ── Import ───────────────────────────────────────────────────────────────

    fun onImportFolderSelected(folderUri: Uri) =
        _uiState.update { it.copy(showConfirmImportDialog = true, pendingImportUri = folderUri) }

    fun dismissImportDialog() =
        _uiState.update { it.copy(showConfirmImportDialog = false, pendingImportUri = null) }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.update {
            it.copy(showConfirmImportDialog = false, pendingImportUri = null,
                isImporting = true, errorMessage = null, successMessage = null)
        }
        viewModelScope.launch {
            when (val result = importDataUseCase(uri)) {
                is ImportExportResult.Success -> {
                    val s = result.data
                    _uiState.update {
                        it.copy(isImporting = false, importResult = s,
                            successMessage = "Import complete! ${s.vehicleCount} vehicles, " +
                                "${s.fuelCount} fuel, ${s.serviceCount} service, " +
                                "${s.expenseCount} expenses, ${s.tripCount} trips.")
                    }
                }
                is ImportExportResult.Error ->
                    _uiState.update { it.copy(isImporting = false, errorMessage = result.message) }
            }
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────

    fun onExportFolderSelected(folderUri: Uri) {
        _uiState.update { it.copy(isExporting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            when (val result = exportDataUseCase(folderUri)) {
                is ImportExportResult.Success -> {
                    val s = result.data
                    _uiState.update {
                        it.copy(isExporting = false, exportResult = s,
                            successMessage = "Export complete! ${s.vehicleCount} vehicles, " +
                                "${s.fuelCount} fuel, ${s.serviceCount} service, " +
                                "${s.expenseCount} expenses, ${s.tripCount} trips.")
                    }
                }
                is ImportExportResult.Error ->
                    _uiState.update { it.copy(isExporting = false, errorMessage = result.message) }
            }
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    fun clearMessage() =
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
}
