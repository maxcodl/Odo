package com.auto.odo.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.core.UnitConverter
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.LogsFeedUiState
import com.auto.odo.presentation.viewmodel.LogsFeedViewModel
import com.auto.odo.presentation.viewmodel.currencySymbol
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// NEW: Data class to carry the calculated metrics into the details screen
data class LogDetailPayload(
    val log: LogItem,
    val delta: Double?,
    val efficiency: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedScreen(
    viewModel: LogsFeedViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false
    onNavigateToEdit: (LogItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LogsFeedContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onFilterSelected = viewModel::setFilter,
        onDeleteLog = viewModel::deleteLog,
        onUndoDelete = viewModel::undoDelete,
        onNavigateToEdit = onNavigateToEdit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedContent(
    uiState: LogsFeedUiState,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onFilterSelected: (String?) -> Unit = {},
    onDeleteLog: (LogItem) -> Unit = {},
    onUndoDelete: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    
    // NEW: State to manage the open/closed status of the detailed view
    var selectedDetailPayload by remember { mutableStateOf<LogDetailPayload?>(null) }

    LaunchedEffect(uiState.pendingDeleteLog) {
        val log = uiState.pendingDeleteLog ?: return@LaunchedEffect
        val label = when (log) {
            is LogItem.Fuel -> "Fuel log"
            is LogItem.Service -> "Service log"
            is LogItem.Expense -> "Expense"
            is LogItem.Trip -> "Trip"
        }
        val result = snackbarHostState.showSnackbar(
            message = "$label deleted",
            actionLabel = "UNDO",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
            contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background, 
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("Log Feed", fontWeight = FontWeight.Bold) },
                    scrollBehavior = if (autoHideTitleBar) scrollBehavior else null,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()) 
            ) {
                if (uiState.selectedVehicle == null && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select or create a vehicle to view logs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val filters = remember {
                        listOf(null to "All", "fuel" to "Fuel", "service" to "Service",
                            "expense" to "Expense", "trip" to "Trips")
                    }
                    
                    ScrollableTabRow(
                        selectedTabIndex = filters.indexOfFirst { it.first == uiState.activeFilter }.coerceAtLeast(0),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        filters.forEachIndexed { index, (filterType, name) ->
                            val isSelected = uiState.activeFilter == filterType
                            Tab(
                                selected = isSelected,
                                onClick = { onFilterSelected(filterType) },
                                text = { 
                                    Text(
                                        text = name, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No logs found matching this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val vehicle = uiState.selectedVehicle
                        val currency = vehicle?.currency ?: "INR"
                        val distUnit = vehicle?.distanceUnit ?: "km"
                        val fuelUnit = vehicle?.fuelUnit ?: "Liters"

                        val fuelLogs = remember(uiState.logs) {
                            uiState.logs.filterIsInstance<LogItem.Fuel>().sortedBy { it.odometer }
                        }
                        
                        val fuelMetrics = remember(fuelLogs) {
                            val metrics = mutableMapOf<Long, Pair<Double, Double?>>()
                            if (fuelLogs.size >= 2) {
                                var anchorOdo = fuelLogs[0].odometer
                                var accumulatedFuel = 0.0
                                val pendingSequence = mutableListOf<Pair<Long, Double>>() 

                                for (i in 1 until fuelLogs.size) {
                                    val current = fuelLogs[i]
                                    val previous = fuelLogs[i - 1]
                                    val legDelta = current.odometer - previous.odometer
                                    
                                    accumulatedFuel += current.quantity
                                    pendingSequence.add(Pair(current.id, legDelta))

                                    if (!current.isPartialTank) {
                                        val totalDistance = current.odometer - anchorOdo
                                        val efficiency = if (accumulatedFuel > 0) totalDistance / accumulatedFuel else null
                                        
                                        for (item in pendingSequence) {
                                            metrics[item.first] = Pair(item.second, efficiency)
                                        }
                                        
                                        anchorOdo = current.odometer
                                        accumulatedFuel = 0.0
                                        pendingSequence.clear()
                                    } else {
                                        metrics[current.id] = Pair(legDelta, null)
                                    }
                                }
                            }
                            metrics
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)
                        ) {
                            items(
                                uiState.logs,
                                key = { "${it.javaClass.simpleName}_${it.id}" }
                            ) { log ->
                                
                                val delta = if (log is LogItem.Fuel) fuelMetrics[log.id]?.first else null
                                val eff = if (log is LogItem.Fuel) fuelMetrics[log.id]?.second else null

                                LogItemCardWrapper(
                                    log = log,
                                    onDelete = onDeleteLog,
                                    onClick = { 
                                        selectedDetailPayload = LogDetailPayload(log, delta, eff)
                                    }
                                ) {
                                    LogItemCard(
                                        log = log,
                                        currency = currency,
                                        distUnit = distUnit,
                                        fuelUnit = fuelUnit,
                                        rawDelta = delta,
                                        rawEfficiency = eff
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // NEW: Animated visibility block for the details screen
        AnimatedVisibility(
            visible = selectedDetailPayload != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            selectedDetailPayload?.let { payload ->
                val vehicle = uiState.selectedVehicle
                LogDetailsFullScreen(
                    payload = payload,
                    currency = vehicle?.currency ?: "INR",
                    distUnit = vehicle?.distanceUnit ?: "km",
                    fuelUnit = vehicle?.fuelUnit ?: "Liters",
                    onBack = { selectedDetailPayload = null },
                    onDelete = { log ->
                        onDeleteLog(log)
                        selectedDetailPayload = null
                    },
                    onEdit = { log -> 
                        selectedDetailPayload = null
                        onNavigateToEdit(log)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailsFullScreen(
    payload: LogDetailPayload,
    currency: String,
    distUnit: String,
    fuelUnit: String,
    onBack: () -> Unit,
    onDelete: (LogItem) -> Unit,
    onEdit: (LogItem) -> Unit
) {
    BackHandler(onBack = onBack)

    val log = payload.log
    val title = when (log) {
        is LogItem.Fuel -> "Fill-Up"
        is LogItem.Service -> "Service"
        is LogItem.Expense -> "Expense"
        is LogItem.Trip -> "Trip"
    }
    
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(log.date) { dateFormatter.format(Date(log.date)) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, 
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Log Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this $title? This action will remove the record.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(log)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { onEdit(log) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                // THEME FIX: Changed from heavy primary block to seamless background matching
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DetailRow(label = "Date", valueText = formattedDate)

            when (log) {
                is LogItem.Fuel -> {
                    val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer) else log.odometer
                    val displayFuel = if (fuelUnit == "Gallons") UnitConverter.litersToGallons(log.quantity) else log.quantity
                    val fuelLabel = if (fuelUnit == "Gallons") "gal" else "Ltr"
                    
                    val displayDelta = if (payload.delta != null) {
                        val d = if (distUnit == "miles") UnitConverter.kmToMiles(payload.delta) else payload.delta
                        String.format(Locale.US, "%.1f %s", d, distUnit)
                    } else "N/A"
                    
                    val displayEff = if (payload.efficiency != null) {
                        val e = if (distUnit == "miles" && fuelUnit == "Gallons") UnitConverter.kmToMiles(payload.efficiency) / UnitConverter.litersToGallons(1.0)
                                else if (distUnit == "miles") UnitConverter.kmToMiles(payload.efficiency)
                                else if (fuelUnit == "Gallons") payload.efficiency / UnitConverter.litersToGallons(1.0)
                                else payload.efficiency
                        val effLabel = if (fuelUnit == "Gallons") "mpg" else "$distUnit/L"
                        String.format(Locale.US, "%.2f %s", e, effLabel)
                    } else "N/A"

                    DetailRow(label = "Odometer", valueText = "${String.format(Locale.US, "%.1f", displayOdo)} $distUnit")
                    DetailRow(label = "Distance", valueText = displayDelta)
                    DetailRow(label = "Efficiency", valueText = displayEff)
                    DetailRow(label = "Quantity", valueText = "${String.format(Locale.US, "%.2f", displayFuel)} $fuelLabel")
                    DetailRow(label = "Partial Tank", valueContent = {
                        Checkbox(checked = log.isPartialTank, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                    })
                    DetailRow(label = "Price/$fuelLabel", valueText = "${String.format(Locale.US, "%.3f", log.pricePerUnit)} $currency")
                    DetailRow(label = "Total Cost", valueText = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency")
                    if (!log.stationName.isNullOrEmpty()) DetailRow(label = "Station", valueText = log.stationName)
                    if (!log.notes.isNullOrEmpty()) DetailRow(label = "Notes", valueText = log.notes)
                }
                is LogItem.Service -> {
                    val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer) else log.odometer
                    DetailRow(label = "Odometer", valueText = "${String.format(Locale.US, "%.1f", displayOdo)} $distUnit")
                    DetailRow(label = "Service Type", valueText = log.serviceType)
                    DetailRow(label = "Total Cost", valueText = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency")
                    if (!log.notes.isNullOrEmpty()) DetailRow(label = "Notes", valueText = log.notes)
                }
                is LogItem.Expense -> {
                    DetailRow(label = "Category", valueText = log.category)
                    DetailRow(label = "Total Cost", valueText = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency")
                    if (!log.notes.isNullOrEmpty()) DetailRow(label = "Notes", valueText = log.notes)
                }
                is LogItem.Trip -> {
                    val displayStart = if (distUnit == "miles") UnitConverter.kmToMiles(log.startOdo) else log.startOdo
                    val displayEnd = if (distUnit == "miles") UnitConverter.kmToMiles(log.endOdo) else log.endOdo
                    val distance = displayEnd - displayStart
                    DetailRow(label = "Purpose", valueText = log.purpose)
                    DetailRow(label = "Distance", valueText = "${String.format(Locale.US, "%.1f", distance)} $distUnit")
                    DetailRow(label = "Start Odo", valueText = "${String.format(Locale.US, "%.1f", displayStart)} $distUnit")
                    DetailRow(label = "End Odo", valueText = "${String.format(Locale.US, "%.1f", displayEnd)} $distUnit")
                    DetailRow(label = "Total Cost", valueText = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency")
                }
            }
        }
    }
}

// NEW: Helper component for the rows in the details screen
@Composable
fun DetailRow(label: String, valueText: String? = null, valueContent: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (valueText != null) {
            Text(text = valueText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        } else if (valueContent != null) {
            valueContent()
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItemCardWrapper(
    log: LogItem,
    onDelete: (LogItem) -> Unit,
    onClick: () -> Unit, // NEW: Click listener added here
    content: @Composable () -> Unit 
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        val label = when (log) {
            is LogItem.Fuel -> "fuel fill-up log"
            is LogItem.Service -> "service log"
            is LogItem.Expense -> "expense record"
            is LogItem.Trip -> "trip log"
        }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, 
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Log Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this $label? This action will remove the record.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onDelete(log)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Delete", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick, // NEW: Trigger details screen
                onLongClick = { showConfirmDialog = true }
            )
    ) {
        content()
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun LogItemCard(
    log: LogItem, 
    currency: String, 
    distUnit: String = "km", 
    fuelUnit: String = "Liters",
    rawDelta: Double? = null,
    rawEfficiency: Double? = null
) {
    val dateFormatter = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(log.date) { dateFormatter.format(Date(log.date)) }
    val fuelUnitLabel = remember(fuelUnit) { if (fuelUnit == "Gallons") "gal" else "Ltr" }

    val displayDelta = remember(rawDelta, distUnit) {
        if (rawDelta != null) {
            if (distUnit == "miles") UnitConverter.kmToMiles(rawDelta) else rawDelta
        } else null
    }

    val displayEff = remember(rawEfficiency, distUnit, fuelUnit) {
        if (rawEfficiency != null) {
            if (distUnit == "miles" && fuelUnit == "Gallons") {
                UnitConverter.kmToMiles(rawEfficiency) / UnitConverter.litersToGallons(1.0)
            } else if (distUnit == "miles") {
                UnitConverter.kmToMiles(rawEfficiency)
            } else if (fuelUnit == "Gallons") {
                rawEfficiency / UnitConverter.litersToGallons(1.0)
            } else {
                rawEfficiency
            }
        } else null
    }

    val (icon, tint) = remember(log) {
        when (log) {
            is LogItem.Fuel -> Pair(Icons.Default.LocalGasStation, "primary")
            is LogItem.Service -> Pair(Icons.Default.Build, "secondary")
            is LogItem.Expense -> Pair(Icons.Default.ShoppingCart, "tertiary")
            is LogItem.Trip -> Pair(Icons.Default.DirectionsCar, "primary")
        }
    }
    
    val resolvedTint = when (tint) {
        "secondary" -> MaterialTheme.colorScheme.secondary
        "tertiary" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(resolvedTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        when (log) {
            is LogItem.Fuel -> {
                val displayFuel = if (fuelUnit == "Gallons") UnitConverter.litersToGallons(log.quantity) else log.quantity
                val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer) else log.odometer
                val partialStr = if (log.isPartialTank) " (P)" else ""

                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Odo: ${String.format(Locale.US, "%.0f", displayOdo)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${String.format(Locale.US, "%.2f", displayFuel)} $fuelUnitLabel$partialStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (displayDelta != null) {
                            Text(text = "(+${String.format(Locale.US, "%.0f", displayDelta)}) $distUnit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(text = "-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                        Text(text = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (displayEff != null) {
                            val effLabel = if (fuelUnit == "Gallons") "mpg" else "$distUnit/$fuelUnitLabel"
                            Text(text = "${String.format(Locale.US, "%.2f", displayEff)} $effLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(text = "-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            is LogItem.Service -> {
                val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer) else log.odometer
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Odo: ${String.format(Locale.US, "%.0f", displayOdo)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = log.serviceType, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Service", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        if (log.totalCost > 0) {
                            Text(text = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            is LogItem.Expense -> {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = log.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = log.notes ?: "Expense", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        if (log.totalCost > 0) {
                            Text(text = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            is LogItem.Trip -> {
                val distance = if (distUnit == "miles") UnitConverter.kmToMiles(log.endOdo - log.startOdo) else (log.endOdo - log.startOdo)
                val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.endOdo) else log.endOdo
                
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Odo: ${String.format(Locale.US, "%.0f", displayOdo)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = log.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${String.format(Locale.US, "%.1f", distance)} $distUnit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        if (log.totalCost > 0) {
                            Text(text = "${String.format(Locale.US, "%.2f", log.totalCost)} $currency", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}