package com.auto.odo.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.LogsFeedViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedScreen(
    viewModel: LogsFeedViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Feed", fontWeight = FontWeight.Bold) },
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

            // Top Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    Pair(null, "All"),
                    Pair("fuel", "Fuel"),
                    Pair("service", "Service"),
                    Pair("expense", "Expense"),
                    Pair("trip", "Trips")
                )

                filters.forEach { (filterType, name) ->
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No logs found matching this filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.logs, key = { "${it.javaClass.simpleName}_${it.id}" }) { log ->
                        SwipeToDeleteContainer(
                            item = log,
                            onDelete = { viewModel.deleteLog(log) }
                        ) {
                            LogItemCard(log = log, currency = uiState.selectedVehicle?.currency ?: "INR")
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
    animationDuration: Int = 500,
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
        content = {
            content(item)
        }
    )
}

@Composable
fun LogItemCard(log: LogItem, currency: String) {
    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(log.date))

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
                is LogItem.Fuel -> Triple(
                    Icons.Default.LocalGasStation,
                    MaterialTheme.colorScheme.primary,
                    "Fuel Fill-Up"
                ) to "${String.format("%.2f", log.quantity)} L @ ${currency}${String.format("%.2f", log.pricePerUnit)}/L"

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

                is LogItem.Trip -> Triple(
                    Icons.Default.DirectionsCar,
                    MaterialTheme.colorScheme.primary,
                    "Trip (${log.purpose})"
                ) to "Distance: ${String.format("%.1f", log.endOdo - log.startOdo)} km"
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
                    Text(
                        text = "Odo: ${log.odometer.toInt()}",
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
