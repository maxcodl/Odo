package com.auto.odo.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.core.UnitConverter
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.LogsFeedViewModel
import com.auto.odo.presentation.viewmodel.currencySymbol
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.viewmodel.LogsFeedUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedScreen(
    viewModel: LogsFeedViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LogsFeedContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onFilterSelected = viewModel::setFilter,
        onDeleteLog = viewModel::deleteLog,
        onUndoDelete = viewModel::undoDelete
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

    Scaffold(
        modifier = if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.selectedVehicle == null && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select or create a vehicle to view logs.")
                }
            } else {
                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = remember {
                        listOf(null to "All", "fuel" to "Fuel", "service" to "Service",
                            "expense" to "Expense", "trip" to "Trips")
                    }
                    filters.forEach { (filterType, name) ->
                        val isSelected = uiState.activeFilter == filterType
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterSelected(filterType) },
                            label = { Text(name) }
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
                        Text("No logs found matching this filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val vehicle = uiState.selectedVehicle
                    val currency = vehicle?.currency ?: "INR"
                    val distUnit = vehicle?.distanceUnit ?: "km"
                    val fuelUnit = vehicle?.fuelUnit ?: "Liters"

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 110.dp // Extra space for FAB and Nav
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            uiState.logs,
                            key = { "${it.javaClass.simpleName}_${it.id}" }
                        ) { log ->
                            // animateItem directly on the content root — no extra Box wrapper
                            SwipeToDeleteContainer(
                                modifier = Modifier.animateItem(),
                                item = log,
                                onDelete = onDeleteLog
                            ) {
                                LogItemCard(
                                    log = log,
                                    currency = currency,
                                    distUnit = distUnit,
                                    fuelUnit = fuelUnit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    modifier: Modifier = Modifier,
    item: LogItem,
    onDelete: (LogItem) -> Unit,
    content: @Composable (LogItem) -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    val coroutineScope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showConfirmDialog = true
                true
            } else {
                false
            }
        }
    )

    if (showConfirmDialog) {
        val label = when (currentItem) {
            is LogItem.Fuel -> "fuel fill-up log"
            is LogItem.Service -> "service log"
            is LogItem.Expense -> "expense record"
            is LogItem.Trip -> "trip log"
        }
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                coroutineScope.launch { dismissState.reset() }
            },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Log Entry?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete this $label? This action will remove the record.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onDelete(currentItem)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        coroutineScope.launch { dismissState.reset() }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = { content(item) }
    )
}

@Composable
fun LogItemCard(log: LogItem, currency: String, distUnit: String = "km", fuelUnit: String = "Liters") {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(log.date) { dateFormatter.format(Date(log.date)) }
    val sym = remember(currency) { currencySymbol(currency) }
    val fuelUnitLabel = remember(fuelUnit) { if (fuelUnit == "Gallons") "gal" else "L" }

    // Compute all display strings and icon choices once, only when log data changes.
    // This prevents String.format and branching from running on every recomposition.
    data class CardData(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val tint: Color,
        val title: String,
        val subtitle: String,
        val odometerText: String?,
        val costText: String?
    )

    val cardData = remember(log, sym, fuelUnitLabel, distUnit, fuelUnit) {
        when (log) {
            is LogItem.Fuel -> {
                val displayFuel = if (fuelUnit == "Gallons") UnitConverter.litersToGallons(log.quantity) else log.quantity
                val displayPrice = if (fuelUnit == "Gallons") log.pricePerUnit * UnitConverter.gallonsToLiters(1.0) else log.pricePerUnit
                val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer) else log.odometer
                CardData(
                    icon = Icons.Default.LocalGasStation,
                    tint = Color.Unspecified, // resolved in composition
                    title = "Fuel Fill-Up",
                    subtitle = "${String.format(Locale.US, "%.2f", displayFuel)} $fuelUnitLabel · " +
                        "${sym}${String.format(Locale.US, "%.2f", displayPrice)}/$fuelUnitLabel",
                    odometerText = "Odo: ${String.format(Locale.US, "%.0f", displayOdo)} $distUnit",
                    costText = if (log.totalCost > 0) "$sym ${String.format(Locale.US, "%.2f", log.totalCost)}" else null
                )
            }
            is LogItem.Service -> CardData(
                icon = Icons.Default.Build,
                tint = Color.Unspecified,
                title = log.serviceType,
                subtitle = log.notes ?: "Routine maintenance",
                odometerText = null,
                costText = if (log.totalCost > 0) "$sym ${String.format(Locale.US, "%.2f", log.totalCost)}" else null
            )
            is LogItem.Expense -> CardData(
                icon = Icons.Default.ShoppingCart,
                tint = Color.Unspecified,
                title = "Expense: ${log.category}",
                subtitle = log.notes ?: "Category expense",
                odometerText = null,
                costText = if (log.totalCost > 0) "$sym ${String.format(Locale.US, "%.2f", log.totalCost)}" else null
            )
            is LogItem.Trip -> {
                val displayDist = if (distUnit == "miles") UnitConverter.kmToMiles(log.endOdo - log.startOdo) else (log.endOdo - log.startOdo)
                CardData(
                    icon = Icons.Default.DirectionsCar,
                    tint = Color.Unspecified,
                    title = "Trip (${log.purpose})",
                    subtitle = "Distance: ${String.format(Locale.US, "%.1f", displayDist)} $distUnit",
                    odometerText = null,
                    costText = if (log.totalCost > 0) "$sym ${String.format(Locale.US, "%.2f", log.totalCost)}" else null
                )
            }
        }
    }

    // Resolve theme colors at composition time (cannot be in remember with log)
    val (icon, tint, title, subtitle) = remember(log) {
        when (log) {
            is LogItem.Fuel -> arrayOf(Icons.Default.LocalGasStation, "primary", cardData.title, cardData.subtitle)
            is LogItem.Service -> arrayOf(Icons.Default.Build, "secondary", cardData.title, cardData.subtitle)
            is LogItem.Expense -> arrayOf(Icons.Default.ShoppingCart, "tertiary", cardData.title, cardData.subtitle)
            is LogItem.Trip -> arrayOf(Icons.Default.DirectionsCar, "primary", cardData.title, cardData.subtitle)
        }
    }
    val resolvedTint = when (tint as String) {
        "secondary" -> MaterialTheme.colorScheme.secondary
        "tertiary" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(resolvedTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                    contentDescription = null,
                    tint = resolvedTint
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title as String, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle as String, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            Column(horizontalAlignment = Alignment.End) {
                cardData.odometerText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                cardData.costText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LogsFeedPreview() {
    val mockVehicle = VehicleEntity(
        id = 1, name = "Harley Davidson", type = "Bike", 
        fuelUnit = "Liters", distanceUnit = "km", currency = "USD"
    )
    val mockUiState = LogsFeedUiState(
        selectedVehicle = mockVehicle,
        logs = listOf(
            LogItem.Fuel(1, 1, System.currentTimeMillis(), 500.0, 10.0, 1.2, 12.0, false, "Shell", null, null),
            LogItem.Service(2, 1, System.currentTimeMillis() - 86400000, 450.0, "Brake Check", 50.0, null),
            LogItem.Trip(3, 1, System.currentTimeMillis() - 172800000, 400.0, 450.0, "Work", null)
        ),
        isLoading = false
    )
    OdoTheme {
        LogsFeedContent(uiState = mockUiState)
    }
}
