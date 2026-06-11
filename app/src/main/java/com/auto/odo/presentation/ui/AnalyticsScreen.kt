package com.auto.odo.presentation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.presentation.viewmodel.AnalyticsViewModel
import com.auto.odo.presentation.viewmodel.currencySymbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    fullScreenStatusBar: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

Scaffold(
        modifier = Modifier.fillMaxSize(),
        // FIX 1: Tell the Scaffold to actually paint your theme's background color!
        containerColor = MaterialTheme.colorScheme.background, 
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.Bold) },
                // FIX 2: Only make the top bar transparent if Edge-to-Edge is enabled
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (fullScreenStatusBar) Color.Transparent else MaterialTheme.colorScheme.background,
                    scrolledContainerColor = if (fullScreenStatusBar) Color.Transparent else MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.activeVehicle == null) {
                Text("Please add a vehicle first.")
            } else {
                val sym = currencySymbol(uiState.activeVehicle!!.currency)
                val dist = uiState.activeVehicle!!.distanceUnit

                // 1. Big Summary Card
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Running Cost", style = MaterialTheme.typography.labelMedium)
                        Text("$sym ${"%.2f".format(uiState.totalCost)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("${sym}${"%.2f".format(uiState.costPerDistanceUnit)} per $dist", color = MaterialTheme.colorScheme.primary)
                    }
                }

                // 2. Breakdown
                Text("Cost Breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                
                Text("Fuel: $sym ${"%.2f".format(uiState.totalFuelCost)}")
                Text("Service: $sym ${"%.2f".format(uiState.totalServiceCost)}")
                Text("Expenses: $sym ${"%.2f".format(uiState.totalExpenseCost)}")
                
                // Vico Charts will go here later!
            }
        }
    }
}