package com.auto.odo.presentation.ui

import com.auto.odo.FloatingNavBarClearance
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.core.UnitConverter
import com.auto.odo.core.location.Route
import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.FeedFilters
import com.auto.odo.presentation.viewmodel.FeedSort
import com.auto.odo.presentation.viewmodel.FeedType
import com.auto.odo.presentation.viewmodel.LogsFeedUiState
import com.auto.odo.presentation.viewmodel.LogsFeedViewModel
import com.auto.odo.presentation.viewmodel.currencySymbol
import com.auto.odo.presentation.viewmodel.monthKey
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
    fullScreenStatusBar: Boolean = false,
    onNavigateToEdit: (LogItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LogsFeedContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onMonthSelected = viewModel::setMonth,
        onCostRange = viewModel::setCostRange,
        onQueryChange = viewModel::setQuery,
        onSortSelected = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        onDeleteLog = viewModel::deleteLog,
        onUndoDelete = viewModel::undoDelete,
        onNavigateToEdit = onNavigateToEdit
    )
}

private val FeedType.icon: ImageVector
    get() = when (this) {
        FeedType.ALL -> Icons.AutoMirrored.Filled.List
        FeedType.FUEL -> Icons.Default.LocalGasStation
        FeedType.SERVICE -> Icons.Default.Build
        FeedType.EXPENSE -> Icons.Default.ShoppingCart
        FeedType.TRIP -> Icons.Default.DirectionsCar
    }

/** ₹3,200 or ₹3,200.50 — decimals only when they carry information. */
internal fun formatMoney(amount: Double, currency: String): String {
    val pattern = if (amount % 1.0 == 0.0) "%,.0f" else "%,.2f"
    return currencySymbol(currency) + String.format(Locale.getDefault(), pattern, amount)
}

private fun monthLabel(key: String): String = runCatching {
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!)
}.getOrDefault(key)

private fun tripDistance(log: LogItem.Trip, distUnit: String): Double =
    (log.endOdo - log.startOdo).let { if (distUnit == "miles") UnitConverter.kmToMiles(it) else it }

/** Per fill-up: (distance since previous fill, efficiency of the full-to-full segment it closes). Stored units. */
private fun computeFuelMetrics(logs: List<LogItem>): Map<Long, Pair<Double, Double?>> {
    val fuelLogs = logs.filterIsInstance<LogItem.Fuel>().sortedBy { it.odometer }
    val metrics = mutableMapOf<Long, Pair<Double, Double?>>()
    if (fuelLogs.size < 2) return metrics
    var anchorOdo = fuelLogs[0].odometer
    var accumulatedFuel = 0.0
    val pendingSequence = mutableListOf<Pair<Long, Double>>()
    for (i in 1 until fuelLogs.size) {
        val current = fuelLogs[i]
        val legDelta = current.odometer - fuelLogs[i - 1].odometer
        accumulatedFuel += current.quantity
        pendingSequence.add(current.id to legDelta)
        if (!current.isPartialTank) {
            val efficiency = if (accumulatedFuel > 0) (current.odometer - anchorOdo) / accumulatedFuel else null
            for (item in pendingSequence) metrics[item.first] = item.second to efficiency
            anchorOdo = current.odometer
            accumulatedFuel = 0.0
            pendingSequence.clear()
        } else {
            metrics[current.id] = legDelta to null
        }
    }
    return metrics
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsFeedContent(
    uiState: LogsFeedUiState,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onMonthSelected: (String?) -> Unit = {},
    onCostRange: (Double?, Double?) -> Unit = { _, _ -> },
    onQueryChange: (String) -> Unit = {},
    onSortSelected: (FeedSort) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onDeleteLog: (LogItem) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onNavigateToEdit: (LogItem) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var selectedDetailPayload by remember { mutableStateOf<LogDetailPayload?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(uiState.filters.query.isNotEmpty()) }

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
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = FloatingNavBarClearance)) { data ->
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
                    title = {
                        Column {
                            Text("Logs", fontWeight = FontWeight.Bold)
                            uiState.selectedVehicle?.let {
                                Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searchOpen) onQueryChange("")
                            searchOpen = !searchOpen
                        }) {
                            Icon(if (searchOpen) Icons.Default.SearchOff else Icons.Default.Search, contentDescription = "Search logs")
                        }
                    },
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
                    return@Column
                }

                AnimatedVisibility(visible = searchOpen) {
                    OutlinedTextField(
                        value = uiState.filters.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Station, service, notes, place…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.filters.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                val types = FeedType.entries
                val pagerState = rememberPagerState { types.size }
                val scope = rememberCoroutineScope()
                val counts = remember(uiState.logs) { types.associateWith { t -> uiState.logs.count(t::matches) } }

                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) }
                ) {
                    types.forEachIndexed { index, type ->
                        FeedTab(
                            type = type,
                            count = counts[type] ?: 0,
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                        )
                    }
                }

                FilterBar(
                    filters = uiState.filters,
                    months = uiState.months,
                    currency = uiState.selectedVehicle?.currency ?: "INR",
                    onMonthSelected = onMonthSelected,
                    onCostRange = onCostRange,
                    onSortSelected = onSortSelected,
                    onClearFilters = {
                        onClearFilters()
                        searchOpen = false
                    }
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                val vehicle = uiState.selectedVehicle
                val currency = vehicle?.currency ?: "INR"
                val distUnit = vehicle?.distanceUnit ?: "km"
                val fuelUnit = vehicle?.fuelUnit ?: "Liters"
                // From every log, not the filtered view: a fill-up's efficiency depends on its neighbours
                val fuelMetrics = remember(uiState.allLogs) { computeFuelMetrics(uiState.allLogs) }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { types[it].name }
                ) { page ->
                    val type = types[page]
                    val pageLogs = remember(uiState.logs, type) { uiState.logs.filter(type::matches) }
                    if (pageLogs.isEmpty()) {
                        EmptyFeed(type, uiState.filters.isActive)
                    } else {
                        FeedList(
                            logs = pageLogs,
                            type = type,
                            grouped = uiState.filters.sort == FeedSort.NEWEST || uiState.filters.sort == FeedSort.OLDEST,
                            currency = currency,
                            distUnit = distUnit,
                            fuelUnit = fuelUnit,
                            fuelMetrics = fuelMetrics,
                            onDeleteLog = onDeleteLog,
                            onOpen = { selectedDetailPayload = it }
                        )
                    }
                }
            }
        }

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

@Composable
private fun FeedTab(type: FeedType, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Tab(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(type.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    filters: FeedFilters,
    months: List<String>,
    currency: String,
    onMonthSelected: (String?) -> Unit,
    onCostRange: (Double?, Double?) -> Unit,
    onSortSelected: (FeedSort) -> Unit,
    onClearFilters: () -> Unit
) {
    var monthMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var costDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FilterChip(
                selected = filters.month != null,
                onClick = { monthMenu = true },
                label = { Text(filters.month?.let(::monthLabel) ?: "Any month") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                DropdownMenuItem(text = { Text("Any month") }, onClick = { onMonthSelected(null); monthMenu = false })
                months.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(monthLabel(m)) },
                        onClick = { onMonthSelected(m); monthMenu = false },
                        trailingIcon = if (m == filters.month) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        val costLabel = when {
            filters.minCost != null && filters.maxCost != null -> "${formatMoney(filters.minCost, currency)} – ${formatMoney(filters.maxCost, currency)}"
            filters.minCost != null -> "≥ ${formatMoney(filters.minCost, currency)}"
            filters.maxCost != null -> "≤ ${formatMoney(filters.maxCost, currency)}"
            else -> "Any cost"
        }
        FilterChip(
            selected = filters.minCost != null || filters.maxCost != null,
            onClick = { costDialog = true },
            label = { Text(costLabel) },
            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        Box {
            FilterChip(
                selected = filters.sort != FeedSort.NEWEST,
                onClick = { sortMenu = true },
                label = { Text(filters.sort.label) },
                leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                FeedSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { onSortSelected(s); sortMenu = false },
                        trailingIcon = if (s == filters.sort) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        if (filters.isActive) {
            AssistChip(
                onClick = onClearFilters,
                label = { Text("Clear") },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }

    if (costDialog) {
        CostRangeDialog(
            currency = currency,
            initialMin = filters.minCost,
            initialMax = filters.maxCost,
            onApply = { min, max -> onCostRange(min, max); costDialog = false },
            onDismiss = { costDialog = false }
        )
    }
}

@Composable
private fun CostRangeDialog(
    currency: String,
    initialMin: Double?,
    initialMax: Double?,
    onApply: (Double?, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    fun Double?.asField() = this?.let { if (it % 1.0 == 0.0) "%.0f".format(Locale.US, it) else it.toString() } ?: ""
    var min by remember { mutableStateOf(initialMin.asField()) }
    var max by remember { mutableStateOf(initialMax.asField()) }
    val symbol = currencySymbol(currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Default.Payments, contentDescription = null) },
        title = { Text("Filter by cost") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = min, onValueChange = { min = it },
                    label = { Text("Min") }, prefix = { Text(symbol) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = max, onValueChange = { max = it },
                    label = { Text("Max") }, prefix = { Text(symbol) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(min.replace(',', '.').toDoubleOrNull(), max.replace(',', '.').toDoubleOrNull())
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null, null) }) { Text("Any cost") }
        }
    )
}

@Composable
private fun EmptyFeed(type: FeedType, filtersActive: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                type.icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    filtersActive -> "Nothing matches these filters"
                    type == FeedType.ALL -> "No logs yet"
                    type == FeedType.TRIP -> "No trips yet"
                    else -> "No ${type.label.lowercase()} logs yet"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedList(
    logs: List<LogItem>,
    type: FeedType,
    grouped: Boolean,
    currency: String,
    distUnit: String,
    fuelUnit: String,
    fuelMetrics: Map<Long, Pair<Double, Double?>>,
    onDeleteLog: (LogItem) -> Unit,
    onOpen: (LogDetailPayload) -> Unit
) {
    // Trips have no cost, so their page (and headers) total distance instead
    fun summary(items: List<LogItem>): String =
        if (type == FeedType.TRIP) "%.1f %s".format(Locale.getDefault(), items.filterIsInstance<LogItem.Trip>().sumOf { tripDistance(it, distUnit) }, distUnit)
        else formatMoney(items.sumOf { it.totalCost }, currency)

    val sections = remember(logs, grouped) {
        if (grouped) logs.groupBy { monthKey(it.date) }.toList() else listOf("" to logs)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        item(key = "summary") {
            Text(
                "${logs.size} ${if (logs.size == 1) "entry" else "entries"} · ${summary(logs)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        sections.forEach { (month, items) ->
            if (month.isNotEmpty()) {
                stickyHeader(key = "header_$month") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            monthLabel(month),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            summary(items),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(items, key = { "${it.javaClass.simpleName}_${it.id}" }) { log ->
                val delta = (log as? LogItem.Fuel)?.let { fuelMetrics[it.id]?.first }
                val eff = (log as? LogItem.Fuel)?.let { fuelMetrics[it.id]?.second }
                LogItemCardWrapper(
                    log = log,
                    onDelete = onDeleteLog,
                    onClick = { onOpen(LogDetailPayload(log, delta, eff)) }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
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
                    val places = listOfNotNull(log.startPlace, log.endPlace).distinct()
                    if (places.isNotEmpty()) DetailRow(label = "Route", valueText = places.joinToString(" → "))
                    DetailRow(label = "Purpose", valueText = log.purpose)
                    DetailRow(label = "Distance", valueText = "${String.format(Locale.US, "%.1f", displayEnd - displayStart)} $distUnit")
                    DetailRow(label = "Start Odo", valueText = "${String.format(Locale.US, "%.1f", displayStart)} $distUnit")
                    DetailRow(label = "End Odo", valueText = "${String.format(Locale.US, "%.1f", displayEnd)} $distUnit")
                    if (!log.notes.isNullOrEmpty()) DetailRow(label = "Notes", valueText = log.notes)
                    val route = remember(log.route) { Route.decode(log.route) }
                    if (route.isNotEmpty()) {
                        LocationMap(points = route, modifier = Modifier.fillMaxWidth().height(240.dp).padding(16.dp))
                    }
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
            modifier = Modifier.padding(start = 74.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** One log row: type icon · title + details · amount. Shared with the dashboard's recent activity. */
@Composable
fun LogItemCard(
    log: LogItem,
    currency: String,
    distUnit: String = "km",
    fuelUnit: String = "Liters",
    rawDelta: Double? = null,
    rawEfficiency: Double? = null
) {
    val dateFormatter = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    val date = remember(log.date) { dateFormatter.format(Date(log.date)) }
    val fuelUnitLabel = if (fuelUnit == "Gallons") "gal" else "L"
    fun dist(km: Double) = if (distUnit == "miles") UnitConverter.kmToMiles(km) else km

    val (icon, tint) = when (log) {
        is LogItem.Fuel -> Icons.Default.LocalGasStation to MaterialTheme.colorScheme.primary
        is LogItem.Service -> Icons.Default.Build to MaterialTheme.colorScheme.secondary
        is LogItem.Expense -> Icons.Default.ShoppingCart to MaterialTheme.colorScheme.tertiary
        is LogItem.Trip -> Icons.Default.DirectionsCar to MaterialTheme.colorScheme.primary
    }

    val title: String
    val subtitle: String
    val amount: String
    var badge: String? = null
    var badgeHighlight = false
    when (log) {
        is LogItem.Fuel -> {
            val qty = if (fuelUnit == "Gallons") UnitConverter.litersToGallons(log.quantity) else log.quantity
            title = log.stationName?.takeIf { it.isNotBlank() } ?: "Fill-up"
            subtitle = listOfNotNull(
                date,
                "%.2f %s".format(Locale.US, qty, fuelUnitLabel),
                rawDelta?.let { "+%.0f %s".format(Locale.US, dist(it), distUnit) }
            ).joinToString(" · ")
            amount = formatMoney(log.totalCost, currency)
            if (rawEfficiency != null) {
                val eff = when {
                    distUnit == "miles" && fuelUnit == "Gallons" -> UnitConverter.kmToMiles(rawEfficiency) / UnitConverter.litersToGallons(1.0)
                    distUnit == "miles" -> UnitConverter.kmToMiles(rawEfficiency)
                    fuelUnit == "Gallons" -> rawEfficiency / UnitConverter.litersToGallons(1.0)
                    else -> rawEfficiency
                }
                badge = "%.1f %s".format(Locale.US, eff, if (fuelUnit == "Gallons") "mpg" else "$distUnit/L")
                badgeHighlight = true
            } else if (log.isPartialTank) {
                badge = "Partial"
            }
        }
        is LogItem.Service -> {
            title = log.serviceType
            subtitle = "$date · Odo %,.0f %s".format(Locale.getDefault(), dist(log.odometer), distUnit)
            amount = if (log.totalCost > 0) formatMoney(log.totalCost, currency) else "—"
        }
        is LogItem.Expense -> {
            title = log.category
            subtitle = listOfNotNull(date, log.notes?.takeIf { it.isNotBlank() }).joinToString(" · ")
            amount = formatMoney(log.totalCost, currency)
        }
        is LogItem.Trip -> {
            val places = listOfNotNull(log.startPlace, log.endPlace).distinct()
            title = if (places.isNotEmpty()) places.joinToString(" → ") else "${log.purpose} trip"
            subtitle = listOfNotNull(date, log.purpose.takeIf { places.isNotEmpty() }, "Odo %,.0f".format(Locale.getDefault(), dist(log.endOdo)))
                .joinToString(" · ")
            amount = "%.1f %s".format(Locale.US, tripDistance(log, distUnit), distUnit)
            if (log.route != null) badge = "Auto"
        }
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
                .background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            badge?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badgeHighlight) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            (if (badgeHighlight) tint else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
