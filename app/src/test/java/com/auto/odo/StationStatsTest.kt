package com.auto.odo

import com.auto.odo.data.entity.FuelLogEntity
import com.auto.odo.presentation.viewmodel.computeStationStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationStatsTest {
    private fun fill(odo: Double, qty: Double, cost: Double, station: String?, partial: Boolean = false) =
        FuelLogEntity(
            vehicleId = 1, date = 0, odometer = odo, quantity = qty, pricePerUnit = cost / qty,
            totalCost = cost, isPartialTank = partial, stationName = station, notes = null, receiptPath = null
        )

    @Test
    fun efficiencyIsCreditedToStationThatStartedTheSegment() {
        val logs = listOf(
            fill(1000.0, 10.0, 1000.0, "Shell"),
            fill(1200.0, 10.0, 1100.0, "HP "),     // Shell's fuel: 200 km / 10 L
            fill(1500.0, 10.0, 1000.0, "shell"),   // HP's fuel: 300 km / 10 L
            fill(1600.0, 5.0, 500.0, "HP", partial = true)
        )
        val stats = computeStationStats(logs, { it }, { it }).associateBy { it.name.lowercase() }

        assertEquals(2, stats.getValue("shell").fillUps)
        assertEquals(100.0, stats.getValue("shell").avgPrice, 0.001)
        assertEquals(20.0, stats.getValue("shell").avgEfficiency!!, 0.001) // last Shell fill → partial next, skipped
        assertEquals(30.0, stats.getValue("hp").avgEfficiency!!, 0.001)    // partial HP fill contributes no segment
        assertEquals(1600.0, stats.getValue("hp").totalSpent, 0.001)
    }

    @Test
    fun noFullToFullSegmentMeansNoEfficiency() {
        val stats = computeStationStats(listOf(fill(1000.0, 10.0, 1000.0, "Solo")), { it }, { it })
        assertNull(stats.single().avgEfficiency)
    }
}
