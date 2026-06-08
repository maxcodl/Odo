package com.auto.odo.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auto.odo.core.UnitConverter
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.ChartPoint
import com.auto.odo.presentation.viewmodel.DashboardUiState
import com.auto.odo.presentation.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAddFillUp: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isVehicleMenuExpanded by remember { mutableStateOf(false) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    // Dialog flags for minor actions
    var showAddServiceDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddTripDialog by remember { mutableStateOf(false) }
    var showUpdateOdoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { isVehicleMenuExpanded = true }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.selectedVehicle?.name ?: "Select Vehicle",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select vehicle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = isVehicleMenuExpanded,
                            onDismissRequest = { isVehicleMenuExpanded = false }
                        ) {
                            uiState.vehicles.forEach { vehicle ->
                                DropdownMenuItem(
                                    text = { Text(vehicle.name) },
                                    onClick = {
                                        viewModel.selectVehicle(vehicle.id)
                                        isVehicleMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Add Vehicle") },
                                onClick = {
                                    showAddVehicleDialog = true
                                    isVehicleMenuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expanding Fab Items
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                onNavigateToAddFillUp()
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Add Fill-Up")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fill-Up", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                showAddServiceDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, contentDescription = "Add Service")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Service", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                showAddExpenseDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Add Expense")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Expense", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                showAddTripDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = "Add Trip")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Trip", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                showUpdateOdoDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = "Update Odometer")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Odometer", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Options Menu"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.vehicles.isEmpty()) {
                // Empty state when no vehicles are registered
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Vehicles Registered",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To start logging your fuel entries, expenses, trips and services, add your first vehicle.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showAddVehicleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Vehicle")
                    }
                }
            } else {
                DashboardContent(
                    uiState = uiState,
                    onNavigateToLogs = onNavigateToLogs
                )
            }
        }
    }

    // Add Vehicle Dialog
    if (showAddVehicleDialog) {
        AddVehicleDialog(
            onDismiss = { showAddVehicleDialog = false },
            onConfirm = { name, type, fuelUnit, distanceUnit, currency ->
                viewModel.addVehicle(name, type, fuelUnit, distanceUnit, currency)
                showAddVehicleDialog = false
            }
        )
    }

    // Minor dialog placeholders to prevent crash
    if (showAddServiceDialog) {
        PlaceholderDialog(title = "Add Service", onDismiss = { showAddServiceDialog = false })
    }
    if (showAddExpenseDialog) {
        PlaceholderDialog(title = "Add Expense", onDismiss = { showAddExpenseDialog = false })
    }
    if (showAddTripDialog) {
        PlaceholderDialog(title = "Add Trip", onDismiss = { showAddTripDialog = false })
    }
    if (showUpdateOdoDialog) {
        PlaceholderDialog(title = "Update Odometer", onDismiss = { showUpdateOdoDialog = false })
    }
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onNavigateToLogs: () -> Unit
) {
    val vehicle = uiState.selectedVehicle ?: return
    val metrics = uiState.metrics

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Metric summary cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Fuel Cost (30d)",
                    value = "${vehicle.currency} ${String.format("%.2f", metrics?.fuelCostLast30Days ?: 0.0)}",
                    icon = Icons.Default.ShoppingCart,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Avg Efficiency",
                    value = if ((metrics?.averageEfficiency ?: 0.0) > 0) {
                        "${String.format("%.1f", metrics?.averageEfficiency)} ${vehicle.distanceUnit}/${if(vehicle.fuelUnit == "Liters") "L" else "gal"}"
                    } else "N/A",
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1.2f)
                )
                MetricCard(
                    title = "Fill-ups (30d)",
                    value = "${metrics?.fillUpCountLast30Days ?: 0}",
                    icon = Icons.Default.DateRange,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }

        // 2. Chart Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Fuel Efficiency Trend",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    BezierChart(
                        points = uiState.chartPoints,
                        distanceUnit = vehicle.distanceUnit,
                        fuelUnit = vehicle.fuelUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // 3. Recent logs feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activity",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "View All",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateToLogs() }
                        .padding(8.dp)
                )
            }
        }

        if (uiState.recentLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No logs logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.recentLogs) { log ->
                RecentLogItemRow(log = log, currency = vehicle.currency, distanceUnit = vehicle.distanceUnit)
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BezierChart(
    points: List<ChartPoint>,
    distanceUnit: String,
    fuelUnit: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Need at least 2 fill-ups to plot trend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Remember calculations to prevent unnecessary recompositions
    val chartData = remember(points) {
        val minX = points.minOf { it.date }.toFloat()
        val maxX = points.maxOf { it.date }.toFloat()
        val minY = points.minOf { it.value }.toFloat()
        val maxY = points.maxOf { it.value }.toFloat()
        val rangeX = if (maxX - minX == 0f) 1f else (maxX - minX)
        val rangeY = if (maxY - minY == 0f) 1f else (maxY - minY)
        
        // Add 15% padding to top/bottom of Y axis
        val paddedMinY = minY - (rangeY * 0.15f)
        val paddedMaxY = maxY + (rangeY * 0.15f)
        val paddedRangeY = paddedMaxY - paddedMinY

        Triple(minX, rangeX, Pair(paddedMinY, paddedRangeY))
    }

    val minX = chartData.first
    val rangeX = chartData.second
    val paddedMinY = chartData.third.first
    val paddedRangeY = chartData.third.second

    var selectedIndex by remember { mutableStateOf(-1) }
    var touchX by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            val width = size.width.toFloat()
                            val stepX = width / (points.size - 1).coerceAtLeast(1)
                            val clickedIndex = ((offset.x + stepX / 2) / stepX).toInt().coerceIn(0, points.size - 1)
                            selectedIndex = clickedIndex
                            touchX = offset.x
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Calculate control coordinates
            val coords = points.mapIndexed { index, point ->
                val x = if (points.size > 1) {
                    (index.toFloat() / (points.size - 1)) * width
                } else {
                    width / 2
                }
                val y = height - (((point.value.toFloat() - paddedMinY) / paddedRangeY) * height)
                Offset(x, y)
            }

            // Draw line connecting coordinates
            val path = Path()
            val fillPath = Path()

            if (coords.isNotEmpty()) {
                path.moveTo(coords[0].x, coords[0].y)
                fillPath.moveTo(coords[0].x, coords[0].y)

                for (i in 1 until coords.size) {
                    val p0 = coords[i - 1]
                    val p1 = coords[i]
                    // Control points for smooth Bezier curve
                    val conX1 = p0.x + (p1.x - p0.x) / 2
                    val conY1 = p0.y
                    val conX2 = p0.x + (p1.x - p0.x) / 2
                    val conY2 = p1.y

                    path.cubicTo(conX1, conY1, conX2, conY2, p1.x, p1.y)
                    fillPath.cubicTo(conX1, conY1, conX2, conY2, p1.x, p1.y)
                }

                // Close fill path to bottom of canvas for gradient fill
                fillPath.lineTo(width, height)
                fillPath.lineTo(0f, height)
                fillPath.close()

                // Draw gradient background below the line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent)
                    )
                )

                // Draw curve outline
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw point dots
                coords.forEachIndexed { idx, offset ->
                    val isSelected = idx == selectedIndex
                    drawCircle(
                        color = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.7f),
                        radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                        center = offset
                    )
                }

                // Draw vertical tooltip indicator if selected
                if (selectedIndex in coords.indices) {
                    val targetOffset = coords[selectedIndex]
                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = Offset(targetOffset.x, 0f),
                        end = Offset(targetOffset.x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        // Overlay tooltip text Reactively inside the Box layout
        if (selectedIndex in points.indices) {
            val point = points[selectedIndex]
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val formattedDate = sdf.format(Date(point.date))
            val valueText = "${String.format("%.1f", point.value)} $distanceUnit/${if(fuelUnit == "Liters") "L" else "gal"}"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(valueText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss tooltip",
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { selectedIndex = -1 },
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun RecentLogItemRow(log: LogItem, currency: String, distanceUnit: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint, title) = when (log) {
                is LogItem.Fuel -> Triple(Icons.Default.LocalGasStation, MaterialTheme.colorScheme.primary, "Fill-Up")
                is LogItem.Service -> Triple(Icons.Default.Build, MaterialTheme.colorScheme.secondary, "Service: ${log.serviceType}")
                is LogItem.Expense -> Triple(Icons.Default.ShoppingCart, MaterialTheme.colorScheme.tertiary, "Expense: ${log.category}")
                is LogItem.Trip -> Triple(Icons.Default.DirectionsCar, MaterialTheme.colorScheme.primary, "Trip: ${log.purpose}")
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(log.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (log is LogItem.Fuel) {
                    Text(
                        text = "${log.odometer.toInt()} $distanceUnit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (log.totalCost > 0) {
                    Text(
                        text = "$currency ${String.format("%.2f", log.totalCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, fuelUnit: String, distanceUnit: String, currency: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Car") }
    var fuelUnit by remember { mutableStateOf("Liters") }
    var distanceUnit by remember { mutableStateOf("km") }
    var currency by remember { mutableStateOf("INR") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add New Vehicle",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vehicle Name (e.g. Yamaha R15)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Vehicle Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "Car", onClick = { type = "Car" })
                        Text("Car")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "Bike", onClick = { type = "Bike" })
                        Text("Bike")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Distance Unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = distanceUnit == "km", onClick = { distanceUnit = "km" })
                            Text("km", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = distanceUnit == "miles", onClick = { distanceUnit = "miles" })
                            Text("miles", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fuel Unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = fuelUnit == "Liters", onClick = { fuelUnit = "Liters" })
                            Text("Liters", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = fuelUnit == "Gallons", onClick = { fuelUnit = "Gallons" })
                            Text("Gallons", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    label = { Text("Currency Symbol (e.g. INR, USD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name, type, fuelUnit, distanceUnit, currency) },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("This is a mockup action interface. The prompt focused specifically on the dynamic 'Add Fill-Up' flow validation engine, interactive Bezier charts, and bottom navigation setup.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
