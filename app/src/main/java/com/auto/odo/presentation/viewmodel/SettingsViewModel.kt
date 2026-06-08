package com.auto.odo.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.domain.usecase.ExportDataUseCase
import com.auto.odo.domain.usecase.ExportSummary
import com.auto.odo.domain.usecase.ImportDataUseCase
import com.auto.odo.domain.usecase.ImportExportResult
import com.auto.odo.domain.usecase.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val showConfirmImportDialog: Boolean = false,
    val pendingImportUri: Uri? = null,
    val importResult: ImportSummary? = null,
    val exportResult: ExportSummary? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val importDataUseCase: ImportDataUseCase,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Called when the user picks a folder for import — shows confirmation dialog first. */
    fun onImportFolderSelected(folderUri: Uri) {
        _uiState.value = _uiState.value.copy(
            showConfirmImportDialog = true,
            pendingImportUri = folderUri
        )
    }

    /** User dismissed the confirmation dialog without confirming. */
    fun dismissImportDialog() {
        _uiState.value = _uiState.value.copy(
            showConfirmImportDialog = false,
            pendingImportUri = null
        )
    }

    /** User confirmed the import dialog — run the import. */
    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.value = _uiState.value.copy(
            showConfirmImportDialog = false,
            pendingImportUri = null,
            isImporting = true,
            errorMessage = null,
            successMessage = null
        )
        viewModelScope.launch {
            when (val result = importDataUseCase(uri)) {
                is ImportExportResult.Success -> {
                    val s = result.data
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importResult = s,
                        successMessage = "Import complete! " +
                            "${s.vehicleCount} vehicles, ${s.fuelCount} fuel logs, " +
                            "${s.serviceCount} service records, ${s.expenseCount} expenses, " +
                            "${s.tripCount} trips."
                    )
                }
                is ImportExportResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /** Called when the user picks a folder for export. */
    fun onExportFolderSelected(folderUri: Uri) {
        _uiState.value = _uiState.value.copy(
            isExporting = true,
            errorMessage = null,
            successMessage = null
        )
        viewModelScope.launch {
            when (val result = exportDataUseCase(folderUri)) {
                is ImportExportResult.Success -> {
                    val s = result.data
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportResult = s,
                        successMessage = "Export complete! " +
                            "${s.vehicleCount} vehicles, ${s.fuelCount} fuel logs, " +
                            "${s.serviceCount} service records, ${s.expenseCount} expenses, " +
                            "${s.tripCount} trips written."
                    )
                }
                is ImportExportResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /** Clear the success/error messages (after they've been shown). */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }
}
