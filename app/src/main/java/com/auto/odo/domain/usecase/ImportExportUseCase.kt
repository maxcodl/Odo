package com.auto.odo.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.auto.odo.core.CsvManager
import com.auto.odo.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

private const val TAG = "ImportExportUseCase"

// ─────────────────────────────────────────────────────────────────────────────
//  Data classes
// ─────────────────────────────────────────────────────────────────────────────

data class ImportSummary(
    val vehicleCount: Int,
    val fuelCount: Int,
    val serviceCount: Int,
    val expenseCount: Int,
    val tripCount: Int
)

data class ExportSummary(
    val vehicleCount: Int,
    val fuelCount: Int,
    val serviceCount: Int,
    val expenseCount: Int,
    val tripCount: Int
)

sealed class ImportExportResult<T> {
    data class Success<T>(val data: T) : ImportExportResult<T>()
    data class Error<T>(val message: String) : ImportExportResult<T>()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Import Use Case
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads 4 CSV files from a user-selected folder (SAF URI) and replaces all
 * Room data with the imported records.
 *
 * Order of operations:
 *  1. Clear all existing data (Vehicles cascade-deletes children automatically)
 *  2. Parse & insert Vehicles → capture name→id map
 *  3. Parse & insert Fuel_Log.csv (fuel, service, expense rows)
 *  4. Parse & insert Trip_Log.csv
 *  5. Services.csv is skipped (template config only, not actual records)
 */
class ImportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleRepo: VehicleRepository,
    private val fuelLogRepo: FuelLogRepository,
    private val serviceLogRepo: ServiceLogRepository,
    private val expenseLogRepo: ExpenseLogRepository,
    private val tripLogRepo: TripLogRepository
) {
    suspend operator fun invoke(folderUri: Uri): ImportExportResult<ImportSummary> =
        withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                    ?: return@withContext ImportExportResult.Error("Cannot access selected folder.")

                // ── 1. Wipe existing data (FK cascade handles children) ──────────
                vehicleRepo.deleteAllVehicles()

                // ── 2. Parse Vehicles.csv ─────────────────────────────────────────
                val vehiclesFile = folder.findFile("Vehicles.csv")
                    ?: return@withContext ImportExportResult.Error("Vehicles.csv not found in selected folder.")

                val vehicleEntities = context.contentResolver
                    .openInputStream(vehiclesFile.uri)
                    ?.use { stream -> CsvManager.parseVehiclesCsv(BufferedReader(InputStreamReader(stream))) }
                    ?: return@withContext ImportExportResult.Error("Could not read Vehicles.csv.")

                if (vehicleEntities.isEmpty()) {
                    return@withContext ImportExportResult.Error("Vehicles.csv contains no vehicles.")
                }

                // Insert vehicles and build name→id map
                // We need the new auto-generated IDs, so insert one-by-one and track
                val vehicleNameToId = mutableMapOf<String, Long>()
                vehicleEntities.forEach { v ->
                    val newId = vehicleRepo.insertVehicle(v)
                    vehicleNameToId[v.name.trim()] = newId
                }

                // ── 3. Parse Fuel_Log.csv ─────────────────────────────────────────
                var fuelCount = 0
                var serviceCount = 0
                var expenseCount = 0

                val fuelLogFile = folder.findFile("Fuel_Log.csv")
                if (fuelLogFile != null) {
                    val result = context.contentResolver
                        .openInputStream(fuelLogFile.uri)
                        ?.use { stream ->
                            CsvManager.parseFuelLogCsv(
                                BufferedReader(InputStreamReader(stream)),
                                vehicleNameToId
                            )
                        }
                    if (result != null) {
                        if (result.fuelLogs.isNotEmpty()) {
                            fuelLogRepo.insertAllFuelLogs(result.fuelLogs)
                            fuelCount = result.fuelLogs.size
                        }
                        if (result.serviceLogs.isNotEmpty()) {
                            serviceLogRepo.insertAllServiceLogs(result.serviceLogs)
                            serviceCount = result.serviceLogs.size
                        }
                        if (result.expenseLogs.isNotEmpty()) {
                            expenseLogRepo.insertAllExpenseLogs(result.expenseLogs)
                            expenseCount = result.expenseLogs.size
                        }
                    }
                } else {
                    Log.w(TAG, "Fuel_Log.csv not found — skipping.")
                }

                // ── 4. Parse Trip_Log.csv ─────────────────────────────────────────
                var tripCount = 0
                val tripLogFile = folder.findFile("Trip_Log.csv")
                if (tripLogFile != null) {
                    val trips = context.contentResolver
                        .openInputStream(tripLogFile.uri)
                        ?.use { stream ->
                            CsvManager.parseTripLogCsv(
                                BufferedReader(InputStreamReader(stream)),
                                vehicleNameToId
                            )
                        }
                    if (!trips.isNullOrEmpty()) {
                        tripLogRepo.insertAllTripLogs(trips)
                        tripCount = trips.size
                    }
                } else {
                    Log.w(TAG, "Trip_Log.csv not found — skipping.")
                }

                // Services.csv is intentionally skipped — it contains template definitions,
                // not actual log records. Real service/expense records live in Fuel_Log.csv.

                ImportExportResult.Success(
                    ImportSummary(
                        vehicleCount = vehicleEntities.size,
                        fuelCount = fuelCount,
                        serviceCount = serviceCount,
                        expenseCount = expenseCount,
                        tripCount = tripCount
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                ImportExportResult.Error("Import failed: ${e.localizedMessage}")
            }
        }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Export Use Case
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads all Room data and writes 4 CSV files to a user-selected folder (SAF URI).
 * Files are created (or overwritten) in the same format as the original backup files.
 */
class ExportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleRepo: VehicleRepository,
    private val fuelLogRepo: FuelLogRepository,
    private val serviceLogRepo: ServiceLogRepository,
    private val expenseLogRepo: ExpenseLogRepository,
    private val tripLogRepo: TripLogRepository
) {
    suspend operator fun invoke(folderUri: Uri): ImportExportResult<ExportSummary> =
        withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                    ?: return@withContext ImportExportResult.Error("Cannot access selected folder.")

                // ── Fetch all data ────────────────────────────────────────────────
                val vehicles = vehicleRepo.getAllVehiclesList()
                val fuelLogs = fuelLogRepo.getAllFuelLogs()
                val serviceLogs = serviceLogRepo.getAllServiceLogs()
                val expenseLogs = expenseLogRepo.getAllExpenseLogs()
                val tripLogs = tripLogRepo.getAllTripLogs()

                val vehicleIdToName = vehicles.associate { it.id to it.name }

                // ── Write Vehicles.csv ────────────────────────────────────────────
                writeFile(folder, "Vehicles.csv") { writer ->
                    CsvManager.writeVehiclesCsv(writer, vehicles)
                }

                // ── Write Fuel_Log.csv ────────────────────────────────────────────
                writeFile(folder, "Fuel_Log.csv") { writer ->
                    CsvManager.writeFuelLogCsv(writer, fuelLogs, serviceLogs, expenseLogs, vehicleIdToName)
                }

                // ── Write Trip_Log.csv ────────────────────────────────────────────
                writeFile(folder, "Trip_Log.csv") { writer ->
                    CsvManager.writeTripLogCsv(writer, tripLogs, vehicleIdToName)
                }

                // ── Write Services.csv ────────────────────────────────────────────
                writeFile(folder, "Services.csv") { writer ->
                    CsvManager.writeServicesCsv(writer, vehicles, serviceLogs, expenseLogs, vehicleIdToName)
                }

                ImportExportResult.Success(
                    ExportSummary(
                        vehicleCount = vehicles.size,
                        fuelCount = fuelLogs.size,
                        serviceCount = serviceLogs.size,
                        expenseCount = expenseLogs.size,
                        tripCount = tripLogs.size
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                ImportExportResult.Error("Export failed: ${e.localizedMessage}")
            }
        }

    /**
     * Create-or-overwrite a file in the given DocumentFile folder and write to it.
     */
    private fun writeFile(
        folder: DocumentFile,
        fileName: String,
        block: (BufferedWriter) -> Unit
    ) {
        // Delete existing file if present so we can recreate it
        folder.findFile(fileName)?.delete()
        val file = folder.createFile("text/csv", fileName)
            ?: throw IllegalStateException("Could not create $fileName in the selected folder.")
        context.contentResolver.openOutputStream(file.uri)?.use { stream ->
            BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                block(writer)
            }
        } ?: throw IllegalStateException("Could not open output stream for $fileName.")
    }
}
