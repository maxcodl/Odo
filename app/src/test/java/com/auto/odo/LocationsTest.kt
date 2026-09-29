package com.auto.odo

import com.auto.odo.core.location.LatLon
import com.auto.odo.core.location.Route
import com.auto.odo.core.location.distanceMeters
import com.auto.odo.core.location.isRealMovement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.auto.odo.core.location.nearestSavedStation
import com.auto.odo.data.entity.FuelLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationsTest {
    private fun fill(station: String?, lat: Double?, lon: Double?) = FuelLogEntity(
        vehicleId = 1, date = 0, odometer = 0.0, quantity = 1.0, pricePerUnit = 1.0, totalCost = 1.0,
        isPartialTank = false, stationName = station, notes = null, receiptPath = null, latitude = lat, longitude = lon
    )

    @Test
    fun distanceOfOneThousandthDegreeLatitudeIsAbout111m() {
        assertEquals(111.2, distanceMeters(LatLon(19.0, 72.8), LatLon(19.001, 72.8)), 0.5)
    }

    @Test
    fun savedStationPicksClosestWithinRadius() {
        val here = LatLon(19.0, 72.8)
        val logs = listOf(
            fill("Far Pump", 19.002, 72.8),   // ~222 m: outside 150 m
            fill("HP Andheri ", 19.001, 72.8), // ~111 m
            fill("No Location", null, null),
            fill(null, 19.0, 72.8)
        )
        assertEquals("HP Andheri", nearestSavedStation(logs, here))
        assertNull(nearestSavedStation(logs.take(1), here))
    }

    @Test
    fun parkedDriftIsNotDistance() {
        // Parked: fix wanders 35 m but GPS speed ~0
        assertFalse(isRealMovement(35f, 5.0, 0.3f, 30f))
        // No speed reported: 35 m jump is within two 20 m-accurate fixes' error
        assertFalse(isRealMovement(35f, 5.0, null, 40f))
        // Driving ~50 km/h: 70 m in 5 s
        assertTrue(isRealMovement(70f, 5.0, 14f, 16f))
        // 2 km in 5 s is a bad fix
        assertFalse(isRealMovement(2000f, 5.0, 20f, 16f))
    }

    @Test
    fun routeRoundTripsAndSkipsGarbage() {
        val route = listOf(LatLon(19.07598, 72.87766), LatLon(18.52043, 73.85674))
        assertEquals(route, Route.decode(Route.encode(route)))
        assertEquals(emptyList<LatLon>(), Route.decode(null))
        assertEquals(1, Route.decode("1.0,2.0;bad;3.0").size)
    }
}
