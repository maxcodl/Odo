package com.auto.odo.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.viewmodel.AddServiceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    viewModel: AddServiceViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    AddServiceScreenContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onNavigateBack = onNavigateBack,
        onOdometerChanged = viewModel::onOdometerChanged,
        onServiceTypeChanged = viewModel::onServiceTypeChanged,
        onTotalCostChanged = viewModel::onTotalCostChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onDateChanged = viewModel::onDateChanged,
        onSaveClick = viewModel::saveService
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreenContent(
    uiState: com.auto.odo.presentation.viewmodel.AddServiceUiState,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onOdometerChanged: (String) -> Unit = {},
    onServiceTypeChanged: (String) -> Unit = {},
    onTotalCostChanged: (String) -> Unit = {},
    onNotesChanged: (String) -> Unit = {},
    onDateChanged: (Long) -> Unit = {},
    onSaveClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    var showDatePicker by remember { mutableStateOf(false) }
    var showDropdownMenu by remember { mutableStateOf(false) }

    val serviceTypes = listOf("Oil Change", "Tyres", "Battery", "Brakes", "Filters", "Other")

    Scaffold(
        modifier = if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("Log Service", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        val vehicle = uiState.selectedVehicle
        if (vehicle == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Vehicle Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Vehicle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(
                        vehicle.distanceUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 2. Date Picker
            val sdf = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
            OutlinedTextField(
                value = sdf.format(Date(uiState.date)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date of Service") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Odometer Input
            OutlinedTextField(
                value = uiState.odometer,
                onValueChange = onOdometerChanged,
                label = { Text("Odometer Reading (${vehicle.distanceUnit})") },
                placeholder = { Text("Last known: ${uiState.lastKnownOdometer}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.odometerError != null,
                supportingText = {
                    if (uiState.odometerError != null) {
                        Text(uiState.odometerError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Must be chronological with other logs.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Service Type Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.serviceType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Service Type") },
                    trailingIcon = {
                        IconButton(onClick = { showDropdownMenu = !showDropdownMenu }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Type")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    serviceTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                onServiceTypeChanged(type)
                                showDropdownMenu = false
                            }
                        )
                    }
                }
            }

            // 5. Total Cost
            OutlinedTextField(
                value = uiState.totalCost,
                onValueChange = onTotalCostChanged,
                label = { Text("Total Cost (${vehicle.currency})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // 6. Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes (Optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = onSaveClick,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Service Log", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateChanged(it)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddServicePreview() {
    val mockVehicle = com.auto.odo.data.entity.VehicleEntity(
        id = 1L,
        name = "Yamaha R15M",
        type = "Bike",
        fuelUnit = "Liters",
        distanceUnit = "km",
        currency = "INR"
    )

    val mockUiState = com.auto.odo.presentation.viewmodel.AddServiceUiState(
        selectedVehicle = mockVehicle,
        date = System.currentTimeMillis(),
        odometer = "14200",
        lastKnownOdometer = 12500.0,
        serviceType = "Oil Change",
        totalCost = "1250",
        notes = "Engine oil and filter element replacement.",
        isSaving = false,
        odometerError = null,
        saveSuccess = false
    )

    OdoTheme {
        AddServiceScreenContent(uiState = mockUiState)
    }
}
