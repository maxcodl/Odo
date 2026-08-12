package com.auto.odo.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import android.net.Uri
import com.auto.odo.core.background.BackupWorker
import com.auto.odo.data.entity.ServiceLogEntity
import com.auto.odo.data.entity.FuelLogEntity
import com.auto.odo.data.entity.TripLogEntity
import com.auto.odo.data.entity.VehicleEntity
import com.auto.odo.data.entity.ExpenseLogEntity
import com.auto.odo.data.AppDatabase
import com.auto.odo.domain.model.BackupFileInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import java.io.FileOutputStream
import java.io.File
import java.util.*
import javax.inject.Inject
import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val lastBackupTime: String = "Never",
    val backupMessage: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _backupList = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backupList: StateFlow<List<BackupFileInfo>> = _backupList.asStateFlow()

    private val _isLoadingList = MutableStateFlow(false)
    val isLoadingList: StateFlow<Boolean> = _isLoadingList.asStateFlow()

    private val workManager = WorkManager.getInstance(application)
    private val prefs = application.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    init {
        val savedMillis = prefs.getLong("last_backup_millis", 0L)
        if (savedMillis > 0) {
            _uiState.update { it.copy(lastBackupTime = formatTimestamp(savedMillis)) }
        }
    }

    private fun extractTimestampFromFileName(name: String): Long? {
        val regex = Regex("odo_backup_(\\d{13})\\.zip")
        val match = regex.find(name)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun formatTimestamp(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        val now = Instant.now()
        return if (Duration.between(instant, now).toMinutes() < 1) {
            "Just now"
        } else {
            val formatter = DateTimeFormatter
                .ofPattern("d MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
    }

    private fun saveLastBackupTime() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_backup_millis", now).apply()
        _uiState.update { it.copy(lastBackupTime = formatTimestamp(now)) }
    }

    // ---------- Backup ----------
    fun triggerManualBackup() {
        _uiState.update { it.copy(isBackingUp = true, backupMessage = "Starting backup...") }

        val backupRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueueUniqueWork("DriveManualBackup", ExistingWorkPolicy.REPLACE, backupRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(backupRequest.id)
                .collect { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup successful!") }
                            saveLastBackupTime()
                            cleanupOldBackups()
                            currentCoroutineContext().cancel()
                        }
                        WorkInfo.State.FAILED -> {
                            _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup failed.") }
                            currentCoroutineContext().cancel()
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup cancelled.") }
                            currentCoroutineContext().cancel()
                        }
                        else -> {}
                    }
                }
        }
    }

    // ---------- List backups ----------
    fun listBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application)
                if (account == null) {
                    _backupList.value = emptyList()
                    return@launch
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("OdoAuto").build()

                val result = driveService.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .execute()

                _backupList.value = result.files.map { file ->
                    val rawName = file.name ?: "Unnamed backup"
                    val dateStr = file.modifiedTime?.toStringRfc3339()?.let { formatModifiedTime(it) }
                        ?: extractTimestampFromFileName(rawName)?.let { formatTimestamp(it) }
                        ?: "Unknown date"
                    BackupFileInfo(
                        id = file.id,
                        displayName = "Backup – $dateStr",
                        formattedDate = dateStr
                    )
                }
            } catch (e: Exception) {
                _backupList.value = emptyList()
                _uiState.update { it.copy(backupMessage = "Failed to fetch backups: ${e.localizedMessage}") }
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    // ---------- Restore ----------
    fun triggerRestore(backupFileId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBackingUp = true, backupMessage = "Restoring...") }

            val account = GoogleSignIn.getLastSignedInAccount(application)
            if (account == null) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Please sign in first.") }
                return@launch
            }

            val credential = GoogleAccountCredential.usingOAuth2(
                application,
                Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
            ).setSelectedAccount(account.account)

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("OdoAuto").build()

            try {
                val fileIdToRestore = backupFileId ?: run {
                    val files = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setOrderBy("createdTime desc")
                        .setPageSize(1)
                        .execute()
                    files.files.firstOrNull()?.id ?: throw Exception("No backup found")
                }

                val zipFile = File(application.cacheDir, "restore.zip")
                driveService.files().get(fileIdToRestore)
                    .executeMediaAndDownloadTo(FileOutputStream(zipFile))

                AppDatabase.getDatabase(application).close()
                AppDatabase.INSTANCE = null

                val dbDir = application.getDatabasePath("odo_database").parentFile!!
                listOf("odo_database", "odo_database-shm", "odo_database-wal").forEach { name ->
                    val f = File(dbDir, name)
                    if (f.exists()) f.delete()
                }

                java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        FileOutputStream(File(dbDir, entry.name)).use { zis.copyTo(it) }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()

                withContext(Dispatchers.Main) {
                    val restartIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
                    restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    application.startActivity(restartIntent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Restore failed: ${e.localizedMessage}") }
            }
        }
    }

    // ---------- Delete Backup ----------
    fun deleteBackup(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application) ?: return@launch
                val credential = GoogleAccountCredential.usingOAuth2(application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("OdoAuto").build()

                driveService.files().delete(fileId).execute()
                listBackups()
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "Failed to delete backup: ${e.localizedMessage}") }
            }
        }
    }

    // ---------- Cleanup Old Backups ----------
    fun cleanupOldBackups(keepCount: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application) ?: return@launch
                val credential = GoogleAccountCredential.usingOAuth2(application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("OdoAuto").build()

                val result = driveService.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .execute()
                result.files.drop(keepCount).forEach { file ->
                    driveService.files().delete(file.id).execute()
                }
            } catch (_: Exception) { }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }

    private fun formatModifiedTime(rfc3339: String?): String {
        if (rfc3339.isNullOrBlank()) return "Unknown"
        return try {
            val instant = java.time.Instant.parse(rfc3339)
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern("d MMM yyyy, HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // ---------- Smart CSV Export ----------
    fun exportCsv(uri: Uri, tableType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isBackingUp = true, backupMessage = "Exporting $tableType...") }
                
                val db = AppDatabase.getDatabase(application)
                val tableName = when(tableType) {
                    "vehicles" -> "vehicles" 
                    "services" -> "service_logs"  
                    "fuel_entries" -> "fuel_logs" 
                    "trips" -> "trip_logs"            
                    else -> return@launch
                }

                val cursor = db.openHelper.readableDatabase.query("SELECT * FROM $tableName")
                
                application.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    val headers = cursor.columnNames.joinToString(",")
                    writer.write("$headers\n")
                    
                    while (cursor.moveToNext()) {
                        val rowData = (0 until cursor.columnCount).map { i -> 
                            "\"${cursor.getString(i) ?: ""}\"" 
                        }
                        writer.write(rowData.joinToString(",") + "\n")
                    }
                }
                cursor.close()
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Exported successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    // ==========================================================================================
    //  Smart CSV Import
    //
    //  Handles the real-world Drivvo-style export files:
    //    - Vehicles.csv    -> vehicles table
    //    - Fuel_Log.csv    -> fuel_logs AND service_logs AND expense_logs
    //                         (this one file mixes all three via its "Record Type" column:
    //                          0 = fuel, 1 = service, 4 = misc "adhoc" expense)
    //    - Trip_Log.csv    -> trip_logs
    //    - Services.csv    -> reminder/category config only, nothing to import (see below)
    //
    //  Plus the app's own native re-import format for service logs (unchanged from before).
    // ==========================================================================================
    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isBackingUp = true, backupMessage = "Reading file...") }

                val headerLine = application.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readLine() }
                    ?: throw Exception("Could not read file.")

                val db = AppDatabase.getDatabase(application)

                val message: String = when {
                    // Vehicles.csv: Row ID,Make,Model,Fuel Type,Year,Lic#,VIN,Insurance#,Notes,Picture Path,Vehicle Docs,Vehicle Img Names,Vehicle ID,Other Specs
                    headerLine.contains("Make", ignoreCase = true) && headerLine.contains("Vehicle ID", ignoreCase = true) ->
                        importVehiclesCsv(uri, db)

                    // Trip_Log.csv: ...,Departure Odo,Arrival Odo,...
                    headerLine.contains("Departure Odo", ignoreCase = true) ->
                        importTripLogCsv(uri, db)

                    // Fuel_Log.csv: ...,Odometer,Qty,...,Record Type,Record Desc
                    headerLine.contains("Odometer", ignoreCase = true) && headerLine.contains("Record Type", ignoreCase = true) ->
                        importFuelLogCsv(uri, db)

                    // Services.csv: Row ID,Vehicle ID,Record Type,Service Name,Recurring,Due Miles,Due Days,Last Odo,Last Date
                    // This file only holds reminder/category setup (every row has Last Date = 0 in practice) —
                    // there is no actual completed-service history in it, so there is nothing to import.
                    headerLine.contains("Service Name", ignoreCase = true) && headerLine.contains("Recurring", ignoreCase = true) ->
                        "This file only contains reminder/category settings, not completed service history — there's nothing to import from it."

                    // Native round-trip: the app's own service_logs CSV export
                    headerLine.contains("serviceType", ignoreCase = true) -> {
                        val vehicleMap = loadVehicleMap(db)
                        val fallbackId = vehicleMap.values.firstOrNull()
                            ?: throw Exception("Please add a vehicle in the app first!")
                        val stream = application.contentResolver.openInputStream(uri)
                            ?: throw Exception("Could not open file.")
                        val logs = parseNativeServiceCsv(stream, fallbackId)
                        logs.forEach { db.serviceLogDao().insertServiceLog(it) }
                        "Imported ${logs.size} service(s) successfully!"
                    }

                    else -> throw Exception("Unrecognized CSV format.")
                }

                _uiState.update { it.copy(isBackingUp = false, backupMessage = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Import failed: ${e.localizedMessage}") }
            }
        }
    }

    // ---------- Vehicle name <-> id helpers ----------

    // Drivvo names vehicles with inconsistent spacing (e.g. "Yamaha  Fz16 " with a double
    // space). We normalize on both the read and write side so lookups aren't thrown off by that.
    private fun normalizeVehicleName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ")

    private fun loadVehicleMap(db: AppDatabase): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        val cursor = db.openHelper.readableDatabase.query("SELECT id, name FROM vehicles")
        cursor.use {
            while (it.moveToNext()) {
                map[normalizeVehicleName(it.getString(1))] = it.getLong(0)
            }
        }
        return map
    }

    // Looks up a vehicle by name, creating it with sensible defaults if it doesn't exist yet.
    // This means fuel/service/trip files can be imported in any order, even before Vehicles.csv,
    // without misattributing rows to the wrong vehicle.
    private suspend fun getOrCreateVehicleId(
        db: AppDatabase,
        rawName: String,
        cache: MutableMap<String, Long>
    ): Long {
        val name = normalizeVehicleName(rawName)
        cache[name]?.let { return it }
        val newId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                name = name,
                type = "Car",        // source file doesn't record this — edit in-app if it's actually a bike
                fuelUnit = "Liters",
                distanceUnit = "km",
                currency = "INR"
            )
        )
        cache[name] = newId
        return newId
    }

    // Splits a raw CSV line on commas. If a row has MORE fields than the header (this happens
    // on at least one real Fuel_Log.csv row, where a service description contains a stray,
    // un-escaped comma), the overflow is folded back into the final column instead of shifting
    // every column after it out of alignment.
    private fun splitCsvRow(line: String, expectedColumns: Int): List<String> {
        val raw = line.split(",")
        val merged = if (raw.size > expectedColumns) {
            val head = raw.subList(0, expectedColumns - 1)
            val tail = raw.subList(expectedColumns - 1, raw.size).joinToString(",")
            head + tail
        } else raw
        return merged.map { it.trim().trim('"') }
    }

    private fun buildDateMillis(day: Int, month: Int, year: Int, hour: Int = 0, minute: Int = 0): Long {
        return try {
            val cal = Calendar.getInstance()
            cal.set(year, (month - 1).coerceIn(0, 11), day.coerceIn(1, 31), hour, minute, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // ---------- Vehicles.csv ----------
    // Row ID,Make,Model,Fuel Type,Year,Lic#,VIN,Insurance#,Notes,Picture Path,Vehicle Docs,Vehicle Img Names,Vehicle ID,Other Specs
    private suspend fun importVehiclesCsv(uri: Uri, db: AppDatabase): String {
        val existingNames = loadVehicleMap(db).keys.toMutableSet()
        var imported = 0
        var skipped = 0

        application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val header = reader.readLine() ?: return "Empty file."
            val columnCount = header.split(",").size
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val t = splitCsvRow(row, columnCount)
                if (t.size < 13) { skipped++; continue }

                // Column 12 ("Vehicle ID") is the display name string used to link this
                // vehicle across Fuel_Log.csv / Trip_Log.csv / Services.csv — use it as-is
                // rather than rebuilding from Make + Model, so lookups match exactly.
                val name = normalizeVehicleName(t[12])
                if (name.isBlank() || name in existingNames) { skipped++; continue }

                db.vehicleDao().insertVehicle(
                    VehicleEntity(
                        name = name,
                        type = "Car",      // not present in this export — edit in-app if it's actually a bike
                        fuelUnit = "Liters",
                        distanceUnit = "km",
                        currency = "INR"
                    )
                )
                existingNames.add(name)
                imported++
            }
        }
        return "Imported $imported vehicle(s)" +
            if (skipped > 0) ", skipped $skipped duplicate/invalid row(s)." else "."
    }

    // ---------- Fuel_Log.csv ----------
    // Row ID,Vehicle ID,Odometer,Qty,Partial Tank,Missed Fill Up,Total Cost,Distance Travelled,
    // Eff,Octane,Fuel Brand,Filling Station,Notes,Day,Month,Year,Receipt Path,Latitude,Longitude,
    // Record Type,Record Desc
    //
    // Record Type: 0 = fuel fill-up, 1 = service performed, 4 = misc "adhoc" entry (only kept as
    // an expense if it actually has a cost — the single sample row of this type had cost 0 and
    // looks like a plain odometer checkpoint, not a real expense).
    private suspend fun importFuelLogCsv(uri: Uri, db: AppDatabase): String {
        val vehicleCache = loadVehicleMap(db)
        var fuelCount = 0
        var serviceCount = 0
        var expenseCount = 0
        var skipped = 0

        application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val header = reader.readLine() ?: return "Empty file."
            val columnCount = header.split(",").size
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val t = splitCsvRow(row, columnCount)
                if (t.size < 20) { skipped++; continue }

                val vehicleName = t[1]
                if (vehicleName.isBlank()) { skipped++; continue }
                val vehicleId = getOrCreateVehicleId(db, vehicleName, vehicleCache)

                val day = t[13].toIntOrNull() ?: 1
                val month = t[14].toIntOrNull() ?: 1
                val year = t[15].toIntOrNull() ?: 1970
                val date = buildDateMillis(day, month, year)
                val odometer = t[2].toDoubleOrNull() ?: 0.0
                val totalCost = t[6].toDoubleOrNull() ?: 0.0
                val recordType = t[19].toIntOrNull() ?: 0
                val notes = t.getOrElse(12) { "" }.ifBlank { null }

                when (recordType) {
                    0 -> { // Fuel Record
                        val qty = t[3].toDoubleOrNull() ?: 0.0
                        val pricePerUnit = if (qty > 0) totalCost / qty else 0.0
                        val station = listOf(t.getOrElse(10) { "" }, t.getOrElse(11) { "" })
                            .filter { it.isNotBlank() }
                            .joinToString(" – ")
                            .ifBlank { null }

                        db.fuelLogDao().insertFuelLog(
                            FuelLogEntity(
                                vehicleId = vehicleId,
                                date = date,
                                odometer = odometer,
                                quantity = qty,
                                pricePerUnit = pricePerUnit,
                                totalCost = totalCost,
                                isPartialTank = t[4] == "1",
                                stationName = station,
                                notes = notes,
                                receiptPath = t.getOrElse(16) { "" }.ifBlank { null }
                            )
                        )
                        fuelCount++
                    }
                    1 -> { // Service Record — the actual service name lives in "Record Desc" (last column)
                        val serviceType = t.getOrElse(20) { "" }.ifBlank { "Service" }
                        db.serviceLogDao().insertServiceLog(
                            ServiceLogEntity(
                                vehicleId = vehicleId,
                                date = date,
                                odometer = odometer,
                                serviceType = serviceType,
                                totalCost = totalCost,
                                notes = notes
                            )
                        )
                        serviceCount++
                    }
                    4 -> { // "adhoc" — only worth keeping if it actually has a cost
                        if (totalCost > 0.0) {
                            db.expenseLogDao().insertExpenseLog(
                                ExpenseLogEntity(
                                    vehicleId = vehicleId,
                                    date = date,
                                    category = "Other",
                                    totalCost = totalCost,
                                    notes = notes
                                )
                            )
                            expenseCount++
                        } else {
                            skipped++
                        }
                    }
                    else -> skipped++
                }
            }
        }
        return "Imported $fuelCount fuel entr${if (fuelCount == 1) "y" else "ies"}, " +
            "$serviceCount service log(s), $expenseCount expense(s)" +
            if (skipped > 0) ", skipped $skipped row(s)." else "."
    }

    // ---------- Trip_Log.csv ----------
    // Row ID,Vehicle ID,Departure Odo,Arrival Odo,Departure Loc,Arrival Loc,Departure Day,
    // Departure Month,Departure Year,Departure Hour,Departure Min,Arrival Day,Arrival Month,
    // Arrival Year,Arrival Hour,Arrival Min,Parking,Toll,Tax Ded,Notes,Departure Latitude,
    // Departure Longitude,Arrival Latitiude,Arrival Longitude,Trip Type
    private suspend fun importTripLogCsv(uri: Uri, db: AppDatabase): String {
        val vehicleCache = loadVehicleMap(db)
        var imported = 0
        var skipped = 0

        application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val header = reader.readLine() ?: return "Empty file."
            val columnCount = header.split(",").size
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val t = splitCsvRow(row, columnCount)
                if (t.size < 25) { skipped++; continue }

                val vehicleName = t[1]
                if (vehicleName.isBlank()) { skipped++; continue }
                val vehicleId = getOrCreateVehicleId(db, vehicleName, vehicleCache)

                // Use the departure date/time as the trip's canonical "date" — the entity
                // only has one date field, and departure is always populated in the sample data.
                val day = t[6].toIntOrNull() ?: 1
                val month = t[7].toIntOrNull() ?: 1
                val year = t[8].toIntOrNull() ?: 1970
                val hour = t[9].toIntOrNull() ?: 0
                val minute = t[10].toIntOrNull() ?: 0
                val date = buildDateMillis(day, month, year, hour, minute)

                val startOdo = t[2].toDoubleOrNull() ?: 0.0
                val endOdo = t[3].toDoubleOrNull() ?: 0.0
                val purpose = t.getOrElse(24) { "" }.ifBlank { "Personal" }

                // TripLogEntity has no separate fields for location/parking/toll, so fold
                // anything useful into notes rather than silently dropping it.
                val extraNotes = buildList {
                    val fromTo = listOfNotNull(
                        t.getOrElse(4) { "" }.ifBlank { null },
                        t.getOrElse(5) { "" }.ifBlank { null }
                    )
                    if (fromTo.isNotEmpty()) add(fromTo.joinToString(" → "))
                    t.getOrElse(19) { "" }.ifBlank { null }?.let { add(it) }
                    val parking = t.getOrElse(16) { "0" }.toDoubleOrNull() ?: 0.0
                    val toll = t.getOrElse(17) { "0" }.toDoubleOrNull() ?: 0.0
                    if (parking > 0.0) add("Parking: $parking")
                    if (toll > 0.0) add("Toll: $toll")
                    if (endOdo <= 0.0) add("(trip appears incomplete — no arrival recorded)")
                }.joinToString(" | ").ifBlank { null }

                db.tripLogDao().insertTripLog(
                    TripLogEntity(
                        vehicleId = vehicleId,
                        date = date,
                        startOdo = startOdo,
                        endOdo = endOdo,
                        purpose = purpose,
                        notes = extraNotes
                    )
                )
                imported++
            }
        }
        return "Imported $imported trip(s)" + if (skipped > 0) ", skipped $skipped row(s)." else "."
    }

    // ---------- Native CSV Parser (unchanged — the app's own service_logs export format) ----------
    private suspend fun parseNativeServiceCsv(
        inputStream: java.io.InputStream, 
        fallbackVehicleId: Long
    ): List<ServiceLogEntity> {
        val importedLogs = mutableListOf<ServiceLogEntity>()
        java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
            reader.readLine() // Skip header
            
            val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split(regex).map { it.trim('"').trim() }
                if (tokens.size >= 7) {
                    val rawDate = tokens[2].toLongOrNull() ?: 0L
                    val safeDate = if (rawDate <= 0L) System.currentTimeMillis() else rawDate
                    
                    importedLogs.add(ServiceLogEntity(
                        vehicleId = tokens[1].toLongOrNull() ?: fallbackVehicleId,
                        date = safeDate,
                        odometer = tokens[3].toDoubleOrNull() ?: 0.0,
                        serviceType = tokens[4], 
                        totalCost = tokens[5].toDoubleOrNull() ?: 0.0, 
                        notes = tokens[6]
                    ))
                }
            }
        }
        return importedLogs
    }
}