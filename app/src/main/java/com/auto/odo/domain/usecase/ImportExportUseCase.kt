package com.auto.odo.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.auto.odo.core.CsvManager
import com.auto.odo.data.AppDatabase
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
 *  1. Parse and validate Vehicles.csv plus optional log CSVs without changing Room data
 *  2. Replace existing data in one Room transaction
 *  3. Remap imported rows from provisional vehicle IDs to generated Room IDs
 *  4. Services.csv is skipped (template config only, not actual records)
 */

class ImportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
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

                // Parse and validate everything we can before touching the existing DB.
                // This prevents a wrong folder or corrupt CSV from wiping user data.
                val vehiclesFile = folder.findFile("Vehicles.csv")
                    ?: return@withContext ImportExportResult.Error("Vehicles.csv not found in selected folder.")

                val vehicleEntities = context.contentResolver
                    .openInputStream(vehiclesFile.uri)
                    ?.use { stream -> CsvManager.parseVehiclesCsv(BufferedReader(InputStreamReader(stream))) }
                    ?: return@withContext ImportExportResult.Error("Could not read Vehicles.csv.")

                if (vehicleEntities.isEmpty()) {
                    return@withContext ImportExportResult.Error("Vehicles.csv contains no vehicles.")
                }

                // Use stable provisional IDs while parsing dependent files. They are remapped
                // to Room's generated IDs inside the transaction below.
                val provisionalVehicleNameToId = vehicleEntities
                    .mapIndexed { index, vehicle -> vehicle.name.trim() to -(index + 1L) }
                    .toMap()

                val fuelLogFile = folder.findFile("Fuel_Log.csv")
                val parsedFuel = if (fuelLogFile != null) {
                    context.contentResolver
                        .openInputStream(fuelLogFile.uri)
                        ?.use { stream ->
                            CsvManager.parseFuelLogCsv(
                                BufferedReader(InputStreamReader(stream)),
                                provisionalVehicleNameToId
                            )
                        }
                        ?: return@withContext ImportExportResult.Error("Could not read Fuel_Log.csv.")
                } else {
                    Log.w(TAG, "Fuel_Log.csv not found — skipping.")
                    CsvManager.FuelLogParseResult(emptyList(), emptyList(), emptyList())
                }

                val tripLogFile = folder.findFile("Trip_Log.csv")
                val parsedTrips = if (tripLogFile != null) {
                    context.contentResolver
                        .openInputStream(tripLogFile.uri)
                        ?.use { stream ->
                            CsvManager.parseTripLogCsv(
                                BufferedReader(InputStreamReader(stream)),
                                provisionalVehicleNameToId
                            )
                        }
                        ?: return@withContext ImportExportResult.Error("Could not read Trip_Log.csv.")
                } else {
                    Log.w(TAG, "Trip_Log.csv not found — skipping.")
                    emptyList()
                }

                var fuelCount = 0
                var serviceCount = 0
                var expenseCount = 0
                var tripCount = 0

                db.withTransaction {
                    vehicleRepo.deleteAllVehicles()

                    val provisionalToActualId = mutableMapOf<Long, Long>()
                    vehicleEntities.forEach { vehicle ->
                        val newId = vehicleRepo.insertVehicle(vehicle)
                        val provisionalId = provisionalVehicleNameToId.getValue(vehicle.name.trim())
                        provisionalToActualId[provisionalId] = newId
                    }

                    fun actualVehicleId(provisionalId: Long): Long =
                        provisionalToActualId[provisionalId]
                            ?: error("Vehicle ID mapping missing for imported row: $provisionalId")

                    val fuelLogs = parsedFuel.fuelLogs.map { it.copy(vehicleId = actualVehicleId(it.vehicleId)) }
                    val serviceLogs = parsedFuel.serviceLogs.map { it.copy(vehicleId = actualVehicleId(it.vehicleId)) }
                    val expenseLogs = parsedFuel.expenseLogs.map { it.copy(vehicleId = actualVehicleId(it.vehicleId)) }
                    val trips = parsedTrips.map { it.copy(vehicleId = actualVehicleId(it.vehicleId)) }

                    if (fuelLogs.isNotEmpty()) fuelLogRepo.insertAllFuelLogs(fuelLogs)
                    if (serviceLogs.isNotEmpty()) serviceLogRepo.insertAllServiceLogs(serviceLogs)
                    if (expenseLogs.isNotEmpty()) expenseLogRepo.insertAllExpenseLogs(expenseLogs)
                    if (trips.isNotEmpty()) tripLogRepo.insertAllTripLogs(trips)

                    fuelCount = fuelLogs.size
                    serviceCount = serviceLogs.size
                    expenseCount = expenseLogs.size
                    tripCount = trips.size
                }

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

                // ── Write About_Format.txt ────────────────────────────────────────
                writeFile(folder, "About_Format.txt", "text/plain") { writer ->
                    CsvManager.writeAboutFormat(writer)
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
        mimeType: String = "text/csv",
        block: (BufferedWriter) -> Unit
    ) {
        // Delete existing file if present so we can recreate it
        folder.findFile(fileName)?.delete()
        val file = folder.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Could not create $fileName in the selected folder.")
        context.contentResolver.openOutputStream(file.uri)?.use { stream ->
            BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                block(writer)
            }
        } ?: throw IllegalStateException("Could not open output stream for $fileName.")
    }
}
