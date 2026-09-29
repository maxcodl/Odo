package com.auto.odo.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.viewmodel.AddFillUpUiState
import com.auto.odo.presentation.viewmodel.AddFillUpViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddFillUpScreen(
    viewModel: AddFillUpViewModel,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AddFillUpContent(
        uiState = uiState,
        autoHideTitleBar = autoHideTitleBar,
        fullScreenStatusBar = fullScreenStatusBar,
        onNavigateBack = onNavigateBack,
        onDateChanged = viewModel::onDateChanged,
        onOdometerChanged = viewModel::onOdometerChanged,
        onQuantityChanged = viewModel::onQuantityChanged,
        onPricePerUnitChanged = viewModel::onPricePerUnitChanged,
        onTotalCostChanged = viewModel::onTotalCostChanged,
        onPartialTankChanged = viewModel::onPartialTankChanged,
        onStationNameChanged = viewModel::onStationNameChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onReceiptAttached = viewModel::onReceiptAttached,
        onScanOdometer = viewModel::scanOdometer,
        onScanPump = viewModel::scanFuelValues,
        onLiveResult = { result ->
            when (result) {
                is LiveScanResult.Odometer -> viewModel.onOdometerChanged(result.reading)
                is LiveScanResult.Fuel -> viewModel.applyFuelValues(result.values)
            }
        },
        onScanMessageShown = viewModel::onScanMessageShown,
        onSaveFillUp = viewModel::saveFillUp,
        onClearForm = viewModel::clearForm
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFillUpContent(
    uiState: AddFillUpUiState,
    autoHideTitleBar: Boolean = true,
    fullScreenStatusBar: Boolean = false,
    onNavigateBack: () -> Unit,
    onDateChanged: (Long) -> Unit,
    onOdometerChanged: (String) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onPricePerUnitChanged: (String) -> Unit,
    onTotalCostChanged: (String) -> Unit,
    onPartialTankChanged: (Boolean) -> Unit,
    onStationNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onReceiptAttached: (Uri?) -> Unit,
    onScanOdometer: (Uri) -> Unit,
    onScanPump: (Uri) -> Unit,
    onLiveResult: (LiveScanResult) -> Unit,
    onScanMessageShown: () -> Unit,
    onSaveFillUp: () -> Unit,
    onClearForm: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onReceiptAttached(uri)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.scanMessage) {
        uiState.scanMessage?.let {
            snackbarHostState.showSnackbar(it)
            onScanMessageShown()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = if (autoHideTitleBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Fill-Up" else "Log Fill-Up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                // Removed the top bar action button to place it closer to the inputs
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
            // 1. Selected Vehicle banner
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
                        "${vehicle.distanceUnit} / ${if(vehicle.fuelUnit == "Liters") "L" else "gal"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 1.5 Header & Clear Button (Noticeable but Minimal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fill-Up Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onClearForm,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, 
                        contentDescription = "Clear fields",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear Inputs", 
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Date Trigger
            val sdf = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
            OutlinedTextField(
                value = sdf.format(Date(uiState.date)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date of Fill-up") },
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
                placeholder = { Text("Last known: ${"%.0f".format(uiState.lastKnownOdometer)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.odometerError != null,
                trailingIcon = {
                    ScanActions(ScanTarget.ODOMETER, "odometer", uiState.isScanning, uiState.odometerScanFloor, uiState.lastPricePerUnit, onScanOdometer, onLiveResult)
                },
                supportingText = {
                    if (uiState.odometerError != null) {
                        Text(uiState.odometerError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Must be chronological with logs around this date.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 3.5 Pump display scan -> quantity / price / total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scan Pump Display", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Scan the pump display to fill quantity & total. Rate is prefilled from your last fill-up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScanActions(ScanTarget.PUMP, "pump display", uiState.isScanning, uiState.odometerScanFloor, uiState.lastPricePerUnit, onScanPump, onLiveResult)
            }

            // 4. Numeric Variables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = onQuantityChanged,
                    label = { Text("Quantity (${if(vehicle.fuelUnit == "Liters") "L" else "gal"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = uiState.pricePerUnit,
                    onValueChange = onPricePerUnitChanged,
                    label = { Text("Price Unit (${vehicle.currency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = uiState.totalCost,
                onValueChange = onTotalCostChanged,
                label = { Text("Total Cost (${vehicle.currency})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Partial Tank Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Partial Fill-Up", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Check if tank wasn't filled to maximum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = uiState.isPartialTank,
                    onCheckedChange = onPartialTankChanged
                )
            }

            // 6. Filling Station Name
            OutlinedTextField(
                value = uiState.stationName,
                onValueChange = onStationNameChanged,
                label = { Text("Filling Station Name (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // 7. Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes (Optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // 8. Receipt Attachment
            Text("Receipt Attachment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { receiptPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.receiptPath != null) {
                    AsyncImage(
                        model = uiState.receiptPath,
                        contentDescription = "Receipt (tap to change)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    FilledTonalIconButton(
                        onClick = { onReceiptAttached(null) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove receipt", modifier = Modifier.size(18.dp))
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to attach receipt image", style = MaterialTheme.typography.bodyMedium)
                        Text("Quantity, price & total are read automatically", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save Button
            Button(
                onClick = onSaveFillUp,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Log Entry", fontWeight = FontWeight.Bold)
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

/** Live camera scanner + gallery pick. Gallery images go through [onImage] for one-shot OCR. */
@Composable
private fun ScanActions(
    target: ScanTarget,
    label: String,
    isScanning: Boolean,
    odometerFloor: Double,
    lastRate: Double,
    onImage: (Uri) -> Unit,
    onLiveResult: (LiveScanResult) -> Unit
) {
    var showScanner by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onImage)
    }
    if (showScanner) {
        LiveScannerDialog(
            target = target,
            odometerFloor = odometerFloor,
            lastRate = lastRate,
            onResult = { onLiveResult(it); showScanner = false },
            onDismiss = { showScanner = false }
        )
    }
    if (isScanning) {
        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
        return
    }
    Row {
        IconButton(onClick = { showScanner = true }) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Scan $label with camera")
        }
        IconButton(onClick = { gallery.launch("image/*") }) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick $label image")
        }
    }
}
