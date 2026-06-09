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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedScreen(
    viewModel: LogsFeedViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // Show undo snackbar whenever pendingDeleteLog appears
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
            duration = SnackbarDuration.Short  // ~4 s
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
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
                    containerColor = MaterialTheme.colorScheme.background
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
            if (uiState.selectedVehicle == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select or create a vehicle to view logs.")
                }
                return@Scaffold
            }

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(null to "All", "fuel" to "Fuel", "service" to "Service",
                    "expense" to "Expense", "trip" to "Trips").forEach { (filterType, name) ->
                    val isSelected = uiState.activeFilter == filterType
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(filterType) },
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
                val currency = uiState.selectedVehicle?.currency ?: "INR"
                val distUnit = uiState.selectedVehicle?.distanceUnit ?: "km"
                val fuelUnit = uiState.selectedVehicle?.fuelUnit ?: "Liters"
                val pendingId = uiState.pendingDeleteLog?.id
                val pendingType = uiState.pendingDeleteLog?.javaClass?.simpleName

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        uiState.logs,
                        key = { "${it.javaClass.simpleName}_${it.id}" }
                    ) { log ->
                        // Hide (animate out) the item that's pending deletion
                        val isPendingDelete = log.id == pendingId &&
                            log.javaClass.simpleName == pendingType

                        AnimatedVisibility(
                            visible = !isPendingDelete,
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SwipeToDeleteContainer(
                                item = log,
                                onDelete = { viewModel.deleteLog(log) }
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
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    content: @Composable (T) -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(currentItem)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
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
    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(log.date))
    val sym = currencySymbol(currency)
    
    val fuelLabel = if (fuelUnit == "Gallons") "gal" else "L"

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
            val (triple, subtitle) = when (log) {
                is LogItem.Fuel -> {
                    val displayQty = if (fuelUnit == "Gallons") UnitConverter.litersToGallons(log.quantity) else log.quantity
                    // Price per unit needs to be adjusted. Cost = Qty * Price.
                    // If we display in gallons, PricePerGal = Cost / displayQty.
                    val displayPrice = if (displayQty > 0) log.totalCost / displayQty else 0.0
                    Triple(
                        Icons.Default.LocalGasStation,
                        MaterialTheme.colorScheme.primary,
                        "Fuel Fill-Up"
                    ) to "${String.format(Locale.US, "%.2f", displayQty)} $fuelLabel · ${sym}${String.format(Locale.US, "%.2f", displayPrice)}/$fuelLabel"
                }

                is LogItem.Service -> Triple(
                    Icons.Default.Build,
                    MaterialTheme.colorScheme.secondary,
                    log.serviceType
                ) to (log.notes ?: "Routine maintenance")

                is LogItem.Expense -> Triple(
                    Icons.Default.ShoppingCart,
                    MaterialTheme.colorScheme.tertiary,
                    "Expense: ${log.category}"
                ) to (log.notes ?: "Category expense")

                is LogItem.Trip -> {
                    val rawDistance = log.endOdo - log.startOdo
                    val displayDistance = if (distUnit == "miles") UnitConverter.kmToMiles(rawDistance) else rawDistance
                    Triple(
                        Icons.Default.DirectionsCar,
                        MaterialTheme.colorScheme.primary,
                        "Trip (${log.purpose})"
                    ) to "Distance: ${String.format(Locale.US, "%.1f", displayDistance)} $distUnit"
                }
            }
            val (icon, tint, title) = triple

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            Column(horizontalAlignment = Alignment.End) {
                if (log is LogItem.Fuel) {
                    val displayOdo = if (distUnit == "miles") UnitConverter.kmToMiles(log.odometer).toInt() else log.odometer.toInt()
                    Text(
                        text = "Odo: $displayOdo $distUnit",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (log.totalCost > 0) {
                    Text(
                        text = "$sym ${String.format(Locale.US, "%.2f", log.totalCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
