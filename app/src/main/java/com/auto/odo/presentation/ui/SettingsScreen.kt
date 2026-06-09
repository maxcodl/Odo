package com.auto.odo.presentation.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.core.NavBarStyle
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.presentation.viewmodel.SUPPORTED_CURRENCIES
import com.auto.odo.presentation.viewmodel.SettingsViewModel
import com.auto.odo.presentation.viewmodel.currencySymbol

private fun persistTreeUriPermission(context: android.content.Context, uri: Uri, flags: Int) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    fullScreenStatusBar: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF launchers
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreeUriPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.onImportFolderSelected(uri)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreeUriPermission(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onExportFolderSelected(uri)
        }
    }

    // Snackbar
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        val msg = uiState.successMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
            viewModel.clearMessage()
        }
    }

    // Import confirmation dialog
    if (uiState.showConfirmImportDialog) {
        ImportConfirmationDialog(
            onConfirm = { viewModel.confirmImport() },
            onDismiss = { viewModel.dismissImportDialog() }
        )
    }

    // Delete vehicle confirmation dialog
    uiState.vehiclePendingDelete?.let { vehicle ->
        DeleteVehicleDialog(
            vehicleName = vehicle.name,
            onConfirm = { viewModel.confirmDeleteVehicle() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    // Edit currency dialog
    uiState.vehiclePendingCurrencyEdit?.let { vehicle ->
        EditCurrencyDialog(
            vehicleName = vehicle.name,
            selectedCurrency = uiState.editCurrencySelected,
            onCurrencySelected = { viewModel.onEditCurrencySelected(it) },
            onConfirm = { viewModel.saveCurrencyEdit() },
            onDismiss = { viewModel.dismissCurrencyEdit() }
        )
    }

    // Add vehicle bottom sheet
    if (uiState.showAddVehicleSheet) {
        AddVehicleSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.closeAddVehicleSheet() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Vehicles ──────────────────────────────────────────────────────
            SectionLabel("Vehicles")

            VehiclesSection(
                vehicles = uiState.vehicles,
                currentVehicleId = uiState.currentVehicleId,
                onSelect = { viewModel.selectVehicle(it.id) },
                onDelete = { viewModel.requestDeleteVehicle(it) },
                onAdd = { viewModel.openAddVehicleSheet() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Preferences ──────────────────────────────────────────────────
            SectionLabel("Preferences")

            PreferencesSection(
                activeVehicle = uiState.activeVehicle,
                navBarStyle = uiState.navBarStyle,
                fullScreenStatusBar = uiState.fullScreenStatusBar,
                autoHideTitleBar = uiState.autoHideTitleBar,
                onChangeCurrency = {
                    uiState.activeVehicle?.let { viewModel.openCurrencyEdit(it) }
                },
                onNavBarStyleChange = { viewModel.setNavBarStyle(it) },
                onFullScreenStatusBarChange = { viewModel.setFullScreenStatusBar(it) },
                onAutoHideTitleBarChange = { viewModel.setAutoHideTitleBar(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Data Management ───────────────────────────────────────────────
            SectionLabel("Data Management")

            DataActionCard(
                icon = Icons.Default.FileDownload,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = "Import Data",
                subtitle = "Load data from CSV backup files\n(Vehicles, Fuel Log, Services, Trips)",
                actionLabel = if (uiState.isImporting) "Importing…" else "Select Folder & Import",
                isLoading = uiState.isImporting,
                accentColor = MaterialTheme.colorScheme.tertiary,
                onActionClick = { importLauncher.launch(null) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            DataActionCard(
                icon = Icons.Default.FileUpload,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "Export Data",
                subtitle = "Save all data as CSV backup files\n(Vehicles, Fuel Log, Services, Trips)",
                actionLabel = if (uiState.isExporting) "Exporting…" else "Select Folder & Export",
                isLoading = uiState.isExporting,
                accentColor = MaterialTheme.colorScheme.secondary,
                onActionClick = { exportLauncher.launch(null) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(110.dp)) // Padding for floating nav bar
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun VehiclesSection(
    vehicles: List<VehicleEntity>,
    currentVehicleId: Long?,
    onSelect: (VehicleEntity) -> Unit,
    onDelete: (VehicleEntity) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (vehicles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No vehicles yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                vehicles.forEachIndexed { index, vehicle ->
                    VehicleRow(
                        vehicle = vehicle,
                        isActive = vehicle.id == currentVehicleId,
                        onSelect = { onSelect(vehicle) },
                        onDelete = { onDelete(vehicle) }
                    )
                    if (index < vehicles.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Add vehicle row
            if (vehicles.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdd() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add vehicle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Add Vehicle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun VehicleRow(
    vehicle: VehicleEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val vehicleIcon = if (vehicle.type == "Bike") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = vehicleIcon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "${vehicle.type} · ${vehicle.distanceUnit} · ${vehicle.fuelUnit} · ${currencySymbol(vehicle.currency)} ${vehicle.currency}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete ${vehicle.name}",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PreferencesSection(
    activeVehicle: VehicleEntity?,
    navBarStyle: NavBarStyle,
    fullScreenStatusBar: Boolean,
    autoHideTitleBar: Boolean,
    onChangeCurrency: () -> Unit,
    onNavBarStyleChange: (NavBarStyle) -> Unit,
    onFullScreenStatusBarChange: (Boolean) -> Unit,
    onAutoHideTitleBarChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Currency
            PreferenceRow(
                icon = Icons.Default.CurrencyExchange,
                iconColor = MaterialTheme.colorScheme.secondary,
                title = "Currency",
                subtitle = activeVehicle?.let { "${currencySymbol(it.currency)} ${it.currency}" } ?: "Not selected",
                onClick = onChangeCurrency
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Nav Bar Style
            var showNavBarStyleMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                PreferenceRow(
                    icon = Icons.Default.TableChart,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "Navigation Bar Style",
                    subtitle = navBarStyle.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { showNavBarStyleMenu = true }
                )

                DropdownMenu(
                    expanded = showNavBarStyleMenu,
                    onDismissRequest = { showNavBarStyleMenu = false }
                ) {
                    NavBarStyle.values().forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onNavBarStyleChange(style)
                                showNavBarStyleMenu = false
                            },
                            trailingIcon = if (style == navBarStyle) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Full Screen Status Bar
            PreferenceSwitchRow(
                icon = Icons.Default.Fullscreen,
                iconColor = MaterialTheme.colorScheme.tertiary,
                title = "Immersive Status Bar",
                subtitle = "Hide status bar for a cleaner look",
                checked = fullScreenStatusBar,
                onCheckedChange = onFullScreenStatusBarChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Auto Hide Title Bar
            PreferenceSwitchRow(
                icon = Icons.Default.UnfoldLess,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "Smart Title Bar",
                subtitle = "Hide title bar while scrolling",
                checked = autoHideTitleBar,
                onCheckedChange = onAutoHideTitleBarChange
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun PreferenceSwitchRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) } } else null
        )
    }
}

@Composable
private fun DataActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    isLoading: Boolean,
    accentColor: Color,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ImportConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Replace All Data?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This will permanently delete all current vehicles, fuel logs, service records, expenses and trips — then replace them with data from the selected CSV files.\n\nThis action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(10.dp)) {
                Text("Yes, Replace")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVehicleSheet(
    uiState: com.auto.odo.presentation.viewmodel.SettingsUiState,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add Vehicle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure your vehicle's preferences",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Name
            OutlinedTextField(
                value = uiState.newVehicleName,
                onValueChange = { viewModel.onNewVehicleNameChanged(it) },
                label = { Text("Vehicle Name") },
                placeholder = { Text("e.g. My Bike, Family Car") },
                singleLine = true,
                isError = uiState.addVehicleError != null,
                supportingText = uiState.addVehicleError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type
            SheetOptionRow(
                label = "Type",
                options = listOf("Bike", "Car"),
                selected = uiState.newVehicleType,
                onSelect = { viewModel.onNewVehicleTypeChanged(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Distance unit
            SheetOptionRow(
                label = "Distance",
                options = listOf("km", "miles"),
                selected = uiState.newVehicleDistUnit,
                onSelect = { viewModel.onNewVehicleDistUnitChanged(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fuel unit
            SheetOptionRow(
                label = "Fuel",
                options = listOf("Liters", "Gallons"),
                selected = uiState.newVehicleFuelUnit,
                onSelect = { viewModel.onNewVehicleFuelUnitChanged(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Currency
            SheetOptionRow(
                label = "Currency",
                options = SUPPORTED_CURRENCIES.map { it.first },
                selected = uiState.newVehicleCurrency,
                onSelect = { viewModel.onNewVehicleCurrencyChanged(it) },
                labelMapper = { code -> "${currencySymbol(code)} $code" }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveNewVehicle() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Vehicle", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SheetOptionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelMapper: (String) -> String = { it }
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!isSelected) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                ) {
                    Text(
                        text = labelMapper(option),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCurrencyDialog(
    vehicleName: String,
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                Icons.Default.CurrencyExchange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = {
            Text(
                "Currency — $vehicleName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_CURRENCIES.forEach { (code, symbol) ->
                    val isSelected = code == selectedCurrency
                    Surface(
                        onClick = { onCurrencySelected(code) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currencyFullName(code),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) { Text("Apply") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
        }
    )
}

private fun currencyFullName(code: String): String = when (code) {
    "INR" -> "Indian Rupee"
    "USD" -> "US Dollar"
    "EUR" -> "Euro"
    "GBP" -> "British Pound"
    "AED" -> "UAE Dirham"
    "BRL" -> "Brazilian Real"
    else -> code
}

@Composable
private fun DeleteVehicleDialog(
    vehicleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                "Delete \"$vehicleName\"?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "This will permanently delete this vehicle and ALL its fuel logs, service records, expenses, and trips.\n\nThis cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
        }
    )
}
