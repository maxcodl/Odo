package com.auto.odo.presentation.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.auto.odo.core.location.AutoTrips
import com.auto.odo.core.location.hasLocationPermission

/** Pump lookup + automatic trips toggles, with a checklist of what automatic trips still needs. */
@SuppressLint("BatteryLife") // Sideloaded app: asking for the exemption directly is allowed
@Composable
fun LocationSettingsCard(
    pumpLookup: Boolean,
    onPumpLookupChange: (Boolean) -> Unit,
    autoTrips: Boolean,
    onAutoTripsChange: (Boolean) -> Unit,
    onPermissionsChanged: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Bumped whenever permissions may have changed, to re-read them
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPermissionsChanged()
        onPauseOrDispose { }
    }
    val afterGrant: (Any?) -> Unit = { refresh++; onPermissionsChanged() }

    val foregroundPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), afterGrant)
    val singlePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Permanently denied (or Android 11+ declined): only the app's settings page can change it
        if (!granted) context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
        afterGrant(granted)
    }
    val batterySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult(), afterGrant)

    val tripPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            PreferenceSwitchRow(
                icon = Icons.Default.LocalGasStation,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "Detect Petrol Pump",
                subtitle = "Fill in the station from GPS when logging a fill-up (uses OpenStreetMap online)",
                checked = pumpLookup,
                onCheckedChange = {
                    onPumpLookupChange(it)
                    if (it && !hasLocationPermission(context)) foregroundPermissions.launch(tripPermissions.take(2).toTypedArray())
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            PreferenceSwitchRow(
                icon = Icons.Default.DirectionsCar,
                iconColor = MaterialTheme.colorScheme.tertiary,
                title = "Automatic Trip Log",
                subtitle = "Records trips when driving is detected. GPS runs only while driving (roughly 3–6% battery per hour of driving)",
                checked = autoTrips,
                onCheckedChange = {
                    onAutoTripsChange(it)
                    if (it) foregroundPermissions.launch(tripPermissions)
                }
            )

            if (autoTrips) {
                key(refresh) {
                    val fine = AutoTrips.hasFineLocation(context)
                    val background = AutoTrips.hasBackgroundLocation(context)
                    val activity = AutoTrips.hasActivityRecognition(context)
                    val battery = AutoTrips.ignoresBatteryOptimizations(context)
                    Column(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (fine && background && activity) "Active — trips are recorded when you drive"
                            else "Not running yet — finish the steps below",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (fine && background && activity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        ChecklistRow("Precise location", fine) { foregroundPermissions.launch(tripPermissions) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ChecklistRow("Location: Allow all the time", background, enabled = fine) {
                                singlePermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                            ChecklistRow("Physical activity", activity) {
                                singlePermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                        }
                        ChecklistRow("Battery: unrestricted (recommended)", battery) {
                            batterySettings.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Trips still cut off? Phone-specific fixes ↗", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, done: Boolean, enabled: Boolean = true, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Missing",
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (!done) {
            TextButton(onClick = onFix, enabled = enabled) { Text("Grant") }
        }
    }
}
