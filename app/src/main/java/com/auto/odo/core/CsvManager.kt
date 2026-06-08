package com.auto.odo.core

import com.auto.odo.data.entity.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure utility for parsing the legacy CSV backup format and writing it back.
 *
 * CSV file specs (from the old app):
 *   Vehicles.csv    – vehicle definitions
 *   Fuel_Log.csv    – mixed fuel + service + expense records (Record Type column)
 *   Services.csv    – service/expense/trip template definitions (config, not logs)
 *   Trip_Log.csv    – trip log entries
 */
object CsvManager {

    // ──────────────────────────────────────────────
    //  Date helpers
    // ──────────────────────────────────────────────

    /** Day/Month/Year ints → epoch millis (start of day, UTC). */
    private fun toEpochMillis(day: Int, month: Int, year: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, 0, 0, 0) // Calendar months are 0-based
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Epoch millis → Triple(day, month, year). */
    private fun fromEpochMillis(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return Triple(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    /** Epoch millis → 5-tuple (day, month, year, hour, minute). */
    private fun fromEpochMillisFull(millis: Long): List<Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return listOf(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    private fun toEpochMillisFull(day: Int, month: Int, year: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ──────────────────────────────────────────────
    //  CSV field helpers
    // ──────────────────────────────────────────────

    /** Parse a single CSV line respecting quoted fields (handles commas inside quotes). */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    /** Escape a value for CSV output (quote if it contains comma, quote, or newline). */
    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun String.trimOrNull(): String? = trim().ifEmpty { null }
    private fun String.safeDouble(): Double = trim().toDoubleOrNull() ?: 0.0
    private fun String.safeInt(): Int = trim().toIntOrNull() ?: 0

    // ══════════════════════════════════════════════
    //  IMPORT — Parsing
    // ══════════════════════════════════════════════

    /**
     * Parse Vehicles.csv → list of VehicleEntity.
     * IDs are set to 0 so Room auto-generates them.
     */
    fun parseVehiclesCsv(reader: BufferedReader): List<VehicleEntity> {
        val vehicles = mutableListOf<VehicleEntity>()
        val lines = reader.readLines()
        if (lines.isEmpty()) return vehicles
        // Skip header row
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val f = parseCsvLine(line)
                // Headers: Row ID,Make,Model,Fuel Type,Year,Lic#,VIN,Insurance#,Notes,
                //          Picture Path,Vehicle Docs,Vehicle Img Names,Vehicle ID,Other Specs
                val vehicleId = f.getOrElse(12) { "" }.trim()
                if (vehicleId.isEmpty()) continue
                vehicles.add(
                    VehicleEntity(
                        id = 0,
                        name = vehicleId.trim(),
                        type = "Bike",
                        fuelUnit = "Liters",
                        distanceUnit = "km",
                        currency = "INR"
                    )
                )
            } catch (_: Exception) {
                // Skip malformed rows
            }
        }
        return vehicles
    }

    /**
     * Result container for the mixed Fuel_Log.csv parse.
     */
    data class FuelLogParseResult(
        val fuelLogs: List<FuelLogEntity>,
        val serviceLogs: List<ServiceLogEntity>,
        val expenseLogs: List<ExpenseLogEntity>
    )

    /**
     * Parse Fuel_Log.csv → split into fuel, service, and expense entities.
     * Record Type: 0 = Fuel, 1 = Service, 4 = Expense/Ad-hoc.
     */
    fun parseFuelLogCsv(
        reader: BufferedReader,
        vehicleNameToId: Map<String, Long>
    ): FuelLogParseResult {
        val fuelLogs = mutableListOf<FuelLogEntity>()
        val serviceLogs = mutableListOf<ServiceLogEntity>()
        val expenseLogs = mutableListOf<ExpenseLogEntity>()

        val lines = reader.readLines()
        if (lines.isEmpty()) return FuelLogParseResult(fuelLogs, serviceLogs, expenseLogs)

        // Headers (index reference):
        // 0:Row ID, 1:Vehicle ID, 2:Odometer, 3:Qty, 4:Partial Tank, 5:Missed Fill Up,
        // 6:Total Cost, 7:Distance Travelled, 8:Eff, 9:Octane, 10:Fuel Brand,
        // 11:Filling Station, 12:Notes, 13:Day, 14:Month, 15:Year, 16:Receipt Path,
        // 17:Latitude, 18:Longitude, 19:Record Type, 20:Record Desc

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val f = parseCsvLine(line)
                val vehicleName = f.getOrElse(1) { "" }.trim()
                val vehicleId = vehicleNameToId[vehicleName] ?: continue

                val odometer = f.getOrElse(2) { "0" }.safeDouble()
                val qty = f.getOrElse(3) { "0" }.safeDouble()
                val totalCost = f.getOrElse(6) { "0" }.safeDouble()
                val stationName = f.getOrElse(11) { "" }.trimOrNull()
                val notes = f.getOrElse(12) { "" }.trimOrNull()
                val day = f.getOrElse(13) { "1" }.safeInt()
                val month = f.getOrElse(14) { "1" }.safeInt()
                val year = f.getOrElse(15) { "2024" }.safeInt()
                val receiptPath = f.getOrElse(16) { "" }.trimOrNull()
                val recordType = f.getOrElse(19) { "0" }.safeInt()
                val recordDesc = f.getOrElse(20) { "" }.trimOrNull()

                val date = toEpochMillis(day, month, year)

                when (recordType) {
                    0 -> {
                        // Fuel record
                        val pricePerUnit = if (qty > 0) totalCost / qty else 0.0
                        val isPartial = f.getOrElse(4) { "0" }.safeInt() == 1
                        fuelLogs.add(
                            FuelLogEntity(
                                id = 0,
                                vehicleId = vehicleId,
                                date = date,
                                odometer = odometer,
                                quantity = qty,
                                pricePerUnit = pricePerUnit,
                                totalCost = totalCost,
                                isPartialTank = isPartial,
                                stationName = stationName,
                                notes = notes,
                                receiptPath = receiptPath
                            )
                        )
                    }
                    1 -> {
                        // Service/Maintenance record
                        serviceLogs.add(
                            ServiceLogEntity(
                                id = 0,
                                vehicleId = vehicleId,
                                date = date,
                                odometer = odometer,
                                serviceType = recordDesc ?: "Unknown Service",
                                totalCost = totalCost,
                                notes = notes
                            )
                        )
                    }
                    else -> {
                        // Ad-hoc / other → Expense
                        if (totalCost > 0 || (recordDesc != null && recordDesc != "adhoc")) {
                            expenseLogs.add(
                                ExpenseLogEntity(
                                    id = 0,
                                    vehicleId = vehicleId,
                                    date = date,
                                    category = recordDesc ?: "Other",
                                    totalCost = totalCost,
                                    notes = notes
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip malformed rows
            }
        }
        return FuelLogParseResult(fuelLogs, serviceLogs, expenseLogs)
    }

    /**
     * Parse Trip_Log.csv → list of TripLogEntity.
     */
    fun parseTripLogCsv(
        reader: BufferedReader,
        vehicleNameToId: Map<String, Long>
    ): List<TripLogEntity> {
        val trips = mutableListOf<TripLogEntity>()
        val lines = reader.readLines()
        if (lines.isEmpty()) return trips

        // Headers (index):
        // 0:Row ID, 1:Vehicle ID, 2:Departure Odo, 3:Arrival Odo,
        // 4:Departure Loc, 5:Arrival Loc,
        // 6:Departure Day, 7:Departure Month, 8:Departure Year,
        // 9:Departure Hour, 10:Departure Min,
        // 11:Arrival Day, 12:Arrival Month, 13:Arrival Year,
        // 14:Arrival Hour, 15:Arrival Min,
        // 16:Parking, 17:Toll, 18:Tax Ded, 19:Notes,
        // 20:Departure Lat, 21:Departure Lon,
        // 22:Arrival Lat, 23:Arrival Lon, 24:Trip Type

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val f = parseCsvLine(line)
                val vehicleName = f.getOrElse(1) { "" }.trim()
                val vehicleId = vehicleNameToId[vehicleName] ?: continue

                val startOdo = f.getOrElse(2) { "0" }.safeDouble()
                var endOdo = f.getOrElse(3) { "0" }.safeDouble()
                if (endOdo == 0.0) endOdo = startOdo // incomplete trip

                val depDay = f.getOrElse(6) { "1" }.safeInt()
                val depMonth = f.getOrElse(7) { "1" }.safeInt()
                val depYear = f.getOrElse(8) { "2024" }.safeInt()
                val depHour = f.getOrElse(9) { "0" }.safeInt()
                val depMin = f.getOrElse(10) { "0" }.safeInt()

                val date = toEpochMillisFull(depDay, depMonth, depYear, depHour, depMin)
                val purpose = f.getOrElse(24) { "Personal" }.trim().ifEmpty { "Personal" }
                val notes = f.getOrElse(19) { "" }.trimOrNull()

                trips.add(
                    TripLogEntity(
                        id = 0,
                        vehicleId = vehicleId,
                        date = date,
                        startOdo = startOdo,
                        endOdo = endOdo,
                        purpose = purpose,
                        notes = notes
                    )
                )
            } catch (_: Exception) {
                // Skip malformed rows
            }
        }
        return trips
    }

    // ══════════════════════════════════════════════
    //  EXPORT — Writing
    // ══════════════════════════════════════════════

    fun writeVehiclesCsv(writer: BufferedWriter, vehicles: List<VehicleEntity>) {
        writer.write("Row ID,Make,Model,Fuel Type,Year,Lic#,VIN,Insurance#,Notes,Picture Path,Vehicle Docs,Vehicle Img Names,Vehicle ID,Other Specs")
        writer.newLine()
        vehicles.forEach { v ->
            // Split name back into Make + Model (best effort: first word = make, rest = model)
            val parts = v.name.trim().split("\\s+".toRegex(), limit = 2)
            val make = parts.getOrElse(0) { "" }
            val model = parts.getOrElse(1) { "" }
            val row = listOf(
                v.id.toString(),
                escapeCsv(make),
                escapeCsv(model),
                "", // Fuel Type
                "", // Year
                "", // Lic#
                "", // VIN
                "", // Insurance#
                "", // Notes
                "", // Picture Path
                "", // Vehicle Docs
                "", // Vehicle Img Names
                escapeCsv(v.name),
                ""  // Other Specs
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }
    }

    fun writeFuelLogCsv(
        writer: BufferedWriter,
        fuelLogs: List<FuelLogEntity>,
        serviceLogs: List<ServiceLogEntity>,
        expenseLogs: List<ExpenseLogEntity>,
        vehicleIdToName: Map<Long, String>
    ) {
        writer.write("Row ID,Vehicle ID,Odometer,Qty,Partial Tank,Missed Fill Up,Total Cost,Distance Travelled,Eff,Octane,Fuel Brand,Filling Station,Notes,Day,Month,Year,Receipt Path,Latitude,Longitude,Record Type,Record Desc")
        writer.newLine()

        var rowId = 1L

        // Write fuel records (Record Type 0)
        fuelLogs.forEach { log ->
            val name = vehicleIdToName[log.vehicleId] ?: "Unknown"
            val (day, month, year) = fromEpochMillis(log.date)
            val row = listOf(
                (rowId++).toString(),
                escapeCsv(name),
                log.odometer.toString(),
                log.quantity.toString(),
                if (log.isPartialTank) "1" else "0",
                "0", // Missed Fill Up
                log.totalCost.toString(),
                "0.0", // Distance Travelled (not stored)
                "0.0", // Eff (not stored)
                "0.0", // Octane
                "",    // Fuel Brand
                escapeCsv(log.stationName ?: ""),
                escapeCsv(log.notes ?: ""),
                day.toString(),
                month.toString(),
                year.toString(),
                escapeCsv(log.receiptPath ?: ""),
                "0.0", // Latitude
                "0.0", // Longitude
                "0",   // Record Type = Fuel
                "Fuel Record"
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }

        // Write service records (Record Type 1)
        serviceLogs.forEach { log ->
            val name = vehicleIdToName[log.vehicleId] ?: "Unknown"
            val (day, month, year) = fromEpochMillis(log.date)
            val row = listOf(
                (rowId++).toString(),
                escapeCsv(name),
                log.odometer.toString(),
                "0.0", // Qty
                "0",   // Partial Tank
                "0",   // Missed Fill Up
                log.totalCost.toString(),
                "0.0", // Distance
                "0.0", // Eff
                "0.0", // Octane
                "",    // Fuel Brand
                "",    // Filling Station
                escapeCsv(log.notes ?: ""),
                day.toString(),
                month.toString(),
                year.toString(),
                "",    // Receipt Path
                "0.0", // Lat
                "0.0", // Lon
                "1",   // Record Type = Service
                escapeCsv(log.serviceType)
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }

        // Write expense records (Record Type 4)
        expenseLogs.forEach { log ->
            val name = vehicleIdToName[log.vehicleId] ?: "Unknown"
            val (day, month, year) = fromEpochMillis(log.date)
            val row = listOf(
                (rowId++).toString(),
                escapeCsv(name),
                "0.0", // Odometer
                "0.0", // Qty
                "0",   // Partial Tank
                "0",   // Missed Fill Up
                log.totalCost.toString(),
                "0.0", // Distance
                "0.0", // Eff
                "0.0", // Octane
                "",    // Fuel Brand
                "",    // Filling Station
                escapeCsv(log.notes ?: ""),
                day.toString(),
                month.toString(),
                year.toString(),
                "",    // Receipt Path
                "0.0", // Lat
                "0.0", // Lon
                "4",   // Record Type = ad-hoc/expense
                escapeCsv(log.category)
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }
    }

    fun writeTripLogCsv(
        writer: BufferedWriter,
        trips: List<TripLogEntity>,
        vehicleIdToName: Map<Long, String>
    ) {
        writer.write("Row ID,Vehicle ID,Departure Odo,Arrival Odo,Departure Loc,Arrival Loc,Departure Day,Departure Month,Departure Year,Departure Hour,Departure Min,Arrival Day,Arrival Month,Arrival Year,Arrival Hour,Arrival Min,Parking,Toll,Tax Ded,Notes,Departure Latitude,Departure Longitude,Arrival Latitiude,Arrival Longitude,Trip Type")
        writer.newLine()
        var rowId = 1L
        trips.forEach { trip ->
            val name = vehicleIdToName[trip.vehicleId] ?: "Unknown"
            val dep = fromEpochMillisFull(trip.date)
            val row = listOf(
                (rowId++).toString(),
                escapeCsv(name),
                trip.startOdo.toString(),
                trip.endOdo.toString(),
                "", // Departure Loc
                "", // Arrival Loc
                dep[0].toString(), dep[1].toString(), dep[2].toString(),
                dep[3].toString(), dep[4].toString(),
                dep[0].toString(), dep[1].toString(), dep[2].toString(), // arrival = departure (no arrival time stored)
                dep[3].toString(), dep[4].toString(),
                "0.0", // Parking
                "0.0", // Toll
                "0.0", // Tax Ded
                escapeCsv(trip.notes ?: ""),
                "0.0", "0.0", // Departure lat/lon
                "0.0", "0.0", // Arrival lat/lon
                escapeCsv(trip.purpose)
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }
    }

    fun writeServicesCsv(
        writer: BufferedWriter,
        vehicles: List<VehicleEntity>,
        serviceLogs: List<ServiceLogEntity>,
        expenseLogs: List<ExpenseLogEntity>,
        vehicleIdToName: Map<Long, String>
    ) {
        writer.write("Row ID,Vehicle ID,Record Type,Service Name,Recurring,Due Miles,Due Days,Last Odo,Last Date")
        writer.newLine()
        var rowId = 1L

        // Trip purpose templates (Record Type 3) — global "All"
        listOf("Business", "Personal").forEach { purpose ->
            val row = listOf(
                (rowId++).toString(), "All", "3", purpose, "0", "0.0", "0", "0.0", "0"
            ).joinToString(",")
            writer.write(row)
            writer.newLine()
        }

        // Per-vehicle service types (Record Type 1) and expense categories (Record Type 2)
        vehicles.forEach { vehicle ->
            val vName = vehicle.name

            // Distinct service types for this vehicle
            val serviceTypes = serviceLogs
                .filter { it.vehicleId == vehicle.id }
                .map { it.serviceType }
                .distinct()
            serviceTypes.forEach { sType ->
                val row = listOf(
                    (rowId++).toString(), escapeCsv(vName), "1", escapeCsv(sType),
                    "1", "0.0", "0", "0.0", "0"
                ).joinToString(",")
                writer.write(row)
                writer.newLine()
            }

            // Distinct expense categories for this vehicle
            val expenseCategories = expenseLogs
                .filter { it.vehicleId == vehicle.id }
                .map { it.category }
                .distinct()
            expenseCategories.forEach { cat ->
                val row = listOf(
                    (rowId++).toString(), escapeCsv(vName), "2", escapeCsv(cat),
                    "0", "0.0", "0", "0.0", "0"
                ).joinToString(",")
                writer.write(row)
                writer.newLine()
            }
        }
    }
}
