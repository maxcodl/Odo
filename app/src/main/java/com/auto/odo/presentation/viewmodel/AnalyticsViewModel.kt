package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.data.dao.*
import com.auto.odo.data.entity.VehicleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. The "State" holds the exact numbers the UI needs to draw
data class AnalyticsUiState(
    val activeVehicle: VehicleEntity? = null,
    val totalFuelCost: Double = 0.0,
    val totalServiceCost: Double = 0.0,
    val totalExpenseCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val costPerDistanceUnit: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val fuelLogDao: FuelLogDao,
    private val serviceLogDao: ServiceLogDao,
    private val expenseLogDao: ExpenseLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            // Get the first vehicle (or however you track the 'active' one)
            val vehicle = vehicleDao.getAllVehiclesList().firstOrNull()
            
            if (vehicle == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Fetch the raw numbers from the DAOs we just updated
            val fuelCost = fuelLogDao.getTotalFuelCost(vehicle.id) ?: 0.0
            val serviceCost = serviceLogDao.getTotalServiceCost(vehicle.id) ?: 0.0
            val expenseCost = expenseLogDao.getTotalExpenseCost(vehicle.id) ?: 0.0
            
            val totalCost = fuelCost + serviceCost + expenseCost

            // Get distance to calculate Cost per Km/Mile
            val minOdo = fuelLogDao.getMinOdometer(vehicle.id) ?: 0.0
            val maxOdo = fuelLogDao.getMaxOdometer(vehicle.id) ?: 0.0
            val distance = maxOdo - minOdo
            
            val costPerUnit = if (distance > 0) totalCost / distance else 0.0

            // Push the final math to the UI State
            _uiState.update { 
                it.copy(
                    activeVehicle = vehicle,
                    totalFuelCost = fuelCost,
                    totalServiceCost = serviceCost,
                    totalExpenseCost = expenseCost,
                    totalCost = totalCost,
                    costPerDistanceUnit = costPerUnit,
                    isLoading = false
                )
            }
        }
    }
}