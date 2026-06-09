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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.ChartPoint
import com.auto.odo.presentation.viewmodel.DashboardUiState
import com.auto.odo.presentation.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateToAddFillUp: () -> Unit,
    onNavigateToAddService: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTrip: () -> Unit,
    onNavigateToUpdateOdo: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isVehicleMenuExpanded by remember { mutableStateOf(false) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    // Enhanced scroll behavior for 140Hz smoothness
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.then(
            if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
        ),
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
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
                            HorizontalDivider()
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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionFab(onClick = onNavigateToAddFillUp, label = "Fill-Up", icon = Icons.Default.Add, color = MaterialTheme.colorScheme.primaryContainer)
                        QuickActionFab(onClick = onNavigateToAddService, label = "Service", icon = Icons.Default.Build, color = MaterialTheme.colorScheme.secondaryContainer)
                        QuickActionFab(onClick = onNavigateToAddExpense, label = "Expense", icon = Icons.Default.ShoppingCart, color = MaterialTheme.colorScheme.secondaryContainer)
                        QuickActionFab(onClick = onNavigateToAddTrip, label = "Trip", icon = Icons.Default.DirectionsCar, color = MaterialTheme.colorScheme.secondaryContainer)
                        QuickActionFab(onClick = onNavigateToUpdateOdo, label = "Odometer", icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondaryContainer)
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.vehicles.isEmpty()) {
                EmptyVehiclesState(paddingValues) { showAddVehicleDialog = true }
            } else {
                DashboardContent(
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onNavigateToLogs = onNavigateToLogs
                )
            }
        }
    }

    if (showAddVehicleDialog) {
        AddVehicleDialog(
            onDismiss = { showAddVehicleDialog = false },
            onConfirm = { name, type, fuelUnit, distanceUnit, currency ->
                viewModel.addVehicle(name, type, fuelUnit, distanceUnit, currency)
                showAddVehicleDialog = false
            }
        )
    }
}

@Composable
private fun EmptyVehiclesState(paddingValues: PaddingValues, onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Vehicle")
        }
    }
}

@Composable
private fun QuickActionFab(onClick: () -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = color,
        contentColor = contentColorFor(color)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    paddingValues: PaddingValues,
    onNavigateToLogs: () -> Unit
) {
    val vehicle = uiState.selectedVehicle ?: return
    val metrics = uiState.metrics

    // Remember expensive formatting strings
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val chartDateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val efficiencyFormat = remember { "%.1f" }
    val costFormat = remember { "%.2f" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = paddingValues.calculateTopPadding() + 16.dp,
            bottom = paddingValues.calculateBottomPadding() + 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Fuel Cost (30d)",
                    value = "${vehicle.currency} ${costFormat.format(metrics?.fuelCostLast30Days ?: 0.0)}",
                    icon = Icons.Default.ShoppingCart,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Avg Efficiency",
                    value = if ((metrics?.averageEfficiency ?: 0.0) > 0) {
                        "${efficiencyFormat.format(metrics?.averageEfficiency)} ${vehicle.distanceUnit}/${if(vehicle.fuelUnit == "Liters") "L" else "gal"}"
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

        item(key = "chart") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = true; shape = RoundedCornerShape(16.dp) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                        dateFormatter = chartDateFormatter,
                        efficiencyFormat = efficiencyFormat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        item(key = "recent_header") {
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
            item(key = "empty_logs") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No logs logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(
                items = uiState.recentLogs,
                key = { "${it.javaClass.simpleName}_${it.id}" }
            ) { log ->
                RecentLogItemRow(
                    log = log, 
                    currency = vehicle.currency, 
                    distanceUnit = vehicle.distanceUnit,
                    dateFormatter = dateFormatter,
                    costFormat = costFormat
                )
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BezierChart(
    points: List<ChartPoint>,
    distanceUnit: String,
    fuelUnit: String,
    dateFormatter: SimpleDateFormat,
    efficiencyFormat: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Need at least 2 fill-ups to plot trend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val chartData = remember(points) {
        val minX = points.minOf { it.date }.toFloat()
        val maxX = points.maxOf { it.date }.toFloat()
        val minY = points.minOf { it.value }.toFloat()
        val maxY = points.maxOf { it.value }.toFloat()
        val rangeX = if (maxX - minX == 0f) 1f else (maxX - minX)
        val rangeY = if (maxY - minY == 0f) 1f else (maxY - minY)
        val paddedMinY = minY - (rangeY * 0.15f)
        val paddedMaxY = maxY + (rangeY * 0.15f)
        val paddedRangeY = paddedMaxY - paddedMinY
        Triple(minX, rangeX, Pair(paddedMinY, paddedRangeY))
    }

    val paddedMinY = chartData.third.first
    val paddedRangeY = chartData.third.second
    var selectedIndex by remember { mutableStateOf(-1) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures(onPress = { offset ->
                        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                        selectedIndex = ((offset.x + stepX / 2) / stepX).toInt().coerceIn(0, points.size - 1)
                    })
                }
                .drawWithCache {
                    // Pre-calculate coordinates to avoid allocations during actual draw
                    val coords = points.mapIndexed { index, point ->
                        val x = if (points.size > 1) (index.toFloat() / (points.size - 1)) * size.width else size.width / 2
                        val y = size.height - (((point.value.toFloat() - paddedMinY) / paddedRangeY) * size.height)
                        Offset(x, y)
                    }

                    val path = Path().apply {
                        if (coords.isNotEmpty()) {
                            moveTo(coords[0].x, coords[0].y)
                            for (i in 1 until coords.size) {
                                val p0 = coords[i - 1]; val p1 = coords[i]
                                val conX = p0.x + (p1.x - p0.x) / 2
                                cubicTo(conX, p0.y, conX, p1.y, p1.x, p1.y)
                            }
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(path)
                        if (coords.isNotEmpty()) {
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                    }

                    onDrawBehind {
                        drawPath(fillPath, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent)))
                        drawPath(path, primaryColor, style = Stroke(width = 3.dp.toPx()))
                        coords.forEachIndexed { idx, offset ->
                            val isSel = idx == selectedIndex
                            drawCircle(if (isSel) primaryColor else primaryColor.copy(alpha = 0.7f), if (isSel) 6.dp.toPx() else 4.dp.toPx(), offset)
                            if (isSel) {
                                drawLine(primaryColor.copy(alpha = 0.5f), Offset(offset.x, 0f), Offset(offset.x, size.height), 1.dp.toPx())
                            }
                        }
                    }
                }
        ) {}

        if (selectedIndex in points.indices) {
            val point = points[selectedIndex]
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(8.dp).align(Alignment.BottomCenter)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dateFormatter.format(Date(point.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${efficiencyFormat.format(point.value)} $distanceUnit/${if(fuelUnit == "Liters") "L" else "gal"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp).clickable { selectedIndex = -1 }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun RecentLogItemRow(log: LogItem, currency: String, distanceUnit: String, dateFormatter: SimpleDateFormat, costFormat: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint, title) = when (log) {
                is LogItem.Fuel -> Triple(Icons.Default.LocalGasStation, MaterialTheme.colorScheme.primary, "Fill-Up")
                is LogItem.Service -> Triple(Icons.Default.Build, MaterialTheme.colorScheme.secondary, "Service: ${log.serviceType}")
                is LogItem.Expense -> Triple(Icons.Default.ShoppingCart, MaterialTheme.colorScheme.tertiary, "Expense: ${log.category}")
                is LogItem.Trip -> Triple(Icons.Default.DirectionsCar, MaterialTheme.colorScheme.primary, "Trip: ${log.purpose}")
            }
            Box(modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(text = dateFormatter.format(Date(log.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (log is LogItem.Fuel) Text(text = "${log.odometer.toInt()} $distanceUnit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (log.totalCost > 0) Text(text = "$currency ${costFormat.format(log.totalCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleDialog(onDismiss: () -> Unit, onConfirm: (name: String, type: String, fuelUnit: String, distanceUnit: String, currency: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Car") }
    var fuelUnit by remember { mutableStateOf("Liters") }
    var distanceUnit by remember { mutableStateOf("km") }
    var currency by remember { mutableStateOf("INR") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add New Vehicle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Vehicle Name (e.g. Yamaha R15)") }, modifier = Modifier.fillMaxWidth())
                Text("Vehicle Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = type == "Car", onClick = { type = "Car" }); Text("Car") }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = type == "Bike", onClick = { type = "Bike" }); Text("Bike") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Distance Unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = distanceUnit == "km", onClick = { distanceUnit = "km" }); Text("km", style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = distanceUnit == "miles", onClick = { distanceUnit = "miles" }); Text("miles", style = MaterialTheme.typography.bodySmall) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fuel Unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = fuelUnit == "Liters", onClick = { fuelUnit = "Liters" }); Text("Liters", style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = fuelUnit == "Gallons", onClick = { fuelUnit = "Gallons" }); Text("Gallons", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency Symbol (e.g. INR, USD)") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (name.isNotBlank()) onConfirm(name, type, fuelUnit, distanceUnit, currency) }, enabled = name.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}
