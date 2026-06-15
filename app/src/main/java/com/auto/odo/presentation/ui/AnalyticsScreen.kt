package com.auto.odo.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        // VISUAL BUG FIX & EDGE-TO-EDGE:
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background, 
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.Bold) },
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.allVehicles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please add a vehicle first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val sym = currencySymbol(uiState.activeVehicle?.currency ?: "USD")
                val dist = uiState.activeVehicle?.distanceUnit ?: "km"
                val fuelUnit = uiState.activeVehicle?.fuelUnit ?: "Liters"

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp), // Clear the navbar
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // SECTION 0: Vehicle Selector Dropdown
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Card(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (uiState.isAllVehiclesSelected) "All Vehicles" else uiState.activeVehicle?.name ?: "All Vehicles",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                if (uiState.allVehicles.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("All Vehicles (Aggregated)") },
                                        onClick = { viewModel.selectVehicle(-1L); expanded = false }
                                    )
                                }
                                uiState.allVehicles.forEach { vehicle ->
                                    DropdownMenuItem(
                                        text = { Text(vehicle.name) },
                                        onClick = { viewModel.selectVehicle(vehicle.id); expanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 1: Summary Card (Theme Fixed!)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            // THEME FIX: Changed from clashing primaryContainer to seamless Surface
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Total Running Cost", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$sym ${"%.2f".format(uiState.totalCost)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text("Cost per $dist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$sym${"%.2f".format(uiState.costPerDistanceUnit)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Projected Yearly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$sym${"%.0f".format(uiState.projectedYearlyCost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: Donut Chart (Cost Breakdown)
                    item {
                        if (uiState.totalCost > 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Cost Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val fuelColor = MaterialTheme.colorScheme.primary
                                        val serviceColor = MaterialTheme.colorScheme.secondary
                                        val expenseColor = MaterialTheme.colorScheme.tertiary
                                        
                                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokeWidth = 24.dp.toPx()
                                                val radius = (size.minDimension - strokeWidth) / 2
                                                val center = Offset(size.width / 2, size.height / 2)
                                                
                                                val fuelSweep = (uiState.totalFuelCost / uiState.totalCost).toFloat() * 360f
                                                val serviceSweep = (uiState.totalServiceCost / uiState.totalCost).toFloat() * 360f
                                                val expenseSweep = (uiState.totalExpenseCost / uiState.totalCost).toFloat() * 360f

                                                if (fuelSweep > 0) drawArc(fuelColor, -90f, fuelSweep, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                                if (serviceSweep > 0) drawArc(serviceColor, -90f + fuelSweep, serviceSweep, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                                if (expenseSweep > 0) drawArc(expenseColor, -90f + fuelSweep + serviceSweep, expenseSweep, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(24.dp))
                                        
                                        Column {
                                            LegendItem("Fuel", sym, uiState.totalFuelCost, fuelColor)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LegendItem("Service", sym, uiState.totalServiceCost, serviceColor)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LegendItem("Expenses", sym, uiState.totalExpenseCost, expenseColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 3: Monthly Spend Chart
                    item {
                        if (uiState.allMonthlyData.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Monthly Spend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (uiState.hasMoreOlderMonths) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                                    .clickable(enabled = uiState.hasMoreOlderMonths) { 
                                                        viewModel.updateMonthWindow(uiState.selectedMonthWindowIndex + 1) 
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.ChevronLeft, 
                                                    contentDescription = "Older",
                                                    tint = if (uiState.hasMoreOlderMonths) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(8.dp))
                                            
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (uiState.hasMoreNewerMonths) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                                    .clickable(enabled = uiState.hasMoreNewerMonths) { 
                                                        viewModel.updateMonthWindow(uiState.selectedMonthWindowIndex - 1) 
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.ChevronRight, 
                                                    contentDescription = "Newer",
                                                    tint = if (uiState.hasMoreNewerMonths) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    val maxVal = remember(uiState.monthlyChartData) { uiState.monthlyChartData.maxOfOrNull { it.totalCost }?.toFloat() ?: 1f }
                                    val barColor = MaterialTheme.colorScheme.primary
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(140.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        uiState.monthlyChartData.forEach { point ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                val heightPercentage = if (maxVal > 0) (point.totalCost.toFloat() / maxVal) else 0f
                                                Box(
                                                    modifier = Modifier
                                                        .width(32.dp)
                                                        .height(100.dp * heightPercentage)
                                                        .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(point.displayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4: Key Performance Grid
                    item {
                        Column {
                            Text("Key Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Avg Efficiency", value = "%.1f".format(uiState.averageEfficiency), subtitle = "$dist/${if(fuelUnit == "Liters") "L" else "gal"}")
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Total Tracked", value = "%.0f".format(uiState.totalDistanceTracked), subtitle = dist)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Daily Burn Rate", value = "$sym ${"%.2f".format(uiState.costPerDay)}", subtitle = "Per Day")
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Max Fuel Price", value = "$sym ${"%.2f".format(uiState.maxFuelPrice)}", subtitle = "Per $fuelUnit")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Longest Gap", value = "%.0f".format(uiState.longestDistanceBetweenFills), subtitle = dist)
                                MiniStatCard(modifier = Modifier.weight(1f), title = "Shortest Gap", value = "%.0f".format(uiState.shortestDistanceBetweenFills), subtitle = dist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, sym: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$sym${"%.2f".format(value)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MiniStatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}