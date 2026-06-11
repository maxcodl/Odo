package com.auto.odo.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.viewmodel.AddTripUiState
import com.auto.odo.presentation.viewmodel.AddTripViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddTripScreen(
    viewModel: AddTripViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AddTripContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onNavigateBack = onNavigateBack,
        onDateChanged = viewModel::onDateChanged,
        onStartOdoChanged = viewModel::onStartOdoChanged,
        onEndOdoChanged = viewModel::onEndOdoChanged,
        onPurposeChanged = viewModel::onPurposeChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onSaveTrip = viewModel::saveTrip
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTripContent(
    uiState: AddTripUiState,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit,
    onDateChanged: (Long) -> Unit,
    onStartOdoChanged: (String) -> Unit,
    onEndOdoChanged: (String) -> Unit,
    onPurposeChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveTrip: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("Log Trip", fontWeight = FontWeight.Bold) },
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
                if (uiState.isSaving) CircularProgressIndicator() 
                else Text("No vehicle selected")
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Vehicle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        vehicle.distanceUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 2. Date Picker
            val sdf = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
            OutlinedTextField(
                value = sdf.format(Date(uiState.date)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date of Trip") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Start Odometer Input
            OutlinedTextField(
                value = uiState.startOdo,
                onValueChange = onStartOdoChanged,
                label = { Text("Start Odometer (${vehicle.distanceUnit})") },
                placeholder = { Text("Last known: ${uiState.lastKnownOdometer}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // 4. End Odometer Input
            OutlinedTextField(
                value = uiState.endOdo,
                onValueChange = onEndOdoChanged,
                label = { Text("End Odometer (${vehicle.distanceUnit})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.odoError != null,
                supportingText = {
                    if (uiState.odoError != null) {
                        Text(uiState.odoError, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Must be chronological and >= Start Odometer.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Dynamic Calculated Distance
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Calculated Distance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${uiState.distanceDisplay} ${vehicle.distanceUnit}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 6. Purpose selection
            Text("Trip Purpose", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.purpose == "Personal",
                        onClick = { onPurposeChanged("Personal") }
                    )
                    Text("Personal")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.purpose == "Business",
                        onClick = { onPurposeChanged("Business") }
                    )
                    Text("Business")
                }
            }

            // 7. Notes
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
                onClick = onSaveTrip,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Trip Log", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Date Picker Dialog
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
fun AddTripPreview() {
    OdoTheme {
        AddTripContent(
            uiState = AddTripUiState(
                selectedVehicle = VehicleEntity(1, "Test Car", "Car", "Liters", "km", "INR"),
                startOdo = "1000",
                endOdo = "1050",
                distanceDisplay = "50.0"
            ),
            autoHideTitleBar = false,
            fullScreenStatusBar = false,
            onNavigateBack = {},
            onDateChanged = {},
            onStartOdoChanged = {},
            onEndOdoChanged = {},
            onPurposeChanged = {},
            onNotesChanged = {},
            onSaveTrip = {}
        )
    }
}
